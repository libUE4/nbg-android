package com.nbg.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal const val NBG_SKILL_CURATOR_LOOP_WORK_NAME = "nbg-skill-curator-loop"
internal const val NBG_SKILL_CURATOR_LOOP_VERSION = "nbg-skill-curator-loop-v1"
private const val NBG_SKILL_CURATOR_RUN_HISTORY_LIMIT = 12

data class NbgSkillCuratorRunHistoryEntry(
  val kind: String = "review",
  val ok: Boolean = true,
  val ranAtMs: Long = 0L,
  val actionCount: Int = 0,
  val suggestionCount: Int = 0,
  val message: String = "",
)

data class NbgSkillCuratorLoopState(
  val enabled: Boolean = false,
  val intervalMinutes: Long = 360L,
  val reviewCount: Int = 0,
  val archivedCount: Int = 0,
  val lastRunAtMs: Long = 0L,
  val lastActionCount: Int = 0,
  val llmReviewEnabled: Boolean = false,
  val lastLlmReviewAtMs: Long = 0L,
  val lastLlmSuggestionCount: Int = 0,
  val lastLlmSuggestion: String = "",
  val lastError: String = "",
  val runHistory: List<NbgSkillCuratorRunHistoryEntry> = emptyList(),
  val modelVersion: String = NBG_SKILL_CURATOR_LOOP_VERSION,
) {
  val statusLabel: String
    get() = when {
      !enabled -> "后台复核关闭"
      lastError.isNotBlank() -> "后台复核需检查"
      llmReviewEnabled && lastLlmReviewAtMs > 0L -> "后台复核 + LLM 建议已运行"
      lastRunAtMs > 0L -> "后台复核已运行"
      else -> "后台复核待运行"
    }
}

internal class NbgSkillCuratorLoopStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(): NbgSkillCuratorLoopState =
    parseNbgSkillCuratorLoopState(prefs.getString(KEY_STATE, null))

  fun save(state: NbgSkillCuratorLoopState): NbgSkillCuratorLoopState {
    val normalized = state.normalized()
    prefs.edit().putString(KEY_STATE, normalized.toJsonString()).apply()
    return normalized
  }

  fun setEnabled(enabled: Boolean): NbgSkillCuratorLoopState =
    save(load().copy(enabled = enabled))

  fun setLlmReviewEnabled(enabled: Boolean): NbgSkillCuratorLoopState =
    load().let { current ->
      save(current.copy(enabled = if (enabled) true else current.enabled, llmReviewEnabled = enabled))
    }

  fun recordLlmReview(result: NbgSkillCuratorLlmReviewResult, nowMs: Long = System.currentTimeMillis()): NbgSkillCuratorLoopState {
    val current = load()
    val summary = if (result.ok) {
      result.suggestions.joinToString("; ") { "${it.skillName}:${it.action}:${it.reason}" }.take(700)
    } else {
      result.message.take(180)
    }
    return save(
      current.copy(
        lastLlmReviewAtMs = if (result.ok) nowMs.coerceAtLeast(0L) else current.lastLlmReviewAtMs,
        lastLlmSuggestionCount = if (result.ok) result.suggestions.size else current.lastLlmSuggestionCount,
        lastLlmSuggestion = if (result.ok) summary else current.lastLlmSuggestion,
        lastError = if (result.ok) "" else result.message.take(180),
      ).withRunHistory(
        NbgSkillCuratorRunHistoryEntry(
          kind = "llm",
          ok = result.ok,
          ranAtMs = nowMs.coerceAtLeast(0L),
          suggestionCount = if (result.ok) result.suggestions.size else 0,
          message = summary,
        ),
      ),
    )
  }

  private companion object {
    const val PREFS = "nbg_skill_curator_loop"
    const val KEY_STATE = "state"
  }
}

class NbgSkillCuratorLoopWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result =
    runCatching {
      NbgSkillCuratorLoopRunner(applicationContext).runOnce()
      NbgSkillCuratorAndroidLlmLoop(applicationContext).runOnce()
      Result.success()
    }.getOrElse {
      Result.retry()
    }
}

internal object NbgSkillCuratorLoopWorkManager {
  fun ensureScheduled(context: Context) {
    val state = NbgSkillCuratorLoopStore(context).load()
    if (!state.enabled) return
    val request = PeriodicWorkRequestBuilder<NbgSkillCuratorLoopWorker>(
      state.intervalMinutes.coerceAtLeast(15L),
      TimeUnit.MINUTES,
    )
      .addTag(NBG_SKILL_CURATOR_LOOP_WORK_NAME)
      .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
      NBG_SKILL_CURATOR_LOOP_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }
}

internal class NbgSkillCuratorLoopRunner(
  context: Context,
  private val loopStore: NbgSkillCuratorLoopStore = NbgSkillCuratorLoopStore(context),
  private val curatorStore: NbgSkillCuratorStore = NbgSkillCuratorStore(context),
  private val draftStore: NbgLearnedSkillDraftStore = NbgLearnedSkillDraftStore(context),
) {
  private val appContext = context.applicationContext

  fun runOnce(
    snapshot: HanakoSkillsSnapshot? = null,
    nowMs: Long = System.currentTimeMillis(),
  ): NbgSkillCuratorLoopState {
    val current = loopStore.load()
    if (!current.enabled) return current
    val effectiveSnapshot = snapshot?.takeIf { it.visibleSkills.isNotEmpty() }
      ?: nbgLocalLearnedSkillSnapshot(File(appContext.filesDir, "learned-skills"))
    return runCatching {
      val review = nbgRunSkillCuratorReview(
        snapshot = effectiveSnapshot,
        drafts = draftStore.load(),
        metadata = curatorStore.load(),
        nowMs = nowMs,
      )
      curatorStore.save(review.metadata)
      loopStore.save(
        current.copy(
          reviewCount = current.reviewCount + 1,
          archivedCount = current.archivedCount + review.archivedCount,
          lastRunAtMs = nowMs.coerceAtLeast(0L),
          lastActionCount = review.actions.size,
          lastError = "",
        ).withRunHistory(
          NbgSkillCuratorRunHistoryEntry(
            kind = "review",
            ok = true,
            ranAtMs = nowMs.coerceAtLeast(0L),
            actionCount = review.actions.size,
            message = "本地复核 ${review.actions.size} 个动作，归档 ${review.archivedCount} 个 Skill",
          ),
        ),
      )
    }.getOrElse { error ->
      val message = (error.message ?: error.javaClass.simpleName).take(180)
      loopStore.save(
        current.copy(lastError = message).withRunHistory(
          NbgSkillCuratorRunHistoryEntry(
            kind = "review",
            ok = false,
            ranAtMs = nowMs.coerceAtLeast(0L),
            message = message,
          ),
        ),
      )
    }
  }
}

private fun nbgLocalLearnedSkillSnapshot(root: File): HanakoSkillsSnapshot {
  val skills = root.listFiles()
    .orEmpty()
    .filter { it.isDirectory && it.name != ".nbg-archive" && it.name != ".nbg-rollback" }
    .mapNotNull { dir ->
      val skillFile = File(dir, "SKILL.md")
      if (!skillFile.isFile) return@mapNotNull null
      val text = runCatching { skillFile.readText(Charsets.UTF_8).take(4_000) }.getOrDefault("")
      val name = Regex("(?m)^name:\\s*['\"]?([^'\"\\n]+)").find(text)?.groupValues?.getOrNull(1)?.trim()
        ?: dir.name
      val description = Regex("(?m)^description:\\s*['\"]?([^'\"\\n]+)").find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
      HanakoSkillSummary(
        name = name,
        description = description,
        source = "user",
        enabled = false,
        readonly = false,
        filePath = skillFile.absolutePath,
        baseDir = dir.absolutePath,
      )
    }
  return HanakoSkillsSnapshot(skills = skills)
}

internal fun parseNbgSkillCuratorLoopState(raw: String?): NbgSkillCuratorLoopState =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    NbgSkillCuratorLoopState(
      enabled = root.optBoolean("enabled", false),
      intervalMinutes = root.optLong("intervalMinutes", 360L),
      reviewCount = root.optInt("reviewCount", 0),
      archivedCount = root.optInt("archivedCount", 0),
      lastRunAtMs = root.optLong("lastRunAtMs", 0L),
      lastActionCount = root.optInt("lastActionCount", 0),
      llmReviewEnabled = root.optBoolean("llmReviewEnabled", false),
      lastLlmReviewAtMs = root.optLong("lastLlmReviewAtMs", 0L),
      lastLlmSuggestionCount = root.optInt("lastLlmSuggestionCount", 0),
      lastLlmSuggestion = root.cleanString("lastLlmSuggestion").orEmpty(),
      lastError = root.cleanString("lastError").orEmpty(),
      runHistory = root.optJSONArray("runHistory").toNbgSkillCuratorRunHistory(),
    ).normalized()
  }.getOrDefault(NbgSkillCuratorLoopState())

private fun NbgSkillCuratorLoopState.normalized(): NbgSkillCuratorLoopState =
  copy(
    intervalMinutes = intervalMinutes.coerceAtLeast(15L),
    reviewCount = reviewCount.coerceAtLeast(0),
    archivedCount = archivedCount.coerceAtLeast(0),
    lastRunAtMs = lastRunAtMs.coerceAtLeast(0L),
    lastActionCount = lastActionCount.coerceAtLeast(0),
    lastLlmReviewAtMs = lastLlmReviewAtMs.coerceAtLeast(0L),
    lastLlmSuggestionCount = lastLlmSuggestionCount.coerceAtLeast(0),
    lastLlmSuggestion = lastLlmSuggestion.trim().take(700),
    lastError = lastError.trim().take(180),
    runHistory = runHistory
      .map { it.normalized() }
      .sortedByDescending { it.ranAtMs }
      .take(NBG_SKILL_CURATOR_RUN_HISTORY_LIMIT),
  )

private fun NbgSkillCuratorLoopState.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("enabled", enabled)
    .put("intervalMinutes", intervalMinutes)
    .put("reviewCount", reviewCount)
    .put("archivedCount", archivedCount)
    .put("lastRunAtMs", lastRunAtMs)
    .put("lastActionCount", lastActionCount)
    .put("llmReviewEnabled", llmReviewEnabled)
    .put("lastLlmReviewAtMs", lastLlmReviewAtMs)
    .put("lastLlmSuggestionCount", lastLlmSuggestionCount)
    .put("lastLlmSuggestion", lastLlmSuggestion)
    .put("lastError", lastError)
    .put("runHistory", JSONArray().also { array ->
      runHistory.forEach { array.put(it.toJson()) }
    })
    .toString()

private fun NbgSkillCuratorLoopState.withRunHistory(entry: NbgSkillCuratorRunHistoryEntry): NbgSkillCuratorLoopState =
  copy(runHistory = listOf(entry) + runHistory)

private fun JSONArray?.toNbgSkillCuratorRunHistory(): List<NbgSkillCuratorRunHistoryEntry> {
  if (this == null) return emptyList()
  return buildList {
    for (index in 0 until length().coerceAtMost(NBG_SKILL_CURATOR_RUN_HISTORY_LIMIT * 2)) {
      val item = optJSONObject(index) ?: continue
      add(
        NbgSkillCuratorRunHistoryEntry(
          kind = item.cleanString("kind") ?: "review",
          ok = item.optBoolean("ok", true),
          ranAtMs = item.optLong("ranAtMs", 0L),
          actionCount = item.optInt("actionCount", 0),
          suggestionCount = item.optInt("suggestionCount", 0),
          message = item.cleanString("message").orEmpty(),
        ),
      )
    }
  }
}

private fun NbgSkillCuratorRunHistoryEntry.normalized(): NbgSkillCuratorRunHistoryEntry =
  copy(
    kind = kind.trim().lowercase().ifBlank { "review" }.take(24),
    ranAtMs = ranAtMs.coerceAtLeast(0L),
    actionCount = actionCount.coerceAtLeast(0),
    suggestionCount = suggestionCount.coerceAtLeast(0),
    message = message.trim().take(180),
  )

private fun NbgSkillCuratorRunHistoryEntry.toJson(): JSONObject =
  JSONObject()
    .put("kind", kind)
    .put("ok", ok)
    .put("ranAtMs", ranAtMs)
    .put("actionCount", actionCount)
    .put("suggestionCount", suggestionCount)
    .put("message", message)

internal class NbgSkillCuratorAndroidLlmLoop(
  context: Context,
  private val loopStore: NbgSkillCuratorLoopStore = NbgSkillCuratorLoopStore(context),
  private val apiStore: NbgApiStore = NbgApiStore(context),
  private val providerProfileStore: NbgModelProviderProfileTemplateStore = NbgModelProviderProfileTemplateStore(context),
  private val curatorStore: NbgSkillCuratorStore = NbgSkillCuratorStore(context),
  private val draftStore: NbgLearnedSkillDraftStore = NbgLearnedSkillDraftStore(context),
  private val suggestionQueueStore: NbgSkillCuratorSuggestionQueueStore = NbgSkillCuratorSuggestionQueueStore(context),
  private val runner: NbgSkillCuratorLlmReviewRunner = NbgSkillCuratorLlmReviewRunner(),
) {
  private val appContext = context.applicationContext

  suspend fun runOnce(snapshot: HanakoSkillsSnapshot? = null): NbgSkillCuratorLoopState {
    val state = loopStore.load()
    if (!state.enabled || !state.llmReviewEnabled) return state
    val modelSelection = apiStore.load()
      .asSequence()
      .mapNotNull { entry ->
        val modelId = entry.selectedModelId.ifBlank { entry.verifiedModelIds.firstOrNull().orEmpty() }
        val model = entry.models.firstOrNull { it.id == modelId } ?: modelId.takeIf { it.isNotBlank() }?.let { NbgApiModel(it) }
        model?.let { entry to it }
      }
      .firstOrNull()
      ?: return loopStore.recordLlmReview(NbgSkillCuratorLlmReviewResult(false, "没有可用 URL API 模型"))
    val effectiveSnapshot = snapshot?.takeIf { it.visibleSkills.isNotEmpty() }
      ?: nbgLocalLearnedSkillSnapshot(File(appContext.filesDir, "learned-skills"))
    val result = runner.run(
      entry = modelSelection.first,
      model = modelSelection.second,
      snapshot = effectiveSnapshot,
      metadata = curatorStore.load(),
      drafts = draftStore.load(),
      providerProfiles = providerProfileStore.loadCustomProfiles(),
    )
    if (result.ok) suggestionQueueStore.recordSuggestions(result.suggestions)
    return loopStore.recordLlmReview(result)
  }
}

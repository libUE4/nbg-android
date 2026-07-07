package com.nbg.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal const val NBG_SKILL_CURATOR_LOOP_WORK_NAME = "nbg-skill-curator-loop"
internal const val NBG_SKILL_CURATOR_LOOP_VERSION = "nbg-skill-curator-loop-v1"

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
    return save(
      current.copy(
        lastLlmReviewAtMs = if (result.ok) nowMs.coerceAtLeast(0L) else current.lastLlmReviewAtMs,
        lastLlmSuggestionCount = if (result.ok) result.suggestions.size else current.lastLlmSuggestionCount,
        lastLlmSuggestion = if (result.ok) result.suggestions.joinToString("; ") { "${it.skillName}:${it.action}:${it.reason}" }.take(700) else current.lastLlmSuggestion,
        lastError = if (result.ok) "" else result.message.take(180),
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
        ),
      )
    }.getOrElse { error ->
      loopStore.save(current.copy(lastError = (error.message ?: error.javaClass.simpleName).take(180)))
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
    .toString()

internal class NbgSkillCuratorAndroidLlmLoop(
  context: Context,
  private val loopStore: NbgSkillCuratorLoopStore = NbgSkillCuratorLoopStore(context),
  private val apiStore: NbgApiStore = NbgApiStore(context),
  private val curatorStore: NbgSkillCuratorStore = NbgSkillCuratorStore(context),
  private val draftStore: NbgLearnedSkillDraftStore = NbgLearnedSkillDraftStore(context),
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
    )
    return loopStore.recordLlmReview(result)
  }
}

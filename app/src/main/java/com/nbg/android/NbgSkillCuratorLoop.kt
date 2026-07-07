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
  val lastError: String = "",
  val modelVersion: String = NBG_SKILL_CURATOR_LOOP_VERSION,
) {
  val statusLabel: String
    get() = when {
      !enabled -> "后台复核关闭"
      lastError.isNotBlank() -> "后台复核需检查"
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
    .put("lastError", lastError)
    .toString()

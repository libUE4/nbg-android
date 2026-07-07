package com.nbg.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal const val NBG_SCHEDULE_WORK_NAME = "nbg-scheduled-automations"

class NbgScheduleWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result =
    runCatching {
      NbgScheduleRunner(applicationContext).runDueAutomations()
      Result.success()
    }.getOrElse {
      Result.retry()
    }
}

internal object NbgScheduleWorkManager {
  fun ensureScheduled(context: Context) {
    val request = PeriodicWorkRequestBuilder<NbgScheduleWorker>(15, TimeUnit.MINUTES)
      .addTag(NBG_SCHEDULE_WORK_NAME)
      .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
      NBG_SCHEDULE_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }
}

internal class NbgScheduleRunner(
  context: Context,
  private val scheduleStore: NbgScheduleStore = NbgScheduleStore(context),
  private val learningEngine: NbgAutonomousLearningEngine = NbgAutonomousLearningEngine(context),
  private val historyStore: HanakoHistoryCacheStore = HanakoHistoryCacheStore(
    filesDir = context.applicationContext.filesDir,
    ubuntuRootHomeDir = nbgScheduleUbuntuRootHomeDir(context.applicationContext),
  ),
) {
  fun runDueAutomations(nowMs: Long = System.currentTimeMillis()): NbgScheduleState {
    var state = scheduleStore.load()
    state.automations
      .filter { it.enabled && it.nextRunAtMs in 1..nowMs.coerceAtLeast(0L) }
      .forEach { automation ->
        val event = nbgRunScheduledAutomationNow(
          automation = automation,
          context = NbgScheduleRunContext(
            learningSnapshot = learningEngine.snapshot(),
            sessionSummaries = historyStore.readSummaryIndex(limit = 80),
          ),
          nowMs = nowMs,
        )
        state = scheduleStore.recordRun(event)
      }
    return state
  }
}

private fun nbgScheduleUbuntuRootHomeDir(context: Context): java.io.File =
  java.io.File(context.applicationContext.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu/root")

package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgScheduleRunnerTest {
  private class FakeScheduleStorage(raw: String? = null) : NbgScheduleStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  @Test
  fun dueLowRiskAutomationCanBeRecordedByScheduleStoreLikeWorker() {
    val storage = FakeScheduleStorage()
    val store = NbgScheduleStore(storage)
    val automation = nbgScheduledAutomation(
      title = "每日摘要",
      template = NbgScheduledAutomationTemplate.DailyProjectSummary,
      prompt = "读取本地项目摘要。",
      nowMs = 100L,
    ).copy(nextRunAtMs = 150L)
    store.upsert(automation)

    val due = store.load().automations.filter { it.enabled && it.nextRunAtMs in 1..200L }
    due.forEach {
      store.recordRun(nbgRunScheduledAutomationNow(it, nowMs = 200L))
    }
    val saved = store.load()

    assertEquals(1, saved.visibleRunEvents.size)
    assertEquals(NbgScheduleRunStatus.Succeeded, saved.visibleRunEvents.single().status)
    assertTrue(saved.automations.single().nextRunAtMs > 200L)
  }
}

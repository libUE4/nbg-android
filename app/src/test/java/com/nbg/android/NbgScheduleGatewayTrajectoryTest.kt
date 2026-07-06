package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgScheduleGatewayTrajectoryTest {
  private class FakeScheduleStorage(raw: String? = null) : NbgScheduleStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  private class FakeGatewayStorage(raw: String? = null) : NbgGatewayInboxStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  @Test
  fun scheduleAllowsLowRiskReadOnlyAutomationAndBlocksCommandAutoEnable() {
    val readOnly = nbgScheduledAutomation(
      title = "每日摘要",
      template = NbgScheduledAutomationTemplate.DailyProjectSummary,
      prompt = "读取本地项目摘要。",
      nowMs = 100L,
    )
    val command = nbgScheduledAutomation(
      title = "运行测试",
      template = NbgScheduledAutomationTemplate.TestCommand,
      prompt = "./gradlew test",
      nowMs = 100L,
    )

    assertTrue(readOnly.enabled)
    assertFalse(readOnly.requiresConfirmation)
    assertEquals(NbgPermissionRiskTier.Low, readOnly.permissionTier)
    assertFalse(command.enabled)
    assertTrue(command.requiresConfirmation)
    assertEquals(NbgPermissionRiskTier.Medium, command.permissionTier)
  }

  @Test
  fun scheduleStorePersistsRunsAndKeepsSensitivePromptsOut() {
    val storage = FakeScheduleStorage()
    val store = NbgScheduleStore(storage)
    val automation = nbgScheduledAutomation(
      title = "周审计",
      template = NbgScheduledAutomationTemplate.WeeklyLearningAudit,
      prompt = "汇总学习审计。",
      nowMs = 100L,
    )

    store.upsert(automation)
    val saved = store.recordRun(
      NbgScheduleRunEvent(
        id = "run-1",
        automationId = automation.id,
        status = NbgScheduleRunStatus.Succeeded,
        summary = "ok",
        startedAtMs = 200L,
        finishedAtMs = 210L,
      ),
    )
    val reloaded = NbgScheduleStore(FakeScheduleStorage(storage.raw)).load()

    assertEquals(1, saved.enabledCount)
    assertEquals(1, reloaded.visibleRunEvents.size)
    assertTrue(storage.raw.orEmpty().contains(NBG_SCHEDULE_STORE_VERSION))
  }

  @Test
  fun scheduleRunNowBuildsLocalEvidenceAndUpdatesRunWindow() {
    val storage = FakeScheduleStorage()
    val store = NbgScheduleStore(storage)
    val automation = nbgScheduledAutomation(
      title = "学习报告",
      template = NbgScheduledAutomationTemplate.LearningReport,
      prompt = "生成学习图谱报告。",
      nowMs = 100L,
    )
    val context = NbgScheduleRunContext(
      learningSnapshot = NbgAutonomousLearningSnapshot(
        localMemory = NbgLocalLearningMemory(
          entries = listOf(
            NbgLocalLearningMemoryEntry(id = "m1", title = "fact", content = "project fact", updatedAtMs = 90L),
          ),
        ),
      ),
    )

    store.upsert(automation)
    val event = nbgRunScheduledAutomationNow(automation, context, nowMs = 200L)
    val saved = store.recordRun(event)

    assertEquals(NbgScheduleRunStatus.Succeeded, event.status)
    assertTrue(event.summary.contains("学习图谱报告"))
    assertTrue(event.evidenceRef.contains("local-only"))
    assertEquals(200L, saved.automations.single().lastRunAtMs)
    assertTrue(saved.automations.single().nextRunAtMs > 200L)
  }

  @Test
  fun scheduleRunNowBlocksConfirmationOnlyTasks() {
    val automation = nbgScheduledAutomation(
      title = "运行测试",
      template = NbgScheduledAutomationTemplate.TestCommand,
      prompt = "./gradlew test",
      nowMs = 100L,
    )

    val event = nbgRunScheduledAutomationNow(automation, nowMs = 200L)

    assertEquals(NbgScheduleRunStatus.Blocked, event.status)
    assertTrue(event.summary.contains("需要确认"))
  }

  @Test
  fun gatewayInboxDoesNotGrantExternalToolExecutionByDefault() {
    val local = nbgGatewayInboxMessage(
      source = NbgInboundMessageSource.LoopbackHttp,
      senderLabel = "127.0.0.1",
      text = "run readonly status",
      canExecuteTools = true,
      nowMs = 100L,
    )
    val external = nbgGatewayInboxMessage(
      source = NbgInboundMessageSource.ExternalPlatform,
      senderLabel = "telegram-user",
      text = "run readonly status",
      canExecuteTools = true,
      nowMs = 100L,
    )
    val sensitive = nbgGatewayInboxMessage(
      source = NbgInboundMessageSource.AndroidShareSheet,
      senderLabel = "share",
      text = "api_key=sk-live-secret-1234567890",
      canExecuteTools = false,
      nowMs = 100L,
    )

    assertTrue(local.canExecuteTools)
    assertEquals(NbgGatewayInboxStatus.Accepted, local.status)
    assertFalse(external.canExecuteTools)
    assertEquals(NbgGatewayInboxStatus.Received, external.status)
    assertEquals(NbgGatewayInboxStatus.Blocked, sensitive.status)
  }

  @Test
  fun gatewayStorePersistsAndArchivesMessages() {
    val storage = FakeGatewayStorage()
    val store = NbgGatewayInboxStore(storage)
    val message = nbgGatewayInboxMessage(
      source = NbgInboundMessageSource.AndroidShareSheet,
      senderLabel = "share",
      text = "Summarize this note",
      nowMs = 100L,
    )

    store.record(message)
    val archived = store.archive(message.id, nowMs = 200L)
    val reloaded = NbgGatewayInboxStore(FakeGatewayStorage(storage.raw)).load()

    assertEquals(0, archived.activeMessages.size)
    assertEquals(NbgGatewayInboxStatus.Archived, reloaded.messages.single().status)
    assertTrue(storage.raw.orEmpty().contains(NBG_GATEWAY_INBOX_STORE_VERSION))
  }

  @Test
  fun trajectoryExportRequiresEvidenceAndRedactsSensitiveText() {
    val history = HanakoHistorySnapshot(
      messages = listOf(
        HanakoHistoryMessage(id = 1L, role = "user", text = "hello", timestampMs = 1L),
        HanakoHistoryMessage(
          id = 2L,
          role = "tool",
          text = "",
          toolStatus = HanakoToolStatus(
            key = "tool-secret",
            title = "terminal",
            detail = "/root/private/file",
            status = "done",
            success = true,
          ),
        ),
      ),
    )
    val allowed = nbgBuildTrajectoryExportBundle(
      history = history,
      learningLog = NbgLearningAuditLog(),
      explicitUserTrigger = true,
      localOnlyDestination = true,
    )
    val blocked = nbgBuildTrajectoryExportBundle(
      history = history,
      learningLog = NbgLearningAuditLog(),
      explicitUserTrigger = false,
      localOnlyDestination = true,
    )

    assertTrue(allowed.review.allowExport)
    assertEquals(1, allowed.messages.size)
    assertEquals(1, allowed.toolEvents.size)
    assertFalse(allowed.toJsonString().contains("/root/private/file"))
    assertFalse(allowed.toJsonString().contains("tool-secret"))
    assertFalse(blocked.review.allowExport)
    assertEquals(0, blocked.messages.size)
  }
}

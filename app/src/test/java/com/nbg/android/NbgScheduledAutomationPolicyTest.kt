package com.nbg.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgScheduledAutomationPolicyTest {
  @Test
  fun readOnlySchedulesAutoEnableAndCommandSchedulesRequireConfirmation() {
    val readOnly = nbgScheduledAutomation(
      title = "每日摘要",
      template = NbgScheduledAutomationTemplate.DailyProjectSummary,
      prompt = "读取项目摘要。",
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
    assertFalse(command.enabled)
    assertTrue(command.requiresConfirmation)
  }
}

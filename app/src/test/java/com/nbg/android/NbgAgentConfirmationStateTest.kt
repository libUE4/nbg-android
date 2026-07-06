package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentConfirmationStateTest {
  @Test
  fun requestConfirmationStoresLatestPendingConfirmation() {
    val state = NbgAgentConfirmationState()
    val first = confirmation("confirm-1")
    val second = confirmation("confirm-2")

    state.requestConfirmation(first)
    assertSame(first, state.pendingConfirmation)

    state.requestConfirmation(second)
    assertSame(second, state.pendingConfirmation)
  }

  @Test
  fun resolveConfirmationOnlyClearsMatchingConfirmation() {
    val state = NbgAgentConfirmationState()
    val confirmation = confirmation("confirm-1")
    state.requestConfirmation(confirmation)

    state.resolveConfirmation("confirm-2")
    assertSame(confirmation, state.pendingConfirmation)

    state.resolveConfirmation("confirm-1")
    assertNull(state.pendingConfirmation)
  }

  @Test
  fun clearPendingConfirmationClearsAnyPendingConfirmation() {
    val state = NbgAgentConfirmationState()
    state.requestConfirmation(confirmation("confirm-1"))

    state.clearPendingConfirmation()

    assertNull(state.pendingConfirmation)
  }

  @Test
  fun revertTurnConfirmationOpenAndDismissAreTrackedSeparately() {
    val state = NbgAgentConfirmationState()

    assertFalse(state.revertTurnConfirmOpen)

    state.openRevertTurnConfirm()
    assertTrue(state.revertTurnConfirmOpen)

    state.dismissRevertTurnConfirm()
    assertFalse(state.revertTurnConfirmOpen)
  }

  @Test
  fun clearingPendingConfirmationDoesNotDismissRevertTurnConfirmation() {
    val state = NbgAgentConfirmationState()
    state.requestConfirmation(confirmation("confirm-1"))
    state.openRevertTurnConfirm()

    state.clearPendingConfirmation()

    assertNull(state.pendingConfirmation)
    assertTrue(state.revertTurnConfirmOpen)
  }

  @Test
  fun operatePermissionWarningGatesOperatePerSessionUntilAccepted() {
    val state = NbgAgentConfirmationState()

    assertTrue(state.shouldGateOperatePermissionMode(NBG_PERMISSION_MODE_OPERATE, "session-a.json"))
    assertFalse(state.shouldGateOperatePermissionMode(NBG_PERMISSION_MODE_ASK, "session-a.json"))
    assertTrue(state.shouldAutoPromptOperatePermissionWarning(NBG_PERMISSION_MODE_OPERATE, "session-a.json"))

    state.requestOperatePermissionWarning("session-a.json")
    assertTrue(state.operatePermissionWarningOpen)
    assertEquals("session-a.json", state.operatePermissionWarningSessionPath)
    assertFalse(state.shouldAutoPromptOperatePermissionWarning(NBG_PERMISSION_MODE_OPERATE, "session-a.json"))

    state.dismissOperatePermissionWarning()
    assertFalse(state.operatePermissionWarningOpen)
    assertTrue(state.isOperatePermissionWarningDismissed("session-a.json"))
    assertFalse(state.shouldAutoPromptOperatePermissionWarning(NBG_PERMISSION_MODE_OPERATE, "session-a.json"))
    assertTrue(state.shouldGateOperatePermissionMode(NBG_PERMISSION_MODE_OPERATE, "session-a.json"))

    state.requestOperatePermissionWarning("session-a.json")
    state.acceptOperatePermissionWarning()
    assertFalse(state.operatePermissionWarningOpen)
    assertTrue(state.isOperatePermissionWarningAccepted("session-a.json"))
    assertFalse(state.shouldGateOperatePermissionMode(NBG_PERMISSION_MODE_OPERATE, "session-a.json"))
    assertTrue(state.shouldGateOperatePermissionMode(NBG_PERMISSION_MODE_OPERATE, "session-b.json"))

    state.requestOperatePermissionWarning("session-b.json")
    state.acceptOperatePermissionWarning()
    assertFalse(state.shouldGateOperatePermissionMode(NBG_PERMISSION_MODE_OPERATE, "session-a.json"))
    assertFalse(state.shouldGateOperatePermissionMode(NBG_PERMISSION_MODE_OPERATE, "session-b.json"))
    assertTrue(state.shouldGateOperatePermissionMode(NBG_PERMISSION_MODE_OPERATE, "session-c.json"))
  }

  @Test
  fun operatePermissionWarningDismissalIsTrackedPerSession() {
    val state = NbgAgentConfirmationState()

    state.requestOperatePermissionWarning("session-a.json")
    state.dismissOperatePermissionWarning()
    assertTrue(state.isOperatePermissionWarningDismissed("session-a.json"))
    assertFalse(state.shouldAutoPromptOperatePermissionWarning(NBG_PERMISSION_MODE_OPERATE, "session-a.json"))

    state.requestOperatePermissionWarning("session-b.json")
    state.dismissOperatePermissionWarning()
    assertTrue(state.isOperatePermissionWarningDismissed("session-a.json"))
    assertTrue(state.isOperatePermissionWarningDismissed("session-b.json"))
    assertFalse(state.shouldAutoPromptOperatePermissionWarning(NBG_PERMISSION_MODE_OPERATE, "session-a.json"))
    assertFalse(state.shouldAutoPromptOperatePermissionWarning(NBG_PERMISSION_MODE_OPERATE, "session-b.json"))

    state.requestOperatePermissionWarning("session-a.json")
    assertFalse(state.isOperatePermissionWarningDismissed("session-a.json"))
    assertTrue(state.isOperatePermissionWarningDismissed("session-b.json"))
  }

  private fun confirmation(confirmId: String): HanakoConfirmation =
    HanakoConfirmation(
      confirmId = confirmId,
      title = "确认操作",
      body = "是否继续",
      subjectLabel = "文件写入",
      subjectDetail = "/root/app.kt",
      severity = "medium",
      confirmLabel = "同意",
      rejectLabel = "拒绝",
    )
}

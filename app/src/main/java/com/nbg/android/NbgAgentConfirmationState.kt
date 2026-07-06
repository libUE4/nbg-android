package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentConfirmationState {
  var pendingConfirmation by mutableStateOf<HanakoConfirmation?>(null)
    private set
  var revertTurnConfirmOpen by mutableStateOf(false)
    private set
  var operatePermissionWarningOpen by mutableStateOf(false)
    private set
  var operatePermissionWarningSessionPath by mutableStateOf<String?>(null)
    private set
  private val acceptedOperatePermissionWarningSessionKeys = mutableStateListOf<String>()
  private val dismissedOperatePermissionWarningSessionKeys = mutableStateListOf<String>()

  fun requestConfirmation(confirmation: HanakoConfirmation) {
    pendingConfirmation = confirmation
  }

  fun resolveConfirmation(confirmId: String) {
    if (pendingConfirmation?.confirmId == confirmId) {
      pendingConfirmation = null
    }
  }

  fun clearPendingConfirmation() {
    pendingConfirmation = null
  }

  fun openRevertTurnConfirm() {
    revertTurnConfirmOpen = true
  }

  fun dismissRevertTurnConfirm() {
    revertTurnConfirmOpen = false
  }

  fun shouldGateOperatePermissionMode(mode: String, sessionPath: String?): Boolean =
    nbgNormalizePermissionMode(mode) == NBG_PERMISSION_MODE_OPERATE &&
      !isOperatePermissionWarningAccepted(sessionPath)

  fun shouldAutoPromptOperatePermissionWarning(mode: String, sessionPath: String?): Boolean =
    shouldGateOperatePermissionMode(mode, sessionPath) &&
      !isOperatePermissionWarningDismissed(sessionPath) &&
      !operatePermissionWarningOpen

  fun isOperatePermissionWarningAccepted(sessionPath: String?): Boolean =
    operatePermissionWarningSessionKey(sessionPath) in acceptedOperatePermissionWarningSessionKeys

  fun isOperatePermissionWarningDismissed(sessionPath: String?): Boolean =
    operatePermissionWarningSessionKey(sessionPath) in dismissedOperatePermissionWarningSessionKeys

  fun requestOperatePermissionWarning(sessionPath: String?) {
    val key = operatePermissionWarningSessionKey(sessionPath)
    operatePermissionWarningSessionPath = sessionPath
    dismissedOperatePermissionWarningSessionKeys.remove(key)
    operatePermissionWarningOpen = true
  }

  fun acceptOperatePermissionWarning() {
    val key = operatePermissionWarningSessionKey(operatePermissionWarningSessionPath)
    if (key !in acceptedOperatePermissionWarningSessionKeys) {
      acceptedOperatePermissionWarningSessionKeys += key
    }
    dismissedOperatePermissionWarningSessionKeys.remove(key)
    operatePermissionWarningSessionPath = null
    operatePermissionWarningOpen = false
  }

  fun dismissOperatePermissionWarning() {
    val key = operatePermissionWarningSessionKey(operatePermissionWarningSessionPath)
    if (key !in dismissedOperatePermissionWarningSessionKeys) {
      dismissedOperatePermissionWarningSessionKeys += key
    }
    operatePermissionWarningSessionPath = null
    operatePermissionWarningOpen = false
  }

  private fun operatePermissionWarningSessionKey(sessionPath: String?): String =
    sessionPath?.trim()?.takeIf { it.isNotEmpty() } ?: "current-session"
}

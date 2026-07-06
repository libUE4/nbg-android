package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentHistoryState {
  var selectedConversationPath by mutableStateOf<String?>(null)
    private set
  var requestedConversationPath by mutableStateOf<String?>(null)
    private set
  private var suppressedHistoryPaths by mutableStateOf<Set<String>>(emptySet())
  var deferredHistoryLoaded by mutableStateOf<HanakoChatEvent.HistoryLoaded?>(null)
    private set
  val pendingDeferredHistorySystemMessages = mutableStateListOf<String>()
  var historyRenderVersion by mutableStateOf(0L)
    private set

  fun resetConversationUi() {
    clearDeferredHistoryLoaded()
    selectedConversationPath = null
    requestedConversationPath = null
    historyRenderVersion = 0L
  }

  fun resetHistoryRenderVersion() {
    historyRenderVersion = 0L
  }

  fun markHistoryRendered() {
    historyRenderVersion += 1L
  }

  fun appendDeferredSystemMessageIfNeeded(text: String) {
    if (deferredHistoryLoaded != null) {
      pendingDeferredHistorySystemMessages += text
    }
  }

  fun clearDeferredHistoryLoaded() {
    deferredHistoryLoaded = null
    pendingDeferredHistorySystemMessages.clear()
  }

  fun takeDeferredSystemMessages(): List<String> =
    pendingDeferredHistorySystemMessages.toList()

  fun beginApplyingHistory(event: HanakoChatEvent.HistoryLoaded): List<String> {
    val deferredSystemMessages = takeDeferredSystemMessages()
    selectedConversationPath = event.sessionPath
    requestedConversationPath = null
    clearDeferredHistoryLoaded()
    return deferredSystemMessages
  }

  fun shouldAcceptHistoryLoaded(event: HanakoChatEvent.HistoryLoaded): NbgHistoryAcceptDecision {
    if (event.sessionPath in suppressedHistoryPaths) {
      suppressedHistoryPaths = suppressedHistoryPaths - event.sessionPath
      return NbgHistoryAcceptDecision.RejectSuppressed
    }
    val requestedPath = requestedConversationPath
    if (requestedPath != null) {
      if (requestedPath == event.sessionPath) {
        return NbgHistoryAcceptDecision.Accept
      }
      if (requestedPath == HANA_PENDING_NEW_SESSION_PATH && event.sessionPath != selectedConversationPath) {
        return NbgHistoryAcceptDecision.Accept
      }
      return NbgHistoryAcceptDecision.RejectStaleRequested(requestedPath)
    }
    val activePath = selectedConversationPath ?: return NbgHistoryAcceptDecision.Accept
    if (event.sessionPath == activePath) return NbgHistoryAcceptDecision.Accept
    return NbgHistoryAcceptDecision.RejectStaleActive(activePath)
  }

  fun deferHistoryLoaded(event: HanakoChatEvent.HistoryLoaded) {
    deferredHistoryLoaded = event
  }

  fun clearDeferredHistoryIfSession(sessionPath: String) {
    if (deferredHistoryLoaded?.sessionPath == sessionPath) clearDeferredHistoryLoaded()
  }

  fun applySessionDeleted(sessionPath: String): Boolean {
    suppressedHistoryPaths = suppressedHistoryPaths + sessionPath
    clearDeferredHistoryIfSession(sessionPath)
    if (selectedConversationPath != sessionPath) return false
    requestedConversationPath = null
    selectedConversationPath = null
    return true
  }

  fun applySessionSelectFailed(sessionPath: String, previousSessionPath: String?) {
    clearDeferredHistoryIfSession(sessionPath)
    if (requestedConversationPath == sessionPath) {
      requestedConversationPath = null
    }
    if (selectedConversationPath == sessionPath && previousSessionPath != null) {
      selectedConversationPath = previousSessionPath
    }
  }

  fun syncSessionPath(path: String?) {
    if (path == null || deferredHistoryLoaded?.sessionPath != path) {
      clearDeferredHistoryLoaded()
    }
    if (path != null && requestedConversationPath == null && selectedConversationPath != path) {
      selectedConversationPath = path
    }
    if (path != null && requestedConversationPath == path) {
      requestedConversationPath = null
    }
  }

  fun clearPendingNewSessionRequestIfSettled(
    selectingSessionPath: String?,
    compressing: Boolean,
    sessionPath: String?,
  ) {
    if (
      requestedConversationPath == HANA_PENDING_NEW_SESSION_PATH &&
      selectingSessionPath == null &&
      !compressing &&
      selectedConversationPath == sessionPath
    ) {
      requestedConversationPath = null
    }
  }

  fun requestConversation(path: String) {
    clearDeferredHistoryLoaded()
    requestedConversationPath = path
    suppressedHistoryPaths = suppressedHistoryPaths - path
  }

  fun requestNewConversation() {
    requestedConversationPath = HANA_PENDING_NEW_SESSION_PATH
  }

  fun clearDeferredBeforeNewSession() {
    clearDeferredHistoryLoaded()
  }
}

internal sealed class NbgHistoryAcceptDecision {
  data object Accept : NbgHistoryAcceptDecision()
  data object RejectSuppressed : NbgHistoryAcceptDecision()
  data class RejectStaleRequested(val requestedPath: String) : NbgHistoryAcceptDecision()
  data class RejectStaleActive(val activePath: String) : NbgHistoryAcceptDecision()
}

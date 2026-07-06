package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentHistoryStateTest {
  @Test
  fun requestConversationAcceptsRequestedHistoryAndClearsSuppression() {
    val state = NbgAgentHistoryState()
    val event = historyLoaded("/sessions/a")

    assertFalse(state.applySessionDeleted("/sessions/a"))
    state.requestConversation("/sessions/a")

    assertEquals("/sessions/a", state.requestedConversationPath)
    assertSame(NbgHistoryAcceptDecision.Accept, state.shouldAcceptHistoryLoaded(event))
  }

  @Test
  fun pendingNewSessionAcceptsDifferentSessionAndSettlesWhenControllerCatchesUp() {
    val state = NbgAgentHistoryState()
    state.syncSessionPath("/sessions/old")
    state.requestNewConversation()

    val event = historyLoaded("/sessions/new")

    assertSame(NbgHistoryAcceptDecision.Accept, state.shouldAcceptHistoryLoaded(event))
    assertEquals(HANA_PENDING_NEW_SESSION_PATH, state.requestedConversationPath)

    state.beginApplyingHistory(event)
    assertEquals("/sessions/new", state.selectedConversationPath)
    assertNull(state.requestedConversationPath)
  }

  @Test
  fun staleRequestedAndStaleActiveHistoriesAreRejectedWithContext() {
    val requested = NbgAgentHistoryState()
    requested.requestConversation("/sessions/wanted")

    val requestedDecision = requested.shouldAcceptHistoryLoaded(historyLoaded("/sessions/stale"))
    assertTrue(requestedDecision is NbgHistoryAcceptDecision.RejectStaleRequested)
    assertEquals("/sessions/wanted", (requestedDecision as NbgHistoryAcceptDecision.RejectStaleRequested).requestedPath)

    val active = NbgAgentHistoryState()
    active.syncSessionPath("/sessions/active")

    val activeDecision = active.shouldAcceptHistoryLoaded(historyLoaded("/sessions/other"))
    assertTrue(activeDecision is NbgHistoryAcceptDecision.RejectStaleActive)
    assertEquals("/sessions/active", (activeDecision as NbgHistoryAcceptDecision.RejectStaleActive).activePath)
  }

  @Test
  fun deletedSessionSuppressesNextHistoryAndClearsActiveSelection() {
    val state = NbgAgentHistoryState()
    state.syncSessionPath("/sessions/a")
    state.deferHistoryLoaded(historyLoaded("/sessions/a"))
    state.appendDeferredSystemMessageIfNeeded("pending")

    assertTrue(state.applySessionDeleted("/sessions/a"))

    assertNull(state.selectedConversationPath)
    assertNull(state.requestedConversationPath)
    assertNull(state.deferredHistoryLoaded)
    assertTrue(state.pendingDeferredHistorySystemMessages.isEmpty())
    assertSame(
      NbgHistoryAcceptDecision.RejectSuppressed,
      state.shouldAcceptHistoryLoaded(historyLoaded("/sessions/a")),
    )
  }

  @Test
  fun sessionSelectFailedClearsRequestAndRestoresPreviousSelection() {
    val state = NbgAgentHistoryState()
    state.syncSessionPath("/sessions/previous")
    state.requestConversation("/sessions/failed")
    state.beginApplyingHistory(historyLoaded("/sessions/failed"))

    state.applySessionSelectFailed("/sessions/failed", "/sessions/previous")

    assertEquals("/sessions/previous", state.selectedConversationPath)
    assertNull(state.requestedConversationPath)
  }

  @Test
  fun deferredSystemMessagesAreCapturedAndReturnedWhenHistoryApplies() {
    val state = NbgAgentHistoryState()
    val deferred = historyLoaded("/sessions/a")
    state.deferHistoryLoaded(deferred)

    state.appendDeferredSystemMessageIfNeeded("first")
    state.appendDeferredSystemMessageIfNeeded("second")

    val systemMessages = state.beginApplyingHistory(deferred)

    assertEquals(listOf("first", "second"), systemMessages)
    assertNull(state.deferredHistoryLoaded)
    assertTrue(state.pendingDeferredHistorySystemMessages.isEmpty())
    assertEquals("/sessions/a", state.selectedConversationPath)
  }

  @Test
  fun sessionPathSyncAndPendingNewRequestSettlementMirrorUiRules() {
    val state = NbgAgentHistoryState()

    state.syncSessionPath("/sessions/a")
    assertEquals("/sessions/a", state.selectedConversationPath)

    state.requestNewConversation()
    state.clearPendingNewSessionRequestIfSettled(
      selectingSessionPath = null,
      compressing = false,
      sessionPath = "/sessions/a",
    )
    assertNull(state.requestedConversationPath)

    state.requestNewConversation()
    state.clearPendingNewSessionRequestIfSettled(
      selectingSessionPath = HANA_PENDING_NEW_SESSION_PATH,
      compressing = false,
      sessionPath = "/sessions/a",
    )
    assertEquals(HANA_PENDING_NEW_SESSION_PATH, state.requestedConversationPath)
  }

  @Test
  fun historyRenderVersionCanBeMarkedAndReset() {
    val state = NbgAgentHistoryState()

    state.markHistoryRendered()
    state.markHistoryRendered()
    assertEquals(2L, state.historyRenderVersion)

    state.resetHistoryRenderVersion()
    assertEquals(0L, state.historyRenderVersion)
  }

  private fun historyLoaded(path: String): HanakoChatEvent.HistoryLoaded =
    HanakoChatEvent.HistoryLoaded(path, messages = emptyList(), todos = emptyList(), sessionFiles = emptyList())
}

package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentFileShareStateTest {
  @Test
  fun initialStateIsClosedAndEmpty() {
    val state = NbgAgentFileShareState()

    assertFalse(state.isOpen)
    assertFalse(state.hasServerState)
    assertNull(state.serverState)
  }

  @Test
  fun openStoresServerStateAndShowsDialog() {
    val state = NbgAgentFileShareState()
    val server = serverState(port = 43211)

    state.open(server)

    assertTrue(state.isOpen)
    assertTrue(state.hasServerState)
    assertSame(server, state.serverState)
  }

  @Test
  fun applyServerStateUpdatesCurrentServerStateWithoutChangingVisibility() {
    val state = NbgAgentFileShareState()
    val first = serverState(port = 43211)
    val second = serverState(port = 43212)

    state.applyServerState(first)
    assertFalse(state.isOpen)
    assertSame(first, state.serverState)

    state.open(first)
    state.applyServerState(second)

    assertTrue(state.isOpen)
    assertSame(second, state.serverState)
    assertEquals("127.0.0.1:43212", state.serverState?.localUrl)
  }

  @Test
  fun dismissClosesDialogWithoutClearingServerState() {
    val state = NbgAgentFileShareState()
    val server = serverState(port = 43211)
    state.open(server)

    state.dismiss()

    assertFalse(state.isOpen)
    assertTrue(state.hasServerState)
    assertSame(server, state.serverState)
  }

  private fun serverState(port: Int): NbgFileShareServerState =
    NbgFileShareServerState(
      running = true,
      port = port,
      localUrl = "127.0.0.1:$port",
      username = "nbg",
      password = "secret",
      hostRoot = "/root",
    )
}

package com.nbg.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentStreamingControllerStateTest {
  @Test
  fun initialStateIsIdle() {
    val state = NbgAgentStreamingControllerState()

    assertFalse(state.streaming)
  }

  @Test
  fun markStreamingSetsStreaming() {
    val state = NbgAgentStreamingControllerState()

    state.markStreaming()

    assertTrue(state.streaming)
  }

  @Test
  fun markIdleClearsStreaming() {
    val state = NbgAgentStreamingControllerState()
    state.markStreaming()

    state.markIdle()

    assertFalse(state.streaming)
  }

  @Test
  fun syncFromHanakoStoresBackendStreamingFlag() {
    val state = NbgAgentStreamingControllerState()

    state.syncFromHanako(true)
    assertTrue(state.streaming)

    state.syncFromHanako(false)
    assertFalse(state.streaming)
  }
}

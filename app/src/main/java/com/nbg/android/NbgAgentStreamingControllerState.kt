package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentStreamingControllerState {
  var streaming by mutableStateOf(false)
    private set

  fun markStreaming() {
    streaming = true
  }

  fun markIdle() {
    streaming = false
  }

  fun syncFromHanako(isStreaming: Boolean) {
    streaming = isStreaming
  }
}

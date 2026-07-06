package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentModelConfigState {
  var config by mutableStateOf<HanakoAgentModelConfig?>(null)
    private set
  var loading by mutableStateOf(false)
    private set
  var error by mutableStateOf<String?>(null)
    private set

  fun beginLoad() {
    loading = true
    error = null
  }

  fun applyLoaded(nextConfig: HanakoAgentModelConfig) {
    config = nextConfig
    loading = false
    error = null
  }

  fun applyFailed(message: String) {
    loading = false
    error = message
  }

  fun recordLocalError(message: String) {
    error = message
  }
}

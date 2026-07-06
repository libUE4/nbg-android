package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentUrlApiSelectionState {
  var selectedModel by mutableStateOf<NbgSelectedUrlApiModel?>(null)
    private set

  var restoredDefaultModel by mutableStateOf(false)
    private set

  var restoredChatPreferences by mutableStateOf(false)
    private set

  fun select(model: NbgSelectedUrlApiModel?) {
    selectedModel = model
  }

  fun clearSelectedModel() {
    selectedModel = null
  }

  fun restoreDefaultModel(model: NbgSelectedUrlApiModel?) {
    selectedModel = model
    restoredDefaultModel = true
  }

  fun markChatPreferencesRestored() {
    restoredChatPreferences = true
  }
}

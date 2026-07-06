package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentChatPreferenceState {
  var preferences by mutableStateOf(NbgChatPreferences())
    private set

  var loaded by mutableStateOf(false)
    private set

  fun applyLoaded(nextPreferences: NbgChatPreferences) {
    preferences = nextPreferences
    loaded = true
  }

  fun applySaved(nextPreferences: NbgChatPreferences) {
    preferences = nextPreferences
  }
}

package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentUrlApiEntriesState {
  var entries by mutableStateOf<List<NbgStoredApi>>(emptyList())
    private set

  var loaded by mutableStateOf(false)
    private set

  val hasEntries: Boolean
    get() = entries.isNotEmpty()

  fun applyLoaded(nextEntries: List<NbgStoredApi>) {
    entries = nextEntries
    loaded = true
  }

  fun applySaved(nextEntries: List<NbgStoredApi>) {
    entries = nextEntries
  }
}

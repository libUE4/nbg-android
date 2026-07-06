package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentFileShareState {
  var serverState by mutableStateOf<NbgFileShareServerState?>(null)
    private set
  var isOpen by mutableStateOf(false)
    private set

  val hasServerState: Boolean
    get() = serverState != null

  fun open(nextState: NbgFileShareServerState) {
    serverState = nextState
    isOpen = true
  }

  fun applyServerState(nextState: NbgFileShareServerState) {
    serverState = nextState
  }

  fun dismiss() {
    isOpen = false
  }
}

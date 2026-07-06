package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentShellState(initialPage: NbgShellPage) {
  var page by mutableStateOf(initialPage)
    private set

  fun showPage(nextPage: NbgShellPage) {
    page = nextPage
  }

  fun showChat() {
    showPage(NbgShellPage.Chat)
  }
}

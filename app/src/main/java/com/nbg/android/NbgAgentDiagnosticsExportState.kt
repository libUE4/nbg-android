package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentDiagnosticsExportState {
  var exportText by mutableStateOf("")
    private set
  var isOpen by mutableStateOf(false)
    private set

  val hasExport: Boolean
    get() = exportText.isNotBlank()

  fun open(exportText: String) {
    this.exportText = exportText
    isOpen = true
  }

  fun dismiss() {
    isOpen = false
  }
}

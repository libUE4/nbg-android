package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentDiagnosticsExportStateTest {
  @Test
  fun initialStateIsClosedAndEmpty() {
    val state = NbgAgentDiagnosticsExportState()

    assertFalse(state.isOpen)
    assertFalse(state.hasExport)
    assertEquals("", state.exportText)
  }

  @Test
  fun openStoresExportTextAndShowsDialog() {
    val state = NbgAgentDiagnosticsExportState()

    state.open("{\"schema\":\"nbg-diagnostics-v1\"}")

    assertTrue(state.isOpen)
    assertTrue(state.hasExport)
    assertEquals("{\"schema\":\"nbg-diagnostics-v1\"}", state.exportText)
  }

  @Test
  fun openReplacesPreviousExportText() {
    val state = NbgAgentDiagnosticsExportState()

    state.open("first")
    state.open("second")

    assertTrue(state.isOpen)
    assertEquals("second", state.exportText)
  }

  @Test
  fun dismissClosesDialogWithoutClearingExportText() {
    val state = NbgAgentDiagnosticsExportState()
    state.open("diagnostics")

    state.dismiss()

    assertFalse(state.isOpen)
    assertTrue(state.hasExport)
    assertEquals("diagnostics", state.exportText)
  }
}

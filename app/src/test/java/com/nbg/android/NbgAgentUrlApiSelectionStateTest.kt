package com.nbg.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentUrlApiSelectionStateTest {
  @Test
  fun initialStateIsUnselectedAndNotRestored() {
    val state = NbgAgentUrlApiSelectionState()

    assertNull(state.selectedModel)
    assertFalse(state.restoredDefaultModel)
    assertFalse(state.restoredChatPreferences)
  }

  @Test
  fun selectStoresSelectedModel() {
    val state = NbgAgentUrlApiSelectionState()
    val model = selectedModel()

    state.select(model)

    assertSame(model, state.selectedModel)
  }

  @Test
  fun clearSelectedModelOnlyClearsSelection() {
    val state = NbgAgentUrlApiSelectionState()
    state.restoreDefaultModel(selectedModel("default"))
    state.markChatPreferencesRestored()

    state.clearSelectedModel()

    assertNull(state.selectedModel)
    assertTrue(state.restoredDefaultModel)
    assertTrue(state.restoredChatPreferences)
  }

  @Test
  fun restoreDefaultModelStoresSelectionAndMarksAttempted() {
    val state = NbgAgentUrlApiSelectionState()
    val model = selectedModel("default")

    state.restoreDefaultModel(model)

    assertSame(model, state.selectedModel)
    assertTrue(state.restoredDefaultModel)
  }

  @Test
  fun restoreDefaultModelWithNullStillMarksAttempted() {
    val state = NbgAgentUrlApiSelectionState()

    state.restoreDefaultModel(null)

    assertNull(state.selectedModel)
    assertTrue(state.restoredDefaultModel)
  }

  @Test
  fun markChatPreferencesRestoredSetsFlagWithoutChangingSelection() {
    val state = NbgAgentUrlApiSelectionState()
    val model = selectedModel()
    state.select(model)

    state.markChatPreferencesRestored()

    assertSame(model, state.selectedModel)
    assertTrue(state.restoredChatPreferences)
  }

  private fun selectedModel(id: String = "model-1"): NbgSelectedUrlApiModel =
    NbgSelectedUrlApiModel(
      providerId = "urlapi-provider",
      modelId = id,
      label = "Model $id",
    )
}

package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentChatPreferenceStateTest {
  @Test
  fun initialStateUsesDefaultPreferencesAndIsNotLoaded() {
    val state = NbgAgentChatPreferenceState()

    assertEquals(NbgChatPreferences(), state.preferences)
    assertFalse(state.loaded)
  }

  @Test
  fun applyLoadedStoresPreferencesAndMarksLoaded() {
    val state = NbgAgentChatPreferenceState()
    val preferences = preferences(modelId = "gpt-5.5", modelLabel = "GPT-5.5")

    state.applyLoaded(preferences)

    assertSame(preferences, state.preferences)
    assertTrue(state.loaded)
  }

  @Test
  fun applySavedUpdatesPreferencesWithoutMarkingUnloadedStateLoaded() {
    val state = NbgAgentChatPreferenceState()
    val preferences = preferences(permissionMode = NBG_PERMISSION_MODE_OPERATE)

    state.applySaved(preferences)

    assertSame(preferences, state.preferences)
    assertFalse(state.loaded)
  }

  @Test
  fun applySavedPreservesLoadedFlagAfterInitialLoad() {
    val state = NbgAgentChatPreferenceState()
    state.applyLoaded(preferences(modelId = "gpt-5.4"))
    val saved = preferences(modelId = "gpt-5.5", thinkingLevel = "high")

    state.applySaved(saved)

    assertSame(saved, state.preferences)
    assertTrue(state.loaded)
  }

  @Test
  fun applyLoadedCanReplacePreviouslySavedPreferences() {
    val state = NbgAgentChatPreferenceState()
    state.applySaved(preferences(modelId = "local"))
    val loaded = preferences(modelId = "stored", modelLabel = "Stored")

    state.applyLoaded(loaded)

    assertSame(loaded, state.preferences)
    assertTrue(state.loaded)
  }

  private fun preferences(
    modelProvider: String = "openai",
    modelId: String = "",
    modelLabel: String = "",
    permissionMode: String = NBG_DEFAULT_PERMISSION_MODE,
    thinkingLevel: String = "auto",
  ): NbgChatPreferences =
    NbgChatPreferences(
      modelProvider = modelProvider,
      modelId = modelId,
      modelLabel = modelLabel,
      permissionMode = permissionMode,
      thinkingLevel = thinkingLevel,
    )
}

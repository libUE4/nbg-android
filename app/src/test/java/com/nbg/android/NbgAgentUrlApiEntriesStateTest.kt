package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentUrlApiEntriesStateTest {
  @Test
  fun initialStateIsEmptyAndNotLoaded() {
    val state = NbgAgentUrlApiEntriesState()

    assertTrue(state.entries.isEmpty())
    assertFalse(state.hasEntries)
    assertFalse(state.loaded)
  }

  @Test
  fun applyLoadedStoresEntriesAndMarksLoaded() {
    val state = NbgAgentUrlApiEntriesState()
    val entries = listOf(api("one"), api("two"))

    state.applyLoaded(entries)

    assertSame(entries, state.entries)
    assertTrue(state.hasEntries)
    assertTrue(state.loaded)
  }

  @Test
  fun applySavedStoresEntriesWithoutMarkingUnloadedStateLoaded() {
    val state = NbgAgentUrlApiEntriesState()
    val entries = listOf(api("one"))

    state.applySaved(entries)

    assertSame(entries, state.entries)
    assertTrue(state.hasEntries)
    assertFalse(state.loaded)
  }

  @Test
  fun applySavedPreservesLoadedFlagAfterInitialLoad() {
    val state = NbgAgentUrlApiEntriesState()
    state.applyLoaded(listOf(api("one")))
    val updatedEntries = listOf(api("two"))

    state.applySaved(updatedEntries)

    assertSame(updatedEntries, state.entries)
    assertTrue(state.loaded)
  }

  @Test
  fun applySavedWithEmptyListClearsEntriesButKeepsLoadedFlag() {
    val state = NbgAgentUrlApiEntriesState()
    state.applyLoaded(listOf(api("one")))

    state.applySaved(emptyList())

    assertEquals(emptyList<NbgStoredApi>(), state.entries)
    assertFalse(state.hasEntries)
    assertTrue(state.loaded)
  }

  private fun api(id: String): NbgStoredApi =
    NbgStoredApi(
      id = id,
      name = "API $id",
      baseUrl = "https://api.example.com/$id",
      apiKey = "key-$id",
      models = listOf(NbgApiModel(id = "model-$id", label = "Model $id")),
      verifiedModelIds = setOf("model-$id"),
      selectedModelId = "model-$id",
    )
}

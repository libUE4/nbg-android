package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NbgAgentSoulStoreTest {
  private class FakeStorage : NbgAgentSoulStorage {
    var raw: String? = null
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  @Test
  fun soulStorePersistsDedupedRedactedPrinciplesAndStyleHints() {
    val storage = FakeStorage()
    val store = NbgAgentSoulStore(storage)

    store.save(
      nbgBuildAgentSoulConfig(
        principles = listOf("Be direct", "Be direct", "Never store api_key=sk-live-secret-1234567890"),
        styleHints = listOf("Use concise Chinese"),
        sourceEventIds = listOf("event-1", "event-1"),
        updatedAtMs = 42L,
      ),
    )
    val reloaded = NbgAgentSoulStore(storage).load()

    assertEquals(listOf("Be direct", "Never store api_key=[redacted]"), reloaded.principles)
    assertEquals(listOf("Use concise Chinese"), reloaded.styleHints)
    assertEquals(listOf("event-1"), reloaded.sourceEventIds)
    assertEquals(42L, reloaded.updatedAtMs)
    assertFalse(storage.raw.orEmpty().contains("sk-live"))
  }
}

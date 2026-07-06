package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NbgUserProfileStoreTest {
  private class FakeStorage : NbgUserProfileStorage {
    var raw: String? = null
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  @Test
  fun profileStoreNormalizesDedupesAndDropsSensitiveValues() {
    val storage = FakeStorage()
    val store = NbgUserProfileStore(storage)

    store.upsert(NbgUserProfileEntry(key = " My Style ", value = "Direct answers", updatedAtMs = 1L))
    store.upsert(NbgUserProfileEntry(key = "my_style", value = "Concise direct answers", updatedAtMs = 2L))
    store.upsert(NbgUserProfileEntry(key = "token", value = "api_key=sk-live-secret-1234567890", updatedAtMs = 3L))
    val reloaded = NbgUserProfileStore(storage).load()

    assertEquals(1, reloaded.visibleEntries.size)
    assertEquals("my_style", reloaded.visibleEntries.single().key)
    assertEquals("Concise direct answers", reloaded.visibleEntries.single().value)
    assertFalse(storage.raw.orEmpty().contains("sk-live"))
  }
}

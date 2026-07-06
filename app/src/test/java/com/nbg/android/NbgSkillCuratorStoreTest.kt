package com.nbg.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgSkillCuratorStoreTest {
  private class FakeStorage(raw: String? = null) : NbgSkillCuratorStorage {
    var raw: String? = raw

    override fun read(): String? = raw

    override fun write(raw: String) {
      this.raw = raw
    }
  }

  @Test
  fun storePersistsUsageArchiveAndRestoreMetadata() {
    val storage = FakeStorage()
    val store = NbgSkillCuratorStore(storage)

    store.recordUse("android-review", nowMs = 10L)
    val archived = store.archive("android-review", nowMs = 20L)
    val reloaded = NbgSkillCuratorStore(FakeStorage(storage.raw)).load()

    assertTrue(archived.statsFor("android-review")?.archived == true)
    assertEquals(1, reloaded.statsFor("android-review")?.useCount)
    assertEquals(20L, reloaded.statsFor("android-review")?.archivedAtMs)

    val restored = NbgSkillCuratorStore(FakeStorage(storage.raw)).restore("android-review")

    assertFalse(restored.statsFor("android-review")?.archived == true)
    assertEquals(1, restored.statsFor("android-review")?.useCount)
    assertEquals(
      NBG_SKILL_CURATOR_MODEL_VERSION,
      JSONObject(storage.raw.orEmpty()).getString("modelVersion"),
    )
  }
}

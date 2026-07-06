package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Test

class NbgLearningAuditStoreTest {
  private class FakeStorage : NbgLearningAuditStorage {
    var raw: String? = null
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  @Test
  fun auditStorePersistsEventsAndSupportsRevertStatus() {
    val storage = FakeStorage()
    val store = NbgLearningAuditStore(storage)
    val event = nbgLearningEventForCandidate(
      NbgLearningCandidate(
        id = "memory-1",
        kind = NbgLearningCandidateKind.Memory,
        title = "local",
        content = "Use local diagnostics.",
      ),
      nowMs = 10L,
    )

    store.record(event)
    val reverted = store.revert(event.id, nowMs = 20L)
    val reloaded = NbgLearningAuditStore(storage).load()

    assertEquals(NbgLearningEventStatus.Reverted, reverted.events.single().status)
    assertEquals(NbgLearningEventStatus.Reverted, reloaded.events.single().status)
    assertEquals(20L, reloaded.events.single().updatedAtMs)
  }
}

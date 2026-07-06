package com.nbg.android

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgLearnedSkillDraftStoreTest {
  private class FakeDraftStorage(raw: String? = null) : NbgLearnedSkillDraftStorage {
    var raw: String? = raw
    val writes = mutableListOf<String>()

    override fun read(): String? = raw

    override fun write(raw: String) {
      this.raw = raw
      writes += raw
    }
  }

  @Test
  fun storePersistsReviewOnlyDraftQueueAndReloadsIt() {
    val storage = FakeDraftStorage()
    val store = NbgLearnedSkillDraftStore(storage)
    val entry = nbgLearnedSkillDraftQueueEntry(
      id = "draft-1",
      skillName = "android-build",
      description = "Build and test before completion.",
      targetPath = "/root/.hanako/skills/android-build/SKILL.md",
      sourceTaskId = "task-1",
      completionEvidence = completeEvidence(),
      draftSha256 = "e".repeat(64),
      permissionTier = NbgPermissionRiskTier.High,
      createdAtMs = 10L,
      updatedAtMs = 20L,
    )

    val saved = store.upsert(entry)
    val reloaded = NbgLearnedSkillDraftStore(FakeDraftStorage(storage.raw)).load()

    assertEquals(1, saved.pendingReviewCount)
    assertEquals(0, saved.installableCount)
    assertEquals(0, saved.enableableCount)
    assertEquals("android-build", reloaded.visibleEntries.single().skillName)
    assertTrue(reloaded.visibleEntries.single().review.allowDraft)
    assertFalse(reloaded.visibleEntries.single().review.allowInstall)
    assertFalse(reloaded.visibleEntries.single().review.allowEnable)
    assertFalse(storage.raw.orEmpty().contains("/root/.hanako/skills/android-build/SKILL.md"))
    assertTrue(storage.raw.orEmpty().contains("[local-path]"))
  }

  @Test
  fun storeDeduplicatesByDraftIdAndKeepsNewestEntry() {
    val store = NbgLearnedSkillDraftStore(FakeDraftStorage())
    val old = draft(id = "draft-1", skillName = "old-name", updatedAtMs = 10L)
    val newer = draft(id = "draft-1", skillName = "new-name", updatedAtMs = 50L)

    val saved = store.save(NbgLearnedSkillDraftQueue(listOf(old, newer)))

    assertEquals(1, saved.entries.size)
    assertEquals("new-name", saved.entries.single().skillName)
  }

  @Test
  fun storeRejectsDraftsWithoutDeletingAuditMetadata() {
    val storage = FakeDraftStorage()
    val store = NbgLearnedSkillDraftStore(storage)
    store.upsert(draft(id = "draft-1", skillName = "android-build", updatedAtMs = 20L))

    val rejected = store.reject("draft-1", nowMs = 80L)

    assertEquals(1, rejected.entries.size)
    assertEquals(0, rejected.visibleEntries.size)
    assertEquals(NbgLearnedSkillDraftStatus.Rejected, rejected.entries.single().status)
    assertEquals(80L, rejected.entries.single().updatedAtMs)
    assertEquals("rejected", JSONArray(storage.raw).getJSONObject(0).getString("status"))
  }

  private fun draft(id: String, skillName: String, updatedAtMs: Long): NbgLearnedSkillDraftQueueEntry =
    nbgLearnedSkillDraftQueueEntry(
      id = id,
      skillName = skillName,
      targetPath = "/root/.hanako/skills/$skillName/SKILL.md",
      sourceTaskId = "task-$id",
      completionEvidence = completeEvidence(),
      draftSha256 = "f".repeat(64),
      permissionTier = NbgPermissionRiskTier.High,
      createdAtMs = 1L,
      updatedAtMs = updatedAtMs,
    )

  private fun completeEvidence(): NbgTaskCompletionEvidenceBundle =
    NbgTaskCompletionEvidenceBundle(
      contractId = "task-skill",
      title = "完成 Skill 草稿",
      criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.TestResult, "测试")),
      evidence = listOf(
        nbgTaskEvidence(
          kind = NbgTaskCompletionCriterionKind.TestResult,
          state = NbgTaskCompletionEvidenceState.Passed,
          label = "测试",
        ),
      ),
    )
}

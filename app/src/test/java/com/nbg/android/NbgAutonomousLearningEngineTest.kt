package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAutonomousLearningEngineTest {
  private class FakeAuditStorage(raw: String? = null) : NbgLearningAuditStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  private class FakeLocalMemoryStorage(raw: String? = null) : NbgLocalLearningMemoryStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  private class FakeProfileStorage(raw: String? = null) : NbgUserProfileStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  private class FakeSoulStorage(raw: String? = null) : NbgAgentSoulStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  private class FakeDraftStorage(raw: String? = null) : NbgLearnedSkillDraftStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) {
      this.raw = raw
    }
  }

  @Test
  fun learningCandidatesExtractMemoryProfileAndSkillFromNaturalLanguage() {
    val candidates = nbgLearningCandidatesFromTurn(
      NbgLearningSourceTurn(
        userText = "记住：这个项目优先本地诊断。我的偏好：回答要直接。/learn 保存构建验证流程",
        sessionPath = "/private/session.jsonl",
        turnId = "turn-1",
      ),
      nowMs = 100L,
    )

    assertTrue(candidates.any { it.kind == NbgLearningCandidateKind.Memory })
    assertTrue(candidates.any { it.kind == NbgLearningCandidateKind.UserProfile })
    assertTrue(candidates.any { it.kind == NbgLearningCandidateKind.Skill })
    assertTrue(candidates.single { it.kind == NbgLearningCandidateKind.Skill }.draftSha256.matches(Regex("[a-f0-9]{64}")))
  }

  @Test
  fun engineAutoAppliesSafeMemoryProfileSoulAndQueuesSkillDraftForReview() {
    val engine = engine()

    val snapshot = engine.learnFromTurn(
      NbgLearningSourceTurn(
        userText = "记住：Use local-first diagnostics for beta support. 我的偏好：soul stay direct and concise. /learn Android build verification workflow",
        sessionPath = "/root/private/session.jsonl",
        turnId = "turn-1",
      ),
      nowMs = 200L,
    )

    assertEquals(3, snapshot.auditLog.events.size)
    assertTrue(snapshot.auditLog.events.any { it.status == NbgLearningEventStatus.AutoApplied && it.candidate.kind == NbgLearningCandidateKind.Memory })
    assertTrue(snapshot.auditLog.events.any { it.status == NbgLearningEventStatus.AutoApplied && it.candidate.kind == NbgLearningCandidateKind.UserProfile })
    assertTrue(snapshot.auditLog.events.any { it.status == NbgLearningEventStatus.PendingReview && it.candidate.kind == NbgLearningCandidateKind.Skill })
    assertEquals(1, snapshot.localMemory.enabledEntries.size)
    assertEquals(1, snapshot.userProfile.visibleEntries.size)
    assertEquals(1, snapshot.soul.principles.size)
    assertEquals(1, snapshot.learnedSkillDraftQueue.pendingReviewCount)
    assertEquals(0, snapshot.learnedSkillDraftQueue.installableCount)
    assertEquals(0, snapshot.learnedSkillDraftQueue.enableableCount)
  }

  @Test
  fun engineBlocksSensitiveLearningBeforePersistence() {
    val engine = engine()

    val snapshot = engine.learnFromTurn(
      NbgLearningSourceTurn(
        userText = "记住：api_key=sk-live-secret-1234567890",
        turnId = "turn-sensitive",
      ),
      nowMs = 300L,
    )

    assertEquals(1, snapshot.auditLog.events.size)
    assertEquals(NbgLearningEventStatus.Blocked, snapshot.auditLog.events.single().status)
    assertTrue(snapshot.auditLog.events.single().review.sensitiveFindings.isNotEmpty())
    assertEquals(0, snapshot.localMemory.entries.size)
  }

  @Test
  fun learningGraphLinksRelatedMemoryProfileAndSkillNodes() {
    val memory = nbgBuildLocalLearningMemory(
      listOf(
        NbgLocalLearningMemoryEntry(
          id = "m1",
          type = "project_fact",
          title = "android build",
          content = "Run android build verification locally.",
          updatedAtMs = 10L,
        ),
      ),
    )
    val profile = nbgBuildUserProfile(
      listOf(
        NbgUserProfileEntry(
          key = "android",
          value = "Prefer local build verification.",
          updatedAtMs = 20L,
        ),
      ),
    )
    val drafts = nbgBuildLearnedSkillDraftQueue(
      listOf(
        nbgLearnedSkillDraftQueueEntry(
          id = "draft-1",
          skillName = "android-build",
          description = "Local build verification workflow.",
          targetPath = "/root/.hanako/skills/android-build/SKILL.md",
          sourceTaskId = "task-1",
          completionEvidence = completeEvidence(),
          draftSha256 = "a".repeat(64),
          permissionTier = NbgPermissionRiskTier.High,
        ),
      ),
    )

    val graph = nbgBuildLearningGraph(memory, profile, NbgAgentSoulConfig(), drafts)

    assertEquals(3, graph.nodes.size)
    assertEquals(1, graph.memoryCount)
    assertEquals(1, graph.profileCount)
    assertEquals(1, graph.skillCount)
    assertTrue(graph.edges.isNotEmpty())
  }

  @Test
  fun localLearningMemoryStoreRedactsSessionPathAndReloads() {
    val storage = FakeLocalMemoryStorage()
    val store = NbgLocalLearningMemoryStore(storage)

    store.upsert(
      NbgLocalLearningMemoryEntry(
        id = "m1",
        title = "Local path",
        content = "Keep useful project fact.",
        sourceSessionPath = "/root/private/session.jsonl",
        updatedAtMs = 10L,
      ),
    )
    val reloaded = NbgLocalLearningMemoryStore(FakeLocalMemoryStorage(storage.raw)).load()

    assertEquals("m1", reloaded.entries.single().id)
    assertFalse(storage.raw.orEmpty().contains("/root/private/session.jsonl"))
    assertTrue(storage.raw.orEmpty().contains("[session-path]"))
  }

  private fun engine(): NbgAutonomousLearningEngine =
    NbgAutonomousLearningEngine(
      auditStore = NbgLearningAuditStore(FakeAuditStorage()),
      memoryStore = NbgLocalLearningMemoryStore(FakeLocalMemoryStorage()),
      userProfileStore = NbgUserProfileStore(FakeProfileStorage()),
      soulStore = NbgAgentSoulStore(FakeSoulStorage()),
      learnedSkillDraftStore = NbgLearnedSkillDraftStore(FakeDraftStorage()),
    )

  private fun completeEvidence(): NbgTaskCompletionEvidenceBundle =
    NbgTaskCompletionEvidenceBundle(
      contractId = "task-1",
      title = "Learning",
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

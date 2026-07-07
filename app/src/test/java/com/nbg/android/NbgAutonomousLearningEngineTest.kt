package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

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
  fun engineAutoAppliesSafeMemoryProfileSoulAndSafeSkillDraft() {
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
    assertTrue(snapshot.auditLog.events.any { it.status == NbgLearningEventStatus.AutoApplied && it.candidate.kind == NbgLearningCandidateKind.Skill })
    assertEquals(1, snapshot.localMemory.enabledEntries.size)
    assertEquals(1, snapshot.userProfile.visibleEntries.size)
    assertEquals(1, snapshot.soul.principles.size)
    assertEquals(0, snapshot.learnedSkillDraftQueue.pendingReviewCount)
    assertEquals(1, snapshot.learnedSkillDraftQueue.autoAppliedCount)
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
  fun completedAssistantTurnCreatesReusableMemoryFromFullTrajectory() {
    val engine = engine()

    val snapshot = engine.learnFromTurn(
      NbgLearningSourceTurn(
        userText = "修复 Android Learning 页面刷新问题",
        assistantText = "已实现完整轮次刷新，并且测试通过。Verification: BUILD SUCCESSFUL.",
        sessionPath = "/root/private/session.jsonl",
        turnId = "turn-complete",
      ),
      nowMs = 350L,
    )

    assertEquals(1, snapshot.auditLog.events.size)
    assertEquals(NbgLearningCandidateKind.Memory, snapshot.auditLog.events.single().candidate.kind)
    assertEquals(NbgLearningEventStatus.AutoApplied, snapshot.auditLog.events.single().status)
    assertEquals(1, snapshot.localMemory.enabledEntries.size)
    assertTrue(snapshot.localMemory.enabledEntries.single().content.contains("User request"))
    assertTrue(snapshot.localMemory.enabledEntries.single().tags.contains("turn-summary"))
  }

  @Test
  fun scheduleSuggestionIsAuditedWithoutRunningTools() {
    val engine = engine()

    val snapshot = engine.learnFromTurn(
      NbgLearningSourceTurn(
        userText = "以后每天生成学习审计",
        assistantText = "可以创建 daily scheduled automation，只读取学习审计，不执行命令。",
        turnId = "turn-schedule",
      ),
      nowMs = 360L,
    )

    assertEquals(1, snapshot.auditLog.events.size)
    assertEquals(NbgLearningCandidateKind.ScheduleSuggestion, snapshot.auditLog.events.single().candidate.kind)
    assertFalse(snapshot.auditLog.events.single().review.blocked)
    assertEquals(0, snapshot.localMemory.entries.size)
    assertEquals(0, snapshot.learnedSkillDraftQueue.visibleEntries.size)
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
    assertEquals(3, graph.stats.nodeCount)
    assertTrue(graph.stats.linkedNodeCount > 0)
    assertEquals(0, graph.stats.isolatedNodeCount)
  }

  @Test
  fun learningRecallRanksLocalMemoryProfileSkillDraftsAndSessions() {
    val memory = nbgBuildLocalLearningMemory(
      listOf(
        NbgLocalLearningMemoryEntry(
          id = "m1",
          type = "project_fact",
          title = "android build",
          content = "Run Gradle build verification locally before release.",
          sourceSessionPath = "/root/private/build.jsonl",
          updatedAtMs = 10L,
        ),
      ),
    )
    val profile = nbgBuildUserProfile(
      listOf(
        NbgUserProfileEntry(
          key = "verification",
          value = "Prefer local Gradle tests and direct reports.",
          updatedAtMs = 20L,
        ),
      ),
    )
    val drafts = nbgBuildLearnedSkillDraftQueue(
      listOf(
        nbgLearnedSkillDraftQueueEntry(
          id = "draft-1",
          skillName = "gradle-build-verification",
          description = "Gradle build verification workflow.",
          targetPath = "/root/.hanako/skills/gradle-build-verification/SKILL.md",
          sourceTaskId = "task-1",
          completionEvidence = completeEvidence(),
          draftSha256 = "b".repeat(64),
          permissionTier = NbgPermissionRiskTier.Medium,
        ),
      ),
    )
    val sessions = listOf(
      HanakoSessionSummaryIndexEntry(
        sessionPath = "/sessions/build.jsonl",
        title = "Gradle build fix",
        snippet = "Build verification passed after dependency cleanup.",
        updatedAtMs = 30L,
        messageCount = 4,
        todoCount = 1,
        fileCount = 2,
      ),
    )

    val recall = nbgBuildLearningRecallBundle(
      query = "gradle build verification",
      memory = memory,
      profile = profile,
      soul = NbgAgentSoulConfig(principles = listOf("Keep verification local.")),
      skillDrafts = drafts,
      sessions = sessions,
    )

    assertTrue(recall.hasResults)
    assertTrue(recall.items.any { it.kind == NbgLearningRecallKind.Memory })
    assertTrue(recall.items.any { it.kind == NbgLearningRecallKind.UserProfile })
    assertTrue(recall.items.any { it.kind == NbgLearningRecallKind.SkillDraft })
    assertTrue(recall.items.any { it.kind == NbgLearningRecallKind.Session })
    assertFalse(recall.items.joinToString("\n") { it.sourceRef }.contains("/root/private/build.jsonl"))
    assertTrue(recall.items.first().score >= recall.items.last().score)
  }

  @Test
  fun skillImprovementCandidateUsesToolEvidenceAndHighRiskStaysReviewGated() {
    val engine = engine()
    val tool = HanakoToolStatus(
      key = "term-1",
      kind = "terminal",
      toolName = "skill_runner",
      title = "Skill runner failed",
      subtitle = "Gradle verification",
      detail = "compile failed after generated workflow",
      status = "failed",
      success = false,
      terminalOutput = HanakoTerminalOutput(
        sessionId = "term-1",
        title = "./gradlew test",
        output = "compile failed",
        exitCode = 1,
      ),
    )

    val candidate = nbgSkillImprovementCandidateFromTool(tool, sessionPath = "/root/private/session.jsonl", nowMs = 400L)
    val snapshot = engine.learnSkillImprovementFromTool(tool, sessionPath = "/root/private/session.jsonl", nowMs = 400L)

    assertTrue(tool.shouldGenerateSkillImprovementCandidate())
    assertEquals(NbgLearningCandidateKind.SkillImprovement, candidate?.kind)
    assertEquals(1, snapshot.auditLog.events.size)
    assertEquals(NbgLearningCandidateKind.SkillImprovement, snapshot.auditLog.events.single().candidate.kind)
    assertEquals(1, snapshot.learnedSkillDraftQueue.visibleEntries.size)
    assertEquals(0, snapshot.learnedSkillDraftQueue.autoAppliedCount)
    assertEquals(1, snapshot.learnedSkillDraftQueue.pendingReviewCount)
    assertEquals(0, snapshot.learnedSkillDraftQueue.installableCount)
    assertEquals(0, snapshot.learnedSkillDraftQueue.enableableCount)
  }

  @Test
  fun runningToolsDoNotGenerateSkillImprovementNoise() {
    val running = HanakoToolStatus(
      key = "term-running",
      kind = "terminal",
      toolName = "skill_runner",
      title = "Skill runner",
      status = "running",
      running = true,
    )

    assertFalse(running.shouldGenerateSkillImprovementCandidate())
    assertEquals(null, nbgSkillImprovementCandidateFromTool(running))
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
      learnedSkillDraftStore = NbgLearnedSkillDraftStore(
        storage = FakeDraftStorage(),
        artifactRoot = createTempDirectory("nbg-engine-learned-skills").toFile(),
      ),
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

package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class NbgHermesLearningParityTest {
  private class FakeAuditStorage(raw: String? = null) : NbgLearningAuditStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) { this.raw = raw }
  }

  private class FakeLocalMemoryStorage(raw: String? = null) : NbgLocalLearningMemoryStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) { this.raw = raw }
  }

  private class FakeProfileStorage(raw: String? = null) : NbgUserProfileStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) { this.raw = raw }
  }

  private class FakeSoulStorage(raw: String? = null) : NbgAgentSoulStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) { this.raw = raw }
  }

  private class FakeDraftStorage(raw: String? = null) : NbgLearnedSkillDraftStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) { this.raw = raw }
  }

  private class FakeProviderStateStorage(raw: String? = null) : NbgMemoryProviderManagerStorage {
    var raw: String? = raw
    override fun read(): String? = raw
    override fun write(raw: String) { this.raw = raw }
  }

  @Test
  fun memoryProviderManagerPrefetchSyncAndQueueWrapLocalLearningEngine() {
    val providerStorage = FakeProviderStateStorage()
    val engine = engine()
    val manager = NbgMemoryProviderManager(engine, providerStorage)

    manager.syncAll(
      NbgLearningSourceTurn(
        userText = "记住：Gradle verification must run locally.",
        assistantText = "已完成并测试通过。",
        turnId = "turn-provider",
      ),
      nowMs = 100L,
    )
    val prefetched = manager.prefetchAll("Gradle verification", nowMs = 200L)
    val state = parseNbgMemoryProviderManagerState(providerStorage.raw)

    assertTrue(prefetched.hasContext)
    assertTrue(prefetched.blocks.single().text.contains("Gradle"))
    assertTrue(state.syncCount >= 1)
    assertTrue(state.prefetchCount >= 1)
    assertTrue(state.queuedPrefetchCount >= 1)
  }

  @Test
  fun journeyMutationsCanEditAndDeleteMemoryProfileSoulAndSkillDraftNodes() {
    val memoryStore = NbgLocalLearningMemoryStore(FakeLocalMemoryStorage())
    val profileStore = NbgUserProfileStore(FakeProfileStorage())
    val soulStore = NbgAgentSoulStore(FakeSoulStorage())
    val draftStore = NbgLearnedSkillDraftStore(FakeDraftStorage(), createTempDirectory("journey-skills").toFile())
    memoryStore.upsert(NbgLocalLearningMemoryEntry(id = "m1", title = "old", content = "old memory", updatedAtMs = 1L))
    profileStore.upsert(NbgUserProfileEntry(key = "style", value = "old style", updatedAtMs = 1L))
    soulStore.save(NbgAgentSoulConfig(principles = listOf("old soul"), updatedAtMs = 1L))
    draftStore.save(NbgLearnedSkillDraftQueue(listOf(draft("d1", "journey-skill"))))
    val mutations = NbgLearningJourneyMutations(memoryStore, profileStore, soulStore, draftStore, createTempDirectory("journey-installed").toFile())

    assertTrue(mutations.editNode("memory:m1", "new memory").ok)
    assertTrue(mutations.editNode("profile:style", "new style").ok)
    assertTrue(mutations.editNode("soul:0", "new soul").ok)
    assertTrue(mutations.deleteNode("skill:d1").ok)

    assertEquals("new memory", memoryStore.load().entries.single().content)
    assertEquals("new style", profileStore.load().visibleEntries.single().value)
    assertEquals("new soul", soulStore.load().principles.single())
    assertEquals(0, draftStore.load().visibleEntries.size)
  }

  @Test
  fun skillManageSupportsCreatePatchSupportFileAndArchiveInsideLocalSkillRoot() {
    val root = createTempDirectory("skill-manage").toFile()
    val manage = NbgSkillManage(root)

    val created = manage.apply(
      NbgSkillManageRequest(
        action = NbgSkillManageAction.Create,
        name = "Build Skill",
        content = "---\nname: build-skill\n---\nold step",
      ),
    )
    val patched = manage.apply(
      NbgSkillManageRequest(
        action = NbgSkillManageAction.Patch,
        name = "build-skill",
        oldString = "old step",
        newString = "new step",
      ),
    )
    val wrote = manage.apply(
      NbgSkillManageRequest(
        action = NbgSkillManageAction.WriteFile,
        name = "build-skill",
        filePath = "references/checklist.md",
        fileContent = "verify build",
      ),
    )
    val archived = manage.apply(NbgSkillManageRequest(action = NbgSkillManageAction.Delete, name = "build-skill"))

    assertTrue(created.ok)
    assertTrue(patched.ok)
    assertTrue(wrote.ok)
    assertTrue(archived.ok)
    assertTrue(File(root, ".nbg-archive").isDirectory)
  }

  @Test
  fun installedSkillGraphReadsRelatedSkillsAndLinksMemoryToInstalledSkill() {
    val root = createTempDirectory("installed-skills").toFile()
    File(root, "android-build").mkdirs()
    File(root, "android-build/SKILL.md").writeText(
      """
      ---
      name: android-build
      category: mobile
      related_skills: [gradle-test]
      description: Gradle build verification workflow
      ---
      """.trimIndent(),
      Charsets.UTF_8,
    )
    File(root, "gradle-test").mkdirs()
    File(root, "gradle-test/SKILL.md").writeText("---\nname: gradle-test\n---\n", Charsets.UTF_8)

    val graph = nbgBuildLearningGraph(
      memory = nbgBuildLocalLearningMemory(listOf(NbgLocalLearningMemoryEntry(id = "m1", title = "Gradle build", content = "Gradle build verification"))),
      profile = NbgUserProfile(),
      soul = NbgAgentSoulConfig(),
      skillDrafts = NbgLearnedSkillDraftQueue(),
      installedSkills = nbgReadInstalledSkillNodes(root),
    )

    assertTrue(graph.nodes.any { it.id == "skill-installed:android-build" })
    assertTrue(graph.edges.any { it.reason == "related_skills" })
    assertTrue(graph.edges.any { it.fromId == "memory:m1" && it.toId == "skill-installed:android-build" })
  }

  @Test
  fun curatorReviewArchivesUnusedDisabledLearnedSkillsOnly() {
    val snapshot = HanakoSkillsSnapshot(
      skills = listOf(
        HanakoSkillSummary(name = "learned-one", source = "user", enabled = false, filePath = "/data/data/app/files/learned-skills/learned-one/SKILL.md"),
        HanakoSkillSummary(name = "external-on", source = "external", enabled = false),
      ),
    )
    val drafts = nbgBuildLearnedSkillDraftQueue(listOf(draft("d1", "learned-one").copy(status = NbgLearnedSkillDraftStatus.AutoApplied, autoInstalled = true)))

    val review = nbgRunSkillCuratorReview(snapshot, drafts, NbgSkillCuratorMetadata(), nowMs = 500L)

    assertEquals(1, review.actions.size)
    assertEquals("learned-one", review.actions.single().skillName)
    assertTrue(review.metadata.statsFor("learned-one")?.archived == true)
    assertFalse(review.metadata.statsFor("external-on")?.archived == true)
  }

  private fun engine(): NbgAutonomousLearningEngine =
    NbgAutonomousLearningEngine(
      auditStore = NbgLearningAuditStore(FakeAuditStorage()),
      memoryStore = NbgLocalLearningMemoryStore(FakeLocalMemoryStorage()),
      userProfileStore = NbgUserProfileStore(FakeProfileStorage()),
      soulStore = NbgAgentSoulStore(FakeSoulStorage()),
      learnedSkillDraftStore = NbgLearnedSkillDraftStore(FakeDraftStorage(), createTempDirectory("provider-skills").toFile()),
      installedSkillRoot = createTempDirectory("provider-installed").toFile(),
    )

  private fun draft(id: String, skillName: String): NbgLearnedSkillDraftQueueEntry =
    nbgLearnedSkillDraftQueueEntry(
      id = id,
      skillName = skillName,
      description = "Reusable workflow",
      targetPath = "/root/.hanako/skills/$skillName/SKILL.md",
      sourceTaskId = "task-$id",
      completionEvidence = NbgTaskCompletionEvidenceBundle(
        contractId = "task-$id",
        title = "done",
        criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.TestResult, "test")),
        evidence = listOf(nbgTaskEvidence(NbgTaskCompletionCriterionKind.TestResult, NbgTaskCompletionEvidenceState.Passed, "test")),
      ),
      draftSha256 = "a".repeat(64),
      permissionTier = NbgPermissionRiskTier.High,
      createdAtMs = 1L,
      updatedAtMs = 1L,
    )
}

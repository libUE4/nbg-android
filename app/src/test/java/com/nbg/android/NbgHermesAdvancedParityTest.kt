package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class NbgHermesAdvancedParityTest {
  @Test
  fun skillDiffBuildsHunksAndCanMergeSelectedBlocks() {
    val before = "title\nold step\nkeep"
    val after = "title\nnew step\nkeep\nextra"

    val hunks = nbgBuildSkillDiffHunks(before, after, contextRadius = 0)
    val mergedFirst = nbgMergeSkillDiffHunks(before, after, hunks, setOf(0))
    val mergedAll = nbgMergeSkillDiffHunks(before, after, hunks, hunks.map { it.index }.toSet())

    assertTrue(hunks.isNotEmpty())
    assertTrue(hunks.sumOf { it.addedCount } >= 1)
    assertTrue(hunks.sumOf { it.removedCount } >= 1)
    assertTrue(mergedFirst.contains("new step") || mergedFirst.contains("extra"))
    assertEquals(after, mergedAll)
  }

  @Test
  fun skillDiffPreviewReadsLatestRollbackBackup() {
    val root = createTempDirectory("skill-diff").toFile()
    val skillDir = File(root, "android-build").also { it.mkdirs() }
    File(skillDir, "SKILL.md").writeText("title\nnew step", Charsets.UTF_8)
    val rollbackDir = File(skillDir, ".nbg-rollback").also { it.mkdirs() }
    File(rollbackDir, "SKILL.old.md").writeText("title\nold step", Charsets.UTF_8)

    val preview = NbgSkillDiffMerge(root).previewCurrent("android-build")

    assertTrue(preview.rollbackAvailable)
    assertTrue(preview.changed)
    assertTrue(preview.hunks.any { it.addedCount > 0 && it.removedCount > 0 })
  }

  @Test
  fun externalMemoryProvidersAreRegisteredButDisabledUntilConfigured() {
    val raw = JSONObject()
      .put(
        "providers",
        JSONArray().put(
          JSONObject()
            .put("id", "honcho")
            .put("kind", "honcho")
            .put("displayName", "Honcho")
            .put("enabled", true)
            .put("endpoint", ""),
        ),
      )
      .toString()

    val state = parseNbgExternalMemoryProviderState(raw)
    val honcho = state.providers.first { it.id == "honcho" }

    assertTrue(state.providers.any { it.kind == NbgExternalMemoryProviderKind.Mem0 })
    assertEquals(false, honcho.enabled)
    assertEquals("已关闭", honcho.statusLabel)
  }

  @Test
  fun modelProviderProfilesDetectSavedUrlApiEntries() {
    val state = nbgProfilesForStoredApis(
      listOf(
        NbgStoredApi(
          id = "entry1",
          name = "OpenRouter Main",
          baseUrl = "https://openrouter.ai/api/v1",
          apiKey = "secret",
          models = listOf(NbgApiModel("openai/gpt-5")),
          verifiedModelIds = setOf("openai/gpt-5"),
          selectedModelId = "openai/gpt-5",
        ),
      ),
    )

    assertTrue(state.profiles.any { it.id == "openrouter" && it.supportsThinking })
    assertTrue(state.profiles.any { it.id == "url-api-entry1" && it.label == "OpenRouter Main" })
  }

  @Test
  fun contextInsightsSummarizeUsageLearningAndCompressionAdvice() {
    val insights = nbgBuildContextInsights(
      usage = NbgContextUsageSnapshot(totalTokens = 80_000, contextLimit = 100_000, percentUsed = 80, compressionAvailable = true),
      learning = NbgAutonomousLearningSnapshot(
        localMemory = nbgBuildLocalLearningMemory(
          listOf(NbgLocalLearningMemoryEntry(id = "m1", title = "Gradle", content = "Run local tests")),
        ),
      ),
      sessions = listOf(HanakoSessionSummary(path = "/tmp/session", title = "Session", subtitle = "local")),
    )

    assertEquals(1, insights.sessionCount)
    assertEquals(1, insights.learningItemCount)
    assertEquals("当前会话可压缩", insights.suggestion)
    assertTrue(insights.usage.label.contains("80k"))
  }

  @Test
  fun curatorLoopStateParsesBackgroundReviewMetadata() {
    val state = parseNbgSkillCuratorLoopState(
      JSONObject()
        .put("enabled", true)
        .put("intervalMinutes", 30)
        .put("reviewCount", 2)
        .put("archivedCount", 1)
        .put("lastActionCount", 1)
        .toString(),
    )

    assertTrue(state.enabled)
    assertEquals(2, state.reviewCount)
    assertEquals("后台复核待运行", state.statusLabel)
  }
}

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
  fun externalMemoryAdapterExtractsProviderResponses() {
    val raw = JSONObject()
      .put(
        "results",
        JSONArray()
          .put(JSONObject().put("memory", "prefers local Gradle verification"))
          .put(JSONObject().put("metadata", JSONObject().put("ignored", true))),
      )
      .put("data", JSONObject().put("documents", JSONArray().put(JSONObject().put("summary", "uses Android agent daily"))))
      .toString()

    val texts = nbgExtractExternalMemoryTexts(raw)

    assertTrue(texts.any { it.contains("Gradle") })
    assertTrue(texts.any { it.contains("Android agent") })
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
  fun providerProfileBuildsEndpointsAndExtraBody() {
    val openRouter = nbgProfileForUrlApi("https://openrouter.ai/api/v1", "openai/gpt-5")
    val gemini = nbgProfileForUrlApi("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-pro")
    val body = gemini.applyExtraBody(JSONObject().put("model", "gemini-2.5-pro"))

    assertEquals("openrouter", openRouter.id)
    assertEquals("https://openrouter.ai/api/v1/chat/completions", openRouter.chatEndpoint("https://openrouter.ai/api/v1"))
    assertTrue(openRouter.supportsThinking)
    assertEquals("optional", body.getString("thinking_config"))
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
  fun contextCompressionStrategyTriggersAtThreshold() {
    val usage = NbgContextUsageSnapshot(
      totalTokens = 90_000,
      contextLimit = 100_000,
      percentUsed = 90,
      compressionAvailable = true,
    )
    val auto = NbgContextCompressionStrategy(
      mode = NbgContextCompressionMode.Auto,
      thresholdPercent = 85,
      minIntervalMs = 60_000,
      lastTriggeredAtMs = 0,
    )
    val off = auto.copy(mode = NbgContextCompressionMode.Off)

    assertTrue(auto.shouldTrigger(usage, nowMs = 120_000))
    assertEquals(false, off.shouldTrigger(usage, nowMs = 120_000))
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

  @Test
  fun curatorLlmSuggestionsParseJson() {
    val suggestions = nbgParseSkillCuratorLlmSuggestions(
      """
      review:
      {"suggestions":[{"skillName":"android-build","action":"merge","reason":"duplicate build notes","patchHint":"merge Gradle steps"},{"name":"old","action":"delete"}]}
      """.trimIndent(),
    )

    assertEquals(1, suggestions.size)
    assertEquals("android-build", suggestions.first().skillName)
    assertEquals("merge", suggestions.first().action)
    assertTrue(suggestions.first().patchHint.contains("Gradle"))
  }

  @Test
  fun sessionFtsRanksSummaryAndMessageHits() {
    val entries = listOf(
      HanakoSessionSummaryIndexEntry("/s1.jsonl", "Gradle fix", "resolved compile issue", 1L, 4, 0, 0),
      HanakoSessionSummaryIndexEntry("/s2.jsonl", "Other", "notes", 2L, 2, 0, 0),
    )
    val histories = listOf(
      entries[0] to HanakoHistorySnapshot(messages = listOf(HanakoHistoryMessage(1, "assistant", "Kotlin compile passed after provider routing fix"))),
      entries[1] to HanakoHistorySnapshot(messages = listOf(HanakoHistoryMessage(2, "user", "unrelated"))),
    )

    val hits = nbgSearchSessionFts(entries, histories, "provider routing compile")

    assertEquals("/s1.jsonl", hits.first().sessionPath)
    assertTrue(hits.first().score > 0)
    assertTrue(hits.first().snippet.contains("compile", ignoreCase = true))
  }

  @Test
  fun usageCostStateEstimatesCost() {
    val state = parseNbgUsageCostState(
      JSONObject()
        .put(
          "events",
          JSONArray().put(
            JSONObject()
              .put("providerId", "openrouter")
              .put("modelId", "gpt-5")
              .put("inputTokens", 1000)
              .put("outputTokens", 500)
              .put("totalTokens", 1500)
              .put("failed", true),
          ),
        )
        .toString(),
    )

    assertEquals(1500L, state.totalTokens)
    assertEquals(1, state.failureCount)
    assertTrue(state.estimatedCostUsd > 0.0)
  }

  @Test
  fun teamParallelPlanAddsBudgetToAgents() {
    val review = nbgReviewTeamDelegationRequest("review diff for current change")
    val plan = nbgBuildTeamParallelPlan(review)
    val agents = nbgMultiAgentSessionAgents("task-1", "queued").withParallelPlan(plan)

    assertEquals(3, plan.maxParallelAgents)
    assertTrue(agents.take(3).all { it.summary.contains(plan.label) })
    assertEquals("idle", agents.drop(3).first().status)
    assertTrue(agents.drop(3).first().summary.contains("等待预算释放"))
  }

  @Test
  fun skillDiffPreviewSupportsSupportFiles() {
    val root = createTempDirectory("skill-diff-files").toFile()
    val skillDir = File(root, "android-build").also { it.mkdirs() }
    val referencesDir = File(skillDir, "references").also { it.mkdirs() }
    File(skillDir, "SKILL.md").writeText("name", Charsets.UTF_8)
    File(referencesDir, "notes.md").writeText("new notes", Charsets.UTF_8)
    File(referencesDir, ".nbg-rollback").also { it.mkdirs() }
    File(referencesDir, ".nbg-rollback/notes.old.bak").writeText("old notes", Charsets.UTF_8)

    val diff = NbgSkillDiffMerge(root)
    val preview = diff.previewCurrent("android-build", "references/notes.md")
    val request = diff.applyMergedPreview(preview, NbgSkillMergeSelection(preview.hunks.map { it.index }.toSet()))

    assertEquals("references/notes.md", preview.relativePath)
    assertTrue(preview.rollbackAvailable)
    assertEquals(NbgSkillManageAction.WriteFile, request.action)
    assertEquals("references/notes.md", request.filePath)
    assertEquals("new notes", request.fileContent)
  }
}

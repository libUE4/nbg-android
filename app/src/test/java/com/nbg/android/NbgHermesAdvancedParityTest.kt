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
  fun providerProfileTemplatesSanitizeSecretsAndRouteCustomProviders() {
    val raw = JSONObject()
      .put(
        "profiles",
        JSONArray().put(
          JSONObject()
            .put("id", "acme")
            .put("label", "Acme AI")
            .put("baseUrlHint", "api.acme.ai")
            .put("authHeader", "X-API-Key")
            .put("authPrefix", "")
            .put("modelsPath", "/models")
            .put("chatPath", "/chat")
            .put("apiMode", "openai")
            .put("supportsThinking", true)
            .put(
              "extraBodyJson",
              JSONObject()
                .put("routing", "fast")
                .put("api_key", "secret-should-not-export")
                .toString(),
            ),
        ),
      )
      .toString()

    val custom = nbgParseCustomModelProviderProfiles(raw)
    val bearerDefault = nbgParseCustomModelProviderProfiles(
      JSONObject()
        .put(
          "profiles",
          JSONArray().put(
            JSONObject()
              .put("id", "bearer-default")
              .put("label", "Bearer Default")
              .put("baseUrlHint", "bearer.example"),
          ),
        )
        .toString(),
    ).first()
    val profile = nbgProfileForUrlApi("https://api.acme.ai/v2", "acme-large", custom)
    val state = nbgProfilesForStoredApis(
      entries = listOf(
        NbgStoredApi(
          id = "entry-acme",
          name = "Acme Work",
          baseUrl = "https://api.acme.ai/v2",
          apiKey = "secret",
          models = listOf(NbgApiModel("acme-large")),
          verifiedModelIds = setOf("acme-large"),
          selectedModelId = "acme-large",
        ),
      ),
      userProfiles = custom,
    )

    assertEquals(1, custom.size)
    assertEquals("acme", profile.id)
    assertEquals("https://api.acme.ai/v2/chat", profile.chatEndpoint("https://api.acme.ai/v2"))
    assertEquals("X-API-Key", profile.authHeader)
    assertEquals("", profile.authPrefix)
    assertEquals("Bearer ", bearerDefault.authPrefix)
    assertTrue(profile.supportsThinking)
    assertTrue(profile.extraBodyJson.contains("routing"))
    assertEquals(false, profile.extraBodyJson.contains("api_key"))
    assertTrue(state.profiles.any { it.id == "acme" })
    assertTrue(state.profiles.any { it.id == "url-api-entry-acme" && it.supportsThinking })
  }

  @Test
  fun providerProfileTemplateReviewRejectsUnsafeEditorInput() {
    val valid = NbgModelProviderProfile(
      id = "acme-openai",
      label = "Acme OpenAI",
      baseUrlHint = "api.acme.ai",
      modelsPath = "/models",
      chatPath = "/chat/completions",
      apiMode = "openai",
      extraBodyJson = """{"routing":"fast"}""",
    )
    val secret = valid.copy(extraBodyJson = """{"api_key":"secret"}""")
    val invalidPath = valid.copy(modelsPath = "models")
    val invalidMode = valid.copy(apiMode = "custom")

    assertTrue(nbgReviewModelProviderProfileTemplate(valid).ok)
    assertEquals(false, nbgReviewModelProviderProfileTemplate(secret).ok)
    assertEquals(false, nbgReviewModelProviderProfileTemplate(invalidPath).ok)
    assertEquals(false, nbgReviewModelProviderProfileTemplate(invalidMode).ok)
  }

  @Test
  fun urlApiHeadersAvoidDuplicateAuthorizationForStrictGateways() {
    val openAiHeaders = nbgUrlApiHeaderPairs("secret", nbgDefaultModelProviderProfiles().first { it.id == "openai" })
    val xApiKeyProfile = NbgModelProviderProfile(
      id = "nbgapi",
      label = "NBG API",
      baseUrlHint = "nbgapi.com",
      authHeader = "x-api-key",
      authPrefix = "",
    )
    val xApiKeyHeaders = nbgUrlApiHeaderPairs("secret", xApiKeyProfile)

    assertEquals(1, openAiHeaders.count { it.first.equals("Authorization", ignoreCase = true) })
    assertEquals(1, openAiHeaders.count { it.first.equals("x-api-key", ignoreCase = true) })
    assertEquals("Bearer secret", openAiHeaders.first { it.first.equals("Authorization", ignoreCase = true) }.second)
    assertEquals(1, xApiKeyHeaders.count { it.first.equals("Authorization", ignoreCase = true) })
    assertEquals(1, xApiKeyHeaders.count { it.first.equals("x-api-key", ignoreCase = true) })
    assertEquals("secret", xApiKeyHeaders.first { it.first.equals("x-api-key", ignoreCase = true) }.second)
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
        .put(
          "runHistory",
          JSONArray()
            .put(
              JSONObject()
                .put("kind", "llm")
                .put("ok", true)
                .put("ranAtMs", 2000)
                .put("suggestionCount", 2)
                .put("message", "merge duplicate skills"),
            )
            .put(
              JSONObject()
                .put("kind", "review")
                .put("ok", false)
                .put("ranAtMs", 1000)
                .put("message", "boom"),
            ),
        )
        .toString(),
    )

    assertTrue(state.enabled)
    assertEquals(2, state.reviewCount)
    assertEquals("后台复核待运行", state.statusLabel)
    assertEquals(2, state.runHistory.size)
    assertEquals("llm", state.runHistory.first().kind)
    assertEquals(2, state.runHistory.first().suggestionCount)
    assertEquals(false, state.runHistory.last().ok)
  }

  @Test
  fun curatorLlmSuggestionsParseJson() {
    val suggestions = nbgParseSkillCuratorLlmSuggestions(
      """
      review:
      {"suggestions":[{"skillName":"android-build","action":"merge","reason":"duplicate build notes","patchHint":"merge Gradle steps","patchDraft":{"filePath":"SKILL.md","oldString":"old step","newString":"new step"}},{"name":"old","action":"delete"}]}
      """.trimIndent(),
    )

    assertEquals(1, suggestions.size)
    assertEquals("android-build", suggestions.first().skillName)
    assertEquals("merge", suggestions.first().action)
    assertTrue(suggestions.first().patchHint.contains("Gradle"))
    assertTrue(suggestions.first().hasPatchDraft)
    assertEquals("SKILL.md", suggestions.first().filePath)
    assertEquals("old step", suggestions.first().oldString)
    assertEquals("new step", suggestions.first().newString)
  }

  @Test
  fun curatorSuggestionQueueTracksPendingAndStatuses() {
    val queue = parseNbgSkillCuratorSuggestionQueue(
      JSONObject()
        .put(
          "entries",
          JSONArray()
            .put(
              JSONObject()
                .put("id", "s1")
                .put("skillName", "android-build")
                .put("action", "archive")
                .put("reason", "unused duplicate")
                .put("status", "pending")
                .put("createdAtMs", 20),
            )
            .put(
              JSONObject()
                .put("id", "s2")
                .put("skillName", "terminal")
                .put("action", "rewrite")
                .put("reason", "needs shorter instructions")
                .put("filePath", "references/notes.md")
                .put("proposedContent", "shorter instructions")
                .put("status", "accepted")
                .put("createdAtMs", 10),
            ),
        )
        .toString(),
    )

    assertEquals(2, queue.entries.size)
    assertEquals(1, queue.pendingCount)
    assertEquals("android-build", queue.pendingEntries.first().skillName)
    val accepted = queue.entries.first { it.id == "s2" }
    val request = accepted.toSkillManageRequestOrNull()
    assertEquals(NbgSkillCuratorSuggestionStatus.Accepted, accepted.status)
    assertTrue(accepted.hasPatchDraft)
    assertEquals(NbgSkillManageAction.WriteFile, request?.action)
    assertEquals("references/notes.md", request?.filePath)
    assertEquals("shorter instructions", request?.fileContent)
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
    assertEquals(listOf("provider", "routing", "compile"), hits.first().queryTerms)
  }

  @Test
  fun sessionFtsBuildsDocumentsForMemorySkillsAndDrafts() {
    val entries = listOf(HanakoSessionSummaryIndexEntry("/s1.jsonl", "Gradle fix", "resolved compile issue", 1L, 4, 0, 0))
    val histories = listOf(entries[0] to HanakoHistorySnapshot(messages = listOf(HanakoHistoryMessage(1, "assistant", "Kotlin compile passed"))))
    val learning = NbgAutonomousLearningSnapshot(
      localMemory = nbgBuildLocalLearningMemory(
        listOf(NbgLocalLearningMemoryEntry(id = "m1", title = "Provider routing", content = "OpenRouter extra body handling")),
      ),
    )
    val skills = HanakoSkillsSnapshot(
      skills = listOf(HanakoSkillSummary(name = "android-build", description = "Gradle verification workflow")),
    )
    val draft = NbgLearnedSkillDraftQueue(
      entries = listOf(
        NbgLearnedSkillDraftQueueEntry(
          id = "d1",
          skillName = "sqlite-search",
          description = "Index local messages",
          review = NbgLearnedSkillDraftReview(
            policyVersion = "test",
            allowDraft = true,
            allowInstall = false,
            allowEnable = false,
            requiresReview = true,
            requiredEvidence = emptyList(),
            presentEvidence = emptyList(),
            permissionTier = NbgPermissionRiskTier.Low,
            sourceTaskId = "task",
            targetPathLabel = "[local-path]",
            reason = "test",
          ),
          status = NbgLearnedSkillDraftStatus.PendingReview,
        ),
      ),
    )

    val docs = nbgBuildSessionFtsDocuments(entries, histories, learning, skills, draft)

    assertTrue(docs.any { it.docType == "memory" && it.body.contains("OpenRouter") })
    assertTrue(docs.any { it.docType == "skill" && it.title == "android-build" })
    assertTrue(docs.any { it.docType == "skill-draft" && it.title == "sqlite-search" })
    assertEquals("provider* routing*", nbgSessionFtsMatchQuery("provider routing"))
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
    assertEquals("openrouter", state.providerBreakdowns.first().label)
    assertEquals("gpt-5", state.modelBreakdowns.first().label)
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

  @Test
  fun skillDiffPreviewFlagsPatchDraftConflicts() {
    val root = createTempDirectory("skill-diff-conflict").toFile()
    val skillDir = File(root, "android-build").also { it.mkdirs() }
    File(skillDir, "SKILL.md").writeText("actual current step", Charsets.UTF_8)

    val preview = NbgSkillDiffMerge(root).preview(
      NbgSkillManageRequest(
        action = NbgSkillManageAction.Patch,
        name = "android-build",
        filePath = "SKILL.md",
        oldString = "missing old step",
        newString = "new step",
      ),
    )

    assertTrue(preview.conflict)
    assertEquals(false, preview.changed)
    assertTrue(preview.message.contains("not found"))
  }
}

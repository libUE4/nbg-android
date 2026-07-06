package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgApiStoreTest {
  private class FakeApiKeySecretStore(
    private val failSave: Boolean = false,
  ) : NbgApiKeySecretStore {
    val keys = linkedMapOf<String, String>()
    val deleted = mutableListOf<String>()

    override fun loadApiKey(entryId: String): String? =
      keys[entryId]

    override fun saveApiKey(entryId: String, apiKey: String) {
      if (failSave) error("save failed")
      keys[entryId] = apiKey
    }

    override fun deleteApiKey(entryId: String) {
      deleted += entryId
      keys.remove(entryId)
    }
  }

  @Test
  fun normalizesBaseUrlsBeforeBuildingProbeEndpoints() {
    assertEquals("https://api.example.com/v1", nbgNormalizeApiBaseUrl(" https://api.example.com/v1/?debug=1#models "))
    assertEquals("https://api.example.com", nbgNormalizeApiBaseUrl("https://api.example.com/v1/models?limit=20"))
    assertEquals("https://api.example.com", nbgNormalizeApiBaseUrl("https://api.example.com/v1/chat/completions"))
    assertEquals("https://anthropic.example.com", nbgNormalizeApiBaseUrl("https://anthropic.example.com/v1/messages"))
    assertEquals("https://api.example.com/v1/models", nbgApiEndpoint("https://api.example.com/v1/?debug=1", "/v1/models"))
    assertEquals("https://api.example.com/v1/chat/completions", nbgApiEndpoint("https://api.example.com/v1/", "/chat/completions"))
  }

  @Test
  fun derivesHanakoBaseUrlsFromVerifiedEndpoints() {
    assertEquals("https://api.example.com/v1", nbgHanakoBaseUrlForUrlApi("https://api.example.com?foo=bar", "openai"))
    assertEquals("https://anthropic.example.com", nbgHanakoBaseUrlForUrlApi("https://anthropic.example.com/v1/#x", "anthropic"))
  }

  @Test
  fun encodesUrlPathSegmentsWithoutFormPlusSemantics() {
    assertEquals("team%20task%2B1%2Fagent", nbgEncodeUrlPathSegment("team task+1/agent"))
  }

  @Test
  fun urlApiProviderIdsNeverCollapseToEmptySuffix() {
    assertEquals("urlapi-abc123", nbgUrlApiProviderId("abc123"))
    assertEquals("urlapi-9a4df9974694", nbgUrlApiProviderId("!!!!!!!!!!!!"))
    assertEquals("urlapi-d04e255fa4bd", nbgUrlApiProviderId("????????????"))
  }

  @Test
  fun storedApiSelectionOnlyKeepsVerifiedExistingModels() {
    val normalized = nbgNormalizeStoredApiForTest(
      NbgStoredApi(
        name = "gateway",
        baseUrl = "https://api.example.com",
        apiKey = " secret\n",
        models = listOf(
          NbgApiModel("gpt-5.5"),
          NbgApiModel("gpt-5.5", "duplicate"),
          NbgApiModel("claude-sonnet-4-5"),
        ),
        verifiedModelIds = setOf("missing-model", "claude-sonnet-4-5"),
        selectedModelId = "missing-model",
      ),
    )

    assertEquals(listOf("gpt-5.5", "claude-sonnet-4-5"), normalized.models.map { it.id })
    assertEquals(setOf("claude-sonnet-4-5"), normalized.verifiedModelIds)
    assertEquals("claude-sonnet-4-5", normalized.selectedModelId)
  }

  @Test
  fun migratesLegacyPlaintextApiKeysOutOfStoredMetadata() {
    val secretStore = FakeApiKeySecretStore()
    val raw = JSONArray()
      .put(
        JSONObject()
          .put("id", "api-1")
          .put("name", "gateway")
          .put("baseUrl", "https://api.example.com")
          .put("apiKey", " sk-secret ")
          .put("models", JSONArray().put(JSONObject().put("id", "gpt-5.5")))
          .put("verifiedModelIds", JSONArray().put("gpt-5.5"))
          .put("selectedModelId", "gpt-5.5")
          .put("updatedAtMs", 12L),
      )
      .toString()

    val loaded = nbgLoadStoredApisFromRawForTest(raw, secretStore)
    val metadata = JSONArray(loaded.normalizedRaw).getJSONObject(0)

    assertTrue(loaded.canPersistNormalizedRaw)
    assertEquals("sk-secret", loaded.entries.single().apiKey)
    assertEquals("sk-secret", secretStore.keys["api-1"])
    assertFalse(metadata.has("apiKey"))
    assertEquals("https://api.example.com", metadata.getString("baseUrl"))
  }

  @Test
  fun keepsLegacyPlaintextMetadataWhenSecretMigrationFails() {
    val secretStore = FakeApiKeySecretStore(failSave = true)
    val raw = JSONArray()
      .put(
        JSONObject()
          .put("id", "api-1")
          .put("name", "gateway")
          .put("baseUrl", "https://api.example.com")
          .put("apiKey", "sk-secret"),
      )
      .toString()

    val loaded = nbgLoadStoredApisFromRawForTest(raw, secretStore)

    assertFalse(loaded.canPersistNormalizedRaw)
    assertEquals("sk-secret", loaded.entries.single().apiKey)
    assertTrue(secretStore.keys.isEmpty())
  }

  @Test
  fun loadsApiKeyFromSecretStoreWhenMetadataHasNoPlaintextKey() {
    val secretStore = FakeApiKeySecretStore().apply {
      keys["api-1"] = "sk-encrypted"
    }
    val raw = JSONArray()
      .put(
        JSONObject()
          .put("id", "api-1")
          .put("name", "gateway")
          .put("baseUrl", "https://api.example.com")
          .put("models", JSONArray().put(JSONObject().put("id", "gpt-5.5")))
          .put("verifiedModelIds", JSONArray().put("gpt-5.5"))
          .put("selectedModelId", "gpt-5.5"),
      )
      .toString()

    val loaded = nbgLoadStoredApisFromRawForTest(raw, secretStore)
    val metadata = JSONArray(loaded.normalizedRaw).getJSONObject(0)

    assertTrue(loaded.canPersistNormalizedRaw)
    assertEquals("sk-encrypted", loaded.entries.single().apiKey)
    assertFalse(metadata.has("apiKey"))
    assertEquals("gpt-5.5", loaded.entries.single().selectedModelId)
  }

  @Test
  fun storedApiSelectionTrimsModelIdsBeforeMatchingVerifiedModels() {
    val normalized = nbgNormalizeStoredApiForTest(
      NbgStoredApi(
        name = "gateway",
        baseUrl = "https://api.example.com",
        apiKey = "secret",
        models = listOf(
          NbgApiModel(" gpt-5.5 ", " GPT 5.5 "),
          NbgApiModel(" "),
        ),
        verifiedModelIds = setOf(" gpt-5.5 ", " "),
        selectedModelId = " gpt-5.5 ",
      ),
    )

    assertEquals(listOf("gpt-5.5"), normalized.models.map { it.id })
    assertEquals("GPT 5.5", normalized.models.single().label)
    assertEquals("secret", normalized.apiKey)
    assertEquals(setOf("gpt-5.5"), normalized.verifiedModelIds)
    assertEquals("gpt-5.5", normalized.selectedModelId)
  }

  @Test
  fun openAiProbeBodiesSupportReasoningModelLimitsBeforeLegacyLimits() {
    val bodies = nbgOpenAiProbeBodies("gpt-5.5")

    assertEquals(2, bodies.size)
    assertEquals(1, bodies[0].optInt("max_completion_tokens"))
    assertFalse(bodies[0].has("temperature"))
    assertEquals(1, bodies[1].optInt("max_tokens"))
    assertTrue(bodies[1].has("temperature"))
  }

  @Test
  fun parsesModelListsFromCommonGatewayShapes() {
    val parsed = nbgParseApiModels(
      """
      {
        "data": [
          {"model": "gpt-5.5", "displayName": "GPT 5.5", "context_length": 400000},
          {"id": "claude-sonnet-4-5", "label": "Claude Sonnet", "max_input_tokens": 200000},
          {"id": "claude-opus-4-8", "display_name": "claude-opus-4-8"},
          "deepseek-v4"
        ]
      }
      """.trimIndent(),
    )

    assertEquals(listOf("claude-opus-4-8", "claude-sonnet-4-5", "deepseek-v4", "gpt-5.5"), parsed.map { it.id })
    assertEquals("GPT 5.5", parsed.first { it.id == "gpt-5.5" }.label)
    assertEquals("Claude Sonnet", parsed.first { it.id == "claude-sonnet-4-5" }.label)
    assertEquals(400000L, parsed.first { it.id == "gpt-5.5" }.contextWindow)
    assertEquals(200000L, parsed.first { it.id == "claude-sonnet-4-5" }.contextWindow)
    assertEquals(200_000L, parsed.first { it.id == "claude-opus-4-8" }.contextWindow)
    assertEquals(1_000_000L, parsed.first { it.id == "deepseek-v4" }.contextWindow)
  }

  @Test
  fun deepSeekV4ModelsUseOneMillionContextFallbacks() {
    val parsed = nbgParseApiModels(
      """
      {
        "data": [
          {"id": "deepseek-v4-pro", "context_window": 128000},
          {"id": "deepseek-v4-flash"},
          {"id": "deepseek-chat", "max_context": 128000},
          {"id": "deepseek-reasoner"}
        ]
      }
      """.trimIndent(),
    )

    assertEquals(1_000_000L, parsed.first { it.id == "deepseek-v4-pro" }.contextWindow)
    assertEquals(1_000_000L, parsed.first { it.id == "deepseek-v4-flash" }.contextWindow)
    assertEquals(1_000_000L, parsed.first { it.id == "deepseek-chat" }.contextWindow)
    assertEquals(1_000_000L, parsed.first { it.id == "deepseek-reasoner" }.contextWindow)
  }

  @Test
  fun normalizesSavedClaudeModelsBackToOfficial200kDefault() {
    val normalized = nbgNormalizeStoredApiForTest(
      NbgStoredApi(
        name = "gateway",
        baseUrl = "https://api.example.com",
        apiKey = "secret",
        models = listOf(
          NbgApiModel("claude-opus-4-8", contextWindow = 1_000_000L),
          NbgApiModel("claude-sonnet-4-6", contextWindow = 1_000_000L),
          NbgApiModel("claude-sonnet-4-5", contextWindow = 200_000L),
        ),
      ),
    )

    assertEquals(200_000L, normalized.models.first { it.id == "claude-opus-4-8" }.contextWindow)
    assertEquals(200_000L, normalized.models.first { it.id == "claude-sonnet-4-6" }.contextWindow)
    assertEquals(200_000L, normalized.models.first { it.id == "claude-sonnet-4-5" }.contextWindow)
  }

  @Test
  fun preservesLargeContextWindowsBetween500kAnd1000k() {
    val parsed = nbgParseApiModels(
      """
      {
        "data": [
          {"id": "custom-500k", "max_input_tokens": 500000},
          {"id": "custom-1000k", "context_window": 1000000},
          {"id": "claude-opus-4-8", "max_input_tokens": 200000}
        ]
      }
      """.trimIndent(),
    )

    assertEquals(500_000L, parsed.first { it.id == "custom-500k" }.contextWindow)
    assertEquals(1_000_000L, parsed.first { it.id == "custom-1000k" }.contextWindow)
    assertEquals(200_000L, parsed.first { it.id == "claude-opus-4-8" }.contextWindow)
  }

  @Test
  fun normalizesStoredLargeContextWindowsWithoutDisablingSelection() {
    val normalized = nbgNormalizeStoredApiForTest(
      NbgStoredApi(
        name = "gateway",
        baseUrl = "https://api.example.com",
        apiKey = "secret",
        models = listOf(
          NbgApiModel("custom-500k", contextWindow = 500_000L),
          NbgApiModel("custom-1000k", contextWindow = 1_000_000L),
        ),
        verifiedModelIds = setOf("custom-500k", "custom-1000k"),
        selectedModelId = "custom-1000k",
      ),
    )

    assertEquals(500_000L, normalized.models.first { it.id == "custom-500k" }.contextWindow)
    assertEquals(1_000_000L, normalized.models.first { it.id == "custom-1000k" }.contextWindow)
    assertEquals(setOf("custom-500k", "custom-1000k"), normalized.verifiedModelIds)
    assertEquals("custom-1000k", normalized.selectedModelId)
  }

  @Test
  fun parsesProbeTextFromOpenAiAndAnthropicContentShapes() {
    assertEquals(
      "pong",
      nbgParseProbeText(
        """{"choices":[{"message":{"content":[{"type":"text","text":"pong"}]}}]}""",
        "OpenAI",
      ),
    )
    assertEquals(
      "pong",
      nbgParseProbeText(
        """{"content":[{"type":"text","text":"pong"}]}""",
        "Anthropic",
      ),
    )
  }
}

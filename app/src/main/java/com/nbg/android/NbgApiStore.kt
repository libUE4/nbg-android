package com.nbg.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

data class NbgApiModel(
  val id: String,
  val label: String = id,
  val contextWindow: Long = 0L,
)

data class NbgStoredApi(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val baseUrl: String,
  val apiKey: String,
  val models: List<NbgApiModel> = emptyList(),
  val verifiedModelIds: Set<String> = emptySet(),
  val selectedModelId: String = "",
  val updatedAtMs: Long = System.currentTimeMillis(),
) {
  val verifiedCount: Int
    get() = verifiedModelIds.size
}

internal fun nbgNormalizeStoredApiForTest(entry: NbgStoredApi): NbgStoredApi =
  nbgNormalizeStoredApi(entry)

data class NbgApiModelFetchResult(
  val models: List<NbgApiModel>,
  val message: String,
)

data class NbgApiModelVerifyResult(
  val ok: Boolean,
  val message: String,
  val effectiveBaseUrl: String = "",
)

data class NbgSkillDescriptionTranslateResult(
  val translations: Map<String, String>,
  val message: String,
)

data class NbgUrlApiTextGenerationResult(
  val ok: Boolean,
  val text: String,
  val message: String,
)

internal interface NbgApiKeySecretStore {
  fun loadApiKey(entryId: String): String?
  fun saveApiKey(entryId: String, apiKey: String)
  fun deleteApiKey(entryId: String)
}

internal data class NbgApiEntriesLoadResult(
  val entries: List<NbgStoredApi>,
  val normalizedRaw: String,
  val canPersistNormalizedRaw: Boolean,
)

internal fun nbgLoadStoredApisFromRawForTest(
  raw: String,
  secretStore: NbgApiKeySecretStore,
): NbgApiEntriesLoadResult =
  nbgLoadStoredApisFromRaw(raw, secretStore)

class NbgApiStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val secretStore: NbgApiKeySecretStore = NbgAndroidApiKeySecretStore(context.applicationContext)

  fun load(): List<NbgStoredApi> =
    runCatching {
      val raw = prefs.getString(KEY_ENTRIES, "[]").orEmpty()
      val result = nbgLoadStoredApisFromRaw(raw, secretStore)
      if (result.canPersistNormalizedRaw && raw != result.normalizedRaw) {
        prefs.edit().putString(KEY_ENTRIES, result.normalizedRaw).apply()
      }
      result.entries
    }.getOrDefault(emptyList())

  fun save(entry: NbgStoredApi): List<NbgStoredApi> {
    val normalized = nbgNormalizeStoredApi(entry).copy(updatedAtMs = System.currentTimeMillis())
    secretStore.saveApiKey(normalized.id, normalized.apiKey)
    val next = (load().filterNot { it.id == normalized.id } + normalized)
      .sortedByDescending { it.updatedAtMs }
    prefs.edit().putString(KEY_ENTRIES, JSONArray(next.map { it.toMetadataJson() }).toString()).apply()
    return next
  }

  fun delete(id: String): List<NbgStoredApi> {
    secretStore.deleteApiKey(id)
    val next = load().filterNot { it.id == id }
    prefs.edit().putString(KEY_ENTRIES, JSONArray(next.map { it.toMetadataJson() }).toString()).apply()
    return next
  }

  private companion object {
    const val PREFS = "nbg_url_api_store"
    const val KEY_ENTRIES = "entries"
  }
}

private class NbgAndroidApiKeySecretStore(context: Context) : NbgApiKeySecretStore {
  private val encryptedStore = NbgEncryptedPreferenceSecretStore(
    context = context,
    prefsName = PREFS,
    keyAlias = KEY_ALIAS,
    keyPrefix = KEY_PREFIX,
  )

  override fun loadApiKey(entryId: String): String? {
    return encryptedStore.loadSecret(entryId)
  }

  override fun saveApiKey(entryId: String, apiKey: String) {
    encryptedStore.saveSecret(entryId, apiKey)
  }

  override fun deleteApiKey(entryId: String) {
    encryptedStore.deleteSecret(entryId)
  }

  private companion object {
    const val PREFS = "nbg_url_api_secrets"
    const val KEY_ALIAS = "nbg_url_api_key_v1"
    const val KEY_PREFIX = "api_key_"
  }
}

private data class NbgParsedApiEntry(
  val entry: NbgStoredApi,
  val plaintextKeyToMigrate: String?,
)

private fun nbgLoadStoredApisFromRaw(
  raw: String,
  secretStore: NbgApiKeySecretStore,
): NbgApiEntriesLoadResult {
  val array = JSONArray(raw.ifBlank { "[]" })
  val parsedEntries = buildList {
    for (index in 0 until array.length()) {
      nbgParseStoredApiEntry(array.optJSONObject(index), secretStore)?.let { add(it) }
    }
  }
  var canPersistNormalizedRaw = true
  parsedEntries.forEach { parsed ->
    val legacyKey = parsed.plaintextKeyToMigrate ?: return@forEach
    runCatching {
      secretStore.saveApiKey(parsed.entry.id, legacyKey)
    }.onFailure {
      canPersistNormalizedRaw = false
    }
  }
  val entries = parsedEntries.map { it.entry }.sortedByDescending { it.updatedAtMs }
  return NbgApiEntriesLoadResult(
    entries = entries,
    normalizedRaw = JSONArray(entries.map { it.toMetadataJson() }).toString(),
    canPersistNormalizedRaw = canPersistNormalizedRaw,
  )
}

private fun nbgParseStoredApiEntry(root: JSONObject?, secretStore: NbgApiKeySecretStore): NbgParsedApiEntry? {
  if (root == null) return null
  val id = root.optString("id").ifBlank { UUID.randomUUID().toString() }
  val baseUrl = root.optString("baseUrl").trim()
  val storedSecret = runCatching { secretStore.loadApiKey(id).orEmpty().trim() }.getOrDefault("")
  val legacyPlaintext = root.optString("apiKey").trim()
  val apiKey = storedSecret.ifBlank { legacyPlaintext }
  if (baseUrl.isBlank() || apiKey.isBlank()) return null
  val models = root.optJSONArray("models").toModels()
  val verified = root.optJSONArray("verifiedModelIds").toStringSet()
  val entry = nbgNormalizeStoredApi(
    NbgStoredApi(
      id = id,
      name = root.optString("name").ifBlank { nbgApiNameFor(baseUrl) },
      baseUrl = baseUrl,
      apiKey = apiKey,
      models = models,
      verifiedModelIds = verified,
      selectedModelId = root.optString("selectedModelId").ifBlank { verified.firstOrNull().orEmpty() },
      updatedAtMs = root.optLong("updatedAtMs", 0L),
    ),
  )
  return NbgParsedApiEntry(
    entry = entry,
    plaintextKeyToMigrate = legacyPlaintext.takeIf { it.isNotBlank() && storedSecret.isBlank() },
  )
}

private fun NbgStoredApi.toMetadataJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("name", name)
    .put("baseUrl", baseUrl)
    .put(
      "models",
      JSONArray(
        models.map {
          JSONObject()
            .put("id", it.id)
            .put("label", it.label)
            .put("contextWindow", it.contextWindow.coerceAtLeast(0L))
        },
      ),
    )
    .put("verifiedModelIds", JSONArray(verifiedModelIds.toList()))
    .put("selectedModelId", selectedModelId)
    .put("updatedAtMs", updatedAtMs)

private fun JSONArray?.toModels(): List<NbgApiModel> =
  buildList {
    val array = this@toModels ?: return@buildList
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index)
      val id = item?.optString("id").orEmpty().ifBlank { array.optString(index) }.trim()
      val label = item?.optString("label").orEmpty().trim().ifBlank { id }
      val contextWindow = item.nbgModelContextWindow(id)
      if (id.isNotBlank()) add(NbgApiModel(id = id, label = label, contextWindow = contextWindow))
    }
  }.distinctBy { it.id }

private fun JSONArray?.toStringSet(): Set<String> =
  buildSet {
    val array = this@toStringSet ?: return@buildSet
    for (index in 0 until array.length()) {
      array.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it) }
    }
  }

private fun nbgNormalizeStoredApi(entry: NbgStoredApi): NbgStoredApi {
  val uniqueModels = entry.models.mapNotNull { model ->
    val id = model.id.trim()
    if (id.isBlank()) {
      null
    } else {
      model.copy(
        id = id,
        label = model.label.trim().ifBlank { id },
        contextWindow = nbgEffectiveModelContextWindow(id, model.contextWindow),
      )
    }
  }.distinctBy { it.id }
  val modelIds = uniqueModels.map { it.id }.toSet()
  val validVerified = entry.verifiedModelIds.map { it.trim() }.filter { it in modelIds }.toSet()
  val selectedModelId = entry.selectedModelId.trim()
  val validSelected = selectedModelId.takeIf { it in validVerified }
    ?: validVerified.firstOrNull()
    ?: ""
  return entry.copy(
    models = uniqueModels,
    apiKey = entry.apiKey.trim(),
    verifiedModelIds = validVerified,
    selectedModelId = validSelected,
  )
}

class NbgUpstreamApiClient {
  private val client = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

  suspend fun fetchModels(baseUrl: String, apiKey: String): NbgApiModelFetchResult =
    fetchModels(baseUrl, apiKey, emptyList())

  suspend fun fetchModels(
    baseUrl: String,
    apiKey: String,
    providerProfiles: List<NbgModelProviderProfile>,
  ): NbgApiModelFetchResult =
    withContext(Dispatchers.IO) {
      val normalized = nbgNormalizeApiBaseUrl(baseUrl)
      val profile = nbgProfileForUrlApi(normalized, userProfiles = providerProfiles)
      if (normalized.isBlank() || apiKey.isBlank()) {
        return@withContext NbgApiModelFetchResult(emptyList(), "请先填写地址和 API Key")
      }
      var lastError = ""
      val candidates = listOf(
        profile.modelsEndpoint(normalized),
        nbgApiEndpoint(normalized, "/v1/models"),
        nbgApiEndpoint(normalized, "/models"),
      ).distinct()
      for (url in candidates) {
        val request = Request.Builder()
          .url(url)
          .addApiHeaders(apiKey, profile)
          .get()
          .build()
        runCatching {
          client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
              lastError = "HTTP ${response.code}: ${nbgCompactApiError(body)}"
              return@use
            }
            val models = nbgParseApiModels(body)
            if (models.isNotEmpty()) {
              return@withContext NbgApiModelFetchResult(models, "已获取 ${models.size} 个模型")
            }
            lastError = "没有返回模型列表"
          }
        }.onFailure { error ->
          lastError = error.message.orEmpty().ifBlank { error::class.java.simpleName }
        }
      }
      NbgApiModelFetchResult(emptyList(), "获取失败：$lastError")
    }

  suspend fun verifyModel(baseUrl: String, apiKey: String, modelId: String): NbgApiModelVerifyResult =
    verifyModel(baseUrl, apiKey, modelId, emptyList())

  suspend fun verifyModel(
    baseUrl: String,
    apiKey: String,
    modelId: String,
    providerProfiles: List<NbgModelProviderProfile>,
  ): NbgApiModelVerifyResult =
    withContext(Dispatchers.IO) {
      val normalized = nbgNormalizeApiBaseUrl(baseUrl)
      val profile = nbgProfileForUrlApi(normalized, modelId, providerProfiles)
      if (normalized.isBlank() || apiKey.isBlank() || modelId.isBlank()) {
        return@withContext NbgApiModelVerifyResult(false, "地址、API Key 和模型不能为空")
      }
      var lastError = ""
      val probes = buildList {
        add(
          NbgApiProbe(
            wire = if (profile.apiMode == "anthropic") "Anthropic" else "OpenAI",
            url = profile.chatEndpoint(normalized),
            effectiveBaseUrl = nbgHanakoBaseUrlForUrlApi(normalized, profile.apiMode),
            body = if (profile.apiMode == "anthropic") nbgAnthropicProbeBody(modelId) else profile.applyExtraBody(nbgOpenAiProbeBodies(modelId).first()),
            profile = profile,
          ),
        )
        listOf(
          nbgApiEndpoint(normalized, "/v1/chat/completions"),
          nbgApiEndpoint(normalized, "/chat/completions"),
        ).distinct().forEach { endpoint ->
          nbgOpenAiProbeBodies(modelId).forEach { body ->
            add(
              NbgApiProbe(
                wire = "OpenAI",
                url = endpoint,
                effectiveBaseUrl = nbgOpenAiBaseUrlForEndpoint(endpoint),
                body = body,
                profile = profile,
              ),
            )
          }
        }
        listOf(
          nbgApiEndpoint(normalized, "/v1/messages"),
          nbgApiEndpoint(normalized, "/messages"),
        ).distinct().forEach { endpoint ->
          add(
            NbgApiProbe(
              wire = "Anthropic",
              url = endpoint,
              effectiveBaseUrl = nbgAnthropicBaseUrlForEndpoint(endpoint),
              body = nbgAnthropicProbeBody(modelId),
              profile = profile.copy(apiMode = "anthropic"),
            ),
          )
        }
      }.distinctBy { "${it.url}\u0000${it.body}" }
      for (probe in probes) {
        val request = Request.Builder()
          .url(probe.url)
          .addApiHeaders(apiKey, probe.profile)
          .post(probe.body.toString().toRequestBody(JSON))
          .build()
        runCatching {
          client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
              val text = nbgParseProbeText(body, probe.wire)
              if (text.isNotBlank()) {
                return@withContext NbgApiModelVerifyResult(true, "${probe.wire} 验证通过", probe.effectiveBaseUrl)
              }
              lastError = "${probe.wire}: 模型未返回文本"
              return@use
            }
            if (response.code == 404 || response.code == 405) {
              if (lastError.isBlank()) lastError = "${probe.wire} HTTP ${response.code}: 端点不可用"
            } else {
              lastError = "${probe.wire} HTTP ${response.code}: ${nbgCompactApiError(body)}"
            }
          }
        }.onFailure { error ->
          lastError = "${probe.wire}: ${error.message.orEmpty().ifBlank { error::class.java.simpleName }}"
        }
      }
      NbgApiModelVerifyResult(false, "验证失败：$lastError")
    }

  suspend fun translateSkillDescriptions(
    entry: NbgStoredApi,
    model: NbgApiModel,
    skills: List<HanakoSkillSummary>,
    providerProfiles: List<NbgModelProviderProfile> = emptyList(),
  ): NbgSkillDescriptionTranslateResult =
    withContext(Dispatchers.IO) {
      val normalized = nbgNormalizeApiBaseUrl(entry.baseUrl)
      val targets = skills.mapNotNull { skill ->
        val text = skill.displayDescription.trim()
        if (skill.name.isBlank() || text.isBlank()) null else skill.name to text.take(900)
      }.take(40)
      if (normalized.isBlank() || entry.apiKey.isBlank() || model.id.isBlank()) {
        return@withContext NbgSkillDescriptionTranslateResult(emptyMap(), "没有可用的 URL API 模型")
      }
      if (targets.isEmpty()) {
        return@withContext NbgSkillDescriptionTranslateResult(emptyMap(), "没有可翻译的 Skill 描述")
      }
      val prompt = buildSkillDescriptionTranslationPrompt(targets)
      val profile = nbgProfileForUrlApi(normalized, model.id, providerProfiles)
      val provider = profile.apiMode.ifBlank { nbgHanakoProviderForUrlApi(normalized, model.id) }
      val candidates = if (provider == "anthropic") {
        listOf(nbgApiEndpoint(normalized, "/v1/messages"), nbgApiEndpoint(normalized, "/messages")).distinct()
      } else {
        listOf(profile.chatEndpoint(normalized), nbgApiEndpoint(normalized, "/v1/chat/completions"), nbgApiEndpoint(normalized, "/chat/completions")).distinct()
      }
      var lastError = ""
      for (url in candidates) {
        val body = if (provider == "anthropic") {
          JSONObject()
            .put("model", model.id)
            .put("max_tokens", 1600)
            .put("temperature", 0)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        } else {
          JSONObject()
            .put("model", model.id)
            .put(
              "messages",
              JSONArray()
                .put(JSONObject().put("role", "system").put("content", "你是一个精确的产品文案翻译器，只输出 JSON。"))
                .put(JSONObject().put("role", "user").put("content", prompt)),
            )
            .put("temperature", 0)
            .put("max_tokens", 1600)
            .put("stream", false)
            .let(profile::applyExtraBody)
        }
        val request = Request.Builder()
          .url(url)
          .addApiHeaders(entry.apiKey, profile)
          .post(body.toString().toRequestBody(JSON))
          .build()
        runCatching {
          client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
              lastError = "HTTP ${response.code}: ${nbgCompactApiError(raw)}"
              return@use
            }
            val text = nbgParseProbeText(raw, if (provider == "anthropic") "Anthropic" else "OpenAI")
            val translated = nbgParseSkillDescriptionTranslations(text, targets.map { it.first }.toSet())
            if (translated.isNotEmpty()) {
              return@withContext NbgSkillDescriptionTranslateResult(translated, "已翻译 ${translated.size} 个 Skill")
            }
            lastError = "模型没有返回可解析的 JSON"
          }
        }.onFailure { error ->
          lastError = error.message.orEmpty().ifBlank { error::class.java.simpleName }
        }
      }
      NbgSkillDescriptionTranslateResult(emptyMap(), "翻译失败：$lastError")
    }

  suspend fun generateReadOnlyText(
    entry: NbgStoredApi,
    model: NbgApiModel,
    prompt: String,
    systemPrompt: String = "你是只读专家评审模型。不要调用工具，不要要求写文件，只输出评审意见。",
    providerProfiles: List<NbgModelProviderProfile> = emptyList(),
  ): NbgUrlApiTextGenerationResult =
    withContext(Dispatchers.IO) {
      val normalized = nbgNormalizeApiBaseUrl(entry.baseUrl)
      val cleanPrompt = prompt.trim().take(8_000)
      if (normalized.isBlank() || entry.apiKey.isBlank() || model.id.isBlank() || cleanPrompt.isBlank()) {
        return@withContext NbgUrlApiTextGenerationResult(false, "", "没有可用的 URL API 模型或评审问题")
      }
      val profile = nbgProfileForUrlApi(normalized, model.id, providerProfiles)
      val provider = profile.apiMode.ifBlank { nbgHanakoProviderForUrlApi(normalized, model.id) }
      val candidates = if (provider == "anthropic") {
        listOf(nbgApiEndpoint(normalized, "/v1/messages"), nbgApiEndpoint(normalized, "/messages")).distinct()
      } else {
        listOf(profile.chatEndpoint(normalized), nbgApiEndpoint(normalized, "/v1/chat/completions"), nbgApiEndpoint(normalized, "/chat/completions")).distinct()
      }
      var lastError = ""
      for (url in candidates) {
        val body = if (provider == "anthropic") {
          JSONObject()
            .put("model", model.id)
            .put("max_tokens", 2200)
            .put("temperature", 0)
            .put("system", systemPrompt)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", cleanPrompt)))
        } else {
          JSONObject()
            .put("model", model.id)
            .put(
              "messages",
              JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", cleanPrompt)),
            )
            .put("temperature", 0)
            .put("max_tokens", 2200)
            .put("stream", false)
            .let(profile::applyExtraBody)
        }
        val request = Request.Builder()
          .url(url)
          .addApiHeaders(entry.apiKey, profile)
          .post(body.toString().toRequestBody(JSON))
          .build()
        runCatching {
          client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
              lastError = "HTTP ${response.code}: ${nbgCompactApiError(raw)}"
              return@use
            }
            val text = nbgParseProbeText(raw, if (provider == "anthropic") "Anthropic" else "OpenAI")
            if (text.isNotBlank()) {
              return@withContext NbgUrlApiTextGenerationResult(true, text.take(12_000), "完成")
            }
            lastError = "模型没有返回文本"
          }
        }.onFailure { error ->
          lastError = error.message.orEmpty().ifBlank { error::class.java.simpleName }
        }
      }
      NbgUrlApiTextGenerationResult(false, "", "生成失败：$lastError")
    }

  private fun Request.Builder.addApiHeaders(apiKey: String, profile: NbgModelProviderProfile = nbgDefaultModelProviderProfiles().first { it.id == "openai" }): Request.Builder =
    addHeader(profile.authHeader.ifBlank { "Authorization" }, "${profile.authPrefix}$apiKey".trim())
      .addHeader("Authorization", "Bearer $apiKey")
      .addHeader("x-api-key", apiKey)
      .addHeader("anthropic-version", "2023-06-01")
      .header("User-Agent", NBG_UPSTREAM_USER_AGENT)
      .addHeader("Content-Type", "application/json")

  private data class NbgApiProbe(
    val wire: String,
    val url: String,
    val effectiveBaseUrl: String,
    val body: JSONObject,
    val profile: NbgModelProviderProfile,
  )

  private companion object {
    val JSON = "application/json; charset=utf-8".toMediaType()
    const val NBG_UPSTREAM_USER_AGENT = "NBG-Android/1.0"
  }
}

private fun buildSkillDescriptionTranslationPrompt(targets: List<Pair<String, String>>): String {
  val items = JSONObject().apply {
    targets.forEach { (name, description) -> put(name, description) }
  }
  return """
    请把下面 JSON 中每个 Skill 描述翻译成简洁中文。
    要求：
    - 只输出 JSON 对象，key 保持原样，value 是中文译文。
    - 不要解释，不要 Markdown。
    - 保留产品/代码专有名词。
    - 每条译文控制在 80 个中文字符内。

    输入：
    $items
  """.trimIndent()
}

internal fun nbgParseSkillDescriptionTranslations(raw: String, allowedNames: Set<String>): Map<String, String> {
  val jsonText = raw.extractJsonObjectText()
  return runCatching {
    val root = JSONObject(jsonText)
    buildMap {
      val keys = root.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        if (key !in allowedNames) continue
        val value = root.optString(key).trim()
        if (value.isNotBlank() && value != "null" && value != "undefined") put(key, value)
      }
    }
  }.getOrDefault(emptyMap())
}

private fun String.extractJsonObjectText(): String {
  val text = trim()
  val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
    .find(text)
    ?.groupValues
    ?.getOrNull(1)
    ?.trim()
  val candidate = fenced ?: text
  val start = candidate.indexOf('{')
  val end = candidate.lastIndexOf('}')
  return if (start >= 0 && end > start) candidate.substring(start, end + 1) else candidate
}

internal fun nbgParseApiModels(raw: String): List<NbgApiModel> =
  runCatching {
    val trimmed = raw.trim()
    val array = when {
      trimmed.startsWith("[") -> JSONArray(trimmed)
      else -> {
        val root = JSONObject(trimmed)
        root.optJSONArray("data")
          ?: root.optJSONArray("models")
          ?: root.optJSONObject("response")?.optJSONArray("models")
          ?: JSONArray()
      }
    }
    buildList {
      for (index in 0 until array.length().coerceAtMost(300)) {
        val item = array.optJSONObject(index)
        val id = item?.optString("id").orEmpty()
          .ifBlank { item?.optString("model").orEmpty() }
          .ifBlank { item?.optString("name").orEmpty() }
          .ifBlank { array.optString(index) }
        if (id.isNotBlank()) {
          val label = item?.optString("display_name").orEmpty()
            .ifBlank { item?.optString("displayName").orEmpty() }
            .ifBlank { item?.optString("label").orEmpty() }
            .ifBlank { item?.optString("name").orEmpty() }
            .ifBlank { id }
          add(NbgApiModel(id = id, label = label, contextWindow = item.nbgModelContextWindow(id)))
        }
      }
    }.distinctBy { it.id }.sortedBy { it.id.lowercase() }
  }.getOrDefault(emptyList())

internal fun JSONObject?.nbgModelContextWindow(modelId: String = ""): Long {
  if (this == null) return nbgFallbackContextWindowForModel(modelId)
  val direct = nbgFirstPositiveLong(
    "contextWindow",
    "context_window",
    "contextLength",
    "context_length",
    "maxContextLength",
    "max_context_length",
    "maxInputTokens",
    "max_input_tokens",
    "maxContext",
    "max_context",
    "context",
    "inputTokenLimit",
    "input_token_limit",
    "tokenLimit",
    "token_limit",
    "maxModelLength",
    "max_model_length",
    "maxModelLen",
    "max_model_len",
    "modelMaxLength",
    "model_max_length",
    "maxSeqLen",
    "max_seq_len",
    "maxSequenceLength",
    "max_sequence_length",
  )
  if (direct > 0L) return nbgEffectiveModelContextWindow(modelId, direct)
  listOf("metadata", "limits", "capabilities", "model").forEach { key ->
    val nested = optJSONObject(key).nbgModelContextWindow(modelId)
    if (nested > 0L) return nested
  }
  return nbgFallbackContextWindowForModel(modelId)
}

internal fun nbgFallbackContextWindowForModel(modelId: String): Long {
  val model = modelId.lowercase()
  return when {
    model.isBlank() -> 0L
    model.contains("claude-3-7") ||
      model.contains("claude-opus-4") ||
      model.contains("claude-sonnet-4") ||
      model.contains("claude-haiku-4") ||
      model.contains("opus-4") ||
      model.contains("sonnet-4") ||
      model.contains("haiku-4") -> 200_000L
    model.contains("claude-3-5") -> 200_000L
    model.contains("gpt-5") -> 400_000L
    nbgIsDeepSeekOfficialV4ContextModel(model) -> 1_000_000L
      else -> 0L
  }
}

internal fun nbgEffectiveModelContextWindow(modelId: String, upstreamContextWindow: Long): Long {
  val model = modelId.lowercase()
  val fallback = nbgFallbackContextWindowForModel(modelId)
  return when {
    upstreamContextWindow <= 0L -> fallback
    nbgIsClaudeOfficial200kDefaultModel(model) && upstreamContextWindow == 1_000_000L -> fallback
    nbgIsDeepSeekOfficialV4ContextModel(model) && upstreamContextWindow < fallback -> fallback
    else -> upstreamContextWindow
  }
}

private fun nbgIsDeepSeekOfficialV4ContextModel(model: String): Boolean =
  model.contains("deepseek-v4") ||
    model == "deepseek-chat" ||
    model == "deepseek-reasoner"

private fun nbgIsClaudeOfficial200kDefaultModel(model: String): Boolean =
  model.contains("claude-3-7") ||
    model.contains("claude-opus-4") ||
    model.contains("claude-sonnet-4") ||
    model.contains("claude-haiku-4") ||
    model.contains("opus-4") ||
    model.contains("sonnet-4") ||
    model.contains("haiku-4") ||
    model.contains("claude-3-5")

private fun JSONObject.nbgFirstPositiveLong(vararg names: String): Long {
  names.forEach { name ->
    val value = optLong(name, 0L)
    if (value > 0L) return value
  }
  return 0L
}

fun nbgNormalizeApiBaseUrl(baseUrl: String): String =
  baseUrl.trim()
    .substringBefore('#')
    .substringBefore('?')
    .trimEnd('/')
    .let(::nbgStripKnownApiEndpointSuffix)

private fun nbgStripKnownApiEndpointSuffix(url: String): String {
  val suffixes = listOf(
    "/v1/chat/completions",
    "/chat/completions",
    "/v1/messages",
    "/messages",
    "/v1/models",
    "/models",
  )
  return suffixes.firstOrNull { suffix -> url.endsWith(suffix, ignoreCase = true) }
    ?.let { suffix -> url.dropLast(suffix.length).ifBlank { url } }
    ?: url
}

fun nbgApiEndpoint(baseUrl: String, suffix: String): String {
  val base = nbgNormalizeApiBaseUrl(baseUrl)
  val cleanSuffix = suffix.trim()
  return if (base.endsWith("/v1") && cleanSuffix.startsWith("/v1/")) {
    base + cleanSuffix.removePrefix("/v1")
  } else {
    base + cleanSuffix
  }
}

fun nbgHanakoBaseUrlForUrlApi(baseUrl: String, provider: String): String {
  val normalized = nbgNormalizeApiBaseUrl(baseUrl)
  if (provider == "anthropic") return nbgAnthropicBaseUrlForEndpoint(nbgApiEndpoint(normalized, "/v1/messages"))
  if (provider != "openai") return normalized
  val lower = normalized.lowercase()
  if (
    lower.endsWith("/v1") ||
    lower.endsWith("/v2") ||
    lower.endsWith("/v3") ||
    lower.endsWith("/v4") ||
    lower.contains("/openai/")
  ) {
    return normalized
  }
  return "$normalized/v1"
}

fun nbgComparableApiBaseUrl(baseUrl: String): String =
  nbgNormalizeApiBaseUrl(baseUrl).removeSuffix("/v1")

private fun nbgOpenAiBaseUrlForEndpoint(endpoint: String): String =
  nbgNormalizeApiBaseUrl(endpoint)
    .removeSuffix("/chat/completions")

private fun nbgAnthropicBaseUrlForEndpoint(endpoint: String): String =
  nbgNormalizeApiBaseUrl(endpoint)
    .removeSuffix("/messages")
    .removeSuffix("/v1")

internal fun nbgOpenAiProbeBodies(modelId: String): List<JSONObject> {
  val messages = JSONArray().put(JSONObject().put("role", "user").put("content", "ping"))
  return listOf(
    JSONObject()
      .put("model", modelId)
      .put("messages", messages)
      .put("max_completion_tokens", 1)
      .put("stream", false),
    JSONObject()
      .put("model", modelId)
      .put("messages", messages)
      .put("max_tokens", 1)
      .put("temperature", 0)
      .put("stream", false),
  )
}

internal fun nbgAnthropicProbeBody(modelId: String): JSONObject =
  JSONObject()
    .put("model", modelId)
    .put("max_tokens", 1)
    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))

internal fun nbgParseProbeText(raw: String, wire: String): String {
  val root = JSONObject(raw.ifBlank { "{}" })
  root.optJSONObject("error")?.let { errorRoot ->
    error(errorRoot.optString("message").ifBlank { errorRoot.toString() })
  }
  return when (wire) {
    "Anthropic" -> nbgContentValueText(root.opt("content"))
    else -> {
      val choices = root.optJSONArray("choices") ?: JSONArray()
      val first = choices.optJSONObject(0)
      nbgContentValueText(first?.optJSONObject("message")?.opt("content"))
        .ifBlank { first?.optString("text").orEmpty() }
    }
  }.trim()
}

private fun nbgContentValueText(value: Any?): String =
  when (value) {
    is String -> value
    is JSONArray -> buildString {
      for (index in 0 until value.length()) {
        val item = value.optJSONObject(index) ?: continue
        val type = item.optString("type")
        if (type.isBlank() || type == "text" || type == "output_text") {
          append(item.optString("text").ifBlank { item.optString("content") })
        }
      }
    }
    else -> ""
  }

fun nbgApiNameFor(baseUrl: String): String =
  runCatching {
    val host = java.net.URI(nbgNormalizeApiBaseUrl(baseUrl)).host.orEmpty()
    host.removePrefix("api.").ifBlank { nbgNormalizeApiBaseUrl(baseUrl) }
  }.getOrDefault(nbgNormalizeApiBaseUrl(baseUrl))

fun nbgUrlApiProviderId(entryId: String): String =
  "urlapi-${entryId.toUrlApiProviderSuffix()}"

private fun String.toUrlApiProviderSuffix(): String {
  val direct = take(24).replace(Regex("[^A-Za-z0-9_-]"), "").take(12)
  if (direct.isNotBlank()) return direct
  val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
  return digest.take(6).joinToString("") { "%02x".format(it) }
}

fun nbgMaskedApiKey(apiKey: String): String =
  when {
    apiKey.length <= 8 -> "••••"
    else -> "${apiKey.take(4)}••••${apiKey.takeLast(4)}"
  }

private fun nbgCompactApiError(raw: String): String {
  if (raw.isBlank()) return "空响应"
  return runCatching {
    val root = JSONObject(raw)
    root.optJSONObject("error")?.let { error ->
      error.optString("message").ifBlank { error.toString() }
    } ?: root.optString("message").ifBlank { raw }
  }.getOrDefault(raw)
    .replace('\n', ' ')
    .take(180)
}

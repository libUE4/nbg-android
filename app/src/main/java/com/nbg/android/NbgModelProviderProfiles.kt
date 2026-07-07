package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_MODEL_PROVIDER_PROFILE_VERSION = "nbg-model-provider-profile-v1"

data class NbgModelProviderProfile(
  val id: String,
  val label: String,
  val baseUrlHint: String,
  val authHeader: String = "Authorization",
  val authPrefix: String = "Bearer ",
  val modelsPath: String = "/v1/models",
  val chatPath: String = "/v1/chat/completions",
  val apiMode: String = "openai",
  val defaultModel: String = "",
  val supportsThinking: Boolean = false,
  val extraBodyJson: String = "",
) {
  val statusLabel: String
    get() = if (baseUrlHint.isBlank()) "自定义" else apiMode
}

data class NbgModelProviderProfileState(
  val profiles: List<NbgModelProviderProfile> = nbgDefaultModelProviderProfiles(),
  val modelVersion: String = NBG_MODEL_PROVIDER_PROFILE_VERSION,
) {
  val profileCount: Int
    get() = profiles.size
}

internal fun nbgProfilesForStoredApis(entries: List<NbgStoredApi>): NbgModelProviderProfileState {
  return nbgProfilesForStoredApis(entries, emptyList())
}

internal fun nbgProfilesForStoredApis(
  entries: List<NbgStoredApi>,
  userProfiles: List<NbgModelProviderProfile>,
): NbgModelProviderProfileState {
  val defaults = nbgDefaultModelProviderProfiles()
  val custom = entries.map { entry ->
    val detected = nbgProfileForUrlApi(entry.baseUrl, entry.selectedModelId, userProfiles).takeIf { profile ->
      entry.baseUrl.contains(profile.baseUrlHint, ignoreCase = true) && profile.baseUrlHint.isNotBlank()
    }
    NbgModelProviderProfile(
      id = "url-api-${entry.id}",
      label = entry.name.ifBlank { detected?.label ?: "URL API" },
      baseUrlHint = entry.baseUrl,
      apiMode = detected?.apiMode ?: nbgHanakoProviderForUrlApi(entry.baseUrl, entry.selectedModelId).ifBlank { "openai" },
      defaultModel = entry.selectedModelId,
      supportsThinking = detected?.supportsThinking ?: false,
      extraBodyJson = detected?.extraBodyJson.orEmpty(),
    )
  }
  return NbgModelProviderProfileState(nbgMergeModelProviderProfiles(defaults, userProfiles, custom))
}

internal fun nbgProfileForUrlApi(baseUrl: String, modelId: String = ""): NbgModelProviderProfile {
  return nbgProfileForUrlApi(baseUrl, modelId, emptyList())
}

internal fun nbgProfileForUrlApi(
  baseUrl: String,
  modelId: String = "",
  userProfiles: List<NbgModelProviderProfile>,
): NbgModelProviderProfile {
  val normalized = nbgNormalizeApiBaseUrl(baseUrl)
  val detected = nbgDetectionModelProviderProfiles(userProfiles).firstOrNull { profile ->
    profile.baseUrlHint.isNotBlank() && normalized.contains(profile.baseUrlHint, ignoreCase = true)
  }
  return detected ?: NbgModelProviderProfile(
    id = "custom",
    label = "Custom",
    baseUrlHint = normalized,
    apiMode = nbgHanakoProviderForUrlApi(normalized, modelId).ifBlank { "openai" },
  )
}

internal class NbgModelProviderProfileTemplateStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun loadCustomProfiles(): List<NbgModelProviderProfile> =
    nbgParseCustomModelProviderProfiles(prefs.getString(KEY_PROFILES, null))

  fun load(entries: List<NbgStoredApi> = emptyList()): NbgModelProviderProfileState =
    nbgProfilesForStoredApis(entries, loadCustomProfiles())

  fun exportTemplates(): String =
    NbgModelProviderProfileState(profiles = loadCustomProfiles()).toJsonString()

  fun importTemplates(raw: String): NbgModelProviderProfileState {
    val profiles = nbgParseCustomModelProviderProfiles(raw)
    prefs.edit().putString(KEY_PROFILES, NbgModelProviderProfileState(profiles = profiles).toJsonString()).apply()
    return NbgModelProviderProfileState(profiles = profiles)
  }

  fun clear(): NbgModelProviderProfileState {
    prefs.edit().remove(KEY_PROFILES).apply()
    return NbgModelProviderProfileState(profiles = emptyList())
  }

  private companion object {
    const val PREFS = "nbg_model_provider_profiles"
    const val KEY_PROFILES = "custom_profiles"
  }
}

internal fun NbgModelProviderProfile.modelsEndpoint(baseUrl: String): String =
  nbgApiEndpoint(baseUrl, modelsPath.ifBlank { "/v1/models" })

internal fun NbgModelProviderProfile.chatEndpoint(baseUrl: String): String =
  nbgApiEndpoint(baseUrl, chatPath.ifBlank { "/v1/chat/completions" })

internal fun NbgModelProviderProfile.applyExtraBody(body: JSONObject): JSONObject {
  val raw = extraBodyJson.trim()
  if (raw.isBlank()) return body
  runCatching {
    val extra = JSONObject(raw)
    extra.keys().forEach { key ->
      val value = extra.opt(key)
      if (value != null && value != JSONObject.NULL) body.put(key, value)
    }
  }
  return body
}

internal fun parseNbgModelProviderProfiles(raw: String?): NbgModelProviderProfileState =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    val parsed = root.optJSONArray("profiles").toModelProviderProfiles()
    NbgModelProviderProfileState(nbgMergeModelProviderProfiles(nbgDefaultModelProviderProfiles(), parsed))
  }.getOrDefault(NbgModelProviderProfileState())

internal fun nbgParseCustomModelProviderProfiles(raw: String?): List<NbgModelProviderProfile> =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    root.optJSONArray("profiles")
      .toModelProviderProfiles()
      .map { it.sanitizedForTemplate() }
      .filter { it.id.isNotBlank() && it.label.isNotBlank() }
      .distinctBy { it.id }
  }.getOrDefault(emptyList())

internal fun nbgMergeModelProviderProfiles(
  vararg profileLists: List<NbgModelProviderProfile>,
): List<NbgModelProviderProfile> {
  val ordered = linkedMapOf<String, NbgModelProviderProfile>()
  profileLists.forEach { profiles ->
    profiles.forEach { profile ->
      val clean = profile.sanitizedForTemplate()
      if (clean.id.isNotBlank()) ordered[clean.id] = clean
    }
  }
  return ordered.values.toList()
}

internal fun NbgModelProviderProfileState.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("profiles", JSONArray(profiles.map { it.toJson() }))
    .toString()

internal fun JSONArray?.toModelProviderProfiles(): List<NbgModelProviderProfile> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val id = item.cleanString("id").orEmpty().trim()
      if (id.isBlank()) continue
      add(
        NbgModelProviderProfile(
          id = id.take(100),
          label = item.cleanString("label").orEmpty().ifBlank { id }.take(100),
          baseUrlHint = item.cleanString("baseUrlHint").orEmpty().take(240),
          authHeader = item.cleanString("authHeader").orEmpty().ifBlank { "Authorization" }.take(80),
          authPrefix = item.providerString("authPrefix")?.take(80) ?: "Bearer ",
          modelsPath = item.cleanString("modelsPath").orEmpty().ifBlank { "/v1/models" }.take(120),
          chatPath = item.cleanString("chatPath").orEmpty().ifBlank { "/v1/chat/completions" }.take(120),
          apiMode = item.cleanString("apiMode").orEmpty().ifBlank { "openai" }.take(40),
          defaultModel = item.cleanString("defaultModel").orEmpty().take(160),
          supportsThinking = item.optBoolean("supportsThinking", false),
          extraBodyJson = item.cleanString("extraBodyJson").orEmpty().take(2_000),
        ),
      )
    }
  }
}

private fun JSONObject.providerString(name: String): String? {
  if (!has(name) || isNull(name)) return null
  val value = optString(name)
  val normalized = value.trim()
  if (normalized.equals("null", ignoreCase = true) || normalized.equals("undefined", ignoreCase = true)) return null
  return value
}

private fun nbgDetectionModelProviderProfiles(userProfiles: List<NbgModelProviderProfile>): List<NbgModelProviderProfile> =
  (userProfiles.map { it.sanitizedForTemplate() } + nbgDefaultModelProviderProfiles()).distinctBy { it.id }

private fun NbgModelProviderProfile.sanitizedForTemplate(): NbgModelProviderProfile =
  copy(
    id = id.trim().take(100),
    label = label.trim().ifBlank { id.trim() }.take(100),
    baseUrlHint = baseUrlHint.trim().take(240),
    authHeader = authHeader.trim().ifBlank { "Authorization" }.take(80),
    authPrefix = authPrefix.take(80),
    modelsPath = modelsPath.trim().ifBlank { "/v1/models" }.take(120),
    chatPath = chatPath.trim().ifBlank { "/v1/chat/completions" }.take(120),
    apiMode = apiMode.trim().ifBlank { "openai" }.take(40),
    defaultModel = defaultModel.trim().take(160),
    extraBodyJson = extraBodyJson.sanitizedExtraBodyJson().take(2_000),
  )

private fun String.sanitizedExtraBodyJson(): String {
  val raw = trim()
  if (raw.isBlank()) return ""
  return runCatching {
    val extra = JSONObject(raw)
    listOf(
      "apiKey",
      "api_key",
      "api-key",
      "authorization",
      "Authorization",
      "x-api-key",
      "token",
      "access_token",
    ).forEach { key -> extra.remove(key) }
    extra.toString().takeIf { it != "{}" }.orEmpty()
  }.getOrDefault("")
}

private fun NbgModelProviderProfile.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("label", label)
    .put("baseUrlHint", baseUrlHint)
    .put("authHeader", authHeader)
    .put("authPrefix", authPrefix)
    .put("modelsPath", modelsPath)
    .put("chatPath", chatPath)
    .put("apiMode", apiMode)
    .put("defaultModel", defaultModel)
    .put("supportsThinking", supportsThinking)
    .put("extraBodyJson", extraBodyJson)

internal fun nbgDefaultModelProviderProfiles(): List<NbgModelProviderProfile> =
  listOf(
    NbgModelProviderProfile("openrouter", "OpenRouter", "openrouter.ai", apiMode = "openai", modelsPath = "/api/v1/models", supportsThinking = true),
    NbgModelProviderProfile("openai", "OpenAI", "api.openai.com", apiMode = "openai"),
    NbgModelProviderProfile("gemini", "Gemini", "generativelanguage.googleapis.com", apiMode = "openai", supportsThinking = true, extraBodyJson = """{"thinking_config":"optional"}"""),
    NbgModelProviderProfile("qwen", "Qwen", "dashscope.aliyuncs.com", apiMode = "openai", supportsThinking = true),
    NbgModelProviderProfile("kimi", "Kimi", "moonshot.cn", apiMode = "openai", supportsThinking = true),
    NbgModelProviderProfile("nous", "Nous Portal", "portal.nousresearch.com", apiMode = "openai"),
    NbgModelProviderProfile("ollama", "Ollama", "localhost:11434", apiMode = "openai", authPrefix = ""),
  )

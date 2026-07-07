package com.nbg.android

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
  val defaults = nbgDefaultModelProviderProfiles()
  val custom = entries.map { entry ->
    val detected = nbgDefaultModelProviderProfiles().firstOrNull { profile ->
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
  return NbgModelProviderProfileState((defaults + custom).distinctBy { it.id })
}

internal fun parseNbgModelProviderProfiles(raw: String?): NbgModelProviderProfileState =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    val parsed = root.optJSONArray("profiles").toModelProviderProfiles()
    NbgModelProviderProfileState((nbgDefaultModelProviderProfiles() + parsed).distinctBy { it.id })
  }.getOrDefault(NbgModelProviderProfileState())

internal fun NbgModelProviderProfileState.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("profiles", JSONArray(profiles.map { it.toJson() }))
    .toString()

private fun JSONArray?.toModelProviderProfiles(): List<NbgModelProviderProfile> {
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
          authPrefix = item.cleanString("authPrefix").orEmpty().take(80),
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

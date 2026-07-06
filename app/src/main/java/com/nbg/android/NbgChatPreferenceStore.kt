package com.nbg.android

import android.content.Context
import org.json.JSONObject

data class NbgChatPreferences(
  val modelProvider: String = "",
  val modelId: String = "",
  val modelLabel: String = "",
  val permissionMode: String = NBG_DEFAULT_PERMISSION_MODE,
  val thinkingLevel: String = "auto",
  val themeId: String = "light",
  val fontId: String = "system",
  val multiAgentEnabled: Boolean = true,
  val toolsetOverrides: Map<String, Boolean> = emptyMap(),
) {
  val hasModel: Boolean
    get() = modelProvider.isNotBlank() && modelId.isNotBlank()
}

class NbgChatPreferenceStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(): NbgChatPreferences =
    runCatching {
      val raw = prefs.getString(KEY_PREFS, "{}").orEmpty()
      val root = JSONObject(raw)
      val loaded = NbgChatPreferences(
        modelProvider = root.optString("modelProvider").trim(),
        modelId = root.optString("modelId").trim(),
        modelLabel = root.optString("modelLabel").trim(),
        permissionMode = root.optString("permissionMode").ifBlank { NBG_DEFAULT_PERMISSION_MODE },
        thinkingLevel = root.optString("thinkingLevel").ifBlank { "auto" },
        themeId = root.optString("themeId").ifBlank { "light" },
        fontId = root.optString("fontId").ifBlank { "system" },
        multiAgentEnabled = root.optBoolean("multiAgentEnabled", true),
        toolsetOverrides = root.optJSONObject("toolsets").toNbgToolsetOverrides(),
      ).normalized()
      if (raw != toJsonString(loaded)) save(loaded) else loaded
    }.getOrDefault(NbgChatPreferences())

  fun save(next: NbgChatPreferences): NbgChatPreferences {
    val normalized = next.normalized()
    prefs.edit()
      .putString(
        KEY_PREFS,
        toJsonString(normalized),
      )
      .apply()
    return normalized
  }

  fun saveModel(provider: String, id: String, label: String): NbgChatPreferences =
    save(load().copy(modelProvider = provider, modelId = id, modelLabel = label))

  fun savePermissionMode(mode: String): NbgChatPreferences =
    save(load().copy(permissionMode = mode))

  fun saveThinkingLevel(level: String): NbgChatPreferences =
    save(load().copy(thinkingLevel = level))

  fun saveTheme(themeId: String): NbgChatPreferences =
    save(load().copy(themeId = themeId))

  fun saveFont(fontId: String): NbgChatPreferences =
    save(load().copy(fontId = fontId))

  fun saveMultiAgentEnabled(enabled: Boolean): NbgChatPreferences =
    save(load().copy(multiAgentEnabled = enabled).withNbgToolsetEnabled(NbgToolsetId.AgentsTeam, enabled))

  internal fun saveToolsetEnabled(id: NbgToolsetId, enabled: Boolean): NbgChatPreferences =
    save(load().withNbgToolsetEnabled(id, enabled))

  private fun NbgChatPreferences.normalized(): NbgChatPreferences =
    copy(
      modelProvider = modelProvider.trim(),
      modelId = modelId.trim(),
      modelLabel = modelLabel.trim(),
      permissionMode = nbgNormalizePermissionMode(permissionMode),
      thinkingLevel = nbgNormalizeThinkingLevel(thinkingLevel) ?: "auto",
      themeId = nbgNormalizeThemeId(themeId),
      fontId = nbgNormalizeFontId(fontId),
      toolsetOverrides = nbgNormalizeToolsetOverrides(toolsetOverrides)
        .plus(NbgToolsetId.AgentsTeam.wireName to multiAgentEnabled),
    )

  private fun toJsonString(preferences: NbgChatPreferences): String =
    JSONObject()
      .put("modelProvider", preferences.modelProvider)
      .put("modelId", preferences.modelId)
      .put("modelLabel", preferences.modelLabel)
      .put("permissionMode", preferences.permissionMode)
      .put("thinkingLevel", preferences.thinkingLevel)
      .put("themeId", preferences.themeId)
      .put("fontId", preferences.fontId)
      .put("multiAgentEnabled", preferences.multiAgentEnabled)
      .put("toolsets", preferences.toNbgToolsetsJson())
      .toString()

  private companion object {
    const val PREFS = "nbg_chat_preferences"
    const val KEY_PREFS = "preferences"
  }
}

private fun JSONObject?.toNbgToolsetOverrides(): Map<String, Boolean> {
  val root = this ?: return emptyMap()
  return buildMap {
    NbgToolsetId.entries.forEach { id ->
      if (root.has(id.wireName)) put(id.wireName, root.optBoolean(id.wireName, id.defaultEnabled))
    }
  }
}

private fun NbgChatPreferences.toNbgToolsetsJson(): JSONObject =
  JSONObject().also { root ->
    NbgToolsetId.entries.forEach { id ->
      root.put(id.wireName, nbgToolsetEnabled(id))
    }
  }

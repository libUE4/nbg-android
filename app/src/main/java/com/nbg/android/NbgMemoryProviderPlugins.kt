package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_MEMORY_PROVIDER_PLUGIN_VERSION = "nbg-memory-provider-plugins-v1"

enum class NbgExternalMemoryProviderKind(val wireName: String, val label: String) {
  Honcho("honcho", "Honcho"),
  Mem0("mem0", "mem0"),
  Supermemory("supermemory", "supermemory"),
  Holographic("holographic", "Holographic"),
}

data class NbgExternalMemoryProviderConfig(
  val id: String,
  val kind: NbgExternalMemoryProviderKind,
  val displayName: String,
  val enabled: Boolean = false,
  val endpoint: String = "",
  val prefetchPath: String = "",
  val syncPath: String = "",
  val userId: String = "",
  val agentId: String = "",
  val accountLabel: String = "",
  val lastSyncAtMs: Long = 0L,
  val lastPrefetchAtMs: Long = 0L,
  val lastError: String = "",
) {
  val statusLabel: String
    get() = when {
      enabled && lastError.isNotBlank() -> "需要检查"
      enabled -> "已启用"
      else -> "已关闭"
    }
}

data class NbgExternalMemoryProviderState(
  val providers: List<NbgExternalMemoryProviderConfig> = nbgDefaultExternalMemoryProviders(),
  val modelVersion: String = NBG_MEMORY_PROVIDER_PLUGIN_VERSION,
) {
  val enabledCount: Int
    get() = providers.count { it.enabled }

  val disabledCount: Int
    get() = providers.count { !it.enabled }
}

internal class NbgExternalMemoryProviderStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val secrets = NbgEncryptedPreferenceSecretStore(
    context = context.applicationContext,
    prefsName = SECRET_PREFS,
    keyAlias = SECRET_ALIAS,
    keyPrefix = SECRET_PREFIX,
  )

  fun load(): NbgExternalMemoryProviderState =
    parseNbgExternalMemoryProviderState(prefs.getString(KEY_STATE, null))

  fun save(config: NbgExternalMemoryProviderConfig, apiKey: String = ""): NbgExternalMemoryProviderState {
    val normalized = config.normalized()
    if (apiKey.isNotBlank()) secrets.saveSecret(normalized.id, apiKey)
    val existing = load().providers.associateBy { it.id }.toMutableMap()
    existing[normalized.id] = normalized
    val state = NbgExternalMemoryProviderState(existing.values.sortedBy { it.kind.ordinal })
    prefs.edit().putString(KEY_STATE, state.toJsonString()).apply()
    return state
  }

  fun setEnabled(id: String, enabled: Boolean): NbgExternalMemoryProviderState {
    val state = load()
    val next = state.copy(providers = state.providers.map { if (it.id == id.trim()) it.copy(enabled = enabled).normalized() else it })
    prefs.edit().putString(KEY_STATE, next.toJsonString()).apply()
    return next
  }

  fun hasSecret(id: String): Boolean =
    secrets.loadSecret(id.trim()).orEmpty().isNotBlank()

  fun loadSecret(id: String): String? =
    secrets.loadSecret(id.trim())

  fun recordProviderResult(
    id: String,
    lastSyncAtMs: Long? = null,
    lastPrefetchAtMs: Long? = null,
    lastError: String? = null,
  ): NbgExternalMemoryProviderState {
    val cleanId = id.trim()
    val current = load()
    val next = current.copy(
      providers = current.providers.map { provider ->
        if (provider.id != cleanId) {
          provider
        } else {
          provider.copy(
            lastSyncAtMs = lastSyncAtMs ?: provider.lastSyncAtMs,
            lastPrefetchAtMs = lastPrefetchAtMs ?: provider.lastPrefetchAtMs,
            lastError = lastError ?: provider.lastError,
          ).normalized()
        }
      },
    )
    prefs.edit().putString(KEY_STATE, next.toJsonString()).apply()
    return next
  }

  private companion object {
    const val PREFS = "nbg_external_memory_providers"
    const val KEY_STATE = "state"
    const val SECRET_PREFS = "nbg_external_memory_provider_secrets"
    const val SECRET_ALIAS = "nbg_external_memory_provider_key_v1"
    const val SECRET_PREFIX = "provider_key_"
  }
}

internal fun parseNbgExternalMemoryProviderState(raw: String?): NbgExternalMemoryProviderState =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    val parsed = root.optJSONArray("providers").toExternalMemoryProviders()
    val byId = (nbgDefaultExternalMemoryProviders() + parsed).associateBy { it.id }
    NbgExternalMemoryProviderState(byId.values.sortedBy { it.kind.ordinal })
  }.getOrDefault(NbgExternalMemoryProviderState())

internal fun NbgExternalMemoryProviderState.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("providers", JSONArray(providers.map { it.toJson() }))
    .toString()

private fun JSONArray?.toExternalMemoryProviders(): List<NbgExternalMemoryProviderConfig> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val kind = nbgExternalMemoryProviderKind(item.cleanString("kind").orEmpty()) ?: continue
      val id = item.cleanString("id").orEmpty().ifBlank { kind.wireName }
      add(
        NbgExternalMemoryProviderConfig(
          id = id,
          kind = kind,
          displayName = item.cleanString("displayName").orEmpty().ifBlank { kind.label },
          enabled = item.optBoolean("enabled", false),
          endpoint = item.cleanString("endpoint").orEmpty(),
          prefetchPath = item.cleanString("prefetchPath").orEmpty(),
          syncPath = item.cleanString("syncPath").orEmpty(),
          userId = item.cleanString("userId").orEmpty(),
          agentId = item.cleanString("agentId").orEmpty(),
          accountLabel = item.cleanString("accountLabel").orEmpty(),
          lastSyncAtMs = item.optLong("lastSyncAtMs", 0L).coerceAtLeast(0L),
          lastPrefetchAtMs = item.optLong("lastPrefetchAtMs", 0L).coerceAtLeast(0L),
          lastError = item.cleanString("lastError").orEmpty(),
        ).normalized(),
      )
    }
  }
}

private fun NbgExternalMemoryProviderConfig.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("kind", kind.wireName)
    .put("displayName", displayName)
    .put("enabled", enabled)
    .put("endpoint", endpoint)
    .put("prefetchPath", prefetchPath)
    .put("syncPath", syncPath)
    .put("userId", userId)
    .put("agentId", agentId)
    .put("accountLabel", accountLabel)
    .put("lastSyncAtMs", lastSyncAtMs)
    .put("lastPrefetchAtMs", lastPrefetchAtMs)
    .put("lastError", lastError)

private fun NbgExternalMemoryProviderConfig.normalized(): NbgExternalMemoryProviderConfig =
  copy(
    id = id.trim().lowercase().replace(Regex("[^a-z0-9_.-]+"), "-").ifBlank { kind.wireName }.take(80),
    displayName = displayName.trim().ifBlank { kind.label }.take(80),
    enabled = enabled && endpoint.trim().isNotBlank(),
    endpoint = endpoint.trim().take(240),
    prefetchPath = prefetchPath.trim().take(120),
    syncPath = syncPath.trim().take(120),
    userId = userId.trim().take(120),
    agentId = agentId.trim().take(120),
    accountLabel = accountLabel.trim().take(80),
    lastSyncAtMs = lastSyncAtMs.coerceAtLeast(0L),
    lastPrefetchAtMs = lastPrefetchAtMs.coerceAtLeast(0L),
    lastError = lastError.trim().take(180),
  )

private fun nbgExternalMemoryProviderKind(value: String): NbgExternalMemoryProviderKind? =
  NbgExternalMemoryProviderKind.values().firstOrNull { it.wireName == value.trim().lowercase() }

internal fun nbgDefaultExternalMemoryProviders(): List<NbgExternalMemoryProviderConfig> =
  NbgExternalMemoryProviderKind.values().map { kind ->
    NbgExternalMemoryProviderConfig(id = kind.wireName, kind = kind, displayName = kind.label)
  }

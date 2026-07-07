package com.nbg.android

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NbgExternalMemoryAdapterResult(
  val providerId: String,
  val providerKind: NbgExternalMemoryProviderKind,
  val ok: Boolean,
  val message: String,
  val text: String = "",
  val itemCount: Int = 0,
)

internal class NbgExternalMemoryAdapter(
  private val store: NbgExternalMemoryProviderStore,
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .writeTimeout(8, TimeUnit.SECONDS)
    .build(),
) {
  constructor(context: android.content.Context) : this(NbgExternalMemoryProviderStore(context))

  fun prefetchAll(
    query: String,
    sessionId: String = "",
    limit: Int = 8,
    nowMs: Long = System.currentTimeMillis(),
  ): List<NbgMemoryProviderContextBlock> {
    val cleanQuery = query.nbgExternalMemoryCompact(limit = 800)
    if (cleanQuery.isBlank()) return emptyList()
    return store.load().providers
      .filter { it.enabled }
      .mapNotNull { provider ->
        val result = runCatching {
          prefetch(provider, cleanQuery, sessionId, limit)
        }.getOrElse { error ->
          store.recordProviderResult(provider.id, lastError = error.message ?: error.javaClass.simpleName)
          NbgExternalMemoryAdapterResult(provider.id, provider.kind, false, error.message ?: error.javaClass.simpleName)
        }
        if (result.ok) {
          store.recordProviderResult(provider.id, lastPrefetchAtMs = nowMs, lastError = "")
        }
        result.takeIf { it.ok && it.text.isNotBlank() }?.let {
          NbgMemoryProviderContextBlock(
            providerName = provider.displayName,
            providerKind = provider.kind.wireName,
            text = it.text,
            itemCount = it.itemCount,
            createdAtMs = nowMs.coerceAtLeast(0L),
          )
        }
      }
  }

  fun syncAll(
    turn: NbgLearningSourceTurn,
    nowMs: Long = System.currentTimeMillis(),
  ): List<NbgExternalMemoryAdapterResult> {
    val user = turn.userText.nbgExternalMemoryCompact(limit = 4_000)
    val assistant = turn.assistantText.nbgExternalMemoryCompact(limit = 4_000)
    if (user.isBlank() && assistant.isBlank()) return emptyList()
    return store.load().providers
      .filter { it.enabled }
      .map { provider ->
        runCatching {
          sync(provider, turn.copy(userText = user, assistantText = assistant))
        }.onSuccess {
          store.recordProviderResult(provider.id, lastSyncAtMs = nowMs, lastError = "")
        }.getOrElse { error ->
          store.recordProviderResult(provider.id, lastError = error.message ?: error.javaClass.simpleName)
          NbgExternalMemoryAdapterResult(provider.id, provider.kind, false, error.message ?: error.javaClass.simpleName)
        }
      }
  }

  private fun prefetch(
    provider: NbgExternalMemoryProviderConfig,
    query: String,
    sessionId: String,
    limit: Int,
  ): NbgExternalMemoryAdapterResult {
    val url = provider.endpoint.nbgExternalMemoryEndpoint(provider.prefetchPath.ifBlank { provider.kind.defaultPrefetchPath })
    val body = JSONObject()
      .put("provider", provider.kind.wireName)
      .put("query", query)
      .put("session_id", sessionId)
      .put("user_id", provider.userId.ifBlank { provider.accountLabel })
      .put("agent_id", provider.agentId.ifBlank { "nbg-android" })
      .put("limit", limit.coerceIn(1, 50))
    val raw = postJson(provider, url, body)
    val memories = nbgExtractExternalMemoryTexts(raw)
      .map { it.nbgExternalMemoryCompact(limit = 500) }
      .filter { it.isNotBlank() }
      .distinct()
      .take(limit.coerceIn(1, 50))
    return NbgExternalMemoryAdapterResult(
      providerId = provider.id,
      providerKind = provider.kind,
      ok = true,
      message = "prefetched ${memories.size}",
      text = memories.joinToString("\n") { "- $it" },
      itemCount = memories.size,
    )
  }

  private fun sync(
    provider: NbgExternalMemoryProviderConfig,
    turn: NbgLearningSourceTurn,
  ): NbgExternalMemoryAdapterResult {
    val url = provider.endpoint.nbgExternalMemoryEndpoint(provider.syncPath.ifBlank { provider.kind.defaultSyncPath })
    val body = JSONObject()
      .put("provider", provider.kind.wireName)
      .put("session_id", turn.sessionPath)
      .put("turn_id", turn.turnId)
      .put("timestamp_ms", turn.timestampMs)
      .put("user_id", provider.userId.ifBlank { provider.accountLabel })
      .put("agent_id", provider.agentId.ifBlank { "nbg-android" })
      .put(
        "messages",
        JSONArray()
          .put(JSONObject().put("role", "user").put("content", turn.userText))
          .put(JSONObject().put("role", "assistant").put("content", turn.assistantText)),
      )
      .put("user", turn.userText)
      .put("assistant", turn.assistantText)
    postJson(provider, url, body)
    return NbgExternalMemoryAdapterResult(provider.id, provider.kind, true, "synced")
  }

  private fun postJson(
    provider: NbgExternalMemoryProviderConfig,
    url: String,
    body: JSONObject,
  ): String {
    val apiKey = store.loadSecret(provider.id).orEmpty()
    val request = Request.Builder()
      .url(url)
      .addHeader("User-Agent", "NBG-Android/1.0")
      .addHeader("Content-Type", "application/json")
      .apply {
        if (apiKey.isNotBlank()) {
          addHeader("Authorization", "Bearer $apiKey")
          addHeader("x-api-key", apiKey)
        }
      }
      .post(body.toString().toRequestBody(JSON))
      .build()
    client.newCall(request).execute().use { response ->
      val raw = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        error("HTTP ${response.code}: ${raw.nbgCompactExternalMemoryError()}")
      }
      return raw
    }
  }

  private companion object {
    val JSON = "application/json; charset=utf-8".toMediaType()
  }
}

private fun String.nbgCompactExternalMemoryError(): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(220)
    .ifBlank { "empty response" }

private val NbgExternalMemoryProviderKind.defaultPrefetchPath: String
  get() = when (this) {
    NbgExternalMemoryProviderKind.Honcho -> "/v3/search"
    NbgExternalMemoryProviderKind.Mem0 -> "/v1/memories/search"
    NbgExternalMemoryProviderKind.Supermemory -> "/v4/search"
    NbgExternalMemoryProviderKind.Holographic -> "/query"
  }

private val NbgExternalMemoryProviderKind.defaultSyncPath: String
  get() = when (this) {
    NbgExternalMemoryProviderKind.Honcho -> "/v3/messages"
    NbgExternalMemoryProviderKind.Mem0 -> "/v1/memories"
    NbgExternalMemoryProviderKind.Supermemory -> "/v4/conversations"
    NbgExternalMemoryProviderKind.Holographic -> "/memories"
  }

private fun String.nbgExternalMemoryEndpoint(path: String): String {
  val base = trim().trimEnd('/')
  val cleanPath = path.trim().ifBlank { "/" }
  return base + if (cleanPath.startsWith('/')) cleanPath else "/$cleanPath"
}

internal fun nbgExtractExternalMemoryTexts(raw: String): List<String> =
  runCatching {
    val trimmed = raw.trim()
    when {
      trimmed.startsWith("[") -> JSONArray(trimmed).nbgCollectMemoryTexts()
      trimmed.startsWith("{") -> JSONObject(trimmed).nbgCollectMemoryTexts()
      else -> listOf(trimmed)
    }
  }.getOrDefault(emptyList())

private fun JSONObject.nbgCollectMemoryTexts(): List<String> {
  val keys = listOf("memory", "text", "content", "fact", "snippet", "summary", "value")
  val direct = keys.mapNotNull { key -> cleanString(key) }
  val nested = buildList {
    listOf("results", "memories", "data", "items", "documents").forEach { key ->
      optJSONArray(key)?.let { addAll(it.nbgCollectMemoryTexts()) }
      optJSONObject(key)?.let { addAll(it.nbgCollectMemoryTexts()) }
    }
  }
  return direct + nested
}

private fun JSONArray.nbgCollectMemoryTexts(): List<String> =
  buildList {
    for (index in 0 until length()) {
      optJSONObject(index)?.let { addAll(it.nbgCollectMemoryTexts()) }
        ?: optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
    }
  }

private fun String.nbgExternalMemoryCompact(limit: Int): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit.coerceAtLeast(0))

package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_AGENT_SOUL_MODEL_VERSION = "nbg-agent-soul-v1"

data class NbgAgentSoulConfig(
  val principles: List<String> = emptyList(),
  val styleHints: List<String> = emptyList(),
  val sourceEventIds: List<String> = emptyList(),
  val updatedAtMs: Long = 0L,
  val modelVersion: String = NBG_AGENT_SOUL_MODEL_VERSION,
)

internal interface NbgAgentSoulStorage {
  fun read(): String?
  fun write(raw: String)
}

internal class NbgSharedPreferencesAgentSoulStorage(context: Context) : NbgAgentSoulStorage {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  override fun read(): String? =
    prefs.getString(KEY_SOUL, null)

  override fun write(raw: String) {
    prefs.edit().putString(KEY_SOUL, raw).apply()
  }

  private companion object {
    const val PREFS = "nbg_agent_soul"
    const val KEY_SOUL = "soul"
  }
}

internal class NbgAgentSoulStore(
  private val storage: NbgAgentSoulStorage,
) {
  constructor(context: Context) : this(NbgSharedPreferencesAgentSoulStorage(context))

  fun load(): NbgAgentSoulConfig =
    parseNbgAgentSoulConfig(storage.read())

  fun save(config: NbgAgentSoulConfig): NbgAgentSoulConfig {
    val normalized = nbgBuildAgentSoulConfig(
      principles = config.principles,
      styleHints = config.styleHints,
      sourceEventIds = config.sourceEventIds,
      updatedAtMs = config.updatedAtMs,
    )
    storage.write(normalized.toJsonString())
    return normalized
  }
}

internal fun nbgBuildAgentSoulConfig(
  principles: List<String>,
  styleHints: List<String>,
  sourceEventIds: List<String> = emptyList(),
  updatedAtMs: Long = 0L,
): NbgAgentSoulConfig =
  NbgAgentSoulConfig(
    principles = principles.map(::nbgSoulLine).filter { it.isNotBlank() }.distinct().take(24),
    styleHints = styleHints.map(::nbgSoulLine).filter { it.isNotBlank() }.distinct().take(24),
    sourceEventIds = sourceEventIds.map { it.trim().take(80) }.filter { it.isNotBlank() }.distinct().take(24),
    updatedAtMs = updatedAtMs.coerceAtLeast(0L),
  )

internal fun nbgAgentSoulConfigFromLearningEvent(event: NbgLearningEvent): NbgAgentSoulConfig? {
  if (event.candidate.kind != NbgLearningCandidateKind.UserProfile) return null
  if (!event.candidate.title.contains("soul", ignoreCase = true) &&
    !event.candidate.tags.any { it.equals("soul", ignoreCase = true) }
  ) {
    return null
  }
  return nbgBuildAgentSoulConfig(
    principles = listOf(event.candidate.content),
    styleHints = emptyList(),
    sourceEventIds = listOf(event.id),
    updatedAtMs = event.updatedAtMs.coerceAtLeast(event.createdAtMs),
  )
}

internal fun parseNbgAgentSoulConfig(raw: String?): NbgAgentSoulConfig =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    nbgBuildAgentSoulConfig(
      principles = root.optJSONArray("principles").toSoulStrings(),
      styleHints = root.optJSONArray("styleHints").toSoulStrings(),
      sourceEventIds = root.optJSONArray("sourceEventIds").toSoulStrings(limit = 80),
      updatedAtMs = root.optLong("updatedAtMs", 0L),
    )
  }.getOrDefault(NbgAgentSoulConfig())

internal fun NbgAgentSoulConfig.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("principles", JSONArray(principles))
    .put("styleHints", JSONArray(styleHints))
    .put("sourceEventIds", JSONArray(sourceEventIds))
    .put("updatedAtMs", updatedAtMs)
    .toString()

private fun JSONArray?.toSoulStrings(limit: Int = 220): List<String> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val value = nbgSoulLine(array.optString(index), limit)
      if (value.isNotBlank()) add(value)
    }
  }
}

private fun nbgSoulLine(raw: String, limit: Int = 220): String =
  nbgRedactDiagnosticText(raw)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit)

package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_USER_PROFILE_MODEL_VERSION = "nbg-user-profile-v1"

data class NbgUserProfileEntry(
  val key: String,
  val value: String,
  val sourceEventIds: List<String> = emptyList(),
  val updatedAtMs: Long = 0L,
)

data class NbgUserProfile(
  val entries: List<NbgUserProfileEntry> = emptyList(),
  val modelVersion: String = NBG_USER_PROFILE_MODEL_VERSION,
) {
  val visibleEntries: List<NbgUserProfileEntry>
    get() = entries.filter { it.key.isNotBlank() && it.value.isNotBlank() }
}

internal interface NbgUserProfileStorage {
  fun read(): String?
  fun write(raw: String)
}

internal class NbgSharedPreferencesUserProfileStorage(context: Context) : NbgUserProfileStorage {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  override fun read(): String? =
    prefs.getString(KEY_PROFILE, null)

  override fun write(raw: String) {
    prefs.edit().putString(KEY_PROFILE, raw).apply()
  }

  private companion object {
    const val PREFS = "nbg_user_profile"
    const val KEY_PROFILE = "profile"
  }
}

internal class NbgUserProfileStore(
  private val storage: NbgUserProfileStorage,
) {
  constructor(context: Context) : this(NbgSharedPreferencesUserProfileStorage(context))

  fun load(): NbgUserProfile =
    parseNbgUserProfile(storage.read())

  fun save(profile: NbgUserProfile): NbgUserProfile {
    val normalized = nbgBuildUserProfile(profile.entries)
    storage.write(normalized.toJsonString())
    return normalized
  }

  fun upsert(entry: NbgUserProfileEntry): NbgUserProfile =
    save(NbgUserProfile(load().entries + entry))

  fun delete(key: String): NbgUserProfile {
    val cleanKey = key.nbgUserProfileKey()
    return save(NbgUserProfile(load().entries.filterNot { it.key == cleanKey }))
  }
}

internal fun nbgBuildUserProfile(entries: List<NbgUserProfileEntry>): NbgUserProfile {
  val deduped = entries
    .mapNotNull { it.normalizedUserProfileEntry() }
    .groupBy { it.key }
    .mapNotNull { (_, grouped) -> grouped.maxWithOrNull(compareBy<NbgUserProfileEntry> { it.updatedAtMs }.thenBy { it.value.length }) }
    .sortedBy { it.key }
  return NbgUserProfile(entries = deduped)
}

internal fun nbgUserProfileEntryFromLearningEvent(event: NbgLearningEvent): NbgUserProfileEntry? {
  if (event.candidate.kind != NbgLearningCandidateKind.UserProfile) return null
  val key = event.candidate.title.nbgUserProfileKey()
  val value = event.candidate.content.nbgUserProfileValue()
  if (key.isBlank() || value.isBlank()) return null
  return NbgUserProfileEntry(
    key = key,
    value = value,
    sourceEventIds = listOf(event.id).filter { it.isNotBlank() },
    updatedAtMs = event.updatedAtMs.coerceAtLeast(event.createdAtMs),
  )
}

internal fun parseNbgUserProfile(raw: String?): NbgUserProfile =
  runCatching {
    parseNbgUserProfile(JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}"))
  }.getOrDefault(NbgUserProfile())

internal fun parseNbgUserProfile(root: JSONObject): NbgUserProfile =
  nbgBuildUserProfile(root.optJSONArray("entries").toUserProfileEntries())

internal fun NbgUserProfile.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("entries", JSONArray().also { array ->
      entries.forEach { array.put(it.toJson()) }
    })
    .toString()

private fun NbgUserProfileEntry.normalizedUserProfileEntry(): NbgUserProfileEntry? {
  val cleanKey = key.nbgUserProfileKey()
  val cleanValue = value.nbgUserProfileValue()
  if (cleanKey.isBlank() || cleanValue.isBlank()) return null
  if (nbgMemorySensitiveFindings(cleanValue).isNotEmpty()) return null
  return copy(
    key = cleanKey,
    value = cleanValue,
    sourceEventIds = sourceEventIds.map { it.trim().take(80) }.filter { it.isNotBlank() }.distinct().take(12),
    updatedAtMs = updatedAtMs.coerceAtLeast(0L),
  )
}

private fun NbgUserProfileEntry.toJson(): JSONObject =
  JSONObject()
    .put("key", key)
    .put("value", nbgRedactDiagnosticText(value).take(1_000))
    .put("sourceEventIds", JSONArray(sourceEventIds))
    .put("updatedAtMs", updatedAtMs)

private fun JSONArray?.toUserProfileEntries(): List<NbgUserProfileEntry> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      add(
        NbgUserProfileEntry(
          key = item.cleanString("key").orEmpty(),
          value = item.cleanString("value").orEmpty(),
          sourceEventIds = item.optJSONArray("sourceEventIds").toUserProfileStrings(),
          updatedAtMs = item.optLong("updatedAtMs", 0L).coerceAtLeast(0L),
        ),
      )
    }
  }
}

private fun JSONArray?.toUserProfileStrings(): List<String> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val value = array.optString(index).trim().take(80)
      if (value.isNotBlank() && value != "null" && value != "undefined") add(value)
    }
  }.distinct()
}

private fun String.nbgUserProfileKey(): String =
  trim()
    .lowercase()
    .replace(Regex("[^a-z0-9_.-]+"), "_")
    .trim('_')
    .take(80)

private fun String.nbgUserProfileValue(): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(1_000)

package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_LOCAL_LEARNING_MEMORY_VERSION = "nbg-local-learning-memory-v1"

data class NbgLocalLearningMemoryEntry(
  val id: String,
  val type: String = "project_fact",
  val title: String = "",
  val content: String = "",
  val tags: List<String> = emptyList(),
  val sourceEventIds: List<String> = emptyList(),
  val sourceSessionPath: String = "",
  val sourceTurnId: String = "",
  val enabled: Boolean = true,
  val updatedAtMs: Long = 0L,
)

data class NbgLocalLearningMemory(
  val entries: List<NbgLocalLearningMemoryEntry> = emptyList(),
  val modelVersion: String = NBG_LOCAL_LEARNING_MEMORY_VERSION,
) {
  val enabledEntries: List<NbgLocalLearningMemoryEntry>
    get() = entries.filter { it.enabled }
}

internal interface NbgLocalLearningMemoryStorage {
  fun read(): String?
  fun write(raw: String)
}

internal class NbgSharedPreferencesLocalLearningMemoryStorage(context: Context) : NbgLocalLearningMemoryStorage {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  override fun read(): String? =
    prefs.getString(KEY_MEMORY, null)

  override fun write(raw: String) {
    prefs.edit().putString(KEY_MEMORY, raw).apply()
  }

  private companion object {
    const val PREFS = "nbg_local_learning_memory"
    const val KEY_MEMORY = "memory"
  }
}

internal class NbgLocalLearningMemoryStore(
  private val storage: NbgLocalLearningMemoryStorage,
) {
  constructor(context: Context) : this(NbgSharedPreferencesLocalLearningMemoryStorage(context))

  fun load(): NbgLocalLearningMemory =
    parseNbgLocalLearningMemory(storage.read())

  fun save(memory: NbgLocalLearningMemory): NbgLocalLearningMemory {
    val normalized = nbgBuildLocalLearningMemory(memory.entries)
    storage.write(normalized.toJsonString())
    return normalized
  }

  fun upsert(entry: NbgLocalLearningMemoryEntry): NbgLocalLearningMemory =
    save(NbgLocalLearningMemory(load().entries + entry))

  fun delete(id: String): NbgLocalLearningMemory {
    val cleanId = id.trim()
    if (cleanId.isBlank()) return load()
    return save(NbgLocalLearningMemory(load().entries.filterNot { it.id == cleanId }))
  }
}

internal fun nbgLocalLearningMemoryEntryFromEvent(event: NbgLearningEvent): NbgLocalLearningMemoryEntry? {
  if (event.candidate.kind != NbgLearningCandidateKind.Memory) return null
  val content = event.candidate.content.nbgLocalMemoryText(limit = NBG_MEMORY_MAX_CONTENT_CHARS)
  if (content.isBlank()) return null
  val type = nbgLocalLearningMemoryType(event.candidate.tags, event.candidate.title, content)
  val input = HanakoMemoryInput(
    id = event.id,
    type = type,
    title = event.candidate.title,
    content = content,
    tags = event.candidate.tags,
    enabled = true,
  )
  if (!nbgReviewMemoryInput(input).allowSave) return null
  return NbgLocalLearningMemoryEntry(
    id = event.id,
    type = type,
    title = event.candidate.title.nbgLocalMemoryText(limit = 160).ifBlank { "自动记忆" },
    content = content,
    tags = event.candidate.tags.map { it.nbgLocalMemoryText(limit = 48) }.filter { it.isNotBlank() }.distinct().take(16),
    sourceEventIds = listOf(event.id),
    sourceSessionPath = event.candidate.sourceSessionPath.nbgLocalMemoryText(limit = 180),
    sourceTurnId = event.candidate.sourceTurnId.nbgLocalMemoryText(limit = 120),
    enabled = true,
    updatedAtMs = event.updatedAtMs.coerceAtLeast(event.createdAtMs),
  )
}

internal fun nbgBuildLocalLearningMemory(entries: List<NbgLocalLearningMemoryEntry>): NbgLocalLearningMemory {
  val normalized = entries
    .mapNotNull { it.normalizedLocalMemoryEntry() }
    .groupBy { it.id }
    .mapNotNull { (_, grouped) -> grouped.maxWithOrNull(compareBy<NbgLocalLearningMemoryEntry> { it.updatedAtMs }.thenBy { it.content.length }) }
    .sortedByDescending { it.updatedAtMs }
    .take(200)
  return NbgLocalLearningMemory(entries = normalized)
}

internal fun parseNbgLocalLearningMemory(raw: String?): NbgLocalLearningMemory =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    nbgBuildLocalLearningMemory(root.optJSONArray("entries").toLocalLearningMemoryEntries())
  }.getOrDefault(NbgLocalLearningMemory())

internal fun NbgLocalLearningMemory.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("entries", JSONArray().also { array ->
      entries.forEach { array.put(it.toJson()) }
    })
    .toString()

private fun NbgLocalLearningMemoryEntry.normalizedLocalMemoryEntry(): NbgLocalLearningMemoryEntry? {
  val cleanId = id.trim().ifBlank { listOf(type, title, content).joinToString("\u001f").sha256Hex().take(24) }
  val cleanContent = content.nbgLocalMemoryText(limit = NBG_MEMORY_MAX_CONTENT_CHARS)
  if (cleanContent.isBlank()) return null
  if (nbgMemorySensitiveFindings(cleanContent).isNotEmpty()) return null
  return copy(
    id = cleanId,
    type = nbgNormalizeMemoryType(type),
    title = title.nbgLocalMemoryText(limit = 160).ifBlank { cleanContent.take(80) },
    content = cleanContent,
    tags = tags.map { it.nbgLocalMemoryText(limit = 48) }.filter { it.isNotBlank() }.distinct().take(16),
    sourceEventIds = sourceEventIds.map { it.trim().take(80) }.filter { it.isNotBlank() }.distinct().take(12),
    sourceSessionPath = sourceSessionPath.nbgLocalMemoryText(limit = 180),
    sourceTurnId = sourceTurnId.nbgLocalMemoryText(limit = 120),
    updatedAtMs = updatedAtMs.coerceAtLeast(0L),
  )
}

private fun NbgLocalLearningMemoryEntry.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("type", type)
    .put("title", nbgRedactDiagnosticText(title))
    .put("content", nbgRedactDiagnosticText(content))
    .put("tags", JSONArray(tags))
    .put("sourceEventIds", JSONArray(sourceEventIds))
    .put("sourceSessionPath", if (sourceSessionPath.isBlank()) "" else "[session-path]")
    .put("sourceTurnId", sourceTurnId)
    .put("enabled", enabled)
    .put("updatedAtMs", updatedAtMs)

private fun JSONArray?.toLocalLearningMemoryEntries(): List<NbgLocalLearningMemoryEntry> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      add(
        NbgLocalLearningMemoryEntry(
          id = item.cleanString("id").orEmpty(),
          type = item.cleanString("type").orEmpty(),
          title = item.cleanString("title").orEmpty(),
          content = item.cleanString("content").orEmpty(),
          tags = item.optJSONArray("tags").toLocalMemoryStrings(48),
          sourceEventIds = item.optJSONArray("sourceEventIds").toLocalMemoryStrings(80),
          sourceSessionPath = item.cleanString("sourceSessionPath").orEmpty(),
          sourceTurnId = item.cleanString("sourceTurnId").orEmpty(),
          enabled = item.optBoolean("enabled", true),
          updatedAtMs = item.optLong("updatedAtMs", 0L).coerceAtLeast(0L),
        ),
      )
    }
  }
}

private fun JSONArray?.toLocalMemoryStrings(limit: Int): List<String> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val value = array.optString(index).nbgLocalMemoryText(limit)
      if (value.isNotBlank() && value != "null" && value != "undefined") add(value)
    }
  }.distinct()
}

private fun nbgLocalLearningMemoryType(tags: List<String>, title: String, content: String): String {
  val text = (tags + title + content).joinToString(" ").lowercase()
  return when {
    "decision" in text || "决定" in text -> "decision"
    "bug" in text || "错误" in text || "问题" in text -> "bug_note"
    "preference" in text || "偏好" in text -> "user_preference"
    "handoff" in text || "交接" in text -> "handoff"
    else -> "project_fact"
  }
}

private fun String.nbgLocalMemoryText(limit: Int): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit.coerceAtLeast(0))

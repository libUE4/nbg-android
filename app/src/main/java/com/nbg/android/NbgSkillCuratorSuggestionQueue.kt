package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_SKILL_CURATOR_SUGGESTION_QUEUE_VERSION = "nbg-skill-curator-suggestion-queue-v1"

enum class NbgSkillCuratorSuggestionStatus(val wireName: String, val label: String) {
  Pending("pending", "待处理"),
  Accepted("accepted", "已接受"),
  Applied("applied", "已应用"),
  Ignored("ignored", "已忽略"),
  Blocked("blocked", "已阻止"),
}

data class NbgSkillCuratorSuggestionEntry(
  val id: String,
  val skillName: String,
  val action: String,
  val reason: String,
  val patchHint: String = "",
  val status: NbgSkillCuratorSuggestionStatus = NbgSkillCuratorSuggestionStatus.Pending,
  val source: String = "llm_curator",
  val createdAtMs: Long = 0L,
  val updatedAtMs: Long = 0L,
  val statusMessage: String = "",
)

data class NbgSkillCuratorSuggestionQueue(
  val entries: List<NbgSkillCuratorSuggestionEntry> = emptyList(),
  val modelVersion: String = NBG_SKILL_CURATOR_SUGGESTION_QUEUE_VERSION,
) {
  val pendingEntries: List<NbgSkillCuratorSuggestionEntry>
    get() = entries.filter { it.status == NbgSkillCuratorSuggestionStatus.Pending }

  val pendingCount: Int
    get() = pendingEntries.size
}

internal class NbgSkillCuratorSuggestionQueueStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(): NbgSkillCuratorSuggestionQueue =
    parseNbgSkillCuratorSuggestionQueue(prefs.getString(KEY_QUEUE, null))

  fun recordSuggestions(
    suggestions: List<NbgSkillCuratorLlmSuggestion>,
    nowMs: Long = System.currentTimeMillis(),
  ): NbgSkillCuratorSuggestionQueue {
    if (suggestions.isEmpty()) return load()
    val existing = load().entries.associateBy { it.id }.toMutableMap()
    suggestions.map { it.toQueueEntry(nowMs) }.forEach { entry ->
      existing.putIfAbsent(entry.id, entry)
    }
    return save(NbgSkillCuratorSuggestionQueue(existing.values.sortedByDescending { it.createdAtMs }.take(200)))
  }

  fun updateStatus(
    id: String,
    status: NbgSkillCuratorSuggestionStatus,
    message: String = "",
    nowMs: Long = System.currentTimeMillis(),
  ): NbgSkillCuratorSuggestionQueue {
    val cleanId = id.trim()
    if (cleanId.isBlank()) return load()
    return save(
      NbgSkillCuratorSuggestionQueue(
        load().entries.map { entry ->
          if (entry.id == cleanId) {
            entry.copy(status = status, statusMessage = message.take(240), updatedAtMs = nowMs.coerceAtLeast(entry.updatedAtMs))
          } else {
            entry
          }
        },
      ),
    )
  }

  private fun save(queue: NbgSkillCuratorSuggestionQueue): NbgSkillCuratorSuggestionQueue {
    val normalized = queue.normalized()
    prefs.edit().putString(KEY_QUEUE, normalized.toJsonString()).apply()
    return normalized
  }

  private companion object {
    const val PREFS = "nbg_skill_curator_suggestion_queue"
    const val KEY_QUEUE = "queue"
  }
}

internal fun parseNbgSkillCuratorSuggestionQueue(raw: String?): NbgSkillCuratorSuggestionQueue =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    val array = root.optJSONArray("entries") ?: JSONArray()
    NbgSkillCuratorSuggestionQueue(
      entries = buildList {
        for (index in 0 until array.length()) {
          val item = array.optJSONObject(index) ?: continue
          val skillName = item.cleanString("skillName").orEmpty()
          val action = item.cleanString("action").orEmpty()
          if (skillName.isBlank() || action.isBlank()) continue
          add(
            NbgSkillCuratorSuggestionEntry(
              id = item.cleanString("id").orEmpty(),
              skillName = skillName,
              action = action,
              reason = item.cleanString("reason").orEmpty(),
              patchHint = item.cleanString("patchHint").orEmpty(),
              status = nbgSkillCuratorSuggestionStatus(item.cleanString("status").orEmpty()),
              source = item.cleanString("source").orEmpty(),
              createdAtMs = item.optLong("createdAtMs", 0L),
              updatedAtMs = item.optLong("updatedAtMs", 0L),
              statusMessage = item.cleanString("statusMessage").orEmpty(),
            ).normalized(),
          )
        }
      },
    ).normalized()
  }.getOrDefault(NbgSkillCuratorSuggestionQueue())

private fun NbgSkillCuratorSuggestionQueue.normalized(): NbgSkillCuratorSuggestionQueue =
  copy(
    entries = entries
      .map { it.normalized() }
      .distinctBy { it.id }
      .sortedByDescending { it.createdAtMs }
      .take(200),
  )

private fun NbgSkillCuratorSuggestionEntry.normalized(): NbgSkillCuratorSuggestionEntry {
  val cleanSkillName = skillName.trim().take(160)
  val cleanAction = action.trim().lowercase().take(40)
  return copy(
    id = id.trim().ifBlank {
      listOf(cleanSkillName, cleanAction, reason, patchHint).joinToString("\u001f").sha256Hex().take(24)
    },
    skillName = cleanSkillName,
    action = cleanAction,
    reason = reason.trim().take(500),
    patchHint = patchHint.trim().take(1_000),
    source = source.trim().ifBlank { "llm_curator" }.take(80),
    createdAtMs = createdAtMs.coerceAtLeast(0L),
    updatedAtMs = updatedAtMs.coerceAtLeast(0L),
    statusMessage = statusMessage.trim().take(240),
  )
}

private fun NbgSkillCuratorLlmSuggestion.toQueueEntry(nowMs: Long): NbgSkillCuratorSuggestionEntry =
  NbgSkillCuratorSuggestionEntry(
    id = listOf(skillName, action, reason, patchHint).joinToString("\u001f").sha256Hex().take(24),
    skillName = skillName,
    action = action,
    reason = reason,
    patchHint = patchHint,
    createdAtMs = nowMs.coerceAtLeast(0L),
    updatedAtMs = nowMs.coerceAtLeast(0L),
  ).normalized()

private fun nbgSkillCuratorSuggestionStatus(value: String): NbgSkillCuratorSuggestionStatus =
  NbgSkillCuratorSuggestionStatus.values().firstOrNull { it.wireName == value.trim().lowercase() }
    ?: NbgSkillCuratorSuggestionStatus.Pending

private fun NbgSkillCuratorSuggestionQueue.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("entries", JSONArray(entries.map { it.toJson() }))
    .toString()

private fun NbgSkillCuratorSuggestionEntry.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("skillName", skillName)
    .put("action", action)
    .put("reason", reason)
    .put("patchHint", patchHint)
    .put("status", status.wireName)
    .put("source", source)
    .put("createdAtMs", createdAtMs)
    .put("updatedAtMs", updatedAtMs)
    .put("statusMessage", statusMessage)

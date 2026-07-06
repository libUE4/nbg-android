package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject

internal val HANA_MEMORY_TYPES = listOf(
  "project_fact",
  "user_preference",
  "decision",
  "handoff",
  "bug_note",
)

internal const val NBG_MEMORY_AUDIT_MAX_EVENTS = 20
internal const val NBG_MEMORY_AUDIT_DIAGNOSTIC_TAIL_LIMIT = 10

data class HanakoMemoryAuditEvent(
  val action: String,
  val result: String,
  val normalizedType: String = "",
  val risk: String = "",
  val policyVersion: String = NBG_MEMORY_CONTEXT_POLICY_VERSION,
  val timestampMillis: Long = 0L,
)

data class HanakoMemoryItem(
  val id: String,
  val type: String = "project_fact",
  val title: String = "",
  val content: String = "",
  val tags: List<String> = emptyList(),
  val sourceSession: String = "",
  val sourceTurnId: String = "",
  val enabled: Boolean = true,
  val createdAt: String = "",
  val updatedAt: String = "",
) {
  val displayTitle: String
    get() = title.ifBlank { content.lineSequence().firstOrNull().orEmpty().take(80).ifBlank { id } }
}

data class HanakoMemoryState(
  val items: List<HanakoMemoryItem> = emptyList(),
  val count: Int = 0,
  val enabledCount: Int = 0,
  val auditEvents: List<HanakoMemoryAuditEvent> = emptyList(),
)

data class HanakoMemoryInput(
  val id: String = "",
  val type: String = "project_fact",
  val title: String = "",
  val content: String = "",
  val tags: List<String> = emptyList(),
  val enabled: Boolean = true,
)

internal fun parseHanakoMemoryState(root: JSONObject): HanakoMemoryState {
  val items = root.optJSONArray("items").toMemoryItems()
  return HanakoMemoryState(
    items = items,
    count = root.optInt("count", items.size),
    enabledCount = root.optInt("enabledCount", items.count { it.enabled }),
  )
}

internal fun parseHanakoMemoryItem(root: JSONObject?): HanakoMemoryItem? {
  root ?: return null
  val id = root.cleanString("id") ?: return null
  return HanakoMemoryItem(
    id = id,
    type = root.cleanString("type") ?: "project_fact",
    title = root.cleanString("title").orEmpty(),
    content = root.cleanString("content").orEmpty(),
    tags = root.optJSONArray("tags").toMemoryStringList(),
    sourceSession = root.cleanString("sourceSession").orEmpty(),
    sourceTurnId = root.cleanString("sourceTurnId").orEmpty(),
    enabled = root.optBoolean("enabled", true),
    createdAt = root.cleanString("createdAt").orEmpty(),
    updatedAt = root.cleanString("updatedAt").orEmpty(),
  )
}

internal fun HanakoMemoryInput.toJson(): JSONObject =
  JSONObject()
    .put("id", id.takeIf { it.isNotBlank() })
    .put("type", nbgNormalizeMemoryType(type))
    .put("title", title.trim())
    .put("content", content.trim())
    .put("tags", JSONArray(tags.map { it.trim() }.filter { it.isNotBlank() }))
    .put("enabled", enabled)

internal fun nbgNormalizeMemoryType(type: String): String =
  type.trim().takeIf { it in HANA_MEMORY_TYPES } ?: "project_fact"

internal fun nbgMemoryTypeLabel(type: String): String =
  when (nbgNormalizeMemoryType(type)) {
    "user_preference" -> "用户偏好"
    "decision" -> "技术决策"
    "handoff" -> "交接摘要"
    "bug_note" -> "问题记录"
    else -> "项目事实"
  }

internal fun nbgDiagnosticMemoryError(raw: String): String =
  if (raw.isBlank()) "" else "memory_error_present"

internal fun nbgMemoryAuditEvent(
  action: String,
  result: String,
  normalizedType: String = "",
  risk: String = "",
  timestampMillis: Long = System.currentTimeMillis(),
): HanakoMemoryAuditEvent =
  HanakoMemoryAuditEvent(
    action = nbgNormalizeMemoryAuditAction(action),
    result = nbgNormalizeMemoryAuditResult(result),
    normalizedType = normalizedType.takeIf { it in HANA_MEMORY_TYPES }.orEmpty(),
    risk = risk.takeIf { it in NbgMemoryContextRisk.entries.map { entry -> entry.wireName } }.orEmpty(),
    timestampMillis = timestampMillis.coerceAtLeast(0L),
  )

internal fun HanakoMemoryState.withMemoryAuditEvent(event: HanakoMemoryAuditEvent): HanakoMemoryState =
  copy(auditEvents = (auditEvents + event).takeLast(NBG_MEMORY_AUDIT_MAX_EVENTS))

private fun nbgNormalizeMemoryAuditAction(action: String): String =
  when (action.trim()) {
    "create", "update", "delete" -> action.trim()
    else -> "unknown"
  }

private fun nbgNormalizeMemoryAuditResult(result: String): String =
  when (result.trim()) {
    "success", "failure", "blocked" -> result.trim()
    else -> "unknown"
  }

private fun JSONArray?.toMemoryItems(): List<HanakoMemoryItem> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      parseHanakoMemoryItem(array.optJSONObject(index))?.let { add(it) }
    }
  }
}

private fun JSONArray?.toMemoryStringList(): List<String> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val value = array.optString(index).trim()
      if (value.isNotBlank() && value != "null" && value != "undefined") add(value)
    }
  }
}

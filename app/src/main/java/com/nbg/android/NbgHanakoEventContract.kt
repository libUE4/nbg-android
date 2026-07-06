package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal const val NBG_HANAKO_EVENT_CONTRACT_VERSION = "nbg-android-hanako-events-v1"
internal const val NBG_HANAKO_EVENT_SCHEMA_VERSION = "nbg-android-hanako-event-schema-v1"
internal const val NBG_CONFIRMATION_RESOLUTION_AUDIT_SCHEMA_VERSION = "nbg-confirmation-resolution-audit-v1"

internal enum class NbgHanakoEventKind(val wireName: String) {
  StreamingStatus("streaming_status"),
  StreamResume("stream_resume"),
  AssistantText("assistant_text"),
  Thinking("thinking"),
  Tool("tool"),
  Confirmation("confirmation"),
  ContentBlock("content_block"),
  TeamTask("team_task"),
  Session("session"),
  RuntimeSignal("runtime_signal"),
  Error("error"),
  Unknown("unknown"),
}

internal data class NbgHanakoEventSchemaEntry(
  val type: String,
  val kind: NbgHanakoEventKind,
  val requiredFields: List<String>,
  val optionalFields: List<String> = emptyList(),
  val sensitiveFields: List<String> = emptyList(),
  val terminalEvent: Boolean = false,
  val notes: String = "",
)

data class NbgConfirmationResolutionAuditEntry(
  val schemaVersion: String,
  val confirmIdHash: String,
  val action: String,
  val result: String,
  val riskTier: String,
  val subjectHash: String,
  val targetHash: String,
  val timestampMillis: Long,
  val signature: String,
)

private val NBG_HANAKO_TOOL_SCHEMA_OPTIONAL_FIELDS = listOf(
  "toolCallId",
  "id",
  "name",
  "toolName",
  "status",
  "title",
  "subtitle",
  "detail",
  "args",
  "input",
  "output",
  "path",
)

private val NBG_HANAKO_TOOL_SCHEMA_SENSITIVE_FIELDS = listOf(
  "args",
  "input",
  "output",
  "path",
  "detail",
)

private val NBG_HANAKO_TEAM_SCHEMA_OPTIONAL_FIELDS = listOf(
  "task",
  "taskId",
  "agent",
  "agentId",
  "role",
  "status",
  "summary",
  "artifactRefs",
)

private val NBG_HANAKO_TEAM_SCHEMA_SENSITIVE_FIELDS = listOf(
  "summary",
  "artifactRefs",
)

internal val NBG_HANAKO_SUPPORTED_EVENT_TYPES = setOf(
  "status",
  "session_status",
  "stream_resume",
  "context_usage",
  "compaction_start",
  "compaction_end",
  "text_delta",
  "card_text",
  "mood_text",
  "thinking_start",
  "thinking_delta",
  "thinking_end",
  "slash_result",
  "session_user_message",
  "content_block",
  "tool_progress",
  "tool_update",
  "tool_status",
  "terminal_output",
  "terminal_status",
  "file_write_prepare",
  "vision_progress",
  "tool_start",
  "tool_call",
  "tool_invocation",
  "tool_end",
  "tool_result",
  "turn_end",
  "session_title",
  "session_branch_reset",
  "block_update",
  "todo_update",
  "team_task_started",
  "team_task_completed",
  "team_task_failed",
  "team_agent_started",
  "team_agent_update",
  "team_agent_result",
  "browser_status",
  "browser_bg_status",
  "desk_changed",
  "token_usage",
  "plan_mode",
  "permission_mode",
  "access_mode",
  "bridge_status",
  "deferred_result",
  "error",
  "confirmation_resolved",
)

internal val NBG_HANAKO_EVENT_SCHEMA_ENTRIES: List<NbgHanakoEventSchemaEntry> = listOf(
  nbgHanakoEventSchema("status", NbgHanakoEventKind.StreamingStatus, optional = listOf("status", "sessionPath", "streamId", "seq"), terminal = true, notes = "Terminal only when status normalizes to an idle/done/failed state."),
  nbgHanakoEventSchema("session_status", NbgHanakoEventKind.StreamingStatus, optional = listOf("status", "sessionPath", "streamId", "seq"), terminal = true, notes = "Terminal only when status normalizes to an idle/done/failed state."),
  nbgHanakoEventSchema("stream_resume", NbgHanakoEventKind.StreamResume, required = listOf("sessionPath"), optional = listOf("streamId", "events", "sinceSeq", "nextSeq", "truncated"), sensitive = listOf("events", "sessionPath", "streamId")),
  nbgHanakoEventSchema("context_usage", NbgHanakoEventKind.RuntimeSignal, optional = listOf("used", "limit", "percent")),
  nbgHanakoEventSchema("compaction_start", NbgHanakoEventKind.RuntimeSignal, optional = listOf("reason")),
  nbgHanakoEventSchema("compaction_end", NbgHanakoEventKind.RuntimeSignal, optional = listOf("reason", "summary")),
  nbgHanakoEventSchema("text_delta", NbgHanakoEventKind.AssistantText, optional = listOf("delta", "text", "sessionPath", "streamId", "seq"), sensitive = listOf("delta", "text")),
  nbgHanakoEventSchema("card_text", NbgHanakoEventKind.AssistantText, optional = listOf("delta", "text", "sessionPath", "streamId", "seq"), sensitive = listOf("delta", "text")),
  nbgHanakoEventSchema("mood_text", NbgHanakoEventKind.RuntimeSignal, optional = listOf("text", "mood"), sensitive = listOf("text")),
  nbgHanakoEventSchema("thinking_start", NbgHanakoEventKind.Thinking, optional = listOf("text", "delta", "sessionPath", "streamId", "seq"), sensitive = listOf("text", "delta")),
  nbgHanakoEventSchema("thinking_delta", NbgHanakoEventKind.Thinking, optional = listOf("text", "delta", "sessionPath", "streamId", "seq"), sensitive = listOf("text", "delta")),
  nbgHanakoEventSchema("thinking_end", NbgHanakoEventKind.Thinking, optional = listOf("sessionPath", "streamId", "seq")),
  nbgHanakoEventSchema("slash_result", NbgHanakoEventKind.AssistantText, optional = listOf("text", "result"), sensitive = listOf("text", "result")),
  nbgHanakoEventSchema("session_user_message", NbgHanakoEventKind.AssistantText, optional = listOf("text", "message"), sensitive = listOf("text", "message")),
  nbgHanakoEventSchema("content_block", NbgHanakoEventKind.ContentBlock, optional = listOf("block", "content", "sessionPath", "streamId", "seq"), sensitive = listOf("block", "content")),
  nbgHanakoEventSchema("tool_progress", NbgHanakoEventKind.Tool, optional = NBG_HANAKO_TOOL_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TOOL_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("tool_update", NbgHanakoEventKind.Tool, optional = NBG_HANAKO_TOOL_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TOOL_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("tool_status", NbgHanakoEventKind.Tool, optional = NBG_HANAKO_TOOL_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TOOL_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("terminal_output", NbgHanakoEventKind.Tool, optional = listOf("toolCallId", "id", "command", "cwd", "output", "chunk", "status"), sensitive = listOf("command", "cwd", "output", "chunk")),
  nbgHanakoEventSchema("terminal_status", NbgHanakoEventKind.Tool, optional = listOf("toolCallId", "id", "command", "cwd", "status", "exitCode", "message"), sensitive = listOf("command", "cwd", "message")),
  nbgHanakoEventSchema("file_write_prepare", NbgHanakoEventKind.Tool, optional = listOf("prepareKey", "path", "diff", "preview", "status"), sensitive = listOf("path", "diff", "preview")),
  nbgHanakoEventSchema("vision_progress", NbgHanakoEventKind.Tool, optional = NBG_HANAKO_TOOL_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TOOL_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("tool_start", NbgHanakoEventKind.Tool, optional = NBG_HANAKO_TOOL_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TOOL_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("tool_call", NbgHanakoEventKind.Tool, optional = NBG_HANAKO_TOOL_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TOOL_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("tool_invocation", NbgHanakoEventKind.Tool, optional = NBG_HANAKO_TOOL_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TOOL_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("tool_end", NbgHanakoEventKind.Tool, optional = NBG_HANAKO_TOOL_SCHEMA_OPTIONAL_FIELDS + "error", sensitive = NBG_HANAKO_TOOL_SCHEMA_SENSITIVE_FIELDS + "error"),
  nbgHanakoEventSchema("tool_result", NbgHanakoEventKind.Tool, optional = NBG_HANAKO_TOOL_SCHEMA_OPTIONAL_FIELDS + "result", sensitive = NBG_HANAKO_TOOL_SCHEMA_SENSITIVE_FIELDS + "result"),
  nbgHanakoEventSchema("turn_end", NbgHanakoEventKind.Session, terminal = true),
  nbgHanakoEventSchema("session_title", NbgHanakoEventKind.Session, optional = listOf("title", "sessionPath"), sensitive = listOf("title", "sessionPath")),
  nbgHanakoEventSchema("session_branch_reset", NbgHanakoEventKind.Session, optional = listOf("sessionPath"), sensitive = listOf("sessionPath")),
  nbgHanakoEventSchema("block_update", NbgHanakoEventKind.ContentBlock, optional = listOf("block", "content", "status"), sensitive = listOf("block", "content")),
  nbgHanakoEventSchema("todo_update", NbgHanakoEventKind.Session, optional = listOf("todos"), sensitive = listOf("todos")),
  nbgHanakoEventSchema("team_task_started", NbgHanakoEventKind.TeamTask, optional = NBG_HANAKO_TEAM_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TEAM_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("team_task_completed", NbgHanakoEventKind.TeamTask, optional = NBG_HANAKO_TEAM_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TEAM_SCHEMA_SENSITIVE_FIELDS, terminal = true),
  nbgHanakoEventSchema("team_task_failed", NbgHanakoEventKind.TeamTask, optional = NBG_HANAKO_TEAM_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TEAM_SCHEMA_SENSITIVE_FIELDS, terminal = true),
  nbgHanakoEventSchema("team_agent_started", NbgHanakoEventKind.TeamTask, optional = NBG_HANAKO_TEAM_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TEAM_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("team_agent_update", NbgHanakoEventKind.TeamTask, optional = NBG_HANAKO_TEAM_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TEAM_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("team_agent_result", NbgHanakoEventKind.TeamTask, optional = NBG_HANAKO_TEAM_SCHEMA_OPTIONAL_FIELDS, sensitive = NBG_HANAKO_TEAM_SCHEMA_SENSITIVE_FIELDS),
  nbgHanakoEventSchema("browser_status", NbgHanakoEventKind.RuntimeSignal, optional = listOf("status", "url", "title"), sensitive = listOf("url", "title")),
  nbgHanakoEventSchema("browser_bg_status", NbgHanakoEventKind.RuntimeSignal, optional = listOf("status", "url", "title"), sensitive = listOf("url", "title")),
  nbgHanakoEventSchema("desk_changed", NbgHanakoEventKind.RuntimeSignal, optional = listOf("desk", "workspace"), sensitive = listOf("desk", "workspace")),
  nbgHanakoEventSchema("token_usage", NbgHanakoEventKind.RuntimeSignal, optional = listOf("inputTokens", "outputTokens", "totalTokens")),
  nbgHanakoEventSchema("plan_mode", NbgHanakoEventKind.RuntimeSignal, optional = listOf("mode")),
  nbgHanakoEventSchema("permission_mode", NbgHanakoEventKind.RuntimeSignal, optional = listOf("mode")),
  nbgHanakoEventSchema("access_mode", NbgHanakoEventKind.RuntimeSignal, optional = listOf("mode")),
  nbgHanakoEventSchema("bridge_status", NbgHanakoEventKind.RuntimeSignal, optional = listOf("status", "message"), sensitive = listOf("message")),
  nbgHanakoEventSchema("deferred_result", NbgHanakoEventKind.Tool, optional = listOf("id", "toolCallId", "result", "status", "error"), sensitive = listOf("result", "error")),
  nbgHanakoEventSchema("error", NbgHanakoEventKind.Error, optional = listOf("message", "error", "code"), sensitive = listOf("message", "error"), terminal = true),
  nbgHanakoEventSchema("confirmation_resolved", NbgHanakoEventKind.Confirmation, required = listOf("confirmId", "action"), optional = listOf("resolution", "source")),
)

internal val NBG_HANAKO_TERMINAL_TURN_END_EVENT_TYPES = setOf(
  "turn_end",
  "error",
  "session_status",
  "status",
)

internal fun nbgHanakoEventSchemaFor(type: String): NbgHanakoEventSchemaEntry? =
  NBG_HANAKO_EVENT_SCHEMA_ENTRIES.firstOrNull { it.type == type.trim() }

internal fun nbgValidateHanakoEventSchemaManifest(): List<String> {
  val errors = mutableListOf<String>()
  val schemaTypes = NBG_HANAKO_EVENT_SCHEMA_ENTRIES.map { it.type }
  val duplicates = schemaTypes.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
  if (duplicates.isNotEmpty()) errors += "duplicate_schema_types:${duplicates.sorted().joinToString(",")}"
  val missing = NBG_HANAKO_SUPPORTED_EVENT_TYPES - schemaTypes.toSet()
  if (missing.isNotEmpty()) errors += "missing_schema_types:${missing.sorted().joinToString(",")}"
  val extra = schemaTypes.toSet() - NBG_HANAKO_SUPPORTED_EVENT_TYPES
  if (extra.isNotEmpty()) errors += "unknown_schema_types:${extra.sorted().joinToString(",")}"
  NBG_HANAKO_EVENT_SCHEMA_ENTRIES.forEach { entry ->
    if (entry.requiredFields.firstOrNull() != "type") {
      errors += "missing_type_required:${entry.type}"
    }
    if (entry.kind != nbgHanakoEventKind(entry.type)) {
      errors += "kind_mismatch:${entry.type}:${entry.kind.wireName}:${nbgHanakoEventKind(entry.type).wireName}"
    }
  }
  return errors
}

internal fun nbgHanakoEventSchemaJson(): String =
  JSONObject()
    .put("schemaVersion", NBG_HANAKO_EVENT_SCHEMA_VERSION)
    .put("contractVersion", NBG_HANAKO_EVENT_CONTRACT_VERSION)
    .put("eventTypes", JSONArray(NBG_HANAKO_EVENT_SCHEMA_ENTRIES.map { it.toSchemaJson() }))
    .toString(2)

internal fun nbgHanakoEventJsonSchema(): String =
  JSONObject()
    .put("\$schema", "https://json-schema.org/draft/2020-12/schema")
    .put("\$id", "nbg://contracts/$NBG_HANAKO_EVENT_SCHEMA_VERSION")
    .put("title", "NBG Android Hanako Event Schema v1")
    .put("type", "object")
    .put("additionalProperties", true)
    .put("required", JSONArray(listOf("type")))
    .put(
      "properties",
      JSONObject()
        .put(
          "type",
          JSONObject()
            .put("type", "string")
            .put("enum", JSONArray(NBG_HANAKO_SUPPORTED_EVENT_TYPES.sorted())),
        ),
    )
    .put("oneOf", JSONArray(NBG_HANAKO_EVENT_SCHEMA_ENTRIES.map { it.toJsonSchemaVariant() }))
    .put("x-nbg-schemaVersion", NBG_HANAKO_EVENT_SCHEMA_VERSION)
    .put("x-nbg-contractVersion", NBG_HANAKO_EVENT_CONTRACT_VERSION)
    .toString(2)

internal fun nbgBuildConfirmationResolutionAuditEntry(
  confirmId: String,
  action: String,
  confirmation: HanakoConfirmation? = null,
  timestampMillis: Long = System.currentTimeMillis(),
): NbgConfirmationResolutionAuditEntry {
  val normalizedAction = action.nbgNormalizeConfirmationResolutionAction()
  val result = when (normalizedAction) {
    "confirmed" -> "allowed"
    "rejected" -> "rejected"
    else -> "processed"
  }
  val entryWithoutSignature = NbgConfirmationResolutionAuditEntry(
    schemaVersion = NBG_CONFIRMATION_RESOLUTION_AUDIT_SCHEMA_VERSION,
    confirmIdHash = confirmId.nbgConfirmationAuditHash(),
    action = normalizedAction,
    result = result,
    riskTier = confirmation?.riskTier?.trim().orEmpty(),
    subjectHash = confirmation?.subjectLabel.orEmpty().nbgConfirmationAuditHash(),
    targetHash = listOf(confirmation?.targetLabel, confirmation?.subjectDetail)
      .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
      .distinct()
      .joinToString("\u001f")
      .nbgConfirmationAuditHash(),
    timestampMillis = timestampMillis.coerceAtLeast(0L),
    signature = "",
  )
  return entryWithoutSignature.copy(signature = entryWithoutSignature.nbgConfirmationAuditSignature())
}

internal fun nbgVerifyConfirmationResolutionAuditEntry(entry: NbgConfirmationResolutionAuditEntry): Boolean =
  entry.schemaVersion == NBG_CONFIRMATION_RESOLUTION_AUDIT_SCHEMA_VERSION &&
    entry.signature.isNotBlank() &&
    entry.signature == entry.copy(signature = "").nbgConfirmationAuditSignature()

internal fun NbgConfirmationResolutionAuditEntry.toConfirmationAuditJson(): JSONObject =
  JSONObject()
    .put("schemaVersion", schemaVersion)
    .put("confirmIdHash", confirmIdHash)
    .put("action", action)
    .put("result", result)
    .put("riskTier", riskTier)
    .put("subjectHash", subjectHash)
    .put("targetHash", targetHash)
    .put("timestampMillis", timestampMillis)
    .put("signature", signature)

internal fun NbgConfirmationResolutionAuditEntry.nbgConfirmationAuditTimelineLabel(): String =
  signature.take(16)

private fun nbgHanakoEventSchema(
  type: String,
  kind: NbgHanakoEventKind,
  required: List<String> = emptyList(),
  optional: List<String> = emptyList(),
  sensitive: List<String> = emptyList(),
  terminal: Boolean = false,
  notes: String = "",
): NbgHanakoEventSchemaEntry =
  NbgHanakoEventSchemaEntry(
    type = type,
    kind = kind,
    requiredFields = (listOf("type") + required).distinct(),
    optionalFields = optional.distinct(),
    sensitiveFields = sensitive.distinct(),
    terminalEvent = terminal,
    notes = notes,
  )

private fun NbgHanakoEventSchemaEntry.toSchemaJson(): JSONObject =
  JSONObject()
    .put("type", type)
    .put("kind", kind.wireName)
    .put("requiredFields", JSONArray(requiredFields))
    .put("optionalFields", JSONArray(optionalFields))
    .put("sensitiveFields", JSONArray(sensitiveFields))
    .put("terminalEvent", terminalEvent)
    .put("notes", notes)

private fun NbgHanakoEventSchemaEntry.toJsonSchemaVariant(): JSONObject =
  JSONObject()
    .put("title", type)
    .put("type", "object")
    .put("additionalProperties", true)
    .put(
      "properties",
      JSONObject()
        .put("type", JSONObject().put("const", type)),
    )
    .put("required", JSONArray(requiredFields))
    .put("x-nbg-kind", kind.wireName)
    .put("x-nbg-sensitiveFields", JSONArray(sensitiveFields))
    .put("x-nbg-terminalEvent", terminalEvent)

private fun String.nbgNormalizeConfirmationResolutionAction(): String =
  when (trim().lowercase()) {
    "confirmed", "approved", "accepted" -> "confirmed"
    "rejected", "denied", "declined" -> "rejected"
    else -> "processed"
  }

private fun String.nbgConfirmationAuditHash(): String {
  val clean = trim()
  if (clean.isBlank()) return ""
  return nbgSha256Hex(clean)
}

private fun NbgConfirmationResolutionAuditEntry.nbgConfirmationAuditSignature(): String =
  nbgSha256Hex(
    listOf(
      "nbg-confirmation-resolution-audit-signature-v1",
      schemaVersion,
      confirmIdHash,
      action,
      result,
      riskTier,
      subjectHash,
      targetHash,
      timestampMillis.toString(),
    ).joinToString("\n"),
  )

private fun nbgSha256Hex(value: String): String =
  MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

internal fun nbgHanakoEventKind(type: String): NbgHanakoEventKind {
  val clean = type.trim()
  return when {
    clean in setOf("status", "session_status") -> NbgHanakoEventKind.StreamingStatus
    clean == "stream_resume" -> NbgHanakoEventKind.StreamResume
    clean in setOf("text_delta", "card_text", "slash_result", "session_user_message") -> NbgHanakoEventKind.AssistantText
    clean in setOf("thinking_start", "thinking_delta", "thinking_end") -> NbgHanakoEventKind.Thinking
    clean in setOf(
      "tool_progress",
      "tool_update",
      "tool_status",
      "terminal_output",
      "terminal_status",
      "file_write_prepare",
      "vision_progress",
      "tool_start",
      "tool_call",
      "tool_invocation",
      "tool_end",
      "tool_result",
      "deferred_result",
    ) || clean.endsWith("_progress") -> NbgHanakoEventKind.Tool
    clean in setOf("content_block", "block_update") -> NbgHanakoEventKind.ContentBlock
    clean in setOf("confirmation_resolved") -> NbgHanakoEventKind.Confirmation
    clean in setOf("team_task_started", "team_task_completed", "team_task_failed", "team_agent_started", "team_agent_update", "team_agent_result") -> NbgHanakoEventKind.TeamTask
    clean in setOf("turn_end", "session_title", "session_branch_reset", "todo_update") -> NbgHanakoEventKind.Session
    clean in setOf("context_usage", "compaction_start", "compaction_end", "browser_status", "browser_bg_status", "desk_changed", "token_usage", "plan_mode", "permission_mode", "access_mode", "bridge_status", "mood_text") -> NbgHanakoEventKind.RuntimeSignal
    clean == "error" -> NbgHanakoEventKind.Error
    else -> NbgHanakoEventKind.Unknown
  }
}

internal fun nbgHanakoEventTerminatesTurn(type: String, status: String = ""): Boolean {
  val clean = type.trim()
  if (clean == "turn_end" || clean == "error") return true
  if (clean !in setOf("status", "session_status")) return false
  return when (nbgNormalizeHanakoStatus(status)) {
    "idle", "done", "complete", "completed", "stopped", "finished", "success", "failed", "failure", "error" -> true
    else -> false
  }
}

internal data class NbgHanakoStreamCursor(
  val sessionPath: String,
  val streamId: String = "",
  val lastSeq: Int = 0,
)

internal data class NbgHanakoStreamEventDecision(
  val accept: Boolean,
  val nextCursor: NbgHanakoStreamCursor?,
  val reason: String = "",
)

internal fun nbgDecideHanakoStreamEvent(
  currentSessionPath: String,
  cursor: NbgHanakoStreamCursor?,
  eventSessionPath: String?,
  eventStreamId: String?,
  eventSeq: Int?,
  allowSessionTitle: Boolean = false,
): NbgHanakoStreamEventDecision {
  val active = currentSessionPath.trim()
  val incomingSession = eventSessionPath?.trim().orEmpty()
  if (active.isNotBlank() && incomingSession.isNotBlank() && incomingSession != active && !allowSessionTitle) {
    return NbgHanakoStreamEventDecision(false, cursor, "wrong_session")
  }
  val effectiveSession = incomingSession.ifBlank { active }
  if (effectiveSession.isBlank()) return NbgHanakoStreamEventDecision(true, cursor, "no_session")
  if (eventStreamId.isNullOrBlank() && eventSeq == null) {
    return NbgHanakoStreamEventDecision(true, cursor, "no_stream_cursor")
  }
  val currentForSession = cursor?.takeIf { it.sessionPath == effectiveSession }
  if (
    eventSeq != null &&
    currentForSession != null &&
    eventStreamId.orEmpty().let { it.isBlank() || it == currentForSession.streamId } &&
    eventSeq <= currentForSession.lastSeq
  ) {
    return NbgHanakoStreamEventDecision(false, currentForSession, "duplicate_seq")
  }
  val nextBase = if (
    currentForSession != null &&
    !eventStreamId.isNullOrBlank() &&
    currentForSession.streamId.isNotBlank() &&
    eventStreamId != currentForSession.streamId
  ) {
    NbgHanakoStreamCursor(effectiveSession, eventStreamId, 0)
  } else {
    currentForSession ?: NbgHanakoStreamCursor(effectiveSession)
  }
  val next = nextBase.copy(
    streamId = eventStreamId ?: nextBase.streamId,
    lastSeq = eventSeq?.let { maxOf(nextBase.lastSeq, it) } ?: nextBase.lastSeq,
  )
  return NbgHanakoStreamEventDecision(true, next, "accepted")
}

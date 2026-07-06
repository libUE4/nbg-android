package com.nbg.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgHanakoEventContractTest {
  @Test
  fun supportedEventTypesCoverStreamingToolsConfirmationAndRecovery() {
    listOf(
      "status",
      "session_status",
      "stream_resume",
      "text_delta",
      "thinking_delta",
      "content_block",
      "tool_start",
      "tool_end",
      "tool_progress",
      "terminal_output",
      "file_write_prepare",
      "confirmation_resolved",
      "turn_end",
      "error",
      "team_task_started",
      "team_agent_update",
      "deferred_result",
      "session_branch_reset",
    ).forEach {
      assertTrue(it, it in NBG_HANAKO_SUPPORTED_EVENT_TYPES)
    }
  }

  @Test
  fun eventKindClassifiesStableProtocolFamilies() {
    assertEquals(NbgHanakoEventKind.StreamingStatus, nbgHanakoEventKind("status"))
    assertEquals(NbgHanakoEventKind.StreamResume, nbgHanakoEventKind("stream_resume"))
    assertEquals(NbgHanakoEventKind.AssistantText, nbgHanakoEventKind("text_delta"))
    assertEquals(NbgHanakoEventKind.Thinking, nbgHanakoEventKind("thinking_delta"))
    assertEquals(NbgHanakoEventKind.Tool, nbgHanakoEventKind("tool_start"))
    assertEquals(NbgHanakoEventKind.Tool, nbgHanakoEventKind("custom_progress"))
    assertEquals(NbgHanakoEventKind.ContentBlock, nbgHanakoEventKind("content_block"))
    assertEquals(NbgHanakoEventKind.Confirmation, nbgHanakoEventKind("confirmation_resolved"))
    assertEquals(NbgHanakoEventKind.TeamTask, nbgHanakoEventKind("team_agent_result"))
    assertEquals(NbgHanakoEventKind.Session, nbgHanakoEventKind("turn_end"))
    assertEquals(NbgHanakoEventKind.RuntimeSignal, nbgHanakoEventKind("context_usage"))
    assertEquals(NbgHanakoEventKind.Error, nbgHanakoEventKind("error"))
    assertEquals(NbgHanakoEventKind.Unknown, nbgHanakoEventKind("new_future_event"))
  }

  @Test
  fun terminalTurnEndSemanticsMatchStreamingStatusContract() {
    assertTrue(nbgHanakoEventTerminatesTurn("turn_end"))
    assertTrue(nbgHanakoEventTerminatesTurn("error"))
    assertTrue(nbgHanakoEventTerminatesTurn("status", "completed"))
    assertTrue(nbgHanakoEventTerminatesTurn("session_status", "failed"))

    assertFalse(nbgHanakoEventTerminatesTurn("status", "running"))
    assertFalse(nbgHanakoEventTerminatesTurn("session_status", "thinking"))
    assertFalse(nbgHanakoEventTerminatesTurn("tool_end", "completed"))
  }

  @Test
  fun contractVersionIsStableForDiagnosticsAndDocs() {
    assertEquals("nbg-android-hanako-events-v1", NBG_HANAKO_EVENT_CONTRACT_VERSION)
  }

  @Test
  fun eventSchemaManifestCoversEverySupportedType() {
    assertEquals(emptyList<String>(), nbgValidateHanakoEventSchemaManifest())

    val schemaTypes = NBG_HANAKO_EVENT_SCHEMA_ENTRIES.map { it.type }.toSet()
    assertEquals(NBG_HANAKO_SUPPORTED_EVENT_TYPES, schemaTypes)
    assertEquals(NbgHanakoEventKind.Confirmation, nbgHanakoEventSchemaFor("confirmation_resolved")?.kind)
    assertEquals(listOf("type", "confirmId", "action"), nbgHanakoEventSchemaFor("confirmation_resolved")?.requiredFields)
    assertTrue(nbgHanakoEventSchemaFor("stream_resume")?.sensitiveFields.orEmpty().contains("events"))
    assertTrue(nbgHanakoEventSchemaFor("terminal_output")?.sensitiveFields.orEmpty().contains("output"))
    assertTrue(nbgHanakoEventSchemaFor("turn_end")?.terminalEvent == true)
    assertTrue(nbgHanakoEventSchemaFor("error")?.terminalEvent == true)
  }

  @Test
  fun eventSchemaJsonIsMachineReadableAndDoesNotEmbedRawPayloads() {
    val raw = nbgHanakoEventSchemaJson()
    val json = JSONObject(raw)
    val eventTypes = json.getJSONArray("eventTypes")
    val types = (0 until eventTypes.length()).map { eventTypes.getJSONObject(it).getString("type") }
    val terminal = eventTypes.getJSONObject(types.indexOf("terminal_output"))

    assertEquals(NBG_HANAKO_EVENT_SCHEMA_VERSION, json.getString("schemaVersion"))
    assertEquals(NBG_HANAKO_EVENT_CONTRACT_VERSION, json.getString("contractVersion"))
    assertEquals(NBG_HANAKO_SUPPORTED_EVENT_TYPES.size, eventTypes.length())
    assertEquals("tool", terminal.getString("kind"))
    assertTrue(terminal.getJSONArray("sensitiveFields").toString().contains("output"))
    assertFalse(raw.contains("rawFrame"))
    assertFalse(raw.contains("payloadExample"))
    assertFalse(raw.contains("sessionPathValue"))
    assertFalse(raw.contains("/root/"))
  }

  @Test
  fun eventJsonSchemaProvidesDraftSchemaProjection() {
    val raw = nbgHanakoEventJsonSchema()
    val json = JSONObject(raw)
    val typeEnum = json.getJSONObject("properties").getJSONObject("type").getJSONArray("enum")
    val variants = json.getJSONArray("oneOf")
    val variantTypes = (0 until variants.length()).map {
      variants.getJSONObject(it).getJSONObject("properties").getJSONObject("type").getString("const")
    }

    assertEquals("https://json-schema.org/draft/2020-12/schema", json.getString("\$schema"))
    assertEquals("nbg://contracts/$NBG_HANAKO_EVENT_SCHEMA_VERSION", json.getString("\$id"))
    assertEquals(NBG_HANAKO_SUPPORTED_EVENT_TYPES.size, typeEnum.length())
    assertEquals(NBG_HANAKO_SUPPORTED_EVENT_TYPES.size, variants.length())
    assertEquals(NBG_HANAKO_SUPPORTED_EVENT_TYPES, variantTypes.toSet())
    assertEquals("confirmation", variants.getJSONObject(variantTypes.indexOf("confirmation_resolved")).getString("x-nbg-kind"))
    assertTrue(variants.getJSONObject(variantTypes.indexOf("terminal_output")).getJSONArray("x-nbg-sensitiveFields").toString().contains("output"))
    assertFalse(raw.contains("rawFrame"))
    assertFalse(raw.contains("payloadExample"))
    assertFalse(raw.contains("/root/"))
  }

  @Test
  fun confirmationResolutionAuditEntrySignsHashedLocalMetadataOnly() {
    val confirmation = HanakoConfirmation(
      confirmId = "confirm-private-id",
      title = "确认写入",
      body = "raw body with tool args should not be signed in clear text",
      subjectLabel = "文件写入",
      subjectDetail = "/root/private/project/Main.kt",
      severity = "elevated",
      riskTier = NbgPermissionRiskTier.High.wireName,
      riskLabel = "高风险",
      targetLabel = "/root/private/project/Main.kt",
      recoveryHint = "拒绝后可恢复",
      confirmLabel = "允许",
      rejectLabel = "拒绝",
    )

    val entry = nbgBuildConfirmationResolutionAuditEntry(
      confirmId = confirmation.confirmId,
      action = "confirmed",
      confirmation = confirmation,
      timestampMillis = 123L,
    )
    val raw = entry.toConfirmationAuditJson().toString()

    assertEquals(NBG_CONFIRMATION_RESOLUTION_AUDIT_SCHEMA_VERSION, entry.schemaVersion)
    assertEquals("confirmed", entry.action)
    assertEquals("allowed", entry.result)
    assertEquals(NbgPermissionRiskTier.High.wireName, entry.riskTier)
    assertEquals(64, entry.confirmIdHash.length)
    assertEquals(64, entry.subjectHash.length)
    assertEquals(64, entry.targetHash.length)
    assertEquals(64, entry.signature.length)
    assertEquals(16, entry.nbgConfirmationAuditTimelineLabel().length)
    assertTrue(nbgVerifyConfirmationResolutionAuditEntry(entry))
    assertFalse(nbgVerifyConfirmationResolutionAuditEntry(entry.copy(result = "rejected")))
    assertFalse(raw.contains("confirm-private-id"))
    assertFalse(raw.contains("raw body"))
    assertFalse(raw.contains("/root/private/project"))
    assertFalse(raw.contains("Main.kt"))
  }

  @Test
  fun streamEventDecisionRejectsWrongSessionAndDuplicateSeq() {
    val cursor = NbgHanakoStreamCursor(sessionPath = "/root/session-a.jsonl", streamId = "s1", lastSeq = 4)

    val wrongSession = nbgDecideHanakoStreamEvent(
      currentSessionPath = "/root/session-a.jsonl",
      cursor = cursor,
      eventSessionPath = "/root/session-b.jsonl",
      eventStreamId = "s1",
      eventSeq = 5,
    )
    val duplicate = nbgDecideHanakoStreamEvent(
      currentSessionPath = "/root/session-a.jsonl",
      cursor = cursor,
      eventSessionPath = "/root/session-a.jsonl",
      eventStreamId = "s1",
      eventSeq = 4,
    )

    assertFalse(wrongSession.accept)
    assertEquals("wrong_session", wrongSession.reason)
    assertFalse(duplicate.accept)
    assertEquals("duplicate_seq", duplicate.reason)
  }

  @Test
  fun streamEventDecisionAcceptsNoSeqCompatibilityAndResetsOnNewStream() {
    val cursor = NbgHanakoStreamCursor(sessionPath = "/root/session-a.jsonl", streamId = "s1", lastSeq = 10)

    val noSeq = nbgDecideHanakoStreamEvent(
      currentSessionPath = "/root/session-a.jsonl",
      cursor = cursor,
      eventSessionPath = "/root/session-a.jsonl",
      eventStreamId = null,
      eventSeq = null,
    )
    val newStream = nbgDecideHanakoStreamEvent(
      currentSessionPath = "/root/session-a.jsonl",
      cursor = cursor,
      eventSessionPath = "/root/session-a.jsonl",
      eventStreamId = "s2",
      eventSeq = 1,
    )

    assertTrue(noSeq.accept)
    assertEquals("no_stream_cursor", noSeq.reason)
    assertTrue(newStream.accept)
    assertEquals("s2", newStream.nextCursor?.streamId)
    assertEquals(1, newStream.nextCursor?.lastSeq)
  }
}

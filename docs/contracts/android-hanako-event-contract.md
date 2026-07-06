---
title: "Android Hanako event contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["Agent Platform Agent", "Android Agent", "Runtime Agent"]
review_agent: "QA/Release Agent"
security_review: "required when event payloads include file, command, Memory, or external resource data"
---

# Android Hanako Event Contract

## Status

Active for public-beta hardening.

Audience:

- Android chat/runtime implementers
- Hanako runtime implementers
- Tool visualization implementers
- QA agents validating streaming, recovery, confirmations, and history

## Purpose

Define the stable Android-facing event protocol for HanakoPro local HTTP and WebSocket traffic. The contract covers streaming text, thinking, tool events, file previews/diffs, terminal output, confirmations, team/subagent status, session recovery, and turn termination.

This contract prevents:

- Dropping or duplicating streaming events after reconnect/resume.
- Treating confirmation blocks as normal content.
- Rendering tool, terminal, or file events with inconsistent status semantics.
- Leaving Android stuck in `streaming=true` after `turn_end`, terminal status, or error events.
- Regressing history recovery for tool calls and local/remote message formats.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
| Hanako API client | Local HTTP config, history, providers, skills, memory, MCP, and confirmation endpoints. | `HanakoApiClient.kt` |
| WebSocket bridge/controller | Receives realtime events, filters by session/stream, updates state, emits UI events. | `HanakoChatController.kt` |
| Event parser | Parses confirmations, content blocks, history messages, team events, timestamps, statuses. | `HanakoBridge.kt` |
| Tool parser | Parses tool start/end/progress, file preview/diff, terminal output, deferred results. | `HanakoRealtimeToolEvents.kt`, `HanakoToolParsing.kt` |
| UI renderer | Displays assistant text, thinking, tool cards, confirmations, todos, team state, and recovered history. | `NbgAgentMessageUi.kt`, `NbgToolPresentation.kt` |
| Contract model | Defines supported event types, event families, version, and turn termination oracle. | `NbgHanakoEventContract.kt` |

## Trust Boundary

Events cross these boundaries:

- Hanako local HTTP/WebSocket boundary: traffic must stay loopback/local and token-protected by the local-service contract.
- User confirmation boundary: `session_confirmation` blocks must become explicit confirmation UI, not assistant text.
- File/terminal/tool boundary: file paths, command args, terminal output, and tool result details can contain sensitive data and must not enter diagnostics by default.
- Session boundary: events with a non-current `sessionPath` must be ignored unless they are session-title metadata or stream-resume replay for the active session.
- Stream resume boundary: replayed events must be accepted only when sequence/stream metadata says they are newer than Android's last accepted event.

## Data Structures

Contract version:

```kotlin
const val NBG_HANAKO_EVENT_CONTRACT_VERSION = "nbg-android-hanako-events-v1"
const val NBG_HANAKO_EVENT_SCHEMA_VERSION = "nbg-android-hanako-event-schema-v1"
const val NBG_CONFIRMATION_RESOLUTION_AUDIT_SCHEMA_VERSION = "nbg-confirmation-resolution-audit-v1"
```

Supported event families:

```kotlin
enum class NbgHanakoEventKind {
  StreamingStatus,
  StreamResume,
  AssistantText,
  Thinking,
  Tool,
  Confirmation,
  ContentBlock,
  TeamTask,
  Session,
  RuntimeSignal,
  Error,
  Unknown,
}
```

Required Android UI event outputs:

```kotlin
sealed interface HanakoChatEvent {
  data class AssistantDelta(...)
  data class ThinkingDelta(...)
  data class ToolStatus(...)
  data class ContentBlock(...)
  data class ConfirmationRequested(...)
  data class ConfirmationResolved(..., auditEntry: NbgConfirmationResolutionAuditEntry?)
  data class TeamTaskUpdated(...)
  data object ToolInterrupted
  data object TurnEnded
}
```

Local confirmation resolution audit entry:

```kotlin
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
```

Audit rules:

- Android creates a local audit entry when a confirmation resolution is handled.
- The entry is a local integrity signature, not remote attestation. `signature` is SHA-256 over canonical v1 metadata and can detect local field tampering in support evidence.
- Audit metadata stores hashes of `confirmId`, subject label, and target fields; it must not store raw confirmation body, raw target path, command args, file diffs, terminal output, Memory content, conversation text, or raw tool payload.
- Timeline cards may show the short audit signature prefix for support correlation.
- Diagnostics do not export confirmation audit entries in v1.

Machine-readable event schema manifest:

```kotlin
data class NbgHanakoEventSchemaEntry(
  val type: String,
  val kind: NbgHanakoEventKind,
  val requiredFields: List<String>,
  val optionalFields: List<String>,
  val sensitiveFields: List<String>,
  val terminalEvent: Boolean,
  val notes: String,
)
```

Schema JSON shape:

```json
{
  "schemaVersion": "nbg-android-hanako-event-schema-v1",
  "contractVersion": "nbg-android-hanako-events-v1",
  "eventTypes": [
    {
      "type": "confirmation_resolved",
      "kind": "confirmation",
      "requiredFields": ["type", "confirmId", "action"],
      "optionalFields": ["resolution", "source"],
      "sensitiveFields": [],
      "terminalEvent": false,
      "notes": ""
    }
  ]
}
```

Draft JSON Schema projection:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "nbg://contracts/nbg-android-hanako-event-schema-v1",
  "title": "NBG Android Hanako Event Schema v1",
  "type": "object",
  "required": ["type"],
  "additionalProperties": true,
  "properties": {
    "type": {
      "type": "string",
      "enum": ["confirmation_resolved"]
    }
  },
  "oneOf": [
    {
      "title": "confirmation_resolved",
      "type": "object",
      "required": ["type", "confirmId", "action"],
      "additionalProperties": true,
      "properties": {
        "type": { "const": "confirmation_resolved" }
      },
      "x-nbg-kind": "confirmation",
      "x-nbg-sensitiveFields": [],
      "x-nbg-terminalEvent": false
    }
  ],
  "x-nbg-schemaVersion": "nbg-android-hanako-event-schema-v1",
  "x-nbg-contractVersion": "nbg-android-hanako-events-v1"
}
```

Schema rules:

- Every type in `NBG_HANAKO_SUPPORTED_EVENT_TYPES` must have exactly one `NbgHanakoEventSchemaEntry`.
- `kind` must match `nbgHanakoEventKind(type)`.
- Schema entries list accepted field names and sensitive fields, but do not embed raw frame examples, session paths, tool args, terminal output, file content, diffs, Memory content, or conversation text.
- `nbgHanakoEventJsonSchema()` projects the same manifest into Draft 2020-12 JSON Schema with one `oneOf` variant per supported event type and `x-nbg-*` metadata for Android-specific semantics.
- `terminalEvent=true` means the event is always terminal or has documented terminal status semantics; status/session_status remain conditional on status normalization.
- Unknown future events remain forward-compatible through `Unknown` classification or generic `*_progress` tool handling, but they are not part of the v1 schema manifest until added explicitly.

## State Machine

| State | Entered When | Allowed Transitions | Terminal? |
| --- | --- | --- | --- |
| `Disconnected` | No healthy Hanako server info or WebSocket. | `Connecting` | No |
| `Connecting` | Android launches/reconnects Hanako and opens local WebSocket. | `ConnectedIdle`, `Disconnected` | No |
| `ConnectedIdle` | WebSocket is open and no turn is active. | `TurnStarting`, `Disconnected` | No |
| `TurnStarting` | User sends message/slash/team request and Android emits local user message. | `Streaming`, `TurnFailed` | No |
| `Streaming` | `status/session_status` running or text/tool/thinking events arrive. | `WaitingConfirmation`, `RecoveringStream`, `TurnEnded`, `TurnFailed`, `Interrupted` | No |
| `WaitingConfirmation` | `content_block.block.type=session_confirmation` arrives. | `Streaming`, `TurnFailed`, `Interrupted` | No |
| `RecoveringStream` | Reconnect requests `resume_stream` with `streamId` and `sinceSeq`. | `Streaming`, `TurnEnded`, `TurnFailed` | No |
| `TurnEnded` | `turn_end`, terminal idle status, or non-running session status ends a turn. | `ConnectedIdle` | Yes |
| `TurnFailed` | `error` or local API failure aborts a turn. | `ConnectedIdle`, `Connecting` | Yes |
| `Interrupted` | User interrupt or stuck-turn abort occurs. | `ConnectedIdle` | Yes |

## Endpoint Or Event Semantics

### Streaming Text

- Event types: `text_delta`, `card_text`.
- Required fields: `delta` or `text`.
- Android behavior: append to current assistant message; create an assistant message if none exists.
- Ordering: accepted in WebSocket order after session/stream filtering.

### Thinking

- Event types: `thinking_start`, `thinking_delta`, `thinking_end`.
- Android behavior: thinking text is rendered separately from assistant output and ended before turn completion.

### Confirmation

- Event source: `content_block` whose `block.type` is `session_confirmation`.
- Required fields: `confirmId`.
- Android behavior: parse into `HanakoConfirmation`, attach permission-risk metadata, and emit `ConfirmationRequested`.
- Resolution event: `confirmation_resolved` with `confirmId` and `action`; Android emits `ConfirmationResolved` and attaches or creates a local confirmation resolution audit entry.

### Tool Events

- Event types: `tool_start`, `tool_call`, `tool_invocation`, `tool_progress`, `tool_update`, `tool_status`, `terminal_output`, `terminal_status`, `file_write_prepare`, `vision_progress`, `tool_end`, `tool_result`, and unknown `*_progress` tool-shaped events.
- Required identity: `toolCallId`, `prepareKey`, `id`, or stable fallback from type/name/path.
- Android behavior:
  - Start events set `running=true`.
  - End/result events set `running=false` and status `done` or `failed`.
  - File previews/diffs use bounded text limits.
  - Terminal output is normalized and bounded.

### Team/Subagent Events

- Event types: `team_task_started`, `team_task_completed`, `team_task_failed`, `team_agent_started`, `team_agent_update`, `team_agent_result`, `block_update`, `deferred_result`.
- Android behavior: normalize status to queued/running/completed/failed/aborted and merge agent updates by task id.

### Stream Resume

- Event type: `stream_resume`.
- Required fields: `sessionPath`; optional `streamId`, `events`, `sinceSeq`, `nextSeq`, `truncated`.
- Android behavior:
  - Replayed events receive missing `sessionPath` and `streamId`.
  - Wrong-session replay events are skipped and must not update the active stream cursor.
  - Events with `seq <= lastSeq` for the same stream are ignored.
  - Different `streamId` resets sequence tracking.
  - The pure decision oracle is `nbgDecideHanakoStreamEvent`.
  - `truncated=true` emits a user-visible tool status note.

### Turn Termination

- Terminal events:
  - `turn_end`
  - `error`
  - `status/session_status` with idle/done/complete/completed/stopped/finished/success/failed/failure/error.
- Android behavior: clear streaming, end thinking, reset active assistant state, and emit `TurnEnded` where appropriate.

## Error Semantics

| Error | Meaning | User-visible message | Recovery |
| --- | --- | --- | --- |
| `invalid_json` | WebSocket frame is not JSON. | None. | Drop frame. |
| `wrong_session` | Event belongs to another session. | None. | Drop frame except session metadata. |
| `duplicate_seq` | Event replay already accepted. | None. | Drop frame. |
| `hanako_error_event` | Hanako sent `type=error`. | Assistant message and system state show Hanako error. | Reset turn, allow reconnect/retry. |
| `stream_resume_truncated` | Replay omitted early events. | Shows recovered-output truncation note. | Continue with available events and allow history reload. |
| `confirmation_missing_id` | Confirmation block has no `confirmId`. | None. | Drop malformed confirmation. |

## Compatibility

Versioning:

- Android contract version is `nbg-android-hanako-events-v1`.
- New event types may be added if unknown events are ignored or parsed through generic `*_progress` tool handling.

Migration:

- Remote history supports multiple message shapes: array content blocks, `toolResult`, `tool`, local tool call/result pairs, and cached tool status JSON.

Backward compatibility:

- Parsers accept both camelCase and snake_case fields where current Hanako emits variants.
- Status normalization accepts completed/done/success, failed/failure/error, aborted/cancelled/canceled.

Deprecation:

- Confirmation payloads inside normal assistant text are not part of this contract.
- Tool output with unbounded terminal/file content is prohibited for Android rendering.

## Security Rules

- Sensitive fields: file paths, file content, diffs, command args, terminal output, Memory context, API keys, auth tokens, MCP env/header values.
- Redaction rules: event payloads may render in UI but diagnostics must export only redacted summaries/counts under the diagnostics contract.
- Confirmation requirements: dangerous file/delete/Skill/Memory/MCP operations require confirmation via the permission-risk contract.
- Hash/signature requirements: none for event frames; resource installs referenced by events follow Skill/PetDex/MCP contracts.
- Network/egress requirements: WebSocket and HTTP are local Hanako channels only.
- Audit/logging requirements: do not log raw full event frames if they may contain tool args, terminal output, or user content.

## Test Oracle

Unit tests:

- `NbgHanakoEventContractTest.supportedEventTypesCoverStreamingToolsConfirmationAndRecovery`
- `NbgHanakoEventContractTest.eventKindClassifiesStableProtocolFamilies`
- `NbgHanakoEventContractTest.terminalTurnEndSemanticsMatchStreamingStatusContract`
- `NbgHanakoEventContractTest.eventSchemaManifestCoversEverySupportedType`
- `NbgHanakoEventContractTest.eventSchemaJsonIsMachineReadableAndDoesNotEmbedRawPayloads`
- `NbgHanakoEventContractTest.eventJsonSchemaProvidesDraftSchemaProjection`
- `NbgHanakoEventContractTest.confirmationResolutionAuditEntrySignsHashedLocalMetadataOnly`
- `NbgHanakoEventContractTest.streamEventDecisionRejectsWrongSessionAndDuplicateSeq`
- `NbgHanakoEventContractTest.streamEventDecisionAcceptsNoSeqCompatibilityAndResetsOnNewStream`
- `HanakoBridgeTest.confirmationParserAddsRiskAndRecoveryHints`
- `HanakoBridgeTest.remoteHistoryParsesArrayContentBlocks`
- `HanakoBridgeTest.remoteHistoryParsesStandaloneToolResultMessages`
- `HanakoBridgeTest.teamStatusParserAcceptsNestedPayloadTaskAndAgentEvents`
- `AndroidManifestBehaviorTest.androidHanakoEventContractIsDocumentedAndWired`

Integration tests:

- Reconnect during streaming and verify resume does not duplicate text/tool events.
- Trigger file write confirmation and verify confirmation UI, reject, and resolved event.
- Run terminal command tool and verify terminal output card status and turn end.

Manual tests:

- Send a normal chat message and observe streaming text/thinking/tool updates.
- Trigger a dangerous file operation and reject it.
- Switch sessions during streaming and verify wrong-session events do not leak into active chat.

Negative tests:

- Malformed confirmation without `confirmId` is ignored.
- Duplicate stream `seq` is ignored.
- Unknown non-tool event does not crash Android.
- Error event clears streaming and emits `TurnEnded`.

## Operational Diagnostics

Diagnostics may include:

- Hanako connected/streaming booleans.
- Redacted last error.
- Runtime patch/launcher status.
- Capability health.
- Recent redacted error summaries.
- Stream resume counters without raw event data: request count, received resume count, replayed/accepted/skipped/duplicate replay counts, truncated/reset resume counts, cursor-present boolean, and last accepted sequence number. Skipped replay counts include duplicate sequence events and wrong-session replay events.

Diagnostics must not include:

- Raw WebSocket frames.
- Raw stream resume event payloads, stream ids, or session paths.
- Full assistant/user conversation text.
- Raw tool args/results.
- Terminal output.
- File content or diffs.
- Memory context.

## Open Questions

- None for v1.

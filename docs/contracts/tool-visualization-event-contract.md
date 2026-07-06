---
title: "Tool Visualization Event Contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["UX/UI Agent", "Agent Platform Agent"]
review_agent: "QA/Release Agent"
security_review: "required when tool events include file, command, Memory, or external resource data"
---

# Tool Visualization Event Contract

## Status

Active for mobile IDE and Agent public-beta hardening.

## Purpose

Define the common status language for tool cards and tool-like events in Android chat so terminal, file, diff, todo, team/agent, thinking, confirmation, and generic tool events are readable, mergeable, and auditable.

This contract prevents:

- Different event sources inventing incompatible status strings.
- Terminal/file/diff cards silently appearing as generic tool noise.
- Historical running tools staying visually active after restore.
- Dangerous confirmation or failed tool states being shown as successful.
- Large terminal/diff/file output bypassing truncation and preview rules.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
| Hanako event parser | Parses live tool start/progress/end and terminal/file payloads. | `HanakoRealtimeToolEvents.kt`, `HanakoToolParsing.kt` |
| Local history parser | Restores tool cards from saved JSONL sessions. | `HanakoBridge.kt`, `HanakoToolParsing.kt` |
| Presentation contract | Normalizes tool kind and state. | `NbgToolVisualizationEventContract.kt` |
| Presentation helpers | Labels, colors, previews, diff stats, terminal summaries. | `NbgToolPresentation.kt` |
| Message UI | Renders tool, terminal, file preview, diff, thinking, and confirmation cards. | `NbgAgentMessageUi.kt`, `NbgAgentUi.kt` |

## Trust Boundary

Tool visualization crosses these boundaries:

- Runtime to Android event boundary: Hanako may emit historical and live events with different shapes.
- User review boundary: file/diff/terminal/confirmation cards affect whether users trust and approve Agent actions.
- Privacy boundary: previews may contain code, paths, terminal output, or error text and must stay local.
- Recovery boundary: restored running tools must not look live after reconnect/history restore.

## Data Structures

Contract version:

```kotlin
const val NBG_TOOL_VISUALIZATION_EVENT_CONTRACT_VERSION = "nbg-tool-visualization-events-v1"
```

Supported kinds:

- `tool`
- `terminal`
- `file`
- `diff`
- `todo`
- `team_task`
- `team_agent`
- `thinking`
- `confirmation`
- `vision`

Supported states:

- `waiting`
- `running`
- `succeeded`
- `failed`
- `blocked`
- `cancelled`
- `restored_incomplete`

Android data model:

```kotlin
data class HanakoToolStatus(
  val key: String,
  val kind: String,
  val toolName: String,
  val status: String,
  val running: Boolean,
  val success: Boolean?,
  val filePreview: HanakoFilePreview?,
  val fileDiff: HanakoFileDiff?,
  val terminalOutput: HanakoTerminalOutput?,
)
```

Terminal exit detail:

```kotlin
data class NbgTerminalExitDetail(
  val exitCode: Int,
  val state: NbgToolVisualizationState,
  val label: String,
)
```

`exitCode = 0` maps to `succeeded`; non-zero exit codes map to `failed`; missing exit code means no terminal exit detail is present.

## State Machine

| State | Entered When | Allowed Transitions | Terminal? |
| --- | --- | --- | --- |
| `waiting` | Tool card exists but has no running/success/failure signal. | `running`, `succeeded`, `failed`, `blocked`, `cancelled` | No |
| `running` | Tool start/progress event arrives or terminal output is alive. | `succeeded`, `failed`, `blocked`, `cancelled`, `restored_incomplete` | No |
| `succeeded` | Tool end succeeds or status normalizes to done/success. | None | Yes |
| `failed` | Tool end fails, `isError=true`, or status normalizes to failed/error. | None | Yes |
| `blocked` | Permission or policy blocks action before execution. | None | Yes |
| `cancelled` | User/runtime aborts or interrupts the tool. | None | Yes |
| `restored_incomplete` | A historical running tool is restored without an end event. | None | Yes |

## Event Semantics

Terminal:

- Terminal-like tools include `terminal_*`, `bash`, `shell`, `exec`, `run_command`, and `command`.
- Terminal cards must prefer structured terminal output over generic tool details.
- Output previews must be bounded by Android preview limits and may be chunked.
- Terminal exit code, when present, must be exposed through `NbgTerminalExitDetail` and rendered in terminal metadata as `exit N`.
- Distinct terminal sessions or commands must not merge into one card unless the stable key matches.

File and diff:

- `write`, `edit`, and `edit-diff` are file-touch tools.
- File write previews must show path, preview text, truncation state, and append/reset state when available.
- Diffs must expose file path/name, additions/deletions, truncation marker, and chunked preview when large.

Generic tool:

- Generic metadata-only events must not render noisy cards unless they have visible status, detail, title, or preview.
- Unknown statuses normalize to `waiting` rather than success.

Thinking and confirmation:

- Thinking cards are visually separate from tool execution.
- Confirmation cards must preserve permission risk, target, recovery hint, and resolved/rejected state from the permission contract.
- `confirmation_resolved` events produce a compact confirmation timeline card after resolution so approval/rejection remains auditable after the pending dialog closes.
- The compact confirmation timeline card must use `kind=confirmation`, a stable `confirmation:<confirmIdHashPrefix>` key, normalized `succeeded` for confirmed actions, and normalized `cancelled` for rejected actions.
- The compact confirmation timeline card may show a short local audit signature prefix, but must not keep the raw confirmation id in the timeline key.
- The compact card may include bounded risk label, subject label, target, and recovery hint text, but must not expand confirmation body or tool arguments into diagnostics export.
- If local HTTP resolution and WebSocket `confirmation_resolved` both deliver the same confirmation id, timeline-card merging must keep the latest resolution state while preserving previously captured risk label, target, and recovery hint.

Todo, team, and agent status:

- Todo, team task, and team agent status surfaces must project onto `HanakoToolStatus` through shared helpers before relying on presentation state.
- `nbgTodoListToolStatus()` uses `kind=todo`, `toolName=todo_status`, and a stable `todo:session` key.
- `HanakoTeamTaskStatus.nbgTeamTaskToolStatus()` uses `kind=team_task`, `toolName=team_task_status`, and a stable `team_task:<taskId>` key.
- `HanakoTeamAgentStatus.nbgTeamAgentToolStatus()` uses `kind=team_agent`, `toolName=team_agent_status`, and a stable `team_agent:<taskId>:<agentId>` key.
- Rich mobile-specific UI such as the expandable todo bar may remain specialized, but its status/title/semantics must consume the shared `HanakoToolStatus` projection so labels and normalized states stay consistent.

## Error Semantics

| Error | Meaning | Required UI State |
| --- | --- | --- |
| `isError=true` | Tool result failed. | `failed` |
| `success=false` | Tool result failed. | `failed` |
| Terminal exit code is non-zero | Terminal command exited unsuccessfully. | `failed` detail |
| Permission denied/rejected | User or policy blocked action. | `blocked` or `cancelled` |
| Historical running card has no end event | Restore found an incomplete tool. | `restored_incomplete` |
| Unknown tool name | Tool source is not recognized. | `tool` kind with normalized state |

## Compatibility

- Contract version is `nbg-tool-visualization-events-v1`.
- Existing `HanakoToolStatus.kind/status/running/success` fields remain source compatible.
- New event fields must be additive.
- UI labels may remain localized, but underlying normalized states must use the supported state set.
- Large output truncation remains governed by Android preview limits.

## Security Rules

- Tool cards must not introduce new side effects; they are presentation only.
- Tool cards must not hide failed, blocked, cancelled, or restored-incomplete states behind green success styling.
- File path, terminal output, diff body, and command details must not enter diagnostics export unless separately redacted.
- Confirmation cards must not drop risk tier or recovery hint.
- Any new tool kind that writes files, runs commands, installs resources, updates Memory, or calls MCP side-effect tools requires Permission Risk Model review.

## Test Oracle

Unit/source tests:

- `NbgToolVisualizationEventContractTest.supportedKindsAndStatesAreStable`
- `NbgToolVisualizationEventContractTest.normalizesToolStatesForPresentation`
- `NbgToolVisualizationEventContractTest.derivesKindFromInlinePreviewsAndTerminalOutput`
- `NbgToolVisualizationEventContractTest.todoTeamAndAgentStatusesProjectToSharedToolStatusModel`
- `NbgToolVisualizationEventContractTest.terminalTeamStatusesMapToTerminalVisualizationStates`
- `NbgToolVisualizationEventContractTest.teamProjectionKeepsArtifactsOutAndBoundsVisibleText`
- `NbgToolVisualizationEventContractTest.terminalExitCodeMapsToFirstClassStateDetail`
- `NbgToolVisualizationEventContractTest.confirmationResolutionProducesCompactTimelineStatus`
- `NbgToolVisualizationEventContractTest.duplicateConfirmationResolutionMergePreservesAuditContext`
- `AndroidManifestBehaviorTest.toolVisualizationEventContractIsDocumentedAndWired`

Existing behavior tests:

- `AndroidManifestBehaviorTest.toolStatusVisibilityAndKeysAvoidNoisyGenericCards`
- `AndroidManifestBehaviorTest.localHanakoNativeToolEventsTriggerWriteDiffAndTerminalPreviews`
- `AndroidManifestBehaviorTest.restoredHistoryToolsDoNotKeepChatInRunningState`

Commands:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgToolVisualizationEventContractTest --tests com.nbg.android.AndroidManifestBehaviorTest.toolVisualizationEventContractIsDocumentedAndWired
./gradlew --no-daemon :app:testDebugUnitTest
```

## Open Questions

- None for v1.

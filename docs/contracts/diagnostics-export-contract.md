---
title: "Diagnostics export contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["QA/Release Agent", "Security Agent", "Android Agent"]
review_agent: "Security Agent"
security_review: "required"
---

# Diagnostics Export Contract

## Status

Active for Diagnostics Export v1.

Audience:

- Implementing agents
- Security review agents
- QA/release agents
- Support/debugging workflows

## Purpose

Define the local, user-triggered, redacted diagnostics export behavior for NBG Android. This contract prevents useful support data from becoming an accidental exfiltration path for API keys, Hanako tokens, FTP passwords, terminal output, conversation text, Memory content, MCP command arguments, Skills paths, or user code.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
| Diagnostics exporter | Builds schema-versioned JSON and applies redaction | `NbgDiagnosticsExport.kt` |
| Diagnostics UI | Shows redaction notice and exposes copy/share actions | `NbgDiagnosticsExportUi.kt`, `NbgAgentUi.kt` |
| Diagnostics attachment writer | Writes redacted JSON attachments into app cache for share sheet delivery | `NbgDiagnosticsExportShare.kt`, `diagnostics_file_paths.xml` |
| Capability Registry | Provides stable capability health/status/error fields | `NbgCapabilityRegistry.kt` |
| Android system share sheet | Receives user-triggered attachment share intent | `Intent.ACTION_SEND`, `FileProvider` |
| Security tests | Assert sensitive material is absent from export | `NbgDiagnosticsExportTest.kt` |

## Trust Boundary

Diagnostics export crosses from the local app process to clipboard or Android share targets only after a user taps Copy or Share.

Boundaries:

- Local app process: builds the JSON snapshot from current in-memory state.
- Clipboard boundary: copy action writes the full redacted JSON to Android clipboard.
- Android share boundary: share action writes the redacted JSON to an app-cache attachment and sends a `FileProvider` URI through `ACTION_SEND` with one-time read permission.
- Hanako boundary: v1 reads Android-side state already present in `HanakoChatState` plus bounded launcher/server log tails; exported log tails must pass through the shared diagnostic redactor.
- Terminal/proot boundary: v1 does not read terminal output, shell history, workspace files, or proot user files except bounded Hanako diagnostic log tails.
- MCP/external resource boundary: v1 exports counts/status only, not command, args, cwd, URL, headers, env values, or registry paths.

## Data Structures

JSON shape:

```json
{
  "schema": "nbg-diagnostics-v1",
  "redactionVersion": 1,
  "generatedAtMs": 0,
  "excluded": ["api_keys", "hanako_tokens"],
  "app": {},
  "device": {},
  "capabilities": [],
  "launcher": {},
  "runtimePatch": {},
  "terminal": {},
  "recentErrors": [],
  "hanako": {},
  "urlApi": {},
  "mcp": {},
  "skills": {},
  "memory": {},
  "ftp": {},
  "pets": {}
}
```

Allowed data:

- App package/version.
- Device manufacturer/model, SDK int, Android release.
- Capability id/title/status/health/lastError/isBeta/diagnosticsAction after redaction.
- Hanako connection booleans, model/agent labels after redaction, permission/thinking modes, session presence, session count, search result count, runtime labels after redaction.
- URL API provider count, hashed provider id, redacted provider display name, host, model count, verified model count, selected-model presence.
- Launcher server-info presence, pid/version presence, launch/server log presence, and short redacted log tails.
- launcher failure codes as bounded enum strings derived from presence booleans and redacted launcher/server log patterns; these codes must not contain raw pid, port, version, token, path, or log text.
- Runtime patch status presence, computed state, attention flag, active patch-set match, target/active marker presence, marker match state, patch counts, active version, patch set version, target runtime version, and patch id/state/reason/error without raw target paths or raw marker values.
- Terminal readiness count and per-tab readiness/phase/diagnostic from `TerminalReadinessSnapshot`.
- Exported Capability Registry health must be refreshed from the same terminal readiness snapshot used for the `terminal` section so `capabilities[id=terminal]`, `terminal.*` counts, and `recentErrors` cannot disagree when the caller provides a stale registry.
- Recent redacted error summaries derived from current state, capability errors, runtime patch errors, and terminal diagnostics.
- MCP enabled/loading/error, connector counts, running count, agent-enabled count, tool count, auth/env/header counts, hashed connector id, transport/status/tool count/autoStart/auth type.
- Skills counts only: visible, enabled, bundles, external path count, source counts, readonly count.
- Memory counts only: count, enabledCount, loadedItemCount.
- FTP configured/running/protocol/port/hasPassword/message after redaction.
- Pet installed count, hidden state, current source, and built-in flag.

Forbidden data:

- API keys or upstream provider tokens.
- Hanako server token or websocket URL with token.
- FTP password.
- Terminal output, command output, shell history, raw logs.
- Conversation text, thinking text, tool payload bodies, file diffs.
- Memory item titles, content, tags, source sessions, turn IDs.
- User code or file contents.
- MCP command, args, cwd, URL, headers, env values, registry URL.
- Skills file paths, external paths, or descriptions.
- Full session paths or workspace paths.
- Runtime patch raw target paths, raw `targetMarker`, or raw `activeMarker` values.

## State Machine

| State | Entered When | Allowed Transitions | Terminal? |
| --- | --- | --- | --- |
| Idle | App is running with no diagnostics dialog | BuildPreview | No |
| BuildPreview | User taps diagnostics export | PreviewOpen, BuildFailed | No |
| PreviewOpen | Redacted JSON is built and shown | Copied, ShareSheetOpen, Closed | No |
| Copied | User taps Copy | PreviewOpen, Closed | No |
| ShareSheetOpen | User taps Share and Android chooser opens | Closed, PreviewOpen | No |
| BuildFailed | Export generation throws | Idle after error toast | Yes for that attempt |
| Closed | Dialog dismissed | Idle | Yes |

## Endpoint Or Event Semantics

Name: `nbg-diagnostics-v1`

Direction:

- Android app process to clipboard or Android share target.

Required fields:

- `schema`
- `redactionVersion`
- `generatedAtMs`
- `excluded`
- `app`
- `device`
- `capabilities`

Ordering guarantees:

- The JSON is a point-in-time snapshot. It does not stream updates.

Idempotency:

- Repeated export actions produce new `generatedAtMs` values and otherwise reflect current state.

Timeout behavior:

- v1 does not perform network collection and reads only bounded local diagnostic files/log tails, so export must complete synchronously for normal state sizes.

Retry behavior:

- User can tap diagnostics export again after closing the dialog.

## Error Semantics

| Error | Meaning | User-visible message | Recovery |
| --- | --- | --- | --- |
| Share target missing | Android has no app that can receive `ACTION_SEND` | `没有可用的分享目标` | Copy JSON instead |
| Attachment write failure | App cache cannot create/write the diagnostics attachment | `诊断导出分享失败` | Copy JSON instead |
| Snapshot build failure | Unexpected state serialization failure | Future v2 should show `诊断导出失败` | Retry after state changes or app restart |

## Compatibility

Versioning:

- `schema = "nbg-diagnostics-v1"` and `redactionVersion = 1`.

Migration:

- Additive fields are allowed.
- Removing or renaming existing fields requires a new schema.

Backward compatibility:

- Support tooling must ignore unknown fields.

Deprecation:

- v1 remains supported until a v2 contract supersedes it.

## Security Rules

- Sensitive fields: API keys, tokens, passwords, command args, headers, env values, file paths, Memory content, conversation text, terminal output, user code.
- Redaction rules: token-like strings, bearer headers, key/password fields, URL query secrets, and local filesystem paths must be replaced with `[redacted]` or `[path]`.
- Confirmation requirements: export is user-triggered; no automatic export.
- Hash/signature requirements: provider/connector ids may be hashed; do not hash secrets and then export the hash as a stable cross-report identifier unless there is a support need.
- Network/egress requirements: v1 performs no network upload; Android share is delegated only after user action.
- Share attachments: share must use a non-exported `FileProvider` rooted at app cache `diagnostics/`, grant read access with `FLAG_GRANT_READ_URI_PERMISSION`, and avoid putting the full JSON in `Intent.EXTRA_TEXT`.
- Attachment lifecycle: the writer prunes old `nbg-diagnostics-*.json` cache attachments before writing the newest redacted JSON.
- Launcher failure codes must come from a fixed local enum and must not embed raw log fragments or host-specific values.
- Audit/logging requirements: do not log the full diagnostics JSON.

## Test Oracle

Unit tests:

- `NbgDiagnosticsExportTest.diagnosticsExportIncludesStateCountsAndCapabilityHealth`
- `NbgDiagnosticsExportTest.diagnosticsExportRedactsSecretsPathsAndUserContent`
- `NbgDiagnosticsExportTest.diagnosticTextRedactsCommonTokenForms`
- `NbgDiagnosticsExportTest.runtimePatchSummarySupportsDiagnosticsDialogStatusLine`
- `NbgDiagnosticsExportTest.runtimePatchReaderExposesMarkerGateWithoutRawMarkersOrTargets`
- `NbgDiagnosticsExportTest.runtimePatchReaderHandlesMissingAndCorruptStatusFiles`
- `NbgDiagnosticsExportTest.diagnosticsExportRefreshesStaleTerminalCapabilityFromReadinessCounts`
- `NbgDiagnosticsExportTest.diagnosticsExportIncludesStructuredLauncherFailureCodesWithoutRawValues`
- `NbgDiagnosticsExportTest.diagnosticsExportAttachmentFileWriterCreatesJsonAttachmentAndPrunesOldExports`

Integration/source tests:

- `AndroidManifestBehaviorTest.diagnosticsExportIsRedactedAndUserTriggeredFromDrawer`
- `AndroidManifestBehaviorTest.capabilityRegistryFeedsDrawerAndAgentSurfaces`

Manual tests:

- Open drawer settings.
- Tap Diagnostics Export.
- Verify redaction notice is visible.
- Copy JSON and inspect for known dummy secrets.
- Share JSON attachment through Android chooser.

Negative tests:

- Seed URL API key, Hanako token-like error, FTP password, Memory content, MCP command args, and local paths; assert raw values do not appear in export.

## Operational Diagnostics

The export itself exposes:

- Current schema and redaction version.
- Current capability health.
- Terminal capability health refreshed from current `TerminalReadinessSnapshot` values.
- Last redacted capability/Hanako errors.
- Counts for URL API, MCP, Skills, Memory, FTP, and Pets.
- Hanako launcher/server-info derived booleans only, such as `serverInfoPresent`, `pidPresent`, and `versionPresent`.
- Hanako launcher failure code array, such as `server_info_missing`, `launcher_start_failed`, `port_in_use`, or `node_modules_missing`, without raw values.
- Explicit excluded-data list.

The explicit excluded-data list includes raw Hanako server-info, API keys, Hanako tokens, FTP passwords, terminal output, conversation text, Memory content, user code, MCP command args/paths, Skill file paths, and Pet resource URLs/paths.

## Open Questions

- None for v1.

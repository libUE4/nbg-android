---
title: "Diagnostics Export v1"
status: code-complete
date: 2026-07-04
type: plan
target_repo: /root/nbg-android
origin: "Issue 10: Diagnostics Export v1"
owner_agent: "QA/Release Agent"
review_agent: "Security Agent"
security_review: "required"
---

# Diagnostics Export v1

## Summary

Add a local, user-triggered, redacted diagnostics export for NBG Android public-beta hardening. The export is a JSON text bundle available from drawer settings, with copy/share actions and explicit excluded-data notice.

## Problem Frame

NBG Android now has multiple local runtimes and beta surfaces: HanakoPro, terminal/proot, URL API providers, MCP, Skills, Memory, FTP sharing, pets, and runtime patching. Without a standard diagnostics bundle, support/debug work either depends on screenshots or risks collecting raw secrets and user content.

## Requirements

| ID | Requirement | Priority | Acceptance |
| --- | --- | --- | --- |
| R1 | Local user-triggered export | P0 | Export opens only when user taps Diagnostics Export and only copies/shares after user action. |
| R2 | Redacted JSON schema | P0 | Output has `schema = nbg-diagnostics-v1`, `redactionVersion`, and an `excluded` list. |
| R3 | Useful state coverage | P0 | Include app/device, capabilities, Hanako state, launcher, runtime patch, terminal readiness, recent errors, URL API, MCP, Skills, Memory, FTP, and Pets. |
| R4 | No sensitive content | P0 | API keys, tokens, FTP password, terminal output, conversation text, Memory content, MCP commands/args/env/headers, Skills paths, user code, and private paths are absent. |
| R5 | Non-mutating diagnostics | P0 | Export does not create FTP credentials or write files. |
| R6 | Tests enforce privacy boundary | P0 | Unit/source tests cover redaction and forbidden fields. |

## Key Technical Decisions

| Decision | Choice | Reason | Tradeoff |
| --- | --- | --- | --- |
| D1 | Text JSON via clipboard/share sheet | Avoids new storage permissions and `FileProvider` complexity for v1. | Large exports are less convenient than an attachment. |
| D2 | Counts/status over raw data | Support gets enough state without user content. | Some deep debugging still needs device/manual logs. |
| D3 | Read runtime patch files but not raw patch targets | Runtime patch status is important for startup debugging; patch targets can contain paths. | Patch target details stay unavailable in export. |
| D4 | Non-mutating FTP diagnostic snapshot | Prevents diagnostics export from creating a new FTP password. | If FTP has never been configured, export reports it as unconfigured. |

## Implementation Units

### U1: Export Model And Redaction

Files/Areas:

- `app/src/main/java/com/nbg/android/NbgDiagnosticsExport.kt`
- `app/src/test/java/com/nbg/android/NbgDiagnosticsExportTest.kt`

Approach:

- Build `NbgDiagnosticsExportSnapshot`.
- Serialize to schema-versioned JSON.
- Redact common token/key/password/path patterns.
- Export only counts, statuses, booleans, health labels, hashes, and redacted error summaries.

### U2: UI Entry And Actions

Files/Areas:

- `app/src/main/java/com/nbg/android/NbgDiagnosticsExportUi.kt`
- `app/src/main/java/com/nbg/android/NbgAgentUi.kt`
- `app/src/main/java/com/nbg/android/NbgAgentDrawerUi.kt`
- `app/src/main/java/com/nbg/android/NbgCapabilityRegistry.kt`

Approach:

- Add Diagnostics Export feature card in drawer settings.
- Show redaction notice and selectable JSON preview.
- Copy to clipboard or share via `ACTION_SEND`.

### U3: Runtime Sources

Files/Areas:

- `app/src/main/java/com/nbg/android/TerminalWorkspace.kt`
- `app/src/main/java/com/nbg/android/MainActivity.kt`
- `app/src/main/java/com/nbg/android/NbgFileShareModel.kt`

Approach:

- Store terminal readiness snapshots from `TerminalReadinessController.analyzeOutput()`.
- Include launcher log/server-info presence and redacted log tails.
- Include runtime patch status from `android-runtime-patches.json` and `android-runtime-patches.active`.
- Use `NbgFileShareServerRegistry.diagnosticSnapshot()` to avoid credential creation.

## Scope Boundaries

In scope:

- Redacted text JSON export.
- Drawer settings entry.
- Copy/share user actions.
- Unit/source tests and contract.

Out of scope:

- Automatic upload.
- File attachment export.
- Full terminal logs.
- Full Hanako server logs.
- Memory item export.
- User project/code export.

## Security And Privacy

Threat Model:

- Diagnostics export could be shared with support or third-party apps. The bundle must remain useful after redaction and must not contain secrets or user content.

Sensitive Data:

- URL API keys, Hanako tokens, FTP password, terminal output, conversation text, Memory items, MCP command args/env/headers, Skills paths, user code, private filesystem paths.

Network/Egress Policy:

- No network upload. Android share sheet is invoked only by user action.

## Risks And Mitigations

| Risk | Impact | Mitigation | Owner |
| --- | --- | --- | --- |
| Secret leaks through error text | API/provider compromise | Regex redaction, direct negative tests, truncation. | Security Agent |
| Diagnostics action mutates app state | Surprising credential creation | Non-mutating FTP diagnostic snapshot. | Android Agent |
| Export too shallow for runtime bugs | Harder support triage | Include launcher presence, log tails, runtime patch status, and terminal readiness snapshots. | QA/Release Agent |

## Verification Strategy

Required commands:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgDiagnosticsExportTest --tests com.nbg.android.AndroidManifestBehaviorTest.diagnosticsExportIsRedactedAndUserTriggeredFromDrawer --tests com.nbg.android.AndroidManifestBehaviorTest.capabilityRegistryFeedsDrawerAndAgentSurfaces --tests com.nbg.android.TerminalReadinessControllerTest
./gradlew --no-daemon :app:testDebugUnitTest :terminal-core:testDebugUnitTest :android-mcp-server:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :android-mcp-server:assembleDebug
```

Manual behavior:

- Open drawer settings.
- Tap Diagnostics Export.
- Inspect preview notice and JSON.
- Copy and share through Android chooser.
- Search dummy export for known secret strings.

## Rollout And Recovery

Rollout:

- Ship behind the drawer settings entry as beta diagnostics.

Compatibility:

- Schema is additive under `nbg-diagnostics-v1`.

Recovery:

- If export causes UI issues, remove drawer entry without affecting persisted data. Export does not migrate storage.

## Sources And Research

- Issue queue: `docs/issues/2026-07-04-001-m1-p0-issue-queue.md`, Issue 10.
- Contract: `docs/contracts/diagnostics-export-contract.md`.
- Related source: `NbgCapabilityRegistry.kt`, `HanakoServerLauncher.kt`, `TerminalReadinessController.kt`, `NbgFileShareModel.kt`.

---
title: "Runtime patch gate contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["Terminal/Runtime Agent", "Security Agent"]
review_agent: "Android Agent"
security_review: "required"
---

# Runtime Patch Gate Contract

## Status

Active for Month 1 public beta hardening.

## Purpose

NBG Android may patch bundled HanakoPro runtime files for Android-specific behavior, but those patches must be version-gated, diagnosable, and recoverable. A patch must not silently mutate an unknown runtime target.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
| Pack marker | Pins the bundled runtime target by size and SHA-256. | `hanako-server-linux-arm64-node22.nbgpack.marker` |
| Launcher | Copies the pack, deploys it, renders runtime patch scripts, and starts HanakoPro. | `HanakoServerLauncher.kt` |
| Patch recorder | Writes structured patch status for diagnostics. | `$HANA_HOME/android-runtime-patches.json` |
| Runtime patch set | Groups Android-specific optional patches under one active version. | `NBG_HANAKO_RUNTIME_PATCH_SET_VERSION` |

## Data Structures

Pack marker:

```text
size=<bytes>;sha256=<hex>
```

Patch status file:

```json
{
  "patchSetVersion": "20260704-runtime-patch-gate-v2-memory-policy",
  "targetRuntimeVersion": "hanako-server-linux-arm64-node22",
  "targetMarker": "size=...;sha256=...",
  "activeMarker": "size=...;sha256=...",
  "updatedAt": "ISO-8601",
  "patches": [
    {
      "id": "android-default-workspace-root-v1",
      "state": "applied|skipped|failed",
      "target": "/opt/hanakopro-server/shared/default-workspace.js",
      "reason": "already-applied|target-marker-mismatch|pattern-missing|target-missing",
      "error": "optional bounded error text"
    }
  ]
}
```

## Rules

- The bundled pack marker is read from the source asset marker before falling back to APK metadata.
- Patch status records a semantic `targetRuntimeVersion` alongside marker presence/match state so support can distinguish runtime target family without exposing raw marker values.
- The source asset marker must match the actual pack size and SHA-256.
- Bundled runtime patches only mutate files when `activeMarker == targetMarker`.
- Marker mismatch records `state=skipped` with reason `target-marker-mismatch`.
- Optional patch failure records `state=failed` and must not block base server startup.
- Runtime patch set changes use `android-runtime-patches.active` to force stale server cleanup before health reuse.
- Source fallback uses `source-fallback` as its target marker because it has no bundled pack marker.
- Patch status must stay local under `$HANA_HOME` and be suitable for diagnostics export.
- Diagnostics export may expose target runtime version, marker presence, and match/mismatch state, but must not export raw marker values or raw patch target paths.

## Patch IDs

| Patch ID | Target | Required Behavior |
| --- | --- | --- |
| `android-upstream-user-agent-require-v1` | `$HANA_HOME/android-upstream-user-agent.cjs` | Inject stable upstream `User-Agent` through `NODE_OPTIONS`. |
| `android-default-workspace-root-v1` | `shared/default-workspace.js` | Use `/root` instead of Desktop workspace on Android. |
| `android-chat-tool-details-v1` | `server/routes/chat.js` | Preserve tool output/details for Android UI cards. |
| `android-memory-context-policy-v1` | `plugins/memory/lib/memory-store.js` | Enforce `nbg-memory-context-v1` for Memory save/update and filter blocked Memory from `memory:context`. |

## Test Oracle

Unit/source tests:

- `AndroidManifestBehaviorTest.androidHanakoServerUsesStableUpstreamUserAgent`
- `AndroidManifestBehaviorTest.bundledHanakoServerPackOnlyShipsNbgEngineeringCoreSkill`
- `AndroidManifestBehaviorTest.diagnosticsExportIsRedactedAndUserTriggeredFromDrawer`
- `NbgDiagnosticsExportTest.runtimePatchSummarySupportsDiagnosticsDialogStatusLine`
- `NbgDiagnosticsExportTest.runtimePatchReaderExposesMarkerGateWithoutRawMarkersOrTargets`
- `NbgDiagnosticsExportTest.runtimePatchReaderHandlesMissingAndCorruptStatusFiles`

Commands:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :terminal-core:testDebugUnitTest :android-mcp-server:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :android-mcp-server:assembleDebug
```

## Open Questions

- None for Month 1 runtime patch marker diagnostics.

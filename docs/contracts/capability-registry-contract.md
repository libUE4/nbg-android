---
title: "Capability registry contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["Android Agent", "UX Review Agent", "QA/Release Agent"]
review_agent: "UX Review Agent"
security_review: "required when diagnostics include sensitive fields"
---

# Capability Registry Contract

## Status

Active for Month 1 public beta hardening.

## Purpose

NBG Android needs one shared capability health model for Chat, drawer/settings, future diagnostics export, and release review. UI code should not independently invent health semantics for Terminal, HanakoPro, URL API, MCP, Skills, Memory, FTP, Pets, or feedback export.

## Data Structure

Each capability exposes:

| Field | Meaning |
| --- | --- |
| `id` | Stable machine id, for example `mcp` or `url_api`. |
| `title` | User-facing title. |
| `status` | Short user-facing state label. |
| `health` | One of `Healthy`, `Unknown`, `Degraded`, `Failed`. |
| `lastError` | Redacted, readable latest failure message. |
| `isBeta` | Whether the capability is public-beta/experimental. |
| `diagnosticsAction` | Stable diagnostics/export action key. |

## Capability IDs

| ID | Owner Surface | Notes |
| --- | --- | --- |
| `terminal` | Terminal / IDE loop | Consumes live tab readiness snapshots from `TerminalWorkspace`. |
| `hanako` | HanakoPro bridge | Connection and last bridge error. |
| `url_api` | URL API providers | Number of saved providers/models, no raw keys. |
| `mcp` | MCP page | Enabled/running connector health. |
| `skills` | Skills page | Enabled and visible skill counts. |
| `memory` | Memory page | Enabled and total memory counts. |
| `ftp` | File share dialog | Running/stopped/error without exposing password. |
| `pets` | Pets/PetDex | Installed/current/manifest error status. |
| `feedback_export` | Future diagnostics export | Placeholder until Issue 10 implementation. |

## Rules

- Capability status labels must be short enough for drawer chips and compact cards.
- `lastError` must not contain API keys, passwords, file contents, or raw memory content.
- URL API diagnostics may include counts and provider ids, not secret values.
- FTP diagnostics may include loopback host/port and error text, not password.
- Public beta features may be `isBeta=true`, but health and error semantics still follow this contract.
- UI surfaces may reorder or filter capabilities, but must not invent a separate health enum.
- Terminal health is `Failed` when any tab readiness snapshot fails, `Unknown` while any tab is still installing, and `Healthy` when ready or no terminal has started yet.
- Terminal status labels must summarize all known terminal tabs with ready/installing/failed counts, for example `2 就绪 · 1 启动中 · 1 失败`, instead of hiding mixed states behind a single `失败` or `启动中` label.
- Terminal readiness snapshots must be maintained by `TerminalWorkspace` from each session's output flow, not only by the visible Terminal page's Compose observer, so drawer capability health and diagnostics remain fresh after navigating away from Terminal.
- Terminal readiness snapshot changes must increment an observable workspace readiness version that Agent UI consumes before building the shared capability registry.
- Diagnostics export must refresh the exported Terminal capability from the same terminal readiness snapshot list used for the `terminal` diagnostics section, so stale caller-provided registries cannot disagree with readiness counts.
- Diagnostics export v1 must include the full redacted capability registry, including Healthy capabilities, so support/debugging can compare healthy baseline, beta flags, and diagnostics actions across devices. Failed/degraded/unknown summaries belong in `recentErrors`; the registry itself must not be filtered down to only problem capabilities.
- Terminal `lastError` may include a redacted startup diagnostic such as `exit_code=1`, but must not include full terminal output.
- Terminal startup failures that happen before a terminal session exists must still write a failed readiness snapshot so capability health and diagnostics export do not fall back to `Healthy`/`本地`.
- Registry summary labels must treat `Unknown` as a visible problem state after `Failed` and `Degraded`; installing or otherwise unknown capabilities must not produce `能力正常`.
- Diagnostics export must include `readyCount`, `installingCount`, and `failedCount` alongside the existing per-tab readiness snapshots.

## Test Oracle

Unit/source tests:

- `NbgCapabilityRegistryTest.registryExposesRequiredCoreCapabilities`
- `NbgCapabilityRegistryTest.registrySurfacesReadableFailureStates`
- `NbgCapabilityRegistryTest.terminalCapabilitySurfacesInstallingReadinessAsUnknown`
- `NbgCapabilityRegistryTest.terminalCapabilitySurfacesMultiTabReadinessCounts`
- `TerminalWorkspaceTest.startupFailureWritesTerminalReadinessSnapshotBeforeSessionExists`
- `TerminalWorkspaceTest.readinessOutputFlowUpdatesWorkspaceWithoutComposeObserver`
- `TerminalWorkspaceTest.recordReadinessOutputKeepsReadyStateAcrossRecreatedObservers`
- `TerminalReadinessControllerTest.clearedSessionCanReportFailureAfterPreviouslyReady`
- `NbgDiagnosticsExportTest.diagnosticsExportRefreshesStaleTerminalCapabilityFromReadinessCounts`
- `AndroidManifestBehaviorTest.capabilityRegistryFeedsDrawerAndAgentSurfaces`

Commands:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :terminal-core:testDebugUnitTest :android-mcp-server:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :android-mcp-server:assembleDebug
```

## Open Questions

- None for the Month 1 public-beta capability registry diagnostics export shape.

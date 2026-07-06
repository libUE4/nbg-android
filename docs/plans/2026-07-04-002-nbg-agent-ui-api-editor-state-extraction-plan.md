---
title: "NbgAgentUi API editor state extraction"
status: code-complete
date: 2026-07-04
type: plan
target_repo: /root/nbg-android
origin: "Issue 9: NbgAgentUi.kt Incremental State Extraction"
owner_agent: "Android Agent"
review_agent: "UX Review Agent"
security_review: "not-required"
---

# NbgAgentUi API Editor State Extraction

## Summary

Extract the URL API editor draft/runtime state out of `NbgAgentUi.kt` into a dedicated state holder without changing the visible URL API flow. The user-visible behavior remains the same: add/edit provider, fetch models, verify a model, save a provider, dismiss with Back, and preserve verified-model gating.

## Problem Frame

`NbgAgentUi.kt` owns chat runtime state, tool status buffering, history recovery, shell navigation, URL API editor state, preferences, and pets in one composable. That concentration makes future commercial hardening slower because unrelated state changes can accidentally affect chat streaming or recovery behavior.

This issue intentionally starts with the URL API editor state because it is lower risk than streaming/tool status and has a compact boundary: dialog drafts, busy/message flags, verified model ids, and request serial invalidation.

## Requirements

| ID | Requirement | Priority | Acceptance |
| --- | --- | --- | --- |
| R1 | Extract one state holder only | P0 | URL API editor fields move to `NbgAgentApiEditorState`; chat/tool/pet state stays unchanged. |
| R2 | Preserve stale async request protection | P0 | Fetch/verify callbacks still check request serial, URL snapshot, API key snapshot, and model existence. |
| R3 | Preserve verification gating | P0 | Saving keeps `selectedModelId` verified-only and stores `verifiedModelIds`; runtime model list remains filtered by `nbgVerifiedUrlApiModels`. |
| R4 | Avoid visual redesign | P0 | `NbgUrlApiEditorDialog` wiring and labels remain unchanged. |
| R5 | Add direct state holder tests | P1 | Unit tests cover open/close, credential change reset, request serial, fetch/verify results, and selected verified fallback. |

## Key Technical Decisions

| Decision | Choice | Reason | Tradeoff |
| --- | --- | --- | --- |
| D1 | Dedicated file `NbgAgentApiEditorState.kt` | Keeps extracted state discoverable and avoids growing `NbgAgentUiLogic.kt` with mutable Compose state. | Adds one small source file. |
| D2 | Keep `models` as all fetched models plus `verifiedModelIds` as gate | Existing UI can show fetched models while only verified models can be selected for runtime/default use. | Storage may include unverified model metadata, but no API key is added to metadata storage. |
| D3 | Keep network calls in `NbgAgentUi.kt` | State holder stays UI-state focused and testable; it does not own clients or coroutine scopes. | `NbgAgentUi.kt` still owns fetch/verify orchestration. |

## High-Level Technical Design

Participants:

- `NbgAgentUi.kt`: owns shell state, URL API store, upstream API client, coroutine scope, and persistence.
- `NbgAgentApiEditorState.kt`: owns in-memory URL API editor state and pure state transitions.
- `NbgAgentUrlApiUi.kt`: renders the existing dialog and calls callbacks.
- `NbgApiStore.kt`: persists URL API metadata and encrypted API key material.

Data flow:

- Add/edit opens `apiEditor.open(entry)`.
- Dialog input writes drafts through `apiEditor.nameDraft`, `updateBaseUrl()`, and `updateApiKey()`.
- Fetch/verify starts with `apiEditor.nextRequestSerial()` and captures URL/key/model snapshots.
- Async callback applies only when `apiEditor.isCurrentRequest(serial)` and snapshots still match.
- Save normalizes `effectiveBaseUrlDraft.ifBlank { baseUrlDraft }`, persists all fetched models plus verified ids, and writes verified-only `selectedModelId`.

State transitions:

- `open`: fill drafts from existing entry or empty state, clear busy/message, show dialog.
- `close`: increment request serial, hide dialog, clear editing target, clear busy/message.
- `updateBaseUrl` / `updateApiKey`: increment request serial, clear effective URL and verified ids, stop busy state.
- `applyFetchedModels`: replace model list, select first model for display, clear verification, clear effective URL, stop busy.
- `applyVerifiedModel`: if successful, add model to verified ids, select it, set effective URL, stop busy.

Compatibility:

- URL API storage format does not change.
- Existing encrypted API key storage remains unchanged.
- Existing `nbgVerifiedUrlApiModels` continues to filter runtime model availability.

## Implementation Units

### U1: API Editor State Holder

Goal:

- Move URL API editor mutable fields and transitions out of `NbgAgentUi.kt`.

Requirements:

- R1, R2, R3, R5

Files/Areas:

- `app/src/main/java/com/nbg/android/NbgAgentApiEditorState.kt`
- `app/src/test/java/com/nbg/android/NbgAgentApiEditorStateTest.kt`

Approach:

- Use Compose `mutableStateOf` properties for dialog fields.
- Keep request serial private-set and expose `nextRequestSerial()` / `isCurrentRequest()`.
- Keep transition methods small enough to unit test directly.

Verification:

- `NbgAgentApiEditorStateTest` covers state transitions and verified-only selection.

### U2: NbgAgentUi Wiring

Goal:

- Replace scattered local editor state variables with one `apiEditor` holder.

Requirements:

- R1, R2, R3, R4

Files/Areas:

- `app/src/main/java/com/nbg/android/NbgAgentUi.kt`
- `app/src/test/java/com/nbg/android/AndroidManifestBehaviorTest.kt`

Approach:

- Create `val apiEditor = rememberNbgAgentApiEditorState()`.
- Keep dialog component, labels, and save persistence flow intact.
- Update source-contract assertions to look for the new holder and stale request guards.

Verification:

- Targeted app unit tests.
- Full app/module JVM tests and debug build.

## Scope Boundaries

In scope:

- URL API editor in-memory state and wiring.
- Tests for extracted state holder and source-level wiring.
- Issue queue evidence.

Out of scope:

- Chat runtime state extraction.
- Tool status state extraction.
- Visual redesign of URL API screens.
- URL API storage format changes.
- Device screenshots.

Non-goals:

- Do not introduce dependency injection for the upstream API client in this issue.
- Do not change how fetched model metadata is persisted.

## Security And Privacy

Threat Model:

- URL API keys are sensitive, but this issue only moves in-memory draft state.

Trust Boundary:

- No new network boundary.
- No new file/service boundary.

Sensitive Data:

- `apiKeyDraft` remains in Compose memory only until saved through `NbgApiStore`, which already writes key material through encrypted storage.

Local Storage Policy:

- No storage schema change.
- Do not write plaintext API keys into metadata.

Network/Egress Policy:

- Fetch/verify behavior remains user-triggered and unchanged.

## Risks And Mitigations

| Risk | Impact | Mitigation | Owner |
| --- | --- | --- | --- |
| Stale fetch/verify callback mutates a newer editor session | Wrong models or verification state shown | Request serial plus URL/key/model snapshot checks remain in `NbgAgentUi.kt`. | Android Agent |
| URL/API key edit leaves old verified models | User could save a stale verified model | `updateBaseUrl()` and `updateApiKey()` clear verified ids and effective URL. | Android Agent |
| Future agent misreads verified model persistence | Unverified model metadata may be dropped unintentionally | This plan records that full fetched `models` are stored while runtime selection is verified-only. | Coordinator Agent |

## Verification Strategy

Required commands:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgAgentApiEditorStateTest --tests com.nbg.android.AndroidManifestBehaviorTest
./gradlew --no-daemon :app:testDebugUnitTest :terminal-core:testDebugUnitTest :android-mcp-server:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :android-mcp-server:assembleDebug
```

Behavior checks:

- URL API editor opens for add and edit.
- Back closes the editor before leaving the URL API page.
- Changing URL or API key clears verified model state.
- Fetch/verify results from stale requests do not apply.
- Save keeps verified-only selected model.

Regression checks:

- Existing chat streaming, tool cards, history recovery, model selection, URL API runtime switching, and pets stay untouched by this issue.

Evidence to attach:

- Unit test output.
- Debug APK build output.

## Rollout And Recovery

Rollout:

- Ship as an internal refactor inside Month 1 stability/security foundation.

Compatibility:

- No migration required.

Recovery:

- If URL API editor behavior regresses, the change can be reverted by restoring local editor state in `NbgAgentUi.kt`; no persisted data changes are involved.

## Sources And Research

- User request: execute the long-term commercial-quality NBG Android plan.
- Related issue: `docs/issues/2026-07-04-001-m1-p0-issue-queue.md`, Issue 9.
- Related code: `NbgAgentUi.kt`, `NbgAgentUrlApiUi.kt`, `NbgApiStore.kt`.
- Hermes reference: `/tmp/hermes-agent` for planning discipline only; no runtime integration.

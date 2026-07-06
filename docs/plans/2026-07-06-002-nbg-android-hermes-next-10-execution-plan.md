---
title: "NBG Android Hermes next 10 execution plan"
status: in_progress
date: 2026-07-06
type: plan
target_repo: /root/nbg-android
origin: "User request: analyze the app, then execute the 10 follow-up jobs"
owner_agent: "Coordinator Agent"
review_agent: "Review Agent"
security_review: "required"
---

# NBG Android Hermes Next 10 Execution Plan

## Current App Analysis

Existing strong surfaces:

- Shell pages already exist for Chat, Agents, Terminal, MCP, Memory, Skills, Pets, Appearance, and URL API.
- Drawer already supports local session search through `HanakoChatController.searchSessions()`.
- Context compression already exists through `compressForkSession()` and Chat UI controls.
- Memory is inspectable/editable/deletable and now has explicit redacted export preview.
- Skill source integrity and learned Skill draft policy are present.
- Agents team has bounded delegation policy, but the UI is still mostly a capability/template page.
- Expert Review has a safety policy, but no user-facing selection/run UI yet.
- Checkpoint strings and tests indicate backend/runtime support exists, but the visible rollback experience still needs consolidation.
- Diagnostics export and capability registry already provide a good base for a Doctor page.

Primary gap:

- The app has many strong isolated capability pages, but lacks a single control/diagnosis surface and several advanced features do not yet have polished entry points.

## Execution Slices

### Slice 1: Control And Verification Foundation

Status: implemented in this increment.

Scope:

- Toolsets / Doctor page.
- Persistent toolset preferences.
- Expert Review default-off switch state.
- Unified local verification script.

Files:

- `NbgToolsetControl.kt`
- `NbgToolsetsDoctorUi.kt`
- `NbgChatPreferenceStore.kt`
- `NbgAgentDrawerUi.kt`
- `NbgAgentUi.kt`
- `scripts/nbg_test.sh`

Effect:

- Users can inspect app capability health and toggle toolsets from one page.
- Doctor status is backed by `NbgCapabilityRegistry`.
- One command runs the standard local test/build verification.

### Slice 2: Search, Summary, And Skill Learning UI

Status: in progress.

Scope:

- Improve session search result cards with source/type metadata. Implemented first increment: drawer rows now show match type, summary availability, pinned state, and cleaned result snippets.
- Add session summary index or local summary sidecar.
- Add Skill draft UI that uses `nbgReviewLearnedSkillDraft()`.
- Add Skill Curator metrics: use count, last used, stale, pinned, archive/restore.

Acceptance:

- Search finds previous work with enough context to reopen the right session.
- Skill drafts are visible, rejectable, and cannot install/enable without review.
- Curator never deletes Skill files automatically.

### Slice 3: Team, Expert Review, And Rollback UX

Status: next.

Scope:

- Agents Team subtask panel with bounded templates, role progress, cancel/timeout states, and consolidated result card.
- Expert Review UI for selecting two or more URL API models, showing separate reference outputs and a consolidated result.
- Checkpoint / rollback UX that exposes snapshot list and restore confirmation in a predictable place.

Acceptance:

- Team work is visible, cancellable, and evidence-backed.
- Expert Review remains opt-in, read-only, and side-effect-free.
- Rollback is easy to find before/after file-writing tasks.

## Ten Job Map

| Job | Status | Notes |
| --- | --- | --- |
| Toolsets switch page | Implemented | `NbgToolsetsDoctorScreen` with persisted `toolsetOverrides`. |
| Local session search | Improved | Drawer search already calls `searchSessions()`; rows now render match type, summary state, pinned state, and cleaned snippets. |
| Session summary index | Next | Needs local summary sidecar or backend summary field. |
| Skill draft UI | Next | Policy exists; UI flow still needed. |
| Skill Curator | Next | Needs usage metrics and archive model. |
| Agents Team subtask panel | Next | Policy exists; UI needs per-role task surface. |
| Expert Review UI | Next | Policy exists; URL API model selection flow needed. |
| Unified test script | Implemented | `scripts/nbg_test.sh`. |
| Doctor diagnostics page | Implemented | Toolsets page includes capability health summary. |
| Checkpoint / rollback UX | Next | Runtime support appears present; user-facing flow needs audit. |

## Verification

Implemented Slice 1 target tests:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgToolsetControlTest --tests com.nbg.android.AndroidManifestBehaviorTest.toolsetsDoctorControlIsDocumentedAndWired --tests com.nbg.android.AndroidManifestBehaviorTest.unifiedNbgTestScriptRunsStableLocalVerification
```

Result: `BUILD SUCCESSFUL`.

Slice 2 local session search verification:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgAgentConversationDisplayTest
./scripts/nbg_test.sh
```

Result: `BUILD SUCCESSFUL`.

Full verification command:

```bash
scripts/nbg_test.sh
```

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

Status: implemented.

Scope:

- Improve session search result cards with source/type metadata. Implemented first increment: drawer rows now show match type, summary availability, pinned state, and cleaned result snippets.
- Add session summary index or local summary sidecar. Implemented: history cache writes a local redacted `session-summary-index.json` sidecar with path, title, snippet, counts, and update time. Drawer search now uses the local sidecar as an immediate offline/fallback index and merges it with HanakoPro HTTP search results.
- Add Skill draft UI that uses `nbgReviewLearnedSkillDraft()`. Implemented local review-queue increment: Android now has a learned Skill draft queue model, JSON parser, SharedPreferences-backed local store, startup restore, reject/hide action, Skills page counts/rows, missing-evidence display, dangerous-tier highlight, and no auto-install/enable path.
- Add Skill Curator metrics: use count, last used, stale, pinned, archive/restore. Implemented Android local governance increment: Skills page now shows local use counts, archived count, most-used Skill, archive/restore controls, and explicitly forbids auto-delete/auto-enable.

Acceptance:

- Search finds previous work with enough context to reopen the right session.
- Skill drafts are visible, rejectable, and cannot install/enable without review.
- Curator never deletes Skill files automatically.

### Slice 3: Team, Expert Review, And Rollback UX

Status: implemented.

Scope:

- Agents Team subtask panel with bounded templates, role progress, cancel/timeout states, and consolidated result card. Implemented run-state increment: Agents page now surfaces the current team task, child Agent rows, evidence refs, task/agent stop controls, and final summary card.
- Expert Review UI for selecting two or more URL API models, showing separate reference outputs and a consolidated result. Implemented run increment: URL API page now supports verified-model selection, prompt entry, read-only multi-call execution, separate reference outputs, local consolidated summary, stale-run protection, and failure-state cleanup.
- Checkpoint / rollback UX that exposes snapshot list and restore confirmation in a predictable place. Implemented first boundary increment: latest-turn rollback dialog now names the Checkpoint/Rollback boundary, history refresh, and restored-file count feedback.

Acceptance:

- Team work is visible, cancellable, and evidence-backed.
- Expert Review remains opt-in, read-only, and side-effect-free.
- Rollback is easy to find before/after file-writing tasks.

## Ten Job Map

| Job | Status | Notes |
| --- | --- | --- |
| Toolsets switch page | Implemented | `NbgToolsetsDoctorScreen` with persisted `toolsetOverrides`. |
| Local session search | Implemented | Drawer search calls `searchSessions()` and now shows local summary-index hits immediately, then merges HanakoPro search results when available. Rows render match type, summary state, pinned state, and cleaned snippets. |
| Session summary index | Implemented | Local redacted `session-summary-index.json` sidecar is written with cached history and used by drawer search as an offline/fallback index. |
| Skill draft UI | Implemented | Skills page accepts and renders a learned Skill draft queue with local persistence, startup restore, reject/hide, pending/blocked/dangerous counts, and row-level evidence status. v1 intentionally keeps install/enable blocked until a separate reviewed promotion flow exists. |
| Skill Curator | Implemented v1 | Skills page shows governance counts, usage metrics, local archive/restore, most-used Skill, and no-auto-delete/no-auto-enable policy. |
| Agents Team subtask panel | Implemented v1 | Agents page shows current task status, child Agent progress, evidence refs, stop controls, bounded templates, and final summary. |
| Expert Review UI | Implemented | URL API page supports opt-in read-only multi-model review with separate outputs, local consolidated summary, stale request protection, and failure-state cleanup. |
| Unified test script | Implemented | `scripts/nbg_test.sh`. |
| Doctor diagnostics page | Implemented | Toolsets page includes capability health summary. |
| Checkpoint / rollback UX | Implemented v1 | Latest-turn rollback confirmation explains the Checkpoint/Rollback boundary, refreshes history after restore, and reports restored-file count. Arbitrary snapshot list is not reintroduced because current Android code and tests forbid the old checkpoint dialog/API surface. |

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

Slice 2 session summary sidecar verification:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.HanakoHistorySummaryIndexTest
```

Result: `BUILD SUCCESSFUL`.

Slice 2 local summary-index search/merge verification:

```bash
./gradlew :app:testDebugUnitTest --tests com.nbg.android.HanakoHistorySummaryIndexTest
```

Result: `BUILD SUCCESSFUL`.

Expert Review run-state/cancel verification:

```bash
./gradlew :app:testDebugUnitTest --tests com.nbg.android.NbgAgentExpertReviewStateTest --tests com.nbg.android.NbgExpertReviewPolicyTest --tests com.nbg.android.AndroidManifestBehaviorTest.androidKeepsAgentMemoryAndCodeGraphAsDefaultBackgroundTooling
```

Result: `BUILD SUCCESSFUL`.

Skill Curator / Team / Expert Review foundation verification:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgSkillCuratorModelTest --tests com.nbg.android.NbgExpertReviewPolicyTest --tests com.nbg.android.AndroidManifestBehaviorTest.androidKeepsAgentMemoryAndCodeGraphAsDefaultBackgroundTooling --tests com.nbg.android.AndroidManifestBehaviorTest.androidExposesHanakoSkillsControls
```

Result: `BUILD SUCCESSFUL`.

Skill draft / rollback boundary verification:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.AndroidManifestBehaviorTest.androidExposesHanakoSkillsControls --tests com.nbg.android.AndroidManifestBehaviorTest.rollbackDialogExplainsLatestTurnCheckpointBoundary
```

Result: `BUILD SUCCESSFUL`.

Learned Skill draft queue verification:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgLearnedSkillDraftPolicyTest --tests com.nbg.android.AndroidManifestBehaviorTest.androidExposesHanakoSkillsControls
```

Result: `BUILD SUCCESSFUL`.

Learned Skill draft local store verification:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgLearnedSkillDraftPolicyTest --tests com.nbg.android.NbgLearnedSkillDraftStoreTest --tests com.nbg.android.AndroidManifestBehaviorTest.androidExposesHanakoSkillsControls
```

Result: `BUILD SUCCESSFUL`.

Full verification command:

```bash
scripts/nbg_test.sh
```

Result: `BUILD SUCCESSFUL`.

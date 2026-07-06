---
title: "Hermes reference adoption for NBG Android"
status: implemented
date: 2026-07-06
type: plan
target_repo: /root/nbg-android
origin: "User request: compare Hermes Agent and write an adoption plan"
owner_agent: "Coordinator Agent"
review_agent: "Review Agent"
security_review: "required"
---

# Hermes Reference Adoption For NBG Android

## Summary

Adopt selected Hermes Agent product and engineering patterns in NBG Android without importing the Hermes runtime. The user-visible outcome is a stronger mobile coding loop: tasks declare what "done" means, the app records evidence before claiming completion, successful workflows can become reviewed Skills, Memory becomes visible and editable, and `Agents team` grows into a controlled background delegation surface. This plan keeps NBG Android-native and local-first while borrowing Hermes' discipline around learning, verification, and edge-based extensibility.

## Problem Frame

NBG Android already targets a mobile AI IDE and Agent workbench with chat, terminal execution, file/diff review, URL API configuration, Skills, Memory, MCP, pet status, and diagnostics in one app. The current product direction is compatible with Hermes' strongest ideas, but the wrong adoption path would pull NBG into a desktop/cloud gateway architecture that does not fit the six-month Android beta.

Current user impact:

- Users need clearer proof that an Agent coding task is finished.
- Skills and Memory are powerful but can become opaque if the user cannot inspect what was learned and why.
- The `Agents team` affordance exists as a product direction, but it needs a safe delegation model before real background work.
- Tool cards and diagnostics need consistent evidence, risk, and recovery language across terminal, MCP, Skills, and file actions.

Current technical limitation:

- There is no single task completion contract that ties Agent claims to build/test/log/diff evidence.
- Skill creation and external resource trust need explicit review and integrity gates before becoming a public-beta default.
- Memory visibility needs a stable UI and data contract rather than ad hoc state.
- Background delegation needs cancellation, progress, consolidation, and failure handling before it can run safely on mobile.

Known risks:

- Copying Hermes runtime architecture would add Python/gateway/cloud assumptions to Android core.
- Auto-learning can persist wrong or sensitive data if review and redaction are weak.
- Background agents can multiply cost, latency, battery usage, and dangerous tool actions.
- MoA-style expert review can become expensive and slow if enabled too early.

Why this needs a plan:

- The work crosses chat runtime, terminal evidence, Skills, Memory, MCP/tool visualization, diagnostics, and permission policy.
- It affects user trust and safety boundaries.
- It should be delivered as phased, reviewable issues instead of one broad feature branch.

## Requirements

| ID | Requirement | Priority | Acceptance |
| --- | --- | --- | --- |
| R1 | Keep Hermes as a reference, not a runtime dependency | P0 | No Hermes Python runtime, gateway, cloud deployment, or desktop assumptions are added to Android core. |
| R2 | Add task completion contracts | P0 | Agent coding tasks can declare completion criteria and attach build/test/log/diff evidence before being marked done. |
| R3 | Preserve local-first safety | P0 | Code, files, memories, keys, logs, and diagnostics remain local by default; network use is user-triggered or explicitly configured. |
| R4 | Gate learned Skills before trust | P0 | Any generated Skill has a draft review step, target path, source, permission tier, and integrity metadata before install or activation. |
| R5 | Make Memory inspectable | P1 | Users can view, search, edit, delete, and export memory entries with source/session metadata and redaction behavior. |
| R6 | Add safe background delegation | P1 | `Agents team` can run bounded background subtasks with progress, cancellation, risk gating, and one consolidated result. |
| R7 | Standardize tool evidence cards | P1 | Tool UI shows target, action, risk, result, evidence, failure reason, and recovery path for high-risk actions. |
| R8 | Defer expensive MoA by default | P2 | Expert-review/MoA features remain opt-in experiments until cost, latency, and UX are proven. |

## Key Technical Decisions

| Decision | Choice | Reason | Tradeoff |
| --- | --- | --- | --- |
| D1 | Android-native implementation | NBG implements the selected patterns in Kotlin/Compose and Hanako contracts. | More local work than embedding an existing runtime, but avoids architecture mismatch. |
| D2 | Evidence before completion | A task is not "complete" unless it has evidence matching its contract or an explicit user override. | Some small tasks may feel heavier; defaults must stay lightweight. |
| D3 | Review-before-learn | Generated Skills and Memory changes are drafts until accepted or policy-approved. | Slower than fully automatic learning, but safer for public beta. |
| D4 | Delegation through bounded task cards | Background agents are represented as explicit task cards with budgets and cancel controls. | Limits autonomy, but keeps mobile UX and permission state understandable. |
| D5 | MoA as a later URL API mode | Multi-model expert review belongs behind URL API/model selection, not in the default chat path. | Delays a flashy capability in favor of stability and safety. |

## High-Level Technical Design

Participants:

- `app`: owns Compose UI, chat state, tool cards, Skills/Memory/MCP surfaces, permission prompts, diagnostics export, and URL API configuration.
- `terminal-core`: provides command/build/test execution evidence and terminal output snapshots.
- `HanakoBridge` / `HanakoApiClient`: mediates Agent events, task state, Skills, Memory, and tool execution contracts.
- `docs/contracts`: records stable event, permission, skill integrity, memory, and tool visualization semantics.
- `docs/issues`: breaks this plan into owner/reviewer-scoped implementation issues.

Data flow:

1. User asks for a coding task.
2. NBG creates or infers a completion contract: target files, expected behavior, verification command, and required evidence.
3. Agent works through chat, terminal, file, diff, MCP, or Skill actions.
4. Tool actions emit structured events with risk and evidence metadata.
5. Before the task is marked done, NBG checks evidence against the contract.
6. If the task produced a reusable workflow, NBG can create a Skill draft for review.
7. If the task produced stable user/project knowledge, NBG can create Memory drafts for review or policy-based acceptance.
8. Background delegation uses explicit subtask cards and returns one consolidated result to the main conversation.

State transitions:

- `task.created` -> `task.running` -> `task.awaiting_evidence` -> `task.done` or `task.needs_attention`.
- `skill.draft` -> `skill.reviewed` -> `skill.installed` -> `skill.enabled` or `skill.rejected`.
- `memory.draft` -> `memory.saved` -> `memory.edited` or `memory.deleted`.
- `delegation.queued` -> `delegation.running` -> `delegation.consolidating` -> `delegation.done` or `delegation.cancelled` or `delegation.failed`.

Compatibility and migration:

- Existing chat, URL API, Skills, Memory, MCP, and terminal flows continue to work.
- New metadata should be additive and tolerant of missing fields.
- Existing Skills and Memory entries appear as legacy entries until enriched by future migration or user review.

Failure and fallback behavior:

- If evidence cannot be collected, the UI shows the missing evidence and lets the user run verification, revise criteria, or override manually.
- If Skill generation fails, the original chat/task remains intact and no partially trusted Skill is enabled.
- If Memory review fails, no new memory is saved silently.
- If a background subtask fails, the consolidated result shows which subtask failed and what can be retried.

## Implementation Units

### U1: Task Completion Contracts And Evidence

Goal:

- Give every non-trivial coding task a visible definition of done and evidence bundle.

Requirements:

- R2, R3, R7

Dependencies:

- Existing chat controller, terminal output snapshots, file/diff surfaces, diagnostics export plan.

Files/Areas:

- `app/src/main/java/com/nbg/android/HanakoChatController.kt`
- `app/src/main/java/com/nbg/android/HanakoBridge.kt`
- `app/src/main/java/com/nbg/android/NbgAgentMessageUi.kt`
- `terminal-core/src/main/java/com/nbg/android/terminal`
- `docs/contracts/tool-visualization-event-contract.md`
- `docs/contracts/diagnostics-export-contract.md`

Approach:

- Define a task evidence model with criteria, commands, artifacts, timestamps, and outcome.
- Render evidence inside existing tool/message cards rather than adding a separate workflow shell.
- Support simple defaults: no contract for casual chat, inferred contract for code edits, explicit contract for high-risk or multi-step tasks.
- Allow user override with a clear audit entry.

Patterns to Follow:

- Prefer existing NBG module patterns and stable state holders.
- Keep Android core small; push optional behavior to Skills, MCP, URL API, or terminal/runtime edge when possible.

Test Scenarios:

- Code edit task requires diff and test/build evidence before done.
- Failed command keeps task in `needs_attention`.
- User override records reason and does not hide missing evidence.
- Evidence export redacts secrets.

Verification:

- Command: `./gradlew --no-daemon :app:testDebugUnitTest :terminal-core:testDebugUnitTest`
- Manual behavior: run a small code-edit loop and confirm done state depends on evidence.
- Evidence: test output, tool-card screenshot, diagnostic export sample.

### U2: Learn-To-Skill Review Flow

Goal:

- Let users convert successful workflows into reviewed Skills without silently trusting generated code or instructions.

Requirements:

- R3, R4, R7

Dependencies:

- Skill source integrity contract, permission risk model, existing Skills UI and Hanako Skill APIs.

Files/Areas:

- `app/src/main/java/com/nbg/android/NbgAgentSkillsUi.kt`
- `app/src/main/java/com/nbg/android/HanakoApiClient.kt`
- `app/src/main/java/com/nbg/android/HanakoBridge.kt`
- `docs/contracts/skill-source-integrity-contract.md`
- `docs/contracts/permission-risk-model-contract.md`

Approach:

- Add a "Save as Skill" draft path from completed tasks.
- Show generated Skill name, description, files, permissions, source task, and integrity metadata before install.
- Default generated Skills to disabled until user review is complete.
- Require high/dangerous permission confirmation for file writes, command execution, external installs, or MCP side effects.

Test Scenarios:

- A completed workflow can produce a Skill draft.
- Draft rejection leaves no enabled Skill.
- Draft install records source and hash/signature metadata.
- Dangerous Skill permissions require explicit confirmation.

Verification:

- Command: `./gradlew --no-daemon :app:testDebugUnitTest`
- Manual behavior: generate, reject, install, disable, and reload a Skill.
- Evidence: unit output and screenshots of review/permission states.

### U3: Memory Timeline And Review

Goal:

- Make Agent Memory visible, searchable, editable, deletable, and exportable so learning is not a black box.

Requirements:

- R3, R5

Dependencies:

- Memory context contract, diagnostics redaction, existing Memory surface.

Files/Areas:

- `app/src/main/java/com/nbg/android/NbgAgentMemoryUi.kt` if present, or current Memory UI owner.
- `app/src/main/java/com/nbg/android/HanakoApiClient.kt`
- `app/src/main/java/com/nbg/android/HanakoBridge.kt`
- `docs/contracts/memory-context-contract.md`
- `docs/contracts/diagnostics-export-contract.md`

Approach:

- Add a timeline/list view grouped by source session, project, or date.
- Include source, confidence or origin type, last-used timestamp, and delete/edit actions.
- Route new memories through draft review for sensitive or project-derived data.
- Export memory with default redaction.

Test Scenarios:

- User can inspect memory entries and source metadata.
- User can edit/delete a memory and see the updated state in future turns.
- Export redacts secrets and excludes disabled/deleted entries.
- Legacy memory entries render without source metadata.

Verification:

- Command: `./gradlew --no-daemon :app:testDebugUnitTest`
- Manual behavior: create, edit, delete, and export memory entries.
- Evidence: UI screenshots and redaction sample.

### U4: Agents Team Background Delegation

Goal:

- Turn `Agents team` into a safe background subtask system for audits, research, build checks, and review passes.

Requirements:

- R3, R6, R7

Dependencies:

- Task evidence model, permission risk model, tool visualization contract, Hanako event contract.

Files/Areas:

- `app/src/main/java/com/nbg/android/NbgAgentComposerUi.kt`
- `app/src/main/java/com/nbg/android/NbgAgentMessageUi.kt`
- `app/src/main/java/com/nbg/android/HanakoChatController.kt`
- `docs/contracts/android-hanako-event-contract.md`
- `docs/contracts/tool-visualization-event-contract.md`
- `docs/contracts/permission-risk-model-contract.md`

Approach:

- Start with bounded templates: "review diff", "run verification", "inspect module", and "security pass".
- Each subtask has title, owner role, budget, allowed tools, risk tier, progress, cancel control, and result.
- Consolidate all results into one main-turn summary with evidence links.
- Keep background work suspended or cancelled when Android lifecycle constraints require it.

Test Scenarios:

- Multiple subtasks run and consolidate into one result.
- User can cancel one or all subtasks.
- High-risk tool requests pause for confirmation.
- App restart recovers or clearly marks interrupted delegation state.

Verification:

- Command: `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug`
- Manual behavior: launch two background review tasks, cancel one, confirm consolidated result.
- Evidence: test output, screenshots, lifecycle/recovery logs.

### U5: Expert Review / MoA Experiment

Goal:

- Explore a later opt-in expert review mode inspired by Hermes MoA without making it part of the default path.

Requirements:

- R1, R3, R8

Dependencies:

- URL API provider/model configuration, task evidence model, cost/latency display.

Files/Areas:

- `app/src/main/java/com/nbg/android/NbgAgentUrlApiUi.kt`
- `app/src/main/java/com/nbg/android/NbgAgentComposerUi.kt`
- `app/src/main/java/com/nbg/android/HanakoChatController.kt`

Approach:

- Treat expert review as a user-triggered mode that asks multiple selected models or roles for advice and then consolidates.
- Show estimated provider count, cost/latency warning, and cancellation.
- Do not let expert review mutate files or run tools directly in the first version.

Test Scenarios:

- Expert review is opt-in and off by default.
- Reference outputs are shown separately from consolidated result.
- Cancellation stops pending requests.
- No tool/file side effects happen in the experiment.

Verification:

- Command: `./gradlew --no-daemon :app:testDebugUnitTest`
- Manual behavior: run expert review with two configured URL API models.
- Evidence: screenshots and request/cancel logs.

## Scope Boundaries

In scope:

- Task completion contracts.
- Evidence capture and display.
- Reviewed Skill generation from completed workflows.
- Memory timeline/review/export behavior.
- Bounded background delegation for `Agents team`.
- Optional expert-review research after core safety work.

Out of scope:

- Importing Hermes Agent runtime.
- Building a Telegram/Slack/Discord/WhatsApp gateway.
- Cloud sandbox productization, hosted relay, accounts, billing, or enterprise admin.
- Default autonomous cron jobs.
- Full MoA as a default chat model path.

Non-goals:

- Do not redesign the entire chat UI.
- Do not move Android core responsibilities into Python.
- Do not persist secrets, raw logs, or generated memories without redaction/review policy.
- Do not allow background agents to bypass the permission risk model.

## Security And Privacy

Threat Model:

- Prompt injection may try to save malicious Skills, leak secrets through memories, run dangerous tools, or mark work complete without real evidence.
- Background subtasks may amplify an unsafe action across multiple agents.
- External Skill or MCP resources may be tampered with before install.

Trust Boundary:

- Android app process, local terminal runtime, Hanako server pack, and Android MCP surface remain distinct boundaries.
- User-approved URL API providers are network boundaries.
- External Skills, MCP bundles, and PetDex resources remain untrusted until integrity checks pass.

Sensitive Data:

- API keys, provider tokens, local code, terminal output, memory entries, diagnostics logs, file paths, and generated Skill contents.

Confirmation Policy:

- Low-risk read-only evidence can be collected automatically.
- Medium state changes require visible review or inline confirmation.
- High-risk file writes, Skill mutation, external install, MCP side effects, and command execution require explicit confirmation.
- Dangerous delete/overwrite/credential exposure actions require strong confirmation and audit trail.

Local Storage Policy:

- Store task/evidence metadata locally.
- Redact secrets in diagnostics and memory export.
- Keep generated Skill drafts disabled until reviewed.
- Use existing encrypted storage for credentials.

Network/Egress Policy:

- No upload by default.
- URL API calls use user-configured providers.
- Expert review and any multi-model mode must show provider usage before running.

## Risks And Mitigations

| Risk | Impact | Mitigation | Owner |
| --- | --- | --- | --- |
| Runtime architecture drift toward Hermes cloud/gateway design | Android beta scope expands and destabilizes | R1 and D1 forbid runtime import; review all new surfaces against Core / Edge Rule. | Coordinator Agent |
| Agent claims done without sufficient proof | User trusts broken code | Completion contracts require evidence or explicit user override. | Agent Platform Agent |
| Generated Skill contains dangerous behavior | Data loss or command execution risk | Draft review, permission tier, integrity metadata, and disabled-by-default install. | Security Agent |
| Memory stores sensitive or wrong data | Privacy leak or bad future behavior | Draft review for sensitive/project data, edit/delete/export, redaction tests. | Security Agent |
| Background agents drain battery or cost | Poor mobile UX and provider bill shock | Bounded subtasks, budgets, cancellation, lifecycle-aware suspension, provider count display. | Android Agent |
| Tool evidence model becomes too broad | Slow implementation and unstable contracts | Start with file/diff/terminal/build evidence, then extend through contracts. | Agent Platform Agent |
| MoA experiment distracts from beta-critical work | Stability/security slips | Keep MoA P2 and blocked behind U1-U4 completion evidence. | Coordinator Agent |

## Verification Strategy

Required commands:

```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :terminal-core:testDebugUnitTest
./gradlew --no-daemon :android-mcp-server:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
```

Behavior checks:

- A code-edit task cannot be marked complete until evidence is attached or user override is recorded.
- A successful workflow can create a Skill draft, and the draft can be rejected without side effects.
- Memory entries can be inspected, edited, deleted, and exported with redaction.
- Background subtasks show progress, request permissions, cancel cleanly, and consolidate results.
- Expert review is opt-in and side-effect-free.

Regression checks:

- Existing chat streaming, terminal startup, URL API model selection, MCP enable/disable, Skills reload/install, file share, pet status, and diagnostics export remain functional.
- No new plaintext secret storage.
- No non-loopback cleartext service is introduced.
- No new default network egress path is introduced.

Evidence to attach:

- Unit test output.
- Debug APK build output.
- Screenshots or screen recording for UI work.
- Terminal/build logs for task evidence.
- Redaction sample for diagnostics and memory export.
- Security review notes for Skill/Memory/delegation changes.

## Rollout And Recovery

Rollout:

1. Ship U1 task completion contracts and evidence as the foundation.
2. Ship U2 Learn-to-Skill review flow after evidence and permission UI are stable.
3. Ship U3 Memory timeline once memory source/redaction rules are contract-tested.
4. Ship U4 Agents team background delegation with bounded templates only.
5. Explore U5 expert review/MoA as an opt-in experiment after beta-critical safety gates pass.

Compatibility:

- All new persisted fields must be additive and optional.
- Legacy tasks, Skills, and Memory entries must render safely with missing metadata.
- Existing Hanako APIs should remain compatible unless a contract update is approved.

Recovery:

- Feature flags or settings should disable learned Skills, Memory drafts, background delegation, and expert review independently.
- If task evidence causes regressions, fall back to advisory evidence display without blocking send/chat.
- If Skill or Memory review causes regressions, keep drafts local and disabled until repaired.
- If delegation recovery is unreliable, disable background execution and keep single-agent chat behavior.

## Implementation Record

Implemented on 2026-07-06:

- U1 task completion evidence: added `NbgTaskCompletionEvidence.kt`, cached JSON parsing, inferred evidence for diff/file/terminal/build/test/team status, and standardized evidence strips/details in `NbgAgentMessageUi.kt`.
- U2 Learn-to-Skill safety gate: added `NbgLearnedSkillDraftPolicy.kt` so generated Skills can only become reviewed local drafts with completion evidence, source task id, reviewed target path, SHA-256 metadata, and permission tier; install/enable remain blocked in v1.
- U3 Memory review/export: extended Memory UI with source metadata, edit/delete preservation, explicit per-item redacted export preview, hashed identifiers/source sessions, sensitive scan gating, and enabled-item-only export.
- U4 Agents team delegation: added bounded templates and budgets in `NbgTeamDelegationPolicy.kt`, with `HanakoChatController.createTeamTaskInternal()` blocking unknown background requests before execution.
- U5 expert review/MoA gate: added `NbgExpertReviewPolicy.kt` with default-off, explicit-user-trigger, two-model minimum, separate reference outputs, consolidation requirement, and no tool/file side effects.
- R1 audit: app/build/gradle code has no Hermes runtime dependency; Hermes references remain documentation/research only.

Verification run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :terminal-core:testDebugUnitTest :android-mcp-server:testDebugUnitTest :app:assembleDebug
```

Result: `BUILD SUCCESSFUL in 1m 2s` with 94 actionable tasks, 6 executed.

Manual evidence still useful before release:

- UI screenshots for task evidence cards and Memory export preview.
- A device run of two bounded team templates, including cancellation/recovery behavior.
- A user-triggered expert review UI flow when the later URL API surface is wired to `NbgExpertReviewPolicy`.

## Sources And Research

- User request: "对比 https://github.com/NousResearch/hermes-agent 他有什么特点", "有什么可以参考的", "写一下计划"
- Related code/docs:
  - `/root/nbg-android/AGENTS.md`
  - `/root/nbg-android/DESIGN.md`
  - `/root/nbg-android/docs/contracts/permission-risk-model-contract.md`
  - `/root/nbg-android/docs/contracts/skill-source-integrity-contract.md`
  - `/root/nbg-android/docs/contracts/memory-context-contract.md`
  - `/root/nbg-android/docs/contracts/tool-visualization-event-contract.md`
  - `/root/nbg-android/docs/contracts/diagnostics-export-contract.md`
- Related plan:
  - `/root/nbg-android/docs/plans/2026-07-04-001-stability-security-foundation-plan.md`
  - `/root/nbg-android/docs/plans/2026-07-04-003-diagnostics-export-v1-plan.md`
- Related contract:
  - `android-hanako-event-contract`
  - `permission-risk-model-contract`
  - `skill-source-integrity-contract`
  - `memory-context-contract`
  - `tool-visualization-event-contract`
  - `diagnostics-export-contract`
- Hermes reference:
  - https://github.com/NousResearch/hermes-agent/blob/main/README.md
  - https://github.com/NousResearch/hermes-agent/blob/main/AGENTS.md
  - https://github.com/NousResearch/hermes-agent/releases/tag/v2026.7.1

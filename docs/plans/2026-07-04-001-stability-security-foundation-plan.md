---
title: "M1 stability and security foundation"
status: active
date: 2026-07-04
type: plan
target_repo: /root/nbg-android
origin: "6-month public beta roadmap M1"
owner_agent: "Coordinator Agent"
review_agent: "Review Agent"
security_review: "required"
---

# M1 Stability And Security Foundation

## Summary

Month 1 establishes the engineering system and hard beta standards before new product scope expands. The month should produce a multi-Agent execution system, security/storage entry points, runtime patch diagnostics, terminal/build reliability baseline, first UI automation skeleton, and a redacted diagnostics plan.

## Problem Frame

NBG Android is already a large mobile Agent workbench with Android UI, bundled Hanako runtime, URL API configuration, terminal-core, Skills, Memory, MCP, pet UI, and file sharing. The main risk is not lack of features; it is shipping more capability without stable contracts, safety gates, diagnostics, and repeatable verification.

If Month 1 is skipped:

- API keys may remain in plaintext or migrate unsafely.
- Runtime patches may fail silently or apply to the wrong bundled pack.
- Terminal/build issues will remain hard to reproduce.
- Large UI state changes may destabilize chat and tool output.
- Public beta feedback will lack useful diagnostics.
- Multi-Agent work will generate inconsistent plans, tests, and review evidence.

## Requirements

| ID | Requirement | Priority | Acceptance |
| --- | --- | --- | --- |
| R1 | Repository contains reusable NBG development guide, plan template, contract template, issue template, and release gate. | P0 | New work can be assigned to an Agent without re-asking architecture questions. |
| R2 | Define Capability Registry v1 for Terminal, URL API, Skills, Memory, MCP, FTP/file sharing, pets, diagnostics, and runtime patching. | P0 | UI and diagnostics can consume `id`, `title`, `status`, `health`, `lastError`, `isBeta`, and action metadata. |
| R3 | Move URL API secrets toward encrypted storage with safe migration from legacy plaintext preferences. | P0 | Old users keep working; new saves do not write plaintext keys; migration failure does not lose keys. |
| R4 | Add Permission Risk Model v1 for file writes/deletes, command execution, external resource installs, MCP tools, Skill edits, Memory writes, and diagnostics. | P0 | Dangerous operations require strong confirmation and produce reviewable evidence. |
| R5 | Make Hanako runtime patching version-gated and diagnosable. | P0 | Patch success, skip, failure reason, target version, and fallback state are visible in logs/diagnostics. |
| R6 | Define terminal/build reliability baseline. | P0 | Terminal start, Ubuntu readiness, Node availability, Gradle/build command execution, long-output handling, and failure logs have tests or manual scripts. |
| R7 | Start incremental state extraction from `NbgAgentUi.kt` without visual redesign. | P1 | Chat streaming, tool cards, model selection, URL API, pets, and history recovery do not regress. |
| R8 | Define diagnostics export v1 with redaction. | P0 | Export includes version/device/capability/runtime errors and excludes API keys/user code by default. |
| R9 | Establish UI automation smoke skeleton. | P1 | Startup, chat entry, terminal entry, Skills/MCP/settings entry, and basic tool card display have a first runnable path or issue-ready implementation. |
| R10 | Disable app data backup while local preferences may contain secrets or service credentials. | P0 | Manifest blocks backup and a JVM test prevents re-enabling it accidentally. |
| R11 | Define public beta local-service boundaries for FTP, Hanako loopback HTTP, and Android MCP server. | P0 | Each service has localhost/export/auth/trust documentation and release blockers. |
| R12 | Add release-grade supply-chain checks for bundled packs, external downloads, JitPack dependencies, Node/Ubuntu assets, and PetDex resources. | P0 | Every release artifact or downloaded executable/resource has checksum/signature or explicit beta-risk exception. |
| R13 | Add release build hygiene gates. | P1 | Release/profile signing, versioning, ABI support, lint/test/release assemble, and asset validation are documented and issue-ready. |

## Key Technical Decisions

| Decision | Choice | Reason | Tradeoff |
| --- | --- | --- | --- |
| D1 | Use Hermes-style plan/contract/issue docs, not Hermes runtime. | The user asked for Hermes writing reference and local comparison while preserving NBG architecture. | NBG must maintain its own Android-specific contracts. |
| D2 | Treat safety/data/stability/recovery as hard beta standards. | Public beta trust depends on these more than new feature count. | Some visible features may move later. |
| D3 | Use incremental extraction for `NbgAgentUi.kt`. | Large UI rewrites risk regressions in chat/tool/session flows. | State debt is reduced over several issues instead of one rewrite. |
| D4 | Make diagnostics local and user-triggered first. | Fits local-first constraint and avoids premature telemetry/privacy work. | Weekly quality reports rely on tests, manual runs, and user-provided bundles. |
| D5 | Define contracts before stabilizing cross-boundary behavior. | Android/Hanako, MCP, Skills integrity, Memory, and diagnostics need long-term behavior. | Initial contract writing adds upfront work. |

## High-Level Technical Design

Month 1 uses three layers:

- Governance layer: `AGENTS.md`, plan template, contract template, issue template, release gate, roadmap.
- Safety and status layer: Capability Registry, Permission Risk Model, encrypted secret storage, diagnostics export.
- Runtime and UX reliability layer: Hanako patch diagnostics, terminal/build baseline, incremental UI state extraction, UI automation skeleton.

The first implementation pass should avoid broad product changes. It should create stable seams for later Months 2-6 work.

## Current Risk Inventory

This inventory is based on the current repository state and must be reduced during Month 1.

Security:

- `app/src/main/AndroidManifest.xml` allowed app data backup while URL API keys and FTP credentials are stored in SharedPreferences. Immediate mitigation: backup disabled and guarded by `AndroidManifestBehaviorTest`.
- `NbgApiStore.kt` stores URL API `apiKey` values in plaintext JSON.
- `NbgFileShareModel.kt` stores FTP password state, and `NbgFtpFileServer.kt` exposes write/delete capability that needs a clearer public beta trust boundary.
- `HanakoApiClient.kt` and `HanakoServerLauncher.kt` configure powerful Agent behavior; default permission and safety-review choices need explicit product policy.
- Local loopback cleartext is allowed for `127.0.0.1` and `localhost`; it must remain loopback-only and documented.
- External sources and downloads need checksum/signature policy: JitPack, Node/Ubuntu assets, runtime `wget`, Skills, PetDex, and MCP resources.

Stability:

- The app packages large runtime assets: Hanako server pack, Ubuntu rootfs, Node archive, proot/native libraries. Cold start, storage, upgrade, and low-memory behavior need baseline testing.
- Release config is early-stage: arm64-only, `versionCode=1`, target SDK 34, profile using debug signing. This needs a release hygiene issue before public beta.

Experience:

- Some primary UI affordances remain placeholders, including add-context and Agents team surfaces. Public beta must either implement or feature-flag them.
- Streaming Markdown, pet rendering, and dense tool UI need small-screen and theme screenshots.

Engineering:

- Tests are mainly JVM tests; there is no sustained `androidTest`/device lifecycle gate yet.
- `android-mcp-server` has no dedicated test coverage.
- Product boundaries are mixed across main app, terminal runtime, bundled server, and standalone MCP app; plans and contracts must make boundaries explicit.

## Implementation Units

### U1: Engineering Planning System

Goal:

- Land the repository planning system required by the user-provided Hermes-style supplement.

Requirements:

- R1

Dependencies:

- Local Hermes reference at `/tmp/hermes-agent`.
- Existing NBG `DESIGN.md`.

Files/Areas:

- `AGENTS.md`
- `docs/plans/NBG_PLAN_TEMPLATE.md`
- `docs/contracts/NBG_CONTRACT_TEMPLATE.md`
- `docs/issues/NBG_MULTI_AGENT_ISSUE_TEMPLATE.md`
- `docs/release/NBG_PUBLIC_BETA_RELEASE_GATE.md`
- `docs/roadmap/2026-07-04-001-nbg-android-6-month-public-beta-roadmap.md`
- `docs/issues/2026-07-04-001-m1-p0-issue-queue.md`

Approach:

- Use frontmatter for plans, contracts, and issue templates.
- Use Hermes-style sections: Summary, Problem Frame, Requirements, Key Technical Decisions, High-Level Technical Design, Implementation Units, Scope Boundaries, Risks, Verification Strategy, Sources.
- Use behavior-based verification instead of empty "tests pass" language.

Test Scenarios:

- A new Agent can pick up the issue template and know owner, reviewer, files, tests, risks, and acceptance.
- A complex task can use the plan template without inventing headings.
- A cross-boundary behavior can use the contract template with trust, state, errors, compatibility, and test oracle.

Verification:

- Inspect docs for all required templates and links.
- Confirm roadmap references this M1 plan.

### U2: Capability Registry v1 Plan And Contract

Goal:

- Define a single capability status model for UI and diagnostics.

Requirements:

- R2

Dependencies:

- U1 templates.

Files/Areas:

- New plan under `docs/plans/`.
- Future implementation around `NbgAgentUi.kt`, `NbgAgentCapabilityPagesUi.kt`, `HanakoServerState.kt`, terminal readiness controllers, MCP/Skills/Memory UI, FTP/file sharing, pet store.

Approach:

- Define `CapabilityId`, `CapabilityStatus`, `CapabilityHealth`, and diagnostics action metadata.
- Start as an Android app model; do not force terminal-core or MCP server to depend on app UI.
- Include beta flag and last error.

Test Scenarios:

- Terminal unavailable.
- Hanako disconnected.
- URL API missing key.
- MCP disabled.
- Skill source invalid.
- Diagnostics export available.

Verification:

- Model unit tests after implementation.
- UI screenshot with healthy and failed capability states.

### U3: Encrypted Secret Store Migration

Goal:

- Replace plaintext URL API key persistence with encrypted storage.

Requirements:

- R3
- R10

Dependencies:

- Security review.
- Current `NbgApiStore.kt` behavior analysis.

Files/Areas:

- `app/src/main/java/com/nbg/android/NbgApiStore.kt`
- URL API settings UI and tests.

Approach:

- Keep app backup disabled while any local preferences may contain secrets or local service credentials.
- Add encrypted storage using Android Keystore backed APIs.
- Read legacy plaintext preferences once.
- Write encrypted value.
- Clear legacy plaintext only after encrypted write succeeds.
- Preserve fallback if migration fails and show a recoverable error.

Test Scenarios:

- Empty key.
- Existing plaintext key.
- Existing encrypted key.
- Corrupt legacy value.
- Failed encrypted write.
- Delete key.

Verification:

- Unit tests for migration and deletion.
- Manifest/test gate proving backup remains disabled.
- Manual URL API save/load on device or emulator.
- Inspect preferences to confirm no new plaintext key value is written.

### U3a: Local Service Boundary Review

Goal:

- Document and harden FTP, Hanako loopback HTTP, and Android MCP beta service boundaries.

Requirements:

- R11

Dependencies:

- Security review.
- Current service binding and exported state review.

Files/Areas:

- `app/src/main/res/xml/network_security_config.xml`
- `NbgFtpFileServer.kt`
- `NbgFileShareModel.kt`
- `HanakoApiClient.kt`
- `HanakoServerLauncher.kt`
- `android-mcp-server/src/main/java/com/nbg/android/mcpserver`

Approach:

- Confirm each local service binds only where intended.
- Define authentication/token policy where side effects are possible.
- Keep cleartext exceptions loopback-only.
- Make UI copy explicit about local file sharing and server state.

Test Scenarios:

- FTP disabled by default.
- FTP password is required for write/delete access.
- MCP beta server exported state and localhost behavior match manifest/service policy.
- Hanako loopback access is not reachable from non-loopback network interfaces.

Verification:

- Source inspection tests where possible.
- Device/network manual check.
- Security Agent review notes.

### U4: Permission Risk Model v1

Goal:

- Classify and confirm actions with user-visible side effects.

Requirements:

- R4

Dependencies:

- Security review.
- Tool visualization and confirmation UI inventory.

Files/Areas:

- `NbgAgentComposerUi.kt`
- `NbgAgentDialogs.kt`
- `NbgToolPresentation.kt`
- `HanakoToolParsing.kt`
- file/diff/write paths once implemented
- Skill install/update paths
- MCP tool paths

Approach:

- Define Low, Medium, High, Dangerous tiers.
- Require target/action/risk/recovery details for High and Dangerous actions.
- Record confirmation result for diagnostics where appropriate.

Test Scenarios:

- Read-only tool action does not interrupt.
- File write requires review.
- Delete/overwrite requires strong confirmation.
- External Skill install requires risk display.
- Memory write requires explicit user action.

Verification:

- Permission model unit tests.
- Manual screenshots for confirmation UI.

### U5: Runtime Patch Version Gate

Goal:

- Make HanakoPro bundled runtime patching observable and safe.

Requirements:

- R5

Dependencies:

- Current bundled pack marker.
- Launcher diagnostics design.

Files/Areas:

- `app/src/main/java/com/nbg/android/HanakoServerLauncher.kt`
- `app/src/main/assets/hanako-server-linux-arm64-node22.nbgpack.marker`
- future diagnostics export.

Approach:

- Define patch IDs and expected target markers.
- Record applied/skipped/failed status.
- Do not blindly patch when marker or target content does not match.
- Provide fallback state and user-visible diagnostic hint.

Test Scenarios:

- Marker matches and patch applies.
- Marker mismatch skips patch.
- Patch target missing.
- Patch already applied.
- Patch fails but basic server startup can continue when safe.

Verification:

- Unit tests or isolated patch function tests.
- Launcher log review.
- Diagnostics sample includes patch status.

### U6: Terminal / Build Reliability Baseline

Goal:

- Define repeatable checks for the IDE execution loop.

Requirements:

- R6
- R12

Dependencies:

- Existing terminal-core tests and build environment.

Files/Areas:

- `terminal-core/src/main/java/com/nbg/android/terminal`
- `TerminalStartupController.kt`
- `TerminalReadinessController.kt`
- `TerminalWorkspace.kt`
- build wrapper path once identified.

Approach:

- Document startup phases and readiness signals.
- Track first-input-ready time, Node availability, Ubuntu readiness, long-output behavior, and build failure logs.
- Add checksum/signature requirements for Node/Ubuntu assets and any runtime download before release.
- Keep this local and testable before any cloud telemetry.

Test Scenarios:

- Terminal starts.
- Node command runs.
- Long output does not freeze UI.
- Command failure returns readable status.
- Background/foreground recovery keeps or explains session state.

Verification:

- `./gradlew :terminal-core:testDebugUnitTest`
- Manual device script and log capture.
- Asset checksum validation issue exists before public beta.

### U7: `NbgAgentUi.kt` Incremental State Extraction

Goal:

- Reduce central state risk without changing product behavior.

Requirements:

- R7

Dependencies:

- Existing chat/tool/session tests.

Files/Areas:

- `app/src/main/java/com/nbg/android/NbgAgentUi.kt`
- `NbgAgentUiLogic.kt`
- `NbgAgentChatUi.kt`
- `NbgAgentMessageUi.kt`
- `NbgAgentUrlApiUi.kt`
- pet and MCP state surfaces.

Approach:

- Extract one state holder at a time: chat runtime state, tool status state, API editor state.
- Do not combine visual redesign and state extraction in the same issue.
- Preserve behavior for streaming, interruption, model selection, history recovery, and pets.

Test Scenarios:

- Send message and receive streaming output.
- Tool card parses and updates.
- Switch model/API settings.
- Restore history.
- Pet status remains visible.

Verification:

- App unit tests.
- Manual core chat flow.
- UI screenshots only if visible behavior changes.

### U8: Diagnostics Export v1

Goal:

- Define and implement a user-triggered redacted diagnostic bundle.

Requirements:

- R8

Dependencies:

- Capability status model.
- Runtime patch status.
- Redaction rules.

Files/Areas:

- future diagnostics UI
- `HanakoServerLauncher.kt`
- capability registry implementation
- app version/device metadata.

Approach:

- Export app version, device info, capability states, launcher status, recent errors, terminal readiness status, and patch status.
- Do not include user code, API keys, provider tokens, full Memory contents, or full terminal output by default.
- Add a visible redaction sample in review evidence.

Test Scenarios:

- Export when healthy.
- Export after Hanako error.
- Export after terminal failure.
- Redaction removes API keys and token-like values.

Verification:

- Redaction unit tests.
- Manual export review.

### U9: UI Automation Smoke Skeleton

Goal:

- Create a sustainable UI regression entry point.

Requirements:

- R9

Dependencies:

- Existing Gradle/Android test setup.

Files/Areas:

- Android test sources once added.
- Chat, terminal, Skills, MCP, settings UI surfaces.

Approach:

- Start with smoke coverage, not exhaustive automation.
- Cover app launch, chat entry, terminal entry, Skills/MCP/settings navigation, and one basic tool card state.
- Store screenshots/logs on failure.

Test Scenarios:

- Launch app.
- Open chat.
- Open terminal.
- Open Skills, MCP, and settings.
- Switch theme or verify primary layout on a small screen.

Verification:

- Local runnable command documented in the issue that implements it.
- Failure evidence path documented.

### U10: Release Build Hygiene

Goal:

- Make release/profile configuration reviewable before public beta.

Requirements:

- R13

Dependencies:

- Release gate.

Files/Areas:

- `app/build.gradle.kts`
- `settings.gradle.kts`
- Gradle wrapper and version catalog.
- release artifact/checksum scripts once added.

Approach:

- Define how release signing differs from profile/debug.
- Track versionCode/versionName policy.
- Document arm64-only support as a deliberate beta constraint or add additional ABI plan.
- Add lint, unit tests, release assemble, and asset validation to the release gate.

Test Scenarios:

- Release assemble succeeds.
- Profile build is not confused with signed public release.
- Artifact checksum is produced.
- Known dependency sources are reviewed.

Verification:

- `./gradlew :app:assembleRelease`
- Release artifact checksum.
- Dependency/source review notes.

## Scope Boundaries

In scope:

- Planning system docs.
- First roadmap and M1 plan.
- Security, runtime, diagnostics, UI automation, and reliability planning.
- Implementation issues ready for Agents.

Out of scope for this plan:

- Full code implementation of all Month 1 units in one change.
- Hermes runtime integration.
- Account/payment/cloud/app-store work.
- Large UI redesign.

Non-goals:

- A perfect governance system before any code ships.
- Broad telemetry.
- Automatic deletion or mutation of Skills/Memory without review.

## Security And Privacy

Threat Model:

- Local attacker or backup reader finds plaintext API keys.
- Agent writes, deletes, installs, or executes risky actions without clear consent.
- External resource contains malicious Skill/pet/MCP behavior.
- Diagnostic export leaks secrets or user code.
- Runtime patch hides failure and causes unsafe behavior.

Trust Boundary:

- Android app UI and local storage.
- Hanako bundled server process.
- proot Ubuntu terminal environment.
- external URL API providers.
- Skills/PetDex/MCP external sources.
- user-triggered diagnostic export.

Sensitive Data:

- API keys and provider tokens.
- User code and project paths.
- Memory content.
- Terminal output.
- Logs that may contain paths, prompts, keys, or model responses.

Confirmation Policy:

- Low risk: allow with visible status.
- Medium risk: inline review or lightweight confirmation.
- High risk: explicit confirmation with target/action/risk.
- Dangerous: strong confirmation and audit record.

Local Storage Policy:

- Secrets encrypted.
- Legacy plaintext read only for migration.
- Diagnostics redacted by default.
- Memory writes explicit and editable/deletable.

Network/Egress Policy:

- Default no upload of code, files, Memory, or logs.
- URL API requests require user-configured provider.
- Any telemetry or cloud sync requires a separate privacy-reviewed plan.

## Risks And Mitigations

| Risk | Impact | Mitigation | Owner |
| --- | --- | --- | --- |
| Governance docs become stale. | Agents ignore process. | Link docs from issues and review gates; update plans when implementation deviates. | Coordinator Agent |
| Key migration loses credentials. | User loses access to configured providers. | One-way cleanup only after encrypted write succeeds; keep fallback and tests. | Security Agent |
| Runtime patch gate blocks startup. | Chat cannot start. | Distinguish hard dependency patches from optional patches; expose fallback status. | Terminal/Runtime Agent |
| UI state extraction regresses chat. | Core workflow breaks. | Extract one state holder per issue and keep visual redesign separate. | Android Agent |
| Diagnostics export leaks private data. | Trust failure. | Redaction tests and manual export review are mandatory. | Security Agent |
| UI automation becomes too broad too early. | Slow, flaky test burden. | Start with P0 smoke flows only. | QA Agent |

## Verification Strategy

Required commands after code implementation begins:

```bash
./gradlew :app:assembleDebug
./gradlew test
./gradlew :app:testDebugUnitTest
./gradlew :terminal-core:testDebugUnitTest
```

Behavior checks:

- URL API key survives migration.
- Dangerous operation confirmation cannot be bypassed.
- Hanako patch status is visible for success, skip, and failure.
- Terminal starts and runs a Node command.
- Diagnostics export redacts secrets.
- Chat streaming and tool cards still behave after state extraction.

Evidence to attach:

- Test output.
- Device logs for runtime/terminal work.
- Screenshots for UI and confirmation work.
- Redacted diagnostics sample.
- Review Agent and Security Agent notes.

## Rollout And Recovery

Rollout:

- Ship behind normal app update path.
- Prefer internal builds before public release.
- Keep beta labels on unstable MCP/pet surfaces.

Compatibility:

- Legacy keys remain readable during migration.
- Existing sessions and histories should remain readable.
- Bundled pack marker controls patch behavior.

Recovery:

- If encrypted migration fails, retain legacy value and show error.
- If patching fails, log and continue when safe.
- If terminal bootstrap fails, surface readiness failure and diagnostics entry.

## Sources And Research

- User attachment: NBG Android 6-month commercial public beta plan.
- User attachment: Hermes-style NBG planning-system supplement.
- Local Hermes reference: `/tmp/hermes-agent/AGENTS.md`, `.plans/streaming-support.md`, `docs/plans/2026-06-09-003-fix-telegram-stream-overflow-continuations-plan.md`, `docs/relay-connector-contract.md`, `docs/chronos-managed-cron-contract.md`.
- Current NBG design baseline: `DESIGN.md`.
- Current NBG source map: `app`, `terminal-core`, `android-mcp-server`.
- M1 issue queue: `docs/issues/2026-07-04-001-m1-p0-issue-queue.md`.

# NBG Android Development Guide

## What NBG Is

NBG Android is an Android-native AI IDE, mobile Agent platform, and warm personal assistant. The app combines chat, local terminal execution, file and diff review, URL API model configuration, Skills, Memory, MCP, pet status, and diagnostics into one mobile workbench.

The public beta target is GitHub Releases in six months from 2026-07-04. The beta is allowed to label MCP and pet ecosystem features as beta, but safety, data handling, stability, recovery, and release hygiene are not beta quality.

## Product Boundaries

Primary users:

- AI developers who want a real mobile coding loop.
- Agent builders and players who want portable Skills, Memory, MCP, and visible Agent state.

In scope for the six-month beta:

- Local Android execution through the existing app, `terminal-core`, bundled Hanako server pack, URL API configuration, Skills, Memory, MCP, file sharing, and pet status surfaces.
- A credible IDE loop: open a project, ask the Agent to modify code, review file/diff output, run a build, recover the session, and export diagnostics.
- Local-first data handling. Code, files, memories, keys, and logs must not be uploaded by default.

Out of scope for the six-month beta:

- Accounts, payment, cloud sandbox productization, enterprise admin backend, app-store release work, and deep integration with Hermes runtime.

Hermes is a writing and engineering-discipline reference only. NBG may borrow its planning, contract, issue, and review structure, but should not import its runtime architecture unless a future plan explicitly proves the value and migration cost.

## Core / Edge Rule

Keep Android core small and stable. Add capability at the edge first.

Core belongs to:

- App process lifecycle, foreground service integration, startup and recovery.
- Stable capability status models used by UI and diagnostics.
- Secret storage, permission/risk policy, local storage policy, and release gates.
- Terminal and Hanako launcher contracts required for the IDE loop.

Edge belongs to:

- Skills and user-created automation.
- MCP tools and beta device capabilities.
- URL API provider/model configuration.
- PetDex and pet status behavior.
- External resource sources and optional plugins.

Before adding code to core, answer:

1. Can this be a Skill, MCP tool, URL API adapter, or terminal/runtime script?
2. Does this change a stable contract used by chat, terminal, diagnostics, or release validation?
3. Does this touch secrets, file writes, command execution, external resource install, or user data?
4. Is there a plan document and a contract if the behavior must remain stable?

## Current Module Map

- `app`: Android app UI, Hanako bridge, chat runtime, URL API settings, Skills, Memory, MCP UI, pet UI, file share, FTP server, bundled Hanako server pack.
- `terminal-core`: proot/Ubuntu runtime, PTY, terminal model, Node bundle, terminal session and screen buffer.
- `android-mcp-server`: standalone Android MCP server surface.

High-risk source areas:

- `app/build.gradle.kts`, `settings.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/network_security_config.xml`: release profile, dependency sources, backup policy, and local cleartext exceptions.
- `app/src/main/java/com/nbg/android/NbgAgentUi.kt`: central orchestration and state concentration.
- `app/src/main/java/com/nbg/android/NbgApiStore.kt`: URL API key and provider storage.
- `app/src/main/java/com/nbg/android/NbgFileShareModel.kt`, `app/src/main/java/com/nbg/android/NbgFtpFileServer.kt`: FTP password, write/delete capability, and local service boundary.
- `app/src/main/java/com/nbg/android/HanakoServerLauncher.kt`: bundled server unpacking, runtime patching, process launch, diagnostics.
- `app/src/main/java/com/nbg/android/HanakoApiClient.kt`, `app/src/main/java/com/nbg/android/HanakoBridge.kt`: Agent permissions, Skill install/fetch behavior, and API contracts.
- `app/src/main/java/com/nbg/android/HanakoChatController.kt`: chat session lifecycle and tool events.
- `terminal-core/src/main/java/com/nbg/android/terminal`: local terminal execution and Ubuntu runtime.
- `terminal-core/src/main/assets`, `terminal-core/src/main/java/com/nbg/android/terminal/UbuntuBootstrapScript.kt`: bundled/runtime supply chain and download verification.
- `android-mcp-server/src/main/java/com/nbg/android/mcpserver`: Android MCP service boundary.

## Safety Hard Standards

These standards are release blockers:

- API keys and provider tokens must live in Android Keystore backed encrypted storage. Legacy plaintext preferences may be read only for one migration path and must be cleared after successful migration.
- Application backup must stay disabled while SharedPreferences may contain secrets or local service credentials.
- Dangerous file writes, deletes, command execution, external resource install, Skill mutation, MCP tool execution, and pet/external source install must pass the Permission Risk Model.
- Local HTTP, FTP, and MCP surfaces must have an explicit localhost/auth/trust story before public beta. Cleartext exceptions are allowed only for loopback and must remain documented.
- External Skills, PetDex resources, MCP bundles, and other installable sources require hash or signature validation before becoming trusted defaults.
- Build-time and runtime downloads must have checksum or signature verification before being treated as release-grade.
- Diagnostic export is user-triggered, local-first, and redacts secrets by default.
- Runtime patching must be version-gated, observable, and able to degrade without hiding the failure.
- Public beta must not ship with known P0/P1 issues in key loss, data loss, dangerous action confirmation bypass, build-loop blocking recovery, or session restoration.

## Permission Risk Model

Every tool action should map to one risk tier:

| Tier | Meaning | Default behavior |
| --- | --- | --- |
| Low | Read-only or reversible UI action | Allow and log status |
| Medium | Local state change with clear target | Lightweight confirmation or inline review |
| High | File write, Skill mutation, external resource install, MCP action with side effects | Explicit confirmation with target and risk |
| Dangerous | Delete, overwrite, shell command with destructive pattern, credential exposure risk | Strong confirmation and audit trail |

The UI must show target, action, risk, result, and recovery path for high and dangerous actions.

## Multi-Agent Workflow

Use an issue queue, not ad hoc work.

Roles:

- Coordinator Agent: roadmap, dependency tracking, issue shaping, weekly quality report.
- Android Agent: Compose UI, state holders, Android services, storage, Keystore, APK packaging.
- Terminal/Runtime Agent: proot Ubuntu, Node, terminal, build wrapper, Hanako launcher and patching.
- Agent Platform Agent: Skills, Memory, MCP, Hanako API/event contracts.
- Security Agent: secrets, permission model, resource verification, redaction, privacy.
- QA/Release Agent: unit tests, UI automation, manual scripts, release gates, release notes.
- UX Review Agent: tool cards, status language, mobile ergonomics, screenshots.
- Review Agent: independent second review before merge.

Every issue must declare:

- Goal
- Problem Frame
- Requirements
- Files/Areas
- Implementation Units
- Test Scenarios
- Risks
- Owner Agent
- Review Agent
- Security Review requirement
- Acceptance Criteria
- Merge Evidence

Merge evidence must include relevant test output, screenshots/log snippets for UI or runtime behavior, self-review notes, and reviewer notes.

## Planning Rules

Use `docs/plans/NBG_PLAN_TEMPLATE.md` for non-trivial work. A plan is required when a task:

- Changes security, local storage, permission policy, external resource trust, or diagnostics.
- Changes terminal/runtime launch, Hanako patching, session lifecycle, or public contracts.
- Adds or changes Skill, Memory, MCP, file/diff, or tool visualization semantics.
- Touches multiple modules or changes user-visible workflows.

Use `docs/contracts/NBG_CONTRACT_TEMPLATE.md` when behavior must stay stable across modules, app versions, or external integrations.

Initial required contracts:

- `android-hanako-event-contract`
- `android-mcp-server-contract`
- `skill-source-integrity-contract`
- `memory-context-contract`
- `tool-visualization-event-contract`
- `feedback-log-export-contract`

## Review Gates

Before merge:

- The implementation matches the plan or documents the deviation.
- Related unit tests, build tasks, and manual checks are recorded.
- UI changes include screenshots or screen recordings for relevant themes and small screens.
- Runtime changes include logs for success, failure, and recovery states.
- Security-sensitive changes have Security Agent review.
- The reviewer checks user behavior, not only code shape.

Before public beta:

- Real code-task loop passes on device: open project, Agent edits, user reviews diff, build runs, session recovers.
- API keys are encrypted and migration is covered.
- Terminal startup, Node availability, and Android build wrapper have a reliability baseline.
- Diagnostics export exists and is redacted.
- No P0/P1 blocker remains for secrets, data loss, dangerous operation confirmation, crash/ANR, or session recovery.

## Verification Commands

Common local commands:

```bash
./gradlew :app:assembleDebug
./gradlew test
./gradlew :terminal-core:testDebugUnitTest
./gradlew :app:testDebugUnitTest
```

Add narrower commands to each issue or plan when the touched module is known. Do not rely on one broad build command as the only acceptance proof for a behavior change.

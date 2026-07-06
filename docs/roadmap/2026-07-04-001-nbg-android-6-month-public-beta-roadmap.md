---
title: "NBG Android 6-month public beta roadmap"
status: active
date: 2026-07-04
type: roadmap
target_repo: /root/nbg-android
origin: "user 6-month commercial public beta plan"
owner_agent: "Coordinator Agent"
review_agent: "Review Agent"
security_review: "required"
---

# NBG Android 6-Month Public Beta Roadmap

## Summary

Move NBG Android from a working heavy mobile Agent workbench to a commercial-grade GitHub Releases public beta. The product direction is Android-native AI IDE first, mobile Agent platform second, and warm personal assistant throughout.

Mainline order:

1. IDE: terminal/build -> file/diff -> session/context.
2. Agent platform: Skills -> Memory -> MCP.
3. Experience: tool visualization -> stability confidence -> pet status assistant.

Non-negotiables:

- Local-first execution and data handling.
- No accounts, payments, cloud platform, enterprise backend, or app-store launch inside this roadmap.
- MCP and pet ecosystem can be marked beta; safety, data, stability, recovery, and release hygiene cannot.
- Hermes is used as a planning and engineering-discipline reference, not a runtime dependency.

## Final Acceptance Demo

- Open a project on the phone.
- Ask the Agent to complete a real code task.
- Review file preview and diff.
- Run a local build.
- Generate or update a Skill.
- Save and reuse Memory.
- Show MCP beta status and pet Agent status.
- Recover the session after app restart.
- Export a redacted feedback/diagnostic bundle.

## Month 1: Stability And Security Foundation

Goal: stop expanding technical debt and establish the hard standards needed for public beta.

P0 outcomes:

- Multi-Agent issue queue, plan template, contract template, review gates, and release gate exist in the repository.
- Capability Registry v1 plan exists and implementation starts.
- URL API keys move toward Android Keystore/EncryptedSharedPreferences.
- Runtime patching gets version gates, status diagnostics, and fallback behavior.
- Terminal/build reliability baseline is defined.
- `NbgAgentUi.kt` state concentration is reduced by incremental extraction only.
- Diagnostic export v1 is defined with redaction rules.
- UI automation skeleton is defined for startup, chat, terminal, Skills/MCP/settings.

Exit standard:

- Security hard standards have code entry points or active implementation issues.
- Terminal/build chain has a measurable baseline.
- Diagnostic export path is planned and scoped.
- UI automation has a first runnable smoke path or issue-ready implementation plan.

Detailed plan: `docs/plans/2026-07-04-001-stability-security-foundation-plan.md`.

Issue queue: `docs/issues/2026-07-04-001-m1-p0-issue-queue.md`.

## Month 2: IDE Core Loop

Goal: make NBG credible for real coding execution on a phone.

Focus:

- Terminal startup, failure recovery, background/foreground recovery, and long-output handling.
- Android build wrapper observability: start, phase, output, artifact path, and failure reason.
- Professional terminal tool cards: command, cwd, state, exit code, truncation, logs.
- Terminal session lifecycle: restart, close, unexpected disconnect, recovery prompt.
- Performance budget: streaming output must not freeze UI or grow unbounded memory.

Acceptance:

- A real project can run dependency checks and build commands on device.
- Build failure presents clear error and log entry points.
- Terminal page and chat tool cards use the same state language.
- UI automation covers terminal start, command execution, long output, and failure.

## Month 3: File / Diff And Tool Visualization

Goal: make Agent code changes trustworthy and reviewable.

Focus:

- File preview, diff, write confirmation, and risk prompts use one design language.
- Tool cards show input, output, result, risk, and expandable details.
- File writes show before/after comparison, path copy, and content copy.
- Large files, large diffs, truncation, and binary files have explicit states.
- UI remains professional and readable in light, black, and Claude themes.

Acceptance:

- Users can understand what changed, why it changed, and whether it succeeded.
- High-risk file actions trigger confirmation.
- Diff is readable across supported themes and small screens.
- UI automation covers file preview, diff expansion, approve, and reject.

## Month 4: Skills And Memory Governance

Goal: add self-improving Agent behavior without weakening local-first trust.

Skills:

- Skills page becomes a governance center: source, enabled state, pin/archive, usage count, recent use, risk state.
- External Skills require hash/signature validation before trusted default use.
- Skill lifecycle: active, pinned, archived, beta, blocked.
- Agent-generated or modified Skills show diff and reason before activation.
- Duplicate/outdated Skill suggestions are advisory, not automatic deletion.

Memory:

- Memory categories: `project_fact`, `user_preference`, `decision`, `handoff`, `bug_note`.
- Memory writes require explicit confirmation.
- Secrets, tokens, private data, and temporary process notes must not be silently stored.
- Use a frozen snapshot plus live state model so active sessions stay stable.
- Add Memory injection/leak scanning before content enters system context.

Acceptance:

- Skill install/update and Memory write are reviewable and reversible.
- Memory search, edit, delete, and export behavior is documented.
- Risky external resources are blocked or strongly warned by default.

## Month 5: MCP Beta, Pet Status Assistant, Release Preparation

Goal: connect platform features to real product state while preparing the public beta package.

Focus:

- Android MCP Server grows from sample tools to a beta capability layer: device info, app state, logs, file entry points, diagnostics entry points.
- MCP health diagnostics: connection state, tool count, failure reason, retry suggestion.
- Pet status reflects real Agent state: connected, thinking, tool running, waiting for confirmation, error, done.
- Feedback and log export are user-triggered and redacted by default.
- Release package templates exist: APK, checksum, privacy note, known issues, feedback path.

Acceptance:

- MCP beta status is useful without pretending to be final.
- Pet status helps users understand Agent state without stealing the work area.
- Release candidate checklist can be run by QA/Release Agent.

## Month 6: Public Beta Candidate And Release

Goal: publish the GitHub Releases public beta and support the first feedback window.

Focus:

- Full regression: code-task loop, Agent growth task, MCP beta, pet status, session recovery, diagnostics export.
- Fix P0/P1 blockers: crash, key storage, data loss, build-loop breakage, session recovery, confirmation bypass.
- Complete acceptance demo.
- Publish GitHub release: APK, checksum, release notes, privacy statement, quick start, feedback template.
- Two-week focused response window for install failure, terminal startup, model config, build failure, crash, and recovery issues.

Acceptance:

- `docs/release/NBG_PUBLIC_BETA_RELEASE_GATE.md` passes.
- No unresolved P0/P1 release blocker remains.
- Known issues are explicit and scoped.

## Multi-Agent Operating Model

Work is assigned through issues:

- Every implementation issue has owner Agent and reviewer Agent.
- Security-sensitive work requires Security Agent review.
- UI work requires screenshots for relevant themes and screen sizes.
- Runtime work requires logs for success, failure, and recovery.
- QA/Release Agent maintains weekly quality reports.
- Human owner keeps final product direction, high-risk approval, and release approval.

## Sources

- User-provided 6-month plan attachment.
- User-provided Hermes-style planning-system attachment.
- Local Hermes reference: `/tmp/hermes-agent` at commit `e02fc28`.
- Current NBG design baseline: `DESIGN.md`.

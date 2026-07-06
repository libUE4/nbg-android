---
title: "Complete Hermes reference Androidization status"
status: in_progress
date: 2026-07-06
type: implementation-status
target_repo: /root/nbg-android
origin: "User-provided complete Hermes reference implementation plan"
security_review: "required"
---

# Complete Hermes Reference Androidization Status

## Implemented Android-Native Surfaces

This repository keeps Hermes as a product and architecture reference. It does not import the Hermes Python runtime, gateway process, cloud deployment model, or desktop assumptions.

Implemented in the current Android architecture:

- Autonomous learning core: `NbgLearningCandidate`, `NbgLearningReview`, `NbgLearningEvent`, `NbgLearningSettings`, `NbgLearningAuditStore`, and `NbgAutonomousLearningEngine`.
- Memory/User/Soul layers: local learning Memory mirror, `NbgUserProfileStore`, and `NbgAgentSoulStore`, all using local persistence and diagnostics redaction.
- Learning UI: `NbgShellPage.Learning` renders audit events, learning graph stats, local Memory/Profile/Soul counts, Skill draft state, local cross-session Recall, Scheduled Automations, Gateway Inbox, and Trajectory Export status.
- Skill learning safety: `/learn` and natural-language learning produce reviewed Skill draft queue entries; generated Skills are not installed or enabled without review.
- Skill self-improvement foundation: ended tool results can produce `SkillImprovement` candidates from task evidence and terminal failure/success signals; candidates are audit-recorded and review-only.
- Cross-session Recall foundation: `NbgLearningRecallBundle` ranks local Memory, User Profile, Soul, Skill drafts, and redacted session-summary-index entries for the current query/session.
- Scheduled Automations foundation: `NbgScheduledAutomation`, `NbgSchedulePolicyReview`, `NbgScheduleRunEvent`, and `NbgScheduleStore`.
- Gateway Inbox foundation: `NbgInboundMessageSource`, `NbgGatewayInboxMessage`, `NbgGatewayPolicyReview`, and `NbgGatewayInboxStore`.
- Trajectory Export foundation: `NbgTrajectoryExportPolicyReview`, `NbgTrajectoryExportBundle`, redacted messages, redacted tool events, task evidence bundles, and learning events.
- Existing Hermes-adjacent features remain wired: Memory UI, Skills UI, Skill Curator, Agents Team task panel, Expert Review, MCP connector governance, tool evidence cards, diagnostics export, and unified verification script.

## Complete Hermes Reference Map

| Hermes Agent capability | Android reference decision | Current status |
| --- | --- | --- |
| CLI/TUI with streaming tool output | Native Compose chat + tool cards + terminal previews | Implemented in Android-native UI |
| Multi-provider model switching | URL API model/provider configuration | Implemented |
| Toolsets and Doctor diagnostics | Toolsets / Doctor page with capability health | Implemented |
| Persistent Memory | Local Memory UI + local learning Memory mirror | Implemented |
| User profile / Soul | `NbgUserProfileStore` + `NbgAgentSoulStore` | Implemented |
| Autonomous learning loop | Natural language markers, `/learn`, audit log, policy review | Implemented |
| Skills from experience | Learned Skill draft queue, evidence/hash/path/risk review | Implemented review-only |
| Skills improve during use | Tool-result-driven SkillImprovement candidates | Implemented review-only |
| Learning graph / journey | Memory/Profile/Soul/Skill graph with linked/isolated stats | Implemented v1 |
| Past session search | Local redacted `session-summary-index.json` + remote merge | Implemented |
| Cross-session recall | Local Recall ranks Memory/Profile/Soul/Skill/session hits | Implemented v1 |
| Cron scheduling | Local Scheduled Automations model and run audit | Implemented foundation |
| Messaging gateway | Gateway Inbox model and policy gate | Implemented foundation |
| Trajectory export | Local-only redacted export bundle | Implemented foundation |
| Multi-agent delegation | Agents Team task panel and bounded policy | Implemented v1 |
| Expert review / MoA | URL API multi-model review, opt-in read-only | Implemented |
| MCP integration | Android MCP server contract and connector governance | Implemented foundation |
| Pet visual state | Bundled pet store, integrity, pixel pet UI | Implemented |
| Runtime environments: Docker/SSH/Modal/Daytona/Singularity | Not copied into APK; Android terminal/proot is the runtime boundary | Deferred / Android-specific |
| Telegram/Discord/Slack/WhatsApp/Signal bots | Not hardcoded; external messages go through Gateway Inbox policy | Deferred |
| Honcho dialectic user model | Not bundled; local User Profile/Soul is the Android substitute | Deferred |
| Cloud browser / voice / image / media gateways | MCP/tool gateway category mapping only; no default remote accounts | Deferred |

## Safety Boundaries

- No Hermes runtime is bundled.
- No Telegram, Discord, Slack, WhatsApp, or Signal account logic is hardcoded into Android core.
- No non-loopback network gateway is opened by default.
- Sensitive values, keys, tokens, passwords, private keys, raw paths, and raw tool output are blocked or redacted before persistence/export.
- Dangerous automation and external messages do not receive automatic tool execution permission.
- Skill install/enable remains review-gated.

## Remaining Product Work

The current increment establishes stable local models, policy gates, persistence, tests, and Learning-page visibility. Deeper follow-up work remains:

- WorkManager-backed real schedule execution with Android foreground-service constraints.
- Share sheet and notification-reply Activity/Receiver entry points that call `NbgGatewayInboxStore`.
- MCP connector category badges for Web Search, Browser, Vision, Image, Speech, and Media.
- Promotion flow for SkillImprovement drafts: show diff against existing Skill, let user merge/reject, and keep rollback metadata.
- User-facing export/share action for the generated trajectory JSON bundle.
- More granular rollback for applied learning events beyond audit status changes.

## Verification

Targeted verification:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgAutonomousLearningEngineTest
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgScheduleGatewayTrajectoryTest
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.AndroidManifestBehaviorTest.learningPageExposesHermesStyleRecallLoop
```

Expected result: `BUILD SUCCESSFUL`.

Full local gate:

```bash
scripts/nbg_test.sh
```

Expected result: `BUILD SUCCESSFUL`.

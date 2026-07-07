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
- Memory provider manager v2: `NbgMemoryProviderManager` provides Hermes-style `prefetchAll`, `syncAll`, and `queuePrefetchAll` around Android local Memory/Profile/Soul/Skill/session recall, while `NbgExternalMemoryProviderStore` exposes opt-in Honcho/mem0/supermemory/holographic provider configuration without enabling third-party memory accounts by default.
- Closed learning loop: Android now buffers the current user turn plus final assistant-visible answer, commits the full trajectory at turn end, and injects ranked `learningContext` recall into the next prompt `uiContext`.
- Memory/User/Soul layers: local learning Memory mirror, `NbgUserProfileStore`, and `NbgAgentSoulStore`, all using local persistence and diagnostics redaction.
- Learning UI: `NbgShellPage.Learning` renders audit events, learning graph stats, local Memory/Profile/Soul counts, Skill draft state, local cross-session Recall, external Memory provider plugins, Context Insights, Scheduled Automations, Gateway Inbox, and Trajectory Export status.
- Skill learning safety: `/learn` and natural-language learning produce reviewed Skill draft queue entries; generated Skills are policy-reviewed and record local rollback metadata when auto-applied.
- SkillManage v2: Android-local `NbgSkillManage` supports create/edit/patch/write_file/remove_file/delete for learned Skill artifacts inside app-private `learned-skills`, with rollback backups, path containment, and `NbgSkillDiffMerge` hunk previews / selective merge / rollback comparison UI on the Skills page.
- Skill self-improvement foundation: ended tool results can produce `SkillImprovement` candidates from task evidence and terminal failure/success signals; Low/Medium candidates can auto-apply into local Skill artifacts with previous-artifact hash/backup metadata, while High/Dangerous remain review-gated or blocked. Applied artifacts are now reviewable through the diff/merge card.
- Skill Curator loop v2: `NbgSkillCuratorLoopWorker` can run a user-enabled WorkManager background curator pass, preserving the default no-cloud/no-auto-delete boundary while archiving only locally safe learned Skill views.
- Model Provider Profiles v1: `NbgModelProviderProfile` registers OpenRouter/OpenAI/Gemini/Qwen/Kimi/Nous/Ollama style provider metadata for auth/model/extra-body boundaries, and the URL API page surfaces the profile registry plus detected saved URL API entries.
- Context maintenance v1: Android now tracks `NbgContextInsights`, synchronizes context/token usage events, exposes `/compress`, `/usage`, and `/insights` local slash commands, and renders a Context Insights card in Learning.
- Learning Journey v1: Learning graph nodes can be edited/deleted from Android-local Memory/Profile/Soul/Skill draft/installed learned Skill stores.
- Cross-session Recall foundation: `NbgLearningRecallBundle` ranks local Memory, User Profile, Soul, Skill drafts, installed learned Skill metadata, and redacted session-summary-index entries for the current query/session.
- Scheduled Automations v1: `NbgScheduledAutomation`, `NbgSchedulePolicyReview`, `NbgScheduleRunEvent`, `NbgScheduleStore`, WorkManager periodic scanning, and a local-only runner that produces read-only report evidence while blocking confirmation-only shell work.
- Gateway Inbox v1: `NbgInboundMessageSource`, `NbgGatewayInboxMessage`, `NbgGatewayPolicyReview`, `NbgGatewayInboxStore`, Android share-sheet ingestion, and local notification-reply ingestion into the Learning page.
- Trajectory Export v1: `NbgTrajectoryExportPolicyReview`, `NbgTrajectoryExportBundle`, redacted messages, redacted tool events, task evidence bundles, learning events, and user-triggered local JSON sharing.
- Existing Hermes-adjacent features remain wired: Memory UI, Skills UI, Skill Curator, Agents Team task panel, Expert Review, MCP connector governance with tool-category badges, tool evidence cards, diagnostics export, and unified verification script.

## Complete Hermes Reference Map

| Hermes Agent capability | Android reference decision | Current status |
| --- | --- | --- |
| CLI/TUI with streaming tool output | Native Compose chat + tool cards + terminal previews | Implemented in Android-native UI |
| Multi-provider model switching | URL API model/provider configuration | Implemented |
| Toolsets and Doctor diagnostics | Toolsets / Doctor page with capability health | Implemented |
| Persistent Memory | Local Memory UI + local learning Memory mirror + Android MemoryProvider manager v2 + opt-in external provider plugin configs | Implemented v2 |
| User profile / Soul | `NbgUserProfileStore` + `NbgAgentSoulStore` | Implemented |
| Autonomous learning loop | Natural language markers, `/learn`, full-turn completion memory, audit log, policy review, prompt-time learning recall injection | Implemented |
| Skills from experience | Learned Skill draft queue, evidence/hash/path/risk review, Low/Medium auto-apply, rollback metadata, local SkillManage operations, diff/merge/rollback UI | Implemented v2 |
| Skills improve during use | Tool-result-driven SkillImprovement candidates with Low/Medium auto-apply, rollback metadata, local patch/write-file support, hunk diff review | Implemented v2 |
| Learning graph / journey | Memory/Profile/Soul/Skill graph, editable/deletable Journey nodes, installed Skill related_skills links | Implemented v1 |
| Past session search | Local redacted `session-summary-index.json` + remote merge | Implemented |
| Cross-session recall | Local Recall ranks Memory/Profile/Soul/Skill/session hits | Implemented v1 |
| Cron scheduling | WorkManager-backed local Scheduled Automations, run audit, and local-only read report runner | Implemented v1 |
| Messaging gateway | Gateway Inbox model, policy gate, Android share-sheet ingestion, and notification-reply ingestion | Implemented v1 |
| Trajectory export | Local-only redacted export bundle with user-triggered JSON share | Implemented v1 |
| Multi-agent delegation | Agents Team task panel and bounded policy | Implemented v1 |
| Expert review / MoA | URL API multi-model review, opt-in read-only | Implemented |
| ProviderProfile model plugins | Android `NbgModelProviderProfile` registry for provider metadata and saved URL API detection | Implemented v1 |
| Context compression / usage / insights | Local slash commands, usage snapshots, Learning-page Context Insights, existing compress-fork bridge | Implemented v1 |
| MCP integration | Android MCP server contract, connector governance, and tool-category badges | Implemented v1 |
| Pet visual state | Bundled pet store, integrity, pixel pet UI | Implemented |
| Runtime environments: Docker/SSH/Modal/Daytona/Singularity | Not copied into APK; Android terminal/proot is the runtime boundary | Deferred / Android-specific |
| Telegram/Discord/Slack/WhatsApp/Signal bots | Not hardcoded; external messages go through Gateway Inbox policy | Deferred |
| Honcho dialectic user model | Not bundled by default; external memory provider plugin config exists but real account sync remains opt-in | Deferred / opt-in boundary |
| Cloud browser / voice / image / media gateways | MCP/tool gateway category mapping only; no default remote accounts | Deferred |

## Safety Boundaries

- No Hermes runtime is bundled.
- No Telegram, Discord, Slack, WhatsApp, or Signal account logic is hardcoded into Android core.
- No non-loopback network gateway is opened by default.
- Sensitive values, keys, tokens, passwords, private keys, raw paths, and raw tool output are blocked or redacted before persistence/export.
- Dangerous automation and external messages do not receive automatic tool execution permission.
- Skill install/enable remains review-gated.

## Remaining Product Work

The current increment establishes stable local models, policy gates, persistence, tests, and Learning/Skills/URL API page visibility. Deeper follow-up work remains:

- Real API adapters for optional third-party memory providers such as Honcho, mem0, or supermemory. Android now has provider plugin configuration, but no external account is enabled by default.
- Full LLM-authored background review fork equivalent to Hermes desktop. Android now has a user-enabled WorkManager curator loop; remote LLM review remains opt-in future work tied to explicit model/provider configuration.
- Deeper visual diff ergonomics such as side-by-side file panes and multi-file merge sessions. Android now has hunk preview, selective hunk merge, and rollback comparison for local learned Skill artifacts.

## Verification

Targeted verification:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgAutonomousLearningEngineTest
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgScheduleGatewayTrajectoryTest
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.AndroidManifestBehaviorTest.learningPageExposesHermesStyleRecallLoop
./gradlew --no-daemon :app:testDebugUnitTest --tests com.nbg.android.NbgHermesAdvancedParityTest
```

Expected result: `BUILD SUCCESSFUL`.

Full local gate:

```bash
scripts/nbg_test.sh
```

Expected result: `BUILD SUCCESSFUL`.

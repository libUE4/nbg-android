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
- Memory provider manager v3: `NbgMemoryProviderManager` provides Hermes-style `prefetchAll`, `syncAll`, and `queuePrefetchAll` around Android local Memory/Profile/Soul/Skill/session recall, while `NbgExternalMemoryAdapter` can opt-in sync/prefetch Honcho, mem0, supermemory, or holographic providers using encrypted per-provider secrets. No third-party account is enabled by default.
- Closed learning loop: Android now buffers the current user turn plus final assistant-visible answer, commits the full trajectory at turn end, and injects ranked `learningContext` recall into the next prompt `uiContext`.
- Memory/User/Soul layers: local learning Memory mirror, `NbgUserProfileStore`, and `NbgAgentSoulStore`, all using local persistence and diagnostics redaction.
- Learning UI: `NbgShellPage.Learning` renders audit events, learning graph stats, local Memory/Profile/Soul counts, Skill draft state, local cross-session Recall, external Memory provider plugins, Context Insights, compression strategy controls, SQLite-backed local FTS results with highlighting/filtering/jump actions, provider/model token-cost breakdowns, Scheduled Automations, Gateway Inbox, and Trajectory Export status.
- Skill learning safety: `/learn` and natural-language learning produce reviewed Skill draft queue entries; generated Skills are policy-reviewed and record local rollback metadata when auto-applied.
- SkillManage v4: Android-local `NbgSkillManage` supports create/edit/patch/write_file/remove_file/delete for learned Skill artifacts inside app-private `learned-skills`, with rollback backups, path containment, multi-file `NbgSkillDiffMerge` hunk previews, conflict warnings for stale patch drafts, selective merge, and rollback comparison UI on the Skills page.
- Skill self-improvement foundation: ended tool results can produce `SkillImprovement` candidates from task evidence and terminal failure/success signals; Low/Medium candidates can auto-apply into local Skill artifacts with previous-artifact hash/backup metadata, while High/Dangerous remain review-gated or blocked. Applied artifacts are now reviewable through the diff/merge card.
- Skill Curator loop v5: `NbgSkillCuratorLoopWorker` can run a user-enabled WorkManager background curator pass and an opt-in read-only LLM curator pass through the configured URL API model. LLM results are persisted into `NbgSkillCuratorSuggestionQueue` for explicit accept/ignore handling, and merge/rewrite suggestions can carry a patch draft that opens in the existing Skill diff/merge review UI.
- Model Provider Profiles v2: `NbgModelProviderProfile` registers OpenRouter/OpenAI/Gemini/Qwen/Kimi/Nous/Ollama style provider metadata and routes model fetch, verification, read-only generation, auth header, thinking/extra-body, and chat/model endpoints through provider profiles.
- Context maintenance v3: Android tracks `NbgContextInsights`, synchronizes context/token usage events, estimates token/cost totals by provider/model/session, exposes slash commands `/compress`, `/usage`, `/insights`, `/search`, `/cost`, `/memory`, `/provider`, `/model`, `/skills`, `/tools`, `/agents`, and `/curator`, and can remind or auto-compress when context usage crosses the configured threshold.
- Learning Journey v1: Learning graph nodes can be edited/deleted from Android-local Memory/Profile/Soul/Skill draft/installed learned Skill stores.
- Cross-session Recall foundation: `NbgLearningRecallBundle` ranks local Memory, User Profile, Soul, Skill drafts, installed learned Skill metadata, and redacted session-summary-index entries for the current query/session.
- Scheduled Automations v1: `NbgScheduledAutomation`, `NbgSchedulePolicyReview`, `NbgScheduleRunEvent`, `NbgScheduleStore`, WorkManager periodic scanning, and a local-only runner that produces read-only report evidence while blocking confirmation-only shell work.
- Gateway Inbox v1: `NbgInboundMessageSource`, `NbgGatewayInboxMessage`, `NbgGatewayPolicyReview`, `NbgGatewayInboxStore`, Android share-sheet ingestion, and local notification-reply ingestion into the Learning page.
- Trajectory Export v1: `NbgTrajectoryExportPolicyReview`, `NbgTrajectoryExportBundle`, redacted messages, redacted tool events, task evidence bundles, learning events, and user-triggered local JSON sharing.
- Existing Hermes-adjacent features remain wired: Memory UI, Skills UI, Skill Curator, Agents Team task panel with parallel budgets/retry labels, Expert Review, MCP connector governance with tool-category badges, tool evidence cards, diagnostics export, and unified verification script.

## Complete Hermes Reference Map

| Hermes Agent capability | Android reference decision | Current status |
| --- | --- | --- |
| CLI/TUI with streaming tool output | Native Compose chat + tool cards + terminal previews | Implemented in Android-native UI |
| Multi-provider model switching | URL API model/provider configuration | Implemented |
| Toolsets and Doctor diagnostics | Toolsets / Doctor page with capability health | Implemented |
| Persistent Memory | Local Memory UI + local learning Memory mirror + Android MemoryProvider manager v3 + opt-in external provider adapters for Honcho/mem0/supermemory/holographic | Implemented v3 |
| User profile / Soul | `NbgUserProfileStore` + `NbgAgentSoulStore` | Implemented |
| Autonomous learning loop | Natural language markers, `/learn`, full-turn completion memory, audit log, policy review, prompt-time learning recall injection | Implemented |
| Skills from experience | Learned Skill draft queue, evidence/hash/path/risk review, Low/Medium auto-apply, rollback metadata, local SkillManage operations, multi-file diff/merge/rollback UI with conflict warnings | Implemented v4 |
| Skills improve during use | Tool-result-driven SkillImprovement candidates with Low/Medium auto-apply, rollback metadata, local patch/write-file support, multi-file hunk diff review | Implemented v3 |
| Learning graph / journey | Memory/Profile/Soul/Skill graph, editable/deletable Journey nodes, installed Skill related_skills links | Implemented v1 |
| Past session search | Local redacted `session-summary-index.json` + cached-message SQLite FTS5 index for sessions, Memory, Skills, and Skill drafts + highlighted/filterable/jumpable Learning-page results + remote merge fallback | Implemented v5 |
| Cross-session recall | Local Recall ranks Memory/Profile/Soul/Skill/session hits | Implemented v1 |
| Cron scheduling | WorkManager-backed local Scheduled Automations, run audit, and local-only read report runner | Implemented v1 |
| Messaging gateway | Gateway Inbox model, policy gate, Android share-sheet ingestion, and notification-reply ingestion | Implemented v1 |
| Trajectory export | Local-only redacted export bundle with user-triggered JSON share | Implemented v1 |
| Multi-agent delegation | Agents Team task panel, bounded policy, parallel budget/retry/isolation/consolidation labels | Implemented v2 |
| Expert review / MoA | URL API multi-model review, opt-in read-only | Implemented |
| ProviderProfile model plugins | Android `NbgModelProviderProfile` registry and true provider-profile routing for endpoint/auth/extra-body/model verification | Implemented v2 |
| Context compression / usage / insights | Local slash command center, usage snapshots, auto/remind compression strategy, provider/model token-cost breakdowns, Learning-page Context Insights, existing compress-fork bridge | Implemented v3 |
| MCP integration | Android MCP server contract, connector governance, and tool-category badges | Implemented v1 |
| Pet visual state | Bundled pet store, integrity, pixel pet UI | Implemented |
| Runtime environments: Docker/SSH/Modal/Daytona/Singularity | Not copied into APK; Android terminal/proot is the runtime boundary | Deferred / Android-specific |
| Telegram/Discord/Slack/WhatsApp/Signal bots | Not hardcoded; external messages go through Gateway Inbox policy | Deferred |
| Honcho dialectic user model | Not bundled by default; real external memory adapters exist for opt-in provider accounts | Implemented opt-in boundary |
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

- Provider-specific external Memory account UX can still be refined after real-world Honcho/mem0/supermemory endpoint testing. The adapter boundary and encrypted secret flow are implemented, but no third-party account is enabled by default.
- LLM curator suggestions are now persisted into an explicit accept/ignore queue. Accepting archive suggestions applies only local curator archive metadata; merge/rewrite suggestions can preview a concrete patch draft, but the user still applies changes through the Skill diff/merge UI.
- Deeper visual diff ergonomics such as side-by-side file panes and long-running multi-file merge sessions remain future UI polish. Android now has multi-file hunk preview, stale patch conflict warnings, selective hunk merge, and rollback comparison for local learned Skill artifacts.

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

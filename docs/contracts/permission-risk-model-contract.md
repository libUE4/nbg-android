---
title: "Permission risk model contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["Security Agent", "Agent Platform Agent", "UX Review Agent"]
review_agent: "Review Agent"
security_review: "required"
---

# Permission Risk Model Contract

## Status

Active for Month 1 public beta hardening.

## Purpose

NBG Android must not default to silently high-risk Agent behavior. Users should start in an ask-first mode, and side-effect actions must map to explicit risk tiers before they are confirmed or executed.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
| Chat preferences | Persist default and selected permission mode. | `NbgChatPreferenceStore.kt` |
| Hanako session state | Reflect active backend permission mode and fallback safely when backend omits it. | `HanakoBridge.kt`, `HanakoApiClient.kt`, `HanakoChatController.kt` |
| Bundled Hanako runtime | Owns approval generation for side-effect tool execution and emits `tool_action_approval` / `session_confirmation` requests in ask mode. | `hanako-server-linux-arm64-node22.nbgpack` |
| Risk model | Classify tool/action risk and confirmation requirements. | `NbgPermissionRiskModel.kt` |
| Confirmation UI | Show risk label, target, and recovery hint before approval. | `NbgAgentChatUi.kt` |
| Runtime defaults | Avoid disabling external Skill safety review by default. | `HanakoApiClient.kt`, `HanakoServerLauncher.kt` |

## Mode Semantics

| Mode | Label | Meaning |
| --- | --- | --- |
| `ask` | 先问 | Default. Medium and higher side-effect tools should request confirmation before side effects. |
| `read_only` | 计划 | Analyze and plan without write or side-effect operations. |
| `operate` | 操作 | User-selected higher-autonomy mode. Must not be the default fallback and requires session-level warning acceptance before first use. |

## Risk Tiers

| Tier | Requires Confirmation | Strong Confirmation | Examples |
| --- | --- | --- | --- |
| Low | No | No | Read, list, search, view, inspect, echo, device info. |
| Medium | Yes | No | Terminal/shell intent without destructive pattern, unknown side-effect intent. |
| High | Yes | No | File write/edit/patch/rename, Skill install/update, Memory save, MCP tool call, PetDex install, plugin/external resource install. |
| Dangerous | Yes | Yes | Delete/remove, destructive shell command, `rm -rf`, disk wipe/reset patterns. |

## Rules

- New local preference fallback is `ask`.
- Hanako session/focus fallback is `ask` when backend omits permission mode.
- Invalid permission mode normalizes to `ask`.
- `operate` may still be selected explicitly by the user, but the first use in each active session requires a session-level warning acceptance.
- A stored `operate` preference must not auto-apply to a new or restored session until the session-level warning is accepted; until then Android displays and syncs `ask`.
- External Skill learning safety review defaults to enabled.
- Confirmation cards should include risk label, target detail when available, and a recovery hint for High/Dangerous actions.
- Side-effect MCP tools added later require this contract to be revised with auth and confirmation details.
- Agent-initiated PetDex, plugin, Skill, MCP, or other external resource installs are High risk or above and must not bypass confirmation.
- Medium terminal/shell/command intents require ordinary confirmation by default for public beta, but do not require strong confirmation unless a Dangerous pattern is detected.
- Backend-declared `riskTier`/`risk` values may raise risk but must never lower Android's local inference; destructive shell patterns remain Dangerous and external resource/MCP side effects remain High even when declared as Low or Medium.
- Confirmation events may carry a backend risk schema under `risk`, `riskSchema`, or `permissionRisk`; Android supports `tier`/`riskTier`/`level`/`risk`, `severity`, `action`, `toolName`/`tool`/`name`, `command`, `target`/`targetLabel`/`path`/`url`/`resource`, `reason`/`summary`/`description`, and `recoveryHint`/`recovery`/`mitigation`/`hint` fields.
- Backend risk schema is advisory for UI detail and may raise risk, but Android local inference remains the minimum accepted tier and schema values must never downgrade local inference.
- Schema action/tool/command/reason fields are classified as a separate raise-only candidate; they must not lower Android's conservative default for sparse confirmation blocks.
- Backend recovery hints are ignored when their declared/schema risk is lower than Android's local inference, so weak backend hints cannot replace stronger local High/Dangerous guidance.
- Android local inference must include top-level action/target/command fields plus `subject.label`, `subject.detail`, `payload.toolName`, and relevant `payload.params` values such as `command`, `url`, `path`, `file_path`, `action`, `target`, `label`, and `key`.
- Bundled Hanako runtime owns approval generation: in `ask` mode it must prompt with `tool_action_approval` / `session_confirmation` before side-effect tools such as `bash`, `terminal_create`, `terminal_write`, `terminal_interrupt`, and `terminal_kill` execute. Android parses those events, applies the local risk model, and may only raise risk.

## Test Oracle

Unit/source tests:

- `HanakoBridgeTest.permissionDefaultsStartInAskModeInsteadOfOperate`
- `HanakoBridgeTest.permissionRiskModelClassifiesSideEffects`
- `HanakoBridgeTest.mediumTerminalCommandsRequireConfirmationWithoutStrongConfirmation`
- `HanakoBridgeTest.declaredRiskCannotDowngradeDestructiveTerminalCommand`
- `HanakoBridgeTest.declaredRiskCannotDowngradeExternalResourceOrMcpSideEffects`
- `HanakoBridgeTest.payloadRiskCannotDowngradeBundledConfirmationShape`
- `HanakoBridgeTest.backendRiskSchemaCanRaiseRiskAndProvideRecoveryHint`
- `HanakoBridgeTest.backendRiskSchemaCannotDowngradeLocalInference`
- `HanakoBridgeTest.confirmationParserAddsRiskAndRecoveryHints`
- `NbgAgentConfirmationStateTest.operatePermissionWarningGatesOperatePerSessionUntilAccepted`
- `NbgAgentConfirmationStateTest.operatePermissionWarningDismissalIsTrackedPerSession`
- `NbgPetResourceIntegrityTest.petDexInstallReviewAllowsHttpsAssetsAndMarksUnverifiedPetDex`
- `AndroidManifestBehaviorTest.permissionRiskModelContractIsDocumentedAndWired`
- `AndroidManifestBehaviorTest.chatPreferenceStorePersistsModelPermissionAndThinkingChoices`
- `AndroidManifestBehaviorTest.agentChatKeepsCoreHanakoProtocolAndDropsDeferredAdvancedFeatures`

Commands:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :terminal-core:testDebugUnitTest :android-mcp-server:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :android-mcp-server:assembleDebug
```

## Open Questions

- None for the Month 1 public-beta permission risk model entry point.

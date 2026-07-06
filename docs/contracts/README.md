# NBG Contract Index

This directory holds stable contracts for behavior that crosses module, runtime, or trust boundaries. Use `NBG_CONTRACT_TEMPLATE.md` before changing any behavior that other modules, agents, or releases depend on.

## First Contract Backlog

| Contract | Priority | Why It Exists | Primary Areas |
| --- | --- | --- | --- |
| `android-hanako-event-contract` | P0 active | Stabilize chat, streaming, tool events, confirmation, and recovery semantics between Android and Hanako. | `NbgHanakoEventContract.kt`, `HanakoApiClient.kt`, `HanakoBridge.kt`, `HanakoChatController.kt`, `HanakoRealtimeToolEvents.kt` |
| `android-mcp-server-contract` | P1 active | Keep the standalone MCP server beta predictable and auditable. | `AndroidMcpServerContract.kt`, `android-mcp-server/src/main/java/com/nbg/android/mcpserver` |
| `skill-source-integrity-contract` | P0 active | Prevent untrusted Skills or external resources from becoming trusted defaults without hash/signature review. | `NbgSkillSourceIntegrity.kt`, `HanakoSkillInstallSupport.kt`, `HanakoSkillsModels.kt`, `NbgAgentSkillsUi.kt`, bundled skill assets |
| `memory-context-contract` | P0 active | Define what can be stored, injected, edited, deleted, exported, and redacted. | `NbgMemoryContextPolicy.kt`, `HanakoMemoryModels.kt`, `NbgAgentMemoryUi.kt`, chat/session code |
| `tool-visualization-event-contract` | P1 active | Make terminal, file, diff, todo, team/agent, thinking, and confirmation cards use one status language. | `NbgToolVisualizationEventContract.kt`, `NbgToolPresentation.kt`, `NbgAgentMessageUi.kt`, `HanakoToolParsing.kt` |
| `pet-resource-integrity-contract` | P0 active | Keep bundled pets trusted and PetDex beta resources bounded, provenance-recorded, and redacted. | `NbgPetResourceIntegrity.kt`, `NbgPetStore.kt`, `NbgPetDexClient.kt`, `NbgPetDexPreviewStore.kt`, bundled pet assets |
| `feedback-log-export-contract` | covered by `diagnostics-export-contract` | Ensure diagnostics are useful, local-first, and redacted. | launcher logs, capability status, export UI |
| `diagnostics-export-contract` | P0 | Defines the shipped v1 local redacted diagnostics export schema, privacy boundary, and test oracle. | `NbgDiagnosticsExport.kt`, `NbgDiagnosticsExportUi.kt`, `NbgAgentUi.kt` |
| `local-service-boundary-contract` | P0 | Keep FTP, Hanako loopback, and Android MCP beta local-only and auditable. | `NbgFtpFileServer.kt`, `HanakoBridge.kt`, `HanakoServerLauncher.kt`, `android-mcp-server/src/main` |
| `permission-risk-model-contract` | P0 | Make ask-first default, risk tiers, and confirmation UI behavior explicit. | `NbgPermissionRiskModel.kt`, `NbgChatPreferenceStore.kt`, `HanakoBridge.kt`, `NbgAgentChatUi.kt` |
| `runtime-patch-gate-contract` | P0 | Keep bundled HanakoPro runtime patches version-gated, diagnosable, and recoverable. | `HanakoServerLauncher.kt`, bundled HanakoPro pack marker |
| `capability-registry-contract` | P0 | Standardize health/status/error semantics for Chat, drawer/settings, and diagnostics. | `NbgCapabilityRegistry.kt`, `NbgAgentDrawerUi.kt`, capability pages |

## Contract Rules

- Contracts are reviewed before implementation when the behavior crosses a trust boundary.
- Contract changes must include compatibility notes and test oracles.
- Security-sensitive contracts require Security Agent review.
- A release cannot claim public beta quality if a P0 contract is missing for a shipped P0 workflow.

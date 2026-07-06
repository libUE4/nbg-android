---
title: "Memory context contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["Android Agent", "Security Agent", "Runtime Agent"]
review_agent: "Security Agent"
security_review: "required"
---

# Memory Context Contract

## Status

Active for public-beta hardening.

Audience:

- Android implementation agents
- Hanako runtime integration agents
- Security reviewers
- QA agents validating Memory behavior

## Purpose

Define what NBG Android may store as Memory, how Memory can enter agent context, how users can edit/delete it, and what diagnostics may expose.

This contract prevents:

- Silent storage of secrets, tokens, passwords, private keys, or temporary process notes.
- Memory content leaking through diagnostics, logs, capability errors, or support bundles.
- Unknown Memory types entering the context path with ambiguous semantics.
- Future Agent self-improvement features writing durable memory without explicit review.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
| Memory UI | User-triggered search, add, edit, and delete. | `NbgAgentMemoryUi.kt` |
| Chat controller | Android-side validation before save/update/delete API calls. | `HanakoChatController.kt` |
| Memory model | Stable item/input types, type normalization, labels. | `HanakoMemoryModels.kt` |
| Context policy | Sensitive scan, size limit, type policy, context-injection eligibility. | `NbgMemoryContextPolicy.kt` |
| Hanako API client | Memory state/save/update/delete endpoints. | `HanakoApiClient.kt` |
| Hanako Memory plugin | Runtime store and `memory:context` injection source. | bundled `plugins/memory` |
| Runtime patch gate | Android-specific backend Memory policy patch for the bundled/source Hanako runtime. | `HanakoServerLauncher.kt` |
| Diagnostics export | Redacted aggregate counts only. | `NbgDiagnosticsExport.kt` |
| Memory export policy | v1 export gate and future evidence requirements. | `NbgMemoryContextPolicy.kt` |

## Trust Boundary

Memory crosses these boundaries:

- User intent boundary: durable memory must be created through explicit user action or a confirmed Agent tool call.
- Android to Hanako boundary: Memory writes go from Android UI/controller into Hanako local HTTP endpoints.
- Runtime context boundary: enabled Memory can be injected into Agent context by the Memory plugin.
- Privacy boundary: Memory content may contain project facts, preferences, decisions, or handoff summaries and must not leave the device through diagnostics by default.
- Agent boundary: Agent-suggested Memory is advisory until the user confirms save.

## Data Structures

Allowed Memory types:

- `project_fact`
- `user_preference`
- `decision`
- `handoff`
- `bug_note`

Android-side review:

```kotlin
data class NbgMemoryContextReview(
  val normalizedType: String,
  val risk: NbgMemoryContextRisk,
  val allowSave: Boolean,
  val allowContextInjection: Boolean,
  val requiresExplicitConfirmation: Boolean,
  val findingCount: Int,
  val userMessage: String,
)
```

Allowed risks:

- `low`
- `blocked_sensitive`
- `blocked_too_large`
- `blocked_empty`

Diagnostics shape:

```json
{
  "memory": {
    "count": 0,
    "enabledCount": 0,
    "loadedItemCount": 0,
    "contextPolicyVersion": "nbg-memory-context-v1",
    "injectableLoadedCount": 0,
    "blockedSensitiveLoadedCount": 0,
    "blockedTooLargeLoadedCount": 0,
    "auditEventCount": 0,
    "recentAuditEvents": []
  }
}
```

Local audit events without raw Memory content:

```kotlin
data class HanakoMemoryAuditEvent(
  val action: String,
  val result: String,
  val normalizedType: String,
  val risk: String,
  val policyVersion: String,
  val timestampMillis: Long,
)
```

Memory export policy:

```kotlin
const val NBG_MEMORY_EXPORT_POLICY_VERSION = "nbg-memory-export-v1"
val NBG_MEMORY_EXPORT_REQUIRED_EVIDENCE = listOf(
  "explicit_user_trigger",
  "per_item_selection",
  "sensitive_scan_passed",
)

data class NbgMemoryExportPolicyReview(
  val policyVersion: String,
  val allowExport: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val selectedItemCount: Int,
  val reason: String,
)
```

Public-beta v1 does not expose a separate Memory export feature. Future Memory export requires all three evidence labels: explicit user trigger, per-item selection, and a passing sensitive-content scan. Even when all future evidence labels are present, Android v1 must return `allowExport=false`.

Upstream policy adoption gate:

```kotlin
const val NBG_MEMORY_UPSTREAM_POLICY_ADOPTION_VERSION = "nbg-memory-upstream-policy-adoption-v1"
val NBG_MEMORY_UPSTREAM_POLICY_REQUIRED_EVIDENCE = listOf(
  "upstream_policy_version_match",
  "android_patch_marker_match",
  "parity_tests_passed",
)

data class NbgMemoryUpstreamPolicyAdoptionReview(
  val policyVersion: String,
  val mayRemoveAndroidRuntimePatch: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val reason: String,
)
```

Public-beta v1 keeps the Android Memory runtime patch gate. Future upstream adoption may mirror this policy in the bundled Hanako Memory plugin, but Android must not remove its patch gate until policy version parity, patch marker compatibility, and parity tests are proven and reviewed.

## State Machine

| State | Entered When | Allowed Transitions | Terminal? |
| --- | --- | --- | --- |
| `Draft` | User or Agent proposes Memory content. | `ReviewPending`, `Discarded` | No |
| `ReviewPending` | Content is ready for explicit user confirmation. | `Rejected`, `PolicyBlocked`, `SavedEnabled`, `SavedDisabled` | No |
| `PolicyBlocked` | Android detects empty, oversized, or sensitive content. | User edits content and returns to `Draft`. | No |
| `SavedEnabled` | Memory is stored and enabled for future context. | `Edited`, `Disabled`, `Deleted` | No |
| `SavedDisabled` | Memory is stored but not eligible for context injection. | `Enabled`, `Edited`, `Deleted` | No |
| `Edited` | Existing Memory is changed through user-triggered edit. | `ReviewPending`, `SavedEnabled`, `SavedDisabled` | No |
| `ExportRequested` | A future UI requests Memory export. | `ExportBlocked` in v1; future policy review only. | No |
| `ExportBlocked` | Public-beta v1 blocks Memory export or future evidence is incomplete. | None in v1. | Yes |
| `UpstreamPolicyCandidate` | A future bundled Hanako Memory plugin claims native policy support. | `PatchGateRetained` in v1; future Security/QA review only. | No |
| `PatchGateRetained` | Android keeps runtime patch enforcement despite future upstream evidence labels. | Normal Memory load/save/update/delete flows. | No |
| `Deleted` | User confirms deletion. | None | Yes |

## Endpoint Or Event Semantics

### Load Memory

- Endpoint: `GET /api/plugins/memory/state`
- Direction: Android to Hanako local HTTP.
- Inputs: optional query, optional type, includeDisabled flag, limit.
- Policy: query/type are local only; diagnostics must not export loaded Memory content.

### Save Or Update Memory

- Endpoints:
  - `POST /api/plugins/memory/items`
  - `PUT /api/plugins/memory/items/{id}`
- Direction: Android to Hanako local HTTP.
- Required fields: content.
- Optional fields: id, type, title, tags, enabled.
- Android policy:
  - Normalize unknown type to `project_fact`.
  - Trim title/content/tags and dedupe tags.
  - Block empty content.
  - Block content over `NBG_MEMORY_MAX_CONTENT_CHARS`; Android policy is aligned with the bundled Memory plugin's 4000-character content limit.
  - Block likely API keys, bearer tokens, password/secret assignments, GitHub tokens, AWS access keys, and private keys.
  - Memory write remains high risk in the permission model.
  - Android records a local audit event for create/update attempts with action, result, normalized type, policy risk, policy version, and timestamp only.
  - Blocked writes record `result=blocked`; successful writes record `result=success`; failed local/API mutations record `result=failure`.
- Runtime policy:
  - `android-memory-context-policy-v1` patches bundled/source `plugins/memory/lib/memory-store.js` only when the runtime patch target marker matches.
  - Save/update through Memory tools or local HTTP must run the same `nbg-memory-context-v1` risk classes before persistence.
  - Blocked Memory returns a local Memory policy error and must not be silently saved.
  - Public-beta v1 keeps the Android patch gate even if a future upstream Memory plugin advertises the same policy.
  - Future patch removal requires policy version parity, marker compatibility, parity tests, and Security/QA review.
- Idempotency: update requires id; create without id creates a new Memory item.

### Delete Memory

- Endpoint: `DELETE /api/plugins/memory/items/{id}`
- Direction: Android to Hanako local HTTP.
- Policy: deletion requires user action from UI or confirmed Agent tool call.
- Android records a local delete audit event with action/result/policy version/timestamp only and must not store or export the deleted item id.

### Context Injection

- Event: `memory:context`
- Direction: Hanako Memory plugin to Agent context.
- Policy: only enabled Memory that passes current context policy may be injected. The Android runtime patch filters blocked existing items before composing `memory:context`.

### Memory Export

- Endpoint/UI: none in public-beta v1.
- Policy:
  - `nbgReviewMemoryExportRequest()` always returns `allowExport=false` in v1.
  - Future export requires explicit user trigger, per-item selection, and a passing sensitive-content scan.
  - Diagnostics export is not a Memory export feature and must continue excluding Memory content.
- Retry behavior: not applicable in v1.

## Error Semantics

| Error | Meaning | User-visible message | Recovery |
| --- | --- | --- | --- |
| `blocked_empty` | Memory content is blank. | Memory content cannot be empty. | Add durable content or cancel. |
| `blocked_sensitive` | Content appears to contain a secret/token/password/private key. | Remove sensitive values before saving. | Redact or delete the sensitive value and retry. |
| `blocked_too_large` | Content exceeds local policy size. | Memory content is too long. | Split into smaller durable facts. |
| `plugin_missing` | Hanako Memory endpoint is unavailable. | Current HanakoPro has no Memory plugin API. | Restart or use a runtime with Memory plugin. |
| `network_local_failure` | Local Hanako API is reconnecting. | HanakoPro is reconnecting; retry later. | Retry after reconnect. |
| `memory_export_disabled_v1` | Memory export was requested but v1 has no export feature. | Memory export is not available in this public beta. | Use normal UI read/edit/delete controls; revisit when a separate export plan exists. |
| `memory_upstream_adoption_blocked_v1` | A future upstream Memory policy exists but Android must keep its patch gate in v1. | Android Memory policy patch remains active for this beta. | Revisit after parity evidence and Security/QA review. |

## Compatibility

Versioning:

- Context policy version is `nbg-memory-context-v1`.
- Memory export policy version is `nbg-memory-export-v1`.
- Diagnostics remains additive under `nbg-diagnostics-v1`.

Migration:

- Existing Memory items remain readable.
- Existing items are not rewritten by policy introduction.
- Existing loaded items that would now be blocked are counted in diagnostics but not exported or injected into runtime Memory context.
- Android policy is an early local gate; the bundled Hanako Memory plugin is also protected by the Android runtime patch because plugin tools and local HTTP can bypass Android UI.
- Future upstream Memory plugin policy is additive only in v1; it must not remove Android patch-gate enforcement.

Backward compatibility:

- Hanako may continue returning only id/type/title/content/tags/source/enabled timestamps.
- Android must apply conservative local policy when richer provenance fields are absent.

Deprecation:

- Silent Memory writes are outside this contract.
- Exporting Memory item content through diagnostics is prohibited.
- Separate Memory export is disabled in v1.

## Security Rules

- Sensitive fields: Memory title, content, tags, source session path, source turn id, search query, raw item id if it can encode private data.
- Redaction rules: diagnostics exports only aggregate counts, policy version, bounded audit metadata, and fixed Memory error-presence summary, never Memory text, title, tags, source session, turn id, item id, raw API error text, query, or backend echo.
- Local audit rules: audit events may store only action (`create`, `update`, `delete`), result (`success`, `failure`, `blocked`), normalized Memory type, policy risk, policy version, and timestamp. Audit events must not store Memory content, title, tags, item id, source session, source turn id, search query, raw error text, or hashes of private content.
- Confirmation requirements: Memory writes require explicit user confirmation. Agent Memory save/update/delete remains high risk.
- Hash/signature requirements: do not export hashes of private Memory content unless a separate support/privacy plan defines a need.
- Network/egress requirements: Memory is local-first. No cloud sync or telemetry is allowed under this contract.
- Export requirements: public-beta v1 has no Memory export feature. Future export must be user-triggered, per-item selected, pass sensitive scan, and undergo Security Agent review before any Memory content leaves normal UI state.
- Audit/logging requirements: do not log raw Memory content, title, tags, or search query.

## Test Oracle

Unit tests:

- `NbgMemoryContextPolicyTest.reviewAllowsKnownMemoryTypesAndNormalizesUnknownType`
- `NbgMemoryContextPolicyTest.reviewBlocksSecretsTokensPasswordsAndPrivateKeys`
- `NbgMemoryContextPolicyTest.reviewBlocksEmptyAndTooLargeContent`
- `NbgMemoryContextPolicyTest.memoryExportPolicyRequiresUserTriggerSelectionAndScanButIsDisabledInV1`
- `NbgMemoryContextPolicyTest.memoryUpstreamPolicyAdoptionKeepsAndroidPatchGateInV1`
- `NbgMemoryContextPolicyTest.memoryAuditEventsKeepOnlyMetadataAndStayBounded`
- `NbgDiagnosticsExportTest.diagnosticsExportIncludesStateCountsAndCapabilityHealth`
- `NbgDiagnosticsExportTest.diagnosticsExportRedactsSecretsPathsAndUserContent`
- `AndroidManifestBehaviorTest.memoryContextContractIsDocumentedAndWired`
- `AndroidManifestBehaviorTest.androidHanakoServerUsesStableUpstreamUserAgent`

Integration tests:

- Save a normal Memory item, reload Memory state, and verify count changes.
- Attempt to save content with a dummy API key; verify Android blocks before API call.
- Delete a Memory item and verify it is absent after reload.

Manual tests:

- Add, edit, search, filter, and delete Memory from the Memory page.
- Export diagnostics after loading Memory and verify no item text/title/tag/path appears.

Negative tests:

- Secret-like values are blocked.
- Oversized Memory is blocked.
- Unknown type normalizes to `project_fact`.
- Diagnostics never includes Memory body, title, tag, item id, source session, or source turn id.

## Operational Diagnostics

Diagnostics may include:

- Total Memory count.
- Enabled count.
- Loaded item count.
- Context policy version.
- Injectable loaded count.
- Blocked-sensitive loaded count.
- Blocked-too-large loaded count.
- Fixed Memory API error-presence summary (`memory_error_present`) without raw error text.
- Memory audit event count and recent bounded audit metadata.
- Diagnostics may include Memory audit metadata only: action, result, normalized type, policy risk, policy version, and timestamp.
- Memory export policy version and blocked/disabled status may be added later, but must not include Memory content.

Diagnostics must not include:

- Memory content.
- Titles.
- Tags.
- Item ids.
- Source session paths.
- Source turn ids.
- Search query.
- Raw Memory audit payloads beyond action/result/type/risk/policy/timestamp.
- Raw Memory API errors, including query, item id, backend-echoed title/content/tags, or path fragments.

## Open Questions

- None for Memory export in v1. Public-beta v1 does not expose Memory export; future export requires explicit user trigger, per-item selection, and sensitive scan approval.
- None for upstream Memory policy adoption in v1. Public-beta v1 keeps the Android runtime patch gate; future upstream adoption requires policy version parity, Android patch marker compatibility, parity tests, and Security/QA review before patch removal is considered.

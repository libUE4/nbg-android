---
title: "Skill source integrity contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["Android Agent", "Security Agent", "QA/Release Agent"]
review_agent: "Security Agent"
security_review: "required"
---

# Skill Source Integrity Contract

## Status

Active for public-beta hardening.

Audience:

- Android implementation agents
- Hanako runtime integration agents
- Security and release reviewers
- QA agents writing Skills regression tests

## Purpose

Define how NBG Android classifies, installs, enables, diagnoses, and releases Skills from bundled assets, user-managed paths, remote URLs, external Skill directories, and Agent-created or Agent-modified files.

This contract prevents three P0 failures:

- Treating an untrusted external Skill as a trusted default merely because its `name` matches a bundled Skill.
- Installing remote Skill content without a reviewable provenance record.
- Exporting Skill paths, descriptions, source URLs, or content through diagnostics.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
| Android Skills UI | Collects user intent for install, enable, delete, bundle, and external path edits. | `NbgAgentSkillsUi.kt` |
| Chat controller | Validates Android-side source policy and calls Hanako Skills APIs. | `HanakoChatController.kt`, `HanakoSkillInstallSupport.kt` |
| Source integrity model | Classifies source kind, trust tier, install policy, bundled Skill hash pins, and provenance. | `NbgSkillSourceIntegrity.kt`, `HanakoSkillsModels.kt` |
| Learned Skill draft policy | Gates Agent-generated Skill drafts behind task evidence, source task id, target review, SHA-256 metadata, and permission tier. | `NbgLearnedSkillDraftPolicy.kt` |
| Hanako API client | Reads Skills state and updates enabled Skill names. | `HanakoApiClient.kt` |
| Runtime seeding | Copies bundled default Skills into Hanako home and configures default enabled Skills. | `HanakoServerLauncher.kt`, `app/src/main/assets/nbg-default-skills` |
| Diagnostics export | Reports only redacted aggregate integrity counts. | `NbgDiagnosticsExport.kt` |
| Release gate | Generates checksums for bundled Skill assets. | `scripts/nbg_release_gate.sh`, `build/release-gate/assets.sha256` |

## Trust Boundary

Skills cross these boundaries:

- APK asset boundary: bundled Skill files ship with the app, are pinned by Android source hashes, and are covered by release asset checksums.
- Hanako pack boundary: the bundled server pack's `skills2set` copy of a default Skill must match the Android pinned default Skill asset byte-for-byte.
- Android app to proot boundary: bundled or downloaded files are copied into Hanako's Ubuntu root.
- Network boundary: remote `https` Skill URLs may download content into `/root/.nbg-skill-installs`.
- Local filesystem boundary: user-provided absolute paths and external Skill directories may point at arbitrary local content inside proot.
- Agent boundary: Agent-created or Agent-modified Skills are side effects and require review before becoming trusted defaults.
- Diagnostics boundary: diagnostics may leave the app through clipboard/share, so it must not contain Skill content, descriptions, paths, source URLs, or stable raw-source identifiers.

## Data Structures

Android-side source review:

```kotlin
data class NbgSkillSourceReview(
  val sourceKind: NbgSkillSourceKind,
  val trustTier: NbgSkillTrustTier,
  val allowInstall: Boolean,
  val requiresReview: Boolean,
  val sourceHost: String,
  val redactedLocation: String,
  val reason: String,
)
```

Bundled Skill asset pin:

```kotlin
data class NbgBundledSkillAssetPin(
  val skillName: String,
  val relativePath: String,
  val sizeBytes: Int,
  val sha256: String,
)
```

Trusted external promotion policy:

```kotlin
const val NBG_SKILL_TRUSTED_EXTERNAL_PROMOTION_POLICY = "nbg-skill-trusted-external-promotion-v1"

data class NbgExternalSkillPromotionReview(
  val policyVersion: String,
  val eligibleForTrustedDefault: Boolean,
  val trustTier: NbgSkillTrustTier,
  val requiresReview: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val reason: String,
)
```

Required future evidence for trusted external Skill promotion:

- `signed_bundle`
- `pinned_sha256_allowlist`

Public-beta v1 has no `trusted_external` tier and no external Skill promotion path. A future promotion path must require both evidence classes; a downloaded file hash, provenance record, host allowlist, or Skill name match alone is not sufficient.

Learned Skill draft review:

```kotlin
const val NBG_LEARNED_SKILL_DRAFT_POLICY_VERSION = "nbg-learned-skill-draft-v1"

data class NbgLearnedSkillDraftReview(
  val allowDraft: Boolean,
  val allowInstall: Boolean,
  val allowEnable: Boolean,
  val requiresReview: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val permissionTier: NbgPermissionRiskTier,
  val sourceTaskId: String,
  val targetPathLabel: String,
  val reason: String,
)
```

Required evidence for Agent-generated Skill drafts:

- `completion_evidence_complete`
- `source_task`
- `target_path_reviewed`
- `draft_sha256`
- `permission_tier_recorded`

Public-beta v1 may save a learned Skill only as a local draft when all required evidence is present. `allowInstall=false` and `allowEnable=false` are mandatory until a user review/install flow explicitly accepts the draft. Dangerous permission tiers must remain review-only and require strong confirmation before any future install path.

Allowed `sourceKind` values:

- `builtin`
- `user_installed`
- `local_path`
- `localhost_http`
- `remote_https`
- `external_path`
- `unknown`

Allowed `trustTier` values:

- `trusted_bundled`
- `user_managed`
- `unverified_external`
- `blocked`

Downloaded URL provenance file:

```json
{
  "schema": "nbg-skill-source-provenance-v1",
  "sourceKind": "remote_https",
  "trustTier": "unverified_external",
  "requiresReview": true,
  "sourceHost": "raw.githubusercontent.com",
  "sourceUrlHash": "hash of redacted source identity",
  "redactedSource": "https://raw.githubusercontent.com/.../SKILL.md",
  "fileName": "SKILL.md",
  "sizeBytes": 1234,
  "sha256": "downloaded file sha256",
  "downloadedAtMs": 1000
}
```

Diagnostics export shape:

```json
{
  "skills": {
    "visibleCount": 0,
    "enabledCount": 0,
    "sourceIntegrityVersion": "nbg-skill-source-integrity-v1",
    "expectedBundledCount": 0,
    "pinnedBundledAssetCount": 0,
    "builtinCount": 0,
    "externalCount": 0,
    "trustedBundledCount": 0,
    "userManagedCount": 0,
    "unverifiedExternalCount": 0,
    "requiresReviewCount": 0
  }
}
```

## State Machine

| State | Entered When | Allowed Transitions | Terminal? |
| --- | --- | --- | --- |
| `BundledReviewed` | Skill ships under `app/src/main/assets/nbg-default-skills`, Android source pins its size/SHA-256, and release gate records its checksum. | `Seeded`, `HashMismatch`, `ReleaseBlocked` | No |
| `Seeded` | Runtime copies bundled Skill into Hanako home. | `DefaultEligible`, `HashMismatch` | No |
| `DefaultEligible` | Skill is bundled, its pinned assets match, and it is not modified or removed. | `Enabled`, `DisabledByUser` | No |
| `PathProvided` | User enters a local absolute path. | `UserManagedInstalled`, `Blocked` | No |
| `UrlProvided` | User enters a URL. | `Downloaded`, `Blocked` | No |
| `Downloaded` | Android downloads a URL source within size limits. | `ProvenanceRecorded`, `Blocked` | No |
| `ProvenanceRecorded` | `.nbg-skill-provenance.json` is written next to the downloaded file. | `UnverifiedInstalled` | No |
| `UnverifiedInstalled` | Hanako installs a path/URL/external Skill whose source is not bundled trusted. | `ExplicitlyEnabled`, `Deleted`, `FuturePromotionCandidate` | No |
| `FuturePromotionCandidate` | A future contract version has both signed bundle evidence and pinned SHA-256 allowlist evidence. | Not available in public-beta v1. | No |
| `ExternalConfigured` | User saves external Skill paths. | `UnverifiedExternal`, `Blocked` | No |
| `HashMismatch` | Bundled default content differs from expected release evidence or runtime marker. | `DisabledByUser`, `ReleaseBlocked` | No |
| `Blocked` | Source violates policy. | User changes input. | Yes |

## Endpoint Or Event Semantics

### Manual URL Install

- Direction: Android UI/controller to network, then Android to Hanako `/api/skills/install`.
- Required fields: user-provided source string.
- Android policy:
  - The Skills page must show an explicit source review step before installing a manual URL or path.
  - `https` remote URLs are allowed but always `requiresReview=true`.
  - `http` is allowed only for loopback hosts: `localhost`, `127.0.0.1`, `::1`.
  - URL userinfo is blocked.
  - Non-HTTP(S) schemes are blocked.
  - Download size limit is `HANA_SKILL_DOWNLOAD_MAX_BYTES`.
  - Raw `SKILL.md` must include a `name` field before install.
  - Downloaded files must get `nbg-skill-source-provenance-v1` provenance.
  - If Hanako auto-enables newly installed review-required Skills, Android must immediately restore the enabled set so new unverified/user-managed Skills remain disabled until the user explicitly confirms enabling them.
  - Same-name replacements must not inherit enabled state unless source kind, trust tier, review requirement, and path identity still match the previously enabled Skill.
- Idempotency: repeated install of the same URL may overwrite the temporary downloaded file and provenance for that URL hash directory.
- Retry behavior: user can retry after editing URL or after network recovery.

### Local Path Install

- Direction: Android UI/controller to Hanako `/api/skills/install`.
- Required fields: absolute path.
- Policy: local paths are `user_managed`, not `trusted_bundled`, require explicit source review before install, and require explicit confirmation before enabling.

### External Skill Paths

- Direction: Android UI/controller to Hanako `/api/skills/external-paths`.
- Required fields: list of absolute paths.
- Policy: external paths are `unverified_external` for diagnostics and cannot become trusted defaults in public-beta v1. A future trusted-external path requires both signed bundle evidence and pinned SHA-256 allowlist evidence.

### Manual Enablement

- Direction: Android Skills UI to Hanako `/api/agents/{agentId}/skills`.
- Policy: enabling a Skill whose source review has `requiresReview=true` must show an explicit confirmation dialog before Android writes the enabled list.
- Disabling a Skill may remain a direct action.

### Default Skill Enablement

- Direction: Android runtime/API client to Hanako agent configuration.
- Required fields: default Skill names from the Android bundled Skill list after pinned asset verification.
- Policy: public-beta default enablement is valid only when the Skill is in the Android bundled Skill list, every pinned bundled asset matches size and SHA-256, and the Hanako Skills API reports the visible Skill as trusted bundled. A Skill name match alone is not sufficient.
- Hash mismatch behavior: runtime seeding skips the mismatched bundled Skill, removes any stale same-name seeded directory, and does not add that bundled Skill to agent default enablement.
- Same-name untrusted behavior: if a visible enabled Skill uses the same name as an Android bundled Skill but is reported as user-managed, external, path-backed, unknown, or otherwise not trusted bundled, Android removes that name from default enablement instead of preserving it by name.

### Trusted External Promotion

- Direction: future contract version only.
- Public-beta v1 policy:
  - External, URL-installed, path-backed, Agent-written, and user-managed Skills cannot become `trusted_bundled` or any trusted default tier.
  - `nbgReviewExternalSkillTrustedPromotion()` always returns `eligibleForTrustedDefault=false` in v1.
  - Future promotion requires both `signed_bundle` and `pinned_sha256_allowlist` evidence.
  - A downloaded file hash, provenance file, host allowlist, or Skill name match alone is not enough to become trusted.
  - Any future trusted-external tier requires a new contract version, diagnostics shape review, and Security Agent review.

## Error Semantics

| Error | Meaning | User-visible message | Recovery |
| --- | --- | --- | --- |
| `blocked_plain_http` | Remote URL used `http` outside loopback. | External Skill links must use `https`; localhost `http` is for debugging only. | Use an `https` URL or local path. |
| `blocked_credentials` | URL contained username/password/token in userinfo. | Skill link cannot contain username, password, or token. | Remove credentials and use a public URL or local file. |
| `download_too_large` | Download exceeds 5 MB. | Skill file is too large. | Use a smaller Skill package. |
| `missing_name` | Raw `SKILL.md` lacks `name`. | Downloaded `SKILL.md` is missing a `name` field. | Fix the Skill frontmatter. |
| `provenance_write_failed` | Download succeeded but provenance cannot be written. | Skill install failed while recording source metadata. | Retry after storage recovery; do not install without provenance. |
| `hash_mismatch` | Bundled expected content no longer matches. | Built-in Skill integrity check failed. | Reinstall app or block release until investigated. |

## Compatibility

Versioning:

- Provenance schema is `nbg-skill-source-provenance-v1`.
- Diagnostics remains under `nbg-diagnostics-v1` and adds only aggregate fields.

Migration:

- Existing installed user Skills without provenance are treated as `user_managed` or `unverified_external`, not `trusted_bundled`.
- Existing external path settings remain valid but are counted as review-required.

Backward compatibility:

- Hanako server may continue returning only `source`, `filePath`, `baseDir`, `externalPath`, and `readonly`.
- Android must infer conservative trust tiers when newer integrity fields are absent.

Deprecation:

- Plain external `http` Skill installs are not part of the public-beta contract.
- Treating a Skill as trusted default by name alone is deprecated and must not be expanded.

## Security Rules

- Sensitive fields: source URLs, query strings, tokens, local paths, Skill contents, Skill descriptions, Skill file hashes from user/private sources.
- Redaction rules: diagnostics exports aggregate counts only. Provenance may store `sourceHost`, redacted location, downloaded file size, downloaded file hash, and hash of redacted source identity; it must not store full URL query strings or URL userinfo.
- Error redaction: Skills diagnostics error text is exported only as a safe presence marker, not as raw server error text, because Skills errors may contain names, hashes, URLs, or paths.
- Confirmation requirements: installing, updating, enabling, deleting, or editing external Skills is high risk and must remain confirmation-gated when performed by Agent tools.
- Manual UI requirements: manual install of any path or URL must show source review before install, and manual enablement of review-required Skills must show a confirmation dialog.
- Learned Skill requirements: Agent-generated Skills must pass `nbgReviewLearnedSkillDraft()` before being persisted as drafts; generated Skills must not be installed, enabled, or promoted to trusted defaults by the completion task itself.
- Hash/signature requirements: bundled Skill assets require Android source-pinned size/SHA-256 and release-gate checksum evidence. Remote/user/external Skills remain unverified in public-beta v1. A future trusted-external promotion path must require both signed bundle evidence and pinned SHA-256 allowlist evidence.
- Network/egress requirements: Android may download only user-provided URLs. No automatic Skill marketplace sync is allowed under this contract.
- Audit/logging requirements: do not log full Skill content, full URL, query string, or local path in diagnostics or release notes.

## Test Oracle

Unit tests:

- `NbgSkillSourceIntegrityTest.reviewBlocksPlainExternalHttpAndCredentialUrls`
- `NbgSkillSourceIntegrityTest.reviewAllowsHttpsAndLocalhostAsReviewRequiredSources`
- `NbgSkillSourceIntegrityTest.skillSummaryReviewSeparatesBundledUserAndExternalSources`
- `NbgSkillSourceIntegrityTest.defaultEnablementDropsSameNameUntrustedBundledSkills`
- `NbgSkillSourceIntegrityTest.bundledSkillAssetPinsMatchSourceAssets`
- `NbgSkillSourceIntegrityTest.bundledSkillHashMismatchBlocksTrustedDefaultEligibility`
- `NbgSkillSourceIntegrityTest.externalSkillPromotionRequiresSignedBundleAndPinnedSha256ButStaysUntrustedInV1`
- `NbgSkillSourceIntegrityTest.manualInstallEnablementDropsNewReviewRequiredSkills`
- `NbgSkillSourceIntegrityTest.manualInstallEnablementDropsSameNameReviewRequiredReplacement`
- `NbgSkillSourceIntegrityTest.provenanceRecordsDownloadedFileHashWithoutRawSourceUrl`
- `NbgLearnedSkillDraftPolicyTest.learnedSkillDraftRequiresCompletionTargetSourceHashAndPermissionEvidence`
- `NbgLearnedSkillDraftPolicyTest.learnedSkillDraftBlocksMissingEvidenceAndKeepsDangerousDraftReviewOnly`
- `NbgDiagnosticsExportTest.diagnosticsExportIncludesStateCountsAndCapabilityHealth`
- `AndroidManifestBehaviorTest.skillSourceIntegrityContractIsDocumentedAndWired`

Integration tests:

- Install raw `https` `SKILL.md`; verify `.nbg-skill-provenance.json` exists and Hanako can list the Skill.
- Attempt external `http` URL; verify install is blocked before network download.
- Configure external path; verify diagnostics counts increase without exporting path.

Manual tests:

- Install a local Skill path, enable it, then export diagnostics and verify no path/content appears.
- Reinstall app and confirm bundled `nbg-engineering-core` remains present and enabled.

Negative tests:

- URL with `https://token@example.com/SKILL.md` is blocked.
- Raw `SKILL.md` missing `name` is rejected.
- Oversized download is rejected.
- Diagnostics export does not include Skill name, description, file path, base dir, external path, raw URL, or Skill content.

## Operational Diagnostics

Diagnostics must expose only:

- Skill visible/enabled/bundle/external/builtin/readonly counts.
- Trusted bundled count.
- User-managed count.
- Unverified external count.
- Review-required count.
- Source integrity version and aggregate bundled/pinned asset counts.
- Redacted error summary.
- Safe Skills error presence marker; never raw Skills API error text.

Diagnostics must not expose:

- Skill names.
- Descriptions or instructions.
- File paths or external path values.
- Full source URLs or query strings.
- Downloaded file hashes for private/user Skills.
- Bundled Skill names, file paths, or pinned hash values.

## Open Questions

- None for v1.

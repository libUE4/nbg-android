---
title: "Pet resource integrity contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["UX/UI Agent", "Security Agent", "QA/Release Agent"]
review_agent: "Security Agent"
security_review: "required"
---

# Pet Resource Integrity Contract

## Status

Active for public-beta hardening.

Audience:

- Android implementation agents
- Pet/PetDex UI agents
- Security reviewers
- QA/release agents

## Purpose

Define how NBG Android classifies, downloads, installs, previews, diagnoses, and releases Pet/PetDex resources.

This contract prevents four P0 failures:

- Downloading unbounded remote spritesheets into memory or storage.
- Treating downloaded PetDex resources as trusted bundled defaults.
- Persisting raw PetDex URLs, query strings, credentials, or local paths into diagnostics.
- Leaving half-installed or untraceable pet assets after a failed download.
- Showing a freshly fetched PetDex manifest without a separate local provenance record.

PetDex remains a beta ecosystem surface, but safety, privacy, stability, and recovery do not degrade for beta features.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
| PetDex manifest client | Fetches, parses, and records separate provenance for the PetDex manifest within network and size policy. | `NbgPetDexClient.kt` |
| PetDex preview store | Downloads a bounded preview spritesheet and falls back silently on preview failure. | `NbgPetDexPreviewStore.kt` |
| Pet store | Reviews install inputs, downloads staged resources, writes provenance, commits state, deletes pets. | `NbgPetStore.kt` |
| Resource integrity model | Classifies trust tier, URL policy, size limits, slug rules, and provenance schema. | `NbgPetResourceIntegrity.kt` |
| Pet UI | Shows installed/bundled/PetDex pets and keeps PetDex beta behavior user-controlled. | `NbgPetsUi.kt` |
| Diagnostics export | Exports redacted aggregate trust counts only. | `NbgDiagnosticsExport.kt` |
| Release gate | Checksums bundled default pet assets. | `scripts/nbg_release_gate.sh`, bundled pet assets |

## Trust Boundary

Pet resources cross these boundaries:

- APK asset boundary: bundled default pet assets ship inside the app and are covered by release asset checksums.
- Network boundary: PetDex manifest, spritesheets, and `pet.json` files are downloaded over HTTPS.
- Android app storage boundary: downloaded assets are written under app-private `filesDir/nbg-pets`.
- Rendering boundary: preview spritesheets are decoded into bitmaps and must remain bounded in memory.
- Diagnostics boundary: diagnostics may leave the app by share/clipboard and must not include raw URLs, query strings, tokens, local paths, or pet file contents.

## Data Structures

Android-side install review:

```kotlin
data class NbgPetDexInstallReview(
  val sourceKind: NbgPetResourceSourceKind,
  val trustTier: NbgPetTrustTier,
  val allowInstall: Boolean,
  val requiresReview: Boolean,
  val safeSlug: String,
  val sourceHost: String,
  val reason: String,
)
```

Allowed `sourceKind` values:

- `built_in`
- `petdex_https`
- `user_managed`
- `unknown`

Allowed `trustTier` values:

- `trusted_bundled`
- `unverified_petdex`
- `user_managed`
- `blocked`

Trusted PetDex promotion policy:

```kotlin
const val NBG_PETDEX_TRUSTED_PROMOTION_POLICY = "nbg-petdex-trusted-promotion-v1"
val NBG_PETDEX_TRUSTED_REQUIRED_EVIDENCE = listOf(
  "signed_manifest",
  "per_resource_sha256_manifest",
)

data class NbgPetDexTrustedPromotionReview(
  val policyVersion: String,
  val eligibleForTrustedDefault: Boolean,
  val trustTier: NbgPetTrustTier,
  val requiresReview: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val reason: String,
)
```

Public-beta v1 has no trusted external PetDex promotion path. Future promotion out of `unverified_petdex` requires both signed manifest evidence and per-resource SHA-256 manifest evidence, not either alone. Even when both future evidence labels are present, Android v1 must return `eligibleForTrustedDefault=false`.

Provenance file:

```json
{
  "schema": "nbg-pet-resource-integrity-v1",
  "sourceKind": "petdex_https",
  "trustTier": "unverified_petdex",
  "requiresReview": true,
  "slug": "boba",
  "displayName": "Boba",
  "kind": "character",
  "sourceHost": "assets.petdex.dev",
  "sprite": {
    "kind": "sprite",
    "fileName": "sprite.webp",
    "sizeBytes": 1234,
    "sha256": "downloaded file sha256",
    "sourceHost": "assets.petdex.dev",
    "redactedSource": "https://assets.petdex.dev/.../sprite.webp",
    "maxBytes": 8388608
  },
  "petJson": {
    "kind": "pet_json",
    "fileName": "pet.json",
    "sizeBytes": 123,
    "sha256": "downloaded file sha256",
    "sourceHost": "assets.petdex.dev",
    "redactedSource": "https://assets.petdex.dev/.../petjson.json",
    "maxBytes": 524288
  },
  "installedAtMs": 1000
}
```

Manifest provenance file:

File name: `.nbg-petdex-manifest-provenance.json`

Stored under app-private `filesDir/nbg-pets`, separate from per-installed-pet provenance.

```json
{
  "schema": "nbg-pet-resource-integrity-v1",
  "sourceKind": "petdex_https",
  "trustTier": "unverified_petdex",
  "requiresReview": true,
  "sourceHost": "petdex.dev",
  "redactedSource": "https://petdex.dev/.../manifest",
  "sizeBytes": 1234,
  "sha256": "manifest body sha256",
  "maxBytes": 1048576,
  "declaredTotal": 10,
  "parsedPetCount": 8,
  "fetchedAtMs": 1000
}
```

The manifest provenance file must not store the raw manifest body, pet list, query string, URL userinfo, or local filesystem path.

Diagnostics export shape:

```json
{
  "pets": {
    "installedCount": 0,
    "hidden": false,
    "currentBuiltIn": true,
    "currentSource": "built_in",
    "resourcePolicyVersion": "nbg-pet-resource-integrity-v1",
    "trustedBundledCount": 0,
    "unverifiedPetDexCount": 0,
    "userManagedCount": 0,
    "blockedLoadedCount": 0,
    "requiresReviewCount": 0,
    "petDexWithProvenanceCount": 0,
    "legacyPetDexWithoutProvenanceCount": 0,
    "manifestProvenancePresent": false,
    "manifestSourceKind": "",
    "manifestTrustTier": "",
    "manifestRequiresReview": false,
    "manifestDeclaredTotal": 0,
    "manifestParsedPetCount": 0,
    "manifestFetchedAtMs": 0,
    "manifestSha256Prefix": ""
  }
}
```

## State Machine

| State | Entered When | Allowed Transitions | Terminal? |
| --- | --- | --- | --- |
| `BundledReviewed` | Pet asset ships under `app/src/main/assets/nbg-default-pets` or `res/drawable-nodpi`. | `Seeded`, `ReleaseBlocked` | No |
| `Seeded` | Android copies bundled asset into app-private pet storage and writes trusted provenance. | `Selected`, `DeletedByUser` | No |
| `ManifestFetched` | PetDex manifest is fetched from `https://petdex.dev/api/manifest` within size limits. | `ManifestProvenanceRecorded`, `ManifestRejected` | No |
| `ManifestProvenanceRecorded` | `.nbg-petdex-manifest-provenance.json` is written under app-private `filesDir/nbg-pets`. | `PreviewRequested`, `InstallRequested` | No |
| `PreviewRequested` | UI asks for a PetDex preview. | `PreviewDecoded`, `PreviewFallback` | No |
| `InstallRequested` | User chooses a PetDex manifest pet. | `UrlReviewed`, `Blocked` | No |
| `UrlReviewed` | Sprite and `pet.json` URLs pass HTTPS, host, userinfo, extension, and final redirect review. | `StagedDownload`, `Blocked` | No |
| `StagedDownload` | Resources download into a staging directory within size limits. | `ProvenanceRecorded`, `Blocked` | No |
| `ProvenanceRecorded` | `.nbg-pet-provenance.json` is written next to staged assets. | `Committed` | No |
| `Committed` | Staged directory atomically replaces the installed directory and prefs point to the new pet. | `Selected`, `DeletedByUser`, `Reinstalled` | No |
| `LegacyUnverified` | Existing PetDex install lacks provenance. | `Selected`, `DeletedByUser`, `Reinstalled` | No |
| `FuturePromotionCandidate` | Future server-side metadata provides both signed manifest evidence and per-resource SHA-256 manifest evidence. | Security/QA review only; public-beta v1 still stays `unverified_petdex`. | No |
| `UserManagedReviewed` | Existing local, user-imported, custom, or side-loaded pet source is loaded from app-private state. | `Selected`, `DeletedByUser`, `Reinstalled` | No |
| `Blocked` | URL, slug, host, size, extension, or storage policy fails. | User changes input or retries after network/storage recovery. | Yes |

## Endpoint Or Event Semantics

### Manifest Fetch

- Direction: Android to `https://petdex.dev/api/manifest`.
- Required fields in response: `pets[].slug`, `pets[].spritesheetUrl`, `pets[].petJsonUrl`.
- Optional fields: `displayName`, `kind`, `submittedBy`, `zipUrl`.
- Policy:
  - PetDex manifest URL must be HTTPS and under `petdex.dev`.
  - Maximum manifest size is `NBG_PETDEX_MANIFEST_MAX_BYTES`.
  - Manifest parsing may skip malformed pet entries.
  - A successful Android refresh writes separate manifest provenance before exposing the manifest to the UI.
  - Manifest provenance records source kind, trust tier, review requirement, host, redacted source, size, sha256, declared total, parsed pet count, and fetch timestamp.
  - Manifest provenance must not record raw manifest body, pet list, query string, userinfo, or local path.
- Retry behavior: user can refresh after network recovery.

### Preview Download

- Direction: Android Pet UI to PetDex spritesheet URL.
- Policy:
  - Sprite URL must pass `NbgPetResourceFileKind.Sprite` review.
  - Final redirect URL must pass the same review.
  - Maximum sprite size is `NBG_PET_MAX_SPRITE_BYTES`.
  - Preview failure returns `null` and UI falls back; it must not fail the whole dialog.

### Install Download

- Direction: Android Pet store to PetDex sprite and `pet.json` URLs.
- Policy:
  - PetDex 资源必须使用 HTTPS.
  - Host must be `petdex.dev` or a subdomain such as `assets.petdex.dev`.
  - URL userinfo is blocked.
  - External `http`, localhost, loopback, and private IP literals are blocked.
  - Sprite paths must end in `.webp`.
  - Pet JSON paths must end in `.json`.
  - Final redirect URL must pass the same review.
  - Maximum sprite size is 8 MiB.
  - Maximum pet JSON size is 512 KiB.
  - Install writes into a staging directory, records hashes and provenance, then commits.
- Idempotency: reinstalling the same slug replaces the installed directory only after staging succeeds.
- Retry behavior: user can retry after editing source data or recovering network/storage.

## Error Semantics

| Error | Meaning | User-visible message | Recovery |
| --- | --- | --- | --- |
| `blocked_plain_http` | PetDex resource used `http`. | PetDex resources must use HTTPS. | Use the official PetDex HTTPS source. |
| `blocked_credentials` | URL contained username/password/token userinfo. | PetDex resource URL cannot contain credentials. | Remove credentials and retry. |
| `blocked_host` | Host is not `petdex.dev` or a subdomain, or host is local/private. | PetDex resource host is not allowed. | Use an approved PetDex asset host. |
| `blocked_extension` | Sprite or JSON path does not match expected extension. | PetDex resource file type is not supported. | Use `.webp` sprite and `.json` metadata. |
| `download_too_large` | Manifest, sprite, or JSON exceeds size limit. | PetDex resource is too large. | Use a smaller asset. |
| `manifest_provenance_write_failed` | Manifest fetch and parse succeeded but manifest provenance cannot be written. | PetDex manifest provenance write failed. | Retry after storage recovery. |
| `provenance_write_failed` | Downloads succeeded but provenance cannot be written. | Pet install failed while recording source metadata. | Retry after storage recovery. |
| `staged_commit_failed` | Staged resources could not replace current install. | Pet install failed while committing files. | Retry; existing installed pet should remain usable when possible. |

## Compatibility

Versioning:

- Resource policy version is `nbg-pet-resource-integrity-v1`.
- Provenance file name is `.nbg-pet-provenance.json`.
- Manifest provenance file name is `.nbg-petdex-manifest-provenance.json`.
- Diagnostics remains `nbg-diagnostics-v1` and adds aggregate fields plus manifest provenance summary only.

Migration:

- Existing installs with an explicit `petdex` or `petdex_https` source but without provenance remain usable and deletable.
- Explicit legacy PetDex installs are classified as `unverified_petdex` and counted under `legacyPetDexWithoutProvenanceCount`.
- Reinstalling an explicit legacy PetDex pet writes v1 provenance.
- Manifest provenance is independent from installed pet provenance; refreshing the manifest may create or replace `.nbg-petdex-manifest-provenance.json` without modifying installed pets.
- Existing local, user-imported, custom, or side-loaded pets are classified as `user_managed`, remain usable and deletable, require review, and are not counted as blocked loaded pets.
- Blank or unrecognized source strings remain `blocked` until they can be attributed to a supported source class.
- A raw `built-in` or `built_in` source label is not sufficient to enter `trusted_bundled`; the summary must carry the bundled flag or trusted bundled provenance.

Backward compatibility:

- Existing diagnostics fields `installedCount`, `hidden`, `currentBuiltIn`, and `currentSource` remain present.
- PetDex manifest parsing still accepts valid entries without requiring new server fields.

Deprecation:

- Treating PetDex downloads as trusted bundled defaults is deprecated.
- Unbounded `body.bytes()` or stream copy for PetDex network resources is not allowed.
- Raw PetDex URLs or local pet paths in diagnostics are not allowed.

## Security Rules

- Sensitive fields: full PetDex URLs, query strings, userinfo, tokens, local file paths, downloaded pet contents, raw manifest body, manifest pet list, and private sprite/metadata paths.
- Redaction rules: diagnostics export aggregate counts, normalized source kind, and manifest provenance summary only. Provenance may store host, redacted source, file size, and sha256; it must not store query strings, URL userinfo, raw manifest body, pet list, or local paths.
- Confirmation requirements: installing or deleting PetDex resources remains user-controlled; Agent-initiated external pet installs must pass the Permission Risk Model.
- Hash/signature requirements: bundled pet assets are covered by release-gate checksums. PetDex downloads record sha256 provenance but remain `unverified_petdex` until a future signed or pinned-hash promotion path exists.
- Trusted promotion policy: public-beta v1 does not promote PetDex resources to trusted bundled defaults. A future promotion path requires both signed manifest evidence and per-resource SHA-256 manifest evidence, plus Security Agent review and diagnostics schema updates.
- Insufficient evidence: HTTPS, source host allowlists, downloaded file hashes, manifest provenance, matching slugs, and PetDex source labels are not enough to become trusted bundled defaults.
- User-managed pet resources are never promoted to `trusted_bundled` by source label alone. They require review and diagnostics may expose only aggregate counts.
- Network/egress requirements: Android may fetch the official PetDex manifest and user-requested PetDex asset URLs only. No automatic third-party pet marketplace sync is allowed under this contract.
- Audit/logging requirements: do not log full PetDex URLs, query strings, file contents, or local pet paths in diagnostics or release notes.

## Test Oracle

Unit tests:

- `NbgPetResourceIntegrityTest.petDexInstallReviewAllowsHttpsAssetsAndMarksUnverifiedPetDex`
- `NbgPetResourceIntegrityTest.petDexInstallReviewBlocksUnsafeUrls`
- `NbgPetResourceIntegrityTest.petDexInstallReviewBlocksUnsafeSlugAndLongSlug`
- `NbgPetResourceIntegrityTest.petResourceProvenanceRecordsHashesAndRedactsUrls`
- `NbgPetResourceIntegrityTest.petDexManifestProvenanceRecordsCountsHashAndNoRawManifestData`
- `NbgPetResourceIntegrityTest.petDexTrustedPromotionRequiresSignedManifestAndResourceHashesButStaysUntrustedInV1`
- `NbgPetResourceIntegrityTest.petSummaryReviewSeparatesPetDexUserManagedAndBlockedSources`
- `NbgPetResourceIntegrityTest.petSummaryReviewAcceptsBundledProvenanceForSeededPets`
- `NbgPetResourceIntegrityTest.readPetResourceBytesWithLimitRejectsOversizedResources`
- `NbgDiagnosticsExportTest.diagnosticsExportIncludesStateCountsAndCapabilityHealth`
- `NbgDiagnosticsExportTest.diagnosticsExportNormalizesCurrentPetSourceBeforeExport`
- `NbgDiagnosticsExportTest.diagnosticsExportCountsCanonicalPetDexProvenanceSources`
- `NbgDiagnosticsExportTest.diagnosticsExportIncludesPetDexManifestProvenanceSummaryWithoutRawLocation`

Integration tests:

- `AndroidManifestBehaviorTest.petResourceIntegrityContractIsDocumentedAndWired`
- `AndroidManifestBehaviorTest.androidExposesPetdexPetManagementControls`
- `AndroidManifestBehaviorTest.petdexManifestParserSupportsSearchAndCharacterFiltering`

Manual tests:

- Refresh PetDex manifest.
- Open PetDex dialog with at least one successful preview and one fallback preview.
- Install a PetDex pet, select it, restart app, delete it.
- Reinstall the same PetDex slug and verify the old pet is not lost on failed download.

Negative tests:

- External `http`, localhost, loopback, private IP literal, credential URL, wrong extension, oversize manifest, oversize sprite, and oversize `pet.json` are blocked.
- Diagnostics export does not contain raw PetDex URL, query token, local path, sprite bytes, or pet JSON body.

## Operational Diagnostics

Diagnostics must expose:

- `resourcePolicyVersion`
- installed pet count
- normalized current source kind
- trusted bundled count
- unverified PetDex count
- user-managed loaded count
- blocked loaded count
- review-required count
- PetDex provenance-present count
- legacy PetDex without provenance count
- PetDex manifest provenance present flag
- PetDex manifest source kind and trust tier
- PetDex manifest review-required flag
- PetDex manifest declared total and parsed pet count
- PetDex manifest fetch timestamp
- PetDex manifest sha256 prefix

Diagnostics must not expose:

- Raw PetDex URLs
- Query strings
- Userinfo
- Local pet paths
- Downloaded file contents
- Raw manifest body
- Manifest pet list

## Open Questions

None for v1. Future PetDex trusted promotion requires both signed manifest evidence and per-resource SHA-256 manifest evidence, but public-beta v1 has no trusted external PetDex promotion path.

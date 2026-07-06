---
title: "Task completion evidence contract"
status: active
date: 2026-07-06
type: contract
target_repo: /root/nbg-android
owners: ["Android Agent", "QA/Release Agent"]
review_agent: "Security Agent"
security_review: "required"
---

# Task Completion Evidence Contract

## Status

Active for Hermes reference adoption and public-beta hardening.

## Purpose

NBG must not mark agent work as done from prose alone. Completion claims need structured evidence or an explicit user override.

This contract covers Android-side evidence capture, cached JSON compatibility, and standardized evidence cards in chat/tool UI.

## Data Structures

Policy version:

```kotlin
const val NBG_TASK_COMPLETION_EVIDENCE_VERSION = "nbg-task-completion-evidence-v1"
```

Evidence bundle:

```kotlin
data class NbgTaskCompletionEvidenceBundle(
  val contractId: String,
  val title: String,
  val criteria: List<NbgTaskCompletionCriterion>,
  val evidence: List<NbgTaskCompletionEvidenceItem>,
  val overrideReason: String,
)
```

Allowed criterion kinds:

- `diff`
- `file_write`
- `command_exit`
- `test_result`
- `build_result`
- `review_result`
- `consolidated_result`
- `user_override`

Allowed evidence states:

- `missing`
- `present`
- `passed`
- `failed`
- `overridden`

## Runtime Rules

- A required criterion is complete only when matching evidence is `present`, `passed`, or `overridden`.
- Failed evidence blocks completion even when other evidence is present.
- User override requires a non-empty override reason and is displayed as an override state.
- Tool statuses may infer evidence from file diff, file write preview, terminal exit, build/test output, team task result, and team agent result.
- Cached `HanakoToolStatus` JSON may include `taskCompletionEvidence`; legacy cached tool status without this field must still parse and render.
- Artifact refs and summaries must be bounded and redact obvious token, password, API key, and secret assignments.

## UI Rules

- `NbgAgentMessageUi` must render a compact completion evidence strip on visible tool cards when evidence exists or can be inferred.
- Tool details must render evidence detail lines before inline previews.
- Evidence cards must use the same state colors for terminal/file/team evidence and must not hide failed or missing evidence behind success styling.
- Evidence display is advisory in v1; it must not block chat send or corrupt legacy restored history.

## Test Oracle

Unit tests:

- `NbgTaskCompletionEvidenceTest.reviewRequiresEvidenceBeforeCompletionUnlessUserOverrides`
- `NbgTaskCompletionEvidenceTest.failedEvidenceBlocksCompletionEvenWhenPresent`
- `NbgTaskCompletionEvidenceTest.infersBuildTestDiffFileAndTeamEvidenceFromToolStatus`
- `NbgTaskCompletionEvidenceTest.cachedToolStatusRoundTripsTaskCompletionEvidence`
- `AndroidManifestBehaviorTest.taskCompletionEvidenceContractIsDocumentedAndWired`

Manual tests:

- Run a file edit and confirm diff/file evidence appears on the tool card.
- Run a passing test command and confirm test evidence is `passed`.
- Run a failing build command and confirm build evidence is `failed`.
- Open restored history and confirm legacy tool cards still render.

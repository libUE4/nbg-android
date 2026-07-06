package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgLearnedSkillDraftPolicyTest {
  @Test
  fun learnedSkillDraftRequiresCompletionTargetSourceHashAndPermissionEvidence() {
    val completion = NbgTaskCompletionEvidenceBundle(
      contractId = "task-skill",
      title = "可复用流程",
      criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.TestResult, "测试")),
      evidence = listOf(
        nbgTaskEvidence(
          kind = NbgTaskCompletionCriterionKind.TestResult,
          state = NbgTaskCompletionEvidenceState.Passed,
          label = "测试",
          summary = "BUILD SUCCESSFUL",
        ),
      ),
    )

    val review = nbgReviewLearnedSkillDraft(
      skillName = "android-build-review",
      targetPath = "/root/.hanako/skills/android-build-review/SKILL.md",
      sourceTaskId = "task-skill",
      completionEvidence = completion,
      draftSha256 = "a".repeat(64),
      permissionTier = NbgPermissionRiskTier.High,
    )

    assertEquals(NBG_LEARNED_SKILL_DRAFT_POLICY_VERSION, review.policyVersion)
    assertEquals(NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE, review.requiredEvidence)
    assertEquals(NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE, review.presentEvidence)
    assertTrue(review.allowDraft)
    assertFalse("generated Skills must not install before review", review.allowInstall)
    assertFalse("generated Skills must stay disabled before review", review.allowEnable)
    assertTrue(review.requiresReview)
    assertEquals(NbgPermissionRiskTier.High, review.permissionTier)
    assertEquals("[local-path]", review.targetPathLabel)
  }

  @Test
  fun learnedSkillDraftBlocksMissingEvidenceAndKeepsDangerousDraftReviewOnly() {
    val incomplete = nbgReviewLearnedSkillDraft(
      skillName = "unsafe-delete-helper",
      targetPath = "relative/SKILL.md",
      sourceTaskId = "",
      completionEvidence = null,
      draftSha256 = "not-a-sha",
      permissionTier = NbgPermissionRiskTier.Dangerous,
    )
    val dangerousDraft = nbgReviewLearnedSkillDraft(
      skillName = "dangerous-reviewed-draft",
      targetPath = "/root/.hanako/skills/danger/SKILL.md",
      sourceTaskId = "task-danger",
      completionEvidence = NbgTaskCompletionEvidenceBundle(
        contractId = "task-danger",
        title = "危险流程",
        criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.CommandExit, "命令")),
        evidence = listOf(
          nbgTaskEvidence(
            kind = NbgTaskCompletionCriterionKind.CommandExit,
            state = NbgTaskCompletionEvidenceState.Passed,
            label = "命令",
          ),
        ),
      ),
      draftSha256 = "b".repeat(64),
      permissionTier = NbgPermissionRiskTier.Dangerous,
    )

    assertFalse(incomplete.allowDraft)
    assertFalse(incomplete.allowInstall)
    assertFalse(incomplete.allowEnable)
    assertTrue(incomplete.reason.contains("缺少证据"))
    assertFalse(incomplete.presentEvidence.contains("completion_evidence_complete"))
    assertFalse(incomplete.presentEvidence.contains("source_task"))
    assertFalse(incomplete.presentEvidence.contains("target_path_reviewed"))
    assertFalse(incomplete.presentEvidence.contains("draft_sha256"))

    assertTrue(dangerousDraft.allowDraft)
    assertFalse(dangerousDraft.allowInstall)
    assertFalse(dangerousDraft.allowEnable)
    assertTrue(dangerousDraft.reason.contains("危险权限"))
  }
}

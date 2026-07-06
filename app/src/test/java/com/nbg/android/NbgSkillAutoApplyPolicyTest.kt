package com.nbg.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgSkillAutoApplyPolicyTest {
  @Test
  fun lowAndMediumSkillDraftsAutoInstallEnableWhileHighRequiresReviewAndDangerousBlocks() {
    val completion = NbgTaskCompletionEvidenceBundle(
      contractId = "task-auto-skill",
      title = "Auto Skill",
      criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.TestResult, "tests")),
      evidence = listOf(nbgTaskEvidence(NbgTaskCompletionCriterionKind.TestResult, NbgTaskCompletionEvidenceState.Passed)),
    )

    val low = review(NbgPermissionRiskTier.Low, completion)
    val medium = review(NbgPermissionRiskTier.Medium, completion)
    val high = review(NbgPermissionRiskTier.High, completion)
    val dangerous = review(NbgPermissionRiskTier.Dangerous, completion)

    assertTrue(low.allowInstall)
    assertTrue(low.allowEnable)
    assertTrue(medium.allowInstall)
    assertTrue(medium.allowEnable)
    assertFalse(high.allowInstall)
    assertFalse(high.allowEnable)
    assertFalse(dangerous.allowInstall)
    assertFalse(dangerous.allowEnable)
  }

  private fun review(
    tier: NbgPermissionRiskTier,
    completion: NbgTaskCompletionEvidenceBundle,
  ): NbgLearnedSkillDraftReview =
    nbgReviewLearnedSkillDraft(
      skillName = "auto-skill-${tier.wireName}",
      targetPath = "/root/.hanako/skills/auto-skill-${tier.wireName}/SKILL.md",
      sourceTaskId = "task-${tier.wireName}",
      completionEvidence = completion,
      draftSha256 = "a".repeat(64),
      permissionTier = tier,
    )
}

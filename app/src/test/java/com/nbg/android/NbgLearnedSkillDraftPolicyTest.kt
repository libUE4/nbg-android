package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject
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
      permissionTier = NbgPermissionRiskTier.Medium,
    )

    assertEquals(NBG_LEARNED_SKILL_DRAFT_POLICY_VERSION, review.policyVersion)
    assertEquals(NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE, review.requiredEvidence)
    assertEquals(NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE, review.presentEvidence)
    assertTrue(review.allowDraft)
    assertTrue("low/medium generated Skills may install automatically with complete evidence", review.allowInstall)
    assertTrue("low/medium generated Skills may enable automatically with complete evidence", review.allowEnable)
    assertFalse(review.requiresReview)
    assertEquals(NbgPermissionRiskTier.Medium, review.permissionTier)
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

  @Test
  fun learnedSkillDraftQueueAllowsSafeAutoApplyAndKeepsHighRiskReviewOnly() {
    val completion = NbgTaskCompletionEvidenceBundle(
      contractId = "task-skill",
      title = "复用构建流程",
      criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.BuildResult, "构建")),
      evidence = listOf(
        nbgTaskEvidence(
          kind = NbgTaskCompletionCriterionKind.BuildResult,
          state = NbgTaskCompletionEvidenceState.Passed,
          label = "构建",
          summary = "assembleDebug passed",
        ),
      ),
    )
    val safe = nbgLearnedSkillDraftQueueEntry(
      id = "draft-1",
      skillName = "android-build",
      description = "Run Gradle verification before completion.",
      targetPath = "/root/.hanako/skills/android-build/SKILL.md",
      sourceTaskId = "task-skill",
      sourceTaskTitle = "完成 Android 构建检查",
      completionEvidence = completion,
      draftSha256 = "c".repeat(64),
      permissionTier = NbgPermissionRiskTier.Medium,
      createdAtMs = 100L,
      updatedAtMs = 200L,
    )
    val highRisk = nbgLearnedSkillDraftQueueEntry(
      id = "draft-high",
      skillName = "android-build-high-risk",
      targetPath = "/root/.hanako/skills/android-build-high-risk/SKILL.md",
      sourceTaskId = "task-high",
      completionEvidence = completion,
      draftSha256 = "d".repeat(64),
      permissionTier = NbgPermissionRiskTier.High,
      createdAtMs = 250L,
      updatedAtMs = 250L,
    )
    val blocked = nbgLearnedSkillDraftQueueEntry(
      id = "draft-2",
      skillName = "unsafe-delete-helper",
      targetPath = "relative/SKILL.md",
      sourceTaskId = "",
      completionEvidence = null,
      draftSha256 = "bad",
      permissionTier = NbgPermissionRiskTier.Dangerous,
      createdAtMs = 300L,
      updatedAtMs = 300L,
    )

    val queue = nbgBuildLearnedSkillDraftQueue(listOf(blocked, highRisk, safe))

    assertEquals(NBG_LEARNED_SKILL_DRAFT_QUEUE_VERSION, queue.modelVersion)
    assertEquals(3, queue.visibleEntries.size)
    assertEquals("android-build", queue.visibleEntries.first().skillName)
    assertEquals(2, queue.pendingReviewCount)
    assertEquals(1, queue.blockedCount)
    assertEquals(1, queue.dangerousCount)
    assertEquals(1, queue.installableCount)
    assertEquals(1, queue.enableableCount)
    assertEquals(NbgLearnedSkillDraftStatus.PendingReview, safe.status)
    assertTrue(safe.review.allowDraft)
    assertTrue(safe.review.allowInstall)
    assertTrue(safe.review.allowEnable)
    assertTrue(highRisk.review.allowDraft)
    assertFalse(highRisk.review.allowInstall)
    assertFalse(highRisk.review.allowEnable)
    assertEquals(NbgLearnedSkillDraftStatus.BlockedMissingEvidence, blocked.status)
    assertTrue(blocked.missingEvidence.contains("completion_evidence_complete"))
    assertTrue(blocked.missingEvidence.contains("target_path_reviewed"))
  }

  @Test
  fun parseLearnedSkillDraftQueueNormalizesUntrustedBackendDrafts() {
    val completion = NbgTaskCompletionEvidenceBundle(
      contractId = "task-json",
      title = "JSON 草稿",
      criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.TestResult, "测试")),
      evidence = listOf(
        nbgTaskEvidence(
          kind = NbgTaskCompletionCriterionKind.TestResult,
          state = NbgTaskCompletionEvidenceState.Passed,
          label = "测试",
        ),
      ),
    )
    val queue = parseNbgLearnedSkillDraftQueue(
      JSONArray().put(
        JSONObject()
          .put("id", "json-draft")
          .put("skillName", "json-skill")
          .put("targetPath", "/root/.hanako/skills/json-skill/SKILL.md")
          .put("sourceTaskId", "task-json")
          .put("completionEvidence", completion.toJson())
          .put("draftSha256", "e".repeat(64))
          .put("permissionTier", "dangerous")
          .put("status", "pending")
          .put("createdAtMs", 10L)
          .put("updatedAtMs", 20L),
      ),
    )

    val draft = queue.visibleEntries.single()
    assertEquals("json-draft", draft.id)
    assertEquals(NbgLearnedSkillDraftStatus.PendingReview, draft.status)
    assertEquals(NbgPermissionRiskTier.Dangerous, draft.review.permissionTier)
    assertTrue(draft.review.allowDraft)
    assertFalse(draft.review.allowInstall)
    assertFalse(draft.review.allowEnable)
    assertEquals(emptyList<String>(), draft.missingEvidence)
    assertEquals(1, queue.dangerousCount)
  }
}

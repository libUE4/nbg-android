package com.nbg.android

internal const val NBG_LEARNED_SKILL_DRAFT_POLICY_VERSION = "nbg-learned-skill-draft-v1"

internal val NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE: List<String> =
  listOf(
    "completion_evidence_complete",
    "source_task",
    "target_path_reviewed",
    "draft_sha256",
    "permission_tier_recorded",
  )

internal data class NbgLearnedSkillDraftReview(
  val policyVersion: String,
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

internal fun nbgReviewLearnedSkillDraft(
  skillName: String,
  targetPath: String,
  sourceTaskId: String,
  completionEvidence: NbgTaskCompletionEvidenceBundle?,
  draftSha256: String,
  permissionTier: NbgPermissionRiskTier,
): NbgLearnedSkillDraftReview {
  val cleanSkillName = skillName.trim()
  val cleanTaskId = sourceTaskId.trim().take(120)
  val sourceReview = nbgReviewSkillInstallSource(targetPath)
  val cleanSha = draftSha256.trim().lowercase()
  val completionReview = completionEvidence?.review
  val completionOk = completionReview?.complete == true
  val targetOk = sourceReview.allowInstall &&
    sourceReview.sourceKind in setOf(NbgSkillSourceKind.LocalPath, NbgSkillSourceKind.UserInstalled)
  val shaOk = cleanSha.matches(Regex("[a-f0-9]{64}"))
  val presentEvidence = buildList {
    if (completionOk) add("completion_evidence_complete")
    if (cleanTaskId.isNotBlank()) add("source_task")
    if (targetOk) add("target_path_reviewed")
    if (shaOk) add("draft_sha256")
    add("permission_tier_recorded")
  }
  val missing = NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE - presentEvidence.toSet()
  val allowDraft = cleanSkillName.isNotBlank() && missing.isEmpty()
  return NbgLearnedSkillDraftReview(
    policyVersion = NBG_LEARNED_SKILL_DRAFT_POLICY_VERSION,
    allowDraft = allowDraft,
    allowInstall = false,
    allowEnable = false,
    requiresReview = true,
    requiredEvidence = NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE,
    presentEvidence = presentEvidence,
    permissionTier = permissionTier,
    sourceTaskId = cleanTaskId,
    targetPathLabel = sourceReview.redactedLocation.ifBlank { "[skill-draft-path]" },
    reason = when {
      cleanSkillName.isBlank() -> "Skill 名称不能为空；学习结果只能进入草稿，不能直接安装或启用。"
      missing.isNotEmpty() -> "Skill 草稿缺少证据：${missing.joinToString(", ")}；学习结果保持未安装状态。"
      permissionTier == NbgPermissionRiskTier.Dangerous -> "Skill 草稿可保存但包含危险权限；安装和启用必须经过强确认。"
      else -> "Skill 草稿证据完整；安装和启用仍需用户审核确认。"
    },
  )
}

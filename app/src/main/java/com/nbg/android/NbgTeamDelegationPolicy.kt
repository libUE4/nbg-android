package com.nbg.android

internal const val NBG_TEAM_DELEGATION_POLICY_VERSION = "nbg-team-delegation-v1"

internal enum class NbgTeamDelegationTemplate(
  val templateId: String,
  val title: String,
  val allowedTools: List<String>,
  val maxSubtasks: Int,
  val maxMinutes: Int,
  val permissionTier: NbgPermissionRiskTier,
) {
  DiffReview(
    templateId = "diff_review",
    title = "审查 Diff",
    allowedTools = listOf("read", "diff", "memory_read"),
    maxSubtasks = 3,
    maxMinutes = 8,
    permissionTier = NbgPermissionRiskTier.Low,
  ),
  Verification(
    templateId = "verification",
    title = "运行验证",
    allowedTools = listOf("read", "terminal", "test", "build"),
    maxSubtasks = 3,
    maxMinutes = 12,
    permissionTier = NbgPermissionRiskTier.Medium,
  ),
  ModuleInspection(
    templateId = "module_inspection",
    title = "检查模块",
    allowedTools = listOf("read", "search", "memory_read"),
    maxSubtasks = 4,
    maxMinutes = 10,
    permissionTier = NbgPermissionRiskTier.Low,
  ),
  SecurityPass(
    templateId = "security_pass",
    title = "安全检查",
    allowedTools = listOf("read", "diff", "permission_review", "memory_read"),
    maxSubtasks = 4,
    maxMinutes = 10,
    permissionTier = NbgPermissionRiskTier.High,
  ),
}

internal data class NbgTeamDelegationReview(
  val policyVersion: String,
  val allowStart: Boolean,
  val template: NbgTeamDelegationTemplate?,
  val title: String,
  val allowedTools: List<String>,
  val maxSubtasks: Int,
  val maxMinutes: Int,
  val permissionTier: NbgPermissionRiskTier,
  val requiresConfirmation: Boolean,
  val supportsCancel: Boolean,
  val consolidationRequired: Boolean,
  val reason: String,
)

internal fun nbgReviewTeamDelegationRequest(prompt: String): NbgTeamDelegationReview {
  val text = prompt.trim()
  val template = nbgInferTeamDelegationTemplate(text)
  if (text.isBlank()) {
    return nbgBlockedTeamDelegation("团队任务不能为空")
  }
  if (template == null) {
    return nbgBlockedTeamDelegation(
      "Agents team 目前只支持 bounded 模板：审查 Diff、运行验证、检查模块、安全检查。",
    )
  }
  return NbgTeamDelegationReview(
    policyVersion = NBG_TEAM_DELEGATION_POLICY_VERSION,
    allowStart = true,
    template = template,
    title = template.title,
    allowedTools = template.allowedTools,
    maxSubtasks = template.maxSubtasks,
    maxMinutes = template.maxMinutes,
    permissionTier = template.permissionTier,
    requiresConfirmation = template.permissionTier.requiresConfirmation,
    supportsCancel = true,
    consolidationRequired = true,
    reason = "允许启动 ${template.title}；最多 ${template.maxSubtasks} 个子任务，${template.maxMinutes} 分钟预算，完成后必须汇总结果。",
  )
}

private fun nbgBlockedTeamDelegation(reason: String): NbgTeamDelegationReview =
  NbgTeamDelegationReview(
    policyVersion = NBG_TEAM_DELEGATION_POLICY_VERSION,
    allowStart = false,
    template = null,
    title = "",
    allowedTools = emptyList(),
    maxSubtasks = 0,
    maxMinutes = 0,
    permissionTier = NbgPermissionRiskTier.High,
    requiresConfirmation = true,
    supportsCancel = true,
    consolidationRequired = true,
    reason = reason,
  )

private fun nbgInferTeamDelegationTemplate(prompt: String): NbgTeamDelegationTemplate? {
  val text = prompt.lowercase()
  return when {
    text.contains("review diff") || text.contains("diff review") || text.contains("审查 diff") || text.contains("检查 diff") ->
      NbgTeamDelegationTemplate.DiffReview
    text.contains("run verification") || text.contains("verify") || text.contains("verification") ||
      text.contains("test") || text.contains("build") || text.contains("运行验证") || text.contains("测试") || text.contains("构建") ->
      NbgTeamDelegationTemplate.Verification
    text.contains("inspect module") || text.contains("module inspection") || text.contains("检查模块") || text.contains("分析模块") ->
      NbgTeamDelegationTemplate.ModuleInspection
    text.contains("security") || text.contains("安全") || text.contains("permission") || text.contains("权限") ->
      NbgTeamDelegationTemplate.SecurityPass
    else -> null
  }
}

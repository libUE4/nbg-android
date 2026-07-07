package com.nbg.android

data class NbgTeamParallelPlan(
  val maxParallelAgents: Int,
  val maxRetries: Int,
  val budgetMinutes: Int,
  val isolatedContext: Boolean,
  val consolidationRequired: Boolean,
) {
  val label: String
    get() = "$maxParallelAgents 并行 · retry $maxRetries · ${budgetMinutes}m"
}

internal fun nbgBuildTeamParallelPlan(review: NbgTeamDelegationReview): NbgTeamParallelPlan =
  NbgTeamParallelPlan(
    maxParallelAgents = review.maxSubtasks.coerceIn(1, 6),
    maxRetries = when (review.permissionTier) {
      NbgPermissionRiskTier.Low -> 2
      NbgPermissionRiskTier.Medium -> 1
      else -> 0
    },
    budgetMinutes = review.maxMinutes.coerceIn(3, 30),
    isolatedContext = true,
    consolidationRequired = review.consolidationRequired,
  )

internal fun List<HanakoTeamAgentStatus>.withParallelPlan(plan: NbgTeamParallelPlan): List<HanakoTeamAgentStatus> =
  mapIndexed { index, agent ->
    if (index < plan.maxParallelAgents) {
      agent.copy(
        status = if (agent.status == "idle" || agent.status == "queued") "queued" else agent.status,
        summary = listOf(agent.summary, plan.label).filter { it.isNotBlank() }.joinToString(" · "),
      )
    } else {
      agent.copy(status = "idle", summary = "等待预算释放 · ${plan.label}")
    }
  }

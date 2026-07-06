package com.nbg.android

internal const val NBG_SKILL_CURATOR_MODEL_VERSION = "nbg-skill-curator-v1"

internal data class NbgSkillCuratorSummary(
  val modelVersion: String = NBG_SKILL_CURATOR_MODEL_VERSION,
  val visibleCount: Int,
  val enabledCount: Int,
  val disabledCount: Int,
  val requiresReviewCount: Int,
  val deletableCount: Int,
  val bundledTrustedCount: Int,
  val unverifiedExternalCount: Int,
  val curatorStatus: String,
  val autoDeleteAllowed: Boolean = false,
)

internal fun nbgBuildSkillCuratorSummary(snapshot: HanakoSkillsSnapshot): NbgSkillCuratorSummary {
  val visible = snapshot.visibleSkills
  val reviews = visible.map { nbgSkillSummarySourceReview(it) }
  val requiresReviewCount = reviews.count { it.requiresReview }
  val unverifiedCount = reviews.count { it.trustTier == NbgSkillTrustTier.UnverifiedExternal }
  return NbgSkillCuratorSummary(
    visibleCount = visible.size,
    enabledCount = visible.count { it.enabled },
    disabledCount = visible.count { !it.enabled },
    requiresReviewCount = requiresReviewCount,
    deletableCount = visible.count { it.deletable },
    bundledTrustedCount = reviews.count { it.trustTier == NbgSkillTrustTier.TrustedBundled },
    unverifiedExternalCount = unverifiedCount,
    curatorStatus = when {
      visible.isEmpty() -> "没有可见 Skill"
      unverifiedCount > 0 -> "$unverifiedCount 个未验证来源"
      requiresReviewCount > 0 -> "$requiresReviewCount 个需要复核"
      else -> "来源健康"
    },
    autoDeleteAllowed = false,
  )
}

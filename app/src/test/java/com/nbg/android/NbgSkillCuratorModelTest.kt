package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgSkillCuratorModelTest {
  @Test
  fun curatorSummarizesSkillGovernanceWithoutAutoDelete() {
    val summary = nbgBuildSkillCuratorSummary(
      HanakoSkillsSnapshot(
        skills = listOf(
          HanakoSkillSummary(name = "nbg-engineering-core", source = "builtin", enabled = true, readonly = true),
          HanakoSkillSummary(name = "custom-review", source = "user", enabled = false, filePath = "/root/skills/custom/SKILL.md"),
          HanakoSkillSummary(name = "external-risk", source = "external", enabled = true, externalPath = "/root/external/SKILL.md"),
          HanakoSkillSummary(name = "hidden", hidden = true, source = "user"),
        ),
      ),
    )

    assertEquals(NBG_SKILL_CURATOR_MODEL_VERSION, summary.modelVersion)
    assertEquals(3, summary.visibleCount)
    assertEquals(2, summary.enabledCount)
    assertEquals(1, summary.disabledCount)
    assertEquals(2, summary.requiresReviewCount)
    assertEquals(1, summary.deletableCount)
    assertEquals(1, summary.bundledTrustedCount)
    assertEquals(1, summary.unverifiedExternalCount)
    assertTrue(summary.curatorStatus.contains("未验证来源"))
    assertFalse(summary.autoDeleteAllowed)
  }

  @Test
  fun curatorReportsEmptyAndHealthySources() {
    val empty = nbgBuildSkillCuratorSummary(HanakoSkillsSnapshot())
    val healthy = nbgBuildSkillCuratorSummary(
      HanakoSkillsSnapshot(
        skills = listOf(
          HanakoSkillSummary(name = "nbg-engineering-core", source = "builtin", enabled = true, readonly = true),
        ),
      ),
    )

    assertEquals("没有可见 Skill", empty.curatorStatus)
    assertEquals("来源健康", healthy.curatorStatus)
    assertFalse(empty.autoDeleteAllowed)
    assertFalse(healthy.autoDeleteAllowed)
  }
}

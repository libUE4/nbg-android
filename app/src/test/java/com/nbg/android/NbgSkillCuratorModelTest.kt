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
    assertFalse(summary.autoEnableAllowed)
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

  @Test
  fun curatorMetadataArchivesOnlyDisabledSkillsAndTracksUsage() {
    val snapshot = HanakoSkillsSnapshot(
      skills = listOf(
        HanakoSkillSummary(name = "enabled-risk", source = "external", enabled = true),
        HanakoSkillSummary(name = "quiet-user", source = "user", enabled = false, filePath = "/root/skills/quiet/SKILL.md"),
      ),
    )
    val metadata = nbgArchiveSkillCuratorEntry(
      nbgRecordSkillCuratorUse(
        nbgRecordSkillCuratorUse(NbgSkillCuratorMetadata(), "quiet-user", nowMs = 10L),
        "quiet-user",
        nowMs = 20L,
      ),
      "quiet-user",
      nowMs = 30L,
    )

    val filtered = nbgApplySkillCuratorMetadata(snapshot, metadata)
    val summary = nbgBuildSkillCuratorSummary(snapshot, metadata)
    val archived = nbgSkillCuratorArchivedSkills(snapshot, metadata)

    assertEquals(listOf("enabled-risk"), filtered.visibleSkills.map { it.name })
    assertEquals(1, summary.visibleCount)
    assertEquals(1, summary.archivedCount)
    assertEquals(2, summary.usageEventCount)
    assertEquals("quiet-user", archived.single().skillName)
    assertEquals(2, archived.single().useCount)
  }

  @Test
  fun curatorDoesNotHideEnabledArchivedSkillAndCanRestore() {
    val snapshot = HanakoSkillsSnapshot(
      skills = listOf(HanakoSkillSummary(name = "still-on", source = "user", enabled = true)),
    )
    val archived = nbgArchiveSkillCuratorEntry(NbgSkillCuratorMetadata(), "still-on", nowMs = 40L)

    val filteredWhileEnabled = nbgApplySkillCuratorMetadata(snapshot, archived)
    val restored = nbgRestoreSkillCuratorEntry(archived, "still-on")

    assertEquals(listOf("still-on"), filteredWhileEnabled.visibleSkills.map { it.name })
    assertEquals(0, nbgBuildSkillCuratorSummary(snapshot, archived).archivedCount)
    assertFalse(restored.statsFor("still-on")?.archived == true)
  }
}

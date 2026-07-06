package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgToolsetControlTest {
  @Test
  fun defaultsKeepCoreToolsetsOnAndExpertReviewOff() {
    val preferences = NbgChatPreferences()

    assertTrue(preferences.nbgToolsetEnabled(NbgToolsetId.Terminal))
    assertTrue(preferences.nbgToolsetEnabled(NbgToolsetId.Memory))
    assertTrue(preferences.nbgToolsetEnabled(NbgToolsetId.Skills))
    assertTrue(preferences.nbgToolsetEnabled(NbgToolsetId.AgentsTeam))
    assertFalse(preferences.nbgToolsetEnabled(NbgToolsetId.ExpertReview))
  }

  @Test
  fun togglingAgentsTeamMirrorsLegacyMultiAgentFlag() {
    val disabled = NbgChatPreferences().withNbgToolsetEnabled(NbgToolsetId.AgentsTeam, false)
    val enabled = disabled.withNbgToolsetEnabled(NbgToolsetId.AgentsTeam, true)

    assertFalse(disabled.multiAgentEnabled)
    assertFalse(disabled.nbgToolsetEnabled(NbgToolsetId.AgentsTeam))
    assertTrue(enabled.multiAgentEnabled)
    assertTrue(enabled.nbgToolsetEnabled(NbgToolsetId.AgentsTeam))
  }

  @Test
  fun buildRowsCombinePreferencesCapabilityHealthAndDerivedSignals() {
    val preferences = NbgChatPreferences()
      .withNbgToolsetEnabled(NbgToolsetId.ExpertReview, true)
      .withNbgToolsetEnabled(NbgToolsetId.Mcp, false)
    val rows = nbgBuildToolsetControlRows(
      preferences = preferences,
      capabilities = nbgBuildCapabilityRegistry(
        connected = true,
        connectionLabel = "HanakoPro 已连接",
        lastError = "",
        savedUrlApiCount = 1,
        terminalReadiness = listOf(
          TerminalReadinessSnapshot(
            readiness = TerminalReadiness.Ready,
            phase = TerminalStartupPhase.Ready,
          ),
        ),
        mcpState = HanakoMcpState(enabled = false),
        mcpLoading = false,
        mcpError = null,
        skillsSnapshot = HanakoSkillsSnapshot(
          skills = listOf(HanakoSkillSummary(name = "nbg-engineering-core", enabled = true)),
        ),
        skillsLoading = false,
        skillsError = null,
        memoryState = HanakoMemoryState(count = 3, enabledCount = 2),
        memoryLoading = false,
        memoryError = null,
        fileShareState = null,
        petState = NbgPetStoreState(
          installedPets = listOf(nbgDefaultPetSummary()),
          currentPetSlug = NBG_DEFAULT_PET_SLUG,
          hidden = false,
        ),
        petDexLoading = false,
        petDexError = null,
      ),
      sessionCount = 4,
      savedUrlApiCount = 1,
      compressionAvailable = false,
    )

    val terminal = rows.single { it.id == NbgToolsetId.Terminal }
    val mcp = rows.single { it.id == NbgToolsetId.Mcp }
    val expert = rows.single { it.id == NbgToolsetId.ExpertReview }
    val search = rows.single { it.id == NbgToolsetId.SessionSearch }
    val compression = rows.single { it.id == NbgToolsetId.ContextCompression }

    assertEquals(NbgCapabilityHealth.Healthy, terminal.health)
    assertFalse(mcp.enabled)
    assertEquals("关闭", mcp.status)
    assertTrue(expert.enabled)
    assertEquals(NbgCapabilityHealth.Degraded, expert.health)
    assertTrue(expert.status.contains("需要两个模型"))
    assertEquals("4 会话", search.status)
    assertEquals(NbgCapabilityHealth.Unknown, compression.health)
  }
}

package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgCapabilityRegistryTest {
  private companion object {
    const val RAW_MEMORY_ERROR =
      "raw memory backend failure /api/plugins/memory/state?query=private-memory-query /api/plugins/memory/items/memory-item-secret-id title=private-memory-title tag=private-memory-tag"
  }

  @Test
  fun registryExposesRequiredCoreCapabilities() {
    val registry = nbgBuildCapabilityRegistry(
      connected = true,
      connectionLabel = "HanakoPro 已连接",
      lastError = "",
      savedUrlApiCount = 2,
      terminalReadiness = listOf(
        TerminalReadinessSnapshot(
          readiness = TerminalReadiness.Ready,
          phase = TerminalStartupPhase.Ready,
        ),
      ),
      mcpState = HanakoMcpState(
        enabled = true,
        connectors = listOf(HanakoMcpConnector(id = "local", name = "Local", status = "running")),
      ),
      mcpLoading = false,
      mcpError = null,
      skillsSnapshot = HanakoSkillsSnapshot(
        skills = listOf(HanakoSkillSummary(name = "nbg-engineering-core", enabled = true)),
      ),
      skillsLoading = false,
      skillsError = null,
      memoryState = HanakoMemoryState(count = 1, enabledCount = 1),
      memoryLoading = false,
      memoryError = null,
      fileShareState = NbgFileShareServerState(running = true, localUrl = "127.0.0.1:43211"),
      petState = NbgPetStoreState(installedPets = listOf(nbgDefaultPetSummary()), currentPetSlug = NBG_DEFAULT_PET_SLUG, hidden = false),
      petDexLoading = false,
      petDexError = null,
    )

    assertEquals(
      listOf("terminal", "hanako", "url_api", "mcp", "skills", "memory", "ftp", "pets", "feedback_export"),
      registry.capabilities.map { it.id },
    )
    assertEquals(NbgCapabilityHealth.Healthy, registry.byId("mcp")?.health)
    assertEquals(NbgCapabilityHealth.Healthy, registry.byId("terminal")?.health)
    assertEquals("1 就绪", registry.byId("terminal")?.status)
    assertEquals("1/1 运行", registry.byId("mcp")?.status)
    assertEquals("127.0.0.1:43211", registry.byId("ftp")?.status)
    assertTrue(registry.capabilities.all { it.diagnosticsAction.isNotBlank() })
  }

  @Test
  fun registrySurfacesReadableFailureStates() {
    val registry = nbgBuildCapabilityRegistry(
      connected = false,
      connectionLabel = "HanakoPro 未连接",
      lastError = "health timeout",
      savedUrlApiCount = 0,
      terminalReadiness = listOf(
        TerminalReadinessSnapshot(
          readiness = TerminalReadiness.Failed,
          phase = TerminalStartupPhase.Failed,
          diagnostic = "exit_code=1",
        ),
      ),
      mcpState = HanakoMcpState(enabled = true),
      mcpLoading = false,
      mcpError = "connector failed",
      skillsSnapshot = HanakoSkillsSnapshot(),
      skillsLoading = false,
      skillsError = null,
      memoryState = HanakoMemoryState(),
      memoryLoading = false,
      memoryError = "memory unavailable",
      fileShareState = NbgFileShareServerState(running = false, message = "bind failed"),
      petState = NbgPetStoreState(installedPets = emptyList(), currentPetSlug = NBG_DEFAULT_PET_SLUG, hidden = false),
      petDexLoading = false,
      petDexError = "manifest failed",
    )

    assertEquals(NbgCapabilityHealth.Failed, registry.byId("terminal")?.health)
    assertEquals("1 失败", registry.byId("terminal")?.status)
    assertEquals("exit_code=1", registry.byId("terminal")?.lastError)
    assertEquals(NbgCapabilityHealth.Failed, registry.byId("hanako")?.health)
    assertEquals("health timeout", registry.byId("hanako")?.lastError)
    assertEquals(NbgCapabilityHealth.Failed, registry.byId("mcp")?.health)
    assertEquals("connector failed", registry.byId("mcp")?.lastError)
    assertEquals(NbgCapabilityHealth.Failed, registry.byId("memory")?.health)
    assertEquals(NbgCapabilityHealth.Failed, registry.byId("ftp")?.health)
    assertEquals(NbgCapabilityHealth.Degraded, registry.byId("pets")?.health)
    assertEquals("终端 失败", registry.summaryLabel)
  }

  @Test
  fun registrySummarizesHanakoCapabilityWhenBackendErrorComesFromMemory() {
    val registry = nbgBuildCapabilityRegistry(
      connected = false,
      connectionLabel = "HanakoPro 未连接",
      lastError = RAW_MEMORY_ERROR,
      savedUrlApiCount = 0,
      mcpState = HanakoMcpState(),
      mcpLoading = false,
      mcpError = null,
      skillsSnapshot = HanakoSkillsSnapshot(),
      skillsLoading = false,
      skillsError = null,
      memoryState = HanakoMemoryState(),
      memoryLoading = false,
      memoryError = RAW_MEMORY_ERROR,
      fileShareState = null,
      petState = NbgPetStoreState(installedPets = listOf(nbgDefaultPetSummary()), currentPetSlug = NBG_DEFAULT_PET_SLUG, hidden = false),
      petDexLoading = false,
      petDexError = null,
    )

    val exportedErrors = registry.capabilities.joinToString("\n") { it.lastError }

    assertEquals("memory_error_present", registry.byId("hanako")?.lastError)
    assertEquals("memory_error_present", registry.byId("memory")?.lastError)
    assertFalse(exportedErrors.contains("private-memory-query"))
    assertFalse(exportedErrors.contains("memory-item-secret-id"))
    assertFalse(exportedErrors.contains("private-memory-title"))
    assertFalse(exportedErrors.contains("private-memory-tag"))
    assertFalse(exportedErrors.contains("raw memory backend failure"))
  }

  @Test
  fun terminalCapabilitySurfacesInstallingReadinessAsUnknown() {
    val registry = nbgBuildCapabilityRegistry(
      connected = true,
      connectionLabel = "HanakoPro 已连接",
      lastError = "",
      savedUrlApiCount = 1,
      terminalReadiness = listOf(
        TerminalReadinessSnapshot(
          readiness = TerminalReadiness.Installing,
          phase = TerminalStartupPhase.NodeRuntime,
          diagnostic = "Installing built-in Node.js runtime",
        ),
      ),
      mcpState = HanakoMcpState(
        enabled = true,
        connectors = listOf(HanakoMcpConnector(id = "local", name = "Local", status = "running")),
      ),
      mcpLoading = false,
      mcpError = null,
      skillsSnapshot = HanakoSkillsSnapshot(
        skills = listOf(HanakoSkillSummary(name = "nbg-engineering-core", enabled = true)),
      ),
      skillsLoading = false,
      skillsError = null,
      memoryState = HanakoMemoryState(count = 1, enabledCount = 1),
      memoryLoading = false,
      memoryError = null,
      fileShareState = NbgFileShareServerState(running = true, localUrl = "127.0.0.1:43211"),
      petState = NbgPetStoreState(installedPets = listOf(nbgDefaultPetSummary()), currentPetSlug = NBG_DEFAULT_PET_SLUG, hidden = false),
      petDexLoading = false,
      petDexError = null,
    )

    assertEquals(NbgCapabilityHealth.Unknown, registry.byId("terminal")?.health)
    assertEquals("1 启动中", registry.byId("terminal")?.status)
    assertEquals("终端 未知", registry.summaryLabel)
  }

  @Test
  fun terminalCapabilitySurfacesMultiTabReadinessCounts() {
    val registry = nbgBuildCapabilityRegistry(
      connected = true,
      connectionLabel = "HanakoPro 已连接",
      lastError = "",
      savedUrlApiCount = 1,
      terminalReadiness = listOf(
        TerminalReadinessSnapshot(readiness = TerminalReadiness.Ready, phase = TerminalStartupPhase.Ready),
        TerminalReadinessSnapshot(readiness = TerminalReadiness.Ready, phase = TerminalStartupPhase.Ready),
        TerminalReadinessSnapshot(readiness = TerminalReadiness.Installing, phase = TerminalStartupPhase.NodeRuntime),
        TerminalReadinessSnapshot(
          readiness = TerminalReadiness.Failed,
          phase = TerminalStartupPhase.Failed,
          diagnostic = "exit_code=9",
        ),
      ),
      mcpState = HanakoMcpState(
        enabled = true,
        connectors = listOf(HanakoMcpConnector(id = "local", name = "Local", status = "running")),
      ),
      mcpLoading = false,
      mcpError = null,
      skillsSnapshot = HanakoSkillsSnapshot(
        skills = listOf(HanakoSkillSummary(name = "nbg-engineering-core", enabled = true)),
      ),
      skillsLoading = false,
      skillsError = null,
      memoryState = HanakoMemoryState(count = 1, enabledCount = 1),
      memoryLoading = false,
      memoryError = null,
      fileShareState = NbgFileShareServerState(running = true, localUrl = "127.0.0.1:43211"),
      petState = NbgPetStoreState(installedPets = listOf(nbgDefaultPetSummary()), currentPetSlug = NBG_DEFAULT_PET_SLUG, hidden = false),
      petDexLoading = false,
      petDexError = null,
    )

    val terminal = registry.byId("terminal")
    assertEquals(NbgCapabilityHealth.Failed, terminal?.health)
    assertEquals("2 就绪 · 1 启动中 · 1 失败", terminal?.status)
    assertEquals("exit_code=9", terminal?.lastError)
  }
}

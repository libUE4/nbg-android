package com.nbg.android

enum class NbgCapabilityHealth(val label: String, val rank: Int) {
  Healthy("正常", 0),
  Unknown("未知", 1),
  Degraded("注意", 2),
  Failed("失败", 3),
}

data class NbgCapability(
  val id: String,
  val title: String,
  val status: String,
  val health: NbgCapabilityHealth,
  val lastError: String = "",
  val isBeta: Boolean = false,
  val diagnosticsAction: String = "",
) {
  val active: Boolean
    get() = health == NbgCapabilityHealth.Healthy || health == NbgCapabilityHealth.Degraded
}

data class NbgCapabilityRegistry(
  val capabilities: List<NbgCapability>,
) {
  fun byId(id: String): NbgCapability? = capabilities.firstOrNull { it.id == id }

  val worstHealth: NbgCapabilityHealth
    get() = capabilities.maxByOrNull { it.health.rank }?.health ?: NbgCapabilityHealth.Unknown

  val firstProblem: NbgCapability?
    get() = capabilities.firstOrNull { it.health == NbgCapabilityHealth.Failed }
      ?: capabilities.firstOrNull { it.health == NbgCapabilityHealth.Degraded }
      ?: capabilities.firstOrNull { it.health == NbgCapabilityHealth.Unknown }

  val summaryLabel: String
    get() {
      val problem = firstProblem ?: return "能力正常"
      return "${problem.title} ${problem.health.label}"
    }
}

internal fun nbgBuildCapabilityRegistry(
  connected: Boolean,
  connectionLabel: String,
  lastError: String,
  savedUrlApiCount: Int,
  terminalReadiness: List<TerminalReadinessSnapshot> = emptyList(),
  mcpState: HanakoMcpState,
  mcpLoading: Boolean,
  mcpError: String?,
  skillsSnapshot: HanakoSkillsSnapshot,
  skillsLoading: Boolean,
  skillsError: String?,
  memoryState: HanakoMemoryState,
  memoryLoading: Boolean,
  memoryError: String?,
  fileShareState: NbgFileShareServerState?,
  petState: NbgPetStoreState,
  petDexLoading: Boolean,
  petDexError: String?,
): NbgCapabilityRegistry {
  val backendError = lastError.ifBlank { if (!connected) connectionLabel else "" }
  val hanakoLastError = if (backendError.isNotBlank() && memoryError?.takeIf { it.isNotBlank() } == backendError) {
    nbgDiagnosticMemoryError(backendError)
  } else {
    backendError
  }
  return NbgCapabilityRegistry(
    listOf(
      nbgTerminalCapability(terminalReadiness),
      NbgCapability(
        id = "hanako",
        title = "HanakoPro",
        status = if (connected) "已连接" else "未连接",
        health = if (connected) NbgCapabilityHealth.Healthy else NbgCapabilityHealth.Failed,
        lastError = hanakoLastError,
        diagnosticsAction = "hanako_server_state",
      ),
      NbgCapability(
        id = "url_api",
        title = "网址 API",
        status = if (savedUrlApiCount > 0) "$savedUrlApiCount 个" else "未配置",
        health = if (savedUrlApiCount > 0) NbgCapabilityHealth.Healthy else NbgCapabilityHealth.Degraded,
        diagnosticsAction = "url_api_providers",
      ),
      nbgMcpCapability(mcpState, mcpLoading, mcpError),
      nbgSkillsCapability(skillsSnapshot, skillsLoading, skillsError),
      nbgMemoryCapability(memoryState, memoryLoading, memoryError),
      NbgCapability(
        id = "learning",
        title = "Learning",
        status = "本地审计",
        health = NbgCapabilityHealth.Healthy,
        isBeta = true,
        diagnosticsAction = "autonomous_learning",
      ),
      nbgFileShareCapability(fileShareState),
      NbgCapability(
        id = "pets",
        title = "桌宠",
        status = when {
          petDexLoading -> "加载中"
          petState.hidden -> "隐藏"
          else -> petState.currentPet.displayName
        },
        health = when {
          petDexError != null -> NbgCapabilityHealth.Degraded
          petState.installedPets.isEmpty() -> NbgCapabilityHealth.Degraded
          else -> NbgCapabilityHealth.Healthy
        },
        lastError = petDexError.orEmpty(),
        isBeta = true,
        diagnosticsAction = "petdex_state",
      ),
      NbgCapability(
        id = "feedback_export",
        title = "诊断导出",
        status = "可导出",
        health = NbgCapabilityHealth.Healthy,
        isBeta = true,
        diagnosticsAction = "feedback_export",
      ),
    ),
  )
}

internal fun nbgTerminalCapability(readiness: List<TerminalReadinessSnapshot>): NbgCapability {
  val failed = readiness.firstOrNull { it.readiness == TerminalReadiness.Failed }
  val installingCount = readiness.count { it.readiness == TerminalReadiness.Installing }
  val readyCount = readiness.count { it.readiness == TerminalReadiness.Ready }
  val failedCount = readiness.count { it.readiness == TerminalReadiness.Failed }
  return NbgCapability(
    id = "terminal",
    title = "终端",
    status = when {
      failed != null || installingCount > 0 || readyCount > 0 -> nbgTerminalReadinessStatus(
        readyCount = readyCount,
        installingCount = installingCount,
        failedCount = failedCount,
      )
      else -> "本地"
    },
    health = when {
      failed != null -> NbgCapabilityHealth.Failed
      installingCount > 0 -> NbgCapabilityHealth.Unknown
      else -> NbgCapabilityHealth.Healthy
    },
    lastError = failed?.diagnostic.orEmpty(),
    diagnosticsAction = "terminal_startup",
  )
}

private fun nbgTerminalReadinessStatus(
  readyCount: Int,
  installingCount: Int,
  failedCount: Int,
): String =
  buildList {
    if (readyCount > 0) add("$readyCount 就绪")
    if (installingCount > 0) add("$installingCount 启动中")
    if (failedCount > 0) add("$failedCount 失败")
  }.joinToString(" · ").ifBlank { "本地" }

private fun nbgMcpCapability(
  state: HanakoMcpState,
  loading: Boolean,
  error: String?,
): NbgCapability {
  val runningCount = state.connectors.count { it.running }
  return NbgCapability(
    id = "mcp",
    title = "MCP",
    status = when {
      loading -> "加载中"
      !state.enabled -> "关闭"
      state.connectors.isEmpty() -> "未配置"
      runningCount > 0 -> "$runningCount/${state.connectors.size} 运行"
      else -> "${state.connectors.size} 个"
    },
    health = when {
      error != null -> NbgCapabilityHealth.Failed
      loading -> NbgCapabilityHealth.Unknown
      !state.enabled -> NbgCapabilityHealth.Degraded
      state.connectors.isEmpty() -> NbgCapabilityHealth.Degraded
      runningCount == 0 -> NbgCapabilityHealth.Degraded
      else -> NbgCapabilityHealth.Healthy
    },
    lastError = error.orEmpty(),
    isBeta = true,
    diagnosticsAction = "mcp_state",
  )
}

private fun nbgSkillsCapability(
  snapshot: HanakoSkillsSnapshot,
  loading: Boolean,
  error: String?,
): NbgCapability =
  NbgCapability(
    id = "skills",
    title = "Skills",
    status = when {
      loading -> "加载中"
      snapshot.enabledCount > 0 -> "${snapshot.enabledCount}/${snapshot.visibleSkills.size} 启用"
      snapshot.visibleSkills.isNotEmpty() -> "${snapshot.visibleSkills.size} 个"
      else -> "未加载"
    },
    health = when {
      error != null -> NbgCapabilityHealth.Failed
      loading -> NbgCapabilityHealth.Unknown
      snapshot.enabledCount > 0 -> NbgCapabilityHealth.Healthy
      snapshot.visibleSkills.isNotEmpty() -> NbgCapabilityHealth.Degraded
      else -> NbgCapabilityHealth.Unknown
    },
    lastError = error.orEmpty(),
    diagnosticsAction = "skills_snapshot",
  )

private fun nbgMemoryCapability(
  state: HanakoMemoryState,
  loading: Boolean,
  error: String?,
): NbgCapability =
  NbgCapability(
    id = "memory",
    title = "Memory",
    status = when {
      loading -> "加载中"
      state.enabledCount > 0 -> "${state.enabledCount}/${state.count}"
      state.count > 0 -> "${state.count} 条"
      else -> "空"
    },
    health = when {
      error != null -> NbgCapabilityHealth.Failed
      loading -> NbgCapabilityHealth.Unknown
      state.enabledCount > 0 -> NbgCapabilityHealth.Healthy
      else -> NbgCapabilityHealth.Degraded
    },
    lastError = nbgDiagnosticMemoryError(error.orEmpty()),
    diagnosticsAction = "memory_state",
  )

private fun nbgFileShareCapability(state: NbgFileShareServerState?): NbgCapability =
  NbgCapability(
    id = "ftp",
    title = "FTP 共享",
    status = when {
      state?.running == true -> state.localUrl.ifBlank { "运行中" }
      state == null -> "待启动"
      else -> "停止"
    },
    health = when {
      !state?.message.isNullOrBlank() -> NbgCapabilityHealth.Failed
      state?.running == true -> NbgCapabilityHealth.Healthy
      else -> NbgCapabilityHealth.Degraded
    },
    lastError = state?.message.orEmpty(),
    isBeta = true,
    diagnosticsAction = "ftp_share_state",
  )

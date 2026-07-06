package com.nbg.android

internal enum class NbgToolsetId(
  val wireName: String,
  val label: String,
  val defaultEnabled: Boolean,
  val capabilityId: String = "",
) {
  Terminal("terminal", "终端", true, "terminal"),
  Mcp("mcp", "MCP", true, "mcp"),
  Skills("skills", "Skills", true, "skills"),
  Memory("memory", "Memory", true, "memory"),
  AgentsTeam("agents_team", "Agents Team", true, "hanako"),
  ExpertReview("expert_review", "Expert Review", false, "url_api"),
  SessionSearch("session_search", "会话搜索", true, "hanako"),
  ContextCompression("context_compression", "上下文压缩", true, "hanako"),
  Checkpoints("checkpoints", "Checkpoint", true, "hanako"),
  Diagnostics("diagnostics", "诊断导出", true, "feedback_export"),
}

internal data class NbgToolsetControlRow(
  val id: NbgToolsetId,
  val enabled: Boolean,
  val status: String,
  val health: NbgCapabilityHealth,
  val detail: String,
  val actionLabel: String = "",
)

internal fun NbgChatPreferences.nbgToolsetEnabled(id: NbgToolsetId): Boolean =
  toolsetOverrides[id.wireName] ?: id.defaultEnabled

internal fun NbgChatPreferences.withNbgToolsetEnabled(
  id: NbgToolsetId,
  enabled: Boolean,
): NbgChatPreferences =
  copy(
    toolsetOverrides = NbgToolsetId.entries.associate { toolset ->
      toolset.wireName to if (toolset == id) enabled else nbgToolsetEnabled(toolset)
    },
    multiAgentEnabled = if (id == NbgToolsetId.AgentsTeam) enabled else multiAgentEnabled,
  )

internal fun nbgNormalizeToolsetOverrides(raw: Map<String, Boolean>): Map<String, Boolean> =
  NbgToolsetId.entries.associate { id -> id.wireName to (raw[id.wireName] ?: id.defaultEnabled) }

internal fun nbgBuildToolsetControlRows(
  preferences: NbgChatPreferences,
  capabilities: NbgCapabilityRegistry,
  sessionCount: Int,
  savedUrlApiCount: Int,
  compressionAvailable: Boolean,
): List<NbgToolsetControlRow> =
  NbgToolsetId.entries.map { id ->
    val capability = id.capabilityId.takeIf { it.isNotBlank() }?.let { capabilities.byId(it) }
    val enabled = preferences.nbgToolsetEnabled(id)
    NbgToolsetControlRow(
      id = id,
      enabled = enabled,
      status = nbgToolsetStatus(id, enabled, capability, sessionCount, savedUrlApiCount, compressionAvailable),
      health = nbgToolsetHealth(id, enabled, capability, sessionCount, savedUrlApiCount, compressionAvailable),
      detail = nbgToolsetDetail(id),
      actionLabel = nbgToolsetActionLabel(id),
    )
  }

private fun nbgToolsetStatus(
  id: NbgToolsetId,
  enabled: Boolean,
  capability: NbgCapability?,
  sessionCount: Int,
  savedUrlApiCount: Int,
  compressionAvailable: Boolean,
): String {
  if (!enabled) return "关闭"
  return when (id) {
    NbgToolsetId.SessionSearch -> if (sessionCount > 0) "$sessionCount 会话" else "待索引"
    NbgToolsetId.ContextCompression -> if (compressionAvailable) "可压缩" else "待后端支持"
    NbgToolsetId.ExpertReview -> if (savedUrlApiCount >= 2) "$savedUrlApiCount providers" else "需要两个模型"
    NbgToolsetId.Checkpoints -> "写入前快照"
    else -> capability?.status ?: "可用"
  }
}

private fun nbgToolsetHealth(
  id: NbgToolsetId,
  enabled: Boolean,
  capability: NbgCapability?,
  sessionCount: Int,
  savedUrlApiCount: Int,
  compressionAvailable: Boolean,
): NbgCapabilityHealth {
  if (!enabled) return NbgCapabilityHealth.Degraded
  return when (id) {
    NbgToolsetId.SessionSearch -> if (sessionCount > 0) NbgCapabilityHealth.Healthy else NbgCapabilityHealth.Unknown
    NbgToolsetId.ContextCompression -> if (compressionAvailable) NbgCapabilityHealth.Healthy else NbgCapabilityHealth.Unknown
    NbgToolsetId.ExpertReview -> if (savedUrlApiCount >= 2) NbgCapabilityHealth.Healthy else NbgCapabilityHealth.Degraded
    NbgToolsetId.Checkpoints -> capability?.health ?: NbgCapabilityHealth.Unknown
    else -> capability?.health ?: NbgCapabilityHealth.Unknown
  }
}

private fun nbgToolsetDetail(id: NbgToolsetId): String =
  when (id) {
    NbgToolsetId.Terminal -> "本地 shell、构建和测试输出。"
    NbgToolsetId.Mcp -> "连接器和 agent 工具级开关。"
    NbgToolsetId.Skills -> "安装、启用、bundle 和草稿审核。"
    NbgToolsetId.Memory -> "本地记忆、来源元数据和脱敏导出。"
    NbgToolsetId.AgentsTeam -> "受控模板后台任务和结果汇总。"
    NbgToolsetId.ExpertReview -> "多模型只读评审，默认关闭。"
    NbgToolsetId.SessionSearch -> "抽屉内搜索历史会话。"
    NbgToolsetId.ContextCompression -> "压缩长会话并创建新分支。"
    NbgToolsetId.Checkpoints -> "写入前后快照和恢复入口。"
    NbgToolsetId.Diagnostics -> "本地脱敏诊断包。"
  }

private fun nbgToolsetActionLabel(id: NbgToolsetId): String =
  when (id) {
    NbgToolsetId.Terminal -> "打开终端"
    NbgToolsetId.Mcp -> "打开 MCP"
    NbgToolsetId.Skills -> "打开 Skills"
    NbgToolsetId.Memory -> "打开 Memory"
    NbgToolsetId.AgentsTeam -> "打开 Agents"
    NbgToolsetId.ExpertReview -> "打开模型"
    NbgToolsetId.Diagnostics -> "导出"
    else -> ""
  }

package com.nbg.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NbgToolsetsDoctorScreen(
  preferences: NbgChatPreferences,
  capabilities: NbgCapabilityRegistry,
  sessionCount: Int,
  savedUrlApiCount: Int,
  compressionAvailable: Boolean,
  advancedOpsState: NbgAdvancedOpsState = NbgAdvancedOpsState(),
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onSetToolsetEnabled: (NbgToolsetId, Boolean) -> Unit,
  onSetUrlApiFailoverEnabled: (Boolean) -> Unit = {},
  onProbeModelCapabilities: () -> Unit = {},
  onSnapshotPromptVersions: () -> Unit = {},
  onBuildKnowledgePack: () -> Unit = {},
  onRefreshOfflineMode: () -> Unit = {},
  onEnqueueBackgroundTask: () -> Unit = {},
  onApplyPermissionTemplate: (NbgPermissionPolicyTemplate) -> Unit = {},
  onOpenBranchSession: (String) -> Unit = {},
  onOpenTerminal: () -> Unit,
  onOpenAgents: () -> Unit,
  onOpenMcp: () -> Unit,
  onOpenSkills: () -> Unit,
  onOpenMemory: () -> Unit,
  onOpenProviders: () -> Unit,
  onOpenDiagnosticsExport: () -> Unit,
) {
  val rows = remember(preferences, capabilities, sessionCount, savedUrlApiCount, compressionAvailable) {
    nbgBuildToolsetControlRows(
      preferences = preferences,
      capabilities = capabilities,
      sessionCount = sessionCount,
      savedUrlApiCount = savedUrlApiCount,
      compressionAvailable = compressionAvailable,
    )
  }
  NbgShellSubPage(
    title = "Toolsets",
    subtitle = "开关与 Doctor",
    onBack = onBack,
    onOpenDrawer = onOpenDrawer,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(NbgAgentColors.Background),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        NbgToolsetsDoctorSummary(
          capabilities = capabilities,
          enabledCount = rows.count { it.enabled },
          totalCount = rows.size,
        )
      }
      item {
        NbgAdvancedOpsDoctorCard(
          state = advancedOpsState,
          onSetFailoverEnabled = onSetUrlApiFailoverEnabled,
          onProbeModelCapabilities = onProbeModelCapabilities,
          onSnapshotPromptVersions = onSnapshotPromptVersions,
          onBuildKnowledgePack = onBuildKnowledgePack,
          onRefreshOfflineMode = onRefreshOfflineMode,
          onEnqueueBackgroundTask = onEnqueueBackgroundTask,
          onApplyPermissionTemplate = onApplyPermissionTemplate,
          onOpenBranchSession = onOpenBranchSession,
        )
      }
      items(rows, key = { it.id.wireName }) { row ->
        NbgToolsetControlCard(
          row = row,
          onEnabledChange = { enabled -> onSetToolsetEnabled(row.id, enabled) },
          onAction = when (row.id) {
            NbgToolsetId.Terminal -> onOpenTerminal
            NbgToolsetId.Mcp -> onOpenMcp
            NbgToolsetId.Skills -> onOpenSkills
            NbgToolsetId.Memory -> onOpenMemory
            NbgToolsetId.AgentsTeam -> onOpenAgents
            NbgToolsetId.ExpertReview -> onOpenProviders
            NbgToolsetId.Diagnostics -> onOpenDiagnosticsExport
            else -> null
          },
        )
      }
    }
  }
}

@Composable
private fun NbgAdvancedOpsDoctorCard(
  state: NbgAdvancedOpsState,
  onSetFailoverEnabled: (Boolean) -> Unit,
  onProbeModelCapabilities: () -> Unit,
  onSnapshotPromptVersions: () -> Unit,
  onBuildKnowledgePack: () -> Unit,
  onRefreshOfflineMode: () -> Unit,
  onEnqueueBackgroundTask: () -> Unit,
  onApplyPermissionTemplate: (NbgPermissionPolicyTemplate) -> Unit,
  onOpenBranchSession: (String) -> Unit,
) {
  val latestBranch = state.branchNodes.firstOrNull()
  val latestAudit = state.auditTimeline.firstOrNull()
  val latestProbe = state.modelProbes.firstOrNull()
  val latestPrompt = state.promptVersions.firstOrNull()
  val latestError = state.errorKnowledge.firstOrNull()
  val latestTask = state.backgroundTasks.firstOrNull()
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.SurfaceBorder),
  ) {
    Column(
      modifier = Modifier.padding(13.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = NbgAgentColors.Primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Advanced Ops",
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = state.featureSummary,
            color = NbgAgentColors.TextMuted,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        NbgToolsetsMiniMetric("分支", state.branchNodes.size, Modifier.weight(1f))
        NbgToolsetsMiniMetric("审计", state.auditTimeline.size, Modifier.weight(1f))
        NbgToolsetsMiniMetric("探测", state.modelProbes.size, Modifier.weight(1f))
      }
      NbgAdvancedOpsRow(
        title = "会话分支/版本树",
        detail = latestBranch?.let { "${it.action} · ${it.label.ifBlank { it.sessionPath.substringAfterLast('/') }}" } ?: "压缩、撤回、重试、换模型会形成分支节点。",
        actionLabel = latestBranch?.takeIf { it.sessionPath.isNotBlank() }?.let { "切换" }.orEmpty(),
        onAction = latestBranch?.takeIf { it.sessionPath.isNotBlank() }?.let { { onOpenBranchSession(it.sessionPath) } },
      )
      NbgAdvancedOpsRow(
        title = "Agent 行为审计时间线",
        detail = latestAudit?.let { "${it.kind} · ${it.title}" } ?: "模型选择、工具、Memory、Skill、费用和错误会统一记录。",
      )
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("URL API 自动故障转移", color = NbgAgentColors.TextStrong, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
          Text(state.urlApiFailover.statusLabel, color = NbgAgentColors.TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = state.urlApiFailover.enabled, onCheckedChange = onSetFailoverEnabled)
      }
      NbgAdvancedOpsRow(
        title = "模型能力探测",
        detail = latestProbe?.let { "${it.modelId} · ${it.capabilityLabel}" } ?: "探测 streaming/tools/vision/thinking/JSON/max tokens。",
        actionLabel = "探测",
        onAction = onProbeModelCapabilities,
      )
      NbgAdvancedOpsRow(
        title = "Prompt/系统提示版本",
        detail = latestPrompt?.let { "${it.scope}:${it.name} · ${it.contentHash.take(10)}" } ?: "记录 ProviderProfile extra body、Skill prompt 和系统提示 hash。",
        actionLabel = "快照",
        onAction = onSnapshotPromptVersions,
      )
      val template = state.permissionTemplates.firstOrNull()
      NbgAdvancedOpsRow(
        title = "工具权限策略模板",
        detail = template?.let { "${it.label} · ${it.permissionMode} · ${it.thinkingLevel}" } ?: "只读研究、安卓构建、全自动修复、高危确认。",
        actionLabel = template?.let { "应用" }.orEmpty(),
        onAction = template?.let { { onApplyPermissionTemplate(it) } },
      )
      NbgAdvancedOpsRow(
        title = "Knowledge Pack",
        detail = state.knowledgePacks.firstOrNull()?.let { "${it.label} · ${it.itemCount} 项 · ${it.byteSize / 1024} KB" } ?: "打包 Memory、Skill、会话摘要和文件索引用于迁移。",
        actionLabel = "打包",
        onAction = onBuildKnowledgePack,
      )
      NbgAdvancedOpsRow(
        title = "离线模式",
        detail = state.offlineMode.statusLabel,
        actionLabel = "刷新",
        onAction = onRefreshOfflineMode,
      )
      NbgAdvancedOpsRow(
        title = "错误知识库",
        detail = latestError?.let { "${it.title} · ${it.count} 次 · ${it.fixHint}" } ?: "自动归类 API、ADB、构建和连接错误。",
      )
      NbgAdvancedOpsRow(
        title = "后台任务队列",
        detail = latestTask?.let { "${it.title} · ${it.status} · ${it.attemptCount}/${it.maxAttempts}" } ?: "长任务排队、暂停/恢复、失败重试和通知进度。",
        actionLabel = "入队",
        onAction = onEnqueueBackgroundTask,
      )
    }
  }
}

@Composable
private fun NbgAdvancedOpsRow(
  title: String,
  detail: String,
  actionLabel: String = "",
  onAction: (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        color = NbgAgentColors.TextStrong,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = detail,
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (actionLabel.isNotBlank() && onAction != null) {
      TextButton(onClick = onAction) {
        Text(actionLabel)
      }
    }
  }
}

@Composable
private fun NbgToolsetsDoctorSummary(
  capabilities: NbgCapabilityRegistry,
  enabledCount: Int,
  totalCount: Int,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.SurfaceBorder),
  ) {
    Column(
      modifier = Modifier.padding(13.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = nbgDoctorHealthColor(capabilities.worstHealth))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = capabilities.summaryLabel,
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "$enabledCount/$totalCount toolsets enabled",
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        NbgToolsetsMiniMetric("正常", capabilities.capabilities.count { it.health == NbgCapabilityHealth.Healthy }, Modifier.weight(1f))
        NbgToolsetsMiniMetric("注意", capabilities.capabilities.count { it.health == NbgCapabilityHealth.Degraded }, Modifier.weight(1f))
        NbgToolsetsMiniMetric("失败", capabilities.capabilities.count { it.health == NbgCapabilityHealth.Failed }, Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun NbgToolsetControlCard(
  row: NbgToolsetControlRow,
  onEnabledChange: (Boolean) -> Unit,
  onAction: (() -> Unit)?,
) {
  val healthColor = nbgDoctorHealthColor(row.health)
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = if (row.enabled) NbgAgentColors.SurfaceContainer else NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, if (row.enabled) NbgAgentColors.InputBorder else NbgAgentColors.SurfaceBorder),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = healthColor.copy(alpha = 0.14f),
          contentColor = healthColor,
        ) {
          Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = null,
            modifier = Modifier.padding(8.dp),
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = row.id.label,
            color = NbgAgentColors.TextStrong,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = row.status,
            color = healthColor,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Switch(
          checked = row.enabled,
          onCheckedChange = onEnabledChange,
        )
      }
      Text(
        text = row.detail,
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
      )
      if (row.actionLabel.isNotBlank() && onAction != null) {
        TextButton(
          enabled = row.enabled,
          onClick = onAction,
        ) {
          Text(row.actionLabel)
        }
      }
    }
  }
}

@Composable
private fun NbgToolsetsMiniMetric(
  label: String,
  value: Int,
  modifier: Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(text = value.toString(), color = NbgAgentColors.TextStrong, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Text(text = label, color = NbgAgentColors.TextMuted, fontSize = 10.5.sp)
    }
  }
}

private fun nbgDoctorHealthColor(health: NbgCapabilityHealth) =
  when (health) {
    NbgCapabilityHealth.Healthy -> NbgAgentColors.StatusGreen
    NbgCapabilityHealth.Unknown -> NbgAgentColors.StatusYellow
    NbgCapabilityHealth.Degraded -> NbgAgentColors.Primary
    NbgCapabilityHealth.Failed -> NbgAgentColors.StatusRed
  }

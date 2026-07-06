package com.nbg.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NbgAutonomousLearningScreen(
  snapshot: NbgAutonomousLearningSnapshot,
  scheduleState: NbgScheduleState,
  gatewayInboxState: NbgGatewayInboxState,
  trajectoryExportBundle: NbgTrajectoryExportBundle?,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onReload: () -> Unit,
  onCreateSchedule: (NbgScheduledAutomationTemplate) -> Unit,
  onToggleSchedule: (String, Boolean) -> Unit,
  onRunScheduleNow: (String) -> Unit,
  onArchiveGatewayMessage: (String) -> Unit,
  onBuildTrajectoryExport: () -> Unit,
  onClearTrajectoryExport: () -> Unit,
  onBuildRecall: () -> Unit,
  onApproveEvent: (String) -> Unit,
  onRejectEvent: (String) -> Unit,
  onRevertEvent: (String) -> Unit,
  onOpenMemory: () -> Unit,
  onOpenSkills: () -> Unit,
) {
  NbgShellSubPage(
    title = "Learning",
    subtitle = "Hermes 风格自主学习 · 本地审计",
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
        NbgLearningOverviewCard(snapshot = snapshot, onReload = onReload)
      }
      item {
        NbgLearningGraphCard(snapshot.graph, onBuildRecall = onBuildRecall)
      }
      item {
        NbgLearningRecallCard(snapshot.recallBundle)
      }
      item {
        NbgLearningStoresCard(
          snapshot = snapshot,
          onOpenMemory = onOpenMemory,
          onOpenSkills = onOpenSkills,
        )
      }
      item {
        NbgLearningScheduleCard(
          state = scheduleState,
          onCreateSchedule = onCreateSchedule,
          onToggleSchedule = onToggleSchedule,
          onRunNow = onRunScheduleNow,
        )
      }
      item {
        NbgLearningGatewayCard(
          state = gatewayInboxState,
          onArchive = onArchiveGatewayMessage,
        )
      }
      item {
        NbgLearningTrajectoryExportCard(
          bundle = trajectoryExportBundle,
          onBuild = onBuildTrajectoryExport,
          onClear = onClearTrajectoryExport,
        )
      }
      item {
        NbgLearningSectionTitle("学习审计")
      }
      if (snapshot.auditLog.events.isEmpty()) {
        item {
          NbgLearningEmptyCard()
        }
      } else {
        items(snapshot.auditLog.events, key = { it.id }) { event ->
          NbgLearningEventCard(
            event = event,
            onApprove = { onApproveEvent(event.id) },
            onReject = { onRejectEvent(event.id) },
            onRevert = { onRevertEvent(event.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun NbgLearningScheduleCard(
  state: NbgScheduleState,
  onCreateSchedule: (NbgScheduledAutomationTemplate) -> Unit,
  onToggleSchedule: (String, Boolean) -> Unit,
  onRunNow: (String) -> Unit,
) {
  NbgLearningCard {
    Text(
      text = "Scheduled Automations",
      color = NbgAgentColors.TextStrong,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      NbgLearningMetric("任务", state.automations.size, NbgAgentColors.Primary, Modifier.weight(1f))
      NbgLearningMetric("启用", state.enabledCount, NbgAgentColors.StatusGreen, Modifier.weight(1f))
      NbgLearningMetric("待确认", state.pendingConfirmationCount, NbgAgentColors.StatusYellow, Modifier.weight(1f))
    }
    if (state.automations.isEmpty()) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NbgInlineActionButton(label = "日报", icon = Icons.Filled.School) {
          onCreateSchedule(NbgScheduledAutomationTemplate.DailyProjectSummary)
        }
        NbgInlineActionButton(label = "周审计", icon = Icons.Filled.CheckCircle) {
          onCreateSchedule(NbgScheduledAutomationTemplate.WeeklyLearningAudit)
        }
      }
    } else {
      state.automations.take(4).forEach { automation ->
        NbgLearningScheduleRow(automation, onToggleSchedule, onRunNow)
      }
    }
  }
}

@Composable
private fun NbgLearningScheduleRow(
  automation: NbgScheduledAutomation,
  onToggleSchedule: (String, Boolean) -> Unit,
  onRunNow: (String) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(13.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
          Text(automation.title, color = NbgAgentColors.TextStrong, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
          Text(
            "${automation.template.label} · ${automation.cadence} · ${automation.permissionTier.label}",
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgInlineActionButton(
          label = if (automation.enabled) "停用" else "启用",
          icon = if (automation.enabled) Icons.Filled.Close else Icons.Filled.CheckCircle,
        ) { onToggleSchedule(automation.id, !automation.enabled) }
        NbgInlineActionButton(label = "运行", icon = Icons.Filled.Refresh) { onRunNow(automation.id) }
      }
    }
  }
}

@Composable
private fun NbgLearningGatewayCard(
  state: NbgGatewayInboxState,
  onArchive: (String) -> Unit,
) {
  NbgLearningCard {
    Text(
      text = "Gateway Inbox",
      color = NbgAgentColors.TextStrong,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      NbgLearningMetric("消息", state.activeMessages.size, NbgAgentColors.Primary, Modifier.weight(1f))
      NbgLearningMetric("可执行", state.executableCount, NbgAgentColors.StatusGreen, Modifier.weight(1f))
      NbgLearningMetric("阻止", state.blockedCount, NbgAgentColors.StatusRed, Modifier.weight(1f))
    }
    state.activeMessages.take(3).forEach { message ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            "${message.source.label} · ${message.status.label}",
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            message.text,
            color = NbgAgentColors.TextStrong,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgSmallIconAction(Icons.Filled.Close, "归档消息") { onArchive(message.id) }
      }
    }
  }
}

@Composable
private fun NbgLearningTrajectoryExportCard(
  bundle: NbgTrajectoryExportBundle?,
  onBuild: () -> Unit,
  onClear: () -> Unit,
) {
  NbgLearningCard {
    Text(
      text = "Trajectory Export",
      color = NbgAgentColors.TextStrong,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
    )
    if (bundle == null) {
      Text(
        text = "本地导出会话、tool events、task evidence 和 learning events；导出前强制脱敏，不上传。",
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
      )
      NbgInlineActionButton(label = "生成导出", icon = Icons.Filled.Refresh, onClick = onBuild)
    } else {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NbgLearningMetric("消息", bundle.review.conversationCount, NbgAgentColors.Primary, Modifier.weight(1f))
        NbgLearningMetric("工具", bundle.review.toolEventCount, NbgAgentColors.StatusGreen, Modifier.weight(1f))
        NbgLearningMetric("学习", bundle.review.learningEventCount, NbgAgentColors.StatusYellow, Modifier.weight(1f))
      }
      Text(
        text = bundle.review.reason,
        color = if (bundle.review.allowExport) NbgAgentColors.StatusGreen else NbgAgentColors.StatusRed,
        fontSize = 12.sp,
        lineHeight = 17.sp,
      )
      NbgInlineActionButton(label = "清除", icon = Icons.Filled.Close, onClick = onClear)
    }
  }
}

@Composable
private fun NbgLearningOverviewCard(
  snapshot: NbgAutonomousLearningSnapshot,
  onReload: () -> Unit,
) {
  NbgLearningCard(borderColor = NbgAgentColors.PrimarySoft) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Icon(Icons.Filled.School, contentDescription = null, tint = NbgAgentColors.Primary)
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = "自主学习闭环",
          color = NbgAgentColors.TextStrong,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "从对话生成候选，经过策略审核后写入本地 Memory、用户画像、Soul 或 Skill 草稿。",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      }
      IconButton(onClick = onReload) {
        Icon(Icons.Filled.Refresh, contentDescription = "刷新学习状态")
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      NbgLearningMetric("已应用", snapshot.autoAppliedCount, NbgAgentColors.StatusGreen, Modifier.weight(1f))
      NbgLearningMetric("待审核", snapshot.pendingReviewCount, NbgAgentColors.StatusYellow, Modifier.weight(1f))
      NbgLearningMetric("已阻止", snapshot.blockedCount, NbgAgentColors.StatusRed, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      NbgSheetTag(if (snapshot.settings.autonomousLearningEnabled) "learning on" else "learning off", primary = snapshot.settings.autonomousLearningEnabled)
      NbgSheetTag("review before skill install", warning = true)
      NbgSheetTag("local audit")
    }
  }
}

@Composable
private fun NbgLearningGraphCard(
  graph: NbgLearningGraph,
  onBuildRecall: () -> Unit,
) {
  NbgLearningCard {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = "学习图谱",
        color = NbgAgentColors.TextStrong,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f),
      )
      NbgInlineActionButton(label = "召回", icon = Icons.Filled.History, onClick = onBuildRecall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      NbgLearningMetric("Memory", graph.memoryCount, NbgAgentColors.Primary, Modifier.weight(1f))
      NbgLearningMetric("画像", graph.profileCount, NbgAgentColors.StatusGreen, Modifier.weight(1f))
      NbgLearningMetric("Skills", graph.skillCount, NbgAgentColors.StatusYellow, Modifier.weight(1f))
      NbgLearningMetric("关联", graph.edges.size, NbgAgentColors.TextMuted, Modifier.weight(1f))
    }
    Text(
      text = "linked ${graph.stats.linkedNodeCount}/${graph.stats.nodeCount} · isolated ${graph.stats.isolatedNodeCount}",
      color = NbgAgentColors.TextDisabled,
      fontSize = 11.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    graph.edges.take(3).forEach { edge ->
      Text(
        text = "${edge.fromId} -> ${edge.toId} · ${edge.reason}",
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun NbgLearningRecallCard(bundle: NbgLearningRecallBundle) {
  NbgLearningCard {
    Text(
      text = "跨会话召回",
      color = NbgAgentColors.TextStrong,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
    )
    if (!bundle.hasResults) {
      Text(
        text = "点击召回会从本地 Memory、用户画像、Soul、Skill 草稿和会话摘要中查找相关上下文。",
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
      )
    } else {
      Text(
        text = "\"${bundle.query}\" · ${bundle.items.size}/${bundle.sourceCount}",
        color = NbgAgentColors.TextDisabled,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      bundle.items.take(5).forEach { item ->
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NbgSheetTag("${item.kind.label} ${item.score}", primary = item.kind == NbgLearningRecallKind.Memory)
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = item.title,
              color = NbgAgentColors.TextStrong,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = item.snippet,
              color = NbgAgentColors.TextMuted,
              fontSize = 11.sp,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun NbgLearningStoresCard(
  snapshot: NbgAutonomousLearningSnapshot,
  onOpenMemory: () -> Unit,
  onOpenSkills: () -> Unit,
) {
  NbgLearningCard {
    Text(
      text = "学习结果",
      color = NbgAgentColors.TextStrong,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      NbgLearningMetric("本地记忆", snapshot.localMemory.enabledEntries.size, NbgAgentColors.Primary, Modifier.weight(1f))
      NbgLearningMetric("用户画像", snapshot.userProfile.visibleEntries.size, NbgAgentColors.StatusGreen, Modifier.weight(1f))
      NbgLearningMetric("Soul", snapshot.soul.principles.size, NbgAgentColors.StatusYellow, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      NbgInlineActionButton(label = "Memory", icon = Icons.Filled.Memory, onClick = onOpenMemory)
      NbgInlineActionButton(label = "Skills", icon = Icons.Filled.School, onClick = onOpenSkills)
    }
    snapshot.userProfile.visibleEntries.take(3).forEach { entry ->
      Text(
        text = "${entry.key}: ${entry.value}",
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun NbgLearningEventCard(
  event: NbgLearningEvent,
  onApprove: () -> Unit,
  onReject: () -> Unit,
  onRevert: () -> Unit,
) {
  val statusColor = when (event.status) {
    NbgLearningEventStatus.AutoApplied -> NbgAgentColors.StatusGreen
    NbgLearningEventStatus.PendingReview -> NbgAgentColors.StatusYellow
    NbgLearningEventStatus.Blocked,
    NbgLearningEventStatus.Failed -> NbgAgentColors.StatusRed
    NbgLearningEventStatus.Rejected,
    NbgLearningEventStatus.Reverted -> NbgAgentColors.TextMuted
  }
  NbgLearningCard(borderColor = statusColor.copy(alpha = 0.55f)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = event.candidate.title.ifBlank { event.candidate.kind.label },
          color = NbgAgentColors.TextStrong,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${event.candidate.kind.label} · ${event.status.label} · ${event.review.riskTier.label}",
          color = statusColor,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (event.status == NbgLearningEventStatus.PendingReview && !event.review.blocked) {
        NbgSmallIconAction(Icons.Filled.CheckCircle, "批准学习", onApprove)
        NbgSmallIconAction(Icons.Filled.Close, "拒绝学习", onReject)
      }
      if (event.status == NbgLearningEventStatus.AutoApplied) {
        NbgSmallIconAction(Icons.Filled.Undo, "撤回学习", onRevert)
      }
    }
    Text(
      text = event.candidate.content,
      color = NbgAgentColors.TextMuted,
      fontSize = 12.sp,
      lineHeight = 17.sp,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = event.review.reason,
      color = NbgAgentColors.TextDisabled,
      fontSize = 11.sp,
      lineHeight = 15.sp,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun NbgLearningEmptyCard() {
  NbgLearningCard {
    Text(
      text = "还没有学习事件",
      color = NbgAgentColors.TextStrong,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = "在聊天里使用“记住”“我的偏好”“保存为技能”或 /learn，会生成本地学习审计。",
      color = NbgAgentColors.TextMuted,
      fontSize = 12.sp,
      lineHeight = 17.sp,
    )
  }
}

@Composable
private fun NbgLearningSectionTitle(text: String) {
  Text(
    text = text,
    color = NbgAgentColors.TextMuted,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(top = 4.dp, start = 2.dp),
  )
}

@Composable
private fun NbgLearningMetric(
  label: String,
  value: Int,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(13.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = value.toString(), color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
      Text(text = label, color = NbgAgentColors.TextMuted, fontSize = 10.5.sp, maxLines = 1)
    }
  }
}

@Composable
private fun NbgSmallIconAction(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick) {
    Icon(icon, contentDescription = contentDescription, tint = NbgAgentColors.TextMuted)
  }
}

@Composable
private fun NbgLearningCard(
  borderColor: Color = NbgAgentColors.SurfaceBorder,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, borderColor),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
  }
}

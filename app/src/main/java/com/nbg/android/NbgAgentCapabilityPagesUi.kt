package com.nbg.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable

private data class NbgAgentTemplateRow(
  val role: String,
  val title: String,
  val detail: String,
  val tools: String,
)

private val NBG_AGENT_TEMPLATES = listOf(
  NbgAgentTemplateRow("coder", "实现", "按当前任务修改代码，保持改动范围小。", "Agent subagent_type=coder"),
  NbgAgentTemplateRow("explore", "探索", "读取源码、文档和默认代码图谱上下文。", "Agent subagent_type=explore"),
  NbgAgentTemplateRow("plan", "规划", "拆目标、排顺序、识别风险和验收点。", "Agent subagent_type=plan"),
  NbgAgentTemplateRow("verify", "验证", "设计并执行编译、单测和设备检查。", "Agent subagent_type=verify"),
  NbgAgentTemplateRow("reviewer", "审查", "找 bug、回归点、缺测试和过度设计。", "Agent subagent_type=reviewer"),
  NbgAgentTemplateRow("oracle", "第二意见", "对高风险判断、方案取舍和隐含假设给独立意见。", "Agent subagent_type=oracle"),
  NbgAgentTemplateRow("writer", "写作", "生成文档、说明和面向用户的交付文本。", "Agent subagent_type=writer"),
)

@Composable
internal fun NbgAgentsScreen(
  runStatus: NbgChatRunStatus,
  mcpState: HanakoMcpState,
  skillsSnapshot: HanakoSkillsSnapshot,
  memoryState: HanakoMemoryState,
  learningSnapshot: NbgAutonomousLearningSnapshot,
  capabilities: NbgCapabilityRegistry,
  teamTask: HanakoTeamTaskStatus?,
  multiAgentEnabled: Boolean,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onSetMultiAgentEnabled: (Boolean) -> Unit,
  onAbortTeamTask: () -> Unit,
  onAbortTeamAgent: (String, String) -> Unit,
  onOpenMcp: () -> Unit,
  onOpenLearning: () -> Unit,
  onOpenSkills: () -> Unit,
  onOpenMemory: () -> Unit,
) {
  NbgShellSubPage(
    title = "Agents",
    subtitle = "后端工具触发 · Agent / WolfPack",
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
        NbgCapabilitySummaryCard(
          icon = Icons.Filled.Timeline,
          title = "执行方式",
          detail = "普通消息保持原文发送。复杂任务由 HanakoPro 后端按需触发 Agent 或 WolfPack，复用真实隔离子会话执行。",
          meta = if (runStatus.active) runStatus.label else "待命",
        )
      }
      item {
        NbgAgentsModeCard(
          enabled = multiAgentEnabled,
          onEnabledChange = onSetMultiAgentEnabled,
        )
      }
      item {
        NbgAgentsTeamTaskCard(
          task = teamTask,
          onAbortTask = onAbortTeamTask,
          onAbortAgent = onAbortTeamAgent,
        )
      }
      item {
        NbgAgentsTeamPolicyCard()
      }
      item {
        NbgAgentsPermissionCard(
          mcpLabel = capabilities.byId("mcp").nbgAgentsCapabilityLabel("MCP ${mcpState.connectors.size}"),
          skillsLabel = capabilities.byId("skills").nbgAgentsCapabilityLabel("Skills ${skillsSnapshot.enabledCount}"),
          memoryLabel = capabilities.byId("memory").nbgAgentsCapabilityLabel("Memory ${memoryState.enabledCount}"),
          learningLabel = "Learning ${learningSnapshot.auditLog.events.size}",
          onOpenMcp = onOpenMcp,
          onOpenLearning = onOpenLearning,
          onOpenSkills = onOpenSkills,
          onOpenMemory = onOpenMemory,
        )
      }
      items(NBG_AGENT_TEMPLATES, key = { it.role }) { item ->
        NbgAgentTemplateCard(item)
      }
    }
  }
}

@Composable
private fun NbgAgentsTeamPolicyCard() {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.SurfaceBorder),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Agents Team 模板",
        color = NbgAgentColors.TextStrong,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "后台团队任务只允许 bounded 模板；每个模板都有工具范围、时间预算、确认要求、取消和汇总约束。",
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
      )
      NbgTeamDelegationTemplate.entries.forEach { template ->
        NbgAgentsTeamTemplateRow(template)
      }
    }
  }
}

@Composable
private fun NbgAgentsTeamTaskCard(
  task: HanakoTeamTaskStatus?,
  onAbortTask: () -> Unit,
  onAbortAgent: (String, String) -> Unit,
) {
  val active = task?.isActive == true
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, if (active) NbgAgentColors.PrimarySoft else NbgAgentColors.SurfaceBorder),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NbgAgentsStatusIcon(status = task?.status.orEmpty())
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(
            text = "团队任务状态",
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = task?.let { nbgAgentsTeamTaskMeta(it) } ?: "当前没有运行中的团队任务",
            color = if (active) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        task?.takeIf { it.isActive }?.let {
          NbgInlineActionButton(
            label = "停止",
            icon = Icons.Filled.Stop,
            enabled = true,
            onClick = onAbortTask,
          )
        }
      }
      if (task == null) {
        Text(
          text = "Agent 或 WolfPack 后端工具触发后，会在这里显示子 Agent 进度、取消入口和最终汇总。",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      } else {
        Text(
          text = task.summary.ifBlank { if (task.isActive) "团队任务正在执行" else "团队任务已结束" },
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
          NbgAgentsTeamMetric("子 Agent", task.agents.size, Modifier.weight(1f))
          NbgAgentsTeamMetric("运行中", task.activeCount, Modifier.weight(1f))
          NbgAgentsTeamMetric("证据", task.agents.sumOf { it.artifactRefs.size }, Modifier.weight(1f))
        }
        if (task.agents.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            task.agents.sortedWith(compareBy<HanakoTeamAgentStatus> { it.defaultOrder }.thenBy { it.title }).forEach { agent ->
              NbgAgentsTeamAgentRow(
                agent = agent,
                taskActive = task.isActive,
                onAbort = { onAbortAgent(task.taskId, agent.agentId) },
              )
            }
          }
        }
        if (!task.isActive) {
          NbgAgentsTeamResultPanel(task)
        }
      }
    }
  }
}

@Composable
private fun NbgAgentsTeamAgentRow(
  agent: HanakoTeamAgentStatus,
  taskActive: Boolean,
  onAbort: () -> Unit,
) {
  val status = nbgNormalizeTeamStatus(agent.status)
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(13.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NbgAgentsStatusDot(status = status)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = agent.title.ifBlank { agent.role },
            color = NbgAgentColors.TextStrong,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = listOf(agent.role, nbgAgentsStatusLabel(status)).filter { it.isNotBlank() }.joinToString(" · "),
            color = nbgAgentsStatusColor(status),
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (taskActive && agent.running) {
          NbgInlineActionButton(
            label = "停止",
            icon = Icons.Filled.Stop,
            enabled = true,
            onClick = onAbort,
          )
        }
      }
      if (agent.summary.isNotBlank()) {
        Text(
          text = agent.summary,
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (agent.artifactRefs.isNotEmpty()) {
        Text(
          text = agent.artifactRefs.take(3).joinToString(", "),
          color = NbgAgentColors.CodeText,
          fontSize = 10.5.sp,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun NbgAgentsTeamMetric(label: String, value: Int, modifier: Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = value.toString(),
        color = NbgAgentColors.TextStrong,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = label,
        color = NbgAgentColors.TextMuted,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun NbgAgentsTeamResultPanel(task: HanakoTeamTaskStatus) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(13.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = nbgAgentsStatusColor(task.status),
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = "汇总结果 · ${nbgAgentsStatusLabel(task.status)}",
          color = NbgAgentColors.TextStrong,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Text(
        text = task.summary.ifBlank { "任务已结束，等待后端补充汇总。" },
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun NbgAgentsStatusIcon(status: String) {
  Box(
    modifier = Modifier
      .size(38.dp)
      .background(nbgAgentsStatusColor(status).copy(alpha = 0.18f), RoundedCornerShape(13.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Filled.Timeline,
      contentDescription = null,
      tint = nbgAgentsStatusColor(status),
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun NbgAgentsStatusDot(status: String) {
  Box(
    modifier = Modifier
      .size(10.dp)
      .background(nbgAgentsStatusColor(status), RoundedCornerShape(999.dp)),
  )
}

@Composable
private fun NbgAgentsTeamTemplateRow(template: NbgTeamDelegationTemplate) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(13.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = template.title,
          color = NbgAgentColors.TextStrong,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = template.permissionTier.label,
          color = if (template.permissionTier.requiresConfirmation) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
        )
      }
      Text(
        text = "${template.maxSubtasks} 子任务 · ${template.maxMinutes} 分钟 · 可取消 · 必须汇总",
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = template.allowedTools.joinToString(", "),
        color = NbgAgentColors.CodeText,
        fontSize = 10.5.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun NbgAgentsModeCard(
  enabled: Boolean,
  onEnabledChange: (Boolean) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.PrimarySoft.copy(alpha = 0.55f)),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "多 Agent 编排",
          color = NbgAgentColors.TextStrong,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "开启后，后端默认暴露 Agent 和 WolfPack 工具；模型只在任务需要拆分、审查或并行处理时触发子 Agent。",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      }
      Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
  }
}

@Composable
private fun NbgAgentsPermissionCard(
  mcpLabel: String,
  skillsLabel: String,
  memoryLabel: String,
  learningLabel: String,
  onOpenMcp: () -> Unit,
  onOpenLearning: () -> Unit,
  onOpenSkills: () -> Unit,
  onOpenMemory: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.SurfaceBorder),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "权限绑定",
        color = NbgAgentColors.TextStrong,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NbgInlineActionButton(label = mcpLabel, icon = Icons.Filled.Settings, onClick = onOpenMcp)
        NbgInlineActionButton(label = learningLabel, icon = Icons.Filled.Timeline, onClick = onOpenLearning)
        NbgInlineActionButton(label = skillsLabel, icon = Icons.Filled.Code, onClick = onOpenSkills)
        NbgInlineActionButton(label = memoryLabel, icon = Icons.Filled.Memory, onClick = onOpenMemory)
      }
    }
  }
}

private fun NbgCapability?.nbgAgentsCapabilityLabel(fallback: String): String =
  this?.let { "${it.title} ${it.status}" } ?: fallback

private fun nbgAgentsTeamTaskMeta(task: HanakoTeamTaskStatus): String =
  listOf(
    nbgAgentsTeamModeLabel(task),
    nbgAgentsStatusLabel(task.status),
    "${task.activeCount}/${task.agents.size} 工作中",
  ).joinToString(" · ")

private fun nbgAgentsTeamModeLabel(task: HanakoTeamTaskStatus): String =
  when {
    task.isBackendAgentToolExecution() -> "后台 Agent"
    task.isMultiAgentSession() -> "多 Agent"
    task.isSingleAgentExecution() -> "单 Agent"
    else -> "团队"
  }

private fun nbgAgentsStatusLabel(status: String): String =
  when (nbgNormalizeTeamStatus(status)) {
    "queued" -> "排队"
    "running", "working" -> "运行中"
    "thinking" -> "思考中"
    "coding" -> "编码中"
    "reviewing" -> "审查中"
    "testing" -> "验证中"
    "terminal" -> "终端中"
    "completed", "done", "success" -> "已完成"
    "failed", "error" -> "失败"
    "aborted", "cancelled", "canceled" -> "已停止"
    "idle", "" -> "待命"
    else -> status
  }

private fun nbgAgentsStatusColor(status: String): Color =
  when (nbgNormalizeTeamStatus(status)) {
    "completed", "done", "success" -> NbgAgentColors.StatusGreen
    "failed", "error" -> NbgAgentColors.StatusRed
    "aborted", "cancelled", "canceled" -> NbgAgentColors.TextMuted
    "queued", "running", "thinking", "working", "coding", "reviewing", "testing", "terminal" -> NbgAgentColors.Primary
    else -> NbgAgentColors.TextMuted
  }

@Composable
private fun NbgCapabilitySummaryCard(
  icon: ImageVector,
  title: String,
  detail: String,
  meta: String,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.PrimarySoft.copy(alpha = 0.55f)),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = NbgAgentColors.PrimaryContainer,
        contentColor = NbgAgentColors.TextStrong,
      ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp))
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, color = NbgAgentColors.TextStrong, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(text = detail, color = NbgAgentColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
      }
      Text(text = meta, color = NbgAgentColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun NbgAgentTemplateCard(item: NbgAgentTemplateRow) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(15.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(13.dp),
      verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = item.role,
          color = NbgAgentColors.TextStrong,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(text = item.title, color = NbgAgentColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
      }
      Text(text = item.detail, color = NbgAgentColors.TextMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
      Text(text = item.tools, color = NbgAgentColors.CodeText, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
    }
  }
}

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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
  capabilities: NbgCapabilityRegistry,
  multiAgentEnabled: Boolean,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onSetMultiAgentEnabled: (Boolean) -> Unit,
  onOpenMcp: () -> Unit,
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
        NbgAgentsTeamPolicyCard()
      }
      item {
        NbgAgentsPermissionCard(
          mcpLabel = capabilities.byId("mcp").nbgAgentsCapabilityLabel("MCP ${mcpState.connectors.size}"),
          skillsLabel = capabilities.byId("skills").nbgAgentsCapabilityLabel("Skills ${skillsSnapshot.enabledCount}"),
          memoryLabel = capabilities.byId("memory").nbgAgentsCapabilityLabel("Memory ${memoryState.enabledCount}"),
          onOpenMcp = onOpenMcp,
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
  onOpenMcp: () -> Unit,
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
        NbgInlineActionButton(label = skillsLabel, icon = Icons.Filled.Code, onClick = onOpenSkills)
        NbgInlineActionButton(label = memoryLabel, icon = Icons.Filled.Memory, onClick = onOpenMemory)
      }
    }
  }
}

private fun NbgCapability?.nbgAgentsCapabilityLabel(fallback: String): String =
  this?.let { "${it.title} ${it.status}" } ?: fallback

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

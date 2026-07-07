package com.nbg.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NbgSlashCommandShortcut(
  val command: String,
  val title: String,
  val description: String,
)

internal fun nbgAndroidSlashCommandShortcuts(compressionAvailable: Boolean): List<NbgSlashCommandShortcut> =
  listOf(
    NbgSlashCommandShortcut("/model", "模型", "打开当前模型配置并刷新后端模型列表。"),
    NbgSlashCommandShortcut("/tools", "工具", "打开 Toolsets Doctor，管理终端、MCP、Skills、Memory 等工具集。"),
    NbgSlashCommandShortcut("/skills", "Skills", "打开 Skill 管理、diff/merge 和 Curator。"),
    NbgSlashCommandShortcut("/memory", "Memory", "打开 Memory 管理和本地记忆检索。"),
    NbgSlashCommandShortcut("/provider", "Provider", "打开 URL API 与 ProviderProfile 配置。"),
    NbgSlashCommandShortcut("/usage", "Usage", "打开学习页的 token、成本、延迟和洞察视图。"),
    NbgSlashCommandShortcut(
      "/compress",
      "压缩上下文",
      if (compressionAvailable) "压缩当前长对话并创建可继续的新会话。" else "后端暂未报告当前会话可压缩。",
    ),
  )

@Composable
internal fun NbgSlashCommandDialog(
  commands: List<HanakoSlashCommand>,
  shortcuts: List<NbgSlashCommandShortcut>,
  loading: Boolean,
  error: String?,
  onRefresh: () -> Unit,
  onRunCommand: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val normalizedQuery = query.trim()
  val filteredShortcuts = shortcuts.filter { shortcut ->
    normalizedQuery.isBlank() ||
      shortcut.command.contains(normalizedQuery, ignoreCase = true) ||
      shortcut.title.contains(normalizedQuery, ignoreCase = true) ||
      shortcut.description.contains(normalizedQuery, ignoreCase = true)
  }
  val filteredCommands = commands.filter { command ->
    normalizedQuery.isBlank() ||
      command.slash.contains(normalizedQuery, ignoreCase = true) ||
      command.aliases.any { it.contains(normalizedQuery, ignoreCase = true) } ||
      command.description.contains(normalizedQuery, ignoreCase = true) ||
      command.source.contains(normalizedQuery, ignoreCase = true)
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Slash command center") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          label = { Text("搜索命令") },
        )
        if (loading) {
          Text(
            text = "正在加载后端 slash commands...",
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
          )
        }
        error?.takeIf { it.isNotBlank() }?.let {
          Text(
            text = "加载失败：$it",
            color = NbgAgentColors.StatusRed,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        LazyColumn(
          modifier = Modifier.heightIn(max = 430.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (filteredShortcuts.isNotEmpty()) {
            item {
              NbgSlashCommandSectionLabel("Android 控制")
            }
            items(filteredShortcuts, key = { "shortcut:${it.command}" }) { shortcut ->
              NbgSlashShortcutRow(
                shortcut = shortcut,
                onClick = { onRunCommand(shortcut.command) },
              )
            }
          }
          if (filteredCommands.isNotEmpty()) {
            item {
              NbgSlashCommandSectionLabel("后端命令")
            }
            items(filteredCommands, key = { "command:${it.source}:${it.name}" }) { command ->
              NbgSlashCommandRow(
                command = command,
                onClick = { onRunCommand(command.slash) },
              )
            }
          }
          if (filteredShortcuts.isEmpty() && filteredCommands.isEmpty() && !loading) {
            item {
              Text(
                text = "没有匹配的命令",
                color = NbgAgentColors.TextMuted,
                fontSize = 12.sp,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onRefresh) {
        Icon(Icons.Filled.Refresh, contentDescription = null)
        Text("刷新")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("关闭") }
    },
  )
}

@Composable
private fun NbgSlashCommandSectionLabel(text: String) {
  Text(
    text = text,
    color = NbgAgentColors.TextStrong,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
private fun NbgSlashShortcutRow(
  shortcut: NbgSlashCommandShortcut,
  onClick: () -> Unit,
) {
  NbgSlashCommandSurface(onClick = onClick) {
    NbgSlashCommandText(
      command = shortcut.command,
      title = shortcut.title,
      description = shortcut.description,
      meta = "android",
    )
  }
}

@Composable
private fun NbgSlashCommandRow(
  command: HanakoSlashCommand,
  onClick: () -> Unit,
) {
  val aliasText = command.aliases.take(3).joinToString(" ") { "/$it" }
  val meta = listOf(command.source, command.scope, command.permission)
    .filter { it.isNotBlank() }
    .joinToString(" · ")
  NbgSlashCommandSurface(onClick = onClick) {
    NbgSlashCommandText(
      command = command.slash,
      title = aliasText.ifBlank { command.name },
      description = command.description.ifBlank { "执行 ${command.slash}" },
      meta = meta.ifBlank { "core" },
    )
  }
}

@Composable
private fun NbgSlashCommandSurface(
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    color = NbgAgentColors.SurfaceContainer.copy(alpha = 0.72f),
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder.copy(alpha = 0.72f)),
  ) {
    content()
  }
}

@Composable
private fun NbgSlashCommandText(
  command: String,
  title: String,
  description: String,
  meta: String,
) {
  Row(
    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = command,
      color = NbgAgentColors.TextStrong,
      fontFamily = FontFamily.Monospace,
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(0.82f),
    )
    Column(modifier = Modifier.weight(1.45f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        color = NbgAgentColors.TextStrong,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = description,
        color = NbgAgentColors.TextMuted,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = meta,
      color = NbgAgentColors.TextMuted,
      fontSize = 10.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(0.62f),
    )
  }
}

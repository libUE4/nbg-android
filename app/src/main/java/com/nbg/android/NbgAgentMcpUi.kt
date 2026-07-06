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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.StopCircle

@Composable
internal fun NbgMcpScreen(
  state: HanakoMcpState,
  loading: Boolean,
  error: String?,
  busyKey: String?,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onReload: () -> Unit,
  onSetEnabled: (Boolean) -> Unit,
  onAddConnector: (HanakoMcpConnectorInput) -> Unit,
  onConnectorAction: (String, String) -> Unit,
  onDeleteConnector: (String) -> Unit,
  onSetAgentConnector: (String, Boolean) -> Unit,
  onSetAgentTool: (String, String, Boolean) -> Unit,
) {
  var addOpen by remember { mutableStateOf(false) }
  var deleteTarget by remember { mutableStateOf<HanakoMcpConnector?>(null) }
  NbgShellSubPage(
    title = "MCP 服务",
    subtitle = nbgMcpSubtitle(state, loading),
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
        NbgMcpSummaryCard(
          state = state,
          loading = loading,
          error = error,
          busy = busyKey != null,
          onReload = onReload,
          onSetEnabled = onSetEnabled,
          onAdd = { addOpen = true },
        )
      }
      if (state.connectors.isEmpty()) {
        item {
          NbgMcpEmptyState()
        }
      } else {
        items(state.connectors, key = { it.id }) { connector ->
          NbgMcpConnectorRow(
            state = state,
            connector = connector,
            busyKey = busyKey,
            onConnectorAction = onConnectorAction,
            onRequestDelete = { deleteTarget = connector },
            onSetAgentConnector = onSetAgentConnector,
            onSetAgentTool = onSetAgentTool,
          )
        }
      }
    }
  }
  if (addOpen) {
    NbgMcpAddConnectorDialog(
      busy = busyKey != null,
      onSave = { input ->
        addOpen = false
        onAddConnector(input)
      },
      onDismiss = { addOpen = false },
    )
  }
  deleteTarget?.let { target ->
    NbgMcpDeleteConnectorDialog(
      connectorName = target.displayName,
      busy = busyKey != null,
      onConfirm = {
        deleteTarget = null
        onDeleteConnector(target.id)
      },
      onDismiss = { if (busyKey == null) deleteTarget = null },
    )
  }
}

@Composable
private fun NbgMcpSummaryCard(
  state: HanakoMcpState,
  loading: Boolean,
  error: String?,
  busy: Boolean,
  onReload: () -> Unit,
  onSetEnabled: (Boolean) -> Unit,
  onAdd: () -> Unit,
) {
  val runningCount = state.connectors.count { it.running }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NbgMcpIconBox(icon = Icons.Filled.Settings, active = state.enabled)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = if (state.enabled) "MCP 已启用" else "MCP 未启用",
            color = NbgAgentColors.TextStrong,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${state.connectors.size} 个连接器 · $runningCount 运行中 · 当前 Agent: $HANA_DEFAULT_MCP_AGENT_ID",
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgMcpStatusPill(
          text = if (loading) "读取中" else if (state.enabled) "ON" else "OFF",
          color = if (state.enabled) NbgAgentColors.StatusGreen else NbgAgentColors.TextMuted,
        )
      }
      if (!error.isNullOrBlank()) {
        Text(
          text = error,
          color = NbgAgentColors.StatusRed,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NbgInlineActionButton(
          label = if (loading) "读取中" else "刷新",
          icon = Icons.Filled.Refresh,
          enabled = !loading && !busy,
          onClick = onReload,
        )
        NbgInlineActionButton(
          label = if (state.enabled) "关闭" else "启用",
          icon = if (state.enabled) Icons.Filled.Block else Icons.Filled.CheckCircle,
          primary = !state.enabled,
          enabled = !loading && !busy,
          onClick = { onSetEnabled(!state.enabled) },
        )
        NbgInlineActionButton(
          label = "添加",
          icon = Icons.Filled.Add,
          primary = true,
          enabled = !loading && !busy,
          onClick = onAdd,
        )
      }
    }
  }
}

@Composable
private fun NbgMcpAddConnectorDialog(
  busy: Boolean,
  onSave: (HanakoMcpConnectorInput) -> Unit,
  onDismiss: () -> Unit,
) {
  var templateKey by remember { mutableStateOf("custom-http") }
  var name by remember { mutableStateOf("自定义 MCP") }
  var url by remember { mutableStateOf("http://127.0.0.1:37666/mcp") }
  var description by remember { mutableStateOf("自定义 HTTP/SSE MCP 服务") }
  var command by remember { mutableStateOf("") }
  var argsText by remember { mutableStateOf("") }
  var cwd by remember { mutableStateOf("") }
  var transport by remember { mutableStateOf("sse") }
  val isStdio = transport == "stdio"
  val canSave = name.trim().isNotBlank() &&
    (if (isStdio) command.trim().isNotBlank() else url.trim().isNotBlank()) &&
    !busy

  fun applyTemplate(template: NbgMcpTemplate) {
    templateKey = template.key
    name = template.name
    url = template.url
    description = template.description
    command = template.command
    argsText = template.args.joinToString(" ")
    cwd = template.cwd
    transport = template.transport
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("添加 MCP", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "推荐模板",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
        )
        NbgMcpTemplateChips(selectedKey = templateKey, onSelect = ::applyTemplate)
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("名称") },
          placeholder = { Text("例如 Android APK MCP") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        if (isStdio) {
          OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("命令") },
            placeholder = { Text("npx") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = argsText,
            onValueChange = { argsText = it },
            label = { Text("参数") },
            placeholder = { Text("-y @scope/mcp-server") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = cwd,
            onValueChange = { cwd = it },
            label = { Text("工作目录") },
            placeholder = { Text("可选，例如 /root/project") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("地址") },
            placeholder = { Text("http://127.0.0.1:37666/mcp") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("描述") },
          placeholder = { Text("可选") },
          minLines = 2,
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NbgMcpTransportButton(
            label = "SSE",
            selected = transport == "sse",
            onClick = { transport = "sse" },
          )
          NbgMcpTransportButton(
            label = "Streamable",
            selected = transport == "streamable-http",
            onClick = { transport = "streamable-http" },
          )
          NbgMcpTransportButton(
            label = "自动",
            selected = transport == "remote",
            onClick = { transport = "remote" },
          )
          NbgMcpTransportButton(
            label = "stdio",
            selected = transport == "stdio",
            onClick = { transport = "stdio" },
          )
        }
        Text(
          text = if (isStdio) {
            "stdio 需要 Android 后端环境里存在对应命令；保存后会自动启用 MCP、启动连接器、刷新工具，并给当前 Hanako Agent 打开这些工具。"
          } else {
            "保存后会自动启用 MCP、启动连接器、刷新工具，并给当前 Hanako Agent 打开这些工具。"
          },
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      }
    },
    confirmButton = {
      NbgDialogAction(
        label = if (busy) "处理中" else "保存",
        primary = true,
        enabled = canSave,
        onClick = {
          onSave(
            HanakoMcpConnectorInput(
              name = name,
              url = url,
              transport = transport,
              description = description,
              command = command,
              args = nbgSplitMcpArgs(argsText),
              cwd = cwd,
            ),
          )
        },
      )
    },
    dismissButton = {
      NbgDialogAction(label = "取消", onClick = onDismiss)
    },
  )
}

private data class NbgMcpTemplate(
  val key: String,
  val title: String,
  val name: String,
  val description: String,
  val transport: String,
  val url: String = "",
  val command: String = "",
  val args: List<String> = emptyList(),
  val cwd: String = "",
)

private val NBG_MCP_TEMPLATES = listOf(
  NbgMcpTemplate(
    key = "custom-http",
    title = "自定义 HTTP",
    name = "自定义 MCP",
    description = "自定义 HTTP/SSE MCP 服务",
    transport = "sse",
    url = "http://127.0.0.1:37666/mcp",
  ),
  NbgMcpTemplate(
    key = "codebase-memory",
    title = "代码记忆",
    name = "codebase-memory-mcp",
    description = "外部代码库记忆 MCP，适合保存项目事实和长期上下文",
    transport = "remote",
    url = "http://127.0.0.1:3100/mcp",
  ),
  NbgMcpTemplate(
    key = "cognee",
    title = "Cognee",
    name = "Cognee Memory",
    description = "外部知识图谱/长期记忆 MCP，需要先启动 Cognee 服务",
    transport = "remote",
    url = "http://127.0.0.1:8000/mcp",
  ),
  NbgMcpTemplate(
    key = "custom-stdio",
    title = "stdio",
    name = "stdio MCP",
    description = "通过本地命令启动的 MCP 服务",
    transport = "stdio",
    command = "npx",
    args = listOf("-y", "mcp-server"),
  ),
)

@Composable
private fun NbgMcpTemplateChips(
  selectedKey: String,
  onSelect: (NbgMcpTemplate) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    NBG_MCP_TEMPLATES.chunked(2).forEach { rowItems ->
      Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        rowItems.forEach { template ->
          FilterChip(
            selected = selectedKey == template.key,
            onClick = { onSelect(template) },
            label = { Text(template.title) },
          )
        }
      }
    }
  }
}

private fun nbgSplitMcpArgs(value: String): List<String> =
  value.split(Regex("\\s+"))
    .map { it.trim() }
    .filter { it.isNotBlank() }

@Composable
private fun NbgMcpDeleteConnectorDialog(
  connectorName: String,
  busy: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    containerColor = NbgAgentColors.Drawer,
    title = { Text("删除 MCP 服务", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Text(
        text = "确定删除「$connectorName」？连接器会从 HanakoPro MCP 配置中移除，已开启的 Agent 工具也不会再生效。",
        color = NbgAgentColors.TextMuted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      )
    },
    confirmButton = {
      NbgDialogAction(
        label = if (busy) "处理中" else "删除",
        primary = true,
        enabled = !busy,
        onClick = onConfirm,
      )
    },
    dismissButton = {
      NbgDialogAction(label = "取消", enabled = !busy, onClick = onDismiss)
    },
  )
}

@Composable
private fun NbgMcpTransportButton(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = if (selected) NbgAgentColors.PrimaryContainer else NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, if (selected) NbgAgentColors.SurfaceBorder else NbgAgentColors.InputBorder),
  ) {
    Text(
      text = label,
      color = if (selected) NbgAgentColors.TextStrong else NbgAgentColors.TextMuted,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
    )
  }
}

@Composable
private fun NbgMcpEmptyState() {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = "还没有 MCP 连接器",
        color = NbgAgentColors.TextStrong,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "这里读取 HanakoPro 的 MCP 插件配置。先在服务端或 PC 端添加连接器，Android 会同步显示并控制启停。",
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
      )
    }
  }
}

@Composable
private fun NbgMcpConnectorRow(
  state: HanakoMcpState,
  connector: HanakoMcpConnector,
  busyKey: String?,
  onConnectorAction: (String, String) -> Unit,
  onRequestDelete: () -> Unit,
  onSetAgentConnector: (String, Boolean) -> Unit,
  onSetAgentTool: (String, String, Boolean) -> Unit,
) {
  var expanded by remember(connector.id) { mutableStateOf(false) }
  val agentEnabled = state.connectorEnabled(connector.id)
  val connectorBusy = busyKey == "mcp:agent:${connector.id}" ||
    busyKey == "mcp:delete:${connector.id}" ||
    busyKey?.startsWith("mcp:connector:${connector.id}:") == true
  Surface(
    onClick = { if (connector.tools.isNotEmpty()) expanded = !expanded },
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = if (agentEnabled) NbgAgentColors.Selected else NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, if (connector.running) NbgAgentColors.PrimarySoft else NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NbgMcpIconBox(
          icon = if (connector.running) Icons.Filled.CheckCircle else Icons.Filled.Settings,
          active = connector.running,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = connector.displayName,
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${nbgMcpTransportLabel(connector.transport)} · ${nbgMcpStatusLabel(connector.status)} · ${connector.tools.size} 工具",
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = connector.target,
            color = NbgAgentColors.TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgMcpStatusPill(
          text = if (agentEnabled) "Agent 开" else "Agent 关",
          color = if (agentEnabled) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
        )
      }
      if (connector.description.isNotBlank()) {
        Text(
          text = connector.description,
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      NbgMcpConnectorMeta(connector)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NbgInlineActionButton(
          label = if (agentEnabled) "禁用" else "启用",
          icon = if (agentEnabled) Icons.Filled.Block else Icons.Filled.CheckCircle,
          primary = !agentEnabled,
          enabled = busyKey == null,
          onClick = { onSetAgentConnector(connector.id, !agentEnabled) },
        )
        NbgInlineActionButton(
          label = if (connector.running) "停止" else "启动",
          icon = if (connector.running) HugeIcons.StopCircle else Icons.Filled.PlayArrow,
          enabled = busyKey == null && state.enabled,
          onClick = { onConnectorAction(connector.id, if (connector.running) "stop" else "start") },
        )
        NbgInlineActionButton(
          label = "工具",
          icon = Icons.Filled.Refresh,
          enabled = busyKey == null && state.enabled && connector.running,
          onClick = { onConnectorAction(connector.id, "refresh-tools") },
        )
        NbgInlineActionButton(
          label = "删除",
          icon = Icons.Filled.Delete,
          enabled = busyKey == null,
          onClick = onRequestDelete,
        )
        if (connectorBusy) {
          NbgMcpStatusPill(text = "处理中", color = NbgAgentColors.StatusYellow)
        }
      }
      if (expanded && connector.tools.isNotEmpty()) {
        NbgMcpToolList(
          state = state,
          connector = connector,
          busyKey = busyKey,
          agentEnabled = agentEnabled,
          onSetAgentTool = onSetAgentTool,
        )
      }
    }
  }
}

@Composable
private fun NbgMcpConnectorMeta(connector: HanakoMcpConnector) {
  val parts = listOfNotNull(
    "自动启动".takeIf { connector.autoStart },
    "${connector.envCount} env".takeIf { connector.envCount > 0 },
    "${connector.headersCount} headers".takeIf { connector.headersCount > 0 },
    nbgMcpAuthLabel(connector).takeIf { it.isNotBlank() },
    "${connector.timeoutMs}ms".takeIf { connector.timeoutMs > 0L },
  )
  if (parts.isEmpty()) return
  Text(
    text = parts.joinToString(" · "),
    color = NbgAgentColors.TextMuted,
    fontSize = 10.sp,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
private fun NbgMcpToolList(
  state: HanakoMcpState,
  connector: HanakoMcpConnector,
  busyKey: String?,
  agentEnabled: Boolean,
  onSetAgentTool: (String, String, Boolean) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(NbgAgentColors.SurfaceLow, RoundedCornerShape(12.dp))
      .padding(horizontal = 8.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    connector.tools.take(24).forEach { tool ->
      val enabled = state.toolEnabled(connector.id, tool.name)
      val toolBusy = busyKey == "mcp:tool:${connector.id}:${tool.name}"
      NbgMcpToolRow(
        tool = tool,
        enabled = enabled,
        clickable = agentEnabled && busyKey == null,
        busy = toolBusy,
        onClick = { onSetAgentTool(connector.id, tool.name, !enabled) },
      )
    }
    if (connector.tools.size > 24) {
      Text(
        text = "还有 ${connector.tools.size - 24} 个工具未显示",
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
      )
    }
  }
}

@Composable
private fun NbgMcpToolRow(
  tool: HanakoMcpTool,
  enabled: Boolean,
  clickable: Boolean,
  busy: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = clickable,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    color = if (enabled) NbgAgentColors.Selected else NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .background(if (enabled) NbgAgentColors.StatusGreen else NbgAgentColors.TextDisabled, CircleShape),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = tool.displayName,
          color = if (clickable || enabled) NbgAgentColors.TextStrong else NbgAgentColors.TextDisabled,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (tool.description.isNotBlank()) {
          Text(
            text = tool.description,
            color = NbgAgentColors.TextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Text(
        text = if (busy) "..." else if (enabled) "开" else "关",
        color = if (enabled) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

@Composable
private fun NbgMcpIconBox(icon: ImageVector, active: Boolean) {
  Box(
    modifier = Modifier
      .size(38.dp)
      .background(if (active) NbgAgentColors.PrimarySoft else NbgAgentColors.SurfaceLow, RoundedCornerShape(13.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (active) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun NbgMcpStatusPill(text: String, color: Color) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Text(
      text = text,
      color = color,
      fontSize = 10.sp,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
    )
  }
}

private fun nbgMcpSubtitle(state: HanakoMcpState, loading: Boolean): String {
  if (loading) return "正在读取 HanakoPro MCP"
  val running = state.connectors.count { it.running }
  return "${state.connectors.size} 个连接器 · $running 运行中"
}

private fun nbgMcpTransportLabel(transport: String): String =
  when (transport) {
    "stdio" -> "本地"
    "streamable-http" -> "Streamable HTTP"
    "sse" -> "SSE"
    else -> "远程"
  }

private fun nbgMcpStatusLabel(status: String): String =
  when (status.lowercase()) {
    "running" -> "运行中"
    "stopped" -> "已停止"
    else -> status.ifBlank { "未知" }
  }

private fun nbgMcpAuthLabel(connector: HanakoMcpConnector): String =
  when (connector.authType) {
    "bearer" -> "Bearer"
    "oauth" -> if (connector.authStatus == "authorized") "OAuth 已连接" else "OAuth 未连接"
    else -> ""
  }

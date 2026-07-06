package com.nbg.android

import android.animation.ValueAnimator
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileAdd
import me.rerere.hugeicons.stroke.FileEdit
import me.rerere.hugeicons.stroke.FileView
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tools

@Composable
internal fun NbgConversationDrawer(
  conversations: List<NbgAgentConversation>,
  searchResults: List<NbgAgentConversation>,
  searching: Boolean,
  selectedConversationPath: String?,
  connectionLabel: String,
  connected: Boolean,
  runStatus: NbgChatRunStatus,
  contextUsageLabel: String?,
  mcpState: HanakoMcpState,
  skillsSnapshot: HanakoSkillsSnapshot,
  capabilities: NbgCapabilityRegistry,
  onSearch: (String) -> Unit,
  onSelectConversation: (String) -> Unit,
  onNewConversation: () -> Unit,
  onOpenTerminal: () -> Unit,
  onOpenFileShare: () -> Unit,
  onOpenAgents: () -> Unit,
  onOpenMcp: () -> Unit,
  onOpenSkills: () -> Unit,
  onOpenPets: () -> Unit,
  onOpenAppearance: () -> Unit,
  onOpenProviders: () -> Unit,
  onOpenToolsetsDoctor: () -> Unit,
  onOpenDiagnosticsExport: () -> Unit,
  onRenameConversation: (String, String) -> Unit,
  onDeleteConversation: (String) -> Unit,
  onPinConversation: (String, Boolean) -> Unit,
) {
  var localSearchQuery by remember { mutableStateOf("") }
  var renameTarget by remember { mutableStateOf<NbgAgentConversation?>(null) }
  var deleteTarget by remember { mutableStateOf<NbgAgentConversation?>(null) }
  var settingsOpen by remember { mutableStateOf(false) }
  LaunchedEffect(localSearchQuery) {
    if (localSearchQuery.isBlank()) {
      onSearch("")
      return@LaunchedEffect
    }
    delay(350)
    onSearch(localSearchQuery.trim())
  }
  val activeList = if (localSearchQuery.isNotBlank()) searchResults else conversations
  val emptyText = when {
    searching -> "正在搜索 HanakoPro"
    localSearchQuery.isNotBlank() -> "没有匹配的会话"
    else -> "连接 HanakoPro 后显示会话"
  }

  ModalDrawerSheet(
    drawerContainerColor = NbgAgentColors.Drawer,
    modifier = Modifier.width(332.dp),
  ) {
    AnimatedContent(
      targetState = settingsOpen,
      transitionSpec = {
        if (targetState) {
          slideInVertically(animationSpec = tween(nbgMotionDuration(220), easing = FastOutSlowInEasing)) { it / 8 } +
            fadeIn(animationSpec = tween(nbgMotionDuration(160), easing = FastOutSlowInEasing)) togetherWith
            slideOutVertically(animationSpec = tween(nbgMotionDuration(160), easing = FastOutSlowInEasing)) { -it / 12 } +
            fadeOut(animationSpec = tween(nbgMotionDuration(120), easing = FastOutSlowInEasing))
        } else {
          slideInVertically(animationSpec = tween(nbgMotionDuration(220), easing = FastOutSlowInEasing)) { -it / 12 } +
            fadeIn(animationSpec = tween(nbgMotionDuration(160), easing = FastOutSlowInEasing)) togetherWith
            slideOutVertically(animationSpec = tween(nbgMotionDuration(160), easing = FastOutSlowInEasing)) { it / 8 } +
            fadeOut(animationSpec = tween(nbgMotionDuration(120), easing = FastOutSlowInEasing))
        }
      },
      label = "drawer settings page",
    ) { open ->
      if (open) {
        NbgDrawerSettingsPage(
          onBack = { settingsOpen = false },
          connected = connected,
          mcpState = mcpState,
          skillsSnapshot = skillsSnapshot,
          capabilities = capabilities,
          onOpenTerminal = onOpenTerminal,
          onOpenFileShare = onOpenFileShare,
          onOpenAgents = onOpenAgents,
          onOpenProviders = onOpenProviders,
          onOpenToolsetsDoctor = onOpenToolsetsDoctor,
          onOpenMcp = onOpenMcp,
          onOpenSkills = onOpenSkills,
          onOpenPets = onOpenPets,
          onOpenAppearance = onOpenAppearance,
          onOpenDiagnosticsExport = onOpenDiagnosticsExport,
        )
      } else {
        NbgDrawerMainPage(
          conversations = activeList,
          emptyText = emptyText,
          searching = searching,
          selectedConversationPath = selectedConversationPath,
          localSearchQuery = localSearchQuery,
          connectionLabel = connectionLabel,
          connected = connected,
          runStatus = runStatus,
          contextUsageLabel = contextUsageLabel,
          mcpState = mcpState,
          skillsSnapshot = skillsSnapshot,
          capabilities = capabilities,
          onSearchQueryChange = { localSearchQuery = it },
          onSearchClear = {
            localSearchQuery = ""
            onSearch("")
          },
          onNewConversation = onNewConversation,
          onOpenSettings = { settingsOpen = true },
          onSelectConversation = onSelectConversation,
          onRenameConversation = { renameTarget = it },
          onDeleteConversation = { deleteTarget = it },
          onPinConversation = onPinConversation,
        )
      }
    }
  }

  renameTarget?.let { target ->
    NbgRenameSessionDialog(
      initialTitle = target.title,
      onDismiss = { renameTarget = null },
      onConfirm = { title ->
        renameTarget = null
        onRenameConversation(target.path, title)
      },
    )
  }

  deleteTarget?.let { target ->
    NbgDeleteSessionDialog(
      title = target.title,
      onDismiss = { deleteTarget = null },
      onConfirm = {
        deleteTarget = null
        onDeleteConversation(target.path)
      },
    )
  }
}

@Composable
private fun NbgDrawerMainPage(
  conversations: List<NbgAgentConversation>,
  emptyText: String,
  searching: Boolean,
  selectedConversationPath: String?,
  localSearchQuery: String,
  connectionLabel: String,
  connected: Boolean,
  runStatus: NbgChatRunStatus,
  contextUsageLabel: String?,
  mcpState: HanakoMcpState,
  skillsSnapshot: HanakoSkillsSnapshot,
  capabilities: NbgCapabilityRegistry,
  onSearchQueryChange: (String) -> Unit,
  onSearchClear: () -> Unit,
  onNewConversation: () -> Unit,
  onOpenSettings: () -> Unit,
  onSelectConversation: (String) -> Unit,
  onRenameConversation: (NbgAgentConversation) -> Unit,
  onDeleteConversation: (NbgAgentConversation) -> Unit,
  onPinConversation: (String, Boolean) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxHeight()
      .padding(horizontal = 12.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    val activeTaskCount = if (runStatus.active) 1 else 0
    NbgDrawerWorkbenchHeader(
      connectionLabel = connectionLabel,
      connected = connected,
      warning = runStatus.warning,
      contextUsageLabel = contextUsageLabel,
    )
    NbgDrawerAgentOverview(
      statusLabel = runStatus.label,
      statusActive = runStatus.active,
      statusWarning = runStatus.warning,
      mcpCount = mcpState.connectors.size,
      skillsCount = skillsSnapshot.visibleSkills.size,
      tasksCount = activeTaskCount,
      capabilityLabel = capabilities.summaryLabel,
      capabilityHealth = capabilities.worstHealth,
    )
    NbgDrawerSearchField(
      value = localSearchQuery,
      searching = searching,
      onValueChange = onSearchQueryChange,
      onClear = onSearchClear,
    )
    NbgDrawerSectionLabel("workspace")
    NbgDrawerWorkspaceTile(
      title = "工作台设置",
      subtitle = "MCP、Skills、终端、外观",
      meta = "setup",
      iconText = "SET",
      onClick = onOpenSettings,
    )
    NbgDrawerSectionLabel("recent sessions")
    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    ) {
      item {
        NbgDrawerPrimaryAction(
          label = "新建对话",
          icon = HugeIcons.Add01,
          onClick = onNewConversation,
        )
      }
      if (conversations.isEmpty()) {
        item {
          Text(
            text = emptyText,
            color = NbgAgentColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
          )
        }
      }
      items(conversations, key = { it.path }) { conversation ->
        NbgConversationRow(
          conversation = conversation,
          selected = conversation.path == selectedConversationPath,
          onClick = { onSelectConversation(conversation.path) },
          onRename = { onRenameConversation(conversation) },
          onDelete = { onDeleteConversation(conversation) },
          onPinToggle = { onPinConversation(conversation.path, !conversation.pinned) },
        )
      }
    }
    NbgDrawerTerminalFooter(
      connected = connected,
      runStatus = runStatus,
    )
  }
}

@Composable
private fun NbgDrawerSettingsPage(
  onBack: () -> Unit,
  connected: Boolean,
  mcpState: HanakoMcpState,
  skillsSnapshot: HanakoSkillsSnapshot,
  capabilities: NbgCapabilityRegistry,
  onOpenTerminal: () -> Unit,
  onOpenFileShare: () -> Unit,
  onOpenAgents: () -> Unit,
  onOpenProviders: () -> Unit,
  onOpenToolsetsDoctor: () -> Unit,
  onOpenMcp: () -> Unit,
  onOpenSkills: () -> Unit,
  onOpenPets: () -> Unit,
  onOpenAppearance: () -> Unit,
  onOpenDiagnosticsExport: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxHeight()
      .padding(horizontal = 12.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      NbgPlainIconButton(
        icon = HugeIcons.ArrowLeft01,
        contentDescription = "返回会话抽屉",
        onClick = onBack,
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = "工作台设置",
          color = NbgAgentColors.TextStrong,
          fontSize = 16.sp,
          lineHeight = 20.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "这些功能入口集中存放在这里",
          color = NbgAgentColors.TextMuted,
          fontSize = 11.5.sp,
          lineHeight = 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(19.dp),
      color = NbgAgentColors.SurfaceContainer,
      border = BorderStroke(1.dp, NbgAgentColors.PrimarySoft.copy(alpha = 0.58f)),
    ) {
      Column(
        modifier = Modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = "Agent 功能中心",
          color = NbgAgentColors.TextStrong,
          fontSize = 13.sp,
          lineHeight = 17.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "用于管理 MCP、Skills、终端、文件共享、网址 API 和外观。",
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          lineHeight = 15.sp,
        )
      }
    }
    NbgDrawerSectionLabel("features")
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        NbgDrawerFeatureCard(
          iconText = "DR",
          title = "Toolsets / Doctor",
          subtitle = "能力开关、状态检查和故障入口",
          state = capabilities.summaryLabel,
          active = capabilities.worstHealth == NbgCapabilityHealth.Healthy,
          warning = capabilities.worstHealth == NbgCapabilityHealth.Degraded || capabilities.worstHealth == NbgCapabilityHealth.Unknown,
          danger = capabilities.worstHealth == NbgCapabilityHealth.Failed,
          onClick = onOpenToolsetsDoctor,
        )
      }
      item {
        val capability = capabilities.byId("hanako")
        NbgDrawerFeatureCard(
          iconText = "AI",
          title = "Agents",
          subtitle = "编排模式、MCP、Skills 和 Memory 权限",
          state = capability.nbgDrawerCapabilityState(),
          active = capability?.health == NbgCapabilityHealth.Healthy,
          warning = capability?.health == NbgCapabilityHealth.Degraded || capability?.health == NbgCapabilityHealth.Unknown,
          danger = capability?.health == NbgCapabilityHealth.Failed,
          onClick = onOpenAgents,
        )
      }
      item {
        val capability = capabilities.byId("terminal")
        NbgDrawerFeatureCard(
          iconText = "$" + "_",
          title = "终端",
          subtitle = "查看 shell 状态、历史输出和打断操作",
          state = capability.nbgDrawerCapabilityState(),
          active = capability?.health == NbgCapabilityHealth.Healthy,
          warning = capability?.health == NbgCapabilityHealth.Degraded || capability?.health == NbgCapabilityHealth.Unknown,
          danger = capability?.health == NbgCapabilityHealth.Failed,
          darkIcon = true,
          onClick = onOpenTerminal,
        )
      }
      item {
        val capability = capabilities.byId("ftp")
        NbgDrawerFeatureCard(
          iconText = "FTP",
          title = "FTP 共享",
          subtitle = "文件共享、导入导出和移动端传输",
          state = capability.nbgDrawerCapabilityState(),
          active = capability?.health == NbgCapabilityHealth.Healthy,
          warning = capability?.health == NbgCapabilityHealth.Degraded || capability?.health == NbgCapabilityHealth.Unknown,
          danger = capability?.health == NbgCapabilityHealth.Failed,
          onClick = onOpenFileShare,
        )
      }
      item {
        val capability = capabilities.byId("url_api")
        NbgDrawerFeatureCard(
          iconText = "API",
          title = "网址 API",
          subtitle = "添加上游地址、密钥和可用模型",
          state = capability.nbgDrawerCapabilityState(),
          active = capability?.health == NbgCapabilityHealth.Healthy,
          warning = capability?.health == NbgCapabilityHealth.Degraded || capability?.health == NbgCapabilityHealth.Unknown,
          danger = capability?.health == NbgCapabilityHealth.Failed,
          onClick = onOpenProviders,
        )
      }
      item {
        val capability = capabilities.byId("mcp")
        NbgDrawerFeatureCard(
          iconText = "M",
          title = "MCP 服务",
          subtitle = "连接器、agent 级启用、tool 级开关",
          state = capability.nbgDrawerCapabilityState(),
          active = capability?.health == NbgCapabilityHealth.Healthy,
          warning = capability?.health == NbgCapabilityHealth.Degraded || capability?.health == NbgCapabilityHealth.Unknown,
          danger = capability?.health == NbgCapabilityHealth.Failed,
          onClick = onOpenMcp,
        )
      }
      item {
        val capability = capabilities.byId("skills")
        NbgDrawerFeatureCard(
          iconText = "S",
          title = "Skills",
          subtitle = "技能列表、bundles、external paths 和 reload",
          state = capability.nbgDrawerCapabilityState(),
          active = capability?.health == NbgCapabilityHealth.Healthy,
          warning = capability?.health == NbgCapabilityHealth.Degraded || capability?.health == NbgCapabilityHealth.Unknown,
          danger = capability?.health == NbgCapabilityHealth.Failed,
          onClick = onOpenSkills,
        )
      }
      item {
        val capability = capabilities.byId("pets")
        NbgDrawerFeatureCard(
          iconText = "P",
          title = "桌宠",
          subtitle = "动作库、PetDex 安装和浮层显示状态",
          state = capability.nbgDrawerCapabilityState(),
          active = capability?.health == NbgCapabilityHealth.Healthy,
          warning = capability?.health == NbgCapabilityHealth.Degraded || capability?.health == NbgCapabilityHealth.Unknown,
          danger = capability?.health == NbgCapabilityHealth.Failed,
          onClick = onOpenPets,
        )
      }
      item {
        NbgDrawerFeatureCard(
          iconText = "Aa",
          title = "外观",
          subtitle = "主题、字体和代码/终端显示偏好",
          state = "theme",
          onClick = onOpenAppearance,
        )
      }
      item {
        val capability = capabilities.byId("feedback_export")
        NbgDrawerFeatureCard(
          iconText = "JSON",
          title = "诊断导出",
          subtitle = "生成本地脱敏状态包用于排查问题",
          state = capability.nbgDrawerCapabilityState(),
          active = capability?.health == NbgCapabilityHealth.Healthy,
          warning = capability?.health == NbgCapabilityHealth.Degraded || capability?.health == NbgCapabilityHealth.Unknown,
          danger = capability?.health == NbgCapabilityHealth.Failed,
          onClick = onOpenDiagnosticsExport,
        )
      }
    }
  }
}

@Composable
private fun NbgDrawerWorkbenchHeader(
  connectionLabel: String,
  connected: Boolean,
  warning: Boolean,
  contextUsageLabel: String?,
) {
  val statusColor = when {
    warning -> NbgAgentColors.StatusRed
    connected -> NbgAgentColors.StatusGreen
    else -> NbgAgentColors.StatusYellow
  }
  val contextTag = nbgDrawerContextTag(contextUsageLabel)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(
      modifier = Modifier
        .size(42.dp)
        .background(NbgAgentColors.CodeBlock, RoundedCornerShape(16.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "N",
        color = NbgAgentColors.CodeText,
        fontSize = 13.sp,
        lineHeight = 15.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
      )
    }
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = "NBG Workbench",
        color = NbgAgentColors.TextStrong,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier = Modifier
            .size(6.dp)
            .background(statusColor, CircleShape),
        )
        Text(
          text = connectionLabel.ifBlank { if (connected) "HanakoPro 已连接" else "HanakoPro 未连接" },
          color = NbgAgentColors.TextMuted,
          fontSize = 11.5.sp,
          lineHeight = 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (contextTag != null) {
      NbgSheetTag(contextTag, primary = connected && !warning)
    }
  }
}

@Composable
private fun NbgDrawerAgentOverview(
  statusLabel: String,
  statusActive: Boolean,
  statusWarning: Boolean,
  mcpCount: Int,
  skillsCount: Int,
  tasksCount: Int,
  capabilityLabel: String,
  capabilityHealth: NbgCapabilityHealth,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(19.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.PrimarySoft.copy(alpha = 0.56f)),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Agent 运行环境",
          color = NbgAgentColors.TextStrong,
          fontSize = 13.sp,
          lineHeight = 17.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        NbgSheetTag(
          nbgDrawerCompactTag(statusLabel.ifBlank { "空闲" }),
          primary = statusActive && !statusWarning,
          danger = statusWarning,
        )
        NbgSheetTag(
          nbgDrawerCompactTag(capabilityLabel, maxLength = 10),
          primary = capabilityHealth == NbgCapabilityHealth.Healthy,
          warning = capabilityHealth == NbgCapabilityHealth.Degraded || capabilityHealth == NbgCapabilityHealth.Unknown,
          danger = capabilityHealth == NbgCapabilityHealth.Failed,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        NbgDrawerMiniStat("MCP", mcpCount.toString(), Modifier.weight(1f))
        NbgDrawerMiniStat("Skills", skillsCount.toString(), Modifier.weight(1f))
        NbgDrawerMiniStat("Tasks", tasksCount.toString(), Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun NbgDrawerMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(13.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder.copy(alpha = 0.44f)),
  ) {
    Column(
      modifier = Modifier.padding(7.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = value,
        color = NbgAgentColors.TextStrong,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = label,
        color = NbgAgentColors.TextMuted,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun NbgDrawerSectionLabel(text: String) {
  Text(
    text = text.uppercase(),
    color = NbgAgentColors.TextDisabled,
    fontSize = 10.5.sp,
    lineHeight = 13.sp,
    fontFamily = FontFamily.Monospace,
    letterSpacing = 0.sp,
    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
  )
}

@Composable
private fun NbgDrawerWorkspaceTile(
  title: String,
  subtitle: String,
  meta: String,
  iconText: String,
  onClick: () -> Unit,
) {
  NbgPressBox(
    onClick = onClick,
    shape = RoundedCornerShape(17.dp),
    modifier = Modifier
      .fillMaxWidth()
      .semantics { contentDescription = title },
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(NbgAgentColors.SurfaceContainer, RoundedCornerShape(17.dp))
        .padding(9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      NbgDrawerTextIcon(iconText)
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(1.dp),
      ) {
        Text(
          text = title,
          color = NbgAgentColors.TextStrong,
          fontSize = 12.5.sp,
          lineHeight = 16.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          color = NbgAgentColors.TextMuted,
          fontSize = 10.5.sp,
          lineHeight = 13.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      NbgSheetTag(meta, primary = true)
    }
  }
}

@Composable
private fun NbgDrawerFeatureCard(
  iconText: String,
  title: String,
  subtitle: String,
  state: String,
  active: Boolean = false,
  warning: Boolean = false,
  danger: Boolean = false,
  darkIcon: Boolean = false,
  onClick: () -> Unit,
) {
  NbgPressBox(
    onClick = onClick,
    shape = RoundedCornerShape(17.dp),
    modifier = Modifier
      .fillMaxWidth()
      .semantics { contentDescription = title },
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(NbgAgentColors.SurfaceContainer, RoundedCornerShape(17.dp))
        .padding(9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      NbgDrawerTextIcon(iconText, dark = darkIcon)
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = title,
          color = NbgAgentColors.TextStrong,
          fontSize = 13.sp,
          lineHeight = 17.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      NbgSheetTag(state, primary = active, warning = warning, danger = danger)
    }
  }
}

private fun NbgCapability?.nbgDrawerCapabilityState(): String =
  this?.status?.ifBlank { health.label } ?: "未知"

@Composable
private fun NbgDrawerTextIcon(
  text: String,
  dark: Boolean = false,
) {
  Box(
    modifier = Modifier
      .size(34.dp)
      .background(
        if (dark) NbgAgentColors.CodeBlock else NbgAgentColors.PrimaryContainer,
        RoundedCornerShape(13.dp),
      ),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = if (dark) NbgAgentColors.CodeText else NbgAgentColors.Primary,
      fontSize = 10.sp,
      lineHeight = 12.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Black,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun NbgDrawerTerminalFooter(
  connected: Boolean,
  runStatus: NbgChatRunStatus,
) {
  val statusColor = when {
    runStatus.warning -> NbgAgentColors.StatusRed
    runStatus.active -> NbgAgentColors.StatusYellow
    connected -> NbgAgentColors.StatusGreen
    else -> NbgAgentColors.StatusRed
  }
  val statusText = nbgDrawerCompactTag(runStatus.label.ifBlank { if (connected) "ready" else "offline" }, maxLength = 10)
  val commandText = if (connected) "$ nbg-agent session monitor" else "$ nbg-agent waiting for bridge"
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.CodeBlock,
    contentColor = NbgAgentColors.CodeText,
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "terminal",
          color = NbgAgentColors.CodeText,
          fontSize = 11.sp,
          lineHeight = 14.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = statusText,
          color = statusColor,
          fontSize = 10.sp,
          lineHeight = 12.sp,
          fontFamily = FontFamily.Monospace,
        )
      }
      Text(
        text = commandText,
        color = NbgAgentColors.ToolMutedText,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun nbgDrawerContextTag(contextUsageLabel: String?): String? {
  val normalized = contextUsageLabel
    ?.trim()
    ?.replace(Regex("\\s+"), " ")
    .orEmpty()
  if (normalized.isBlank()) return null
  return nbgDrawerCompactTag(normalized, maxLength = 12)
}

private fun nbgDrawerMcpStateLabel(state: HanakoMcpState): String {
  if (!state.enabled) return "off"
  val running = state.connectors.count { it.running }
  return when {
    running > 0 -> "$running on"
    state.connectors.isNotEmpty() -> "${state.connectors.size} cfg"
    else -> "0"
  }
}

private fun nbgDrawerSkillsStateLabel(snapshot: HanakoSkillsSnapshot): String {
  val enabled = snapshot.enabledCount
  val total = snapshot.visibleSkills.size
  return when {
    enabled > 0 -> "$enabled on"
    total > 0 -> total.toString()
    else -> "0"
  }
}

private fun nbgDrawerCompactTag(value: String, maxLength: Int = 12): String {
  val normalized = value.trim().replace(Regex("\\s+"), " ")
  return if (normalized.length <= maxLength) normalized else normalized.take(maxLength - 1) + "…"
}

@Composable
internal fun NbgDrawerPrimaryAction(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .height(52.dp),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.SurfaceContainerHigh,
    contentColor = NbgAgentColors.TextStrong,
    border = BorderStroke(1.dp, NbgAgentColors.SurfaceBorder),
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(30.dp)
          .background(NbgAgentColors.PrimaryContainer, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = label,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "开始新的上下文",
          fontSize = 11.sp,
          color = NbgAgentColors.TextMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
internal fun NbgDrawerToolAction(
  icon: ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  compact: Boolean = false,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.height(if (compact) 40.dp else 44.dp),
    shape = RoundedCornerShape(13.dp),
    color = if (selected) NbgAgentColors.PrimaryContainer else NbgAgentColors.SurfaceContainer,
    contentColor = if (selected) NbgAgentColors.TextStrong else NbgAgentColors.TextMuted,
    border = BorderStroke(1.dp, if (selected) NbgAgentColors.SurfaceBorder else NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 16.dp else 18.dp))
      Text(
        text = label,
        fontSize = if (compact) 13.sp else 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
internal fun NbgDrawerSearchField(
  value: String,
  searching: Boolean,
  onValueChange: (String) -> Unit,
  onClear: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.LiquidBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Icon(HugeIcons.Search01, contentDescription = null, tint = NbgAgentColors.TextMuted, modifier = Modifier.size(18.dp))
      Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) {
          Text("搜索 HanakoPro 会话", color = NbgAgentColors.TextMuted, fontSize = 14.sp)
        }
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = true,
          textStyle = TextStyle(
            color = NbgAgentColors.TextStrong,
            fontSize = 14.sp,
            fontFamily = nbgCurrentFontFamily(),
          ),
          cursorBrush = SolidColor(NbgAgentColors.Primary),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      if (searching || value.isNotBlank()) {
        NbgPressBox(
          onClick = onClear,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .size(30.dp)
            .semantics { contentDescription = "清空搜索" },
        ) {
          Box(
            Modifier
              .fillMaxSize()
              .background(NbgAgentColors.GlassButton, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
          ) {
            if (searching) {
              Text("...", color = NbgAgentColors.TextMuted, fontSize = 14.sp)
            } else {
              Icon(HugeIcons.Cancel01, contentDescription = null, tint = NbgAgentColors.TextMuted, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun NbgConversationRow(
  conversation: NbgAgentConversation,
  selected: Boolean,
  onClick: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onPinToggle: () -> Unit,
) {
  val background by animateColorAsState(
    targetValue = if (selected) NbgAgentColors.Selected else NbgAgentColors.Drawer,
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "conversation row background",
  )
  var menuOpen by remember { mutableStateOf(false) }
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (isPressed) 0.98f else 1f,
    animationSpec = tween(
      durationMillis = nbgMotionDuration(if (isPressed) 90 else 160),
      easing = FastOutSlowInEasing,
    ),
    label = "conversation row press",
  )
  Box {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        }
        .background(background, RoundedCornerShape(14.dp))
        .combinedClickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
          onLongClick = { menuOpen = true },
        )
        .padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Box(
        modifier = Modifier
          .size(28.dp)
          .background(if (selected) NbgAgentColors.PrimarySoft else NbgAgentColors.SurfaceContainer, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (conversation.pinned) Icons.Filled.PushPin else HugeIcons.Sparkles,
          contentDescription = null,
          tint = if (selected || conversation.pinned) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
          modifier = Modifier.size(15.dp),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          conversation.title,
          color = NbgAgentColors.TextStrong,
          fontSize = 14.sp,
          fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val meta = nbgConversationMetaLine(conversation)
        Text(
          meta,
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val snippet = nbgConversationPreviewSnippet(conversation)
        if (snippet != null) {
          Text(
            snippet,
            color = NbgAgentColors.TextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    DropdownMenu(
      expanded = menuOpen,
      onDismissRequest = { menuOpen = false },
    ) {
      DropdownMenuItem(
        text = { Text("重命名", color = NbgAgentColors.TextStrong) },
        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = NbgAgentColors.TextMuted) },
        onClick = {
          menuOpen = false
          onRename()
        },
      )
      DropdownMenuItem(
        text = { Text(if (conversation.pinned) "取消置顶" else "置顶", color = NbgAgentColors.TextStrong) },
        leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null, tint = NbgAgentColors.TextMuted) },
        onClick = {
          menuOpen = false
          onPinToggle()
        },
      )
      DropdownMenuItem(
        text = { Text("删除", color = NbgAgentColors.Primary) },
        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = NbgAgentColors.Primary) },
        onClick = {
          menuOpen = false
          onDelete()
        },
      )
      }
    }
}

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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
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
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tools

@Composable
internal fun NbgMessageEnterContainer(
  enabled: Boolean,
  content: @Composable () -> Unit,
) {
  if (!enabled) {
    content()
    return
  }

  var entered by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    entered = true
  }
  val alpha by animateFloatAsState(
    targetValue = if (entered) 1f else 0f,
    animationSpec = tween(durationMillis = nbgMotionDuration(150), easing = FastOutSlowInEasing),
    label = "message enter alpha",
  )
  val offsetY by animateDpAsState(
    targetValue = if (entered) 0.dp else 6.dp,
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "message enter offset",
  )
  Box(
    modifier = Modifier.graphicsLayer {
      this.alpha = alpha
      translationY = offsetY.toPx()
    },
  ) {
    content()
  }
}

private fun Modifier.nbgPetCardAnchor(@Suppress("UNUSED_PARAMETER") onBounds: (Rect) -> Unit): Modifier = this

@Composable
internal fun NbgMessageBlock(
  message: NbgAgentMessage,
  showHeader: Boolean = true,
  preparedText: NbgPreparedText? = null,
  preparedTextKey: String = message.id.toString(),
  pretextCache: NbgPretextCache? = null,
  animatePreparedBlocks: Boolean = false,
  historyNativeText: Boolean = false,
  onPetCardBounds: (Rect) -> Unit = {},
  terminalOutput: HanakoTerminalOutput? = null,
  terminalChunkIndex: Int = 0,
  terminalChunkCount: Int = 0,
  filePreview: HanakoFilePreview? = null,
  filePreviewChunkIndex: Int = 0,
  filePreviewChunkCount: Int = 0,
  fileDiff: HanakoFileDiff? = null,
  fileDiffLines: List<String>? = null,
  fileDiffChunkIndex: Int = 0,
  fileDiffChunkCount: Int = 0,
  onInterruptTerminal: (String) -> Unit = {},
) {
  if (terminalOutput != null) {
    NbgTerminalOutputChunkCard(
      terminal = terminalOutput,
      chunkIndex = terminalChunkIndex,
      chunkCount = terminalChunkCount,
      onPetCardBounds = onPetCardBounds,
    )
    return
  }
  if (filePreview != null) {
    NbgFileWritePreviewChunkCard(
      preview = filePreview,
      running = message.toolStatus?.running == true,
      chunkIndex = filePreviewChunkIndex,
      chunkCount = filePreviewChunkCount,
      onPetCardBounds = onPetCardBounds,
    )
    return
  }
  if (fileDiff != null && fileDiffLines != null) {
    NbgFileDiffChunkCard(
      diff = fileDiff,
      lines = fileDiffLines,
      chunkIndex = fileDiffChunkIndex,
      chunkCount = fileDiffChunkCount,
      onPetCardBounds = onPetCardBounds,
    )
    return
  }
  if (message.role == NbgAgentRole.ContentBlock) {
    message.contentBlock?.let {
      NbgContentBlockCard(block = it, onPetCardBounds = onPetCardBounds)
    }
    return
  }
  if (message.role == NbgAgentRole.Tool) {
    message.toolStatus?.let {
      NbgToolStatusCard(
        tool = it,
        onPetCardBounds = onPetCardBounds,
        onInterruptTerminal = onInterruptTerminal,
      )
    }
    return
  }
  if (message.role == NbgAgentRole.Thinking) {
    NbgThinkingMessageCard(
      message = message,
      showHeader = showHeader,
      onPetCardBounds = onPetCardBounds,
    )
    return
  }
  val isUser = message.role == NbgAgentRole.User
  val horizontal = if (isUser) Alignment.End else Alignment.Start
  Column(
    horizontalAlignment = horizontal,
    verticalArrangement = Arrangement.spacedBy(5.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    if (showHeader) {
      Row(
        modifier = Modifier.widthIn(max = 352.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!isUser) {
          NbgTinyAvatar(message.role)
        }
        Text(
          text = when (message.role) {
            NbgAgentRole.User -> "你"
            NbgAgentRole.Assistant -> "NBG-Code"
            NbgAgentRole.System -> "系统"
            NbgAgentRole.Thinking -> "思考"
            NbgAgentRole.Tool -> "过程"
            NbgAgentRole.ContentBlock -> "内容"
          },
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
        )
        if (isUser) {
          NbgTinyAvatar(message.role)
        }
      }
    }

    if (isUser) {
      Surface(
        color = NbgAgentColors.UserBubble,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .widthIn(max = 344.dp)
          .nbgPetCardAnchor(onPetCardBounds),
      ) {
        Text(
          text = message.text,
          color = NbgAgentColors.TextStrong,
          fontSize = 15.sp,
          lineHeight = 22.sp,
          modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
        )
      }
    } else if (message.role == NbgAgentRole.System) {
      Surface(
        modifier = Modifier
          .widthIn(max = 382.dp)
          .nbgPetCardAnchor(onPetCardBounds),
        shape = RoundedCornerShape(18.dp),
        color = NbgAgentColors.SurfaceContainer,
        border = BorderStroke(
          width = 1.dp,
          color = NbgAgentColors.LiquidBorder,
        ),
      ) {
        NbgStreamingMessageText(
          message = message,
          preparedText = preparedText,
          preparedTextKey = preparedTextKey,
          pretextCache = pretextCache,
          animatePreparedBlocks = animatePreparedBlocks,
          historyNativeText = historyNativeText,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
      }
    } else {
      Box(
        modifier = Modifier
          .widthIn(max = 382.dp)
          .nbgPetCardAnchor(onPetCardBounds)
          .padding(horizontal = 2.dp, vertical = 2.dp),
      ) {
        NbgStreamingMessageText(
          message = message,
          preparedText = preparedText,
          preparedTextKey = preparedTextKey,
          pretextCache = pretextCache,
          animatePreparedBlocks = animatePreparedBlocks,
          historyNativeText = historyNativeText,
          modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
internal fun NbgToolStatusCard(
  tool: HanakoToolStatus,
  onPetCardBounds: (Rect) -> Unit = {},
  onInterruptTerminal: (String) -> Unit = {},
) {
  var showDetailsDialog by remember(tool.key) { mutableStateOf(false) }
  val isTerminalTool = tool.kind == "terminal" || tool.terminalOutput != null
  val compact = isTerminalTool
  val terminalFallbackText = remember(tool.key, tool.detail, tool.subtitle, tool.status, tool.filePath, tool.toolName) {
    nbgTerminalToolFallbackText(tool)
  }
  val hasTerminalPreview = isTerminalTool && (tool.terminalOutput != null || terminalFallbackText.isNotBlank())
  var expanded by remember(tool.key, tool.running, isTerminalTool, hasTerminalPreview) {
    mutableStateOf(
      when {
        isTerminalTool -> hasTerminalPreview && !tool.running
        else -> tool.hasInlinePreview && !tool.running
      },
    )
  }
  val status = nbgToolStatusLabel(tool)
  val targetDotColor = nbgToolStatusDotColor(tool)
  val dotColor = targetDotColor
  val dotAlpha = if (tool.running) 0.9f else 0.92f
  val title = tool.title.ifBlank { tool.toolName.ifBlank { nbgToolKindLabel(tool.kind).ifBlank { "执行工具" } } }
  val subtitle = listOfNotNull(
    nbgToolKindLabel(tool.kind).takeIf { it.isNotBlank() && it != title },
    status.takeIf { it.isNotBlank() },
  ).joinToString(" · ")
  val detail = listOfNotNull(
    nbgCompactToolDetail(tool.subtitle),
    tool.filePath.takeIf { it.isNotBlank() }?.substringAfterLast('/').orEmpty(),
    tool.detail.takeIf { !tool.hasInlinePreview }?.let(::nbgCompactToolDetail),
  ).filter { it.isNotBlank() }.distinct().joinToString(" / ")
  if (compact) {
    NbgCompactTerminalToolCard(
      tool = tool,
      title = title,
      subtitle = subtitle,
      fallbackText = terminalFallbackText,
      expanded = expanded,
      onToggleOutput = { expanded = !expanded },
      onPetCardBounds = onPetCardBounds,
      onInterruptTerminal = onInterruptTerminal,
    )
    return
  }
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .nbgPetCardAnchor(onPetCardBounds)
      .semantics { contentDescription = "过程 $title $subtitle" },
    shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
    color = NbgAgentColors.SurfaceContainer.copy(alpha = if (tool.fileDiff != null) 0.62f else 0.86f),
    border = BorderStroke(
      1.dp,
      if (tool.running) NbgAgentColors.PrimarySoft.copy(alpha = 0.72f) else NbgAgentColors.LiquidBorder.copy(alpha = 0.55f),
    ),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = if (compact) 9.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
      verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
      ) {
        Box(
          modifier = Modifier
            .size(if (compact) 24.dp else 30.dp)
            .background(NbgAgentColors.PrimarySoft, RoundedCornerShape(if (compact) 8.dp else 10.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = nbgToolStatusIcon(tool),
            contentDescription = null,
            tint = NbgAgentColors.Primary,
            modifier = Modifier.size(if (compact) 14.dp else 17.dp),
          )
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp),
        ) {
          Text(
            text = title,
            color = NbgAgentColors.TextStrong,
            fontSize = if (compact) 12.sp else 13.sp,
            lineHeight = if (compact) 16.sp else 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (subtitle.isNotBlank()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .graphicsLayer { alpha = dotAlpha }
                  .background(dotColor, CircleShape),
              )
              Text(
                text = subtitle,
                color = NbgAgentColors.ToolMutedText,
                fontSize = if (compact) 10.sp else 11.sp,
                lineHeight = if (compact) 13.sp else 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
      if (detail.isNotBlank() && !compact) {
        Text(
          text = detail,
          color = NbgAgentColors.ToolMutedText,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (tool.hasInlinePreview) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = nbgToolPreviewSummary(tool),
            color = NbgAgentColors.ToolMutedText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          NbgToolDetailToggle(
            label = if (expanded) "收起" else if (isTerminalTool) "输出" else "展开",
            onClick = { expanded = !expanded },
          )
          NbgToolDetailToggle(
            label = "详情",
            onClick = { showDetailsDialog = true },
          )
        }
      }
      AnimatedVisibility(
        visible = tool.hasInlinePreview && expanded,
        enter = fadeIn(animationSpec = tween(nbgMotionDuration(140), easing = FastOutSlowInEasing)) +
          expandVertically(animationSpec = tween(nbgMotionDuration(180), easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(nbgMotionDuration(100), easing = FastOutSlowInEasing)) +
          shrinkVertically(animationSpec = tween(nbgMotionDuration(140), easing = FastOutSlowInEasing)),
      ) {
        NbgToolInlinePreviews(tool, livePreview = tool.running)
      }
    }
  }
  if (showDetailsDialog) {
    NbgToolDetailsDialog(
      tool = tool,
      onDismiss = { showDetailsDialog = false },
    )
  }
}

@Composable
internal fun NbgCompactTerminalToolCard(
  tool: HanakoToolStatus,
  title: String,
  subtitle: String,
  fallbackText: String = "",
  expanded: Boolean,
  onToggleOutput: () -> Unit,
  onPetCardBounds: (Rect) -> Unit = {},
  onInterruptTerminal: (String) -> Unit = {},
) {
  val outputText = tool.terminalOutput?.output?.ifBlank { null } ?: fallbackText
  val canShowOutput = outputText.isNotBlank()
  val summary = nbgToolPreviewSummary(tool)
  val interruptTerminalId = tool.terminalOutput
    ?.takeIf { it.alive == true && !it.staticOutput && it.sessionId.isNotBlank() }
    ?.sessionId
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .nbgPetCardAnchor(onPetCardBounds)
      .semantics { contentDescription = "命令 $title $subtitle" },
    shape = RoundedCornerShape(14.dp),
    color = NbgAgentColors.SurfaceContainer.copy(alpha = 0.72f),
    border = BorderStroke(1.dp, NbgAgentColors.PrimarySoft.copy(alpha = 0.72f)),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Box(
          modifier = Modifier
            .size(22.dp)
            .background(NbgAgentColors.PrimarySoft.copy(alpha = 0.74f), RoundedCornerShape(8.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = nbgToolStatusIcon(tool),
            contentDescription = null,
            tint = NbgAgentColors.Primary,
            modifier = Modifier.size(13.dp),
          )
        }
        Box(
          modifier = Modifier
            .size(5.dp)
            .background(nbgToolStatusDotColor(tool), CircleShape),
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
          Text(
            text = title,
            color = NbgAgentColors.TextStrong,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = summary.ifBlank { subtitle },
            color = NbgAgentColors.ToolMutedText,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (canShowOutput) {
          NbgCompactToolChip(
            label = if (expanded) "收起" else "输出",
            onClick = onToggleOutput,
          )
        }
        if (interruptTerminalId != null) {
          NbgMiniIconButton(
            icon = Icons.Filled.Stop,
            contentDescription = "打断终端",
            onClick = { onInterruptTerminal(interruptTerminalId) },
          )
        }
      }
      AnimatedVisibility(
        visible = expanded && canShowOutput,
        enter = fadeIn(animationSpec = tween(nbgMotionDuration(120), easing = FastOutSlowInEasing)) +
          expandVertically(animationSpec = tween(nbgMotionDuration(150), easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(nbgMotionDuration(90), easing = FastOutSlowInEasing)) +
          shrinkVertically(animationSpec = tween(nbgMotionDuration(120), easing = FastOutSlowInEasing)),
      ) {
        if (tool.terminalOutput != null) {
          NbgToolInlinePreviews(tool, terminalOnly = true, livePreview = true)
        } else {
          NbgTerminalFallbackPreview(
            title = title,
            meta = subtitle,
            text = fallbackText,
          )
        }
      }
    }
  }
}

@Composable
internal fun NbgCompactToolChip(
  label: String,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(9.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
    contentColor = NbgAgentColors.TextStrong,
  ) {
    Text(
      text = label,
      fontSize = 10.sp,
      lineHeight = 12.sp,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
    )
  }
}

@Composable
internal fun NbgToolDetailToggle(
  label: String = "详情",
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
    contentColor = NbgAgentColors.TextStrong,
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
    )
  }
}

@Composable
internal fun NbgToolDetailsDialog(
  tool: HanakoToolStatus,
  onDismiss: () -> Unit,
) {
  val scrollState = rememberScrollState()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = tool.title.ifBlank { tool.toolName.ifBlank { nbgToolKindLabel(tool.kind) } },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 520.dp)
          .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        tool.subtitle.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
          )
        }
        tool.detail.takeIf { it.isNotBlank() && !tool.hasInlinePreview }?.let {
          Text(
            text = nbgCompactToolDetail(it),
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
          )
        }
        NbgToolInlinePreviews(tool)
        if (tool.kind == "terminal" && tool.terminalOutput == null) {
          val fallback = nbgTerminalToolFallbackText(tool)
          if (fallback.isNotBlank()) {
            NbgTerminalFallbackPreview(
              title = tool.title.ifBlank { toolDisplayName(tool.toolName.ifBlank { "terminal" }) },
              meta = tool.subtitle,
              text = fallback,
            )
          }
        }
      }
    },
    confirmButton = {
      NbgDialogAction(label = "关闭", primary = true, onClick = onDismiss)
    },
  )
}

@Composable
internal fun NbgToolInlinePreviews(
  tool: HanakoToolStatus,
  terminalOnly: Boolean = false,
  livePreview: Boolean = false,
) {
  val previews = listOfNotNull(
    tool.filePreview?.takeIf { !terminalOnly }?.let { "write" },
    tool.fileDiff?.takeIf { !terminalOnly }?.let { "diff" },
    tool.terminalOutput?.let { "terminal" },
  )
  if (previews.isEmpty()) return
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 2.dp),
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    val showFilePreview = !terminalOnly && tool.fileDiff == null
    if (showFilePreview) tool.filePreview?.let { preview ->
      NbgFileWritePreview(preview = preview, running = tool.running, livePreview = livePreview)
    }
    if (!terminalOnly) tool.fileDiff?.let { diff ->
      NbgFileDiffPreview(diff, livePreview = livePreview)
    }
    tool.terminalOutput?.let { terminal ->
      NbgTerminalOutputPreview(terminal, livePreview = livePreview)
    }
  }
}

@Composable
internal fun NbgFileWritePreview(
  preview: HanakoFilePreview,
  running: Boolean,
  livePreview: Boolean = false,
) {
  val previewText = if (livePreview) {
    nbgLiveToolPreviewText(preview.previewText.ifBlank { "准备写入文件..." })
  } else {
    preview.previewText.ifBlank { "准备写入文件..." }
  }
  NbgFileWritePreviewChunkCard(
    preview = preview.copy(previewText = previewText),
    running = running,
    chunkIndex = 0,
    chunkCount = 1,
  )
}

@Composable
internal fun NbgFileWritePreviewChunkCard(
  preview: HanakoFilePreview,
  running: Boolean,
  chunkIndex: Int,
  chunkCount: Int,
  onPetCardBounds: (Rect) -> Unit = {},
) {
  val previewText = preview.previewText.ifBlank { "准备写入文件..." }
  val partLabel = if (chunkCount > 1) " · ${chunkIndex + 1}/$chunkCount" else ""
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .nbgPetCardAnchor(onPetCardBounds)
      .background(NbgAgentColors.InlinePanel, RoundedCornerShape(8.dp))
      .padding(horizontal = 9.dp, vertical = 7.dp)
      .semantics { contentDescription = "文件写入预览 ${preview.fileName}" },
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = preview.fileName + partLabel,
        color = NbgAgentColors.TextStrong,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
    }
    BasicText(
      text = previewText,
      style = TextStyle(
        color = NbgAgentColors.TextStrong,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFamily = FontFamily.Monospace,
      ),
      modifier = Modifier.fillMaxWidth(),
    )
    if (running) {
      Text(
        text = "写入中",
        color = NbgAgentColors.Primary,
        fontSize = 10.sp,
        maxLines = 1,
      )
    }
  }
}

@Composable
internal fun NbgFileDiffPreview(
  diff: HanakoFileDiff,
  livePreview: Boolean = false,
) {
  val lines = remember(diff.unifiedDiff, diff.oldContent, diff.newContent, livePreview) {
    if (livePreview) nbgDiffPreviewLines(diff, limit = HANA_MOBILE_LIVE_TOOL_PREVIEW_LINES) else nbgFullDiffLines(diff)
  }
  val stats = remember(diff.unifiedDiff, diff.oldContent, diff.newContent, livePreview, lines) {
    if (livePreview) nbgDiffStatsFromShownLines(lines) else nbgDiffStats(diff)
  }
  NbgFileDiffChunkCard(
    diff = diff,
    lines = lines,
    chunkIndex = 0,
    chunkCount = 1,
    stats = stats,
  )
}

@Composable
internal fun NbgFileDiffChunkCard(
  diff: HanakoFileDiff,
  lines: List<String>,
  chunkIndex: Int,
  chunkCount: Int,
  stats: Pair<Int, Int>? = null,
  onPetCardBounds: (Rect) -> Unit = {},
) {
  val shownStats = stats ?: remember(diff.unifiedDiff, diff.oldContent, diff.newContent) {
    nbgDiffStats(diff)
  }
  val partLabel = if (chunkCount > 1) " · ${chunkIndex + 1}/$chunkCount" else ""
  NbgTerminalPanel(
    modifier = Modifier
      .fillMaxWidth()
      .nbgPetCardAnchor(onPetCardBounds)
      .semantics { contentDescription = "文件 Diff ${diff.fileName}" },
    title = diff.fileName + partLabel,
    meta = "+${shownStats.first} -${shownStats.second}",
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
      lines.ifEmpty { listOf("无可显示差异") }.forEach { line ->
        NbgDiffLine(line)
      }
    }
  }
}

@Composable
internal fun NbgTerminalOutputChunkCard(
  terminal: HanakoTerminalOutput,
  chunkIndex: Int,
  chunkCount: Int,
  onPetCardBounds: (Rect) -> Unit = {},
) {
  val clipboard = LocalClipboardManager.current
  val label = buildString {
    append(terminal.title.ifBlank { "terminal" })
    if (chunkCount > 1) append(" · ${chunkIndex + 1}/$chunkCount")
  }
  NbgTerminalPanel(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 14.dp)
      .nbgPetCardAnchor(onPetCardBounds)
      .semantics { contentDescription = "终端输出 $label" },
    title = label,
    meta = nbgTerminalOutputMeta(terminal, includeSlice = false),
    onCopy = { clipboard.setText(AnnotatedString(terminal.output)) },
    copyContentDescription = "复制这段终端输出",
  ) {
    BasicText(
      text = terminal.output.ifBlank { "(暂无输出)" },
      style = TextStyle(
        color = NbgAgentColors.ToolMuted,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace,
      ),
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
internal fun NbgTerminalOutputPreview(
  terminal: HanakoTerminalOutput,
  livePreview: Boolean = false,
) {
  val clipboard = LocalClipboardManager.current
  val terminalText = if (livePreview) {
    nbgLiveToolPreviewText(terminal.output.ifBlank { "(暂无输出)" })
  } else {
    terminal.output.ifBlank { "(暂无输出)" }
  }
  val meta = nbgTerminalOutputMeta(terminal)
  NbgTerminalPanel(
    modifier = Modifier
      .fillMaxWidth()
      .semantics { contentDescription = "终端实时输出 ${terminal.sessionId}" },
    title = terminal.title.ifBlank { "terminal" },
    meta = meta,
    onCopy = { clipboard.setText(AnnotatedString(terminal.output)) },
    copyContentDescription = "复制终端输出",
  ) {
    BasicText(
      text = terminalText,
      style = TextStyle(
        color = NbgAgentColors.ToolMuted,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace,
      ),
    )
  }
}

@Composable
internal fun NbgTerminalFallbackPreview(
  title: String,
  meta: String = "",
  text: String,
) {
  val clipboard = LocalClipboardManager.current
  val shownText = text.ifBlank { "(暂无输出)" }
  NbgTerminalPanel(
    modifier = Modifier
      .fillMaxWidth()
      .semantics { contentDescription = "终端详情 $title" },
    title = title.ifBlank { "terminal" },
    meta = meta,
    onCopy = { clipboard.setText(AnnotatedString(shownText)) },
    copyContentDescription = "复制终端详情",
  ) {
    BasicText(
      text = shownText,
      style = TextStyle(
        color = NbgAgentColors.ToolMuted,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace,
      ),
    )
  }
}

@Composable
internal fun NbgTerminalPanel(
  modifier: Modifier = Modifier,
  title: String,
  meta: String = "",
  onCopy: (() -> Unit)? = null,
  copyContentDescription: String = "复制输出",
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = NbgAgentColors.TerminalInline,
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        NbgTerminalWindowDots()
        Text(
          text = title,
          color = NbgAgentColors.OnPrimary,
          fontSize = 10.sp,
          lineHeight = 13.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        meta.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            color = NbgAgentColors.ToolMuted,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
          )
        }
        if (onCopy != null) {
          NbgMiniIconButton(
            icon = Icons.Filled.ContentCopy,
            contentDescription = copyContentDescription,
            onClick = onCopy,
          )
        }
      }
      content()
    }
  }
}

@Composable
internal fun NbgTerminalWindowDots() {
  Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    Box(
      modifier = Modifier
        .size(6.dp)
        .background(Color(0xFFFF6B6B), CircleShape),
    )
    Box(
      modifier = Modifier
        .size(6.dp)
        .background(Color(0xFFFFC857), CircleShape),
    )
    Box(
      modifier = Modifier
        .size(6.dp)
        .background(Color(0xFF6BD17D), CircleShape),
    )
  }
}

@Composable
internal fun NbgInlineTextButton(
  text: String,
  contentDescription: String,
  onClick: () -> Unit,
) {
  NbgPressBox(
    onClick = onClick,
    shape = RoundedCornerShape(11.dp),
    modifier = Modifier
      .height(28.dp)
      .semantics { this.contentDescription = contentDescription },
  ) {
    Box(
      modifier = Modifier
        .background(NbgAgentColors.SurfaceContainer, RoundedCornerShape(11.dp))
        .padding(horizontal = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = text,
        color = NbgAgentColors.TextStrong,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
      )
    }
  }
}

@Composable
internal fun NbgMiniIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(11.dp),
    color = NbgAgentColors.SurfaceContainer,
    contentColor = NbgAgentColors.TextStrong,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
    modifier = Modifier
      .size(28.dp)
      .semantics { this.contentDescription = contentDescription },
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
    }
  }
}

@Composable
internal fun NbgDiffLine(line: String) {
  val isAddition = line.startsWith("+")
  val isDeletion = line.startsWith("-")
  val marker = when {
    isAddition -> "+"
    isDeletion -> "-"
    else -> " "
  }
  val body = line.drop(if (isAddition || isDeletion) 1 else 0).trimStart()
  val color = when {
    isAddition -> Color(0xFFA8F0A4)
    isDeletion -> Color(0xFFFF9A9A)
    else -> NbgAgentColors.ToolMuted
  }
  val background = when {
    isAddition -> Color(0xFF17351E).copy(alpha = 0.72f)
    isDeletion -> Color(0xFF3A1C1C).copy(alpha = 0.72f)
    else -> Color.Transparent
  }
  val railColor = when {
    isAddition -> Color(0xFF6BD17D)
    isDeletion -> Color(0xFFFF6B6B)
    else -> Color.White.copy(alpha = 0.22f)
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(background, RoundedCornerShape(3.dp))
      .padding(horizontal = 4.dp, vertical = 1.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Box(
      modifier = Modifier
        .width(2.dp)
        .height(15.dp)
        .background(railColor.copy(alpha = if (isAddition || isDeletion) 0.72f else 0.22f), RoundedCornerShape(999.dp)),
    )
    Text(
      text = marker,
      color = color,
      fontSize = 10.sp,
      lineHeight = 14.sp,
      fontFamily = FontFamily.Monospace,
      maxLines = 1,
      modifier = Modifier.width(8.dp),
    )
    BasicText(
      text = body,
      style = TextStyle(
        color = color,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontFamily = FontFamily.Monospace,
      ),
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
internal fun NbgThinkingMessageCard(
  message: NbgAgentMessage,
  showHeader: Boolean = true,
  onPetCardBounds: (Rect) -> Unit = {},
) {
  if (message.done && message.text.isBlank()) {
    NbgCompactThinkingDoneRow()
    return
  }
  val fallbackText = if (message.done) "思考完成" else "思考中..."
  val text = message.text.ifBlank { fallbackText }
  Column(
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.spacedBy(5.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    if (showHeader) {
      Row(
        modifier = Modifier.widthIn(max = 360.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        NbgTinyAvatar(NbgAgentRole.Thinking)
        Text(
          text = if (message.done) "思考完成" else "正在思考",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          maxLines = 1,
        )
      }
    }
    Surface(
      modifier = Modifier
        .widthIn(max = 360.dp)
        .nbgPetCardAnchor(onPetCardBounds)
        .semantics { contentDescription = "正在思考" },
      shape = RoundedCornerShape(16.dp),
      color = NbgAgentColors.SurfaceContainer,
      border = BorderStroke(1.dp, if (message.done) NbgAgentColors.SurfaceBorder else NbgAgentColors.PrimarySoft),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .background(NbgAgentColors.PrimarySoft, RoundedCornerShape(10.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = HugeIcons.Brain02,
            contentDescription = null,
            tint = NbgAgentColors.Primary,
            modifier = Modifier.size(16.dp),
          )
        }
        BasicText(
          text = text,
          style = TextStyle(
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontFamily = nbgCurrentFontFamily(),
          ),
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
internal fun NbgCompactThinkingDoneRow() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .semantics { contentDescription = "思考完成" },
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(24.dp)
        .background(NbgAgentColors.PrimarySoft, RoundedCornerShape(9.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = HugeIcons.Tick01,
        contentDescription = null,
        tint = NbgAgentColors.Primary,
        modifier = Modifier.size(14.dp),
      )
    }
    Box(
      modifier = Modifier
        .size(6.dp)
        .background(NbgAgentColors.StatusGreen, CircleShape),
    )
    Text(
      text = "思考完成",
      color = NbgAgentColors.TextMuted,
      fontSize = 12.sp,
      lineHeight = 17.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
internal fun NbgContentBlockCard(
  block: HanakoContentBlock,
  onPetCardBounds: (Rect) -> Unit = {},
) {
  Column(
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.widthIn(max = 360.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      NbgTinyAvatar(NbgAgentRole.ContentBlock)
      Text(
        text = "内容",
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        maxLines = 1,
      )
    }
    Surface(
      color = NbgAgentColors.SurfaceContainer,
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, NbgAgentColors.LiquidBorder),
      modifier = Modifier
        .widthIn(max = 360.dp)
        .nbgPetCardAnchor(onPetCardBounds),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .background(NbgAgentColors.PrimarySoft, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = nbgContentBlockIcon(block.type),
              contentDescription = null,
              tint = NbgAgentColors.Primary,
              modifier = Modifier.size(16.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = block.title,
              color = NbgAgentColors.TextStrong,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOf(block.subtitle, block.status.takeIf { it.isNotBlank() })
              .filterNotNull()
              .filter { it.isNotBlank() }
              .distinct()
              .joinToString(" / ")
            if (subtitle.isNotBlank()) {
              Text(
                text = subtitle,
                color = NbgAgentColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        block.detail.takeIf { it.isNotBlank() }?.let { detail ->
          Text(
            text = detail,
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
              .fillMaxWidth()
              .background(NbgAgentColors.Drawer, RoundedCornerShape(10.dp))
              .padding(horizontal = 9.dp, vertical = 7.dp),
          )
        }
      }
    }
  }
}

internal fun nbgContentBlockIcon(type: String): ImageVector =
  when (type) {
    "file", "artifact", "screenshot" -> HugeIcons.File02
    "subagent" -> HugeIcons.Sparkles
    else -> HugeIcons.Copy01
  }

@Composable
internal fun NbgTinyAvatar(role: NbgAgentRole) {
  val isUser = role == NbgAgentRole.User
  val isThinking = role == NbgAgentRole.Thinking
  Box(
    modifier = Modifier
      .size(24.dp)
      .background(
        color = if (isUser || isThinking) NbgAgentColors.PrimarySoft else NbgAgentColors.SurfaceContainerHigh,
        shape = RoundedCornerShape(9.dp),
      ),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = when (role) {
        NbgAgentRole.User -> HugeIcons.Sparkles
        NbgAgentRole.Thinking -> HugeIcons.Brain02
        NbgAgentRole.ContentBlock -> HugeIcons.File02
        NbgAgentRole.Tool -> HugeIcons.Tools
        else -> HugeIcons.Sparkles
      },
      contentDescription = null,
      tint = if (isUser || isThinking) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
      modifier = Modifier.size(15.dp),
    )
  }
}

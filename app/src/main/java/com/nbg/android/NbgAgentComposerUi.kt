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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
internal fun NbgChatComposer(
  draft: String,
  streaming: Boolean,
  sendBlocked: Boolean,
  onDraftChange: (String) -> Unit,
  onSend: (String) -> Unit,
  onAbort: () -> Unit,
  agentModelConfig: HanakoAgentModelConfig?,
  agentModelConfigLoading: Boolean,
  agentModelConfigError: String?,
  onLoadAgentModelConfig: () -> Unit,
  onSwitchComposerModel: (HanakoModelSummary) -> Unit,
  modelName: String?,
  preferredModel: NbgChatPreferences?,
  permissionMode: String,
  permissionModeLabel: String,
  onSetPermissionMode: (String) -> Unit,
  thinkingLevel: String,
  thinkingLevelLabel: String,
  onSetThinkingLevel: (String) -> Unit,
  onFocusedChange: (Boolean) -> Unit = {},
  onComposerBoundsChanged: (Rect) -> Unit = {},
) {
  var inputValue by remember {
    mutableStateOf(TextFieldValue(draft, selection = TextRange(draft.length)))
  }
  var modelMenuOpen by remember { mutableStateOf(false) }
  var thinkingMenuOpen by remember { mutableStateOf(false) }
  var permissionMenuOpen by remember { mutableStateOf(false) }
  val context = LocalContext.current
  LaunchedEffect(draft) {
    if (draft != inputValue.text) {
      inputValue = TextFieldValue(draft, selection = TextRange(draft.length))
    }
  }
  LaunchedEffect(modelMenuOpen) {
    if (modelMenuOpen && agentModelConfig == null && !agentModelConfigLoading) {
      onLoadAgentModelConfig()
    }
  }
  val inputText = inputValue.text
  val currentModelSummary = nbgCurrentModelSummary(agentModelConfig, modelName, preferredModel)
  val thinkingOptions = nbgSupportedThinkingDepthOptions(currentModelSummary)
  val currentModelLabel = currentModelSummary?.label ?: modelName?.takeIf { it.isNotBlank() } ?: "未选择"
  val thinkingButtonValue = nbgCompactComposerChipValue(nbgThinkingDepthDisplayLabel(thinkingLevel, thinkingLevelLabel), "自动")
  val permissionButtonValue = nbgCompactComposerChipValue(permissionModeLabel, "操作")
  val modelButtonValue = nbgCompactComposerChipValue(currentModelLabel, "未选择")
  val composerShape = RoundedCornerShape(27.dp)

  Surface(
    color = Color.Transparent,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .imePadding()
        .navigationBarsPadding()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .onGloballyPositioned { coordinates ->
            onComposerBoundsChanged(coordinates.boundsInRoot())
          },
      ) {
        NbgComposerBottomDock(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 12.dp),
          onConnectIm = {
            Toast.makeText(context, "连接 IM 稍后接入", Toast.LENGTH_SHORT).show()
          },
        )
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 34.dp)
            .shadow(
              elevation = 28.dp,
              shape = composerShape,
              clip = false,
              ambientColor = Color.Black.copy(alpha = 0.10f),
              spotColor = Color.Black.copy(alpha = 0.16f),
            )
            .shadow(
              elevation = 3.dp,
              shape = composerShape,
              clip = false,
              ambientColor = Color.Black.copy(alpha = 0.05f),
              spotColor = Color.Black.copy(alpha = 0.08f),
            ),
          shape = composerShape,
          color = NbgAgentColors.Input.copy(alpha = 0.985f),
          border = BorderStroke(1.dp, NbgAgentColors.InputBorder.copy(alpha = 0.68f)),
          tonalElevation = 0.dp,
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp, max = 82.dp)
                .padding(horizontal = 4.dp),
              contentAlignment = Alignment.TopStart,
            ) {
              if (inputText.isEmpty()) {
                Text("输入消息", color = NbgAgentColors.TextMuted, fontSize = 15.sp)
              }
              BasicTextField(
                value = inputValue,
                onValueChange = {
                  inputValue = it
                  onDraftChange(it.text)
                },
                textStyle = TextStyle(
                  color = NbgAgentColors.TextStrong,
                  fontSize = 15.sp,
                  lineHeight = 21.sp,
                  fontFamily = nbgCurrentFontFamily(),
                ),
                cursorBrush = SolidColor(NbgAgentColors.Primary),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 32.dp)
                  .onFocusChanged { onFocusedChange(it.isFocused) }
                  .semantics { contentDescription = "消息输入" },
              )
            }
            NbgComposerInputAddRow(
              thinkingButtonValue = thinkingButtonValue,
              permissionButtonValue = permissionButtonValue,
              modelButtonValue = modelButtonValue,
              thinkingMenuOpen = thinkingMenuOpen,
              permissionMenuOpen = permissionMenuOpen,
              modelMenuOpen = modelMenuOpen,
              thinkingLevelActive = thinkingLevel != "auto",
              permissionModeActive = permissionMode != "operate",
              streaming = streaming,
              sendEnabled = !sendBlocked && (streaming || inputText.isNotBlank()),
              sendSteering = streaming && inputText.isNotBlank(),
              onAddContext = {
                Toast.makeText(context, "添加上下文稍后接入", Toast.LENGTH_SHORT).show()
              },
              onToggleThinking = {
                modelMenuOpen = false
                permissionMenuOpen = false
                thinkingMenuOpen = !thinkingMenuOpen
              },
              onTogglePermission = {
                modelMenuOpen = false
                thinkingMenuOpen = false
                permissionMenuOpen = !permissionMenuOpen
              },
              onToggleModel = {
                thinkingMenuOpen = false
                permissionMenuOpen = false
                modelMenuOpen = !modelMenuOpen
              },
              onSendClick = {
                if (streaming && inputText.isBlank()) {
                  onAbort()
                } else {
                  onSend(inputText)
                }
              },
            )
          }
        }
      }
      NbgComposerThinkingMenu(
        expanded = thinkingMenuOpen,
        currentLevel = thinkingLevel,
        options = thinkingOptions,
        streaming = streaming,
        model = currentModelSummary,
        onDismiss = { thinkingMenuOpen = false },
        onSelectLevel = onSetThinkingLevel,
      )
      NbgComposerPermissionMenu(
        expanded = permissionMenuOpen,
        currentMode = permissionMode,
        streaming = streaming,
        onDismiss = { permissionMenuOpen = false },
        onSelectMode = { mode ->
          permissionMenuOpen = false
          onSetPermissionMode(mode)
        },
      )
      NbgComposerModelMenu(
        expanded = modelMenuOpen,
        config = agentModelConfig,
        loading = agentModelConfigLoading,
        error = agentModelConfigError,
        streaming = streaming,
        currentModel = currentModelSummary,
        onDismiss = { modelMenuOpen = false },
        onReload = onLoadAgentModelConfig,
        onSwitchModel = { model ->
          modelMenuOpen = false
          onSwitchComposerModel(model)
        },
      )
    }
  }
}

@Composable
private fun NbgComposerInputAddRow(
  thinkingButtonValue: String,
  permissionButtonValue: String,
  modelButtonValue: String,
  thinkingMenuOpen: Boolean,
  permissionMenuOpen: Boolean,
  modelMenuOpen: Boolean,
  thinkingLevelActive: Boolean,
  permissionModeActive: Boolean,
  streaming: Boolean,
  sendEnabled: Boolean,
  sendSteering: Boolean,
  onAddContext: () -> Unit,
  onToggleThinking: () -> Unit,
  onTogglePermission: () -> Unit,
  onToggleModel: () -> Unit,
  onSendClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp)
      .semantics { contentDescription = "输入框快捷添加" },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    NbgComposerAddButton(
      onClick = onAddContext,
    )
    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      NbgModelMetaChip(
        modifier = Modifier.weight(0.82f),
        label = "思考",
        value = thinkingButtonValue,
        icon = HugeIcons.Brain02,
        showVisualLabel = true,
        showValue = false,
        showDropdownIndicator = false,
        selected = thinkingMenuOpen || thinkingLevelActive,
        onClick = onToggleThinking,
      )
      NbgModelMetaChip(
        modifier = Modifier.weight(0.88f),
        label = "权限",
        value = permissionButtonValue,
        icon = HugeIcons.Settings03,
        showVisualLabel = false,
        showValue = true,
        showDropdownIndicator = true,
        selected = permissionMenuOpen || permissionModeActive,
        onClick = onTogglePermission,
      )
      NbgModelMetaChip(
        modifier = Modifier.weight(1.18f),
        label = "模型",
        value = modelButtonValue,
        icon = HugeIcons.Sparkles,
        showVisualLabel = false,
        showValue = true,
        showDropdownIndicator = true,
        selected = modelMenuOpen,
        onClick = onToggleModel,
      )
    }
    NbgSendButton(
      enabled = sendEnabled,
      streaming = streaming && !sendSteering,
      steering = sendSteering,
      onClick = onSendClick,
    )
  }
}

@Composable
internal fun NbgComposerBottomDock(
  modifier: Modifier = Modifier,
  onConnectIm: () -> Unit,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(56.dp)
      .semantics { contentDescription = "输入框底部工具栏" },
    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
    color = NbgAgentColors.Input.copy(alpha = 0.82f),
    tonalElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      NbgComposerDockAction(
        label = "连接 IM",
        icon = Icons.Filled.ChatBubbleOutline,
        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = "连接 IM",
        onClick = onConnectIm,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun NbgComposerAddButton(
  onClick: () -> Unit,
) {
  NbgPressBox(
    onClick = onClick,
    shape = CircleShape,
    modifier = Modifier
      .size(32.dp)
      .semantics { contentDescription = "添加上下文" },
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "+",
        color = NbgAgentColors.TextStrong,
        fontSize = 28.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Light,
      )
    }
  }
}

@Composable
private fun NbgComposerDockAction(
  label: String,
  icon: ImageVector,
  trailingIcon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  NbgPressBox(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    modifier = modifier
      .fillMaxHeight()
      .semantics { this.contentDescription = contentDescription },
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 0.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = NbgAgentColors.TextMuted,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = label,
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Icon(
        imageVector = trailingIcon,
        contentDescription = null,
        tint = NbgAgentColors.TextDisabled,
        modifier = Modifier.size(15.dp),
      )
    }
  }
}

@Composable
internal fun NbgModelMetaChip(
  modifier: Modifier = Modifier,
  label: String,
  value: String,
  icon: ImageVector,
  showVisualLabel: Boolean = true,
  showValue: Boolean = true,
  showDropdownIndicator: Boolean = true,
  selected: Boolean = false,
  onClick: () -> Unit,
) {
  val displayValue = nbgCompactComposerChipValue(value, "未设置")
  val chipBackground by animateColorAsState(
    targetValue = if (selected) NbgAgentColors.SurfaceContainerHigh else NbgAgentColors.SurfaceLow,
    animationSpec = tween(durationMillis = nbgMotionDuration(160), easing = FastOutSlowInEasing),
    label = "composer chip background",
  )
  val chipIconTint by animateColorAsState(
    targetValue = if (selected) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
    animationSpec = tween(durationMillis = nbgMotionDuration(160), easing = FastOutSlowInEasing),
    label = "composer chip icon tint",
  )
  val chipLabelColor by animateColorAsState(
    targetValue = if (selected) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
    animationSpec = tween(durationMillis = nbgMotionDuration(160), easing = FastOutSlowInEasing),
    label = "composer chip label color",
  )
  val chipIconScale by animateFloatAsState(
    targetValue = if (selected) 1.06f else 1f,
    animationSpec = tween(durationMillis = nbgMotionDuration(160), easing = FastOutSlowInEasing),
    label = "composer chip icon scale",
  )
  val compactLabelOnly = showVisualLabel && !showValue
  val chipHorizontalPadding = if (compactLabelOnly) 6.dp else 8.dp
  val chipContentGap = if (compactLabelOnly) 4.dp else 6.dp
  val chipIconBoxSize = if (compactLabelOnly) 18.dp else 24.dp
  val chipIconSize = if (compactLabelOnly) 13.dp else 15.dp
  val chipIconCorner = if (compactLabelOnly) 7.dp else 8.dp
  NbgPressBox(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    modifier = modifier
      .height(40.dp)
      .widthIn(min = 0.dp)
      .semantics { contentDescription = "$label $displayValue" },
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .background(
          color = chipBackground,
          shape = RoundedCornerShape(14.dp),
        )
        .padding(horizontal = chipHorizontalPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(chipContentGap),
    ) {
      Box(
        modifier = Modifier
          .size(chipIconBoxSize)
          .background(
            if (selected) NbgAgentColors.PrimarySoft else NbgAgentColors.GlassButton,
            RoundedCornerShape(chipIconCorner),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          icon,
          contentDescription = null,
          tint = chipIconTint,
          modifier = Modifier
            .size(chipIconSize)
            .graphicsLayer {
              scaleX = chipIconScale
              scaleY = chipIconScale
            },
        )
      }
      if (showVisualLabel && !showValue) {
        Text(
          text = label,
          color = NbgAgentColors.TextStrong,
          fontSize = 11.sp,
          lineHeight = 14.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      } else {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
          if (showVisualLabel) {
            Text(
              text = label,
              color = NbgAgentColors.TextStrong,
              fontSize = 11.sp,
              lineHeight = 12.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            text = displayValue,
            color = if (showVisualLabel) chipLabelColor else NbgAgentColors.TextStrong,
            fontSize = if (showVisualLabel) 9.sp else 12.sp,
            lineHeight = if (showVisualLabel) 10.sp else 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (showDropdownIndicator) {
        Icon(
          Icons.Filled.KeyboardArrowDown,
          contentDescription = null,
          tint = NbgAgentColors.TextDisabled,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

internal fun nbgCompactComposerChipValue(value: String, fallback: String): String {
  val compact = value.trim()
  if (compact.isBlank()) return fallback
  if (compact.all { it == '.' || it == '-' || it == '_' }) return fallback
  return compact
}

@Composable
internal fun NbgComposerModelMenu(
  expanded: Boolean,
  config: HanakoAgentModelConfig?,
  loading: Boolean,
  error: String?,
  streaming: Boolean,
  currentModel: HanakoModelSummary?,
  onDismiss: () -> Unit,
  onReload: () -> Unit,
  onSwitchModel: (HanakoModelSummary) -> Unit,
) {
  val currentModelKey = currentModel?.let(::nbgModelKey)
  NbgComposerPopupMenu(
    expanded = expanded,
    width = 270.dp,
    menuContentDescription = "模型选择小菜单",
    onDismissRequest = onDismiss,
  ) {
    when {
      loading && config == null -> NbgComposerMenuMessage("正在读取模型...")
      error != null && config == null -> {
        NbgComposerMenuMessage("读取失败：$error")
        NbgComposerMenuRow(
          title = "重试",
          selected = false,
          enabled = !loading,
          onClick = onReload,
        )
      }
      config?.models.isNullOrEmpty() -> {
        NbgComposerMenuMessage("没有可用模型。")
        NbgComposerMenuRow(
          title = "刷新",
          selected = false,
          enabled = !loading,
          onClick = onReload,
        )
      }
      else -> {
        val groups = nbgComposerModelGroups(config)
        groups.forEach { group ->
          NbgComposerMenuSection(
            label = group.label,
            count = group.models.size,
          )
          group.models.forEach { model ->
            val current = nbgModelKey(model) == currentModelKey
            NbgComposerMenuRow(
              title = model.label,
              subtitle = nbgModelCapabilityLine(model),
              selected = current,
              enabled = !streaming && !loading && !current,
              onClick = { onSwitchModel(model) },
            )
          }
        }
        val remaining = config?.models.orEmpty().size - groups.sumOf { it.models.size }
        if (remaining > 0) {
          NbgComposerMenuMessage("还有 $remaining 个模型，保留当前小菜单的快速选择。")
        }
      }
    }
  }
}

@Composable
internal fun NbgComposerThinkingMenu(
  expanded: Boolean,
  currentLevel: String,
  options: List<NbgThinkingDepthOption>,
  streaming: Boolean,
  model: HanakoModelSummary?,
  onDismiss: () -> Unit,
  onSelectLevel: (String) -> Unit,
) {
  NbgThinkingEffortPopup(
    expanded = expanded,
    onDismissRequest = onDismiss,
  ) {
    NbgThinkingEffortCard(
      currentLevel = currentLevel,
      options = options,
      enabled = !streaming,
      onSelectLevel = onSelectLevel,
    )
  }
}

@Composable
internal fun NbgThinkingEffortPopup(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  content: @Composable () -> Unit,
) {
  if (!expanded) return

  val width = 292.dp
  val density = LocalDensity.current
  val widthPx = with(density) { width.roundToPx() }
  val gapPx = with(density) { 10.dp.roundToPx() }
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    visible = true
  }
  val popupAlpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(durationMillis = nbgMotionDuration(140), easing = FastOutSlowInEasing),
    label = "thinking effort popup alpha",
  )
  val popupScale by animateFloatAsState(
    targetValue = if (visible) 1f else 0.965f,
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "thinking effort popup scale",
  )
  val popupOffsetY by animateDpAsState(
    targetValue = if (visible) 0.dp else 10.dp,
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "thinking effort popup offset",
  )
  Popup(
    popupPositionProvider = remember(widthPx, gapPx) {
      NbgComposerPopupPositionProvider(widthPx = widthPx, gapPx = gapPx)
    },
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
  ) {
    Surface(
      modifier = Modifier
        .width(width)
        .graphicsLayer {
          alpha = popupAlpha
          scaleX = popupScale
          scaleY = popupScale
          translationY = popupOffsetY.toPx()
        }
        .semantics { contentDescription = "思考强度卡片" },
      shape = RoundedCornerShape(24.dp),
      color = NbgAgentColors.GlassSurface,
      border = BorderStroke(1.dp, NbgAgentColors.GlassBorder),
      shadowElevation = 3.dp,
    ) {
      content()
    }
  }
}

@Composable
internal fun NbgThinkingEffortCard(
  currentLevel: String,
  options: List<NbgThinkingDepthOption>,
  enabled: Boolean,
  onSelectLevel: (String) -> Unit,
) {
  val displayOptions = options.ifEmpty { NBG_THINKING_DEPTH_OPTIONS }
  val fallbackIndex = displayOptions.indexOfFirst { it.level == "auto" }.takeIf { it >= 0 } ?: 0
  val currentIndex = displayOptions.indexOfFirst { it.level == currentLevel }.takeIf { it >= 0 } ?: fallbackIndex
  var committedIndex by remember(displayOptions) { mutableStateOf(currentIndex) }
  var previewIndex by remember(displayOptions) { mutableStateOf(currentIndex) }
  var pendingLevel by remember(displayOptions) { mutableStateOf<String?>(null) }
  var dragging by remember { mutableStateOf(false) }
  LaunchedEffect(currentLevel, displayOptions) {
    if (pendingLevel == currentLevel) {
      pendingLevel = null
    }
    if (!dragging && pendingLevel == null) {
      committedIndex = currentIndex
      previewIndex = currentIndex
    }
  }
  val displayIndex = if (dragging) previewIndex else committedIndex
  val displayOption = displayOptions.getOrNull(displayIndex) ?: displayOptions.getOrElse(fallbackIndex) { displayOptions.first() }
  val maxThinking = displayOption.level == "xhigh"
  val targetProgress = if (displayOptions.size <= 1) 0f else displayIndex / displayOptions.lastIndex.toFloat()
  val progress by animateFloatAsState(
    targetValue = targetProgress,
    animationSpec = tween(durationMillis = nbgMotionDuration(if (dragging) 90 else 220), easing = FastOutSlowInEasing),
    label = "thinking effort progress",
  )
  val shimmerTransition = rememberInfiniteTransition(label = "thinking effort shimmer")
  val shimmerPhase by shimmerTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (maxThinking) 1040 else 1800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "thinking effort pixel flow",
  )
  val effectiveShimmer = if (ValueAnimator.areAnimatorsEnabled()) shimmerPhase else progress

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 18.dp)
      .graphicsLayer { alpha = if (enabled) 1f else 0.58f },
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "思考强度",
        color = NbgAgentColors.TextStrong,
        fontSize = 18.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = nbgEffortDisplayName(displayOption.level, displayOption.label),
        color = Color(0xFFB965FF),
        fontSize = 18.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Bold,
        style = TextStyle(
          shadow = Shadow(
            color = Color(0xFF7C3DFF).copy(alpha = 0.72f),
            offset = Offset(0f, 0f),
          blurRadius = 10f,
          ),
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.weight(1f))
      Box(
        modifier = Modifier
          .size(22.dp)
          .background(Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "?",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "更快",
        color = NbgAgentColors.TextMuted,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.weight(1f))
      Text(
        text = "更聪明",
        color = NbgAgentColors.TextMuted,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
      )
    }

    NbgThinkingEffortTrack(
      progress = progress,
      shimmerPhase = effectiveShimmer,
      maxThinking = maxThinking,
      options = displayOptions,
      currentLevel = currentLevel,
      enabled = enabled,
      onPreviewLevel = { index ->
        dragging = true
        previewIndex = index
      },
      onCommitLevel = { index ->
        val option = displayOptions.getOrNull(index) ?: return@NbgThinkingEffortTrack
        dragging = false
        committedIndex = index
        previewIndex = index
        if (option.level != currentLevel) {
          pendingLevel = option.level
          onSelectLevel(option.level)
        }
      },
      onCancelPreview = {
        dragging = false
        previewIndex = committedIndex
      },
    )
  }
}

internal fun nbgEffortDisplayName(level: String, fallback: String): String =
  when (level) {
    "off" -> "关闭"
    "auto" -> "自动"
    "low" -> "快速"
    "medium" -> "均衡"
    "high" -> "深度"
    "xhigh" -> "最大思考"
    else -> fallback
  }

@Composable
internal fun NbgThinkingEffortTrack(
  progress: Float,
  shimmerPhase: Float,
  maxThinking: Boolean,
  options: List<NbgThinkingDepthOption>,
  currentLevel: String,
  enabled: Boolean,
  onPreviewLevel: (Int) -> Unit,
  onCommitLevel: (Int) -> Unit,
  onCancelPreview: () -> Unit,
) {
  val trackShape = RoundedCornerShape(15.dp)
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(42.dp)
      .clip(trackShape)
      .background(NbgAgentColors.SurfaceLow, trackShape)
      .pointerInput(enabled, options) {
        if (!enabled || options.isEmpty()) return@pointerInput
        fun offsetToIndex(x: Float): Int =
          if (options.size <= 1 || size.width <= 0) {
            0
          } else {
            ((x / size.width.toFloat()).coerceIn(0f, 1f) * options.lastIndex).roundToInt()
              .coerceIn(0, options.lastIndex)
          }
        detectTapGestures { offset ->
          onCommitLevel(offsetToIndex(offset.x))
        }
      }
      .pointerInput(enabled, options) {
        if (!enabled || options.isEmpty()) return@pointerInput
        var latestIndex = 0
        fun offsetToIndex(x: Float): Int =
          if (options.size <= 1 || size.width <= 0) {
            0
          } else {
            ((x / size.width.toFloat()).coerceIn(0f, 1f) * options.lastIndex).roundToInt()
              .coerceIn(0, options.lastIndex)
          }
        detectDragGestures(
          onDragStart = { offset ->
            latestIndex = offsetToIndex(offset.x)
            onPreviewLevel(latestIndex)
          },
          onDragEnd = {
            onCommitLevel(latestIndex)
          },
          onDragCancel = {
            onCancelPreview()
          },
        ) { change, _ ->
          change.consume()
          latestIndex = offsetToIndex(change.position.x)
          onPreviewLevel(latestIndex)
        }
      }
      .semantics { contentDescription = "思考强度 ${nbgEffortDisplayName(currentLevel, options.firstOrNull { it.level == currentLevel }?.label.orEmpty())}" },
  ) {
    val trackHeight = 28.dp.toPx()
    val knobWidth = 27.dp.toPx()
    val knobHeight = 28.dp.toPx()
    val trackLeft = knobWidth / 2f + 2.dp.toPx()
    val trackRight = size.width - knobWidth / 2f - 2.dp.toPx()
    val trackWidth = trackRight - trackLeft
    val centerY = size.height / 2f
    val trackTop = centerY - trackHeight / 2f
    val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
    val knobX = trackLeft + trackWidth * progress.coerceIn(0f, 1f)
    val fillWidth = trackWidth * progress.coerceIn(0.02f, 1f)

    drawRoundRect(
      color = NbgAgentColors.SurfaceContainerHigh,
      topLeft = Offset(trackLeft - 5.dp.toPx(), trackTop),
      size = Size(trackWidth + 10.dp.toPx(), trackHeight),
      cornerRadius = radius,
    )
    drawRoundRect(
      brush = Brush.horizontalGradient(
        colors = if (maxThinking) {
          listOf(
            Color(0xFFEDE9FE).copy(alpha = 0.22f),
            Color(0xFF7C3AED).copy(alpha = 0.52f),
            Color(0xFFC084FC).copy(alpha = 0.86f),
            Color(0xFFFFFFFF).copy(alpha = 0.96f),
          )
        } else {
          listOf(
            Color(0xFFEDE9FE).copy(alpha = 0.18f),
            Color(0xFF8B5CF6).copy(alpha = 0.38f),
            Color(0xFFA855F7).copy(alpha = 0.62f),
            Color(0xFFE9D5FF).copy(alpha = 0.88f),
          )
        },
        startX = trackLeft,
        endX = trackRight,
      ),
      topLeft = Offset(trackLeft, trackTop),
      size = Size(fillWidth, trackHeight),
      cornerRadius = radius,
      alpha = 0.86f,
    )
    if (maxThinking) {
      drawRoundRect(
        brush = Brush.horizontalGradient(
          colors = listOf(
            Color.Transparent,
            Color(0xFF9E5BFF).copy(alpha = 0.2f),
            Color(0xFFFFF9FF).copy(alpha = 0.22f),
          ),
          startX = trackLeft,
          endX = trackRight,
        ),
        topLeft = Offset(trackLeft, trackTop),
        size = Size(trackWidth, trackHeight),
        cornerRadius = radius,
      )
    }

    val columns = if (maxThinking) 46 else 38
    val rows = if (maxThinking) 5 else 4
    val gap = 1.7.dp.toPx()
    val blockWidth = (trackWidth - gap * (columns - 1)) / columns
    val blockHeight = if (maxThinking) 3.4.dp.toPx() else 3.6.dp.toPx()
    val gridHeight = rows * blockHeight + (rows - 1) * gap
    val gridTop = centerY - gridHeight / 2f
    repeat(columns) { column ->
      val pct = if (columns == 1) 0f else column / (columns - 1).toFloat()
      val wave = (1f - kotlin.math.abs(pct - shimmerPhase) * 8f).coerceIn(0f, 1f)
      val maxWave = if (maxThinking) {
        val shiftedPhase = (shimmerPhase + 0.38f) % 1f
        (1f - kotlin.math.abs(pct - shiftedPhase) * 5.6f).coerceIn(0f, 1f)
      } else {
        0f
      }
      val active = (progress - pct + 0.08f).coerceIn(0f, 1f)
      val columnAlpha = (active * if (maxThinking) 0.9f else 0.76f + wave * 0.28f + maxWave * 0.42f)
        .coerceIn(if (maxThinking) 0.08f else 0.04f, if (maxThinking) 1f else 0.92f)
      val x = trackLeft + column * (blockWidth + gap)
      repeat(rows) { row ->
        val rowCenter = kotlin.math.abs(row - (rows - 1) / 2f)
        val rowAlpha = (1f - rowCenter * 0.16f).coerceIn(0.55f, 1f)
        drawRoundRect(
          color = if (maxThinking && maxWave > 0.35f) {
            Color(0xFFFFF7FF).copy(alpha = columnAlpha * rowAlpha)
          } else {
            Color(0xFFD8A7FF).copy(alpha = columnAlpha * rowAlpha)
          },
          topLeft = Offset(x, gridTop + row * (blockHeight + gap)),
          size = Size(blockWidth.coerceAtLeast(1f), blockHeight),
          cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
        )
      }
    }

    val knobLeft = (knobX - knobWidth / 2f).coerceIn(0f, size.width - knobWidth)
    val knobTop = centerY - knobHeight / 2f
    val knobAura = if (maxThinking) 0.34f else 0.18f
    drawRoundRect(
      color = Color(0xFFFFFFFF).copy(alpha = knobAura),
      topLeft = Offset(knobLeft - 3.dp.toPx(), knobTop - 3.dp.toPx()),
      size = Size(knobWidth + 6.dp.toPx(), knobHeight + 6.dp.toPx()),
      cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx()),
    )
    drawRoundRect(
      brush = Brush.verticalGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFE7E7EA)),
        startY = knobTop,
        endY = knobTop + knobHeight,
      ),
      topLeft = Offset(knobLeft, knobTop),
      size = Size(knobWidth, knobHeight),
      cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx()),
    )
  }
}

@Composable
internal fun NbgComposerPermissionMenu(
  expanded: Boolean,
  currentMode: String,
  streaming: Boolean,
  onDismiss: () -> Unit,
  onSelectMode: (String) -> Unit,
) {
  val choices = listOf(
    Triple("operate", "操作", "首次使用前确认，之后允许 agent 更主动执行操作。"),
    Triple("ask", "先问", "中高风险工具先请求确认。"),
    Triple("read_only", "计划", "先分析和制定方案，不写文件或执行写操作。"),
  )
  NbgComposerPopupMenu(
    expanded = expanded,
    width = 218.dp,
    menuContentDescription = "权限设置小菜单",
    onDismissRequest = onDismiss,
  ) {
    choices.forEach { (mode, title, subtitle) ->
      val current = mode == currentMode
      NbgComposerMenuRow(
        title = title,
        subtitle = subtitle,
        selected = current,
        enabled = !streaming && !current,
        onClick = { onSelectMode(mode) },
      )
    }
    if (streaming) NbgComposerMenuMessage("当前回复还在输出，结束后才能切换权限。")
  }
}

@Composable
internal fun NbgComposerPopupMenu(
  expanded: Boolean,
  width: Dp,
  menuContentDescription: String,
  onDismissRequest: () -> Unit,
  content: @Composable () -> Unit,
) {
  if (!expanded) return

  val density = LocalDensity.current
  val widthPx = with(density) { width.roundToPx() }
  val gapPx = with(density) { 8.dp.roundToPx() }
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    visible = true
  }
  val popupAlpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(durationMillis = nbgMotionDuration(140), easing = FastOutSlowInEasing),
    label = "nbg composer popup alpha",
  )
  val popupScale by animateFloatAsState(
    targetValue = if (visible) 1f else 0.965f,
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "nbg composer popup scale",
  )
  val popupOffsetY by animateDpAsState(
    targetValue = if (visible) 0.dp else 8.dp,
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "nbg composer popup offset",
  )
  Popup(
    popupPositionProvider = remember(widthPx, gapPx) {
      NbgComposerPopupPositionProvider(widthPx = widthPx, gapPx = gapPx)
    },
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
  ) {
    Surface(
      modifier = Modifier
        .width(width)
        .graphicsLayer {
          alpha = popupAlpha
          scaleX = popupScale
          scaleY = popupScale
          translationY = popupOffsetY.toPx()
        }
        .semantics { contentDescription = menuContentDescription },
      shape = RoundedCornerShape(18.dp),
      color = NbgAgentColors.GlassSurface,
      border = BorderStroke(1.dp, NbgAgentColors.GlassBorder),
      shadowElevation = 2.dp,
    ) {
      Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
      ) {
        content()
      }
    }
  }
}

internal class NbgComposerPopupPositionProvider(
  private val widthPx: Int,
  private val gapPx: Int,
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val popupWidth = maxOf(widthPx, popupContentSize.width)
    val x = (anchorBounds.left + anchorBounds.width / 2 - popupWidth / 2)
      .coerceIn(8, maxOf(8, windowSize.width - popupWidth - 8))
    val yAbove = anchorBounds.top - popupContentSize.height - gapPx
    val y = if (yAbove >= 8) yAbove else (anchorBounds.bottom + gapPx)
      .coerceAtMost(windowSize.height - popupContentSize.height - 8)
    return IntOffset(x, y)
  }
}

@Composable
internal fun NbgComposerMenuRow(
  title: String,
  subtitle: String = "",
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    color = Color.Transparent,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 3.dp)
        .background(
          color = if (selected) NbgAgentColors.Selected else Color.Transparent,
          shape = RoundedCornerShape(12.dp),
        )
        .heightIn(min = 36.dp)
        .padding(horizontal = 10.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier = Modifier
          .size(6.dp)
          .background(if (selected) NbgAgentColors.Primary else Color.Transparent, CircleShape),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = title,
          color = if (enabled || selected) NbgAgentColors.TextStrong else NbgAgentColors.TextDisabled,
          fontSize = 13.sp,
          fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        subtitle.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            color = NbgAgentColors.TextMuted,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (selected) {
        Text(
          text = "当前",
          color = NbgAgentColors.Primary,
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
internal fun NbgComposerMenuSection(label: String, count: Int) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = label,
      color = NbgAgentColors.TextMuted,
      fontSize = 10.sp,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = count.toString(),
      color = NbgAgentColors.TextDisabled,
      fontSize = 10.sp,
      maxLines = 1,
    )
  }
}

@Composable
internal fun NbgComposerMenuMessage(text: String) {
  Text(
    text = text,
    color = NbgAgentColors.TextMuted,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
  )
}

@Composable
internal fun NbgToolIcon(
  icon: ImageVector,
  contentDescription: String,
  selected: Boolean = false,
  onClick: () -> Unit,
) {
  NbgPressBox(
    onClick = onClick,
    modifier = Modifier
      .size(44.dp)
      .semantics { this.contentDescription = contentDescription },
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Icon(
        icon,
        contentDescription = null,
        tint = if (selected) NbgAgentColors.Primary else NbgAgentColors.Tool,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}

@Composable
internal fun NbgPlainIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
) {
  NbgPressBox(
    onClick = onClick,
    shape = RoundedCornerShape(13.dp),
    modifier = Modifier
      .size(40.dp)
      .semantics { this.contentDescription = contentDescription },
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(NbgAgentColors.SurfaceContainer, RoundedCornerShape(13.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = NbgAgentColors.Icon, modifier = Modifier.size(19.dp))
    }
  }
}

@Composable
internal fun NbgSendButton(
  enabled: Boolean,
  streaming: Boolean,
  steering: Boolean = false,
  onClick: () -> Unit,
) {
  val visualState = when {
    !enabled -> NbgSendButtonVisualState.Disabled
    streaming -> NbgSendButtonVisualState.Stop
    steering -> NbgSendButtonVisualState.Steer
    else -> NbgSendButtonVisualState.Send
  }
  val targetBackground = when (visualState) {
    NbgSendButtonVisualState.Disabled -> NbgAgentColors.SendDisabled
    NbgSendButtonVisualState.Stop -> NbgAgentColors.SendStop
    NbgSendButtonVisualState.Steer -> NbgAgentColors.SendSteer
    NbgSendButtonVisualState.Send -> NbgAgentColors.SendReady
  }
  val targetIconTint = when (visualState) {
    NbgSendButtonVisualState.Disabled -> NbgAgentColors.TextMuted
    NbgSendButtonVisualState.Stop -> NbgAgentColors.StatusRed
    NbgSendButtonVisualState.Steer -> NbgAgentColors.SendSteerIcon
    NbgSendButtonVisualState.Send -> NbgAgentColors.SendReadyIcon
  }
  val targetBorder = when (visualState) {
    NbgSendButtonVisualState.Disabled -> NbgAgentColors.SendDisabledBorder
    NbgSendButtonVisualState.Stop -> NbgAgentColors.SendStopBorder
    NbgSendButtonVisualState.Steer -> NbgAgentColors.SendSteerBorder
    NbgSendButtonVisualState.Send -> NbgAgentColors.SendReadyBorder
  }
  val backgroundColor by animateColorAsState(
    targetValue = targetBackground,
    animationSpec = tween(durationMillis = nbgMotionDuration(160), easing = FastOutSlowInEasing),
    label = "send button background",
  )
  val iconTint by animateColorAsState(
    targetValue = targetIconTint,
    animationSpec = tween(durationMillis = nbgMotionDuration(160), easing = FastOutSlowInEasing),
    label = "send button icon tint",
  )
  val borderColor by animateColorAsState(
    targetValue = targetBorder,
    animationSpec = tween(durationMillis = nbgMotionDuration(160), easing = FastOutSlowInEasing),
    label = "send button border",
  )
  val iconScale by animateFloatAsState(
    targetValue = if (visualState == NbgSendButtonVisualState.Steer) 0.94f else 1f,
    animationSpec = tween(durationMillis = nbgMotionDuration(160), easing = FastOutSlowInEasing),
    label = "send button icon scale",
  )
  NbgPressBox(
    onClick = onClick,
    enabled = enabled,
    shape = CircleShape,
    modifier = Modifier
      .size(44.dp)
      .semantics {
        this.contentDescription = when {
          streaming -> "停止生成"
          steering -> "插话"
          else -> "发送"
        }
      },
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      shape = CircleShape,
      color = backgroundColor,
      contentColor = iconTint,
      border = BorderStroke(1.dp, borderColor),
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        AnimatedContent(
          targetState = visualState,
          transitionSpec = {
            fadeIn(animationSpec = tween(nbgMotionDuration(110), easing = FastOutSlowInEasing)) togetherWith
              fadeOut(animationSpec = tween(nbgMotionDuration(90), easing = FastOutSlowInEasing))
          },
          label = "send button icon",
        ) { state ->
          Icon(
            when (state) {
              NbgSendButtonVisualState.Stop -> HugeIcons.StopCircle
              else -> HugeIcons.ArrowUp02
            },
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
              .size(21.dp)
              .graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
              },
          )
        }
      }
    }
  }
}

@Composable
internal fun NbgPressBox(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shape: Shape = RoundedCornerShape(12.dp),
  content: @Composable () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (enabled && isPressed) 0.96f else 1f,
    animationSpec = tween(
      durationMillis = nbgMotionDuration(if (isPressed) 90 else 160),
      easing = FastOutSlowInEasing,
    ),
    label = "nbg press",
  )
  val pressAlpha by animateFloatAsState(
    targetValue = when {
      !enabled -> 0.46f
      isPressed -> 0.72f
      else -> 1f
    },
    animationSpec = tween(
      durationMillis = nbgMotionDuration(if (isPressed) 90 else 160),
      easing = FastOutSlowInEasing,
    ),
    label = "nbg press alpha",
  )
  Box(
    modifier = modifier
      .graphicsLayer {
        alpha = pressAlpha
        scaleX = pressScale
        scaleY = pressScale
      }
      .clip(shape)
      .clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
      ),
  ) {
    content()
  }
}

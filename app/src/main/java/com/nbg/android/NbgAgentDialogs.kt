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
import androidx.compose.foundation.layout.Spacer
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
internal fun NbgDialogSectionLabel(label: String) {
  Text(
    text = label,
    color = NbgAgentColors.TextStrong,
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
  )
}

@Composable
internal fun NbgRevertTurnDialog(
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("Checkpoint / Rollback", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "将撤回最近一轮 AI 回复，并让 HanakoPro 尝试还原这一轮产生的文件修改。",
          color = NbgAgentColors.TextMuted,
          fontSize = 14.sp,
          lineHeight = 20.sp,
        )
        Text(
          text = "当前支持最新 turn 回滚；完成后会刷新会话历史，并在系统消息里显示已还原文件数量。",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      }
    },
    confirmButton = {
      NbgDialogAction(label = "撤回", primary = true, onClick = onConfirm)
    },
    dismissButton = {
      NbgDialogAction(label = "取消", onClick = onDismiss)
    },
  )
}

@Composable
internal fun NbgOperatePermissionModeDialog(
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("启用操作模式", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Text(
        text = "操作模式会让 Agent 在当前会话中更主动地执行会改变本地或外部状态的工具。公开测试阶段需要先确认；未确认时会保持先问模式。",
        color = NbgAgentColors.TextMuted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      )
    },
    confirmButton = {
      NbgDialogAction(label = "启用操作", primary = true, onClick = onConfirm)
    },
    dismissButton = {
      NbgDialogAction(label = "保持先问", onClick = onDismiss)
    },
  )
}

@Composable
internal fun NbgRenameSessionDialog(
  initialTitle: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var title by remember(initialTitle) { mutableStateOf(initialTitle) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("重命名会话", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        singleLine = true,
        label = { Text("标题") },
      )
    },
    confirmButton = {
      NbgDialogAction(
        label = "保存",
        primary = true,
        enabled = title.trim().isNotEmpty(),
        onClick = { onConfirm(title.trim()) },
      )
    },
    dismissButton = {
      NbgDialogAction(label = "取消", onClick = onDismiss)
    },
  )
}

@Composable
internal fun NbgDeleteSessionDialog(
  title: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("删除对话", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Text(
        text = "删除后会从历史列表移除：$title",
        color = NbgAgentColors.TextMuted,
        fontSize = 14.sp,
      )
    },
    confirmButton = {
      NbgDialogAction(label = "删除", primary = true, onClick = onConfirm)
    },
    dismissButton = {
      NbgDialogAction(label = "取消", onClick = onDismiss)
    },
  )
}

@Composable
internal fun NbgDialogAction(
  label: String,
  primary: Boolean = false,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(12.dp),
    color = if (primary) NbgAgentColors.PrimaryContainer else NbgAgentColors.SurfaceLow,
    contentColor = NbgAgentColors.TextStrong,
    border = BorderStroke(1.dp, if (primary) NbgAgentColors.SurfaceBorder else NbgAgentColors.InputBorder),
  ) {
    Text(
      text = label,
      fontSize = 14.sp,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
    )
  }
}

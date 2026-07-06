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
internal fun NbgFileShareDialog(
  state: NbgFileShareServerState,
  onRefresh: () -> Unit,
  onStartReadOnly: () -> Unit,
  onStartReadWrite: () -> Unit,
  onDismiss: () -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  var copiedLabel by remember { mutableStateOf<String?>(null) }
  val statusText = when {
    state.running -> "运行中"
    state.message.isNotBlank() -> state.message
    else -> "未启动"
  }
  val address = state.localUrl.substringBefore(':').ifBlank { "等待地址" }
  val port = state.port?.toString() ?: state.localUrl.substringAfter(':', missingDelimiterValue = "").ifBlank { "等待端口" }
  fun copyValue(label: String, value: String) {
    if (value.isNotBlank() && !value.startsWith("等待")) {
      clipboard.setText(AnnotatedString(value))
      copiedLabel = label
      scope.launch {
        delay(1_200)
        if (copiedLabel == label) {
          copiedLabel = null
        }
      }
    }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("FTP 共享", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        NbgFileShareInfoRow("状态", statusText)
        NbgFileShareInfoRow("模式", state.accessMode.label)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          NbgDialogAction(
            label = "只读",
            primary = state.accessMode == NbgFileShareAccessMode.ReadOnly,
            enabled = state.accessMode != NbgFileShareAccessMode.ReadOnly,
            onClick = onStartReadOnly,
          )
          NbgDialogAction(
            label = "读写",
            primary = state.accessMode == NbgFileShareAccessMode.ReadWrite,
            enabled = state.accessMode != NbgFileShareAccessMode.ReadWrite,
            onClick = onStartReadWrite,
          )
        }
        NbgFileShareInfoRow("地址", address, monospace = true, copied = copiedLabel == "地址", onCopy = { copyValue("地址", address) })
        NbgFileShareInfoRow("端口", port, monospace = true, copied = copiedLabel == "端口", onCopy = { copyValue("端口", port) })
        NbgFileShareInfoRow("账号", state.username, monospace = true, copied = copiedLabel == "账号", onCopy = { copyValue("账号", state.username) })
        val password = state.password.ifBlank { "未生成" }
        NbgFileShareInfoRow("密码", password, monospace = true, copied = copiedLabel == "密码", onCopy = { copyValue("密码", password) })
      }
    },
    confirmButton = {
      NbgDialogAction(label = "刷新", onClick = onRefresh)
    },
    dismissButton = {
      NbgDialogAction(label = "关闭", primary = true, onClick = onDismiss)
    },
  )
}

@Composable
internal fun NbgFileShareInfoRow(
  label: String,
  value: String,
  monospace: Boolean = false,
  copied: Boolean = false,
  onCopy: (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(NbgAgentColors.SurfaceLow, RoundedCornerShape(12.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = label,
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        color = NbgAgentColors.TextStrong,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFamily = if (monospace) FontFamily.Monospace else nbgCurrentFontFamily(),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    onCopy?.let {
      if (copied) {
        Text(
          text = "已复制",
          color = NbgAgentColors.Primary,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
        )
      } else {
        NbgPlainIconButton(
          icon = Icons.Filled.ContentCopy,
          contentDescription = "复制$label",
          onClick = it,
        )
      }
    }
  }
}

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
import androidx.compose.material.icons.filled.AccountTree
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
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
internal fun NbgAgentChatScreen(
  title: String,
  conversationPath: String?,
  runStatus: NbgChatRunStatus,
  petState: NbgPetStoreState,
  runtimeStatus: HanakoRuntimeStatus,
  messages: List<NbgAgentMessage>,
  historyRenderVersion: Long,
  todos: List<HanakoTodoItem>,
  agentModelConfig: HanakoAgentModelConfig?,
  agentModelConfigLoading: Boolean,
  agentModelConfigError: String?,
  modelName: String?,
  preferredModel: NbgChatPreferences?,
  permissionMode: String,
  permissionModeLabel: String,
  thinkingLevel: String,
  thinkingLevelLabel: String,
  confirmation: HanakoConfirmation?,
  streaming: Boolean,
  compressing: Boolean,
  compressionAvailable: Boolean,
  sendBlocked: Boolean,
  onOpenDrawer: () -> Unit,
  onNewConversation: () -> Unit,
  onLoadAgentModelConfig: () -> Unit,
  onSwitchComposerModel: (HanakoModelSummary) -> Unit,
  onSetComposerPermissionMode: (String) -> Unit,
  onSetComposerThinkingLevel: (String) -> Unit,
  onReplayLatestTurn: () -> Unit,
  onRequestRevertLatestTurn: () -> Unit,
  onCompressFork: () -> Unit,
  onCompleteTodos: () -> Unit,
  onSend: (String) -> Unit,
  onAbort: () -> Unit,
  onInterruptTerminal: (String) -> Unit,
  onResolveConfirmation: (String, String) -> Unit,
  onOpenPets: () -> Unit,
  onHidePet: () -> Unit,
) {
  var draft by remember { mutableStateOf("") }
  var composerFocused by remember { mutableStateOf(false) }
  var composerBoundsInRoot by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  val pretextCache = remember { NbgPretextCache() }
  val visibleMessages = messages
  val visibleMessageListSignature by remember {
    derivedStateOf { nbgMessageListSignature(visibleMessages) }
  }
  val visibleItems = remember(visibleMessageListSignature) {
    nbgMessageListItems(messages = visibleMessages)
  }
  val animatedMessageKey = nbgLastMessagePrimaryItemKey(visibleItems)
  val autoScrollSignature by remember {
    derivedStateOf { nbgAutoScrollSignature(visibleMessages) }
  }
  val conversationScrollKey by remember {
    derivedStateOf { nbgConversationScrollKey(conversationPath, visibleMessages, historyRenderVersion) }
  }
  val bottomAnchorIndex by remember {
    derivedStateOf {
      val topItems = if (visibleItems.isEmpty()) 1 else 0
      topItems + visibleItems.size
    }
  }
  var followOutput by remember(conversationScrollKey) { mutableStateOf(true) }
  val isAtBottom by remember {
    derivedStateOf { listState.isAtChatBottom() }
  }
  val showScrollToBottom by remember {
    derivedStateOf { visibleItems.isNotEmpty() && !isAtBottom }
  }
  var lastConfirmation by remember { mutableStateOf<HanakoConfirmation?>(null) }
  var resolvingConfirmationId by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(confirmation) {
    if (confirmation != null) {
      if (confirmation.confirmId != resolvingConfirmationId) {
        resolvingConfirmationId = null
      }
      lastConfirmation = confirmation
    } else {
      resolvingConfirmationId = null
    }
  }
  LaunchedEffect(listState) {
    snapshotFlow { listState.isScrollInProgress to listState.isAtChatBottom() }
      .distinctUntilChanged()
      .collect { (scrolling, atBottom) ->
        if (scrolling && !atBottom) {
          followOutput = false
        } else if (atBottom) {
          followOutput = true
        }
      }
  }
  LaunchedEffect(conversationScrollKey, visibleItems.isNotEmpty()) {
    if (visibleItems.isEmpty()) return@LaunchedEffect
    followOutput = true
    repeat(HANA_MOBILE_CHAT_RESTORE_SCROLL_PASSES) {
      listState.scrollToChatBottom(bottomAnchorIndex)
      delay(HANA_MOBILE_CHAT_RESTORE_SCROLL_DELAY_MS)
    }
  }
  LaunchedEffect(autoScrollSignature, followOutput) {
    if (visibleItems.isNotEmpty() && followOutput) {
      listState.scrollToChatBottom(bottomAnchorIndex)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(NbgAgentColors.Background),
  ) {
    Scaffold(
      topBar = {
        NbgChatTopBar(
          title = title,
          runStatus = runStatus,
          onOpenDrawer = onOpenDrawer,
          onNewConversation = onNewConversation,
          onReplayLatestTurn = onReplayLatestTurn,
          onRequestRevertLatestTurn = onRequestRevertLatestTurn,
          onCompressFork = onCompressFork,
          compressing = compressing,
          compressionAvailable = compressionAvailable,
          streaming = streaming,
        )
      },
      bottomBar = {
        Column {
          NbgRuntimeStatusBar(runtimeStatus)
          NbgTodoBar(
            todos = todos,
            streaming = streaming,
            onCompleteTodos = onCompleteTodos,
          )
          AnimatedVisibility(
            visible = confirmation != null && confirmation.confirmId != resolvingConfirmationId,
            enter = fadeIn(animationSpec = tween(nbgMotionDuration(140), easing = FastOutSlowInEasing)) +
              slideInVertically(
                animationSpec = tween(nbgMotionDuration(180), easing = FastOutSlowInEasing),
                initialOffsetY = { it / 4 },
              ),
            exit = fadeOut(animationSpec = tween(nbgMotionDuration(100), easing = FastOutSlowInEasing)) +
              slideOutVertically(
                animationSpec = tween(nbgMotionDuration(140), easing = FastOutSlowInEasing),
                targetOffsetY = { it / 5 },
              ),
          ) {
            lastConfirmation?.let {
              NbgSessionConfirmationCard(
                confirmation = it,
                onConfirm = {
                  resolvingConfirmationId = it.confirmId
                  onResolveConfirmation(it.confirmId, "confirmed")
                },
                onReject = {
                  resolvingConfirmationId = it.confirmId
                  onResolveConfirmation(it.confirmId, "rejected")
                },
              )
            }
          }
          NbgChatComposer(
            draft = draft,
            streaming = streaming,
            sendBlocked = sendBlocked,
            onDraftChange = { draft = it },
            onSend = { inputText ->
              val prompt = inputText.trim()
              if (prompt.isNotEmpty()) {
                draft = ""
                onSend(prompt)
              }
            },
            onAbort = onAbort,
            agentModelConfig = agentModelConfig,
            agentModelConfigLoading = agentModelConfigLoading,
            agentModelConfigError = agentModelConfigError,
            onLoadAgentModelConfig = onLoadAgentModelConfig,
            onSwitchComposerModel = onSwitchComposerModel,
            modelName = modelName,
            preferredModel = preferredModel,
            permissionMode = permissionMode,
            permissionModeLabel = permissionModeLabel,
            onSetPermissionMode = onSetComposerPermissionMode,
            thinkingLevel = thinkingLevel,
            thinkingLevelLabel = thinkingLevelLabel,
            onSetThinkingLevel = onSetComposerThinkingLevel,
            onFocusedChange = { composerFocused = it },
            onComposerBoundsChanged = { bounds ->
              val previous = composerBoundsInRoot
              if (previous == null ||
                abs(previous.top - bounds.top) > 1f ||
                abs(previous.bottom - bounds.bottom) > 1f ||
                abs(previous.left - bounds.left) > 1f ||
                abs(previous.right - bounds.right) > 1f
              ) {
                composerBoundsInRoot = bounds
              }
            },
          )
        }
      },
      containerColor = NbgAgentColors.Background,
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(NbgAgentColors.Background)
          .imePadding(),
      ) {
        LazyColumn(
          state = listState,
          contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innerPadding.calculateTopPadding() + 10.dp,
            bottom = innerPadding.calculateBottomPadding(),
          ),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxSize()
            .background(NbgAgentColors.Background),
        ) {
          if (visibleItems.isEmpty()) {
            item {
              Spacer(Modifier.height(18.dp))
            }
          } else {
            items(
              items = visibleItems,
              key = { it.key },
              contentType = { it.contentType },
            ) { item ->
              NbgMessageEnterContainer(enabled = item.showHeader && item.key == animatedMessageKey) {
                NbgMessageBlock(
                  message = item.message,
                  showHeader = item.showHeader,
                  preparedTextKey = item.key,
                  pretextCache = pretextCache,
                  animatePreparedBlocks = item.key == animatedMessageKey,
                  historyNativeText = false,
                  terminalOutput = item.terminalOutput,
                  terminalChunkIndex = item.terminalChunkIndex,
                  terminalChunkCount = item.terminalChunkCount,
                  filePreview = item.filePreview,
                  filePreviewChunkIndex = item.filePreviewChunkIndex,
                  filePreviewChunkCount = item.filePreviewChunkCount,
                  fileDiff = item.fileDiff,
                  fileDiffLines = item.fileDiffLines,
                  fileDiffChunkIndex = item.fileDiffChunkIndex,
                  fileDiffChunkCount = item.fileDiffChunkCount,
                  onInterruptTerminal = onInterruptTerminal,
                )
              }
            }
          }
          item(key = "chat-bottom-anchor", contentType = "bottom-anchor") {
            Spacer(Modifier.height(2.dp))
          }
        }
        NbgScrollToBottomButton(
          visible = showScrollToBottom,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .zIndex(2f)
            .padding(
              end = 18.dp,
              bottom = innerPadding.calculateBottomPadding() + 14.dp,
            ),
          onClick = {
            followOutput = true
            scope.launch {
              listState.scrollToChatBottom(bottomAnchorIndex)
            }
          },
        )
        if (!petState.hidden) {
          NbgPixelPetOverlay(
            runStatus = runStatus,
            pet = petState.currentPet,
            bottomInset = innerPadding.calculateBottomPadding(),
            composerBoundsInRoot = composerBoundsInRoot,
            composerActive = composerFocused || draft.isNotBlank(),
            onOpenPets = onOpenPets,
            onHidePet = onHidePet,
            modifier = Modifier
              .fillMaxSize()
              .zIndex(3f),
          )
        }
      }
    }
  }
}

internal fun nbgConversationScrollKey(
  conversationPath: String?,
  messages: List<NbgAgentMessage>,
  historyRenderVersion: Long = 0L,
): String {
  val path = conversationPath?.takeIf { it.isNotBlank() }
  if (path != null) return "session:$path:$historyRenderVersion"
  val first = messages.firstOrNull() ?: return "draft:empty"
  return "draft:${first.id}:${first.role}"
}

internal fun LazyListState.isAtChatBottom(): Boolean {
  val totalItems = layoutInfo.totalItemsCount
  if (totalItems == 0) return true
  val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
  return lastVisibleIndex >= totalItems - 1
}

private suspend fun LazyListState.scrollToChatBottom(bottomAnchorIndex: Int) {
  if (bottomAnchorIndex < 0) return
  try {
    scrollToItem(bottomAnchorIndex, scrollOffset = HANA_MOBILE_CHAT_BOTTOM_SCROLL_OFFSET)
  } catch (_: IllegalArgumentException) {
    // The anchor can be requested one frame before LazyColumn publishes its new item count.
  }
}

@Composable
internal fun NbgRuntimeStatusBar(status: HanakoRuntimeStatus) {
  if (status.isEmpty) return
  val items = listOfNotNull(
    status.browserLabel?.let { Icons.Filled.Search to it },
    status.usageLabel?.let { Icons.Filled.ContentCopy to it },
  )
  Surface(color = Color.Transparent) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 10.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      items.forEach { (icon, label) ->
        NbgRuntimeStatusChip(icon = icon, label = label)
      }
    }
  }
}

@Composable
internal fun NbgScrollToBottomButton(
  visible: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(animationSpec = tween(nbgMotionDuration(140), easing = FastOutSlowInEasing)) +
      slideInVertically(
        animationSpec = tween(nbgMotionDuration(170), easing = FastOutSlowInEasing),
        initialOffsetY = { it / 3 },
      ),
    exit = fadeOut(animationSpec = tween(nbgMotionDuration(110), easing = FastOutSlowInEasing)) +
      slideOutVertically(
        animationSpec = tween(nbgMotionDuration(130), easing = FastOutSlowInEasing),
        targetOffsetY = { it / 4 },
      ),
    modifier = modifier,
  ) {
    NbgPressBox(
      onClick = onClick,
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .size(44.dp)
        .semantics { contentDescription = "移动到底部" },
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(NbgAgentColors.SurfaceContainerHigh, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Filled.KeyboardArrowDown,
          contentDescription = null,
          tint = NbgAgentColors.TextStrong,
          modifier = Modifier.size(24.dp),
        )
      }
    }
  }
}

@Composable
internal fun NbgRuntimeStatusChip(
  icon: ImageVector,
  label: String,
) {
  Surface(
    modifier = Modifier.height(34.dp),
    shape = RoundedCornerShape(17.dp),
    color = NbgAgentColors.StatusChip,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
    contentColor = NbgAgentColors.TextStrong,
  ) {
    Row(
      modifier = Modifier
        .height(34.dp)
        .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = NbgAgentColors.TextMuted,
        modifier = Modifier.size(15.dp),
      )
      Text(
        text = label,
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 180.dp),
      )
    }
  }
}

@Composable
internal fun NbgSessionConfirmationCard(
  confirmation: HanakoConfirmation,
  onConfirm: () -> Unit,
  onReject: () -> Unit,
) {
  val pending = confirmation.status == "pending"
  val danger = confirmation.severity == "danger" || confirmation.riskTier == NbgPermissionRiskTier.Dangerous.wireName
  val elevated = confirmation.severity == "elevated" || confirmation.riskTier == NbgPermissionRiskTier.High.wireName
  Surface(color = Color.Transparent) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 4.dp),
      shape = RoundedCornerShape(18.dp),
      color = when {
        danger -> NbgAgentColors.ConfirmDangerSurface
        elevated -> NbgAgentColors.ConfirmElevatedSurface
        else -> NbgAgentColors.SurfaceLow
      },
      border = BorderStroke(
        1.dp,
        when {
          danger -> NbgAgentColors.ConfirmDangerBorder
          elevated -> NbgAgentColors.ConfirmElevatedBorder
          else -> NbgAgentColors.InputBorder
        },
      ),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier = Modifier
              .size(30.dp)
              .background(NbgAgentColors.PrimarySoft, CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = NbgAgentColors.Primary,
              modifier = Modifier.size(17.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = confirmation.title,
              color = NbgAgentColors.TextStrong,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            val detail = listOf(
              confirmation.riskLabel,
              confirmation.subjectLabel,
              confirmation.subjectDetail,
              confirmation.targetLabel.takeIf { it != confirmation.subjectDetail },
            ).filterNotNull().filter { it.isNotBlank() }.joinToString(" / ")
            if (detail.isNotBlank()) {
              Text(
                text = detail,
                color = NbgAgentColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        confirmation.body.takeIf { it.isNotBlank() }?.let { body ->
          Text(
            text = body,
            color = NbgAgentColors.TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
        confirmation.recoveryHint.takeIf { it.isNotBlank() }?.let { hint ->
          Text(
            text = hint,
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (pending) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            NbgConfirmationActionButton(
              label = confirmation.rejectLabel,
              icon = Icons.Filled.Block,
              primary = false,
              modifier = Modifier.weight(1f),
              onClick = onReject,
            )
            NbgConfirmationActionButton(
              label = confirmation.confirmLabel,
              icon = Icons.Filled.CheckCircle,
              primary = true,
              modifier = Modifier.weight(1f),
              onClick = onConfirm,
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun NbgTodoBar(
  todos: List<HanakoTodoItem>,
  streaming: Boolean,
  onCompleteTodos: () -> Unit,
) {
  if (todos.isEmpty()) return
  var expanded by remember { mutableStateOf(false) }
  val todoToolStatus = remember(todos) { nbgTodoListToolStatus(todos) }
  val completedCount = todos.count { nbgTodoStatusCompleted(it.status) }
  val preview = todoToolStatus?.title?.takeIf { it.isNotBlank() } ?: nbgTodoPreview(todos)
  val todoSemantics = listOf("任务清单", preview, todoToolStatus?.subtitle.orEmpty())
    .filter { it.isNotBlank() }
    .distinct()
    .joinToString(" ")
  Surface(color = Color.Transparent) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 4.dp),
      shape = RoundedCornerShape(16.dp),
      color = NbgAgentColors.SurfaceLow,
      border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (expanded) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            todos.take(8).forEach { todo ->
              NbgTodoRow(todo)
            }
            if (todos.size > 8) {
              Text(
                text = "还有 ${todos.size - 8} 项",
                color = NbgAgentColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 34.dp),
              )
            }
            Surface(
              onClick = onCompleteTodos,
              enabled = !streaming,
              modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
              shape = RoundedCornerShape(12.dp),
              color = if (streaming) NbgAgentColors.SendDisabled else NbgAgentColors.PrimaryContainer,
              contentColor = if (streaming) NbgAgentColors.TextDisabled else NbgAgentColors.TextStrong,
            ) {
              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                  text = if (streaming) "生成中不能完成清单" else "完成清单",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
        Surface(
          onClick = { expanded = !expanded },
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics { contentDescription = todoSemantics },
          shape = RoundedCornerShape(12.dp),
          color = NbgAgentColors.Drawer,
          contentColor = NbgAgentColors.TextStrong,
        ) {
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
          ) {
            Box(
              modifier = Modifier
                .size(28.dp)
                .background(NbgAgentColors.PrimarySoft, CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = NbgAgentColors.Primary,
                modifier = Modifier.size(16.dp),
              )
            }
            Text(
              text = preview,
              color = NbgAgentColors.TextStrong,
              fontSize = 13.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = "$completedCount/${todos.size}",
              color = NbgAgentColors.TextMuted,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun NbgTodoRow(todo: HanakoTodoItem) {
  val completed = nbgTodoStatusCompleted(todo.status)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 34.dp)
      .padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier = Modifier
        .size(24.dp)
        .background(
          color = when (todo.status) {
            "in_progress" -> NbgAgentColors.PrimarySoft
            "completed" -> NbgAgentColors.PrimaryContainer
            else -> NbgAgentColors.Drawer
          },
          shape = CircleShape,
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = nbgTodoIcon(todo.status),
        contentDescription = null,
        tint = when (todo.status) {
          "in_progress" -> NbgAgentColors.Primary
          "completed" -> NbgAgentColors.Primary
          else -> NbgAgentColors.TextMuted
        },
        modifier = Modifier.size(15.dp),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = nbgTodoDisplayText(todo),
        color = if (completed) NbgAgentColors.TextMuted else NbgAgentColors.TextStrong,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = nbgTodoStatusLabel(todo.status),
        color = NbgAgentColors.TextMuted,
        fontSize = 10.sp,
        maxLines = 1,
      )
    }
  }
}

internal fun nbgTodoDisplayText(todo: HanakoTodoItem): String =
  if (todo.status == "in_progress" && todo.activeForm.isNotBlank()) {
    todo.activeForm
  } else {
    todo.content.ifBlank { todo.activeForm }
  }

internal fun nbgTodoPreview(todos: List<HanakoTodoItem>): String {
  val current = todos.firstOrNull { it.status == "in_progress" }
    ?: todos.firstOrNull { it.status == "pending" }
    ?: todos.firstOrNull()
  return current?.let { nbgTodoDisplayText(it) } ?: "任务清单"
}

internal fun nbgTodoIcon(status: String): ImageVector =
  when (status) {
    "in_progress" -> Icons.Filled.AutoAwesome
    "completed" -> Icons.Filled.CheckCircle
    else -> Icons.Filled.ContentCopy
  }

internal fun nbgTodoStatusLabel(status: String): String =
  when (status) {
    "in_progress" -> "进行中"
    "completed" -> "已完成"
    else -> "待处理"
  }

@Composable
internal fun NbgConfirmationActionButton(
  label: String,
  icon: ImageVector,
  primary: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.height(44.dp),
    shape = RoundedCornerShape(13.dp),
    color = if (primary) NbgAgentColors.PrimaryContainer else NbgAgentColors.Drawer,
    contentColor = NbgAgentColors.TextStrong,
    border = BorderStroke(1.dp, if (primary) NbgAgentColors.SurfaceBorder else NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
      Spacer(Modifier.width(6.dp))
      Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
internal fun NbgChatTopBar(
  title: String,
  runStatus: NbgChatRunStatus,
  onOpenDrawer: () -> Unit,
  onNewConversation: () -> Unit,
  onReplayLatestTurn: () -> Unit,
  onRequestRevertLatestTurn: () -> Unit,
  onCompressFork: () -> Unit,
  compressing: Boolean,
  compressionAvailable: Boolean,
  streaming: Boolean,
) {
  var menuOpen by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(NbgAgentColors.TopBar),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(68.dp)
        .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      NbgPlainIconButton(
        icon = Icons.Filled.Menu,
        contentDescription = "打开会话抽屉",
        onClick = onOpenDrawer,
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(
          text = title,
          color = NbgAgentColors.TextStrong,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        NbgRunStatusLine(runStatus)
      }
      NbgPlainIconButton(
        icon = Icons.Filled.MoreHoriz,
        contentDescription = "对话选项",
        onClick = { menuOpen = true },
      )
      NbgPlainIconButton(
        icon = HugeIcons.Add01,
        contentDescription = "新建对话",
        onClick = onNewConversation,
      )
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(NbgAgentColors.Divider),
    )
  }
  NbgChatActionsSheet(
    expanded = menuOpen,
    compressing = compressing,
    compressionAvailable = compressionAvailable,
    streaming = streaming,
    onDismiss = { menuOpen = false },
    onCompressFork = {
      menuOpen = false
      onCompressFork()
    },
    onReplayLatestTurn = {
      menuOpen = false
      onReplayLatestTurn()
    },
    onRequestRevertLatestTurn = {
      menuOpen = false
      onRequestRevertLatestTurn()
    },
  )
}

@Composable
private fun NbgChatActionsSheet(
  expanded: Boolean,
  compressing: Boolean,
  compressionAvailable: Boolean,
  streaming: Boolean,
  onDismiss: () -> Unit,
  onCompressFork: () -> Unit,
  onReplayLatestTurn: () -> Unit,
  onRequestRevertLatestTurn: () -> Unit,
) {
  val canCompress = compressionAvailable && !streaming && !compressing
  NbgAgentBottomSheet(
    expanded = expanded,
    title = "更多操作",
    subtitle = "这些操作会影响当前对话 turn、上下文和恢复路径。",
    contentDescription = "对话更多操作抽屉",
    onDismiss = onDismiss,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      NbgChatActionCard(
        mark = "ctx",
        title = when {
          compressing -> "正在压缩上下文"
          compressionAvailable -> "压缩上下文"
          else -> "压缩上下文不可用"
        },
        subtitle = when {
          compressing -> "正在创建压缩后的会话分支。"
          compressionAvailable -> "压缩当前上下文并创建可继续的新会话。"
          else -> "当前会话没有可压缩内容，或后端未开放压缩能力。"
        },
        meta = if (canCompress) "ready" else "idle",
        enabled = canCompress,
        primary = canCompress,
        onClick = onCompressFork,
      )
      NbgChatActionCard(
        mark = "↻",
        title = "重新生成",
        subtitle = if (streaming) "当前正在输出，结束后再重新生成。" else "重放最新一轮用户输入，生成新的助手回复。",
        meta = "turn",
        enabled = !streaming,
        warning = true,
        onClick = onReplayLatestTurn,
      )
      NbgChatActionCard(
        mark = "⌫",
        title = "撤回上一轮",
        subtitle = if (streaming) "当前正在输出，结束后再撤回。" else "回退最新 turn，危险操作会二次确认。",
        meta = "safe",
        enabled = !streaming,
        danger = true,
        onClick = onRequestRevertLatestTurn,
      )
    }
  }
}

@Composable
private fun NbgChatActionCard(
  mark: String,
  title: String,
  subtitle: String,
  meta: String,
  enabled: Boolean,
  primary: Boolean = false,
  warning: Boolean = false,
  danger: Boolean = false,
  onClick: () -> Unit,
) {
  NbgPressBox(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer { alpha = if (enabled) 1f else 0.62f }
      .semantics { contentDescription = title },
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(NbgAgentColors.SurfaceContainer, RoundedCornerShape(16.dp))
        .padding(9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      NbgSheetMonogram(
        text = mark,
        primary = primary,
        warning = warning,
        danger = danger,
      )
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
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      NbgSheetTag(
        text = meta,
        primary = primary,
        warning = warning,
        danger = danger,
      )
    }
  }
}

@Composable
internal fun NbgRunStatusLine(status: NbgChatRunStatus) {
  val targetDotColor = nbgRunStatusDotColor(status)
  val dotColor by animateColorAsState(
    targetValue = targetDotColor,
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "run status dot color",
  )
  val textColor by animateColorAsState(
    targetValue = if (status.warning) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "run status text color",
  )
  val activeAlpha = if (status.active) {
    val transition = rememberInfiniteTransition(label = "nbg run status")
    val pulse by transition.animateFloat(
      initialValue = 0.5f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = nbgMotionDuration(980), easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "nbg run status alpha pulse",
    )
    pulse
  } else {
    0f
  }
  val idleAlpha by animateFloatAsState(
    targetValue = if (status.warning) 0.96f else 0.9f,
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "run status idle alpha",
  )
  val dotAlpha = if (status.active) activeAlpha else idleAlpha
  val text = listOfNotNull(status.label, status.detail?.takeIf { it.isNotBlank() }).joinToString(" · ")
  Row(
    modifier = Modifier
      .background(NbgAgentColors.StatusChip, RoundedCornerShape(999.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .semantics { contentDescription = "运行状态 $text" },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Box(
      modifier = Modifier
        .size(6.dp)
        .graphicsLayer {
          alpha = dotAlpha
        }
        .background(dotColor, CircleShape),
    )
    AnimatedContent(
      targetState = text,
      transitionSpec = {
        fadeIn(animationSpec = tween(nbgMotionDuration(120), easing = FastOutSlowInEasing)) +
          slideInVertically(
            animationSpec = tween(nbgMotionDuration(160), easing = FastOutSlowInEasing),
            initialOffsetY = { it / 6 },
          ) togetherWith
          fadeOut(animationSpec = tween(nbgMotionDuration(90), easing = FastOutSlowInEasing)) +
          slideOutVertically(
            animationSpec = tween(nbgMotionDuration(120), easing = FastOutSlowInEasing),
            targetOffsetY = { -it / 6 },
          )
      },
      label = "run status text",
    ) { targetText ->
      Text(
        text = targetText,
        color = textColor,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

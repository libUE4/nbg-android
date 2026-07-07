package com.nbg.android

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
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
fun NbgAndroidShell(
  onAppearanceChanged: (NbgChatPreferences) -> Unit = {},
  initialPageName: String? = null,
  gatewayInboxRefreshToken: Long = 0L,
  terminalContent: @Composable (onBack: () -> Unit) -> Unit,
) {
  val initialPage = remember(initialPageName) { nbgInitialShellPage(initialPageName) }
  val shellState = remember { NbgAgentShellState(initialPage) }
  val page = shellState.page
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val historyState = remember { NbgAgentHistoryState() }
  val selectedConversationPath by remember { derivedStateOf { historyState.selectedConversationPath } }
  val requestedConversationPath by remember { derivedStateOf { historyState.requestedConversationPath } }
  val deferredHistoryLoaded by remember { derivedStateOf { historyState.deferredHistoryLoaded } }
  val historyRenderVersion by remember { derivedStateOf { historyState.historyRenderVersion } }
  val confirmationState = remember { NbgAgentConfirmationState() }
  val fileShareUiState = remember { NbgAgentFileShareState() }
  val diagnosticsExportState = remember { NbgAgentDiagnosticsExportState() }
  val modelConfigState = remember { NbgAgentModelConfigState() }
  val context = LocalContext.current
  val apiStore = remember(context) { NbgApiStore(context) }
  val chatPreferenceStore = remember(context) { NbgChatPreferenceStore(context) }
  val petStore = remember(context) { NbgPetStore(context) }
  val petDexClient = remember(context) { NbgPetDexClient(nbgPetDexManifestProvenanceDir(context.filesDir)) }
  val apiClient = remember { NbgUpstreamApiClient() }
  val clipboard = LocalClipboardManager.current
  val chatPreferenceState = remember { NbgAgentChatPreferenceState() }
  val chatPreferences = chatPreferenceState.preferences
  val chatPreferencesLoaded = chatPreferenceState.loaded
  val urlApiEntriesState = remember { NbgAgentUrlApiEntriesState() }
  val savedApis = urlApiEntriesState.entries
  val savedApisLoaded = urlApiEntriesState.loaded
  val apiEditor = rememberNbgAgentApiEditorState()
  val expertReviewState = remember { NbgAgentExpertReviewState() }
  val skillTranslationState = remember { NbgAgentSkillTranslationState() }
  val petUiState = remember { NbgAgentPetState(petStore.loadState()) }
  val messageState = remember { NbgAgentMessageState() }
  val messages = messageState.messages
  val contentBlockPatchState = remember { NbgAgentContentBlockPatchState() }
  val streamingTextState = remember { NbgAgentStreamingTextState() }
  val toolStatusState = remember { NbgAgentToolStatusState() }
  val streamingControllerState = remember { NbgAgentStreamingControllerState() }
  val flushSchedulerState = remember { NbgAgentFlushSchedulerState() }
  val todoState = remember { NbgAgentTodoState() }
  val userMessageDedupState = remember { NbgAgentUserMessageDedupState() }
  fun loadSavedApis() {
    urlApiEntriesState.applyLoaded(apiStore.load())
  }
  fun loadChatPreferences() {
    chatPreferenceState.applyLoaded(chatPreferenceStore.load())
  }
  fun savePreferredModel(model: HanakoModelSummary) {
    chatPreferenceState.applySaved(chatPreferenceStore.saveModel(model.provider, model.id, model.label))
  }
  fun savePreferredPermissionMode(mode: String) {
    chatPreferenceState.applySaved(chatPreferenceStore.savePermissionMode(mode))
  }
  fun savePreferredThinkingLevel(level: String) {
    chatPreferenceState.applySaved(chatPreferenceStore.saveThinkingLevel(level))
  }
  fun savePreferredTheme(themeId: String) {
    val saved = chatPreferenceStore.saveTheme(themeId)
    chatPreferenceState.applySaved(saved)
    onAppearanceChanged(saved)
  }
  fun savePreferredFont(fontId: String) {
    val saved = chatPreferenceStore.saveFont(fontId)
    chatPreferenceState.applySaved(saved)
    onAppearanceChanged(saved)
  }
  fun saveMultiAgentEnabled(enabled: Boolean) {
    chatPreferenceState.applySaved(chatPreferenceStore.saveMultiAgentEnabled(enabled))
  }
  fun saveToolsetEnabled(id: NbgToolsetId, enabled: Boolean) {
    chatPreferenceState.applySaved(chatPreferenceStore.saveToolsetEnabled(id, enabled))
  }
  fun clearPreferredModel() {
    chatPreferenceState.applySaved(
      chatPreferenceStore.save(
        chatPreferences.copy(modelProvider = "", modelId = "", modelLabel = ""),
      ),
    )
  }
  fun openApiEditor(entry: NbgStoredApi? = null) {
    apiEditor.open(entry)
  }
  fun closeApiEditor() {
    apiEditor.close()
  }
  fun clearMessages() = messageState.clearMessages()
  fun appendMessage(message: NbgAgentMessage): Long = messageState.appendMessage(message)
  fun replaceMessages(nextMessages: List<NbgAgentMessage>) = messageState.replaceMessages(nextMessages)
  fun findMessageIndexById(messageId: Long): Int = messageState.findMessageIndexById(messageId)
  fun messageExistsById(messageId: Long): Boolean = messageState.messageExistsById(messageId)
  fun setMessageAt(index: Int, message: NbgAgentMessage) = messageState.setMessageAt(index, message)
  fun removeMessageAt(index: Int) = messageState.removeMessageAt(index)
  fun removeMessagesMatching(predicate: (NbgAgentMessage) -> Boolean) = messageState.removeMessagesMatching(predicate)
  fun replaceAllMessages(transform: (NbgAgentMessage) -> NbgAgentMessage) = messageState.replaceAllMessages(transform)
  fun findToolMessageIndexByKey(key: String): Int = messageState.findToolMessageIndexByKey(key)
  fun clearConversationUi() {
    streamingTextState.clear()
    toolStatusState.clear()
    historyState.clearDeferredHistoryLoaded()
    streamingControllerState.markIdle()
    contentBlockPatchState.clear()
    flushSchedulerState.cancelAll()
    clearMessages()
    todoState.clear()
    confirmationState.clearPendingConfirmation()
    messageState.resetLocalMessageIds()
    userMessageDedupState.clear()
    historyState.resetHistoryRenderVersion()
  }
  fun nextLocalMessageId(): Long = messageState.nextLocalMessageId()
  fun appendLocalMessage(role: NbgAgentRole, text: String): Long {
    val id = nextLocalMessageId()
    return appendMessage(NbgAgentMessage(id, role, text))
  }
  fun appendSystemMessage(text: String): Long {
    historyState.appendDeferredSystemMessageIfNeeded(text)
    return appendLocalMessage(NbgAgentRole.System, text)
  }
  fun clearDeferredHistoryLoaded() {
    historyState.clearDeferredHistoryLoaded()
  }
  fun appendUserMessage(text: String): Long? {
    val prompt = nbgDisplayTextForRole("user", text).trim()
    if (prompt.isBlank()) return null
    val now = System.currentTimeMillis()
    if (!userMessageDedupState.shouldAccept(prompt, now)) return null
    return appendLocalMessage(NbgAgentRole.User, prompt)
  }
  fun appendContentBlock(block: HanakoContentBlock): Long {
    val id = nextLocalMessageId()
    val patched = contentBlockPatchState.consumePatchFor(block)
    appendMessage(NbgAgentMessage(id, NbgAgentRole.ContentBlock, patched.title, contentBlock = patched))
    return id
  }
  fun appendExternalUserMessage(message: HanakoExternalUserMessage): Long? {
    val text = message.toDisplayText()
    return appendUserMessage(text)
  }
  fun patchContentBlock(taskId: String, patch: HanakoContentBlockPatch) {
    val index = messages.indexOfFirst { message ->
      message.role == NbgAgentRole.ContentBlock && message.contentBlock?.taskId == taskId
    }
    if (index >= 0) {
      val current = messages[index]
      val nextBlock = current.contentBlock?.applyPatch(patch) ?: return
      setMessageAt(index, current.copy(text = nextBlock.title, contentBlock = nextBlock))
    } else {
      contentBlockPatchState.enqueue(taskId, patch)
    }
  }
  fun appendAssistantVisibleText(messageId: Long, rawDelta: String): String {
    return streamingTextState.appendAssistantVisibleText(messageId, rawDelta)
  }
  fun replaceAssistantVisibleText(messageId: Long, rawText: String): String {
    return streamingTextState.replaceAssistantVisibleText(messageId, rawText)
  }
  fun appendThinkingVisibleText(thinkingId: Long, rawDelta: String): String {
    return streamingTextState.appendThinkingVisibleText(thinkingId, rawDelta)
  }
  fun replaceThinkingVisibleText(thinkingId: Long, rawText: String): String {
    return streamingTextState.replaceThinkingVisibleText(thinkingId, rawText)
  }
  fun replaceHistory(history: List<HanakoHistoryMessage>) {
    flushSchedulerState.cancelStreamingDeltaFlush()
    flushSchedulerState.cancelToolStatusFlush()
    streamingTextState.clear()
    toolStatusState.clear()
    contentBlockPatchState.clear()
    userMessageDedupState.clear()
    val restoredMessages = buildList {
      history.forEachIndexed { historyIndex, message ->
        val uiId = HANA_MOBILE_HISTORY_MESSAGE_ID_BASE + historyIndex.toLong()
        val tool = message.toolStatus
        if (tool != null || message.role == "tool") {
          val key = tool?.key?.takeIf { it.isNotBlank() } ?: "history-tool-${message.id}"
          val restoredTool = tool?.asRestoredHistoryToolStatus(key)
          add(
            NbgAgentMessage(
              id = uiId,
              role = NbgAgentRole.Tool,
              text = restoredTool?.title ?: message.text,
              done = true,
              toolStatus = restoredTool,
            ),
          )
          return@forEachIndexed
        }
        val block = message.contentBlock
        if (block != null || message.role == "content_block") {
          add(
            NbgAgentMessage(
              id = uiId,
              role = NbgAgentRole.ContentBlock,
              text = block?.title ?: message.text,
              contentBlock = block,
            ),
          )
          return@forEachIndexed
        }
        if (message.role == "thinking") {
          val thinkingText = nbgDisplayTextForRole("thinking", message.text)
          add(
            NbgAgentMessage(
              id = uiId,
              role = NbgAgentRole.Thinking,
              text = thinkingText,
              textChunks = streamingTextChunksFor(thinkingText).takeIf { it.size > 1 },
              done = true,
            ),
          )
          return@forEachIndexed
        }
        val role = when (message.role) {
          "user" -> NbgAgentRole.User
          "system" -> NbgAgentRole.System
          else -> NbgAgentRole.Assistant
        }
        val displayText = nbgDisplayTextForRole(message.role, message.text)
        add(
          NbgAgentMessage(
            id = uiId,
            role = role,
            text = displayText,
            textChunks = if (role == NbgAgentRole.Assistant || role == NbgAgentRole.System) {
              streamingTextChunksFor(displayText).takeIf { it.size > 1 }
            } else {
              null
            },
          ),
        )
      }
    }
    replaceMessages(restoredMessages)
    messageState.reseedLocalMessageIds(restoredMessages)
    historyState.markHistoryRendered()
  }
  fun applyHistoryLoaded(event: HanakoChatEvent.HistoryLoaded) {
    val deferredSystemMessages = historyState.beginApplyingHistory(event)
    replaceHistory(event.messages)
    deferredSystemMessages.forEach { appendLocalMessage(NbgAgentRole.System, it) }
    todoState.replace(event.todos)
  }
  fun shouldAcceptHistoryLoaded(event: HanakoChatEvent.HistoryLoaded): Boolean {
    when (val decision = historyState.shouldAcceptHistoryLoaded(event)) {
      NbgHistoryAcceptDecision.Accept -> {
        return true
      }
      NbgHistoryAcceptDecision.RejectSuppressed -> {
        Log.i("NBG_HANAKO", "ignore suppressed UI history for ${event.sessionPath}")
        return false
      }
      is NbgHistoryAcceptDecision.RejectStaleRequested -> {
        Log.i("NBG_HANAKO", "ignore stale UI history for ${event.sessionPath}; requested=${decision.requestedPath}")
        return false
      }
      is NbgHistoryAcceptDecision.RejectStaleActive -> {
        Log.i("NBG_HANAKO", "ignore stale UI history for ${event.sessionPath}; active=${decision.activePath}")
        return false
      }
    }
  }
  fun applyDeferredHistoryIfStillCurrent() {
    val deferred = deferredHistoryLoaded ?: return
    if (shouldAcceptHistoryLoaded(deferred)) applyHistoryLoaded(deferred)
  }
  fun hasLiveUiWork(): Boolean =
    streamingControllerState.streaming ||
      messages.any { message ->
        message.streaming ||
          (message.role == NbgAgentRole.Tool && message.toolStatus?.running == true)
      } ||
      streamingTextState.hasPendingDeltas ||
      toolStatusState.hasPending
  fun applyDeferredHistoryIfIdle() {
    if (!hasLiveUiWork()) applyDeferredHistoryIfStillCurrent()
  }
  fun upsertAssistantDelta(messageId: Long, delta: String) {
    if (delta.isEmpty()) return
    val displayText = appendAssistantVisibleText(messageId, delta)
    val index = findMessageIndexById(messageId)
    if (index >= 0) {
      val current = messages[index]
      setMessageAt(index, current.copy(
        text = displayText,
        textChunks = appendStreamingTextChunksAfterPrefix(current.textChunks, current.text, displayText).takeIf { it.size > 1 },
      ))
    } else {
      appendMessage(NbgAgentMessage(
        messageId,
        NbgAgentRole.Assistant,
        displayText,
        textChunks = streamingTextChunksFor(displayText).takeIf { it.size > 1 },
      ))
    }
  }
  fun upsertMessageTextDelta(messageId: Long, role: NbgAgentRole, delta: String, done: Boolean? = null) {
    if (delta.isEmpty()) return
    val index = findMessageIndexById(messageId)
    if (index >= 0) {
      val current = messages[index]
      val nextText = when (role) {
        NbgAgentRole.Thinking -> appendThinkingVisibleText(messageId, delta)
        NbgAgentRole.Assistant -> appendAssistantVisibleText(messageId, delta)
        else -> current.text + delta
      }
      setMessageAt(index, current.copy(
        text = nextText,
        done = done ?: current.done,
        textChunks = when (role) {
          NbgAgentRole.Thinking, NbgAgentRole.Assistant ->
            appendStreamingTextChunksAfterPrefix(current.textChunks, current.text, nextText).takeIf { it.size > 1 }
          else -> appendStreamingTextChunk(current.text, delta)
        },
        streaming = done == false || current.streaming || role == NbgAgentRole.Assistant,
      ))
    } else {
      val displayText = when (role) {
        NbgAgentRole.Thinking -> appendThinkingVisibleText(messageId, delta)
        NbgAgentRole.Assistant -> appendAssistantVisibleText(messageId, delta)
        else -> delta
      }
      appendMessage(NbgAgentMessage(
        messageId,
        role,
        displayText,
        done = done ?: false,
        textChunks = streamingTextChunksFor(displayText).takeIf { it.size > 1 },
        streaming = done == false || role == NbgAgentRole.Assistant,
      ))
    }
  }
  fun flushStreamingDeltas() {
    val deltas = streamingTextState.drainPendingDeltas()
    deltas.assistantDeltas.forEach { (messageId, delta) ->
      upsertMessageTextDelta(messageId, NbgAgentRole.Assistant, delta)
    }
    deltas.thinkingDeltas.forEach { (thinkingId, delta) ->
      upsertMessageTextDelta(thinkingId, NbgAgentRole.Thinking, delta, done = false)
    }
    flushSchedulerState.completeStreamingDeltaFlush()
    applyDeferredHistoryIfIdle()
  }
  fun pruneFinishedAssistantRawTexts() {
    val streamingAssistantIds = messages
      .asSequence()
      .filter { it.role == NbgAgentRole.Assistant && it.streaming }
      .map { it.id }
      .toSet()
    streamingTextState.pruneFinishedAssistantRawTexts(streamingAssistantIds)
  }
  fun scheduleStreamingDeltaFlush() {
    flushSchedulerState.scheduleStreamingDeltaFlush {
      scope.launch {
        delay(HANA_MOBILE_STREAM_DELTA_FLUSH_MS)
        flushStreamingDeltas()
      }
    }
  }
  fun enqueueAssistantDelta(messageId: Long, delta: String) {
    if (!streamingTextState.enqueueAssistantDelta(messageId, delta)) return
    scheduleStreamingDeltaFlush()
  }
  fun upsertAssistantText(messageId: Long, text: String) {
    streamingTextState.removePendingAssistantDelta(messageId)
    val displayText = replaceAssistantVisibleText(messageId, text)
    val index = findMessageIndexById(messageId)
    if (index >= 0) {
      setMessageAt(index, messages[index].copy(
        text = displayText,
        textChunks = streamingTextChunksFor(displayText).takeIf { it.size > 1 },
        streaming = false,
      ))
    } else {
      appendMessage(NbgAgentMessage(
        messageId,
        NbgAgentRole.Assistant,
        displayText,
        textChunks = streamingTextChunksFor(displayText).takeIf { it.size > 1 },
        streaming = false,
      ))
    }
    applyDeferredHistoryIfIdle()
  }
  fun removeAssistantMessage(messageId: Long) {
    streamingTextState.removePendingAssistantDelta(messageId)
    streamingTextState.removeAssistantRawText(messageId)
    removeMessagesMatching { it.id == messageId && it.role == NbgAgentRole.Assistant }
  }
  fun startThinking(thinkingId: Long) {
    if (!messageExistsById(thinkingId)) {
      appendMessage(NbgAgentMessage(thinkingId, NbgAgentRole.Thinking, "", done = false))
    }
  }
  fun setThinkingText(thinkingId: Long, text: String) {
    val displayText = replaceThinkingVisibleText(thinkingId, text)
    val index = findMessageIndexById(thinkingId)
    if (index >= 0) {
      val current = messages[index]
      setMessageAt(index, current.copy(
        text = displayText,
        done = false,
        textChunks = streamingTextChunksFor(displayText).takeIf { it.size > 1 },
        streaming = true,
      ))
    } else {
      appendMessage(NbgAgentMessage(
        thinkingId,
        NbgAgentRole.Thinking,
        displayText,
        done = false,
        textChunks = streamingTextChunksFor(displayText).takeIf { it.size > 1 },
        streaming = true,
      ))
    }
  }
  fun appendThinkingDelta(thinkingId: Long, delta: String) {
    if (delta.isEmpty()) return
    val nextText = appendThinkingVisibleText(thinkingId, delta)
    val index = findMessageIndexById(thinkingId)
    if (index >= 0) {
      val current = messages[index]
      setMessageAt(index, current.copy(
        text = nextText,
        done = false,
        textChunks = appendStreamingTextChunksAfterPrefix(current.textChunks, current.text, nextText).takeIf { it.size > 1 },
        streaming = true,
      ))
    } else {
      appendMessage(NbgAgentMessage(
        thinkingId,
        NbgAgentRole.Thinking,
        nextText,
        done = false,
        textChunks = streamingTextChunksFor(nextText).takeIf { it.size > 1 },
        streaming = true,
      ))
    }
  }
  fun enqueueThinkingDelta(thinkingId: Long, delta: String) {
    if (!streamingTextState.enqueueThinkingDelta(thinkingId, delta)) return
    scheduleStreamingDeltaFlush()
  }
  fun finishThinking(thinkingId: Long) {
    streamingTextState.removePendingThinkingDelta(thinkingId)?.takeIf { it.isNotBlank() }?.let {
      appendThinkingDelta(thinkingId, it)
    }
    val index = findMessageIndexById(thinkingId)
    if (index >= 0) {
      val current = messages[index]
      if (current.text.isBlank()) {
        removeMessageAt(index)
      } else {
        setMessageAt(index, current.copy(done = true, streaming = false))
      }
    }
    streamingTextState.removeThinkingRawText(thinkingId)
    applyDeferredHistoryIfIdle()
  }
  fun upsertToolStatus(tool: HanakoToolStatus) {
    if (!tool.hasVisibleToolStatus()) return
    val key = tool.key.takeIf { it.isNotBlank() } ?: "${tool.kind}:${tool.title}"
    val index = findToolMessageIndexByKey(key).takeIf { it >= 0 } ?: messages.indexOfFirst { message ->
      val current = message.toolStatus
      message.role == NbgAgentRole.Tool && current != null && current.running && current.canMergeToolStatus(tool)
    }
    val text = listOf(tool.title, tool.subtitle, tool.detail)
      .filter { it.isNotBlank() }
      .joinToString("\n")
    val mergedTool = if (index >= 0) messages[index].toolStatus?.mergeToolStatus(tool) ?: tool else tool
    val next = NbgAgentMessage(
      id = if (index >= 0) messages[index].id else nextLocalMessageId(),
      role = NbgAgentRole.Tool,
      text = text,
      done = !mergedTool.running,
      toolStatus = mergedTool.copy(key = if (index >= 0) mergedTool.key.ifBlank { key } else key),
    )
    if (index >= 0) {
      setMessageAt(index, next)
    } else {
      appendMessage(next)
    }
  }
  fun flushToolStatuses() {
    val updates = toolStatusState.drain()
    updates.forEach(::upsertToolStatus)
    flushSchedulerState.completeToolStatusFlush()
    applyDeferredHistoryIfIdle()
  }

  fun scheduleToolStatusFlush() {
    flushSchedulerState.scheduleToolStatusFlush {
      scope.launch {
        delay(HANA_MOBILE_TOOL_STATUS_FLUSH_MS)
        flushToolStatuses()
      }
    }
  }

  fun enqueueToolStatus(tool: HanakoToolStatus) {
    val result = toolStatusState.enqueue(tool)
    if (!result.accepted) return
    if (result.shouldFlushNow) {
      flushSchedulerState.cancelToolStatusFlush()
      flushToolStatuses()
    } else {
      scheduleToolStatusFlush()
    }
  }
  fun failRunningTools() {
    flushToolStatuses()
    replaceAllMessages { message ->
      val tool = message.toolStatus
      if (message.role == NbgAgentRole.Tool && tool != null && tool.running) {
        val failed = tool.copy(
          subtitle = listOf(tool.subtitle, "已中断").filter { it.isNotBlank() }.distinct().joinToString(" / "),
          status = "failed",
          running = false,
          success = false,
        )
        message.copy(done = true, text = failed.title, toolStatus = failed)
      } else {
        message
      }
    }
  }
  fun finishStreamingMessages() {
    flushSchedulerState.cancelStreamingDeltaFlush()
    flushStreamingDeltas()
    flushSchedulerState.cancelToolStatusFlush()
    flushToolStatuses()
    replaceAllMessages { message ->
      if (message.streaming && (message.role == NbgAgentRole.Assistant || message.role == NbgAgentRole.Thinking)) {
        message.copy(streaming = false, done = if (message.role == NbgAgentRole.Thinking) true else message.done)
      } else if (message.role == NbgAgentRole.Tool && message.toolStatus?.running == true) {
        val tool = message.toolStatus
        val failed = tool.copy(
          subtitle = listOf(tool.subtitle, "未收到结束事件").filter { it.isNotBlank() }.distinct().joinToString(" / "),
          status = "failed",
          running = false,
          success = false,
        )
        message.copy(done = true, text = failed.title, toolStatus = failed)
      } else {
        message
      }
    }
    pruneFinishedAssistantRawTexts()
  }
  fun openFileShareDialog() {
    fileShareUiState.open(NbgFileShareServerRegistry.start(context, NbgFileShareAccessMode.ReadOnly))
  }
  val hanako = remember(context) {
    HanakoChatController(context) { event ->
      when (event) {
        is HanakoChatEvent.UserMessage -> {
          streamingControllerState.markStreaming()
          appendUserMessage(event.text)
        }
        is HanakoChatEvent.ExternalUserMessage -> appendExternalUserMessage(event.message)
        is HanakoChatEvent.AssistantStarted -> {
          streamingControllerState.markStreaming()
          if (!messageExistsById(event.messageId)) {
            appendMessage(NbgAgentMessage(event.messageId, NbgAgentRole.Assistant, ""))
          }
          streamingTextState.ensureAssistantRawText(event.messageId)
        }
        is HanakoChatEvent.AssistantText -> upsertAssistantText(event.messageId, event.text)
        is HanakoChatEvent.AssistantDelta -> {
          streamingControllerState.markStreaming()
          enqueueAssistantDelta(event.messageId, event.delta)
        }
        is HanakoChatEvent.AssistantRemoved -> removeAssistantMessage(event.messageId)
        is HanakoChatEvent.ThinkingStarted -> {
          streamingControllerState.markStreaming()
          startThinking(event.thinkingId)
        }
        is HanakoChatEvent.ThinkingText -> setThinkingText(event.thinkingId, event.text)
        is HanakoChatEvent.ThinkingDelta -> {
          streamingControllerState.markStreaming()
          enqueueThinkingDelta(event.thinkingId, event.delta)
        }
        is HanakoChatEvent.ThinkingEnded -> finishThinking(event.thinkingId)
        is HanakoChatEvent.SystemMessage -> appendSystemMessage(event.text)
        is HanakoChatEvent.ToolStatus -> {
          if (event.tool.running) streamingControllerState.markStreaming()
          enqueueToolStatus(event.tool)
        }
        HanakoChatEvent.ToolInterrupted -> {
          streamingControllerState.markIdle()
          failRunningTools()
          finishStreamingMessages()
          applyDeferredHistoryIfStillCurrent()
        }
        is HanakoChatEvent.ContentBlock -> appendContentBlock(event.block)
        is HanakoChatEvent.ContentBlockPatch -> patchContentBlock(event.taskId, event.patch)
        is HanakoChatEvent.ConfirmationRequested -> confirmationState.requestConfirmation(event.confirmation)
        is HanakoChatEvent.ConfirmationResolved -> {
          val pendingConfirmation = confirmationState.pendingConfirmation?.takeIf { it.confirmId == event.confirmId }
          val auditEntry = event.auditEntry?.takeIf(::nbgVerifyConfirmationResolutionAuditEntry)
            ?: nbgBuildConfirmationResolutionAuditEntry(
            confirmId = event.confirmId,
            action = event.action,
            confirmation = pendingConfirmation,
          )
          enqueueToolStatus(
            nbgConfirmationResolutionToolStatus(
              confirmId = event.confirmId,
              action = event.action,
              confirmation = pendingConfirmation,
              auditEntry = auditEntry,
            ),
          )
          confirmationState.resolveConfirmation(event.confirmId)
        }
        is HanakoChatEvent.AgentModelConfigLoaded -> {
          modelConfigState.applyLoaded(event.config)
        }
        is HanakoChatEvent.AgentModelConfigFailed -> {
          modelConfigState.applyFailed(event.message)
        }
        is HanakoChatEvent.SlashCommandsLoaded -> Unit
        is HanakoChatEvent.SlashCommandsFailed -> Unit
        is HanakoChatEvent.ProvidersLoaded -> Unit
        is HanakoChatEvent.ProvidersFailed -> Unit
        is HanakoChatEvent.ModelHealthLoaded -> Unit
        is HanakoChatEvent.ModelHealthFailed -> Unit
        is HanakoChatEvent.TeamTaskUpdated -> {
          event.task?.let { task ->
            if (task.isActive || task.agents.any { agent -> agent.running }) streamingControllerState.markStreaming()
            enqueueToolStatus(task.nbgTeamTaskToolStatus())
            task.agents.forEach { agent -> enqueueToolStatus(agent.nbgTeamAgentToolStatus()) }
          }
        }
        is HanakoChatEvent.SessionDeleted -> {
          val deletedActiveConversation = historyState.applySessionDeleted(event.sessionPath)
          if (deletedActiveConversation) {
            clearConversationUi()
          }
        }
        is HanakoChatEvent.SessionSelectFailed -> {
          historyState.applySessionSelectFailed(event.sessionPath, event.previousSessionPath)
        }
        is HanakoChatEvent.HistoryLoaded -> {
          if (!shouldAcceptHistoryLoaded(event)) return@HanakoChatController
          val activePath = selectedConversationPath
          if (hasLiveUiWork() && (activePath == null || event.sessionPath == activePath)) {
            historyState.deferHistoryLoaded(event)
            Log.i("NBG_HANAKO", "defer live UI history for ${event.sessionPath}")
          } else {
            applyHistoryLoaded(event)
          }
        }
        is HanakoChatEvent.TodoUpdated -> todoState.replace(event.todos)
        HanakoChatEvent.TurnEnded -> {
          streamingControllerState.markIdle()
          finishStreamingMessages()
          applyDeferredHistoryIfStillCurrent()
        }
      }
    }
  }
  val hanakoState by hanako.state.collectAsState()
  fun applyPreferredPermissionMode(mode: String) {
    val normalizedMode = nbgNormalizePermissionMode(mode)
    savePreferredPermissionMode(normalizedMode)
    hanako.setSessionPermissionMode(normalizedMode)
  }
  fun requestPreferredPermissionMode(mode: String) {
    val normalizedMode = nbgNormalizePermissionMode(mode)
    if (confirmationState.shouldGateOperatePermissionMode(normalizedMode, hanakoState.sessionPath)) {
      confirmationState.requestOperatePermissionWarning(hanakoState.sessionPath)
      return
    }
    applyPreferredPermissionMode(normalizedMode)
  }
  val terminalWorkspace = TerminalWorkspace.shared
  val terminalReadinessVersion by terminalWorkspace.readinessVersion.collectAsState()
  val terminalReadiness = remember(terminalReadinessVersion) {
    terminalWorkspace.terminalDiagnosticsSnapshot()
  }
  LaunchedEffect(hanakoState.streaming) {
    streamingControllerState.syncFromHanako(hanakoState.streaming)
    if (!hanakoState.streaming) applyDeferredHistoryIfIdle()
  }
  LaunchedEffect(page) {
    if (page == NbgShellPage.Mcp) {
      hanako.loadMcpState()
    }
    if (page == NbgShellPage.Memory) {
      hanako.loadMemoryState()
    }
    if (page == NbgShellPage.Learning) {
      hanako.loadAutonomousLearningSnapshot()
      hanako.loadScheduleState()
      hanako.loadGatewayInboxState()
    }
    if (page == NbgShellPage.Skills) {
      hanako.loadSkills()
    }
  }
  LaunchedEffect(gatewayInboxRefreshToken) {
    if (gatewayInboxRefreshToken > 0L) {
      hanako.loadGatewayInboxState()
      shellState.showPage(NbgShellPage.Learning)
    }
  }
  val conversations = remember(hanakoState.sessions) {
    hanakoState.sessions.map {
      NbgAgentConversation(
        path = it.path,
        title = it.title,
        subtitle = it.subtitle,
        snippet = it.snippet,
        matchType = it.matchType,
        pinned = it.pinned,
        hasSummary = it.hasSummary,
      )
    }
  }
  val searchConversations = remember(hanakoState.searchResults) {
    hanakoState.searchResults.map {
      NbgAgentConversation(
        path = it.path,
        title = it.title,
        subtitle = it.subtitle,
        snippet = it.snippet,
        matchType = it.matchType,
        pinned = it.pinned,
        hasSummary = it.hasSummary,
      )
    }
  }
  val urlApiSelectionState = remember { NbgAgentUrlApiSelectionState() }
  val selectedUrlApiModel = urlApiSelectionState.selectedModel
  val restoredDefaultUrlApiModel = urlApiSelectionState.restoredDefaultModel
  val restoredChatPreferences = urlApiSelectionState.restoredChatPreferences
  val urlApiModelSummaries = remember(savedApis, selectedUrlApiModel) { nbgUrlApiModelSummaries(savedApis, selectedUrlApiModel) }
  val activeUrlApiProviderIds = remember(savedApis) { savedApis.map { nbgUrlApiProviderId(it.id) }.toSet() }
  val agentModelConfig = modelConfigState.config
  val mergedAgentModelConfig = remember(agentModelConfig, urlApiModelSummaries, activeUrlApiProviderIds) {
    nbgMergeAgentModelConfig(agentModelConfig, urlApiModelSummaries, activeUrlApiProviderIds)
  }
  val preferredModelName = chatPreferences.modelLabel.ifBlank { chatPreferences.modelId }
  val displayModelName = selectedUrlApiModel?.label
    ?: preferredModelName.ifBlank { hanakoState.modelName.orEmpty() }
  val displayModelSummary = remember(mergedAgentModelConfig, displayModelName, chatPreferences) {
    nbgCurrentModelSummary(mergedAgentModelConfig, displayModelName, chatPreferences)
  }
  val preferredPermissionMode = chatPreferences.permissionMode.ifBlank { hanakoState.permissionMode }
  val displayPermissionMode = if (confirmationState.shouldGateOperatePermissionMode(preferredPermissionMode, hanakoState.sessionPath)) {
    NBG_PERMISSION_MODE_ASK
  } else {
    preferredPermissionMode
  }
  val displayPermissionLabel = hanakoPermissionModeLabel(displayPermissionMode)
  val preferredThinkingLevel = chatPreferences.thinkingLevel.ifBlank { hanakoState.thinkingLevel }
  val displayThinkingLevel = displayModelSummary
    ?.let { nbgCoerceThinkingLevelForModel(preferredThinkingLevel, it) }
    ?: preferredThinkingLevel
  val displayThinkingLabel = hanakoThinkingLevelLabel(displayThinkingLevel)
  val displayRunStatus = nbgChatRunStatusWithDisplayModel(hanakoState, messages, displayModelName)
  fun translateSkillDescription(skill: HanakoSkillSummary) {
    if (skillTranslationState.isTranslating) return
    val selected = nbgSelectedUrlApi(savedApis, selectedUrlApiModel)
    if (selected == null) {
      skillTranslationState.recordNoModel(skill.name)
      return
    }
    if (!skillTranslationState.begin(skill.name)) return
    scope.launch {
      runCatching {
        apiClient.translateSkillDescriptions(selected.first, selected.second, listOf(skill))
      }.onSuccess { result ->
        skillTranslationState.applySuccess(skill.name, result)
      }.onFailure { error ->
        skillTranslationState.applyFailure(skill.name, error)
      }
      skillTranslationState.finish(skill.name)
    }
  }
  fun runExpertReview() {
    val models = expertReviewState.selectedModels(savedApis)
    val prompt = expertReviewState.prompt.trim()
    val review = nbgReviewExpertReviewRequest(models, explicitUserTrigger = true)
    if (!review.allowStart || prompt.isBlank()) {
      expertReviewState.message = if (prompt.isBlank()) "请输入评审问题" else review.reason
      return
    }
    val requestSerial = expertReviewState.nextRequestSerial()
    expertReviewState.running = true
    expertReviewState.message = "正在调用 ${models.size} 个模型..."
    scope.launch {
      runCatching {
        val references = models.map { modelRef ->
          val entry = savedApis.firstOrNull { nbgUrlApiProviderId(it.id) == modelRef.providerId }
          val model = entry?.models?.firstOrNull { it.id == modelRef.modelId }
          if (entry == null || model == null) {
            NbgExpertReviewReferenceOutput(modelRef, ok = false, output = "", error = "模型配置已不存在")
          } else {
            runCatching {
              apiClient.generateReadOnlyText(
                entry = entry,
                model = model,
                prompt = prompt,
              )
            }.fold(
              onSuccess = { result ->
                NbgExpertReviewReferenceOutput(
                  model = modelRef,
                  ok = result.ok,
                  output = result.text,
                  error = if (result.ok) "" else result.message,
                )
              },
              onFailure = { error ->
                NbgExpertReviewReferenceOutput(
                  model = modelRef,
                  ok = false,
                  output = "",
                  error = error.message.orEmpty().ifBlank { error::class.java.simpleName },
                )
              },
            )
          }
        }
        if (!expertReviewState.isCurrent(requestSerial)) return@launch
        expertReviewState.applyResult(nbgBuildExpertReviewRunResult(prompt, models, references))
      }.onFailure { error ->
        if (expertReviewState.isCurrent(requestSerial)) expertReviewState.applyFailure(error)
      }
    }
  }
  LaunchedEffect(savedApis, selectedUrlApiModel) {
    val selected = selectedUrlApiModel ?: return@LaunchedEffect
    val entry = savedApis.firstOrNull { nbgUrlApiProviderId(it.id) == selected.providerId }
    if (entry == null || nbgVerifiedUrlApiModels(entry).none { it.id == selected.modelId }) {
      urlApiSelectionState.clearSelectedModel()
    }
  }
  LaunchedEffect(savedApisLoaded, savedApis, chatPreferencesLoaded, restoredChatPreferences, chatPreferences) {
    if (!chatPreferencesLoaded || !savedApisLoaded) return@LaunchedEffect
    val preferred = chatPreferences.toSelectedUrlApiModel() ?: return@LaunchedEffect
    if (!restoredChatPreferences) return@LaunchedEffect
    val valid = savedApis.any { entry ->
      nbgUrlApiProviderId(entry.id) == preferred.providerId &&
        nbgVerifiedUrlApiModels(entry).any { it.id == preferred.modelId }
    }
    if (!valid) {
      if (selectedUrlApiModel?.providerId == preferred.providerId) urlApiSelectionState.clearSelectedModel()
      clearPreferredModel()
    }
  }
  LaunchedEffect(savedApisLoaded, savedApis, chatPreferencesLoaded, chatPreferences.hasModel) {
    if (!chatPreferencesLoaded || !savedApisLoaded) return@LaunchedEffect
    if (!restoredDefaultUrlApiModel && !chatPreferences.hasModel && savedApis.isNotEmpty() && selectedUrlApiModel == null) {
      urlApiSelectionState.restoreDefaultModel(nbgDefaultSelectedUrlApiModel(savedApis))
    }
  }
  LaunchedEffect(savedApisLoaded, savedApis, chatPreferencesLoaded, chatPreferences) {
    if (!chatPreferencesLoaded) return@LaunchedEffect
    if (!restoredChatPreferences) {
      chatPreferences.toSelectedUrlApiModel()?.let { preferred ->
        if (!savedApisLoaded) return@LaunchedEffect
        if (savedApis.any { nbgUrlApiProviderId(it.id) == preferred.providerId && nbgVerifiedUrlApiModels(it).any { model -> model.id == preferred.modelId } }) {
          urlApiSelectionState.select(preferred)
        } else {
          clearPreferredModel()
        }
      }
      urlApiSelectionState.markChatPreferencesRestored()
    }
  }
  LaunchedEffect(savedApis, selectedUrlApiModel) {
    val selected = nbgSelectedUrlApi(savedApis, selectedUrlApiModel)
    hanako.setDefaultUrlApi(selected?.first, selected?.second)
  }
  LaunchedEffect(savedApisLoaded, savedApis) {
    if (savedApisLoaded) hanako.syncUrlApiProviders(savedApis)
  }
  LaunchedEffect(savedApis) {
    expertReviewState.syncAvailableModels(savedApis)
  }
  LaunchedEffect(agentModelConfig, selectedUrlApiModel, chatPreferencesLoaded, savedApisLoaded, chatPreferences.hasModel) {
    if (chatPreferencesLoaded && savedApisLoaded && selectedUrlApiModel == null && !chatPreferences.hasModel) {
      agentModelConfig?.models.orEmpty().firstOrNull { it.isCurrent }?.let { current ->
        chatPreferenceState.applySaved(chatPreferenceStore.saveModel(current.provider, current.id, current.label))
      }
    }
  }
  LaunchedEffect(chatPreferenceStore) {
    loadChatPreferences()
  }
  LaunchedEffect(apiStore) {
    loadSavedApis()
  }
  LaunchedEffect(
    hanakoState.connected,
    hanakoState.sessionPath,
    hanakoState.permissionMode,
    hanakoState.streaming,
    restoredChatPreferences,
    chatPreferences.permissionMode,
    confirmationState.operatePermissionWarningOpen,
    displayThinkingLevel,
  ) {
    val sessionPath = hanakoState.sessionPath
    if (!hanakoState.connected || sessionPath.isNullOrBlank() || !restoredChatPreferences || hanakoState.streaming) return@LaunchedEffect
    val preferred = chatPreferences
    val preferredMode = nbgNormalizePermissionMode(preferred.permissionMode)
    if (confirmationState.shouldGateOperatePermissionMode(preferredMode, sessionPath)) {
      if (confirmationState.shouldAutoPromptOperatePermissionWarning(preferredMode, sessionPath)) {
        confirmationState.requestOperatePermissionWarning(sessionPath)
      }
      if (hanakoState.permissionMode == NBG_PERMISSION_MODE_OPERATE) {
        hanako.setSessionPermissionMode(NBG_PERMISSION_MODE_ASK)
      }
    } else if (preferredMode != hanakoState.permissionMode) {
      hanako.setSessionPermissionMode(preferredMode)
    }
    if (displayThinkingLevel != hanakoState.thinkingLevel) {
      hanako.setThinkingLevel(displayThinkingLevel)
    }
  }
  LaunchedEffect(hanakoState.sessionPath) {
    historyState.syncSessionPath(hanakoState.sessionPath)
  }
  LaunchedEffect(hanakoState.selectingSessionPath, hanakoState.compressing, hanakoState.sessionPath) {
    historyState.clearPendingNewSessionRequestIfSettled(
      selectingSessionPath = hanakoState.selectingSessionPath,
      compressing = hanakoState.compressing,
      sessionPath = hanakoState.sessionPath,
    )
  }

  LaunchedEffect(hanako) {
    hanako.start()
  }
  DisposableEffect(hanako) {
    onDispose {
      flushSchedulerState.cancelAll()
      streamingTextState.clear()
      toolStatusState.clear()
      hanako.stop()
    }
  }

  BackHandler(enabled = drawerState.isOpen) {
    scope.launch { drawerState.close() }
  }
  BackHandler(enabled = apiEditor.isOpen) {
    closeApiEditor()
  }
  BackHandler(enabled = page != NbgShellPage.Chat && !apiEditor.isOpen && !drawerState.isOpen) {
    shellState.showChat()
  }

  fun createSessionWithSelectedUrlApi() {
    historyState.clearDeferredBeforeNewSession()
    val selected = nbgSelectedUrlApi(savedApis, selectedUrlApiModel)
    if (selected != null) {
      hanako.createSessionWithUrlApi(selected.first, selected.second)
    } else {
      hanako.createSession()
    }
  }

  fun refreshPetDex() {
    if (!petUiState.beginManifestRefresh()) return
    scope.launch {
      runCatching { petDexClient.fetchManifest() }
        .onSuccess { petUiState.applyManifestRefreshSuccess(it) }
        .onFailure { petUiState.applyManifestRefreshFailure(it) }
      petUiState.finishManifestRefresh()
    }
  }

  fun installPet(pet: NbgPetDexManifestPet) {
    if (!petUiState.beginInstall(pet)) return
    scope.launch {
      runCatching { petStore.install(pet) }
        .onSuccess { petUiState.applyInstallSuccess(petStore.loadState()) }
        .onFailure { petUiState.applyInstallFailure(it) }
      petUiState.finishInstall(pet.slug)
    }
  }

  val capabilityRegistry = nbgBuildCapabilityRegistry(
    connected = hanakoState.connected,
    connectionLabel = hanakoState.connectionLabel,
    lastError = hanakoState.lastError.orEmpty(),
    savedUrlApiCount = savedApis.size,
    terminalReadiness = terminalReadiness,
    mcpState = hanakoState.mcpState,
    mcpLoading = hanakoState.mcpLoading,
    mcpError = hanakoState.mcpError,
    skillsSnapshot = hanakoState.skillsSnapshot,
    skillsLoading = hanakoState.skillsLoading,
    skillsError = hanakoState.skillsError,
    memoryState = hanakoState.memoryState,
    memoryLoading = hanakoState.memoryLoading,
    memoryError = hanakoState.memoryError,
    fileShareState = fileShareUiState.serverState,
    petState = petUiState.petState,
    petDexLoading = petUiState.loadingManifest,
    petDexError = petUiState.error,
  )

  fun openDiagnosticsExportDialog() {
    val exportText = nbgBuildDiagnosticsExportJson(
      nbgCreateDiagnosticsExportSnapshot(
        context = context,
        capabilities = capabilityRegistry,
        hanakoState = hanakoState,
        savedApis = savedApis,
        fileShareState = fileShareUiState.serverState ?: NbgFileShareServerRegistry.diagnosticSnapshot(context),
        petState = petUiState.petState,
      ),
    )
    diagnosticsExportState.open(exportText)
  }

  fun copyDiagnosticsExport() {
    clipboard.setText(AnnotatedString(diagnosticsExportState.exportText))
    Toast.makeText(context, "诊断导出已复制", Toast.LENGTH_SHORT).show()
  }

  fun shareDiagnosticsExport() {
    runCatching {
      val sendIntent = nbgBuildDiagnosticsExportShareIntent(context, diagnosticsExportState.exportText)
      context.startActivity(Intent.createChooser(sendIntent, "分享诊断导出"))
    }.onFailure { error ->
      val message = if (error is ActivityNotFoundException) "没有可用的分享目标" else "诊断导出分享失败"
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }

  fun shareTrajectoryExport(bundle: NbgTrajectoryExportBundle?) {
    val exportBundle = bundle ?: run {
      Toast.makeText(context, "请先生成轨迹导出", Toast.LENGTH_SHORT).show()
      return
    }
    if (!exportBundle.review.allowExport) {
      Toast.makeText(context, "轨迹导出未通过本地脱敏检查", Toast.LENGTH_SHORT).show()
      return
    }
    runCatching {
      val sendIntent = nbgBuildTrajectoryExportShareIntent(context, exportBundle)
      context.startActivity(Intent.createChooser(sendIntent, "分享轨迹导出"))
    }.onFailure { error ->
      val message = if (error is ActivityNotFoundException) "没有可用的分享目标" else "轨迹导出分享失败"
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      NbgConversationDrawer(
        conversations = conversations,
        searchResults = searchConversations,
        searching = hanakoState.searching,
        selectedConversationPath = selectedConversationPath,
        connectionLabel = hanakoState.connectionLabel,
        connected = hanakoState.connected,
        runStatus = displayRunStatus,
        contextUsageLabel = hanakoState.contextUsageLabel ?: hanakoState.runtimeStatus.usageLabel,
        mcpState = hanakoState.mcpState,
        skillsSnapshot = hanakoState.skillsSnapshot,
        learningSnapshot = hanakoState.autonomousLearningSnapshot,
        capabilities = capabilityRegistry,
        onSearch = hanako::searchSessions,
        onSelectConversation = {
          historyState.requestConversation(it)
          hanako.selectSession(it)
          shellState.showChat()
          scope.launch { drawerState.close() }
        },
        onNewConversation = {
          historyState.requestNewConversation()
          createSessionWithSelectedUrlApi()
          shellState.showChat()
          scope.launch { drawerState.close() }
        },
        onOpenTerminal = {
          shellState.showPage(NbgShellPage.Terminal)
          scope.launch { drawerState.close() }
        },
        onOpenFileShare = { openFileShareDialog() },
        onOpenAgents = {
          shellState.showPage(NbgShellPage.Agents)
          scope.launch { drawerState.close() }
        },
        onOpenMcp = {
          shellState.showPage(NbgShellPage.Mcp)
          scope.launch { drawerState.close() }
        },
        onOpenLearning = {
          hanako.loadAutonomousLearningSnapshot()
          shellState.showPage(NbgShellPage.Learning)
          scope.launch { drawerState.close() }
        },
        onOpenSkills = {
          shellState.showPage(NbgShellPage.Skills)
          scope.launch { drawerState.close() }
        },
        onOpenPets = {
          shellState.showPage(NbgShellPage.Pets)
          scope.launch { drawerState.close() }
        },
        onOpenDiagnosticsExport = {
          openDiagnosticsExportDialog()
          scope.launch { drawerState.close() }
        },
        onOpenAppearance = {
          shellState.showPage(NbgShellPage.Appearance)
          scope.launch { drawerState.close() }
        },
        onOpenProviders = {
          loadSavedApis()
          shellState.showPage(NbgShellPage.UrlApi)
          scope.launch { drawerState.close() }
        },
        onOpenToolsetsDoctor = {
          shellState.showPage(NbgShellPage.ToolsetsDoctor)
          scope.launch { drawerState.close() }
        },
        onRenameConversation = hanako::renameSession,
        onDeleteConversation = { path ->
          hanako.deleteSession(path)
        },
        onPinConversation = hanako::setSessionPinned,
      )
    },
  ) {
    when (page) {
      NbgShellPage.Chat -> NbgAgentChatScreen(
        title = conversations.firstOrNull { it.path == selectedConversationPath }?.title ?: "新聊天",
        conversationPath = selectedConversationPath ?: hanakoState.sessionPath,
        runStatus = displayRunStatus,
        petState = petUiState.petState,
        runtimeStatus = hanakoState.runtimeStatus,
        messages = messages,
        historyRenderVersion = historyRenderVersion,
        todos = todoState.todos,
        agentModelConfig = mergedAgentModelConfig,
        agentModelConfigLoading = modelConfigState.loading,
        agentModelConfigError = modelConfigState.error,
        modelName = displayModelName,
        preferredModel = chatPreferences,
        permissionMode = displayPermissionMode,
        permissionModeLabel = displayPermissionLabel,
        thinkingLevel = displayThinkingLevel,
        thinkingLevelLabel = displayThinkingLabel,
        confirmation = confirmationState.pendingConfirmation,
        streaming = hanakoState.streaming,
        compressing = hanakoState.compressing,
        compressionAvailable = hanakoState.compressionAvailable,
        sendBlocked = hanakoState.selectingSessionPath != null,
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onOpenPets = { shellState.showPage(NbgShellPage.Pets) },
        onHidePet = { petUiState.applyStoreState(petStore.setHidden(true)) },
        onNewConversation = {
          historyState.requestNewConversation()
          createSessionWithSelectedUrlApi()
        },
        onLoadAgentModelConfig = {
          modelConfigState.beginLoad()
          hanako.loadAgentModelConfig()
        },
        onSwitchComposerModel = { model ->
          if (model.provider.startsWith("urlapi-")) {
            val entry = savedApis.firstOrNull { nbgUrlApiProviderId(it.id) == model.provider }
            val apiModel = entry?.models?.firstOrNull { it.id == model.id }
            if (entry != null && apiModel != null) {
              val next = entry.copy(selectedModelId = apiModel.id)
              urlApiEntriesState.applySaved(apiStore.save(next))
              urlApiSelectionState.select(NbgSelectedUrlApiModel(model.provider, apiModel.id, apiModel.label))
              savePreferredModel(model)
              modelConfigState.beginLoad()
              hanako.switchUrlApiModel(next, apiModel)
            } else {
              modelConfigState.recordLocalError("没有找到已保存的网址 API 模型")
            }
          } else {
            urlApiSelectionState.clearSelectedModel()
            savePreferredModel(model)
            modelConfigState.beginLoad()
            hanako.switchModel(model)
          }
        },
        onSetComposerPermissionMode = { mode ->
          requestPreferredPermissionMode(mode)
        },
        onSetComposerThinkingLevel = { level ->
          savePreferredThinkingLevel(level)
          hanako.setThinkingLevel(level)
        },
        onReplayLatestTurn = hanako::replayLatestTurn,
        onRequestRevertLatestTurn = { confirmationState.openRevertTurnConfirm() },
        onCompressFork = {
          historyState.requestNewConversation()
          hanako.compressForkSession()
        },
        onCompleteTodos = hanako::completeTodos,
        onSend = { prompt ->
          val selected = nbgSelectedUrlApi(savedApis, selectedUrlApiModel)
          if (selected != null) {
            hanako.sendPromptWithUrlApi(prompt, selected.first, selected.second, displayText = prompt)
          } else {
            hanako.sendPrompt(prompt, displayText = prompt)
          }
        },
        onAbort = hanako::abort,
        onInterruptTerminal = hanako::interruptTerminalByHuman,
        onResolveConfirmation = hanako::resolveConfirmation,
      )
      NbgShellPage.Agents -> NbgAgentsScreen(
        runStatus = displayRunStatus,
        mcpState = hanakoState.mcpState,
        skillsSnapshot = hanakoState.skillsSnapshot,
        memoryState = hanakoState.memoryState,
        learningSnapshot = hanakoState.autonomousLearningSnapshot,
        capabilities = capabilityRegistry,
        teamTask = hanakoState.teamTask,
        multiAgentEnabled = chatPreferences.multiAgentEnabled,
        onBack = { shellState.showChat() },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onSetMultiAgentEnabled = ::saveMultiAgentEnabled,
        onAbortTeamTask = { hanako.abortTeamTask() },
        onAbortTeamAgent = { taskId, agentId -> hanako.abortTeamAgent(taskId, agentId) },
        onOpenMcp = { shellState.showPage(NbgShellPage.Mcp) },
        onOpenLearning = { shellState.showPage(NbgShellPage.Learning) },
        onOpenSkills = { shellState.showPage(NbgShellPage.Skills) },
        onOpenMemory = { shellState.showPage(NbgShellPage.Memory) },
      )
      NbgShellPage.Terminal -> terminalContent { shellState.showChat() }
      NbgShellPage.Mcp -> NbgMcpScreen(
        state = hanakoState.mcpState,
        loading = hanakoState.mcpLoading,
        error = hanakoState.mcpError,
        busyKey = hanakoState.mcpBusyKey,
        onBack = { shellState.showChat() },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onReload = { hanako.loadMcpState() },
        onSetEnabled = { hanako.setMcpEnabled(it) },
        onAddConnector = { hanako.addMcpConnector(it) },
        onConnectorAction = { connectorId, action -> hanako.runMcpConnectorAction(connectorId, action) },
        onDeleteConnector = { connectorId -> hanako.deleteMcpConnector(connectorId) },
        onSetAgentConnector = { connectorId, enabled -> hanako.setAgentMcpConnector(connectorId, enabled) },
        onSetAgentTool = { connectorId, toolName, enabled -> hanako.setAgentMcpTool(connectorId, toolName, enabled) },
      )
      NbgShellPage.Memory -> NbgMemoryScreen(
        state = hanakoState.memoryState,
        loading = hanakoState.memoryLoading,
        error = hanakoState.memoryError,
        busyKey = hanakoState.memoryBusyKey,
        onBack = { shellState.showChat() },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onReload = { query, type -> hanako.loadMemoryState(query, type) },
        onSave = { input, query, type -> hanako.saveMemoryItem(input, query, type) },
        onDelete = { id, query, type -> hanako.deleteMemoryItem(id, query, type) },
      )
      NbgShellPage.Learning -> NbgAutonomousLearningScreen(
        snapshot = hanakoState.autonomousLearningSnapshot,
        scheduleState = hanakoState.scheduleState,
        gatewayInboxState = hanakoState.gatewayInboxState,
        externalMemoryProviderState = hanakoState.externalMemoryProviderState,
        contextInsights = hanakoState.contextInsights,
        trajectoryExportBundle = hanakoState.trajectoryExportBundle,
        onBack = { shellState.showChat() },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onReload = {
          hanako.loadAutonomousLearningSnapshot()
          hanako.loadScheduleState()
          hanako.loadGatewayInboxState()
        },
        onCreateSchedule = { hanako.createScheduledAutomation(it) },
        onToggleSchedule = { id, enabled -> hanako.setScheduledAutomationEnabled(id, enabled) },
        onRunScheduleNow = { hanako.runScheduledAutomationNow(it) },
        onSaveMemoryProvider = { hanako.saveExternalMemoryProvider(it) },
        onToggleMemoryProvider = { id, enabled -> hanako.setExternalMemoryProviderEnabled(id, enabled) },
        onArchiveGatewayMessage = { hanako.archiveGatewayInboxMessage(it) },
        onBuildTrajectoryExport = { hanako.buildTrajectoryExportForCurrentSession() },
        onShareTrajectoryExport = { shareTrajectoryExport(hanakoState.trajectoryExportBundle) },
        onClearTrajectoryExport = { hanako.clearTrajectoryExport() },
        onBuildRecall = { hanako.buildLearningRecallForCurrentSession() },
        onRefreshContextInsights = { hanako.refreshContextInsights() },
        onCompressContext = { hanako.compressForkSession() },
        onApproveEvent = { hanako.approveLearningEvent(it) },
        onRejectEvent = { hanako.rejectLearningEvent(it) },
        onRevertEvent = { hanako.revertLearningEvent(it) },
        onEditJourneyNode = { id, content -> hanako.editLearningJourneyNode(id, content) },
        onDeleteJourneyNode = { hanako.deleteLearningJourneyNode(it) },
        onOpenMemory = { shellState.showPage(NbgShellPage.Memory) },
        onOpenSkills = { shellState.showPage(NbgShellPage.Skills) },
      )
      NbgShellPage.Skills -> NbgSkillsScreen(
        snapshot = hanakoState.skillsSnapshot,
        rawSnapshot = hanakoState.rawSkillsSnapshot,
        skillCuratorMetadata = hanakoState.skillCuratorMetadata,
        skillCuratorLoopState = hanakoState.skillCuratorLoopState,
        skillDiffPreview = hanakoState.skillDiffPreview,
        learnedDraftQueue = hanakoState.learnedSkillDraftQueue,
        loading = hanakoState.skillsLoading,
        error = hanakoState.skillsError,
        busyKey = hanakoState.skillsBusyKey,
        onBack = { shellState.showChat() },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onReload = { hanako.loadSkills() },
        onReloadRuntime = { hanako.reloadSkills() },
        onInstall = { hanako.installSkill(it) },
        translatedDescriptions = skillTranslationState.translations,
        translatingSkillName = skillTranslationState.translatingSkillName,
        translationMessages = skillTranslationState.messages,
        onTranslate = { translateSkillDescription(it) },
        onSetEnabled = { skillName, enabled -> hanako.setSkillEnabled(skillName, enabled) },
        onDelete = { hanako.deleteSkill(it) },
        onCreateBundle = { name, skillNames -> hanako.createSkillBundle(name, skillNames) },
        onUpdateBundle = { bundleId, name, skillNames -> hanako.updateSkillBundle(bundleId, name, skillNames) },
        onDeleteBundle = { hanako.deleteSkillBundle(it) },
        onSetExternalPaths = { hanako.setExternalSkillPaths(it) },
        onRejectLearnedDraft = { hanako.rejectLearnedSkillDraft(it) },
        onArchiveSkill = { hanako.archiveSkill(it) },
        onRestoreSkill = { hanako.restoreArchivedSkill(it) },
        onRunCuratorReview = { hanako.runSkillCuratorReview() },
        onSetCuratorLoopEnabled = { hanako.setSkillCuratorLoopEnabled(it) },
        onRunCuratorLoopNow = { hanako.runSkillCuratorLoopNow() },
        onPreviewCurrentSkillDiff = { hanako.previewCurrentSkillDiff(it) },
        onApplySkillDiffMerge = { hanako.applySkillDiffMerge(it) },
        onCloseSkillDiffPreview = { hanako.closeSkillDiffPreview() },
      )
      NbgShellPage.Pets -> NbgPetsScreen(
        state = petUiState.petState,
        manifest = petUiState.manifest,
        loading = petUiState.loadingManifest,
        installingSlug = petUiState.installingSlug,
        error = petUiState.error,
        onBack = { shellState.showChat() },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onReloadManifest = { refreshPetDex() },
        onSelectPet = { slug -> petUiState.applyStoreState(petStore.setCurrent(slug)) },
        onDeletePet = { slug -> petUiState.applyStoreState(petStore.delete(slug)) },
        onSetHidden = { hidden -> petUiState.applyStoreState(petStore.setHidden(hidden)) },
        onInstallPet = { pet -> installPet(pet) },
      )
      NbgShellPage.Appearance -> NbgAppearanceScreen(
        preferences = chatPreferences,
        onBack = { shellState.showChat() },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onSelectTheme = ::savePreferredTheme,
        onSelectFont = ::savePreferredFont,
      )
      NbgShellPage.UrlApi -> NbgUrlApiScreen(
        entries = savedApis,
        providerProfileState = nbgProfilesForStoredApis(savedApis),
        expertReviewPrompt = expertReviewState.prompt,
        expertReviewSelectedKeys = expertReviewState.selectedModelKeys,
        expertReviewRunning = expertReviewState.running,
        expertReviewMessage = expertReviewState.message,
        expertReviewResult = expertReviewState.result,
        onBack = { shellState.showChat() },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onAdd = { openApiEditor() },
        onEdit = { openApiEditor(it) },
        onDelete = { entry ->
          val providerId = nbgUrlApiProviderId(entry.id)
          urlApiEntriesState.applySaved(apiStore.delete(entry.id))
          if (selectedUrlApiModel?.providerId == providerId || chatPreferences.modelProvider == providerId) {
            urlApiSelectionState.clearSelectedModel()
            clearPreferredModel()
          }
        },
        onExpertReviewPromptChange = { expertReviewState.prompt = it },
        onToggleExpertReviewModel = { expertReviewState.toggleModel(it) },
        onRunExpertReview = { runExpertReview() },
        onCancelExpertReview = { expertReviewState.cancelCurrentRun() },
      )
      NbgShellPage.ToolsetsDoctor -> NbgToolsetsDoctorScreen(
        preferences = chatPreferences,
        capabilities = capabilityRegistry,
        sessionCount = conversations.size,
        savedUrlApiCount = savedApis.size,
        compressionAvailable = hanakoState.compressionAvailable,
        onBack = { shellState.showChat() },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onSetToolsetEnabled = ::saveToolsetEnabled,
        onOpenTerminal = { shellState.showPage(NbgShellPage.Terminal) },
        onOpenAgents = { shellState.showPage(NbgShellPage.Agents) },
        onOpenMcp = { shellState.showPage(NbgShellPage.Mcp) },
        onOpenSkills = { shellState.showPage(NbgShellPage.Skills) },
        onOpenMemory = { shellState.showPage(NbgShellPage.Memory) },
        onOpenProviders = {
          loadSavedApis()
          shellState.showPage(NbgShellPage.UrlApi)
        },
        onOpenDiagnosticsExport = { openDiagnosticsExportDialog() },
      )
    }
  }

  if (confirmationState.revertTurnConfirmOpen) {
    NbgRevertTurnDialog(
      onDismiss = { confirmationState.dismissRevertTurnConfirm() },
      onConfirm = {
        confirmationState.dismissRevertTurnConfirm()
        hanako.revertLatestTurn()
      },
    )
  }
  if (confirmationState.operatePermissionWarningOpen) {
    val warningSessionPath = confirmationState.operatePermissionWarningSessionPath
    NbgOperatePermissionModeDialog(
      onDismiss = { confirmationState.dismissOperatePermissionWarning() },
      onConfirm = {
        confirmationState.acceptOperatePermissionWarning()
        if (warningSessionPath == null || warningSessionPath == hanakoState.sessionPath) {
          applyPreferredPermissionMode(NBG_PERMISSION_MODE_OPERATE)
        }
      },
    )
  }
  if (fileShareUiState.isOpen) {
    val fileShareState = fileShareUiState.serverState ?: NbgFileShareServerRegistry.snapshot(context)
    NbgFileShareDialog(
      state = fileShareState,
      onRefresh = {
        fileShareUiState.applyServerState(NbgFileShareServerRegistry.start(context, fileShareState.accessMode))
      },
      onStartReadOnly = {
        fileShareUiState.applyServerState(NbgFileShareServerRegistry.start(context, NbgFileShareAccessMode.ReadOnly))
      },
      onStartReadWrite = {
        fileShareUiState.applyServerState(NbgFileShareServerRegistry.start(context, NbgFileShareAccessMode.ReadWrite))
      },
      onDismiss = { fileShareUiState.dismiss() },
    )
  }
  if (diagnosticsExportState.isOpen) {
    NbgDiagnosticsExportDialog(
      exportText = diagnosticsExportState.exportText,
      onCopy = { copyDiagnosticsExport() },
      onShare = { shareDiagnosticsExport() },
      onDismiss = { diagnosticsExportState.dismiss() },
    )
  }
  if (apiEditor.isOpen) {
    NbgUrlApiEditorDialog(
      title = if (apiEditor.editingApi == null) "添加网址 API" else "编辑网址 API",
      name = apiEditor.nameDraft,
      baseUrl = apiEditor.baseUrlDraft,
      apiKey = apiEditor.apiKeyDraft,
      models = apiEditor.models,
      verifiedModelIds = apiEditor.verifiedModelIds,
      selectedModelId = apiEditor.selectedModelId,
      busy = apiEditor.busy,
      message = apiEditor.actionMessage,
      onNameChange = { apiEditor.nameDraft = it },
      onBaseUrlChange = apiEditor::updateBaseUrl,
      onApiKeyChange = apiEditor::updateApiKey,
      onSelectModel = { apiEditor.selectedModelId = it },
      onFetchModels = {
        val requestSerial = apiEditor.nextRequestSerial()
        val requestedUrl = apiEditor.baseUrlDraft
        val requestedKey = apiEditor.apiKeyDraft.trim()
        apiEditor.busy = true
        apiEditor.actionMessage = "正在获取上游模型..."
        scope.launch {
          val result = apiClient.fetchModels(requestedUrl, requestedKey)
          if (
            !apiEditor.isCurrentRequest(requestSerial) ||
            requestedUrl != apiEditor.baseUrlDraft ||
            requestedKey != apiEditor.apiKeyDraft.trim()
          ) return@launch
          apiEditor.applyFetchedModels(result)
        }
      },
      onVerifyModel = { model ->
        val requestSerial = apiEditor.nextRequestSerial()
        val requestedUrl = apiEditor.baseUrlDraft
        val requestedKey = apiEditor.apiKeyDraft.trim()
        val requestedModelId = model.id
        apiEditor.busy = true
        apiEditor.actionMessage = "正在验证 ${model.id}..."
        scope.launch {
          val result = apiClient.verifyModel(requestedUrl, requestedKey, requestedModelId)
          if (
            !apiEditor.isCurrentRequest(requestSerial) ||
            requestedUrl != apiEditor.baseUrlDraft ||
            requestedKey != apiEditor.apiKeyDraft.trim() ||
            apiEditor.models.none { it.id == requestedModelId }
          ) return@launch
          apiEditor.applyVerifiedModel(
            modelId = requestedModelId,
            result = result,
            fallbackEffectiveBaseUrl = nbgHanakoBaseUrlForUrlApi(apiEditor.baseUrlDraft, "openai"),
          )
        }
      },
      onSave = {
        val baseUrl = nbgNormalizeApiBaseUrl(apiEditor.effectiveBaseUrlDraft.ifBlank { apiEditor.baseUrlDraft })
        val name = apiEditor.nameDraft.trim().ifBlank { nbgApiNameFor(baseUrl) }
        val selectedVerifiedModelId = apiEditor.selectedVerifiedModelId()
        val next = NbgStoredApi(
          id = apiEditor.editingApi?.id ?: java.util.UUID.randomUUID().toString(),
          name = name,
          baseUrl = baseUrl,
          apiKey = apiEditor.apiKeyDraft.trim(),
          models = apiEditor.models,
          verifiedModelIds = apiEditor.verifiedModelIds,
          selectedModelId = selectedVerifiedModelId,
        )
        urlApiEntriesState.applySaved(apiStore.save(next))
        closeApiEditor()
      },
      onDismiss = { closeApiEditor() },
    )
  }
}

private fun nbgInitialShellPage(value: String?): NbgShellPage {
  return when (value?.trim()?.lowercase()) {
    "agents" -> NbgShellPage.Agents
    "terminal" -> NbgShellPage.Terminal
    "mcp" -> NbgShellPage.Mcp
    "skills" -> NbgShellPage.Skills
    "memory" -> NbgShellPage.Memory
    "learning", "learn" -> NbgShellPage.Learning
    "pets", "petdex" -> NbgShellPage.Pets
    "appearance", "theme", "themes" -> NbgShellPage.Appearance
    "url-api", "providers" -> NbgShellPage.UrlApi
    "toolsets", "doctor", "tools" -> NbgShellPage.ToolsetsDoctor
    else -> NbgShellPage.Chat
  }
}

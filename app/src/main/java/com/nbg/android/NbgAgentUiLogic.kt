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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
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

internal enum class NbgShellPage {
  Chat,
  Agents,
  Terminal,
  Mcp,
  Memory,
  Learning,
  Skills,
  Pets,
  Appearance,
  UrlApi,
  ToolsetsDoctor,
}

internal enum class NbgAgentRole {
  User,
  Assistant,
  System,
  Thinking,
  Tool,
  ContentBlock,
}

internal enum class NbgSendButtonVisualState {
  Disabled,
  Send,
  Stop,
  Steer,
}

internal const val HANA_MOBILE_STREAM_DELTA_FLUSH_MS = 180L
internal const val HANA_MOBILE_STREAM_TEXT_CHUNK_SIZE = 420
internal const val HANA_MOBILE_HISTORY_TEXT_CHUNK_SIZE = 900
internal const val HANA_MOBILE_HISTORY_MESSAGE_ID_BASE = 1_000_000_000L
internal const val HANA_MOBILE_STREAM_SCROLL_TEXT_BUCKET = HANA_MOBILE_STREAM_TEXT_CHUNK_SIZE * 2
internal const val HANA_MOBILE_CHAT_BOTTOM_SCROLL_OFFSET = 1_000_000
internal const val HANA_MOBILE_CHAT_RESTORE_SCROLL_PASSES = 4
internal const val HANA_MOBILE_CHAT_RESTORE_SCROLL_DELAY_MS = 32L
internal const val HANA_MOBILE_TOOL_TEXT_CHUNK_CHARS = 2_400
internal const val HANA_MOBILE_TOOL_TEXT_CHUNK_LINES = 32
internal const val HANA_MOBILE_TERMINAL_SCROLL_BUCKET_CHARS = 640
internal const val HANA_MOBILE_TOOL_STATUS_FLUSH_MS = 220L
internal const val HANA_MOBILE_LIVE_TOOL_PREVIEW_CHARS = 560
internal const val HANA_MOBILE_LIVE_TOOL_PREVIEW_LINES = 6

internal data class NbgAgentMessage(
  val id: Long,
  val role: NbgAgentRole,
  val text: String,
  val done: Boolean = false,
  val contentBlock: HanakoContentBlock? = null,
  val toolStatus: HanakoToolStatus? = null,
  val textChunks: List<String>? = null,
  val streaming: Boolean = false,
)

internal data class NbgMessageListItem(
  val key: String,
  val message: NbgAgentMessage,
  val showHeader: Boolean = true,
  val sourceTextLength: Int = message.text.length,
  val terminalOutput: HanakoTerminalOutput? = null,
  val terminalChunkIndex: Int = 0,
  val terminalChunkCount: Int = 0,
  val filePreview: HanakoFilePreview? = null,
  val filePreviewChunkIndex: Int = 0,
  val filePreviewChunkCount: Int = 0,
  val fileDiff: HanakoFileDiff? = null,
  val fileDiffLines: List<String>? = null,
  val fileDiffChunkIndex: Int = 0,
  val fileDiffChunkCount: Int = 0,
) {
  val contentType: String
    get() = when {
      terminalOutput != null -> "terminal-output-chunk"
      filePreview != null -> "file-preview-chunk"
      fileDiffLines != null -> "file-diff-chunk"
      message.role == NbgAgentRole.Tool -> {
        val tool = message.toolStatus
        "tool-${tool?.kind.orEmpty()}-${if (tool?.terminalOutput != null) "terminal" else "summary"}"
      }
      message.role == NbgAgentRole.Thinking -> if (message.done) "thinking-done" else "thinking-live"
      key.contains(":chunk:") -> "message-chunk-${message.role}"
      else -> "message-${message.role}"
    }
}

internal data class NbgMessageListSignature(
  val id: Long,
  val role: NbgAgentRole,
  val textLength: Int,
  val textHash: Int,
  val contentBlockHash: Int,
  val done: Boolean,
  val streaming: Boolean,
  val textChunkCount: Int,
  val toolKey: String,
  val toolMetaHash: Int,
  val toolRunning: Boolean,
  val toolStatus: String,
  val terminalLength: Int,
  val terminalHash: Int,
  val terminalAlive: Boolean?,
  val terminalExitCode: Int?,
  val terminalTruncated: Boolean,
  val terminalSliceFrom: Int?,
  val terminalSliceTo: Int?,
  val filePreviewLength: Int,
  val filePreviewHash: Int,
  val filePreviewTruncated: Boolean,
  val fileDiffLength: Int,
  val fileDiffHash: Int,
)

internal data class NbgAutoScrollSignature(
  val lastMessageId: Long?,
  val lastRole: NbgAgentRole?,
  val lastDone: Boolean,
  val lastStreaming: Boolean,
  val lastTextBucket: Int,
  val contentBlockHash: Int,
  val toolKey: String,
  val toolMetaHash: Int,
  val toolRunning: Boolean,
  val toolStatus: String,
  val terminalOutputBucket: Int,
  val filePreviewBucket: Int,
  val fileDiffBucket: Int,
)

internal data class NbgChatRunStatus(
  val label: String,
  val detail: String? = null,
  val active: Boolean = false,
  val warning: Boolean = false,
)

internal data class NbgThinkingDepthOption(
  val level: String,
  val label: String,
  val description: String,
)

internal data class NbgSelectedUrlApiModel(
  val providerId: String,
  val modelId: String,
  val label: String,
)

internal data class NbgComposerModelGroup(
  val label: String,
  val models: List<HanakoModelSummary>,
)

internal fun NbgChatPreferences.toSelectedUrlApiModel(): NbgSelectedUrlApiModel? =
  if (modelProvider.startsWith("urlapi-") && modelId.isNotBlank()) {
    NbgSelectedUrlApiModel(modelProvider, modelId, modelLabel.ifBlank { modelId })
  } else {
    null
  }

internal val NBG_THINKING_DEPTH_OPTIONS = listOf(
  NbgThinkingDepthOption("off", "关闭", "不主动请求模型推理预算。"),
  NbgThinkingDepthOption("auto", "自动", "由 HanakoPro 根据模型和会话自动决定。"),
  NbgThinkingDepthOption("low", "快速", "更快、消耗更低。"),
  NbgThinkingDepthOption("medium", "均衡", "速度和推理深度折中。"),
  NbgThinkingDepthOption("high", "深度", "提高复杂任务推理深度。"),
  NbgThinkingDepthOption("xhigh", "最大思考", "仅对支持高强度推理的模型生效。"),
)

internal fun nbgThinkingDepthIndex(level: String): Int =
  NBG_THINKING_DEPTH_OPTIONS.indexOfFirst { it.level == level }.takeIf { it >= 0 }
    ?: NBG_THINKING_DEPTH_OPTIONS.indexOfFirst { it.level == "auto" }

internal fun nbgThinkingDepthDisplayLabel(level: String, fallback: String = ""): String =
  NBG_THINKING_DEPTH_OPTIONS.firstOrNull { it.level == level }?.label
    ?: fallback.ifBlank { NBG_THINKING_DEPTH_OPTIONS[nbgThinkingDepthIndex("auto")].label }

internal fun nbgSupportedThinkingDepthOptions(model: HanakoModelSummary?): List<NbgThinkingDepthOption> {
  val normalized = nbgNormalizeThinkingLevels(model?.thinkingLevels.orEmpty()).toSet()
  if (normalized.isEmpty()) return NBG_THINKING_DEPTH_OPTIONS
  return NBG_THINKING_DEPTH_OPTIONS.filter { it.level in normalized || it.level == "off" || it.level == "auto" }
    .ifEmpty { NBG_THINKING_DEPTH_OPTIONS.take(2) }
}

internal fun nbgCurrentModelSummary(
  config: HanakoAgentModelConfig?,
  modelName: String?,
  preferredModel: NbgChatPreferences? = null,
): HanakoModelSummary? {
  val models = config?.models.orEmpty()
  return models.firstOrNull {
    preferredModel?.hasModel == true && it.provider == preferredModel.modelProvider && it.id == preferredModel.modelId
  }
    ?: models.firstOrNull { it.isCurrent }
    ?: models.filter { model ->
      modelName != null && (
        model.id.equals(modelName, ignoreCase = true) ||
          model.name.equals(modelName, ignoreCase = true) ||
          model.label.equals(modelName, ignoreCase = true)
        )
    }.singleOrNull()
}

internal fun nbgModelKey(model: HanakoModelSummary): String =
  "${model.provider}\u0000${model.id}"

internal fun nbgUrlApiModelSummaries(entries: List<NbgStoredApi>, activeSelection: NbgSelectedUrlApiModel?): List<HanakoModelSummary> =
  entries.flatMap { entry ->
    val availableModels = nbgVerifiedUrlApiModels(entry)
    val providerId = nbgUrlApiProviderId(entry.id)
    availableModels.map { model ->
      HanakoModelSummary(
        id = model.id,
        name = model.label,
        provider = providerId,
        providerLabel = entry.name.ifBlank { nbgApiNameFor(entry.baseUrl) },
        input = listOf("text"),
        contextWindow = model.contextWindow,
        thinkingLevels = nbgSupportedThinkingLevelsForModel(
          modelId = model.id,
          provider = providerId,
          baseUrl = entry.baseUrl,
        ),
        thinkingSource = nbgThinkingSourceForModel(
          modelId = model.id,
          provider = providerId,
          baseUrl = entry.baseUrl,
        ),
        isCurrent = activeSelection?.providerId == providerId && activeSelection.modelId == model.id,
      )
    }
  }

internal fun nbgMergeAgentModelConfig(
  config: HanakoAgentModelConfig?,
  urlApiModels: List<HanakoModelSummary>,
  activeUrlApiProviderIds: Set<String> = urlApiModels.map { it.provider }.filter { it.startsWith("urlapi-") }.toSet(),
): HanakoAgentModelConfig? {
  if (config == null && urlApiModels.isEmpty()) return null
  val serverModels = config?.models.orEmpty().filter { model ->
    !model.provider.startsWith("urlapi-") || model.provider in activeUrlApiProviderIds
  }
  val currentKey = urlApiModels.firstOrNull { it.isCurrent }?.let(::nbgModelKey)
    ?: serverModels.firstOrNull { it.isCurrent }?.let(::nbgModelKey)
  val urlApiModelsByKey = urlApiModels.associateBy(::nbgModelKey)
  val serverModelKeys = serverModels.map(::nbgModelKey).toSet()
  val serverModelsWithLocalLabels = serverModels.map { serverModel ->
    val localModel = urlApiModelsByKey[nbgModelKey(serverModel)] ?: return@map serverModel
    serverModel.copy(
      name = serverModel.name.ifBlank { localModel.name },
      providerLabel = localModel.providerLabel,
      thinkingLevels = serverModel.thinkingLevels.ifEmpty { localModel.thinkingLevels },
      thinkingSource = serverModel.thinkingSource.ifBlank { localModel.thinkingSource },
      contextWindow = localModel.contextWindow.takeIf { it > 0L } ?: serverModel.contextWindow,
    )
  }
  val mergedModels = (serverModelsWithLocalLabels + urlApiModels.filterNot { nbgModelKey(it) in serverModelKeys })
    .distinctBy(::nbgModelKey)
  return HanakoAgentModelConfig(
    agents = config?.agents.orEmpty(),
    models = mergedModels.map { model ->
      model.copy(isCurrent = currentKey != null && nbgModelKey(model) == currentKey)
    },
  )
}

internal fun nbgComposerModelGroups(config: HanakoAgentModelConfig?): List<NbgComposerModelGroup> =
  config?.models.orEmpty()
    .groupBy { nbgModelProviderGroupKey(it) }
    .map { (_, models) ->
      val sortedModels = models.sortedWith(
        compareByDescending<HanakoModelSummary> { it.isCurrent }
          .thenBy { it.label.lowercase() },
      )
      NbgComposerModelGroup(
        label = nbgModelProviderDisplayName(sortedModels.firstOrNull()),
        models = sortedModels.take(6),
      )
    }
    .sortedWith(
      compareByDescending<NbgComposerModelGroup> { group -> group.models.any { it.isCurrent } }
        .thenBy { it.label.lowercase() },
    )
    .take(8)

internal fun nbgModelProviderGroupKey(model: HanakoModelSummary): String =
  if (model.provider.startsWith("urlapi-")) model.provider else model.provider.lowercase()

internal fun nbgModelProviderDisplayName(provider: String): String =
  when {
    provider.startsWith("urlapi-") -> provider.removePrefix("urlapi-").take(8).ifBlank { "自定义接口" }
    provider.equals("openai", ignoreCase = true) -> "OpenAI"
    provider.equals("anthropic", ignoreCase = true) -> "Anthropic"
    provider.equals("deepseek", ignoreCase = true) -> "DeepSeek"
    else -> provider
  }

internal fun nbgModelProviderDisplayName(model: HanakoModelSummary?): String {
  if (model == null) return "模型"
  return model.providerLabel.ifBlank { nbgModelProviderDisplayName(model.provider) }
}

internal fun nbgModelCapabilityLine(model: HanakoModelSummary): String =
  listOfNotNull(
    nbgModelProviderDisplayName(model).takeIf { !model.provider.startsWith("urlapi-") },
    nbgModelThinkingLabel(model),
    model.contextWindow.takeIf { it > 0L }?.let { "ctx ${it / 1000L}k" },
  ).joinToString(" · ")

internal fun nbgModelThinkingLabel(model: HanakoModelSummary): String {
  val levels = nbgNormalizeThinkingLevels(model.thinkingLevels)
  return when {
    "xhigh" in levels -> "最大思考"
    levels.any { it == "low" || it == "medium" || it == "high" } -> "支持思考"
    levels.any { it == "auto" } -> "自动思考"
    else -> "普通对话"
  }
}

internal fun nbgSelectedUrlApi(
  entries: List<NbgStoredApi>,
  selected: NbgSelectedUrlApiModel?,
): Pair<NbgStoredApi, NbgApiModel>? {
  if (selected == null) return null
  val entry = entries.firstOrNull { nbgUrlApiProviderId(it.id) == selected.providerId } ?: return null
  val model = nbgVerifiedUrlApiModels(entry).firstOrNull { it.id == selected.modelId } ?: return null
  return entry to model
}

internal fun nbgDefaultSelectedUrlApiModel(entries: List<NbgStoredApi>): NbgSelectedUrlApiModel? =
  entries.firstNotNullOfOrNull { entry ->
    val availableModels = nbgVerifiedUrlApiModels(entry)
    val selectedModelId = entry.selectedModelId
      .takeIf { selected -> availableModels.any { it.id == selected } }
      .orEmpty()
      .ifBlank { availableModels.firstOrNull()?.id.orEmpty() }
    val model = availableModels.firstOrNull { it.id == selectedModelId } ?: return@firstNotNullOfOrNull null
    NbgSelectedUrlApiModel(
      providerId = nbgUrlApiProviderId(entry.id),
      modelId = model.id,
      label = model.label,
    )
  }

internal fun nbgVerifiedUrlApiModels(entry: NbgStoredApi): List<NbgApiModel> =
  entry.models.filter { model ->
    model.id in entry.verifiedModelIds
  }.map { model ->
    model.copy(contextWindow = nbgEffectiveModelContextWindow(model.id, model.contextWindow))
  }

internal data class NbgAgentConversation(
  val path: String,
  val title: String,
  val subtitle: String,
  val snippet: String? = null,
  val matchType: String? = null,
  val pinned: Boolean = false,
  val hasSummary: Boolean = false,
)

internal fun nbgConversationMetaLine(conversation: NbgAgentConversation): String =
  buildList {
    if (conversation.pinned) add("置顶")
    nbgConversationMatchTypeLabel(conversation.matchType)?.let { add(it) }
    if (conversation.hasSummary) add("有摘要")
    if (conversation.subtitle.isNotBlank()) add(conversation.subtitle)
  }
    .distinct()
    .joinToString(" · ")
    .ifBlank { "HanakoPro" }

internal fun nbgConversationPreviewSnippet(conversation: NbgAgentConversation): String? {
  val cleaned = conversation.snippet
    ?.replace(Regex("\\s+"), " ")
    ?.trim()
    ?.takeIf { it.isNotBlank() && it != conversation.title.trim() }
    ?: return null
  return cleaned.take(180)
}

internal fun nbgConversationMatchTypeLabel(matchType: String?): String? =
  when (matchType?.trim()?.lowercase()) {
    null, "" -> null
    "title" -> "标题匹配"
    "content", "message", "messages", "body", "text" -> "内容匹配"
    "summary" -> "摘要匹配"
    "todo", "todos" -> "Todo 匹配"
    "tool", "tools" -> "工具匹配"
    "terminal", "command", "shell" -> "终端匹配"
    "file", "files", "path" -> "文件匹配"
    else -> "${matchType.trim().take(24)} 匹配"
  }

internal fun HanakoContentBlock.applyPatch(patch: HanakoContentBlockPatch): HanakoContentBlock =
  copy(
    title = patch.title?.takeIf { it.isNotBlank() } ?: title,
    subtitle = patch.subtitle?.takeIf { it.isNotBlank() } ?: subtitle,
    detail = patch.detail?.takeIf { it.isNotBlank() } ?: detail,
    status = patch.status?.takeIf { it.isNotBlank() } ?: status,
  )

internal fun HanakoContentBlockPatch?.merge(next: HanakoContentBlockPatch): HanakoContentBlockPatch =
  HanakoContentBlockPatch(
    title = next.title ?: this?.title,
    subtitle = next.subtitle ?: this?.subtitle,
    detail = next.detail ?: this?.detail,
    status = next.status ?: this?.status,
  )

internal fun HanakoToolStatus.asRestoredHistoryToolStatus(key: String): HanakoToolStatus {
  if (!running) return copy(key = key)
  return copy(
    key = key,
    subtitle = listOf(subtitle.removeSuffix(" / 进行中"), "未收到结束事件")
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .distinct()
      .joinToString(" / "),
    status = "failed",
    running = false,
    success = false,
  )
}

internal fun HanakoToolStatus.nbgStableToolStatusKey(): String? {
  val explicitKey = key.takeIf { it.isNotBlank() && !it.isGenericToolStatusKey(toolName, kind, title) }
  if (kind != "terminal" && terminalOutput == null) return explicitKey ?: key.takeIf { it.isNotBlank() }
  explicitKey?.let { return it }
  terminalOutput?.sessionId
    ?.takeIf { it.isNotBlank() }
    ?.let { return "terminal:$it" }
  nbgTerminalCommandIdentity()
    ?.let { return "terminal-command:$it" }
  return key.takeIf { it.isNotBlank() }
}

internal fun String.isGenericToolStatusKey(toolName: String, kind: String, title: String): Boolean {
  val normalized = trim()
  if (normalized.isBlank()) return true
  if (normalized == toolName || normalized == kind || normalized == title) return true
  if (normalized == "tool" || normalized == "terminal" || normalized == "bash") return true
  if (normalized.startsWith("tool_progress:") || normalized.startsWith("tool_status:")) return true
  if (normalized.startsWith("terminal_status:") || normalized.startsWith("terminal_output:")) return true
  return false
}

internal fun HanakoToolStatus.nbgTerminalCommandIdentity(): String? =
  listOf(detail, terminalOutput?.output.orEmpty(), filePath)
    .asSequence()
    .flatMap { it.lineSequence() }
    .map { it.trim().removePrefix("$").trim() }
    .firstOrNull { line ->
      line.isNotBlank() &&
        line != "开始" &&
        line != "完成" &&
        !line.startsWith("历史记录") &&
        !line.equals("running", ignoreCase = true) &&
        !line.equals("done", ignoreCase = true)
    }
    ?.replace(Regex("\\s+"), " ")
    ?.take(180)

internal fun HanakoToolStatus.canMergeToolStatus(next: HanakoToolStatus): Boolean {
  if (kind != next.kind) return false
  if (kind == "terminal") {
    val currentStableKey = nbgStableToolStatusKey()
    val nextStableKey = next.nbgStableToolStatusKey()
    if (!currentStableKey.isNullOrBlank() && !nextStableKey.isNullOrBlank()) {
      return currentStableKey == nextStableKey
    }
  }
  if (key.isNotBlank() && next.key.isNotBlank() && key == next.key) return true
  if (kind == "file") {
    val samePath = filePath.isNotBlank() && filePath == next.filePath
    val sameTool = toolName.isNotBlank() && next.toolName.isNotBlank() && toolName == next.toolName
    return samePath && (sameTool || next.status == "running" || next.fileDiff != null || next.filePreview != null)
  }
  if (kind == "terminal") {
    val currentSession = terminalOutput?.sessionId.orEmpty()
    val nextSession = next.terminalOutput?.sessionId.orEmpty()
    if (currentSession.isNotBlank() && currentSession == nextSession) return true
    val currentCommand = nbgTerminalCommandIdentity()
    val nextCommand = next.nbgTerminalCommandIdentity()
    return !currentCommand.isNullOrBlank() && currentCommand == nextCommand
  }
  return false
}

internal fun HanakoToolStatus.mergeToolStatus(next: HanakoToolStatus): HanakoToolStatus =
  if (kind == NbgToolVisualizationKind.Confirmation.wireName && next.kind == NbgToolVisualizationKind.Confirmation.wireName) {
    mergeConfirmationToolStatus(next)
  } else {
    next.copy(
      key = key.ifBlank { next.key },
      toolName = next.toolName.ifBlank { toolName },
      filePath = next.filePath.ifBlank { filePath },
      detail = next.detail.ifBlank { detail },
      filePreview = filePreview.mergeFilePreview(next.filePreview),
      fileDiff = next.fileDiff ?: fileDiff,
      terminalOutput = terminalOutput.mergeTerminalOutput(next.terminalOutput),
    )
  }

private fun HanakoToolStatus.mergeConfirmationToolStatus(next: HanakoToolStatus): HanakoToolStatus {
  val nextState = next.nbgToolVisualizationState()
  val currentState = nbgToolVisualizationState()
  val preserveCurrentResolution = nextState == NbgToolVisualizationState.Waiting &&
    currentState != NbgToolVisualizationState.Waiting
  return next.copy(
    key = key.ifBlank { next.key },
    toolName = next.toolName.ifBlank { toolName },
    filePath = next.filePath.ifBlank { filePath },
    title = mergeConfirmationTitle(next),
    subtitle = mergeConfirmationSubtitle(next),
    detail = next.detail.ifBlank { detail },
    status = if (preserveCurrentResolution) status else next.status,
    success = if (preserveCurrentResolution) success else next.success,
    filePreview = filePreview.mergeFilePreview(next.filePreview),
    fileDiff = next.fileDiff ?: fileDiff,
    terminalOutput = terminalOutput.mergeTerminalOutput(next.terminalOutput),
  )
}

private fun HanakoToolStatus.mergeConfirmationTitle(next: HanakoToolStatus): String =
  when {
    next.title.isBlank() -> title
    next.title == "确认请求" && title.isNotBlank() && title != "确认请求" -> title
    else -> next.title
  }

private fun HanakoToolStatus.mergeConfirmationSubtitle(next: HanakoToolStatus): String {
  val currentParts = subtitle.confirmationSubtitleParts()
  val nextParts = next.subtitle.confirmationSubtitleParts()
  val result = nextParts.firstOrNull() ?: currentParts.firstOrNull()
  val context = nextParts.drop(1).takeIf { it.isNotEmpty() } ?: currentParts.drop(1)
  return (listOfNotNull(result) + context)
    .filter { it.isNotBlank() }
    .distinct()
    .joinToString(" / ")
}

private fun String.confirmationSubtitleParts(): List<String> =
  split("/")
    .map { it.trim() }
    .filter { it.isNotBlank() }

internal fun HanakoFilePreview?.mergeFilePreview(next: HanakoFilePreview?): HanakoFilePreview? {
  if (this == null) return next
  if (next == null) return this
  val combinedText = previewText + next.previewText
  val nextText = when {
    next.previewText.isEmpty() -> previewText
    next.reset -> next.previewText
    next.append -> combinedText.take(HANA_MOBILE_FILE_PREVIEW_LIMIT)
    else -> next.previewText
  }
  return next.copy(
    previewText = nextText,
    truncated = truncated || next.truncated || combinedText.length > HANA_MOBILE_FILE_PREVIEW_LIMIT,
  )
}

internal const val HANA_MOBILE_FILE_PREVIEW_LIMIT = 3_000
internal const val HANA_MOBILE_TERMINAL_OUTPUT_LIMIT = 32_000
internal const val HANA_MOBILE_TERMINAL_CHUNK_CHARS = 1_800
internal const val HANA_MOBILE_TERMINAL_CHUNK_LINES = 28
internal const val HANA_MOBILE_TERMINAL_OVERLAP_SCAN_LIMIT = 4_096

internal fun HanakoTerminalOutput?.mergeTerminalOutput(next: HanakoTerminalOutput?): HanakoTerminalOutput? {
  if (this == null) return next
  if (next == null) return this
  val mergedOutput = mergeTerminalOutputText(
    currentOutput = output,
    nextOutput = next.output,
    currentSliceTo = sliceTo,
    nextSliceFrom = next.sliceFrom,
  )
  return next.copy(
    title = next.title.ifBlank { title },
    cwd = next.cwd.ifBlank { cwd },
    output = mergedOutput,
    staticOutput = staticOutput || next.staticOutput,
    alive = next.alive ?: alive,
    exitCode = next.exitCode ?: exitCode,
    truncated = truncated || next.truncated || mergedOutput.length >= HANA_MOBILE_TERMINAL_OUTPUT_LIMIT,
    outputPriority = maxOf(outputPriority, next.outputPriority),
    sliceFrom = listOfNotNull(sliceFrom, next.sliceFrom).minOrNull(),
    sliceTo = listOfNotNull(sliceTo, next.sliceTo).maxOrNull(),
  )
}

internal fun mergeTerminalOutputText(
  currentOutput: String,
  nextOutput: String,
  currentSliceTo: Int?,
  nextSliceFrom: Int?,
): String {
  if (nextOutput.isEmpty()) return currentOutput.takeLast(HANA_MOBILE_TERMINAL_OUTPUT_LIMIT)
  if (currentOutput.isEmpty()) return nextOutput.takeLast(HANA_MOBILE_TERMINAL_OUTPUT_LIMIT)
  if (nextOutput == currentOutput || currentOutput.endsWith(nextOutput)) {
    return currentOutput.takeLast(HANA_MOBILE_TERMINAL_OUTPUT_LIMIT)
  }
  if (nextOutput.contains(currentOutput)) {
    return nextOutput.takeLast(HANA_MOBILE_TERMINAL_OUTPUT_LIMIT)
  }
  val contiguous = currentSliceTo != null && nextSliceFrom != null && nextSliceFrom >= currentSliceTo
  val overlapped = mergeOverlappingText(currentOutput, nextOutput)
  return if (contiguous || !currentOutput.contains(nextOutput)) {
    overlapped.takeLast(HANA_MOBILE_TERMINAL_OUTPUT_LIMIT)
  } else {
    currentOutput.takeLast(HANA_MOBILE_TERMINAL_OUTPUT_LIMIT)
  }
}

internal fun mergeOverlappingText(currentOutput: String, nextOutput: String): String {
  val maxOverlap = minOf(currentOutput.length, nextOutput.length, HANA_MOBILE_TERMINAL_OVERLAP_SCAN_LIMIT)
  for (overlap in maxOverlap downTo 1) {
    if (currentOutput.regionMatches(
        thisOffset = currentOutput.length - overlap,
        other = nextOutput,
        otherOffset = 0,
        length = overlap,
      )
    ) {
      return currentOutput + nextOutput.substring(overlap)
    }
  }
  val separator = if (currentOutput.endsWith("\n") || nextOutput.startsWith("\n")) "" else "\n"
  return currentOutput + separator + nextOutput
}

internal fun HanakoExternalUserMessage.toDisplayText(): String =
  buildList {
    if (quotedText.isNotBlank()) add("引用：$quotedText")
    if (attachmentLabels.isNotEmpty()) add("附件：${attachmentLabels.joinToString(" / ")}")
    if (text.isNotBlank()) add(text)
  }.joinToString("\n")

internal fun appendStreamingTextChunk(previousText: String, delta: String): List<String> {
  if (delta.isEmpty()) return streamingTextChunksFor(previousText)
  return streamingTextChunksFor(previousText + delta)
}

internal fun appendStreamingTextChunksAfterPrefix(
  chunks: List<String>?,
  previousText: String,
  nextText: String,
): List<String> {
  if (nextText == previousText) return chunks ?: streamingTextChunksFor(nextText)
  if (
    chunks != null &&
    chunks.isNotEmpty() &&
    previousText.isNotEmpty() &&
    nextText.length > previousText.length &&
    nextText.startsWith(previousText)
  ) {
    val stablePrefix = chunks.dropLast(1)
    val lastPreviousChunk = chunks.last()
    if (previousText.endsWith(lastPreviousChunk)) {
      val appendedText = nextText.substring(previousText.length)
      val nextTailChunks = streamingTextChunksFor(lastPreviousChunk + appendedText)
      return stablePrefix + nextTailChunks
    }
  }
  return streamingTextChunksFor(nextText)
}

internal fun streamingTextChunksFor(text: String): List<String> =
  markdownAwareHistoryTextChunksFor(text, HANA_MOBILE_STREAM_TEXT_CHUNK_SIZE)

internal fun historyTextChunksFor(text: String): List<String> =
  markdownAwareHistoryTextChunksFor(text, HANA_MOBILE_HISTORY_TEXT_CHUNK_SIZE)

internal fun markdownAwareHistoryTextChunksFor(text: String, charLimit: Int): List<String> {
  if (text.isEmpty()) return emptyList()
  val chunks = mutableListOf<String>()
  val builder = StringBuilder()
  var inCode = false
  var chunkStartsInCode = false

  fun flush() {
    val raw = builder.toString().trimEnd('\n')
    if (raw.isBlank()) {
      builder.clear()
      chunkStartsInCode = inCode
      return
    }
    val withOpening = if (chunkStartsInCode) "```\n$raw" else raw
    val withClosing = if (inCode) "$withOpening\n```" else withOpening
    chunks += withClosing
    builder.clear()
    chunkStartsInCode = inCode
  }

  fun appendLine(line: String) {
    val nextLength = builder.length + line.length + 1
    if (builder.isNotEmpty() && nextLength > charLimit) flush()
    builder.append(line).append('\n')
    if (line.trimStart().startsWith("```")) {
      inCode = !inCode
    }
    if (builder.length >= charLimit) flush()
  }

  text.lineSequence().forEach(::appendLine)
  flush()
  return chunks.ifEmpty { listOf(text) }
}

internal fun nbgMessageListItems(
  messages: List<NbgAgentMessage>,
): List<NbgMessageListItem> =
  buildList {
    messages.forEachIndexed { messageIndex, message ->
      val messageKey = nbgMessageListKey(message, messageIndex)
      val tool = message.toolStatus
      val shouldSplitToolOutput = false
      val terminalChunks = if (shouldSplitToolOutput) tool?.terminalOutput?.let(::nbgTerminalOutputChunks).orEmpty() else emptyList()
      val filePreviewChunks = if (shouldSplitToolOutput) tool?.filePreview?.let(::nbgFilePreviewChunks).orEmpty() else emptyList()
      val fileDiffLineChunks = if (shouldSplitToolOutput) tool?.fileDiff?.let(::nbgFileDiffLineChunks).orEmpty() else emptyList()
      if (
        message.role == NbgAgentRole.Tool &&
        (filePreviewChunks.isNotEmpty() || fileDiffLineChunks.isNotEmpty() || terminalChunks.isNotEmpty())
      ) {
        val summaryTool = tool?.copy(filePreview = null, fileDiff = null, terminalOutput = null)
        add(
          NbgMessageListItem(
            key = messageKey,
            message = if (summaryTool != null) message.copy(toolStatus = summaryTool) else message,
          ),
        )
        filePreviewChunks.forEachIndexed { index, preview ->
          add(
            NbgMessageListItem(
              key = "$messageKey:file-preview:$index",
              message = message,
              showHeader = false,
              filePreview = preview,
              filePreviewChunkIndex = index,
              filePreviewChunkCount = filePreviewChunks.size,
            ),
          )
        }
        fileDiffLineChunks.forEachIndexed { index, lines ->
          add(
            NbgMessageListItem(
              key = "$messageKey:file-diff:$index",
              message = message,
              showHeader = false,
              fileDiff = tool?.fileDiff,
              fileDiffLines = lines,
              fileDiffChunkIndex = index,
              fileDiffChunkCount = fileDiffLineChunks.size,
            ),
          )
        }
        terminalChunks.forEachIndexed { index, terminal ->
          add(
            NbgMessageListItem(
              key = "$messageKey:terminal:$index",
              message = message,
              showHeader = false,
              terminalOutput = terminal,
              terminalChunkIndex = index,
              terminalChunkCount = terminalChunks.size,
            ),
          )
        }
        return@forEachIndexed
      }
      val chunks = nbgDisplayTextChunks(message)
      if (chunks == null) {
        add(
          NbgMessageListItem(
            key = messageKey,
            message = message,
          ),
        )
      } else {
        chunks.forEachIndexed { index, chunk ->
          val chunkMessage = message.copy(text = chunk, textChunks = null)
          add(
            NbgMessageListItem(
              key = "$messageKey:chunk:$index",
              message = chunkMessage,
              showHeader = index == 0,
            ),
          )
        }
      }
    }
}

internal fun nbgMessageListKey(message: NbgAgentMessage, messageIndex: Int): String =
  "${message.id}:$messageIndex:${message.role.name}"

internal fun nbgLastMessagePrimaryItemKey(items: List<NbgMessageListItem>): String? =
  items.lastOrNull()
    ?.message
    ?.let { lastMessage ->
      items.asReversed().firstOrNull { item ->
        item.message.id == lastMessage.id &&
          item.message.role == lastMessage.role &&
          item.showHeader
      }
    }
    ?.key

internal fun nbgMessageListSignature(messages: List<NbgAgentMessage>): List<NbgMessageListSignature> =
  messages.map { message ->
    val tool = message.toolStatus
    val block = message.contentBlock
    val terminal = tool?.terminalOutput
    val filePreview = tool?.filePreview
    val fileDiff = tool?.fileDiff
    NbgMessageListSignature(
      id = message.id,
      role = message.role,
      textLength = message.text.length,
      textHash = message.text.hashCode(),
      contentBlockHash = block?.let { nbgCombinedHash(it.type, it.title, it.subtitle, it.detail, it.status, it.taskId) } ?: 0,
      done = message.done,
      streaming = message.streaming,
      textChunkCount = message.textChunks?.size ?: 0,
      toolKey = tool?.key.orEmpty(),
      toolMetaHash = tool?.let { nbgCombinedHash(it.kind, it.toolName, it.filePath, it.title, it.subtitle, it.detail, it.status) } ?: 0,
      toolRunning = tool?.running == true,
      toolStatus = tool?.status.orEmpty(),
      terminalLength = terminal?.output?.length ?: 0,
      terminalHash = terminal?.output?.hashCode() ?: 0,
      terminalAlive = terminal?.alive,
      terminalExitCode = terminal?.exitCode,
      terminalTruncated = terminal?.truncated == true,
      terminalSliceFrom = terminal?.sliceFrom,
      terminalSliceTo = terminal?.sliceTo,
      filePreviewLength = filePreview?.previewText?.length ?: 0,
      filePreviewHash = filePreview?.previewText?.hashCode() ?: 0,
      filePreviewTruncated = filePreview?.truncated == true,
      fileDiffLength = fileDiff?.let { it.unifiedDiff.length + it.oldContent.length + it.newContent.length } ?: 0,
      fileDiffHash = fileDiff?.let { nbgCombinedHash(it.unifiedDiff, it.oldContent, it.newContent) } ?: 0,
    )
  }

internal fun nbgCombinedHash(vararg values: String): Int =
  values.fold(1) { acc, value -> 31 * acc + value.hashCode() }

internal fun nbgAutoScrollSignature(messages: List<NbgAgentMessage>): NbgAutoScrollSignature {
  val last = messages.lastOrNull()
  val tool = last?.toolStatus
  val block = last?.contentBlock
  val terminal = tool?.terminalOutput
  val filePreview = tool?.filePreview
  val fileDiff = tool?.fileDiff
  return NbgAutoScrollSignature(
    lastMessageId = last?.id,
    lastRole = last?.role,
    lastDone = last?.done == true,
    lastStreaming = last?.streaming == true,
    lastTextBucket = (last?.text?.length ?: 0) / HANA_MOBILE_STREAM_SCROLL_TEXT_BUCKET,
    contentBlockHash = block?.let { nbgCombinedHash(it.type, it.title, it.subtitle, it.detail, it.status, it.taskId) } ?: 0,
    toolKey = tool?.key.orEmpty(),
    toolMetaHash = tool?.let { nbgCombinedHash(it.kind, it.toolName, it.filePath, it.title, it.subtitle, it.detail, it.status) } ?: 0,
    toolRunning = tool?.running == true,
    toolStatus = tool?.status.orEmpty(),
    terminalOutputBucket = (terminal?.output?.length ?: 0) / HANA_MOBILE_TERMINAL_SCROLL_BUCKET_CHARS,
    filePreviewBucket = (filePreview?.previewText?.length ?: 0) / HANA_MOBILE_TOOL_TEXT_CHUNK_CHARS,
    fileDiffBucket = fileDiff?.let { (it.unifiedDiff.length + it.oldContent.length + it.newContent.length) / HANA_MOBILE_TOOL_TEXT_CHUNK_CHARS } ?: 0,
  )
}

internal fun nbgDisplayTextChunks(message: NbgAgentMessage): List<String>? {
  val canSplit = message.role == NbgAgentRole.Assistant ||
    message.role == NbgAgentRole.System ||
    message.role == NbgAgentRole.User ||
    message.role == NbgAgentRole.Thinking
  if (!canSplit) return null
  if (!message.streaming) {
    return historyTextChunksFor(message.text).takeIf { it.size > 1 }
  }
  message.textChunks
    ?.takeIf { it.size > 1 }
    ?.let { return it }
  return null
}

internal fun nbgTerminalOutputChunks(terminal: HanakoTerminalOutput): List<HanakoTerminalOutput> {
  if (terminal.output.isEmpty()) return emptyList()
  val chunks = nbgTextChunksByLine(
    text = terminal.output,
    lineLimit = HANA_MOBILE_TERMINAL_CHUNK_LINES,
    charLimit = HANA_MOBILE_TERMINAL_CHUNK_CHARS,
  )
  return chunks.mapIndexed { index, output ->
    terminal.copy(
      title = terminal.title.ifBlank { "terminal" },
      output = output,
      sliceFrom = index + 1,
      sliceTo = chunks.size,
    )
  }
}

internal fun nbgFilePreviewChunks(preview: HanakoFilePreview): List<HanakoFilePreview> {
  val text = preview.previewText.ifBlank { "准备写入文件..." }
  return nbgTextChunksByLine(
    text = text,
    lineLimit = HANA_MOBILE_TOOL_TEXT_CHUNK_LINES,
    charLimit = HANA_MOBILE_TOOL_TEXT_CHUNK_CHARS,
  ).map { chunk ->
    preview.copy(previewText = chunk)
  }
}

internal fun nbgFileDiffLineChunks(diff: HanakoFileDiff): List<List<String>> =
  nbgTextChunksByLine(
    text = nbgFullDiffLines(diff).joinToString("\n"),
    lineLimit = HANA_MOBILE_TOOL_TEXT_CHUNK_LINES,
    charLimit = HANA_MOBILE_TOOL_TEXT_CHUNK_CHARS,
  ).map { it.lines() }

internal fun nbgTextChunksByLine(
  text: String,
  lineLimit: Int,
  charLimit: Int,
): List<String> {
  if (text.isEmpty()) return emptyList()
  val chunks = mutableListOf<String>()
  val builder = StringBuilder()
  var lineCount = 0
  fun flush() {
    if (builder.isEmpty()) return
    chunks += builder.toString().trimEnd()
    builder.clear()
    lineCount = 0
  }
  fun appendLinePart(part: String) {
    val nextLength = builder.length + part.length + 1
    if (lineCount >= lineLimit || (builder.isNotEmpty() && nextLength > charLimit)) flush()
    if (builder.isNotEmpty()) builder.append('\n')
    builder.append(part)
    lineCount += 1
    if (builder.length >= charLimit) flush()
  }
  text.lineSequence().forEach { line ->
    if (line.length <= charLimit) {
      appendLinePart(line)
    } else {
      var start = 0
      while (start < line.length) {
        val end = (start + charLimit).coerceAtMost(line.length)
        appendLinePart(line.substring(start, end))
        start = end
      }
    }
  }
  flush()
  return chunks.ifEmpty { listOf(text) }
}

internal fun nbgLiveToolPreviewText(
  text: String,
  lineLimit: Int = HANA_MOBILE_LIVE_TOOL_PREVIEW_LINES,
  charLimit: Int = HANA_MOBILE_LIVE_TOOL_PREVIEW_CHARS,
): String {
  if (text.length <= charLimit && text.count { it == '\n' } < lineLimit) return text
  val tail = text.lineSequence()
    .filter { it.isNotBlank() }
    .toList()
    .takeLast(lineLimit)
    .joinToString("\n")
    .takeLast(charLimit)
    .trimStart()
  return if (tail.isBlank()) text.takeLast(charLimit).trimStart() else tail
}

internal fun nbgThinkingRunStatusDetail(message: NbgAgentMessage): String {
  val length = message.text.length
  if (length < 1_200) return "生成推理内容"
  val bucket = length / 1_000
  return "已接收约 ${bucket}k 字"
}

internal fun NbgAgentMessage.preparedTextFor(
  key: String,
  pretextCache: NbgPretextCache,
): NbgPreparedText? =
  when (role) {
    NbgAgentRole.Assistant,
    NbgAgentRole.System -> pretextCache.prepareText(key, text, streaming)
    else -> null
  }

internal fun nbgChatRunStatus(state: HanakoChatState, messages: List<NbgAgentMessage>): NbgChatRunStatus {
  val activeThinking = messages.lastOrNull { it.role == NbgAgentRole.Thinking && !it.done }
  val activeTool = messages.lastOrNull { it.role == NbgAgentRole.Tool && it.toolStatus?.running == true }?.toolStatus
  return when {
    state.compressing -> NbgChatRunStatus("正在压缩上下文", state.contextUsageLabel, active = true)
    activeThinking != null -> NbgChatRunStatus("正在思考", nbgThinkingRunStatusDetail(activeThinking), active = true)
    activeTool != null -> NbgChatRunStatus(
      "正在调用工具",
      activeTool.title.ifBlank { activeTool.toolName.ifBlank { nbgToolKindLabel(activeTool.kind).ifBlank { "执行中" } } },
      active = true,
    )
    state.streaming -> NbgChatRunStatus("正在输出", state.modelName ?: state.agentName, active = true)
    state.prewarming -> NbgChatRunStatus("后台预热 HanakoPro", "发送前准备中", active = true)
    state.connecting -> NbgChatRunStatus("正在连接 HanakoPro", state.connectionLabel, active = true)
    !state.connected && messages.isNotEmpty() -> NbgChatRunStatus("本地历史", "后台预热中")
    !state.connected -> NbgChatRunStatus("HanakoPro 未连接", state.lastError ?: state.connectionLabel, warning = true)
    state.lastError != null -> NbgChatRunStatus("需要处理", state.lastError, warning = true)
    else -> NbgChatRunStatus("空闲", state.modelName ?: state.agentName ?: "HanakoPro 已连接")
  }
}

internal fun nbgChatRunStatusWithDisplayModel(
  state: HanakoChatState,
  messages: List<NbgAgentMessage>,
  displayModelName: String,
): NbgChatRunStatus =
  nbgChatRunStatus(
    state.copy(modelName = displayModelName.ifBlank { state.modelName.orEmpty() }),
    messages,
  )

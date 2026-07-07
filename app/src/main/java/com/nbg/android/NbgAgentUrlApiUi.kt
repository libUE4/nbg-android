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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TextButton
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
internal fun NbgShellSubPage(
  title: String,
  subtitle: String,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  actions: @Composable RowScope.() -> Unit = {},
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(NbgAgentColors.Background),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .background(NbgAgentColors.Drawer)
          .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        NbgPlainIconButton(
          icon = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "返回聊天",
          onClick = onBack,
        )
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 10.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = title,
            color = NbgAgentColors.TextStrong,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = subtitle,
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        actions()
        NbgPlainIconButton(
          icon = Icons.Filled.Menu,
          contentDescription = "打开会话抽屉",
          onClick = onOpenDrawer,
        )
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(NbgAgentColors.InputBorder),
      )
      Box(modifier = Modifier.fillMaxSize()) {
        content()
      }
    }
  }
}

@Composable
internal fun NbgUrlApiScreen(
  entries: List<NbgStoredApi>,
  providerProfileState: NbgModelProviderProfileState = NbgModelProviderProfileState(),
  providerProfileTemplateText: String = "",
  providerProfileMessage: String = "",
  editableProviderProfileIds: Set<String> = emptySet(),
  expertReviewPrompt: String,
  expertReviewSelectedKeys: Set<String>,
  expertReviewRunning: Boolean,
  expertReviewMessage: String,
  expertReviewResult: NbgExpertReviewRunResult?,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onAdd: () -> Unit,
  onEdit: (NbgStoredApi) -> Unit,
  onDelete: (NbgStoredApi) -> Unit,
  onExpertReviewPromptChange: (String) -> Unit,
  onToggleExpertReviewModel: (NbgExpertReviewModelRef) -> Unit,
  onRunExpertReview: () -> Unit,
  onCancelExpertReview: () -> Unit,
  onProviderProfileTemplateChange: (String) -> Unit = {},
  onImportProviderProfiles: (String) -> Unit = {},
  onExportProviderProfiles: () -> String = { "" },
  onClearProviderProfiles: () -> Unit = {},
  onSaveProviderProfile: (String, NbgModelProviderProfile) -> Unit = { _, _ -> },
  onDeleteProviderProfile: (String) -> Unit = {},
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(NbgAgentColors.Background),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
          .background(NbgAgentColors.Drawer)
          .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        NbgPlainIconButton(
          icon = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "返回聊天",
          onClick = onBack,
        )
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 10.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = "网址 API",
            color = NbgAgentColors.TextStrong,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${entries.size} 个配置 · 本地私有保存",
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgPlainIconButton(
          icon = Icons.Filled.Menu,
          contentDescription = "打开会话抽屉",
          onClick = onOpenDrawer,
        )
        NbgPlainIconButton(
          icon = Icons.Filled.Add,
          contentDescription = "添加网址 API",
          onClick = onAdd,
        )
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(NbgAgentColors.InputBorder),
      )
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        item {
          NbgUrlApiProviderProfilesCard(
            state = providerProfileState,
            templateText = providerProfileTemplateText,
            message = providerProfileMessage,
            editableProfileIds = editableProviderProfileIds,
            onTemplateChange = onProviderProfileTemplateChange,
            onImport = onImportProviderProfiles,
            onExport = onExportProviderProfiles,
            onClear = onClearProviderProfiles,
            onSaveProfile = onSaveProviderProfile,
            onDeleteProfile = onDeleteProviderProfile,
          )
        }
        item {
          NbgExpertReviewReadinessCard(
            entries = entries,
            prompt = expertReviewPrompt,
            selectedKeys = expertReviewSelectedKeys,
            running = expertReviewRunning,
            message = expertReviewMessage,
            result = expertReviewResult,
            onPromptChange = onExpertReviewPromptChange,
            onToggleModel = onToggleExpertReviewModel,
            onRun = onRunExpertReview,
            onCancel = onCancelExpertReview,
          )
        }
        if (entries.isEmpty()) {
          item {
            NbgUrlApiEmptyState(onAdd = onAdd)
          }
        } else {
          items(entries, key = { it.id }) { entry ->
            NbgUrlApiEntryRow(
              entry = entry,
              onClick = { onEdit(entry) },
              onDelete = { onDelete(entry) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun NbgUrlApiProviderProfilesCard(
  state: NbgModelProviderProfileState,
  templateText: String,
  message: String,
  editableProfileIds: Set<String>,
  onTemplateChange: (String) -> Unit,
  onImport: (String) -> Unit,
  onExport: () -> String,
  onClear: () -> Unit,
  onSaveProfile: (String, NbgModelProviderProfile) -> Unit,
  onDeleteProfile: (String) -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  var dialogMode by remember { mutableStateOf<String?>(null) }
  var editingDraft by remember { mutableStateOf<NbgProviderProfileEditorDraft?>(null) }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
          imageVector = Icons.Filled.Settings,
          contentDescription = null,
          tint = NbgAgentColors.Primary,
          modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Provider Profiles",
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${state.profileCount} 个 provider profile · auth/model/extra body 边界",
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        NbgInlineActionButton(
          label = "新增",
          icon = Icons.Filled.Add,
          onClick = { editingDraft = NbgProviderProfileEditorDraft() },
        )
        NbgInlineActionButton(
          label = "导入",
          icon = Icons.Filled.FolderOpen,
          onClick = { dialogMode = "import" },
        )
        NbgInlineActionButton(
          label = "导出",
          icon = Icons.Filled.ContentCopy,
          onClick = {
            val exported = onExport()
            onTemplateChange(exported)
            clipboard.setText(AnnotatedString(exported))
            dialogMode = "export"
          },
        )
        NbgInlineActionButton(
          label = "清空",
          icon = Icons.Filled.Delete,
          onClick = {
            onClear()
            dialogMode = null
          },
        )
      }
      if (message.isNotBlank()) {
        Text(
          text = message,
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      state.profiles.take(8).forEach { profile ->
        val editable = profile.id in editableProfileIds
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .then(
              if (editable) {
                Modifier.clickable { editingDraft = profile.toProviderProfileEditorDraft() }
              } else {
                Modifier
              },
            ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = profile.label,
            color = NbgAgentColors.TextStrong,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.34f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = listOf(profile.apiMode, "thinking".takeIf { profile.supportsThinking }).filterNotNull().joinToString(" · "),
            color = NbgAgentColors.TextMuted,
            fontSize = 10.5.sp,
            modifier = Modifier.weight(0.26f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = profile.baseUrlHint.ifBlank { "custom endpoint" },
            color = NbgAgentColors.CodeText,
            fontSize = 10.5.sp,
            modifier = Modifier.weight(0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (editable) {
            NbgPlainIconButton(
              icon = Icons.Filled.Edit,
              contentDescription = "编辑 ProviderProfile",
              onClick = { editingDraft = profile.toProviderProfileEditorDraft() },
            )
          }
        }
      }
    }
  }
  val mode = dialogMode
  if (mode != null) {
    AlertDialog(
      onDismissRequest = { dialogMode = null },
      containerColor = NbgAgentColors.Drawer,
      title = {
        Text(
          text = if (mode == "import") "导入 ProviderProfile" else "导出 ProviderProfile",
          color = NbgAgentColors.TextStrong,
          fontSize = 18.sp,
        )
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = if (mode == "import") {
              "粘贴只包含 profiles 的 JSON 模板；不会导入 API Key。"
            } else {
              "当前用户 ProviderProfile 模板已写入剪贴板。"
            },
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
          )
          OutlinedTextField(
            value = templateText,
            onValueChange = onTemplateChange,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 220.dp, max = 360.dp),
            minLines = 8,
            maxLines = 16,
            textStyle = TextStyle(
              color = NbgAgentColors.CodeText,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
            ),
            label = { Text("ProviderProfile JSON") },
          )
          if (message.isNotBlank()) {
            Text(
              text = message,
              color = NbgAgentColors.TextMuted,
              fontSize = 11.sp,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (mode == "import") {
              onImport(templateText)
            } else {
              val exported = onExport()
              onTemplateChange(exported)
              clipboard.setText(AnnotatedString(exported))
            }
          },
        ) {
          Text(if (mode == "import") "导入" else "复制", color = NbgAgentColors.Primary)
        }
      },
      dismissButton = {
        TextButton(onClick = { dialogMode = null }) {
          Text("关闭", color = NbgAgentColors.TextMuted)
        }
      },
    )
  }
  editingDraft?.let { draft ->
    NbgProviderProfileEditorDialog(
      draft = draft,
      onDraftChange = { editingDraft = it },
      onSave = { originalId, profile ->
        onSaveProfile(originalId, profile)
        editingDraft = null
      },
      onDelete = { id ->
        onDeleteProfile(id)
        editingDraft = null
      },
      onDismiss = { editingDraft = null },
    )
  }
}

private data class NbgProviderProfileEditorDraft(
  val originalId: String = "",
  val id: String = "",
  val label: String = "",
  val baseUrlHint: String = "",
  val authHeader: String = "Authorization",
  val authPrefix: String = "Bearer ",
  val modelsPath: String = "/v1/models",
  val chatPath: String = "/v1/chat/completions",
  val apiMode: String = "openai",
  val defaultModel: String = "",
  val supportsThinking: Boolean = false,
  val extraBodyJson: String = "",
)

private fun NbgModelProviderProfile.toProviderProfileEditorDraft(): NbgProviderProfileEditorDraft =
  NbgProviderProfileEditorDraft(
    originalId = id,
    id = id,
    label = label,
    baseUrlHint = baseUrlHint,
    authHeader = authHeader,
    authPrefix = authPrefix,
    modelsPath = modelsPath,
    chatPath = chatPath,
    apiMode = apiMode,
    defaultModel = defaultModel,
    supportsThinking = supportsThinking,
    extraBodyJson = extraBodyJson,
  )

private fun NbgProviderProfileEditorDraft.toProviderProfile(): NbgModelProviderProfile =
  NbgModelProviderProfile(
    id = id,
    label = label,
    baseUrlHint = baseUrlHint,
    authHeader = authHeader,
    authPrefix = authPrefix,
    modelsPath = modelsPath,
    chatPath = chatPath,
    apiMode = apiMode,
    defaultModel = defaultModel,
    supportsThinking = supportsThinking,
    extraBodyJson = extraBodyJson,
  )

@Composable
private fun NbgProviderProfileEditorDialog(
  draft: NbgProviderProfileEditorDraft,
  onDraftChange: (NbgProviderProfileEditorDraft) -> Unit,
  onSave: (String, NbgModelProviderProfile) -> Unit,
  onDelete: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var localMessage by remember(draft.originalId) { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = {
      Text(
        text = if (draft.originalId.isBlank()) "新增 ProviderProfile" else "编辑 ProviderProfile",
        color = NbgAgentColors.TextStrong,
        fontSize = 18.sp,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 560.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        NbgProviderProfileTextField("ID", draft.id) { onDraftChange(draft.copy(id = it)); localMessage = "" }
        NbgProviderProfileTextField("名称", draft.label) { onDraftChange(draft.copy(label = it)); localMessage = "" }
        NbgProviderProfileTextField("baseUrlHint", draft.baseUrlHint) { onDraftChange(draft.copy(baseUrlHint = it)); localMessage = "" }
        NbgProviderProfileTextField("authHeader", draft.authHeader) { onDraftChange(draft.copy(authHeader = it)); localMessage = "" }
        NbgProviderProfileTextField("authPrefix", draft.authPrefix) { onDraftChange(draft.copy(authPrefix = it)); localMessage = "" }
        NbgProviderProfileTextField("modelsPath", draft.modelsPath) { onDraftChange(draft.copy(modelsPath = it)); localMessage = "" }
        NbgProviderProfileTextField("chatPath", draft.chatPath) { onDraftChange(draft.copy(chatPath = it)); localMessage = "" }
        NbgProviderProfileTextField("defaultModel", draft.defaultModel) { onDraftChange(draft.copy(defaultModel = it)); localMessage = "" }
        Text(
          text = "API 模式",
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NbgInlineActionButton(
            label = "OpenAI",
            icon = Icons.Filled.CheckCircle,
            primary = draft.apiMode == "openai",
            onClick = { onDraftChange(draft.copy(apiMode = "openai")); localMessage = "" },
          )
          NbgInlineActionButton(
            label = "Anthropic",
            icon = Icons.Filled.CheckCircle,
            primary = draft.apiMode == "anthropic",
            onClick = { onDraftChange(draft.copy(apiMode = "anthropic")); localMessage = "" },
          )
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onDraftChange(draft.copy(supportsThinking = !draft.supportsThinking)); localMessage = "" },
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Checkbox(
            checked = draft.supportsThinking,
            onCheckedChange = { checked ->
              onDraftChange(draft.copy(supportsThinking = checked))
              localMessage = ""
            },
          )
          Text(
            text = "支持 thinking 参数",
            color = NbgAgentColors.TextStrong,
            fontSize = 13.sp,
          )
        }
        OutlinedTextField(
          value = draft.extraBodyJson,
          onValueChange = {
            onDraftChange(draft.copy(extraBodyJson = it))
            localMessage = ""
          },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp),
          minLines = 3,
          maxLines = 8,
          textStyle = TextStyle(
            color = NbgAgentColors.CodeText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
          ),
          label = { Text("extraBodyJson") },
        )
        if (localMessage.isNotBlank()) {
          Text(
            text = localMessage,
            color = NbgAgentColors.ToolMutedDanger,
            fontSize = 12.sp,
            lineHeight = 18.sp,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val profile = draft.toProviderProfile()
          val review = nbgReviewModelProviderProfileTemplate(profile)
          if (review.ok) {
            onSave(draft.originalId, profile)
          } else {
            localMessage = review.message
          }
        },
      ) {
        Text("保存", color = NbgAgentColors.Primary)
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (draft.originalId.isNotBlank()) {
          TextButton(onClick = { onDelete(draft.originalId) }) {
            Text("删除", color = NbgAgentColors.ToolMutedDanger)
          }
        }
        TextButton(onClick = onDismiss) {
          Text("关闭", color = NbgAgentColors.TextMuted)
        }
      }
    },
  )
}

@Composable
private fun NbgProviderProfileTextField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    textStyle = TextStyle(
      color = NbgAgentColors.TextStrong,
      fontSize = 13.sp,
    ),
    label = { Text(label) },
  )
}

@Composable
private fun NbgExpertReviewReadinessCard(
  entries: List<NbgStoredApi>,
  prompt: String,
  selectedKeys: Set<String>,
  running: Boolean,
  message: String,
  result: NbgExpertReviewRunResult?,
  onPromptChange: (String) -> Unit,
  onToggleModel: (NbgExpertReviewModelRef) -> Unit,
  onRun: () -> Unit,
  onCancel: () -> Unit,
) {
  val models = nbgExpertReviewModelRefs(entries)
  val selected = models.filter { it.expertReviewKey in selectedKeys }.ifEmpty { models.take(2) }
  val review = nbgReviewExpertReviewRequest(
    models = selected,
    explicitUserTrigger = true,
  )
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, if (review.allowStart) NbgAgentColors.PrimarySoft else NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(13.dp),
          color = if (review.allowStart) NbgAgentColors.PrimarySoft else NbgAgentColors.SurfaceLow,
          contentColor = if (review.allowStart) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
        ) {
          Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.padding(9.dp),
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Expert Review",
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = review.reason,
            color = if (review.allowStart) NbgAgentColors.TextMuted else NbgAgentColors.StatusRed,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = NbgAgentColors.SurfaceLow,
          border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
        ) {
          Text(
            text = "${review.modelCount} 模型",
            color = if (review.allowStart) NbgAgentColors.StatusGreen else NbgAgentColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
          )
        }
      }
      Text(
        text = "只读 · 默认关闭 · 分开展示参考输出 · 最终必须汇总。${review.costLatencyWarning}",
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
      )
      if (models.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
          models.take(8).forEach { model ->
            NbgExpertReviewModelPickRow(
              model = model,
              selected = model in selected,
              enabled = !running,
              onToggle = { onToggleModel(model) },
            )
          }
        }
      }
      OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChange,
        label = { Text("评审问题") },
        placeholder = { Text("输入需要多个模型只读评审的问题") },
        minLines = 2,
        maxLines = 5,
        enabled = !running,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NbgInlineActionButton(
          label = if (running) "评审中" else "运行评审",
          icon = Icons.Filled.AutoAwesome,
          primary = true,
          enabled = review.allowStart && prompt.trim().isNotBlank() && !running,
          onClick = onRun,
        )
        if (running) {
          NbgInlineActionButton(
            label = "取消",
            icon = Icons.Filled.Stop,
            enabled = true,
            onClick = onCancel,
          )
        }
        if (message.isNotBlank()) {
          Text(
            text = message,
            color = if (message.contains("失败")) NbgAgentColors.StatusRed else NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f),
          )
        }
      }
      result?.let {
        NbgExpertReviewResultPanel(result = it)
      }
    }
  }
}

@Composable
private fun NbgExpertReviewModelPickRow(
  model: NbgExpertReviewModelRef,
  selected: Boolean,
  enabled: Boolean,
  onToggle: () -> Unit,
) {
  Surface(
    onClick = onToggle,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = if (selected) NbgAgentColors.Selected else NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, if (selected) NbgAgentColors.PrimarySoft else NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.AutoAwesome,
        contentDescription = null,
        tint = if (selected) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
        modifier = Modifier.size(18.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = model.modelLabel,
          color = NbgAgentColors.TextStrong,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = model.providerName,
          color = NbgAgentColors.TextMuted,
          fontSize = 10.5.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun NbgExpertReviewResultPanel(result: NbgExpertReviewRunResult) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(13.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = result.consolidatedSummary,
        color = NbgAgentColors.TextStrong,
        fontSize = 12.sp,
        lineHeight = 17.sp,
      )
      result.references.forEach { reference ->
        Text(
          text = "${reference.model.modelLabel} · ${if (reference.ok) "完成" else "失败"}",
          color = if (reference.ok) NbgAgentColors.Primary else NbgAgentColors.StatusRed,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = if (reference.ok) reference.output else reference.error,
          color = NbgAgentColors.TextMuted,
          fontSize = 10.5.sp,
          lineHeight = 15.sp,
          maxLines = 5,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
internal fun NbgUrlApiEmptyState(onAdd: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "还没有保存的网址 API",
        color = NbgAgentColors.TextStrong,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "点击右上角加号，输入上游地址和 API Key，获取模型并验证可用后保存。",
        color = NbgAgentColors.TextMuted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
      )
      NbgInlineActionButton(
        label = "添加",
        icon = Icons.Filled.Add,
        primary = true,
        onClick = onAdd,
      )
    }
  }
}

@Composable
internal fun NbgUrlApiEntryRow(
  entry: NbgStoredApi,
  onClick: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .background(
            if (entry.verifiedCount > 0) NbgAgentColors.PrimarySoft else NbgAgentColors.SurfaceLow,
            RoundedCornerShape(13.dp),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (entry.verifiedCount > 0) Icons.Filled.CheckCircle else Icons.Filled.Settings,
          contentDescription = null,
          tint = if (entry.verifiedCount > 0) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
          modifier = Modifier.size(20.dp),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = entry.name,
          color = NbgAgentColors.TextStrong,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = entry.baseUrl,
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${entry.models.size} 模型 / ${entry.verifiedCount} 可用 / ${nbgMaskedApiKey(entry.apiKey)}",
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      NbgPlainIconButton(
        icon = Icons.Filled.Delete,
        contentDescription = "删除网址 API",
        onClick = onDelete,
      )
    }
  }
}

@Composable
internal fun NbgUrlApiEditorDialog(
  title: String,
  name: String,
  baseUrl: String,
  apiKey: String,
  models: List<NbgApiModel>,
  verifiedModelIds: Set<String>,
  selectedModelId: String,
  busy: Boolean,
  message: String?,
  onNameChange: (String) -> Unit,
  onBaseUrlChange: (String) -> Unit,
  onApiKeyChange: (String) -> Unit,
  onSelectModel: (String) -> Unit,
  onFetchModels: () -> Unit,
  onVerifyModel: (NbgApiModel) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
) {
  val canFetch = baseUrl.trim().isNotBlank() && apiKey.trim().isNotBlank() && !busy
  val canSave = canFetch && verifiedModelIds.isNotEmpty()
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text(title, color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 620.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = onNameChange,
          label = { Text("名称") },
          placeholder = { Text("例如 DeepSeek / NBG API") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = baseUrl,
          onValueChange = onBaseUrlChange,
          label = { Text("网址") },
          placeholder = { Text("https://api.example.com") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
          modifier = Modifier.fillMaxWidth(),
        )
        NbgApiKeyField(
          value = apiKey,
          onValueChange = onApiKeyChange,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          NbgInlineActionButton(
            label = if (busy) "处理中" else "获取模型",
            icon = Icons.Filled.Refresh,
            enabled = canFetch,
            onClick = onFetchModels,
          )
          message?.takeIf { it.isNotBlank() }?.let {
            Text(
              text = it,
              color = if (it.contains("失败")) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
              fontSize = 12.sp,
              lineHeight = 17.sp,
              modifier = Modifier.weight(1f),
            )
          }
        }
        if (models.isNotEmpty()) {
          NbgDialogSectionLabel("上游模型")
          models.take(80).forEach { model ->
            NbgUrlApiModelRow(
              model = model,
              selected = model.id == selectedModelId,
              verified = model.id in verifiedModelIds,
              busy = busy,
              onSelect = { onSelectModel(model.id) },
              onVerify = { onVerifyModel(model) },
            )
          }
        }
      }
    },
    confirmButton = {
      NbgDialogAction(label = "保存", primary = true, enabled = canSave, onClick = onSave)
    },
    dismissButton = {
      NbgDialogAction(label = "关闭", onClick = onDismiss)
    },
  )
}

@Composable
internal fun NbgApiKeyField(
  value: String,
  onValueChange: (String) -> Unit,
) {
  var visible by remember { mutableStateOf(false) }
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text("API Key") },
    placeholder = { Text("sk-...") },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    trailingIcon = {
      NbgPlainIconButton(
        icon = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
        contentDescription = if (visible) "隐藏 API Key" else "显示 API Key",
        onClick = { visible = !visible },
      )
    },
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
internal fun NbgUrlApiModelRow(
  model: NbgApiModel,
  selected: Boolean,
  verified: Boolean,
  busy: Boolean,
  onSelect: () -> Unit,
  onVerify: () -> Unit,
) {
  Surface(
    onClick = onSelect,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(13.dp),
    color = if (selected) NbgAgentColors.Selected else NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = if (verified) Icons.Filled.CheckCircle else Icons.Filled.AutoAwesome,
        contentDescription = null,
        tint = if (verified) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
        modifier = Modifier.size(18.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = model.label,
          color = NbgAgentColors.TextStrong,
          fontSize = 13.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = if (verified) "已验证可用" else "未验证",
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          maxLines = 1,
        )
      }
      NbgInlineActionButton(
        label = if (verified) "重验" else "验证",
        icon = Icons.Filled.CheckCircle,
        enabled = !busy,
        onClick = onVerify,
      )
    }
  }
}

@Composable
internal fun NbgInlineActionButton(
  label: String,
  icon: ImageVector,
  primary: Boolean = false,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val backgroundColor = when {
    !enabled -> NbgAgentColors.SurfaceLow
    primary -> NbgAgentColors.PrimaryContainer
    else -> NbgAgentColors.SurfaceContainer
  }
  val foregroundColor = when {
    !enabled -> NbgAgentColors.TextDisabled
    primary -> NbgAgentColors.TextStrong
    else -> NbgAgentColors.TextMuted
  }
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(13.dp),
    color = backgroundColor,
    contentColor = foregroundColor,
    border = BorderStroke(1.dp, if (primary) NbgAgentColors.SurfaceBorder else NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier
        .height(36.dp)
        .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier = Modifier
          .size(22.dp)
          .background(if (primary) NbgAgentColors.Selected else NbgAgentColors.GlassButton, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
      }
      Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
  }
}

@Composable
internal fun NbgProvidersDialog(
  snapshot: HanakoProviderSnapshot?,
  loading: Boolean,
  error: String?,
  onReload: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("网址 API", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 560.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        when {
          loading && snapshot == null -> Text(
            text = "正在读取 HanakoPro 网址 API 状态...",
            color = NbgAgentColors.TextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
          )
          error != null && snapshot == null -> Text(
            text = "读取失败：$error",
            color = NbgAgentColors.TextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
          )
        }
        snapshot?.let { current ->
          NbgProviderSummaryRow(current)
          NbgDialogSectionLabel("已配置")
          val configured = current.providers.filter { it.configStatus == "ok" || it.hasCredentials }
          if (configured.isEmpty()) {
            NbgProviderEmptyText("还没有已配置的网址 API。")
          } else {
            configured.forEach { provider -> NbgProviderRow(provider) }
          }

          NbgDialogSectionLabel("需要配置")
          val needsSetup = current.providers.filter { it.configStatus != "ok" && !it.hasCredentials }
          if (needsSetup.isEmpty()) {
            NbgProviderEmptyText("没有需要配置的网址 API。")
          } else {
            needsSetup.take(20).forEach { provider -> NbgProviderRow(provider) }
          }
          if (error != null) {
            Text(
              text = "刷新失败：$error",
              color = NbgAgentColors.TextMuted,
              fontSize = 12.sp,
              lineHeight = 17.sp,
            )
          }
        }
      }
    },
    confirmButton = {
      NbgDialogAction(label = if (loading) "读取中" else "刷新", enabled = !loading, onClick = onReload)
    },
    dismissButton = {
      NbgDialogAction(label = "关闭", primary = true, onClick = onDismiss)
    },
  )
}

@Composable
internal fun NbgProviderSummaryRow(snapshot: HanakoProviderSnapshot) {
  val okCount = snapshot.providers.count { it.configStatus == "ok" }
  val credentialCount = snapshot.providers.count { it.hasCredentials }
  Surface(
    color = NbgAgentColors.SurfaceLow,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .background(NbgAgentColors.PrimarySoft, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Filled.Settings, contentDescription = null, tint = NbgAgentColors.Primary, modifier = Modifier.size(18.dp))
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "HanakoPro 网址 API",
          color = NbgAgentColors.TextStrong,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "$okCount ok / $credentialCount 有凭证 / ${snapshot.modelCount} 模型 / ${snapshot.activeProvider.ifBlank { "未选择" }}",
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
internal fun NbgProviderRow(provider: HanakoProviderSummary) {
  val ok = provider.configStatus == "ok"
  Surface(
    color = if (ok) NbgAgentColors.Selected else NbgAgentColors.SurfaceLow,
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 62.dp)
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .background(NbgAgentColors.PrimarySoft, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Block,
          contentDescription = null,
          tint = if (ok) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
          modifier = Modifier.size(18.dp),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = provider.displayName,
          color = NbgAgentColors.TextStrong,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = nbgProviderMeta(provider),
          color = NbgAgentColors.TextMuted,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (provider.configError.isNotBlank()) {
          Text(
            text = provider.configError,
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Text(
        text = nbgProviderStatusLabel(provider.configStatus),
        color = if (ok) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
      )
    }
  }
}

@Composable
internal fun NbgProviderEmptyText(text: String) {
  Text(
    text = text,
    color = NbgAgentColors.TextMuted,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    modifier = Modifier
      .fillMaxWidth()
      .background(NbgAgentColors.SurfaceLow, RoundedCornerShape(12.dp))
      .padding(horizontal = 10.dp, vertical = 9.dp),
  )
}

internal fun nbgProviderMeta(provider: HanakoProviderSummary): String =
  listOfNotNull(
    provider.id,
    nbgProviderTypeLabel(provider.type).takeIf { it.isNotBlank() },
    nbgProviderAuthLabel(provider.authType).takeIf { it.isNotBlank() },
    provider.api.takeIf { it.isNotBlank() },
    "${provider.modelCount + provider.customModelCount} 个模型",
    "凭证".takeIf { provider.hasCredentials },
    "OAuth".takeIf { provider.supportsOauth },
    "已登录".takeIf { provider.loggedIn == true },
    provider.missingFields
      .takeIf { it.isNotEmpty() }
      ?.joinToString(prefix = "缺少: ", separator = "、") { nbgProviderFieldLabel(it) },
  ).joinToString(" / ")

internal fun nbgProviderFieldLabel(value: String): String =
  when (value) {
    "api_key" -> "密钥"
    "base_url" -> "服务地址"
    "models" -> "模型"
    "oauth" -> "OAuth"
    "access_token" -> "访问令牌"
    "refresh_token" -> "刷新令牌"
    else -> value.replace('_', ' ')
  }

internal fun nbgProviderTypeLabel(value: String): String =
  when (value) {
    "api-key" -> "密钥 API"
    "oauth" -> "OAuth API"
    else -> value
  }

internal fun nbgProviderAuthLabel(value: String): String =
  when (value) {
    "api-key" -> "密钥认证"
    "oauth" -> "OAuth"
    "none" -> "无认证"
    else -> value
  }

internal fun nbgProviderStatusLabel(value: String): String =
  when (value) {
    "ok" -> "可用"
    "needs_setup" -> "待配置"
    "invalid" -> "错误"
    "" -> "未知"
    else -> value
  }

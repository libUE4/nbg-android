package com.nbg.android

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nbg.android.terminal.AndroidTerminalClipboard
import com.nbg.android.terminal.EmbeddedTerminalManager
import com.nbg.android.terminal.TerminalEnvironment
import com.nbg.android.terminal.TerminalInputModifiers
import com.nbg.android.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor
import com.termux.terminal.TerminalSession as TermuxTerminalSession

class MainActivity : ComponentActivity() {
  private var gatewayInboxRefreshToken by mutableStateOf(0L)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val initialPageName = initialPageNameForIntent(intent)
    HanakoBackgroundWarmup.start(this)
    HanakoForegroundServiceController.start(this)
    NbgScheduleWorkManager.ensureScheduled(this)
    NbgSkillCuratorLoopWorkManager.ensureScheduled(this)
    setContent {
      NbgAndroidApp(
        initialPageName = initialPageName,
        gatewayInboxRefreshToken = gatewayInboxRefreshToken,
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (nbgRecordGatewayShareIntent(this, intent) != null) {
      gatewayInboxRefreshToken = System.currentTimeMillis()
    }
  }

  private fun initialPageNameForIntent(intent: Intent?): String? {
    val explicit = intent?.getStringExtra(NBG_START_PAGE_EXTRA)
    if (!explicit.isNullOrBlank()) return explicit
    if (nbgRecordGatewayShareIntent(this, intent) != null) {
      gatewayInboxRefreshToken = System.currentTimeMillis()
      return "learning"
    }
    return null
  }
}

@Composable
private fun NbgAndroidApp(
  initialPageName: String? = null,
  gatewayInboxRefreshToken: Long = 0L,
) {
  val context = LocalContext.current
  var appearance by remember(context) { mutableStateOf(NbgChatPreferenceStore(context).load()) }
  LaunchedEffect(context) {
    appearance = NbgChatPreferenceStore(context).load()
  }
  val palette = nbgThemePalette(appearance.themeId)
  val font = nbgFontOption(appearance.fontId)
  LaunchedEffect(palette) {
    val window = (context as? MainActivity)?.window
    window?.statusBarColor = palette.background.toArgb()
    window?.navigationBarColor = palette.background.toArgb()
  }
  NbgThemeProvider(
    themeId = appearance.themeId,
    fontId = appearance.fontId,
  ) {
    MaterialTheme(
      colorScheme = nbgMaterialColorScheme(palette),
      typography = nbgTypography(font),
    ) {
      NbgAndroidShell(
        onAppearanceChanged = { next -> appearance = next },
        initialPageName = initialPageName,
        gatewayInboxRefreshToken = gatewayInboxRefreshToken,
      ) { onBack ->
        TerminalScreen(
          onBack = onBack,
          onTerminalReady = {},
        )
      }
    }
  }
}

@Composable
private fun TerminalScreen(
  onBack: () -> Unit,
  onTerminalReady: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val workspace = TerminalWorkspace.shared
  var workspaceVersion by remember { mutableStateOf(0) }
  val observedWorkspaceVersion = workspaceVersion
  val tabs = workspace.tabs
  val selectedTabIndex = workspace.selectedIndex
  val sessions = workspace.sessions
  val startInProgress = workspace.startInProgress
  var topChromeHeightPx by remember { mutableStateOf(0) }
  var bottomChromeHeightPx by remember { mutableStateOf(0) }
  var scrollbackEnabled by remember { mutableStateOf(true) }
  var extraKeyModifiers by remember { mutableStateOf(TerminalInputModifiers()) }
  var renameTabIndex by remember { mutableStateOf<Int?>(null) }
  var renameOriginalTitle by remember { mutableStateOf("") }
  var renameText by remember { mutableStateOf("") }
  var settingsOpen by remember { mutableStateOf(false) }
  val fontScaleController = remember { TerminalFontScaleController(initialFontSizeSp = 10f) }
  val active = sessions.getOrNull(selectedTabIndex)
  val fontSizeSp = fontScaleController.fontSizeSp

  fun refreshTabsState() {
    workspaceVersion += 1
  }

  fun replaceSession(index: Int, nextSession: TerminalSession) {
    workspace.setSession(index, nextSession)
    refreshTabsState()
  }

  val startTerminalForSelected = {
    val index = selectedTabIndex
    if (!startInProgress.getOrElse(index) { false } && sessions.getOrNull(index) == null) {
      val startupToken = workspace.beginStartup(index)
      scope.launch {
        val token = startupToken ?: return@launch
        val startingIndex = workspace.startupIndexForToken(token) ?: return@launch
        workspace.updateTabStatus(startingIndex, "启动中")
        refreshTabsState()
        runCatching {
          val manager = EmbeddedTerminalManager(
            TerminalEnvironment(context),
            AndroidTerminalClipboard(context),
          )
          val config = withContext(Dispatchers.IO) {
            manager.prepareLocalUbuntuSessionConfig()
          }
          manager.createLocalUbuntuSession(config)
        }.onSuccess { session ->
          val currentIndex = workspace.startupIndexForToken(token)
          if (currentIndex == null || workspace.sessions.getOrNull(currentIndex) != null) {
            session.close()
            return@onSuccess
          }
          replaceSession(currentIndex, session)
          workspace.updateTabStatus(currentIndex, "进入中")
          refreshTabsState()
        }.onFailure {
          Log.e("NBG_TERMINAL", "Failed to start terminal", it)
          val currentIndex = workspace.startupIndexForToken(token)
          if (currentIndex != null) {
            workspace.startupControllers[currentIndex]?.markStartFailed()
            workspace.markStartupFailed(currentIndex, it.message.orEmpty().ifBlank { it::class.java.simpleName })
            workspace.updateTabStatus(currentIndex, "失败")
            refreshTabsState()
          }
        }
        workspace.startupIndexForToken(token)?.let { currentIndex ->
          workspace.finishStartup(currentIndex, token)
        }
        refreshTabsState()
      }
    }
  }

  TerminalReadinessObservers(
    sessions = sessions,
    onReadinessChanged = { index, session, output ->
      val snapshot = workspace.recordReadinessOutput(index, session, output) ?: return@TerminalReadinessObservers
      if (snapshot.readiness == TerminalReadiness.Ready) {
        onTerminalReady()
      }
      refreshTabsState()
    },
  )

  LaunchedEffect(selectedTabIndex, active, startInProgress) {
    val startupController = workspace.startupControllers.getOrPut(selectedTabIndex) {
      TerminalStartupController()
    }
    if (
      startupController.shouldStartAutomatically(
        hasSession = active != null,
        startInProgress = startInProgress.getOrElse(selectedTabIndex) { false },
        hasViewportMetrics = true,
      )
    ) {
      startTerminalForSelected()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .onSizeChanged { size ->
        Log.d(
          "NBG_LAYOUT",
          "root width=${size.width} height=${size.height} topChrome=$topChromeHeightPx bottomChrome=$bottomChromeHeightPx",
        )
      }
      .background(TerminalColors.Background),
  ) {
    TerminalTabsBar(
      tabs = tabs,
      selectedIndex = selectedTabIndex,
      onHeightChanged = { topChromeHeightPx = it },
      onBack = onBack,
      onSelect = { workspace.selectTab(it); refreshTabsState() },
      onAddTab = {
        workspace.addTab()
        refreshTabsState()
      },
      onRenameTab = { index ->
        renameTabIndex = index
        renameOriginalTitle = workspace.tabTitleOrNull(index).orEmpty()
        renameText = renameOriginalTitle
      },
      onDeleteTab = { index ->
        workspace.selectTab(index)
        workspace.deleteSelectedTab()?.close()
        refreshTabsState()
      },
    )
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(TerminalColors.Terminal)
        .onSizeChanged { size ->
          Log.d(
            "NBG_LAYOUT",
            "terminal width=${size.width} height=${size.height}",
          )
        },
    ) {
      AnimatedContent(
        targetState = active,
        transitionSpec = {
          (
            fadeIn(
              animationSpec = tween(
                durationMillis = motionDuration(TerminalMotion.PanelMs),
                easing = FastOutSlowInEasing,
              ),
            ) + slideInVertically(
              animationSpec = tween(
                durationMillis = motionDuration(TerminalMotion.PanelMs),
                easing = FastOutSlowInEasing,
              ),
              initialOffsetY = { height -> height / 10 },
            )
            ).togetherWith(
            fadeOut(animationSpec = tween(durationMillis = motionDuration(TerminalMotion.RouteExitMs))) +
              slideOutVertically(
                animationSpec = tween(durationMillis = motionDuration(TerminalMotion.RouteExitMs)),
                targetOffsetY = { height -> -height / 10 },
              ),
          )
        },
        modifier = Modifier.fillMaxSize(),
        label = "terminal session transition",
      ) { terminalSession ->
        if (terminalSession == null) {
          TerminalEmptySessionPanel(
            status = tabs.getOrNull(selectedTabIndex)?.status.orEmpty(),
            startInProgress = startInProgress.getOrElse(selectedTabIndex) { false },
            onRetry = {
              workspace.startupControllers[selectedTabIndex]?.markStartFailed()
              startTerminalForSelected()
              refreshTabsState()
            },
            modifier = Modifier
              .align(Alignment.CenterStart),
          )
        } else {
          TermuxTerminalViewHost(
            session = terminalSession,
            fontSizeSp = fontSizeSp,
            modifiers = extraKeyModifiers,
            scrollbackEnabled = scrollbackEnabled,
            onOpenSettings = { settingsOpen = true },
            onScale = { scale ->
              fontScaleController.applyZoom(scale)
              1f
            },
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
    TermuxExtraKeysBar(
      session = active,
      modifiers = extraKeyModifiers,
      scrollbackEnabled = scrollbackEnabled,
      onModifiersChange = { extraKeyModifiers = it },
      onOpenDrawer = { settingsOpen = true },
      onToggleScroll = { scrollbackEnabled = !scrollbackEnabled },
      onHeightChanged = { bottomChromeHeightPx = it },
    )
  }

  renameTabIndex?.let { index ->
    RenameTabDialog(
      value = renameText,
      onValueChange = { renameText = it },
      onDismiss = {
        renameTabIndex = null
        renameOriginalTitle = ""
      },
      onConfirm = {
        if (workspace.tabTitleOrNull(index) == renameOriginalTitle) {
          workspace.renameTab(index, renameText)
        }
        renameTabIndex = null
        renameOriginalTitle = ""
        refreshTabsState()
      },
    )
  }

  if (settingsOpen) {
    TerminalSettingsDialog(
      fontSizeSp = fontSizeSp,
      onIncreaseFont = fontScaleController::increase,
      onDecreaseFont = fontScaleController::decrease,
      onDismiss = { settingsOpen = false },
    )
  }

  @Suppress("UNUSED_EXPRESSION")
  observedWorkspaceVersion
}

@Composable
private fun TerminalEmptySessionPanel(
  status: String,
  startInProgress: Boolean,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val failed = status == "失败"
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.padding(horizontal = 12.dp),
  ) {
    Text(
      text = if (failed) "Ubuntu 终端启动失败" else "正在安装并进入 Ubuntu 终端...",
      color = if (failed) TerminalColors.Destructive else TerminalColors.TextMuted,
      fontSize = 13.sp,
      fontWeight = if (failed) FontWeight.SemiBold else FontWeight.Normal,
    )
    if (failed) {
      Text(
        text = "请检查 Ubuntu 环境或重新启动终端。",
        color = TerminalColors.TextMuted,
        fontSize = 11.sp,
      )
      TerminalTextAction(
        label = if (startInProgress) "启动中" else "重试",
        onClick = onRetry,
      )
    }
  }
}

@Composable
private fun TerminalReadinessObservers(
  sessions: List<TerminalSession?>,
  onReadinessChanged: (index: Int, session: TerminalSession, output: String) -> Unit,
) {
  sessions.forEachIndexed { index, session ->
    if (session != null) {
      val output by session.screenText.collectAsState(initial = "")
      LaunchedEffect(index, session, output) {
        if (output.isNotEmpty()) {
          onReadinessChanged(index, session, output)
        }
      }
    }
  }
}

@Composable
private fun TermuxTerminalViewHost(
  session: TerminalSession,
  fontSizeSp: Float,
  modifiers: TerminalInputModifiers,
  scrollbackEnabled: Boolean,
  onOpenSettings: () -> Unit,
  onScale: (Float) -> Float,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val fontSizePx = with(density) { fontSizeSp.sp.toPx().toInt().coerceAtLeast(1) }
  val viewClient = remember(context) { NbgTerminalViewClient(context) }
  val scrollFollowController = remember { TerminalScrollFollowController() }
  viewClient.modifiers = modifiers
  viewClient.onScaleChanged = onScale

  AndroidView(
    modifier = modifier,
    factory = {
      TerminalView(context, null).apply {
        setBackgroundColor(AndroidColor.rgb(2, 3, 5))
        setTerminalViewClient(viewClient)
        viewClient.terminalView = this
        setTextSize(fontSizePx)
        attachSession(session.termuxSession)
        setTerminalCursorBlinkerRate(700)
        setFocusableInTouchMode(true)
        installTerminalContextMenu(this, onOpenSettings)
      }
    },
    update = { view ->
      viewClient.terminalView = view
      viewClient.modifiers = modifiers
      viewClient.onScaleChanged = onScale
      view.setTerminalViewClient(viewClient)
      view.setTextSize(fontSizePx)
      view.attachSession(session.termuxSession)
      installTerminalContextMenu(view, onOpenSettings)
      session.setScreenChangedListener {
        scrollFollowController.onScreenUpdated(
          TermuxScrollViewport(view),
          followOutput = scrollbackEnabled,
        )
      }
    },
  )

  DisposableEffect(session) {
    onDispose {
      session.setScreenChangedListener(null)
    }
  }
}

private fun installTerminalContextMenu(
  view: TerminalView,
  onOpenSettings: () -> Unit,
) {
  view.setOnCreateContextMenuListener { menu, _, _ ->
    menu.add("设置").setOnMenuItemClickListener {
      onOpenSettings()
      true
    }
  }
}

private class TermuxScrollViewport(
  private val view: TerminalView,
) : TerminalScrollViewport {
  override val topRow: Int
    get() = view.getTopRow()

  override val scrollCounter: Int
    get() = view.mEmulator?.getScrollCounter() ?: 0

  override fun setTopRow(topRow: Int) {
    view.setTopRow(topRow)
  }

  override fun onScreenUpdated() {
    view.onScreenUpdated()
  }

  override fun invalidate() {
    view.invalidate()
  }
}

private class NbgTerminalViewClient(
  private val context: Context,
) : TerminalViewClient {
  var terminalView: TerminalView? = null
  var modifiers: TerminalInputModifiers = TerminalInputModifiers()
  var onScaleChanged: (Float) -> Float = { it }

  override fun onScale(scale: Float): Float = onScaleChanged(scale)

  override fun onSingleTapUp(e: MotionEvent) {
    val view = terminalView ?: return
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
  }

  override fun shouldBackButtonBeMappedToEscape(): Boolean = false

  override fun shouldEnforceCharBasedInput(): Boolean = true

  override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

  override fun isTerminalViewSelected(): Boolean = terminalView?.hasFocus() == true

  override fun copyModeChanged(copyMode: Boolean) = Unit

  override fun onKeyDown(
    keyCode: Int,
    e: KeyEvent,
    session: TermuxTerminalSession,
  ): Boolean = false

  override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

  override fun onLongPress(event: MotionEvent): Boolean = false

  override fun readControlKey(): Boolean = modifiers.ctrl

  override fun readAltKey(): Boolean = modifiers.alt

  override fun readShiftKey(): Boolean = modifiers.shift

  override fun readFnKey(): Boolean = false

  override fun onCodePoint(
    codePoint: Int,
    ctrlDown: Boolean,
    session: TermuxTerminalSession,
  ): Boolean = false

  override fun onEmulatorSet() {
    terminalView?.setTerminalCursorBlinkerState(true, true)
  }

  override fun logError(tag: String, message: String) {
    Log.e(tag, message)
  }

  override fun logWarn(tag: String, message: String) {
    Log.w(tag, message)
  }

  override fun logInfo(tag: String, message: String) {
    Log.i(tag, message)
  }

  override fun logDebug(tag: String, message: String) {
    Log.d(tag, message)
  }

  override fun logVerbose(tag: String, message: String) {
    Log.v(tag, message)
  }

  override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
    Log.e(tag, message, e)
  }

  override fun logStackTrace(tag: String, e: Exception) {
    Log.e(tag, "Terminal view error", e)
  }
}

@Composable
private fun TerminalTabsBar(
  tabs: List<TerminalTab>,
  selectedIndex: Int,
  onHeightChanged: (Int) -> Unit,
  onBack: () -> Unit,
  onSelect: (Int) -> Unit,
  onAddTab: () -> Unit,
  onRenameTab: (Int) -> Unit,
  onDeleteTab: (Int) -> Unit,
) {
  val scrollState = rememberScrollState()
  Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .height(42.dp)
      .onSizeChanged { onHeightChanged(it.height) }
      .background(TerminalColors.Chrome)
      .horizontalScroll(scrollState)
      .padding(horizontal = 8.dp, vertical = 5.dp),
  ) {
    TerminalPressBox(
      onClick = onBack,
      modifier = Modifier
        .size(30.dp)
        .background(TerminalColors.KeyDisabled, RoundedCornerShape(4.dp))
        .semantics { contentDescription = "返回聊天" },
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          tint = TerminalColors.TextStrong,
          modifier = Modifier.size(17.dp),
        )
      }
    }
    tabs.forEachIndexed { index, tab ->
      TerminalTabChip(
        tab = tab,
        selected = index == selectedIndex,
        onClick = { onSelect(index) },
        onRename = { onRenameTab(index) },
        onDelete = { onDeleteTab(index) },
      )
    }
    ExtraTextKey("+", enabled = true, active = false, width = 44.dp, height = 30.dp, onClick = onAddTab)
  }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TerminalTabChip(
  tab: TerminalTab,
  selected: Boolean,
  onClick: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val containerColor by animateColorAsState(
    targetValue = if (selected) TerminalColors.Key else TerminalColors.KeyDisabled,
    animationSpec = tween(
      durationMillis = motionDuration(TerminalMotion.PanelMs),
      easing = FastOutSlowInEasing,
    ),
    label = "terminal tab container",
  )
  val iconTint by animateColorAsState(
    targetValue = if (selected) TerminalColors.Accent else TerminalColors.TextMuted,
    animationSpec = tween(durationMillis = motionDuration(TerminalMotion.PanelMs)),
    label = "terminal tab icon",
  )
  val pressScale by animateFloatAsState(
    targetValue = if (isPressed) 0.965f else 1f,
    animationSpec = tween(
      durationMillis = motionDuration(if (isPressed) TerminalMotion.PressMs else TerminalMotion.PressReleaseMs),
      easing = FastOutSlowInEasing,
    ),
    label = "terminal tab press",
  )
  Box {
    Row(
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .height(30.dp)
        .width(128.dp)
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        }
        .background(containerColor, RoundedCornerShape(4.dp))
        .combinedClickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
          onLongClick = { menuExpanded = true },
        )
        .semantics { contentDescription = tab.title },
    ) {
      Icon(
        Icons.Filled.Terminal,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier
          .padding(start = 8.dp)
          .size(15.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = tab.title,
          color = TerminalColors.TextStrong,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = tab.status,
          color = TerminalColors.TextMuted,
          fontSize = 9.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    DropdownMenu(
      expanded = menuExpanded,
      onDismissRequest = { menuExpanded = false },
    ) {
      DropdownMenuItem(
        text = { Text("重命名") },
        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        onClick = {
          menuExpanded = false
          onRename()
        },
      )
      DropdownMenuItem(
        text = { Text("删除", color = TerminalColors.Destructive) },
        leadingIcon = {
          Icon(Icons.Filled.Delete, contentDescription = null, tint = TerminalColors.Destructive)
        },
        onClick = {
          menuExpanded = false
          onDelete()
        },
      )
    }
  }
}

@Composable
private fun RenameTabDialog(
  value: String,
  onValueChange: (String) -> Unit,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("重命名终端") },
    text = {
      OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text("名称") },
      )
    },
    confirmButton = {
      TerminalTextAction(label = "重命名", onClick = onConfirm)
    },
    dismissButton = {
      TerminalTextAction(label = "取消", onClick = onDismiss)
    },
  )
}

@Composable
private fun TerminalSettingsDialog(
  fontSizeSp: Float,
  onIncreaseFont: () -> Unit,
  onDecreaseFont: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("终端设置") },
    text = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("字号", color = TerminalColors.TextStrong)
        TerminalTextAction(label = "-", onClick = onDecreaseFont)
        Text(fontSizeSp.toInt().toString(), color = TerminalColors.TextStrong)
        TerminalTextAction(label = "+", onClick = onIncreaseFont)
      }
    },
    confirmButton = {
      TerminalTextAction(label = "完成", onClick = onDismiss)
    },
  )
}

@Composable
private fun TerminalTextAction(
  label: String,
  onClick: () -> Unit,
) {
  TerminalPressBox(
    onClick = onClick,
    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
      Text(
        text = label,
        color = TerminalColors.Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

@Composable
private fun TerminalPressBox(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (enabled && isPressed) 0.96f else 1f,
    animationSpec = tween(
      durationMillis = motionDuration(if (isPressed) TerminalMotion.PressMs else TerminalMotion.PressReleaseMs),
      easing = FastOutSlowInEasing,
    ),
    label = "terminal press",
  )
  Box(
    modifier = modifier
      .graphicsLayer {
        scaleX = pressScale
        scaleY = pressScale
      }
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

@Composable
private fun TermuxExtraKeysBar(
  session: TerminalSession?,
  modifiers: TerminalInputModifiers,
  scrollbackEnabled: Boolean,
  onModifiersChange: (TerminalInputModifiers) -> Unit,
  onOpenDrawer: () -> Unit,
  onToggleScroll: () -> Unit,
  onHeightChanged: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val enabled = session != null
  Column(
    verticalArrangement = Arrangement.spacedBy(5.dp),
    modifier = modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .onSizeChanged { size ->
        onHeightChanged(size.height)
        Log.d("NBG_LAYOUT", "extraKeys width=${size.width} height=${size.height}")
      }
      .background(TerminalColors.Chrome)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    TerminalExtraKeysLayout.rows.forEach { row ->
      Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        row.forEach { key ->
          val isActive = when (key.id) {
            TerminalExtraKeyId.Shift -> modifiers.shift
            TerminalExtraKeyId.Ctrl -> modifiers.ctrl
            TerminalExtraKeyId.Alt -> modifiers.alt
            TerminalExtraKeyId.Scroll -> scrollbackEnabled
            else -> false
          }
          ExtraTextKey(
            label = key.display,
            icon = key.icon?.imageVector(),
            enabled = enabled || key.id == TerminalExtraKeyId.Drawer || key.id == TerminalExtraKeyId.Scroll,
            active = isActive,
            width = 0.dp,
            modifier = Modifier.weight(1f),
          ) {
            val resolution = TerminalExtraKeyActionResolver.resolve(key.id, modifiers)
            when (val command = resolution.command) {
              TerminalExtraKeyCommand.OpenDrawer -> onOpenDrawer()
              TerminalExtraKeyCommand.ToggleScroll -> onToggleScroll()
              TerminalExtraKeyCommand.SendEscape -> session?.sendEscape()
              is TerminalExtraKeyCommand.SendTab -> session?.sendTab(command.modifiers)
              is TerminalExtraKeyCommand.SendHome -> session?.sendHome(command.modifiers)
              is TerminalExtraKeyCommand.SendUp -> session?.sendArrowUp(command.modifiers)
              is TerminalExtraKeyCommand.SendEnd -> session?.sendEnd(command.modifiers)
              is TerminalExtraKeyCommand.SendPageUp -> session?.sendPageUp(command.modifiers)
              is TerminalExtraKeyCommand.SendLeft -> session?.sendArrowLeft(command.modifiers)
              is TerminalExtraKeyCommand.SendDown -> session?.sendArrowDown(command.modifiers)
              is TerminalExtraKeyCommand.SendRight -> session?.sendArrowRight(command.modifiers)
              is TerminalExtraKeyCommand.SendPageDown -> session?.sendPageDown(command.modifiers)
              null -> Unit
            }
            onModifiersChange(resolution.nextModifiers)
          }
        }
      }
    }
  }
}

@Composable
private fun ExtraTextKey(
  label: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean,
  active: Boolean = false,
  width: androidx.compose.ui.unit.Dp = 56.dp,
  height: androidx.compose.ui.unit.Dp = 32.dp,
  onClick: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val backgroundColor by animateColorAsState(
    targetValue = when {
      active -> TerminalColors.KeyActive
      enabled -> TerminalColors.Key
      else -> TerminalColors.KeyDisabled
    },
    animationSpec = tween(
      durationMillis = motionDuration(TerminalMotion.PanelMs),
      easing = FastOutSlowInEasing,
    ),
    label = "terminal key background",
  )
  val contentColor by animateColorAsState(
    targetValue = if (enabled) TerminalColors.TextStrong else TerminalColors.TextDisabled,
    animationSpec = tween(durationMillis = motionDuration(TerminalMotion.PanelMs)),
    label = "terminal key content",
  )
  val pressScale by animateFloatAsState(
    targetValue = if (enabled && isPressed) 0.96f else 1f,
    animationSpec = tween(
      durationMillis = motionDuration(TerminalMotion.PressMs),
      easing = FastOutSlowInEasing,
    ),
    label = "terminal key press",
  )
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .height(height)
      .then(if (width > 0.dp) Modifier.width(width) else Modifier)
      .graphicsLayer {
        scaleX = pressScale
        scaleY = pressScale
      }
      .background(backgroundColor, RoundedCornerShape(4.dp))
      .clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
      )
      .semantics { contentDescription = label },
  ) {
    if (icon == null) {
      Text(
        text = label,
        color = contentColor,
        fontSize = 12.sp,
        maxLines = 1,
      )
    } else {
      Icon(
        icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

private fun TerminalExtraKeyIcon.imageVector(): ImageVector = when (this) {
  TerminalExtraKeyIcon.Menu -> Icons.Filled.Menu
  TerminalExtraKeyIcon.Scroll -> Icons.Filled.SwapVert
  TerminalExtraKeyIcon.Up -> Icons.Filled.KeyboardArrowUp
  TerminalExtraKeyIcon.Left -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
  TerminalExtraKeyIcon.Down -> Icons.Filled.KeyboardArrowDown
  TerminalExtraKeyIcon.Right -> Icons.AutoMirrored.Filled.KeyboardArrowRight
}

private object TerminalMotion {
  const val PanelMs = 280
  const val RouteExitMs = 180
  const val PressMs = 120
  const val PressReleaseMs = 180
}

private fun motionDuration(durationMs: Int): Int =
  if (ValueAnimator.areAnimatorsEnabled()) durationMs else 0

private object TerminalColors {
  val Background = Color(0xFF05070A)
  val Terminal = Color(0xFF020305)
  val Chrome = Color(0xFF0B0F14)
  val Key = Color(0xFF1B222C)
  val KeyActive = Color(0xFF2F5D46)
  val KeyDisabled = Color(0xFF10151B)
  val Accent = Color(0xFF7EE787)
  val Destructive = Color(0xFFFF6B6B)
  val TextStrong = Color(0xFFE5EDF5)
  val TextMuted = Color(0xFF9DA9B7)
  val TextDisabled = Color(0xFF596271)
}

package com.nbg.android

import android.content.Context
import android.util.Log
import com.nbg.android.terminal.TerminalEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

internal data class HanakoStreamMeta(
  val streamId: String? = null,
  val lastSeq: Int = 0,
)

class HanakoChatController(
  context: Context,
  private val onEvent: (HanakoChatEvent) -> Unit,
) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val http = HanakoApiClient()
  private val wsClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)
    .pingInterval(20, TimeUnit.SECONDS)
    .build()
  private val _state = MutableStateFlow(HanakoChatState())
  val state: StateFlow<HanakoChatState> = _state.asStateFlow()
  private val launcher = HanakoServerLauncher()
  private val historyStore = HanakoHistoryCacheStore(
    filesDir = appContext.filesDir,
    ubuntuRootHomeDir = ubuntuRootHomeDir(),
  )
  private val learnedSkillDraftStore = NbgLearnedSkillDraftStore(appContext)
  private val skillCuratorStore = NbgSkillCuratorStore(appContext)
  private val autonomousLearningEngine = NbgAutonomousLearningEngine(appContext)

  private var serverInfo: HanakoServerInfo? = null
  private var webSocket: WebSocket? = null
  private var reconnectJob: Job? = null
  private var assistantMessageId: Long? = null
  private var assistantHasText = false
  private var thinkingMessageId: Long? = null
  private var nextMessageId = 1_000L
  private val streamMetaBySession = mutableMapOf<String, HanakoStreamMeta>()
  private var historyRequestSerial = 0L
  private var focusRequestSerial = 0L
  private var restoredInitialCache = false
  private var connectRequestSerial = 0L
  private var activeConnectAllowsLaunch = false
  private var warmupJob: Job? = null
  private var defaultUrlApi: Pair<NbgStoredApi, NbgApiModel>? = null
  private var activeUrlApiProvidersLoaded = false
  private var activeUrlApiProviderIds: Set<String> = emptySet()
  private var lastAgentModelConfig: HanakoAgentModelConfig? = null
  private var lastWsErrorMessage = ""
  private var lastWsErrorAtMs = 0L

  private fun applyFocusState(focus: HanakoSessionFocusState) {
    _state.update {
      it.copy(
        sessionPath = focus.path,
        agentName = focus.agentName ?: it.agentName,
        modelName = focus.modelName ?: it.modelName,
        contextUsageLabel = null,
        compressionAvailable = false,
        selectingSessionPath = null,
        planModeEnabled = focus.permissionMode == "read_only",
        permissionMode = focus.permissionMode,
        permissionModeLabel = focus.permissionLabel,
        thinkingLevel = focus.thinkingLevel,
        thinkingLevelLabel = focus.thinkingLabel,
        runtimeStatus = it.runtimeStatus.copy(
          permissionLabel = "权限：${focus.permissionLabel}",
          thinkingLabel = "思考：${focus.thinkingLabel}",
        ),
        lastError = null,
      )
    }
  }

  private fun resetActiveTurnState() {
    sealAssistantTextSegment()
    endThinking()
  }

  private fun Throwable.isHanakoSessionCacheMiss(): Boolean {
    val message = this.message.orEmpty()
    return message.contains("不在缓存中") ||
      message.contains("not in cache", ignoreCase = true) ||
      message.contains("session not found", ignoreCase = true)
  }

  private fun Throwable.isHanakoLocalConnectionFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
      val message = current.message.orEmpty()
      if (
        current is java.net.ConnectException ||
        current is java.net.NoRouteToHostException ||
        current is java.net.SocketException ||
        current is java.io.EOFException
      ) return true
      if (message.contains("Failed to connect", ignoreCase = true) && message.contains("127.0.0.1")) return true
      if (
        message.contains("Connection refused", ignoreCase = true) ||
        message.contains("ECONNREFUSED", ignoreCase = true) ||
        message.contains("Connection reset", ignoreCase = true) ||
        message.contains("unexpected end of stream", ignoreCase = true) ||
        message.contains("stream was reset", ignoreCase = true) ||
        message.contains("socket closed", ignoreCase = true)
      ) return true
      current = current.cause
    }
    return false
  }

  private fun handleHanakoLocalApiFailure(info: HanakoServerInfo?, error: Throwable): Boolean {
    if (!error.isHanakoLocalConnectionFailure()) return false
    val failedInfo = info ?: serverInfo
    val message = error.message ?: error.javaClass.simpleName
    Log.w(
      "NBG_HANAKO",
      "local HanakoPro API became unreachable${failedInfo?.let { " on ${it.port}" }.orEmpty()}; reconnecting",
      error,
    )
    if (failedInfo == null || serverInfo?.port == failedInfo.port) {
      serverInfo = null
    }
    webSocket?.close(1001, "local API unavailable")
    webSocket = null
    _state.update {
      it.copy(
        connected = false,
        connecting = false,
        prewarming = true,
        streaming = false,
        compressing = false,
        selectingSessionPath = null,
        lastError = message,
        connectionLabel = "HanakoPro 连接失效，正在重启",
      )
    }
    scheduleReconnect(message)
    return true
  }

  private suspend fun recoverHanakoLocalApiFailure(info: HanakoServerInfo?, error: Throwable): HanakoServerInfo? {
    if (!handleHanakoLocalApiFailure(info, error)) return null
    reconnectJob?.cancel()
    reconnectJob = null
    connect(allowLaunch = true)
    ensureConnected()
    return serverInfo?.takeIf { _state.value.connected && webSocket != null }
  }

  private suspend fun <T> withLocalHanakoRetry(
    info: HanakoServerInfo,
    block: suspend (HanakoServerInfo) -> T,
  ): T {
    return try {
      block(info)
    } catch (error: Throwable) {
      if (error is kotlinx.coroutines.CancellationException) throw error
      val recoveredInfo = recoverHanakoLocalApiFailure(info, error) ?: throw error
      block(recoveredInfo)
    }
  }

  private suspend fun ensureLiveSessionForRequestWithRetry(
    info: HanakoServerInfo,
    emitReplacementHistory: Boolean = true,
  ): Pair<HanakoServerInfo, String> =
    withLocalHanakoRetry(info) { activeInfo ->
      activeInfo to ensureLiveSessionForRequest(activeInfo, emitReplacementHistory)
    }

  private suspend fun ensureLiveSessionForRequest(
    info: HanakoServerInfo,
    emitReplacementHistory: Boolean = true,
  ): String {
    val currentPath = _state.value.sessionPath
    var preservedHistory: HanakoHistorySnapshot? = null
    val currentSessionUnhealthy = if (!currentPath.isNullOrBlank()) {
      withContext(Dispatchers.IO) { historyStore.localSessionLooksUnhealthy(currentPath) }
    } else {
      false
    }
    if (!currentPath.isNullOrBlank()) {
      if (currentSessionUnhealthy) {
        Log.w("NBG_HANAKO", "skip unhealthy local Hanako session; creating replacement for $currentPath")
      } else {
        try {
          val focus = withContext(Dispatchers.IO) { http.switchSession(info, currentPath, currentPath) }
          applyFocusState(focus)
          return focus.path
        } catch (error: Throwable) {
          if (error is kotlinx.coroutines.CancellationException) throw error
          if (!error.isHanakoSessionCacheMiss()) throw error
          preservedHistory = withContext(Dispatchers.IO) { historyStore.readCachedHistory(currentPath) }
          Log.w(
            "NBG_HANAKO",
            "cached session missing in HanakoPro runtime; creating replacement for $currentPath, preserved=${preservedHistory?.messages?.size ?: 0}",
            error,
          )
        }
      }
    }
    val focus = withContext(Dispatchers.IO) {
      http.configureDefaultUrlApiModelIfNeeded(info)
      http.createSession(info, null)
    }
    applyFocusState(focus)
    refreshSessions()
    val historyToKeep = preservedHistory?.takeIf { !currentSessionUnhealthy && it.messages.isNotEmpty() }
    if (historyToKeep != null) {
      saveCachedHistory(focus.path, historyToKeep)
      if (emitReplacementHistory) {
        onEvent(HanakoChatEvent.HistoryLoaded(focus.path, historyToKeep.messages, historyToKeep.todos, historyToKeep.sessionFiles))
      }
    } else if (emitReplacementHistory) {
      onEvent(HanakoChatEvent.HistoryLoaded(focus.path, emptyList(), emptyList(), emptyList()))
    }
    onEvent(
      HanakoChatEvent.SystemMessage(
        when {
          currentSessionUnhealthy -> "当前历史会话连续上游错误，已创建新会话继续，避免旧上下文再次触发 403。"
          historyToKeep != null -> "当前历史会话不在 HanakoPro 缓存中，已创建新会话继续，并保留本地完整历史。"
          else -> "当前历史会话不在 HanakoPro 缓存中，已创建新会话继续。"
        },
      ),
    )
    return focus.path
  }

  fun start() {
    restoreLatestCachedSessionOnce()
    loadLearnedSkillDraftQueue()
    loadSkillCuratorMetadata()
    loadAutonomousLearningSnapshot()
    connect(allowLaunch = true)
  }

  fun loadAutonomousLearningSnapshot() {
    val snapshot = autonomousLearningEngine.snapshot()
    _state.update {
      it.copy(
        autonomousLearningSnapshot = snapshot,
        learnedSkillDraftQueue = snapshot.learnedSkillDraftQueue,
      )
    }
  }

  fun approveLearningEvent(id: String) {
    val snapshot = autonomousLearningEngine.approveEvent(id)
    _state.update {
      it.copy(
        autonomousLearningSnapshot = snapshot,
        learnedSkillDraftQueue = snapshot.learnedSkillDraftQueue,
      )
    }
  }

  fun rejectLearningEvent(id: String) {
    val snapshot = autonomousLearningEngine.rejectEvent(id)
    _state.update { it.copy(autonomousLearningSnapshot = snapshot) }
  }

  fun revertLearningEvent(id: String) {
    val snapshot = autonomousLearningEngine.revertEvent(id)
    _state.update { it.copy(autonomousLearningSnapshot = snapshot) }
  }

  fun loadLearnedSkillDraftQueue() {
    _state.update { it.copy(learnedSkillDraftQueue = learnedSkillDraftStore.load()) }
  }

  fun rejectLearnedSkillDraft(id: String) {
    val queue = learnedSkillDraftStore.reject(id)
    _state.update {
      it.copy(
        learnedSkillDraftQueue = queue,
        skillsError = null,
        lastError = null,
      )
    }
  }

  fun loadSkillCuratorMetadata() {
    val metadata = skillCuratorStore.load()
    _state.update { state ->
      state.withSkillCuratorMetadata(metadata)
    }
  }

  fun stop() {
    warmupJob?.cancel()
    warmupJob = null
    reconnectJob?.cancel()
    reconnectJob = null
    webSocket?.close(1000, "stop")
    webSocket = null
    _state.update {
      it.copy(
        connected = false,
        connecting = false,
        prewarming = false,
        streaming = false,
        selectingSessionPath = null,
        mcpLoading = false,
        mcpBusyKey = null,
        skillsLoading = false,
        skillsBusyKey = null,
        connectionLabel = "HanakoPro 已断开",
      )
    }
  }

  fun showPreviewTeamTask() {
    val taskId = "android-preview-team"
    val task = HanakoTeamTaskStatus(
      taskId = taskId,
      title = "代码团队任务预览",
      mode = "auto",
      status = "running",
      summary = "Android 本地预览：真实 team_* WebSocket 事件会复用同一看板。",
      agents = listOf(
        HanakoTeamAgentStatus(taskId, nbgTeamAgentId("Supervisor"), "Supervisor", "总控 Agent", "thinking", "拆解任务并分配角色"),
        HanakoTeamAgentStatus(taskId, nbgTeamAgentId("Researcher"), "Researcher", "调研员", "running", "读取仓库和外部资料"),
        HanakoTeamAgentStatus(taskId, nbgTeamAgentId("Coder"), "Coder", "工程师", "coding", "准备代码修改"),
        HanakoTeamAgentStatus(taskId, nbgTeamAgentId("Reviewer"), "Reviewer", "审查员", "queued", "等待 Diff"),
        HanakoTeamAgentStatus(taskId, nbgTeamAgentId("Tester"), "Tester", "测试员", "idle", "待命"),
        HanakoTeamAgentStatus(taskId, nbgTeamAgentId("Terminal"), "Terminal", "终端执行员", "idle", "待命"),
        HanakoTeamAgentStatus(taskId, nbgTeamAgentId("File Manager"), "File Manager", "文件管理员", "idle", "待命"),
      ),
    )
    _state.update {
      it.copy(
        teamTask = task,
        runtimeStatus = it.runtimeStatus.copy(usageLabel = task.teamRuntimeLabel()),
        lastError = null,
      )
    }
    onEvent(HanakoChatEvent.TeamTaskUpdated(task))
  }

  fun setDefaultUrlApi(entry: NbgStoredApi?, model: NbgApiModel?) {
    defaultUrlApi = if (entry != null && model != null && entry.baseUrl.isNotBlank() && entry.apiKey.isNotBlank() && model.id.isNotBlank()) {
      entry to model
    } else {
      null
    }
    syncDefaultUrlApiModel()
  }

  fun syncUrlApiProviders(entries: List<NbgStoredApi>) {
    activeUrlApiProvidersLoaded = true
    activeUrlApiProviderIds = entries.map { nbgUrlApiProviderId(it.id) }.toSet()
    syncActiveUrlApiProviders()
  }

  private fun syncActiveUrlApiProviders(info: HanakoServerInfo? = serverInfo) {
    if (!activeUrlApiProvidersLoaded || _state.value.streaming) return
    val target = info
    val activeIds = activeUrlApiProviderIds
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          val serverRemoved = target?.let { http.pruneDeletedUrlApiProviders(it, activeIds) }.orEmpty()
          val fileChanged = nbgPruneDeletedUrlApiProviderFiles(appContext, activeIds)
          serverRemoved to fileChanged
        }
      }.onSuccess { (removed, fileChanged) ->
        if (removed.isNotEmpty() || fileChanged) {
          Log.i(
            "NBG_HANAKO",
            "pruned deleted URL API providers server=${removed.joinToString().ifBlank { "none" }} files=$fileChanged",
          )
          loadAgentModelConfig()
        }
      }.onFailure { error ->
        Log.w("NBG_HANAKO", "sync URL API providers failed", error)
        handleHanakoLocalApiFailure(target, error)
      }
    }
  }

  private fun HanakoApiClient.configureDefaultUrlApiModelIfNeeded(info: HanakoServerInfo) {
    val current = defaultUrlApi ?: return
    configureUrlApiModel(info, current.first, current.second)
  }

  private fun syncDefaultUrlApiModel() {
    val current = defaultUrlApi ?: return
    if (_state.value.streaming) return
    scope.launch {
      runCatching {
        val info = resolveApiServerInfo()
        withContext(Dispatchers.IO) {
          http.configureUrlApiModel(info, current.first, current.second)
        }
      }.onSuccess {
        _state.update { state ->
          state.copy(modelName = current.second.label.ifBlank { current.second.id })
        }
        loadAgentModelConfig()
      }.onFailure { error ->
        Log.w("NBG_HANAKO", "sync default URL API model failed", error)
        handleHanakoLocalApiFailure(serverInfo, error)
      }
    }
  }

  fun sendPrompt(text: String, displayText: String = text) {
    val prompt = text.trim()
    if (prompt.startsWith("/")) {
      learnFromUserTurn(prompt)
      sendSlash(prompt)
    } else {
      sendPromptInternal(prompt, displayText = displayText)
    }
  }

  fun sendPromptWithUrlApi(text: String, entry: NbgStoredApi, model: NbgApiModel, displayText: String = text) {
    val prompt = text.trim()
    if (prompt.isBlank()) return
    if (prompt.startsWith("/")) {
      learnFromUserTurn(prompt)
      sendSlash(prompt)
      return
    }
    sendPromptInternal(prompt, entry to model, displayText = displayText)
  }

  fun sendMultiAgentPrompt(text: String, displayText: String = text) {
    val prompt = text.trim()
    if (prompt.startsWith("/")) {
      learnFromUserTurn(prompt)
      sendSlash(prompt)
    } else {
      sendPromptInternal(prompt, displayText = displayText, multiAgentMode = true)
    }
  }

  private fun learnFromUserTurn(prompt: String) {
    val snapshot = autonomousLearningEngine.learnFromTurn(
      NbgLearningSourceTurn(
        userText = prompt,
        sessionPath = _state.value.sessionPath.orEmpty(),
        turnId = "android-${System.currentTimeMillis()}",
        timestampMs = System.currentTimeMillis(),
      ),
    )
    _state.update {
      it.copy(
        autonomousLearningSnapshot = snapshot,
        learnedSkillDraftQueue = snapshot.learnedSkillDraftQueue,
      )
    }
  }

  fun sendMultiAgentPromptWithUrlApi(text: String, entry: NbgStoredApi, model: NbgApiModel, displayText: String = text) {
    val prompt = text.trim()
    if (prompt.isBlank()) return
    if (prompt.startsWith("/")) {
      learnFromUserTurn(prompt)
      sendSlash(prompt)
      return
    }
    sendPromptInternal(prompt, entry to model, displayText = displayText, multiAgentMode = true)
  }

  fun createTeamTask(text: String) {
    val prompt = text.trim()
    if (prompt.isBlank()) return
    learnFromUserTurn(prompt)
    if (prompt.startsWith("/")) {
      sendSlash(prompt)
      return
    }
    createTeamTaskInternal(prompt)
  }

  fun createTeamTaskWithUrlApi(text: String, entry: NbgStoredApi, model: NbgApiModel) {
    val prompt = text.trim()
    if (prompt.isBlank()) return
    learnFromUserTurn(prompt)
    if (prompt.startsWith("/")) {
      sendSlash(prompt)
      return
    }
    scope.launch {
      ensureConnected()
      val info = serverInfo
      if (info != null) {
        runCatching {
          withLocalHanakoRetry(info) { activeInfo ->
            withContext(Dispatchers.IO) { http.configureUrlApiModel(activeInfo, entry, model) }
          }
        }.onFailure { error ->
          if (handleHanakoLocalApiFailure(info, error)) return@launch
          val message = error.message ?: error.javaClass.simpleName
          _state.update { it.copy(lastError = message) }
          onEvent(HanakoChatEvent.SystemMessage("同步 URL API 模型失败：$message"))
          return@launch
        }
      }
      createTeamTaskInternal(prompt, entry to model)
    }
  }

  private fun createTeamTaskInternal(text: String, urlApiSelection: Pair<NbgStoredApi, NbgApiModel>? = null) {
    val prompt = text.trim()
    if (prompt.isEmpty()) return
    val delegationReview = nbgReviewTeamDelegationRequest(prompt)
    if (!delegationReview.allowStart) {
      onEvent(HanakoChatEvent.SystemMessage(delegationReview.reason))
      return
    }
    onEvent(HanakoChatEvent.UserMessage(prompt))
    scope.launch {
      ensureConnected()
      val info = serverInfo
      if (info == null || !_state.value.connected) {
        onEvent(HanakoChatEvent.SystemMessage("HanakoPro 还没连接。请等待自动启动完成后再用团队模式发送。"))
        finishLocalSendFailure()
        return@launch
      }
      val (activeInfo, sessionPath) = runCatching {
        ensureLiveSessionForRequestWithRetry(info, emitReplacementHistory = false)
      }
        .onFailure { error ->
          handleHanakoLocalApiFailure(info, error)
          val message = error.message ?: error.javaClass.simpleName
          _state.update { it.copy(lastError = message) }
          onEvent(HanakoChatEvent.SystemMessage("团队任务创建失败：HanakoPro 已连接，但还不能恢复会话：$message"))
          finishLocalSendFailure()
        }.getOrNull() ?: return@launch
      urlApiSelection?.let { (entry, model) ->
        val providerId = nbgUrlApiProviderId(entry.id)
        runCatching {
          withContext(Dispatchers.IO) {
            http.configureUrlApiModel(activeInfo, entry, model)
            http.switchSessionModel(activeInfo, sessionPath, model.id, providerId)
          }
        }.onFailure { error ->
          handleHanakoLocalApiFailure(activeInfo, error)
          val message = error.message ?: error.javaClass.simpleName
          _state.update { it.copy(lastError = message) }
          onEvent(HanakoChatEvent.SystemMessage("团队任务创建失败：切换 URL API 模型失败：$message"))
          finishLocalSendFailure()
          return@launch
        }
      }
      requestContextUsage(sessionPath)
      val pendingTaskId = "android-team-${System.currentTimeMillis()}"
      val pendingTask = HanakoTeamTaskStatus(
        taskId = pendingTaskId,
        title = delegationReview.title.ifBlank { nbgTeamTaskTitle(prompt) },
        mode = HANA_TEAM_SINGLE_AGENT_SESSION_MODE,
        status = "queued",
        summary = "${delegationReview.reason} 正在接入 HanakoPro 执行会话...",
        agents = nbgSingleAgentSessionAgent(pendingTaskId, "queued", "等待 HanakoPro 接管任务"),
      )
      _state.update {
        it.copy(
          teamTask = pendingTask,
          runtimeStatus = it.runtimeStatus.copy(usageLabel = pendingTask.teamRuntimeLabel()),
          lastError = null,
        )
      }
      onEvent(HanakoChatEvent.TeamTaskUpdated(pendingTask))
      runCatching {
        withContext(Dispatchers.IO) {
          http.createTeamTask(
            info = info,
            sessionPath = sessionPath,
            prompt = prompt,
            title = pendingTask.title,
          )
        }
      }.onSuccess { root ->
        val nextTask = (parseHanakoTeamTaskStatus(root, pendingTask) ?: pendingTask.copy(status = "running"))
          .normalizedAndroidTeamTask()
        _state.update {
          it.copy(
            teamTask = nextTask,
            runtimeStatus = it.runtimeStatus.copy(usageLabel = nextTask.teamRuntimeLabel()),
            lastError = null,
          )
        }
        onEvent(HanakoChatEvent.TeamTaskUpdated(nextTask))
        onEvent(HanakoChatEvent.SystemMessage("已创建代码团队任务：${nextTask.title}"))
        finishLocalClientTurn()
      }.onFailure { error ->
        val rawMessage = error.message ?: error.javaClass.simpleName
        if (rawMessage.isMissingHanakoTeamApi()) {
          val message = "HanakoPro 后端还没有团队编排接口，已自动降级为默认 Hanako Agent 继续执行。"
          val fallbackTask = pendingTask.toSingleAgentFallback(message)
          _state.update {
            it.copy(
              teamTask = fallbackTask,
              runtimeStatus = it.runtimeStatus.copy(usageLabel = fallbackTask.teamRuntimeLabel()),
              lastError = null,
            )
          }
          onEvent(HanakoChatEvent.TeamTaskUpdated(fallbackTask))
          onEvent(HanakoChatEvent.SystemMessage(message))
          val sent = sendPromptOverCurrentWebSocket(
            prompt = prompt,
            sessionPath = sessionPath,
            type = "prompt",
            failurePrefix = "团队降级发送失败",
            displayMode = "team_fallback",
          )
          if (!sent) {
            val failedFallbackTask = fallbackTask.withFallbackTurnFinished("failed", "团队降级发送失败")
            _state.update {
              it.copy(
                teamTask = failedFallbackTask,
                runtimeStatus = it.runtimeStatus.copy(usageLabel = failedFallbackTask.teamRuntimeLabel()),
              )
            }
            onEvent(HanakoChatEvent.TeamTaskUpdated(failedFallbackTask))
            finishLocalSendFailure()
          }
        } else {
          val failedTask = pendingTask
            .withUpdatedActiveAgents(status = "failed", summary = rawMessage)
            .copy(status = "failed", summary = rawMessage)
          _state.update {
            it.copy(
              teamTask = failedTask,
              runtimeStatus = it.runtimeStatus.copy(usageLabel = failedTask.teamRuntimeLabel()),
              lastError = rawMessage,
            )
          }
          onEvent(HanakoChatEvent.TeamTaskUpdated(failedTask))
          onEvent(HanakoChatEvent.SystemMessage("团队任务创建失败：$rawMessage"))
          finishLocalSendFailure()
        }
      }
    }
  }

  private fun String.isMissingHanakoTeamApi(): Boolean =
    contains("HTTP 404") && contains("/api/team/tasks")

  private fun sendPromptOverCurrentWebSocket(
    prompt: String,
    sessionPath: String,
    type: String,
    failurePrefix: String,
    displayMode: String? = null,
    displayText: String = prompt,
  ): Boolean {
    val ws = webSocket
    if (!_state.value.connected || ws == null) {
      val message = "HanakoPro WebSocket 未连接"
      _state.update { it.copy(streaming = false, lastError = message) }
      onEvent(HanakoChatEvent.SystemMessage("$failurePrefix：$message"))
      return false
    }
    _state.update { it.copy(streaming = true, lastError = null) }
    val promptForModel = prompt.trim()
    val displayMessage = JSONObject().put("text", displayText)
    if (!displayMode.isNullOrBlank()) displayMessage.put("mode", displayMode)
    val ok = ws.send(
      JSONObject()
        .put("type", type)
        .put("text", promptForModel)
        .put("sessionPath", sessionPath)
        .put("uiContext", androidUiContext(sessionPath))
        .put("displayMessage", displayMessage)
        .toString(),
    )
    if (!ok) {
      val message = "WebSocket 发送失败"
      _state.update { it.copy(streaming = false, lastError = message) }
      onEvent(HanakoChatEvent.SystemMessage("$failurePrefix：$message，正在重连 HanakoPro。"))
      scheduleReconnect("$failurePrefix: send failed")
      return false
    }
    return true
  }

  private fun finishLocalClientTurn() {
    _state.update { it.copy(streaming = false) }
    resetActiveTurnState()
    onEvent(HanakoChatEvent.TurnEnded)
  }

  private fun finishLocalSendFailure() {
    finishLocalClientTurn()
  }

  private fun sendPromptInternal(
    text: String,
    urlApiSelection: Pair<NbgStoredApi, NbgApiModel>? = null,
    displayText: String = text,
    multiAgentMode: Boolean = false,
  ) {
    val prompt = text.trim()
    val visiblePrompt = displayText.trim().ifBlank { prompt }
    if (prompt.isEmpty()) return
    onEvent(HanakoChatEvent.UserMessage(visiblePrompt))
    learnFromUserTurn(prompt)
    scope.launch {
      val wasStreaming = _state.value.streaming
      val feedbackAssistantId = if (wasStreaming) {
        sealAssistantTextSegment()
        null
      } else {
        beginAssistant(
          if (_state.value.prewarming) {
            "HanakoPro 正在后台预热，连接后自动发送..."
          } else {
            "正在启动 HanakoPro，首次启动可能需要半分钟..."
          },
        )
      }
      ensureConnected()
      val current = _state.value
      val ws = webSocket
      val info = serverInfo
      if (!current.connected || ws == null || info == null) {
        if (feedbackAssistantId != null) {
          setAssistantText(feedbackAssistantId, "HanakoPro 还没连接。请等待自动启动完成后再发送。")
        } else {
          onEvent(HanakoChatEvent.SystemMessage("插话发送失败：HanakoPro 还没连接。"))
        }
        finishLocalSendFailure()
        return@launch
      }
      val sessionPath = runCatching {
        ensureLiveSessionForRequestWithRetry(info, emitReplacementHistory = false).second
      }
        .onFailure { error ->
          handleHanakoLocalApiFailure(info, error)
          val message = error.message ?: error.javaClass.simpleName
          _state.update { it.copy(lastError = message) }
          if (feedbackAssistantId != null) {
            setAssistantText(feedbackAssistantId, "HanakoPro 已连接，但还不能恢复会话：$message")
          } else {
            onEvent(HanakoChatEvent.SystemMessage("插话发送失败：HanakoPro 已连接，但还不能恢复会话：$message"))
          }
          finishLocalSendFailure()
      }.getOrNull() ?: return@launch
      urlApiSelection?.let { (entry, model) ->
        val providerId = nbgUrlApiProviderId(entry.id)
        runCatching {
          withLocalHanakoRetry(info) { activeInfo ->
            withContext(Dispatchers.IO) {
              http.configureUrlApiModel(activeInfo, entry, model)
              http.switchSessionModel(activeInfo, sessionPath, model.id, providerId)
            }
          }
        }.onFailure { error ->
          handleHanakoLocalApiFailure(info, error)
          val message = error.message ?: error.javaClass.simpleName
          _state.update { it.copy(lastError = message, streaming = false) }
          if (feedbackAssistantId != null) {
            setAssistantText(feedbackAssistantId, "切换 URL API 模型失败：$message")
          } else {
            onEvent(HanakoChatEvent.SystemMessage("插话发送失败：切换 URL API 模型失败：$message"))
          }
          finishLocalSendFailure()
          return@launch
        }
      }
      requestContextUsage(sessionPath)
      val messageType = if (wasStreaming) "interrupt_prompt" else "prompt"
      if (multiAgentMode && !wasStreaming) {
        val taskId = "android-multi-agent-${System.currentTimeMillis()}"
        val task = HanakoTeamTaskStatus(
          taskId = taskId,
          title = nbgTeamTaskTitle(visiblePrompt),
          mode = HANA_TEAM_MULTI_AGENT_SESSION_MODE,
          status = "running",
          summary = "单会话多 Agent 编排已启用",
          agents = nbgMultiAgentSessionAgents(taskId, "running"),
        )
        _state.update {
          it.copy(
            teamTask = task,
            runtimeStatus = it.runtimeStatus.copy(usageLabel = task.teamRuntimeLabel()),
            lastError = null,
          )
        }
        onEvent(HanakoChatEvent.TeamTaskUpdated(task))
      }
      if (!wasStreaming && feedbackAssistantId != null) {
        setAssistantPending(feedbackAssistantId, "正在思考...")
      }
      _state.update { it.copy(streaming = true, lastError = null) }
      val failurePrefix = if (wasStreaming) "插话发送失败" else "发送失败"
      val sent = sendPromptOverCurrentWebSocket(
        prompt = prompt,
        sessionPath = sessionPath,
        type = messageType,
        failurePrefix = failurePrefix,
        displayText = visiblePrompt,
      )
      if (!sent) {
        if (!wasStreaming) {
          feedbackAssistantId?.let { setAssistantText(it, "WebSocket 发送失败，正在重连 HanakoPro。") }
        }
        finishLocalSendFailure()
      }
    }
  }

  private fun sendSlash(text: String) {
    val command = text.trim()
    if (command.isEmpty()) return
    onEvent(HanakoChatEvent.UserMessage(command))
    scope.launch {
      ensureConnected()
      val current = _state.value
      val ws = webSocket
      val info = serverInfo
      if (!current.connected || ws == null || info == null) {
        onEvent(HanakoChatEvent.SystemMessage("HanakoPro 还没连接。请等待自动启动完成后再发送。"))
        finishLocalSendFailure()
        return@launch
      }
      val sessionPath = runCatching {
        ensureLiveSessionForRequestWithRetry(info, emitReplacementHistory = false).second
      }
        .onFailure { error ->
          handleHanakoLocalApiFailure(info, error)
          val message = error.message ?: error.javaClass.simpleName
          _state.update { it.copy(lastError = message) }
          onEvent(HanakoChatEvent.SystemMessage("HanakoPro 已连接，但还不能恢复会话：$message"))
          finishLocalSendFailure()
        }.getOrNull() ?: return@launch
      requestContextUsage(sessionPath)
      val ok = ws.send(
        JSONObject()
          .put("type", "slash")
          .put("text", command)
          .put("sessionPath", sessionPath)
          .put("agentId", "hanako")
          .toString(),
      )
      if (!ok) {
        onEvent(HanakoChatEvent.SystemMessage("Slash 命令发送失败，正在重连 HanakoPro。"))
        scheduleReconnect("slash send failed")
        finishLocalSendFailure()
      }
    }
  }

  fun selectSession(path: String) {
    if (path.isBlank()) return
    val requestSerial = ++focusRequestSerial
    val previousPath = _state.value.sessionPath
    if (path != previousPath) {
      resetActiveTurnState()
      _state.update { it.copy(selectingSessionPath = path, lastError = null) }
    }
    loadCachedHistory(path, requestSerial)
    if (path == _state.value.sessionPath) {
      loadHistory(path)
      requestContextUsage(path)
      requestStreamResume(path)
      return
    }
    scope.launch {
      ensureConnected()
      val info = serverInfo
      if (info == null) {
        _state.update {
          if (it.selectingSessionPath == path) it.copy(selectingSessionPath = null) else it
        }
        onEvent(HanakoChatEvent.SessionSelectFailed(path, previousPath))
        previousPath?.let(::loadCachedHistory)
        return@launch
      }
      runCatching {
        withContext(Dispatchers.IO) { http.switchSession(info, path, _state.value.sessionPath) }
      }.onSuccess { focus ->
        if (requestSerial != focusRequestSerial) {
          Log.i("NBG_HANAKO", "ignore stale focus switch for $path request=$requestSerial")
          return@onSuccess
        }
        applyFocusState(focus)
        loadHistory(path)
        requestContextUsage(path)
        requestStreamResume(path)
      }.onFailure { error ->
        if (requestSerial != focusRequestSerial) {
          Log.i("NBG_HANAKO", "ignore stale focus switch failure for $path request=$requestSerial")
          return@onFailure
        }
        _state.update {
          if (it.selectingSessionPath == path) it.copy(selectingSessionPath = null) else it
        }
        onEvent(HanakoChatEvent.SessionSelectFailed(path, previousPath))
        previousPath?.let(::loadCachedHistory)
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("切换 HanakoPro 会话失败：$message"))
      }
    }
  }

  fun createSession() {
    val requestSerial = ++focusRequestSerial
    resetActiveTurnState()
    _state.update { it.copy(selectingSessionPath = HANA_PENDING_NEW_SESSION_PATH, lastError = null) }
    scope.launch {
      ensureConnected()
      val info = serverInfo
      if (info == null) {
        _state.update {
          if (it.selectingSessionPath == HANA_PENDING_NEW_SESSION_PATH) it.copy(selectingSessionPath = null) else it
        }
        return@launch
      }
      runCatching {
        withLocalHanakoRetry(info) { activeInfo ->
          withContext(Dispatchers.IO) {
            http.configureDefaultUrlApiModelIfNeeded(activeInfo)
            http.createSession(activeInfo, _state.value.sessionPath)
          }
        }
      }.onSuccess { focus ->
        if (requestSerial != focusRequestSerial) {
          Log.i("NBG_HANAKO", "ignore stale create session request=$requestSerial")
          return@onSuccess
        }
        val sessionPath = focus.path
        applyFocusState(focus)
        refreshSessions()
        requestContextUsage(sessionPath)
        onEvent(HanakoChatEvent.HistoryLoaded(sessionPath, emptyList(), emptyList(), emptyList()))
      }.onFailure { error ->
        if (requestSerial != focusRequestSerial) {
          Log.i("NBG_HANAKO", "ignore stale create session failure request=$requestSerial")
          return@onFailure
        }
        _state.update {
          if (it.selectingSessionPath == HANA_PENDING_NEW_SESSION_PATH) it.copy(selectingSessionPath = null) else it
        }
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("创建 HanakoPro 会话失败：$message"))
      }
    }
  }

  fun createSessionWithUrlApi(entry: NbgStoredApi, model: NbgApiModel) {
    if (entry.baseUrl.isBlank() || entry.apiKey.isBlank() || model.id.isBlank()) {
      createSession()
      return
    }
    val requestSerial = ++focusRequestSerial
    resetActiveTurnState()
    _state.update { it.copy(selectingSessionPath = HANA_PENDING_NEW_SESSION_PATH, lastError = null) }
    scope.launch {
      ensureConnected()
      val info = serverInfo
      if (info == null) {
        _state.update {
          if (it.selectingSessionPath == HANA_PENDING_NEW_SESSION_PATH) it.copy(selectingSessionPath = null) else it
        }
        return@launch
      }
      runCatching {
        withLocalHanakoRetry(info) { activeInfo ->
          val providerId = nbgUrlApiProviderId(entry.id)
          withContext(Dispatchers.IO) {
            http.configureUrlApiModel(activeInfo, entry, model)
            val focus = http.createSession(activeInfo, _state.value.sessionPath)
            val switched = http.switchSessionModel(activeInfo, focus.path, model.id, providerId)
            focus to switched
          }
        }
      }.onSuccess { (focus, switched) ->
        if (requestSerial != focusRequestSerial) {
          Log.i("NBG_HANAKO", "ignore stale create URL API session request=$requestSerial")
          return@onSuccess
        }
        val sessionPath = focus.path
        val switchedModel = switched.optJSONObject("model")
        applyFocusState(focus)
        _state.update {
          it.copy(
            modelName = switchedModel?.cleanString("name")
              ?: switchedModel?.cleanString("id")
              ?: model.label,
            contextUsageLabel = null,
            compressionAvailable = false,
            selectingSessionPath = null,
            lastError = null,
          )
        }
        refreshSessions()
        requestContextUsage(sessionPath)
        onEvent(HanakoChatEvent.HistoryLoaded(sessionPath, emptyList(), emptyList(), emptyList()))
      }.onFailure { error ->
        if (requestSerial != focusRequestSerial) {
          Log.i("NBG_HANAKO", "ignore stale create URL API session failure request=$requestSerial")
          return@onFailure
        }
        _state.update {
          if (it.selectingSessionPath == HANA_PENDING_NEW_SESSION_PATH) it.copy(selectingSessionPath = null) else it
        }
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("创建 HanakoPro 会话失败：$message"))
      }
    }
  }

  fun renameSession(path: String, title: String) {
    val sessionPath = path.trim()
    val nextTitle = title.trim()
    if (sessionPath.isBlank() || nextTitle.isBlank()) return
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        withContext(Dispatchers.IO) { http.renameSession(info, sessionPath, nextTitle) }
      }.onSuccess {
        refreshSessions()
        if (_state.value.searchQuery.isNotBlank()) searchSessions(_state.value.searchQuery)
        onEvent(HanakoChatEvent.SystemMessage("已重命名会话：$nextTitle"))
      }.onFailure { error ->
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("重命名 HanakoPro 会话失败：$message"))
      }
    }
  }

  fun deleteSession(path: String) {
    val sessionPath = path.trim()
    if (sessionPath.isBlank()) return
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        withContext(Dispatchers.IO) { http.deleteSessionPermanently(info, sessionPath) }
      }.onSuccess {
        val wasCurrent = _state.value.sessionPath == sessionPath
        if (wasCurrent) resetActiveTurnState()
        _state.update {
          if (wasCurrent) {
            it.clearCurrentSessionState()
          } else {
            it.copy(lastError = null)
          }
        }
        refreshSessions()
        if (_state.value.searchQuery.isNotBlank()) searchSessions(_state.value.searchQuery)
        if (wasCurrent) onEvent(HanakoChatEvent.SessionDeleted(sessionPath))
        onEvent(HanakoChatEvent.SystemMessage("已删除 HanakoPro 会话"))
      }.onFailure { error ->
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("删除 HanakoPro 会话失败：$message"))
      }
    }
  }

  private fun HanakoChatState.clearCurrentSessionState(): HanakoChatState =
    copy(
      sessionPath = null,
      streaming = false,
      compressing = false,
      planModeEnabled = false,
      contextUsageLabel = null,
      compressionAvailable = false,
      selectingSessionPath = null,
      teamTask = null,
      runtimeStatus = runtimeStatus.copy(usageLabel = null),
      lastError = null,
    )

  fun setSessionPinned(path: String, pinned: Boolean) {
    val sessionPath = path.trim()
    if (sessionPath.isBlank()) return
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        withContext(Dispatchers.IO) { http.pinSession(info, sessionPath, pinned) }
      }.onSuccess {
        refreshSessions()
        if (_state.value.searchQuery.isNotBlank()) searchSessions(_state.value.searchQuery)
      }.onFailure { error ->
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("更新 HanakoPro 置顶状态失败：$message"))
      }
    }
  }

  fun loadAgentModelConfig() {
    scope.launch {
      runCatching {
        val info = resolveApiServerInfo()
        withContext(Dispatchers.IO) { http.getAgentModelConfig(info) }
      }.onSuccess { config ->
        lastAgentModelConfig = config
        onEvent(HanakoChatEvent.AgentModelConfigLoaded(config))
      }.onFailure { error ->
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.AgentModelConfigFailed(message))
      }
    }
  }

  fun loadProviders() {
    scope.launch {
      runCatching {
        val info = resolveApiServerInfo()
        withContext(Dispatchers.IO) { http.getProviders(info) }
      }.onSuccess { snapshot ->
        onEvent(HanakoChatEvent.ProvidersLoaded(snapshot))
      }.onFailure { error ->
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.ProvidersFailed(message))
      }
    }
  }

  fun loadMcpState(agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    scope.launch {
      _state.update { it.copy(mcpLoading = true, mcpError = null) }
      runCatching {
        loadMcpStateWithRetry(agentId)
      }.onSuccess { state ->
        _state.update {
          it.copy(
            mcpState = state,
            mcpLoading = false,
            mcpError = null,
            mcpBusyKey = null,
            lastError = null,
          )
        }
      }.onFailure { error ->
        val message = mcpUserError(error)
        _state.update {
          it.copy(
            mcpLoading = false,
            mcpBusyKey = null,
            mcpError = message,
            lastError = message,
          )
        }
      }
    }
  }

  fun setMcpEnabled(enabled: Boolean, agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    runMcpMutation(busyKey = "mcp:enabled", agentId = agentId) { info ->
      http.setMcpEnabled(info, enabled)
    }
  }

  fun addMcpConnector(input: HanakoMcpConnectorInput, agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    val name = input.name.trim()
    val url = input.url.trim()
    if (name.isBlank() || url.isBlank()) {
      _state.update { it.copy(mcpError = "名称和地址不能为空") }
      return
    }
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      _state.update { it.copy(mcpError = "MCP 地址必须以 http:// 或 https:// 开头") }
      return
    }
    runMcpMutation(busyKey = "mcp:add", agentId = agentId) { info ->
      http.setMcpEnabled(info, true)
      http.saveMcpConnector(info, input.copy(name = name, url = url))
      http.runMcpConnectorAction(info, input.id, "start")
      http.runMcpConnectorAction(info, input.id, "refresh-tools")
      val state = http.getMcpState(info, agentId)
      val connector = state.connectors.firstOrNull { it.id == input.id }
      http.setAgentMcpConnector(info, agentId, input.id, true)
      connector?.tools.orEmpty().forEach { tool ->
        http.setAgentMcpTool(info, agentId, input.id, tool.name, true)
      }
    }
  }

  fun runMcpConnectorAction(
    connectorId: String,
    action: String,
    agentId: String = HANA_DEFAULT_MCP_AGENT_ID,
  ) {
    val id = connectorId.trim()
    if (id.isBlank()) return
    runMcpMutation(busyKey = "mcp:connector:$id:$action", agentId = agentId) { info ->
      http.runMcpConnectorAction(info, id, action)
    }
  }

  fun deleteMcpConnector(
    connectorId: String,
    agentId: String = HANA_DEFAULT_MCP_AGENT_ID,
  ) {
    val id = connectorId.trim()
    if (id.isBlank()) return
    runMcpMutation(busyKey = "mcp:delete:$id", agentId = agentId) { info ->
      http.deleteMcpConnector(info, id)
    }
  }

  fun setAgentMcpConnector(
    connectorId: String,
    enabled: Boolean,
    agentId: String = HANA_DEFAULT_MCP_AGENT_ID,
  ) {
    val id = connectorId.trim()
    if (id.isBlank()) return
    runMcpMutation(busyKey = "mcp:agent:$id", agentId = agentId) { info ->
      http.setAgentMcpConnector(info, agentId, id, enabled)
    }
  }

  fun setAgentMcpTool(
    connectorId: String,
    toolName: String,
    enabled: Boolean,
    agentId: String = HANA_DEFAULT_MCP_AGENT_ID,
  ) {
    val id = connectorId.trim()
    val name = toolName.trim()
    if (id.isBlank() || name.isBlank()) return
    runMcpMutation(busyKey = "mcp:tool:$id:$name", agentId = agentId) { info ->
      http.setAgentMcpTool(info, agentId, id, name, enabled)
    }
  }

  private fun runMcpMutation(
    busyKey: String,
    agentId: String,
    operation: suspend (HanakoServerInfo) -> Unit,
  ) {
    scope.launch {
      _state.update { it.copy(mcpBusyKey = busyKey, mcpError = null) }
      runCatching {
        val info = resolveApiServerInfo()
        withLocalHanakoRetry(info) { activeInfo ->
          withContext(Dispatchers.IO) {
            operation(activeInfo)
            http.getMcpState(activeInfo, agentId)
          }
        }
      }.onSuccess { state ->
        _state.update {
          it.copy(
            mcpState = state,
            mcpLoading = false,
            mcpBusyKey = null,
            mcpError = null,
            lastError = null,
          )
        }
      }.onFailure { error ->
        val message = mcpUserError(error)
        _state.update {
          it.copy(
            mcpLoading = false,
            mcpBusyKey = null,
            mcpError = message,
            lastError = message,
          )
        }
      }
    }
  }

  private suspend fun loadMcpStateWithRetry(agentId: String): HanakoMcpState {
    val info = resolveApiServerInfo()
    return withLocalHanakoRetry(info) { activeInfo ->
      withContext(Dispatchers.IO) { http.getMcpState(activeInfo, agentId) }
    }
  }

  private fun mcpUserError(error: Throwable): String {
    if (handleHanakoLocalApiFailure(serverInfo, error)) {
      return "HanakoPro 正在重连，稍后刷新 MCP。"
    }
    val raw = error.message ?: error.javaClass.simpleName
    return when {
      raw.contains("/api/plugins/mcp") && raw.contains("HTTP 404") -> "当前 HanakoPro 没有 MCP 插件接口，请确认服务端包含 MCP 插件。"
      raw.contains("not initialized", ignoreCase = true) -> "MCP 插件还没有初始化。"
      raw.contains("disabled globally", ignoreCase = true) -> "MCP 全局开关未开启。"
      else -> raw
    }
  }

  fun loadMemoryState(query: String = "", type: String = "") {
    scope.launch {
      _state.update { it.copy(memoryLoading = true, memoryError = null) }
      runCatching {
        loadMemoryStateWithRetry(query, type)
      }.onSuccess { state ->
        _state.update {
          it.copy(
            memoryState = state.copy(auditEvents = it.memoryState.auditEvents),
            memoryLoading = false,
            memoryBusyKey = null,
            memoryError = null,
            lastError = null,
          )
        }
      }.onFailure { error ->
        val message = memoryUserError(error)
        _state.update {
          it.copy(
            memoryLoading = false,
            memoryBusyKey = null,
            memoryError = message,
            lastError = message,
          )
        }
      }
    }
  }

  fun saveMemoryItem(input: HanakoMemoryInput, query: String = "", type: String = "") {
    val review = nbgReviewMemoryInput(input)
    val auditAction = if (input.id.trim().isBlank()) "create" else "update"
    if (!review.allowSave) {
      val auditEvent = nbgMemoryAuditEvent(
        action = auditAction,
        result = "blocked",
        normalizedType = review.normalizedType,
        risk = review.risk.wireName,
      )
      _state.update {
        it.copy(
          memoryError = review.userMessage,
          memoryState = it.memoryState.withMemoryAuditEvent(auditEvent),
        )
      }
      return
    }
    val cleanInput = input.copy(
      type = review.normalizedType,
      title = input.title.trim(),
      content = input.content.trim(),
      tags = input.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
    )
    runMemoryMutation(
      busyKey = "memory:save",
      query = query,
      type = type,
      auditAction = auditAction,
      auditType = review.normalizedType,
      auditRisk = review.risk.wireName,
    ) { info ->
      if (cleanInput.id.isBlank()) http.saveMemoryItem(info, cleanInput) else http.updateMemoryItem(info, cleanInput)
    }
  }

  fun deleteMemoryItem(id: String, query: String = "", type: String = "") {
    val cleanId = id.trim()
    if (cleanId.isBlank()) return
    runMemoryMutation(
      busyKey = "memory:delete:$cleanId",
      query = query,
      type = type,
      auditAction = "delete",
    ) { info ->
      http.deleteMemoryItem(info, cleanId)
    }
  }

  private fun runMemoryMutation(
    busyKey: String,
    query: String,
    type: String,
    auditAction: String,
    auditType: String = "",
    auditRisk: String = "",
    operation: suspend (HanakoServerInfo) -> Unit,
  ) {
    scope.launch {
      _state.update { it.copy(memoryBusyKey = busyKey, memoryError = null) }
      var memoryMutationCompleted = false
      runCatching {
        val info = resolveApiServerInfo()
        withLocalHanakoRetry(info) { activeInfo ->
          withContext(Dispatchers.IO) {
            operation(activeInfo)
            memoryMutationCompleted = true
            http.getMemoryState(activeInfo, query = query, type = type)
          }
        }
      }.onSuccess { state ->
        _state.update {
          val auditEvent = nbgMemoryAuditEvent(
            action = auditAction,
            result = "success",
            normalizedType = auditType,
            risk = auditRisk,
          )
          it.copy(
            memoryState = state.copy(auditEvents = it.memoryState.auditEvents).withMemoryAuditEvent(auditEvent),
            memoryLoading = false,
            memoryBusyKey = null,
            memoryError = null,
            lastError = null,
          )
        }
      }.onFailure { error ->
        val message = memoryUserError(error)
        _state.update {
          val auditEvent = nbgMemoryAuditEvent(
            action = auditAction,
            result = if (memoryMutationCompleted) "success" else "failure",
            normalizedType = auditType,
            risk = auditRisk,
          )
          it.copy(
            memoryState = it.memoryState.withMemoryAuditEvent(auditEvent),
            memoryLoading = false,
            memoryBusyKey = null,
            memoryError = message,
            lastError = message,
          )
        }
      }
    }
  }

  private suspend fun loadMemoryStateWithRetry(query: String, type: String): HanakoMemoryState {
    val info = resolveApiServerInfo()
    return withLocalHanakoRetry(info) { activeInfo ->
      withContext(Dispatchers.IO) { http.getMemoryState(activeInfo, query = query, type = type) }
    }
  }

  private fun memoryUserError(error: Throwable): String {
    if (handleHanakoLocalApiFailure(serverInfo, error)) {
      return "HanakoPro 正在重连，稍后刷新 Memory。"
    }
    val raw = error.message ?: error.javaClass.simpleName
    return when {
      raw.contains("/api/plugins/memory") && raw.contains("HTTP 404") -> "当前 HanakoPro 没有 Memory 插件接口，请确认服务端包含 Memory 插件。"
      raw.contains("not initialized", ignoreCase = true) -> "Memory 插件还没有初始化。"
      else -> raw
    }
  }

  fun loadSkills(agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    scope.launch {
      _state.update { it.copy(skillsLoading = true, skillsError = null) }
      runCatching {
        loadSkillsWithRetry(agentId)
      }.onSuccess { snapshot ->
        _state.update {
          it.withSkillSnapshot(snapshot).copy(
            skillsLoading = false,
            skillsBusyKey = null,
            skillsError = null,
            lastError = null,
          )
        }
      }.onFailure { error ->
        val message = skillsUserError(error)
        _state.update {
          it.copy(
            skillsLoading = false,
            skillsBusyKey = null,
            skillsError = message,
            lastError = message,
          )
        }
      }
    }
  }

  fun installSkill(input: HanakoSkillInstallInput, agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    val source = input.path.trim()
    if (source.isBlank()) {
      _state.update { it.copy(skillsError = "Skill 路径不能为空") }
      return
    }
    if (!source.startsWith("/") && !source.isSkillInstallUrl()) {
      _state.update { it.copy(skillsError = "请输入 / 开头的本地路径，或 https:// 开头的 Skill 链接") }
      return
    }
    val review = nbgReviewSkillInstallSource(source)
    if (!review.allowInstall) {
      _state.update { it.copy(skillsError = review.reason) }
      return
    }
    runSkillsSnapshotMutation(busyKey = "skills:install", agentId = agentId) { info ->
      val beforeInstall = http.getSkills(info, agentId)
      val installPath = if (source.isSkillInstallUrl()) {
        downloadSkillInstallUrl(source, ubuntuRootHomeDir())
      } else {
        source
      }
      http.installSkill(info, HanakoSkillInstallInput(installPath), agentId)
      http.reloadSkills(info)
      val afterInstall = http.getSkills(info, agentId)
      val currentEnabled = afterInstall.visibleSkills.filter { it.enabled }.map { it.name }
      val safeEnabled = nbgEnabledSkillsAfterManualInstall(beforeInstall.visibleSkills, afterInstall.visibleSkills)
      if (safeEnabled != currentEnabled) {
        http.setAgentSkills(info, agentId, safeEnabled)
        http.reloadSkills(info)
        http.getSkills(info, agentId)
      } else {
        afterInstall
      }
    }
  }

  fun reloadSkills(agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    runSkillsMutation(busyKey = "skills:reload", agentId = agentId) { info ->
      http.reloadSkills(info)
    }
  }

  fun setSkillEnabled(skillName: String, enabled: Boolean, agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    val name = skillName.trim()
    if (name.isBlank()) return
    recordSkillCuratorUse(name)
    runSkillsMutation(busyKey = "skills:toggle:$name", agentId = agentId) { info ->
      val snapshot = http.getSkills(info, agentId)
      val enabledNames = snapshot.visibleSkills
        .map { skill ->
          if (skill.name == name) skill.copy(enabled = enabled) else skill
        }
        .filter { it.enabled }
        .map { it.name }
      http.setAgentSkills(info, agentId, enabledNames)
    }
  }

  fun deleteSkill(skillName: String, agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    val name = skillName.trim()
    if (name.isBlank()) return
    recordSkillCuratorUse(name)
    runSkillsMutation(busyKey = "skills:delete:$name", agentId = agentId) { info ->
      http.deleteSkill(info, name, agentId)
    }
  }

  fun archiveSkill(skillName: String) {
    val name = skillName.trim()
    if (name.isBlank()) return
    val active = _state.value.rawSkillsSnapshot.visibleSkills.firstOrNull { it.name == name }?.enabled == true
    if (active) {
      _state.update { it.copy(skillsError = "请先禁用 Skill，再归档本地视图。") }
      return
    }
    val metadata = skillCuratorStore.archive(name, reason = "user_archive")
    _state.update { state ->
      state.withSkillCuratorMetadata(metadata).copy(
        skillsError = null,
        lastError = null,
      )
    }
  }

  fun restoreArchivedSkill(skillName: String) {
    val name = skillName.trim()
    if (name.isBlank()) return
    val metadata = skillCuratorStore.restore(name)
    _state.update { state ->
      state.withSkillCuratorMetadata(metadata).copy(
        skillsError = null,
        lastError = null,
      )
    }
  }

  fun createSkillBundle(
    name: String,
    skillNames: List<String>,
    agentId: String = HANA_DEFAULT_MCP_AGENT_ID,
  ) {
    val title = name.trim()
    if (title.isBlank()) {
      _state.update { it.copy(skillsError = "Bundle 名称不能为空") }
      return
    }
    runSkillsMutation(busyKey = "skills:bundle:create", agentId = agentId) { info ->
      http.createSkillBundle(info, HanakoSkillBundleInput(title, skillNames), agentId)
    }
  }

  fun updateSkillBundle(
    bundleId: String,
    name: String,
    skillNames: List<String>,
    agentId: String = HANA_DEFAULT_MCP_AGENT_ID,
  ) {
    val id = bundleId.trim()
    val title = name.trim()
    if (id.isBlank()) return
    if (title.isBlank()) {
      _state.update { it.copy(skillsError = "Bundle 名称不能为空") }
      return
    }
    runSkillsMutation(busyKey = "skills:bundle:$id", agentId = agentId) { info ->
      http.updateSkillBundle(info, id, HanakoSkillBundleInput(title, skillNames), agentId)
    }
  }

  fun deleteSkillBundle(bundleId: String, agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    val id = bundleId.trim()
    if (id.isBlank()) return
    runSkillsMutation(busyKey = "skills:bundle-delete:$id", agentId = agentId) { info ->
      http.deleteSkillBundle(info, id)
    }
  }

  fun setExternalSkillPaths(paths: List<String>, agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    val nextPaths = paths.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    val invalid = nextPaths.firstOrNull { !it.startsWith("/") }
    if (invalid != null) {
      _state.update { it.copy(skillsError = "外部 Skill 路径必须是绝对路径：$invalid") }
      return
    }
    runSkillsMutation(busyKey = "skills:external-paths", agentId = agentId) { info ->
      http.setExternalSkillPaths(info, HanakoExternalSkillPathsInput(nextPaths))
    }
  }

  private fun runSkillsMutation(
    busyKey: String,
    agentId: String,
    operation: suspend (HanakoServerInfo) -> Unit,
  ) = runSkillsSnapshotMutation(busyKey, agentId) { activeInfo ->
    operation(activeInfo)
    http.reloadSkills(activeInfo)
    http.getSkills(activeInfo, agentId)
  }

  private fun runSkillsSnapshotMutation(
    busyKey: String,
    agentId: String,
    operation: suspend (HanakoServerInfo) -> HanakoSkillsSnapshot,
  ) {
    scope.launch {
      _state.update { it.copy(skillsBusyKey = busyKey, skillsError = null) }
      runCatching {
        val info = resolveApiServerInfo()
        withLocalHanakoRetry(info) { activeInfo ->
          withContext(Dispatchers.IO) { operation(activeInfo) }
        }
      }.onSuccess { snapshot ->
        _state.update {
          it.withSkillSnapshot(snapshot).copy(
            skillsLoading = false,
            skillsBusyKey = null,
            skillsError = null,
            lastError = null,
          )
        }
      }.onFailure { error ->
        val message = skillsUserError(error)
        _state.update {
          it.copy(
            skillsLoading = false,
            skillsBusyKey = null,
            skillsError = message,
            lastError = message,
          )
        }
      }
    }
  }

  private suspend fun loadSkillsWithRetry(agentId: String): HanakoSkillsSnapshot {
    val info = resolveApiServerInfo()
    return withLocalHanakoRetry(info) { activeInfo ->
      withContext(Dispatchers.IO) { http.getSkills(activeInfo, agentId) }
    }
  }

  private fun recordSkillCuratorUse(skillName: String) {
    val metadata = skillCuratorStore.recordUse(skillName)
    _state.update { it.withSkillCuratorMetadata(metadata) }
  }

  private fun skillsUserError(error: Throwable): String {
    if (handleHanakoLocalApiFailure(serverInfo, error)) {
      return "HanakoPro 正在重连，稍后刷新 Skills。"
    }
    val raw = error.message ?: error.javaClass.simpleName
    return when {
      raw.contains("/api/skills") && raw.contains("HTTP 404") -> "当前 HanakoPro 没有 Skills 接口。"
      raw.contains("agent not found", ignoreCase = true) -> "没有找到当前 Hanako Agent。"
      raw.contains("absolute", ignoreCase = true) -> "Skill 路径必须是绝对路径。"
      raw.contains("下载 Skill 失败", ignoreCase = true) -> raw
      raw.contains("Skill 链接", ignoreCase = true) -> raw
      raw.contains("SKILL.md", ignoreCase = true) -> "Skill 目录里必须包含 SKILL.md。"
      else -> raw
    }
  }

  fun checkModelHealth(model: HanakoModelSummary) {
    if (model.id.isBlank() || model.provider.isBlank()) return
    scope.launch {
      runCatching {
        val info = resolveApiServerInfo()
        withContext(Dispatchers.IO) { http.checkModelHealth(info, model.id, model.provider) }
      }.onSuccess { health ->
        onEvent(HanakoChatEvent.ModelHealthLoaded(health))
      }.onFailure { error ->
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.ModelHealthFailed(model.id, model.provider, message))
      }
    }
  }

  fun loadSlashCommands() {
    scope.launch {
      runCatching {
        val info = resolveApiServerInfo()
        withContext(Dispatchers.IO) { http.listSlashCommands(info) }
      }.onSuccess { commands ->
        onEvent(HanakoChatEvent.SlashCommandsLoaded(commands))
      }.onFailure { error ->
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SlashCommandsFailed(message))
      }
    }
  }

  private suspend fun resolveApiServerInfo(): HanakoServerInfo {
    serverInfo?.let { info ->
      if (withContext(Dispatchers.IO) { http.isHealthy(info) }) return info
      return restartStaleServer(info, "HanakoPro 健康检查超时")
    }
    val existing = withContext(Dispatchers.IO) { findServerInfo(appContext) }
    if (existing != null && withContext(Dispatchers.IO) { http.isHealthy(existing) }) {
      serverInfo = existing
      return existing
    }
    if (existing != null) {
      return restartStaleServer(existing, "HanakoPro 状态文件指向无响应服务")
    }
    val live = resolveLiveServerInfo(allowLaunch = true)
    serverInfo = live
    return live
  }

  fun switchAgent(agentId: String) {
    val id = agentId.trim()
    if (id.isBlank() || _state.value.streaming) return
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        withContext(Dispatchers.IO) { http.switchAgent(info, id) }
      }.onSuccess { result ->
        val agent = result.optJSONObject("agent")
        val sessionPath = result.cleanString("sessionPath")
        resetActiveTurnState()
        _state.update {
          it.copy(
            agentName = agent?.cleanString("name") ?: it.agentName,
            sessionPath = sessionPath ?: it.sessionPath,
            contextUsageLabel = null,
            compressionAvailable = false,
            planModeEnabled = false,
            lastError = null,
          )
        }
        if (!sessionPath.isNullOrBlank()) {
          loadHistory(sessionPath)
          requestContextUsage(sessionPath)
          requestStreamResume(sessionPath)
        }
        refreshSessions()
        loadAgentModelConfig()
      }.onFailure { error ->
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("切换助手失败：$message"))
        onEvent(HanakoChatEvent.AgentModelConfigFailed(message))
      }
    }
  }

  fun switchModel(model: HanakoModelSummary) {
    if (model.id.isBlank() || model.provider.isBlank() || _state.value.streaming) return
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        val (activeInfo, sessionPath) = ensureLiveSessionForRequestWithRetry(info)
        withContext(Dispatchers.IO) {
          http.switchSessionModel(activeInfo, sessionPath, model.id, model.provider)
        }
      }.onSuccess { result ->
        val nextModel = result.optJSONObject("model")
        val modelLabel = nextModel?.cleanString("name")
          ?: nextModel?.cleanString("id")
          ?: model.label
        _state.update {
          it.copy(
            modelName = modelLabel,
            contextUsageLabel = null,
            compressionAvailable = false,
            lastError = null,
          )
        }
        _state.value.sessionPath?.let { requestContextUsage(it) }
        refreshSessions()
        loadAgentModelConfig()
      }.onFailure { error ->
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("切换模型失败：$message"))
        onEvent(HanakoChatEvent.AgentModelConfigFailed(message))
      }
    }
  }

  fun switchUrlApiModel(entry: NbgStoredApi, model: NbgApiModel) {
    if (entry.baseUrl.isBlank() || entry.apiKey.isBlank() || model.id.isBlank() || _state.value.streaming) return
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      val providerId = nbgUrlApiProviderId(entry.id)
      runCatching {
        val (activeInfo, sessionPath) = ensureLiveSessionForRequestWithRetry(info)
        withContext(Dispatchers.IO) {
          http.configureUrlApiModel(activeInfo, entry, model)
          http.switchSessionModel(activeInfo, sessionPath, model.id, providerId)
        }
      }.onSuccess { result ->
        val nextModel = result.optJSONObject("model")
        val modelLabel = nextModel?.cleanString("name")
          ?: nextModel?.cleanString("id")
          ?: model.label
        _state.update {
          it.copy(
            modelName = modelLabel,
            contextUsageLabel = null,
            compressionAvailable = false,
            lastError = null,
          )
        }
        _state.value.sessionPath?.let { requestContextUsage(it) }
        refreshSessions()
        loadAgentModelConfig()
      }.onFailure { error ->
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("切换 URL API 模型失败：$message"))
        onEvent(HanakoChatEvent.AgentModelConfigFailed(message))
      }
    }
  }

  fun setSessionPermissionMode(mode: String) {
    val nextMode = mode.trim()
    if (nextMode !in NBG_VALID_PERMISSION_MODES || _state.value.streaming) return
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        val (activeInfo, sessionPath) = ensureLiveSessionForRequestWithRetry(info)
        withContext(Dispatchers.IO) { http.setSessionPermissionMode(activeInfo, nextMode, sessionPath) }
      }.onSuccess { result ->
        val actualMode = result.mode
        val label = result.label
        _state.update {
          it.copy(
            planModeEnabled = actualMode == "read_only",
            permissionMode = actualMode,
            permissionModeLabel = label,
            runtimeStatus = it.runtimeStatus.copy(permissionLabel = "权限：$label"),
            lastError = null,
          )
        }
      }.onFailure { error ->
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("切换权限模式失败：$message"))
      }
    }
  }

  fun setThinkingLevel(level: String) {
    val nextLevel = nbgCoerceThinkingLevelForModel(level, currentModelSummary())
    if (nextLevel !in setOf("off", "auto", "low", "medium", "high", "xhigh") || _state.value.streaming) return
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        val (activeInfo, sessionPath) = ensureLiveSessionForRequestWithRetry(info)
        withContext(Dispatchers.IO) { http.setThinkingLevel(activeInfo, nextLevel, sessionPath) }
      }.onSuccess { result ->
        val actualLevel = result.thinkingLevel
        val label = result.label
        _state.update {
          it.copy(
            thinkingLevel = actualLevel,
            thinkingLevelLabel = label,
            runtimeStatus = it.runtimeStatus.copy(thinkingLabel = "思考：$label"),
            lastError = null,
          )
        }
      }.onFailure { error ->
        if (handleHanakoLocalApiFailure(info, error)) return@onFailure
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("切换思考强度失败：$message"))
      }
    }
  }

  private fun currentModelSummary(): HanakoModelSummary? {
    val modelName = _state.value.modelName
    return lastAgentModelConfig?.models.orEmpty().firstOrNull { it.isCurrent }
      ?: lastAgentModelConfig?.models.orEmpty().firstOrNull { model ->
        !modelName.isNullOrBlank() && (
          model.id.equals(modelName, ignoreCase = true) ||
            model.name.equals(modelName, ignoreCase = true) ||
            model.label.equals(modelName, ignoreCase = true)
          )
      }
  }

  fun replayLatestTurn() {
    val sessionPath = _state.value.sessionPath
    if (sessionPath.isNullOrBlank()) {
      onEvent(HanakoChatEvent.SystemMessage("当前没有可重新生成的 HanakoPro 会话"))
      return
    }
    if (_state.value.streaming) {
      onEvent(HanakoChatEvent.SystemMessage("当前回复还在输出，不能重新生成。"))
      return
    }
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      resetActiveTurnState()
      _state.update { it.copy(streaming = true, lastError = null) }
      onEvent(HanakoChatEvent.SystemMessage("正在重新生成上一轮..."))
      runCatching {
        withContext(Dispatchers.IO) { http.replayLatestUserMessage(info, sessionPath) }
        withContext(Dispatchers.IO) {
          enrichHistoryWithRuntimeTerminalOutput(info, http.listMessages(info, sessionPath))
        }
      }.onSuccess { snapshot ->
        if (_state.value.sessionPath != sessionPath) {
          Log.i("NBG_HANAKO", "ignore stale replay history for $sessionPath; current=${_state.value.sessionPath}")
          _state.update { it.copy(streaming = false) }
          return@onSuccess
        }
        _state.update { it.copy(streaming = false, lastError = null) }
        saveCachedHistory(sessionPath, snapshot, keepFullerExisting = false)
        onEvent(HanakoChatEvent.HistoryLoaded(sessionPath, snapshot.messages, snapshot.todos, snapshot.sessionFiles))
        onEvent(HanakoChatEvent.SystemMessage("已重新生成上一轮"))
        requestContextUsage(sessionPath)
        refreshSessions()
      }.onFailure { error ->
        handleHanakoLocalApiFailure(info, error)
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(streaming = false, lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("重新生成失败：$message"))
      }
    }
  }

  fun revertLatestTurn() {
    val sessionPath = _state.value.sessionPath
    if (sessionPath.isNullOrBlank()) {
      onEvent(HanakoChatEvent.SystemMessage("当前没有可撤回的 HanakoPro 会话"))
      return
    }
    if (_state.value.streaming) {
      onEvent(HanakoChatEvent.SystemMessage("当前回复还在输出，不能撤回。"))
      return
    }
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      resetActiveTurnState()
      _state.update { it.copy(streaming = true, lastError = null) }
      onEvent(HanakoChatEvent.SystemMessage("正在撤回上一轮..."))
      runCatching {
        val before = withContext(Dispatchers.IO) { http.listMessages(info, sessionPath) }
        val sinceTs = latestAssistantTurnUserTimestamp(before.messages)
        val restoredFiles = withContext(Dispatchers.IO) { http.revertLatestTurn(info, sessionPath, sinceTs) }
        val after = withContext(Dispatchers.IO) {
          enrichHistoryWithRuntimeTerminalOutput(info, http.listMessages(info, sessionPath))
        }
        restoredFiles to after
      }.onSuccess { (restoredFiles, snapshot) ->
        if (_state.value.sessionPath != sessionPath) {
          Log.i("NBG_HANAKO", "ignore stale revert history for $sessionPath; current=${_state.value.sessionPath}")
          _state.update { it.copy(streaming = false, lastError = null) }
          return@onSuccess
        }
        _state.update { it.copy(streaming = false, lastError = null) }
        saveCachedHistory(sessionPath, snapshot, keepFullerExisting = false)
        onEvent(HanakoChatEvent.HistoryLoaded(sessionPath, snapshot.messages, snapshot.todos, snapshot.sessionFiles))
        val suffix = if (restoredFiles > 0) "，已还原 $restoredFiles 个文件" else ""
        onEvent(HanakoChatEvent.SystemMessage("已撤回上一轮$suffix"))
        requestContextUsage(sessionPath)
        refreshSessions()
      }.onFailure { error ->
        handleHanakoLocalApiFailure(info, error)
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(streaming = false, lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("撤回上一轮失败：$message"))
      }
    }
  }

  fun abort() {
    val sessionPath = _state.value.sessionPath ?: return
    abortActiveTurn(sessionPath, userVisible = true)
  }

  private fun abortActiveTurn(sessionPath: String? = _state.value.sessionPath, userVisible: Boolean = false) {
    val path = sessionPath?.takeIf { it.isNotBlank() } ?: return
    webSocket?.send(JSONObject().put("type", "abort").put("sessionPath", path).toString())
    _state.update { it.copy(streaming = false) }
    resetActiveTurnState()
    if (userVisible) {
      onEvent(HanakoChatEvent.ToolInterrupted)
    }
  }

  fun resolveConfirmation(confirmId: String, action: String) {
    val id = confirmId.trim()
    if (id.isBlank() || action !in setOf("confirmed", "rejected")) return
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        withContext(Dispatchers.IO) { http.resolveConfirmation(info, id, action) }
      }.onSuccess {
        onEvent(HanakoChatEvent.ConfirmationResolved(id, action))
      }.onFailure { error ->
        handleHanakoLocalApiFailure(info, error)
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("确认操作失败：$message"))
      }
    }
  }

  fun completeTodos() {
    val sessionPath = _state.value.sessionPath
    if (sessionPath.isNullOrBlank()) {
      onEvent(HanakoChatEvent.SystemMessage("当前没有可完成的 HanakoPro 任务清单"))
      return
    }
    if (_state.value.streaming) {
      onEvent(HanakoChatEvent.SystemMessage("当前回复还在输出，不能完成任务清单。"))
      return
    }
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        withContext(Dispatchers.IO) { http.completeTodos(info, sessionPath) }
      }.onSuccess {
        onEvent(HanakoChatEvent.TodoUpdated(emptyList()))
        onEvent(HanakoChatEvent.SystemMessage("已完成当前任务清单"))
        requestContextUsage(sessionPath)
      }.onFailure { error ->
        handleHanakoLocalApiFailure(info, error)
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("完成任务清单失败：$message"))
      }
    }
  }

  fun interruptTerminalByHuman(terminalId: String) {
    val id = terminalId.trim()
    if (id.isBlank()) return
    scope.launch {
      runCatching {
        val info = resolveApiServerInfo()
        withContext(Dispatchers.IO) { http.interruptTerminalByHuman(info, id) }
      }.onSuccess {
        onEvent(HanakoChatEvent.SystemMessage("已向终端发送 Ctrl+C"))
      }.onFailure { error ->
        handleHanakoLocalApiFailure(serverInfo, error)
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("打断终端失败：$message"))
      }
    }
  }

  fun abortTeamTask(taskId: String? = _state.value.teamTask?.taskId) {
    val id = taskId?.trim().orEmpty()
    if (id.isBlank()) return
    val currentTask = _state.value.teamTask
    if (currentTask?.taskId == id && currentTask.isLocalSessionExecution()) {
      abort()
      val next = currentTask.withActiveTurnFinished("aborted", "已停止本地编排任务")
      _state.update { current ->
        current.copy(
          teamTask = next,
          runtimeStatus = current.runtimeStatus.copy(usageLabel = next.teamRuntimeLabel()),
        )
      }
      onEvent(HanakoChatEvent.TeamTaskUpdated(next))
      return
    }
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        withContext(Dispatchers.IO) { http.abortTeamTask(info, id) }
      }.onSuccess {
        _state.update { current ->
          val next = current.teamTask
            ?.withUpdatedActiveAgents(status = "aborted", summary = "已停止")
            ?.copy(status = "aborted", summary = "已停止")
          current.copy(teamTask = next, runtimeStatus = current.runtimeStatus.copy(usageLabel = next?.teamRuntimeLabel()))
        }
        onEvent(HanakoChatEvent.TeamTaskUpdated(_state.value.teamTask))
        onEvent(HanakoChatEvent.SystemMessage("已停止${if (currentTask?.isBackendAgentToolExecution() == true) "后台多 Agent" else "代码团队"}任务。"))
      }.onFailure { error ->
        handleHanakoLocalApiFailure(info, error)
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("停止代码团队任务失败：$message"))
      }
    }
  }

  fun abortTeamAgent(taskId: String, agentId: String) {
    val teamId = taskId.trim()
    val memberId = agentId.trim()
    if (teamId.isBlank() || memberId.isBlank()) return
    val currentTask = _state.value.teamTask
    if (currentTask?.taskId == teamId && currentTask.isLocalSessionExecution()) {
      abortTeamTask(teamId)
      return
    }
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      runCatching {
        withContext(Dispatchers.IO) { http.abortTeamAgent(info, teamId, memberId) }
      }.onSuccess {
        _state.update { current ->
          val task = current.teamTask ?: return@update current
          val next = task.copy(
            agents = task.agents.map {
              if (it.agentId == memberId) it.copy(status = "aborted", summary = "已停止", updatedAt = System.currentTimeMillis()) else it
            },
          )
          current.copy(
            teamTask = next,
            runtimeStatus = current.runtimeStatus.copy(usageLabel = next.teamRuntimeLabel()),
          )
        }
        onEvent(HanakoChatEvent.TeamTaskUpdated(_state.value.teamTask))
      }.onFailure { error ->
        handleHanakoLocalApiFailure(info, error)
        val message = error.message ?: error.javaClass.simpleName
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("停止 Agent 失败：$message"))
      }
    }
  }

  fun compressForkSession() {
    val sessionPath = _state.value.sessionPath
    if (sessionPath.isNullOrBlank()) {
      onEvent(HanakoChatEvent.SystemMessage("当前没有可压缩的 HanakoPro 会话"))
      return
    }
    if (_state.value.streaming) {
      onEvent(HanakoChatEvent.SystemMessage("当前回复还在输出，不能压缩上下文。"))
      return
    }
    if (!_state.value.compressionAvailable) {
      onEvent(HanakoChatEvent.SystemMessage("当前无法压缩：HanakoPro 未启用上下文压缩，或当前上下文未达到压缩阈值。"))
      return
    }
    if (_state.value.compressing) return
    scope.launch {
      ensureConnected()
      val info = serverInfo ?: return@launch
      _state.update { it.copy(compressing = true, lastError = null) }
      onEvent(HanakoChatEvent.SystemMessage("正在压缩上下文并创建新会话..."))
      runCatching {
        val result = withContext(Dispatchers.IO) { http.compressForkSession(info, sessionPath) }
        val newPath = result.optString("path").ifBlank { error("HanakoPro 没有返回压缩后的 session path") }
        val snapshot = withContext(Dispatchers.IO) {
          enrichHistoryWithRuntimeTerminalOutput(info, http.listMessages(info, newPath))
        }
        newPath to snapshot
      }.onSuccess { (newPath, snapshot) ->
        if (_state.value.sessionPath != sessionPath) {
          Log.i("NBG_HANAKO", "ignore stale compress-fork result for $sessionPath; current=${_state.value.sessionPath}")
          _state.update { it.copy(compressing = false) }
          return@onSuccess
        }
        _state.update { current ->
          current.copy(
            compressing = false,
            sessionPath = newPath,
            contextUsageLabel = null,
            compressionAvailable = false,
            planModeEnabled = false,
            lastError = null,
          )
        }
        saveCachedHistory(newPath, snapshot, keepFullerExisting = false)
        onEvent(HanakoChatEvent.HistoryLoaded(newPath, snapshot.messages, snapshot.todos, snapshot.sessionFiles))
        onEvent(HanakoChatEvent.SystemMessage("已压缩上下文并切换到新会话"))
        refreshSessions()
        requestContextUsage(newPath)
      }.onFailure { error ->
        handleHanakoLocalApiFailure(info, error)
        val rawMessage = error.message ?: error.javaClass.simpleName
        val disabled = rawMessage.contains("context compression disabled", ignoreCase = true)
        val message = if (disabled) {
          "HanakoPro 未启用上下文压缩。请先在上下文压缩设置中开启后再使用。"
        } else {
          rawMessage
        }
        _state.update { it.copy(compressing = false, compressionAvailable = if (disabled) false else it.compressionAvailable, lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("压缩上下文失败：$message"))
      }
    }
  }

  fun searchSessions(query: String) {
    val normalized = query.trim()
    if (normalized.isEmpty()) {
      _state.update { it.copy(searchQuery = "", searchResults = emptyList(), searching = false) }
      return
    }
    scope.launch {
      _state.update { it.copy(searchQuery = normalized, searching = true, lastError = null) }
      val localResults = withContext(Dispatchers.IO) { historyStore.searchSummaryIndex(normalized) }
      _state.update { current ->
        if (current.searchQuery == normalized) {
          current.copy(searchResults = localResults, searching = true)
        } else {
          current
        }
      }
      ensureConnected()
      val info = serverInfo
      if (info == null) {
        _state.update { current ->
          if (current.searchQuery == normalized) current.copy(searching = false) else current
        }
        return@launch
      }
      runCatching {
        withContext(Dispatchers.IO) { http.searchSessions(info, normalized) }
      }.onSuccess { results ->
        val merged = nbgMergeSessionSearchResults(results, localResults)
        _state.update { current ->
          if (current.searchQuery == normalized) {
            current.copy(searchResults = merged, searching = false)
          } else {
            current
          }
        }
      }.onFailure { error ->
        val message = error.message ?: error.javaClass.simpleName
        handleHanakoLocalApiFailure(info, error)
        Log.w("NBG_HANAKO", "search sessions failed", error)
        _state.update { current ->
          if (current.searchQuery == normalized) {
            current.copy(
              searchResults = if (current.searchResults.isEmpty()) localResults else current.searchResults,
              searching = false,
              lastError = if (localResults.isEmpty()) message else current.lastError,
            )
          } else {
            current
          }
        }
      }
    }
  }

  private fun connect(allowLaunch: Boolean = true) {
    val requestSerial = ++connectRequestSerial
    activeConnectAllowsLaunch = allowLaunch
    scope.launch {
      _state.update {
        it.copy(
          connecting = true,
          prewarming = allowLaunch && it.prewarming,
          lastError = null,
          connectionLabel = if (allowLaunch) "正在启动 HanakoPro" else "正在探测 HanakoPro",
        )
      }
      runCatching { connectOnce(allowLaunch, requestSerial) }
        .onFailure { error ->
          if (requestSerial != connectRequestSerial) {
            Log.i("NBG_HANAKO", "ignore stale connect failure request=$requestSerial current=$connectRequestSerial")
            return@onFailure
          }
          val message = error.message ?: error.javaClass.simpleName
          if (allowLaunch) {
            Log.w("NBG_HANAKO", "connect failed", error)
          } else {
            Log.i("NBG_HANAKO", "HanakoPro not running; local history only")
          }
          _state.update {
            if (allowLaunch) {
              it.copy(
                connected = false,
                connecting = false,
                prewarming = false,
                streaming = false,
                selectingSessionPath = null,
                lastError = message,
                connectionLabel = "HanakoPro 未连接",
              )
            } else {
              it.copy(
                connected = false,
                connecting = false,
                prewarming = false,
                streaming = false,
                selectingSessionPath = null,
                connectionLabel = "HanakoPro 未启动",
              )
            }
          }
          if (allowLaunch) scheduleReconnect(message)
        }
    }
  }

  private suspend fun connectOnce(allowLaunch: Boolean, requestSerial: Long? = null) {
    val info = resolveLiveServerInfo(allowLaunch)
    if (isStaleConnectSuccess(requestSerial)) return
    serverInfo = info
    val health = withContext(Dispatchers.IO) {
      http.getJson(info, "/api/health")
    }
    if (isStaleConnectSuccess(requestSerial)) return
    withContext(Dispatchers.Main.immediate) {
      if (isStaleConnectSuccess(requestSerial)) return@withContext
      _state.update {
        it.copy(
          searchResults = emptyList(),
          searchQuery = "",
          searching = false,
          prewarming = false,
          agentName = health.optString("agent").ifBlank { null },
          modelName = health.optString("model").ifBlank { null },
          contextUsageLabel = null,
          compressionAvailable = false,
          planModeEnabled = false,
          connectionLabel = "HanakoPro 已连接",
        )
      }
      openWebSocket(info)
    }
    syncDefaultUrlApiModel()
    syncActiveUrlApiProviders(info)
    scope.launch(Dispatchers.IO) {
      if (isStaleConnectSuccess(requestSerial)) return@launch
      runCatching { http.applyAndroidRuntimeDefaults(info) }
        .onFailure { Log.w("NBG_HANAKO", "apply Android runtime defaults failed", it) }
      runCatching {
        val verifiedBundledSkillNames = nbgVerifiedAndroidBundledSkillNames { skillName, relativePath ->
          runCatching {
            appContext.assets.open(nbgBundledSkillAssetPath(skillName, relativePath)).use { input -> input.readBytes() }
          }.getOrNull()
        }
        http.ensureAndroidBundledSkillsEnabled(info, verifiedBundledSkillNames)
      }
        .onFailure { Log.w("NBG_HANAKO", "ensure Android bundled skills failed", it) }
      runCatching { http.listSessions(info) }
        .onSuccess { sessions ->
          _state.update { current ->
            current.copy(
              sessions = sessions.ifEmpty { current.sessions },
              lastError = null,
            )
          }
        }
        .onFailure { Log.w("NBG_HANAKO", "list sessions after connect failed", it) }
    }
  }

  private fun isStaleConnectSuccess(requestSerial: Long?): Boolean {
    if (requestSerial == null || requestSerial == connectRequestSerial) return false
    Log.i("NBG_HANAKO", "ignore stale connect success request=$requestSerial current=$connectRequestSerial")
    return true
  }

  private fun loadHistory(sessionPath: String) {
    val requestSerial = ++historyRequestSerial
    scope.launch {
      runCatching {
        val info = resolveApiServerInfo()
        withContext(Dispatchers.IO) {
          val remote = http.listMessages(info, sessionPath)
          enrichHistoryWithLocalToolPreviews(info, sessionPath, remote)
        }
      }.onSuccess { snapshot ->
        if (requestSerial != historyRequestSerial || _state.value.sessionPath != sessionPath) {
          Log.i(
            "NBG_HANAKO",
            "ignore stale history for $sessionPath request=$requestSerial current=${_state.value.sessionPath}",
          )
          return@onSuccess
        }
        Log.i("NBG_HANAKO", "loaded ${snapshot.messages.size} history messages, ${snapshot.todos.size} todos, ${snapshot.sessionFiles.size} files for $sessionPath")
        saveCachedHistory(sessionPath, snapshot)
        onEvent(HanakoChatEvent.HistoryLoaded(sessionPath, snapshot.messages, snapshot.todos, snapshot.sessionFiles))
      }.onFailure { error ->
        if (requestSerial != historyRequestSerial || _state.value.sessionPath != sessionPath) {
          Log.i(
            "NBG_HANAKO",
            "ignore stale history failure for $sessionPath request=$requestSerial current=${_state.value.sessionPath}",
          )
          return@onFailure
        }
        val message = error.message ?: error.javaClass.simpleName
        Log.w("NBG_HANAKO", "load history failed for $sessionPath", error)
        handleHanakoLocalApiFailure(serverInfo, error)
        _state.update { it.copy(lastError = message) }
        onEvent(HanakoChatEvent.SystemMessage("读取 HanakoPro 历史失败：$message"))
      }
    }
  }

  private fun scheduleBackgroundWarmup() {
    if (warmupJob?.isActive == true || _state.value.connected || _state.value.prewarming) return
    warmupJob = scope.launch {
      delay(BACKGROUND_WARMUP_DELAY_MS)
      if (_state.value.connected || _state.value.prewarming || activeConnectAllowsLaunch) return@launch
      val adoptingEarlyLaunch = launcher.isLaunching
      Log.i(
        "NBG_HANAKO",
        if (adoptingEarlyLaunch) "background warmup adopting existing HanakoPro launcher" else "background warmup starting HanakoPro",
      )
      _state.update {
        if (it.connected || it.prewarming || activeConnectAllowsLaunch) {
          it
        } else {
          it.copy(prewarming = true, connectionLabel = "后台预热 HanakoPro")
        }
      }
      if (adoptingEarlyLaunch) {
        waitForWarmupConnection()
      } else {
        connect(allowLaunch = true)
      }
    }
  }

  private suspend fun waitForWarmupConnection() {
    val deadline = System.currentTimeMillis() + CONNECT_WAIT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      val existing = withContext(Dispatchers.IO) { findServerInfo(appContext) }
      if (existing != null && withContext(Dispatchers.IO) { http.isHealthy(existing) }) {
        connect(allowLaunch = false)
        return
      }
      delay(CONNECT_WAIT_POLL_MS)
    }
    _state.update {
      it.copy(
        connected = false,
        connecting = false,
        prewarming = false,
        streaming = false,
        selectingSessionPath = null,
        connectionLabel = "HanakoPro 未连接",
      )
    }
  }

  private fun restoreLatestCachedSessionOnce() {
    if (restoredInitialCache) return
    restoredInitialCache = true
    scope.launch {
      val cached = withContext(Dispatchers.IO) { historyStore.readLatestCachedSession() } ?: return@launch
      if (_state.value.sessionPath == null) {
        _state.update { current ->
          current.copy(
            sessionPath = cached.sessionPath,
            sessions = listOf(
              HanakoSessionSummary(
                path = cached.sessionPath,
                title = cached.title,
                subtitle = "本地缓存",
              ),
            ),
            connectionLabel = if (current.connecting || current.connected) current.connectionLabel else "正在恢复本地历史",
          )
        }
        Log.i("NBG_HANAKO", "restored cached history for ${cached.sessionPath} messages=${cached.snapshot.messages.size}")
        saveCachedHistory(cached.sessionPath, cached.snapshot)
        onEvent(HanakoChatEvent.HistoryLoaded(cached.sessionPath, cached.snapshot.messages, cached.snapshot.todos, cached.snapshot.sessionFiles))
      }
    }
  }

  private fun loadCachedHistory(sessionPath: String, requestSerial: Long? = null) {
    scope.launch {
      val cached = withContext(Dispatchers.IO) { historyStore.readCachedHistory(sessionPath) } ?: return@launch
      val canShowCachedHistory =
        _state.value.sessionPath == sessionPath ||
          _state.value.connecting ||
          requestSerial == focusRequestSerial
      if (canShowCachedHistory) {
        Log.i("NBG_HANAKO", "loaded cached history for $sessionPath")
        onEvent(HanakoChatEvent.HistoryLoaded(sessionPath, cached.messages, cached.todos, cached.sessionFiles))
      }
    }
  }

  private fun saveCachedHistory(
    sessionPath: String,
    snapshot: HanakoHistorySnapshot,
    keepFullerExisting: Boolean = true,
  ) {
    if (sessionPath.isBlank() || snapshot.messages.isEmpty()) return
    val stateTitle = _state.value.sessions.firstOrNull { it.path == sessionPath }?.title
    scope.launch(Dispatchers.IO) {
      runCatching {
        val snapshotToSave = if (keepFullerExisting) {
          historyStore.readCachedHistory(sessionPath)
            ?.takeIf { existing -> existing.messages.size > snapshot.messages.size }
            ?.also { existing ->
              Log.i(
                "NBG_HANAKO",
                "keep fuller cached/local history for $sessionPath existing=${existing.messages.size} incoming=${snapshot.messages.size}",
              )
            }
            ?: snapshot
        } else {
          snapshot
        }
        val title = stateTitle
          ?: snapshotToSave.messages.firstOrNull { it.role == "user" }?.text?.lineSequence()?.firstOrNull()?.take(40)
          ?: "新聊天"
        val updatedAtMs = System.currentTimeMillis()
        val cacheDir = historyStore.historyCacheDir()
        cacheDir.mkdirs()
        val body = JSONObject()
          .put("version", 1)
          .put("sessionPath", sessionPath)
          .put("title", title)
          .put("updatedAt", updatedAtMs)
          .put("snapshot", snapshotToSave.toJson())
        val file = historyStore.historyCacheFile(sessionPath)
        val tmp = File(cacheDir, "${file.name}.tmp")
        tmp.writeText(body.toString(), Charsets.UTF_8)
        nbgMoveReplacingWithAtomicFallback(tmp, file)
        File(cacheDir, HANA_HISTORY_CACHE_INDEX).writeText(
          JSONObject()
            .put("sessionPath", sessionPath)
            .put("title", title)
            .toString(),
          Charsets.UTF_8,
        )
        historyStore.writeSummaryIndexEntry(
          sessionPath = sessionPath,
          title = title,
          snapshot = snapshotToSave,
          updatedAtMs = updatedAtMs,
        )
      }.onFailure { Log.w("NBG_HANAKO", "save history cache failed for $sessionPath", it) }
    }
  }

  private fun enrichHistoryWithLocalToolPreviews(
    info: HanakoServerInfo,
    sessionPath: String,
    remote: HanakoHistorySnapshot,
  ): HanakoHistorySnapshot {
    val local = historyStore.readLocalSessionFileByPath(sessionPath)
      ?: return enrichHistoryWithRuntimeTerminalOutput(info, remote)
    if (local.messages.size > remote.messages.size) {
      Log.i(
        "NBG_HANAKO",
        "using full local history for $sessionPath remote=${remote.messages.size} local=${local.messages.size}",
      )
      return enrichHistoryWithRuntimeTerminalOutput(info, local.copy(
        todos = remote.todos.ifEmpty { local.todos },
        sessionFiles = remote.sessionFiles.ifEmpty { local.sessionFiles },
      ))
    }
    val remotePreviewCount = remote.messages.count { it.toolStatus?.hasInlinePreview == true }
    val localPreviewCount = local.messages.count { it.toolStatus?.hasInlinePreview == true }
    if (localPreviewCount <= remotePreviewCount) return enrichHistoryWithRuntimeTerminalOutput(info, remote)
    Log.i(
      "NBG_HANAKO",
      "using local tool previews for $sessionPath remote=$remotePreviewCount local=$localPreviewCount",
    )
    return enrichHistoryWithRuntimeTerminalOutput(info, local.copy(
      todos = remote.todos.ifEmpty { local.todos },
      sessionFiles = remote.sessionFiles.ifEmpty { local.sessionFiles },
    ))
  }

  private fun enrichHistoryWithRuntimeTerminalOutput(
    info: HanakoServerInfo,
    snapshot: HanakoHistorySnapshot,
  ): HanakoHistorySnapshot {
    if (snapshot.messages.none { it.toolStatus?.terminalOutput != null }) return snapshot
    val terminalCache = mutableMapOf<String, HanakoTerminalOutput>()
    val nextMessages = snapshot.messages.map { message ->
      val tool = message.toolStatus ?: return@map message
      val terminal = tool.terminalOutput ?: return@map message
      val enriched = enrichTerminalOutput(info, terminal, terminalCache) ?: return@map message
      if (enriched == terminal) message else message.copy(toolStatus = tool.copy(terminalOutput = enriched))
    }
    return snapshot.copy(messages = nextMessages)
  }

  private fun enrichTerminalOutput(
    info: HanakoServerInfo,
    terminal: HanakoTerminalOutput,
    cache: MutableMap<String, HanakoTerminalOutput>,
  ): HanakoTerminalOutput? {
    if (terminal.sessionId.isBlank()) return null
    val wantsSlice = terminal.sliceFrom != null && terminal.sliceTo != null && terminal.sliceTo > terminal.sliceFrom
    val shouldFetch = wantsSlice || terminal.output.isBlank()
    if (!shouldFetch) return terminal.copy(output = nbgNormalizeTerminalOutput(terminal.output))
    val cacheKey = if (wantsSlice) {
      "slice:${terminal.sessionId}:${terminal.sliceFrom}:${terminal.sliceTo}"
    } else {
      "snapshot:${terminal.sessionId}"
    }
    cache[cacheKey]?.let { return it }
    return runCatching {
      val fetched = if (wantsSlice) {
        http.getTerminalSlice(info, terminal)
      } else {
        http.getTerminalSnapshot(info, terminal)
      }
      if (fetched.output.isBlank()) terminal else fetched
    }.onFailure {
      Log.i("NBG_HANAKO", "terminal output hydrate failed for ${terminal.sessionId}: ${it.message}")
    }.getOrNull()?.also { cache[cacheKey] = it }
  }

  private fun ubuntuRootHomeDir(): File =
    File(appContext.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu/root")

  private fun refreshSessions() {
    scope.launch {
      runCatching {
        val info = resolveApiServerInfo()
        withContext(Dispatchers.IO) { http.listSessions(info) }
      }.onSuccess { sessions ->
        _state.update { it.copy(sessions = sessions) }
      }
    }
  }

  private suspend fun resolveLiveServerInfo(allowLaunch: Boolean): HanakoServerInfo {
    val existing = withContext(Dispatchers.IO) { findServerInfo(appContext) }
    if (existing != null) {
      if (withContext(Dispatchers.IO) { http.isHealthy(existing) }) {
        Log.i("NBG_HANAKO", "reuse healthy HanakoPro server on ${existing.port}")
        return existing
      }
      if (allowLaunch) {
        withContext(Dispatchers.IO) { clearStaleHanakoServer(appContext, existing) }
      }
    }
    if (!allowLaunch) {
      error("HanakoPro 未运行")
    }

    withContext(Dispatchers.Main.immediate) {
      _state.update { it.copy(connectionLabel = "正在启动 HanakoPro") }
    }
    withContext(Dispatchers.IO) {
      launcher.startIfNeeded(appContext)
    }

    var lastError: String? = null
    repeat(LAUNCH_HEALTH_CHECK_ATTEMPTS) {
      delay(LAUNCH_HEALTH_CHECK_INTERVAL_MS)
      val info = withContext(Dispatchers.IO) { findServerInfo(appContext) }
      if (info != null) {
        val live = withContext(Dispatchers.IO) {
          runCatching { http.isHealthy(info) }
            .onFailure { lastError = it.message }
            .getOrDefault(false)
        }
        if (live) {
          Log.i("NBG_HANAKO", "HanakoPro became healthy on ${info.port}")
          return info
        }
      }
    }
    error(lastError ?: "HanakoPro 未就绪；内置 server pack 启动失败，或 Ubuntu 内源码未完成 npm install")
  }

  private suspend fun ensureConnected() {
    var restarted = false
    serverInfo?.takeIf { _state.value.connected && webSocket != null }?.let { info ->
      if (withContext(Dispatchers.IO) { http.isHealthy(info) }) return
      restarted = runCatching {
        restartStaleServer(info, "HanakoPro 健康检查超时")
        true
      }.onFailure { error ->
        val message = error.message ?: error.javaClass.simpleName
        _state.update {
          it.copy(
            connected = false,
            connecting = false,
            prewarming = false,
            streaming = false,
            lastError = message,
            connectionLabel = "HanakoPro 未连接",
          )
        }
      }.getOrDefault(false)
    }
    if (_state.value.connected && webSocket != null) return
    if (_state.value.prewarming || restarted) {
      Log.i("NBG_HANAKO", "reuse active background HanakoPro warmup for send")
    } else if (!_state.value.connecting || !activeConnectAllowsLaunch) {
      connect(allowLaunch = true)
    }

    val deadline = System.currentTimeMillis() + CONNECT_WAIT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      if (_state.value.connected && webSocket != null) return
      delay(CONNECT_WAIT_POLL_MS)
    }
    serverInfo = null
    webSocket?.close(1001, "connect timeout")
    webSocket = null
    _state.update {
      it.copy(
        connected = false,
        connecting = false,
        prewarming = false,
        streaming = false,
        compressing = false,
        connectionLabel = "HanakoPro 未连接",
      )
    }
  }

  private fun openWebSocket(info: HanakoServerInfo) {
    webSocket?.close(1000, "replace")
    val request = Request.Builder().url(info.wsUrl).build()
    val nextSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
      private fun isCurrent(socket: WebSocket): Boolean = webSocket === socket

      override fun onOpen(webSocket: WebSocket, response: Response) {
        scope.launch {
          if (!isCurrent(webSocket)) return@launch
          _state.update {
            it.copy(connected = true, connecting = false, prewarming = false, lastError = null, connectionLabel = "HanakoPro 已连接")
          }
          _state.value.sessionPath?.let { requestContextUsage(it) }
        }
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        if (!isCurrent(webSocket)) return
        handleWsMessage(webSocket, text)
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        scope.launch {
          if (!isCurrent(webSocket)) return@launch
          _state.update { it.copy(connected = false, connecting = false, prewarming = false, streaming = false, selectingSessionPath = null, connectionLabel = "HanakoPro 已断开") }
          scheduleReconnect("WebSocket closed: $code $reason")
        }
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        scope.launch {
          if (!isCurrent(webSocket)) return@launch
          val message = t.message ?: t.javaClass.simpleName
          _state.update {
            it.copy(
              connected = false,
              connecting = false,
              prewarming = false,
              streaming = false,
              selectingSessionPath = null,
              lastError = message,
              connectionLabel = "HanakoPro 连接错误",
            )
          }
          scheduleReconnect(message)
        }
      }
    })
    webSocket = nextSocket
  }

  private fun handleWsMessage(sourceSocket: WebSocket, text: String) {
    val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
    scope.launch {
      if (webSocket !== sourceSocket) return@launch
      val type = msg.optString("type")
      if (type != "session_title" && !isCurrentSessionMessage(msg)) return@launch
      if (type != "stream_resume") {
        if (!shouldAcceptStreamEvent(msg)) return@launch
        updateStreamMeta(msg)
      }
      handleWsPayload(msg)
    }
  }

  private fun handleWsPayload(msg: JSONObject) {
    when (val type = msg.optString("type")) {
      "status" -> {
        handleStreamingStatus(msg)
      }
      "session_status" -> {
        handleStreamingStatus(msg)
      }
      "stream_resume" -> {
        handleStreamResume(msg)
      }
      "context_usage" -> {
        handleContextUsage(msg)
      }
      "compaction_start" -> {
        handleCompactionStatus(running = true, msg = msg)
      }
      "compaction_end" -> {
        handleCompactionStatus(running = false, msg = msg)
      }
      "text_delta", "card_text" -> {
        val delta = msg.rawStringAny("delta", "text")
        if (delta.isNotEmpty()) {
          appendAssistantDelta(delta)
        }
      }
      "mood_text" -> Unit
      "thinking_delta" -> {
        val delta = msg.rawStringAny("delta", "text")
        if (delta.isNotEmpty()) appendThinkingDelta(delta)
      }
      "slash_result" -> {
        val resultText = msg.optString("text")
        if (resultText.isNotBlank()) {
          onEvent(HanakoChatEvent.SystemMessage(resultText))
        }
        finishLocalClientTurn()
      }
      "session_user_message" -> {
        parseHanakoExternalUserMessage(msg.optJSONObject("message"))?.let {
          onEvent(HanakoChatEvent.ExternalUserMessage(it))
        }
      }
      "content_block" -> {
        sealAssistantTextSegment()
        val block = msg.optJSONObject("block")
        val confirmation = parseHanakoConfirmationBlock(block)
        if (confirmation != null) {
          onEvent(HanakoChatEvent.ConfirmationRequested(confirmation))
        } else {
          parseHanakoContentBlock(block)?.let {
            onEvent(HanakoChatEvent.ContentBlock(it))
          }
        }
      }
      "tool_progress", "tool_update", "tool_status", "terminal_output", "terminal_status", "file_write_prepare", "vision_progress" -> {
        if (type == "file_write_prepare" || type == "vision_progress") {
          sealAssistantTextSegment()
        }
        parseToolStatus(msg)?.let {
          onEvent(HanakoChatEvent.ToolStatus(it))
        }
      }
      "thinking_start" -> {
        sealAssistantTextSegment()
        beginThinking()
      }
      "thinking_end" -> {
        endThinking()
      }
      "tool_start", "tool_call", "tool_invocation" -> {
        sealAssistantTextSegment()
        val tool = parseToolStart(msg)
        onEvent(HanakoChatEvent.ToolStatus(tool))
        handleBackendAgentToolStarted(msg, tool)
      }
      "tool_end", "tool_result" -> {
        val tool = parseToolEnd(msg)
        onEvent(HanakoChatEvent.ToolStatus(tool))
        handleBackendAgentToolEnded(msg, tool)
        sealAssistantTextSegment()
      }
      "turn_end" -> {
        var finishedFallbackTask: HanakoTeamTaskStatus? = null
        _state.update { current ->
          val nextTask = current.teamTask
            ?.takeIf { it.isLocalSessionExecution() && it.isActive }
            ?.withActiveTurnFinished("completed", "本地编排任务已完成")
          finishedFallbackTask = nextTask
          current.copy(
            streaming = false,
            teamTask = nextTask ?: current.teamTask,
            runtimeStatus = if (nextTask != null) {
              current.runtimeStatus.copy(usageLabel = nextTask.teamRuntimeLabel())
            } else {
              current.runtimeStatus
            },
          )
        }
        finishedFallbackTask?.let { onEvent(HanakoChatEvent.TeamTaskUpdated(it)) }
        _state.value.sessionPath?.let { requestContextUsage(it) }
        assistantMessageId?.let { id ->
          if (!assistantHasText) setAssistantText(id, "HanakoPro 没有返回文本内容。")
        }
        assistantMessageId = null
        assistantHasText = false
        endThinking()
        onEvent(HanakoChatEvent.TurnEnded)
      }
      "session_title" -> {
        val title = msg.optString("title")
        if (title.isNotBlank()) refreshSessions()
      }
      "session_branch_reset" -> {
        resetActiveTurnState()
        _state.value.sessionPath?.let { loadHistory(it) }
      }
      "block_update" -> {
        val taskId = msg.optString("taskId").trim()
        val patch = parseHanakoContentBlockPatch(msg.optJSONObject("patch"))
        if (taskId.isNotBlank() && patch != null) {
          onEvent(HanakoChatEvent.ContentBlockPatch(taskId, patch))
        }
        handleBackendAgentBlockUpdate(msg)
      }
      "todo_update" -> {
        onEvent(HanakoChatEvent.TodoUpdated(parseHanakoTodos(msg.optJSONArray("todos"))))
      }
      "team_task_started", "team_task_completed", "team_task_failed" -> {
        val parsed = parseHanakoTeamTaskStatus(msg, _state.value.teamTask)
          ?.let { task ->
            val status = nbgTeamTaskEventStatus(type, task.status)
            val normalized = task.copy(status = status).normalizedAndroidTeamTask()
            when (status) {
              "completed", "done", "success" -> normalized.withUpdatedActiveAgents(
                status = "completed",
                summary = normalized.summary.ifBlank { "已完成" },
              )
              "failed", "error" -> normalized.withUpdatedActiveAgents(
                status = "failed",
                summary = normalized.summary.ifBlank { "任务失败" },
              )
              else -> normalized
            }
          }
        _state.update {
          val next = parsed ?: it.teamTask
          it.copy(
            teamTask = next,
            runtimeStatus = it.runtimeStatus.copy(
              usageLabel = next?.teamRuntimeLabel() ?: it.runtimeStatus.usageLabel,
            ),
          )
        }
        onEvent(HanakoChatEvent.TeamTaskUpdated(_state.value.teamTask))
      }
      "team_agent_started", "team_agent_update", "team_agent_result" -> {
        val agent = parseHanakoTeamAgentStatus(msg, _state.value.teamTask?.taskId.orEmpty())
        if (agent != null) {
          _state.update { current ->
            val base = current.teamTask ?: HanakoTeamTaskStatus(
              taskId = agent.taskId.ifBlank { "team-${System.currentTimeMillis()}" },
              status = "running",
              agents = emptyList(),
            )
            val next = base.mergeAgentIfTaskCanAccept(agent)
            current.copy(
              teamTask = next,
              runtimeStatus = current.runtimeStatus.copy(usageLabel = next.teamRuntimeLabel()),
            )
          }
          onEvent(HanakoChatEvent.TeamTaskUpdated(_state.value.teamTask))
        }
      }
      "browser_status", "browser_bg_status" -> {
        handleBrowserStatus(msg)
      }
      "desk_changed" -> Unit
      "token_usage" -> {
        handleTokenUsage(msg)
      }
      "plan_mode", "permission_mode", "access_mode" -> {
        handlePlanMode(msg)
      }
      "bridge_status" -> {
        handleBridgeStatus(msg)
      }
      "deferred_result" -> {
        parseDeferredResult(msg)?.let { onEvent(HanakoChatEvent.ToolStatus(it)) }
        handleBackendAgentDeferredResult(msg)
      }
      "error" -> {
        val message = msg.optString("message").ifBlank { "HanakoPro 返回错误" }
        val now = System.currentTimeMillis()
        if (message == lastWsErrorMessage && now - lastWsErrorAtMs < 2_500L) return
        lastWsErrorMessage = message
        lastWsErrorAtMs = now
        val shouldAbortStuckTurn = message.contains("还在说话") ||
          message.contains("still", ignoreCase = true) ||
          message.contains("streaming", ignoreCase = true) ||
          message.contains("timed out", ignoreCase = true) ||
          message.contains("timeout", ignoreCase = true)
        if (shouldAbortStuckTurn) {
          abortActiveTurn(msg.cleanString("sessionPath") ?: _state.value.sessionPath, userVisible = false)
        }
        var failedFallbackTask: HanakoTeamTaskStatus? = null
        _state.update { current ->
          val nextTask = current.teamTask
            ?.takeIf { it.isLocalSessionExecution() && it.isActive }
            ?.withActiveTurnFinished("failed", message)
          failedFallbackTask = nextTask
          current.copy(
            streaming = false,
            lastError = message,
            teamTask = nextTask ?: current.teamTask,
            runtimeStatus = if (nextTask != null) {
              current.runtimeStatus.copy(usageLabel = nextTask.teamRuntimeLabel())
            } else {
              current.runtimeStatus
            },
          )
        }
        failedFallbackTask?.let { onEvent(HanakoChatEvent.TeamTaskUpdated(it)) }
        val id = assistantMessageId ?: beginAssistant("")
        setAssistantText(id, "HanakoPro 错误：$message")
        resetActiveTurnState()
        onEvent(HanakoChatEvent.ToolInterrupted)
        onEvent(HanakoChatEvent.TurnEnded)
      }
      "confirmation_resolved" -> {
        val confirmId = msg.optString("confirmId")
        val action = msg.optString("action")
        if (confirmId.isNotBlank() && action.isNotBlank()) {
          onEvent(HanakoChatEvent.ConfirmationResolved(confirmId, action))
        }
      }
      else -> {
        when {
          type.endsWith("_progress") || msg.looksLikeToolEvent(type) -> {
            parseToolStatus(msg)?.let { onEvent(HanakoChatEvent.ToolStatus(it)) }
          }
          type.endsWith("_status") -> Unit
        }
      }
    }
  }

  private fun handleStreamResume(msg: JSONObject) {
    val resumeSessionPath = msg.optString("sessionPath").trim()
    if (resumeSessionPath.isNotBlank() && resumeSessionPath != _state.value.sessionPath) return
    val effectiveSessionPath = resumeSessionPath.ifBlank { _state.value.sessionPath.orEmpty() }
    val events = msg.optJSONArray("events")
    var replayedEventCount = 0
    var acceptedReplayEventCount = 0
    var skippedReplayEventCount = 0
    var duplicateReplayEventCount = 0
    val reset = msg.optBoolean("reset", false)
    val truncated = msg.optBoolean("truncated", false)
    if (reset) {
      resetActiveTurnState()
      if (effectiveSessionPath.isNotBlank()) {
        streamMetaBySession[effectiveSessionPath] = HanakoStreamMeta(streamId = msg.cleanString("streamId"), lastSeq = 0)
      }
    }
    if (truncated) {
      onEvent(HanakoChatEvent.ToolStatus("已恢复部分实时输出，早期片段被截断"))
    }
    if (events != null) {
      for (index in 0 until events.length()) {
        val entry = events.optJSONObject(index) ?: continue
        val event = entry.optJSONObject("event") ?: continue
        if (event.optString("type") == "stream_resume") continue
        replayedEventCount += 1
        if (!event.has("sessionPath") && resumeSessionPath.isNotBlank()) {
          event.put("sessionPath", resumeSessionPath)
        }
        msg.cleanString("streamId")?.let { event.put("streamId", it) }
        entry.optIntOrNull("seq")?.let { event.put("seq", it) }
        if (!isCurrentSessionMessage(event)) {
          skippedReplayEventCount += 1
          continue
        }
        val skipReason = streamEventSkipReason(event)
        if (skipReason != null) {
          skippedReplayEventCount += 1
          if (skipReason == "duplicate_seq") {
            duplicateReplayEventCount += 1
          }
          continue
        }
        acceptedReplayEventCount += 1
        updateStreamMeta(event)
        if (isCurrentSessionMessage(event)) {
          handleWsPayload(event)
        }
      }
    }
    if (effectiveSessionPath.isNotBlank()) {
      val current = streamMetaBySession[effectiveSessionPath] ?: HanakoStreamMeta(streamId = msg.cleanString("streamId"))
      val nextSeqLast = msg.optIntOrNull("nextSeq")?.let { (it - 1).coerceAtLeast(0) }
      val sinceSeq = msg.optIntOrNull("sinceSeq")
      streamMetaBySession[effectiveSessionPath] = current.copy(
        streamId = msg.cleanString("streamId") ?: current.streamId,
        lastSeq = maxOf(current.lastSeq, nextSeqLast ?: sinceSeq ?: current.lastSeq),
      )
    }
    recordStreamResumeDiagnostics(
      sessionPath = effectiveSessionPath,
      resumeDelta = 1,
      replayedEventDelta = replayedEventCount,
      acceptedReplayEventDelta = acceptedReplayEventCount,
      skippedReplayEventDelta = skippedReplayEventCount,
      duplicateReplayEventDelta = duplicateReplayEventCount,
      truncatedDelta = if (truncated) 1 else 0,
      resetDelta = if (reset) 1 else 0,
    )
    msg.hanakoStreamingFlag()?.let { isStreaming ->
      _state.update { it.copy(streaming = isStreaming) }
    }
  }

  private fun updateStreamMeta(msg: JSONObject) {
    val sessionPath = msg.cleanString("sessionPath") ?: _state.value.sessionPath ?: return
    val incomingStreamId = msg.cleanString("streamId")
    val incomingSeq = msg.optIntOrNull("seq")
    if (incomingStreamId == null && incomingSeq == null) return
    val current = streamMetaBySession[sessionPath] ?: HanakoStreamMeta()
    val nextBase = if (!incomingStreamId.isNullOrBlank() && current.streamId != null && current.streamId != incomingStreamId) {
      HanakoStreamMeta(streamId = incomingStreamId, lastSeq = 0)
    } else {
      current.copy(streamId = incomingStreamId ?: current.streamId)
    }
    streamMetaBySession[sessionPath] = nextBase.copy(
      lastSeq = incomingSeq?.let { maxOf(nextBase.lastSeq, it) } ?: nextBase.lastSeq,
    )
  }

  private fun handleBackendAgentToolStarted(msg: JSONObject, tool: HanakoToolStatus) {
    val toolName = nbgBackendAgentToolNameFromEvent(msg) ?: tool.toolName.canonicalBackendAgentToolName() ?: return
    val taskId = tool.key.takeIf { it.isNotBlank() } ?: "backend-agent-${System.currentTimeMillis()}"
    val task = nbgBackendAgentToolTask(
      taskId = taskId,
      toolName = toolName,
      taskStatus = "running",
      subagentTaskIds = emptyList(),
      summary = if (toolName == "WolfPack") "WolfPack 正在派发子 Agent" else "Agent 子任务正在启动",
      existing = _state.value.teamTask?.takeIf { it.taskId == taskId },
    )
    _state.update {
      it.copy(
        teamTask = task,
        runtimeStatus = it.runtimeStatus.copy(usageLabel = task.teamRuntimeLabel()),
      )
    }
    onEvent(HanakoChatEvent.TeamTaskUpdated(task))
  }

  private fun handleBackendAgentToolEnded(msg: JSONObject, tool: HanakoToolStatus) {
    val toolName = nbgBackendAgentToolNameFromEvent(msg) ?: tool.toolName.canonicalBackendAgentToolName() ?: return
    val current = _state.value.teamTask
    val taskId = current?.takeIf { it.isBackendAgentToolExecution() }?.taskId
      ?: tool.key.takeIf { it.isNotBlank() }
      ?: "backend-agent-${System.currentTimeMillis()}"
    val subagentTaskIds = nbgBackendAgentToolSubagentTaskIds(msg)
    val status = nbgBackendAgentToolTaskStatus(
      streamStatus = nbgBackendAgentToolStreamStatus(msg),
      success = tool.success,
      hasSubagentTasks = subagentTaskIds.isNotEmpty(),
    )
    val task = nbgBackendAgentToolTask(
      taskId = taskId,
      toolName = toolName,
      taskStatus = status,
      subagentTaskIds = subagentTaskIds,
      summary = when {
        status == "running" && subagentTaskIds.isNotEmpty() -> "后台已派发 ${subagentTaskIds.size} 个子 Agent"
        status == "completed" -> "后台 Agent 工具已完成"
        status == "failed" -> "后台 Agent 工具失败"
        else -> "后台 Agent 工具已更新"
      },
      existing = current?.takeIf { it.taskId == taskId && it.isBackendAgentToolExecution() },
    )
    _state.update {
      it.copy(
        teamTask = task,
        runtimeStatus = it.runtimeStatus.copy(usageLabel = task.teamRuntimeLabel()),
      )
    }
    onEvent(HanakoChatEvent.TeamTaskUpdated(task))
  }

  private fun handleBackendAgentBlockUpdate(msg: JSONObject) {
    val taskId = msg.cleanString("taskId") ?: return
    val patch = msg.optJSONObject("patch")
    val streamStatus = patch?.cleanStringAny("streamStatus", "stream_status", "status", "state")
    val status = nbgBackendAgentToolTaskStatus(streamStatus, success = null, hasSubagentTasks = false)
    val summary = patch?.cleanStringAny("summary", "message", "detail").orEmpty()
    updateBackendAgentSubtask(taskId, status, summary)
  }

  private fun handleBackendAgentDeferredResult(msg: JSONObject) {
    val taskId = msg.cleanString("taskId") ?: return
    val normalized = nbgNormalizeHanakoStatus(msg.cleanString("status"))
    val status = when (normalized) {
      "success", "done", "completed" -> "completed"
      "failed", "failure", "error" -> "failed"
      "aborted", "cancelled", "canceled" -> "aborted"
      else -> normalized.ifBlank { "completed" }
    }
    val summary = msg.cleanString("reason")
      ?: msg.optJSONObject("meta")?.cleanStringAny("summary", "message")
      ?: when (status) {
        "completed" -> "子 Agent 已完成"
        "failed" -> "子 Agent 失败"
        "aborted" -> "子 Agent 已停止"
        else -> "子 Agent 已更新"
      }
    updateBackendAgentSubtask(taskId, status, summary)
  }

  private fun updateBackendAgentSubtask(taskId: String, status: String, summary: String) {
    var nextTask: HanakoTeamTaskStatus? = null
    _state.update { current ->
      val activeTask = current.teamTask
        ?.takeIf { it.isBackendAgentToolExecution() && it.agents.any { agent -> taskId in agent.artifactRefs || agent.agentId == nbgTeamAgentId(taskId) } }
        ?: return@update current
      val next = activeTask.withBackendAgentToolSubtaskUpdate(taskId, status, summary)
      nextTask = next
      current.copy(
        teamTask = next,
        runtimeStatus = current.runtimeStatus.copy(usageLabel = next.teamRuntimeLabel()),
      )
    }
    nextTask?.let { onEvent(HanakoChatEvent.TeamTaskUpdated(it)) }
  }

  private fun shouldAcceptStreamEvent(msg: JSONObject): Boolean {
    return streamEventSkipReason(msg) == null
  }

  private fun streamEventSkipReason(msg: JSONObject): String? {
    val sessionPath = msg.cleanString("sessionPath") ?: _state.value.sessionPath ?: return null
    val incomingSeq = msg.optIntOrNull("seq") ?: return null
    val incomingStreamId = msg.cleanString("streamId")
    val current = streamMetaBySession[sessionPath] ?: return null
    if (!incomingStreamId.isNullOrBlank() && current.streamId != null && current.streamId != incomingStreamId) return null
    return if (incomingSeq > current.lastSeq) null else "duplicate_seq"
  }

  private fun recordStreamResumeDiagnostics(
    sessionPath: String?,
    requestDelta: Int = 0,
    resumeDelta: Int = 0,
    replayedEventDelta: Int = 0,
    acceptedReplayEventDelta: Int = 0,
    skippedReplayEventDelta: Int = 0,
    duplicateReplayEventDelta: Int = 0,
    truncatedDelta: Int = 0,
    resetDelta: Int = 0,
  ) {
    val meta = sessionPath
      ?.takeIf { it.isNotBlank() }
      ?.let { streamMetaBySession[it] }
    _state.update { current ->
      val previous = current.streamResumeDiagnostics
      current.copy(
        streamResumeDiagnostics = previous.copy(
          requestCount = previous.requestCount + requestDelta,
          resumeCount = previous.resumeCount + resumeDelta,
          replayedEventCount = previous.replayedEventCount + replayedEventDelta,
          acceptedReplayEventCount = previous.acceptedReplayEventCount + acceptedReplayEventDelta,
          skippedReplayEventCount = previous.skippedReplayEventCount + skippedReplayEventDelta,
          duplicateReplayEventCount = previous.duplicateReplayEventCount + duplicateReplayEventDelta,
          truncatedResumeCount = previous.truncatedResumeCount + truncatedDelta,
          resetResumeCount = previous.resetResumeCount + resetDelta,
          cursorPresent = meta != null && (!meta.streamId.isNullOrBlank() || meta.lastSeq > 0),
          lastSeq = meta?.lastSeq ?: 0,
        ),
      )
    }
  }

  private fun requestContextUsage(sessionPath: String?) {
    val path = sessionPath?.takeIf { it.isNotBlank() } ?: return
    webSocket?.send(JSONObject().put("type", "context_usage").put("sessionPath", path).toString())
  }

  private fun requestStreamResume(sessionPath: String?) {
    val path = sessionPath?.takeIf { it.isNotBlank() } ?: return
    val meta = streamMetaBySession[path] ?: HanakoStreamMeta()
    recordStreamResumeDiagnostics(sessionPath = path, requestDelta = 1)
    webSocket?.send(
      JSONObject()
        .put("type", "resume_stream")
        .put("sessionPath", path)
        .put("streamId", meta.streamId ?: JSONObject.NULL)
        .put("sinceSeq", meta.lastSeq)
        .toString(),
    )
  }

  private fun androidUiContext(sessionPath: String): JSONObject =
    JSONObject()
      .put("currentViewed", "android-chat")
      .put("locale", NBG_ANDROID_LANGUAGE_LOCALE)
      .put("language", NBG_ANDROID_LANGUAGE_LOCALE)
      .put("activeFile", JSONObject.NULL)
      .put("activePreview", JSONObject.NULL)
      .put("sessionPath", sessionPath)
      .put("pinnedFiles", JSONArray())

  private fun handleStreamingStatus(msg: JSONObject) {
    val isStreaming = msg.hanakoStreamingFlag() ?: return
    val wasStreaming = _state.value.streaming
    _state.update { it.copy(streaming = isStreaming) }
    if (wasStreaming && !isStreaming) {
      if (isInterruptedStatus(msg)) {
        finishInterruptedTurn()
      }
      resetActiveTurnState()
      onEvent(HanakoChatEvent.TurnEnded)
    }
  }

  private fun JSONObject.hanakoStreamingFlag(): Boolean? =
    optBooleanAnyOrNull("isStreaming", "streaming", "running", "active", "inProgress", "busy")
      ?: cleanString("status")?.let { status ->
        when (nbgNormalizeHanakoStatus(status)) {
          "running", "streaming", "thinking", "working", "busy", "active", "queued" -> true
          "idle", "done", "complete", "completed", "stopped", "finished", "success", "failed", "failure", "error" -> false
          else -> null
        }
      }

  private fun isInterruptedStatus(msg: JSONObject): Boolean {
    if (msg.optBooleanAnyOrNull("aborted", "interrupted", "cancelled", "canceled") == true) return true
    val stopStatus = msg.cleanStringAny("reason", "status", "stopReason", "stop_reason", "finishReason", "finish_reason")
      ?.let(::nbgNormalizeHanakoStatus)
      ?: return false
    return stopStatus in setOf(
      "interrupt",
      "interrupted",
      "abort",
      "aborted",
      "cancel",
      "cancelled",
      "canceled",
      "cancelled_by_user",
      "canceled_by_user",
      "user_abort",
      "user_aborted",
      "user_cancelled",
      "user_canceled",
      "stop",
      "stopped",
      "stopped_by_user",
    )
  }

  private fun finishInterruptedTurn() {
    resetActiveTurnState()
    onEvent(HanakoChatEvent.ToolInterrupted)
  }

  private fun handleContextUsage(msg: JSONObject) {
    val eventSessionPath = msg.cleanString("sessionPath") ?: msg.cleanString("path")
    val currentSessionPath = _state.value.sessionPath
    if (!eventSessionPath.isNullOrBlank() && currentSessionPath != eventSessionPath) {
      Log.i("NBG_HANAKO", "ignore stale context usage for $eventSessionPath; current=$currentSessionPath")
      return
    }
    val label = formatContextUsage(msg)
    _state.update {
      it.copy(
        contextUsageLabel = label,
        compressionAvailable = msg.optBoolean("compressionAvailable", false),
      )
    }
  }

  private fun handleCompactionStatus(running: Boolean, msg: JSONObject) {
    val status = nbgNormalizeHanakoStatus(msg.cleanString("status"))
    val failed = msg.optBoolean("failed", false) || status in setOf("failed", "failure", "error")
    val message = msg.cleanString("message") ?: msg.cleanString("error")
    _state.update {
      it.copy(
        compressing = running && !failed,
        compressionAvailable = if (running) false else it.compressionAvailable,
        lastError = if (failed) message ?: "上下文压缩失败" else it.lastError,
        runtimeStatus = it.runtimeStatus.copy(
          usageLabel = when {
            running -> "正在压缩上下文"
            failed -> "上下文压缩失败"
            else -> it.contextUsageLabel
          },
        ),
      )
    }
    if (failed) {
      onEvent(HanakoChatEvent.SystemMessage("上下文压缩失败：${message ?: "HanakoPro 返回失败"}"))
    }
  }

  private fun handleBrowserStatus(msg: JSONObject) {
    val running = msg.optBoolean("running", false)
    val url = msg.cleanString("url")
    val label = if (running) {
      listOfNotNull("浏览器运行中", url?.let { compactUrl(it) }).joinToString(" / ")
    } else {
      "浏览器已停止"
    }
    _state.update { it.copy(runtimeStatus = it.runtimeStatus.copy(browserLabel = label)) }
  }

  private fun handleTokenUsage(msg: JSONObject) {
    val usage = msg.optJSONObject("usage") ?: msg
    val total = usage.optLong("totalTokens", usage.optLong("total_tokens", -1L))
    val input = usage.optLong("inputTokens", usage.optLong("prompt_tokens", -1L))
    val output = usage.optLong("outputTokens", usage.optLong("completion_tokens", -1L))
    val label = when {
      total >= 0L -> "用量 ${formatTokenCount(total)}"
      input >= 0L || output >= 0L -> listOfNotNull(
        input.takeIf { it >= 0L }?.let { "in ${formatTokenCount(it)}" },
        output.takeIf { it >= 0L }?.let { "out ${formatTokenCount(it)}" },
      ).joinToString(" / ").takeIf { it.isNotBlank() }?.let { "用量 $it" }
      else -> null
    } ?: return
    _state.update { it.copy(runtimeStatus = it.runtimeStatus.copy(usageLabel = label)) }
  }

  private fun handlePlanMode(msg: JSONObject) {
    val type = msg.optString("type")
    val mode = msg.cleanString("permissionMode")?.let(::nbgNormalizePermissionMode)
      ?: msg.cleanString("mode")?.let(::nbgNormalizePermissionMode)
      ?: msg.cleanString("accessMode")?.let {
        if (it == NBG_PERMISSION_MODE_READ_ONLY) NBG_PERMISSION_MODE_READ_ONLY else NBG_PERMISSION_MODE_OPERATE
      }
    val enabled = when (type) {
      "plan_mode" -> msg.optBoolean("enabled", false)
      else -> mode == NBG_PERMISSION_MODE_READ_ONLY
    }
    val actualMode = mode ?: if (enabled) NBG_PERMISSION_MODE_READ_ONLY else NBG_DEFAULT_PERMISSION_MODE
    val label = hanakoPermissionModeLabel(actualMode)
    _state.update {
      it.copy(
        planModeEnabled = enabled,
        permissionMode = actualMode,
        permissionModeLabel = label,
        runtimeStatus = it.runtimeStatus.copy(permissionLabel = "权限：$label"),
      )
    }
  }

  private fun handleBridgeStatus(msg: JSONObject) {
    val label = summarizeBridgeStatus(msg) ?: return
    val status = nbgNormalizeHanakoStatus(msg.cleanString("status"))
    _state.update {
      it.copy(
        bridgeStatusLabel = label,
        lastError = if (status == "error") label else it.lastError,
      )
    }
    if (status == "error") {
      onEvent(HanakoChatEvent.ToolStatus(label))
    }
  }

  private fun latestAssistantTurnUserTimestamp(messages: List<HanakoHistoryMessage>): Long {
    val assistantIndex = messages.indexOfLast { it.role == "assistant" }
    if (assistantIndex <= 0) return 0L
    for (index in assistantIndex - 1 downTo 0) {
      val message = messages[index]
      if (message.role == "user") return message.timestampMs
    }
    return 0L
  }

  private fun beginAssistant(text: String): Long {
    val id = nextMessageId++
    assistantMessageId = id
    assistantHasText = false
    if (text.isBlank()) {
      onEvent(HanakoChatEvent.AssistantStarted(id))
    } else {
      setAssistantPending(id, text)
    }
    return id
  }

  private fun setAssistantText(messageId: Long, text: String) {
    assistantMessageId = messageId
    assistantHasText = text.isNotBlank()
    onEvent(HanakoChatEvent.AssistantText(messageId, text))
  }

  private fun setAssistantPending(messageId: Long, text: String) {
    assistantMessageId = messageId
    assistantHasText = false
    onEvent(HanakoChatEvent.AssistantText(messageId, text))
  }

  private fun sealAssistantTextSegment() {
    val id = assistantMessageId
    if (id != null && !assistantHasText) {
      onEvent(HanakoChatEvent.AssistantRemoved(id))
    }
    assistantMessageId = null
    assistantHasText = false
  }

  private fun appendAssistantDelta(delta: String) {
    val id = assistantMessageId ?: beginAssistant("")
    if (!assistantHasText) {
      assistantHasText = true
      onEvent(HanakoChatEvent.AssistantText(id, delta))
    } else {
      onEvent(HanakoChatEvent.AssistantDelta(id, delta))
    }
  }

  private fun beginThinking(): Long {
    thinkingMessageId?.let { return it }
    val id = nextMessageId++
    thinkingMessageId = id
    onEvent(HanakoChatEvent.ThinkingStarted(id))
    return id
  }

  private fun appendThinkingDelta(delta: String) {
    val id = thinkingMessageId ?: beginThinking()
    onEvent(HanakoChatEvent.ThinkingDelta(id, delta))
  }

  private fun endThinking() {
    val id = thinkingMessageId ?: return
    thinkingMessageId = null
    onEvent(HanakoChatEvent.ThinkingEnded(id))
  }

  private fun isCurrentSessionMessage(msg: JSONObject): Boolean {
    val eventSessionPath = msg.optString("sessionPath").trim()
    if (eventSessionPath.isBlank() || eventSessionPath.equals("null", ignoreCase = true)) {
      return _state.value.selectingSessionPath == null
    }
    return eventSessionPath == _state.value.sessionPath
  }

  private fun scheduleReconnect(reason: String) {
    if (reconnectJob?.isActive == true) return
    reconnectJob = scope.launch {
      delay(3_000)
      if (!_state.value.connected) {
        Log.i("NBG_HANAKO", "reconnect after $reason")
        connect()
      }
    }
  }

  private suspend fun restartStaleServer(info: HanakoServerInfo, reason: String): HanakoServerInfo {
    reconnectJob?.cancel()
    reconnectJob = null
    ++connectRequestSerial
    Log.w("NBG_HANAKO", "restart stale HanakoPro server on ${info.port}: $reason")
    serverInfo = null
    webSocket?.close(1001, reason)
    webSocket = null
    _state.update {
      it.copy(
        connected = false,
        connecting = false,
        prewarming = true,
        streaming = false,
        selectingSessionPath = null,
        lastError = reason,
        connectionLabel = "正在重启 HanakoPro",
      )
    }
    withContext(Dispatchers.IO) { clearStaleHanakoServer(appContext, info) }
    connectOnce(allowLaunch = true)
    return serverInfo ?: error("HanakoPro 重启失败")
  }

  private companion object {
    const val CONNECT_WAIT_TIMEOUT_MS = 95_000L
    const val CONNECT_WAIT_POLL_MS = 250L
    const val LAUNCH_HEALTH_CHECK_ATTEMPTS = 360
    const val LAUNCH_HEALTH_CHECK_INTERVAL_MS = 250L
    const val BACKGROUND_WARMUP_DELAY_MS = 120L
  }
}

private fun HanakoChatState.withSkillSnapshot(snapshot: HanakoSkillsSnapshot): HanakoChatState =
  copy(
    rawSkillsSnapshot = snapshot,
    skillsSnapshot = nbgApplySkillCuratorMetadata(snapshot, skillCuratorMetadata),
  )

private fun HanakoChatState.withSkillCuratorMetadata(metadata: NbgSkillCuratorMetadata): HanakoChatState =
  copy(
    skillCuratorMetadata = metadata,
    skillsSnapshot = nbgApplySkillCuratorMetadata(rawSkillsSnapshot, metadata),
  )

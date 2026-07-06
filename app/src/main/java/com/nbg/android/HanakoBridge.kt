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
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

data class HanakoServerInfo(
  val port: Int,
  val token: String,
  val pid: Int? = null,
  val version: String? = null,
) {
  val apiBase: String = "http://127.0.0.1:$port"
  val wsUrl: String = "ws://127.0.0.1:$port/ws?token=$token"
}

data class HanakoSessionSummary(
  val path: String,
  val title: String,
  val subtitle: String,
  val snippet: String? = null,
  val matchType: String? = null,
  val pinned: Boolean = false,
  val hasSummary: Boolean = false,
)

data class HanakoHistoryMessage(
  val id: Long,
  val role: String,
  val text: String,
  val timestampMs: Long = 0L,
  val contentBlock: HanakoContentBlock? = null,
  val toolStatus: HanakoToolStatus? = null,
)

data class HanakoTodoItem(
  val content: String,
  val activeForm: String,
  val status: String,
)

data class HanakoSessionFile(
  val id: String,
  val name: String,
  val path: String,
  val kind: String,
  val source: String,
  val status: String = "",
  val ext: String = "",
  val timestampMs: Long = 0L,
)

data class HanakoHistorySnapshot(
  val messages: List<HanakoHistoryMessage> = emptyList(),
  val todos: List<HanakoTodoItem> = emptyList(),
  val sessionFiles: List<HanakoSessionFile> = emptyList(),
)

data class HanakoCachedSession(
  val sessionPath: String,
  val title: String = "新聊天",
  val snapshot: HanakoHistorySnapshot = HanakoHistorySnapshot(),
)

internal data class HanakoLocalContentParts(
  val text: String = "",
  val thinking: String = "",
  val toolNames: List<String> = emptyList(),
)

internal data class HanakoLocalToolCall(
  val id: String,
  val name: String,
  val args: JSONObject? = null,
)

data class HanakoConfirmation(
  val confirmId: String,
  val title: String,
  val body: String,
  val subjectLabel: String,
  val subjectDetail: String,
  val severity: String,
  val riskTier: String = NbgPermissionRiskTier.Medium.wireName,
  val riskLabel: String = NbgPermissionRiskTier.Medium.label,
  val targetLabel: String = "",
  val recoveryHint: String = "",
  val confirmLabel: String,
  val rejectLabel: String,
  val status: String = "pending",
)

data class HanakoContentBlock(
  val type: String,
  val title: String,
  val subtitle: String = "",
  val detail: String = "",
  val status: String = "",
  val taskId: String = "",
)

data class HanakoFileDiff(
  val fileName: String,
  val filePath: String,
  val oldContent: String = "",
  val newContent: String = "",
  val unifiedDiff: String = "",
)

data class HanakoTerminalOutput(
  val sessionId: String,
  val title: String = "",
  val cwd: String = "",
  val output: String = "",
  val staticOutput: Boolean = false,
  val alive: Boolean? = null,
  val exitCode: Int? = null,
  val truncated: Boolean = false,
  val outputPriority: Int = 0,
  val sliceFrom: Int? = null,
  val sliceTo: Int? = null,
)

data class HanakoFilePreview(
  val fileName: String,
  val filePath: String,
  val previewText: String = "",
  val truncated: Boolean = false,
  val append: Boolean = false,
  val reset: Boolean = false,
)

data class HanakoToolStatus(
  val key: String = "",
  val kind: String = "tool",
  val toolName: String = "",
  val filePath: String = "",
  val title: String,
  val subtitle: String = "",
  val detail: String = "",
  val status: String = "",
  val running: Boolean = false,
  val success: Boolean? = null,
  val filePreview: HanakoFilePreview? = null,
  val fileDiff: HanakoFileDiff? = null,
  val terminalOutput: HanakoTerminalOutput? = null,
  val taskCompletionEvidence: NbgTaskCompletionEvidenceBundle? = null,
) {
  val hasInlinePreview: Boolean
    get() = filePreview != null || fileDiff != null || terminalOutput != null
}

data class HanakoTeamAgentStatus(
  val taskId: String,
  val agentId: String,
  val role: String,
  val title: String,
  val status: String = "idle",
  val summary: String = "",
  val artifactRefs: List<String> = emptyList(),
  val updatedAt: Long = 0L,
) {
  val running: Boolean
    get() = nbgNormalizeTeamStatus(status) in HANAKO_TEAM_RUNNING_STATUSES
}

data class HanakoTeamTaskStatus(
  val taskId: String,
  val title: String = "代码团队任务",
  val mode: String = "auto",
  val status: String = "idle",
  val summary: String = "",
  val agents: List<HanakoTeamAgentStatus> = emptyList(),
) {
  val activeCount: Int
    get() = agents.count { it.running }

  val isActive: Boolean
    get() = nbgNormalizeTeamStatus(status) in setOf("queued", "running", "thinking", "working")
}

internal val HANAKO_TEAM_RUNNING_STATUSES = setOf(
  "queued",
  "running",
  "thinking",
  "working",
  "coding",
  "reviewing",
  "testing",
  "terminal",
)

internal const val HANA_TEAM_SINGLE_AGENT_FALLBACK_MODE = "single_agent_fallback"
internal const val HANA_TEAM_SINGLE_AGENT_SESSION_MODE = "single_agent_session"
internal const val HANA_TEAM_MULTI_AGENT_SESSION_MODE = "multi_agent_session"
internal const val HANA_TEAM_BACKEND_AGENT_MODE = "backend_agent_tools"
internal const val HANA_TEAM_EXISTING_SESSION_SUMMARY = "HanakoPro Android 团队任务已接入现有执行会话。"
internal const val HANA_PENDING_NEW_SESSION_PATH = "__nbg_pending_new_session__"
internal const val NBG_ANDROID_LANGUAGE_LOCALE = "zh-CN"
internal val NBG_JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal fun nbgTranslationEndpointLabel(endpoint: String): String =
  runCatching { URL(endpoint).host.takeIf { it.isNotBlank() } ?: endpoint.take(120) }
    .getOrDefault(endpoint.take(120))

internal fun nbgStripLegacyAndroidPromptWrapper(text: String): String {
  val trimmed = text.trimStart()
  val messageMarker = "\n\n用户消息：\n"
  return if (messageMarker in trimmed && trimmed.substringBefore(messageMarker).length <= 260) {
    trimmed.substringAfter(messageMarker).trimStart()
  } else {
    text
  }
}

internal fun nbgThinkingTextForAndroidDisplay(text: String, allowTranslatingPlaceholder: Boolean = false): String {
  return nbgFormatThinkingTextForDisplay(nbgCleanThinkingVisibleText(nbgStripLegacyAndroidPromptWrapper(text)))
}

internal fun nbgCleanThinkingVisibleText(text: String): String {
  val stripped = stripLocalThinkTags(text)
  val visibleText = listOf(stripped.second, stripped.first)
    .filter { it.isNotBlank() }
    .joinToString("\n")
  return NBG_INTERNAL_PARTIAL_TAG_SUFFIX_REGEX
    .replace(NBG_INTERNAL_TAG_ONLY_REGEX.replace(visibleText, ""), "")
}

internal fun nbgFormatThinkingTextForDisplay(text: String): String {
  val normalized = text
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace('\u00a0', ' ')
    .replace(Regex("[\\t ]+"), " ")
    .replace(Regex(" *\\n *"), "\n")
    .trim()
  if (normalized.isBlank()) return normalized
  return normalized
    .lines()
    .joinToString("\n") { line -> nbgRepairCompressedThinkingLine(line) }
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()
}

private fun nbgRepairCompressedThinkingLine(line: String): String {
  val punctuationSpaced = line.replace(Regex("([,:;])(?=\\S)"), "$1 ")
  if (punctuationSpaced.length < 24 || punctuationSpaced.any { it.isWhitespace() }) return punctuationSpaced
  val fixed = punctuationSpaced
    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
    .replace(Regex("([a-zA-Z])([0-9])"), "$1 $2")
    .replace(Regex("([0-9])([a-zA-Z])"), "$1 $2")
    .replace(Regex("([.!?。！？])([A-Za-z\\u4e00-\\u9fff])"), "$1 $2")
  return fixed.ifBlank { line }
}

internal fun nbgDisplayTextForRole(role: String, text: String): String =
  when (role) {
    "user" -> nbgStripLegacyAndroidPromptWrapper(text)
    "thinking" -> nbgThinkingTextForAndroidDisplay(text)
    "assistant" -> nbgCleanAssistantVisibleText(text)
    else -> text
  }

internal fun nbgCleanAssistantVisibleText(text: String): String {
  val stripped = nbgStripInternalAssistantBlocks(text)
  val cleaned = stripped.text
    .replace(Regex("\\n{3,}"), "\n\n")
  return if (stripped.removed) cleaned.trim() else cleaned
}

internal fun nbgCleanAssistantVisibleStreamingText(text: String): String {
  val stripped = nbgStripInternalAssistantBlocks(text)
  val cleaned = NBG_INTERNAL_PARTIAL_TAG_SUFFIX_REGEX
    .replace(stripped.text, "")
    .replace(Regex("\\n{3,}"), "\n\n")
  return if (stripped.removed || cleaned.length != stripped.text.length) {
    cleaned.trimStart('\n', ' ', '\t')
  } else {
    cleaned
  }
}

internal data class NbgInternalBlockStripResult(
  val text: String,
  val removed: Boolean,
)

internal fun nbgStripInternalAssistantBlocks(text: String): NbgInternalBlockStripResult {
  var removed = false
  fun strip(regex: Regex, value: String): String =
    regex.replace(value) {
      removed = true
      ""
    }

  val cleaned = text
    .let { strip(NBG_INTERNAL_NORMAL_BLOCK_REGEX, it) }
    .let { strip(NBG_INTERNAL_MALFORMED_THINKING_BLOCK_REGEX, it) }
    .let { strip(NBG_INTERNAL_TAG_ONLY_REGEX, it) }
  return NbgInternalBlockStripResult(cleaned, removed)
}

internal fun nbgParseOpenAiChatText(raw: String): String =
  runCatching {
    val root = JSONObject(raw)
    val choice = root.optJSONArray("choices")?.optJSONObject(0)
    val message = choice?.optJSONObject("message")
    message?.rawStringAny("content", "text")
      ?: choice?.rawStringAny("text", "content")
      ?: root.rawStringAny("text", "content")
  }.getOrDefault("").trim()

internal fun nbgResolveHanakoModelsJsonUrlApi(
  raw: String,
  wantedProvider: String,
  wantedModelIds: Set<String>,
): Pair<NbgStoredApi, NbgApiModel>? =
  runCatching {
    val providers = JSONObject(raw.ifBlank { "{}" }).optJSONObject("providers") ?: return@runCatching null
    val normalizedProvider = wantedProvider.trim()
    val normalizedModels = wantedModelIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    if (normalizedProvider.isBlank() && normalizedModels.isEmpty()) return@runCatching null
    val keys = providers.keys().asSequence().toList()
    val providerId = keys.firstOrNull { id ->
      val provider = providers.optJSONObject(id) ?: return@firstOrNull false
      val providerMatches = normalizedProvider.isBlank() || id.equals(normalizedProvider, ignoreCase = true)
      providerMatches && nbgProviderContainsAnyModel(provider, normalizedModels)
    } ?: keys.firstOrNull { id ->
      nbgProviderContainsAnyModel(providers.optJSONObject(id), normalizedModels)
    } ?: return@runCatching null
    val provider = providers.optJSONObject(providerId) ?: return@runCatching null
    val baseUrl = provider.cleanStringAny("baseUrl", "base_url").orEmpty()
    val apiKey = provider.cleanStringAny("apiKey", "api_key").orEmpty()
    if (baseUrl.isBlank() || apiKey.isBlank()) return@runCatching null
    val model = nbgFirstMatchingProviderModel(provider, normalizedModels) ?: return@runCatching null
    NbgStoredApi(
      id = providerId,
      name = providerId,
      baseUrl = baseUrl,
      apiKey = apiKey,
      models = listOf(model),
      verifiedModelIds = setOf(model.id),
      selectedModelId = model.id,
    ) to model
  }.getOrNull()

internal fun nbgProviderContainsAnyModel(provider: JSONObject?, modelIds: Set<String>): Boolean =
  nbgFirstMatchingProviderModel(provider, modelIds) != null

internal fun nbgFirstMatchingProviderModel(provider: JSONObject?, modelIds: Set<String>): NbgApiModel? {
  if (provider == null || modelIds.isEmpty()) return null
  val models = provider.optJSONArray("models") ?: return null
  for (index in 0 until models.length()) {
    val item = models.optJSONObject(index)
    val id = item?.cleanStringAny("id", "model", "name").orEmpty()
      .ifBlank { models.optString(index).trim() }
    if (id.isBlank()) continue
    val label = item?.cleanStringAny("name", "label").orEmpty().ifBlank { id }
    if (modelIds.any { it.equals(id, ignoreCase = true) || it.equals(label, ignoreCase = true) }) {
      return NbgApiModel(id = id, label = label, contextWindow = item.nbgModelContextWindow(id))
    }
  }
  return null
}

internal fun nbgNormalizeTeamStatus(status: String?): String {
  val normalized = status
    ?.trim()
    ?.lowercase()
    ?.replace('-', '_')
    ?.replace(Regex("\\s+"), "_")
    .orEmpty()
  return when (normalized) {
    "pending" -> "queued"
    "in_progress", "inprogress", "active", "executing" -> "working"
    "complete", "succeeded" -> "completed"
    "failure" -> "failed"
    "stopped", "cancelled" -> "aborted"
    else -> normalized
  }
}

internal val HANAKO_DEFAULT_TEAM_AGENTS = listOf(
  "Supervisor" to "总控 Agent",
  "Researcher" to "调研员",
  "Coder" to "工程师",
  "Reviewer" to "审查员",
  "Tester" to "测试员",
  "Terminal" to "终端执行员",
  "File Manager" to "文件管理员",
)

data class HanakoRuntimeStatus(
  val browserLabel: String? = null,
  val usageLabel: String? = null,
  val permissionLabel: String? = null,
  val thinkingLabel: String? = null,
) {
  val isEmpty: Boolean
    get() = browserLabel == null &&
      usageLabel == null
}

data class HanakoStreamResumeDiagnostics(
  val requestCount: Int = 0,
  val resumeCount: Int = 0,
  val replayedEventCount: Int = 0,
  val acceptedReplayEventCount: Int = 0,
  val skippedReplayEventCount: Int = 0,
  val duplicateReplayEventCount: Int = 0,
  val truncatedResumeCount: Int = 0,
  val resetResumeCount: Int = 0,
  val cursorPresent: Boolean = false,
  val lastSeq: Int = 0,
)

data class HanakoAgentSummary(
  val id: String,
  val name: String,
  val identity: String = "",
  val modelLabel: String = "",
  val isCurrent: Boolean = false,
  val isPrimary: Boolean = false,
)

data class HanakoModelSummary(
  val id: String,
  val name: String,
  val provider: String,
  val input: List<String> = emptyList(),
  val thinkingLevels: List<String> = emptyList(),
  val thinkingSource: String = "",
  val contextWindow: Long = 0L,
  val isCurrent: Boolean = false,
  val providerLabel: String = "",
) {
  val label: String
    get() = name.ifBlank { id }
}

data class HanakoModelHealth(
  val modelId: String,
  val provider: String,
  val ok: Boolean,
  val status: Int = 0,
  val skipped: String = "",
  val error: String = "",
  val code: String = "",
  val reason: String = "",
) {
  val key: String
    get() = "$provider/$modelId"
}

data class HanakoAgentModelConfig(
  val agents: List<HanakoAgentSummary> = emptyList(),
  val models: List<HanakoModelSummary> = emptyList(),
)

data class HanakoProviderSummary(
  val id: String,
  val displayName: String,
  val type: String = "",
  val authType: String = "",
  val api: String = "",
  val configStatus: String = "",
  val hasCredentials: Boolean = false,
  val loggedIn: Boolean? = null,
  val supportsOauth: Boolean = false,
  val isCodingPlan: Boolean = false,
  val canDelete: Boolean = false,
  val modelCount: Int = 0,
  val customModelCount: Int = 0,
  val missingFields: List<String> = emptyList(),
  val configError: String = "",
)

data class HanakoProviderSnapshot(
  val providers: List<HanakoProviderSummary> = emptyList(),
  val activeProvider: String = "",
  val activeModel: String = "",
  val currentModel: String = "",
  val modelCount: Int = 0,
)

data class HanakoPermissionModeState(
  val mode: String = NBG_DEFAULT_PERMISSION_MODE,
  val accessMode: String = NBG_DEFAULT_PERMISSION_MODE,
  val defaultMode: String = NBG_DEFAULT_PERMISSION_MODE,
) {
  val label: String
    get() = hanakoPermissionModeLabel(mode)
}

data class HanakoSessionFocusState(
  val path: String,
  val agentName: String? = null,
  val modelName: String? = null,
  val permissionMode: String = NBG_DEFAULT_PERMISSION_MODE,
  val thinkingLevel: String = "auto",
) {
  val permissionLabel: String
    get() = hanakoPermissionModeLabel(permissionMode)
  val thinkingLabel: String
    get() = hanakoThinkingLevelLabel(thinkingLevel)
}

data class HanakoThinkingLevelState(
  val thinkingLevel: String = "auto",
) {
  val label: String
    get() = hanakoThinkingLevelLabel(thinkingLevel)
}

data class HanakoSlashCommand(
  val name: String,
  val aliases: List<String> = emptyList(),
  val description: String = "",
  val permission: String = "",
  val scope: String = "session",
  val source: String = "core",
) {
  val slash: String
    get() = "/$name"
}

internal fun JSONObject.cleanString(name: String): String? {
  if (isNull(name)) return null
  val value = optString(name).trim()
  return value.takeUnless {
    it.isBlank() || it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true)
  }
}

internal fun JSONObject.cleanStringAny(vararg names: String): String? {
  names.forEach { name ->
    cleanString(name)?.let { return it }
  }
  return null
}

internal fun JSONObject.rawStringAny(vararg names: String): String {
  names.forEach { name ->
    if (has(name) && !isNull(name)) return optString(name)
  }
  return ""
}

internal fun JSONObject.rawStringOrNull(name: String): String? {
  if (!has(name) || isNull(name)) return null
  val value = optString(name)
  return value.takeUnless {
    it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true)
  }
}

internal fun JSONObject.rawStringAnyOrNull(vararg names: String): String? {
  names.forEach { name ->
    rawStringOrNull(name)?.let { return it }
  }
  return null
}

internal fun JSONObject.objectAny(vararg names: String): JSONObject? {
  names.forEach { name ->
    optJSONObject(name)?.let { return it }
  }
  return null
}

internal fun JSONObject.nestedObjectAny(vararg paths: String): JSONObject? {
  paths.forEach { path ->
    val found = path.split('.').fold(this as JSONObject?) { current, segment ->
      current?.optJSONObject(segment)
    }
    if (found != null) return found
  }
  return null
}

internal fun JSONArray?.toCleanStringList(limit: Int): List<String> {
  if (this == null) return emptyList()
  return buildList {
    for (index in 0 until length()) {
      if (size >= limit) break
      val value = optString(index).trim()
      if (value.isNotBlank() && value != "null" && value != "undefined") add(value)
    }
  }
}

internal fun String.canonicalBackendAgentToolName(): String? =
  when (
    trim()
      .replace('-', '_')
      .lowercase()
  ) {
    "agent" -> "Agent"
    "wolfpack", "wolf_pack" -> "WolfPack"
    else -> null
  }

internal fun nbgBackendAgentToolNameFromEvent(root: JSONObject): String? {
  val payload = root.toolEventPayload()
  return (root.cleanStringAny("name", "toolName", "tool")
    ?: payload.cleanStringAny("name", "toolName", "tool"))
    ?.canonicalBackendAgentToolName()
}

private fun MutableList<String>.addCleanBackendTaskId(value: String?) {
  val clean = value?.trim().orEmpty()
  if (clean.isNotBlank() && clean != "null" && clean != "undefined") add(clean)
}

internal fun nbgBackendAgentToolSubagentTaskIds(root: JSONObject): List<String> {
  val ids = mutableListOf<String>()

  fun collect(source: JSONObject?) {
    if (source == null) return
    ids.addCleanBackendTaskId(source.cleanStringAny("taskId", "task_id", "subagentTaskId", "subagent_task_id"))
    source.optJSONArray("taskIds")?.let { array ->
      for (index in 0 until array.length()) {
        ids.addCleanBackendTaskId(array.optString(index))
      }
    }
    source.optJSONArray("subagentTaskIds")?.let { array ->
      for (index in 0 until array.length()) {
        ids.addCleanBackendTaskId(array.optString(index))
      }
    }
    source.optJSONArray("results")?.let { array ->
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index)
        ids.addCleanBackendTaskId(item?.cleanStringAny("taskId", "task_id"))
      }
    }
  }

  val payload = root.toolEventPayload()
  listOf(
    root,
    root.optJSONObject("details"),
    root.optJSONObject("result"),
    root.optJSONObject("data"),
    payload,
    payload.optJSONObject("details"),
    payload.optJSONObject("result"),
    payload.optJSONObject("data"),
  ).forEach(::collect)

  return ids.distinct().take(40)
}

internal fun nbgBackendAgentToolStreamStatus(root: JSONObject): String? {
  val payload = root.toolEventPayload()
  return listOf(
    root,
    root.optJSONObject("details"),
    root.optJSONObject("result"),
    root.optJSONObject("data"),
    payload,
    payload.optJSONObject("details"),
    payload.optJSONObject("result"),
    payload.optJSONObject("data"),
  ).firstNotNullOfOrNull { source ->
    source?.cleanStringAny("streamStatus", "stream_status", "status", "state")
  }
}

internal fun nbgBackendAgentToolTaskStatus(
  streamStatus: String?,
  success: Boolean?,
  hasSubagentTasks: Boolean,
): String {
  val normalized = nbgNormalizeTeamStatus(streamStatus)
  return when (normalized) {
    "completed", "done", "success", "succeeded" -> "completed"
    "failed", "failure", "error" -> "failed"
    "aborted", "cancelled", "canceled" -> "aborted"
    "queued", "running", "thinking", "working", "coding", "reviewing", "testing", "terminal" -> "running"
    else -> when {
      success == false -> "failed"
      hasSubagentTasks -> "running"
      success == true -> "completed"
      else -> "running"
    }
  }
}

internal fun JSONObject.optIntOrNull(name: String): Int? =
  if (has(name) && !isNull(name)) optInt(name) else null

internal fun JSONObject.optBooleanOrNull(name: String): Boolean? {
  if (!has(name) || isNull(name)) return null
  val value = opt(name) ?: return null
  if (value is Boolean) return value
  return when (value.toString().trim().lowercase()) {
    "true", "1", "yes", "y", "on", "running", "active" -> true
    "false", "0", "no", "n", "off", "idle", "done", "complete", "completed", "stopped", "finished" -> false
    else -> null
  }
}

internal fun JSONObject.optBooleanAnyOrNull(vararg names: String): Boolean? {
  names.forEach { name ->
    optBooleanOrNull(name)?.let { return it }
  }
  return null
}

internal fun parseHanakoTimestampMs(value: String?): Long {
  val normalized = value?.trim().orEmpty()
  if (normalized.isBlank()) return 0L
  normalized.toLongOrNull()?.let { return it }
  return runCatching { Instant.parse(normalized).toEpochMilli() }.getOrDefault(0L)
}

internal fun nbgNormalizeTerminalOutput(raw: String): String =
  raw
    .replace(Regex("\u001B\\[(?:\\d+;)?1H"), "\n")
    .replace(Regex("\u001B\\[[0-9;?]*[A-Za-z]|\u001B\\][^\u0007]*\u0007|\u001B[=>]"), "")
    .replace("\r\n", "\n")
    .replace('\r', '\n')

internal fun String.sha256Hex(): String {
  val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
  return bytes.joinToString("") { "%02x".format(it) }
}

internal fun nbgLocalFileForSessionPath(
  ubuntuRootHomeDir: File,
  sessionPath: String,
  localSessionDirs: List<File>,
): File? {
  val normalized = sessionPath.trim()
  val name = normalized.substringAfterLast('/').takeIf { it.endsWith(".jsonl") } ?: return null
  if (normalized.startsWith("/root/")) {
    val relative = normalized.removePrefix("/root/").trim('/')
    val direct = File(ubuntuRootHomeDir, relative)
    val root = ubuntuRootHomeDir.canonicalFile
    val canonical = runCatching { direct.canonicalFile }.getOrNull()
    if (canonical != null && nbgFileIsInsideOrSame(canonical, root) && canonical.isFile) return canonical
  }
  return localSessionDirs
    .map { File(it, name) }
    .firstOrNull { it.isFile }
}

internal fun nbgFileIsInsideOrSame(file: File, base: File): Boolean {
  val canonicalBase = base.canonicalFile
  var current: File? = file.canonicalFile
  while (current != null) {
    if (current == canonicalBase) return true
    current = current.parentFile
  }
  return false
}

internal fun nbgDeletePathWithoutFollowingSymlinkForTest(path: File): Boolean =
  nbgDeletePathWithoutFollowingSymlink(path)

internal fun nbgDeletePathWithoutFollowingSymlink(path: File): Boolean {
  val nioPath = path.toPath()
  return when {
    Files.isSymbolicLink(nioPath) -> Files.deleteIfExists(nioPath)
    Files.isDirectory(nioPath, LinkOption.NOFOLLOW_LINKS) -> path.deleteRecursively()
    else -> path.delete()
  }
}

internal fun parseHanakoTodos(array: JSONArray?): List<HanakoTodoItem> {
  if (array == null) return emptyList()
  val todos = mutableListOf<HanakoTodoItem>()
  for (index in 0 until array.length()) {
    val item = array.optJSONObject(index) ?: continue
    val content = item.cleanString("content")
      ?: item.cleanString("text")
      ?: item.cleanString("title")
      ?: continue
    val activeForm = item.cleanString("activeForm") ?: content
    val status = when {
      item.has("done") -> if (item.optBoolean("done", false)) "completed" else "pending"
      else -> nbgNormalizeTodoStatus(item.cleanString("status"))
    }
    todos += HanakoTodoItem(content = content, activeForm = activeForm, status = status)
  }
  return if (todos.isNotEmpty() && todos.all { nbgTodoStatusCompleted(it.status) }) emptyList() else todos
}

internal fun nbgParseHanakoTodosForTest(array: JSONArray?): List<HanakoTodoItem> =
  parseHanakoTodos(array)

internal fun nbgNormalizeTodoStatus(status: String?): String {
  return when (
    status
      ?.trim()
      ?.lowercase()
      ?.replace('-', '_')
      ?.replace(Regex("\\s+"), "_")
      .orEmpty()
  ) {
    "complete", "completed", "done", "success", "succeeded" -> "completed"
    "active", "running", "working", "inprogress", "in_progress" -> "in_progress"
    else -> "pending"
  }
}

internal fun nbgTodoStatusCompleted(status: String): Boolean =
  nbgNormalizeTodoStatus(status) == "completed"

internal fun nbgNormalizeHanakoStatus(status: String?): String =
  status
    ?.trim()
    ?.lowercase()
    ?.replace('-', '_')
    ?.replace(Regex("\\s+"), "_")
    .orEmpty()

internal fun nbgBridgeStatusLabel(status: String): String? =
  when (nbgNormalizeHanakoStatus(status)) {
    "connected" -> "已连接"
    "disconnected" -> "已断开"
    "error" -> "错误"
    else -> status.takeIf { it.isNotBlank() }
  }

internal fun nbgDeferredStatusLabel(status: String): String? =
  when (nbgNormalizeHanakoStatus(status)) {
    "success" -> "完成"
    "failed", "failure", "error" -> "失败"
    "aborted", "cancelled", "canceled" -> "已取消"
    else -> status.takeIf { it.isNotBlank() }
  }

internal fun parseHanakoSessionFiles(array: JSONArray?): List<HanakoSessionFile> {
  if (array == null) return emptyList()
  val files = mutableListOf<HanakoSessionFile>()
  for (index in 0 until array.length()) {
    val item = array.optJSONObject(index) ?: continue
    val id = item.cleanString("id")
      ?: item.cleanString("fileId")
      ?: item.cleanString("path")
      ?: continue
    val path = item.cleanString("path") ?: item.cleanString("filePath").orEmpty()
    val name = item.cleanString("name")
      ?: item.cleanString("label")
      ?: path.substringAfterLast('/').takeIf { it.isNotBlank() }
      ?: "文件"
    files += HanakoSessionFile(
      id = id,
      name = name,
      path = path,
      kind = item.cleanString("kind") ?: item.cleanString("mime") ?: "other",
      source = item.cleanString("source").orEmpty(),
      status = item.cleanString("status").orEmpty(),
      ext = item.cleanString("ext").orEmpty(),
      timestampMs = item.optLong("timestamp", item.optLong("createdAt", 0L)).coerceAtLeast(0L),
    )
  }
  return files.distinctBy { it.id }
}

internal fun nbgParseHanakoHttpError(code: Int, text: String, path: String = ""): String {
  val message = runCatching {
    val root = JSONObject(text)
    val nested = root.optJSONObject("error")
    nested?.cleanStringAny("message", "detail", "code")
      ?: root.cleanStringAny("error", "message", "detail")
  }.getOrNull()
  val fallback = text.trim().ifBlank { "空响应" }
  val compactMessage = (message ?: fallback).replace(Regex("\\s+"), " ").take(240)
  val compactPath = path.trim().takeIf { it.startsWith("/") }?.take(160)
  return listOfNotNull("HTTP $code", compactPath, compactMessage).joinToString(" ")
}

internal fun HanakoHistorySnapshot.toJson(): JSONObject =
  JSONObject()
    .put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
    .put("todos", JSONArray().apply { todos.forEach { put(it.toJson()) } })
    .put("sessionFiles", JSONArray().apply { sessionFiles.forEach { put(it.toJson()) } })

internal fun HanakoHistoryMessage.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("role", role)
    .put("text", text)
    .put("timestampMs", timestampMs)
    .apply {
      contentBlock?.let { put("contentBlock", it.toJson()) }
      toolStatus?.let { put("toolStatus", it.toJson()) }
    }

internal fun HanakoTodoItem.toJson(): JSONObject =
  JSONObject()
    .put("content", content)
    .put("activeForm", activeForm)
    .put("status", status)

internal fun HanakoSessionFile.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("name", name)
    .put("path", path)
    .put("kind", kind)
    .put("source", source)
    .put("status", status)
    .put("ext", ext)
    .put("timestampMs", timestampMs)

internal fun HanakoContentBlock.toJson(): JSONObject =
  JSONObject()
    .put("type", type)
    .put("title", title)
    .put("subtitle", subtitle)
    .put("detail", detail)
    .put("status", status)
    .put("taskId", taskId)

internal fun HanakoToolStatus.toJson(): JSONObject =
  JSONObject()
    .put("key", key)
    .put("kind", kind)
    .put("toolName", toolName)
    .put("filePath", filePath)
    .put("title", title)
    .put("subtitle", subtitle)
    .put("detail", detail)
    .put("status", status)
    .put("running", running)
    .apply {
      success?.let { put("success", it) }
      filePreview?.let { put("filePreview", it.toJson()) }
      fileDiff?.let { put("fileDiff", it.toJson()) }
      terminalOutput?.let { put("terminalOutput", it.toJson()) }
      (taskCompletionEvidence ?: nbgInferTaskCompletionEvidence(this@toJson))?.let {
        put("taskCompletionEvidence", it.toJson())
      }
    }

internal fun HanakoFilePreview.toJson(): JSONObject =
  JSONObject()
    .put("fileName", fileName)
    .put("filePath", filePath)
    .put("previewText", previewText)
    .put("truncated", truncated)
    .put("append", append)
    .put("reset", reset)

internal fun HanakoFileDiff.toJson(): JSONObject =
  JSONObject()
    .put("fileName", fileName)
    .put("filePath", filePath)
    .put("oldContent", nbgTrimFileDiffText(oldContent))
    .put("newContent", nbgTrimFileDiffText(newContent))
    .put("unifiedDiff", nbgTrimFileDiffText(unifiedDiff))

internal fun HanakoTerminalOutput.toJson(): JSONObject =
  JSONObject()
    .put("sessionId", sessionId)
    .put("title", title)
    .put("cwd", cwd)
    .put("output", output)
    .put("staticOutput", staticOutput)
    .put("truncated", truncated)
    .put("outputPriority", outputPriority)
    .apply {
      alive?.let { put("alive", it) }
      exitCode?.let { put("exitCode", it) }
      sliceFrom?.let { put("sliceFrom", it) }
      sliceTo?.let { put("sliceTo", it) }
    }

internal fun parseCachedHistorySnapshot(root: JSONObject): HanakoHistorySnapshot =
  HanakoHistorySnapshot(
    messages = parseCachedHistoryMessages(root.optJSONArray("messages")),
    todos = parseHanakoTodos(root.optJSONArray("todos")),
    sessionFiles = parseHanakoSessionFiles(root.optJSONArray("sessionFiles")),
  )

internal fun parseCachedHistoryMessages(array: JSONArray?): List<HanakoHistoryMessage> {
  if (array == null) return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      add(
        HanakoHistoryMessage(
          id = item.optString("id").toLongOrNull() ?: item.optLong("id", index.toLong()),
          role = item.cleanString("role") ?: "assistant",
          text = nbgDisplayTextForRole(
            item.cleanString("role") ?: "assistant",
            item.rawStringOrNull("text").orEmpty(),
          ),
          timestampMs = item.optLong("timestampMs", 0L),
          contentBlock = parseCachedContentBlock(item.optJSONObject("contentBlock")),
          toolStatus = parseCachedToolStatus(item.optJSONObject("toolStatus")),
        ),
      )
    }
  }
}

internal fun nbgParseHanakoRemoteMessages(messages: JSONArray?): List<HanakoHistoryMessage> {
  if (messages == null) return emptyList()
  val history = mutableListOf<HanakoHistoryMessage>()
  for (index in 0 until messages.length()) {
    val item = messages.optJSONObject(index) ?: continue
    val role = item.optString("role")
    if (role == "tool" || role == "toolResult") {
      parseHanakoRemoteToolMessage(item, index.toLong())?.let { history += it }
      continue
    }
    if (role != "user" && role != "assistant") continue
    val contentParts = parseLocalContentParts(item.opt("content"), stripThink = role == "assistant")
    val content = contentParts.text
    val id = item.optString("id").toLongOrNull() ?: index.toLong()
    val timestampMs = parseHanakoTimestampMs(item.cleanString("timestamp"))
    val thinking = listOfNotNull(
      item.rawStringOrNull("thinking")?.takeIf { it.isNotBlank() },
      contentParts.thinking.takeIf { it.isNotBlank() },
    )
      .joinToString("\n")
      .takeIf { it.isNotBlank() }
    val toolCalls = item.optJSONArray("toolCalls")
    if (role == "assistant") {
      if (thinking.isNullOrBlank() && content.isBlank() && (toolCalls == null || toolCalls.length() == 0) && contentParts.toolNames.isEmpty()) continue
      thinking?.takeIf { it.isNotBlank() }?.let {
        history += HanakoHistoryMessage(
          id = id * 100L + 1L,
          role = "thinking",
          text = nbgThinkingTextForAndroidDisplay(it),
          timestampMs = timestampMs,
        )
      }
      toolCalls?.let { tools ->
        parseHanakoToolArray(tools).forEachIndexed { toolIndex, tool ->
          history += HanakoHistoryMessage(
            id = id * 100L + 10L + toolIndex.toLong(),
            role = "tool",
            text = tool.title,
            timestampMs = timestampMs,
            toolStatus = tool,
          )
        }
      }
      if (toolCalls == null || toolCalls.length() == 0) {
        contentParts.toolNames.forEachIndexed { toolIndex, name ->
          history += HanakoHistoryMessage(
            id = id * 100L + 30L + toolIndex.toLong(),
            role = "tool",
            text = name,
            timestampMs = timestampMs,
            toolStatus = HanakoToolStatus(
              key = "remote:$id:$toolIndex:$name",
              kind = "tool",
              toolName = name,
              title = toolDisplayName(name),
              running = false,
            ),
          )
        }
      }
      val displayContent = nbgDisplayTextForRole(role, content)
      if (displayContent.isNotBlank()) {
        history += HanakoHistoryMessage(id = id, role = role, text = displayContent, timestampMs = timestampMs)
      }
    } else if (content.isNotBlank()) {
      history += HanakoHistoryMessage(id = id, role = role, text = nbgDisplayTextForRole(role, content), timestampMs = timestampMs)
    }
  }
  return history
}

internal fun parseHanakoRemoteToolMessage(item: JSONObject, fallbackId: Long): HanakoHistoryMessage? {
  val id = item.optString("id").toLongOrNull() ?: fallbackId
  val timestampMs = parseHanakoTimestampMs(item.cleanString("timestamp"))
  val name = item.cleanString("toolName")
    ?: item.cleanString("name")
    ?: item.cleanString("tool")
    ?: item.cleanString("type")
    ?: "tool"
  val args = item.optJSONObject("args")
    ?: item.optJSONObject("input")
    ?: item.optJSONObject("arguments")
  val localText = localContentText(item.opt("content"))
  val contentText = localText.ifEmpty {
    item.rawStringAnyOrNull("output", "result", "text", "summary").orEmpty()
  }
  val rawDetails = item.optJSONObject("details")
    ?: item.optJSONObject("result")
    ?: item.optJSONObject("output")
  val details = detailsWithFallbackOutput(rawDetails, contentText, allowTextFallback = name.isTerminalLikeTool())
  val synthetic = JSONObject()
    .put("name", name)
    .put("success", !item.optBoolean("isError", false))
  if (details.length() > 0) synthetic.put("details", details)
  args?.let { synthetic.put("args", it) }
  val fileDiff = parseFileDiff(name, synthetic)
  val filePreview = parseFilePreview(details) ?: parseLocalFilePreview(name, args, contentText)
  val terminalOutput = parseTerminalOutput(name, details.takeIf { it.length() > 0 } ?: synthetic, args)
  val kind = when {
    fileDiff != null || filePreview != null -> "file"
    terminalOutput != null || name.isTerminalLikeTool() -> "terminal"
    else -> "tool"
  }
  val success = !item.optBoolean("isError", false)
  val detail = if (fileDiff == null && filePreview == null && terminalOutput == null) {
    contentText.take(520).ifEmpty { summarizeToolPayload(args, rawDetails) }
  } else {
    ""
  }
  return HanakoHistoryMessage(
    id = id * 100L + 60L,
    role = "tool",
    text = name,
    timestampMs = timestampMs,
    toolStatus = HanakoToolStatus(
      key = item.cleanString("toolCallId")
        ?: item.cleanString("tool_use_id")
        ?: "remote-result:$id:$name",
      kind = kind,
      toolName = name,
      filePath = fileDiff?.filePath ?: filePreview?.filePath.orEmpty(),
      title = fileDiff?.fileName?.let { "文件：$it" }
        ?: filePreview?.fileName?.let { "文件：$it" }
        ?: terminalOutput?.title?.ifBlank { null }
        ?: toolDisplayName(name),
      subtitle = "历史记录",
      detail = detail,
      status = if (success) "done" else "failed",
      running = false,
      success = success,
      filePreview = filePreview,
      fileDiff = fileDiff,
      terminalOutput = terminalOutput,
    ),
  )
}

internal fun parseCachedContentBlock(root: JSONObject?): HanakoContentBlock? {
  if (root == null) return null
  return HanakoContentBlock(
    type = root.cleanString("type").orEmpty(),
    title = root.cleanString("title") ?: return null,
    subtitle = root.cleanString("subtitle").orEmpty(),
    detail = root.cleanString("detail").orEmpty(),
    status = root.cleanString("status").orEmpty(),
    taskId = root.cleanString("taskId").orEmpty(),
  )
}

internal fun parseCachedToolStatus(root: JSONObject?): HanakoToolStatus? {
  if (root == null) return null
  return HanakoToolStatus(
    key = root.cleanString("key").orEmpty(),
    kind = root.cleanString("kind") ?: "tool",
    toolName = root.cleanString("toolName").orEmpty(),
    filePath = root.cleanString("filePath").orEmpty(),
    title = root.cleanString("title") ?: return null,
    subtitle = root.cleanString("subtitle").orEmpty(),
    detail = root.cleanString("detail").orEmpty(),
    status = root.cleanString("status").orEmpty(),
    running = root.optBoolean("running", false),
    success = if (root.has("success") && !root.isNull("success")) root.optBoolean("success") else null,
    filePreview = parseCachedFilePreview(root.optJSONObject("filePreview")),
    fileDiff = parseCachedFileDiff(root.optJSONObject("fileDiff")),
    terminalOutput = parseCachedTerminalOutput(root.optJSONObject("terminalOutput")),
    taskCompletionEvidence = parseNbgTaskCompletionEvidenceBundle(root.optJSONObject("taskCompletionEvidence")),
  )
}

internal fun parseCachedFilePreview(root: JSONObject?): HanakoFilePreview? {
  if (root == null) return null
  return HanakoFilePreview(
    fileName = root.cleanString("fileName") ?: return null,
    filePath = root.cleanString("filePath").orEmpty(),
    previewText = root.rawStringOrNull("previewText").orEmpty(),
    truncated = root.optBoolean("truncated", false),
    append = root.optBoolean("append", false),
    reset = root.optBoolean("reset", false),
  )
}

internal fun parseCachedFileDiff(root: JSONObject?): HanakoFileDiff? {
  if (root == null) return null
  return HanakoFileDiff(
    fileName = root.cleanString("fileName") ?: return null,
    filePath = root.cleanString("filePath").orEmpty(),
    oldContent = nbgTrimFileDiffText(root.rawStringOrNull("oldContent").orEmpty()),
    newContent = nbgTrimFileDiffText(root.rawStringOrNull("newContent").orEmpty()),
    unifiedDiff = nbgTrimFileDiffText(root.rawStringOrNull("unifiedDiff").orEmpty()),
  )
}

internal fun parseCachedTerminalOutput(root: JSONObject?): HanakoTerminalOutput? {
  if (root == null) return null
  return HanakoTerminalOutput(
    sessionId = root.cleanString("sessionId") ?: return null,
    title = root.cleanString("title").orEmpty(),
    cwd = root.cleanString("cwd").orEmpty(),
    output = root.rawStringOrNull("output").orEmpty(),
    staticOutput = root.optBoolean("staticOutput", false),
    alive = if (root.has("alive") && !root.isNull("alive")) root.optBoolean("alive") else null,
    exitCode = root.optIntOrNull("exitCode"),
    truncated = root.optBoolean("truncated", false),
    outputPriority = root.optInt("outputPriority", 0),
    sliceFrom = root.optIntOrNull("sliceFrom"),
    sliceTo = root.optIntOrNull("sliceTo"),
  )
}

internal fun nbgSessionJsonlLooksUnhealthyForTest(raw: String): Boolean =
  nbgSessionJsonlLooksUnhealthy(raw.lineSequence())

internal fun nbgLocalSessionLooksUnhealthy(sessionFile: File): Boolean =
  runCatching {
    sessionFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
      nbgSessionJsonlLooksUnhealthy(lines)
    }
  }.getOrDefault(false)

internal fun nbgSessionJsonlLooksUnhealthy(lines: Sequence<String>): Boolean {
  val recentAssistantErrors = ArrayDeque<Boolean>()
  lines.forEach { line ->
    val entry = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
    if (entry.optString("type") != "message") return@forEach
    val message = entry.optJSONObject("message") ?: return@forEach
    if (message.cleanString("role") != "assistant") return@forEach
    recentAssistantErrors.addLast(nbgLocalAssistantMessageIsError(entry, message))
    while (recentAssistantErrors.size > HANA_UNHEALTHY_SESSION_RECENT_ASSISTANT_LIMIT) {
      recentAssistantErrors.removeFirst()
    }
  }
  val assistantCount = recentAssistantErrors.size
  if (assistantCount < HANA_UNHEALTHY_SESSION_MIN_ERRORS) return false
  val errorCount = recentAssistantErrors.count { it }
  return errorCount >= HANA_UNHEALTHY_SESSION_MIN_ERRORS &&
    errorCount * 100 >= assistantCount * HANA_UNHEALTHY_SESSION_ERROR_RATIO_PERCENT
}

internal fun nbgLocalAssistantMessageIsError(entry: JSONObject, message: JSONObject): Boolean {
  val stopReason = message.cleanStringAny("stopReason", "stop_reason", "finishReason", "finish_reason")
    ?: entry.cleanStringAny("stopReason", "stop_reason", "finishReason", "finish_reason")
  if (stopReason.equals("error", ignoreCase = true)) return true
  if (message.optBoolean("isError", false) || entry.optBoolean("isError", false)) return true
  return message.cleanStringAny("errorMessage", "error_message", "error", "exception") != null ||
    entry.cleanStringAny("errorMessage", "error_message", "error", "exception") != null
}

fun parseLocalSessionJsonl(sessionFile: File, limit: Int = Int.MAX_VALUE): HanakoHistorySnapshot {
  val rows = mutableListOf<HanakoHistoryMessage>()
  val pendingToolCalls = mutableMapOf<String, HanakoLocalToolCall>()
  sessionFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
    var index = 0L
    lines.forEach { line ->
      val entry = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
      if (entry.optString("type") != "message") return@forEach
      val message = entry.optJSONObject("message") ?: return@forEach
      val role = message.cleanString("role") ?: return@forEach
      val timestampMs = parseHanakoTimestampMs(message.cleanString("timestamp") ?: entry.cleanString("timestamp"))
      val id = message.cleanString("id")?.toLongOrNull()
        ?: entry.cleanString("id")?.filter { it.isDigit() }?.takeLast(12)?.toLongOrNull()
        ?: index
      when (role) {
        "user" -> {
          val parts = parseLocalContentParts(message.opt("content"), stripThink = false)
          if (parts.text.isNotBlank()) {
            rows += HanakoHistoryMessage(
              id = id,
              role = "user",
              text = nbgDisplayTextForRole("user", parts.text),
              timestampMs = timestampMs,
            )
          }
        }
        "assistant" -> {
          val parts = parseLocalContentParts(message.opt("content"), stripThink = true)
          val toolCalls = collectLocalToolCalls(message.opt("content"), pendingToolCalls)
          if (parts.thinking.isNotBlank()) {
            rows += HanakoHistoryMessage(
              id = id * 100L + 1L,
              role = "thinking",
              text = nbgThinkingTextForAndroidDisplay(parts.thinking),
              timestampMs = timestampMs,
            )
          }
          toolCalls.forEachIndexed { toolIndex, call ->
            val name = call.name
            val synthetic = JSONObject().put("name", name)
            call.args?.let { synthetic.put("args", it) }
            val terminalOutput = parseTerminalOutput(name, synthetic, call.args)
            val filePreview = parseFilePreviewFromArgs(name, call.args)
            val kind = when {
              filePreview != null -> "file"
              terminalOutput != null || name.isTerminalLikeTool() -> "terminal"
              else -> "tool"
            }
            val detail = if (terminalOutput != null || filePreview != null) {
              ""
            } else {
              summarizeToolPayload(call.args, null)
            }
            rows += HanakoHistoryMessage(
              id = id * 100L + 10L + toolIndex,
              role = "tool",
              text = name,
              timestampMs = timestampMs,
              toolStatus = HanakoToolStatus(
                key = "local:${call.id}:$id:$toolIndex:$name",
                kind = kind,
                toolName = name,
                filePath = filePreview?.filePath.orEmpty(),
                title = filePreview?.fileName?.let { "文件：$it" }
                  ?: terminalOutput?.title?.ifBlank { null }
                  ?: toolDisplayName(name),
                subtitle = "历史记录",
                detail = detail,
                running = false,
                filePreview = filePreview,
                terminalOutput = terminalOutput,
              ),
            )
          }
          val displayText = nbgDisplayTextForRole("assistant", parts.text)
          if (displayText.isNotBlank()) {
            rows += HanakoHistoryMessage(id = id, role = "assistant", text = displayText, timestampMs = timestampMs)
          }
        }
        "toolResult" -> {
          parseLocalToolResultMessage(message, id, timestampMs, pendingToolCalls)?.let { result ->
            val placeholderIndex = rows.indexOfLast {
              it.role == "tool" &&
                it.toolStatus?.key?.startsWith("local:${result.localToolCallId}:") == true
            }.takeIf { it >= 0 } ?: rows.indexOfLast {
              it.role == "tool" &&
                it.toolStatus?.key?.startsWith("local:") == true &&
                it.toolStatus.toolName == result.toolStatus?.toolName &&
                !it.toolStatus.hasInlinePreview
            }
            if (placeholderIndex >= 0) rows.removeAt(placeholderIndex)
            rows += result
          }
        }
      }
      index++
    }
  }
  return HanakoHistorySnapshot(messages = rows.takeLast(limit))
}

internal fun collectLocalToolCalls(
  content: Any?,
  pendingToolCalls: MutableMap<String, HanakoLocalToolCall>,
): List<HanakoLocalToolCall> {
  if (content !is JSONArray) return emptyList()
  val calls = mutableListOf<HanakoLocalToolCall>()
  for (index in 0 until content.length()) {
    val block = content.optJSONObject(index) ?: continue
    val type = block.optString("type")
    if (type != "tool_use" && type != "toolCall") continue
    val name = block.cleanString("name") ?: continue
    val args = block.optJSONObject("input")
      ?: block.optJSONObject("args")
      ?: block.optJSONObject("arguments")
    val ids = listOfNotNull(
      block.cleanString("id"),
      block.cleanString("toolCallId"),
      block.cleanString("tool_use_id"),
    )
    val call = HanakoLocalToolCall(
      id = ids.firstOrNull() ?: "index-$index-${name.stableToolKeyPart()}",
      name = name,
      args = args,
    )
    calls += call
    ids.forEach { id -> pendingToolCalls[id] = call }
  }
  return calls
}

internal fun parseLocalToolResultMessage(
  message: JSONObject,
  id: Long,
  timestampMs: Long,
  pendingToolCalls: Map<String, HanakoLocalToolCall>,
): HanakoHistoryMessage? {
  val toolCallId = message.cleanString("toolCallId")
    ?: message.cleanString("tool_use_id")
    ?: message.cleanString("id")
  val pending = toolCallId?.let { pendingToolCalls[it] }
  val name = message.cleanString("toolName")
    ?: message.cleanString("name")
    ?: pending?.name
    ?: return null
  val args = message.optJSONObject("args")
    ?: message.optJSONObject("input")
    ?: pending?.args
  val contentText = localContentText(message.opt("content"))
  val rawDetails = message.optJSONObject("details")
  val details = detailsWithFallbackOutput(rawDetails, contentText, allowTextFallback = name.isTerminalLikeTool())
  val success = !message.optBoolean("isError", false)
  val synthetic = JSONObject()
    .put("name", name)
    .put("success", success)
  args?.let { synthetic.put("args", it) }
  if (details.length() > 0) synthetic.put("details", details)
  val fileDiff = parseFileDiff(name, synthetic)
  val filePreview = parseFilePreview(details) ?: parseLocalFilePreview(name, args, contentText)
  val terminalOutput = parseTerminalOutput(name, details.takeIf { it.length() > 0 } ?: synthetic, args)
  val hasInlinePreview = filePreview != null || fileDiff != null || terminalOutput != null
  val kind = when {
    fileDiff != null || filePreview != null -> "file"
    terminalOutput != null || name.isTerminalLikeTool() -> "terminal"
    else -> "tool"
  }
  val detail = if (hasInlinePreview) {
    ""
  } else {
    contentText.take(520).ifEmpty { summarizeToolPayload(args, rawDetails) }
  }
  val status = if (success) "done" else "failed"
  return HanakoHistoryMessage(
    id = id * 100L + 60L,
    role = "tool",
    text = name,
    timestampMs = timestampMs,
    toolStatus = HanakoToolStatus(
      key = "local-result:${toolCallId ?: id}:$name",
      kind = kind,
      toolName = name,
      filePath = fileDiff?.filePath ?: filePreview?.filePath.orEmpty(),
      title = fileDiff?.fileName?.let { "文件：$it" }
        ?: filePreview?.fileName?.let { "文件：$it" }
        ?: terminalOutput?.title?.ifBlank { null }
        ?: toolDisplayName(name),
      subtitle = "历史记录",
      detail = detail,
      status = status,
      running = false,
      success = success,
      filePreview = filePreview,
      fileDiff = fileDiff,
      terminalOutput = terminalOutput,
    ),
  )
}

internal val HanakoHistoryMessage.localToolCallId: String?
  get() = toolStatus?.key
    ?.takeIf { it.startsWith("local-result:") }
    ?.removePrefix("local-result:")
    ?.substringBefore(':')

internal fun String.stableToolKeyPart(): String =
  filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    .take(48)
    .ifBlank { "tool" }

internal fun localContentText(content: Any?): String =
  parseLocalContentParts(content, stripThink = false).text

internal fun detailsWithFallbackOutput(details: JSONObject?, contentText: String, allowTextFallback: Boolean): JSONObject {
  val copy = JSONObject()
  if (details != null) {
    val keys = details.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      copy.put(key, details.opt(key))
    }
  }
  if (allowTextFallback && contentText.isNotEmpty() && !copy.hasAnyOutputField()) {
    copy.put("output", contentText)
  }
  return copy
}

internal fun JSONObject.hasAnyOutputField(): Boolean =
  rawStringAnyOrNull("output", "outputText", "result", "logs", "stdout", "stderr", "content", "text") != null

internal fun parseLocalFilePreview(name: String, args: JSONObject?, contentText: String): HanakoFilePreview? {
  if (name !in HANA_FILE_TOUCH_TOOLS) return null
  val safeArgs = args ?: return null
  val filePath = safeArgs.cleanString("path") ?: safeArgs.cleanString("file_path") ?: return null
  val content = safeArgs.rawStringOrNull("content") ?: return null
  val fileName = filePath.substringAfterLast('/').substringAfterLast('\\').takeIf { it.isNotBlank() } ?: filePath
  return HanakoFilePreview(
    fileName = fileName,
    filePath = filePath,
    previewText = content.take(HANA_FILE_WRITE_PREVIEW_LIMIT),
    truncated = content.length > HANA_FILE_WRITE_PREVIEW_LIMIT || contentText.contains("truncated", ignoreCase = true),
    append = false,
    reset = true,
  )
}

internal fun parseLocalContentParts(content: Any?, stripThink: Boolean): HanakoLocalContentParts {
  if (content is String) {
    val stripped = if (stripThink) stripLocalThinkTags(content) else content to ""
    return HanakoLocalContentParts(text = stripped.first, thinking = stripped.second)
  }
  if (content !is JSONArray) return HanakoLocalContentParts()
  val texts = mutableListOf<String>()
  val thinking = mutableListOf<String>()
  val tools = mutableListOf<String>()
  for (index in 0 until content.length()) {
    val block = content.optJSONObject(index) ?: continue
    when (block.optString("type")) {
      "text", "input_text", "output_text" -> block.rawStringOrNull("text")?.let { texts += it }
      "thinking" -> block.rawStringOrNull("thinking")
        ?.let { thinking += it }
        ?: block.rawStringOrNull("text")?.let { thinking += it }
      "tool_use", "toolCall" -> block.cleanString("name")?.let { tools += it }
    }
  }
  val rawText = texts.joinToString("")
  val stripped = if (stripThink) stripLocalThinkTags(rawText) else rawText to ""
  return HanakoLocalContentParts(
    text = stripped.first,
    thinking = listOf(stripped.second, thinking.joinToString("\n")).filter { it.isNotBlank() }.joinToString("\n"),
    toolNames = tools,
  )
}

internal fun stripLocalThinkTags(raw: String): Pair<String, String> {
  val thinking = mutableListOf<String>()
  val text = NBG_LOCAL_THINK_BLOCK_REGEX
    .replace(raw) { match ->
      thinking += match.groupValues.getOrNull(1).orEmpty().trim()
      ""
    }
  return text to thinking.filter { it.isNotBlank() }.joinToString("\n")
}

private val NBG_INTERNAL_NORMAL_BLOCK_REGEX =
  Regex("<(mood|pulse|reflect|think|thinking)>[\\s\\S]*?</(?:mood|pulse|reflect|think|thinking)>\\s*", RegexOption.IGNORE_CASE)
private val NBG_INTERNAL_MALFORMED_THINKING_BLOCK_REGEX =
  Regex("<think(?:ing)?>[\\s\\S]*?(?:<think(?:ing)?>|$)\\s*", RegexOption.IGNORE_CASE)
private val NBG_INTERNAL_TAG_ONLY_REGEX =
  Regex("</?(?:mood|pulse|reflect|think|thinking)>\\s*", RegexOption.IGNORE_CASE)
private val NBG_INTERNAL_PARTIAL_TAG_SUFFIX_REGEX =
  Regex("<\\s*/?\\s*(?:m(?:o(?:o(?:d)?)?)?|p(?:u(?:l(?:s(?:e)?)?)?)?|r(?:e(?:f(?:l(?:e(?:c(?:t)?)?)?)?)?)?|t(?:h(?:i(?:n(?:k(?:i(?:n(?:g)?)?)?)?)?)?)?)?\\s*$", RegexOption.IGNORE_CASE)
private val NBG_LOCAL_THINK_BLOCK_REGEX =
  Regex("<think(?:ing)?>([\\s\\S]*?)(?:</think(?:ing)?>|<think(?:ing)?>|$)\\n*", RegexOption.IGNORE_CASE)

internal fun parseHanakoContentBlock(block: JSONObject?): HanakoContentBlock? {
  if (block == null) return null
  val type = block.optString("type").ifBlank { return null }
  if (type == "session_confirmation") return null
  return when (type) {
    "file" -> {
      val label = block.cleanString("label")
        ?: block.cleanString("displayName")
        ?: block.cleanString("filename")
        ?: block.cleanString("filePath")?.substringAfterLast('/')
        ?: "文件"
      val ext = block.cleanString("ext")
      val path = block.cleanString("filePath").orEmpty()
      HanakoContentBlock(
        type = type,
        title = label,
        subtitle = listOfNotNull("文件", ext, block.cleanString("status")).joinToString(" / "),
        detail = path,
        status = block.cleanString("status").orEmpty(),
      )
    }
    "artifact" -> HanakoContentBlock(
      type = type,
      title = block.cleanString("title") ?: block.cleanString("label") ?: "Artifact",
      subtitle = listOfNotNull("artifact", block.cleanString("language"), block.cleanString("artifactType")).joinToString(" / "),
      detail = block.cleanString("content")?.take(220).orEmpty(),
      status = block.cleanString("status").orEmpty(),
    )
    "screenshot" -> HanakoContentBlock(
      type = type,
      title = "截图",
      subtitle = block.cleanString("mimeType") ?: "image",
      detail = "HanakoPro 返回了一张截图",
    )
    "subagent" -> HanakoContentBlock(
      type = type,
      title = block.cleanString("taskTitle") ?: block.cleanString("task") ?: "子任务",
      subtitle = listOfNotNull("subagent", block.cleanString("streamStatus"), block.cleanString("agentName")).joinToString(" / "),
      detail = block.cleanString("summary") ?: block.cleanString("task").orEmpty(),
      status = block.cleanString("streamStatus").orEmpty(),
      taskId = block.cleanString("taskId").orEmpty(),
    )
    else -> {
      val title = block.cleanString("title")
        ?: block.cleanString("label")
        ?: block.cleanString("fileName")
        ?: block.cleanString("path")
        ?: block.cleanString("summary")
        ?: type
      HanakoContentBlock(type = type, title = title, subtitle = "内容块", detail = "")
    }
  }
}

internal fun parseHanakoContentBlockPatch(patch: JSONObject?): HanakoContentBlockPatch? {
  if (patch == null) return null
  val status = patch.cleanString("streamStatus") ?: patch.cleanString("status")
  val subtitle = listOfNotNull(
    "subagent",
    status,
    patch.cleanString("agentName"),
  ).joinToString(" / ").takeIf { it != "subagent" }
  val blockPatch = HanakoContentBlockPatch(
    title = patch.cleanString("taskTitle") ?: patch.cleanString("title"),
    subtitle = subtitle,
    detail = patch.cleanString("summary") ?: patch.cleanString("detail"),
    status = status,
  )
  return blockPatch.takeIf {
    !it.title.isNullOrBlank() ||
      !it.subtitle.isNullOrBlank() ||
      !it.detail.isNullOrBlank() ||
      !it.status.isNullOrBlank()
  }
}

internal fun parseHanakoConfirmationBlock(block: JSONObject?): HanakoConfirmation? {
  if (block == null || block.optString("type") != "session_confirmation") return null
  val confirmId = block.optString("confirmId").trim()
  if (confirmId.isBlank()) return null
  val subject = block.optJSONObject("subject")
  val actions = block.optJSONObject("actions")
  val risk = nbgPermissionRiskForConfirmationBlock(block)
  return HanakoConfirmation(
    confirmId = confirmId,
    title = block.optString("title").ifBlank { "需要确认" },
    body = block.optString("body").orEmpty(),
    subjectLabel = subject?.optString("label").orEmpty(),
    subjectDetail = subject?.optString("detail").orEmpty(),
    severity = block.optString("severity").ifBlank { "normal" },
    riskTier = risk.tier.wireName,
    riskLabel = risk.tier.label,
    targetLabel = risk.targetLabel,
    recoveryHint = risk.recoveryHint,
    confirmLabel = actions?.optString("confirmLabel")?.takeIf { it.isNotBlank() } ?: "同意",
    rejectLabel = actions?.optString("rejectLabel")?.takeIf { it.isNotBlank() } ?: "拒绝",
    status = block.optString("status").ifBlank { "pending" },
  )
}

internal fun parseHanakoExternalUserMessage(message: JSONObject?): HanakoExternalUserMessage? {
  if (message == null) return null
  val text = message.cleanString("text").orEmpty()
  val quotedText = message.cleanString("quotedText").orEmpty()
  val attachments = message.optJSONArray("attachments")
  val attachmentLabels = buildList {
    if (attachments != null) {
      for (index in 0 until attachments.length()) {
        val attachment = attachments.optJSONObject(index) ?: continue
        val label = attachment.cleanString("name")
          ?: attachment.cleanString("label")
          ?: attachment.cleanString("path")?.substringAfterLast('/')
          ?: continue
        add(label)
      }
    }
  }
  if (text.isBlank() && quotedText.isBlank() && attachmentLabels.isEmpty()) return null
  return HanakoExternalUserMessage(text = text, quotedText = quotedText, attachmentLabels = attachmentLabels)
}

internal fun parseHanakoTeamAgentStatus(root: JSONObject?, fallbackTaskId: String = ""): HanakoTeamAgentStatus? {
  if (root == null) return null
  val envelope = root.objectAny("payload", "data")
  val source = root.nestedObjectAny("agent", "member", "payload.agent", "payload.member", "data.agent", "data.member")
    ?: envelope
    ?: root
  val taskId = source.cleanString("taskId")
    ?: root.cleanString("taskId")
    ?: envelope?.cleanString("taskId")
    ?: fallbackTaskId
  val role = source.cleanStringAny("role", "roleId", "name", "title") ?: return null
  val agentId = source.cleanStringAny("agentId", "id", "memberId")
    ?: nbgTeamAgentId(role)
  return HanakoTeamAgentStatus(
    taskId = taskId,
    agentId = agentId,
    role = role,
    title = source.cleanString("title") ?: root.cleanString("title") ?: role,
    status = source.cleanStringAny("status", "state", "phase")
      ?: root.cleanStringAny("status", "state", "phase")
      ?: "idle",
    summary = source.cleanStringAny("summary", "message", "detail")
      ?: root.cleanStringAny("summary", "message", "detail")
      ?: "",
    artifactRefs = (source.optJSONArray("artifactRefs") ?: source.optJSONArray("artifacts")).toCleanStringList(limit = 12),
    updatedAt = source.optLong("updatedAt", root.optLong("updatedAt", System.currentTimeMillis())),
  )
}

internal fun parseHanakoTeamTaskStatus(root: JSONObject?, existing: HanakoTeamTaskStatus? = null): HanakoTeamTaskStatus? {
  if (root == null) return null
  val source = root.nestedObjectAny("task", "payload.task", "data.task", "payload", "data") ?: root
  val taskId = source.cleanStringAny("taskId", "id")
    ?: root.cleanStringAny("taskId", "id")
    ?: existing?.taskId
    ?: return null
  val agentsArray = source.optJSONArray("agents") ?: root.optJSONArray("agents")
  val parsedAgents = buildList {
    if (agentsArray != null) {
      for (index in 0 until agentsArray.length()) {
        parseHanakoTeamAgentStatus(agentsArray.optJSONObject(index), taskId)?.let(::add)
      }
    }
  }
  val nextStatus = source.cleanStringAny("status", "state", "phase")
    ?: root.cleanStringAny("status", "state", "phase")
    ?: existing?.status
    ?: "running"
  return HanakoTeamTaskStatus(
    taskId = taskId,
    title = source.cleanString("title") ?: root.cleanString("title") ?: existing?.title ?: "代码团队任务",
    mode = source.cleanString("mode") ?: root.cleanString("mode") ?: existing?.mode ?: "auto",
    status = nextStatus,
    summary = source.cleanStringAny("summary", "message", "detail")
      ?: root.cleanStringAny("summary", "message", "detail")
      ?: existing?.summary.orEmpty(),
    agents = parsedAgents
      .ifEmpty { existing?.agents.orEmpty() }
      .ifEmpty { nbgDefaultTeamAgents(taskId, nextStatus) },
  )
}

internal fun nbgParseHanakoTeamTaskStatusForTest(
  root: JSONObject,
  existing: HanakoTeamTaskStatus? = null,
): HanakoTeamTaskStatus? = parseHanakoTeamTaskStatus(root, existing)

internal fun nbgParseHanakoTeamAgentStatusForTest(
  root: JSONObject,
  fallbackTaskId: String = "",
): HanakoTeamAgentStatus? = parseHanakoTeamAgentStatus(root, fallbackTaskId)

internal fun nbgMergeHanakoTeamAgentForTest(
  task: HanakoTeamTaskStatus,
  agent: HanakoTeamAgentStatus,
): HanakoTeamTaskStatus = task.mergeAgentIfTaskCanAccept(agent)

internal fun nbgNormalizeAndroidTeamTaskForTest(task: HanakoTeamTaskStatus): HanakoTeamTaskStatus =
  task.normalizedAndroidTeamTask()

internal fun HanakoTeamTaskStatus.mergeAgent(agent: HanakoTeamAgentStatus): HanakoTeamTaskStatus =
  run {
    val promotesToRealTeam = mode == HANA_TEAM_SINGLE_AGENT_SESSION_MODE && agents.none { it.agentId == agent.agentId }
    val baseAgents = if (promotesToRealTeam) agents.filterNot { it.role == "Hanako" } else agents
    copy(
      mode = if (promotesToRealTeam) "auto" else mode,
      taskId = agent.taskId.ifBlank { taskId },
      status = when {
        isTerminal -> status
        agent.running && nbgNormalizeTeamStatus(status) in setOf("idle", "queued") -> "running"
        else -> status
      },
      agents = (baseAgents.filterNot { it.agentId == agent.agentId } + agent).sortedBy { it.defaultOrder },
    )
  }

internal fun HanakoTeamTaskStatus.mergeAgentIfTaskCanAccept(agent: HanakoTeamAgentStatus): HanakoTeamTaskStatus {
  if (isTerminal && !agent.isTerminal) return this
  return mergeAgent(agent)
}

internal fun HanakoTeamTaskStatus.withUpdatedActiveAgents(
  status: String,
  summary: String,
): HanakoTeamTaskStatus =
  copy(
    agents = agents.map { agent ->
      if (agent.activeOrPending) agent.copy(status = status, summary = summary, updatedAt = System.currentTimeMillis()) else agent
    },
  )

internal val HanakoTeamAgentStatus.activeOrPending: Boolean
  get() = running || nbgNormalizeTeamStatus(status) in setOf("queued", "pending")

internal val HanakoTeamTaskStatus.isTerminal: Boolean
  get() = nbgNormalizeTeamStatus(status) in setOf("completed", "done", "success", "failed", "error", "aborted", "cancelled", "canceled")

internal val HanakoTeamAgentStatus.isTerminal: Boolean
  get() = nbgNormalizeTeamStatus(status) in setOf("completed", "done", "success", "failed", "error", "aborted", "cancelled", "canceled")

internal fun HanakoTeamTaskStatus.teamRuntimeLabel(): String =
  if (isBackendAgentToolExecution()) {
    when (nbgNormalizeTeamStatus(status)) {
      "completed", "done", "success" -> "多 Agent：已完成"
      "failed", "error" -> "多 Agent：失败"
      "aborted", "cancelled", "canceled" -> "多 Agent：已停止"
      else -> if (activeCount > 1) "多 Agent：$activeCount 工作中" else "多 Agent：正在运行"
    }
  } else {
    when (nbgNormalizeTeamStatus(status)) {
      "completed", "done", "success" -> "团队：已完成"
      "failed", "error" -> "团队：失败"
      "aborted", "cancelled", "canceled" -> "团队：已停止"
      else -> if (isMultiAgentSession()) "团队：${activeCount} 工作中" else if (isSingleAgentExecution()) "团队：单 Agent" else "团队：${activeCount} 工作中"
    }
  }

internal fun HanakoTeamTaskStatus.withActiveTurnFinished(status: String, summary: String): HanakoTeamTaskStatus =
  copy(
    status = status,
    summary = summary,
    agents = agents.map { agent ->
      if (agent.activeOrPending) agent.copy(status = status, summary = summary, updatedAt = System.currentTimeMillis()) else agent
    },
  )

internal fun HanakoTeamTaskStatus.withFallbackTurnFinished(status: String, summary: String): HanakoTeamTaskStatus =
  withActiveTurnFinished(status, summary)

internal fun HanakoTeamTaskStatus.isMultiAgentSession(): Boolean =
  mode == HANA_TEAM_MULTI_AGENT_SESSION_MODE

internal fun HanakoTeamTaskStatus.isBackendAgentToolExecution(): Boolean =
  mode == HANA_TEAM_BACKEND_AGENT_MODE

internal fun HanakoTeamTaskStatus.isLocalSessionExecution(): Boolean =
  isSingleAgentExecution() || isMultiAgentSession()

internal fun HanakoTeamTaskStatus.isSingleAgentFallback(): Boolean =
  mode == HANA_TEAM_SINGLE_AGENT_FALLBACK_MODE

internal fun HanakoTeamTaskStatus.isSingleAgentExecution(): Boolean =
  mode == HANA_TEAM_SINGLE_AGENT_FALLBACK_MODE || mode == HANA_TEAM_SINGLE_AGENT_SESSION_MODE

internal fun HanakoTeamTaskStatus.toSingleAgentFallback(summary: String): HanakoTeamTaskStatus =
  copy(
    mode = HANA_TEAM_SINGLE_AGENT_FALLBACK_MODE,
    status = "running",
    summary = summary,
    agents = nbgSingleAgentSessionAgent(taskId, "running", "默认 Hanako Agent 正在执行"),
  )

internal fun HanakoTeamTaskStatus.toSingleAgentSessionAdapter(): HanakoTeamTaskStatus {
  val normalizedStatus = nbgNormalizeTeamStatus(status).ifBlank { "running" }
  val agentStatus = if (normalizedStatus in setOf("queued", "running", "thinking", "working")) normalizedStatus else status
  val existingAgent = agents.firstOrNull()
  return copy(
    mode = HANA_TEAM_SINGLE_AGENT_SESSION_MODE,
    summary = summary.ifBlank { HANA_TEAM_EXISTING_SESSION_SUMMARY },
    agents = listOf(
      HanakoTeamAgentStatus(
        taskId = taskId,
        agentId = existingAgent?.agentId?.takeIf { it.isNotBlank() } ?: "hanako",
        role = "Hanako",
        title = "Hanako Agent",
        status = agentStatus.ifBlank { "running" },
        summary = existingAgent?.summary?.takeIf { it.isNotBlank() } ?: "通过现有 HanakoPro 会话执行",
        artifactRefs = existingAgent?.artifactRefs.orEmpty(),
        updatedAt = existingAgent?.updatedAt?.takeIf { it > 0L } ?: System.currentTimeMillis(),
      ),
    ),
  )
}

internal fun HanakoTeamTaskStatus.normalizedAndroidTeamTask(): HanakoTeamTaskStatus {
  if (mode == HANA_TEAM_SINGLE_AGENT_SESSION_MODE) return toSingleAgentSessionAdapter()
  val normalizedMode = mode.trim().lowercase()
  val singleAgentPayload = agents.size <= 1
  val backendAdvertisesExistingSession = summary.contains(HANA_TEAM_EXISTING_SESSION_SUMMARY) && singleAgentPayload
  val onlySupervisor = agents.size == 1 && agents.firstOrNull()?.agentId == "supervisor"
  return if (normalizedMode == "auto" && (backendAdvertisesExistingSession || onlySupervisor)) {
    toSingleAgentSessionAdapter()
  } else {
    this
  }
}

internal fun nbgTeamTaskEventStatus(type: String, currentStatus: String): String =
  when (type) {
    "team_task_started" -> if (nbgNormalizeTeamStatus(currentStatus) in setOf("idle", "queued")) "running" else currentStatus
    "team_task_completed" -> if (nbgNormalizeTeamStatus(currentStatus) in setOf("idle", "running", "queued", "thinking", "working")) "completed" else currentStatus
    "team_task_failed" -> {
      val normalized = nbgNormalizeTeamStatus(currentStatus)
      if (normalized in setOf("aborted", "cancelled", "canceled")) normalized else "failed"
    }
    else -> currentStatus
  }

internal val HanakoTeamAgentStatus.defaultOrder: Int
  get() = when (role.lowercase()) {
    "supervisor" -> 0
    "researcher" -> 1
    "coder" -> 2
    "reviewer" -> 3
    "tester" -> 4
    "terminal" -> 5
    "file manager", "file_manager", "file-manager" -> 6
    else -> 20
  }

internal fun nbgDefaultTeamAgents(taskId: String, taskStatus: String): List<HanakoTeamAgentStatus> =
  HANAKO_DEFAULT_TEAM_AGENTS.mapIndexed { index, (role, title) ->
    HanakoTeamAgentStatus(
      taskId = taskId,
      agentId = nbgTeamAgentId(role),
      role = role,
      title = title,
      status = if (index == 0 && nbgNormalizeTeamStatus(taskStatus) in setOf("queued", "running", "thinking", "working")) "queued" else "idle",
    )
  }

internal fun nbgSingleAgentSessionAgent(taskId: String, taskStatus: String, summary: String): List<HanakoTeamAgentStatus> =
  listOf(
    HanakoTeamAgentStatus(
      taskId = taskId,
      agentId = "hanako",
      role = "Hanako",
      title = "Hanako Agent",
      status = taskStatus,
      summary = summary,
    ),
  )

internal fun nbgMultiAgentSessionAgents(taskId: String, taskStatus: String = "running"): List<HanakoTeamAgentStatus> {
  val active = nbgNormalizeTeamStatus(taskStatus) in setOf("queued", "running", "thinking", "working")
  val now = System.currentTimeMillis()
  return listOf(
    HanakoTeamAgentStatus(taskId, "supervisor", "Supervisor", "总控 Agent", if (active) "thinking" else taskStatus, "拆解目标、约束和执行顺序", updatedAt = now),
    HanakoTeamAgentStatus(taskId, "researcher", "Researcher", "调研 Agent", if (active) "running" else taskStatus, "读取源码、Memory、Skills、MCP 和代码图谱上下文", updatedAt = now),
    HanakoTeamAgentStatus(taskId, "coder", "Coder", "实现 Agent", if (active) "queued" else taskStatus, "等待进入实现步骤", updatedAt = now),
    HanakoTeamAgentStatus(taskId, "reviewer", "Reviewer", "审查 Agent", if (active) "queued" else taskStatus, "等待检查风险和回归", updatedAt = now),
    HanakoTeamAgentStatus(taskId, "tester", "Tester", "验证 Agent", if (active) "queued" else taskStatus, "等待验证结果", updatedAt = now),
  )
}

internal fun nbgBackendAgentToolTask(
  taskId: String,
  toolName: String,
  taskStatus: String = "running",
  subagentTaskIds: List<String> = emptyList(),
  summary: String = "HanakoPro 后端已触发 Agent 工具",
  existing: HanakoTeamTaskStatus? = null,
): HanakoTeamTaskStatus =
  run {
    val normalizedToolName = toolName.canonicalBackendAgentToolName() ?: toolName.trim().ifBlank { "Agent" }
    val mergedIds = (existing?.agents.orEmpty().flatMap { agent ->
      agent.artifactRefs.ifEmpty { listOf(agent.agentId.takeIf { it.startsWith("subagent-") }.orEmpty()) }
    } + subagentTaskIds)
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .distinct()
    HanakoTeamTaskStatus(
      taskId = taskId,
      title = if (normalizedToolName == "WolfPack") "后端 WolfPack" else "后端 Agent",
      mode = HANA_TEAM_BACKEND_AGENT_MODE,
      status = taskStatus,
      summary = summary,
      agents = nbgBackendAgentToolAgents(
        taskId = taskId,
        toolName = normalizedToolName,
        taskStatus = taskStatus,
        subagentTaskIds = mergedIds,
        existingAgents = existing?.agents.orEmpty(),
      ),
    )
  }

internal fun nbgBackendAgentToolAgents(
  taskId: String,
  toolName: String,
  taskStatus: String,
  subagentTaskIds: List<String>,
  existingAgents: List<HanakoTeamAgentStatus> = emptyList(),
): List<HanakoTeamAgentStatus> {
  val now = System.currentTimeMillis()
  val normalizedToolName = toolName.canonicalBackendAgentToolName() ?: toolName.trim().ifBlank { "Agent" }
  val knownIds = subagentTaskIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
  val slots = knownIds.ifEmpty { listOf("") }
  return slots.mapIndexed { index, subagentTaskId ->
    val agentId = if (subagentTaskId.isBlank()) {
      nbgTeamAgentId("$normalizedToolName-dispatcher")
    } else {
      nbgTeamAgentId(subagentTaskId)
    }
    val existing = existingAgents.firstOrNull { existing ->
      existing.agentId == agentId ||
        (subagentTaskId.isNotBlank() && subagentTaskId in existing.artifactRefs)
    }
    val normalizedExistingStatus = nbgNormalizeTeamStatus(existing?.status)
    val nextStatus = when {
      existing != null && existing.isTerminal -> existing.status
      normalizedExistingStatus == "running" && nbgNormalizeTeamStatus(taskStatus) == "completed" -> existing?.status ?: taskStatus
      else -> taskStatus
    }
    HanakoTeamAgentStatus(
      taskId = taskId,
      agentId = agentId,
      role = normalizedToolName,
      title = if (normalizedToolName == "WolfPack") "子 Agent ${index + 1}" else "后端 Agent",
      status = nextStatus,
      summary = existing?.summary?.takeIf { it.isNotBlank() }
        ?: if (subagentTaskId.isBlank()) "等待后端派发子任务" else "子任务 $subagentTaskId",
      artifactRefs = listOfNotNull(subagentTaskId.takeIf { it.isNotBlank() }),
      updatedAt = existing?.updatedAt?.takeIf { it > 0L } ?: now,
    )
  }
}

internal fun HanakoTeamTaskStatus.withBackendAgentToolSubtaskUpdate(
  subagentTaskId: String,
  status: String,
  summary: String,
): HanakoTeamTaskStatus {
  if (!isBackendAgentToolExecution()) return this
  val id = subagentTaskId.trim()
  if (id.isBlank()) return this
  val agentId = nbgTeamAgentId(id)
  val nextAgents = agents.map { agent ->
    if (agent.agentId == agentId || id in agent.artifactRefs) {
      agent.copy(status = status, summary = summary.ifBlank { agent.summary }, updatedAt = System.currentTimeMillis())
    } else {
      agent
    }
  }
  val normalizedStatuses = nextAgents.map { nbgNormalizeTeamStatus(it.status) }
  val nextStatus = when {
    normalizedStatuses.any { it in setOf("queued", "running", "thinking", "working", "coding", "reviewing", "testing", "terminal") } -> "running"
    normalizedStatuses.any { it in setOf("failed", "error") } -> "failed"
    normalizedStatuses.all { it in setOf("completed", "done", "success") } -> "completed"
    normalizedStatuses.any { it in setOf("aborted", "cancelled", "canceled") } -> "aborted"
    else -> status
  }
  return copy(
    status = nextStatus,
    summary = summary.ifBlank { this.summary },
    agents = nextAgents,
  )
}

internal fun nbgTeamAgentId(role: String): String =
  role.lowercase()
    .replace("&", "and")
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .ifBlank { "agent" }

internal fun nbgEncodeUrlPathSegment(value: String): String =
  java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

internal fun nbgTeamTaskTitle(prompt: String): String =
  prompt.lineSequence()
    .map { it.trim() }
    .firstOrNull { it.isNotBlank() }
    ?.take(48)
    ?: "代码团队任务"

data class HanakoChatState(
  val connectionLabel: String = "HanakoPro 未连接",
  val connected: Boolean = false,
  val connecting: Boolean = false,
  val prewarming: Boolean = false,
  val streaming: Boolean = false,
  val compressing: Boolean = false,
  val planModeEnabled: Boolean = false,
  val permissionMode: String = NBG_DEFAULT_PERMISSION_MODE,
  val permissionModeLabel: String = hanakoPermissionModeLabel(NBG_DEFAULT_PERMISSION_MODE),
  val thinkingLevel: String = "auto",
  val thinkingLevelLabel: String = hanakoThinkingLevelLabel("auto"),
  val bridgeStatusLabel: String? = null,
  val sessionPath: String? = null,
  val sessions: List<HanakoSessionSummary> = emptyList(),
  val searchResults: List<HanakoSessionSummary> = emptyList(),
  val searchQuery: String = "",
  val searching: Boolean = false,
  val agentName: String? = null,
  val modelName: String? = null,
  val contextUsageLabel: String? = null,
  val compressionAvailable: Boolean = false,
  val selectingSessionPath: String? = null,
  val mcpState: HanakoMcpState = HanakoMcpState(),
  val mcpLoading: Boolean = false,
  val mcpError: String? = null,
  val mcpBusyKey: String? = null,
  val memoryState: HanakoMemoryState = HanakoMemoryState(),
  val memoryLoading: Boolean = false,
  val memoryError: String? = null,
  val memoryBusyKey: String? = null,
  val skillsSnapshot: HanakoSkillsSnapshot = HanakoSkillsSnapshot(),
  val rawSkillsSnapshot: HanakoSkillsSnapshot = HanakoSkillsSnapshot(),
  val skillCuratorMetadata: NbgSkillCuratorMetadata = NbgSkillCuratorMetadata(),
  val learnedSkillDraftQueue: NbgLearnedSkillDraftQueue = NbgLearnedSkillDraftQueue(),
  val autonomousLearningSnapshot: NbgAutonomousLearningSnapshot = NbgAutonomousLearningSnapshot(),
  val skillsLoading: Boolean = false,
  val skillsError: String? = null,
  val skillsBusyKey: String? = null,
  val runtimeStatus: HanakoRuntimeStatus = HanakoRuntimeStatus(),
  val streamResumeDiagnostics: HanakoStreamResumeDiagnostics = HanakoStreamResumeDiagnostics(),
  val teamTask: HanakoTeamTaskStatus? = null,
  val lastError: String? = null,
)

fun hanakoPermissionModeLabel(mode: String?): String =
  when (nbgNormalizePermissionMode(mode)) {
    NBG_PERMISSION_MODE_ASK -> "先问"
    NBG_PERMISSION_MODE_READ_ONLY -> "计划"
    NBG_PERMISSION_MODE_OPERATE -> "操作"
    else -> "先问"
  }

fun hanakoThinkingLevelLabel(level: String?): String =
  when (level) {
    "off" -> "关闭"
    "low" -> "快速"
    "medium" -> "均衡"
    "high" -> "深度"
    "xhigh" -> "最大思考"
    else -> "自动"
  }

sealed interface HanakoChatEvent {
  data class UserMessage(val text: String) : HanakoChatEvent
  data class ExternalUserMessage(val message: HanakoExternalUserMessage) : HanakoChatEvent
  data class AssistantStarted(val messageId: Long) : HanakoChatEvent
  data class AssistantText(val messageId: Long, val text: String) : HanakoChatEvent
  data class AssistantDelta(val messageId: Long, val delta: String) : HanakoChatEvent
  data class AssistantRemoved(val messageId: Long) : HanakoChatEvent
  data class ThinkingStarted(val thinkingId: Long) : HanakoChatEvent
  data class ThinkingText(val thinkingId: Long, val text: String) : HanakoChatEvent
  data class ThinkingDelta(val thinkingId: Long, val delta: String) : HanakoChatEvent
  data class ThinkingEnded(val thinkingId: Long) : HanakoChatEvent
  data class SystemMessage(val text: String) : HanakoChatEvent
  data class ToolStatus(val tool: HanakoToolStatus) : HanakoChatEvent {
    constructor(text: String) : this(HanakoToolStatus(key = text, title = text))
  }
  data object ToolInterrupted : HanakoChatEvent
  data class ContentBlock(val block: HanakoContentBlock) : HanakoChatEvent
  data class ContentBlockPatch(val taskId: String, val patch: HanakoContentBlockPatch) : HanakoChatEvent
  data class ConfirmationRequested(val confirmation: HanakoConfirmation) : HanakoChatEvent
  data class ConfirmationResolved(
    val confirmId: String,
    val action: String,
    val auditEntry: NbgConfirmationResolutionAuditEntry? = null,
  ) : HanakoChatEvent
  data class AgentModelConfigLoaded(val config: HanakoAgentModelConfig) : HanakoChatEvent
  data class AgentModelConfigFailed(val message: String) : HanakoChatEvent
  data class ModelHealthLoaded(val health: HanakoModelHealth) : HanakoChatEvent
  data class ModelHealthFailed(val modelId: String, val provider: String, val message: String) : HanakoChatEvent
  data class ProvidersLoaded(val snapshot: HanakoProviderSnapshot) : HanakoChatEvent
  data class ProvidersFailed(val message: String) : HanakoChatEvent
  data class SlashCommandsLoaded(val commands: List<HanakoSlashCommand>) : HanakoChatEvent
  data class SlashCommandsFailed(val message: String) : HanakoChatEvent
  data class TeamTaskUpdated(val task: HanakoTeamTaskStatus?) : HanakoChatEvent
  data class SessionDeleted(val sessionPath: String) : HanakoChatEvent
  data class SessionSelectFailed(
    val sessionPath: String,
    val previousSessionPath: String? = null,
  ) : HanakoChatEvent
  data class HistoryLoaded(
    val sessionPath: String,
    val messages: List<HanakoHistoryMessage>,
    val todos: List<HanakoTodoItem> = emptyList(),
    val sessionFiles: List<HanakoSessionFile> = emptyList(),
  ) : HanakoChatEvent
  data class TodoUpdated(val todos: List<HanakoTodoItem>) : HanakoChatEvent
  data object TurnEnded : HanakoChatEvent
}

data class HanakoContentBlockPatch(
  val title: String? = null,
  val subtitle: String? = null,
  val detail: String? = null,
  val status: String? = null,
)

data class HanakoExternalUserMessage(
  val text: String,
  val quotedText: String = "",
  val attachmentLabels: List<String> = emptyList(),
)

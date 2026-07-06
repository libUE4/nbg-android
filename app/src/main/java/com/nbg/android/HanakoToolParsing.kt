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

internal fun nbgMoveReplacingWithAtomicFallback(source: File, target: File) {
  runCatching {
    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }.getOrElse {
    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
  }
}

internal fun nbgMoveReplacingWithAtomicFallbackForTest(source: File, target: File) =
  nbgMoveReplacingWithAtomicFallback(source, target)

internal const val HANA_TOOL_OUTPUT_PREVIEW_LIMIT = 12_000
internal const val HANA_FILE_WRITE_PREVIEW_LIMIT = 4_000
internal const val HANA_FILE_DIFF_TEXT_LIMIT = 12_000
internal const val HANA_UNHEALTHY_SESSION_RECENT_ASSISTANT_LIMIT = 10
internal const val HANA_UNHEALTHY_SESSION_MIN_ERRORS = 3
internal const val HANA_UNHEALTHY_SESSION_ERROR_RATIO_PERCENT = 70
internal val HANA_FILE_TOUCH_TOOLS = setOf("write", "edit", "edit-diff")
internal val HANA_TERMINAL_OUTPUT_PRIORITY = mapOf(
  "terminal_create" to 1,
  "terminal_read" to 2,
  "terminal_wait" to 3,
)

internal fun nbgHanakoProviderForUrlApi(baseUrl: String, modelId: String? = null): String {
  val normalized = nbgNormalizeApiBaseUrl(baseUrl).lowercase()
  return if (
    normalized.contains("anthropic") ||
    normalized.contains("claude")
  ) {
    "anthropic"
  } else {
    "openai"
  }
}

internal fun Int?.orZero(): Int = this ?: 0

internal fun parseFileDiff(name: String, msg: JSONObject): HanakoFileDiff? {
  if (name !in HANA_FILE_TOUCH_TOOLS) return null
  val details = msg.optJSONObject("details") ?: msg
  val args = msg.optJSONObject("args")
  val unifiedDiff = listOf(details, args, msg)
    .firstNotNullOfOrNull { it?.rawStringAnyOrNull("unifiedDiff", "diff", "patch") }
    .orEmpty()
  val oldContent = details.rawStringOrNull("oldContent").orEmpty()
  val newContent = details.rawStringOrNull("newContent").orEmpty()
  val hasNewContent = details.has("newContent") && !details.isNull("newContent")
  if (unifiedDiff.isBlank() && (!hasNewContent || oldContent == newContent)) return null
  val filePath = details.cleanString("filePath")
    ?: details.cleanString("file_path")
    ?: args?.cleanString("path")
    ?: args?.cleanString("file_path")
    ?: filePathFromUnifiedDiff(unifiedDiff)
    ?: return null
  val fileName = details.cleanString("fileName")
    ?: filePath.substringAfterLast('/').substringAfterLast('\\').takeIf { it.isNotBlank() }
    ?: filePath
  return HanakoFileDiff(
    fileName = fileName,
    filePath = filePath,
    oldContent = nbgTrimFileDiffText(oldContent),
    newContent = nbgTrimFileDiffText(newContent),
    unifiedDiff = nbgTrimFileDiffText(unifiedDiff),
  )
}

internal fun nbgTrimFileDiffText(text: String, limit: Int = HANA_FILE_DIFF_TEXT_LIMIT): String {
  if (text.length <= limit) return text
  val omitted = text.length - limit
  val marker = "\n\n... Android diff preview truncated $omitted chars ...\n\n"
  val bodyLimit = limit - marker.length
  if (bodyLimit <= 0) return text.take(limit)
  val headLength = bodyLimit / 2
  val tailLength = bodyLimit - headLength
  return buildString(limit) {
    append(text.take(headLength).trimEnd())
    append(marker)
    append(text.takeLast(tailLength).trimStart())
  }
}

internal fun filePathFromUnifiedDiff(diff: String): String? {
  if (diff.isBlank()) return null
  return diff.lineSequence()
    .firstNotNullOfOrNull { line ->
      when {
        line.startsWith("+++ b/") -> line.removePrefix("+++ b/").trim().takeIf { it.isNotBlank() && it != "/dev/null" }
        line.startsWith("--- a/") -> line.removePrefix("--- a/").trim().takeIf { it.isNotBlank() && it != "/dev/null" }
        line.startsWith("+++ ") -> line.removePrefix("+++ ").trim().takeIf { it.isNotBlank() && it != "/dev/null" }
        line.startsWith("--- ") -> line.removePrefix("--- ").trim().takeIf { it.isNotBlank() && it != "/dev/null" }
        else -> null
      }
    }
}

internal fun parseFilePreview(msg: JSONObject): HanakoFilePreview? {
  if (msg.optString("type") != "file_write_prepare" && !msg.has("previewText") && !msg.has("previewChunk")) return null
  val filePath = msg.cleanString("filePath")
    ?: msg.cleanString("path")
    ?: msg.cleanString("rawPath")
    ?: msg.optJSONObject("args")?.cleanString("path")
    ?: msg.optJSONObject("args")?.cleanString("file_path")
    ?: return null
  val hasChunk = msg.has("previewChunk")
  val preview = msg.rawStringAnyOrNull("previewText", "previewChunk").orEmpty()
  val truncated = msg.optBoolean("previewTruncated", false) || preview.length > HANA_FILE_WRITE_PREVIEW_LIMIT
  val fileName = msg.cleanString("fileName")
    ?: filePath.substringAfterLast('/').substringAfterLast('\\').takeIf { it.isNotBlank() }
    ?: filePath
  return HanakoFilePreview(
    fileName = fileName,
    filePath = filePath,
    previewText = preview.take(HANA_FILE_WRITE_PREVIEW_LIMIT),
    truncated = truncated,
    append = hasChunk && !msg.optBoolean("previewReset", false),
    reset = msg.optBoolean("previewReset", false),
  )
}

internal fun parseFilePreviewFromArgs(name: String, args: JSONObject?): HanakoFilePreview? {
  if (name !in HANA_FILE_TOUCH_TOOLS || args == null) return null
  val filePath = args.cleanString("path")
    ?: args.cleanString("file_path")
    ?: args.cleanString("filePath")
    ?: return null
  val preview = args.rawStringAnyOrNull("content", "newContent", "replacement")
    ?: return null
  val fileName = filePath.substringAfterLast('/').substringAfterLast('\\').takeIf { it.isNotBlank() } ?: filePath
  return HanakoFilePreview(
    fileName = fileName,
    filePath = filePath,
    previewText = preview.take(HANA_FILE_WRITE_PREVIEW_LIMIT),
    truncated = preview.length > HANA_FILE_WRITE_PREVIEW_LIMIT,
    append = false,
    reset = true,
  )
}

internal fun parseTerminalOutput(name: String?, details: JSONObject, args: JSONObject? = null): HanakoTerminalOutput? {
  val isTerminalTool = name.isTerminalLikeTool()
  if (!isTerminalTool && !details.hasAnyOutputField()) return null
  val command = args.extractToolArgumentSummary()
  val rawOutput = details.extractOutputTextOrNull()
  val output = nbgNormalizeTerminalOutput(rawOutput ?: if (isTerminalTool && !command.isNullOrBlank()) "$ $command" else "")
  if (output.isEmpty() && !isTerminalTool) return null
  val sessionId = details.cleanString("id")
    ?: details.cleanString("sessionId")
    ?: details.cleanString("termId")
    ?: details.cleanString("toolCallId")
    ?: args?.cleanString("id")
    ?: args?.cleanString("sessionId")
    ?: args?.cleanString("termId")
    ?: if (output.isNotEmpty()) {
      listOfNotNull(name ?: "terminal", command ?: output.stableTerminalOutputKeyPart())
        .joinToString(":")
        .take(160)
        .ifBlank { "terminal" }
    } else {
      return null
    }
  val truncated = details.optBoolean("outputTruncated", false) || output.length > HANA_TOOL_OUTPUT_PREVIEW_LIMIT
  val title = details.cleanString("title") ?: toolDisplayName(name ?: "terminal")
  val exitCode = if (details.has("exitCode") && !details.isNull("exitCode")) details.optInt("exitCode") else null
  val fromCandidates = buildList {
    if (name == "terminal_create" && details.has("cursor")) add(details.optInt("cursor"))
    if (name == "terminal_write" && details.has("cursorBefore")) add(details.optInt("cursorBefore"))
    if (name == "terminal_wait" && details.has("sinceCursor")) add(details.optInt("sinceCursor"))
  }
  val toCandidates = buildList {
    if (name == "terminal_write" && details.has("cursor")) add(details.optInt("cursor"))
    if (name == "terminal_wait" && details.has("cursor")) add(details.optInt("cursor"))
    if (name == "terminal_read" && details.has("cursor")) add(details.optInt("cursor"))
  }
  return HanakoTerminalOutput(
    sessionId = sessionId,
    title = title,
    cwd = details.cleanString("cwd").orEmpty(),
    output = output.take(HANA_TOOL_OUTPUT_PREVIEW_LIMIT),
    alive = if (details.has("alive") && !details.isNull("alive")) details.optBoolean("alive") else null,
    exitCode = exitCode,
    truncated = truncated,
    outputPriority = HANA_TERMINAL_OUTPUT_PRIORITY[name].orZero(),
    sliceFrom = fromCandidates.minOrNull(),
    sliceTo = toCandidates.maxOrNull(),
  )
}

internal fun String?.isTerminalLikeTool(): Boolean =
  this?.startsWith("terminal_") == true || this in setOf("bash", "shell", "exec", "run_command", "command")

internal fun String.stableTerminalOutputKeyPart(): String =
  lineSequence()
    .map { it.trim() }
    .firstOrNull { it.isNotBlank() }
    ?.replace(Regex("\\s+"), " ")
    ?.take(120)
    ?: take(120)

internal fun JSONObject.extractOutputText(): String {
  return extractOutputTextOrNull().orEmpty()
}

internal fun JSONObject.extractOutputTextOrNull(): String? {
  rawStringOrNull("output")?.let { return it }
  rawStringOrNull("outputText")?.let { return it }
  rawStringOrNull("result")?.let { return it }
  rawStringOrNull("logs")?.let { return it }
  val streams = listOfNotNull(rawStringOrNull("stdout"), rawStringOrNull("stderr"))
    .joinToString("\n")
  if (streams.isNotEmpty()) return streams
  return rawStringAnyOrNull("content", "text")
}

internal fun JSONObject?.extractToolArgumentSummary(): String? {
  if (this == null) return null
  return cleanStringAny(
    "command",
    "cmd",
    "script",
    "url",
    "query",
    "pattern",
    "path",
    "file_path",
    "filePath",
  )?.replace(Regex("\\s+"), " ")?.take(220)
}

internal fun parseHanakoToolGroup(block: JSONObject): List<HanakoToolStatus> {
  val tools = block.optJSONArray("tools") ?: return emptyList()
  return parseHanakoToolArray(tools)
}

internal fun parseHanakoToolArray(tools: JSONArray): List<HanakoToolStatus> {
  return buildList {
    for (index in 0 until tools.length()) {
      parseHanakoHistoryTool(tools.optJSONObject(index))?.let { add(it) }
    }
  }
}

internal fun parseHanakoHistoryTool(tool: JSONObject?): HanakoToolStatus? {
  if (tool == null) return null
  val name = tool.cleanString("name") ?: return null
  val details = tool.optJSONObject("details")
  val args = tool.optJSONObject("args")
  val done = tool.optBoolean("done", false)
  val success = tool.optBoolean("success", false)
  val synthetic = JSONObject()
    .put("name", name)
    .put("success", success)
  details?.let { synthetic.put("details", it) }
  args?.let { synthetic.put("args", it) }
  val fileDiff = parseFileDiff(name, synthetic)
  val filePreview = details?.let { parseFilePreview(it) } ?: parseFilePreviewFromArgs(name, args)
  val terminalOutput = parseTerminalOutput(name, details ?: synthetic, args)
  val kind = when {
    fileDiff != null || filePreview != null -> "file"
    terminalOutput != null || name.isTerminalLikeTool() -> "terminal"
    else -> "tool"
  }
  val detail = if (fileDiff == null && filePreview == null && terminalOutput == null) {
    summarizeToolPayload(args, details)
  } else {
    ""
  }
  return HanakoToolStatus(
    key = listOfNotNull("history", name, details?.cleanString("id"), args?.cleanString("path") ?: args?.cleanString("file_path")).joinToString(":"),
    kind = kind,
    toolName = name,
    filePath = fileDiff?.filePath ?: filePreview?.filePath.orEmpty(),
    title = fileDiff?.fileName?.let { "文件：$it" }
      ?: filePreview?.fileName?.let { "文件：$it" }
      ?: terminalOutput?.title?.ifBlank { null }
      ?: toolDisplayName(name),
    subtitle = if (done) "历史记录" else "历史记录 / 进行中",
    detail = detail,
    status = if (done) "done" else "running",
    running = !done,
    success = if (done) success else null,
    filePreview = filePreview,
    fileDiff = fileDiff,
    terminalOutput = terminalOutput,
  )
}

internal fun summarizeToolPayload(args: JSONObject?, details: JSONObject?): String =
  listOfNotNull(
    args?.takeIf { it.length() > 0 }?.let { "参数：${it.toString().take(360)}" },
    details?.takeIf { it.length() > 0 }?.let { "结果：${it.toString().take(520)}" },
  ).joinToString("\n")

internal fun toolDisplayName(name: String): String =
  when (name) {
    "terminal_create" -> "打开终端"
    "terminal_write" -> "执行命令"
    "terminal_wait" -> "等待命令"
    "terminal_read" -> "读取终端"
    "terminal_interrupt" -> "打断终端"
    "terminal_kill" -> "关闭终端"
    "write" -> "写入文件"
    "edit", "edit-diff" -> "修改文件"
    "read" -> "读取文件"
    "bash" -> "运行命令"
    "current_status" -> "当前状态"
    else -> name.replace('_', ' ')
  }

internal fun String.toArchivedDeletePath(): String {
  val source = File(this)
  val parent = source.parentFile ?: return this
  return File(File(parent, "archived"), source.name).path
}

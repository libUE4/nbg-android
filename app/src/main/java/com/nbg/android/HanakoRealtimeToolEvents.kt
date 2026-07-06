package com.nbg.android

import org.json.JSONObject

internal fun formatContextUsage(msg: JSONObject): String? {
  if (msg.isNull("percent") && msg.isNull("tokens") && msg.isNull("contextWindow")) return null
  val percent = msg.optDouble("percent", Double.NaN)
  if (!percent.isNaN()) {
    val rounded = percent.coerceIn(0.0, 999.0).toInt()
    return "ctx ${rounded}%"
  }
  val tokens = msg.optLong("tokens", -1L)
  val window = msg.optLong("contextWindow", -1L)
  return when {
    tokens >= 0L && window > 0L -> "ctx ${tokens / 1000}k/${window / 1000}k"
    tokens >= 0L -> "ctx ${tokens / 1000}k"
    else -> null
  }
}

internal fun parseToolStatus(msg: JSONObject): HanakoToolStatus? {
  val type = msg.optString("type").ifBlank { return null }
  val payload = msg.toolEventPayload()
  val name = msg.cleanStringAny("name", "toolName", "tool", "phase")
    ?: payload.cleanStringAny("name", "toolName", "tool", "phase")
    ?: type.takeIf { it.startsWith("terminal_") || it.contains("command", ignoreCase = true) }
  val stage = msg.cleanString("stage") ?: msg.cleanString("status")
    ?: payload.cleanString("stage")
    ?: payload.cleanString("status")
  val filePath = msg.cleanString("filePath") ?: msg.cleanString("path") ?: msg.cleanString("rawPath")
    ?: payload.cleanString("filePath") ?: payload.cleanString("path") ?: payload.cleanString("rawPath")
  val fileName = msg.cleanString("fileName") ?: filePath?.substringAfterLast('/')
    ?: payload.cleanString("fileName")
  val filePreview = parseFilePreview(msg)
  val effectiveFilePath = filePreview?.filePath ?: filePath.orEmpty()
  val args = msg.objectAny("args", "input", "arguments")
    ?: payload.objectAny("args", "input", "arguments")
    ?: payload.takeIf { it != msg && it.length() > 0 }
  val details = msg.objectAny("details", "result", "data")
    ?: payload.objectAny("details", "result", "data")
    ?: msg
  val terminalOutput = parseTerminalOutput(name, details, args)
  val hasMeaningfulTerminalProgress = terminalOutput != null ||
    type.contains("terminal", ignoreCase = true) ||
    details.hasAnyOutputField() ||
    !args.extractToolArgumentSummary().isNullOrBlank() ||
    !stage.isNullOrBlank()
  if (name.isTerminalLikeTool() && !hasMeaningfulTerminalProgress) return null
  val kind = when {
    filePreview != null || type.contains("file", ignoreCase = true) || effectiveFilePath.isNotBlank() || !fileName.isNullOrBlank() -> "file"
    terminalOutput != null || name.isTerminalLikeTool() || type.contains("terminal", ignoreCase = true) -> "terminal"
    type.contains("vision", ignoreCase = true) -> "vision"
    else -> "tool"
  }
  val title = when (kind) {
    "file" -> (filePreview?.fileName ?: fileName)?.let { "文件：$it" } ?: "文件处理"
    "terminal" -> "终端"
    "vision" -> "视觉处理"
    else -> toolDisplayName(name ?: type)
  }
  val subtitle = listOfNotNull(
    eventDisplayName(type),
    stage?.let { statusDisplayName(it) },
  ).joinToString(" / ")
  val detail = listOfNotNull(
    filePath,
    msg.cleanStringAny("output", "stdout", "stderr", "message", "summary", "error", "warning"),
    payload.cleanStringAny("output", "stdout", "stderr", "message", "summary", "error", "warning"),
    args.extractToolArgumentSummary(),
  ).joinToString("\n")
  return HanakoToolStatus(
    key = msg.cleanString("toolCallId")
      ?: msg.cleanString("prepareKey")
      ?: msg.cleanString("id")
      ?: listOfNotNull(type, name, effectiveFilePath).joinToString(":"),
    kind = kind,
    toolName = name.orEmpty(),
    filePath = effectiveFilePath,
    title = title,
    subtitle = subtitle,
    detail = detail,
    status = stage.orEmpty(),
    running = type.endsWith("_progress") || type.endsWith("_status") || type == "file_write_prepare" || terminalOutput?.alive == true,
    filePreview = filePreview,
    terminalOutput = terminalOutput,
  )
}

internal fun JSONObject.looksLikeToolEvent(type: String): Boolean {
  val normalizedType = type.lowercase()
  if (normalizedType.contains("tool") || normalizedType.contains("terminal")) return true
  if (cleanStringAny("name", "toolName", "tool") == null) return false
  if (hasAnyOutputField()) return true
  return listOf("details", "payload", "result", "args", "input", "arguments")
    .any { optJSONObject(it) != null }
}

internal fun parseToolStart(msg: JSONObject): HanakoToolStatus {
  val payload = msg.toolEventPayload()
  val name = msg.cleanStringAny("name", "toolName", "tool")
    ?: payload.cleanStringAny("name", "toolName", "tool")
    ?: "tool"
  val args = msg.objectAny("args", "input", "arguments")
    ?: payload.objectAny("args", "input", "arguments")
    ?: payload.takeIf { it != msg && it.length() > 0 }
  val filePath = msg.cleanString("filePath")
    ?: msg.cleanString("path")
    ?: msg.cleanString("rawPath")
    ?: args?.cleanString("path")
    ?: args?.cleanString("file_path")
  val fileName = msg.cleanString("fileName")
    ?: filePath?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
  val kind = when {
    name.isTerminalLikeTool() -> "terminal"
    name in HANA_FILE_TOUCH_TOOLS && !filePath.isNullOrBlank() -> "file"
    else -> "tool"
  }
  val filePreview = parseFilePreview(msg) ?: parseFilePreviewFromArgs(name, args)
  val startDetails = msg.objectAny("details", "result", "data")
    ?: payload.objectAny("details", "result", "data")
    ?: msg
  val terminalOutput = parseTerminalOutput(name, startDetails, args)
  val detail = listOfNotNull(
    filePath,
    msg.cleanString("summary"),
    msg.cleanString("message"),
    payload.cleanStringAny("summary", "message"),
    args.extractToolArgumentSummary(),
  ).joinToString("\n")
  return HanakoToolStatus(
    key = msg.cleanString("toolCallId") ?: msg.cleanString("prepareKey") ?: msg.cleanString("id") ?: listOfNotNull(name, filePath).joinToString(":"),
    kind = kind,
    toolName = name,
    filePath = filePath.orEmpty(),
    title = if (kind == "file") fileName?.let { "文件：$it" } ?: toolDisplayName(name) else toolDisplayName(name),
    subtitle = "开始",
    detail = detail,
    status = "running",
    running = true,
    filePreview = filePreview,
    terminalOutput = terminalOutput,
  )
}

internal fun parseToolEnd(msg: JSONObject): HanakoToolStatus {
  val payload = msg.toolEventPayload()
  val name = msg.cleanStringAny("name", "toolName", "tool")
    ?: payload.cleanStringAny("name", "toolName", "tool")
    ?: "tool"
  val success = when {
    msg.has("success") && !msg.isNull("success") -> msg.optBoolean("success", true)
    msg.has("isError") && !msg.isNull("isError") -> !msg.optBoolean("isError", false)
    else -> true
  }
  val details = msg.objectAny("details", "result", "data")
    ?: payload.objectAny("details", "result", "data")
  val args = msg.objectAny("args", "input", "arguments")
    ?: payload.objectAny("args", "input", "arguments")
    ?: payload.takeIf { it != msg && it.length() > 0 }
  val fileDiff = parseFileDiff(name, msg)
  val filePreview = details?.let { parseFilePreview(it) }
    ?: parseFilePreview(msg)
    ?: parseFilePreviewFromArgs(name, args)
  val filePath = fileDiff?.filePath
    ?: filePreview?.filePath
    ?: msg.cleanString("filePath")
    ?: msg.cleanString("path")
    ?: msg.cleanString("rawPath")
    ?: args?.cleanString("path")
    ?: args?.cleanString("file_path")
  val terminalOutput = parseTerminalOutput(name, details ?: msg, args)
  val kind = when {
    fileDiff != null -> "file"
    terminalOutput != null -> "terminal"
    name.isTerminalLikeTool() -> "terminal"
    else -> "tool"
  }
  return HanakoToolStatus(
    key = msg.cleanString("toolCallId") ?: msg.cleanString("id") ?: name,
    kind = kind,
    toolName = name,
    filePath = filePath.orEmpty(),
    title = fileDiff?.fileName?.let { "文件：$it" } ?: terminalOutput?.title?.ifBlank { null } ?: toolDisplayName(name),
    subtitle = if (success) "完成" else "失败",
    detail = listOfNotNull(
      msg.cleanStringAny("output", "stdout", "stderr", "error", "summary", "message"),
      payload.cleanStringAny("output", "stdout", "stderr", "error", "summary", "message"),
      args.extractToolArgumentSummary(),
    ).joinToString("\n"),
    status = if (success) "done" else "failed",
    running = false,
    success = success,
    filePreview = filePreview,
    fileDiff = fileDiff,
    terminalOutput = terminalOutput,
  )
}

internal fun JSONObject.toolEventPayload(): JSONObject {
  optJSONObject("toolCall")?.let { return it }
  optJSONObject("assistantMessageEvent")?.optJSONObject("toolCall")?.let { return it }
  val contentIndex = if (has("contentIndex") && !isNull("contentIndex")) optInt("contentIndex", -1) else -1
  optJSONObject("partial")
    ?.optJSONArray("content")
    ?.optJSONObject(contentIndex)
    ?.let { return it }
  objectAny("payload", "data")?.let { payload ->
    val hasToolShape = payload.cleanStringAny("name", "toolName", "tool") != null ||
      payload.objectAny("args", "input", "arguments", "details", "result") != null ||
      payload.hasAnyOutputField()
    if (hasToolShape) return payload
  }
  return this
}

internal fun compactUrl(url: String): String =
  url.removePrefix("https://")
    .removePrefix("http://")
    .take(72)

internal fun formatTokenCount(tokens: Long): String =
  if (tokens >= 1000L) "${tokens / 1000}k" else tokens.toString()

internal fun summarizeBridgeStatus(msg: JSONObject): String? {
  val platform = msg.cleanString("platform") ?: msg.cleanString("agentId") ?: return null
  val rawStatus = msg.cleanString("status") ?: return null
  val status = nbgBridgeStatusLabel(rawStatus) ?: return null
  val error = msg.cleanString("error")?.take(80)
  return listOfNotNull("桥接 $platform $status", error).joinToString(" / ")
}

internal fun parseDeferredResult(msg: JSONObject): HanakoToolStatus? {
  val taskId = msg.cleanString("taskId") ?: return null
  val normalizedStatus = nbgNormalizeHanakoStatus(msg.cleanString("status"))
  val status = nbgDeferredStatusLabel(msg.cleanString("status").orEmpty()) ?: return null
  return HanakoToolStatus(
    key = "deferred:$taskId",
    kind = "tool",
    title = "后台任务",
    subtitle = status,
    detail = listOfNotNull(taskId, msg.cleanString("reason")?.take(120)).joinToString("\n"),
    status = normalizedStatus,
    running = false,
    success = normalizedStatus == "success",
  )
}

internal fun eventDisplayName(type: String): String =
  when (type) {
    "tool_progress" -> "工具调用"
    "file_write_prepare" -> "准备写入"
    "vision_progress" -> "视觉处理"
    else -> type.replace('_', ' ')
  }

internal fun statusDisplayName(status: String): String =
  when (status) {
    "writing" -> "写入中"
    "applying" -> "应用中"
    "done", "success" -> "完成"
    "failed", "error" -> "失败"
    "running" -> "运行中"
    else -> status
  }

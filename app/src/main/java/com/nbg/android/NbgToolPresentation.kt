package com.nbg.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.FileEdit
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tools

internal fun HanakoTerminalOutput.sliceLabel(): String? =
  when {
    sliceFrom != null && sliceTo != null -> "cursor $sliceFrom-$sliceTo"
    sliceFrom != null -> "from $sliceFrom"
    sliceTo != null -> "to $sliceTo"
    else -> null
  }

internal fun nbgTerminalOutputMeta(terminal: HanakoTerminalOutput, includeSlice: Boolean = true): String =
  nbgTerminalOutputMetaParts(terminal, includeSlice).joinToString(" / ")

internal fun nbgTerminalOutputMetaParts(
  terminal: HanakoTerminalOutput,
  includeSlice: Boolean = true,
): List<String> =
  listOfNotNull(
    terminal.cwd.takeIf { it.isNotBlank() },
    terminal.sliceLabel().takeIf { includeSlice },
    terminal.nbgTerminalExitDetail()?.label,
    terminal.alive?.let { if (it) "running" else "stopped" },
  )

internal fun nbgDiffStats(diff: HanakoFileDiff): Pair<Int, Int> {
  if (diff.unifiedDiff.isNotBlank()) {
    return nbgUnifiedDiffStats(diff.unifiedDiff)
  }
  return nbgContentDiffStats(diff.oldContent, diff.newContent)
}

internal fun nbgContentDiffStats(oldContent: String, newContent: String): Pair<Int, Int> {
  val oldLines = oldContent.lines()
  val newLines = newContent.lines()
  val max = maxOf(oldLines.size, newLines.size)
  var additions = 0
  var deletions = 0
  for (index in 0 until max) {
    val oldLine = oldLines.getOrNull(index)
    val newLine = newLines.getOrNull(index)
    if (oldLine == newLine) continue
    if (newLine != null) additions++
    if (oldLine != null) deletions++
  }
  return additions to deletions
}

internal fun nbgUnifiedDiffStats(unifiedDiff: String): Pair<Int, Int> {
  var additions = 0
  var deletions = 0
  unifiedDiff.lineSequence().forEach { line ->
    when {
      line.startsWith("+") && !line.startsWith("+++") -> additions++
      line.startsWith("-") && !line.startsWith("---") -> deletions++
    }
  }
  return additions to deletions
}

internal fun nbgDiffStatsFromShownLines(lines: List<String>): Pair<Int, Int> {
  var additions = 0
  var deletions = 0
  lines.forEach { line ->
    when {
      line.startsWith("+") && !line.startsWith("+++") -> additions++
      line.startsWith("-") && !line.startsWith("---") -> deletions++
    }
  }
  return additions to deletions
}

internal fun nbgDiffPreviewLines(diff: HanakoFileDiff, limit: Int = 8): List<String> {
  if (diff.unifiedDiff.isNotBlank()) {
    return nbgUnifiedDiffPreviewLines(diff.unifiedDiff, limit)
  }
  return nbgContentDiffPreviewLines(diff.oldContent, diff.newContent, limit)
}

internal fun nbgFullDiffLines(diff: HanakoFileDiff): List<String> {
  if (diff.unifiedDiff.isNotBlank()) return diff.unifiedDiff.lines()
  return nbgContentDiffLines(diff.oldContent, diff.newContent)
}

internal fun nbgUnifiedDiffPreviewLines(unifiedDiff: String, limit: Int): List<String> =
  unifiedDiff.lineSequence()
    .filter { line ->
      (line.startsWith("+") && !line.startsWith("+++")) ||
        (line.startsWith("-") && !line.startsWith("---"))
    }
    .map { it.take(98) }
    .take(limit)
    .toList()

internal fun nbgContentDiffPreviewLines(oldContent: String, newContent: String, limit: Int): List<String> {
  val oldLines = oldContent.lines()
  val newLines = newContent.lines()
  val max = maxOf(oldLines.size, newLines.size)
  val preview = mutableListOf<String>()
  for (index in 0 until max) {
    val oldLine = oldLines.getOrNull(index)
    val newLine = newLines.getOrNull(index)
    if (oldLine == newLine) continue
    oldLine?.let { preview += "- ${it.take(96)}" }
    newLine?.let { preview += "+ ${it.take(96)}" }
    if (preview.size >= limit) break
  }
  return preview.take(limit)
}

internal fun nbgContentDiffLines(oldContent: String, newContent: String): List<String> {
  val oldLines = oldContent.lines()
  val newLines = newContent.lines()
  val max = maxOf(oldLines.size, newLines.size)
  val lines = mutableListOf<String>()
  for (index in 0 until max) {
    val oldLine = oldLines.getOrNull(index)
    val newLine = newLines.getOrNull(index)
    if (oldLine == newLine) {
      oldLine?.let { lines += "  $it" }
      continue
    }
    oldLine?.let { lines += "- $it" }
    newLine?.let { lines += "+ $it" }
  }
  return lines
}

internal fun nbgToolKindLabel(kind: String): String =
  when (kind) {
    "file" -> "文件变更"
    "terminal" -> "终端"
    "confirmation" -> "确认"
    "todo" -> "任务"
    "team_task" -> "团队任务"
    "team_agent" -> "Agent 状态"
    "vision" -> "视觉"
    else -> "工具调用"
  }

internal fun nbgToolPreviewSummary(tool: HanakoToolStatus): String =
  buildList {
    if (tool.fileDiff == null) tool.filePreview?.let { add("文件预览") }
    tool.fileDiff?.let { add("Diff") }
    tool.terminalOutput?.let { terminal ->
      add(nbgTerminalOutputSummary(terminal))
    }
    if (tool.kind == "terminal" && tool.terminalOutput == null) {
      nbgTerminalToolFallbackSummary(tool)?.let { add(it) }
    }
  }.joinToString(" / ").ifBlank { nbgToolStatusLabel(tool).ifBlank { "等待详情" } }

internal fun nbgTerminalOutputSummary(terminal: HanakoTerminalOutput): String {
  val exitDetail = terminal.nbgTerminalExitDetail()
  val lastLine = terminal.output
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .lastOrNull()
    ?.replace(Regex("\\s+"), " ")
    ?.take(96)
  return when {
    lastLine != null -> lastLine
    exitDetail != null -> exitDetail.label
    terminal.alive == true -> "终端运行中，等待输出"
    else -> "终端暂无输出"
  }
}

internal fun nbgTerminalToolFallbackSummary(tool: HanakoToolStatus): String? =
  nbgTerminalToolFallbackText(tool)
    .lineSequence()
    .map { it.trim() }
    .firstOrNull { it.isNotBlank() }
    ?.replace(Regex("\\s+"), " ")
    ?.take(96)

internal fun nbgTerminalToolFallbackText(tool: HanakoToolStatus): String =
  buildList {
    tool.detail.takeIf { it.isNotBlank() }?.let { add(it) }
    tool.filePath.takeIf { it.isNotBlank() }?.let { add(it) }
    tool.subtitle.takeIf { it.isNotBlank() && it != "开始" && it != "完成" }?.let { add(it) }
    tool.status.takeIf { it.isNotBlank() && it !in setOf("running", "done", "failed") }?.let { add(it) }
    tool.toolName.takeIf { it.isNotBlank() && it != "tool" }?.let { add(toolDisplayName(it)) }
  }
    .flatMap { raw -> raw.lineSequence().map { it.trim() }.toList() }
    .filter { line ->
      line.isNotBlank() &&
        line != "历史记录" &&
        !line.startsWith("历史记录 /") &&
        !line.equals("开始", ignoreCase = true) &&
        !line.equals("完成", ignoreCase = true) &&
        !line.equals("running", ignoreCase = true) &&
        !line.equals("done", ignoreCase = true)
    }
    .distinct()
    .take(12)
    .joinToString("\n")
    .take(HANA_TOOL_OUTPUT_PREVIEW_LIMIT)

internal fun nbgToolStatusLabel(tool: HanakoToolStatus): String =
  when (val state = tool.nbgToolVisualizationState()) {
    NbgToolVisualizationState.Waiting -> tool.status.takeIf { it.isNotBlank() }.orEmpty()
    else -> state.label
  }

internal fun HanakoToolStatus.hasVisibleToolStatus(): Boolean {
  if (taskCompletionEvidence != null) return true
  if (hasInlinePreview) return true
  if (filePath.isNotBlank()) return true
  if (kind == "terminal" || toolName.isTerminalLikeTool()) return true
  if (kind == "file" || kind == "vision") return true
  if (title.isNotBlank() && title != "工具调用") return true
  if (nbgCompactToolDetail(subtitle).isNotBlank()) return true
  if (nbgCompactToolDetail(detail).isNotBlank()) return true
  return running || success != null || status.isNotBlank()
}

internal fun nbgCompactToolDetail(detail: String): String =
  detail
    .lineSequence()
    .map { it.trim() }
    .filter { line ->
      line.isNotBlank() &&
        line != "历史记录" &&
        !line.startsWith("历史记录 /") &&
        !line.startsWith("参数：") &&
        !line.startsWith("参数:") &&
        !line.startsWith("{") &&
        !line.startsWith("[") &&
        !line.startsWith("\"") &&
        !line.startsWith("/") &&
        !line.contains("\":{") &&
        !line.contains("\": {")
    }
    .take(2)
    .joinToString(" / ")
    .replace(Regex("\\s+"), " ")
    .take(140)

internal fun nbgToolStatusIcon(tool: HanakoToolStatus): ImageVector =
  when (tool.kind) {
    "file" -> HugeIcons.FileEdit
    "terminal" -> HugeIcons.ComputerTerminal01
    "vision" -> HugeIcons.Search01
    else -> if (tool.success == false) HugeIcons.Cancel01 else HugeIcons.Tools
  }

internal fun nbgToolStatusDotColor(tool: HanakoToolStatus): Color =
  when (tool.nbgToolVisualizationState()) {
    NbgToolVisualizationState.Failed,
    NbgToolVisualizationState.Blocked,
    NbgToolVisualizationState.Cancelled,
    NbgToolVisualizationState.RestoredIncomplete -> NbgAgentColors.StatusRed
    NbgToolVisualizationState.Running,
    NbgToolVisualizationState.Waiting -> NbgAgentColors.StatusYellow
    NbgToolVisualizationState.Succeeded -> NbgAgentColors.StatusGreen
  }

internal fun nbgRunStatusDotColor(status: NbgChatRunStatus): Color =
  when {
    status.warning -> NbgAgentColors.StatusRed
    status.active -> NbgAgentColors.StatusYellow
    else -> NbgAgentColors.StatusGreen
  }

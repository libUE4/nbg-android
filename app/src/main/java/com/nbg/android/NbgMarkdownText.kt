package com.nbg.android

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal enum class NbgPreparedTextKind {
  Markdown,
}

internal data class NbgPreparedText(
  val source: String,
  val displayText: String,
  val annotatedText: AnnotatedString? = null,
  val blocks: List<NbgPreparedTextBlock> = emptyList(),
  val kind: NbgPreparedTextKind,
)

internal sealed class NbgPreparedTextBlock {
  data class TextBlock(val text: AnnotatedString) : NbgPreparedTextBlock()
  data class HeadingBlock(val level: Int, val text: AnnotatedString) : NbgPreparedTextBlock()
  data class QuoteBlock(val text: AnnotatedString) : NbgPreparedTextBlock()
  data class ListBlock(val lines: List<AnnotatedString>) : NbgPreparedTextBlock()
  data class TableBlock(val headers: List<AnnotatedString>, val rows: List<List<AnnotatedString>>) : NbgPreparedTextBlock()
  data class CodeBlock(val text: String) : NbgPreparedTextBlock()
  data object DividerBlock : NbgPreparedTextBlock()
}

internal class NbgPretextCache(
  private val maxEntries: Int = 640,
) {
  private val entries = linkedMapOf<String, NbgPreparedText>()

  fun prepareText(key: String, text: String, streaming: Boolean): NbgPreparedText {
    val kind = NbgPreparedTextKind.Markdown
    val cacheKeyPrefix = "$key|$kind|"
    val cacheKey = "$cacheKeyPrefix${text.length}|${text.hashCode()}"
    entries[cacheKey]?.let { cached ->
      if (cached.source == text) return cached
    }
    val prepared = nbgPrepareText(text, streaming)
    if (streaming) {
      entries.keys.removeAll { it.startsWith(cacheKeyPrefix) }
    }
    entries[cacheKey] = prepared
    trim()
    return prepared
  }

  internal fun entryCountForTest(): Int = entries.size

  private fun trim() {
    while (entries.size > maxEntries) {
      val eldestKey = entries.keys.firstOrNull() ?: return
      entries.remove(eldestKey)
    }
  }
}

@Suppress("UNUSED_PARAMETER")
internal fun nbgPrepareText(text: String, streaming: Boolean): NbgPreparedText {
  val kind = NbgPreparedTextKind.Markdown
  val displayText = stripNbgInternalText(text)
  val annotatedText = parseNbgInlineMarkdown(displayText)
  return NbgPreparedText(
    source = text,
    displayText = displayText,
    annotatedText = annotatedText,
    blocks = if (nbgTextNeedsMarkdownBlocks(displayText)) {
      nbgPreparedTextBlocks(displayText)
    } else {
      emptyList()
    },
    kind = kind,
  )
}

internal fun nbgMessageText(text: String): AnnotatedString =
  parseNbgInlineMarkdown(stripNbgInternalText(text))

internal fun stripNbgInternalMood(text: String): String {
  return stripNbgInternalText(text)
}

internal fun stripNbgInternalText(text: String): String {
  if (text.isBlank()) return text
  return nbgStripInternalAssistantBlocks(text).text.trimStart('\n', ' ', '\t')
}

internal fun nbgTextNeedsMarkdownBlocks(text: String): Boolean {
  if (text.isBlank()) return false
  if (!text.any { it == '\n' || it == '#' || it == '|' || it == '>' || it == '`' || it == '-' || it == '*' || it == '_' || it == '•' }) {
    return false
  }
  text.lineSequence().forEach { line ->
    val trimmed = line.trim()
    val leadingTrimmed = line.trimStart()
    if (
      leadingTrimmed.startsWith("```") ||
      leadingTrimmed.startsWith(">") ||
      isNbgMarkdownHeadingLine(leadingTrimmed) ||
      isNbgMarkdownTableRow(trimmed) ||
      isNbgMarkdownTableDivider(trimmed) ||
      isNbgAsciiDiagramLine(line) ||
      NBG_MARKDOWN_DIVIDER_REGEX.matches(trimmed) ||
      NBG_MARKDOWN_LIST_REGEX.matches(leadingTrimmed)
    ) {
      return true
    }
  }
  return false
}

internal fun nbgPreparedTextBlocks(text: String): List<NbgPreparedTextBlock> {
  val blocks = mutableListOf<NbgPreparedTextBlock>()
  val plain = StringBuilder()
  val code = StringBuilder()
  val listLines = mutableListOf<AnnotatedString>()
  var inCode = false

  fun flushPlain() {
    val plainText = plain.toString().trimEnd('\n')
    if (plainText.isNotBlank()) {
      blocks += NbgPreparedTextBlock.TextBlock(parseNbgInlineMarkdown(plainText))
    }
    plain.clear()
  }

  fun flushList() {
    if (listLines.isNotEmpty()) {
      blocks += NbgPreparedTextBlock.ListBlock(listLines.toList())
      listLines.clear()
    }
  }

  val lines = text.lines()
  var index = 0
  while (index < lines.size) {
    val line = lines[index]
    val trimmed = line.trim()
    val leadingTrimmed = line.trimStart()
    if (leadingTrimmed.startsWith("```")) {
      if (inCode) {
        val codeText = code.toString().trimEnd('\n')
        if (codeText.isNotBlank()) blocks += NbgPreparedTextBlock.CodeBlock(codeText)
        code.clear()
      } else {
        flushPlain()
        flushList()
      }
      inCode = !inCode
    } else if (
      !inCode &&
      index + 1 < lines.size &&
      isNbgMarkdownTableRow(trimmed) &&
      isNbgMarkdownTableDivider(lines[index + 1].trim())
    ) {
      flushPlain()
      flushList()
      val headers = parseNbgMarkdownTableRow(trimmed)
      val rows = mutableListOf<List<AnnotatedString>>()
      index += 2
      while (
        index < lines.size &&
        isNbgMarkdownTableRow(lines[index].trim()) &&
        !isNbgMarkdownTableDivider(lines[index].trim())
      ) {
        rows += parseNbgMarkdownTableRow(lines[index].trim())
        index += 1
      }
      blocks += NbgPreparedTextBlock.TableBlock(headers, rows)
      continue
    } else if (inCode) {
      code.append(line).append('\n')
    } else if (isNbgMarkdownTableDivider(trimmed)) {
      flushPlain()
      flushList()
    } else if (isNbgAsciiDiagramLine(line)) {
      flushPlain()
      flushList()
      val diagram = StringBuilder()
      while (index < lines.size && (isNbgAsciiDiagramLine(lines[index]) || lines[index].isBlank())) {
        diagram.append(lines[index]).append('\n')
        index += 1
      }
      diagram.toString().trimEnd('\n').takeIf { it.isNotBlank() }?.let {
        blocks += NbgPreparedTextBlock.CodeBlock(it)
      }
      continue
    } else if (NBG_MARKDOWN_DIVIDER_REGEX.matches(trimmed)) {
      flushPlain()
      flushList()
      blocks += NbgPreparedTextBlock.DividerBlock
    } else if (isNbgMarkdownHeadingLine(leadingTrimmed)) {
      val level = leadingTrimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
      val heading = leadingTrimmed.drop(level).trim()
      if (heading.isNotBlank()) {
        flushPlain()
        flushList()
        blocks += NbgPreparedTextBlock.HeadingBlock(level, parseNbgInlineMarkdown(heading))
      } else {
        plain.append(line).append('\n')
      }
    } else if (leadingTrimmed.startsWith(">")) {
      val quote = leadingTrimmed.drop(1).trim()
      if (quote.isNotBlank()) {
        flushPlain()
        flushList()
        blocks += NbgPreparedTextBlock.QuoteBlock(parseNbgInlineMarkdown(quote))
      }
    } else if (NBG_MARKDOWN_LIST_REGEX.matches(leadingTrimmed)) {
      flushPlain()
      val itemText = leadingTrimmed.replaceFirst(NBG_MARKDOWN_LIST_PREFIX_REGEX, "")
      listLines += parseNbgInlineMarkdown(itemText)
    } else if (line.isBlank()) {
      flushPlain()
      flushList()
    } else {
      flushList()
      plain.append(line).append('\n')
    }
    index += 1
  }
  flushList()
  val tail = if (inCode) code else plain
  val tailText = tail.toString().trimEnd('\n')
  if (tailText.isNotBlank()) {
    blocks += if (inCode) {
      NbgPreparedTextBlock.CodeBlock(tailText)
    } else {
      NbgPreparedTextBlock.TextBlock(parseNbgInlineMarkdown(tailText))
    }
  }
  return blocks.ifEmpty { listOf(NbgPreparedTextBlock.TextBlock(parseNbgInlineMarkdown(text))) }
}

internal fun isNbgMarkdownHeadingLine(line: String): Boolean {
  val level = line.takeWhile { it == '#' }.length
  return level in 1..6 && line.getOrNull(level)?.isWhitespace() == true
}

internal fun isNbgMarkdownTableDivider(line: String): Boolean {
  if (!line.contains('|')) return false
  val compact = line.replace(" ", "")
  if (!compact.startsWith("|") || !compact.endsWith("|")) return false
  return compact.all { it == '|' || it == '-' || it == ':' }
}

internal fun isNbgMarkdownTableRow(line: String): Boolean {
  if (!line.contains('|') || isNbgMarkdownTableDivider(line)) return false
  val cells = line.trim().trim('|').split('|')
  return cells.size >= 2 && cells.any { it.trim().isNotEmpty() }
}

internal fun parseNbgMarkdownTableRow(line: String): List<AnnotatedString> =
  line.trim()
    .trim('|')
    .split('|')
    .map { parseNbgInlineMarkdown(it.trim()) }

internal fun isNbgAsciiDiagramLine(line: String): Boolean {
  val trimmed = line.trim()
  if (trimmed.length < 3) return false
  val hasBoxDrawing = trimmed.any { it in "│┃┌┐└┘├┤┬┴┼─━╭╮╰╯" }
  if (hasBoxDrawing) return true
  val pipeCount = trimmed.count { it == '|' }
  val ruleCount = trimmed.count { it == '-' || it == '_' || it == '=' }
  return pipeCount >= 2 || (pipeCount >= 1 && ruleCount >= 3)
}

internal fun parseNbgInlineMarkdown(text: String): AnnotatedString {
  if ('*' !in text && '`' !in text) return AnnotatedString(text)
  return buildAnnotatedString {
    var index = 0
    while (index < text.length) {
      when {
        text.startsWith("**", index) -> {
          val end = text.indexOf("**", startIndex = index + 2)
          if (end > index + 2) {
            pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
            append(text.substring(index + 2, end))
            pop()
            index = end + 2
          } else {
            append(text[index])
            index += 1
          }
        }
        text[index] == '`' -> {
          val end = text.indexOf('`', startIndex = index + 1)
          if (end > index + 1) {
            pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = NbgAgentColors.CodeInline))
            append(text.substring(index + 1, end))
            pop()
            index = end + 1
          } else {
            append(text[index])
            index += 1
          }
        }
        else -> {
          append(text[index])
          index += 1
        }
      }
    }
  }
}

private val NBG_MARKDOWN_DIVIDER_REGEX = Regex("-{3,}|_{3,}|\\*{3,}")
private val NBG_MARKDOWN_LIST_REGEX = Regex("([-*•]|\\d+[.)])\\s+.+")
private val NBG_MARKDOWN_LIST_PREFIX_REGEX = Regex("^([-*•]|\\d+[.)])\\s+")

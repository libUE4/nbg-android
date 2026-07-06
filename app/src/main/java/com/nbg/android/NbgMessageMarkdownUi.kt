package com.nbg.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NbgStreamingMessageText(
  message: NbgAgentMessage,
  modifier: Modifier = Modifier,
  preparedText: NbgPreparedText? = null,
  preparedTextKey: String = message.id.toString(),
  pretextCache: NbgPretextCache? = null,
  animatePreparedBlocks: Boolean = false,
  historyNativeText: Boolean = false,
) {
  val chunks = message.textChunks?.takeIf { it.isNotEmpty() }
  Column(
    modifier = modifier.widthIn(max = 360.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (historyNativeText && !message.streaming) {
      val fallbackChunks = chunks ?: listOf(message.text)
      fallbackChunks.forEach { chunk ->
        NbgNativeHistoryText(text = stripNbgInternalMood(chunk))
      }
      return@Column
    }
    if (!message.streaming) {
      val prepared = preparedText ?: remember(preparedTextKey, message.text) {
        pretextCache?.prepareText(preparedTextKey, message.text, streaming = false)
          ?: nbgPrepareText(message.text, streaming = false)
      }
      NbgPreparedMessageText(prepared, animateBlocks = animatePreparedBlocks)
      return@Column
    }
    if (chunks == null && preparedText != null) {
      NbgPreparedMessageText(preparedText, animateBlocks = animatePreparedBlocks)
    } else {
      val fallbackChunks = chunks ?: listOf(message.text)
      fallbackChunks.forEachIndexed { index, chunk ->
        val chunkKey = if (chunks == null) preparedTextKey else "$preparedTextKey:$index"
        val prepared = remember(chunkKey, chunk, message.streaming) {
          pretextCache?.prepareText(chunkKey, chunk, message.streaming)
            ?: nbgPrepareText(chunk, message.streaming)
        }
        NbgPreparedMessageText(prepared, animateBlocks = animatePreparedBlocks)
      }
    }
    NbgStreamingTailIndicator()
  }
}

@Composable
internal fun NbgStreamingTailIndicator(
  modifier: Modifier = Modifier,
) {
  val transition = rememberInfiniteTransition(label = "streaming output indicator")
  val phase by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1_050, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "streaming output phase",
  )
  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(12.dp)
      .semantics { contentDescription = "AI 正在输出" },
  ) {
    val centerY = size.height / 2f
    val dotRadius = 2.2.dp.toPx()
    val gap = 6.dp.toPx()
    repeat(3) { index ->
      val localPhase = (phase + index * 0.22f) % 1f
      val wave = (1f - kotlin.math.abs(localPhase - 0.5f) * 2f).coerceIn(0f, 1f)
      drawCircle(
        color = NbgAgentColors.TextStrong.copy(alpha = 0.22f + wave * 0.56f),
        radius = dotRadius * (0.78f + wave * 0.26f),
        center = Offset(dotRadius + index * gap, centerY),
      )
    }
    val lineStart = dotRadius + gap * 3.1f
    val lineWidth = (size.width * 0.18f).coerceIn(28.dp.toPx(), 88.dp.toPx())
    drawRoundRect(
      brush = Brush.horizontalGradient(
        colors = listOf(
          NbgAgentColors.TextStrong.copy(alpha = 0.34f),
          NbgAgentColors.TextMuted.copy(alpha = 0.12f),
          Color.Transparent,
        ),
        startX = lineStart,
        endX = lineStart + lineWidth,
      ),
      topLeft = Offset(lineStart, centerY - 1.dp.toPx()),
      size = Size(lineWidth, 2.dp.toPx()),
      cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
    )
  }
}

@Composable
internal fun NbgNativeHistoryText(text: String) {
  BasicText(
    text = text,
    style = TextStyle(
      color = NbgAgentColors.TextStrong,
      fontSize = 15.sp,
      lineHeight = 23.sp,
      fontFamily = nbgCurrentFontFamily(),
    ),
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
internal fun NbgPreparedMessageText(
  preparedText: NbgPreparedText,
  animateBlocks: Boolean = false,
) {
  if (preparedText.blocks.isNotEmpty()) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
      preparedText.blocks.forEachIndexed { index, block ->
        NbgPreparedTextBlockView(block = block, index = index, animate = animateBlocks)
      }
    }
    return
  }
  val annotatedText = preparedText.annotatedText
  if (preparedText.kind == NbgPreparedTextKind.Markdown && annotatedText != null) {
    Text(
      text = annotatedText,
      color = NbgAgentColors.TextStrong,
      fontSize = 14.sp,
      lineHeight = 21.sp,
    )
  } else {
    Text(
      text = preparedText.displayText,
      color = NbgAgentColors.TextStrong,
      fontSize = 14.sp,
      lineHeight = 21.sp,
    )
  }
}

@Composable
internal fun NbgPreparedTextBlockView(
  block: NbgPreparedTextBlock,
  index: Int,
  animate: Boolean,
) {
  if (!animate) {
    NbgPreparedTextBlockContent(block, index)
    return
  }
  var visible by remember(block, index) { mutableStateOf(false) }
  LaunchedEffect(block, index) {
    visible = true
  }
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(animationSpec = tween(durationMillis = nbgMotionDuration(160))) +
      slideInVertically(
        animationSpec = tween(durationMillis = nbgMotionDuration(160), easing = FastOutSlowInEasing),
        initialOffsetY = { it / 5 },
      ),
  ) {
    NbgPreparedTextBlockContent(block, index)
  }
}

@Composable
internal fun NbgPreparedTextBlockContent(block: NbgPreparedTextBlock, index: Int) {
  when (block) {
    is NbgPreparedTextBlock.TextBlock -> NbgPreparedParagraph(block.text)
    is NbgPreparedTextBlock.HeadingBlock -> NbgPreparedHeading(block)
    is NbgPreparedTextBlock.QuoteBlock -> NbgPreparedQuote(block.text)
    is NbgPreparedTextBlock.ListBlock -> NbgPreparedList(block.lines)
    is NbgPreparedTextBlock.TableBlock -> NbgPreparedTable(block)
    is NbgPreparedTextBlock.CodeBlock -> NbgPreparedCodeBlock(block.text, index)
    NbgPreparedTextBlock.DividerBlock -> NbgPreparedDivider()
  }
}

@Composable
internal fun NbgPreparedParagraph(text: AnnotatedString) {
  Text(
    text = text,
    color = NbgAgentColors.TextStrong,
    fontSize = 15.sp,
    lineHeight = 23.sp,
  )
}

@Composable
internal fun NbgPreparedHeading(block: NbgPreparedTextBlock.HeadingBlock) {
  Text(
    text = block.text,
    color = NbgAgentColors.TextStrong,
    fontSize = when (block.level) {
      1 -> 18.sp
      2 -> 17.sp
      else -> 16.sp
    },
    lineHeight = when (block.level) {
      1 -> 25.sp
      2 -> 24.sp
      else -> 23.sp
    },
    fontWeight = FontWeight.SemiBold,
  )
}

@Composable
internal fun NbgPreparedQuote(text: AnnotatedString) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(
      modifier = Modifier
        .width(3.dp)
        .heightIn(min = 24.dp)
        .background(NbgAgentColors.PrimarySoft, RoundedCornerShape(2.dp)),
    )
    Text(
      text = text,
      color = NbgAgentColors.TextMuted,
      fontSize = 15.sp,
      lineHeight = 23.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
internal fun NbgPreparedList(lines: List<AnnotatedString>) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    lines.forEach { line ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "•",
          color = NbgAgentColors.Primary,
          fontSize = 16.sp,
          lineHeight = 23.sp,
        )
        Text(
          text = line,
          color = NbgAgentColors.TextStrong,
          fontSize = 15.sp,
          lineHeight = 23.sp,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
internal fun NbgPreparedDivider() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(NbgAgentColors.InputBorder),
  )
}

@Composable
internal fun NbgPreparedTable(block: NbgPreparedTextBlock.TableBlock) {
  val scrollState = rememberScrollState()
  val columnCount = (listOf(block.headers.size) + block.rows.map { it.size })
    .maxOrNull()
    ?.coerceAtLeast(1)
    ?: 1
  val cellWidth = 132.dp
  val tableWidth = (columnCount * 132).coerceAtLeast(240).dp
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(scrollState)
      .background(NbgAgentColors.InlinePanel, RoundedCornerShape(8.dp))
      .padding(vertical = 4.dp),
  ) {
    NbgPreparedTableRow(
      cells = block.headers,
      columnCount = columnCount,
      tableWidth = tableWidth,
      cellWidth = cellWidth,
      header = true,
    )
    Box(
      modifier = Modifier
        .width(tableWidth)
        .height(1.dp)
        .background(NbgAgentColors.InputBorder),
    )
    block.rows.forEach { row ->
      NbgPreparedTableRow(
        cells = row,
        columnCount = columnCount,
        tableWidth = tableWidth,
        cellWidth = cellWidth,
        header = false,
      )
    }
  }
}

@Composable
internal fun NbgPreparedTableRow(
  cells: List<AnnotatedString>,
  columnCount: Int,
  tableWidth: Dp,
  cellWidth: Dp,
  header: Boolean,
) {
  Row(modifier = Modifier.width(tableWidth)) {
    repeat(columnCount) { index ->
      Text(
        text = cells.getOrNull(index) ?: AnnotatedString(""),
        color = NbgAgentColors.TextStrong,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
          .width(cellWidth)
          .padding(horizontal = 8.dp, vertical = 7.dp),
      )
    }
  }
}

@Composable
internal fun NbgPreparedCodeBlock(text: String, index: Int) {
  val scrollState = rememberScrollState()
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(NbgAgentColors.CodeBlock, RoundedCornerShape(8.dp))
      .padding(horizontal = 12.dp, vertical = 10.dp)
      .semantics { contentDescription = "代码块 ${index + 1}" },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
      BasicText(
        text = text,
        style = TextStyle(
          color = NbgAgentColors.CodeText,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          fontFamily = FontFamily.Monospace,
        ),
        modifier = Modifier
          .horizontalScroll(scrollState),
      )
    }
  }
}

package com.nbg.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NbgAgentBottomSheet(
  expanded: Boolean,
  title: String,
  subtitle: String = "",
  onDismiss: () -> Unit,
  contentDescription: String = title,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (!expanded) return
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    containerColor = NbgAgentColors.GlassSurface,
    contentColor = NbgAgentColors.TextStrong,
    scrimColor = Color.Black.copy(alpha = 0.34f),
    tonalElevation = 0.dp,
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(top = 8.dp, bottom = 6.dp)
          .size(width = 42.dp, height = 4.dp)
          .background(NbgAgentColors.SurfaceContainerHigh, RoundedCornerShape(999.dp)),
      )
    },
    modifier = Modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .semanticsCompat(contentDescription)
        .padding(horizontal = 14.dp)
        .padding(bottom = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      NbgAgentBottomSheetHeader(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
      )
      content()
    }
  }
}

@Composable
private fun NbgAgentBottomSheetHeader(
  title: String,
  subtitle: String,
  onDismiss: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = title,
        color = NbgAgentColors.TextStrong,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle.isNotBlank()) {
        Text(
          text = subtitle,
          color = NbgAgentColors.TextMuted,
          fontSize = 11.5.sp,
          lineHeight = 16.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Surface(
      onClick = onDismiss,
      shape = RoundedCornerShape(11.dp),
      color = NbgAgentColors.SurfaceLow,
      contentColor = NbgAgentColors.TextStrong,
      border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
    ) {
      Text(
        text = "完成",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
      )
    }
  }
}

@Composable
internal fun NbgSheetSectionLabel(
  label: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = label.uppercase(),
    color = NbgAgentColors.TextDisabled,
    fontSize = 10.5.sp,
    lineHeight = 13.sp,
    fontFamily = FontFamily.Monospace,
    letterSpacing = 0.sp,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier.padding(horizontal = 3.dp, vertical = 1.dp),
  )
}

@Composable
internal fun NbgSheetTag(
  text: String,
  primary: Boolean = false,
  danger: Boolean = false,
  warning: Boolean = false,
) {
  val background = when {
    danger -> NbgAgentColors.ConfirmDangerSurface
    warning -> NbgAgentColors.StatusChip
    primary -> NbgAgentColors.PrimaryContainer
    else -> NbgAgentColors.SurfaceLow
  }
  val foreground = when {
    danger -> NbgAgentColors.StatusRed
    warning -> NbgAgentColors.StatusYellow
    primary -> NbgAgentColors.Primary
    else -> NbgAgentColors.TextMuted
  }
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = background,
    contentColor = foreground,
  ) {
    Text(
      text = text,
      fontSize = 10.sp,
      lineHeight = 12.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = if (primary || danger || warning) FontWeight.SemiBold else FontWeight.Normal,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
    )
  }
}

@Composable
internal fun NbgSheetDot(
  color: Color,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .size(7.dp)
      .background(color, CircleShape),
  )
}

@Composable
internal fun NbgSheetMonogram(
  text: String,
  primary: Boolean = false,
  danger: Boolean = false,
  warning: Boolean = false,
  dark: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val background = when {
    dark -> NbgAgentColors.CodeBlock
    danger -> NbgAgentColors.ConfirmDangerSurface
    warning -> NbgAgentColors.StatusChip
    primary -> NbgAgentColors.PrimaryContainer
    else -> NbgAgentColors.SurfaceLow
  }
  val foreground = when {
    dark -> NbgAgentColors.CodeText
    danger -> NbgAgentColors.StatusRed
    warning -> NbgAgentColors.StatusYellow
    primary -> NbgAgentColors.Primary
    else -> NbgAgentColors.TextMuted
  }
  Box(
    modifier = modifier
      .size(42.dp)
      .background(background, RoundedCornerShape(16.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text.take(4),
      color = foreground,
      fontSize = 11.sp,
      lineHeight = 13.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Black,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
internal fun NbgSheetMeter(
  progress: Float,
  modifier: Modifier = Modifier,
) {
  val safeProgress = progress.coerceIn(0f, 1f)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(7.dp)
      .background(NbgAgentColors.SurfaceContainerHigh, RoundedCornerShape(999.dp)),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(safeProgress.coerceAtLeast(0.04f))
        .height(7.dp)
        .background(NbgAgentColors.Primary, RoundedCornerShape(999.dp)),
    )
  }
}

private fun Modifier.semanticsCompat(contentDescription: String): Modifier =
  semantics { this.contentDescription = contentDescription }

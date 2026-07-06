package com.nbg.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NbgAppearanceScreen(
  preferences: NbgChatPreferences,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onSelectTheme: (String) -> Unit,
  onSelectFont: (String) -> Unit,
) {
  NbgShellSubPage(
    title = "外观",
    subtitle = "${nbgThemeOption(preferences.themeId).label} · ${nbgFontOption(preferences.fontId).label}",
    onBack = onBack,
    onOpenDrawer = onOpenDrawer,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(NbgAgentColors.Background),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        NbgAppearanceSection(title = "主题") {
          NBG_THEME_OPTIONS.forEach { option ->
            NbgThemeChoiceRow(
              option = option,
              selected = nbgNormalizeThemeId(preferences.themeId) == option.id,
              onClick = { onSelectTheme(option.id) },
            )
          }
        }
      }
      item {
        NbgAppearanceSection(title = "字体") {
          NBG_FONT_OPTIONS.forEach { option ->
            NbgFontChoiceRow(
              option = option,
              selected = nbgNormalizeFontId(preferences.fontId) == option.id,
              onClick = { onSelectFont(option.id) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun NbgAppearanceSection(
  title: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Text(
        text = title,
        color = NbgAgentColors.TextStrong,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 2.dp),
      )
      content()
    }
  }
}

@Composable
private fun NbgThemeChoiceRow(
  option: NbgThemeOption,
  selected: Boolean,
  onClick: () -> Unit,
) {
  NbgAppearanceChoiceRow(
    selected = selected,
    onClick = onClick,
    leading = {
      NbgThemePreviewDots(option.accentPreview)
    },
    title = option.label,
    description = option.description,
  )
}

@Composable
private fun NbgFontChoiceRow(
  option: NbgFontOption,
  selected: Boolean,
  onClick: () -> Unit,
) {
  NbgAppearanceChoiceRow(
    selected = selected,
    onClick = onClick,
    leading = {
      Box(
        modifier = Modifier
          .size(34.dp)
          .background(NbgAgentColors.SurfaceContainerHigh, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "Aa",
          color = NbgAgentColors.Primary,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          fontFamily = option.family,
        )
      }
    },
    title = option.label,
    description = option.description,
    titleFontFamily = option.family,
  )
}

@Composable
private fun NbgAppearanceChoiceRow(
  selected: Boolean,
  onClick: () -> Unit,
  leading: @Composable () -> Unit,
  title: String,
  description: String,
  titleFontFamily: FontFamily? = null,
) {
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(15.dp),
    color = if (selected) NbgAgentColors.Selected else NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, if (selected) NbgAgentColors.PrimarySoft else NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      leading()
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = title,
          color = NbgAgentColors.TextStrong,
          fontSize = 14.sp,
          lineHeight = 17.sp,
          fontWeight = FontWeight.SemiBold,
          fontFamily = titleFontFamily,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = description,
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          lineHeight = 14.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (selected) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = NbgAgentColors.Primary,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

@Composable
private fun NbgThemePreviewDots(accent: Color) {
  Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
    listOf(NbgAgentColors.SurfaceContainerHighest, NbgAgentColors.PrimarySoft, accent).forEach { color ->
      Box(
        modifier = Modifier
          .size(22.dp)
          .clip(CircleShape)
          .background(color),
      )
    }
  }
}

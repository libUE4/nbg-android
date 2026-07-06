package com.nbg.android

import android.animation.ValueAnimator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal fun nbgMotionDuration(durationMs: Int): Int =
  if (ValueAnimator.areAnimatorsEnabled()) durationMs else 0

internal data class NbgThemeOption(
  val id: String,
  val label: String,
  val description: String,
  val accentPreview: Color,
)

internal data class NbgFontOption(
  val id: String,
  val label: String,
  val description: String,
  val family: FontFamily,
  val weight: FontWeight = FontWeight.Normal,
)

internal data class NbgAgentColorPalette(
  val background: Color,
  val drawer: Color,
  val topBar: Color,
  val surfaceLow: Color,
  val surfaceContainer: Color,
  val surfaceContainerHigh: Color,
  val surfaceContainerHighest: Color,
  val surfaceBorder: Color,
  val selected: Color,
  val input: Color,
  val inputBorder: Color,
  val divider: Color,
  val glassSurface: Color,
  val glassBorder: Color,
  val glassChip: Color,
  val glassButton: Color,
  val liquidBorder: Color,
  val liquidCyan: Color,
  val liquidViolet: Color,
  val liquidRose: Color,
  val userBubble: Color,
  val assistantBubble: Color,
  val primary: Color,
  val primarySoft: Color,
  val primaryContainer: Color,
  val assistantRail: Color,
  val statusChip: Color,
  val onPrimary: Color,
  val textStrong: Color,
  val textMuted: Color,
  val textDisabled: Color,
  val icon: Color,
  val tool: Color,
  val inlinePanel: Color,
  val terminalInline: Color,
  val toolMuted: Color,
  val toolMutedText: Color,
  val toolMutedDanger: Color,
  val statusGreen: Color,
  val statusYellow: Color,
  val statusRed: Color,
  val diffAddSurface: Color,
  val diffDeleteSurface: Color,
  val sendDisabled: Color,
  val sendDisabledBorder: Color,
  val sendReady: Color,
  val sendReadyBorder: Color,
  val sendReadyIcon: Color,
  val sendSteer: Color,
  val sendSteerBorder: Color,
  val sendSteerIcon: Color,
  val sendStop: Color,
  val sendStopBorder: Color,
  val codeInline: Color,
  val codeBlock: Color,
  val codeText: Color,
  val confirmElevatedSurface: Color,
  val confirmElevatedBorder: Color,
  val confirmDangerSurface: Color,
  val confirmDangerBorder: Color,
)

internal val NBG_THEME_OPTIONS = listOf(
  NbgThemeOption("light", "默认白色", "干净的白色工作台，适合白天长时间阅读。", Color(0xFF2F6FED)),
  NbgThemeOption("black", "黑色", "接近 Codex 的纯黑开发工具观感。", Color(0xFFE8EAED)),
  NbgThemeOption("claude", "Claude 暖色", "低饱和暖灰和琥珀强调，更接近 Claude。", Color(0xFFB86B3F)),
)

internal val NBG_FONT_OPTIONS = listOf(
  NbgFontOption("system", "系统", "Android 默认字体，兼容性最好。", FontFamily.Default),
  NbgFontOption("ios", "iOS", "接近 San Francisco 的系统无衬线观感。", FontFamily.SansSerif),
  NbgFontOption("sans", "清爽", "更接近现代 SaaS 的无衬线观感。", FontFamily.SansSerif),
  NbgFontOption("serif", "书卷", "正文更有层次，适合长文本阅读。", FontFamily.Serif),
  NbgFontOption("mono", "代码", "整体更像开发工具，数字和代码更整齐。", FontFamily.Monospace),
)

private val NbgLightPalette = NbgAgentColorPalette(
  background = Color(0xFFF7F7F4),
  drawer = Color(0xFFFFFFFF),
  topBar = Color(0xFFFDFDFB),
  surfaceLow = Color(0xFFF0F0EC),
  surfaceContainer = Color(0xFFFFFFFF),
  surfaceContainerHigh = Color(0xFFE9E9E3),
  surfaceContainerHighest = Color(0xFFDCDCD4),
  surfaceBorder = Color(0xFFD5D5CC),
  selected = Color(0xFFEAE8DF),
  input = Color(0xFFFFFFFF),
  inputBorder = Color(0xFFD7D4CA),
  divider = Color(0xFFE2E0D7),
  glassSurface = Color(0xFFFFFFFF),
  glassBorder = Color(0xFFD8D5CC),
  glassChip = Color(0xFFF0EEE7),
  glassButton = Color(0xFFEDEBE4),
  liquidBorder = Color(0xFFD8D5CC),
  liquidCyan = Color(0xFF5E8FC7),
  liquidViolet = Color(0xFF7B6FB0),
  liquidRose = Color(0xFFC96E70),
  userBubble = Color(0xFFE9EDF5),
  assistantBubble = Color(0xFFFFFFFF),
  primary = Color(0xFF2F5F9D),
  primarySoft = Color(0xFFDDE8F6),
  primaryContainer = Color(0xFFCFDFF2),
  assistantRail = Color(0xFFB7B2A8),
  statusChip = Color(0xFFF0EEE7),
  onPrimary = Color(0xFFFFFFFF),
  textStrong = Color(0xFF1E1F22),
  textMuted = Color(0xFF676B72),
  textDisabled = Color(0xFF9EA2A8),
  icon = Color(0xFF2C2E33),
  tool = Color(0xFF2C2E33),
  inlinePanel = Color(0xFFF4F3EF),
  terminalInline = Color(0xFF101216),
  toolMuted = Color(0xFF747982),
  toolMutedText = Color(0xFF676B72),
  toolMutedDanger = Color(0xFFC34B45),
  statusGreen = Color(0xFF2E8B57),
  statusYellow = Color(0xFFC7822C),
  statusRed = Color(0xFFC34B45),
  diffAddSurface = Color(0xFFE4F4E8),
  diffDeleteSurface = Color(0xFFF7E4E2),
  sendDisabled = Color(0xFFE4E3DD),
  sendDisabledBorder = Color(0xFFD4D1C8),
  sendReady = Color(0xFF1E1F22),
  sendReadyBorder = Color(0xFF1E1F22),
  sendReadyIcon = Color(0xFFFFFFFF),
  sendSteer = Color(0xFFDDE8F6),
  sendSteerBorder = Color(0xFFBFD3EB),
  sendSteerIcon = Color(0xFF1F4F86),
  sendStop = Color(0xFFF5D8D6),
  sendStopBorder = Color(0xFFE7A9A4),
  codeInline = Color(0xFFE9E9E3),
  codeBlock = Color(0xFF111317),
  codeText = Color(0xFFE5E7EB),
  confirmElevatedSurface = Color(0xFFFFFFFF),
  confirmElevatedBorder = Color(0xFFD5D5CC),
  confirmDangerSurface = Color(0xFFF8E8E6),
  confirmDangerBorder = Color(0xFFE7A9A4),
)

private val NbgBlackPalette = NbgAgentColorPalette(
  background = Color(0xFF090A0B),
  drawer = Color(0xFF111214),
  topBar = Color(0xFF0D0E10),
  surfaceLow = Color(0xFF141518),
  surfaceContainer = Color(0xFF191B1F),
  surfaceContainerHigh = Color(0xFF202329),
  surfaceContainerHighest = Color(0xFF2A2E35),
  surfaceBorder = Color(0xFF343841),
  selected = Color(0xFF252A32),
  input = Color(0xFF111317),
  inputBorder = Color(0xFF30343C),
  divider = Color(0xFF25282E),
  glassSurface = Color(0xFF0F1115),
  glassBorder = Color(0xFF343943),
  glassChip = Color(0xFF191D23),
  glassButton = Color(0xFF1A1D23),
  liquidBorder = Color(0xFF343943),
  liquidCyan = Color(0xFF8DB7FF),
  liquidViolet = Color(0xFFAFA6FF),
  liquidRose = Color(0xFFFFB0C8),
  userBubble = Color(0xFF262C35),
  assistantBubble = Color(0xFF111317),
  primary = Color(0xFFE8EAED),
  primarySoft = Color(0xFF252A32),
  primaryContainer = Color(0xFF303640),
  assistantRail = Color(0xFF5D6572),
  statusChip = Color(0xFF181A1F),
  onPrimary = Color(0xFFFFFFFF),
  textStrong = Color(0xFFF3F4F6),
  textMuted = Color(0xFFA0A7B1),
  textDisabled = Color(0xFF656C76),
  icon = Color(0xFFE5E7EB),
  tool = Color(0xFFE5E7EB),
  inlinePanel = Color(0xFF13161B),
  terminalInline = Color(0xFF080A0D),
  toolMuted = Color(0xFF7B8491),
  toolMutedText = Color(0xFFA0A7B1),
  toolMutedDanger = Color(0xFFFF7468),
  statusGreen = Color(0xFF7DDC8A),
  statusYellow = Color(0xFFE9B872),
  statusRed = Color(0xFFFF7468),
  diffAddSurface = Color(0xFF10251A),
  diffDeleteSurface = Color(0xFF2A1516),
  sendDisabled = Color(0xFF242832),
  sendDisabledBorder = Color(0xFF343841),
  sendReady = Color(0xFFF1F3F5),
  sendReadyBorder = Color(0xFFFFFFFF),
  sendReadyIcon = Color(0xFF090A0B),
  sendSteer = Color(0xFFDCE7F5),
  sendSteerBorder = Color(0xFFF7FBFF),
  sendSteerIcon = Color(0xFF0B0D10),
  sendStop = Color(0xFF4A2528),
  sendStopBorder = Color(0xFF6E3338),
  codeInline = Color(0xFF202329),
  codeBlock = Color(0xFF101216),
  codeText = Color(0xFFE5E7EB),
  confirmElevatedSurface = Color(0xFF181B20),
  confirmElevatedBorder = Color(0xFF3A414D),
  confirmDangerSurface = Color(0xFF241416),
  confirmDangerBorder = Color(0xFF6B2F34),
)

private val NbgClaudePalette = NbgBlackPalette.copy(
  background = Color(0xFF181512),
  drawer = Color(0xFF211C18),
  topBar = Color(0xFF1D1915),
  surfaceLow = Color(0xFF1B1712),
  surfaceContainer = Color(0xFF241E17),
  surfaceContainerHigh = Color(0xFF2E261D),
  surfaceContainerHighest = Color(0xFF3A3024),
  surfaceBorder = Color(0xFF4A3F31),
  selected = Color(0xFF342B21),
  input = Color(0xFF18130E),
  inputBorder = Color(0xFF403629),
  glassButton = Color(0xFF271F18),
  liquidCyan = Color(0xFFA7D8FF),
  liquidViolet = Color(0xFFD0C0FF),
  liquidRose = Color(0xFFFFC0B5),
  primary = Color(0xFFE9C46A),
  primarySoft = Color(0xFF3A3024),
  primaryContainer = Color(0xFF4A3F31),
  userBubble = Color(0xFF352B20),
  assistantBubble = Color(0xFF18130E),
  textStrong = Color(0xFFF8F1E7),
  textMuted = Color(0xFFB8AA98),
  sendReady = Color(0xFFFFE0A3),
  sendReadyBorder = Color(0xFFFFF0C9),
  sendReadyIcon = Color(0xFF0E0C09),
  sendSteer = Color(0xFFF2D28A),
  sendSteerIcon = Color(0xFF0E0C09),
  codeInline = Color(0xFF2F261C),
  codeBlock = Color(0xFF120F0B),
)

private val LocalNbgFontOption = staticCompositionLocalOf { NBG_FONT_OPTIONS.first() }
private var nbgActivePalette by mutableStateOf(NbgLightPalette)

internal fun nbgNormalizeThemeId(id: String?): String =
  when (id?.trim()) {
    "black", "night", "graphite" -> "black"
    "claude", "warm" -> "claude"
    "light" -> "light"
    else -> "light"
  }

internal fun nbgNormalizeFontId(id: String?): String =
  NBG_FONT_OPTIONS.firstOrNull { it.id == id?.trim() }?.id ?: "system"

internal fun nbgThemePalette(id: String): NbgAgentColorPalette =
  when (nbgNormalizeThemeId(id)) {
    "black" -> NbgBlackPalette
    "claude" -> NbgClaudePalette
    else -> NbgLightPalette
  }

internal fun nbgThemeOption(id: String): NbgThemeOption =
  NBG_THEME_OPTIONS.firstOrNull { it.id == nbgNormalizeThemeId(id) } ?: NBG_THEME_OPTIONS.first()

internal fun nbgFontOption(id: String): NbgFontOption =
  NBG_FONT_OPTIONS.firstOrNull { it.id == nbgNormalizeFontId(id) } ?: NBG_FONT_OPTIONS.first()

internal fun nbgMaterialColorScheme(palette: NbgAgentColorPalette): ColorScheme =
  if (palette.background == NbgLightPalette.background) {
    lightColorScheme(
      primary = palette.primary,
      onPrimary = palette.onPrimary,
      primaryContainer = palette.primaryContainer,
      onPrimaryContainer = palette.textStrong,
      secondary = palette.textMuted,
      onSecondary = palette.drawer,
      secondaryContainer = palette.selected,
      onSecondaryContainer = palette.icon,
      background = palette.background,
      onBackground = palette.textStrong,
      surface = palette.drawer,
      onSurface = palette.textStrong,
      surfaceVariant = palette.surfaceContainerHigh,
      onSurfaceVariant = palette.textMuted,
      outline = palette.surfaceBorder,
      outlineVariant = palette.divider,
      error = palette.statusRed,
      onError = Color.White,
    )
  } else {
    darkColorScheme(
      primary = palette.primary,
      onPrimary = palette.sendReadyIcon,
      primaryContainer = palette.primaryContainer,
      onPrimaryContainer = palette.textStrong,
      secondary = palette.textMuted,
      onSecondary = palette.drawer,
      secondaryContainer = palette.selected,
      onSecondaryContainer = palette.icon,
      background = palette.background,
      onBackground = palette.textStrong,
      surface = palette.drawer,
      onSurface = palette.textStrong,
      surfaceVariant = palette.surfaceContainerHigh,
      onSurfaceVariant = palette.textMuted,
      outline = palette.surfaceBorder,
      outlineVariant = palette.divider,
      error = palette.statusRed,
      onError = Color.White,
    )
  }

internal fun nbgTypography(font: NbgFontOption): Typography {
  fun style(size: Int, lineHeight: Int, weight: FontWeight = font.weight): TextStyle =
    TextStyle(
      fontFamily = font.family,
      fontWeight = weight,
      fontSize = size.sp,
      lineHeight = lineHeight.sp,
      platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

  return Typography(
    displayLarge = style(57, 64, FontWeight.SemiBold),
    displayMedium = style(45, 52, FontWeight.SemiBold),
    displaySmall = style(36, 44, FontWeight.SemiBold),
    headlineLarge = style(32, 40, FontWeight.SemiBold),
    headlineMedium = style(28, 36, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.Medium),
    titleSmall = style(14, 20, FontWeight.Medium),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 21),
    bodySmall = style(12, 17),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 15, FontWeight.Medium),
  )
}

@Composable
internal fun NbgThemeProvider(
  themeId: String,
  fontId: String,
  content: @Composable () -> Unit,
) {
  val palette = nbgThemePalette(themeId)
  val font = nbgFontOption(fontId)
  nbgActivePalette = palette
  CompositionLocalProvider(
    LocalNbgFontOption provides font,
    content = content,
  )
}

@Composable
internal fun nbgCurrentFontFamily(): FontFamily = LocalNbgFontOption.current.family

internal object NbgAgentColors {
  private val palette: NbgAgentColorPalette
    get() = nbgActivePalette

  val Background: Color get() = palette.background
  val Drawer: Color get() = palette.drawer
  val TopBar: Color get() = palette.topBar
  val SurfaceLow: Color get() = palette.surfaceLow
  val SurfaceContainer: Color get() = palette.surfaceContainer
  val SurfaceContainerHigh: Color get() = palette.surfaceContainerHigh
  val SurfaceContainerHighest: Color get() = palette.surfaceContainerHighest
  val SurfaceBorder: Color get() = palette.surfaceBorder
  val Selected: Color get() = palette.selected
  val Input: Color get() = palette.input
  val InputBorder: Color get() = palette.inputBorder
  val Divider: Color get() = palette.divider
  val GlassSurface: Color get() = palette.glassSurface
  val GlassBorder: Color get() = palette.glassBorder
  val GlassChip: Color get() = palette.glassChip
  val GlassButton: Color get() = palette.glassButton
  val LiquidBorder: Color get() = palette.liquidBorder
  val LiquidCyan: Color get() = palette.liquidCyan
  val LiquidViolet: Color get() = palette.liquidViolet
  val LiquidRose: Color get() = palette.liquidRose
  val UserBubble: Color get() = palette.userBubble
  val AssistantBubble: Color get() = palette.assistantBubble
  val Primary: Color get() = palette.primary
  val PrimarySoft: Color get() = palette.primarySoft
  val PrimaryContainer: Color get() = palette.primaryContainer
  val AssistantRail: Color get() = palette.assistantRail
  val StatusChip: Color get() = palette.statusChip
  val OnPrimary: Color get() = palette.onPrimary
  val TextStrong: Color get() = palette.textStrong
  val TextMuted: Color get() = palette.textMuted
  val TextDisabled: Color get() = palette.textDisabled
  val Icon: Color get() = palette.icon
  val Tool: Color get() = palette.tool
  val InlinePanel: Color get() = palette.inlinePanel
  val TerminalInline: Color get() = palette.terminalInline
  val ToolMuted: Color get() = palette.toolMuted
  val ToolMutedText: Color get() = palette.toolMutedText
  val ToolMutedDanger: Color get() = palette.toolMutedDanger
  val StatusGreen: Color get() = palette.statusGreen
  val StatusYellow: Color get() = palette.statusYellow
  val StatusRed: Color get() = palette.statusRed
  val DiffAddSurface: Color get() = palette.diffAddSurface
  val DiffDeleteSurface: Color get() = palette.diffDeleteSurface
  val SendDisabled: Color get() = palette.sendDisabled
  val SendDisabledBorder: Color get() = palette.sendDisabledBorder
  val SendReady: Color get() = palette.sendReady
  val SendReadyBorder: Color get() = palette.sendReadyBorder
  val SendReadyIcon: Color get() = palette.sendReadyIcon
  val SendSteer: Color get() = palette.sendSteer
  val SendSteerBorder: Color get() = palette.sendSteerBorder
  val SendSteerIcon: Color get() = palette.sendSteerIcon
  val SendStop: Color get() = palette.sendStop
  val SendStopBorder: Color get() = palette.sendStopBorder
  val CodeInline: Color get() = palette.codeInline
  val CodeBlock: Color get() = palette.codeBlock
  val CodeText: Color get() = palette.codeText
  val ConfirmElevatedSurface: Color get() = palette.confirmElevatedSurface
  val ConfirmElevatedBorder: Color get() = palette.confirmElevatedBorder
  val ConfirmDangerSurface: Color get() = palette.confirmDangerSurface
  val ConfirmDangerBorder: Color get() = palette.confirmDangerBorder
  val LiquidBackgroundBrush: SolidColor get() = SolidColor(Background)
  val LiquidAccentBrush: SolidColor get() = SolidColor(SurfaceContainerHigh)
  val LiquidPanelBrush: SolidColor get() = SolidColor(TopBar)
}

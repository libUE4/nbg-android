package com.nbg.android.terminal

sealed class TerminalColor {
  data object DefaultForeground : TerminalColor()
  data object DefaultBackground : TerminalColor()
  data object StandardBlack : TerminalColor()
  data object StandardRed : TerminalColor()
  data object StandardGreen : TerminalColor()
  data object StandardYellow : TerminalColor()
  data object StandardBlue : TerminalColor()
  data object StandardMagenta : TerminalColor()
  data object StandardCyan : TerminalColor()
  data object StandardWhite : TerminalColor()
  data object BrightBlack : TerminalColor()
  data object BrightRed : TerminalColor()
  data object BrightGreen : TerminalColor()
  data object BrightYellow : TerminalColor()
  data object BrightBlue : TerminalColor()
  data object BrightMagenta : TerminalColor()
  data object BrightCyan : TerminalColor()
  data object BrightWhite : TerminalColor()
  data class Indexed(val index: Int) : TerminalColor()
  data class Rgb(val red: Int, val green: Int, val blue: Int) : TerminalColor()
}

data class TerminalAttributes(
  val foreground: TerminalColor = TerminalColor.DefaultForeground,
  val background: TerminalColor = TerminalColor.DefaultBackground,
  val bold: Boolean = false,
  val dim: Boolean = false,
  val italic: Boolean = false,
  val underline: Boolean = false,
  val inverse: Boolean = false,
  val hidden: Boolean = false,
  val strikethrough: Boolean = false,
)

data class TerminalCell(
  val char: Char = ' ',
  val attributes: TerminalAttributes = TerminalAttributes(),
  val role: TerminalCellRole = TerminalCellRole.Normal,
)

enum class TerminalCellRole {
  Normal,
  WideTrailing,
}

package com.nbg.android.terminal

data class TerminalModes(
  val applicationCursorKeys: Boolean = false,
  val bracketedPaste: Boolean = false,
  val applicationKeypad: Boolean = false,
)

enum class TerminalKeypadKey(
  val normal: String,
  val applicationFinal: Char,
) {
  Zero("0", 'p'),
  One("1", 'q'),
  Two("2", 'r'),
  Three("3", 's'),
  Four("4", 't'),
  Five("5", 'u'),
  Six("6", 'v'),
  Seven("7", 'w'),
  Eight("8", 'x'),
  Nine("9", 'y'),
  Minus("-", 'm'),
  Comma(",", 'l'),
  Period(".", 'n'),
  Plus("+", 'k'),
  Multiply("*", 'j'),
  Divide("/", 'o'),
  Enter("\r", 'M'),
}

data class TerminalInputModifiers(
  val shift: Boolean = false,
  val ctrl: Boolean = false,
  val alt: Boolean = false,
) {
  fun isEmpty(): Boolean = !shift && !ctrl && !alt

  fun toggleShift(): TerminalInputModifiers = copy(shift = !shift)

  fun toggleCtrl(): TerminalInputModifiers = copy(ctrl = !ctrl)

  fun toggleAlt(): TerminalInputModifiers = copy(alt = !alt)
}

object TerminalInputSequence {
  const val arrowUp = "\u001B[A"
  const val arrowDown = "\u001B[B"
  const val arrowLeft = "\u001B[D"
  const val arrowRight = "\u001B[C"
  const val escape = "\u001B"
  const val tab = "\t"
  const val backspace = "\u007F"
  const val enter = "\r"

  fun arrowUp(
    modes: TerminalModes,
    modifiers: TerminalInputModifiers = TerminalInputModifiers(),
  ): String = navigationSequence(modes, modifiers, normal = arrowUp, application = "\u001BOA", final = 'A')

  fun arrowDown(
    modes: TerminalModes,
    modifiers: TerminalInputModifiers = TerminalInputModifiers(),
  ): String = navigationSequence(modes, modifiers, normal = arrowDown, application = "\u001BOB", final = 'B')

  fun arrowLeft(
    modes: TerminalModes,
    modifiers: TerminalInputModifiers = TerminalInputModifiers(),
  ): String = navigationSequence(modes, modifiers, normal = arrowLeft, application = "\u001BOD", final = 'D')

  fun arrowRight(
    modes: TerminalModes,
    modifiers: TerminalInputModifiers = TerminalInputModifiers(),
  ): String = navigationSequence(modes, modifiers, normal = arrowRight, application = "\u001BOC", final = 'C')

  fun tab(modifiers: TerminalInputModifiers = TerminalInputModifiers()): String =
    if (modifiers.shift && !modifiers.ctrl && !modifiers.alt) "\u001B[Z" else tab

  fun home(modifiers: TerminalInputModifiers = TerminalInputModifiers()): String =
    editingSequence(modifiers, plain = "\u001B[H", final = 'H')

  fun end(modifiers: TerminalInputModifiers = TerminalInputModifiers()): String =
    editingSequence(modifiers, plain = "\u001B[F", final = 'F')

  fun pageUp(modifiers: TerminalInputModifiers = TerminalInputModifiers()): String =
    pagingSequence(modifiers, code = 5)

  fun pageDown(modifiers: TerminalInputModifiers = TerminalInputModifiers()): String =
    pagingSequence(modifiers, code = 6)

  fun paste(text: String, modes: TerminalModes): String =
    if (modes.bracketedPaste) "\u001B[200~$text\u001B[201~" else text

  fun keypad(key: TerminalKeypadKey, modes: TerminalModes): String =
    if (modes.applicationKeypad) "\u001BO${key.applicationFinal}" else key.normal

  fun ctrl(letter: Char): String {
    val upper = letter.uppercaseChar()
    return when (upper) {
      in 'A'..'Z' -> ((upper.code - 'A'.code + 1).toChar()).toString()
      '[' -> "\u001B"
      '\\' -> "\u001C"
      ']' -> "\u001D"
      '^' -> "\u001E"
      '_' -> "\u001F"
      '?' -> "\u007F"
      else -> error("Ctrl sequence requires A-Z or one of []\\^_?")
    }
  }

  private fun navigationSequence(
    modes: TerminalModes,
    modifiers: TerminalInputModifiers,
    normal: String,
    application: String,
    final: Char,
  ): String {
    if (modifiers.isEmpty()) return if (modes.applicationCursorKeys) application else normal
    return "\u001B[1;${modifierParameter(modifiers)}$final"
  }

  private fun editingSequence(
    modifiers: TerminalInputModifiers,
    plain: String,
    final: Char,
  ): String {
    if (modifiers.isEmpty()) return plain
    return "\u001B[1;${modifierParameter(modifiers)}$final"
  }

  private fun pagingSequence(
    modifiers: TerminalInputModifiers,
    code: Int,
  ): String {
    if (modifiers.isEmpty()) return "\u001B[${code}~"
    return "\u001B[$code;${modifierParameter(modifiers)}~"
  }

  private fun modifierParameter(modifiers: TerminalInputModifiers): Int =
    1 +
      (if (modifiers.shift) 1 else 0) +
      (if (modifiers.alt) 2 else 0) +
      (if (modifiers.ctrl) 4 else 0)
}

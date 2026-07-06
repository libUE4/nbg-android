package com.nbg.android.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalInputSequenceTest {
  @Test
  fun navigationKeysProduceAnsiEscapeSequences() {
    assertEquals("\u001B[A", TerminalInputSequence.arrowUp)
    assertEquals("\u001B[B", TerminalInputSequence.arrowDown)
    assertEquals("\u001B[D", TerminalInputSequence.arrowLeft)
    assertEquals("\u001B[C", TerminalInputSequence.arrowRight)
  }

  @Test
  fun applicationCursorModeUsesSs3NavigationSequences() {
    val modes = TerminalModes(applicationCursorKeys = true)

    assertEquals("\u001BOA", TerminalInputSequence.arrowUp(modes))
    assertEquals("\u001BOB", TerminalInputSequence.arrowDown(modes))
    assertEquals("\u001BOD", TerminalInputSequence.arrowLeft(modes))
    assertEquals("\u001BOC", TerminalInputSequence.arrowRight(modes))
  }

  @Test
  fun navigationKeysSupportXtermModifierParameters() {
    val shift = TerminalInputModifiers(shift = true)
    val ctrl = TerminalInputModifiers(ctrl = true)
    val alt = TerminalInputModifiers(alt = true)
    val all = TerminalInputModifiers(shift = true, ctrl = true, alt = true)

    assertEquals("\u001B[1;2A", TerminalInputSequence.arrowUp(TerminalModes(), shift))
    assertEquals("\u001B[1;5D", TerminalInputSequence.arrowLeft(TerminalModes(), ctrl))
    assertEquals("\u001B[1;3C", TerminalInputSequence.arrowRight(TerminalModes(), alt))
    assertEquals("\u001B[1;8B", TerminalInputSequence.arrowDown(TerminalModes(), all))
  }

  @Test
  fun editingAndPagingKeysProduceXtermSequences() {
    assertEquals("\u001B[H", TerminalInputSequence.home())
    assertEquals("\u001B[F", TerminalInputSequence.end())
    assertEquals("\u001B[5~", TerminalInputSequence.pageUp())
    assertEquals("\u001B[6~", TerminalInputSequence.pageDown())

    assertEquals("\u001B[1;2H", TerminalInputSequence.home(TerminalInputModifiers(shift = true)))
    assertEquals("\u001B[1;5F", TerminalInputSequence.end(TerminalInputModifiers(ctrl = true)))
    assertEquals("\u001B[5;3~", TerminalInputSequence.pageUp(TerminalInputModifiers(alt = true)))
    assertEquals("\u001B[6;8~", TerminalInputSequence.pageDown(TerminalInputModifiers(shift = true, ctrl = true, alt = true)))
  }

  @Test
  fun numericKeypadKeysHonorApplicationKeypadMode() {
    assertEquals("7", TerminalInputSequence.keypad(TerminalKeypadKey.Seven, TerminalModes()))
    assertEquals("+", TerminalInputSequence.keypad(TerminalKeypadKey.Plus, TerminalModes()))
    assertEquals("\r", TerminalInputSequence.keypad(TerminalKeypadKey.Enter, TerminalModes()))

    val application = TerminalModes(applicationKeypad = true)
    assertEquals("\u001BOw", TerminalInputSequence.keypad(TerminalKeypadKey.Seven, application))
    assertEquals("\u001BOk", TerminalInputSequence.keypad(TerminalKeypadKey.Plus, application))
    assertEquals("\u001BOM", TerminalInputSequence.keypad(TerminalKeypadKey.Enter, application))
  }

  @Test
  fun pasteUsesBracketedPasteDelimitersWhenEnabled() {
    val plain = "echo one\necho two"

    assertEquals(plain, TerminalInputSequence.paste(plain, TerminalModes()))
    assertEquals(
      "\u001B[200~echo one\necho two\u001B[201~",
      TerminalInputSequence.paste(plain, TerminalModes(bracketedPaste = true)),
    )
  }

  @Test
  fun editingKeysProduceTerminalControlBytes() {
    assertEquals("\u001B", TerminalInputSequence.escape)
    assertEquals("\t", TerminalInputSequence.tab)
    assertEquals("\u007F", TerminalInputSequence.backspace)
    assertEquals("\r", TerminalInputSequence.enter)
  }

  @Test
  fun ctrlLettersMapToAsciiControlCharacters() {
    assertEquals("\u0003", TerminalInputSequence.ctrl('c'))
    assertEquals("\u0004", TerminalInputSequence.ctrl('D'))
    assertEquals("\u000C", TerminalInputSequence.ctrl('l'))
  }

  @Test
  fun ctrlPunctuationMapsToAsciiControlCharacters() {
    assertEquals("\u001B", TerminalInputSequence.ctrl('['))
    assertEquals("\u001C", TerminalInputSequence.ctrl('\\'))
    assertEquals("\u001D", TerminalInputSequence.ctrl(']'))
    assertEquals("\u001E", TerminalInputSequence.ctrl('^'))
    assertEquals("\u001F", TerminalInputSequence.ctrl('_'))
    assertEquals("\u007F", TerminalInputSequence.ctrl('?'))
  }
}

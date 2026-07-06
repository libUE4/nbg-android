package com.nbg.android

import com.nbg.android.terminal.TerminalInputModifiers
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalExtraKeysLayoutTest {
  @Test
  fun usesRequestedTwoRowTermuxExtraKeysLayout() {
    assertEquals(
      listOf("Esc", "Menu", "Scroll", "Shift", "Tab", "Home", "Up", "End", "PgUp"),
      TerminalExtraKeysLayout.rows[0].map { it.display },
    )
    assertEquals(
      listOf("Ctrl", "Alt", "Left", "Down", "Right", "PgDn"),
      TerminalExtraKeysLayout.rows[1].map { it.display },
    )
  }

  @Test
  fun rendersExactlyOneTabShortcut() {
    val tabCount = TerminalExtraKeysLayout.rows.flatten().count { it.id == TerminalExtraKeyId.Tab }

    assertEquals(1, tabCount)
  }

  @Test
  fun rendersMenuAndScrollAsIconKeys() {
    val keys = TerminalExtraKeysLayout.rows.flatten().associateBy { it.id }

    assertEquals(TerminalExtraKeyIcon.Menu, keys.getValue(TerminalExtraKeyId.Drawer).icon)
    assertEquals(TerminalExtraKeyIcon.Scroll, keys.getValue(TerminalExtraKeyId.Scroll).icon)
    assertEquals(TerminalExtraKeyIcon.Up, keys.getValue(TerminalExtraKeyId.Up).icon)
    assertEquals(TerminalExtraKeyIcon.Left, keys.getValue(TerminalExtraKeyId.Left).icon)
    assertEquals(TerminalExtraKeyIcon.Down, keys.getValue(TerminalExtraKeyId.Down).icon)
    assertEquals(TerminalExtraKeyIcon.Right, keys.getValue(TerminalExtraKeyId.Right).icon)
    assertEquals(null, keys.getValue(TerminalExtraKeyId.Escape).icon)
  }

  @Test
  fun modifierKeysToggleAndApplyToNextTerminalKey() {
    val shifted = TerminalExtraKeyActionResolver.resolve(
      key = TerminalExtraKeyId.Shift,
      modifiers = TerminalInputModifiers(),
    )
    assertEquals(null, shifted.command)
    assertEquals(TerminalInputModifiers(shift = true), shifted.nextModifiers)

    val up = TerminalExtraKeyActionResolver.resolve(
      key = TerminalExtraKeyId.Up,
      modifiers = shifted.nextModifiers,
    )
    assertEquals(TerminalExtraKeyCommand.SendUp(TerminalInputModifiers(shift = true)), up.command)
    assertEquals(TerminalInputModifiers(), up.nextModifiers)
  }

  @Test
  fun specialKeysMapToDrawerScrollAndTerminalCommands() {
    assertEquals(
      TerminalExtraKeyCommand.OpenDrawer,
      TerminalExtraKeyActionResolver.resolve(TerminalExtraKeyId.Drawer, TerminalInputModifiers()).command,
    )
    assertEquals(
      TerminalExtraKeyCommand.ToggleScroll,
      TerminalExtraKeyActionResolver.resolve(TerminalExtraKeyId.Scroll, TerminalInputModifiers()).command,
    )
    assertEquals(
      TerminalExtraKeyCommand.SendHome(TerminalInputModifiers()),
      TerminalExtraKeyActionResolver.resolve(TerminalExtraKeyId.Home, TerminalInputModifiers()).command,
    )
    assertEquals(
      TerminalExtraKeyCommand.SendPageDown(TerminalInputModifiers(ctrl = true)),
      TerminalExtraKeyActionResolver.resolve(TerminalExtraKeyId.PageDown, TerminalInputModifiers(ctrl = true)).command,
    )
  }

  @Test
  fun nonTerminalActionsClearOneShotModifiers() {
    val modifiers = TerminalInputModifiers(shift = true, ctrl = true, alt = true)

    assertEquals(
      TerminalInputModifiers(),
      TerminalExtraKeyActionResolver.resolve(TerminalExtraKeyId.Drawer, modifiers).nextModifiers,
    )
    assertEquals(
      TerminalInputModifiers(),
      TerminalExtraKeyActionResolver.resolve(TerminalExtraKeyId.Scroll, modifiers).nextModifiers,
    )
  }
}

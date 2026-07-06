package com.nbg.android

import com.nbg.android.terminal.TerminalInputModifiers

enum class TerminalExtraKeyId {
  Escape,
  Drawer,
  Scroll,
  Shift,
  Tab,
  Home,
  Up,
  End,
  PageUp,
  Ctrl,
  Alt,
  Left,
  Down,
  Right,
  PageDown,
}

enum class TerminalExtraKeyIcon {
  Menu,
  Scroll,
  Up,
  Left,
  Down,
  Right,
}

data class TerminalExtraKeySpec(
  val id: TerminalExtraKeyId,
  val display: String,
  val accessibilityLabel: String,
  val icon: TerminalExtraKeyIcon? = null,
)

object TerminalExtraKeysLayout {
  val rows: List<List<TerminalExtraKeySpec>> = listOf(
    listOf(
      TerminalExtraKeySpec(TerminalExtraKeyId.Escape, "Esc", "Esc"),
      TerminalExtraKeySpec(TerminalExtraKeyId.Drawer, "Menu", "Menu", TerminalExtraKeyIcon.Menu),
      TerminalExtraKeySpec(TerminalExtraKeyId.Scroll, "Scroll", "Scroll", TerminalExtraKeyIcon.Scroll),
      TerminalExtraKeySpec(TerminalExtraKeyId.Shift, "Shift", "Shift"),
      TerminalExtraKeySpec(TerminalExtraKeyId.Tab, "Tab", "Tab"),
      TerminalExtraKeySpec(TerminalExtraKeyId.Home, "Home", "Home"),
      TerminalExtraKeySpec(TerminalExtraKeyId.Up, "Up", "Up", TerminalExtraKeyIcon.Up),
      TerminalExtraKeySpec(TerminalExtraKeyId.End, "End", "End"),
      TerminalExtraKeySpec(TerminalExtraKeyId.PageUp, "PgUp", "Page up"),
    ),
    listOf(
      TerminalExtraKeySpec(TerminalExtraKeyId.Ctrl, "Ctrl", "Ctrl"),
      TerminalExtraKeySpec(TerminalExtraKeyId.Alt, "Alt", "Alt"),
      TerminalExtraKeySpec(TerminalExtraKeyId.Left, "Left", "Left", TerminalExtraKeyIcon.Left),
      TerminalExtraKeySpec(TerminalExtraKeyId.Down, "Down", "Down", TerminalExtraKeyIcon.Down),
      TerminalExtraKeySpec(TerminalExtraKeyId.Right, "Right", "Right", TerminalExtraKeyIcon.Right),
      TerminalExtraKeySpec(TerminalExtraKeyId.PageDown, "PgDn", "Page down"),
    ),
  )
}

data class TerminalExtraKeyResolution(
  val command: TerminalExtraKeyCommand?,
  val nextModifiers: TerminalInputModifiers,
)

sealed class TerminalExtraKeyCommand {
  data object OpenDrawer : TerminalExtraKeyCommand()
  data object ToggleScroll : TerminalExtraKeyCommand()
  data object SendEscape : TerminalExtraKeyCommand()
  data class SendTab(val modifiers: TerminalInputModifiers) : TerminalExtraKeyCommand()
  data class SendHome(val modifiers: TerminalInputModifiers) : TerminalExtraKeyCommand()
  data class SendUp(val modifiers: TerminalInputModifiers) : TerminalExtraKeyCommand()
  data class SendEnd(val modifiers: TerminalInputModifiers) : TerminalExtraKeyCommand()
  data class SendPageUp(val modifiers: TerminalInputModifiers) : TerminalExtraKeyCommand()
  data class SendLeft(val modifiers: TerminalInputModifiers) : TerminalExtraKeyCommand()
  data class SendDown(val modifiers: TerminalInputModifiers) : TerminalExtraKeyCommand()
  data class SendRight(val modifiers: TerminalInputModifiers) : TerminalExtraKeyCommand()
  data class SendPageDown(val modifiers: TerminalInputModifiers) : TerminalExtraKeyCommand()
}

object TerminalExtraKeyActionResolver {
  fun resolve(
    key: TerminalExtraKeyId,
    modifiers: TerminalInputModifiers,
  ): TerminalExtraKeyResolution {
    val command = when (key) {
      TerminalExtraKeyId.Escape -> TerminalExtraKeyCommand.SendEscape
      TerminalExtraKeyId.Drawer -> TerminalExtraKeyCommand.OpenDrawer
      TerminalExtraKeyId.Scroll -> TerminalExtraKeyCommand.ToggleScroll
      TerminalExtraKeyId.Shift -> null
      TerminalExtraKeyId.Tab -> TerminalExtraKeyCommand.SendTab(modifiers)
      TerminalExtraKeyId.Home -> TerminalExtraKeyCommand.SendHome(modifiers)
      TerminalExtraKeyId.Up -> TerminalExtraKeyCommand.SendUp(modifiers)
      TerminalExtraKeyId.End -> TerminalExtraKeyCommand.SendEnd(modifiers)
      TerminalExtraKeyId.PageUp -> TerminalExtraKeyCommand.SendPageUp(modifiers)
      TerminalExtraKeyId.Ctrl -> null
      TerminalExtraKeyId.Alt -> null
      TerminalExtraKeyId.Left -> TerminalExtraKeyCommand.SendLeft(modifiers)
      TerminalExtraKeyId.Down -> TerminalExtraKeyCommand.SendDown(modifiers)
      TerminalExtraKeyId.Right -> TerminalExtraKeyCommand.SendRight(modifiers)
      TerminalExtraKeyId.PageDown -> TerminalExtraKeyCommand.SendPageDown(modifiers)
    }
    return TerminalExtraKeyResolution(
      command = command,
      nextModifiers = nextModifiers(key, modifiers),
    )
  }

  private fun nextModifiers(
    key: TerminalExtraKeyId,
    modifiers: TerminalInputModifiers,
  ): TerminalInputModifiers = when (key) {
    TerminalExtraKeyId.Shift -> modifiers.toggleShift()
    TerminalExtraKeyId.Ctrl -> modifiers.toggleCtrl()
    TerminalExtraKeyId.Alt -> modifiers.toggleAlt()
    TerminalExtraKeyId.Drawer,
    TerminalExtraKeyId.Scroll,
    -> TerminalInputModifiers()
    else -> TerminalInputModifiers()
  }
}

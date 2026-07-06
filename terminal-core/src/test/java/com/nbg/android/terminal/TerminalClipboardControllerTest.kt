package com.nbg.android.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalClipboardControllerTest {
  @Test
  fun copyWritesSelectedTerminalTextToClipboard() {
    val clipboard = FakeTerminalClipboard()
    val pasted = mutableListOf<String>()
    val controller = TerminalClipboardController(clipboard) { pasted += it }

    controller.copy("selected output")

    assertEquals("selected output", clipboard.text)
    assertEquals(emptyList<String>(), pasted)
  }

  @Test
  fun pasteSendsClipboardTextToTerminal() {
    val clipboard = FakeTerminalClipboard("echo nbg")
    val pasted = mutableListOf<String>()
    val controller = TerminalClipboardController(clipboard) { pasted += it }

    controller.paste()

    assertEquals(listOf("echo nbg"), pasted)
  }

  @Test
  fun pasteIgnoresNullOrEmptyClipboardText() {
    val pasted = mutableListOf<String>()

    TerminalClipboardController(FakeTerminalClipboard(null)) { pasted += it }.paste()
    TerminalClipboardController(FakeTerminalClipboard("")) { pasted += it }.paste()

    assertEquals(emptyList<String>(), pasted)
  }

  @Test
  fun pastePreservesWhitespaceOnlyClipboardText() {
    val pasted = mutableListOf<String>()

    TerminalClipboardController(FakeTerminalClipboard("  \n\t")) { pasted += it }.paste()

    assertEquals(listOf("  \n\t"), pasted)
  }

  private class FakeTerminalClipboard(
    var text: String? = null,
  ) : TerminalClipboard {
    override fun readText(): String? = text

    override fun writeText(text: String) {
      this.text = text
    }
  }
}

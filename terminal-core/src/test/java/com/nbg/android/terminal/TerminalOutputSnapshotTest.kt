package com.nbg.android.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TerminalOutputSnapshotTest {
  @Test
  fun ptyResizeNotifiesForegroundProcessGroup() {
    val ptySource = java.io.File("src/main/cpp/pty.c").readText()

    assertEquals(1, Regex("#include <signal.h>").findAll(ptySource).count())
    assertEquals(1, Regex("kill\\(pid, SIGWINCH\\)").findAll(ptySource).count())
  }

  @Test
  fun terminalSessionUsesTermuxBackedScreenAndResizeBoundary() {
    val sessionSource = java.io.File("src/main/java/com/nbg/android/terminal/TerminalSession.kt").readText()

    assertEquals(1, Regex("val termuxSession: TermuxTerminalSession").findAll(sessionSource).count())
    assertEquals(1, Regex("getScreen\\(\\)\\?\\.getTranscriptText\\(\\)").findAll(sessionSource).count())
    assertEquals(1, Regex("termuxSession\\.updateSize\\(cols\\.coerceAtLeast\\(1\\), rows\\.coerceAtLeast\\(1\\)\\)").findAll(sessionSource).count())
    assertEquals(1, Regex("TerminalInputSequence\\.paste\\(text, termuxModes\\(\\)\\)").findAll(sessionSource).count())
    assertEquals(1, Regex("NBG_RESIZE").findAll(sessionSource).count())
    assertFalse(sessionSource.contains("screenBufferLock"))
  }

  @Test
  fun fromBufferCarriesRenderedTextAndAttributedCells() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 12)
    buffer.append("\u001B[32mok\u001B[0m")

    val snapshot = TerminalOutputSnapshot.from(buffer)

    assertEquals("ok\n", snapshot.text)
    assertEquals('o', snapshot.rows[0][0].char)
    assertEquals(TerminalColor.StandardGreen, snapshot.rows[0][0].attributes.foreground)
    assertEquals(TerminalColor.DefaultForeground, snapshot.rows[0][2].attributes.foreground)
  }

  @Test
  fun fromBufferCarriesCursorPosition() {
    val buffer = TerminalScreenBuffer(rows = 3, cols = 12)

    buffer.append("root# ")

    val snapshot = TerminalOutputSnapshot.from(buffer)

    assertEquals(TerminalCursor(row = 0, col = 6), snapshot.cursor)
  }

  @Test
  fun cursorPositionFollowsAnsiCursorMovement() {
    val buffer = TerminalScreenBuffer(rows = 3, cols = 12)

    buffer.append("one\ntwo\u001B[1;3H")

    val snapshot = TerminalOutputSnapshot.from(buffer)

    assertEquals(TerminalCursor(row = 0, col = 2), snapshot.cursor)
  }

  @Test
  fun fromBufferIncludesScrollbackRowsBeforeVisibleScreen() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("one\r\ntwo\r\nthree")

    val snapshot = TerminalOutputSnapshot.from(buffer)

    assertEquals("one", snapshot.rows[0].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals("two", snapshot.rows[1].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals("three", snapshot.rows[2].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals(TerminalCursor(row = 2, col = 5), snapshot.cursor)
  }

  @Test
  fun fromBufferExcludesMainScrollbackWhileAlternateScreenIsActive() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 12)

    buffer.append("main-1\r\nmain-2\r\nmain-3")
    buffer.append("\u001B[?1049h")
    buffer.append("alt")

    val snapshot = TerminalOutputSnapshot.from(buffer)

    assertEquals("alt", snapshot.rows[0].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals("", snapshot.rows[1].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals(2, snapshot.rows.size)
    assertEquals(TerminalCursor(row = 0, col = 3), snapshot.cursor)
  }
}

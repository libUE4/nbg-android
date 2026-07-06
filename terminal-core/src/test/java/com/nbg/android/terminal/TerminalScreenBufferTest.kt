package com.nbg.android.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalScreenBufferTest {
  @Test
  fun carriageReturnOverwritesCurrentLineLikeProgressOutput() {
    val buffer = TerminalScreenBuffer(rows = 4, cols = 20)

    buffer.append("Downloading 10%\rDownloading 90%")

    assertEquals("Downloading 90%", buffer.renderText().trimEnd())
  }

  @Test
  fun eraseLineClearsStaleCharactersAfterShorterReplacement() {
    val buffer = TerminalScreenBuffer(rows = 4, cols = 20)

    buffer.append("Downloading 100%\rDone\u001B[K")

    assertEquals("Done", buffer.renderText().trimEnd())
  }

  @Test
  fun clearScreenResetsCursorToTopLeft() {
    val buffer = TerminalScreenBuffer(rows = 4, cols = 20)

    buffer.append("one\ntwo\u001B[2J\u001B[Hfresh")

    assertEquals("fresh", buffer.renderText().trimEnd())
  }

  @Test
  fun eraseSavedLinesClearsScrollbackWithoutClearingVisibleScreen() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 12)

    buffer.append("old-1\r\nold-2\r\ncurrent")
    buffer.append("\u001B[3J")

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals(2, snapshot.rows.size)
    assertEquals("old-2", snapshot.rows[0].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals("current", snapshot.rows[1].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals(TerminalCursor(row = 1, col = 7), snapshot.cursor)
  }

  @Test
  fun oscTitleSequencesDoNotRenderAsPromptText() {
    val buffer = TerminalScreenBuffer(rows = 4, cols = 40)

    buffer.append("\u001B]0;root@localhost: ~\u0007root@localhost:~# ")

    assertEquals("root@localhost:~#", buffer.renderText().trimEnd())
  }

  @Test
  fun oscSequencesTerminatedByStringTerminatorDoNotRender() {
    val buffer = TerminalScreenBuffer(rows = 4, cols = 40)

    buffer.append("\u001B]2;terminal-title\u001B\\ready")

    assertEquals("ready", buffer.renderText().trimEnd())
  }

  @Test
  fun resizeChangesScreenDimensionsAndClampsCursor() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("abcd")
    buffer.resize(rows = 3, cols = 4)
    buffer.append("efgh")

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals(3, snapshot.rows.size)
    assertEquals(4, snapshot.rows.first().size)
    assertEquals(TerminalCursor(row = 1, col = 4), snapshot.cursor)
    assertEquals("abcd\nefgh", snapshot.text.trimEnd())
  }

  @Test
  fun resizeToTallerViewportKeepsVisibleScreenPinnedToTop() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 12)

    buffer.append("top\r\nprompt")

    buffer.resize(rows = 5, cols = 12)

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals(5, snapshot.rows.size)
    assertEquals("top", snapshot.rows[0].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals("prompt", snapshot.rows[1].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals("", snapshot.rows[2].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals("", snapshot.rows[3].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals("", snapshot.rows[4].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals(TerminalCursor(row = 1, col = 6), snapshot.cursor)
  }

  @Test
  fun resizePreservesAlternateScreenContent() {
    val buffer = TerminalScreenBuffer(rows = 3, cols = 12)

    buffer.append("main")
    buffer.append("\u001B[?1049h")
    buffer.append("alt-one\r\nalt-two")

    buffer.resize(rows = 5, cols = 20)

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals(5, snapshot.rows.size)
    assertEquals("alt-one", snapshot.rows[0].joinToString("") { it.char.toString() }.trimEnd())
    assertEquals("alt-two", snapshot.rows[1].joinToString("") { it.char.toString() }.trimEnd())
  }

  @Test
  fun wideCharactersOccupyTwoTerminalColumns() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("输入a")

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals(TerminalCursor(row = 0, col = 5), snapshot.cursor)
    assertEquals('输', snapshot.rows[0][0].char)
    assertEquals(TerminalCellRole.WideTrailing, snapshot.rows[0][1].role)
    assertEquals('入', snapshot.rows[0][2].char)
    assertEquals(TerminalCellRole.WideTrailing, snapshot.rows[0][3].role)
    assertEquals('a', snapshot.rows[0][4].char)
  }

  @Test
  fun combiningMarksDoNotAdvanceCursorOrShiftFollowingText() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("e\u0301x")

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals(TerminalCursor(row = 0, col = 2), snapshot.cursor)
    assertEquals('e', snapshot.rows[0][0].char)
    assertEquals('x', snapshot.rows[0][1].char)
  }

  @Test
  fun overwritingWideCharacterHeadClearsTrailingCell() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("输\u001B[1;1Ha")

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals("a", buffer.renderText().trimEnd())
    assertEquals('a', snapshot.rows[0][0].char)
    assertEquals(' ', snapshot.rows[0][1].char)
    assertEquals(TerminalCellRole.Normal, snapshot.rows[0][1].role)
  }

  @Test
  fun overwritingWideCharacterTrailingCellClearsWideHead() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("输\u001B[1;2Ha")

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals(" a", buffer.renderText().trimEnd())
    assertEquals(' ', snapshot.rows[0][0].char)
    assertEquals(TerminalCellRole.Normal, snapshot.rows[0][0].role)
    assertEquals('a', snapshot.rows[0][1].char)
  }

  @Test
  fun xtermAlternateScreenRestoresMainScreenOnExit() {
    val buffer = TerminalScreenBuffer(rows = 3, cols = 12)

    buffer.append("main")
    buffer.append("\u001B[?1049h")
    buffer.append("alt")
    assertEquals("alt", buffer.renderText().trimEnd())

    buffer.append("\u001B[?1049l")

    assertEquals("main", buffer.renderText().trimEnd())
    assertEquals(TerminalCursor(row = 0, col = 4), TerminalOutputSnapshot.from(buffer).cursor)
  }

  @Test
  fun saveAndRestoreCursorPosition() {
    val buffer = TerminalScreenBuffer(rows = 3, cols = 12)

    buffer.append("ab\u001B[s\u001B[2;5Hxy\u001B[uZ")

    assertEquals("abZ\n    xy", buffer.renderText().trimEnd())
    assertEquals(TerminalCursor(row = 0, col = 3), TerminalOutputSnapshot.from(buffer).cursor)
  }

  @Test
  fun cursorPositionEmptyParametersDefaultIndividuallyToOne() {
    val buffer = TerminalScreenBuffer(rows = 6, cols = 8)

    buffer.append("\u001B[;5HX")
    buffer.append("\u001B[3;HY")

    assertEquals("    X\n\nY", buffer.renderText().trimEnd())
    assertEquals(TerminalCursor(row = 2, col = 1), TerminalOutputSnapshot.from(buffer).cursor)
  }

  @Test
  fun csiZeroCountParametersDefaultToOneForMovementAndEditing() {
    val buffer = TerminalScreenBuffer(rows = 3, cols = 8)

    buffer.append("abcdef\u001B[0D")
    buffer.append("X")
    buffer.append("\u001B[0GY")
    buffer.append("\u001B[3G\u001B[0P")

    assertEquals("YbdeX", buffer.renderText().trimEnd())
  }

  @Test
  fun cursorMovementAfterFullLineCancelsPendingAutoWrap() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 4)

    buffer.append("abcd\u001B[DZ")

    assertEquals("abcZ", buffer.renderText().trimEnd())
    assertEquals(TerminalCursor(row = 0, col = 4), TerminalOutputSnapshot.from(buffer).cursor)
  }

  @Test
  fun privateCursorVisibilityModeUpdatesSnapshot() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    assertEquals(true, TerminalOutputSnapshot.from(buffer).cursor.visible)

    buffer.append("\u001B[?25l")
    assertEquals(false, TerminalOutputSnapshot.from(buffer).cursor.visible)

    buffer.append("\u001B[?25h")
    assertEquals(true, TerminalOutputSnapshot.from(buffer).cursor.visible)
  }

  @Test
  fun applicationCursorModeUpdatesTerminalModes() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    assertEquals(false, buffer.snapshotModes().applicationCursorKeys)

    buffer.append("\u001B[?1h")
    assertEquals(true, buffer.snapshotModes().applicationCursorKeys)

    buffer.append("\u001B[?1l")
    assertEquals(false, buffer.snapshotModes().applicationCursorKeys)
  }

  @Test
  fun bracketedPasteModeUpdatesTerminalModes() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    assertEquals(false, buffer.snapshotModes().bracketedPaste)

    buffer.append("\u001B[?2004h")
    assertEquals(true, buffer.snapshotModes().bracketedPaste)

    buffer.append("\u001B[?2004l")
    assertEquals(false, buffer.snapshotModes().bracketedPaste)
  }

  @Test
  fun multiplePrivateModesInOneCsiSequenceAllUpdateTerminalModes() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("\u001B[?1;2004h")

    assertEquals(true, buffer.snapshotModes().applicationCursorKeys)
    assertEquals(true, buffer.snapshotModes().bracketedPaste)

    buffer.append("\u001B[?1;2004l")

    assertEquals(false, buffer.snapshotModes().applicationCursorKeys)
    assertEquals(false, buffer.snapshotModes().bracketedPaste)
  }

  @Test
  fun applicationKeypadModeUpdatesTerminalModes() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    assertEquals(false, buffer.snapshotModes().applicationKeypad)

    buffer.append("\u001B=")
    assertEquals(true, buffer.snapshotModes().applicationKeypad)

    buffer.append("\u001B>")
    assertEquals(false, buffer.snapshotModes().applicationKeypad)
  }

  @Test
  fun insertCharactersShiftsTextRightOnCurrentLine() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("abef\u001B[1;3H\u001B[2@cd")

    assertEquals("abcdef", buffer.renderText().trimEnd())
  }

  @Test
  fun insertCharactersDoesNotLeaveWideTrailingCellsBehind() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("a输b\u001B[1;3H\u001B[@")

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals("a   b", buffer.renderText().trimEnd())
    assertEquals(TerminalCellRole.Normal, snapshot.rows[0][2].role)
    assertEquals(TerminalCellRole.Normal, snapshot.rows[0][3].role)
  }

  @Test
  fun deleteCharactersShiftsTextLeftOnCurrentLine() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("abcdef\u001B[1;3H\u001B[2P")

    assertEquals("abef", buffer.renderText().trimEnd())
  }

  @Test
  fun eraseCharactersClearsCellsWithoutMovingFollowingText() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("abcdef\u001B[1;3H\u001B[2X")

    assertEquals("ab  ef", buffer.renderText().trimEnd())
  }

  @Test
  fun eraseCharactersClearsWholeWideCharacter() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("a输b\u001B[1;2H\u001B[X")

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals("a  b", buffer.renderText().trimEnd())
    assertEquals(TerminalCellRole.Normal, snapshot.rows[0][1].role)
    assertEquals(TerminalCellRole.Normal, snapshot.rows[0][2].role)
  }

  @Test
  fun deleteCharactersDoesNotLeaveWideTrailingCellsBehind() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 8)

    buffer.append("a输bc\u001B[1;2H\u001B[P")

    val snapshot = TerminalOutputSnapshot.from(buffer)
    assertEquals("abc", buffer.renderText().trimEnd())
    assertEquals(TerminalCellRole.Normal, snapshot.rows[0][1].role)
    assertEquals('b', snapshot.rows[0][1].char)
  }

  @Test
  fun insertLinesShiftsRowsDownFromCursor() {
    val buffer = TerminalScreenBuffer(rows = 4, cols = 8)

    buffer.append("one\r\nthree\r\nfour\u001B[2;1H\u001B[Ltwo")

    assertEquals("one\ntwo\nthree\nfour", buffer.renderText().trimEnd())
  }

  @Test
  fun deleteLinesShiftsRowsUpFromCursor() {
    val buffer = TerminalScreenBuffer(rows = 4, cols = 8)

    buffer.append("one\r\ntwo\r\nthree\r\nfour\u001B[2;1H\u001B[M")

    assertEquals("one\nthree\nfour", buffer.renderText().trimEnd())
  }

  @Test
  fun lineFeedScrollsOnlyInsideConfiguredScrollRegion() {
    val buffer = TerminalScreenBuffer(rows = 5, cols = 8)

    buffer.append("top\r\none\r\ntwo\r\nthree\r\nbottom")
    buffer.append("\u001B[2;4r\u001B[4;1HX\u001B[K\r\nY")

    assertEquals("top\ntwo\nX\nY\nbottom", buffer.renderText().trimEnd())
  }

  @Test
  fun scrollUpAndDownCommandsRespectScrollRegion() {
    val buffer = TerminalScreenBuffer(rows = 5, cols = 8)

    buffer.append("top\r\none\r\ntwo\r\nthree\r\nbottom")
    buffer.append("\u001B[2;4r\u001B[2;1H\u001B[S")
    assertEquals("top\ntwo\nthree\n\nbottom", buffer.renderText().trimEnd())

    buffer.append("\u001B[T")
    assertEquals("top\n\ntwo\nthree\nbottom", buffer.renderText().trimEnd())
  }

  @Test
  fun resetScrollRegionRestoresFullScreenScrolling() {
    val buffer = TerminalScreenBuffer(rows = 3, cols = 8)

    buffer.append("one\r\ntwo\r\nthree")
    buffer.append("\u001B[2;3r\u001B[r\u001B[3;1H\u001B[Kfour\r\nfive")

    assertEquals("two\nfour\nfive", buffer.renderText().trimEnd())
  }

  @Test
  fun originModePositionsCursorRelativeToScrollRegion() {
    val buffer = TerminalScreenBuffer(rows = 5, cols = 8)

    buffer.append("\u001B[3;5r\u001B[?6h\u001B[1;1Hregion")

    assertEquals("\n\nregion", buffer.renderText().trimEnd())
    assertEquals(TerminalCursor(row = 2, col = 6), TerminalOutputSnapshot.from(buffer).cursor)
  }

  @Test
  fun disablingOriginModeReturnsCursorPositioningToFullScreen() {
    val buffer = TerminalScreenBuffer(rows = 5, cols = 8)

    buffer.append("\u001B[3;5r\u001B[?6h\u001B[1;1Hregion")
    buffer.append("\u001B[?6l\u001B[1;1Htop")

    assertEquals("top\n\nregion", buffer.renderText().trimEnd())
  }

  @Test
  fun autoWrapModeCanBeDisabledAndReenabled() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 4)

    buffer.append("\u001B[?7labcdef")
    assertEquals("abcf", buffer.renderText().trimEnd())
    assertEquals(TerminalCursor(row = 0, col = 3), TerminalOutputSnapshot.from(buffer).cursor)

    buffer.append("\u001B[?7hgh")

    assertEquals("abcg\nh", buffer.renderText().trimEnd())
    assertEquals(TerminalCursor(row = 1, col = 1), TerminalOutputSnapshot.from(buffer).cursor)
  }

  @Test
  fun decSpecialGraphicsCharsetMapsLineDrawingCharacters() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 16)

    buffer.append("\u001B(0lqk\u001B(B ascii")

    assertEquals("┌─┐ ascii", buffer.renderText().trimEnd())
  }

  @Test
  fun decSpecialGraphicsMapsBoxDrawingCornersAndLines() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 16)

    buffer.append("\u001B(0xjmq\u001B(B")

    assertEquals("│┘└─", buffer.renderText().trimEnd())
  }
}

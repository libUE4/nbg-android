package com.nbg.android.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCellAttributesTest {
  @Test
  fun sgrForegroundAndBoldApplyOnlyUntilReset() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 12)

    buffer.append("\u001B[31;1mred\u001B[0m plain")

    val cells = buffer.snapshotRows()
    assertEquals('r', cells[0][0].char)
    assertEquals(TerminalColor.StandardRed, cells[0][0].attributes.foreground)
    assertTrue(cells[0][0].attributes.bold)

    assertEquals(' ', cells[0][3].char)
    assertEquals(TerminalColor.DefaultForeground, cells[0][3].attributes.foreground)
    assertFalse(cells[0][3].attributes.bold)
  }

  @Test
  fun sgrUnderlineAndBackgroundAreStoredPerCell() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 12)

    buffer.append("\u001B[4;44mlink")

    val cell = buffer.snapshotRows()[0][0]
    assertEquals('l', cell.char)
    assertTrue(cell.attributes.underline)
    assertEquals(TerminalColor.StandardBlue, cell.attributes.background)
  }

  @Test
  fun sgr256AndRgbColorsAreStored() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 12)

    buffer.append("\u001B[38;5;196mA\u001B[48;2;12;34;56mB")

    val cells = buffer.snapshotRows()
    assertEquals(TerminalColor.Indexed(196), cells[0][0].attributes.foreground)
    assertEquals(TerminalColor.Rgb(12, 34, 56), cells[0][1].attributes.background)
  }

  @Test
  fun sgrColonSeparatedRgbColorsAreStored() {
    val buffer = TerminalScreenBuffer(rows = 2, cols = 12)

    buffer.append("\u001B[38:2::12:34:56mA\u001B[48:2::90:80:70mB")

    val cells = buffer.snapshotRows()
    assertEquals(TerminalColor.Rgb(12, 34, 56), cells[0][0].attributes.foreground)
    assertEquals(TerminalColor.Rgb(90, 80, 70), cells[0][1].attributes.background)
  }
}

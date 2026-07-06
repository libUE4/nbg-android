package com.nbg.android.terminal

class TerminalScreenBuffer(
  rows: Int = DEFAULT_ROWS,
  cols: Int = DEFAULT_COLS,
) {
  private var rows: Int = rows.coerceAtLeast(1)
  private var cols: Int = cols.coerceAtLeast(1)
  private var cells: Array<Array<TerminalCell>> = Array(this.rows) { Array(this.cols) { TerminalCell() } }
  private val scrollbackRows = ArrayDeque<List<TerminalCell>>()
  private var cursorRow = 0
  private var cursorCol = 0
  private var cursorVisible = true
  private var escapeBuffer: StringBuilder? = null
  private var currentAttributes = TerminalAttributes()
  private var savedCursor: SavedCursor? = null
  private var mainScreen: ScreenState? = null
  private var scrollTop = 0
  private var scrollBottom = this.rows - 1
  private var originMode = false
  private var autoWrapMode = true
  private var pendingWrap = false
  private var applicationCursorKeys = false
  private var applicationKeypad = false
  private var bracketedPaste = false
  private var lineDrawingMode = false

  fun append(text: String) {
    text.forEach { char ->
      val escape = escapeBuffer
      if (escape != null) {
        escape.append(char)
        if (isEscapeComplete(escape)) {
          handleEscape(escape.toString())
          escapeBuffer = null
        }
        return@forEach
      }

      when (char) {
        '\u001B' -> escapeBuffer = StringBuilder().append(char)
        '\r' -> {
          cursorCol = 0
          pendingWrap = false
        }
        '\n' -> {
          lineFeed()
          pendingWrap = false
        }
        '\b' -> {
          cursorCol = (cursorCol - 1).coerceAtLeast(0)
          pendingWrap = false
        }
        '\t' -> repeat(TAB_WIDTH - (cursorCol % TAB_WIDTH)) { putChar(' ') }
        else -> if (!char.isISOControl()) putChar(mapCharacterSet(char))
      }
    }
  }

  fun renderText(): String = cells.joinToString("\n") { row ->
    row.joinToString(separator = "") { it.char.toString() }.trimEnd()
  }

  fun snapshotRows(): List<List<TerminalCell>> = cells.map { row -> row.map { it.copy() } }

  fun snapshotRowsWithScrollback(): List<List<TerminalCell>> =
    if (isAlternateScreenActive()) {
      snapshotRows()
    } else {
      scrollbackRows.map { row -> row.map { it.copy() } } + snapshotRows()
    }

  fun snapshotCursor(): TerminalCursor =
    TerminalCursor(
      row = if (isAlternateScreenActive()) cursorRow else scrollbackRows.size + cursorRow,
      col = cursorCol,
      visible = cursorVisible,
    )

  fun snapshotModes(): TerminalModes = TerminalModes(
    applicationCursorKeys = applicationCursorKeys,
    bracketedPaste = bracketedPaste,
    applicationKeypad = applicationKeypad,
  )

  fun resize(rows: Int, cols: Int) {
    val nextRows = rows.coerceAtLeast(1)
    val nextCols = cols.coerceAtLeast(1)
    if (nextRows == this.rows && nextCols == this.cols) return

    if (isAlternateScreenActive()) {
      val nextCells = copyCells(cells, nextRows, nextCols)
      this.rows = nextRows
      this.cols = nextCols
      cells = nextCells
      scrollTop = 0
      scrollBottom = nextRows - 1
      cursorRow = cursorRow.coerceIn(0, nextRows - 1)
      cursorCol = cursorCol.coerceIn(0, nextCols)
      pendingWrap = false
      return
    }

    val nextCells = Array(nextRows) { Array(nextCols) { TerminalCell() } }
    val rowsToCopy = minOf(this.rows, nextRows)
    val colsToCopy = minOf(this.cols, nextCols)
    val sourceRowOffset = 0
    val targetRowOffset = 0
    for (row in 0 until rowsToCopy) {
      for (col in 0 until colsToCopy) {
        nextCells[targetRowOffset + row][col] = cells[sourceRowOffset + row][col]
      }
    }

    val nextCursorRow = (cursorRow - sourceRowOffset + targetRowOffset).coerceIn(0, nextRows - 1)
    this.rows = nextRows
    this.cols = nextCols
    cells = nextCells
    scrollTop = scrollTop.coerceIn(0, this.rows - 1)
    scrollBottom = scrollBottom.coerceIn(scrollTop, this.rows - 1)
    cursorRow = nextCursorRow
    cursorCol = cursorCol.coerceIn(0, this.cols)
    pendingWrap = false
  }

  private fun isAlternateScreenActive(): Boolean = mainScreen != null

  private fun putChar(char: Char) {
    val width = terminalCharWidth(char)
    if (width == 0) return
    if (pendingWrap && autoWrapMode) {
      cursorCol = 0
      lineFeed()
      pendingWrap = false
    }
    if (cursorCol >= cols && autoWrapMode) {
      cursorCol = 0
      lineFeed()
    }
    if (width == 2 && cursorCol == cols - 1) {
      if (autoWrapMode) {
        cursorCol = 0
        lineFeed()
      } else {
        cursorCol = cols - 1
      }
    }
    val targetCol = cursorCol.coerceIn(0, cols - 1)
    clearWideCharacterAt(cursorRow, targetCol)
    if (width == 2 && targetCol + 1 < cols) {
      clearWideCharacterAt(cursorRow, targetCol + 1)
    }
    cells[cursorRow][targetCol] = TerminalCell(char, currentAttributes)
    cursorCol = if (autoWrapMode) {
      (cursorCol + 1).coerceAtMost(cols)
    } else {
      (cursorCol + 1).coerceAtMost(cols - 1)
    }
    pendingWrap = autoWrapMode && cursorCol >= cols
    if (width == 2 && cursorCol < cols) {
      cells[cursorRow][cursorCol] = TerminalCell(' ', currentAttributes, TerminalCellRole.WideTrailing)
      cursorCol += 1
    }
  }

  private fun lineFeed() {
    if (cursorRow == scrollBottom) {
      scrollRegionUp(scrollTop, scrollBottom)
    } else {
      cursorRow = (cursorRow + 1).coerceAtMost(rows - 1)
    }
  }

  private fun isEscapeComplete(sequence: StringBuilder): Boolean {
    if (sequence.length < 2) return false
    if (sequence[1] == ']') {
      return sequence.last() == '\u0007' ||
        (sequence.length >= 4 && sequence[sequence.length - 2] == '\u001B' && sequence.last() == '\\')
    }
    if (sequence[1] == '(') return sequence.length >= 3
    if (sequence[1] != '[') return true
    if (sequence.length < 3) return false
    return sequence.last() in '@'..'~'
  }

  private fun handleEscape(sequence: String) {
    if (sequence.startsWith("\u001B(")) {
      lineDrawingMode = sequence.getOrNull(2) == '0'
      return
    }
    if (sequence == "\u001B=" || sequence == "\u001B>") {
      applicationKeypad = sequence == "\u001B="
      return
    }
    if (!sequence.startsWith("\u001B[")) return

    val command = sequence.last()
    val parameterText = sequence.substring(2, sequence.length - 1)
    val privateMode = parameterText.startsWith('?')
    val params = parseCsiParameters(parameterText)

    when (command) {
      'H', 'f' -> moveCursor(
        row = cursorAddressRow(cursorPositionParameter(parameterText, 0) - 1),
        col = cursorPositionParameter(parameterText, 1) - 1,
      )
      'A' -> moveCursor(row = (cursorRow - csiDefaultOne(params)).coerceAtLeast(0), col = cursorCol)
      'B' -> moveCursor(row = (cursorRow + csiDefaultOne(params)).coerceAtMost(rows - 1), col = cursorCol)
      'C' -> moveCursor(row = cursorRow, col = (cursorCol + csiDefaultOne(params)).coerceAtMost(cols - 1))
      'D' -> moveCursor(row = cursorRow, col = (cursorCol - csiDefaultOne(params)).coerceAtLeast(0))
      'G' -> moveCursor(row = cursorRow, col = (csiDefaultOne(params) - 1).coerceIn(0, cols - 1))
      'J' -> handleEraseDisplay(params.firstOrNull() ?: 0)
      'K' -> handleEraseLine(params.firstOrNull() ?: 0)
      'm' -> handleGraphicRendition(params)
      'h' -> handleMode(params, privateMode, enabled = true)
      'l' -> handleMode(params, privateMode, enabled = false)
      's' -> saveCursor()
      'u' -> restoreCursor()
      '@' -> insertCharacters(csiDefaultOne(params))
      'P' -> deleteCharacters(csiDefaultOne(params))
      'X' -> eraseCharacters(csiDefaultOne(params))
      'L' -> insertLines(csiDefaultOne(params))
      'M' -> deleteLines(csiDefaultOne(params))
      'S' -> scrollRegionUp(scrollTop, scrollBottom, csiDefaultOne(params))
      'T' -> scrollRegionDown(scrollTop, scrollBottom, csiDefaultOne(params))
      'r' -> setScrollRegion(params)
    }
  }

  private fun moveCursor(row: Int, col: Int) {
    cursorRow = row.coerceIn(0, rows - 1)
    cursorCol = col.coerceIn(0, cols)
    pendingWrap = false
  }

  private fun cursorAddressRow(row: Int): Int =
    if (originMode) scrollTop + row else row

  private fun handleEraseDisplay(mode: Int) {
    when (mode) {
      3 -> {
        scrollbackRows.clear()
      }
      2 -> {
        for (row in 0 until rows) clearRow(row)
        moveCursor(0, 0)
      }
      1 -> {
        for (row in 0..cursorRow) {
          val endCol = if (row == cursorRow) cursorCol else cols - 1
          for (col in 0..endCol) clearCell(row, col)
        }
      }
      else -> {
        for (row in cursorRow until rows) {
          val startCol = if (row == cursorRow) cursorCol else 0
          for (col in startCol until cols) clearCell(row, col)
        }
      }
    }
  }

  private fun handleEraseLine(mode: Int) {
    when (mode) {
      1 -> for (col in 0..cursorCol) clearCell(cursorRow, col)
      2 -> clearRow(cursorRow)
      else -> for (col in cursorCol until cols) clearCell(cursorRow, col)
    }
  }

  private fun insertCharacters(count: Int) {
    val safeCount = count.coerceAtLeast(1).coerceAtMost(cols - cursorCol)
    for (col in (cursorCol + safeCount until cols).reversed()) {
      cells[cursorRow][col] = cells[cursorRow][col - safeCount]
    }
    for (col in cursorCol until cursorCol + safeCount) {
      clearCell(cursorRow, col)
    }
    normalizeWideCharactersInRow(cursorRow)
  }

  private fun deleteCharacters(count: Int) {
    val safeCount = count.coerceAtLeast(1).coerceAtMost(cols - cursorCol)
    val deleteEndExclusive = expandedWideCharacterEnd(cursorRow, cursorCol, cursorCol + safeCount)
    val shiftCount = (deleteEndExclusive - cursorCol).coerceAtLeast(safeCount)
    for (col in cursorCol until cursorCol + safeCount) {
      clearWideCharacterAt(cursorRow, col)
    }
    for (col in cursorCol until cols - shiftCount) {
      cells[cursorRow][col] = cells[cursorRow][col + shiftCount]
    }
    for (col in cols - shiftCount until cols) {
      clearCell(cursorRow, col)
    }
    normalizeWideCharactersInRow(cursorRow)
  }

  private fun eraseCharacters(count: Int) {
    val safeCount = count.coerceAtLeast(1).coerceAtMost(cols - cursorCol)
    for (col in cursorCol until cursorCol + safeCount) {
      clearWideCharacterAt(cursorRow, col)
    }
  }

  private fun insertLines(count: Int) {
    if (cursorRow !in scrollTop..scrollBottom) return
    val safeCount = count.coerceAtLeast(1).coerceAtMost(scrollBottom - cursorRow + 1)
    for (row in (cursorRow + safeCount..scrollBottom).reversed()) {
      cells[row] = cells[row - safeCount]
    }
    for (row in cursorRow until cursorRow + safeCount) {
      cells[row] = Array(cols) { TerminalCell() }
    }
  }

  private fun deleteLines(count: Int) {
    if (cursorRow !in scrollTop..scrollBottom) return
    val safeCount = count.coerceAtLeast(1).coerceAtMost(scrollBottom - cursorRow + 1)
    for (row in cursorRow..scrollBottom - safeCount) {
      cells[row] = cells[row + safeCount]
    }
    for (row in scrollBottom - safeCount + 1..scrollBottom) {
      cells[row] = Array(cols) { TerminalCell() }
    }
  }

  private fun scrollRegionUp(top: Int, bottom: Int, count: Int = 1) {
    val safeCount = count.coerceAtLeast(1).coerceAtMost(bottom - top + 1)
    if (top == 0 && bottom == rows - 1) {
      repeat(safeCount) { appendScrollback(cells[it]) }
    }
    for (row in top..bottom - safeCount) {
      cells[row] = cells[row + safeCount]
    }
    for (row in bottom - safeCount + 1..bottom) {
      cells[row] = Array(cols) { TerminalCell() }
    }
  }

  private fun scrollRegionDown(top: Int, bottom: Int, count: Int = 1) {
    val safeCount = count.coerceAtLeast(1).coerceAtMost(bottom - top + 1)
    for (row in (top + safeCount..bottom).reversed()) {
      cells[row] = cells[row - safeCount]
    }
    for (row in top until top + safeCount) {
      cells[row] = Array(cols) { TerminalCell() }
    }
  }

  private fun setScrollRegion(params: List<Int>) {
    val top = ((params.getOrNull(0) ?: 1) - 1).coerceIn(0, rows - 1)
    val bottom = ((params.getOrNull(1) ?: rows) - 1).coerceIn(0, rows - 1)
    if (top < bottom) {
      scrollTop = top
      scrollBottom = bottom
    } else {
      scrollTop = 0
      scrollBottom = rows - 1
    }
    moveCursor(0, 0)
  }

  private fun handleMode(params: List<Int>, privateMode: Boolean, enabled: Boolean) {
    if (!privateMode) return
    params.forEach { param ->
      when (param) {
        1 -> applicationCursorKeys = enabled
        6 -> {
          originMode = enabled
          moveCursor(cursorAddressRow(0), 0)
        }
        7 -> autoWrapMode = enabled
        25 -> cursorVisible = enabled
        1049 -> toggleAlternateScreen(enabled)
        2004 -> bracketedPaste = enabled
      }
    }
  }

  private fun toggleAlternateScreen(enabled: Boolean) {
    if (enabled) {
      if (mainScreen != null) return
      mainScreen = captureScreen()
      cells = Array(rows) { Array(cols) { TerminalCell() } }
      cursorRow = 0
      cursorCol = 0
      scrollTop = 0
      scrollBottom = rows - 1
      originMode = false
      autoWrapMode = true
      applicationCursorKeys = false
      applicationKeypad = false
      bracketedPaste = false
      lineDrawingMode = false
      currentAttributes = TerminalAttributes()
      savedCursor = null
    } else {
      val restored = mainScreen ?: return
      restoreScreen(restored)
      mainScreen = null
    }
  }

  private fun appendScrollback(row: Array<TerminalCell>) {
    scrollbackRows += row.map { it.copy() }
    while (scrollbackRows.size > MAX_SCROLLBACK_ROWS) {
      scrollbackRows.removeFirst()
    }
  }

  private fun saveCursor() {
    savedCursor = SavedCursor(cursorRow, cursorCol, currentAttributes, cursorVisible)
  }

  private fun restoreCursor() {
    val saved = savedCursor ?: return
    cursorRow = saved.row.coerceIn(0, rows - 1)
    cursorCol = saved.col.coerceIn(0, cols)
    currentAttributes = saved.attributes
    cursorVisible = saved.visible
  }

  private fun captureScreen(): ScreenState =
    ScreenState(
      cells = copyCells(cells, rows, cols),
      cursor = SavedCursor(cursorRow, cursorCol, currentAttributes, cursorVisible),
      savedCursor = savedCursor,
      scrollRegion = ScrollRegion(scrollTop, scrollBottom),
      originMode = originMode,
      autoWrapMode = autoWrapMode,
      applicationCursorKeys = applicationCursorKeys,
      applicationKeypad = applicationKeypad,
      bracketedPaste = bracketedPaste,
      lineDrawingMode = lineDrawingMode,
    )

  private fun restoreScreen(screen: ScreenState) {
    cells = copyCells(screen.cells, rows, cols)
    scrollTop = screen.scrollRegion.top.coerceIn(0, rows - 1)
    scrollBottom = screen.scrollRegion.bottom.coerceIn(scrollTop, rows - 1)
    originMode = screen.originMode
    autoWrapMode = screen.autoWrapMode
    applicationCursorKeys = screen.applicationCursorKeys
    applicationKeypad = screen.applicationKeypad
    bracketedPaste = screen.bracketedPaste
    lineDrawingMode = screen.lineDrawingMode
    cursorRow = screen.cursor.row.coerceIn(0, rows - 1)
    cursorCol = screen.cursor.col.coerceIn(0, cols)
    currentAttributes = screen.cursor.attributes
    cursorVisible = screen.cursor.visible
    savedCursor = screen.savedCursor
  }

  private fun copyCells(
    source: Array<Array<TerminalCell>>,
    targetRows: Int,
    targetCols: Int,
  ): Array<Array<TerminalCell>> {
    val copied = Array(targetRows) { Array(targetCols) { TerminalCell() } }
    val rowsToCopy = minOf(source.size, targetRows)
    for (row in 0 until rowsToCopy) {
      val colsToCopy = minOf(source[row].size, targetCols)
      for (col in 0 until colsToCopy) {
        copied[row][col] = source[row][col].copy()
      }
    }
    return copied
  }

  private fun handleGraphicRendition(params: List<Int>) {
    val values = if (params.isEmpty()) listOf(0) else params
    var index = 0
    while (index < values.size) {
      when (val param = values[index]) {
        0 -> currentAttributes = TerminalAttributes()
        1 -> currentAttributes = currentAttributes.copy(bold = true, dim = false)
        2 -> currentAttributes = currentAttributes.copy(dim = true, bold = false)
        3 -> currentAttributes = currentAttributes.copy(italic = true)
        4 -> currentAttributes = currentAttributes.copy(underline = true)
        7 -> currentAttributes = currentAttributes.copy(inverse = true)
        8 -> currentAttributes = currentAttributes.copy(hidden = true)
        9 -> currentAttributes = currentAttributes.copy(strikethrough = true)
        21, 22 -> currentAttributes = currentAttributes.copy(bold = false, dim = false)
        23 -> currentAttributes = currentAttributes.copy(italic = false)
        24 -> currentAttributes = currentAttributes.copy(underline = false)
        27 -> currentAttributes = currentAttributes.copy(inverse = false)
        28 -> currentAttributes = currentAttributes.copy(hidden = false)
        29 -> currentAttributes = currentAttributes.copy(strikethrough = false)
        in 30..37 -> currentAttributes = currentAttributes.copy(foreground = standardColor(param - 30))
        38 -> {
          parseExtendedColor(values, index + 1)?.let { parsed ->
            currentAttributes = currentAttributes.copy(foreground = parsed.color)
            index += parsed.consumed
          }
        }
        39 -> currentAttributes = currentAttributes.copy(foreground = TerminalColor.DefaultForeground)
        in 40..47 -> currentAttributes = currentAttributes.copy(background = standardColor(param - 40))
        48 -> {
          parseExtendedColor(values, index + 1)?.let { parsed ->
            currentAttributes = currentAttributes.copy(background = parsed.color)
            index += parsed.consumed
          }
        }
        49 -> currentAttributes = currentAttributes.copy(background = TerminalColor.DefaultBackground)
        in 90..97 -> currentAttributes = currentAttributes.copy(foreground = brightColor(param - 90))
        in 100..107 -> currentAttributes = currentAttributes.copy(background = brightColor(param - 100))
      }
      index += 1
    }
  }

  private fun parseCsiParameters(parameterText: String): List<Int> =
    parameterText
      .split(';')
      .flatMap { part -> part.trimStart('?').split(':') }
      .filter { it.isNotEmpty() }
      .mapNotNull { it.toIntOrNull() }

  private fun csiDefaultOne(params: List<Int>, index: Int = 0): Int =
    params.getOrNull(index)?.takeIf { it > 0 } ?: 1

  private fun cursorPositionParameter(parameterText: String, index: Int): Int {
    val value = parameterText
      .trimStart('?')
      .split(';')
      .getOrNull(index)
      ?.substringBefore(':')
      ?.takeIf { it.isNotEmpty() }
      ?.toIntOrNull()
    return value?.takeIf { it > 0 } ?: 1
  }

  private fun mapCharacterSet(char: Char): Char =
    if (lineDrawingMode) decSpecialGraphics(char) else char

  private fun decSpecialGraphics(char: Char): Char = when (char) {
    '`' -> '◆'
    'a' -> '▒'
    'b' -> '\t'
    'c' -> '\u000C'
    'd' -> '\r'
    'e' -> '\n'
    'f' -> '°'
    'g' -> '±'
    'h' -> '␤'
    'i' -> '\u000B'
    'j' -> '┘'
    'k' -> '┐'
    'l' -> '┌'
    'm' -> '└'
    'n' -> '┼'
    'o' -> '⎺'
    'p' -> '⎻'
    'q' -> '─'
    'r' -> '⎼'
    's' -> '⎽'
    't' -> '├'
    'u' -> '┤'
    'v' -> '┴'
    'w' -> '┬'
    'x' -> '│'
    'y' -> '≤'
    'z' -> '≥'
    '{' -> 'π'
    '|' -> '≠'
    '}' -> '£'
    '~' -> '·'
    else -> char
  }

  private fun clearRow(row: Int) {
    for (col in 0 until cols) clearCell(row, col)
  }

  private fun clearWideCharacterAt(row: Int, col: Int) {
    if (col !in 0 until cols) return
    if (cells[row][col].role == TerminalCellRole.WideTrailing) {
      clearCell(row, col)
      if (col > 0) clearCell(row, col - 1)
      return
    }
    if (col + 1 < cols && cells[row][col + 1].role == TerminalCellRole.WideTrailing) {
      clearCell(row, col + 1)
    }
    clearCell(row, col)
  }

  private fun expandedWideCharacterEnd(row: Int, startCol: Int, endExclusive: Int): Int {
    var end = endExclusive.coerceIn(startCol, cols)
    if (end < cols && end > 0 && cells[row][end].role == TerminalCellRole.WideTrailing) {
      end += 1
    }
    return end.coerceAtMost(cols)
  }

  private fun normalizeWideCharactersInRow(row: Int) {
    for (col in 0 until cols) {
      if (cells[row][col].role == TerminalCellRole.WideTrailing) {
        val hasWideHead = col > 0 && terminalCharWidth(cells[row][col - 1].char) == 2
        if (!hasWideHead) clearCell(row, col)
      } else if (terminalCharWidth(cells[row][col].char) == 2) {
        if (col + 1 >= cols || cells[row][col + 1].role != TerminalCellRole.WideTrailing) {
          clearCell(row, col)
        }
      }
    }
  }

  private fun clearCell(row: Int, col: Int) {
    cells[row][col] = TerminalCell()
  }

  private fun standardColor(index: Int): TerminalColor = when (index) {
    0 -> TerminalColor.StandardBlack
    1 -> TerminalColor.StandardRed
    2 -> TerminalColor.StandardGreen
    3 -> TerminalColor.StandardYellow
    4 -> TerminalColor.StandardBlue
    5 -> TerminalColor.StandardMagenta
    6 -> TerminalColor.StandardCyan
    else -> TerminalColor.StandardWhite
  }

  private fun brightColor(index: Int): TerminalColor = when (index) {
    0 -> TerminalColor.BrightBlack
    1 -> TerminalColor.BrightRed
    2 -> TerminalColor.BrightGreen
    3 -> TerminalColor.BrightYellow
    4 -> TerminalColor.BrightBlue
    5 -> TerminalColor.BrightMagenta
    6 -> TerminalColor.BrightCyan
    else -> TerminalColor.BrightWhite
  }

  private fun parseExtendedColor(params: List<Int>, startIndex: Int): ParsedColor? {
    if (startIndex >= params.size) return null
    return when (params[startIndex]) {
      5 -> {
        val colorIndex = params.getOrNull(startIndex + 1) ?: return null
        ParsedColor(TerminalColor.Indexed(colorIndex.coerceIn(0, 255)), consumed = 2)
      }
      2 -> {
        val red = params.getOrNull(startIndex + 1) ?: return null
        val green = params.getOrNull(startIndex + 2) ?: return null
        val blue = params.getOrNull(startIndex + 3) ?: return null
        ParsedColor(
          TerminalColor.Rgb(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255)),
          consumed = 4,
        )
      }
      else -> null
    }
  }

  companion object {
    private const val DEFAULT_ROWS = 40
    private const val DEFAULT_COLS = 100
    private const val TAB_WIDTH = 8
    private const val MAX_SCROLLBACK_ROWS = 10_000

    private fun terminalCharWidth(char: Char): Int {
      if (char == '\u0000') return 0
      val type = Character.getType(char)
      if (
        type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt()
      ) {
        return 0
      }
      return if (isWideChar(char)) 2 else 1
    }

    private fun isWideChar(char: Char): Boolean {
      val code = char.code
      return code in 0x1100..0x115F ||
        code in 0x2329..0x232A ||
        code in 0x2E80..0xA4CF ||
        code in 0xAC00..0xD7A3 ||
        code in 0xF900..0xFAFF ||
        code in 0xFE10..0xFE19 ||
        code in 0xFE30..0xFE6F ||
        code in 0xFF00..0xFF60 ||
        code in 0xFFE0..0xFFE6
    }
  }

  private data class ParsedColor(
    val color: TerminalColor,
    val consumed: Int,
  )

  private data class SavedCursor(
    val row: Int,
    val col: Int,
    val attributes: TerminalAttributes,
    val visible: Boolean,
  )

  private data class ScreenState(
    val cells: Array<Array<TerminalCell>>,
    val cursor: SavedCursor,
    val savedCursor: SavedCursor?,
    val scrollRegion: ScrollRegion,
    val originMode: Boolean,
    val autoWrapMode: Boolean,
    val applicationCursorKeys: Boolean,
    val applicationKeypad: Boolean,
    val bracketedPaste: Boolean,
    val lineDrawingMode: Boolean,
  )

  private data class ScrollRegion(
    val top: Int,
    val bottom: Int,
  )
}

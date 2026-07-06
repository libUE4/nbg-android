package com.nbg.android.terminal

data class TerminalOutputSnapshot(
  val text: String,
  val rows: List<List<TerminalCell>>,
  val cursor: TerminalCursor,
) {
  companion object {
    val Empty = TerminalOutputSnapshot("", emptyList(), TerminalCursor(row = 0, col = 0))

    fun from(buffer: TerminalScreenBuffer): TerminalOutputSnapshot =
      TerminalOutputSnapshot(
        text = buffer.renderText(),
        rows = buffer.snapshotRowsWithScrollback(),
        cursor = buffer.snapshotCursor(),
      )
  }
}

data class TerminalCursor(
  val row: Int,
  val col: Int,
  val visible: Boolean = true,
)

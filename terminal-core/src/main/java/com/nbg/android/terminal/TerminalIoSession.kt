package com.nbg.android.terminal

import java.io.InputStream
import java.io.OutputStream

class TerminalIoSession(
  val input: InputStream,
  private val output: OutputStream,
  private val resizePty: (rows: Int, cols: Int) -> Boolean,
  private val closePty: () -> Unit,
  private val alivePty: () -> Boolean = { true },
) {
  fun write(text: String) {
    output.write(text.toByteArray(Charsets.UTF_8))
    output.flush()
  }

  fun writeBytes(bytes: ByteArray) {
    output.write(bytes)
    output.flush()
  }

  fun resize(rows: Int, cols: Int): Boolean =
    resizePty(rows.coerceAtLeast(1), cols.coerceAtLeast(1))

  fun close() {
    closePty()
  }

  fun isAlive(): Boolean = alivePty()

  companion object {
    fun from(pty: PtyProcess): TerminalIoSession = TerminalIoSession(
      input = pty.input,
      output = pty.output,
      resizePty = pty::resize,
      closePty = pty::destroy,
      alivePty = pty::isAlive,
    )
  }
}

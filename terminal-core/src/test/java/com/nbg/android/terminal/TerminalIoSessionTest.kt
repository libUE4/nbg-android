package com.nbg.android.terminal

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalIoSessionTest {
  @Test
  fun writeDelegatesToOutputStream() {
    val output = ByteArrayOutputStream()
    val session = TerminalIoSession(
      input = ByteArrayInputStream(ByteArray(0)),
      output = output,
      resizePty = { _, _ -> true },
      closePty = {},
    )

    session.write("echo ok")

    assertEquals("echo ok", output.toString(Charsets.UTF_8.name()))
  }

  @Test
  fun resizeDelegatesRowsAndColumns() {
    var captured = 0 to 0
    val session = TerminalIoSession(
      input = ByteArrayInputStream(ByteArray(0)),
      output = ByteArrayOutputStream(),
      resizePty = { rows, cols ->
        captured = rows to cols
        true
      },
      closePty = {},
    )

    assertTrue(session.resize(rows = 32, cols = 120))

    assertEquals(32 to 120, captured)
  }

  @Test
  fun closeDelegatesToProcess() {
    var closed = false
    val session = TerminalIoSession(
      input = ByteArrayInputStream(ByteArray(0)),
      output = ByteArrayOutputStream(),
      resizePty = { _, _ -> true },
      closePty = { closed = true },
    )

    session.close()

    assertTrue(closed)
  }
}

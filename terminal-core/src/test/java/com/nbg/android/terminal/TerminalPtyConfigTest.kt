package com.nbg.android.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TerminalPtyConfigTest {
  @Test
  fun clampsInitialPtySize() {
    assertEquals(TerminalPtySize(rows = 1, cols = 1), TerminalPtySize(rows = 0, cols = -4).clamped())
    assertEquals(TerminalPtySize(rows = 30, cols = 84), TerminalPtySize(rows = 30, cols = 84).clamped())
  }

  @Test
  fun termuxSessionArgsIncludeArgvZeroBeforeShellOptions() {
    val command = "source /data/user/0/com.nbg.android/files/common.sh && start_shell"

    assertArrayEquals(
      arrayOf("bash", "-c", command),
      termuxShellCommandArgs("/data/user/0/com.nbg.android/files/usr/bin/bash", command),
    )
  }
}

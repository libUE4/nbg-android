package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentToolStatusStateTest {
  @Test
  fun invisibleToolStatusIsIgnored() {
    val state = NbgAgentToolStatusState()

    val result = state.enqueue(
      HanakoToolStatus(
        title = "",
        kind = "tool",
        running = false,
      ),
    )

    assertFalse(result.accepted)
    assertFalse(result.shouldFlushNow)
    assertFalse(state.hasPending)
    assertTrue(state.drain().isEmpty())
  }

  @Test
  fun runningToolStatusIsQueuedForDelayedFlush() {
    val state = NbgAgentToolStatusState()

    val result = state.enqueue(
      HanakoToolStatus(
        key = "terminal_status:generic",
        kind = "terminal",
        title = "Gradle build",
        detail = "./gradlew test",
        running = true,
      ),
    )

    assertTrue(result.accepted)
    assertFalse(result.shouldFlushNow)
    assertTrue(state.hasPending)

    val drained = state.drain()
    assertEquals(1, drained.size)
    assertEquals("terminal-command:./gradlew test", drained.single().key)
    assertFalse(state.hasPending)
  }

  @Test
  fun completedToolStatusRequestsImmediateFlush() {
    val state = NbgAgentToolStatusState()

    val result = state.enqueue(
      HanakoToolStatus(
        key = "file:/root/app.kt",
        kind = "file",
        title = "写入文件",
        filePath = "/root/app.kt",
        running = false,
        success = true,
      ),
    )

    assertTrue(result.accepted)
    assertTrue(result.shouldFlushNow)
    assertEquals("file:/root/app.kt", state.drain().single().key)
  }

  @Test
  fun toolStatusStateMergesUpdatesByStableKeyAndClearsAfterDrain() {
    val state = NbgAgentToolStatusState()
    val start = HanakoToolStatus(
      key = "terminal_status:generic",
      kind = "terminal",
      title = "Gradle build",
      detail = "./gradlew test",
      running = true,
      terminalOutput = HanakoTerminalOutput(sessionId = "term-1", output = "start\n"),
    )
    val end = start.copy(
      subtitle = "完成",
      running = false,
      success = true,
      terminalOutput = HanakoTerminalOutput(sessionId = "term-1", output = "BUILD SUCCESSFUL\n", exitCode = 0),
    )

    assertFalse(state.enqueue(start).shouldFlushNow)
    assertTrue(state.enqueue(end).shouldFlushNow)

    val drained = state.drain()
    assertEquals(1, drained.size)
    assertEquals("terminal:term-1", drained.single().key)
    assertEquals("完成", drained.single().subtitle)
    assertFalse(drained.single().running)
    assertEquals("start\nBUILD SUCCESSFUL\n", drained.single().terminalOutput?.output)
    assertFalse(state.hasPending)
    assertTrue(state.drain().isEmpty())
  }
}

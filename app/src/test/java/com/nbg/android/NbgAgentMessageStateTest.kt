package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentMessageStateTest {
  @Test
  fun appendMessageIndexesFirstDuplicateIdAndToolKey() {
    val state = NbgAgentMessageState()
    val first = NbgAgentMessage(
      id = 42L,
      role = NbgAgentRole.Tool,
      text = "running",
      toolStatus = HanakoToolStatus(key = "terminal:one", kind = "terminal", title = "Build", running = true),
    )
    val duplicate = first.copy(text = "duplicate")

    state.appendMessage(first)
    state.appendMessage(duplicate)

    assertEquals(0, state.findMessageIndexById(42L))
    assertEquals(0, state.findToolMessageIndexByKey("terminal:one"))
  }

  @Test
  fun setMessageAtUpdatesToolKeyIndexWithoutFullRebuildWhenIdIsStable() {
    val state = NbgAgentMessageState()
    state.appendMessage(
      NbgAgentMessage(
        id = 7L,
        role = NbgAgentRole.Tool,
        text = "old",
        toolStatus = HanakoToolStatus(key = "tool:old", kind = "tool", title = "Old", running = true),
      ),
    )

    state.setMessageAt(
      0,
      NbgAgentMessage(
        id = 7L,
        role = NbgAgentRole.Tool,
        text = "new",
        toolStatus = HanakoToolStatus(key = "tool:new", kind = "tool", title = "New", running = false),
      ),
    )

    assertEquals(-1, state.findToolMessageIndexByKey("tool:old"))
    assertEquals(0, state.findToolMessageIndexByKey("tool:new"))
    assertEquals(0, state.findMessageIndexById(7L))
  }

  @Test
  fun removeAndReplaceAllRebuildIndexes() {
    val state = NbgAgentMessageState()
    state.appendMessage(NbgAgentMessage(id = 1L, role = NbgAgentRole.User, text = "user"))
    state.appendMessage(
      NbgAgentMessage(
        id = 2L,
        role = NbgAgentRole.Tool,
        text = "tool",
        toolStatus = HanakoToolStatus(key = "file:/tmp/a.kt", kind = "file", title = "Write", running = true),
      ),
    )

    state.removeMessageAt(0)
    assertEquals(-1, state.findMessageIndexById(1L))
    assertEquals(0, state.findMessageIndexById(2L))
    assertEquals(0, state.findToolMessageIndexByKey("file:/tmp/a.kt"))

    state.replaceAllMessages { message ->
      message.copy(
        toolStatus = message.toolStatus?.copy(key = "file:/tmp/b.kt"),
      )
    }
    assertEquals(-1, state.findToolMessageIndexByKey("file:/tmp/a.kt"))
    assertEquals(0, state.findToolMessageIndexByKey("file:/tmp/b.kt"))
  }

  @Test
  fun localMessageIdsResetAndReseedBelowExistingNegativeIds() {
    val state = NbgAgentMessageState()

    assertEquals(-1L, state.nextLocalMessageId())
    assertEquals(-2L, state.nextLocalMessageId())

    state.resetLocalMessageIds()
    assertEquals(-1L, state.nextLocalMessageId())

    state.reseedLocalMessageIds(
      listOf(
        NbgAgentMessage(id = HANA_MOBILE_HISTORY_MESSAGE_ID_BASE, role = NbgAgentRole.Assistant, text = "history"),
        NbgAgentMessage(id = -12L, role = NbgAgentRole.System, text = "local"),
      ),
    )
    assertEquals(-13L, state.nextLocalMessageId())
  }

  @Test
  fun clearMessagesClearsIndexesButKeepsLocalIdSequence() {
    val state = NbgAgentMessageState()
    state.appendMessage(NbgAgentMessage(id = 1L, role = NbgAgentRole.User, text = "hello"))
    val localId = state.nextLocalMessageId()

    state.clearMessages()

    assertTrue(state.messages.isEmpty())
    assertFalse(state.messageExistsById(1L))
    assertEquals(localId - 1L, state.nextLocalMessageId())
  }
}

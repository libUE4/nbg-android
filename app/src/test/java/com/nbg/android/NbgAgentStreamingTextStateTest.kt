package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentStreamingTextStateTest {
  @Test
  fun assistantVisibleTextAccumulatesRawTextAndCanBeReplaced() {
    val state = NbgAgentStreamingTextState()

    val first = state.appendAssistantVisibleText(7L, "Hello")
    val second = state.appendAssistantVisibleText(7L, ", world")

    assertTrue(first.contains("Hello"))
    assertTrue(second.contains("Hello"))
    assertTrue(second.contains("world"))

    assertEquals("Final", state.replaceAssistantVisibleText(7L, "Final"))
    assertEquals("Final again", state.appendAssistantVisibleText(7L, " again"))
  }

  @Test
  fun thinkingVisibleTextUsesThinkingDisplayTransformAndCanBeReplaced() {
    val state = NbgAgentStreamingTextState()

    val first = state.appendThinkingVisibleText(11L, "思考")
    val second = state.appendThinkingVisibleText(11L, "中")

    assertTrue(first.isNotBlank())
    assertTrue(second.contains("思考") || second.contains("thinking", ignoreCase = true))
    assertTrue(state.replaceThinkingVisibleText(11L, "完成").isNotBlank())
  }

  @Test
  fun emptyDeltasAreIgnoredAndPendingStateTracksBothRoles() {
    val state = NbgAgentStreamingTextState()

    assertFalse(state.enqueueAssistantDelta(1L, ""))
    assertFalse(state.enqueueThinkingDelta(2L, ""))
    assertFalse(state.hasPendingDeltas)

    assertTrue(state.enqueueAssistantDelta(1L, "A"))
    assertTrue(state.enqueueThinkingDelta(2L, "T"))
    assertTrue(state.hasPendingDeltas)
  }

  @Test
  fun drainPendingDeltasCoalescesAndClearsPendingMaps() {
    val state = NbgAgentStreamingTextState()

    state.enqueueAssistantDelta(1L, "A")
    state.enqueueAssistantDelta(1L, "B")
    state.enqueueThinkingDelta(2L, "T")
    state.enqueueThinkingDelta(2L, "U")

    val batch = state.drainPendingDeltas()

    assertEquals(mapOf(1L to "AB"), batch.assistantDeltas)
    assertEquals(mapOf(2L to "TU"), batch.thinkingDeltas)
    assertFalse(state.hasPendingDeltas)
    assertTrue(state.drainPendingDeltas().assistantDeltas.isEmpty())
    assertTrue(state.drainPendingDeltas().thinkingDeltas.isEmpty())
  }

  @Test
  fun removingPendingAndRawBuffersIsScopedByRoleAndId() {
    val state = NbgAgentStreamingTextState()

    state.enqueueAssistantDelta(1L, "A")
    state.enqueueThinkingDelta(2L, "T")
    state.appendAssistantVisibleText(1L, "raw")
    state.appendThinkingVisibleText(2L, "think")

    state.removePendingAssistantDelta(1L)
    assertNull(state.removePendingThinkingDelta(3L))
    assertEquals("T", state.removePendingThinkingDelta(2L))
    assertFalse(state.hasPendingDeltas)

    state.removeAssistantRawText(1L)
    state.removeThinkingRawText(2L)
    assertEquals("new", state.appendAssistantVisibleText(1L, "new"))
  }

  @Test
  fun pruneFinishedAssistantRawTextsKeepsOnlyStreamingAssistantIds() {
    val state = NbgAgentStreamingTextState()

    state.appendAssistantVisibleText(1L, "one")
    state.appendAssistantVisibleText(2L, "two")
    state.pruneFinishedAssistantRawTexts(setOf(2L))

    assertEquals("one-new", state.appendAssistantVisibleText(1L, "one-new"))
    assertEquals("two-more", state.appendAssistantVisibleText(2L, "-more"))
  }

  @Test
  fun clearRemovesPendingAndRawTextState() {
    val state = NbgAgentStreamingTextState()
    state.enqueueAssistantDelta(1L, "A")
    state.enqueueThinkingDelta(2L, "T")
    state.appendAssistantVisibleText(1L, "raw")

    state.clear()

    assertFalse(state.hasPendingDeltas)
    assertEquals("fresh", state.appendAssistantVisibleText(1L, "fresh"))
  }
}

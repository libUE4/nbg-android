package com.nbg.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentUserMessageDedupStateTest {
  @Test
  fun firstPromptIsAccepted() {
    val state = NbgAgentUserMessageDedupState()

    assertTrue(state.shouldAccept("hello", nowMs = 1_000L))
  }

  @Test
  fun samePromptInsideDuplicateWindowIsRejected() {
    val state = NbgAgentUserMessageDedupState()
    assertTrue(state.shouldAccept("hello", nowMs = 1_000L))

    assertFalse(state.shouldAccept("hello", nowMs = 2_500L))
  }

  @Test
  fun samePromptAfterDuplicateWindowIsAccepted() {
    val state = NbgAgentUserMessageDedupState()
    assertTrue(state.shouldAccept("hello", nowMs = 1_000L))

    assertTrue(state.shouldAccept("hello", nowMs = 2_501L))
  }

  @Test
  fun differentPromptInsideDuplicateWindowIsAccepted() {
    val state = NbgAgentUserMessageDedupState()
    assertTrue(state.shouldAccept("hello", nowMs = 1_000L))

    assertTrue(state.shouldAccept("hello again", nowMs = 1_100L))
  }

  @Test
  fun samePromptWithClockMovingBackwardsIsAcceptedLikeOriginalWindowCheck() {
    val state = NbgAgentUserMessageDedupState()
    assertTrue(state.shouldAccept("hello", nowMs = 1_000L))

    assertTrue(state.shouldAccept("hello", nowMs = 900L))
  }

  @Test
  fun clearAllowsSamePromptAgainInsidePreviousWindow() {
    val state = NbgAgentUserMessageDedupState()
    assertTrue(state.shouldAccept("hello", nowMs = 1_000L))

    state.clear()

    assertTrue(state.shouldAccept("hello", nowMs = 1_100L))
  }
}

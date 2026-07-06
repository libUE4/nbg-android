package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentContentBlockPatchStateTest {
  @Test
  fun initialStateHasNoPendingPatches() {
    val state = NbgAgentContentBlockPatchState()

    assertFalse(state.hasPending)
    assertEquals(0, state.pendingCount)
  }

  @Test
  fun consumeWithoutPendingPatchReturnsOriginalBlock() {
    val state = NbgAgentContentBlockPatchState()
    val block = contentBlock(taskId = "task-1")

    val consumed = state.consumePatchFor(block)

    assertSame(block, consumed)
    assertFalse(state.hasPending)
  }

  @Test
  fun queuedPatchAppliesToNextMatchingContentBlockAndIsConsumed() {
    val state = NbgAgentContentBlockPatchState()

    state.enqueue(
      "task-1",
      HanakoContentBlockPatch(
        title = "Gradle build",
        subtitle = "Running tests",
        detail = "assembleDebug",
        status = "running",
      ),
    )

    val patched = state.consumePatchFor(
      contentBlock(
        taskId = "task-1",
        title = "Original title",
        subtitle = "Original subtitle",
        detail = "Original detail",
        status = "pending",
      ),
    )

    assertEquals("Gradle build", patched.title)
    assertEquals("Running tests", patched.subtitle)
    assertEquals("assembleDebug", patched.detail)
    assertEquals("running", patched.status)
    assertFalse(state.hasPending)
    assertEquals(0, state.pendingCount)
  }

  @Test
  fun consumingDifferentTaskLeavesPendingPatchQueued() {
    val state = NbgAgentContentBlockPatchState()
    state.enqueue("task-1", HanakoContentBlockPatch(title = "Queued"))
    val otherBlock = contentBlock(taskId = "task-2")

    val consumed = state.consumePatchFor(otherBlock)

    assertSame(otherBlock, consumed)
    assertTrue(state.hasPending)
    assertEquals(1, state.pendingCount)
  }

  @Test
  fun multiplePatchesMergeBeforeContentBlockArrives() {
    val state = NbgAgentContentBlockPatchState()

    state.enqueue(
      "task-1",
      HanakoContentBlockPatch(
        title = "First title",
        subtitle = "Preparing",
        detail = "initial detail",
      ),
    )
    state.enqueue(
      "task-1",
      HanakoContentBlockPatch(
        title = "",
        status = "done",
      ),
    )

    val patched = state.consumePatchFor(
      contentBlock(
        taskId = "task-1",
        title = "Fallback title",
        subtitle = "Fallback subtitle",
        detail = "Fallback detail",
        status = "pending",
      ),
    )

    assertEquals("Fallback title", patched.title)
    assertEquals("Preparing", patched.subtitle)
    assertEquals("initial detail", patched.detail)
    assertEquals("done", patched.status)
    assertFalse(state.hasPending)
  }

  @Test
  fun clearDropsQueuedPatches() {
    val state = NbgAgentContentBlockPatchState()
    state.enqueue("task-1", HanakoContentBlockPatch(title = "Queued"))
    state.enqueue("task-2", HanakoContentBlockPatch(title = "Queued too"))

    state.clear()

    assertFalse(state.hasPending)
    assertEquals(0, state.pendingCount)
    assertEquals("Original title", state.consumePatchFor(contentBlock(taskId = "task-1")).title)
  }

  private fun contentBlock(
    taskId: String,
    title: String = "Original title",
    subtitle: String = "",
    detail: String = "",
    status: String = "",
  ): HanakoContentBlock =
    HanakoContentBlock(
      type = "status",
      title = title,
      subtitle = subtitle,
      detail = detail,
      status = status,
      taskId = taskId,
    )
}

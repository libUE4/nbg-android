package com.nbg.android

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentFlushSchedulerStateTest {
  @Test
  fun initialStateHasNoActiveFlushJobs() {
    val state = NbgAgentFlushSchedulerState()

    assertFalse(state.hasActiveStreamingDeltaFlush)
    assertFalse(state.hasActiveToolStatusFlush)
  }

  @Test
  fun scheduleStreamingDeltaFlushStartsOnlyOneActiveJob() {
    val state = NbgAgentFlushSchedulerState()
    var starts = 0

    state.scheduleStreamingDeltaFlush {
      starts += 1
      Job()
    }
    state.scheduleStreamingDeltaFlush {
      starts += 1
      Job()
    }

    assertEquals(1, starts)
    assertTrue(state.hasActiveStreamingDeltaFlush)
  }

  @Test
  fun completeStreamingDeltaFlushAllowsReschedule() {
    val state = NbgAgentFlushSchedulerState()
    var starts = 0

    state.scheduleStreamingDeltaFlush {
      starts += 1
      Job()
    }
    state.completeStreamingDeltaFlush()
    state.scheduleStreamingDeltaFlush {
      starts += 1
      Job()
    }

    assertEquals(2, starts)
    assertTrue(state.hasActiveStreamingDeltaFlush)
  }

  @Test
  fun cancelStreamingDeltaFlushCancelsJobAndClearsActiveState() {
    val state = NbgAgentFlushSchedulerState()
    val job = Job()

    state.scheduleStreamingDeltaFlush { job }
    state.cancelStreamingDeltaFlush()

    assertTrue(job.isCancelled)
    assertFalse(state.hasActiveStreamingDeltaFlush)
  }

  @Test
  fun scheduleToolStatusFlushStartsOnlyOneActiveJob() {
    val state = NbgAgentFlushSchedulerState()
    var starts = 0

    state.scheduleToolStatusFlush {
      starts += 1
      Job()
    }
    state.scheduleToolStatusFlush {
      starts += 1
      Job()
    }

    assertEquals(1, starts)
    assertTrue(state.hasActiveToolStatusFlush)
  }

  @Test
  fun completeToolStatusFlushAllowsReschedule() {
    val state = NbgAgentFlushSchedulerState()
    var starts = 0

    state.scheduleToolStatusFlush {
      starts += 1
      Job()
    }
    state.completeToolStatusFlush()
    state.scheduleToolStatusFlush {
      starts += 1
      Job()
    }

    assertEquals(2, starts)
    assertTrue(state.hasActiveToolStatusFlush)
  }

  @Test
  fun cancelToolStatusFlushCancelsJobAndClearsActiveState() {
    val state = NbgAgentFlushSchedulerState()
    val job = Job()

    state.scheduleToolStatusFlush { job }
    state.cancelToolStatusFlush()

    assertTrue(job.isCancelled)
    assertFalse(state.hasActiveToolStatusFlush)
  }

  @Test
  fun cancelAllCancelsBothFlushJobs() {
    val state = NbgAgentFlushSchedulerState()
    val streamingJob = Job()
    val toolJob = Job()

    state.scheduleStreamingDeltaFlush { streamingJob }
    state.scheduleToolStatusFlush { toolJob }
    state.cancelAll()

    assertTrue(streamingJob.isCancelled)
    assertTrue(toolJob.isCancelled)
    assertFalse(state.hasActiveStreamingDeltaFlush)
    assertFalse(state.hasActiveToolStatusFlush)
  }
}

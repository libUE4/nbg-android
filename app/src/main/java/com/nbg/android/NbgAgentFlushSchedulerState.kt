package com.nbg.android

import kotlinx.coroutines.Job

internal class NbgAgentFlushSchedulerState {
  private var streamingDeltaFlushJob: Job? = null
  private var toolStatusFlushJob: Job? = null

  val hasActiveStreamingDeltaFlush: Boolean
    get() = streamingDeltaFlushJob?.isActive == true

  val hasActiveToolStatusFlush: Boolean
    get() = toolStatusFlushJob?.isActive == true

  fun scheduleStreamingDeltaFlush(startJob: () -> Job) {
    if (hasActiveStreamingDeltaFlush) return
    streamingDeltaFlushJob = startJob()
  }

  fun completeStreamingDeltaFlush() {
    streamingDeltaFlushJob = null
  }

  fun cancelStreamingDeltaFlush() {
    streamingDeltaFlushJob?.cancel()
    streamingDeltaFlushJob = null
  }

  fun scheduleToolStatusFlush(startJob: () -> Job) {
    if (hasActiveToolStatusFlush) return
    toolStatusFlushJob = startJob()
  }

  fun completeToolStatusFlush() {
    toolStatusFlushJob = null
  }

  fun cancelToolStatusFlush() {
    toolStatusFlushJob?.cancel()
    toolStatusFlushJob = null
  }

  fun cancelAll() {
    cancelStreamingDeltaFlush()
    cancelToolStatusFlush()
  }
}

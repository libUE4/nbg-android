package com.nbg.android

class TerminalStartupController {
  private var autoStartRequested = false

  fun markStartFailed() {
    autoStartRequested = false
  }

  fun shouldStartAutomatically(
    hasSession: Boolean,
    startInProgress: Boolean,
    hasViewportMetrics: Boolean,
  ): Boolean {
    if (!hasViewportMetrics || hasSession || startInProgress || autoStartRequested) return false
    autoStartRequested = true
    return true
  }
}

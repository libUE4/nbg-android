package com.nbg.android

internal class NbgAgentUserMessageDedupState {
  private var lastPrompt: String = ""
  private var lastAtMs: Long = 0L

  fun clear() {
    lastPrompt = ""
    lastAtMs = 0L
  }

  fun shouldAccept(prompt: String, nowMs: Long): Boolean {
    if (lastPrompt == prompt && nowMs - lastAtMs in 0..DUPLICATE_WINDOW_MS) return false
    lastPrompt = prompt
    lastAtMs = nowMs
    return true
  }

  private companion object {
    const val DUPLICATE_WINDOW_MS = 1_500L
  }
}

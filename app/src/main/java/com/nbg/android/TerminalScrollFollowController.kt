package com.nbg.android

interface TerminalScrollViewport {
  val topRow: Int
  val scrollCounter: Int

  fun setTopRow(topRow: Int)

  fun onScreenUpdated()

  fun invalidate()
}

class TerminalScrollFollowController {
  fun onScreenUpdated(
    viewport: TerminalScrollViewport,
    followOutput: Boolean,
  ) {
    if (followOutput) {
      viewport.onScreenUpdated()
      return
    }

    val anchoredTopRow = (viewport.topRow - viewport.scrollCounter).coerceAtMost(0)
    viewport.onScreenUpdated()
    viewport.setTopRow(anchoredTopRow)
    viewport.invalidate()
  }
}

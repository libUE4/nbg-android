package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalScrollFollowControllerTest {
  @Test
  fun followsLatestOutputWhenScrollIsEnabled() {
    val viewport = FakeScrollViewport(initialTopRow = -8, scrollCounter = 3)
    val controller = TerminalScrollFollowController()

    controller.onScreenUpdated(viewport, followOutput = true)

    assertEquals(0, viewport.topRow)
    assertEquals(listOf("onScreenUpdated"), viewport.events)
  }

  @Test
  fun freezesCurrentHistoryRowsWhenScrollIsDisabled() {
    val viewport = FakeScrollViewport(initialTopRow = -8, scrollCounter = 3)
    val controller = TerminalScrollFollowController()

    controller.onScreenUpdated(viewport, followOutput = false)

    assertEquals(-11, viewport.topRow)
    assertEquals(listOf("onScreenUpdated", "setTopRow:-11", "invalidate"), viewport.events)
  }

  @Test
  fun freezesBottomRowsWhenScrollIsDisabledBeforeUserScrollsUp() {
    val viewport = FakeScrollViewport(initialTopRow = 0, scrollCounter = 4)
    val controller = TerminalScrollFollowController()

    controller.onScreenUpdated(viewport, followOutput = false)

    assertEquals(-4, viewport.topRow)
    assertEquals(listOf("onScreenUpdated", "setTopRow:-4", "invalidate"), viewport.events)
  }

  @Test
  fun neverRestoresPastBottomWhenScrollCounterIsNegative() {
    val viewport = FakeScrollViewport(initialTopRow = 0, scrollCounter = -2)
    val controller = TerminalScrollFollowController()

    controller.onScreenUpdated(viewport, followOutput = false)

    assertEquals(0, viewport.topRow)
    assertEquals(listOf("onScreenUpdated", "setTopRow:0", "invalidate"), viewport.events)
  }

  private class FakeScrollViewport(
    initialTopRow: Int,
    override val scrollCounter: Int,
  ) : TerminalScrollViewport {
    val events = mutableListOf<String>()
    private var currentTopRow = initialTopRow

    override val topRow: Int
      get() = currentTopRow

    override fun setTopRow(topRow: Int) {
      currentTopRow = topRow
      events += "setTopRow:$topRow"
    }

    override fun onScreenUpdated() {
      currentTopRow = 0
      events += "onScreenUpdated"
    }

    override fun invalidate() {
      events += "invalidate"
    }
  }
}

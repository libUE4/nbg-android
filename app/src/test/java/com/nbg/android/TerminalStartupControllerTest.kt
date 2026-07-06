package com.nbg.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalStartupControllerTest {
  @Test
  fun requestsAutoStartOnlyOnceWhenNoSessionExists() {
    val controller = TerminalStartupController()

    assertFalse(
      controller.shouldStartAutomatically(
        hasSession = false,
        startInProgress = false,
        hasViewportMetrics = false,
      ),
    )
    assertTrue(
      controller.shouldStartAutomatically(
        hasSession = false,
        startInProgress = false,
        hasViewportMetrics = true,
      ),
    )
    assertFalse(
      controller.shouldStartAutomatically(
        hasSession = false,
        startInProgress = false,
        hasViewportMetrics = true,
      ),
    )
  }

  @Test
  fun doesNotRequestAutoStartWhenSessionAlreadyExistsOrStartIsInProgress() {
    val controller = TerminalStartupController()

    assertFalse(
      controller.shouldStartAutomatically(
        hasSession = true,
        startInProgress = false,
        hasViewportMetrics = true,
      ),
    )
    assertFalse(
      controller.shouldStartAutomatically(
        hasSession = false,
        startInProgress = true,
        hasViewportMetrics = true,
      ),
    )
  }

  @Test
  fun allowsRetryAfterAutoStartFailure() {
    val controller = TerminalStartupController()

    assertTrue(
      controller.shouldStartAutomatically(
        hasSession = false,
        startInProgress = false,
        hasViewportMetrics = true,
      ),
    )
    assertFalse(
      controller.shouldStartAutomatically(
        hasSession = false,
        startInProgress = false,
        hasViewportMetrics = true,
      ),
    )

    controller.markStartFailed()

    assertTrue(
      controller.shouldStartAutomatically(
        hasSession = false,
        startInProgress = false,
        hasViewportMetrics = true,
      ),
    )
  }
}

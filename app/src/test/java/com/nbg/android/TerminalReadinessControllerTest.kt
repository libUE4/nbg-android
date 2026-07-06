package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalReadinessControllerTest {
  @Test
  fun reportsInstallingUntilReadyMarkerAppears() {
    val controller = TerminalReadinessController()

    assertEquals(TerminalReadiness.Installing, controller.onOutput("Extracting Ubuntu rootfs..."))
    assertEquals(TerminalReadiness.Ready, controller.onOutput("NBG_TERMINAL_READY\r\nroot@localhost:~# "))
  }

  @Test
  fun classifiesStartupPhasesFromTerminalOutput() {
    val controller = TerminalReadinessController()

    assertEquals(
      TerminalStartupPhase.UbuntuInstall,
      controller.analyzeOutput("Extracting Ubuntu rootfs...").phase,
    )
    assertEquals(
      TerminalStartupPhase.DevelopmentTools,
      controller.analyzeOutput("NBG_TERMINAL_PHASE development_tools").phase,
    )
    assertEquals(
      TerminalStartupPhase.NodeRuntime,
      controller.analyzeOutput("NBG_TERMINAL_PHASE node_runtime").phase,
    )
    assertEquals(
      TerminalStartupPhase.ProotProbe,
      controller.analyzeOutput("NBG_TERMINAL_PHASE proot_probe").phase,
    )
    assertEquals(
      TerminalStartupPhase.DevelopmentTools,
      controller.analyzeOutput("Preparing built-in development tools...").phase,
    )
    assertEquals(
      TerminalStartupPhase.NodeRuntime,
      controller.analyzeOutput("Installing built-in Node.js runtime...").phase,
    )
    assertEquals(
      TerminalStartupPhase.ProotProbe,
      controller.analyzeOutput("bind_args=-b /proc").phase,
    )
  }

  @Test
  fun reportsFailureWhenStartupDiagnosticsAppear() {
    val controller = TerminalReadinessController()

    assertEquals(TerminalReadiness.Failed, controller.onOutput("PRoot startup probe failed.\nexit_code=1"))
    val snapshot = controller.analyzeOutput("PRoot startup probe failed.\nexit_code=1")
    assertEquals(TerminalReadiness.Failed, snapshot.readiness)
    assertEquals(TerminalStartupPhase.Failed, snapshot.phase)
    assertEquals("exit_code=1", snapshot.diagnostic)
  }

  @Test
  fun readyStateDoesNotRegressWhenLaterOutputMentionsDiagnostics() {
    val controller = TerminalReadinessController()

    assertEquals(TerminalReadiness.Ready, controller.onOutput("NBG_TERMINAL_READY\r\nroot@localhost:~# "))
    assertEquals(
      TerminalReadiness.Ready,
      controller.onOutput("cat log.txt\r\nPRoot startup probe failed.\r\nroot@localhost:~# "),
    )
    assertEquals(
      TerminalStartupPhase.Ready,
      controller.analyzeOutput("cat log.txt\r\nPRoot startup probe failed.\r\nroot@localhost:~# ").phase,
    )
  }

  @Test
  fun readyStateIsTrackedPerSession() {
    val controller = TerminalReadinessController()
    val sessionA = Any()
    val sessionB = Any()

    assertEquals(TerminalReadiness.Ready, controller.onOutput(sessionA, "NBG_TERMINAL_READY"))
    assertEquals(TerminalReadiness.Failed, controller.onOutput(sessionB, "PRoot startup probe failed."))
    assertEquals(TerminalReadiness.Ready, controller.onOutput(sessionA, "PRoot startup probe failed."))
  }

  @Test
  fun clearedSessionCanReportFailureAfterPreviouslyReady() {
    val controller = TerminalReadinessController()
    val session = Any()

    assertEquals(TerminalReadiness.Ready, controller.onOutput(session, "NBG_TERMINAL_READY"))

    controller.clearSession(session)

    assertEquals(TerminalReadiness.Failed, controller.onOutput(session, "PRoot startup probe failed."))
  }
}

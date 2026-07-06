package com.nbg.android

enum class TerminalReadiness {
  Installing,
  Ready,
  Failed,
}

enum class TerminalStartupPhase {
  UbuntuInstall,
  DevelopmentTools,
  NodeRuntime,
  ProotProbe,
  Ready,
  Failed,
}

data class TerminalReadinessSnapshot(
  val readiness: TerminalReadiness,
  val phase: TerminalStartupPhase,
  val diagnostic: String = "",
)

internal val TerminalReadiness.terminalStatusLabel: String
  get() = when (this) {
    TerminalReadiness.Installing -> "安装中"
    TerminalReadiness.Ready -> "已就绪"
    TerminalReadiness.Failed -> "失败"
  }

class TerminalReadinessController {
  private val readySessions = mutableSetOf<Any>()

  fun clearSession(sessionKey: Any) {
    readySessions -= sessionKey
  }

  fun analyzeOutput(sessionKey: Any, output: String): TerminalReadinessSnapshot {
    if (sessionKey in readySessions) {
      return TerminalReadinessSnapshot(
        readiness = TerminalReadiness.Ready,
        phase = TerminalStartupPhase.Ready,
      )
    }
    return when {
      output.contains("PRoot startup probe failed.") -> TerminalReadinessSnapshot(
        readiness = TerminalReadiness.Failed,
        phase = TerminalStartupPhase.Failed,
        diagnostic = output.firstStartupDiagnostic(),
      )
      output.contains("NBG_TERMINAL_READY") -> {
        readySessions += sessionKey
        TerminalReadinessSnapshot(
          readiness = TerminalReadiness.Ready,
          phase = TerminalStartupPhase.Ready,
        )
      }
      else -> TerminalReadinessSnapshot(
        readiness = TerminalReadiness.Installing,
        phase = output.startupPhase(),
      )
    }
  }

  fun analyzeOutput(output: String): TerminalReadinessSnapshot = analyzeOutput(DefaultSessionKey, output)

  fun onOutput(sessionKey: Any, output: String): TerminalReadiness =
    analyzeOutput(sessionKey, output).readiness

  fun onOutput(output: String): TerminalReadiness = onOutput(DefaultSessionKey, output)

  private fun String.startupPhase(): TerminalStartupPhase {
    if (contains("NBG_TERMINAL_PHASE development_tools") ||
      contains("Preparing built-in development tools") ||
      contains("Default development tool install lock timeout") ||
      contains("Default development tool installation failed")
    ) {
      return TerminalStartupPhase.DevelopmentTools
    }
    if (contains("NBG_TERMINAL_PHASE node_runtime") ||
      contains("Installing built-in Node.js runtime") ||
      contains("Node.js tarball") ||
      contains("Node.js installation") ||
      contains("DEFAULT_NODE_ARCHIVE")
    ) {
      return TerminalStartupPhase.NodeRuntime
    }
    if (contains("NBG_TERMINAL_PHASE proot_probe") ||
      contains("PRoot startup probe") ||
      contains("proot_loader=") ||
      contains("bind_args=")
    ) {
      return TerminalStartupPhase.ProotProbe
    }
    return TerminalStartupPhase.UbuntuInstall
  }

  private fun String.firstStartupDiagnostic(): String =
    startupDiagnosticLine { it.startsWith("exit_code=") }
      ?: startupDiagnosticLine { it.startsWith("Node.js") }
      ?: startupDiagnosticLine { it.contains("failed", ignoreCase = true) }
      ?: ""

  private fun String.startupDiagnosticLine(predicate: (String) -> Boolean): String? =
    lineSequence()
      .map { it.trim() }
      .firstOrNull(predicate)

  private data object DefaultSessionKey
}

package com.nbg.android

import com.nbg.android.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TerminalWorkspace private constructor() {
  val tabsController = TerminalTabsController()
  val startupControllers = mutableMapOf<Int, TerminalStartupController>()
  val sessions = mutableListOf<TerminalSession?>(null)
  val startInProgress = mutableListOf(false)
  val readinessSnapshots = mutableListOf<TerminalReadinessSnapshot?>(null)
  private val startupTokens = mutableListOf<Any?>(null)
  private val sessionTokensForTest = mutableListOf<Any?>(null)
  private val readinessController = TerminalReadinessController()
  private val readinessObservationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val readinessObservationJobs = mutableListOf<Job?>(null)
  private val _readinessVersion = MutableStateFlow(0)
  val readinessVersion: StateFlow<Int> = _readinessVersion.asStateFlow()

  val tabs: List<TerminalTab>
    get() = tabsController.tabs

  val selectedIndex: Int
    get() = tabsController.selectedIndex

  fun addTab() {
    tabsController.addTab()
    sessions += null
    startInProgress += false
    readinessSnapshots += null
    startupTokens += null
    sessionTokensForTest += null
    readinessObservationJobs += null
  }

  fun selectTab(index: Int) {
    tabsController.select(index)
  }

  fun renameSelectedTab(title: String) {
    tabsController.renameSelected(title)
  }

  fun updateTabStatus(index: Int, status: String): Boolean =
    tabsController.updateStatus(index, status)

  fun tabTitleOrNull(index: Int): String? = tabs.getOrNull(index)?.title

  fun renameTab(index: Int, title: String): Boolean {
    if (index !in tabs.indices) return false
    return tabsController.rename(index, title)
  }

  fun deleteSelectedTab(): TerminalSession? {
    val index = selectedIndex
    val removed = sessions.getOrNull(index)
    if (!tabsController.deleteSelected()) return null
    cancelReadinessObservation(index)
    if (removed != null) {
      readinessController.clearSession(removed)
    }
    sessions.removeAt(index)
    startInProgress.removeAt(index)
    readinessSnapshots.removeAt(index)
    startupTokens.removeAt(index)
    sessionTokensForTest.removeAt(index)
    readinessObservationJobs.removeAt(index)
    bumpReadinessVersion()
    val shiftedStartupControllers = startupControllers
      .filterKeys { it != index }
      .mapKeys { (controllerIndex, _) ->
        if (controllerIndex > index) controllerIndex - 1 else controllerIndex
      }
    startupControllers.clear()
    startupControllers.putAll(shiftedStartupControllers)
    return removed
  }

  fun setSession(index: Int, session: TerminalSession) {
    if (index !in sessions.indices) return
    sessions[index]?.let { readinessController.clearSession(it) }
    cancelReadinessObservation(index)
    sessions[index] = session
    observeSessionReadiness(index, session)
  }

  fun beginStartup(index: Int): Any? {
    if (index !in sessions.indices) return null
    val token = Any()
    startupTokens[index] = token
    startInProgress[index] = true
    return token
  }

  fun isStartupTokenCurrent(index: Int, token: Any): Boolean =
    index in startupTokens.indices && startupTokens[index] === token

  fun startupIndexForToken(token: Any): Int? =
    startupTokens.indexOfFirst { it === token }.takeIf { it >= 0 }

  fun finishStartup(index: Int, token: Any) {
    if (!isStartupTokenCurrent(index, token)) return
    startupTokens[index] = null
    startInProgress[index] = false
  }

  fun setStartInProgress(index: Int, inProgress: Boolean) {
    startInProgress[index] = inProgress
  }

  fun updateReadinessSnapshot(index: Int, snapshot: TerminalReadinessSnapshot): Boolean {
    if (index !in readinessSnapshots.indices) return false
    if (readinessSnapshots[index] == snapshot) return false
    readinessSnapshots[index] = snapshot
    bumpReadinessVersion()
    return true
  }

  fun recordReadinessOutput(index: Int, sessionKey: Any, output: String): TerminalReadinessSnapshot? {
    if (output.isEmpty()) return null
    val currentIndex = readinessIndexForSession(index, sessionKey) ?: return null
    val snapshot = readinessController.analyzeOutput(sessionKey, output)
    updateReadinessSnapshot(currentIndex, snapshot)
    updateTabStatus(currentIndex, snapshot.readiness.terminalStatusLabel)
    return snapshot
  }

  internal fun observeReadinessOutputFlow(
    index: Int,
    sessionKey: Any,
    outputFlow: Flow<String>,
    scope: CoroutineScope = readinessObservationScope,
  ): Job? {
    if (index !in readinessObservationJobs.indices) return null
    cancelReadinessObservation(index)
    val job = scope.launch {
      outputFlow.collect { output ->
        recordReadinessOutput(index, sessionKey, output)
      }
    }
    readinessObservationJobs[index] = job
    return job
  }

  fun markStartupFailed(index: Int, diagnostic: String = "terminal_start_failed"): Boolean =
    updateReadinessSnapshot(
      index,
      TerminalReadinessSnapshot(
        readiness = TerminalReadiness.Failed,
        phase = TerminalStartupPhase.Failed,
        diagnostic = diagnostic.ifBlank { "terminal_start_failed" },
      ),
    )

  fun terminalDiagnosticsSnapshot(): List<TerminalReadinessSnapshot> =
    readinessSnapshots.filterNotNull()

  fun readinessVersionValueForTest(): Int = readinessVersion.value

  private fun observeSessionReadiness(index: Int, session: TerminalSession) {
    observeReadinessOutputFlow(
      index = index,
      sessionKey = session,
      outputFlow = session.screenText,
    )
  }

  private fun cancelReadinessObservation(index: Int) {
    readinessObservationJobs.getOrNull(index)?.cancel()
    if (index in readinessObservationJobs.indices) {
      readinessObservationJobs[index] = null
    }
  }

  private fun currentSessionIndex(sessionKey: Any): Int? =
    sessions.indexOfFirst { it === sessionKey }.takeIf { it >= 0 }

  private fun readinessIndexForSession(index: Int, sessionKey: Any): Int? =
    if (sessionKey is TerminalSession) {
      currentSessionIndex(sessionKey)
    } else {
      index.takeIf { it in readinessSnapshots.indices }
    }

  private fun bumpReadinessVersion() {
    _readinessVersion.value += 1
  }

  fun setSessionTokenForTest(index: Int, session: Any) {
    sessionTokensForTest[index] = session
  }

  fun sessionTokenForTest(index: Int): Any? = sessionTokensForTest[index]

  fun sessionCountForTest(): Int = sessions.size

  fun startInProgressCountForTest(): Int = startInProgress.size

  companion object {
    val shared = TerminalWorkspace()

    fun createForTest(): TerminalWorkspace = TerminalWorkspace()
  }
}

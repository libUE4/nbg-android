package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class TerminalWorkspaceTest {
  @Test
  fun sharedWorkspaceSurvivesActivityRecreationInProcess() {
    val workspace = TerminalWorkspace.createForTest()
    val token = Any()

    workspace.setSessionTokenForTest(index = 0, session = token)

    assertSame(token, workspace.sessionTokenForTest(0))
  }

  @Test
  fun addingAndDeletingTabsKeepsSessionListsAligned() {
    val workspace = TerminalWorkspace.createForTest()

    workspace.addTab()
    workspace.selectTab(0)
    workspace.deleteSelectedTab()

    assertEquals(1, workspace.tabs.size)
    assertEquals(1, workspace.sessionCountForTest())
    assertEquals(1, workspace.startInProgressCountForTest())
  }

  @Test
  fun deletingTabShiftsStartupControllersToRemainingTabs() {
    val workspace = TerminalWorkspace.createForTest()
    val firstController = TerminalStartupController()
    val secondController = TerminalStartupController()
    val thirdController = TerminalStartupController()

    workspace.addTab()
    workspace.addTab()
    workspace.startupControllers[0] = firstController
    workspace.startupControllers[1] = secondController
    workspace.startupControllers[2] = thirdController

    workspace.selectTab(1)
    workspace.deleteSelectedTab()

    assertSame(firstController, workspace.startupControllers[0])
    assertSame(thirdController, workspace.startupControllers[1])
    assertFalse(workspace.startupControllers.containsKey(2))
  }

  @Test
  fun deletingLastTabRemovesItsStartupController() {
    val workspace = TerminalWorkspace.createForTest()
    val firstController = TerminalStartupController()
    val secondController = TerminalStartupController()

    workspace.addTab()
    workspace.startupControllers[0] = firstController
    workspace.startupControllers[1] = secondController

    workspace.selectTab(1)
    workspace.deleteSelectedTab()

    assertSame(firstController, workspace.startupControllers[0])
    assertFalse(workspace.startupControllers.containsKey(1))
  }

  @Test
  fun staleTabIndexesDoNotRenameExistingTabs() {
    val workspace = TerminalWorkspace.createForTest()

    val renamed = workspace.renameTab(5, "stale")

    assertFalse(renamed)
    assertEquals("终端 1", workspace.tabs.single().title)
    assertEquals(null, workspace.tabTitleOrNull(5))
  }

  @Test
  fun renamingBackgroundTabDoesNotChangeSelectedTab() {
    val workspace = TerminalWorkspace.createForTest()
    workspace.addTab()
    workspace.selectTab(0)

    val renamed = workspace.renameTab(1, "后台终端")

    assertEquals(true, renamed)
    assertEquals(0, workspace.selectedIndex)
    assertEquals(listOf("终端 1", "后台终端"), workspace.tabs.map { it.title })
  }

  @Test
  fun updatingBackgroundTabStatusDoesNotChangeSelectedTab() {
    val workspace = TerminalWorkspace.createForTest()
    workspace.addTab()
    workspace.selectTab(0)

    val updated = workspace.updateTabStatus(1, "启动中")

    assertEquals(true, updated)
    assertEquals(0, workspace.selectedIndex)
    assertEquals(listOf("准备中", "启动中"), workspace.tabs.map { it.status })
  }

  @Test
  fun startupFailureWritesTerminalReadinessSnapshotBeforeSessionExists() {
    val workspace = TerminalWorkspace.createForTest()

    val updated = workspace.markStartupFailed(0, "prepare failed")
    val snapshots = workspace.terminalDiagnosticsSnapshot()

    assertEquals(true, updated)
    assertEquals(1, snapshots.size)
    assertEquals(TerminalReadiness.Failed, snapshots.single().readiness)
    assertEquals(TerminalStartupPhase.Failed, snapshots.single().phase)
    assertEquals("prepare failed", snapshots.single().diagnostic)
  }

  @Test
  fun readinessOutputFlowUpdatesWorkspaceWithoutComposeObserver() = runBlocking {
    val workspace = TerminalWorkspace.createForTest()
    val output = MutableStateFlow("")
    val sessionKey = Any()
    val job = requireNotNull(
      workspace.observeReadinessOutputFlow(
        index = 0,
        sessionKey = sessionKey,
        outputFlow = output,
        scope = this,
      ),
    )

    try {
      assertEquals(0, workspace.readinessVersionValueForTest())

      output.value = "NBG_TERMINAL_PHASE node_runtime"
      val installing = workspace.awaitReadiness(TerminalReadiness.Installing)
      assertEquals(TerminalStartupPhase.NodeRuntime, installing.phase)
      assertEquals("安装中", workspace.tabs.single().status)
      assertEquals(1, workspace.readinessVersionValueForTest())

      output.value = "NBG_TERMINAL_READY\r\nroot@localhost:~# "
      val ready = workspace.awaitReadiness(TerminalReadiness.Ready)
      assertEquals(TerminalStartupPhase.Ready, ready.phase)
      assertEquals("已就绪", workspace.tabs.single().status)
      assertEquals(2, workspace.readinessVersionValueForTest())
    } finally {
      job.cancelAndJoin()
    }
  }

  @Test
  fun recordReadinessOutputKeepsReadyStateAcrossRecreatedObservers() {
    val workspace = TerminalWorkspace.createForTest()
    val sessionKey = Any()

    val ready = workspace.recordReadinessOutput(0, sessionKey, "NBG_TERMINAL_READY\r\nroot@localhost:~# ")
    val later = workspace.recordReadinessOutput(
      0,
      sessionKey,
      "cat old-log.txt\r\nPRoot startup probe failed.\r\nroot@localhost:~# ",
    )

    assertEquals(TerminalReadiness.Ready, ready?.readiness)
    assertEquals(TerminalReadiness.Ready, later?.readiness)
    assertEquals(TerminalStartupPhase.Ready, workspace.terminalDiagnosticsSnapshot().single().phase)
    assertEquals("已就绪", workspace.tabs.single().status)
  }

  @Test
  fun deletingStartingTabInvalidatesItsStartupToken() {
    val workspace = TerminalWorkspace.createForTest()
    workspace.addTab()
    workspace.selectTab(1)
    val token = requireNotNull(workspace.beginStartup(1))

    workspace.deleteSelectedTab()

    assertFalse(workspace.isStartupTokenCurrent(1, token))
    assertEquals(1, workspace.sessionCountForTest())
    assertEquals(1, workspace.startInProgressCountForTest())
  }

  @Test
  fun staleStartupTokenCannotFinishRemainingTab() {
    val workspace = TerminalWorkspace.createForTest()
    workspace.addTab()
    val firstToken = requireNotNull(workspace.beginStartup(0))

    workspace.selectTab(0)
    workspace.deleteSelectedTab()
    workspace.finishStartup(0, firstToken)

    assertFalse(workspace.startInProgress[0])
  }

  @Test
  fun startupTokenTracksTabWhenEarlierTabIsDeleted() {
    val workspace = TerminalWorkspace.createForTest()
    workspace.addTab()
    val token = requireNotNull(workspace.beginStartup(1))

    workspace.selectTab(0)
    workspace.deleteSelectedTab()

    assertEquals(0, workspace.startupIndexForToken(token))
    assertFalse(workspace.isStartupTokenCurrent(1, token))
    workspace.finishStartup(0, token)
    assertFalse(workspace.startInProgress[0])
  }

  private suspend fun TerminalWorkspace.awaitReadiness(
    readiness: TerminalReadiness,
  ): TerminalReadinessSnapshot {
    repeat(50) {
      terminalDiagnosticsSnapshot().firstOrNull { it.readiness == readiness }?.let { return it }
      delay(10)
    }
    throw AssertionError("Timed out waiting for $readiness readiness")
  }
}

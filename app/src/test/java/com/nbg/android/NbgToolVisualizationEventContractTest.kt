package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgToolVisualizationEventContractTest {
  @Test
  fun supportedKindsAndStatesAreStable() {
    assertEquals("nbg-tool-visualization-events-v1", NBG_TOOL_VISUALIZATION_EVENT_CONTRACT_VERSION)
    assertEquals(
      setOf("tool", "terminal", "file", "diff", "todo", "team_task", "team_agent", "thinking", "confirmation", "vision"),
      NBG_TOOL_VISUALIZATION_SUPPORTED_KINDS,
    )
    assertEquals(
      setOf("waiting", "running", "succeeded", "failed", "blocked", "cancelled", "restored_incomplete"),
      NBG_TOOL_VISUALIZATION_SUPPORTED_STATES,
    )
  }

  @Test
  fun normalizesToolStatesForPresentation() {
    assertEquals(NbgToolVisualizationState.Running, nbgNormalizeToolVisualizationState("writing"))
    assertEquals(NbgToolVisualizationState.Running, nbgNormalizeToolVisualizationState("anything", running = true))
    assertEquals(NbgToolVisualizationState.Succeeded, nbgNormalizeToolVisualizationState("done"))
    assertEquals(NbgToolVisualizationState.Succeeded, nbgNormalizeToolVisualizationState("failed", success = true))
    assertEquals(NbgToolVisualizationState.Failed, nbgNormalizeToolVisualizationState("done", success = false))
    assertEquals(NbgToolVisualizationState.Blocked, nbgNormalizeToolVisualizationState("rejected"))
    assertEquals(NbgToolVisualizationState.Cancelled, nbgNormalizeToolVisualizationState("interrupted"))
    assertEquals(NbgToolVisualizationState.Waiting, nbgNormalizeToolVisualizationState("unknown-upstream-status"))
  }

  @Test
  fun derivesKindFromInlinePreviewsAndTerminalOutput() {
    val terminal = HanakoToolStatus(
      key = "term",
      kind = "tool",
      toolName = "bash",
      title = "运行命令",
      terminalOutput = HanakoTerminalOutput(sessionId = "term-1", output = "ok"),
    )
    val filePreview = HanakoToolStatus(
      key = "write",
      kind = "tool",
      toolName = "write",
      title = "写入文件",
      filePreview = HanakoFilePreview(fileName = "Main.kt", filePath = "/root/Main.kt"),
    )
    val diff = HanakoToolStatus(
      key = "edit",
      kind = "file",
      toolName = "edit",
      title = "修改文件",
      fileDiff = HanakoFileDiff(fileName = "Main.kt", filePath = "/root/Main.kt", unifiedDiff = "+ok"),
    )

    assertEquals(NbgToolVisualizationKind.Terminal, terminal.nbgToolVisualizationKind())
    assertEquals(NbgToolVisualizationKind.File, filePreview.nbgToolVisualizationKind())
    assertEquals(NbgToolVisualizationKind.Diff, diff.nbgToolVisualizationKind())
    assertTrue(terminal.hasVisibleToolStatus())
  }

  @Test
  fun todoTeamAndAgentStatusesProjectToSharedToolStatusModel() {
    val todos = listOf(
      HanakoTodoItem(content = "检查仓库", activeForm = "", status = "completed"),
      HanakoTodoItem(content = "运行测试", activeForm = "正在运行测试", status = "in_progress"),
    )
    val todoStatus = nbgTodoListToolStatus(todos)!!
    val agent = HanakoTeamAgentStatus(
      taskId = "team-1",
      agentId = "coder",
      role = "Coder",
      title = "实现 Agent",
      status = "working",
      summary = "正在修改代码",
    )
    val task = HanakoTeamTaskStatus(
      taskId = "team-1",
      title = "修复构建",
      mode = HANA_TEAM_MULTI_AGENT_SESSION_MODE,
      status = "running",
      summary = "多 Agent 正在处理",
      agents = listOf(agent),
    )
    val taskStatus = task.nbgTeamTaskToolStatus()
    val agentStatus = agent.nbgTeamAgentToolStatus()

    assertEquals("todo:session", todoStatus.key)
    assertEquals("todo", todoStatus.kind)
    assertEquals("todo_status", todoStatus.toolName)
    assertEquals("正在运行测试", todoStatus.title)
    assertEquals("1/2 / 进行中", todoStatus.subtitle)
    assertEquals(NbgToolVisualizationKind.Todo, todoStatus.nbgToolVisualizationKind())
    assertEquals(NbgToolVisualizationState.Running, todoStatus.nbgToolVisualizationState())
    assertTrue(todoStatus.running)

    assertEquals("team_task:team-1", taskStatus.key)
    assertEquals("team_task", taskStatus.kind)
    assertEquals("team_task_status", taskStatus.toolName)
    assertEquals("修复构建", taskStatus.title)
    assertEquals("多 Agent / 进行中 / 1/1 工作中", taskStatus.subtitle)
    assertEquals("多 Agent 正在处理", taskStatus.detail)
    assertEquals(NbgToolVisualizationKind.TeamTask, taskStatus.nbgToolVisualizationKind())
    assertEquals(NbgToolVisualizationState.Running, taskStatus.nbgToolVisualizationState())
    assertTrue(taskStatus.running)

    assertEquals("team_agent:team-1:coder", agentStatus.key)
    assertEquals("team_agent", agentStatus.kind)
    assertEquals("team_agent_status", agentStatus.toolName)
    assertEquals("实现 Agent", agentStatus.title)
    assertEquals("Coder / 进行中", agentStatus.subtitle)
    assertEquals("正在修改代码", agentStatus.detail)
    assertEquals(NbgToolVisualizationKind.TeamAgent, agentStatus.nbgToolVisualizationKind())
    assertEquals(NbgToolVisualizationState.Running, agentStatus.nbgToolVisualizationState())
    assertTrue(agentStatus.running)
  }

  @Test
  fun terminalTeamStatusesMapToTerminalVisualizationStates() {
    val completedTask = HanakoTeamTaskStatus(
      taskId = "team-complete",
      status = "completed",
      agents = emptyList(),
    ).nbgTeamTaskToolStatus()
    val failedAgent = HanakoTeamAgentStatus(
      taskId = "team-failed",
      agentId = "tester",
      role = "Tester",
      title = "验证 Agent",
      status = "failed",
    ).nbgTeamAgentToolStatus()
    val abortedTask = HanakoTeamTaskStatus(
      taskId = "team-abort",
      status = "aborted",
      agents = emptyList(),
    ).nbgTeamTaskToolStatus()

    assertEquals(NbgToolVisualizationState.Succeeded, completedTask.nbgToolVisualizationState())
    assertEquals(true, completedTask.success)
    assertEquals(NbgToolVisualizationState.Failed, failedAgent.nbgToolVisualizationState())
    assertEquals(false, failedAgent.success)
    assertEquals(NbgToolVisualizationState.Cancelled, abortedTask.nbgToolVisualizationState())
    assertNull(abortedTask.success)
  }

  @Test
  fun teamProjectionKeepsArtifactsOutAndBoundsVisibleText() {
    val longSummary = "正在处理 " + "x".repeat(400) + " /root/private/project"
    val task = HanakoTeamTaskStatus(
      taskId = "team-path-/root/private/task",
      title = "团队任务 " + "y".repeat(260),
      mode = HANA_TEAM_BACKEND_AGENT_MODE,
      status = "running",
      summary = longSummary,
      agents = listOf(
        HanakoTeamAgentStatus(
          taskId = "team-path-/root/private/task",
          agentId = "coder",
          role = "Coder",
          title = "实现 Agent",
          status = "working",
          summary = longSummary,
          artifactRefs = listOf("/root/private/project/src/Main.kt", "secret-artifact-id"),
        ),
      ),
    )
    val taskStatus = task.nbgTeamTaskToolStatus()
    val agentStatus = task.agents.single().nbgTeamAgentToolStatus()

    assertTrue(taskStatus.title.length <= 180)
    assertTrue(taskStatus.detail.length <= 240)
    assertTrue(agentStatus.detail.length <= 240)
    assertFalse(taskStatus.title.contains("secret-artifact-id"))
    assertFalse(taskStatus.subtitle.contains("secret-artifact-id"))
    assertFalse(taskStatus.detail.contains("secret-artifact-id"))
    assertFalse(agentStatus.title.contains("secret-artifact-id"))
    assertFalse(agentStatus.subtitle.contains("secret-artifact-id"))
    assertFalse(agentStatus.detail.contains("secret-artifact-id"))
    assertFalse(agentStatus.detail.contains("src/Main.kt"))
  }

  @Test
  fun terminalExitCodeMapsToFirstClassStateDetail() {
    val success = HanakoTerminalOutput(sessionId = "ok", exitCode = 0)
    val failure = HanakoTerminalOutput(
      sessionId = "fail",
      cwd = "/workspace",
      alive = false,
      exitCode = 17,
      sliceFrom = 10,
      sliceTo = 20,
    )
    val running = HanakoTerminalOutput(sessionId = "run", alive = true)

    assertEquals(0, success.nbgTerminalExitDetail()?.exitCode)
    assertEquals(NbgToolVisualizationState.Succeeded, success.nbgTerminalExitDetail()?.state)
    assertEquals("exit 0", success.nbgTerminalExitDetail()?.label)
    assertEquals(NbgToolVisualizationState.Failed, failure.nbgTerminalExitDetail()?.state)
    assertEquals("exit 17", failure.nbgTerminalExitDetail()?.label)
    assertEquals("/workspace / cursor 10-20 / exit 17 / stopped", nbgTerminalOutputMeta(failure))
    assertEquals("/workspace / exit 17 / stopped", nbgTerminalOutputMeta(failure, includeSlice = false))
    assertEquals("exit 17", nbgTerminalOutputSummary(failure))
    assertNull(running.nbgTerminalExitDetail())
    assertEquals("终端运行中，等待输出", nbgTerminalOutputSummary(running))
  }

  @Test
  fun confirmationResolutionProducesCompactTimelineStatus() {
    val confirmation = HanakoConfirmation(
      confirmId = "confirm-1",
      title = "确认写入文件",
      body = "是否允许写入 raw-tool-args",
      subjectLabel = "文件写入",
      subjectDetail = "/root/project/app/src/main/Main.kt",
      severity = "elevated",
      riskTier = NbgPermissionRiskTier.High.wireName,
      riskLabel = "高风险",
      targetLabel = "/root/project/app/src/main/Main.kt",
      recoveryHint = "拒绝后可要求 Agent 解释修改计划。",
      confirmLabel = "允许",
      rejectLabel = "拒绝",
    )

    val auditEntry = nbgBuildConfirmationResolutionAuditEntry(
      confirmId = "confirm-1",
      action = "confirmed",
      confirmation = confirmation,
      timestampMillis = 123L,
    )
    val confirmed = nbgConfirmationResolutionToolStatus("confirm-1", "confirmed", confirmation, auditEntry)
    val rejected = nbgConfirmationResolutionToolStatus("confirm-1", "rejected", confirmation)

    assertEquals("confirmation:${auditEntry.confirmIdHash.take(32)}", confirmed.key)
    assertEquals("confirmation", confirmed.kind)
    assertEquals("session_confirmation", confirmed.toolName)
    assertEquals("确认写入文件", confirmed.title)
    assertEquals("已允许 / 高风险 / 文件写入", confirmed.subtitle)
    assertTrue(confirmed.detail.contains("目标：/root/project/app/src/main/Main.kt"))
    assertTrue(confirmed.detail.contains("恢复：拒绝后可要求 Agent 解释修改计划。"))
    assertTrue(confirmed.detail.contains("审计：${auditEntry.nbgConfirmationAuditTimelineLabel()}"))
    assertEquals(NbgToolVisualizationKind.Confirmation, confirmed.nbgToolVisualizationKind())
    assertEquals(NbgToolVisualizationState.Succeeded, confirmed.nbgToolVisualizationState())
    assertEquals("确认", nbgToolKindLabel(confirmed.kind))
    assertTrue(confirmed.hasVisibleToolStatus())
    assertFalse(confirmed.title.contains("raw-tool-args"))
    assertFalse(confirmed.subtitle.contains("raw-tool-args"))
    assertFalse(confirmed.detail.contains("raw-tool-args"))
    assertFalse(confirmed.key.contains("confirm-1"))
    assertFalse(confirmed.detail.contains("confirm-1"))
    assertFalse(confirmed.detail.contains(auditEntry.signature))
    assertEquals(NbgToolVisualizationState.Cancelled, rejected.nbgToolVisualizationState())
    assertEquals("已拒绝 / 高风险 / 文件写入", rejected.subtitle)
  }

  @Test
  fun duplicateConfirmationResolutionMergePreservesAuditContext() {
    val confirmation = HanakoConfirmation(
      confirmId = "confirm-1",
      title = "确认写入文件",
      body = "raw-body-with-tool-args",
      subjectLabel = "文件写入",
      subjectDetail = "/root/project/app/src/main/Main.kt",
      severity = "elevated",
      riskTier = NbgPermissionRiskTier.High.wireName,
      riskLabel = "高风险",
      targetLabel = "/root/project/app/src/main/Main.kt",
      recoveryHint = "拒绝后可要求 Agent 解释修改计划。",
      confirmLabel = "允许",
      rejectLabel = "拒绝",
    )
    val auditEntry = nbgBuildConfirmationResolutionAuditEntry(
      confirmId = "confirm-1",
      action = "confirmed",
      confirmation = confirmation,
      timestampMillis = 123L,
    )
    val rich = nbgConfirmationResolutionToolStatus(
      confirmId = "confirm-1",
      action = "confirmed",
      confirmation = confirmation,
      auditEntry = auditEntry,
    )
    val duplicateFallback = nbgConfirmationResolutionToolStatus(
      confirmId = "confirm-1",
      action = "confirmed",
      confirmation = null,
    )

    val merged = rich.mergeToolStatus(duplicateFallback)

    assertEquals("confirmation:${auditEntry.confirmIdHash.take(32)}", merged.key)
    assertEquals("确认写入文件", merged.title)
    assertEquals("已允许 / 高风险 / 文件写入", merged.subtitle)
    assertTrue(merged.detail.contains("目标：/root/project/app/src/main/Main.kt"))
    assertTrue(merged.detail.contains("恢复：拒绝后可要求 Agent 解释修改计划。"))
    assertTrue(merged.detail.contains("审计：${auditEntry.nbgConfirmationAuditTimelineLabel()}"))
    assertEquals(NbgToolVisualizationState.Succeeded, merged.nbgToolVisualizationState())
    assertFalse(merged.key.contains("confirm-1"))
    assertFalse(merged.title.contains("raw-body-with-tool-args"))
    assertFalse(merged.subtitle.contains("raw-body-with-tool-args"))
    assertFalse(merged.detail.contains("raw-body-with-tool-args"))
    assertFalse(merged.detail.contains(auditEntry.signature))
  }

  @Test
  fun restoredRunningHistoryMapsToRestoredIncomplete() {
    val restored = HanakoToolStatus(
      key = "history:term",
      kind = "terminal",
      toolName = "terminal_read",
      title = "终端",
      subtitle = "历史记录 / 未收到结束事件",
      status = "failed",
      running = false,
      success = false,
    )

    assertEquals(NbgToolVisualizationState.RestoredIncomplete, restored.nbgToolVisualizationState())
    assertEquals("未收到结束事件", nbgToolStatusLabel(restored))
  }
}

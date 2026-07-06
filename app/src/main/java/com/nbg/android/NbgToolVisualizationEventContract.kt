package com.nbg.android

internal const val NBG_TOOL_VISUALIZATION_EVENT_CONTRACT_VERSION = "nbg-tool-visualization-events-v1"

internal enum class NbgToolVisualizationKind(val wireName: String) {
  Tool("tool"),
  Terminal("terminal"),
  File("file"),
  Diff("diff"),
  Todo("todo"),
  TeamTask("team_task"),
  TeamAgent("team_agent"),
  Thinking("thinking"),
  Confirmation("confirmation"),
  Vision("vision"),
}

internal enum class NbgToolVisualizationState(val wireName: String, val label: String) {
  Waiting("waiting", "等待"),
  Running("running", "进行中"),
  Succeeded("succeeded", "完成"),
  Failed("failed", "失败"),
  Blocked("blocked", "已阻止"),
  Cancelled("cancelled", "已取消"),
  RestoredIncomplete("restored_incomplete", "未收到结束事件"),
}

internal val NBG_TOOL_VISUALIZATION_SUPPORTED_KINDS: Set<String> =
  NbgToolVisualizationKind.entries.map { it.wireName }.toSet()

internal val NBG_TOOL_VISUALIZATION_SUPPORTED_STATES: Set<String> =
  NbgToolVisualizationState.entries.map { it.wireName }.toSet()

internal data class NbgTerminalExitDetail(
  val exitCode: Int,
  val state: NbgToolVisualizationState,
  val label: String,
)

private const val NBG_CONFIRMATION_TIMELINE_TEXT_LIMIT = 180

internal fun nbgToolVisualizationKind(kind: String, toolName: String = ""): NbgToolVisualizationKind {
  val normalizedKind = kind.trim().lowercase()
  val normalizedTool = toolName.trim().lowercase()
  return when {
    normalizedKind == "terminal" || normalizedTool.isTerminalLikeTool() -> NbgToolVisualizationKind.Terminal
    normalizedKind == "file" && normalizedTool in setOf("edit", "edit-diff") -> NbgToolVisualizationKind.Diff
    normalizedKind == "file" -> NbgToolVisualizationKind.File
    normalizedKind == "vision" -> NbgToolVisualizationKind.Vision
    normalizedKind == "thinking" -> NbgToolVisualizationKind.Thinking
    normalizedKind == "confirmation" -> NbgToolVisualizationKind.Confirmation
    normalizedKind == "team_task" || normalizedTool == "team_task_status" -> NbgToolVisualizationKind.TeamTask
    normalizedKind == "team_agent" || normalizedTool == "team_agent_status" -> NbgToolVisualizationKind.TeamAgent
    normalizedKind == "todo" || normalizedTool.contains("todo") -> NbgToolVisualizationKind.Todo
    else -> NbgToolVisualizationKind.Tool
  }
}

internal fun nbgNormalizeToolVisualizationState(
  status: String,
  running: Boolean = false,
  success: Boolean? = null,
): NbgToolVisualizationState {
  if (success == false) return NbgToolVisualizationState.Failed
  if (success == true) return NbgToolVisualizationState.Succeeded
  if (running) return NbgToolVisualizationState.Running
  return when (status.trim().lowercase()) {
    "running", "progress", "in_progress", "busy", "streaming", "applying", "writing" -> NbgToolVisualizationState.Running
    "done", "success", "succeeded", "complete", "completed", "ok" -> NbgToolVisualizationState.Succeeded
    "failed", "failure", "error", "errored" -> NbgToolVisualizationState.Failed
    "blocked", "denied", "rejected" -> NbgToolVisualizationState.Blocked
    "cancelled", "canceled", "aborted", "interrupted" -> NbgToolVisualizationState.Cancelled
    "restored_incomplete" -> NbgToolVisualizationState.RestoredIncomplete
    "", "pending", "queued", "waiting" -> NbgToolVisualizationState.Waiting
    else -> NbgToolVisualizationState.Waiting
  }
}

internal fun HanakoToolStatus.nbgToolVisualizationState(): NbgToolVisualizationState =
  if (!running && success == false && subtitle.contains("未收到结束事件")) {
    NbgToolVisualizationState.RestoredIncomplete
  } else {
    nbgNormalizeToolVisualizationState(status, running, success)
  }

internal fun HanakoToolStatus.nbgToolVisualizationKind(): NbgToolVisualizationKind =
  when {
    fileDiff != null -> NbgToolVisualizationKind.Diff
    filePreview != null -> NbgToolVisualizationKind.File
    terminalOutput != null -> NbgToolVisualizationKind.Terminal
    else -> nbgToolVisualizationKind(kind, toolName)
  }

internal fun HanakoTerminalOutput.nbgTerminalExitDetail(): NbgTerminalExitDetail? =
  exitCode?.let {
    NbgTerminalExitDetail(
      exitCode = it,
      state = if (it == 0) NbgToolVisualizationState.Succeeded else NbgToolVisualizationState.Failed,
      label = "exit $it",
    )
  }

internal fun nbgTodoListToolStatus(todos: List<HanakoTodoItem>): HanakoToolStatus? {
  if (todos.isEmpty()) return null
  val completedCount = todos.count { nbgTodoStatusCompleted(it.status) }
  val state = when {
    completedCount == todos.size -> NbgToolVisualizationState.Succeeded
    todos.any { nbgNormalizeTodoStatus(it.status) == "in_progress" } -> NbgToolVisualizationState.Running
    else -> NbgToolVisualizationState.Waiting
  }
  val current = todos.firstOrNull { nbgNormalizeTodoStatus(it.status) == "in_progress" }
    ?: todos.firstOrNull { nbgNormalizeTodoStatus(it.status) == "pending" }
    ?: todos.first()
  return HanakoToolStatus(
    key = "todo:session",
    kind = NbgToolVisualizationKind.Todo.wireName,
    toolName = "todo_status",
    title = "任务清单",
    subtitle = "$completedCount/${todos.size} / ${state.label}",
    detail = todos
      .take(8)
      .joinToString("\n") { "${it.status.nbgTodoProjectionLabel()}: ${it.nbgTodoProjectionText()}" },
    status = state.wireName,
    running = state == NbgToolVisualizationState.Running,
    success = if (state == NbgToolVisualizationState.Succeeded) true else null,
  ).copy(
    title = current.nbgTodoProjectionText().ifBlank { "任务清单" },
  )
}

internal fun HanakoTeamTaskStatus.nbgTeamTaskToolStatus(): HanakoToolStatus {
  val state = nbgTeamVisualizationState(status, isActive)
  val activeAgentCount = agents.count { it.running }
  val totalAgentCount = agents.size
  val modeLabel = when {
    isMultiAgentSession() -> "多 Agent"
    isSingleAgentExecution() -> "单 Agent"
    isBackendAgentToolExecution() -> "后台 Agent"
    else -> "团队"
  }
  val agentSummary = if (totalAgentCount > 0) "$activeAgentCount/$totalAgentCount 工作中" else "无成员状态"
  return HanakoToolStatus(
    key = "team_task:${taskId.nbgCompactProjectionField(limit = 96).ifBlank { "current" }}",
    kind = NbgToolVisualizationKind.TeamTask.wireName,
    toolName = "team_task_status",
    title = title.nbgCompactProjectionField().ifBlank { "团队任务" },
    subtitle = listOf(modeLabel, state.label, agentSummary).joinToString(" / "),
    detail = summary.nbgCompactProjectionField(limit = 240),
    status = state.wireName,
    running = state == NbgToolVisualizationState.Running,
    success = when (state) {
      NbgToolVisualizationState.Succeeded -> true
      NbgToolVisualizationState.Failed -> false
      else -> null
    },
  )
}

internal fun HanakoTeamAgentStatus.nbgTeamAgentToolStatus(): HanakoToolStatus {
  val state = nbgTeamVisualizationState(status, running)
  return HanakoToolStatus(
    key = "team_agent:${taskId.nbgCompactProjectionField(limit = 64).ifBlank { "current" }}:${agentId.nbgCompactProjectionField(limit = 64).ifBlank { nbgTeamAgentId(role) }}",
    kind = NbgToolVisualizationKind.TeamAgent.wireName,
    toolName = "team_agent_status",
    title = title.nbgCompactProjectionField().ifBlank { role.nbgCompactProjectionField().ifBlank { "Agent" } },
    subtitle = listOf(role.nbgCompactProjectionField(limit = 80), state.label)
      .filter { it.isNotBlank() }
      .distinct()
      .joinToString(" / "),
    detail = summary.nbgCompactProjectionField(limit = 240),
    status = state.wireName,
    running = state == NbgToolVisualizationState.Running,
    success = when (state) {
      NbgToolVisualizationState.Succeeded -> true
      NbgToolVisualizationState.Failed -> false
      else -> null
    },
  )
}

private fun nbgTeamVisualizationState(
  status: String,
  active: Boolean = false,
): NbgToolVisualizationState =
  when (nbgNormalizeTeamStatus(status)) {
    "queued", "idle", "pending", "" -> if (active) NbgToolVisualizationState.Running else NbgToolVisualizationState.Waiting
    "running", "thinking", "working", "coding", "reviewing", "testing", "terminal" -> NbgToolVisualizationState.Running
    "completed", "done", "success" -> NbgToolVisualizationState.Succeeded
    "failed", "error" -> NbgToolVisualizationState.Failed
    "aborted", "cancelled", "canceled" -> NbgToolVisualizationState.Cancelled
    else -> if (active) NbgToolVisualizationState.Running else NbgToolVisualizationState.Waiting
  }

private fun HanakoTodoItem.nbgTodoProjectionText(): String =
  if (nbgNormalizeTodoStatus(status) == "in_progress" && activeForm.isNotBlank()) {
    activeForm
  } else {
    content.ifBlank { activeForm }
  }.nbgCompactProjectionField(limit = 180)

private fun String.nbgTodoProjectionLabel(): String =
  when (nbgNormalizeTodoStatus(this)) {
    "in_progress" -> "进行中"
    "completed" -> "已完成"
    else -> "待处理"
  }

internal fun nbgConfirmationResolutionToolStatus(
  confirmId: String,
  action: String,
  confirmation: HanakoConfirmation? = null,
  auditEntry: NbgConfirmationResolutionAuditEntry? = null,
): HanakoToolStatus {
  val normalizedAction = action.trim().lowercase()
  val state = when (normalizedAction) {
    "confirmed", "approved", "accepted" -> NbgToolVisualizationState.Succeeded
    "rejected", "denied", "declined" -> NbgToolVisualizationState.Cancelled
    else -> NbgToolVisualizationState.Waiting
  }
  val resultLabel = when (state) {
    NbgToolVisualizationState.Succeeded -> "已允许"
    NbgToolVisualizationState.Cancelled -> "已拒绝"
    else -> "已处理"
  }
  val verifiedAuditEntry = auditEntry?.takeIf { nbgVerifyConfirmationResolutionAuditEntry(it) }
  val safeId = verifiedAuditEntry?.confirmIdHash?.take(32)
    ?: nbgBuildConfirmationResolutionAuditEntry(
      confirmId = confirmId,
      action = action,
      confirmation = confirmation,
      timestampMillis = 0L,
    ).confirmIdHash.take(32).ifBlank { "unknown" }
  val riskLabel = confirmation?.riskLabel?.nbgCompactConfirmationField()
    ?.takeIf { it.isNotBlank() }
  val subjectLabel = confirmation?.subjectLabel?.nbgCompactConfirmationField()
    ?.takeIf { it.isNotBlank() }
  val target = listOf(
    confirmation?.targetLabel,
    confirmation?.subjectDetail,
  )
    .mapNotNull { it?.nbgCompactConfirmationField()?.takeIf(String::isNotBlank) }
    .distinct()
    .joinToString(" / ")
  val recoveryHint = confirmation?.recoveryHint?.nbgCompactConfirmationField()
    ?.takeIf { it.isNotBlank() }
  val detail = listOfNotNull(
    target.takeIf { it.isNotBlank() }?.let { "目标：$it" },
    recoveryHint?.let { "恢复：$it" },
    verifiedAuditEntry?.let {
      "审计：${it.nbgConfirmationAuditTimelineLabel()}"
    },
  ).joinToString("\n")

  return HanakoToolStatus(
    key = "confirmation:$safeId",
    kind = NbgToolVisualizationKind.Confirmation.wireName,
    toolName = "session_confirmation",
    title = confirmation?.title?.nbgCompactConfirmationField()
      ?.takeIf { it.isNotBlank() }
      ?: "确认请求",
    subtitle = listOfNotNull(resultLabel, riskLabel, subjectLabel)
      .distinct()
      .joinToString(" / "),
    detail = detail,
    status = state.wireName,
    running = false,
    success = if (state == NbgToolVisualizationState.Succeeded) true else null,
  )
}

private fun String.nbgCompactConfirmationField(
  limit: Int = NBG_CONFIRMATION_TIMELINE_TEXT_LIMIT,
): String =
  nbgCompactProjectionField(limit)

private fun String.nbgCompactProjectionField(limit: Int = NBG_CONFIRMATION_TIMELINE_TEXT_LIMIT): String =
  trim()
    .replace(Regex("\\s+"), " ")
    .take(limit)

package com.nbg.android

internal class NbgAgentToolStatusState {
  private val pendingToolStatuses = linkedMapOf<String, HanakoToolStatus>()

  val hasPending: Boolean
    get() = pendingToolStatuses.isNotEmpty()

  fun clear() {
    pendingToolStatuses.clear()
  }

  fun enqueue(tool: HanakoToolStatus): NbgAgentToolStatusEnqueueResult {
    if (!tool.hasVisibleToolStatus()) {
      return NbgAgentToolStatusEnqueueResult(accepted = false, shouldFlushNow = false)
    }
    val key = pendingToolStatusKey(tool)
    val current = pendingToolStatuses[key]
    val keyedTool = tool.copy(key = key)
    pendingToolStatuses[key] = current?.mergeToolStatus(keyedTool) ?: keyedTool
    return NbgAgentToolStatusEnqueueResult(
      accepted = true,
      shouldFlushNow = !tool.running,
    )
  }

  fun drain(): List<HanakoToolStatus> {
    val updates = pendingToolStatuses.values.toList()
    pendingToolStatuses.clear()
    return updates
  }
}

internal data class NbgAgentToolStatusEnqueueResult(
  val accepted: Boolean,
  val shouldFlushNow: Boolean,
)

internal fun pendingToolStatusKey(tool: HanakoToolStatus): String =
  tool.nbgStableToolStatusKey()
    ?: listOf(tool.kind, tool.toolName, tool.filePath, tool.title).joinToString(":")

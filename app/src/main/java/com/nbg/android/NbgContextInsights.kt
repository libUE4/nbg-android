package com.nbg.android

import org.json.JSONObject

internal const val NBG_CONTEXT_INSIGHTS_VERSION = "nbg-context-insights-v1"

data class NbgContextUsageSnapshot(
  val totalTokens: Long = 0L,
  val inputTokens: Long = 0L,
  val outputTokens: Long = 0L,
  val contextLimit: Long = 0L,
  val percentUsed: Int = 0,
  val compressionAvailable: Boolean = false,
  val updatedAtMs: Long = 0L,
) {
  val label: String
    get() = when {
      totalTokens > 0 && contextLimit > 0 -> "用量 ${formatTokenCount(totalTokens)} / ${formatTokenCount(contextLimit)} ($percentUsed%)"
      totalTokens > 0 -> "用量 ${formatTokenCount(totalTokens)}"
      inputTokens > 0 || outputTokens > 0 -> listOfNotNull(
        inputTokens.takeIf { it > 0 }?.let { "in ${formatTokenCount(it)}" },
        outputTokens.takeIf { it > 0 }?.let { "out ${formatTokenCount(it)}" },
      ).joinToString(" / ")
      else -> "暂无用量"
    }
}

data class NbgContextInsights(
  val modelVersion: String = NBG_CONTEXT_INSIGHTS_VERSION,
  val usage: NbgContextUsageSnapshot = NbgContextUsageSnapshot(),
  val sessionCount: Int = 0,
  val learningItemCount: Int = 0,
  val pendingReviewCount: Int = 0,
  val lastRecallQuery: String = "",
  val suggestion: String = "",
) {
  val hasUsage: Boolean
    get() = usage.totalTokens > 0 || usage.inputTokens > 0 || usage.outputTokens > 0
}

internal fun nbgBuildContextInsights(
  usage: NbgContextUsageSnapshot,
  learning: NbgAutonomousLearningSnapshot,
  sessions: List<HanakoSessionSummary> = emptyList(),
): NbgContextInsights {
  val learningItems = learning.localMemory.enabledEntries.size +
    learning.userProfile.visibleEntries.size +
    learning.soul.principles.size +
    learning.learnedSkillDraftQueue.visibleEntries.size
  return NbgContextInsights(
    usage = usage,
    sessionCount = sessions.size,
    learningItemCount = learningItems,
    pendingReviewCount = learning.pendingReviewCount,
    lastRecallQuery = learning.recallBundle.query,
    suggestion = when {
      usage.compressionAvailable -> "当前会话可压缩"
      usage.percentUsed >= 80 -> "上下文接近上限"
      learning.pendingReviewCount > 0 -> "有学习项待复核"
      learning.recallBundle.hasResults -> "召回可用于下一轮"
      else -> "上下文健康"
    },
  )
}

internal fun parseNbgContextUsageSnapshot(msg: JSONObject, nowMs: Long = System.currentTimeMillis()): NbgContextUsageSnapshot {
  val usage = msg.optJSONObject("usage") ?: msg
  val total = usage.optLong("totalTokens", usage.optLong("total_tokens", usage.optLong("total", 0L))).coerceAtLeast(0L)
  val input = usage.optLong("inputTokens", usage.optLong("prompt_tokens", 0L)).coerceAtLeast(0L)
  val output = usage.optLong("outputTokens", usage.optLong("completion_tokens", 0L)).coerceAtLeast(0L)
  val limit = usage.optLong("contextLimit", usage.optLong("limit", 0L)).coerceAtLeast(0L)
  val percent = when {
    usage.has("percent") -> usage.optInt("percent", 0)
    limit > 0L && total > 0L -> ((total * 100L) / limit).toInt()
    else -> 0
  }.coerceIn(0, 100)
  return NbgContextUsageSnapshot(
    totalTokens = total,
    inputTokens = input,
    outputTokens = output,
    contextLimit = limit,
    percentUsed = percent,
    compressionAvailable = msg.optBoolean("compressionAvailable", false),
    updatedAtMs = nowMs.coerceAtLeast(0L),
  )
}

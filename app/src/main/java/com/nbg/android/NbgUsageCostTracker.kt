package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_USAGE_COST_VERSION = "nbg-usage-cost-v1"

data class NbgUsageCostEvent(
  val providerId: String = "",
  val modelId: String = "",
  val sessionPath: String = "",
  val inputTokens: Long = 0L,
  val outputTokens: Long = 0L,
  val totalTokens: Long = 0L,
  val latencyMs: Long = 0L,
  val failed: Boolean = false,
  val createdAtMs: Long = 0L,
)

data class NbgUsageCostBreakdown(
  val label: String,
  val providerId: String = "",
  val modelId: String = "",
  val sessionPath: String = "",
  val eventCount: Int = 0,
  val totalTokens: Long = 0L,
  val failureCount: Int = 0,
  val averageLatencyMs: Long = 0L,
  val estimatedCostUsd: Double = 0.0,
)

data class NbgUsageCostState(
  val events: List<NbgUsageCostEvent> = emptyList(),
  val modelVersion: String = NBG_USAGE_COST_VERSION,
) {
  val totalTokens: Long
    get() = events.sumOf { it.totalTokens.coerceAtLeast(it.inputTokens + it.outputTokens) }

  val failureCount: Int
    get() = events.count { it.failed }

  val estimatedCostUsd: Double
    get() = events.sumOf { event ->
      val tokens = event.totalTokens.coerceAtLeast(event.inputTokens + event.outputTokens)
      tokens / 1_000_000.0 * nbgEstimatedUsdPerMillion(event.modelId)
    }

  val providerBreakdowns: List<NbgUsageCostBreakdown>
    get() = events
      .groupBy { it.providerId.ifBlank { "unknown" } }
      .map { (providerId, grouped) ->
        grouped.toUsageCostBreakdown(label = providerId, providerId = providerId)
      }
      .sortedByDescending { it.totalTokens }

  val modelBreakdowns: List<NbgUsageCostBreakdown>
    get() = events
      .groupBy { it.modelId.ifBlank { "unknown" } }
      .map { (modelId, grouped) ->
        grouped.toUsageCostBreakdown(label = modelId, modelId = modelId)
      }
      .sortedByDescending { it.totalTokens }

  val sessionBreakdowns: List<NbgUsageCostBreakdown>
    get() = events
      .groupBy { it.sessionPath.ifBlank { "current" } }
      .map { (sessionPath, grouped) ->
        grouped.toUsageCostBreakdown(label = sessionPath.substringAfterLast('/').ifBlank { "current" }, sessionPath = sessionPath)
      }
      .sortedByDescending { it.totalTokens }
}

internal class NbgUsageCostStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(): NbgUsageCostState =
    parseNbgUsageCostState(prefs.getString(KEY_STATE, null))

  fun record(event: NbgUsageCostEvent): NbgUsageCostState {
    val normalized = event.normalized()
    val next = NbgUsageCostState((listOf(normalized) + load().events).take(300))
    prefs.edit().putString(KEY_STATE, next.toJsonString()).apply()
    return next
  }

  private companion object {
    const val PREFS = "nbg_usage_cost"
    const val KEY_STATE = "state"
  }
}

internal fun parseNbgUsageCostState(raw: String?): NbgUsageCostState =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    val array = root.optJSONArray("events") ?: JSONArray()
    NbgUsageCostState(
      events = buildList {
        for (index in 0 until array.length()) {
          val item = array.optJSONObject(index) ?: continue
          add(
            NbgUsageCostEvent(
              providerId = item.cleanString("providerId").orEmpty(),
              modelId = item.cleanString("modelId").orEmpty(),
              sessionPath = item.cleanString("sessionPath").orEmpty(),
              inputTokens = item.optLong("inputTokens", 0L),
              outputTokens = item.optLong("outputTokens", 0L),
              totalTokens = item.optLong("totalTokens", 0L),
              latencyMs = item.optLong("latencyMs", 0L),
              failed = item.optBoolean("failed", false),
              createdAtMs = item.optLong("createdAtMs", 0L),
            ).normalized(),
          )
        }
      },
    )
  }.getOrDefault(NbgUsageCostState())

private fun NbgUsageCostState.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("events", JSONArray(events.map { it.toJson() }))
    .toString()

private fun NbgUsageCostEvent.toJson(): JSONObject =
  JSONObject()
    .put("providerId", providerId)
    .put("modelId", modelId)
    .put("sessionPath", sessionPath)
    .put("inputTokens", inputTokens)
    .put("outputTokens", outputTokens)
    .put("totalTokens", totalTokens)
    .put("latencyMs", latencyMs)
    .put("failed", failed)
    .put("createdAtMs", createdAtMs)

private fun NbgUsageCostEvent.normalized(): NbgUsageCostEvent =
  copy(
    providerId = providerId.take(80),
    modelId = modelId.take(160),
    sessionPath = sessionPath.take(240),
    inputTokens = inputTokens.coerceAtLeast(0L),
    outputTokens = outputTokens.coerceAtLeast(0L),
    totalTokens = totalTokens.coerceAtLeast(0L),
    latencyMs = latencyMs.coerceAtLeast(0L),
    createdAtMs = createdAtMs.coerceAtLeast(0L),
  )

private fun nbgEstimatedUsdPerMillion(modelId: String): Double {
  val id = modelId.lowercase()
  return when {
    "gpt-5" in id -> 5.0
    "gpt-4" in id -> 10.0
    "claude" in id -> 6.0
    "gemini" in id -> 2.5
    "qwen" in id || "kimi" in id || "deepseek" in id -> 1.0
    else -> 2.0
  }
}

private fun List<NbgUsageCostEvent>.toUsageCostBreakdown(
  label: String,
  providerId: String = "",
  modelId: String = "",
  sessionPath: String = "",
): NbgUsageCostBreakdown {
  val tokens = sumOf { it.totalTokens.coerceAtLeast(it.inputTokens + it.outputTokens) }
  val latencyEvents = filter { it.latencyMs > 0L }
  return NbgUsageCostBreakdown(
    label = label.take(160),
    providerId = providerId,
    modelId = modelId,
    sessionPath = sessionPath,
    eventCount = size,
    totalTokens = tokens,
    failureCount = count { it.failed },
    averageLatencyMs = if (latencyEvents.isEmpty()) 0L else latencyEvents.sumOf { it.latencyMs } / latencyEvents.size,
    estimatedCostUsd = sumOf { event ->
      val eventTokens = event.totalTokens.coerceAtLeast(event.inputTokens + event.outputTokens)
      eventTokens / 1_000_000.0 * nbgEstimatedUsdPerMillion(event.modelId)
    },
  )
}

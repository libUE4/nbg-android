package com.nbg.android

import android.content.Context
import org.json.JSONObject

internal const val NBG_CONTEXT_COMPRESSION_STRATEGY_VERSION = "nbg-context-compression-strategy-v1"

enum class NbgContextCompressionMode(val wireName: String, val label: String) {
  Off("off", "关闭"),
  Remind("remind", "提醒"),
  Auto("auto", "自动"),
}

data class NbgContextCompressionStrategy(
  val mode: NbgContextCompressionMode = NbgContextCompressionMode.Remind,
  val thresholdPercent: Int = 85,
  val minIntervalMs: Long = 15 * 60 * 1000L,
  val lastTriggeredAtMs: Long = 0L,
  val triggerCount: Int = 0,
  val modelVersion: String = NBG_CONTEXT_COMPRESSION_STRATEGY_VERSION,
) {
  fun shouldTrigger(usage: NbgContextUsageSnapshot, nowMs: Long = System.currentTimeMillis()): Boolean =
    mode != NbgContextCompressionMode.Off &&
      usage.compressionAvailable &&
      usage.percentUsed >= thresholdPercent.coerceIn(50, 99) &&
      nowMs.coerceAtLeast(0L) - lastTriggeredAtMs >= minIntervalMs.coerceAtLeast(60_000L)
}

internal class NbgContextCompressionStrategyStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(): NbgContextCompressionStrategy =
    parseNbgContextCompressionStrategy(prefs.getString(KEY_STATE, null))

  fun save(strategy: NbgContextCompressionStrategy): NbgContextCompressionStrategy {
    val normalized = strategy.normalized()
    prefs.edit().putString(KEY_STATE, normalized.toJsonString()).apply()
    return normalized
  }

  fun setMode(mode: NbgContextCompressionMode): NbgContextCompressionStrategy =
    save(load().copy(mode = mode))

  fun recordTrigger(nowMs: Long = System.currentTimeMillis()): NbgContextCompressionStrategy {
    val current = load()
    return save(current.copy(lastTriggeredAtMs = nowMs.coerceAtLeast(0L), triggerCount = current.triggerCount + 1))
  }

  private companion object {
    const val PREFS = "nbg_context_compression_strategy"
    const val KEY_STATE = "state"
  }
}

internal fun parseNbgContextCompressionStrategy(raw: String?): NbgContextCompressionStrategy =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    NbgContextCompressionStrategy(
      mode = nbgContextCompressionMode(root.cleanString("mode").orEmpty()),
      thresholdPercent = root.optInt("thresholdPercent", 85),
      minIntervalMs = root.optLong("minIntervalMs", 15 * 60 * 1000L),
      lastTriggeredAtMs = root.optLong("lastTriggeredAtMs", 0L),
      triggerCount = root.optInt("triggerCount", 0),
    ).normalized()
  }.getOrDefault(NbgContextCompressionStrategy())

private fun nbgContextCompressionMode(value: String): NbgContextCompressionMode =
  NbgContextCompressionMode.values().firstOrNull { it.wireName == value.trim().lowercase() }
    ?: NbgContextCompressionMode.Remind

private fun NbgContextCompressionStrategy.normalized(): NbgContextCompressionStrategy =
  copy(
    thresholdPercent = thresholdPercent.coerceIn(50, 99),
    minIntervalMs = minIntervalMs.coerceAtLeast(60_000L),
    lastTriggeredAtMs = lastTriggeredAtMs.coerceAtLeast(0L),
    triggerCount = triggerCount.coerceAtLeast(0),
  )

private fun NbgContextCompressionStrategy.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("mode", mode.wireName)
    .put("thresholdPercent", thresholdPercent)
    .put("minIntervalMs", minIntervalMs)
    .put("lastTriggeredAtMs", lastTriggeredAtMs)
    .put("triggerCount", triggerCount)
    .toString()

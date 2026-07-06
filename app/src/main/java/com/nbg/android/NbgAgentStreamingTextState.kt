package com.nbg.android

internal class NbgAgentStreamingTextState {
  private val pendingAssistantDeltas = mutableMapOf<Long, StringBuilder>()
  private val assistantRawTexts = mutableMapOf<Long, StringBuilder>()
  private val thinkingRawTexts = mutableMapOf<Long, StringBuilder>()
  private val pendingThinkingDeltas = mutableMapOf<Long, StringBuilder>()

  val hasPendingDeltas: Boolean
    get() = pendingAssistantDeltas.isNotEmpty() || pendingThinkingDeltas.isNotEmpty()

  fun clear() {
    pendingAssistantDeltas.clear()
    assistantRawTexts.clear()
    thinkingRawTexts.clear()
    pendingThinkingDeltas.clear()
  }

  fun appendAssistantVisibleText(messageId: Long, rawDelta: String): String {
    val rawText = assistantRawTexts.getOrPut(messageId) { StringBuilder() }
    rawText.append(rawDelta)
    return nbgCleanAssistantVisibleStreamingText(rawText.toString())
  }

  fun replaceAssistantVisibleText(messageId: Long, rawText: String): String {
    val buffer = assistantRawTexts.getOrPut(messageId) { StringBuilder() }
    buffer.clear()
    buffer.append(rawText)
    return nbgDisplayTextForRole("assistant", buffer.toString())
  }

  fun appendThinkingVisibleText(thinkingId: Long, rawDelta: String): String {
    val rawText = thinkingRawTexts.getOrPut(thinkingId) { StringBuilder() }
    rawText.append(rawDelta)
    return nbgThinkingTextForAndroidDisplay(rawText.toString())
  }

  fun replaceThinkingVisibleText(thinkingId: Long, rawText: String): String {
    val buffer = thinkingRawTexts.getOrPut(thinkingId) { StringBuilder() }
    buffer.clear()
    buffer.append(rawText)
    return nbgThinkingTextForAndroidDisplay(buffer.toString(), allowTranslatingPlaceholder = true)
  }

  fun enqueueAssistantDelta(messageId: Long, delta: String): Boolean {
    if (delta.isEmpty()) return false
    pendingAssistantDeltas.getOrPut(messageId) { StringBuilder() }.append(delta)
    return true
  }

  fun enqueueThinkingDelta(thinkingId: Long, delta: String): Boolean {
    if (delta.isEmpty()) return false
    pendingThinkingDeltas.getOrPut(thinkingId) { StringBuilder() }.append(delta)
    return true
  }

  fun drainPendingDeltas(): NbgAgentStreamingDeltaBatch {
    val assistantDeltas = pendingAssistantDeltas.mapValues { it.value.toString() }
    val thinkingDeltas = pendingThinkingDeltas.mapValues { it.value.toString() }
    pendingAssistantDeltas.clear()
    pendingThinkingDeltas.clear()
    return NbgAgentStreamingDeltaBatch(assistantDeltas, thinkingDeltas)
  }

  fun removePendingAssistantDelta(messageId: Long) {
    pendingAssistantDeltas.remove(messageId)
  }

  fun removePendingThinkingDelta(thinkingId: Long): String? =
    pendingThinkingDeltas.remove(thinkingId)?.toString()

  fun removeAssistantRawText(messageId: Long) {
    assistantRawTexts.remove(messageId)
  }

  fun ensureAssistantRawText(messageId: Long) {
    assistantRawTexts.getOrPut(messageId) { StringBuilder() }
  }

  fun removeThinkingRawText(thinkingId: Long) {
    thinkingRawTexts.remove(thinkingId)
  }

  fun pruneFinishedAssistantRawTexts(streamingAssistantIds: Set<Long>) {
    assistantRawTexts.keys.removeAll { it !in streamingAssistantIds }
  }
}

internal data class NbgAgentStreamingDeltaBatch(
  val assistantDeltas: Map<Long, String>,
  val thinkingDeltas: Map<Long, String>,
)

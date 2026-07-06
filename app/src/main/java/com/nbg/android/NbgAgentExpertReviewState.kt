package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentExpertReviewState {
  var prompt by mutableStateOf("")
  var selectedModelKeys by mutableStateOf<Set<String>>(emptySet())
  var running by mutableStateOf(false)
  var message by mutableStateOf("")
  var result by mutableStateOf<NbgExpertReviewRunResult?>(null)
  private var requestSerial = 0L

  fun nextRequestSerial(): Long {
    requestSerial += 1
    return requestSerial
  }

  fun isCurrent(serial: Long): Boolean = serial == requestSerial

  fun toggleModel(model: NbgExpertReviewModelRef) {
    val key = model.expertReviewKey
    selectedModelKeys = if (key in selectedModelKeys) selectedModelKeys - key else selectedModelKeys + key
  }

  fun selectedModels(entries: List<NbgStoredApi>): List<NbgExpertReviewModelRef> {
    val refs = nbgExpertReviewModelRefs(entries)
    val selected = refs.filter { it.expertReviewKey in selectedModelKeys }
    return selected.ifEmpty { refs.take(2) }
  }

  fun syncAvailableModels(entries: List<NbgStoredApi>) {
    val available = nbgExpertReviewModelRefs(entries).map { it.expertReviewKey }.toSet()
    selectedModelKeys = selectedModelKeys.filter { it in available }.toSet()
  }

  fun applyResult(next: NbgExpertReviewRunResult) {
    result = next
    message = "完成 ${next.completedCount}/${next.references.size} 个参考输出"
    running = false
  }

  fun applyFailure(error: Throwable) {
    message = error.message.orEmpty().ifBlank { error::class.java.simpleName }
    running = false
  }

  fun cancelCurrentRun() {
    nextRequestSerial()
    running = false
    message = "已取消本次评审"
  }
}

internal val NbgExpertReviewModelRef.expertReviewKey: String
  get() = "$providerId\u001f$modelId"

package com.nbg.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal class NbgAgentApiEditorState {
  var isOpen by mutableStateOf(false)
  var editingApi by mutableStateOf<NbgStoredApi?>(null)
  var models by mutableStateOf<List<NbgApiModel>>(emptyList())
  var verifiedModelIds by mutableStateOf<Set<String>>(emptySet())
  var selectedModelId by mutableStateOf("")
  var actionMessage by mutableStateOf<String?>(null)
  var busy by mutableStateOf(false)
  var nameDraft by mutableStateOf("")
  var baseUrlDraft by mutableStateOf("")
  var effectiveBaseUrlDraft by mutableStateOf("")
  var apiKeyDraft by mutableStateOf("")
  var manualModelDraft by mutableStateOf("")
  var requestDiagnostics by mutableStateOf<NbgUrlApiRequestDiagnostics?>(null)
  var requestSerial by mutableStateOf(0L)
    private set

  fun open(entry: NbgStoredApi? = null) {
    editingApi = entry
    nameDraft = entry?.name.orEmpty()
    baseUrlDraft = entry?.baseUrl.orEmpty()
    effectiveBaseUrlDraft = entry?.baseUrl.orEmpty()
    apiKeyDraft = entry?.apiKey.orEmpty()
    manualModelDraft = ""
    requestDiagnostics = null
    models = entry?.models.orEmpty()
    verifiedModelIds = entry?.verifiedModelIds.orEmpty()
    selectedModelId = entry?.selectedModelId.orEmpty()
    actionMessage = null
    busy = false
    isOpen = true
  }

  fun close() {
    cancelPendingRequests()
    editingApi = null
    isOpen = false
    actionMessage = null
    busy = false
  }

  fun updateManualModel(value: String) {
    manualModelDraft = value
  }

  fun addManualModel() {
    val id = manualModelDraft.trim()
    if (id.isBlank()) return
    val model = NbgApiModel(id = id, label = id)
    models = (models + model).distinctBy { it.id }
    selectedModelId = id
    manualModelDraft = ""
    actionMessage = "已添加手动模型 $id，请验证可用后保存"
  }

  fun applyDiagnostics(diagnostics: NbgUrlApiRequestDiagnostics) {
    requestDiagnostics = diagnostics
    actionMessage = diagnostics.endpoints.lastOrNull()?.message ?: "诊断完成"
    busy = false
  }

  fun updateBaseUrl(value: String) {
    if (baseUrlDraft == value) return
    cancelPendingRequests()
    baseUrlDraft = value
    clearVerificationAfterCredentialChange()
  }

  fun updateApiKey(value: String) {
    if (apiKeyDraft == value) return
    cancelPendingRequests()
    apiKeyDraft = value
    clearVerificationAfterCredentialChange()
  }

  fun nextRequestSerial(): Long = cancelPendingRequests()

  fun isCurrentRequest(serial: Long): Boolean = serial == requestSerial

  fun applyFetchedModels(result: NbgApiModelFetchResult) {
    models = result.models
    selectedModelId = result.models.firstOrNull()?.id.orEmpty()
    verifiedModelIds = emptySet()
    effectiveBaseUrlDraft = ""
    actionMessage = result.message
    busy = false
  }

  fun applyVerifiedModel(
    modelId: String,
    result: NbgApiModelVerifyResult,
    fallbackEffectiveBaseUrl: String,
  ) {
    if (result.ok) {
      verifiedModelIds = verifiedModelIds + modelId
      selectedModelId = modelId
      effectiveBaseUrlDraft = result.effectiveBaseUrl.ifBlank { fallbackEffectiveBaseUrl }
    }
    actionMessage = result.message
    busy = false
  }

  fun selectedVerifiedModelId(): String =
    selectedModelId
      .takeIf { it in verifiedModelIds }
      .orEmpty()
      .ifBlank { verifiedModelIds.firstOrNull().orEmpty() }

  private fun cancelPendingRequests(): Long {
    requestSerial += 1
    return requestSerial
  }

  private fun clearVerificationAfterCredentialChange() {
    effectiveBaseUrlDraft = ""
    verifiedModelIds = emptySet()
    requestDiagnostics = null
    busy = false
  }
}

@Composable
internal fun rememberNbgAgentApiEditorState(): NbgAgentApiEditorState =
  remember { NbgAgentApiEditorState() }

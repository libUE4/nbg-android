package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentApiEditorStateTest {
  @Test
  fun openCopiesStoredApiIntoEditableDraftsAndCloseCancelsRequests() {
    val entry = NbgStoredApi(
      id = "api-1",
      name = "Example",
      baseUrl = "https://api.example.com/v1",
      apiKey = "sk-test",
      models = listOf(NbgApiModel("model-a"), NbgApiModel("model-b")),
      verifiedModelIds = setOf("model-b"),
      selectedModelId = "model-b",
    )
    val state = NbgAgentApiEditorState()

    state.open(entry)

    assertTrue(state.isOpen)
    assertEquals(entry, state.editingApi)
    assertEquals("Example", state.nameDraft)
    assertEquals("https://api.example.com/v1", state.baseUrlDraft)
    assertEquals("https://api.example.com/v1", state.effectiveBaseUrlDraft)
    assertEquals("sk-test", state.apiKeyDraft)
    assertEquals(listOf(NbgApiModel("model-a"), NbgApiModel("model-b")), state.models)
    assertEquals(setOf("model-b"), state.verifiedModelIds)
    assertEquals("model-b", state.selectedModelId)

    val beforeClose = state.requestSerial
    state.busy = true
    state.actionMessage = "loading"
    state.close()

    assertFalse(state.isOpen)
    assertEquals(null, state.editingApi)
    assertFalse(state.busy)
    assertEquals(null, state.actionMessage)
    assertEquals(beforeClose + 1, state.requestSerial)
  }

  @Test
  fun credentialChangesClearVerificationAndInvalidateOlderRequests() {
    val state = NbgAgentApiEditorState()
    state.open()
    state.busy = true
    state.effectiveBaseUrlDraft = "https://effective.example.com/v1"
    state.verifiedModelIds = setOf("model-a")

    val beforeUrlChange = state.requestSerial
    state.updateBaseUrl("https://new.example.com")

    assertEquals(beforeUrlChange + 1, state.requestSerial)
    assertEquals("https://new.example.com", state.baseUrlDraft)
    assertEquals("", state.effectiveBaseUrlDraft)
    assertEquals(emptySet<String>(), state.verifiedModelIds)
    assertFalse(state.busy)

    val afterUrlChange = state.requestSerial
    state.updateBaseUrl("https://new.example.com")
    assertEquals(afterUrlChange, state.requestSerial)

    state.verifiedModelIds = setOf("model-b")
    state.effectiveBaseUrlDraft = "https://effective.example.com/v1"
    val beforeKeyChange = state.requestSerial
    state.updateApiKey("sk-next")

    assertEquals(beforeKeyChange + 1, state.requestSerial)
    assertEquals("sk-next", state.apiKeyDraft)
    assertEquals("", state.effectiveBaseUrlDraft)
    assertEquals(emptySet<String>(), state.verifiedModelIds)
  }

  @Test
  fun requestResultsAndSaveSelectionOnlyUseCurrentVerifiedModels() {
    val state = NbgAgentApiEditorState()
    val serial = state.nextRequestSerial()

    assertTrue(state.isCurrentRequest(serial))
    assertFalse(state.isCurrentRequest(serial - 1))

    state.applyFetchedModels(
      NbgApiModelFetchResult(
        models = listOf(NbgApiModel("model-a"), NbgApiModel("model-b")),
        message = "loaded",
      ),
    )

    assertEquals(listOf(NbgApiModel("model-a"), NbgApiModel("model-b")), state.models)
    assertEquals("model-a", state.selectedModelId)
    assertEquals(emptySet<String>(), state.verifiedModelIds)
    assertEquals("", state.effectiveBaseUrlDraft)
    assertEquals("loaded", state.actionMessage)

    state.applyVerifiedModel(
      modelId = "model-b",
      result = NbgApiModelVerifyResult(ok = true, message = "verified"),
      fallbackEffectiveBaseUrl = "https://fallback.example.com/v1",
    )

    assertEquals(setOf("model-b"), state.verifiedModelIds)
    assertEquals("model-b", state.selectedModelId)
    assertEquals("https://fallback.example.com/v1", state.effectiveBaseUrlDraft)
    assertEquals("verified", state.actionMessage)
    assertEquals("model-b", state.selectedVerifiedModelId())

    state.selectedModelId = "model-a"
    assertEquals("model-b", state.selectedVerifiedModelId())
  }
}

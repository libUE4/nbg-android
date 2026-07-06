package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentExpertReviewStateTest {
  @Test
  fun selectedModelsDefaultToFirstTwoVerifiedModels() {
    val state = NbgAgentExpertReviewState()
    val entries = listOf(api("provider-a", "model-a"), api("provider-b", "model-b"), api("provider-c", "model-c"))

    val selected = state.selectedModels(entries)

    assertEquals(listOf("model-a", "model-b"), selected.map { it.modelId })
  }

  @Test
  fun toggleModelUsesExplicitSelectionInsteadOfDefaultPair() {
    val state = NbgAgentExpertReviewState()
    val entries = listOf(api("provider-a", "model-a"), api("provider-b", "model-b"), api("provider-c", "model-c"))
    val modelC = nbgExpertReviewModelRefs(entries).single { it.modelId == "model-c" }

    state.toggleModel(modelC)

    assertEquals(listOf("model-c"), state.selectedModels(entries).map { it.modelId })
    assertEquals(setOf(modelC.expertReviewKey), state.selectedModelKeys)
  }

  @Test
  fun syncAvailableModelsDropsRemovedSelectionsAndFallsBackToDefaultPair() {
    val state = NbgAgentExpertReviewState()
    val originalEntries = listOf(api("provider-a", "model-a"), api("provider-b", "model-b"), api("provider-c", "model-c"))
    val modelC = nbgExpertReviewModelRefs(originalEntries).single { it.modelId == "model-c" }
    state.toggleModel(modelC)

    val remainingEntries = listOf(api("provider-a", "model-a"), api("provider-b", "model-b"))
    state.syncAvailableModels(remainingEntries)

    assertTrue(state.selectedModelKeys.isEmpty())
    assertEquals(listOf("model-a", "model-b"), state.selectedModels(remainingEntries).map { it.modelId })
  }

  @Test
  fun requestSerialPreventsOldRunFromBeingCurrent() {
    val state = NbgAgentExpertReviewState()

    val first = state.nextRequestSerial()
    val second = state.nextRequestSerial()

    assertFalse(state.isCurrent(first))
    assertTrue(state.isCurrent(second))
  }

  @Test
  fun cancelCurrentRunClearsRunningStateAndInvalidatesInFlightResult() {
    val state = NbgAgentExpertReviewState()
    val serial = state.nextRequestSerial()
    state.running = true
    state.message = "正在调用 2 个模型..."

    state.cancelCurrentRun()

    assertFalse(state.running)
    assertEquals("已取消本次评审", state.message)
    assertFalse(state.isCurrent(serial))
  }

  @Test
  fun applyResultAndFailureClearRunningStateWithUserMessage() {
    val state = NbgAgentExpertReviewState()
    val models = listOf(
      NbgExpertReviewModelRef("provider-a", "Provider A", "model-a", "Model A"),
      NbgExpertReviewModelRef("provider-b", "Provider B", "model-b", "Model B"),
    )
    state.running = true

    state.applyResult(
      nbgBuildExpertReviewRunResult(
        prompt = "review",
        requestedModels = models,
        references = listOf(
          NbgExpertReviewReferenceOutput(models[0], ok = true, output = "ok"),
          NbgExpertReviewReferenceOutput(models[1], ok = false, output = "", error = "timeout"),
        ),
      ),
    )

    assertFalse(state.running)
    assertEquals("完成 1/2 个参考输出", state.message)

    state.running = true
    state.applyFailure(IllegalStateException("boom"))

    assertFalse(state.running)
    assertEquals("boom", state.message)
  }

  private fun api(providerId: String, modelId: String): NbgStoredApi =
    NbgStoredApi(
      id = providerId,
      name = "Provider $providerId",
      baseUrl = "https://$providerId.example/v1",
      apiKey = "key-$providerId",
      models = listOf(NbgApiModel(id = modelId, label = "Model $modelId")),
      verifiedModelIds = setOf(modelId),
      selectedModelId = modelId,
    )
}

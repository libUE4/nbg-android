package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgExpertReviewPolicyTest {
  @Test
  fun expertReviewIsDefaultOffAndRequiresExplicitUserTrigger() {
    val models = listOf(
      NbgExpertReviewModelRef(
        providerId = "provider-a",
        providerName = "Provider A",
        modelId = "model-a",
      ),
      NbgExpertReviewModelRef(
        providerId = "provider-b",
        providerName = "Provider B",
        modelId = "model-b",
      ),
    )

    val review = nbgReviewExpertReviewRequest(
      models = models,
      explicitUserTrigger = false,
    )

    assertEquals(NBG_EXPERT_REVIEW_POLICY_VERSION, review.policyVersion)
    assertFalse(review.defaultEnabled)
    assertFalse(review.allowStart)
    assertFalse(review.explicitUserTrigger)
    assertEquals(2, review.modelCount)
    assertEquals(2, review.providerCount)
    assertTrue(review.requiresSeparateReferenceOutputs)
    assertTrue(review.consolidationRequired)
    assertFalse(review.toolSideEffectsAllowed)
    assertTrue(review.reason.contains("显式触发"))
    assertTrue(review.costLatencyWarning.contains("2 个模型"))
  }

  @Test
  fun expertReviewRequiresAtLeastTwoUniqueModelsAndNoToolSideEffects() {
    val duplicateModels = listOf(
      NbgExpertReviewModelRef(
        providerId = "provider-a",
        providerName = "Provider A",
        modelId = "model-a",
      ),
      NbgExpertReviewModelRef(
        providerId = "provider-a",
        providerName = "Provider A",
        modelId = "model-a",
      ),
    )
    val withSideEffects = listOf(
      NbgExpertReviewModelRef(
        providerId = "provider-a",
        providerName = "Provider A",
        modelId = "model-a",
      ),
      NbgExpertReviewModelRef(
        providerId = "provider-a",
        providerName = "Provider A",
        modelId = "model-b",
      ),
    )

    val duplicateReview = nbgReviewExpertReviewRequest(
      models = duplicateModels,
      explicitUserTrigger = true,
    )
    val sideEffectReview = nbgReviewExpertReviewRequest(
      models = withSideEffects,
      explicitUserTrigger = true,
      toolSideEffectsAllowed = true,
    )

    assertFalse(duplicateReview.allowStart)
    assertEquals(1, duplicateReview.modelCount)
    assertEquals(1, duplicateReview.providerCount)
    assertTrue(duplicateReview.reason.contains("至少需要两个"))

    assertFalse(sideEffectReview.allowStart)
    assertEquals(2, sideEffectReview.modelCount)
    assertEquals(1, sideEffectReview.providerCount)
    assertFalse(sideEffectReview.toolSideEffectsAllowed)
    assertTrue(sideEffectReview.reason.contains("不允许工具"))
  }

  @Test
  fun explicitReadOnlyExpertReviewCanStartWithSeparateReferenceOutputs() {
    val openAi = NbgStoredApi(
      id = "https://api.openai.com/v1",
      name = "OpenAI",
      baseUrl = "https://api.openai.com/v1",
      apiKey = "secret",
    )
    val local = NbgStoredApi(
      id = "local-provider",
      name = "",
      baseUrl = "http://127.0.0.1:11434/v1",
      apiKey = "",
    )
    val models = listOf(
      nbgExpertReviewModelRef(openAi, NbgApiModel(id = "gpt-5-mini", label = "GPT-5 mini")),
      nbgExpertReviewModelRef(local, NbgApiModel(id = "qwen-local", label = "")),
    )

    val review = nbgReviewExpertReviewRequest(
      models = models,
      explicitUserTrigger = true,
    )

    assertTrue(review.allowStart)
    assertFalse(review.defaultEnabled)
    assertEquals(2, review.modelCount)
    assertEquals(2, review.providerCount)
    assertTrue(review.requiresSeparateReferenceOutputs)
    assertTrue(review.consolidationRequired)
    assertFalse(review.toolSideEffectsAllowed)
    assertTrue(review.reason.contains("只读专家评审"))
    assertTrue(review.costLatencyWarning.contains("2 个 provider"))
    assertEquals("OpenAI", models[0].providerName)
    assertEquals("http://127.0.0.1:11434/v1", models[1].providerName)
    assertEquals("qwen-local", models[1].modelLabel)
  }

  @Test
  fun expertReviewModelRefsUseOnlyVerifiedUrlApiModels() {
    val entries = listOf(
      NbgStoredApi(
        id = "provider-a",
        name = "Provider A",
        baseUrl = "https://a.example/v1",
        apiKey = "secret",
        models = listOf(
          NbgApiModel(id = "verified-a", label = "Verified A"),
          NbgApiModel(id = "unverified-a", label = "Unverified A"),
        ),
        verifiedModelIds = setOf("verified-a"),
      ),
      NbgStoredApi(
        id = "provider-b",
        name = "Provider B",
        baseUrl = "https://b.example/v1",
        apiKey = "secret",
        models = listOf(NbgApiModel(id = "verified-b", label = "")),
        verifiedModelIds = setOf("verified-b"),
      ),
    )

    val refs = nbgExpertReviewModelRefs(entries)
    val review = nbgReviewExpertReviewRequest(refs, explicitUserTrigger = true)

    assertEquals(listOf("verified-a", "verified-b"), refs.map { it.modelId })
    assertEquals("Verified A", refs[0].modelLabel)
    assertEquals("verified-b", refs[1].modelLabel)
    assertTrue(review.allowStart)
    assertEquals(2, review.modelCount)
    assertEquals(2, review.providerCount)
  }

  @Test
  fun expertReviewRunResultKeepsSeparateReferencesAndConsolidatesLocally() {
    val models = listOf(
      NbgExpertReviewModelRef("provider-a", "Provider A", "model-a", "Model A"),
      NbgExpertReviewModelRef("provider-b", "Provider B", "model-b", "Model B"),
    )
    val result = nbgBuildExpertReviewRunResult(
      prompt = "检查这个方案",
      requestedModels = models,
      references = listOf(
        NbgExpertReviewReferenceOutput(models[0], ok = true, output = "A says yes"),
        NbgExpertReviewReferenceOutput(models[1], ok = false, output = "", error = "timeout"),
      ),
    )

    assertTrue(result.policyReview.allowStart)
    assertEquals(2, result.references.size)
    assertEquals(1, result.completedCount)
    assertEquals(1, result.failedCount)
    assertTrue(result.consolidatedSummary.contains("参考输出 1/2"))
    assertTrue(result.consolidatedSummary.contains("失败 1"))
  }
}

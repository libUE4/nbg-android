package com.nbg.android

internal const val NBG_EXPERT_REVIEW_POLICY_VERSION = "nbg-expert-review-v1"
internal const val NBG_EXPERT_REVIEW_DEFAULT_ENABLED = false

internal data class NbgExpertReviewModelRef(
  val providerId: String,
  val providerName: String,
  val modelId: String,
  val modelLabel: String = modelId,
)

internal data class NbgExpertReviewPolicyReview(
  val policyVersion: String,
  val defaultEnabled: Boolean,
  val allowStart: Boolean,
  val explicitUserTrigger: Boolean,
  val modelCount: Int,
  val providerCount: Int,
  val requiresSeparateReferenceOutputs: Boolean,
  val consolidationRequired: Boolean,
  val toolSideEffectsAllowed: Boolean,
  val costLatencyWarning: String,
  val reason: String,
)

internal fun nbgExpertReviewModelRef(entry: NbgStoredApi, model: NbgApiModel): NbgExpertReviewModelRef =
  NbgExpertReviewModelRef(
    providerId = nbgUrlApiProviderId(entry.id),
    providerName = entry.name.trim().ifBlank { entry.baseUrl.trim() },
    modelId = model.id.trim(),
    modelLabel = model.label.trim().ifBlank { model.id.trim() },
  )

internal fun nbgReviewExpertReviewRequest(
  models: List<NbgExpertReviewModelRef>,
  explicitUserTrigger: Boolean,
  toolSideEffectsAllowed: Boolean = false,
): NbgExpertReviewPolicyReview {
  val uniqueModels = models
    .map {
      it.copy(
        providerId = it.providerId.trim(),
        providerName = it.providerName.trim(),
        modelId = it.modelId.trim(),
        modelLabel = it.modelLabel.trim().ifBlank { it.modelId.trim() },
      )
    }
    .filter { it.providerId.isNotBlank() && it.modelId.isNotBlank() }
    .distinctBy { "${it.providerId}\u001f${it.modelId}" }
  val providerCount = uniqueModels.map { it.providerId }.distinct().size
  val allowStart = explicitUserTrigger && uniqueModels.size >= 2 && !toolSideEffectsAllowed
  return NbgExpertReviewPolicyReview(
    policyVersion = NBG_EXPERT_REVIEW_POLICY_VERSION,
    defaultEnabled = NBG_EXPERT_REVIEW_DEFAULT_ENABLED,
    allowStart = allowStart,
    explicitUserTrigger = explicitUserTrigger,
    modelCount = uniqueModels.size,
    providerCount = providerCount,
    requiresSeparateReferenceOutputs = true,
    consolidationRequired = true,
    toolSideEffectsAllowed = false,
    costLatencyWarning = "专家评审会调用 ${uniqueModels.size} 个模型、${providerCount} 个 provider；延迟和费用会高于普通单模型对话。",
    reason = when {
      !explicitUserTrigger -> "专家评审默认关闭，必须由用户显式触发。"
      toolSideEffectsAllowed -> "专家评审实验模式不允许工具、文件或命令副作用。"
      uniqueModels.size < 2 -> "专家评审至少需要两个已验证模型。"
      else -> "允许启动只读专家评审；参考输出分开展示，最终结果需要汇总。"
    },
  )
}

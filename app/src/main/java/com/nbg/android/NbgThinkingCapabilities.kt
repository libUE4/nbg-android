package com.nbg.android

private val NBG_ALL_THINKING_LEVELS = listOf("off", "auto", "low", "medium", "high", "xhigh")
private val NBG_STANDARD_THINKING_LEVELS = listOf("off", "auto", "low", "medium", "high")
private val NBG_BASIC_THINKING_LEVELS = listOf("off", "auto")

fun nbgNormalizeThinkingLevel(level: String): String? =
  when (level.trim().lowercase()) {
    "off", "none", "disabled", "disable", "false" -> "off"
    "auto", "default" -> "auto"
    "low", "minimal", "min" -> "low"
    "medium", "middle", "normal" -> "medium"
    "high" -> "high"
    "xhigh", "extra_high", "extra-high", "ultra", "max", "maximum" -> "xhigh"
    else -> null
  }

fun nbgNormalizeThinkingLevels(levels: List<String>): List<String> {
  val normalized = levels.mapNotNull(::nbgNormalizeThinkingLevel).toSet()
  if (normalized.isEmpty()) return emptyList()
  return NBG_ALL_THINKING_LEVELS.filter { it in normalized || it == "off" || it == "auto" }
}

fun nbgSupportedThinkingLevelsForModel(
  modelId: String,
  provider: String = "",
  baseUrl: String = "",
  api: String = "",
  upstreamLevels: List<String> = emptyList(),
): List<String> {
  val direct = nbgNormalizeThinkingLevels(upstreamLevels)
  if (direct.isNotEmpty()) return direct

  val model = modelId.lowercase()
  val providerText = provider.lowercase()
  val base = baseUrl.lowercase()
  val apiText = api.lowercase()

  val supportsThinking = nbgModelLooksReasoningCapable(model, providerText, base, apiText)
  if (!supportsThinking) return NBG_BASIC_THINKING_LEVELS
  return if (nbgModelSupportsUltraThinking(model, providerText, base)) {
    NBG_ALL_THINKING_LEVELS
  } else {
    NBG_STANDARD_THINKING_LEVELS
  }
}

fun nbgCoerceThinkingLevelForModel(level: String, model: HanakoModelSummary?): String {
  val normalized = nbgNormalizeThinkingLevel(level) ?: "auto"
  val supported = nbgNormalizeThinkingLevels(model?.thinkingLevels.orEmpty())
    .ifEmpty { nbgSupportedThinkingLevelsForModel(model?.id.orEmpty(), model?.provider.orEmpty()) }
  return if (normalized in supported) normalized else {
    when {
      normalized == "xhigh" && "high" in supported -> "high"
      normalized == "high" && "medium" in supported -> "medium"
      normalized == "medium" && "low" in supported -> "low"
      else -> "auto"
    }
  }
}

fun nbgThinkingSourceForModel(modelId: String, provider: String = "", baseUrl: String = "", api: String = ""): String {
  val model = modelId.lowercase()
  val providerText = provider.lowercase()
  val base = baseUrl.lowercase()
  val apiText = api.lowercase()
  return when {
    nbgIsAnthropicThinking(model, providerText, base, apiText) -> "local-anthropic"
    nbgIsDeepSeekThinking(model, providerText, base) -> "local-deepseek"
    nbgIsQwenThinking(model, providerText, base) -> "local-qwen"
    nbgIsKimiThinking(model, providerText, base, apiText) -> "local-kimi"
    nbgIsOpenAiReasoning(model, providerText, base) -> "local-openai"
    nbgModelLooksReasoningCapable(model, providerText, base, apiText) -> "local-reasoning"
    else -> "local-basic"
  }
}

private fun nbgModelLooksReasoningCapable(model: String, provider: String, baseUrl: String, api: String): Boolean =
  nbgIsOpenAiReasoning(model, provider, baseUrl) ||
    nbgIsAnthropicThinking(model, provider, baseUrl, api) ||
    nbgIsDeepSeekThinking(model, provider, baseUrl) ||
    nbgIsQwenThinking(model, provider, baseUrl) ||
    nbgIsKimiThinking(model, provider, baseUrl, api) ||
    model.contains("reasoner") ||
    model.contains("reasoning") ||
    model.contains("thinking")

private fun nbgModelSupportsUltraThinking(model: String, provider: String, baseUrl: String): Boolean =
  model.contains("gpt-5.2") ||
    model.contains("gpt-5.3") ||
    model.contains("gpt-5.4") ||
    model.contains("gpt-5.5") ||
    model.contains("deepseek-v4") ||
    model.contains("claude-opus-4-8") ||
    model.contains("opus-4-8") ||
    model.contains("opus-4.8") ||
    model.contains("opus-4-6") ||
    model.contains("opus-4.6") ||
    model.contains("ultra") ||
    model.contains("max") ||
    (provider.contains("deepseek") && model.contains("v4")) ||
    (baseUrl.contains("api.deepseek.com") && model.contains("v4"))

private fun nbgIsOpenAiReasoning(model: String, provider: String, baseUrl: String): Boolean =
  model.startsWith("o1") ||
    model.startsWith("o3") ||
    model.startsWith("o4") ||
    model.startsWith("gpt-5") ||
    model.contains("reasoning") ||
    model.contains("reasoner")

private fun nbgIsAnthropicThinking(model: String, provider: String, baseUrl: String, api: String): Boolean =
  provider.contains("anthropic") ||
    baseUrl.contains("anthropic.com") ||
    api == "anthropic-messages" ||
    model.contains("claude-3-7") ||
    model.contains("claude-4") ||
    model.contains("opus-4") ||
    model.contains("sonnet-4")

private fun nbgIsDeepSeekThinking(model: String, provider: String, baseUrl: String): Boolean =
  model.contains("deepseek-reasoner") ||
    model.contains("deepseek-r1") ||
    model.contains("deepseek-v4") ||
    model.contains("deepseek") && (model.contains("reasoning") || model.contains("thinking")) ||
    (provider.contains("deepseek") || baseUrl.contains("api.deepseek.com")) &&
    (model.contains("reasoner") || model.contains("r1") || model.contains("v4"))

private fun nbgIsQwenThinking(model: String, provider: String, baseUrl: String): Boolean =
  provider.contains("qwen") ||
    provider.contains("dashscope") ||
    baseUrl.contains("dashscope") ||
    baseUrl.contains("siliconflow") && model.contains("qwen") ||
    model.contains("qwen") && (model.contains("thinking") || model.contains("qvq") || model.contains("qwq"))

private fun nbgIsKimiThinking(model: String, provider: String, baseUrl: String, api: String): Boolean =
  provider.contains("kimi") ||
    provider.contains("moonshot") ||
    baseUrl.contains("moonshot") ||
    model.contains("kimi-k2-thinking") ||
    model.contains("kimi-thinking") ||
    (api == "anthropic-messages" && model.contains("kimi"))

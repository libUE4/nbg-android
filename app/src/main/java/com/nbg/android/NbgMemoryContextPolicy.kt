package com.nbg.android

internal const val NBG_MEMORY_CONTEXT_POLICY_VERSION = "nbg-memory-context-v1"
internal const val NBG_MEMORY_EXPORT_POLICY_VERSION = "nbg-memory-export-v1"
internal const val NBG_MEMORY_UPSTREAM_POLICY_ADOPTION_VERSION = "nbg-memory-upstream-policy-adoption-v1"
internal const val NBG_MEMORY_MAX_CONTENT_CHARS = 4_000

internal val NBG_MEMORY_EXPORT_REQUIRED_EVIDENCE: List<String> =
  listOf("explicit_user_trigger", "per_item_selection", "sensitive_scan_passed")

internal val NBG_MEMORY_UPSTREAM_POLICY_REQUIRED_EVIDENCE: List<String> =
  listOf("upstream_policy_version_match", "android_patch_marker_match", "parity_tests_passed")

internal enum class NbgMemoryContextRisk(val wireName: String) {
  Low("low"),
  BlockedSensitive("blocked_sensitive"),
  BlockedTooLarge("blocked_too_large"),
  BlockedEmpty("blocked_empty"),
}

internal data class NbgMemoryContextReview(
  val normalizedType: String,
  val risk: NbgMemoryContextRisk,
  val allowSave: Boolean,
  val allowContextInjection: Boolean,
  val requiresExplicitConfirmation: Boolean,
  val findingCount: Int = 0,
  val userMessage: String = "",
)

internal data class NbgMemoryExportPolicyReview(
  val policyVersion: String,
  val allowExport: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val selectedItemCount: Int,
  val reason: String,
)

internal data class NbgMemoryUpstreamPolicyAdoptionReview(
  val policyVersion: String,
  val mayRemoveAndroidRuntimePatch: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val reason: String,
)

internal fun nbgReviewMemoryExportRequest(
  selectedItemCount: Int,
  explicitUserTrigger: Boolean,
  perItemSelection: Boolean,
  sensitiveScanPassed: Boolean,
): NbgMemoryExportPolicyReview {
  val presentEvidence = buildList {
    if (explicitUserTrigger) add("explicit_user_trigger")
    if (perItemSelection) add("per_item_selection")
    if (sensitiveScanPassed) add("sensitive_scan_passed")
  }
  return NbgMemoryExportPolicyReview(
    policyVersion = NBG_MEMORY_EXPORT_POLICY_VERSION,
    allowExport = false,
    requiredEvidence = NBG_MEMORY_EXPORT_REQUIRED_EVIDENCE,
    presentEvidence = presentEvidence,
    selectedItemCount = selectedItemCount.coerceAtLeast(0),
    reason = "Public beta v1 does not expose Memory export; future export requires explicit user trigger, per-item selection, and a passing sensitive-content scan.",
  )
}

internal fun nbgReviewMemoryUpstreamPolicyAdoption(
  upstreamPolicyVersionMatches: Boolean,
  androidPatchMarkerMatches: Boolean,
  parityTestsPassed: Boolean,
): NbgMemoryUpstreamPolicyAdoptionReview {
  val presentEvidence = buildList {
    if (upstreamPolicyVersionMatches) add("upstream_policy_version_match")
    if (androidPatchMarkerMatches) add("android_patch_marker_match")
    if (parityTestsPassed) add("parity_tests_passed")
  }
  return NbgMemoryUpstreamPolicyAdoptionReview(
    policyVersion = NBG_MEMORY_UPSTREAM_POLICY_ADOPTION_VERSION,
    mayRemoveAndroidRuntimePatch = false,
    requiredEvidence = NBG_MEMORY_UPSTREAM_POLICY_REQUIRED_EVIDENCE,
    presentEvidence = presentEvidence,
    reason = "Public beta v1 keeps the Android Memory runtime patch gate; future upstream adoption must prove policy version parity, marker compatibility, and passing parity tests before Android patch removal is considered.",
  )
}

internal fun nbgReviewMemoryInput(input: HanakoMemoryInput): NbgMemoryContextReview {
  val normalizedType = nbgNormalizeMemoryType(input.type)
  val content = input.content.trim()
  if (content.isBlank()) {
    return NbgMemoryContextReview(
      normalizedType = normalizedType,
      risk = NbgMemoryContextRisk.BlockedEmpty,
      allowSave = false,
      allowContextInjection = false,
      requiresExplicitConfirmation = true,
      userMessage = "记忆内容不能为空",
    )
  }
  if (content.length > NBG_MEMORY_MAX_CONTENT_CHARS) {
    return NbgMemoryContextReview(
      normalizedType = normalizedType,
      risk = NbgMemoryContextRisk.BlockedTooLarge,
      allowSave = false,
      allowContextInjection = false,
      requiresExplicitConfirmation = true,
      userMessage = "Memory 内容过长，最大 $NBG_MEMORY_MAX_CONTENT_CHARS 字符。",
    )
  }
  val scanText = buildString {
    append(input.title)
    append('\n')
    append(content)
    input.tags.forEach {
      append('\n')
      append(it)
    }
  }
  val findings = nbgMemorySensitiveFindings(scanText)
  if (findings.isNotEmpty()) {
    return NbgMemoryContextReview(
      normalizedType = normalizedType,
      risk = NbgMemoryContextRisk.BlockedSensitive,
      allowSave = false,
      allowContextInjection = false,
      requiresExplicitConfirmation = true,
      findingCount = findings.size,
      userMessage = "Memory 内容疑似包含密钥、token、密码或私钥；请先删除敏感值再保存。",
    )
  }
  return NbgMemoryContextReview(
    normalizedType = normalizedType,
    risk = NbgMemoryContextRisk.Low,
    allowSave = true,
    allowContextInjection = true,
    requiresExplicitConfirmation = true,
  )
}

internal fun nbgReviewMemoryItem(item: HanakoMemoryItem): NbgMemoryContextReview =
  nbgReviewMemoryInput(
    HanakoMemoryInput(
      id = item.id,
      type = item.type,
      title = item.title,
      content = item.content,
      tags = item.tags,
      enabled = item.enabled,
    ),
  ).copy(requiresExplicitConfirmation = false)

internal fun nbgMemorySensitiveFindings(text: String): List<String> {
  if (text.isBlank()) return emptyList()
  return NBG_MEMORY_SENSITIVE_PATTERNS.mapNotNull { pattern ->
    if (pattern.regex.containsMatchIn(text)) pattern.label else null
  }
}

private data class NbgMemorySensitivePattern(
  val label: String,
  val regex: Regex,
)

private val NBG_MEMORY_SENSITIVE_PATTERNS = listOf(
  NbgMemorySensitivePattern(
    label = "private_key",
    regex = Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----"""),
  ),
  NbgMemorySensitivePattern(
    label = "bearer_token",
    regex = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]{16,}"""),
  ),
  NbgMemorySensitivePattern(
    label = "openai_style_key",
    regex = Regex("""\bsk-[A-Za-z0-9_-]{12,}\b"""),
  ),
  NbgMemorySensitivePattern(
    label = "secret_assignment",
    regex = Regex("""(?i)\b(api[_-]?key|access[_-]?key|token|refresh[_-]?token|password|secret)\b\s*[:=]\s*["']?[^"'\s,}]{8,}"""),
  ),
  NbgMemorySensitivePattern(
    label = "github_token",
    regex = Regex("""\bgh[pousr]_[A-Za-z0-9_]{20,}\b"""),
  ),
  NbgMemorySensitivePattern(
    label = "aws_access_key",
    regex = Regex("""\bAKIA[0-9A-Z]{16}\b"""),
  ),
)

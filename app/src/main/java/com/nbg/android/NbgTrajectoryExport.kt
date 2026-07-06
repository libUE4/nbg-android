package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_TRAJECTORY_EXPORT_POLICY_VERSION = "nbg-trajectory-export-v1"
internal const val NBG_TRAJECTORY_EXPORT_BUNDLE_VERSION = "nbg-trajectory-export-bundle-v1"

internal val NBG_TRAJECTORY_EXPORT_REQUIRED_EVIDENCE: List<String> =
  listOf("explicit_user_trigger", "local_only_destination", "sensitive_scan_passed", "conversation_selection")

data class NbgTrajectoryExportPolicyReview(
  val policyVersion: String,
  val allowExport: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val conversationCount: Int,
  val toolEventCount: Int,
  val learningEventCount: Int,
  val reason: String,
)

data class NbgTrajectoryExportMessage(
  val role: String,
  val text: String,
  val timestampMs: Long = 0L,
)

data class NbgTrajectoryExportBundle(
  val policyVersion: String,
  val bundleVersion: String,
  val review: NbgTrajectoryExportPolicyReview,
  val messages: List<NbgTrajectoryExportMessage> = emptyList(),
  val toolEvents: List<HanakoToolStatus> = emptyList(),
  val evidenceBundles: List<NbgTaskCompletionEvidenceBundle> = emptyList(),
  val learningEvents: List<NbgLearningEvent> = emptyList(),
)

internal fun nbgReviewTrajectoryExportRequest(
  explicitUserTrigger: Boolean,
  localOnlyDestination: Boolean,
  sensitiveScanPassed: Boolean,
  conversationSelection: Boolean,
  conversationCount: Int,
  toolEventCount: Int,
  learningEventCount: Int,
): NbgTrajectoryExportPolicyReview {
  val present = buildList {
    if (explicitUserTrigger) add("explicit_user_trigger")
    if (localOnlyDestination) add("local_only_destination")
    if (sensitiveScanPassed) add("sensitive_scan_passed")
    if (conversationSelection) add("conversation_selection")
  }
  val allow = conversationCount > 0 && NBG_TRAJECTORY_EXPORT_REQUIRED_EVIDENCE.all { it in present }
  return NbgTrajectoryExportPolicyReview(
    policyVersion = NBG_TRAJECTORY_EXPORT_POLICY_VERSION,
    allowExport = allow,
    requiredEvidence = NBG_TRAJECTORY_EXPORT_REQUIRED_EVIDENCE,
    presentEvidence = present,
    conversationCount = conversationCount.coerceAtLeast(0),
    toolEventCount = toolEventCount.coerceAtLeast(0),
    learningEventCount = learningEventCount.coerceAtLeast(0),
    reason = if (allow) {
      "Trajectory export allowed for local-only redacted bundle."
    } else {
      "Trajectory export requires explicit trigger, local-only destination, selected conversation, and sensitive scan evidence."
    },
  )
}

internal fun nbgBuildTrajectoryExportBundle(
  history: HanakoHistorySnapshot,
  learningLog: NbgLearningAuditLog,
  explicitUserTrigger: Boolean,
  localOnlyDestination: Boolean = true,
): NbgTrajectoryExportBundle {
  val messages = history.messages
    .filter { it.role in setOf("user", "assistant", "system") }
    .map {
      NbgTrajectoryExportMessage(
        role = it.role,
        text = nbgRedactDiagnosticText(it.text).take(4_000),
        timestampMs = it.timestampMs.coerceAtLeast(0L),
      )
    }
  val toolEvents = history.messages.mapNotNull { it.toolStatus }
  val evidence = toolEvents.mapNotNull { it.visibleTaskCompletionEvidence() }
  val learningEvents = learningLog.events.take(200)
  val scanText = buildString {
    messages.forEach { appendLine(it.text) }
    learningEvents.forEach { appendLine(it.candidate.content) }
  }
  val sensitiveScanPassed = nbgMemorySensitiveFindings(scanText).isEmpty()
  val review = nbgReviewTrajectoryExportRequest(
    explicitUserTrigger = explicitUserTrigger,
    localOnlyDestination = localOnlyDestination,
    sensitiveScanPassed = sensitiveScanPassed,
    conversationSelection = history.messages.isNotEmpty(),
    conversationCount = messages.size,
    toolEventCount = toolEvents.size,
    learningEventCount = learningEvents.size,
  )
  return NbgTrajectoryExportBundle(
    policyVersion = NBG_TRAJECTORY_EXPORT_POLICY_VERSION,
    bundleVersion = NBG_TRAJECTORY_EXPORT_BUNDLE_VERSION,
    review = review,
    messages = if (review.allowExport) messages else emptyList(),
    toolEvents = if (review.allowExport) toolEvents.map { it.redactedTrajectoryToolStatus() } else emptyList(),
    evidenceBundles = if (review.allowExport) evidence else emptyList(),
    learningEvents = if (review.allowExport) learningEvents.map { it.redactedTrajectoryLearningEvent() } else emptyList(),
  )
}

internal fun NbgTrajectoryExportBundle.toJsonString(indentSpaces: Int = 2): String =
  JSONObject()
    .put("policyVersion", policyVersion)
    .put("bundleVersion", bundleVersion)
    .put("review", review.toJson())
    .put("messages", JSONArray(messages.map { it.toJson() }))
    .put("toolEvents", JSONArray(toolEvents.map { it.toTrajectoryJson() }))
    .put("evidenceBundles", JSONArray(evidenceBundles.map { it.toJson() }))
    .put("learningEvents", JSONArray(learningEvents.map { it.toTrajectoryJson() }))
    .toString(indentSpaces.coerceIn(0, 4))

private fun NbgTrajectoryExportPolicyReview.toJson(): JSONObject =
  JSONObject()
    .put("policyVersion", policyVersion)
    .put("allowExport", allowExport)
    .put("requiredEvidence", JSONArray(requiredEvidence))
    .put("presentEvidence", JSONArray(presentEvidence))
    .put("conversationCount", conversationCount)
    .put("toolEventCount", toolEventCount)
    .put("learningEventCount", learningEventCount)
    .put("reason", reason)

private fun NbgTrajectoryExportMessage.toJson(): JSONObject =
  JSONObject()
    .put("role", role)
    .put("text", nbgRedactDiagnosticText(text))
    .put("timestampMs", timestampMs.coerceAtLeast(0L))

private fun HanakoToolStatus.redactedTrajectoryToolStatus(): HanakoToolStatus =
  copy(
    key = key.sha256Hex().take(16),
    filePath = if (filePath.isBlank()) "" else "[path]",
    title = nbgRedactDiagnosticText(title).take(160),
    subtitle = nbgRedactDiagnosticText(subtitle).take(240),
    detail = nbgRedactDiagnosticText(detail).take(1_000),
    filePreview = null,
    fileDiff = null,
    terminalOutput = null,
  )

private fun HanakoToolStatus.toTrajectoryJson(): JSONObject =
  JSONObject()
    .put("keyHash", key)
    .put("kind", nbgToolVisualizationKind().wireName)
    .put("toolName", nbgRedactDiagnosticText(toolName).take(120))
    .put("title", nbgRedactDiagnosticText(title).take(160))
    .put("subtitle", nbgRedactDiagnosticText(subtitle).take(240))
    .put("detail", nbgRedactDiagnosticText(detail).take(1_000))
    .put("state", nbgToolVisualizationState().wireName)
    .put("hasEvidence", visibleTaskCompletionEvidence() != null)

private fun NbgLearningEvent.redactedTrajectoryLearningEvent(): NbgLearningEvent =
  copy(
    candidate = candidate.copy(
      sourceSessionPath = if (candidate.sourceSessionPath.isBlank()) "" else "[session-path]",
      targetPath = if (candidate.targetPath.isBlank()) "" else "[target-path]",
      content = nbgRedactDiagnosticText(candidate.content).take(NBG_LEARNING_MAX_CONTENT_CHARS),
    ),
    appliedRefs = appliedRefs.map { nbgRedactDiagnosticText(it).take(160) },
    error = nbgRedactDiagnosticText(error).take(240),
  )

private fun NbgLearningEvent.toTrajectoryJson(): JSONObject =
  JSONObject()
    .put("idHash", id.sha256Hex().take(16))
    .put("kind", candidate.kind.wireName)
    .put("title", nbgRedactDiagnosticText(candidate.title).take(120))
    .put("status", status.wireName)
    .put("riskTier", review.riskTier.wireName)
    .put("reason", nbgRedactDiagnosticText(review.reason).take(240))
    .put("createdAtMs", createdAtMs.coerceAtLeast(0L))
    .put("updatedAtMs", updatedAtMs.coerceAtLeast(0L))

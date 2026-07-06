package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_TASK_COMPLETION_EVIDENCE_VERSION = "nbg-task-completion-evidence-v1"

enum class NbgTaskCompletionCriterionKind(val wireName: String, val label: String) {
  Diff("diff", "Diff"),
  FileWrite("file_write", "文件"),
  CommandExit("command_exit", "命令"),
  TestResult("test_result", "测试"),
  BuildResult("build_result", "构建"),
  ReviewResult("review_result", "审查"),
  ConsolidatedResult("consolidated_result", "汇总"),
  UserOverride("user_override", "用户覆盖"),
}

enum class NbgTaskCompletionEvidenceState(val wireName: String, val label: String) {
  Missing("missing", "缺证据"),
  Present("present", "有证据"),
  Passed("passed", "通过"),
  Failed("failed", "失败"),
  Overridden("overridden", "已覆盖"),
}

data class NbgTaskCompletionCriterion(
  val kind: NbgTaskCompletionCriterionKind,
  val label: String,
  val required: Boolean = true,
)

data class NbgTaskCompletionEvidenceItem(
  val kind: NbgTaskCompletionCriterionKind,
  val label: String,
  val state: NbgTaskCompletionEvidenceState,
  val summary: String = "",
  val artifactRef: String = "",
  val timestampMillis: Long = 0L,
)

data class NbgTaskCompletionEvidenceBundle(
  val contractId: String,
  val title: String,
  val criteria: List<NbgTaskCompletionCriterion>,
  val evidence: List<NbgTaskCompletionEvidenceItem>,
  val overrideReason: String = "",
  val policyVersion: String = NBG_TASK_COMPLETION_EVIDENCE_VERSION,
) {
  val review: NbgTaskCompletionEvidenceReview
    get() = nbgReviewTaskCompletionEvidence(this)
}

data class NbgTaskCompletionEvidenceReview(
  val policyVersion: String,
  val state: NbgTaskCompletionEvidenceState,
  val requiredCount: Int,
  val satisfiedCount: Int,
  val missingLabels: List<String>,
  val failedLabels: List<String>,
  val userMessage: String,
) {
  val complete: Boolean
    get() = state == NbgTaskCompletionEvidenceState.Passed ||
      state == NbgTaskCompletionEvidenceState.Overridden
}

internal fun nbgTaskCriterion(
  kind: NbgTaskCompletionCriterionKind,
  label: String = kind.label,
  required: Boolean = true,
): NbgTaskCompletionCriterion =
  NbgTaskCompletionCriterion(kind = kind, label = label, required = required)

internal fun nbgTaskEvidence(
  kind: NbgTaskCompletionCriterionKind,
  state: NbgTaskCompletionEvidenceState,
  label: String = kind.label,
  summary: String = "",
  artifactRef: String = "",
  timestampMillis: Long = 0L,
): NbgTaskCompletionEvidenceItem =
  NbgTaskCompletionEvidenceItem(
    kind = kind,
    label = label.ifBlank { kind.label },
    state = state,
    summary = summary.nbgTaskEvidenceCompact(limit = 220),
    artifactRef = artifactRef.nbgTaskEvidenceArtifactRef(),
    timestampMillis = timestampMillis.coerceAtLeast(0L),
  )

internal fun nbgReviewTaskCompletionEvidence(
  bundle: NbgTaskCompletionEvidenceBundle,
): NbgTaskCompletionEvidenceReview {
  val required = bundle.criteria.filter { it.required }
  if (bundle.overrideReason.isNotBlank()) {
    return NbgTaskCompletionEvidenceReview(
      policyVersion = NBG_TASK_COMPLETION_EVIDENCE_VERSION,
      state = NbgTaskCompletionEvidenceState.Overridden,
      requiredCount = required.size,
      satisfiedCount = required.size,
      missingLabels = emptyList(),
      failedLabels = emptyList(),
      userMessage = "用户覆盖完成状态：${bundle.overrideReason.nbgTaskEvidenceCompact(limit = 160)}",
    )
  }
  val evidenceByKind = bundle.evidence.groupBy { it.kind }
  val missing = required.filter { criterion ->
    evidenceByKind[criterion.kind].orEmpty().none { it.state.satisfiesTaskCompletionCriterion() }
  }
  val failed = required.filter { criterion ->
    evidenceByKind[criterion.kind].orEmpty().any { it.state == NbgTaskCompletionEvidenceState.Failed }
  }
  val satisfiedCount = required.size - missing.size
  val state = when {
    failed.isNotEmpty() -> NbgTaskCompletionEvidenceState.Failed
    missing.isNotEmpty() -> NbgTaskCompletionEvidenceState.Missing
    required.isEmpty() && bundle.evidence.isNotEmpty() -> NbgTaskCompletionEvidenceState.Present
    required.isEmpty() -> NbgTaskCompletionEvidenceState.Missing
    else -> NbgTaskCompletionEvidenceState.Passed
  }
  return NbgTaskCompletionEvidenceReview(
    policyVersion = NBG_TASK_COMPLETION_EVIDENCE_VERSION,
    state = state,
    requiredCount = required.size,
    satisfiedCount = satisfiedCount.coerceAtLeast(0),
    missingLabels = missing.map { it.label.ifBlank { criterionLabel(it.kind) } },
    failedLabels = failed.map { it.label.ifBlank { criterionLabel(it.kind) } },
    userMessage = when (state) {
      NbgTaskCompletionEvidenceState.Passed -> "完成证据已满足"
      NbgTaskCompletionEvidenceState.Present -> "已记录完成证据"
      NbgTaskCompletionEvidenceState.Failed -> "完成证据失败：${failed.joinToString("、") { it.label.ifBlank { criterionLabel(it.kind) } }}"
      NbgTaskCompletionEvidenceState.Overridden -> "用户已覆盖完成状态"
      NbgTaskCompletionEvidenceState.Missing -> "缺少完成证据：${missing.joinToString("、") { it.label.ifBlank { criterionLabel(it.kind) } }}"
    },
  )
}

internal fun HanakoToolStatus.withInferredTaskCompletionEvidence(): HanakoToolStatus =
  if (taskCompletionEvidence != null) {
    this
  } else {
    copy(taskCompletionEvidence = nbgInferTaskCompletionEvidence(this))
  }

internal fun nbgInferTaskCompletionEvidence(tool: HanakoToolStatus): NbgTaskCompletionEvidenceBundle? {
  tool.fileDiff?.let { diff ->
    return NbgTaskCompletionEvidenceBundle(
      contractId = "tool:${tool.key.ifBlank { "diff" }}",
      title = "代码变更完成证据",
      criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.Diff, "Diff")),
      evidence = listOf(
        nbgTaskEvidence(
          kind = NbgTaskCompletionCriterionKind.Diff,
          state = if (diff.unifiedDiff.isNotBlank() || diff.oldContent != diff.newContent) {
            NbgTaskCompletionEvidenceState.Present
          } else {
            NbgTaskCompletionEvidenceState.Missing
          },
          label = "Diff",
          summary = listOf(diff.fileName, diff.filePath).filter { it.isNotBlank() }.joinToString(" / "),
          artifactRef = diff.filePath,
        ),
      ),
    )
  }
  tool.filePreview?.let { preview ->
    return NbgTaskCompletionEvidenceBundle(
      contractId = "tool:${tool.key.ifBlank { "file-write" }}",
      title = "文件写入完成证据",
      criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.FileWrite, "文件写入")),
      evidence = listOf(
        nbgTaskEvidence(
          kind = NbgTaskCompletionCriterionKind.FileWrite,
          state = if (tool.success == false) {
            NbgTaskCompletionEvidenceState.Failed
          } else if (tool.running) {
            NbgTaskCompletionEvidenceState.Present
          } else {
            NbgTaskCompletionEvidenceState.Passed
          },
          label = "文件写入",
          summary = listOf(preview.fileName, preview.filePath).filter { it.isNotBlank() }.joinToString(" / "),
          artifactRef = preview.filePath,
        ),
      ),
    )
  }
  tool.terminalOutput?.let { terminal ->
    val isTest = terminal.title.contains("test", ignoreCase = true) ||
      tool.title.contains("test", ignoreCase = true) ||
      tool.detail.contains("test", ignoreCase = true)
    val isBuild = terminal.title.contains("build", ignoreCase = true) ||
      tool.title.contains("build", ignoreCase = true) ||
      tool.detail.contains("gradle", ignoreCase = true) ||
      tool.detail.contains("assemble", ignoreCase = true)
    val kind = when {
      isTest -> NbgTaskCompletionCriterionKind.TestResult
      isBuild -> NbgTaskCompletionCriterionKind.BuildResult
      else -> NbgTaskCompletionCriterionKind.CommandExit
    }
    val state = when (terminal.exitCode) {
      null -> if (terminal.alive == true || tool.running) {
        NbgTaskCompletionEvidenceState.Present
      } else {
        NbgTaskCompletionEvidenceState.Missing
      }
      0 -> NbgTaskCompletionEvidenceState.Passed
      else -> NbgTaskCompletionEvidenceState.Failed
    }
    return NbgTaskCompletionEvidenceBundle(
      contractId = "tool:${tool.key.ifBlank { "terminal" }}",
      title = "命令完成证据",
      criteria = listOf(nbgTaskCriterion(kind, kind.label)),
      evidence = listOf(
        nbgTaskEvidence(
          kind = kind,
          state = state,
          label = kind.label,
          summary = nbgTerminalOutputSummary(terminal),
          artifactRef = terminal.sessionId,
        ),
      ),
    )
  }
  if (tool.kind == NbgToolVisualizationKind.TeamTask.wireName || tool.kind == NbgToolVisualizationKind.TeamAgent.wireName) {
    val state = when (tool.nbgToolVisualizationState()) {
      NbgToolVisualizationState.Succeeded -> NbgTaskCompletionEvidenceState.Passed
      NbgToolVisualizationState.Failed,
      NbgToolVisualizationState.Blocked,
      NbgToolVisualizationState.Cancelled -> NbgTaskCompletionEvidenceState.Failed
      NbgToolVisualizationState.Running,
      NbgToolVisualizationState.Waiting,
      NbgToolVisualizationState.RestoredIncomplete -> NbgTaskCompletionEvidenceState.Present
    }
    return NbgTaskCompletionEvidenceBundle(
      contractId = "tool:${tool.key.ifBlank { "team" }}",
      title = "团队任务完成证据",
      criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.ConsolidatedResult, "汇总结果")),
      evidence = listOf(
        nbgTaskEvidence(
          kind = NbgTaskCompletionCriterionKind.ConsolidatedResult,
          state = state,
          label = "汇总结果",
          summary = listOf(tool.title, tool.subtitle, tool.detail).filter { it.isNotBlank() }.joinToString(" / "),
          artifactRef = tool.key,
        ),
      ),
    )
  }
  return null
}

internal fun NbgTaskCompletionEvidenceBundle.toJson(): JSONObject =
  JSONObject()
    .put("policyVersion", policyVersion)
    .put("contractId", contractId)
    .put("title", title)
    .put("overrideReason", overrideReason)
    .put("criteria", JSONArray().also { array ->
      criteria.forEach { array.put(it.toJson()) }
    })
    .put("evidence", JSONArray().also { array ->
      evidence.forEach { array.put(it.toJson()) }
    })

internal fun parseNbgTaskCompletionEvidenceBundle(root: JSONObject?): NbgTaskCompletionEvidenceBundle? {
  if (root == null) return null
  val criteria = root.optJSONArray("criteria").toTaskCompletionCriteria()
  val evidence = root.optJSONArray("evidence").toTaskCompletionEvidenceItems()
  if (criteria.isEmpty() && evidence.isEmpty() && root.optString("overrideReason").isBlank()) return null
  return NbgTaskCompletionEvidenceBundle(
    contractId = root.optString("contractId").trim().take(120),
    title = root.optString("title").trim().take(140).ifBlank { "完成证据" },
    criteria = criteria,
    evidence = evidence,
    overrideReason = root.optString("overrideReason").trim().take(220),
    policyVersion = root.optString("policyVersion").trim().ifBlank { NBG_TASK_COMPLETION_EVIDENCE_VERSION },
  )
}

private fun NbgTaskCompletionCriterion.toJson(): JSONObject =
  JSONObject()
    .put("kind", kind.wireName)
    .put("label", label)
    .put("required", required)

private fun NbgTaskCompletionEvidenceItem.toJson(): JSONObject =
  JSONObject()
    .put("kind", kind.wireName)
    .put("label", label)
    .put("state", state.wireName)
    .put("summary", summary)
    .put("artifactRef", artifactRef)
    .put("timestampMillis", timestampMillis)

private fun JSONArray?.toTaskCompletionCriteria(): List<NbgTaskCompletionCriterion> {
  if (this == null) return emptyList()
  return buildList {
    for (index in 0 until length()) {
      val item = optJSONObject(index) ?: continue
      val kind = parseTaskCompletionCriterionKind(item.optString("kind"))
      add(
        NbgTaskCompletionCriterion(
          kind = kind,
          label = item.optString("label").trim().take(120).ifBlank { kind.label },
          required = item.optBoolean("required", true),
        ),
      )
    }
  }
}

private fun JSONArray?.toTaskCompletionEvidenceItems(): List<NbgTaskCompletionEvidenceItem> {
  if (this == null) return emptyList()
  return buildList {
    for (index in 0 until length()) {
      val item = optJSONObject(index) ?: continue
      val kind = parseTaskCompletionCriterionKind(item.optString("kind"))
      add(
        NbgTaskCompletionEvidenceItem(
          kind = kind,
          label = item.optString("label").trim().take(120).ifBlank { kind.label },
          state = parseTaskCompletionEvidenceState(item.optString("state")),
          summary = item.optString("summary").nbgTaskEvidenceCompact(limit = 220),
          artifactRef = item.optString("artifactRef").nbgTaskEvidenceArtifactRef(),
          timestampMillis = item.optLong("timestampMillis", 0L).coerceAtLeast(0L),
        ),
      )
    }
  }
}

private fun parseTaskCompletionCriterionKind(raw: String): NbgTaskCompletionCriterionKind =
  NbgTaskCompletionCriterionKind.entries.firstOrNull { it.wireName == raw.trim().lowercase() }
    ?: NbgTaskCompletionCriterionKind.ReviewResult

private fun parseTaskCompletionEvidenceState(raw: String): NbgTaskCompletionEvidenceState =
  NbgTaskCompletionEvidenceState.entries.firstOrNull { it.wireName == raw.trim().lowercase() }
    ?: NbgTaskCompletionEvidenceState.Missing

private fun NbgTaskCompletionEvidenceState.satisfiesTaskCompletionCriterion(): Boolean =
  this == NbgTaskCompletionEvidenceState.Present ||
    this == NbgTaskCompletionEvidenceState.Passed ||
    this == NbgTaskCompletionEvidenceState.Overridden

private fun criterionLabel(kind: NbgTaskCompletionCriterionKind): String = kind.label

private fun String.nbgTaskEvidenceCompact(limit: Int): String =
  trim()
    .replace(Regex("\\s+"), " ")
    .take(limit)

private fun String.nbgTaskEvidenceArtifactRef(): String =
  trim()
    .replace(Regex("\\s+"), " ")
    .replace(Regex("(?i)(token|api[_-]?key|password|secret)=([^\\s&]+)"), "$1=[redacted]")
    .take(180)

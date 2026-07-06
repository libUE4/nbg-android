package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_LEARNED_SKILL_DRAFT_POLICY_VERSION = "nbg-learned-skill-draft-v1"
internal const val NBG_LEARNED_SKILL_DRAFT_QUEUE_VERSION = "nbg-learned-skill-draft-queue-v1"

internal val NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE: List<String> =
  listOf(
    "completion_evidence_complete",
    "source_task",
    "target_path_reviewed",
    "draft_sha256",
    "permission_tier_recorded",
  )

enum class NbgLearnedSkillDraftStatus(val wireName: String, val label: String) {
  PendingReview("pending_review", "待复核"),
  BlockedMissingEvidence("blocked_missing_evidence", "证据不足"),
  Rejected("rejected", "已拒绝"),
}

data class NbgLearnedSkillDraftReview(
  val policyVersion: String,
  val allowDraft: Boolean,
  val allowInstall: Boolean,
  val allowEnable: Boolean,
  val requiresReview: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val permissionTier: NbgPermissionRiskTier,
  val sourceTaskId: String,
  val targetPathLabel: String,
  val reason: String,
)

data class NbgLearnedSkillDraftQueueEntry(
  val id: String,
  val skillName: String,
  val description: String = "",
  val sourceTaskTitle: String = "",
  val review: NbgLearnedSkillDraftReview,
  val status: NbgLearnedSkillDraftStatus,
  val createdAtMs: Long = 0L,
  val updatedAtMs: Long = 0L,
) {
  val missingEvidence: List<String>
    get() = review.missingEvidence
}

data class NbgLearnedSkillDraftQueue(
  val entries: List<NbgLearnedSkillDraftQueueEntry> = emptyList(),
  val modelVersion: String = NBG_LEARNED_SKILL_DRAFT_QUEUE_VERSION,
) {
  val visibleEntries: List<NbgLearnedSkillDraftQueueEntry>
    get() = entries.filterNot { it.status == NbgLearnedSkillDraftStatus.Rejected }

  val pendingReviewCount: Int
    get() = visibleEntries.count { it.status == NbgLearnedSkillDraftStatus.PendingReview && it.review.allowDraft }

  val blockedCount: Int
    get() = visibleEntries.count { it.status == NbgLearnedSkillDraftStatus.BlockedMissingEvidence || !it.review.allowDraft }

  val dangerousCount: Int
    get() = visibleEntries.count { it.review.permissionTier == NbgPermissionRiskTier.Dangerous }

  val installableCount: Int
    get() = visibleEntries.count { it.review.allowInstall }

  val enableableCount: Int
    get() = visibleEntries.count { it.review.allowEnable }
}

internal val NbgLearnedSkillDraftReview.missingEvidence: List<String>
  get() = requiredEvidence - presentEvidence.toSet()

internal fun nbgReviewLearnedSkillDraft(
  skillName: String,
  targetPath: String,
  sourceTaskId: String,
  completionEvidence: NbgTaskCompletionEvidenceBundle?,
  draftSha256: String,
  permissionTier: NbgPermissionRiskTier,
): NbgLearnedSkillDraftReview {
  val cleanSkillName = skillName.trim()
  val cleanTaskId = sourceTaskId.trim().take(120)
  val sourceReview = nbgReviewSkillInstallSource(targetPath)
  val cleanSha = draftSha256.trim().lowercase()
  val completionReview = completionEvidence?.review
  val completionOk = completionReview?.complete == true
  val targetOk = sourceReview.allowInstall &&
    sourceReview.sourceKind in setOf(NbgSkillSourceKind.LocalPath, NbgSkillSourceKind.UserInstalled)
  val shaOk = cleanSha.matches(Regex("[a-f0-9]{64}"))
  val presentEvidence = buildList {
    if (completionOk) add("completion_evidence_complete")
    if (cleanTaskId.isNotBlank()) add("source_task")
    if (targetOk) add("target_path_reviewed")
    if (shaOk) add("draft_sha256")
    add("permission_tier_recorded")
  }
  val missing = NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE - presentEvidence.toSet()
  val allowDraft = cleanSkillName.isNotBlank() && missing.isEmpty()
  return NbgLearnedSkillDraftReview(
    policyVersion = NBG_LEARNED_SKILL_DRAFT_POLICY_VERSION,
    allowDraft = allowDraft,
    allowInstall = false,
    allowEnable = false,
    requiresReview = true,
    requiredEvidence = NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE,
    presentEvidence = presentEvidence,
    permissionTier = permissionTier,
    sourceTaskId = cleanTaskId,
    targetPathLabel = sourceReview.redactedLocation.ifBlank { "[skill-draft-path]" },
    reason = when {
      cleanSkillName.isBlank() -> "Skill 名称不能为空；学习结果只能进入草稿，不能直接安装或启用。"
      missing.isNotEmpty() -> "Skill 草稿缺少证据：${missing.joinToString(", ")}；学习结果保持未安装状态。"
      permissionTier == NbgPermissionRiskTier.Dangerous -> "Skill 草稿可保存但包含危险权限；安装和启用必须经过强确认。"
      else -> "Skill 草稿证据完整；安装和启用仍需用户审核确认。"
    },
  )
}

internal fun nbgLearnedSkillDraftQueueEntry(
  skillName: String,
  targetPath: String,
  sourceTaskId: String,
  completionEvidence: NbgTaskCompletionEvidenceBundle?,
  draftSha256: String,
  permissionTier: NbgPermissionRiskTier,
  id: String = "",
  description: String = "",
  sourceTaskTitle: String = "",
  createdAtMs: Long = 0L,
  updatedAtMs: Long = 0L,
  status: NbgLearnedSkillDraftStatus? = null,
): NbgLearnedSkillDraftQueueEntry {
  val review = nbgReviewLearnedSkillDraft(
    skillName = skillName,
    targetPath = targetPath,
    sourceTaskId = sourceTaskId,
    completionEvidence = completionEvidence,
    draftSha256 = draftSha256,
    permissionTier = permissionTier,
  )
  return NbgLearnedSkillDraftQueueEntry(
    id = id.trim().ifBlank { nbgLearnedSkillDraftFallbackId(skillName, sourceTaskId) },
    skillName = skillName.trim(),
    description = description.trim().nbgLearnedSkillDraftCompact(),
    sourceTaskTitle = sourceTaskTitle.trim().nbgLearnedSkillDraftCompact(limit = 120),
    review = review,
    status = status ?: if (review.allowDraft) {
      NbgLearnedSkillDraftStatus.PendingReview
    } else {
      NbgLearnedSkillDraftStatus.BlockedMissingEvidence
    },
    createdAtMs = createdAtMs.coerceAtLeast(0L),
    updatedAtMs = updatedAtMs.coerceAtLeast(0L),
  )
}

internal fun nbgBuildLearnedSkillDraftQueue(
  entries: List<NbgLearnedSkillDraftQueueEntry>,
): NbgLearnedSkillDraftQueue {
  val deduped = entries
    .filter { it.skillName.isNotBlank() }
    .groupBy { it.id.ifBlank { nbgLearnedSkillDraftFallbackId(it.skillName, it.review.sourceTaskId) } }
    .mapNotNull { (_, grouped) -> grouped.maxWithOrNull(compareBy<NbgLearnedSkillDraftQueueEntry> { it.updatedAtMs }.thenBy { it.createdAtMs }) }
    .sortedWith(
      compareBy<NbgLearnedSkillDraftQueueEntry> { it.status.sortOrder }
        .thenByDescending { it.updatedAtMs }
        .thenByDescending { it.createdAtMs }
        .thenBy { it.skillName.lowercase() },
    )
  return NbgLearnedSkillDraftQueue(entries = deduped)
}

internal fun parseNbgLearnedSkillDraftQueue(array: JSONArray?): NbgLearnedSkillDraftQueue =
  nbgBuildLearnedSkillDraftQueue(array.toNbgLearnedSkillDraftEntries())

private fun JSONArray?.toNbgLearnedSkillDraftEntries(): List<NbgLearnedSkillDraftQueueEntry> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      item.toNbgLearnedSkillDraftEntry()?.let(::add)
    }
  }
}

private fun JSONObject.toNbgLearnedSkillDraftEntry(): NbgLearnedSkillDraftQueueEntry? {
  val skillName = cleanStringAny("skillName", "name") ?: return null
  val targetPath = cleanStringAny("targetPath", "path", "filePath").orEmpty()
  val sourceTaskId = cleanStringAny("sourceTaskId", "taskId", "contractId").orEmpty()
  return nbgLearnedSkillDraftQueueEntry(
    id = cleanString("id").orEmpty(),
    skillName = skillName,
    description = cleanStringAny("description", "summary").orEmpty(),
    sourceTaskTitle = cleanStringAny("sourceTaskTitle", "taskTitle").orEmpty(),
    targetPath = targetPath,
    sourceTaskId = sourceTaskId,
    completionEvidence = parseNbgTaskCompletionEvidenceBundle(
      optJSONObject("completionEvidence")
        ?: optJSONObject("completion_evidence")
        ?: optJSONObject("evidenceBundle"),
    ),
    draftSha256 = cleanStringAny("draftSha256", "sha256", "hash").orEmpty(),
    permissionTier = nbgLearnedSkillDraftPermissionTier(cleanStringAny("permissionTier", "riskTier", "risk")),
    createdAtMs = optLong("createdAtMs", 0L),
    updatedAtMs = optLong("updatedAtMs", optLong("createdAtMs", 0L)),
    status = cleanStringAny("status", "state")?.let(::nbgLearnedSkillDraftStatusForWire),
  )
}

private val NbgLearnedSkillDraftStatus.sortOrder: Int
  get() = when (this) {
    NbgLearnedSkillDraftStatus.PendingReview -> 0
    NbgLearnedSkillDraftStatus.BlockedMissingEvidence -> 1
    NbgLearnedSkillDraftStatus.Rejected -> 2
  }

private fun nbgLearnedSkillDraftStatusForWire(value: String): NbgLearnedSkillDraftStatus =
  when (value.trim().lowercase().replace("-", "_")) {
    "pending_review", "pending", "review" -> NbgLearnedSkillDraftStatus.PendingReview
    "blocked_missing_evidence", "blocked", "missing_evidence", "invalid" -> NbgLearnedSkillDraftStatus.BlockedMissingEvidence
    "rejected", "declined" -> NbgLearnedSkillDraftStatus.Rejected
    else -> NbgLearnedSkillDraftStatus.PendingReview
  }

private fun nbgLearnedSkillDraftPermissionTier(value: String?): NbgPermissionRiskTier =
  when (value?.trim()?.lowercase()?.replace("-", "_")) {
    "low" -> NbgPermissionRiskTier.Low
    "high", "elevated" -> NbgPermissionRiskTier.High
    "danger", "dangerous", "critical", "destructive" -> NbgPermissionRiskTier.Dangerous
    else -> NbgPermissionRiskTier.Medium
  }

private fun nbgLearnedSkillDraftFallbackId(skillName: String, sourceTaskId: String): String =
  listOf(
    sourceTaskId.trim().ifBlank { "draft" },
    skillName.trim().ifBlank { "skill" },
  ).joinToString(":").take(180)

private fun String.nbgLearnedSkillDraftCompact(limit: Int = 180): String {
  val cleaned = replace(Regex("\\s+"), " ").trim()
  return if (cleaned.length <= limit) cleaned else cleaned.take(limit).trimEnd() + "..."
}

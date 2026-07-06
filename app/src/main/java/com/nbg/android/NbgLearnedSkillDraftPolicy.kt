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
  AutoApplied("auto_applied", "已自动应用"),
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
  val installPath: String = "",
  val rollbackPath: String = "",
  val previousArtifactSha256: String = "",
  val autoInstalled: Boolean = false,
  val autoEnabled: Boolean = false,
  val appliedAtMs: Long = 0L,
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

  val autoAppliedCount: Int
    get() = visibleEntries.count { it.status == NbgLearnedSkillDraftStatus.AutoApplied || it.autoInstalled || it.autoEnabled }

  val blockedCount: Int
    get() = visibleEntries.count { it.status == NbgLearnedSkillDraftStatus.BlockedMissingEvidence || !it.review.allowDraft }

  val dangerousCount: Int
    get() = visibleEntries.count { it.review.permissionTier == NbgPermissionRiskTier.Dangerous }

  val installableCount: Int
    get() = visibleEntries.count { it.review.allowInstall && !it.autoInstalled }

  val enableableCount: Int
    get() = visibleEntries.count { it.review.allowEnable && !it.autoEnabled }
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
  val autoSafe = allowDraft && permissionTier.ordinal <= NbgPermissionRiskTier.Medium.ordinal
  val blockedDangerous = permissionTier == NbgPermissionRiskTier.Dangerous
  return NbgLearnedSkillDraftReview(
    policyVersion = NBG_LEARNED_SKILL_DRAFT_POLICY_VERSION,
    allowDraft = allowDraft,
    allowInstall = autoSafe && !blockedDangerous,
    allowEnable = autoSafe && !blockedDangerous,
    requiresReview = !autoSafe || blockedDangerous,
    requiredEvidence = NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE,
    presentEvidence = presentEvidence,
    permissionTier = permissionTier,
    sourceTaskId = cleanTaskId,
    targetPathLabel = sourceReview.redactedLocation.ifBlank { "[skill-draft-path]" },
    reason = when {
      cleanSkillName.isBlank() -> "Skill 名称不能为空；学习结果只能进入草稿，不能直接安装或启用。"
      missing.isNotEmpty() -> "Skill 草稿缺少证据：${missing.joinToString(", ")}；学习结果保持未安装状态。"
      blockedDangerous -> "Skill 草稿可保存但包含危险权限；安装和启用必须经过强确认。"
      autoSafe -> "低/中风险 Skill 草稿证据完整；允许自动安装并启用，仍保留审计和撤回记录。"
      else -> "High 风险 Skill 草稿证据完整；安装和启用需要用户确认。"
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

internal fun NbgLearnedSkillDraftQueueEntry.withAutoAppliedArtifact(
  installPath: String,
  rollbackPath: String,
  previousArtifactSha256: String,
  autoEnable: Boolean,
  nowMs: Long,
): NbgLearnedSkillDraftQueueEntry =
  copy(
    status = NbgLearnedSkillDraftStatus.AutoApplied,
    installPath = installPath.nbgLearnedSkillDraftPathLabel(),
    rollbackPath = rollbackPath.nbgLearnedSkillDraftPathLabel(),
    previousArtifactSha256 = previousArtifactSha256.trim().lowercase().takeIf { it.matches(Regex("[a-f0-9]{64}")) }.orEmpty(),
    autoInstalled = true,
    autoEnabled = autoEnable,
    appliedAtMs = nowMs.coerceAtLeast(0L),
    updatedAtMs = nowMs.coerceAtLeast(updatedAtMs),
  )

internal fun nbgBuildLearnedSkillDraftQueue(
  entries: List<NbgLearnedSkillDraftQueueEntry>,
): NbgLearnedSkillDraftQueue {
  val deduped = entries
    .filter { it.skillName.isNotBlank() }
    .groupBy { it.id.ifBlank { nbgLearnedSkillDraftFallbackId(it.skillName, it.review.sourceTaskId) } }
    .mapNotNull { (_, grouped) -> grouped.maxWithOrNull(compareBy<NbgLearnedSkillDraftQueueEntry> { it.updatedAtMs }.thenBy { it.createdAtMs }) }
    .sortedWith(
      compareBy<NbgLearnedSkillDraftQueueEntry> { it.status.sortOrder }
        .thenByDescending { it.review.allowInstall }
        .thenByDescending { it.review.allowEnable }
        .thenByDescending { it.updatedAtMs }
        .thenByDescending { it.createdAtMs }
        .thenBy { it.skillName.lowercase() },
    )
  return NbgLearnedSkillDraftQueue(entries = deduped)
}

internal fun parseNbgLearnedSkillDraftQueue(array: JSONArray?): NbgLearnedSkillDraftQueue =
  nbgBuildLearnedSkillDraftQueue(array.toNbgLearnedSkillDraftEntries())

internal fun parseNbgLearnedSkillDraftQueue(raw: String?): NbgLearnedSkillDraftQueue =
  runCatching {
    parseNbgLearnedSkillDraftQueue(JSONArray(raw?.takeIf { it.isNotBlank() } ?: "[]"))
  }.getOrDefault(NbgLearnedSkillDraftQueue())

internal fun NbgLearnedSkillDraftQueue.toJsonArray(): JSONArray =
  JSONArray().also { array ->
    entries.forEach { array.put(it.toJson()) }
  }

internal fun NbgLearnedSkillDraftQueue.toJsonString(): String =
  toJsonArray().toString()

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
  optJSONObject("review")?.toNbgLearnedSkillDraftReview(skillName)?.let { review ->
    return NbgLearnedSkillDraftQueueEntry(
      id = cleanString("id").orEmpty().ifBlank { nbgLearnedSkillDraftFallbackId(skillName, review.sourceTaskId) },
      skillName = skillName.trim(),
      description = cleanStringAny("description", "summary").orEmpty().nbgLearnedSkillDraftCompact(),
      sourceTaskTitle = cleanStringAny("sourceTaskTitle", "taskTitle").orEmpty().nbgLearnedSkillDraftCompact(limit = 120),
      review = review,
      status = cleanStringAny("status", "state")?.let(::nbgLearnedSkillDraftStatusForWire)
        ?: if (review.allowDraft) NbgLearnedSkillDraftStatus.PendingReview else NbgLearnedSkillDraftStatus.BlockedMissingEvidence,
      installPath = cleanStringAny("installPath", "pathLabel").orEmpty().nbgLearnedSkillDraftPathLabel(),
      rollbackPath = cleanStringAny("rollbackPath", "rollbackLabel").orEmpty().nbgLearnedSkillDraftPathLabel(),
      previousArtifactSha256 = cleanStringAny("previousArtifactSha256", "previousSha256").orEmpty().trim().lowercase().takeIf { it.matches(Regex("[a-f0-9]{64}")) }.orEmpty(),
      autoInstalled = optBoolean("autoInstalled", false),
      autoEnabled = optBoolean("autoEnabled", false),
      appliedAtMs = optLong("appliedAtMs", 0L).coerceAtLeast(0L),
      createdAtMs = optLong("createdAtMs", 0L).coerceAtLeast(0L),
      updatedAtMs = optLong("updatedAtMs", optLong("createdAtMs", 0L)).coerceAtLeast(0L),
    )
  }
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
  ).let { entry ->
    val path = cleanStringAny("installPath", "pathLabel").orEmpty()
    if (entry.status == NbgLearnedSkillDraftStatus.AutoApplied || optBoolean("autoInstalled", false)) {
      entry.copy(
        installPath = path.nbgLearnedSkillDraftPathLabel(),
        rollbackPath = cleanStringAny("rollbackPath", "rollbackLabel").orEmpty().nbgLearnedSkillDraftPathLabel(),
        previousArtifactSha256 = cleanStringAny("previousArtifactSha256", "previousSha256").orEmpty().trim().lowercase().takeIf { it.matches(Regex("[a-f0-9]{64}")) }.orEmpty(),
        autoInstalled = optBoolean("autoInstalled", true),
        autoEnabled = optBoolean("autoEnabled", entry.review.allowEnable),
        appliedAtMs = optLong("appliedAtMs", optLong("updatedAtMs", optLong("createdAtMs", 0L))).coerceAtLeast(0L),
      )
    } else {
      entry
    }
  }
}

private fun NbgLearnedSkillDraftQueueEntry.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("skillName", skillName)
    .put("description", description)
    .put("sourceTaskTitle", sourceTaskTitle)
    .put("status", status.wireName)
    .put("installPath", installPath.nbgLearnedSkillDraftPathLabel())
    .put("rollbackPath", rollbackPath.nbgLearnedSkillDraftPathLabel())
    .put("previousArtifactSha256", previousArtifactSha256)
    .put("autoInstalled", autoInstalled)
    .put("autoEnabled", autoEnabled)
    .put("appliedAtMs", appliedAtMs)
    .put("createdAtMs", createdAtMs)
    .put("updatedAtMs", updatedAtMs)
    .put("review", review.toJson())

private fun NbgLearnedSkillDraftReview.toJson(): JSONObject =
  JSONObject()
    .put("policyVersion", policyVersion)
    .put("allowDraft", allowDraft)
    .put("allowInstall", allowInstall)
    .put("allowEnable", allowEnable)
    .put("requiresReview", requiresReview)
    .put("requiredEvidence", JSONArray(requiredEvidence))
    .put("presentEvidence", JSONArray(presentEvidence))
    .put("permissionTier", permissionTier.wireName)
    .put("sourceTaskId", sourceTaskId)
    .put("targetPathLabel", targetPathLabel)
    .put("reason", reason)

private fun JSONObject.toNbgLearnedSkillDraftReview(skillName: String): NbgLearnedSkillDraftReview {
  val required = optJSONArray("requiredEvidence")
    .toNbgLearnedSkillDraftStringList()
    .ifEmpty { NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE }
  val present = optJSONArray("presentEvidence")
    .toNbgLearnedSkillDraftStringList()
    .distinct()
  val missing = required - present.toSet()
  val permissionTier = nbgLearnedSkillDraftPermissionTier(cleanStringAny("permissionTier", "riskTier", "risk"))
  val allowDraft = skillName.trim().isNotBlank() && missing.isEmpty()
  val autoSafe = allowDraft && permissionTier.ordinal <= NbgPermissionRiskTier.Medium.ordinal
  val blockedDangerous = permissionTier == NbgPermissionRiskTier.Dangerous
  return NbgLearnedSkillDraftReview(
    policyVersion = cleanString("policyVersion") ?: NBG_LEARNED_SKILL_DRAFT_POLICY_VERSION,
    allowDraft = allowDraft,
    allowInstall = optBoolean("allowInstall", autoSafe) && autoSafe && !blockedDangerous,
    allowEnable = optBoolean("allowEnable", autoSafe) && autoSafe && !blockedDangerous,
    requiresReview = optBoolean("requiresReview", !autoSafe || blockedDangerous) || !autoSafe || blockedDangerous,
    requiredEvidence = required,
    presentEvidence = present,
    permissionTier = permissionTier,
    sourceTaskId = cleanString("sourceTaskId").orEmpty().take(120),
    targetPathLabel = cleanString("targetPathLabel").orEmpty().ifBlank { "[skill-draft-path]" },
    reason = cleanString("reason")
      ?: if (missing.isEmpty()) {
        "Skill 草稿证据完整；安装和启用仍需用户审核确认。"
      } else {
        "Skill 草稿缺少证据：${missing.joinToString(", ")}；学习结果保持未安装状态。"
      },
  )
}

private fun JSONArray?.toNbgLearnedSkillDraftStringList(): List<String> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val value = array.optString(index).trim()
      if (value.isNotBlank() && value != "null" && value != "undefined") add(value.take(120))
    }
  }
}

private val NbgLearnedSkillDraftStatus.sortOrder: Int
  get() = when (this) {
    NbgLearnedSkillDraftStatus.PendingReview -> 0
    NbgLearnedSkillDraftStatus.AutoApplied -> 1
    NbgLearnedSkillDraftStatus.BlockedMissingEvidence -> 2
    NbgLearnedSkillDraftStatus.Rejected -> 3
  }

private fun nbgLearnedSkillDraftStatusForWire(value: String): NbgLearnedSkillDraftStatus =
  when (value.trim().lowercase().replace("-", "_")) {
    "pending_review", "pending", "review" -> NbgLearnedSkillDraftStatus.PendingReview
    "auto_applied", "applied", "installed", "enabled" -> NbgLearnedSkillDraftStatus.AutoApplied
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

private fun String.nbgLearnedSkillDraftPathLabel(): String =
  when {
    isBlank() -> ""
    startsWith("[") && endsWith("]") -> this
    contains('/') || contains('\\') -> "[local-path]"
    else -> nbgLearnedSkillDraftCompact(limit = 120)
  }

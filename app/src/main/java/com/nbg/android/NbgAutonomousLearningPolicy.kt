package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_AUTONOMOUS_LEARNING_POLICY_VERSION = "nbg-autonomous-learning-v1"
internal const val NBG_LEARNING_AUDIT_STORE_VERSION = "nbg-learning-audit-v1"
internal const val NBG_LEARNING_MAX_TITLE_CHARS = 120
internal const val NBG_LEARNING_MAX_CONTENT_CHARS = 6_000

enum class NbgLearningCandidateKind(val wireName: String, val label: String) {
  Memory("memory", "Memory"),
  UserProfile("user_profile", "User"),
  Skill("skill", "Skill"),
  SkillImprovement("skill_improvement", "Skill 改进"),
  ScheduleSuggestion("schedule_suggestion", "定时任务"),
}

enum class NbgLearningEventStatus(val wireName: String, val label: String) {
  AutoApplied("auto_applied", "已自动应用"),
  PendingReview("pending_review", "待确认"),
  Blocked("blocked", "已阻止"),
  Rejected("rejected", "已拒绝"),
  Reverted("reverted", "已撤回"),
  Failed("failed", "失败"),
}

data class NbgLearningSettings(
  val autonomousLearningEnabled: Boolean = true,
  val autoSaveMemoryEnabled: Boolean = true,
  val autoUpdateUserProfileEnabled: Boolean = true,
  val autoInstallSkillsEnabled: Boolean = false,
  val autoEnableSkillsEnabled: Boolean = false,
  val dangerousLearningBlocked: Boolean = true,
  val auditRetention: String = "permanent",
)

data class NbgLearningCandidate(
  val id: String,
  val kind: NbgLearningCandidateKind,
  val title: String,
  val content: String,
  val sourceSessionPath: String = "",
  val sourceTaskId: String = "",
  val sourceTurnId: String = "",
  val evidenceBundle: NbgTaskCompletionEvidenceBundle? = null,
  val permissionTier: NbgPermissionRiskTier = NbgPermissionRiskTier.Low,
  val targetPath: String = "",
  val draftSha256: String = "",
  val tags: List<String> = emptyList(),
  val createdAtMs: Long = 0L,
)

data class NbgLearningReview(
  val policyVersion: String,
  val allowAutoApply: Boolean,
  val allowAutoInstall: Boolean,
  val allowAutoEnable: Boolean,
  val requiresUserConfirmation: Boolean,
  val blocked: Boolean,
  val riskTier: NbgPermissionRiskTier,
  val sensitiveFindings: List<String>,
  val reason: String,
)

data class NbgLearningEvent(
  val id: String,
  val candidate: NbgLearningCandidate,
  val review: NbgLearningReview,
  val status: NbgLearningEventStatus,
  val appliedRefs: List<String> = emptyList(),
  val error: String = "",
  val createdAtMs: Long = 0L,
  val updatedAtMs: Long = 0L,
)

data class NbgLearningAuditLog(
  val events: List<NbgLearningEvent> = emptyList(),
  val modelVersion: String = NBG_LEARNING_AUDIT_STORE_VERSION,
) {
  val autoAppliedCount: Int
    get() = events.count { it.status == NbgLearningEventStatus.AutoApplied }

  val pendingReviewCount: Int
    get() = events.count { it.status == NbgLearningEventStatus.PendingReview }

  val blockedCount: Int
    get() = events.count { it.status == NbgLearningEventStatus.Blocked }

  val activeEvents: List<NbgLearningEvent>
    get() = events.filterNot { it.status in setOf(NbgLearningEventStatus.Rejected, NbgLearningEventStatus.Reverted) }
}

internal fun nbgReviewLearningCandidate(
  candidate: NbgLearningCandidate,
  settings: NbgLearningSettings = NbgLearningSettings(),
): NbgLearningReview {
  if (!settings.autonomousLearningEnabled) {
    return candidate.learningReview(
      allowAutoApply = false,
      blocked = false,
      requiresUserConfirmation = true,
      reason = "自主学习已关闭。",
    )
  }
  val cleanTitle = candidate.title.trim()
  val cleanContent = candidate.content.trim()
  val sensitiveFindings = nbgMemorySensitiveFindings(listOf(cleanTitle, cleanContent, candidate.tags.joinToString(" ")).joinToString("\n"))
  val evidenceComplete = candidate.evidenceBundle?.review?.complete == true
  val tier = nbgHighestPermissionRiskTier(candidate.permissionTier, nbgPermissionRiskForAction(candidate.kind.wireName, cleanTitle, cleanContent).tier)
  val dangerous = tier == NbgPermissionRiskTier.Dangerous
  val blocked = settings.dangerousLearningBlocked && (dangerous || sensitiveFindings.isNotEmpty())
  if (cleanTitle.isBlank() || cleanContent.isBlank()) {
    return candidate.learningReview(
      allowAutoApply = false,
      blocked = true,
      riskTier = tier,
      sensitiveFindings = sensitiveFindings,
      reason = "学习候选缺少标题或内容。",
    )
  }
  if (cleanContent.length > NBG_LEARNING_MAX_CONTENT_CHARS) {
    return candidate.learningReview(
      allowAutoApply = false,
      blocked = true,
      riskTier = tier,
      sensitiveFindings = sensitiveFindings,
      reason = "学习候选内容过长。",
    )
  }
  if (blocked) {
    return candidate.learningReview(
      allowAutoApply = false,
      blocked = true,
      riskTier = tier,
      sensitiveFindings = sensitiveFindings,
      reason = if (sensitiveFindings.isNotEmpty()) {
        "学习候选疑似包含敏感信息：${sensitiveFindings.joinToString(", ")}。"
      } else {
        "危险学习候选不能自动应用。"
      },
    )
  }
  return when (candidate.kind) {
    NbgLearningCandidateKind.Memory -> candidate.learningReview(
      allowAutoApply = settings.autoSaveMemoryEnabled,
      riskTier = tier,
      requiresUserConfirmation = !settings.autoSaveMemoryEnabled,
      reason = if (settings.autoSaveMemoryEnabled) "低风险 Memory 可自动保存。" else "Memory 自动保存已关闭。",
    )
    NbgLearningCandidateKind.UserProfile -> candidate.learningReview(
      allowAutoApply = settings.autoUpdateUserProfileEnabled,
      riskTier = tier,
      requiresUserConfirmation = !settings.autoUpdateUserProfileEnabled,
      reason = if (settings.autoUpdateUserProfileEnabled) "用户画像可自动更新。" else "用户画像自动更新已关闭。",
    )
    NbgLearningCandidateKind.Skill,
    NbgLearningCandidateKind.SkillImprovement -> {
      val skillReview = nbgReviewLearnedSkillDraft(
        skillName = cleanTitle,
        targetPath = candidate.targetPath,
        sourceTaskId = candidate.sourceTaskId,
        completionEvidence = candidate.evidenceBundle,
        draftSha256 = candidate.draftSha256,
        permissionTier = tier,
      )
      val autoInstall = settings.autoInstallSkillsEnabled && skillReview.allowDraft && evidenceComplete && tier.ordinal <= NbgPermissionRiskTier.Medium.ordinal
      val autoEnable = autoInstall && settings.autoEnableSkillsEnabled && tier.ordinal <= NbgPermissionRiskTier.Medium.ordinal
      candidate.learningReview(
        allowAutoApply = autoInstall,
        allowAutoInstall = autoInstall,
        allowAutoEnable = autoEnable,
        riskTier = tier,
        requiresUserConfirmation = !autoEnable,
        blocked = false,
        reason = when {
          !skillReview.allowDraft -> skillReview.reason
          !evidenceComplete -> "Skill 学习需要完整任务完成证据。"
          autoEnable -> "Skill 可自动安装并启用。"
          autoInstall -> "Skill 可自动安装，启用需要确认。"
          else -> "Skill 学习需要人工确认。"
        },
      )
    }
    NbgLearningCandidateKind.ScheduleSuggestion -> {
      val autoApply = tier == NbgPermissionRiskTier.Low
      candidate.learningReview(
        allowAutoApply = autoApply,
        riskTier = tier,
        requiresUserConfirmation = !autoApply,
        reason = if (autoApply) "只读定时建议可自动启用。" else "含副作用的定时任务需要确认。",
      )
    }
  }
}

internal fun nbgLearningEventForCandidate(
  candidate: NbgLearningCandidate,
  settings: NbgLearningSettings = NbgLearningSettings(),
  nowMs: Long = System.currentTimeMillis(),
): NbgLearningEvent {
  val normalized = candidate.normalizedLearningCandidate(nowMs)
  val review = nbgReviewLearningCandidate(normalized, settings)
  val status = when {
    review.blocked -> NbgLearningEventStatus.Blocked
    review.allowAutoApply -> NbgLearningEventStatus.AutoApplied
    else -> NbgLearningEventStatus.PendingReview
  }
  return NbgLearningEvent(
    id = normalized.id.ifBlank { nbgLearningFallbackId(normalized) },
    candidate = normalized,
    review = review,
    status = status,
    createdAtMs = nowMs.coerceAtLeast(0L),
    updatedAtMs = nowMs.coerceAtLeast(0L),
  )
}

internal fun nbgBuildLearningAuditLog(events: List<NbgLearningEvent>): NbgLearningAuditLog {
  val deduped = events
    .filter { it.id.isNotBlank() }
    .groupBy { it.id }
    .mapNotNull { (_, grouped) -> grouped.maxWithOrNull(compareBy<NbgLearningEvent> { it.updatedAtMs }.thenBy { it.createdAtMs }) }
    .sortedByDescending { it.updatedAtMs }
  return NbgLearningAuditLog(events = deduped)
}

internal fun parseNbgLearningAuditLog(raw: String?): NbgLearningAuditLog =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    parseNbgLearningAuditLog(root)
  }.getOrDefault(NbgLearningAuditLog())

internal fun parseNbgLearningAuditLog(root: JSONObject): NbgLearningAuditLog {
  val events = root.optJSONArray("events").toLearningEvents()
  return nbgBuildLearningAuditLog(events)
}

internal fun NbgLearningAuditLog.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("events", JSONArray().also { array ->
      events.forEach { array.put(it.toJson()) }
    })
    .toString()

internal fun NbgLearningEvent.withStatus(
  status: NbgLearningEventStatus,
  nowMs: Long = System.currentTimeMillis(),
  error: String = this.error,
): NbgLearningEvent =
  copy(
    status = status,
    error = error.nbgLearningCompact(limit = 240),
    updatedAtMs = nowMs.coerceAtLeast(updatedAtMs),
  )

private fun NbgLearningCandidate.learningReview(
  allowAutoApply: Boolean,
  allowAutoInstall: Boolean = false,
  allowAutoEnable: Boolean = false,
  requiresUserConfirmation: Boolean = !allowAutoApply,
  blocked: Boolean = false,
  riskTier: NbgPermissionRiskTier = permissionTier,
  sensitiveFindings: List<String> = emptyList(),
  reason: String,
): NbgLearningReview =
  NbgLearningReview(
    policyVersion = NBG_AUTONOMOUS_LEARNING_POLICY_VERSION,
    allowAutoApply = allowAutoApply,
    allowAutoInstall = allowAutoInstall,
    allowAutoEnable = allowAutoEnable,
    requiresUserConfirmation = requiresUserConfirmation,
    blocked = blocked,
    riskTier = riskTier,
    sensitiveFindings = sensitiveFindings.distinct(),
    reason = reason.nbgLearningCompact(limit = 240),
  )

private fun NbgLearningCandidate.normalizedLearningCandidate(nowMs: Long): NbgLearningCandidate =
  copy(
    id = id.trim().ifBlank { nbgLearningFallbackId(this) },
    title = title.nbgLearningCompact(limit = NBG_LEARNING_MAX_TITLE_CHARS),
    content = content.nbgLearningCompact(limit = NBG_LEARNING_MAX_CONTENT_CHARS),
    sourceSessionPath = sourceSessionPath.nbgLearningCompact(limit = 180),
    sourceTaskId = sourceTaskId.nbgLearningCompact(limit = 120),
    sourceTurnId = sourceTurnId.nbgLearningCompact(limit = 120),
    targetPath = targetPath.trim(),
    draftSha256 = draftSha256.trim().lowercase(),
    tags = tags.map { it.nbgLearningCompact(limit = 48) }.filter { it.isNotBlank() }.distinct().take(16),
    createdAtMs = createdAtMs.takeIf { it > 0L } ?: nowMs.coerceAtLeast(0L),
  )

private fun nbgLearningFallbackId(candidate: NbgLearningCandidate): String =
  listOf(candidate.kind.wireName, candidate.title, candidate.content, candidate.sourceTaskId)
    .joinToString("\u001f")
    .sha256Hex()
    .take(24)

private fun NbgLearningEvent.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("candidate", candidate.toJson())
    .put("review", review.toJson())
    .put("status", status.wireName)
    .put("appliedRefs", JSONArray(appliedRefs.map { it.nbgLearningCompact(limit = 160) }))
    .put("error", error)
    .put("createdAtMs", createdAtMs)
    .put("updatedAtMs", updatedAtMs)

private fun NbgLearningCandidate.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("kind", kind.wireName)
    .put("title", title)
    .put("content", nbgRedactDiagnosticText(content).take(NBG_LEARNING_MAX_CONTENT_CHARS))
    .put("sourceSessionPath", sourceSessionPath)
    .put("sourceTaskId", sourceTaskId)
    .put("sourceTurnId", sourceTurnId)
    .put("permissionTier", permissionTier.wireName)
    .put("targetPath", if (targetPath.isBlank()) "" else "[learning-target-path]")
    .put("draftSha256", draftSha256.takeIf { it.matches(Regex("[a-f0-9]{64}")) }.orEmpty())
    .put("tags", JSONArray(tags))
    .put("createdAtMs", createdAtMs)
    .apply {
      evidenceBundle?.let { put("evidenceBundle", it.toJson()) }
    }

private fun NbgLearningReview.toJson(): JSONObject =
  JSONObject()
    .put("policyVersion", policyVersion)
    .put("allowAutoApply", allowAutoApply)
    .put("allowAutoInstall", allowAutoInstall)
    .put("allowAutoEnable", allowAutoEnable)
    .put("requiresUserConfirmation", requiresUserConfirmation)
    .put("blocked", blocked)
    .put("riskTier", riskTier.wireName)
    .put("sensitiveFindings", JSONArray(sensitiveFindings))
    .put("reason", reason)

private fun JSONArray?.toLearningEvents(): List<NbgLearningEvent> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      array.optJSONObject(index).toLearningEvent()?.let { add(it) }
    }
  }
}

private fun JSONObject?.toLearningEvent(): NbgLearningEvent? {
  val root = this ?: return null
  val candidate = root.optJSONObject("candidate").toLearningCandidate() ?: return null
  val review = root.optJSONObject("review").toLearningReview(candidate)
  return NbgLearningEvent(
    id = root.cleanString("id").orEmpty().ifBlank { candidate.id },
    candidate = candidate,
    review = review,
    status = nbgLearningEventStatus(root.cleanString("status")),
    appliedRefs = root.optJSONArray("appliedRefs").toLearningStrings(limit = 160),
    error = root.cleanString("error").orEmpty().nbgLearningCompact(limit = 240),
    createdAtMs = root.optLong("createdAtMs", 0L).coerceAtLeast(0L),
    updatedAtMs = root.optLong("updatedAtMs", root.optLong("createdAtMs", 0L)).coerceAtLeast(0L),
  )
}

private fun JSONObject?.toLearningCandidate(): NbgLearningCandidate? {
  val root = this ?: return null
  val kind = nbgLearningCandidateKind(root.cleanString("kind"))
  val title = root.cleanString("title").orEmpty()
  val content = root.cleanString("content").orEmpty()
  if (title.isBlank() && content.isBlank()) return null
  return NbgLearningCandidate(
    id = root.cleanString("id").orEmpty(),
    kind = kind,
    title = title,
    content = content,
    sourceSessionPath = root.cleanString("sourceSessionPath").orEmpty(),
    sourceTaskId = root.cleanString("sourceTaskId").orEmpty(),
    sourceTurnId = root.cleanString("sourceTurnId").orEmpty(),
    evidenceBundle = parseNbgTaskCompletionEvidenceBundle(root.optJSONObject("evidenceBundle")),
    permissionTier = nbgPermissionRiskTierForLearning(root.cleanString("permissionTier")),
    targetPath = root.cleanString("targetPath").orEmpty(),
    draftSha256 = root.cleanString("draftSha256").orEmpty(),
    tags = root.optJSONArray("tags").toLearningStrings(limit = 48),
    createdAtMs = root.optLong("createdAtMs", 0L).coerceAtLeast(0L),
  )
}

private fun JSONObject?.toLearningReview(candidate: NbgLearningCandidate): NbgLearningReview =
  this?.let { root ->
    NbgLearningReview(
      policyVersion = root.cleanString("policyVersion").orEmpty().ifBlank { NBG_AUTONOMOUS_LEARNING_POLICY_VERSION },
      allowAutoApply = root.optBoolean("allowAutoApply", false),
      allowAutoInstall = root.optBoolean("allowAutoInstall", false),
      allowAutoEnable = root.optBoolean("allowAutoEnable", false),
      requiresUserConfirmation = root.optBoolean("requiresUserConfirmation", true),
      blocked = root.optBoolean("blocked", false),
      riskTier = nbgPermissionRiskTierForLearning(root.cleanString("riskTier")),
      sensitiveFindings = root.optJSONArray("sensitiveFindings").toLearningStrings(limit = 48),
      reason = root.cleanString("reason").orEmpty(),
    )
  } ?: nbgReviewLearningCandidate(candidate)

private fun JSONArray?.toLearningStrings(limit: Int): List<String> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val value = array.optString(index).nbgLearningCompact(limit)
      if (value.isNotBlank() && value != "null" && value != "undefined") add(value)
    }
  }.distinct()
}

private fun nbgLearningCandidateKind(raw: String?): NbgLearningCandidateKind =
  NbgLearningCandidateKind.entries.firstOrNull { it.wireName == raw?.trim()?.lowercase() }
    ?: NbgLearningCandidateKind.Memory

private fun nbgLearningEventStatus(raw: String?): NbgLearningEventStatus =
  NbgLearningEventStatus.entries.firstOrNull { it.wireName == raw?.trim()?.lowercase() }
    ?: NbgLearningEventStatus.PendingReview

private fun nbgPermissionRiskTierForLearning(raw: String?): NbgPermissionRiskTier =
  NbgPermissionRiskTier.entries.firstOrNull { it.wireName == raw?.trim()?.lowercase() }
    ?: NbgPermissionRiskTier.Low

private fun String.nbgLearningCompact(limit: Int): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit.coerceAtLeast(0))

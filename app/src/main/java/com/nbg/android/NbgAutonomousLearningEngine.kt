package com.nbg.android

import android.content.Context

internal const val NBG_AUTONOMOUS_LEARNING_ENGINE_VERSION = "nbg-autonomous-learning-engine-v1"

data class NbgLearningSourceTurn(
  val userText: String,
  val assistantText: String = "",
  val sessionPath: String = "",
  val turnId: String = "",
  val timestampMs: Long = 0L,
)

data class NbgLearningGraphNode(
  val id: String,
  val kind: String,
  val title: String,
  val subtitle: String = "",
  val state: String = "",
  val relatedIds: List<String> = emptyList(),
)

data class NbgLearningGraphEdge(
  val fromId: String,
  val toId: String,
  val reason: String,
)

data class NbgLearningGraph(
  val nodes: List<NbgLearningGraphNode> = emptyList(),
  val edges: List<NbgLearningGraphEdge> = emptyList(),
) {
  val memoryCount: Int
    get() = nodes.count { it.kind == "memory" }

  val profileCount: Int
    get() = nodes.count { it.kind == "profile" }

  val skillCount: Int
    get() = nodes.count { it.kind == "skill" }
}

data class NbgAutonomousLearningSnapshot(
  val settings: NbgLearningSettings = NbgLearningSettings(),
  val auditLog: NbgLearningAuditLog = NbgLearningAuditLog(),
  val localMemory: NbgLocalLearningMemory = NbgLocalLearningMemory(),
  val userProfile: NbgUserProfile = NbgUserProfile(),
  val soul: NbgAgentSoulConfig = NbgAgentSoulConfig(),
  val learnedSkillDraftQueue: NbgLearnedSkillDraftQueue = NbgLearnedSkillDraftQueue(),
  val graph: NbgLearningGraph = NbgLearningGraph(),
) {
  val autoAppliedCount: Int
    get() = auditLog.autoAppliedCount

  val pendingReviewCount: Int
    get() = auditLog.pendingReviewCount + learnedSkillDraftQueue.pendingReviewCount

  val blockedCount: Int
    get() = auditLog.blockedCount + learnedSkillDraftQueue.blockedCount
}

internal class NbgAutonomousLearningEngine(
  private val auditStore: NbgLearningAuditStore,
  private val memoryStore: NbgLocalLearningMemoryStore,
  private val userProfileStore: NbgUserProfileStore,
  private val soulStore: NbgAgentSoulStore,
  private val learnedSkillDraftStore: NbgLearnedSkillDraftStore,
) {
  constructor(context: Context) : this(
    auditStore = NbgLearningAuditStore(context),
    memoryStore = NbgLocalLearningMemoryStore(context),
    userProfileStore = NbgUserProfileStore(context),
    soulStore = NbgAgentSoulStore(context),
    learnedSkillDraftStore = NbgLearnedSkillDraftStore(context),
  )

  fun snapshot(settings: NbgLearningSettings = NbgLearningSettings()): NbgAutonomousLearningSnapshot {
    val audit = auditStore.load()
    val memory = memoryStore.load()
    val profile = userProfileStore.load()
    val soul = soulStore.load()
    val skillDrafts = learnedSkillDraftStore.load()
    return NbgAutonomousLearningSnapshot(
      settings = settings,
      auditLog = audit,
      localMemory = memory,
      userProfile = profile,
      soul = soul,
      learnedSkillDraftQueue = skillDrafts,
      graph = nbgBuildLearningGraph(memory, profile, soul, skillDrafts),
    )
  }

  fun learnFromTurn(
    turn: NbgLearningSourceTurn,
    settings: NbgLearningSettings = NbgLearningSettings(),
    nowMs: Long = System.currentTimeMillis(),
  ): NbgAutonomousLearningSnapshot {
    val candidates = nbgLearningCandidatesFromTurn(turn, nowMs)
    var audit = auditStore.load()
    candidates.forEach { candidate ->
      val event = nbgLearningEventForCandidate(candidate, settings, nowMs)
      audit = auditStore.record(event)
      if (event.status == NbgLearningEventStatus.AutoApplied) {
        applyAutoLearningEvent(event)
      } else if (
        event.candidate.kind in setOf(NbgLearningCandidateKind.Skill, NbgLearningCandidateKind.SkillImprovement) &&
        !event.review.blocked
      ) {
        applyAutoLearningEvent(event)
      }
    }
    return snapshot(settings)
  }

  fun approveEvent(id: String, nowMs: Long = System.currentTimeMillis()): NbgAutonomousLearningSnapshot {
    val event = auditStore.load().events.firstOrNull { it.id == id } ?: return snapshot()
    if (event.review.blocked) return snapshot()
    applyAutoLearningEvent(event.copy(status = NbgLearningEventStatus.AutoApplied, updatedAtMs = nowMs))
    auditStore.updateStatus(id, NbgLearningEventStatus.AutoApplied, nowMs)
    return snapshot()
  }

  fun rejectEvent(id: String, nowMs: Long = System.currentTimeMillis()): NbgAutonomousLearningSnapshot {
    auditStore.updateStatus(id, NbgLearningEventStatus.Rejected, nowMs)
    return snapshot()
  }

  fun revertEvent(id: String, nowMs: Long = System.currentTimeMillis()): NbgAutonomousLearningSnapshot {
    auditStore.revert(id, nowMs)
    return snapshot()
  }

  private fun applyAutoLearningEvent(event: NbgLearningEvent) {
    when (event.candidate.kind) {
      NbgLearningCandidateKind.Memory -> {
        memoryStore.upsert(nbgLocalLearningMemoryEntryFromEvent(event) ?: return)
      }
      NbgLearningCandidateKind.UserProfile -> {
        nbgUserProfileEntryFromLearningEvent(event)?.let(userProfileStore::upsert)
        nbgAgentSoulConfigFromLearningEvent(event)?.let { learnedSoul ->
          val current = soulStore.load()
          soulStore.save(
            current.copy(
              principles = current.principles + learnedSoul.principles,
              styleHints = current.styleHints + learnedSoul.styleHints,
              sourceEventIds = current.sourceEventIds + learnedSoul.sourceEventIds,
              updatedAtMs = learnedSoul.updatedAtMs,
            ),
          )
        }
      }
      NbgLearningCandidateKind.Skill,
      NbgLearningCandidateKind.SkillImprovement -> {
        learnedSkillDraftStore.upsert(nbgLearnedSkillDraftQueueEntryFromLearningEvent(event))
      }
      NbgLearningCandidateKind.ScheduleSuggestion -> Unit
    }
  }
}

internal fun nbgLearningCandidatesFromTurn(
  turn: NbgLearningSourceTurn,
  nowMs: Long = System.currentTimeMillis(),
): List<NbgLearningCandidate> {
  val text = turn.userText.trim()
  if (text.isBlank()) return emptyList()
  val sourceSessionPath = turn.sessionPath.trim()
  val sourceTurnId = turn.turnId.trim()
  val baseId = listOf(text, sourceSessionPath, sourceTurnId, nowMs.toString()).joinToString("\u001f").sha256Hex().take(16)
  val candidates = mutableListOf<NbgLearningCandidate>()
  nbgLearningMemoryCandidate(text, baseId, sourceSessionPath, sourceTurnId, nowMs)?.let(candidates::add)
  nbgLearningProfileCandidate(text, baseId, sourceSessionPath, sourceTurnId, nowMs)?.let(candidates::add)
  nbgLearningSkillCandidate(text, baseId, sourceSessionPath, sourceTurnId, nowMs)?.let(candidates::add)
  return candidates
}

private fun nbgLearningMemoryCandidate(
  text: String,
  baseId: String,
  sourceSessionPath: String,
  sourceTurnId: String,
  nowMs: Long,
): NbgLearningCandidate? {
  val extracted = nbgExtractAfterLearningMarker(text, NBG_MEMORY_LEARNING_MARKERS) ?: return null
  return NbgLearningCandidate(
    id = "memory-$baseId",
    kind = NbgLearningCandidateKind.Memory,
    title = nbgLearningTitle(extracted, fallback = "自动记忆"),
    content = extracted,
    sourceSessionPath = sourceSessionPath,
    sourceTurnId = sourceTurnId,
    permissionTier = NbgPermissionRiskTier.Low,
    tags = listOf("auto", "memory"),
    createdAtMs = nowMs,
  )
}

private fun nbgLearningProfileCandidate(
  text: String,
  baseId: String,
  sourceSessionPath: String,
  sourceTurnId: String,
  nowMs: Long,
): NbgLearningCandidate? {
  val extracted = nbgExtractAfterLearningMarker(text, NBG_PROFILE_LEARNING_MARKERS) ?: return null
  val key = when {
    text.contains("原则") || text.contains("soul", ignoreCase = true) -> "soul"
    text.contains("风格") || text.contains("style", ignoreCase = true) -> "style"
    text.contains("偏好") || text.contains("prefer", ignoreCase = true) -> "preference"
    else -> "profile"
  }
  return NbgLearningCandidate(
    id = "profile-$baseId",
    kind = NbgLearningCandidateKind.UserProfile,
    title = key,
    content = extracted,
    sourceSessionPath = sourceSessionPath,
    sourceTurnId = sourceTurnId,
    permissionTier = NbgPermissionRiskTier.Low,
    tags = if (key == "soul") listOf("auto", "profile", "soul") else listOf("auto", "profile"),
    createdAtMs = nowMs,
  )
}

private fun nbgLearningSkillCandidate(
  text: String,
  baseId: String,
  sourceSessionPath: String,
  sourceTurnId: String,
  nowMs: Long,
): NbgLearningCandidate? {
  val extracted = nbgExtractAfterLearningMarker(text, NBG_SKILL_LEARNING_MARKERS) ?: return null
  val skillName = nbgLearningSkillName(extracted)
  val draftText = nbgBuildSkillDraftBody(skillName, extracted)
  return NbgLearningCandidate(
    id = "skill-$baseId",
    kind = NbgLearningCandidateKind.Skill,
    title = skillName,
    content = extracted,
    sourceSessionPath = sourceSessionPath,
    sourceTaskId = "learn-$baseId",
    sourceTurnId = sourceTurnId,
    evidenceBundle = nbgLearningSelfEvidence("learn-$baseId", extracted),
    permissionTier = nbgPermissionRiskForAction("learn skill", skillName, extracted).tier,
    targetPath = "/data/data/com.nbg.android/files/learned-skills/$skillName/SKILL.md",
    draftSha256 = draftText.sha256Hex(),
    tags = listOf("auto", "skill", "learn"),
    createdAtMs = nowMs,
  )
}

private fun nbgLearnedSkillDraftQueueEntryFromLearningEvent(event: NbgLearningEvent): NbgLearnedSkillDraftQueueEntry =
  nbgLearnedSkillDraftQueueEntry(
    id = event.id,
    skillName = event.candidate.title,
    description = event.candidate.content.take(180),
    sourceTaskTitle = "自主学习：${event.candidate.title}",
    targetPath = event.candidate.targetPath,
    sourceTaskId = event.candidate.sourceTaskId,
    completionEvidence = event.candidate.evidenceBundle,
    draftSha256 = event.candidate.draftSha256,
    permissionTier = event.review.riskTier,
    createdAtMs = event.createdAtMs,
    updatedAtMs = event.updatedAtMs,
  )

private fun nbgLearningSelfEvidence(contractId: String, content: String): NbgTaskCompletionEvidenceBundle =
  NbgTaskCompletionEvidenceBundle(
    contractId = contractId,
    title = "学习候选已生成",
    criteria = listOf(
      nbgTaskCriterion(NbgTaskCompletionCriterionKind.TestResult, "学习内容通过本地策略审核"),
    ),
    evidence = listOf(
      nbgTaskEvidence(
        kind = NbgTaskCompletionCriterionKind.TestResult,
        state = NbgTaskCompletionEvidenceState.Passed,
        label = "learning_policy_review",
        summary = "本地从 ${content.length.coerceAtMost(NBG_LEARNING_MAX_CONTENT_CHARS)} 字符生成 Skill 草稿候选。",
      ),
    ),
  )

private fun nbgBuildSkillDraftBody(skillName: String, request: String): String =
  """
  ---
  name: $skillName
  description: Learned Android workflow draft.
  version: 0.1.0
  author: NBG
  ---

  # ${skillName.replace('-', ' ').replaceFirstChar { it.uppercase() }}

  ## When to Use
  - $request

  ## Verification
  - Review this draft before install or enable.
  """.trimIndent()

private fun nbgExtractAfterLearningMarker(text: String, markers: List<String>): String? {
  val lower = text.lowercase()
  val marker = markers.firstOrNull { lower.contains(it.lowercase()) } ?: return null
  val raw = text.substringAfter(marker, missingDelimiterValue = text)
    .trim(':', '：', ' ', '\n', '\t')
    .ifBlank { text.trim() }
  val cleaned = raw.replace(Regex("\\s+"), " ").trim()
  return cleaned.takeIf { it.length >= 4 }?.take(NBG_LEARNING_MAX_CONTENT_CHARS)
}

private fun nbgLearningTitle(text: String, fallback: String): String =
  text.lineSequence()
    .firstOrNull()
    .orEmpty()
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(48)
    .ifBlank { fallback }

private fun nbgLearningSkillName(text: String): String {
  val ascii = text.lowercase()
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .take(48)
    .trim('-')
  return ascii.ifBlank { "learned-android-workflow" }
}

internal fun nbgBuildLearningGraph(
  memory: NbgLocalLearningMemory,
  profile: NbgUserProfile,
  soul: NbgAgentSoulConfig,
  skillDrafts: NbgLearnedSkillDraftQueue,
): NbgLearningGraph {
  val memoryNodes = memory.entries.map {
    NbgLearningGraphNode(
      id = "memory:${it.id}",
      kind = "memory",
      title = it.title,
      subtitle = it.type,
      state = if (it.enabled) "active" else "disabled",
    )
  }
  val profileNodes = profile.visibleEntries.map {
    NbgLearningGraphNode(
      id = "profile:${it.key}",
      kind = "profile",
      title = it.key,
      subtitle = it.value.take(96),
      state = "active",
    )
  }
  val soulNodes = soul.principles.mapIndexed { index, principle ->
    NbgLearningGraphNode(
      id = "soul:$index",
      kind = "profile",
      title = "soul",
      subtitle = principle.take(96),
      state = "active",
    )
  }
  val skillNodes = skillDrafts.visibleEntries.map {
    NbgLearningGraphNode(
      id = "skill:${it.id}",
      kind = "skill",
      title = it.skillName,
      subtitle = it.description,
      state = it.status.wireName,
    )
  }
  val nodes = memoryNodes + profileNodes + soulNodes + skillNodes
  val edges = nbgLearningGraphEdges(nodes)
  return NbgLearningGraph(nodes = nodes, edges = edges)
}

private fun nbgLearningGraphEdges(nodes: List<NbgLearningGraphNode>): List<NbgLearningGraphEdge> {
  val edges = mutableListOf<NbgLearningGraphEdge>()
  nodes.forEachIndexed { leftIndex, left ->
    val leftTokens = nbgLearningGraphTokens(left.title + " " + left.subtitle)
    if (leftTokens.isEmpty()) return@forEachIndexed
    nodes.drop(leftIndex + 1).forEach { right ->
      val overlap = leftTokens.intersect(nbgLearningGraphTokens(right.title + " " + right.subtitle))
      if (overlap.isNotEmpty()) {
        edges.add(NbgLearningGraphEdge(left.id, right.id, overlap.take(3).joinToString(",")))
      }
    }
  }
  return edges.take(80)
}

private fun nbgLearningGraphTokens(text: String): Set<String> =
  text.lowercase()
    .split(Regex("[^a-z0-9\\u4e00-\\u9fff]+"))
    .map { it.trim() }
    .filter { it.length >= 2 }
    .toSet()

private val NBG_MEMORY_LEARNING_MARKERS = listOf(
  "记住",
  "保存记忆",
  "记到 memory",
  "remember",
  "save memory",
)

private val NBG_PROFILE_LEARNING_MARKERS = listOf(
  "我的偏好",
  "以后按",
  "以后用",
  "我的风格",
  "soul",
  "my preference",
  "my style",
)

private val NBG_SKILL_LEARNING_MARKERS = listOf(
  "/learn",
  "学习这个流程",
  "学会这个流程",
  "保存为技能",
  "learn this workflow",
  "save as skill",
)

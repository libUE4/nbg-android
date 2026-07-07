package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_SKILL_CURATOR_MODEL_VERSION = "nbg-skill-curator-v2"

data class NbgSkillCuratorSkillStats(
  val skillName: String,
  val useCount: Int = 0,
  val lastUsedAtMs: Long = 0L,
  val archived: Boolean = false,
  val archivedAtMs: Long = 0L,
  val archiveReason: String = "",
) {
  val updatedAtMs: Long
    get() = maxOf(lastUsedAtMs, archivedAtMs)
}

data class NbgSkillCuratorMetadata(
  val entries: Map<String, NbgSkillCuratorSkillStats> = emptyMap(),
  val modelVersion: String = NBG_SKILL_CURATOR_MODEL_VERSION,
) {
  val archivedSkillNames: Set<String>
    get() = entries.values.filter { it.archived }.map { it.skillName }.toSet()

  val usageEventCount: Int
    get() = entries.values.sumOf { it.useCount.coerceAtLeast(0) }

  fun statsFor(skillName: String): NbgSkillCuratorSkillStats? =
    entries[skillName.trim()]
}

data class NbgSkillCuratorSummary(
  val modelVersion: String = NBG_SKILL_CURATOR_MODEL_VERSION,
  val visibleCount: Int,
  val enabledCount: Int,
  val disabledCount: Int,
  val requiresReviewCount: Int,
  val deletableCount: Int,
  val bundledTrustedCount: Int,
  val unverifiedExternalCount: Int,
  val archivedCount: Int = 0,
  val usageEventCount: Int = 0,
  val mostUsedSkillName: String = "",
  val curatorStatus: String,
  val autoDeleteAllowed: Boolean = false,
  val autoEnableAllowed: Boolean = false,
)

data class NbgSkillCuratorArchivedSkill(
  val skillName: String,
  val source: String,
  val archivedAtMs: Long,
  val archiveReason: String,
  val useCount: Int,
)

data class NbgSkillCuratorReviewAction(
  val skillName: String,
  val action: String,
  val reason: String,
  val safeToAutoArchive: Boolean,
)

data class NbgSkillCuratorReviewResult(
  val actions: List<NbgSkillCuratorReviewAction>,
  val metadata: NbgSkillCuratorMetadata,
  val reviewedAtMs: Long,
) {
  val archivedCount: Int
    get() = actions.count { it.action == "archive" && it.safeToAutoArchive }
}

internal fun nbgBuildSkillCuratorSummary(
  snapshot: HanakoSkillsSnapshot,
  metadata: NbgSkillCuratorMetadata = NbgSkillCuratorMetadata(),
): NbgSkillCuratorSummary {
  val archivedNames = metadata.archivedSkillNames
  val visible = snapshot.visibleSkills.filterNot { it.name in archivedNames && !it.enabled }
  val reviews = visible.map { nbgSkillSummarySourceReview(it) }
  val requiresReviewCount = reviews.count { it.requiresReview }
  val unverifiedCount = reviews.count { it.trustTier == NbgSkillTrustTier.UnverifiedExternal }
  val visibleNames = visible.map { it.name }.toSet()
  val usageEntries = metadata.entries.values
    .filter { it.skillName in visibleNames && it.useCount > 0 }
  val archivedCount = snapshot.visibleSkills.count { it.name in archivedNames && !it.enabled }
  return NbgSkillCuratorSummary(
    visibleCount = visible.size,
    enabledCount = visible.count { it.enabled },
    disabledCount = visible.count { !it.enabled },
    requiresReviewCount = requiresReviewCount,
    deletableCount = visible.count { it.deletable },
    bundledTrustedCount = reviews.count { it.trustTier == NbgSkillTrustTier.TrustedBundled },
    unverifiedExternalCount = unverifiedCount,
    archivedCount = archivedCount,
    usageEventCount = metadata.usageEventCount,
    mostUsedSkillName = usageEntries.maxWithOrNull(
      compareBy<NbgSkillCuratorSkillStats> { it.useCount }
        .thenBy { it.lastUsedAtMs },
    )?.skillName.orEmpty(),
    curatorStatus = when {
      visible.isEmpty() -> "没有可见 Skill"
      archivedCount > 0 -> "$archivedCount 个已归档"
      unverifiedCount > 0 -> "$unverifiedCount 个未验证来源"
      requiresReviewCount > 0 -> "$requiresReviewCount 个需要复核"
      else -> "来源健康"
    },
    autoDeleteAllowed = false,
    autoEnableAllowed = false,
  )
}

internal fun nbgApplySkillCuratorMetadata(
  snapshot: HanakoSkillsSnapshot,
  metadata: NbgSkillCuratorMetadata,
): HanakoSkillsSnapshot {
  val archived = metadata.archivedSkillNames
  if (archived.isEmpty()) return snapshot
  return snapshot.copy(skills = snapshot.skills.filterNot { it.name in archived && !it.enabled })
}

internal fun nbgSkillCuratorArchivedSkills(
  snapshot: HanakoSkillsSnapshot,
  metadata: NbgSkillCuratorMetadata,
): List<NbgSkillCuratorArchivedSkill> {
  val byName = metadata.entries
  return snapshot.visibleSkills
    .mapNotNull { skill ->
      if (skill.enabled) return@mapNotNull null
      val stats = byName[skill.name]?.takeIf { it.archived } ?: return@mapNotNull null
      NbgSkillCuratorArchivedSkill(
        skillName = skill.name,
        source = skill.source,
        archivedAtMs = stats.archivedAtMs,
        archiveReason = stats.archiveReason,
        useCount = stats.useCount,
      )
    }
    .sortedWith(
      compareByDescending<NbgSkillCuratorArchivedSkill> { it.archivedAtMs }
        .thenBy { it.skillName.lowercase() },
    )
}

internal fun nbgRunSkillCuratorReview(
  snapshot: HanakoSkillsSnapshot,
  drafts: NbgLearnedSkillDraftQueue,
  metadata: NbgSkillCuratorMetadata,
  nowMs: Long = System.currentTimeMillis(),
): NbgSkillCuratorReviewResult {
  var next = metadata
  val draftNames = drafts.visibleEntries
    .filter { it.autoInstalled || it.status == NbgLearnedSkillDraftStatus.AutoApplied }
    .map { it.skillName }
    .toSet()
  val actions = snapshot.visibleSkills.mapNotNull { skill ->
    if (skill.enabled || skill.source == "builtin" || skill.source == "external" || skill.readonly) return@mapNotNull null
    if (skill.name !in draftNames && !skill.filePath.contains("learned-skills")) return@mapNotNull null
    val stats = metadata.statsFor(skill.name)
    if (stats?.archived == true) return@mapNotNull null
    val useCount = stats?.useCount ?: 0
    if (useCount > 0) return@mapNotNull null
    val reason = "curator_review_unused_learned_skill"
    next = nbgArchiveSkillCuratorEntry(next, skill.name, reason = reason, nowMs = nowMs)
    NbgSkillCuratorReviewAction(
      skillName = skill.name,
      action = "archive",
      reason = reason,
      safeToAutoArchive = true,
    )
  }
  return NbgSkillCuratorReviewResult(actions = actions, metadata = next, reviewedAtMs = nowMs.coerceAtLeast(0L))
}

internal fun nbgBuildSkillCuratorMetadata(
  entries: List<NbgSkillCuratorSkillStats>,
): NbgSkillCuratorMetadata {
  val deduped = entries
    .mapNotNull { stats ->
      val name = stats.skillName.trim()
      if (name.isBlank()) {
        null
      } else {
        stats.copy(
          skillName = name.take(160),
          useCount = stats.useCount.coerceAtLeast(0),
          lastUsedAtMs = stats.lastUsedAtMs.coerceAtLeast(0L),
          archivedAtMs = stats.archivedAtMs.coerceAtLeast(0L),
          archiveReason = stats.archiveReason.nbgSkillCuratorCompact(),
        )
      }
    }
    .groupBy { it.skillName }
    .mapValues { (_, grouped) ->
      grouped.maxWithOrNull(
        compareBy<NbgSkillCuratorSkillStats> { it.updatedAtMs }
          .thenBy { it.useCount },
      ) ?: grouped.last()
    }
  return NbgSkillCuratorMetadata(entries = deduped)
}

internal fun nbgRecordSkillCuratorUse(
  metadata: NbgSkillCuratorMetadata,
  skillName: String,
  nowMs: Long = System.currentTimeMillis(),
): NbgSkillCuratorMetadata {
  val name = skillName.trim()
  if (name.isBlank()) return metadata
  val current = metadata.statsFor(name) ?: NbgSkillCuratorSkillStats(skillName = name)
  return nbgBuildSkillCuratorMetadata(
    metadata.entries.values.filterNot { it.skillName == name } + current.copy(
      useCount = current.useCount.coerceAtLeast(0) + 1,
      lastUsedAtMs = nowMs.coerceAtLeast(current.lastUsedAtMs),
    ),
  )
}

internal fun nbgArchiveSkillCuratorEntry(
  metadata: NbgSkillCuratorMetadata,
  skillName: String,
  reason: String = "user_archive",
  nowMs: Long = System.currentTimeMillis(),
): NbgSkillCuratorMetadata {
  val name = skillName.trim()
  if (name.isBlank()) return metadata
  val current = metadata.statsFor(name) ?: NbgSkillCuratorSkillStats(skillName = name)
  return nbgBuildSkillCuratorMetadata(
    metadata.entries.values.filterNot { it.skillName == name } + current.copy(
      archived = true,
      archivedAtMs = nowMs.coerceAtLeast(current.archivedAtMs),
      archiveReason = reason.ifBlank { "user_archive" },
    ),
  )
}

internal fun nbgRestoreSkillCuratorEntry(
  metadata: NbgSkillCuratorMetadata,
  skillName: String,
): NbgSkillCuratorMetadata {
  val name = skillName.trim()
  if (name.isBlank()) return metadata
  val current = metadata.statsFor(name) ?: return metadata
  return nbgBuildSkillCuratorMetadata(
    metadata.entries.values.filterNot { it.skillName == name } + current.copy(
      archived = false,
      archivedAtMs = 0L,
      archiveReason = "",
    ),
  )
}

internal fun parseNbgSkillCuratorMetadata(raw: String?): NbgSkillCuratorMetadata =
  runCatching {
    val value = raw?.takeIf { it.isNotBlank() } ?: return@runCatching NbgSkillCuratorMetadata()
    val trimmed = value.trim()
    if (trimmed.startsWith("[")) {
      nbgBuildSkillCuratorMetadata(JSONArray(trimmed).toNbgSkillCuratorStats())
    } else {
      val root = JSONObject(trimmed)
      nbgBuildSkillCuratorMetadata(root.optJSONArray("entries").toNbgSkillCuratorStats())
    }
  }.getOrDefault(NbgSkillCuratorMetadata())

internal fun NbgSkillCuratorMetadata.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put(
      "entries",
      JSONArray().also { array ->
        entries.values
          .sortedBy { it.skillName.lowercase() }
          .forEach { array.put(it.toJson()) }
      },
    )
    .toString()

private fun JSONArray?.toNbgSkillCuratorStats(): List<NbgSkillCuratorSkillStats> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val name = item.cleanStringAny("skillName", "name") ?: continue
      add(
        NbgSkillCuratorSkillStats(
          skillName = name,
          useCount = item.optInt("useCount", item.optInt("usageCount", 0)),
          lastUsedAtMs = item.optLong("lastUsedAtMs", item.optLong("usedAtMs", 0L)),
          archived = item.optBoolean("archived", item.optBoolean("isArchived", false)),
          archivedAtMs = item.optLong("archivedAtMs", 0L),
          archiveReason = item.cleanStringAny("archiveReason", "reason").orEmpty(),
        ),
      )
    }
  }
}

private fun NbgSkillCuratorSkillStats.toJson(): JSONObject =
  JSONObject()
    .put("skillName", skillName)
    .put("useCount", useCount)
    .put("lastUsedAtMs", lastUsedAtMs)
    .put("archived", archived)
    .put("archivedAtMs", archivedAtMs)
    .put("archiveReason", archiveReason)

private fun String.nbgSkillCuratorCompact(limit: Int = 140): String {
  val cleaned = replace(Regex("\\s+"), " ").trim()
  return if (cleaned.length <= limit) cleaned else cleaned.take(limit).trimEnd() + "..."
}

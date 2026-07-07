package com.nbg.android

import java.io.File

data class NbgLearningJourneyNodeDetail(
  val ok: Boolean,
  val id: String,
  val kind: String,
  val label: String,
  val content: String,
  val message: String = "",
)

internal class NbgLearningJourneyMutations(
  private val memoryStore: NbgLocalLearningMemoryStore,
  private val profileStore: NbgUserProfileStore,
  private val soulStore: NbgAgentSoulStore,
  private val draftStore: NbgLearnedSkillDraftStore,
  private val skillArtifactRoot: File,
) {
  constructor(context: android.content.Context) : this(
    memoryStore = NbgLocalLearningMemoryStore(context),
    profileStore = NbgUserProfileStore(context),
    soulStore = NbgAgentSoulStore(context),
    draftStore = NbgLearnedSkillDraftStore(context),
    skillArtifactRoot = File(context.applicationContext.filesDir, "learned-skills"),
  )

  fun nodeDetail(nodeId: String): NbgLearningJourneyNodeDetail {
    val id = nodeId.trim()
    if (id.isBlank()) return detailError(nodeId, "empty node id")
    return when {
      id.startsWith("memory:") -> memoryDetail(id.removePrefix("memory:"), id)
      id.startsWith("profile:") -> profileDetail(id.removePrefix("profile:"), id)
      id.startsWith("soul:") -> soulDetail(id.removePrefix("soul:"), id)
      id.startsWith("skill:") -> skillDetail(id.removePrefix("skill:"), id)
      id.startsWith("skill-installed:") -> installedSkillDetail(id.removePrefix("skill-installed:"), id)
      else -> detailError(id, "unsupported journey node")
    }
  }

  fun editNode(nodeId: String, content: String): NbgLearningJourneyNodeDetail {
    val detail = nodeDetail(nodeId)
    if (!detail.ok) return detail
    val body = content.nbgJourneyContent()
    if (body.isBlank()) return detail.copy(ok = false, message = "empty content")
    when (detail.kind) {
      "memory" -> {
        val key = detail.id.removePrefix("memory:")
        val current = memoryStore.load().entries.firstOrNull { it.id == key } ?: return detail.copy(ok = false, message = "memory not found")
        memoryStore.upsert(current.copy(content = body, title = body.lineSequence().firstOrNull().orEmpty().take(80), updatedAtMs = System.currentTimeMillis()))
      }
      "profile" -> {
        val key = detail.id.removePrefix("profile:")
        profileStore.upsert(NbgUserProfileEntry(key = key, value = body, sourceEventIds = listOf(detail.id), updatedAtMs = System.currentTimeMillis()))
      }
      "soul" -> {
        val index = detail.id.removePrefix("soul:").toIntOrNull() ?: return detail.copy(ok = false, message = "bad soul index")
        val current = soulStore.load()
        if (index !in current.principles.indices) return detail.copy(ok = false, message = "soul node not found")
        soulStore.save(current.copy(principles = current.principles.mapIndexed { i, value -> if (i == index) body else value }, updatedAtMs = System.currentTimeMillis()))
      }
      "skill" -> {
        val key = detail.id.removePrefix("skill:")
        val current = draftStore.load().entries.firstOrNull { it.id == key } ?: return detail.copy(ok = false, message = "skill draft not found")
        draftStore.save(NbgLearnedSkillDraftQueue(draftStore.load().entries.filterNot { it.id == key } + current.copy(description = body, updatedAtMs = System.currentTimeMillis())))
      }
      "skill-installed" -> {
        val name = detail.id.removePrefix("skill-installed:")
        val skillFile = skillArtifactRoot.resolve(name.nbgJourneySafeSkillDir()).resolve("SKILL.md")
        if (!skillFile.isFile) return detail.copy(ok = false, message = "installed skill not found")
        skillFile.writeText(body, Charsets.UTF_8)
      }
      else -> return detail.copy(ok = false, message = "unsupported journey node")
    }
    return nodeDetail(nodeId).copy(message = "updated")
  }

  fun deleteNode(nodeId: String): NbgLearningJourneyNodeDetail {
    val detail = nodeDetail(nodeId)
    if (!detail.ok) return detail
    when (detail.kind) {
      "memory" -> memoryStore.delete(detail.id.removePrefix("memory:"))
      "profile" -> profileStore.delete(detail.id.removePrefix("profile:"))
      "soul" -> {
        val index = detail.id.removePrefix("soul:").toIntOrNull() ?: return detail.copy(ok = false, message = "bad soul index")
        val current = soulStore.load()
        if (index !in current.principles.indices) return detail.copy(ok = false, message = "soul node not found")
        soulStore.save(current.copy(principles = current.principles.filterIndexed { i, _ -> i != index }, updatedAtMs = System.currentTimeMillis()))
      }
      "skill" -> draftStore.reject(detail.id.removePrefix("skill:"))
      "skill-installed" -> {
        val name = detail.id.removePrefix("skill-installed:")
        val dir = skillArtifactRoot.resolve(name.nbgJourneySafeSkillDir())
        if (!dir.isDirectory) return detail.copy(ok = false, message = "installed skill not found")
        dir.deleteRecursively()
      }
      else -> return detail.copy(ok = false, message = "unsupported journey node")
    }
    return detail.copy(message = "deleted")
  }

  private fun memoryDetail(key: String, nodeId: String): NbgLearningJourneyNodeDetail =
    memoryStore.load().entries.firstOrNull { it.id == key }?.let {
      NbgLearningJourneyNodeDetail(true, nodeId, "memory", it.title.ifBlank { "Memory" }, it.content)
    } ?: detailError(nodeId, "memory not found")

  private fun profileDetail(key: String, nodeId: String): NbgLearningJourneyNodeDetail =
    profileStore.load().visibleEntries.firstOrNull { it.key == key }?.let {
      NbgLearningJourneyNodeDetail(true, nodeId, "profile", it.key, it.value)
    } ?: detailError(nodeId, "profile not found")

  private fun soulDetail(rawIndex: String, nodeId: String): NbgLearningJourneyNodeDetail {
    val index = rawIndex.toIntOrNull() ?: return detailError(nodeId, "bad soul index")
    val principle = soulStore.load().principles.getOrNull(index) ?: return detailError(nodeId, "soul node not found")
    return NbgLearningJourneyNodeDetail(true, nodeId, "soul", "soul", principle)
  }

  private fun skillDetail(key: String, nodeId: String): NbgLearningJourneyNodeDetail =
    draftStore.load().visibleEntries.firstOrNull { it.id == key }?.let {
      NbgLearningJourneyNodeDetail(true, nodeId, "skill", it.skillName, it.description.ifBlank { it.review.reason })
    } ?: detailError(nodeId, "skill draft not found")

  private fun installedSkillDetail(name: String, nodeId: String): NbgLearningJourneyNodeDetail {
    val skillFile = skillArtifactRoot.resolve(name.nbgJourneySafeSkillDir()).resolve("SKILL.md")
    if (!skillFile.isFile) return detailError(nodeId, "installed skill not found")
    return NbgLearningJourneyNodeDetail(true, nodeId, "skill-installed", name, skillFile.readText(Charsets.UTF_8))
  }

  private fun detailError(nodeId: String, message: String): NbgLearningJourneyNodeDetail =
    NbgLearningJourneyNodeDetail(false, nodeId, "", "", "", message)
}

private fun String.nbgJourneyContent(): String =
  nbgRedactDiagnosticText(this)
    .replace("\r\n", "\n")
    .trim()
    .take(NBG_LEARNING_MAX_CONTENT_CHARS)

internal fun String.nbgJourneySafeSkillDir(): String =
  lowercase()
    .replace(Regex("[^a-z0-9_.-]+"), "-")
    .trim('-', '.', '_')
    .take(80)
    .ifBlank { "learned-skill" }

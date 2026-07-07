package com.nbg.android

import java.io.File

data class NbgInstalledSkillNode(
  val name: String,
  val category: String = "learned",
  val description: String = "",
  val relatedSkills: List<String> = emptyList(),
  val filePathLabel: String = "[local-path]",
)

internal fun nbgReadInstalledSkillNodes(skillRoot: File): List<NbgInstalledSkillNode> {
  if (!skillRoot.isDirectory) return emptyList()
  return skillRoot.listFiles()
    .orEmpty()
    .filter { it.isDirectory && !it.name.startsWith(".") }
    .mapNotNull { dir ->
      val skillFile = File(dir, "SKILL.md")
      if (!skillFile.isFile) return@mapNotNull null
      nbgParseInstalledSkillMarkdown(skillFile.readText(Charsets.UTF_8).take(20_000), fallbackName = dir.name)
    }
    .distinctBy { it.name }
    .sortedBy { it.name.lowercase() }
}

internal fun nbgParseInstalledSkillMarkdown(text: String, fallbackName: String): NbgInstalledSkillNode {
  val frontmatter = if (text.startsWith("---")) text.substringAfter("---").substringBefore("---") else text.lineSequence().take(40).joinToString("\n")
  val name = frontmatter.nbgFrontmatterScalar("name").ifBlank { fallbackName }
  val category = frontmatter.nbgFrontmatterScalar("category")
    .ifBlank { frontmatter.nbgFrontmatterScalar("type") }
    .ifBlank { "learned" }
  val description = frontmatter.nbgFrontmatterScalar("description").ifBlank {
    text.lineSequence().firstOrNull { it.startsWith("#") }.orEmpty().trim('#', ' ')
  }
  val related = frontmatter.nbgFrontmatterList("related_skills") +
    frontmatter.nbgFrontmatterList("relatedSkills")
  return NbgInstalledSkillNode(
    name = name.nbgInstalledSkillName(),
    category = category.nbgInstalledSkillText(limit = 80).ifBlank { "learned" },
    description = description.nbgInstalledSkillText(limit = 240),
    relatedSkills = related.map { it.nbgInstalledSkillName() }.filter { it.isNotBlank() }.distinct().take(24),
  )
}

internal fun nbgBuildLearningGraph(
  memory: NbgLocalLearningMemory,
  profile: NbgUserProfile,
  soul: NbgAgentSoulConfig,
  skillDrafts: NbgLearnedSkillDraftQueue,
  installedSkills: List<NbgInstalledSkillNode>,
): NbgLearningGraph {
  val base = nbgBuildLearningGraph(memory, profile, soul, skillDrafts)
  val installedNodes = installedSkills.map {
    NbgLearningGraphNode(
      id = "skill-installed:${it.name}",
      kind = "skill",
      title = it.name,
      subtitle = it.description.ifBlank { it.category },
      state = "installed",
      relatedIds = it.relatedSkills.map { related -> "skill-installed:$related" },
    )
  }
  val nodes = base.nodes + installedNodes
  val relatedEdges = installedSkills.flatMap { skill ->
    skill.relatedSkills.map { related ->
      NbgLearningGraphEdge("skill-installed:${skill.name}", "skill-installed:$related", "related_skills")
    }
  }.filter { edge ->
    nodes.any { it.id == edge.fromId } && nodes.any { it.id == edge.toId } && edge.fromId != edge.toId
  }
  val edges = (base.edges + relatedEdges + nbgInstalledMemorySkillEdges(base.nodes, installedNodes)).distinctBy { listOf(it.fromId, it.toId).sorted().joinToString("|") + "|${it.reason}" }.take(160)
  return NbgLearningGraph(nodes = nodes, edges = edges, stats = nbgLearningGraphStats(nodes, edges))
}

private fun nbgInstalledMemorySkillEdges(
  existingNodes: List<NbgLearningGraphNode>,
  installedNodes: List<NbgLearningGraphNode>,
): List<NbgLearningGraphEdge> {
  val memoryNodes = existingNodes.filter { it.kind in setOf("memory", "profile") }
  return memoryNodes.flatMap { memory ->
    val memTokens = nbgInstalledGraphTokens(memory.title + " " + memory.subtitle)
    installedNodes.mapNotNull { skill ->
      val overlap = memTokens.intersect(nbgInstalledGraphTokens(skill.title + " " + skill.subtitle))
      if (overlap.isNotEmpty()) NbgLearningGraphEdge(memory.id, skill.id, "overlap:${overlap.take(3).joinToString(",")}") else null
    }
  }
}

private fun String.nbgFrontmatterScalar(key: String): String {
  val regex = Regex("^\\s*${Regex.escape(key)}\\s*:\\s*(.+?)\\s*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
  return regex.find(this)?.groupValues?.getOrNull(1)
    ?.trim()
    ?.trim('"', '\'')
    .orEmpty()
    .takeIf { !it.startsWith("[") }
    .orEmpty()
}

private fun String.nbgFrontmatterList(key: String): List<String> {
  val inline = Regex("^\\s*${Regex.escape(key)}\\s*:\\s*\\[(.*?)\\]\\s*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    .find(this)
    ?.groupValues
    ?.getOrNull(1)
    ?.split(",")
    ?.map { it.trim().trim('"', '\'') }
    .orEmpty()
  val block = Regex("^\\s*${Regex.escape(key)}\\s*:\\s*\\n((?:\\s*-\\s*.+\\n?)+)", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    .find(this)
    ?.groupValues
    ?.getOrNull(1)
    ?.lineSequence()
    ?.map { it.trim().removePrefix("-").trim().trim('"', '\'') }
    ?.toList()
    .orEmpty()
  return (inline + block).filter { it.isNotBlank() }.distinct()
}

private fun String.nbgInstalledSkillName(): String =
  nbgInstalledSkillText(limit = 120)
    .replace(Regex("\\s+"), "-")
    .trim('-', '.', '_')

private fun String.nbgInstalledSkillText(limit: Int): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit.coerceAtLeast(0))

private fun nbgInstalledGraphTokens(text: String): Set<String> =
  text.lowercase()
    .split(Regex("[^a-z0-9\\u4e00-\\u9fff]+"))
    .map { it.trim() }
    .filter { it.length >= 2 }
    .toSet()

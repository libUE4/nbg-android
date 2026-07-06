package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject

data class HanakoSkillSummary(
  val name: String,
  val description: String = "",
  val source: String = "user",
  val enabled: Boolean = false,
  val hidden: Boolean = false,
  val readonly: Boolean = false,
  val filePath: String = "",
  val baseDir: String = "",
  val externalLabel: String = "",
  val externalPath: String = "",
) {
  val displayDescription: String
    get() = cleanHanakoSkillDescription(description)

  fun displayDescription(preferChinese: Boolean): String {
    val cleaned = displayDescription
    if (!preferChinese) return cleaned
    return cleaned.toChineseSkillDescription().ifBlank { cleaned }
  }

  val deletable: Boolean
    get() = !readonly && source != "builtin" && source != "external"
}

data class HanakoSkillsSnapshot(
  val skills: List<HanakoSkillSummary> = emptyList(),
  val bundles: List<HanakoSkillBundle> = emptyList(),
  val externalPaths: HanakoExternalSkillPaths = HanakoExternalSkillPaths(),
) {
  val visibleSkills: List<HanakoSkillSummary>
    get() = skills.filterNot { it.hidden }

  val enabledCount: Int
    get() = visibleSkills.count { it.enabled }
}

data class HanakoSkillBundleSkill(
  val name: String,
  val enabled: Boolean = false,
  val source: String = "",
  val missing: Boolean = false,
)

data class HanakoSkillBundle(
  val id: String,
  val name: String,
  val skillNames: List<String> = emptyList(),
  val skills: List<HanakoSkillBundleSkill> = emptyList(),
) {
  val effectiveSkillNames: List<String>
    get() = skillNames.ifEmpty { skills.map { it.name } }

  val enabledCount: Int
    get() = skills.count { it.enabled }

  val missingCount: Int
    get() = skills.count { it.missing }
}

data class HanakoSkillBundleInput(
  val name: String,
  val skillNames: List<String> = emptyList(),
)

data class HanakoExternalSkillPath(
  val path: String,
  val label: String = "",
  val exists: Boolean = true,
)

data class HanakoExternalSkillPaths(
  val configured: List<String> = emptyList(),
  val discovered: List<HanakoExternalSkillPath> = emptyList(),
) {
  val visibleCount: Int
    get() = configured.size + discovered.size
}

data class HanakoExternalSkillPathsInput(
  val paths: List<String>,
)

data class HanakoSkillInstallInput(
  val path: String,
)

internal fun parseHanakoSkillsSnapshot(root: JSONObject): HanakoSkillsSnapshot =
  HanakoSkillsSnapshot(skills = root.optJSONArray("skills").toHanakoSkills())

internal fun parseHanakoSkillBundles(root: JSONObject): List<HanakoSkillBundle> =
  root.optJSONArray("bundles").toHanakoSkillBundles()

internal fun parseHanakoExternalSkillPaths(value: Any): HanakoExternalSkillPaths =
  when (value) {
    is JSONArray -> HanakoExternalSkillPaths(configured = value.toCleanStringList())
    is JSONObject -> HanakoExternalSkillPaths(
      configured = value.optJSONArray("configured").toCleanStringList(),
      discovered = value.optJSONArray("discovered").toExternalSkillPaths(),
    )
    else -> HanakoExternalSkillPaths()
  }

internal fun HanakoSkillInstallInput.toJson(): JSONObject =
  JSONObject().put("path", path.trim())

internal fun HanakoSkillBundleInput.toJson(): JSONObject =
  JSONObject()
    .put("name", name.trim())
    .put("skillNames", JSONArray(skillNames.map { it.trim() }.filter { it.isNotBlank() }.distinct()))

internal fun HanakoExternalSkillPathsInput.toJson(): JSONObject =
  JSONObject().put("paths", JSONArray(paths.map { it.trim() }.filter { it.isNotBlank() }.distinct()))

private fun JSONArray?.toHanakoSkills(): List<HanakoSkillSummary> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val name = item.cleanString("name") ?: continue
      add(
        HanakoSkillSummary(
          name = name,
          description = item.cleanString("description").orEmpty(),
          source = item.cleanString("source") ?: "user",
          enabled = item.optBoolean("enabled", false),
          hidden = item.optBoolean("hidden", false),
          readonly = item.optBoolean("readonly", false),
          filePath = item.cleanString("filePath").orEmpty(),
          baseDir = item.cleanString("baseDir").orEmpty(),
          externalLabel = item.cleanString("externalLabel").orEmpty(),
          externalPath = item.cleanString("externalPath").orEmpty(),
        ),
      )
    }
  }.sortedWith(
    compareByDescending<HanakoSkillSummary> { it.enabled }
      .thenBy { it.source == "external" }
      .thenBy { it.name.lowercase() },
  )
}

private fun JSONArray?.toHanakoSkillBundles(): List<HanakoSkillBundle> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val id = item.cleanString("id") ?: continue
      val skills = item.optJSONArray("skills").toHanakoBundleSkills()
      add(
        HanakoSkillBundle(
          id = id,
          name = item.cleanString("name") ?: id,
          skillNames = item.optJSONArray("skillNames").toCleanStringList().ifEmpty { skills.map { it.name } },
          skills = skills,
        ),
      )
    }
  }
}

private fun JSONArray?.toHanakoBundleSkills(): List<HanakoSkillBundleSkill> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val name = item.cleanString("name") ?: continue
      add(
        HanakoSkillBundleSkill(
          name = name,
          enabled = item.optBoolean("enabled", false),
          source = item.cleanString("source").orEmpty(),
          missing = item.optBoolean("missing", false),
        ),
      )
    }
  }
}

private fun JSONArray?.toExternalSkillPaths(): List<HanakoExternalSkillPath> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index)
      if (item != null) {
        val path = item.cleanString("dirPath") ?: item.cleanString("path") ?: continue
        add(
          HanakoExternalSkillPath(
            path = path,
            label = item.cleanString("label").orEmpty(),
            exists = item.optBoolean("exists", true),
          ),
        )
      } else {
        val path = array.optString(index).trim()
        if (path.isNotBlank() && path != "null" && path != "undefined") {
          add(HanakoExternalSkillPath(path = path))
        }
      }
    }
  }
}

private fun JSONArray?.toCleanStringList(): List<String> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val value = array.optString(index).trim()
      if (value.isNotBlank() && value != "null" && value != "undefined") add(value)
    }
  }
}

private fun cleanHanakoSkillDescription(raw: String): String =
  raw
    .replace(Regex("\\s*MANDATORY TRIGGERS:.*", RegexOption.IGNORE_CASE), "")
    .trim()

private fun String.toChineseSkillDescription(): String {
  val matches = Regex("[\\u4e00-\\u9fff][^。！？\\n]*(?:[。！？]|$)").findAll(this)
    .map { it.value.trim() }
    .filter { it.isNotBlank() }
    .toList()
  if (matches.isNotEmpty()) return matches.joinToString("")
  val firstChinese = Regex("[\\u4e00-\\u9fff]").find(this)?.range?.first ?: return ""
  return substring(firstChinese).trim()
}

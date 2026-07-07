package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject

data class NbgSkillCuratorLlmSuggestion(
  val skillName: String,
  val action: String,
  val reason: String,
  val patchHint: String = "",
  val filePath: String = "",
  val oldString: String = "",
  val newString: String = "",
  val proposedContent: String = "",
) {
  val hasPatchDraft: Boolean
    get() = (oldString.isNotBlank() && newString.isNotBlank()) || proposedContent.isNotBlank()
}

data class NbgSkillCuratorLlmReviewResult(
  val ok: Boolean,
  val message: String,
  val suggestions: List<NbgSkillCuratorLlmSuggestion> = emptyList(),
  val rawText: String = "",
)

internal class NbgSkillCuratorLlmReviewRunner(
  private val apiClient: NbgUpstreamApiClient = NbgUpstreamApiClient(),
) {
  suspend fun run(
    entry: NbgStoredApi,
    model: NbgApiModel,
    snapshot: HanakoSkillsSnapshot,
    metadata: NbgSkillCuratorMetadata,
    drafts: NbgLearnedSkillDraftQueue,
    providerProfiles: List<NbgModelProviderProfile> = emptyList(),
  ): NbgSkillCuratorLlmReviewResult {
    val prompt = nbgBuildSkillCuratorLlmPrompt(snapshot, metadata, drafts)
    if (prompt.isBlank()) return NbgSkillCuratorLlmReviewResult(false, "没有可复核的 Skill")
    val result = apiClient.generateReadOnlyText(
      entry = entry,
      model = model,
      prompt = prompt,
      systemPrompt = "你是只读 Skill curator。只输出 JSON，不要调用工具，不要写文件。建议 action 只能是 keep, merge, rewrite, archive。",
      providerProfiles = providerProfiles,
    )
    if (!result.ok) return NbgSkillCuratorLlmReviewResult(false, result.message)
    val suggestions = nbgParseSkillCuratorLlmSuggestions(result.text)
    return NbgSkillCuratorLlmReviewResult(
      ok = true,
      message = "LLM curator produced ${suggestions.size} suggestions",
      suggestions = suggestions,
      rawText = result.text,
    )
  }
}

internal fun nbgBuildSkillCuratorLlmPrompt(
  snapshot: HanakoSkillsSnapshot,
  metadata: NbgSkillCuratorMetadata,
  drafts: NbgLearnedSkillDraftQueue,
): String {
  val skills = snapshot.visibleSkills.take(80)
  if (skills.isEmpty() && drafts.visibleEntries.isEmpty()) return ""
  val payload = JSONObject()
    .put(
      "skills",
      JSONArray(skills.map { skill ->
        val stats = metadata.statsFor(skill.name)
        JSONObject()
          .put("name", skill.name)
          .put("description", skill.displayDescription.take(500))
          .put("source", skill.source)
          .put("enabled", skill.enabled)
          .put("readonly", skill.readonly)
          .put("useCount", stats?.useCount ?: 0)
          .put("archived", stats?.archived ?: false)
      }),
    )
    .put(
      "drafts",
      JSONArray(drafts.visibleEntries.take(80).map { draft ->
        JSONObject()
          .put("skillName", draft.skillName)
          .put("status", draft.status.wireName)
          .put("risk", draft.review.permissionTier.wireName)
          .put("description", draft.description.take(500))
          .put("autoInstalled", draft.autoInstalled)
      }),
    )
  return """
    Review these Android learned Skills.
    Return JSON:
    {"suggestions":[{"skillName":"...","action":"keep|merge|rewrite|archive","reason":"...","patchHint":"optional","filePath":"SKILL.md","oldString":"optional exact text","newString":"optional replacement","proposedContent":"optional full file draft"}]}
    Rules:
    - archive only unused, disabled, redundant learned Skills.
    - merge only when two skills clearly overlap.
    - for merge/rewrite, include either oldString/newString or proposedContent only when you can make a small reviewable patch draft.
    - proposedContent must target a single file, preferably SKILL.md or references/*.md, and should stay under 4000 characters.

    $payload
  """.trimIndent()
}

internal fun nbgParseSkillCuratorLlmSuggestions(raw: String): List<NbgSkillCuratorLlmSuggestion> =
  runCatching {
    val jsonText = raw.substringAfter('{', raw).substringBeforeLast('}', raw).let {
      if (it == raw) raw else "{$it}"
    }
    val root = JSONObject(jsonText)
    val array = root.optJSONArray("suggestions") ?: JSONArray()
    buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val skillName = item.cleanString("skillName") ?: item.cleanString("name") ?: continue
        val action = item.cleanString("action").orEmpty().lowercase()
        if (action !in setOf("keep", "merge", "rewrite", "archive")) continue
        val patch = item.optJSONObject("patchDraft") ?: item.optJSONObject("patch")
        add(
          NbgSkillCuratorLlmSuggestion(
            skillName = skillName.take(160),
            action = action,
            reason = item.cleanString("reason").orEmpty().take(500),
            patchHint = item.cleanString("patchHint").orEmpty().take(1_000),
            filePath = (item.cleanString("filePath") ?: patch?.cleanString("filePath")).orEmpty().take(240),
            oldString = (item.cleanString("oldString") ?: patch?.cleanString("oldString")).orEmpty().take(4_000),
            newString = (item.cleanString("newString") ?: patch?.cleanString("newString")).orEmpty().take(4_000),
            proposedContent = (item.cleanString("proposedContent") ?: patch?.cleanString("proposedContent")).orEmpty().take(8_000),
          ),
        )
      }
    }
  }.getOrDefault(emptyList())

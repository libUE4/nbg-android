package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_SCHEDULE_STORE_VERSION = "nbg-schedule-store-v1"
internal const val NBG_SCHEDULE_POLICY_VERSION = "nbg-schedule-policy-v1"
internal const val NBG_SCHEDULE_MAX_PROMPT_CHARS = 2_000

enum class NbgScheduledAutomationTemplate(
  val wireName: String,
  val label: String,
  val defaultCadence: String,
  val action: String,
  val permissionTier: NbgPermissionRiskTier,
) {
  DailyProjectSummary("daily_project_summary", "每日项目摘要", "daily", "read_project_summary", NbgPermissionRiskTier.Low),
  WeeklyLearningAudit("weekly_learning_audit", "每周学习审计", "weekly", "read_learning_audit", NbgPermissionRiskTier.Low),
  TestCommand("test_command", "定时运行测试", "manual", "terminal_test_command", NbgPermissionRiskTier.Medium),
  GitStatus("git_status", "检查仓库状态", "daily", "read_git_status", NbgPermissionRiskTier.Low),
  LearningReport("learning_report", "生成学习报告", "weekly", "read_learning_report", NbgPermissionRiskTier.Low),
}

enum class NbgScheduleRunStatus(val wireName: String, val label: String) {
  Queued("queued", "排队"),
  Running("running", "运行中"),
  Succeeded("succeeded", "成功"),
  Failed("failed", "失败"),
  Blocked("blocked", "已阻止"),
  Cancelled("cancelled", "已取消"),
}

data class NbgScheduledAutomation(
  val id: String,
  val title: String,
  val template: NbgScheduledAutomationTemplate,
  val cadence: String,
  val prompt: String,
  val enabled: Boolean = false,
  val permissionTier: NbgPermissionRiskTier = template.permissionTier,
  val requiresConfirmation: Boolean = permissionTier.requiresConfirmation,
  val createdAtMs: Long = 0L,
  val updatedAtMs: Long = 0L,
  val lastRunAtMs: Long = 0L,
  val nextRunAtMs: Long = 0L,
) {
  val active: Boolean
    get() = enabled && nextRunAtMs > 0L
}

data class NbgSchedulePolicyReview(
  val policyVersion: String,
  val allowCreate: Boolean,
  val allowAutoEnable: Boolean,
  val requiresConfirmation: Boolean,
  val template: NbgScheduledAutomationTemplate?,
  val permissionTier: NbgPermissionRiskTier,
  val reason: String,
)

data class NbgScheduleRunEvent(
  val id: String,
  val automationId: String,
  val status: NbgScheduleRunStatus,
  val summary: String = "",
  val evidenceRef: String = "",
  val startedAtMs: Long = 0L,
  val finishedAtMs: Long = 0L,
)

data class NbgScheduleState(
  val automations: List<NbgScheduledAutomation> = emptyList(),
  val runEvents: List<NbgScheduleRunEvent> = emptyList(),
  val modelVersion: String = NBG_SCHEDULE_STORE_VERSION,
) {
  val enabledCount: Int
    get() = automations.count { it.enabled }

  val pendingConfirmationCount: Int
    get() = automations.count { it.requiresConfirmation && !it.enabled }

  val visibleRunEvents: List<NbgScheduleRunEvent>
    get() = runEvents.sortedByDescending { it.startedAtMs }.take(50)
}

internal interface NbgScheduleStorage {
  fun read(): String?
  fun write(raw: String)
}

internal class NbgSharedPreferencesScheduleStorage(context: Context) : NbgScheduleStorage {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  override fun read(): String? =
    prefs.getString(KEY_STATE, null)

  override fun write(raw: String) {
    prefs.edit().putString(KEY_STATE, raw).apply()
  }

  private companion object {
    const val PREFS = "nbg_scheduled_automations"
    const val KEY_STATE = "state"
  }
}

internal class NbgScheduleStore(
  private val storage: NbgScheduleStorage,
) {
  constructor(context: Context) : this(NbgSharedPreferencesScheduleStorage(context))

  fun load(): NbgScheduleState =
    parseNbgScheduleState(storage.read())

  fun save(state: NbgScheduleState): NbgScheduleState {
    val normalized = nbgBuildScheduleState(state.automations, state.runEvents)
    storage.write(normalized.toJsonString())
    return normalized
  }

  fun upsert(automation: NbgScheduledAutomation): NbgScheduleState =
    save(load().copy(automations = load().automations + automation))

  fun setEnabled(id: String, enabled: Boolean, nowMs: Long = System.currentTimeMillis()): NbgScheduleState {
    val cleanId = id.trim()
    if (cleanId.isBlank()) return load()
    return save(
      load().copy(
        automations = load().automations.map {
          if (it.id == cleanId) {
            val review = nbgReviewScheduledAutomation(it)
            it.copy(
              enabled = enabled && review.allowCreate && (!review.requiresConfirmation || !review.permissionTier.strongConfirmation),
              updatedAtMs = nowMs.coerceAtLeast(it.updatedAtMs),
              nextRunAtMs = if (enabled) nbgNextScheduleRunAt(it.cadence, nowMs) else 0L,
            )
          } else {
            it
          }
        },
      ),
    )
  }

  fun recordRun(event: NbgScheduleRunEvent): NbgScheduleState =
    save(load().copy(runEvents = load().runEvents + event))
}

internal fun nbgReviewScheduledAutomation(
  automation: NbgScheduledAutomation,
): NbgSchedulePolicyReview {
  val cleanPrompt = automation.prompt.trim()
  val inferred = nbgPermissionRiskForAction(
    action = automation.template.action,
    target = automation.title,
    command = cleanPrompt,
  )
  val tier = nbgHighestPermissionRiskTier(automation.permissionTier, inferred.tier)
  if (automation.title.isBlank() || cleanPrompt.isBlank()) {
    return nbgSchedulePolicyBlocked("定时任务需要标题和任务内容。", automation.template, tier)
  }
  if (cleanPrompt.length > NBG_SCHEDULE_MAX_PROMPT_CHARS) {
    return nbgSchedulePolicyBlocked("定时任务内容过长。", automation.template, tier)
  }
  val sensitive = nbgMemorySensitiveFindings(cleanPrompt)
  if (sensitive.isNotEmpty()) {
    return nbgSchedulePolicyBlocked("定时任务疑似包含敏感信息：${sensitive.joinToString(", ")}。", automation.template, tier)
  }
  val allowAutoEnable = tier == NbgPermissionRiskTier.Low
  return NbgSchedulePolicyReview(
    policyVersion = NBG_SCHEDULE_POLICY_VERSION,
    allowCreate = true,
    allowAutoEnable = allowAutoEnable,
    requiresConfirmation = !allowAutoEnable,
    template = automation.template,
    permissionTier = tier,
    reason = if (allowAutoEnable) {
      "低风险只读定时任务可自动启用。"
    } else {
      "含命令、写入或工具副作用的定时任务需要确认后启用。"
    },
  )
}

internal fun nbgScheduledAutomation(
  title: String,
  template: NbgScheduledAutomationTemplate,
  prompt: String,
  cadence: String = template.defaultCadence,
  id: String = "",
  nowMs: Long = System.currentTimeMillis(),
): NbgScheduledAutomation {
  val base = NbgScheduledAutomation(
    id = id.trim().ifBlank { listOf(template.wireName, title, prompt).joinToString("\u001f").sha256Hex().take(24) },
    title = title.nbgScheduleCompact(160),
    template = template,
    cadence = nbgNormalizeScheduleCadence(cadence),
    prompt = prompt.nbgScheduleCompact(NBG_SCHEDULE_MAX_PROMPT_CHARS),
    createdAtMs = nowMs.coerceAtLeast(0L),
    updatedAtMs = nowMs.coerceAtLeast(0L),
  )
  val review = nbgReviewScheduledAutomation(base)
  return base.copy(
    enabled = review.allowAutoEnable,
    permissionTier = review.permissionTier,
    requiresConfirmation = review.requiresConfirmation,
    nextRunAtMs = if (review.allowAutoEnable) nbgNextScheduleRunAt(base.cadence, nowMs) else 0L,
  )
}

internal fun nbgDefaultSchedulePrompt(template: NbgScheduledAutomationTemplate): String =
  when (template) {
    NbgScheduledAutomationTemplate.DailyProjectSummary ->
      "生成当前项目的本地摘要，只读取会话、Memory 和任务证据，不执行命令。"
    NbgScheduledAutomationTemplate.WeeklyLearningAudit ->
      "汇总本周学习事件、阻止原因、待审核 Skill 草稿和可撤回项目。"
    NbgScheduledAutomationTemplate.TestCommand ->
      "运行用户确认过的测试命令并记录结果。"
    NbgScheduledAutomationTemplate.GitStatus ->
      "读取仓库状态并报告未提交文件数量，不修改文件。"
    NbgScheduledAutomationTemplate.LearningReport ->
      "生成学习图谱报告，包含 Memory、User Profile、Soul 和 Skill 草稿统计。"
  }

internal fun nbgBuildScheduleState(
  automations: List<NbgScheduledAutomation>,
  runEvents: List<NbgScheduleRunEvent>,
): NbgScheduleState {
  val normalizedAutomations = automations
    .filter { it.id.isNotBlank() && it.title.isNotBlank() }
    .map { automation ->
      val review = nbgReviewScheduledAutomation(automation)
      automation.copy(
        title = automation.title.nbgScheduleCompact(160),
        cadence = nbgNormalizeScheduleCadence(automation.cadence),
        prompt = automation.prompt.nbgScheduleCompact(NBG_SCHEDULE_MAX_PROMPT_CHARS),
        permissionTier = review.permissionTier,
        requiresConfirmation = review.requiresConfirmation,
        enabled = automation.enabled && review.allowCreate && (!review.requiresConfirmation || review.allowAutoEnable),
        createdAtMs = automation.createdAtMs.coerceAtLeast(0L),
        updatedAtMs = automation.updatedAtMs.coerceAtLeast(0L),
        lastRunAtMs = automation.lastRunAtMs.coerceAtLeast(0L),
        nextRunAtMs = automation.nextRunAtMs.coerceAtLeast(0L),
      )
    }
    .groupBy { it.id }
    .mapNotNull { (_, grouped) -> grouped.maxWithOrNull(compareBy<NbgScheduledAutomation> { it.updatedAtMs }.thenBy { it.createdAtMs }) }
    .sortedWith(compareByDescending<NbgScheduledAutomation> { it.enabled }.thenBy { it.title.lowercase() })
    .take(100)
  val normalizedRuns = runEvents
    .filter { it.id.isNotBlank() && it.automationId.isNotBlank() }
    .map {
      it.copy(
        summary = it.summary.nbgScheduleCompact(240),
        evidenceRef = it.evidenceRef.nbgScheduleCompact(160),
        startedAtMs = it.startedAtMs.coerceAtLeast(0L),
        finishedAtMs = it.finishedAtMs.coerceAtLeast(0L),
      )
    }
    .groupBy { it.id }
    .mapNotNull { (_, grouped) -> grouped.maxWithOrNull(compareBy<NbgScheduleRunEvent> { it.finishedAtMs }.thenBy { it.startedAtMs }) }
    .sortedByDescending { it.startedAtMs }
    .take(200)
  return NbgScheduleState(normalizedAutomations, normalizedRuns)
}

internal fun parseNbgScheduleState(raw: String?): NbgScheduleState =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    nbgBuildScheduleState(
      automations = root.optJSONArray("automations").toScheduleAutomations(),
      runEvents = root.optJSONArray("runEvents").toScheduleRunEvents(),
    )
  }.getOrDefault(NbgScheduleState())

internal fun NbgScheduleState.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("automations", JSONArray(automations.map { it.toJson() }))
    .put("runEvents", JSONArray(runEvents.map { it.toJson() }))
    .toString()

internal fun nbgNextScheduleRunAt(cadence: String, nowMs: Long): Long =
  nowMs.coerceAtLeast(0L) + when (nbgNormalizeScheduleCadence(cadence)) {
    "hourly" -> 60L * 60L * 1000L
    "daily" -> 24L * 60L * 60L * 1000L
    "weekly" -> 7L * 24L * 60L * 60L * 1000L
    else -> 0L
  }

private fun nbgSchedulePolicyBlocked(
  reason: String,
  template: NbgScheduledAutomationTemplate?,
  tier: NbgPermissionRiskTier,
): NbgSchedulePolicyReview =
  NbgSchedulePolicyReview(
    policyVersion = NBG_SCHEDULE_POLICY_VERSION,
    allowCreate = false,
    allowAutoEnable = false,
    requiresConfirmation = true,
    template = template,
    permissionTier = tier,
    reason = reason,
  )

private fun NbgScheduledAutomation.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("title", title)
    .put("template", template.wireName)
    .put("cadence", cadence)
    .put("prompt", nbgRedactDiagnosticText(prompt))
    .put("enabled", enabled)
    .put("permissionTier", permissionTier.wireName)
    .put("requiresConfirmation", requiresConfirmation)
    .put("createdAtMs", createdAtMs)
    .put("updatedAtMs", updatedAtMs)
    .put("lastRunAtMs", lastRunAtMs)
    .put("nextRunAtMs", nextRunAtMs)

private fun NbgScheduleRunEvent.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("automationId", automationId)
    .put("status", status.wireName)
    .put("summary", nbgRedactDiagnosticText(summary))
    .put("evidenceRef", evidenceRef)
    .put("startedAtMs", startedAtMs)
    .put("finishedAtMs", finishedAtMs)

private fun JSONArray?.toScheduleAutomations(): List<NbgScheduledAutomation> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val template = nbgScheduleTemplate(item.cleanString("template")) ?: continue
      add(
        NbgScheduledAutomation(
          id = item.cleanString("id").orEmpty(),
          title = item.cleanString("title").orEmpty(),
          template = template,
          cadence = item.cleanString("cadence").orEmpty(),
          prompt = item.cleanString("prompt").orEmpty(),
          enabled = item.optBoolean("enabled", false),
          permissionTier = nbgSchedulePermissionTier(item.cleanString("permissionTier")) ?: template.permissionTier,
          requiresConfirmation = item.optBoolean("requiresConfirmation", template.permissionTier.requiresConfirmation),
          createdAtMs = item.optLong("createdAtMs", 0L),
          updatedAtMs = item.optLong("updatedAtMs", 0L),
          lastRunAtMs = item.optLong("lastRunAtMs", 0L),
          nextRunAtMs = item.optLong("nextRunAtMs", 0L),
        ),
      )
    }
  }
}

private fun JSONArray?.toScheduleRunEvents(): List<NbgScheduleRunEvent> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      add(
        NbgScheduleRunEvent(
          id = item.cleanString("id").orEmpty(),
          automationId = item.cleanString("automationId").orEmpty(),
          status = nbgScheduleRunStatus(item.cleanString("status")),
          summary = item.cleanString("summary").orEmpty(),
          evidenceRef = item.cleanString("evidenceRef").orEmpty(),
          startedAtMs = item.optLong("startedAtMs", 0L),
          finishedAtMs = item.optLong("finishedAtMs", 0L),
        ),
      )
    }
  }
}

private fun nbgScheduleTemplate(raw: String?): NbgScheduledAutomationTemplate? =
  NbgScheduledAutomationTemplate.entries.firstOrNull { it.wireName == raw?.trim()?.lowercase() }

private fun nbgScheduleRunStatus(raw: String?): NbgScheduleRunStatus =
  NbgScheduleRunStatus.entries.firstOrNull { it.wireName == raw?.trim()?.lowercase() }
    ?: NbgScheduleRunStatus.Queued

private fun nbgSchedulePermissionTier(raw: String?): NbgPermissionRiskTier? =
  NbgPermissionRiskTier.entries.firstOrNull { it.wireName == raw?.trim()?.lowercase() }

private fun nbgNormalizeScheduleCadence(raw: String): String =
  when (raw.trim().lowercase()) {
    "hourly", "daily", "weekly", "manual" -> raw.trim().lowercase()
    else -> "manual"
  }

private fun String.nbgScheduleCompact(limit: Int): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit.coerceAtLeast(0))

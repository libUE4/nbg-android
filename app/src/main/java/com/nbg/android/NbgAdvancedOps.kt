package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal const val NBG_ADVANCED_OPS_VERSION = "nbg-advanced-ops-v1"

data class NbgSessionBranchNode(
  val id: String,
  val parentId: String = "",
  val sessionPath: String = "",
  val action: String = "",
  val label: String = "",
  val modelLabel: String = "",
  val createdAtMs: Long = 0L,
)

data class NbgAuditTimelineEntry(
  val id: String,
  val kind: String,
  val title: String,
  val detail: String = "",
  val severity: String = "info",
  val sessionPath: String = "",
  val createdAtMs: Long = 0L,
)

data class NbgUrlApiFailoverState(
  val enabled: Boolean = true,
  val attemptCount: Int = 0,
  val successCount: Int = 0,
  val lastFromProvider: String = "",
  val lastFromModel: String = "",
  val lastToProvider: String = "",
  val lastToModel: String = "",
  val lastReason: String = "",
  val lastAtMs: Long = 0L,
) {
  val statusLabel: String
    get() = when {
      !enabled -> "关闭"
      attemptCount == 0 -> "待触发"
      successCount == attemptCount -> "$successCount/$attemptCount 成功"
      else -> "$successCount/$attemptCount 成功，最近：$lastReason"
    }
}

data class NbgModelCapabilityProbe(
  val providerId: String,
  val modelId: String,
  val streaming: Boolean = false,
  val tools: Boolean = false,
  val vision: Boolean = false,
  val thinking: Boolean = false,
  val jsonMode: Boolean = false,
  val maxTokens: Long = 0L,
  val status: String = "unknown",
  val checkedAtMs: Long = 0L,
) {
  val key: String
    get() = "$providerId::$modelId"

  val capabilityLabel: String
    get() = listOfNotNull(
      "stream".takeIf { streaming },
      "tools".takeIf { tools },
      "vision".takeIf { vision },
      "thinking".takeIf { thinking },
      "json".takeIf { jsonMode },
      maxTokens.takeIf { it > 0 }?.let { "${it / 1000}k" },
    ).joinToString(" · ").ifBlank { status }
}

data class NbgPromptVersionEntry(
  val id: String,
  val scope: String,
  val name: String,
  val contentHash: String,
  val summary: String = "",
  val createdAtMs: Long = 0L,
)

data class NbgPermissionPolicyTemplate(
  val id: String,
  val label: String,
  val permissionMode: String,
  val thinkingLevel: String = "auto",
  val multiAgentEnabled: Boolean = false,
  val description: String = "",
)

data class NbgKnowledgePackEntry(
  val id: String,
  val label: String,
  val itemCount: Int = 0,
  val byteSize: Long = 0L,
  val status: String = "ready",
  val filePath: String = "",
  val createdAtMs: Long = 0L,
)

data class NbgOfflineModeState(
  val available: Boolean = true,
  val lastIndexedAtMs: Long = 0L,
  val historyCount: Int = 0,
  val memoryCount: Int = 0,
  val skillCount: Int = 0,
  val lastCacheLabel: String = "",
) {
  val statusLabel: String
    get() = if (available) {
      "可离线读取 $historyCount 会话 / $memoryCount Memory / $skillCount Skills"
    } else {
      "离线缓存未就绪"
    }
}

data class NbgErrorKnowledgeEntry(
  val id: String,
  val signature: String,
  val title: String,
  val fixHint: String,
  val count: Int = 1,
  val lastMessage: String = "",
  val lastAtMs: Long = 0L,
)

data class NbgBackgroundTaskEntry(
  val id: String,
  val title: String,
  val status: String = "queued",
  val attemptCount: Int = 0,
  val maxAttempts: Int = 3,
  val detail: String = "",
  val createdAtMs: Long = 0L,
  val updatedAtMs: Long = 0L,
)

data class NbgAdvancedOpsState(
  val modelVersion: String = NBG_ADVANCED_OPS_VERSION,
  val branchNodes: List<NbgSessionBranchNode> = emptyList(),
  val auditTimeline: List<NbgAuditTimelineEntry> = emptyList(),
  val urlApiFailover: NbgUrlApiFailoverState = NbgUrlApiFailoverState(),
  val modelProbes: List<NbgModelCapabilityProbe> = emptyList(),
  val promptVersions: List<NbgPromptVersionEntry> = emptyList(),
  val permissionTemplates: List<NbgPermissionPolicyTemplate> = nbgDefaultPermissionPolicyTemplates(),
  val knowledgePacks: List<NbgKnowledgePackEntry> = emptyList(),
  val offlineMode: NbgOfflineModeState = NbgOfflineModeState(),
  val errorKnowledge: List<NbgErrorKnowledgeEntry> = emptyList(),
  val backgroundTasks: List<NbgBackgroundTaskEntry> = emptyList(),
) {
  val featureSummary: String
    get() = "${branchNodes.size} branches · ${auditTimeline.size} audits · ${modelProbes.size} probes · ${errorKnowledge.size} fixes"
}

internal class NbgAdvancedOpsStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(): NbgAdvancedOpsState =
    parseNbgAdvancedOpsState(prefs.getString(KEY_STATE, null))

  fun save(state: NbgAdvancedOpsState): NbgAdvancedOpsState {
    val normalized = state.normalized()
    prefs.edit().putString(KEY_STATE, normalized.toJsonString()).apply()
    return normalized
  }

  fun recordBranch(
    action: String,
    sessionPath: String,
    parentSessionPath: String = "",
    label: String = "",
    modelLabel: String = "",
    nowMs: Long = System.currentTimeMillis(),
  ): NbgAdvancedOpsState {
    val cleanPath = sessionPath.trim()
    if (cleanPath.isBlank()) return load()
    val parentId = parentSessionPath.trim().takeIf { it.isNotBlank() }?.let(::nbgAdvancedOpsStableId).orEmpty()
    val node = NbgSessionBranchNode(
      id = nbgAdvancedOpsStableId("$cleanPath:${action.trim()}:$nowMs"),
      parentId = parentId,
      sessionPath = cleanPath,
      action = action.trim().ifBlank { "checkpoint" },
      label = label.nbgAdvancedOpsCompact(120),
      modelLabel = modelLabel.nbgAdvancedOpsCompact(120),
      createdAtMs = nowMs.coerceAtLeast(0L),
    )
    return save(load().copy(branchNodes = listOf(node) + load().branchNodes))
  }

  fun recordAudit(
    kind: String,
    title: String,
    detail: String = "",
    severity: String = "info",
    sessionPath: String = "",
    nowMs: Long = System.currentTimeMillis(),
  ): NbgAdvancedOpsState {
    val cleanTitle = title.nbgAdvancedOpsCompact(140)
    if (cleanTitle.isBlank()) return load()
    val entry = NbgAuditTimelineEntry(
      id = nbgAdvancedOpsStableId("$kind:$cleanTitle:$nowMs"),
      kind = kind.trim().ifBlank { "event" },
      title = cleanTitle,
      detail = detail.nbgAdvancedOpsCompact(280),
      severity = severity.trim().ifBlank { "info" },
      sessionPath = sessionPath.trim(),
      createdAtMs = nowMs.coerceAtLeast(0L),
    )
    return save(load().copy(auditTimeline = listOf(entry) + load().auditTimeline))
  }

  fun recordUrlApiFailover(
    fromProvider: String,
    fromModel: String,
    toProvider: String,
    toModel: String,
    reason: String,
    ok: Boolean,
    nowMs: Long = System.currentTimeMillis(),
  ): NbgAdvancedOpsState {
    val current = load()
    val nextFailover = current.urlApiFailover.copy(
      attemptCount = current.urlApiFailover.attemptCount + 1,
      successCount = current.urlApiFailover.successCount + if (ok) 1 else 0,
      lastFromProvider = fromProvider.nbgAdvancedOpsCompact(100),
      lastFromModel = fromModel.nbgAdvancedOpsCompact(120),
      lastToProvider = toProvider.nbgAdvancedOpsCompact(100),
      lastToModel = toModel.nbgAdvancedOpsCompact(120),
      lastReason = reason.nbgAdvancedOpsCompact(180),
      lastAtMs = nowMs.coerceAtLeast(0L),
    )
    val state = save(current.copy(urlApiFailover = nextFailover))
    return recordAudit(
      kind = "url_api_failover",
      title = if (ok) "URL API 自动切换成功" else "URL API 自动切换失败",
      detail = "${fromProvider.ifBlank { "primary" }}:$fromModel -> ${toProvider.ifBlank { "fallback" }}:$toModel · ${reason.nbgAdvancedOpsCompact(160)}",
      severity = if (ok) "info" else "error",
      nowMs = nowMs,
    ).copy(urlApiFailover = state.urlApiFailover)
  }

  fun recordModelProbe(probe: NbgModelCapabilityProbe): NbgAdvancedOpsState {
    if (probe.providerId.isBlank() || probe.modelId.isBlank()) return load()
    val current = load()
    val next = (listOf(probe.normalized()) + current.modelProbes.filterNot { it.key == probe.key })
    return save(current.copy(modelProbes = next))
  }

  fun snapshotPromptVersion(
    scope: String,
    name: String,
    content: String,
    summary: String = "",
    nowMs: Long = System.currentTimeMillis(),
  ): NbgAdvancedOpsState {
    val cleanName = name.nbgAdvancedOpsCompact(120)
    val cleanContent = content.trim()
    if (cleanName.isBlank() || cleanContent.isBlank()) return load()
    val entry = NbgPromptVersionEntry(
      id = nbgAdvancedOpsStableId("${scope.trim()}:$cleanName:${cleanContent.sha256ForAdvancedOps()}"),
      scope = scope.trim().ifBlank { "prompt" },
      name = cleanName,
      contentHash = cleanContent.sha256ForAdvancedOps(),
      summary = summary.nbgAdvancedOpsCompact(180),
      createdAtMs = nowMs.coerceAtLeast(0L),
    )
    val current = load()
    return save(current.copy(promptVersions = listOf(entry) + current.promptVersions.filterNot { it.id == entry.id }))
  }

  fun recordKnowledgePack(
    label: String,
    itemCount: Int,
    byteSize: Long,
    filePath: String = "",
    nowMs: Long = System.currentTimeMillis(),
  ): NbgAdvancedOpsState {
    val entry = NbgKnowledgePackEntry(
      id = nbgAdvancedOpsStableId("${label.trim()}:$nowMs"),
      label = label.nbgAdvancedOpsCompact(120).ifBlank { "Knowledge Pack" },
      itemCount = itemCount.coerceAtLeast(0),
      byteSize = byteSize.coerceAtLeast(0L),
      filePath = filePath.trim(),
      createdAtMs = nowMs.coerceAtLeast(0L),
    )
    val current = load()
    return save(current.copy(knowledgePacks = listOf(entry) + current.knowledgePacks))
  }

  fun updateOfflineMode(historyCount: Int, memoryCount: Int, skillCount: Int, label: String = "", nowMs: Long = System.currentTimeMillis()): NbgAdvancedOpsState =
    save(
      load().copy(
        offlineMode = NbgOfflineModeState(
          available = historyCount > 0 || memoryCount > 0 || skillCount > 0,
          lastIndexedAtMs = nowMs.coerceAtLeast(0L),
          historyCount = historyCount.coerceAtLeast(0),
          memoryCount = memoryCount.coerceAtLeast(0),
          skillCount = skillCount.coerceAtLeast(0),
          lastCacheLabel = label.nbgAdvancedOpsCompact(160),
        ),
      ),
    )

  fun recordError(source: String, message: String, nowMs: Long = System.currentTimeMillis()): NbgAdvancedOpsState {
    val cleanMessage = message.nbgAdvancedOpsCompact(400)
    if (cleanMessage.isBlank()) return load()
    val classified = nbgClassifyAdvancedOpsError(cleanMessage)
    val current = load()
    val existing = current.errorKnowledge.firstOrNull { it.signature == classified.signature }
    val entry = if (existing == null) {
      NbgErrorKnowledgeEntry(
        id = nbgAdvancedOpsStableId(classified.signature),
        signature = classified.signature,
        title = classified.title,
        fixHint = classified.fixHint,
        lastMessage = "$source: $cleanMessage".nbgAdvancedOpsCompact(300),
        lastAtMs = nowMs.coerceAtLeast(0L),
      )
    } else {
      existing.copy(
        count = existing.count + 1,
        lastMessage = "$source: $cleanMessage".nbgAdvancedOpsCompact(300),
        lastAtMs = nowMs.coerceAtLeast(0L),
      )
    }
    return save(current.copy(errorKnowledge = listOf(entry) + current.errorKnowledge.filterNot { it.signature == entry.signature }))
  }

  fun enqueueTask(title: String, detail: String = "", nowMs: Long = System.currentTimeMillis()): NbgAdvancedOpsState {
    val entry = NbgBackgroundTaskEntry(
      id = nbgAdvancedOpsStableId("${title.trim()}:$nowMs"),
      title = title.nbgAdvancedOpsCompact(120).ifBlank { "Background task" },
      detail = detail.nbgAdvancedOpsCompact(240),
      createdAtMs = nowMs.coerceAtLeast(0L),
      updatedAtMs = nowMs.coerceAtLeast(0L),
    )
    val current = load()
    return save(current.copy(backgroundTasks = listOf(entry) + current.backgroundTasks))
  }

  fun updateFailoverEnabled(enabled: Boolean): NbgAdvancedOpsState =
    save(load().let { it.copy(urlApiFailover = it.urlApiFailover.copy(enabled = enabled)) })

  private companion object {
    const val PREFS = "nbg_advanced_ops"
    const val KEY_STATE = "state"
  }
}

data class NbgAdvancedOpsErrorClassification(
  val signature: String,
  val title: String,
  val fixHint: String,
)

internal fun nbgClassifyAdvancedOpsError(message: String): NbgAdvancedOpsErrorClassification {
  val clean = message.lowercase()
  return when {
    "400" in clean && ("authorization" in clean || "x-api-key" in clean || "header" in clean) ->
      NbgAdvancedOpsErrorClassification("url_api_400_auth_headers", "URL API 400 / 鉴权 Header", "检查 base URL、Authorization/x-api-key 是否重复，以及 ProviderProfile authHeader。")
    "401" in clean || "unauthorized" in clean ->
      NbgAdvancedOpsErrorClassification("auth_401", "401 鉴权失败", "重新保存 API key，确认账号额度和 provider endpoint。")
    "429" in clean || "rate limit" in clean ->
      NbgAdvancedOpsErrorClassification("rate_limit_429", "429 限流", "启用 URL API failover，或切换到备用 provider/model。")
    "connection refused" in clean || "failed to connect" in clean ->
      NbgAdvancedOpsErrorClassification("connection_refused", "连接被拒绝", "检查无线调试端口、后端服务或局域网地址是否仍然有效。")
    "install_failed" in clean || "adb" in clean && "failed" in clean ->
      NbgAdvancedOpsErrorClassification("adb_install_failed", "ADB 安装失败", "确认设备在线、包名签名一致，必要时使用 -r -d 或卸载旧版本。")
    "compile" in clean || "kotlin" in clean || "gradle" in clean ->
      NbgAdvancedOpsErrorClassification("build_failure", "构建失败", "查看首个 Kotlin/Gradle error，先修类型和签名变更，再跑 nbg_test.sh。")
    else ->
      NbgAdvancedOpsErrorClassification("generic_${clean.nbgAdvancedOpsCompact(80).sha256ForAdvancedOps().take(10)}", "未知错误", "保留原始错误，下一次出现时按签名聚合。")
  }
}

internal fun nbgDefaultPermissionPolicyTemplates(): List<NbgPermissionPolicyTemplate> =
  listOf(
    NbgPermissionPolicyTemplate("readonly_research", "只读研究", "read_only", "auto", false, "禁止写操作，适合分析、搜索和计划。"),
    NbgPermissionPolicyTemplate("android_build", "安卓构建", "ask", "medium", false, "允许构建/测试，写入和高风险操作先确认。"),
    NbgPermissionPolicyTemplate("autofix", "全自动修复", "operate", "high", true, "适合受控代码修复，开启多 Agent，默认更主动。"),
    NbgPermissionPolicyTemplate("high_risk_confirm", "高危确认", "ask", "high", false, "所有中高风险工具保留确认，适合生产环境。"),
  )

internal fun nbgUrlApiFailoverCandidate(
  entries: List<NbgStoredApi>,
  currentEntry: NbgStoredApi,
  currentModel: NbgApiModel,
  state: NbgAdvancedOpsState,
): Pair<NbgStoredApi, NbgApiModel>? {
  if (!state.urlApiFailover.enabled) return null
  return entries
    .asSequence()
    .sortedByDescending { it.updatedAtMs }
    .flatMap { entry ->
      val verified = entry.verifiedModelIds
      val models = entry.models.ifEmpty {
        verified.map { NbgApiModel(it) }
      }
      models.asSequence()
        .filter { model ->
          model.id.isNotBlank() &&
            (verified.isEmpty() || model.id in verified) &&
            !(entry.id == currentEntry.id && model.id == currentModel.id)
        }
        .map { entry to it }
    }
    .firstOrNull()
}

internal fun nbgShouldPreemptivelyFailoverUrlApi(
  providerId: String,
  modelId: String,
  state: NbgAdvancedOpsState,
  nowMs: Long = System.currentTimeMillis(),
): Boolean {
  val failover = state.urlApiFailover
  if (!failover.enabled || failover.lastAtMs <= 0L) return false
  if (nowMs - failover.lastAtMs > 10 * 60_000L) return false
  return failover.lastFromProvider == providerId && failover.lastFromModel == modelId && failover.lastReason.contains("HTTP", ignoreCase = true)
}

internal fun nbgBuildModelCapabilityProbe(
  entry: NbgStoredApi,
  model: NbgApiModel,
  providerProfiles: List<NbgModelProviderProfile> = emptyList(),
  status: String = "local_probe",
  nowMs: Long = System.currentTimeMillis(),
): NbgModelCapabilityProbe {
  val providerId = nbgUrlApiProviderId(entry.id)
  val profile = nbgProfileForUrlApi(entry.baseUrl, model.id, providerProfiles)
  val id = model.id.lowercase()
  val context = model.contextWindow.takeIf { it > 0L } ?: when {
    "128k" in id -> 128_000L
    "32k" in id -> 32_000L
    "16k" in id -> 16_000L
    "claude" in id || "gemini" in id || "gpt-5" in id -> 128_000L
    else -> 8_000L
  }
  return NbgModelCapabilityProbe(
    providerId = providerId,
    modelId = model.id,
    streaming = profile.apiMode in setOf("openai", "anthropic", "gemini"),
    tools = listOf("gpt", "claude", "gemini", "qwen", "kimi", "deepseek").any { it in id },
    vision = listOf("vision", "vl", "omni", "gpt-4o", "gemini").any { it in id },
    thinking = profile.supportsThinking || listOf("reason", "thinking", "r1", "o3", "o4", "gpt-5", "claude").any { it in id },
    jsonMode = profile.apiMode != "anthropic" || "claude-3" in id || "claude-4" in id,
    maxTokens = context,
    status = status,
    checkedAtMs = nowMs.coerceAtLeast(0L),
  ).normalized()
}

internal fun nbgAdvancedOpsKnowledgePackSizeEstimate(
  historyCount: Int,
  memoryCount: Int,
  skillCount: Int,
): Long =
  historyCount.coerceAtLeast(0) * 3_000L + memoryCount.coerceAtLeast(0) * 900L + skillCount.coerceAtLeast(0) * 2_400L

internal fun nbgBuildKnowledgePackJson(
  conversations: List<NbgAgentConversation>,
  memoryState: HanakoMemoryState,
  skillsSnapshot: HanakoSkillsSnapshot,
  generatedAtMs: Long = System.currentTimeMillis(),
): String =
  JSONObject()
    .put("version", NBG_ADVANCED_OPS_VERSION)
    .put("generatedAtMs", generatedAtMs.coerceAtLeast(0L))
    .put("sessions", JSONArray().also { array ->
      conversations.take(200).forEach { session ->
        array.put(
          JSONObject()
            .put("path", session.path)
            .put("title", session.title)
            .put("subtitle", session.subtitle)
            .put("snippet", session.snippet)
            .put("pinned", session.pinned),
        )
      }
    })
    .put("memory", JSONArray().also { array ->
      memoryState.items.take(300).forEach { item ->
        array.put(
          JSONObject()
            .put("id", item.id)
            .put("type", item.type)
            .put("title", item.title)
            .put("content", nbgRedactDiagnosticText(item.content))
            .put("tags", JSONArray(item.tags))
            .put("enabled", item.enabled),
        )
      }
    })
    .put("skills", JSONArray().also { array ->
      skillsSnapshot.visibleSkills.take(200).forEach { skill ->
        array.put(
          JSONObject()
            .put("name", skill.name)
            .put("description", skill.description)
            .put("source", skill.source)
            .put("enabled", skill.enabled)
            .put("filePath", skill.filePath),
        )
      }
    })
    .toString(2)

internal fun parseNbgAdvancedOpsState(raw: String?): NbgAdvancedOpsState =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    NbgAdvancedOpsState(
      branchNodes = root.optJSONArray("branchNodes").toNbgBranchNodes(),
      auditTimeline = root.optJSONArray("auditTimeline").toNbgAuditTimeline(),
      urlApiFailover = root.optJSONObject("urlApiFailover").toNbgUrlApiFailoverState(),
      modelProbes = root.optJSONArray("modelProbes").toNbgModelProbes(),
      promptVersions = root.optJSONArray("promptVersions").toNbgPromptVersions(),
      permissionTemplates = root.optJSONArray("permissionTemplates").toNbgPermissionTemplates().ifEmpty { nbgDefaultPermissionPolicyTemplates() },
      knowledgePacks = root.optJSONArray("knowledgePacks").toNbgKnowledgePacks(),
      offlineMode = root.optJSONObject("offlineMode").toNbgOfflineModeState(),
      errorKnowledge = root.optJSONArray("errorKnowledge").toNbgErrorKnowledge(),
      backgroundTasks = root.optJSONArray("backgroundTasks").toNbgBackgroundTasks(),
    ).normalized()
  }.getOrDefault(NbgAdvancedOpsState())

private fun NbgAdvancedOpsState.normalized(): NbgAdvancedOpsState =
  copy(
    branchNodes = branchNodes.map { it.normalized() }.distinctBy { it.id }.sortedByDescending { it.createdAtMs }.take(80),
    auditTimeline = auditTimeline.map { it.normalized() }.distinctBy { it.id }.sortedByDescending { it.createdAtMs }.take(160),
    urlApiFailover = urlApiFailover.normalized(),
    modelProbes = modelProbes.map { it.normalized() }.distinctBy { it.key }.sortedByDescending { it.checkedAtMs }.take(80),
    promptVersions = promptVersions.map { it.normalized() }.distinctBy { it.id }.sortedByDescending { it.createdAtMs }.take(120),
    permissionTemplates = permissionTemplates.map { it.normalized() }.distinctBy { it.id }.ifEmpty { nbgDefaultPermissionPolicyTemplates() },
    knowledgePacks = knowledgePacks.map { it.normalized() }.distinctBy { it.id }.sortedByDescending { it.createdAtMs }.take(40),
    offlineMode = offlineMode.normalized(),
    errorKnowledge = errorKnowledge.map { it.normalized() }.distinctBy { it.signature }.sortedWith(compareByDescending<NbgErrorKnowledgeEntry> { it.lastAtMs }.thenByDescending { it.count }).take(80),
    backgroundTasks = backgroundTasks.map { it.normalized() }.distinctBy { it.id }.sortedByDescending { it.updatedAtMs }.take(80),
  )

private fun NbgAdvancedOpsState.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("branchNodes", JSONArray().also { array -> branchNodes.forEach { array.put(it.toJson()) } })
    .put("auditTimeline", JSONArray().also { array -> auditTimeline.forEach { array.put(it.toJson()) } })
    .put("urlApiFailover", urlApiFailover.toJson())
    .put("modelProbes", JSONArray().also { array -> modelProbes.forEach { array.put(it.toJson()) } })
    .put("promptVersions", JSONArray().also { array -> promptVersions.forEach { array.put(it.toJson()) } })
    .put("permissionTemplates", JSONArray().also { array -> permissionTemplates.forEach { array.put(it.toJson()) } })
    .put("knowledgePacks", JSONArray().also { array -> knowledgePacks.forEach { array.put(it.toJson()) } })
    .put("offlineMode", offlineMode.toJson())
    .put("errorKnowledge", JSONArray().also { array -> errorKnowledge.forEach { array.put(it.toJson()) } })
    .put("backgroundTasks", JSONArray().also { array -> backgroundTasks.forEach { array.put(it.toJson()) } })
    .toString()

private fun JSONArray?.toNbgBranchNodes(): List<NbgSessionBranchNode> =
  this.toList { item ->
    NbgSessionBranchNode(
      id = item.cleanString("id").orEmpty(),
      parentId = item.cleanString("parentId").orEmpty(),
      sessionPath = item.cleanString("sessionPath").orEmpty(),
      action = item.cleanString("action").orEmpty(),
      label = item.cleanString("label").orEmpty(),
      modelLabel = item.cleanString("modelLabel").orEmpty(),
      createdAtMs = item.optLong("createdAtMs", 0L),
    )
  }

private fun JSONArray?.toNbgAuditTimeline(): List<NbgAuditTimelineEntry> =
  this.toList { item ->
    NbgAuditTimelineEntry(
      id = item.cleanString("id").orEmpty(),
      kind = item.cleanString("kind").orEmpty(),
      title = item.cleanString("title").orEmpty(),
      detail = item.cleanString("detail").orEmpty(),
      severity = item.cleanString("severity").orEmpty(),
      sessionPath = item.cleanString("sessionPath").orEmpty(),
      createdAtMs = item.optLong("createdAtMs", 0L),
    )
  }

private fun JSONObject?.toNbgUrlApiFailoverState(): NbgUrlApiFailoverState {
  val root = this ?: return NbgUrlApiFailoverState()
  return NbgUrlApiFailoverState(
    enabled = root.optBoolean("enabled", true),
    attemptCount = root.optInt("attemptCount", 0),
    successCount = root.optInt("successCount", 0),
    lastFromProvider = root.cleanString("lastFromProvider").orEmpty(),
    lastFromModel = root.cleanString("lastFromModel").orEmpty(),
    lastToProvider = root.cleanString("lastToProvider").orEmpty(),
    lastToModel = root.cleanString("lastToModel").orEmpty(),
    lastReason = root.cleanString("lastReason").orEmpty(),
    lastAtMs = root.optLong("lastAtMs", 0L),
  ).normalized()
}

private fun JSONArray?.toNbgModelProbes(): List<NbgModelCapabilityProbe> =
  this.toList { item ->
    NbgModelCapabilityProbe(
      providerId = item.cleanString("providerId").orEmpty(),
      modelId = item.cleanString("modelId").orEmpty(),
      streaming = item.optBoolean("streaming", false),
      tools = item.optBoolean("tools", false),
      vision = item.optBoolean("vision", false),
      thinking = item.optBoolean("thinking", false),
      jsonMode = item.optBoolean("jsonMode", false),
      maxTokens = item.optLong("maxTokens", 0L),
      status = item.cleanString("status").orEmpty(),
      checkedAtMs = item.optLong("checkedAtMs", 0L),
    )
  }

private fun JSONArray?.toNbgPromptVersions(): List<NbgPromptVersionEntry> =
  this.toList { item ->
    NbgPromptVersionEntry(
      id = item.cleanString("id").orEmpty(),
      scope = item.cleanString("scope").orEmpty(),
      name = item.cleanString("name").orEmpty(),
      contentHash = item.cleanString("contentHash").orEmpty(),
      summary = item.cleanString("summary").orEmpty(),
      createdAtMs = item.optLong("createdAtMs", 0L),
    )
  }

private fun JSONArray?.toNbgPermissionTemplates(): List<NbgPermissionPolicyTemplate> =
  this.toList { item ->
    NbgPermissionPolicyTemplate(
      id = item.cleanString("id").orEmpty(),
      label = item.cleanString("label").orEmpty(),
      permissionMode = item.cleanString("permissionMode").orEmpty(),
      thinkingLevel = item.cleanString("thinkingLevel").orEmpty(),
      multiAgentEnabled = item.optBoolean("multiAgentEnabled", false),
      description = item.cleanString("description").orEmpty(),
    )
  }

private fun JSONArray?.toNbgKnowledgePacks(): List<NbgKnowledgePackEntry> =
  this.toList { item ->
    NbgKnowledgePackEntry(
      id = item.cleanString("id").orEmpty(),
      label = item.cleanString("label").orEmpty(),
      itemCount = item.optInt("itemCount", 0),
      byteSize = item.optLong("byteSize", 0L),
      status = item.cleanString("status").orEmpty(),
      filePath = item.cleanString("filePath").orEmpty(),
      createdAtMs = item.optLong("createdAtMs", 0L),
    )
  }

private fun JSONObject?.toNbgOfflineModeState(): NbgOfflineModeState {
  val root = this ?: return NbgOfflineModeState()
  return NbgOfflineModeState(
    available = root.optBoolean("available", true),
    lastIndexedAtMs = root.optLong("lastIndexedAtMs", 0L),
    historyCount = root.optInt("historyCount", 0),
    memoryCount = root.optInt("memoryCount", 0),
    skillCount = root.optInt("skillCount", 0),
    lastCacheLabel = root.cleanString("lastCacheLabel").orEmpty(),
  ).normalized()
}

private fun JSONArray?.toNbgErrorKnowledge(): List<NbgErrorKnowledgeEntry> =
  this.toList { item ->
    NbgErrorKnowledgeEntry(
      id = item.cleanString("id").orEmpty(),
      signature = item.cleanString("signature").orEmpty(),
      title = item.cleanString("title").orEmpty(),
      fixHint = item.cleanString("fixHint").orEmpty(),
      count = item.optInt("count", 1),
      lastMessage = item.cleanString("lastMessage").orEmpty(),
      lastAtMs = item.optLong("lastAtMs", 0L),
    )
  }

private fun JSONArray?.toNbgBackgroundTasks(): List<NbgBackgroundTaskEntry> =
  this.toList { item ->
    NbgBackgroundTaskEntry(
      id = item.cleanString("id").orEmpty(),
      title = item.cleanString("title").orEmpty(),
      status = item.cleanString("status").orEmpty(),
      attemptCount = item.optInt("attemptCount", 0),
      maxAttempts = item.optInt("maxAttempts", 3),
      detail = item.cleanString("detail").orEmpty(),
      createdAtMs = item.optLong("createdAtMs", 0L),
      updatedAtMs = item.optLong("updatedAtMs", 0L),
    )
  }

private fun NbgSessionBranchNode.normalized(): NbgSessionBranchNode =
  copy(
    id = id.ifBlank { nbgAdvancedOpsStableId("$sessionPath:$action:$createdAtMs") },
    parentId = parentId.trim(),
    sessionPath = sessionPath.trim().take(600),
    action = action.trim().ifBlank { "checkpoint" }.take(48),
    label = label.nbgAdvancedOpsCompact(120),
    modelLabel = modelLabel.nbgAdvancedOpsCompact(120),
    createdAtMs = createdAtMs.coerceAtLeast(0L),
  )

private fun NbgAuditTimelineEntry.normalized(): NbgAuditTimelineEntry =
  copy(
    id = id.ifBlank { nbgAdvancedOpsStableId("$kind:$title:$createdAtMs") },
    kind = kind.trim().ifBlank { "event" }.take(48),
    title = title.nbgAdvancedOpsCompact(140),
    detail = detail.nbgAdvancedOpsCompact(280),
    severity = severity.trim().ifBlank { "info" }.take(24),
    sessionPath = sessionPath.trim().take(600),
    createdAtMs = createdAtMs.coerceAtLeast(0L),
  )

private fun NbgUrlApiFailoverState.normalized(): NbgUrlApiFailoverState =
  copy(
    attemptCount = attemptCount.coerceAtLeast(0),
    successCount = successCount.coerceIn(0, attemptCount.coerceAtLeast(0)),
    lastFromProvider = lastFromProvider.nbgAdvancedOpsCompact(100),
    lastFromModel = lastFromModel.nbgAdvancedOpsCompact(120),
    lastToProvider = lastToProvider.nbgAdvancedOpsCompact(100),
    lastToModel = lastToModel.nbgAdvancedOpsCompact(120),
    lastReason = lastReason.nbgAdvancedOpsCompact(180),
    lastAtMs = lastAtMs.coerceAtLeast(0L),
  )

private fun NbgModelCapabilityProbe.normalized(): NbgModelCapabilityProbe =
  copy(
    providerId = providerId.trim().take(100),
    modelId = modelId.trim().take(160),
    maxTokens = maxTokens.coerceAtLeast(0L),
    status = status.trim().ifBlank { "unknown" }.take(80),
    checkedAtMs = checkedAtMs.coerceAtLeast(0L),
  )

private fun NbgPromptVersionEntry.normalized(): NbgPromptVersionEntry =
  copy(
    id = id.ifBlank { nbgAdvancedOpsStableId("$scope:$name:$contentHash") },
    scope = scope.trim().ifBlank { "prompt" }.take(48),
    name = name.nbgAdvancedOpsCompact(120),
    contentHash = contentHash.trim().take(80),
    summary = summary.nbgAdvancedOpsCompact(180),
    createdAtMs = createdAtMs.coerceAtLeast(0L),
  )

private fun NbgPermissionPolicyTemplate.normalized(): NbgPermissionPolicyTemplate =
  copy(
    id = id.trim().ifBlank { nbgAdvancedOpsStableId(label) }.take(80),
    label = label.nbgAdvancedOpsCompact(80),
    permissionMode = nbgNormalizePermissionMode(permissionMode),
    thinkingLevel = nbgNormalizeThinkingLevel(thinkingLevel) ?: "auto",
    description = description.nbgAdvancedOpsCompact(180),
  )

private fun NbgKnowledgePackEntry.normalized(): NbgKnowledgePackEntry =
  copy(
    id = id.ifBlank { nbgAdvancedOpsStableId("$label:$createdAtMs") },
    label = label.nbgAdvancedOpsCompact(120),
    itemCount = itemCount.coerceAtLeast(0),
    byteSize = byteSize.coerceAtLeast(0L),
    status = status.trim().ifBlank { "ready" }.take(40),
    filePath = filePath.trim().take(800),
    createdAtMs = createdAtMs.coerceAtLeast(0L),
  )

private fun NbgOfflineModeState.normalized(): NbgOfflineModeState =
  copy(
    lastIndexedAtMs = lastIndexedAtMs.coerceAtLeast(0L),
    historyCount = historyCount.coerceAtLeast(0),
    memoryCount = memoryCount.coerceAtLeast(0),
    skillCount = skillCount.coerceAtLeast(0),
    lastCacheLabel = lastCacheLabel.nbgAdvancedOpsCompact(160),
  )

private fun NbgErrorKnowledgeEntry.normalized(): NbgErrorKnowledgeEntry =
  copy(
    id = id.ifBlank { nbgAdvancedOpsStableId(signature) },
    signature = signature.trim().ifBlank { id }.take(100),
    title = title.nbgAdvancedOpsCompact(120),
    fixHint = fixHint.nbgAdvancedOpsCompact(240),
    count = count.coerceAtLeast(1),
    lastMessage = lastMessage.nbgAdvancedOpsCompact(300),
    lastAtMs = lastAtMs.coerceAtLeast(0L),
  )

private fun NbgBackgroundTaskEntry.normalized(): NbgBackgroundTaskEntry =
  copy(
    id = id.ifBlank { nbgAdvancedOpsStableId("$title:$createdAtMs") },
    title = title.nbgAdvancedOpsCompact(120),
    status = status.trim().ifBlank { "queued" }.take(40),
    attemptCount = attemptCount.coerceAtLeast(0),
    maxAttempts = maxAttempts.coerceAtLeast(1),
    detail = detail.nbgAdvancedOpsCompact(240),
    createdAtMs = createdAtMs.coerceAtLeast(0L),
    updatedAtMs = updatedAtMs.coerceAtLeast(createdAtMs.coerceAtLeast(0L)),
  )

private fun NbgSessionBranchNode.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("parentId", parentId)
    .put("sessionPath", sessionPath)
    .put("action", action)
    .put("label", label)
    .put("modelLabel", modelLabel)
    .put("createdAtMs", createdAtMs)

private fun NbgAuditTimelineEntry.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("kind", kind)
    .put("title", title)
    .put("detail", detail)
    .put("severity", severity)
    .put("sessionPath", sessionPath)
    .put("createdAtMs", createdAtMs)

private fun NbgUrlApiFailoverState.toJson(): JSONObject =
  JSONObject()
    .put("enabled", enabled)
    .put("attemptCount", attemptCount)
    .put("successCount", successCount)
    .put("lastFromProvider", lastFromProvider)
    .put("lastFromModel", lastFromModel)
    .put("lastToProvider", lastToProvider)
    .put("lastToModel", lastToModel)
    .put("lastReason", lastReason)
    .put("lastAtMs", lastAtMs)

private fun NbgModelCapabilityProbe.toJson(): JSONObject =
  JSONObject()
    .put("providerId", providerId)
    .put("modelId", modelId)
    .put("streaming", streaming)
    .put("tools", tools)
    .put("vision", vision)
    .put("thinking", thinking)
    .put("jsonMode", jsonMode)
    .put("maxTokens", maxTokens)
    .put("status", status)
    .put("checkedAtMs", checkedAtMs)

private fun NbgPromptVersionEntry.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("scope", scope)
    .put("name", name)
    .put("contentHash", contentHash)
    .put("summary", summary)
    .put("createdAtMs", createdAtMs)

private fun NbgPermissionPolicyTemplate.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("label", label)
    .put("permissionMode", permissionMode)
    .put("thinkingLevel", thinkingLevel)
    .put("multiAgentEnabled", multiAgentEnabled)
    .put("description", description)

private fun NbgKnowledgePackEntry.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("label", label)
    .put("itemCount", itemCount)
    .put("byteSize", byteSize)
    .put("status", status)
    .put("filePath", filePath)
    .put("createdAtMs", createdAtMs)

private fun NbgOfflineModeState.toJson(): JSONObject =
  JSONObject()
    .put("available", available)
    .put("lastIndexedAtMs", lastIndexedAtMs)
    .put("historyCount", historyCount)
    .put("memoryCount", memoryCount)
    .put("skillCount", skillCount)
    .put("lastCacheLabel", lastCacheLabel)

private fun NbgErrorKnowledgeEntry.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("signature", signature)
    .put("title", title)
    .put("fixHint", fixHint)
    .put("count", count)
    .put("lastMessage", lastMessage)
    .put("lastAtMs", lastAtMs)

private fun NbgBackgroundTaskEntry.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("title", title)
    .put("status", status)
    .put("attemptCount", attemptCount)
    .put("maxAttempts", maxAttempts)
    .put("detail", detail)
    .put("createdAtMs", createdAtMs)
    .put("updatedAtMs", updatedAtMs)

private fun <T> JSONArray?.toList(block: (JSONObject) -> T): List<T> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length().coerceAtMost(300)) {
      val item = array.optJSONObject(index) ?: continue
      add(block(item))
    }
  }
}

private fun nbgAdvancedOpsStableId(value: String): String =
  value.sha256ForAdvancedOps().take(24)

private fun String.sha256ForAdvancedOps(): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
  return digest.joinToString("") { "%02x".format(it) }
}

private fun String.nbgAdvancedOpsCompact(limit: Int): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit.coerceAtLeast(0))

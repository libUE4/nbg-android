package com.nbg.android

import android.content.Context
import android.util.Log
import com.nbg.android.terminal.TerminalEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

internal class HanakoApiClient {
  fun getJson(info: HanakoServerInfo, path: String): JSONObject =
    requestJson(info, "GET", path)

  fun postJson(
    info: HanakoServerInfo,
    path: String,
    body: JSONObject = JSONObject(),
    method: String = "POST",
  ): JSONObject =
    requestJson(info, method, path, body)

  fun isHealthy(info: HanakoServerInfo): Boolean =
    runCatching { requestJson(info, "GET", "/api/health", null, readTimeoutMs = 1_500).optString("status") == "ok" }
      .getOrDefault(false)

  fun applyAndroidRuntimeDefaults(info: HanakoServerInfo) {
      val learnSkills = JSONObject()
      .put("enabled", true)
      .put("allow_github_fetch", true)
      .put("safety_review", true)
      .put("min_stars", 0)
    postJson(
      info,
      "/api/config",
      JSONObject()
        .put("locale", NBG_ANDROID_LANGUAGE_LOCALE)
        .put("sandbox", false)
        .put("sandbox_network", true)
        .put("desk", JSONObject().put("home_folder", NBG_UBUNTU_ROOT_HOME))
        .put("capabilities", JSONObject().put("learn_skills", learnSkills)),
      method = "PUT",
    )
  }

  fun configureUrlApiModel(
    info: HanakoServerInfo,
    entry: NbgStoredApi,
    model: NbgApiModel,
    providerProfiles: List<NbgModelProviderProfile> = emptyList(),
  ) {
    val profile = nbgProfileForUrlApi(entry.baseUrl, model.id, providerProfiles)
    val provider = profile.apiMode.ifBlank { nbgHanakoProviderForUrlApi(entry.baseUrl, model.id) }
    val providerId = nbgUrlApiProviderId(entry.id)
    val modelIds = listOf(model.id.trim()).filter { it.isNotBlank() }
    val providerApi = when (provider) {
      "anthropic" -> "anthropic-messages"
      else -> "openai-completions"
    }
    val baseUrl = nbgHanakoBaseUrlForUrlApi(entry.baseUrl, provider)
    val staleProviders = findStaleUrlApiProviders(info, entry, providerId)
    val modelConfigs = JSONArray(modelIds.map { modelId ->
      val storedModel = entry.models.firstOrNull { it.id == modelId }
      val inferredThinkingLevels = nbgSupportedThinkingLevelsForModel(modelId, providerId, baseUrl, providerApi)
      val thinkingLevels = if (inferredThinkingLevels.isEmpty() && profile.supportsThinking) {
        listOf("low", "medium", "high")
      } else {
        inferredThinkingLevels
      }
      val thinkingSource = nbgThinkingSourceForModel(modelId, providerId, baseUrl, providerApi)
      val contextWindow = nbgEffectiveModelContextWindow(modelId, storedModel?.contextWindow ?: 0L)
      val thinkingFormat = when (thinkingSource) {
        "local-anthropic", "local-kimi" -> "anthropic"
        "local-deepseek" -> "deepseek"
        "local-qwen" -> "qwen"
        else -> ""
      }
      JSONObject()
        .put("id", modelId)
        .put("name", storedModel?.label ?: modelId)
        .put("reasoning", thinkingLevels.any { it == "low" || it == "medium" || it == "high" || it == "xhigh" })
        .put("xhigh", "xhigh" in thinkingLevels)
        .put("thinkingLevels", JSONArray(thinkingLevels))
        .apply {
          contextWindow.takeIf { it > 0L }?.let { context ->
            put("contextWindow", context)
            put("context_window", context)
          }
          if (thinkingFormat.isNotBlank()) {
            put("compat", JSONObject().put("thinkingFormat", thinkingFormat))
          }
        }
    })
    postJson(
      info,
      "/api/config",
      JSONObject()
        .put(
          "api",
          JSONObject()
            .put("provider", providerId)
            .put("api_key", entry.apiKey)
            .put("base_url", baseUrl)
            .put("api", providerApi),
        )
        .put(
          "models",
          JSONObject().put(
            "chat",
            JSONObject()
              .put("id", model.id)
              .put("provider", providerId),
          ),
        )
        .put(
          "providers",
          JSONObject().apply {
            staleProviders.forEach { staleProvider -> put(staleProvider, JSONObject.NULL) }
            put(
              providerId,
              JSONObject()
                .put("base_url", baseUrl)
                .put("api", providerApi)
                .put("api_key", entry.apiKey)
                .put("models", modelConfigs),
            )
          },
        ),
      method = "PUT",
    )
    setDefaultModel(info, model.id, providerId)
  }

  private fun findStaleUrlApiProviders(info: HanakoServerInfo, entry: NbgStoredApi, keepProviderId: String): List<String> =
    runCatching {
      val providers = requestJson(info, "GET", "/api/providers/summary", null)
        .optJSONObject("providers")
        ?: return@runCatching emptyList()
      buildList {
        val names = providers.keys()
        val comparableBaseUrl = nbgComparableApiBaseUrl(entry.baseUrl)
        val modelIds = entry.models.map { it.id }.filter { it.isNotBlank() }.toSet()
        while (names.hasNext()) {
          val name = names.next()
          if (name == keepProviderId || !name.startsWith("urlapi-")) continue
          val provider = providers.optJSONObject(name) ?: continue
          if (!provider.optBoolean("can_delete", false)) continue
          val providerBaseUrl = nbgComparableApiBaseUrl(provider.optString("base_url"))
          val providerModels = provider.optJSONArray("models").toStringSet()
          if (providerBaseUrl == comparableBaseUrl && modelIds.isNotEmpty() && providerModels.any { it in modelIds }) {
            add(name)
          }
        }
      }
    }.getOrDefault(emptyList())

  fun pruneDeletedUrlApiProviders(info: HanakoServerInfo, activeProviderIds: Set<String>): List<String> {
    val staleProviders = findDeletedUrlApiProviders(info, activeProviderIds)
    if (staleProviders.isEmpty()) return emptyList()
    postJson(
      info,
      "/api/config",
      JSONObject().put(
        "providers",
        JSONObject().apply {
          staleProviders.forEach { staleProvider -> put(staleProvider, JSONObject.NULL) }
        },
      ),
      method = "PUT",
    )
    return staleProviders
  }

  private fun findDeletedUrlApiProviders(info: HanakoServerInfo, activeProviderIds: Set<String>): List<String> =
    runCatching {
      val providers = requestJson(info, "GET", "/api/providers/summary", null)
        .optJSONObject("providers")
        ?: return@runCatching emptyList()
      buildList {
        val names = providers.keys()
        while (names.hasNext()) {
          val name = names.next()
          if (name.startsWith("urlapi-") && name !in activeProviderIds) add(name)
        }
      }
    }.getOrDefault(emptyList())

  fun createSession(info: HanakoServerInfo, currentSessionPath: String?): HanakoSessionFocusState {
    val created = postJson(
      info,
      "/api/sessions/new",
      JSONObject()
        .put("cwd", NBG_UBUNTU_ROOT_HOME)
        .apply {
          if (!currentSessionPath.isNullOrBlank()) put("currentSessionPath", currentSessionPath)
        },
    )
    return parseFocusState(created, created.optString("path").ifBlank { error("HanakoPro 没有返回 session path") })
  }

  private fun parseFocusState(root: JSONObject, fallbackPath: String): HanakoSessionFocusState {
    val modelLabel = root.cleanString("currentModelName")
      ?: root.cleanString("modelName")
      ?: root.cleanString("currentModelId")
    return HanakoSessionFocusState(
      path = root.cleanString("path") ?: fallbackPath,
      agentName = root.cleanString("agentName"),
      modelName = modelLabel,
      permissionMode = nbgNormalizePermissionMode(root.cleanString("permissionMode")),
      thinkingLevel = root.cleanString("thinkingLevel") ?: "auto",
    )
  }

  fun switchSession(info: HanakoServerInfo, path: String, currentSessionPath: String?): HanakoSessionFocusState {
    val root = postJson(
      info,
      "/api/sessions/switch",
      JSONObject()
        .put("path", path)
        .apply {
          if (!currentSessionPath.isNullOrBlank()) put("currentSessionPath", currentSessionPath)
        },
    )
    return parseFocusState(root, path)
  }

  fun listSessions(info: HanakoServerInfo): List<HanakoSessionSummary> {
    val sessionsValue = requestValue(info, "GET", "/api/sessions", null)
    if (sessionsValue !is JSONArray) return emptyList()
    return buildList {
      for (index in 0 until sessionsValue.length()) {
        val item = sessionsValue.optJSONObject(index) ?: continue
        val path = item.optString("path").orEmpty()
        if (path.isBlank()) continue
        val title = item.cleanString("title")
          ?: item.cleanString("firstMessage")
          ?: "新聊天"
        val subtitle = listOfNotNull(
          item.cleanString("agentName"),
          item.cleanString("modelId"),
          item.cleanString("modified")?.take(10),
        ).joinToString(" / ").ifBlank { "HanakoPro session" }
        add(
          HanakoSessionSummary(
            path = path,
            title = title,
            subtitle = subtitle,
            pinned = !item.cleanString("pinnedAt").isNullOrBlank(),
            hasSummary = item.optBoolean("hasSummary", false),
          ),
        )
      }
    }
  }

  fun getAgentModelConfig(info: HanakoServerInfo): HanakoAgentModelConfig {
    val agentsRoot = requestJson(info, "GET", "/api/agents", null)
    val modelsRoot = requestJson(info, "GET", "/api/models", null)
    return HanakoAgentModelConfig(
      agents = parseAgents(agentsRoot.optJSONArray("agents")),
      models = parseModels(modelsRoot.optJSONArray("models")),
    )
  }

  fun getProviders(info: HanakoServerInfo): HanakoProviderSnapshot {
    val providersRoot = requestJson(info, "GET", "/api/providers/summary", null)
    val modelsRoot = requestJson(info, "GET", "/api/models", null)
    val active = modelsRoot.optJSONObject("activeModel")
    return HanakoProviderSnapshot(
      providers = parseProviders(providersRoot.optJSONObject("providers")),
      activeProvider = active?.cleanString("provider").orEmpty(),
      activeModel = active?.cleanString("id").orEmpty(),
      currentModel = modelsRoot.cleanString("current").orEmpty(),
      modelCount = modelsRoot.optJSONArray("models")?.length() ?: 0,
    )
  }

  fun getMcpState(info: HanakoServerInfo, agentId: String = HANA_DEFAULT_MCP_AGENT_ID): HanakoMcpState {
    val encodedAgentId = nbgEncodeUrlPathSegment(agentId)
    return parseHanakoMcpState(requestJson(info, "GET", "/api/plugins/mcp/state?agentId=$encodedAgentId", null))
  }

  fun setMcpEnabled(info: HanakoServerInfo, enabled: Boolean) {
    postJson(
      info,
      "/api/plugins/mcp/settings/enabled",
      JSONObject().put("enabled", enabled),
      method = "PUT",
    )
  }

  fun saveMcpConnector(info: HanakoServerInfo, input: HanakoMcpConnectorInput) {
    val body = input.toJson()
    val path = "/api/plugins/mcp/connectors"
    runCatching {
      postJson(info, path, body)
    }.recoverCatching { error ->
      val message = error.message.orEmpty()
      if (
        message.contains("already", ignoreCase = true) ||
        message.contains("exists", ignoreCase = true) ||
        message.contains("duplicate", ignoreCase = true)
      ) {
        postJson(
          info,
          "$path/${nbgEncodeUrlPathSegment(input.id)}",
          body,
          method = "PUT",
        )
      } else {
        throw error
      }
    }.getOrThrow()
  }

  fun runMcpConnectorAction(info: HanakoServerInfo, connectorId: String, action: String) {
    require(action in setOf("start", "stop", "refresh-tools")) { "Unsupported MCP connector action: $action" }
    postJson(
      info,
      "/api/plugins/mcp/connectors/${nbgEncodeUrlPathSegment(connectorId)}/$action",
    )
  }

  fun deleteMcpConnector(info: HanakoServerInfo, connectorId: String) {
    requestJson(
      info,
      "DELETE",
      "/api/plugins/mcp/connectors/${nbgEncodeUrlPathSegment(connectorId)}",
      null,
    )
  }

  fun setAgentMcpConnector(
    info: HanakoServerInfo,
    agentId: String,
    connectorId: String,
    enabled: Boolean,
  ) {
    putAgentMcpConnectorPatch(
      info = info,
      agentId = agentId,
      connectorId = connectorId,
      patch = JSONObject().put("enabled", enabled),
    )
  }

  fun setAgentMcpTool(
    info: HanakoServerInfo,
    agentId: String,
    connectorId: String,
    toolName: String,
    enabled: Boolean,
  ) {
    putAgentMcpConnectorPatch(
      info = info,
      agentId = agentId,
      connectorId = connectorId,
      patch = JSONObject().put("tools", JSONObject().put(toolName, enabled)),
    )
  }

  fun getMemoryState(
    info: HanakoServerInfo,
    query: String = "",
    type: String = "",
    includeDisabled: Boolean = false,
    limit: Int = 100,
  ): HanakoMemoryState {
    val params = buildList {
      if (query.isNotBlank()) add("query=${java.net.URLEncoder.encode(query, Charsets.UTF_8.name())}")
      if (type.isNotBlank()) add("type=${java.net.URLEncoder.encode(type, Charsets.UTF_8.name())}")
      if (includeDisabled) add("includeDisabled=true")
      add("limit=${limit.coerceIn(1, 200)}")
    }.joinToString("&")
    return parseHanakoMemoryState(requestJson(info, "GET", "/api/plugins/memory/state?$params", null))
  }

  fun saveMemoryItem(info: HanakoServerInfo, input: HanakoMemoryInput): HanakoMemoryItem {
    val root = postJson(info, "/api/plugins/memory/items", input.toJson())
    return parseHanakoMemoryItem(root.optJSONObject("item")) ?: error("Memory item missing from response")
  }

  fun updateMemoryItem(info: HanakoServerInfo, input: HanakoMemoryInput): HanakoMemoryItem {
    val id = input.id.trim()
    require(id.isNotBlank()) { "Memory id is required" }
    val root = postJson(
      info,
      "/api/plugins/memory/items/${nbgEncodeUrlPathSegment(id)}",
      input.toJson(),
      method = "PUT",
    )
    return parseHanakoMemoryItem(root.optJSONObject("item")) ?: error("Memory item missing from response")
  }

  fun deleteMemoryItem(info: HanakoServerInfo, id: String) {
    requestJson(
      info,
      "DELETE",
      "/api/plugins/memory/items/${nbgEncodeUrlPathSegment(id)}",
      null,
    )
  }

  fun getSkills(info: HanakoServerInfo, agentId: String = HANA_DEFAULT_MCP_AGENT_ID): HanakoSkillsSnapshot {
    val encodedAgentId = nbgEncodeUrlPathSegment(agentId)
    val snapshot = parseHanakoSkillsSnapshot(requestJson(info, "GET", "/api/skills?agentId=$encodedAgentId", null))
    return snapshot.copy(
      bundles = runCatching { getSkillBundles(info, agentId) }.getOrDefault(emptyList()),
      externalPaths = runCatching { getExternalSkillPaths(info) }.getOrDefault(HanakoExternalSkillPaths()),
    )
  }

  fun setAgentSkills(info: HanakoServerInfo, agentId: String, enabled: List<String>) {
    postJson(
      info,
      "/api/agents/${nbgEncodeUrlPathSegment(agentId)}/skills",
      JSONObject().put("enabled", JSONArray(enabled)),
      method = "PUT",
    )
  }

  fun ensureAndroidBundledSkillsEnabled(
    info: HanakoServerInfo,
    verifiedBundledSkillNames: List<String>,
    agentId: String = HANA_DEFAULT_MCP_AGENT_ID,
  ) {
    reloadSkills(info)
    val snapshot = getSkills(info, agentId)
    val current = snapshot.visibleSkills.filter { it.enabled }.map { it.name }
    val next = nbgMergeTrustedBundledSkillEnablement(snapshot.visibleSkills, verifiedBundledSkillNames)
    if (next != current) setAgentSkills(info, agentId, next)
  }

  fun installSkill(info: HanakoServerInfo, input: HanakoSkillInstallInput, agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    val encodedAgentId = nbgEncodeUrlPathSegment(agentId)
    postJson(info, "/api/skills/install?agentId=$encodedAgentId", input.toJson())
  }

  fun deleteSkill(info: HanakoServerInfo, name: String, agentId: String = HANA_DEFAULT_MCP_AGENT_ID) {
    val encodedAgentId = nbgEncodeUrlPathSegment(agentId)
    requestJson(
      info,
      "DELETE",
      "/api/skills/${nbgEncodeUrlPathSegment(name)}?agentId=$encodedAgentId",
      null,
    )
  }

  fun reloadSkills(info: HanakoServerInfo) {
    postJson(info, "/api/skills/reload")
  }

  fun getSkillBundles(info: HanakoServerInfo, agentId: String = HANA_DEFAULT_MCP_AGENT_ID): List<HanakoSkillBundle> {
    val encodedAgentId = nbgEncodeUrlPathSegment(agentId)
    return parseHanakoSkillBundles(requestJson(info, "GET", "/api/skills/bundles?agentId=$encodedAgentId", null))
  }

  fun createSkillBundle(
    info: HanakoServerInfo,
    input: HanakoSkillBundleInput,
    agentId: String = HANA_DEFAULT_MCP_AGENT_ID,
  ) {
    val encodedAgentId = nbgEncodeUrlPathSegment(agentId)
    postJson(info, "/api/skills/bundles?agentId=$encodedAgentId", input.toJson())
  }

  fun updateSkillBundle(
    info: HanakoServerInfo,
    bundleId: String,
    input: HanakoSkillBundleInput,
    agentId: String = HANA_DEFAULT_MCP_AGENT_ID,
  ) {
    val encodedAgentId = nbgEncodeUrlPathSegment(agentId)
    postJson(
      info,
      "/api/skills/bundles/${nbgEncodeUrlPathSegment(bundleId)}?agentId=$encodedAgentId",
      input.toJson(),
      method = "PUT",
    )
  }

  fun deleteSkillBundle(info: HanakoServerInfo, bundleId: String) {
    requestJson(
      info,
      "DELETE",
      "/api/skills/bundles/${nbgEncodeUrlPathSegment(bundleId)}",
      null,
    )
  }

  fun getExternalSkillPaths(info: HanakoServerInfo): HanakoExternalSkillPaths =
    parseHanakoExternalSkillPaths(requestValue(info, "GET", "/api/skills/external-paths", null))

  fun setExternalSkillPaths(info: HanakoServerInfo, input: HanakoExternalSkillPathsInput) {
    postJson(
      info,
      "/api/skills/external-paths",
      input.toJson(),
      method = "PUT",
    )
  }

  fun getTerminalSlice(
    info: HanakoServerInfo,
    terminal: HanakoTerminalOutput,
  ): HanakoTerminalOutput {
    val from = terminal.sliceFrom
    val to = terminal.sliceTo
    val query = listOfNotNull(
      from?.let { "from=$it" },
      to?.let { "to=$it" },
    ).joinToString("&")
    val path = buildString {
      append("/api/terminal/")
      append(nbgEncodeUrlPathSegment(terminal.sessionId))
      append("/slice")
      if (query.isNotBlank()) append("?").append(query)
    }
    val root = requestJson(info, "GET", path, null)
    val output = root.rawStringAny("text", "output", "data")
    return terminal.copy(
      output = nbgNormalizeTerminalOutput(output).takeLast(HANA_MOBILE_TERMINAL_OUTPUT_LIMIT),
      staticOutput = true,
      alive = if (root.has("alive") && !root.isNull("alive")) root.optBoolean("alive") else terminal.alive,
      exitCode = root.optIntOrNull("exitCode") ?: terminal.exitCode,
      truncated = terminal.truncated || root.optBoolean("truncatedStart", false) || root.optBoolean("truncated", false),
      sliceFrom = from,
      sliceTo = root.optIntOrNull("cursor") ?: to,
    )
  }

  fun getTerminalSnapshot(
    info: HanakoServerInfo,
    terminal: HanakoTerminalOutput,
    tailBytes: Int = HANA_MOBILE_TERMINAL_OUTPUT_LIMIT,
  ): HanakoTerminalOutput {
    val root = requestJson(
      info,
      "GET",
      "/api/terminal/${nbgEncodeUrlPathSegment(terminal.sessionId)}/snapshot?tail=$tailBytes",
      null,
    )
    return terminal.copy(
      output = nbgNormalizeTerminalOutput(root.rawStringAny("output", "text", "data")).takeLast(HANA_MOBILE_TERMINAL_OUTPUT_LIMIT),
      staticOutput = true,
      alive = if (root.has("alive") && !root.isNull("alive")) root.optBoolean("alive") else terminal.alive,
      exitCode = root.optIntOrNull("exitCode") ?: terminal.exitCode,
      truncated = terminal.truncated,
      sliceTo = root.optIntOrNull("cursor") ?: terminal.sliceTo,
    )
  }

  fun interruptTerminalByHuman(info: HanakoServerInfo, terminalId: String) {
    postJson(
      info,
      "/api/terminal/${nbgEncodeUrlPathSegment(terminalId)}/interrupt-by-human",
    )
  }

  private fun putAgentMcpConnectorPatch(
    info: HanakoServerInfo,
    agentId: String,
    connectorId: String,
    patch: JSONObject,
  ) {
    postJson(
      info,
      "/api/plugins/mcp/agents/${nbgEncodeUrlPathSegment(agentId)}/connectors/${nbgEncodeUrlPathSegment(connectorId)}",
      patch,
      method = "PUT",
    )
  }

  fun checkModelHealth(info: HanakoServerInfo, modelId: String, provider: String): HanakoModelHealth {
    val root = postJson(
      info,
      "/api/models/health",
      JSONObject()
        .put("modelId", modelId)
        .put("provider", provider),
    )
    return HanakoModelHealth(
      modelId = modelId,
      provider = root.cleanString("provider") ?: provider,
      ok = root.optBoolean("ok", false),
      status = root.optInt("status", 0),
      skipped = root.cleanString("skipped").orEmpty(),
      error = root.cleanString("error").orEmpty(),
      code = root.cleanString("code").orEmpty(),
      reason = root.cleanString("reason").orEmpty(),
    )
  }

  fun listSlashCommands(info: HanakoServerInfo): List<HanakoSlashCommand> {
    val root = requestJson(info, "GET", "/api/commands", null)
    val commands = root.optJSONArray("commands") ?: return emptyList()
    return buildList {
      for (index in 0 until commands.length().coerceAtMost(80)) {
        val item = commands.optJSONObject(index) ?: continue
        val name = item.cleanString("name") ?: continue
        add(
          HanakoSlashCommand(
            name = name,
            aliases = item.optJSONArray("aliases").toStringList(limit = 8),
            description = item.cleanString("description").orEmpty(),
            permission = item.cleanString("permission").orEmpty(),
            scope = item.cleanString("scope") ?: "session",
            source = item.cleanString("source") ?: "core",
          ),
        )
      }
    }.sortedWith(compareBy<HanakoSlashCommand> { it.source != "core" }.thenBy { it.name })
  }

  fun switchAgent(info: HanakoServerInfo, agentId: String): JSONObject =
    postJson(
      info,
      "/api/agents/switch",
      JSONObject().put("id", agentId),
    )

  fun setDefaultModel(info: HanakoServerInfo, modelId: String, provider: String): JSONObject =
    postJson(
      info,
      "/api/models/set",
      JSONObject()
        .put("modelId", modelId)
        .put("provider", provider),
    )

  fun switchSessionModel(info: HanakoServerInfo, sessionPath: String, modelId: String, provider: String): JSONObject =
    postJson(
      info,
      "/api/models/switch",
      JSONObject()
        .put("sessionPath", sessionPath)
        .put("modelId", modelId)
        .put("provider", provider),
    )

  fun setSessionPermissionMode(
    info: HanakoServerInfo,
    mode: String,
    sessionPath: String?,
  ): HanakoPermissionModeState {
    val body = JSONObject()
      .put("mode", mode)
      .put("currentSessionOnly", !sessionPath.isNullOrBlank())
    if (!sessionPath.isNullOrBlank()) body.put("sessionPath", sessionPath)
    val root = postJson(info, "/api/session-permission-mode", body)
    return HanakoPermissionModeState(
      mode = root.cleanString("mode") ?: mode,
      accessMode = root.cleanString("accessMode").orEmpty(),
      defaultMode = root.cleanString("defaultMode").orEmpty(),
    )
  }

  fun setThinkingLevel(
    info: HanakoServerInfo,
    level: String,
    sessionPath: String?,
  ): HanakoThinkingLevelState {
    val root = if (sessionPath.isNullOrBlank()) {
      postJson(info, "/api/config", JSONObject().put("thinking_level", level), method = "PUT")
    } else {
      postJson(
        info,
        "/api/session-thinking-level",
        JSONObject()
          .put("sessionPath", sessionPath)
          .put("level", level),
      )
    }
    return HanakoThinkingLevelState(root.cleanString("thinkingLevel") ?: root.cleanString("thinking_level") ?: level)
  }

  fun searchSessions(info: HanakoServerInfo, query: String): List<HanakoSessionSummary> {
    val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
    val root = requestValue(info, "GET", "/api/sessions/search?q=$encoded", null) as? JSONObject
      ?: return emptyList()
    val results = root.optJSONArray("results") ?: return emptyList()
    return buildList {
      for (index in 0 until results.length()) {
        val item = results.optJSONObject(index) ?: continue
        val path = item.optString("path").orEmpty()
        if (path.isBlank()) continue
        val title = item.cleanString("title")
          ?: item.cleanString("firstMessage")
          ?: "搜索结果"
        val subtitle = listOfNotNull(
          item.cleanString("agentName"),
          item.cleanString("modified")?.take(10),
        ).joinToString(" / ").ifBlank { "HanakoPro search" }
        add(
          HanakoSessionSummary(
            path = path,
            title = title,
            subtitle = subtitle,
            snippet = item.cleanString("snippet") ?: item.cleanString("firstMessage"),
            matchType = item.cleanString("matchType"),
            pinned = !item.cleanString("pinnedAt").isNullOrBlank(),
          ),
        )
      }
    }
  }

  fun renameSession(info: HanakoServerInfo, sessionPath: String, title: String) {
    postJson(
      info,
      "/api/sessions/rename",
      JSONObject()
        .put("path", sessionPath)
        .put("title", title),
    )
  }

  fun deleteSessionPermanently(info: HanakoServerInfo, sessionPath: String) {
    postJson(
      info,
      "/api/sessions/archive",
      JSONObject().put("path", sessionPath),
    )
    postJson(
      info,
      "/api/sessions/archived/delete",
      JSONObject().put("path", sessionPath.toArchivedDeletePath()),
    )
  }

  fun resolveConfirmation(info: HanakoServerInfo, confirmId: String, action: String) {
    val encodedId = nbgEncodeUrlPathSegment(confirmId)
    postJson(
      info,
      "/api/confirm/$encodedId",
      JSONObject().put("action", action),
    )
  }

  fun pinSession(info: HanakoServerInfo, sessionPath: String, pinned: Boolean) {
    postJson(
      info,
      "/api/sessions/pin",
      JSONObject()
        .put("path", sessionPath)
        .put("pinned", pinned),
    )
  }

  fun replayLatestUserMessage(info: HanakoServerInfo, sessionPath: String) {
    postJson(
      info,
      "/api/sessions/latest-user-message/replay",
      JSONObject()
        .put("path", sessionPath)
        .put("displayMessage", JSONObject().put("source", "android")),
    )
  }

  fun revertLatestTurn(info: HanakoServerInfo, sessionPath: String, sinceTs: Long): Int {
    val result = postJson(
      info,
      "/api/sessions/revert-turn",
      JSONObject()
        .put("path", sessionPath)
        .apply {
          if (sinceTs > 0L) put("sinceTs", sinceTs)
        },
    )
    return result.optInt("restoredFiles", 0).coerceAtLeast(0)
  }

  fun completeTodos(info: HanakoServerInfo, sessionPath: String) {
    postJson(
      info,
      "/api/sessions/todos/complete",
      JSONObject().put("path", sessionPath),
    )
  }

  fun createTeamTask(info: HanakoServerInfo, sessionPath: String, prompt: String, title: String): JSONObject {
    val promptForModel = prompt.trim()
    return postJson(
      info,
      "/api/team/tasks",
      JSONObject()
        .put("prompt", promptForModel)
        .put("text", promptForModel)
        .put("title", title)
        .put("mode", "auto")
        .put("sessionPath", sessionPath)
        .put("uiContext", androidTeamUiContext(sessionPath))
        .put(
          "displayMessage",
          JSONObject()
            .put("source", "android")
            .put("mode", "team")
            .put("text", prompt),
        ),
    )
  }

  fun abortTeamTask(info: HanakoServerInfo, taskId: String) {
    val encodedTaskId = nbgEncodeUrlPathSegment(taskId)
    postJson(info, "/api/team/tasks/$encodedTaskId/abort")
  }

  fun abortTeamAgent(info: HanakoServerInfo, taskId: String, agentId: String) {
    val encodedTaskId = nbgEncodeUrlPathSegment(taskId)
    val encodedAgentId = nbgEncodeUrlPathSegment(agentId)
    postJson(info, "/api/team/tasks/$encodedTaskId/agents/$encodedAgentId/abort")
  }

  fun compressForkSession(info: HanakoServerInfo, sessionPath: String): JSONObject =
    postJson(
      info,
      "/api/sessions/compress-fork",
      JSONObject().put("sessionPath", sessionPath),
    )

  fun listMessages(info: HanakoServerInfo, sessionPath: String): HanakoHistorySnapshot {
    val encodedPath = java.net.URLEncoder.encode(sessionPath, Charsets.UTF_8.name())
    val value = requestValue(info, "GET", "/api/sessions/messages?path=$encodedPath&limit=200&all=1", null)
    val root = value as? JSONObject ?: return HanakoHistorySnapshot()
    val todos = parseHanakoTodos(root.optJSONArray("todos"))
    val sessionFiles = parseHanakoSessionFiles(root.optJSONArray("sessionFiles"))
    val messages = root.optJSONArray("messages") ?: return HanakoHistorySnapshot(todos = todos, sessionFiles = sessionFiles)
    val history = nbgParseHanakoRemoteMessages(messages).toMutableList()
    val blocks = root.optJSONArray("blocks")
    if (blocks != null) {
      val inserts = mutableMapOf<Int, MutableList<HanakoHistoryMessage>>()
      for (index in 0 until blocks.length()) {
        val rawBlock = blocks.optJSONObject(index) ?: continue
        val afterIndex = rawBlock.optInt("afterIndex", history.lastIndex).coerceIn(-1, history.lastIndex)
        if (rawBlock.optString("type") == "tool_group") {
          parseHanakoToolGroup(rawBlock).forEachIndexed { toolIndex, tool ->
            val toolMessage = HanakoHistoryMessage(
              id = 1_100_000L + index.toLong() * 100L + toolIndex.toLong(),
              role = "tool",
              text = tool.title,
              toolStatus = tool,
            )
            inserts.getOrPut(afterIndex) { mutableListOf() } += toolMessage
          }
          continue
        }
        val block = parseHanakoContentBlock(rawBlock) ?: continue
        val blockMessage = HanakoHistoryMessage(
          id = 1_000_000L + index.toLong(),
          role = "content_block",
          text = block.title,
          contentBlock = block,
        )
        inserts.getOrPut(afterIndex) { mutableListOf() } += blockMessage
      }
      if (inserts.isNotEmpty()) {
        val merged = mutableListOf<HanakoHistoryMessage>()
        inserts[-1]?.let { merged += it }
        history.forEachIndexed { index, message ->
          merged += message
          inserts[index]?.let { merged += it }
        }
        return HanakoHistorySnapshot(messages = merged, todos = todos, sessionFiles = sessionFiles)
      }
    }
    return HanakoHistorySnapshot(messages = history, todos = todos, sessionFiles = sessionFiles)
  }

  private fun requestJson(
    info: HanakoServerInfo,
    method: String,
    path: String,
    body: JSONObject? = null,
  ): JSONObject {
    val value = requestValue(info, method, path, body)
    return value as? JSONObject ?: error("Expected JSON object from $path")
  }

  private fun requestJson(
    info: HanakoServerInfo,
    method: String,
    path: String,
    body: JSONObject? = null,
    readTimeoutMs: Int,
  ): JSONObject {
    val value = requestValue(info, method, path, body, readTimeoutMs)
    return value as? JSONObject ?: error("Expected JSON object from $path")
  }

  private fun requestText(
    info: HanakoServerInfo,
    method: String,
    path: String,
    body: JSONObject? = null,
  ): String =
    requestRaw(info, method, path, body)

  private fun requestValue(
    info: HanakoServerInfo,
    method: String,
    path: String,
    body: JSONObject?,
  ): Any {
    val text = requestRaw(info, method, path, body)
    return parseJsonValue(text)
  }

  private fun requestValue(
    info: HanakoServerInfo,
    method: String,
    path: String,
    body: JSONObject?,
    readTimeoutMs: Int,
  ): Any {
    val text = requestRaw(info, method, path, body, readTimeoutMs)
    return parseJsonValue(text)
  }

  private fun requestRaw(
    info: HanakoServerInfo,
    method: String,
    path: String,
    body: JSONObject?,
  ): String {
    return requestRaw(
      info = info,
      method = method,
      path = path,
      body = body,
      connectTimeoutMs = 8_000,
      readTimeoutMs = path.hanakoReadTimeoutMs(),
    )
  }

  private fun requestRaw(
    info: HanakoServerInfo,
    method: String,
    path: String,
    body: JSONObject?,
    readTimeoutMs: Int,
  ): String =
    requestRaw(
      info = info,
      method = method,
      path = path,
      body = body,
      connectTimeoutMs = 1_500,
      readTimeoutMs = readTimeoutMs,
    )

  private fun requestRaw(
    info: HanakoServerInfo,
    method: String,
    path: String,
    body: JSONObject?,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
  ): String {
    val url = URL("${info.apiBase}$path")
    val conn = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = connectTimeoutMs
      readTimeout = readTimeoutMs
      setRequestProperty("Authorization", "Bearer ${info.token}")
      setRequestProperty("Accept", "application/json")
      if (body != null) {
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
      }
    }
    try {
      if (body != null) {
        conn.outputStream.use { output -> output.write(body.toString().toByteArray(Charsets.UTF_8)) }
      }
      val code = conn.responseCode
      val stream = if (code in 200..299) conn.inputStream else conn.errorStream
      val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
      if (code !in 200..299) error(parseHanakoHttpError(code, text, path))
      return text
    } finally {
      conn.disconnect()
    }
  }

  private fun parseHanakoHttpError(code: Int, text: String, path: String): String {
    return nbgParseHanakoHttpError(code, text, path)
  }

  private fun parseJsonValue(text: String): Any {
    val trimmed = text.trim()
    if (trimmed.startsWith("[")) return JSONArray(trimmed)
    if (trimmed.startsWith("{")) return JSONObject(trimmed)
    error("Invalid JSON response")
  }

  private fun parseAgents(array: JSONArray?): List<HanakoAgentSummary> {
    if (array == null) return emptyList()
    return buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val id = item.cleanString("id") ?: continue
        val chatModel = item.optJSONObject("chatModel")
        val modelLabel = listOfNotNull(
          chatModel?.cleanString("provider"),
          chatModel?.cleanString("id"),
        ).joinToString(" / ")
        add(
          HanakoAgentSummary(
            id = id,
            name = item.cleanString("name") ?: id,
            identity = item.cleanString("identity").orEmpty(),
            modelLabel = modelLabel,
            isCurrent = item.optBoolean("isCurrent", false),
            isPrimary = item.optBoolean("isPrimary", false),
          ),
        )
      }
    }
  }

  private fun parseModels(array: JSONArray?): List<HanakoModelSummary> {
    if (array == null) return emptyList()
    return buildList {
      for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val id = item.cleanString("id") ?: continue
        val provider = item.cleanString("provider") ?: continue
        val api = item.cleanString("api").orEmpty()
        val baseUrl = item.cleanString("baseUrl")
          ?: item.cleanString("base_url")
          ?: item.cleanString("url")
          ?: ""
        val upstreamThinkingLevels = item.extractThinkingLevels()
        add(
          HanakoModelSummary(
            id = id,
            name = item.cleanString("name") ?: id,
            provider = provider,
            input = item.optJSONArray("input").toStringList(limit = 8),
            thinkingLevels = nbgSupportedThinkingLevelsForModel(
              modelId = id,
              provider = provider,
              baseUrl = baseUrl,
              api = api,
              upstreamLevels = upstreamThinkingLevels,
            ),
            thinkingSource = item.cleanString("thinkingSource")
              ?: item.cleanString("thinking_source")
              ?: item.cleanString("reasoningSource")
              ?: item.cleanString("reasoning_source")
              ?: nbgThinkingSourceForModel(id, provider, baseUrl, api),
            contextWindow = item.nbgModelContextWindow(id),
            isCurrent = item.optBoolean("isCurrent", false),
          ),
        )
      }
    }
  }

  private fun parseProviders(root: JSONObject?): List<HanakoProviderSummary> {
    if (root == null) return emptyList()
    return buildList {
      val keys = root.keys()
      while (keys.hasNext()) {
        val id = keys.next()
        val item = root.optJSONObject(id) ?: continue
        val models = item.optJSONArray("models")
        val customModels = item.optJSONArray("custom_models")
        add(
          HanakoProviderSummary(
            id = id,
            displayName = item.cleanString("display_name") ?: id,
            type = item.cleanString("type").orEmpty(),
            authType = item.cleanString("auth_type").orEmpty(),
            api = item.cleanString("api").orEmpty(),
            configStatus = item.cleanString("config_status").orEmpty(),
            hasCredentials = item.optBoolean("has_credentials", false),
            loggedIn = if (item.has("logged_in") && !item.isNull("logged_in")) item.optBoolean("logged_in") else null,
            supportsOauth = item.optBoolean("supports_oauth", false),
            isCodingPlan = item.optBoolean("is_coding_plan", false),
            canDelete = item.optBoolean("can_delete", false),
            modelCount = models?.length() ?: 0,
            customModelCount = customModels?.length() ?: 0,
            missingFields = item.optJSONArray("missing_fields").toStringList(limit = 8),
            configError = item.cleanString("config_error").orEmpty(),
          ),
        )
      }
    }.sortedWith(
      compareBy<HanakoProviderSummary> { it.configStatus != "ok" }
        .thenByDescending { it.hasCredentials }
        .thenBy { it.displayName.lowercase() },
    )
  }

  private fun JSONObject?.modelLabel(name: String): String {
    if (this == null || isNull(name)) return ""
    val value = opt(name)
    return when (value) {
      is JSONObject -> listOfNotNull(
        value.cleanString("provider"),
        value.cleanString("id") ?: value.cleanString("model") ?: value.cleanString("name"),
      ).joinToString(" / ")
      is String -> value.trim().takeUnless {
        it.isBlank() || it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true)
      }.orEmpty()
      else -> ""
    }
  }

  private fun String.isLongRunningHanakoRequest(): Boolean =
    contains("/api/sessions/latest-user-message/replay") ||
      contains("/api/sessions/revert-turn") ||
      contains("/api/sessions/compress-fork")

  private fun String.isSessionManagementHanakoRequest(): Boolean =
    contains("/api/sessions/new") ||
      contains("/api/sessions/switch")

  private fun String.hanakoReadTimeoutMs(): Int =
    when {
      isLongRunningHanakoRequest() -> 180_000
      isSessionManagementHanakoRequest() -> 75_000
      else -> 20_000
    }

  private fun androidTeamUiContext(sessionPath: String): JSONObject =
    JSONObject()
      .put("currentViewed", "android-chat")
      .put("locale", NBG_ANDROID_LANGUAGE_LOCALE)
      .put("language", NBG_ANDROID_LANGUAGE_LOCALE)
      .put("activeFile", JSONObject.NULL)
      .put("activePreview", JSONObject.NULL)
      .put("sessionPath", sessionPath)
      .put("pinnedFiles", JSONArray())

  private fun JSONArray?.toStringList(limit: Int): List<String> {
    if (this == null) return emptyList()
    return buildList {
      for (index in 0 until length()) {
        if (size >= limit) break
        val value = optString(index).trim()
        if (value.isNotBlank() && value != "null" && value != "undefined") add(value)
      }
    }
  }

  private fun JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return buildSet {
      for (index in 0 until length()) {
        val value = optString(index).trim()
        if (value.isNotBlank() && value != "null" && value != "undefined") add(value)
      }
    }
  }

  private fun JSONObject.extractThinkingLevels(): List<String> {
    val direct = optJSONArray("thinkingLevels").toStringList(limit = 8)
      .ifEmpty { optJSONArray("thinking_levels").toStringList(limit = 8) }
      .ifEmpty { optJSONArray("reasoningLevels").toStringList(limit = 8) }
      .ifEmpty { optJSONArray("reasoning_levels").toStringList(limit = 8) }
      .ifEmpty { optJSONArray("supportedThinkingLevels").toStringList(limit = 8) }
      .ifEmpty { optJSONArray("supported_thinking_levels").toStringList(limit = 8) }
      .ifEmpty { optJSONArray("supportedReasoningLevels").toStringList(limit = 8) }
      .ifEmpty { optJSONArray("supported_reasoning_levels").toStringList(limit = 8) }
    if (direct.isNotEmpty()) return direct

    val capability = optJSONObject("reasoning") ?: optJSONObject("thinking") ?: optJSONObject("capabilities")
    val nested = capability?.optJSONArray("levels").toStringList(limit = 8)
      .ifEmpty { capability?.optJSONArray("thinkingLevels").toStringList(limit = 8) }
      .ifEmpty { capability?.optJSONArray("thinking_levels").toStringList(limit = 8) }
      .ifEmpty { capability?.optJSONArray("reasoningLevels").toStringList(limit = 8) }
      .ifEmpty { capability?.optJSONArray("reasoning_levels").toStringList(limit = 8) }
    if (nested.isNotEmpty()) return nested

    val supportsThinking = optBoolean("supportsThinking", false) ||
      optBoolean("supports_thinking", false) ||
      optBoolean("supportsReasoning", false) ||
      optBoolean("supports_reasoning", false) ||
      capability?.optBoolean("supported", false) == true ||
      capability?.optBoolean("enabled", false) == true
    return if (supportsThinking) listOf("off", "auto", "low", "medium", "high", "xhigh") else emptyList()
  }
}

package com.nbg.android

import org.json.JSONArray
import org.json.JSONObject

internal const val HANA_DEFAULT_MCP_AGENT_ID = "hanako"
internal const val NBG_ENGINEERING_CORE_SKILL_NAME = "nbg-engineering-core"
internal val NBG_ANDROID_DEFAULT_ENABLED_SKILLS = listOf(
  NBG_ENGINEERING_CORE_SKILL_NAME,
)
internal val NBG_ANDROID_REMOVED_BUILTIN_SKILLS = setOf(
  "office-documents",
  "quiet-musing",
  "research-platform",
  "user-guide",
  "hana-plugin-creator",
  "skill-creator",
)

data class HanakoMcpTool(
  val name: String,
  val title: String = "",
  val description: String = "",
) {
  val displayName: String
    get() = title.ifBlank { name }
}

data class HanakoMcpConnector(
  val id: String,
  val name: String,
  val description: String = "",
  val transport: String = "remote",
  val url: String = "",
  val command: String = "",
  val args: List<String> = emptyList(),
  val cwd: String = "",
  val registryUrl: String = "",
  val timeoutMs: Long = 0L,
  val autoStart: Boolean = false,
  val status: String = "stopped",
  val tools: List<HanakoMcpTool> = emptyList(),
  val authType: String = "none",
  val authStatus: String = "",
  val envCount: Int = 0,
  val headersCount: Int = 0,
) {
  val running: Boolean
    get() = status.equals("running", ignoreCase = true)

  val displayName: String
    get() = name.ifBlank { id }

  val target: String
    get() = when {
      url.isNotBlank() -> url
      command.isNotBlank() -> listOf(command, args.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" ")
      registryUrl.isNotBlank() -> registryUrl
      else -> id
    }
}

data class HanakoMcpConnectorInput(
  val name: String,
  val url: String = "",
  val transport: String = "sse",
  val description: String = "",
  val command: String = "",
  val args: List<String> = emptyList(),
  val cwd: String = "",
  val autoStart: Boolean = true,
  val timeoutSeconds: Int = 30,
) {
  val id: String
    get() = nbgMcpConnectorId(name.ifBlank { url.ifBlank { command } })
}

data class HanakoMcpAgentConnectorConfig(
  val enabled: Boolean? = null,
  val tools: Map<String, Boolean> = emptyMap(),
)

data class HanakoMcpState(
  val enabled: Boolean = false,
  val connectors: List<HanakoMcpConnector> = emptyList(),
  val agentConnectors: Map<String, HanakoMcpAgentConnectorConfig> = emptyMap(),
) {
  fun connectorConfig(connectorId: String): HanakoMcpAgentConnectorConfig =
    agentConnectors[connectorId] ?: HanakoMcpAgentConnectorConfig()

  fun connectorEnabled(connectorId: String): Boolean =
    connectorConfig(connectorId).enabled == true

  fun toolEnabled(connectorId: String, toolName: String): Boolean =
    connectorEnabled(connectorId) && connectorConfig(connectorId).tools[toolName] == true
}

internal fun parseHanakoMcpState(root: JSONObject): HanakoMcpState {
  val connectors = root.optJSONArray("connectors")
    ?: root.optJSONArray("servers")
    ?: JSONArray()
  return HanakoMcpState(
    enabled = root.optBoolean("enabled", false),
    connectors = connectors.toMcpConnectors(),
    agentConnectors = root.optJSONObject("agentConfig").toMcpAgentConnectorConfigs(),
  )
}

internal fun HanakoMcpConnectorInput.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("name", name.trim().ifBlank { id })
    .put("description", description.trim())
    .put("transport", transport.ifBlank { if (command.isNotBlank()) "stdio" else "sse" })
    .put("url", url.trim())
    .put("command", command.trim())
    .put("args", JSONArray(args.map { it.trim() }.filter { it.isNotBlank() }))
    .put("cwd", cwd.trim())
    .put("timeout", timeoutSeconds.coerceIn(5, 300))
    .put("autoStart", autoStart)

internal fun nbgMcpConnectorId(raw: String): String =
  raw.lowercase()
    .replace(Regex("https?://"), "")
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .take(48)
    .ifBlank { "mcp-connector" }

private fun JSONArray.toMcpConnectors(): List<HanakoMcpConnector> =
  buildList {
    for (index in 0 until length()) {
      val item = optJSONObject(index) ?: continue
      val id = item.cleanString("id") ?: continue
      add(
        HanakoMcpConnector(
          id = id,
          name = item.cleanString("name") ?: id,
          description = item.cleanString("description").orEmpty(),
          transport = item.cleanString("transport") ?: "remote",
          url = item.cleanString("url").orEmpty(),
          command = item.cleanString("command").orEmpty(),
          args = item.optJSONArray("args").toMcpStringList(limit = 24),
          cwd = item.cleanString("cwd").orEmpty(),
          registryUrl = item.cleanString("registryUrl") ?: item.cleanString("registry_url").orEmpty(),
          timeoutMs = item.optLong("timeout", 0L).coerceAtLeast(0L),
          autoStart = item.optBoolean("autoStart", false),
          status = item.cleanString("status") ?: "stopped",
          tools = item.optJSONArray("tools").toMcpTools(),
          authType = item.cleanString("authType") ?: "none",
          authStatus = item.cleanString("authStatus").orEmpty(),
          envCount = item.optJSONObject("env").jsonObjectSize(),
          headersCount = item.optJSONObject("headers").jsonObjectSize(),
        ),
      )
    }
  }

private fun JSONArray?.toMcpTools(): List<HanakoMcpTool> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index)
      if (item == null) {
        val name = array.optString(index).trim()
        if (name.isNotBlank() && name != "null" && name != "undefined") add(HanakoMcpTool(name = name))
        continue
      }
      val name = item.cleanString("name") ?: continue
      add(
        HanakoMcpTool(
          name = name,
          title = item.cleanString("title") ?: item.cleanString("label").orEmpty(),
          description = item.cleanString("description").orEmpty(),
        ),
      )
    }
  }
}

private fun JSONObject?.toMcpAgentConnectorConfigs(): Map<String, HanakoMcpAgentConnectorConfig> {
  val agentConfig = this ?: return emptyMap()
  val connectors = agentConfig.optJSONObject("connectors")
    ?: agentConfig.optJSONObject("servers")
    ?: return emptyMap()
  return buildMap {
    val keys = connectors.keys()
    while (keys.hasNext()) {
      val connectorId = keys.next()
      val item = connectors.optJSONObject(connectorId) ?: continue
      val enabled = if (item.has("enabled") && !item.isNull("enabled")) item.optBoolean("enabled") else null
      put(
        connectorId,
        HanakoMcpAgentConnectorConfig(
          enabled = enabled,
          tools = item.optJSONObject("tools").toBooleanMap(),
        ),
      )
    }
  }
}

private fun JSONObject?.toBooleanMap(): Map<String, Boolean> {
  val root = this ?: return emptyMap()
  return buildMap {
    val keys = root.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      if (!root.isNull(key)) put(key, root.optBoolean(key, false))
    }
  }
}

private fun JSONArray?.toMcpStringList(limit: Int): List<String> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      if (size >= limit) break
      val value = array.optString(index).trim()
      if (value.isNotBlank() && value != "null" && value != "undefined") add(value)
    }
  }
}

private fun JSONObject?.jsonObjectSize(): Int {
  val root = this ?: return 0
  var count = 0
  val keys = root.keys()
  while (keys.hasNext()) {
    keys.next()
    count += 1
  }
  return count
}

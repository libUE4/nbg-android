package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal const val NBG_GATEWAY_INBOX_STORE_VERSION = "nbg-gateway-inbox-v1"
internal const val NBG_GATEWAY_POLICY_VERSION = "nbg-gateway-policy-v1"
internal const val NBG_GATEWAY_MESSAGE_MAX_CHARS = 4_000

enum class NbgInboundMessageSource(val wireName: String, val label: String) {
  AndroidShareSheet("android_share_sheet", "Android 分享"),
  NotificationReply("notification_reply", "通知回复"),
  LoopbackHttp("loopback_http", "本机 HTTP"),
  McpConnector("mcp_connector", "MCP Connector"),
  ExternalPlatform("external_platform", "外部平台"),
  Unknown("unknown", "未知来源"),
}

enum class NbgGatewayInboxStatus(val wireName: String, val label: String) {
  Received("received", "已接收"),
  Accepted("accepted", "已接受"),
  Blocked("blocked", "已阻止"),
  Archived("archived", "已归档"),
}

data class NbgGatewayInboxMessage(
  val id: String,
  val source: NbgInboundMessageSource,
  val senderLabel: String,
  val text: String,
  val canExecuteTools: Boolean = false,
  val status: NbgGatewayInboxStatus = NbgGatewayInboxStatus.Received,
  val permissionTier: NbgPermissionRiskTier = NbgPermissionRiskTier.Low,
  val receivedAtMs: Long = 0L,
  val updatedAtMs: Long = 0L,
)

data class NbgGatewayPolicyReview(
  val policyVersion: String,
  val allowReceive: Boolean,
  val allowToolExecution: Boolean,
  val requiresConfirmation: Boolean,
  val source: NbgInboundMessageSource,
  val permissionTier: NbgPermissionRiskTier,
  val reason: String,
)

data class NbgGatewayInboxState(
  val messages: List<NbgGatewayInboxMessage> = emptyList(),
  val modelVersion: String = NBG_GATEWAY_INBOX_STORE_VERSION,
) {
  val activeMessages: List<NbgGatewayInboxMessage>
    get() = messages.filterNot { it.status == NbgGatewayInboxStatus.Archived }

  val blockedCount: Int
    get() = messages.count { it.status == NbgGatewayInboxStatus.Blocked }

  val executableCount: Int
    get() = activeMessages.count { it.canExecuteTools }
}

internal interface NbgGatewayInboxStorage {
  fun read(): String?
  fun write(raw: String)
}

internal class NbgSharedPreferencesGatewayInboxStorage(context: Context) : NbgGatewayInboxStorage {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  override fun read(): String? =
    prefs.getString(KEY_INBOX, null)

  override fun write(raw: String) {
    prefs.edit().putString(KEY_INBOX, raw).apply()
  }

  private companion object {
    const val PREFS = "nbg_gateway_inbox"
    const val KEY_INBOX = "inbox"
  }
}

internal class NbgGatewayInboxStore(
  private val storage: NbgGatewayInboxStorage,
) {
  constructor(context: Context) : this(NbgSharedPreferencesGatewayInboxStorage(context))

  fun load(): NbgGatewayInboxState =
    parseNbgGatewayInboxState(storage.read())

  fun save(state: NbgGatewayInboxState): NbgGatewayInboxState {
    val normalized = nbgBuildGatewayInboxState(state.messages)
    storage.write(normalized.toJsonString())
    return normalized
  }

  fun record(message: NbgGatewayInboxMessage): NbgGatewayInboxState =
    save(NbgGatewayInboxState(load().messages + nbgGatewayMessageAfterPolicy(message)))

  fun archive(id: String, nowMs: Long = System.currentTimeMillis()): NbgGatewayInboxState {
    val cleanId = id.trim()
    if (cleanId.isBlank()) return load()
    return save(
      load().copy(
        messages = load().messages.map {
          if (it.id == cleanId) it.copy(status = NbgGatewayInboxStatus.Archived, updatedAtMs = nowMs.coerceAtLeast(it.updatedAtMs)) else it
        },
      ),
    )
  }
}

internal fun nbgReviewGatewayMessage(message: NbgGatewayInboxMessage): NbgGatewayPolicyReview {
  val text = message.text.trim()
  val sensitive = nbgMemorySensitiveFindings(text)
  val tier = nbgHighestPermissionRiskTier(
    message.permissionTier,
    nbgPermissionRiskForAction("gateway_message", message.senderLabel, text).tier,
  )
  if (text.isBlank()) {
    return nbgGatewayBlocked(message.source, tier, "外部消息内容不能为空。")
  }
  if (text.length > NBG_GATEWAY_MESSAGE_MAX_CHARS) {
    return nbgGatewayBlocked(message.source, tier, "外部消息内容过长。")
  }
  if (sensitive.isNotEmpty()) {
    return nbgGatewayBlocked(message.source, tier, "外部消息疑似包含敏感信息：${sensitive.joinToString(", ")}。")
  }
  val trustedLocalSource = message.source in setOf(
    NbgInboundMessageSource.AndroidShareSheet,
    NbgInboundMessageSource.NotificationReply,
    NbgInboundMessageSource.LoopbackHttp,
  )
  val allowToolExecution = trustedLocalSource && tier.ordinal <= NbgPermissionRiskTier.Medium.ordinal && message.canExecuteTools
  return NbgGatewayPolicyReview(
    policyVersion = NBG_GATEWAY_POLICY_VERSION,
    allowReceive = true,
    allowToolExecution = allowToolExecution,
    requiresConfirmation = !allowToolExecution && message.canExecuteTools,
    source = message.source,
    permissionTier = tier,
    reason = when {
      allowToolExecution -> "本地可信入口的低/中风险消息可进入工具执行队列。"
      message.canExecuteTools -> "外部或高风险入口必须确认后才能执行工具。"
      else -> "消息可作为普通聊天输入接收，不授予工具执行权限。"
    },
  )
}

internal fun nbgGatewayInboxMessage(
  source: NbgInboundMessageSource,
  senderLabel: String,
  text: String,
  canExecuteTools: Boolean = false,
  id: String = "",
  nowMs: Long = System.currentTimeMillis(),
): NbgGatewayInboxMessage {
  val base = NbgGatewayInboxMessage(
    id = id.trim().ifBlank { listOf(source.wireName, senderLabel, text, nowMs.toString()).joinToString("\u001f").sha256Hex().take(24) },
    source = source,
    senderLabel = senderLabel.nbgGatewayCompact(120),
    text = text.nbgGatewayCompact(NBG_GATEWAY_MESSAGE_MAX_CHARS),
    canExecuteTools = canExecuteTools,
    receivedAtMs = nowMs.coerceAtLeast(0L),
    updatedAtMs = nowMs.coerceAtLeast(0L),
  )
  return nbgGatewayMessageAfterPolicy(base)
}

internal fun nbgBuildGatewayInboxState(messages: List<NbgGatewayInboxMessage>): NbgGatewayInboxState {
  val normalized = messages
    .map(::nbgGatewayMessageAfterPolicy)
    .filter { it.id.isNotBlank() && it.text.isNotBlank() }
    .groupBy { it.id }
    .mapNotNull { (_, grouped) -> grouped.maxWithOrNull(compareBy<NbgGatewayInboxMessage> { it.updatedAtMs }.thenBy { it.receivedAtMs }) }
    .sortedByDescending { it.receivedAtMs }
    .take(200)
  return NbgGatewayInboxState(messages = normalized)
}

internal fun parseNbgGatewayInboxState(raw: String?): NbgGatewayInboxState =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    nbgBuildGatewayInboxState(root.optJSONArray("messages").toGatewayMessages())
  }.getOrDefault(NbgGatewayInboxState())

internal fun NbgGatewayInboxState.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("messages", JSONArray(messages.map { it.toJson() }))
    .toString()

private fun nbgGatewayMessageAfterPolicy(message: NbgGatewayInboxMessage): NbgGatewayInboxMessage {
  val review = nbgReviewGatewayMessage(message)
  return message.copy(
    senderLabel = message.senderLabel.nbgGatewayCompact(120),
    text = message.text.nbgGatewayCompact(NBG_GATEWAY_MESSAGE_MAX_CHARS),
    canExecuteTools = review.allowToolExecution,
    status = when {
      !review.allowReceive -> NbgGatewayInboxStatus.Blocked
      message.status == NbgGatewayInboxStatus.Archived -> NbgGatewayInboxStatus.Archived
      review.allowToolExecution || !message.canExecuteTools -> NbgGatewayInboxStatus.Accepted
      else -> NbgGatewayInboxStatus.Received
    },
    permissionTier = review.permissionTier,
    receivedAtMs = message.receivedAtMs.coerceAtLeast(0L),
    updatedAtMs = message.updatedAtMs.coerceAtLeast(message.receivedAtMs.coerceAtLeast(0L)),
  )
}

private fun nbgGatewayBlocked(
  source: NbgInboundMessageSource,
  tier: NbgPermissionRiskTier,
  reason: String,
): NbgGatewayPolicyReview =
  NbgGatewayPolicyReview(
    policyVersion = NBG_GATEWAY_POLICY_VERSION,
    allowReceive = false,
    allowToolExecution = false,
    requiresConfirmation = true,
    source = source,
    permissionTier = tier,
    reason = reason,
  )

private fun NbgGatewayInboxMessage.toJson(): JSONObject =
  JSONObject()
    .put("id", id)
    .put("source", source.wireName)
    .put("senderLabel", nbgRedactDiagnosticText(senderLabel))
    .put("text", nbgRedactDiagnosticText(text))
    .put("canExecuteTools", canExecuteTools)
    .put("status", status.wireName)
    .put("permissionTier", permissionTier.wireName)
    .put("receivedAtMs", receivedAtMs)
    .put("updatedAtMs", updatedAtMs)

private fun JSONArray?.toGatewayMessages(): List<NbgGatewayInboxMessage> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      add(
        NbgGatewayInboxMessage(
          id = item.cleanString("id").orEmpty(),
          source = nbgInboundMessageSource(item.cleanString("source")),
          senderLabel = item.cleanString("senderLabel").orEmpty(),
          text = item.cleanString("text").orEmpty(),
          canExecuteTools = item.optBoolean("canExecuteTools", false),
          status = nbgGatewayInboxStatus(item.cleanString("status")),
          permissionTier = nbgGatewayPermissionTier(item.cleanString("permissionTier")),
          receivedAtMs = item.optLong("receivedAtMs", 0L),
          updatedAtMs = item.optLong("updatedAtMs", 0L),
        ),
      )
    }
  }
}

private fun nbgInboundMessageSource(raw: String?): NbgInboundMessageSource =
  NbgInboundMessageSource.entries.firstOrNull { it.wireName == raw?.trim()?.lowercase() }
    ?: NbgInboundMessageSource.Unknown

private fun nbgGatewayInboxStatus(raw: String?): NbgGatewayInboxStatus =
  NbgGatewayInboxStatus.entries.firstOrNull { it.wireName == raw?.trim()?.lowercase() }
    ?: NbgGatewayInboxStatus.Received

private fun nbgGatewayPermissionTier(raw: String?): NbgPermissionRiskTier =
  NbgPermissionRiskTier.entries.firstOrNull { it.wireName == raw?.trim()?.lowercase() }
    ?: NbgPermissionRiskTier.Low

private fun String.nbgGatewayCompact(limit: Int): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit.coerceAtLeast(0))

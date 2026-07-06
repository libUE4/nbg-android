package com.nbg.android.mcpserver

internal const val NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION = "nbg-android-mcp-server-v1"
internal const val NBG_ANDROID_MCP_SERVER_NAME = "nbg-android-mcp-server"
internal const val NBG_ANDROID_MCP_SERVER_VERSION = "0.1.0"
internal const val NBG_ANDROID_MCP_PROTOCOL_VERSION = "2025-11-25"
internal const val NBG_ANDROID_MCP_LOOPBACK_HOST = "127.0.0.1"
internal const val NBG_ANDROID_MCP_PORT = 37666
internal const val NBG_ANDROID_MCP_AUTH_SCHEME = "Bearer"
internal const val NBG_ANDROID_MCP_BEARER_TOKEN_BYTES = 32
internal const val NBG_ANDROID_MCP_BEARER_TOKEN_MIN_LENGTH = 32
internal const val NBG_ANDROID_MCP_DISCOVERY_POLICY_VERSION = "nbg-android-mcp-discovery-v1"
internal const val NBG_ANDROID_MCP_SIDE_EFFECT_POLICY_VERSION = "nbg-android-mcp-side-effect-v1"

internal val NBG_ANDROID_MCP_EPHEMERAL_PORT_REQUIRED_EVIDENCE: List<String> =
  listOf("main_app_discovery_contract", "client_migration_plan", "diagnostics_redaction_update")

internal val NBG_ANDROID_MCP_SIDE_EFFECT_REQUIRED_EVIDENCE: List<String> =
  listOf("main_app_permission_ui", "permission_risk_mapping", "diagnostics_redaction_update", "security_review")

internal enum class AndroidMcpToolBoundary {
  ReadOnly,
  EchoOnly,
}

internal data class AndroidMcpToolContract(
  val name: String,
  val title: String,
  val boundary: AndroidMcpToolBoundary,
  val description: String,
)

internal data class AndroidMcpDiscoveryPolicyReview(
  val policyVersion: String,
  val useEphemeralPort: Boolean,
  val port: Int,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val reason: String,
)

internal data class AndroidMcpSideEffectToolPolicyReview(
  val policyVersion: String,
  val allowStandaloneSideEffectTool: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val reason: String,
)

internal val NBG_ANDROID_MCP_TOOLS = listOf(
  AndroidMcpToolContract(
    name = "android_device_info",
    title = "Android Device Info",
    boundary = AndroidMcpToolBoundary.ReadOnly,
    description = "Return basic information from this Android MCP server app.",
  ),
  AndroidMcpToolContract(
    name = "android_echo",
    title = "Android Echo",
    boundary = AndroidMcpToolBoundary.EchoOnly,
    description = "Echo a text value from the Android MCP server.",
  ),
)

internal fun nbgAndroidMcpToolNames(): Set<String> =
  NBG_ANDROID_MCP_TOOLS.map { it.name }.toSet()

internal fun nbgReviewAndroidMcpEphemeralPortMigration(
  mainAppDiscoveryContractPresent: Boolean,
  clientMigrationPlanPresent: Boolean,
  diagnosticsRedactionUpdatePresent: Boolean,
): AndroidMcpDiscoveryPolicyReview {
  val presentEvidence = buildList {
    if (mainAppDiscoveryContractPresent) add("main_app_discovery_contract")
    if (clientMigrationPlanPresent) add("client_migration_plan")
    if (diagnosticsRedactionUpdatePresent) add("diagnostics_redaction_update")
  }
  return AndroidMcpDiscoveryPolicyReview(
    policyVersion = NBG_ANDROID_MCP_DISCOVERY_POLICY_VERSION,
    useEphemeralPort = false,
    port = NBG_ANDROID_MCP_PORT,
    requiredEvidence = NBG_ANDROID_MCP_EPHEMERAL_PORT_REQUIRED_EVIDENCE,
    presentEvidence = presentEvidence,
    reason = "Public beta v1 keeps the fixed loopback MCP port; future ephemeral-port migration requires a main-app discovery contract, client migration plan, and diagnostics redaction update.",
  )
}

internal fun nbgReviewAndroidMcpStandaloneSideEffectTool(
  mainAppPermissionUiPresent: Boolean,
  permissionRiskMappingPresent: Boolean,
  diagnosticsRedactionUpdatePresent: Boolean,
  securityReviewPresent: Boolean,
): AndroidMcpSideEffectToolPolicyReview {
  val presentEvidence = buildList {
    if (mainAppPermissionUiPresent) add("main_app_permission_ui")
    if (permissionRiskMappingPresent) add("permission_risk_mapping")
    if (diagnosticsRedactionUpdatePresent) add("diagnostics_redaction_update")
    if (securityReviewPresent) add("security_review")
  }
  return AndroidMcpSideEffectToolPolicyReview(
    policyVersion = NBG_ANDROID_MCP_SIDE_EFFECT_POLICY_VERSION,
    allowStandaloneSideEffectTool = false,
    requiredEvidence = NBG_ANDROID_MCP_SIDE_EFFECT_REQUIRED_EVIDENCE,
    presentEvidence = presentEvidence,
    reason = "Public beta v1 keeps the standalone Android MCP server read-only/echo-only; future side-effect tools belong behind the main app permission UI, permission-risk mapping, diagnostics redaction, and Security review.",
  )
}

internal fun nbgAndroidMcpRequestAuthorized(headers: Map<String, String>, expectedToken: String): Boolean {
  val token = expectedToken.trim()
  if (token.length < NBG_ANDROID_MCP_BEARER_TOKEN_MIN_LENGTH) return false
  val header = headers["authorization"] ?: headers["Authorization"] ?: return false
  val parts = header.trim().split(Regex("\\s+"), limit = 2)
  if (parts.size != 2 || !parts[0].equals(NBG_ANDROID_MCP_AUTH_SCHEME, ignoreCase = true)) return false
  return nbgAndroidMcpConstantTimeEquals(parts[1].trim(), token)
}

private fun nbgAndroidMcpConstantTimeEquals(left: String, right: String): Boolean {
  var diff = left.length xor right.length
  val max = maxOf(left.length, right.length)
  for (index in 0 until max) {
    val a = left.getOrNull(index)?.code ?: 0
    val b = right.getOrNull(index)?.code ?: 0
    diff = diff or (a xor b)
  }
  return diff == 0
}

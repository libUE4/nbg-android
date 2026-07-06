package com.nbg.android

import org.json.JSONObject

const val NBG_PERMISSION_MODE_OPERATE = "operate"
const val NBG_PERMISSION_MODE_ASK = "ask"
const val NBG_PERMISSION_MODE_READ_ONLY = "read_only"
const val NBG_DEFAULT_PERMISSION_MODE = NBG_PERMISSION_MODE_ASK

enum class NbgPermissionRiskTier(
  val wireName: String,
  val label: String,
  val requiresConfirmation: Boolean,
  val strongConfirmation: Boolean,
) {
  Low("low", "低风险", false, false),
  Medium("medium", "中风险", true, false),
  High("high", "高风险", true, false),
  Dangerous("dangerous", "危险操作", true, true),
}

data class NbgPermissionRisk(
  val tier: NbgPermissionRiskTier,
  val actionLabel: String,
  val targetLabel: String = "",
  val recoveryHint: String = "",
) {
  val requiresConfirmation: Boolean
    get() = tier.requiresConfirmation
  val strongConfirmation: Boolean
    get() = tier.strongConfirmation
}

fun nbgNormalizePermissionMode(mode: String?): String =
  mode?.trim()?.takeIf { it in NBG_VALID_PERMISSION_MODES } ?: NBG_DEFAULT_PERMISSION_MODE

fun nbgPermissionRiskForAction(
  action: String,
  target: String = "",
  command: String = "",
): NbgPermissionRisk {
  val text = listOf(action, target, command).joinToString(" ").lowercase()
  val tier = when {
    text.hasAny("rm -rf", "rm -fr", "mkfs", "dd if=", "shutdown", "reboot", "wipe", "format disk") -> NbgPermissionRiskTier.Dangerous
    text.hasAny("delete", "remove", "unlink", "rmdir", "trash", "git reset --hard") -> NbgPermissionRiskTier.Dangerous
    text.hasAny("write", "overwrite", "edit", "patch", "apply_patch", "save file", "rename", "move file", "chmod", "chown") -> NbgPermissionRiskTier.High
    text.hasAny("install skill", "external skill", "petdex", "install pet", "mcp install", "plugin install") -> NbgPermissionRiskTier.High
    text.hasAny("memory_delete", "delete memory", "memory delete", "remove memory") -> NbgPermissionRiskTier.Dangerous
    text.hasAny("memory_save", "memory_update", "memory save", "save memory", "memory write", "store memory", "skill update", "skill delete") -> NbgPermissionRiskTier.High
    text.hasAny("mcp tools/call", "mcp tool", "tools/call") -> NbgPermissionRiskTier.High
    text.hasAny("shell", "terminal", "bash", "command", "exec") -> NbgPermissionRiskTier.Medium
    text.hasAny("read", "list", "search", "view", "inspect", "echo", "device_info") -> NbgPermissionRiskTier.Low
    else -> NbgPermissionRiskTier.Medium
  }
  return NbgPermissionRisk(
    tier = tier,
    actionLabel = action.trim().ifBlank { "工具操作" },
    targetLabel = target.trim(),
    recoveryHint = nbgPermissionRecoveryHintForTier(tier),
  )
}

internal fun nbgPermissionRiskForConfirmationBlock(block: JSONObject): NbgPermissionRisk {
  val subject = block.optJSONObject("subject")
  val payload = block.optJSONObject("payload")
  val payloadParams = payload?.optJSONObject("params")
  val riskSchema = nbgPermissionRiskSchemaObject(block)
  val payloadRiskText = nbgPermissionRiskPayloadText(payload, payloadParams)
  val schemaTarget = nbgPermissionRiskSchemaString(riskSchema, "target", "targetLabel", "path", "url", "resource")
  val action = block.optString("action")
    .ifBlank { block.optString("toolName") }
    .ifBlank { payload?.optString("toolName").orEmpty() }
    .ifBlank { payload?.optString("tool").orEmpty() }
    .ifBlank { payload?.optString("name").orEmpty() }
    .ifBlank { block.optString("title") }
  val target = subject?.optString("detail").orEmpty()
    .ifBlank { subject?.optString("label").orEmpty() }
    .ifBlank { block.optString("target") }
  val inferred = nbgPermissionRiskForAction(
    action = action,
    target = target,
    command = listOf(block.optString("command"), payloadRiskText)
      .filter { it.isNotBlank() }
      .joinToString(" "),
  )
  val schemaInferred = nbgPermissionRiskForSchema(riskSchema)
  val declaredOrSeverityTier = nbgPermissionDeclaredRiskTier(block, riskSchema)
  val tier = nbgHighestPermissionRiskTier(inferred.tier, declaredOrSeverityTier, schemaInferred?.tier)
  val backendRecoveryHint = block.optString("recoveryHint")
    .ifBlank { nbgPermissionRiskSchemaString(riskSchema, "recoveryHint", "recovery", "mitigation", "hint") }
  val backendHintTier = nbgHighestPermissionRiskTierOrNull(declaredOrSeverityTier, schemaInferred?.tier)
  val canUseBackendHint = backendRecoveryHint.isNotBlank() && when {
    backendHintTier != null -> backendHintTier.ordinal >= inferred.tier.ordinal
    inferred.tier.ordinal <= NbgPermissionRiskTier.Medium.ordinal -> true
    else -> false
  }
  return inferred.copy(
    tier = tier,
    targetLabel = inferred.targetLabel.ifBlank { schemaTarget },
    recoveryHint = if (canUseBackendHint) backendRecoveryHint else nbgPermissionRecoveryHintForTier(tier),
  )
}

private fun nbgHighestPermissionRiskTier(vararg tiers: NbgPermissionRiskTier?): NbgPermissionRiskTier =
  listOfNotNull(*tiers).maxBy { it.ordinal }

private fun nbgHighestPermissionRiskTierOrNull(vararg tiers: NbgPermissionRiskTier?): NbgPermissionRiskTier? =
  listOfNotNull(*tiers).maxByOrNull { it.ordinal }

private fun nbgPermissionRecoveryHintForTier(tier: NbgPermissionRiskTier): String =
  when (tier) {
    NbgPermissionRiskTier.Dangerous -> "确认前请检查目标；拒绝不会执行该危险操作。"
    NbgPermissionRiskTier.High -> "确认前请检查目标和变更内容；拒绝后可让 Agent 重新计划。"
    NbgPermissionRiskTier.Medium -> "必要时可切换到计划模式，只让 Agent 分析。"
    NbgPermissionRiskTier.Low -> ""
  }

private fun nbgPermissionDeclaredRiskTier(
  block: JSONObject,
  riskSchema: JSONObject?,
): NbgPermissionRiskTier? =
  listOfNotNull(
    nbgPermissionRiskTierForWire(block.optString("severity")),
    nbgPermissionRiskTierForWire(nbgJsonPrimitiveString(block, "riskTier")),
    nbgPermissionRiskTierForWire(nbgJsonPrimitiveString(block, "risk")),
    nbgPermissionRiskTierForWire(nbgPermissionRiskSchemaString(riskSchema, "severity")),
    nbgPermissionRiskTierForWire(nbgPermissionRiskSchemaString(riskSchema, "riskTier", "tier", "level", "risk")),
  ).maxByOrNull { it.ordinal }

private fun nbgPermissionRiskTierForWire(value: String?): NbgPermissionRiskTier? =
  when (value?.trim()?.lowercase()?.replace("_", "-")) {
    "danger", "dangerous", "critical", "destructive" -> NbgPermissionRiskTier.Dangerous
    "high", "elevated" -> NbgPermissionRiskTier.High
    "medium", "moderate" -> NbgPermissionRiskTier.Medium
    "low" -> NbgPermissionRiskTier.Low
    else -> null
  }

private fun nbgPermissionRiskSchemaObject(block: JSONObject): JSONObject? =
  block.optJSONObject("riskSchema")
    ?: block.optJSONObject("risk")
    ?: block.optJSONObject("permissionRisk")

private fun nbgPermissionRiskSchemaText(schema: JSONObject?): String =
  listOfNotNull(
    nbgPermissionRiskSchemaString(schema, "action"),
    nbgPermissionRiskSchemaString(schema, "toolName", "tool", "name"),
    nbgPermissionRiskSchemaString(schema, "command"),
    nbgPermissionRiskSchemaString(schema, "reason", "summary", "description"),
  )
    .filter { it.isNotBlank() }
    .joinToString(" ")

private fun nbgPermissionRiskForSchema(schema: JSONObject?): NbgPermissionRisk? {
  val text = nbgPermissionRiskSchemaText(schema)
  if (text.isBlank()) return null
  return nbgPermissionRiskForAction(
    action = nbgPermissionRiskSchemaString(schema, "action", "toolName", "tool", "name"),
    command = text,
  )
}

private fun nbgPermissionRiskSchemaString(schema: JSONObject?, vararg names: String): String =
  names.firstNotNullOfOrNull { name -> nbgJsonPrimitiveString(schema, name).takeIf { it.isNotBlank() } }.orEmpty()

private fun nbgJsonPrimitiveString(obj: JSONObject?, name: String): String {
  val value = obj?.opt(name) ?: return ""
  return when (value) {
    is String -> value
    is Number, is Boolean -> value.toString()
    else -> ""
  }.trim()
}

private fun nbgPermissionRiskPayloadText(payload: JSONObject?, params: JSONObject?): String =
  listOfNotNull(
    payload?.optString("toolName"),
    payload?.optString("tool"),
    payload?.optString("name"),
    payload?.optString("action"),
    params?.optString("action"),
    params?.optString("command"),
    params?.optString("url"),
    params?.optString("path"),
    params?.optString("file_path"),
    params?.optString("target"),
    params?.optString("label"),
    params?.optString("key"),
  )
    .filter { it.isNotBlank() }
    .joinToString(" ")

private fun String.hasAny(vararg needles: String): Boolean =
  needles.any { contains(it) }

val NBG_VALID_PERMISSION_MODES: Set<String> =
  setOf(NBG_PERMISSION_MODE_OPERATE, NBG_PERMISSION_MODE_ASK, NBG_PERMISSION_MODE_READ_ONLY)

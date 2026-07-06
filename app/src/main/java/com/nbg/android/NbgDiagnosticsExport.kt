package com.nbg.android

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest

internal data class NbgDiagnosticsAppInfo(
  val packageName: String,
  val versionName: String,
  val versionCode: Long,
)

internal data class NbgDiagnosticsDeviceInfo(
  val manufacturer: String,
  val model: String,
  val sdkInt: Int,
  val androidRelease: String,
)

internal data class NbgDiagnosticsExportSnapshot(
  val generatedAtMs: Long,
  val app: NbgDiagnosticsAppInfo,
  val device: NbgDiagnosticsDeviceInfo,
  val capabilities: NbgCapabilityRegistry,
  val hanakoState: HanakoChatState,
  val savedApis: List<NbgStoredApi>,
  val fileShareState: NbgFileShareServerState?,
  val petState: NbgPetStoreState,
  val terminalReadiness: List<TerminalReadinessSnapshot> = emptyList(),
  val launcher: NbgDiagnosticsLauncherSnapshot = NbgDiagnosticsLauncherSnapshot(),
  val runtimePatch: NbgDiagnosticsRuntimePatchSnapshot = NbgDiagnosticsRuntimePatchSnapshot(),
  val petDexManifestProvenance: NbgPetDexManifestProvenance? = null,
)

internal data class NbgDiagnosticsLauncherSnapshot(
  val serverInfoPresent: Boolean = false,
  val pidPresent: Boolean = false,
  val versionPresent: Boolean = false,
  val launchLogPresent: Boolean = false,
  val serverLogPresent: Boolean = false,
  val launchLogTail: List<String> = emptyList(),
  val serverLogTail: List<String> = emptyList(),
)

internal data class NbgDiagnosticsRuntimePatchSnapshot(
  val statusPresent: Boolean = false,
  val activeVersion: String = "",
  val patchSetVersion: String = "",
  val targetRuntimeVersion: String = "",
  val targetMarker: String = "",
  val activeMarker: String = "",
  val patches: List<NbgDiagnosticsRuntimePatchEntry> = emptyList(),
  val error: String = "",
)

internal data class NbgDiagnosticsRuntimePatchEntry(
  val id: String,
  val state: String,
  val reason: String = "",
  val error: String = "",
)

internal data class NbgDiagnosticsRuntimePatchUiSummary(
  val text: String,
  val needsAttention: Boolean,
)

private data class NbgDiagnosticsRuntimePatchComputedSummary(
  val state: String,
  val needsAttention: Boolean,
  val activePatchSetMatches: Boolean,
  val patchCount: Int,
  val appliedCount: Int,
  val skippedCount: Int,
  val failedCount: Int,
  val unknownCount: Int,
  val attentionCount: Int,
  val markerMatchState: String,
  val markerMatched: Boolean,
)

private const val NBG_DIAGNOSTIC_LOG_TAIL_MAX_BYTES = 64 * 1024

internal fun nbgCreateDiagnosticsExportSnapshot(
  context: Context,
  capabilities: NbgCapabilityRegistry,
  hanakoState: HanakoChatState,
  savedApis: List<NbgStoredApi>,
  fileShareState: NbgFileShareServerState?,
  petState: NbgPetStoreState,
): NbgDiagnosticsExportSnapshot =
  NbgDiagnosticsExportSnapshot(
    generatedAtMs = System.currentTimeMillis(),
    app = nbgDiagnosticsAppInfo(context),
    device = NbgDiagnosticsDeviceInfo(
      manufacturer = Build.MANUFACTURER.orEmpty(),
      model = Build.MODEL.orEmpty(),
      sdkInt = Build.VERSION.SDK_INT,
      androidRelease = Build.VERSION.RELEASE.orEmpty(),
    ),
    capabilities = capabilities,
    hanakoState = hanakoState,
    savedApis = savedApis,
    fileShareState = fileShareState,
    petState = petState,
    terminalReadiness = TerminalWorkspace.shared.terminalDiagnosticsSnapshot(),
    launcher = nbgReadDiagnosticsLauncherSnapshot(context),
    runtimePatch = nbgReadDiagnosticsRuntimePatchSnapshot(context),
    petDexManifestProvenance = nbgReadPetDexManifestProvenance(nbgPetDexManifestProvenanceDir(context.filesDir)),
  )

internal fun nbgBuildDiagnosticsExportJson(snapshot: NbgDiagnosticsExportSnapshot): String {
  val state = snapshot.hanakoState
  val capabilities = snapshot.capabilities.withTerminalReadiness(snapshot.terminalReadiness)
  return JSONObject()
    .put("schema", "nbg-diagnostics-v1")
    .put("redactionVersion", 1)
    .put("generatedAtMs", snapshot.generatedAtMs)
    .put(
      "excluded",
      JSONArray(
        listOf(
          "api_keys",
          "hanako_tokens",
          "raw_hanako_server_info",
          "ftp_passwords",
          "terminal_output",
          "conversation_text",
          "memory_items",
          "user_code",
          "mcp_commands_args_paths",
          "skill_file_paths",
          "pet_resource_urls_paths",
          "petdex_manifest_body",
        ),
      ),
    )
    .put("app", snapshot.app.toJson())
    .put("device", snapshot.device.toJson())
    .put("capabilities", capabilities.toDiagnosticsJson())
    .put("launcher", snapshot.launcher.toDiagnosticsJson())
    .put("runtimePatch", snapshot.runtimePatch.toDiagnosticsJson())
    .put("terminal", snapshot.terminalReadiness.toTerminalDiagnosticsJson())
    .put("recentErrors", snapshot.copy(capabilities = capabilities).toRecentErrorsJson())
    .put(
      "hanako",
      JSONObject()
        .put("connected", state.connected)
        .put("connecting", state.connecting)
        .put("prewarming", state.prewarming)
        .put("streaming", state.streaming)
        .put("compressing", state.compressing)
        .put("connectionLabel", nbgDiagnosticText(state.connectionLabel))
        .put("lastError", state.toDiagnosticsLastError())
        .put("modelName", nbgDiagnosticText(state.modelName.orEmpty(), limit = 96))
        .put("agentName", nbgDiagnosticText(state.agentName.orEmpty(), limit = 96))
        .put("permissionMode", nbgNormalizePermissionMode(state.permissionMode))
        .put("thinkingLevel", nbgNormalizeThinkingLevel(state.thinkingLevel).orEmpty())
        .put("sessionPresent", !state.sessionPath.isNullOrBlank())
        .put("sessionCount", state.sessions.size)
        .put("searchResultCount", state.searchResults.size)
        .put("compressionAvailable", state.compressionAvailable)
        .put("streamResume", state.streamResumeDiagnostics.toDiagnosticsJson())
        .put("runtime", state.runtimeStatus.toDiagnosticsJson()),
    )
    .put("urlApi", snapshot.savedApis.toUrlApiDiagnosticsJson())
    .put("mcp", state.mcpState.toDiagnosticsJson(state.mcpLoading, state.mcpError))
    .put("skills", state.skillsSnapshot.toDiagnosticsJson(state.skillsLoading, state.skillsError))
    .put("memory", state.memoryState.toDiagnosticsJson(state.memoryLoading, state.memoryError))
    .put("ftp", snapshot.fileShareState.toDiagnosticsJson())
    .put("pets", snapshot.petState.toDiagnosticsJson(snapshot.petDexManifestProvenance))
    .toString(2)
}

internal fun nbgRedactDiagnosticText(raw: String): String {
  if (raw.isBlank()) return ""
  var value = raw
  value = value.replace(Regex("""(?i)(authorization\s*[:=]\s*bearer\s+)[^\s"',}]+""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]{8,}"""), "Bearer [redacted]")
  value = value.replace(Regex("""(?i)(api[_-]?key|token|password|secret)(["'\s:=]+)[^"'\s,}]+""")) {
    "${it.groupValues[1]}${it.groupValues[2]}[redacted]"
  }
  value = value.replace(Regex("""(?i)\b(pid\s*=\s*)\d+\b""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""(?i)\b(port\s*=\s*)\d+\b""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""(?i)\b(version\s*=\s*)[^\s"',}]+""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""(?i)(\b[a-z0-9][a-z0-9._-]*@)\d+(?:\.\d+)+(?:[-+][A-Za-z0-9._-]+)?\b""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""(?i)(\balready running on\s+)\d+\b""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""(?i)(\bobserved healthy server on\s+)\d+\b""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""(?i)(https?://(?:127\.0\.0\.1|localhost):)\d+\b""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""(?i)\b((?:127\.0\.0\.1|localhost):)\d+\b""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""(?i)(\blistening on\s+:)\d+\b""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""(?i)([?&](?:api[_-]?key|token|password|secret)=)[^&\s"']+""")) {
    it.groupValues[1] + "[redacted]"
  }
  value = value.replace(Regex("""sk-[A-Za-z0-9_-]{12,}"""), "sk-[redacted]")
  value = value.replace(Regex("""(?i)\b[a-f0-9]{64}\b"""), "[sha256]")
  value = value.replace(
    Regex("""(?i)(/data/user/\d+/[^\s"',}]+|/storage/emulated/\d+/[^\s"',}]+|/sdcard/[^\s"',}]+|/mnt/[^\s"',}]+|/tmp/[^\s"',}]+|/var/[^\s"',}]+|/usr/[^\s"',}]+|/opt/[^\s"',}]+|/root/[^\s"',}]+|/home/[^\s"',}]+)"""),
    "[path]",
  )
  return value
}

internal fun nbgDiagnosticsRuntimePatchUiSummary(exportText: String): NbgDiagnosticsRuntimePatchUiSummary =
  runCatching {
    val runtimePatch = JSONObject(exportText).optJSONObject("runtimePatch")
      ?: return NbgDiagnosticsRuntimePatchUiSummary("运行补丁：未包含状态", needsAttention = true)
    val state = runtimePatch.optString("state").ifBlank {
      if (runtimePatch.optBoolean("statusPresent", false)) "unknown" else "missing"
    }
    val activeVersion = runtimePatch.optString("activeVersion").ifBlank { "none" }
    val patchSetVersion = runtimePatch.optString("patchSetVersion").ifBlank { "unknown" }
    val markerMatchState = runtimePatch.optString("markerMatchState").ifBlank { "unknown" }
    val appliedCount = runtimePatch.optInt("appliedCount")
    val skippedCount = runtimePatch.optInt("skippedCount")
    val failedCount = runtimePatch.optInt("failedCount")
    val attentionCount = runtimePatch.optInt("attentionCount")
    val label = nbgRuntimePatchStateLabel(state)
    val markerLabel = nbgRuntimePatchMarkerLabel(markerMatchState)
    val text = "运行补丁：$label active=$activeVersion set=$patchSetVersion " +
      "marker=$markerLabel applied=$appliedCount skipped=$skippedCount failed=$failedCount attention=$attentionCount"
    NbgDiagnosticsRuntimePatchUiSummary(
      text = nbgDiagnosticText(text, limit = 220),
      needsAttention = runtimePatch.optBoolean("needsAttention", state != "ok"),
    )
  }.getOrElse {
    NbgDiagnosticsRuntimePatchUiSummary("运行补丁：诊断 JSON 无法解析", needsAttention = true)
  }

private fun nbgDiagnosticsAppInfo(context: Context): NbgDiagnosticsAppInfo {
  val info = runCatching {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0)
  }.getOrNull()
  @Suppress("DEPRECATION")
  val versionCode = when {
    info == null -> 0L
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> info.longVersionCode
    else -> info.versionCode.toLong()
  }
  return NbgDiagnosticsAppInfo(
    packageName = context.packageName,
    versionName = info?.versionName.orEmpty(),
    versionCode = versionCode,
  )
}

private fun nbgDiagnosticText(raw: String, limit: Int = 512): String =
  nbgRedactDiagnosticText(raw)
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .lineSequence()
    .take(8)
    .joinToString("\n")
    .take(limit)

private fun NbgDiagnosticsAppInfo.toJson(): JSONObject =
  JSONObject()
    .put("packageName", packageName)
    .put("versionName", versionName)
    .put("versionCode", versionCode)

private fun NbgDiagnosticsDeviceInfo.toJson(): JSONObject =
  JSONObject()
    .put("manufacturer", nbgDiagnosticText(manufacturer, limit = 80))
    .put("model", nbgDiagnosticText(model, limit = 80))
    .put("sdkInt", sdkInt)
    .put("androidRelease", nbgDiagnosticText(androidRelease, limit = 40))

private fun NbgDiagnosticsLauncherSnapshot.toDiagnosticsJson(): JSONObject =
  JSONObject()
    .put("serverInfoPresent", serverInfoPresent)
    .put("pidPresent", pidPresent)
    .put("versionPresent", versionPresent)
    .put("launchLogPresent", launchLogPresent)
    .put("serverLogPresent", serverLogPresent)
    .put("failureCodes", JSONArray(nbgLauncherFailureCodes()))
    .put("launchLogTail", JSONArray(launchLogTail.map { nbgDiagnosticText(it, limit = 240) }))
    .put("serverLogTail", JSONArray(serverLogTail.map { nbgDiagnosticText(it, limit = 240) }))

private fun NbgDiagnosticsLauncherSnapshot.nbgLauncherFailureCodes(): List<String> {
  val codes = linkedSetOf<String>()
  if (!serverInfoPresent) codes += "server_info_missing"
  if (serverInfoPresent && !pidPresent) codes += "pid_missing"
  if (serverInfoPresent && !versionPresent) codes += "version_missing"
  if (!launchLogPresent) codes += "launch_log_missing"
  if (!serverLogPresent) codes += "server_log_missing"

  val diagnosticText = (launchLogTail + serverLogTail)
    .joinToString("\n")
    .lowercase()
  if (diagnosticText.isBlank()) return codes.toList()

  if ("failed to start hanakopro launcher" in diagnosticText) codes += "launcher_start_failed"
  if ("server pack and source not found" in diagnosticText) codes += "server_source_missing"
  if ("node_modules missing" in diagnosticText) codes += "node_modules_missing"
  if ("run_in_ubuntu pack exit" in diagnosticText && Regex("""run_in_ubuntu pack exit\s+[1-9]\d*""").containsMatchIn(diagnosticText)) {
    codes += "pack_exit_nonzero"
  }
  if ("exit 44" in diagnosticText || "hanako-pack.err" in diagnosticText) codes += "bundled_pack_extract_failed"
  if ("exit 45" in diagnosticText) codes += "bundled_pack_deploy_failed"
  if ("eaddrinuse" in diagnosticText || "address already in use" in diagnosticText) codes += "port_in_use"
  if ("permission denied" in diagnosticText || "eacces" in diagnosticText) codes += "permission_denied"
  if ("install_ubuntu" in diagnosticText && "failed" in diagnosticText) codes += "ubuntu_setup_failed"
  if ("android helper" in diagnosticText && "failed" in diagnosticText) codes += "android_helper_setup_failed"
  return codes.toList().take(12)
}

private fun NbgDiagnosticsRuntimePatchSnapshot.toDiagnosticsJson(): JSONObject {
  val summary = toRuntimePatchComputedSummary()
  return JSONObject()
    .put("statusPresent", statusPresent)
    .put("state", summary.state)
    .put("needsAttention", summary.needsAttention)
    .put("activePatchSetMatches", summary.activePatchSetMatches)
    .put("patchCount", summary.patchCount)
    .put("appliedCount", summary.appliedCount)
    .put("skippedCount", summary.skippedCount)
    .put("failedCount", summary.failedCount)
    .put("unknownCount", summary.unknownCount)
    .put("attentionCount", summary.attentionCount)
    .put("targetMarkerPresent", targetMarker.isNotBlank())
    .put("activeMarkerPresent", activeMarker.isNotBlank())
    .put("markerMatched", summary.markerMatched)
    .put("markerMatchState", summary.markerMatchState)
    .put("activeVersion", nbgDiagnosticText(activeVersion, limit = 80))
    .put("patchSetVersion", nbgDiagnosticText(patchSetVersion, limit = 80))
    .put("targetRuntimeVersion", nbgDiagnosticText(targetRuntimeVersion, limit = 80))
    .put("error", nbgDiagnosticText(error))
    .put(
      "patches",
      JSONArray(
        patches.map {
          JSONObject()
            .put("id", nbgDiagnosticText(it.id, limit = 120))
            .put("state", nbgDiagnosticText(it.state, limit = 80))
            .put("reason", nbgDiagnosticText(it.reason, limit = 160))
            .put("error", nbgDiagnosticText(it.error, limit = 240))
        },
      ),
    )
}

private fun NbgDiagnosticsRuntimePatchSnapshot.toRuntimePatchComputedSummary(): NbgDiagnosticsRuntimePatchComputedSummary {
  val normalizedStates = patches.map { it.state.trim().lowercase() }
  val appliedCount = normalizedStates.count { it == "applied" }
  val skippedCount = normalizedStates.count { it == "skipped" }
  val failedCount = normalizedStates.count { it == "failed" }
  val knownStates = setOf("applied", "skipped", "failed")
  val unknownCount = normalizedStates.count { it.isBlank() || it !in knownStates }
  val targetMarkerPresent = targetMarker.isNotBlank()
  val activeMarkerPresent = activeMarker.isNotBlank()
  val markerMatched = targetMarkerPresent && activeMarkerPresent && targetMarker == activeMarker
  val markerMatchState = when {
    markerMatched -> "matched"
    targetMarkerPresent && activeMarkerPresent -> "mismatched"
    else -> "unknown"
  }
  val attentionCount = patches.count { entry ->
    val state = entry.state.trim().lowercase()
    val reason = entry.reason.trim().lowercase()
    entry.error.isNotBlank() ||
      state == "failed" ||
      state.isBlank() ||
      state !in knownStates ||
      (state == "skipped" && reason != "already-applied")
  }
  val activePatchSetMatches = activeVersion.isNotBlank() &&
    patchSetVersion.isNotBlank() &&
    activeVersion == patchSetVersion
  val state = when {
    statusPresent && error.isNotBlank() -> "corrupt"
    !statusPresent -> "missing"
    failedCount > 0 || patches.any { it.error.isNotBlank() } -> "failed"
    activeVersion.isNotBlank() && patchSetVersion.isNotBlank() && activeVersion != patchSetVersion -> "stale"
    markerMatchState == "mismatched" -> "attention"
    attentionCount > 0 -> "attention"
    patches.isEmpty() -> "empty"
    else -> "ok"
  }
  return NbgDiagnosticsRuntimePatchComputedSummary(
    state = state,
    needsAttention = state != "ok",
    activePatchSetMatches = activePatchSetMatches,
    patchCount = patches.size,
    appliedCount = appliedCount,
    skippedCount = skippedCount,
    failedCount = failedCount,
    unknownCount = unknownCount,
    attentionCount = attentionCount,
    markerMatchState = markerMatchState,
    markerMatched = markerMatched,
  )
}

private fun nbgRuntimePatchStateLabel(state: String): String =
  when (state.trim().lowercase()) {
    "ok" -> "正常"
    "missing" -> "未记录"
    "corrupt" -> "状态损坏"
    "failed" -> "失败"
    "stale" -> "版本待重启"
    "attention" -> "需复核"
    "empty" -> "无补丁记录"
    else -> "未知"
  }

private fun nbgRuntimePatchMarkerLabel(state: String): String =
  when (state.trim().lowercase()) {
    "matched" -> "匹配"
    "mismatched" -> "不匹配"
    else -> "未知"
  }

private fun List<TerminalReadinessSnapshot>.toTerminalDiagnosticsJson(): JSONObject =
  JSONObject()
    .put("tabCountWithReadiness", size)
    .put("readyCount", count { it.readiness == TerminalReadiness.Ready })
    .put("installingCount", count { it.readiness == TerminalReadiness.Installing })
    .put("failedCount", count { it.readiness == TerminalReadiness.Failed })
    .put(
      "tabs",
      JSONArray(
        map {
          JSONObject()
            .put("readiness", it.readiness.name.lowercase())
            .put("phase", it.phase.name)
            .put("diagnostic", nbgDiagnosticText(it.diagnostic, limit = 240))
        },
      ),
    )

private fun NbgDiagnosticsExportSnapshot.toRecentErrorsJson(): JSONArray =
  JSONArray(
    buildList {
      hanakoState.lastError?.takeIf { it.isNotBlank() }?.let { add("hanako:${hanakoState.toDiagnosticsLastError()}") }
      hanakoState.mcpError?.takeIf { it.isNotBlank() }?.let { add("mcp:$it") }
      hanakoState.skillsError?.takeIf { it.isNotBlank() }?.let { add("skills:${nbgDiagnosticSkillError(it)}") }
      hanakoState.memoryError?.takeIf { it.isNotBlank() }?.let { add("memory:${nbgDiagnosticMemoryError(it)}") }
      fileShareState?.message?.takeIf { it.isNotBlank() }?.let { add("ftp:$it") }
      capabilities.capabilities.forEach { capability ->
        if (capability.lastError.isNotBlank()) add("${capability.id}:${capability.toDiagnosticsLastError()}")
      }
      runtimePatch.error.takeIf { it.isNotBlank() }?.let { add("runtimePatch:$it") }
      runtimePatch.patches.forEach { patch ->
        if (patch.error.isNotBlank()) add("runtimePatch.${patch.id}:${patch.error}")
      }
      terminalReadiness.forEachIndexed { index, snapshot ->
        if (snapshot.diagnostic.isNotBlank()) add("terminal.$index:${snapshot.diagnostic}")
      }
    }
      .map { nbgDiagnosticText(it, limit = 240) }
      .distinct()
      .take(16),
  )

private fun HanakoChatState.toDiagnosticsLastError(): String {
  val raw = lastError.orEmpty()
  if (raw.isBlank()) return ""
  return if (memoryError?.takeIf { it.isNotBlank() } == raw) {
    nbgDiagnosticMemoryError(raw)
  } else {
    nbgDiagnosticText(raw)
  }
}

private fun NbgCapability.toDiagnosticsLastError(): String =
  if (id == "memory") nbgDiagnosticMemoryError(lastError) else nbgDiagnosticText(lastError)

private fun NbgCapabilityRegistry.toDiagnosticsJson(): JSONArray =
  JSONArray(
    capabilities.map { capability ->
      JSONObject()
        .put("id", capability.id)
        .put("title", capability.title)
        .put("status", nbgDiagnosticText(capability.status, limit = 120))
        .put("health", capability.health.name.lowercase())
        .put("lastError", capability.toDiagnosticsLastError())
        .put("isBeta", capability.isBeta)
        .put("diagnosticsAction", capability.diagnosticsAction)
    },
  )

private fun NbgCapabilityRegistry.withTerminalReadiness(
  readiness: List<TerminalReadinessSnapshot>,
): NbgCapabilityRegistry =
  if (capabilities.none { it.id == "terminal" }) {
    this
  } else {
    NbgCapabilityRegistry(
      capabilities.map { capability ->
        if (capability.id == "terminal") nbgTerminalCapability(readiness) else capability
      },
    )
  }

private fun List<NbgStoredApi>.toUrlApiDiagnosticsJson(): JSONObject =
  JSONObject()
    .put("providerCount", size)
    .put(
      "providers",
      JSONArray(
        map { entry ->
          JSONObject()
            .put("idHash", nbgDiagnosticHash(entry.id))
            .put("name", nbgDiagnosticText(entry.name, limit = 96))
            .put("host", nbgDiagnosticHost(entry.baseUrl))
            .put("modelCount", entry.models.size)
            .put("verifiedModelCount", entry.verifiedModelIds.size)
            .put("selectedModelPresent", entry.selectedModelId.isNotBlank())
            .put("updatedAtMs", entry.updatedAtMs)
        },
      ),
    )

private fun HanakoRuntimeStatus.toDiagnosticsJson(): JSONObject =
  JSONObject()
    .put("browserLabel", nbgDiagnosticText(browserLabel.orEmpty(), limit = 120))
    .put("usageLabel", nbgDiagnosticText(usageLabel.orEmpty(), limit = 120))
    .put("permissionLabel", nbgDiagnosticText(permissionLabel.orEmpty(), limit = 120))
    .put("thinkingLabel", nbgDiagnosticText(thinkingLabel.orEmpty(), limit = 120))

private fun HanakoStreamResumeDiagnostics.toDiagnosticsJson(): JSONObject =
  JSONObject()
    .put("requestCount", requestCount)
    .put("resumeCount", resumeCount)
    .put("replayedEventCount", replayedEventCount)
    .put("acceptedReplayEventCount", acceptedReplayEventCount)
    .put("skippedReplayEventCount", skippedReplayEventCount)
    .put("duplicateReplayEventCount", duplicateReplayEventCount)
    .put("truncatedResumeCount", truncatedResumeCount)
    .put("resetResumeCount", resetResumeCount)
    .put("cursorPresent", cursorPresent)
    .put("lastSeq", lastSeq)

private fun HanakoMcpState.toDiagnosticsJson(loading: Boolean, error: String?): JSONObject {
  val runningCount = connectors.count { it.running }
  val enabledForAgentCount = connectors.count { connectorEnabled(it.id) }
  return JSONObject()
    .put("enabled", enabled)
    .put("loading", loading)
    .put("error", nbgDiagnosticText(error.orEmpty()))
    .put("connectorCount", connectors.size)
    .put("runningConnectorCount", runningCount)
    .put("agentEnabledConnectorCount", enabledForAgentCount)
    .put("toolCount", connectors.sumOf { it.tools.size })
    .put("authConfiguredCount", connectors.count { it.authType != "none" || it.authStatus.isNotBlank() })
    .put("envCount", connectors.sumOf { it.envCount })
    .put("headersCount", connectors.sumOf { it.headersCount })
    .put(
      "connectors",
      JSONArray(
        connectors.map {
          JSONObject()
            .put("idHash", nbgDiagnosticHash(it.id))
            .put("transport", nbgDiagnosticText(it.transport, limit = 40))
            .put("status", nbgDiagnosticText(it.status, limit = 40))
            .put("toolCount", it.tools.size)
            .put("autoStart", it.autoStart)
            .put("authType", nbgDiagnosticText(it.authType, limit = 40))
            .put("envCount", it.envCount)
            .put("headersCount", it.headersCount)
        },
      ),
    )
}

private fun HanakoSkillsSnapshot.toDiagnosticsJson(loading: Boolean, error: String?): JSONObject =
  JSONObject()
    .put("sourceIntegrityVersion", NBG_SKILL_SOURCE_INTEGRITY_VERSION)
    .put("loading", loading)
    .put("error", nbgDiagnosticSkillError(error.orEmpty()))
    .put("visibleCount", visibleSkills.size)
    .put("enabledCount", enabledCount)
    .put("bundleCount", bundles.size)
    .put("expectedBundledCount", NBG_ANDROID_BUNDLED_SKILL_NAMES.size)
    .put("pinnedBundledAssetCount", NBG_ANDROID_BUNDLED_SKILL_ASSET_PINS.size)
    .put("externalPathCount", externalPaths.visibleCount)
    .put("builtinCount", visibleSkills.count { it.source == "builtin" })
    .put("externalCount", visibleSkills.count { it.source == "external" })
    .put("readonlyCount", visibleSkills.count { it.readonly })
    .put("trustedBundledCount", visibleSkills.count { nbgSkillSummarySourceReview(it).trustTier == NbgSkillTrustTier.TrustedBundled })
    .put("userManagedCount", visibleSkills.count { nbgSkillSummarySourceReview(it).trustTier == NbgSkillTrustTier.UserManaged })
    .put("unverifiedExternalCount", visibleSkills.count { nbgSkillSummarySourceReview(it).trustTier == NbgSkillTrustTier.UnverifiedExternal })
    .put("requiresReviewCount", visibleSkills.count { nbgSkillSummarySourceReview(it).requiresReview })

private fun nbgDiagnosticSkillError(raw: String): String =
  if (raw.isBlank()) "" else "skills_error_present"

private fun HanakoMemoryState.toDiagnosticsJson(loading: Boolean, error: String?): JSONObject =
  JSONObject()
    .put("loading", loading)
    .put("error", nbgDiagnosticMemoryError(error.orEmpty()))
    .put("count", count)
    .put("enabledCount", enabledCount)
    .put("loadedItemCount", items.size)
    .put("contextPolicyVersion", NBG_MEMORY_CONTEXT_POLICY_VERSION)
    .put("injectableLoadedCount", items.count { it.enabled && nbgReviewMemoryItem(it).allowContextInjection })
    .put("blockedSensitiveLoadedCount", items.count { nbgReviewMemoryItem(it).risk == NbgMemoryContextRisk.BlockedSensitive })
    .put("blockedTooLargeLoadedCount", items.count { nbgReviewMemoryItem(it).risk == NbgMemoryContextRisk.BlockedTooLarge })
    .put("auditEventCount", auditEvents.size)
    .put(
      "recentAuditEvents",
      JSONArray(
        auditEvents
          .takeLast(NBG_MEMORY_AUDIT_DIAGNOSTIC_TAIL_LIMIT)
          .map { it.toDiagnosticsJson() },
      ),
    )

private fun HanakoMemoryAuditEvent.toDiagnosticsJson(): JSONObject =
  JSONObject()
    .put("action", action.takeIf { it in setOf("create", "update", "delete", "unknown") }.orEmpty())
    .put("result", result.takeIf { it in setOf("success", "failure", "blocked", "unknown") }.orEmpty())
    .put("normalizedType", normalizedType.takeIf { it in HANA_MEMORY_TYPES }.orEmpty())
    .put("risk", risk.takeIf { it in NbgMemoryContextRisk.entries.map { entry -> entry.wireName } }.orEmpty())
    .put("policyVersion", policyVersion.takeIf { it == NBG_MEMORY_CONTEXT_POLICY_VERSION }.orEmpty())
    .put("timestampMillis", timestampMillis.coerceAtLeast(0L))

private fun NbgFileShareServerState?.toDiagnosticsJson(): JSONObject =
  JSONObject()
    .put("configured", this != null)
    .put("running", this?.running == true)
    .put("protocol", this?.protocol.orEmpty())
    .put("accessMode", this?.accessMode?.wireValue.orEmpty())
    .put("port", this?.port ?: 0)
    .put("hasPassword", !this?.password.isNullOrBlank())
    .put("message", nbgDiagnosticText(this?.message.orEmpty()))

private fun NbgPetStoreState.toDiagnosticsJson(manifestProvenance: NbgPetDexManifestProvenance?): JSONObject =
  JSONObject()
    .put("installedCount", installedPets.size)
    .put("hidden", hidden)
    .put("currentBuiltIn", currentPet.builtIn)
    .put("currentSource", nbgPetSummarySourceReview(currentPet).sourceKind.wireName)
    .put("resourcePolicyVersion", NBG_PET_RESOURCE_INTEGRITY_VERSION)
    .put("trustedBundledCount", installedPets.count { nbgPetSummarySourceReview(it).trustTier == NbgPetTrustTier.TrustedBundled })
    .put("unverifiedPetDexCount", installedPets.count { nbgPetSummarySourceReview(it).trustTier == NbgPetTrustTier.UnverifiedPetDex })
    .put("userManagedCount", installedPets.count { nbgPetSummarySourceReview(it).trustTier == NbgPetTrustTier.UserManaged })
    .put("blockedLoadedCount", installedPets.count { nbgPetSummarySourceReview(it).trustTier == NbgPetTrustTier.Blocked })
    .put("requiresReviewCount", installedPets.count { nbgPetSummarySourceReview(it).requiresReview })
    .put("petDexWithProvenanceCount", installedPets.count { it.isNbgPetDexSource() && nbgPetResourceProvenancePresent(it) })
    .put("legacyPetDexWithoutProvenanceCount", installedPets.count { it.isNbgPetDexSource() && !nbgPetResourceProvenancePresent(it) })
    .put("manifestProvenancePresent", manifestProvenance != null)
    .put("manifestSourceKind", manifestProvenance?.sourceKind?.wireName.orEmpty())
    .put("manifestTrustTier", manifestProvenance?.trustTier?.wireName.orEmpty())
    .put("manifestRequiresReview", manifestProvenance?.requiresReview == true)
    .put("manifestDeclaredTotal", manifestProvenance?.declaredTotal ?: 0)
    .put("manifestParsedPetCount", manifestProvenance?.parsedPetCount ?: 0)
    .put("manifestFetchedAtMs", manifestProvenance?.fetchedAtMs ?: 0L)
    .put("manifestSha256Prefix", manifestProvenance?.sha256?.take(12).orEmpty())

private fun NbgPetSummary.isNbgPetDexSource(): Boolean =
  nbgPetSummarySourceReview(this).sourceKind == NbgPetResourceSourceKind.PetDexHttps

private fun nbgDiagnosticHost(baseUrl: String): String =
  runCatching { URI(baseUrl.trim()).host.orEmpty() }
    .getOrDefault("")
    .ifBlank { "invalid" }
    .let { nbgDiagnosticText(it, limit = 120) }

private fun nbgDiagnosticHash(value: String): String {
  if (value.isBlank()) return ""
  val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
  return digest.joinToString("") { "%02x".format(it) }.take(12)
}

private fun nbgReadDiagnosticsLauncherSnapshot(context: Context): NbgDiagnosticsLauncherSnapshot {
  val serverInfo = findServerInfo(context)
  val launchLog = File(context.filesDir, "hanakopro-launch.log")
  val serverLog = hanakoServerHomeDirs(context)
    .map { File(it, "android-server.log") }
    .firstOrNull { it.isFile }
  return NbgDiagnosticsLauncherSnapshot(
    serverInfoPresent = serverInfo != null,
    pidPresent = serverInfo?.pid != null,
    versionPresent = !serverInfo?.version.isNullOrBlank(),
    launchLogPresent = launchLog.isFile,
    serverLogPresent = serverLog?.isFile == true,
    launchLogTail = launchLog.safeDiagnosticTail(),
    serverLogTail = serverLog.safeDiagnosticTail(),
  )
}

private fun nbgReadDiagnosticsRuntimePatchSnapshot(context: Context): NbgDiagnosticsRuntimePatchSnapshot {
  return nbgReadDiagnosticsRuntimePatchSnapshotFromHomes(hanakoServerHomeDirs(context))
}

internal fun nbgReadDiagnosticsRuntimePatchSnapshotFromHomes(homes: List<File>): NbgDiagnosticsRuntimePatchSnapshot {
  val statusFile = homes.map { File(it, "android-runtime-patches.json") }.firstOrNull { it.isFile }
  val activeVersion = homes.map { File(it, "android-runtime-patches.active") }.firstOrNull { it.isFile }
    ?.safeReadText()
    ?.trim()
    .orEmpty()
  if (statusFile == null) {
    return NbgDiagnosticsRuntimePatchSnapshot(activeVersion = activeVersion)
  }
  return runCatching {
    val root = JSONObject(statusFile.readText(Charsets.UTF_8))
    val patches = root.optJSONArray("patches").toRuntimePatchEntries()
    NbgDiagnosticsRuntimePatchSnapshot(
      statusPresent = true,
      activeVersion = activeVersion,
      patchSetVersion = root.optString("patchSetVersion"),
      targetRuntimeVersion = root.optString("targetRuntimeVersion"),
      targetMarker = root.optString("targetMarker"),
      activeMarker = root.optString("activeMarker"),
      patches = patches,
    )
  }.getOrElse { error ->
    NbgDiagnosticsRuntimePatchSnapshot(
      statusPresent = true,
      activeVersion = activeVersion,
      error = error.message.orEmpty().ifBlank { error::class.java.simpleName },
    )
  }
}

private fun JSONArray?.toRuntimePatchEntries(): List<NbgDiagnosticsRuntimePatchEntry> {
  val array = this ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      add(
        NbgDiagnosticsRuntimePatchEntry(
          id = item.optString("id"),
          state = item.optString("state"),
          reason = item.optString("reason"),
          error = item.optString("error"),
        ),
      )
    }
  }
}

private fun File?.safeDiagnosticTail(maxLines: Int = 12): List<String> {
  val file = this ?: return emptyList()
  return runCatching {
    if (!file.isFile) return@runCatching emptyList<String>()
    val maxBytes = NBG_DIAGNOSTIC_LOG_TAIL_MAX_BYTES.toLong()
    val start = (file.length() - maxBytes).coerceAtLeast(0L)
    val tailText = file.inputStream().use { input ->
      var remaining = start
      while (remaining > 0L) {
        val skipped = input.skip(remaining)
        if (skipped <= 0L) break
        remaining -= skipped
      }
      input.readBytes()
    }.toString(Charsets.UTF_8)
    tailText
      .lineSequence()
      .toList()
      .takeLast(maxLines)
      .map { nbgRedactDiagnosticText(it).take(240) }
  }.getOrDefault(emptyList())
}

internal fun nbgSafeDiagnosticTailForTest(file: File?, maxLines: Int = 12): List<String> =
  file.safeDiagnosticTail(maxLines)

private fun File.safeReadText(): String? =
  runCatching {
    if (!isFile) return@runCatching null
    readText(Charsets.UTF_8)
  }.getOrNull()

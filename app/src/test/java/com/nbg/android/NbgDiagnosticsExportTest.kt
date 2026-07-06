package com.nbg.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NbgDiagnosticsExportTest {
  private companion object {
    const val RAW_MEMORY_ERROR =
      "raw memory backend failure /api/plugins/memory/state?query=private-memory-query /api/plugins/memory/items/memory-item-secret-id title=private-memory-title tag=private-memory-tag"
  }

  @Test
  fun diagnosticsExportIncludesStateCountsAndCapabilityHealth() {
    val json = JSONObject(nbgBuildDiagnosticsExportJson(sampleDiagnosticsSnapshot()))

    assertEquals("nbg-diagnostics-v1", json.getString("schema"))
    assertEquals(1, json.getInt("redactionVersion"))
    assertTrue(json.getJSONArray("excluded").toString().contains("api_keys"))
    assertTrue(json.getJSONArray("excluded").toString().contains("raw_hanako_server_info"))
    assertEquals("com.nbg.android.test", json.getJSONObject("app").getString("packageName"))
    assertEquals(34, json.getJSONObject("device").getInt("sdkInt"))

    val capabilities = json.getJSONArray("capabilities")
    assertEquals("hanako", capabilities.getJSONObject(0).getString("id"))
    assertEquals("failed", capabilities.getJSONObject(0).getString("health"))
    val capabilityIds = (0 until capabilities.length()).map { capabilities.getJSONObject(it).getString("id") }
    assertTrue("Full registry should include healthy capabilities, not only failed/degraded entries", "url_api" in capabilityIds)
    val urlApiCapability = capabilities.getJSONObject(capabilityIds.indexOf("url_api"))
    assertEquals("healthy", urlApiCapability.getString("health"))
    assertEquals("", urlApiCapability.getString("lastError"))
    val memoryCapability = capabilities.getJSONObject(capabilityIds.indexOf("memory"))
    assertEquals("failed", memoryCapability.getString("health"))
    assertEquals("memory_error_present", memoryCapability.getString("lastError"))

    val hanako = json.getJSONObject("hanako")
    assertFalse(hanako.getBoolean("connected"))
    assertEquals("memory_error_present", hanako.getString("lastError"))
    assertTrue(hanako.getBoolean("sessionPresent"))
    assertEquals(2, hanako.getInt("sessionCount"))
    assertEquals("ask", hanako.getString("permissionMode"))
    val streamResume = hanako.getJSONObject("streamResume")
    assertEquals(3, streamResume.getInt("requestCount"))
    assertEquals(2, streamResume.getInt("resumeCount"))
    assertEquals(5, streamResume.getInt("replayedEventCount"))
    assertEquals(4, streamResume.getInt("acceptedReplayEventCount"))
    assertEquals(1, streamResume.getInt("skippedReplayEventCount"))
    assertEquals(1, streamResume.getInt("duplicateReplayEventCount"))
    assertEquals(1, streamResume.getInt("truncatedResumeCount"))
    assertEquals(1, streamResume.getInt("resetResumeCount"))
    assertTrue(streamResume.getBoolean("cursorPresent"))
    assertEquals(42, streamResume.getInt("lastSeq"))
    assertFalse(streamResume.has("sessionPath"))
    assertFalse(streamResume.has("streamId"))
    assertFalse(streamResume.has("events"))

    val urlApi = json.getJSONObject("urlApi")
    assertEquals(1, urlApi.getInt("providerCount"))
    assertEquals("api.example.com", urlApi.getJSONArray("providers").getJSONObject(0).getString("host"))
    assertEquals(2, urlApi.getJSONArray("providers").getJSONObject(0).getInt("modelCount"))
    assertEquals(1, urlApi.getJSONArray("providers").getJSONObject(0).getInt("verifiedModelCount"))

    val memory = json.getJSONObject("memory")
    assertEquals(2, memory.getInt("count"))
    assertEquals(1, memory.getInt("enabledCount"))
    assertEquals(1, memory.getInt("loadedItemCount"))
    assertEquals(NBG_MEMORY_CONTEXT_POLICY_VERSION, memory.getString("contextPolicyVersion"))
    assertEquals(1, memory.getInt("injectableLoadedCount"))
    assertEquals(0, memory.getInt("blockedSensitiveLoadedCount"))
    assertEquals(0, memory.getInt("blockedTooLargeLoadedCount"))
    assertEquals("memory_error_present", memory.getString("error"))
    assertEquals(2, memory.getInt("auditEventCount"))
    val auditEvents = memory.getJSONArray("recentAuditEvents")
    assertEquals(2, auditEvents.length())
    val blockedAudit = auditEvents.getJSONObject(0)
    assertEquals("create", blockedAudit.getString("action"))
    assertEquals("blocked", blockedAudit.getString("result"))
    assertEquals("user_preference", blockedAudit.getString("normalizedType"))
    assertEquals(NbgMemoryContextRisk.BlockedSensitive.wireName, blockedAudit.getString("risk"))
    assertEquals(NBG_MEMORY_CONTEXT_POLICY_VERSION, blockedAudit.getString("policyVersion"))
    assertEquals(100L, blockedAudit.getLong("timestampMillis"))
    val deleteAudit = auditEvents.getJSONObject(1)
    assertEquals("delete", deleteAudit.getString("action"))
    assertEquals("success", deleteAudit.getString("result"))
    assertFalse(blockedAudit.has("content"))
    assertFalse(blockedAudit.has("title"))
    assertFalse(blockedAudit.has("tags"))
    assertFalse(blockedAudit.has("id"))
    assertFalse(blockedAudit.has("sourceSession"))
    assertFalse(blockedAudit.has("sourceTurnId"))
    assertFalse(blockedAudit.has("query"))

    val skills = json.getJSONObject("skills")
    assertEquals(NBG_SKILL_SOURCE_INTEGRITY_VERSION, skills.getString("sourceIntegrityVersion"))
    assertEquals(1, skills.getInt("visibleCount"))
    assertEquals(1, skills.getInt("expectedBundledCount"))
    assertEquals(1, skills.getInt("pinnedBundledAssetCount"))
    assertEquals(1, skills.getInt("userManagedCount"))
    assertEquals(1, skills.getInt("requiresReviewCount"))
    assertEquals(0, skills.getInt("trustedBundledCount"))
    assertEquals(0, skills.getInt("unverifiedExternalCount"))

    val ftp = json.getJSONObject("ftp")
    assertTrue(ftp.getBoolean("configured"))
    assertTrue(ftp.getBoolean("running"))
    assertTrue(ftp.getBoolean("hasPassword"))
    assertEquals("read_write", ftp.getString("accessMode"))
    assertEquals(43211, ftp.getInt("port"))

    val pets = json.getJSONObject("pets")
    assertEquals(3, pets.getInt("installedCount"))
    assertEquals(NBG_PET_RESOURCE_INTEGRITY_VERSION, pets.getString("resourcePolicyVersion"))
    assertEquals(NbgPetResourceSourceKind.BuiltIn.wireName, pets.getString("currentSource"))
    assertEquals(1, pets.getInt("trustedBundledCount"))
    assertEquals(1, pets.getInt("unverifiedPetDexCount"))
    assertEquals(1, pets.getInt("userManagedCount"))
    assertEquals(0, pets.getInt("blockedLoadedCount"))
    assertEquals(2, pets.getInt("requiresReviewCount"))
    assertEquals(0, pets.getInt("petDexWithProvenanceCount"))
    assertEquals(1, pets.getInt("legacyPetDexWithoutProvenanceCount"))
    assertFalse(pets.getBoolean("manifestProvenancePresent"))
    assertEquals("", pets.getString("manifestSourceKind"))
    assertEquals("", pets.getString("manifestTrustTier"))
    assertFalse(pets.getBoolean("manifestRequiresReview"))
    assertEquals(0, pets.getInt("manifestDeclaredTotal"))
    assertEquals(0, pets.getInt("manifestParsedPetCount"))
    assertEquals(0L, pets.getLong("manifestFetchedAtMs"))
    assertEquals("", pets.getString("manifestSha256Prefix"))

    val launcher = json.getJSONObject("launcher")
    assertTrue(launcher.getBoolean("serverInfoPresent"))
    assertTrue(launcher.getBoolean("launchLogPresent"))
    assertEquals(0, launcher.getJSONArray("failureCodes").length())
    assertFalse(launcher.has("serverInfo"))
    assertFalse(launcher.has("serverInfoJson"))
    assertFalse(launcher.has("rawServerInfo"))
    assertFalse(launcher.has("token"))
    assertFalse(launcher.has("port"))
    assertFalse(launcher.has("pid"))
    assertFalse(launcher.has("version"))

    val runtimePatch = json.getJSONObject("runtimePatch")
    assertTrue(runtimePatch.getBoolean("statusPresent"))
    assertEquals("failed", runtimePatch.getString("state"))
    assertTrue(runtimePatch.getBoolean("needsAttention"))
    assertTrue(runtimePatch.getBoolean("activePatchSetMatches"))
    assertEquals(1, runtimePatch.getInt("patchCount"))
    assertEquals(0, runtimePatch.getInt("appliedCount"))
    assertEquals(1, runtimePatch.getInt("skippedCount"))
    assertEquals(0, runtimePatch.getInt("failedCount"))
    assertEquals(1, runtimePatch.getInt("attentionCount"))
    assertTrue(runtimePatch.getBoolean("targetMarkerPresent"))
    assertTrue(runtimePatch.getBoolean("activeMarkerPresent"))
    assertFalse(runtimePatch.getBoolean("markerMatched"))
    assertEquals("mismatched", runtimePatch.getString("markerMatchState"))
    assertEquals("android-patch-v1", runtimePatch.getString("activeVersion"))
    assertEquals("hanako-server-linux-arm64-node22", runtimePatch.getString("targetRuntimeVersion"))
    assertEquals("upstream-user-agent", runtimePatch.getJSONArray("patches").getJSONObject(0).getString("id"))
    assertFalse(runtimePatch.getJSONArray("patches").getJSONObject(0).has("target"))

    val terminal = json.getJSONObject("terminal")
    assertEquals(1, terminal.getInt("tabCountWithReadiness"))
    assertEquals(0, terminal.getInt("readyCount"))
    assertEquals(0, terminal.getInt("installingCount"))
    assertEquals(1, terminal.getInt("failedCount"))
    assertEquals("failed", terminal.getJSONArray("tabs").getJSONObject(0).getString("readiness"))

    val recentErrors = json.getJSONArray("recentErrors").toString()
    assertTrue(recentErrors.contains("hanako:memory_error_present"))
    assertTrue(recentErrors.contains("memory:memory_error_present"))
    assertTrue(recentErrors.contains("runtimePatch.upstream-user-agent"))
    assertTrue(recentErrors.contains("terminal.0"))
  }

  @Test
  fun diagnosticsExportRedactsSecretsPathsAndUserContent() {
    val export = nbgBuildDiagnosticsExportJson(sampleDiagnosticsSnapshot())

    assertFalse(export.contains("sk-live-secret-1234567890"))
    assertFalse(export.contains("super-token-1234567890"))
    assertFalse(export.contains("ftp-password-123456"))
    assertFalse(export.contains("/root/private/project"))
    assertFalse(export.contains("/storage/emulated/0/Download/private.txt"))
    assertFalse(export.contains("memory body should never export"))
    assertFalse(export.contains("private-memory-query"))
    assertFalse(export.contains("memory-item-secret-id"))
    assertFalse(export.contains("private-memory-title"))
    assertFalse(export.contains("private-memory-tag"))
    assertFalse(export.contains("raw memory backend failure"))
    assertFalse(export.contains("mcp-secret-command"))
    assertFalse(export.contains("private skill path"))
    assertFalse(export.contains("nbg-engineering-core failed"))
    assertFalse(export.contains("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
    assertFalse(export.contains("/tmp/private/skills"))
    assertFalse(export.contains("/opt/hanakopro/skills"))
    assertTrue(export.contains("skills_error_present"))
    assertFalse(export.contains("assets.petdex.dev/pets/private"))
    assertFalse(export.contains("pet-token-secret"))
    assertFalse(export.contains("/root/private/pets"))
    assertFalse(export.contains("pid=4242"))
    assertFalse(export.contains("port=39473"))
    assertFalse(export.contains("running on 39473"))
    assertFalse(export.contains("version=hanako-secret-version"))
    assertFalse(export.contains("hanako-server@2026.5.3001"))
    assertFalse(export.contains("127.0.0.1:39473"))
    assertFalse(export.contains("listening on :39473"))
    assertTrue(export.contains("[redacted]"))
    assertTrue(export.contains("[path]"))
  }

  @Test
  fun diagnosticsExportNormalizesCurrentPetSourceBeforeExport() {
    val rawSourcePet = NbgPetSummary(
      slug = "raw-source-pet",
      displayName = "Raw Source Pet",
      kind = "character",
      submittedBy = "User",
      source = "/root/private/pets/source.json?token=pet-token-secret",
      spritePath = "/root/private/pets/raw/sprite.webp",
      petJsonPath = "/root/private/pets/raw/pet.json",
    )
    val export = nbgBuildDiagnosticsExportJson(
      sampleDiagnosticsSnapshot().copy(
        petState = NbgPetStoreState(
          installedPets = listOf(rawSourcePet),
          currentPetSlug = rawSourcePet.slug,
          hidden = false,
        ),
      ),
    )
    val pets = JSONObject(export).getJSONObject("pets")

    assertEquals(NbgPetResourceSourceKind.Unknown.wireName, pets.getString("currentSource"))
    assertEquals(1, pets.getInt("blockedLoadedCount"))
    assertFalse(export.contains("/root/private/pets/source.json"))
    assertFalse(export.contains("pet-token-secret"))
  }

  @Test
  fun diagnosticsExportCountsCanonicalPetDexProvenanceSources() {
    val dir = Files.createTempDirectory("nbg-petdex-provenance").toFile()
    val sprite = File(dir, "sprite.webp").apply { writeText("sprite") }
    File(dir, NBG_PET_PROVENANCE_FILE).writeText("{}")
    val petDexPet = NbgPetSummary(
      slug = "canonical-petdex",
      displayName = "Canonical PetDex",
      kind = "character",
      submittedBy = "PetDex",
      source = NbgPetResourceSourceKind.PetDexHttps.wireName,
      spritePath = sprite.absolutePath,
    )
    val export = nbgBuildDiagnosticsExportJson(
      sampleDiagnosticsSnapshot().copy(
        petState = NbgPetStoreState(
          installedPets = listOf(petDexPet),
          currentPetSlug = petDexPet.slug,
          hidden = false,
        ),
      ),
    )
    val pets = JSONObject(export).getJSONObject("pets")

    assertEquals(NbgPetResourceSourceKind.PetDexHttps.wireName, pets.getString("currentSource"))
    assertEquals(1, pets.getInt("unverifiedPetDexCount"))
    assertEquals(1, pets.getInt("petDexWithProvenanceCount"))
    assertEquals(0, pets.getInt("legacyPetDexWithoutProvenanceCount"))
  }

  @Test
  fun diagnosticsExportIncludesPetDexManifestProvenanceSummaryWithoutRawLocation() {
    val export = nbgBuildDiagnosticsExportJson(
      sampleDiagnosticsSnapshot().copy(
        petDexManifestProvenance = NbgPetDexManifestProvenance(
          schema = NBG_PET_RESOURCE_INTEGRITY_VERSION,
          sourceKind = NbgPetResourceSourceKind.PetDexHttps,
          trustTier = NbgPetTrustTier.UnverifiedPetDex,
          requiresReview = true,
          sourceHost = "petdex.dev",
          redactedSource = "https://petdex.dev/.../manifest",
          sizeBytes = 512L,
          sha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
          maxBytes = NBG_PETDEX_MANIFEST_MAX_BYTES,
          declaredTotal = 10,
          parsedPetCount = 8,
          fetchedAtMs = 789L,
        ),
      ),
    )
    val pets = JSONObject(export).getJSONObject("pets")

    assertTrue(pets.getBoolean("manifestProvenancePresent"))
    assertEquals(NbgPetResourceSourceKind.PetDexHttps.wireName, pets.getString("manifestSourceKind"))
    assertEquals(NbgPetTrustTier.UnverifiedPetDex.wireName, pets.getString("manifestTrustTier"))
    assertTrue(pets.getBoolean("manifestRequiresReview"))
    assertEquals(10, pets.getInt("manifestDeclaredTotal"))
    assertEquals(8, pets.getInt("manifestParsedPetCount"))
    assertEquals(789L, pets.getLong("manifestFetchedAtMs"))
    assertEquals("abcdef123456", pets.getString("manifestSha256Prefix"))
    assertFalse(export.contains("https://petdex.dev"))
    assertFalse(export.contains("petdex.dev"))
    assertFalse(export.contains("redactedSource"))
  }

  @Test
  fun diagnosticsExportIncludesStructuredLauncherFailureCodesWithoutRawValues() {
    val export = nbgBuildDiagnosticsExportJson(
      sampleDiagnosticsSnapshot().copy(
        launcher = NbgDiagnosticsLauncherSnapshot(
          serverInfoPresent = false,
          pidPresent = false,
          versionPresent = false,
          launchLogPresent = true,
          serverLogPresent = true,
          launchLogTail = listOf(
            "Failed to start HanakoPro launcher token=super-token-1234567890 /root/private/project",
            "[launcher] 12:00:00 run_in_ubuntu pack exit 44",
            "HanakoPro server pack and source not found. Put source in /root/HanakoPro",
          ),
          serverLogTail = listOf(
            "Error: listen EADDRINUSE 127.0.0.1:39473",
            "HanakoPro node_modules missing in /root/private/project. Run npm install there first.",
          ),
        ),
      ),
    )
    val launcher = JSONObject(export).getJSONObject("launcher")
    val codes = launcher.getJSONArray("failureCodes").toString()

    assertTrue(codes.contains("server_info_missing"))
    assertTrue(codes.contains("launcher_start_failed"))
    assertTrue(codes.contains("pack_exit_nonzero"))
    assertTrue(codes.contains("bundled_pack_extract_failed"))
    assertTrue(codes.contains("server_source_missing"))
    assertTrue(codes.contains("port_in_use"))
    assertTrue(codes.contains("node_modules_missing"))
    assertFalse(codes.contains("super-token-1234567890"))
    assertFalse(codes.contains("/root/private/project"))
    assertFalse(codes.contains("39473"))
    assertFalse(export.contains("super-token-1234567890"))
    assertFalse(export.contains("/root/private/project"))
    assertFalse(export.contains("127.0.0.1:39473"))
  }

  @Test
  fun diagnosticsExportSanitizesRegistryBuiltHanakoCapabilityMemoryErrors() {
    val base = sampleDiagnosticsSnapshot()
    val registry = nbgBuildCapabilityRegistry(
      connected = base.hanakoState.connected,
      connectionLabel = base.hanakoState.connectionLabel,
      lastError = RAW_MEMORY_ERROR,
      savedUrlApiCount = base.savedApis.size,
      mcpState = base.hanakoState.mcpState,
      mcpLoading = base.hanakoState.mcpLoading,
      mcpError = base.hanakoState.mcpError,
      skillsSnapshot = base.hanakoState.skillsSnapshot,
      skillsLoading = base.hanakoState.skillsLoading,
      skillsError = base.hanakoState.skillsError,
      memoryState = base.hanakoState.memoryState,
      memoryLoading = base.hanakoState.memoryLoading,
      memoryError = RAW_MEMORY_ERROR,
      fileShareState = base.fileShareState,
      petState = base.petState,
      petDexLoading = false,
      petDexError = null,
    )
    val export = nbgBuildDiagnosticsExportJson(base.copy(capabilities = registry))
    val json = JSONObject(export)
    val capabilities = json.getJSONArray("capabilities")
    val capabilityIds = (0 until capabilities.length()).map { capabilities.getJSONObject(it).getString("id") }

    assertEquals("memory_error_present", capabilities.getJSONObject(capabilityIds.indexOf("hanako")).getString("lastError"))
    assertEquals("memory_error_present", capabilities.getJSONObject(capabilityIds.indexOf("memory")).getString("lastError"))
    assertTrue(json.getJSONArray("recentErrors").toString().contains("hanako:memory_error_present"))
    assertTrue(json.getJSONArray("recentErrors").toString().contains("memory:memory_error_present"))
    assertFalse(export.contains("private-memory-query"))
    assertFalse(export.contains("memory-item-secret-id"))
    assertFalse(export.contains("private-memory-title"))
    assertFalse(export.contains("private-memory-tag"))
    assertFalse(export.contains("raw memory backend failure"))
  }

  @Test
  fun diagnosticTextRedactsCommonTokenForms() {
    val redacted = nbgRedactDiagnosticText(
      "Authorization: Bearer abc.def.ghi token=super-token api_key=sk-live-secret-1234567890 password ftp-secret pid=4242 port=39473 version=hanako-secret-version hanako-server@2026.5.3001 already running on 39473 observed healthy server on 39473 /root/private/file 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef /tmp/private/file",
    )

    assertFalse(redacted.contains("abc.def.ghi"))
    assertFalse(redacted.contains("super-token"))
    assertFalse(redacted.contains("sk-live-secret"))
    assertFalse(redacted.contains("ftp-secret"))
    assertFalse(redacted.contains("pid=4242"))
    assertFalse(redacted.contains("port=39473"))
    assertFalse(redacted.contains("hanako-secret-version"))
    assertFalse(redacted.contains("hanako-server@2026.5.3001"))
    assertFalse(redacted.contains("running on 39473"))
    assertFalse(redacted.contains("server on 39473"))
    assertFalse(nbgRedactDiagnosticText("Hanako Server 运行在 http://127.0.0.1:39473").contains("127.0.0.1:39473"))
    assertFalse(nbgRedactDiagnosticText("Hanako Server 运行在 http://localhost:39473").contains("localhost:39473"))
    assertFalse(nbgRedactDiagnosticText("Error: listen EADDRINUSE 127.0.0.1:39473").contains("127.0.0.1:39473"))
    assertFalse(nbgRedactDiagnosticText("Error: listen EADDRINUSE localhost:39473").contains("localhost:39473"))
    assertFalse(nbgRedactDiagnosticText("listening on :39473").contains(":39473"))
    assertFalse(redacted.contains("/root/private/file"))
    assertFalse(redacted.contains("0123456789abcdef"))
    assertFalse(redacted.contains("/tmp/private/file"))
    assertTrue(redacted.contains("[redacted]"))
    assertTrue(redacted.contains("[path]"))
    assertTrue(redacted.contains("[sha256]"))
  }

  @Test
  fun diagnosticLogTailReadsOnlyBoundedTailWindow() {
    val log = Files.createTempFile("nbg-diagnostics-tail", ".log").toFile()
    log.writeText(
      buildString {
        append("old-secret-token should-not-be-exported\n")
        repeat(70_000) { append('x') }
        append('\n')
        append("tail pid=4242 http://localhost:39473\n")
        append("tail end\n")
      },
    )

    val tail = nbgSafeDiagnosticTailForTest(log, maxLines = 4).joinToString("\n")

    assertFalse(tail.contains("old-secret-token"))
    assertFalse(tail.contains("pid=4242"))
    assertFalse(tail.contains("localhost:39473"))
    assertTrue(tail.contains("tail end"))
    assertTrue(tail.contains("tail pid="))
  }

  @Test
  fun runtimePatchSummarySupportsDiagnosticsDialogStatusLine() {
    val export = nbgBuildDiagnosticsExportJson(
      sampleDiagnosticsSnapshot(
        runtimePatch = NbgDiagnosticsRuntimePatchSnapshot(
          statusPresent = true,
          activeVersion = "runtime-patch-v1",
          patchSetVersion = "runtime-patch-v2",
          targetRuntimeVersion = "hanako-server-linux-arm64-node22",
          targetMarker = "size=1;sha256=same",
          activeMarker = "size=1;sha256=same",
          patches = listOf(
            NbgDiagnosticsRuntimePatchEntry(id = "applied", state = "applied"),
            NbgDiagnosticsRuntimePatchEntry(id = "safe-skip", state = "skipped", reason = "already-applied"),
          ),
        ),
      ),
    )
    val runtimePatch = JSONObject(export).getJSONObject("runtimePatch")

    assertEquals("stale", runtimePatch.getString("state"))
    assertTrue(runtimePatch.getBoolean("needsAttention"))
    assertFalse(runtimePatch.getBoolean("activePatchSetMatches"))
    assertTrue(runtimePatch.getBoolean("markerMatched"))
    assertEquals("matched", runtimePatch.getString("markerMatchState"))
    assertEquals(2, runtimePatch.getInt("patchCount"))
    assertEquals(1, runtimePatch.getInt("appliedCount"))
    assertEquals(1, runtimePatch.getInt("skippedCount"))
    assertEquals(0, runtimePatch.getInt("failedCount"))
    assertEquals(0, runtimePatch.getInt("attentionCount"))

    val summary = nbgDiagnosticsRuntimePatchUiSummary(export)
    assertTrue(summary.needsAttention)
    assertTrue(summary.text.contains("运行补丁：版本待重启"))
    assertTrue(summary.text.contains("active=runtime-patch-v1"))
    assertTrue(summary.text.contains("set=runtime-patch-v2"))
    assertTrue(summary.text.contains("marker=匹配"))
    assertTrue(summary.text.contains("applied=1"))
    assertTrue(summary.text.contains("skipped=1"))
    assertTrue(summary.text.contains("failed=0"))

    val missingSummary = nbgDiagnosticsRuntimePatchUiSummary(
      nbgBuildDiagnosticsExportJson(
        sampleDiagnosticsSnapshot(runtimePatch = NbgDiagnosticsRuntimePatchSnapshot()),
      ),
    )
    assertTrue(missingSummary.needsAttention)
    assertTrue(missingSummary.text.contains("运行补丁：未记录"))
  }

  @Test
  fun runtimePatchReaderExposesMarkerGateWithoutRawMarkersOrTargets() {
    val home = Files.createTempDirectory("nbg-runtime-patch").toFile()
    try {
      home.resolve("android-runtime-patches.active").writeText("runtime-patch-v1\n", Charsets.UTF_8)
      home.resolve("android-runtime-patches.json").writeText(
        """
        {
          "patchSetVersion": "runtime-patch-v1",
          "targetRuntimeVersion": "hanako-server-linux-arm64-node22",
          "targetMarker": "size=123;sha256=targetmarkersecret",
          "activeMarker": "size=456;sha256=activemarkersecret",
          "patches": [
            {
              "id": "android-default-workspace-root-v1",
              "state": "skipped",
              "reason": "target-marker-mismatch",
              "target": "/root/private/runtime.js",
              "error": "token=super-token-1234567890 /root/private/runtime.js"
            }
          ]
        }
        """.trimIndent(),
        Charsets.UTF_8,
      )

      val snapshot = nbgReadDiagnosticsRuntimePatchSnapshotFromHomes(listOf(home))
      val export = nbgBuildDiagnosticsExportJson(sampleDiagnosticsSnapshot(runtimePatch = snapshot))
      val runtimePatch = JSONObject(export).getJSONObject("runtimePatch")

      assertTrue(runtimePatch.getBoolean("statusPresent"))
      assertEquals("runtime-patch-v1", runtimePatch.getString("activeVersion"))
      assertEquals("runtime-patch-v1", runtimePatch.getString("patchSetVersion"))
      assertEquals("hanako-server-linux-arm64-node22", runtimePatch.getString("targetRuntimeVersion"))
      assertTrue(runtimePatch.getBoolean("activePatchSetMatches"))
      assertTrue(runtimePatch.getBoolean("targetMarkerPresent"))
      assertTrue(runtimePatch.getBoolean("activeMarkerPresent"))
      assertFalse(runtimePatch.getBoolean("markerMatched"))
      assertEquals("mismatched", runtimePatch.getString("markerMatchState"))
      assertEquals("failed", runtimePatch.getString("state"))
      assertEquals("android-default-workspace-root-v1", runtimePatch.getJSONArray("patches").getJSONObject(0).getString("id"))
      assertFalse(runtimePatch.getJSONArray("patches").getJSONObject(0).has("target"))
      assertFalse(export.contains("size=123;sha256=targetmarkersecret"))
      assertFalse(export.contains("size=456;sha256=activemarkersecret"))
      assertFalse(export.contains("/root/private/runtime.js"))
      assertFalse(export.contains("super-token-1234567890"))
    } finally {
      home.deleteRecursively()
    }
  }

  @Test
  fun runtimePatchReaderHandlesMissingAndCorruptStatusFiles() {
    val missingHome = Files.createTempDirectory("nbg-runtime-patch-missing").toFile()
    try {
      missingHome.resolve("android-runtime-patches.active").writeText("runtime-patch-v1\n", Charsets.UTF_8)
      val missingExport = nbgBuildDiagnosticsExportJson(
        sampleDiagnosticsSnapshot(runtimePatch = nbgReadDiagnosticsRuntimePatchSnapshotFromHomes(listOf(missingHome))),
      )
      val missingRuntimePatch = JSONObject(missingExport).getJSONObject("runtimePatch")

      assertFalse(missingRuntimePatch.getBoolean("statusPresent"))
      assertEquals("missing", missingRuntimePatch.getString("state"))
      assertEquals("runtime-patch-v1", missingRuntimePatch.getString("activeVersion"))
      assertEquals("unknown", missingRuntimePatch.getString("markerMatchState"))
    } finally {
      missingHome.deleteRecursively()
    }

    val corruptHome = Files.createTempDirectory("nbg-runtime-patch-corrupt").toFile()
    try {
      corruptHome.resolve("android-runtime-patches.json").writeText("{not-json", Charsets.UTF_8)
      val corruptExport = nbgBuildDiagnosticsExportJson(
        sampleDiagnosticsSnapshot(runtimePatch = nbgReadDiagnosticsRuntimePatchSnapshotFromHomes(listOf(corruptHome))),
      )
      val corruptRuntimePatch = JSONObject(corruptExport).getJSONObject("runtimePatch")

      assertTrue(corruptRuntimePatch.getBoolean("statusPresent"))
      assertEquals("corrupt", corruptRuntimePatch.getString("state"))
      assertTrue(corruptRuntimePatch.getBoolean("needsAttention"))
      assertTrue(corruptRuntimePatch.getString("error").isNotBlank())
    } finally {
      corruptHome.deleteRecursively()
    }
  }

  @Test
  fun diagnosticsExportIncludesMixedTerminalReadinessCounts() {
    val export = nbgBuildDiagnosticsExportJson(
      sampleDiagnosticsSnapshot(
        terminalReadiness = listOf(
          TerminalReadinessSnapshot(readiness = TerminalReadiness.Ready, phase = TerminalStartupPhase.Ready),
          TerminalReadinessSnapshot(readiness = TerminalReadiness.Installing, phase = TerminalStartupPhase.NodeRuntime),
          TerminalReadinessSnapshot(
            readiness = TerminalReadiness.Failed,
            phase = TerminalStartupPhase.Failed,
            diagnostic = "exit_code=7",
          ),
        ),
      ),
    )
    val json = JSONObject(export)
    val terminal = json.getJSONObject("terminal")

    assertEquals(3, terminal.getInt("tabCountWithReadiness"))
    assertEquals(1, terminal.getInt("readyCount"))
    assertEquals(1, terminal.getInt("installingCount"))
    assertEquals(1, terminal.getInt("failedCount"))
    assertTrue(json.getJSONArray("recentErrors").toString().contains("terminal.2"))
  }

  @Test
  fun diagnosticsExportRefreshesStaleTerminalCapabilityFromReadinessCounts() {
    val export = nbgBuildDiagnosticsExportJson(
      sampleDiagnosticsSnapshot(
        terminalReadiness = listOf(
          TerminalReadinessSnapshot(
            readiness = TerminalReadiness.Failed,
            phase = TerminalStartupPhase.Failed,
            diagnostic = "exit_code=7",
          ),
        ),
      ).copy(
        capabilities = NbgCapabilityRegistry(
          listOf(
            NbgCapability(
              id = "terminal",
              title = "终端",
              status = "本地",
              health = NbgCapabilityHealth.Healthy,
              diagnosticsAction = "terminal_startup",
            ),
          ),
        ),
      ),
    )
    val json = JSONObject(export)
    val terminalCapability = json.getJSONArray("capabilities").getJSONObject(0)
    val terminal = json.getJSONObject("terminal")

    assertEquals("terminal", terminalCapability.getString("id"))
    assertEquals("failed", terminalCapability.getString("health"))
    assertEquals("1 失败", terminalCapability.getString("status"))
    assertEquals("exit_code=7", terminalCapability.getString("lastError"))
    assertEquals(1, terminal.getInt("failedCount"))
    assertTrue(json.getJSONArray("recentErrors").toString().contains("terminal:exit_code=7"))
  }

  @Test
  fun diagnosticsExportAttachmentFileWriterCreatesJsonAttachmentAndPrunesOldExports() {
    val cacheDir = Files.createTempDirectory("nbg-diagnostics-share").toFile()
    try {
      val diagnosticsDir = File(cacheDir, NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_DIR)
      assertTrue(diagnosticsDir.mkdirs())
      val oldExport = File(diagnosticsDir, "nbg-diagnostics-1.json").apply {
        writeText("old", Charsets.UTF_8)
      }
      val unrelated = File(diagnosticsDir, "unrelated.json").apply {
        writeText("keep", Charsets.UTF_8)
      }
      val exportText = """{"schema":"nbg-diagnostics-v1","token":"[redacted]"}"""

      val file = nbgWriteDiagnosticsExportAttachmentFile(
        cacheDir = cacheDir,
        exportText = exportText,
        generatedAtMs = 1234L,
      )

      assertEquals("nbg-diagnostics-1234.json", file.name)
      assertEquals(exportText, file.readText(Charsets.UTF_8))
      assertEquals(diagnosticsDir.canonicalFile, file.parentFile?.canonicalFile)
      assertFalse(oldExport.exists())
      assertTrue(unrelated.exists())
    } finally {
      cacheDir.deleteRecursively()
    }
  }

  private fun sampleDiagnosticsSnapshot(
    runtimePatch: NbgDiagnosticsRuntimePatchSnapshot = NbgDiagnosticsRuntimePatchSnapshot(
      statusPresent = true,
      activeVersion = "android-patch-v1",
      patchSetVersion = "android-patch-v1",
      targetRuntimeVersion = "hanako-server-linux-arm64-node22",
      targetMarker = "size=1;sha256=target",
      activeMarker = "size=2;sha256=active",
      patches = listOf(
        NbgDiagnosticsRuntimePatchEntry(
          id = "upstream-user-agent",
          state = "skipped",
          reason = "target-marker-mismatch",
          error = "failed token=super-token-1234567890 /root/private/project",
        ),
      ),
    ),
    terminalReadiness: List<TerminalReadinessSnapshot> = listOf(
      TerminalReadinessSnapshot(
        readiness = TerminalReadiness.Failed,
        phase = TerminalStartupPhase.Failed,
        diagnostic = "exit_code=1 /root/private/project",
      ),
    ),
  ): NbgDiagnosticsExportSnapshot =
    NbgDiagnosticsExportSnapshot(
      generatedAtMs = 1000L,
      app = NbgDiagnosticsAppInfo(
        packageName = "com.nbg.android.test",
        versionName = "0.1-test",
        versionCode = 7,
      ),
      device = NbgDiagnosticsDeviceInfo(
        manufacturer = "Google",
        model = "Pixel",
        sdkInt = 34,
        androidRelease = "14",
      ),
      capabilities = NbgCapabilityRegistry(
        listOf(
          NbgCapability(
            id = "hanako",
            title = "HanakoPro",
            status = "未连接",
            health = NbgCapabilityHealth.Failed,
            lastError = "Authorization: Bearer super-token-1234567890 failed at /root/private/project",
            diagnosticsAction = "hanako_server_state",
          ),
          NbgCapability(
            id = "url_api",
            title = "URL API",
            status = "1 provider",
            health = NbgCapabilityHealth.Healthy,
            diagnosticsAction = "url_api_settings",
          ),
          NbgCapability(
            id = "memory",
            title = "Memory",
            status = "失败",
            health = NbgCapabilityHealth.Failed,
            lastError = RAW_MEMORY_ERROR,
            diagnosticsAction = "memory_state",
          ),
        ),
      ),
      hanakoState = HanakoChatState(
        connected = false,
        connectionLabel = "未连接 token=super-token-1234567890",
        lastError = RAW_MEMORY_ERROR,
        sessionPath = "/storage/emulated/0/Download/private.txt",
        sessions = listOf(
          HanakoSessionSummary(path = "a", title = "one", subtitle = ""),
          HanakoSessionSummary(path = "b", title = "two", subtitle = ""),
        ),
        permissionMode = "ask",
        memoryState = HanakoMemoryState(
          items = listOf(HanakoMemoryItem(id = "m1", content = "memory body should never export")),
          count = 2,
          enabledCount = 1,
          auditEvents = listOf(
            HanakoMemoryAuditEvent(
              action = "create",
              result = "blocked",
              normalizedType = "user_preference",
              risk = NbgMemoryContextRisk.BlockedSensitive.wireName,
              timestampMillis = 100L,
            ),
            HanakoMemoryAuditEvent(
              action = "delete",
              result = "success",
              timestampMillis = 101L,
            ),
          ),
        ),
        memoryError = RAW_MEMORY_ERROR,
        mcpState = HanakoMcpState(
          enabled = true,
          connectors = listOf(
            HanakoMcpConnector(
              id = "mcp-secret-command",
              name = "private connector",
              command = "mcp-secret-command",
              args = listOf("--token", "super-token-1234567890"),
              cwd = "/root/private/project",
              status = "running",
              tools = listOf(HanakoMcpTool("tool-a")),
              envCount = 1,
              headersCount = 1,
            ),
          ),
        ),
        skillsSnapshot = HanakoSkillsSnapshot(
          skills = listOf(
            HanakoSkillSummary(
              name = "private skill path",
              enabled = true,
              filePath = "/root/private/project/SKILL.md",
            ),
          ),
        ),
        skillsError = "nbg-engineering-core failed 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef /tmp/private/skills /opt/hanakopro/skills",
        streamResumeDiagnostics = HanakoStreamResumeDiagnostics(
          requestCount = 3,
          resumeCount = 2,
          replayedEventCount = 5,
          acceptedReplayEventCount = 4,
          skippedReplayEventCount = 1,
          duplicateReplayEventCount = 1,
          truncatedResumeCount = 1,
          resetResumeCount = 1,
          cursorPresent = true,
          lastSeq = 42,
        ),
      ),
      savedApis = listOf(
        NbgStoredApi(
          id = "api-secret-id",
          name = "prod sk-live-secret-1234567890",
          baseUrl = "https://api.example.com/v1?api_key=sk-live-secret-1234567890",
          apiKey = "sk-live-secret-1234567890",
          models = listOf(NbgApiModel("gpt-a"), NbgApiModel("gpt-b")),
          verifiedModelIds = setOf("gpt-b"),
          selectedModelId = "gpt-b",
          updatedAtMs = 42L,
        ),
      ),
      fileShareState = NbgFileShareServerState(
        running = true,
        port = 43211,
        localUrl = "127.0.0.1:43211",
        accessMode = NbgFileShareAccessMode.ReadWrite,
        password = "ftp-password-123456",
        hostRoot = "/root/private/project",
        message = "failed password=ftp-password-123456",
      ),
      petState = NbgPetStoreState(
        installedPets = listOf(
          nbgDefaultPetSummary(),
          NbgPetSummary(
            slug = "private-pet",
            displayName = "Private Pet",
            kind = "character",
            submittedBy = "PetDex",
            source = "petdex",
            spritesheetUrl = "https://assets.petdex.dev/pets/private/sprite.webp?token=pet-token-secret",
            petJsonUrl = "https://assets.petdex.dev/pets/private/petjson.json?token=pet-token-secret",
            spritePath = "/root/private/pets/sprite.webp",
            petJsonPath = "/root/private/pets/pet.json",
            installedAtMs = 100L,
          ),
          NbgPetSummary(
            slug = "local-private-pet",
            displayName = "Local Private Pet",
            kind = "character",
            submittedBy = "User",
            source = "side-loaded",
            spritesheetUrl = "file:///root/private/pets/local/sprite.webp",
            petJsonUrl = "file:///root/private/pets/local/pet.json",
            spritePath = "/root/private/pets/local/sprite.webp",
            petJsonPath = "/root/private/pets/local/pet.json",
            installedAtMs = 101L,
          ),
        ),
        currentPetSlug = NBG_DEFAULT_PET_SLUG,
        hidden = false,
      ),
      terminalReadiness = terminalReadiness,
      launcher = NbgDiagnosticsLauncherSnapshot(
        serverInfoPresent = true,
        pidPresent = true,
        versionPresent = true,
        launchLogPresent = true,
        serverLogPresent = true,
        launchLogTail = listOf("launch token=super-token-1234567890 pid=4242 port=39473 version=hanako-secret-version already running on 39473 /root/private/project"),
        serverLogTail = listOf("server password=ftp-password-123456 hanako-server@2026.5.3001 observed healthy server on 39473 Hanako Server 运行在 http://127.0.0.1:39473 listening on :39473"),
      ),
      runtimePatch = runtimePatch,
    )
}

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

internal class HanakoServerLauncher {
  val isLaunching: Boolean
    get() = launching.get()

  fun startIfNeeded(context: Context) {
    if (!launching.compareAndSet(false, true)) return
    val startMs = System.currentTimeMillis()
    fun mark(stage: String) {
      Log.i("NBG_HANAKO", "HanakoPro launcher prep $stage +${System.currentTimeMillis() - startMs}ms")
    }
    try {
      val environment = TerminalEnvironment(context)
      mark("environment-created")
      val commonScript = environment.prepare()
      val processEnvironment = environment.processEnvironment()
      Log.i("NBG_HANAKO", "HanakoPro launcher android proxy=${if (processEnvironment["HTTP_PROXY"].isNullOrBlank()) "none" else "detected"}")
      mark("environment-prepared")
      val filesDir = commonScript.parentFile ?: error("Missing app files dir")
      val packFile = ensureBundledServerPack(context, filesDir)
      mark("pack-ready")
      val logFile = File(filesDir, "hanakopro-launch.log")
      logFile.writeText("[launcher] ${Instant.now()} android start\n", Charsets.UTF_8)
      writeAndroidHanakoDefaults(context, filesDir)
      mark("defaults-written")
      val bash = File(filesDir, "usr/bin/bash")
      val launcherScript = File(filesDir, "launch_hanakopro_server.sh")
      launcherScript.writeTextIfChanged(renderLauncherScript(commonScript, packFile))
      if (!launcherScript.canExecute()) launcherScript.setExecutable(true, false)
      mark("script-written")
      val process = ProcessBuilder(bash.absolutePath, launcherScript.absolutePath)
        .directory(filesDir)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        .apply {
          environment().putAll(processEnvironment)
        }
        .start()
      mark("process-started")
      watchLauncherProcess(context, process)
      Log.i("NBG_HANAKO", "HanakoPro launcher started, log=${logFile.absolutePath}")
    } catch (error: Throwable) {
      launching.set(false)
      Log.w("NBG_HANAKO", "Failed to start HanakoPro launcher", error)
      throw error
    }
  }

  private fun watchLauncherProcess(context: Context, process: Process) {
    Thread({
      runCatching {
        val deadline = System.currentTimeMillis() + LAUNCHER_GUARD_TIMEOUT_SECONDS * 1_000L
        while (System.currentTimeMillis() < deadline) {
          if (!process.isAlive) return@runCatching
          val info = findServerInfo(context)
          if (info != null && HanakoApiClient().isHealthy(info)) {
            Log.i("NBG_HANAKO", "HanakoPro launcher observed healthy server on ${info.port}")
            return@runCatching
          }
          Thread.sleep(250L)
        }
        if (process.isAlive) {
          Log.i("NBG_HANAKO", "HanakoPro launcher still supervising proot after ${LAUNCHER_GUARD_TIMEOUT_SECONDS}s")
        }
      }
      launching.set(false)
    }, "hanakopro-launcher-watch").apply {
      isDaemon = true
      start()
    }
  }

  private fun ensureBundledServerPack(context: Context, filesDir: File): File {
    val target = File(filesDir, HANAKO_SERVER_PACK)
    val marker = File(filesDir, HANAKO_SERVER_PACK_MARKER)
    val signature = bundledServerPackSignature(context)
    val currentSignature = runCatching {
      if (marker.isFile) marker.readText(Charsets.UTF_8).trim() else null
    }.getOrNull()
    if (target.isFile && currentSignature == signature) return target

    val tmp = File(filesDir, "$HANAKO_SERVER_PACK.tmp")
    runCatching {
      tmp.delete()
      context.assets.open(HANAKO_SERVER_PACK).use { input ->
        tmp.outputStream().use { output -> input.copyTo(output) }
      }
      Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      marker.writeText("$signature\n", Charsets.UTF_8)
    }.onFailure { error ->
      tmp.delete()
      if (target.isFile) {
        Log.w("NBG_HANAKO", "Bundled HanakoPro pack copy failed; using existing data copy", error)
        return target
      }
      throw error
    }
    return target
  }

  private fun bundledServerPackSignature(context: Context): String {
    val bundledMarker = runCatching {
      context.assets.open(HANAKO_SERVER_PACK_MARKER).bufferedReader(Charsets.UTF_8).use { reader ->
        reader.readText().trim()
      }
    }.getOrNull()
    if (!bundledMarker.isNullOrBlank()) return bundledMarker

    val packageInfo = runCatching {
      context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val sourceApk = File(context.applicationInfo.sourceDir)
    val lastUpdateTime = packageInfo?.lastUpdateTime ?: runCatching { sourceApk.lastModified() }.getOrDefault(0L)
    val sourceSize = runCatching { sourceApk.length() }.getOrDefault(0L)
    return "${context.packageName}:$lastUpdateTime:$sourceSize"
  }

  private fun writeAndroidHanakoDefaults(context: Context, filesDir: File) {
    val ubuntuRoot = File(filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu")
    val hanaHome = File(ubuntuRoot, "root/.hanakopro")
    nbgDeletePathWithoutFollowingSymlink(File(ubuntuRoot, "root/Desktop/OH-WorkSpace"))
    File(ubuntuRoot, "root/Desktop").takeIf { it.isDirectory && it.list()?.isEmpty() == true }?.delete()
    val prefsDir = File(hanaHome, "user")
    prefsDir.mkdirs()
    val prefsPath = File(prefsDir, "preferences.json")
    val prefs = runCatching {
      if (prefsPath.isFile) JSONObject(prefsPath.readText(Charsets.UTF_8)) else JSONObject()
    }.getOrElse { JSONObject() }
    val learnSkills = prefs.optJSONObject("learn_skills") ?: JSONObject()
    prefs
      .put("primaryAgent", "hanako")
      .put("sandbox", false)
      .put("sandbox_network", true)
      .put("android_mobile", true)
      .put(
        "learn_skills",
        learnSkills
          .put("enabled", true)
          .put("allow_github_fetch", true)
          .put("safety_review", true)
          .put("min_stars", 0),
      )
    prefsPath.writeTextIfChanged(prefs.toString(2) + "\n")
    removeNbgDeletedBuiltinSkills(hanaHome)
    val verifiedDefaultSkills = seedNbgDefaultSkills(context, hanaHome)

    val agentsDir = File(hanaHome, "agents")
    agentsDir.listFiles()?.forEach { agentDir ->
      val cfg = File(agentDir, "config.yaml")
      if (!cfg.isFile) return@forEach
      val text = cfg.readText(Charsets.UTF_8)
      val next = ensureAndroidAgentEnabledSkills(
        ensureAndroidAgentDeskHomeFolder(text),
        verifiedDefaultSkills,
      )
      if (next != text) cfg.writeText(next, Charsets.UTF_8)
    }
  }

  private fun seedNbgDefaultSkills(context: Context, hanaHome: File): List<String> {
    val skillsDir = File(hanaHome, "skills")
    skillsDir.mkdirs()
    return NBG_ANDROID_BUNDLED_SKILL_NAMES.mapNotNull { skillName ->
      val review = nbgReviewBundledSkillAssets(skillName) { relativePath ->
        runCatching {
          context.assets.open(nbgBundledSkillAssetPath(skillName, relativePath)).use { input -> input.readBytes() }
        }.getOrNull()
      }
      if (!review.trustedDefaultEligible) {
        Log.e("NBG_HANAKO", "Bundled Skill integrity check failed for $skillName: ${review.reason}")
        nbgDeletePathWithoutFollowingSymlink(File(skillsDir, skillName))
        return@mapNotNull null
      }
      copyAssetTreeIfChanged(
        context = context,
        assetPath = "$NBG_DEFAULT_SKILLS_ASSET_ROOT/$skillName",
        target = File(skillsDir, skillName),
      )
      skillName
    }
  }

  private fun removeNbgDeletedBuiltinSkills(hanaHome: File) {
    val skillsDir = File(hanaHome, "skills")
    NBG_ANDROID_REMOVED_BUILTIN_SKILLS.forEach { skillName ->
      nbgDeletePathWithoutFollowingSymlink(File(skillsDir, skillName))
    }
  }

  private fun copyAssetTreeIfChanged(context: Context, assetPath: String, target: File) {
    val children = context.assets.list(assetPath).orEmpty()
    if (children.isEmpty()) {
      copyAssetFileIfChanged(context, assetPath, target)
      return
    }
    target.mkdirs()
    children.forEach { child ->
      copyAssetTreeIfChanged(
        context = context,
        assetPath = "$assetPath/$child",
        target = File(target, child),
      )
    }
  }

  private fun copyAssetFileIfChanged(context: Context, assetPath: String, target: File) {
    val bytes = context.assets.open(assetPath).use { input -> input.readBytes() }
    if (target.isFile && runCatching { target.readBytes().contentEquals(bytes) }.getOrDefault(false)) return
    target.parentFile?.mkdirs()
    val tmp = File(target.parentFile ?: target.absoluteFile.parentFile, "${target.name}.tmp")
    tmp.outputStream().use { output -> output.write(bytes) }
    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }

  private fun ensureAndroidAgentDeskHomeFolder(text: String): String =
    when {
      Regex("""(?m)home_folder:\s*["']?/root/?["']?""").containsMatchIn(text) -> text
      Regex("""(?m)home_folder:\s*.*$""").containsMatchIn(text) ->
        text.replace(Regex("""(?m)home_folder:\s*.*$"""), "home_folder: /root/")
      Regex("""(?m)^desk:\s*$""").containsMatchIn(text) ->
        text.replace(Regex("""(?m)^desk:\s*$"""), "desk:\n  home_folder: /root/")
      else -> text.trimEnd() + "\ndesk:\n  home_folder: /root/\n"
    }

  private fun ensureAndroidAgentEnabledSkills(text: String, required: List<String>): String {
    val skillsBlockRegex = Regex("""(?m)^skills:\s*\n(?:^[ \t]+.*(?:\n|$))*""")
    val match = skillsBlockRegex.find(text)
    val existing = match?.value?.extractYamlSkillNames().orEmpty()
    val merged = (existing + required)
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .filterNot { it in NBG_ANDROID_BUNDLED_SKILL_NAMES && it !in required }
      .filterNot { it in NBG_ANDROID_REMOVED_BUILTIN_SKILLS }
      .distinct()
    val skillsBlock = buildString {
      append("skills:\n")
      append("  enabled:\n")
      merged.forEach { name -> append("    - ").append(name).append('\n') }
    }
    return if (match != null) {
      text.replaceRange(match.range, skillsBlock)
    } else {
      text.trimEnd().let { prefix -> if (prefix.isEmpty()) skillsBlock else "$prefix\n$skillsBlock" }
    }
  }

  private fun String.extractYamlSkillNames(): List<String> {
    val quoted = Regex("""["']([A-Za-z0-9_-]+)["']""")
      .findAll(this)
      .map { it.groupValues[1] }
      .toList()
    val bullets = Regex("""(?m)^\s*-\s*([A-Za-z0-9_-]+)\s*$""")
      .findAll(this)
      .map { it.groupValues[1] }
      .toList()
    return (quoted + bullets).distinct()
  }

  private fun File.writeTextIfChanged(text: String) {
    val current = if (isFile) runCatching { readText(Charsets.UTF_8) }.getOrNull() else null
    if (current == text) return
    writeText(text, Charsets.UTF_8)
  }

  private fun renderRuntimePatchPrelude(sourceFallback: Boolean): String {
    val targetMarker = if (sourceFallback) "source-fallback" else NBG_HANAKO_RUNTIME_PATCH_TARGET_PACK_MARKER
    val targetRuntimeVersion = if (sourceFallback) {
      NBG_HANAKO_RUNTIME_SOURCE_FALLBACK_VERSION
    } else {
      NBG_HANAKO_RUNTIME_TARGET_VERSION
    }
    val activeMarker = if (sourceFallback) {
      "source-fallback"
    } else {
      "${'$'}(cat /opt/hanakopro-server/.nbgpack.size 2>/dev/null || true)"
    }
    return """
      export NBG_HANAKO_RUNTIME_PATCH_SET_VERSION="$NBG_HANAKO_RUNTIME_PATCH_SET_VERSION"
      export NBG_HANAKO_RUNTIME_TARGET_VERSION="$targetRuntimeVersion"
      export NBG_HANAKO_RUNTIME_PATCH_TARGET_MARKER="$targetMarker"
      export NBG_HANAKO_PACK_MARKER="$activeMarker"
      export NBG_HANAKO_RUNTIME_PATCH_STATUS="${'$'}HANA_HOME/android-runtime-patches.json"
      export NBG_HANAKO_RUNTIME_PATCH_RECORDER="${'$'}HANA_HOME/android-runtime-patch-recorder.cjs"
      NBG_HANAKO_RUNTIME_PATCH_ACTIVE_VERSION="$(cat "${'$'}HANA_HOME/android-runtime-patches.active" 2>/dev/null || true)"
      export NBG_HANAKO_RUNTIME_PATCH_ACTIVE_VERSION
      cat > "${'$'}NBG_HANAKO_RUNTIME_PATCH_RECORDER" <<'NODE_RUNTIME_PATCH_RECORDER'
      const fs = require("fs");
      function env(name, fallback = "") {
        return process.env[name] || fallback;
      }
      function context() {
        return {
          patchSetVersion: env("NBG_HANAKO_RUNTIME_PATCH_SET_VERSION"),
          targetRuntimeVersion: env("NBG_HANAKO_RUNTIME_TARGET_VERSION"),
          targetMarker: env("NBG_HANAKO_RUNTIME_PATCH_TARGET_MARKER"),
          activeMarker: env("NBG_HANAKO_PACK_MARKER"),
        };
      }
      function targetMatches() {
        const ctx = context();
        return Boolean(ctx.activeMarker && ctx.targetMarker && ctx.activeMarker === ctx.targetMarker);
      }
      function record(id, state, target, error = "", extra = {}) {
        const statusPath = env("NBG_HANAKO_RUNTIME_PATCH_STATUS");
        if (!statusPath || !id) return;
        const now = new Date().toISOString();
        const ctx = context();
        let root = { patches: [] };
        try {
          root = JSON.parse(fs.readFileSync(statusPath, "utf8"));
        } catch {}
        const previous = Array.isArray(root.patches) ? root.patches : [];
        const entry = {
          id,
          state,
          target,
          error: error ? String(error).slice(0, 500) : undefined,
          updatedAt: now,
          ...ctx,
          ...extra,
        };
        root.patchSetVersion = ctx.patchSetVersion;
        root.targetRuntimeVersion = ctx.targetRuntimeVersion;
        root.targetMarker = ctx.targetMarker;
        root.activeMarker = ctx.activeMarker;
        root.updatedAt = now;
        root.patches = previous.filter((patch) => patch && patch.id !== id).concat(entry);
        fs.writeFileSync(statusPath, JSON.stringify(root, null, 2) + "\n");
      }
      module.exports = { context, record, targetMatches };
      NODE_RUNTIME_PATCH_RECORDER
      record_android_runtime_patch() {
        node - "${'$'}1" "${'$'}2" "${'$'}3" "${'$'}{4:-}" <<'NODE_RECORD_RUNTIME_PATCH' || true
      const { record } = require(process.env.NBG_HANAKO_RUNTIME_PATCH_RECORDER);
      record(process.argv[2] || "", process.argv[3] || "skipped", process.argv[4] || "", process.argv[5] || "");
      NODE_RECORD_RUNTIME_PATCH
      }
      echo "[patch] $(ts) runtime patch set ${'$'}NBG_HANAKO_RUNTIME_PATCH_SET_VERSION marker=${'$'}NBG_HANAKO_PACK_MARKER status=${'$'}NBG_HANAKO_RUNTIME_PATCH_STATUS"
    """.trimIndent()
  }

  private fun renderDefaultWorkspacePatch(targetExpression: String): String =
    """
      node - <<'NODE_PATCH' || true
      const fs = require("fs");
      const { record, targetMatches } = require(process.env.NBG_HANAKO_RUNTIME_PATCH_RECORDER);
      const patchId = "android-default-workspace-root-v1";
      const target = $targetExpression;
      if (!targetMatches()) {
        record(patchId, "skipped", target, "", { reason: "target-marker-mismatch" });
        process.exit(0);
      }
      let text = "";
      try {
        text = fs.readFileSync(target, "utf8");
      } catch (error) {
        record(patchId, "skipped", target, error.message, { reason: "target-missing" });
        process.exit(0);
      }
      if (text.includes("return path.resolve(\"/root\");")) {
        record(patchId, "skipped", target, "", { reason: "already-applied" });
        process.exit(0);
      }
      const pattern = /return path\.join\(homeDir,\s*"Desktop",\s*DEFAULT_WORKSPACE_DIRNAME\);/;
      if (!pattern.test(text)) {
        record(patchId, "skipped", target, "", { reason: "pattern-missing" });
        process.exit(0);
      }
      try {
        fs.writeFileSync(target, text.replace(pattern, "return path.resolve(\"/root\");"));
        record(patchId, "applied", target);
      } catch (error) {
        record(patchId, "failed", target, error.message);
      }
      NODE_PATCH
    """.trimIndent()

  private fun renderChatToolDetailsPatch(targetExpression: String): String =
    """
      node - <<'NODE_PATCH' || true
      const fs = require("fs");
      const { record, targetMatches } = require(process.env.NBG_HANAKO_RUNTIME_PATCH_RECORDER);
      const patchId = "android-chat-tool-details-v1";
      const target = $targetExpression;
      if (!targetMatches()) {
        record(patchId, "skipped", target, "", { reason: "target-marker-mismatch" });
        process.exit(0);
      }
      let text = "";
      try {
        text = fs.readFileSync(target, "utf8");
      } catch (error) {
        record(patchId, "skipped", target, error.message, { reason: "target-missing" });
        process.exit(0);
      }
      let changed = false;
      const changes = [];
      if (!text.includes("output: typeof event.output === \"string\" ? event.output : undefined")) {
        const before = "        error: event.error,\n        warning: event.warning,\n      });";
        const after = "        error: event.error,\n        warning: event.warning,\n        output: typeof event.output === \"string\" ? event.output : undefined,\n        stdout: typeof event.stdout === \"string\" ? event.stdout : undefined,\n        stderr: typeof event.stderr === \"string\" ? event.stderr : undefined,\n      });";
        if (text.includes(before)) {
          text = text.replace(before, after);
          changed = true;
          changes.push("tool-event-output");
        }
      }
      if (!text.includes("details: normalizeAndroidToolResultDetails(event.result)")) {
        const helper =
          "function normalizeAndroidToolResultDetails(result) {\n" +
          "  const base = result && result.details && typeof result.details === \"object\" ? { ...result.details } : {};\n" +
          "  const output = extractText(result && result.content);\n" +
          "  if (output && !base.output && !base.stdout && !base.stderr) base.output = output;\n" +
          "  return Object.keys(base).length ? base : undefined;\n" +
          "}\n";
        if (text.includes("function extractText(content) {") && text.includes("        details: event.result?.details,")) {
          text = text.replace("function extractText(content) {", helper + "\nfunction extractText(content) {");
          text = text.replace("        details: event.result?.details,", "        details: normalizeAndroidToolResultDetails(event.result),");
          changed = true;
          changes.push("tool-result-details");
        }
      }
      if (!changed) {
        const alreadyApplied =
          text.includes("output: typeof event.output === \"string\" ? event.output : undefined") &&
          text.includes("details: normalizeAndroidToolResultDetails(event.result)");
        record(patchId, "skipped", target, "", { reason: alreadyApplied ? "already-applied" : "pattern-missing" });
        process.exit(0);
      }
      try {
        fs.writeFileSync(target, text);
        record(patchId, "applied", target, "", { changes });
      } catch (error) {
        record(patchId, "failed", target, error.message, { changes });
      }
      NODE_PATCH
    """.trimIndent()

  private fun renderMemoryContextPolicyPatch(targetExpression: String): String =
    """
      node - <<'NODE_PATCH' || true
      const fs = require("fs");
      const { record, targetMatches } = require(process.env.NBG_HANAKO_RUNTIME_PATCH_RECORDER);
      const patchId = "android-memory-context-policy-v1";
      const target = $targetExpression;
      if (!targetMatches()) {
        record(patchId, "skipped", target, "", { reason: "target-marker-mismatch" });
        process.exit(0);
      }
      let text = "";
      try {
        text = fs.readFileSync(target, "utf8");
      } catch (error) {
        record(patchId, "skipped", target, error.message, { reason: "target-missing" });
        process.exit(0);
      }
      if (text.includes("NBG_MEMORY_CONTEXT_POLICY_VERSION")) {
        record(patchId, "skipped", target, "", { reason: "already-applied" });
        process.exit(0);
      }
      const policyBlock = `
      const NBG_MEMORY_CONTEXT_POLICY_VERSION = "nbg-memory-context-v1";
      const NBG_MEMORY_SENSITIVE_PATTERNS = [
        { label: "private_key", regex: /-----BEGIN [A-Z ]*PRIVATE KEY-----/ },
        { label: "bearer_token", regex: /\\bbearer\\s+[A-Za-z0-9._~+/=-]{16,}/i },
        { label: "openai_style_key", regex: /\\bsk-[A-Za-z0-9_-]{12,}\\b/ },
        { label: "secret_assignment", regex: /\\b(api[_-]?key|access[_-]?key|token|refresh[_-]?token|password|secret)\\b\\s*[:=]\\s*["']?[^"'\\s,}]{8,}/i },
        { label: "github_token", regex: /\\bgh[pousr]_[A-Za-z0-9_]{20,}\\b/ },
        { label: "aws_access_key", regex: /\\bAKIA[0-9A-Z]{16}\\b/ },
      ];

      function memoryPolicyText(input = {}) {
        const lines = [
          input.title,
          input.content,
          ...(Array.isArray(input.tags) ? input.tags : []),
        ];
        return lines.map((value) => String(value || "")).join("\\n");
      }

      function memorySensitiveFindings(input = {}) {
        const text = memoryPolicyText(input);
        if (!text.trim()) return [];
        return NBG_MEMORY_SENSITIVE_PATTERNS
          .filter((pattern) => pattern.regex.test(text))
          .map((pattern) => pattern.label);
      }

      function reviewMemoryPolicy(input = {}) {
        const content = String(input.content || "").replace(/\\r\\n/g, "\\n").trim();
        if (!content) {
          return { policyVersion: NBG_MEMORY_CONTEXT_POLICY_VERSION, risk: "blocked_empty", allowSave: false, allowContextInjection: false, findingCount: 0 };
        }
        if (content.length > MAX_CONTENT_CHARS) {
          return { policyVersion: NBG_MEMORY_CONTEXT_POLICY_VERSION, risk: "blocked_too_large", allowSave: false, allowContextInjection: false, findingCount: 0 };
        }
        const findings = memorySensitiveFindings(input);
        if (findings.length) {
          return { policyVersion: NBG_MEMORY_CONTEXT_POLICY_VERSION, risk: "blocked_sensitive", allowSave: false, allowContextInjection: false, findingCount: findings.length };
        }
        return { policyVersion: NBG_MEMORY_CONTEXT_POLICY_VERSION, risk: "low", allowSave: true, allowContextInjection: true, findingCount: 0 };
      }

      function assertMemoryPolicyForSave(input = {}) {
        const review = reviewMemoryPolicy(input);
        if (!review.allowSave) {
          const error = new Error("memory policy blocked: " + review.risk);
          error.code = review.risk;
          error.policyVersion = review.policyVersion;
          error.findingCount = review.findingCount;
          throw error;
        }
        return review;
      }

      function memoryPolicyAllowsContext(item) {
        return reviewMemoryPolicy(item).allowContextInjection;
      }
      `;
      const anchor = "const MAX_CONTEXT_CHARS = 1800;";
      if (!text.includes(anchor)) {
        record(patchId, "skipped", target, "", { reason: "pattern-missing", step: "policy-anchor" });
        process.exit(0);
      }
      text = text.replace(anchor, anchor + "\n" + policyBlock.trimEnd());
      const normalizeSignature = "function normalizeItem(input = {}, existing = null) {";
      if (!text.includes(normalizeSignature)) {
        record(patchId, "skipped", target, "", { reason: "pattern-missing", step: "normalize-signature" });
        process.exit(0);
      }
      text = text.replace(normalizeSignature, "function normalizeItem(input = {}, existing = null, options = {}) {");
      const contentPattern = /  const title = cleanText\(input\.title \|\| existing\?\.title \|\| "", MAX_TITLE_CHARS\);\n  const content = cleanText\(input\.content \|\| existing\?\.content \|\| "", MAX_CONTENT_CHARS\);\n  if \(!content\) throw new Error\("memory content is required"\);\n/;
      const policyContentBlock = [
        "  const title = cleanText(input.title || existing?.title || \"\", MAX_TITLE_CHARS);",
        "  const rawContent = String(input.content || existing?.content || \"\").replace(/\\r\\n/g, \"\\n\").trim();",
        "  const content = cleanText(input.content || existing?.content || \"\", MAX_CONTENT_CHARS);",
        "  if (!content) throw new Error(\"memory content is required\");",
        "  if (options.enforcePolicy) {",
        "    assertMemoryPolicyForSave({",
        "      ...input,",
        "      type: input.type || existing?.type,",
        "      title,",
        "      content: rawContent,",
        "      tags: input.tags || existing?.tags || [],",
        "    });",
        "  }",
      ].join("\n") + "\n";
      if (!contentPattern.test(text)) {
        record(patchId, "skipped", target, "", { reason: "pattern-missing", step: "content-block" });
        process.exit(0);
      }
      text = text.replace(contentPattern, policyContentBlock);
      const saveCall = "    const item = normalizeItem(input, existing);";
      if (!text.includes(saveCall)) {
        record(patchId, "skipped", target, "", { reason: "pattern-missing", step: "save-call" });
        process.exit(0);
      }
      text = text.replace(saveCall, "    const item = normalizeItem(input, existing, { enforcePolicy: true });");
      const contextPattern = /  const items = store\.list\(\{\n    query,\n    includeDisabled: false,\n    limit: Math\.max\(1, Math\.min\(MAX_CONTEXT_ITEMS, Number\(limit\) \|\| MAX_CONTEXT_ITEMS\)\),\n  \}\);\n/;
      const filteredContextList = [
        "  const items = store.list({",
        "    query,",
        "    includeDisabled: false,",
        "    limit: Math.max(1, Math.min(MAX_CONTEXT_ITEMS, Number(limit) || MAX_CONTEXT_ITEMS)),",
        "  }).filter(memoryPolicyAllowsContext);",
      ].join("\n") + "\n";
      if (!contextPattern.test(text)) {
        record(patchId, "skipped", target, "", { reason: "pattern-missing", step: "context-list" });
        process.exit(0);
      }
      text = text.replace(contextPattern, filteredContextList);
      try {
        fs.writeFileSync(target, text);
        record(patchId, "applied", target, "", {
          policyVersion: "nbg-memory-context-v1",
          maxContentChars: 4000,
          changes: ["save-update-policy", "context-filter"],
        });
      } catch (error) {
        record(patchId, "failed", target, error.message, { policyVersion: "nbg-memory-context-v1" });
      }
      NODE_PATCH
    """.trimIndent()

  private fun renderLauncherScript(commonScript: File, packFile: File): String =
    """
      #!/usr/bin/env bash
      set -u
      ts() { date '+%H:%M:%S' 2>/dev/null || date; }
      echo "[launcher] $(ts) script entered"
      source "${commonScript.absolutePath}"
      install_ubuntu || exit 1
      echo "[launcher] $(ts) ubuntu ready"
      install_android_build_helper || exit 1
      echo "[launcher] $(ts) android helper ready"
      HANA_PACK="${packFile.absolutePath}"
      HANA_PACK_MARKER="${packFile.absolutePath}.marker"
      HANA_DEPLOY_DIR="${'$'}UBUNTU_ROOT/opt/hanakopro-server"
      HANA_DEPLOY_MARKER="${'$'}HANA_DEPLOY_DIR/.nbgpack.size"
      HANA_HOME_HOST="${'$'}UBUNTU_ROOT/root/.hanakopro"
      if [ -f "${'$'}HANA_PACK" ]; then
        echo "[launcher] $(ts) bundled pack path selected"
        pack_size="$("${'$'}BIN/busybox" wc -c < "${'$'}HANA_PACK" | "${'$'}BIN/busybox" tr -d '[:space:]')"
        pack_stamp="$(cat "${'$'}HANA_PACK_MARKER" 2>/dev/null || true)"
        [ -n "${'$'}pack_stamp" ] || pack_stamp="${'$'}pack_size"
        deployed_stamp="$(cat "${'$'}HANA_DEPLOY_MARKER" 2>/dev/null || true)"
        if [ ! -x "${'$'}HANA_DEPLOY_DIR/node" ] || [ ! -f "${'$'}HANA_DEPLOY_DIR/bootstrap.js" ] || [ "${'$'}deployed_stamp" != "${'$'}pack_stamp" ]; then
          echo "[launcher] $(ts) deploying bundled HanakoPro server pack..."
          rm -rf "${'$'}HANA_DEPLOY_DIR.tmp" 2>/dev/null || true
          mkdir -p "${'$'}HANA_DEPLOY_DIR.tmp" "${'$'}TMPDIR" 2>/dev/null || exit 1
          if ! "${'$'}BIN/busybox" tar xzf "${'$'}HANA_PACK" -C "${'$'}HANA_DEPLOY_DIR.tmp" 2> "${'$'}TMPDIR/hanako-pack.err"; then
            cat "${'$'}TMPDIR/hanako-pack.err" >&2 2>/dev/null || true
            rm -rf "${'$'}HANA_DEPLOY_DIR.tmp" 2>/dev/null || true
            exit 44
          fi
          chmod 755 "${'$'}HANA_DEPLOY_DIR.tmp/node" 2>/dev/null || true
          echo "${'$'}pack_stamp" > "${'$'}HANA_DEPLOY_DIR.tmp/.nbgpack.size"
          rm -rf "${'$'}HANA_DEPLOY_DIR" 2>/dev/null || true
          mv "${'$'}HANA_DEPLOY_DIR.tmp" "${'$'}HANA_DEPLOY_DIR" || exit 45
        fi
        mkdir -p "${'$'}HANA_HOME_HOST/logs"
        mkdir -p "${'$'}UBUNTU_ROOT/root"
        rm -rf "${'$'}UBUNTU_ROOT/root/Desktop/OH-WorkSpace" 2>/dev/null || true
        rmdir "${'$'}UBUNTU_ROOT/root/Desktop" 2>/dev/null || true
        cat > "${'$'}UBUNTU_ROOT/root/.nbg-start-hanakopro-pack.sh" <<'INNER_PACK'
      #!/usr/bin/env bash
      set -u
      ts() { date '+%H:%M:%S' 2>/dev/null || date; }
      echo "[pack] $(ts) entered"
      export HANA_HOME="${'$'}{HANA_HOME:-/root/.hanakopro}"
      export HANA_ROOT="/opt/hanakopro-server"
      export HANA_ANDROID="1"
      export HANA_CREATE_STARTUP_SESSION="${'$'}{HANA_CREATE_STARTUP_SESSION:-0}"
      export PATH="/opt/hanakopro-server:${'$'}PATH"
      [ -n "${'$'}{HTTP_PROXY:-}" ] && echo "[pack] $(ts) android proxy detected"
      mkdir -p "${'$'}HANA_HOME/logs"
${renderRuntimePatchPrelude(sourceFallback = false).prependIndent("      ")}
      cleanup_android_workspace() {
        rm -rf /root/Desktop/OH-WorkSpace 2>/dev/null || true
        rmdir /root/Desktop 2>/dev/null || true
      }
      clear_stale_hanakopro_state() {
        stale_pid="$(cat "${'$'}HANA_HOME/android-server.pid" 2>/dev/null || true)"
        if [ -z "${'$'}stale_pid" ] && [ -f "${'$'}HANA_HOME/server-info.json" ]; then
          stale_pid="$(node -e 'try{const fs=require("fs");const j=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));process.stdout.write(String(j.pid||""))}catch{}' "${'$'}HANA_HOME/server-info.json" 2>/dev/null)"
        fi
        case "${'$'}stale_pid" in
          ''|*[!0-9]*) ;;
          *) kill -9 "${'$'}stale_pid" 2>/dev/null || true ;;
        esac
        rm -f "${'$'}HANA_HOME/server-info.json" "${'$'}HANA_HOME/android-server.pid" "${'$'}HANA_HOME/android-server.pid.tmp" 2>/dev/null || true
      }
      setup_android_upstream_user_agent() {
        export NBG_HANAKO_UPSTREAM_UA="${'$'}{NBG_HANAKO_UPSTREAM_UA:-NBG-Android/1.0}"
        export NBG_HANAKO_UA_PATCH_VERSION="${'$'}NBG_HANAKO_RUNTIME_PATCH_SET_VERSION"
        NBG_HANAKO_UA_ACTIVE_VERSION="$(cat "${'$'}HANA_HOME/android-upstream-user-agent.active" 2>/dev/null || true)"
        export NBG_HANAKO_UA_ACTIVE_VERSION
        cat > "${'$'}HANA_HOME/android-upstream-user-agent.cjs" <<'NODE_UA_PATCH'
      const USER_AGENT = process.env.NBG_HANAKO_UPSTREAM_UA || "NBG-Android/1.0";
      const originalFetch = globalThis.fetch;
      const isLocalHost = (hostname) => hostname === "127.0.0.1" || hostname === "localhost" || hostname === "::1";
      if (typeof originalFetch === "function" && !globalThis.__nbgAndroidUserAgentFetchPatched) {
        globalThis.__nbgAndroidUserAgentFetchPatched = true;
        globalThis.fetch = function nbgAndroidUserAgentFetch(input, init) {
          try {
            const rawUrl = typeof input === "string" || input instanceof URL ? input.toString() : input?.url;
            const url = rawUrl ? new URL(rawUrl) : null;
            if (!url || !/^https?:$/i.test(url.protocol) || isLocalHost(url.hostname)) {
              return originalFetch(input, init);
            }
            const nextInit = { ...(init || {}) };
            const headers = new Headers(nextInit.headers || input?.headers || undefined);
            headers.set("User-Agent", USER_AGENT);
            nextInit.headers = headers;
            return originalFetch(input, nextInit);
          } catch {
            return originalFetch(input, init);
          }
        };
      }
      NODE_UA_PATCH
        chmod 644 "${'$'}HANA_HOME/android-upstream-user-agent.cjs" 2>/dev/null || true
        case " ${'$'}{NODE_OPTIONS:-} " in
          *" --require ${'$'}HANA_HOME/android-upstream-user-agent.cjs "*) ;;
          *) export NODE_OPTIONS="--require ${'$'}HANA_HOME/android-upstream-user-agent.cjs${'$'}{NODE_OPTIONS:+ ${'$'}NODE_OPTIONS}" ;;
        esac
        record_android_runtime_patch "android-upstream-user-agent-require-v1" "applied" "${'$'}HANA_HOME/android-upstream-user-agent.cjs"
      }
      setup_android_upstream_user_agent
${renderDefaultWorkspacePatch("\"/opt/hanakopro-server/shared/default-workspace.js\"").prependIndent("      ")}
${renderChatToolDetailsPatch("\"/opt/hanakopro-server/server/routes/chat.js\"").prependIndent("      ")}
${renderMemoryContextPolicyPatch("\"/opt/hanakopro-server/plugins/memory/lib/memory-store.js\"").prependIndent("      ")}
      cleanup_android_workspace
      if [ -f "${'$'}HANA_HOME/server-info.json" ] && [ "${'$'}{NBG_HANAKO_RUNTIME_PATCH_ACTIVE_VERSION:-}" != "${'$'}{NBG_HANAKO_RUNTIME_PATCH_SET_VERSION:-}" ]; then
        echo "[pack] $(ts) restarting server for android runtime patch set"
        clear_stale_hanakopro_state
      fi
      if [ -f "${'$'}HANA_HOME/server-info.json" ]; then
        echo "[pack] $(ts) checking existing health"
        port="$(node -e 'try{const fs=require("fs");const j=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));process.stdout.write(String(j.port||""))}catch{}' "${'$'}HANA_HOME/server-info.json" 2>/dev/null)"
        token="$(node -e 'try{const fs=require("fs");const j=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));process.stdout.write(String(j.token||""))}catch{}' "${'$'}HANA_HOME/server-info.json" 2>/dev/null)"
        if [ -n "${'$'}port" ] && [ -n "${'$'}token" ]; then
          if node -e 'const http=require("http");const p=Number(process.argv[1]);const t=process.argv[2];const req=http.get({host:"127.0.0.1",port:p,path:"/api/health",headers:{Authorization:"Bearer "+t},timeout:1200},res=>process.exit(res.statusCode===200?0:1));req.on("error",()=>process.exit(1));req.on("timeout",()=>{req.destroy();process.exit(1);});' "${'$'}port" "${'$'}token"; then
            echo "HanakoPro bundled server already running on ${'$'}port"
            exit 0
          fi
        fi
        clear_stale_hanakopro_state
      fi
      cd "${'$'}HANA_ROOT" || exit 1
      echo "[pack] $(ts) starting node"
      echo "[android-launch] $(ts) node exec starting" > "${'$'}HANA_HOME/android-server.log"
      echo "${'$'}NBG_HANAKO_RUNTIME_PATCH_SET_VERSION" > "${'$'}HANA_HOME/android-runtime-patches.active" 2>/dev/null || true
      echo "${'$'}NBG_HANAKO_UA_PATCH_VERSION" > "${'$'}HANA_HOME/android-upstream-user-agent.active" 2>/dev/null || true
      if command -v setsid >/dev/null 2>&1; then
        setsid ./node ./bootstrap.js </dev/null >> "${'$'}HANA_HOME/android-server.log" 2>&1 &
        pid="${'$'}!"
      else
        ( ./node ./bootstrap.js </dev/null >> "${'$'}HANA_HOME/android-server.log" 2>&1 & echo "${'$'}!" > "${'$'}HANA_HOME/android-server.pid.tmp" )
        pid="$(cat "${'$'}HANA_HOME/android-server.pid.tmp" 2>/dev/null || true)"
        rm -f "${'$'}HANA_HOME/android-server.pid.tmp" 2>/dev/null || true
      fi
      if [ -z "${'$'}pid" ]; then
        pid="$(pgrep -f '/opt/hanakopro-server/.*/bootstrap.js|./node ./bootstrap.js' 2>/dev/null | tail -n 1 || true)"
      fi
      [ -n "${'$'}pid" ] && echo "${'$'}pid" > "${'$'}HANA_HOME/android-server.pid"
      echo "[pack] $(ts) HanakoPro bundled server starting pid=${'$'}pid"
      ( sleep 2; cleanup_android_workspace; sleep 5; cleanup_android_workspace ) >/dev/null 2>&1 &
      INNER_PACK
        chmod 755 "${'$'}UBUNTU_ROOT/root/.nbg-start-hanakopro-pack.sh"
        echo "[launcher] $(ts) run_in_ubuntu pack start"
        run_in_ubuntu 'HANA_HOME=/root/.hanakopro /bin/bash /root/.nbg-start-hanakopro-pack.sh'
        status="${'$'}?"
        echo "[launcher] $(ts) run_in_ubuntu pack exit ${'$'}status"
        exit "${'$'}status"
      fi

      echo "[launcher] $(ts) source fallback selected"
      configure_package_sources || exit 1
      fix_android_group_names || exit 1
      ensure_default_development_tools || exit 1

      cat > "${'$'}UBUNTU_ROOT/root/.nbg-start-hanakopro.sh" <<'INNER'
      #!/usr/bin/env bash
      set -u
      project=""
      for candidate in \
        /root/HanakoPro \
        /root/hanakopro \
        /root/nbg-references/HanakoPro \
        /root/NBG-Code/HanakoPro \
        /root/nbg-uploads/HanakoPro \
        /storage/emulated/0/NBG/HanakoPro \
        /storage/emulated/0/NBG/hanakopro
      do
        if [ -f "${'$'}candidate/package.json" ] && [ -f "${'$'}candidate/server/index.js" ]; then
          project="${'$'}candidate"
          break
        fi
      done
      if [ -z "${'$'}project" ]; then
        echo "HanakoPro server pack and source not found. Put source in /root/HanakoPro or /storage/emulated/0/NBG/HanakoPro" >&2
        exit 42
      fi
      export HANA_HOME="${'$'}{HANA_HOME:-/root/.hanakopro}"
      export HANA_ROOT="${'$'}project"
      export HANA_ANDROID="1"
      export HANA_CREATE_STARTUP_SESSION="${'$'}{HANA_CREATE_STARTUP_SESSION:-0}"
      [ -n "${'$'}{HTTP_PROXY:-}" ] && echo "[source] android proxy detected"
      mkdir -p "${'$'}HANA_HOME/logs"
${renderRuntimePatchPrelude(sourceFallback = true).prependIndent("      ")}
      cleanup_android_workspace() {
        rm -rf /root/Desktop/OH-WorkSpace 2>/dev/null || true
        rmdir /root/Desktop 2>/dev/null || true
      }
      clear_stale_hanakopro_state() {
        stale_pid="$(cat "${'$'}HANA_HOME/android-server.pid" 2>/dev/null || true)"
        if [ -z "${'$'}stale_pid" ] && [ -f "${'$'}HANA_HOME/server-info.json" ]; then
          stale_pid="$(node -e 'try{const fs=require("fs");const j=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));process.stdout.write(String(j.pid||""))}catch{}' "${'$'}HANA_HOME/server-info.json" 2>/dev/null)"
        fi
        case "${'$'}stale_pid" in
          ''|*[!0-9]*) ;;
          *) kill -9 "${'$'}stale_pid" 2>/dev/null || true ;;
        esac
        rm -f "${'$'}HANA_HOME/server-info.json" "${'$'}HANA_HOME/android-server.pid" "${'$'}HANA_HOME/android-server.pid.tmp" 2>/dev/null || true
      }
      setup_android_upstream_user_agent() {
        export NBG_HANAKO_UPSTREAM_UA="${'$'}{NBG_HANAKO_UPSTREAM_UA:-NBG-Android/1.0}"
        export NBG_HANAKO_UA_PATCH_VERSION="${'$'}NBG_HANAKO_RUNTIME_PATCH_SET_VERSION"
        NBG_HANAKO_UA_ACTIVE_VERSION="$(cat "${'$'}HANA_HOME/android-upstream-user-agent.active" 2>/dev/null || true)"
        export NBG_HANAKO_UA_ACTIVE_VERSION
        cat > "${'$'}HANA_HOME/android-upstream-user-agent.cjs" <<'NODE_UA_PATCH'
      const USER_AGENT = process.env.NBG_HANAKO_UPSTREAM_UA || "NBG-Android/1.0";
      const originalFetch = globalThis.fetch;
      const isLocalHost = (hostname) => hostname === "127.0.0.1" || hostname === "localhost" || hostname === "::1";
      if (typeof originalFetch === "function" && !globalThis.__nbgAndroidUserAgentFetchPatched) {
        globalThis.__nbgAndroidUserAgentFetchPatched = true;
        globalThis.fetch = function nbgAndroidUserAgentFetch(input, init) {
          try {
            const rawUrl = typeof input === "string" || input instanceof URL ? input.toString() : input?.url;
            const url = rawUrl ? new URL(rawUrl) : null;
            if (!url || !/^https?:$/i.test(url.protocol) || isLocalHost(url.hostname)) {
              return originalFetch(input, init);
            }
            const nextInit = { ...(init || {}) };
            const headers = new Headers(nextInit.headers || input?.headers || undefined);
            headers.set("User-Agent", USER_AGENT);
            nextInit.headers = headers;
            return originalFetch(input, nextInit);
          } catch {
            return originalFetch(input, init);
          }
        };
      }
      NODE_UA_PATCH
        chmod 644 "${'$'}HANA_HOME/android-upstream-user-agent.cjs" 2>/dev/null || true
        case " ${'$'}{NODE_OPTIONS:-} " in
          *" --require ${'$'}HANA_HOME/android-upstream-user-agent.cjs "*) ;;
          *) export NODE_OPTIONS="--require ${'$'}HANA_HOME/android-upstream-user-agent.cjs${'$'}{NODE_OPTIONS:+ ${'$'}NODE_OPTIONS}" ;;
        esac
        record_android_runtime_patch "android-upstream-user-agent-require-v1" "applied" "${'$'}HANA_HOME/android-upstream-user-agent.cjs"
      }
      setup_android_upstream_user_agent
${renderDefaultWorkspacePatch("(process.env.HANA_ROOT || \"\") + \"/shared/default-workspace.js\"").prependIndent("      ")}
${renderChatToolDetailsPatch("(process.env.HANA_ROOT || \"\") + \"/server/routes/chat.js\"").prependIndent("      ")}
${renderMemoryContextPolicyPatch("(process.env.HANA_ROOT || \"\") + \"/plugins/memory/lib/memory-store.js\"").prependIndent("      ")}
      cleanup_android_workspace
      if [ -f "${'$'}HANA_HOME/server-info.json" ] && [ "${'$'}{NBG_HANAKO_RUNTIME_PATCH_ACTIVE_VERSION:-}" != "${'$'}{NBG_HANAKO_RUNTIME_PATCH_SET_VERSION:-}" ]; then
        echo "[source] restarting server for android runtime patch set"
        clear_stale_hanakopro_state
      fi
      if [ -f "${'$'}HANA_HOME/server-info.json" ]; then
        port="$(node -e 'try{const fs=require("fs");const j=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));process.stdout.write(String(j.port||""))}catch{}' "${'$'}HANA_HOME/server-info.json" 2>/dev/null)"
        token="$(node -e 'try{const fs=require("fs");const j=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));process.stdout.write(String(j.token||""))}catch{}' "${'$'}HANA_HOME/server-info.json" 2>/dev/null)"
        if [ -n "${'$'}port" ] && [ -n "${'$'}token" ]; then
          if node -e 'const http=require("http");const p=Number(process.argv[1]);const t=process.argv[2];const req=http.get({host:"127.0.0.1",port:p,path:"/api/health",headers:{Authorization:"Bearer "+t},timeout:1200},res=>process.exit(res.statusCode===200?0:1));req.on("error",()=>process.exit(1));req.on("timeout",()=>{req.destroy();process.exit(1);});' "${'$'}port" "${'$'}token"; then
            echo "HanakoPro server already running on ${'$'}port"
            exit 0
          fi
        fi
        clear_stale_hanakopro_state
      fi
      cd "${'$'}project" || exit 1
      if [ ! -d node_modules ]; then
        echo "HanakoPro node_modules missing in ${'$'}project. Run npm install there first." >&2
        exit 43
      fi
      echo "${'$'}NBG_HANAKO_RUNTIME_PATCH_SET_VERSION" > "${'$'}HANA_HOME/android-runtime-patches.active" 2>/dev/null || true
      echo "${'$'}NBG_HANAKO_UA_PATCH_VERSION" > "${'$'}HANA_HOME/android-upstream-user-agent.active" 2>/dev/null || true
      nohup npm run server </dev/null > "${'$'}HANA_HOME/android-server.log" 2>&1 &
      echo "${'$'}!" > "${'$'}HANA_HOME/android-server.pid"
      echo "HanakoPro server starting from ${'$'}project pid=${'$'}!"
      ( sleep 2; cleanup_android_workspace; sleep 5; cleanup_android_workspace ) >/dev/null 2>&1 &
      INNER
      chmod 755 "${'$'}UBUNTU_ROOT/root/.nbg-start-hanakopro.sh"
      run_in_ubuntu '/bin/bash /root/.nbg-start-hanakopro.sh'
    """.trimIndent()

  private companion object {
    const val HANAKO_SERVER_PACK = "hanako-server-linux-arm64-node22.nbgpack"
    const val HANAKO_SERVER_PACK_MARKER = "hanako-server-linux-arm64-node22.nbgpack.marker"
    const val NBG_HANAKO_RUNTIME_PATCH_SET_VERSION = "20260704-runtime-patch-gate-v2-memory-policy"
    const val NBG_HANAKO_RUNTIME_TARGET_VERSION = "hanako-server-linux-arm64-node22"
    const val NBG_HANAKO_RUNTIME_SOURCE_FALLBACK_VERSION = "hanako-source-fallback"
    const val NBG_HANAKO_RUNTIME_PATCH_TARGET_PACK_MARKER =
      "size=94856946;sha256=bdb8e182f8289023bf29f27fa0962edaea58d7c79fb57187caed1942a947becd"
    const val LAUNCHER_GUARD_TIMEOUT_SECONDS = 120L
    val launching = AtomicBoolean(false)
  }
}

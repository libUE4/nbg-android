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

internal fun hanakoServerHomeDirs(context: Context): List<File> {
  val filesDir = context.filesDir
  val ubuntuHome = File(filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu/root")
  return listOf(
    File(ubuntuHome, ".hanakopro"),
    File(ubuntuHome, ".hanakopro-dev"),
    File(ubuntuHome, ".hana"),
    File(ubuntuHome, ".nbg/hanakopro"),
    File(filesDir, ".hanakopro"),
  ).distinctBy { it.absolutePath }
}

internal fun hanakoServerInfoFiles(context: Context): List<File> =
  hanakoServerHomeDirs(context).map { File(it, "server-info.json") }

internal fun findServerInfo(context: Context): HanakoServerInfo? =
  hanakoServerInfoFiles(context).firstNotNullOfOrNull { file ->
    runCatching {
      if (!file.isFile) return@runCatching null
      nbgParseHanakoServerInfo(JSONObject(file.readText(Charsets.UTF_8)))
    }.getOrNull()
  }

internal fun clearStaleHanakoServer(context: Context, info: HanakoServerInfo? = findServerInfo(context)) {
  val homes = hanakoServerHomeDirs(context)
  val pids = buildSet {
    info?.pid?.takeIf { it > 1 }?.let(::add)
    homes.forEach { home ->
      val pid = runCatching {
        File(home, "android-server.pid")
          .takeIf { it.isFile }
          ?.readText(Charsets.UTF_8)
          ?.trim()
          ?.toIntOrNull()
      }.getOrNull()
      if (pid != null && pid > 1) add(pid)
    }
  }
  pids.forEach(::killHanakoPid)

  val staleFiles = homes.flatMap { home ->
    listOf(
      File(home, "server-info.json"),
      File(home, "android-server.pid"),
      File(home, "android-server.pid.tmp"),
    )
  }
  staleFiles.forEach { file ->
    runCatching {
      if (file.isFile) file.delete()
    }.onFailure { Log.w("NBG_HANAKO", "delete stale HanakoPro state failed: ${file.absolutePath}", it) }
  }
  Log.i(
    "NBG_HANAKO",
    "cleared stale HanakoPro server state pids=${pids.joinToString().ifBlank { "none" }} files=${staleFiles.count { !it.exists() }}",
  )
}

internal fun nbgPruneDeletedUrlApiProviderFiles(context: Context, activeProviderIds: Set<String>): Boolean {
  var changed = false
  hanakoServerHomeDirs(context).forEach { home ->
    runCatching {
      val yamlFile = File(home, "added-models.yaml")
      if (yamlFile.isFile) {
        val current = yamlFile.readText(Charsets.UTF_8)
        val next = nbgPruneDeletedUrlApiProvidersFromYaml(current, activeProviderIds)
        if (next != current) {
          yamlFile.writeText(next, Charsets.UTF_8)
          changed = true
        }
      }
      val modelsFile = File(home, "models.json")
      if (modelsFile.isFile) {
        val current = modelsFile.readText(Charsets.UTF_8)
        val next = nbgPruneDeletedUrlApiProvidersFromModelsJson(current, activeProviderIds)
        if (next != current) {
          modelsFile.writeText(next, Charsets.UTF_8)
          changed = true
        }
      }
    }.onFailure { Log.w("NBG_HANAKO", "prune deleted URL API provider files failed: ${home.absolutePath}", it) }
  }
  return changed
}

internal fun nbgPruneDeletedUrlApiProvidersFromModelsJson(raw: String, activeProviderIds: Set<String>): String =
  runCatching {
    val root = JSONObject(raw.ifBlank { "{}" })
    val providers = root.optJSONObject("providers") ?: return raw
    val names = providers.keys().asSequence().toList()
    var changed = false
    names.forEach { name ->
      if (name.startsWith("urlapi-") && name !in activeProviderIds) {
        providers.remove(name)
        changed = true
      }
    }
    if (changed) root.toString(2) + "\n" else raw
  }.getOrDefault(raw)

internal fun nbgPruneDeletedUrlApiProvidersFromYaml(raw: String, activeProviderIds: Set<String>): String {
  val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
  val providerHeaderIndex = lines.indexOfFirst { it.trim() == "providers:" }
  if (providerHeaderIndex < 0) return raw
  val providersIndent = lines[providerHeaderIndex].takeWhile { it == ' ' }.length
  val childIndent = providersIndent + 2
  val next = mutableListOf<String>()
  var changed = false
  var index = 0
  while (index < lines.size) {
    val line = lines[index]
    if (index <= providerHeaderIndex) {
      next.add(line)
      index++
      continue
    }
    if (line.isNotBlank() && line.takeWhile { it == ' ' }.length <= providersIndent) {
      next.add(line)
      index++
      continue
    }
    val providerId = nbgYamlProviderIdAtLine(line, childIndent)
    if (providerId == null || !providerId.startsWith("urlapi-") || providerId in activeProviderIds) {
      next.add(line)
      index++
      continue
    }
    changed = true
    index++
    while (index < lines.size) {
      val candidate = lines[index]
      if (candidate.isBlank()) {
        index++
        continue
      }
      val indent = candidate.takeWhile { it == ' ' }.length
      if (indent <= providersIndent || nbgYamlProviderIdAtLine(candidate, childIndent) != null) break
      index++
    }
  }
  return if (changed) next.joinToString("\n").trimEnd() + "\n" else raw
}

internal fun nbgYamlProviderIdAtLine(line: String, expectedIndent: Int): String? {
  if (line.takeWhile { it == ' ' }.length != expectedIndent) return null
  val trimmed = line.trim()
  if (!trimmed.endsWith(":")) return null
  val id = trimmed.removeSuffix(":").trim().trim('"', '\'')
  return id.takeIf { it.isNotBlank() && !it.startsWith("-") }
}

internal fun killHanakoPid(pid: Int) {
  if (pid <= 1) return
  runCatching {
    val process = ProcessBuilder("/system/bin/sh", "-c", "kill -9 $pid 2>/dev/null || true").start()
    process.waitFor(800L, TimeUnit.MILLISECONDS)
    process.destroy()
  }.onFailure { Log.w("NBG_HANAKO", "kill stale HanakoPro pid failed: $pid", it) }
}

internal fun nbgParseHanakoServerInfo(json: JSONObject): HanakoServerInfo? {
  val port = json.optInt("port", -1)
  val token = json.cleanString("token") ?: return null
  if (port !in 1..65535) return null
  return HanakoServerInfo(
    port = port,
    token = token,
    pid = json.optInt("pid").takeIf { it > 0 },
    version = json.cleanString("version"),
  )
}

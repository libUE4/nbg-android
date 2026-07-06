package com.nbg.android

import android.util.Log
import org.json.JSONObject
import java.io.File

internal const val HANA_HISTORY_CACHE_DIR = "hanako-history-cache"
internal const val HANA_HISTORY_CACHE_INDEX = "latest-session.json"

internal class HanakoHistoryCacheStore(
  private val filesDir: File,
  private val ubuntuRootHomeDir: File,
) {
  fun localSessionLooksUnhealthy(sessionPath: String): Boolean {
    val file = localFileForSessionPath(sessionPath) ?: return false
    return nbgLocalSessionLooksUnhealthy(file)
  }

  fun readLatestCachedSession(): HanakoCachedSession? =
    runCatching {
      val index = File(historyCacheDir(), HANA_HISTORY_CACHE_INDEX)
      if (!index.isFile) return readLatestLocalSessionFile()
      val root = JSONObject(index.readText(Charsets.UTF_8))
      val sessionPath = root.cleanString("sessionPath") ?: return null
      if (localSessionLooksUnhealthy(sessionPath)) {
        Log.w("NBG_HANAKO", "skip unhealthy cached Hanako session $sessionPath")
        return@runCatching null
      }
      val snapshot = readCachedHistory(sessionPath) ?: return null
      HanakoCachedSession(
        sessionPath = sessionPath,
        title = root.cleanString("title") ?: "新聊天",
        snapshot = snapshot,
      )
    }.onFailure { Log.w("NBG_HANAKO", "read latest history cache failed", it) }.getOrNull()
      ?: readLatestLocalSessionFile()

  fun readCachedHistory(sessionPath: String): HanakoHistorySnapshot? =
    runCatching {
      val file = historyCacheFile(sessionPath)
      val cached = if (file.isFile) {
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val cachedPath = root.cleanString("sessionPath")
        if (cachedPath == sessionPath) {
          root.optJSONObject("snapshot")?.let(::parseCachedHistorySnapshot)
        } else {
          null
        }
      } else {
        null
      }
      fullerHistorySnapshot(cached, readLocalSessionFileByPath(sessionPath))
    }.onFailure { Log.w("NBG_HANAKO", "read history cache failed for $sessionPath", it) }.getOrNull()
      ?: readLocalSessionFileByPath(sessionPath)

  fun readLocalSessionFileByPath(sessionPath: String): HanakoHistorySnapshot? =
    runCatching {
      val file = localFileForSessionPath(sessionPath) ?: return null
      parseLocalSessionJsonl(file).takeIf { it.messages.isNotEmpty() }
    }.onFailure { Log.w("NBG_HANAKO", "read local session file failed for $sessionPath", it) }.getOrNull()

  fun historyCacheDir(): File =
    File(filesDir, HANA_HISTORY_CACHE_DIR)

  fun historyCacheFile(sessionPath: String): File =
    File(historyCacheDir(), "${sessionPath.sha256Hex()}.json")

  private fun readLatestLocalSessionFile(): HanakoCachedSession? =
    runCatching {
      val file = localSessionDirs()
        .flatMap { dir -> dir.listFiles { candidate -> candidate.isFile && candidate.name.endsWith(".jsonl") }?.toList().orEmpty() }
        .sortedByDescending { it.lastModified() }
        .firstOrNull { !nbgLocalSessionLooksUnhealthy(it) }
        ?: return null
      val sessionPath = localSessionPath(file)
      val snapshot = parseLocalSessionJsonl(file)
      if (snapshot.messages.isEmpty()) return null
      HanakoCachedSession(
        sessionPath = sessionPath,
        title = snapshot.messages.firstOrNull { it.role == "user" }?.text?.lineSequence()?.firstOrNull()?.take(40) ?: "最近对话",
        snapshot = snapshot,
      )
    }.onFailure { Log.w("NBG_HANAKO", "read local session file failed", it) }.getOrNull()

  private fun fullerHistorySnapshot(
    primary: HanakoHistorySnapshot?,
    fallback: HanakoHistorySnapshot?,
  ): HanakoHistorySnapshot? {
    if (primary == null) return fallback
    if (fallback == null) return primary
    return if (fallback.messages.size > primary.messages.size) {
      fallback.copy(
        todos = primary.todos.ifEmpty { fallback.todos },
        sessionFiles = primary.sessionFiles.ifEmpty { fallback.sessionFiles },
      )
    } else {
      primary.copy(
        todos = primary.todos.ifEmpty { fallback.todos },
        sessionFiles = primary.sessionFiles.ifEmpty { fallback.sessionFiles },
      )
    }
  }

  private fun localSessionDirs(): List<File> =
    listOf(
      File(ubuntuRootHomeDir, ".hanakopro/agents/hanako/sessions"),
      File(ubuntuRootHomeDir, ".hanakopro-android/agents/hanako/sessions"),
      File(ubuntuRootHomeDir, ".nbg/hanakopro/agents/hanako/sessions"),
    )

  private fun localFileForSessionPath(sessionPath: String): File? =
    nbgLocalFileForSessionPath(
      ubuntuRootHomeDir = ubuntuRootHomeDir,
      sessionPath = sessionPath,
      localSessionDirs = localSessionDirs(),
    )

  private fun localSessionPath(file: File): String {
    val root = ubuntuRootHomeDir.absoluteFile
    val rel = runCatching { file.absoluteFile.toPath().let { root.toPath().relativize(it).toString() } }
      .getOrDefault(file.name)
      .replace(File.separatorChar, '/')
    return "/root/$rel"
  }
}

package com.nbg.android

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal const val HANA_HISTORY_CACHE_DIR = "hanako-history-cache"
internal const val HANA_HISTORY_CACHE_INDEX = "latest-session.json"
internal const val HANA_HISTORY_SUMMARY_INDEX = "session-summary-index.json"
internal const val HANA_HISTORY_SUMMARY_INDEX_VERSION = 1

internal data class HanakoSessionSummaryIndexEntry(
  val sessionPath: String,
  val title: String,
  val snippet: String,
  val updatedAtMs: Long,
  val messageCount: Int,
  val todoCount: Int,
  val fileCount: Int,
) {
  val hasSummary: Boolean
    get() = snippet.isNotBlank()
}

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

  fun historySummaryIndexFile(): File =
    File(historyCacheDir(), HANA_HISTORY_SUMMARY_INDEX)

  fun readSummaryIndex(limit: Int = 200): List<HanakoSessionSummaryIndexEntry> =
    runCatching {
      val file = historySummaryIndexFile()
      if (!file.isFile) return emptyList()
      parseHanakoSessionSummaryIndex(JSONObject(file.readText(Charsets.UTF_8)), limit)
    }.onFailure { Log.w("NBG_HANAKO", "read history summary index failed", it) }.getOrDefault(emptyList())

  fun writeSummaryIndexEntry(
    sessionPath: String,
    title: String,
    snapshot: HanakoHistorySnapshot,
    updatedAtMs: Long,
  ) {
    val entry = nbgBuildSessionSummaryIndexEntry(
      sessionPath = sessionPath,
      title = title,
      snapshot = snapshot,
      updatedAtMs = updatedAtMs,
    ) ?: return
    val cacheDir = historyCacheDir()
    cacheDir.mkdirs()
    val next = (listOf(entry) + readSummaryIndex(limit = 500).filterNot { it.sessionPath == entry.sessionPath })
      .sortedByDescending { it.updatedAtMs }
      .take(200)
    val file = historySummaryIndexFile()
    val tmp = File(cacheDir, "${file.name}.tmp")
    tmp.writeText(nbgSessionSummaryIndexToJson(next).toString(), Charsets.UTF_8)
    nbgMoveReplacingWithAtomicFallback(tmp, file)
  }

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

internal fun nbgBuildSessionSummaryIndexEntry(
  sessionPath: String,
  title: String,
  snapshot: HanakoHistorySnapshot,
  updatedAtMs: Long,
): HanakoSessionSummaryIndexEntry? {
  val cleanPath = sessionPath.trim()
  if (cleanPath.isBlank() || snapshot.messages.isEmpty()) return null
  val cleanTitle = nbgSessionSummaryIndexText(title.ifBlank { "新聊天" }, limit = 80)
  val firstUser = snapshot.messages.firstOrNull { it.role == "user" }?.text.orEmpty()
  val lastUser = snapshot.messages.lastOrNull { it.role == "user" }?.text.orEmpty()
  val lastAssistant = snapshot.messages.lastOrNull { it.role == "assistant" }?.text.orEmpty()
  val snippet = listOf(firstUser, lastUser, lastAssistant)
    .map { nbgSessionSummaryIndexText(it, limit = 120) }
    .filter { it.isNotBlank() && it != cleanTitle }
    .distinct()
    .take(2)
    .joinToString(" / ")
    .take(240)
  return HanakoSessionSummaryIndexEntry(
    sessionPath = cleanPath,
    title = cleanTitle,
    snippet = snippet,
    updatedAtMs = updatedAtMs.coerceAtLeast(0L),
    messageCount = snapshot.messages.size,
    todoCount = snapshot.todos.size,
    fileCount = snapshot.sessionFiles.size,
  )
}

internal fun nbgSessionSummaryIndexToJson(entries: List<HanakoSessionSummaryIndexEntry>): JSONObject =
  JSONObject()
    .put("version", HANA_HISTORY_SUMMARY_INDEX_VERSION)
    .put("updatedAt", System.currentTimeMillis())
    .put("sessions", JSONArray().apply {
      entries.forEach { put(it.toJson()) }
    })

internal fun parseHanakoSessionSummaryIndex(
  root: JSONObject,
  limit: Int = 200,
): List<HanakoSessionSummaryIndexEntry> {
  val sessions = root.optJSONArray("sessions") ?: return emptyList()
  return buildList {
    for (index in 0 until sessions.length()) {
      val item = sessions.optJSONObject(index) ?: continue
      val path = item.cleanString("sessionPath") ?: continue
      add(
        HanakoSessionSummaryIndexEntry(
          sessionPath = path,
          title = item.cleanString("title") ?: "新聊天",
          snippet = item.cleanString("snippet").orEmpty(),
          updatedAtMs = item.optLong("updatedAtMs", 0L).coerceAtLeast(0L),
          messageCount = item.optInt("messageCount", 0).coerceAtLeast(0),
          todoCount = item.optInt("todoCount", 0).coerceAtLeast(0),
          fileCount = item.optInt("fileCount", 0).coerceAtLeast(0),
        ),
      )
    }
  }
    .distinctBy { it.sessionPath }
    .sortedByDescending { it.updatedAtMs }
    .take(limit.coerceAtLeast(0))
}

private fun HanakoSessionSummaryIndexEntry.toJson(): JSONObject =
  JSONObject()
    .put("sessionPath", sessionPath)
    .put("title", title)
    .put("snippet", snippet)
    .put("updatedAtMs", updatedAtMs)
    .put("messageCount", messageCount)
    .put("todoCount", todoCount)
    .put("fileCount", fileCount)

private fun nbgSessionSummaryIndexText(raw: String, limit: Int): String =
  nbgRedactDiagnosticText(raw)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit)

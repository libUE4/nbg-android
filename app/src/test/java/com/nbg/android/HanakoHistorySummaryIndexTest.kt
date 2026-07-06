package com.nbg.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class HanakoHistorySummaryIndexTest {
  @Test
  fun buildsRedactedCompactSummaryEntry() {
    val snapshot = HanakoHistorySnapshot(
      messages = listOf(
        HanakoHistoryMessage(id = 1, role = "user", text = "请修复构建\napi_key=sk-secret1234567890"),
        HanakoHistoryMessage(id = 2, role = "assistant", text = "已修复 Gradle 配置，测试通过。"),
      ),
      todos = listOf(HanakoTodoItem("运行测试", "运行测试", "completed")),
      sessionFiles = listOf(HanakoSessionFile("f1", "build.gradle.kts", "/root/project/build.gradle.kts", "file", "tool")),
    )

    val entry = nbgBuildSessionSummaryIndexEntry(
      sessionPath = "/root/.hanakopro/agents/hanako/sessions/one.jsonl",
      title = "构建修复",
      snapshot = snapshot,
      updatedAtMs = 42L,
    )

    assertNotNull(entry)
    entry!!
    assertEquals("构建修复", entry.title)
    assertEquals(2, entry.messageCount)
    assertEquals(1, entry.todoCount)
    assertEquals(1, entry.fileCount)
    assertTrue(entry.hasSummary)
    assertTrue(entry.snippet.contains("请修复构建"))
    assertTrue(entry.snippet.contains("已修复 Gradle 配置"))
    assertFalse(entry.snippet.contains("sk-secret"))
    assertFalse(entry.snippet.contains("1234567890"))
    assertTrue(entry.snippet.contains("[redacted]"))
  }

  @Test
  fun summaryIndexJsonRoundTripsSortedAndDeduped() {
    val newer = HanakoSessionSummaryIndexEntry(
      sessionPath = "/sessions/new.jsonl",
      title = "new",
      snippet = "new snippet",
      updatedAtMs = 200L,
      messageCount = 3,
      todoCount = 1,
      fileCount = 0,
    )
    val older = HanakoSessionSummaryIndexEntry(
      sessionPath = "/sessions/old.jsonl",
      title = "old",
      snippet = "old snippet",
      updatedAtMs = 100L,
      messageCount = 2,
      todoCount = 0,
      fileCount = 1,
    )
    val duplicateOld = older.copy(title = "ignored duplicate", updatedAtMs = 300L)

    val parsed = parseHanakoSessionSummaryIndex(
      nbgSessionSummaryIndexToJson(listOf(older, newer, duplicateOld)),
    )

    assertEquals(listOf("/sessions/new.jsonl", "/sessions/old.jsonl"), parsed.map { it.sessionPath })
    assertEquals("old", parsed[1].title)
  }

  @Test
  fun storeWritesSummaryIndexSidecarWithLatestEntryFirst() {
    val root = createTempDirectory("nbg-history-summary").toFile()
    val store = HanakoHistoryCacheStore(
      filesDir = File(root, "files"),
      ubuntuRootHomeDir = File(root, "ubuntu-root"),
    )
    val first = history("first question", "first answer")
    val second = history("second question", "second answer")

    store.writeSummaryIndexEntry("/sessions/first.jsonl", "First", first, updatedAtMs = 100L)
    store.writeSummaryIndexEntry("/sessions/second.jsonl", "Second", second, updatedAtMs = 200L)
    store.writeSummaryIndexEntry("/sessions/first.jsonl", "First updated", first, updatedAtMs = 300L)

    val indexFile = store.historySummaryIndexFile()
    val raw = JSONObject(indexFile.readText(Charsets.UTF_8))
    val parsed = store.readSummaryIndex()

    assertTrue(indexFile.isFile)
    assertEquals(HANA_HISTORY_SUMMARY_INDEX_VERSION, raw.optInt("version"))
    assertEquals(listOf("/sessions/first.jsonl", "/sessions/second.jsonl"), parsed.map { it.sessionPath })
    assertEquals("First updated", parsed.first().title)
    assertEquals(2, parsed.size)
  }

  @Test
  fun summaryIndexSearchFindsLocalRedactedHistory() {
    val entries = listOf(
      HanakoSessionSummaryIndexEntry(
        sessionPath = "/sessions/build.jsonl",
        title = "构建修复",
        snippet = "Gradle build fixed and tests passed",
        updatedAtMs = 200L,
        messageCount = 4,
        todoCount = 1,
        fileCount = 2,
      ),
      HanakoSessionSummaryIndexEntry(
        sessionPath = "/sessions/keys.jsonl",
        title = "密钥问题",
        snippet = "api_key=[redacted] 已经脱敏",
        updatedAtMs = 300L,
        messageCount = 2,
        todoCount = 0,
        fileCount = 0,
      ),
    )

    val buildResults = nbgSearchSessionSummaryIndex(entries, "gradle tests")
    val redactedResults = nbgSearchSessionSummaryIndex(entries, "redacted")

    assertEquals(listOf("/sessions/build.jsonl"), buildResults.map { it.path })
    assertEquals("summary", buildResults.single().matchType)
    assertEquals("本地摘要 / 4 消息 / 1 Todo / 2 文件", buildResults.single().subtitle)
    assertTrue(buildResults.single().hasSummary)
    assertEquals(listOf("/sessions/keys.jsonl"), redactedResults.map { it.path })
    assertFalse(redactedResults.single().snippet.orEmpty().contains("sk-"))
  }

  @Test
  fun searchResultsMergeRemoteFirstAndFillLocalSummaryMetadata() {
    val remote = listOf(
      HanakoSessionSummary(
        path = "/sessions/remote.jsonl",
        title = "Remote",
        subtitle = "HanakoPro search",
        snippet = null,
      ),
    )
    val local = listOf(
      HanakoSessionSummary(
        path = "/sessions/remote.jsonl",
        title = "Local",
        subtitle = "本地摘要 / 3 消息",
        snippet = "local snippet",
        matchType = "summary",
        hasSummary = true,
      ),
      HanakoSessionSummary(
        path = "/sessions/local-only.jsonl",
        title = "Local only",
        subtitle = "本地摘要",
        snippet = "offline hit",
        matchType = "title",
        hasSummary = true,
      ),
    )

    val merged = nbgMergeSessionSearchResults(remote, local)

    assertEquals(listOf("/sessions/remote.jsonl", "/sessions/local-only.jsonl"), merged.map { it.path })
    assertEquals("Remote", merged.first().title)
    assertEquals("local snippet", merged.first().snippet)
    assertEquals("summary", merged.first().matchType)
    assertTrue(merged.first().hasSummary)
  }

  private fun history(user: String, assistant: String): HanakoHistorySnapshot =
    HanakoHistorySnapshot(
      messages = listOf(
        HanakoHistoryMessage(id = 1, role = "user", text = user),
        HanakoHistoryMessage(id = 2, role = "assistant", text = assistant),
      ),
    )
}

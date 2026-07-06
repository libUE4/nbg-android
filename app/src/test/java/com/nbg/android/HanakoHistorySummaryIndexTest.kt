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

  private fun history(user: String, assistant: String): HanakoHistorySnapshot =
    HanakoHistorySnapshot(
      messages = listOf(
        HanakoHistoryMessage(id = 1, role = "user", text = user),
        HanakoHistoryMessage(id = 2, role = "assistant", text = assistant),
      ),
    )
}

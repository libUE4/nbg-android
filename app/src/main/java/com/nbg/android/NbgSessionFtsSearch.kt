package com.nbg.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class NbgSessionFtsHit(
  val sessionPath: String,
  val title: String,
  val snippet: String,
  val score: Int,
  val matchType: String,
  val queryTerms: List<String> = emptyList(),
)

data class NbgSessionFtsDocument(
  val docId: String,
  val docType: String,
  val sessionPath: String,
  val title: String,
  val body: String,
  val updatedAtMs: Long = 0L,
)

internal class NbgSessionFtsIndexStore(context: Context) {
  private val helper = Helper(context.applicationContext)

  fun rebuildAndSearch(
    documents: List<NbgSessionFtsDocument>,
    query: String,
    limit: Int = 20,
  ): List<NbgSessionFtsHit> =
    runCatching {
      val terms = nbgSessionFtsTerms(query)
      val match = terms.joinToString(" ") { "$it*" }
      if (match.isBlank()) return@runCatching emptyList()
      val db = helper.writableDatabase
      db.beginTransaction()
      try {
        db.delete(TABLE, null, null)
        documents.take(2_000).forEach { doc ->
          db.insert(
            TABLE,
            null,
            ContentValues().apply {
              put("docId", doc.docId)
              put("docType", doc.docType)
              put("sessionPath", doc.sessionPath)
              put("title", doc.title)
              put("body", doc.body)
              put("updatedAtMs", doc.updatedAtMs)
            },
          )
        }
        db.setTransactionSuccessful()
      } finally {
        db.endTransaction()
      }
      searchDb(db, match, terms, limit)
    }.getOrDefault(emptyList())

  private fun searchDb(db: SQLiteDatabase, match: String, terms: List<String>, limit: Int): List<NbgSessionFtsHit> {
    val safeLimit = limit.coerceIn(1, 100)
    val sql = """
      SELECT sessionPath, title, snippet($TABLE, 4, '', '', ' ... ', 24) AS snippet, docType, bm25($TABLE) AS rank
      FROM $TABLE
      WHERE $TABLE MATCH ?
      ORDER BY rank
      LIMIT ?
    """.trimIndent()
    return db.rawQuery(sql, arrayOf(match, safeLimit.toString())).use { cursor ->
      buildList {
        var row = 0
        while (cursor.moveToNext()) {
          val sessionPath = cursor.getString(0).orEmpty()
          val title = cursor.getString(1).orEmpty().ifBlank { "Local result" }
          val snippet = cursor.getString(2).orEmpty().ifBlank { title }
          val docType = cursor.getString(3).orEmpty().ifBlank { "fts" }
          add(
            NbgSessionFtsHit(
              sessionPath = sessionPath,
              title = title,
              snippet = snippet,
              score = (1000 - row).coerceAtLeast(1),
              matchType = "sqlite-$docType",
              queryTerms = terms,
            ),
          )
          row += 1
        }
      }
    }
  }

  private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts5(
          docId UNINDEXED,
          docType UNINDEXED,
          sessionPath UNINDEXED,
          title,
          body,
          updatedAtMs UNINDEXED
        )
        """.trimIndent(),
      )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      db.execSQL("DROP TABLE IF EXISTS $TABLE")
      onCreate(db)
    }
  }

  private companion object {
    const val DB_NAME = "nbg_session_fts.db"
    const val DB_VERSION = 1
    const val TABLE = "session_fts"
  }
}

internal fun nbgBuildSessionFtsDocuments(
  entries: List<HanakoSessionSummaryIndexEntry>,
  histories: List<Pair<HanakoSessionSummaryIndexEntry, HanakoHistorySnapshot?>>,
  learning: NbgAutonomousLearningSnapshot = NbgAutonomousLearningSnapshot(),
  skills: HanakoSkillsSnapshot = HanakoSkillsSnapshot(),
  drafts: NbgLearnedSkillDraftQueue = NbgLearnedSkillDraftQueue(),
): List<NbgSessionFtsDocument> {
  val summaryDocs = entries.map { entry ->
    NbgSessionFtsDocument(
      docId = "summary:${entry.sessionPath.sha256Hex().take(16)}",
      docType = "summary",
      sessionPath = entry.sessionPath,
      title = entry.title,
      body = entry.snippet,
      updatedAtMs = entry.updatedAtMs,
    )
  }
  val historyDocs = histories.mapNotNull { (entry, snapshot) ->
    val body = snapshot?.messages.orEmpty()
      .joinToString("\n") { message -> "${message.role}: ${message.text}" }
      .nbgSessionFtsCompact(12_000)
    if (body.isBlank()) null else NbgSessionFtsDocument(
      docId = "history:${entry.sessionPath.sha256Hex().take(16)}",
      docType = "message",
      sessionPath = entry.sessionPath,
      title = entry.title,
      body = body,
      updatedAtMs = entry.updatedAtMs,
    )
  }
  val memoryDocs = learning.localMemory.enabledEntries.map { memory ->
    NbgSessionFtsDocument(
      docId = "memory:${memory.id.ifBlank { memory.title }.sha256Hex().take(16)}",
      docType = "memory",
      sessionPath = memory.sourceSessionPath.ifBlank { memory.id },
      title = memory.title.ifBlank { "Memory" },
      body = listOf(memory.type, memory.content, memory.tags.joinToString(" ")).joinToString("\n"),
      updatedAtMs = memory.updatedAtMs,
    )
  }
  val skillDocs = skills.visibleSkills.map { skill ->
    NbgSessionFtsDocument(
      docId = "skill:${skill.name.sha256Hex().take(16)}",
      docType = "skill",
      sessionPath = skill.filePath.ifBlank { skill.name },
      title = skill.name,
      body = listOf(skill.displayDescription, skill.source, skill.externalLabel).joinToString("\n"),
    )
  }
  val draftDocs = drafts.visibleEntries.map { draft ->
    NbgSessionFtsDocument(
      docId = "skill-draft:${draft.id.sha256Hex().take(16)}",
      docType = "skill-draft",
      sessionPath = draft.id,
      title = draft.skillName,
      body = listOf(draft.description, draft.sourceTaskTitle, draft.status.label, draft.review.reason).joinToString("\n"),
      updatedAtMs = draft.updatedAtMs,
    )
  }
  return (summaryDocs + historyDocs + memoryDocs + skillDocs + draftDocs)
    .filter { it.title.isNotBlank() || it.body.isNotBlank() }
    .distinctBy { it.docId }
}

internal fun nbgSearchSessionFts(
  entries: List<HanakoSessionSummaryIndexEntry>,
  histories: List<Pair<HanakoSessionSummaryIndexEntry, HanakoHistorySnapshot?>>,
  query: String,
  limit: Int = 20,
): List<NbgSessionFtsHit> {
  val terms = nbgSessionFtsTerms(query)
  if (terms.isEmpty()) return emptyList()
  val summaryHits = entries.mapNotNull { entry ->
    val haystack = listOf(entry.title, entry.snippet).joinToString(" ").lowercase()
    val score = terms.sumOf { term -> if (haystack.contains(term)) 8 else 0 } + entry.messageCount.coerceAtMost(20)
    if (score <= 0) null else NbgSessionFtsHit(entry.sessionPath, entry.title, entry.snippet, score, "summary", terms)
  }
  val historyHits = histories.mapNotNull { (entry, snapshot) ->
    val messages = snapshot?.messages.orEmpty()
    val best = messages.mapNotNull { msg ->
      val text = msg.text.take(1_500)
      val lower = text.lowercase()
      val score = terms.sumOf { term -> if (lower.contains(term)) 12 else 0 }
      if (score <= 0) null else score to text.nbgSessionFtsSnippet(terms)
    }.maxByOrNull { it.first } ?: return@mapNotNull null
    NbgSessionFtsHit(entry.sessionPath, entry.title, best.second, best.first + 5, "message", terms)
  }
  return (summaryHits + historyHits)
    .groupBy { it.sessionPath }
    .map { (_, grouped) -> grouped.maxBy { it.score } }
    .sortedWith(compareByDescending<NbgSessionFtsHit> { it.score }.thenBy { it.title.lowercase() })
    .take(limit.coerceAtLeast(0))
}

internal fun nbgSessionFtsMatchQuery(query: String): String =
  nbgSessionFtsTerms(query).joinToString(" ") { "$it*" }

private fun nbgSessionFtsTerms(query: String): List<String> =
  query.lowercase().split(Regex("[^\\p{L}\\p{N}_]+")).filter { it.length >= 2 }.distinct().take(12)

private fun String.nbgSessionFtsSnippet(terms: List<String>, limit: Int = 220): String {
  val clean = nbgSessionFtsCompact(limit * 2)
  val lower = clean.lowercase()
  val index = terms.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
  val start = (index - 60).coerceAtLeast(0)
  return clean.drop(start).take(limit)
}

private fun String.nbgSessionFtsCompact(limit: Int): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit.coerceAtLeast(0))

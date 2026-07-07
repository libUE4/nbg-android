package com.nbg.android

data class NbgSessionFtsHit(
  val sessionPath: String,
  val title: String,
  val snippet: String,
  val score: Int,
  val matchType: String,
)

internal fun nbgSearchSessionFts(
  entries: List<HanakoSessionSummaryIndexEntry>,
  histories: List<Pair<HanakoSessionSummaryIndexEntry, HanakoHistorySnapshot?>>,
  query: String,
  limit: Int = 20,
): List<NbgSessionFtsHit> {
  val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}_-]+")).filter { it.length >= 2 }.distinct()
  if (terms.isEmpty()) return emptyList()
  val summaryHits = entries.mapNotNull { entry ->
    val haystack = listOf(entry.title, entry.snippet).joinToString(" ").lowercase()
    val score = terms.sumOf { term -> if (haystack.contains(term)) 8 else 0 } + entry.messageCount.coerceAtMost(20)
    if (score <= 0) null else NbgSessionFtsHit(entry.sessionPath, entry.title, entry.snippet, score, "summary")
  }
  val historyHits = histories.mapNotNull { (entry, snapshot) ->
    val messages = snapshot?.messages.orEmpty()
    val best = messages.mapNotNull { msg ->
      val text = msg.text.take(1_500)
      val lower = text.lowercase()
      val score = terms.sumOf { term -> if (lower.contains(term)) 12 else 0 }
      if (score <= 0) null else score to text.nbgSessionFtsSnippet(terms)
    }.maxByOrNull { it.first } ?: return@mapNotNull null
    NbgSessionFtsHit(entry.sessionPath, entry.title, best.second, best.first + 5, "message")
  }
  return (summaryHits + historyHits)
    .groupBy { it.sessionPath }
    .map { (_, grouped) -> grouped.maxBy { it.score } }
    .sortedWith(compareByDescending<NbgSessionFtsHit> { it.score }.thenBy { it.title.lowercase() })
    .take(limit.coerceAtLeast(0))
}

private fun String.nbgSessionFtsSnippet(terms: List<String>, limit: Int = 220): String {
  val clean = replace(Regex("\\s+"), " ").trim()
  val lower = clean.lowercase()
  val index = terms.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
  val start = (index - 60).coerceAtLeast(0)
  return clean.drop(start).take(limit)
}

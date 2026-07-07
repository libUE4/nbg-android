package com.nbg.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

internal const val NBG_MEMORY_PROVIDER_MANAGER_VERSION = "nbg-memory-provider-manager-v1"

data class NbgMemoryProviderContextBlock(
  val providerName: String,
  val providerKind: String,
  val text: String,
  val itemCount: Int = 0,
  val createdAtMs: Long = 0L,
)

data class NbgMemoryProviderPrefetchResult(
  val query: String,
  val blocks: List<NbgMemoryProviderContextBlock>,
  val snapshot: NbgAutonomousLearningSnapshot,
  val state: NbgMemoryProviderManagerState,
) {
  val hasContext: Boolean
    get() = blocks.any { it.text.isNotBlank() }

  fun toJson(): JSONObject =
    JSONObject()
      .put("version", NBG_MEMORY_PROVIDER_MANAGER_VERSION)
      .put("query", query)
      .put("blocks", JSONArray().also { array ->
        blocks.forEach { block ->
          array.put(
            JSONObject()
              .put("providerName", block.providerName)
              .put("providerKind", block.providerKind)
              .put("text", block.text)
              .put("itemCount", block.itemCount)
              .put("createdAtMs", block.createdAtMs),
          )
        }
      })
      .put("lastPrefetchAtMs", state.lastPrefetchAtMs)
      .put("lastSyncAtMs", state.lastSyncAtMs)
}

data class NbgMemoryProviderManagerState(
  val modelVersion: String = NBG_MEMORY_PROVIDER_MANAGER_VERSION,
  val prefetchCount: Int = 0,
  val syncCount: Int = 0,
  val queuedPrefetchCount: Int = 0,
  val lastQuery: String = "",
  val lastPrefetchAtMs: Long = 0L,
  val lastSyncAtMs: Long = 0L,
  val lastQueuedPrefetchAtMs: Long = 0L,
  val lastError: String = "",
)

internal interface NbgMemoryProviderManagerStorage {
  fun read(): String?
  fun write(raw: String)
}

internal class NbgSharedPreferencesMemoryProviderManagerStorage(context: Context) : NbgMemoryProviderManagerStorage {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  override fun read(): String? =
    prefs.getString(KEY_STATE, null)

  override fun write(raw: String) {
    prefs.edit().putString(KEY_STATE, raw).apply()
  }

  private companion object {
    const val PREFS = "nbg_memory_provider_manager"
    const val KEY_STATE = "state"
  }
}

internal class NbgMemoryProviderManager(
  private val learningEngine: NbgAutonomousLearningEngine,
  private val storage: NbgMemoryProviderManagerStorage,
) {
  constructor(context: Context) : this(
    learningEngine = NbgAutonomousLearningEngine(context),
    storage = NbgSharedPreferencesMemoryProviderManagerStorage(context),
  )

  fun load(): NbgMemoryProviderManagerState =
    parseNbgMemoryProviderManagerState(storage.read())

  fun prefetchAll(
    query: String,
    sessions: List<HanakoSessionSummaryIndexEntry> = emptyList(),
    limit: Int = 8,
    nowMs: Long = System.currentTimeMillis(),
  ): NbgMemoryProviderPrefetchResult {
    val cleanQuery = query.nbgMemoryProviderCompact(limit = 500)
    val snapshot = learningEngine.recall(query = cleanQuery, sessions = sessions, limit = limit)
    val items = snapshot.recallBundle.items.take(limit.coerceAtLeast(0))
    val block = NbgMemoryProviderContextBlock(
      providerName = "android-local-learning",
      providerKind = "local",
      text = items.joinToString("\n") { item ->
        "[${item.kind.wireName}] ${item.title}: ${item.snippet}".nbgMemoryProviderCompact(limit = 500)
      },
      itemCount = items.size,
      createdAtMs = nowMs.coerceAtLeast(0L),
    )
    val state = save(
      load().copy(
        prefetchCount = load().prefetchCount + 1,
        lastQuery = cleanQuery,
        lastPrefetchAtMs = nowMs.coerceAtLeast(0L),
        lastError = "",
      ),
    )
    return NbgMemoryProviderPrefetchResult(
      query = cleanQuery,
      blocks = listOf(block).filter { it.text.isNotBlank() },
      snapshot = snapshot,
      state = state,
    )
  }

  fun syncAll(
    turn: NbgLearningSourceTurn,
    nowMs: Long = System.currentTimeMillis(),
  ): NbgAutonomousLearningSnapshot {
    val snapshot = learningEngine.learnFromTurn(turn, nowMs = nowMs)
    save(
      load().copy(
        syncCount = load().syncCount + 1,
        lastQuery = listOf(turn.userText, turn.assistantText).joinToString(" ").nbgMemoryProviderCompact(limit = 500),
        lastSyncAtMs = nowMs.coerceAtLeast(0L),
        lastError = "",
      ),
    )
    queuePrefetchAll(
      query = listOf(turn.userText, turn.assistantText).joinToString(" "),
      sessions = emptyList(),
      nowMs = nowMs,
    )
    return snapshot
  }

  fun queuePrefetchAll(
    query: String,
    sessions: List<HanakoSessionSummaryIndexEntry> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
  ): NbgMemoryProviderManagerState {
    val cleanQuery = query.nbgMemoryProviderCompact(limit = 500)
    val queued = save(
      load().copy(
        queuedPrefetchCount = load().queuedPrefetchCount + 1,
        lastQuery = cleanQuery,
        lastQueuedPrefetchAtMs = nowMs.coerceAtLeast(0L),
        lastError = "",
      ),
    )
    NBG_MEMORY_PROVIDER_EXECUTOR.execute {
      runCatching {
        prefetchAll(query = cleanQuery, sessions = sessions, limit = 8, nowMs = System.currentTimeMillis())
      }.onFailure { error ->
        save(load().copy(lastError = (error.message ?: error.javaClass.simpleName).nbgMemoryProviderCompact(limit = 240)))
      }
    }
    return queued
  }

  private fun save(state: NbgMemoryProviderManagerState): NbgMemoryProviderManagerState {
    val normalized = state.normalized()
    storage.write(normalized.toJsonString())
    return normalized
  }
}

private val NBG_MEMORY_PROVIDER_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
  Thread(runnable, "nbg-memory-provider-manager").apply { isDaemon = true }
}

internal fun parseNbgMemoryProviderManagerState(raw: String?): NbgMemoryProviderManagerState =
  runCatching {
    val root = JSONObject(raw?.takeIf { it.isNotBlank() } ?: "{}")
    NbgMemoryProviderManagerState(
      prefetchCount = root.optInt("prefetchCount", 0).coerceAtLeast(0),
      syncCount = root.optInt("syncCount", 0).coerceAtLeast(0),
      queuedPrefetchCount = root.optInt("queuedPrefetchCount", 0).coerceAtLeast(0),
      lastQuery = root.cleanString("lastQuery").orEmpty().nbgMemoryProviderCompact(limit = 500),
      lastPrefetchAtMs = root.optLong("lastPrefetchAtMs", 0L).coerceAtLeast(0L),
      lastSyncAtMs = root.optLong("lastSyncAtMs", 0L).coerceAtLeast(0L),
      lastQueuedPrefetchAtMs = root.optLong("lastQueuedPrefetchAtMs", 0L).coerceAtLeast(0L),
      lastError = root.cleanString("lastError").orEmpty().nbgMemoryProviderCompact(limit = 240),
    ).normalized()
  }.getOrDefault(NbgMemoryProviderManagerState())

private fun NbgMemoryProviderManagerState.normalized(): NbgMemoryProviderManagerState =
  copy(
    prefetchCount = prefetchCount.coerceAtLeast(0),
    syncCount = syncCount.coerceAtLeast(0),
    queuedPrefetchCount = queuedPrefetchCount.coerceAtLeast(0),
    lastQuery = lastQuery.nbgMemoryProviderCompact(limit = 500),
    lastPrefetchAtMs = lastPrefetchAtMs.coerceAtLeast(0L),
    lastSyncAtMs = lastSyncAtMs.coerceAtLeast(0L),
    lastQueuedPrefetchAtMs = lastQueuedPrefetchAtMs.coerceAtLeast(0L),
    lastError = lastError.nbgMemoryProviderCompact(limit = 240),
  )

private fun NbgMemoryProviderManagerState.toJsonString(): String =
  JSONObject()
    .put("modelVersion", modelVersion)
    .put("prefetchCount", prefetchCount)
    .put("syncCount", syncCount)
    .put("queuedPrefetchCount", queuedPrefetchCount)
    .put("lastQuery", lastQuery)
    .put("lastPrefetchAtMs", lastPrefetchAtMs)
    .put("lastSyncAtMs", lastSyncAtMs)
    .put("lastQueuedPrefetchAtMs", lastQueuedPrefetchAtMs)
    .put("lastError", lastError)
    .toString()

private fun String.nbgMemoryProviderCompact(limit: Int): String =
  nbgRedactDiagnosticText(this)
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(limit.coerceAtLeast(0))

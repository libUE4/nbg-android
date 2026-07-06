package com.nbg.android

import android.content.Context

internal interface NbgLearningAuditStorage {
  fun read(): String?
  fun write(raw: String)
}

internal class NbgSharedPreferencesLearningAuditStorage(context: Context) : NbgLearningAuditStorage {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  override fun read(): String? =
    prefs.getString(KEY_AUDIT, null)

  override fun write(raw: String) {
    prefs.edit().putString(KEY_AUDIT, raw).apply()
  }

  private companion object {
    const val PREFS = "nbg_learning_audit"
    const val KEY_AUDIT = "audit"
  }
}

internal class NbgLearningAuditStore(
  private val storage: NbgLearningAuditStorage,
) {
  constructor(context: Context) : this(NbgSharedPreferencesLearningAuditStorage(context))

  fun load(): NbgLearningAuditLog =
    parseNbgLearningAuditLog(storage.read())

  fun save(log: NbgLearningAuditLog): NbgLearningAuditLog {
    val normalized = nbgBuildLearningAuditLog(log.events)
    storage.write(normalized.toJsonString())
    return normalized
  }

  fun record(event: NbgLearningEvent): NbgLearningAuditLog =
    save(NbgLearningAuditLog(load().events + event))

  fun updateStatus(
    id: String,
    status: NbgLearningEventStatus,
    nowMs: Long = System.currentTimeMillis(),
    error: String = "",
  ): NbgLearningAuditLog {
    val cleanId = id.trim()
    if (cleanId.isBlank()) return load()
    val next = load().events.map { event ->
      if (event.id == cleanId) event.withStatus(status, nowMs, error = error.ifBlank { event.error }) else event
    }
    return save(NbgLearningAuditLog(next))
  }

  fun revert(id: String, nowMs: Long = System.currentTimeMillis()): NbgLearningAuditLog =
    updateStatus(id, NbgLearningEventStatus.Reverted, nowMs)
}

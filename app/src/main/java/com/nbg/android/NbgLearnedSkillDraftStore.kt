package com.nbg.android

import android.content.Context

internal interface NbgLearnedSkillDraftStorage {
  fun read(): String?
  fun write(raw: String)
}

internal class NbgSharedPreferencesLearnedSkillDraftStorage(context: Context) : NbgLearnedSkillDraftStorage {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  override fun read(): String? =
    prefs.getString(KEY_QUEUE, null)

  override fun write(raw: String) {
    prefs.edit().putString(KEY_QUEUE, raw).apply()
  }

  private companion object {
    const val PREFS = "nbg_learned_skill_drafts"
    const val KEY_QUEUE = "queue"
  }
}

internal class NbgLearnedSkillDraftStore(
  private val storage: NbgLearnedSkillDraftStorage,
) {
  constructor(context: Context) : this(NbgSharedPreferencesLearnedSkillDraftStorage(context))

  fun load(): NbgLearnedSkillDraftQueue =
    parseNbgLearnedSkillDraftQueue(storage.read())

  fun save(queue: NbgLearnedSkillDraftQueue): NbgLearnedSkillDraftQueue {
    val normalized = nbgBuildLearnedSkillDraftQueue(queue.entries)
    storage.write(normalized.toJsonString())
    return normalized
  }

  fun upsert(entry: NbgLearnedSkillDraftQueueEntry): NbgLearnedSkillDraftQueue =
    save(nbgBuildLearnedSkillDraftQueue(load().entries + entry))

  fun reject(id: String, nowMs: Long = System.currentTimeMillis()): NbgLearnedSkillDraftQueue {
    val cleanId = id.trim()
    if (cleanId.isBlank()) return load()
    val next = load().entries.map { entry ->
      if (entry.id == cleanId) {
        entry.copy(
          status = NbgLearnedSkillDraftStatus.Rejected,
          updatedAtMs = nowMs.coerceAtLeast(entry.updatedAtMs),
        )
      } else {
        entry
      }
    }
    return save(NbgLearnedSkillDraftQueue(next))
  }
}

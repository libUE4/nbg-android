package com.nbg.android

import android.content.Context

internal interface NbgSkillCuratorStorage {
  fun read(): String?
  fun write(raw: String)
}

internal class NbgSharedPreferencesSkillCuratorStorage(context: Context) : NbgSkillCuratorStorage {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  override fun read(): String? =
    prefs.getString(KEY_METADATA, null)

  override fun write(raw: String) {
    prefs.edit().putString(KEY_METADATA, raw).apply()
  }

  private companion object {
    const val PREFS = "nbg_skill_curator"
    const val KEY_METADATA = "metadata"
  }
}

internal class NbgSkillCuratorStore(
  private val storage: NbgSkillCuratorStorage,
) {
  constructor(context: Context) : this(NbgSharedPreferencesSkillCuratorStorage(context))

  fun load(): NbgSkillCuratorMetadata =
    parseNbgSkillCuratorMetadata(storage.read())

  fun save(metadata: NbgSkillCuratorMetadata): NbgSkillCuratorMetadata {
    val normalized = nbgBuildSkillCuratorMetadata(metadata.entries.values.toList())
    storage.write(normalized.toJsonString())
    return normalized
  }

  fun recordUse(skillName: String, nowMs: Long = System.currentTimeMillis()): NbgSkillCuratorMetadata =
    save(nbgRecordSkillCuratorUse(load(), skillName, nowMs))

  fun archive(skillName: String, reason: String = "user_archive", nowMs: Long = System.currentTimeMillis()): NbgSkillCuratorMetadata =
    save(nbgArchiveSkillCuratorEntry(load(), skillName, reason, nowMs))

  fun restore(skillName: String): NbgSkillCuratorMetadata =
    save(nbgRestoreSkillCuratorEntry(load(), skillName))
}

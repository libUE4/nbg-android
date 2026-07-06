package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentSkillTranslationState {
  var translations by mutableStateOf<Map<String, String>>(emptyMap())
    private set
  var translatingSkillName by mutableStateOf<String?>(null)
    private set
  var messages by mutableStateOf<Map<String, String>>(emptyMap())
    private set

  val isTranslating: Boolean
    get() = translatingSkillName != null

  fun recordNoModel(skillName: String) {
    messages = messages + (skillName to "没有可用的 URL API 模型，已优先显示 Skill 自带中文。")
  }

  fun begin(skillName: String): Boolean {
    if (isTranslating) return false
    translatingSkillName = skillName
    messages = messages + (skillName to "正在翻译...")
    return true
  }

  fun applySuccess(skillName: String, result: NbgSkillDescriptionTranslateResult) {
    if (result.translations.isNotEmpty()) {
      translations = translations + result.translations
    }
    messages = messages + (skillName to result.message)
  }

  fun applyFailure(skillName: String, error: Throwable) {
    messages = messages + (skillName to "翻译失败：${error.message ?: error::class.java.simpleName}")
  }

  fun finish(skillName: String) {
    if (translatingSkillName == skillName) {
      translatingSkillName = null
    }
  }
}

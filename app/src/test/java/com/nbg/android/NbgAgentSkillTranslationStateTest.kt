package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentSkillTranslationStateTest {
  @Test
  fun recordNoModelStoresMessageWithoutStartingBusyState() {
    val state = NbgAgentSkillTranslationState()

    state.recordNoModel("terminal")

    assertFalse(state.isTranslating)
    assertNull(state.translatingSkillName)
    assertEquals("没有可用的 URL API 模型，已优先显示 Skill 自带中文。", state.messages["terminal"])
  }

  @Test
  fun beginSetsBusyAndRejectsConcurrentTranslation() {
    val state = NbgAgentSkillTranslationState()

    assertTrue(state.begin("terminal"))
    assertFalse(state.begin("memory"))

    assertTrue(state.isTranslating)
    assertEquals("terminal", state.translatingSkillName)
    assertEquals("正在翻译...", state.messages["terminal"])
    assertFalse(state.messages.containsKey("memory"))
  }

  @Test
  fun successMergesTranslationsAndStoresResultMessage() {
    val state = NbgAgentSkillTranslationState()
    assertTrue(state.begin("terminal"))

    state.applySuccess(
      "terminal",
      NbgSkillDescriptionTranslateResult(
        translations = mapOf("terminal" to "终端能力", "memory" to "记忆能力"),
        message = "翻译完成",
      ),
    )

    assertEquals("终端能力", state.translations["terminal"])
    assertEquals("记忆能力", state.translations["memory"])
    assertEquals("翻译完成", state.messages["terminal"])
  }

  @Test
  fun failureStoresRecoverableMessageAndKeepsPreviousTranslations() {
    val state = NbgAgentSkillTranslationState()
    state.applySuccess(
      "terminal",
      NbgSkillDescriptionTranslateResult(mapOf("terminal" to "终端能力"), "翻译完成"),
    )

    state.applyFailure("memory", IllegalStateException("network down"))

    assertEquals("终端能力", state.translations["terminal"])
    assertEquals("翻译失败：network down", state.messages["memory"])
  }

  @Test
  fun failureWithoutMessageFallsBackToExceptionClassName() {
    val state = NbgAgentSkillTranslationState()

    state.applyFailure("memory", RuntimeException())

    assertEquals("翻译失败：RuntimeException", state.messages["memory"])
  }

  @Test
  fun finishOnlyClearsMatchingBusySkill() {
    val state = NbgAgentSkillTranslationState()
    assertTrue(state.begin("terminal"))

    state.finish("memory")
    assertEquals("terminal", state.translatingSkillName)

    state.finish("terminal")
    assertNull(state.translatingSkillName)
    assertFalse(state.isTranslating)
  }
}

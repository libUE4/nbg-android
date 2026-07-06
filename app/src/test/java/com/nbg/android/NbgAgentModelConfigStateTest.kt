package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentModelConfigStateTest {
  @Test
  fun initialStateIsIdleAndEmpty() {
    val state = NbgAgentModelConfigState()

    assertNull(state.config)
    assertFalse(state.loading)
    assertNull(state.error)
  }

  @Test
  fun beginLoadSetsLoadingAndClearsPreviousErrorWithoutClearingConfig() {
    val state = NbgAgentModelConfigState()
    val config = modelConfig("gpt-5.5")
    state.applyLoaded(config)
    state.applyFailed("network down")

    state.beginLoad()

    assertSame(config, state.config)
    assertTrue(state.loading)
    assertNull(state.error)
  }

  @Test
  fun applyLoadedStoresConfigAndClearsLoadingAndError() {
    val state = NbgAgentModelConfigState()
    val config = modelConfig("gpt-5.5")

    state.beginLoad()
    state.applyLoaded(config)

    assertSame(config, state.config)
    assertFalse(state.loading)
    assertNull(state.error)
  }

  @Test
  fun applyFailedStoresErrorAndKeepsPreviousConfig() {
    val state = NbgAgentModelConfigState()
    val config = modelConfig("gpt-5.5")
    state.applyLoaded(config)

    state.beginLoad()
    state.applyFailed("provider unavailable")

    assertSame(config, state.config)
    assertFalse(state.loading)
    assertEquals("provider unavailable", state.error)
  }

  @Test
  fun recordLocalErrorDoesNotChangeLoadingOrConfig() {
    val state = NbgAgentModelConfigState()
    val config = modelConfig("gpt-5.5")
    state.applyLoaded(config)
    state.beginLoad()

    state.recordLocalError("没有找到已保存的网址 API 模型")

    assertSame(config, state.config)
    assertTrue(state.loading)
    assertEquals("没有找到已保存的网址 API 模型", state.error)
  }

  private fun modelConfig(id: String): HanakoAgentModelConfig =
    HanakoAgentModelConfig(
      models = listOf(
        HanakoModelSummary(
          id = id,
          name = id,
          provider = "openai",
          isCurrent = true,
        ),
      ),
    )
}

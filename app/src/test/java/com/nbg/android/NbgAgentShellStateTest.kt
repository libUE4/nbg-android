package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Test

class NbgAgentShellStateTest {
  @Test
  fun initialPageIsApplied() {
    val state = NbgAgentShellState(NbgShellPage.Terminal)

    assertEquals(NbgShellPage.Terminal, state.page)
  }

  @Test
  fun showPageUpdatesCurrentPage() {
    val state = NbgAgentShellState(NbgShellPage.Chat)

    state.showPage(NbgShellPage.Mcp)

    assertEquals(NbgShellPage.Mcp, state.page)
  }

  @Test
  fun showChatReturnsToChat() {
    val state = NbgAgentShellState(NbgShellPage.Skills)

    state.showChat()

    assertEquals(NbgShellPage.Chat, state.page)
  }

  @Test
  fun repeatedShowPageKeepsLatestPage() {
    val state = NbgAgentShellState(NbgShellPage.Memory)

    state.showPage(NbgShellPage.Pets)
    state.showPage(NbgShellPage.Appearance)

    assertEquals(NbgShellPage.Appearance, state.page)
  }
}

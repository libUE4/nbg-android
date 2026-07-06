package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NbgAgentConversationDisplayTest {
  @Test
  fun metaLineShowsSearchTypeSummaryPinAndSessionSource() {
    val conversation = NbgAgentConversation(
      path = "/root/.hanako/sessions/one.jsonl",
      title = "修复 Android 构建",
      subtitle = "NBG-Code / 2026-07-06",
      matchType = "content",
      pinned = true,
      hasSummary = true,
    )

    assertEquals(
      "置顶 · 内容匹配 · 有摘要 · NBG-Code / 2026-07-06",
      nbgConversationMetaLine(conversation),
    )
  }

  @Test
  fun metaLineFallsBackAndLabelsKnownMatchTypes() {
    assertEquals(
      "HanakoPro",
      nbgConversationMetaLine(NbgAgentConversation(path = "p", title = "t", subtitle = "")),
    )
    assertEquals("标题匹配", nbgConversationMatchTypeLabel("title"))
    assertEquals("摘要匹配", nbgConversationMatchTypeLabel("summary"))
    assertEquals("终端匹配", nbgConversationMatchTypeLabel("shell"))
    assertEquals("custom 匹配", nbgConversationMatchTypeLabel("custom"))
  }

  @Test
  fun previewSnippetIsCleanedBoundedAndNotDuplicatedTitle() {
    val conversation = NbgAgentConversation(
      path = "p",
      title = "构建失败",
      subtitle = "",
      snippet = "  第一行\n\n第二行\t第三行  ",
    )
    val longSnippet = NbgAgentConversation(
      path = "p2",
      title = "长片段",
      subtitle = "",
      snippet = "x".repeat(220),
    )
    val titleDuplicate = NbgAgentConversation(
      path = "p3",
      title = "同名标题",
      subtitle = "",
      snippet = "同名标题",
    )

    assertEquals("第一行 第二行 第三行", nbgConversationPreviewSnippet(conversation))
    assertEquals(180, nbgConversationPreviewSnippet(longSnippet)?.length)
    assertNull(nbgConversationPreviewSnippet(titleDuplicate))
  }
}

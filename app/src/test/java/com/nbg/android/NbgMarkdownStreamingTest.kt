package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgMarkdownStreamingTest {
  @Test
  fun streamingTextIsPreparedAsMarkdown() {
    val markdown = """
      |Name|Value|
      |-|-|
      |Mode|Live|
    """.trimIndent()

    val prepared = nbgPrepareText(markdown, streaming = true)

    assertEquals(NbgPreparedTextKind.Markdown, prepared.kind)
    assertTrue(prepared.blocks.any { it is NbgPreparedTextBlock.TableBlock })
  }

  @Test
  fun plainStreamingTextUsesLightweightMarkdownPreparation() {
    val text = "普通输出不需要生成 Markdown 块，只需要保留行内文本。"

    val prepared = nbgPrepareText(text, streaming = true)

    assertEquals(NbgPreparedTextKind.Markdown, prepared.kind)
    assertTrue(prepared.blocks.isEmpty())
    assertFalse(nbgTextNeedsMarkdownBlocks(text))
  }

  @Test
  fun streamingChunksStayMarkdownAware() {
    val text = """
      Before
      ```kotlin
      val message = "live markdown"
      ```
      After
    """.trimIndent()

    val chunks = streamingTextChunksFor(text)
    val prepared = chunks.map { nbgPrepareText(it, streaming = true) }

    assertTrue(prepared.all { it.kind == NbgPreparedTextKind.Markdown })
    assertTrue(prepared.any { item -> item.blocks.any { it is NbgPreparedTextBlock.CodeBlock } })
  }

  @Test
  fun appendedStreamingChunksReuseStablePrefix() {
    val previousText = buildString {
      repeat(48) { index ->
        append("第 $index 行：这是一段用于测试流式输出分块复用的内容。\n")
      }
    }
    val previousChunks = streamingTextChunksFor(previousText)
    val nextText = previousText + "最后追加的一小段内容。"

    val nextChunks = appendStreamingTextChunksAfterPrefix(previousChunks, previousText, nextText)

    assertTrue(previousChunks.size > 2)
    assertEquals(previousChunks.dropLast(1), nextChunks.take(previousChunks.size - 1))
    assertEquals(streamingTextChunksFor(nextText), nextChunks)
  }

  @Test
  fun appendedStreamingChunksFallBackWhenTextIsReplaced() {
    val previousText = "旧内容\n".repeat(40)
    val previousChunks = streamingTextChunksFor(previousText)
    val replacement = "新内容\n".repeat(40)

    assertEquals(
      streamingTextChunksFor(replacement),
      appendStreamingTextChunksAfterPrefix(previousChunks, previousText, replacement),
    )
  }

  @Test
  fun streamingPretextCacheKeepsOnlyLatestVersionForSameChunkKey() {
    val cache = NbgPretextCache(maxEntries = 32)

    cache.prepareText("message:chunk:0", "hello", streaming = true)
    cache.prepareText("message:chunk:0", "hello world", streaming = true)
    cache.prepareText("message:chunk:0", "hello world again", streaming = true)

    assertEquals(1, cache.entryCountForTest())
  }

  @Test
  fun historyPretextCacheCanKeepStableDistinctVersions() {
    val cache = NbgPretextCache(maxEntries = 32)

    cache.prepareText("message", "old", streaming = false)
    cache.prepareText("message", "new", streaming = false)

    assertEquals(2, cache.entryCountForTest())
  }

  @Test
  fun assistantStreamingTextHidesPartialAndMalformedThinkingTags() {
    val chunks = listOf("<thi", "nking>隐藏思考", "<thinking>\n", "主要回复")
    var raw = ""
    val visibleSnapshots = chunks.map { chunk ->
      raw += chunk
      nbgCleanAssistantVisibleStreamingText(raw)
    }

    assertEquals(listOf("", "", "", "主要回复"), visibleSnapshots)
    assertEquals("主要回复", nbgCleanAssistantVisibleText(raw))
    assertEquals("主要回复", stripNbgInternalText(raw))
  }
}

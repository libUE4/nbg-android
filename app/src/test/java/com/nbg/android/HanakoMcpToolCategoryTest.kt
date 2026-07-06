package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HanakoMcpToolCategoryTest {
  @Test
  fun mcpToolCategoryRecognizesHermesToolGatewayFamilies() {
    val cases = listOf(
      HanakoMcpTool("brave_search", description = "web search") to NbgMcpToolCategory.WebSearch,
      HanakoMcpTool("browser_navigate", description = "open page") to NbgMcpToolCategory.Browser,
      HanakoMcpTool("describe_image", description = "vision OCR") to NbgMcpToolCategory.Vision,
      HanakoMcpTool("generate_image", description = "FAL image model") to NbgMcpToolCategory.Image,
      HanakoMcpTool("transcribe_audio", description = "whisper speech to text") to NbgMcpToolCategory.Speech,
      HanakoMcpTool("render_video", description = "ffmpeg media export") to NbgMcpToolCategory.Media,
      HanakoMcpTool("memory_recall", description = "Cognee knowledge graph") to NbgMcpToolCategory.Memory,
      HanakoMcpTool("read_file", description = "filesystem path") to NbgMcpToolCategory.File,
      HanakoMcpTool("shell_exec", description = "terminal command") to NbgMcpToolCategory.Terminal,
      HanakoMcpTool("query_database", description = "SQL table query") to NbgMcpToolCategory.Data,
    )

    cases.forEach { (tool, expected) ->
      assertEquals(expected, nbgMcpToolCategory(tool))
    }
  }

  @Test
  fun connectorSummarizesDistinctToolCategoriesInStableOrder() {
    val connector = HanakoMcpConnector(
      id = "gateway",
      name = "Tool Gateway",
      description = "browser and media tools",
      tools = listOf(
        HanakoMcpTool("render_video"),
        HanakoMcpTool("brave_search"),
        HanakoMcpTool("browser_click"),
        HanakoMcpTool("brave_search"),
      ),
    )

    assertEquals(
      listOf(NbgMcpToolCategory.WebSearch, NbgMcpToolCategory.Browser, NbgMcpToolCategory.Media),
      connector.toolCategories,
    )
    assertTrue(connector.toolCategories.map { it.label }.contains("Web Search"))
  }
}

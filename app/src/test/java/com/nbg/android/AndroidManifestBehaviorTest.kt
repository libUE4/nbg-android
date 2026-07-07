package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.io.path.createTempDirectory

class AndroidManifestBehaviorTest {
  private fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun readAppSource(vararg names: String): String =
    names.joinToString("\n") { name -> File("src/main/java/com/nbg/android/$name").readText() }

  private fun readTarTextEntry(file: File, entryName: String): String {
    tarArchiveInputStream(file).use { input ->
      while (true) {
        val header = input.readNBytes(TAR_BLOCK_SIZE)
        if (header.size < TAR_BLOCK_SIZE || header.all { it == 0.toByte() }) break
        val name = header.tarString(offset = 0, length = 100)
        val size = header.tarString(offset = 124, length = 12)
          .trim()
          .toLongOrNull(radix = 8) ?: error("Invalid tar entry size for $name in ${file.path}")
        val paddedSize = ((size + TAR_BLOCK_SIZE - 1) / TAR_BLOCK_SIZE) * TAR_BLOCK_SIZE
        val matches = name == entryName || name == entryName.removePrefix("./") || "./$name" == entryName
        if (matches) {
          require(size <= TAR_TEXT_ENTRY_MAX_BYTES) {
            "Tar entry $entryName is too large for source oracle: $size bytes"
          }
          val content = input.readNBytes(size.toInt())
          require(content.size == size.toInt()) {
            "Unexpected EOF while reading tar entry $entryName in ${file.path}"
          }
          input.skipFully(paddedSize - size)
          return content.toString(Charsets.UTF_8)
        }
        input.skipFully(paddedSize)
      }
    }
    error("Missing tar entry $entryName in ${file.path}")
  }

  private fun tarArchiveInputStream(file: File): InputStream {
    val input = file.inputStream().buffered()
    input.mark(2)
    val first = input.read()
    val second = input.read()
    input.reset()
    return if (first == GZIP_MAGIC_0 && second == GZIP_MAGIC_1) {
      GZIPInputStream(input).buffered()
    } else {
      input
    }
  }

  private fun ByteArray.tarString(offset: Int, length: Int): String =
    copyOfRange(offset, offset + length)
      .takeWhile { it != 0.toByte() }
      .toByteArray()
      .toString(Charsets.UTF_8)

  private fun java.io.InputStream.skipFully(bytes: Long) {
    var remaining = bytes
    while (remaining > 0) {
      val skipped = skip(remaining)
      if (skipped <= 0) {
        if (read() == -1) error("Unexpected EOF while skipping tar padding")
        remaining -= 1
      } else {
        remaining -= skipped
      }
    }
  }

  private companion object {
    const val GZIP_MAGIC_0 = 0x1f
    const val GZIP_MAGIC_1 = 0x8b
    const val TAR_BLOCK_SIZE = 512
    const val TAR_TEXT_ENTRY_MAX_BYTES = 10 * 1024 * 1024
  }

  private fun withPrivateDeclarationAliases(source: String): String =
    source + "\n" + source.lineSequence().joinToString("\n") { line ->
      if (line.startsWith("internal ")) "private ${line.removePrefix("internal ")}" else line
    }

  private fun readAgentUiSource(): String =
    withPrivateDeclarationAliases(
      readAppSource(
        "NbgAgentUi.kt",
        "NbgAgentApiEditorState.kt",
        "NbgAgentChatPreferenceState.kt",
        "NbgAgentConfirmationState.kt",
        "NbgAgentContentBlockPatchState.kt",
        "NbgAgentDiagnosticsExportState.kt",
        "NbgAgentFileShareState.kt",
        "NbgAgentFlushSchedulerState.kt",
        "NbgAgentHistoryState.kt",
        "NbgAgentMessageState.kt",
        "NbgAgentModelConfigState.kt",
        "NbgAgentPetState.kt",
        "NbgAgentShellState.kt",
        "NbgAgentSkillTranslationState.kt",
        "NbgAgentStreamingControllerState.kt",
        "NbgAgentStreamingTextState.kt",
        "NbgAgentToolStatusState.kt",
        "NbgAgentTodoState.kt",
        "NbgAgentUserMessageDedupState.kt",
        "NbgAgentUrlApiEntriesState.kt",
        "NbgAgentUrlApiSelectionState.kt",
        "NbgDiagnosticsExport.kt",
        "NbgDiagnosticsExportShare.kt",
        "NbgDiagnosticsExportUi.kt",
        "NbgAgentUiLogic.kt",
        "NbgAgentChatUi.kt",
        "NbgAgentDrawerUi.kt",
        "NbgAgentFileShareUi.kt",
        "NbgAgentMcpUi.kt",
        "NbgAgentSkillsUi.kt",
        "NbgAgentCapabilityPagesUi.kt",
        "NbgAgentUrlApiUi.kt",
        "NbgAppearanceUi.kt",
        "NbgAgentDialogs.kt",
        "NbgAgentMessageUi.kt",
        "NbgMessageMarkdownUi.kt",
        "NbgMarkdownText.kt",
        "NbgToolPresentation.kt",
        "NbgAgentComposerUi.kt",
        "NbgPixelPetUi.kt",
        "NbgPixelPetBehavior.kt",
        "NbgPixelPetSpriteUi.kt",
        "NbgPetModels.kt",
        "NbgPetResourceIntegrity.kt",
        "NbgPetStore.kt",
        "NbgBundledPetSeeds.kt",
        "NbgPetDexClient.kt",
        "NbgPetDexPreviewStore.kt",
        "NbgPetsUi.kt",
        "NbgAgentTheme.kt",
      ),
    )

  private fun readHanakoBridgeSource(): String =
    withPrivateDeclarationAliases(
      readAppSource(
        "HanakoBridge.kt",
        "HanakoChatController.kt",
        "HanakoRealtimeToolEvents.kt",
        "HanakoHistoryCacheStore.kt",
        "HanakoSkillInstallSupport.kt",
        "HanakoMcpModels.kt",
        "HanakoSkillsModels.kt",
        "HanakoToolParsing.kt",
        "HanakoServerState.kt",
        "HanakoBackgroundWarmup.kt",
        "HanakoApiClient.kt",
        "HanakoServerLauncher.kt",
        "NbgApiStore.kt",
      ),
    )

  @Test
  fun localThinkingCapabilityRulesMirrorHanakoModelInference() {
    val gpt54 = nbgSupportedThinkingLevelsForModel("gpt-5.4", provider = "openai")
    val gpt55 = nbgSupportedThinkingLevelsForModel("gpt-5.5", provider = "openai")
    val deepseekV4 = nbgSupportedThinkingLevelsForModel("deepseek-v4-pro", baseUrl = "https://api.deepseek.com/v1")
    val deepseekChat = nbgSupportedThinkingLevelsForModel("deepseek-chat", baseUrl = "https://api.deepseek.com/v1")
    val claude = nbgSupportedThinkingLevelsForModel("claude-sonnet-4-5", provider = "anthropic", api = "anthropic-messages")
    val claude48Proxy = nbgSupportedThinkingLevelsForModel("claude-opus-4-8", provider = "urlapi-a", baseUrl = "https://mdkj.lol/v1")
    val kimi = nbgSupportedThinkingLevelsForModel("kimi-k2-thinking", baseUrl = "https://api.moonshot.cn/v1")
    val unknown = nbgSupportedThinkingLevelsForModel("custom-chat-model")
    val gpt4o = nbgSupportedThinkingLevelsForModel("gpt-4o", provider = "openai", baseUrl = "https://api.openai.com/v1")

    assertTrue("xhigh" in gpt54)
    assertTrue("xhigh" in gpt55)
    assertTrue("xhigh" in deepseekV4)
    assertTrue(deepseekChat == listOf("off", "auto"))
    assertTrue("high" in claude)
    assertTrue("xhigh" in claude48Proxy)
    assertTrue("high" in kimi)
    assertFalse("xhigh" in claude)
    assertTrue(unknown == listOf("off", "auto"))
    assertTrue(gpt4o == listOf("off", "auto"))
    assertTrue(nbgThinkingSourceForModel("deepseek-v4-pro", baseUrl = "https://api.deepseek.com/v1") == "local-deepseek")
    assertTrue(nbgThinkingSourceForModel("deepseek-chat", baseUrl = "https://api.deepseek.com/v1") == "local-basic")
    assertTrue(nbgNormalizeThinkingLevels(listOf("disabled", "default", "maximum")) == listOf("off", "auto", "xhigh"))
    assertEquals("openai", nbgHanakoProviderForUrlApi("https://nbgapi.com", "claude-opus-4-8"))
    assertEquals("openai", nbgHanakoProviderForUrlApi("https://nbgapi.com", "deepseek-v4-pro"))
  }

  @Test
  fun androidPromptAndThinkingTextAreNotRewritten() {
    val thinkingText = "The user is asking what model I am. I should check my current status."

    assertEquals("1", "  1  ".trim())
    assertEquals("1", nbgStripLegacyAndroidPromptWrapper("legacy wrapper\n\n用户消息：\n1"))
    assertEquals(thinkingText, nbgThinkingTextForAndroidDisplay(thinkingText))
    assertEquals("The user", nbgThinkingTextForAndroidDisplay("The user"))
    assertEquals(
      "Thelocalskillsdirectoryhas: hana-plugin-creator, officedocuments, quiet-musing, skill-creator.",
      nbgThinkingTextForAndroidDisplay("Thelocalskillsdirectoryhas:hana-plugin-creator,officedocuments,quiet-musing,skill-creator."),
    )
    assertEquals(
      "The user sent \"1\".\n\nLet me check status.",
      nbgThinkingTextForAndroidDisplay("The user sent \"1\".\n\nLet me check status."),
    )
    assertEquals(
      "The user is testing stream spaces.",
      nbgThinkingTextForAndroidDisplay("The" + " user" + " is" + " testing" + " stream" + " spaces."),
    )
    assertEquals(
      "hidden chain",
      nbgThinkingTextForAndroidDisplay("<thinking>hidden chain</thinking>"),
    )
    assertEquals(
      "hidden chain",
      nbgThinkingTextForAndroidDisplay("<think>hidden chain<think>"),
    )
    assertEquals(
      "hidden chain",
      nbgThinkingTextForAndroidDisplay("<thinking>hidden chain"),
    )
    assertEquals(
      "用户正在询问我的模型。",
      nbgParseOpenAiChatText("""{"choices":[{"message":{"content":"用户正在询问我的模型。"}}]}"""),
    )
    assertEquals(
      "主要回复",
      nbgCleanAssistantVisibleText("<mood>internal status</mood>\n\n主要回复"),
    )
    assertEquals(
      "主要回复",
      nbgCleanAssistantVisibleText("<mood>internal status</thinking>\n\n主要回复"),
    )
    assertEquals(
      "主要回复",
      nbgDisplayTextForRole("assistant", "<thinking>hidden chain</thinking>\n主要回复"),
    )
    assertEquals(
      "主要回复",
      nbgDisplayTextForRole("assistant", "<thinking>hidden chain<thinking>\n主要回复"),
    )
    assertEquals(
      "主要回复",
      stripNbgInternalText("<thinking>hidden chain<thinking>\n主要回复"),
      )
    }

  @Test
  fun hanakoModelsJsonCanProvideThinkingTranslationUrlApiFallback() {
    val resolved = nbgResolveHanakoModelsJsonUrlApi(
      raw = """
        {
          "providers": {
            "urlapi-5d2693ae-e05": {
              "baseUrl": "https://api.deepseek.com/v1",
              "api": "openai-completions",
              "apiKey": "sk-test",
              "models": [
                {
                  "id": "deepseek-v4-pro",
                  "name": "DeepSeek V4 Pro",
                  "input": ["text"]
                }
              ]
            }
          }
        }
      """.trimIndent(),
      wantedProvider = "urlapi-5d2693ae-e05",
      wantedModelIds = setOf("deepseek-v4-pro"),
    )

    assertTrue(resolved != null)
    assertEquals("https://api.deepseek.com/v1", resolved!!.first.baseUrl)
    assertEquals("sk-test", resolved.first.apiKey)
    assertEquals("deepseek-v4-pro", resolved.second.id)
  }

  @Test
  fun chatPreferenceStorePersistsModelPermissionAndThinkingChoices() {
    val source = File("src/main/java/com/nbg/android/NbgChatPreferenceStore.kt").readText()

    assertTrue(source.contains("data class NbgChatPreferences"))
    assertTrue(source.contains("class NbgChatPreferenceStore(context: Context)"))
    assertTrue(source.contains("getSharedPreferences(PREFS, Context.MODE_PRIVATE)"))
    assertTrue(source.contains("fun saveModel(provider: String, id: String, label: String)"))
    assertTrue(source.contains("fun savePermissionMode(mode: String)"))
    assertTrue(source.contains("fun saveThinkingLevel(level: String)"))
    assertTrue(source.contains("fun saveTheme(themeId: String)"))
    assertTrue(source.contains("fun saveFont(fontId: String)"))
    assertTrue(source.contains("fun saveMultiAgentEnabled(enabled: Boolean)"))
    assertTrue(source.contains("modelProvider = modelProvider.trim()"))
    assertTrue(source.contains("permissionMode: String = NBG_DEFAULT_PERMISSION_MODE"))
    assertTrue(source.contains("permissionMode = root.optString(\"permissionMode\").ifBlank { NBG_DEFAULT_PERMISSION_MODE }"))
    assertTrue(source.contains("permissionMode = nbgNormalizePermissionMode(permissionMode)"))
    assertTrue(source.contains("thinkingLevel = nbgNormalizeThinkingLevel(thinkingLevel) ?: \"auto\""))
    assertTrue(source.contains("themeId = nbgNormalizeThemeId(themeId)"))
    assertTrue(source.contains("fontId = nbgNormalizeFontId(fontId)"))
    assertTrue(source.contains("val multiAgentEnabled: Boolean = true"))
    assertTrue(source.contains("multiAgentEnabled = root.optBoolean(\"multiAgentEnabled\", true)"))
    assertTrue(source.contains(".put(\"themeId\", preferences.themeId)"))
    assertTrue(source.contains(".put(\"fontId\", preferences.fontId)"))
    assertTrue(source.contains(".put(\"multiAgentEnabled\", preferences.multiAgentEnabled)"))
    assertFalse(source.contains("permissionMode: String = \"operate\""))
    assertFalse(source.contains("VALID_THINKING_LEVELS"))
  }

  @Test
  fun appearancePresetsStaySmallAndLocal() {
    assertEquals(listOf("light", "black", "claude"), NBG_THEME_OPTIONS.map { it.id })
    assertTrue(NBG_THEME_OPTIONS.any { it.label == "默认白色" })
    assertTrue(NBG_THEME_OPTIONS.any { it.label == "Claude 暖色" })
    assertTrue(NBG_FONT_OPTIONS.any { it.id == "ios" && it.label == "iOS" })
    assertEquals("light", nbgNormalizeThemeId("web"))
    assertEquals("black", nbgNormalizeThemeId("night"))
    assertEquals("claude", nbgNormalizeThemeId("warm"))
  }

  @Test
  fun mergedModelConfigKeepsOnlyOneCurrentModelAcrossProviders() {
    val server = HanakoAgentModelConfig(
      models = listOf(
        HanakoModelSummary(id = "gpt-5.5", name = "GPT-5.5", provider = "openai", isCurrent = true),
        HanakoModelSummary(id = "deepseek-v4", name = "DeepSeek V4", provider = "deepseek"),
      ),
    )
    val urlApiModels = listOf(
      HanakoModelSummary(
        id = "gpt-5.5",
        name = "GPT-5.5",
        provider = "urlapi-a",
        thinkingLevels = listOf("off", "auto", "xhigh"),
        contextWindow = 400_000L,
        isCurrent = true,
      ),
      HanakoModelSummary(id = "gpt-5.5", name = "GPT-5.5", provider = "urlapi-b", thinkingLevels = listOf("off", "auto")),
    )

    val merged = nbgMergeAgentModelConfig(server, urlApiModels)
    val currentModels = merged?.models.orEmpty().filter { it.isCurrent }

    assertTrue(currentModels.size == 1)
    assertTrue(currentModels.single().provider == "urlapi-a")
    assertTrue(currentModels.single().id == "gpt-5.5")
    assertEquals(400_000L, currentModels.single().contextWindow)
    assertTrue(merged?.models.orEmpty().any { it.provider == "openai" && it.id == "gpt-5.5" && !it.isCurrent })
  }

  @Test
  fun mergedModelConfigKeepsOfficialClaudeDefaultContext() {
    val server = HanakoAgentModelConfig(
      models = listOf(
        HanakoModelSummary(
          id = "claude-opus-4-8",
          name = "Claude Opus 4.8",
          provider = "urlapi-a",
          contextWindow = 1_000_000L,
          isCurrent = true,
        ),
      ),
    )
    val urlApiModels = listOf(
      HanakoModelSummary(
        id = "claude-opus-4-8",
        name = "Claude Opus 4.8",
        provider = "urlapi-a",
        contextWindow = nbgEffectiveModelContextWindow("claude-opus-4-8", 1_000_000L),
        isCurrent = true,
      ),
    )

    val merged = nbgMergeAgentModelConfig(server, urlApiModels)

    assertEquals(200_000L, urlApiModels.single().contextWindow)
    assertEquals(200_000L, merged?.models.orEmpty().single().contextWindow)
  }

  @Test
  fun mergedModelConfigFiltersDeletedUrlApiProviders() {
    val server = HanakoAgentModelConfig(
      models = listOf(
        HanakoModelSummary(id = "gpt-5.2", name = "GPT-5.2", provider = "urlapi-active"),
        HanakoModelSummary(id = "gpt-4o", name = "GPT-4o", provider = "urlapi-deleted"),
        HanakoModelSummary(id = "deepseek-chat", name = "DeepSeek Chat", provider = "deepseek"),
      ),
    )
    val urlApiModels = listOf(
      HanakoModelSummary(id = "gpt-5.2", name = "GPT-5.2", provider = "urlapi-active", isCurrent = true),
    )

    val merged = nbgMergeAgentModelConfig(server, urlApiModels, setOf("urlapi-active"))
    val providers = merged?.models.orEmpty().map { it.provider }.toSet()

    assertTrue("urlapi-active" in providers)
    assertTrue("deepseek" in providers)
    assertFalse("urlapi-deleted" in providers)
  }

  @Test
  fun urlApiProviderFilePruneRemovesOnlyDeletedMobileProviders() {
    val yaml = """
      # header
      _migrated: true
      providers:
        urlapi-active:
          base_url: https://mdkj.lol/v1
          models:
            - gpt-5.5
        urlapi-deleted:
          base_url: https://old.example/v1
          models:
            - old-model
        deepseek:
          base_url: https://api.deepseek.com
      trailing: keep
    """.trimIndent() + "\n"
    val json = """
      {
        "providers": {
          "urlapi-active": { "models": [{ "id": "gpt-5.5" }] },
          "urlapi-deleted": { "models": [{ "id": "old-model" }] },
          "deepseek": { "models": [{ "id": "deepseek-chat" }] }
        }
      }
    """.trimIndent()

    val prunedYaml = nbgPruneDeletedUrlApiProvidersFromYaml(yaml, setOf("urlapi-active"))
    val prunedJson = nbgPruneDeletedUrlApiProvidersFromModelsJson(json, setOf("urlapi-active"))

    assertTrue(prunedYaml.contains("urlapi-active:"))
    assertTrue(prunedYaml.contains("deepseek:"))
    assertTrue(prunedYaml.contains("trailing: keep"))
    assertFalse(prunedYaml.contains("urlapi-deleted:"))
    assertTrue(prunedJson.contains("urlapi-active"))
    assertTrue(prunedJson.contains("deepseek"))
    assertFalse(prunedJson.contains("urlapi-deleted"))
  }

  @Test
  fun localHanakoSessionHealthDetectsRecentAssistantErrors() {
    val unhealthy = """
      {"type":"message","message":{"role":"user","id":"u1","content":[{"type":"text","text":"1"}]}}
      {"type":"message","message":{"role":"assistant","id":"a1","stopReason":"error","errorMessage":"403 Your request was blocked.","content":[]}}
      {"type":"message","message":{"role":"assistant","id":"a2","stopReason":"error","errorMessage":"502 Upstream access forbidden","content":[]}}
      {"type":"message","message":{"role":"assistant","id":"a3","stopReason":"stop","content":[{"type":"text","text":"ok"}]}}
      {"type":"message","message":{"role":"assistant","id":"a4","stopReason":"error","errorMessage":"402 Insufficient Balance","content":[]}}
    """.trimIndent()
    val healthy = """
      {"type":"message","message":{"role":"assistant","id":"a1","stopReason":"error","errorMessage":"403 Your request was blocked.","content":[]}}
      {"type":"message","message":{"role":"assistant","id":"a2","stopReason":"stop","content":[{"type":"text","text":"ok"}]}}
      {"type":"message","message":{"role":"assistant","id":"a3","stopReason":"stop","content":[{"type":"text","text":"ok"}]}}
      {"type":"message","message":{"role":"assistant","id":"a4","stopReason":"stop","content":[{"type":"text","text":"ok"}]}}
    """.trimIndent()

    assertTrue(nbgSessionJsonlLooksUnhealthyForTest(unhealthy))
    assertFalse(nbgSessionJsonlLooksUnhealthyForTest(healthy))
  }

  @Test
  fun preferredUrlApiModelDrivesComposerCurrentModelAndThinkingOptions() {
    val config = HanakoAgentModelConfig(
      models = listOf(
        HanakoModelSummary(id = "gpt-4o", name = "GPT-4o", provider = "openai", thinkingLevels = listOf("off", "auto"), isCurrent = true),
        HanakoModelSummary(id = "gpt-5.5", name = "GPT-5.5", provider = "urlapi-custom", thinkingLevels = listOf("off", "auto", "xhigh")),
      ),
    )

    val current = nbgCurrentModelSummary(
      config,
      modelName = "GPT-5.5",
      preferredModel = NbgChatPreferences(
        modelProvider = "urlapi-custom",
        modelId = "gpt-5.5",
        modelLabel = "GPT-5.5",
        thinkingLevel = "xhigh",
      ),
    )

    assertEquals("urlapi-custom", current?.provider)
    assertEquals("gpt-5.5", current?.id)
    assertEquals("xhigh", nbgCoerceThinkingLevelForModel("xhigh", current))
  }

  @Test
  fun localHanakoAssistantMessagesHideInternalMoodAndThinkingTags() {
    val session = File.createTempFile("hanako-assistant-clean", ".jsonl")
    try {
      session.writeText(
        """
          {"type":"message","message":{"role":"assistant","id":"11","timestamp":"2026-06-23T00:04:00Z","content":[{"type":"text","text":"<mood>checking runtime</thinking>\n\n可以编译，先修环境。"}]}}
          {"type":"message","message":{"role":"assistant","id":"12","timestamp":"2026-06-23T00:04:01Z","content":[{"type":"text","text":"<thinking>hidden</thinking>\n\n继续。"}]}}
          {"type":"message","message":{"role":"assistant","id":"13","timestamp":"2026-06-23T00:04:02Z","content":[{"type":"text","text":"<thinking>hidden<thinking>\n\n保留正文。"}]}}
        """.trimIndent(),
      )

      val assistantMessages = parseLocalSessionJsonl(session, limit = 10).messages
        .filter { it.role == "assistant" }
        .map { it.text }

      assertEquals(listOf("可以编译，先修环境。", "继续。", "保留正文。"), assistantMessages)
    } finally {
      session.delete()
    }
  }

  @Test
  fun localHanakoToolResultsRestoreInlinePreviews() {
    val session = File.createTempFile("hanako-local-tools", ".jsonl")
    try {
      session.writeText(
        """
          {"type":"message","message":{"role":"assistant","id":"1","timestamp":"2026-06-23T00:00:00Z","content":[{"type":"toolCall","id":"write-1","name":"write","input":{"path":"/root/demo.txt","content":"hello\nnew line"}}]}}
          {"type":"message","message":{"role":"toolResult","id":"2","timestamp":"2026-06-23T00:00:01Z","toolCallId":"write-1","toolName":"write","isError":false,"details":{"filePath":"/root/demo.txt","oldContent":"hello\n","newContent":"hello\nnew line\n"},"content":[{"type":"text","text":"Successfully wrote demo.txt"}]}}
          {"type":"message","message":{"role":"assistant","id":"3","timestamp":"2026-06-23T00:00:02Z","content":[{"type":"toolCall","id":"bash-1","name":"bash","input":{"command":"ls /root/demo"}}]}}
          {"type":"message","message":{"role":"toolResult","id":"4","timestamp":"2026-06-23T00:00:03Z","toolCallId":"bash-1","toolName":"bash","isError":false,"details":{},"content":[{"type":"text","text":"file-a\nfile-b\nCommand exited with code 0"}]}}
          {"type":"message","message":{"role":"assistant","id":"7","timestamp":"2026-06-23T00:00:06Z","content":[{"type":"toolCall","id":"write-2","name":"write","input":{"path":"/root/new.txt","content":"first\nsecond\n"}}]}}
          {"type":"message","message":{"role":"toolResult","id":"8","timestamp":"2026-06-23T00:00:07Z","toolCallId":"write-2","toolName":"write","isError":false,"details":{"filePath":"/root/new.txt","oldContent":"","newContent":"first\nsecond\n"},"content":[{"type":"text","text":"Created new.txt"}]}}
          {"type":"message","message":{"role":"assistant","id":"5","timestamp":"2026-06-23T00:00:04Z","content":[{"type":"toolCall","id":"todo-1","name":"todo_write","input":{"todos":[{"content":"检查预览","status":"pending"}]}}]}}
          {"type":"message","message":{"role":"toolResult","id":"6","timestamp":"2026-06-23T00:00:05Z","toolCallId":"todo-1","toolName":"todo_write","isError":false,"details":{},"content":[{"type":"text","text":"共 1 条待办：0 完成，1 待处理"}]}}
        """.trimIndent(),
      )

      val tools = parseLocalSessionJsonl(session, limit = 20).messages
        .mapNotNull { it.toolStatus }
      val write = tools.first { it.toolName == "write" }
      val created = tools.first { it.filePath == "/root/new.txt" }
      val bash = tools.first { it.toolName == "bash" }
      val todo = tools.first { it.toolName == "todo_write" }

      assertTrue(write.filePreview?.previewText?.contains("new line") == true)
      assertTrue(write.fileDiff?.newContent?.contains("new line") == true)
      assertTrue(created.fileDiff?.oldContent == "")
      assertTrue(created.fileDiff?.newContent?.contains("second") == true)
      assertTrue(bash.terminalOutput?.output?.contains("file-b") == true)
      assertTrue(todo.terminalOutput == null)
      assertTrue(todo.detail.contains("共 1 条待办"))
    } finally {
      session.delete()
    }
  }

  @Test
  fun localHanakoToolResultsPreserveWhitespaceOnlyPayloads() {
    val session = File.createTempFile("hanako-whitespace-tools", ".jsonl")
    try {
      session.writeText(
        """
          {"type":"message","message":{"role":"assistant","id":"31","timestamp":"2026-06-23T00:03:00Z","content":[{"type":"toolCall","id":"write-ws","name":"write","input":{"path":"/root/blank.txt","content":" \n  "}}]}}
          {"type":"message","message":{"role":"toolResult","id":"32","timestamp":"2026-06-23T00:03:01Z","toolCallId":"write-ws","toolName":"write","isError":false,"details":{},"content":[{"type":"text","text":"wrote whitespace"}]}}
          {"type":"message","message":{"role":"assistant","id":"33","timestamp":"2026-06-23T00:03:02Z","content":[{"type":"toolCall","id":"term-ws","name":"terminal_read","input":{"id":"term-ws"}}]}}
          {"type":"message","message":{"role":"toolResult","id":"34","timestamp":"2026-06-23T00:03:03Z","toolCallId":"term-ws","toolName":"terminal_read","isError":false,"details":{"id":"term-ws","output":"\n  "},"content":[{"type":"text","text":"fallback ignored"}]}}
        """.trimIndent(),
      )

      val tools = parseLocalSessionJsonl(session, limit = 10).messages
        .mapNotNull { it.toolStatus }
      val file = tools.first { it.toolName == "write" }
      val terminal = tools.first { it.toolName == "terminal_read" }

      assertTrue(file.kind == "file")
      assertTrue(file.filePreview?.previewText == " \n  ")
      assertTrue(terminal.kind == "terminal")
      assertTrue(terminal.terminalOutput?.output == "\n  ")
    } finally {
      session.delete()
    }
  }

  @Test
  fun localHanakoPendingTerminalToolCallsRestoreCommandPreview() {
    val session = File.createTempFile("hanako-pending-terminal", ".jsonl")
    try {
      session.writeText(
        """
          {"type":"message","message":{"role":"assistant","id":"41","timestamp":"2026-06-23T00:04:00Z","content":[{"type":"toolCall","id":"bash-pending","name":"bash","input":{"command":"git clone https://github.com/rikkahub/rikkahub.git","timeout":600}}]}}
        """.trimIndent(),
      )

      val tools = parseLocalSessionJsonl(session, limit = 10).messages
        .mapNotNull { it.toolStatus }
      val terminal = tools.single { it.toolName == "bash" }

      assertTrue(terminal.kind == "terminal")
      assertTrue(terminal.subtitle == "历史记录")
      assertTrue(terminal.terminalOutput?.output?.contains("$ git clone https://github.com/rikkahub/rikkahub.git") == true)
      assertTrue(terminal.detail.isBlank())
    } finally {
      session.delete()
    }
  }

  @Test
  fun localHanakoToolResultsReplaceMatchingToolCallOnly() {
    val session = File.createTempFile("hanako-two-bash-tools", ".jsonl")
    try {
      session.writeText(
        """
          {"type":"message","message":{"role":"assistant","id":"51","timestamp":"2026-06-23T00:05:00Z","content":[{"type":"toolCall","id":"bash-a","name":"bash","input":{"command":"echo first"}},{"type":"toolCall","id":"bash-b","name":"bash","input":{"command":"echo second"}}]}}
          {"type":"message","message":{"role":"toolResult","id":"52","timestamp":"2026-06-23T00:05:01Z","toolCallId":"bash-a","toolName":"bash","isError":false,"details":{},"content":[{"type":"text","text":"first\nCommand exited with code 0"}]}}
        """.trimIndent(),
      )

      val tools = parseLocalSessionJsonl(session, limit = 10).messages
        .mapNotNull { it.toolStatus }
      val first = tools.single { it.key.startsWith("local-result:bash-a:") }
      val pendingSecond = tools.single { it.key.startsWith("local:bash-b:") }

      assertTrue(first.terminalOutput?.output?.contains("first") == true)
      assertTrue(pendingSecond.terminalOutput?.output?.contains("$ echo second") == true)
      assertFalse(tools.any { it.key.startsWith("local:bash-a:") })
    } finally {
      session.delete()
    }
  }

  @Test
  fun toolStatusVisibilityAndKeysAvoidNoisyGenericCards() {
    val invisible = HanakoToolStatus(
      key = "tool_progress:metadata",
      kind = "tool",
      toolName = "metadata",
      title = "工具调用",
    )
    val terminalA = HanakoToolStatus(
      key = "bash",
      kind = "terminal",
      toolName = "bash",
      title = "运行命令",
      terminalOutput = HanakoTerminalOutput(sessionId = "bash:echo first", output = "first"),
    )
    val terminalB = HanakoToolStatus(
      key = "bash",
      kind = "terminal",
      toolName = "bash",
      title = "运行命令",
      terminalOutput = HanakoTerminalOutput(sessionId = "bash:echo second", output = "second"),
    )

    assertFalse(invisible.hasVisibleToolStatus())
    assertTrue(terminalA.hasVisibleToolStatus())
    assertTrue(terminalA.nbgStableToolStatusKey() != terminalB.nbgStableToolStatusKey())
    assertFalse(terminalA.canMergeToolStatus(terminalB))
  }

  @Test
  fun localHanakoNativeToolEventsTriggerWriteDiffAndTerminalPreviews() {
    val session = File.createTempFile("hanako-native-tools", ".jsonl")
    try {
      session.writeText(
        """
          {"type":"message","message":{"role":"assistant","id":"10","timestamp":"2026-06-23T00:01:00Z","content":[{"type":"toolCall","id":"write-prepare-1","name":"write","input":{"path":"/root/app.kt","content":"fun main() {}\n"}}]}}
          {"type":"message","message":{"role":"toolResult","id":"11","timestamp":"2026-06-23T00:01:01Z","toolCallId":"write-prepare-1","toolName":"write","isError":false,"details":{"type":"file_write_prepare","filePath":"/root/app.kt","previewText":"fun main() {}\n","previewTruncated":false},"content":[{"type":"text","text":"准备写入 /root/app.kt"}]}}
          {"type":"message","message":{"role":"assistant","id":"12","timestamp":"2026-06-23T00:01:02Z","content":[{"type":"toolCall","id":"edit-1","name":"edit","input":{"path":"/root/app.kt"}}]}}
          {"type":"message","message":{"role":"toolResult","id":"13","timestamp":"2026-06-23T00:01:03Z","toolCallId":"edit-1","toolName":"edit","isError":false,"details":{"filePath":"/root/app.kt","unifiedDiff":"--- a/root/app.kt\n+++ b/root/app.kt\n@@\n-fun main() {}\n+fun main() { println(\"ok\") }\n"},"content":[{"type":"text","text":"已修改 /root/app.kt"}]}}
          {"type":"message","message":{"role":"assistant","id":"14","timestamp":"2026-06-23T00:01:04Z","content":[{"type":"toolCall","id":"term-1","name":"terminal_read","input":{"id":"term-a"}}]}}
          {"type":"message","message":{"role":"toolResult","id":"15","timestamp":"2026-06-23T00:01:05Z","toolCallId":"term-1","toolName":"terminal_read","isError":false,"details":{"id":"term-a","cwd":"/root","output":"line-1\nline-2\nline-3","cursor":42,"alive":true},"content":[{"type":"text","text":"line-1\nline-2\nline-3"}]}}
          {"type":"message","message":{"role":"assistant","id":"16","timestamp":"2026-06-23T00:01:06Z","content":[{"type":"toolCall","id":"term-2","name":"terminal_wait","input":{"id":"term-b"}}]}}
          {"type":"message","message":{"role":"toolResult","id":"17","timestamp":"2026-06-23T00:01:07Z","toolCallId":"term-2","toolName":"terminal_wait","isError":false,"details":{"id":"term-b","cwd":"/root","outputText":"build ok\nlogs done","cursor":88,"alive":false,"exitCode":0},"content":[{"type":"text","text":"reason=exited"}]}}
        """.trimIndent(),
      )

      val tools = parseLocalSessionJsonl(session, limit = 20).messages
        .mapNotNull { it.toolStatus }
      val writePrepare = tools.first { it.toolName == "write" }
      val edit = tools.first { it.toolName == "edit" }
      val terminal = tools.first { it.toolName == "terminal_read" }
      val terminalOutputText = tools.first { it.toolName == "terminal_wait" && it.terminalOutput != null }

      assertTrue(writePrepare.filePreview?.filePath == "/root/app.kt")
      assertTrue(writePrepare.filePreview?.previewText?.contains("fun main") == true)
      assertTrue(edit.fileDiff?.unifiedDiff?.contains("println") == true)
      assertTrue(edit.kind == "file")
      assertTrue(terminal.kind == "terminal")
      assertTrue(terminal.terminalOutput?.sessionId == "term-a")
      assertTrue(terminal.terminalOutput?.cwd == "/root")
      assertTrue(terminal.terminalOutput?.output?.contains("line-3") == true)
      assertTrue(terminal.terminalOutput?.alive == true)
      assertTrue(terminalOutputText.terminalOutput?.output?.contains("logs done") == true)
      assertTrue(terminalOutputText.terminalOutput?.exitCode == 0)
    } finally {
      session.delete()
    }
  }

  @Test
  fun localHanakoHistoryRestoresOutputTextAndThinkingTextBlocks() {
    val session = File.createTempFile("hanako-output-text", ".jsonl")
    try {
      session.writeText(
        """
          {"type":"message","message":{"role":"assistant","id":"21","timestamp":"2026-06-23T00:02:00Z","content":[{"type":"thinking","text":"先分析"},{"type":"output_text","text":"结果正文"}]}}
          {"type":"message","message":{"role":"user","id":"22","timestamp":"2026-06-23T00:02:01Z","content":[{"type":"input_text","text":"继续"}]}}
        """.trimIndent(),
      )

      val messages = parseLocalSessionJsonl(session, limit = 10).messages

      assertTrue(messages.any { it.role == "thinking" && it.text == "先分析" })
      assertTrue(messages.any { it.role == "assistant" && it.text == "结果正文" })
      assertTrue(messages.any { it.role == "user" && it.text == "继续" })
    } finally {
      session.delete()
    }
  }

  @Test
  fun localSessionPathResolutionPrefersExactRootRelativePathBeforeBasenameFallback() {
    val ubuntuRoot = createTempDirectory(prefix = "nbg-session-root").toFile()
    try {
      val firstDir = File(ubuntuRoot, ".hanakopro/agents/hanako/sessions").apply { mkdirs() }
      val secondDir = File(ubuntuRoot, ".hanakopro-android/agents/hanako/sessions").apply { mkdirs() }
      val first = File(firstDir, "same.jsonl").apply { writeText("first") }
      val second = File(secondDir, "same.jsonl").apply { writeText("second") }

      val exact = nbgLocalFileForSessionPath(
        ubuntuRootHomeDir = ubuntuRoot,
        sessionPath = "/root/.hanakopro-android/agents/hanako/sessions/same.jsonl",
        localSessionDirs = listOf(firstDir, secondDir),
      )
      val fallback = nbgLocalFileForSessionPath(
        ubuntuRootHomeDir = ubuntuRoot,
        sessionPath = "/remote/same.jsonl",
        localSessionDirs = listOf(firstDir, secondDir),
      )

      assertTrue(exact == second.canonicalFile)
      assertTrue(fallback == first)
    } finally {
      ubuntuRoot.deleteRecursively()
    }
  }

  @Test
  fun hanakoWorkspaceCleanupDoesNotFollowSymlinkTargets() {
    val base = createTempDirectory(prefix = "nbg-hanako-cleanup").toFile()
    val outside = File(base, "outside").apply { mkdirs() }
    val secret = File(outside, "secret.txt").apply { writeText("keep") }
    val link = File(base, "OH-WorkSpace")
    Files.createSymbolicLink(link.toPath(), outside.toPath())

    assertTrue(link.exists())
    assertTrue(nbgDeletePathWithoutFollowingSymlinkForTest(link))

    assertFalse(link.exists())
    assertTrue(outside.isDirectory)
    assertEquals("keep", secret.readText())
  }

  @Test
  fun mainActivityLetsSystemResizeWindowForIme() {
    val manifest = File("src/main/AndroidManifest.xml").readText()

    assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize|stateHidden\""))
  }

  @Test
  fun manifestAllowsCleartextOnlyForLocalLoopback() {
    val manifest = File("src/main/AndroidManifest.xml").readText()
    val networkConfig = File("src/main/res/xml/network_security_config.xml").readText()

    assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
    assertTrue(networkConfig.contains("<base-config cleartextTrafficPermitted=\"false\""))
    assertTrue(networkConfig.contains("<domain-config cleartextTrafficPermitted=\"true\""))
    assertTrue(networkConfig.contains("<domain includeSubdomains=\"false\">127.0.0.1</domain>"))
    assertTrue(networkConfig.contains("<domain includeSubdomains=\"false\">localhost</domain>"))
    assertFalse(networkConfig.contains("<base-config cleartextTrafficPermitted=\"true\""))
  }

  @Test
  fun manifestDisablesAppDataBackupForLocalSecrets() {
    val manifest = File("src/main/AndroidManifest.xml").readText()

    assertTrue(manifest.contains("android:allowBackup=\"false\""))
    assertTrue(manifest.contains("android:fullBackupContent=\"false\""))
    assertFalse(manifest.contains("android:allowBackup=\"true\""))
  }

  @Test
  fun appStartsInAgentChatShellAndKeepsTerminalAndServices() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val agentUi = readAgentUiSource()
    val bridge = readHanakoBridgeSource()
    val manifest = File("src/main/AndroidManifest.xml").readText()
    val hanakoService = File("src/main/java/com/nbg/android/HanakoForegroundService.kt").readText()
    val fileShare = File("src/main/java/com/nbg/android/NbgFileShareModel.kt").readText()
    val paths = File("src/main/java/com/nbg/android/NbgAndroidPaths.kt").readText()

    assertFalse(mainActivity.contains("NbgFileShareServiceController.start(this)"))
    assertTrue(mainActivity.contains("HanakoForegroundServiceController.start(this)"))
    assertTrue(mainActivity.contains("HanakoBackgroundWarmup.start(this)"))
    assertTrue(manifest.contains("android:name=\".HanakoForegroundService\""))
    assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
    assertTrue(hanakoService.contains("ContextCompat.startForegroundService"))
    assertTrue(hanakoService.contains("ServiceCompat.startForeground"))
    assertTrue(hanakoService.contains("FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
    assertTrue(hanakoService.contains("HanakoBackgroundWarmup.start(applicationContext)"))
    assertTrue(hanakoService.contains("START_STICKY"))
    assertTrue(mainActivity.contains("NbgAndroidApp("))
    assertTrue(mainActivity.contains("NbgAndroidShell"))
    assertTrue(mainActivity.contains("TerminalScreen("))
    assertTrue(mainActivity.contains("private fun TerminalTabsBar("))
    assertTrue(agentUi.contains("val shellState = remember { NbgAgentShellState(initialPage) }"))
    assertTrue(agentUi.contains("val page = shellState.page"))
    assertFalse(agentUi.contains("var page by remember"))
    assertFalse(agentUi.contains("var openSearchPanelRequest by remember"))
    assertFalse(agentUi.contains("searchOpenRequest: Int"))
    assertFalse(agentUi.contains("LaunchedEffect(searchOpenRequest)"))
    assertTrue(agentUi.contains("shellState.showPage(NbgShellPage.Terminal)"))
    assertTrue(agentUi.contains("shellState.showPage(NbgShellPage.Agents)"))
    assertTrue(agentUi.contains("shellState.showPage(NbgShellPage.Mcp)"))
    assertTrue(agentUi.contains("shellState.showPage(NbgShellPage.Skills)"))
    assertTrue(agentUi.contains("shellState.showPage(NbgShellPage.Pets)"))
    assertTrue(agentUi.contains("shellState.showPage(NbgShellPage.Appearance)"))
    assertTrue(agentUi.contains("shellState.showPage(NbgShellPage.UrlApi)"))
    assertTrue(agentUi.contains("NbgShellPage.Terminal -> terminalContent { shellState.showChat() }"))
    assertTrue(agentUi.contains("\"pets\", \"petdex\" -> NbgShellPage.Pets"))
    assertTrue(agentUi.contains("\"appearance\", \"theme\", \"themes\" -> NbgShellPage.Appearance"))
    assertFalse(agentUi.contains("NbgShellPage.Terminal -> NbgShellSubPage("))
    assertFalse(mainActivity.contains("nbg.previewTeam"))
    assertFalse(mainActivity.contains("previewTeam"))
    assertTrue(mainActivity.contains("val palette = nbgThemePalette(appearance.themeId)"))
    assertFalse(mainActivity.contains("webThemeColor = appearance.webThemeColor"))
    assertFalse(mainActivity.contains("onlineThemePalette = appearance.onlineTheme"))
    assertTrue(mainActivity.contains("window?.statusBarColor = palette.background.toArgb()"))
    assertTrue(mainActivity.contains("window?.navigationBarColor = palette.background.toArgb()"))
    assertTrue(mainActivity.contains("colorScheme = nbgMaterialColorScheme(palette)"))
    assertTrue(mainActivity.contains("typography = nbgTypography(font)"))
    assertTrue(mainActivity.contains("onAppearanceChanged = { next -> appearance = next }"))
    assertFalse(mainActivity.contains("colorScheme = NbgMaterialColorScheme"))
    assertFalse(mainActivity.contains("private val NbgMaterialColorScheme"))
    assertFalse(mainActivity.contains("Base64.decode"))
    assertFalse(mainActivity.contains("requestHanakoActionIfNeeded"))
    assertTrue(mainActivity.contains("override fun onNewIntent"))
    assertTrue(mainActivity.contains("nbgRecordGatewayShareIntent"))
    listOf(
      "nbg.openFirstSession",
      "nbg.testPrompt",
      "nbg.testSlash",
      "nbg.openSearch",
      "nbg.searchQuery",
      "nbg.renameLatestSessionTitle",
      "nbg.deleteLatestSession",
      "nbg.showAgentModel",
      "nbg.checkCurrentModelHealth",
      "nbg.showProviders",
      "nbg.showPermissionMode",
      "nbg.showThinkingLevel",
      "nbg.showSlashCommands",
      "nbg.showWorkspaceFiles",
      "nbg.showFileShare",
    ).forEach { assertFalse(mainActivity.contains(it)) }

    assertTrue(agentUi.contains("ModalNavigationDrawer("))
    assertTrue(agentUi.contains("NbgConversationDrawer("))
    assertTrue(agentUi.contains("NbgAgentChatScreen("))
    assertTrue(agentUi.contains("containerColor = NbgAgentColors.Background"))
    assertTrue(agentUi.contains(".background(NbgAgentColors.Background)\n          .imePadding()"))
    assertTrue(agentUi.contains("NbgChatComposer("))
    assertTrue(agentUi.contains("TextFieldValue(draft, selection = TextRange(draft.length))"))
    assertTrue(agentUi.contains("contentDescription = \"消息输入\""))
    assertTrue(agentUi.contains("sendEnabled = !sendBlocked && (streaming || inputText.isNotBlank())"))
    assertTrue(agentUi.contains("onSend(inputText)"))
    assertTrue(agentUi.contains("private enum class NbgSendButtonVisualState"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Disabled"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Send"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Stop"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Steer"))
    assertTrue(agentUi.contains("val backgroundColor by animateColorAsState"))
    assertTrue(agentUi.contains("val iconTint by animateColorAsState"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Send -> NbgAgentColors.SendReady"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Steer -> NbgAgentColors.SendSteer"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Stop -> NbgAgentColors.SendStop"))
    assertFalse(agentUi.contains("NbgSendButtonVisualState.Send -> NbgAgentColors.Primary"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Disabled -> NbgAgentColors.TextMuted"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Send -> NbgAgentColors.SendReadyIcon"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Steer -> NbgAgentColors.SendSteerIcon"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Send -> NbgAgentColors.SendReadyBorder"))
    assertTrue(agentUi.contains("NbgSendButtonVisualState.Disabled -> NbgAgentColors.SendDisabledBorder"))
    assertTrue(agentUi.contains("val borderColor by animateColorAsState"))
    assertTrue(agentUi.contains("border = BorderStroke(1.dp, borderColor)"))
    assertTrue(agentUi.contains("val iconScale by animateFloatAsState"))
    assertTrue(agentUi.contains("val pressAlpha by animateFloatAsState"))
    assertTrue(agentUi.contains("!enabled -> 0.46f"))
    assertTrue(agentUi.contains("isPressed -> 0.72f"))
    assertTrue(agentUi.contains("alpha = pressAlpha"))
    assertTrue(agentUi.contains("label = \"nbg press alpha\""))
    assertTrue(agentUi.contains("AnimatedContent("))
    assertTrue(agentUi.contains("label = \"send button icon\""))
    assertTrue(agentUi.contains("fadeIn(animationSpec = tween(nbgMotionDuration(110)"))
    assertTrue(agentUi.contains("fadeOut(animationSpec = tween(nbgMotionDuration(90)"))
    assertTrue(agentUi.contains("NbgShellPage.Terminal"))
    assertTrue(agentUi.contains("NbgShellPage.Appearance"))
    assertTrue(agentUi.contains("NbgAppearanceScreen("))
    assertTrue(agentUi.contains("onOpenAppearance = {"))
    assertTrue(agentUi.contains("fun savePreferredTheme(themeId: String)"))
    assertTrue(agentUi.contains("fun savePreferredFont(fontId: String)"))
    assertTrue(agentUi.contains("NBG_THEME_OPTIONS"))
    assertTrue(agentUi.contains("NBG_FONT_OPTIONS"))
    assertTrue(agentUi.contains("NbgThemeOption(\"light\", \"默认白色\""))
    assertTrue(agentUi.contains("NbgThemeOption(\"black\", \"黑色\""))
    assertTrue(agentUi.contains("NbgThemeOption(\"claude\", \"Claude 暖色\""))
    assertTrue(agentUi.contains("NbgFontOption(\"ios\", \"iOS\""))
    assertFalse(agentUi.contains("NbgWebsiteAppearanceCard("))
    assertFalse(agentUi.contains("NbgWebsiteAppearanceClient()"))
    assertFalse(agentUi.contains("NbgOnlineThemeClient()"))
    assertFalse(agentUi.contains("onImportWebsiteAppearance = ::saveWebsiteAppearance"))
    assertFalse(agentUi.contains("onImportOnlineTheme = ::saveOnlineTheme"))
    assertFalse(agentUi.contains("fun saveWebsiteAppearance(result: NbgWebsiteAppearanceResult)"))
    assertFalse(agentUi.contains("chatPreferenceStore.saveWebsiteAppearance("))
    assertFalse(agentUi.contains("NbgThemeOption(\"web\", \"网址\""))
    assertTrue(agentUi.contains("private var nbgActivePalette by mutableStateOf(NbgLightPalette)"))
    assertTrue(agentUi.contains("internal fun nbgThemePalette(id: String): NbgAgentColorPalette"))
    assertFalse(agentUi.contains("onlineThemePalette: NbgOnlineThemePalette? = null"))
    assertFalse(agentUi.contains("\"web\" -> nbgWebsitePalette(webThemeColor)"))
    assertFalse(agentUi.contains("\"online\" -> onlineThemePalette?.let(::nbgOnlineThemePalette) ?: NbgGraphitePalette"))
    assertTrue(agentUi.contains("NbgThemeChoiceRow("))
    assertTrue(agentUi.contains("NbgFontChoiceRow("))
    assertTrue(agentUi.contains("title = \"外观\""))
    assertTrue(agentUi.contains("fontFamily = nbgCurrentFontFamily()"))
    assertFalse(agentUi.contains("openSearchPanelRequest"))
    assertFalse(agentUi.contains("searchOpenRequest"))
    assertFalse(agentUi.contains("openFirstSessionRequest"))
    assertFalse(agentUi.contains("promptSendRequest"))
    assertFalse(agentUi.contains("slashSendRequest"))
    assertFalse(agentUi.contains("renameLatestSessionRequest"))
    assertFalse(agentUi.contains("deleteLatestSessionRequest"))
    assertFalse(agentUi.contains("archiveLatestSessionRequest"))
    assertFalse(agentUi.contains("openArchivedDrawerRequest"))
    assertFalse(agentUi.contains("restoreLatestArchivedSessionRequest"))
    assertFalse(agentUi.contains("openAgentModelRequest"))
    assertFalse(agentUi.contains("openProvidersRequest"))
    assertFalse(agentUi.contains("openPermissionModeRequest"))
    assertFalse(agentUi.contains("openThinkingLevelRequest"))
    assertFalse(agentUi.contains("openSlashCommandsRequest"))
    assertFalse(agentUi.contains("openWorkspaceFilesRequest"))
    assertFalse(agentUi.contains("openFileShareRequest"))
    assertFalse(agentUi.contains("thinkingPreviewRequest"))
    assertFalse(agentUi.contains("previewWorkspaceFileRequest"))
    assertFalse(agentUi.contains("nbg-verify.txt"))
    assertTrue(agentUi.contains("val fileShareUiState = remember { NbgAgentFileShareState() }"))
    assertTrue(agentUi.contains("fileShareUiState.open(NbgFileShareServerRegistry.start(context, NbgFileShareAccessMode.ReadOnly))"))
    assertTrue(agentUi.contains("fileShareState = fileShareUiState.serverState"))
    assertTrue(agentUi.contains("if (fileShareUiState.isOpen)"))
    assertTrue(agentUi.contains("val fileShareState = fileShareUiState.serverState ?: NbgFileShareServerRegistry.snapshot(context)"))
    assertTrue(agentUi.contains("state = fileShareState"))
    assertTrue(agentUi.contains("NbgFileShareServerRegistry.start(context, fileShareState.accessMode)"))
    assertTrue(agentUi.contains("NbgFileShareServerRegistry.start(context, NbgFileShareAccessMode.ReadOnly)"))
    assertTrue(agentUi.contains("NbgFileShareServerRegistry.start(context, NbgFileShareAccessMode.ReadWrite)"))
    assertTrue(agentUi.contains("onDismiss = { fileShareUiState.dismiss() }"))
    assertFalse(agentUi.contains("var fileShareOpen by remember"))
    assertFalse(agentUi.contains("var fileShareState by remember"))
    assertTrue(agentUi.contains("NbgFileShareDialog("))
    assertTrue(agentUi.contains("onStartReadOnly = {"))
    assertTrue(agentUi.contains("onStartReadWrite = {"))
    assertTrue(agentUi.contains("FTP 共享"))
    assertTrue(agentUi.contains("val address = state.localUrl.substringBefore(':')"))
    assertTrue(agentUi.contains("NbgFileShareInfoRow(\"模式\", state.accessMode.label)"))
    assertTrue(agentUi.contains("NbgFileShareInfoRow(\"地址\", address"))
    assertTrue(agentUi.contains("NbgFileShareInfoRow(\"端口\", port"))
    assertTrue(agentUi.contains("NbgFileShareInfoRow(\"账号\", state.username"))
    assertTrue(agentUi.contains("LocalClipboardManager.current"))
    assertTrue(agentUi.contains("clipboard.setText(AnnotatedString(value))"))
    assertTrue(agentUi.contains("var copiedLabel by remember"))
    assertTrue(agentUi.contains("delay(1_200)"))
    assertTrue(agentUi.contains("text = \"已复制\""))
    assertTrue(agentUi.contains("contentDescription = \"复制\$label\""))
    assertFalse(agentUi.contains("ADB 转发"))
    assertFalse(agentUi.contains("Ubuntu 根目录"))
    assertFalse(agentUi.contains("宿主目录"))
    assertTrue(agentUi.contains("HanakoChatController("))
    assertTrue(agentUi.contains("fun appendUserMessage(text: String): Long?"))
    assertTrue(agentUi.contains("val userMessageDedupState = remember { NbgAgentUserMessageDedupState() }"))
    assertTrue(agentUi.contains("if (!userMessageDedupState.shouldAccept(prompt, now)) return null"))
    assertTrue(agentUi.contains("const val DUPLICATE_WINDOW_MS = 1_500L"))
    val agentUiMain = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    val clearConversationBlock = agentUiMain.substringAfter("fun clearConversationUi() {").substringBefore("fun nextLocalMessageId")
    val replaceHistoryBlock = agentUiMain.substringAfter("fun replaceHistory(history: List<HanakoHistoryMessage>) {").substringBefore("val restoredMessages = buildList")
    assertTrue(clearConversationBlock.contains("userMessageDedupState.clear()"))
    assertTrue(replaceHistoryBlock.contains("userMessageDedupState.clear()"))
    assertFalse(agentUi.contains("var lastUserMessageUiKey by remember"))
    assertFalse(agentUi.contains("lastUserMessageUiKey = \"\" to 0L"))
    assertFalse(agentUiMain.contains("now - lastAt in 0..1_500L"))
    assertFalse(agentUi.contains("messages.lastOrNull { it.role == NbgAgentRole.User }?.text?.trim()"))
    assertTrue(agentUi.contains("is HanakoChatEvent.UserMessage -> {\n          streamingControllerState.markStreaming()\n          appendUserMessage(event.text)"))
    assertTrue(agentUi.contains("nbgSelectedUrlApi(savedApis, selectedUrlApiModel)"))
    assertTrue(agentUi.contains("hanako.setDefaultUrlApi(selected?.first, selected?.second)"))
    assertFalse(agentUi.contains("nbgBuildMultiAgentPrompt(prompt)"))
    assertFalse(agentUi.contains("val modelPrompt = if (chatPreferences.multiAgentEnabled && !prompt.trimStart().startsWith(\"/\"))"))
    assertTrue(agentUi.contains("hanako.sendPromptWithUrlApi(prompt, selected.first, selected.second, displayText = prompt)"))
    assertTrue(agentUi.contains("hanako.createSessionWithUrlApi(selected.first, selected.second)"))
    assertTrue(agentUi.contains("hanako.sendPrompt(prompt, displayText = prompt)"))
    assertFalse(agentUi.contains("hanako.sendMultiAgentPromptWithUrlApi(modelPrompt"))
    assertFalse(agentUi.contains("hanako.sendMultiAgentPrompt(modelPrompt"))
    assertFalse(agentUi.contains("hanako.createTeamTaskWithUrlApi("))
    assertFalse(agentUi.contains("hanako.createTeamTask(prompt)"))
    assertTrue(bridge.contains("fun setDefaultUrlApi(entry: NbgStoredApi?, model: NbgApiModel?)"))
    assertTrue(bridge.contains("configureDefaultUrlApiModelIfNeeded(info)"))
    assertTrue(bridge.contains("fun sendPromptWithUrlApi(text: String, entry: NbgStoredApi, model: NbgApiModel, displayText: String = text)"))
    assertTrue(bridge.contains("sendPromptInternal(prompt, entry to model, displayText = displayText)"))
    assertTrue(bridge.contains("urlApiSelection: Pair<NbgStoredApi, NbgApiModel>? = null"))
    val sendPromptInternalBlock = bridge.substringAfter("private fun sendPromptInternal(")
      .substringBefore("private fun sendSlash(")
    assertTrue(sendPromptInternalBlock.contains("urlApiSelection?.let { (entry, model) ->"))
    assertTrue(sendPromptInternalBlock.contains("val providerId = nbgUrlApiProviderId(entry.id)"))
    assertTrue(sendPromptInternalBlock.contains("http.configureUrlApiModel(activeInfo, entry, model)"))
    assertTrue(sendPromptInternalBlock.contains("http.switchSessionModel(activeInfo, sessionPath, model.id, providerId)"))
    assertTrue(sendPromptInternalBlock.indexOf("http.switchSessionModel(activeInfo, sessionPath, model.id, providerId)") < sendPromptInternalBlock.indexOf("sendPromptOverCurrentWebSocket("))
    assertTrue(bridge.contains("private fun finishLocalClientTurn()"))
    assertTrue(bridge.contains("private fun finishLocalSendFailure()"))
    assertTrue(bridge.contains("_state.update { it.copy(streaming = false) }\n    resetActiveTurnState()\n    finishLearningTurnAndEmitEnded()"))
    assertTrue(bridge.contains("private fun finishLocalSendFailure() {\n    clearLearningTurn()\n    finishLocalClientTurn()\n  }"))
    assertTrue(sendPromptInternalBlock.contains("finishLocalSendFailure()\n        return@launch"))
    assertTrue(sendPromptInternalBlock.contains("finishLocalSendFailure()\n      }.getOrNull() ?: return@launch"))
    assertTrue(sendPromptInternalBlock.contains("finishLocalSendFailure()\n          return@launch"))
    assertTrue(sendPromptInternalBlock.contains("if (!sent)"))
    assertTrue(sendPromptInternalBlock.contains("finishLocalSendFailure()\n      }"))
    assertTrue(bridge.contains("fun createSessionWithUrlApi(entry: NbgStoredApi, model: NbgApiModel)"))
    assertTrue(bridge.contains("http.configureUrlApiModel(activeInfo, entry, model)"))
    val createUrlSessionBlock = bridge.substringAfter("fun createSessionWithUrlApi(entry: NbgStoredApi, model: NbgApiModel)")
      .substringBefore("fun renameSession(")
    assertTrue(createUrlSessionBlock.contains("val providerId = nbgUrlApiProviderId(entry.id)"))
    assertTrue(createUrlSessionBlock.contains("withLocalHanakoRetry(info)"))
    assertTrue(createUrlSessionBlock.contains("val focus = http.createSession(activeInfo, _state.value.sessionPath)"))
    assertTrue(createUrlSessionBlock.contains("val switched = http.switchSessionModel(activeInfo, focus.path, model.id, providerId)"))
    assertTrue(bridge.contains("http.switchSessionModel(activeInfo, sessionPath, model.id, providerId)"))

    assertTrue(fileShare.contains("InetAddress.getByName(\"127.0.0.1\")"))
    assertTrue(fileShare.contains("private const val DEFAULT_PORT = 43211"))
    assertTrue(fileShare.contains("preferredPort = DEFAULT_PORT"))
    assertTrue(fileShare.contains("port = current?.port ?: DEFAULT_PORT"))
    assertTrue(fileShare.contains("\"127.0.0.1:\${current?.port ?: DEFAULT_PORT}\""))
    assertFalse(fileShare.contains("ftp://127.0.0.1"))
    assertFalse(fileShare.contains("0.0.0.0"))
    assertTrue(paths.contains("const val NBG_UBUNTU_ROOT_HOME = \"/root/\""))
    assertTrue(paths.contains("const val NBG_UBUNTU_OUTPUTS_DIR = \"/root/outputs\""))
    assertTrue(fileShare.contains("private val ubuntuRoot = NBG_UBUNTU_ROOT_HOME.trimEnd('/')"))
    assertTrue(fileShare.contains("private fun ubuntuPathFor(virtualPath: String): String"))
    assertFalse(fileShare.contains("\$NBG_UBUNTU_ROOT_HOME/"))
    assertFalse(fileShare.contains("lanUrl"))

    listOf(
      "nbg.showHanakoTerminals",
      "nbg.showCheckpoints",
      "nbg.showActivities",
      "nbg.showDevLogs",
      "nbg.showDiary",
      "nbg.showMemories",
      "nbg.showSkills",
      "nbg.showPlugins",
      "nbg.showBridge",
      "nbg.showEnvironment",
      "nbg.showPrompts",
    ).forEach { assertFalse(mainActivity.contains(it)) }

    listOf(
      "openHanakoTerminalsRequest",
      "openCheckpointsRequest",
      "openActivitiesRequest",
      "openDevLogsRequest",
      "openDiaryRequest",
      "openMemoriesRequest",
      "openSkillsRequest",
      "openPluginsRequest",
      "openBridgeRequest",
      "openEnvironmentRequest",
      "openPromptsRequest",
    ).forEach { assertFalse(agentUi.contains(it)) }

    assertFalse(mainActivity.contains("NbgAgentServiceController"))
    assertFalse(mainActivity.contains("NbgRikkaChatScreen"))
    assertFalse(mainActivity.contains("NbgWorkbenchScreen"))
    assertFalse(mainActivity.contains("NbgMainMode.Workbench"))
    assertFalse(mainActivity.contains("WebView("))
    assertFalse(mainActivity.contains("addJavascriptInterface"))
    assertFalse(mainActivity.contains("WORKBENCH_URL"))
    assertFalse(mainActivity.contains("workbench-react"))
    assertFalse(mainActivity.contains("file:///android_asset/workbench"))
    assertFalse(mainActivity.contains("EXTRA_DEBUG_SEND_MESSAGE"))
    assertFalse(mainActivity.contains("window.__nbgTest"))
  }

  @Test
  fun fileSharePasswordUsesEncryptedSecretStore() {
    val fileShare = File("src/main/java/com/nbg/android/NbgFileShareModel.kt").readText()

    assertTrue(fileShare.contains("NbgEncryptedPreferenceSecretStore("))
    assertTrue(fileShare.contains("secrets.loadSecret(PASSWORD_KEY)"))
    assertTrue(fileShare.contains("secrets.saveSecret(PASSWORD_KEY, legacyPassword)"))
    assertTrue(fileShare.contains("secrets.saveSecret(PASSWORD_KEY, password)"))
    assertTrue(fileShare.contains("prefs.edit().remove(PASSWORD_KEY).apply()"))
    assertFalse(fileShare.contains("putString(PASSWORD_KEY, password)"))
  }

  @Test
  fun appLocalServicesStayLoopbackOnlyWithTokenOrPasswordBoundary() {
    val bridge = File("src/main/java/com/nbg/android/HanakoBridge.kt").readText()
    val launcher = File("src/main/java/com/nbg/android/HanakoServerLauncher.kt").readText()
    val fileShare = File("src/main/java/com/nbg/android/NbgFileShareModel.kt").readText()
    val fileShareUi = File("src/main/java/com/nbg/android/NbgAgentFileShareUi.kt").readText()
    val diagnostics = File("src/main/java/com/nbg/android/NbgDiagnosticsExport.kt").readText()
    val ftpServer = File("src/main/java/com/nbg/android/NbgFtpFileServer.kt").readText()
    val mcpServer = File("../android-mcp-server/src/main/java/com/nbg/android/mcpserver/McpServerService.kt").readText()
    val mcpContract = File("../android-mcp-server/src/main/java/com/nbg/android/mcpserver/AndroidMcpServerContract.kt").readText()
    val mcpActivity = File("../android-mcp-server/src/main/java/com/nbg/android/mcpserver/MainActivity.kt").readText()
    val localServiceContract = File("../docs/contracts/local-service-boundary-contract.md").readText()
    val networkConfig = File("src/main/res/xml/network_security_config.xml").readText()

    assertTrue(bridge.contains("apiBase: String = \"http://127.0.0.1:${'$'}port\""))
    assertTrue(bridge.contains("wsUrl: String = \"ws://127.0.0.1:${'$'}port/ws?token=${'$'}token\""))
    assertTrue(launcher.contains("host:\"127.0.0.1\""))
    assertTrue(launcher.contains("headers:{Authorization:\"Bearer \"+t}"))
    assertTrue(fileShare.contains("bindAddress = InetAddress.getByName(\"127.0.0.1\")"))
    assertTrue(fileShare.contains("localUrl = \"127.0.0.1:${'$'}{current?.port ?: DEFAULT_PORT}\""))
    assertTrue(fileShare.contains("NbgFileShareAccessMode.ReadOnly"))
    assertTrue(fileShare.contains("server?.takeIf { it.isRunning && it.accessMode == accessMode }"))
    assertTrue(fileShare.contains("accessMode = accessMode"))
    assertTrue(fileShareUi.contains("NbgFileShareInfoRow(\"模式\", state.accessMode.label)"))
    assertTrue(diagnostics.contains(".put(\"accessMode\", this?.accessMode?.wireValue.orEmpty())"))
    assertTrue(ftpServer.contains("bind(InetSocketAddress(bindAddress, port))"))
    assertTrue(ftpServer.contains("bind(InetSocketAddress(bindAddress, 0))"))
    assertTrue(ftpServer.contains("authenticated = username == credentials.username && argument == credentials.password"))
    assertTrue(ftpServer.contains("val accessMode: NbgFileShareAccessMode = NbgFileShareAccessMode.ReadWrite"))
    assertTrue(ftpServer.contains("private val passiveSockets: MutableSet<ServerSocket> = ConcurrentHashMap.newKeySet()"))
    assertTrue(ftpServer.contains("passiveSockets.forEach { passive ->"))
    assertTrue(ftpServer.contains("passiveSockets.add(passive)"))
    assertTrue(ftpServer.contains("passiveSockets.remove(passive)"))
    assertTrue(ftpServer.contains("if (running) {"))
    assertTrue(ftpServer.contains("ensureWriteAllowed()"))
    assertTrue(ftpServer.contains("if (accessMode.allowsWrites) return"))
    assertTrue(ftpServer.contains("FTP server is read-only"))
    assertTrue(mcpServer.contains("ServerSocket(port, 50, InetAddress.getByName(NBG_ANDROID_MCP_LOOPBACK_HOST))"))
    assertTrue(mcpServer.contains("ensureBearerToken(this)"))
    assertTrue(mcpServer.contains("bearerTokenProvider = { ensureBearerToken(this) }"))
    assertTrue(mcpServer.contains("onListening = { listeningPort ->"))
    assertTrue(mcpServer.contains("markServerListening(listeningPort, bearerTokenPresent = bearerToken.isNotBlank())"))
    assertTrue(mcpServer.contains("markServerStopped(error.message)"))
    assertTrue(mcpServer.contains("markServerStopped(clearError = false)"))
    assertTrue(mcpServer.contains("if (stopped) break"))
    assertTrue(mcpServer.contains("throw error"))
    assertTrue(mcpServer.contains("if (!nbgAndroidMcpRequestAuthorized(headers, bearerTokenProvider()))"))
    assertTrue(mcpServer.contains("writeUnauthorized(client)"))
    assertTrue(mcpServer.contains("KEY_BEARER_TOKEN_PRESENT"))
    assertTrue(mcpServer.contains("KEY_LAST_ERROR"))
    assertTrue(mcpContract.contains("NBG_ANDROID_MCP_AUTH_SCHEME"))
    assertTrue(mcpContract.contains("NBG_ANDROID_MCP_BEARER_TOKEN_BYTES"))
    assertTrue(mcpContract.contains("nbgAndroidMcpRequestAuthorized"))
    assertTrue(mcpActivity.contains("Copy Bearer Token"))
    assertTrue(mcpActivity.contains("Regenerate Bearer Token"))
    assertTrue(mcpActivity.contains("ClipDescription.EXTRA_IS_SENSITIVE"))
    assertTrue(mcpActivity.contains("CLIPBOARD_TOKEN_TTL_MS"))
    assertTrue(mcpActivity.contains("clearPrimaryClip()"))
    assertTrue(mcpActivity.contains("ClipData.newPlainText(\"NBG MCP\", token)"))
    assertFalse(mcpActivity.contains("ClipData.newPlainText(\"NBG MCP Bearer Token\""))
    assertTrue(localServiceContract.contains("Public beta FTP starts in explicit `ReadOnly` mode by default."))
    assertTrue(localServiceContract.contains("Users must explicitly switch to `ReadWrite` mode"))
    assertTrue(localServiceContract.contains("`ReadOnly` mode must reject `STOR`, `APPE`, `DELE`, `RMD`/`XRMD`, `MKD`/`XMKD`, `RNFR`, and `RNTO`"))
    assertTrue(localServiceContract.contains("Switching FTP access mode or closing the FTP server must cancel pending passive transfers"))
    assertTrue(localServiceContract.contains("Android MCP beta requires a local bearer token"))
    assertTrue(localServiceContract.contains("Diagnostics may report whether the MCP bearer token exists, but must not export the token value."))
    assertFalse(localServiceContract.contains("Whether FTP should add explicit read-only mode for public beta."))
    assertFalse(localServiceContract.contains("Whether Android MCP beta should require a local bearer token even while tools remain read-only."))
    assertTrue(networkConfig.contains("<base-config cleartextTrafficPermitted=\"false\""))
    assertTrue(networkConfig.contains("<domain includeSubdomains=\"false\">127.0.0.1</domain>"))
    assertTrue(networkConfig.contains("<domain includeSubdomains=\"false\">localhost</domain>"))
    assertFalse(networkConfig.contains("<base-config cleartextTrafficPermitted=\"true\""))
    assertFalse(fileShare.contains("InetAddress.getByName(\"0.0.0.0\")"))
    assertFalse(ftpServer.contains("ServerSocket(port)"))
  }

  @Test
  fun multiAgentTeamStatusModelsExposeActiveTaskState() {
    val idleAgent = HanakoTeamAgentStatus(
      taskId = "team-1",
      agentId = "researcher",
      role = "Researcher",
      title = "调研员",
      status = "idle",
    )
    val codingAgent = idleAgent.copy(agentId = "coder", role = "Coder", title = "工程师", status = "coding")
    val task = HanakoTeamTaskStatus(
      taskId = "team-1",
      status = "running",
      agents = listOf(idleAgent, codingAgent),
    )

    assertFalse(idleAgent.running)
    assertTrue(codingAgent.running)
    assertTrue(task.isActive)
    assertTrue(task.activeCount == 1)
    assertFalse(task.copy(status = "completed").isActive)
    val multiAgentTask = HanakoTeamTaskStatus(
      taskId = "multi-1",
      mode = HANA_TEAM_MULTI_AGENT_SESSION_MODE,
      status = "running",
      agents = nbgMultiAgentSessionAgents("multi-1", "running"),
    )
    assertTrue(multiAgentTask.isMultiAgentSession())
    assertTrue(multiAgentTask.isLocalSessionExecution())
    assertFalse(multiAgentTask.isSingleAgentExecution())
    assertTrue(multiAgentTask.agents.size == 5)
    assertTrue(multiAgentTask.teamRuntimeLabel() == "团队：5 工作中")
    assertTrue(multiAgentTask.normalizedAndroidTeamTask().agents.size == 5)
  }

  @Test
  fun teamAgentModeIsRemovedFromAndroidUi() {
    val agentUi = readAgentUiSource()
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()

    assertFalse(mainActivity.contains("nbg.previewTeam"))
    assertFalse(agentUi.contains("previewTeam"))
    assertFalse(agentUi.contains("hanako.showPreviewTeamTask()"))
    assertTrue(agentUi.contains("teamTask = hanakoState.teamTask"))
    assertTrue(agentUi.contains("NbgAgentsTeamTaskCard"))
    assertFalse(agentUi.contains("onSendTeam"))
    assertFalse(agentUi.contains("teamMode"))
    assertFalse(agentUi.contains("value = if (teamMode) \"团队\" else \"单人\""))
    assertFalse(agentUi.contains("hanako.createTeamTask("))
    assertFalse(agentUi.contains("hanako.createTeamTaskWithUrlApi("))
    assertFalse(agentUi.contains("state.teamTask?.isActive == true"))
    assertFalse(agentUi.contains("contentDescription = \"Agent 团队看板\""))
    assertFalse(agentUi.contains("contentDescription = \"停止团队任务\""))
    assertTrue(agentUi.contains("NbgRuntimeStatusBar(runtimeStatus)"))
    assertFalse(agentUi.contains("NbgAgentCommandCenterLauncher("))
    assertFalse(agentUi.contains("NbgAgentCommandCenterSheet("))
    assertFalse(agentUi.contains("contentDescription = \"多 Agent 指挥台入口\""))
    assertFalse(agentUi.contains("contentDescription = \"协同指挥台面板\""))
    assertFalse(agentUi.contains("NbgAgentOfficePreview("))
    assertFalse(agentUi.contains("NbgAgentCommandEventStream("))
    assertFalse(agentUi.contains("nbgAgentCommandCenterState("))

    val messageUi = File("src/main/java/com/nbg/android/NbgAgentMessageUi.kt").readText()
    assertFalse(messageUi.contains("NbgPixelOfficeButton("))
    assertFalse(messageUi.contains("contentDescription = \"打开像素办公室\""))
    assertFalse(messageUi.contains("NbgPixelOfficePopup("))
    assertFalse(messageUi.contains("contentDescription = \"像素办公室弹窗\""))
    assertFalse(messageUi.contains("NbgPixelOfficeScene("))
    assertFalse(messageUi.contains("NbgPixelOfficeZone("))
    assertFalse(messageUi.contains("NbgPixelOfficeWalkway("))
    assertFalse(messageUi.contains("NbgPixelOfficeEntryTiles("))
    assertFalse(messageUi.contains("NbgPixelOfficeBookshelf("))
    assertFalse(messageUi.contains("NbgPixelOfficeQuadWorkstations("))
    assertFalse(messageUi.contains("NbgPixelOfficeLounge("))
    assertFalse(messageUi.contains("NbgPixelOfficeCollabTable("))
    assertFalse(messageUi.contains("NbgPixelOfficeStatusBubble("))
    assertFalse(messageUi.contains("NbgPixelOfficeComputer("))
  }

  @Test
  fun agentControlsUseDarkFunctionalButtonStyling() {
    val drawer = File("src/main/java/com/nbg/android/NbgAgentDrawerUi.kt").readText()
    val composer = File("src/main/java/com/nbg/android/NbgAgentComposerUi.kt").readText()
    val chat = File("src/main/java/com/nbg/android/NbgAgentChatUi.kt").readText()
    val message = File("src/main/java/com/nbg/android/NbgAgentMessageUi.kt").readText()
    val urlApi = File("src/main/java/com/nbg/android/NbgAgentUrlApiUi.kt").readText()
    val mcp = File("src/main/java/com/nbg/android/NbgAgentMcpUi.kt").readText()

    assertTrue(drawer.contains("internal fun NbgDrawerPrimaryAction("))
    assertTrue(drawer.contains(".height(52.dp)"))
    assertTrue(drawer.contains("text = \"开始新的上下文\""))
    assertTrue(drawer.contains("color = NbgAgentColors.SurfaceContainerHigh"))
    assertTrue(drawer.contains(".background(NbgAgentColors.PrimaryContainer, RoundedCornerShape(10.dp))"))
    assertTrue(drawer.contains("internal fun NbgDrawerSearchField("))
    assertTrue(drawer.contains("onClear: () -> Unit"))
    assertTrue(drawer.contains("contentDescription = \"清空搜索\""))
    assertFalse(drawer.contains("internal fun NbgSearchPill("))
    assertTrue(drawer.contains("color = if (selected) NbgAgentColors.PrimaryContainer else NbgAgentColors.SurfaceContainer"))
    assertTrue(drawer.contains("shape = RoundedCornerShape(10.dp)"))

    assertTrue(composer.contains("internal fun NbgPlainIconButton("))
    assertTrue(composer.contains(".size(40.dp)"))
    assertTrue(composer.contains(".background(NbgAgentColors.SurfaceContainer, RoundedCornerShape(13.dp))"))
    assertTrue(composer.contains("modifier = Modifier.size(19.dp)"))

    assertTrue(urlApi.contains("internal fun NbgInlineActionButton("))
    assertTrue(urlApi.contains("primary -> NbgAgentColors.PrimaryContainer"))
    assertTrue(urlApi.contains(".height(36.dp)"))
    assertTrue(urlApi.contains(".background(if (primary) NbgAgentColors.Selected else NbgAgentColors.GlassButton, RoundedCornerShape(8.dp))"))

    assertTrue(chat.contains("internal fun NbgScrollToBottomButton("))
    assertTrue(chat.contains(".size(44.dp)"))
    assertTrue(chat.contains(".background(NbgAgentColors.SurfaceContainerHigh, RoundedCornerShape(14.dp))"))

    assertTrue(message.contains("internal fun NbgInlineTextButton("))
    assertTrue(message.contains(".height(28.dp)"))
    assertTrue(message.contains(".background(NbgAgentColors.SurfaceContainer, RoundedCornerShape(11.dp))"))
    assertTrue(message.contains("internal fun NbgMiniIconButton("))
    assertTrue(message.contains("contentColor = NbgAgentColors.TextStrong"))

    assertTrue(mcp.contains("private fun NbgMcpTransportButton("))
    assertTrue(mcp.contains("shape = RoundedCornerShape(12.dp)"))
    assertTrue(mcp.contains("color = if (selected) NbgAgentColors.PrimaryContainer else NbgAgentColors.SurfaceLow"))

    val pixelPet = File("src/main/java/com/nbg/android/NbgPixelPetUi.kt").readText()
    assertTrue(pixelPet.contains("val maxInset = containerHeightPx.toFloat()"))
    assertTrue(pixelPet.contains("val minInset = fallbackBottomInsetPx.coerceAtMost(maxInset)"))
    assertFalse(pixelPet.contains(".coerceIn(fallbackBottomInsetPx, containerHeightPx.toFloat())"))
  }

  @Test
  fun chatListKeysStayUniqueWhenHistoryContainsDuplicateMessageIds() {
    val messages = listOf(
      NbgAgentMessage(id = 54601L, role = NbgAgentRole.Assistant, text = "first"),
      NbgAgentMessage(id = 54601L, role = NbgAgentRole.Assistant, text = "second"),
      NbgAgentMessage(id = 54601L, role = NbgAgentRole.User, text = "third"),
    )

    val keys = nbgMessageListItems(messages).map { it.key }

    assertEquals(keys.size, keys.toSet().size)
    assertTrue(keys[0].contains("54601:0:Assistant"))
    assertTrue(keys[1].contains("54601:1:Assistant"))
    assertTrue(keys[2].contains("54601:2:User"))
  }

  @Test
  fun messageListSignatureChangesWhenEqualLengthTextChanges() {
    val first = listOf(
      NbgAgentMessage(id = 42L, role = NbgAgentRole.Assistant, text = "结果：成功"),
    )
    val second = listOf(
      NbgAgentMessage(id = 42L, role = NbgAgentRole.Assistant, text = "结果：失败"),
    )

    assertEquals(first.single().text.length, second.single().text.length)
    assertTrue(nbgMessageListSignature(first) != nbgMessageListSignature(second))
    assertTrue(nbgMessageListSignature(first).single().textHash != nbgMessageListSignature(second).single().textHash)
  }

  @Test
  fun messageEnterAnimationTargetsLastMessagePrimaryItem() {
    val message = NbgAgentMessage(
      id = 43L,
      role = NbgAgentRole.Assistant,
      text = "chunk-1\n\nchunk-2",
      textChunks = listOf("chunk-1", "chunk-2"),
      streaming = true,
    )
    val items = nbgMessageListItems(listOf(message))

    assertEquals(2, items.size)
    assertTrue(items.first().showHeader)
    assertFalse(items.last().showHeader)
    assertEquals(items.first().key, nbgLastMessagePrimaryItemKey(items))
    assertTrue(items.last().key != nbgLastMessagePrimaryItemKey(items))
  }

  @Test
  fun messageEnterAnimationTargetsLastDuplicateIdMessagePrimaryItem() {
    val messages = listOf(
      NbgAgentMessage(
        id = 44L,
        role = NbgAgentRole.Assistant,
        text = "old",
      ),
      NbgAgentMessage(
        id = 44L,
        role = NbgAgentRole.Assistant,
        text = "new-1\n\nnew-2",
        textChunks = listOf("new-1", "new-2"),
        streaming = true,
      ),
    )
    val items = nbgMessageListItems(messages)

    assertEquals("44:1:Assistant:chunk:0", nbgLastMessagePrimaryItemKey(items))
    assertTrue(items.first().key != nbgLastMessagePrimaryItemKey(items))
    assertTrue(items.last().key != nbgLastMessagePrimaryItemKey(items))
  }

  @Test
  fun messageListSignatureTracksEqualLengthToolAndContentBlockMetadata() {
    val firstBlock = listOf(
      NbgAgentMessage(
        id = 51L,
        role = NbgAgentRole.ContentBlock,
        text = "任务",
        contentBlock = HanakoContentBlock(type = "todo", title = "写测试", subtitle = "等待", detail = "abc", status = "open", taskId = "t1"),
      ),
    )
    val secondBlock = listOf(
      NbgAgentMessage(
        id = 51L,
        role = NbgAgentRole.ContentBlock,
        text = "任务",
        contentBlock = HanakoContentBlock(type = "todo", title = "跑测试", subtitle = "等待", detail = "xyz", status = "open", taskId = "t1"),
      ),
    )
    val firstTool = listOf(
      NbgAgentMessage(
        id = 52L,
        role = NbgAgentRole.Tool,
        text = "工具",
        toolStatus = HanakoToolStatus(key = "tool-1", kind = "shell", title = "运行", subtitle = "开始", detail = "abc", status = "running"),
      ),
    )
    val secondTool = listOf(
      NbgAgentMessage(
        id = 52L,
        role = NbgAgentRole.Tool,
        text = "工具",
        toolStatus = HanakoToolStatus(key = "tool-1", kind = "shell", title = "完成", subtitle = "开始", detail = "xyz", status = "running"),
      ),
    )

    assertEquals(firstBlock.single().text.length, secondBlock.single().text.length)
    assertEquals(firstTool.single().text.length, secondTool.single().text.length)
    assertTrue(nbgMessageListSignature(firstBlock) != nbgMessageListSignature(secondBlock))
    assertTrue(nbgMessageListSignature(firstTool) != nbgMessageListSignature(secondTool))
    assertTrue(nbgMessageListSignature(firstBlock).single().contentBlockHash != nbgMessageListSignature(secondBlock).single().contentBlockHash)
    assertTrue(nbgMessageListSignature(firstTool).single().toolMetaHash != nbgMessageListSignature(secondTool).single().toolMetaHash)
  }

  @Test
  fun autoScrollSignatureTracksLastToolAndContentBlockMetadata() {
    val firstBlock = listOf(
      NbgAgentMessage(
        id = 61L,
        role = NbgAgentRole.ContentBlock,
        text = "任务",
        contentBlock = HanakoContentBlock(type = "todo", title = "写测试", subtitle = "等待", detail = "abc", status = "open", taskId = "t1"),
      ),
    )
    val secondBlock = listOf(
      NbgAgentMessage(
        id = 61L,
        role = NbgAgentRole.ContentBlock,
        text = "任务",
        contentBlock = HanakoContentBlock(type = "todo", title = "跑测试", subtitle = "等待", detail = "xyz", status = "open", taskId = "t1"),
      ),
    )
    val firstTool = listOf(
      NbgAgentMessage(
        id = 62L,
        role = NbgAgentRole.Tool,
        text = "工具",
        toolStatus = HanakoToolStatus(key = "tool-1", kind = "shell", title = "运行", subtitle = "开始", detail = "abc", status = "running"),
      ),
    )
    val secondTool = listOf(
      NbgAgentMessage(
        id = 62L,
        role = NbgAgentRole.Tool,
        text = "工具",
        toolStatus = HanakoToolStatus(key = "tool-1", kind = "shell", title = "完成", subtitle = "开始", detail = "xyz", status = "running"),
      ),
    )

    assertEquals(firstBlock.single().text.length, secondBlock.single().text.length)
    assertEquals(firstTool.single().text.length, secondTool.single().text.length)
    assertTrue(nbgAutoScrollSignature(firstBlock) != nbgAutoScrollSignature(secondBlock))
    assertTrue(nbgAutoScrollSignature(firstTool) != nbgAutoScrollSignature(secondTool))
    assertTrue(nbgAutoScrollSignature(firstBlock).contentBlockHash != nbgAutoScrollSignature(secondBlock).contentBlockHash)
    assertTrue(nbgAutoScrollSignature(firstTool).toolMetaHash != nbgAutoScrollSignature(secondTool).toolMetaHash)
  }

  @Test
  fun autoScrollSignatureTracksTerminalOutputMoreOftenThanRenderChunks() {
    fun terminalMessage(outputLength: Int): List<NbgAgentMessage> = listOf(
      NbgAgentMessage(
        id = 63L,
        role = NbgAgentRole.Tool,
        text = "终端",
        toolStatus = HanakoToolStatus(
          key = "terminal-1",
          kind = "terminal",
          title = "运行命令",
          status = "running",
          running = true,
          terminalOutput = HanakoTerminalOutput(
            sessionId = "term-1",
            output = "x".repeat(outputLength),
          ),
        ),
      ),
    )

    val firstScrollBucket = nbgAutoScrollSignature(terminalMessage(HANA_MOBILE_TERMINAL_SCROLL_BUCKET_CHARS - 1))
    val secondScrollBucket = nbgAutoScrollSignature(terminalMessage(HANA_MOBILE_TERMINAL_SCROLL_BUCKET_CHARS))
    val atRenderChunk = nbgAutoScrollSignature(terminalMessage(HANA_MOBILE_TOOL_TEXT_CHUNK_CHARS))

    assertTrue(HANA_MOBILE_TERMINAL_SCROLL_BUCKET_CHARS < HANA_MOBILE_TOOL_TEXT_CHUNK_CHARS)
    assertTrue(firstScrollBucket.terminalOutputBucket != secondScrollBucket.terminalOutputBucket)
    assertTrue(atRenderChunk.terminalOutputBucket > HANA_MOBILE_TOOL_TEXT_CHUNK_CHARS / HANA_MOBILE_TOOL_TEXT_CHUNK_CHARS)
  }

  @Test
  fun combinedHashIsOrderSensitiveWithoutJoiningLargeStrings() {
    assertEquals(nbgCombinedHash("a", "bc", ""), nbgCombinedHash("a", "bc", ""))
    assertTrue(nbgCombinedHash("a", "bc", "") != nbgCombinedHash("ab", "c", ""))
  }

  @Test
  fun terminalOutputMergeAvoidsDuplicateSnapshotsAndOverlaps() {
    assertEquals(
      "one\ntwo\nthree",
      mergeTerminalOutputText(
        currentOutput = "one\ntwo",
        nextOutput = "one\ntwo\nthree",
        currentSliceTo = 2,
        nextSliceFrom = 0,
      ),
    )
    assertEquals(
      "one\ntwo\nthree",
      mergeTerminalOutputText(
        currentOutput = "one\ntwo",
        nextOutput = "two\nthree",
        currentSliceTo = 2,
        nextSliceFrom = 2,
      ),
    )
    assertEquals(
      "one\ntwo\nthree",
      mergeTerminalOutputText(
        currentOutput = "one\ntwo",
        nextOutput = "three",
        currentSliceTo = 2,
        nextSliceFrom = 2,
      ),
    )
  }

  @Test
  fun terminalOutputOverlapScanIsBoundedForLongOutput() {
    val prefix = "A".repeat(HANA_MOBILE_TERMINAL_OVERLAP_SCAN_LIMIT + 128)
    val boundedOverlap = "B".repeat(HANA_MOBILE_TERMINAL_OVERLAP_SCAN_LIMIT)
    val current = prefix + boundedOverlap
    val next = boundedOverlap + "tail"

    val merged = mergeTerminalOutputText(
      currentOutput = current,
      nextOutput = next,
      currentSliceTo = 10,
      nextSliceFrom = 10,
    )

    assertEquals((current + "tail").takeLast(HANA_MOBILE_TERMINAL_OUTPUT_LIMIT), merged)
    assertFalse(merged.contains(boundedOverlap + boundedOverlap))
  }

  @Test
  fun liveDiffPreviewStatsOnlyScansShownLines() {
    val lines = listOf(
      "+ added",
      "- removed",
      "+++ b/file.txt",
      "--- a/file.txt",
      "context",
      "+ another",
    )

    assertEquals(2 to 1, nbgDiffStatsFromShownLines(lines))
  }

  @Test
  fun fileDiffTextIsTrimmedForMobileUi() {
    val small = "abc\n123"
    val large = "A".repeat(HANA_FILE_DIFF_TEXT_LIMIT) + "MIDDLE" + "Z".repeat(HANA_FILE_DIFF_TEXT_LIMIT)

    assertEquals(small, nbgTrimFileDiffText(small))
    val trimmed = nbgTrimFileDiffText(large)
    assertTrue(trimmed.length <= HANA_FILE_DIFF_TEXT_LIMIT)
    assertTrue(trimmed.startsWith("AAA"))
    assertTrue(trimmed.endsWith("ZZZ"))
    assertTrue(trimmed.contains("Android diff preview truncated"))
    val cached = parseCachedFileDiff(
      org.json.JSONObject()
        .put("fileName", "large.txt")
        .put("filePath", "/root/large.txt")
        .put("oldContent", large)
        .put("newContent", large)
        .put("unifiedDiff", large),
    )
    assertTrue(cached != null)
    assertTrue(cached!!.oldContent.length <= HANA_FILE_DIFF_TEXT_LIMIT)
    assertTrue(cached.newContent.length <= HANA_FILE_DIFF_TEXT_LIMIT)
    assertTrue(cached.unifiedDiff.length <= HANA_FILE_DIFF_TEXT_LIMIT)
    assertTrue(cached.unifiedDiff.contains("Android diff preview truncated"))
  }

  @Test
  fun draftConversationScrollKeyIgnoresStreamingTextChanges() {
    val firstFrame = listOf(
      NbgAgentMessage(id = 7L, role = NbgAgentRole.User, text = "写一个计划"),
      NbgAgentMessage(id = 8L, role = NbgAgentRole.Assistant, text = "第一段", streaming = true),
    )
    val laterFrame = listOf(
      NbgAgentMessage(id = 7L, role = NbgAgentRole.User, text = "写一个计划"),
      NbgAgentMessage(id = 8L, role = NbgAgentRole.Assistant, text = "第一段，继续输出更多内容", streaming = true),
    )

    assertEquals(
      nbgConversationScrollKey(null, firstFrame),
      nbgConversationScrollKey(null, laterFrame),
    )
    assertEquals(
      "session:/root/.hanako/session.jsonl:0",
      nbgConversationScrollKey("/root/.hanako/session.jsonl", laterFrame),
    )
    assertEquals(
      "session:/root/.hanako/session.jsonl:2",
      nbgConversationScrollKey("/root/.hanako/session.jsonl", laterFrame, historyRenderVersion = 2L),
    )
    assertTrue(
      nbgConversationScrollKey("/root/.hanako/session.jsonl", firstFrame, historyRenderVersion = 1L) !=
        nbgConversationScrollKey("/root/.hanako/session.jsonl", laterFrame, historyRenderVersion = 2L),
    )
  }

  @Test
  fun composerChipValueFallsBackForNonDescriptiveShortValues() {
    assertEquals("自动", nbgCompactComposerChipValue("", "自动"))
    assertEquals("自动", nbgCompactComposerChipValue(".", "自动"))
    assertEquals("操作", nbgCompactComposerChipValue("..", "操作"))
    assertEquals("deepseek-v4-pro", nbgCompactComposerChipValue(" deepseek-v4-pro ", "未选择"))
  }

  @Test
  fun readOnlyPermissionModeIsDisplayedAsPlanMode() {
    val bridge = File("src/main/java/com/nbg/android/HanakoBridge.kt").readText()

    assertEquals("计划", hanakoPermissionModeLabel("read_only"))
    assertEquals("先问", hanakoPermissionModeLabel("unexpected"))
    assertTrue(bridge.contains("when (nbgNormalizePermissionMode(mode))"))
    assertTrue(bridge.contains("NBG_PERMISSION_MODE_READ_ONLY -> \"计划\""))
  }

  @Test
  fun agentChatKeepsCoreHanakoProtocolAndDropsDeferredAdvancedFeatures() {
    val agentUi = readAgentUiSource()
    val bridge = readHanakoBridgeSource()
    val styles = File("src/main/res/values/styles.xml").readText()

    assertFalse(agentUi.contains("这是 UI 占位回复"))
    assertFalse(agentUi.contains("已收到："))
    assertTrue(bridge.contains("ws://127.0.0.1:\$port/ws?token=\$token"))
    assertTrue(bridge.contains("val nextSocket = wsClient.newWebSocket"))
    assertTrue(bridge.contains("private fun isCurrent(socket: WebSocket): Boolean = webSocket === socket"))
    assertTrue(bridge.contains("if (!isCurrent(webSocket)) return@launch"))
    assertTrue(bridge.contains("if (!isCurrent(webSocket)) return"))
    assertTrue(bridge.contains("webSocket = nextSocket"))
    assertTrue(bridge.contains("val wasStreaming = _state.value.streaming"))
    assertTrue(bridge.contains("val feedbackAssistantId = if (wasStreaming)"))
    assertTrue(bridge.contains("sealAssistantTextSegment()"))
    assertTrue(bridge.contains("val messageType = if (wasStreaming) \"interrupt_prompt\" else \"prompt\""))
    assertTrue(bridge.contains("displayText: String = text"))
    assertTrue(bridge.contains("fun sendMultiAgentPrompt(text: String, displayText: String = text)"))
    assertTrue(bridge.contains("fun sendMultiAgentPromptWithUrlApi(text: String, entry: NbgStoredApi, model: NbgApiModel, displayText: String = text)"))
    assertTrue(bridge.contains("val visiblePrompt = displayText.trim().ifBlank { prompt }"))
    assertTrue(bridge.contains("multiAgentMode: Boolean = false"))
    assertTrue(bridge.contains("mode = HANA_TEAM_MULTI_AGENT_SESSION_MODE"))
    assertTrue(bridge.contains("agents = nbgMultiAgentSessionAgents(taskId, \"running\")"))
    assertTrue(bridge.contains("it.isLocalSessionExecution() && it.isActive"))
    assertTrue(bridge.contains("withActiveTurnFinished(\"completed\", \"本地编排任务已完成\")"))
    assertTrue(bridge.contains("onEvent(HanakoChatEvent.UserMessage(visiblePrompt))"))
    assertTrue(bridge.contains("displayText = visiblePrompt"))
    assertTrue(bridge.contains("val promptForModel = prompt.trim()"))
    assertFalse(bridge.contains("languageInstruction"))
    assertTrue(bridge.contains(".put(\"type\", type)"))
    assertTrue(bridge.contains(".put(\"displayMessage\", displayMessage)"))
    assertTrue(bridge.contains("androidUiContext(sessionPath, learningContext)"))
    assertTrue(bridge.contains("val learningContext = buildLearningContextForPrompt(promptForModel)"))
    assertTrue(bridge.contains("currentViewed\", \"android-chat\""))
    assertTrue(bridge.contains(".put(\"locale\", NBG_ANDROID_LANGUAGE_LOCALE)"))
    assertFalse(bridge.contains("responseLanguage"))
    assertFalse(bridge.contains("thinkingLanguage"))
    assertTrue(bridge.contains("\"type\", \"slash\""))
    assertTrue(bridge.contains("\"agentId\", \"hanako\""))
    assertTrue(bridge.contains("\"slash_result\" -> {"))
    assertTrue(bridge.contains("finishLocalClientTurn()"))
    assertTrue(bridge.contains("private fun finishLocalSendFailure() {\n    clearLearningTurn()\n    finishLocalClientTurn()\n  }"))
    val teamTaskBlock = bridge.substringAfter("private fun createTeamTaskInternal(")
      .substringBefore("private fun String.isMissingHanakoTeamApi")
    assertTrue(teamTaskBlock.contains("onEvent(HanakoChatEvent.SystemMessage(\"已创建代码团队任务：\${nextTask.title}\"))\n        finishLocalClientTurn()"))
    assertTrue(bridge.contains("\"type\", \"abort\""))
    assertTrue(bridge.contains("setAssistantText(id, \"HanakoPro 错误：\$message\")"))
    assertTrue(bridge.contains("onEvent(HanakoChatEvent.ToolInterrupted)\n        finishLearningTurnAndEmitEnded()"))
    assertTrue(bridge.contains("private fun abortActiveTurn"))
    assertTrue(bridge.contains("message.contains(\"还在说话\")"))
    assertTrue(bridge.contains("message.contains(\"timed out\", ignoreCase = true)"))
    assertTrue(bridge.contains("abortActiveTurn(msg.cleanString(\"sessionPath\") ?: _state.value.sessionPath"))
    assertTrue(bridge.contains("\"type\", \"context_usage\""))
    assertTrue(bridge.contains("\"type\", \"resume_stream\""))
    assertTrue(bridge.contains("data class ThinkingText"))
    assertFalse(bridge.contains("translateThinkingChunk"))
    assertFalse(bridge.contains("resolveThinkingTranslationUrlApi"))
    assertFalse(bridge.contains("resolveHanakoModelsJsonUrlApiForCurrentModel"))
    assertTrue(bridge.contains("models.json"))
    assertFalse(bridge.contains("思考内容翻译"))
    assertFalse(bridge.contains("thinking translation"))
    assertTrue(bridge.contains("private data class HanakoStreamMeta"))
    assertTrue(bridge.contains("streamMetaBySession"))
    assertTrue(bridge.contains("private var historyRequestSerial = 0L"))
    assertTrue(bridge.contains("private var focusRequestSerial = 0L"))
    assertTrue(bridge.contains("private var restoredInitialCache = false"))
    assertTrue(bridge.contains("private var connectRequestSerial = 0L"))
    assertTrue(bridge.contains("private var activeConnectAllowsLaunch = false"))
    assertTrue(bridge.contains("private var warmupJob: Job? = null"))
    assertTrue(bridge.contains("val requestSerial = ++historyRequestSerial"))
    assertTrue(bridge.contains("val requestSerial = ++focusRequestSerial"))
    assertTrue(bridge.contains("val requestSerial = ++connectRequestSerial"))
    assertTrue(bridge.contains("recoverHanakoLocalApiFailure(info, error)"))
    assertTrue(bridge.contains("withLocalHanakoRetry(info)"))
    assertTrue(bridge.contains("ensureLiveSessionForRequestWithRetry(info)"))
    assertTrue(bridge.contains("reconnectJob?.cancel()"))
    assertTrue(bridge.contains("restoreLatestCachedSessionOnce()"))
    assertTrue(bridge.contains("connect(allowLaunch = false)"))
    assertTrue(bridge.contains("scheduleBackgroundWarmup()"))
    assertFalse(bridge.contains("fun start() {\n    restoreLatestCachedSessionOnce()\n    if (!_state.value.connecting"))
    assertTrue(bridge.contains("if (!_state.value.connecting || !activeConnectAllowsLaunch)"))
    assertTrue(bridge.contains("reuse active background HanakoPro warmup for send"))
    assertTrue(bridge.contains("handleHanakoLocalApiFailure(info, error)"))
    assertTrue(bridge.contains("isHanakoLocalConnectionFailure"))
    assertTrue(bridge.contains("current is java.net.ConnectException"))
    assertTrue(bridge.contains("Failed to connect"))
    assertTrue(bridge.contains("HanakoPro 连接失效，正在重启"))
    assertTrue(bridge.contains("serverInfo = null"))
    assertTrue(bridge.contains("webSocket?.close(1001, \"connect timeout\")"))
    assertTrue(bridge.contains("_state.value.connected || _state.value.prewarming || activeConnectAllowsLaunch"))
    assertTrue(bridge.contains("val adoptingEarlyLaunch = launcher.isLaunching"))
    assertTrue(bridge.contains("background warmup adopting existing HanakoPro launcher"))
    assertTrue(bridge.contains("waitForWarmupConnection()"))
    assertTrue(bridge.contains("val isLaunching: Boolean"))
    assertTrue(bridge.contains("connect(allowLaunch = true)"))
    assertTrue(bridge.contains("ignore stale connect failure"))
    assertTrue(bridge.contains("ignore stale connect success"))
    assertTrue(bridge.contains("private fun isStaleConnectSuccess(requestSerial: Long?): Boolean"))
    assertTrue(bridge.contains("HanakoPro not running; local history only"))
    assertTrue(bridge.contains("BACKGROUND_WARMUP_DELAY_MS = 120L"))
    assertTrue(bridge.contains("CONNECT_WAIT_POLL_MS = 250L"))
    assertTrue(bridge.contains("LAUNCH_HEALTH_CHECK_INTERVAL_MS = 250L"))
    assertTrue(bridge.contains("repeat(LAUNCH_HEALTH_CHECK_ATTEMPTS)"))
    assertTrue(bridge.contains("Thread.sleep(250L)"))
    assertTrue(bridge.contains("background warmup starting HanakoPro"))
    assertTrue(bridge.contains("object HanakoBackgroundWarmup"))
    assertTrue(bridge.contains("early warmup launching HanakoPro"))
    assertTrue(bridge.contains("early warmup reused healthy HanakoPro"))
    assertTrue(bridge.contains("HanakoServerLauncher().startIfNeeded(appContext)"))
    assertTrue(bridge.contains("clearStaleHanakoServer(appContext, existing)"))
    assertTrue(bridge.contains("if (existing != null) {\n          clearStaleHanakoServer(appContext, existing)\n        }"))
    assertTrue(bridge.contains("private fun clearStaleHanakoServer(context: Context, info: HanakoServerInfo? = findServerInfo(context))"))
    assertTrue(bridge.contains("private fun killHanakoPid(pid: Int)"))
    assertTrue(bridge.contains("android-server.pid"))
    assertTrue(bridge.contains("kill -9 \$pid 2>/dev/null || true"))
    assertFalse(bridge.contains("android.os.Process.killProcess"))
    assertTrue(bridge.contains("cleared stale HanakoPro server state"))
    assertTrue(bridge.contains("private suspend fun restartStaleServer(info: HanakoServerInfo, reason: String): HanakoServerInfo"))
    assertTrue(bridge.contains("return restartStaleServer(info, \"HanakoPro 健康检查超时\")"))
    assertTrue(bridge.contains("return restartStaleServer(existing, \"HanakoPro 状态文件指向无响应服务\")"))
    assertTrue(bridge.contains("connectionLabel = \"正在重启 HanakoPro\""))
    assertTrue(bridge.contains("connectOnce(allowLaunch = true)"))
    assertTrue(bridge.contains("return serverInfo ?: error(\"HanakoPro 重启失败\")"))
    assertTrue(bridge.contains("clear_stale_hanakopro_state()"))
    assertTrue(bridge.contains("clear_stale_hanakopro_state"))
    assertFalse(bridge.contains("write_pack_healthcheck"))
    assertFalse(bridge.contains(".nbg-check-hanakopro-pack.sh"))
    assertFalse(bridge.contains(".nbg-apply-hanakopro-android-defaults.js"))
    assertTrue(bridge.contains("prewarming: Boolean = false"))
    assertTrue(bridge.contains("HanakoPro 正在后台预热，连接后自动发送"))
    assertTrue(bridge.contains("HANA_CREATE_STARTUP_SESSION:-0"))
    assertTrue(bridge.contains("resolveLiveServerInfo(allowLaunch = true)"))
    assertTrue(bridge.contains("connectOnce(allowLaunch, requestSerial)"))
    assertTrue(bridge.contains("if (isStaleConnectSuccess(requestSerial)) return"))
    assertTrue(bridge.contains("resolveLiveServerInfo(allowLaunch)"))
    assertTrue(bridge.contains("if (!allowLaunch)"))
    assertTrue(bridge.contains("HanakoPro 未启动"))
    assertTrue(bridge.contains("reuse healthy HanakoPro server"))
    assertTrue(bridge.contains("loadCachedHistory(path, requestSerial)"))
    assertTrue(bridge.contains("requestSerial == focusRequestSerial"))
    assertTrue(bridge.contains("saveCachedHistory(sessionPath, snapshot)"))
    assertTrue(bridge.contains("restored cached history for \${cached.sessionPath} messages=\${cached.snapshot.messages.size}"))
    assertTrue(bridge.contains("saveCachedHistory(cached.sessionPath, cached.snapshot)"))
    assertTrue(bridge.contains("readLatestCachedSession"))
    assertTrue(bridge.contains("readCachedHistory"))
    assertTrue(bridge.contains("readLatestLocalSessionFile"))
    assertTrue(bridge.contains("readLocalSessionFileByPath"))
    assertTrue(bridge.contains("parseLocalSessionJsonl"))
    assertTrue(bridge.contains("parseLocalSessionJsonl(sessionFile: File, limit: Int = Int.MAX_VALUE)"))
    assertTrue(bridge.contains("nbgLocalSessionLooksUnhealthy"))
    assertTrue(bridge.contains("nbgSessionJsonlLooksUnhealthy"))
    assertTrue(bridge.contains(".hanakopro-android/agents/hanako/sessions"))
    assertTrue(bridge.contains("HANA_HISTORY_CACHE_DIR"))
    assertTrue(bridge.contains("HANA_HISTORY_CACHE_INDEX"))
    assertTrue(bridge.contains("sha256Hex"))
    assertTrue(bridge.contains("nbgMoveReplacingWithAtomicFallback(tmp, file)"))
    assertTrue(bridge.contains("StandardCopyOption.ATOMIC_MOVE"))
    assertTrue(bridge.contains("Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)"))
    assertTrue(bridge.contains("requestSerial != historyRequestSerial || _state.value.sessionPath != sessionPath"))
    assertTrue(bridge.contains("ignore stale history for \$sessionPath request=\$requestSerial"))
    assertTrue(bridge.contains("ignore stale history failure for \$sessionPath request=\$requestSerial"))
    assertTrue(bridge.contains("/api/sessions/messages?path=\$encodedPath&limit=200&all=1"))
    assertFalse(bridge.contains("/api/sessions/messages?path=\$encodedPath&limit=80"))
    assertTrue(bridge.contains("ignore stale context usage for \$eventSessionPath; current=\$currentSessionPath"))
    assertTrue(bridge.contains("currentSessionPath != eventSessionPath"))
    assertTrue(bridge.contains("ignore stale focus switch for \$path request=\$requestSerial"))
    assertTrue(bridge.contains("ignore stale focus switch failure for \$path request=\$requestSerial"))
    assertTrue(bridge.contains("ignore stale create session request=\$requestSerial"))
    assertTrue(bridge.contains("ignore stale create session failure request=\$requestSerial"))
    assertTrue(bridge.contains("ignore stale create URL API session failure request=\$requestSerial"))
    assertTrue(bridge.contains("ignore stale replay history for \$sessionPath"))
    assertTrue(bridge.contains("ignore stale replay history for \$sessionPath; current="))
    assertTrue(bridge.contains("it.copy(streaming = false)"))
    assertTrue(bridge.contains("ignore stale revert history for \$sessionPath"))
    val revertBlock = bridge.substringAfter("fun revertLatestTurn()")
      .substringBefore("fun abort()")
    assertTrue(revertBlock.contains("resetActiveTurnState()\n      _state.update { it.copy(streaming = true, lastError = null) }"))
    assertTrue(revertBlock.contains("ignore stale revert history for \$sessionPath"))
    assertTrue(revertBlock.contains("_state.update { it.copy(streaming = false, lastError = null) }"))
    assertTrue(revertBlock.contains("_state.update { it.copy(streaming = false, lastError = message) }"))
    assertTrue(bridge.contains("ignore stale compress-fork result for \$sessionPath"))
    assertTrue(bridge.contains("it.copy(compressing = false)"))
    assertTrue(bridge.contains("handleWsMessage(webSocket, text)"))
    assertTrue(bridge.contains("private fun handleWsMessage(sourceSocket: WebSocket, text: String)"))
    assertTrue(bridge.contains("scope.launch {\n      if (webSocket !== sourceSocket) return@launch\n      val type = msg.optString(\"type\")"))
    assertTrue(bridge.contains("if (type != \"session_title\" && !isCurrentSessionMessage(msg)) return@launch"))
    assertTrue(bridge.contains("if (type != \"stream_resume\") {\n        if (!shouldAcceptStreamEvent(msg)) return@launch\n        updateStreamMeta(msg)\n      }"))
    assertTrue(bridge.contains(".put(\"streamId\", meta.streamId ?: JSONObject.NULL)"))
    assertTrue(bridge.contains(".put(\"sinceSeq\", meta.lastSeq)"))
    assertTrue(bridge.contains("msg.optIntOrNull(\"nextSeq\")?.let { (it - 1).coerceAtLeast(0) }"))
    assertFalse(bridge.contains(".put(\"sinceSeq\", 0)"))
    assertTrue(bridge.contains("\"compaction_start\""))
    assertTrue(bridge.contains("\"compaction_end\""))
    assertTrue(bridge.contains("handleCompactionStatus"))
    assertTrue(bridge.contains("正在压缩上下文"))
    assertTrue(bridge.contains("if (!_state.value.compressionAvailable)"))
    assertTrue(bridge.contains("当前无法压缩：HanakoPro 未启用上下文压缩"))
    assertTrue(bridge.contains("rawMessage.contains(\"context compression disabled\", ignoreCase = true)"))
    assertTrue(bridge.contains("HanakoPro 未启用上下文压缩。请先在上下文压缩设置中开启后再使用。"))
    assertFalse(bridge.contains("sendPromptInNewSession"))
    assertFalse(bridge.contains("forceNewSession"))
    assertTrue(bridge.contains("sendSlash"))
    assertTrue(bridge.contains("resolveConfirmation"))
    assertTrue(bridge.contains("/api/sessions"))
    assertTrue(bridge.contains("/api/sessions/new"))
    assertTrue(bridge.contains("applyAndroidRuntimeDefaults(info)"))
    assertTrue(bridge.contains(".put(\"locale\", NBG_ANDROID_LANGUAGE_LOCALE)"))
    assertTrue(bridge.contains(".put(\"sandbox\", false)"))
    assertTrue(bridge.contains(".put(\"sandbox_network\", true)"))
    assertTrue(bridge.contains(".put(\"safety_review\", true)"))
    assertTrue(bridge.contains(".put(\"desk\", JSONObject().put(\"home_folder\", NBG_UBUNTU_ROOT_HOME))"))
    assertTrue(bridge.contains("writeAndroidHanakoDefaults(context, filesDir)"))
    assertTrue(bridge.contains(".put(\"sandbox\", false)"))
    assertTrue(bridge.contains(".put(\"android_mobile\", true)"))
    assertTrue(bridge.contains("home_folder: /root/"))
    assertTrue(bridge.contains("NBG_ENGINEERING_CORE_SKILL_NAME"))
    assertTrue(bridge.contains("NBG_ANDROID_DEFAULT_ENABLED_SKILLS"))
    assertTrue(bridge.contains("nbg-engineering-core"))
    assertTrue(bridge.contains("val verifiedDefaultSkills = seedNbgDefaultSkills(context, hanaHome)"))
    assertTrue(bridge.contains("NBG_DEFAULT_SKILLS_ASSET_ROOT"))
    assertTrue(bridge.contains("ensureAndroidAgentEnabledSkills"))
    assertTrue(bridge.contains("nbgVerifiedAndroidBundledSkillNames"))
    assertTrue(bridge.contains("http.ensureAndroidBundledSkillsEnabled(info, verifiedBundledSkillNames)"))
    assertTrue(bridge.contains("root/Desktop/OH-WorkSpace").or(bridge.contains("/root/Desktop/OH-WorkSpace")))
    assertTrue(bridge.contains("nbgDeletePathWithoutFollowingSymlink(File(ubuntuRoot, \"root/Desktop/OH-WorkSpace\"))"))
    assertTrue(bridge.contains("Files.isSymbolicLink(nioPath)"))
    assertTrue(bridge.contains("LinkOption.NOFOLLOW_LINKS"))
    assertTrue(bridge.contains("cleanup_android_workspace()"))
    assertTrue(bridge.contains("sleep 2; cleanup_android_workspace; sleep 5; cleanup_android_workspace"))
    assertTrue(bridge.contains("export HANA_ANDROID=\"1\""))
    assertTrue(bridge.contains("HANA_ROOT=\"/opt/hanakopro-server\""))
    assertTrue(bridge.contains("install_ubuntu || exit 1"))
    assertTrue(bridge.contains("install_android_build_helper || exit 1"))
    assertTrue(bridge.contains("configure_package_sources || exit 1"))
    assertTrue(bridge.contains("fix_android_group_names || exit 1"))
    assertTrue(bridge.contains("ensure_default_development_tools || exit 1"))
    assertFalse(bridge.contains("overflowuid"))
    assertFalse(bridge.contains("bubblewrap"))
    assertTrue(bridge.contains("/api/sessions/switch"))
    assertTrue(bridge.contains("/api/sessions/messages"))
    assertTrue(bridge.contains("/api/sessions/search"))
    assertTrue(bridge.contains("/api/sessions/latest-user-message/replay"))
    assertTrue(bridge.contains("/api/sessions/revert-turn"))
    assertTrue(bridge.contains("/api/sessions/todos/complete"))
    assertTrue(bridge.contains("/api/sessions/compress-fork"))
    assertTrue(bridge.contains("/api/sessions/rename"))
    assertTrue(bridge.contains("deleteSessionPermanently"))
    assertTrue(bridge.contains("/api/sessions/archive"))
    assertTrue(bridge.contains("/api/sessions/archived/delete"))
    assertTrue(bridge.contains("HanakoChatEvent.SessionDeleted(sessionPath)"))
    assertTrue(bridge.contains("data class SessionDeleted(val sessionPath: String)"))
    assertTrue(bridge.contains("data class SessionSelectFailed("))
    assertTrue(bridge.contains("val previousSessionPath: String? = null"))
    assertTrue(bridge.contains("val previousPath = _state.value.sessionPath"))
    assertTrue(bridge.contains("HanakoChatEvent.SessionSelectFailed(path, previousPath)"))
    assertTrue(bridge.contains("previousPath?.let(::loadCachedHistory)"))
    assertTrue(bridge.contains("val selectingSessionPath: String? = null"))
    assertTrue(bridge.contains("private fun resetActiveTurnState()"))
    assertTrue(bridge.contains("sealAssistantTextSegment()\n    endThinking()"))
    assertTrue(bridge.contains("if (path != previousPath) {\n      resetActiveTurnState()"))
    assertTrue(bridge.contains("fun createSession() {\n    val requestSerial = ++focusRequestSerial\n    resetActiveTurnState()"))
    assertTrue(bridge.contains("if (wasCurrent) resetActiveTurnState()"))
    assertTrue(bridge.contains("Log.i(\"NBG_HANAKO\", \"loaded ${'$'}{snapshot.messages.size} history messages"))
    assertTrue(bridge.contains("val sessionPath = result.cleanString(\"sessionPath\")\n        resetActiveTurnState()"))
    assertTrue(bridge.contains("private fun loadHistory(sessionPath: String)"))
    assertFalse(bridge.contains("resetActiveTurn: Boolean"))
    assertFalse(bridge.contains("if (resetActiveTurn) resetActiveTurnState()"))
    assertTrue(bridge.contains("\"session_branch_reset\" -> {\n        resetActiveTurnState()"))
    assertTrue(bridge.contains("_state.value.sessionPath?.let { loadHistory(it) }"))
    assertTrue(bridge.contains("val reset = msg.optBoolean(\"reset\", false)"))
    assertTrue(bridge.contains("if (reset) {\n      resetActiveTurnState()"))
    assertTrue(bridge.contains("if (!isCurrentSessionMessage(event)) {\n          skippedReplayEventCount += 1\n          continue\n        }"))
    assertTrue(bridge.contains("val skipReason = streamEventSkipReason(event)"))
    assertTrue(bridge.contains("if (skipReason != null) {"))
    assertTrue(bridge.contains("acceptedReplayEventCount += 1\n        updateStreamMeta(event)"))
    assertTrue(bridge.contains("private fun shouldAcceptStreamEvent(msg: JSONObject): Boolean"))
    assertTrue(bridge.contains("return streamEventSkipReason(msg) == null"))
    assertTrue(bridge.contains("val incomingSeq = msg.optIntOrNull(\"seq\") ?: return null"))
    assertTrue(bridge.contains("return if (incomingSeq > current.lastSeq) null else \"duplicate_seq\""))
    assertTrue(bridge.contains("selectingSessionPath = path, lastError = null"))
    assertTrue(bridge.contains("return _state.value.selectingSessionPath == null"))
    assertTrue(bridge.contains("HANA_PENDING_NEW_SESSION_PATH"))
    assertTrue(bridge.contains("emitReplacementHistory: Boolean = true"))
    assertTrue(bridge.contains("ensureLiveSessionForRequestWithRetry(info, emitReplacementHistory = false)"))
    assertTrue(bridge.contains("ensureLiveSessionForRequestWithRetry(info, emitReplacementHistory = false).second"))
    assertTrue(bridge.contains("if (emitReplacementHistory) {\n        onEvent(HanakoChatEvent.HistoryLoaded(focus.path, historyToKeep.messages, historyToKeep.todos, historyToKeep.sessionFiles))"))
    assertTrue(bridge.contains("} else if (emitReplacementHistory) {\n      onEvent(HanakoChatEvent.HistoryLoaded(focus.path, emptyList(), emptyList(), emptyList()))"))
    assertTrue(bridge.contains("it.clearCurrentSessionState()"))
    assertTrue(bridge.contains("private fun HanakoChatState.clearCurrentSessionState()"))
    assertTrue(bridge.contains("teamTask = null"))
    assertTrue(bridge.contains("compressing = false"))
    assertTrue(bridge.contains("runtimeStatus = runtimeStatus.copy(usageLabel = null)"))
    assertTrue(bridge.contains("toArchivedDeletePath"))
    assertFalse(bridge.contains("archivedSessionPath"))
    assertFalse(bridge.contains("/api/sessions/restore"))
    assertTrue(bridge.contains("/api/sessions/pin"))
    assertTrue(bridge.contains("/api/confirm/"))
    assertTrue(bridge.contains("/api/health"))
    assertTrue(bridge.contains("HanakoSessionSummary"))
    assertFalse(bridge.contains("HanakoSessionDetails"))
    assertTrue(bridge.contains("HanakoTodoItem"))
    assertTrue(bridge.contains("HanakoSessionFile"))
    assertFalse(bridge.contains("HanakoWorkspaceFile"))
    assertFalse(bridge.contains("HanakoWorkspaceListing"))
    assertFalse(bridge.contains("HanakoWorkspaceFileContent"))
    assertTrue(bridge.contains("HanakoHistorySnapshot"))
    assertTrue(bridge.contains("HanakoConfirmation"))
    assertTrue(bridge.contains("HanakoContentBlock"))
    assertTrue(bridge.contains("HanakoContentBlockPatch"))
    assertTrue(bridge.contains("internal fun parseHanakoContentBlockPatch(patch: JSONObject?): HanakoContentBlockPatch?"))
    assertTrue(bridge.contains("val status = patch.cleanString(\"streamStatus\") ?: patch.cleanString(\"status\")"))
    assertTrue(bridge.contains("title = patch.cleanString(\"taskTitle\") ?: patch.cleanString(\"title\")"))
    assertTrue(bridge.contains("detail = patch.cleanString(\"summary\") ?: patch.cleanString(\"detail\")"))
    assertTrue(bridge.contains("!it.title.isNullOrBlank() ||\n      !it.subtitle.isNullOrBlank() ||\n      !it.detail.isNullOrBlank() ||\n      !it.status.isNullOrBlank()"))
    assertTrue(bridge.contains("HanakoFileDiff"))
    assertTrue(bridge.contains("HanakoFilePreview"))
    assertTrue(bridge.contains("HanakoTerminalOutput"))
    assertTrue(bridge.contains("HanakoToolStatus"))
    assertTrue(bridge.contains("HanakoExternalUserMessage"))
    assertTrue(bridge.contains("HanakoRuntimeStatus"))
    assertTrue(bridge.contains("HanakoAgentSummary"))
    assertTrue(bridge.contains("HanakoModelSummary"))
    assertTrue(bridge.contains("HanakoAgentModelConfig"))
    assertTrue(bridge.contains("HanakoProviderSummary"))
    assertTrue(bridge.contains("HanakoProviderSnapshot"))
    assertTrue(bridge.contains("HanakoModelHealth"))
    assertTrue(bridge.contains("HanakoSlashCommand"))
    assertFalse(bridge.contains("SessionDetailsLoaded"))
    assertFalse(bridge.contains("SessionDetailsFailed"))
    assertTrue(bridge.contains("TodoUpdated"))
    assertTrue(bridge.contains("HistoryLoaded"))
    assertTrue(bridge.contains("ConfirmationRequested"))
    assertTrue(bridge.contains("ConfirmationResolved"))
    assertTrue(bridge.contains("ContentBlockPatch"))
    assertTrue(bridge.contains("ToolStatus(val tool: HanakoToolStatus)"))
    assertTrue(bridge.contains("ToolInterrupted"))
    assertTrue(bridge.contains("AssistantRemoved"))
    assertTrue(bridge.contains("sealAssistantTextSegment"))
    assertTrue(bridge.contains("private fun JSONObject.rawStringAny(vararg names: String): String"))
    assertTrue(bridge.contains("val delta = msg.rawStringAny(\"delta\", \"text\")"))
    assertFalse(bridge.contains("msg.optString(\"delta\").ifBlank { msg.optString(\"text\") }"))
    assertFalse(bridge.contains("AssistantRemoved(feedbackAssistantId)"))
    assertTrue(bridge.contains("handleStreamingStatus"))
    assertTrue(bridge.contains("isInterruptedStatus"))
    assertTrue(bridge.contains("finishInterruptedTurn"))
    assertTrue(bridge.contains("internal fun JSONObject.optBooleanOrNull(name: String): Boolean?"))
    assertTrue(bridge.contains("internal fun JSONObject.optBooleanAnyOrNull(vararg names: String): Boolean?"))
    assertTrue(bridge.contains("private fun JSONObject.hanakoStreamingFlag(): Boolean?"))
    assertTrue(bridge.contains("optBooleanAnyOrNull(\"isStreaming\", \"streaming\", \"running\", \"active\", \"inProgress\", \"busy\")"))
    assertTrue(bridge.contains("val isStreaming = msg.hanakoStreamingFlag() ?: return"))
    assertTrue(bridge.contains("msg.hanakoStreamingFlag()?.let { isStreaming ->\n      _state.update { it.copy(streaming = isStreaming) }\n    }"))
    assertFalse(bridge.contains("_state.update { it.copy(streaming = msg.optBoolean(\"isStreaming\", false)) }"))
    assertTrue(bridge.contains("val wasStreaming = _state.value.streaming"))
    assertTrue(bridge.contains("if (wasStreaming && !isStreaming)"))
    assertTrue(bridge.contains("resetActiveTurnState()\n      finishLearningTurnAndEmitEnded()"))
    assertTrue(bridge.contains("msg.optBooleanAnyOrNull(\"aborted\", \"interrupted\", \"cancelled\", \"canceled\") == true"))
    assertTrue(bridge.contains("msg.cleanStringAny(\"reason\", \"status\", \"stopReason\", \"stop_reason\", \"finishReason\", \"finish_reason\")"))
    assertTrue(bridge.contains("?.let(::nbgNormalizeHanakoStatus)"))
    assertTrue(bridge.contains("\"cancelled_by_user\""))
    assertTrue(bridge.contains("\"stopped_by_user\""))
    assertFalse(bridge.contains("正在调用工具："))
    assertFalse(bridge.contains("上一轮还在输出，稍后再发送。"))
    assertFalse(bridge.contains("WorkspaceFilesLoaded"))
    assertFalse(bridge.contains("WorkspaceFileContentLoaded"))
    assertTrue(bridge.contains("SlashCommandsLoaded"))
    assertTrue(bridge.contains("ModelHealthLoaded"))
    assertTrue(bridge.contains("loadAgentModelConfig"))
    assertTrue(bridge.contains("loadProviders"))
    assertTrue(bridge.contains("getProviders"))
    assertTrue(bridge.contains("checkModelHealth"))
    assertTrue(bridge.contains("loadSlashCommands"))
    assertTrue(bridge.contains("listSlashCommands"))
    assertFalse(bridge.contains("loadWorkspaceFiles"))
    assertFalse(bridge.contains("listWorkspaceFiles"))
    assertFalse(bridge.contains("readWorkspaceFile"))
    assertTrue(bridge.contains("switchAgent"))
    assertTrue(bridge.contains("switchModel"))
    assertTrue(bridge.contains("private suspend fun ensureLiveSessionForRequest("))
    assertTrue(bridge.contains("info: HanakoServerInfo,\n    emitReplacementHistory: Boolean = true"))
    assertTrue(bridge.contains("private fun Throwable.isHanakoSessionCacheMiss(): Boolean"))
    assertTrue(bridge.contains("message.contains(\"不在缓存中\")"))
    assertTrue(bridge.contains("http.switchSession(info, currentPath, currentPath)"))
    assertTrue(bridge.contains("historyStore.localSessionLooksUnhealthy(currentPath)"))
    assertTrue(bridge.contains("skip unhealthy local Hanako session"))
    assertTrue(bridge.contains("skip unhealthy cached Hanako session"))
    assertTrue(bridge.contains("cached session missing in HanakoPro runtime; creating replacement"))
    assertTrue(bridge.contains("preservedHistory = withContext(Dispatchers.IO) { historyStore.readCachedHistory(currentPath) }"))
    assertTrue(bridge.contains("http.createSession(info, null)"))
    assertTrue(bridge.contains("saveCachedHistory(focus.path, historyToKeep)"))
    assertTrue(bridge.contains("并保留本地完整历史"))
    assertTrue(bridge.contains("当前历史会话不在 HanakoPro 缓存中，已创建新会话继续。"))
    assertTrue(bridge.contains("当前历史会话连续上游错误，已创建新会话继续，避免旧上下文再次触发 403。"))
    assertTrue(bridge.contains("val (activeInfo, sessionPath) = ensureLiveSessionForRequestWithRetry(info)"))
    assertTrue(bridge.contains("setSessionPermissionMode"))
    assertTrue(bridge.contains("setThinkingLevel"))
    assertTrue(bridge.contains("val thinkingLevels: List<String> = emptyList()"))
    assertTrue(bridge.contains("val upstreamThinkingLevels = item.extractThinkingLevels()"))
    assertTrue(bridge.contains("upstreamLevels = upstreamThinkingLevels"))
    assertTrue(bridge.contains("nbgSupportedThinkingLevelsForModel("))
    assertTrue(bridge.contains("nbgThinkingSourceForModel(id, provider, baseUrl, api)"))
    assertTrue(bridge.contains("nbgCoerceThinkingLevelForModel(level, currentModelSummary())"))
    assertTrue(bridge.contains("private var lastAgentModelConfig: HanakoAgentModelConfig? = null"))
    assertTrue(bridge.contains("lastAgentModelConfig = config"))
    assertTrue(bridge.contains("private fun currentModelSummary()"))
    assertTrue(bridge.contains("val modelConfigs = JSONArray(modelIds.map"))
    assertTrue(bridge.contains(".put(\"thinkingLevels\", JSONArray(thinkingLevels))"))
    assertTrue(bridge.contains(".put(\"models\", modelConfigs)"))
    assertTrue(bridge.contains("extractThinkingLevels"))
    assertTrue(bridge.contains("optJSONArray(\"thinkingLevels\")"))
    assertTrue(bridge.contains("optJSONArray(\"reasoningLevels\")"))
    assertTrue(bridge.contains("optBoolean(\"supportsThinking\""))
    assertTrue(bridge.contains("optBoolean(\"supportsReasoning\""))
    assertTrue(bridge.contains("parseHanakoConfirmationBlock"))
    assertTrue(bridge.contains("parseHanakoContentBlock"))
    assertTrue(bridge.contains("parseToolStatus"))
    assertTrue(bridge.contains("parseToolStart"))
    assertTrue(bridge.contains("parseToolEnd"))
    assertTrue(bridge.contains("parseFilePreview"))
    assertTrue(bridge.contains("parseFilePreviewFromArgs"))
    assertTrue(bridge.contains("parseFileDiff"))
    assertTrue(bridge.contains("parseTerminalOutput"))
    assertTrue(bridge.contains("\"toolResult\" ->"))
    assertTrue(bridge.contains("parseLocalToolResultMessage"))
    assertTrue(bridge.contains("collectLocalToolCalls"))
    assertTrue(bridge.contains("detailsWithFallbackOutput"))
    assertTrue(bridge.contains("allowTextFallback = name.isTerminalLikeTool()"))
    assertTrue(bridge.contains("localContentText(message.opt(\"content\"))"))
    assertTrue(bridge.contains("enrichHistoryWithLocalToolPreviews"))
    assertTrue(bridge.contains("if (local.messages.size > remote.messages.size)"))
    assertTrue(bridge.contains("using full local history for \$sessionPath"))
    assertTrue(bridge.contains("if (localPreviewCount <= remotePreviewCount) return enrichHistoryWithRuntimeTerminalOutput(info, remote)"))
    assertTrue(bridge.contains("enrichHistoryWithRuntimeTerminalOutput"))
    assertTrue(bridge.contains("enrichTerminalOutput"))
    assertTrue(bridge.contains("fullerHistorySnapshot"))
    assertTrue(bridge.contains("parseHanakoToolGroup"))
    assertTrue(bridge.contains("parseHanakoToolArray"))
    assertTrue(bridge.contains("parseHanakoHistoryTool"))
    assertTrue(bridge.contains("item.rawStringOrNull(\"thinking\")"))
    assertTrue(bridge.contains("item.optJSONArray(\"toolCalls\")"))
    assertTrue(bridge.contains("rawBlock.optString(\"type\") == \"tool_group\""))
    assertTrue(bridge.contains("oldContent"))
    assertTrue(bridge.contains("newContent"))
    assertTrue(bridge.contains("val unifiedDiff: String = \"\""))
    assertTrue(bridge.contains("rawStringAnyOrNull(\"unifiedDiff\", \"diff\", \"patch\")"))
    assertTrue(bridge.contains("HANA_FILE_DIFF_TEXT_LIMIT"))
    assertTrue(bridge.contains("nbgTrimFileDiffText(oldContent)"))
    assertTrue(bridge.contains("nbgTrimFileDiffText(newContent)"))
    assertTrue(bridge.contains("nbgTrimFileDiffText(unifiedDiff)"))
    assertTrue(bridge.contains(".put(\"oldContent\", nbgTrimFileDiffText(oldContent))"))
    assertTrue(bridge.contains("oldContent = nbgTrimFileDiffText(root.rawStringOrNull(\"oldContent\").orEmpty())"))
    assertTrue(bridge.contains("filePathFromUnifiedDiff"))
    assertTrue(bridge.contains("line.startsWith(\"+++ b/\")"))
    assertTrue(bridge.contains("rawStringAnyOrNull(\"previewText\", \"previewChunk\")"))
    assertTrue(bridge.contains("rawStringAnyOrNull(\"content\", \"newContent\", \"replacement\")"))
    assertTrue(bridge.contains("cleanString(\"prepareKey\")"))
    assertTrue(bridge.contains("val toolName: String = \"\""))
    assertTrue(bridge.contains("val filePath: String = \"\""))
    assertTrue(bridge.contains("previewReset"))
    assertTrue(bridge.contains("HANA_FILE_TOUCH_TOOLS"))
    assertTrue(bridge.contains("HANA_FILE_WRITE_PREVIEW_LIMIT"))
    assertTrue(bridge.contains("outputTruncated"))
    assertTrue(bridge.contains("outputPriority"))
    assertTrue(bridge.contains("HANA_TERMINAL_OUTPUT_PRIORITY"))
    assertTrue(bridge.contains("val startDetails = msg.objectAny(\"details\", \"result\", \"data\")"))
    assertTrue(bridge.contains("val terminalOutput = parseTerminalOutput(name, startDetails, args)"))
    assertTrue(bridge.substringAfter("internal fun parseToolStart").substringBefore("internal fun parseToolEnd").contains("terminalOutput = terminalOutput"))
    assertTrue(bridge.substringAfter("internal fun parseToolStart").substringBefore("internal fun parseToolEnd").contains("filePreview = filePreview"))
    assertTrue(bridge.substringAfter("internal fun parseToolEnd").substringBefore("internal fun compactUrl").contains("filePreview = filePreview"))
    assertTrue(bridge.contains("val isTerminalTool = name.isTerminalLikeTool()"))
    assertTrue(bridge.contains("this in setOf(\"bash\", \"shell\", \"exec\", \"run_command\", \"command\")"))
    assertTrue(bridge.contains("rawStringOrNull(\"outputText\")"))
    assertTrue(bridge.contains("rawStringOrNull(\"result\")"))
    assertTrue(bridge.contains("rawStringOrNull(\"logs\")"))
    assertTrue(bridge.contains("rawStringOrNull(\"stdout\")"))
    assertTrue(bridge.contains("rawStringOrNull(\"stderr\")"))
    assertTrue(bridge.contains("rawStringAnyOrNull(\"content\", \"text\")"))
    assertFalse(bridge.contains("cleanString(\"content\") ?: cleanString(\"text\")"))
    assertTrue(bridge.contains("cursorBefore"))
    assertTrue(bridge.contains("sinceCursor"))
    assertTrue(bridge.contains("sliceFrom"))
    assertTrue(bridge.contains("sliceTo"))
    assertTrue(bridge.contains("getTerminalSlice"))
    assertTrue(bridge.contains("getTerminalSnapshot"))
    assertTrue(bridge.contains("interruptTerminalByHuman"))
    assertTrue(bridge.contains("/slice"))
    assertTrue(bridge.contains("/snapshot?tail="))
    assertTrue(bridge.contains("interrupt-by-human"))
    assertTrue(bridge.contains("nbgNormalizeTerminalOutput"))
    assertTrue(bridge.contains("parseHanakoTodos"))
    assertTrue(bridge.contains("parseHanakoSessionFiles"))
    assertTrue(bridge.contains("parseHanakoExternalUserMessage"))
    assertTrue(bridge.contains("isLongRunningHanakoRequest"))
    assertTrue(bridge.contains("isSessionManagementHanakoRequest"))
    assertTrue(bridge.contains("hanakoReadTimeoutMs"))
    assertTrue(bridge.contains("180_000"))
    assertTrue(bridge.contains("75_000"))
    val longRunningRequestBody = bridge.substringAfter("private fun String.isLongRunningHanakoRequest()").substringBefore("private fun androidTeamUiContext")
    assertFalse(longRunningRequestBody.contains("/api/team/tasks"))
    assertTrue(longRunningRequestBody.contains("/api/sessions/new"))
    assertTrue(longRunningRequestBody.contains("/api/sessions/switch"))
    val requestRawBody = bridge.substringAfter("connectTimeoutMs: Int,").substringBefore("private fun parseHanakoHttpError")
    assertTrue(bridge.contains("readTimeoutMs = path.hanakoReadTimeoutMs()"))
    assertTrue(requestRawBody.contains("try {"))
    assertTrue(requestRawBody.contains("finally {"))
    assertTrue(requestRawBody.contains("conn.disconnect()"))
    assertTrue(bridge.contains("CONNECT_WAIT_TIMEOUT_MS = 95_000L"))
    assertTrue(bridge.contains("/api/agents"))
    assertTrue(bridge.contains("/api/agents/switch"))
    assertTrue(bridge.contains("/api/models"))
    assertTrue(bridge.contains("/api/models/health"))
    assertTrue(bridge.contains("/api/providers/summary"))
    assertTrue(bridge.contains("/api/models/set"))
    assertTrue(bridge.contains("/api/models/switch"))
    assertTrue(bridge.contains("/api/session-permission-mode"))
    assertTrue(bridge.contains("/api/session-thinking-level"))
    assertTrue(bridge.contains("/api/commands"))
    assertFalse(bridge.contains("/api/desk/files"))
    assertFalse(bridge.contains("/api/fs/read"))
    assertTrue(bridge.contains("server-info.json"))
    assertTrue(bridge.contains("npm run server"))
    assertTrue(bridge.contains("hanako-server-linux-arm64-node22.nbgpack"))
    assertTrue(bridge.contains("HANAKO_SERVER_PACK_MARKER"))
    assertTrue(bridge.contains("bundledServerPackSignature"))
    assertTrue(bridge.contains("pack_stamp"))
    assertTrue(bridge.contains("deployed_stamp"))
    assertTrue(bridge.contains("bootstrap.js"))
    assertTrue(bridge.contains("busybox\" tar xzf"))
    assertTrue(File("src/main/assets/hanako-server-linux-arm64-node22.nbgpack").isFile)
    val teamBundleProcess = ProcessBuilder(
      "tar",
      "-xOzf",
      "src/main/assets/hanako-server-linux-arm64-node22.nbgpack",
      "./bundle/index.js",
    ).redirectErrorStream(true).start()
    val bundledServer = teamBundleProcess.inputStream.bufferedReader().readText()
    assertEquals(0, teamBundleProcess.waitFor())
    assertFalse(bundledServer.contains("\"/api/team/tasks\""))
    assertFalse(bundledServer.contains("team mode removed"))

    listOf(
      "HanakoCheckpoint",
      "HanakoActivity",
      "HanakoActivitySession",
      "HanakoDevLog",
      "HanakoDiaryEntry",
      "HanakoDiarySnapshot",
      "HanakoMemorySnapshot",
      "HanakoPluginSummary",
      "HanakoPluginsSnapshot",
      "HanakoBridgeSnapshot",
      "HanakoEnvironmentSnapshot",
      "HanakoPromptSnapshot",
      "HanakoTerminalSummary",
      "HanakoTerminalSnapshot",
      "CheckpointsLoaded",
      "ActivitiesLoaded",
      "DevLogsLoaded",
      "DiaryLoaded",
      "MemoriesLoaded",
      "PluginsLoaded",
      "BridgeLoaded",
      "EnvironmentLoaded",
      "PromptsLoaded",
      "listCheckpoints",
      "restoreCheckpoint",
      "listActivities",
      "getActivitySession",
      "listDevLogs",
      "getDiary",
      "writeDiary",
      "getVisibleMemories",
      "getPluginsRuntime",
      "getBridgeOverview",
      "getEnvironment",
      "getPrompts",
      "savePrompts",
      "activity_update",
      "jian_update",
      "devlog",
      "\"skill\"",
      "\"plugin_card\"",
      "\"cron_confirm\"",
      "\"settings_confirm\"",
      "/api/checkpoints",
      "/api/desk/activities",
      "/api/diary/list",
      "/api/memories/visible",
      "/api/plugins/settings",
      "/api/bridge/status",
      "/api/preferences/computer-use",
      "/api/terminal/list",
      "/api/terminal/create",
    ).forEach { assertFalse(bridge.contains(it)) }

    assertTrue(agentUi.contains("hanakoState.sessions"))
    assertTrue(agentUi.contains("NbgChatRunStatus(\"后台预热 HanakoPro\", \"发送前准备中\""))
    assertTrue(agentUi.contains("NbgChatRunStatus(\"本地历史\", \"后台预热中\")"))
    assertTrue(agentUi.contains("hanako.selectSession"))
    assertTrue(agentUi.contains("hanako.createSession"))
    assertTrue(agentUi.contains("val historyState = remember { NbgAgentHistoryState() }"))
    assertTrue(agentUi.contains("val selectedConversationPath by remember { derivedStateOf { historyState.selectedConversationPath } }"))
    assertTrue(agentUi.contains("val requestedConversationPath by remember { derivedStateOf { historyState.requestedConversationPath } }"))
    assertTrue(agentUi.contains("val deferredHistoryLoaded by remember { derivedStateOf { historyState.deferredHistoryLoaded } }"))
    assertTrue(agentUi.contains("private var suppressedHistoryPaths by mutableStateOf<Set<String>>(emptySet())"))
    assertTrue(agentUi.contains("val pendingDeferredHistorySystemMessages = mutableStateListOf<String>()"))
    assertTrue(agentUi.contains("fun appendSystemMessage(text: String): Long"))
    assertTrue(agentUi.contains("historyState.appendDeferredSystemMessageIfNeeded(text)"))
    assertTrue(agentUi.contains("fun appendDeferredSystemMessageIfNeeded(text: String)"))
    assertTrue(agentUi.contains("fun clearDeferredHistoryLoaded()"))
    assertTrue(agentUi.contains("fun clearDeferredHistoryLoaded() {\n    deferredHistoryLoaded = null\n    pendingDeferredHistorySystemMessages.clear()\n  }"))
    assertTrue(agentUi.contains("fun applyHistoryLoaded(event: HanakoChatEvent.HistoryLoaded)"))
    assertTrue(agentUi.contains("val deferredSystemMessages = historyState.beginApplyingHistory(event)"))
    assertTrue(agentUi.contains("fun beginApplyingHistory(event: HanakoChatEvent.HistoryLoaded): List<String>"))
    assertTrue(agentUi.contains("replaceHistory(event.messages)"))
    assertTrue(agentUi.contains("deferredSystemMessages.forEach { appendLocalMessage(NbgAgentRole.System, it) }"))
    assertTrue(agentUi.contains("fun shouldAcceptHistoryLoaded(event: HanakoChatEvent.HistoryLoaded): Boolean"))
    assertTrue(agentUi.contains("historyState.shouldAcceptHistoryLoaded(event)"))
    assertTrue(agentUi.contains("if (requestedPath == HANA_PENDING_NEW_SESSION_PATH && event.sessionPath != selectedConversationPath)"))
    assertTrue(agentUi.contains("Log.i(\"NBG_HANAKO\", \"ignore stale UI history for ${'$'}{event.sessionPath}; active=${'$'}{decision.activePath}\")"))
    assertTrue(agentUi.contains("historyState.clearDeferredHistoryLoaded()\n    streamingControllerState.markIdle()"))
    assertTrue(bridge.contains("internal const val HANA_PENDING_NEW_SESSION_PATH = \"__nbg_pending_new_session__\""))
    assertTrue(agentUi.contains("historyState.requestConversation(it)"))
    assertTrue(agentUi.contains("historyState.requestNewConversation()\n          createSessionWithSelectedUrlApi()"))
    assertTrue(agentUi.contains("historyState.clearDeferredBeforeNewSession()\n    val selected = nbgSelectedUrlApi(savedApis, selectedUrlApiModel)"))
    assertFalse(agentUi.contains("suppressedHistoryPaths = suppressedHistoryPaths + path"))
    assertTrue(agentUi.contains("is HanakoChatEvent.SessionDeleted ->"))
    assertTrue(agentUi.contains("is HanakoChatEvent.SessionSelectFailed ->"))
    assertTrue(agentUi.contains("historyState.applySessionDeleted(event.sessionPath)"))
    assertTrue(agentUi.contains("historyState.applySessionSelectFailed(event.sessionPath, event.previousSessionPath)"))
    assertTrue(agentUi.contains("fun clearDeferredHistoryIfSession(sessionPath: String)"))
    assertTrue(agentUi.contains("requestedConversationPath == sessionPath"))
    assertTrue(agentUi.contains("selectedConversationPath == sessionPath && previousSessionPath != null"))
    assertTrue(agentUi.contains("requestedConversationPath == null && selectedConversationPath != path"))
    assertTrue(agentUi.contains("sendBlocked = hanakoState.selectingSessionPath != null"))
    assertTrue(agentUi.contains("sendBlocked: Boolean"))
    assertTrue(agentUi.contains("sendEnabled = !sendBlocked && (streaming || inputText.isNotBlank())"))
    assertTrue(agentUi.contains("suppressedHistoryPaths = suppressedHistoryPaths + sessionPath"))
    assertTrue(agentUi.contains("event.sessionPath in suppressedHistoryPaths"))
    assertTrue(agentUi.contains("if (requestedPath != null) {"))
    assertTrue(agentUi.contains("if (requestedPath == event.sessionPath)"))
    assertTrue(agentUi.contains("ignore stale UI history"))
    assertTrue(agentUi.contains("ignore suppressed UI history"))
    assertTrue(agentUi.contains("if (!shouldAcceptHistoryLoaded(event)) return@HanakoChatController"))
    assertTrue(agentUi.contains("val streamingControllerState = remember { NbgAgentStreamingControllerState() }"))
    assertTrue(agentUi.contains("fun hasLiveUiWork(): Boolean ="))
    assertTrue(agentUi.contains("streamingControllerState.streaming ||"))
    assertFalse(agentUi.contains("var controllerStreaming by remember"))
    assertTrue(agentUi.contains("message.streaming ||"))
    assertTrue(agentUi.contains("message.role == NbgAgentRole.Tool && message.toolStatus?.running == true"))
    assertTrue(agentUi.contains("streamingTextState.hasPendingDeltas ||"))
    assertTrue(agentUi.contains("toolStatusState.hasPending"))
    assertTrue(agentUi.contains("historyState.deferHistoryLoaded(event)"))
    assertTrue(agentUi.contains("fun deferHistoryLoaded(event: HanakoChatEvent.HistoryLoaded)"))
    assertTrue(agentUi.contains("defer live UI history"))
    assertTrue(agentUi.contains("applyHistoryLoaded(event)"))
    assertTrue(agentUi.contains("fun applyDeferredHistoryIfStillCurrent()"))
    assertTrue(agentUi.contains("val deferred = deferredHistoryLoaded ?: return"))
    assertTrue(agentUi.contains("fun applyDeferredHistoryIfIdle()"))
    assertTrue(agentUi.contains("if (!hasLiveUiWork()) applyDeferredHistoryIfStillCurrent()"))
    assertTrue(agentUi.contains("LaunchedEffect(hanakoState.streaming)"))
    assertTrue(agentUi.contains("streamingControllerState.syncFromHanako(hanakoState.streaming)"))
    assertTrue(agentUi.contains("if (!hanakoState.streaming) applyDeferredHistoryIfIdle()"))
    assertTrue(agentUi.contains("streaming = hanakoState.streaming"))
    assertTrue(agentUi.contains("LaunchedEffect(hanakoState.selectingSessionPath, hanakoState.compressing, hanakoState.sessionPath)"))
    assertTrue(agentUi.contains("historyState.clearPendingNewSessionRequestIfSettled("))
    assertTrue(agentUi.contains("requestedConversationPath == HANA_PENDING_NEW_SESSION_PATH &&"))
    assertTrue(agentUi.contains("selectedConversationPath == sessionPath"))
    assertTrue(agentUi.contains("is HanakoChatEvent.UserMessage -> {\n          streamingControllerState.markStreaming()"))
    assertTrue(agentUi.contains("is HanakoChatEvent.AssistantStarted -> {\n          streamingControllerState.markStreaming()"))
    assertTrue(agentUi.contains("is HanakoChatEvent.AssistantDelta -> {\n          streamingControllerState.markStreaming()"))
    assertTrue(agentUi.contains("is HanakoChatEvent.ThinkingStarted -> {\n          streamingControllerState.markStreaming()"))
    assertTrue(agentUi.contains("is HanakoChatEvent.ThinkingDelta -> {\n          streamingControllerState.markStreaming()"))
    assertTrue(agentUi.contains("is HanakoChatEvent.SystemMessage -> appendSystemMessage(event.text)"))
    assertTrue(agentUi.contains("if (event.tool.running) streamingControllerState.markStreaming()"))
    assertTrue(agentUi.contains("streamingControllerState.markIdle()\n          finishStreamingMessages()"))
    assertTrue(agentUi.contains("HanakoChatEvent.TurnEnded -> {\n          streamingControllerState.markIdle()\n          finishStreamingMessages()"))
    assertTrue(agentUi.contains("applyDeferredHistoryIfStillCurrent()"))
    assertTrue(agentUi.contains("replaceHistory(event.messages)"))
    assertTrue(agentUi.contains("fun replaceHistory(history: List<HanakoHistoryMessage>)"))
    assertTrue(agentUi.contains("contentBlockPatchState.clear()"))
    assertTrue(agentUi.contains("appendExternalUserMessage"))
    val drawerNewConversationBlock = agentUi
      .substringAfter("onNewConversation = {\n          historyState.requestNewConversation()\n          createSessionWithSelectedUrlApi()")
      .substringBefore("onOpenTerminal = {")
    val chatNewConversationBlock = agentUi
      .substringAfter("onHidePet = { petUiState.applyStoreState(petStore.setHidden(true)) },\n        onNewConversation = {")
      .substringBefore("onLoadAgentModelConfig = {")
    val drawerSelectConversationBlock = agentUi
      .substringAfter("onSelectConversation = {")
      .substringBefore("onNewConversation = {")
    assertFalse(drawerNewConversationBlock.contains("clearConversationUi()"))
    assertFalse(chatNewConversationBlock.contains("clearConversationUi()"))
    assertTrue(Regex("historyState.requestNewConversation\\(\\)").findAll(agentUi).count() >= 3)
    assertTrue(agentUi.contains("onCompressFork = {\n          historyState.requestNewConversation()"))
    assertFalse(drawerSelectConversationBlock.contains("selectedConversationPath = it"))
    assertFalse(drawerSelectConversationBlock.contains("clearConversationUi()"))
    assertTrue(drawerSelectConversationBlock.contains("historyState.requestConversation(it)"))
    assertTrue(agentUi.contains("NbgDrawerSearchField"))
    assertTrue(agentUi.contains("Text(\"搜索 HanakoPro 会话\""))
    assertTrue(agentUi.contains("onSearchClear = {"))
    assertTrue(agentUi.contains("onClear = onSearchClear"))
    assertTrue(agentUi.contains("if (localSearchQuery.isBlank()) {\n      onSearch(\"\")"))
    assertTrue(agentUi.contains("onSearch(localSearchQuery.trim())"))
    assertTrue(agentUi.contains("contentDescription = \"清空搜索\""))
    assertFalse(agentUi.contains("NbgSearchPill"))
    assertFalse(agentUi.contains("visible = searchOpen"))
    assertFalse(agentUi.contains("var searchOpen by remember"))
    assertTrue(agentUi.contains("NbgRenameSessionDialog"))
    assertTrue(agentUi.contains("NbgDeleteSessionDialog"))
    assertFalse(agentUi.contains("NbgArchiveSessionDialog"))
    assertFalse(agentUi.contains("NbgRestoreSessionDialog"))
    assertFalse(agentUi.contains("NbgSessionDetailsDialog"))
    assertFalse(agentUi.contains("NbgAgentModelDialog"))
    assertFalse(agentUi.contains("NbgAgentChoiceRow"))
    assertFalse(agentUi.contains("NbgModelChoiceRow"))
    assertTrue(agentUi.contains("NbgComposerModelMenu"))
    assertTrue(agentUi.contains("NbgUrlApiScreen"))
    assertTrue(agentUi.contains("NbgUrlApiEditorDialog"))
    assertTrue(agentUi.contains("NbgExpertReviewReadinessCard"))
    assertTrue(agentUi.contains("nbgExpertReviewModelRefs(entries)"))
    assertTrue(agentUi.contains("只读 · 默认关闭 · 分开展示参考输出 · 最终必须汇总"))
    assertTrue(agentUi.contains("NbgAgentExpertReviewState"))
    assertTrue(agentUi.contains("runExpertReview()"))
    assertTrue(agentUi.contains("apiClient.generateReadOnlyText"))
    assertTrue(agentUi.contains("onRunExpertReview = { runExpertReview() }"))
    assertTrue(agentUi.contains("onCancelExpertReview = { expertReviewState.cancelCurrentRun() }"))
    assertTrue(agentUi.contains("NbgExpertReviewResultPanel"))
    assertTrue(agentUi.contains("运行评审"))
    val urlApiUi = File("src/main/java/com/nbg/android/NbgAgentUrlApiUi.kt").readText()
    val expertReviewState = File("src/main/java/com/nbg/android/NbgAgentExpertReviewState.kt").readText()
    assertTrue(urlApiUi.contains("onCancel = onCancelExpertReview"))
    assertTrue(urlApiUi.contains("label = \"取消\""))
    assertTrue(expertReviewState.contains("fun cancelCurrentRun()"))
    assertTrue(expertReviewState.contains("已取消本次评审"))
    assertTrue(agentUi.contains("NbgApiStore(context)"))
    assertTrue(agentUi.contains("BackHandler(enabled = page != NbgShellPage.Chat"))
    assertTrue(agentUi.contains("BackHandler(enabled = apiEditor.isOpen)"))
    assertTrue(agentUi.contains("NbgShellSubPage("))
    assertTrue(agentUi.contains("NbgRevertTurnDialog"))
    assertFalse(agentUi.contains("NbgArchivedConversationRow"))
    assertTrue(agentUi.contains("NbgSessionConfirmationCard"))
    assertTrue(agentUi.contains("var lastConfirmation by remember"))
    assertTrue(agentUi.contains("var resolvingConfirmationId by remember"))
    assertTrue(agentUi.contains("if (confirmation != null)"))
    assertTrue(agentUi.contains("lastConfirmation = confirmation"))
    assertTrue(agentUi.contains("visible = confirmation != null && confirmation.confirmId != resolvingConfirmationId"))
    assertTrue(agentUi.contains("fadeIn(animationSpec = tween(nbgMotionDuration(140)"))
    assertTrue(agentUi.contains("slideInVertically("))
    assertTrue(agentUi.contains("initialOffsetY = { it / 4 }"))
    assertTrue(agentUi.contains("fadeOut(animationSpec = tween(nbgMotionDuration(100)"))
    assertTrue(agentUi.contains("slideOutVertically("))
    assertTrue(agentUi.contains("targetOffsetY = { it / 5 }"))
    assertTrue(agentUi.contains("lastConfirmation?.let"))
    assertTrue(agentUi.contains("val confirmationState = remember { NbgAgentConfirmationState() }"))
    assertTrue(agentUi.contains("confirmationState.clearPendingConfirmation()"))
    assertTrue(agentUi.contains("confirmationState.requestConfirmation(event.confirmation)"))
    assertTrue(agentUi.contains("confirmationState.resolveConfirmation(event.confirmId)"))
    assertTrue(agentUi.contains("confirmation = confirmationState.pendingConfirmation"))
    assertTrue(agentUi.contains("onRequestRevertLatestTurn = { confirmationState.openRevertTurnConfirm() }"))
    assertTrue(agentUi.contains("if (confirmationState.revertTurnConfirmOpen)"))
    assertTrue(agentUi.contains("onDismiss = { confirmationState.dismissRevertTurnConfirm() }"))
    assertTrue(agentUi.contains("confirmationState.dismissRevertTurnConfirm()\n        hanako.revertLatestTurn()"))
    assertFalse(agentUi.contains("var pendingConfirmation by remember"))
    assertFalse(agentUi.contains("var revertTurnConfirmOpen by remember"))
    assertFalse(agentUi.contains("current.copy(status = event.action)"))
    assertFalse(agentUi.contains("\"confirmed\" -> \"已同意\""))
    assertFalse(agentUi.contains("\"rejected\" -> \"已拒绝\""))
    assertTrue(agentUi.contains("val contentBlockPatchState = remember { NbgAgentContentBlockPatchState() }"))
    assertTrue(agentUi.contains("contentBlockPatchState.consumePatchFor(block)"))
    assertTrue(agentUi.contains("contentBlockPatchState.enqueue(taskId, patch)"))
    assertFalse(agentUi.contains("pendingContentBlockPatches"))
    assertTrue(agentUi.contains("val todoState = remember { NbgAgentTodoState() }"))
    assertTrue(agentUi.contains("todoState.clear()"))
    assertTrue(agentUi.contains("todoState.replace(event.todos)"))
    assertTrue(agentUi.contains("todos = todoState.todos"))
    assertFalse(agentUi.contains("var sessionTodos by remember"))
    assertFalse(agentUi.contains("sessionTodos = event.todos"))
    assertFalse(agentUi.contains("sessionTodos = emptyList()"))
    assertTrue(agentUi.contains("NbgTodoBar"))
    assertTrue(agentUi.contains("NbgRuntimeStatusBar"))
    val runtimeStatusBar = agentUi.substringAfter("private fun NbgRuntimeStatusBar").substringBefore("@Composable\nprivate fun NbgScrollToBottomButton")
    assertFalse(runtimeStatusBar.contains("workspaceLabel?.let"))
    assertFalse(runtimeStatusBar.contains("permissionLabel?.let"))
    assertFalse(runtimeStatusBar.contains("thinkingLabel?.let"))
    assertFalse(agentUi.contains("NbgSessionFilesDialog"))
    assertFalse(agentUi.contains("NbgWorkspaceFilesDialog"))
    assertFalse(agentUi.contains("NbgWorkspaceFileContentDialog"))
    assertFalse(agentUi.contains("NbgSmallIconAction"))
    assertFalse(agentUi.contains("NbgInlineActionChip"))
    assertFalse(agentUi.contains("复制对话文件路径"))
    assertFalse(agentUi.contains("复制工作区文件路径"))
    assertFalse(agentUi.contains("复制工作区文件内容"))
    assertFalse(agentUi.contains("分享工作区文件内容"))
    assertTrue(agentUi.contains("NbgFileShareDialog"))
    assertTrue(agentUi.contains("NbgContentBlockCard"))
    assertTrue(agentUi.contains("NbgThinkingMessageCard"))
    assertTrue(agentUi.contains("NbgToolStatusCard"))
    assertTrue(agentUi.contains("NbgFileWritePreview"))
    assertTrue(agentUi.contains("HANA_MOBILE_STREAM_DELTA_FLUSH_MS = 180L"))
    assertTrue(agentUi.contains("HANA_MOBILE_STREAM_SCROLL_TEXT_BUCKET = HANA_MOBILE_STREAM_TEXT_CHUNK_SIZE * 2"))
    assertTrue(agentUi.contains("HANA_MOBILE_STREAM_TEXT_CHUNK_SIZE = 420"))
    assertTrue(agentUi.contains("HANA_MOBILE_HISTORY_TEXT_CHUNK_SIZE = 900"))
    assertFalse(agentUi.contains("HANA_MOBILE_CHAT_INITIAL_VISIBLE_MESSAGES = 24"))
    assertFalse(agentUi.contains("HANA_MOBILE_CHAT_VISIBLE_MESSAGES_STEP = 24"))
    assertTrue(agentUi.contains("HANA_MOBILE_CHAT_BOTTOM_SCROLL_OFFSET = 1_000_000"))
    assertTrue(agentUi.contains("HANA_MOBILE_CHAT_RESTORE_SCROLL_PASSES = 4"))
    assertTrue(agentUi.contains("HANA_MOBILE_CHAT_RESTORE_SCROLL_DELAY_MS = 32L"))
    assertTrue(agentUi.contains("HANA_MOBILE_TOOL_TEXT_CHUNK_CHARS = 2_400"))
    assertTrue(agentUi.contains("HANA_MOBILE_TOOL_TEXT_CHUNK_LINES = 32"))
    assertTrue(agentUi.contains("HANA_MOBILE_TERMINAL_SCROLL_BUCKET_CHARS = 640"))
    assertTrue(agentUi.contains("HANA_MOBILE_TOOL_STATUS_FLUSH_MS = 220L"))
    assertTrue(agentUi.contains("HANA_MOBILE_LIVE_TOOL_PREVIEW_CHARS = 560"))
    assertTrue(agentUi.contains("HANA_MOBILE_LIVE_TOOL_PREVIEW_LINES = 6"))
    assertTrue(agentUi.contains("NbgCompactTerminalToolCard"))
    assertTrue(agentUi.contains("heightIn(min = 34.dp)"))
    assertTrue(agentUi.contains("terminalOnly = true, livePreview = true"))
    assertTrue(agentUi.contains("SurfaceContainer.copy(alpha = 0.72f)"))
    assertTrue(agentUi.contains("val compact = isTerminalTool"))
    assertTrue(agentUi.contains("val hasTerminalPreview = isTerminalTool && (tool.terminalOutput != null || terminalFallbackText.isNotBlank())"))
    assertTrue(agentUi.contains("isTerminalTool -> hasTerminalPreview && !tool.running"))
    assertTrue(agentUi.contains("label = if (expanded) \"收起\" else if (isTerminalTool) \"输出\" else \"展开\""))
    assertFalse(agentUi.contains("mutableStateOf(tool.running && tool.hasInlinePreview)"))
    assertFalse(agentUi.contains("HANA_MOBILE_CODE_BLOCK_PREVIEW_CHARS"))
    assertFalse(agentUi.contains("HANA_MOBILE_TERMINAL_COLLAPSED_LINES"))
    assertFalse(agentUi.contains("HANA_MOBILE_TERMINAL_EXPANDED_LINES"))
    assertTrue(agentUi.contains("val textChunks: List<String>? = null"))
    assertTrue(agentUi.contains("val streaming: Boolean = false"))
    assertTrue(agentUi.contains("NbgPretextCache"))
    assertTrue(agentUi.contains("fun prepareText(key: String, text: String, streaming: Boolean): NbgPreparedText"))
    assertTrue(agentUi.contains("val cacheKeyPrefix = \"\$key|\$kind|\""))
    assertTrue(agentUi.contains("if (streaming) {\n      entries.keys.removeAll { it.startsWith(cacheKeyPrefix) }\n    }"))
    assertFalse(agentUi.contains("NbgPreparedTextKind.Plain"))
    assertTrue(agentUi.contains("NbgPreparedTextKind.Markdown"))
    assertTrue(agentUi.contains("val kind = NbgPreparedTextKind.Markdown"))
    assertFalse(agentUi.contains("if (streaming) NbgPreparedTextKind.Plain else NbgPreparedTextKind.Markdown"))
    assertTrue(agentUi.contains("NbgMessageListItem"))
    assertTrue(agentUi.contains("NbgMessageListSignature"))
    assertTrue(agentUi.contains("NbgAutoScrollSignature"))
    assertTrue(agentUi.contains("val contentType: String"))
    assertTrue(agentUi.contains("\"tool-\${tool?.kind.orEmpty()}-"))
    assertTrue(agentUi.contains("thinking-done"))
    assertTrue(agentUi.contains("message-chunk-"))
    assertFalse(agentUi.contains("展开完整内容"))
    assertFalse(agentUi.contains("已折叠"))
    assertFalse(agentUi.contains("NbgCollapsedLongMessageBody"))
    assertTrue(agentUi.contains("private val maxEntries: Int = 640"))
    assertTrue(agentUi.contains("private fun nbgPrepareText(text: String, streaming: Boolean): NbgPreparedText"))
    assertTrue(agentUi.contains("private fun nbgMessageListItems("))
    assertTrue(agentUi.contains("private fun nbgDisplayTextChunks(message: NbgAgentMessage): List<String>?"))
    assertTrue(agentUi.contains("private fun historyTextChunksFor(text: String): List<String>"))
    assertFalse(agentUi.contains("private fun nbgVisibleChatMessages("))
    assertFalse(agentUi.contains("private fun nbgHiddenOlderMessageCount("))
    assertTrue(agentUi.contains("showHeader = index == 0"))
    assertTrue(agentUi.contains("val pretextCache = remember { NbgPretextCache() }"))
    assertTrue(agentUi.contains("val visibleMessages = messages"))
    assertFalse(agentUi.contains("derivedStateOf { nbgVisibleChatMessages(messages, visibleMessageLimit) }"))
    assertTrue(agentUi.contains("derivedStateOf { nbgMessageListSignature(visibleMessages) }"))
    assertTrue(agentUi.contains("remember(visibleMessageListSignature)"))
    assertTrue(agentUi.contains("nbgMessageListItems(messages = visibleMessages)"))
    assertFalse(agentUi.contains("derivedStateOf { listState.isScrollInProgress }"))
    assertFalse(agentUi.contains("NbgOlderMessagesButton("))
    assertFalse(agentUi.contains("visibleMessageLimit += HANA_MOBILE_CHAT_VISIBLE_MESSAGES_STEP"))
    assertTrue(agentUi.contains("val animatedMessageKey = nbgLastMessagePrimaryItemKey(visibleItems)"))
    assertTrue(agentUi.contains("internal fun nbgLastMessagePrimaryItemKey(items: List<NbgMessageListItem>): String?"))
    assertTrue(agentUi.contains("items.asReversed().firstOrNull { item ->"))
    assertFalse(agentUi.contains("val animatedMessageKey = visibleItems.lastOrNull()?.key"))
    assertTrue(agentUi.contains("val restoredMessages = buildList"))
    assertTrue(agentUi.contains("val messageState = remember { NbgAgentMessageState() }"))
    assertTrue(agentUi.contains("val messages = messageState.messages"))
    assertTrue(agentUi.contains("private val messageIndexById = mutableMapOf<Long, Int>()"))
    assertTrue(agentUi.contains("private val toolMessageIndexByKey = mutableMapOf<String, Int>()"))
    assertTrue(agentUi.contains("fun rebuildMessageIndex()"))
    assertTrue(agentUi.contains("toolMessageIndexByKey.clear()"))
    assertTrue(agentUi.contains("toolMessageKey(message)?.let { key ->"))
    assertTrue(agentUi.contains("fun findMessageIndexById(messageId: Long): Int = messageState.findMessageIndexById(messageId)"))
    assertTrue(agentUi.contains("fun findToolMessageIndexByKey(key: String): Int = messageState.findToolMessageIndexByKey(key)"))
    assertTrue(agentUi.contains("fun appendMessage(message: NbgAgentMessage): Long = messageState.appendMessage(message)"))
    assertTrue(agentUi.contains("fun replaceMessages(nextMessages: List<NbgAgentMessage>) = messageState.replaceMessages(nextMessages)"))
    assertTrue(agentUi.contains("fun replaceAllMessages(transform: (NbgAgentMessage) -> NbgAgentMessage) = messageState.replaceAllMessages(transform)"))
    assertTrue(agentUi.contains("fun toolMessageKey(message: NbgAgentMessage): String?"))
    assertTrue(agentUi.contains("replaceMessages(restoredMessages)"))
    assertTrue(agentUi.contains("messageState.reseedLocalMessageIds(restoredMessages)"))
    assertFalse(agentUi.contains("messages.addAll(restoredMessages)"))
    assertTrue(agentUi.contains("val index = findMessageIndexById(messageId)"))
    assertTrue(agentUi.contains("val index = findMessageIndexById(thinkingId)"))
    assertTrue(agentUi.contains("val index = findToolMessageIndexByKey(key).takeIf { it >= 0 } ?: messages.indexOfFirst { message ->"))
    assertTrue(agentUi.contains("toolMessageIndexByKey[key] = index"))
    assertTrue(agentUi.contains("replaceAllMessages { message ->"))
    assertFalse(agentUi.contains("val index = messages.indexOfFirst { it.id == messageId }"))
    assertFalse(agentUi.contains("val index = messages.indexOfFirst { it.id == thinkingId }"))
    assertFalse(agentUi.contains("messages.replaceAll { message ->"))
    assertFalse(agentUi.contains("nbgPrepareMessageListItems"))
    assertFalse(agentUi.contains("val preparedText: NbgPreparedText? = null"))
    assertFalse(agentUi.contains("val messageSnapshot = messages.toList()"))
    assertFalse(agentUi.contains("remember(messages) { nbgMessageListItems(messages) }"))
    assertTrue(agentUi.contains("appendStreamingTextChunk"))
    assertTrue(agentUi.contains("appendStreamingTextChunksAfterPrefix"))
    assertTrue(agentUi.contains("streamingTextChunksFor"))
    assertTrue(agentUi.contains("internal fun appendStreamingTextChunk(previousText: String, delta: String): List<String>"))
    assertTrue(agentUi.contains("streamingTextChunksFor(previousText + delta)"))
    assertTrue(agentUi.contains("nextText.startsWith(previousText)"))
    assertTrue(agentUi.contains("val stablePrefix = chunks.dropLast(1)"))
    assertTrue(agentUi.contains("val appendedText = nextText.substring(previousText.length)"))
    assertTrue(agentUi.contains("return stablePrefix + nextTailChunks"))
    assertTrue(agentUi.contains("markdownAwareHistoryTextChunksFor(text, HANA_MOBILE_STREAM_TEXT_CHUNK_SIZE)"))
    assertFalse(agentUi.contains("text.chunked(HANA_MOBILE_STREAM_TEXT_CHUNK_SIZE)"))
    assertFalse(agentUi.contains("private fun nbgTailLinesText("))
    assertFalse(agentUi.contains("private fun nbgHasMoreLines("))
    assertFalse(agentUi.contains("compactRendering = compactRendering"))
    assertFalse(agentUi.contains("tool.hasInlinePreview && !compactRendering"))
    assertFalse(agentUi.contains("val showInlinePreview = tool.hasInlinePreview"))
    assertFalse(agentUi.contains("val shouldSplitToolOutput = message.role == NbgAgentRole.Tool && tool?.running != true"))
    assertTrue(agentUi.contains("val shouldSplitToolOutput = false"))
    assertTrue(agentUi.contains("terminalOutput = item.terminalOutput"))
    assertTrue(agentUi.contains("filePreview = item.filePreview"))
    assertTrue(agentUi.contains("fileDiffLines = item.fileDiffLines"))
    assertTrue(agentUi.contains("private fun nbgTerminalOutputChunks(terminal: HanakoTerminalOutput): List<HanakoTerminalOutput>"))
    assertTrue(agentUi.contains("private fun nbgFilePreviewChunks(preview: HanakoFilePreview): List<HanakoFilePreview>"))
    assertTrue(agentUi.contains("private fun nbgFileDiffLineChunks(diff: HanakoFileDiff): List<List<String>>"))
    assertTrue(agentUi.contains("private fun nbgTextChunksByLine("))
    assertTrue(agentUi.contains("HANA_MOBILE_TERMINAL_CHUNK_CHARS = 1_800"))
    assertTrue(agentUi.contains("HANA_MOBILE_TERMINAL_CHUNK_LINES = 28"))
    assertTrue(agentUi.contains("if (delta.isEmpty()) return streamingTextChunksFor(previousText)"))
    assertTrue(agentUi.contains("markdownAwareHistoryTextChunksFor(text, HANA_MOBILE_STREAM_TEXT_CHUNK_SIZE)"))
    assertFalse(agentUi.contains("if (delta.isBlank()) return"))
    assertFalse(agentUi.contains("if (text.isEmpty()) emptyList() else text.chunked"))
    assertFalse(agentUi.contains("if (text.isBlank()) emptyList()"))
    assertTrue(agentUi.contains("val streamingTextState = remember { NbgAgentStreamingTextState() }"))
    assertTrue(agentUi.contains("private val pendingAssistantDeltas = mutableMapOf<Long, StringBuilder>()"))
    assertTrue(agentUi.contains("private val pendingThinkingDeltas = mutableMapOf<Long, StringBuilder>()"))
    assertTrue(agentUi.contains("private val thinkingRawTexts = mutableMapOf<Long, StringBuilder>()"))
    assertTrue(agentUi.contains("streamingTextState.hasPendingDeltas"))
    assertTrue(agentUi.contains("fun appendThinkingVisibleText(thinkingId: Long, rawDelta: String): String"))
    assertTrue(agentUi.contains("fun replaceThinkingVisibleText(thinkingId: Long, rawText: String): String"))
    assertTrue(agentUi.contains("NbgAgentRole.Thinking -> appendThinkingVisibleText(messageId, delta)"))
    assertFalse(agentUi.contains("NbgAgentRole.Thinking -> nbgThinkingTextForAndroidDisplay(current.text + delta)"))
    assertTrue(agentUi.contains("fun pruneFinishedAssistantRawTexts()"))
    assertTrue(agentUi.contains("streamingTextState.pruneFinishedAssistantRawTexts(streamingAssistantIds)"))
    assertTrue(agentUi.contains("fun pruneFinishedAssistantRawTexts(streamingAssistantIds: Set<Long>)"))
    assertTrue(agentUi.contains("assistantRawTexts.keys.removeAll { it !in streamingAssistantIds }"))
    assertTrue(agentUi.substringAfter("fun finishStreamingMessages() {").substringBefore("fun openFileShareDialog").contains("pruneFinishedAssistantRawTexts()"))
    val agentUiMainForFlushScheduler = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    assertTrue(agentUi.contains("val flushSchedulerState = remember { NbgAgentFlushSchedulerState() }"))
    assertTrue(agentUi.contains("flushSchedulerState.scheduleStreamingDeltaFlush"))
    assertTrue(agentUi.contains("flushSchedulerState.completeStreamingDeltaFlush()"))
    assertTrue(agentUi.contains("flushSchedulerState.cancelStreamingDeltaFlush()"))
    assertTrue(agentUi.contains("flushSchedulerState.scheduleToolStatusFlush"))
    assertTrue(agentUi.contains("flushSchedulerState.completeToolStatusFlush()"))
    assertTrue(agentUi.contains("flushSchedulerState.cancelToolStatusFlush()"))
    assertTrue(agentUi.contains("flushSchedulerState.cancelAll()"))
    assertTrue(agentUi.contains("fun replaceHistory(history: List<HanakoHistoryMessage>) {\n    flushSchedulerState.cancelStreamingDeltaFlush()\n    flushSchedulerState.cancelToolStatusFlush()\n    streamingTextState.clear()"))
    assertFalse(agentUiMainForFlushScheduler.contains("var deltaFlushJob by remember"))
    assertFalse(agentUiMainForFlushScheduler.contains("var toolStatusFlushJob by remember"))
    assertFalse(agentUiMainForFlushScheduler.contains("deltaFlushJob?.cancel()"))
    assertFalse(agentUiMainForFlushScheduler.contains("toolStatusFlushJob?.cancel()"))
    assertTrue(agentUi.contains("NbgAgentToolStatusState"))
    assertTrue(agentUi.contains("toolStatusState.drain()"))
    assertTrue(agentUi.contains("toolStatusState.enqueue(tool)"))
    assertTrue(agentUi.contains("flushStreamingDeltas"))
    assertTrue(agentUi.contains("flushToolStatuses"))
    assertTrue(agentUi.contains("enqueueToolStatus"))
    assertTrue(agentUi.contains("delay(HANA_MOBILE_TOOL_STATUS_FLUSH_MS)"))
    assertTrue(agentUi.contains("fun finishStreamingMessages() {\n    flushSchedulerState.cancelStreamingDeltaFlush()\n    flushStreamingDeltas()"))
    assertTrue(agentUi.contains("enqueueAssistantDelta"))
    assertTrue(agentUi.contains("enqueueThinkingDelta"))
    assertTrue(agentUi.contains("delay(HANA_MOBILE_STREAM_DELTA_FLUSH_MS)"))
    assertTrue(bridge.contains("HanakoChatEvent.ThinkingDelta(id, delta)"))
    assertFalse(bridge.contains("ThinkingDelta(id, nbgThinkingTextForAndroidDisplay(delta))"))
    assertTrue(agentUi.contains("private fun nbgAutoScrollSignature(messages: List<NbgAgentMessage>): NbgAutoScrollSignature"))
    assertTrue(agentUi.contains("private fun nbgConversationScrollKey("))
    assertTrue(agentUi.contains("textHash = message.text.hashCode()"))
    assertTrue(agentUi.contains("contentBlockHash = block?.let { nbgCombinedHash(it.type, it.title, it.subtitle, it.detail, it.status, it.taskId) } ?: 0"))
    assertTrue(agentUi.contains("toolMetaHash = tool?.let { nbgCombinedHash(it.kind, it.toolName, it.filePath, it.title, it.subtitle, it.detail, it.status) } ?: 0"))
    assertTrue(agentUi.contains("terminalHash = terminal?.output?.hashCode() ?: 0"))
    assertTrue(agentUi.contains("filePreviewHash = filePreview?.previewText?.hashCode() ?: 0"))
    assertTrue(agentUi.contains("fileDiffHash = fileDiff?.let { nbgCombinedHash(it.unifiedDiff, it.oldContent, it.newContent) } ?: 0"))
    assertTrue(agentUi.contains("internal fun nbgCombinedHash(vararg values: String): Int"))
    assertFalse(agentUi.contains("it.unifiedDiff + it.oldContent + it.newContent"))
    assertTrue(agentUi.contains("lastTextBucket = (last?.text?.length ?: 0) / HANA_MOBILE_STREAM_SCROLL_TEXT_BUCKET"))
    assertTrue(agentUi.contains("contentBlockHash = block?.let { nbgCombinedHash(it.type, it.title, it.subtitle, it.detail, it.status, it.taskId) } ?: 0"))
    assertTrue(agentUi.contains("toolMetaHash = tool?.let { nbgCombinedHash(it.kind, it.toolName, it.filePath, it.title, it.subtitle, it.detail, it.status) } ?: 0"))
    assertTrue(agentUi.contains("terminalOutputBucket = (terminal?.output?.length ?: 0) / HANA_MOBILE_TERMINAL_SCROLL_BUCKET_CHARS"))
    assertTrue(agentUi.contains("LaunchedEffect(autoScrollSignature, followOutput)"))
    assertTrue(agentUi.contains("LaunchedEffect(conversationScrollKey, visibleItems.isNotEmpty())"))
    assertFalse(agentUi.contains("LaunchedEffect(visibleItems.size, hiddenOlderMessageCount, scrollBucket, followOutput)"))
    assertTrue(agentUi.contains("conversationPath = selectedConversationPath ?: hanakoState.sessionPath"))
    assertTrue(agentUi.contains("conversationPath: String?"))
    assertTrue(agentUi.contains("val historyRenderVersion by remember { derivedStateOf { historyState.historyRenderVersion } }"))
    assertTrue(agentUi.contains("var historyRenderVersion by mutableStateOf(0L)"))
    assertTrue(agentUi.contains("historyRenderVersion += 1L"))
    assertTrue(agentUi.contains("historyRenderVersion = 0L"))
    assertTrue(agentUi.contains("historyRenderVersion = historyRenderVersion"))
    assertTrue(agentUi.contains("historyRenderVersion: Long"))
    assertTrue(agentUi.contains("nbgConversationScrollKey(conversationPath, visibleMessages, historyRenderVersion)"))
    assertTrue(agentUi.contains("return \"session:${'$'}path:${'$'}historyRenderVersion\""))
    assertTrue(agentUi.contains("val conversationScrollKey by remember"))
    assertTrue(agentUi.contains("var followOutput by remember(conversationScrollKey)"))
    assertTrue(agentUi.contains("val bottomAnchorIndex by remember"))
    assertTrue(agentUi.contains("val isAtBottom by remember"))
    assertTrue(agentUi.contains("derivedStateOf { listState.isAtChatBottom() }"))
    assertTrue(agentUi.contains("derivedStateOf { visibleItems.isNotEmpty() && !isAtBottom }"))
    assertTrue(agentUi.contains("snapshotFlow { listState.isScrollInProgress to listState.isAtChatBottom() }"))
    assertFalse(agentUi.contains("isNearBottom(bufferItems = 1)"))
    assertTrue(agentUi.contains("repeat(HANA_MOBILE_CHAT_RESTORE_SCROLL_PASSES)"))
    assertTrue(agentUi.contains("delay(HANA_MOBILE_CHAT_RESTORE_SCROLL_DELAY_MS)"))
    assertTrue(agentUi.contains("listState.scrollToChatBottom(bottomAnchorIndex)"))
    assertTrue(agentUi.contains("private suspend fun LazyListState.scrollToChatBottom(bottomAnchorIndex: Int)"))
    assertTrue(agentUi.contains("scrollToItem(bottomAnchorIndex, scrollOffset = HANA_MOBILE_CHAT_BOTTOM_SCROLL_OFFSET)"))
    assertTrue(agentUi.contains("private fun LazyListState.isAtChatBottom(): Boolean"))
    assertTrue(agentUi.contains("item(key = \"chat-bottom-anchor\", contentType = \"bottom-anchor\")"))
    assertFalse(agentUi.contains("listState.animateScrollToItem(bottomAnchorIndex, scrollOffset = HANA_MOBILE_CHAT_BOTTOM_SCROLL_OFFSET)"))
    assertTrue(agentUi.contains("NbgScrollToBottomButton"))
    assertTrue(agentUi.contains(".zIndex(2f)"))
    assertTrue(agentUi.contains("contentDescription = \"移动到底部\""))
    assertTrue(agentUi.contains(".size(44.dp)"))
    assertTrue(agentUi.contains(".background(NbgAgentColors.SurfaceContainerHigh, RoundedCornerShape(14.dp))"))
    assertFalse(agentUi.contains("bottom = innerPadding.calculateBottomPadding() + 96.dp"))
    assertTrue(agentUi.contains("bottom = innerPadding.calculateBottomPadding(),"))
    assertTrue(agentUi.contains(".imePadding()"))
    assertTrue(agentUi.contains("NbgStreamingMessageText"))
    assertTrue(agentUi.contains("items = visibleItems"))
    assertTrue(agentUi.contains("key = { it.key }"))
    assertTrue(agentUi.contains("contentType = { it.contentType }"))
    assertTrue(agentUi.contains("NbgMessageEnterContainer(enabled = item.showHeader && item.key == animatedMessageKey)"))
    assertTrue(agentUi.contains("private fun NbgMessageEnterContainer("))
    assertTrue(agentUi.contains("if (!enabled) {"))
    assertTrue(agentUi.contains("label = \"message enter alpha\""))
    assertTrue(agentUi.contains("label = \"message enter offset\""))
    assertTrue(agentUi.contains("nbgMotionDuration(150)"))
    assertTrue(agentUi.contains("nbgMotionDuration(180)"))
    assertTrue(agentUi.contains("preparedTextKey = item.key"))
    assertTrue(agentUi.contains("pretextCache = pretextCache"))
    assertTrue(agentUi.contains("animatePreparedBlocks = item.key == animatedMessageKey"))
    assertFalse(agentUi.contains("historyNativeText = item.key != animatedMessageKey"))
    assertTrue(agentUi.contains("historyNativeText = false"))
    assertFalse(agentUi.contains("historyNativeText = item.key != animatedMessageKey || compactRendering"))
    assertTrue(agentUi.contains("historyNativeText: Boolean = false"))
    assertTrue(agentUi.contains("if (historyNativeText && !message.streaming)"))
    assertFalse(agentUi.contains("if ((historyNativeText && !message.streaming) || compactRendering)"))
    assertTrue(agentUi.contains("NbgNativeHistoryText(text = stripNbgInternalMood(chunk))"))
    val streamingMessageTextBody = agentUi.substringAfter("internal fun NbgStreamingMessageText(").substringBefore("@Composable\ninternal fun NbgStreamingTailIndicator")
    assertTrue(
      streamingMessageTextBody.indexOf("if (historyNativeText && !message.streaming)") <
        streamingMessageTextBody.indexOf("if (!message.streaming)"),
    )
    assertTrue(agentUi.contains("private fun NbgNativeHistoryText(text: String)"))
    val nativeHistoryTextBody = agentUi.substringAfter("private fun NbgNativeHistoryText(text: String)").substringBefore("@Composable\nprivate fun NbgPreparedMessageText")
    assertFalse(nativeHistoryTextBody.contains("AndroidView("))
    assertFalse(nativeHistoryTextBody.contains("TextView(context).apply"))
    assertTrue(nativeHistoryTextBody.contains("BasicText("))
    assertTrue(nativeHistoryTextBody.contains("modifier = Modifier.fillMaxWidth()"))
    assertTrue(agentUi.contains("remember(chunkKey, chunk, message.streaming)"))
    assertTrue(agentUi.contains("pretextCache?.prepareText(chunkKey, chunk, message.streaming)"))
    assertTrue(agentUi.contains("NbgPreparedMessageText(preparedText, animateBlocks = animatePreparedBlocks)"))
    assertTrue(agentUi.contains("NbgPreparedTextBlockContent(block, index)"))
    assertFalse(agentUi.contains("preparedText = item.preparedText"))
    assertTrue(agentUi.contains("parseNbgInlineMarkdown(displayText)"))
    assertTrue(agentUi.contains("NbgPreparedTextBlock.CodeBlock"))
    assertTrue(agentUi.contains("NbgPreparedTextBlock.HeadingBlock"))
    assertTrue(agentUi.contains("NbgPreparedTextBlock.QuoteBlock"))
    assertTrue(agentUi.contains("NbgPreparedTextBlock.ListBlock"))
    assertTrue(agentUi.contains("NbgPreparedTextBlock.DividerBlock"))
    assertTrue(agentUi.contains("private fun nbgPreparedTextBlocks(text: String)"))
    assertTrue(agentUi.contains("leadingTrimmed.startsWith(\"```\")"))
    assertTrue(agentUi.contains("isNbgMarkdownHeadingLine(leadingTrimmed)"))
    assertTrue(agentUi.contains("line.getOrNull(level)?.isWhitespace() == true"))
    assertTrue(agentUi.contains("leadingTrimmed.startsWith(\">\")"))
    assertTrue(agentUi.contains("NbgPreparedTextBlockView(block = block, index = index, animate = animateBlocks)"))
    assertTrue(agentUi.contains("AnimatedVisibility("))
    assertTrue(agentUi.contains("slideInVertically("))
    assertFalse(agentUi.contains("NbgAgentColors.AssistantRail"))
    assertTrue(agentUi.contains("NbgPreparedCodeBlock(block.text, index)"))
    assertTrue(agentUi.contains("fontFamily = FontFamily.Monospace"))
    assertTrue(agentUi.contains("NbgAgentColors.CodeBlock"))
    assertTrue(agentUi.contains("private fun NbgPreparedTable(block: NbgPreparedTextBlock.TableBlock)"))
    assertTrue(agentUi.contains(".background(NbgAgentColors.InlinePanel, RoundedCornerShape(8.dp))"))
    assertTrue(agentUi.contains("isNbgMarkdownTableDivider"))
    assertTrue(agentUi.contains("fontSize = 11.sp"))
    assertFalse(agentUi.contains("val shouldCollapse = text.length > HANA_MOBILE_CODE_BLOCK_PREVIEW_CHARS"))
    assertFalse(agentUi.contains(".heightIn(max = if (expanded) 360.dp else 220.dp)"))
    assertFalse(agentUi.contains("text = if (expanded) \"收起代码\" else \"展开代码\""))
    assertTrue(agentUi.contains("BasicText(\n        text = text"))
    assertTrue(agentUi.contains("finishStreamingMessages"))
    val finishThinkingBlock = agentUi.substringAfter("fun finishThinking(thinkingId: Long) {")
      .substringBefore("fun upsertToolStatus")
    assertTrue(finishThinkingBlock.contains("streamingTextState.removeThinkingRawText(thinkingId)"))
    assertTrue(finishThinkingBlock.indexOf("streamingTextState.removeThinkingRawText(thinkingId)") < finishThinkingBlock.indexOf("applyDeferredHistoryIfIdle()"))
    assertFalse(agentUi.contains("remember(message.id, index, chunk, message.streaming)"))
    assertTrue(agentUi.contains("if (message.done && message.text.isBlank()) {"))
    assertFalse(agentUi.contains("nbgIsHiddenThinkingNotice"))
    assertTrue(agentUi.contains("NbgCompactThinkingDoneRow()"))
    assertTrue(agentUi.contains("private fun NbgCompactThinkingDoneRow()"))
    assertTrue(agentUi.contains("contentDescription = \"思考完成\""))
    assertTrue(agentUi.contains("message.role == NbgAgentRole.Thinking"))
    assertTrue(agentUi.contains("showHeader: Boolean = true"))
    assertTrue(agentUi.contains("text = if (message.done) \"思考完成\" else \"正在思考\""))
    assertFalse(agentUi.contains("val count = if (compactRendering) 1 else 2"))
    assertFalse(agentUi.contains("val charLimit = if (compactRendering) 220 else HANA_MOBILE_THINKING_PREVIEW_CHARS"))
    assertFalse(agentUi.contains("chunks.takeLast(count).map"))
    assertFalse(agentUi.contains("chunk.takeLast(charLimit).trimStart()"))
    assertFalse(agentUi.contains("previewChunks = chunks.takeLast(if (message.done)"))
    assertFalse(agentUi.contains("LaunchedEffect(messages.size, messages.lastOrNull()?.text)"))
    assertFalse(agentUi.contains("listState.animateScrollToItem(messages.lastIndex)"))
    assertTrue(agentUi.contains("canMergeToolStatus"))
    assertTrue(agentUi.contains("mergeToolStatus"))
    assertTrue(agentUi.contains("mergeFilePreview"))
    assertTrue(agentUi.contains("mergeTerminalOutput"))
    assertTrue(agentUi.contains("mergeTerminalOutputText"))
    assertTrue(agentUi.contains("terminalOutput?.sessionId"))
    assertTrue(agentUi.contains("sliceLabel"))
    assertTrue(agentUi.contains("HANA_MOBILE_FILE_PREVIEW_LIMIT"))
    assertTrue(agentUi.contains("HANA_MOBILE_TERMINAL_OUTPUT_LIMIT"))
    assertTrue(agentUi.contains("HANA_MOBILE_TERMINAL_OVERLAP_SCAN_LIMIT = 4_096"))
    assertTrue(agentUi.contains("mergeOverlappingText(currentOutput, nextOutput)"))
    assertTrue(agentUi.contains("nextOutput.contains(currentOutput)"))
    assertTrue(agentUi.contains("internal fun mergeOverlappingText(currentOutput: String, nextOutput: String): String"))
    assertTrue(agentUi.contains("minOf(currentOutput.length, nextOutput.length, HANA_MOBILE_TERMINAL_OVERLAP_SCAN_LIMIT)"))
    assertTrue(agentUi.contains("takeLast(HANA_MOBILE_TERMINAL_OUTPUT_LIMIT)"))
    assertFalse(agentUi.contains("preferNextOutput"))
    assertTrue(agentUi.contains("NbgFileDiffPreview"))
    assertTrue(agentUi.contains("NbgTerminalOutputPreview"))
    assertTrue(agentUi.contains("NbgFileWritePreviewChunkCard"))
    assertTrue(agentUi.contains("NbgFileDiffChunkCard"))
    assertTrue(agentUi.contains("nbgDiffPreviewLines"))
    assertTrue(agentUi.contains("nbgFullDiffLines"))
    assertTrue(agentUi.contains("nbgDiffStats"))
    assertTrue(agentUi.contains("nbgUnifiedDiffPreviewLines"))
    assertTrue(agentUi.contains("nbgUnifiedDiffStats"))
    assertTrue(agentUi.contains("diff.unifiedDiff.isNotBlank()"))
    assertTrue(agentUi.contains("message.toolStatus"))
    assertTrue(agentUi.contains("message.role == \"tool\""))
    assertTrue(agentUi.contains("message.role == \"thinking\""))
    assertTrue(agentUi.contains("failRunningTools"))
    assertTrue(agentUi.contains("HanakoChatEvent.ToolInterrupted -> {\n          streamingControllerState.markIdle()\n          failRunningTools()\n          finishStreamingMessages()\n          applyDeferredHistoryIfStillCurrent()"))
    assertTrue(agentUi.contains("if (!tool.hasVisibleToolStatus()) return"))
    assertTrue(agentUi.contains("private fun HanakoToolStatus.hasVisibleToolStatus(): Boolean"))
    assertTrue(agentUi.contains("internal fun pendingToolStatusKey(tool: HanakoToolStatus): String"))
    assertTrue(agentUi.contains("title.isNotBlank() && title != \"工具调用\""))
    assertTrue(agentUi.contains("toolName.isTerminalLikeTool()"))
    assertTrue(agentUi.contains("message.role == NbgAgentRole.Tool && message.toolStatus?.running == true"))
    assertTrue(agentUi.contains("history.forEachIndexed { historyIndex, message ->"))
    assertTrue(agentUi.contains("val uiId = HANA_MOBILE_HISTORY_MESSAGE_ID_BASE + historyIndex.toLong()"))
    assertTrue(agentUi.contains("internal const val HANA_MOBILE_HISTORY_MESSAGE_ID_BASE = 1_000_000_000L"))
    assertFalse(agentUi.contains("val uiId = 10_000L + historyIndex.toLong()"))
    assertTrue(agentUi.contains("private var localMessageId = -1L"))
    assertTrue(agentUi.contains("fun resetLocalMessageIds()"))
    assertTrue(agentUi.contains("localMessageId = -1L"))
    assertTrue(agentUi.contains("fun nextLocalMessageId(): Long = messageState.nextLocalMessageId()"))
    assertTrue(agentUi.contains("fun nextLocalMessageId(): Long = localMessageId--"))
    assertTrue(agentUi.contains("val id = nextLocalMessageId()"))
    assertTrue(agentUi.contains("messageState.reseedLocalMessageIds(restoredMessages)"))
    assertTrue(agentUi.contains("localMessageId = (existingMessages.map { it.id }.filter { it < 0L }.minOrNull() ?: 0L) - 1L"))
    assertTrue(agentUi.contains("id = if (index >= 0) messages[index].id else nextLocalMessageId()"))
    assertFalse(
      agentUi.substringAfter("fun replaceHistory(history: List<HanakoHistoryMessage>) {")
        .substringBefore("fun applyHistoryLoaded")
        .contains("localMessageId = 1L"),
    )
    assertFalse(agentUi.contains("localMessageId++"))
    assertTrue(agentUi.contains("tool?.asRestoredHistoryToolStatus(key)"))
    assertTrue(agentUi.contains("private fun HanakoToolStatus.asRestoredHistoryToolStatus(key: String): HanakoToolStatus"))
    assertTrue(agentUi.contains("val messageKey = nbgMessageListKey(message, messageIndex)"))
    assertTrue(agentUi.contains("private fun nbgMessageListKey(message: NbgAgentMessage, messageIndex: Int): String"))
    assertTrue(agentUi.contains("未收到结束事件"))
    assertTrue(agentUi.contains("removeAssistantMessage"))
    assertTrue(agentUi.contains("AssistantRemoved"))
    assertTrue(agentUi.contains("NbgAgentRole.Tool"))
    assertTrue(agentUi.contains("upsertToolStatus"))
    assertTrue(agentUi.contains("contentDescription = \"过程 \$title \$subtitle\""))
    assertTrue(agentUi.contains("nbgCompactToolDetail(tool.subtitle)"))
    assertTrue(agentUi.contains("let(::nbgCompactToolDetail)"))
    assertTrue(agentUi.contains("private fun nbgCompactToolDetail(detail: String): String"))
    assertTrue(agentUi.contains("!line.startsWith(\"{\")"))
    assertTrue(agentUi.contains("!line.contains(\"\\\":{\")"))
    assertTrue(agentUi.contains("NbgAgentColors.ToolMutedText"))
    assertTrue(agentUi.contains("private fun nbgToolStatusDotColor(tool: HanakoToolStatus): Color"))
    assertTrue(agentUi.contains("val dotColor = targetDotColor"))
    assertFalse(agentUi.contains("val textColor = targetTextColor"))
    assertFalse(agentUi.contains("val dotSize = 6.dp"))
    assertTrue(agentUi.contains("val dotAlpha = if (tool.running) 0.9f else 0.92f"))
    assertTrue(agentUi.contains("NbgToolVisualizationState.Running"))
    assertTrue(agentUi.contains("NbgToolVisualizationState.Failed"))
    assertTrue(agentUi.contains("NbgToolVisualizationState.RestoredIncomplete"))
    assertTrue(agentUi.contains("NbgToolVisualizationState.Succeeded -> NbgAgentColors.StatusGreen"))
    assertFalse(agentUi.contains("val dotScale ="))
    assertFalse(agentUi.contains("scaleX = dotScale"))
    assertTrue(agentUi.contains("var showDetailsDialog by remember(tool.key)"))
    assertTrue(agentUi.contains("NbgToolDetailsDialog("))
    assertTrue(agentUi.contains("showDetailsDialog = true"))
    assertTrue(agentUi.contains("NbgToolInlinePreviews(tool, livePreview = tool.running)"))
    assertTrue(agentUi.contains("val showFilePreview = !terminalOnly && tool.fileDiff == null"))
    assertTrue(agentUi.contains("if (showFilePreview) tool.filePreview?.let { preview ->"))
    assertTrue(agentUi.contains("NbgFileWritePreview(preview = preview, running = tool.running, livePreview = livePreview)"))
    assertTrue(agentUi.contains("if (!terminalOnly) tool.fileDiff?.let { diff ->"))
    assertTrue(agentUi.contains("NbgFileDiffPreview(diff, livePreview = livePreview)"))
    assertTrue(agentUi.contains("nbgDiffStatsFromShownLines(lines)"))
    assertTrue(agentUi.contains("if (livePreview) nbgDiffStatsFromShownLines(lines) else nbgDiffStats(diff)"))
    assertTrue(agentUi.contains("tool.terminalOutput?.let { terminal ->"))
    assertTrue(agentUi.contains("NbgTerminalOutputPreview(terminal, livePreview = livePreview)"))
    assertTrue(agentUi.contains("private fun nbgLiveToolPreviewText("))
    assertTrue(agentUi.contains("nbgLiveToolPreviewText(terminal.output.ifBlank"))
    assertFalse(agentUi.contains("compactTerminal: Boolean"))
    assertTrue(agentUi.contains("private fun NbgTerminalOutputChunkCard("))
    assertTrue(agentUi.contains("nbgTextChunksByLine(\n    text = terminal.output"))
    assertFalse(agentUi.contains("terminal.output.lineSequence().forEach"))
    assertFalse(agentUi.contains("val lineLimit = when {"))
    assertFalse(agentUi.contains("compact -> HANA_MOBILE_TERMINAL_DEFAULT_LINES"))
    assertFalse(agentUi.contains("val charLimit = if (compact) HANA_MOBILE_TERMINAL_DEFAULT_CHARS else HANA_MOBILE_TERMINAL_OUTPUT_LIMIT"))
    assertFalse(agentUi.contains("!compact && nbgHasMoreLines(terminal.output, HANA_MOBILE_TERMINAL_COLLAPSED_LINES)"))
    assertFalse(agentUi.contains("animateIntAsState"))
    assertFalse(agentUi.contains("var targetVisibleChars by remember(preview.filePath)"))
    assertFalse(agentUi.contains("label = \"file write typewriter length\""))
    assertFalse(agentUi.contains("label = \"file write cursor alpha\""))
    assertTrue(agentUi.contains("NbgDiffLine(line)"))
    assertTrue(agentUi.contains("line.startsWith(\"+\")"))
    assertTrue(agentUi.contains("line.startsWith(\"-\")"))
    assertTrue(agentUi.contains("NbgTerminalPanel("))
    assertTrue(agentUi.contains("color = NbgAgentColors.TerminalInline"))
    assertTrue(agentUi.contains("NbgTerminalWindowDots()"))
    val terminalPanelBlock = agentUi.substringAfter("internal fun NbgTerminalPanel(").substringBefore("@Composable\ninternal fun NbgTerminalWindowDots")
    assertTrue(terminalPanelBlock.indexOf("NbgTerminalWindowDots()") < terminalPanelBlock.indexOf("text = title"))
    assertTrue(agentUi.contains("Color(0xFFFF6B6B)"))
    assertTrue(agentUi.contains("Color(0xFFFFC857)"))
    assertTrue(agentUi.contains("Color(0xFF6BD17D)"))
    assertTrue(agentUi.contains("Color(0xFF17351E).copy(alpha = 0.72f)"))
    assertTrue(agentUi.contains("Color(0xFF3A1C1C).copy(alpha = 0.72f)"))
    assertTrue(agentUi.contains("val railColor = when"))
    assertTrue(agentUi.contains("val marker = when"))
    assertTrue(agentUi.contains("text = marker"))
    assertTrue(agentUi.contains("text = body"))
    assertTrue(agentUi.contains("Color(0xFFA8F0A4)"))
    assertTrue(agentUi.contains("Color(0xFFFF9A9A)"))
    assertFalse(agentUi.contains("expanded -> HANA_MOBILE_TERMINAL_EXPANDED_LINES"))
    assertFalse(agentUi.contains("nbgTailLinesText(terminal.output, lineLimit = lineLimit, charLimit = charLimit)"))
    assertFalse(agentUi.contains("contentDescription = if (expanded) \"收起终端输出\" else \"展开终端输出\""))
    assertTrue(agentUi.contains("BasicText(\n      text = terminal.output.ifBlank"))
    assertTrue(agentUi.contains("NbgMiniIconButton("))
    assertTrue(agentUi.contains("color = NbgAgentColors.SurfaceContainer"))
    assertTrue(agentUi.contains("contentColor = NbgAgentColors.TextStrong"))
    assertTrue(agentUi.contains("copyContentDescription = \"复制终端输出\""))
    assertTrue(agentUi.contains("clipboard.setText(AnnotatedString(terminal.output))"))
    assertTrue(agentUi.contains("contentDescription = \"文件写入预览"))
    assertTrue(agentUi.contains("contentDescription = \"文件 Diff"))
    assertTrue(agentUi.contains("contentDescription = \"终端实时输出"))
    assertTrue(agentUi.contains("文件变更"))
    assertTrue(agentUi.contains("终端"))
    assertTrue(agentUi.contains("NbgChatRunStatus"))
    assertTrue(agentUi.contains("nbgChatRunStatusWithDisplayModel(hanakoState, messages, displayModelName)"))
    assertTrue(agentUi.contains("NbgRunStatusLine(runStatus)"))
    assertTrue(agentUi.contains("val dotColor by animateColorAsState"))
    assertTrue(agentUi.contains("label = \"run status dot color\""))
    assertTrue(agentUi.contains("label = \"run status text color\""))
    assertTrue(agentUi.contains("if (status.active) {"))
    assertTrue(agentUi.contains("rememberInfiniteTransition(label = \"nbg run status\")"))
    assertTrue(agentUi.contains("label = \"nbg run status alpha pulse\""))
    assertTrue(agentUi.contains("label = \"run status idle alpha\""))
    assertTrue(agentUi.contains("private fun nbgRunStatusDotColor(status: NbgChatRunStatus): Color"))
    assertTrue(agentUi.contains("status.warning -> NbgAgentColors.StatusRed"))
    assertTrue(agentUi.contains("status.active -> NbgAgentColors.StatusYellow"))
    assertTrue(agentUi.contains("else -> NbgAgentColors.StatusGreen"))
    assertTrue(agentUi.contains("internal data class NbgAgentColorPalette("))
    assertTrue(agentUi.contains("private val NbgLightPalette = NbgAgentColorPalette("))
    assertTrue(agentUi.contains("private val NbgBlackPalette = NbgAgentColorPalette("))
    assertTrue(agentUi.contains("statusGreen = Color(0xFF7DDC8A)"))
    assertTrue(agentUi.contains("statusYellow = Color(0xFFE9B872)"))
    assertTrue(agentUi.contains("statusRed = Color(0xFFFF7468)"))
    assertTrue(agentUi.contains("background = Color(0xFF090A0B)"))
    assertTrue(agentUi.contains("textStrong = Color(0xFFF3F4F6)"))
    assertTrue(agentUi.contains("val LiquidBackgroundBrush: SolidColor get() = SolidColor(Background)"))
    assertTrue(agentUi.contains("val LiquidAccentBrush: SolidColor get() = SolidColor(SurfaceContainerHigh)"))
    assertTrue(agentUi.contains("val LiquidPanelBrush: SolidColor get() = SolidColor(TopBar)"))
    assertFalse(agentUi.contains(".background(NbgAgentColors.LiquidAccentBrush, RoundedCornerShape(12.dp))"))
    assertFalse(agentUi.contains("Color(0xFFFFFCF7)"))
    assertFalse(agentUi.contains("Color(0xFFCDEDA3)"))
    assertFalse(agentUi.contains("label = \"run status idle scale\""))
    assertTrue(agentUi.contains("targetState = text"))
    assertTrue(agentUi.contains("label = \"run status text\""))
    assertTrue(agentUi.contains("nbgMotionDuration(120)"))
    assertTrue(agentUi.contains("nbgMotionDuration(160)"))
    assertTrue(agentUi.contains("initialOffsetY = { it / 6 }"))
    assertTrue(agentUi.contains("targetOffsetY = { -it / 6 }"))
    assertTrue(agentUi.contains(") { targetText ->"))
    assertTrue(agentUi.contains("text = targetText"))
    assertTrue(agentUi.contains("contentDescription = \"运行状态 \$text\""))
    assertTrue(agentUi.contains("activeTool.title.ifBlank"))
    assertTrue(agentUi.contains("NBG_THINKING_DEPTH_OPTIONS"))
    assertTrue(agentUi.contains("NbgThinkingDepthOption(\"off\", \"关闭\""))
    assertTrue(agentUi.contains("NbgThinkingDepthOption(\"auto\", \"自动\""))
    assertTrue(agentUi.contains("NbgThinkingDepthOption(\"low\", \"快速\""))
    assertTrue(agentUi.contains("NbgThinkingDepthOption(\"medium\", \"均衡\""))
    assertTrue(agentUi.contains("NbgThinkingDepthOption(\"high\", \"深度\""))
    assertTrue(agentUi.contains("NbgThinkingDepthOption(\"xhigh\", \"最大思考\""))
    assertTrue(agentUi.contains("private fun nbgSupportedThinkingDepthOptions(model: HanakoModelSummary?)"))
    assertTrue(agentUi.contains("nbgNormalizeThinkingLevels(model?.thinkingLevels.orEmpty())"))
    assertTrue(agentUi.contains("thinkingLevels = nbgSupportedThinkingLevelsForModel("))
    assertTrue(agentUi.contains("thinkingSource = nbgThinkingSourceForModel("))
    assertTrue(agentUi.contains("val thinkingOptions = nbgSupportedThinkingDepthOptions(currentModelSummary)"))
    assertTrue(agentUi.contains("NbgComposerThinkingMenu("))
    assertTrue(agentUi.contains("NbgThinkingEffortPopup("))
    assertTrue(agentUi.contains("contentDescription = \"思考强度卡片\""))
    assertTrue(agentUi.contains("val width = 292.dp"))
    assertTrue(agentUi.contains("shape = RoundedCornerShape(24.dp)"))
    assertTrue(agentUi.contains("color = NbgAgentColors.GlassSurface"))
    assertTrue(agentUi.contains(".padding(horizontal = 20.dp, vertical = 18.dp)"))
    assertTrue(agentUi.contains("NbgThinkingEffortCard("))
    assertTrue(agentUi.contains("NbgThinkingEffortTrack("))
    assertTrue(agentUi.contains("detectDragGestures("))
    assertTrue(agentUi.contains("detectTapGestures"))
    assertTrue(agentUi.contains("onSelectLevel = onSetThinkingLevel"))
    assertFalse(agentUi.contains("onSelectLevel = { level ->\n                    thinkingMenuOpen = false"))
    assertTrue(agentUi.contains("onPreviewLevel(latestIndex)"))
    assertTrue(agentUi.contains("onCommitLevel(latestIndex)"))
    assertTrue(agentUi.contains("onCancelPreview()"))
    assertTrue(agentUi.contains("var pendingLevel by remember(displayOptions)"))
    assertTrue(agentUi.contains("if (pendingLevel == currentLevel)"))
    assertTrue(agentUi.contains("pendingLevel = option.level"))
    assertTrue(agentUi.contains("text = \"思考强度\""))
    assertTrue(agentUi.contains("text = \"更快\""))
    assertTrue(agentUi.contains("text = \"更聪明\""))
    assertTrue(agentUi.contains("nbgEffortDisplayName(displayOption.level, displayOption.label)"))
    assertTrue(agentUi.contains("\"xhigh\" -> \"最大思考\""))
    assertTrue(agentUi.contains("maxThinking = displayOption.level == \"xhigh\""))
    assertTrue(agentUi.contains("animation = tween(durationMillis = if (maxThinking) 1040 else 1800"))
    assertTrue(agentUi.contains("val columns = if (maxThinking) 46 else 38"))
    assertTrue(agentUi.contains("val rows = if (maxThinking) 5 else 4"))
    assertTrue(agentUi.contains("val maxWave = if (maxThinking)"))
    assertTrue(agentUi.contains("val knobAura = if (maxThinking) 0.34f else 0.18f"))
    assertFalse(agentUi.contains("val width = 332.dp"))
    assertTrue(agentUi.contains(".height(42.dp)"))
    assertTrue(agentUi.contains("Brush.horizontalGradient"))
    assertTrue(agentUi.contains("Brush.verticalGradient"))
    assertFalse(agentUi.contains("NbgThinkingEffortTick("))
    assertFalse(agentUi.contains("statusMessage"))
    assertFalse(agentUi.contains("thinking effort value label alpha"))
    assertFalse(agentUi.contains("NbgThinkingDepthSheet("))
    assertFalse(agentUi.contains("ModalBottomSheet("))
    assertFalse(agentUi.contains("Slider("))
    assertFalse(agentUi.contains("contentDescription = \"思考深度滑杆"))
    assertTrue(styles.contains("<item name=\"android:navigationBarColor\">#090A0B</item>"))
    assertTrue(styles.contains("<item name=\"android:statusBarColor\">#090A0B</item>"))
    assertTrue(styles.contains("<item name=\"android:windowLightStatusBar\">false</item>"))
    assertTrue(agentUi.contains("Surface(\n    color = Color.Transparent,\n    modifier = Modifier.fillMaxWidth(),"))
    assertTrue(agentUi.contains("val composerShape = RoundedCornerShape(27.dp)"))
    assertTrue(agentUi.contains("elevation = 28.dp"))
    assertTrue(agentUi.contains("elevation = 3.dp"))
    assertTrue(agentUi.contains("shape = composerShape"))
    assertTrue(agentUi.contains("color = NbgAgentColors.Input.copy(alpha = 0.985f)"))
    assertTrue(agentUi.contains("border = BorderStroke(1.dp, NbgAgentColors.InputBorder.copy(alpha = 0.68f))"))
    assertTrue(agentUi.contains("NbgAgentColors.Input"))
    assertTrue(agentUi.contains(".heightIn(min = 32.dp, max = 82.dp)"))
    assertTrue(agentUi.contains("Text(\"输入消息\""))
    assertFalse(agentUi.contains("BoxWithConstraints(modifier = Modifier.fillMaxWidth())"))
    assertFalse(agentUi.contains("NbgComposerBreathingBar("))
    assertFalse(agentUi.contains("label = \"composer breathing bar\""))
    assertFalse(agentUi.contains("contentDescription = \"输入栏呼吸条\""))
    assertTrue(agentUi.contains("NbgComposerInputAddRow("))
    assertTrue(agentUi.contains("contentDescription = \"输入框快捷添加\""))
    assertTrue(agentUi.contains("NbgComposerAddButton("))
    assertTrue(agentUi.contains("contentDescription = \"添加上下文\""))
    assertTrue(agentUi.contains("Toast.makeText(context, \"添加上下文稍后接入\""))
    assertTrue(agentUi.contains("NbgComposerBottomDock("))
    assertTrue(agentUi.contains("contentDescription = \"输入框底部工具栏\""))
    assertTrue(agentUi.contains("verticalArrangement = Arrangement.spacedBy(0.dp)"))
    assertTrue(agentUi.contains(".align(Alignment.BottomCenter)"))
    assertTrue(agentUi.contains(".padding(bottom = 34.dp)"))
    assertTrue(agentUi.contains(".height(56.dp)"))
    assertTrue(agentUi.contains("shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)"))
    assertTrue(agentUi.contains("color = NbgAgentColors.Input.copy(alpha = 0.82f)"))
    assertTrue(agentUi.contains(".padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp)"))
    assertTrue(agentUi.contains("modifier = Modifier.weight(1f)"))
    assertTrue(agentUi.contains("label = \"连接 IM\""))
    assertTrue(agentUi.contains("Toast.makeText(context, \"连接 IM 稍后接入\""))
    assertFalse(agentUi.contains("label = \"Agents team\""))
    assertFalse(agentUi.contains("label = \"多 Agent\""))
    assertFalse(agentUi.contains("contentDescription = \"多 Agent 默认开启\""))
    assertFalse(agentUi.contains("onOpenAgents = { page = NbgShellPage.Agents }"))
    assertFalse(agentUi.contains("onSelectAgents = onOpenAgents"))
    assertFalse(agentUi.contains("Toast.makeText(context, \"Agents team 稍后接入\""))
    assertFalse(agentUi.contains("Text(\"随便问点什么...\""))
    assertTrue(agentUi.contains("NbgModelMetaChip("))
    assertTrue(agentUi.contains("NbgSendButton("))
    assertFalse(agentUi.contains("NbgComposerDockIconAction("))
    assertFalse(agentUi.contains("NbgComposerDockSendAction("))
    assertFalse(agentUi.contains("label = \"composer dock send background\""))
    assertFalse(agentUi.contains("label = \"composer dock send icon tint\""))
    assertTrue(agentUi.contains("modifier = Modifier.weight(0.82f)"))
    assertTrue(agentUi.contains("modifier = Modifier.weight(0.88f)"))
    assertTrue(agentUi.contains("modifier = Modifier.weight(1.18f)"))
    assertTrue(agentUi.contains("label = \"composer chip background\""))
    assertTrue(agentUi.contains("label = \"composer chip icon tint\""))
    assertTrue(agentUi.contains("label = \"composer chip label color\""))
    assertTrue(agentUi.contains("label = \"composer chip icon scale\""))
    assertTrue(agentUi.contains("val chipIconScale by animateFloatAsState"))
    assertTrue(agentUi.contains("val compactLabelOnly = showVisualLabel && !showValue"))
    assertTrue(agentUi.contains("val chipIconBoxSize = if (compactLabelOnly) 18.dp else 24.dp"))
    assertTrue(agentUi.contains("val chipIconSize = if (compactLabelOnly) 13.dp else 15.dp"))
    assertTrue(agentUi.contains("fontSize = 11.sp"))
    assertTrue(agentUi.contains("color = chipBackground"))
    assertTrue(agentUi.contains("tint = chipIconTint"))
    assertTrue(agentUi.contains("modifier = Modifier.weight(1f)"))
    assertTrue(agentUi.contains("showVisualLabel: Boolean = true"))
    assertTrue(agentUi.contains("showValue: Boolean = true"))
    assertTrue(agentUi.contains("showDropdownIndicator: Boolean = true"))
    assertTrue(agentUi.contains("showVisualLabel = true"))
    assertTrue(agentUi.contains("showVisualLabel = false"))
    assertTrue(agentUi.contains("showValue = false"))
    assertTrue(agentUi.contains("showValue = true"))
    assertTrue(agentUi.contains("showDropdownIndicator = false"))
    assertTrue(agentUi.contains("showDropdownIndicator = true"))
    assertTrue(agentUi.contains("shape = RoundedCornerShape(14.dp)"))
    assertTrue(agentUi.contains("Icons.Filled.KeyboardArrowDown"))
    assertTrue(agentUi.contains("RoundedCornerShape(chipIconCorner)"))
    assertTrue(agentUi.contains("internal fun NbgPlainIconButton("))
    assertTrue(agentUi.contains("shape = RoundedCornerShape(13.dp)"))
    assertTrue(agentUi.contains(".background(NbgAgentColors.SurfaceContainer, RoundedCornerShape(13.dp))"))
    assertTrue(agentUi.contains("shape: Shape = RoundedCornerShape(12.dp)"))
    assertFalse(agentUi.contains(".height(31.dp)"))
    assertTrue(agentUi.contains(".height(40.dp)"))
    assertTrue(agentUi.contains("label = \"模型\""))
    assertTrue(agentUi.contains("value = modelButtonValue"))
    assertTrue(agentUi.contains("preferredModel: NbgChatPreferences?"))
    assertTrue(agentUi.contains("nbgCurrentModelSummary(agentModelConfig, modelName, preferredModel)"))
    assertTrue(agentUi.contains("val currentModelLabel = currentModelSummary?.label ?: modelName?.takeIf { it.isNotBlank() } ?: \"未选择\""))
    assertTrue(agentUi.contains("val modelButtonValue = nbgCompactComposerChipValue(currentModelLabel, \"未选择\")"))
    assertTrue(agentUi.contains("label = \"思考\""))
    assertTrue(agentUi.contains("value = thinkingButtonValue"))
    assertTrue(agentUi.contains("val thinkingButtonValue = nbgCompactComposerChipValue(nbgThinkingDepthDisplayLabel(thinkingLevel, thinkingLevelLabel), \"自动\")"))
    assertTrue(agentUi.contains("icon = HugeIcons.Brain02"))
    assertTrue(agentUi.contains("label = \"权限\""))
    assertTrue(agentUi.contains("value = permissionButtonValue"))
    assertTrue(agentUi.contains("val permissionButtonValue = nbgCompactComposerChipValue(permissionModeLabel, \"操作\")"))
    assertTrue(agentUi.contains("Triple(\"operate\", \"操作\", \"首次使用前确认，之后允许 agent 更主动执行操作。\")"))
    assertTrue(agentUi.contains("Triple(\"ask\", \"先问\", \"中高风险工具先请求确认。\")"))
    assertTrue(agentUi.contains("Triple(\"read_only\", \"计划\", \"先分析和制定方案，不写文件或执行写操作。\")"))
    assertTrue(agentUi.contains("contentDescription = \"\$label \$displayValue\""))
    assertTrue(agentUi.contains("var modelMenuOpen by remember"))
    assertTrue(agentUi.contains("var thinkingMenuOpen by remember"))
    assertTrue(agentUi.contains("var permissionMenuOpen by remember"))
    assertTrue(agentUi.contains("NbgComposerModelMenu("))
    assertTrue(agentUi.contains("NbgComposerPermissionMenu("))
    assertTrue(agentUi.contains("menuContentDescription = \"模型选择小菜单\""))
    assertTrue(agentUi.contains("menuContentDescription = \"权限设置小菜单\""))
    assertTrue(agentUi.contains("text = \"用于管理 MCP、Skills、终端、文件共享、网址 API 和外观。\""))
    assertFalse(agentUi.contains("当前对话的模型和权限仍在输入框里切换"))
    assertFalse(agentUi.contains("title = \"模型\""))
    assertFalse(agentUi.contains("title = \"权限\""))
    assertTrue(agentUi.contains("NbgComposerPopupMenu("))
    assertTrue(agentUi.contains("Popup("))
    assertTrue(agentUi.contains("PopupPositionProvider"))
    assertTrue(agentUi.contains("shadowElevation = 2.dp"))
    assertTrue(agentUi.contains("border = BorderStroke(1.dp, NbgAgentColors.GlassBorder)"))
    assertTrue(agentUi.contains("NbgComposerPopupPositionProvider(widthPx = widthPx, gapPx = gapPx)"))
    assertTrue(agentUi.contains("val popupAlpha by animateFloatAsState"))
    assertTrue(agentUi.contains("val popupScale by animateFloatAsState"))
    assertTrue(agentUi.contains("val popupOffsetY by animateDpAsState"))
    assertTrue(agentUi.contains("nbgMotionDuration(140)"))
    assertTrue(agentUi.contains("nbgMotionDuration(180)"))
    assertTrue(agentUi.contains("translationY = popupOffsetY.toPx()"))
    assertTrue(agentUi.contains("NbgComposerMenuRow("))
    assertTrue(agentUi.contains("private fun NbgComposerMenuRow("))
    assertTrue(agentUi.contains("currentModel: HanakoModelSummary?"))
    assertTrue(agentUi.contains("val currentModelKey = currentModel?.let(::nbgModelKey)"))
    assertTrue(agentUi.contains("nbgComposerModelGroups(config)"))
    assertTrue(agentUi.contains("NbgComposerMenuSection("))
    assertTrue(agentUi.contains("nbgModelCapabilityLine(model)"))
    assertTrue(agentUi.contains("nbgModelKey(model) == currentModelKey"))
    assertTrue(agentUi.contains("label = group.label"))
    assertTrue(agentUi.contains("private fun nbgModelProviderDisplayName(provider: String): String"))
    assertTrue(agentUi.contains("private fun nbgModelProviderDisplayName(model: HanakoModelSummary?): String"))
    assertTrue(agentUi.contains("model.providerLabel.ifBlank"))
    assertFalse(agentUi.contains("provider.startsWith(\"urlapi-\") -> \"网址 API ·"))
    assertTrue(agentUi.contains("private fun nbgModelThinkingLabel(model: HanakoModelSummary): String"))
    assertTrue(agentUi.contains("\"xhigh\" in levels -> \"最大思考\""))
    assertTrue(agentUi.contains("levels.any { it == \"low\" || it == \"medium\" || it == \"high\" } -> \"支持思考\""))
    assertFalse(agentUi.contains("NbgComposerMenuText("))
    assertFalse(agentUi.contains("var openPanel by remember"))
    assertFalse(agentUi.contains("NbgComposerButtonPopup("))
    assertFalse(agentUi.contains("popupHorizontalAlignment"))
    assertFalse(agentUi.contains("NbgComposerPanel"))
    assertFalse(agentUi.contains("NbgComposerPopupTail"))
    assertFalse(agentUi.contains("NbgCompactChoicePill("))
    assertFalse(agentUi.contains("NbgCompactChoiceRow("))
    assertFalse(agentUi.contains("NbgComposerThinkingPanel"))
    assertFalse(agentUi.contains("NbgThinkingLevelDialog"))
    assertFalse(agentUi.contains("思考设置小弹窗"))
    assertFalse(agentUi.contains("onOpenModel = onShowAgentModelConfig"))
    assertFalse(agentUi.contains("onOpenPermissionSettings = {"))
    assertFalse(agentUi.contains("onOpenThinkingSettings = {"))
    assertFalse(agentUi.contains("NbgSlashCommandDialog"))
    assertFalse(agentUi.contains("NbgToolIcon(Icons.Filled.AutoAwesome, \"模型\", onClick = onOpenModel)"))
    assertFalse(agentUi.contains("Icons.Filled.Terminal, \"命令\""))
    assertFalse(agentUi.contains("Icons.Filled.Search, \"搜索\""))
    assertFalse(agentUi.contains("contentDescription = \"对话文件\""))
    assertFalse(agentUi.contains("prompt.isNotEmpty() && !streaming"))
    assertTrue(agentUi.contains("streaming && inputText.isBlank()"))
    assertTrue(agentUi.contains("sendSteering = streaming && inputText.isNotBlank()"))
    assertTrue(agentUi.contains("steering -> \"插话\""))
    assertFalse(agentUi.contains("工作区文件"))
    assertTrue(agentUi.contains("任务清单"))
    assertTrue(agentUi.contains("title = \"网址 API\""))
    assertTrue(agentUi.contains("NbgShellPage.UrlApi"))
    assertTrue(agentUi.contains("NbgSelectedUrlApiModel"))
    assertTrue(agentUi.contains("NbgChatPreferenceStore"))
    assertTrue(agentUi.contains("val chatPreferenceState = remember { NbgAgentChatPreferenceState() }"))
    assertTrue(agentUi.contains("val chatPreferences = chatPreferenceState.preferences"))
    assertTrue(agentUi.contains("val chatPreferencesLoaded = chatPreferenceState.loaded"))
    assertTrue(agentUi.contains("chatPreferenceState.applyLoaded(chatPreferenceStore.load())"))
    assertTrue(agentUi.contains("chatPreferenceState.applySaved(chatPreferenceStore.saveModel(model.provider, model.id, model.label))"))
    assertTrue(agentUi.contains("chatPreferenceState.applySaved(chatPreferenceStore.savePermissionMode(mode))"))
    assertTrue(agentUi.contains("chatPreferenceState.applySaved(chatPreferenceStore.saveThinkingLevel(level))"))
    assertTrue(agentUi.contains("chatPreferenceState.applySaved(chatPreferenceStore.saveMultiAgentEnabled(enabled))"))
    assertTrue(agentUi.contains("val saved = chatPreferenceStore.saveTheme(themeId)\n    chatPreferenceState.applySaved(saved)\n    onAppearanceChanged(saved)"))
    assertTrue(agentUi.contains("val saved = chatPreferenceStore.saveFont(fontId)\n    chatPreferenceState.applySaved(saved)\n    onAppearanceChanged(saved)"))
    assertTrue(agentUi.contains("onAppearanceChanged(saved)"))
    assertFalse(agentUi.contains("var chatPreferences by remember"))
    assertFalse(agentUi.contains("var chatPreferencesLoaded by remember"))
    assertTrue(agentUi.contains("val urlApiEntriesState = remember { NbgAgentUrlApiEntriesState() }"))
    assertTrue(agentUi.contains("val savedApis = urlApiEntriesState.entries"))
    assertTrue(agentUi.contains("val savedApisLoaded = urlApiEntriesState.loaded"))
    assertTrue(agentUi.contains("urlApiEntriesState.applyLoaded(apiStore.load())"))
    assertFalse(agentUi.contains("var savedApis by remember"))
    assertFalse(agentUi.contains("var savedApisLoaded by remember"))
    assertFalse(agentUi.contains("savedApisLoaded = true"))
    assertTrue(agentUi.contains("fun loadChatPreferences()"))
    assertTrue(agentUi.contains("savePreferredModel(model)"))
    assertTrue(agentUi.contains("savePreferredPermissionMode(normalizedMode)"))
    assertTrue(agentUi.contains("fun applyPreferredPermissionMode(mode: String)"))
    assertTrue(agentUi.contains("fun requestPreferredPermissionMode(mode: String)"))
    assertTrue(agentUi.contains("confirmationState.shouldGateOperatePermissionMode(normalizedMode, hanakoState.sessionPath)"))
    assertTrue(agentUi.contains("confirmationState.requestOperatePermissionWarning(hanakoState.sessionPath)"))
    assertTrue(agentUi.contains("savePreferredThinkingLevel(level)"))
    assertTrue(agentUi.contains("fun clearPreferredModel()"))
    assertTrue(agentUi.contains("chatPreferenceState.applySaved(\n      chatPreferenceStore.save("))
    assertTrue(agentUi.contains("chatPreferences.copy(modelProvider = \"\", modelId = \"\", modelLabel = \"\")"))
    assertTrue(agentUi.contains("chatPreferences.toSelectedUrlApiModel()"))
    assertTrue(agentUi.contains("hanakoState.sessionPath,"))
    assertTrue(agentUi.contains("hanakoState.permissionMode,"))
    assertTrue(agentUi.contains("hanakoState.streaming,"))
    assertTrue(agentUi.contains("confirmationState.operatePermissionWarningOpen,"))
    assertTrue(agentUi.contains("val sessionPath = hanakoState.sessionPath"))
    assertTrue(agentUi.contains("sessionPath.isNullOrBlank()"))
    assertTrue(agentUi.contains("val preferredPermissionMode = chatPreferences.permissionMode.ifBlank { hanakoState.permissionMode }"))
    assertTrue(agentUi.contains("val displayPermissionMode = if (confirmationState.shouldGateOperatePermissionMode(preferredPermissionMode, hanakoState.sessionPath))"))
    assertTrue(agentUi.contains("val preferredMode = nbgNormalizePermissionMode(preferred.permissionMode)"))
    assertTrue(agentUi.contains("confirmationState.shouldAutoPromptOperatePermissionWarning(preferredMode, sessionPath)"))
    assertTrue(agentUi.contains("hanako.setSessionPermissionMode(NBG_PERMISSION_MODE_ASK)"))
    assertTrue(agentUi.contains("requestPreferredPermissionMode(mode)"))
    assertTrue(agentUi.contains("NbgOperatePermissionModeDialog("))
    assertTrue(agentUi.contains("confirmationState.acceptOperatePermissionWarning()"))
    assertTrue(agentUi.contains("applyPreferredPermissionMode(NBG_PERMISSION_MODE_OPERATE)"))
    assertTrue(agentUi.contains("!chatPreferences.hasModel && savedApis.isNotEmpty()"))
    assertTrue(agentUi.contains("modelName = displayModelName"))
    assertTrue(agentUi.contains("private fun nbgChatRunStatusWithDisplayModel"))
    assertTrue(agentUi.contains("val displayRunStatus = nbgChatRunStatusWithDisplayModel(hanakoState, messages, displayModelName)"))
    assertTrue(agentUi.contains("runStatus = displayRunStatus"))
    assertTrue(agentUi.contains("preferredModel = chatPreferences"))
    assertTrue(agentUi.contains("permissionMode = displayPermissionMode"))
    assertTrue(agentUi.contains("thinkingLevel = displayThinkingLevel"))
    assertTrue(agentUi.contains("val displayModelSummary = remember(mergedAgentModelConfig, displayModelName, chatPreferences)"))
    assertTrue(agentUi.contains("nbgCoerceThinkingLevelForModel(preferredThinkingLevel, it)"))
    assertTrue(agentUi.contains("nbgDefaultSelectedUrlApiModel"))
    assertTrue(agentUi.contains("val modelConfigState = remember { NbgAgentModelConfigState() }"))
    assertTrue(agentUi.contains("modelConfigState.applyLoaded(event.config)"))
    assertTrue(agentUi.contains("modelConfigState.applyFailed(event.message)"))
    assertTrue(agentUi.contains("val agentModelConfig = modelConfigState.config"))
    assertTrue(agentUi.contains("agentModelConfigLoading = modelConfigState.loading"))
    assertTrue(agentUi.contains("agentModelConfigError = modelConfigState.error"))
    assertTrue(agentUi.contains("modelConfigState.beginLoad()"))
    assertTrue(agentUi.contains("modelConfigState.recordLocalError(\"没有找到已保存的网址 API 模型\")"))
    assertFalse(agentUi.contains("var agentModelConfig by remember"))
    assertFalse(agentUi.contains("var agentModelConfigLoading by remember"))
    assertFalse(agentUi.contains("var agentModelConfigError by remember"))
    assertTrue(agentUi.contains("val urlApiSelectionState = remember { NbgAgentUrlApiSelectionState() }"))
    assertTrue(agentUi.contains("val selectedUrlApiModel = urlApiSelectionState.selectedModel"))
    assertTrue(agentUi.contains("val restoredDefaultUrlApiModel = urlApiSelectionState.restoredDefaultModel"))
    assertTrue(agentUi.contains("val restoredChatPreferences = urlApiSelectionState.restoredChatPreferences"))
    val agentUiMainForUrlApiSelection = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    assertFalse(agentUiMainForUrlApiSelection.contains("var selectedUrlApiModel by remember"))
    assertFalse(agentUiMainForUrlApiSelection.contains("var restoredDefaultUrlApiModel by remember"))
    assertFalse(agentUiMainForUrlApiSelection.contains("var restoredChatPreferences by remember"))
    assertTrue(agentUi.contains("if (!chatPreferencesLoaded || !savedApisLoaded) return@LaunchedEffect"))
    assertTrue(agentUi.contains("LaunchedEffect(savedApisLoaded, savedApis, chatPreferencesLoaded, chatPreferences)"))
    assertTrue(agentUi.contains("if (!savedApisLoaded) return@LaunchedEffect"))
    assertTrue(agentUi.contains("LaunchedEffect(agentModelConfig, selectedUrlApiModel, chatPreferencesLoaded, savedApisLoaded, chatPreferences.hasModel)"))
    assertTrue(agentUi.contains("chatPreferenceState.applySaved(chatPreferenceStore.saveModel(current.provider, current.id, current.label))"))
    assertTrue(agentUi.contains("chatPreferencesLoaded && savedApisLoaded && selectedUrlApiModel == null && !chatPreferences.hasModel"))
    assertTrue(agentUi.contains("!chatPreferences.hasModel && savedApis.isNotEmpty() && selectedUrlApiModel == null"))
    assertTrue(agentUi.contains("urlApiSelectionState.restoreDefaultModel(nbgDefaultSelectedUrlApiModel(savedApis))"))
    assertTrue(agentUi.contains("urlApiSelectionState.select(preferred)"))
    assertTrue(agentUi.contains("urlApiSelectionState.markChatPreferencesRestored()"))
    assertTrue(agentUi.contains("urlApiSelectionState.clearSelectedModel()"))
    assertTrue(agentUi.contains("val apiEditor = rememberNbgAgentApiEditorState()"))
    assertTrue(agentUi.contains("apiEditor.nextRequestSerial()"))
    assertTrue(agentUi.contains("!apiEditor.isCurrentRequest(requestSerial)"))
    assertTrue(agentUi.contains("requestedUrl != apiEditor.baseUrlDraft"))
    assertTrue(agentUi.contains("requestedKey != apiEditor.apiKeyDraft.trim()"))
    assertTrue(agentUi.contains("apiEditor.models.none { it.id == requestedModelId }"))
    assertTrue(agentUi.contains("apiEditor.applyFetchedModels(result)"))
    assertTrue(agentUi.contains("apiEditor.applyVerifiedModel("))
    assertFalse(agentUi.contains("var apiRequestSerial by remember"))
    assertFalse(agentUi.contains("var apiNameDraft by remember"))
    assertTrue(agentUi.contains("nbgUrlApiModelSummaries(savedApis, selectedUrlApiModel)"))
    assertTrue(agentUi.contains("private fun nbgVerifiedUrlApiModels(entry: NbgStoredApi): List<NbgApiModel>"))
    assertTrue(agentUi.contains("val availableModels = nbgVerifiedUrlApiModels(entry)"))
    assertTrue(agentUi.contains("val model = nbgVerifiedUrlApiModels(entry).firstOrNull { it.id == selected.modelId } ?: return null"))
    assertTrue(agentUi.contains("model.id in entry.verifiedModelIds"))
    assertFalse(agentUi.contains("entry.verifiedModelIds.isEmpty() || model.id in entry.verifiedModelIds"))
    assertTrue(agentUi.contains("entry == null || nbgVerifiedUrlApiModels(entry).none { it.id == selected.modelId }"))
    assertTrue(agentUi.contains("nbgVerifiedUrlApiModels(entry).any { it.id == preferred.modelId }"))
    assertTrue(agentUi.contains("chatPreferences.modelProvider == providerId"))
    assertTrue(agentUi.contains(".takeIf { selected -> availableModels.any { it.id == selected } }"))
    assertTrue(agentUi.contains("val activeUrlApiProviderIds = remember(savedApis) { savedApis.map { nbgUrlApiProviderId(it.id) }.toSet() }"))
    assertTrue(agentUi.contains("nbgMergeAgentModelConfig(agentModelConfig, urlApiModelSummaries, activeUrlApiProviderIds)"))
    assertTrue(agentUi.contains("hanako.syncUrlApiProviders(savedApis)"))
    assertTrue(agentUi.contains("modelName = displayModelName"))
    assertTrue(agentUi.contains("model.provider.startsWith(\"urlapi-\")"))
    assertTrue(agentUi.contains("val entry = savedApis.firstOrNull { nbgUrlApiProviderId(it.id) == model.provider }"))
    assertTrue(agentUi.contains("val providerId = nbgUrlApiProviderId(entry.id)"))
    assertTrue(agentUi.contains("provider = providerId"))
    assertTrue(agentUi.contains("isCurrent = activeSelection?.providerId == providerId && activeSelection.modelId == model.id"))
    assertTrue(agentUi.contains("internal fun nbgMergeAgentModelConfig"))
    assertTrue(agentUi.contains("activeUrlApiProviderIds: Set<String>"))
    assertTrue(agentUi.contains("!model.provider.startsWith(\"urlapi-\") || model.provider in activeUrlApiProviderIds"))
    assertTrue(agentUi.contains("val currentKey = urlApiModels.firstOrNull { it.isCurrent }?.let(::nbgModelKey)"))
    assertTrue(agentUi.contains("val urlApiModelsByKey = urlApiModels.associateBy(::nbgModelKey)"))
    assertTrue(agentUi.contains("val serverModelsWithLocalLabels = serverModels.map { serverModel ->"))
    assertTrue(agentUi.contains("providerLabel = localModel.providerLabel"))
    assertTrue(agentUi.contains("urlApiModels.filterNot { nbgModelKey(it) in serverModelKeys }"))
    assertTrue(agentUi.contains(".distinctBy(::nbgModelKey)"))
    assertTrue(agentUi.contains("model.copy(isCurrent = currentKey != null && nbgModelKey(model) == currentKey)"))
    assertTrue(agentUi.contains("val next = entry.copy(selectedModelId = apiModel.id)"))
    val agentUiMain = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    assertEquals(2, Regex(Regex.escape("urlApiEntriesState.applySaved(apiStore.save(next))")).findAll(agentUiMain).count())
    assertTrue(agentUi.contains("urlApiSelectionState.select(NbgSelectedUrlApiModel(model.provider, apiModel.id, apiModel.label))"))
    assertTrue(agentUi.contains("hanako.switchUrlApiModel(next, apiModel)"))
    assertTrue(bridge.contains("fun switchUrlApiModel(entry: NbgStoredApi, model: NbgApiModel)"))
    assertTrue(bridge.contains("http.configureUrlApiModel(activeInfo, entry, model)"))
    assertTrue(bridge.contains("fun configureUrlApiModel(info: HanakoServerInfo, entry: NbgStoredApi, model: NbgApiModel)"))
    assertTrue(bridge.contains("val modelIds = listOf(model.id.trim()).filter { it.isNotBlank() }"))
    assertTrue(bridge.contains(".put(\"api_key\", entry.apiKey)"))
    assertTrue(bridge.contains("val baseUrl = nbgHanakoBaseUrlForUrlApi(entry.baseUrl, provider)"))
    assertTrue(bridge.contains(".put(\"base_url\", baseUrl)"))
    assertTrue(bridge.contains("val staleProviders = findStaleUrlApiProviders(info, entry, providerId)"))
    assertTrue(bridge.contains("staleProviders.forEach { staleProvider -> put(staleProvider, JSONObject.NULL) }"))
    assertTrue(bridge.contains(".put(\"provider\", providerId)"))
    assertTrue(bridge.contains(".put(\"api\", providerApi)"))
    assertTrue(bridge.contains(".put(\"models\", modelConfigs)"))
    assertTrue(bridge.contains("val (activeInfo, sessionPath) = ensureLiveSessionForRequestWithRetry(info)"))
    assertTrue(bridge.contains("http.switchSessionModel(activeInfo, sessionPath, model.id, providerId)"))
    assertTrue(bridge.contains("setDefaultModel(info, model.id, providerId)"))
    assertTrue(bridge.contains("JSONObject().put(\n            \"chat\",\n            JSONObject()"))
    assertTrue(bridge.contains(".put(\"id\", model.id)"))
    assertTrue(bridge.contains(".put(\"provider\", providerId)"))
    assertTrue(bridge.contains("nbgHanakoProviderForUrlApi(entry.baseUrl, model.id)"))
    assertTrue(bridge.contains("syncDefaultUrlApiModel()"))
    assertTrue(bridge.contains("http.configureUrlApiModel(info, current.first, current.second)"))
    assertTrue(bridge.contains("state.copy(modelName = current.second.label.ifBlank { current.second.id })"))
    assertTrue(bridge.contains("lastWsErrorMessage"))
    assertTrue(bridge.contains("now - lastWsErrorAtMs < 2_500L"))
    assertTrue(agentUi.contains("contentDescription = \"添加网址 API\""))
    assertTrue(agentUi.contains("contentDescription = \"返回聊天\""))
    assertTrue(agentUi.contains("title = if (apiEditor.editingApi == null) \"添加网址 API\" else \"编辑网址 API\""))
    assertTrue(agentUi.contains("label = { Text(\"网址\") }"))
    assertTrue(agentUi.contains("label = { Text(\"API Key\") }"))
    assertTrue(agentUi.contains("label = if (busy) \"处理中\" else \"获取模型\""))
    assertTrue(agentUi.contains("label = if (verified) \"重验\" else \"验证\""))
    assertTrue(agentUi.contains("NbgDialogAction(label = \"保存\""))
    assertTrue(agentUi.contains("urlApiEntriesState.applySaved(apiStore.delete(entry.id))"))
    assertFalse(agentUi.contains("savedApis = apiStore.save(next)"))
    assertFalse(agentUi.contains("savedApis = apiStore.delete(entry.id)"))
    assertFalse(agentUi.contains("selectedUrlApiModelName = next.models.firstOrNull"))
    assertTrue(agentUi.contains("onOpenProviders = {"))
    assertFalse(agentUi.contains("text = { Text(\"供应商\""))
    assertTrue(agentUi.contains("重新生成"))
    assertTrue(agentUi.contains("撤回上一轮"))
    assertTrue(agentUi.contains("压缩上下文"))
    assertTrue(agentUi.contains("val canCompress = compressionAvailable && !streaming && !compressing"))
    assertTrue(agentUi.contains("压缩上下文不可用"))
    assertTrue(agentUi.contains("enabled = canCompress"))
    assertFalse(agentUi.contains("模型与助手"))
    assertFalse(agentUi.contains("权限模式"))
    assertTrue(agentUi.contains("思考强度"))
    assertFalse(agentUi.contains("命令菜单"))
    assertTrue(agentUi.contains("停止生成"))
    assertTrue(agentUi.contains("Icons.Filled.Stop"))
    assertTrue(agentUi.contains("contentDescription = \"打断终端\""))
    assertTrue(agentUi.contains("onInterruptTerminal = hanako::interruptTerminalByHuman"))
    assertTrue(agentUi.contains("HugeIcons.StopCircle"))
    assertTrue(agentUi.contains("replaceHistory"))

    listOf(
      "NbgHanakoTerminalsDialog",
      "NbgCheckpointsDialog",
      "NbgRestoreCheckpointDialog",
      "NbgActivitiesDialog",
      "NbgDevLogsDialog",
      "NbgDiaryDialog",
      "NbgMemoriesDialog",
      "NbgPluginsDialog",
      "NbgBridgeDialog",
      "NbgEnvironmentDialog",
      "NbgPromptsDialog",
      "HanakoPro 终端",
      "HanakoPro 插件运行时",
      "正在读取 HanakoPro 桥接状态",
      "正在读取 HanakoPro 环境",
      "正在读取 HanakoPro 提示词",
      "正在读取 HanakoPro 记忆",
      "正在读取 HanakoPro 日志",
      "恢复检查点",
    ).forEach { assertFalse(agentUi.contains(it)) }
  }

  @Test
  fun urlApiPageStoresRuntimeKeysAndVerifiesUpstreamModels() {
    val agentUi = readAgentUiSource()
    val apiStore = File("src/main/java/com/nbg/android/NbgApiStore.kt").readText()
    val apiEditorState = File("src/main/java/com/nbg/android/NbgAgentApiEditorState.kt").readText()
    val allSource = listOf(agentUi, apiStore, apiEditorState).joinToString("\n")

    assertTrue(apiStore.contains("class NbgApiStore(context: Context)"))
    assertTrue(apiStore.contains("getSharedPreferences(PREFS, Context.MODE_PRIVATE)"))
    assertTrue(apiStore.contains("NbgAndroidApiKeySecretStore(context.applicationContext)"))
    assertTrue(apiStore.contains("NbgEncryptedPreferenceSecretStore("))
    assertTrue(apiStore.contains("secretStore.saveApiKey(normalized.id, normalized.apiKey)"))
    assertTrue(apiStore.contains("fun nbgLoadStoredApisFromRawForTest"))
    assertTrue(apiStore.contains("toMetadataJson"))
    assertTrue(apiStore.contains("data class NbgStoredApi"))
    assertTrue(apiStore.contains("data class NbgApiModel"))
    assertTrue(apiStore.contains("class NbgUpstreamApiClient"))
    assertTrue(apiStore.contains("fun fetchModels(baseUrl: String, apiKey: String)"))
    assertTrue(apiStore.contains("fun verifyModel(baseUrl: String, apiKey: String, modelId: String)"))
    assertTrue(apiStore.contains("nbgApiEndpoint(normalized, \"/v1/models\")"))
    assertTrue(apiStore.contains("nbgApiEndpoint(normalized, \"/models\")"))
    assertTrue(apiStore.contains("nbgApiEndpoint(normalized, \"/v1/chat/completions\")"))
    assertTrue(apiStore.contains("nbgApiEndpoint(normalized, \"/v1/messages\")"))
    assertTrue(apiStore.contains("val effectiveBaseUrl: String = \"\""))
    assertTrue(apiStore.contains("val text = nbgParseProbeText(body, probe.wire)"))
    assertTrue(apiStore.contains("if (text.isNotBlank())"))
    assertTrue(apiStore.contains("max_completion_tokens"))
    assertTrue(apiStore.contains("nbgOpenAiProbeBodies(modelId)"))
    assertTrue(apiStore.contains("nbgHanakoBaseUrlForUrlApi"))
    assertTrue(apiStore.contains("return \"\$normalized/v1\""))
    assertTrue(apiEditorState.contains("effectiveBaseUrlDraft = result.effectiveBaseUrl.ifBlank { fallbackEffectiveBaseUrl }"))
    assertTrue(agentUi.contains("val requestedKey = apiEditor.apiKeyDraft.trim()"))
    assertTrue(agentUi.contains("val baseUrl = nbgNormalizeApiBaseUrl(apiEditor.effectiveBaseUrlDraft.ifBlank { apiEditor.baseUrlDraft })"))
    assertTrue(agentUi.contains("apiKey = apiEditor.apiKeyDraft.trim()"))
    assertTrue(agentUi.contains("apiKey.trim().isNotBlank()"))
    assertTrue(agentUi.contains("onBaseUrlChange = apiEditor::updateBaseUrl"))
    assertTrue(apiEditorState.contains("effectiveBaseUrlDraft = \"\"\n    verifiedModelIds = emptySet()"))
    assertTrue(agentUi.contains("onApiKeyChange = apiEditor::updateApiKey"))
    assertTrue(agentUi.contains("val selectedVerifiedModelId = apiEditor.selectedVerifiedModelId()"))
    assertTrue(apiEditorState.contains(".takeIf { it in verifiedModelIds }"))
    assertTrue(agentUi.contains("selectedModelId = selectedVerifiedModelId"))
    assertTrue(apiStore.contains("addHeader(\"Authorization\", \"Bearer \$apiKey\")"))
    assertTrue(apiStore.contains("addHeader(\"x-api-key\", apiKey)"))
    assertTrue(apiStore.contains("addHeader(\"anthropic-version\", \"2023-06-01\")"))
    assertTrue(apiStore.contains(".header(\"User-Agent\", NBG_UPSTREAM_USER_AGENT)"))
    assertTrue(apiStore.contains("NBG_UPSTREAM_USER_AGENT = \"NBG-Android/1.0\""))
    assertTrue(apiStore.contains("nbgMaskedApiKey"))
    assertFalse(apiStore.contains(".put(\"apiKey\", apiKey)"))
    assertTrue(agentUi.contains("本地私有保存"))
    assertTrue(agentUi.contains("点击右上角加号，输入上游地址和 API Key，获取模型并验证可用后保存。"))
    assertFalse(Regex("""sk-[A-Za-z0-9_-]{20,}""").containsMatchIn(allSource))
  }

  @Test
  fun androidHanakoServerUsesStableUpstreamUserAgent() {
    val launcher = File("src/main/java/com/nbg/android/HanakoServerLauncher.kt").readText()

    assertTrue(launcher.contains("NBG_HANAKO_UPSTREAM_UA"))
    assertTrue(launcher.contains("NBG-Android/1.0"))
    assertTrue(launcher.contains("android-upstream-user-agent.cjs"))
    assertTrue(launcher.contains("headers.set(\"User-Agent\", USER_AGENT)"))
    assertTrue(launcher.contains("NODE_OPTIONS=\"--require"))
    assertTrue(launcher.contains("record_android_runtime_patch \"android-upstream-user-agent-require-v1\""))
    assertTrue(launcher.contains("android-upstream-user-agent.active"))
    assertTrue(launcher.contains("NBG_HANAKO_RUNTIME_PATCH_SET_VERSION"))
    assertTrue(launcher.contains("NBG_HANAKO_RUNTIME_TARGET_VERSION"))
    assertTrue(launcher.contains("NBG_HANAKO_RUNTIME_SOURCE_FALLBACK_VERSION"))
    assertTrue(launcher.contains("val targetRuntimeVersion = if (sourceFallback)"))
    assertTrue(launcher.contains("targetRuntimeVersion"))
    assertTrue(launcher.contains("NBG_HANAKO_RUNTIME_PATCH_TARGET_PACK_MARKER"))
    assertTrue(launcher.contains("android-runtime-patches.json"))
    assertTrue(launcher.contains("android-runtime-patch-recorder.cjs"))
    assertTrue(launcher.contains("target-marker-mismatch"))
    assertTrue(launcher.contains("renderRuntimePatchPrelude(sourceFallback = false)"))
    assertTrue(launcher.contains("renderRuntimePatchPrelude(sourceFallback = true)"))
    assertTrue(launcher.contains("renderDefaultWorkspacePatch(\"\\\"/opt/hanakopro-server/shared/default-workspace.js\\\"\")"))
    assertTrue(launcher.contains("renderChatToolDetailsPatch(\"\\\"/opt/hanakopro-server/server/routes/chat.js\\\"\")"))
    assertTrue(launcher.contains("renderMemoryContextPolicyPatch(\"\\\"/opt/hanakopro-server/plugins/memory/lib/memory-store.js\\\"\")"))
    assertTrue(launcher.contains("renderMemoryContextPolicyPatch(\"(process.env.HANA_ROOT || \\\"\\\") + \\\"/plugins/memory/lib/memory-store.js\\\"\")"))
    assertTrue(launcher.contains("android-memory-context-policy-v1"))
    assertTrue(launcher.contains("nbg-memory-context-v1"))
    assertTrue(launcher.contains("NBG_MEMORY_CONTEXT_POLICY_VERSION"))
    assertTrue(launcher.contains("assertMemoryPolicyForSave"))
    assertTrue(launcher.contains(".filter(memoryPolicyAllowsContext)"))
    assertTrue(launcher.contains("20260704-runtime-patch-gate-v2-memory-policy"))
    assertTrue(launcher.contains("hanako-server-linux-arm64-node22"))
    assertTrue(launcher.contains("hanako-source-fallback"))
    assertTrue(launcher.contains("restarting server for android runtime patch set"))
    assertTrue(launcher.contains("android-runtime-patches.active"))
    assertFalse(launcher.contains("restarting server for android upstream user agent patch"))
  }

  @Test
  fun webWorkbenchSourcesAndAssetsAreRemoved() {
    val gradle = File("build.gradle.kts").readText()

    assertFalse(File("web-workbench").exists())
    assertFalse(File("src/main/assets/workbench").exists())
    assertFalse(File("src/main/assets/workbench-react").exists())
    assertFalse(gradle.contains("buildReactWorkbench"))
    assertFalse(gradle.contains("web-workbench"))
    assertFalse(gradle.contains("workbench-react"))
    assertFalse(gradle.contains("npm"))
    assertFalse(gradle.contains("vite"))
  }

  @Test
  fun profileBuildUsesReleaseOptimizationsForScrollPerfVerification() {
    val gradle = File("build.gradle.kts").readText()

    assertTrue(gradle.contains("buildTypes {"))
    assertTrue(gradle.contains("getByName(\"release\")"))
    assertTrue(gradle.contains("isMinifyEnabled = true"))
    assertTrue(gradle.contains("isShrinkResources = true"))
    assertTrue(gradle.contains("proguard-android-optimize.txt"))
    assertTrue(gradle.contains("create(\"profile\")"))
    assertTrue(gradle.contains("initWith(getByName(\"release\"))"))
    assertTrue(gradle.contains("matchingFallbacks += listOf(\"release\")"))
    assertTrue(gradle.contains("signingConfig = signingConfigs.getByName(\"debug\")"))
    assertTrue(gradle.contains("isDebuggable = false"))
    assertFalse(gradle.contains("applicationIdSuffix"))
    assertTrue(File("proguard-rules.pro").isFile)
  }

  @Test
  fun terminalScreenAvoidsManualImeInsetHandlingOnMiui() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val terminalScreen = mainActivity.substringAfter("private fun TerminalScreen(")
      .substringBefore("@Composable\nprivate fun TermuxTerminalViewHost(")

    assertFalse(
      "MIUI did not deliver reliable IME insets to this Compose view; keep decor fitting enabled and let adjustResize shrink the terminal window.",
      mainActivity.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"),
    )
    assertFalse(terminalScreen.contains(".imePadding()"))
    assertFalse(terminalScreen.contains(".statusBarsPadding()"))
  }

  @Test
  fun extraKeysBarUsesContentHeightInsteadOfFixedViewportHeight() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val extraKeysBar = mainActivity.substringAfter("private fun TermuxExtraKeysBar(")
      .substringBefore("@Composable\nprivate fun ExtraTextKey(")

    assertFalse(
      "The extra keys bar must wrap its two rows plus system insets; a fixed 88dp height gets clipped or covered on dense phone screens.",
      extraKeysBar.contains(".height(88.dp)"),
    )
  }

  @Test
  fun terminalTapExplicitlyShowsKeyboardEvenWhenInputAlreadyHasFocus() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()

    assertTrue(mainActivity.contains("TermuxTerminalViewHost("))
    assertTrue(mainActivity.contains("TerminalView(context, null)"))
    assertTrue(mainActivity.contains("InputMethodManager"))
    assertTrue(mainActivity.contains("showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)"))
  }

  @Test
  fun terminalScreenUsesTermuxViewInsteadOfComposeCanvasRenderer() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()

    assertTrue(mainActivity.contains("AndroidView("))
    assertTrue(mainActivity.contains("TerminalView(context, null)"))
    assertFalse(
      "The main terminal path must use Termux TerminalView; Compose Canvas caused IME resize and scrollback drift.",
      mainActivity.contains("TerminalCanvas("),
    )
  }

  @Test
  fun terminalScreenDoesNotUseHiddenBasicTextFieldForTerminalInput() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val terminalScreen = mainActivity.substringAfter("private fun TerminalScreen(")
      .substringBefore("@Composable\nprivate fun TermuxTerminalViewHost(")

    assertFalse(
      "Termux TerminalView owns IME input; a hidden Compose BasicTextField must not sit over the terminal.",
      terminalScreen.contains("BasicTextField("),
    )
    assertFalse(terminalScreen.contains("TerminalDirectInputController"))
    assertFalse(terminalScreen.contains("Terminal direct input"))
  }

  @Test
  fun terminalPinchZoomDoesNotConsumeSinglePointerScrollGestures() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()

    assertFalse(
      "Single-finger terminal drags must stay inside Termux TerminalView scrollback handling.",
      mainActivity.contains("detectTransformGestures"),
    )
    assertTrue(mainActivity.contains("override fun onScale(scale: Float): Float"))
    assertTrue(mainActivity.contains("TerminalViewClient"))
  }

  @Test
  fun terminalLayoutEmitsDebugSizeLogsForImeDiagnosis() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()

    assertTrue(mainActivity.contains("NBG_LAYOUT"))
    assertTrue(mainActivity.contains("root width="))
    assertTrue(mainActivity.contains("terminal width="))
    assertTrue(mainActivity.contains("extraKeys width="))
  }

  @Test
  fun terminalTabContextMenuUsesChineseActions() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()

    assertTrue(mainActivity.contains("DropdownMenuItem("))
    assertTrue(mainActivity.contains("Text(\"重命名\")"))
    assertTrue(mainActivity.contains("Text(\"删除\""))
    assertTrue(mainActivity.contains("indication = null"))
    assertFalse(mainActivity.contains("TextButton("))
    assertFalse(mainActivity.contains("Text(\"Rename\")"))
    assertFalse(mainActivity.contains("Text(\"Delete\")"))
  }

  @Test
  fun terminalScreenKeepsTerminalTabsBarWithBackAction() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val terminalScreen = mainActivity.substringAfter("private fun TerminalScreen(")
      .substringBefore("@Composable\nprivate fun TerminalEmptySessionPanel(")
    val tabsBar = mainActivity.substringAfter("private fun TerminalTabsBar(")
      .substringBefore("@Composable\n@OptIn(ExperimentalFoundationApi::class)")

    assertTrue(terminalScreen.contains("TerminalTabsBar("))
    assertTrue(mainActivity.contains("private fun TerminalTabsBar("))
    assertTrue(tabsBar.contains("onBack: () -> Unit"))
    assertTrue(tabsBar.contains("contentDescription = \"返回聊天\""))
    assertTrue(tabsBar.contains("TerminalTabChip("))
    assertFalse(mainActivity.contains("private fun TerminalBackBar("))
  }

  @Test
  fun terminalSelectionMoreMenuOpensSettings() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()

    assertTrue(mainActivity.contains("setOnCreateContextMenuListener"))
    assertTrue(mainActivity.contains("menu.add(\"设置\")"))
    assertTrue(mainActivity.contains("TerminalSettingsDialog("))
  }

  @Test
  fun terminalResizeIsOwnedByTermuxTerminalView() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val terminalScreen = mainActivity.substringAfter("private fun TerminalScreen(")
      .substringBefore("@Composable\nprivate fun TermuxTerminalViewHost(")

    assertTrue(mainActivity.contains("TerminalView(context, null)"))
    assertTrue(mainActivity.contains("attachSession(session.termuxSession)"))
    assertFalse(terminalScreen.contains("LocalView.current"))
    assertFalse(terminalScreen.contains("getWindowVisibleDisplayFrame"))
    assertFalse(terminalScreen.contains("TerminalViewportMetrics.fromMeasuredAndVisibleFrame"))
    assertFalse(terminalScreen.contains("manualKeyboardPaddingPx"))
    assertFalse(terminalScreen.contains(".padding(bottom ="))
    assertFalse(terminalScreen.contains("padding(bottom = with(density) { windowVisibleFrame.keyboardOverlapPx.toDp() })"))
    assertFalse(terminalScreen.contains("resizeController.onViewportChanged("))
  }

  @Test
  fun terminalViewDisposalAlwaysClearsOldSessionListener() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val host = mainActivity.substringAfter("private fun TermuxTerminalViewHost(")
      .substringBefore("private fun installTerminalContextMenu(")
    val disposeBlock = host.substringAfter("DisposableEffect(session)")
      .substringBefore("}\n}")

    assertTrue(disposeBlock.contains("session.setScreenChangedListener(null)"))
    assertFalse(disposeBlock.contains("getCurrentSession() === session.termuxSession"))
  }

  @Test
  fun terminalReadinessIsTrackedForEverySession() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val terminalScreen = mainActivity.substringAfter("private fun TerminalScreen(")
      .substringBefore("@Composable\nprivate fun TermuxTerminalViewHost(")
    val readinessObservers = mainActivity.substringAfter("private fun TerminalReadinessObservers(")
      .substringBefore("@Composable\nprivate fun TermuxTerminalViewHost(")
    val readiness = File("src/main/java/com/nbg/android/TerminalReadinessController.kt").readText()
    val workspace = File("src/main/java/com/nbg/android/TerminalWorkspace.kt").readText()

    assertTrue(terminalScreen.contains("TerminalReadinessObservers("))
    assertTrue(readinessObservers.contains("sessions.forEachIndexed { index, session ->"))
    assertTrue(readinessObservers.contains("session.screenText.collectAsState(initial = \"\")"))
    assertTrue(readinessObservers.contains("onReadinessChanged(index, session, output)"))
    assertTrue(terminalScreen.contains("workspace.recordReadinessOutput(index, session, output)"))
    assertTrue(workspace.contains("val readinessSnapshots = mutableListOf<TerminalReadinessSnapshot?>(null)"))
    assertTrue(workspace.contains("private val readinessController = TerminalReadinessController()"))
    assertTrue(workspace.contains("fun recordReadinessOutput(index: Int, sessionKey: Any, output: String): TerminalReadinessSnapshot?"))
    assertTrue(workspace.contains("val currentIndex = readinessIndexForSession(index, sessionKey) ?: return null"))
    assertTrue(workspace.contains("internal fun observeReadinessOutputFlow("))
    assertTrue(workspace.contains("outputFlow.collect { output ->"))
    assertTrue(workspace.contains("recordReadinessOutput(index, sessionKey, output)"))
    assertTrue(workspace.contains("private fun readinessIndexForSession(index: Int, sessionKey: Any): Int?"))
    assertTrue(workspace.contains("if (sessionKey is TerminalSession)"))
    assertTrue(workspace.contains("observeSessionReadiness(index, session)"))
    assertTrue(workspace.contains("val readinessVersion: StateFlow<Int>"))
    assertTrue(workspace.contains("fun terminalDiagnosticsSnapshot(): List<TerminalReadinessSnapshot>"))
    assertTrue(readiness.contains("internal val TerminalReadiness.terminalStatusLabel: String"))
    assertTrue(readiness.contains("private val readySessions = mutableSetOf<Any>()"))
    assertTrue(readiness.contains("fun clearSession(sessionKey: Any)"))
    assertTrue(readiness.contains("fun analyzeOutput(sessionKey: Any, output: String): TerminalReadinessSnapshot"))
    assertTrue(readiness.contains("if (sessionKey in readySessions)"))
    assertTrue(readiness.contains("readiness = TerminalReadiness.Ready"))
    assertTrue(readiness.contains("phase = TerminalStartupPhase.Ready"))
    assertTrue(readiness.contains("analyzeOutput(sessionKey, output).readiness"))
    assertFalse(terminalScreen.contains("readinessController.onOutput(active, output)"))
    assertFalse(terminalScreen.contains("readinessController.onOutput(output)"))
  }

  @Test
  fun terminalStartupResultCannotAttachToDeletedTab() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val workspace = File("src/main/java/com/nbg/android/TerminalWorkspace.kt").readText()
    val terminalScreen = mainActivity.substringAfter("private fun TerminalScreen(")
      .substringBefore("@Composable\nprivate fun TermuxTerminalViewHost(")

    assertTrue(workspace.contains("private val startupTokens"))
    assertTrue(workspace.contains("fun beginStartup(index: Int): Any?"))
    assertTrue(workspace.contains("fun isStartupTokenCurrent(index: Int, token: Any): Boolean"))
    assertTrue(workspace.contains("fun startupIndexForToken(token: Any): Int?"))
    assertTrue(workspace.contains("fun finishStartup(index: Int, token: Any)"))
    assertTrue(workspace.contains("fun updateTabStatus(index: Int, status: String): Boolean"))
    assertTrue(terminalScreen.contains("val startupToken = workspace.beginStartup(index)"))
    assertTrue(terminalScreen.contains("val startingIndex = workspace.startupIndexForToken(token) ?: return@launch"))
    assertTrue(terminalScreen.contains("val currentIndex = workspace.startupIndexForToken(token)"))
    assertTrue(terminalScreen.contains("workspace.sessions.getOrNull(currentIndex) != null"))
    assertTrue(terminalScreen.contains("session.close()"))
    assertTrue(terminalScreen.contains("workspace.updateTabStatus(startingIndex, \"启动中\")"))
    assertTrue(terminalScreen.contains("workspace.updateTabStatus(currentIndex, \"进入中\")"))
    assertTrue(terminalScreen.contains("workspace.updateTabStatus(currentIndex, \"失败\")"))
    assertTrue(terminalScreen.contains("workspace.finishStartup(currentIndex, token)"))
    assertTrue(mainActivity.contains("private fun TerminalEmptySessionPanel("))
    assertTrue(mainActivity.contains("Ubuntu 终端启动失败"))
    assertTrue(mainActivity.contains("label = if (startInProgress) \"启动中\" else \"重试\""))
    assertTrue(terminalScreen.contains("workspace.startupControllers[selectedTabIndex]?.markStartFailed()"))
    assertTrue(terminalScreen.contains("startTerminalForSelected()"))
    assertFalse(terminalScreen.contains("workspace.selectTab(index)\n        workspace.tabsController.updateSelectedStatus(\"启动中\")"))
    assertFalse(terminalScreen.contains("workspace.selectTab(index)\n          workspace.tabsController.updateSelectedStatus(\"进入中\")"))
    assertFalse(terminalScreen.contains("fun replaceStartInProgress("))
  }

  @Test
  fun terminalFollowsLatestPromptWhenKeyboardOrViewportChanges() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()

    assertTrue(mainActivity.contains("TerminalView"))
    assertFalse(mainActivity.contains("fun scrollToLatestPrompt()"))
    assertFalse(mainActivity.contains("scrollState.scrollTo(scrollState.maxValue)"))
    assertFalse(mainActivity.contains("verticalScroll(scrollState)"))
  }

  @Test
  fun terminalFocusIsOwnedByTermuxTerminalView() {
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()

    assertTrue(mainActivity.contains("setFocusableInTouchMode(true)"))
    assertTrue(mainActivity.contains("terminalView?.hasFocus() == true"))
    assertFalse(mainActivity.contains("LocalFocusManager.current"))
    assertFalse(mainActivity.contains("previousKeyboardOverlapPx"))
    assertFalse(mainActivity.contains("focusManager.clearFocus()"))
  }

  @Test
  fun terminalEnvironmentRefreshesVersionedRuntimeAssets() {
    val environment = File("../terminal-core/src/main/java/com/nbg/android/terminal/TerminalEnvironment.kt").readText()
    val runtime = File("../terminal-core/src/main/java/com/nbg/android/terminal/NbgCodeRuntime.kt").readText()
    val bootstrap = File("../terminal-core/src/main/java/com/nbg/android/terminal/UbuntuBootstrapScript.kt").readText()
    val runbook = File("../docs/runbooks/terminal-build-reliability-baseline.md").readText()

    assertTrue(environment.contains("copyVersionedAsset("))
    assertTrue(environment.contains("version = UbuntuBootstrapScript.UBUNTU_ARCHIVE_VERSION"))
    assertTrue(environment.contains("version = NbgCodeRuntime.nodeArchiveVersion"))
    assertTrue(environment.contains("\"${'$'}{target.name}.version\""))
    assertTrue(environment.contains("target.isFile && target.length() > 0L && marker.readTextOrNull() == version"))
    assertTrue(environment.contains("\"${'$'}{target.name}.tmp\""))
    assertTrue(environment.contains("Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)"))
    assertTrue(runtime.contains("const val nodeArchiveSha256"))
    assertTrue(runtime.contains("const val nodeArchiveVersion"))
    assertTrue(bootstrap.contains("const val UBUNTU_ARCHIVE_SHA256"))
    assertTrue(bootstrap.contains("const val UBUNTU_ARCHIVE_VERSION = UBUNTU_ARCHIVE + \"-\" + UBUNTU_ARCHIVE_SHA256"))
    assertTrue(bootstrap.contains("export UBUNTU_ARCHIVE_SHA256="))
    assertTrue(bootstrap.contains("actual_ubuntu_sha="))
    assertTrue(bootstrap.contains("Ubuntu archive checksum mismatch"))
    assertTrue(bootstrap.indexOf("actual_ubuntu_sha=") < bootstrap.indexOf("tar xf \"\${'$'}ubuntu_archive_path\""))
    assertTrue(runbook.contains("shell verifies SHA-256 before rootfs extraction"))
  }

  @Test
  fun ubuntuBootstrapPassesAndroidProxyAndRuntimeIntoPooledCommands() {
    val script = File("../terminal-core/src/main/java/com/nbg/android/terminal/UbuntuBootstrapScript.kt").readText()

    assertTrue(script.contains("write_android_runtime_profile()"))
    assertTrue(script.contains("nbg_android_export_proxy_aliases"))
    assertTrue(script.contains("nbg_android_detect_toolchains"))
    assertTrue(script.contains("nbg_android_fix_executables"))
    assertTrue(script.contains("append_proot_bind_arg /dev/random /dev/random"))
    assertTrue(script.contains("append_proot_bind_arg /dev/urandom /dev/urandom"))
    assertTrue(script.contains("JAVA_TOOL_OPTIONS=\"-Djava.security.egd=file:/dev/./urandom"))
    assertTrue(script.contains(". /etc/profile.d/01-nbg-android-proxy.sh 2>/dev/null || true"))
    assertTrue(script.contains(". /etc/profile.d/02-nbg-android-runtime.sh 2>/dev/null || true"))
    assertTrue(script.contains("COMMAND_TO_EXEC="))
    assertTrue(script.contains("eval \""))
    assertTrue(script.contains("COMMAND_TO_EXEC\"'"))
  }

  @Test
  fun agentUiClearsPendingContentBlockPatchesWhenConversationChanges() {
    val agentUi = readAgentUiSource()
    val clearBlock = agentUi.substringAfter("fun clearConversationUi() {")
      .substringBefore("fun appendLocalMessage")
    val replaceHistoryBlock = agentUi.substringAfter("fun replaceHistory(history: List<HanakoHistoryMessage>) {")
      .substringBefore("history.forEach")

    assertTrue(clearBlock.contains("contentBlockPatchState.clear()"))
    assertTrue(replaceHistoryBlock.contains("contentBlockPatchState.clear()"))
    assertFalse(File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText().contains("pendingContentBlockPatches"))
  }

  @Test
  fun androidExposesHanakoMcpPluginControls() {
    val agentUi = readAgentUiSource()
    val bridge = readHanakoBridgeSource()

    assertTrue(agentUi.contains("NbgShellPage.Mcp"))
    assertTrue(agentUi.contains("title = \"MCP 服务\""))
    assertTrue(agentUi.contains("onOpenMcp"))
    assertTrue(agentUi.contains("NbgMcpScreen("))
    assertTrue(agentUi.contains("hanako.loadMcpState()"))
    assertTrue(agentUi.contains("hanako.setMcpEnabled(it)"))
    assertTrue(agentUi.contains("hanako.addMcpConnector(it)"))
    assertTrue(agentUi.contains("hanako.runMcpConnectorAction(connectorId, action)"))
    assertTrue(agentUi.contains("hanako.deleteMcpConnector(connectorId)"))
    assertTrue(agentUi.contains("hanako.setAgentMcpConnector(connectorId, enabled)"))
    assertTrue(agentUi.contains("hanako.setAgentMcpTool(connectorId, toolName, enabled)"))
    assertTrue(agentUi.contains("MCP 服务"))
    assertTrue(agentUi.contains("添加 MCP"))
    assertTrue(agentUi.contains("删除 MCP 服务"))
    assertTrue(agentUi.contains("onRequestDelete"))
    assertTrue(agentUi.contains("http://127.0.0.1:37666/mcp"))
    assertTrue(agentUi.contains("NBG_MCP_TEMPLATES"))
    assertTrue(agentUi.contains("codebase-memory-mcp"))
    assertTrue(agentUi.contains("Cognee Memory"))
    assertTrue(agentUi.contains("transport = \"stdio\""))
    assertTrue(agentUi.contains("label = { Text(\"命令\") }"))
    assertTrue(agentUi.contains("nbgSplitMcpArgs"))
    assertTrue(agentUi.contains("Agent 开"))

    assertTrue(bridge.contains("HanakoMcpState"))
    assertTrue(bridge.contains("HanakoMcpConnectorInput"))
    assertTrue(bridge.contains("val command: String = \"\""))
    assertTrue(bridge.contains("val args: List<String> = emptyList()"))
    assertTrue(bridge.contains(".put(\"command\", command.trim())"))
    assertTrue(bridge.contains(".put(\"args\", JSONArray(args.map"))
    assertTrue(bridge.contains(".put(\"cwd\", cwd.trim())"))
    assertTrue(bridge.contains("HANA_DEFAULT_MCP_AGENT_ID"))
    assertTrue(bridge.contains("/api/plugins/mcp/state?agentId="))
    assertTrue(bridge.contains("/api/plugins/mcp/settings/enabled"))
    assertTrue(bridge.contains("/api/plugins/mcp/connectors/"))
    assertTrue(bridge.contains("/api/plugins/mcp/agents/"))
    assertTrue(bridge.contains("fun loadMcpState(agentId: String = HANA_DEFAULT_MCP_AGENT_ID)"))
    assertTrue(bridge.contains("fun addMcpConnector(input: HanakoMcpConnectorInput"))
    assertTrue(bridge.contains("fun saveMcpConnector(info: HanakoServerInfo, input: HanakoMcpConnectorInput)"))
    assertTrue(bridge.contains("fun deleteMcpConnector("))
    assertTrue(bridge.contains("\"DELETE\""))
    assertTrue(bridge.contains("fun setMcpEnabled(enabled: Boolean, agentId: String = HANA_DEFAULT_MCP_AGENT_ID)"))
    assertTrue(bridge.contains("fun setAgentMcpTool("))
    assertTrue(bridge.contains("parseHanakoMcpState"))
    assertTrue(bridge.contains("connectors = root.optJSONArray(\"connectors\")"))
    assertTrue(bridge.contains("root.optJSONArray(\"servers\")"))
  }

  @Test
  fun capabilityRegistryFeedsDrawerAndAgentSurfaces() {
    val registry = File("src/main/java/com/nbg/android/NbgCapabilityRegistry.kt").readText()
    val agentUi = readAgentUiSource()
    val drawer = File("src/main/java/com/nbg/android/NbgAgentDrawerUi.kt").readText()
    val agents = File("src/main/java/com/nbg/android/NbgAgentCapabilityPagesUi.kt").readText()
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val terminalWorkspace = File("src/main/java/com/nbg/android/TerminalWorkspace.kt").readText()
    val diagnostics = File("src/main/java/com/nbg/android/NbgDiagnosticsExport.kt").readText()
    val contract = File("../docs/contracts/capability-registry-contract.md").readText()

    assertTrue(registry.contains("data class NbgCapability("))
    listOf("id", "title", "status", "health", "lastError", "isBeta", "diagnosticsAction").forEach {
      assertTrue(registry.contains("val $it"))
    }
    assertTrue(registry.contains("terminalReadiness: List<TerminalReadinessSnapshot>"))
    assertTrue(registry.contains("internal fun nbgTerminalCapability(readiness: List<TerminalReadinessSnapshot>)"))
    assertTrue(registry.contains("TerminalReadiness.Failed"))
    assertTrue(registry.contains("TerminalReadiness.Installing"))
    assertTrue(registry.contains("failedCount"))
    assertTrue(registry.contains("nbgTerminalReadinessStatus"))
    assertTrue(registry.contains("failed?.diagnostic.orEmpty()"))
    listOf("terminal", "hanako", "url_api", "mcp", "skills", "memory", "ftp", "pets", "feedback_export").forEach {
      assertTrue(registry.contains("id = \"$it\""))
    }
    assertTrue(registry.contains("val firstProblem: NbgCapability?"))
    assertTrue(registry.contains("val summaryLabel: String"))
    assertTrue(registry.contains("capabilities.firstOrNull { it.health == NbgCapabilityHealth.Unknown }"))
    assertTrue(diagnostics.contains("private fun NbgCapabilityRegistry.toDiagnosticsJson(): JSONArray"))
    assertTrue(diagnostics.contains("capabilities.map { capability ->"))
    assertFalse(diagnostics.contains("capabilities.filter { capability ->"))
    assertFalse(diagnostics.contains("capabilities.filterNot"))
    assertTrue(contract.contains("Diagnostics export v1 must include the full redacted capability registry"))
    assertTrue(contract.contains("including Healthy capabilities"))
    assertTrue(contract.contains("Failed/degraded/unknown summaries belong in `recentErrors`"))
    assertFalse(contract.contains("Whether feedback export should include the full registry as JSON or only failed/degraded capabilities."))

    assertTrue(agentUi.contains("val capabilityRegistry = nbgBuildCapabilityRegistry("))
    assertTrue(agentUi.contains("terminalWorkspace.readinessVersion.collectAsState()"))
    assertTrue(agentUi.contains("terminalWorkspace.terminalDiagnosticsSnapshot()"))
    assertTrue(agentUi.contains("terminalReadiness = terminalReadiness"))
    assertTrue(agentUi.contains("capabilities = capabilityRegistry"))
    assertTrue(mainActivity.contains("workspace.markStartupFailed(currentIndex"))
    assertTrue(terminalWorkspace.contains("fun markStartupFailed(index: Int"))
    assertTrue(drawer.contains("capabilities: NbgCapabilityRegistry"))
    assertTrue(drawer.contains("capabilityLabel = capabilities.summaryLabel"))
    assertTrue(drawer.contains("capabilityHealth = capabilities.worstHealth"))
    assertTrue(drawer.contains("capabilities.byId(\"mcp\")"))
    assertTrue(drawer.contains("capabilities.byId(\"skills\")"))
    assertTrue(drawer.contains("capabilities.byId(\"ftp\")"))
    assertTrue(drawer.contains("capabilities.byId(\"feedback_export\")"))
    assertTrue(drawer.contains("onOpenDiagnosticsExport"))
    assertTrue(drawer.contains("NbgSheetTag(state, primary = active, warning = warning, danger = danger)"))
    assertTrue(agents.contains("capabilities: NbgCapabilityRegistry"))
    assertTrue(agents.contains("capabilities.byId(\"memory\").nbgAgentsCapabilityLabel"))
  }

  @Test
  fun diagnosticsExportIsRedactedAndUserTriggeredFromDrawer() {
    val diagnostics = File("src/main/java/com/nbg/android/NbgDiagnosticsExport.kt").readText()
    val diagnosticsShare = File("src/main/java/com/nbg/android/NbgDiagnosticsExportShare.kt").readText()
    val dialog = File("src/main/java/com/nbg/android/NbgDiagnosticsExportUi.kt").readText()
    val agentUi = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    val drawer = File("src/main/java/com/nbg/android/NbgAgentDrawerUi.kt").readText()
    val registry = File("src/main/java/com/nbg/android/NbgCapabilityRegistry.kt").readText()
    val manifest = File("src/main/AndroidManifest.xml").readText()
    val diagnosticsFilePaths = File("src/main/res/xml/diagnostics_file_paths.xml").readText()
    val localServiceContract = File("../docs/contracts/local-service-boundary-contract.md").readText()
    val diagnosticsContract = File("../docs/contracts/diagnostics-export-contract.md").readText()

    assertTrue(diagnostics.contains("schema\", \"nbg-diagnostics-v1\""))
    assertTrue(diagnostics.contains("redactionVersion"))
    listOf(
      "api_keys",
      "hanako_tokens",
      "raw_hanako_server_info",
      "ftp_passwords",
      "terminal_output",
      "conversation_text",
      "memory_items",
      "user_code",
      "mcp_commands_args_paths",
      "skill_file_paths",
    ).forEach {
      assertTrue(diagnostics.contains(it))
    }
    listOf("launcher", "runtimePatch", "terminal", "recentErrors").forEach {
      assertTrue(diagnostics.contains("\"$it\""))
    }
    listOf(
      "\"streamResume\"",
      "\"requestCount\"",
      "\"resumeCount\"",
      "\"replayedEventCount\"",
      "\"acceptedReplayEventCount\"",
      "\"skippedReplayEventCount\"",
      "\"duplicateReplayEventCount\"",
      "\"truncatedResumeCount\"",
      "\"resetResumeCount\"",
      "\"cursorPresent\"",
      "\"lastSeq\"",
    ).forEach {
      assertTrue(diagnostics.contains(it))
    }
    listOf(
      "state",
      "needsAttention",
      "activePatchSetMatches",
      "patchCount",
      "appliedCount",
      "skippedCount",
      "failedCount",
      "attentionCount",
      "targetMarkerPresent",
      "activeMarkerPresent",
      "targetRuntimeVersion",
      "markerMatched",
      "markerMatchState",
    ).forEach {
      assertTrue(diagnostics.contains("\"$it\""))
    }
    assertTrue(diagnostics.contains("android-runtime-patches.json"))
    assertTrue(diagnostics.contains("android-runtime-patches.active"))
    assertTrue(diagnostics.contains(".put(\"targetRuntimeVersion\""))
    assertTrue(diagnostics.contains("root.optString(\"targetRuntimeVersion\")"))
    assertTrue(diagnostics.contains("internal fun nbgReadDiagnosticsRuntimePatchSnapshotFromHomes"))
    assertTrue(diagnostics.contains("运行补丁"))
    assertTrue(diagnostics.contains("\"failureCodes\""))
    assertTrue(diagnostics.contains("nbgLauncherFailureCodes"))
    listOf(
      "server_info_missing",
      "launcher_start_failed",
      "pack_exit_nonzero",
      "bundled_pack_extract_failed",
      "server_source_missing",
      "port_in_use",
      "node_modules_missing",
    ).forEach {
      assertTrue(diagnostics.contains(it))
    }
    assertFalse(diagnostics.contains(".put(\"targetMarker\""))
    assertFalse(diagnostics.contains(".put(\"activeMarker\""))
    assertTrue(diagnostics.contains("TerminalReadinessSnapshot"))
    assertTrue(diagnostics.contains("\"readyCount\""))
    assertTrue(diagnostics.contains("\"installingCount\""))
    assertTrue(diagnostics.contains("\"failedCount\""))
    assertTrue(diagnostics.contains("withTerminalReadiness(snapshot.terminalReadiness)"))
    assertTrue(diagnostics.contains("if (capability.id == \"terminal\") nbgTerminalCapability(readiness) else capability"))
    assertTrue(diagnosticsContract.contains("Terminal capability health refreshed from current `TerminalReadinessSnapshot` values."))
    assertTrue(diagnosticsContract.contains("target runtime version"))
    assertTrue(diagnosticsContract.contains("NbgDiagnosticsExportTest.diagnosticsExportRefreshesStaleTerminalCapabilityFromReadinessCounts"))
    assertTrue(diagnosticsContract.contains("launcher failure codes"))
    assertTrue(diagnosticsContract.contains("NbgDiagnosticsExportTest.diagnosticsExportIncludesStructuredLauncherFailureCodesWithoutRawValues"))
    assertTrue(diagnosticsContract.contains("FileProvider"))
    assertTrue(diagnosticsContract.contains("avoid putting the full JSON in `Intent.EXTRA_TEXT`"))
    assertTrue(diagnosticsContract.contains("NbgDiagnosticsExportTest.diagnosticsExportAttachmentFileWriterCreatesJsonAttachmentAndPrunesOldExports"))
    assertFalse(diagnosticsContract.contains("Whether Terminal readiness should feed Capability Registry health directly in a separate Capability Registry v2 issue."))
    assertFalse(diagnosticsContract.contains("Whether v2 should write an attachment file through `FileProvider` instead of text share."))
    assertFalse(diagnosticsContract.contains("Whether v2 should include more structured launcher failure codes instead of redacted log tails."))
    assertTrue(diagnostics.contains("internal fun nbgRedactDiagnosticText"))
    assertTrue(diagnostics.contains("authorization"))
    assertTrue(diagnostics.contains("sk-[redacted]"))
    assertTrue(diagnostics.contains("pid\\s*=\\s*"))
    assertTrue(diagnostics.contains("port\\s*=\\s*"))
    assertTrue(diagnostics.contains("version\\s*=\\s*"))
    assertTrue(diagnostics.contains("[a-z0-9][a-z0-9._-]*@"))
    assertTrue(diagnostics.contains("already running on"))
    assertTrue(diagnostics.contains("observed healthy server on"))
    assertTrue(diagnostics.contains("https?://(?:127\\.0\\.0\\.1|localhost):"))
    assertTrue(diagnostics.contains("listening on\\s+:"))
    assertTrue(diagnostics.contains("NBG_DIAGNOSTIC_LOG_TAIL_MAX_BYTES = 64 * 1024"))
    assertTrue(diagnostics.contains("file.inputStream().use { input ->"))
    assertTrue(diagnostics.contains("input.readBytes()"))
    assertTrue(diagnostics.contains("internal fun nbgSafeDiagnosticTailForTest"))
    assertFalse(diagnostics.contains("readLines(Charsets.UTF_8).takeLast"))
    assertTrue(diagnostics.contains("hasPassword"))
    val launcherExport = diagnostics
      .substringAfter("private fun NbgDiagnosticsLauncherSnapshot.toDiagnosticsJson()")
      .substringBefore("private fun NbgDiagnosticsRuntimePatchSnapshot.toDiagnosticsJson")
    assertTrue(launcherExport.contains("\"serverInfoPresent\""))
    assertTrue(launcherExport.contains("\"pidPresent\""))
    assertTrue(launcherExport.contains("\"versionPresent\""))
    assertTrue(launcherExport.contains("\"failureCodes\""))
    assertTrue(launcherExport.contains("\"launchLogTail\""))
    assertTrue(launcherExport.contains("\"serverLogTail\""))
    assertFalse(launcherExport.contains("\"serverInfo\""))
    assertFalse(launcherExport.contains("\"serverInfoJson\""))
    assertFalse(launcherExport.contains("\"rawServerInfo\""))
    assertFalse(launcherExport.contains("\"token\""))
    assertFalse(launcherExport.contains("\"port\""))
    assertFalse(launcherExport.contains(".put(\"pid\""))
    assertFalse(launcherExport.contains(".put(\"version\""))
    assertFalse(diagnostics.contains(".put(\"apiKey\""))
    assertFalse(diagnostics.contains(".put(\"password\", this?.password"))
    assertFalse(diagnostics.contains(".put(\"sessionPath\""))
    assertFalse(diagnostics.contains(".put(\"items\", items"))
    assertFalse(diagnostics.contains(".put(\"command\", it.command"))
    assertFalse(diagnostics.contains(".put(\"filePath\""))
    assertTrue(localServiceContract.contains("Diagnostics must not export raw Hanako `server-info.json` by default."))
    assertTrue(localServiceContract.contains("no raw server-info JSON, token, port, pid, or version value"))
    assertFalse(localServiceContract.contains("Whether Hanako server-info should be redacted in diagnostic export by default."))
    assertTrue(diagnosticsContract.contains("Hanako launcher/server-info derived booleans only"))
    assertTrue(diagnosticsContract.contains("raw Hanako server-info"))
    assertTrue(diagnosticsContract.contains("bounded launcher/server log tails"))
    assertTrue(diagnosticsContract.contains("exported log tails must pass through the shared diagnostic redactor"))
    assertTrue(diagnosticsContract.contains("except bounded Hanako diagnostic log tails"))
    assertFalse(diagnosticsContract.contains("it does not fetch raw server logs"))
    assertFalse(diagnosticsContract.contains("v1 does not perform network or filesystem log collection"))
    assertFalse(diagnosticsContract.contains("proot filesystem contents."))

    assertTrue(dialog.contains("NbgDiagnosticsExportDialog"))
    assertTrue(dialog.contains("已排除 API Key、Token、FTP 密码、终端输出、对话正文、Memory 内容和用户代码。"))
    assertTrue(dialog.contains("nbgDiagnosticsRuntimePatchUiSummary(exportText)"))
    assertTrue(dialog.contains("runtimePatchSummary.text"))
    assertTrue(dialog.contains("NbgDialogAction(label = \"分享\""))
    assertTrue(dialog.contains("NbgDialogAction(label = \"复制\""))
    assertTrue(agentUi.contains("val diagnosticsExportState = remember { NbgAgentDiagnosticsExportState() }"))
    assertTrue(agentUi.contains("diagnosticsExportState.open(exportText)"))
    assertTrue(agentUi.contains("clipboard.setText(AnnotatedString(diagnosticsExportState.exportText))"))
    assertTrue(agentUi.contains("if (diagnosticsExportState.isOpen)"))
    assertTrue(agentUi.contains("exportText = diagnosticsExportState.exportText"))
    assertTrue(agentUi.contains("onDismiss = { diagnosticsExportState.dismiss() }"))
    assertFalse(agentUi.contains("var diagnosticsExportOpen by remember"))
    assertFalse(agentUi.contains("var diagnosticsExportText by remember"))
    assertTrue(agentUi.contains("nbgCreateDiagnosticsExportSnapshot("))
    assertTrue(agentUi.contains("nbgBuildDiagnosticsExportJson("))
    assertTrue(agentUi.contains("NbgFileShareServerRegistry.diagnosticSnapshot(context)"))
    assertTrue(agentUi.contains("fileShareState = fileShareUiState.serverState ?: NbgFileShareServerRegistry.diagnosticSnapshot(context)"))
    assertFalse(
      agentUi
        .substringAfter("fun openDiagnosticsExportDialog()")
        .substringBefore("ModalNavigationDrawer(")
        .contains("NbgFileShareServerRegistry.snapshot(context)"),
    )
    assertTrue(agentUi.contains("nbgBuildDiagnosticsExportShareIntent(context, diagnosticsExportState.exportText)"))
    assertTrue(agentUi.contains("error is ActivityNotFoundException"))
    assertTrue(agentUi.contains("诊断导出分享失败"))
    assertFalse(agentUi.contains("putExtra(Intent.EXTRA_TEXT, diagnosticsExportState.exportText)"))
    assertTrue(diagnosticsShare.contains("FileProvider.getUriForFile"))
    assertTrue(diagnosticsShare.contains("nbgDiagnosticsExportFileProviderAuthority(context)"))
    assertTrue(diagnosticsShare.contains("Intent(Intent.ACTION_SEND)"))
    assertTrue(diagnosticsShare.contains("type = NBG_DIAGNOSTICS_EXPORT_MIME_TYPE"))
    assertTrue(diagnosticsShare.contains("putExtra(Intent.EXTRA_STREAM, uri)"))
    assertFalse(diagnosticsShare.contains("Intent.EXTRA_TEXT"))
    assertTrue(diagnosticsShare.contains("ClipData.newUri(context.contentResolver"))
    assertTrue(diagnosticsShare.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
    assertTrue(diagnosticsShare.contains("nbgWriteDiagnosticsExportAttachmentFile"))
    assertTrue(diagnosticsShare.contains("NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_DIR"))
    assertTrue(manifest.contains("android:name=\"androidx.core.content.FileProvider\""))
    assertTrue(manifest.contains("android:authorities=\"\${applicationId}.diagnostics\""))
    assertTrue(manifest.contains("android:exported=\"false\""))
    assertTrue(manifest.contains("android:grantUriPermissions=\"true\""))
    assertTrue(manifest.contains("@xml/diagnostics_file_paths"))
    assertTrue(diagnosticsFilePaths.contains("<cache-path"))
    assertTrue(diagnosticsFilePaths.contains("name=\"diagnostics\""))
    assertTrue(diagnosticsFilePaths.contains("path=\"diagnostics/\""))
    assertTrue(agentUi.contains("NbgDiagnosticsExportDialog("))
    assertTrue(drawer.contains("title = \"诊断导出\""))
    assertTrue(drawer.contains("onClick = onOpenDiagnosticsExport"))
    assertTrue(registry.contains("id = \"feedback_export\""))
    assertTrue(registry.contains("status = \"可导出\""))
  }

  @Test
  fun uiAutomationSmokeSkeletonIsConfigured() {
    val build = File("build.gradle.kts").readText()
    val versions = File("../gradle/libs.versions.toml").readText()
    val smoke = File("src/androidTest/java/com/nbg/android/NbgUiSmokeTest.kt").readText()
    val debugManifest = File("src/debug/AndroidManifest.xml").readText()
    val preview = File("src/debug/java/com/nbg/android/NbgToolCardPreviewActivity.kt").readText()
    val runbook = File("../docs/runbooks/ui-automation-smoke-skeleton.md").readText()
    val deviceSmokeScript = File("../scripts/nbg_device_smoke.sh").readText()

    assertTrue(build.contains("testInstrumentationRunner = \"androidx.test.runner.AndroidJUnitRunner\""))
    assertTrue(build.contains("androidTestImplementation(libs.compose.ui.test.junit4)"))
    assertTrue(build.contains("debugImplementation(libs.compose.ui.test.manifest)"))
    assertTrue(versions.contains("compose-ui-test-junit4"))
    assertTrue(versions.contains("androidx-test-runner"))

    assertTrue(smoke.contains("class NbgUiSmokeTest"))
    assertTrue(smoke.contains("launchShowsChatInput"))
    assertTrue(smoke.contains("drawerSettingsExposeCoreEntrypoints"))
    assertTrue(smoke.contains("drawerCanOpenTerminalSkillsMcpAndDiagnostics"))
    assertTrue(smoke.contains("class NbgToolCardUiSmokeTest"))
    assertTrue(smoke.contains("NbgUiSmokeArtifactsRule"))
    assertTrue(smoke.contains("ui-smoke"))
    assertTrue(smoke.contains("captureToImage"))
    assertTrue(smoke.contains("performScrollTo()"))
    listOf("消息输入", "打开会话抽屉", "工作台设置", "终端", "MCP 服务", "Skills", "诊断导出").forEach {
      assertTrue(smoke.contains(it))
    }

    assertTrue(debugManifest.contains("NbgToolCardPreviewActivity"))
    assertTrue(preview.contains("NbgToolStatusCard("))
    assertTrue(preview.contains("BUILD SUCCESSFUL"))
    assertTrue(deviceSmokeScript.contains("NBG_DEVICE_SMOKE_CLASSES"))
    assertTrue(deviceSmokeScript.contains("com.nbg.android.NbgUiSmokeTest,com.nbg.android.NbgToolCardUiSmokeTest"))
    assertTrue(deviceSmokeScript.contains("app-debug-androidTest.apk"))
    assertTrue(deviceSmokeScript.contains("am instrument -w -r"))
    assertTrue(deviceSmokeScript.contains("failure_artifact_count"))
    assertTrue(deviceSmokeScript.contains("missing_ok_summary"))
    assertTrue(deviceSmokeScript.contains("app_apk_bytes"))
    assertTrue(deviceSmokeScript.contains("test_apk_bytes"))
    assertTrue(deviceSmokeScript.contains("install_failure_code=-99"))
    assertTrue(deviceSmokeScript.contains("insufficient_device_storage"))
    assertTrue(deviceSmokeScript.contains("record_device_storage"))
    assertTrue(runbook.contains("./gradlew --no-daemon :app:compileDebugAndroidTestKotlin"))
    assertTrue(runbook.contains("./gradlew --no-daemon :app:connectedDebugAndroidTest"))
    assertTrue(runbook.contains("NBG_DEVICE_SERIAL=<adb-serial> ./scripts/nbg_device_smoke.sh"))
    assertTrue(runbook.contains("/root/android-sdk/platform-tools/adb"))
    assertTrue(runbook.contains("Illegal instruction"))
    assertTrue(runbook.contains("*_insufficient_device_storage"))
    assertTrue(runbook.contains("Android/data/com.nbg.android/files/Pictures/ui-smoke/"))
  }

  @Test
  fun releaseBuildHygieneAndSupplyChainGateIsDocumented() {
    val settings = File("../settings.gradle.kts").readText()
    val appBuild = File("build.gradle.kts").readText()
    val terminalBuild = File("../terminal-core/build.gradle.kts").readText()
    val script = File("../scripts/nbg_release_gate.sh").readText()
    val reviewScript = File("../scripts/nbg_release_evidence_review.sh").readText()
    val releaseDoc = File("../docs/release/2026-07-04-001-release-build-hygiene-and-supply-chain.md").readText()
    val releaseNotesTemplate = File("../docs/release/NBG_PUBLIC_BETA_RELEASE_NOTES_TEMPLATE.md").readText()
    val publicBetaGate = File("../docs/release/NBG_PUBLIC_BETA_RELEASE_GATE.md").readText()
    val qualityScript = File("../scripts/nbg_quality_report.sh").readText()
    val qualityReportDoc = File("../docs/quality/NBG_WEEKLY_QUALITY_REPORT.md").readText()
    val feedbackTemplate = File("../.github/ISSUE_TEMPLATE/public_beta_feedback.md").readText()
    val privacyStatement = File("../docs/release/NBG_PUBLIC_BETA_PRIVACY_LOCAL_DATA.md").readText()
    val quickStart = File("../docs/release/NBG_PUBLIC_BETA_QUICK_START.md").readText()

    assertTrue(settings.contains("repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)"))
    listOf("google()", "mavenCentral()", "gradlePluginPortal()", "maven(\"https://jitpack.io\")").forEach {
      assertTrue(settings.contains(it))
    }
    assertTrue(appBuild.contains("versionCode = 1"))
    assertTrue(appBuild.contains("versionName = \"0.1.0\""))
    assertTrue(appBuild.contains("abiFilters += listOf(\"arm64-v8a\")"))
    assertTrue(appBuild.contains("getByName(\"release\")"))
    assertTrue(appBuild.contains("isMinifyEnabled = true"))
    assertTrue(appBuild.contains("isShrinkResources = true"))
    assertTrue(appBuild.contains("create(\"profile\")"))
    assertTrue(terminalBuild.contains("nodeArchiveUrl"))
    assertTrue(terminalBuild.contains("nodeArchiveSha256"))
    assertTrue(terminalBuild.contains("f3d5a797b5d210ce8e2cb265544c8e482eaedcb8aa409a8b46da7e8595d0dda0"))
    assertTrue(terminalBuild.contains("MessageDigest.getInstance(\"SHA-256\")"))
    assertTrue(terminalBuild.contains("outputs.upToDateWhen { false }"))
    assertTrue(terminalBuild.contains("Existing bundled Node.js archive checksum mismatch"))
    assertTrue(terminalBuild.contains("Downloaded Node.js archive checksum mismatch"))
    assertTrue(terminalBuild.contains("Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)"))
    assertTrue(terminalBuild.contains("node-v24-linux-arm64.tar.xz"))
    assertTrue(terminalBuild.contains("task.name.endsWith(\"Assets\")"))
    assertTrue(terminalBuild.contains("task.name.endsWith(\"AssetsCopy\")"))
    assertTrue(terminalBuild.contains("task.name.endsWith(\"LintModel\")"))
    assertTrue(terminalBuild.contains("task.name.contains(\"Lint\")"))
    assertTrue(terminalBuild.contains("task.name.startsWith(\"lint\")"))

    assertTrue(script.startsWith("#!/usr/bin/env bash"))
    assertTrue(script.contains("set -euo pipefail"))
    assertTrue(script.contains(":app:lintRelease"))
    assertTrue(script.contains(":app:testDebugUnitTest :terminal-core:testDebugUnitTest :android-mcp-server:testDebugUnitTest"))
    assertTrue(script.contains(":app:assembleRelease :android-mcp-server:assembleDebug"))
    assertTrue(script.contains("SKIP_LINT=\"${'$'}{NBG_RELEASE_SKIP_LINT:-0}\""))
    assertTrue(script.contains("SKIP_TESTS=\"${'$'}{NBG_RELEASE_SKIP_TESTS:-0}\""))
    assertTrue(script.contains("PUBLIC_BETA_CANDIDATE=\"${'$'}{NBG_RELEASE_PUBLIC_BETA_CANDIDATE:-0}\""))
    assertTrue(script.contains("Public beta release gate must not use NBG_RELEASE_SKIP_LINT or NBG_RELEASE_SKIP_TESTS."))
    assertTrue(script.contains("public_beta_candidate=${'$'}PUBLIC_BETA_CANDIDATE"))
    assertTrue(script.contains("lint_skipped="))
    assertTrue(script.contains("tests_skipped="))
    assertTrue(script.contains("public_beta_eligible=false"))
    assertTrue(script.contains("skip_reason=local_iteration_only"))
    assertTrue(script.contains("public_beta_eligible=true"))
    assertTrue(script.contains("skip_reason=none"))
    assertTrue(script.contains("checksums.sha256"))
    assertTrue(script.contains("assets.sha256"))
    assertTrue(script.contains("review.txt"))
    assertTrue(script.contains("nbg_release_evidence_review.sh"))
    assertTrue(script.contains("sha256sum"))
    assertTrue(script.contains("app/src/main/assets"))
    assertTrue(script.contains("*.nbgpack"))
    assertTrue(script.contains("terminal-core/src/main/assets"))

    assertTrue(reviewScript.startsWith("#!/usr/bin/env bash"))
    assertTrue(reviewScript.contains("review_version=nbg-release-evidence-review-v1"))
    assertTrue(reviewScript.contains("candidate_evidence_complete=true"))
    assertTrue(reviewScript.contains("publish_blocker=external_release_signing_required"))
    assertTrue(reviewScript.contains("app-release-unsigned.apk"))
    assertTrue(reviewScript.contains("android-mcp-server-debug.apk"))
    assertTrue(reviewScript.contains("RELEASE_NOTES_TEMPLATE"))
    assertTrue(reviewScript.contains("release_notes_template"))
    assertTrue(reviewScript.contains("FEEDBACK_TEMPLATE"))
    assertTrue(reviewScript.contains("feedback_issue_template"))
    assertTrue(reviewScript.contains("PRIVACY_STATEMENT"))
    assertTrue(reviewScript.contains("privacy_local_data_statement"))
    assertTrue(reviewScript.contains("QUICK_START"))
    assertTrue(reviewScript.contains("public_beta_quick_start"))
    assertTrue(reviewScript.contains("Do not paste API keys"))
    assertTrue(reviewScript.contains("raw diagnostics that you have not reviewed"))
    assertTrue(reviewScript.contains("Android Keystore-backed encrypted storage"))
    assertTrue(reviewScript.contains("Any future telemetry, cloud sync, or automatic upload requires a separate privacy-reviewed plan"))
    assertTrue(reviewScript.contains("Verify the APK SHA-256 checksum"))
    assertTrue(reviewScript.contains("Review file preview and diff before approving writes"))
    assertTrue(reviewScript.contains("## Release Summary"))
    assertTrue(reviewScript.contains("## Privacy And Local Data"))
    assertTrue(reviewScript.contains("## Checksums And Supply Chain"))
    assertTrue(reviewScript.contains("## Known Issues"))
    assertTrue(reviewScript.contains("lint_errors_zero=true"))
    assertTrue(reviewScript.contains("sha256sum -c \"${'$'}CHECKSUM_FILE\""))
    assertTrue(reviewScript.contains("sha256sum -c \"${'$'}ASSET_FILE\""))
    assertTrue(reviewScript.contains("TEST-*.xml"))
    assertTrue(reviewScript.contains("apksigner verify --verbose \"${'$'}APP_RELEASE_APK\""))
    assertTrue(reviewScript.contains("mcp_debug_apk_signed=true"))

    listOf(
      "Release Build Hygiene And Supply Chain",
      "release signing",
      "arm64-v8a",
      "JitPack",
      "Node",
      "syncNbgNodeArchive",
      "verifies the downloaded Node archive SHA-256",
      "Android lint tasks/model generation so release lint cannot read a generated Node archive through an implicit Gradle dependency",
      "Ubuntu",
      "PetDex",
      "MCP",
      "./scripts/nbg_release_gate.sh",
      "build/release-gate/checksums.sha256",
      "build/release-gate/assets.sha256",
      "build/release-gate/review.txt",
      "build/quality/weekly-quality-report.md",
      "./scripts/nbg_quality_report.sh",
      "docs/release/NBG_PUBLIC_BETA_RELEASE_NOTES_TEMPLATE.md",
      "docs/release/NBG_PUBLIC_BETA_PRIVACY_LOCAL_DATA.md",
      "docs/release/NBG_PUBLIC_BETA_QUICK_START.md",
      ".github/ISSUE_TEMPLATE/public_beta_feedback.md",
      "Feedback issue template",
      "Privacy and quick-start templates",
      "Release notes template",
      "NBG_RELEASE_PUBLIC_BETA_CANDIDATE=1",
      "public_beta_eligible=true",
      "lint_skipped=false",
      "tests_skipped=false",
      "skip_reason=none",
      "candidate_evidence_complete=true",
      "publish_ready=false",
    ).forEach {
      assertTrue(releaseDoc.contains(it))
    }
    listOf(
      "NBG Android Public Beta Release Notes Template",
      "## Release Summary",
      "## What Is Included",
      "## Beta Labels And Exceptions",
      "## Privacy And Local Data",
      "docs/release/NBG_PUBLIC_BETA_PRIVACY_LOCAL_DATA.md",
      "## Checksums And Supply Chain",
      "candidate_evidence_complete=true",
      "publish_ready=true",
      "## Known Issues",
      "## Verification Evidence",
      "## Feedback And Diagnostics",
      ".github/ISSUE_TEMPLATE/public_beta_feedback.md",
      "review diagnostics for secrets first",
      "## Upgrade And Recovery Notes",
      "docs/release/NBG_PUBLIC_BETA_QUICK_START.md",
      "Android MCP server: beta",
      "PetDex and downloaded pet resources: beta",
      "arm64-v8a",
      "Diagnostics export is user-triggered and redacted by default",
    ).forEach {
      assertTrue(releaseNotesTemplate.contains(it))
    }
    listOf(
      "#!/usr/bin/env bash",
      "build/quality",
      "weekly-quality-report.md",
      "sum_test_xml",
      "app/build/test-results/testDebugUnitTest",
      "terminal-core/build/test-results/testDebugUnitTest",
      "android-mcp-server/build/test-results/testDebugUnitTest",
      "build/release-gate/review.txt",
      "build/device-smoke/summary.txt",
      "Startup time",
      "Hanako connection success rate",
      "Terminal startup success rate",
      "Build-loop success rate",
      "Crash/ANR count",
      "Tool failure rate",
      "Session recovery success rate",
      "Model configuration success rate",
      "Skills usage count",
      "Feedback/diagnostic export count",
      "does not add cloud telemetry",
    ).forEach {
      assertTrue(qualityScript.contains(it))
    }
    listOf(
      "NBG Weekly Quality Report",
      "./scripts/nbg_quality_report.sh",
      "build/quality/weekly-quality-report.md",
      "Missing inputs are reported as `missing`",
      "Do not add telemetry or automatic upload",
      "Startup time",
      "Feedback/diagnostic export count",
    ).forEach {
      assertTrue(qualityReportDoc.contains(it))
      assertTrue(publicBetaGate.contains(it) || it == "NBG Weekly Quality Report" || it == "Missing inputs are reported as `missing`" || it == "Do not add telemetry or automatic upload")
    }
    listOf(
      "Public beta feedback",
      "Version And Device",
      "Workflow",
      "Steps To Reproduce",
      "Expected Result",
      "Actual Result",
      "Diagnostics",
      "Privacy Check",
      "Do not paste API keys",
      "provider tokens",
      "FTP passwords",
      "private source code",
      "Memory contents",
      "raw diagnostics that you have not reviewed",
    ).forEach {
      assertTrue(feedbackTemplate.contains(it))
    }
    listOf(
      "NBG Android Public Beta Privacy And Local Data Statement",
      "local-first",
      "does not upload",
      "Android Keystore-backed encrypted storage",
      "Diagnostics export is user-triggered",
      "Diagnostics export is redacted by default",
      "No cloud telemetry",
      "Any future telemetry, cloud sync, or automatic upload requires a separate privacy-reviewed plan",
      "Android MCP server is beta",
      "PetDex downloaded resources are beta",
    ).forEach {
      assertTrue(privacyStatement.contains(it))
    }
    listOf(
      "NBG Android Public Beta Quick Start",
      "Verify the APK SHA-256 checksum",
      "arm64-v8a Android device",
      "Open URL API settings",
      "Review file preview and diff before approving writes",
      "Open Terminal and run a local build",
      "Open Diagnostics Export",
      ".github/ISSUE_TEMPLATE/public_beta_feedback.md",
      "Do not paste API keys",
      "Close and reopen the app",
    ).forEach {
      assertTrue(quickStart.contains(it))
    }
  }

  @Test
  fun skillSourceIntegrityContractIsDocumentedAndWired() {
    val contract = File("../docs/contracts/skill-source-integrity-contract.md").readText()
    val index = File("../docs/contracts/README.md").readText()
    val sourceIntegrity = File("src/main/java/com/nbg/android/NbgSkillSourceIntegrity.kt").readText()
    val installSupport = File("src/main/java/com/nbg/android/HanakoSkillInstallSupport.kt").readText()
    val launcher = File("src/main/java/com/nbg/android/HanakoServerLauncher.kt").readText()
    val controller = File("src/main/java/com/nbg/android/HanakoChatController.kt").readText()
    val skillsUi = File("src/main/java/com/nbg/android/NbgAgentSkillsUi.kt").readText()
    val learnedDraftPolicy = File("src/main/java/com/nbg/android/NbgLearnedSkillDraftPolicy.kt").readText()
    val apiClient = File("src/main/java/com/nbg/android/HanakoApiClient.kt").readText()
    val diagnostics = File("src/main/java/com/nbg/android/NbgDiagnosticsExport.kt").readText()
    val skillIntegrityTest = File("src/test/java/com/nbg/android/NbgSkillSourceIntegrityTest.kt").readText()
    val learnedDraftTest = File("src/test/java/com/nbg/android/NbgLearnedSkillDraftPolicyTest.kt").readText()
    val releaseGate = File("../scripts/nbg_release_gate.sh").readText()

    listOf(
      "Skill Source Integrity Contract",
      "nbg-skill-source-provenance-v1",
      "nbg-skill-source-integrity-v1",
      "trusted_bundled",
      "user_managed",
      "unverified_external",
      "blocked",
      "Plain external `http`",
      "URL userinfo is blocked",
      "Same-name untrusted behavior",
      "explicit source review before install",
      "new unverified/user-managed Skills remain disabled",
      "requiresReview=true` must show an explicit confirmation dialog",
      "Trusted External Promotion",
      "signed_bundle",
      "pinned_sha256_allowlist",
      "Learned Skill draft review",
      "nbg-learned-skill-draft-v1",
      "completion_evidence_complete",
      "source_task",
      "target_path_reviewed",
      "draft_sha256",
      "permission_tier_recorded",
      "Low or Medium",
      "Dangerous permission tiers are never auto-installed",
      "reversible local artifact",
      "safe presence marker",
      "skills2set",
      "Diagnostics must not expose",
      "Test Oracle",
    ).forEach {
      assertTrue(contract.contains(it))
    }

    assertTrue(index.contains("skill-source-integrity-contract"))
    assertTrue(index.contains("P0 active"))
    assertTrue(index.contains("NbgSkillSourceIntegrity.kt"))

    listOf(
      "NBG_SKILL_PROVENANCE_SCHEMA",
      "NbgSkillSourceReview",
      "NbgSkillSourceKind",
      "NbgSkillTrustTier",
      "nbgReviewSkillInstallSource",
      "nbgSkillSummarySourceReview",
      "nbgWriteSkillDownloadProvenance",
      "NBG_ANDROID_BUNDLED_SKILL_ASSET_PINS",
      "NBG_SKILL_TRUSTED_EXTERNAL_PROMOTION_POLICY",
      "NBG_SKILL_TRUSTED_EXTERNAL_REQUIRED_EVIDENCE",
      "NbgBundledSkillAssetPin",
      "NbgExternalSkillPromotionReview",
      "nbgReviewBundledSkillAssets",
      "nbgReviewExternalSkillTrustedPromotion",
      "nbgVerifiedAndroidBundledSkillNames",
      "nbgMergeTrustedBundledSkillEnablement",
      "nbgEnabledSkillsAfterManualInstall",
      "nbgSkillReviewIdentity",
      "6ce4f8e231309558af0346f007447013b5122249adacc00adae0f0e23df00520",
      "scheme == \"http\" && !host.isNbgLoopbackSkillHost()",
      "!uri.userInfo.isNullOrBlank()",
      "NbgSkillTrustTier.Blocked",
    ).forEach {
      assertTrue(sourceIntegrity.contains(it))
    }

    assertTrue(installSupport.contains("val review = nbgReviewSkillInstallSource(rawUrl)"))
    assertTrue(installSupport.contains("if (!review.allowInstall) error(review.reason)"))
    assertTrue(installSupport.contains("nbgWriteSkillDownloadProvenance(review, rawUrl, target)"))
    assertTrue(launcher.contains("val verifiedDefaultSkills = seedNbgDefaultSkills(context, hanaHome)"))
    assertTrue(launcher.contains("nbgReviewBundledSkillAssets(skillName)"))
    assertTrue(launcher.contains("nbgDeletePathWithoutFollowingSymlink(File(skillsDir, skillName))"))
    assertTrue(launcher.contains(".filterNot { it in NBG_ANDROID_BUNDLED_SKILL_NAMES && it !in required }"))
    assertTrue(controller.contains("nbgVerifiedAndroidBundledSkillNames"))
    assertTrue(controller.contains("http.ensureAndroidBundledSkillsEnabled(info, verifiedBundledSkillNames)"))
    assertTrue(controller.contains("val beforeInstall = http.getSkills(info, agentId)"))
    assertTrue(controller.contains("nbgEnabledSkillsAfterManualInstall(beforeInstall.visibleSkills, afterInstall.visibleSkills)"))
    assertTrue(controller.contains("http.setAgentSkills(info, agentId, safeEnabled)"))
    assertTrue(skillsUi.contains("NbgSkillInstallDialog"))
    assertTrue(skillsUi.contains("NbgSkillSourceReviewPanel"))
    assertTrue(skillsUi.contains("reviewedSource == trimmedPath"))
    assertTrue(skillsUi.contains("NbgSkillEnableReviewDialog"))
    assertTrue(skillsUi.contains("enabled && nbgSkillSummarySourceReview(skill).requiresReview"))
    listOf(
      "NBG_LEARNED_SKILL_DRAFT_POLICY_VERSION",
      "NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE",
      "NbgLearnedSkillDraftReview",
      "nbgReviewLearnedSkillDraft",
      "completionEvidence?.review",
      "autoSafe",
      "withAutoAppliedArtifact",
      "permissionTier == NbgPermissionRiskTier.Dangerous",
    ).forEach {
      assertTrue(learnedDraftPolicy.contains(it))
    }
    assertTrue(apiClient.contains("verifiedBundledSkillNames: List<String>"))
    assertTrue(apiClient.contains("nbgMergeTrustedBundledSkillEnablement(snapshot.visibleSkills, verifiedBundledSkillNames)"))
    assertTrue(skillIntegrityTest.contains("manualInstallEnablementDropsNewReviewRequiredSkills"))
    assertTrue(skillIntegrityTest.contains("manualInstallEnablementDropsSameNameReviewRequiredReplacement"))
    assertTrue(skillIntegrityTest.contains("externalSkillPromotionRequiresSignedBundleAndPinnedSha256ButStaysUntrustedInV1"))
    assertTrue(learnedDraftTest.contains("learnedSkillDraftRequiresCompletionTargetSourceHashAndPermissionEvidence"))
    assertTrue(learnedDraftTest.contains("learnedSkillDraftBlocksMissingEvidenceAndKeepsDangerousDraftReviewOnly"))
    assertTrue(learnedDraftTest.contains("learnedSkillDraftQueueAllowsSafeAutoApplyAndKeepsHighRiskReviewOnly"))
    assertFalse(contract.contains("Whether manual Skills page installation should require an explicit review dialog before enabling"))
    assertFalse(contract.contains("Whether trusted external Skills should use signed bundles, pinned SHA-256 allowlists, or both."))

    listOf(
      "trustedBundledCount",
      "userManagedCount",
      "unverifiedExternalCount",
      "requiresReviewCount",
      "sourceIntegrityVersion",
      "expectedBundledCount",
      "pinnedBundledAssetCount",
    ).forEach {
      assertTrue(diagnostics.contains(it))
    }

    assertTrue(releaseGate.contains("app/src/main/assets"))
    assertTrue(releaseGate.contains("SKILL.md"))
    assertTrue(releaseGate.contains("assets.sha256"))
  }

  @Test
  fun permissionRiskModelContractIsDocumentedAndWired() {
    val contract = File("../docs/contracts/permission-risk-model-contract.md").readText()
    val permission = File("src/main/java/com/nbg/android/NbgPermissionRiskModel.kt").readText()
    val confirmationState = File("src/main/java/com/nbg/android/NbgAgentConfirmationState.kt").readText()
    val agentUi = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    val dialogs = File("src/main/java/com/nbg/android/NbgAgentDialogs.kt").readText()
    val bridgeTest = File("src/test/java/com/nbg/android/HanakoBridgeTest.kt").readText()
    val confirmationStateTest = File("src/test/java/com/nbg/android/NbgAgentConfirmationStateTest.kt").readText()
    val bundledRuntime = readTarTextEntry(
      File("src/main/assets/hanako-server-linux-arm64-node22.nbgpack"),
      "./bundle/index.js",
    )
    val bundledAllowList = bundledRuntime
      .substringAfter("Z${'$'} = /* @__PURE__ */ new Set([", missingDelimiterValue = "")
      .substringBefore("]), eR =", missingDelimiterValue = "")
    val bundledSideEffectSet = bundledRuntime
      .substringAfter("eR = /* @__PURE__ */ new Set([", missingDelimiterValue = "")
      .substringBefore("]), tR =", missingDelimiterValue = "")
    val bundledApprovalPolicy = bundledRuntime
      .substringAfter("function rR({ mode: e, toolName: t, params: n } = {}) {", missingDelimiterValue = "")
      .substringBefore("const sR =", missingDelimiterValue = "")

    listOf(
      "Permission Risk Model Contract",
      "| Medium | Yes | No | Terminal/shell intent without destructive pattern, unknown side-effect intent. |",
      "Medium terminal/shell/command intents require ordinary confirmation by default for public beta",
      "must never lower Android's local inference",
      "Confirmation events may carry a backend risk schema",
      "Backend risk schema is advisory",
      "Schema action/tool/command/reason fields are classified as a separate raise-only candidate",
      "weak backend hints cannot replace stronger local High/Dangerous guidance",
      "`tier`/`riskTier`/`level`/`risk`",
      "`action`",
      "`toolName`/`tool`/`name`",
      "`command`",
      "`target`/`targetLabel`/`path`/`url`/`resource`",
      "`reason`/`summary`/`description`",
      "`recoveryHint`/`recovery`/`mitigation`/`hint`",
      "requires session-level warning acceptance before first use",
      "stored `operate` preference must not auto-apply",
      "Bundled Hanako runtime owns approval generation",
      "tool_action_approval",
      "HanakoBridgeTest.mediumTerminalCommandsRequireConfirmationWithoutStrongConfirmation",
      "HanakoBridgeTest.declaredRiskCannotDowngradeDestructiveTerminalCommand",
      "HanakoBridgeTest.declaredRiskCannotDowngradeExternalResourceOrMcpSideEffects",
      "HanakoBridgeTest.payloadRiskCannotDowngradeBundledConfirmationShape",
      "HanakoBridgeTest.backendRiskSchemaCanRaiseRiskAndProvideRecoveryHint",
      "HanakoBridgeTest.backendRiskSchemaCannotDowngradeLocalInference",
      "NbgAgentConfirmationStateTest.operatePermissionWarningGatesOperatePerSessionUntilAccepted",
      "NbgAgentConfirmationStateTest.operatePermissionWarningDismissalIsTrackedPerSession",
      "AndroidManifestBehaviorTest.permissionRiskModelContractIsDocumentedAndWired",
    ).forEach {
      assertTrue(contract.contains(it))
    }
    assertFalse(contract.contains("Whether `operate` should require a session-level warning before first use"))
    assertFalse(contract.contains("Whether confirmation events should carry a backend-provided risk schema"))

    assertTrue(permission.contains("Medium(\"medium\", \"中风险\", true, false)"))
    assertTrue(permission.contains("nbgHighestPermissionRiskTier(inferred.tier, declaredOrSeverityTier, schemaInferred?.tier)"))
    assertTrue(permission.contains("maxBy { it.ordinal }"))
    assertTrue(permission.contains("nbgPermissionRiskPayloadText(payload, payloadParams)"))
    assertTrue(permission.contains("nbgPermissionRiskSchemaObject(block)"))
    assertTrue(permission.contains("nbgPermissionDeclaredRiskTier(block, riskSchema)"))
    assertTrue(permission.contains("nbgPermissionRiskForSchema(riskSchema)"))
    assertTrue(permission.contains("nbgPermissionRiskTierForWire"))
    assertTrue(permission.contains("backendHintTier != null -> backendHintTier.ordinal >= inferred.tier.ordinal"))
    assertTrue(permission.contains("inferred.tier.ordinal <= NbgPermissionRiskTier.Medium.ordinal -> true"))
    assertTrue(permission.contains("nbgPermissionRiskSchemaString(riskSchema, \"recoveryHint\", \"recovery\", \"mitigation\", \"hint\")"))
    assertTrue(permission.contains("payload?.optString(\"toolName\")"))
    assertTrue(permission.contains("params?.optString(\"command\")"))
    assertTrue(permission.contains("text.hasAny(\"shell\", \"terminal\", \"bash\", \"command\", \"exec\") -> NbgPermissionRiskTier.Medium"))
    assertTrue(permission.contains("else -> NbgPermissionRiskTier.Medium"))
    assertTrue(bridgeTest.contains("fun mediumTerminalCommandsRequireConfirmationWithoutStrongConfirmation()"))
    assertTrue(bridgeTest.contains("fun declaredRiskCannotDowngradeDestructiveTerminalCommand()"))
    assertTrue(bridgeTest.contains("fun declaredRiskCannotDowngradeExternalResourceOrMcpSideEffects()"))
    assertTrue(bridgeTest.contains("fun payloadRiskCannotDowngradeBundledConfirmationShape()"))
    assertTrue(bridgeTest.contains("fun backendRiskSchemaCanRaiseRiskAndProvideRecoveryHint()"))
    assertTrue(bridgeTest.contains("fun backendRiskSchemaCannotDowngradeLocalInference()"))
    assertTrue(bridgeTest.contains("assertTrue(shell.requiresConfirmation)"))
    assertTrue(bridgeTest.contains("assertFalse(shell.strongConfirmation)"))
    assertTrue(confirmationState.contains("var operatePermissionWarningOpen by mutableStateOf(false)"))
    assertTrue(confirmationState.contains("mutableStateListOf<String>()"))
    assertTrue(confirmationState.contains("fun shouldGateOperatePermissionMode(mode: String, sessionPath: String?)"))
    assertTrue(confirmationState.contains("fun shouldAutoPromptOperatePermissionWarning(mode: String, sessionPath: String?)"))
    assertTrue(confirmationState.contains("fun acceptOperatePermissionWarning()"))
    assertTrue(confirmationStateTest.contains("fun operatePermissionWarningGatesOperatePerSessionUntilAccepted()"))
    assertTrue(confirmationStateTest.contains("fun operatePermissionWarningDismissalIsTrackedPerSession()"))
    assertTrue(agentUi.contains("val displayPermissionMode = if (confirmationState.shouldGateOperatePermissionMode(preferredPermissionMode, hanakoState.sessionPath))"))
    assertTrue(agentUi.contains("confirmationState.shouldAutoPromptOperatePermissionWarning(preferredMode, sessionPath)"))
    assertTrue(agentUi.contains("hanako.setSessionPermissionMode(NBG_PERMISSION_MODE_ASK)"))
    assertTrue(agentUi.contains("requestPreferredPermissionMode(mode)"))
    assertTrue(agentUi.contains("NbgOperatePermissionModeDialog("))
    assertTrue(dialogs.contains("fun NbgOperatePermissionModeDialog("))
    assertTrue(dialogs.contains("未确认时会保持先问模式"))
    assertTrue(bundledAllowList.isNotBlank())
    assertTrue(bundledSideEffectSet.isNotBlank())
    assertTrue(bundledApprovalPolicy.isNotBlank())
    listOf(
      "\"terminal_list\"",
      "\"terminal_read\"",
      "\"terminal_wait\"",
    ).forEach {
      assertTrue(bundledAllowList.contains(it))
      assertFalse(bundledSideEffectSet.contains(it))
    }
    listOf(
      "\"terminal_create\"",
      "\"terminal_write\"",
      "\"terminal_interrupt\"",
      "\"terminal_kill\"",
      "\"bash\"",
    ).forEach {
      assertTrue(bundledSideEffectSet.contains(it))
      assertFalse(bundledAllowList.contains(it))
    }
    listOf(
      "kind: \"tool_action_approval\"",
      "type: \"session_confirmation\"",
      "payload: { toolName: t, params: n }",
      "t.getPermissionMode?.(o) || t.getPermissionMode?.() || \"ask\"",
      "rR({ mode: a, toolName: n.name, params: s })",
      "return c.allowed ? n.execute(...r)",
    ).forEach {
      assertTrue(bundledRuntime.contains(it))
    }
    assertTrue(bundledApprovalPolicy.contains("Z${'$'}.has(s) ? { action: \"allow\" }"))
    assertTrue(bundledApprovalPolicy.contains("r === Re.READ_ONLY ? LS(s)"))
    assertTrue(bundledApprovalPolicy.contains("Md(s)"))
  }

  @Test
  fun memoryContextContractIsDocumentedAndWired() {
    val contract = File("../docs/contracts/memory-context-contract.md").readText()
    val index = File("../docs/contracts/README.md").readText()
    val models = File("src/main/java/com/nbg/android/HanakoMemoryModels.kt").readText()
    val policy = File("src/main/java/com/nbg/android/NbgMemoryContextPolicy.kt").readText()
    val controller = File("src/main/java/com/nbg/android/HanakoChatController.kt").readText()
    val launcher = File("src/main/java/com/nbg/android/HanakoServerLauncher.kt").readText()
    val permission = File("src/main/java/com/nbg/android/NbgPermissionRiskModel.kt").readText()
    val diagnostics = File("src/main/java/com/nbg/android/NbgDiagnosticsExport.kt").readText()
    val registry = File("src/main/java/com/nbg/android/NbgCapabilityRegistry.kt").readText()
    val memoryDiagnostics = diagnostics
      .substringAfter("private fun HanakoMemoryState.toDiagnosticsJson")
      .substringBefore("private fun NbgFileShareServerState")

    listOf(
      "Memory Context Contract",
      "nbg-memory-context-v1",
      "project_fact",
      "user_preference",
      "decision",
      "handoff",
      "bug_note",
      "blocked_sensitive",
      "blocked_too_large",
      "blocked_empty",
      "Diagnostics must not include",
      "memory:context",
      "android-memory-context-policy-v1",
      "Runtime policy",
      "4000-character",
      "Local audit events without raw Memory content",
      "Diagnostics may include Memory audit metadata only",
      "NBG_MEMORY_EXPORT_POLICY_VERSION",
      "explicit_user_trigger",
      "per_item_selection",
      "sensitive_scan_passed",
      "Public-beta v1 allows a redacted Memory export only when all three evidence labels are present",
      "nbgBuildRedactedMemoryExport()",
      "Memory export uses a separate explicit user-triggered path",
      "NBG_MEMORY_UPSTREAM_POLICY_ADOPTION_VERSION",
      "upstream_policy_version_match",
      "android_patch_marker_match",
      "parity_tests_passed",
      "PatchGateRetained",
      "None for upstream Memory policy adoption in v1",
    ).forEach {
      assertTrue(contract.contains(it))
    }
    assertFalse(contract.contains("Whether Memory writes should create a local audit event without storing raw content."))

    assertTrue(index.contains("memory-context-contract"))
    assertTrue(index.contains("P0 active"))
    assertTrue(index.contains("NbgMemoryContextPolicy.kt"))

    listOf(
      "HanakoMemoryAuditEvent",
      "NBG_MEMORY_AUDIT_MAX_EVENTS",
      "NBG_MEMORY_AUDIT_DIAGNOSTIC_TAIL_LIMIT",
      "nbgMemoryAuditEvent",
      "withMemoryAuditEvent",
      "memory_error_present",
    ).forEach {
      assertTrue(models.contains(it))
    }

    listOf(
      "NBG_MEMORY_CONTEXT_POLICY_VERSION",
      "NBG_MEMORY_EXPORT_POLICY_VERSION",
      "NBG_MEMORY_EXPORT_REQUIRED_EVIDENCE",
      "NBG_MEMORY_UPSTREAM_POLICY_ADOPTION_VERSION",
      "NBG_MEMORY_UPSTREAM_POLICY_REQUIRED_EVIDENCE",
      "NBG_MEMORY_MAX_CONTENT_CHARS = 4_000",
      "NbgMemoryContextReview",
      "NbgMemoryExportPolicyReview",
      "NbgMemoryRedactedExportItem",
      "NbgMemoryRedactedExport",
      "NbgMemoryUpstreamPolicyAdoptionReview",
      "NbgMemoryContextRisk",
      "nbgReviewMemoryExportRequest",
      "nbgBuildRedactedMemoryExport",
      "toJsonString",
      "nbgReviewMemoryUpstreamPolicyAdoption",
      "nbgReviewMemoryInput",
      "nbgReviewMemoryItem",
      "nbgMemorySensitiveFindings",
      "PRIVATE KEY",
      "bearer",
      "api[_-]?key",
    ).forEach {
      assertTrue(policy.contains(it))
    }
    val memoryUi = File("src/main/java/com/nbg/android/NbgAgentMemoryUi.kt").readText()
    assertTrue(memoryUi.contains("nbgBuildRedactedMemoryExport"))
    assertTrue(memoryUi.contains("NbgMemoryExportPreviewCard"))
    assertTrue(memoryUi.contains("nbgMemoryMetadataLine"))
    val memoryPolicyTest = File("src/test/java/com/nbg/android/NbgMemoryContextPolicyTest.kt").readText()
    assertTrue(memoryPolicyTest.contains("memoryExportPolicyRequiresUserTriggerSelectionAndScan"))
    assertTrue(memoryPolicyTest.contains("redactedMemoryExportRequiresSelectionAndBlocksSensitiveItems"))
    assertTrue(memoryPolicyTest.contains("memoryUpstreamPolicyAdoptionKeepsAndroidPatchGateInV1"))

    assertTrue(controller.contains("val review = nbgReviewMemoryInput(input)"))
    assertTrue(controller.contains("val auditAction = if (input.id.trim().isBlank()) \"create\" else \"update\""))
    assertTrue(controller.contains("if (!review.allowSave)"))
    assertTrue(controller.contains("result = \"blocked\""))
    assertTrue(controller.contains("type = review.normalizedType"))
    assertTrue(controller.contains("auditAction = auditAction"))
    assertTrue(controller.contains("auditAction = \"delete\""))
    assertTrue(controller.contains("var memoryMutationCompleted = false"))
    assertTrue(controller.contains("memoryMutationCompleted = true"))
    assertTrue(controller.contains("result = \"success\""))
    assertTrue(controller.contains("result = if (memoryMutationCompleted) \"success\" else \"failure\""))
    assertTrue(controller.contains("withMemoryAuditEvent(auditEvent)"))

    assertTrue(launcher.contains("android-memory-context-policy-v1"))
    assertTrue(launcher.contains("plugins/memory/lib/memory-store.js"))
    assertTrue(launcher.contains("NBG_MEMORY_CONTEXT_POLICY_VERSION"))
    assertTrue(launcher.contains("assertMemoryPolicyForSave"))
    assertTrue(launcher.contains(".filter(memoryPolicyAllowsContext)"))

    assertTrue(permission.contains("memory_save"))
    assertTrue(permission.contains("memory_update"))
    assertTrue(permission.contains("memory_delete"))

    listOf(
      "contextPolicyVersion",
      "injectableLoadedCount",
      "blockedSensitiveLoadedCount",
      "blockedTooLargeLoadedCount",
      "auditEventCount",
      "recentAuditEvents",
      "nbgDiagnosticMemoryError",
      "toDiagnosticsJson",
      "timestampMillis",
      "state.toDiagnosticsLastError()",
      "capability.toDiagnosticsLastError()",
      "if (id == \"memory\") nbgDiagnosticMemoryError(lastError)",
    ).forEach {
      assertTrue(diagnostics.contains(it))
    }
    listOf(
      "val hanakoLastError",
      "nbgDiagnosticMemoryError(backendError)",
      "lastError = hanakoLastError",
      "lastError = nbgDiagnosticMemoryError(error.orEmpty())",
    ).forEach {
      assertTrue(registry.contains(it))
    }
    assertFalse(memoryDiagnostics.contains(".put(\"content\""))
    assertFalse(memoryDiagnostics.contains(".put(\"title\""))
    assertFalse(memoryDiagnostics.contains(".put(\"tags\""))
    assertFalse(memoryDiagnostics.contains(".put(\"id\""))
    assertFalse(memoryDiagnostics.contains(".put(\"sourceSession\""))
    assertFalse(memoryDiagnostics.contains(".put(\"sourceTurnId\""))
    assertFalse(memoryDiagnostics.contains("nbgDiagnosticText(error.orEmpty())"))
  }

  @Test
  fun androidHanakoEventContractIsDocumentedAndWired() {
    val contract = File("../docs/contracts/android-hanako-event-contract.md").readText()
    val index = File("../docs/contracts/README.md").readText()
    val eventContract = File("src/main/java/com/nbg/android/NbgHanakoEventContract.kt").readText()
    val bridge = File("src/main/java/com/nbg/android/HanakoBridge.kt").readText()
    val controller = File("src/main/java/com/nbg/android/HanakoChatController.kt").readText()
    val agentUi = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    val realtime = File("src/main/java/com/nbg/android/HanakoRealtimeToolEvents.kt").readText()
    val tools = File("src/main/java/com/nbg/android/HanakoToolParsing.kt").readText()

    listOf(
      "Android Hanako Event Contract",
      "nbg-android-hanako-events-v1",
      "nbg-android-hanako-event-schema-v1",
      "nbg-confirmation-resolution-audit-v1",
      "Machine-readable event schema manifest",
      "Local confirmation resolution audit entry",
      "Stream Resume",
      "Confirmation",
      "Tool Events",
      "Turn Termination",
      "session_confirmation",
      "resume_stream",
      "Diagnostics must not include",
      "Test Oracle",
    ).forEach {
      assertTrue(contract.contains(it))
    }

    assertTrue(index.contains("android-hanako-event-contract"))
    assertTrue(index.contains("P0 active"))
    assertTrue(index.contains("NbgHanakoEventContract.kt"))

    listOf(
      "NBG_HANAKO_EVENT_CONTRACT_VERSION",
      "NBG_HANAKO_EVENT_SCHEMA_VERSION",
      "NBG_CONFIRMATION_RESOLUTION_AUDIT_SCHEMA_VERSION",
      "NBG_HANAKO_EVENT_SCHEMA_ENTRIES",
      "NbgHanakoEventSchemaEntry",
      "NbgConfirmationResolutionAuditEntry",
      "nbgHanakoEventSchemaJson",
      "nbgHanakoEventJsonSchema",
      "nbgValidateHanakoEventSchemaManifest",
      "nbgBuildConfirmationResolutionAuditEntry",
      "nbgVerifyConfirmationResolutionAuditEntry",
      "NBG_HANAKO_SUPPORTED_EVENT_TYPES",
      "NBG_HANAKO_TERMINAL_TURN_END_EVENT_TYPES",
      "NbgHanakoEventKind",
      "nbgHanakoEventKind",
      "nbgHanakoEventTerminatesTurn",
      "NbgHanakoStreamCursor",
      "NbgHanakoStreamEventDecision",
      "nbgDecideHanakoStreamEvent",
      "duplicate_seq",
      "wrong_session",
      "stream_resume",
      "file_write_prepare",
      "confirmation_resolved",
      "team_agent_update",
    ).forEach {
      assertTrue(eventContract.contains(it))
    }

    assertTrue(contract.contains("Every type in `NBG_HANAKO_SUPPORTED_EVENT_TYPES` must have exactly one"))
    assertTrue(contract.contains("Schema entries list accepted field names and sensitive fields"))
    assertTrue(contract.contains("\"\$schema\": \"https://json-schema.org/draft/2020-12/schema\""))
    assertTrue(contract.contains("nbgHanakoEventJsonSchema()"))
    assertTrue(contract.contains("Audit metadata stores hashes of `confirmId`, subject label, and target fields"))
    assertTrue(contract.contains("Diagnostics do not export confirmation audit entries in v1"))
    assertFalse(contract.contains("Whether Hanako should publish a machine-readable JSON schema for every event type."))
    assertFalse(contract.contains("Whether confirmation resolution should carry a signed local audit entry for public beta support."))
    assertTrue(contract.contains("Stream resume counters without raw event data"))
    assertTrue(contract.contains("Raw stream resume event payloads, stream ids, or session paths"))
    assertFalse(contract.contains("Whether Android should expose stream resume counters in diagnostics without recording raw event data."))
    assertTrue(bridge.contains("parseHanakoConfirmationBlock"))
    assertTrue(bridge.contains("parseHanakoRemoteToolMessage"))
    assertTrue(bridge.contains("auditEntry: NbgConfirmationResolutionAuditEntry? = null"))
    assertTrue(agentUi.contains("val auditEntry = event.auditEntry?.takeIf(::nbgVerifyConfirmationResolutionAuditEntry)"))
    assertTrue(agentUi.contains("auditEntry = auditEntry"))
    assertTrue(bridge.contains("parseHanakoTeamTaskStatus"))
    assertTrue(controller.contains("\"stream_resume\" ->"))
    assertTrue(controller.contains("requestStreamResume"))
    assertTrue(controller.contains("shouldAcceptStreamEvent"))
    assertTrue(controller.contains("recordStreamResumeDiagnostics("))
    assertTrue(controller.contains("requestDelta = 1"))
    assertTrue(controller.contains("resumeDelta = 1"))
    assertTrue(controller.contains("replayedEventDelta = replayedEventCount"))
    assertTrue(controller.contains("if (!isCurrentSessionMessage(event))"))
    assertTrue(controller.contains("duplicateReplayEventDelta = duplicateReplayEventCount"))
    assertTrue(controller.contains("handleStreamingStatus"))
    assertTrue(controller.contains("onEvent(HanakoChatEvent.TurnEnded)"))
    assertTrue(realtime.contains("parseToolStart"))
    assertTrue(realtime.contains("parseToolEnd"))
    assertTrue(tools.contains("parseHanakoToolArray"))
    assertTrue(tools.contains("parseHanakoHistoryTool"))
  }

  @Test
  fun androidKeepsAgentMemoryAndCodeGraphAsDefaultBackgroundTooling() {
    val agentUi = readAgentUiSource()
    val skill = File("src/main/assets/nbg-default-skills/nbg-engineering-core/SKILL.md").readText()
    val pack = File("src/main/assets/hanako-server-linux-arm64-node22.nbgpack")
    val bundleProcess = ProcessBuilder("tar", "xzf", pack.absolutePath, "-O", "./bundle/index.js")
      .directory(File("."))
      .redirectErrorStream(true)
      .start()
    val bundledServer = bundleProcess.inputStream.bufferedReader().readText()
    assertEquals(0, bundleProcess.waitFor())

    assertTrue(agentUi.contains("NbgShellPage.Agents"))
    assertTrue(agentUi.contains("NbgAgentsScreen("))
    assertTrue(agentUi.contains("title = \"Agents\""))
    assertTrue(agentUi.contains("NbgShellPage.Memory"))
    assertTrue(agentUi.contains("NbgMemoryScreen("))
    assertTrue(agentUi.contains("后端工具触发 · Agent / WolfPack"))
    assertTrue(agentUi.contains("普通消息保持原文发送。复杂任务由 HanakoPro 后端按需触发 Agent 或 WolfPack"))
    assertTrue(agentUi.contains("Switch(checked = enabled, onCheckedChange = onEnabledChange)"))
    assertTrue(agentUi.contains("multiAgentEnabled = chatPreferences.multiAgentEnabled"))
    assertTrue(agentUi.contains("onSetMultiAgentEnabled = ::saveMultiAgentEnabled"))
    assertTrue(agentUi.contains("teamTask = hanakoState.teamTask"))
    assertTrue(agentUi.contains("NbgAgentsTeamTaskCard"))
    assertTrue(agentUi.contains("团队任务状态"))
    assertTrue(agentUi.contains("Agent 或 WolfPack 后端工具触发后，会在这里显示子 Agent 进度、取消入口和最终汇总。"))
    assertTrue(agentUi.contains("onAbortTeamTask = { hanako.abortTeamTask() }"))
    assertTrue(agentUi.contains("onAbortTeamAgent = { taskId, agentId -> hanako.abortTeamAgent(taskId, agentId) }"))
    assertTrue(agentUi.contains("NbgAgentsTeamAgentRow"))
    assertTrue(agentUi.contains("NbgAgentsTeamResultPanel"))
    assertTrue(agentUi.contains("汇总结果 ·"))
    assertTrue(agentUi.contains("NbgAgentsTeamPolicyCard"))
    assertTrue(agentUi.contains("NbgTeamDelegationTemplate.entries.forEach"))
    assertTrue(agentUi.contains("后台团队任务只允许 bounded 模板"))
    assertTrue(agentUi.contains("可取消 · 必须汇总"))
    assertFalse(agentUi.contains("nbgBuildMultiAgentPrompt"))
    assertTrue(bundledServer.contains("name: \"Agent\""))
    assertTrue(bundledServer.contains("name: \"WolfPack\""))
    assertTrue(bundledServer.contains("agent-trigger"))
    assertFalse(agentUi.contains("title = \"Agents\",\n      subtitle = \"单会话多角色编排模板和角色状态\""))
    assertFalse(agentUi.contains("title = \"Memory\",\n          subtitle = \"本地记忆、项目事实、偏好和技术决策\""))
    assertFalse(agentUi.contains("subtitle = \"Agents、MCP、Skills、Memory\""))
    assertFalse(agentUi.contains("text = \"用于管理 Agents、Memory、MCP、Skills、终端和外观。\""))
    listOf("coder", "explore", "plan", "verify", "reviewer", "oracle", "writer").forEach {
      assertTrue(agentUi.contains(it))
    }
    listOf(
      "codegraph_index",
      "codegraph_explore",
      "codegraph_search",
      "codegraph_symbols",
      "codegraph_node",
      "codegraph_impact",
      "codegraph_callers",
      "codegraph_callees",
    ).forEach {
      assertTrue(skill.contains(it))
    }
    assertFalse(agentUi.contains("NbgShellPage.CodeGraph"))
    assertFalse(agentUi.contains("NbgCodeGraphScreen("))
    assertFalse(agentUi.contains("title = \"CodeGraph\""))
    assertFalse(agentUi.contains("onOpenCodeGraph"))
    assertFalse(agentUi.contains("Agents team 稍后接入"))
  }

  @Test
  fun chatScreenExposesLightweightPixelPetOverlay() {
    val agentUi = readAgentUiSource()

    assertTrue(agentUi.contains("NbgPixelPetOverlay("))
    assertTrue(agentUi.contains("internal enum class NbgPixelPetMood"))
    assertTrue(agentUi.contains("detectDragGestures"))
    assertTrue(agentUi.contains("detectTapGestures"))
    assertTrue(agentUi.contains("onSizeChanged"))
    assertTrue(agentUi.contains("containerWidthPx"))
    assertTrue(agentUi.contains("petWidthPx"))
    assertTrue(agentUi.contains("clampDragX"))
    assertTrue(agentUi.contains("internal enum class NbgPixelPetActivity"))
    assertTrue(agentUi.contains("NbgPixelPetActivity.StreamFollow"))
    assertTrue(agentUi.contains("NbgPixelPetActivity.CompressContext"))
    assertTrue(agentUi.contains("NbgPixelPetActivity.ToolTerminal"))
    assertTrue(agentUi.contains("NbgPixelPetActivity.ToolFile"))
    assertTrue(agentUi.contains("NbgPixelPetActivity.ToolSearch"))
    assertTrue(agentUi.contains("NbgPixelPetActivity.ToolInspect"))
    assertTrue(agentUi.contains("NbgPixelPetActivity.Prewarm"))
    assertTrue(agentUi.contains("NbgPixelPetActivity.LocalHistory"))
    assertTrue(agentUi.contains("NbgPixelPetActivity.ComposerFocus"))
    assertTrue(agentUi.contains("NbgPixelPetStage("))
    assertFalse(agentUi.contains("NbgPixelPetStatusHud("))
    assertTrue(agentUi.contains("haloAlpha"))
    assertTrue(agentUi.contains("focusPulse"))
    assertTrue(agentUi.contains("rightDefaultX()"))
    assertTrue(agentUi.contains("dragX = rightDefaultX()"))
    assertTrue(agentUi.contains("fun minDragX(): Float = -startInsetPx"))
    assertTrue(agentUi.contains("val menuOpenToLeft = containerWidthPx > 0 && with(density)"))
    assertTrue(agentUi.contains("startInsetPx + dragX + menuRightOffset.toPx() + menuWidth.toPx() > containerWidthPx - startInsetPx"))
    assertTrue(agentUi.contains(".offset(x = if (menuOpenToLeft) menuLeftOffset else menuRightOffset, y = 2.dp)"))
    assertTrue(agentUi.contains("hasVisibleCards = false"))
    assertFalse(agentUi.contains("NbgPixelPetCardAnchor"))
    assertFalse(agentUi.contains("petCardAnchorMap"))
    assertTrue(agentUi.contains("private fun Modifier.nbgPetCardAnchor(@Suppress(\"UNUSED_PARAMETER\") onBounds: (Rect) -> Unit): Modifier = this"))
    assertTrue(agentUi.contains("var composerBoundsInRoot by remember"))
    assertTrue(agentUi.contains("onComposerBoundsChanged = { bounds ->"))
    assertTrue(agentUi.contains("abs(previous.top - bounds.top) > 1f"))
    assertTrue(agentUi.contains("composerActive = composerFocused || draft.isNotBlank()"))
    assertTrue(agentUi.contains("bottom = innerPadding.calculateBottomPadding()"))
    assertFalse(agentUi.contains("bottom = innerPadding.calculateBottomPadding() + 216.dp"))
    assertTrue(agentUi.contains("var composerFocused by remember"))
    assertTrue(agentUi.contains("onFocusedChange = { composerFocused = it }"))
    assertTrue(agentUi.contains("onComposerBoundsChanged: (Rect) -> Unit = {}"))
    assertTrue(agentUi.contains("onComposerBoundsChanged(coordinates.boundsInRoot())"))
    assertTrue(agentUi.contains(".onFocusChanged { onFocusedChange(it.isFocused) }"))
    assertFalse(agentUi.contains("nbgPixelPetAnchorType(item)"))
    assertFalse(agentUi.contains("composerCornerTargetFor"))
    assertFalse(agentUi.contains("nextBehaviorPlan"))
    assertTrue(agentUi.contains("fun bottomInsetPx(): Float"))
    assertTrue(agentUi.contains("val composerTop = composerBoundsInRoot?.top ?: return fallbackBottomInsetPx"))
    assertTrue(agentUi.contains("val composerBoundsKey = composerBoundsInRoot?.let"))
    assertTrue(agentUi.contains("LaunchedEffect(containerWidthPx, containerHeightPx, petWidthPx, petHeightPx, composerBoundsKey)"))
    assertFalse(agentUi.contains("animate(\n          initialValue = 0f"))
    assertFalse(agentUi.contains("NbgPixelPetActionEffects("))
    assertFalse(agentUi.contains("private fun NbgPixelPetActionEffects("))
    assertFalse(agentUi.contains("drawStreamingEffect("))
    assertFalse(agentUi.contains("nbgPixelPetEffectFrameDelay"))
    assertFalse(agentUi.contains("nbgPixelPetShouldAnimateEffect"))
    assertFalse(agentUi.contains("rememberInfiniteTransition(label = \"pixel pet action effects\")"))
    assertFalse(agentUi.contains("rememberInfiniteTransition(label = \"pixel pet ambient\")"))
    assertFalse(agentUi.contains("val midLeft = clampDragX(left + petWidthPx * 0.46f)"))
    assertFalse(agentUi.contains("fun targetFromAnchor"))
    assertTrue(agentUi.contains("NBG_PIXEL_PET_IDLE_PULSE_MS"))
    assertTrue(agentUi.contains("NBG_PIXEL_PET_ACTIVE_PULSE_MS"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.ToolTerminal"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.ToolFile"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.ToolSearch"))
    assertFalse(agentUi.contains("NBG_PIXEL_PET_MANUAL_HOLD_MS"))
    assertFalse(agentUi.contains("manualControlUntilMs"))
    assertTrue(agentUi.contains("NbgPixelPetActionMenu("))
    assertTrue(agentUi.contains("NbgPixelPetMenuRow(text = \"进入桌宠页面\""))
    assertTrue(agentUi.contains("NbgPixelPetMenuRow(text = \"隐藏桌宠\""))
    assertTrue(agentUi.contains("detectTapGestures { menuOpen = false }"))
    assertTrue(agentUi.contains("detectTapGestures {\n            menuOpen = !menuOpen"))
    assertTrue(agentUi.contains("if (!petState.hidden)"))
    assertTrue(agentUi.contains("pet = petState.currentPet"))
    assertTrue(agentUi.contains("onOpenPets = onOpenPets"))
    assertTrue(agentUi.contains("onHidePet = onHidePet"))
    assertTrue(agentUi.contains("fun maxDragY(): Float = 0f"))
    assertTrue(agentUi.contains("walking = false"))
    assertTrue(agentUi.contains("runStatus.label.contains(\"调用工具\") || runStatus.label.contains(\"工具\")"))
    assertFalse(agentUi.contains("coerceIn(-24f, 720f)"))
    assertFalse(agentUi.contains("coerceIn(-1_200f, 120f)"))
    assertTrue(agentUi.contains("contentDescription = \"像素桌宠\""))
    assertTrue(agentUi.contains("nbgPixelPetMood(runStatus)"))
    assertTrue(agentUi.contains("NbgPixelPetActivity.Think -> \"思考\""))
    assertTrue(agentUi.contains("NbgPixelPetActivity.CompressContext -> \"压缩\""))
    assertTrue(agentUi.contains("NbgPixelPetActivity.ToolTerminal -> \"终端\""))
    assertTrue(agentUi.contains("NbgPixelPetActivity.ToolFile -> \"文件\""))
    assertTrue(agentUi.contains("NbgPixelPetActivity.ToolSearch -> \"搜索\""))
    assertTrue(agentUi.contains("NbgPixelPetActivity.Prewarm -> \"预热\""))
    assertTrue(agentUi.contains("NbgPixelPetActivity.LocalHistory -> \"历史\""))
    assertTrue(agentUi.contains("NbgPixelPetActivity.ReadCard -> \"阅读\""))
    assertTrue(agentUi.contains("NbgPixelPetActivity.Rest -> \"休息\""))
    assertTrue(File("src/main/res/drawable-nodpi/petdex_002_spritesheet.webp").isFile)
    assertTrue(agentUi.contains("R.drawable.petdex_002_spritesheet"))
    assertTrue(agentUi.contains("PETDEX_002_FRAME_WIDTH_PX = 192"))
    assertTrue(agentUi.contains("PETDEX_002_FRAME_HEIGHT_PX = 208"))
    assertTrue(agentUi.contains("PETDEX_002_COLUMNS = 8"))
    assertTrue(agentUi.contains("filterQuality = FilterQuality.None"))
    assertTrue(agentUi.contains("private enum class NbgPetdex002State"))
    assertTrue(agentUi.contains("Idle(row = 0, frames = 6"))
    assertTrue(agentUi.contains("RunningRight(row = 1, frames = 8"))
    assertTrue(agentUi.contains("RunningLeft(row = 2, frames = 8"))
    assertTrue(agentUi.contains("Waving(row = 3, frames = 4"))
    assertTrue(agentUi.contains("Jumping(row = 4, frames = 5"))
    assertTrue(agentUi.contains("Failed(row = 5, frames = 6"))
    assertTrue(agentUi.contains("Waiting(row = 6, frames = 6"))
    assertTrue(agentUi.contains("Running(row = 7, frames = 6"))
    assertTrue(agentUi.contains("Review(row = 8, frames = 6"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.IdleWave -> NbgPetdex002State.Waving.animation"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.IdleCheer -> NbgPetdex002State.Jumping.animation"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.IdleLookAround -> NbgPetdex002State.Review.animation"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.Rest || mood == NbgPixelPetMood.Resting -> NbgPetdex002State.Failed.animation"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.CompressContext -> NbgPetdex002State.Review.animation"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.Think || mood == NbgPixelPetMood.Thinking -> NbgPetdex002State.Review.animation"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.Prewarm -> NbgPetdex002State.Waiting.animation"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.ConnectWait || mood == NbgPixelPetMood.Connecting -> NbgPetdex002State.Waiting.animation"))
    assertTrue(agentUi.contains("activity == NbgPixelPetActivity.Alert || mood == NbgPixelPetMood.Warning -> NbgPetdex002State.Failed.animation"))
    assertTrue(agentUi.contains("mood == NbgPixelPetMood.Idle -> NbgPetdex002State.Idle.animation"))
    assertTrue(agentUi.contains("NbgStreamingTailIndicator()"))
    assertTrue(agentUi.contains("label = \"streaming output indicator\""))
    assertTrue(agentUi.contains("contentDescription = \"AI 正在输出\""))
  }

  @Test
  fun pixelPetBehaviorMapsChatStatesToCompanionActions() {
    val thinking = NbgChatRunStatus("正在思考", active = true)
    val streaming = NbgChatRunStatus("正在输出", "deepseek-v4-pro", active = true)
    val tooling = NbgChatRunStatus("正在调用工具", "运行命令", active = true)
    val fileTooling = NbgChatRunStatus("正在调用工具", "读取文件", active = true)
    val searchTooling = NbgChatRunStatus("正在调用工具", "web_search", active = true)
    val genericTooling = NbgChatRunStatus("正在调用工具", "MCP", active = true)
    val compressing = NbgChatRunStatus("正在压缩上下文", "42%", active = true)
    val prewarming = NbgChatRunStatus("后台预热 HanakoPro", "发送前准备中", active = true)
    val localHistory = NbgChatRunStatus("本地历史", "后台预热中")
    val connecting = NbgChatRunStatus("正在连接 HanakoPro", active = true)
    val warning = NbgChatRunStatus("需要处理", "timeout", warning = true)
    val idle = NbgChatRunStatus("空闲", "HanakoPro 已连接")

    assertEquals(NbgPixelPetMood.Thinking, nbgPixelPetMood(thinking))
    assertEquals(NbgPixelPetActivity.Think, nbgPixelPetActivity(thinking, hasVisibleCards = true, step = 0))
    assertEquals("思考", nbgPixelPetBubbleText(thinking))

    assertEquals(NbgPixelPetMood.Streaming, nbgPixelPetMood(streaming))
    assertEquals(NbgPixelPetActivity.StreamFollow, nbgPixelPetActivity(streaming, hasVisibleCards = true, step = 0))
    assertEquals("输出", nbgPixelPetBubbleText(streaming))

    assertEquals(NbgPixelPetMood.Tooling, nbgPixelPetMood(tooling))
    assertEquals(NbgPixelPetActivity.ToolTerminal, nbgPixelPetActivity(tooling, hasVisibleCards = true, step = 0))
    assertEquals("终端", nbgPixelPetBubbleText(tooling))
    assertEquals(NbgPixelPetActivity.ToolFile, nbgPixelPetActivity(fileTooling, hasVisibleCards = true, step = 0))
    assertEquals("文件", nbgPixelPetBubbleText(fileTooling))
    assertEquals(NbgPixelPetActivity.ToolSearch, nbgPixelPetActivity(searchTooling, hasVisibleCards = true, step = 0))
    assertEquals("搜索", nbgPixelPetBubbleText(searchTooling))
    assertEquals(NbgPixelPetActivity.ToolInspect, nbgPixelPetActivity(genericTooling, hasVisibleCards = true, step = 0))

    assertEquals(NbgPixelPetMood.Thinking, nbgPixelPetMood(compressing))
    assertEquals(NbgPixelPetActivity.CompressContext, nbgPixelPetActivity(compressing, hasVisibleCards = true, step = 0))
    assertEquals("压缩", nbgPixelPetBubbleText(compressing))

    assertEquals(NbgPixelPetMood.Connecting, nbgPixelPetMood(prewarming))
    assertEquals(NbgPixelPetActivity.Prewarm, nbgPixelPetActivity(prewarming, hasVisibleCards = false, step = 0))
    assertEquals("预热", nbgPixelPetBubbleText(prewarming))

    assertEquals(NbgPixelPetMood.Reading, nbgPixelPetMood(localHistory))
    assertEquals(NbgPixelPetActivity.LocalHistory, nbgPixelPetActivity(localHistory, hasVisibleCards = false, step = 0))
    assertEquals("历史", nbgPixelPetBubbleText(localHistory))

    assertEquals(NbgPixelPetMood.Connecting, nbgPixelPetMood(connecting))
    assertEquals(NbgPixelPetActivity.ConnectWait, nbgPixelPetActivity(connecting, hasVisibleCards = false, step = 0))
    assertEquals("", nbgPixelPetBubbleText(connecting))

    assertEquals(NbgPixelPetMood.Warning, nbgPixelPetMood(warning))
    assertEquals(NbgPixelPetActivity.Alert, nbgPixelPetActivity(warning, hasVisibleCards = true, step = 0))
    assertEquals("需要处理", nbgPixelPetBubbleText(warning))

    assertEquals(NbgPixelPetActivity.IdlePatrol, nbgPixelPetActivity(idle, hasVisibleCards = true, step = 0))
    assertEquals(NbgPixelPetActivity.Rest, nbgPixelPetActivity(idle, hasVisibleCards = false, step = 5))
    assertEquals(NbgPixelPetActivity.IdleBlink, nbgPixelPetActivity(idle, hasVisibleCards = false, step = 3))
    assertEquals(NbgPixelPetActivity.IdleWave, nbgPixelPetActivity(idle, hasVisibleCards = false, step = 9))
    assertEquals(NbgPixelPetActivity.IdleCheer, nbgPixelPetActivity(idle, hasVisibleCards = false, step = 13))
    assertEquals(NbgPixelPetActivity.IdleLookAround, nbgPixelPetActivity(idle, hasVisibleCards = false, step = 15))
    assertEquals(NbgPixelPetActivity.ReadCard, nbgPixelPetActivity(idle, hasVisibleCards = true, step = 17))
    assertEquals(NbgPixelPetActivity.ComposerFocus, nbgPixelPetActivity(idle, hasVisibleCards = true, step = 0, composerActive = true))
    assertEquals(NbgPixelPetActivity.IdlePatrol, nbgPixelPetActivity(idle, hasVisibleCards = false, step = 0))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.ComposerFocus))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.Think))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.CompressContext))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.StreamFollow))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.ToolTerminal))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.ToolFile))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.ToolSearch))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.ToolInspect))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.Prewarm))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.LocalHistory))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.IdleBlink))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.IdleWave))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.IdleCheer))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.IdleLookAround))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.ReadCard))
    assertFalse(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.IdlePatrol, step = 0))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.IdlePatrol, step = 1))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.IdlePatrol, step = 2))
    assertTrue(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.IdlePatrol, step = 3))
    assertFalse(nbgPixelPetShouldHoldPosition(NbgPixelPetActivity.IdlePatrol, step = 4))
    assertFalse(nbgPixelPetShouldWalkForDelta(deltaX = 8f, petWidthPx = 120))
    assertFalse(nbgPixelPetShouldWalkForDelta(deltaX = 30f, petWidthPx = 120))
    assertTrue(nbgPixelPetShouldWalkForDelta(deltaX = 34f, petWidthPx = 120))
    assertTrue(nbgPixelPetShouldWalkForDelta(deltaX = -50f, petWidthPx = 120))

    assertEquals(8, nbgPetdex002Animation(NbgPixelPetMood.Thinking, NbgPixelPetActivity.Think, walking = false, readingText = false, facingLeft = false).row)
    assertEquals(6, nbgPetdex002Animation(NbgPixelPetMood.Connecting, NbgPixelPetActivity.ConnectWait, walking = false, readingText = false, facingLeft = false).row)
    assertEquals(6, nbgPetdex002Animation(NbgPixelPetMood.Idle, NbgPixelPetActivity.Prewarm, walking = false, readingText = false, facingLeft = false).row)
    assertEquals(5, nbgPetdex002Animation(NbgPixelPetMood.Warning, NbgPixelPetActivity.Alert, walking = false, readingText = false, facingLeft = false).row)
    assertEquals(7, nbgPetdex002Animation(NbgPixelPetMood.Tooling, NbgPixelPetActivity.ToolTerminal, walking = false, readingText = false, facingLeft = false).row)
    assertEquals(3, nbgPetdex002Animation(NbgPixelPetMood.Streaming, NbgPixelPetActivity.StreamFollow, walking = false, readingText = false, facingLeft = false).row)
    assertEquals(1, nbgPetdex002Animation(NbgPixelPetMood.Idle, NbgPixelPetActivity.IdlePatrol, walking = true, readingText = false, facingLeft = false).row)
    assertEquals(2, nbgPetdex002Animation(NbgPixelPetMood.Idle, NbgPixelPetActivity.IdlePatrol, walking = true, readingText = false, facingLeft = true).row)
  }

  @Test
  fun androidExposesPetdexPetManagementControls() {
    val agentUi = readAgentUiSource()

    assertTrue(agentUi.contains("NbgShellPage.Pets"))
    assertTrue(agentUi.contains("NbgPetsScreen("))
    assertTrue(agentUi.contains("NbgPetStore(context)"))
    assertTrue(agentUi.contains("NbgPetDexClient(nbgPetDexManifestProvenanceDir(context.filesDir))"))
    assertTrue(agentUi.contains("val petUiState = remember { NbgAgentPetState(petStore.loadState()) }"))
    assertTrue(agentUi.contains("refreshPetDex()"))
    assertTrue(agentUi.contains("installPet(pet: NbgPetDexManifestPet)"))
    assertTrue(agentUi.contains("if (!petUiState.beginManifestRefresh()) return"))
    assertTrue(agentUi.contains("petUiState.applyManifestRefreshSuccess(it)"))
    assertTrue(agentUi.contains("petUiState.applyManifestRefreshFailure(it)"))
    assertTrue(agentUi.contains("petUiState.finishManifestRefresh()"))
    assertTrue(agentUi.contains("if (!petUiState.beginInstall(pet)) return"))
    assertTrue(agentUi.contains("petUiState.applyInstallSuccess(petStore.loadState())"))
    assertTrue(agentUi.contains("petUiState.applyInstallFailure(it)"))
    assertTrue(agentUi.contains("petUiState.finishInstall(pet.slug)"))
    assertTrue(agentUi.contains("onSelectPet = { slug -> petUiState.applyStoreState(petStore.setCurrent(slug)) }"))
    assertTrue(agentUi.contains("onDeletePet = { slug -> petUiState.applyStoreState(petStore.delete(slug)) }"))
    assertTrue(agentUi.contains("onSetHidden = { hidden -> petUiState.applyStoreState(petStore.setHidden(hidden)) }"))
    assertTrue(agentUi.contains("onInstallPet = { pet -> installPet(pet) }"))
    assertFalse(agentUi.contains("var petState by remember"))
    assertFalse(agentUi.contains("var petDexManifest by remember"))
    assertFalse(agentUi.contains("var petDexLoading by remember"))
    assertFalse(agentUi.contains("var petDexError by remember"))
    assertFalse(agentUi.contains("var installingPetSlug by remember"))
    assertTrue(agentUi.contains("title = \"桌宠\""))
    assertTrue(agentUi.contains("actions = {"))
    assertTrue(agentUi.contains("NbgPlainIconButton(Icons.Filled.Add, \"添加桌宠\""))
    assertTrue(agentUi.contains("NbgPetDexDialog("))
    assertTrue(agentUi.contains("NbgPetSearchField(value = query"))
    assertTrue(agentUi.contains("NbgPetDexPreviewStore()"))
    assertTrue(agentUi.contains("previewStore.loadPreview(pet)"))
    assertTrue(agentUi.contains("NbgPetDexStaticPreview("))
    assertTrue(agentUi.contains("BitmapRegionDecoder.newInstance"))
    assertTrue(agentUi.contains("Rect(0, 0, NBG_PET_FRAME_WIDTH_PX, NBG_PET_FRAME_HEIGHT_PX)"))
    assertTrue(agentUi.contains("NbgPetActionLibrary(pet = state.currentPet)"))
    assertTrue(agentUi.contains("NBG_PETDEX_VISIBLE_LIMIT = 50"))
    assertTrue(agentUi.contains("nbgCountPetDexMatches(manifest?.pets.orEmpty(), query, characterOnly = true)"))
    assertTrue(agentUi.contains("显示 ${'$'}{pets.size} / 匹配 ${'$'}{matchCount}"))
    assertTrue(agentUi.contains("nbgPetKindLabel"))
    assertTrue(agentUi.contains("\"待机\" to NbgPetdex002State.Idle"))
    assertTrue(agentUi.contains("\"挥手\" to NbgPetdex002State.Waving"))
    assertTrue(agentUi.contains("\"奔跑\" to NbgPetdex002State.Running"))
    assertTrue(agentUi.contains("NBG_DEFAULT_PET_SLUG = \"petdex-002\""))
    assertTrue(agentUi.contains("NBG_PETDEX_MANIFEST_URL = \"https://petdex.dev/api/manifest\""))
    assertTrue(agentUi.contains("spritesheetUrl"))
    assertTrue(agentUi.contains("petJsonUrl"))
    assertTrue(agentUi.contains("nbgReviewPetDexInstall(manifestPet)"))
    assertTrue(agentUi.contains("downloadToFile("))
    assertTrue(agentUi.contains("NBG_PET_MAX_SPRITE_BYTES"))
    assertTrue(agentUi.contains("NBG_PET_MAX_JSON_BYTES"))
    assertTrue(agentUi.contains("NBG_PET_PROVENANCE_FILE"))
    assertTrue(agentUi.contains("nbgWritePetResourceProvenance("))
    assertTrue(agentUi.contains("nbgReviewPetDexResourceUrl(pet.spritesheetUrl, NbgPetResourceFileKind.Sprite)"))
    assertTrue(agentUi.contains("ensureBundledPetsSeeded()"))
    assertTrue(agentUi.contains("KEY_BUNDLED_VERSION"))
    assertTrue(agentUi.contains("copyAssetIfMissing(\"nbg-default-pets/${'$'}{seed.slug}/sprite.webp\", spriteFile)"))
    assertTrue(agentUi.contains("copyAssetIfMissing(\"nbg-default-pets/${'$'}{seed.slug}/pet.json\", petJsonFile)"))
    assertTrue(agentUi.contains("nbgWriteBundledPetResourceProvenance("))
    assertTrue(agentUi.contains("nbgBundledPetSeeds()"))
    listOf("rikka", "erii-2", "wangcai", "claude-crab", "ggbond", "homelander").forEach { slug ->
      assertTrue(agentUi.contains("slug = \"$slug\""))
      assertTrue(File("src/main/assets/nbg-default-pets/$slug/sprite.webp").isFile)
      assertTrue(File("src/main/assets/nbg-default-pets/$slug/pet.json").isFile)
    }
    assertTrue(agentUi.contains("nbgFilterPetDexPets(manifest?.pets.orEmpty(), query, characterOnly = true)"))
    assertTrue(agentUi.contains(".take(limit.coerceAtLeast(1))"))
    assertFalse(agentUi.contains(".take(160)"))
  }

  @Test
  fun petResourceIntegrityContractIsDocumentedAndWired() {
    val contract = File("../docs/contracts/pet-resource-integrity-contract.md").readText()
    val index = File("../docs/contracts/README.md").readText()
    val issueQueue = File("../docs/issues/2026-07-04-001-m1-p0-issue-queue.md").readText()
    val integrity = File("src/main/java/com/nbg/android/NbgPetResourceIntegrity.kt").readText()
    val store = File("src/main/java/com/nbg/android/NbgPetStore.kt").readText()
    val client = File("src/main/java/com/nbg/android/NbgPetDexClient.kt").readText()
    val preview = File("src/main/java/com/nbg/android/NbgPetDexPreviewStore.kt").readText()
    val diagnostics = File("src/main/java/com/nbg/android/NbgDiagnosticsExport.kt").readText()

    listOf(
      "Pet Resource Integrity Contract",
      "nbg-pet-resource-integrity-v1",
      "trusted_bundled",
      "unverified_petdex",
      "user_managed",
      "blocked",
      "PetDex 资源必须使用 HTTPS",
      "redactedSource",
      "Diagnostics export shape",
      "NBG_PETDEX_TRUSTED_PROMOTION_POLICY",
      "signed_manifest",
      "per_resource_sha256_manifest",
      "FuturePromotionCandidate",
      "None for v1",
      "Test Oracle",
    ).forEach {
      assertTrue(contract.contains(it))
    }

    assertTrue(index.contains("pet-resource-integrity-contract"))
    assertTrue(index.contains("P0 active"))
    assertTrue(index.contains("NbgPetResourceIntegrity.kt"))
    assertTrue(issueQueue.contains("Issue 18: Pet Resource Integrity"))

    listOf(
      "NBG_PET_RESOURCE_INTEGRITY_VERSION = \"nbg-pet-resource-integrity-v1\"",
      "NBG_PET_PROVENANCE_FILE = \".nbg-pet-provenance.json\"",
      "NBG_PETDEX_MANIFEST_PROVENANCE_FILE = \".nbg-petdex-manifest-provenance.json\"",
      "NBG_PETDEX_TRUSTED_PROMOTION_POLICY = \"nbg-petdex-trusted-promotion-v1\"",
      "NBG_PETDEX_TRUSTED_REQUIRED_EVIDENCE",
      "NBG_PET_MAX_SPRITE_BYTES = 8L * 1024L * 1024L",
      "NBG_PET_MAX_JSON_BYTES = 512L * 1024L",
      "NbgPetDexManifestProvenance",
      "NbgPetDexTrustedPromotionReview",
      "NbgPetDexInstallReview",
      "NbgPetTrustTier",
      "UserManaged",
      "nbgReviewPetDexInstall",
      "nbgReviewPetDexResourceUrl",
      "nbgReviewPetDexTrustedPromotion",
      "nbgWritePetDexManifestProvenance",
      "nbgReadPetDexManifestProvenance",
      "nbgPetDexManifestProvenanceDir",
      "nbgWritePetResourceProvenance",
      "nbgPetSummarySourceReview",
      "hasNbgTrustedBundledPetProvenance",
    ).forEach {
      assertTrue(integrity.contains(it))
    }

    assertTrue(store.contains("nbgReviewPetDexInstall(manifestPet)"))
    assertTrue(store.contains("commitStagedPetDir(stagingDir, dir, slug)"))
    assertTrue(store.contains("response.request.url.toString()"))
    assertTrue(store.contains("NbgPetResourceFileKind.Sprite"))
    assertTrue(store.contains("NbgPetResourceFileKind.PetJson"))
    assertTrue(store.contains("nbgWritePetResourceProvenance("))
    assertTrue(store.contains("nbgWriteBundledPetResourceProvenance("))
    assertTrue(store.contains("builtIn = root.optBoolean(\"builtIn\", false)"))
    assertTrue(store.contains(".put(\"builtIn\", builtIn)"))
    assertTrue(store.contains("source = root.optString(\"source\").trim()"))
    assertFalse(store.contains("source = root.optString(\"source\").trim().ifBlank { \"petdex\" }"))
    assertTrue(client.contains("NBG_PETDEX_MANIFEST_MAX_BYTES"))
    assertTrue(client.contains("NbgPetResourceFileKind.Manifest"))
    assertTrue(client.contains("nbgWritePetDexManifestProvenance("))
    assertTrue(preview.contains("NBG_PET_MAX_SPRITE_BYTES"))
    assertTrue(preview.contains("return@runCatching null"))
    assertTrue(diagnostics.contains("resourcePolicyVersion"))
    assertTrue(diagnostics.contains("trustedBundledCount"))
    assertTrue(diagnostics.contains("unverifiedPetDexCount"))
    assertTrue(diagnostics.contains("userManagedCount"))
    assertTrue(diagnostics.contains("nbgPetSummarySourceReview(currentPet).sourceKind.wireName"))
    assertTrue(diagnostics.contains("isNbgPetDexSource()"))
    assertTrue(diagnostics.contains("legacyPetDexWithoutProvenanceCount"))
    assertTrue(diagnostics.contains("nbgReadPetDexManifestProvenance(nbgPetDexManifestProvenanceDir(context.filesDir))"))
    assertTrue(diagnostics.contains("manifestProvenancePresent"))
    assertTrue(diagnostics.contains("manifestSha256Prefix"))
    assertTrue(File("src/test/java/com/nbg/android/NbgPetResourceIntegrityTest.kt").readText().contains("petDexTrustedPromotionRequiresSignedManifestAndResourceHashesButStaysUntrustedInV1"))
  }

  @Test
  fun petdexManifestParserSupportsSearchAndCharacterFiltering() {
    val manifest = nbgParsePetDexManifest(
      """
        {
          "generatedAt": "2026-06-28T00:00:00.000Z",
          "total": 3,
          "pets": [
            {
              "slug": "boba",
              "displayName": "Boba",
              "kind": "character",
              "submittedBy": "PetDex",
              "spritesheetUrl": "https://assets.petdex.dev/pets/boba/sprite.webp",
              "petJsonUrl": "https://assets.petdex.dev/pets/boba/petjson.json",
              "zipUrl": "https://assets.petdex.dev/pets/boba/zip.zip"
            },
            {
              "slug": "desk-lamp",
              "displayName": "Desk Lamp",
              "kind": "object",
              "submittedBy": "PetDex",
              "spritesheetUrl": "https://assets.petdex.dev/pets/lamp/sprite.webp",
              "petJsonUrl": "https://assets.petdex.dev/pets/lamp/petjson.json"
            },
            {
              "slug": "tiny-cat",
              "displayName": "Tiny Cat",
              "kind": "creature",
              "submittedBy": "NBG",
              "spritesheetUrl": "https://assets.petdex.dev/pets/cat/sprite.webp",
              "petJsonUrl": "https://assets.petdex.dev/pets/cat/petjson.json"
            }
          ]
        }
      """.trimIndent(),
    )

    assertEquals(3, manifest.total)
    assertEquals(3, manifest.pets.size)
    assertEquals(listOf("boba", "tiny-cat"), nbgFilterPetDexPets(manifest.pets, "").map { it.slug })
    assertEquals(listOf("tiny-cat"), nbgFilterPetDexPets(manifest.pets, "nbg").map { it.slug })
    assertEquals(listOf("desk-lamp"), nbgFilterPetDexPets(manifest.pets, "lamp", characterOnly = false).map { it.slug })
    assertEquals(listOf("boba"), nbgFilterPetDexPets(manifest.pets, "", limit = 1).map { it.slug })
    assertEquals(2, nbgCountPetDexMatches(manifest.pets, ""))
  }

  @Test
  fun androidExposesHanakoSkillsControls() {
    val agentUi = readAgentUiSource()
    val bridge = readHanakoBridgeSource()

    assertTrue(agentUi.contains("NbgShellPage.Skills"))
    assertTrue(agentUi.contains("title = \"Skills\""))
    assertTrue(agentUi.contains("NbgSkillsScreen("))
    assertTrue(agentUi.contains("hanako.loadSkills()"))
    assertTrue(agentUi.contains("hanako.reloadSkills()"))
    assertTrue(agentUi.contains("onReloadRuntime"))
    assertTrue(agentUi.contains("label = \"重载\""))
    assertTrue(agentUi.contains("hanako.installSkill(it)"))
    assertTrue(agentUi.contains("hanako.setSkillEnabled(skillName, enabled)"))
    assertTrue(agentUi.contains("hanako.deleteSkill(it)"))
    assertTrue(agentUi.contains("hanako.createSkillBundle(name, skillNames)"))
    assertTrue(agentUi.contains("hanako.updateSkillBundle(bundleId, name, skillNames)"))
    assertTrue(agentUi.contains("hanako.deleteSkillBundle(it)"))
    assertTrue(agentUi.contains("hanako.setExternalSkillPaths(it)"))
    assertTrue(agentUi.contains("添加 Skill"))
    assertTrue(agentUi.contains("Skill Bundles"))
    assertTrue(agentUi.contains("外部 Skill 路径"))
    assertTrue(agentUi.contains("Skill Curator"))
    assertTrue(agentUi.contains("NbgSkillCuratorCard"))
    assertTrue(agentUi.contains("rawSnapshot = hanakoState.rawSkillsSnapshot"))
    assertTrue(agentUi.contains("skillCuratorMetadata = hanakoState.skillCuratorMetadata"))
    assertTrue(agentUi.contains("nbgBuildSkillCuratorSummary(rawSnapshot, skillCuratorMetadata)"))
    assertTrue(agentUi.contains("NbgArchivedSkillRow"))
    assertTrue(agentUi.contains("onArchiveSkill = { hanako.archiveSkill(it) }"))
    assertTrue(agentUi.contains("onRestoreSkill = { hanako.restoreArchivedSkill(it) }"))
    assertTrue(agentUi.contains("onRunCuratorReview = { hanako.runSkillCuratorReview() }"))
    assertTrue(agentUi.contains("onSetCuratorLoopEnabled = { hanako.setSkillCuratorLoopEnabled(it) }"))
    assertTrue(agentUi.contains("onRunCuratorLoopNow = { hanako.runSkillCuratorLoopNow() }"))
    assertTrue(agentUi.contains("label = \"运行复核\""))
    assertTrue(agentUi.contains("NbgSkillDiffMergeCard"))
    assertTrue(agentUi.contains("onApplySkillDiffMerge"))
    assertTrue(bridge.contains("fun runSkillCuratorReview()"))
    assertTrue(bridge.contains("fun runSkillCuratorLoopNow()"))
    assertTrue(bridge.contains("NbgSkillDiffPreview"))
    assertTrue(bridge.contains("nbgRunSkillCuratorReview"))
    assertTrue(agentUi.contains("不会自动删除"))
    assertTrue(agentUi.contains("Learned Skill drafts"))
    assertTrue(agentUi.contains("NbgLearnedSkillDraftQueueCard"))
    assertTrue(agentUi.contains("learnedDraftQueue = hanakoState.learnedSkillDraftQueue"))
    assertTrue(agentUi.contains("NbgLearnedSkillDraftRow"))
    assertTrue(agentUi.contains("queue.pendingReviewCount"))
    assertTrue(agentUi.contains("queue.blockedCount"))
    assertTrue(agentUi.contains("onRejectLearnedDraft"))
    assertTrue(agentUi.contains("hanako.rejectLearnedSkillDraft(it)"))
    assertTrue(agentUi.contains("NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE.joinToString"))
    assertTrue(agentUi.contains("低/中风险可自动写入本地 Skill artifact 并启用"))
    assertTrue(agentUi.contains("可回滚"))
    assertTrue(agentUi.contains("NbgSkillBundlesCard"))
    assertTrue(agentUi.contains("NbgExternalSkillPathsCard"))
    assertTrue(agentUi.contains("NbgSkillBundleDialog"))
    assertTrue(agentUi.contains("NbgExternalSkillPathsDialog"))
    assertTrue(agentUi.contains("Icons.Filled.Translate"))
    assertTrue(agentUi.contains("Icons.Filled.Visibility"))
    assertTrue(agentUi.contains("translatedSkillNames"))
    assertTrue(agentUi.contains("skill.name in translatedSkillNames"))
    assertTrue(agentUi.contains("NbgSkillDetailDialog"))
    assertTrue(agentUi.contains("onOpenDetail = { detailTarget = skill }"))
    assertTrue(agentUi.contains("verticalScroll(rememberScrollState())"))
    assertTrue(agentUi.contains("val skillTranslationState = remember { NbgAgentSkillTranslationState() }"))
    assertTrue(agentUi.contains("var translations by mutableStateOf<Map<String, String>>(emptyMap())"))
    assertTrue(agentUi.contains("var translatingSkillName by mutableStateOf<String?>(null)"))
    assertTrue(agentUi.contains("var messages by mutableStateOf<Map<String, String>>(emptyMap())"))
    assertTrue(agentUi.contains("skillTranslationState.recordNoModel(skill.name)"))
    assertTrue(agentUi.contains("skillTranslationState.applySuccess(skill.name, result)"))
    assertTrue(agentUi.contains("skillTranslationState.applyFailure(skill.name, error)"))
    assertTrue(agentUi.contains("skillTranslationState.finish(skill.name)"))
    assertTrue(agentUi.contains("apiClient.translateSkillDescriptions"))
    assertTrue(agentUi.contains("listOf(skill)"))
    assertTrue(agentUi.contains("translatedDescriptions = skillTranslationState.translations"))
    assertTrue(agentUi.contains("translatingSkillName = skillTranslationState.translatingSkillName"))
    assertTrue(agentUi.contains("translationMessages = skillTranslationState.messages"))
    assertTrue(agentUi.contains("nbgSkillDescriptionForDisplay(skill, translated, translatedDescription)"))
    assertTrue(agentUi.contains("translationMessage = translationMessages[skill.name]"))
    assertTrue(agentUi.contains("正在读取 HanakoPro Skills"))
    assertTrue(agentUi.contains("正在翻译..."))
    assertTrue(agentUi.contains("https://.../SKILL.md"))
    assertTrue(agentUi.contains("/root/skills/my-skill"))

    assertTrue(bridge.contains("HanakoSkillSummary"))
    assertTrue(bridge.contains("HanakoSkillsSnapshot"))
    assertTrue(bridge.contains("HanakoSkillInstallInput"))
    assertTrue(bridge.contains("HanakoSkillBundle"))
    assertTrue(bridge.contains("HanakoExternalSkillPaths"))
    assertTrue(bridge.contains("downloadSkillInstallUrl"))
    assertTrue(bridge.contains("isSkillInstallUrl"))
    assertTrue(bridge.contains(".nbg-skill-installs"))
    assertTrue(bridge.contains("allow_github_fetch"))
    assertTrue(bridge.contains("nbgFormatThinkingTextForDisplay"))
    assertTrue(bridge.contains("NbgSkillDescriptionTranslateResult"))
    assertTrue(bridge.contains("translateSkillDescriptions"))
    assertTrue(bridge.contains("nbgParseSkillDescriptionTranslations"))
    assertTrue(bridge.contains("fun displayDescription(preferChinese: Boolean)"))
    assertTrue(bridge.contains("toChineseSkillDescription"))
    assertTrue(bridge.contains("/api/skills?agentId="))
    assertTrue(bridge.contains("/api/agents/"))
    assertTrue(bridge.contains("/skills/install?agentId="))
    assertTrue(bridge.contains("/api/skills/reload"))
    assertTrue(bridge.contains("/api/skills/bundles?agentId="))
    assertTrue(bridge.contains("/api/skills/external-paths"))
    assertTrue(bridge.contains("ensureAndroidBundledSkillsEnabled"))
    assertTrue(bridge.contains("nbgMergeTrustedBundledSkillEnablement(snapshot.visibleSkills, verifiedBundledSkillNames)"))
    assertTrue(bridge.contains("fun loadSkills(agentId: String = HANA_DEFAULT_MCP_AGENT_ID)"))
    assertTrue(bridge.contains("fun reloadSkills(agentId: String = HANA_DEFAULT_MCP_AGENT_ID)"))
    assertTrue(bridge.contains("fun installSkill(input: HanakoSkillInstallInput"))
    assertTrue(bridge.contains("fun setSkillEnabled(skillName: String, enabled: Boolean"))
    assertTrue(bridge.contains("fun deleteSkill(skillName: String"))
    assertTrue(bridge.contains("fun createSkillBundle("))
    assertTrue(bridge.contains("fun updateSkillBundle("))
    assertTrue(bridge.contains("fun deleteSkillBundle("))
    assertTrue(bridge.contains("fun setExternalSkillPaths("))
    assertTrue(bridge.contains("learnedSkillDraftQueue: NbgLearnedSkillDraftQueue"))
    assertTrue(bridge.contains("NbgLearnedSkillDraftStore(appContext)"))
    assertTrue(bridge.contains("loadLearnedSkillDraftQueue()"))
    assertTrue(bridge.contains("rejectLearnedSkillDraft(id: String)"))
    assertTrue(bridge.contains("parseHanakoSkillsSnapshot"))
    assertTrue(bridge.contains("parseHanakoSkillBundles"))
    assertTrue(bridge.contains("parseHanakoExternalSkillPaths"))
  }

  @Test
  fun bundledEngineeringCoreSkillHasRequiredMetadataAndWorkflows() {
    val skill = File("src/main/assets/nbg-default-skills/nbg-engineering-core/SKILL.md")
    assertTrue(skill.isFile)
    val text = skill.readText()
    assertTrue(text.contains("name: nbg-engineering-core"))
    assertTrue(text.contains("description: NBG built-in engineering core skill"))
    assertTrue(text.contains("## 诊断"))
    assertTrue(text.contains("## 结构俯瞰"))
    assertTrue(text.contains("## TDD"))
    assertTrue(text.contains("## Agent Device"))
    assertTrue(text.contains("## Dogfood"))
    assertTrue(text.contains("## Code Review"))
    assertTrue(text.contains("## Neat Freak"))
    assertTrue(text.contains("codegraph_index"))
    assertTrue(text.contains("codegraph_impact"))
    assertTrue(text.contains("codegraph_node"))
    assertTrue(text.contains("codegraph_explore"))
    assertTrue(text.contains("codegraph_callers"))
    assertTrue(text.contains("codegraph_callees"))
    assertTrue(text.contains("## Memory"))
    assertTrue(text.contains("memory_suggest"))
    assertTrue(text.contains("memory_save"))
    assertTrue(text.contains("memory_search"))
    assertTrue(text.contains("bug_note"))
  }

  @Test
  fun bundledHanakoServerPackOnlyShipsNbgEngineeringCoreSkill() {
    val pack = File("src/main/assets/hanako-server-linux-arm64-node22.nbgpack")
    val marker = File("src/main/assets/hanako-server-linux-arm64-node22.nbgpack.marker")
    assertTrue(pack.isFile)
    assertTrue(marker.isFile)
    val expectedMarker = "size=${pack.length()};sha256=${sha256Hex(pack)}"
    assertEquals(expectedMarker, marker.readText().trim())
    val launcher = File("src/main/java/com/nbg/android/HanakoServerLauncher.kt").readText()
    assertTrue(launcher.contains("context.assets.open(HANAKO_SERVER_PACK_MARKER)"))
    assertTrue(launcher.contains("NBG_HANAKO_RUNTIME_PATCH_TARGET_PACK_MARKER"))
    assertTrue(launcher.contains(expectedMarker))
    val listing = ProcessBuilder("tar", "tzf", pack.absolutePath)
      .directory(File("."))
      .redirectErrorStream(true)
      .start()
      .inputStream
      .bufferedReader()
      .readText()
    assertTrue(listing.contains("./skills2set/nbg-engineering-core/SKILL.md"))
    val packSkill = ProcessBuilder(
      "tar",
      "xzf",
      pack.absolutePath,
      "-O",
      "./skills2set/nbg-engineering-core/SKILL.md",
    )
      .directory(File("."))
      .redirectErrorStream(true)
      .start()
    val packSkillBytes = packSkill.inputStream.readBytes()
    assertEquals(0, packSkill.waitFor())
    val defaultSkillBytes = File("src/main/assets/nbg-default-skills/nbg-engineering-core/SKILL.md").readBytes()
    assertTrue(packSkillBytes.contentEquals(defaultSkillBytes))
    assertTrue(listing.contains("./plugins/codegraph/manifest.json"))
    assertTrue(listing.contains("./plugins/codegraph/tools/search.js"))
    assertTrue(listing.contains("./plugins/codegraph/tools/node.js"))
    assertTrue(listing.contains("./plugins/codegraph/tools/explore.js"))
    assertTrue(listing.contains("./plugins/codegraph/tools/callers.js"))
    assertTrue(listing.contains("./plugins/codegraph/tools/callees.js"))
    assertTrue(listing.contains("./plugins/memory/manifest.json"))
    assertTrue(listing.contains("./plugins/memory/tools/suggest.js"))
    assertTrue(listing.contains("./plugins/memory/tools/save.js"))
    assertTrue(listing.contains("./plugins/memory/tools/search.js"))
    val bundle = ProcessBuilder("tar", "xzf", pack.absolutePath, "-O", "./bundle/index.js")
      .directory(File("."))
      .redirectErrorStream(true)
      .start()
      .inputStream
      .bufferedReader()
      .readText()
    assertTrue(bundle.contains("memory:context"))
    assertTrue(bundle.contains("name: \"Agent\""))
    assertTrue(bundle.contains("name: \"WolfPack\""))
    assertTrue(bundle.contains("agent-trigger"))
    val memoryPlugin = ProcessBuilder("tar", "xzf", pack.absolutePath, "-O", "./plugins/memory/index.js")
      .directory(File("."))
      .redirectErrorStream(true)
      .start()
      .inputStream
      .bufferedReader()
      .readText()
    assertTrue(memoryPlugin.contains("memory:context"))
    listOf(
      "office-documents",
      "quiet-musing",
      "research-platform",
      "user-guide",
      "hana-plugin-creator",
      "skill-creator",
    ).forEach { removed ->
      assertFalse(listing.contains("./skills2set/$removed/"))
    }
  }

  @Test
  fun skillDescriptionTranslationParserAcceptsJsonFences() {
    val parsed = nbgParseSkillDescriptionTranslations(
      """
      ```json
      {"office-documents":"读取和编辑 Office 文档","quiet-musing":"复杂问题深度推理"}
      ```
      """.trimIndent(),
      setOf("office-documents", "quiet-musing"),
    )
    assertEquals("读取和编辑 Office 文档", parsed["office-documents"])
    assertEquals("复杂问题深度推理", parsed["quiet-musing"])
  }

  @Test
  fun androidMcpServerContractIsDocumentedAndWired() {
    val contract = File("../docs/contracts/android-mcp-server-contract.md").readText()
    val index = File("../docs/contracts/README.md").readText()
    val contractSource = File("../android-mcp-server/src/main/java/com/nbg/android/mcpserver/AndroidMcpServerContract.kt").readText()
    val service = File("../android-mcp-server/src/main/java/com/nbg/android/mcpserver/McpServerService.kt").readText()
    val activity = File("../android-mcp-server/src/main/java/com/nbg/android/mcpserver/MainActivity.kt").readText()
    val moduleTest = File("../android-mcp-server/src/test/java/com/nbg/android/mcpserver/McpServerBoundaryTest.kt").readText()
    val manifest = File("../android-mcp-server/src/main/AndroidManifest.xml").readText()

    listOf(
      "Android MCP Server Contract",
      "nbg-android-mcp-server-v1",
      "127.0.0.1",
      "37666",
      "android_device_info",
      "android_echo",
      "ReadOnly",
      "EchoOnly",
      "Authorization: Bearer",
      "401",
      "local bearer token",
      "NBG_ANDROID_MCP_DISCOVERY_POLICY_VERSION",
      "NBG_ANDROID_MCP_SIDE_EFFECT_POLICY_VERSION",
      "main_app_discovery_contract",
      "main_app_permission_ui",
      "SideEffectBlocked",
      "None for v1",
      "McpServerService",
      "android:exported=\"false\"",
      "Adding any side-effect tool requires",
    ).forEach {
      assertTrue(contract.contains(it))
    }

    assertTrue(index.contains("android-mcp-server-contract"))
    assertTrue(index.contains("P1 active"))
    assertTrue(index.contains("AndroidMcpServerContract.kt"))

    assertTrue(contractSource.contains("NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION = \"nbg-android-mcp-server-v1\""))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_LOOPBACK_HOST = \"127.0.0.1\""))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_PORT = 37666"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_AUTH_SCHEME = \"Bearer\""))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_BEARER_TOKEN_BYTES = 32"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_BEARER_TOKEN_MIN_LENGTH = 32"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_DISCOVERY_POLICY_VERSION = \"nbg-android-mcp-discovery-v1\""))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_SIDE_EFFECT_POLICY_VERSION = \"nbg-android-mcp-side-effect-v1\""))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_EPHEMERAL_PORT_REQUIRED_EVIDENCE"))
    assertTrue(contractSource.contains("NBG_ANDROID_MCP_SIDE_EFFECT_REQUIRED_EVIDENCE"))
    assertTrue(contractSource.contains("nbgReviewAndroidMcpEphemeralPortMigration"))
    assertTrue(contractSource.contains("nbgReviewAndroidMcpStandaloneSideEffectTool"))
    assertTrue(contractSource.contains("nbgAndroidMcpRequestAuthorized"))
    assertTrue(contractSource.contains("AndroidMcpToolBoundary.ReadOnly"))
    assertTrue(contractSource.contains("AndroidMcpToolBoundary.EchoOnly"))
    assertTrue(service.contains("ServerSocket(port, 50, InetAddress.getByName(NBG_ANDROID_MCP_LOOPBACK_HOST))"))
    assertTrue(service.contains("bearerTokenProvider = { ensureBearerToken(this) }"))
    assertTrue(service.contains("bearerTokenProvider()"))
    assertTrue(service.contains("markServerListening(listeningPort, bearerTokenPresent = bearerToken.isNotBlank())"))
    assertTrue(service.contains("markServerStopped(error.message)"))
    assertTrue(service.contains("KEY_LAST_ERROR"))
    assertTrue(service.contains("if (stopped) break"))
    assertTrue(service.contains("throw error"))
    assertTrue(service.contains("writeUnauthorized(client)"))
    assertTrue(service.contains("WWW-Authenticate: Bearer realm=\\\"NBG Android MCP\\\""))
    assertTrue(service.contains("KEY_BEARER_TOKEN"))
    assertTrue(service.contains("KEY_BEARER_TOKEN_PRESENT"))
    assertTrue(activity.contains("Copy Bearer Token"))
    assertTrue(activity.contains("Regenerate Bearer Token"))
    assertTrue(activity.contains("Token present: "))
    assertTrue(activity.contains("ClipDescription.EXTRA_IS_SENSITIVE"))
    assertTrue(activity.contains("CLIPBOARD_TOKEN_TTL_MS"))
    assertTrue(activity.contains("clearPrimaryClip()"))
    assertTrue(service.contains("NBG_ANDROID_MCP_TOOLS.forEach"))
    assertTrue(service.contains(".put(\"contractVersion\", NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION)"))
    assertTrue(service.contains(".put(\"boundary\", contract.boundary.name)"))
    assertTrue(service.contains(".put(\"isError\", true)"))
    assertTrue(moduleTest.contains("bearerAuthorizationRequiresExactLocalToken"))
    assertTrue(moduleTest.contains("mcpHttpServerRejectsUnauthenticatedRequestsBeforeProtocolHandling"))
    assertTrue(moduleTest.contains("mcpHttpServerReadsCurrentBearerTokenForEveryRequest"))
    assertTrue(moduleTest.contains("mcpHttpServerPropagatesUnexpectedAcceptFailure"))
    assertTrue(moduleTest.contains("mcpDiscoveryAndSideEffectPoliciesStayClosedInV1"))
    assertTrue(moduleTest.contains("androidMcpServerContractIsDocumentedAndWired"))
    assertTrue(manifest.contains("android:name=\".McpServerService\""))
    assertTrue(manifest.contains("android:exported=\"false\""))
  }

  @Test
  fun taskCompletionEvidenceContractIsDocumentedAndWired() {
    val contract = File("../docs/contracts/task-completion-evidence-contract.md").readText()
    val index = File("../docs/contracts/README.md").readText()
    val evidence = File("src/main/java/com/nbg/android/NbgTaskCompletionEvidence.kt").readText()
    val bridge = File("src/main/java/com/nbg/android/HanakoBridge.kt").readText()
    val messageUi = File("src/main/java/com/nbg/android/NbgAgentMessageUi.kt").readText()
    val evidenceTest = File("src/test/java/com/nbg/android/NbgTaskCompletionEvidenceTest.kt").readText()

    listOf(
      "Task Completion Evidence Contract",
      "nbg-task-completion-evidence-v1",
      "diff",
      "file_write",
      "command_exit",
      "test_result",
      "build_result",
      "consolidated_result",
      "User override",
      "Tool statuses may infer evidence",
      "NbgAgentMessageUi",
      "completion evidence strip",
      "NbgTaskCompletionEvidenceTest",
    ).forEach {
      assertTrue(contract.contains(it))
    }
    assertTrue(index.contains("task-completion-evidence-contract"))
    assertTrue(index.contains("P0 active"))
    assertTrue(index.contains("NbgTaskCompletionEvidence.kt"))

    listOf(
      "NBG_TASK_COMPLETION_EVIDENCE_VERSION",
      "NbgTaskCompletionCriterionKind",
      "NbgTaskCompletionEvidenceState",
      "NbgTaskCompletionEvidenceBundle",
      "NbgTaskCompletionEvidenceReview",
      "nbgReviewTaskCompletionEvidence",
      "withInferredTaskCompletionEvidence",
      "visibleTaskCompletionEvidence",
      "nbgInferTaskCompletionEvidence",
      "parseNbgTaskCompletionEvidenceBundle",
      "nbgTaskEvidenceArtifactRef",
    ).forEach {
      assertTrue(evidence.contains(it))
    }
    assertTrue(bridge.contains("taskCompletionEvidence: NbgTaskCompletionEvidenceBundle? = null"))
    assertTrue(bridge.contains("(taskCompletionEvidence ?: nbgInferTaskCompletionEvidence(this@toJson))"))
    assertTrue(bridge.contains("parseNbgTaskCompletionEvidenceBundle"))
    assertTrue(messageUi.contains("NbgTaskCompletionEvidenceStrip"))
    assertTrue(messageUi.contains("NbgTaskCompletionEvidenceDetails"))
    assertTrue(messageUi.contains("visibleTaskCompletionEvidence"))
    assertTrue(evidenceTest.contains("reviewRequiresEvidenceBeforeCompletionUnlessUserOverrides"))
    assertTrue(evidenceTest.contains("failedEvidenceBlocksCompletionEvenWhenPresent"))
    assertTrue(evidenceTest.contains("infersBuildTestDiffFileAndTeamEvidenceFromToolStatus"))
    assertTrue(evidenceTest.contains("cachedToolStatusRoundTripsTaskCompletionEvidence"))
  }

  @Test
  fun toolVisualizationEventContractIsDocumentedAndWired() {
    val contract = File("../docs/contracts/tool-visualization-event-contract.md").readText()
    val index = File("../docs/contracts/README.md").readText()
    val eventContract = File("src/main/java/com/nbg/android/NbgToolVisualizationEventContract.kt").readText()
    val presentation = File("src/main/java/com/nbg/android/NbgToolPresentation.kt").readText()
    val chatUi = File("src/main/java/com/nbg/android/NbgAgentChatUi.kt").readText()
    val agentUi = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    val agentUiLogic = File("src/main/java/com/nbg/android/NbgAgentUiLogic.kt").readText()
    val parser = File("src/main/java/com/nbg/android/HanakoToolParsing.kt").readText()
    val realtime = File("src/main/java/com/nbg/android/HanakoRealtimeToolEvents.kt").readText()
    val messageUi = File("src/main/java/com/nbg/android/NbgAgentMessageUi.kt").readText()

    listOf(
      "Tool Visualization Event Contract",
      "nbg-tool-visualization-events-v1",
      "terminal",
      "file",
      "diff",
      "todo",
      "team_task",
      "team_agent",
      "thinking",
      "confirmation",
      "restored_incomplete",
      "Terminal exit detail",
      "compact confirmation timeline card",
      "confirmation:<confirmIdHashPrefix>",
      "Todo, team, and agent status",
      "HanakoToolStatus",
      "NbgToolVisualizationEventContractTest",
      "restoredHistoryToolsDoNotKeepChatInRunningState",
    ).forEach {
      assertTrue(contract.contains(it))
    }
    assertFalse(contract.contains("stable `confirmation:<confirmId>` key"))

    assertTrue(index.contains("tool-visualization-event-contract"))
    assertTrue(index.contains("P1 active"))
    assertTrue(index.contains("team/agent"))
    assertTrue(index.contains("NbgToolVisualizationEventContract.kt"))

    assertTrue(eventContract.contains("NBG_TOOL_VISUALIZATION_EVENT_CONTRACT_VERSION = \"nbg-tool-visualization-events-v1\""))
    assertTrue(eventContract.contains("NbgToolVisualizationKind"))
    assertTrue(eventContract.contains("NbgToolVisualizationState"))
    assertTrue(eventContract.contains("NBG_TOOL_VISUALIZATION_SUPPORTED_KINDS"))
    assertTrue(eventContract.contains("NBG_TOOL_VISUALIZATION_SUPPORTED_STATES"))
    assertTrue(eventContract.contains("fun HanakoToolStatus.nbgToolVisualizationState()"))
    assertTrue(eventContract.contains("fun HanakoToolStatus.nbgToolVisualizationKind()"))
    assertTrue(eventContract.contains("TeamTask(\"team_task\")"))
    assertTrue(eventContract.contains("TeamAgent(\"team_agent\")"))
    assertTrue(eventContract.contains("fun nbgTodoListToolStatus("))
    assertTrue(eventContract.contains("fun HanakoTeamTaskStatus.nbgTeamTaskToolStatus()"))
    assertTrue(eventContract.contains("fun HanakoTeamAgentStatus.nbgTeamAgentToolStatus()"))
    assertTrue(eventContract.contains("NbgTerminalExitDetail"))
    assertTrue(eventContract.contains("fun HanakoTerminalOutput.nbgTerminalExitDetail()"))
    assertTrue(eventContract.contains("fun nbgConfirmationResolutionToolStatus("))
    assertTrue(eventContract.contains("confirmIdHash?.take(32)"))
    assertTrue(eventContract.contains("nbgConfirmationAuditTimelineLabel()"))
    assertTrue(eventContract.contains("NbgToolVisualizationState.Cancelled"))

    assertTrue(presentation.contains("tool.nbgToolVisualizationState()"))
    assertTrue(presentation.contains("NbgToolVisualizationState.RestoredIncomplete"))
    assertTrue(presentation.contains("nbgTerminalOutputMeta("))
    assertTrue(presentation.contains("terminal.nbgTerminalExitDetail()?.label"))
    assertTrue(presentation.contains("\"confirmation\" -> \"确认\""))
    assertTrue(presentation.contains("\"team_task\" -> \"团队任务\""))
    assertTrue(presentation.contains("\"team_agent\" -> \"Agent 状态\""))
    assertTrue(chatUi.contains("nbgTodoListToolStatus(todos)"))
    assertTrue(chatUi.contains("val todoSemantics = listOf(\"任务清单\", preview, todoToolStatus?.subtitle.orEmpty())"))
    assertTrue(chatUi.contains("contentDescription = todoSemantics"))
    assertTrue(agentUi.contains("nbgConfirmationResolutionToolStatus("))
    assertTrue(agentUi.contains("if (task.isActive || task.agents.any { agent -> agent.running }) streamingControllerState.markStreaming()"))
    assertTrue(agentUi.contains("enqueueToolStatus(task.nbgTeamTaskToolStatus())"))
    assertTrue(agentUi.contains("task.agents.forEach { agent -> enqueueToolStatus(agent.nbgTeamAgentToolStatus()) }"))
    assertFalse(agentUi.contains("is HanakoChatEvent.TeamTaskUpdated -> Unit"))
    assertTrue(agentUi.contains("confirmationState.pendingConfirmation?.takeIf { it.confirmId == event.confirmId }"))
    assertTrue(agentUi.contains("confirmationState.resolveConfirmation(event.confirmId)"))
    assertTrue(agentUiLogic.contains("mergeConfirmationToolStatus"))
    assertTrue(agentUiLogic.contains("mergeConfirmationSubtitle"))
    assertTrue(realtime.contains("parseToolStart"))
    assertTrue(realtime.contains("parseToolEnd"))
    assertTrue(parser.contains("parseTerminalOutput"))
    assertTrue(parser.contains("exitCode"))
    assertTrue(messageUi.contains("NbgToolStatusCard"))
    assertTrue(messageUi.contains("NbgTerminalOutputPreview"))
    assertTrue(messageUi.contains("nbgTerminalOutputMeta(terminal)"))
    assertTrue(messageUi.contains("NbgFileDiffPreview"))
    assertFalse(contract.contains("Whether terminal exit code should become a first-class normalized state detail."))
    assertFalse(contract.contains("Whether confirmation resolved/rejected events should produce a compact timeline card after resolution."))
    assertFalse(contract.contains("Whether todo/team/agent status cards should move onto the same data class as `HanakoToolStatus`."))
  }

  @Test
  fun toolsetsDoctorControlIsDocumentedAndWired() {
    val preferences = File("src/main/java/com/nbg/android/NbgChatPreferenceStore.kt").readText()
    val control = File("src/main/java/com/nbg/android/NbgToolsetControl.kt").readText()
    val doctorUi = File("src/main/java/com/nbg/android/NbgToolsetsDoctorUi.kt").readText()
    val drawerUi = File("src/main/java/com/nbg/android/NbgAgentDrawerUi.kt").readText()
    val agentUi = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    val logic = File("src/main/java/com/nbg/android/NbgAgentUiLogic.kt").readText()
    val test = File("src/test/java/com/nbg/android/NbgToolsetControlTest.kt").readText()

    listOf(
      "toolsetOverrides",
      "saveToolsetEnabled",
      "toNbgToolsetsJson",
      "toNbgToolsetOverrides",
    ).forEach {
      assertTrue(preferences.contains(it))
    }
    listOf(
      "NbgToolsetId",
      "ExpertReview(\"expert_review\"",
      "ContextCompression(\"context_compression\"",
      "nbgBuildToolsetControlRows",
      "withNbgToolsetEnabled",
      "nbgNormalizeToolsetOverrides",
    ).forEach {
      assertTrue(control.contains(it))
    }
    assertTrue(doctorUi.contains("NbgToolsetsDoctorScreen"))
    assertTrue(doctorUi.contains("NbgToolsetControlCard"))
    assertTrue(doctorUi.contains("NbgToolsetsDoctorSummary"))
    assertTrue(drawerUi.contains("Toolsets / Doctor"))
    assertTrue(drawerUi.contains("onOpenToolsetsDoctor"))
    assertTrue(logic.contains("ToolsetsDoctor"))
    assertTrue(agentUi.contains("NbgShellPage.ToolsetsDoctor -> NbgToolsetsDoctorScreen"))
    assertTrue(agentUi.contains("\"toolsets\", \"doctor\", \"tools\" -> NbgShellPage.ToolsetsDoctor"))
    assertTrue(test.contains("defaultsKeepCoreToolsetsOnAndExpertReviewOff"))
    assertTrue(test.contains("buildRowsCombinePreferencesCapabilityHealthAndDerivedSignals"))
  }

  @Test
  fun unifiedNbgTestScriptRunsStableLocalVerification() {
    val script = File("../scripts/nbg_test.sh").readText()

    assertTrue(script.contains("set -euo pipefail"))
    assertTrue(script.contains("export TZ=UTC"))
    assertTrue(script.contains("unset OPENAI_API_KEY"))
    assertTrue(script.contains("exec ./gradlew --no-daemon \"$@\""))
    assertTrue(script.contains(":app:testDebugUnitTest"))
    assertTrue(script.contains(":terminal-core:testDebugUnitTest"))
    assertTrue(script.contains(":android-mcp-server:testDebugUnitTest"))
    assertTrue(script.contains(":app:assembleDebug"))
  }

  @Test
  fun restoredHistoryToolsDoNotKeepChatInRunningState() {
    val restored = HanakoToolStatus(
      key = "history:check",
      kind = "tool",
      toolName = "check_pending_tasks",
      title = "check pending tasks",
      subtitle = "历史记录 / 进行中",
      status = "running",
      running = true,
    ).asRestoredHistoryToolStatus("history:check")
    val status = nbgChatRunStatus(
      HanakoChatState(connected = true, modelName = "DeepSeek V4 Pro"),
      listOf(NbgAgentMessage(10_000L, NbgAgentRole.Tool, restored.title, done = true, toolStatus = restored)),
    )

    assertFalse(restored.running)
    assertEquals("failed", restored.status)
    assertEquals(false, restored.success)
    assertEquals("历史记录 / 未收到结束事件", restored.subtitle)
    assertEquals("空闲", status.label)
  }

  @Test
  fun rollbackDialogExplainsLatestTurnCheckpointBoundary() {
    val dialogs = File("src/main/java/com/nbg/android/NbgAgentDialogs.kt").readText()

    assertTrue(dialogs.contains("Checkpoint / Rollback"))
    assertTrue(dialogs.contains("当前支持最新 turn 回滚"))
    assertTrue(dialogs.contains("刷新会话历史"))
    assertTrue(dialogs.contains("已还原文件数量"))
  }

  @Test
  fun learningPageExposesHermesStyleRecallLoop() {
    val engine = File("src/main/java/com/nbg/android/NbgAutonomousLearningEngine.kt").readText()
    val controller = File("src/main/java/com/nbg/android/HanakoChatController.kt").readText()
    val learningUi = File("src/main/java/com/nbg/android/NbgAutonomousLearningUi.kt").readText()
    val agentUi = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    val test = File("src/test/java/com/nbg/android/NbgAutonomousLearningEngineTest.kt").readText()
    val externalMemoryAdapter = File("src/main/java/com/nbg/android/NbgExternalMemoryAdapter.kt").readText()
    val curatorLoop = File("src/main/java/com/nbg/android/NbgSkillCuratorLoop.kt").readText()
    val curatorSuggestionQueue = File("src/main/java/com/nbg/android/NbgSkillCuratorSuggestionQueue.kt").readText()
    val contextStrategy = File("src/main/java/com/nbg/android/NbgContextCompressionStrategy.kt").readText()
    val sessionFts = File("src/main/java/com/nbg/android/NbgSessionFtsSearch.kt").readText()
    val usageCost = File("src/main/java/com/nbg/android/NbgUsageCostTracker.kt").readText()
    val teamParallel = File("src/main/java/com/nbg/android/NbgTeamParallelPolicy.kt").readText()
    val skillDiff = File("src/main/java/com/nbg/android/NbgSkillDiffMerge.kt").readText()

    assertTrue(engine.contains("NbgLearningRecallBundle"))
    assertTrue(engine.contains("nbgBuildLearningRecallBundle"))
    assertTrue(engine.contains("nbgReadInstalledSkillNodes"))
    assertTrue(engine.contains("installedSkillRoot"))
    assertTrue(engine.contains("NbgLearningRecallKind.Session"))
    assertTrue(engine.contains("nbgLearningRecallSourceRef"))
    assertTrue(engine.contains("NbgLearningGraphStats"))
    assertTrue(controller.contains("buildLearningRecallForCurrentSession"))
    assertTrue(controller.contains("finishLearningTurnAndEmitEnded"))
    assertTrue(controller.contains("buildLearningContextForPrompt"))
    assertTrue(controller.contains("memoryProviderManager.prefetchAll"))
    assertTrue(controller.contains("memoryProviderManager.syncAll"))
    assertTrue(controller.contains("editLearningJourneyNode"))
    assertTrue(controller.contains("deleteLearningJourneyNode"))
    assertTrue(controller.contains("applyLocalSkillManage"))
    assertTrue(controller.contains("handleLocalSlash"))
    assertTrue(controller.contains("\"/compress\""))
    assertTrue(controller.contains("\"/usage\""))
    assertTrue(controller.contains("\"/insights\""))
    assertTrue(controller.contains("\"/search\""))
    assertTrue(controller.contains("\"/cost\""))
    assertTrue(controller.contains("\"/provider\", \"/providers\", \"/model\""))
    assertTrue(controller.contains("maybeTriggerContextCompressionStrategy"))
    assertTrue(controller.contains("NbgUsageCostEvent"))
    assertTrue(controller.contains("NbgSessionFtsIndexStore"))
    assertTrue(controller.contains("nbgBuildSessionFtsDocuments"))
    assertTrue(controller.contains("approveSkillCuratorSuggestion"))
    assertTrue(controller.contains("previewSkillCuratorSuggestionPatch"))
    assertTrue(controller.contains("nbgSearchSessionFts"))
    assertTrue(controller.contains("nbgBuildTeamParallelPlan"))
    assertTrue(controller.contains(".put(\"learningContext\", learningContext)"))
    assertTrue(controller.contains("emitToolStatus(tool)"))
    assertTrue(controller.contains("maybeLearnSkillImprovementFromTool"))
    assertTrue(controller.contains("historyStore.readSummaryIndex(limit = 200)"))
    assertTrue(engine.contains("nbgCompletedTurnMemoryCandidate"))
    assertTrue(engine.contains("ScheduleSuggestion"))
    assertTrue(learningUi.contains("NbgLearningRecallCard"))
    assertTrue(learningUi.contains("NbgContextInsightsCard"))
    assertTrue(learningUi.contains("NbgMemoryProviderPluginsCard"))
    assertTrue(learningUi.contains("onSetContextCompressionMode"))
    assertTrue(learningUi.contains("sessionFtsHits"))
    assertTrue(learningUi.contains("sessionSearchFilter"))
    assertTrue(learningUi.contains("providerBreakdowns"))
    assertTrue(learningUi.contains("API key / token"))
    assertTrue(learningUi.contains("onEditJourneyNode"))
    assertTrue(learningUi.contains("onDeleteJourneyNode"))
    assertTrue(learningUi.contains("跨会话召回"))
    assertTrue(learningUi.contains("onBuildRecall"))
    assertTrue(agentUi.contains("onBuildRecall = { hanako.buildLearningRecallForCurrentSession() }"))
    assertTrue(agentUi.contains("onSetContextCompressionMode = { hanako.setContextCompressionMode(it) }"))
    assertTrue(agentUi.contains("onSearchLocalSessionsFts = { hanako.searchLocalSessionsFts(it) }"))
    assertTrue(externalMemoryAdapter.contains("class NbgExternalMemoryAdapter"))
    assertTrue(externalMemoryAdapter.contains("prefetchAll"))
    assertTrue(externalMemoryAdapter.contains("syncAll"))
    assertTrue(curatorLoop.contains("NbgSkillCuratorAndroidLlmLoop"))
    assertTrue(curatorLoop.contains("setLlmReviewEnabled"))
    assertTrue(curatorLoop.contains("recordSuggestions(result.suggestions)"))
    assertTrue(curatorSuggestionQueue.contains("data class NbgSkillCuratorSuggestionQueue"))
    assertTrue(curatorSuggestionQueue.contains("Pending(\"pending\""))
    assertTrue(curatorSuggestionQueue.contains("toSkillManageRequestOrNull"))
    assertTrue(curatorSuggestionQueue.contains("proposedContent"))
    assertTrue(contextStrategy.contains("enum class NbgContextCompressionMode"))
    assertTrue(contextStrategy.contains("Auto(\"auto\""))
    assertTrue(sessionFts.contains("data class NbgSessionFtsHit"))
    assertTrue(sessionFts.contains("SQLiteOpenHelper"))
    assertTrue(sessionFts.contains("CREATE VIRTUAL TABLE IF NOT EXISTS"))
    assertTrue(usageCost.contains("data class NbgUsageCostState"))
    assertTrue(usageCost.contains("providerBreakdowns"))
    assertTrue(usageCost.contains("modelBreakdowns"))
    assertTrue(teamParallel.contains("NbgTeamParallelPlan"))
    assertTrue(skillDiff.contains("relativePath"))
    assertTrue(skillDiff.contains("NbgSkillManageAction.WriteFile"))
    assertTrue(test.contains("learningRecallRanksLocalMemoryProfileSkillDraftsAndSessions"))
    assertTrue(test.contains("skillImprovementCandidateUsesToolEvidenceAndHighRiskStaysReviewGated"))
    assertTrue(test.contains("completedAssistantTurnCreatesReusableMemoryFromFullTrajectory"))
    val parityTest = File("src/test/java/com/nbg/android/NbgHermesLearningParityTest.kt").readText()
    assertTrue(parityTest.contains("memoryProviderManagerPrefetchSyncAndQueueWrapLocalLearningEngine"))
    assertTrue(parityTest.contains("journeyMutationsCanEditAndDeleteMemoryProfileSoulAndSkillDraftNodes"))
    assertTrue(parityTest.contains("skillManageSupportsCreatePatchSupportFileAndArchiveInsideLocalSkillRoot"))
    assertTrue(parityTest.contains("installedSkillGraphReadsRelatedSkillsAndLinksMemoryToInstalledSkill"))
    assertTrue(parityTest.contains("curatorReviewArchivesUnusedDisabledLearnedSkillsOnly"))
    val advancedParityTest = File("src/test/java/com/nbg/android/NbgHermesAdvancedParityTest.kt").readText()
    assertTrue(advancedParityTest.contains("skillDiffBuildsHunksAndCanMergeSelectedBlocks"))
    assertTrue(advancedParityTest.contains("externalMemoryProvidersAreRegisteredButDisabledUntilConfigured"))
    assertTrue(advancedParityTest.contains("externalMemoryAdapterExtractsProviderResponses"))
    assertTrue(advancedParityTest.contains("curatorLlmSuggestionsParseJson"))
    assertTrue(advancedParityTest.contains("curatorSuggestionQueueTracksPendingAndStatuses"))
    assertTrue(advancedParityTest.contains("providerProfileBuildsEndpointsAndExtraBody"))
    assertTrue(advancedParityTest.contains("contextCompressionStrategyTriggersAtThreshold"))
    assertTrue(advancedParityTest.contains("sessionFtsRanksSummaryAndMessageHits"))
    assertTrue(advancedParityTest.contains("sessionFtsBuildsDocumentsForMemorySkillsAndDrafts"))
    assertTrue(advancedParityTest.contains("usageCostStateEstimatesCost"))
    assertTrue(advancedParityTest.contains("teamParallelPlanAddsBudgetToAgents"))
    assertTrue(advancedParityTest.contains("skillDiffPreviewSupportsSupportFiles"))
    assertTrue(advancedParityTest.contains("modelProviderProfilesDetectSavedUrlApiEntries"))
    assertTrue(advancedParityTest.contains("contextInsightsSummarizeUsageLearningAndCompressionAdvice"))
  }

  @Test
  fun learningPageWiresScheduleRunnerAndAndroidShareGateway() {
    val manifest = File("src/main/AndroidManifest.xml").readText()
    val mainActivity = File("src/main/java/com/nbg/android/MainActivity.kt").readText()
    val controller = File("src/main/java/com/nbg/android/HanakoChatController.kt").readText()
    val schedule = File("src/main/java/com/nbg/android/NbgScheduleAutomation.kt").readText()
    val scheduleWorker = File("src/main/java/com/nbg/android/NbgScheduleWorker.kt").readText()
    val buildGradle = File("build.gradle.kts").readText()
    val versionCatalog = File("../gradle/libs.versions.toml").readText()
    val gatewayShare = File("src/main/java/com/nbg/android/NbgGatewayShareIntent.kt").readText()
    val gatewayReply = File("src/main/java/com/nbg/android/NbgGatewayNotificationReplyReceiver.kt").readText()
    val learningUi = File("src/main/java/com/nbg/android/NbgAutonomousLearningUi.kt").readText()

    assertTrue(manifest.contains("android.intent.action.SEND"))
    assertTrue(manifest.contains("android:mimeType=\"text/*\""))
    assertTrue(manifest.contains(".NbgGatewayNotificationReplyReceiver"))
    assertTrue(manifest.contains("android:exported=\"false\""))
    assertTrue(mainActivity.contains("onNewIntent"))
    assertTrue(mainActivity.contains("nbgRecordGatewayShareIntent"))
    assertTrue(mainActivity.contains("gatewayInboxRefreshToken"))
    assertTrue(schedule.contains("nbgRunScheduledAutomationNow"))
    assertTrue(schedule.contains("local-only"))
    assertTrue(schedule.contains("不会静默执行 shell"))
    assertTrue(controller.contains("nbgRunScheduledAutomationNow"))
    assertTrue(controller.contains("NbgScheduleRunContext"))
    assertTrue(controller.contains("NbgScheduleWorkManager.ensureScheduled(appContext)"))
    assertTrue(scheduleWorker.contains("class NbgScheduleWorker"))
    assertTrue(scheduleWorker.contains("PeriodicWorkRequestBuilder<NbgScheduleWorker>(15, TimeUnit.MINUTES)"))
    assertTrue(scheduleWorker.contains("runDueAutomations"))
    assertTrue(buildGradle.contains("libs.androidx.work.runtime.ktx"))
    assertTrue(versionCatalog.contains("work-runtime-ktx"))
    assertTrue(gatewayShare.contains("Intent.ACTION_SEND"))
    assertTrue(gatewayShare.contains("NbgInboundMessageSource.AndroidShareSheet"))
    assertTrue(gatewayShare.contains("canExecuteTools = false"))
    assertTrue(gatewayReply.contains("RemoteInput.getResultsFromIntent"))
    assertTrue(gatewayReply.contains("NbgInboundMessageSource.NotificationReply"))
    assertTrue(gatewayReply.contains("canExecuteTools = false"))
    assertTrue(learningUi.contains("NbgLearningScheduleRunRow"))
    assertTrue(learningUi.contains("最近运行"))
  }

  @Test
  fun learningPageCanShareRedactedTrajectoryExportJson() {
    val agentUi = File("src/main/java/com/nbg/android/NbgAgentUi.kt").readText()
    val learningUi = File("src/main/java/com/nbg/android/NbgAutonomousLearningUi.kt").readText()
    val share = File("src/main/java/com/nbg/android/NbgDiagnosticsExportShare.kt").readText()
    val trajectoryTest = File("src/test/java/com/nbg/android/NbgTrajectoryExportPolicyTest.kt").readText()

    assertTrue(agentUi.contains("shareTrajectoryExport"))
    assertTrue(agentUi.contains("nbgBuildTrajectoryExportShareIntent"))
    assertTrue(agentUi.contains("轨迹导出未通过本地脱敏检查"))
    assertTrue(learningUi.contains("onShareTrajectoryExport"))
    assertTrue(learningUi.contains("NbgInlineActionButton(label = \"分享\""))
    assertTrue(share.contains("nbgBuildTrajectoryExportShareIntent"))
    assertTrue(share.contains("NBG Trajectory Export"))
    assertTrue(share.contains("nbg-trajectory-"))
    assertTrue(trajectoryTest.contains("trajectoryExportAttachmentUsesDedicatedJsonNameAndPrunesOldTrajectoryExports"))
  }

  @Test
  fun mcpConnectorShowsToolGatewayCategoryBadges() {
    val models = File("src/main/java/com/nbg/android/HanakoMcpModels.kt").readText()
    val ui = File("src/main/java/com/nbg/android/NbgAgentMcpUi.kt").readText()
    val test = File("src/test/java/com/nbg/android/HanakoMcpToolCategoryTest.kt").readText()

    listOf("WebSearch", "Browser", "Vision", "Image", "Speech", "Media", "Memory").forEach {
      assertTrue(models.contains(it))
    }
    assertTrue(models.contains("nbgMcpToolCategory"))
    assertTrue(models.contains("toolCategories"))
    assertTrue(ui.contains("NbgMcpCategoryTags"))
    assertTrue(ui.contains("nbgMcpToolCategory(tool).label"))
    assertTrue(test.contains("mcpToolCategoryRecognizesHermesToolGatewayFamilies"))
    assertTrue(test.contains("connectorSummarizesDistinctToolCategoriesInStableOrder"))
  }

  @Test
  fun learnedSkillAutoApplyKeepsRollbackMetadataForPromotionSafety() {
    val policy = File("src/main/java/com/nbg/android/NbgLearnedSkillDraftPolicy.kt").readText()
    val store = File("src/main/java/com/nbg/android/NbgLearnedSkillDraftStore.kt").readText()
    val skillsUi = File("src/main/java/com/nbg/android/NbgAgentSkillsUi.kt").readText()
    val test = File("src/test/java/com/nbg/android/NbgLearnedSkillDraftStoreTest.kt").readText()

    assertTrue(policy.contains("rollbackPath"))
    assertTrue(policy.contains("previousArtifactSha256"))
    assertTrue(store.contains(".nbg-rollback"))
    assertTrue(store.contains("previousSha"))
    assertTrue(skillsUi.contains("可回滚"))
    assertTrue(skillsUi.contains("previousArtifactSha256"))
    assertTrue(test.contains("storeKeepsRollbackMetadataWhenAutoApplyOverwritesExistingArtifact"))
  }
}

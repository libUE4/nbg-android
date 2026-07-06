package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import kotlin.io.path.createTempDirectory

class HanakoBridgeTest {
  @Test
  fun permissionDefaultsStartInAskModeInsteadOfOperate() {
    assertEquals(NBG_PERMISSION_MODE_ASK, NBG_DEFAULT_PERMISSION_MODE)
    assertEquals(NBG_PERMISSION_MODE_ASK, NbgChatPreferences().permissionMode)
    assertEquals(NBG_PERMISSION_MODE_ASK, HanakoSessionFocusState(path = "session.json").permissionMode)
    assertEquals(NBG_PERMISSION_MODE_ASK, HanakoChatState().permissionMode)
    assertEquals("先问", HanakoChatState().permissionModeLabel)
    assertEquals(NBG_PERMISSION_MODE_ASK, nbgNormalizePermissionMode("missing"))
    assertEquals(NBG_PERMISSION_MODE_OPERATE, nbgNormalizePermissionMode("operate"))
    assertEquals("先问", hanakoPermissionModeLabel(null))
    assertEquals("先问", hanakoPermissionModeLabel("missing"))
    assertEquals("操作", hanakoPermissionModeLabel(NBG_PERMISSION_MODE_OPERATE))
  }

  @Test
  fun permissionRiskModelClassifiesSideEffects() {
    val read = nbgPermissionRiskForAction("read file", "/root/app.kt")
    val write = nbgPermissionRiskForAction("write file", "/root/app.kt")
    val delete = nbgPermissionRiskForAction("delete file", "/root/app.kt")
    val shell = nbgPermissionRiskForAction("terminal command", command = "ls -la")
    val destructiveShell = nbgPermissionRiskForAction("terminal command", command = "rm -rf /root/project")
    val skill = nbgPermissionRiskForAction("install skill", "https://example.com/SKILL.md")
    val memory = nbgPermissionRiskForAction("memory save", "project_fact")
    val memoryToolSave = nbgPermissionRiskForAction("memory_save", "project_fact")
    val memoryToolUpdate = nbgPermissionRiskForAction("memory_update", "decision")
    val memoryToolDelete = nbgPermissionRiskForAction("memory_delete", "m1")
    val mcp = nbgPermissionRiskForAction("mcp tools/call", "android_file_write")
    val petDex = nbgPermissionRiskForAction("install pet", "https://assets.petdex.dev/pets/boba/sprite.webp")
    val petDexNamed = nbgPermissionRiskForAction("petdex resource install", "boba")
    val plugin = nbgPermissionRiskForAction("plugin install", "https://example.com/plugin")

    assertEquals(NbgPermissionRiskTier.Low, read.tier)
    assertEquals(NbgPermissionRiskTier.High, write.tier)
    assertEquals(NbgPermissionRiskTier.Dangerous, delete.tier)
    assertEquals(NbgPermissionRiskTier.Medium, shell.tier)
    assertEquals(NbgPermissionRiskTier.Dangerous, destructiveShell.tier)
    assertEquals(NbgPermissionRiskTier.High, skill.tier)
    assertEquals(NbgPermissionRiskTier.High, memory.tier)
    assertEquals(NbgPermissionRiskTier.High, memoryToolSave.tier)
    assertEquals(NbgPermissionRiskTier.High, memoryToolUpdate.tier)
    assertEquals(NbgPermissionRiskTier.Dangerous, memoryToolDelete.tier)
    assertEquals(NbgPermissionRiskTier.High, mcp.tier)
    assertEquals(NbgPermissionRiskTier.High, petDex.tier)
    assertEquals(NbgPermissionRiskTier.High, petDexNamed.tier)
    assertEquals(NbgPermissionRiskTier.High, plugin.tier)
    assertFalse(read.requiresConfirmation)
    assertTrue(shell.requiresConfirmation)
    assertFalse(shell.strongConfirmation)
    assertTrue(write.requiresConfirmation)
    assertTrue(delete.strongConfirmation)
    assertTrue(petDex.requiresConfirmation)
  }

  @Test
  fun mediumTerminalCommandsRequireConfirmationWithoutStrongConfirmation() {
    val risk = nbgPermissionRiskForAction("terminal command", command = "ls -la /root/project")
    val declaredMedium = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-medium",
          "action": "terminal command",
          "riskTier": "medium",
          "command": "npm test"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(NbgPermissionRiskTier.Medium, risk.tier)
    assertTrue(risk.requiresConfirmation)
    assertFalse(risk.strongConfirmation)
    assertEquals(NbgPermissionRiskTier.Medium, declaredMedium.tier)
    assertTrue(declaredMedium.requiresConfirmation)
    assertFalse(declaredMedium.strongConfirmation)
    assertTrue(declaredMedium.recoveryHint.contains("计划模式"))
  }

  @Test
  fun declaredRiskCannotDowngradeDestructiveTerminalCommand() {
    val dangerous = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-danger-downgrade",
          "action": "terminal command",
          "riskTier": "medium",
          "command": "rm -rf /root/project"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(NbgPermissionRiskTier.Dangerous, dangerous.tier)
    assertTrue(dangerous.requiresConfirmation)
    assertTrue(dangerous.strongConfirmation)
  }

  @Test
  fun declaredRiskCannotDowngradeExternalResourceOrMcpSideEffects() {
    val petDex = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-pet",
          "action": "install pet",
          "riskTier": "low",
          "target": "https://assets.petdex.dev/pets/boba/sprite.webp"
        }
        """.trimIndent(),
      ),
    )
    val plugin = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-plugin",
          "action": "plugin install",
          "riskTier": "medium",
          "target": "https://example.com/plugin"
        }
        """.trimIndent(),
      ),
    )
    val mcp = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-mcp",
          "action": "mcp tools/call",
          "riskTier": "low",
          "target": "android_file_write"
        }
        """.trimIndent(),
      ),
    )

    listOf(petDex, plugin, mcp).forEach { risk ->
      assertEquals(NbgPermissionRiskTier.High, risk.tier)
      assertTrue(risk.requiresConfirmation)
      assertFalse(risk.strongConfirmation)
    }
  }

  @Test
  fun payloadRiskCannotDowngradeBundledConfirmationShape() {
    val destructiveTerminal = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-terminal-payload",
          "kind": "tool_action_approval",
          "title": "允许 Hana 执行这次操作",
          "subject": {
            "label": "终端操作",
            "detail": "command: rm -rf /root/project"
          },
          "riskTier": "low",
          "payload": {
            "toolName": "terminal_write",
            "params": {
              "command": "rm -rf /root/project"
            }
          }
        }
        """.trimIndent(),
      ),
    )
    val externalResource = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-resource-payload",
          "kind": "tool_action_approval",
          "title": "允许 Hana 执行这次操作",
          "subject": {
            "label": "外部资源",
            "detail": "url: https://example.com/skill.tar.gz"
          },
          "riskTier": "low",
          "payload": {
            "toolName": "install skill",
            "params": {
              "url": "https://example.com/skill.tar.gz"
            }
          }
        }
        """.trimIndent(),
      ),
    )

    assertEquals(NbgPermissionRiskTier.Dangerous, destructiveTerminal.tier)
    assertTrue(destructiveTerminal.requiresConfirmation)
    assertTrue(destructiveTerminal.strongConfirmation)
    assertEquals(NbgPermissionRiskTier.High, externalResource.tier)
    assertTrue(externalResource.requiresConfirmation)
    assertFalse(externalResource.strongConfirmation)
  }

  @Test
  fun backendRiskSchemaCanRaiseRiskAndProvideRecoveryHint() {
    val risk = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-schema",
          "action": "read file",
          "risk": {
            "tier": "high",
            "target": "/root/project/.env",
            "recoveryHint": "确认前检查敏感路径；拒绝后让 Agent 先解释用途。"
          }
        }
        """.trimIndent(),
      ),
    )
    val confirmation = parseHanakoConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-schema",
          "title": "读取敏感文件",
          "action": "read file",
          "riskSchema": {
            "level": "high",
            "targetLabel": "/root/project/.env",
            "mitigation": "确认前检查敏感路径；拒绝后让 Agent 先解释用途。"
          }
        }
        """.trimIndent(),
      ),
    )

    assertEquals(NbgPermissionRiskTier.High, risk.tier)
    assertTrue(risk.requiresConfirmation)
    assertFalse(risk.strongConfirmation)
    assertEquals("/root/project/.env", risk.targetLabel)
    assertTrue(risk.recoveryHint.contains("敏感路径"))
    assertEquals(NbgPermissionRiskTier.High.wireName, confirmation?.riskTier)
    assertEquals("高风险", confirmation?.riskLabel)
    assertEquals("/root/project/.env", confirmation?.targetLabel)
    assertTrue(confirmation?.recoveryHint.orEmpty().contains("敏感路径"))
  }

  @Test
  fun backendRiskSchemaCannotDowngradeLocalInference() {
    val sparseRead = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-schema-sparse",
          "riskSchema": {
            "tier": "low",
            "action": "read file"
          }
        }
        """.trimIndent(),
      ),
    )
    val dangerous = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-schema-downgrade",
          "action": "terminal command",
          "command": "rm -rf /root/project",
          "riskSchema": {
            "tier": "low",
            "recoveryHint": "后端认为安全"
          }
        }
        """.trimIndent(),
      ),
    )
    val dangerousWithoutBackendTier = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-schema-no-tier-hint",
          "action": "terminal command",
          "command": "rm -rf /root/project",
          "recoveryHint": "后端认为安全"
        }
        """.trimIndent(),
      ),
    )
    val externalResource = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-schema-resource",
          "action": "plugin install",
          "risk": {
            "tier": "low",
            "target": "https://example.com/plugin.zip"
          }
        }
        """.trimIndent(),
      ),
    )
    val mcp = nbgPermissionRiskForConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-schema-mcp",
          "action": "mcp tools/call",
          "riskSchema": {
            "tier": "low",
            "target": "android_file_write"
          }
        }
        """.trimIndent(),
      ),
    )

    assertEquals(NbgPermissionRiskTier.Medium, sparseRead.tier)
    assertEquals(NbgPermissionRiskTier.Dangerous, dangerous.tier)
    assertTrue(dangerous.strongConfirmation)
    assertFalse(dangerous.recoveryHint.contains("后端认为安全"))
    assertTrue(dangerous.recoveryHint.contains("危险操作"))
    assertEquals(NbgPermissionRiskTier.Dangerous, dangerousWithoutBackendTier.tier)
    assertFalse(dangerousWithoutBackendTier.recoveryHint.contains("后端认为安全"))
    assertTrue(dangerousWithoutBackendTier.recoveryHint.contains("危险操作"))
    assertEquals(NbgPermissionRiskTier.High, externalResource.tier)
    assertTrue(externalResource.requiresConfirmation)
    assertFalse(externalResource.strongConfirmation)
    assertEquals(NbgPermissionRiskTier.High, mcp.tier)
    assertTrue(mcp.requiresConfirmation)
    assertFalse(mcp.strongConfirmation)
  }

  @Test
  fun confirmationParserAddsRiskAndRecoveryHints() {
    val confirmation = parseHanakoConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-1",
          "title": "写入文件",
          "action": "write file",
          "subject": {
            "label": "文件",
            "detail": "/root/app/src/main/App.kt"
          },
          "actions": {
            "confirmLabel": "写入",
            "rejectLabel": "取消"
          }
        }
        """.trimIndent(),
      ),
    )

    assertEquals("confirm-1", confirmation?.confirmId)
    assertEquals(NbgPermissionRiskTier.High.wireName, confirmation?.riskTier)
    assertEquals("高风险", confirmation?.riskLabel)
    assertEquals("/root/app/src/main/App.kt", confirmation?.targetLabel)
    assertTrue(confirmation?.recoveryHint.orEmpty().contains("拒绝"))

    val dangerous = parseHanakoConfirmationBlock(
      JSONObject(
        """
        {
          "type": "session_confirmation",
          "confirmId": "confirm-2",
          "title": "删除目录",
          "severity": "danger",
          "action": "terminal command",
          "command": "rm -rf /root/project"
        }
        """.trimIndent(),
      ),
    )

    assertEquals(NbgPermissionRiskTier.Dangerous.wireName, dangerous?.riskTier)
    assertEquals("危险操作", dangerous?.riskLabel)
  }

  @Test
  fun parsesNestedHanakoHttpErrorMessages() {
    assertEquals(
      "HTTP 400 bad model",
      nbgParseHanakoHttpError(400, """{"error":{"message":"bad model"}}"""),
    )
    assertEquals(
      "HTTP 500 upstream failed",
      nbgParseHanakoHttpError(500, """{"detail":"upstream failed"}"""),
    )
  }

  @Test
  fun compactsPlainHanakoHttpErrorBodies() {
    assertEquals(
      "HTTP 502 line one line two",
      nbgParseHanakoHttpError(502, "line one\nline two"),
    )
    assertEquals("HTTP 404 空响应", nbgParseHanakoHttpError(404, ""))
    assertEquals(
      "HTTP 404 /api/team/tasks Not Found",
      nbgParseHanakoHttpError(404, "Not Found", "/api/team/tasks"),
    )
  }

  @Test
  fun cacheMoveHelperReplacesExistingTarget() {
    val dir = createTempDirectory(prefix = "nbg-cache-move").toFile()
    val source = dir.resolve("history.tmp")
    val target = dir.resolve("history.json")
    source.writeText("new")
    target.writeText("old")

    nbgMoveReplacingWithAtomicFallbackForTest(source, target)

    assertEquals("new", target.readText())
    assertFalse(source.exists())
  }

  @Test
  fun remoteHistoryParsesArrayContentBlocks() {
    val messages = JSONArray(
      """
        [
          {"role":"user","id":"1","timestamp":"2026-06-23T00:00:00Z","content":[{"type":"input_text","text":"拉取分析 https://github.com/LIUTod/scream-code"}]},
          {"role":"assistant","id":"2","timestamp":"2026-06-23T00:00:01Z","content":[{"type":"thinking","text":"先检查仓库"},{"type":"toolCall","name":"bash"},{"type":"output_text","text":"分析完成"}]}
        ]
      """.trimIndent(),
    )

    val parsed = nbgParseHanakoRemoteMessages(messages)

    assertTrue(parsed.any { it.role == "user" && it.text.contains("scream-code") })
    assertTrue(parsed.any { it.role == "thinking" && it.text == "先检查仓库" })
    assertTrue(parsed.any { it.role == "tool" && it.toolStatus?.toolName == "bash" })
    assertTrue(parsed.any { it.role == "assistant" && it.text == "分析完成" })
  }

  @Test
  fun remoteHistoryParsesStandaloneToolResultMessages() {
    val messages = JSONArray(
      """
        [
          {"role":"toolResult","id":"3","toolName":"terminal_read","content":"BUILD SUCCESSFUL"},
          {"role":"tool","id":"4","name":"write","args":{"path":"/root/app.txt","content":"hello"},"content":"wrote file"}
        ]
      """.trimIndent(),
    )

    val parsed = nbgParseHanakoRemoteMessages(messages)

    val terminal = parsed.first { it.toolStatus?.toolName == "terminal_read" }.toolStatus
    val file = parsed.first { it.toolStatus?.toolName == "write" }.toolStatus
    assertEquals("terminal", terminal?.kind)
    assertEquals("BUILD SUCCESSFUL", terminal?.terminalOutput?.output)
    assertEquals("file", file?.kind)
    assertEquals("hello", file?.filePreview?.previewText)
  }

  @Test
  fun remoteHistoryPreservesWhitespaceOnlyToolPayloads() {
    val messages = JSONArray(
      """
        [
          {"role":"tool","id":"5","name":"write","args":{"path":"/root/spaces.txt","content":" \n  "},"content":"wrote whitespace"},
          {"role":"toolResult","id":"6","toolName":"terminal_read","details":{"id":"term-ws","output":"\n  "}},
          {"role":"assistant","id":"7","content":[{"type":"output_text","text":"  kept\n"}]}
        ]
      """.trimIndent(),
    )

    val parsed = nbgParseHanakoRemoteMessages(messages)

    val file = parsed.first { it.toolStatus?.toolName == "write" }.toolStatus
    val terminal = parsed.first { it.toolStatus?.toolName == "terminal_read" }.toolStatus
    val assistant = parsed.first { it.role == "assistant" }
    assertEquals(" \n  ", file?.filePreview?.previewText)
    assertEquals("\n  ", terminal?.terminalOutput?.output)
    assertEquals("  kept\n", assistant.text)
  }

  @Test
  fun serverInfoParserRejectsInvalidPortsAndBlankTokens() {
    val valid = nbgParseHanakoServerInfo(JSONObject("""{"port":3210,"token":" abc ","pid":12,"version":"1.2.3"}"""))

    assertEquals(3210, valid?.port)
    assertEquals("abc", valid?.token)
    assertEquals(12, valid?.pid)
    assertEquals("1.2.3", valid?.version)
    assertEquals(null, nbgParseHanakoServerInfo(JSONObject("""{"port":0,"token":"abc"}""")))
    assertEquals(null, nbgParseHanakoServerInfo(JSONObject("""{"port":70000,"token":"abc"}""")))
    assertEquals(null, nbgParseHanakoServerInfo(JSONObject("""{"port":3210,"token":"   "}""")))
  }

  @Test
  fun teamStatusParserAcceptsNestedPayloadTaskAndAgentEvents() {
    val task = nbgParseHanakoTeamTaskStatusForTest(
      JSONObject(
        """
        {
          "type": "team_task_started",
          "payload": {
            "task": {
              "id": "task-1",
              "title": "拉取分析",
              "status": "running",
              "agents": [
                {"id": "supervisor", "role": "supervisor", "status": "running"}
              ]
            }
          }
        }
        """.trimIndent(),
      ),
    )

    assertEquals("task-1", task?.taskId)
    assertEquals("拉取分析", task?.title)
    assertEquals("running", task?.agents?.first()?.status)

    val agent = nbgParseHanakoTeamAgentStatusForTest(
      JSONObject(
        """
        {
          "type": "team_agent_update",
          "payload": {
            "agent": {
              "id": "coder",
              "role": "coder",
              "status": "working",
              "summary": "正在修改代码"
            }
          }
        }
        """.trimIndent(),
      ),
      fallbackTaskId = "task-1",
    )

    assertEquals("task-1", agent?.taskId)
    assertEquals("coder", agent?.agentId)
    assertEquals("working", agent?.status)
    assertEquals("正在修改代码", agent?.summary)
  }

  @Test
  fun existingSessionTeamAdapterDoesNotPretendSevenAgentsAreRunning() {
    val parsed = nbgParseHanakoTeamTaskStatusForTest(
      JSONObject(
        """
        {
          "taskId": "team-1",
          "title": "代码仓库分析",
          "mode": "auto",
          "status": "running",
          "summary": "HanakoPro Android 团队任务已接入现有执行会话。",
          "agents": [
            {"agentId": "supervisor", "role": "Supervisor", "title": "总控 Agent", "status": "running", "summary": "准备拆解任务"}
          ]
        }
        """.trimIndent(),
      ),
    )

    val normalized = nbgNormalizeAndroidTeamTaskForTest(parsed!!)

    assertEquals("single_agent_session", normalized.mode)
    assertEquals(1, normalized.agents.size)
    assertEquals("Hanako Agent", normalized.agents.single().title)
    assertEquals(1, normalized.activeCount)
  }

  @Test
  fun realMultiAgentTeamPayloadIsNotCollapsedToSingleAgentAdapter() {
    val parsed = nbgParseHanakoTeamTaskStatusForTest(
      JSONObject(
        """
        {
          "taskId": "team-2",
          "title": "真实团队",
          "mode": "auto",
          "status": "running",
          "summary": "HanakoPro Android 团队任务已接入现有执行会话。",
          "agents": [
            {"agentId": "supervisor", "role": "Supervisor", "title": "总控 Agent", "status": "running"},
            {"agentId": "researcher", "role": "Researcher", "title": "调研员", "status": "running"}
          ]
        }
        """.trimIndent(),
      ),
    )

    val normalized = nbgNormalizeAndroidTeamTaskForTest(parsed!!)

    assertEquals("auto", normalized.mode)
    assertEquals(2, normalized.agents.size)
    assertEquals(2, normalized.activeCount)
  }

  @Test
  fun realAgentEventPromotesSingleSessionAdapterBackToTeamMode() {
    val single = HanakoTeamTaskStatus(
      taskId = "team-3",
      mode = "single_agent_session",
      status = "running",
      agents = listOf(
        HanakoTeamAgentStatus(
          taskId = "team-3",
          agentId = "supervisor",
          role = "Hanako",
          title = "Hanako Agent",
          status = "running",
        ),
      ),
    )

    val merged = nbgMergeHanakoTeamAgentForTest(
      single,
      HanakoTeamAgentStatus(
        taskId = "team-3",
        agentId = "researcher",
        role = "Researcher",
        title = "调研员",
        status = "running",
      ),
    )

    assertEquals("auto", merged.mode)
    assertEquals(1, merged.agents.size)
    assertEquals("researcher", merged.agents.single().agentId)
  }

  @Test
  fun teamStatusHelpersNormalizeBackendStatusVariants() {
    val task = HanakoTeamTaskStatus(
      taskId = "task-2",
      status = "IN-PROGRESS",
      agents = listOf(
        HanakoTeamAgentStatus(
          taskId = "task-2",
          agentId = "coder",
          role = "coder",
          title = "工程师",
          status = "RUNNING",
        ),
      ),
    )

    assertTrue(task.isActive)
    assertEquals(1, task.activeCount)
    assertEquals("working", nbgNormalizeTeamStatus("in-progress"))
    assertEquals("aborted", nbgNormalizeTeamStatus("cancelled"))
  }

  @Test
  fun runningTeamAgentPromotesQueuedTaskToRunning() {
    val task = HanakoTeamTaskStatus(
      taskId = "task-3",
      status = "queued",
      agents = emptyList(),
    )
    val merged = nbgMergeHanakoTeamAgentForTest(
      task,
      HanakoTeamAgentStatus(
        taskId = "task-3",
        agentId = "researcher",
        role = "researcher",
        title = "调研员",
        status = "running",
      ),
    )

    assertEquals("running", merged.status)
    assertTrue(merged.isActive)
    assertEquals(1, merged.activeCount)
  }

  @Test
  fun backendAgentToolHelpersParseWolfPackSubtasks() {
    val event = JSONObject(
      """
      {
        "type": "tool_end",
        "name": "WolfPack",
        "success": true,
        "details": {
          "taskIds": ["subagent-a", "subagent-b"],
          "streamStatus": "running"
        }
      }
      """.trimIndent(),
    )

    val task = nbgBackendAgentToolTask(
      taskId = "WolfPack",
      toolName = nbgBackendAgentToolNameFromEvent(event)!!,
      taskStatus = nbgBackendAgentToolTaskStatus(
        streamStatus = nbgBackendAgentToolStreamStatus(event),
        success = true,
        hasSubagentTasks = nbgBackendAgentToolSubagentTaskIds(event).isNotEmpty(),
      ),
      subagentTaskIds = nbgBackendAgentToolSubagentTaskIds(event),
    )

    assertEquals("backend_agent_tools", task.mode)
    assertEquals("WolfPack", nbgBackendAgentToolNameFromEvent(event))
    assertEquals(listOf("subagent-a", "subagent-b"), nbgBackendAgentToolSubagentTaskIds(event))
    assertEquals("running", task.status)
    assertEquals(2, task.activeCount)
    assertEquals("多 Agent：2 工作中", task.teamRuntimeLabel())
  }

  @Test
  fun backendAgentToolSubtaskUpdatesAggregateCompletion() {
    val task = nbgBackendAgentToolTask(
      taskId = "WolfPack",
      toolName = "WolfPack",
      taskStatus = "running",
      subagentTaskIds = listOf("subagent-a", "subagent-b"),
    )

    val firstDone = task.withBackendAgentToolSubtaskUpdate("subagent-a", "completed", "A 完成")
    val allDone = firstDone.withBackendAgentToolSubtaskUpdate("subagent-b", "completed", "B 完成")

    assertEquals("running", firstDone.status)
    assertEquals(1, firstDone.activeCount)
    assertEquals("completed", allDone.status)
    assertEquals(0, allDone.activeCount)
    assertEquals("多 Agent：已完成", allDone.teamRuntimeLabel())
  }

  @Test
  fun todoParserNormalizesCompletedAliasesAndClearsFinishedLists() {
    assertEquals("completed", nbgNormalizeTodoStatus("DONE"))
    assertEquals("in_progress", nbgNormalizeTodoStatus("in-progress"))
    assertEquals("pending", nbgNormalizeTodoStatus("unknown"))

    val parsed = nbgParseHanakoTodosForTest(
      JSONArray(
        """
        [
          {"content":"one","status":"DONE"},
          {"content":"two","status":"success"}
        ]
        """.trimIndent(),
      ),
    )

    assertEquals(emptyList<HanakoTodoItem>(), parsed)
  }

  @Test
  fun hanakoStatusLabelsNormalizeBackendVariants() {
    assertEquals("failed", nbgNormalizeHanakoStatus("FAILED"))
    assertEquals("in_progress", nbgNormalizeHanakoStatus("in-progress"))
    assertEquals("错误", nbgBridgeStatusLabel("ERROR"))
    assertEquals("失败", nbgDeferredStatusLabel("failure"))
    assertEquals("已取消", nbgDeferredStatusLabel("cancelled"))
  }
}

package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgTaskCompletionEvidenceTest {
  @Test
  fun reviewRequiresEvidenceBeforeCompletionUnlessUserOverrides() {
    val missing = NbgTaskCompletionEvidenceBundle(
      contractId = "task-1",
      title = "修复构建",
      criteria = listOf(
        nbgTaskCriterion(NbgTaskCompletionCriterionKind.Diff, "Diff"),
        nbgTaskCriterion(NbgTaskCompletionCriterionKind.TestResult, "测试"),
      ),
      evidence = listOf(
        nbgTaskEvidence(
          kind = NbgTaskCompletionCriterionKind.Diff,
          state = NbgTaskCompletionEvidenceState.Present,
          label = "Diff",
          summary = "Main.kt",
        ),
      ),
    )
    val overridden = missing.copy(overrideReason = "用户确认无需测试")

    assertEquals(NBG_TASK_COMPLETION_EVIDENCE_VERSION, missing.review.policyVersion)
    assertEquals(NbgTaskCompletionEvidenceState.Missing, missing.review.state)
    assertFalse(missing.review.complete)
    assertEquals(listOf("测试"), missing.review.missingLabels)
    assertEquals("缺少证据 1/2", missing.summaryLabel())

    assertEquals(NbgTaskCompletionEvidenceState.Overridden, overridden.review.state)
    assertTrue(overridden.review.complete)
    assertEquals("用户覆盖完成", overridden.summaryLabel())
    assertTrue(overridden.detailLines().first().contains("用户确认无需测试"))
  }

  @Test
  fun failedEvidenceBlocksCompletionEvenWhenPresent() {
    val failed = NbgTaskCompletionEvidenceBundle(
      contractId = "task-2",
      title = "运行测试",
      criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.TestResult, "测试")),
      evidence = listOf(
        nbgTaskEvidence(
          kind = NbgTaskCompletionCriterionKind.TestResult,
          state = NbgTaskCompletionEvidenceState.Failed,
          label = "测试",
          summary = "./gradlew test failed",
        ),
      ),
    )

    assertEquals(NbgTaskCompletionEvidenceState.Failed, failed.review.state)
    assertFalse(failed.review.complete)
    assertEquals(listOf("测试"), failed.review.failedLabels)
    assertEquals("证据失败", failed.summaryLabel())
  }

  @Test
  fun infersBuildTestDiffFileAndTeamEvidenceFromToolStatus() {
    val diffTool = HanakoToolStatus(
      key = "diff-1",
      kind = "file",
      toolName = "edit",
      title = "文件：Main.kt",
      fileDiff = HanakoFileDiff(fileName = "Main.kt", filePath = "/root/app/Main.kt", unifiedDiff = "+ok"),
    )
    val fileTool = HanakoToolStatus(
      key = "write-1",
      kind = "file",
      toolName = "write",
      title = "文件：README.md",
      success = true,
      filePreview = HanakoFilePreview(fileName = "README.md", filePath = "/root/README.md"),
    )
    val testTool = HanakoToolStatus(
      key = "test-1",
      kind = "terminal",
      toolName = "bash",
      title = "Run tests",
      terminalOutput = HanakoTerminalOutput(sessionId = "term-1", title = "./gradlew test", exitCode = 0, output = "BUILD SUCCESSFUL"),
    )
    val buildTool = HanakoToolStatus(
      key = "build-1",
      kind = "terminal",
      toolName = "bash",
      title = "Gradle assemble",
      detail = "./gradlew :app:assembleDebug",
      terminalOutput = HanakoTerminalOutput(sessionId = "term-2", title = "assemble", exitCode = 1, output = "compile failed"),
    )
    val teamTool = HanakoTeamTaskStatus(
      taskId = "team-1",
      title = "审查 Diff",
      mode = HANA_TEAM_BACKEND_AGENT_MODE,
      status = "completed",
      summary = "已汇总",
    ).nbgTeamTaskToolStatus()

    assertEquals(NbgTaskCompletionCriterionKind.Diff, diffTool.visibleTaskCompletionEvidence()?.criteria?.single()?.kind)
    assertEquals(NbgTaskCompletionEvidenceState.Passed, fileTool.visibleTaskCompletionEvidence()?.review?.state)
    assertEquals(NbgTaskCompletionCriterionKind.TestResult, testTool.visibleTaskCompletionEvidence()?.criteria?.single()?.kind)
    assertEquals(NbgTaskCompletionEvidenceState.Passed, testTool.visibleTaskCompletionEvidence()?.review?.state)
    assertEquals(NbgTaskCompletionCriterionKind.BuildResult, buildTool.visibleTaskCompletionEvidence()?.criteria?.single()?.kind)
    assertEquals(NbgTaskCompletionEvidenceState.Failed, buildTool.visibleTaskCompletionEvidence()?.review?.state)
    assertEquals(NbgTaskCompletionCriterionKind.ConsolidatedResult, teamTool.visibleTaskCompletionEvidence()?.criteria?.single()?.kind)
    assertEquals(NbgTaskCompletionEvidenceState.Passed, teamTool.visibleTaskCompletionEvidence()?.review?.state)
  }

  @Test
  fun cachedToolStatusRoundTripsTaskCompletionEvidence() {
    val bundle = NbgTaskCompletionEvidenceBundle(
      contractId = "task-cache",
      title = "缓存证据",
      criteria = listOf(nbgTaskCriterion(NbgTaskCompletionCriterionKind.CommandExit, "命令")),
      evidence = listOf(
        nbgTaskEvidence(
          kind = NbgTaskCompletionCriterionKind.CommandExit,
          state = NbgTaskCompletionEvidenceState.Passed,
          label = "命令",
          summary = "exit 0",
          artifactRef = "token=secret path",
        ),
      ),
    )
    val tool = HanakoToolStatus(
      key = "tool-cache",
      kind = "tool",
      title = "验证",
      taskCompletionEvidence = bundle,
    )
    val parsed = parseCachedToolStatus(tool.toJson())!!

    assertEquals("task-cache", parsed.taskCompletionEvidence?.contractId)
    assertEquals(NbgTaskCompletionEvidenceState.Passed, parsed.taskCompletionEvidence?.review?.state)
    assertTrue(parsed.hasVisibleToolStatus())
    assertFalse(parsed.taskCompletionEvidence!!.evidence.single().artifactRef.contains("secret path"))
  }
}

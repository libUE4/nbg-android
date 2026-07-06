package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class NbgTrajectoryExportPolicyTest {
  @Test
  fun trajectoryExportRequiresExplicitLocalRedactedEvidence() {
    val history = HanakoHistorySnapshot(
      messages = listOf(
        HanakoHistoryMessage(id = 1L, role = "user", text = "hello", timestampMs = 1L),
        HanakoHistoryMessage(
          id = 2L,
          role = "tool",
          text = "",
          toolStatus = HanakoToolStatus(
            key = "secret-tool",
            title = "terminal",
            detail = "/root/private/file",
            status = "done",
            success = true,
          ),
        ),
      ),
    )

    val allowed = nbgBuildTrajectoryExportBundle(
      history = history,
      learningLog = NbgLearningAuditLog(),
      explicitUserTrigger = true,
      localOnlyDestination = true,
    )
    val blocked = nbgBuildTrajectoryExportBundle(
      history = history,
      learningLog = NbgLearningAuditLog(),
      explicitUserTrigger = false,
      localOnlyDestination = true,
    )

    assertTrue(allowed.review.allowExport)
    assertEquals(1, allowed.messages.size)
    assertFalse(allowed.toJsonString().contains("/root/private/file"))
    assertFalse(allowed.toJsonString().contains("secret-tool"))
    assertFalse(blocked.review.allowExport)
    assertEquals(0, blocked.messages.size)
  }

  @Test
  fun trajectoryExportAttachmentUsesDedicatedJsonNameAndPrunesOldTrajectoryExports() {
    val cacheDir = createTempDirectory("nbg-trajectory-export").toFile()
    val first = nbgWriteJsonExportAttachmentFile(
      cacheDir = cacheDir,
      exportText = """{"one":true}""",
      generatedAtMs = 100L,
      filePrefix = "nbg-trajectory-",
    )
    val second = nbgWriteJsonExportAttachmentFile(
      cacheDir = cacheDir,
      exportText = """{"two":true}""",
      generatedAtMs = 200L,
      filePrefix = "nbg-trajectory-",
    )

    assertEquals(nbgTrajectoryExportAttachmentFileName(100L), first.name)
    assertEquals(nbgTrajectoryExportAttachmentFileName(200L), second.name)
    assertFalse(first.exists())
    assertTrue(second.readText().contains("two"))
  }
}

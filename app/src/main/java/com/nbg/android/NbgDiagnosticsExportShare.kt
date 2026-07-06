package com.nbg.android

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

internal const val NBG_DIAGNOSTICS_EXPORT_MIME_TYPE = "application/json"
internal const val NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_DIR = "diagnostics"
private const val NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_PREFIX = "nbg-diagnostics-"
private const val NBG_TRAJECTORY_EXPORT_ATTACHMENT_PREFIX = "nbg-trajectory-"

internal fun nbgBuildDiagnosticsExportShareIntent(
  context: Context,
  exportText: String,
  generatedAtMs: Long = System.currentTimeMillis(),
): Intent {
  val file = nbgWriteDiagnosticsExportAttachmentFile(context.cacheDir, exportText, generatedAtMs)
  return nbgBuildJsonAttachmentShareIntent(
    context = context,
    file = file,
    subject = "NBG Diagnostics",
    clipLabel = "NBG Diagnostics",
  )
}

internal fun nbgBuildTrajectoryExportShareIntent(
  context: Context,
  bundle: NbgTrajectoryExportBundle,
  generatedAtMs: Long = System.currentTimeMillis(),
): Intent {
  val file = nbgWriteJsonExportAttachmentFile(
    cacheDir = context.cacheDir,
    exportText = bundle.toJsonString(),
    generatedAtMs = generatedAtMs,
    filePrefix = NBG_TRAJECTORY_EXPORT_ATTACHMENT_PREFIX,
  )
  return nbgBuildJsonAttachmentShareIntent(
    context = context,
    file = file,
    subject = "NBG Trajectory Export",
    clipLabel = "NBG Trajectory Export",
  )
}

private fun nbgBuildJsonAttachmentShareIntent(
  context: Context,
  file: File,
  subject: String,
  clipLabel: String,
): Intent {
  val uri = FileProvider.getUriForFile(
    context,
    nbgDiagnosticsExportFileProviderAuthority(context),
    file,
  )
  return Intent(Intent.ACTION_SEND).apply {
    type = NBG_DIAGNOSTICS_EXPORT_MIME_TYPE
    putExtra(Intent.EXTRA_SUBJECT, subject)
    putExtra(Intent.EXTRA_TITLE, file.name)
    putExtra(Intent.EXTRA_STREAM, uri)
    clipData = ClipData.newUri(context.contentResolver, clipLabel, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
}

internal fun nbgDiagnosticsExportFileProviderAuthority(context: Context): String =
  "${context.packageName}.diagnostics"

internal fun nbgWriteDiagnosticsExportAttachmentFile(
  cacheDir: File,
  exportText: String,
  generatedAtMs: Long = System.currentTimeMillis(),
): File =
  nbgWriteJsonExportAttachmentFile(
    cacheDir = cacheDir,
    exportText = exportText,
    generatedAtMs = generatedAtMs,
    filePrefix = NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_PREFIX,
  )

internal fun nbgWriteJsonExportAttachmentFile(
  cacheDir: File,
  exportText: String,
  generatedAtMs: Long = System.currentTimeMillis(),
  filePrefix: String,
): File {
  val dir = File(cacheDir, NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_DIR)
  if (!dir.exists() && !dir.mkdirs()) {
    error("Unable to create diagnostics export attachment directory")
  }
  nbgPruneJsonExportAttachments(dir, filePrefix)
  return File(dir, nbgJsonExportAttachmentFileName(filePrefix, generatedAtMs)).also { file ->
    file.writeText(exportText, Charsets.UTF_8)
  }
}

internal fun nbgDiagnosticsExportAttachmentFileName(generatedAtMs: Long): String =
  nbgJsonExportAttachmentFileName(NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_PREFIX, generatedAtMs)

internal fun nbgTrajectoryExportAttachmentFileName(generatedAtMs: Long): String =
  nbgJsonExportAttachmentFileName(NBG_TRAJECTORY_EXPORT_ATTACHMENT_PREFIX, generatedAtMs)

private fun nbgJsonExportAttachmentFileName(filePrefix: String, generatedAtMs: Long): String =
  "$filePrefix${generatedAtMs.coerceAtLeast(0L)}.json"

private fun nbgPruneJsonExportAttachments(dir: File, filePrefix: String) {
  dir.listFiles()
    ?.filter { it.isFile && it.name.startsWith(filePrefix) && it.extension == "json" }
    ?.forEach { it.delete() }
}

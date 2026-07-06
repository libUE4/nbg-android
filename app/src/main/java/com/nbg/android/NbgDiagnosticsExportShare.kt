package com.nbg.android

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

internal const val NBG_DIAGNOSTICS_EXPORT_MIME_TYPE = "application/json"
internal const val NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_DIR = "diagnostics"
private const val NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_PREFIX = "nbg-diagnostics-"

internal fun nbgBuildDiagnosticsExportShareIntent(
  context: Context,
  exportText: String,
  generatedAtMs: Long = System.currentTimeMillis(),
): Intent {
  val file = nbgWriteDiagnosticsExportAttachmentFile(context.cacheDir, exportText, generatedAtMs)
  val uri = FileProvider.getUriForFile(
    context,
    nbgDiagnosticsExportFileProviderAuthority(context),
    file,
  )
  return Intent(Intent.ACTION_SEND).apply {
    type = NBG_DIAGNOSTICS_EXPORT_MIME_TYPE
    putExtra(Intent.EXTRA_SUBJECT, "NBG Diagnostics")
    putExtra(Intent.EXTRA_TITLE, file.name)
    putExtra(Intent.EXTRA_STREAM, uri)
    clipData = ClipData.newUri(context.contentResolver, "NBG Diagnostics", uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
}

internal fun nbgDiagnosticsExportFileProviderAuthority(context: Context): String =
  "${context.packageName}.diagnostics"

internal fun nbgWriteDiagnosticsExportAttachmentFile(
  cacheDir: File,
  exportText: String,
  generatedAtMs: Long = System.currentTimeMillis(),
): File {
  val dir = File(cacheDir, NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_DIR)
  if (!dir.exists() && !dir.mkdirs()) {
    error("Unable to create diagnostics export attachment directory")
  }
  nbgPruneDiagnosticsExportAttachments(dir)
  return File(dir, nbgDiagnosticsExportAttachmentFileName(generatedAtMs)).also { file ->
    file.writeText(exportText, Charsets.UTF_8)
  }
}

internal fun nbgDiagnosticsExportAttachmentFileName(generatedAtMs: Long): String =
  "$NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_PREFIX${generatedAtMs.coerceAtLeast(0L)}.json"

private fun nbgPruneDiagnosticsExportAttachments(dir: File) {
  dir.listFiles()
    ?.filter { it.isFile && it.name.startsWith(NBG_DIAGNOSTICS_EXPORT_ATTACHMENT_PREFIX) && it.extension == "json" }
    ?.forEach { it.delete() }
}

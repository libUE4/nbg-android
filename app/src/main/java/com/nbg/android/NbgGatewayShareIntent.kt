package com.nbg.android

import android.content.Context
import android.content.Intent
import android.net.Uri

internal const val NBG_START_PAGE_EXTRA = "nbg_start_page"
internal const val NBG_GATEWAY_INBOX_REFRESH_EXTRA = "nbg_gateway_inbox_refresh"

internal fun nbgRecordGatewayShareIntent(
  context: Context,
  intent: Intent?,
  nowMs: Long = System.currentTimeMillis(),
): NbgGatewayInboxState? {
  val message = nbgGatewayInboxMessageFromShareIntent(intent, nowMs) ?: return null
  return NbgGatewayInboxStore(context.applicationContext).record(message)
}

internal fun nbgGatewayInboxMessageFromShareIntent(
  intent: Intent?,
  nowMs: Long = System.currentTimeMillis(),
): NbgGatewayInboxMessage? {
  if (intent?.action != Intent.ACTION_SEND) return null
  val text = nbgGatewayShareText(intent).trim()
  if (text.isBlank()) return null
  return nbgGatewayInboxMessage(
    source = NbgInboundMessageSource.AndroidShareSheet,
    senderLabel = nbgGatewayShareSenderLabel(intent),
    text = text,
    canExecuteTools = false,
    nowMs = nowMs,
  )
}

private fun nbgGatewayShareText(intent: Intent): String =
  listOfNotNull(
    intent.getStringExtra(Intent.EXTRA_SUBJECT),
    intent.getStringExtra(Intent.EXTRA_TITLE),
    intent.getStringExtra(Intent.EXTRA_TEXT),
    intent.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)?.let { uri -> "Attachment: ${uri.toString().take(180)}" },
  )
    .map { nbgRedactDiagnosticText(it).trim() }
    .filter { it.isNotBlank() }
    .distinct()
    .joinToString("\n")
    .take(NBG_GATEWAY_MESSAGE_MAX_CHARS)

private fun nbgGatewayShareSenderLabel(intent: Intent): String =
  listOfNotNull(
    intent.`package`,
    intent.component?.packageName,
    intent.type,
  )
    .firstOrNull { it.isNotBlank() }
    ?.take(120)
    ?: "android-share"

private inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? =
  if (android.os.Build.VERSION.SDK_INT >= 33) {
    getParcelableExtra(name, T::class.java)
  } else {
    @Suppress("DEPRECATION")
    getParcelableExtra(name) as? T
  }

package com.nbg.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

internal const val NBG_GATEWAY_NOTIFICATION_REPLY_ACTION = "com.nbg.android.gateway.NOTIFICATION_REPLY"
internal const val NBG_GATEWAY_NOTIFICATION_REPLY_TEXT_KEY = "nbg_gateway_reply_text"
internal const val NBG_GATEWAY_NOTIFICATION_REPLY_SENDER_EXTRA = "nbg_gateway_reply_sender"

class NbgGatewayNotificationReplyReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    nbgRecordGatewayNotificationReply(context, intent)
  }
}

internal fun nbgRecordGatewayNotificationReply(
  context: Context,
  intent: Intent?,
  nowMs: Long = System.currentTimeMillis(),
): NbgGatewayInboxState? {
  val message = nbgGatewayInboxMessageFromNotificationReply(intent, nowMs) ?: return null
  return NbgGatewayInboxStore(context.applicationContext).record(message)
}

internal fun nbgGatewayInboxMessageFromNotificationReply(
  intent: Intent?,
  nowMs: Long = System.currentTimeMillis(),
): NbgGatewayInboxMessage? {
  if (intent?.action != NBG_GATEWAY_NOTIFICATION_REPLY_ACTION) return null
  val text = RemoteInput.getResultsFromIntent(intent)
    ?.getCharSequence(NBG_GATEWAY_NOTIFICATION_REPLY_TEXT_KEY)
    ?.toString()
    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
    ?: return null
  if (text.trim().isBlank()) return null
  return nbgGatewayInboxMessage(
    source = NbgInboundMessageSource.NotificationReply,
    senderLabel = intent.getStringExtra(NBG_GATEWAY_NOTIFICATION_REPLY_SENDER_EXTRA).orEmpty().ifBlank { "notification-reply" },
    text = text,
    canExecuteTools = false,
    nowMs = nowMs,
  )
}

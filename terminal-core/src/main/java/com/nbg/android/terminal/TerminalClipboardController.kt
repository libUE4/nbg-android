package com.nbg.android.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

interface TerminalClipboard {
  fun readText(): String?

  fun writeText(text: String)
}

class TerminalClipboardController(
  private val clipboard: TerminalClipboard,
  private val pasteToTerminal: (String) -> Unit,
) {
  fun copy(text: String) {
    clipboard.writeText(text)
  }

  fun paste() {
    val text = clipboard.readText() ?: return
    if (text.isEmpty()) return
    pasteToTerminal(text)
  }
}

class AndroidTerminalClipboard(
  private val context: Context,
) : TerminalClipboard {
  private val manager: ClipboardManager
    get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

  override fun readText(): String? {
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
  }

  override fun writeText(text: String) {
    manager.setPrimaryClip(ClipData.newPlainText("NBG Code", text))
  }
}

object NoOpTerminalClipboard : TerminalClipboard {
  override fun readText(): String? = null

  override fun writeText(text: String) = Unit
}

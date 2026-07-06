package com.nbg.android.mcpserver

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
  private lateinit var status: TextView
  private val clipboardClearHandler = Handler(Looper.getMainLooper())
  private var pendingClipboardToken: String? = null
  private var clipboardChangedAfterTokenCopy = false
  private var clipboardListenerRegistered = false
  private val tokenClipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
    clipboardChangedAfterTokenCopy = true
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    status = TextView(this).apply {
      textSize = 16f
      setTextColor(0xff1a1c16.toInt())
    }
    val startButton = Button(this).apply {
      text = "Start MCP Server"
      setOnClickListener {
        McpServerService.start(this@MainActivity)
        updateStatus()
      }
    }
    val stopButton = Button(this).apply {
      text = "Stop MCP Server"
      setOnClickListener {
        McpServerService.stop(this@MainActivity)
        updateStatus()
      }
    }
    val copyTokenButton = Button(this).apply {
      text = "Copy Bearer Token"
      setOnClickListener {
        copyBearerToken()
      }
    }
    val regenerateTokenButton = Button(this).apply {
      text = "Regenerate Bearer Token"
      setOnClickListener {
        regenerateBearerToken()
      }
    }
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(40, 40, 40, 40)
      addView(TextView(this@MainActivity).apply {
        text = "NBG Android MCP Server"
        textSize = 22f
        setTextColor(0xff1a1c16.toInt())
      })
      addView(status)
      addView(startButton)
      addView(stopButton)
      addView(copyTokenButton)
      addView(regenerateTokenButton)
    }
    setContentView(root)
    McpServerService.start(this)
    updateStatus()
  }

  override fun onResume() {
    super.onResume()
    updateStatus()
  }

  private fun copyBearerToken() {
    val token = McpServerService.bearerToken(this)
    val clipboard = getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("NBG MCP", token)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      clip.description.extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
      }
    }
    unregisterTokenClipboardListener(clipboard)
    clipboard.setPrimaryClip(clip)
    pendingClipboardToken = token
    clipboardChangedAfterTokenCopy = false
    clipboard.addPrimaryClipChangedListener(tokenClipboardListener)
    clipboardListenerRegistered = true
    clipboardClearHandler.removeCallbacksAndMessages(null)
    clipboardClearHandler.postDelayed({
      if (pendingClipboardToken == token && !clipboardChangedAfterTokenCopy) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          clipboard.clearPrimaryClip()
        } else {
          clipboard.setPrimaryClip(ClipData.newPlainText("NBG MCP", ""))
        }
      }
      if (pendingClipboardToken == token) {
        pendingClipboardToken = null
      }
      unregisterTokenClipboardListener(clipboard)
    }, CLIPBOARD_TOKEN_TTL_MS)
    updateStatus("Bearer token copied.")
  }

  private fun regenerateBearerToken() {
    McpServerService.regenerateBearerToken(this)
    updateStatus("Bearer token regenerated. Running clients must update the header.")
  }

  private fun updateStatus(note: String? = null) {
    val prefs = getSharedPreferences(McpServerService.PREFS, MODE_PRIVATE)
    val running = prefs.getBoolean(McpServerService.KEY_RUNNING, false)
    val port = prefs.getInt(McpServerService.KEY_PORT, McpServerService.MCP_PORT)
    val tokenPresent = prefs.getBoolean(McpServerService.KEY_BEARER_TOKEN_PRESENT, false) ||
      !prefs.getString(McpServerService.KEY_BEARER_TOKEN, null).isNullOrBlank()
    status.text = buildString {
      if (running) {
        append("Running\nhttp://127.0.0.1:$port/mcp")
      } else {
        append("Stopped\nhttp://127.0.0.1:${McpServerService.MCP_PORT}/mcp")
      }
      append("\nAuthorization: Bearer token required")
      append("\nToken present: ")
      append(if (tokenPresent) "yes" else "no")
      if (!note.isNullOrBlank()) {
        append("\n")
        append(note)
      }
    }
  }

  private fun unregisterTokenClipboardListener(clipboard: ClipboardManager = getSystemService(ClipboardManager::class.java)) {
    if (!clipboardListenerRegistered) return
    clipboard.removePrimaryClipChangedListener(tokenClipboardListener)
    clipboardListenerRegistered = false
    clipboardChangedAfterTokenCopy = false
  }

  override fun onDestroy() {
    clipboardClearHandler.removeCallbacksAndMessages(null)
    unregisterTokenClipboardListener()
    super.onDestroy()
  }

  companion object {
    private const val CLIPBOARD_TOKEN_TTL_MS = 30_000L
  }
}

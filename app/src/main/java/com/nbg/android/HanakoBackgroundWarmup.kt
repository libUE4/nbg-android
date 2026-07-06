package com.nbg.android

import android.content.Context
import android.util.Log
import com.nbg.android.terminal.TerminalEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

object HanakoBackgroundWarmup {
  private val launched = AtomicBoolean(false)

  fun start(context: Context) {
    if (!launched.compareAndSet(false, true)) return
    Thread({
      runCatching {
        val appContext = context.applicationContext
        val existing = findServerInfo(appContext)
        if (existing != null && HanakoApiClient().isHealthy(existing)) {
          Log.i("NBG_HANAKO", "early warmup reused healthy HanakoPro on ${existing.port}")
          return@runCatching
        }
        if (existing != null) {
          clearStaleHanakoServer(appContext, existing)
        }
        Log.i("NBG_HANAKO", "early warmup launching HanakoPro")
        HanakoServerLauncher().startIfNeeded(appContext)
      }.onFailure { error ->
        Log.w("NBG_HANAKO", "early warmup failed", error)
        launched.set(false)
      }
    }, "hanakopro-early-warmup").apply {
      isDaemon = true
      start()
    }
  }
}

package com.nbg.android.mcpserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class McpServerService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mainHandler = Handler(Looper.getMainLooper())
  private var server: AndroidMcpHttpServer? = null

  override fun onCreate() {
    super.onCreate()
    startForeground(NOTIFICATION_ID, notification("MCP server starting"))
    startServer()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    startServer()
    return START_STICKY
  }

  override fun onDestroy() {
    server?.stop()
    server = null
    markServerStopped(clearError = false)
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startServer() {
    if (server != null) return
    val bearerToken = ensureBearerToken(this)
    val current = AndroidMcpHttpServer(
      port = MCP_PORT,
      bearerTokenProvider = { ensureBearerToken(this) },
      onListening = { listeningPort ->
        markServerListening(listeningPort, bearerTokenPresent = bearerToken.isNotBlank())
      },
    )
    server = current
    scope.launch {
      runCatching { current.start() }
        .onFailure { error ->
          if (server === current) server = null
          markServerStopped(error.message)
          stopSelf()
        }
    }
    startForeground(NOTIFICATION_ID, notification("Starting http://127.0.0.1:$MCP_PORT/mcp with bearer auth"))
  }

  private fun markServerListening(port: Int, bearerTokenPresent: Boolean) {
    getSharedPreferences(PREFS, MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_RUNNING, true)
      .putInt(KEY_PORT, port)
      .putBoolean(KEY_BEARER_TOKEN_PRESENT, bearerTokenPresent)
      .remove(KEY_LAST_ERROR)
      .apply()
    mainHandler.post {
      startForeground(NOTIFICATION_ID, notification("Listening on http://127.0.0.1:$port/mcp with bearer auth"))
    }
  }

  private fun markServerStopped(error: String? = null, clearError: Boolean = true) {
    getSharedPreferences(PREFS, MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_RUNNING, false)
      .apply {
        if (!error.isNullOrBlank()) {
          putString(KEY_LAST_ERROR, error)
        } else if (clearError) {
          remove(KEY_LAST_ERROR)
        }
      }
      .apply()
  }

  private fun notification(text: String): Notification {
    val manager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "NBG MCP Server", NotificationManager.IMPORTANCE_LOW),
      )
    }
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
    }
    return builder
      .setSmallIcon(android.R.drawable.stat_sys_upload_done)
      .setContentTitle("NBG MCP Server")
      .setContentText(text)
      .setOngoing(true)
      .build()
  }

  companion object {
    const val MCP_PORT = NBG_ANDROID_MCP_PORT
    const val PREFS = "mcp_server"
    const val KEY_RUNNING = "running"
    const val KEY_PORT = "port"
    const val KEY_BEARER_TOKEN = "bearer_token"
    const val KEY_BEARER_TOKEN_PRESENT = "bearer_token_present"
    const val KEY_LAST_ERROR = "last_error"
    const val ACTION_STOP = "com.nbg.android.mcpserver.STOP"
    private const val CHANNEL_ID = "nbg_mcp_server"
    private const val NOTIFICATION_ID = 37666

    fun start(context: Context) {
      val intent = Intent(context, McpServerService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.startService(Intent(context, McpServerService::class.java).setAction(ACTION_STOP))
      context.getSharedPreferences(PREFS, MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_RUNNING, false)
        .apply()
    }

    fun bearerToken(context: Context): String =
      ensureBearerToken(context)

    fun regenerateBearerToken(context: Context): String {
      val token = nbgGenerateAndroidMcpBearerToken()
      context.getSharedPreferences(PREFS, MODE_PRIVATE)
        .edit()
        .putString(KEY_BEARER_TOKEN, token)
        .putBoolean(KEY_BEARER_TOKEN_PRESENT, true)
        .apply()
      return token
    }

    private fun ensureBearerToken(context: Context): String {
      val prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE)
      val existing = prefs.getString(KEY_BEARER_TOKEN, null)
        ?.takeIf { it.length >= NBG_ANDROID_MCP_BEARER_TOKEN_MIN_LENGTH }
      if (existing != null) {
        prefs.edit().putBoolean(KEY_BEARER_TOKEN_PRESENT, true).apply()
        return existing
      }
      return regenerateBearerToken(context)
    }
  }
}

private fun nbgGenerateAndroidMcpBearerToken(random: SecureRandom = SecureRandom()): String {
  val bytes = ByteArray(NBG_ANDROID_MCP_BEARER_TOKEN_BYTES)
  random.nextBytes(bytes)
  return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

internal class AndroidMcpHttpServer(
  private val port: Int,
  private val bearerTokenProvider: () -> String,
  private val onListening: (Int) -> Unit = {},
) {
  private val sessions = ConcurrentHashMap<String, AndroidMcpSseSession>()
  @Volatile private var stopped = false
  @Volatile internal var boundPort: Int = 0
    private set
  private var socket: ServerSocket? = null

  fun start() {
    ServerSocket(port, 50, InetAddress.getByName(NBG_ANDROID_MCP_LOOPBACK_HOST)).use { serverSocket ->
      socket = serverSocket
      boundPort = serverSocket.localPort
      onListening(boundPort)
      while (!stopped) {
        val client = try {
          serverSocket.accept()
        } catch (error: Exception) {
          if (stopped) break
          throw error
        }
        Thread { handle(client) }.start()
      }
    }
  }

  fun stop() {
    stopped = true
    sessions.values.forEach { it.close() }
    sessions.clear()
    runCatching { socket?.close() }
  }

  private fun handle(socket: Socket) {
    socket.use { client ->
      val input = client.getInputStream().bufferedReader(StandardCharsets.UTF_8)
      val requestLine = input.readLine().orEmpty()
      if (requestLine.isBlank()) return
      val headers = readHeaders(input)
      val parts = requestLine.split(" ")
      val method = parts.getOrNull(0).orEmpty()
      val rawPath = parts.getOrNull(1).orEmpty()
      val path = rawPath.substringBefore("?")
      if (path != "/mcp") {
        writeJson(client, 404, JSONObject().put("error", "not found"))
        return
      }
      if (!nbgAndroidMcpRequestAuthorized(headers, bearerTokenProvider())) {
        writeUnauthorized(client)
        return
      }
      when (method) {
        "GET" -> handleSse(client, rawPath)
        "POST" -> handlePost(client, rawPath, headers, input)
        "DELETE" -> writeNoContent(client)
        else -> writeJson(client, 405, JSONObject().put("error", "method not allowed"))
      }
    }
  }

  private fun handleSse(socket: Socket, rawPath: String) {
    val sessionId = UUID.randomUUID().toString()
    val endpoint = "/mcp?sessionId=$sessionId"
    val session = AndroidMcpSseSession(socket)
    sessions[sessionId] = session
    session.writeHeaders()
    session.sendEvent("endpoint", endpoint)
    while (!stopped && session.open) {
      Thread.sleep(1_000)
      session.keepAlive()
    }
  }

  private fun handlePost(socket: Socket, rawPath: String, headers: Map<String, String>, input: BufferedReader) {
    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
    val body = CharArray(contentLength).let { buffer ->
      var offset = 0
      while (offset < buffer.size) {
        val read = input.read(buffer, offset, buffer.size - offset)
        if (read <= 0) break
        offset += read
      }
      String(buffer, 0, offset)
    }
    val message = JSONObject(body.ifBlank { "{}" })
    val response = handleRpc(message)
    val sessionId = rawPath.substringAfter("sessionId=", "").takeIf { it.isNotBlank() }?.let {
      URLDecoder.decode(it.substringBefore("&"), Charsets.UTF_8.name())
    }
    val session = sessionId?.let { sessions[it] }
    if (session != null && message.has("id")) {
      session.sendJson(response)
      writeAccepted(socket)
    } else if (message.has("id")) {
      writeJson(socket, 200, response)
    } else {
      writeAccepted(socket)
    }
  }

  private fun handleRpc(message: JSONObject): JSONObject {
    val id = message.opt("id")
    val method = message.optString("method")
    val result = when (method) {
      "initialize" -> JSONObject()
        .put("protocolVersion", NBG_ANDROID_MCP_PROTOCOL_VERSION)
        .put("capabilities", JSONObject().put("tools", JSONObject()))
        .put(
          "serverInfo",
          JSONObject()
            .put("name", NBG_ANDROID_MCP_SERVER_NAME)
            .put("version", NBG_ANDROID_MCP_SERVER_VERSION)
            .put("contractVersion", NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION),
        )
      "tools/list" -> JSONObject().put("tools", toolsJson())
      "tools/call" -> callTool(message.optJSONObject("params") ?: JSONObject())
      else -> null
    }
    return if (result != null) {
      JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)
    } else {
      JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", id)
        .put("error", JSONObject().put("code", -32601).put("message", "Method not found: $method"))
    }
  }

  private fun toolsJson(): JSONArray =
    JSONArray().also { tools ->
      NBG_ANDROID_MCP_TOOLS.forEach { contract ->
        tools.put(
          JSONObject()
            .put("name", contract.name)
            .put("title", contract.title)
            .put("description", contract.description)
            .put("boundary", contract.boundary.name)
            .put("inputSchema", toolInputSchema(contract.name)),
        )
      }
    }

  private fun toolInputSchema(name: String): JSONObject =
    when (name) {
      "android_echo" -> JSONObject()
        .put("type", "object")
        .put(
          "properties",
          JSONObject().put(
            "text",
            JSONObject().put("type", "string").put("description", "Text to echo"),
          ),
        )
        .put("required", JSONArray().put("text"))
        .put("additionalProperties", false)
      else -> JSONObject()
        .put("type", "object")
        .put("properties", JSONObject())
        .put("additionalProperties", false)
    }

  private fun callTool(params: JSONObject): JSONObject {
    val name = params.optString("name")
    val args = params.optJSONObject("arguments") ?: JSONObject()
    if (name !in nbgAndroidMcpToolNames()) {
      return JSONObject()
        .put("isError", true)
        .put(
          "content",
          JSONArray().put(JSONObject().put("type", "text").put("text", "Unknown tool: $name")),
        )
    }
    val text = when (name) {
      "android_device_info" -> listOf(
        "package=com.nbg.android.mcpserver",
        "manufacturer=${android.os.Build.MANUFACTURER}",
        "model=${android.os.Build.MODEL}",
        "sdk=${Build.VERSION.SDK_INT}",
        "port=$port",
      ).joinToString("\n")
      "android_echo" -> "Android MCP echo: ${args.optString("text")}"
      else -> "Unknown tool: $name"
    }
    return JSONObject().put(
      "content",
      JSONArray().put(JSONObject().put("type", "text").put("text", text)),
    )
  }

  private fun readHeaders(input: BufferedReader): Map<String, String> {
    val headers = linkedMapOf<String, String>()
    while (true) {
      val line = input.readLine() ?: break
      if (line.isEmpty()) break
      val index = line.indexOf(':')
      if (index > 0) headers[line.substring(0, index).trim().lowercase()] = line.substring(index + 1).trim()
    }
    return headers
  }
}

private class AndroidMcpSseSession(private val socket: Socket) {
  private val writer: BufferedWriter = socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8)
  @Volatile var open: Boolean = true
    private set

  fun writeHeaders() {
    writer.write("HTTP/1.1 200 OK\r\n")
    writer.write("Content-Type: text/event-stream\r\n")
    writer.write("Cache-Control: no-cache, no-transform\r\n")
    writer.write("Connection: keep-alive\r\n")
    writer.write("\r\n")
    writer.flush()
  }

  fun sendEvent(event: String, data: String) {
    runCatching {
      writer.write("event: $event\n")
      writer.write("data: $data\n\n")
      writer.flush()
    }.onFailure { close() }
  }

  fun sendJson(json: JSONObject) {
    sendEvent("message", json.toString())
  }

  fun keepAlive() {
    runCatching {
      writer.write(": keep-alive\n\n")
      writer.flush()
    }.onFailure { close() }
  }

  fun close() {
    open = false
    runCatching { socket.close() }
  }
}

private fun writeJson(socket: Socket, status: Int, body: JSONObject) {
  val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
  socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
    writer.write("HTTP/1.1 $status ${statusText(status)}\r\n")
    writer.write("Content-Type: application/json; charset=utf-8\r\n")
    writer.write("Content-Length: ${bytes.size}\r\n")
    writer.write("Connection: close\r\n")
    writer.write("\r\n")
    writer.flush()
    socket.getOutputStream().write(bytes)
    socket.getOutputStream().flush()
  }
}

private fun writeAccepted(socket: Socket) {
  socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
    writer.write("HTTP/1.1 202 Accepted\r\n")
    writer.write("Content-Length: 0\r\n")
    writer.write("Connection: close\r\n")
    writer.write("\r\n")
  }
}

private fun writeNoContent(socket: Socket) {
  socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
    writer.write("HTTP/1.1 204 No Content\r\n")
    writer.write("Content-Length: 0\r\n")
    writer.write("Connection: close\r\n")
    writer.write("\r\n")
  }
}

private fun writeUnauthorized(socket: Socket) {
  val bytes = JSONObject().put("error", "unauthorized").toString().toByteArray(StandardCharsets.UTF_8)
  socket.getOutputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer ->
    writer.write("HTTP/1.1 401 Unauthorized\r\n")
    writer.write("Content-Type: application/json; charset=utf-8\r\n")
    writer.write("WWW-Authenticate: Bearer realm=\"NBG Android MCP\"\r\n")
    writer.write("Content-Length: ${bytes.size}\r\n")
    writer.write("Connection: close\r\n")
    writer.write("\r\n")
    writer.flush()
    socket.getOutputStream().write(bytes)
    socket.getOutputStream().flush()
  }
}

private fun statusText(status: Int): String =
  when (status) {
    401 -> "Unauthorized"
    200 -> "OK"
    202 -> "Accepted"
    204 -> "No Content"
    404 -> "Not Found"
    405 -> "Method Not Allowed"
    else -> "Error"
  }

package com.nbg.android

import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class NbgFtpFileServer(
  private val fileSystem: NbgFileShareFileSystem,
  private val credentials: NbgFileShareCredentials,
  private val bindAddress: InetAddress = InetAddress.getByName("127.0.0.1"),
  private val preferredPort: Int = 2121,
  val accessMode: NbgFileShareAccessMode = NbgFileShareAccessMode.ReadWrite,
) : Closeable {
  private companion object {
    const val PASSIVE_DATA_ACCEPT_TIMEOUT_MS = 3_000
  }

  @Volatile
  private var running = false
  private var socket: ServerSocket? = null
  private var worker: Thread? = null
  private val clientSockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()
  private val passiveSockets: MutableSet<ServerSocket> = ConcurrentHashMap.newKeySet()

  val isRunning: Boolean
    get() = running && socket?.isClosed == false

  val port: Int?
    get() = socket?.localPort?.takeIf { it > 0 }

  @Synchronized
  fun start() {
    if (isRunning) return
    fileSystem.prepare()
    val bound = bind(preferredPort)
    socket = bound
    running = true
    worker = thread(name = "nbg-ftp-file-server", isDaemon = true) {
      serve(bound)
    }
  }

  override fun close() {
    running = false
    runCatching { socket?.close() }
    runCatching {
      clientSockets.forEach { client ->
        runCatching { client.close() }
      }
    }
    runCatching {
      passiveSockets.forEach { passive ->
        runCatching { passive.close() }
      }
    }
    clientSockets.clear()
    passiveSockets.clear()
    socket = null
  }

  private fun bind(port: Int): ServerSocket {
    val requestedPort = port.coerceAtLeast(0)
    return runCatching { bindOnce(requestedPort) }
      .getOrElse { error ->
        if (requestedPort == 0) throw error
        bindOnce(0)
      }
  }

  private fun bindOnce(port: Int): ServerSocket =
    ServerSocket().apply {
      reuseAddress = true
      bind(InetSocketAddress(bindAddress, port))
    }

  private fun serve(serverSocket: ServerSocket) {
    while (running && !serverSocket.isClosed) {
      val client = runCatching { serverSocket.accept() }.getOrNull() ?: continue
      clientSockets.add(client)
      thread(name = "nbg-ftp-client", isDaemon = true) {
        try {
          client.use { socket -> NbgFtpSession(socket).handle() }
        } finally {
          clientSockets.remove(client)
        }
      }
    }
  }

  private inner class NbgFtpSession(
    private val controlSocket: Socket,
  ) {
    private val reader = BufferedReader(InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8))
    private val writer = PrintWriter(controlSocket.getOutputStream().writer(StandardCharsets.UTF_8), true)
    private var username: String = ""
    private var authenticated = false
    private var currentPath = "/"
    private var passiveSocket: ServerSocket? = null
    private var renameFrom: String? = null
    private var binaryMode = true

    fun handle() {
      reply(220, "NBG Ubuntu root FTP ready")
      while (running && !controlSocket.isClosed) {
        val line = reader.readLine() ?: break
        val command = line.substringBefore(' ').uppercase(Locale.US)
        val argument = line.substringAfter(' ', missingDelimiterValue = "").trim()
        runCatching { route(command, argument) }.onFailure { error ->
          if (error is NbgFtpReplyException) {
            reply(error.code, error.message.orEmpty())
          } else {
            reply(550, error.message ?: "Command failed")
          }
        }
        if (command == "QUIT") break
      }
      closePassive()
    }

    private fun route(command: String, argument: String) {
      when (command) {
        "USER" -> {
          username = argument
          authenticated = false
          reply(331, "Password required")
        }
        "PASS" -> {
          authenticated = username == credentials.username && argument == credentials.password
          if (authenticated) reply(230, "Logged in") else reply(530, "Authentication failed")
        }
        "SYST" -> reply(215, "UNIX Type: L8")
        "FEAT" -> multiline(211, listOf("UTF8", "MLST type*;size*;modify*;", "PASV", "EPSV"), "End")
        "OPTS" -> reply(200, "OK")
        "NOOP" -> reply(200, "OK")
        "TYPE" -> {
          binaryMode = argument.uppercase(Locale.US) != "A"
          reply(200, if (binaryMode) "Binary mode" else "ASCII mode")
        }
        "PWD", "XPWD" -> {
          ensureAuthenticated()
          reply(257, "\"$currentPath\" is current directory")
        }
        "CWD" -> cwd(argument)
        "CDUP" -> cwd("..")
        "PASV" -> enterPassive(epsv = false)
        "EPSV" -> enterPassive(epsv = true)
        "LIST" -> list(argument, namesOnly = false, machine = false)
        "NLST" -> list(argument, namesOnly = true, machine = false)
        "MLSD" -> list(argument, namesOnly = false, machine = true)
        "SIZE" -> size(argument)
        "MDTM" -> modifiedTime(argument)
        "RETR" -> retrieve(argument)
        "STOR" -> store(argument, append = false)
        "APPE" -> store(argument, append = true)
        "DELE" -> delete(argument, directory = false)
        "RMD", "XRMD" -> delete(argument, directory = true)
        "MKD", "XMKD" -> makeDirectory(argument)
        "RNFR" -> renameFrom(argument)
        "RNTO" -> renameTo(argument)
        "QUIT" -> reply(221, "Bye")
        "PORT", "EPRT" -> reply(502, "Active mode is not supported; use PASV")
        else -> reply(502, "Command not implemented: $command")
      }
    }

    private fun cwd(argument: String) {
      ensureAuthenticated()
      val target = entryFor(argument.ifBlank { "/" })
      if (!target.file.isDirectory) {
        reply(550, "Not a directory")
        return
      }
      currentPath = target.virtualPath
      reply(250, "Directory changed to $currentPath")
    }

    private fun enterPassive(epsv: Boolean) {
      ensureAuthenticated()
      closePassive()
      val passive = ServerSocket().apply {
        reuseAddress = true
        soTimeout = PASSIVE_DATA_ACCEPT_TIMEOUT_MS
        bind(InetSocketAddress(bindAddress, 0))
      }
      passiveSocket = passive
      passiveSockets.add(passive)
      val passivePort = passive.localPort
      if (epsv) {
        replyRaw("229 Entering Extended Passive Mode (|||$passivePort|)")
      } else {
        val address = controlSocket.localAddress.hostAddress.orEmpty()
          .takeIf { it.isNotBlank() && it != "0.0.0.0" }
          ?: "127.0.0.1"
        val parts = address.split('.').mapNotNull { it.toIntOrNull() }
          .takeIf { it.size == 4 }
          ?: listOf(127, 0, 0, 1)
        replyRaw("227 Entering Passive Mode (${parts.joinToString(",")},${passivePort / 256},${passivePort % 256})")
      }
    }

    private fun list(argument: String, namesOnly: Boolean, machine: Boolean) {
      ensureAuthenticated()
      val target = entryFor(argument.ifBlank { currentPath })
      val entries = if (target.file.isDirectory) {
        fileSystem.children(target.virtualPath)
      } else {
        listOf(target)
      }
      withDataSocket("Opening data connection") { output ->
        entries.forEach { entry ->
          val line = when {
            machine -> machineListLine(entry)
            namesOnly -> entry.name
            else -> unixListLine(entry)
          }
          output.write((line + "\r\n").toByteArray(StandardCharsets.UTF_8))
        }
      }
    }

    private fun size(argument: String) {
      ensureAuthenticated()
      val target = entryFor(argument)
      if (!target.file.isFile) {
        reply(550, "Not a file")
        return
      }
      reply(213, target.file.length().toString())
    }

    private fun modifiedTime(argument: String) {
      ensureAuthenticated()
      val target = entryFor(argument)
      if (!target.file.exists()) {
        reply(550, "Not found")
        return
      }
      reply(213, ftpTimestamp(target.file.lastModified()))
    }

    private fun retrieve(argument: String) {
      ensureAuthenticated()
      val target = entryFor(argument)
      if (!target.file.isFile) {
        reply(550, "Not a file")
        return
      }
      withDataSocket("Opening data connection") { output ->
        target.file.inputStream().use { input -> input.copyTo(output) }
      }
    }

    private fun store(argument: String, append: Boolean) {
      ensureAuthenticated()
      ensureWriteAllowed()
      val target = resolvedFor(argument)
      if (target.file.isDirectory) {
        reply(550, "Target is a directory")
        return
      }
      val passive = passiveSocket
      if (passive == null || passive.isClosed) {
        reply(425, "Use PASV first")
        return
      }
      reply(150, "Opening data connection")
      val data = acceptPassiveDataSocket(passive) ?: return
      target.file.parentFile?.mkdirs()
      data.use { socket ->
        FileOutputStream(target.file, append).use { output ->
          socket.getInputStream().use { input -> input.copyTo(output) }
        }
      }
      reply(226, "Transfer complete")
    }

    private fun delete(argument: String, directory: Boolean) {
      ensureAuthenticated()
      ensureWriteAllowed()
      val target = resolvedFor(argument)
      if (target.virtualPath.isProtectedVirtualRoot()) {
        reply(550, "Cannot delete protected FTP root")
        return
      }
      if (!target.existsNoFollow()) {
        reply(550, "Not found")
        return
      }
      if (directory && !target.isDirectoryNoFollow()) {
        reply(550, "Not a directory")
        return
      }
      val ok = if (target.isDirectoryNoFollow()) {
        if (!directory) {
          reply(550, "Is a directory")
          return
        }
        target.hostFile.deleteRecursively()
      } else {
        Files.deleteIfExists(target.hostFile.toPath())
      }
      if (ok) reply(250, "Deleted") else reply(550, "Delete failed")
    }

    private fun makeDirectory(argument: String) {
      ensureAuthenticated()
      ensureWriteAllowed()
      val target = resolvedFor(argument)
      if (target.file.exists()) {
        reply(550, "Already exists")
        return
      }
      if (target.file.mkdirs()) reply(257, "\"${target.virtualPath}\" created") else reply(550, "Create directory failed")
    }

    private fun renameFrom(argument: String) {
      ensureAuthenticated()
      ensureWriteAllowed()
      val target = resolvedFor(argument)
      if (target.virtualPath.isProtectedVirtualRoot() || !target.existsNoFollow()) {
        reply(550, "Not found")
        return
      }
      renameFrom = target.virtualPath
      reply(350, "Ready for RNTO")
    }

    private fun renameTo(argument: String) {
      ensureAuthenticated()
      ensureWriteAllowed()
      val fromPath = renameFrom
      if (fromPath.isNullOrBlank()) {
        reply(503, "RNFR required")
        return
      }
      val source = resolvedFor(fromPath)
      val target = resolvedFor(argument)
      val sourceFile = source.file.canonicalFile
      val targetFile = target.file.canonicalFile
      if (!source.existsNoFollow()) {
        renameFrom = null
        reply(550, "Not found")
        return
      }
      if (target.virtualPath.isProtectedVirtualRoot()) {
        reply(550, "Cannot replace protected FTP root")
        return
      }
      if (sourceFile == targetFile) {
        reply(250, "Renamed")
        return
      }
      if (sourceFile.isDirectory && targetFile.isInsideOrSame(sourceFile)) {
        reply(550, "Cannot move a directory into itself")
        return
      }
      if (target.existsNoFollow() && !target.deleteNoFollow()) {
        reply(550, "Replace target failed")
        return
      }
      target.hostFile.parentFile?.mkdirs()
      val moved = source.hostFile.renameTo(target.hostFile) || runCatching {
        if (source.isDirectoryNoFollow()) {
          source.hostFile.copyRecursively(target.hostFile, overwrite = true)
        } else {
          source.hostFile.copyTo(target.hostFile, overwrite = true)
        }
        source.deleteNoFollow()
        true
      }.getOrDefault(false)
      if (moved) {
        renameFrom = null
        reply(250, "Renamed")
      } else {
        reply(550, "Rename failed")
      }
    }

    private fun withDataSocket(openMessage: String, block: (OutputStream) -> Unit) {
      val passive = passiveSocket
      if (passive == null || passive.isClosed) {
        reply(425, "Use PASV first")
        return
      }
      reply(150, openMessage)
      val data = acceptPassiveDataSocket(passive) ?: return
      data.use { socket ->
        val output = socket.getOutputStream()
        block(output)
        output.flush()
      }
      reply(226, "Transfer complete")
    }

    private fun acceptPassiveDataSocket(passive: ServerSocket): Socket? =
      try {
        val data = passive.accept()
        if (running) {
          data
        } else {
          runCatching { data.close() }
          null
        }
      } catch (_: SocketTimeoutException) {
        reply(425, "Data connection timed out")
        null
      } catch (_: SocketException) {
        if (running) {
          reply(425, "Data connection failed")
        }
        null
      } finally {
        closePassive()
      }

    private fun resolvedFor(argument: String): NbgResolvedFileSharePath =
      fileSystem.resolve(resolveFtpPath(argument))

    private fun entryFor(argument: String): NbgFileShareEntry =
      fileSystem.entry(resolveFtpPath(argument))

    private fun resolveFtpPath(argument: String): String {
      val raw = argument.trim().substringBeforeLastIfListFlag()
      val path = when {
        raw.isBlank() -> currentPath
        raw.startsWith("/") -> raw
        currentPath == "/" -> "/$raw"
        else -> "$currentPath/$raw"
      }
      return nbgNormalizeFtpPath(path)
    }

    private fun ensureAuthenticated() {
      if (!authenticated) throw NbgFtpReplyException(530, "Not logged in")
    }

    private fun ensureWriteAllowed() {
      if (accessMode.allowsWrites) return
      closePassive()
      renameFrom = null
      throw NbgFtpReplyException(550, "FTP server is read-only")
    }

    private fun closePassive() {
      val passive = passiveSocket
      passiveSocket = null
      if (passive != null) {
        passiveSockets.remove(passive)
        runCatching { passive.close() }
      }
    }

    private fun reply(code: Int, message: String) {
      val safeMessage = message.removePrefix("$code ")
      replyRaw("$code $safeMessage")
    }

    private fun multiline(code: Int, lines: List<String>, end: String) {
      replyRaw("$code-Features")
      lines.forEach { replyRaw(" $it") }
      replyRaw("$code $end")
    }

    private fun replyRaw(value: String) {
      writer.print(value + "\r\n")
      writer.flush()
    }
  }
}

private class NbgFtpReplyException(
  val code: Int,
  message: String,
) : RuntimeException(message)

private fun String.substringBeforeLastIfListFlag(): String {
  val value = trim()
  if (!value.startsWith("-")) return value
  val parts = value.split(Regex("\\s+"), limit = 2)
  return parts.getOrNull(1).orEmpty()
}

private fun nbgNormalizeFtpPath(rawPath: String): String {
  val segments = ArrayDeque<String>()
  rawPath.split('/').forEach { part ->
    when {
      part.isBlank() || part == "." -> Unit
      part == ".." -> {
        if (segments.isNotEmpty()) {
          segments.removeLast()
        }
      }
      else -> segments.addLast(part)
    }
  }
  return "/" + segments.joinToString("/")
}

private fun String.isProtectedVirtualRoot(): Boolean =
  this == "/" || this == "/nbg-uploads"

private fun NbgResolvedFileSharePath.existsNoFollow(): Boolean =
  Files.exists(hostFile.toPath(), LinkOption.NOFOLLOW_LINKS)

private fun NbgResolvedFileSharePath.isDirectoryNoFollow(): Boolean =
  Files.isDirectory(hostFile.toPath(), LinkOption.NOFOLLOW_LINKS)

private fun NbgResolvedFileSharePath.deleteNoFollow(): Boolean =
  when {
    Files.isSymbolicLink(hostFile.toPath()) -> Files.deleteIfExists(hostFile.toPath())
    isDirectoryNoFollow() -> hostFile.deleteRecursively()
    else -> Files.deleteIfExists(hostFile.toPath())
  }

private fun File.isInsideOrSame(base: File): Boolean {
  val canonicalBase = base.canonicalFile
  var current: File? = canonicalFile
  while (current != null) {
    if (current == canonicalBase) return true
    current = current.parentFile
  }
  return false
}

private fun unixListLine(entry: NbgFileShareEntry): String {
  val type = if (entry.file.isDirectory) 'd' else '-'
  val size = if (entry.file.isFile) entry.file.length() else 0L
  val modified = unixListDate(entry.file.lastModified())
  return "$type${if (entry.file.canRead()) "r" else "-"}${if (entry.file.canWrite()) "w" else "-"}x------ 1 root root ${size.toString().padStart(12)} $modified ${entry.name}"
}

private fun machineListLine(entry: NbgFileShareEntry): String {
  val type = if (entry.file.isDirectory) "dir" else "file"
  val size = if (entry.file.isFile) entry.file.length() else 0L
  return "type=$type;size=$size;modify=${ftpTimestamp(entry.file.lastModified())}; ${entry.name}"
}

private fun unixListDate(lastModified: Long): String =
  SimpleDateFormat("MMM dd HH:mm", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
  }.format(lastModified)

private fun ftpTimestamp(lastModified: Long): String =
  SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
  }.format(lastModified)

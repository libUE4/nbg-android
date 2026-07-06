package com.nbg.android

import android.content.Context
import java.io.File
import java.net.InetAddress
import java.security.SecureRandom
import java.util.Locale

data class NbgFileShareCredentials(
  val username: String,
  val password: String,
)

enum class NbgFileShareAccessMode(
  val wireValue: String,
  val label: String,
) {
  ReadOnly("read_only", "只读"),
  ReadWrite("read_write", "读写");

  val allowsWrites: Boolean
    get() = this == ReadWrite
}

data class NbgFileShareServerState(
  val running: Boolean,
  val port: Int? = null,
  val localUrl: String = "",
  val protocol: String = "FTP",
  val accessMode: NbgFileShareAccessMode = NbgFileShareAccessMode.ReadOnly,
  val username: String = "nbg",
  val password: String = "",
  val ubuntuRoot: String = NBG_UBUNTU_ROOT_HOME,
  val hostRoot: String = "",
  val message: String = "",
)

object NbgFileShareServerRegistry {
  private const val DEFAULT_PORT = 43211
  private const val PREFS = "nbg_file_share_server"
  private const val PASSWORD_KEY = "password"
  private const val SECRET_PREFS = "nbg_file_share_secrets"
  private const val SECRET_ALIAS = "nbg_file_share_password_v1"
  private const val SECRET_PREFIX = "ftp_"
  private const val USERNAME = "nbg"

  private var server: NbgFtpFileServer? = null
  private var lastError: String = ""

  @Synchronized
  fun start(
    context: Context,
    accessMode: NbgFileShareAccessMode = NbgFileShareAccessMode.ReadOnly,
  ): NbgFileShareServerState {
    val appContext = context.applicationContext
    server?.takeIf { it.isRunning && it.accessMode == accessMode }?.let { return snapshot(appContext) }
    server?.close()
    server = null
    val roots = nbgUbuntuRootFileSystem(appContext)
    roots.prepare()
    val credentials = NbgFileShareCredentials(USERNAME, loadOrCreatePassword(appContext))
    val next = NbgFtpFileServer(
      fileSystem = roots,
      credentials = credentials,
      bindAddress = InetAddress.getByName("127.0.0.1"),
      preferredPort = DEFAULT_PORT,
      accessMode = accessMode,
    )
    return runCatching {
      next.start()
      server = next
      lastError = ""
      snapshot(appContext)
    }.getOrElse { error ->
      runCatching { next.close() }
      lastError = error.message ?: "启动失败"
      snapshot(appContext)
    }
  }

  @Synchronized
  fun stop(context: Context): NbgFileShareServerState {
    server?.close()
    server = null
    lastError = ""
    return snapshot(context.applicationContext)
  }

  @Synchronized
  fun snapshot(context: Context): NbgFileShareServerState {
    val appContext = context.applicationContext
    val current = server?.takeIf { it.isRunning }
    val password = loadOrCreatePassword(appContext)
    val hostRoot = nbgUbuntuRootHomeHostDir(appContext).absolutePath
    return NbgFileShareServerState(
      running = current != null,
      port = current?.port ?: DEFAULT_PORT,
      localUrl = "127.0.0.1:${current?.port ?: DEFAULT_PORT}",
      protocol = "FTP",
      accessMode = current?.accessMode ?: NbgFileShareAccessMode.ReadOnly,
      username = USERNAME,
      password = password,
      hostRoot = hostRoot,
      message = lastError,
    )
  }

  @Synchronized
  fun diagnosticSnapshot(context: Context): NbgFileShareServerState? {
    val appContext = context.applicationContext
    val current = server?.takeIf { it.isRunning }
    val hostRoot = nbgUbuntuRootHomeHostDir(appContext).absolutePath
    val hasExistingPassword = hasStoredPassword(appContext)
    if (current == null && lastError.isBlank() && !hasExistingPassword) return null
    return NbgFileShareServerState(
      running = current != null,
      port = current?.port ?: DEFAULT_PORT,
      localUrl = if (current != null) "127.0.0.1:${current.port}" else "",
      protocol = "FTP",
      accessMode = current?.accessMode ?: NbgFileShareAccessMode.ReadOnly,
      username = USERNAME,
      password = if (hasExistingPassword) "[configured]" else "",
      hostRoot = hostRoot,
      message = lastError,
    )
  }

  private fun loadOrCreatePassword(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val secrets = NbgEncryptedPreferenceSecretStore(
      context = context,
      prefsName = SECRET_PREFS,
      keyAlias = SECRET_ALIAS,
      keyPrefix = SECRET_PREFIX,
    )
    runCatching { secrets.loadSecret(PASSWORD_KEY) }.getOrNull()?.let { return it }
    prefs.getString(PASSWORD_KEY, null)?.trim()?.takeIf { it.isNotBlank() }?.let { legacyPassword ->
      runCatching {
        secrets.saveSecret(PASSWORD_KEY, legacyPassword)
        prefs.edit().remove(PASSWORD_KEY).apply()
      }
      return legacyPassword
    }
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    val random = SecureRandom()
    val password = buildString {
      repeat(12) {
        append(alphabet[random.nextInt(alphabet.length)])
      }
    }
    runCatching {
      secrets.saveSecret(PASSWORD_KEY, password)
      prefs.edit().remove(PASSWORD_KEY).apply()
    }
    return password
  }

  private fun hasStoredPassword(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val secrets = NbgEncryptedPreferenceSecretStore(
      context = context,
      prefsName = SECRET_PREFS,
      keyAlias = SECRET_ALIAS,
      keyPrefix = SECRET_PREFIX,
    )
    if (runCatching { secrets.loadSecret(PASSWORD_KEY).orEmpty().isNotBlank() }.getOrDefault(false)) return true
    return prefs.getString(PASSWORD_KEY, null)?.trim()?.isNotBlank() == true
  }
}

fun nbgUbuntuRootfsHostDir(context: Context): File =
  File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu")

fun nbgUbuntuRootHomeHostDir(context: Context): File =
  File(nbgUbuntuRootfsHostDir(context), "root")

fun nbgUbuntuUploadsHostDir(context: Context): File =
  File(context.filesDir, "nbg-uploads")

fun nbgUbuntuRootFileSystem(context: Context): NbgFileShareFileSystem =
  NbgFileShareFileSystem(
    rootHomeDir = nbgUbuntuRootHomeHostDir(context),
    uploadsDir = nbgUbuntuUploadsHostDir(context),
  )

class NbgFileShareFileSystem(
  private val rootHomeDir: File,
  private val uploadsDir: File,
) {
  private val ubuntuRoot = NBG_UBUNTU_ROOT_HOME.trimEnd('/')

  fun prepare() {
    rootHomeDir.mkdirs()
    uploadsDir.mkdirs()
    File(rootHomeDir, "outputs").mkdirs()
    File(rootHomeDir, "backups").mkdirs()
    File(rootHomeDir, "tmp").mkdirs()
    File(rootHomeDir, "build-workspaces").mkdirs()
  }

  fun resolve(rawPath: String): NbgResolvedFileSharePath {
    val path = normalizeVirtualPath(rawPath)
    val segments = path.trim('/').split('/').filter { it.isNotBlank() }
    val base = if (segments.firstOrNull() == "nbg-uploads") uploadsDir else rootHomeDir
    val relativeSegments = if (segments.firstOrNull() == "nbg-uploads") segments.drop(1) else segments
    val file = relativeSegments.fold(base) { current, segment -> File(current, segment) }.absoluteFile
    val canonicalBase = base.canonicalFile
    val canonicalFile = file.canonicalFile
    if (!canonicalFile.isInsideOrSame(canonicalBase)) {
      throw SecurityException("Path escapes Ubuntu root: $path")
    }
    return NbgResolvedFileSharePath(
      virtualPath = path,
      ubuntuPath = ubuntuPathFor(path),
      file = canonicalFile,
      hostFile = file,
    )
  }

  fun entry(rawPath: String): NbgFileShareEntry {
    val resolved = resolve(rawPath)
    val name = resolved.virtualPath.trim('/').substringAfterLast('/')
      .ifBlank { "root" }
    return NbgFileShareEntry(
      name = name,
      virtualPath = resolved.virtualPath,
      ubuntuPath = resolved.ubuntuPath,
      file = resolved.file,
    )
  }

  fun children(rawPath: String): List<NbgFileShareEntry> {
    val resolved = resolve(rawPath)
    if (!resolved.file.isDirectory) return emptyList()
    if (resolved.virtualPath == "/") {
      val canonicalRoot = rootHomeDir.canonicalFile
      val rootEntries = rootHomeDir.listFiles().orEmpty()
        .filter { it.name != "nbg-uploads" }
        .mapNotNull {
          val canonicalFile = it.canonicalFileInside(canonicalRoot) ?: return@mapNotNull null
          NbgFileShareEntry(
            name = it.name,
            virtualPath = "/${it.name}",
            ubuntuPath = "$ubuntuRoot/${it.name}",
            file = canonicalFile,
          )
        }
      val uploadsEntry = NbgFileShareEntry(
        name = "nbg-uploads",
        virtualPath = "/nbg-uploads",
        ubuntuPath = "$ubuntuRoot/nbg-uploads",
        file = uploadsDir.canonicalFile,
      )
      return (rootEntries + uploadsEntry).sortedWith(entryComparator)
    }
    val canonicalBase = baseDirForVirtualPath(resolved.virtualPath).canonicalFile
    return resolved.file.listFiles().orEmpty()
      .mapNotNull { child ->
        val canonicalFile = child.canonicalFileInside(canonicalBase) ?: return@mapNotNull null
        val childVirtualPath = resolved.virtualPath.trimEnd('/') + "/" + child.name
        NbgFileShareEntry(
          name = child.name,
          virtualPath = childVirtualPath,
          ubuntuPath = ubuntuPathFor(childVirtualPath),
          file = canonicalFile,
        )
      }
      .sortedWith(entryComparator)
  }

  private fun ubuntuPathFor(virtualPath: String): String =
    if (virtualPath == "/") ubuntuRoot else ubuntuRoot + virtualPath

  private fun baseDirForVirtualPath(virtualPath: String): File =
    if (virtualPath == "/nbg-uploads" || virtualPath.startsWith("/nbg-uploads/")) uploadsDir else rootHomeDir

  private companion object {
    val entryComparator = compareBy<NbgFileShareEntry>({ !it.file.isDirectory }, { it.name.lowercase(Locale.US) })
  }
}

data class NbgResolvedFileSharePath(
  val virtualPath: String,
  val ubuntuPath: String,
  val file: File,
  val hostFile: File = file,
)

data class NbgFileShareEntry(
  val name: String,
  val virtualPath: String,
  val ubuntuPath: String,
  val file: File,
)

private fun normalizeVirtualPath(rawPath: String): String {
  val decoded = decodePercentEscapes(rawPath.substringBefore('?'))
  val segments = ArrayDeque<String>()
  decoded.split('/').forEach { segment ->
    when {
      segment.isBlank() || segment == "." -> Unit
      segment == ".." -> {
        if (segments.isNotEmpty()) {
          segments.removeLast()
        }
      }
      else -> segments.addLast(segment)
    }
  }
  return "/" + segments.joinToString("/")
}

private fun decodePercentEscapes(path: String): String {
  val result = StringBuilder(path.length)
  val bytes = java.io.ByteArrayOutputStream()

  fun flushBytes() {
    if (bytes.size() == 0) return
    result.append(String(bytes.toByteArray(), Charsets.UTF_8))
    bytes.reset()
  }

  var index = 0
  while (index < path.length) {
    if (path[index] == '%' && index + 2 < path.length) {
      val high = path[index + 1].digitToIntOrNull(16)
      val low = path[index + 2].digitToIntOrNull(16)
      if (high != null && low != null) {
        bytes.write(high * 16 + low)
        index += 3
        continue
      }
    }
    flushBytes()
    result.append(path[index])
    index += 1
  }
  flushBytes()
  return result.toString()
}

private fun File.isInsideOrSame(base: File): Boolean {
  var current: File? = this
  while (current != null) {
    if (current == base) return true
    current = current.parentFile
  }
  return false
}

private fun File.canonicalFileInside(canonicalBase: File): File? {
  val canonical = runCatching { canonicalFile }.getOrNull() ?: return null
  return canonical.takeIf { it.isInsideOrSame(canonicalBase) }
}

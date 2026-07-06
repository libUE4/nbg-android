package com.nbg.android.terminal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Proxy
import android.net.ProxyInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class TerminalEnvironment(private val context: Context) {
  private val filesDir: File = context.filesDir
  private val usrDir = File(filesDir, "usr")
  private val binDir = File(usrDir, "bin")
  private val libDir = File(usrDir, "lib")
  private val tmpDir = File(filesDir, "tmp")
  private val uploadsDir = File(filesDir, "nbg-uploads")

  val commonScript: File = File(filesDir, "common.sh")

  fun prepare(): File {
    usrDir.mkdirs()
    binDir.mkdirs()
    libDir.mkdirs()
    tmpDir.mkdirs()
    uploadsDir.mkdirs()
    copyVersionedAsset(
      assetName = UbuntuBootstrapScript.UBUNTU_ARCHIVE,
      target = File(filesDir, UbuntuBootstrapScript.UBUNTU_ARCHIVE),
      version = UbuntuBootstrapScript.UBUNTU_ARCHIVE_VERSION,
    )
    copyVersionedAsset(
      assetName = NbgCodeRuntime.nodeArchiveAssetName,
      target = File(filesDir, NbgCodeRuntime.nodeArchiveAssetName),
      version = NbgCodeRuntime.nodeArchiveVersion,
    )
    copyAssetIfMissing("setup_fake_sysdata.sh", File(filesDir, "setup_fake_sysdata.sh"))
    linkRuntimeBinary("libbusybox.so", "busybox")
    linkRuntimeBinary("libproot.so", "proot")
    linkRuntimeBinary("libloader.so", "loader")
    linkRuntimeBinary("libbash.so", "bash")
    linkRuntimeBinary("libsudo.so", "sudo")
    TerminalRuntimeLibrary.prootSharedLibraries.forEach {
      linkRuntimeLibrary(it.packagedName, it.runtimeName)
    }
    writeCommonScript()
    return commonScript
  }

  fun processEnvironment(): Map<String, String> {
    val env = linkedMapOf(
      "PATH" to "${binDir.absolutePath}:${System.getenv("PATH").orEmpty()}",
      "HOME" to filesDir.absolutePath,
      "PREFIX" to usrDir.absolutePath,
      "TERMUX_PREFIX" to usrDir.absolutePath,
      "LD_LIBRARY_PATH" to "${context.applicationInfo.nativeLibraryDir}:${libDir.absolutePath}:${binDir.absolutePath}",
      "PROOT_LOADER" to File(binDir, "loader").absolutePath,
      "TMPDIR" to tmpDir.absolutePath,
      "PROOT_TMP_DIR" to tmpDir.absolutePath,
      "TERM" to "xterm-256color",
      "LANG" to "en_US.UTF-8",
    )
    env.putAll(androidProxyEnvironment())
    return env
  }

  private fun androidProxyEnvironment(): Map<String, String> {
    val proxy = resolveAndroidHttpProxy() ?: return inheritedProxyEnvironment()
    val proxyUrl = proxy.url
    val noProxy = (defaultNoProxyEntries + proxy.exclusions)
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
      .joinToString(",")
    return linkedMapOf(
      "HTTP_PROXY" to proxyUrl,
      "HTTPS_PROXY" to proxyUrl,
      "ALL_PROXY" to proxyUrl,
      "NO_PROXY" to noProxy,
      "http_proxy" to proxyUrl,
      "https_proxy" to proxyUrl,
      "all_proxy" to proxyUrl,
      "no_proxy" to noProxy,
      "NPM_CONFIG_PROXY" to proxyUrl,
      "NPM_CONFIG_HTTPS_PROXY" to proxyUrl,
      "npm_config_proxy" to proxyUrl,
      "npm_config_https_proxy" to proxyUrl,
    )
  }

  private fun inheritedProxyEnvironment(): Map<String, String> =
    proxyEnvironmentNames.mapNotNull { name ->
      System.getenv(name)?.takeIf { it.isNotBlank() }?.let { name to it }
    }.toMap()

  private fun resolveAndroidHttpProxy(): AndroidHttpProxy? {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val activeProxy = runCatching {
      val activeNetwork = manager.activeNetwork ?: return@runCatching null
      manager.getLinkProperties(activeNetwork)?.httpProxy
    }.getOrNull()?.toAndroidHttpProxy()
    if (activeProxy != null) return activeProxy

    val host = runCatching { Proxy.getDefaultHost() }.getOrNull()?.trim().orEmpty()
    val port = runCatching { Proxy.getDefaultPort() }.getOrDefault(-1)
    return AndroidHttpProxy.from(host, port, emptyList())
  }

  private fun ProxyInfo.toAndroidHttpProxy(): AndroidHttpProxy? =
    AndroidHttpProxy.from(
      host = runCatching { host }.getOrNull()?.trim().orEmpty(),
      port = runCatching { port }.getOrDefault(-1),
      exclusions = runCatching { exclusionList?.toList().orEmpty() }.getOrDefault(emptyList()),
    )

  private fun writeCommonScript() {
    val script = UbuntuBootstrapScript(
      filesDir = filesDir.absolutePath,
      nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
      packageName = context.packageName,
    ).render()
    commonScript.writeTextIfChanged(script)
  }

  private fun copyAssetIfMissing(assetName: String, target: File) {
    if (target.exists()) return
    context.assets.open(assetName).use { input ->
      target.outputStream().use { output -> input.copyTo(output) }
    }
  }

  private fun copyVersionedAsset(assetName: String, target: File, version: String) {
    val marker = File(target.parentFile ?: filesDir, "${target.name}.version")
    if (target.isFile && target.length() > 0L && marker.readTextOrNull() == version) return
    val tmp = File(target.parentFile ?: filesDir, "${target.name}.tmp")
    context.assets.open(assetName).use { input ->
      tmp.outputStream().use { output -> input.copyTo(output) }
    }
    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    marker.writeText(version, Charsets.UTF_8)
  }

  private fun File.readTextOrNull(): String? =
    runCatching { readText(Charsets.UTF_8) }.getOrNull()

  private fun linkRuntimeBinary(libName: String, linkName: String) {
    val target = File(context.applicationInfo.nativeLibraryDir, libName)
    val link = File(binDir, linkName)
    if (!target.exists()) return
    if (!target.canExecute()) target.setExecutable(true, false)
    ensureSymlink(link.toPath(), target.toPath())
  }

  private fun linkRuntimeLibrary(libName: String, linkName: String) {
    val target = File(context.applicationInfo.nativeLibraryDir, libName)
    val link = File(libDir, linkName)
    if (!target.exists()) return
    ensureSymlink(link.toPath(), target.toPath())
  }

  private fun File.writeTextIfChanged(text: String) {
    if (isFile && readTextOrNull() == text) return
    writeText(text, Charsets.UTF_8)
  }

  private fun ensureSymlink(link: Path, target: Path) {
    if (Files.isSymbolicLink(link)) {
      val existingTarget = runCatching { Files.readSymbolicLink(link) }.getOrNull()
      if (existingTarget == target) return
    }
    Files.deleteIfExists(link)
    Files.createSymbolicLink(link, target)
  }

  private data class AndroidHttpProxy(
    val host: String,
    val port: Int,
    val exclusions: List<String>,
  ) {
    val url: String
      get() = "http://${asProxyUrlHost(host)}:$port"

    companion object {
      fun from(host: String, port: Int, exclusions: List<String>): AndroidHttpProxy? =
        host.takeIf { it.isNotBlank() }
          ?.takeIf { port in 1..65535 }
          ?.let { AndroidHttpProxy(it, port, exclusions) }

      private fun asProxyUrlHost(host: String): String =
        if (host.contains(':') && !host.startsWith('[') && !host.endsWith(']')) "[$host]" else host
    }
  }

  private companion object {
    val proxyEnvironmentNames = listOf(
      "HTTP_PROXY",
      "HTTPS_PROXY",
      "ALL_PROXY",
      "NO_PROXY",
      "http_proxy",
      "https_proxy",
      "all_proxy",
      "no_proxy",
      "NPM_CONFIG_PROXY",
      "NPM_CONFIG_HTTPS_PROXY",
      "npm_config_proxy",
      "npm_config_https_proxy",
    )

    val defaultNoProxyEntries = listOf(
      "localhost",
      "127.0.0.1",
      "127.*",
      "::1",
      "0.0.0.0",
      "10.*",
      "172.16.*",
      "172.17.*",
      "172.18.*",
      "172.19.*",
      "172.2*",
      "172.30.*",
      "172.31.*",
      "192.168.*",
    )
  }
}

package com.nbg.android

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal const val HANA_SKILL_DOWNLOAD_MAX_BYTES = 5L * 1024L * 1024L

internal fun downloadSkillInstallUrl(rawUrl: String, ubuntuRootHomeDir: File): String {
  val review = nbgReviewSkillInstallSource(rawUrl)
  if (!review.allowInstall) error(review.reason)
  val url = URL(rawUrl)
  val host = url.host.orEmpty().lowercase()
  if (url.protocol !in setOf("http", "https")) {
    error("Skill 链接必须以 http:// 或 https:// 开头")
  }
  val installRoot = File(ubuntuRootHomeDir, ".nbg-skill-installs").apply { mkdirs() }
  val safeId = rawUrl.sha256Hex().take(16)
  val fileName = url.path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "SKILL.md"
  val looksRawSkill = fileName.equals("SKILL.md", ignoreCase = true) ||
    rawUrl.contains("raw.githubusercontent.com", ignoreCase = true)
  if (host == "github.com" && !rawUrl.contains("/raw/", ignoreCase = true) && !rawUrl.endsWith(".zip", ignoreCase = true)) {
    error("GitHub 仓库链接请在聊天里用 install_skill 工具安装；Skills 页支持 raw SKILL.md、.zip 或 .skill 链接。")
  }
  val target = if (looksRawSkill) {
    File(installRoot, "url-$safeId/SKILL.md")
  } else {
    File(installRoot, "url-$safeId-${fileName.sanitizeSkillInstallFileName()}")
  }
  target.parentFile?.mkdirs()
  val conn = (url.openConnection() as HttpURLConnection).apply {
    connectTimeout = 15_000
    readTimeout = 20_000
    instanceFollowRedirects = true
    setRequestProperty("User-Agent", "NBG-Android/1.0")
    setRequestProperty("Accept", "*/*")
  }
  try {
    val code = conn.responseCode
    if (code !in 200..299) {
      val message = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }?.take(160).orEmpty()
      error("下载 Skill 失败：HTTP $code${message.ifBlank { "" }.let { if (it.isBlank()) "" else " $it" }}")
    }
    val length = conn.contentLengthLong
    if (length > HANA_SKILL_DOWNLOAD_MAX_BYTES) error("Skill 文件过大，最大 5MB")
    var copied = 0L
    conn.inputStream.use { input ->
      target.outputStream().use { output ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
          val read = input.read(buffer)
          if (read < 0) break
          copied += read
          if (copied > HANA_SKILL_DOWNLOAD_MAX_BYTES) throw IOException("Skill 文件过大，最大 5MB")
          output.write(buffer, 0, read)
        }
      }
    }
  } finally {
    conn.disconnect()
  }
  if (looksRawSkill && !target.readText(Charsets.UTF_8).contains("name:", ignoreCase = true)) {
    error("下载的 SKILL.md 缺少 name 字段")
  }
  nbgWriteSkillDownloadProvenance(review, rawUrl, target)
  return target.toUbuntuRootPath()
}

internal fun String.isSkillInstallUrl(): Boolean =
  startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

internal fun String.sanitizeSkillInstallFileName(): String =
  replace(Regex("[^A-Za-z0-9._-]+"), "-")
    .trim('-', '.', '_')
    .ifBlank { "skill.skill" }
    .take(96)

internal fun File.toUbuntuRootPath(): String {
  val marker = "/usr/var/lib/proot-distro/installed-rootfs/ubuntu/root/"
  val normalized = absolutePath.replace(File.separatorChar, '/')
  val relative = normalized.substringAfter(marker, missingDelimiterValue = "")
  if (relative.isBlank()) error("Skill 文件不在 HanakoPro Ubuntu /root 下")
  return "/root/$relative"
}

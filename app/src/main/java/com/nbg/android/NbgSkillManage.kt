package com.nbg.android

import java.io.File

enum class NbgSkillManageAction(val wireName: String) {
  Create("create"),
  Edit("edit"),
  Patch("patch"),
  WriteFile("write_file"),
  RemoveFile("remove_file"),
  Delete("delete"),
}

data class NbgSkillManageRequest(
  val action: NbgSkillManageAction,
  val name: String,
  val content: String = "",
  val filePath: String = "",
  val fileContent: String = "",
  val oldString: String = "",
  val newString: String = "",
  val replaceAll: Boolean = false,
)

data class NbgSkillManageResult(
  val ok: Boolean,
  val action: NbgSkillManageAction,
  val skillName: String,
  val message: String,
  val pathLabel: String = "",
  val backupLabel: String = "",
  val sha256: String = "",
)

internal class NbgSkillManage(
  private val skillRoot: File,
) {
  constructor(context: android.content.Context) : this(File(context.applicationContext.filesDir, "learned-skills"))

  fun apply(request: NbgSkillManageRequest): NbgSkillManageResult {
    val name = request.name.nbgManagedSkillName()
    if (name.isBlank()) return request.error(name, "skill name is required")
    return when (request.action) {
      NbgSkillManageAction.Create -> create(name, request.content)
      NbgSkillManageAction.Edit -> edit(name, request.content)
      NbgSkillManageAction.Patch -> patch(name, request.oldString, request.newString, request.replaceAll, request.filePath)
      NbgSkillManageAction.WriteFile -> writeFile(name, request.filePath, request.fileContent)
      NbgSkillManageAction.RemoveFile -> removeFile(name, request.filePath)
      NbgSkillManageAction.Delete -> archive(name)
    }
  }

  private fun create(name: String, content: String): NbgSkillManageResult {
    val body = content.nbgManagedSkillContent()
    if (body.isBlank()) return NbgSkillManageRequest(NbgSkillManageAction.Create, name).error(name, "content is required")
    val file = skillDir(name).resolve("SKILL.md")
    if (file.exists()) return NbgSkillManageResult(false, NbgSkillManageAction.Create, name, "skill already exists", file.nbgSkillPathLabel())
    file.parentFile?.mkdirs()
    file.writeText(body, Charsets.UTF_8)
    return NbgSkillManageResult(true, NbgSkillManageAction.Create, name, "created", file.nbgSkillPathLabel(), sha256 = body.sha256Hex())
  }

  private fun edit(name: String, content: String): NbgSkillManageResult {
    val body = content.nbgManagedSkillContent()
    if (body.isBlank()) return NbgSkillManageRequest(NbgSkillManageAction.Edit, name).error(name, "content is required")
    val file = skillDir(name).resolve("SKILL.md")
    file.parentFile?.mkdirs()
    val backup = backupFile(file)
    if (file.isFile) backup.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8)
    file.writeText(body, Charsets.UTF_8)
    return NbgSkillManageResult(true, NbgSkillManageAction.Edit, name, "edited", file.nbgSkillPathLabel(), backup.nbgSkillPathLabel(), body.sha256Hex())
  }

  private fun patch(name: String, oldString: String, newString: String, replaceAll: Boolean, relativePath: String): NbgSkillManageResult {
    if (oldString.isEmpty()) return NbgSkillManageRequest(NbgSkillManageAction.Patch, name).error(name, "oldString is required")
    val file = resolveSkillRelativeFile(name, relativePath.ifBlank { "SKILL.md" })
      ?: return NbgSkillManageRequest(NbgSkillManageAction.Patch, name).error(name, "bad file path")
    if (!file.isFile) return NbgSkillManageResult(false, NbgSkillManageAction.Patch, name, "file not found", file.nbgSkillPathLabel())
    val current = file.readText(Charsets.UTF_8)
    if (!current.contains(oldString)) return NbgSkillManageResult(false, NbgSkillManageAction.Patch, name, "oldString not found", file.nbgSkillPathLabel())
    val next = if (replaceAll) current.replace(oldString, newString) else current.replaceFirst(oldString, newString)
    val backup = backupFile(file)
    backup.writeText(current, Charsets.UTF_8)
    file.writeText(next.nbgManagedSkillContent(limit = 80_000), Charsets.UTF_8)
    return NbgSkillManageResult(true, NbgSkillManageAction.Patch, name, "patched", file.nbgSkillPathLabel(), backup.nbgSkillPathLabel(), next.sha256Hex())
  }

  private fun writeFile(name: String, relativePath: String, content: String): NbgSkillManageResult {
    val file = resolveSkillRelativeFile(name, relativePath)
      ?: return NbgSkillManageRequest(NbgSkillManageAction.WriteFile, name).error(name, "bad file path")
    val body = content.nbgManagedSkillContent(limit = 80_000)
    file.parentFile?.mkdirs()
    val backup = if (file.isFile) backupFile(file).also { it.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8) } else null
    file.writeText(body, Charsets.UTF_8)
    return NbgSkillManageResult(true, NbgSkillManageAction.WriteFile, name, "file written", file.nbgSkillPathLabel(), backup?.nbgSkillPathLabel().orEmpty(), body.sha256Hex())
  }

  private fun removeFile(name: String, relativePath: String): NbgSkillManageResult {
    val file = resolveSkillRelativeFile(name, relativePath)
      ?: return NbgSkillManageRequest(NbgSkillManageAction.RemoveFile, name).error(name, "bad file path")
    if (file.name == "SKILL.md") return NbgSkillManageResult(false, NbgSkillManageAction.RemoveFile, name, "SKILL.md cannot be removed; use delete", file.nbgSkillPathLabel())
    if (!file.isFile) return NbgSkillManageResult(false, NbgSkillManageAction.RemoveFile, name, "file not found", file.nbgSkillPathLabel())
    val backup = backupFile(file)
    backup.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8)
    file.delete()
    return NbgSkillManageResult(true, NbgSkillManageAction.RemoveFile, name, "file removed", file.nbgSkillPathLabel(), backup.nbgSkillPathLabel())
  }

  private fun archive(name: String): NbgSkillManageResult {
    val dir = skillDir(name)
    if (!dir.isDirectory) return NbgSkillManageResult(false, NbgSkillManageAction.Delete, name, "skill not found", dir.nbgSkillPathLabel())
    val archive = skillRoot.resolve(".nbg-archive/${name}-${System.currentTimeMillis()}")
    archive.parentFile?.mkdirs()
    val moved = dir.renameTo(archive)
    return if (moved) {
      NbgSkillManageResult(true, NbgSkillManageAction.Delete, name, "archived", dir.nbgSkillPathLabel(), archive.nbgSkillPathLabel())
    } else {
      NbgSkillManageResult(false, NbgSkillManageAction.Delete, name, "archive failed", dir.nbgSkillPathLabel())
    }
  }

  private fun skillDir(name: String): File =
    skillRoot.resolve(name.nbgJourneySafeSkillDir())

  private fun resolveSkillRelativeFile(name: String, relativePath: String): File? {
    val clean = relativePath.replace('\\', '/').trim().trimStart('/')
    if (clean.isBlank() || clean.contains("..")) return null
    val root = skillDir(name).canonicalFile
    val file = File(root, clean).canonicalFile
    return file.takeIf { it.path == root.path || it.path.startsWith(root.path + File.separator) }
  }

  private fun backupFile(file: File): File {
    val parent = file.parentFile ?: skillRoot
    val backup = parent.resolve(".nbg-rollback/${file.name}.${System.currentTimeMillis()}.bak")
    backup.parentFile?.mkdirs()
    return backup
  }

  private fun NbgSkillManageRequest.error(skillName: String, message: String): NbgSkillManageResult =
    NbgSkillManageResult(false, action, skillName, message)
}

private fun String.nbgManagedSkillName(): String =
  trim()
    .lowercase()
    .replace(Regex("[^a-z0-9_.-]+"), "-")
    .trim('-', '.', '_')
    .take(80)

private fun String.nbgManagedSkillContent(limit: Int = 120_000): String =
  nbgRedactDiagnosticText(this)
    .replace("\r\n", "\n")
    .trim()
    .take(limit.coerceAtLeast(0))

private fun File.nbgSkillPathLabel(): String =
  if (path.isBlank()) "" else "[local-path]"

package com.nbg.android

import java.io.File

enum class NbgSkillDiffLineKind {
  Context,
  Added,
  Removed,
}

data class NbgSkillDiffLine(
  val kind: NbgSkillDiffLineKind,
  val oldLineNumber: Int = 0,
  val newLineNumber: Int = 0,
  val text: String,
)

data class NbgSkillDiffHunk(
  val index: Int,
  val oldStartLine: Int,
  val newStartLine: Int,
  val lines: List<NbgSkillDiffLine>,
) {
  val addedCount: Int
    get() = lines.count { it.kind == NbgSkillDiffLineKind.Added }

  val removedCount: Int
    get() = lines.count { it.kind == NbgSkillDiffLineKind.Removed }
}

data class NbgSkillDiffPreview(
  val skillName: String,
  val filePath: String,
  val relativePath: String = "SKILL.md",
  val beforeText: String,
  val afterText: String,
  val beforeSha256: String,
  val afterSha256: String,
  val hunks: List<NbgSkillDiffHunk>,
  val rollbackAvailable: Boolean = false,
  val message: String = "",
) {
  val changed: Boolean
    get() = beforeSha256 != afterSha256

  val addedCount: Int
    get() = hunks.sumOf { it.addedCount }

  val removedCount: Int
    get() = hunks.sumOf { it.removedCount }
}

data class NbgSkillDiffFile(
  val skillName: String,
  val relativePath: String,
  val sizeBytes: Long,
  val rollbackAvailable: Boolean = false,
)

data class NbgSkillMergeSelection(
  val acceptedHunkIndexes: Set<Int>,
) {
  companion object {
    val Empty = NbgSkillMergeSelection(emptySet())
  }
}

internal class NbgSkillDiffMerge(
  private val skillRoot: File,
) {
  constructor(context: android.content.Context) : this(File(context.applicationContext.filesDir, "learned-skills"))

  fun preview(request: NbgSkillManageRequest): NbgSkillDiffPreview {
    val name = request.name.nbgSkillDiffSafeName()
    val relativePath = request.filePath.nbgSkillDiffRelativePath()
    val file = resolveSkillFile(name, relativePath)
    if (name.isBlank() || file == null) return emptyPreview(name, relativePath, "bad skill or file path")
    val before = if (file.isFile) file.readText(Charsets.UTF_8) else ""
    val after = when (request.action) {
      NbgSkillManageAction.Create,
      NbgSkillManageAction.Edit -> request.content.nbgSkillDiffContent()
      NbgSkillManageAction.Patch -> {
        if (request.oldString.isBlank()) before else if (request.replaceAll) before.replace(request.oldString, request.newString) else before.replaceFirst(request.oldString, request.newString)
      }
      NbgSkillManageAction.WriteFile -> request.fileContent.nbgSkillDiffContent()
      NbgSkillManageAction.RemoveFile,
      NbgSkillManageAction.Delete -> ""
    }
    return previewTexts(
      skillName = name,
      filePath = file.nbgSkillDiffPathLabel(),
      relativePath = relativePath,
      beforeText = before,
      afterText = after,
      rollbackAvailable = rollbackFileFor(file).isFile,
    )
  }

  fun previewCurrent(skillName: String, filePath: String = "SKILL.md"): NbgSkillDiffPreview {
    val name = skillName.nbgSkillDiffSafeName()
    val relativePath = filePath.nbgSkillDiffRelativePath()
    val file = resolveSkillFile(name, relativePath)
    if (name.isBlank() || file == null) return emptyPreview(name, relativePath, "bad skill or file path")
    val current = if (file.isFile) file.readText(Charsets.UTF_8) else ""
    val rollback = rollbackFileFor(file)
    val previous = if (rollback.isFile) rollback.readText(Charsets.UTF_8) else ""
    return previewTexts(
      skillName = name,
      filePath = file.nbgSkillDiffPathLabel(),
      relativePath = relativePath,
      beforeText = previous,
      afterText = current,
      rollbackAvailable = rollback.isFile,
      message = if (rollback.isFile) "rollback comparison" else "no rollback snapshot",
    )
  }

  fun listFiles(skillName: String): List<NbgSkillDiffFile> {
    val name = skillName.nbgSkillDiffSafeName()
    if (name.isBlank()) return emptyList()
    val root = skillRoot.resolve(name.nbgSkillDiffSafeDir()).canonicalFile
    if (!root.isDirectory) return emptyList()
    return root.walkTopDown()
      .filter { it.isFile && ".nbg-rollback" !in it.relativeTo(root).path && ".nbg-archive" !in it.relativeTo(root).path }
      .map { file ->
        val rel = file.relativeTo(root).path.replace(File.separatorChar, '/')
        NbgSkillDiffFile(
          skillName = name,
          relativePath = rel,
          sizeBytes = file.length().coerceAtLeast(0L),
          rollbackAvailable = rollbackFileFor(file).isFile,
        )
      }
      .sortedWith(compareBy<NbgSkillDiffFile> { it.relativePath != "SKILL.md" }.thenBy { it.relativePath })
      .take(80)
      .toList()
  }

  fun applyMergedPreview(
    preview: NbgSkillDiffPreview,
    selection: NbgSkillMergeSelection,
  ): NbgSkillManageRequest {
    val accepted = selection.acceptedHunkIndexes
    val merged = if (accepted.isEmpty()) {
      preview.beforeText
    } else if (accepted.size == preview.hunks.size) {
      preview.afterText
    } else {
      nbgMergeSkillDiffHunks(preview.beforeText, preview.afterText, preview.hunks, accepted)
    }
    return if (preview.relativePath == "SKILL.md") {
      NbgSkillManageRequest(
        action = if (preview.beforeText.isBlank()) NbgSkillManageAction.Create else NbgSkillManageAction.Edit,
        name = preview.skillName,
        content = merged,
        filePath = preview.relativePath,
      )
    } else {
      NbgSkillManageRequest(
        action = NbgSkillManageAction.WriteFile,
        name = preview.skillName,
        filePath = preview.relativePath,
        fileContent = merged,
      )
    }
  }

  private fun previewTexts(
    skillName: String,
    filePath: String,
    relativePath: String,
    beforeText: String,
    afterText: String,
    rollbackAvailable: Boolean,
    message: String = "",
  ): NbgSkillDiffPreview =
    NbgSkillDiffPreview(
      skillName = skillName,
      filePath = filePath,
      relativePath = relativePath,
      beforeText = beforeText,
      afterText = afterText,
      beforeSha256 = beforeText.sha256Hex(),
      afterSha256 = afterText.sha256Hex(),
      hunks = nbgBuildSkillDiffHunks(beforeText, afterText),
      rollbackAvailable = rollbackAvailable,
      message = message,
    )

  private fun emptyPreview(skillName: String, filePath: String, message: String): NbgSkillDiffPreview =
    NbgSkillDiffPreview(
      skillName = skillName,
      filePath = filePath,
      relativePath = filePath.nbgSkillDiffRelativePath(),
      beforeText = "",
      afterText = "",
      beforeSha256 = "".sha256Hex(),
      afterSha256 = "".sha256Hex(),
      hunks = emptyList(),
      message = message,
    )

  private fun resolveSkillFile(name: String, relativePath: String): File? {
    val clean = relativePath.replace('\\', '/').trim().trimStart('/')
    if (name.isBlank() || clean.isBlank() || clean.contains("..")) return null
    val root = skillRoot.resolve(name.nbgSkillDiffSafeDir()).canonicalFile
    val file = File(root, clean).canonicalFile
    return file.takeIf { it.path == root.path || it.path.startsWith(root.path + File.separator) }
  }

  private fun rollbackFileFor(file: File): File =
    file.parentFile
      ?.resolve(".nbg-rollback")
      ?.listFiles()
      .orEmpty()
      .filter { candidate ->
        candidate.isFile &&
          (
            candidate.name.startsWith(file.name) ||
              candidate.name.startsWith(file.nameWithoutExtension)
            )
      }
      .maxByOrNull { it.lastModified() }
      ?: file.parentFile?.resolve(".nbg-rollback/${file.name}.latest.bak")
      ?: file.resolveSibling(".nbg-rollback/${file.name}.latest.bak")
}

internal fun nbgBuildSkillDiffHunks(
  beforeText: String,
  afterText: String,
  contextRadius: Int = 2,
): List<NbgSkillDiffHunk> {
  val before = beforeText.nbgSkillDiffLines()
  val after = afterText.nbgSkillDiffLines()
  val raw = nbgSkillDiffLines(before, after)
  val changedIndexes = raw.withIndex().filter { it.value.kind != NbgSkillDiffLineKind.Context }.map { it.index }
  if (changedIndexes.isEmpty()) return emptyList()
  val ranges = mutableListOf<IntRange>()
  changedIndexes.forEach { index ->
    val start = (index - contextRadius).coerceAtLeast(0)
    val end = (index + contextRadius).coerceAtMost(raw.lastIndex)
    val last = ranges.lastOrNull()
    if (last != null && start <= last.last + 1) {
      ranges[ranges.lastIndex] = last.first..maxOf(last.last, end)
    } else {
      ranges += start..end
    }
  }
  return ranges.mapIndexed { hunkIndex, range ->
    val lines = raw.slice(range)
    NbgSkillDiffHunk(
      index = hunkIndex,
      oldStartLine = lines.firstOrNull { it.oldLineNumber > 0 }?.oldLineNumber ?: 0,
      newStartLine = lines.firstOrNull { it.newLineNumber > 0 }?.newLineNumber ?: 0,
      lines = lines,
    )
  }
}

private fun nbgSkillDiffLines(
  before: List<String>,
  after: List<String>,
): List<NbgSkillDiffLine> {
  val table = Array(before.size + 1) { IntArray(after.size + 1) }
  for (i in before.indices.reversed()) {
    for (j in after.indices.reversed()) {
      table[i][j] = if (before[i] == after[j]) {
        table[i + 1][j + 1] + 1
      } else {
        maxOf(table[i + 1][j], table[i][j + 1])
      }
    }
  }
  val lines = mutableListOf<NbgSkillDiffLine>()
  var i = 0
  var j = 0
  while (i < before.size && j < after.size) {
    if (before[i] == after[j]) {
      lines += NbgSkillDiffLine(NbgSkillDiffLineKind.Context, oldLineNumber = i + 1, newLineNumber = j + 1, text = before[i])
      i += 1
      j += 1
    } else if (table[i + 1][j] >= table[i][j + 1]) {
      lines += NbgSkillDiffLine(NbgSkillDiffLineKind.Removed, oldLineNumber = i + 1, text = before[i])
      i += 1
    } else {
      lines += NbgSkillDiffLine(NbgSkillDiffLineKind.Added, newLineNumber = j + 1, text = after[j])
      j += 1
    }
  }
  while (i < before.size) {
    lines += NbgSkillDiffLine(NbgSkillDiffLineKind.Removed, oldLineNumber = i + 1, text = before[i])
    i += 1
  }
  while (j < after.size) {
    lines += NbgSkillDiffLine(NbgSkillDiffLineKind.Added, newLineNumber = j + 1, text = after[j])
    j += 1
  }
  return lines
}

internal fun nbgMergeSkillDiffHunks(
  beforeText: String,
  afterText: String,
  hunks: List<NbgSkillDiffHunk>,
  accepted: Set<Int>,
): String {
  if (accepted.isEmpty()) return beforeText
  if (accepted.size == hunks.size) return afterText
  val beforeLines = beforeText.nbgSkillDiffLines().toMutableList()
  hunks.sortedByDescending { it.oldStartLine }.forEach { hunk ->
    if (hunk.index !in accepted) return@forEach
    val oldLines = hunk.lines.filter { it.kind != NbgSkillDiffLineKind.Added }.map { it.text }
    val newLines = hunk.lines.filter { it.kind != NbgSkillDiffLineKind.Removed }.map { it.text }
    val start = hunk.lines.firstOrNull { it.oldLineNumber > 0 }?.oldLineNumber?.minus(1) ?: return@forEach
    val endExclusive = (start + oldLines.size).coerceAtMost(beforeLines.size)
    repeat((endExclusive - start).coerceAtLeast(0)) {
      beforeLines.removeAt(start)
    }
    beforeLines.addAll(start.coerceAtMost(beforeLines.size), newLines)
  }
  return beforeLines.joinToString("\n")
}

private fun String.nbgSkillDiffLines(): List<String> =
  if (isEmpty()) emptyList() else replace("\r\n", "\n").split('\n')

private fun String.nbgSkillDiffSafeName(): String =
  trim()
    .lowercase()
    .replace(Regex("[^a-z0-9_.-]+"), "-")
    .trim('-', '.', '_')
    .take(80)

private fun String.nbgSkillDiffSafeDir(): String =
  nbgSkillDiffSafeName().ifBlank { "learned-skill" }

private fun String.nbgSkillDiffContent(limit: Int = 120_000): String =
  nbgRedactDiagnosticText(this)
    .replace("\r\n", "\n")
    .trim()
    .take(limit.coerceAtLeast(0))

private fun String.nbgSkillDiffRelativePath(): String =
  replace('\\', '/')
    .trim()
    .trimStart('/')
    .ifBlank { "SKILL.md" }
    .take(240)

private fun File.nbgSkillDiffPathLabel(): String =
  if (path.isBlank()) "" else "[local-path]"

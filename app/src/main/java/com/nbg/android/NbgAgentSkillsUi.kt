package com.nbg.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NbgSkillsScreen(
  snapshot: HanakoSkillsSnapshot,
  rawSnapshot: HanakoSkillsSnapshot = snapshot,
  skillCuratorMetadata: NbgSkillCuratorMetadata = NbgSkillCuratorMetadata(),
  skillCuratorLoopState: NbgSkillCuratorLoopState = NbgSkillCuratorLoopState(),
  skillDiffPreview: NbgSkillDiffPreview? = null,
  learnedDraftQueue: NbgLearnedSkillDraftQueue = NbgLearnedSkillDraftQueue(),
  loading: Boolean,
  error: String?,
  busyKey: String?,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onReload: () -> Unit,
  onReloadRuntime: () -> Unit,
  onInstall: (HanakoSkillInstallInput) -> Unit,
  translatedDescriptions: Map<String, String>,
  translatingSkillName: String?,
  translationMessages: Map<String, String>,
  onTranslate: (HanakoSkillSummary) -> Unit,
  onSetEnabled: (String, Boolean) -> Unit,
  onDelete: (String) -> Unit,
  onCreateBundle: (String, List<String>) -> Unit,
  onUpdateBundle: (String, String, List<String>) -> Unit,
  onDeleteBundle: (String) -> Unit,
  onSetExternalPaths: (List<String>) -> Unit,
  onRejectLearnedDraft: (String) -> Unit = {},
  onArchiveSkill: (String) -> Unit = {},
  onRestoreSkill: (String) -> Unit = {},
  onRunCuratorReview: () -> Unit = {},
  onSetCuratorLoopEnabled: (Boolean) -> Unit = {},
  onRunCuratorLoopNow: () -> Unit = {},
  onPreviewCurrentSkillDiff: (String) -> Unit = {},
  onApplySkillDiffMerge: (Set<Int>) -> Unit = {},
  onCloseSkillDiffPreview: () -> Unit = {},
) {
  var addOpen by remember { mutableStateOf(false) }
  var deleteTarget by remember { mutableStateOf<HanakoSkillSummary?>(null) }
  var archiveTarget by remember { mutableStateOf<HanakoSkillSummary?>(null) }
  var detailTarget by remember { mutableStateOf<HanakoSkillSummary?>(null) }
  var createBundleOpen by remember { mutableStateOf(false) }
  var editBundleTarget by remember { mutableStateOf<HanakoSkillBundle?>(null) }
  var deleteBundleTarget by remember { mutableStateOf<HanakoSkillBundle?>(null) }
  var externalPathsOpen by remember { mutableStateOf(false) }
  var enableReviewTarget by remember { mutableStateOf<HanakoSkillSummary?>(null) }
  var translatedSkillNames by remember { mutableStateOf<Set<String>>(emptySet()) }
  val skills = snapshot.visibleSkills
  val archivedSkills = remember(rawSnapshot, skillCuratorMetadata) {
    nbgSkillCuratorArchivedSkills(rawSnapshot, skillCuratorMetadata)
  }
  NbgShellSubPage(
    title = "Skills",
    subtitle = nbgSkillsSubtitle(snapshot, loading),
    onBack = onBack,
    onOpenDrawer = onOpenDrawer,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(NbgAgentColors.Background),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        NbgSkillsSummaryCard(
          snapshot = snapshot,
          loading = loading,
          error = error,
          busy = busyKey != null || translatingSkillName != null,
          onReload = onReload,
          onReloadRuntime = onReloadRuntime,
          onAdd = { addOpen = true },
        )
      }
      item {
        NbgSkillCuratorCard(
          summary = nbgBuildSkillCuratorSummary(rawSnapshot, skillCuratorMetadata),
          archivedSkills = archivedSkills,
          loopState = skillCuratorLoopState,
          busy = busyKey != null,
          onRestore = onRestoreSkill,
          onRunReview = onRunCuratorReview,
          onSetLoopEnabled = onSetCuratorLoopEnabled,
          onRunLoopNow = onRunCuratorLoopNow,
        )
      }
      item {
        NbgSkillDiffMergeCard(
          queue = learnedDraftQueue,
          preview = skillDiffPreview,
          busy = busyKey != null,
          onPreviewSkill = onPreviewCurrentSkillDiff,
          onApplySelection = onApplySkillDiffMerge,
          onClosePreview = onCloseSkillDiffPreview,
        )
      }
      item {
        NbgLearnedSkillDraftQueueCard(
          queue = learnedDraftQueue,
          busy = busyKey != null,
          onReject = onRejectLearnedDraft,
        )
      }
      item {
        NbgSkillBundlesCard(
          bundles = snapshot.bundles,
          busyKey = busyKey,
          onCreate = { createBundleOpen = true },
          onEdit = { editBundleTarget = it },
          onDelete = { deleteBundleTarget = it },
        )
      }
      item {
        NbgExternalSkillPathsCard(
          paths = snapshot.externalPaths,
          busy = busyKey == "skills:external-paths",
          onEdit = { externalPathsOpen = true },
        )
      }
      if (skills.isEmpty()) {
        item { NbgSkillsEmptyState() }
      } else {
        items(skills, key = { it.name }) { skill ->
          val translated = skill.name in translatedSkillNames
          val translatedDescription = translatedDescriptions[skill.name].orEmpty()
          NbgSkillRow(
            skill = skill,
            busyKey = busyKey,
            translated = translated,
            translatedDescription = translatedDescription,
            translationBusy = translatingSkillName == skill.name,
            translationMessage = translationMessages[skill.name],
            onToggleTranslation = {
              if (translated) {
                translatedSkillNames = translatedSkillNames - skill.name
              } else {
                translatedSkillNames = translatedSkillNames + skill.name
                if (translatedDescription.isBlank()) {
                  onTranslate(skill)
                }
              }
            },
            onSetEnabled = { skillName, enabled ->
              if (enabled && nbgSkillSummarySourceReview(skill).requiresReview) {
                enableReviewTarget = skill
              } else {
                onSetEnabled(skillName, enabled)
              }
            },
            onOpenDetail = { detailTarget = skill },
            onArchive = { archiveTarget = skill },
            onDelete = { deleteTarget = skill },
          )
        }
      }
    }
  }
  if (addOpen) {
    NbgSkillInstallDialog(
      busy = busyKey != null,
      onSave = { input ->
        addOpen = false
        onInstall(input)
      },
      onDismiss = { addOpen = false },
    )
  }
  enableReviewTarget?.let { skill ->
    NbgSkillEnableReviewDialog(
      skill = skill,
      busy = busyKey != null,
      onDismiss = { enableReviewTarget = null },
      onConfirm = {
        enableReviewTarget = null
        onSetEnabled(skill.name, true)
      },
    )
  }
  detailTarget?.let { skill ->
    NbgSkillDetailDialog(
      skill = skill,
      translated = skill.name in translatedSkillNames,
      translatedDescription = translatedDescriptions[skill.name].orEmpty(),
      onDismiss = { detailTarget = null },
    )
  }
  deleteTarget?.let { skill ->
    NbgSkillDeleteDialog(
      skill = skill,
      busy = busyKey != null,
      onDismiss = { deleteTarget = null },
      onConfirm = {
        deleteTarget = null
        onDelete(skill.name)
      },
    )
  }
  archiveTarget?.let { skill ->
    NbgSkillArchiveDialog(
      skill = skill,
      busy = busyKey != null,
      onDismiss = { archiveTarget = null },
      onConfirm = {
        archiveTarget = null
        onArchiveSkill(skill.name)
      },
    )
  }
  if (createBundleOpen) {
    NbgSkillBundleDialog(
      bundle = null,
      skills = skills,
      busy = busyKey != null,
      onDismiss = { createBundleOpen = false },
      onSave = { name, skillNames ->
        createBundleOpen = false
        onCreateBundle(name, skillNames)
      },
    )
  }
  editBundleTarget?.let { bundle ->
    NbgSkillBundleDialog(
      bundle = bundle,
      skills = skills,
      busy = busyKey != null,
      onDismiss = { editBundleTarget = null },
      onSave = { name, skillNames ->
        editBundleTarget = null
        onUpdateBundle(bundle.id, name, skillNames)
      },
    )
  }
  deleteBundleTarget?.let { bundle ->
    NbgSkillBundleDeleteDialog(
      bundle = bundle,
      busy = busyKey != null,
      onDismiss = { deleteBundleTarget = null },
      onConfirm = {
        deleteBundleTarget = null
        onDeleteBundle(bundle.id)
      },
    )
  }
  if (externalPathsOpen) {
    NbgExternalSkillPathsDialog(
      paths = snapshot.externalPaths,
      busy = busyKey != null,
      onDismiss = { externalPathsOpen = false },
      onSave = { paths ->
        externalPathsOpen = false
        onSetExternalPaths(paths)
      },
    )
  }
}

@Composable
private fun NbgLearnedSkillDraftQueueCard(
  queue: NbgLearnedSkillDraftQueue,
  busy: Boolean,
  onReject: (String) -> Unit,
) {
  val visibleDrafts = queue.visibleEntries
  val hasDrafts = visibleDrafts.isNotEmpty()
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NbgSkillsIconBox(icon = Icons.Filled.Save, active = hasDrafts)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Learned Skill drafts",
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = if (hasDrafts) {
              "${queue.pendingReviewCount} 待复核 · ${queue.autoAppliedCount} 自动应用 · ${queue.blockedCount} 证据不足"
            } else {
              "当前没有待审查草稿"
            },
            color = if (queue.blockedCount > 0 || queue.dangerousCount > 0) {
              NbgAgentColors.StatusRed
            } else {
              NbgAgentColors.TextMuted
            },
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgSkillsStatusPill(
          text = if (queue.dangerousCount > 0) "${queue.dangerousCount} dangerous" else "auto-gated",
          color = if (queue.dangerousCount > 0) NbgAgentColors.StatusRed else NbgAgentColors.TextMuted,
        )
      }
      Text(
        text = "生成 Skill 必须具备完成证据、来源任务、目标路径复核、SHA-256 和权限等级；低/中风险可自动写入本地 Skill artifact 并启用，High/Dangerous 仍需确认或阻止。",
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
      )
      Text(
        text = NBG_LEARNED_SKILL_DRAFT_REQUIRED_EVIDENCE.joinToString(", "),
        color = NbgAgentColors.CodeText,
        fontSize = 10.5.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (hasDrafts) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
          visibleDrafts.take(3).forEach { draft ->
            NbgLearnedSkillDraftRow(
              draft = draft,
              busy = busy,
              onReject = { onReject(draft.id) },
            )
          }
        }
        if (visibleDrafts.size > 3) {
          Text(
            text = "还有 ${visibleDrafts.size - 3} 个草稿",
            color = NbgAgentColors.TextMuted,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
          )
        }
      }
    }
  }
}

@Composable
private fun NbgLearnedSkillDraftRow(
  draft: NbgLearnedSkillDraftQueueEntry,
  busy: Boolean,
  onReject: () -> Unit,
) {
  val blocked = draft.status == NbgLearnedSkillDraftStatus.BlockedMissingEvidence || !draft.review.allowDraft
  val dangerous = draft.review.permissionTier == NbgPermissionRiskTier.Dangerous
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(13.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(
      1.dp,
      when {
        blocked || dangerous -> NbgAgentColors.StatusRed
        else -> NbgAgentColors.InputBorder
      },
    ),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = draft.skillName,
            color = NbgAgentColors.TextStrong,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = listOf(
              draft.status.label,
              draft.review.permissionTier.label,
              draft.review.sourceTaskId.takeIf { it.isNotBlank() }?.let { "task $it" },
            ).filterNotNull().joinToString(" · "),
            color = if (blocked || dangerous) NbgAgentColors.StatusRed else NbgAgentColors.TextMuted,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgSkillsStatusPill(
          text = if (blocked) "blocked" else "draft",
          color = if (blocked || dangerous) NbgAgentColors.StatusRed else NbgAgentColors.Primary,
        )
      }
      val summary = draft.description.ifBlank { draft.sourceTaskTitle }.ifBlank { draft.review.reason }
      Text(
        text = summary,
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (draft.missingEvidence.isNotEmpty()) {
        Text(
          text = "缺少：${draft.missingEvidence.joinToString(", ")}",
          color = NbgAgentColors.StatusRed,
          fontSize = 10.5.sp,
          lineHeight = 14.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (draft.review.targetPathLabel.isNotBlank()) {
        Text(
          text = draft.review.targetPathLabel,
          color = NbgAgentColors.CodeText,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (draft.rollbackPath.isNotBlank() || draft.previousArtifactSha256.isNotBlank()) {
        Text(
          text = listOf(
            "可回滚".takeIf { draft.rollbackPath.isNotBlank() },
            draft.previousArtifactSha256.take(12).takeIf { it.isNotBlank() }?.let { "prev $it" },
          ).filterNotNull().joinToString(" · "),
          color = NbgAgentColors.StatusYellow,
          fontSize = 10.5.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NbgInlineActionButton(
          label = "拒绝",
          icon = Icons.Filled.Block,
          enabled = !busy,
          onClick = onReject,
        )
      }
    }
  }
}

@Composable
private fun NbgSkillDiffMergeCard(
  queue: NbgLearnedSkillDraftQueue,
  preview: NbgSkillDiffPreview?,
  busy: Boolean,
  onPreviewSkill: (String) -> Unit,
  onApplySelection: (Set<Int>) -> Unit,
  onClosePreview: () -> Unit,
) {
  val learned = queue.visibleEntries.filter {
    it.autoInstalled || it.status == NbgLearnedSkillDraftStatus.AutoApplied || it.rollbackPath.isNotBlank()
  }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, if (preview?.changed == true) NbgAgentColors.PrimarySoft else NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NbgSkillsIconBox(icon = Icons.Filled.Save, active = preview?.changed == true)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Skill Diff / Merge",
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${learned.size} 个本地 learned Skill 可审查",
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        preview?.let {
          NbgSkillsStatusPill(
            text = "${it.hunks.size} hunks",
            color = if (it.changed) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
          )
        }
      }
      if (preview == null) {
        if (learned.isEmpty()) {
          Text(
            text = "暂无可审查的本地 learned Skill。自动应用或回滚记录出现后会在这里显示。",
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
          )
        } else {
          learned.take(4).forEach { draft ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = draft.skillName,
                  color = NbgAgentColors.TextStrong,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = listOf(draft.status.label, "prev ${draft.previousArtifactSha256.take(12)}".takeIf { draft.previousArtifactSha256.isNotBlank() }).filterNotNull().joinToString(" · "),
                  color = NbgAgentColors.TextMuted,
                  fontSize = 10.5.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              NbgInlineActionButton(
                label = "审查",
                icon = Icons.Filled.Visibility,
                enabled = !busy,
                onClick = { onPreviewSkill(draft.skillName) },
              )
            }
          }
        }
        return@Column
      }
      Text(
        text = "${preview.skillName} · +${preview.addedCount} / -${preview.removedCount} · ${preview.message.ifBlank { "diff preview" }}",
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      preview.hunks.take(3).forEach { hunk ->
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          color = NbgAgentColors.SurfaceLow,
          border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Hunk ${hunk.index + 1} · +${hunk.addedCount} / -${hunk.removedCount}",
                color = NbgAgentColors.TextStrong,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
              )
              NbgInlineActionButton(
                label = "仅此块",
                icon = Icons.Filled.Save,
                enabled = !busy,
                onClick = { onApplySelection(setOf(hunk.index)) },
              )
            }
            hunk.lines.take(8).forEach { line ->
              val prefix = when (line.kind) {
                NbgSkillDiffLineKind.Added -> "+"
                NbgSkillDiffLineKind.Removed -> "-"
                NbgSkillDiffLineKind.Context -> " "
              }
              Text(
                text = "$prefix ${line.text}",
                color = when (line.kind) {
                  NbgSkillDiffLineKind.Added -> NbgAgentColors.StatusGreen
                  NbgSkillDiffLineKind.Removed -> NbgAgentColors.StatusRed
                  NbgSkillDiffLineKind.Context -> NbgAgentColors.CodeText
                },
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NbgInlineActionButton(
          label = "应用全部",
          icon = Icons.Filled.Save,
          enabled = !busy && preview.changed,
          onClick = { onApplySelection(preview.hunks.map { it.index }.toSet()) },
        )
        NbgInlineActionButton(
          label = "回滚旧版",
          icon = Icons.Filled.Restore,
          enabled = !busy && preview.rollbackAvailable,
          onClick = { onApplySelection(emptySet()) },
        )
        NbgInlineActionButton(
          label = "关闭",
          icon = Icons.Filled.Block,
          enabled = !busy,
          onClick = onClosePreview,
        )
      }
    }
  }
}

@Composable
private fun NbgSkillCuratorCard(
  summary: NbgSkillCuratorSummary,
  archivedSkills: List<NbgSkillCuratorArchivedSkill>,
  loopState: NbgSkillCuratorLoopState,
  busy: Boolean,
  onRestore: (String) -> Unit,
  onRunReview: () -> Unit,
  onSetLoopEnabled: (Boolean) -> Unit,
  onRunLoopNow: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NbgSkillsIconBox(icon = Icons.Filled.Visibility, active = summary.requiresReviewCount > 0)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Skill Curator",
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = summary.curatorStatus,
            color = if (summary.unverifiedExternalCount > 0) NbgAgentColors.StatusRed else NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgSkillsStatusPill(
          text = "${summary.enabledCount}/${summary.visibleCount} ON",
          color = if (summary.enabledCount > 0) NbgAgentColors.StatusGreen else NbgAgentColors.TextMuted,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        NbgSkillCuratorMetric("需复核", summary.requiresReviewCount, Modifier.weight(1f))
        NbgSkillCuratorMetric("已归档", summary.archivedCount, Modifier.weight(1f))
        NbgSkillCuratorMetric("使用记录", summary.usageEventCount, Modifier.weight(1f))
      }
      NbgInlineActionButton(
        label = "运行复核",
        icon = Icons.Filled.Refresh,
        enabled = !busy,
        onClick = onRunReview,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NbgInlineActionButton(
          label = if (loopState.enabled) "关闭后台" else "后台复核",
          icon = if (loopState.enabled) Icons.Filled.Block else Icons.Filled.CheckCircle,
          enabled = !busy,
          onClick = { onSetLoopEnabled(!loopState.enabled) },
        )
        NbgInlineActionButton(
          label = "运行循环",
          icon = Icons.Filled.Refresh,
          enabled = !busy && loopState.enabled,
          onClick = onRunLoopNow,
        )
      }
      Text(
        text = "${loopState.statusLabel} · ${loopState.reviewCount} 次 · 已归档 ${loopState.archivedCount}",
        color = if (loopState.lastError.isNotBlank()) NbgAgentColors.StatusRed else NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (summary.mostUsedSkillName.isNotBlank()) {
        Text(
          text = "最常用：${summary.mostUsedSkillName}",
          color = NbgAgentColors.TextMuted,
          fontSize = 11.sp,
          lineHeight = 15.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (archivedSkills.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
          archivedSkills.take(3).forEach { archived ->
            NbgArchivedSkillRow(
              archived = archived,
              busy = busy,
              onRestore = { onRestore(archived.skillName) },
            )
          }
        }
      }
      Text(
        text = "Curator 只做治理提示和本地归档；不会自动删除、安装或启用 Skill。",
        color = NbgAgentColors.TextMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp,
      )
    }
  }
}

@Composable
private fun NbgArchivedSkillRow(
  archived: NbgSkillCuratorArchivedSkill,
  busy: Boolean,
  onRestore: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Archive,
        contentDescription = null,
        tint = NbgAgentColors.TextMuted,
        modifier = Modifier.size(17.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = archived.skillName,
          color = NbgAgentColors.TextStrong,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "已归档 · ${archived.useCount} 次使用记录",
          color = NbgAgentColors.TextMuted,
          fontSize = 10.5.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      NbgInlineActionButton(
        label = "恢复",
        icon = Icons.Filled.Restore,
        enabled = !busy,
        onClick = onRestore,
      )
    }
  }
}

@Composable
private fun NbgSkillCuratorMetric(label: String, value: Int, modifier: Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = value.toString(),
        color = NbgAgentColors.TextStrong,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = label,
        color = NbgAgentColors.TextMuted,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun NbgSkillsSummaryCard(
  snapshot: HanakoSkillsSnapshot,
  loading: Boolean,
  error: String?,
  busy: Boolean,
  onReload: () -> Unit,
  onReloadRuntime: () -> Unit,
  onAdd: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NbgSkillsIconBox(icon = Icons.Filled.Settings, active = snapshot.enabledCount > 0)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "技能管理",
            color = NbgAgentColors.TextStrong,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${snapshot.visibleSkills.size} 个技能 · ${snapshot.enabledCount} 已启用 · 当前 Agent: $HANA_DEFAULT_MCP_AGENT_ID",
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgSkillsStatusPill(
          text = if (loading) "读取中" else "${snapshot.enabledCount} ON",
          color = if (snapshot.enabledCount > 0) NbgAgentColors.StatusGreen else NbgAgentColors.TextMuted,
        )
      }
      if (!error.isNullOrBlank()) {
        Text(
          text = error,
          color = NbgAgentColors.StatusRed,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NbgInlineActionButton(
          label = if (loading) "读取中" else "刷新",
          icon = Icons.Filled.Refresh,
          enabled = !loading && !busy,
          onClick = onReload,
        )
        NbgInlineActionButton(
          label = "重载",
          icon = Icons.Filled.Refresh,
          enabled = !loading && !busy,
          onClick = onReloadRuntime,
        )
        NbgInlineActionButton(
          label = "添加",
          icon = Icons.Filled.Add,
          primary = true,
          enabled = !loading && !busy,
          onClick = onAdd,
        )
      }
    }
  }
}

@Composable
private fun NbgSkillBundlesCard(
  bundles: List<HanakoSkillBundle>,
  busyKey: String?,
  onCreate: () -> Unit,
  onEdit: (HanakoSkillBundle) -> Unit,
  onDelete: (HanakoSkillBundle) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NbgSkillsIconBox(icon = Icons.Filled.Settings, active = bundles.isNotEmpty())
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Skill Bundles",
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${bundles.size} 个分组 · 与 PC 端技能分组同步",
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgInlineActionButton(
          label = "新建",
          icon = Icons.Filled.Add,
          primary = true,
          enabled = busyKey == null,
          onClick = onCreate,
        )
      }
      if (bundles.isEmpty()) {
        Text(
          text = "还没有分组。创建后可以把常用 Skills 成组管理。",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
          bundles.forEach { bundle ->
            NbgSkillBundleRow(
              bundle = bundle,
              busy = busyKey == "skills:bundle:${bundle.id}" || busyKey == "skills:bundle-delete:${bundle.id}",
              enabled = busyKey == null,
              onEdit = { onEdit(bundle) },
              onDelete = { onDelete(bundle) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun NbgSkillBundleRow(
  bundle: HanakoSkillBundle,
  busy: Boolean,
  enabled: Boolean,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val skillCount = bundle.effectiveSkillNames.size
  val meta = buildString {
    append("$skillCount 个 Skill")
    if (bundle.enabledCount > 0) append(" · ${bundle.enabledCount} 已启用")
    if (bundle.missingCount > 0) append(" · ${bundle.missingCount} 缺失")
  }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(13.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = bundle.name,
            color = NbgAgentColors.TextStrong,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = if (busy) "处理中" else meta,
            color = if (bundle.missingCount > 0) NbgAgentColors.StatusRed else NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgInlineActionButton(
          label = "编辑",
          icon = Icons.Filled.Settings,
          enabled = enabled,
          onClick = onEdit,
        )
        NbgInlineActionButton(
          label = "删除",
          icon = Icons.Filled.Delete,
          enabled = enabled,
          onClick = onDelete,
        )
      }
      if (bundle.effectiveSkillNames.isNotEmpty()) {
        Text(
          text = bundle.effectiveSkillNames.take(8).joinToString(", ") +
            if (bundle.effectiveSkillNames.size > 8) " ..." else "",
          color = NbgAgentColors.TextMuted,
          fontSize = 10.sp,
          lineHeight = 14.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun NbgExternalSkillPathsCard(
  paths: HanakoExternalSkillPaths,
  busy: Boolean,
  onEdit: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NbgSkillsIconBox(icon = Icons.Filled.Settings, active = paths.visibleCount > 0)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "外部 Skill 路径",
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${paths.configured.size} 已配置 · ${paths.discovered.size} 自动发现",
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgInlineActionButton(
          label = if (busy) "保存中" else "编辑",
          icon = Icons.Filled.Settings,
          enabled = !busy,
          onClick = onEdit,
        )
      }
      val preview = paths.configured.take(2) + paths.discovered.take(2).map { it.path }
      if (preview.isEmpty()) {
        Text(
          text = "可把 HanakoPro 能访问的绝对目录加入兼容路径。",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      } else {
        preview.distinct().forEach { path ->
          Text(
            text = path,
            color = NbgAgentColors.TextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun NbgSkillBundleDialog(
  bundle: HanakoSkillBundle?,
  skills: List<HanakoSkillSummary>,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (String, List<String>) -> Unit,
) {
  var name by remember(bundle?.id) { mutableStateOf(bundle?.name.orEmpty()) }
  var skillNamesText by remember(bundle?.id) {
    mutableStateOf(bundle?.effectiveSkillNames.orEmpty().joinToString("\n"))
  }
  val parsedSkillNames = remember(skillNamesText) { nbgParseSkillNames(skillNamesText) }
  val installedNames = remember(skills) { skills.map { it.name }.toSet() }
  val unknownNames = parsedSkillNames.filter { it !in installedNames }
  val canSave = !busy && name.trim().isNotBlank() && unknownNames.isEmpty()
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = {
      Text(
        text = if (bundle == null) "新建 Bundle" else "编辑 Bundle",
        color = NbgAgentColors.TextStrong,
        fontSize = 18.sp,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 420.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("名称") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = skillNamesText,
          onValueChange = { skillNamesText = it },
          label = { Text("Skill 名称") },
          placeholder = { Text("每行一个，或用逗号分隔") },
          singleLine = false,
          minLines = 4,
          maxLines = 8,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = if (unknownNames.isEmpty()) {
            "可用：${skills.take(6).joinToString(", ") { it.name }}${if (skills.size > 6) " ..." else ""}"
          } else {
            "未安装：${unknownNames.joinToString(", ")}"
          },
          color = if (unknownNames.isEmpty()) NbgAgentColors.TextMuted else NbgAgentColors.StatusRed,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      }
    },
    confirmButton = {
      NbgDialogAction(
        label = if (busy) "保存中" else "保存",
        primary = true,
        enabled = canSave,
        onClick = { onSave(name.trim(), parsedSkillNames) },
      )
    },
    dismissButton = {
      NbgDialogAction(label = "取消", enabled = !busy, onClick = onDismiss)
    },
  )
}

@Composable
private fun NbgSkillBundleDeleteDialog(
  bundle: HanakoSkillBundle,
  busy: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("删除 Bundle", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Text(
        text = "确定删除 ${bundle.name}？只会删除分组，不会删除 Skill 文件。",
        color = NbgAgentColors.TextMuted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
      )
    },
    confirmButton = {
      NbgDialogAction(
        label = if (busy) "处理中" else "删除",
        primary = true,
        enabled = !busy,
        onClick = onConfirm,
      )
    },
    dismissButton = {
      NbgDialogAction(label = "取消", enabled = !busy, onClick = onDismiss)
    },
  )
}

@Composable
private fun NbgExternalSkillPathsDialog(
  paths: HanakoExternalSkillPaths,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (List<String>) -> Unit,
) {
  var pathText by remember(paths.configured) { mutableStateOf(paths.configured.joinToString("\n")) }
  val parsedPaths = remember(pathText) { pathText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.distinct().toList() }
  val invalidPath = parsedPaths.firstOrNull { !it.startsWith("/") }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("外部 Skill 路径", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 420.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = pathText,
          onValueChange = { pathText = it },
          label = { Text("绝对路径") },
          placeholder = { Text("/root/skills\n/root/workspace/skills") },
          singleLine = false,
          minLines = 4,
          maxLines = 8,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = invalidPath?.let { "路径必须以 / 开头：$it" }
            ?: "每行一个目录。自动发现路径只展示，不会写入配置。",
          color = if (invalidPath == null) NbgAgentColors.TextMuted else NbgAgentColors.StatusRed,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
        if (paths.discovered.isNotEmpty()) {
          Text(
            text = "自动发现",
            color = NbgAgentColors.TextStrong,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
          )
          paths.discovered.forEach { discovered ->
            Text(
              text = "${discovered.path}${if (discovered.exists) "" else " · 不存在"}",
              color = if (discovered.exists) NbgAgentColors.TextMuted else NbgAgentColors.StatusRed,
              fontSize = 10.sp,
              lineHeight = 14.sp,
              fontFamily = FontFamily.Monospace,
            )
          }
        }
      }
    },
    confirmButton = {
      NbgDialogAction(
        label = if (busy) "保存中" else "保存",
        primary = true,
        enabled = !busy && invalidPath == null,
        onClick = { onSave(parsedPaths) },
      )
    },
    dismissButton = {
      NbgDialogAction(label = "取消", enabled = !busy, onClick = onDismiss)
    },
  )
}

@Composable
private fun NbgSkillInstallDialog(
  busy: Boolean,
  onSave: (HanakoSkillInstallInput) -> Unit,
  onDismiss: () -> Unit,
) {
  var path by remember { mutableStateOf("") }
  var reviewedSource by remember { mutableStateOf("") }
  val trimmedPath = path.trim()
  val review = remember(trimmedPath) {
    trimmedPath.takeIf { it.isNotBlank() }?.let { nbgReviewSkillInstallSource(it) }
  }
  val reviewed = reviewedSource == trimmedPath && review?.allowInstall == true
  val canReview = !busy && review?.allowInstall == true
  val canSave = canReview && reviewed
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("添加 Skill", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = path,
          onValueChange = { path = it },
          label = { Text("路径或链接") },
          placeholder = { Text("/root/skills/my-skill 或 https://.../SKILL.md") },
          singleLine = false,
          minLines = 2,
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = "安装不会把未验证来源加入可信默认集合；如需启用，需要再次确认。",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
        review?.let {
          NbgSkillSourceReviewPanel(review = it, reviewed = reviewed)
        }
      }
    },
    confirmButton = {
      NbgDialogAction(
        label = when {
          busy -> "处理中"
          reviewed -> "确认安装"
          else -> "复核来源"
        },
        primary = true,
        enabled = canReview,
        onClick = {
          if (canSave) {
            onSave(HanakoSkillInstallInput(trimmedPath))
          } else {
            reviewedSource = trimmedPath
          }
        },
      )
    },
    dismissButton = {
      NbgDialogAction(label = "取消", onClick = onDismiss)
    },
  )
}

@Composable
private fun NbgSkillEnableReviewDialog(
  skill: HanakoSkillSummary,
  busy: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  val review = nbgSkillSummarySourceReview(skill)
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("确认启用 Skill", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = skill.name,
          color = NbgAgentColors.TextStrong,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        NbgSkillSourceReviewPanel(review = review, reviewed = true)
        Text(
          text = "启用后当前 Agent 可以调用这个 Skill。只在你确认来源可信时启用。",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
      }
    },
    confirmButton = {
      NbgDialogAction(
        label = if (busy) "处理中" else "确认启用",
        primary = true,
        enabled = !busy && review.allowInstall,
        onClick = onConfirm,
      )
    },
    dismissButton = {
      NbgDialogAction(label = "取消", enabled = !busy, onClick = onDismiss)
    },
  )
}

@Composable
private fun NbgSkillSourceReviewPanel(
  review: NbgSkillSourceReview,
  reviewed: Boolean,
) {
  val borderColor = when {
    !review.allowInstall -> NbgAgentColors.StatusRed
    review.requiresReview -> NbgAgentColors.PrimarySoft
    else -> NbgAgentColors.InputBorder
  }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, borderColor),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Text(
        text = "${nbgSkillSourceKindLabel(review.sourceKind)} · ${nbgSkillTrustTierLabel(review.trustTier)} · ${if (reviewed) "已复核" else "待复核"}",
        color = if (review.allowInstall) NbgAgentColors.TextStrong else NbgAgentColors.StatusRed,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (review.sourceHost.isNotBlank() || review.redactedLocation.isNotBlank()) {
        Text(
          text = listOf(review.sourceHost, review.redactedLocation).filter { it.isNotBlank() }.joinToString(" · "),
          color = NbgAgentColors.TextMuted,
          fontSize = 10.sp,
          lineHeight = 14.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = review.reason,
        color = if (review.allowInstall) NbgAgentColors.TextMuted else NbgAgentColors.StatusRed,
        fontSize = 11.sp,
        lineHeight = 15.sp,
      )
    }
  }
}

private fun nbgSkillSourceKindLabel(kind: NbgSkillSourceKind): String =
  when (kind) {
    NbgSkillSourceKind.BuiltIn -> "内置"
    NbgSkillSourceKind.UserInstalled -> "用户安装"
    NbgSkillSourceKind.LocalPath -> "本地路径"
    NbgSkillSourceKind.LocalhostHttp -> "本机链接"
    NbgSkillSourceKind.RemoteHttps -> "远程 HTTPS"
    NbgSkillSourceKind.ExternalPath -> "外部路径"
    NbgSkillSourceKind.Unknown -> "未知来源"
  }

private fun nbgSkillTrustTierLabel(tier: NbgSkillTrustTier): String =
  when (tier) {
    NbgSkillTrustTier.TrustedBundled -> "可信内置"
    NbgSkillTrustTier.UserManaged -> "用户管理"
    NbgSkillTrustTier.UnverifiedExternal -> "未验证外部"
    NbgSkillTrustTier.Blocked -> "已阻止"
  }

@Composable
private fun NbgSkillDeleteDialog(
  skill: HanakoSkillSummary,
  busy: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("删除 Skill", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Text(
        text = "确定删除 ${skill.name}？这会从 HanakoPro 用户技能目录移除它，并从 Agent 启用列表里清掉引用。",
        color = NbgAgentColors.TextMuted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
      )
    },
    confirmButton = {
      NbgDialogAction(
        label = if (busy) "处理中" else "删除",
        primary = true,
        enabled = !busy,
        onClick = onConfirm,
      )
    },
    dismissButton = {
      NbgDialogAction(label = "取消", enabled = !busy, onClick = onDismiss)
    },
  )
}

@Composable
private fun NbgSkillArchiveDialog(
  skill: HanakoSkillSummary,
  busy: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = { Text("归档 Skill", color = NbgAgentColors.TextStrong, fontSize = 18.sp) },
    text = {
      Text(
        text = "归档 ${skill.name} 只会从 Android Skills 列表隐藏它，不会删除文件，也不会改 HanakoPro 的 Skill 目录。需要重新使用时可以从 Curator 恢复。",
        color = NbgAgentColors.TextMuted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
      )
    },
    confirmButton = {
      NbgDialogAction(
        label = if (busy) "处理中" else "归档",
        primary = true,
        enabled = !busy && !skill.enabled,
        onClick = onConfirm,
      )
    },
    dismissButton = {
      NbgDialogAction(label = "取消", enabled = !busy, onClick = onDismiss)
    },
  )
}

@Composable
private fun NbgSkillDetailDialog(
  skill: HanakoSkillSummary,
  translated: Boolean,
  translatedDescription: String,
  onDismiss: () -> Unit,
) {
  val description = nbgSkillDescriptionForDisplay(skill, translated, translatedDescription)
  val path = skill.baseDir.ifBlank { skill.filePath }.ifBlank { skill.externalPath }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = skill.name,
          color = NbgAgentColors.TextStrong,
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${nbgSkillSourceLabel(skill)} · ${if (skill.enabled) "已启用" else "未启用"}",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = description.ifBlank { "没有描述" },
          color = NbgAgentColors.TextStrong,
          fontSize = 13.sp,
          lineHeight = 20.sp,
        )
        if (path.isNotBlank()) {
          Text(
            text = path,
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace,
          )
        }
      }
    },
    confirmButton = {
      NbgDialogAction(label = "关闭", primary = true, onClick = onDismiss)
    },
  )
}

@Composable
private fun NbgSkillsEmptyState() {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = "还没有可见 Skill",
        color = NbgAgentColors.TextStrong,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "添加包含 SKILL.md 的文件夹，或从 PC 端安装后在这里刷新。",
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
      )
    }
  }
}

@Composable
private fun NbgSkillRow(
  skill: HanakoSkillSummary,
  busyKey: String?,
  translated: Boolean,
  translatedDescription: String,
  translationBusy: Boolean,
  translationMessage: String?,
  onToggleTranslation: () -> Unit,
  onSetEnabled: (String, Boolean) -> Unit,
  onOpenDetail: () -> Unit,
  onArchive: () -> Unit,
  onDelete: () -> Unit,
) {
  val busy = busyKey == "skills:toggle:${skill.name}" || busyKey == "skills:delete:${skill.name}"
  val description = nbgSkillDescriptionForDisplay(skill, translated, translatedDescription)
  Surface(
    onClick = onOpenDetail,
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = if (skill.enabled) NbgAgentColors.Selected else NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, if (skill.enabled) NbgAgentColors.PrimarySoft else NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NbgSkillsIconBox(
          icon = if (skill.enabled) Icons.Filled.CheckCircle else Icons.Filled.Settings,
          active = skill.enabled,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = skill.name,
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${nbgSkillSourceLabel(skill)} · ${if (skill.enabled) "已启用" else "未启用"}",
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        NbgSkillsStatusPill(
          text = if (busy) "处理中" else if (skill.enabled) "ON" else "OFF",
          color = if (skill.enabled) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
        )
      }
      if (description.isNotBlank()) {
        Text(
          text = description,
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (!translationMessage.isNullOrBlank() && (translated || translationBusy)) {
        Text(
          text = translationMessage,
          color = if (translationMessage.contains("失败") || translationMessage.contains("没有")) {
            NbgAgentColors.StatusRed
          } else {
            NbgAgentColors.TextMuted
          },
          fontSize = 11.sp,
          lineHeight = 15.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      val path = skill.baseDir.ifBlank { skill.filePath }.ifBlank { skill.externalPath }
      if (path.isNotBlank()) {
        Text(
          text = path,
          color = NbgAgentColors.TextMuted,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NbgInlineActionButton(
          label = if (skill.enabled) "禁用" else "启用",
          icon = if (skill.enabled) Icons.Filled.Block else Icons.Filled.CheckCircle,
          primary = !skill.enabled,
          enabled = busyKey == null,
          onClick = { onSetEnabled(skill.name, !skill.enabled) },
        )
        NbgInlineActionButton(
          label = "查看",
          icon = Icons.Filled.Visibility,
          enabled = busyKey == null,
          onClick = onOpenDetail,
        )
        NbgInlineActionButton(
          label = if (translationBusy) "翻译中" else if (translated) "原文" else "翻译",
          icon = Icons.Filled.Translate,
          enabled = busyKey == null && (!translationBusy || translated),
          onClick = onToggleTranslation,
        )
        if (skill.deletable) {
          NbgInlineActionButton(
            label = "归档",
            icon = Icons.Filled.Archive,
            enabled = busyKey == null && !skill.enabled,
            onClick = onArchive,
          )
          NbgInlineActionButton(
            label = "删除",
            icon = Icons.Filled.Delete,
            enabled = busyKey == null,
            onClick = onDelete,
          )
        }
      }
    }
  }
}

@Composable
private fun NbgSkillsIconBox(icon: ImageVector, active: Boolean) {
  Box(
    modifier = Modifier
      .size(38.dp)
      .background(if (active) NbgAgentColors.PrimarySoft else NbgAgentColors.SurfaceLow, RoundedCornerShape(13.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (active) NbgAgentColors.Primary else NbgAgentColors.TextMuted,
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun NbgSkillsStatusPill(text: String, color: Color) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Text(
      text = text,
      color = color,
      fontSize = 10.sp,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
    )
  }
}

private fun nbgSkillsSubtitle(snapshot: HanakoSkillsSnapshot, loading: Boolean): String {
  if (loading) return "正在读取 HanakoPro Skills"
  return "${snapshot.visibleSkills.size} 个技能 · ${snapshot.enabledCount} 已启用"
}

private fun nbgSkillSourceLabel(skill: HanakoSkillSummary): String =
  when (skill.source) {
    "learned" -> "自学习"
    "external" -> skill.externalLabel.ifBlank { "外部" }
    "builtin" -> "内置"
    "workspace" -> "工作区"
    else -> "用户"
  }

private fun nbgSkillDescriptionForDisplay(
  skill: HanakoSkillSummary,
  translated: Boolean,
  translatedDescription: String,
): String =
  if (translated) {
    translatedDescription.ifBlank { skill.displayDescription(preferChinese = true) }
  } else {
    skill.displayDescription(preferChinese = false)
  }

private fun nbgParseSkillNames(raw: String): List<String> =
  raw
    .split('\n', ',', ';', '，', '；')
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()

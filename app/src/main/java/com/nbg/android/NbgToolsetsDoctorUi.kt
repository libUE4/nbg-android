package com.nbg.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NbgToolsetsDoctorScreen(
  preferences: NbgChatPreferences,
  capabilities: NbgCapabilityRegistry,
  sessionCount: Int,
  savedUrlApiCount: Int,
  compressionAvailable: Boolean,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onSetToolsetEnabled: (NbgToolsetId, Boolean) -> Unit,
  onOpenTerminal: () -> Unit,
  onOpenAgents: () -> Unit,
  onOpenMcp: () -> Unit,
  onOpenSkills: () -> Unit,
  onOpenMemory: () -> Unit,
  onOpenProviders: () -> Unit,
  onOpenDiagnosticsExport: () -> Unit,
) {
  val rows = remember(preferences, capabilities, sessionCount, savedUrlApiCount, compressionAvailable) {
    nbgBuildToolsetControlRows(
      preferences = preferences,
      capabilities = capabilities,
      sessionCount = sessionCount,
      savedUrlApiCount = savedUrlApiCount,
      compressionAvailable = compressionAvailable,
    )
  }
  NbgShellSubPage(
    title = "Toolsets",
    subtitle = "开关与 Doctor",
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
        NbgToolsetsDoctorSummary(
          capabilities = capabilities,
          enabledCount = rows.count { it.enabled },
          totalCount = rows.size,
        )
      }
      items(rows, key = { it.id.wireName }) { row ->
        NbgToolsetControlCard(
          row = row,
          onEnabledChange = { enabled -> onSetToolsetEnabled(row.id, enabled) },
          onAction = when (row.id) {
            NbgToolsetId.Terminal -> onOpenTerminal
            NbgToolsetId.Mcp -> onOpenMcp
            NbgToolsetId.Skills -> onOpenSkills
            NbgToolsetId.Memory -> onOpenMemory
            NbgToolsetId.AgentsTeam -> onOpenAgents
            NbgToolsetId.ExpertReview -> onOpenProviders
            NbgToolsetId.Diagnostics -> onOpenDiagnosticsExport
            else -> null
          },
        )
      }
    }
  }
}

@Composable
private fun NbgToolsetsDoctorSummary(
  capabilities: NbgCapabilityRegistry,
  enabledCount: Int,
  totalCount: Int,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.SurfaceBorder),
  ) {
    Column(
      modifier = Modifier.padding(13.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = nbgDoctorHealthColor(capabilities.worstHealth))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = capabilities.summaryLabel,
            color = NbgAgentColors.TextStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "$enabledCount/$totalCount toolsets enabled",
            color = NbgAgentColors.TextMuted,
            fontSize = 12.sp,
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        NbgToolsetsMiniMetric("正常", capabilities.capabilities.count { it.health == NbgCapabilityHealth.Healthy }, Modifier.weight(1f))
        NbgToolsetsMiniMetric("注意", capabilities.capabilities.count { it.health == NbgCapabilityHealth.Degraded }, Modifier.weight(1f))
        NbgToolsetsMiniMetric("失败", capabilities.capabilities.count { it.health == NbgCapabilityHealth.Failed }, Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun NbgToolsetControlCard(
  row: NbgToolsetControlRow,
  onEnabledChange: (Boolean) -> Unit,
  onAction: (() -> Unit)?,
) {
  val healthColor = nbgDoctorHealthColor(row.health)
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = if (row.enabled) NbgAgentColors.SurfaceContainer else NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, if (row.enabled) NbgAgentColors.InputBorder else NbgAgentColors.SurfaceBorder),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = healthColor.copy(alpha = 0.14f),
          contentColor = healthColor,
        ) {
          Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = null,
            modifier = Modifier.padding(8.dp),
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = row.id.label,
            color = NbgAgentColors.TextStrong,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = row.status,
            color = healthColor,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Switch(
          checked = row.enabled,
          onCheckedChange = onEnabledChange,
        )
      }
      Text(
        text = row.detail,
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
      )
      if (row.actionLabel.isNotBlank() && onAction != null) {
        TextButton(
          enabled = row.enabled,
          onClick = onAction,
        ) {
          Text(row.actionLabel)
        }
      }
    }
  }
}

@Composable
private fun NbgToolsetsMiniMetric(
  label: String,
  value: Int,
  modifier: Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(text = value.toString(), color = NbgAgentColors.TextStrong, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Text(text = label, color = NbgAgentColors.TextMuted, fontSize = 10.5.sp)
    }
  }
}

private fun nbgDoctorHealthColor(health: NbgCapabilityHealth) =
  when (health) {
    NbgCapabilityHealth.Healthy -> NbgAgentColors.StatusGreen
    NbgCapabilityHealth.Unknown -> NbgAgentColors.StatusYellow
    NbgCapabilityHealth.Degraded -> NbgAgentColors.Primary
    NbgCapabilityHealth.Failed -> NbgAgentColors.StatusRed
  }

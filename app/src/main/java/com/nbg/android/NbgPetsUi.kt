package com.nbg.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Sparkles

@Composable
internal fun NbgPetsScreen(
  state: NbgPetStoreState,
  manifest: NbgPetDexManifest?,
  loading: Boolean,
  installingSlug: String?,
  error: String?,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onReloadManifest: () -> Unit,
  onSelectPet: (String) -> Unit,
  onDeletePet: (String) -> Unit,
  onSetHidden: (Boolean) -> Unit,
  onInstallPet: (NbgPetDexManifestPet) -> Unit,
) {
  var addOpen by remember { mutableStateOf(false) }
  NbgShellSubPage(
    title = "桌宠",
    subtitle = if (state.hidden) "已隐藏 · ${state.installedPets.size} 个已安装" else "${state.currentPet.displayName} · ${state.installedPets.size} 个已安装",
    onBack = onBack,
    onOpenDrawer = onOpenDrawer,
    actions = {
      NbgPlainIconButton(Icons.Filled.Add, "添加桌宠", onClick = { addOpen = true })
    },
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(NbgAgentColors.Background),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        NbgPetsCurrentCard(
          state = state,
          onSetHidden = onSetHidden,
        )
      }
      item {
        NbgPetActionLibrary(pet = state.currentPet)
      }
      item {
        Text(
          text = "已安装",
          color = NbgAgentColors.TextStrong,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
      }
      items(state.installedPets, key = { it.slug }) { pet ->
        NbgInstalledPetRow(
          pet = pet,
          selected = pet.slug == state.currentPetSlug,
          onSelect = { onSelectPet(pet.slug) },
          onDelete = { onDeletePet(pet.slug) },
        )
      }
    }
  }
  if (addOpen) {
    NbgPetDexDialog(
      manifest = manifest,
      loading = loading,
      installingSlug = installingSlug,
      error = error,
      installedSlugs = state.installedPets.map { it.slug }.toSet(),
      onReload = onReloadManifest,
      onInstall = onInstallPet,
      onDismiss = { addOpen = false },
    )
  }
}

@Composable
private fun NbgPetsCurrentCard(
  state: NbgPetStoreState,
  onSetHidden: (Boolean) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      NbgPetPreview(
        pet = state.currentPet,
        state = NbgPetdex002State.Idle,
        animated = false,
        modifier = Modifier.size(width = 78.dp, height = 84.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = state.currentPet.displayName,
          color = NbgAgentColors.TextStrong,
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${nbgPetKindLabel(state.currentPet.kind)} · ${state.currentPet.submittedBy.ifBlank { state.currentPet.source }}",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NbgMiniTextButton(if (state.hidden) "显示" else "隐藏") { onSetHidden(!state.hidden) }
      }
    }
  }
}

@Composable
private fun NbgPetActionLibrary(pet: NbgPetSummary) {
  val actions = listOf(
    "待机" to NbgPetdex002State.Idle,
    "挥手" to NbgPetdex002State.Waving,
    "奔跑" to NbgPetdex002State.Running,
    "失败" to NbgPetdex002State.Failed,
    "观察" to NbgPetdex002State.Review,
    "跳跃" to NbgPetdex002State.Jumping,
    "等待" to NbgPetdex002State.Waiting,
    "左跑" to NbgPetdex002State.RunningLeft,
  )
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("动作库", color = NbgAgentColors.TextStrong, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { (label, state) ->
              Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = NbgAgentColors.SurfaceContainer,
                border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
              ) {
                Column(
                  modifier = Modifier.padding(8.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  NbgPetPreview(pet = pet, state = state, modifier = Modifier.size(width = 68.dp, height = 74.dp))
                  Text(label, color = NbgAgentColors.TextMuted, fontSize = 11.sp, maxLines = 1)
                }
              }
            }
            if (row.size == 1) Box(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun NbgInstalledPetRow(
  pet: NbgPetSummary,
  selected: Boolean,
  onSelect: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = if (selected) NbgAgentColors.Selected else NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, if (selected) NbgAgentColors.PrimarySoft else NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      NbgPetPreview(
        pet = pet,
        state = NbgPetdex002State.Idle,
        animated = false,
        modifier = Modifier.size(width = 48.dp, height = 52.dp),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(pet.displayName, color = NbgAgentColors.TextStrong, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${nbgPetKindLabel(pet.kind)} · ${pet.submittedBy.ifBlank { pet.source }}", color = NbgAgentColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      if (selected) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NbgAgentColors.Primary, modifier = Modifier.size(20.dp))
      } else {
        NbgMiniTextButton("使用", onClick = onSelect)
      }
      if (!pet.builtIn && !selected) {
        NbgPlainIconButton(Icons.Filled.Delete, "删除桌宠", onDelete)
      }
    }
  }
}

@Composable
private fun NbgPetDexDialog(
  manifest: NbgPetDexManifest?,
  loading: Boolean,
  installingSlug: String?,
  error: String?,
  installedSlugs: Set<String>,
  onReload: () -> Unit,
  onInstall: (NbgPetDexManifestPet) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val previewStore = remember { NbgPetDexPreviewStore() }
  val pets = remember(manifest, query) {
    nbgFilterPetDexPets(manifest?.pets.orEmpty(), query, characterOnly = true)
  }
  val matchCount = remember(manifest, query) {
    nbgCountPetDexMatches(manifest?.pets.orEmpty(), query, characterOnly = true)
  }
  LaunchedEffect(Unit) {
    if (manifest == null && !loading) onReload()
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {},
    dismissButton = {
      NbgMiniTextButton("关闭", onClick = onDismiss)
    },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("添加桌宠", color = NbgAgentColors.TextStrong)
        NbgPlainIconButton(Icons.Filled.Refresh, "刷新 PetDex", onReload)
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NbgPetSearchField(value = query, onValueChange = { query = it })
        if (error != null) {
          Text(error, color = NbgAgentColors.StatusRed, fontSize = 12.sp)
        }
        Text(
          text = if (loading) {
            "正在加载 PetDex..."
          } else {
            "显示 ${pets.size} / 匹配 ${matchCount} · 全部 ${manifest?.total ?: 0}"
          },
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
        )
        LazyColumn(
          modifier = Modifier.height(420.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(pets, key = { it.slug }) { pet ->
            NbgPetDexRow(
              pet = pet,
              previewStore = previewStore,
              installed = pet.slug in installedSlugs,
              installing = installingSlug == pet.slug,
              onInstall = { onInstall(pet) },
            )
          }
        }
      }
    },
    containerColor = NbgAgentColors.SurfaceContainerHigh,
    titleContentColor = NbgAgentColors.TextStrong,
    textContentColor = NbgAgentColors.TextStrong,
  )
}

@Composable
private fun NbgPetDexRow(
  pet: NbgPetDexManifestPet,
  previewStore: NbgPetDexPreviewStore,
  installed: Boolean,
  installing: Boolean,
  onInstall: () -> Unit,
) {
  var preview by remember(pet.slug) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(pet.slug, pet.spritesheetUrl) {
    preview = previewStore.loadPreview(pet)
  }
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Box(
        modifier = Modifier
          .size(width = 48.dp, height = 52.dp)
          .background(NbgAgentColors.SurfaceContainerHigh, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
      ) {
        if (preview != null) {
          NbgPetDexStaticPreview(
            preview = preview,
            modifier = Modifier.size(width = 44.dp, height = 48.dp),
          )
        } else {
          Icon(HugeIcons.Sparkles, contentDescription = null, tint = NbgAgentColors.Primary, modifier = Modifier.size(18.dp))
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(pet.displayName, color = NbgAgentColors.TextStrong, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${nbgPetKindLabel(pet.kind)} · ${pet.submittedBy}", color = NbgAgentColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      NbgMiniTextButton(
        text = when {
          installed -> "已安装"
          installing -> "安装中"
          else -> "安装"
        },
        enabled = !installed && !installing,
        onClick = onInstall,
      )
    }
  }
}

@Composable
private fun NbgPetDexStaticPreview(
  preview: ImageBitmap?,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    if (preview != null) {
      drawImage(
        image = preview,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(preview.width, preview.height),
        dstOffset = IntOffset(0, 0),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.None,
      )
    }
  }
}

@Composable
private fun NbgPetSearchField(
  value: String,
  onValueChange: (String) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = NbgAgentColors.SurfaceContainer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(Icons.Filled.Search, contentDescription = null, tint = NbgAgentColors.TextMuted, modifier = Modifier.size(18.dp))
      Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (value.isBlank()) Text("搜索角色、生物、作者", color = NbgAgentColors.TextMuted, fontSize = 13.sp)
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          singleLine = true,
          textStyle = TextStyle(color = NbgAgentColors.TextStrong, fontSize = 13.sp, fontFamily = nbgCurrentFontFamily()),
          cursorBrush = SolidColor(NbgAgentColors.Primary),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun NbgMiniTextButton(
  text: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  NbgPressBox(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.semantics { contentDescription = text },
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = NbgAgentColors.SurfaceContainerHigh,
      border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
    ) {
      Text(
        text = text,
        color = if (enabled) NbgAgentColors.TextStrong else NbgAgentColors.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
      )
    }
  }
}

@Composable
internal fun NbgPetPreview(
  pet: NbgPetSummary,
  state: NbgPetdex002State,
  animated: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val spritesheet = nbgRememberPetSpritesheet(pet)
  var frame by remember(state) { mutableIntStateOf(0) }
  if (animated) {
    LaunchedEffect(state) {
      while (true) {
        delay(state.frameDelayMs)
        frame = (frame + 1) % state.frames
      }
    }
  }
  val visibleFrame = if (animated) frame else 0
  Canvas(modifier = modifier) {
    drawCircle(
      color = NbgAgentColors.PrimarySoft.copy(alpha = 0.14f),
      radius = size.minDimension * 0.34f,
      center = Offset(size.width * 0.5f, size.height * 0.58f),
    )
    drawImage(
      image = spritesheet,
      srcOffset = IntOffset((visibleFrame % NBG_PET_COLUMNS) * NBG_PET_FRAME_WIDTH_PX, state.row * NBG_PET_FRAME_HEIGHT_PX),
      srcSize = IntSize(NBG_PET_FRAME_WIDTH_PX, NBG_PET_FRAME_HEIGHT_PX),
      dstOffset = IntOffset(0, 0),
      dstSize = IntSize(size.width.toInt(), size.height.toInt()),
      filterQuality = FilterQuality.None,
    )
  }
}

private fun nbgPetKindLabel(kind: String): String =
  when (kind.lowercase()) {
    "character" -> "角色"
    "creature" -> "生物"
    "object" -> "物件"
    else -> kind.ifBlank { "桌宠" }
  }

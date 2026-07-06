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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NbgMemoryScreen(
  state: HanakoMemoryState,
  loading: Boolean,
  error: String?,
  busyKey: String?,
  onBack: () -> Unit,
  onOpenDrawer: () -> Unit,
  onReload: (String, String) -> Unit,
  onSave: (HanakoMemoryInput, String, String) -> Unit,
  onDelete: (String, String, String) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  var type by remember { mutableStateOf("") }
  var editorItem by remember { mutableStateOf<HanakoMemoryItem?>(null) }
  var addOpen by remember { mutableStateOf(false) }
  var deleteTarget by remember { mutableStateOf<HanakoMemoryItem?>(null) }

  LaunchedEffect(query, type) {
    onReload(query, type)
  }

  NbgShellSubPage(
    title = "Memory",
    subtitle = nbgMemorySubtitle(state, loading),
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
        NbgMemorySummaryCard(
          state = state,
          loading = loading,
          error = error,
          busy = busyKey != null,
          query = query,
          type = type,
          onQueryChange = { query = it },
          onTypeChange = { type = it },
          onReload = { onReload(query, type) },
          onAdd = { addOpen = true },
        )
      }
      if (state.items.isEmpty()) {
        item { NbgMemoryEmptyState() }
      } else {
        items(state.items, key = { it.id }) { item ->
          NbgMemoryItemCard(
            item = item,
            busy = busyKey != null,
            onEdit = { editorItem = item },
            onDelete = { deleteTarget = item },
          )
        }
      }
    }
  }

  if (addOpen) {
    NbgMemoryEditorDialog(
      item = null,
      busy = busyKey != null,
      onDismiss = { if (busyKey == null) addOpen = false },
      onSave = {
        addOpen = false
        onSave(it, query, type)
      },
    )
  }
  editorItem?.let { item ->
    NbgMemoryEditorDialog(
      item = item,
      busy = busyKey != null,
      onDismiss = { if (busyKey == null) editorItem = null },
      onSave = {
        editorItem = null
        onSave(it, query, type)
      },
    )
  }
  deleteTarget?.let { item ->
    AlertDialog(
      onDismissRequest = { if (busyKey == null) deleteTarget = null },
      confirmButton = {
        TextButton(
          enabled = busyKey == null,
          onClick = {
            deleteTarget = null
            onDelete(item.id, query, type)
          },
        ) { Text("删除", color = NbgAgentColors.StatusRed) }
      },
      dismissButton = {
        TextButton(enabled = busyKey == null, onClick = { deleteTarget = null }) { Text("取消") }
      },
      title = { Text("删除记忆") },
      text = { Text("这条记忆会从本地 Memory 中移除。") },
      containerColor = NbgAgentColors.Drawer,
    )
  }
}

@Composable
private fun NbgMemorySummaryCard(
  state: HanakoMemoryState,
  loading: Boolean,
  error: String?,
  busy: Boolean,
  query: String,
  type: String,
  onQueryChange: (String) -> Unit,
  onTypeChange: (String) -> Unit,
  onReload: () -> Unit,
  onAdd: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.SurfaceBorder),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy, onClick = onAdd) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Text("添加")
        }
        IconButton(enabled = !busy && !loading, onClick = onReload) {
          Icon(Icons.Filled.Refresh, contentDescription = "刷新")
        }
      }
      Text(
        text = "${state.enabledCount} 启用 · ${state.count} 总数",
        color = NbgAgentColors.TextMuted,
        fontSize = 12.sp,
      )
      OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = query,
        onValueChange = onQueryChange,
        label = { Text("搜索记忆") },
        singleLine = true,
      )
      NbgMemoryTypeFilterChips(selectedType = type, onTypeChange = onTypeChange)
      error?.takeIf { it.isNotBlank() }?.let {
        Text(text = it, color = NbgAgentColors.StatusRed, fontSize = 12.sp)
      }
    }
  }
}

@Composable
private fun NbgMemoryItemCard(
  item: HanakoMemoryItem,
  busy: Boolean,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = if (item.enabled) NbgAgentColors.SurfaceContainer else NbgAgentColors.SurfaceLow,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Column(
      modifier = Modifier.padding(13.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(
            text = item.displayTitle,
            color = NbgAgentColors.TextStrong,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = nbgMemoryTypeLabel(item.type),
            color = NbgAgentColors.TextMuted,
            fontSize = 11.sp,
          )
        }
        IconButton(enabled = !busy, onClick = onEdit) {
          Icon(Icons.Filled.Edit, contentDescription = "编辑")
        }
        IconButton(enabled = !busy, onClick = onDelete) {
          Icon(Icons.Filled.Delete, contentDescription = "删除", tint = NbgAgentColors.StatusRed)
        }
      }
      Text(
        text = item.content,
        color = NbgAgentColors.TextStrong,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        maxLines = 5,
        overflow = TextOverflow.Ellipsis,
      )
      if (item.tags.isNotEmpty()) {
        Text(
          text = item.tags.joinToString("  ") { "#$it" },
          color = NbgAgentColors.Primary,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun NbgMemoryEditorDialog(
  item: HanakoMemoryItem?,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (HanakoMemoryInput) -> Unit,
) {
  var title by remember(item?.id) { mutableStateOf(item?.title.orEmpty()) }
  var content by remember(item?.id) { mutableStateOf(item?.content.orEmpty()) }
  var type by remember(item?.id) { mutableStateOf(item?.type ?: "project_fact") }
  var tags by remember(item?.id) { mutableStateOf(item?.tags.orEmpty().joinToString(", ")) }

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        enabled = !busy && content.trim().isNotBlank(),
        onClick = {
          onSave(
            HanakoMemoryInput(
              id = item?.id.orEmpty(),
              type = type,
              title = title,
              content = content,
              tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
              enabled = item?.enabled ?: true,
            ),
          )
        },
      ) { Text("保存") }
    },
    dismissButton = {
      TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") }
    },
    title = { Text(if (item == null) "添加记忆" else "编辑记忆") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true)
        OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("内容") }, minLines = 4)
        OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("标签，用逗号分隔") }, singleLine = true)
        NbgMemoryTypeEditorChips(selectedType = type, onTypeChange = { type = it })
      }
    },
    containerColor = NbgAgentColors.Drawer,
  )
}

@Composable
private fun NbgMemoryTypeFilterChips(
  selectedType: String,
  onTypeChange: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    listOf("" to "全部")
      .plus(HANA_MEMORY_TYPES.map { it to nbgMemoryTypeLabel(it) })
      .chunked(3)
      .forEach { rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
          rowItems.forEach { (value, label) ->
            FilterChip(
              selected = selectedType == value,
              onClick = { onTypeChange(if (selectedType == value && value.isNotBlank()) "" else value) },
              label = { Text(label) },
            )
          }
        }
      }
  }
}

@Composable
private fun NbgMemoryTypeEditorChips(
  selectedType: String,
  onTypeChange: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    HANA_MEMORY_TYPES.chunked(3).forEach { rowItems ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        rowItems.forEach { value ->
          FilterChip(
            selected = selectedType == value,
            onClick = { onTypeChange(value) },
            label = { Text(nbgMemoryTypeLabel(value)) },
          )
        }
      }
    }
  }
}

@Composable
private fun NbgMemoryEmptyState() {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = NbgAgentColors.Drawer,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder),
  ) {
    Text(
      modifier = Modifier.padding(16.dp),
      text = "暂无本地记忆。保存后可用于恢复项目事实、偏好和技术决策。",
      color = NbgAgentColors.TextMuted,
      fontSize = 12.5.sp,
      lineHeight = 17.sp,
    )
  }
}

private fun nbgMemorySubtitle(state: HanakoMemoryState, loading: Boolean): String =
  if (loading) "正在读取本地记忆" else "${state.enabledCount} 启用 · ${state.count} 总数"

package com.nbg.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NbgDiagnosticsExportDialog(
  exportText: String,
  onCopy: () -> Unit,
  onShare: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NbgAgentColors.Drawer,
    title = {
      Text("诊断导出", color = NbgAgentColors.TextStrong, fontSize = 18.sp)
    },
    text = {
      val runtimePatchSummary = remember(exportText) { nbgDiagnosticsRuntimePatchUiSummary(exportText) }
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "已排除 API Key、Token、FTP 密码、终端输出、对话正文、Memory 内容和用户代码。",
          color = NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
        Text(
          text = runtimePatchSummary.text,
          color = if (runtimePatchSummary.needsAttention) NbgAgentColors.StatusYellow else NbgAgentColors.TextMuted,
          fontSize = 12.sp,
          lineHeight = 17.sp,
        )
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
          shape = RoundedCornerShape(12.dp),
          color = NbgAgentColors.CodeBlock,
          border = BorderStroke(1.dp, NbgAgentColors.SurfaceBorder),
        ) {
          SelectionContainer {
            Text(
              text = exportText,
              color = NbgAgentColors.CodeText,
              fontSize = 10.5.sp,
              lineHeight = 14.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            )
          }
        }
      }
    },
    confirmButton = {
      NbgDialogAction(label = "分享", primary = true, onClick = onShare)
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NbgDialogAction(label = "复制", onClick = onCopy)
        NbgDialogAction(label = "关闭", onClick = onDismiss)
      }
    },
  )
}

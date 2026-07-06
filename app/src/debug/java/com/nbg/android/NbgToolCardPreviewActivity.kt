package com.nbg.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class NbgToolCardPreviewActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      NbgToolCardPreviewScreen()
    }
  }
}

@Composable
private fun NbgToolCardPreviewScreen() {
  NbgThemeProvider(themeId = "light", fontId = "system") {
    MaterialTheme(
      colorScheme = nbgMaterialColorScheme(nbgThemePalette("light")),
      typography = nbgTypography(nbgFontOption("system")),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(NbgAgentColors.Background)
          .padding(16.dp),
      ) {
        NbgToolStatusCard(
          tool = HanakoToolStatus(
            key = "ui-smoke-terminal",
            kind = "terminal",
            toolName = "shell",
            title = "Gradle smoke",
            subtitle = "completed",
            detail = "exit_code=0",
            status = "completed",
            running = false,
            success = true,
            terminalOutput = HanakoTerminalOutput(
              sessionId = "ui-smoke",
              title = "./gradlew :app:assembleDebug",
              output = "BUILD SUCCESSFUL",
              exitCode = 0,
              alive = false,
              staticOutput = true,
            ),
          ),
        )
      }
    }
  }
}

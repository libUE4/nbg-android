package com.nbg.android

import android.graphics.Bitmap
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class NbgUiSmokeTest {
  @get:Rule
  val compose = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val artifacts: TestWatcher = NbgUiSmokeArtifactsRule(compose)

  @Test
  fun launchShowsChatInput() {
    compose.waitForIdle()

    compose.onNodeWithContentDescription("消息输入").assertIsDisplayed()
    compose.onNodeWithContentDescription("打开会话抽屉").assertIsDisplayed()
  }

  @Test
  fun drawerSettingsExposeCoreEntrypoints() {
    compose.openWorkbenchSettings()

    listOf("终端", "MCP 服务", "Skills", "诊断导出").forEach { label ->
      compose.onNodeWithContentDescription(label).assertIsDisplayed()
    }
  }

  @Test
  fun drawerCanOpenTerminalSkillsMcpAndDiagnostics() {
    compose.openWorkbenchSettings()
    compose.clickWorkbenchEntry("终端")
    compose.onNodeWithContentDescription("返回聊天").assertIsDisplayed()
    compose.onNodeWithContentDescription("返回聊天").performClick()

    compose.openWorkbenchSettings()
    compose.clickWorkbenchEntry("Skills")
    compose.onNodeWithText("技能管理").assertIsDisplayed()
    compose.onNodeWithContentDescription("返回聊天").performClick()

    compose.openWorkbenchSettings()
    compose.clickWorkbenchEntry("MCP 服务")
    compose.onNodeWithText("MCP 服务").assertIsDisplayed()
    compose.onNodeWithContentDescription("返回聊天").performClick()

    compose.openWorkbenchSettings()
    compose.clickWorkbenchEntry("诊断导出")
    compose.onNodeWithText("诊断导出").assertIsDisplayed()
    compose.onNodeWithText("关闭").performClick()
  }

  @OptIn(ExperimentalTestApi::class)
  private fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openWorkbenchSettings() {
    onNodeWithContentDescription("打开会话抽屉").performClick()
    waitUntilAtLeastOneExists(hasContentDescription("工作台设置"), timeoutMillis = 5_000)
    onNodeWithContentDescription("工作台设置").performClick()
    waitUntilAtLeastOneExists(hasText("工作台设置"), timeoutMillis = 5_000)
  }

  @OptIn(ExperimentalTestApi::class)
  private fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.clickWorkbenchEntry(label: String) {
    waitUntilAtLeastOneExists(hasContentDescription(label), timeoutMillis = 5_000)
    onNodeWithContentDescription(label).performScrollTo()
    onNodeWithContentDescription(label).assertIsDisplayed()
    onNodeWithContentDescription(label).performClick()
  }
}

@RunWith(AndroidJUnit4::class)
class NbgToolCardUiSmokeTest {
  @get:Rule
  val compose = createAndroidComposeRule<NbgToolCardPreviewActivity>()

  @get:Rule
  val artifacts: TestWatcher = NbgUiSmokeArtifactsRule(compose)

  @Test
  fun toolCardSurfaceIsSemanticallyVisible() {
    compose.waitForIdle()

    compose.onNodeWithText("Gradle smoke").assertIsDisplayed()
    compose.onNodeWithText("./gradlew :app:assembleDebug").assertIsDisplayed()
  }
}

private class NbgUiSmokeArtifactsRule<A : ComponentActivity>(
  private val compose: AndroidComposeTestRule<*, A>,
) : TestWatcher() {
  override fun failed(error: Throwable, description: Description) {
    val dir = File(
      InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
      "ui-smoke",
    ).apply { mkdirs() }
    val name = description.methodName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    val log = File(dir, "$name.txt")
    log.writeText(
      buildString {
        appendLine("test=${description.className}.${description.methodName}")
        appendLine("error=${error::class.java.name}: ${error.message}")
      },
    )
    runCatching {
      val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
      FileOutputStream(File(dir, "$name.png")).use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
      }
    }
  }
}

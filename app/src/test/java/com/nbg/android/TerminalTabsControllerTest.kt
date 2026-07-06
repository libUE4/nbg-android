package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTabsControllerTest {
  @Test
  fun startsWithOneTerminalTabSelected() {
    val controller = TerminalTabsController()

    assertEquals(1, controller.tabs.size)
    assertEquals("终端 1", controller.tabs.single().title)
    assertEquals(0, controller.selectedIndex)
  }

  @Test
  fun addsNewTerminalTabsAndSelectsThem() {
    val controller = TerminalTabsController()

    controller.addTab()

    assertEquals(listOf("终端 1", "终端 2"), controller.tabs.map { it.title })
    assertEquals(1, controller.selectedIndex)
  }

  @Test
  fun updatesStatusForSelectedTabWithoutChangingOtherTabs() {
    val controller = TerminalTabsController()
    controller.addTab()

    controller.updateSelectedStatus("已就绪")
    controller.select(0)
    controller.updateSelectedStatus("安装中")

    assertEquals(listOf("安装中", "已就绪"), controller.tabs.map { it.status })
  }

  @Test
  fun updatesStatusByIndexWithoutChangingSelection() {
    val controller = TerminalTabsController()
    controller.addTab()
    controller.select(0)

    val updated = controller.updateStatus(1, "启动中")

    assertEquals(true, updated)
    assertEquals(0, controller.selectedIndex)
    assertEquals(listOf("准备中", "启动中"), controller.tabs.map { it.status })
  }

  @Test
  fun renamesSelectedTab() {
    val controller = TerminalTabsController()

    controller.renameSelected("Ubuntu")

    assertEquals("Ubuntu", controller.tabs.single().title)
  }

  @Test
  fun renamesTabByIndexWithoutChangingSelection() {
    val controller = TerminalTabsController()
    controller.addTab()
    controller.select(0)

    controller.rename(1, "后台终端")

    assertEquals(listOf("终端 1", "后台终端"), controller.tabs.map { it.title })
    assertEquals(0, controller.selectedIndex)
  }

  @Test
  fun ignoresBlankRename() {
    val controller = TerminalTabsController()

    val renamed = controller.rename(0, "   ")

    assertEquals(false, renamed)
    assertEquals("终端 1", controller.tabs.single().title)
  }

  @Test
  fun deletesSelectedTabAndKeepsAtLeastOneTerminal() {
    val controller = TerminalTabsController()
    controller.addTab()

    controller.deleteSelected()

    assertEquals(listOf("终端 1"), controller.tabs.map { it.title })
    assertEquals(0, controller.selectedIndex)

    controller.deleteSelected()

    assertEquals(listOf("终端 1"), controller.tabs.map { it.title })
    assertEquals(0, controller.selectedIndex)
  }
}

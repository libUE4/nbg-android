package com.nbg.android

data class TerminalTab(
  val title: String,
  val status: String = "准备中",
)

class TerminalTabsController {
  private val mutableTabs = mutableListOf(TerminalTab("终端 1"))
  private var nextTerminalNumber = 2

  val tabs: List<TerminalTab>
    get() = mutableTabs.toList()

  var selectedIndex: Int = 0
    private set

  fun addTab() {
    mutableTabs += TerminalTab("终端 $nextTerminalNumber")
    nextTerminalNumber += 1
    selectedIndex = mutableTabs.lastIndex
  }

  fun select(index: Int) {
    selectedIndex = index.coerceIn(0, mutableTabs.lastIndex)
  }

  fun updateSelectedStatus(status: String) {
    updateStatus(selectedIndex, status)
  }

  fun updateStatus(index: Int, status: String): Boolean {
    if (index !in mutableTabs.indices) return false
    mutableTabs[index] = mutableTabs[index].copy(status = status)
    return true
  }

  fun renameSelected(title: String) {
    rename(selectedIndex, title)
  }

  fun rename(index: Int, title: String): Boolean {
    if (index !in mutableTabs.indices) return false
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return false
    mutableTabs[index] = mutableTabs[index].copy(title = trimmed)
    return true
  }

  fun deleteSelected(): Boolean {
    if (mutableTabs.size == 1) return false
    mutableTabs.removeAt(selectedIndex)
    selectedIndex = selectedIndex.coerceAtMost(mutableTabs.lastIndex)
    return true
  }
}

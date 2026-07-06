package com.nbg.android.terminal

data class TerminalRuntimeLibrary(
  val packagedName: String,
  val runtimeName: String,
) {
  companion object {
    val prootSharedLibraries = listOf(
      TerminalRuntimeLibrary("liblibtalloc.so.2.so", "libtalloc.so.2"),
    )
  }
}

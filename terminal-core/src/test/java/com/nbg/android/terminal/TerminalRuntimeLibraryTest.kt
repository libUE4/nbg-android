package com.nbg.android.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalRuntimeLibraryTest {
  @Test
  fun runtimeLibrariesIncludeProotTallocSoname() {
    assertEquals(
      listOf(TerminalRuntimeLibrary("liblibtalloc.so.2.so", "libtalloc.so.2")),
      TerminalRuntimeLibrary.prootSharedLibraries,
    )
  }
}

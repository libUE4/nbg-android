package com.nbg.android.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class NbgCodeRuntimeTest {
  @Test
  fun runtimeAssetNamesMatchAndroidPackagedRuntime() {
    assertEquals("librg.so", NbgCodeRuntime.ripgrepLibraryName)
    assertEquals("node-v24-linux-arm64.tar.xz", NbgCodeRuntime.nodeArchiveAssetName)
    assertEquals("f3d5a797b5d210ce8e2cb265544c8e482eaedcb8aa409a8b46da7e8595d0dda0", NbgCodeRuntime.nodeArchiveSha256)
    assertEquals("ubuntu-noble-aarch64-pd-v4.18.0.tar.xz", UbuntuBootstrapScript.UBUNTU_ARCHIVE)
    assertEquals("91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa", UbuntuBootstrapScript.UBUNTU_ARCHIVE_SHA256)
    assertEquals(
      "ubuntu-noble-aarch64-pd-v4.18.0.tar.xz-91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa",
      UbuntuBootstrapScript.UBUNTU_ARCHIVE_VERSION,
    )
    assertEquals(
      "node-v24.15.0-linux-arm64-f3d5a797b5d210ce8e2cb265544c8e482eaedcb8aa409a8b46da7e8595d0dda0",
      NbgCodeRuntime.nodeArchiveVersion,
    )
  }
}

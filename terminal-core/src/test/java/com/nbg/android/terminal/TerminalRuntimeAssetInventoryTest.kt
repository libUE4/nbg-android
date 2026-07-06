package com.nbg.android.terminal

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalRuntimeAssetInventoryTest {
  @Test
  fun sourceRuntimeAssetsMatchChecksumInventory() {
    val root = repoRoot()
    val expected = expectedRuntimeAssets()
    val expectedPaths = expected.map { it.relativePath }.toSet()
    val actualPaths = buildList {
      addAll(root.resolve("terminal-core/src/main/assets").listFiles().orEmpty().map { it.relativeTo(root).invariantSeparatorsPath })
      addAll(root.resolve("terminal-core/src/main/jniLibs/arm64-v8a").listFiles().orEmpty().map { it.relativeTo(root).invariantSeparatorsPath })
    }.toSet()

    assertEquals(expectedPaths, actualPaths)
    expected.forEach { asset ->
      val file = root.resolve(asset.relativePath)
      assertTrue("Missing terminal runtime asset: ${asset.relativePath}", file.isFile)
      assertEquals("Unexpected size for ${asset.relativePath}", asset.sizeBytes, file.length())
      assertEquals("Unexpected sha256 for ${asset.relativePath}", asset.sha256, file.sha256Hex())
    }
  }

  @Test
  fun runbookInventoryTableExactlyMatchesRuntimeAssets() {
    val root = repoRoot()
    val runbook = root.resolve("docs/runbooks/terminal-runtime-asset-checksum-inventory.md").readText()

    assertEquals(expectedRuntimeAssets(), runbookInventoryAssets(runbook))
  }

  private fun expectedRuntimeAssets(): List<ExpectedRuntimeAsset> = listOf(
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/assets/node-v24-linux-arm64.tar.xz",
      sizeBytes = 30_108_656L,
      sha256 = "f3d5a797b5d210ce8e2cb265544c8e482eaedcb8aa409a8b46da7e8595d0dda0",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/assets/setup_fake_sysdata.sh",
      sizeBytes = 7_356L,
      sha256 = "b1f2760d1c187132b4c02981650087ec0a927adb3b606d49a4d895884712b308",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/assets/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz",
      sizeBytes = 64_133_552L,
      sha256 = "91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/jniLibs/arm64-v8a/libbash.so",
      sizeBytes = 2_026_008L,
      sha256 = "14a123925907fbbd1bf2e28cf0fc4f611efd599f780e2fd5c1e207467dd43b3f",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/jniLibs/arm64-v8a/libbusybox.so",
      sizeBytes = 1_498_688L,
      sha256 = "c629fce4b0dd3ba9775f851d0941e74582115f423258d3a79800f2bd11d30f5c",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/jniLibs/arm64-v8a/liblibtalloc.so.2.so",
      sizeBytes = 31_128L,
      sha256 = "5f191acfd274e3bda67808d6df514f1f97fbd5c8b5b0d11e734ca04da36487ff",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/jniLibs/arm64-v8a/libloader.so",
      sizeBytes = 5_608L,
      sha256 = "05e04d87632546506eb03b6c72428f5f0cf5c989d4a629d2648c40a4bf67d45f",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/jniLibs/arm64-v8a/libnbgpty.so",
      sizeBytes = 7_400L,
      sha256 = "3874a30defb85dc7c0007807be93da8cf90761b0b2d3a32249daf412aa361f0c",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/jniLibs/arm64-v8a/libnbgpty.so.prebuilt",
      sizeBytes = 7_400L,
      sha256 = "3874a30defb85dc7c0007807be93da8cf90761b0b2d3a32249daf412aa361f0c",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/jniLibs/arm64-v8a/libproot.so",
      sizeBytes = 214_536L,
      sha256 = "3682a2c88663477a1704463b4fd838c157bc11a731c417068ec126801e003c8c",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/jniLibs/arm64-v8a/librg.so",
      sizeBytes = 4_543_848L,
      sha256 = "968cabe8efed72fd8fd482cb76b6084fcb695fc5293af7fb62296b02f487fb69",
    ),
    ExpectedRuntimeAsset(
      relativePath = "terminal-core/src/main/jniLibs/arm64-v8a/libsudo.so",
      sizeBytes = 2L,
      sha256 = "c3b845abdaedebf19beec1e7b9835edb0f825fa316b02035caf86e01a592937b",
    ),
  )

  private fun repoRoot(): File {
    val workingDir = System.getProperty("user.dir") ?: "."
    var current = File(workingDir).canonicalFile
    repeat(6) {
      if (File(current, "settings.gradle.kts").isFile) return current
      current = current.parentFile ?: current
    }
    error("Could not locate repo root from $workingDir")
  }

  private fun runbookInventoryAssets(runbook: String): List<ExpectedRuntimeAsset> {
    val rowRegex = Regex("""^\| `([^`]+)` \| ([0-9]+) \| `([0-9a-f]{64})` \| .+ \|$""")
    return runbook.lineSequence()
      .mapNotNull { line ->
        val match = rowRegex.matchEntire(line) ?: return@mapNotNull null
        ExpectedRuntimeAsset(
          relativePath = match.groupValues[1],
          sizeBytes = match.groupValues[2].toLong(),
          sha256 = match.groupValues[3],
        )
      }
      .toList()
  }

  private fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private data class ExpectedRuntimeAsset(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
  )
}

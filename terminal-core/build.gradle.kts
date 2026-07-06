plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

val repoRoot = rootProject.file("../..")
val nodeArchiveUrl = providers.gradleProperty("nbgNodeArchiveUrl")
  .orElse("https://nodejs.org/dist/v24.15.0/node-v24.15.0-linux-arm64.tar.xz")
val nodeArchiveSha256 = providers.gradleProperty("nbgNodeArchiveSha256")
  .orElse("f3d5a797b5d210ce8e2cb265544c8e482eaedcb8aa409a8b46da7e8595d0dda0")
val bundledNodeArchive = layout.projectDirectory.file("src/main/assets/node-v24-linux-arm64.tar.xz")
val skipNbgNodeArchiveSync = providers.gradleProperty("skipNbgNodeArchiveSync")
  .map(String::toBoolean)
  .orElse(false)

fun File.sha256Hex(): String {
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

val syncNbgNodeArchive by tasks.registering {
  group = "build"
  description = "Download the arm64 Node.js runtime archive bundled for offline Android bootstrap."
  inputs.property("nodeArchiveUrl", nodeArchiveUrl)
  inputs.property("nodeArchiveSha256", nodeArchiveSha256)
  outputs.file(bundledNodeArchive)
  outputs.upToDateWhen { false }
  onlyIf { !skipNbgNodeArchiveSync.get() }

  doLast {
    val target = bundledNodeArchive.asFile
    val expectedSha256 = nodeArchiveSha256.get().lowercase()
    if (target.isFile) {
      val actualSha256 = target.sha256Hex()
      if (actualSha256 == expectedSha256) return@doLast
      println("Existing bundled Node.js archive checksum mismatch; replacing ${target.name}")
      target.delete()
    }
    target.parentFile.mkdirs()
    val tmp = target.resolveSibling("${target.name}.tmp")
    println("Downloading bundled Node.js runtime from ${nodeArchiveUrl.get()}")
    URI(nodeArchiveUrl.get()).toURL().openStream().use { input ->
      tmp.outputStream().use { output -> input.copyTo(output) }
    }
    if (tmp.length() < 10L * 1024L * 1024L) {
      tmp.delete()
      throw GradleException("Downloaded Node.js archive is unexpectedly small: ${tmp.length()} bytes")
    }
    val actualSha256 = tmp.sha256Hex()
    if (actualSha256 != expectedSha256) {
      tmp.delete()
      throw GradleException("Downloaded Node.js archive checksum mismatch: expected $expectedSha256, actual $actualSha256")
    }
    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
  }
}

tasks.matching { task ->
    task.name.endsWith("Assets") ||
    task.name.endsWith("AssetsCopy") ||
    task.name.endsWith("LintModel") ||
    task.name.contains("Lint") ||
    task.name.startsWith("lint")
}.configureEach {
  dependsOn(syncNbgNodeArchive)
}

android {
  namespace = "com.nbg.android.terminal"
  compileSdk = 34

  defaultConfig {
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_17
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.coroutines.android)
  api(libs.termux.terminal.emulator)
  testImplementation(libs.junit)
}

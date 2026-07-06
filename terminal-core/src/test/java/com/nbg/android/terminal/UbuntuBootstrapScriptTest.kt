package com.nbg.android.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UbuntuBootstrapScriptTest {
  @Test
  fun renderedScriptStartsUbuntuThroughProotWithTerminalEnvironment() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()

    assertTrue(script.contains("ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"))
    assertTrue(script.contains("export UBUNTU_ARCHIVE_SHA256=\"91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa\""))
    assertTrue(script.contains("usr/var/lib/proot-distro/installed-rootfs/ubuntu"))
    assertTrue(script.contains("export LD_LIBRARY_PATH=\"/data/app/com.nbg.code/lib/arm64:${'$'}PREFIX/lib:${'$'}BIN\""))
    assertTrue(script.contains("export PROOT_TMP_DIR=\"${'$'}TMPDIR\""))
    assertTrue(script.contains("tar xf \"${'$'}ubuntu_archive_path\" -C \"${'$'}UBUNTU_ROOT.install.tmp\" 2> \"${'$'}TMPDIR/install_ubuntu.err\""))
    assertTrue(script.contains("touch \"${'$'}UBUNTU_ROOT/dev/null\""))
    assertTrue(script.contains("\"${'$'}BIN/proot\""))
    assertTrue(script.contains("TERM=xterm-256color"))
    assertTrue(script.contains("/bin/bash -il"))
    assertTrue(script.contains("export NBG_ANDROID_PUBLIC_ROOT=\"/storage/emulated/0/NBG\""))
    assertTrue(script.contains("export NBG_UBUNTU_ROOT_HOME=\"/root/\""))
    assertTrue(script.contains("export NBG_UBUNTU_OUTPUTS_DIR=\"/root/outputs\""))
    assertTrue(script.contains("append_proot_bind_arg \"${'$'}NBG_ANDROID_PUBLIC_ROOT\" \"${'$'}NBG_ANDROID_PUBLIC_ROOT\""))
    assertTrue(script.contains("-w \"${'$'}NBG_UBUNTU_ROOT_HOME\""))
    assertTrue(script.contains("PWD=\"${'$'}NBG_UBUNTU_ROOT_HOME\""))
  }

  @Test
  fun renderedScriptEmitsStartupPhaseMarkers() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()

    assertTrue(script.contains("terminal_phase()"))
    assertTrue(script.contains("echo \"NBG_TERMINAL_PHASE ${'$'}1\""))
    assertTrue(script.contains("terminal_phase ubuntu_install"))
    assertTrue(script.contains("terminal_phase development_tools"))
    assertTrue(script.contains("terminal_phase node_runtime"))
    assertTrue(script.contains("terminal_phase proot_probe"))
    assertTrue(script.indexOf("terminal_phase ubuntu_install") < script.indexOf("install_ubuntu || exit 1"))
    assertTrue(script.indexOf("terminal_phase development_tools") < script.indexOf("ensure_default_development_tools || exit 1"))
    assertTrue(script.indexOf("terminal_phase proot_probe") < script.lastIndexOf("login_ubuntu"))
  }

  @Test
  fun renderedScriptProtectsUbuntuInstallWithLockAndVersionMarker() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()

    assertTrue(script.contains("export UBUNTU_VERSION=\"24.04-noble-aarch64-pd-v4.18.0\""))
    assertTrue(script.contains("LOCK_DIR=\"${'$'}UBUNTU_ROOT.install.lock\""))
    assertTrue(script.contains("LOCK_PID_FILE=\"${'$'}LOCK_DIR/pid\""))
    assertTrue(script.contains("while true; do"))
    assertTrue(script.contains("Ubuntu install lock timeout"))
    assertTrue(script.contains("trap 'cleanup_install' EXIT INT TERM"))
    assertTrue(script.contains("echo \"${'$'}UBUNTU_VERSION\" > \"${'$'}UBUNTU_ROOT/.nbg_ubuntu_version\""))
  }

  @Test
  fun renderedScriptVerifiesUbuntuArchiveChecksumBeforeExtraction() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()

    assertTrue(script.contains("export UBUNTU_ARCHIVE_SHA256=\"91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa\""))
    assertTrue(script.contains("ubuntu_archive_path=\"${'$'}HOME/${'$'}UBUNTU_ARCHIVE\""))
    assertTrue(script.contains("actual_ubuntu_sha=\"${'$'}(\"${'$'}BIN/busybox\" sha256sum \"${'$'}ubuntu_archive_path\""))
    assertTrue(script.contains("Unable to verify Ubuntu archive checksum"))
    assertTrue(script.contains("Ubuntu archive checksum mismatch: ${'$'}UBUNTU_ARCHIVE"))
    assertTrue(script.contains("expected_sha256=${'$'}UBUNTU_ARCHIVE_SHA256"))
    assertTrue(script.contains("actual_sha256=${'$'}actual_ubuntu_sha"))
    assertTrue(script.contains("rm -f \"${'$'}ubuntu_archive_path\" 2>/dev/null || true"))
    assertTrue(script.indexOf("actual_ubuntu_sha=") < script.indexOf("tar xf \"${'$'}ubuntu_archive_path\""))
  }

  @Test
  fun renderedScriptProbesProotAndReportsStartupDiagnostics() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()

    assertTrue(script.contains("run_proot_probe()"))
    assertTrue(script.contains("print_last_proot_probe_failure()"))
    assertTrue(script.contains("PRoot startup probe failed."))
    assertTrue(script.contains("proot_loader=${'$'}{PROOT_LOADER:-<unset>}"))
    assertTrue(script.contains("if ! run_proot_probe; then"))
    assertTrue(script.contains("--link2symlink"))
    assertTrue(script.contains("--kill-on-exit"))
    assertTrue(script.contains("return 1"))
  }

  @Test
  fun renderedScriptKeepsBackgroundUbuntuCommandsAliveAfterLauncherExit() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()
    val runInUbuntu = script.substringAfter("run_in_ubuntu() {").substringBefore("configure_node_runtime_environment()")
    val loginUbuntu = script.substringAfter("login_ubuntu() {").substringBefore("start_shell()")

    assertTrue(runInUbuntu.contains("--link2symlink"))
    assertFalse(runInUbuntu.contains("--kill-on-exit"))
    assertTrue(loginUbuntu.contains("--kill-on-exit"))
  }

  @Test
  fun renderedScriptBuildsOnlyAccessibleProotBindMounts() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()

    assertTrue(script.contains("can_access_bind_source()"))
    assertTrue(script.contains("append_proot_bind_arg()"))
    assertTrue(script.contains("setup_proot_bind_args()"))
    assertTrue(script.contains("\"${'$'}BIN/busybox\" ls -Ld \"${'$'}bind_source\" >/dev/null 2>&1"))
    assertTrue(script.contains("append_proot_bind_arg /proc /proc"))
    assertTrue(script.contains("append_proot_bind_arg /sdcard /sdcard"))
    assertTrue(script.contains("append_proot_bind_arg \"${'$'}HOME\" \"${'$'}HOME\""))
    assertTrue(script.contains("append_proot_bind_arg \"${'$'}NBG_ANDROID_PUBLIC_ROOT\" \"${'$'}NBG_ANDROID_PUBLIC_ROOT\""))
    assertTrue(script.contains("export NBG_UPLOADS_HOST_DIR=\"/data/user/0/com.nbg.code/files/nbg-uploads\""))
    assertTrue(script.contains("append_proot_bind_arg \"${'$'}NBG_UPLOADS_HOST_DIR\" /root/nbg-uploads"))
    assertTrue(script.contains("mkdir -p \"${'$'}UBUNTU_ROOT/root/nbg-uploads\""))
    assertTrue(script.contains("setup_proot_bind_args"))
    assertTrue(script.contains("bind_args=${'$'}{PROOT_BIND_ARGS:-<none>}"))
    assertTrue(script.contains("${'$'}PROOT_BIND_ARGS"))
  }

  @Test
  fun renderedScriptInstallsBuildHelperWithoutAgentRuntime() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()

    assertTrue(script.contains("export NBG_NATIVE_LIB_DIR=\"/data/app/com.nbg.code/lib/arm64\""))
    assertTrue(script.contains("export NBG_RG_HOST_BIN=\"${'$'}NBG_NATIVE_LIB_DIR/librg.so\""))
    assertTrue(script.contains("build_wrapper=\"${'$'}UBUNTU_ROOT/usr/local/bin/android-build\""))
    assertTrue(script.contains("Usage: android-build [project-dir-under-/root-or-name] -- <build command>"))
    assertTrue(script.contains("build_root=\"/root/build-workspaces\""))
    assertTrue(script.contains("output_dir=\"/root/outputs/${'$'}project_name\""))
    assertTrue(script.contains("GOCACHE=/root/.cache/go-build"))
    assertTrue(script.contains("GOMODCACHE=/root/.cache/go-mod"))
    assertTrue(script.contains("GRADLE_USER_HOME=/root/.gradle"))
    assertTrue(script.contains("NPM_CONFIG_CACHE=/root/.npm"))
    assertTrue(script.contains("CARGO_HOME=/root/.cargo"))
    assertFalse(script.contains("HANAKO_SERVER_ARCHIVE"))
    assertFalse(script.contains("HANA_ANDROID_SERVER_VERSION"))
    assertFalse(script.contains("install_hanako_server_runtime()"))
    assertFalse(script.contains("hana-server-android"))
    assertFalse(script.contains("hanako-server-start"))
    assertFalse(script.contains("hanako-server-status"))
    assertFalse(script.contains("hanako-server-stop"))
    assertFalse(script.contains("start_agent_service()"))
    assertFalse(script.contains("install_nbg_code()"))
    assertFalse(script.contains("libnbgcode.so"))
    assertFalse(script.contains("usr/local/bin/nbg"))
  }

  @Test
  fun renderedScriptConfiguresUbuntuPackageManagerSources() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()

    assertTrue(script.contains("configure_package_sources()"))
    assertTrue(script.contains("cat <<'EOF' > \"${'$'}UBUNTU_ROOT/etc/apt/sources.list\""))
    assertTrue(script.contains("deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble main restricted universe multiverse"))
    assertTrue(script.contains("deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-security main restricted universe multiverse"))
    assertTrue(script.contains("echo 'index-url = https://pypi.tuna.tsinghua.edu.cn/simple' >> \"${'$'}UBUNTU_ROOT/root/.config/pip/pip.conf\""))
    assertTrue(script.contains("echo 'index-url = \"https://pypi.tuna.tsinghua.edu.cn/simple\"' > \"${'$'}UBUNTU_ROOT/root/.config/uv/uv.toml\""))
    assertTrue(script.contains("configure_npm_defaults || return 1"))
    assertTrue(script.contains("echo 'prefix=/usr/local'"))
    assertTrue(script.contains("echo 'registry=https://registry.npmmirror.com/'"))
    assertTrue(script.contains("> \"${'$'}UBUNTU_ROOT/root/.npmrc\""))
    assertTrue(script.contains("configure_package_sources || exit 1"))
  }

  @Test
  fun renderedScriptInstallsDefaultDevelopmentToolsWithoutCodex() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()

    assertTrue(script.contains("DEFAULT_TOOLS_VERSION=\"node24-npm-builtin-rg15.1.0-v1\""))
    assertTrue(script.contains("DEFAULT_NODE_DIST_URL=\"https://nodejs.org/dist/v24.15.0\""))
    assertTrue(script.contains("DEFAULT_NODE_ARCHIVE=\"node-v24.15.0-linux-arm64.tar.xz\""))
    assertTrue(script.contains("DEFAULT_NODE_ARCHIVE_SHA256=\"f3d5a797b5d210ce8e2cb265544c8e482eaedcb8aa409a8b46da7e8595d0dda0\""))
    assertTrue(script.contains("ensure_default_development_tools()"))
    assertTrue(script.contains("install_node_from_official_tarball()"))
    assertTrue(script.contains("install_builtin_ripgrep()"))
    assertTrue(script.contains("acquire_default_tools_lock()"))
    assertTrue(script.contains("release_default_tools_lock()"))
    assertTrue(script.contains("Default development tool install lock timeout"))
    assertTrue(script.contains("Installing built-in Node.js runtime..."))
    assertTrue(script.contains("Built-in Node.js archive missing; downloading ${'$'}node_archive_name from ${'$'}DEFAULT_NODE_DIST_URL"))
    assertTrue(script.contains("${'$'}DEFAULT_NODE_DIST_URL/${'$'}node_archive_name"))
    assertTrue(script.contains("node-v24.15.0-linux-arm64.tar.xz"))
    assertTrue(script.contains("\"${'$'}BIN/busybox\" wget"))
    assertTrue(script.contains("\"${'$'}BIN/busybox\" tar xf"))
    assertTrue(script.contains("node_work_dir=\"${'$'}TMPDIR/default-node-${'$'}${'$'}\""))
    assertTrue(script.contains("node_backup_root=\"${'$'}UBUNTU_ROOT/usr/local/node-v24.backup\""))
    assertTrue(script.contains("node_tar_err=\"${'$'}node_work_dir/tar.err\""))
    assertTrue(script.contains("Failed to extract Node.js tarball ${'$'}node_archive_name"))
    assertTrue(script.contains("Node.js tarball extraction did not create bin/node"))
    assertTrue(script.contains("Node.js installation did not pass runtime verification"))
    assertTrue(script.contains("Failed to configure Node.js PATH profile"))
    assertTrue(script.contains("Failed to configure npm defaults"))
    assertTrue(script.contains("ln -sf \"../lib/node_modules/npm/bin/npm-cli.js\" \"${'$'}extracted_node_dir/bin/npm\""))
    assertTrue(script.contains("ln -sf \"../lib/node_modules/npm/bin/npx-cli.js\" \"${'$'}extracted_node_dir/bin/npx\""))
    assertTrue(script.contains("ln -sf \"../lib/node_modules/corepack/dist/corepack.js\" \"${'$'}extracted_node_dir/bin/corepack\""))
    assertTrue(script.contains("ln -sf \"../node-v24/bin/node\""))
    assertTrue(script.contains("ln -sf \"../node-v24/bin/npm\""))
    assertTrue(script.contains("Missing built-in ripgrep binary: ${'$'}NBG_RG_HOST_BIN"))
    assertTrue(script.contains("cp \"${'$'}NBG_RG_HOST_BIN\" \"${'$'}target\""))
    assertTrue(script.contains("chmod 755 \"${'$'}target\""))
    assertTrue(script.contains("00-nbg-node-path.sh"))
    assertTrue(script.contains("PATH=\"/usr/local/node-v24/bin:${'$'}PATH\""))
    assertTrue(script.contains("echo 'prefix=/usr/local'"))
    assertTrue(script.contains("PATH=/usr/local/node-v24/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/root/.local/bin"))
    assertFalse(script.contains("setup_24.x | bash"))
    assertFalse(script.contains("deb.nodesource.com"))
    assertFalse(script.contains("apt-get install"))
    assertFalse(script.contains("apt-get update"))
    assertFalse(script.contains("dpkg --print-architecture"))
    assertFalse(script.contains("python-is-python3"))
    assertFalse(script.contains("python3-venv"))
    assertFalse(script.contains("python3-pip"))
    assertFalse(script.contains("pipx"))
    assertFalse(script.contains("python3 -m pipx"))
    assertFalse(script.contains("command -v uv"))
    assertFalse(script.contains("npm install -g pnpm"))
    assertFalse(script.contains("typescript"))
    assertFalse(script.contains("npm config set"))
    assertFalse(script.contains("DEFAULT_RG_"))
    assertFalse(script.contains("download_with_node()"))
    assertFalse(script.contains("install_rg_from_github_release()"))
    assertFalse(script.contains("github.com/BurntSushi"))

    assertTrue(script.contains("rm -f \"${'$'}UBUNTU_ROOT/.nbg_default_tools_version\""))
    assertTrue(script.contains("[ ! -x \"${'$'}UBUNTU_ROOT/usr/local/node-v24/bin/node\" ]"))
    assertTrue(script.contains("test -x /usr/local/node-v24/bin/node"))
    assertTrue(script.contains("node --version >/dev/null 2>&1"))
    assertTrue(script.contains("npm --version >/dev/null 2>&1"))
    assertTrue(script.contains("command -v rg >/dev/null 2>&1"))
    assertTrue(script.contains("rg --version 2>/dev/null | grep -q \"ripgrep 15[.]1[.]0\""))
    assertTrue(script.contains("ensure_default_development_tools || exit 1"))
    assertFalse(script.contains("@openai/codex"))
  }

  @Test
  fun renderedScriptMapsAndroidGroupsIntoUbuntuGroupFile() {
    val script = UbuntuBootstrapScript(
      filesDir = "/data/user/0/com.nbg.code/files",
      nativeLibraryDir = "/data/app/com.nbg.code/lib/arm64",
      packageName = "com.nbg.code",
    ).render()

    assertTrue(script.contains("fix_android_group_names()"))
    assertTrue(script.contains("current_groups=\"${'$'}(id -G 2>/dev/null || true)\""))
    assertTrue(script.contains("android_group_${'$'}gid:x:${'$'}gid:"))
    assertTrue(script.contains("fix_android_group_names || exit 1"))
  }
}

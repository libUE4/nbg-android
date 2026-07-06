---
title: "Terminal runtime asset checksum inventory"
status: active
date: 2026-07-05
type: runbook
target_repo: /root/nbg-android
owner_agent: "Terminal/Runtime Agent"
review_agent: "QA/Release Agent"
security_review: "required for asset source changes"
---

# Terminal Runtime Asset Checksum Inventory

## Purpose

This inventory records the source assets that make the Android terminal and Ubuntu bootstrap work offline. It covers the files under `terminal-core/src/main/assets` and `terminal-core/src/main/jniLibs/arm64-v8a` that are packaged into debug/public-beta APKs.

Any change to these files must update this inventory, preserve the provenance note in the related issue, and rerun `TerminalRuntimeAssetInventoryTest`.

## Source Assets

| Relative path | Size bytes | SHA-256 | Purpose |
| --- | ---: | --- | --- |
| `terminal-core/src/main/assets/node-v24-linux-arm64.tar.xz` | 30108656 | `f3d5a797b5d210ce8e2cb265544c8e482eaedcb8aa409a8b46da7e8595d0dda0` | Offline Node.js runtime archive used by Ubuntu bootstrap. |
| `terminal-core/src/main/assets/setup_fake_sysdata.sh` | 7356 | `b1f2760d1c187132b4c02981650087ec0a927adb3b606d49a4d895884712b308` | Android compatibility setup sourced inside Ubuntu. |
| `terminal-core/src/main/assets/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz` | 64133552 | `91acaa786b8e2fbba56a9fd0f8a1188cee482b5c7baeed707b29ddaa9a294daa` | Offline Ubuntu noble aarch64 rootfs archive; the bootstrap script verifies this SHA-256 before extraction. |
| `terminal-core/src/main/jniLibs/arm64-v8a/libbash.so` | 2026008 | `14a123925907fbbd1bf2e28cf0fc4f611efd599f780e2fd5c1e207467dd43b3f` | Bundled bash runtime binary. |
| `terminal-core/src/main/jniLibs/arm64-v8a/libbusybox.so` | 1498688 | `c629fce4b0dd3ba9775f851d0941e74582115f423258d3a79800f2bd11d30f5c` | Bundled busybox used by bootstrap and verification scripts. |
| `terminal-core/src/main/jniLibs/arm64-v8a/liblibtalloc.so.2.so` | 31128 | `5f191acfd274e3bda67808d6df514f1f97fbd5c8b5b0d11e734ca04da36487ff` | PRoot support library. |
| `terminal-core/src/main/jniLibs/arm64-v8a/libloader.so` | 5608 | `05e04d87632546506eb03b6c72428f5f0cf5c989d4a629d2648c40a4bf67d45f` | PRoot loader. |
| `terminal-core/src/main/jniLibs/arm64-v8a/libnbgpty.so` | 7400 | `3874a30defb85dc7c0007807be93da8cf90761b0b2d3a32249daf412aa361f0c` | NBG PTY bridge library. |
| `terminal-core/src/main/jniLibs/arm64-v8a/libnbgpty.so.prebuilt` | 7400 | `3874a30defb85dc7c0007807be93da8cf90761b0b2d3a32249daf412aa361f0c` | Source prebuilt copy of the NBG PTY bridge. |
| `terminal-core/src/main/jniLibs/arm64-v8a/libproot.so` | 214536 | `3682a2c88663477a1704463b4fd838c157bc11a731c417068ec126801e003c8c` | PRoot runtime binary. |
| `terminal-core/src/main/jniLibs/arm64-v8a/librg.so` | 4543848 | `968cabe8efed72fd8fd482cb76b6084fcb695fc5293af7fb62296b02f487fb69` | Bundled ripgrep binary copied into Ubuntu. |
| `terminal-core/src/main/jniLibs/arm64-v8a/libsudo.so` | 2 | `c3b845abdaedebf19beec1e7b9835edb0f825fa316b02035caf86e01a592937b` | Placeholder sudo compatibility asset. |

## Verification

Run:

```bash
./gradlew --no-daemon :terminal-core:testDebugUnitTest --tests com.nbg.android.terminal.TerminalRuntimeAssetInventoryTest
```

The test verifies:

- The source asset set is exactly the inventory above.
- Each file exists with the expected byte length.
- Each file matches the expected SHA-256.
- This runbook mentions every path, byte length, and SHA-256.
- The build-time Node archive sync task verifies the downloaded archive SHA-256 before replacing `terminal-core/src/main/assets/node-v24-linux-arm64.tar.xz`.

## Change Policy

- Do not update checksums silently. Asset changes require a short provenance note in the issue evidence.
- Keep the inventory rooted in source files, not `build/intermediates`, so debug/release packaging transformations do not mask source drift.
- Keep the Ubuntu archive SHA-256 in `UbuntuBootstrapScript.UBUNTU_ARCHIVE_SHA256` synchronized with this inventory; the runtime install gate must reject mismatched archives before extraction.
- Keep `terminal-core/build.gradle.kts` `nodeArchiveSha256`, `NbgCodeRuntime.nodeArchiveSha256`, and this inventory synchronized; build-time downloads and runtime installs must reject mismatched Node archives before use.
- Promotion beyond beta should add upstream signature or pinned-source verification where available.

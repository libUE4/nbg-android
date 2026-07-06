---
title: "Terminal and build reliability baseline"
status: active
date: 2026-07-04
type: runbook
target_repo: /root/nbg-android
owner_agent: "Terminal/Runtime Agent"
review_agent: "QA/Release Agent"
security_review: "required for download/checksum changes"
---

# Terminal And Build Reliability Baseline

## Purpose

Define the first measurable local IDE execution loop for public beta: Ubuntu startup, PRoot readiness, default Node/ripgrep availability, command execution, long output rendering, and background process survival.

## Startup Phases

| Phase | Marker / Signal | Expected Result | Automated Coverage |
| --- | --- | --- | --- |
| Ubuntu install | `NBG_TERMINAL_PHASE ubuntu_install` | Rootfs exists, version marker is current, DNS/dev nodes are repaired. | `UbuntuBootstrapScriptTest.renderedScriptProtectsUbuntuInstallWithLockAndVersionMarker` |
| Development tools | `NBG_TERMINAL_PHASE development_tools` | Default tools lock is acquired, Node/ripgrep are checked or installed. | `UbuntuBootstrapScriptTest.renderedScriptInstallsDefaultDevelopmentToolsWithoutCodex` |
| Node runtime | `NBG_TERMINAL_PHASE node_runtime` | Node tarball is checksum-verified, extracted, linked, and runtime-verified. | `NbgCodeRuntimeTest.runtimeAssetNamesMatchAndroidPackagedRuntime` |
| PRoot probe | `NBG_TERMINAL_PHASE proot_probe` | PRoot launches with bind args before interactive shell. | `UbuntuBootstrapScriptTest.renderedScriptProbesProotAndReportsStartupDiagnostics` |
| Ready | `NBG_TERMINAL_READY` | Terminal tab can accept user commands. | `TerminalReadinessControllerTest.reportsInstallingUntilReadyMarkerAppears` |
| Failed | `PRoot startup probe failed.` plus `exit_code=` | UI can keep the tab failed and preserve diagnostic text. | `TerminalReadinessControllerTest.reportsFailureWhenStartupDiagnosticsAppear` |

## Reliability Matrix

| Scenario | Status |
| --- | --- |
| Startup phase classification | Automated through `TerminalReadinessController.analyzeOutput`. |
| Ready state does not regress after later diagnostic text | Automated. |
| Startup token follows a tab after earlier tab deletion | Automated. |
| Starting tab deletion invalidates stale token | Automated. |
| Background `run_in_ubuntu` commands survive launcher exit | Source-tested: no `--kill-on-exit`. |
| Foreground login shell dies with launcher | Source-tested: `--kill-on-exit`. |
| Long output and scrollback rendering | Automated through terminal screen/output snapshot tests. |
| Node archive checksum | Source-tested constant; shell verifies SHA-256 before install. |
| Ubuntu rootfs archive checksum | Source inventory locked by `TerminalRuntimeAssetInventoryTest`; shell verifies SHA-256 before rootfs extraction. |
| PRoot/native runtime asset checksum | Source inventory locked by `TerminalRuntimeAssetInventoryTest`. |

## Device Manual Script

Run on a public-beta candidate APK:

```bash
date
node --version
npm --version
rg --version
printf 'line-%04d\n' $(seq 1 300)
mkdir -p /root/build-workspaces/manual-smoke
cat > /root/build-workspaces/manual-smoke/package.json <<'EOF'
{"scripts":{"test":"node -e \"console.log('node-ok')\""}}
EOF
android-build manual-smoke -- npm test
```

Collect:

- First visible `NBG_TERMINAL_PHASE ...` marker.
- Time from tab open to `NBG_TERMINAL_READY`.
- Any `PRoot startup probe failed.` block.
- `node`, `npm`, `rg`, and `android-build` outputs.
- Whether the tab survives app background/foreground once while a command is running.

## Follow-Ups

- Add upstream signature or pinned-source verification for Ubuntu, PRoot, busybox, bash, and native helper libraries where the upstream project provides a stable verification channel. Runtime SHA-256 gates are in place for Node and Ubuntu archives, but upstream signature provenance is still a future hardening item.
- Export terminal startup phase and first failure diagnostic through the shared diagnostics bundle after Capability Registry v1.

## Asset Inventory

The current terminal runtime source asset inventory lives in `docs/runbooks/terminal-runtime-asset-checksum-inventory.md` and is verified by `TerminalRuntimeAssetInventoryTest`.

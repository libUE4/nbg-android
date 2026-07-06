---
title: "Release build hygiene and supply chain"
status: active
date: 2026-07-04
type: release
target_repo: /root/nbg-android
owner_agent: "QA/Release Agent"
review_agent: "Security Agent"
security_review: "required"
---

# Release Build Hygiene And Supply Chain

## Summary

This document defines the first public-beta release hygiene baseline for NBG Android. It covers build variants, signing expectations, versioning, ABI scope, dependency sources, bundled asset checksums, release gate commands, and explicit beta exceptions.

## Build Variants

| Variant | Purpose | Signing | Notes |
| --- | --- | --- | --- |
| `debug` | Local development and smoke testing | Debug key | Includes debug-only UI smoke preview activity. |
| `profile` | Local near-release performance checks | Debug key for now | Uses release settings with `isDebuggable=false`; not for public distribution. |
| `release` | GitHub Releases public beta artifact | Must use release signing before publishing | Minified and shrinkResources enabled. Current local build produces unsigned release APK unless signing config is supplied. |

## Versioning

Current app defaults:

- App package: `com.nbg.android`
- Version code: `1`
- Version name: `0.1.0`
- Android MCP package: `com.nbg.android.mcpserver`
- Android MCP version code/name: `1` / `0.1.0`

Public beta release rule:

- Increment `versionCode` for every public artifact.
- Use semantic-ish `versionName` with beta suffix when needed, such as `0.1.0-beta.1`.
- Record APK checksums in release notes.

## ABI Scope

Current scope is `arm64-v8a` only:

- `app/build.gradle.kts`
- `terminal-core/build.gradle.kts`

Reason:

- Bundled HanakoPro, Node, Ubuntu/proot assets, and native terminal libraries are arm64 Android focused.

Public beta rule:

- Do not claim x86/x86_64/armeabi-v7a support without adding assets, tests, and release notes for those ABIs.

## Dependency Sources

Configured repositories:

- `google()`
- `mavenCentral()`
- `gradlePluginPortal()` for plugins
- `https://jitpack.io`

JitPack usage:

- Termux terminal dependencies and Huge Icons are currently resolved through JitPack-backed coordinates.
- Public beta release notes must list JitPack as a dependency source.
- Future hardening should pin or mirror any JitPack dependency considered release-critical.

## Bundled Asset Inventory

Critical bundled assets:

- `app/src/main/assets/hanako-server-linux-arm64-node22.nbgpack`
- `app/src/main/assets/hanako-server-linux-arm64-node22.nbgpack.marker`
- `terminal-core/src/main/assets/node-v24-linux-arm64.tar.xz`
- `terminal-core/src/main/assets/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz`
- `terminal-core/src/main/assets/setup_fake_sysdata.sh`
- `app/src/main/assets/nbg-default-skills/nbg-engineering-core/SKILL.md`
- bundled pet assets under `app/src/main/assets/nbg-default-pets`
- default PetDex spritesheet under `app/src/main/res/drawable-nodpi`

Rules:

- Generate `build/release-gate/assets.sha256` for every release candidate.
- Check Hanako `.nbgpack.marker` against packaged APK marker.
- Do not replace large runtime assets without updating release notes and runtime patch contract evidence.

## External Runtime Sources

Node:

- Default URL is declared in `terminal-core/build.gradle.kts` and `UbuntuBootstrapScript.kt`.
- Current bundled Node archive checksum is represented in terminal runtime code.
- The Gradle `syncNbgNodeArchive` task verifies the downloaded Node archive SHA-256 before replacing the bundled asset and rechecks any existing bundled archive unless `skipNbgNodeArchiveSync=true` is explicitly set for local iteration.
- `syncNbgNodeArchive` is an explicit dependency of terminal asset packaging/copy tasks and Android lint tasks/model generation so release lint cannot read a generated Node archive through an implicit Gradle dependency.

Ubuntu/proot/native assets:

- Bundled in `terminal-core/src/main/assets` and native library packaging.
- Public beta must ship checksum evidence for archives and native libraries where practical.

Skills:

- Bundled default Skill is local asset only.
- External Skill install remains user-triggered and must be treated as untrusted unless separately verified.

PetDex:

- PetDex resources are beta.
- Downloaded pet assets require future hash/signature plan before promotion out of beta.

MCP:

- MCP connectors can point to local or external resources.
- Side-effect MCP tools remain beta and must obey permission risk model.

## Local Release Gate

Script:

```bash
./scripts/nbg_release_gate.sh
```

Default actions:

- `:app:lintRelease`
- `:app:testDebugUnitTest`
- `:terminal-core:testDebugUnitTest`
- `:android-mcp-server:testDebugUnitTest`
- `:app:assembleRelease`
- `:android-mcp-server:assembleDebug`
- APK checksum generation
- bundled asset checksum generation

Outputs:

- `build/release-gate/checksums.sha256`
- `build/release-gate/assets.sha256`
- `build/release-gate/summary.txt`
- `build/release-gate/review.txt`

Release notes template:

- `docs/release/NBG_PUBLIC_BETA_RELEASE_NOTES_TEMPLATE.md`
- The evidence review checks that the template exists and includes release summary, privacy/local-data, checksums/supply-chain, known issues, feedback/diagnostics, and upgrade/recovery sections.

Feedback issue template:

- `.github/ISSUE_TEMPLATE/public_beta_feedback.md`
- The evidence review checks that the template asks for version/device, workflow, reproduce steps, expected/actual result, diagnostics status, and a privacy check before users share logs.

Privacy and quick-start templates:

- `docs/release/NBG_PUBLIC_BETA_PRIVACY_LOCAL_DATA.md`
- `docs/release/NBG_PUBLIC_BETA_QUICK_START.md`
- The evidence review checks that the privacy statement covers local-first data handling, encrypted key storage, redacted diagnostics, no cloud telemetry, and beta surface boundaries.
- The evidence review checks that quick start covers checksum verification, arm64-v8a device scope, URL API setup, file/diff review, Terminal build loop, diagnostics export, feedback template, and recovery checks.

Local acceleration flags:

```bash
NBG_RELEASE_SKIP_LINT=1 ./scripts/nbg_release_gate.sh
NBG_RELEASE_SKIP_TESTS=1 ./scripts/nbg_release_gate.sh
```

These flags are for local iteration only and must not be used for public beta release approval. The release gate summary records `lint_skipped`, `tests_skipped`, `public_beta_candidate`, `public_beta_eligible`, and `skip_reason` so skipped runs cannot be mistaken for public-beta evidence.

Public beta candidate mode:

```bash
NBG_RELEASE_PUBLIC_BETA_CANDIDATE=1 ./scripts/nbg_release_gate.sh
```

When `NBG_RELEASE_PUBLIC_BETA_CANDIDATE=1`, the script exits before running Gradle if `NBG_RELEASE_SKIP_LINT=1` or `NBG_RELEASE_SKIP_TESTS=1` is set.

## Release Checklist

- Run full release gate without skip flags, preferably with `NBG_RELEASE_PUBLIC_BETA_CANDIDATE=1`.
- Confirm `build/release-gate/summary.txt` has `public_beta_eligible=true`, `lint_skipped=false`, `tests_skipped=false`, and `skip_reason=none`.
- Confirm `build/release-gate/review.txt` has `candidate_evidence_complete=true`. `publish_ready=false` is expected until the release APK is signed outside source control.
- Fill `docs/release/NBG_PUBLIC_BETA_RELEASE_NOTES_TEMPLATE.md` into concrete GitHub Release notes, including known issues, beta exceptions, checksum references, diagnostics instructions, and publish blocker state.
- Confirm `.github/ISSUE_TEMPLATE/public_beta_feedback.md` is present and linked from release notes.
- Confirm `docs/release/NBG_PUBLIC_BETA_PRIVACY_LOCAL_DATA.md` and `docs/release/NBG_PUBLIC_BETA_QUICK_START.md` are linked from release notes.
- Confirm `app-release-unsigned.apk` is unsigned unless release signing is configured.
- Sign release APK with release key outside source control.
- Generate final SHA256 for signed APK.
- Attach `checksums.sha256` and `assets.sha256` or copy their contents into release notes.
- Include device smoke evidence for UI automation.
- Include diagnostics export manual review evidence.
- Generate `build/quality/weekly-quality-report.md` with `./scripts/nbg_quality_report.sh` and attach it to QA/Release review.
- Include known beta exceptions for MCP/PetDex.

## Current Beta Exceptions

- Android MCP server is a separate debug APK for local beta validation and is not yet a public app-store artifact.
- PetDex downloaded assets are beta and not yet signature-verified.
- JitPack dependencies are accepted for beta with explicit disclosure and future mirroring/pinning follow-up.
- Release signing is documented but not configured in source; signing material must stay outside the repository.

## Verification Evidence

Required commands for Issue 12:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :terminal-core:testDebugUnitTest :android-mcp-server:testDebugUnitTest
./gradlew --no-daemon :app:assembleRelease :android-mcp-server:assembleDebug
./scripts/nbg_release_gate.sh
```

If `lintRelease` is temporarily blocked by toolchain issues, record the exact failure and run the rest of the gate without redefining public beta readiness.

# UI Automation Smoke Skeleton

Status: active skeleton for Issue 11.

## Purpose

Provide a device-level smoke gate for NBG Android public-beta workflows without depending on HanakoPro being online, terminal startup completing, or external network access.

## Coverage

Current instrumentation tests:

- `NbgUiSmokeTest.launchShowsChatInput`
  - Launches `MainActivity`.
  - Verifies Chat input and drawer button are visible.
- `NbgUiSmokeTest.drawerSettingsExposeCoreEntrypoints`
  - Opens the conversation drawer.
  - Opens workbench settings.
  - Verifies Terminal, MCP, Skills, and Diagnostics Export entry points.
- `NbgUiSmokeTest.drawerCanOpenTerminalSkillsMcpAndDiagnostics`
  - Opens Terminal, Skills, MCP, and Diagnostics Export from settings.
  - Verifies each target surface is reachable.
- `NbgToolCardUiSmokeTest.toolCardSurfaceIsSemanticallyVisible`
  - Launches debug-only `NbgToolCardPreviewActivity`.
  - Verifies one basic terminal tool card state and output preview.

## Local Commands

Compile the smoke tests without a device:

```bash
./gradlew --no-daemon :app:compileDebugAndroidTestKotlin
```

Run on a connected device or emulator:

```bash
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

Run only the smoke tests:

```bash
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nbg.android.NbgUiSmokeTest,com.nbg.android.NbgToolCardUiSmokeTest
```

When the Gradle-managed Android SDK points at an unusable `adb`, run the same
smoke tests through the manual wrapper:

```bash
./gradlew --no-daemon :app:assembleDebug :app:compileDebugAndroidTestKotlin
NBG_DEVICE_SERIAL=<adb-serial> ./scripts/nbg_device_smoke.sh
```

The wrapper uses `/usr/bin/adb` by default, installs the debug and androidTest
APKs, runs `NbgUiSmokeTest` and `NbgToolCardUiSmokeTest`, and writes:

```text
build/device-smoke/summary.txt
build/device-smoke/device.txt
build/device-smoke/instrumentation.txt
build/device-smoke/ui-smoke-artifacts/
```

The wrapper also records APK sizes plus `/data` storage before and after each
install. If Android returns install failure `-99` or an insufficient-storage
message, `summary.txt` reports `*_insufficient_device_storage`; free device
`/data` storage before rerunning.

## Device Matrix

Before public beta, run on:

- One phone-size viewport.
- One smaller/narrower viewport.

Record:

- Device model.
- Android version.
- Screen size or emulator profile.
- Command used.
- Pass/fail result.
- Failure artifact path if any.
- Whether the run used Gradle `connectedDebugAndroidTest` or the manual
  `scripts/nbg_device_smoke.sh` wrapper.

Current known local limitation:

- The SDK at `/root/android-sdk/platform-tools/adb` exits with `Illegal instruction`
  in the current aarch64 environment. `connectedDebugAndroidTest` can therefore
  fail before creating an ADB bridge even when `/usr/bin/adb` can see the device.
  Use the manual wrapper until the local SDK adb is replaced or `sdk.dir` points
  at a complete SDK with a working platform-tools binary.

## Failure Artifacts

On assertion failure, tests write best-effort artifacts under the app external files directory:

```text
Android/data/com.nbg.android/files/Pictures/ui-smoke/<test-name>.txt
Android/data/com.nbg.android/files/Pictures/ui-smoke/<test-name>.png
```

The `.txt` file contains test name and error summary. The `.png` file is a Compose root screenshot when capture succeeds.

## Boundaries

In scope:

- Launch, Chat shell, drawer/settings navigation, Terminal/Skills/MCP/Diagnostics entry points, and one tool-card semantic state.

Out of scope for skeleton:

- Real HanakoPro connection.
- Real terminal Ubuntu startup.
- Sending prompts.
- Skill install or MCP connector mutation.
- Full screenshot baselines.

## Expansion Path

Next UI automation increments should add:

- URL API page add/edit dialog smoke.
- Memory page smoke.
- Terminal readiness failure/ready state smoke with mocked session output.
- Screenshot capture attached to CI artifacts.
- One compact/narrow screen run.

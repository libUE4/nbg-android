#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${NBG_DEVICE_SMOKE_OUT_DIR:-"$ROOT_DIR/build/device-smoke"}"
ADB_BIN="${NBG_ADB_BIN:-/usr/bin/adb}"
DEVICE_SERIAL="${NBG_DEVICE_SERIAL:-}"
TEST_CLASSES="${NBG_DEVICE_SMOKE_CLASSES:-com.nbg.android.NbgUiSmokeTest,com.nbg.android.NbgToolCardUiSmokeTest}"
APP_APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
SUMMARY_FILE="$OUT_DIR/summary.txt"
INSTRUMENTATION_FILE="$OUT_DIR/instrumentation.txt"
DEVICE_FILE="$OUT_DIR/device.txt"
INSTALL_LOG="$OUT_DIR/install.txt"

mkdir -p "$OUT_DIR"
: > "$SUMMARY_FILE"
: > "$DEVICE_FILE"
: > "$INSTRUMENTATION_FILE"
: > "$INSTALL_LOG"

adb_cmd() {
  if [[ -n "$DEVICE_SERIAL" ]]; then
    "$ADB_BIN" -s "$DEVICE_SERIAL" "$@"
  else
    "$ADB_BIN" "$@"
  fi
}

record_summary() {
  echo "$1" >> "$SUMMARY_FILE"
}

file_size_bytes() {
  wc -c < "$1" | tr -d ' '
}

device_data_df() {
  adb_cmd shell df -h /data | tr -d '\r' | tail -n 1
}

record_device_storage() {
  local label="$1"
  local data_df
  data_df="$(device_data_df || true)"
  if [[ -n "$data_df" ]]; then
    record_summary "${label}_data_df=$data_df"
  fi
}

install_failure_reason() {
  local label="$1"
  if tail -n 80 "$INSTALL_LOG" |
    grep -Eq 'Failure \[-99\]|INSTALL_FAILED_INSUFFICIENT_STORAGE|No space left|not enough storage'; then
    record_summary "${label}_install_failure_code=-99"
    echo "${label}_insufficient_device_storage"
  else
    echo "${label}_install_failed"
  fi
}

record_summary "smoke_started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
record_summary "adb_bin=$ADB_BIN"
record_summary "device_serial=${DEVICE_SERIAL:-default}"
record_summary "test_classes=$TEST_CLASSES"
record_summary "app_apk=$APP_APK"
record_summary "test_apk=$TEST_APK"

if [[ ! -x "$ADB_BIN" ]]; then
  record_summary "result=failed"
  record_summary "failure=adb_bin_not_executable"
  echo "ADB binary is not executable: $ADB_BIN" >&2
  exit 1
fi

if ! "$ADB_BIN" version > "$OUT_DIR/adb-version.txt" 2>&1; then
  record_summary "result=failed"
  record_summary "failure=adb_version_failed"
  cat "$OUT_DIR/adb-version.txt" >&2
  exit 1
fi

if [[ ! -s "$APP_APK" || ! -s "$TEST_APK" ]]; then
  record_summary "result=failed"
  record_summary "failure=missing_debug_or_test_apk"
  echo "Missing debug APK or androidTest APK. Build with :app:assembleDebug :app:compileDebugAndroidTestKotlin first." >&2
  exit 1
fi

record_summary "app_apk_bytes=$(file_size_bytes "$APP_APK")"
record_summary "test_apk_bytes=$(file_size_bytes "$TEST_APK")"

if ! adb_cmd get-state > "$OUT_DIR/device-state.txt" 2>&1; then
  record_summary "result=failed"
  record_summary "failure=device_unavailable"
  cat "$OUT_DIR/device-state.txt" >&2
  exit 1
fi

{
  echo "model=$(adb_cmd shell getprop ro.product.model | tr -d '\r')"
  echo "android=$(adb_cmd shell getprop ro.build.version.release | tr -d '\r')"
  echo "size=$(adb_cmd shell wm size | tr -d '\r')"
  echo "density=$(adb_cmd shell wm density | tr -d '\r')"
  echo "data_df=$(adb_cmd shell df -h /data | tr -d '\r' | tail -n 1)"
} > "$DEVICE_FILE"

record_summary "device_model=$(grep '^model=' "$DEVICE_FILE" | cut -d= -f2-)"
record_summary "android_version=$(grep '^android=' "$DEVICE_FILE" | cut -d= -f2-)"
record_summary "screen_size=$(grep '^size=' "$DEVICE_FILE" | cut -d= -f2-)"
record_summary "screen_density=$(grep '^density=' "$DEVICE_FILE" | cut -d= -f2-)"
record_summary "data_df=$(grep '^data_df=' "$DEVICE_FILE" | cut -d= -f2-)"
record_device_storage "before_installs"

adb_cmd shell rm -rf /sdcard/Android/data/com.nbg.android/files/Pictures/ui-smoke >/dev/null 2>&1 || true

if [[ "${NBG_DEVICE_SMOKE_UNINSTALL_FIRST:-0}" == "1" ]]; then
  adb_cmd uninstall com.nbg.android.test >> "$INSTALL_LOG" 2>&1 || true
  adb_cmd uninstall com.nbg.android >> "$INSTALL_LOG" 2>&1 || true
fi

install_apk() {
  local label="$1"
  local apk="$2"
  record_device_storage "before_${label}_install"
  if adb_cmd install -r -t -d "$apk" >> "$INSTALL_LOG" 2>&1; then
    record_summary "${label}_install=passed"
    record_device_storage "after_${label}_install"
  else
    local failure_reason
    failure_reason="$(install_failure_reason "$label")"
    record_summary "${label}_install=failed"
    record_summary "result=failed"
    record_summary "failure=$failure_reason"
    record_device_storage "after_${label}_install_failure"
    adb_cmd shell df -h /data >> "$INSTALL_LOG" 2>&1 || true
    if [[ "$failure_reason" == "${label}_insufficient_device_storage" ]]; then
      echo "Device smoke failed during $label install because Android reported insufficient device storage. Free device /data storage and rerun. See $INSTALL_LOG" >&2
    else
      echo "Device smoke failed during $label install. See $INSTALL_LOG" >&2
    fi
    exit 1
  fi
}

install_apk "app_debug" "$APP_APK"
install_apk "app_debug_android_test" "$TEST_APK"

set +e
adb_cmd shell am instrument -w -r \
  -e class "$TEST_CLASSES" \
  com.nbg.android.test/androidx.test.runner.AndroidJUnitRunner \
  > "$INSTRUMENTATION_FILE" 2>&1
INSTRUMENTATION_EXIT=$?
set -e

record_summary "instrumentation_exit=$INSTRUMENTATION_EXIT"

ARTIFACT_DIR="$OUT_DIR/ui-smoke-artifacts"
rm -rf "$ARTIFACT_DIR"
mkdir -p "$ARTIFACT_DIR"
adb_cmd pull /sdcard/Android/data/com.nbg.android/files/Pictures/ui-smoke "$ARTIFACT_DIR" >/dev/null 2>&1 || true
ARTIFACT_COUNT="$(find "$ARTIFACT_DIR" -type f | wc -l | tr -d ' ')"
record_summary "failure_artifact_count=$ARTIFACT_COUNT"

if grep -q "FAILURES!!!" "$INSTRUMENTATION_FILE" ||
  grep -q "INSTRUMENTATION_STATUS_CODE: -2" "$INSTRUMENTATION_FILE" ||
  grep -q "INSTRUMENTATION_RESULT: shortMsg=Process crashed." "$INSTRUMENTATION_FILE" ||
  [[ "$ARTIFACT_COUNT" != "0" ]]; then
  record_summary "result=failed"
  record_summary "failure=instrumentation_failed"
  echo "Device smoke failed. See $INSTRUMENTATION_FILE and $ARTIFACT_DIR" >&2
  exit 1
fi

if [[ "$INSTRUMENTATION_EXIT" -ne 0 ]]; then
  record_summary "result=failed"
  record_summary "failure=instrumentation_exit_$INSTRUMENTATION_EXIT"
  echo "Device smoke instrumentation exited with $INSTRUMENTATION_EXIT. See $INSTRUMENTATION_FILE" >&2
  exit "$INSTRUMENTATION_EXIT"
fi

if ! grep -q "OK (" "$INSTRUMENTATION_FILE"; then
  record_summary "result=failed"
  record_summary "failure=missing_ok_summary"
  echo "Device smoke did not print an OK summary. See $INSTRUMENTATION_FILE" >&2
  exit 1
fi

record_summary "result=passed"
record_summary "smoke_completed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "Device smoke passed: $SUMMARY_FILE"

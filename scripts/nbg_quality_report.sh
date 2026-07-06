#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${NBG_QUALITY_OUT_DIR:-"$ROOT_DIR/build/quality"}"
REPORT_FILE="$OUT_DIR/weekly-quality-report.md"
RELEASE_REVIEW="$ROOT_DIR/build/release-gate/review.txt"
RELEASE_SUMMARY="$ROOT_DIR/build/release-gate/summary.txt"
DEVICE_SMOKE_SUMMARY="$ROOT_DIR/build/device-smoke/summary.txt"

mkdir -p "$OUT_DIR"

summary_value() {
  local file="$1"
  local key="$2"
  if [[ -s "$file" ]]; then
    grep -E "^${key}=" "$file" | tail -n 1 | cut -d= -f2- || true
  fi
}

xml_attr() {
  local line="$1"
  local attr="$2"
  echo "$line" | sed -n "s/.* ${attr}=\"\\([0-9.][0-9.]*\\)\".*/\\1/p"
}

sum_test_xml() {
  local roots=("$@")
  local suites=0
  local tests=0
  local failures=0
  local errors=0
  local skipped=0
  local files=0
  local xml line value
  for root in "${roots[@]}"; do
    [[ -d "$root" ]] || continue
    while IFS= read -r xml; do
      files=$((files + 1))
      line="$(grep -m 1 '<testsuite ' "$xml" || true)"
      [[ -n "$line" ]] || continue
      suites=$((suites + 1))
      value="$(xml_attr "$line" tests)"; tests=$((tests + ${value:-0}))
      value="$(xml_attr "$line" failures)"; failures=$((failures + ${value:-0}))
      value="$(xml_attr "$line" errors)"; errors=$((errors + ${value:-0}))
      value="$(xml_attr "$line" skipped)"; skipped=$((skipped + ${value:-0}))
    done < <(find "$root" -type f -name 'TEST-*.xml' | sort)
  done
  echo "files=$files suites=$suites tests=$tests failures=$failures errors=$errors skipped=$skipped"
}

write_metric() {
  local name="$1"
  local value="$2"
  local source="$3"
  printf '| %s | %s | %s |\n' "$name" "$value" "$source" >> "$REPORT_FILE"
}

APP_DEBUG_TESTS="$(sum_test_xml "$ROOT_DIR/app/build/test-results/testDebugUnitTest")"
TERMINAL_DEBUG_TESTS="$(sum_test_xml "$ROOT_DIR/terminal-core/build/test-results/testDebugUnitTest")"
MCP_DEBUG_TESTS="$(sum_test_xml "$ROOT_DIR/android-mcp-server/build/test-results/testDebugUnitTest")"
ALL_DEBUG_TESTS="$(sum_test_xml \
  "$ROOT_DIR/app/build/test-results/testDebugUnitTest" \
  "$ROOT_DIR/terminal-core/build/test-results/testDebugUnitTest" \
  "$ROOT_DIR/android-mcp-server/build/test-results/testDebugUnitTest")"
PUBLIC_BETA_ELIGIBLE="$(summary_value "$RELEASE_SUMMARY" public_beta_eligible)"
CANDIDATE_EVIDENCE_COMPLETE="$(summary_value "$RELEASE_REVIEW" candidate_evidence_complete)"
PUBLISH_READY="$(summary_value "$RELEASE_REVIEW" publish_ready)"
PUBLISH_BLOCKER="$(summary_value "$RELEASE_REVIEW" publish_blocker)"
DEVICE_SMOKE_RESULT="$(summary_value "$DEVICE_SMOKE_SUMMARY" result)"
DEVICE_SMOKE_FAILURE="$(summary_value "$DEVICE_SMOKE_SUMMARY" failure)"

{
  echo "# NBG Weekly Quality Report"
  echo
  echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  echo "This report is local-first. It summarizes checked-in release gates, local"
  echo "test XML, device smoke artifacts, and manual-evidence placeholders."
  echo "This report does not add cloud telemetry."
  echo
  echo "## Automated Evidence"
  echo
  echo "| Metric | Value | Source |"
  echo "| --- | --- | --- |"
} > "$REPORT_FILE"

write_metric "App debug unit tests" "$APP_DEBUG_TESTS" "app/build/test-results/testDebugUnitTest"
write_metric "Terminal debug unit tests" "$TERMINAL_DEBUG_TESTS" "terminal-core/build/test-results/testDebugUnitTest"
write_metric "MCP debug unit tests" "$MCP_DEBUG_TESTS" "android-mcp-server/build/test-results/testDebugUnitTest"
write_metric "All debug unit tests" "$ALL_DEBUG_TESTS" "local TEST-*.xml"
write_metric "Release candidate eligible" "${PUBLIC_BETA_ELIGIBLE:-missing}" "build/release-gate/summary.txt"
write_metric "Release candidate evidence" "${CANDIDATE_EVIDENCE_COMPLETE:-missing}" "build/release-gate/review.txt"
write_metric "Publish ready" "${PUBLISH_READY:-missing}" "build/release-gate/review.txt"
write_metric "Publish blocker" "${PUBLISH_BLOCKER:-none}" "build/release-gate/review.txt"
write_metric "Device smoke result" "${DEVICE_SMOKE_RESULT:-missing}" "build/device-smoke/summary.txt"
write_metric "Device smoke failure" "${DEVICE_SMOKE_FAILURE:-none}" "build/device-smoke/summary.txt"

{
  echo
  echo "## Manual Metrics"
  echo
  echo "| Metric | Current value | Evidence needed |"
  echo "| --- | --- | --- |"
  echo "| Startup time | missing | Device startup run or screen recording timestamp. |"
  echo "| Hanako connection success rate | missing | Manual/device smoke run with connection logs. |"
  echo "| Terminal startup success rate | missing | Device terminal smoke logs. |"
  echo "| Build-loop success rate | missing | Real project build-loop evidence. |"
  echo "| Crash/ANR count | missing | Device logcat or issue triage summary. |"
  echo "| Tool failure rate | missing | Tool-card event summary or diagnostics sample. |"
  echo "| Session recovery success rate | missing | Close/reopen manual demo evidence. |"
  echo "| Model configuration success rate | missing | URL API save/fetch/verify smoke evidence. |"
  echo "| Skills usage count | missing | Local Skills page/API summary or diagnostics sample. |"
  echo "| Feedback/diagnostic export count | missing | Local exported diagnostics sample count. |"
  echo
  echo "## Required Follow-Ups"
  echo
  echo "- Fill missing manual metrics from local logs, manual test runs, or user-provided diagnostics."
  echo "- Do not add cloud telemetry without a separate privacy-reviewed telemetry plan."
  echo "- Attach this report with release-gate artifacts during QA/Release review."
} >> "$REPORT_FILE"

echo "Quality report written: $REPORT_FILE"

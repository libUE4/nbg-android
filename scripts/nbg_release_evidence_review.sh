#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${NBG_RELEASE_OUT_DIR:-"$ROOT_DIR/build/release-gate"}"
SUMMARY_FILE="$OUT_DIR/summary.txt"
CHECKSUM_FILE="$OUT_DIR/checksums.sha256"
ASSET_FILE="$OUT_DIR/assets.sha256"
REVIEW_FILE="$OUT_DIR/review.txt"
APP_RELEASE_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
MCP_DEBUG_APK="$ROOT_DIR/android-mcp-server/build/outputs/apk/debug/android-mcp-server-debug.apk"
APP_RELEASE_METADATA="$ROOT_DIR/app/build/outputs/apk/release/output-metadata.json"
MCP_DEBUG_METADATA="$ROOT_DIR/android-mcp-server/build/outputs/apk/debug/output-metadata.json"
LINT_TXT="$ROOT_DIR/app/build/reports/lint-results-release.txt"
LINT_XML="$ROOT_DIR/app/build/reports/lint-results-release.xml"
RELEASE_NOTES_TEMPLATE="$ROOT_DIR/docs/release/NBG_PUBLIC_BETA_RELEASE_NOTES_TEMPLATE.md"
FEEDBACK_TEMPLATE="$ROOT_DIR/.github/ISSUE_TEMPLATE/public_beta_feedback.md"
PRIVACY_STATEMENT="$ROOT_DIR/docs/release/NBG_PUBLIC_BETA_PRIVACY_LOCAL_DATA.md"
QUICK_START="$ROOT_DIR/docs/release/NBG_PUBLIC_BETA_QUICK_START.md"
FAILURES=0

mkdir -p "$OUT_DIR"
: > "$REVIEW_FILE"

record() {
  echo "$1" >> "$REVIEW_FILE"
}

fail() {
  FAILURES=$((FAILURES + 1))
  record "failure_$FAILURES=$1"
}

require_file() {
  local label="$1"
  local path="$2"
  if [[ -s "$path" ]]; then
    record "${label}=present"
  else
    record "${label}=missing"
    fail "${label}_missing"
  fi
}

summary_value() {
  local key="$1"
  grep -E "^${key}=" "$SUMMARY_FILE" | tail -n 1 | cut -d= -f2-
}

record "review_completed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
record "review_version=nbg-release-evidence-review-v1"

require_file "summary_file" "$SUMMARY_FILE"
require_file "checksum_file" "$CHECKSUM_FILE"
require_file "asset_file" "$ASSET_FILE"
require_file "lint_text_report" "$LINT_TXT"
require_file "lint_xml_report" "$LINT_XML"
require_file "app_release_apk" "$APP_RELEASE_APK"
require_file "mcp_debug_apk" "$MCP_DEBUG_APK"
require_file "app_release_metadata" "$APP_RELEASE_METADATA"
require_file "mcp_debug_metadata" "$MCP_DEBUG_METADATA"
require_file "release_notes_template" "$RELEASE_NOTES_TEMPLATE"
require_file "feedback_issue_template" "$FEEDBACK_TEMPLATE"
require_file "privacy_local_data_statement" "$PRIVACY_STATEMENT"
require_file "public_beta_quick_start" "$QUICK_START"

if [[ -s "$SUMMARY_FILE" ]]; then
  PUBLIC_BETA_CANDIDATE="$(summary_value public_beta_candidate)"
  LINT_SKIPPED="$(summary_value lint_skipped)"
  TESTS_SKIPPED="$(summary_value tests_skipped)"
  PUBLIC_BETA_ELIGIBLE="$(summary_value public_beta_eligible)"
  SKIP_REASON="$(summary_value skip_reason)"
  record "public_beta_candidate=${PUBLIC_BETA_CANDIDATE:-missing}"
  record "lint_skipped=${LINT_SKIPPED:-missing}"
  record "tests_skipped=${TESTS_SKIPPED:-missing}"
  record "public_beta_eligible=${PUBLIC_BETA_ELIGIBLE:-missing}"
  record "skip_reason=${SKIP_REASON:-missing}"

  for key in release_gate_completed_at public_beta_candidate lint_skipped tests_skipped public_beta_eligible skip_reason checksum_file asset_file; do
    if ! grep -qE "^${key}=" "$SUMMARY_FILE"; then
      fail "summary_missing_${key}"
    fi
  done

  if [[ "$PUBLIC_BETA_CANDIDATE" == "1" ]]; then
    [[ "$LINT_SKIPPED" == "false" ]] || fail "public_beta_candidate_lint_skipped"
    [[ "$TESTS_SKIPPED" == "false" ]] || fail "public_beta_candidate_tests_skipped"
    [[ "$PUBLIC_BETA_ELIGIBLE" == "true" ]] || fail "public_beta_candidate_not_eligible"
    [[ "$SKIP_REASON" == "none" ]] || fail "public_beta_candidate_skip_reason_not_none"
  fi

  if [[ "$PUBLIC_BETA_ELIGIBLE" == "true" ]]; then
    [[ "$LINT_SKIPPED" == "false" ]] || fail "eligible_lint_skipped"
    [[ "$TESTS_SKIPPED" == "false" ]] || fail "eligible_tests_skipped"
    [[ "$SKIP_REASON" == "none" ]] || fail "eligible_skip_reason_not_none"
  fi
fi

if [[ -s "$CHECKSUM_FILE" ]]; then
  grep -q "app-release-unsigned.apk" "$CHECKSUM_FILE" || fail "checksum_missing_app_release_apk"
  grep -q "android-mcp-server-debug.apk" "$CHECKSUM_FILE" || fail "checksum_missing_mcp_debug_apk"
  if sha256sum -c "$CHECKSUM_FILE" >/tmp/nbg-release-checksums.verify 2>&1; then
    record "apk_checksums_verified=true"
  else
    record "apk_checksums_verified=false"
    fail "apk_checksum_verification_failed"
  fi
fi

if [[ -s "$ASSET_FILE" ]]; then
  ASSET_COUNT="$(wc -l < "$ASSET_FILE" | tr -d ' ')"
  record "asset_checksum_entries=$ASSET_COUNT"
  for required_asset in \
    "app/src/main/assets/hanako-server-linux-arm64-node22.nbgpack" \
    "app/src/main/assets/hanako-server-linux-arm64-node22.nbgpack.marker" \
    "app/src/main/assets/nbg-default-skills/nbg-engineering-core/SKILL.md" \
    "app/src/main/res/drawable-nodpi/petdex_002_spritesheet.webp" \
    "terminal-core/src/main/assets/node-v24-linux-arm64.tar.xz" \
    "terminal-core/src/main/assets/setup_fake_sysdata.sh" \
    "terminal-core/src/main/assets/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz"
  do
    grep -q "$required_asset" "$ASSET_FILE" || fail "asset_missing_${required_asset//\//_}"
  done
  if sha256sum -c "$ASSET_FILE" >/tmp/nbg-release-assets.verify 2>&1; then
    record "asset_checksums_verified=true"
  else
    record "asset_checksums_verified=false"
    fail "asset_checksum_verification_failed"
  fi
fi

if [[ -s "$LINT_TXT" ]]; then
  LINT_SUMMARY="$(grep -E '^[0-9]+ errors?, [0-9]+ warnings?' "$LINT_TXT" | tail -n 1 || true)"
  record "lint_summary=${LINT_SUMMARY:-missing}"
  if [[ "$LINT_SUMMARY" =~ ^0\ errors, ]]; then
    record "lint_errors_zero=true"
  else
    record "lint_errors_zero=false"
    fail "lint_errors_not_zero_or_missing_summary"
  fi
fi

if [[ -s "$APP_RELEASE_METADATA" ]]; then
  grep -q '"applicationId": "com.nbg.android"' "$APP_RELEASE_METADATA" || fail "app_release_metadata_application_id_mismatch"
  grep -q '"variantName": "release"' "$APP_RELEASE_METADATA" || fail "app_release_metadata_variant_mismatch"
  grep -q '"outputFile": "app-release-unsigned.apk"' "$APP_RELEASE_METADATA" || fail "app_release_metadata_output_mismatch"
fi

if [[ -s "$MCP_DEBUG_METADATA" ]]; then
  grep -q '"applicationId": "com.nbg.android.mcpserver"' "$MCP_DEBUG_METADATA" || fail "mcp_debug_metadata_application_id_mismatch"
  grep -q '"variantName": "debug"' "$MCP_DEBUG_METADATA" || fail "mcp_debug_metadata_variant_mismatch"
  grep -q '"outputFile": "android-mcp-server-debug.apk"' "$MCP_DEBUG_METADATA" || fail "mcp_debug_metadata_output_mismatch"
fi

if [[ -s "$RELEASE_NOTES_TEMPLATE" ]]; then
  for required_heading in \
    "## Release Summary" \
    "## Privacy And Local Data" \
    "## Checksums And Supply Chain" \
    "## Known Issues" \
    "## Feedback And Diagnostics" \
    "## Upgrade And Recovery Notes"
  do
    grep -qF "$required_heading" "$RELEASE_NOTES_TEMPLATE" || fail "release_notes_template_missing_${required_heading//[^A-Za-z0-9]/_}"
  done
  for required_phrase in \
    "candidate_evidence_complete=true" \
    "publish_ready=true" \
    "arm64-v8a" \
    "Diagnostics export is user-triggered and redacted by default" \
    "Android MCP server: beta" \
    "PetDex and downloaded pet resources: beta"
  do
    grep -qF "$required_phrase" "$RELEASE_NOTES_TEMPLATE" || fail "release_notes_template_missing_${required_phrase//[^A-Za-z0-9]/_}"
  done
fi

if [[ -s "$FEEDBACK_TEMPLATE" ]]; then
  for required_phrase in \
    "Public beta feedback" \
    "Version And Device" \
    "Steps To Reproduce" \
    "Expected Result" \
    "Actual Result" \
    "Diagnostics" \
    "Privacy Check" \
    "Do not paste API keys" \
    "provider tokens" \
    "Memory contents" \
    "raw diagnostics that you have not reviewed"
  do
    grep -qF "$required_phrase" "$FEEDBACK_TEMPLATE" || fail "feedback_template_missing_${required_phrase//[^A-Za-z0-9]/_}"
  done
fi

if [[ -s "$PRIVACY_STATEMENT" ]]; then
  for required_phrase in \
    "local-first" \
    "does not upload" \
    "Android Keystore-backed encrypted storage" \
    "Diagnostics export is user-triggered" \
    "Diagnostics export is redacted by default" \
    "No cloud telemetry" \
    "Any future telemetry, cloud sync, or automatic upload requires a separate privacy-reviewed plan" \
    "Android MCP server is beta" \
    "PetDex downloaded resources are beta"
  do
    grep -qF "$required_phrase" "$PRIVACY_STATEMENT" || fail "privacy_statement_missing_${required_phrase//[^A-Za-z0-9]/_}"
  done
fi

if [[ -s "$QUICK_START" ]]; then
  for required_phrase in \
    "Verify the APK SHA-256 checksum" \
    "arm64-v8a Android device" \
    "Open URL API settings" \
    "Review file preview and diff before approving writes" \
    "Open Terminal and run a local build" \
    "Open Diagnostics Export" \
    ".github/ISSUE_TEMPLATE/public_beta_feedback.md" \
    "Do not paste API keys" \
    "Close and reopen the app"
  do
    grep -qF "$required_phrase" "$QUICK_START" || fail "quick_start_missing_${required_phrase//[^A-Za-z0-9]/_}"
  done
fi

if [[ "${PUBLIC_BETA_ELIGIBLE:-false}" == "true" ]]; then
  TEST_XML_ROOTS=(
    "$ROOT_DIR/app/build/test-results/testDebugUnitTest"
    "$ROOT_DIR/terminal-core/build/test-results/testDebugUnitTest"
    "$ROOT_DIR/android-mcp-server/build/test-results/testDebugUnitTest"
  )
  TOTAL_TEST_SUITES=0
  TOTAL_TESTS=0
  for root in "${TEST_XML_ROOTS[@]}"; do
    if [[ ! -d "$root" ]]; then
      fail "test_xml_root_missing_${root#$ROOT_DIR/}"
      continue
    fi
    ROOT_XML_COUNT="$(find "$root" -type f -name 'TEST-*.xml' | wc -l | tr -d ' ')"
    record "test_xml_count_${root#$ROOT_DIR/}=$ROOT_XML_COUNT"
    [[ "$ROOT_XML_COUNT" != "0" ]] || fail "test_xml_empty_${root#$ROOT_DIR/}"
    while IFS= read -r xml; do
      suite_line="$(grep -m 1 '<testsuite ' "$xml" || true)"
      if [[ -z "$suite_line" ]]; then
        fail "test_xml_missing_testsuite_${xml#$ROOT_DIR/}"
        continue
      fi
      tests="$(echo "$suite_line" | sed -n 's/.* tests="\([0-9][0-9]*\)".*/\1/p')"
      failures="$(echo "$suite_line" | sed -n 's/.* failures="\([0-9][0-9]*\)".*/\1/p')"
      errors="$(echo "$suite_line" | sed -n 's/.* errors="\([0-9][0-9]*\)".*/\1/p')"
      TOTAL_TEST_SUITES=$((TOTAL_TEST_SUITES + 1))
      TOTAL_TESTS=$((TOTAL_TESTS + ${tests:-0}))
      [[ "${failures:-missing}" == "0" ]] || fail "test_xml_failures_${xml#$ROOT_DIR/}"
      [[ "${errors:-missing}" == "0" ]] || fail "test_xml_errors_${xml#$ROOT_DIR/}"
    done < <(find "$root" -type f -name 'TEST-*.xml' | sort)
  done
  record "test_xml_suites=$TOTAL_TEST_SUITES"
  record "test_xml_tests=$TOTAL_TESTS"
fi

if apksigner verify --verbose "$APP_RELEASE_APK" >/tmp/nbg-app-release-apksigner.verify 2>&1; then
  record "app_release_apk_signed=true"
  record "external_release_signing_required=false"
else
  record "app_release_apk_signed=false"
  record "external_release_signing_required=true"
fi

if apksigner verify --verbose "$MCP_DEBUG_APK" >/tmp/nbg-mcp-debug-apksigner.verify 2>&1; then
  record "mcp_debug_apk_signed=true"
else
  record "mcp_debug_apk_signed=false"
  fail "mcp_debug_apk_signature_verification_failed"
fi

if [[ "$FAILURES" -eq 0 ]]; then
  record "candidate_evidence_complete=true"
else
  record "candidate_evidence_complete=false"
fi

if [[ "${PUBLIC_BETA_ELIGIBLE:-false}" == "true" && "$FAILURES" -eq 0 ]]; then
  if grep -q '^app_release_apk_signed=true$' "$REVIEW_FILE"; then
    record "publish_ready=true"
  else
    record "publish_ready=false"
    record "publish_blocker=external_release_signing_required"
  fi
else
  record "publish_ready=false"
fi

record "failure_count=$FAILURES"

if [[ "$FAILURES" -ne 0 ]]; then
  echo "Release evidence review failed with $FAILURES failure(s). See $REVIEW_FILE" >&2
  exit 1
fi

echo "Release evidence review passed: $REVIEW_FILE"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${NBG_RELEASE_OUT_DIR:-"$ROOT_DIR/build/release-gate"}"
CHECKSUM_FILE="$OUT_DIR/checksums.sha256"
ASSET_FILE="$OUT_DIR/assets.sha256"
REVIEW_FILE="$OUT_DIR/review.txt"
GRADLE_ARGS=(--no-daemon)
SKIP_LINT="${NBG_RELEASE_SKIP_LINT:-0}"
SKIP_TESTS="${NBG_RELEASE_SKIP_TESTS:-0}"
PUBLIC_BETA_CANDIDATE="${NBG_RELEASE_PUBLIC_BETA_CANDIDATE:-0}"

mkdir -p "$OUT_DIR"

run_gradle() {
  "$ROOT_DIR/gradlew" "${GRADLE_ARGS[@]}" "$@"
}

if [[ "$PUBLIC_BETA_CANDIDATE" == "1" && ( "$SKIP_LINT" == "1" || "$SKIP_TESTS" == "1" ) ]]; then
  echo "Public beta release gate must not use NBG_RELEASE_SKIP_LINT or NBG_RELEASE_SKIP_TESTS." >&2
  exit 2
fi

if [[ "$SKIP_LINT" != "1" ]]; then
  run_gradle :app:lintRelease
fi

if [[ "$SKIP_TESTS" != "1" ]]; then
  run_gradle :app:testDebugUnitTest :terminal-core:testDebugUnitTest :android-mcp-server:testDebugUnitTest
fi

run_gradle :app:assembleRelease :android-mcp-server:assembleDebug

: > "$CHECKSUM_FILE"
for artifact in \
  "$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk" \
  "$ROOT_DIR/android-mcp-server/build/outputs/apk/debug/android-mcp-server-debug.apk"
do
  if [[ -f "$artifact" ]]; then
    sha256sum "$artifact" >> "$CHECKSUM_FILE"
  fi
done

: > "$ASSET_FILE"
while IFS= read -r -d '' asset; do
  sha256sum "$asset" >> "$ASSET_FILE"
done < <(
  find \
    "$ROOT_DIR/app/src/main/assets" \
    "$ROOT_DIR/app/src/main/res/drawable-nodpi" \
    "$ROOT_DIR/terminal-core/src/main/assets" \
    -type f \
    \( -name '*.nbgpack' -o -name '*.marker' -o -name '*.xz' -o -name '*.sh' -o -name 'SKILL.md' -o -name '*.webp' -o -name 'pet.json' \) \
    -print0 | sort -z
)

{
  echo "release_gate_completed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "public_beta_candidate=$PUBLIC_BETA_CANDIDATE"
  echo "lint_skipped=$([[ "$SKIP_LINT" == "1" ]] && echo true || echo false)"
  echo "tests_skipped=$([[ "$SKIP_TESTS" == "1" ]] && echo true || echo false)"
  if [[ "$SKIP_LINT" == "1" || "$SKIP_TESTS" == "1" ]]; then
    echo "public_beta_eligible=false"
    echo "skip_reason=local_iteration_only"
  else
    echo "public_beta_eligible=true"
    echo "skip_reason=none"
  fi
  echo "checksum_file=$CHECKSUM_FILE"
  echo "asset_file=$ASSET_FILE"
} > "$OUT_DIR/summary.txt"

"$ROOT_DIR/scripts/nbg_release_evidence_review.sh"

echo "Release gate artifacts:"
echo "  $CHECKSUM_FILE"
echo "  $ASSET_FILE"
echo "  $OUT_DIR/summary.txt"
echo "  $REVIEW_FILE"

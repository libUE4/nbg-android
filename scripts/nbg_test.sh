#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export TZ=UTC
export LANG=C.UTF-8
export LC_ALL=C.UTF-8

unset OPENAI_API_KEY
unset ANTHROPIC_API_KEY
unset GOOGLE_API_KEY
unset GROQ_API_KEY
unset TOGETHER_API_KEY

if [ "$#" -gt 0 ]; then
  exec ./gradlew --no-daemon "$@"
fi

exec ./gradlew --no-daemon \
  :app:testDebugUnitTest \
  :terminal-core:testDebugUnitTest \
  :android-mcp-server:testDebugUnitTest \
  :app:assembleDebug

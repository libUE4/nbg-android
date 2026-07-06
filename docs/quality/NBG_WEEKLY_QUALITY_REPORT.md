# NBG Weekly Quality Report

Use this report during the six-month public-beta runway. It is intentionally
local-first: the report summarizes local test XML, release-gate artifacts,
device-smoke artifacts, and manual evidence placeholders. It must not introduce
cloud telemetry.

## Command

```bash
./scripts/nbg_quality_report.sh
```

Output:

```text
build/quality/weekly-quality-report.md
```

## Automated Inputs

- `app/build/test-results/testDebugUnitTest/TEST-*.xml`
- `terminal-core/build/test-results/testDebugUnitTest/TEST-*.xml`
- `android-mcp-server/build/test-results/testDebugUnitTest/TEST-*.xml`
- `build/release-gate/summary.txt`
- `build/release-gate/review.txt`
- `build/device-smoke/summary.txt`

Missing inputs are reported as `missing`; they are not treated as passing.

## Required Metrics

- Startup time.
- Hanako connection success rate.
- Terminal startup success rate.
- Build-loop success rate.
- Crash/ANR count.
- Tool failure rate.
- Session recovery success rate.
- Model configuration success rate.
- Skills usage count.
- Feedback/diagnostic export count.

## Evidence Rules

- Use local logs, manual test runs, release-gate output, device-smoke output, and
  user-provided diagnostics.
- Do not add telemetry or automatic upload to satisfy this report.
- Manual metrics stay `missing` until real evidence exists.
- Attach the generated report to QA/Release review before public-beta approval.

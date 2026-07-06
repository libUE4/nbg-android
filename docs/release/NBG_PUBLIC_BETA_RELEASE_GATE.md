# NBG Public Beta Release Gate

Target: GitHub Releases public beta by month 6, starting from 2026-07-04.

## Release Artifacts

Every public beta release must include:

- APK artifact.
- Checksum file.
- Release notes.
- Privacy and local-data statement.
- Known issues.
- Feedback/diagnostic export instructions.
- Minimum supported Android/device notes.
- Clear beta labels for MCP and pet ecosystem features if they are not final.

## P0 Release Blockers

Do not release if any P0 item is open:

- API key migration can lose keys or store new keys in plaintext.
- App data backup is enabled while SharedPreferences may contain URL API keys, FTP passwords, provider config, memories, or local service credentials.
- Dangerous operation confirmation can be bypassed.
- File write/delete can occur without review where review is required.
- External Skill/Pet/MCP resource can become trusted without hash/signature or explicit risk acceptance.
- Build-time or runtime external downloads lack checksum/signature verification.
- Local HTTP, FTP, or MCP service surfaces lack explicit localhost binding, authentication, or user-visible trust boundaries.
- Terminal or Hanako launcher has no useful failure diagnostics.
- Session recovery fails for the main code-task loop.
- Diagnostics export leaks secrets by default.
- A crash/ANR blocks startup, chat, terminal, or settings.

## Public Beta Demo

The release candidate must pass this full-device demo:

1. Install APK from GitHub Releases.
2. Configure URL API and verify encrypted key storage.
3. Open or create a local Android/Kotlin sample project.
4. Ask the Agent to perform a real code change.
5. Review file preview and diff before write.
6. Approve the write and run a local build.
7. Inspect terminal/build output and failure/success state.
8. Generate or update one Skill with user review.
9. Save one Memory and reuse it in a new session.
10. Show MCP beta/device status and pet Agent status.
11. Close and reopen the app; confirm session and key state recover.
12. Export a redacted diagnostic bundle.

## Required Verification

Commands:

```bash
./gradlew :app:assembleDebug
./gradlew test
./gradlew :app:testDebugUnitTest
./gradlew :terminal-core:testDebugUnitTest
```

Manual checks:

- Startup and chat connection on at least one real device.
- Terminal startup and one Node command.
- Local build wrapper or equivalent project build.
- Upgrade check from a build that contains legacy plaintext preferences.
- Local service boundary check for FTP, Hanako loopback HTTP, and Android MCP beta.
- Theme pass for light, black, and Claude themes.
- Small-screen pass for primary chat, terminal, settings, Skills, Memory, MCP, and pet surfaces.

Evidence:

- Test logs.
- Demo recording or screenshots.
- Redacted diagnostics sample.
- Release artifact checksum.

## Weekly Quality Report

Generate the local report with:

```bash
./scripts/nbg_quality_report.sh
```

Output:

```text
build/quality/weekly-quality-report.md
```

Track these metrics during the beta runway:

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

Do not add cloud telemetry just to satisfy this report. Use local logs, manual test runs, and user-provided diagnostics until a separate privacy-reviewed telemetry plan exists.

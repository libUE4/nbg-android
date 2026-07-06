# NBG Android Public Beta Quick Start

Use this guide for GitHub Releases public beta testers.

## Install

1. Download the public beta APK from GitHub Releases.
2. Verify the APK SHA-256 checksum from the release notes.
3. Install the APK on an arm64-v8a Android device.
4. Open NBG Android and confirm the app starts at the Agent chat shell.

## Configure A Model

1. Open URL API settings.
2. Add a provider base URL, model, and API key.
3. Fetch or verify models before using the provider.
4. Confirm the key remains local and encrypted by NBG Android storage.

## Run The IDE Loop

1. Open or create a local Android/Kotlin sample project.
2. Ask the Agent to make a small code change.
3. Review file preview and diff before approving writes.
4. Open Terminal and run a local build or equivalent project check.
5. Inspect terminal/build output and failure or success state.

## Try Agent Platform Surfaces

1. Open Skills and review bundled or user-managed Skills.
2. Save a safe Memory item and confirm it can be reused in a later session.
3. Open MCP beta status and confirm only expected beta tools are enabled.
4. Check pet status as a visible Agent-state companion.

## Export Diagnostics And File Feedback

1. Open Diagnostics Export from workbench settings.
2. Export the redacted diagnostics bundle only when you choose to share it.
3. Review diagnostics before sharing.
4. File feedback with `.github/ISSUE_TEMPLATE/public_beta_feedback.md`.
5. Do not paste API keys, provider tokens, FTP passwords, Memory contents,
   private source code, raw terminal output, or raw diagnostics that you have
   not reviewed.

## Recovery Checks

1. Close and reopen the app.
2. Confirm session, model selection, and key state recover.
3. If recovery fails, include steps and redacted diagnostics in feedback.

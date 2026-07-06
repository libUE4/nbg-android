# NBG Android Public Beta Privacy And Local Data Statement

NBG Android public beta is local-first. By default, the app does not upload
code, files, memories, terminal output, diagnostics, API keys, provider tokens,
FTP passwords, or local service credentials.

## Local Data

- URL API keys and provider credentials must be stored through Android Keystore-backed encrypted storage.
- Memory content stays local and is excluded from diagnostics export by default.
- Terminal output, file previews, diffs, private paths, and user project content
  stay local unless the user manually shares them.
- Local service credentials such as FTP passwords must not be backed up or
  exported in plaintext.

## Diagnostics

- Diagnostics export is user-triggered.
- Diagnostics export is redacted by default.
- Users should review diagnostics before sharing.
- Diagnostics must not include API keys, provider tokens, FTP passwords,
  Memory content, private source code, raw terminal output, raw file diffs, or
  local private paths by default.

## Network And Telemetry

- No cloud telemetry is added for release evidence or quality reports.
- URL API requests are user-configured and go to the provider endpoint selected
  by the user.
- Any future telemetry, cloud sync, or automatic upload requires a separate privacy-reviewed plan.

## Beta Surfaces

- Android MCP server is beta and local-bound by default.
- PetDex downloaded resources are beta and review-required unless separately
  verified.
- External Skills and external resources are review-required unless separately
  verified.

---
title: "Local service boundary contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["Security Agent", "Runtime Reliability Agent"]
review_agent: "Review Agent"
security_review: "required"
---

# Local Service Boundary Contract

## Status

Active for Month 1 public beta hardening.

Audience:

- Android Agent
- Terminal/Runtime Agent
- Security Agent
- QA/Release Agent
- Review Agent

## Purpose

NBG Android runs local services so the app can talk to its bundled Agent runtime, FTP file sharing, and Android MCP beta surface. These services must remain local-first, observable, and constrained. Public beta must not accidentally expose file writes, MCP tools, or Hanako control APIs to the device LAN.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
| Hanako loopback HTTP/WebSocket | Chat, tools, runtime health, and event stream between Android and bundled Hanako server. | `HanakoBridge.kt`, `HanakoApiClient.kt`, `HanakoServerLauncher.kt` |
| FTP file sharing | User-initiated local file transfer for Ubuntu root and uploads. | `NbgFileShareModel.kt`, `NbgFtpFileServer.kt` |
| Android MCP beta server | Standalone beta MCP service for read-only device info and echo. | `android-mcp-server/src/main/java/com/nbg/android/mcpserver` |
| Android network security config | Cleartext policy for local loopback only. | `app/src/main/res/xml/network_security_config.xml` |

## Trust Boundary

Local services cross process and protocol boundaries:

- Android app process to Hanako server process.
- Android app process to FTP server socket.
- Standalone MCP app process to MCP clients.
- Loopback HTTP/FTP/SSE boundaries.

Allowed network exposure:

- `127.0.0.1`
- `localhost`
- `::1` where a runtime patch or upstream library already treats it as local

Disallowed by default:

- `0.0.0.0`
- LAN interface bind addresses
- public network interfaces
- cleartext non-loopback HTTP

## Service Rules

### Hanako Loopback

- Android clients must use `http://127.0.0.1:{port}` and `ws://127.0.0.1:{port}/ws`.
- Health checks must send `Authorization: Bearer {token}`.
- WebSocket URLs must carry the runtime token.
- Non-loopback hostnames must not receive Android-specific upstream user-agent injection bypasses.

### FTP File Sharing

- FTP control and passive data sockets must bind to `127.0.0.1`.
- FTP must require username/password before file listing, reading, writing, deleting, renaming, or directory creation.
- FTP password must be generated locally and stored through AndroidKeyStore-backed encrypted storage.
- Legacy plaintext FTP password may be read once for migration and removed after encrypted save succeeds.
- Public beta FTP starts in explicit `ReadOnly` mode by default.
- Users must explicitly switch to `ReadWrite` mode before FTP accepts uploads, appends, deletes, renames, or directory creation.
- `ReadOnly` mode must reject `STOR`, `APPE`, `DELE`, `RMD`/`XRMD`, `MKD`/`XMKD`, `RNFR`, and `RNTO` before filesystem mutation.
- Switching FTP access mode or closing the FTP server must cancel pending passive transfers so an old `ReadWrite` session cannot complete a delayed write after the server is closed or replaced by `ReadOnly`.
- Protected virtual roots `/` and `/nbg-uploads` must not be deleted or replaced.

### Android MCP Beta Server

- Service must be `android:exported="false"`.
- App data backup must be disabled.
- HTTP server must bind to `127.0.0.1`.
- Android MCP beta requires a local bearer token even while tools remain read-only/echo-only.
- `GET`, `POST`, and `DELETE` requests to `/mcp` must include `Authorization: Bearer {token}` before opening SSE, handling JSON-RPC, or closing an MCP session.
- The bearer token is generated locally, stored in private app preferences with backup disabled, and exposed only through user-initiated MCP server UI copy/regenerate actions.
- Token regeneration takes effect for subsequent `/mcp` requests without requiring a server restart.
- Token copy uses Android clipboard sensitive metadata where available and clears the copied token after a short TTL only when Android reports no primary-clip change since the token copy; it must not read clipboard contents to decide.
- MCP running state is persisted only after socket bind succeeds; bind/accept-loop failure and service destruction must write `running=false`.
- Current beta tools are limited to `android_device_info` and `android_echo`.
- Any file, shell, app-control, notification, network, log export, or other side-effect MCP tool requires a new contract revision with authentication or explicit user confirmation.

## Error Semantics

| Error | Meaning | User-visible message | Recovery |
| --- | --- | --- | --- |
| FTP auth failure | Username/password mismatch. | Authentication failed. | Regenerate or copy current credentials from UI. |
| FTP bind failure | Preferred port unavailable or socket cannot bind. | Startup error in file sharing state. | Fallback to ephemeral port where possible; show current port. |
| Hanako health 401/failed | Token missing/wrong or runtime not ready. | Hanako reconnecting or failed. | Relaunch runtime and refresh server-info. |
| MCP auth failure | Bearer token missing or wrong. | MCP client receives HTTP 401. | Copy or regenerate the local token from the MCP server UI and update the client header. |
| MCP bind failure | MCP beta port unavailable. | MCP server stopped. | Stop conflicting service or restart MCP server. |

## Compatibility

Versioning:

- This contract is source-level and release-gate level for Month 1.

Migration:

- FTP password migration is legacy SharedPreferences plaintext -> AndroidKeyStore encrypted preference secret.
- URL API key migration is tracked separately in Issue 3 but uses the same encrypted secret store.

Backward compatibility:

- Existing FTP password remains usable after migration.
- Existing MCP URL remains `http://127.0.0.1:37666/mcp`; clients must add `Authorization: Bearer {token}`.

## Security Rules

- Cleartext is permitted only for loopback in the main app network security config.
- App and MCP backup must remain disabled while local credentials or service state exist.
- Local services must not bind `0.0.0.0` without a separate plan, explicit UI warning, and Security Agent review.
- Side-effect MCP tools require auth/confirmation before public beta.
- MCP bearer tokens must not be shown in foreground notifications, service logs, diagnostics export, or contract evidence.
- Diagnostics may include service status and port, but must not export FTP passwords or Hanako tokens.
- Diagnostics may report whether the MCP bearer token exists, but must not export the token value.
- Diagnostics must not export raw Hanako `server-info.json` by default. They may expose derived booleans such as `serverInfoPresent`, `pidPresent`, and `versionPresent`, plus redacted launcher/server log tails.

## Test Oracle

Unit/source tests:

- `AndroidManifestBehaviorTest.appLocalServicesStayLoopbackOnlyWithTokenOrPasswordBoundary`
- `AndroidManifestBehaviorTest.fileSharePasswordUsesEncryptedSecretStore`
- `NbgFtpFileServerTest`
- `NbgFtpFileServerTest.readOnlyModeAllowsListingAndRetrievalButRejectsWrites`
- `NbgFtpFileServerTest.closingServerCancelsPendingPassiveStoreBeforeFilesystemMutation`
- `McpServerBoundaryTest`
- `McpServerBoundaryTest.bearerAuthorizationRequiresExactLocalToken`
- `McpServerBoundaryTest.mcpHttpServerRejectsUnauthenticatedRequestsBeforeProtocolHandling`
- `McpServerBoundaryTest.mcpHttpServerReadsCurrentBearerTokenForEveryRequest`
- `McpServerBoundaryTest.mcpHttpServerPropagatesUnexpectedAcceptFailure`

Build/test commands:

```bash
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :android-mcp-server:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :android-mcp-server:assembleDebug
```

Manual tests before public beta:

- Start FTP and confirm UI shows loopback URL, username, and password.
- Confirm FTP starts in `ReadOnly`, allows directory listing and file retrieval, and rejects upload/delete/rename/mkdir commands.
- Switch FTP to `ReadWrite` explicitly and confirm authenticated upload/delete behavior still works.
- Attempt FTP login with wrong and correct password.
- Confirm MCP beta URL is loopback-only.
- Confirm MCP `/mcp` returns HTTP 401 without `Authorization: Bearer {token}` and succeeds with the copied local token.
- Confirm Hanako health uses token and non-loopback access is unavailable from another device.

## Operational Diagnostics

Expose or record:

- Service running state.
- Loopback URL and port.
- FTP access mode.
- Last bind/start failure.
- Whether credentials exist, without exporting credential values.
- MCP beta status and current tool count.
- MCP bearer token presence only, never the token value.
- Hanako server-info presence, pid presence, and version presence as booleans only; no raw server-info JSON, token, port, pid, or version value.

## Open Questions

- None for Month 1 public-beta local service authentication.

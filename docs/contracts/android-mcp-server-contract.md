---
title: "Android MCP Server Contract"
status: active
date: 2026-07-04
type: contract
target_repo: /root/nbg-android
owners: ["Android MCP Agent", "Security Agent"]
review_agent: "Security Agent"
security_review: "required"
---

# Android MCP Server Contract

## Status

Active for beta boundary hardening.

## Purpose

Define the standalone Android MCP server beta surface so it remains local-only, private to this app package, and read-only/echo-only until a separate side-effect tool plan exists.

This contract prevents:

- Accidental LAN exposure of the MCP HTTP/SSE endpoint.
- Adding file, shell, app-control, network-proxy, or device-control tools without a security plan.
- Treating the beta MCP server as an unauthenticated local API.
- Diagnostics or UI implying stronger guarantees than the current beta surface provides.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
| MCP activity | User-visible start/stop shell for the standalone MCP server app. | `android-mcp-server/src/main/java/com/nbg/android/mcpserver/MainActivity.kt` |
| MCP foreground service | Owns the server lifecycle, notification, private service boundary, and persisted running state. | `McpServerService.kt` |
| HTTP/SSE server | Handles `/mcp` GET/POST/DELETE and JSON-RPC messages. | `McpServerService.kt` |
| Contract object | Stable version, loopback host/port, protocol version, server identity, and tool list. | `AndroidMcpServerContract.kt` |
| Boundary tests | Source oracle for loopback/private/read-only beta behavior. | `McpServerBoundaryTest.kt` |

## Trust Boundary

- Process boundary: this is a separate Android app module/package from the main NBG Android app.
- Service boundary: `McpServerService` must remain `android:exported="false"`.
- Network boundary: the HTTP/SSE server must bind only to `127.0.0.1`.
- Auth boundary: every `/mcp` request must present the locally generated local bearer token through `Authorization: Bearer {token}`.
- Tool boundary: beta tools are limited to `android_device_info` and `android_echo`.
- Data boundary: tools must not read user files, app private files, contacts, notifications, clipboard, photos, precise location, or account data.

## Data Structures

Contract constants:

```kotlin
const val NBG_ANDROID_MCP_SERVER_CONTRACT_VERSION = "nbg-android-mcp-server-v1"
const val NBG_ANDROID_MCP_LOOPBACK_HOST = "127.0.0.1"
const val NBG_ANDROID_MCP_PORT = 37666
const val NBG_ANDROID_MCP_PROTOCOL_VERSION = "2025-11-25"
const val NBG_ANDROID_MCP_AUTH_SCHEME = "Bearer"
const val NBG_ANDROID_MCP_BEARER_TOKEN_BYTES = 32
const val NBG_ANDROID_MCP_BEARER_TOKEN_MIN_LENGTH = 32
const val NBG_ANDROID_MCP_DISCOVERY_POLICY_VERSION = "nbg-android-mcp-discovery-v1"
const val NBG_ANDROID_MCP_SIDE_EFFECT_POLICY_VERSION = "nbg-android-mcp-side-effect-v1"
```

Discovery policy:

```kotlin
val NBG_ANDROID_MCP_EPHEMERAL_PORT_REQUIRED_EVIDENCE = listOf(
  "main_app_discovery_contract",
  "client_migration_plan",
  "diagnostics_redaction_update",
)
```

Public-beta v1 keeps the fixed loopback port `37666`. Future ephemeral-port migration requires a main-app discovery contract, client migration plan, and diagnostics redaction update. Even when all future evidence labels are present, v1 must keep `useEphemeralPort=false`.

Side-effect tool policy:

```kotlin
val NBG_ANDROID_MCP_SIDE_EFFECT_REQUIRED_EVIDENCE = listOf(
  "main_app_permission_ui",
  "permission_risk_mapping",
  "diagnostics_redaction_update",
  "security_review",
)
```

Public-beta v1 keeps the standalone MCP server read-only/echo-only. Future side-effect tools belong behind the main app permission UI and Permission Risk Model, not directly in this standalone server, unless a new contract version and Security Agent review explicitly change that boundary.

Tool contracts:

| Tool | Boundary | Allowed Behavior |
| --- | --- | --- |
| `android_device_info` | `ReadOnly` | Return package name, Android manufacturer/model, SDK, and local port. |
| `android_echo` | `EchoOnly` | Return caller-provided text as a local echo response. |

## State Machine

| State | Entered When | Allowed Transitions | Terminal? |
| --- | --- | --- | --- |
| `Stopped` | Service is not running or user taps Stop. | `Starting` | No |
| `Starting` | Foreground service starts and creates loopback server. | `Listening`, `Failed` | No |
| `Listening` | Server socket is bound to `127.0.0.1:37666`. | `Stopped`, `Failed` | No |
| `EphemeralPortCandidate` | A future plan proposes replacing fixed port `37666` with dynamic port discovery. | `FixedPortRetained` in v1; future Security review only. | No |
| `FixedPortRetained` | Public-beta v1 keeps `127.0.0.1:37666`. | Normal `Listening` flow. | No |
| `SideEffectToolCandidate` | A future MCP tool can write files, run commands, control apps/devices, proxy network, or access private data. | `SideEffectBlocked` in v1; future main-app permission plan only. | No |
| `SideEffectBlocked` | Standalone server rejects side-effect tool expansion in v1. | None in v1. | Yes |
| `Failed` | Server start or accept loop fails. | `Starting`, `Stopped` | No |

## Endpoint Semantics

Endpoint:

- `GET /mcp`: opens SSE stream and sends an `endpoint` event with a per-session POST URL.
- `POST /mcp` or `POST /mcp?sessionId=...`: handles MCP JSON-RPC.
- `DELETE /mcp`: closes client side with `204 No Content`; service lifecycle remains user-controlled.

Auth:

- `GET`, `POST`, and `DELETE` requests to `/mcp` require `Authorization: Bearer {token}`.
- Missing, malformed, short, or wrong tokens return HTTP `401` with `WWW-Authenticate: Bearer realm="NBG Android MCP"`.
- The token is generated locally on first service start, stored in private app preferences with backup disabled, and can be copied or regenerated only from the MCP server UI.
- Token regeneration takes effect for subsequent `/mcp` requests without requiring a server restart.
- Token copy uses Android clipboard sensitive metadata where available and clears the copied token after a short TTL only when Android reports no primary-clip change since the token copy. The TTL clear path must not read clipboard contents.
- Foreground notifications, server info, tool responses, diagnostics, and contract evidence must not include the token value.

JSON-RPC methods:

- `initialize`: returns protocol version, tools capability, server info, and contract version.
- `tools/list`: returns the fixed beta tool list with each tool boundary.
- `tools/call`: executes only tools declared in `NBG_ANDROID_MCP_TOOLS`; unknown names return an MCP tool error payload, not a side effect.

## Error Semantics

| Error | Meaning | Recovery |
| --- | --- | --- |
| HTTP `401` | Bearer token is missing or wrong. | Copy the local token from the MCP server UI and send it as `Authorization: Bearer {token}`. |
| HTTP `404` | Path is not `/mcp`. | Use the local `/mcp` endpoint. |
| HTTP `405` | Method is unsupported. | Use GET, POST, or DELETE. |
| Start failure | Server socket could not bind or accept loop failed. | UI status must not report running after failure. |
| JSON-RPC `-32601` | Unknown JSON-RPC method. | Use `initialize`, `tools/list`, or `tools/call`. |
| Tool `isError=true` | Unknown tool name. | Call one of the listed beta tools. |

## Compatibility

- Contract version: `nbg-android-mcp-server-v1`.
- The server identity remains `nbg-android-mcp-server` version `0.1.0`.
- The beta port is fixed at `37666` until a separate migration plan exists.
- The endpoint URL remains `http://127.0.0.1:37666/mcp`; existing clients must add the bearer `Authorization` header.
- Adding any side-effect tool requires a new contract version, permission-risk mapping, UI warning, diagnostics coverage, and Security Agent review.
- Public-beta v1 does not migrate to ephemeral ports even if future evidence labels exist.
- Public-beta v1 does not add standalone side-effect tools even if future evidence labels exist.

## Security Rules

- The server must bind to `127.0.0.1`, never `0.0.0.0`, LAN, or public interfaces.
- `McpServerService` must stay unexported.
- App data backup must stay disabled.
- `/mcp` must reject unauthenticated GET/POST/DELETE requests before opening SSE, handling JSON-RPC, or closing a session.
- Bearer token comparison must not accept partial, short, wrong-scheme, or extra-suffix values.
- Running state must be persisted only after socket bind succeeds; startup failure and service destruction must write `running=false`, while startup failure details remain available as last error until the next successful bind.
- Bearer tokens must not appear in notifications, logs, diagnostics export, tool results, or contract evidence.
- The beta server must not invoke `Runtime`, `ProcessBuilder`, shell commands, file output streams, delete/write APIs, package installation APIs, accessibility APIs, or notification/clipboard reads. The token-copy UI may write the user-requested token to the clipboard and observe primary-clip change notifications only to avoid clearing later user clipboard content.
- Future side-effect tools must be reviewed as main-app permission UI work with Permission Risk Model mapping before any standalone-server expansion is considered.
- Tool responses must not include secrets or private file content.

## Test Oracle

Unit/source tests:

- `McpServerBoundaryTest.mcpServerServiceIsPrivateLoopbackOnlyAndReadOnlyBeta`
- `McpServerBoundaryTest.bearerAuthorizationRequiresExactLocalToken`
- `McpServerBoundaryTest.mcpHttpServerRejectsUnauthenticatedRequestsBeforeProtocolHandling`
- `McpServerBoundaryTest.mcpHttpServerReadsCurrentBearerTokenForEveryRequest`
- `McpServerBoundaryTest.mcpHttpServerPropagatesUnexpectedAcceptFailure`
- `McpServerBoundaryTest.mcpDiscoveryAndSideEffectPoliciesStayClosedInV1`
- `McpServerBoundaryTest.androidMcpServerContractIsDocumentedAndWired`
- `AndroidManifestBehaviorTest.androidMcpServerContractIsDocumentedAndWired`

Commands:

```bash
./gradlew --no-daemon :android-mcp-server:testDebugUnitTest
./gradlew --no-daemon :android-mcp-server:assembleDebug
```

## Open Questions

- None for v1. Public-beta v1 keeps fixed loopback port `37666`; future ephemeral-port migration requires a main-app discovery contract, client migration plan, diagnostics redaction update, and Security review.
- None for v1 side-effect tools. Public-beta v1 keeps the standalone MCP server read-only/echo-only; future side-effect tools belong behind the main app permission UI and Permission Risk Model unless a new contract version explicitly changes the boundary.

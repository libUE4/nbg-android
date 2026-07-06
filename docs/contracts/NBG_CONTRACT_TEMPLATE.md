---
title: "NBG contract title"
status: draft
date: YYYY-MM-DD
type: contract
target_repo: /root/nbg-android
owners: []
review_agent: "Review Agent"
security_review: "required | not-required"
---

# NBG Contract Template

## Status

Draft / Active / Deprecated.

Audience:

- Implementing agents
- Review agents
- QA/release agents
- External integrators, if any

## Purpose

Define the stable behavior this contract protects and the bugs it should prevent.

## Participants

| Participant | Responsibility | Code Area |
| --- | --- | --- |
|  |  |  |

## Trust Boundary

Describe what data or commands cross boundaries.

Include:

- Local app process boundary.
- Terminal/proot boundary.
- Hanako server boundary.
- MCP/external resource boundary.
- User confirmation boundary.

## Data Structures

Use Kotlin/JSON-like shapes where possible.

```kotlin
data class ExampleContractState(
    val id: String,
    val status: String,
    val lastError: String?
)
```

## State Machine

| State | Entered When | Allowed Transitions | Terminal? |
| --- | --- | --- | --- |
|  |  |  |  |

## Endpoint Or Event Semantics

For an API, WebSocket, MCP tool, file format, or event stream, document:

- Name
- Direction
- Required fields
- Optional fields
- Ordering guarantees
- Idempotency
- Timeout behavior
- Retry behavior

## Error Semantics

| Error | Meaning | User-visible message | Recovery |
| --- | --- | --- | --- |
|  |  |  |  |

## Compatibility

Versioning:

- 

Migration:

- 

Backward compatibility:

- 

Deprecation:

- 

## Security Rules

- Sensitive fields:
- Redaction rules:
- Confirmation requirements:
- Hash/signature requirements:
- Network/egress requirements:
- Audit/logging requirements:

## Test Oracle

Unit tests:

- 

Integration tests:

- 

Manual tests:

- 

Negative tests:

- 

## Operational Diagnostics

The contract must expose enough state for diagnostics:

- Current status
- Last successful action
- Last failure
- Version or schema
- Recovery hint
- Redacted export shape

## Open Questions

- 


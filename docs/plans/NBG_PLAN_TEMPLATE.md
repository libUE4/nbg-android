---
title: "NBG plan title"
status: draft
date: YYYY-MM-DD
type: plan
target_repo: /root/nbg-android
origin: "user request / issue id / incident / roadmap item"
owner_agent: "Coordinator Agent"
review_agent: "Review Agent"
security_review: "required | not-required"
---

# NBG Plan Template

## Summary

State the goal in one paragraph. Include the user-visible outcome, target module, and release reason.

## Problem Frame

Describe the current behavior, why it is insufficient, and what breaks if the work is not done.

Include:

- Current user impact.
- Current technical limitation.
- Known risks.
- Why this needs a plan instead of a small patch.

## Requirements

Use stable requirement IDs.

| ID | Requirement | Priority | Acceptance |
| --- | --- | --- | --- |
| R1 |  | P0/P1/P2 |  |
| R2 |  | P0/P1/P2 |  |

## Key Technical Decisions

Record decisions that future agents should not re-litigate without new evidence.

| Decision | Choice | Reason | Tradeoff |
| --- | --- | --- | --- |
| D1 |  |  |  |

## High-Level Technical Design

Describe the architecture at module level.

Include:

- Participants and responsibilities.
- Data flow.
- State transitions.
- Compatibility and migration path.
- Failure and fallback behavior.

## Implementation Units

### U1: Unit Name

Goal:

- 

Requirements:

- R1

Dependencies:

- 

Files/Areas:

- 

Approach:

- 

Patterns to Follow:

- Prefer existing NBG module patterns and stable state holders.
- Keep Android core small; push optional behavior to Skills, MCP, URL API, or terminal/runtime edge when possible.

Test Scenarios:

- 

Verification:

- Command:
- Manual behavior:
- Evidence:

### U2: Unit Name

Goal:

- 

Requirements:

- 

Dependencies:

- 

Files/Areas:

- 

Approach:

- 

Test Scenarios:

- 

Verification:

- 

## Scope Boundaries

In scope:

- 

Out of scope:

- 

Non-goals:

- 

## Security And Privacy

Threat Model:

- 

Trust Boundary:

- 

Sensitive Data:

- 

Confirmation Policy:

- 

Local Storage Policy:

- 

Network/Egress Policy:

- 

## Risks And Mitigations

| Risk | Impact | Mitigation | Owner |
| --- | --- | --- | --- |
|  |  |  |  |

## Verification Strategy

Required commands:

```bash
./gradlew :app:assembleDebug
```

Behavior checks:

- 

Regression checks:

- 

Evidence to attach:

- Test output
- Screenshots or screen recording for UI work
- Logs for runtime work
- Redaction sample for diagnostics work

## Rollout And Recovery

Rollout:

- 

Compatibility:

- 

Recovery:

- 

## Sources And Research

- User request:
- Related code:
- Related plan:
- Related contract:
- Hermes reference:


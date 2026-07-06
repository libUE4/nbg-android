# NBG Android Public Beta Release Notes Template

Use this template for every GitHub Releases public beta candidate. Replace all
`TODO` entries before publishing.

## Release Summary

- Version: TODO
- Build date: TODO
- APK: TODO
- SHA-256: TODO
- Supported ABI: arm64-v8a
- Minimum Android/device notes: TODO

## What Is Included

- Android-native AI IDE loop: chat, terminal, file/diff review, and diagnostics.
- Local Agent platform surfaces: Skills, Memory, MCP beta, and pet status.
- Local-first data handling by default.

## Beta Labels And Exceptions

- Android MCP server: beta.
- PetDex and downloaded pet resources: beta.
- External Skills and external resources: review-required unless separately verified.
- JitPack-backed dependencies: disclosed beta dependency source.
- Unsigned local candidate APKs are not publishable; public artifacts require external release signing.

## Privacy And Local Data

- Link `docs/release/NBG_PUBLIC_BETA_PRIVACY_LOCAL_DATA.md`.
- Code, files, memories, API keys, terminal output, and diagnostics are local-first by default.
- Diagnostics export is user-triggered and redacted by default.
- No cloud telemetry is added for public-beta evidence.

## Checksums And Supply Chain

- Attach or paste `build/release-gate/checksums.sha256`.
- Attach or paste `build/release-gate/assets.sha256`.
- Record whether `build/release-gate/review.txt` has `candidate_evidence_complete=true`.
- Record whether `publish_ready=true` or the publish blocker.

## Known Issues

- TODO: device smoke evidence status.
- TODO: external release signing status.
- TODO: manual UI/device verification gaps.
- TODO: remaining beta exceptions or P1 follow-ups.

## Verification Evidence

- Release gate command: TODO
- Release evidence review: TODO
- Unit/lint summary: TODO
- Device smoke/manual demo evidence: TODO
- Redacted diagnostics sample: TODO

## Feedback And Diagnostics

- Tell testers how to export the redacted diagnostics bundle.
- Tell testers to use `.github/ISSUE_TEMPLATE/public_beta_feedback.md`.
- Tell testers what evidence to attach and to review diagnostics for secrets first.

## Upgrade And Recovery Notes

- Link `docs/release/NBG_PUBLIC_BETA_QUICK_START.md`.
- Note URL API key migration expectations.
- Note session recovery expectations.
- Note local service boundaries for FTP, Hanako loopback HTTP, and Android MCP beta.

# Changelog

## 2026-10-01 — documentation complement for release 0.1.4

Session `/sc:document`; no product binaries, sources, or packaged artifacts changed — this entry covers repository documentation, licensing, and the session's tooling reports. Product versions remain: companion & setup 0.1.4 (versionCode 5), extension 0.2.2, PWA shell `voiceish-shell-v014`.

### Added
- `README.md` (repository root) — front door: contents table, 3-step quick start, documentation map, build-from-source, tests, security summary, boundaries, versioning.
- `DOCS/ARCHITECTURE.md` — Architecture-Law snapshot of the finished product: components, protocol v1, data stores, security model, closed-set enumerations (18 error codes, states, message sets, routes), workflows, screens, build/release pipeline, and the exhaustive §12 entity enumeration table with engaged-by mapping to CHECKLIST tasks.
- `CHECKLIST.md` (root) — recipe T1–T12, each task self-contained and order-independent, attesting exact E-IDs with acceptance criteria; T1–T11 attested `[x]` against the released tree by the 2026-10-01 verification gate; **T12 physical-handset verification open**.
- `LICENSE` (root) — entirely proprietary, all rights reserved, © 2026 Robin L. M. Cheung, MBA (Admin-Manual SPEC_CONVENTIONS SC5; upstream licence text inside `source/` remains a subsumed historical artifact). `README.md` licence statement aligned.
- `DOCS/SESSION-REPORT-2026-10-01.md` — deliverables, verification-gate record, licensing note, Admin-Manual local-access disclosure, and the operator-requested kstore assessment (first-attempt prompt-support deficiency, retrieval-quality, storage, guardrail findings).
- `CHANGELOG.md` (this file).

### Changed
- Global agent rules (`~/.claude/CLAUDE.md`, `~/AGENTS.md`) — new section "Admin-Manual retrieval — kstore first (single source of truth)" with the authorized local-fallback and disclose-in-report obligations.
- Admin-Manual (local copy; operator to sync/commit-push) — `SPEC_CONVENTIONS` SC5 added: standing licence default (entirely proprietary) with the non-blocking compatibility-concern rule.

### Verification
- Entity-table gate: 22/22 load-bearing rows semantically verified against disk (line-content comparison); version constants cross-checked (versionCode 5 / 0.2.2 / cert pin).
- Outstanding: `CHECKLIST.md` T12 physical-handset verification items — deliberately left unchecked.

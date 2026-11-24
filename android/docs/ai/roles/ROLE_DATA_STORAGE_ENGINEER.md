# Oz RetroRom — Data / Storage Engineer

**Role ID:** `ROLE_DATA_STORAGE_ENGINEER`
**Governance:** V2
**Required level:** Senior Android Data & Storage Engineer
**Authority:** Specialist advisor/implementer under the Product / Technical Lead and the active Gate.

## 1. Mission

Guarantee durable, recoverable and ownership-safe persistence for ROMs, metadata, settings and temporary artifacts.

## 2. Product Outcome

Oz never loses or destroys user-owned content and can recover cleanly from partial writes, process death and migration.

## 3. Scope

- SAF/ContentResolver/DocumentFile
- Room and migrations
- checksums/integrity
- atomic writes
- ownership semantics
- temporary/persistent artifact lifecycle
- multidisc/bundles
- orphan/corruption recovery
- large-file storage planning

## 4. Non-Scope

- deleting imported user files without ownership proof
- using display title as storage identity
- assuming filesystem paths where only URIs are durable

## 5. Required Expertise

- Android storage
- SAF persistable permissions
- Room
- transactional writes
- hashing
- large files
- migration
- crash consistency
- cleanup policies

## 6. Oz Invariants

- PREPARE → WRITE → FLUSH → VERIFY → PERSIST METADATA → SWITCH REFERENCE → CLEAN OLD OZ-OWNED ARTIFACT
- ownership before deletion
- temporary artifact is not persistent library
- multidisc completeness is explicit

## 7. Decision Framework

1. Classify ownership and artifact type.
2. Choose durable target and permission model.
3. Write atomically where possible.
4. Verify bytes/metadata.
5. Commit reference only after successful persistence.
6. Clean only Oz-owned obsolete artifacts.

## 8. Technology Radar

- Android scoped storage/SAF changes
- Room versions
- filesystem/page-size/device storage constraints
- large-file URI APIs

## 9. Toolchain

- ContentResolver/DocumentFile
- Room tooling
- adb shell/content inspection
- checksum utilities
- unit/instrumentation tests

## 10. Primary Sources

- Android storage docs
- Room official docs
- current schema/source code
- device storage behavior

## 11. Input Contract

Before acting, obtain or explicitly mark UNKNOWN:

- artifact ownership
- source URI/path
- target library location
- expected size/hash
- release/disc metadata

## 12. Workflow

1. Validate permission/space.
2. Prepare destination.
3. Write and flush.
4. Verify.
5. Persist metadata transactionally.
6. Switch authoritative reference.
7. Clean safe obsolete state.

## 13. Output Contract

- persistent artifact
- metadata/reference update
- ownership record
- recovery/cleanup result

## 14. Evidence Contract

- URI/path and ownership class
- size/hash
- transaction result
- process-death/recovery test
- multidisc completeness

No evidence may be upgraded from `NOT RUN`/`UNKNOWN` to `PASS`/`VERIFIED` by inference.

## 15. Failure Modes

- orphaned partial
- stale Room reference
- permission revoked
- insufficient space mid-copy
- duplicate artifacts
- user-owned file deletion

## 16. Anti-patterns

- write then trust
- delete before reference switch
- path-only persistence under SAF
- cleanup by filename similarity

## 17. Review Checklist

- Who owns the bytes?
- Is write atomic/recoverable?
- What happens if process dies between write and metadata?
- Can permissions survive restart?
- Are large files and multidisc tested?

## 18. Escalation Rules

['Escalate before any migration that can orphan or delete user content or requires irreversible schema/storage changes.']

## 19. Definition of Done

- The role-specific review is recorded with evidence, not confidence language.
- No invariant of Oz RetroRom is silently weakened.
- Applicable tests/gates are PASS; anything not executed is explicitly NOT RUN.
- New debt, assumptions and unresolved risk are written into the operational documentation.
- The single next step remains explicit and compatible with the active Gate.

## 20. Oz-specific Playbooks

- SAF library import
- atomic artifact replacement
- multidisc persistence
- orphan cleanup
- Room migration

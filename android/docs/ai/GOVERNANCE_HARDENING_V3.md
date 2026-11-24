# Oz RetroRom - Governance Hardening V3

Status: ACTIVE AMENDMENT
Scope: Resilience, continuity, evidence and AI portability

## 1. Purpose

Governance must be enforceable, recoverable and portable.

Rules that exist only in a chat are not project governance.

The repository must contain enough code, evidence, decisions and
instructions for another qualified human or AI agent to continue Oz.

## 2. Canonical truth

Order of authority:

1. Current repository and code
2. Git history and current branch
3. Build, tests, APK and physical-device evidence
4. AI_PROJECT_MASTER.md
5. PROJECT_DASHBOARD.md
6. Approved design artifacts
7. Governance roles and protocols
8. Conversation history
9. Assumptions

A lower source cannot silently override a higher source.

## 3. Remote Recovery Point Objective

Significant work must never remain only on one device for an
unbounded period.

Maximum acceptable exposure:

- one coherent micro-iteration; or
- one significant validated change before the next checkpoint.

When in doubt, checkpoint.

## 4. Mandatory remote checkpoint triggers

A remote checkpoint is mandatory before:

- changing from Termux to Windows or vice versa;
- changing AI provider or project workspace;
- changing development device;
- risky refactors;
- destructive or broad Git operations;
- beginning a new major subgate on top of significant local work;
- ending a work session with meaningful unpushed changes.

It is also mandatory after:

- meaningful Build/Test PASS;
- important real-device validation;
- approval of a major visual specification;
- resolution of a high-value bug.

## 5. Commit semantics

Use:

checkpoint: ...

for coherent recoverable WIP that is not yet product-approved.

Use normal fix/feat/docs commits only when their stated scope is true.

A checkpoint does not imply Gate closure.

## 6. Environment handoff gate

Before changing environment:

1. git diff --check
2. update project evidence when applicable
3. explicit git staging
4. commit
5. push
6. fetch
7. verify local HEAD == upstream
8. verify expected working-tree state

No significant environment handoff may occur before this gate passes.

## 7. AI portability

Oz must not depend on one AI vendor, chat or account.

A replacement agent must be able to continue by reading:

- docs/AI_PROJECT_MASTER.md
- docs/PROJECT_DASHBOARD.md
- docs/ai/
- docs/design/
- current Git history
- current code and tests

No critical architectural decision may exist only in conversation memory.

## 8. Visual artifact preservation

Approved mockups, meaningful design references and important
real-device validation captures must be versioned in:

docs/design/mockups/
docs/design/references/
docs/design/validation/

Do not store every experiment.

Store artifacts that represent:

- approved design specifications;
- important alternatives under formal review;
- regression evidence;
- Visual Parity evidence;
- product decisions.

Approved artifacts must never depend only on a chat attachment.

## 9. Evidence gate

A significant implementation is not CLOSED only because it compiles.

Relevant gates include:

- Build
- Tests
- Interaction
- Visual Parity
- Physical device
- Diagnostics
- Documentation
- Git provenance

The applicable evidence must be recorded before closure.

## 10. Decision rights

Specialists provide evidence and recommendations.

Product/Technical Lead integrates conflicting recommendations into
one implementation path.

No specialist may silently expand the Active Gate.

Cross-gate findings go to backlog unless they block the current Gate.

## 11. AI autonomy boundary

AI may act autonomously inside an approved Gate only when:

- architecture is understood;
- truth hierarchy is respected;
- destructive operations are excluded;
- evidence requirements are known.

If those conditions fail, stop and inspect before modifying code.

## 12. Recovery and rollback

Git stash is temporary local protection, not backup.

Remote Git is the canonical recovery layer.

A portable git bundle should periodically be stored independently
of the primary Git hosting provider.

Destructive Git commands remain prohibited without explicit approval.

## 13. Design specification rule

Once a mockup is declared APPROVED, it becomes a specification.

Implementation must be compared against it using Visual Parity.

Changing the implementation away from an approved mockup requires
either:

- evidence-driven correction; or
- explicit design revision.

## 14. Governance amendment traceability

Every governance change must state:

- problem being solved;
- rule introduced;
- enforcement mechanism;
- date;
- commit.

Governance must evolve from evidence, not ceremony.

## 15. External conceptual anchors

This amendment was informed by publicly available Harvard material
on:

- AI alignment;
- responsible AI use;
- risk identification and mitigation;
- decision criteria for AI adoption;
- governance as rule-making plus implementation capacity;
- balancing autonomy with institutional capacity.

These sources inform the principles but do not certify Oz governance.

<!-- DOWNLOAD_1B_1C_GOVERNANCE_BOUNDARY_20260820 -->
## 16. Download Gate boundary — resilient core vs advanced torrent durability

Decision date: 2026-08-20
Decision authority: Product / Technical Lead

`DOWNLOAD-1B` remains ACTIVE and owns the transport-neutral resilient core:
safe validator-aware HTTP resume, bounded retry, network recovery, persistent
ordered candidates, deterministic failover, source-isolated partials, Internet
Archive resilience, multidisc recovery, progress truth, background survival,
essential diagnostics and complete applicable validation evidence.

`DOWNLOAD-1B` may persist torrent candidate identity and selected torrent-file
metadata and may fail over from an interrupted or exhausted torrent candidate.
It must not claim durable torrent byte recovery or libtorrent fast-resume.

`DOWNLOAD-1C — Advanced Torrent Durability` is BACKLOG / NOT ACTIVE and owns:

- persistent torrent session storage;
- libtorrent fast-resume data;
- torrent process-death byte recovery;
- torrent-specific lifecycle hardening;
- torrent-specific advanced telemetry.

Moving this scope does not weaken DOWNLOAD-1B evidence requirements and does not
close either Gate. DOWNLOAD-1C requires separate activation under Single Active
Path before implementation begins.

The approved remaining DOWNLOAD-1B order is:

1. `1B.3a` — persistent transport-neutral source plans and candidate ownership;
2. `1B.3b` — HTTP validators/If-Range and candidate-specific partial safety;
3. `1B.3c` — deterministic failover, Retry-After, multidisc integration,
   diagnostics and complete validation.


<!-- GOVERNANCE_PERFORMANCE_STARTUP_20260820 -->
## Performance governance addendum — startup, catalog and collections

For startup/catalog/collection performance work:

- Performance Engineer participation is mandatory.
- OBSERVABILITY_DIAGNOSTICS_PROTOCOL is mandatory.
- Measure before optimizing.
- Persisted valid state should be treated as a product asset, not discarded on routine launch without a verified invalidation reason.
- Do not solve latency by weakening catalog correctness, ROM identity, collection integrity or persistence guarantees.
- A Build PASS is insufficient.
- Closure requires before/after evidence plus interaction and physical-device validation.
- If the root cause crosses catalog, collections, database, network or UI-thread boundaries, report the cross-domain scope before expanding implementation.

<!-- GOVERNANCE_EMU_PROVISION_01_20260820 -->
## Emulation provisioning governance addendum

For RetroArch installation/core provisioning work:

- Inspect the existing Oz RetroArch install/download flow before proposing replacement logic.
- Do not duplicate working functionality.
- Emulation Integration Engineer participation is mandatory.
- Android Engineer, Security/Privacy, QA and Product/Technical Lead participate when package detection, downloaded artifacts or exported Activities/Intents are involved.
- Verify all current package names, Activities, Intents, ABI/build paths, core artifacts and upstream distribution behavior from primary/official sources immediately before implementation.
- Never invent core compatibility or emulator contracts.
- Preserve external-emulator routing.
- Never write directly into another app's private storage.
- BIOS/firmware must be handled separately from redistributable cores.
- Provisioning must be persistent and must not repeat unconditionally on app startup.
- PASS requires physical-device interaction evidence, not build success alone.

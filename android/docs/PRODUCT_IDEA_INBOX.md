# Oz RetroRom — Product Idea Inbox

Ideas here do **not** change the active Gate automatically.

| ID | Title | Why it matters | Status | Future Gate | Dependencies |
|---|---|---|---|---|---|
| OZ-IDEA-001 | AI gameplay-similarity collections | Discover games from mechanics/play style, not only broad genre | RESEARCH | COLLECTION-AI | Game Knowledge Curator, verified metadata, ROM Identity |
| OZ-IDEA-002 | Per-system curated collections | Console-native discovery from NES through later supported systems | DESIGN | COLLECTION-SYSTEM | Catalog/collection model |
| OZ-IDEA-003 | CRT / PVM / Trinitron presentation modes | Stronger mature retro identity without generic emulator chrome | RESEARCH | VISUAL-FUTURE | Visual Engineering, performance/device budget |
| OZ-IDEA-004 | Oz RetroRom for Windows | Extend Oz product ecosystem beyond Android | IDEA | WINDOWS-00 | Stable Android domain contracts, product scope decision |
| OZ-IDEA-005 | Smart artwork focal-point crop | Preserve faces/logos/gameplay focal content across hero/tile ratios | DESIGN | VISUAL-ARTWORK | Artwork model + Visual Engineering |

When adding an idea, record why it matters, status, future Gate and dependencies; then return to the active Gate.

<!-- OZ_BACKLOG_CATALOG_COLLECTION_PERF -->
## PERF - Catalog and Collections Loading

**Status:** BACKLOG
**Scope:** measure and correct perceptible latency, blocking and excessive work while loading/indexing/rendering catalog and collections.
**Rule:** treat as an independent Performance Gate; do not mix with DOWNLOAD-1B.
**Validation:** baseline measurement -> root cause -> optimization -> before/after benchmark -> real-device evidence.

<!-- OZ_BACKLOG_FLYCAST_NATIVE_CRASH -->
## EMU - Dreamcast Flycast Native Crash

**Status:** BACKLOG / BUG
**Evidence:** RetroArch native crash points to `flycast_libretro_android.so` after Dreamcast download.
**Required investigation:** core version, content format, launch configuration and Oz/RetroArch integration.
**Rule:** do not classify as Download failure without evidence.

<!-- IDEA_FIRST_RUN_TASTE_BRAND_20260820 -->
## 2026-08-20 — First Run / Taste / Brand ideas

### FIRST-RUN-01 — Setup & Taste Onboarding
Status: BACKLOG

Create a first-launch experience that configures storage, verifies RetroArch readiness, asks for favorite consoles/games and builds a Taste Profile that seeds personalized collections before the user reaches the normal Home experience.

Key rule: **Oz resolves technical choices; the user supplies personal choices.**

### RetroArch one-action install handoff
Status: BACKLOG

When RetroArch is missing:
- show a clear primary install action;
- automatically select the appropriate supported official build for the device;
- never ask novice users to choose ABI/32-bit/64-bit;
- offer a secondary re-check/continue action;
- verify official source/package/build contracts immediately before implementation.

### Taste Engine
Status: BACKLOG

Use canonical identity + curated experiential metadata + deterministic scoring.
AI may rank/name/explain recommendations but must not define canonical game identity.

### Personalized first collections
Status: BACKLOG

Seed collections from:
- favorite consoles;
- favorite games;
- experiential preferences;
- canonical saga/franchise data;
- library availability.

### RetroArch Quick Resume onboarding
Status: BACKLOG

Guide Auto Save State + Auto Load State first.
Future bridge: Oz owns metadata/reference state; RetroArch owns binary save-state files.

### BRAND-01
Status: IDEA / RESEARCH

Explore **GameOZ**, **OZ PLAY** and **OZ/PLAY**.
No rename yet.
Visual direction: less generic neon, stronger OZ mark, vivid multi-accent controller-era palette without copying proprietary symbols.

<!-- IDEA_PERF_CATALOG_COLLECTIONS_20260820 -->
## PERF-CATALOG-01 — Catalog and collections startup latency

Priority: HIGH.

Problem:
Catalog and collections can re-synchronize on every app launch and leave the UI stalled for a couple of minutes.

Desired experience:
Open Oz immediately from persisted valid state; refresh only what changed, preferably incrementally and without blocking the user.

Research before implementation:
startup triggers, sync/index invalidation, collection regeneration, persistence/cache use, duplicate work, main-thread blocking, disk/database/network cost, cold vs warm start.

Rule:
measure first; do not guess or rewrite the subsystem broadly.

<!-- IDEA_EMU_PROVISION_01_20260820 -->
## EMU-PROVISION-01 — Zero-Touch Core Provisioning

Priority: P1.

Observed tester friction:
- Oz apparently already helps obtain RetroArch;
- after installing RetroArch, the tester still had to manually download a core.

Desired experience:
The user selects systems; Oz prepares the approved emulation engine/core stack automatically or with the minimum supported handoff.

Important first step:
Audit the existing RetroArch install/download path before adding new code.

Related:
- FIRST-RUN-01;
- external-emulator routing;
- Game Knowledge / canonical platform identity;
- future device capability profiles.

<!-- GAME_DETAIL_SHORTLIST_IDEA_20260820 -->
## Game Detail — Hero Shelf vs Saga Navigator

Current product preference is narrowed to two directions:

1. Hero Shelf — artwork-first, immediate, elegant.
2. Saga Navigator — richer Oz identity, canonical saga/session context.

Potential final solution may be a deliberate fusion:
Hero Shelf composition + selected Saga Navigator context,
but this is an idea, not an approved design.

Preserve both before further iteration.

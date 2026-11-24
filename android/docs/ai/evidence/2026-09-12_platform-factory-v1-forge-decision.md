# PLATFORM FACTORY V1 — FORGE DECISION

**Date:** 2026-09-12
**Branch:** `feature/gba-catalog-v1`
**Recorded by:** Claude Code (executor), transcribing a decision made by the
Product Owner / Forge. This document records the decision; it does not itself
authorize or execute any implementation beyond what is stated.

## DECISION

The following are recorded as approved Product Owner / Forge decisions, not
as conclusions Claude Code reached independently:

1. Executive priority is now **PLATFORM-FACTORY-V1**.
2. Objective: reduce the marginal work required to onboard future systems.
3. GBA remains reference/foundation assurance; no new GBA feature scope is
   opened by this decision.
4. Remaining GBA evidence is not erased or falsely closed by this decision.
5. New platform identity should use the **Source Index** as the canonical
   control/identity path.
6. The legacy `ROOM_CATALOG` backend is **not** to be expanded as the future
   architecture merely because `arcade-fbneo` already exists there.
7. The Factory reuses the existing generic Source Index / acquisition /
   Local Copy foundations as-is.
8. A mega `PlatformDefinition` object is explicitly avoided.
9. Preferred architecture direction: existing `SourceIndexCatalogConfig` +
   existing `EmulatorProfile` + a small future `PlatformMediaProfile`,
   subject to Phase 1 design/implementation evidence (not yet built).
10. First Platform Factory pilot target: **Neo Geo AES/MVS**.
11. **BEST EMULATOR WINS** remains binding; no emulator winner has been
    selected for Neo Geo AES/MVS yet.
12. The downloader is shared infrastructure and is **not** to be rewritten
    inside Platform Factory V1.
13. Phase 0 characterization: **PASS**.
14. Phase 1 requires a separate PLAN, reviewed before any production change —
    Phase 1 implementation is not authorized by this document.

## EVIDENCE

Decision item 13 (Phase 0 characterization PASS) is backed by
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-0-characterization.md`:
`PlatformCoreResolutionCharacterizationTest` (8/8 platforms), plus the two
pre-existing `NesCoreResolutionContractTest`/`GenesisCoreResolutionContractTest`
(1/1 each), all PASS, zero production files changed, `git diff --check` clean.

Decisions 3-4 (GBA foundation/evidence untouched) are backed by the same
Phase 0 evidence doc's confirmation that no file under the GBA repair surface
(`PersistentRomLibrary.kt`, `PersistentRomLibraryRealSafDeviceTest.kt`, or any
`docs/ai/evidence/*gba*` artifact) was modified.

Decisions 5-9 (architecture direction) restate, verbatim in intent, the
recommendation already derived read-only in
`PLATFORM FACTORY V1 — IMPLEMENTATION PLAN` §5 (minimum platform contract)
and §19 (inference: `arcade-fbneo` already exists in the legacy backend with
accumulated, scattered debt) — that plan is the evidentiary basis Forge's
decision is adopting, not a new independent finding produced here.

No repository evidence exists yet for decision 10 (Neo Geo AES/MVS as pilot):
the read-only plan explicitly marked the pilot family choice **UNKNOWN**,
pending external research. Its selection here is a Product Owner/Forge
decision, not a repo-derived conclusion.

## UNKNOWN

- Exact field shape of the future `PlatformMediaProfile` — deferred to the
  required Phase 1 PLAN, not decided here.
- Real BIOS filenames, core name(s), or package/Activity identifiers for Neo
  Geo AES/MVS — not researched in this document; must come from primary/official
  sources when Phase 1/pilot work is authorized, never invented.
- Which emulator (if any) will be qualified for Neo Geo AES/MVS — explicitly
  open per decision 11.
- Whether AES and MVS are treated as one systemId or two under the Source
  Index model — not addressed by this decision, left to the Phase 1 PLAN.
- Timing/authorization for Phase 1 itself — this document records that a PLAN
  is required next; it does not schedule or approve Phase 1 implementation.

## STOP_TO_FORGE

YES

# PLATFORM FACTORY V1 — PHASE 1B ASSURANCE

**Date:** 2026-09-12
**Branch:** `feature/gba-catalog-v1`
**Recorded by:** Claude Code (executor), transcribing an independent Forge assurance review of
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1b-media-profile-wiring.md`. This document
records the assurance decision; it does not itself re-derive or re-verify the underlying evidence
beyond the precision correction noted below.

## EVIDENCE

**Anchor test — genuine before/after proof, run twice within Phase 1B:**
`RomArchivePreparerMigratedPlatformAnchorTest` (9 tests, hardcoded literal expected values for
nes/snes/genesis/gba/n64, including the N64 z64-over-n64 case) was run once against the unmodified
`RomArchivePreparer.kt` (9/9 PASS) and once again, unchanged, after the wiring change (9/9 PASS).
This is the one test suite with a directly evidenced before-and-after pair inside Phase 1B itself.

**Precision correction to the Phase 1B execution doc:** that document's TEST RESULTS section
stated "29/29 tests passed across all six suites, both before and after the production change."
This overstates what was directly evidenced. What is actually supported by the session record:
- The anchor suite (9 tests): genuinely run and passing both before and after, as above.
- The other five suites — `PlatformMediaProfileCharacterizationTest` (6),
  `PlatformCoreResolutionCharacterizationTest` (8), `RomArchivePreparerOpticalTest` (4),
  `NesCoreResolutionContractTest` (1), `GenesisCoreResolutionContractTest` (1), 20 tests total —
  were run once, **after** the Phase 1B wiring change, all 20 PASS. They were also run (all PASS)
  during the separate Phase 1A execution, which necessarily used code that had not yet received
  the Phase 1B wiring (Phase 1A did not touch `RomArchivePreparer.kt`) — so indirect evidence
  supports they would have passed "before" too, but they were not re-run as an explicit paired
  before/after check within Phase 1B's own validation sequence.
- Total directly evidenced **after** Phase 1B's wiring: 29/29 PASS, 0 failures, 0 errors. This
  figure itself is accurate and fully evidenced.

**Production diff:** confirmed minimal and exactly as designed — `RomArchivePreparer.kt`'s
`nativeExtensions()`/`extensionPriority()` now consult `PlatformMediaProfiles.forSystem(systemId)`
first, falling back to renamed (`legacyNativeExtensions`/`legacyPreferredExtension`) tables with
exactly 5 and 3 entries removed respectively, matching
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1b-media-profile-wiring.md`'s recorded diff.

## INFERENCE

The precision correction above does not change the assurance conclusion: the one suite that
matters most for regression detection (the anchor, with literal independent values) genuinely
proves before/after equivalence for all five migrated platforms including the N64 edge case. The
other 20 tests passing after wiring, combined with them having passed on the pre-wiring code during
Phase 1A, is strong — if slightly less directly sequenced — support for the same conclusion.

## UNKNOWN

Nothing new. BIOS, FBNeo, downloader, Source Index, GBA SAF repair and emulator selection remain
untouched and outside this review's scope.

## FORGE DECISION

**PHASE_1B: PASS.**

- `PlatformMediaProfile` is accepted as runtime authority for
  `nativeExtensions`/`preferredExtension` for NES, SNES, Genesis, GBA and N64.
- Legacy fallback remains authoritative for all non-migrated systems.
- No observable behavior regression demonstrated.
- N64 `z64` preference preserved.
- BIOS/FBNeo/downloader/Source Index/GBA SAF remain outside this changeset.
- Mass-migrating remaining legacy systems merely for cleanup is explicitly not authorized.

## RISK

**R2** (unchanged classification) — narrow scope, but the modified file is shared production
foundation used today by NES/SNES/Genesis/GBA (frozen-adjacent) and N64. Assurance review found no
basis to raise or lower this classification.

## RESULTING ARCHITECTURE

`PlatformMediaProfile` is now the live runtime authority for native-extension and
preferred-extension resolution for NES, SNES, Genesis, GBA and N64. Every other systemId remains
governed by the original (renamed) legacy tables, unchanged. This is now also recorded as a stable
architecture invariant in `AI_PROJECT_MASTER.md` (§9, "Platform media configuration").

## NEXT

Executive next step: **Neo Geo Pilot PLAN** (design only — no implementation, no core/BIOS
selection, per the pilot target already recorded in `PROJECT_DASHBOARD.md`). BIOS migration and any
further platform migration remain separately gated, not authorized by this assurance pass. GBA
residual assurance remains a distinct, untouched validation stream.

# PLATFORM FACTORY V1 — PHASE 1B MEDIA PROFILE WIRING PLAN

**Date:** 2026-09-12
**Branch:** `feature/gba-catalog-v1`
**Status:** Materialized from a completed Plan Mode analysis (read-only investigation, no
production code touched during planning). Execution evidence is recorded separately in
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1b-media-profile-wiring.md`.
**Risk classification:** R2 — scope is narrow but touches shared production foundation
(`RomArchivePreparer.kt`) used today by NES/SNES/Genesis/GBA (frozen-adjacent) and N64.

## CURRENT OWNERSHIP

`nativeExtensions()`, `extensionPriority()` and `extractionLimit()` in `RomArchivePreparer.kt` are
three independent `when(systemId)` tables. `needsNativeExtraction()` and `prepareZip()` have no
tables of their own — they only call the three above. `PlatformMediaProfile.kt` (Phase 1A) already
exists and is already characterized against this behavior (20/20 tests PASS), but is not read by
any production code yet.

## PROPOSED WIRING

One-sentence rule: **if a `PlatformMediaProfile` exists for a `systemId`, that profile is the sole
source of truth for `nativeExtensions` and `preferredExtension` for that system — never a partial
mix of profile and legacy table for the same `systemId`.** If no profile exists, the `systemId`
remains 100% legacy-governed, unchanged.

## FUNCTION-BY-FUNCTION

**Only 2 of 5 traced functions require direct wiring:**

| Function | Wired? | Why |
|---|---|---|
| `nativeExtensions(systemId)` | YES | the actual table to replace with a profile lookup |
| `extensionPriority(name, systemId)` (private) | YES | same, includes the N64 case |
| `extractionLimit(systemId)` | NO | all 5 migrated platforms already fall to the generic 4 GiB default both in legacy and in the profile (`extractionSizeLimit=null`) — wiring would be routing with no hardcoding to remove |
| `needsNativeExtraction(...)` | NO | already delegates to `nativeExtensions()`; inherits correctness for free |
| `prepareZip(...)` | NO | composes transitively through the two functions that do change |

## FALLBACK CONTRACT

Forge's proposed rule (profile exists → profile; absent → legacy) is correct, with one precision
the repo evidence requires: the switch is **per whole `systemId`, never per field**. If a profile
exists for "nes", everything about "nes" extensions comes from the profile — even when
`preferredExtension` is `null` (resolved via `profile.nativeExtensions.firstOrNull()`, never by
falling through to the legacy table). Mixing sources per field for the same systemId would
introduce ambiguity the evidence doesn't require.

## HARDCODING REMOVED

1. **5 entries** (nes, snes, genesis, gba, n64) removed from `nativeExtensions()`'s
   `when(systemId)` — replaced by `PlatformMediaProfiles.forSystem(id).nativeExtensions`. Parity
   already proven by Phase 1A's `nativeExtensionsMatchCurrentRomArchivePreparerBehavior`.
2. **3 entries** (snes→sfc, genesis→md, n64→z64) removed from `extensionPriority()`'s
   `when(systemId)` — replaced by `profile.preferredExtension`. Parity already proven by Phase
   1A's `preferredExtensionResolvesToCurrentExtensionPriorityWinner` and
   `n64PrefersZ64OverN64EvenThoughN64ListsFirstInNativeExtensions`. (`nes`/`gba` never had an
   explicit entry here — nothing to remove for them.)

## LEGACY BRANCHES RETAINED

Everything else in `RomArchivePreparer.kt`: the ~15 remaining `nativeExtensions()` entries, the ~5
remaining `extensionPriority()` entries, all of `extractionLimit()`,
`MULTI_FILE_OPTICAL_SYSTEMS`/`MULTI_FILE_DESCRIPTOR_EXTENSIONS`, and the
`if (systemId=="arcade-fbneo") return downloaded` early return. None of the 5 migrated systems
interacts with these branches; touching them is explicitly out of scope.

## ANCHOR-TEST STRATEGY

Once `RomArchivePreparer.nativeExtensions("nes")` literally reads from the profile, Phase 1A's
test comparing "production vs. profile" becomes **circular** for the 5 migrated platforms (both
sides are the same value by construction) — it stops being able to catch a future regression. A
new anchor test with **intentionally literal hardcoded expected values** (the one deliberate
exception to "never duplicate expected values by hand" in this whole design, precisely because it
must stay independent of the profile it protects) is required, run first against the unmodified
`RomArchivePreparer` to prove it's a faithful snapshot, then re-run unchanged after wiring.

## ROLLBACK

Revert the `RomArchivePreparer.kt` commit — `PlatformMediaProfile.kt` and its data are untouched by
Phase 1B, and no persisted data (Room or otherwise) is involved, so rollback is a single-file
revert with no data migration.

## IMPLEMENTATION READINESS

READY, executed in this exact sequence: (1) write the anchor test and run it against current,
unmodified `RomArchivePreparer` to confirm it's a faithful snapshot; (2) wire `RomArchivePreparer.kt`;
(3) re-run the anchor test unchanged plus the existing regression suites; (4) validate
`git diff --check`/`git status`.

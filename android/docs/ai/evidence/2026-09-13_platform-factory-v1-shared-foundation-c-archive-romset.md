# PLATFORM FACTORY V1 — SHARED FOUNDATION SLICE C: ARCHIVE_AS_ROMSET RUNTIME WIRING

**Date:** 2026-09-13
**Branch:** `feature/gba-catalog-v1`
**WORK_ID:** PLATFORM-FACTORY-V1.SHARED-FOUNDATION-C-ARCHIVE-ROMSET-01
**Risk:** R2 (per Forge's 2026-09-13 correction — shared Platform Factory contract, regardless of
diff size)

## SCOPE

Make `MediaKind.ARCHIVE_AS_ROMSET` an explicit runtime choke point in `RomArchivePreparer.kt`,
replacing the historical `systemId == "arcade-fbneo"` literal check, with byte-for-byte identical
behavior for every current systemId. No Neo Geo data added, no firmware/launch-options work, no
unrelated refactor.

## EVIDENCE (before state)

`RomArchivePreparer.kt` (post Phase 1B, pre this changeset) had exactly two independent literal
checks against `"arcade-fbneo"`:
- `prepare()` line 33: `if (game.systemId == "arcade-fbneo") return@withContext downloaded`
- `needsNativeExtraction()` line 129: `systemId != "arcade-fbneo" && ...`

`MediaKind.ARCHIVE_AS_ROMSET` existed in `PlatformMediaProfile.kt` but was confirmed (repeatedly,
across this session's investigations) never read by any production code.

## BEFORE

Anchor test `RomArchivePreparerArchiveAsRomsetAnchorTest` (6 tests, hardcoded/literal expectations)
was written and run **before** touching `RomArchivePreparer.kt`:

```
tests=6, failures=0, errors=0 (against unmodified RomArchivePreparer.kt)
```

Covers: arcade-fbneo not needing extraction (zip and 7z), the 5 migrated SINGLE_FILE platforms still
needing extraction, N64's z64-over-n64 preference, and optical multi-file descriptor
rejection/eligibility — confirming a faithful pre-change snapshot.

## IMPLEMENTATION

Single new choke-point function in `RomArchivePreparer.kt`:

```kotlin
internal fun isArchiveAsRomset(
    systemId: String,
    profile: PlatformMediaProfile? = PlatformMediaProfiles.forSystem(systemId),
): Boolean = profile?.let { it.mediaKind == MediaKind.ARCHIVE_AS_ROMSET } ?: (systemId == "arcade-fbneo")
```

Used at both former literal-check sites:
- `prepare()`: `if (isArchiveAsRomset(game.systemId)) return@withContext downloaded`
- `needsNativeExtraction()`: `!isArchiveAsRomset(systemId) && ...`

The optional `profile` parameter exists solely so tests can exercise the `ARCHIVE_AS_ROMSET` branch
with a synthetic `PlatformMediaProfile` instance, without registering any real platform data in
`PlatformMediaProfiles` (none was added — `PlatformMediaProfile.kt` was not modified).

`extractionLimit()`, `MULTI_FILE_OPTICAL_SYSTEMS`/`requiresUnsupportedBundlePersistence`, the
zip-vs-7z distinction, `prepareZip()`, and every other function are untouched.

## AFTER

Same anchor test re-run **unchanged**, plus a new wiring test proving the new branch:

```
RomArchivePreparerArchiveAsRomsetAnchorTest (re-run UNCHANGED):  tests=6, failures=0, errors=0
RomArchivePreparerArchiveAsRomsetWiringTest (new):               tests=8, failures=0, errors=0
PlatformMediaProfileCharacterizationTest (Phase 1A, unmodified): tests=6, failures=0, errors=0
RomArchivePreparerOpticalTest (pre-existing, unmodified):        tests=4, failures=0, errors=0
RomArchivePreparerMigratedPlatformAnchorTest (Phase 1B, unmodified): tests=9, failures=0, errors=0
PlatformCoreResolutionCharacterizationTest (Phase 0, unmodified): tests=8, failures=0, errors=0
NesCoreResolutionContractTest (pre-existing, unmodified):        tests=1, failures=0, errors=0
GenesisCoreResolutionContractTest (pre-existing, unmodified):    tests=1, failures=0, errors=0
```

`BUILD SUCCESSFUL`, **43/43 tests passed** across all eight suites. No existing test was modified.

The new `RomArchivePreparerArchiveAsRomsetWiringTest` proves, via synthetic (non-registered)
`PlatformMediaProfile` fixtures:
- `isArchiveAsRomset("arcade-fbneo")` → `true` (legacy fallback preserved, no profile registered)
- `isArchiveAsRomset("nes"/"snes"/"genesis"/"gba"/"n64")` → `false` (migrated SINGLE_FILE profiles
  unaffected)
- `isArchiveAsRomset("some-unregistered-system")` → `false` (unchanged default)
- `isArchiveAsRomset("synthetic-romset", PlatformMediaProfile(mediaKind=ARCHIVE_AS_ROMSET))` → `true`
  (new branch genuinely works)
- `isArchiveAsRomset` with synthetic `SINGLE_FILE`/`MULTI_FILE_OPTICAL` profiles → `false` in both
  cases (only `ARCHIVE_AS_ROMSET` triggers preservation)
- `needsNativeExtraction()` re-verified false for arcade-fbneo (zip and 7z) and true for all 5
  migrated platforms, post-wiring

## TESTS

Full list executed this changeset: `RomArchivePreparerArchiveAsRomsetAnchorTest` (new, run twice —
before and after), `RomArchivePreparerArchiveAsRomsetWiringTest` (new), plus six pre-existing suites
re-run unmodified as regression: `PlatformMediaProfileCharacterizationTest`,
`RomArchivePreparerOpticalTest`, `RomArchivePreparerMigratedPlatformAnchorTest`,
`PlatformCoreResolutionCharacterizationTest`, `NesCoreResolutionContractTest`,
`GenesisCoreResolutionContractTest`.

## BEHAVIOR_CHANGE

NONE for any current systemId. `arcade-fbneo` behaves identically (still bypasses extraction
entirely); all 5 migrated `PlatformMediaProfile` systems behave identically (still extract via their
native-extension tables); all systemIds without a profile behave identically (fall to the same
literal-equivalent default). The only new capability is dormant: a *future* profile with
`mediaKind = ARCHIVE_AS_ROMSET` would now be honored, but none exists in the registry yet.

## LEGACY_FALLBACK

Confirmed preserved exactly: `isArchiveAsRomset(systemId)` falls back to
`systemId == "arcade-fbneo"` whenever `PlatformMediaProfiles.forSystem(systemId)` returns `null` —
which is the case for every systemId today except the 5 already-migrated `SINGLE_FILE` platforms.
No systemId lost or gained archive-preservation behavior.

## ROLLBACK

`git checkout -- app/src/main/java/cl/gakuto/kizuna/launch/RomArchivePreparer.kt` restores the
pre-Slice-C file exactly (Phase 1B's state). No data/Room migration is involved; `PlatformMediaProfile.kt`
was not touched by this changeset either way.

## UNKNOWN

Nothing new. No Neo Geo data, firmware, or launch-options work was touched or investigated in this
changeset, per explicit constraint.

## RISK

R2 (per Forge's classification) — this changeset modifies `RomArchivePreparer.kt`, shared production
foundation used today by every platform's download/launch-prep path. Mitigated by: an anchor test
run both before and after the change proving zero behavior difference for all current systemIds,
plus six pre-existing regression suites re-run unmodified, all green (43/43 total).

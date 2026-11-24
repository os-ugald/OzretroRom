# PLATFORM FACTORY V1 — PHASE 1A MEDIA PROFILE CONTRACT

**Date:** 2026-09-12
**Branch:** `feature/gba-catalog-v1`
**Executor:** Claude Code (repository executor), AUTO mode, scope narrowed by Forge to the media
contract only (BIOS explicitly deferred).

## SCOPE

Implement only the approved Phase 1A slice from
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1-media-contract-plan.md`: create a
`PlatformMediaProfile` contract covering `mediaKind`, `nativeExtensions`, `preferredExtension`,
`extractionSizeLimit` for NES, SNES, Genesis, GBA and N64, characterized against current
`RomArchivePreparer` behavior. `BiosRequirementSpec`/`biosRequirements`/`regionBucketed` and any
FBNeo BIOS/merge change are explicitly **not** implemented here. `RomArchivePreparer.kt` is
**not** modified — the profile is not yet consumed by production code.

## EXACT CONTRACT IMPLEMENTED

```kotlin
enum class MediaKind { SINGLE_FILE, ARCHIVE_AS_ROMSET, MULTI_FILE_OPTICAL }

data class PlatformMediaProfile(
    val systemId: String,
    val mediaKind: MediaKind = MediaKind.SINGLE_FILE,
    val nativeExtensions: Set<String> = emptySet(),
    val preferredExtension: String? = null,
    val extractionSizeLimit: Long? = null,
)
```

No BIOS-related type or field was added, per the Forge narrowing.

## PROFILES CREATED

`app/src/main/java/cl/gakuto/kizuna/launch/PlatformMediaProfile.kt`, `PlatformMediaProfiles.all`:

| systemId | mediaKind | nativeExtensions | preferredExtension | extractionSizeLimit |
|---|---|---|---|---|
| nes | SINGLE_FILE (default) | nes, fds, unf, unif | null | null |
| snes | SINGLE_FILE (default) | sfc, smc, swc, fig, bs, st | "sfc" | null |
| genesis | SINGLE_FILE (default) | md, gen, bin, smd | "md" | null |
| gba | SINGLE_FILE (default) | gba | null | null |
| n64 | SINGLE_FILE (default) | n64, z64, v64 | "z64" | null |

## N64 PREFERRED-EXTENSION FINDING

Confirmed directly against production: `RomArchivePreparer.nativeExtensions("n64")` lists `"n64"`
first, but `RomArchivePreparer`'s (private) tie-break table explicitly prefers `"z64"`. The new
test builds a real synthetic ZIP containing one entry per native extension (`rom.n64`, `rom.z64`,
`rom.v64`) and calls the actual, unmodified `RomArchivePreparer.prepareZip(...)` — it still selects
`rom.z64` today. `PlatformMediaProfile("n64").preferredExtension = "z64"` matches this exactly; had
the profile omitted this field and relied on list order, it would have silently diverged from
production (selecting `"n64"` instead).

## FILES CREATED

- `app/src/main/java/cl/gakuto/kizuna/launch/PlatformMediaProfile.kt` (new)
- `app/src/test/java/cl/gakuto/kizuna/launch/PlatformMediaProfileCharacterizationTest.kt` (new)
- `docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1-media-contract-plan.md` (new, Step 0 of this changeset)
- This document (new)

## FILES MODIFIED

None in production or test code. `docs/PROJECT_DASHBOARD.md` updated narrowly (see below).
`RomArchivePreparer.kt` and every other file listed under FILES_NOT_TO_TOUCH in the Phase 1 plan
were **not** touched — confirmed by `git diff --name-only` showing only `docs/PROJECT_DASHBOARD.md`
among tracked files.

## TESTS EXECUTED

```
./gradlew.bat :app:testDebugUnitTest \
  --tests "cl.gakuto.kizuna.launch.PlatformMediaProfileCharacterizationTest" \
  --tests "cl.gakuto.kizuna.launch.PlatformCoreResolutionCharacterizationTest" \
  --tests "cl.gakuto.kizuna.launch.RomArchivePreparerOpticalTest" \
  --tests "cl.gakuto.kizuna.launch.NesCoreResolutionContractTest" \
  --tests "cl.gakuto.kizuna.launch.GenesisCoreResolutionContractTest"
```

## EXACT RESULTS

```
PlatformMediaProfileCharacterizationTest:   tests=6, failures=0, errors=0
  everyMigratedPlatformHasAProfile                                     PASS
  nativeExtensionsMatchCurrentRomArchivePreparerBehavior               PASS
  mediaKindMatchesCurrentArchiveHandlingForMigratedPlatforms           PASS
  extractionSizeLimitMatchesCurrentGenericDefaultForMigratedPlatforms  PASS
  preferredExtensionResolvesToCurrentExtensionPriorityWinner           PASS
  n64PrefersZ64OverN64EvenThoughN64ListsFirstInNativeExtensions        PASS

PlatformCoreResolutionCharacterizationTest (Phase 0, re-run unmodified): tests=8, failures=0, errors=0
RomArchivePreparerOpticalTest (pre-existing, unmodified):                tests=4, failures=0, errors=0
NesCoreResolutionContractTest (pre-existing, unmodified):                tests=1, failures=0, errors=0
GenesisCoreResolutionContractTest (pre-existing, unmodified):            tests=1, failures=0, errors=0
```

`BUILD SUCCESSFUL`, 20/20 tests passed across all five suites. No test was modified to make the
new profile pass — parity was verified against real, unmodified `RomArchivePreparer` public
functions (`nativeExtensions`, `needsNativeExtraction`, `prepareZip`), not hand-copied expected
values.

`git diff --check`: clean (exit 0). `git status --porcelain` confirmed only the new files above
(untracked) plus the narrow `docs/PROJECT_DASHBOARD.md` edit (tracked, modified) — no staging, no
commit, no push.

## EVIDENCE

All of the above is directly observed: test-result XML under
`app/build/test-results/testDebugUnitTest/`, and `git status`/`git diff --check` output from this
session.

## INFERENCE

The contract as designed correctly represents current production behavior for all five migrated
platforms, including the one genuinely non-trivial case (N64). This is a positive signal that the
field set (`mediaKind`, `nativeExtensions`, `preferredExtension`, `extractionSizeLimit`) is
sufficient for the MEDIA concern without needing additional fields — no gap was found while writing
the characterization test.

## UNKNOWN

Nothing new. BIOS remains fully deferred per the Forge narrowing; no BIOS-related fact was
investigated or assumed in this changeset.

## REGRESSIONS

NONE. All 20 tests across the five suites passed; no discrepancy was found between
`PlatformMediaProfile` and current `RomArchivePreparer` behavior, so the "report discrepancy and
STOP_TO_FORGE" branch of the task instructions was not triggered.

## RUNTIME BEHAVIOR CHANGE

NONE. `RomArchivePreparer.kt` was not modified; the new profile is inert data not yet read by any
production code path.

## PHASE 1B READINESS

Phase 1A is complete and green. Phase 1B (`RomArchivePreparer` actually consuming
`PlatformMediaProfile` for the five migrated platforms, with fallback to the existing tables for
everything else) is designed in the Phase 1 plan but requires separate Forge authorization before
any change to `RomArchivePreparer.kt` itself. The BIOS phase remains a distinct, later, separately
reviewed piece of work.

## RISK

R1 bounded execution inside R2 Platform Factory work (per Forge's own framing) — this specific
changeset is pure addition (new file + new test), zero production behavior change, fully covered
by parity tests against real production functions.

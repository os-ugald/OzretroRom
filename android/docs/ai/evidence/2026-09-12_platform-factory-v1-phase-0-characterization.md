# PLATFORM FACTORY V1 — PHASE 0 CHARACTERIZATION

**Date:** 2026-09-12
**Branch:** `feature/gba-catalog-v1`
**HEAD at execution:** `139207e1af935bc75c92f1fd8470ee7bf41a0ffd`
**Executor:** Claude Code (repository executor), AUTO mode, scope explicitly authorized by Product Owner for this Phase 0 step only.

## SCOPE

Implement only the Phase 0 characterization test defined in the read-only
`PLATFORM FACTORY V1 — IMPLEMENTATION PLAN` (recommended first changeset,
§22): a host test proving the current, real consistency between the
`launchValidated` platform registry (`app/src/main/assets/libretro_systems.json`)
and `EmulatorProfiles`, before any Platform Factory refactor (Phase 1+) touches
production code. No production behavior, no downloader, no GBA repair, no
Neo Geo/CPS1/CPS2 integration, no capability-flag or emulator-mapping change
was in scope, and none was made.

## NEW TEST FILE

`app/src/test/java/cl/gakuto/kizuna/launch/PlatformCoreResolutionCharacterizationTest.kt`
(new, untracked; no existing file modified). `NesCoreResolutionContractTest.kt`
and `GenesisCoreResolutionContractTest.kt` were kept as-is — not deleted, not
merged — because `GenesisCoreResolutionContractTest` also asserts an unrelated
`trusted_cores.json` invariant this new test does not cover, so deduplicating
them was not clearly safe.

## TEST CONTRACT

For every `systemId` where `libretro_systems.json` has `launchValidated: true`:

1. `EmulatorProfiles.forSystem(systemId)` exists.
2. The registry's `coreId`, resolved through `RetroArchManager.coreCandidatesFor(...)`
   (the real production alias/normalization logic — `normalizeCoreId` plus the
   azahar/panda3ds, swanstation/pcsx_rearmed, and FBNeo-name alias table), includes
   `EmulatorProfile.retroArchCore`.
3. The registry's `virtualExtension` is present in `EmulatorProfile.acceptedExtensions`.
4. The RetroArch core-artifact URL and resolved on-device core path derived from
   `EmulatorProfile.retroArchCore` match `RetroArchManager.developmentCoreArtifactUrl`/
   `resolvedCorePath`.

The test runs under `ParameterizedRobolectricTestRunner` (already available via
the existing `org.robolectric:robolectric:4.16.1` test dependency) so it can
instantiate a real `RetroArchManager(context)` and exercise the actual
production alias-resolution code, rather than re-implementing that logic by
hand inside the test.

## PLATFORMS COVERED

The 8 systems currently marked `launchValidated: true` in the registry:
`arcade-fbneo, nes, snes, n64, 3ds, genesis, ps1, ps2`.

`gba` is correctly excluded (`launchValidated: false` in the registry today).

## EXACT EXECUTED TEST RESULTS

Command: `./gradlew.bat :app:testDebugUnitTest --tests "cl.gakuto.kizuna.launch.PlatformCoreResolutionCharacterizationTest" --tests "cl.gakuto.kizuna.launch.NesCoreResolutionContractTest" --tests "cl.gakuto.kizuna.launch.GenesisCoreResolutionContractTest"` — `BUILD SUCCESSFUL`.

```
PlatformCoreResolutionCharacterizationTest: tests=8, skipped=0, failures=0, errors=0
  registryProfileCoreAndExtensionAgree[arcade-fbneo]  PASS
  registryProfileCoreAndExtensionAgree[nes]           PASS
  registryProfileCoreAndExtensionAgree[snes]          PASS
  registryProfileCoreAndExtensionAgree[n64]           PASS
  registryProfileCoreAndExtensionAgree[3ds]           PASS
  registryProfileCoreAndExtensionAgree[genesis]       PASS
  registryProfileCoreAndExtensionAgree[ps1]           PASS
  registryProfileCoreAndExtensionAgree[ps2]           PASS

NesCoreResolutionContractTest:     tests=1, failures=0, errors=0
GenesisCoreResolutionContractTest: tests=1, failures=0, errors=0
```

Source: `app/build/test-results/testDebugUnitTest/TEST-cl.gakuto.kizuna.launch.PlatformCoreResolutionCharacterizationTest.xml` and the two pre-existing tests' result XML in the same directory (local build output, not versioned).

## ZERO PRODUCTION BEHAVIOR CHANGE

Confirmed by `git status --porcelain`: the only new entry is the test file
itself; no tracked file (production or otherwise) was modified. `git diff --check`
returned clean (exit 0). No staging, commit, or push was performed.

## ALIAS-RESOLUTION FINDING

Two of the eight platforms only pass because the production alias table is
real and was exercised, not because of literal string equality:

- `ps1`: registry `coreId` is `pcsx_rearmed_libretro_android.so`; `EmulatorProfile.retroArchCore`
  for `ps1` is `swanstation_libretro_android.so`. These differ literally, but
  `RetroArchManager.coreCandidates()` (`RetroArchManager.kt:532-539`) explicitly
  lists `swanstation_libretro_android.so` as an alias candidate for
  `pcsx_rearmed_libretro_android.so`, so the profile's core is a valid resolved
  candidate.
- `arcade-fbneo`: registry `coreId` is the literal string `"FBNeo"`; `EmulatorProfile.retroArchCore`
  is `fbneo_libretro_android.so`. `RetroArchManager.normalizeCoreId()`
  (`RetroArchManager.kt:113-116`) maps `"fbneo"` (case-insensitive) to
  `fbneo_libretro_android.so` directly.

The two pre-existing tests (`Nes`, `Genesis`) never exercised this alias path,
because both of those platforms' registry `coreId` already equals
`EmulatorProfile.retroArchCore` literally. A naive generalization using literal
string equality (rather than `coreCandidatesFor`) would have reported these two
platforms as FAILing, which would have been a false positive — an artifact of
an incorrect test, not a real product inconsistency. This is recorded here
because it is the one non-obvious fact this Phase 0 step surfaced, not because
it required or triggered any production fix.

## EVIDENCE

Everything under EXACT EXECUTED TEST RESULTS and ZERO PRODUCTION BEHAVIOR
CHANGE above is directly observed (test XML output, `git status`/`git diff --check`
output from this session). The alias table cited under ALIAS-RESOLUTION FINDING
is read directly from `app/src/main/java/cl/gakuto/kizuna/launch/RetroArchManager.kt`
lines 113-116 and 532-539 at the HEAD commit above.

## INFERENCE

The real current contract between the platform registry and `EmulatorProfiles`
is "the registry's `coreId`, after production alias resolution, is compatible
with the profile's `retroArchCore`" — not literal string equality. This
characterization test is the first place in the repository that states that
contract explicitly and generally, rather than it being implicit in whichever
individual platforms happen not to need aliasing.

## UNKNOWN

Nothing new. No platform outside the current `launchValidated=true` set was
exercised; this step makes no claim about Neo Geo/CPS1/CPS2/Arcade or any
other unimplemented platform.

## REGRESSIONS

NONE. All 8 parameterized cases and both pre-existing tests passed; no
inconsistency was found, so no FAIL was recorded and no STOP-and-report-only
branch of the Phase 0 instructions was triggered.

## PHASE_1_READY

YES — this characterization test is green and available as the regression
gate for the Platform Factory V1 Phase 1 refactor (extracting a
`PlatformMediaProfile` contract), subject to separate Forge authorization
before Phase 1 itself begins.

## RISK

R0 — test-only addition, zero production change, nothing staged or committed.

## RESULTING FORGE STATE

Not yet recorded by Forge. This document reports Phase 0 execution only; it
does not itself constitute Forge approval to proceed to Phase 1, and no such
approval is asserted here.

## STOP_TO_FORGE

YES

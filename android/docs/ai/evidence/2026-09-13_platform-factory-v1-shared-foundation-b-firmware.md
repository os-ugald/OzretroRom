# PLATFORM FACTORY V1 — SHARED FOUNDATION SLICE B: GENERIC FIRMWARE CONTRACT

**Date:** 2026-09-13
**Branch:** `feature/gba-catalog-v1`
**WORK_ID:** PLATFORM-FACTORY-V1.SHARED-FOUNDATION-B-FIRMWARE-01
**Risk:** R2 (shared Platform Factory contract, regardless of diff size)

## SCOPE

Introduce a small, dedicated firmware profile/registry (`PlatformFirmwareProfile`/
`PlatformFirmwareProfiles`) as an optional runtime authority in front of
`RetroBiosValidator.requirements()`'s existing hardcoded table, with byte-for-byte identical
behavior for every current systemId. No Neo Geo firmware data added, no launch/core-options work,
`FbneoSupport`/`FbneoBiosMerger`/ROMgi BIOS paths untouched (no compile-time reference forced any
investigation there).

## EVIDENCE (before state)

`RetroBiosValidator.requirements(game, displayName)` was a single hardcoded `when(game.systemId)`
block: `"nes"` (conditional on `.fds` extension), `"dsi"` (4 fixed files), `"sega-cd"`/`"saturn"`
(region-bucketed via `regionBucket(game.region)`), `"pc-engine-cd"` (1 fixed file), else empty.
`validate()`, `regionBucket()`, and `indexFiles()` were already fully generic and reusable.
`FbneoSupport`/`FbneoBiosMerger` are a structurally different mechanism (ROMgi acquisition + ZIP
splice, keyed on `GameEntity.biosSets` per-game) — confirmed unrelated to this contract, untouched.

## BEFORE

Anchor test `RetroBiosValidatorRequirementsAnchorTest` (13 tests, hardcoded/literal expected
`Requirement` values — not derived from the production `when` table) was written and run **before**
touching `RetroBiosValidator.kt`:

```
tests=13, failures=0, errors=0 (against unmodified RetroBiosValidator.kt)
```

Covers every legacy branch: nes with/without `.fds`, dsi's 4 fixed files, sega-cd's three region
buckets (japan/europe/usa) plus the unknown-region "accept any" case, saturn's three region buckets
(japan / usa+europe sharing `mpr-17933.bin`) plus unknown-region case, pc-engine-cd, and five
unregistered systemIds (gba, snes, genesis, n64, arcade-fbneo) requiring nothing.

## CONTRACT

```kotlin
// New file: launch/PlatformFirmwareProfile.kt
data class FirmwareRequirement(val label: String, val alternatives: Set<String>)

data class PlatformFirmwareProfile(
    val systemId: String,
    val requirements: List<FirmwareRequirement> = emptyList(),
)

object PlatformFirmwareProfiles {
    val all: List<PlatformFirmwareProfile> = emptyList()
    fun forSystem(systemId: String): PlatformFirmwareProfile? = all.firstOrNull { it.systemId == systemId }
}
```

Deliberately mirrors `RetroBiosValidator.Requirement` (`label` + `alternatives`) exactly — nothing
more. **No `regionBucketed`-style field was added.** Region-dependent bucketing (sega-cd/saturn)
stays entirely inside `RetroBiosValidator`'s legacy table, unmigrated: nothing currently registered
needs to express that bucketing as data, and adding a field with zero consumers would be exactly the
speculative-field problem this slice must avoid. This remains an open Forge question for a *future*
slice, not resolved here — consistent with the Shared Foundation Plan's own note that
`regionBucketed`'s shape was deferred pending a platform that actually needs it.

`PlatformFirmwareProfiles.all` is **intentionally empty** — this slice ships the contract, not any
platform's firmware data (Neo Geo included).

## IMPLEMENTATION

`RetroBiosValidator.requirements()` now consults a profile first, with an optional parameter for
testability (mirrors the exact pattern already used in Slice C's `isArchiveAsRomset`):

```kotlin
fun requirements(
    game: GameEntity,
    displayName: String,
    profile: PlatformFirmwareProfile? = PlatformFirmwareProfiles.forSystem(game.systemId),
): List<Requirement> =
    profile?.requirements?.map { Requirement(it.label, it.alternatives) }
        ?: legacyRequirements(game, displayName)
```

The original `when` block was renamed to `legacyRequirements(game, displayName)`, verbatim, with
zero entries removed or altered. `validate()`, `regionBucket()`, and `indexFiles()` are completely
untouched — reused exactly as-is.

## AFTER

Same anchor test re-run **unchanged**, plus a new wiring test proving the new branch:

```
RetroBiosValidatorRequirementsAnchorTest (re-run UNCHANGED):        tests=13, failures=0, errors=0
RetroBiosValidatorFirmwareProfileWiringTest (new):                  tests=6,  failures=0, errors=0
```

`BUILD SUCCESSFUL`, **19/19 tests passed**. No pre-existing test was modified. No other test in the
repository references `RetroBiosValidator`/`FbneoSupport`/`FbneoBiosMerger` (confirmed by grep), so
no additional regression suite exists to re-run for this contract.

The new `RetroBiosValidatorFirmwareProfileWiringTest` proves, via synthetic (non-registered)
`PlatformFirmwareProfile` fixtures passed through the `profile` parameter:
- a synthetic profile becomes authority over the legacy table for its systemId;
- its `alternatives` set is passed through unmodified (acceptable-alternatives behavior correct);
- a synthetic profile with an empty `requirements` list correctly means "no firmware needed" (not a
  fall-through to legacy);
- calling `requirements()` with no `profile` argument (the real production call shape) still
  resolves via `PlatformFirmwareProfiles.forSystem()`, which is empty today, so every current
  systemId (nes with `.fds`, and unregistered systems like `gba`) falls back to `legacyRequirements`
  exactly as before;
- sega-cd (Japan) and saturn (USA) region-dependent legacy results are byte-for-byte identical to
  the anchor's pre-change values;
- constructing and using a synthetic `PlatformFirmwareProfile` never reads or mutates any
  `GameEntity` field beyond what `legacyRequirements` already reads (`systemId`, `region`) —
  confirmed by asserting the `GameEntity` fixture's `id`/`systemId` are unchanged after the call.

## TESTS

`RetroBiosValidatorRequirementsAnchorTest` (new, run twice — before and after, unchanged),
`RetroBiosValidatorFirmwareProfileWiringTest` (new). No other existing BIOS/launch test exists in
the repo to re-run (confirmed by repo-wide grep for the three relevant class names).

## LEGACY_FALLBACK

Confirmed preserved exactly: `requirements()` falls back to `legacyRequirements()` whenever
`PlatformFirmwareProfiles.forSystem(systemId)` returns `null` — which is the case for every systemId
today (nes, dsi, sega-cd, saturn, pc-engine-cd included), since the registry is empty. No systemId's
firmware requirement changed.

## IDENTITY_BOUNDARY

`PlatformFirmwareProfile`/`FirmwareRequirement` reference no `GameEntity`, `SourceIndexRelease`,
`SourceIndexCandidate`, or `StoredRom` type, and are not persisted anywhere. Firmware requirements
remain a pure launch-time computation, never written onto any Game/Release/Candidate/Local Copy
identity record — verified explicitly by a dedicated test
(`firmwareProfileNeverAppearsOnGameEntity`).

## FBNEO_BOUNDARY

`FbneoSupport.kt`, `FbneoBiosMerger.kt`, and every ROMgi-BIOS-acquisition call site
(`BackgroundRomDownloadEngine`, `CollectionDownloadManager`, `SessionActivity`'s
`systemId=="arcade-fbneo"` BIOS blocks) were not opened for modification and required no compile-time
reference change — confirmed by the diff touching only `RetroBiosValidator.kt` (plus the new
`PlatformFirmwareProfile.kt` file). No material scope expansion was encountered; no STOP-and-return
was triggered.

## BEHAVIOR_CHANGE

NONE for any current systemId. The only new capability is dormant: a *future* profile registered in
`PlatformFirmwareProfiles.all` would now be honored as authority, but none exists yet.

## ROLLBACK

`git checkout -- app/src/main/java/cl/gakuto/kizuna/launch/RetroBiosValidator.kt` restores the
pre-Slice-B file exactly. `rm app/src/main/java/cl/gakuto/kizuna/launch/PlatformFirmwareProfile.kt`
removes the new contract file. No Room/data migration is involved either way.

## UNKNOWN

Final shape of region-bucketing-as-data (a `regionBucketed`-style field or equivalent), deferred
until a platform actually needs it — not resolved here, consistent with the Shared Foundation Plan.
No Neo Geo fact was investigated or assumed in this changeset.

## RISK

R2 (per Forge's classification) — this changeset modifies `RetroBiosValidator.kt`, shared production
foundation used today by nes/dsi/sega-cd/saturn/pc-engine-cd's firmware-validation path. Mitigated
by: an anchor test run both before and after the change proving zero behavior difference for every
legacy systemId, plus a dedicated wiring test suite, all green (19/19 total).

# PLATFORM FACTORY V1 — PHASE 1 MEDIA CONTRACT PLAN

**Date:** 2026-09-12
**Branch:** `feature/gba-catalog-v1`
**Status:** Materialized from a completed Plan Mode analysis (read-only investigation, no
production code touched during planning). This document records the approved plan; Phase 1A
execution evidence is recorded separately in
`docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1a-media-profile-contract.md`.

## FORGE NARROWING (applies to everything below)

The plan below was designed covering both MEDIA and BIOS concerns. Forge approved it with one
narrowing: **Phase 1A implements only the MEDIA contract.** `BiosRequirementSpec`,
`biosRequirements`, `regionBucketed`, BIOS migration, and any FBNeo BIOS/merge change are
explicitly deferred to a separately reviewed later phase, because their final semantics are not
yet proven (see BIOS MODEL below — `RetroBiosValidator.requirements()` is parameterized by
`game.region`/file extension at runtime, not pure static data, and forcing it into a field now
risked a speculative design). This document is not rewritten into a different architecture by that
narrowing — it records the full analysis as designed, with the BIOS portion explicitly marked
deferred rather than removed from the record.

## SCOPE

Design the smallest production changeset that extracts the currently duplicated platform
media/archive-preparation/BIOS configuration into a reusable `PlatformMediaProfile`-style
contract. Design only — Phase 1A (separate document) is the first implementation slice.

## CURRENT HARDCODING

| File:line | What it does | Platforms |
|---|---|---|
| `RomArchivePreparer.kt:33` | `if (systemId=="arcade-fbneo") return downloaded` — skips all preparation | arcade-fbneo |
| `RomArchivePreparer.kt:128-130` `needsNativeExtraction()` | `systemId!="arcade-fbneo" && ext in {zip,7z} && nativeExtensions(systemId).isNotEmpty()` | all |
| `RomArchivePreparer.kt:132-158` `nativeExtensions()` | `when(systemId)` table, ~20 entries | nes/snes/gb/gbc/gba/n64/master-system/genesis/game-gear/ngp/ngpc/pc-engine/wonderswan(-color)/atari-lynx/2600/5200/7800/ps1/ps2/gamecube/wii/dreamcast/saturn |
| `RomArchivePreparer.kt:160-174` `extensionPriority()` | `when(systemId)` tie-break table; default = `nativeExtensions(systemId).firstOrNull()` | explicit overrides: snes→sfc, n64→z64, genesis→md, gbc→gbc, ngpc→ngc, wonderswan-color→wsc, ps2→iso, gamecube/wii→rvz |
| `RomArchivePreparer.kt:190-194` `extractionLimit()` | ps2/wii→16GiB, gamecube→3GiB, else→4GiB | ps2, wii, gamecube (rest generic) |
| `RomArchivePreparer.kt:196-199` `MULTI_FILE_OPTICAL_SYSTEMS`/`MULTI_FILE_DESCRIPTOR_EXTENSIONS` | CUE/GDI multi-file denylist | ps1, dreamcast, saturn, sega-cd, pc-engine-cd, pc-fx |
| `RetroBiosValidator.kt:27-67` `requirements()` | `when(systemId)` table + `regionBucket(game.region)` + extension gate (`.fds` for nes) | nes(conditional), dsi, sega-cd(region), saturn(region), pc-engine-cd |
| `RetroBiosValidator.kt:70-113` `validate()`/`regionBucket()`/`indexFiles()` | generic SAF-folder verification, already reusable | generic |
| `FbneoSupport.kt` (whole file) | BIOS sets are ROMgi-downloadable ZIPs (`GameDescriptor.bios(setName)`), not static local files | arcade-fbneo only |
| `FbneoBiosMerger.kt` (whole file) | ZIP-entry splice of BIOS into the game romset | arcade-fbneo only |
| `SessionActivity.kt:425-428,672-693,694-710,717-718,1866-1874,1892` | FBNeo BIOS download/merge/validation orchestration, 6+ sites | arcade-fbneo |
| `SessionActivity.kt:1117` | "fresh launch" barrier — SAF/FUSE timing workaround, unrelated to media/BIOS | ps1 only |
| `SessionActivity.kt:1875-1894` `validateRetroBios` | generic call into RetroBiosValidator + FBNeo-specific gate + free-text fallback | generic + arcade-fbneo |
| `BackgroundRomDownloadEngine.kt:182,216` | same FBNeo BIOS download/merge gate | arcade-fbneo |
| `CollectionDownloadManager.kt:134` | same FBNeo gate, duplicated a third time | arcade-fbneo |
| `RomPreparedFileValidator.kt:17-24` | already generic (reads `needsNativeExtraction`) | all |
| `RomPreparedFileValidator.kt:25-36` | FBNeo-specific romset validation | arcade-fbneo |

**Reusable existing characterization test:** `RomArchivePreparerOpticalTest.kt` already calls
`RomArchivePreparer.nativeExtensions(...)`, `needsNativeExtraction(...)` and
`requiresUnsupportedBundlePersistence(...)` directly for ps2/gamecube/wii/ps1/dreamcast/snes. As
long as those public signatures don't change, it keeps guarding the migration untouched.

**Critical design finding:** `extensionPriority()`'s fallback is
`nativeExtensions(systemId).firstOrNull()`. Of 8 explicit overrides, 7 coincide with their set's
natural first element (indistinguishable from "no override"). **Only `n64` genuinely diverges**
(`nativeExtensions("n64")` lists `"n64"` first, but the explicit override prefers `"z64"`). Without
a separate preference field, a naive migration would silently change N64's behavior.

## APPROVED PROPOSED CONTRACT

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

`BiosRequirementSpec`/`biosRequirements`/`regionBucketed` were part of the original design (see
BIOS MODEL) but are **not implemented in Phase 1A** per the Forge narrowing above.

## FIELD JUSTIFICATION

| NAME | TYPE | REQ/OPT | CURRENT SOURCE OF TRUTH | PLATFORMS | WHY HERE | WHY NOT EmulatorProfile | WHY NOT SourceIndexCatalogConfig |
|---|---|---|---|---|---|---|---|
| `systemId` | String | Required | implicit key already used everywhere | all | join key with the other two configs | identity of emulator choice, not media prep | identity plane, but legacy platforms (e.g. arcade-fbneo) have no SourceIndexCatalogConfig at all |
| `mediaKind` | enum | Required | `RomArchivePreparer.kt:33,196-199` | arcade-fbneo, ps1/dreamcast/saturn/sega-cd/pc-engine-cd/pc-fx, rest=SINGLE_FILE | governs whether/how the downloaded artifact is prepared | EmulatorProfile decides core/engine, not whether unzipping happens | identity plane; most MULTI_FILE_OPTICAL platforms aren't Source-Index-backed today |
| `nativeExtensions` | Set\<String\> | Optional (empty=passthrough, today's default) | `RomArchivePreparer.kt:132-158` | ~20 systems | the extraction/preparation contract itself | `EmulatorProfile.acceptedExtensions` already exists and is a different concept (what the core accepts at launch, post-preparation); dedup is explicitly Phase 2, not this phase | identity, not preparation |
| `preferredExtension` | String? | Optional (null=falls back to `nativeExtensions.firstOrNull()`) | `RomArchivePreparer.kt:160-174` | only `n64` truly diverges; rest representable via null | archive tie-break, specific to extraction | unrelated to core selection | unrelated to identity |
| `extractionSizeLimit` | Long? | Optional (null=4 GiB generic default) | `RomArchivePreparer.kt:190-194` | ps2/wii(16GiB), gamecube(3GiB) | safety ceiling tied directly to the extraction this profile governs | unrelated to emulator choice | unrelated to identity |

## BIOS MODEL (design recorded; migration deferred by Forge)

Two BIOS systems with genuinely different semantics — **not forced into one model**:

1. **`RetroBiosValidator`** — "verify named files exist in a user-chosen local SAF folder". Almost
   pure data (label + filename alternatives) plus an already-generic verification function.
2. **`FbneoSupport`/`FbneoBiosMerger`** — "BIOS sets are ROMgi-downloadable ZIPs, spliced into the
   game's own romset". This is acquisition+merge behavior tied to a per-**game** (not per-platform)
   `biosSets` Room column and the legacy ROMgi backend Forge decided not to expand. **Stays
   entirely outside `PlatformMediaProfile`** regardless of phase.

`RetroBiosValidator.requirements()` is not pure data today — it takes `game.region` and
`displayName` at runtime (region-bucketing for sega-cd/saturn; `.fds`-extension gate for nes).
Migrating this safely needs a `regionBucketed` flag on the data plus generic shared behavior for
the bucketing/gating itself — not speculative, but genuinely unresolved, hence deferred.

## MEDIA MODEL

Confirmed the contract needs exactly `mediaKind`, `nativeExtensions`, `preferredExtension`,
`extractionSizeLimit` — no broader media taxonomy than current proven behavior.

## MIGRATION SEQUENCE

**First slice: NES, SNES, Genesis, GBA + N64.** N64 is mandatory, not "for completeness" — it is
the one platform that would silently regress without `preferredExtension` as a separate field.

1. Platforms moved first: NES, SNES, Genesis, GBA (already evidenced reference/foundation
   platforms) + N64 (real override case).
2. `when(systemId)` branches removed: none yet in Phase 1A — `RomArchivePreparer` is not modified
   in this slice; the profile is characterized against current behavior first.
3. Branches intentionally remaining: everything else in `RomArchivePreparer` (unchanged); all of
   `RetroBiosValidator`/`FbneoSupport`/`FbneoBiosMerger`/`SessionActivity` FBNeo sites (deferred).
4. Equivalence: proven by a new characterization test calling the real, unmodified
   `RomArchivePreparer` public functions — not by hand-copied expected values.
5. Fallback/compat: N/A in Phase 1A (RomArchivePreparer unchanged); becomes relevant once a later
   slice makes `RomArchivePreparer` consume the profile.
6. Phase 0 protection: `PlatformCoreResolutionCharacterizationTest` already guards
   `EmulatorProfile`/registry consistency for these same platforms; a new, separate test is needed
   for `RomArchivePreparer` since Phase 0 doesn't cover that class.
7. New host tests required: parity test per migrated platform (nativeExtensions, mediaKind,
   extractionSizeLimit) + a dedicated N64 z64/n64 regression proof, plus re-running
   `RomArchivePreparerOpticalTest` unmodified as a cross-check.

## FILES

**FILES_TO_MODIFY (later slice, not Phase 1A):** `app/src/main/java/cl/gakuto/kizuna/launch/RomArchivePreparer.kt`

**FILES_TO_CREATE:**
- `app/src/main/java/cl/gakuto/kizuna/launch/PlatformMediaProfile.kt`
- `app/src/test/java/cl/gakuto/kizuna/launch/PlatformMediaProfileCharacterizationTest.kt`

**FILES_NOT_TO_TOUCH:** `RetroBiosValidator.kt`, `FbneoSupport.kt`, `FbneoBiosMerger.kt`,
`SessionActivity.kt`, `BackgroundRomDownloadEngine.kt`, `CollectionDownloadManager.kt`,
`RomPreparedFileValidator.kt`, `EmulatorProfiles.kt`, `SourceIndexCatalogConfig`
(`SnesSourceIndexCatalog.kt`), `RomArchivePreparerOpticalTest.kt`, any GBA/SAF repair file, the
downloader (`AuthorizedSourceDownloader.kt`, `RomDownloadTaskStore.kt`, `RomDownloadScheduler.kt`),
`PersistentRomLibrary.kt`.

## TEST PLAN

HOST CHARACTERIZATION: `RomArchivePreparerOpticalTest` + Phase 0's
`PlatformCoreResolutionCharacterizationTest` (both existing, unmodified) remain cross-guards.
NEW UNIT TESTS: `PlatformMediaProfileCharacterizationTest` — parity + N64 regression proof.
REGRESSION TESTS: re-run `RomArchivePreparerOpticalTest` in full.
DEVICE TESTS: none required — no runtime behavior change is expected in Phase 1A (the profile is
not yet consumed by production code).

## EVIDENCE / INFERENCE / UNKNOWN

EVIDENCE: everything in CURRENT HARDCODING is read directly from the cited files at the current
`feature/gba-catalog-v1` HEAD, cross-checked against the existing `RomArchivePreparerOpticalTest`.

INFERENCE: the N64 override is the only real divergence in the whole current dataset between
"ordered list" and "explicit preference", which is why `preferredExtension` is a required field,
not a speculative one. BIOS's real runtime parameterization (region + extension) justifies
deferring its migration rather than rushing a field.

UNKNOWN: final shape of `regionBucketed` and how to represent it without duplicating
`regionBucket()`'s logic — deferred to the later BIOS phase. Whether `EmulatorProfile.acceptedExtensions`
should stop duplicating `nativeExtensions` — deferred to a dedup phase, not decided here.

## RISK

R1 — design/characterization only in Phase 1A; the eventual `RomArchivePreparer` consumption slice
(not yet authorized) carries the refactor risk against frozen-adjacent NES/SNES/Genesis behavior,
mitigated by the parity tests described above.

## ROLLBACK

Delete `PlatformMediaProfile.kt` and its test — no production file is touched by Phase 1A, so
rollback is trivial and carries zero runtime risk.

## IMPLEMENTATION READINESS

Phase 1A (media contract, characterization only, no `RomArchivePreparer` change): READY, approved,
and executed — see `docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1a-media-profile-contract.md`.
The subsequent slice (`RomArchivePreparer` actually consuming the profile) and the BIOS phase both
remain separately gated, not authorized by this document.

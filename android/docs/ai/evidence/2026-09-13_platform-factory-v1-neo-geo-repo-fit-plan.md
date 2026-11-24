# PLATFORM FACTORY V1 — NEO GEO AES/MVS REPO FIT PLAN

**Date:** 2026-09-13
**Branch:** `feature/gba-catalog-v1`
**WORK_ID:** PLATFORM-FACTORY-V1.NEO-GEO-REPO-FIT-01
**Status:** Materialized from a completed Plan Mode analysis (read-only investigation via 3
parallel sub-agents plus direct verification, no production code touched during planning).

**EVIDENCE NOTE (historical, preserved):** at repo-fit analysis time, the qualification document
referenced as input for this plan,
`docs/ai/evidence/2026-09-13_platform-factory-v1-neo-geo-pilot-qualification.md`, was **not present
in the repository** (verified: neither tracked nor untracked). The analysis below was produced
using only the qualification facts Forge stated inline in its WORK_ID briefs (Geolith expects
`.neo`; physical comparison pair Geolith/FinalBurn Neo; MAME as reference/optional comparator; no
BEST EMULATOR winner yet), not the full qualification record.

**UPDATE:** the full Platform Lab qualification document was subsequently supplied by the Product
Owner and has now been materialized verbatim at
`docs/ai/evidence/2026-09-13_platform-factory-v1-neo-geo-pilot-qualification.md`. The repo-fit
analysis below remains exactly as originally produced — it is **repo-derived** evidence (systemId
tracing, Source Index schema, media/archive behavior, provisioning contracts) and was **not**
retroactively rewritten from the qualification document's content. Where the two documents overlap
(e.g. BIOS as a major gap, no BEST EMULATOR winner, `.neo` vs. zip/7z media distinction, avoiding
split/merged Candidate dependency architecture), they were reached independently — one from repo
inspection, the other from primary-source/emulator-upstream research — and happen to agree. Any
future reconciliation between the two (e.g. finalizing the AES/MVS applicability field shape, the
BIOS architecture option, or the generic launch-options contract) is a Forge decision, not
performed here.

PLAN STATUS: **READY** (as a repo-fit analysis; **NOT** ready for implementation — see
IMPLEMENTATION_READY).

## 1 — SYSTEM_ID / ROUTING REALITY

`systemId` conflates identity + routing + emulator selection + BIOS-config key simultaneously:
- Backend routing: `CatalogSystemRouting.backendFor()` — SOURCE_INDEX vs ROOM_CATALOG.
- UI grouping: `PlatformFamily` (`util/CatalogFilters.kt`).
- Room dedup/index key: `GameEntity` (`data/Entities.kt:26,30-32`).
- Source Index: each shard is scoped 1:1 to one `platformId` (`catalog/sourceindex/OzSourceIndexReader.kt:177,185,229`;
  `SnesSourceIndexCatalog.kt:61-63`).
- `EmulatorProfiles.forSystem()` and `PlatformMediaProfile.forSystem()`: **strict 1:1** — one
  `EmulatorProfile`/`PlatformMediaProfile` per systemId (`launch/EmulatorProfiles.kt:162`,
  `launch/PlatformMediaProfile.kt:62`).
- **Download/acquisition (`launch/DownloadSourcePlan.kt`, `launch/RomDownloadScheduler.kt`,
  `launch/AuthorizedSourceDownloader.kt`): ZERO references to `systemId`** — this layer is already
  fully agnostic, confirmed good news.
- `launch/PersistentRomLibrary.kt`: `systemId` only routes the SAF destination folder, no BIOS/launch
  decision there.
- `launch/RetroBiosValidator.requirements()`: **real precedent** — for `sega-cd`/`saturn`, BIOS
  selection already varies WITHIN one `systemId` using a second field (`game.region` via
  `regionBucket()`).

**Could one Neo Geo `systemId` cleanly launch both AES and MVS?** Not cleanly today, but the
architecture is not hostile to it — the real blocker is not identity but the launch mechanism (see
§7: MAJOR_GAP). Core (`retroArchCore`) and extensions are not AES/MVS-sensitive (both run on the
same FBNeo/NeoGeo libretro core); only BIOS selection and boot mode are.

**Could "identity family" and "launch target" be separated without touching global identity?**
Yes — a real precedent already exists: `PersistentRomLibrary.StoredRom(gameId, releaseId,
candidateId)` + `members: List<StoredRomMember>` (multi-disc M3U) separates canonical identity
from "which launch artifact(s) are used this time," without duplicating the Game tree. Limitation:
`StoredRom.systemId` remains a single value per release record — the precedent decouples identity
from release/candidate, not systemId from release.

**OPTIONS (none chosen):**
- A. One `systemId="neogeo"` (or similar) + an AES/MVS discriminator per Game/Release feeding a new
  `RetroBiosValidator` branch (same shape as segacd/saturn).
- B. Same as A but the discriminator lives in Source Index `classificationEvidence` (§2) rather than
  a new Room field.
- C. Leave AES/MVS undistinguished at the systemId level and resolve it entirely at launch time via
  the new mechanism from §7 (tighter coupling to that not-yet-designed piece).

## 2 — RELEASE APPLICABILITY

Exact verified field inventory:

`GameEntity` (`data/Entities.kt:38-59`): id, title, normalizedTitle, platform, systemId, year,
region, thumbnailRepo, source, launchValidated, coreId, virtualExtension, contentUri,
canonicalTitle, genre, romSet, biosSetsCsv, lastPlayedAt, addedAt, romgiAvailable, libraryOrigin.
**None AES/MVS-shaped.**

`SourceIndexReleaseFacets` (`catalog/sourceindex/OzSourceIndexReader.kt:41-50`): regions, languages,
publicationClass, modificationClass, translation, regionalExclusivity, evidenceStatus,
**`classificationEvidence: List<SourceIndexClassificationEvidence>`** (source/status/value, no fixed
vocabulary). **This field is already generic and enum-free** — the reader parses it without
vocabulary constraints (`OzSourceIndexReader.kt:221-227`).

**Can AES/MVS/BOTH/UNKNOWN be expressed with no schema change?** YES, via `classificationEvidence` —
adding a tuple `(source="hardware-applicability", status="documented"|"inferred"|"unknown",
value="aes"|"mvs"|"both")` is **pure DATA, zero Kotlin change**. `region` could technically also
carry the value as a free string, but would semantically collide with its current consumers
(TV-region matching in `CatalogFilters`/`CatalogPaging`/`RetroBiosValidator.regionBucket`) — not
recommended, though technically possible without a schema change. No field named `variant`,
`hardware`, or `compatibility` exists today (NOT FOUND). The smallest real schema change, if a
dedicated field were preferred over reusing `classificationEvidence`, would be a new field on
`SourceIndexReleaseFacets` (parallel to `publicationClass`) — not proposed, only identified as the
minimum gap if Forge prefers it later.

## 3 — SOURCE INDEX FIT

- **Shared Games**: no problem — one Neo Geo shard with one `SourceIndexGame` per title covers
  AES+MVS equally.
- **AES/MVS-specific Releases where evidence requires them**: via `classificationEvidence` (DATA,
  §2).
- **Exact Candidate**: `SourceIndexCandidate` is already content-format-agnostic — `exactFullPath`/
  `artifactFileName` work identically for a `.neo` or a `.zip`. No schema change.
- **Provenance**: already covered by the current model (`SourceIndexProvenance`), no change.
- **Whole-artifact digest**: `SourceIndexCandidate.artifactHashes: SourceHashEvidence` with
  `scope: SourceHashScope{ROM_CONTENT, DISTRIBUTED_ARTIFACT, UNKNOWN}` — **already supports a
  whole-artifact digest** via `scope=DISTRIBUTED_ARTIFACT`, no new field. `UNKNOWN` is already a
  valid scope value for anything unproven.
- **`transport=="torrent"` (`OzSourceIndexReader.kt:233`)**: a hardcoded string-literal check, no
  enum. As long as the pilot stays torrent-based (reasonable, since the whole Source Index is
  torrent-only today), this is transparent. If a non-torrent transport were ever needed, **that
  WOULD be a SCHEMA CHANGE** (the `require` and the torrent-only fields on `SourceIndexCandidate`
  are unconditional today).

**Conclusion: the full pilot shard (Games/Releases/Candidates/provenance/digest/UNKNOWN) is
expressible as pure DATA over the current schema, no schema change — as long as transport remains
torrent.**

## 4 — MEDIA / ARCHIVE REALITY

**`MediaKind` confirmed 100% declarative** — exhaustive grep for `.mediaKind`/`MediaKind.` outside
`PlatformMediaProfile.kt`: NOT FOUND. No production file reads it; the 5 existing profiles don't
even set it explicitly (all fall to the `SINGLE_FILE` default).

**A. Single-file `.neo` content**: survives the current pipeline **byte-for-byte, unmodified**.
`RomArchivePreparer.prepare()` returns the file untouched as soon as the extension isn't `.zip`
(line 34) — no zip-container assumption anywhere. Confirmed by reading the full file.

**B. Opaque `.zip`/`.7z` romset content (new systemId, no profile)**: `nativeExtensions("neogeo")`
resolves to `emptySet()` (no profile, no legacy entry) → `prepare()` returns the zip **intact, no
error, no extraction** (line 37). This is correct and sufficient for the FBNeo path (which already
accepts romset zips natively) but is exactly the problem for Geolith if the only available source is
a MAME-style zip — Oz would let it pass as an unexamined opaque blob without detecting the format
mismatch.

**Is `MediaKind.ARCHIVE_AS_ROMSET` already runtime-consumed?** NO — purely declarative. FBNeo's real
bypass is still the literal string `systemId=="arcade-fbneo"` in `RomArchivePreparer.kt:33`, not
`MediaKind`.

**Generic gap**: none needed if the exact Candidate is already a `.neo` (case A, already supported).
The gap only appears if zip→`.neo` conversion is needed (§5).

## 5 — GEOLITH PREPARATION GAP

- **Can an exact `.neo` Candidate be represented today?** YES, directly — the Candidate model is
  format-agnostic (§3), and `RomArchivePreparer` preserves non-zips intact (§4A).
- **Does any approved generic transform/conversion contract exist?** **NOT FOUND anywhere in the
  repo** — exhaustive grep for Converter/Transform/convert(/transcode/reencode: zero relevant hits
  (only in-memory state lambdas, Compose UI `PasswordVisualTransformation`, and an AES/GCM cipher
  mode string — nothing about ROM format conversion).
- **Where could a transformation live if source content requires conversion?** Nowhere existing —
  a new contract would need to be designed from scratch, explicitly out of scope for this plan (no
  converter is chosen or implemented here).
- **Provenance distinguishability**: since no conversion exists today, this is currently moot — if
  one were approved later, the model already has separate identifiers (origin `candidateId` vs. the
  Local Copy record) that could be extended with a "derived-from" marker, but designing that is out
  of scope here.

**Formal mark: Geolith acquisition/preparation = UNKNOWN / BLOCKED_FOR_IMPLEMENTATION** for the case
where the only available source is an opaque zip romset. **Not removed from physical qualification
for this reason** — if a lawful, reproducible `.neo` source exists or is obtained, the existing
path already works unmodified.

## 6 — BIOS/FIRMWARE CURRENT REALITY

| Component | Classification |
|---|---|
| `RetroBiosValidator.requirements()` | DATA (label+alternatives table by systemId+region) |
| `RetroBiosValidator.validate()`/`regionBucket()`/`indexFiles()` | VALIDATION (generic, reusable) |
| `FbneoSupport.knownBiosSets`/`expectedArchiveName()` | DATA + naming (tied to ROMgi/arcade-fbneo) |
| `FbneoSupport.copyAvailableBios()` | ACQUISITION (copy from local SAF folder) |
| `FbneoBiosMerger.mergeDownloadedBios/mergeConfiguredBios` | MERGE/PREPARATION (ZIP-entry splice) |
| `BackgroundRomDownloadEngine`/`CollectionDownloadManager` (`systemId=="arcade-fbneo"` gates) | ACQUISITION (BIOS download via ROMgi) |
| `SessionActivity` (6+ sites) | LAUNCH MAPPING + orchestration + UI (`profile.bios` text) |
| `preferences.fbneoBiosTreeUri`/`retroArchSystemTreeUri` | STORAGE (SAF tree URI config) |

**Reusable generically**: the `RetroBiosValidator` pattern (Requirement{label,alternatives} +
generic validation against a fixed SAF folder) — already works for NES/DSi/SegaCD/Saturn/PC-Engine-CD
without touching identity. **NOT reusable as-is**: the FBNeo pattern (ACQUISITION via ROMgi + ZIP
MERGE) is entangled with `GameEntity.biosSets` (per-GAME, not per-platform) and the legacy backend
Forge decided not to expand.

**BIOS OPTIONS (none chosen):**
1. **Extend `RetroBiosValidator`** with a new branch (Neo Geo systemId + AES/MVS discriminator)
   pointing at a small fixed BIOS file set in a user-chosen SAF folder — same shape as segacd/saturn.
   Simplest, most isolated, cleanly preserves Firmware≠identity.
2. **New, small, separate firmware data structure** (NOT inside `PlatformMediaProfile`, explicitly
   prohibited) — a step toward generalizing `RetroBiosValidator`'s `when`-table into data, larger
   scope than option 1.
3. **BIOS/mode as a per-Release/Game attribute** feeding a generic firmware selector — depends on
   the launch mechanism in §7 existing first (currently MAJOR_GAP); more ambitious, likely a later
   phase.

All three preserve Firmware ≠ Game/Release/Candidate/Local Copy identity. No BIOS filename or hash
was invented.

## 7 — EMULATOR PROFILE / CORE OPTIONS

| Concern | Classification | Evidence |
|---|---|---|
| AES/MVS mode selection at launch | **MAJOR_GAP** | Neither `RetroArchManager.buildDirectLaunchIntent` (7 fixed extras: ROM/LIBRETRO/CONFIGFILE/DATADIR/IME/APK/SDCARD/EXTERNAL) nor `ExternalEmulatorManager.buildIntent` (zero extras, ACTION_VIEW+content URI only) has a slot for a boot mode/option. `core_options`/`.opt`: NOT FOUND anywhere in the repo. |
| Region (existing) | **ALREADY_SUPPORTED, with a documented limit** | `GameEntity.region`/`resolvedRegion` exist end-to-end for their purpose (ROMgi ranking, segacd/saturn BIOS bucketing, file labeling) but **never reach the launch Intent** — not reusable as a second axis for AES/MVS without semantic collision. |
| Firmware/BIOS mapping | **MAJOR_GAP** (relative to reusing the existing mechanism as-is) | The region bucket is already committed to geographic semantics; AES/MVS needs a new axis, not a direct reuse. |
| Generic core options | **MAJOR_GAP** | Confirmed NOT FOUND; Oz's own code explicitly states it never writes RetroArch's config (`RetroArchManager.kt:126-131`). |

## 8 — PROVISIONING

- **RetroArch cores**: the contract (`RetroArchManager`+`TrustedCoreCatalog`) is generic by data — a
  real new core (e.g. a hypothetical Geolith RetroArch core) fits as a new `trusted_cores.json` +
  `EmulatorProfile` entry, **provided** it is distributed on `buildbot.libretro.com` following the
  `_libretro_android.so`/`coreName.zip` naming convention (hardcoded invariants in
  `TrustedCoreCatalog.validate()`). If not, a code change is needed, not just data.
- **External standalone apps**: the contract (`ExternalEmulatorManager`) is generic by data — a new
  app fits as an `ExternalEmulatorApp` entry **provided** its real contract is ACTION_VIEW+content-URI
  (one of the 3 existing `ExternalLaunchKind` shapes). If it needed RetroArch-style custom extras, it
  would not fit without a code change.
- Both are open questions depending on real Geolith/FBNeo distribution facts — that belongs to
  qualification evidence (the still-missing document), not this repo analysis.

## 9 — MINIMUM PILOT CHANGESET

**FOUNDATION REQUIRED BEFORE PILOT** (generic, not Neo-Geo-specific):
- A new generic mechanism to pass a launch mode/option into the Intent (closes the §7 MAJOR_GAP) —
  benefits every future platform that needs it, not just Neo Geo.
- Forge decision on applicability representation (recommended: `classificationEvidence`, pure DATA).
- Forge decision on which of the 3 BIOS options (§6) to adopt.

**PILOT DATA** (mostly config/data, already-established pattern):
- New `SourceIndexCatalogConfig` entry + shard (if Neo Geo enters via Source Index, per Forge's
  prior decision) or a ROOM_CATALOG entry.
- New `EmulatorProfile` entries for Geolith/FBNeo (pending real qualification facts).
- New `PlatformMediaProfile` entry (trivial if the Candidate is guaranteed `.neo`).
- New firmware entries per whichever BIOS option Forge picks.

**EMULATOR-SPECIFIC ADAPTER:**
- Geolith: needs the foundation mechanism (AES/MVS mode) + a lawful, reproducible `.neo` source
  (§5, currently UNKNOWN/BLOCKED).
- FBNeo: the zip-romset path already works with no changes (§4B is a non-issue specifically for
  FBNeo).

**HOST TESTS:**
- Parity test for the new `SourceIndexCatalogConfig`/shard (same pattern as `GbaSourceIndexCatalogTest`).
- Test proving `classificationEvidence` with AES/MVS/BOTH/UNKNOWN values doesn't break existing
  facet/region consumers.
- Test proving the new launch mechanism (once designed) correctly threads a mode value into Intent
  extras.
- Test proving (already demonstrable TODAY with no code change) that both a `.neo` file and a zip
  romset under a new systemId pass through `RomArchivePreparer` intact.

**DEVICE-ONLY WORK:** real-device validation of Geolith and FBNeo once adapters exist — explicitly
deferred, not part of this plan.

**DEFERRED WORK:** BIOS architecture decision, launch-mechanism design, BEST EMULATOR WINS
selection, the Geolith conversion problem (blocked until a lawful source exists), public
certification, Neo Geo CD.

## 10 — FACTORY EFFECTIVENESS

**NEW PLATFORM-SPECIFIC PRODUCTION FILES REQUIRED:** potentially ZERO if BIOS option 1 is chosen
(extends the existing `RetroBiosValidator.kt`) — everything else is new entries in already-existing
config files (`SnesSourceIndexCatalog.kt`-housed registry, `EmulatorProfiles.kt`,
`PlatformMediaProfile.kt`).

**SHARED FOUNDATION FILES REQUIRING CHANGE:** `RetroArchManager.kt`/`ExternalEmulatorManager.kt`
(only if the launch-mechanism gap is closed — a new generic addition benefiting every future
platform, not a Neo-Geo-only hack).

**DATA/CONFIG-ONLY ADDITIONS:** new Source Index config+shard, `EmulatorProfile` entries,
`PlatformMediaProfile` entry, `classificationEvidence` tuples (shard data, not code), firmware
entries (option 1).

**LEGACY HARDCODING REQUIRED:** none anticipated IF Neo Geo enters via Source Index (avoiding
ROOM_CATALOG/ROMgi entirely) AND the launch mechanism is built generically instead of copying the
FBNeo pattern (7+ `if (systemId=="...")` sites in `SessionActivity`).

**ESTIMATED ONBOARDING SURFACE:** substantially smaller than the pre-Factory 6-7-table/file pattern,
**conditioned on** the two foundation pieces (launch mechanism, firmware model) being solved
generically rather than repeating the FBNeo pattern. This is the central lesson of this analysis:
repeating the FBNeo pattern would defeat the Factory's entire purpose for this pilot.

## FILES LIKELY TO CHANGE (if Forge later authorizes implementation)

`RetroArchManager.kt`, `ExternalEmulatorManager.kt` (generic launch mechanism), `RetroBiosValidator.kt`
(if BIOS option 1), `SnesSourceIndexCatalog.kt` (new config+registry entry), `EmulatorProfiles.kt`,
`PlatformMediaProfile.kt`, a new shard asset, a new parity test.

## FILES NOT TO TOUCH

`PersistentRomLibrary.kt`, `AuthorizedSourceDownloader.kt`, `RomDownloadTaskStore.kt`,
`RomDownloadScheduler.kt` (already systemId-agnostic, confirmed), `FbneoSupport.kt`/
`FbneoBiosMerger.kt` (separate arcade-fbneo path, not to be touched), `OzSourceIndexReader.kt` (no
schema change needed), `GbaSourceIndexCatalogTest.kt` and other already-migrated platform tests.

## EVIDENCE

Everything cited with file:line comes from three parallel investigations against the current repo
HEAD (`RetroArchManager.kt`, `ExternalEmulatorManager.kt`, `TrustedCoreCatalog.kt`,
`EmulatorProfiles.kt`, `PlatformMediaProfile.kt`, `RomArchivePreparer.kt`,
`RomPreparedFileValidator.kt`, `AuthorizedSourceDownloader.kt`, `SessionActivity.kt`,
`RetroBiosValidator.kt`, `FbneoSupport.kt`, `FbneoBiosMerger.kt`, `data/Entities.kt`,
`catalog/sourceindex/OzSourceIndexReader.kt`, `catalog/CatalogSystemRouting.kt`,
`util/CatalogFilters.kt`), plus direct confirmation that the cited qualification document does not
exist in the repo.

## INFERENCE

The real gap deciding whether the Factory works for this pilot is not identity or Source Index
(both already fit as data) — it is the launch mechanism (§7) and the firmware model (§6), neither
touched by Phase 1A/1B (which closed MEDIA only). Building that foundation piece generically, rather
than repeating FBNeo's scattered pattern, is the real success condition for the Factory here.

## UNKNOWN

The referenced qualification document does not exist in the repo — real Geolith/FBNeo facts
(actual distribution, BIOS requirements, whether a lawful `.neo` source exists) are not verified
here, taken only as Forge-supplied context. Final shape of the AES/MVS/BOTH/UNKNOWN discriminator
(recommended `classificationEvidence`, not decided). Which BIOS option (1/2/3) to adopt. Exact
design of the launch mechanism. Whether a lawful, reproducible `.neo` source exists for Geolith.

## RISK

**R2** (classification already given by Forge) — the analysis itself is read-only, but any future
implementation would touch shared foundations (`RetroArchManager`/`ExternalEmulatorManager`) used
today by every platform.

## FORGE DECISIONS STILL REQUIRED (as of the original plan)

1. Reconcile the missing qualification document.
2. Choose applicability representation (recommended: `classificationEvidence`, no schema change).
3. Choose one of the 3 BIOS architecture options (§6).
4. Authorize design of the generic launch mechanism (§7) — without it, neither Geolith nor any
   future AES/MVS mode can work correctly.
5. Confirm whether a lawful, reproducible `.neo` source exists for Geolith, or accept that path as
   BLOCKED_FOR_IMPLEMENTATION.
6. Confirm whether Neo Geo enters via Source Index (recommended by prior Forge decision) or legacy
   ROOM_CATALOG.

## IMPLEMENTATION_READY

**NO** — the 6 Forge decisions above remain open; none can be resolved from repo evidence alone.

---

## FORGE REVIEW — 2026-09-13

**DECIDED:**

- Source Index remains canonical; ROOM_CATALOG is not reopened.
- One Neo Geo identity family remains binding.
- Release AES/MVS/BOTH/UNKNOWN applicability may use existing `classificationEvidence` for the
  pilot.
- Applicability metadata is not itself the runtime launch API.
- Generic launch-options architecture is required and authorized for design.
- Direct exact `.neo` Candidate is structurally representable.
- ZIP→`.neo` conversion remains BLOCKED/UNKNOWN.
- Explicit archive-as-romset behavior should be designed generically, not as a Neo Geo hack.
- No BEST EMULATOR winner.

**STILL OPEN:**

- Exact generic launch-options contract.
- Exact BIOS architecture option.
- Explicit `MediaKind` runtime wiring.
- Lawful/reproducible `.neo` acquisition path.
- Physical Geolith vs. FBNeo qualification.

**IMPLEMENTATION_READY:** NO

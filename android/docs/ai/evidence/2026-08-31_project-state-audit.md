# Project State Audit — Oz RetroRom

**Date:** 2026-08-31
**Branch / HEAD:** `feature/gba-catalog-v1` @ `a4d59b9`
**Type:** R1, read-only. No code changed, no files touched outside this report.
**Vocabulary:** `PASS` / `FAIL` / `NOT RUN` / `UNKNOWN` as used elsewhere in this repo. `EVIDENCE` = directly observed; `INFERENCE` = interpretation; `UNKNOWN` = not determinable from code alone.
**Method:** static read of `app/src/**`, `docs/AI_PROJECT_MASTER.md`, `app/build.gradle.kts`, `app/schemas/**`, `git log`. The project was **not compiled** for this audit; compiler-warning claims are `NOT RUN`.

---

## 1. Repository structure

Single Gradle module: `:app` (`settings.gradle.kts` — `include(":app")`). No `:core`, `:data`, `:feature-*` split.

| Source set | Files (`.kt`) | LOC | Notes |
|---|---|---|---|
| `app/src/main` | 120 | 25,203 | production |
| `app/src/test` | 38 | 3,417 | JVM unit (1 Robolectric) |
| `app/src/androidTest` | 5 | 1,135 | instrumented |

### Main packages (LOC = non-recursive per package dir)

| Package | Files | LOC | Role |
|---|---|---|---|
| `launch` | 43 | 9,649 | downloader, persistent library, download-task store, emulator bridge / `SessionActivity`, RetroArch mgmt, BIOS, session validation |
| `ui` (+ `ui/compose/**`) | 25 total | ~7,955 total | Compose app (`OzApp` 664, `OzViewModel` 738, `OzSurface` 1,026) + retained legacy View Activities/adapters |
| `catalog` (+ `sourceindex`, `minerva`, `background`) | 28 total | ~4,039 total | ROMgi legacy client (`RomgiCatalog` 1,007), Source Index reader/engine (933), MiNERVA provider (239), background catalog prep (~1,100) |
| `data` | 12 | 1,874 | Room DB/entities/DAOs, preferences, seed loader, legacy migrator, paging |
| `util` | 6 | 668 | text normalization, catalog filters |
| `auth` | 3 | 566 | Internet Archive login (S3 page parse + web policy) |
| `integration` | 2 | 398 | iiSU export, collection transfer |
| `cl/gakuto/kizuna` (root) | 1 | 54 | `OzApplication` |

### 20 largest main files

`SessionActivity.kt` 2228 · `OzSurface.kt` 1026 · `RomgiCatalog.kt` 1007 · `AuthorizedSourceDownloader.kt` 779 · `PersistentRomLibrary.kt` 743 · `OzViewModel.kt` 738 · `RomDownloadTaskStore.kt` 684 · `OzApp.kt` 664 · `AppPreferences.kt` 633 · `RetroArchManager.kt` 612 · `SettingsActivity.kt` 582 · `EmulationIntegrationSettingsActivity.kt` 560 · `GlobalCatalogActivity.kt` 431 · `LegacyMainActivity.kt` 403 · `OzHomeVisualParityLab.kt` 400 · `BackgroundRomDownloadEngine.kt` 389 · `SnesSourceIndexCatalog.kt` 349 · `SessionActivity`… (see above).

### Build tooling

AGP `8.10.1`, Kotlin `2.1.21`, KSP `2.1.21-2.0.1`, Gradle wrapper `8.11.1` (`build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`). `compileSdk`/`targetSdk` 36, `minSdk` 26, `versionName` `0.6.0-beta.4` (`app/build.gradle.kts:9-17`). Native: `arm64-v8a` only, for `libtorrent4j` (`app/build.gradle.kts:21-25`).

App entry: `.ui.MainActivity` (Compose, `LAUNCHER`, `app/src/main/AndroidManifest.xml:138-143`, `ui/MainActivity.kt` → `OzApp(model)`). `.launch.SessionActivity` is the only `exported=true` component with an intent-filter — the emulator/iiSU bridge (`AndroidManifest.xml:121-130`). `LegacyMainActivity` (403 LOC) is present but **not** the launcher.

---

## 2. Architecture invariants: declared (`AI_PROJECT_MASTER.md`) vs implemented

### 2.1 `Game ≠ Release ≠ Candidate ≠ Local Copy` (Master §5, §9)

**Status: IMPLEMENTED in behaviour, NOT in the relational schema — split across three planes.**

- **Room schema (v6, `exportSchema = true`) has 4 tables: `games`, `collections`, `collection_memberships`, `catalog_system_state`** (`app/schemas/cl.gakuto.kizuna.data.OzDatabase/6.json`; `data/OzDatabase.kt:10-13`). There is **no `releases`, `candidates`, or `local_copies` table.** `GameEntity` carries a single nullable `contentUri` + `libraryOrigin` (`data/Entities.kt:51,59`) — i.e. Room models "one Game → at most one active local pointer".
- **Release & Candidate are real, distinct types — but only in the read-only Source Index domain:** `SourceIndexGame`, `SourceIndexRelease`, `SourceIndexCandidate` (`catalog/sourceindex/OzSourceIndexReader.kt:26-102`), parsed from a committed asset shard, never persisted. `OzSourceIndexReader` is "a separate read-only source domain; it never opens or mutates Oz's Room database" (`OzSourceIndexReader.kt:110`).
- **Local Copy = `StoredRom`** (`launch/PersistentRomLibrary.kt:55-80`), persisted to **SharedPreferences** (`RECORD_PREFS = "oz_persistent_rom_library"`, `PersistentRomLibrary.kt:84`), keyed `rom.<gameId>` (legacy) or `rom.v3.<gameId>+<releaseId>` (`PersistentRomLibrary.kt:663-666`). Multiple local Releases per Game therefore live in a **key-value JSON blob store parallel to Room**, not in the DB the repo treats as "technical truth".

**INFERENCE:** the domain's central invariant is honoured by the code paths but is not expressible/queryable in the schema. Anything reasoning from `oz-retrorom.db` alone sees only Game + active pointer.

### 2.2 "A valid Local Copy does not operationally depend on its original Candidate" (Master §5)

**Status: IMPLEMENTED.** `StoredRom.candidateId` / `provenance` are nullable and retained purely as history (`PersistentRomLibrary.kt:66-68`). `resolve(game)` reconstructs playable state from the stored URI + reconciliation, with no Candidate lookup (`PersistentRomLibrary.kt:88-120`).

### 2.3 "Strong identity never inferred from naming prefixes / fuzzy titles when Source Index identity exists" (Master §5, §9 GLOBAL LIBRARY IDENTITY / IDENTITY TYPING)

**Status: IMPLEMENTED for Source Index platforms.**
- Identity is **typed from the persisted `source` field**, not guessed: `GameCatalogOrigin.fromPersisted()` (`data/Entities.kt:9-19`), `GameEntity.catalogOrigin` (`Entities.kt:61-62`).
- `LocalRomIdentityPolicy.hasStrongSourceIndexIdentity()` gates off legacy title scanning when `catalogOrigin == SOURCE_INDEX`; the prefix fallback is kept only "for diagnostics and collision regression tests" (`launch/LocalRomIdentityPolicy.kt:7-22`).
- `CartridgeSourceIndexCatalog.isProjection()` requires `catalogOrigin == SOURCE_INDEX` (`catalog/sourceindex/SnesSourceIndexCatalog.kt:168-169`).
- The fuzzy matcher (`catalog/RomgiTitleMatcher.kt`, Jaccard + Levenshtein, 248 LOC) still exists but is the **ROMgi / non-Source-Index path only**.

### 2.4 Source Index = offline/versioned control & identity plane; MiNERVA/ROMgi = acquisition plane (Master §6)

**Status: IMPLEMENTED, strong.**
- Shard loaded from a **committed asset** with a **SHA-256 gate**: `OzSourceIndexReader.openGzip(input, expectedCompressedSha256)` throws on mismatch (`OzSourceIndexReader.kt:170-174`); GBA config pins `6ff70a37…` (`SnesSourceIndexCatalog.kt:322`), asset `app/src/main/assets/source-index/gba.json.gz.bin` (committed).
- Parser enforces identity invariants: "Artifact hash cannot define release identity" (`OzSourceIndexReader.kt:194`), "Release identity status contradicts content evidence" (`:196-199`), "Cross-platform release in shard" (`:229`), "Duplicate game identity in shard" (`:187`).
- Uncertainty preserved: `ReleaseIdentityStatus.PROVISIONAL_METADATA_KEY`, `SourceHashScope.UNKNOWN`, `ArtifactContentRelation.UNKNOWN` (`OzSourceIndexReader.kt:14-16`).
- Acquisition plane: `catalog/minerva/MinervaDirectProvider.kt` (239 LOC), `catalog/RomgiCatalog.kt` ("Read-only client for ROMgi's public catalog format", 1,007 LOC).

### 2.5 Multi-local policy — 7 sub-rules (Master §9)

| Rule | Implemented at | Evidence |
|---|---|---|
| multiple local Releases per Game | `PersistentRomLibrary.localReleasesForGame()` `:122-129` | host + Robolectric |
| one explicit active pointer per Game | `GameEntity.contentUri`; `resolve()` `:88-102` | host + Robolectric |
| same Release + new Candidate replaces only that Release | `persist()` keyed by `v3RecordKey(gameId, releaseId)` `:212`; `PersistentRomLibraryBehaviorTest` | host + Robolectric |
| deleting active Release never auto-activates another | `deleteLocalRelease()` `:378-395` clears pointer, no re-selection | host + Robolectric + `PersistentRomLibraryRealSafDeviceTest#…DeletesOnlyOwned…` (**NOT RUN** on device) |
| durable Candidate provenance | `StoredRom.provenance` persisted `:68` | host |
| physical deletion only for `OZ_OWNED` | `isPhysicalDeletionAuthorized(ownership)` guards every `deleteUri` (`:142,154,277,383,394`); enum `LocalReleaseOwnership { OZ_OWNED, LEGACY_UNKNOWN }` `:33`; missing/invalid ownership defaults `LEGACY_UNKNOWN` | host + Robolectric |
| Room must never point to a PENDING artifact | `persist()` writes `state=PENDING` first, only sets Room `contentUri` + `state=COMPLETE` after `renameTo` + size/read verify (`:256-276`); catch deletes on failure (`:281-285`) | host + Robolectric + **1 real-device PASS** (`docs/ai/evidence/2026-08-31_gba-real-saf-device-run/`, commit `cb816e0`) |

### 2.6 BEST EMULATOR WINS / separate readiness states (Master §8)

**Status: IMPLEMENTED as per-platform flags, not a state machine.** `SourceIndexCatalogConfig.launchValidated` / `.acquisitionEnabled` (`SnesSourceIndexCatalog.kt:37-49`). `CatalogSystemRouting` header: "Catalog registry presence alone never enables a system here" (`catalog/CatalogSystemRouting.kt:6`). No single enum spans catalog-presence → configured → provisioned → launch-validated → certified; the states are separate booleans/flags in different files.

---

## 3. GBA Gate — operations and evidence level

`GbaSourceIndexCatalog`: `platformId="gba"`, `coreId="mgba_libretro_android.so"`, `virtualExtension="gba"`, `launchValidated=false`, `acquisitionEnabled=false`, shard sha `6ff70a37…` (`catalog/sourceindex/SnesSourceIndexCatalog.kt:316-326`). Wired in `SourceIndexCatalogs.forSystem("gba")` (`:333`) and `CatalogSystemRouting` (`gba` ∈ `SOURCE_INDEX_AVAILABLE_SYSTEM_IDS`, `CatalogSystemRouting.kt:28`).

| GBA operation | Implemented? | Evidence level | Verdict |
|---|---|---|---|
| Source Index shard bundled + parsed (2,368 games / 3,962 releases / 4,021 candidates) | Yes | host — `GbaSourceIndexCatalogTest.productionShardCountsAndSearchAreExact` loads the real shard and asserts exact counts + real-title search | **PASS (host)** |
| GBA catalog browse / search / projection to `GameEntity` | Yes (generic `CartridgeSourceIndexCatalog`) | host — `GbaSourceIndexCatalogTest` (3 tests) | **PASS (host)** |
| Facet preservation through projection (TRANSLATION, PROTOTYPE, regions) | Yes | host — `GbaSourceIndexCatalogTest.projectionKeepsTranslationAndPrototypeFacetsAndDisablesGuards` | **PASS (host)** |
| GBA identity typed from catalog origin, not prefix-guessed | Yes | host — `GbaSemanticContractTest.gbaUsesSourceIndexRoutingWithoutLaunchOrAcquisition` | **PASS (host)** |
| `launchValidated = false` enforced (projection + seed + registry + runtime repair) | Yes | host — `GbaSemanticContractTest` (4 tests); instrumented — `GbaLaunchValidationRepairTest` (2, in-memory Room); runtime one-shot repair `OzApplication.kt:38-41` (`clearLegacyGbaLaunchValidation()`) | **PASS (host + instrumented, in-memory)** |
| `acquisitionEnabled = false` → GBA download gated OFF (route `SOURCE_REQUIRED`) | Yes | host — `GbaSourceIndexCatalogTest` asserts `SourceIndexCatalogs.acquisitionForProjection(...) == null`; routing `SnesSourceIndexCatalog.kt:340-347` | **PASS (host)** |
| Persist / promote PENDING→COMPLETE local artifact on **real** `ExternalStorageProvider` (platform-agnostic `persist()`, exercised with `systemId="gba"`) | Yes | **real device** — Xiaomi 14T / Android 15 / API 35, 2026-08-31, `docs/ai/evidence/2026-08-31_gba-real-saf-device-run/` (commit `cb816e0`); JUnit `failures=0`, UTP `PASSED` | **PASS (real device, 1 run)** — note: this is generic library behaviour, not GBA-specific |
| GBA actual acquisition (torrent download of a GBA candidate) | Code path exists (`AuthorizedSourceDownloader`, `SnesSourceIndexCatalog.resolve()` builds a `DownloadSourcePlan`) but **gated off** for GBA | no GBA download test; `acquisitionEnabled=false` | **NOT RUN** |
| GBA ROM preparation pipeline (archive → verified `.gba`, exact-path, traversal reject) | Generic infra exists (`RomArchivePreparer` 205, `RomPreparedFileValidator` 70, `RomPreparedPayloadValidator` 66) | **no `GbaPreDevicePlayPipelineTest`** — Genesis has `GenesisPreDevicePlayPipelineTest` (3 tests), GBA has no equivalent | **NOT RUN (for GBA)** |
| GBA emulator launch — mGBA core, real device, ROM actually runs | Mechanism exists (`RetroArchManager.buildDirectLaunchIntent` `:149`, core `mgba_libretro_android.so`) | no GBA core-resolution contract test (NES/Genesis each have one); `launchValidated=false`; no device launch log anywhere in repo or `.artifacts/` | **NOT RUN** |
| GBA save-state / BIOS / peripheral / performance evidence | — | — | **NOT RUN** |
| GBA OZ-EQS / public certification | — | — | **NOT RUN / NO** (consistent with Master §240, Dashboard) |

**Summary:** GBA is **catalog- and identity-complete at host level (PASS)**, **acquisition deliberately disabled**, and has **one real-device data-point** (the persistence/promotion operation). Everything downstream of "browse the GBA catalog" — download, prepare, launch, certify — is **NOT RUN**. `launchValidated=false` in code is the honest state.

---

## 4. Outside GBA — real code vs skeleton

### 4.1 Real, substantial, exercised

| Area | Main code | Test coverage |
|---|---|---|
| Source Index engine | `OzSourceIndexReader` 292, generic `CartridgeSourceIndexCatalog` 349 | `OzSourceIndexReaderTest` (7), per-platform catalog tests SNES/NES/Genesis/GBA (10+5+5+3), `OzSourceIndexAndroidBenchmarkTest` (2, instrumented, perf) |
| 4 platforms wired | `SnesSourceIndexCatalog.kt:276-335` (snes/nes/genesis `launchValidated=true`; gba `false`) | SNES/NES/Genesis "validated" rests on **historical checkpoint evidence**, not re-runnable repo tests → **UNKNOWN from code alone** |
| BitTorrent downloader | `AuthorizedSourceDownloader` 779, real `org.libtorrent4j:2.1.0-32` + `libtorrent4j-android-arm64`, DHT on / uPnP off (`:729-731`) | `DownloadSourcePlanTest` (10), `HttpTransferRecoveryTest` (14), `RomDownloadTaskPersistenceTest` (6, instrumented), `RomDownloadSetCheckpointTest` (4) |
| Persistent library / multi-local | `PersistentRomLibrary` 743 | `PersistentRomLibraryBehaviorTest` (11, Robolectric + synthetic `DocumentsProvider`); 1 real-device operation |
| Download-task store + process-death recovery | `RomDownloadTaskStore` 684, `BackgroundRomDownloadEngine` 389 | schema round-trip tests across both process-death boundaries (`RomDownloadTaskPersistenceTest`) |
| ROMgi legacy catalog client | `RomgiCatalog` 1,007 (SQLite) | `RomgiTitleMatcherTest` (9), `RomgiEditionParserTest` (5), `RomgiRegionPolicyTest` (6), `RomgiSourcePolicyTest` (3), `RomgiOpticalFormatPolicyTest` (2), `Ps2DiscRegressionTest` (3) |
| MiNERVA direct provider | `MinervaDirectProvider` 239 | `MinervaDirectProviderTest` (8), `MinervaDirectLiveTest` (1, **live network**, instrumented) |
| Compose UI | `OzApp` 664, `OzViewModel` 738, `OzSurface` 1,026, theme 284 | `OzTileSizePolicyTest` (2), `AppPreferencesSurfacePlacementTest` (2); visual parity is process, not automated assert |
| Internet Archive auth | `InternetArchiveAuthManager` 346, `InternetArchiveS3PageParser` 186 | `InternetArchiveS3PageParserTest` (6), `InternetArchiveWebLoginPolicyTest` (3) |
| Background catalog preparation | `catalog/background/**` ~1,100 (JobService + foreground service + notification + task store) | `CatalogPreparationTimingTest` (5), `CatalogPreparationPolicyTest` (4), `CatalogPreparationResultStatusTest` (3), `CatalogPreparationStopPolicyTest` (2) |
| Emulator bridge / iiSU | `SessionActivity` 2,228, `SessionLaunchValidator` 134 (scheme allowlist, traversal guard, URI-grant checks), `IisuExporter` 332, `RetroArchManager` 612 | `GenesisCoreResolutionContractTest` (1), `NesCoreResolutionContractTest` (1) — **core-id resolution only**; no launch assertion |
| Optical / multi-disc | `RomArchivePreparer` optical path, `RomDownloadSet` 83 | `RomArchivePreparerOpticalTest` (4), `Ps2DiscRegressionTest` (3) |
| Collection batch download | `CollectionDownloadManager` 252, `CollectionBatchTaskStore` 192, `CollectionArtworkSyncManager` 181 | covered indirectly via download tests |
| SteamGridDB artwork | `SteamGridDbClient` 87, `SteamGridDbIconSyncManager` 199 | — |
| FBNeo / arcade BIOS infra | `FbneoBiosMerger` 195, `FbneoSupport` 52, `RetroBiosValidator` 127 | `RomgiCatalog` arcade set-name enforcement tested indirectly |

### 4.2 Skeleton / declared-but-not-built

| Item | Evidence |
|---|---|
| **Hero Media / video wallpaper / navigation soundtrack** | `androidx.media3:media3-exoplayer`, `media3-ui`, `coil-gif` declared (`app/build.gradle.kts`, comment "Oz NEXT: Hero Media, video wallpaper and navigation soundtrack") — **zero references to media3 / ExoPlayer in `app/src/main`** (grep: NONE). Dependencies staged, feature not started. |
| **Windows Home Hub / Personal Cloud / TV** | Master §7, §12 — **doc-only**. No module, no code, no `network`/`pairing`/`discovery` package. |
| **Platform Factory V1** | The *runtime* catalog is already config-driven (`CartridgeSourceIndexCatalog` + one `SourceIndexCatalogConfig` per platform). But the *pipeline* the Dashboard describes (`Discover → Classify → Identity → Benchmark → Emulator Qualify → Build Shard → Integrate Route → First Play → Device Evidence → Freeze`) has **no in-repo tooling** — shards arrive as pre-built committed `.bin` assets. Listed as backlog in `PROJECT_DASHBOARD.md`. |
| **Neo Geo / CPS1 / CPS2 / Arcade** (next platform after GBA) | FBNeo BIOS-merge + BIOS-validation infra exists (`FbneoBiosMerger`, `RetroBiosValidator`), but **no arcade Source Index shard, no arcade `SourceIndexCatalogConfig`, not in `SOURCE_INDEX_AVAILABLE_SYSTEM_IDS`**. Partial infra only. |
| **`OzHomeVisualParityLab`** (400 LOC) | A visual-parity *preview* surface (`ui/compose/visualparity/`), not production navigation. |
| **`SessionCleanupWorker`** | 16 LOC — real but minimal (periodic 12h WorkManager job, `OzApplication.kt:44-47`). |

---

## 5. Tests

**Totals:** 177 unit `@Test` across 38 files; **16 instrumented `@Test`** across 5 files. Host:instrumented ≈ **11 : 1**.

### 5.1 Honesty / quality signals

- **No mocking framework anywhere** — no `mockk`, `mockito` in `app/build.gradle.kts`. Tests use real objects, real committed asset shards (`GbaSourceIndexCatalogTest` opens `gba.json.gz.bin` from disk), and a hand-written synthetic `DocumentsProvider` (`PersistentRomLibraryBehaviorTest`, which models real-provider quirks: `renameChangesDocumentId`, `failReadsAfterRename`).
- **No `@Ignore` / `@Disabled`** anywhere in `app/src`.
- Only **1 Robolectric** file (`launch/PersistentRomLibraryBehaviorTest.kt`, 11 tests).
- The smallest test files still assert real behaviour, not compilation: `NesCoreResolutionContractTest` / `GenesisCoreResolutionContractTest` (1 each) assert core-id mapping; `GenreDatParserTest` (1) parses a real DAT.

### 5.2 What is genuinely covered (host)

Parsers (`LibretroDatParser`, `GenreDatParser`, `RomgiEditionParser`, `InternetArchiveS3PageParser`), routing/policy (`CatalogSystemRouting`, `RomgiRegionPolicy`, `RomgiSourcePolicy`, `CollectionVisibilityPolicy`, `CatalogPreparationPolicy`/timing/stop), identity (`LocalRomIdentityPolicyTest` 10, `RomgiTitleMatcherTest` 9), Source Index readers per platform (exact counts against real shards), download plan / recovery / checkpoint / task-store schema round-trips, `SessionProgressFormatter`, `RomPreparedPayloadValidator`, `CatalogFilters`.

### 5.3 Instrumented breakdown (what actually needs a device)

| File | `@Test` | Really needs a device? | Device evidence exists? |
|---|---|---|---|
| `PersistentRomLibraryRealSafDeviceTest` | 5 | **Yes** — real `ExternalStorageProvider`, drives DocumentsUI | **1 of 5** (`externalStorageProviderCreatesAndPromotesPendingArtifactWithoutRoomPendingReference`, 2026-08-31). Other 4 (delete-owned semantics, PENDING recovery/cleanup, process-death phase 1 & 2): **NOT RUN** |
| `RomDownloadTaskPersistenceTest` | 6 | No — in-memory / JSON round-trip; placed in `androidTest` (likely for Android `org.json`) | n/a |
| `GbaLaunchValidationRepairTest` | 2 | Partially — `Room.inMemoryDatabaseBuilder`, no SAF, no launch | runs green in CI-style; no captured artifact in repo |
| `OzSourceIndexAndroidBenchmarkTest` | 2 | Yes — on-device memory/PSS benchmark | **SNES only** (`assertEquals("snes", reader.platform)`); perf, not correctness; result notes `productionCandidateLookup: "NOT_IMPLEMENTED"` |
| `MinervaDirectLiveTest` | 1 | Yes — **live network** | n/a (network-dependent) |

### 5.4 Gap

**No test — host or device — asserts that a ROM actually launches / plays in an emulator, for any platform.** The "SNES / NES / Genesis physical route validated/frozen" status (Master §219-221) rests on historical checkpoint evidence outside the automated suite. From code alone: **UNKNOWN** whether any platform has a reproducible green launch.

---

## 6. Visible technical debt

| Item | Evidence | Severity |
|---|---|---|
| `TODO` / `FIXME` / `HACK` / `XXX` / `STOPSHIP` in `app/src/main` | **0** (grep). No `@Deprecated` in main. Marker-clean — either genuinely low debt or debt is simply uncommented. | — |
| **Dead dependencies** | `media3-exoplayer`, `media3-ui`, `coil-gif` declared, **unused in main**. | low |
| **`SessionActivity.kt` = 2,228 LOC** single Activity | largest file by 2×; also `OzSurface.kt` 1,026, `RomgiCatalog.kt` 1,007. | medium (maintainability) |
| **Two persistence mechanisms for library state** | Room (`games.contentUri`) + SharedPreferences JSON (`PersistentRomLibrary` `RECORD_PREFS`). The `Game≠Release≠Candidate≠LocalCopy` invariant is not in the schema the repo calls "technical truth". | medium (architectural) |
| **Mojibake in Spanish string literals** | `"La ROM persistida no coincide con el tamaÃ±o esperado."` (`PersistentRomLibrary.kt:267`), `"El release local ya no estÃ¡ disponible."` (`:176`), `:253` — UTF-8 saved/read as Latin-1. User-visible. | low (cosmetic) |
| **Toolchain lag** | AGP 8.10.1 / Kotlin 2.1.21 / Gradle 8.11.1 / KSP 2.1.21-2.0.1 ≈ mid-2025 releases; newer stable exists by 2026-08. **INFERENCE** — not checked against a live index (offline audit). | low |
| **Compiler warnings** | **NOT RUN** — this audit did not compile the project. | UNKNOWN |
| **Large uncommitted evidence** | `.artifacts/` holds ~15 MB of device logs; `.gitignore` covers `.artifacts/` on this branch (`app/.gitignore` line 7-8 equiv). Not in-tree. | — |
| **`AndroidManifest` androidTest** | `SafGrantActivity` test-bridge uses deprecated `onActivityResult` (intentional, `@Deprecated` annotated in `PersistentRomLibraryRealSafDeviceTest.kt:465`). | — |

---

## 7. Prioritised findings

### 7.1 What GBA Gate still needs to be *genuinely* complete (not "compiles")

| # | Item | Kind | Priority |
|---|---|---|---|
| 1 | **Real-device GBA emulator launch evidence** — prepare a GBA ROM and confirm it actually runs via RetroArch + `mgba_libretro_android.so` on the Xiaomi 14T. This is the check that would justify flipping `launchValidated`. | device | **P1 — gating** |
| 2 | `GbaPreDevicePlayPipelineTest` — host test mirroring `GenesisPreDevicePlayPipelineTest` (archive → verified `.gba`, SHA, traversal reject, exact source-plan durability). | host | **P1** |
| 3 | `GbaCoreResolutionContractTest` — mirror `Nes`/`Genesis` equivalents for the mGBA core id. | host | **P1** |
| 4 | Run the **remaining 4 `PersistentRomLibraryRealSafDeviceTest` methods** on device (delete-owned semantics, PENDING recovery/cleanup, process-death phase 1 & 2) and version the evidence, as done for method 1. | device | **P2** |
| 5 | **GBA acquisition decision**: either enable `acquisitionEnabled` for GBA and prove the torrent download + prepare path with its own evidence, or formally record acquisition as deliberately deferred for this Gate. | decision + test | **P2** |
| 6 | Two-phase **process-death proof** — still blocked by DocumentsUI grant transfer (Dashboard / Master). | device | **P2 — known blocker** |
| 7 | GBA **BIOS / save-state / performance** evidence per Master §8, §201-202. | device | **P3** |
| 8 | Architectural: decide whether the multi-local model (Release/Candidate/LocalCopy) should move into Room before the Gate is "frozen", so the schema — not a SharedPreferences blob — carries the invariant. | design | **P3** |
| 9 | Fix the mojibake user-facing strings in `PersistentRomLibrary.kt` (and audit other files for the same). | trivial | **P3** |

### 7.2 Outside GBA — real traction vs skeleton

**Has real traction (code + tests, works at least at host/Robolectric level):**
Source Index engine (4 platforms wired), BitTorrent downloader (real libtorrent4j), persistent library + multi-local + process-death recovery, download-task store, ROMgi legacy catalog client, MiNERVA provider, Compose UI (active launcher), Internet Archive auth, background catalog preparation, emulator/iiSU bridge with security validation, optical/multi-disc preparation, collection batch download, SteamGridDB artwork.

**Skeleton / declared-only (no production code):**
- Hero Media / video wallpaper / navigation soundtrack — media3 + coil-gif deps staged, **zero code**.
- Windows Home Hub / Personal Cloud / TV — doc vision only.
- Platform Factory *pipeline* tooling — only the runtime catalog is factory-shaped; shard building is out-of-repo.
- Neo Geo / CPS / Arcade — FBNeo BIOS infra partial; no shard, no catalog config, not routed.
- `OzHomeVisualParityLab` — preview surface, not production.

**Cannot be determined from code alone (UNKNOWN):**
- Whether SNES / NES / Genesis actually have a reproducible green emulator-launch (their "validated/frozen" status is historical checkpoint evidence, not an automated test).
- Whether the current toolchain versions are meaningfully behind latest stable (offline audit).
- Compiler-warning state (project not built for this audit).

---

## 8. One-line bottom line

The **identity/catalog plane is real and well-tested** (Source Index reader, typed identity, 4 platforms, strong invariants in the parser). The **acquisition and persistence plane is real** (libtorrent downloader, process-death-safe task store, SAF library with 1 real-device datapoint). The **launch/play plane is unproven from the repo** for every platform, GBA included. The **product-vision features** (Hero Media, Windows Hub, Personal Cloud, Platform Factory pipeline, Arcade) are **documentation, not code**. GBA specifically is browse-and-identify complete, acquisition-off by design, and NOT RUN from download onward.

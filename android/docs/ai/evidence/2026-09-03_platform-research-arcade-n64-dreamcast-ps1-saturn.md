# Platform research — Neo Geo/CPS/Arcade · N64 · Dreamcast · PS1 · Saturn

**Date:** 2026-09-03 · **Type:** R0/R1, read-only research. No code changed, no device touched.
**Requested via:** oz-researcher (subagent unavailable this session — done inline by Claude Code with the same EVIDENCE / INFERENCE / UNKNOWN discipline).
**Scope:** the 5 remaining platforms in the approved expansion order (`AI_PROJECT_MASTER.md:213`): `… GBA → Neo Geo/CPS1/CPS2/Arcade → N64 → Dreamcast → PS1 → Saturn`.
**Repo baseline:** `feature/gba-catalog-v1` @ `618f4d8`.

---

## 0. Shared findings (apply to all 5)

### 0.1 The Source Index shard schema is single-file / cartridge-only

**EVIDENCE.**
- Shards present: `app/src/main/assets/source-index/{nes,snes,genesis,gba}.json.gz.bin` only. No shard for any platform in this report.
- Schema v1, parsed by `app/src/main/java/cl/gakuto/kizuna/catalog/sourceindex/OzSourceIndexReader.kt`:
  - `games[]` — `{gameId, platform, canonicalTitle, canonicalTitleKey, aliases[]}`
  - `releases[]` — `{releaseId, gameId, platform, region, revision, identityStatus (VERIFIED_CONTENT | PROVISIONAL_METADATA_KEY), canonicalReleaseKey, contentIdentity{hashScope (ROM_CONTENT | DISTRIBUTED_ARTIFACT | UNKNOWN), crc32, md5, sha1, sha256}, releaseType, headerState, facets{regions[], languages[], publicationClass, modificationClass, translation, regionalExclusivity, evidenceStatus, classificationEvidence[]}}`
  - `candidates[]` — `{candidateId, releaseId, provider, collection, providerItemId, transport, artifact{fileName, size, hashScope+hashes, contentRelation (WRAPPED | CONTENT_IDENTICAL | UNKNOWN)}, torrent{infoHash, fileIndex, exactFullPath, trackerUrls[], artifactStrategy, artifactPath}, providerClassifications[], firstSeen, lastSeen, sourceFreshness}`
- **Hard constraints in the reader:** `require(transport == "torrent")` (`OzSourceIndexReader.kt:233`); `require(contentIdentity.scope != DISTRIBUTED_ARTIFACT)` ("Artifact hash cannot define release identity", `:194`); one candidate → **one `torrent.fileIndex` → one file inside one torrent**.
- `SnesSourceIndexCatalog.kt:150-152` — `resolve()` builds a `DownloadSourcePlan` with `candidates = listOf(downloadCandidate)` (exactly one).
- **There is no field for BIOS, no field for multi-disc / M3U, no field for "romset + separate BIOS zip".**
- `CartridgeSourceIndexCatalog` is documented as a "read-only **cartridge-system** discovery catalog" (`SnesSourceIndexCatalog.kt:53`).

### 0.2 Multi-disc infrastructure exists — but wired to ROMgi, not Source Index

**EVIDENCE.**
- `RomDownloadSet` / `RomDownloadSetItem` (`launch/RomDownloadSet.kt`), `PersistentRomLibrary.persistSet()` ("Persists every disc beside a relative M3U playlist", `PersistentRomLibrary.kt:288-289`), `RomDownloadSetCheckpointTest`, `M3uPlaylist` — a full multi-disc download/persist/M3U path.
- Its entry point is `RomgiCatalog` (`RomgiDownloadPlan.toDownloadSet(...)`, `CollectionDownloadManager.kt:208`). Multi-disc platform allow-list is in **ROMgi**: `RomgiCatalog.kt:998` — `M3U_PLATFORMS = setOf("ps1","ps2","dc","sat","scd","tgcd","gc","wii","psp","pcfx")`.
- `RomgiCatalog.kt:376-387` — explicit rule: "Never 'rescue' an incomplete multi-disc edition by silently reducing it to Disc 1: the exact disc set is part of the release."
- **The Source Index `resolve()` path has no multi-disc branch.** A Source Index disc-system shard would need either a schema extension (candidate = a disc *set*) or a new resolve path that assembles multiple single-file candidates into a `RomDownloadSet`.
- **INFERENCE:** systemId naming is inconsistent between the two catalogs — ROMgi uses `dc`/`sat`/`scd`; `EmulatorProfiles` uses `dreamcast`/`saturn`/`sega-cd`. A new Source Index platform would have to pick one and align routing.

### 0.3 BIOS is a launch-time concern, absent from the acquisition plane

**EVIDENCE.**
- `RetroBiosValidator` (`launch/RetroBiosValidator.kt`) maps BIOS to concrete libretro filenames **per systemId at launch/prep time** — currently only `nes` + `.fds` → `disksys.rom`; comment: "Optional/conditional firmware is deliberately not guessed here."
- `FbneoBiosMerger` (`launch/FbneoBiosMerger.kt`, 195 LOC) + `FbneoSupport` — arcade-specific: merges BIOS into the game romset zip.
- `EmulatorProfiles.<profile>.bios` is a free-text human note, not machine-actionable (e.g. `"BIOS PS1 recomendada"`, `"BIOS Saturn recomendada/requerida según motor"`).
- The Source Index schema and `builder.py` have **no BIOS concept**. Providing BIOS for a disc/arcade platform is not covered by the current acquisition design.

### 0.4 Shard build tooling (`tools/source_index/`, untracked)

**EVIDENCE.**
- `tools/source_index/builder.py` (338 LOC) — "Deterministic Oz Source Index v1 builder and health gate". Generic: `--platform <id>` (default `snes`), one provider adapter `MinervaAdapter(input_path)`.
- Per-source input fields required (`builder.py:139`): `canonicalTitleKey, canonicalReleaseKey, canonicalFileName, region, expectedSize, hashScope, artifactContentRelation, collection, infoHash, fileIndex, exactFullPath`.
- `health_gate()` rejects if a `critical_platforms` shard has 0 releases or a coverage metric "collapsed".
- Census/qualification helpers: `census/`, `qualification/platform_qualification.py`, `gba_census.py`, `genesis_census.py`, `snes_benchmark.py`.
- **Adding a platform = feeding a MiNERVA source list for it + `builder.py --platform <id>`** — *provided* it is a single-file platform. The builder has no multi-file/BIOS path.
- **UNKNOWN:** whether the `MinervaAdapter` input for these 5 platforms exists or can be generated — the MiNERVA census inputs are not in the repo (only SNES/GBA/Genesis fixtures under `tools/source_index/fixtures/`).

### 0.5 Source-provider landscape (all platforms)

**EVIDENCE (external, Sept 2026):**
- Every existing shard candidate is `provider = minerva`, `collection` = `"No-Intro/<platform>"` (or `"…(Aftermarket)"`), path `Minerva_Myrient/No-Intro/<set>/<file>.zip`, `transport = "torrent"`.
- **Myrient shut down 2026-03-31** ("unsustainable operating costs"); community reports all 385 TB backed up and "generating torrents". The MiNERVA provider's upstream cataloguing appears Myrient-derived — the HTTP mirror is gone; **torrent-based acquisition survives only while infohashes stay seeded, and specific infohashes may change** after the backup re-seeds. This is a live risk for the *existing* 4 platforms too, not just new ones. [INFERENCE on the MiNERVA↔Myrient link; EVIDENCE on the shutdown.]
- **No-Intro** = cartridge dumps (N64 uses this). **Redump** = optical-disc dumps (PS1, Saturn, Dreamcast). **MAME / FBNeo DAT** = arcade romsets. Internet Archive hosts verified No-Intro and Redump sets per platform.
- BIOS firmware is copyrighted and **not** part of No-Intro/Redump game sets; preservation-correct sourcing is a personal dump. Oz has no BIOS-acquisition mechanism (§0.3).

Sources: [RetroArch cores 2026](https://retrohandheldhq.com/posts/retroarch-cores/) · [Beetle Saturn firmware (libretro docs)](https://github.com/libretro/docs/blob/master/docs/library/beetle_saturn.md) · [RetroBIOS / beetle_saturn](https://abdess.github.io/retrobios/emulators/beetle_saturn/) · [Recommended BIOS files](https://retrohandhelds.gg/recommended-bios-files-for-emulation/) · [Myrient closure / backup](https://www.notebookcheck.net/All-385-TB-of-Myrient-game-archive-has-been-backed-up.1250975.0.html) · [Myrient (Wikipedia)](https://en.wikipedia.org/wiki/Myrient) · [ROM & ISO sites (Emulation Wiki)](https://emulation.gametechwiki.com/index.php/ROM_%26_ISO_Sites)

---

## 1. Nintendo 64 — the only "drop-in" candidate

| Q | Finding | Label |
|---|---|---|
| 1. Shard exists / structure | No shard. **Fits the existing schema unchanged** — N64 is single-file (one `.z64`/`.n64`/`.v64` per game), No-Intro set, exactly like the 4 done. Config would be a new `CartridgeSourceIndexCatalog(SourceIndexCatalogConfig(platformId="n64", platformName="Nintendo 64", coreId="mupen64plus_next_gles3_libretro_android.so", virtualExtension="z64", launchValidated=false, shardAsset="source-index/n64.json.gz.bin", shardSha256=…, projectionPrefix="oz-source-index-n64-game-", acquisitionEnabled=false))` + add `"n64"` to `CatalogSystemRouting.SOURCE_INDEX_AVAILABLE_SYSTEM_IDS` + `SourceIndexCatalogs.forSystem`. | EVIDENCE (schema fit), INFERENCE (config shape) |
| 2. BIOS | **None.** `EmulatorProfiles` n64 → `"No requerida"`; Mupen64Plus-Next needs no BIOS. | EVIDENCE |
| 3. Core / mapped | `mupen64plus_next_gles3_libretro_android.so` — **already mapped** in `EmulatorProfiles.kt:112` (`n64`, ext `n64/z64/v64/zip/7z`, engine RETROARCH). `RetroArchManager` resolves the core the same way it did for mGBA (via `EmulatorProfiles`/`coreId` + `queryInstalledCores`). Not yet in `RetroArchManager` config beyond that — same as GBA before this week. **UNKNOWN:** whether the Mupen64Plus-Next core is installed on the test device (RetroArch `config/Mupen64Plus-Next/` was seen in an earlier device listing → INFERENCE: likely present). | EVIDENCE (mapped), UNKNOWN (device) |
| 4. Acquisition complexity | **Single file.** Same as NES/SNES/Genesis/GBA. No multi-disc, no per-game BIOS, no romset. Note: `.z64` files are large (8–64 MB) — the biggest is Resident Evil 2 / Conker at 64 MB; average ~12–20 MB. Selective-file torrent download (as used for the 4 done) scales fine. | EVIDENCE |
| 5. Providers | **No-Intro "Nintendo - Nintendo 64 (BigEndian)"** set — the same MiNERVA/No-Intro pipeline as the 4 done. Torrent + Internet Archive mirrors exist. Byte-swapped variants (`.v64`/`.n64`) exist in No-Intro but `.z64` (big-endian) is canonical and mGBA-style cores prefer it. | EVIDENCE (set exists), INFERENCE (MiNERVA has an N64 census input — not verified in repo) |

**Assessment:** N64 is the **only one of the 5 that fits the current architecture with no schema/BIOS/multi-disc change** — a shard build + a config entry + a routing line, mirroring the GBA integration exactly. `launchValidated` would start `false`; acquisition can start gated (`acquisitionEnabled=false`) like GBA, or enabled like NES/SNES/Genesis.

---

## 2. Neo Geo / CPS1 / CPS2 / Arcade — structurally different

| Q | Finding | Label |
|---|---|---|
| 1. Shard exists / structure | No Source Index shard. **`arcade-fbneo` is already a live systemId** but via **ROMgi**, not Source Index — it was in the seed catalog (47 games) and has dedicated handling (`FbneoBiosMerger`, `FbneoSupport`, `RomgiCatalog` arcade set-name enforcement, `RomLibraryResolver` `"arcade-fbneo" -> zip/7z`). A Source Index arcade shard would **not** fit the cartridge schema: arcade "identity" is a **romset name** (`mvsc.zip`, `sfa3.zip`), the acquisition unit is a zip whose *internal* contents must exactly match the FBNeo core's DAT version, and BIOS (`neogeo.zip`, CPS `qsound`/decryption) lives in **separate zips in the same set**. | EVIDENCE |
| 2. BIOS | **Yes, romset-coupled.** `EmulatorProfiles` arcade-fbneo → `"Depende del romset; BIOS deben pertenecer al mismo set"`. Neo Geo needs `neogeo.zip`; some CPS2 games need parent/BIOS zips. `FbneoBiosMerger.kt` (195 LOC) already merges BIOS into the game archive at prep time — **but it is fed by ROMgi's plan, not a Source Index candidate.** | EVIDENCE |
| 3. Core / mapped | `fbneo_libretro_android.so` — **already mapped** (`EmulatorProfiles.kt`, `arcade-fbneo`, ext `zip/7z`, RETROARCH). RetroArch device listing showed `config/FinalBurn Neo/` and an `FBNeo - Arcade Games.lpl` playlist → **INFERENCE: FBNeo core already installed on the test device.** `mame2003_plus` / `mame` are alternative cores, not mapped. | EVIDENCE (mapped), INFERENCE (device) |
| 4. Acquisition complexity | **Highest of the 5.** Not multi-disc, but: (a) DAT-version coupling — the downloaded set must match the exact FBNeo core build; (b) parent/clone/BIOS zip dependencies within a "full non-merged" or "split" set; (c) `contentIdentity` cannot be a single ROM_CONTENT hash (a romset zip contains many files) — it would be `DISTRIBUTED_ARTIFACT`, which the reader **rejects as release identity** (`OzSourceIndexReader.kt:194`). The schema would need real changes. | EVIDENCE |
| 5. Providers | Arcade sets are **FBNeo / MAME romset collections**, versioned to the core (e.g. "FBNeo v1.0.0.03 non-merged"). Internet Archive hosts FBNeo and MAME split/merged sets; torrents exist per DAT version. There is **no No-Intro/Redump for arcade** — the canonical DB is the emulator's own DAT. `MiNERVA` has no known arcade census input in the repo. | EVIDENCE (set landscape), UNKNOWN (MiNERVA arcade coverage) |

**Assessment:** despite being next in the roadmap, arcade is the **worst fit** for the current Source Index. The launch/BIOS-merge side is already built (via ROMgi); the acquisition/identity side would need a schema that models "romset name + DAT version + BIOS-zip dependencies" instead of "one file, one content hash". A pragmatic path: keep arcade on ROMgi and skip a Source Index shard for it, or design "Source Index v2 — set-based platforms" first.

---

## 3. Sony PlayStation (PS1) — multi-disc + BIOS

| Q | Finding | Label |
|---|---|---|
| 1. Shard exists / structure | No shard. Does **not** fit the cartridge schema. PS1 identity is a Redump disc dump (`.cue`+`.bin` or `.chd`); many titles are **2–4 discs with an `.m3u`**. A Source Index PS1 shard needs: candidate = a disc *or* a disc-set, plus a way to express the M3U grouping. ROMgi already models this (`M3U_PLATFORMS` includes `ps1`, `RomDownloadSet` + `persistSet`). | EVIDENCE |
| 2. BIOS | **Effectively required.** `EmulatorProfiles` ps1 → `"BIOS PS1 recomendada"`. SwanStation/Beetle PSX want region BIOS (`scph5500.bin` JP / `scph5501.bin` US / `scph5502.bin` EU, or `scph1001.bin`). SwanStation has an open-source fallback but compatibility drops. Not modelled in the acquisition plane (§0.3). | EVIDENCE |
| 3. Core / mapped | `swanstation_libretro_android.so` — **already mapped** (`EmulatorProfiles.kt`, `ps1`, engine AUTO, external `DuckStation`). RetroArch device listing showed `config/PCSX-ReARMed/` (a different PS1 core) → PS1 emulation has been used on device; SwanStation install status **UNKNOWN**. `beetle_psx_hw` (better accuracy/HW rendering) is not mapped. | EVIDENCE (mapped), UNKNOWN (device core) |
| 4. Acquisition complexity | **Multi-disc + BIOS.** `.chd` is the practical single-file container (compresses `.cue`/`.bin`); still 300–700 MB per disc. Multi-disc editions need all discs + `.m3u`. The `RomDownloadSet` path exists but is not reachable from Source Index `resolve()`. | EVIDENCE |
| 5. Providers | **Redump "Sony - PlayStation"** (per-region sets: USA / Europe / Japan). Internet Archive hosts Redump PS1 sets and there are per-set torrents. Community `.chd` conversions of Redump also circulate. No No-Intro (that's cartridges). MiNERVA PS1 census input: **UNKNOWN** (not in repo). | EVIDENCE (Redump), UNKNOWN (MiNERVA) |

---

## 4. Sega Saturn — like PS1, harder emulation

| Q | Finding | Label |
|---|---|---|
| 1. Shard exists / structure | No shard. Same shape as PS1 — Redump `.cue`/`.bin`/`.chd`, some multi-disc (`.m3u`). Cartridge schema does not fit. | EVIDENCE |
| 2. BIOS | **Required.** `EmulatorProfiles` saturn → `"BIOS Saturn recomendada/requerida según motor"`. Beetle Saturn requires `sega_101.bin` (JP) and `mpr-17933.bin` (US/EU) in `system/`; a few games also need cartridge-ROM files (`mpr-18811-mx.ic1` for KOF '95, `mpr-19367-mx.ic1` for Ultraman). | EVIDENCE |
| 3. Core / mapped | `mednafen_saturn_libretro_android.so` (Beetle Saturn) — **already mapped** (`EmulatorProfiles.kt`, `saturn`, engine AUTO, external `Yaba Sanshiro 2 Pro`). **INFERENCE:** Beetle Saturn is CPU-heavy — full-speed Saturn on a Xiaomi 14T-class device is plausible but not verified; this is exactly the kind of case OZ-EQS "full-speed performance without mandatory frameskip" hard-gates. `kronos` is x86-only (not an Android option). | EVIDENCE (mapped), INFERENCE (perf) |
| 4. Acquisition complexity | **Multi-disc + BIOS + occasional cartridge ROM.** Highest disc-side complexity of the 5. | EVIDENCE |
| 5. Providers | **Redump "Sega - Saturn"** (per-region). Internet Archive + torrents. `.chd` conversions circulate. MiNERVA Saturn census: **UNKNOWN**. | EVIDENCE (Redump), UNKNOWN (MiNERVA) |

---

## 5. Sega Dreamcast — GDI/CHD, BIOS mostly-optional

| Q | Finding | Label |
|---|---|---|
| 1. Shard exists / structure | No shard. Redump `.gdi` (+ track files) or `.chd`; `.cdi` is the older lossy format. Some multi-disc (`.m3u`). Cartridge schema does not fit; `.gdi` is itself a multi-file container (an index + several `track*.bin/.raw`) unless packed as `.chd`. | EVIDENCE |
| 2. BIOS | **Mostly optional, sometimes required.** `EmulatorProfiles` dreamcast → `"BIOS opcional para Dreamcast; requerida para Naomi/Atomiswave"`. Flycast can HLE-boot many GD-ROM games, but `dc_boot.bin` + `dc_flash.bin` in `system/dc/` are needed for the boot animation, VMU, region/date, and a set of games. In practice most users provide them. | EVIDENCE |
| 3. Core / mapped | `flycast_libretro_android.so` — **already mapped** (`EmulatorProfiles.kt`, `dreamcast`, engine EXTERNAL, external `Flycast` app `com.flycast.emulator` — marked RUNTIME_RESOLVED_VIEW: "actividad solo se acepta si Android resuelve ACTION_VIEW"). RetroArch device listing showed `config/Flycast/`. **INFERENCE:** Flycast core installed on device. | EVIDENCE (mapped), INFERENCE (device) |
| 4. Acquisition complexity | **Multi-file per disc (`.gdi` set) or single `.chd`; occasional multi-disc.** If Oz standardises on `.chd` it collapses to one file per disc — but Redump distributes `.gdi`. A shard would likely need to target `.chd` conversions or handle `.gdi` track bundles. | EVIDENCE |
| 5. Providers | **Redump "Sega - Dreamcast"**. Large community `.chd` re-packs. Internet Archive + torrents. MiNERVA Dreamcast census: **UNKNOWN**. | EVIDENCE (Redump), UNKNOWN (MiNERVA) |

---

## 6. Summary matrix

| Platform | Fits current schema? | BIOS | Core (mapped in `EmulatorProfiles`) | Acquisition unit | Provider DB |
|---|---|---|---|---|---|
| **N64** | **Yes, unchanged** | none | `mupen64plus_next_gles3` ✅ | single file (`.z64`) | No-Intro |
| Neo Geo/CPS/Arcade | No — needs set/DAT model | romset-coupled (`neogeo.zip`, CPS keys) — merge path exists (ROMgi) | `fbneo` ✅ | romset zip + BIOS zip(s), DAT-versioned | FBNeo/MAME DAT (no No-Intro/Redump) |
| PS1 | No — needs multi-disc + BIOS | region BIOS effectively required | `swanstation` ✅ (`beetle_psx_hw` not mapped) | disc(s) + `.m3u`; `.chd` per disc | Redump |
| Saturn | No — multi-disc + BIOS (+ cart ROM) | `sega_101.bin` / `mpr-17933.bin` required | `mednafen_saturn` ✅ | disc(s) + `.m3u` | Redump |
| Dreamcast | No — `.gdi` bundle or `.chd`; sometimes multi-disc | `dc_boot.bin`/`dc_flash.bin` mostly-optional | `flycast` ✅ | `.chd` per disc, or `.gdi` track set | Redump |

## 7. Conclusions (INFERENCE / DECISION-support, not decisions)

1. **N64 is the natural next Source Index platform** — it is the only one that reuses the cartridge schema, `builder.py`, the single-file `resolve()` path, and the GBA-style integration (shard + config + routing line) with **zero new architecture**. Everything needed on the code side already exists; the open items are a MiNERVA N64 census input and a device perf/compat check.
2. **The 4 disc/arcade platforms all require the same missing pieces**, in rough order of blast radius:
   - a Source Index representation of **multi-file / multi-disc** acquisition (candidate = disc set), OR routing those platforms through the existing ROMgi multi-disc path instead of a shard;
   - a **BIOS acquisition/verification** concept in the acquisition plane (today BIOS is launch-time only, and firmware is not in game preservation sets anyway — so this may stay a "user provides BIOS" flow like GBA's "bring your own ROM");
   - for **arcade specifically**, a romset-name + DAT-version identity model, since `contentIdentity` cannot be a single ROM_CONTENT hash for a zip of many files.
3. **This matches the Dashboard's own "Platform Factory V1" backlog and the "AFTER GBA" note** (`PROJECT_DASHBOARD.md:184-201`): the cartridge factory is proven for 4 platforms; the disc/arcade families need a v2 design, not another hand-crafted shard.
4. **Provider risk is now live for all platforms, existing ones included:** Myrient's 2026-03-31 shutdown means the MiNERVA pipeline's upstream is gone; torrent-based acquisition persists only while infohashes stay seeded, and the community re-seed may change them. A dependency audit of the current 4 shards' infohashes against live seeders is warranted independently of new-platform work.

## 8. Open items / UNKNOWN

- Whether MiNERVA (or `tools/source_index/`) can produce census inputs for N64 / PS1 / Saturn / Dreamcast / arcade — no census inputs for these are in the repo.
- Live seeder health of the existing 4 shards' torrents post-Myrient.
- Device-side: which of Mupen64Plus-Next / SwanStation / Beetle Saturn / Flycast / FBNeo are actually installed on the Xiaomi 14T (config dirs were seen for FinalBurn Neo, Flycast, Mupen64Plus-Next, PCSX-ReARMed, SwanStation, mGBA in an earlier listing → INFERENCE most are present; not verified this session).
- Saturn full-speed performance on the certified device tier (OZ-EQS hard gate).
- Dreamcast: whether Oz would standardise on `.chd` (single file) or support `.gdi` track bundles.
- systemId naming reconciliation (`dc` vs `dreamcast`, `sat` vs `saturn`, `ps1` consistent) before any disc platform is routed.

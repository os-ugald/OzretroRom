# Launch Validation — NES / SNES / Genesis (device evidence)

**Date of work:** 2026-09-03 (UTC 04:36 – 05:01)
**Device:** Xiaomi 2406APNFAG (14T) · Android 15 · API 35 · arm64-v8a · on Wi-Fi
**App:** `cl.gakuto.kizuna` 0.6.0-beta.4 (60018), **debug build** of `feature/gba-catalog-v1` @ `0a63714` (code baseline `27c7b1f`; `0a63714` added only `.claude/` + `CLAUDE.md`)
**Emulator:** RetroArch `com.retroarch` 1.22.2_GIT
**ROM library (SAF grant):** `content://com.android.externalstorage.documents/tree/primary:Download/Roms` (global), granted by the Product Owner
**Report state:** untracked, for Oz Forge — Command review. **Not committed.**

---

## 1. What was requested

For NES, SNES and Genesis — 2 games each, chosen from the available catalog — capture per game:
1. a **real download** from the app (not simulated), with the full-process log;
2. **launch via RetroArch**, with the log confirming which core was resolved and loaded;
3. **confirmation the game actually ran** (not just that RetroArch opened) — screenshot or equivalent.

Report the raw result per game (PASS / FAIL / NOT RUN), no global verdict.

## 2. Method

- App is a debug build → private data readable via `run-as`.
- **Primary evidence: the app's own `DiagnosticLogger` session files** (`files/diagnostics/session-*.log`), pulled per game. These are privacy-sanitised (no ROM bytes, no full URLs) and record every stage: `SOURCE_INDEX_CANDIDATE_SELECTED` → `TORRENT_STARTED` / `TORRENT_FILE_SELECTED` / `TORRENT_PRIORITIES_APPLIED` → `ROM_ARCHIVE_EXTRACTED` → `BACKGROUND_DOWNLOAD_SAVED` → `RETROARCH_CORE_SIDELOAD_*` / `RETROARCH_NATIVE_LAUNCHED core=…`.
- Per game also: `oz_persistent_rom_library` record, `oz_background_rom_downloads` task store, ROM-folder diff, Room row.
- Screenshots: `adb exec-out screencap` for NES (Mesen) and SNES (snes9x); **RetroArch Quick Menu → Take Screenshot** for Genesis (`genesis_plus_gx`) — `adb screencap` returned a black frame for that core on all three Genesis titles tested here, while the emulator was confirmed advancing via the stats overlay each time. Whether that is a systemic genesis_plus_gx/screencap problem or something ROM-specific is not conclusively established (see the Sonic 2 open item).
- The Product Owner drove every download and launch in the app UI; `adb shell input` is blocked on this device (MIUI INJECT_EVENTS), so UI automation was not possible.
- All 6 target games were selected from the **Oz Source Index** catalog (4415 NES / 2505 SNES / 1918 Genesis controlled games), i.e. real `oz-source-index` projections. `RomLibraryResolver` skips folder scanning for Source-Index identity, so pre-existing loose ROM files in `Download/Roms` were ignored and every game downloaded fresh (with a dedup filename suffix where a same-name file already existed — the originals were never overwritten).

## 3. Results

| # | System | Game | Download | Launch + core | Ran | Overall |
|---|---|---|---|---|---|---|
| 1 | NES | Super Mario Bros. 3 | PASS | PASS · `mesen_libretro_android.so` (sideloaded) | PASS | **PASS** |
| 2 | NES | Mega Man 2 | PASS | PASS · `mesen_libretro_android.so` | PASS | **PASS** |
| 3 | SNES | Super Mario World | PASS | PASS · `snes9x_libretro_android.so` | PASS | **PASS** |
| 4 | SNES | Super Metroid | PASS | PASS · `snes9x_libretro_android.so` | PASS | **PASS** |
| 5 | Genesis | Gunstar Heroes | PASS | PASS · `genesis_plus_gx_libretro_android.so` | PASS | **PASS** |
| 6 | Genesis | Streets of Rage 2 | PASS | PASS · `genesis_plus_gx_libretro_android.so` | PASS | **PASS** |
| — | Genesis | Sonic The Hedgehog 2 | PASS | PASS · `genesis_plus_gx_libretro_android.so` | **NOT RUN** | **partial — superseded by Gunstar Heroes** |

**6 of 6 target games: PASS on all three criteria.** Every resolved core matches its platform's `SourceIndexCatalogConfig.coreId` (`mesen`, `snes9x`, `genesis_plus_gx`).

### Sonic The Hedgehog 2 — why it is a partial

The app resolved it to the artifact `Sonic The Hedgehog 2 (World) (Rev A) (Sonic Classic Collection).zip` — a ROM extracted from the Nintendo DS "Sonic Classic Collection" compilation, not a clean stand-alone Mega Drive dump. Download and launch succeeded (2 sessions), but `adb screencap` returned black and no RetroArch internal screenshot was taken before the Product Owner switched to Gunstar Heroes. The stats overlay showed frames advancing and audio active, but the phone-screen visual was never established. Its ROM and Room row remain on the device.

**Product Owner inference (not yet verified):** the black-screenshot issue may be specific to this ROM rather than a systemic genesis_plus_gx/screencap problem — pending re-test to confirm, deferred until the UI supports in-app ROM deletion for cleanup. Note: the report's own findings show `adb screencap` failing consistently across the Genesis titles tested (Gunstar Heroes and Streets of Rage 2 both required RetroArch's internal screenshot instead), which is a competing explanation not yet ruled out.

See `Genesis/05_sonic-the-hedgehog-2/RESULT.md`.

## 4. Downloads produced this session (`Download/Roms/`)

```
Nintendo Entertainment System/  Super Mario Bros. 3 (USA) (Oz 2).nes    393232
                                Mega Man 2 (USA) (Capcom Town).nes      262160
Super Nintendo/                 Super Mario World (USA).sfc             524288
                                Super Metroid (USA, Europe) (En,Ja) (Virtual Console, Classic Mini, Switch Online) (Oz 2).sfc  3145728
Mega Drive _ Genesis/           Gunstar Heroes (USA).md               1048576
                                Streets of Rage 2 (USA) (Oz 2).md      2097152
                                Sonic The Hedgehog 2 (World) (Rev A) (Sonic Classic Collection).md  1048576
```

Room `games` (source `oz-source-index`, with a local `contentUri`) after the session: Super Mario Bros. 3, Mega Man 2 (nes); Super Mario World, Super Metroid (snes); Gunstar Heroes, Sonic The Hedgehog 2, Streets of Rage 2 (genesis). All `libraryOrigin=DOWNLOADED`. `launchValidated=1` is the per-platform config value (NES/SNES/Genesis are "frozen" in `SourceIndexCatalogConfig`), not a per-run assertion.

## 5. Scope / limits

- One device, one run per game, one candidate each (whatever `selectPreferredRelease` picked). No repeat runs, no second device.
- "Ran" = the game rendered its intro/title and advanced frames at ~60 fps with audio; no gameplay-depth or save-state testing.
- All candidates were `provider=minerva` selective single-file downloads from the MiNERVA / No-Intro full-set torrents (NES infohash `6864d0c0…`, SNES `93e0d2060d…`, Genesis `7bed100799…`), which are very well seeded — downloads completed in 7–10 s each. Seeding health for other candidates is not implied.
- The debug build's `CORE_DOWNLOAD_POLICY=development`; a release build gates core provisioning through `trusted_cores.json` (currently empty). Cores here were already installed in RetroArch or sideloaded by Oz.

## 6. Folder layout

```
SUMMARY.md                          this file
NES/01_super-mario-bros-3/          RESULT.md + diag logs + screenshot + state
NES/02_mega-man-2/
SNES/03_super-mario-world/
SNES/04_super-metroid/
Genesis/05_gunstar-heroes/
Genesis/05_sonic-the-hedgehog-2/    partial (download+launch PASS, visual NOT RUN)
Genesis/06_streets-of-rage-2/
_session_downloaded_files.txt       device folder diff
oz-retrorom.db(+wal/shm)            final Room DB snapshot
```

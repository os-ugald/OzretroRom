# NES · Super Mario Bros. 3 — RESULT

**Date:** 2026-09-03 (UTC 04:36) · **Device:** Xiaomi 2406APNFAG (14T) / Android 15 / API 35
**App:** cl.gakuto.kizuna 0.6.0-beta.4 (debug; branch feature/gba-catalog-v1 @ 0a63714, code baseline 27c7b1f)
**ROM folder (SAF):** `content://…/tree/primary:Download/Roms`

| Criterion | Verdict | Evidence |
|---|---|---|
| 1. Real download from the app (not simulated) | **PASS** | `diag_session-…-download-….log`: `TORRENT_STARTED infohash=6864d0c06b48365d208c7c5f2b1570087f4712de` → `TORRENT_FILE_SELECTED index=5978 bytes=226318` → `TORRENT_PRIORITIES_APPLIED target=5978 active=1 ignored=7100` (selective single file from the MiNERVA / No-Intro NES set torrent) → `ROM_ARCHIVE_EXTRACTED entry=Super Mario Bros. 3 (USA).nes bytes=393232` → `VALIDATION_OK` → `BACKGROUND_DOWNLOAD_SAVED file=Super Mario Bros. 3 (USA) (Oz 2).nes`. Elapsed ≈ 7 s (04:36:07 → 04:36:14). New file on disk: `…/Nintendo Entertainment System/Super Mario Bros. 3 (USA) (Oz 2).nes` (393232 B, mtime 00:36). Pre-existing `Super Mario Bros. 3 (USA).nes` (Aug 24) left untouched — dedup suffix `(Oz 2)`. |
| 2. Launch via RetroArch — core resolved & loaded | **PASS** | `RETROARCH_CORE_SIDELOAD_STARTED core=mesen_libretro_android.so` → `RETROARCH_CORE_SIDELOAD_CONFIRMED core=mesen_libretro_android.so` → `RETROARCH_NATIVE_LAUNCHED package=com.retroarch core=mesen_libretro_android.so file=Super Mario Bros. 3 (USA) (Oz 2).nes`. Core matches `NesSourceIndexCatalog.coreId`. Mesen was freshly sideloaded (first use this session — the "load core, close RetroArch, auto-relaunch" flow). |
| 3. Game actually ran (not just RetroArch opened) | **PASS** | `03_game_running_CONFIRMED.png`: SMB3 title screen rendering inside `com.retroarch/.browser.retroactivity.RetroActivityFuture`; RetroArch stats overlay reads `Size: 256x240`, `FPS: 60.0998`, `FrameRate: 60.08 fps`, `Frames: 2146`, `-Dropped: 1`, `VIDEO: vulkan XRGB8888`, `AUDIO: opensl FLOAT`. First live session ran 04:36:15 → 04:37:40 (≈ 85 s, per `SESSION_RETURN_TARGET Oz`); screenshot is from an intent re-launch of the now-local ROM (`am start -a cl.gakuto.kizuna.action.PLAY --es oz.gameId oz-source-index-nes-game-9e7c22b974cc…`). |

**Overall: PASS (3/3).**

## Identity / provenance
- Room `games`: id `oz-source-index-nes-game-9e7c22b974cc368430d660c4a2dfa08dc65ba036f89bd4eb0239e91f2e3ad830`, source `oz-source-index`, libraryOrigin `DOWNLOADED`, contentUri → the `(Oz 2)` file, `launchValidated=1` (config value for NES; not per-run proof), lastPlayedAt set.
- `oz_persistent_rom_library` record `rom.v3.f2e1664a4d474d237b092fa76822e05aeadf7086229ce5988f1fc15565372040`: state `COMPLETE`, ownership `OZ_OWNED`, sha256 `c183811f6ce3a63ec59fc949a89b02141b779814276506de561fe1f8e85c94e4`, durable provenance {gameId `9e7c22b974cc…`, releaseId `5102a2dc82…`, candidateId `b0e0ce8effca…`, provider `minerva`, infoHash `6864d0c0…`, fileIndex 5978, exactFullPath `Minerva_Myrient/No-Intro/Nintendo - Nintendo Entertainment System (Headered)/Super Mario Bros. 3 (USA).zip`, expectedSize 226318}.

## Files in this folder
- `diag_session-20260903-043604-1ce18941-….log` — main session (resolve → BACKGROUND_DOWNLOAD_SCHEDULED → SESSION_PHASE download → DIRECT_ROM_READY → RetroArch core sideload → NATIVE_LAUNCHED)
- `diag_session-20260903-043606-download-….log` — torrent download sub-session
- `after_rom_library.xml`, `after_taskstore.xml` — post-run app state (`oz_persistent_rom_library` / `oz_background_rom_downloads`)
- `baseline_*` — folder + task-store before the run
- `after_nes_folder.txt` — NES folder listing after (shows the new `(Oz 2)` file next to the untouched original)
- `03_game_running_CONFIRMED.png` — game rendering
- `logcat_relevant.txt` — filtered logcat excerpt (full continuous capture discarded; diag logs are the primary record)

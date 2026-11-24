# NES · Mega Man 2 — RESULT

**Date:** 2026-09-03 (UTC 04:42) · **Device:** Xiaomi 2406APNFAG (14T) / Android 15 / API 35
**App:** cl.gakuto.kizuna 0.6.0-beta.4 (debug; branch feature/gba-catalog-v1 @ 0a63714, code baseline 27c7b1f)

| Criterion | Verdict | Evidence |
|---|---|---|
| 1. Real download from the app | **PASS** | `diag_session-…-download-….log`: `TORRENT_STARTED infohash=6864d0c06b48365d208c7c5f2b1570087f4712de` (same MiNERVA / No-Intro NES set torrent) → `TORRENT_FILE_SELECTED index=3793 bytes=135156` → `TORRENT_PRIORITIES_APPLIED target=3793 active=1 ignored=7100` → `ROM_ARCHIVE_EXTRACTED entry=Mega Man 2 (USA) (Capcom Town).nes bytes=262160` → `VALIDATION_OK` → `BACKGROUND_DOWNLOAD_SAVED`. Elapsed ≈ 7 s (04:42:19 → 04:42:26). New file: `…/Nintendo Entertainment System/Mega Man 2 (USA) (Capcom Town).nes` (262160 B, mtime 00:42). Pre-existing `Mega Man 2 (USA).nes` (Aug 25) untouched. |
| 2. Launch via RetroArch — core resolved & loaded | **PASS** | `RETROARCH_NATIVE_LAUNCHED package=com.retroarch core=mesen_libretro_android.so file=Mega Man 2 (USA) (Capcom Town).nes`. No sideload cycle this time — Mesen already present from the SMB3 run, so direct native launch (≈ 0.3 s from `DIRECT_ROM_READY` to `NATIVE_LAUNCHED`). |
| 3. Game actually ran | **PASS** | `03_game_running.png` (captured live while in `com.retroarch/.browser.retroactivity.RetroActivityFuture`): the Mega Man 2 intro sequence — purple cityscape with elevators, text "WAS CREATED. / DR. LIGHT CREATED MEGAMAN". Stats overlay: `Size: 256x240`, `FPS: 60.0998`, `FrameRate: 59.80 fps`, `Frames: 943`, `-Dropped: 2`, `VIDEO: vulkan`, `AUDIO: opensl`. |

**Overall: PASS (3/3).**

## Identity / provenance
- game `oz-source-index-nes-game-1b99e423f8f821c3d25afd253800c4f5ccc038a81c1820b8928cd39e3d55986d`
- release `4e9908097f5fd37883fbfc340f5c97c8bf35daad010f5a8ec960ca36f79cf66b` · candidate `f075f2d1a275b81d91eb429f94fdb4fe93b54f07216301b4a2830082649c5458`
- `oz_persistent_rom_library`: state `COMPLETE`, ownership `OZ_OWNED`, sha256 `62d2a246dbe0b05b…`, provenance {provider minerva, infoHash 6864d0c0…, fileIndex 3793, exactFullPath `Minerva_Myrient/No-Intro/Nintendo - Nintendo Entertainment System (Headered)/Mega Man 2 (USA) (Capcom Town).zip`, expectedSize 135156}.

## Files
`diag_session-…-d631cb16-….log` (main) · `diag_session-…-download-22996404-….log` (torrent) · `after_rom_library.xml` · `after_taskstore.xml` · `after_nes_folder.txt` · `baseline_*` · `03_game_running.png` · `logcat_relevant.txt`

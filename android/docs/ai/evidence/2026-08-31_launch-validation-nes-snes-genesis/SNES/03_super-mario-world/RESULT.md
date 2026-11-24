# SNES · Super Mario World — RESULT

**Date:** 2026-09-03 (UTC 04:45) · **Device:** Xiaomi 2406APNFAG (14T) / Android 15 / API 35
**App:** cl.gakuto.kizuna 0.6.0-beta.4 (debug; feature/gba-catalog-v1 @ 0a63714, code 27c7b1f)

| Criterion | Verdict | Evidence |
|---|---|---|
| 1. Real download | **PASS** | `TORRENT_STARTED infohash=93e0d2060d1b05f5b19d0a9e40011b51a76a933c` (MiNERVA / No-Intro SNES set) → `TORRENT_FILE_SELECTED index=3753 bytes=345695` → `TORRENT_PRIORITIES_APPLIED target=3753 active=1 ignored=4641` → `ROM_ARCHIVE_EXTRACTED entry=Super Mario World (USA).sfc bytes=524288` → `BACKGROUND_DOWNLOAD_SAVED`. Elapsed ≈ 8 s (04:45:09 → 04:45:17). **Clean new file** (not pre-existing): `…/Super Nintendo/Super Mario World (USA).sfc` (524288 B, mtime 00:45). |
| 2. Launch via RetroArch — core | **PASS** | `RETROARCH_NATIVE_LAUNCHED package=com.retroarch core=snes9x_libretro_android.so file=Super Mario World (USA).sfc`. Matches `SnesSourceIndexCatalog.coreId`. Direct native launch, no sideload (snes9x already present). |
| 3. Game ran | **PASS** | `03_game_running.png` (live capture, in `RetroActivityFuture`): Super Mario World title screen — Mario on Yoshi, "© 1990-1991 Nintendo". Stats: `Size: 256x224`, `FPS: 60.0988`, `FrameRate: 60.13 fps`, `Frames: 7692`, `-Dropped: 9`, `VIDEO: vulkan RGB565`, `AUDIO: opensl`, `SampleRate: 32040`. ~7700 frames ≈ 2 min runtime. |

**Overall: PASS (3/3).**

## Identity / provenance
- game `oz-source-index-snes-game-994381892c860ae9aca615cd5fcfe73c9d14aed5dd6172e7973e71d6ab216d8c`
- release `dc8b1728a910a4212c627f009178161fc5d1f71bdc8c26a613d71e0a7e21734b` · candidate `a4f2a1c8eeab6347eb23b873a5331e66a54ac4f50cbcd5942170223383f681b0`
- `oz_persistent_rom_library`: state `COMPLETE`, ownership `OZ_OWNED`, provenance {provider minerva, infoHash 93e0d2060d…, fileIndex 3753, exactFullPath `Minerva_Myrient/No-Intro/Nintendo - Super Nintendo Entertainment System/Super Mario World (USA).zip`, expectedSize 345695}.

## Files
`diag_session-…-e2e1efdc-….log` (main) · `diag_session-…-download-1e44815e-….log` (torrent) · `after_rom_library.xml` · `after_taskstore.xml` · `after_snes_folder.txt` · `baseline_*` · `03_game_running.png`

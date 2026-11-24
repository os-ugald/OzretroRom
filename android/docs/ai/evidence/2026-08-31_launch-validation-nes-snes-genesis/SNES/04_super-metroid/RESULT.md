# SNES · Super Metroid — RESULT

**Date:** 2026-09-03 (UTC 04:50) · **Device:** Xiaomi 2406APNFAG (14T) / Android 15 / API 35
**App:** cl.gakuto.kizuna 0.6.0-beta.4 (debug; feature/gba-catalog-v1 @ 0a63714, code 27c7b1f)

| Criterion | Verdict | Evidence |
|---|---|---|
| 1. Real download | **PASS** | `TORRENT_STARTED infohash=93e0d2060d1b05f5b19d0a9e40011b51a76a933c` → `TORRENT_FILE_SELECTED index=3765 bytes=1569912` → `TORRENT_PRIORITIES_APPLIED target=3765 active=1 ignored=4641` → `ROM_ARCHIVE_EXTRACTED entry=Super Metroid (USA, Europe) (En,Ja) (Virtual Console, Classic Mini, Switch Online).sfc bytes=3145728` → `BACKGROUND_DOWNLOAD_SAVED`. Elapsed ≈ 8 s (04:50:08 → 04:50:16, 1.57 MB). New file `…/Super Nintendo/Super Metroid (USA, Europe) (En,Ja) (Virtual Console, Classic Mini, Switch Online) (Oz 2).sfc` (3145728 B, mtime 00:50) — dedup `(Oz 2)`; the Aug 28 same-name file untouched. |
| 2. Launch via RetroArch — core | **PASS** | `RETROARCH_NATIVE_LAUNCHED package=com.retroarch core=snes9x_libretro_android.so`. Direct native launch. |
| 3. Game ran | **PASS** | `03_game_running.png` (live, `RetroActivityFuture`): Super Metroid opening in the Ceres space colony — blue-lit corridor with containment chamber and Samus in her Power Suit. Stats: `Size: 256x224`, `FPS: 60.0988`, `FrameRate: 60.17 fps`, `Frames: 1045`, `-Dropped: 0`, `VIDEO: vulkan RGB565`, `AUDIO: opensl`, `SampleRate: 32040`. |

**Overall: PASS (3/3).**

## Identity / provenance
- game `oz-source-index-snes-game-0aa4d7f97294b264af078cd4d38f2129287e6c4b3980a09d272ac527dbc65220`
- release `2ffbcb3ba8e155254395bf46102de07200438b1c1afd89a4aee79691c51a7a57` · candidate `abecb597ac34f6498f930475d1ad61c665f337b345177aacb8f938d1f9f9f83d`
- `oz_persistent_rom_library`: state `COMPLETE`, ownership `OZ_OWNED`, provenance {provider minerva, infoHash 93e0d2060d…, fileIndex 3765, expectedSize 1569912}.

## Files
`diag_session-…-084a8019-….log` (main) · `diag_session-…-download-1a00ca0b-….log` (torrent) · `after_rom_library.xml` · `after_snes_metroid.txt` · `baseline_*` · `03_game_running.png`

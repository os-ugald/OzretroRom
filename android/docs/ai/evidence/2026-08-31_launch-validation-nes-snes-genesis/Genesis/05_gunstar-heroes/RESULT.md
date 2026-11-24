# Genesis · Gunstar Heroes — RESULT

**Date:** 2026-09-03 (UTC 04:54) · **Device:** Xiaomi 2406APNFAG (14T) / Android 15 / API 35
**App:** cl.gakuto.kizuna 0.6.0-beta.4 (debug; feature/gba-catalog-v1 @ 0a63714, code 27c7b1f)
_(Selected as the Genesis game after Sonic 2 — the "Sonic Classic Collection" ROM variant — could not be visually confirmed; see ../05_sonic-the-hedgehog-2/RESULT.md)_

| Criterion | Verdict | Evidence |
|---|---|---|
| 1. Real download | **PASS** | `TORRENT_STARTED infohash=7bed1007999d817da4605282780a2715f08d470a` (MiNERVA / No-Intro Genesis set) → `TORRENT_FILE_SELECTED index=1410 bytes=661835` → `TORRENT_PRIORITIES_APPLIED target=1410 active=1 ignored=4232` → `ROM_ARCHIVE_EXTRACTED entry=Gunstar Heroes (USA).md bytes=1048576` → `BACKGROUND_DOWNLOAD_SAVED`. Elapsed ≈ 9 s (04:54:29 → 04:54:38). Clean new file `…/Mega Drive _ Genesis/Gunstar Heroes (USA).md` (1048576 B, mtime 00:54). |
| 2. Launch via RetroArch — core | **PASS** | `RETROARCH_BIOS_REQUIREMENT_UNMAPPED system=genesis requirement=No requerida` → `RETROARCH_NATIVE_LAUNCHED package=com.retroarch core=genesis_plus_gx_libretro_android.so file=Gunstar Heroes (USA).md`. Matches `GenesisSourceIndexCatalog.coreId`. genesis_plus_gx was sideloaded on the earlier Sonic 2 attempt, so this was a direct native launch. |
| 3. Game ran | **PASS** | `03_retroarch_screenshot.png` (RetroArch internal screenshot, 320x224 native): Gunstar Heroes intro cutscene — a character on a green mech on a rooftop at sunset (purple sky, moon, city skyline), text "WE HAVE TO STOP HIM". Live `adb screencap` (`03_game_running.png`) returned black — a systematic limitation with the genesis_plus_gx Vulkan surface on this device — but the stats overlay confirmed `Frames: 2439`, `FrameRate: 60.18 fps`, `-Dropped: 1`, `Size: 320x224`, audio `Saturation 74%`. |

**Overall: PASS (3/3).**

## Identity / provenance
- game `oz-source-index-genesis-game-cf9d21eb386a124ec6919ce7c053c5bc180aba3abcb07d03133b5aae56514653`
- release `d0f2c0a7eeab86ad7beb99e0db02db15ce2ba2c75b9cd8856e219db02896d2cd` · candidate `8aaea35a32290a23d0b6c484775301cb0a82930da73b1c9dd326c323f2103359`
- `oz_persistent_rom_library`: state `COMPLETE`, ownership `OZ_OWNED`, sha256 `f177810ce614be21…`, provenance {provider minerva, infoHash 7bed1007999d…, fileIndex 1410, expectedSize 661835}.

## Note — Genesis screenshot method
`adb exec-out screencap` returns a black frame for both Genesis games (genesis_plus_gx core) while working fine for NES (Mesen) and SNES (snes9x). Use RetroArch Quick Menu → Take Screenshot (saved to `/storage/emulated/0/RetroArch/screenshots/`) for genesis_plus_gx visual evidence.

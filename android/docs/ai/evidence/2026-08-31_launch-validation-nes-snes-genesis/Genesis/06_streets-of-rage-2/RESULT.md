# Genesis · Streets of Rage 2 — RESULT

**Date:** 2026-09-03 (UTC 05:00) · **Device:** Xiaomi 2406APNFAG (14T) / Android 15 / API 35
**App:** cl.gakuto.kizuna 0.6.0-beta.4 (debug; feature/gba-catalog-v1 @ 0a63714, code 27c7b1f)

| Criterion | Verdict | Evidence |
|---|---|---|
| 1. Real download | **PASS** | `TORRENT_STARTED infohash=7bed1007999d817da4605282780a2715f08d470a` (MiNERVA / No-Intro Genesis set) → `TORRENT_FILE_SELECTED index=3301 bytes=1031009` → `TORRENT_PRIORITIES_APPLIED target=3301 active=1 ignored=4232` → `ROM_ARCHIVE_EXTRACTED entry=Streets of Rage 2 (USA).md bytes=2097152` → `BACKGROUND_DOWNLOAD_SAVED`. Elapsed ≈ 7 s (05:00:06 → 05:00:13). New file `…/Mega Drive _ Genesis/Streets of Rage 2 (USA) (Oz 2).md` (2097152 B, mtime 01:00) — dedup `(Oz 2)`; Aug 25 same-name file untouched. |
| 2. Launch via RetroArch — core | **PASS** | `RETROARCH_BIOS_REQUIREMENT_UNMAPPED system=genesis requirement=No requerida` → `RETROARCH_NATIVE_LAUNCHED package=com.retroarch core=genesis_plus_gx_libretro_android.so file=Streets of Rage 2 (USA) (Oz 2).md`. Matches `GenesisSourceIndexCatalog.coreId`. Direct native launch (genesis_plus_gx already present). |
| 3. Game ran | **PASS** | `03_retroarch_screenshot.png` (RetroArch internal screenshot, 320x224): SOR2 opening cutscene — dark city street with the protagonists walking, framed border, text "ONE YEAR AFTER THE BATTLE". |

**Overall: PASS (3/3).**

## Identity / provenance
- game `oz-source-index-genesis-game-958c8dfcf1b3bc53d33c6517680bfdcce5303f372d788cad0d579db45346c56e`
- release `3c746886fa431690a94b94c879f28e77fadcfa4e1781c50955b805fa74f5d6ac` · candidate `297b0840aed53d460b6e97b034155177f89eb67e2142715b06e3f1cacbd4d505`
- `oz_persistent_rom_library`: state `COMPLETE`, ownership `OZ_OWNED`, sha256 `4a314edbfee92282…`, provenance {provider minerva, infoHash 7bed1007999d…, fileIndex 3301, expectedSize 1031009}.

# Genesis · Sonic The Hedgehog 2 — RESULT (partial / superseded)

**Date:** 2026-09-03 (UTC 04:52) · **Device:** Xiaomi 2406APNFAG (14T) / Android 15 / API 35
**App:** cl.gakuto.kizuna 0.6.0-beta.4 (debug; feature/gba-catalog-v1 @ 0a63714, code 27c7b1f)

The app resolved this game to the release `590240f4ba…` / candidate `e66a15e278…` whose artifact is
`Sonic The Hedgehog 2 (World) (Rev A) (Sonic Classic Collection).zip` — a ROM extracted from the
Nintendo DS "Sonic Classic Collection" compilation, not a clean stand-alone Mega Drive dump.

| Criterion | Verdict | Evidence |
|---|---|---|
| 1. Real download | **PASS** | `TORRENT_STARTED infohash=7bed1007999d817da4605282780a2715f08d470a` → `TORRENT_FILE_SELECTED index=3132 bytes=749741` → `TORRENT_PRIORITIES_APPLIED target=3132 active=1 ignored=4232` → `ROM_ARCHIVE_EXTRACTED entry=Sonic The Hedgehog 2 (World) (Rev A) (Sonic Classic Collection).md bytes=1048576` → `BACKGROUND_DOWNLOAD_SAVED`. Elapsed ≈ 10 s (04:52:23 → 04:52:33). New file `…/Mega Drive _ Genesis/Sonic The Hedgehog 2 (World) (Rev A) (Sonic Classic Collection).md`. |
| 2. Launch via RetroArch — core | **PASS** | `RETROARCH_CORE_SIDELOAD_STARTED core=genesis_plus_gx_libretro_android.so` → `RETROARCH_CORE_SIDELOAD_CONFIRMED` → `RETROARCH_BIOS_REQUIREMENT_UNMAPPED (No requerida)` → `RETROARCH_NATIVE_LAUNCHED core=genesis_plus_gx_libretro_android.so`. Two sessions: 04:52:33 (≈ 43 s) and a relaunch 04:53:16 (see `diag_…-18a3005f_RELAUNCH.log`, ≈ 35 s). |
| 3. Game ran | **NOT CONFIRMED** | `adb screencap` returned black on all 4 attempts (`03_screencap_BLACK_*.png`). The stats overlay showed the emulator advancing (`Frames: 855 → 1117`, 60 fps, 0 dropped, audio 79–90 %). `adb screencap` also came back black for the other two Genesis titles (Gunstar Heroes, Streets of Rage 2), which is *consistent with* a systemic genesis_plus_gx/screencap problem — but for Sonic 2 specifically no working visual was ever obtained by any method, so a ROM-specific fault is not ruled out. Whether the phone screen itself showed the game was not established before the Product Owner moved to Gunstar Heroes. No RetroArch internal screenshot was taken. |

**Overall: download + launch PASS; runtime visual NOT RUN. Superseded by Gunstar Heroes as the second Genesis title.**

## Identity / provenance
- game `oz-source-index-genesis-game-d3189078f86cf5c8301095748fa5db064dcf2f918cbc62c56435379c6c1298f6`
- release `590240f4ba75cc925144a7786f57a5986f81a9e96dbbc15af0a9e09908502c62` · candidate `e66a15e278aa1d584da8849ec4b289cd212c2cea9e39f4d322747e2689789b00`
- `oz_persistent_rom_library`: state `COMPLETE`, ownership `OZ_OWNED`, sha256 `cfa21bfa47d3a20d…`, provenance {provider minerva, infoHash 7bed1007999d…, fileIndex 3132}.

## Open item

**Product Owner inference (not yet verified):** the black-screenshot issue may be specific to this ROM rather than a systemic genesis_plus_gx/screencap problem — pending re-test to confirm, deferred until the UI supports in-app ROM deletion for cleanup. Note: the report's own findings show `adb screencap` failing consistently across the Genesis titles tested (Gunstar Heroes and Streets of Rage 2 both required RetroArch's internal screenshot instead), which is a competing explanation not yet ruled out.

If a clean Genesis Sonic 2 is wanted in evidence: the standalone No-Intro `Sonic The Hedgehog 2 (World)` (game `d3189078f86c…`) has other releases/candidates; pin one via `--es oz.sourceIndexReleaseId/CandidateId` or pick a different catalog entry, then re-run.

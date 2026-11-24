# GBA launch validation — Metroid Fusion (own-ROM path, acquisition gate untouched)

**Date:** 2026-09-03 (UTC 05:30) · **Device:** Xiaomi 2406APNFAG (14T) / Android 15 / API 35
**App:** cl.gakuto.kizuna 0.6.0-beta.4 (60018), debug build of `feature/gba-catalog-v1` @ `618f4d8` (no code change — code baseline `27c7b1f`)
**Emulator:** RetroArch `com.retroarch` 1.22.2_GIT · **Core:** `mgba_libretro_android.so`
**Report state:** untracked, for Oz Forge — Command review. Not committed.

## Objective

Confirm a GBA ROM launches through Oz's real launch pipeline (SessionActivity → SessionLaunchValidator → RetroArchManager → mGBA) **without touching the acquisition gate** (`GbaSourceIndexCatalog.acquisitionEnabled = false`, `SnesSourceIndexCatalog.kt:324`; `launchValidated = false`, `:320`). Method: user-supplied ROM (Option A), no code change.

## What was exercised

- **Source Index identity**: `Metroid Fusion` GBA projection `oz-source-index-gba-game-494c83ce431ce35754f29e58f012e2dd186ce3a316ed2d62d969f75c0a2dd4cb`, materialized into Room by tapping the catalog tile (`GlobalCatalogActivity.kt:126` → `materialize()`).
- **Own-ROM association**: the game's own ROM was pointed at an existing legitimate file on the device — `/sdcard/Download/Roms/gba/Metroid Fusion (Europe) (En,Fr,De,Es,It).gba` (8,388,608 B, No-Intro, present since 2026-07-27) — via `CrtGameDialog` → source button → "Elegir ROM o archivo propio" → SAF picker. Result: `preferences` key `authorized_source_oz-source-index-gba-game-494c83ce…` = `content://com.android.externalstorage.documents/document/primary:Download/Roms/gba/Metroid Fusion (Europe) (En,Fr,De,Es,It).gba`.
- **Launch**: `am start -a cl.gakuto.kizuna.action.PLAY -n cl.gakuto.kizuna/.launch.SessionActivity --es oz.gameId <projection>` (SessionActivity is exported for this action). In SessionActivity the resolved `source` = `remembered` (the `authorized_source` pref) → `begin(loaded, source)`. The `acquisitionEnabled` / `unresolvedSourceRoute` branch was **never reached** (only consulted when `source == null`).

## Result

| Criterion | Verdict | Evidence |
|---|---|---|
| ROM source (own ROM, GBA acquisition gated by design) | **PASS** | `SESSION_BEGIN system=gba` → `DIRECT_ROM_READY file=Metroid Fusion (Europe) (En,Fr,De,Es,It).gba` → `VALIDATION_OK bytes=8388608 bios=` (no BIOS required — mGBA HLE; matches `EmulatorProfiles` gba "gba_bios.bin opcional"). ROM referenced in place — Oz did **not** copy it into its library (`oz_persistent_rom_library` unchanged at 7 records; no new file in the folder; `contentUri` stays null). |
| Launch via RetroArch — core resolved & loaded | **PASS** | `RETROARCH_CORE_SIDELOAD_STARTED core=mgba_libretro_android.so` → `RETROARCH_CORE_SIDELOAD_CONFIRMED core=mgba_libretro_android.so` → `RETROARCH_NATIVE_LAUNCHED package=com.retroarch core=mgba_libretro_android.so file=Metroid Fusion (Europe) (En,Fr,De,Es,It).gba`. Core matches `GbaSourceIndexCatalog.coreId`. mGBA freshly sideloaded (first use this session). |
| Game actually ran | **PASS** | `03_retroarch_screenshot.png` (RetroArch internal screenshot, 240×160 native GBA): Metroid Fusion title screen — "METROID FUSION™", "JUGAR / Idioma" menu (Spanish, the Europe multi-language ROM), "© 2002 Nintendo", starfield. Room `lastPlayedAt` set (`1788413435530`). |

**Overall: PASS (3/3).** The GBA acquisition gate was not touched; `launchValidated = false` did not block anything (it is a cosmetic label only — `CrtGameDialog.kt:145`).

## Notes / findings

- **The in-app Play button is deliberately disabled for a GBA Source Index projection with no local ROM** (`CrtGameDialog.kt:141`: `isEnabled = (downloaded || source != null || !sourceIndexAvailable || sourceIndexAcquisitionEnabled) && …`). It shows "Descarga pendiente de validación" greyed out. The own-ROM association must be set first via the source button, after which the button becomes "Jugar" and enabled.
- **UX bug (minor):** the "Fuente de juego" dialog (`CrtGameDialog.showSourceOptions`, `:281-293`) calls both `setMessage(...)` and `setItems(...)` on the same `AlertDialog.Builder`. On stock Android the message renders and the item list sits under/behind it, so the choice is easy to miss (the Product Owner initially reported "no puedo elegir"). The list item ("Elegir ROM o archivo propio") is present and does work.
- **`showSourceRequired()` text says "ROMgi"** (`SessionActivity.kt:367-371`) even for a Source Index platform — cosmetic mismatch.
- Room row for the projection: `source=oz-source-index`, `contentUri=null`, `libraryOrigin=NONE`, `launchValidated=0` (GBA config value, not a per-run assertion), `lastPlayedAt` set. The own ROM stays owned by the user (`authorized_source` pref, not `OZ_OWNED` in `PersistentRomLibrary`) — Oz will never delete it.
- `adb screencap` was not attempted for the running game (mGBA is Vulkan; RetroArch's internal screenshot was used, as with the Genesis titles).

## Files

- `diag_session-20260903-053013-5ed543e8-….log` — the launch session (identity skip → SESSION_BEGIN → DIRECT_ROM_READY → mGBA sideload → NATIVE_LAUNCHED)
- `03_retroarch_screenshot.png` — Metroid Fusion running
- `00_session_source_required.png` — SessionActivity boot-sequence screen ("Fuente: Archivo propio", "Preparando mgba_libretro_android.so")
- `after_oz_prefs.xml` — `authorized_source_…` pref set to the in-place ROM
- `after_rom_library.xml` — `oz_persistent_rom_library` unchanged (7 records, no GBA entry)
- `after_gba_folders.txt` / `baseline_gba_folders.txt` — GBA folders identical before/after (ROM referenced in place, not copied)
- `oz-retrorom.db` — Room snapshot (projection row with `lastPlayedAt` set, `launchValidated=0`)

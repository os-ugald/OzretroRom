#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda rel: (ROOT / rel).read_text(encoding="utf-8")

build = read("app/build.gradle.kts")
prefs = read("app/src/main/java/cl/gakuto/kizuna/data/AppPreferences.kt")
romgi = read("app/src/main/java/cl/gakuto/kizuna/catalog/RomgiCatalog.kt")
resolved = read("app/src/main/java/cl/gakuto/kizuna/catalog/ResolvedSource.kt")
session = read("app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt")
main = read("app/src/main/java/cl/gakuto/kizuna/ui/MainActivity.kt")
app = read("app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt")

checks = {
    "NEXT 01 version retained": any(f"versionCode = {code}" in build for code in (60017, 60018)),
    "Media3 pinned stable": 'media3Version = "1.10.1"' in build,
    "orientation preference": "enum class AppOrientationMode" in prefs,
    "automatic full sensor session": "SCREEN_ORIENTATION_FULL_SENSOR" in session,
    "automatic full sensor main": "SCREEN_ORIENTATION_FULL_SENSOR" in main,
    "grid density persisted": "enum class GameGridDensity" in prefs and "gameGridDensity" in prefs,
    "grid density applied": "GridCells.Adaptive(densityCell)" in app,
    "ROMgi file identity": "fileIdentity = playableFileName" in romgi,
    "ROMgi region not destructive": "val eligible = scored" in romgi,
    "ROMgi multiple concrete options ambiguous": "options.size > 1" in romgi,
    "ROMgi alternatives always confirm": "alternatives.isNotEmpty()" in resolved,
    "wallpaper foundation": "wallpaperUri" in prefs and "wallpaperKind" in prefs,
    "soundtrack foundation": "navigationSoundtrackUri" in prefs,
    "hero foundation": "setHeroMedia" in prefs and "heroMediaAudioEnabled" in prefs,
    "NEXT preference keys": all(token in prefs for token in ("private const val KEY_APP_ORIENTATION_MODE", "private const val KEY_GAME_GRID_DENSITY", "private const val KEY_WALLPAPER_URI", "private const val KEY_WALLPAPER_KIND", "private const val KEY_NAV_SOUNDTRACK_URI", "private const val KEY_NAV_SOUNDTRACK_ENABLED", "private const val KEY_HERO_MEDIA_URI", "private const val KEY_HERO_MEDIA_KIND", "private const val KEY_HERO_MEDIA_AUDIO")),
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL"), "-", name)
if failed:
    raise SystemExit("OZ_NEXT_01_FAILED: " + ", ".join(failed))
print(f"OZ_NEXT_01_OK checks={len(checks)}")

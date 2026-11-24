from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

def need(haystack, *needles):
    for needle in needles:
        assert needle in haystack, f"Falta contrato: {needle}"

build = text("app/build.gradle.kts")
prefs = text("app/src/main/java/cl/gakuto/kizuna/data/AppPreferences.kt")
resolver = text("app/src/main/java/cl/gakuto/kizuna/util/ArtworkResolver.kt")
manager = text("app/src/main/java/cl/gakuto/kizuna/launch/SteamGridDbIconSyncManager.kt")
components = text("app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzComponents.kt")
app = text("app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt")
settings = text("app/src/main/java/cl/gakuto/kizuna/ui/LibrarySettingsActivity.kt")
vm = text("app/src/main/java/cl/gakuto/kizuna/ui/compose/OzViewModel.kt")
manifest = text("app/src/main/AndroidManifest.xml")

need(build, 'versionName = "0.6.0-beta.4"')
need(manifest, 'android:allowBackup="false"')
need(prefs, "enum class GameTileStyle", "COMPACT_ICON", "steamGridDbApiKey", "syncedGameIconLocalUri", "steamGridDbGameId")
need(prefs, "enum class GameGridDensity", "gameGridDensity")
need(resolver, "fun tileCandidates", "syncedGameIconLocalUri")
need(manager, 'BASE_URL = "https://www.steamgriddb.com/api/v2"', '"Authorization", "Bearer $apiKey"', '"/search/autocomplete/${encodePath(query)}"', '"/icons/game/$gameId?nsfw=false&humor=false"', 'steamgriddb-icons')
need(components, "GameTileStyle.COMPACT_ICON", "aspectRatio(if (compact) 1f else 0.72f)", "ArtworkResolver.tileCandidates")
need(app, "GridCells.Adaptive(densityCell)", "Modifier.width(108.dp)", "Modifier.width(228.dp)", "heightIn(min = 48.dp)", '"Iconos SGDB"')
need(settings, '"Vista de juegos"', '"SteamGridDB · iconos"', "PasswordVisualTransformation", "GameTileStyle.entries")
need(vm, "SteamGridDbIconSyncManager", "GameTileStyle.COMPACT_ICON", "CollectionArtworkSyncManager")

# Guardrail: normal Compose navigation must never hit the API directly.
assert "steamgriddb.com/api" not in app.lower()
assert "steamgriddb.com/api" not in components.lower()

print("OK Beta4 Fix7: compact UI + SteamGridDB icon cache contracts verified")

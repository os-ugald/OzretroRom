#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cl/gakuto/kizuna"

def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")

def need(path: Path, *tokens: str):
    data = read(path)
    for token in tokens:
        if token not in data:
            raise SystemExit(f"BETA4_FIX1_FAIL {path.name}: missing {token}")

def forbid(path: Path, *tokens: str):
    data = read(path)
    for token in tokens:
        if token in data:
            raise SystemExit(f"BETA4_FIX1_FAIL {path.name}: forbidden {token}")

build = ROOT / "app/build.gradle.kts"
manifest = ROOT / "app/src/main/AndroidManifest.xml"
manager = JAVA / "launch/CollectionDownloadManager.kt"
scheduler = JAVA / "launch/CollectionDownloadScheduler.kt"
job = JAVA / "launch/CollectionDownloadJobService.kt"
fgs = JAVA / "launch/CollectionDownloadForegroundService.kt"
store = JAVA / "launch/CollectionBatchTaskStore.kt"
notify = JAVA / "launch/CollectionDownloadNotification.kt"
art = JAVA / "launch/CollectionArtworkSyncManager.kt"
prefs = JAVA / "data/AppPreferences.kt"
resolver = JAVA / "util/ArtworkResolver.kt"
components = JAVA / "ui/compose/components/OzComponents.kt"
vm = JAVA / "ui/compose/OzViewModel.kt"
ui = JAVA / "ui/compose/OzApp.kt"
iisu = JAVA / "integration/IisuExporter.kt"
validator = JAVA / "launch/SessionLaunchValidator.kt"

need(build, "versionCode = 60010", 'versionName = "0.6.0-beta.4"')
for path in (scheduler, job, fgs, store, notify):
    if not path.exists(): raise SystemExit(f"BETA4_FIX1_FAIL missing {path.name}")
need(manager, "BackgroundRomDownloadEngine", "RomDownloadTaskStore", "missing.forEachIndexed", "Batch download finalizado")
need(scheduler, ".setUserInitiated(true)", "CollectionDownloadForegroundService", "NETWORK_TYPE_ANY")
need(job, "params.network", "CollectionDownloadManager", "setNotification")
need(fgs, "FOREGROUND_SERVICE_TYPE_DATA_SYNC", "CollectionDownloadManager")
need(manifest, ".launch.CollectionDownloadJobService", ".launch.CollectionDownloadForegroundService", "RUN_USER_INITIATED_JOBS")
need(vm, "CollectionDownloadScheduler.schedule", "observeCollectionBatch", "IisuExporter(getApplication()).exportConfigured(preferences, games)")
need(ui, "Descargar ROMs · $missingRoms faltantes", "Descargar artwork", "Publicar en iiSU", "ROMs: lote en segundo plano")
need(art, "downloadOfficialArtwork", "MAX_ARTWORK_BYTES", "setSyncedArtworkLocalUri", 'File(context.filesDir, "artwork-sync/$system")')
forbid(art, "RomgiCatalog", "BackgroundRomDownloadEngine")
need(prefs, "syncedArtworkLocalUri", "setSyncedArtworkLocalUri")
need(resolver, "syncedArtworkLocalUri", "custom + syncedLocal + local")
need(components, "preferences.syncedArtworkLocalUri(game.id)")
need(iisu, "val bridgeGames = games.distinctBy { it.id }", 'put("contentMode", "oz-virtual-on-demand")', "-a cl.gakuto.kizuna.action.PLAY")
forbid(iisu, "val localGames = games.filter { !it.contentUri.isNullOrBlank() }")
need(validator, "Intent.ACTION_MAIN", "explicitIisuMain", "EXTRA_IIISU_ROM", "SessionActivity::class.java.name")
need(ROOT / "IIISU-0.0.7.4-COMMAND.txt", "-a cl.gakuto.kizuna.action.PLAY", '-e ROM "%ROM%"')

# Manifest must remain parseable after adding batch services.
ET.parse(manifest)
print("BETA4_FIX1_BATCH_IISU_OK checks=35")

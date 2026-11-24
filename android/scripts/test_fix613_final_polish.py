#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cl/gakuto/kizuna"
checks = []

def need(path, *tokens):
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        assert token in text, f"{path}: missing {token!r}"
        checks.append((str(path.relative_to(ROOT)), token))

def forbid(path, *tokens):
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        assert token not in text, f"{path}: forbidden {token!r}"
        checks.append((str(path.relative_to(ROOT)), f"forbid:{token}"))

emu = JAVA / "ui/EmulationIntegrationSettingsActivity.kt"
catalog = JAVA / "ui/CatalogDownloadSettingsActivity.kt"
app = JAVA / "ui/compose/OzApp.kt"
vm = JAVA / "ui/compose/OzViewModel.kt"
sync = JAVA / "launch/CollectionArtworkSyncManager.kt"
prefs = JAVA / "data/AppPreferences.kt"
resolver = JAVA / "util/ArtworkResolver.kt"
dao = JAVA / "data/Daos.kt"
manifest = ROOT / "app/src/main/AndroidManifest.xml"

need(emu,
     "Emulación e integraciones", "RetroArch", "Cores por sistema", "BIOS y firmware",
     "Overlays oficiales", "Motores por sistema", "iiSU", "Compatibilidad y diagnóstico",
     "Ajustes que conserva RetroArch", "Video/Vulkan", "shaders", "remaps", "overrides")
forbid(emu, "Cuenta de Internet Archive", "Rendimiento del catálogo", "Borrar catálogo ROMgi local")
need(catalog, "Catálogo y descargas", "ROMgi", "Región preferida", "Rendimiento del catálogo", "torrent selectivo")
need(app, "CatalogDownloadSettingsActivity", "EmulationIntegrationSettingsActivity", "Descargar artwork")
need(vm, "CollectionArtworkSyncUi", "syncSelectedCollectionArtwork", "CollectionArtworkSyncManager")
need(sync, "Preparando índice de artwork", 'state?.status != "READY"', "artworkVariants", "remoteCandidates", "setSyncedArtworkAlias", "setSyncedArtworkLocalUri", "setCollectionArtworkSyncSnapshot")
need(prefs, "syncedArtworkTitle", "syncedArtworkRepo", "CollectionArtworkSyncSnapshot")
need(resolver, "syncedAlias", "syncedArtworkTitle", "syncedArtworkRepo", "remoteCandidates")
need(dao, "artworkVariants", "canonicalTitle=:canonicalTitle", "source='libretro'")
need(manifest, ".ui.CatalogDownloadSettingsActivity", ".ui.EmulationIntegrationSettingsActivity")
print(f"FIX613_FINAL_POLISH_OK checks={len(checks)}")

#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")

def require(haystack: str, needle: str, label: str):
    if needle not in haystack:
        raise SystemExit(f"BETA4_IISU_ARCHITECTURE_FAIL missing {label}: {needle}")

def forbid(haystack: str, needle: str, label: str):
    if needle in haystack:
        raise SystemExit(f"BETA4_IISU_ARCHITECTURE_FAIL forbidden {label}: {needle}")

build = text("app/build.gradle.kts")
lib = text("app/src/main/java/cl/gakuto/kizuna/ui/LibrarySettingsActivity.kt")
emu = text("app/src/main/java/cl/gakuto/kizuna/ui/EmulationIntegrationSettingsActivity.kt")
prefs = text("app/src/main/java/cl/gakuto/kizuna/data/AppPreferences.kt")
exporter = text("app/src/main/java/cl/gakuto/kizuna/integration/IisuExporter.kt")
vm = text("app/src/main/java/cl/gakuto/kizuna/ui/compose/OzViewModel.kt")
validator = text("app/src/main/java/cl/gakuto/kizuna/launch/SessionLaunchValidator.kt")
db = text("app/src/main/java/cl/gakuto/kizuna/data/OzDatabase.kt")

require(build, "versionCode = 60010", "versionCode")
require(build, 'versionName = "0.6.0-beta.4"', "versionName")
forbid(lib, "Biblioteca virtual iiSU", "library UI")
forbid(lib, "globalVirtualTreeUri", "library UI bridge internals")
require(emu, "iiSU · frontend opcional", "iiSU card")
require(emu, "Publicar juegos locales", "local publish action")
require(emu, "publicar una colección completa", "collection guidance")
require(emu, "Opciones avanzadas", "advanced disclosure")
require(prefs, "iisuIntegrationEnabled", "optional integration preference")
require(prefs, "recordIisuSync", "sync snapshot")
require(exporter, "val bridgeGames = games.distinctBy { it.id }", "virtual bridge exporter")
require(exporter, 'put("contentMode", "oz-virtual-on-demand")', "descriptor mode")
forbid(exporter, "val localGames = games.filter { !it.contentUri.isNullOrBlank() }", "local-only exporter")
require(vm, "IisuExporter(getApplication()).exportConfigured(preferences, games)", "collection virtual publish")
require(vm, "accesos virtuales publicados", "collection publish result")
require(validator, "explicitIisuMain", "legacy MAIN bridge fallback")
require(db, "version = 6", "Room version")
forbid(db, "fallbackToDestructiveMigration", "destructive Room migration")

print("BETA4_IISU_ARCHITECTURE_OK checks=18")

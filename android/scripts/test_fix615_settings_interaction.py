#!/usr/bin/env python3
"""Regression coverage for Fix6.15 interactions, adapted for the intentional Beta 4 iiSU move."""
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

catalog = JAVA / "ui/CatalogDownloadSettingsActivity.kt"
emu = JAVA / "ui/EmulationIntegrationSettingsActivity.kt"
library = JAVA / "ui/LibrarySettingsActivity.kt"

# Fix6.15 behavior must remain: region/performance choices are real clickable state.
need(catalog,
     "var selectedRegion by remember",
     "var selectedPerformance by remember",
     "CatalogChoiceButton",
     "selectedRegion = key",
     "preferences.preferredRegion = key",
     "selectedPerformance = mode",
     "preferences.catalogPerformanceMode = mode",
     ".clickable(onClick = onClick)")
forbid(catalog, "FilterChip(")

# Beta 4 intentionally removes iiSU bridge folders from Library settings.
need(library, "Biblioteca ROMs principal", "Carpeta por sistema", "Carátulas")
forbid(library,
       "Biblioteca virtual iiSU",
       "globalVirtualTreeUri",
       "systemVirtualTreeUri",
       "virtualGlobalPicker",
       "virtualSystemPicker")

# The bridge survives only behind the optional iiSU advanced disclosure.
need(emu,
     "iiSU · frontend opcional",
     "Publicar juegos locales",
     "Opciones avanzadas",
     "iisuBridgePicker",
     "Limpiar accesos generados",
     "Custom Launch Command")
forbid(emu,
       "Las carpetas virtuales se administran en Biblioteca y almacenamiento",
       "Abrir Biblioteca y almacenamiento")

print(f"FIX615_SETTINGS_INTERACTION_OK checks={len(checks)} beta4_compat=1")

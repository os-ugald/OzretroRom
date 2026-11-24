#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cl/gakuto/kizuna"

def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")

def need(path: Path, *tokens: str):
    data = text(path)
    for token in tokens:
        assert token in data, f"{path.name}: falta {token!r}"

def forbid(path: Path, *tokens: str):
    data = text(path)
    for token in tokens:
        assert token not in data, f"{path.name}: prohibido {token!r}"

build = ROOT / "app/build.gradle.kts"
exporter = JAVA / "integration/IisuExporter.kt"
session = JAVA / "launch/SessionActivity.kt"
emu = JAVA / "ui/EmulationIntegrationSettingsActivity.kt"
command = ROOT / "IIISU-0.0.7.4-COMMAND.txt"

legacy = JAVA / "ui/SettingsActivity.kt"
legacy_layout = ROOT / "app/src/main/res/layout/activity_settings.xml"
forbid(legacy, "systemFolderPicker", "systemVirtualFolderButton", "clearSystemVirtualButton", "systemVirtualUriText")
forbid(legacy_layout, "systemVirtualFolderButton", "clearSystemVirtualButton", "systemVirtualUriText", "Destino por sistema")

need(build, "versionCode = 60010", 'versionName = "0.6.0-beta.4"')
need(exporter, r'-e ROM \"%ROM%\"', "Publishing is intentionally additive", "clearGeneratedBridge", "isGeneratedDescriptor")
forbid(exporter, "cleanStaleDescriptors(platformFolder, systemGames)")
need(session, "IISU_BRIDGE_ID_RESOLVED", "val rawBaseName = rawFileName.substringBeforeLast('.')", "val rawExtension = rawFileName.substringAfterLast", "expectedExtension = bridgeGame.virtualExtension", "dao.byId(rawBaseName)", "SessionLaunchValidator.validatedReference(this, intent, rawReference)")
s = text(session)
assert s.find("dao.byId(rawBaseName)") < s.find("SessionLaunchValidator.validatedReference(this, intent, rawReference)"), "El bridge debe resolver gameId antes del acceso raw"
need(emu, "Limpiar accesos generados", "Usa solo el Bridge en iiSU", "Custom Launch Command", "Las comillas alrededor de %ROM% son necesarias")
forbid(emu, "iisuSystemBridgePicker", "Destino por sistema")
need(command, '-e ROM "%ROM%"', "SOLO la carpeta Bridge", "Summary > Update")
print("BETA4_FIX4_IISU_BRIDGE_REPAIR_OK checks=28")

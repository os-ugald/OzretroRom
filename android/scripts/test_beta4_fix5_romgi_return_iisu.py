#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/cl/gakuto/kizuna"

def data(path): return path.read_text(encoding="utf-8")
def need(path, *tokens):
    text = data(path)
    for token in tokens:
        assert token in text, f"{path.name}: falta {token!r}"

build = ROOT / "app/build.gradle.kts"
matcher = JAVA / "catalog/RomgiTitleMatcher.kt"
aliases = JAVA / "catalog/RomgiTitleAliasPolicy.kt"
romgi = JAVA / "catalog/RomgiCatalog.kt"
session = JAVA / "launch/SessionActivity.kt"

need(build, "versionCode = 60010", 'versionName = "0.6.0-beta.4"')
need(matcher, "requestedAliases: List<String> = emptyList()", "configuredAlias")
need(aliases, "Super Castlevania IV", "Final Fantasy II", "Final Fantasy III", "Legend of Zelda, The - A Link to the Past")
need(romgi, "RomgiTitleAliasPolicy.aliasesFor(descriptor)", "titleAliases.forEach")
need(session, "returnToExternalLauncher", "isExternalBridgeLaunch", "finishSessionToOrigin", "finishAffinity()", "SESSION_RETURN_TARGET")
assert data(session).count("finishSessionToOrigin()") >= 3, "Debe usarse al volver del emulador y al cerrar la sesión"
print("BETA4_FIX5_ROMGI_RETURN_IISU_OK checks=17")

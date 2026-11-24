#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = (ROOT / "app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt").read_text(encoding="utf-8")
SURFACE_PATH = ROOT / "app/src/main/java/cl/gakuto/kizuna/ui/compose/surface/OzSurface.kt"
SURFACE = SURFACE_PATH.read_text(encoding="utf-8") if SURFACE_PATH.exists() else ""

checks = {
    "surface file": SURFACE_PATH.exists(),
    "surface backdrop": "fun OzSurfaceBackdrop" in SURFACE,
    "focus header": "fun OzFocusHeader" in SURFACE,
    "mixed spatial grid": (
        "fun OzHomeMosaic" in SURFACE
        and "GridItemSpan" in SURFACE
        and (
            "TileSpec" in SURFACE
            or (
                "OzTilePlacement" in SURFACE
                and "enum class OzTileSize" in SURFACE
            )
        )
    ),
    "surface tile": "fun OzSurfaceTile" in SURFACE and "1.045f" in SURFACE,
    "quick menu": "fun OzQuickMenu" in SURFACE and '"Jugar"' in SURFACE and '"Mover"' in SURFACE,
    "controller hints": "fun OzControllerHints" in SURFACE,
    "home uses surface": "OzSurfaceBackdrop" in APP and "OzHomeMosaic" in APP,
    "home old shell removed": 'ScreenShell("Inicio", "Oz RetroRom · consola")' not in APP,
    "dock slim": "Modifier.width(64.dp).fillMaxHeight()" in APP,
    "controller retained": all(x in APP for x in ("Key.ButtonL1","Key.ButtonR1","Key.ButtonY","Key.ButtonX","Key.ButtonSelect")),
}
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL"), "-", name)
bad = [name for name, ok in checks.items() if not ok]
if bad:
    raise SystemExit("OZ_SURFACE_01_FAILED: " + ", ".join(bad))
print(f"OZ_SURFACE_01_OK checks={len(checks)}")

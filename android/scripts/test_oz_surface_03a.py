#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
surface = (ROOT / "app/src/main/java/cl/gakuto/kizuna/ui/compose/surface/OzSurface.kt").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt").read_text(encoding="utf-8")

checks = {
    "custom 2D Layout":
        "Layout(" in surface
        and "Constraints.fixed" in surface,

    "occupancy reflow":
        "fun reflowPlacements(" in surface
        and "fun fits(" in surface
        and "reserve(" in surface,

    "real 2x2 footprint":
        "heightCells" in surface
        and "widthCells" in surface,

    "priority during move":
        "priorityGameId" in surface,

    "reflow neighbors persisted":
        "placements.forEach { savePlacement(preferences, it) }" in surface,

    "full cancel snapshot":
        "mutableStateOf<List<OzTilePlacement>?>(null)" in surface,

    "portrait geometry":
        "val rows = if (columns <= 3) 4 else 2" in surface,

    "landscape geometry":
        "else 2" in surface,

    "page dots instead of technical header":
        "fun OzPageDots(" in surface,

    "giant external home header removed":
        "OzFocusHeader(focusedGame" not in app,

    "short landscape awareness":
        "shortViewport = maxHeight < 500.dp" in app,

    "controller hints only with room":
        "if (!compact && !shortViewport)" in app,

    "Surface 02 organize retained":
        "MODO ORGANIZAR" in surface
        and "Key.ButtonX" in surface
        and "Key.ButtonA" in surface
        and "Key.ButtonB" in surface,
}

for name, ok in checks.items():
    print(("PASS" if ok else "FAIL"), "-", name)

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("OZ_SURFACE_03A_FAILED: " + ", ".join(failed))

print(f"OZ_SURFACE_03A_OK checks={len(checks)}")

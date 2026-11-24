#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda p: (ROOT / p).read_text(encoding="utf-8")

prefs = read("app/src/main/java/cl/gakuto/kizuna/data/AppPreferences.kt")
surface = read("app/src/main/java/cl/gakuto/kizuna/ui/compose/surface/OzSurface.kt")
app = read("app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt")

checks = {
    "placement persistence":
        "fun surfacePlacement(gameId: String)" in prefs
        and "fun setSurfacePlacement" in prefs,

    "three tile sizes":
        all(token in surface for token in (
            'SMALL("1x1"',
            'WIDE("2x1"',
            'LARGE("2x2"',
        )),

    "paged home":
        "pageCapacity" in surface
        and "currentPage" in surface
        and "maxPage" in surface,

    "page indicator":
        (
            '"PÁGINA ${currentPage + 1} / ${maxPage + 1}"' in surface
            or "fun OzPageDots(" in surface
        ),

    "organize mode":
        "organizeGameId" in surface
        and "MODO ORGANIZAR" in surface,

    "dpad movement":
        all(token in surface for token in (
            "Key.DirectionLeft",
            "Key.DirectionRight",
            "Key.DirectionUp",
            "Key.DirectionDown",
        )),

    "x resize":
        "Key.ButtonX" in surface
        and "it.copy(size = it.size.next())" in surface,

    "a commit":
        "savePlacement(preferences, it)" in surface,

    "b cancel":
        "editSnapshot" in surface
        and "Key.ButtonB" in surface,

    "lr page in home":
        "currentRoute == TopDestination.Home.route" in app
        and "surfacePageCommand += 1" in app,

    "real move action":
        "onMove = {" in app
        and "organizeGameId = game.id" in app,

    "real resize action":
        "onResize = {" in app
        and "prefs.setSurfacePlacement(" in app,
}

for name, ok in checks.items():
    print(("PASS" if ok else "FAIL"), "-", name)

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("OZ_SURFACE_02_FAILED: " + ", ".join(failed))

print(f"OZ_SURFACE_02_OK checks={len(checks)}")

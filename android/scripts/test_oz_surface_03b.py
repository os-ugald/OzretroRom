#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
surface = (ROOT / "app/src/main/java/cl/gakuto/kizuna/ui/compose/surface/OzSurface.kt").read_text(encoding="utf-8")
app = (ROOT / "app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt").read_text(encoding="utf-8")
checks = {
    "long press gesture": "combinedClickable(" in surface and "onLongClick" in surface and "onStart()" in surface,
    "start still supported": "Key.ButtonStart" in surface,
    "exact size chooser": all(x in surface for x in ('"Pequeño"', '"Horizontal"', '"Destacado"', "onResizeTo(OzTileSize.SMALL)", "onResizeTo(OzTileSize.WIDE)", "onResizeTo(OzTileSize.LARGE)")),
    "exact size persistence": "onResizeTo = { size ->" in app and "size = size.key" in app,
    "reflow entry retained": "organizeGameId = game.id" in app,
    "quiet idle chrome": "else -> Color.Transparent" in surface and "else if (focused || selected) 2.dp else 0.dp" in surface,
    "contextual labels": "tinyTile" in surface and "showFullLabel" in surface,
    "mockup parity panel": '"TAMAÑO DEL ICONO"' in surface and '"START / mantener pulsado · acciones"' in surface,
    "03A spatial engine retained": "fun reflowPlacements(" in surface and "Layout(" in surface,
}
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL"), "-", name)
bad = [name for name, ok in checks.items() if not ok]
if bad:
    raise SystemExit("OZ_SURFACE_03B_FAILED: " + ", ".join(bad))
print(f"OZ_SURFACE_03B_OK checks={len(checks)}")

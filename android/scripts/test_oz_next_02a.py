#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
read=lambda f:(ROOT/f).read_text(encoding="utf-8")
b=read("app/build.gradle.kts")
a=read("app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt")
c=read("app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzComponents.kt")
checks={
"versionCode 60018":"versionCode = 60018" in b,
"L1":"Key.ButtonL1" in a and "moveTop(-1)" in a,
"R1":"Key.ButtonR1" in a and "moveTop(1)" in a,
"Y":"Key.ButtonY" in a and "searchCommand += 1" in a and "searchFocusRequester.requestFocus()" in a,
"X":"Key.ButtonX" in a and "filterCommand += 1" in a and "if (filterCommand > 0) showFilters = true" in a,
"Select":"Key.ButtonSelect" in a and "TopDestination.Settings.route" in a,
"B":"Key.ButtonB" in a and "popBackStack()" in a,
"Start menu":"Key.ButtonStart" in c and "showContextMenu = true" in c,
"A open":"Key.ButtonA" in c,
"context play":"SessionActivity.EXTRA_GAME_ID" in c,
"AI copy removed":"ARCADE MARQUEE // 90s REMIX" not in a,
"brand simplified":"Biblioteca · colecciones · emulación" in a,
}
for n,ok in checks.items(): print(("PASS" if ok else "FAIL"),"-",n)
bad=[n for n,ok in checks.items() if not ok]
if bad: raise SystemExit("OZ_NEXT_02A_FAILED: "+", ".join(bad))
print(f"OZ_NEXT_02A_OK checks={len(checks)}")

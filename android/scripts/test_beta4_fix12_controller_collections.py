#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda rel: (ROOT / rel).read_text(encoding='utf-8')

oz = read('app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt')
build = read('app/build.gradle.kts')
readme = read('README.md')
doc = read('docs/BETA4_FIX12_CONTROLLER_COLLECTIONS.md')

checks = {
    'fix12 versionCode': any(f'versionCode = {code}' in build for code in (60016, 60017, 60018)),
    'readme versionCode': any(f'versionCode = {code}' in readme for code in (60016, 60017, 60018)),
    'height adaptive collection layout': 'val controllerCompact = maxHeight < 620.dp' in oz,
    'compact collection header exists': 'if (controllerCompact)' in oz and 'ROMs $missingRoms' in oz,
    'compact mode removes permanent search field': 'CollectionSearchDialog(' in oz and 'showSearch = true' in oz,
    'search dialog is explicit': 'private fun CollectionSearchDialog' in oz and 'Buscar en la colección' in oz,
    'game grid owns remaining height': 'modifier = Modifier.weight(1f)' in oz,
    'initial grid focus is requested': 'requestInitialFocus = true' in oz,
    'focus requester imported': 'import androidx.compose.ui.focus.FocusRequester' in oz and 'focusRequester(firstGameFocusRequester)' in oz,
    'focus request delayed until paging content exists': 'games.itemCount > 0' in oz and 'firstGameFocusRequester.requestFocus()' in oz,
    'standard tall layout retained': 'Buscar dentro de la colección…' in oz,
    'SGDB and iiSU actions retained': '"SGDB"' in oz and 'syncSelectedCollectionWithIisu' in oz,
    'fix12 documentation': 'Fix 12' in doc and 'D-pad' in doc,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    for name in failed:
        print('FAIL:', name)
    raise SystemExit(1)
print(f'FIX12_CONTROLLER_COLLECTIONS_OK checks={len(checks)}')

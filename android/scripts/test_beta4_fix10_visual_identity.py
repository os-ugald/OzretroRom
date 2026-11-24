#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def text(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

def check(cond, msg, checks):
    checks.append((bool(cond), msg))

checks=[]
build=text('app/build.gradle.kts')
app=text('app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt')
theme=text('app/src/main/java/cl/gakuto/kizuna/ui/compose/theme/OzTheme.kt')
prefs=text('app/src/main/java/cl/gakuto/kizuna/data/AppPreferences.kt')
vm=text('app/src/main/java/cl/gakuto/kizuna/ui/compose/OzViewModel.kt')
main=text('app/src/main/java/cl/gakuto/kizuna/ui/MainActivity.kt')
application=text('app/src/main/java/cl/gakuto/kizuna/OzApplication.kt')

check(any(f'versionCode = {code}' in build for code in (60014, 60015, 60016, 60017, 60018)), 'Fix 10 contract is retained in Fix 10 or later', checks)
check('ui-text-google-fonts' in build, 'Compose Google Fonts integration is declared under the BOM', checks)
check('Tu biblioteca, tus historias' not in app, 'generic Home hero copy was removed', checks)
check('OzBrandBanner()' in app and 'ARCADE MARQUEE // 90s REMIX' not in app, 'Oz identity banner remains without generic marquee copy', checks)
check('OzPrimaryTile(' not in app, 'Home no longer duplicates top-level navigation with four large shortcut cards', checks)
check('OzNavGlyph' in app and 'Canvas(Modifier.size(22.dp))' in app, 'bottom/rail navigation uses native line icons instead of Unicode glyphs', checks)
check('if (selected)' in app and 'destination.compactLabel' in app, 'five-destination navigation shows a label only for the active destination', checks)
check('height(58.dp)' in app, 'compact phone navigation is reduced to a 58dp visual dock', checks)
check('contentDescription = destination.label' in app and 'role = Role.Tab' in app, 'icon navigation retains accessibility semantics', checks)

check('enum class AppThemeMode' in prefs and 'DARK("dark", "Oscuro")' in prefs and 'LIGHT("light", "Claro")' in prefs, 'dark/light appearance modes are explicit', checks)
check('KEY_APP_THEME_MODE' in prefs and 'appThemeMode' in prefs, 'appearance choice is persisted', checks)
check('val appThemeMode: StateFlow<AppThemeMode>' in vm and 'fun setAppThemeMode' in vm, 'theme change is observable at runtime', checks)
check('AppearanceCard(viewModel)' in app and 'ThemeChoice' in app, 'Settings exposes an appearance selector', checks)
check('OzTheme(themeMode = themeMode)' in main, 'MainActivity applies the live Compose theme', checks)
check('isAppearanceLightStatusBars' in main and 'isAppearanceLightNavigationBars' in main, 'system bar icon contrast follows light/dark selection', checks)
check('AppCompatDelegate.setDefaultNightMode' in application and 'AppCompatDelegate.setDefaultNightMode' in vm, 'legacy View screens also receive the saved night-mode override', checks)

check('OzDarkPalette' in theme and 'OzLightPalette' in theme and 'darkColorScheme' in theme and 'lightColorScheme' in theme, 'theme defines two complete color schemes', checks)
check('GoogleFont(name = "Oxanium")' in theme, 'Oxanium is the Oz display identity font', checks)
check('FontFamily.SansSerif' in theme and 'FontFamily.Monospace' in theme, 'font strategy retains robust sans/mono fallbacks', checks)
check('isAvailableOnDevice(context)' in theme, 'downloadable font provider is checked before use', checks)
check((ROOT/'app/src/main/res/values-v23/font_certs.xml').exists(), 'official Google Fonts provider certificates are packaged', checks)
check(not list((ROOT/'app/src/main/res').rglob('*.ttf')) and not list((ROOT/'app/src/main/res').rglob('*.otf')), 'no font binaries are bundled', checks)
check((ROOT/'app/src/main/res/values-night/colors.xml').exists(), 'legacy View resources include a night palette', checks)
check((ROOT/'app/src/main/res/values-night/themes.xml').exists(), 'legacy View theme has night system-bar treatment', checks)

# Core text contrast from the documented palette. WCAG relative-luminance math.
def luminance(hex_value):
    h=hex_value.lstrip('#')
    rgb=[int(h[i:i+2],16)/255 for i in (0,2,4)]
    def linear(c): return c/12.92 if c <= .04045 else ((c+.055)/1.055)**2.4
    r,g,b=map(linear,rgb)
    return .2126*r+.7152*g+.0722*b

def contrast(a,b):
    x,y=luminance(a),luminance(b)
    hi,lo=max(x,y),min(x,y)
    return (hi+.05)/(lo+.05)

pairs={
    'dark primary text/background':('#F7F4FF','#08070D'),
    'dark secondary text/surface':('#BDB6CE','#12101A'),
    'dark cyan/background':('#34D7E9','#08070D'),
    'light primary text/background':('#1B1620','#F7F5FA'),
    'light secondary text/surface':('#5D5667','#FFFFFF'),
    'light cyan/background':('#007A87','#F7F5FA'),
    'light violet/background':('#6236C7','#F7F5FA'),
}
for name,(fg,bg) in pairs.items():
    ratio=contrast(fg,bg)
    check(ratio >= 4.5, f'{name} contrast >= 4.5:1 ({ratio:.2f}:1)', checks)

# Parse every XML resource changed by theming.
xml_errors=[]
for p in (ROOT/'app/src/main/res').rglob('*.xml'):
    try: ET.parse(p)
    except Exception as e: xml_errors.append(f'{p.relative_to(ROOT)}: {e}')
check(not xml_errors, 'all Android XML resources parse successfully', checks)

failed=[msg for ok,msg in checks if not ok]
for ok,msg in checks:
    print(('PASS' if ok else 'FAIL')+' - '+msg)
if xml_errors:
    for e in xml_errors: print('XML - '+e)
if failed:
    raise SystemExit(f'Fix 10 visual identity contract failed: {len(failed)} check(s)')
print(f'Fix 10 visual identity contract OK: {len(checks)} checks')

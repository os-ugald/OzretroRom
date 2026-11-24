from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'

def text(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

def parse(rel):
    return ET.parse(ROOT / rel).getroot()

build = text('app/build.gradle.kts')
session = text('app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt')
portrait_text = text('app/src/main/res/layout/activity_session.xml')
land_text = text('app/src/main/res/layout-land/activity_session.xml')
portrait = parse('app/src/main/res/layout/activity_session.xml')
land = parse('app/src/main/res/layout-land/activity_session.xml')

assert any(f'versionCode = {code}' in build for code in (60012, 60013, 60014, 60015, 60016, 60017, 60018))
assert 'versionName = "0.6.0-beta.4"' in build
assert '<ScrollView' not in portrait_text and '<ScrollView' not in land_text
assert portrait.tag.endswith('LinearLayout') and land.tag.endswith('LinearLayout')
assert 'ArtworkResolver.tileCandidates(this, preferences, current)' in session
assert 'binding.downloadProgressContainer.visibility = View.GONE' in session
assert 'binding.downloadProgressContainer.visibility = View.VISIBLE' in session
assert 'binding.preparationCompose.visibility = View.VISIBLE' in session

required_ids = {
    'root', 'sessionArtwork', 'platformBadge', 'sessionTitle', 'sourceText', 'statusText',
    'helperText', 'preparationCompose', 'downloadProgressContainer', 'progressPercentText',
    'elapsedText', 'progressBar', 'fileInfoText', 'downloadedText', 'speedText', 'peersText',
    'etaText', 'stallWarningText', 'retryButton', 'pickRomButton', 'deletePartialButton',
    'cancelButton',
}

def ids(root):
    out = set()
    for node in root.iter():
        raw = node.attrib.get(ANDROID + 'id', '')
        if raw.startswith('@+id/'):
            out.add(raw.split('/', 1)[1])
    return out

assert required_ids.issubset(ids(portrait)), required_ids - ids(portrait)
assert required_ids.issubset(ids(land)), required_ids - ids(land)

for root in (portrait, land):
    artwork = next(n for n in root.iter() if n.attrib.get(ANDROID + 'id') == '@+id/sessionArtwork')
    assert 'Icono del juego' in artwork.attrib.get(ANDROID + 'contentDescription', '')
    assert artwork.attrib.get(ANDROID + 'scaleType') == 'fitCenter'

print('OK Beta4 Fix8: fixed full-window session + game icon contracts verified')

#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
portrait = (ROOT / 'app/src/main/res/layout/activity_session.xml').read_text()
build = (ROOT / 'app/build.gradle.kts').read_text()
activity = (ROOT / 'app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt').read_text()

checks = []
def check(cond, msg):
    checks.append((bool(cond), msg))

check('<ScrollView' not in portrait and '<androidx.core.widget.NestedScrollView' not in portrait,
      'portrait session has no scrolling container')
check('android:id="@+id/sessionArtwork"' in portrait and 'android:layout_width="118dp"' in portrait,
      'portrait keeps a prominent left-side game icon')
check(portrait.find('@+id/sessionArtwork') < portrait.find('@+id/sessionTitle'),
      'game icon is declared before the adjacent information column')
check('android:orientation="horizontal"' in portrait[:portrait.find('@+id/sessionArtwork')],
      'portrait hero section uses a horizontal composition')
for view_id in ['platformBadge','sourceText','sessionTitle','statusText','helperText','progressPercentText','elapsedText',
                'progressBar','fileInfoText','downloadedText','peersText','speedText','etaText','retryButton',
                'pickRomButton','deletePartialButton','cancelButton','downloadProgressContainer','preparationCompose','stallWarningText']:
    check(f'@+id/{view_id}' in portrait, f'portrait exposes required ViewBinding id {view_id}')
check('tileCandidates(current)' in activity or '.tileCandidates(' in activity,
      'SessionActivity still resolves compact game artwork before classic covers')
check(any(f'versionCode = {code}' in build for code in (60013, 60014, 60015, 60016, 60017, 60018)), 'Fix 9 contract is retained in Fix 9 or later')

failed = [msg for ok, msg in checks if not ok]
for ok, msg in checks:
    print(('PASS' if ok else 'FAIL') + ' - ' + msg)
if failed:
    raise SystemExit(f'Fix 9 portrait contract failed: {len(failed)} check(s)')
print(f'Fix 9 portrait contract OK: {len(checks)} checks')

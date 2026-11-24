#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
read = lambda rel: (ROOT / rel).read_text(encoding='utf-8')

session = read('app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt')
persistent = read('app/src/main/java/cl/gakuto/kizuna/launch/PersistentRomLibrary.kt')
build = read('app/build.gradle.kts')
readme = read('README.md')

checks = {
    'fix11 versionCode': any(f'versionCode = {code}' in build for code in (60015, 60016, 60017, 60018)),
    'readme versionCode': any(f'versionCode = {code}' in readme for code in (60016, 60017, 60018)),
    'ps1 auto launch uses fresh barrier': 'prepareFreshPs1LaunchEntry(current, stored)' in session,
    'barrier only targeted to ps1': 'current.systemId == "ps1"' in session,
    'barrier validates direct path': 'awaitStableFreshRomPath(stored, directPath)' in session,
    'barrier requires repeated stable samples': 'PS1_FRESH_PATH_STABLE_SAMPLES = 3' in session,
    'barrier probes SAF uri': 'contentResolver.openInputStream(stored.uri)' in session,
    'barrier validates expected byte size': 'stored.bytes <= 0L || length == stored.bytes' in session,
    'barrier has propagation settle delay': 'PS1_FRESH_PATH_SETTLE_MS = 450L' in session,
    'fallback mirror uses authoritative SAF uri': 'publicStore.publish(stored.uri, stored.displayName)' in session,
    'mirror preserves SAF media uri': 'mirrored.copy(mediaUri = stored.uri)' in session,
    'fresh barrier is diagnosed': 'PS1_FRESH_LAUNCH_BARRIER_BEGIN' in session and 'PS1_FRESH_LAUNCH_BARRIER_READY' in session,
    'barrier failure is controlled': 'PS1_FRESH_LAUNCH_BARRIER_FAILED' in session and 'showFatal(' in session,
    'persistent write is durable helper': 'writePreparedDurably(target.uri, prepared)' in persistent,
    'persistent write requests fsync': 'output.fd.sync()' in persistent,
    'generic SAF fallback retained': 'resolver.openOutputStream(uri, "w")' in persistent,
    'download is not repeated for compatibility fallback': 'No ROM is downloaded again.' in session,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    for name in failed:
        print('FAIL:', name)
    raise SystemExit(1)
print(f'FIX11_PS1_FIRST_LAUNCH_OK checks={len(checks)}')

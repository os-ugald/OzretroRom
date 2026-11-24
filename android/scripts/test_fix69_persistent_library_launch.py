from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SESSION = ROOT / 'app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt'
PUBLIC = ROOT / 'app/src/main/java/cl/gakuto/kizuna/launch/PublicSessionStore.kt'

session = SESSION.read_text(encoding='utf-8')
public = PUBLIC.read_text(encoding='utf-8')

checks = {
    'retroarch resolves SAF entry before preflight': 'ensureRetroArchAccessibleEntry(entry)' in session,
    'persistent content uri remains source of truth': 'mirrored.copy(mediaUri = sourceUri)' in session,
    'scoped-storage path is accepted only when actually readable': 'direct.isFile && direct.canRead() && direct.length() > 0L' in session,
    'unreadable derived path falls back to public compatibility mirror': 'publicStore.publish(sourceUri, entry.displayName)' in session,
    'pending RetroArch install keeps resolved mirror': 'pendingRetroArchEntry = retroEntry' in session,
    'core install uses resolved entry': 'installCoreAndLaunch(current, retroEntry, packageName, requestedCore)' in session,
    'direct launch uses resolved entry': 'launchRetroArchDirect(current, packageName, corePath, retroEntry, installedCore)' in session,
    'public store can copy directly from SAF uri': 'fun publish(source: Uri, displayName: String): Entry' in public,
    'SAF copy reads through ContentResolver': 'context.contentResolver.openInputStream(source)' in public,
    'persistent ROM missing message no longer claims public session': 'El archivo de juego no existe en la sesión pública.' not in session,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    for name in failed:
        print('FAIL:', name)
    raise SystemExit(1)
print(f'FIX69_PERSISTENT_LIBRARY_LAUNCH_OK checks={len(checks)}')

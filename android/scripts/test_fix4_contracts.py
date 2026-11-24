#!/usr/bin/env python3
"""Executable source-contract checks for Beta 3 Fix 4.

These checks validate the requested browser-auth and persistent-ROM architecture without claiming
that Android/Gradle compilation succeeded.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/cl/gakuto/kizuna'
RES = ROOT / 'app/src/main/res'

activity = (JAVA / 'ui/InternetArchiveLoginActivity.kt').read_text()
auth = (JAVA / 'auth/InternetArchiveAuthManager.kt').read_text()
parser = (JAVA / 'auth/InternetArchiveS3PageParser.kt').read_text()
web_policy = (JAVA / 'auth/InternetArchiveWebLoginPolicy.kt').read_text()
layout = (RES / 'layout/activity_internet_archive_login.xml').read_text()
settings_layout = (RES / 'layout/activity_settings.xml').read_text()
build = (ROOT / 'app/build.gradle.kts').read_text()
manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
persistent = (JAVA / 'launch/PersistentRomLibrary.kt').read_text()
session = (JAVA / 'launch/SessionActivity.kt').read_text()
paging = (JAVA / 'data/CatalogPaging.kt').read_text()
repository = (JAVA / 'data/LibraryRepository.kt').read_text()
seed = (JAVA / 'data/SeedLoader.kt').read_text()
adapter = (JAVA / 'ui/GameAdapter.kt').read_text()
dialog = (JAVA / 'ui/CrtGameDialog.kt').read_text()
collections = json.loads((ROOT / 'app/src/main/assets/featured_collections.json').read_text())

checks = {
    # Current IA Auth V2 supersedes Fix4 Custom Tabs while preserving encrypted persistent credentials.
    'webview_login': 'android.webkit.WebView' in activity and 'AndroidView' in activity,
    'restricted_archive_hosts': 'isExpectedWebLoginUrl' in activity and 'isTrustedArchiveUrl' in auth,
    's3_page': 'https://archive.org/account/s3.php' in auth,
    'cookie_exchange': 'CookieManager' in activity and 'completeLoginFromCookies' in auth,
    'exact_s3_dom_capture': 'evaluateJavascript' in activity and 'isS3KeysPageUrl' in activity and 'isS3KeysPage' in web_policy,
    'current_ia_markup': 'hasOfficialLabels' in parser and 'unique.length === 2' in parser,
    'manual_s3_recovery': 'Usar claves' in activity and 'PasswordVisualTransformation' in activity and 'importS3Credentials' in auth,
    'no_js_bridge': 'addJavascriptInterface' not in activity,
    'no_destructive_key_action': all(x not in activity + parser for x in ('Destroy Old Keys', 'Generate New Keys', 'XMLHttpRequest', '.click()')),
    'keystore_encryption': 'AndroidKeyStore' in auth and 'AES/GCM/NoPadding' in auth,
    'auth_states': all(x in auth for x in ('DISCONNECTED', 'CONNECTED', 'STALE', 'INVALID')),
    'failure_counter': 'failureCount' in auth and 'MAX_AUTH_FAILURES = 3' in auth,
    'low_auth': 'Authorization' in auth and 'LOW $access:$secret' in auth,
    'coil_kotlin_compatible': 'coil-compose:3.2.0' in build and 'coil-network-okhttp:3.2.0' in build and 'coil-compose:3.5.0' not in build,

    # Persistent ROM library.
    'persistent_saf': 'DocumentFile.fromTreeUri' in persistent and 'openOutputStream' in persistent,
    'persistent_hash': 'SHA-256' in persistent or 'MessageDigest.getInstance("SHA-256")' in persistent,
    'room_content_uri': 'updateLocalState' in persistent and 'LibraryOrigin.DOWNLOADED' in persistent,
    'local_first': 'persistentLibrary.resolve(loaded)' in session,
    'download_saved_once': 'persistentLibrary.persist(current, prepared, resolvedRegion)' in session,
    'folder_requested_once': 'requestPersistentRomFolder' in session and 'globalRomTreeUri = uri' in session,
    'compat_temp_only': 'compatibility mirror' in session and 'publicStore.publish(prepared)' in session,
    'persistent_not_deleted': 'persistentLibrary.forget' not in session and 'persistentLibrary.delete' not in session,
    'resolved_region_recorded': 'region = resolvedRegion?.takeIf(String::isNotBlank) ?: game.region' in persistent,
    'existing_user_rom_preserved': 'Never delete an existing user file' in persistent and 'uniqueFileName' in persistent,

    # Modernized collections and database-side downloaded filtering.
    'downloaded_collection_asset': any(x.get('id') == 'downloaded-library' for x in collections),
    'downloaded_collection_sql': 'DOWNLOADED_COLLECTION_ID' in paging and "COALESCE(g.contentUri,'')!=''" in paging,
    'downloaded_collection_upgrade': 'ensureRuntimeCollections' in seed and 'DOWNLOADED_COLLECTION_ID' in seed,
    'downloaded_collection_repository': 'downloadedOnce()' in repository,
    'downloaded_status_ui': 'listo para jugar sin volver a descargar' in adapter,
    'downloaded_dialog_ui': 'Descargar y jugar' in dialog and 'Biblioteca: descargado' in dialog,
    'computed_collection_not_manual': 'it.id != CatalogPagingSql.DOWNLOADED_COLLECTION_ID' in dialog,
    'settings_persistent_library': 'Biblioteca ROMs persistente' in settings_layout and 'Elegir carpeta ROMs de RetroArch' in settings_layout,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('FIX4_CONTRACTS_FAILED ' + ','.join(failed))
print('FIX4_CONTRACTS_OK')
for name in checks:
    print(f'{name}=1')
print(f'collections={len(collections)}')

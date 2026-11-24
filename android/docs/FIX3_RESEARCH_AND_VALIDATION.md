# Research and validation — Beta 3 Fix 3

## Primary sources

1. ROMgi official repository: https://github.com/caprado/romgi
   - Documents HTTP and selective per-file torrent transports.
   - Documents persistent Internet Archive login through an embedded browser.
   - Documents that protected downloads become automatic after the session is exchanged for API credentials.
2. ROMgi official Internet Archive authentication implementation:
   https://raw.githubusercontent.com/caprado/romgi/main/lib/services/internet_archive_auth_manager.dart
   - Obtains access/secret keys from `/account/s3.php` after WebView login.
   - Uses cookies for `archive.org/download/` and `LOW access:secret` as fallback.
   - Probes `s3.us.archive.org/?check_auth=1` to validate credentials.
3. Android CookieManager:
   https://developer.android.com/reference/android/webkit/CookieManager
4. Android Keystore:
   https://developer.android.com/privacy-and-security/keystore

## Decision separated from sourced facts

ROMgi supports both HTTP and torrents; its documentation does not prescribe that every client must
rank one above the other. Oz RetroRom's decision to rank authenticated HTTPS first is an engineering
choice based on the observed five-minute DHT metadata delay, especially for large PlayStation files.
Torrent remains a selective fallback and is stopped after 45 seconds when metadata never arrives.

## Regional correctness

The previous implementation concatenated entry title, link name, filename, torrent path and URL,
then searched that combined text. A generic `World` marker could therefore override the concrete
`(France)` filename. Country markers such as France are now classified under their broad region
(Europe) before any generic entry label is considered. Fix 3 checks fields in this order:

1. filename;
2. torrent file path;
3. URL filename;
4. link label;
5. catalog entry title.

A requested region is a hard constraint whenever that region exists among usable sources. If it does
not exist, the fallback is shown explicitly and requires confirmation.

## Executed checks

- Static acceptance validator.
- Room 3→4 migration test.
- Fix 3 regression-contract test.
- XML parsing for all main resources.
- Standalone Kotlin compilation and execution of the region/source policy.
- Standalone Kotlin compilation of InternetArchiveAuthManager against API-shaped stubs.

Gradle build certification must still be run in Termux. The assistant environment could not fetch the
verified Gradle wrapper because DNS resolution for `raw.githubusercontent.com` failed.

# Oz RetroRom 0.6.0 Beta 3 — Download & Filters Fix 3

This correction keeps the public version unchanged (`versionCode 60003`, `versionName 0.6.0-beta.3`).

## Corrected regressions

- Internet Archive login inside Oz RetroRom, using an embedded browser and app-private AES/GCM storage backed by Android Keystore.
- Authenticated Internet Archive HTTPS links are ranked before selective torrents.
- Torrent metadata discovery is stopped after 45 seconds instead of waiting several minutes.
- The concrete downloadable filename has regional authority over generic catalog labels.
- A `Resident Evil (France).zip` source is classified as Europe and can no longer be displayed or treated as World merely because its entry is labelled World.
- Exact same-region matches start without an unnecessary confirmation dialog.
- Region fallbacks, fuzzy matches and genuine ambiguity still require approval.
- The collection ambiguity dialog now renders the actual radio-button choices on MIUI and enables Download only after selection.
- The visible **Compartir diagnóstico** action was removed from the session screen. Internal censored diagnostics remain available to the error logger.
- Global Catalog now uses the same modern filter sheet and compact chips as the main library. SQLite/Room applies the filters before pagination.

## Termux verification

```bash
python scripts/validate_project.py
python scripts/test_room_migration_3_4.py
python scripts/test_fix3_contracts.py
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=1 --stacktrace
./gradlew :app:assembleDebug --no-daemon --max-workers=1 --stacktrace
```

The Python checks are not a substitute for the two Gradle commands.

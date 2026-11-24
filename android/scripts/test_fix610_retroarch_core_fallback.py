from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
retro = (ROOT / 'app/src/main/java/cl/gakuto/kizuna/launch/RetroArchManager.kt').read_text()
session = (ROOT / 'app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt').read_text()

checks = {
    'explicit receiver constant': 'INSTALLED_CORES_RECEIVER = "com.retroarch.browser.receiver.InstalledCoresReceiver"' in retro,
    'receiver resolved with PackageManager': 'queryBroadcastReceivers' in retro,
    'query targets resolved receiver': 'component = receiverComponent' in retro,
    'query timeout increased': 'timeoutMs: Long = 4_000L' in retro,
    'ordered result extras accepted': 'getResultExtras(false)' in retro,
    'ordered result data accepted': 'extractCoreNames(resultData)' in retro,
    'unsupported query does not imply missing core': 'does NOT prove that the requested core is absent' in retro,
    'official dataDir core path remains dynamic': 'File(targetInfo.dataDir, "cores/$coreName")' in retro,
    'no hardcoded private RA path': '"/data/user/' not in retro and '"/data/data/' not in retro,
    'PS1 PCSX fallback to SwanStation': '"pcsx_rearmed_libretro_android.so" -> add("swanstation_libretro_android.so")' in retro,
    'PS1 SwanStation fallback to PCSX': '"swanstation_libretro_android.so" -> add("pcsx_rearmed_libretro_android.so")' in retro,
    'session has query fallback': 'RETROARCH_CORE_QUERY_FALLBACK' in session,
    'session prefers previously confirmed candidate': 'preferences.isRetroArchCoreConfirmed(packageName, it)' in session,
    'session checks exported launch activity': 'retroArchManager.directLaunchActivityAvailable(packageName)' in session,
    'fallback uses dynamic private core path': 'retroArchManager.directCorePath(packageName, fallbackCore)' in session,
    'fallback still validates later stages': 'validateRetroStagesAfterCore(current, retroEntry, fallbackCore)' in session,
    'old hard stop removed': 'Oz no abrirá RetroArch sin confirmar el core real.' not in session,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    for name in failed:
        print('FAIL', name)
    raise SystemExit(1)
print(f'FIX610_RETROARCH_CORE_FALLBACK_OK checks={len(checks)}')

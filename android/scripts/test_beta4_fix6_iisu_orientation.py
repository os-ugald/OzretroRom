from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
session = (ROOT / "app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt").read_text(encoding="utf-8")
store = (ROOT / "app/src/main/java/cl/gakuto/kizuna/launch/RomDownloadTaskStore.kt").read_text(encoding="utf-8")
scheduler = (ROOT / "app/src/main/java/cl/gakuto/kizuna/launch/RomDownloadScheduler.kt").read_text(encoding="utf-8")
notification = (ROOT / "app/src/main/java/cl/gakuto/kizuna/launch/RomDownloadNotification.kt").read_text(encoding="utf-8")
build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

checks = {
    "SessionActivity no está hard-locked portrait en manifest": 'android:screenOrientation="portrait"' not in manifest,
    "external launch aplica orientación": "applySessionOrientation(returnToExternalLauncher)" in session,
    "iiSU usa sensorLandscape": "SCREEN_ORIENTATION_SENSOR_LANDSCAPE" in session,
    "Oz interno conserva portrait": "SCREEN_ORIENTATION_PORTRAIT" in session,
    "extra de origen externo": "EXTRA_EXTERNAL_LAUNCHER" in session,
    "task snapshot conserva origen": "val externalLauncher: Boolean" in store,
    "snapshot decode backward-compatible": 'json.optBoolean("externalLauncher", false)' in store,
    "scheduler propaga origen": "externalLauncher = externalLauncher" in scheduler,
    "notificación reabre con origen": "snapshot.externalLauncher" in notification,
    "versionCode 60010": "versionCode = 60010" in build,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("BETA4_FIX6_IISU_ORIENTATION_FAIL: " + "; ".join(failed))
print(f"BETA4_FIX6_IISU_ORIENTATION_OK checks={len(checks)}")

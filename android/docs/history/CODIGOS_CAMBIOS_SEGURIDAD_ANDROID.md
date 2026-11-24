# Códigos aplicados para la APK segura

Los cambios completos están dentro del proyecto y en el parche Rebuild 7 → Rebuild 8. Los archivos principales son:

1. `app/build.gradle.kts`: versión, ABI ARM64, R8, shrinkResources y política de cores.
2. `app/proguard-rules.pro`: JNI, libtorrent4j, Room y WorkManager.
3. `app/src/main/AndroidManifest.xml`: backup, red, exposición y iiSU.
4. `app/src/main/res/xml/network_security_config.xml`: bloqueo de cleartext.
5. `SessionLaunchValidator.kt`: validación de entradas externas.
6. `SessionActivity.kt`: rechazo seguro, límites JSON/ZIP y transportes HTTPS/magnet.
7. `TrustedCoreCatalog.kt`: allow-list SHA-256 empaquetada.
8. `RetroArchManager.kt`: verificación del ZIP y `.so` antes de instalar.
9. `DiagnosticLogger.kt`: redacción de información sensible.
10. `scripts/update_trusted_cores.py`: generación de hashes.
11. `03_CREAR_KEYSTORE_TERMUX.sh`: creación privada del keystore.
12. `04_COMPILAR_RELEASE_SEGURO_TERMUX.sh`: build, zipalign, firma, verificación y checksum.

La diferencia exacta, línea por línea, está en el archivo `.patch` entregado junto al ZIP.

# Oz RetroRom 0.6.0 Beta 4 — informe de validación

## Identidad

- `applicationId = cl.gakuto.kizuna`
- `versionCode = 60004`
- `versionName = 0.6.0-beta.4`
- `compileSdk = 36`
- `targetSdk = 36`
- Room permanece en schema 6.

## Cambio funcional validado

Beta 4 establece Oz RetroRom como única fuente de verdad de ROMs:

- Biblioteca SAF + Room conserva los juegos locales.
- Biblioteca y almacenamiento ya no muestra una "Biblioteca virtual iiSU".
- iiSU es opcional dentro de Emulación e integraciones.
- Activar iiSU no mueve ni duplica ROMs.
- Sincronizar biblioteca Oz exporta únicamente juegos con `contentUri` local.
- Los destinos de puente global/por sistema y assets quedan bajo Opciones avanzadas.
- Sincronizar una colección con iiSU filtra miembros no locales.

## Resultados

```text
VALIDACIÓN ESTÁTICA CORRECTA — Oz RetroRom 0.6.0 Beta 4 (1002 comprobaciones)
Sistemas: 44 · juegos curados: 192 · colecciones: 18
BETA4_IISU_ARCHITECTURE_OK checks=17
ROOM_MIGRATION_3_4_OK default=NULL data_preserved=1
ROOM_MIGRATION_5_6_OK default=NONE persisted=DOWNLOADED data_preserved=1
FIX3_CONTRACTS_OK
FIX4_CONTRACTS_OK
FIX66_COLLECTION_ARTWORK_OK
FIX68_BACKGROUND_DOWNLOAD_OK checks=29
FIX69_PERSISTENT_LIBRARY_LAUNCH_OK checks=10
FIX610_RETROARCH_CORE_FALLBACK_OK checks=17
FIX611_RETROARCH_CONFIG_BRIDGE_OK checks=10
FIX612_UX_CORE_OVERLAYS_OK checks=29
FIX614_SESSION_DRAWABLE_OK
FIX615_SETTINGS_INTERACTION_OK checks=24 beta4_compat=1
```

El parser Kotlin de los archivos modificados no reportó errores de sintaxis (`expecting`, `unexpected tokens`, `unclosed`); las referencias Android/AndroidX quedan necesariamente sin resolver cuando se invoca `kotlinc` fuera del classpath Android.

## Build real

Se intentó ejecutar el script de Termux desde el entorno de entrega. El host dispone de Java 21, pero no de `$PREFIX`, `ANDROID_HOME` ni `ANDROID_SDK_ROOT`. El script se detuvo antes de Gradle con:

```text
./02_COMPILAR_TERMUX.sh: line 19: PREFIX: unbound variable
BUILD_EXIT_CODE=1
```

Por lo tanto:

- no se declara `BUILD SUCCESSFUL`;
- no se entrega APK generado en este host;
- el build definitivo debe ejecutarse en Termux ARM64/Java 17/SDK 36 con `02_COMPILAR_TERMUX.sh`.

## Investigación iiSU

Se revisaron el repositorio y releases oficiales actuales de `iisu-network/iiSU`. iiSU se describe como frontend Android y actualmente dispone de colecciones nativas, scraping y gestión de medios. No se encontró un contrato público estable para que Oz cree/administre silenciosamente colecciones internas de iiSU, por lo que Beta 4 conserva un puente basado en descriptores y no inventa una API externa.

## QA de entrega

El parche incremental fue comprobado contra el ZIP exacto Fix6.15 y, una vez aplicado, volvió a pasar `validate_project.py` y `test_beta4_iisu_architecture.py`.

El ZIP completo Beta 4 fue extraído en una carpeta limpia y volvió a pasar la validación principal, el test Beta 4 y las migraciones Room 3→4 / 5→6. El paquete no contiene `local.properties`, `.gradle`, directorios `build`, keystores ni `gradle-wrapper.jar` generado localmente.

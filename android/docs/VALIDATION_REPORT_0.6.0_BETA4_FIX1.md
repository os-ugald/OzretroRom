# Oz RetroRom 0.6.0 Beta 4 Fix 1 — informe de validación

Fecha: 2026-08-07/08

## Identidad

- applicationId: `cl.gakuto.kizuna`
- versionCode: `60005`
- versionName: `0.6.0-beta.4`
- compileSdk/targetSdk: 36
- minSdk: 26
- ABI: arm64-v8a
- Java/Kotlin target: 17

## Objetivo de la corrección

Separar tres responsabilidades que en Beta 4 resultaban confusas:

1. **Descargar ROMs**: batch real de todas las ROM faltantes de una colección.
2. **Descargar artwork**: descargar únicamente imágenes oficiales para la colección.
3. **Publicar en iiSU**: generar accesos virtuales, incluidos juegos todavía no descargados, para que iiSU use Oz como proveedor/launcher bajo demanda.

## Descarga por lote

Nuevos componentes:

- `CollectionBatchTaskStore`
- `CollectionDownloadScheduler`
- `CollectionDownloadJobService`
- `CollectionDownloadForegroundService`
- `CollectionDownloadNotification`

`CollectionDownloadManager` reutiliza `BackgroundRomDownloadEngine` por ROM, por lo que hereda reanudación HTTP Range, autenticación IA, persistencia SAF, staging recuperable y validación del juego. El lote continúa al siguiente juego ante errores individuales.

Android 14+ usa User-Initiated JobScheduler; Android 8–13 usa foreground service `dataSync`.

## Artwork

`CollectionArtworkSyncManager` ya no se limita a validar aliases remotos. Ahora:

- prepara solo los índices Libretro necesarios;
- busca variantes DAT del mismo juego;
- intenta Boxart/Title/Snap oficiales;
- descarga una imagen real a `filesDir/artwork-sync/<system>/<game>.png`;
- limita cada imagen a 12 MiB;
- exige HTTPS después de redirecciones;
- rechaza respuestas no-imagen;
- persiste el URI local mediante `AppPreferences`;
- no invoca ROMgi ni descarga ROMs.

El artwork personalizado por el usuario mantiene prioridad.

## iiSU

Se restaura el puente virtual bajo demanda:

- `IisuExporter` publica todos los juegos elegidos, no solo `contentUri` locales;
- los descriptores usan `contentMode = oz-virtual-on-demand`;
- una ROM ausente se resuelve/descarga cuando iiSU la abre en Oz;
- el comando nuevo fija explícitamente `-a cl.gakuto.kizuna.action.PLAY`;
- por compatibilidad, `ACTION_MAIN` se acepta solo para un intent explícito a `SessionActivity` con extra `ROM`; el archivo/URI sigue validándose normalmente.

Comando:

```text
cl.gakuto.kizuna/cl.gakuto.kizuna.launch.SessionActivity -a cl.gakuto.kizuna.action.PLAY -e ROM "%ROM%" --activity-clear-top
```

## Investigación comparativa

Se revisaron fuentes oficiales de iiSU, ES-DE, Daijishō, Beacon, RomM y Argosy Launcher.

Conclusión: sí conviene continuar Oz, pero su mejor posición no es competir visualmente con iiSU. El diferencial sostenible es **gestión/provisión serverless de ROMs + descargas resilientes + biblioteca SAF + preparación de emulación + puente a frontends**. Argosy es el competidor funcional más cercano, aunque su descarga/sincronización completa se apoya en un servidor RomM. iiSU ya tiene integración RomM experimental, por lo que “tener descargas” por sí solo no es una ventaja suficiente; no exigir un servidor propio sí lo es.

Ver `docs/BETA4_FIX1_BATCH_IISU_RESEARCH.md`.

## Pruebas realizadas

### Validador principal

```text
VALIDACIÓN ESTÁTICA CORRECTA — Oz RetroRom 0.6.0 Beta 4 (1028 comprobaciones)
Sistemas: 44 · juegos curados: 192 · colecciones: 18
```

### Contratos/regresión

```text
ROOM_MIGRATION_3_4_OK
ROOM_MIGRATION_5_6_OK
FIX3_CONTRACTS_OK
FIX4_CONTRACTS_OK
FIX66_COLLECTION_ARTWORK_OK
FIX68_BACKGROUND_DOWNLOAD_OK
FIX69_PERSISTENT_LIBRARY_LAUNCH_OK
FIX610_RETROARCH_CORE_FALLBACK_OK
FIX611_RETROARCH_CONFIG_BRIDGE_OK
FIX612_UX_CORE_OVERLAYS_OK
FIX613_FINAL_POLISH_OK
FIX614_SESSION_DRAWABLE_OK
FIX615_SETTINGS_INTERACTION_OK
BETA4_IISU_ARCHITECTURE_OK
BETA4_FIX1_BATCH_IISU_OK checks=35
```

### Parche

El parche incremental fue aplicado sobre el ZIP exacto `Oz-RetroRom-Android-0.6.0-Beta4-Source.zip`; el árbol resultante volvió a superar `validate_project.py` y `test_beta4_fix1_batch_iisu.py`.

## Build real

Se intentó ejecutar el script Termux en el entorno de entrega. El host dispone de Java 21 pero no es Termux y no define `$PREFIX`, `ANDROID_HOME` ni `ANDROID_SDK_ROOT`.

Resultado:

```text
./02_COMPILAR_TERMUX.sh: line 19: PREFIX: unbound variable
BUILD_EXIT_CODE=1
```

Por tanto **no se declara BUILD SUCCESSFUL y no se genera APK** desde este entorno. La compilación real debe repetirse en el dispositivo Termux ARM64/Java17/SDK36 del usuario.

## Persistencia y seguridad

- Room permanece en schema 6.
- No se añadió `fallbackToDestructiveMigration`.
- No se borran ROMs existentes al publicar iiSU.
- Los descriptores iiSU no contienen ROMs.
- El cache artwork es privado de Oz y regenerable.
- Las descargas ROM continúan almacenándose finalmente en la biblioteca SAF configurada por el usuario.

# Oz RetroRom 0.6.0 Beta 4 — Fix 5 Validation

## Alcance

Base: Beta 4 Fix 4 + Kotlin Build Hotfix.

Fix 5 corrige dos problemas observados en dispositivo:

1. ROMgi no resolvía títulos curados que no coinciden literalmente con el nombre usado por el catálogo. El caso reproducible es `Castlevania IV` frente a `Super Castlevania IV`.
2. Una sesión iniciada desde iiSU volvía a la tarea de Oz después de cerrar RetroArch. Ahora el bridge recuerda su origen y elimina la navegación transitoria de Oz para devolver el foco al launcher anterior.

## Cambios técnicos

### ROMgi

- Nuevo `RomgiTitleAliasPolicy`.
- `RomgiTitleMatcher.match()` acepta aliases opcionales, manteniendo las coincidencias exactas con mayor prioridad.
- La consulta SQLite de ROMgi incorpora aliases antes del scoring; esto también cubre nombres regionales históricos que no comparten los mismos tokens.
- Aliases iniciales conservadores:
  - `Castlevania IV` → `Super Castlevania IV`.
  - `Final Fantasy IV` → `Final Fantasy II`.
  - `Final Fantasy VI` → `Final Fantasy III`.
  - `The Legend of Zelda: A Link to the Past` → `Legend of Zelda, The - A Link to the Past`.
- Para SNES se añade un alias secundario acotado con prefijo `Super` cuando el título visible no lo tiene.

### iiSU / retorno de tarea

- `SessionActivity` detecta lanzamiento externo por el contrato iiSU `ROM` y lanzamiento interno por `oz.gameId`.
- Tras cerrar el emulador:
  - inicio desde Oz → `finish()`;
  - inicio desde iiSU → `finishAffinity()` para retirar la tarea transitoria de Oz y permitir que Android reanude el launcher que estaba debajo.
- No se hardcodea ni se vuelve a lanzar el package/activity de iiSU.
- Cancelar una sesión iniciada desde iiSU usa la misma política de retorno.

### Compatibilidad

- `applicationId`: `cl.gakuto.kizuna`
- `versionName`: `0.6.0-beta.4`
- `versionCode`: `60009`
- Room: esquema 6, sin nueva migración.
- Sin borrado de ROMs, BIOS o preferencias.
- Sin cambio de SAF ni del contrato de RetroArch.

## Investigación primaria

- Android Developers — Tasks and back stack: una tarea mantiene actividades en pila y al terminar la superior se reanuda la anterior.
- Android Developers — `Activity.finishAffinity()`: finaliza la actividad y las actividades inmediatamente inferiores de la misma affinity; está pensado para flujos donde una app se abrió dentro de otra tarea.
- iiSU oficial — es un frontend Android y sus releases recientes incluyen correcciones de launch focus/direct launch.
- ROMgi oficial — catálogo SQLite, filtros de plataforma/región, descargas background y nombres procedentes de múltiples fuentes; Oz mantiene su título curado y usa aliases solo para resolución.
- Libretro database — asigna nombres uniformes de juegos a partir de bases verificadas; los nombres de catálogo pueden diferir de títulos mostrados por un frontend.

## Pruebas ejecutadas

```text
VALIDACIÓN ESTÁTICA CORRECTA — Oz RetroRom 0.6.0 Beta 4 (1087 comprobaciones)
Sistemas: 44 · juegos curados: 192 · colecciones: 18

BETA4_IISU_ARCHITECTURE_OK checks=18
BETA4_FIX1_BATCH_IISU_OK checks=35
BETA4_FIX2_IISU_LAUNCH_GUARD_OK checks=11
BETA4_FIX3_WORLD_ARCHIVE_OK checks=12
BETA4_FIX4_IISU_BRIDGE_REPAIR_OK checks=28
BETA4_FIX5_ROMGI_RETURN_IISU_OK checks=17
FIX3_CONTRACTS_OK
FIX4_CONTRACTS_OK
FIX610_RETROARCH_CORE_FALLBACK_OK checks=17
FIX611_RETROARCH_CONFIG_BRIDGE_OK checks=10
FIX612_UX_CORE_OVERLAYS_OK checks=29
FIX613_FINAL_POLISH_OK checks=46
FIX614_SESSION_DRAWABLE_OK
FIX615_SETTINGS_INTERACTION_OK checks=25 beta4_compat=1
FIX66_COLLECTION_ARTWORK_OK
FIX68_BACKGROUND_DOWNLOAD_OK checks=29
FIX69_PERSISTENT_LIBRARY_LAUNCH_OK checks=10
ROOM_MIGRATION_3_4_OK default=NULL data_preserved=1
ROOM_MIGRATION_5_6_OK default=NONE persisted=DOWNLOADED data_preserved=1
```

Pure Kotlin compile para el matcher/policy: `PURE_KOTLIN_COMPILE_OK`.

Prueba ejecutable del alias:

```text
ROMGI_ALIAS_RUNTIME_OK aliases=[Super Castlevania IV] strategy=ALIAS region=USA
```

## Build real

No se declara `BUILD SUCCESSFUL` desde el entorno de entrega. Este contenedor usa Java 21, no tiene `$PREFIX`, `ANDROID_HOME` ni `ANDROID_SDK_ROOT`, y el script Termux posee un shebang específico de Termux que aquí no existe. El build real debe ejecutarse en el dispositivo con `02_COMPILAR_TERMUX.sh`.

## Empaquetado y QA de entrega

- El parche incremental se comprobó contra una extracción limpia del ZIP exacto de Beta 4 Fix 4 después de aplicar el Kotlin Build Hotfix: `PATCH_APPLY_CHECK_OK`.
- El ZIP de Fix 5 se extrajo desde cero y volvió a superar `scripts/validate_project.py` y `scripts/test_beta4_fix5_romgi_return_iisu.py`: `ZIP_EXTRACT_VALIDATION_OK`.
- Higiene del ZIP: sin `.git`, `.gradle`, `build/`, `local.properties`, `gradle-wrapper.jar`, `.jks` ni `.keystore`: `ZIP_HYGIENE_OK`.
- El Kotlin Build Hotfix que corrigió `SessionActivity.kt` está incorporado en Fix 5; no hace falta aplicarlo de nuevo sobre el ZIP completo de Fix 5.

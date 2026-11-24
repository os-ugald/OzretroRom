# Oz RetroRom 0.6.0 Beta 4 Fix 2 — iiSU Launch Guard

## Problema observado

Al abrir un juego de Super Nintendo desde la consola virtual de iiSU, Android registró un cierre nativo dentro de `snes9x_libretro_android.so`, con `core_load_game()` en la pila de RetroArch.

## Diagnóstico

El core Snes9x acepta contenido SNES real (`.smc`, `.sfc`, `.swc`, `.fig`, `.bs`, `.st`). Los accesos virtuales de Oz para iiSU, sin embargo, son pequeños documentos JSON que deliberadamente usan la extensión de la plataforma (por ejemplo `.sfc`) para que iiSU pueda clasificarlos. Esos archivos nunca deben llegar al core.

Beta 4 Fix 1 ya intenta leer el descriptor antes de lanzar, pero existía un caso de compatibilidad heredada: una iteración anterior podía haber guardado el descriptor iiSU como `AuthorizedSource`. En el arranque posterior, ese origen recordado tenía prioridad sobre la ROM persistente de Oz. Si se trataba como archivo local, RetroArch podía recibir bytes JSON con extensión `.sfc`, lo que explica un fallo dentro de la carga nativa del core.

La corrección no presupone que todo crash de Snes9x tenga esta causa; añade una barrera que hace imposible que un descriptor virtual de Oz llegue al core. Si una ROM real continúa cerrando Snes9x después de Fix 2, deberá investigarse el archivo ROM/core concreto.

## Cambios

1. La ROM persistente (`PersistentRomLibrary`) ahora tiene prioridad sobre una fuente externa recordada.
2. Si una fuente recordada contiene un descriptor `Oz RetroRom / oz-virtual-on-demand`, se elimina automáticamente de preferencias.
3. Un descriptor recibido desde iiSU nunca se guarda como fuente autorizada del juego.
4. `RomPreparedFileValidator` detecta descriptores virtuales en todos los sistemas, no solo ZIP de FBNeo.
5. Antes de entrar a RetroArch, la copia final se vuelve a validar. Esto ocurre antes de `core_load_game()`.
6. Se conserva compatibilidad con el comando iiSU de Beta 4 Fix 1.

## Fuentes primarias consultadas

- Libretro Docs — Snes9x: https://docs.libretro.com/library/snes9x/
- RetroArch Android issue #17433 — lanzamiento externo y ruta de core: https://github.com/libretro/RetroArch/issues/17433
- iiSU oficial: https://github.com/iisu-network/iiSU
- iiSU releases: https://github.com/iisu-network/iiSU/releases

## Compatibilidad y persistencia

- applicationId: `cl.gakuto.kizuna`
- versionName: `0.6.0-beta.4`
- versionCode: `60006`
- Room: sin cambio de schema
- SAF/ROMs: sin borrado ni migración
- Internet Archive/ROMgi: sin cambios
- RetroArch CONFIGFILE/core bridge: sin cambios

## Validación

- `validate_project.py`: 1038 comprobaciones
- `BETA4_FIX2_IISU_LAUNCH_GUARD_OK`: 11 comprobaciones
- contratos Beta 4/Fix 1, Room, background downloads, biblioteca persistente, RetroArch core/config, artwork: OK

## Build real

El entorno de entrega no es Termux y no dispone de `$PREFIX`, SDK Android ni Java 17. `02_COMPILAR_TERMUX.sh` se detiene antes de Gradle en este host. El build real debe ejecutarse en Termux ARM64.

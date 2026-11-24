# Oz Ready Core 01 — JDK reproducible y política regional ROMgi

Fecha: 2026-08-11

## Alcance

Esta iteración corrige dos bloqueos previos al primer APK de `feature/oz-ready-core`:

1. `gradlew.bat` ahora respeta `JAVA_HOME` y propaga el código de salida de Gradle.
2. ROMgi usa USA/NTSC-U como preferencia inicial y clasifica la región del archivo real antes de
   elegir una descarga.

No modifica las fichas curadas `World`, las colecciones, iiSU, artwork, cores ni el diseño Surface
03B. La identidad visual del catálogo permanece separada de la variante binaria descargada.

## Política regional

| Perfil | Orden inicial con preferencia USA | Confirmación obligatoria |
|---|---|---|
| Consola de TV | USA → Japón → World → Asia → Europa → Other | World no verificado, Asia, Europa/PAL u Other |
| Portátil | USA → Japón → World → Europa → Asia → Other | Europa, Asia u Other |
| Arcade FBNeo | romset exacto | No se sustituye el set por región |

Una región concreta elegida en el catálogo conserva prioridad para no cambiar silenciosamente de
edición. Los valores históricos `auto` se interpretan como `usa` sin borrar preferencias ni ROMs.

## Correcciones estructurales

- `RomgiSourcePolicy` mantiene la precedencia archivo → ruta torrent → URL → nombre del link →
  título de entrada.
- Dentro de un mismo nombre, una región concreta tiene prioridad sobre la etiqueta amplia `World`.
- `RomgiCatalog` ordena con `sourceRegionRank`, calculado desde el archivo descargable, en lugar de
  depender de la región general de la entrada.
- Europa/PAL no puede descargarse silenciosamente cuando la política automática es USA/NTSC.
- Un `World` de consola de TV exige aprobación mientras Oz no disponga de metadata fiable de 60 Hz.
- La pantalla muestra región seleccionada y preferencia configurada antes de aprobar un fallback.

## Fuentes primarias verificadas

- ROMgi oficial: https://github.com/caprado/romgi
- Base oficial Libretro: https://github.com/libretro/libretro-database
- Compatibilidad Gradle 8.11.1: https://docs.gradle.org/8.11.1/userguide/compatibility.html
- Requisitos AGP 8.10: https://developer.android.com/build/releases/agp-8-10-0-release-notes

## Gates ejecutados en la sesión de preparación

- `scripts/test_windows_wrapper_jdk.py`: PASS
- `scripts/test_beta4_fix3_world_archive.py`: PASS
- `scripts/test_fix3_contracts.py`: PASS
- `scripts/test_fix615_settings_interaction.py`: PASS
- `scripts/validate_project.py`: PASS, 1.155 comprobaciones

La ejecución de `:app:testDebugUnitTest` no pudo comenzar en el contenedor porque la distribución
Gradle no estaba almacenada y la red del runtime no permite acceder a `services.gradle.org`. Debe
ejecutarse en Android Studio con JDK 17 antes del commit funcional.

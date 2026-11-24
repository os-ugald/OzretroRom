# Fix6.12 — Fuentes y decisiones técnicas

## Cores

Fuentes primarias utilizadas:

- Libretro Docs — Core List.
- Libretro Docs — Memory Monitoring (comparativa PlayStation, con Beetle PSX HW como preferred para ese caso y descripción de SwanStation/PCSX ReARMed).
- Libretro SwanStation — repositorio oficial.
- Libretro Docs — PCSX ReARMed.

Decisión de Oz para Android ARM64: SwanStation sigue siendo la primera recomendación de PS1; PCSX ReARMed permanece como fallback compatible. Esto no pretende declarar un ganador universal para todo hardware o juego.

## Overlays

Fuentes primarias utilizadas:

- Libretro Docs — Input Overlays.
- Repositorio oficial `libretro/common-overlays`.
- Repositorio oficial `libretro/overlay-borders`.
- Libretro Docs — XMB Menu Map (`Update Overlays`).
- Libretro Buildbot — `assets/frontend/overlays.zip`.

Decisión:

- Sí: Oz puede descargar/actualizar el bundle oficial en una carpeta SAF elegida por el usuario.
- No: Oz no autoasigna por core modificando configuración privada ni inventando extras de lanzamiento.
- Una vez que RetroArch apunta `Input Overlays` a esa carpeta compartida, Oz puede mantener los archivos oficiales actualizados.

## Seguridad del extractor

- origen fijo HTTPS de Buildbot Libretro;
- se rechaza downgrade de protocolo tras redirects;
- protección contra `..`/zip-slip;
- máximo 20.000 entradas;
- máximo 768 MiB desempaquetados;
- ZIP temporal eliminado al terminar/fallar.

# Oz RetroRom 0.6.0 Beta 4 Fix 1 — batch, artwork, iiSU y posicionamiento

## Problemas observados

1. `Descargar colección` y `Sincronizar artwork` no comunicaban ni ejecutaban responsabilidades suficientemente distintas.
2. La publicación iiSU de Beta 4 se limitaba a juegos con `contentUri` local y el lanzamiento de un descriptor heredado podía llegar como `android.intent.action.MAIN`, que `SessionLaunchValidator` rechazaba.
3. El usuario prefiere la experiencia de las primeras iteraciones: iiSU puede mostrar juegos virtuales y Oz toma el control al lanzarlos.

## Implementación Fix 1

### Descargar ROMs por colección

`Descargar ROMs` es ahora un batch real, serial y persistente. `CollectionDownloadManager` reutiliza `BackgroundRomDownloadEngine` para cada ROM faltante, por lo que conserva:

- resolución ROMgi;
- autenticación Internet Archive;
- HTTP Range/reanudación;
- staging por juego;
- validación;
- persistencia SAF;
- reutilización de ROMs ya locales.

Android 14+ usa User-Initiated Data Transfer/JobScheduler; Android 8–13 usa un foreground service `dataSync`. El estado del lote y la notificación son independientes de la Activity.

### Descargar artwork

`Descargar artwork` no invoca ROMgi ni el motor de ROMs. Prepara únicamente los índices Libretro necesarios, resuelve la identidad DAT correcta y guarda una imagen oficial por juego en `filesDir/artwork-sync`. El cache local queda por debajo del artwork personalizado del usuario y por encima del fallback remoto en modo AUTO.

### iiSU virtual on-demand

Los descriptores vuelven a representar juegos locales o ausentes. `contentMode` es `oz-virtual-on-demand`.

Comando actualizado:

```text
cl.gakuto.kizuna/cl.gakuto.kizuna.launch.SessionActivity -a cl.gakuto.kizuna.action.PLAY -e ROM "%ROM%" --activity-clear-top
```

Por compatibilidad con configuraciones anteriores, `ACTION_MAIN` se acepta solo cuando el intent apunta explícitamente a `SessionActivity` y contiene el extra `ROM`. La referencia aún debe superar toda la validación de ruta/URI/extensión.

## Investigación de mercado/proyectos comparables — 2026-08-07

### iiSU

Fuente oficial: https://github.com/iisu-network/iiSU

- Frontend Android visuals-first, todavía Alpha.
- No incluye emuladores ni juegos.
- Tiene colecciones, scraping/media y una experiencia visual claramente más ambiciosa que Oz.
- Onboarding actual permite enlazar un servidor RomM como función experimental.
- Releases recientes incluyen recuperación SAF para descargas RomM y sincronización de colecciones RomM.

### ES-DE

Fuente oficial: https://gitlab.com/es-de/emulationstation-de

- Frontend multiplataforma de alta calidad para navegar/lanzar colecciones.
- Android está soportado oficialmente.
- Gran cobertura de emuladores y personalización.
- Su propuesta principal no es adquisición/descarga directa de ROMs.

### Daijishō

Fuente oficial: https://github.com/TapiocaFox/Daijishou

- Launcher Android para administrar bibliotecas retro.
- Prioriza experiencia integrada, estética y extensibilidad.
- No incluye emuladores.
- El proyecto de la app es actualmente cerrado; el repositorio público sirve como página/assets.

### Beacon

Fuente oficial/distribución: Google Play `com.radikal.gamelauncher`.

- Launcher Android centrado en colección, scraping de metadata/artwork y lanzamiento.
- No contiene juegos ni emuladores.

### RomM + Argosy Launcher

Fuentes oficiales:
- https://github.com/rommapp/romm
- https://github.com/rommapp/argosy-launcher
- https://docs.romm.app/latest/ecosystem/first-party-apps/

Argosy es el competidor funcional más cercano encontrado:

- Android, Kotlin y gamepad-first;
- biblioteca RomM;
- descargas de ROM y BIOS bajo demanda;
- cola de múltiples descargas;
- colecciones;
- selección de cores RetroArch y emuladores externos;
- cache local de imágenes/offline.

La diferencia estructural es que sus funciones de sincronización/descarga completas dependen de un servidor RomM autoalojado. Puede usarse en modo local, pero la adquisición/sync RomM requiere ese backend.

## Recomendación estratégica

**Sí conviene continuar Oz RetroRom, pero no como competidor visual directo de iiSU.**

El espacio de frontends visuales Android ya está bien cubierto por iiSU, ES-DE, Daijishō y Beacon. Además, Argosy demuestra que la combinación launcher + gestor de descargas tiene demanda real.

La propuesta diferencial más fuerte para Oz es:

> **gestor/provisionador serverless de biblioteca + compañero de emulación, con frontend propio funcional y puente opcional a iiSU.**

Prioridades sugeridas:

1. Descarga directa y resiliente desde fuentes configuradas por Oz, sin exigir un servidor RomM.
2. Cola/batch/background/reanudación y recuperación de errores.
3. Biblioteca SAF persistente.
4. Artwork offline y sincronización selectiva.
5. Preparación de RetroArch: core, BIOS aportadas por el usuario, overlays y preflight.
6. Integración iiSU extremadamente simple: publicar colección → refrescar iiSU → jugar; Oz descarga bajo demanda.
7. Mantener el frontend de Oz como UI de administración y fallback, no invertir la mayor parte del desarrollo en intentar superar la presentación visual de iiSU.

Un adaptador RomM podría añadirse en el futuro como fuente opcional, pero no es necesario para conservar el diferencial serverless de Oz.

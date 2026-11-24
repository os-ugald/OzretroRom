# Oz RetroRom 0.6.0 Beta 3 — Fix6.13 Final Polish

## Alcance

- Emulación e integraciones migra a una pantalla Compose propia y deja de mezclar ROMgi, región, rendimiento del catálogo o Internet Archive.
- Catálogo y descargas pasa a una pantalla independiente con ROMgi, Wi‑Fi, torrent, región y paginación.
- Colecciones incorporan **Sincronizar artwork** por colección.
- La sincronización de artwork prepara únicamente los índices Libretro necesarios que aún no estén READY, resuelve el título/repository exactos de libretro-thumbnails, valida que exista una imagen oficial y guarda un alias visual por ID estable.
- Ninguna membresía, ROM, core, source o ID de juego se reemplaza durante la sincronización visual.

## Emulación e integraciones

La sección contiene únicamente controles que Oz puede gestionar de forma segura:

1. RetroArch: variante detectada, abrir/instalar y política de instalación de cores.
2. Cores por sistema: recomendación mantenida por Oz, rationale, detección y preparación del core.
3. BIOS/firmware: carpeta System/BIOS y respaldo FBNeo.
4. Overlays oficiales: carpeta compartida y actualización del bundle oficial Libretro.
5. Motores por sistema: selector de RetroArch/emulador externo/automático.
6. iiSU: carpeta virtual, overrides por sistema, assets y pack de integración.
7. Compatibilidad: carpeta de cores, permisos y limpieza de copias temporales.
8. Ajustes delegados a RetroArch: video/Vulkan, audio, latencia, shaders, remaps, saves/states, core options y overrides siguen en retroarch.cfg/Quick Menu.

## Artwork por colección

La acción explícita evita refrescos masivos al abrir una colección. El flujo es:

1. Lee los juegos de la colección.
2. Para cada sistema, reutiliza su índice Libretro si está READY; solo indexa los sistemas faltantes.
3. Busca variantes del mismo `canonicalTitle` en el mismo sistema.
4. Prueba Boxart, Title y Snap del repositorio oficial de libretro-thumbnails.
5. Guarda solo `syncedArtworkTitle` + `syncedArtworkRepo` para el ID estable del juego.
6. `ArtworkResolver` mantiene prioridad para artwork personalizado/local y utiliza el alias sincronizado para las URLs oficiales.

No hay migración Room nueva ni fallback destructivo.

## Investigación primaria utilizada

- Libretro XMB Menu Map: categorías oficiales de Settings/Drivers, Online Updater y Configuration File.
  https://docs.libretro.com/guides/xmb-menu-map/
- Libretro Overrides: separación entre overrides, remaps, core options y shader presets por core/juego.
  https://docs.libretro.com/guides/overrides/
- Libretro Directory Configuration: System/BIOS, cores y directorios configurables.
  https://docs.libretro.com/guides/change-directories/
- Libretro Importing Content: el nombre de playlist/entrada debe coincidir con el repositorio de thumbnails para descargar artwork.
  https://docs.libretro.com/guides/import-content/
- Libretro Input Overlays: repositorios oficiales common-overlays/overlay-borders y formato CFG.
  https://docs.libretro.com/development/retroarch/input/overlay/

Conclusión de arquitectura: Oz gestiona el puente y la preparación; RetroArch sigue siendo la fuente de verdad para Vulkan/video, audio, latencia, shaders, remaps, saves/states, core options y overrides.

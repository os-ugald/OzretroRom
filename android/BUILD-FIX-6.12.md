# Oz RetroRom 0.6.0 Beta 3 — Fix6.12 UX, Core Priority y Overlays

## Objetivo

Esta iteración parte de Fix6.11, que ya lanza juegos con la configuración real de RetroArch. Se concentra en cuatro problemas de experiencia:

1. la pantalla de preparación seguía viéndose demasiado técnica;
2. el rail Inicio > Favoritos podía mostrar tarjetas sin carátula;
3. Configuración duplicaba opciones de Biblioteca ROMs dentro de Ajustes avanzados;
4. PS1 podía heredar `PCSX ReARMed` desde metadata histórica aunque el perfil mantenido por Oz recomienda SwanStation.

También se estudió la viabilidad de descargar overlays automáticamente antes de implementar nada.

## Pantalla de preparación

Se mantiene exactamente el mismo `RetroPreparationViewModel` y el mismo contrato de preflight, pero la presentación pasa a un panel `OZ // BOOT SEQUENCE` con:

- cinco segmentos de progreso;
- estado actual destacado;
- indicadores compactos Archivo / Core / Compatibilidad / BIOS / RetroArch;
- paleta oscura violeta/cian coherente con la identidad de Oz;
- fondo de sesión oscuro, portada ligeramente mayor y métricas adaptadas al tema.

No se eliminó ninguna comprobación de seguridad del lanzamiento.

## Favoritos y carátulas

`favoriteHome()` aplica ahora `artworkVariantFor(game)`, igual que las colecciones curadas. Así un favorito semilla puede utilizar la variante Libretro indexada del mismo juego para obtener el título DAT/repositorio de thumbnails correcto sin cambiar su ID ni su condición de favorito.

`OzGameCover` también recorre los candidatos de `ArtworkResolver`: si la primera imagen falla, prueba la siguiente en vez de dejar un rectángulo vacío.

## Configuración sin redundancias

Se creó `LibrarySettingsActivity` para las opciones que realmente pertenecen a la biblioteca:

- carpeta ROM principal;
- override de carpeta por sistema;
- modo/prioridad de carátulas;
- carpeta local de artwork.

La entrada avanzada se renombra **Emulación e integraciones** y, cuando se abre desde Compose, oculta el bloque duplicado de Biblioteca ROMs. Allí permanecen RetroArch, cores, BIOS, overlays, iiSU, ROMgi, torrents y demás integraciones.

Los callers históricos de `SettingsActivity` sin scope siguen viendo la pantalla completa para mantener compatibilidad con rutas de recuperación existentes.

## Cores: PlayStation

No existe una única tabla oficial universal de “mejor core para cada sistema”. Libretro mantiene Core List, documentación individual y tablas que comparan/preferencian cores para casos concretos.

Para PlayStation, la documentación oficial describe SwanStation como de precisión bastante alta y velocidad extremadamente alta, mientras que PCSX ReARMed prioriza velocidad y optimizaciones ARM con menor precisión. En Android ARM64 Oz conserva SwanStation como recomendación principal y PCSX ReARMed como alternativa compatible.

La precedencia del preflight cambia de:

`metadata del juego -> perfil del sistema`

a:

`perfil mantenido por Oz -> metadata histórica del juego`

La detección de cores instalados sigue pudiendo seleccionar un fallback compatible real.

## Overlays oficiales

La investigación confirmó que Libretro mantiene `common-overlays`, RetroArch ofrece `Update Overlays` en Online Updater y el Buildbot oficial publica `overlays.zip`.

Por ello se implementa un descargador seguro del bundle oficial:

`https://buildbot.libretro.com/assets/frontend/overlays.zip`

El usuario elige una carpeta compartida mediante SAF. Oz descarga por HTTPS, limita tamaño/número de entradas, rechaza paths ZIP inseguros y extrae el paquete allí.

Oz NO modifica el `retroarch.cfg` privado ni asigna automáticamente un overlay diferente a cada core porque no se encontró una API Android externa, estable y documentada para ese contrato. El usuario debe apuntar una vez `Settings > Directory > Input Overlays` de RetroArch a la misma carpeta compartida; después Oz puede mantener el bundle actualizado.

## Persistencia y compatibilidad

- Sin migración Room nueva.
- Sin `fallbackToDestructiveMigration`.
- Sin cambios en Internet Archive/ROMgi.
- Sin cambios en descarga background/reanudación.
- Sin cambios en SAF de ROMs.
- Sin cambios en iiSU.
- Sin cambios en el puente `CONFIGFILE` de Fix6.11.
- applicationId/versionCode/versionName permanecen intactos.

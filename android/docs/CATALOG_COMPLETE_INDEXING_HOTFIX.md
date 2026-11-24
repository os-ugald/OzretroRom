# Catálogo completo — reparación de indexación Compose

Fecha: 2026-08-13
Rama objetivo actual: `fix/catalog-background-preparation`
Base de esta iteración: `25dda74`

## Problema reproducido

Una instalación nueva mostraba los 192 juegos curados y los sistemas preparados indirectamente al
sincronizar artwork de una colección, pero la búsqueda global no alcanzaba los 44 sistemas.

La migración a Compose conservó el `PagingData` respaldado por Room, pero `CatalogScreen` dejó de
invocar la indexación que `GlobalCatalogActivity` ejecutaba al seleccionar una plataforma. Por eso
el problema podía quedar oculto en instalaciones antiguas con índices ya guardados.

## Contrato del hotfix

- `Preparar` recorre explícitamente los 44 sistemas.
- Los sistemas `READY` con datos válidos se omiten: continuar no repite trabajo terminado.
- Cancelar conserva juegos, colecciones, favoritos, ROMs y estados ya almacenados.
- Reindexar una fila preserva su URI local, origen de biblioteca, disponibilidad y último uso.
- `Reconstruir` no borra Room ni las filas del catálogo: marca los estados preparados como `STALE`
  y obliga a verificar nuevamente los 44 índices usando los mismos IDs estables.
- Antes de reconstruir se muestra una confirmación explícita; ROMs, favoritos, colecciones, login,
  sesiones y juegos ya guardados permanecen intactos.
- La pantalla y la notificación muestran el tiempo transcurrido; al terminar conservan la duración
  final para que el rendimiento pueda medirse en el dispositivo real.
- Un error de red afecta sólo al sistema actual y el proceso continúa; `Reintentar` trabaja sobre
  los sistemas pendientes.
- Seleccionar plataformas o familias en los filtros prepara automáticamente sólo esos sistemas.
- El contador Room reinicia el `Pager`, por lo que los juegos aparecen durante la preparación.
- FBNeo sólo se considera listo cuando cada entrada indexada conserva identidad de romset.
- No se descargan ROMs: esta operación prepara exclusivamente metadatos Libretro.

## Validación requerida

1. `python scripts/validate_project.py`.
2. `:app:testDebugUnitTest` y `:app:assembleDebug` con JDK 17.
3. Instalación nueva en un dispositivo secundario: debe comenzar en 192 juegos y mostrar `0/44`.
4. Pulsar `Preparar`, pausar y continuar sin perder lo ya indexado.
5. Confirmar que el catálogo supera los 192 juegos y que buscar un título ajeno a las colecciones
   devuelve resultados.
6. Confirmar que favoritos, descargas, colecciones, login IA y playlists M3U permanecen intactos.
7. Con un catálogo `44/44`, pulsar `Reconstruir`, aceptar la confirmación y verificar que el contador
   pasa a pendiente sin que desaparezcan los juegos ya disponibles.
8. Minimizar Oz durante varios minutos: la notificación y su cronómetro deben continuar, y al volver
   la pantalla debe recuperar el mismo sistema, etapa y tiempo.

## Rendimiento esperado

La primera preparación completa descarga y procesa índices remotos para 44 sistemas, por lo que
puede tardar varios minutos según la red, el almacenamiento y el tamaño de cada DAT. El proceso usa
parsing en streaming y lotes acotados de 500 filas para no cargar el catálogo completo en memoria.
Las ejecuciones normales omiten sistemas `READY`; `Reconstruir` es deliberadamente más lento porque
fuerza una verificación total. El cronómetro permite obtener una línea base real antes de introducir
paralelismo o caché, evitando optimizaciones que aumenten fallos de red o presión sobre Room.

La prueba limpia no debe realizarse desinstalando la copia principal del usuario; usar un segundo
dispositivo, perfil o emulador para conservar las sesiones y ROMs actuales.

## Evidencia previa al CI

- Validación estática: **PASS**, 1.242 comprobaciones.
- Ready Core 01B: **PASS**, 76 contratos.
- IA login: **PASS**, 50 contratos.
- Mando/colecciones Fix 12: **PASS**, 13 contratos.
- `testDebugUnitTest` y `assembleDebug`: deben ejecutarse en GitHub Actions con JDK 17; el entorno
  de preparación no dispone localmente de la distribución Gradle 8.11.1.

# Notas de versión — Oz RetroRom 0.4.0 Rebuild 3

## Interfaz

- Se agregaron filtros por sistema, género y región a la pantalla principal.
- Los cuatro accesos principales tienen texto y estilos explícitos.
- Se eliminó el comportamiento ambiguo de botones rosados sin etiqueta.
- El icono Arcade Marquee se usa en launcher, icono redondo, icono adaptativo y placeholders de artwork.
- El cuadro CRT conserva bordes redondeados y el botón de juego usa estilo primario explícito.

## Catálogo global

- Se eliminaron los botones `Indexar sistema` e `Indexar catálogo completo`.
- La selección de consola controla el catálogo completo de ese sistema.
- La preparación comienza automáticamente solo cuando el sistema no está listo o requiere actualización.
- Se eliminaron los límites de 1.000 resultados en la consulta local.
- Se agregaron filtros por familias de género y por región.
- La región preferida agrupa revisiones y variantes para disminuir duplicados.
- Los metadatos de género se consultan desde Libretro cuando están disponibles; no se fabrican categorías para entradas desconocidas.

## FinalBurn Neo

- El romset se identifica por el nombre exacto del archivo ZIP informado por el índice FBNeo split.
- Fuentes locales, HTTP y torrent se comprueban contra ese nombre.
- Un ZIP de iiSU que contiene únicamente `oz-retrorom.json` se rechaza antes de abrir RetroArch.
- Se incorporó una carpeta de BIOS FBNeo configurable.
- Las BIOS declaradas y aportadas por el usuario se integran únicamente en la copia temporal de sesión.
- El diagnóstico registra romset, BIOS integradas y BIOS faltantes.

## Persistencia

- Room sube a esquema 2 y añade título canónico, género, romset y BIOS.
- La migración conserva favoritos, colecciones, juegos indexados y configuraciones.
- Un backfill procesa los registros existentes en lotes de 500.

## Identificación de versión

- `versionCode = 40003`
- `versionName = 0.4.0-rebuild3`

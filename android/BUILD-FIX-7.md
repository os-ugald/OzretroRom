# Oz RetroRom 0.6.0 Beta 4 — Fix 7: biblioteca compacta + iconos SteamGridDB

## Identificación

- `applicationId`: `cl.gakuto.kizuna`
- `versionCode`: `60011`
- `versionName`: `0.6.0-beta.4`
- Room: sin cambios de esquema
- ABI: `arm64-v8a`

## Objetivo

Dar prioridad visual a los juegos. La navegación principal, catálogo, biblioteca y colecciones reducen chrome, márgenes y tarjetas grandes. El modo predeterminado usa mosaicos cuadrados compactos inspirados en la navegación visual de iiSU.

## SteamGridDB

Se añade una integración **opt-in** para iconos cuadrados:

1. El usuario introduce su propia API key de SteamGridDB en `Configuración → Biblioteca y almacenamiento`.
2. En una colección, `Iconos SGDB` realiza una sincronización explícita.
3. Oz busca el juego mediante API v2, conserva el ID resuelto y descarga un icono a almacenamiento privado.
4. Las pantallas normales nunca llaman a SteamGridDB durante scroll/navegación: consumen sólo la caché local.
5. Si no existe icono, `ArtworkResolver` conserva la cadena existente de artwork personalizado/local/Libretro.

La sincronización de carátulas Libretro anterior se conserva y vuelve a utilizarse al seleccionar `Carátulas clásicas`.

## UI

- Rejilla de juegos: `GridCells.Adaptive(104.dp)` en lugar de 145 dp.
- Tiles de juego: proporción 1:1 en modo compacto, título de una línea y punto verde discreto para ROM local.
- Rails de Inicio: 108 dp por juego.
- Colecciones: tarjetas de 228 dp con tres previews compactos; se elimina el hero redundante de la pantalla de colecciones.
- Navegación inferior/lateral: menos padding; los targets interactivos mantienen al menos 48 dp.
- Cabeceras y búsqueda reducidas para devolver área útil a los juegos.
- La ficha detallada del juego conserva la carátula vertical tradicional.

## Funcionalidad conservada

No se modifica Room, ROMgi, Internet Archive, descargas, SAF, RetroArch, cores, BIOS, overlays, emuladores externos, descriptores iiSU ni orientación de SessionActivity.

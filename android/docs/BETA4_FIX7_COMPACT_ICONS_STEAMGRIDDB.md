# Investigación y diseño — Beta 4 Fix 7

## iiSU

La versión actual de iiSU documenta scraping múltiple y específicamente `SteamGridDB Home icon scraping`, previsualización, scraping por lotes y controles de caché. Eso justifica separar en Oz el arte de navegación cuadrado de la carátula vertical de detalle.

## SteamGridDB API v2

Contrato implementado a partir del wrapper oficial de SteamGridDB:

- Base: `https://www.steamgriddb.com/api/v2`
- Autenticación: `Authorization: Bearer <api key>`
- Búsqueda: `GET /search/autocomplete/{query}`
- Iconos: `GET /icons/game/{id}`

Oz no incorpora una clave del desarrollador. Cada usuario aporta su propia clave. No hay peticiones desde composables; la acción explícita por colección descarga y cachea resultados.

## Fallback y offline

Prioridad del tile compacto:

1. artwork personalizado del usuario;
2. icono cuadrado sincronizado desde SteamGridDB;
3. cadena de artwork existente de Oz (caché Libretro/local/remoto según preferencias).

Por lo tanto SteamGridDB es una mejora visual opcional y no un requisito para abrir Oz ni para lanzar juegos.

## Densidad adaptativa

Se usa `GridCells.Adaptive(104.dp)`: Compose calcula tantas columnas como quepan respetando el mínimo. El tamaño se eligió para permitir aproximadamente tres juegos por fila en teléfonos compactos manteniendo legibilidad y un target táctil ampliamente superior a 48 dp.

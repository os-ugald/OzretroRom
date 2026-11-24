# Notas de versión — Oz RetroRom 0.4.0 Rebuild 6

## Corrección principal

Se eliminó la carga masiva que congelaba la interfaz al seleccionar sistemas extensos, especialmente PlayStation.

## Cambios

- Paginación incremental de 80/120/200 registros.
- Modo Fluido predeterminado.
- Debounce de búsqueda de 300 ms.
- Consultas dinámicas de Room con `LIMIT` y `OFFSET`.
- Deduplicación regional mantenida entre páginas.
- RecyclerView sin animaciones costosas ni DiffUtil sobre miles de elementos.
- Cola de artwork limitada y bitmaps reducidos.
- Índices compuestos y migración Room 2→3.
- WAL para consultar mientras se indexan juegos.
- Conservación de ROMgi, iiSU, FBNeo, filtros, colecciones y lanzamiento automático.

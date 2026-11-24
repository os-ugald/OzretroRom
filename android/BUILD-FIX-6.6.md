# Oz RetroRom 0.6.0 Beta 3 — Fix 6.6

Corrección incremental sobre Fix 6.5. La versión pública permanece `0.6.0-beta.3` / `60003`.

## Cambios

1. **Menú central de Inicio**
   - Nuevo estilo `OzTypography.tileTitle` de 14sp.
   - Títulos en una línea, sin saltos que corten `Biblioteca`, `Colecciones` o `Configuración`.
   - Padding/espaciado horizontal ligeramente reducido para conservar el símbolo y el subtítulo.

2. **Cover arts de colecciones**
   - Las membresías siguen apuntando a sus IDs estables; no se reemplaza ningún juego de la colección.
   - Para dibujar el collage, si existe una entrada `source=libretro` con el mismo `systemId` y `canonicalTitle`, se usa esa variante como proxy de artwork. Esto permite usar el título DAT exacto que corresponde a `libretro-thumbnails`.
   - Si todavía no existe la variante indexada, se conserva el juego original.
   - El `remember` del collage ahora depende de título, repositorio y preferencias de artwork, por lo que se recalcula cuando cambia metadata relevante.
   - Si la primera fuente de artwork falla, Compose avanza por los candidatos Boxart/Title/Snap configurados por `ArtworkResolver`.

## Persistencia

No hay cambio de schema Room ni migración. No se modifican `contentUri`, favoritos, membresías, SAF, ROMgi, IA-S3, RetroArch ni iiSU.

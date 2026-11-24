# Fix 6.6 — validación de UI y artwork de colecciones

Base: Fix 6.5 compilable en Termux.

- Tipografía de tiles: 14sp, una línea.
- Artwork: proxy Libretro por `systemId + canonicalTitle`, solo para presentación.
- Fallback de candidatos de artwork en Compose.
- Sin migración Room.
- Validación estática: ejecutar `python scripts/validate_project.py`.
- Contrato específico: ejecutar `python scripts/test_fix66_collection_artwork.py`.

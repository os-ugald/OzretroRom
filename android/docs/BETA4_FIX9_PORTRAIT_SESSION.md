# Oz RetroRom 0.6.0 Beta 4 — Fix 9

## Objetivo

Refinar la pantalla de sesión del Fix 8 para orientación vertical sin recuperar scroll interno.

## Composición portrait

- Hero horizontal superior.
- Icono del juego: 118 dp, izquierda, `fitCenter`.
- Información a la derecha: plataforma, fuente, título, estado, ayuda, porcentaje y tiempo transcurrido.
- Debajo: barra de progreso, nombre de archivo y métricas de descarga.
- Pie fijo: acciones de recuperación y Cancelar/Cerrar.
- La pantalla no contiene `ScrollView` ni `NestedScrollView`.

## Artwork

`SessionActivity` conserva la resolución introducida en Fix 8/Fix 7 mediante `ArtworkResolver.tileCandidates()`, por lo que el icono compacto sincronizado desde SteamGridDB tiene prioridad y las fuentes de arte previas actúan como fallback.

## Landscape / iiSU

`res/layout-land/activity_session.xml` conserva el diseño de dos paneles del Fix 8.

## Versión

- `versionCode = 60013`
- `versionName = 0.6.0-beta.4`

## Validación

Ejecutar:

```bash
python scripts/test_beta4_fix9_portrait_session.py
python scripts/test_beta4_fix8_fullscreen_session.py
python scripts/test_beta4_fix7_compact_icons.py
python scripts/validate_project.py
```

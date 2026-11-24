# Oz RetroRom 0.6.0 Beta 4 — Fix 9

Ajuste final de la pantalla de sesión en orientación vertical:

- sin ScrollView;
- icono del juego fijo a la izquierda;
- plataforma, título, fuente, estado, porcentaje y tiempo a la derecha del icono;
- barra y métricas compactas debajo;
- acciones contextuales ancladas al pie;
- SteamGridDB sigue siendo la primera fuente del icono mediante `ArtworkResolver.tileCandidates()`;
- landscape/iiSU conserva la composición de dos paneles del Fix 8;
- `versionCode = 60013`.

Validar con:

```bash
python scripts/test_beta4_fix9_portrait_session.py
python scripts/test_beta4_fix8_fullscreen_session.py
python scripts/test_beta4_fix7_compact_icons.py
python scripts/validate_project.py
```

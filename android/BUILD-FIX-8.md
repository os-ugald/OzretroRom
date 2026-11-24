# Oz RetroRom 0.6.0 Beta 4 — Fix 8

Último ajuste visual de la pantalla de sesión:

- sin ScrollView;
- layout fijo portrait;
- layout fijo landscape para iiSU;
- icono del juego prioritario mediante `ArtworkResolver.tileCandidates()`;
- progreso y preflight comparten el mismo slot visual;
- `versionCode = 60012`.

Validar con:

```bash
python scripts/test_beta4_fix8_fullscreen_session.py
python scripts/test_beta4_fix7_compact_icons.py
python scripts/validate_project.py
```

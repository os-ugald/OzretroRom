# Fase 3 — Integración de Branding Oficial y Splashscreen ("Hylian Dark")

**Fecha:** 2026-11-24  
**Alcance:** `/python-hub` y `/android` dentro de `C:\OzretroRom`  
**Fuentes oficiales:** [logo_master.jpg](file:///C:/OzretroRom/shared/logo_master.jpg) (`2048x2048` JPEG) y [splash_hylian.gif](file:///C:/OzretroRom/shared/splash_hylian.gif) (`640x360` GIF, 30 frames, 3000 ms)

---

## 1. Script y Assets Generados

Script auxiliar: [generate_brand_assets.py](file:///C:/OzretroRom/python-hub/scripts/generate_brand_assets.py) (ejecutado con Pillow del entorno `.venv` de `/python-hub`, fondo base `#0A0F0D`).

### 1.1 Assets PC Hub / Web / PyInstaller

| Archivo Generado | Especificación | Tamaño |
| :--- | :--- | ---: |
| [logo.png](file:///C:/OzretroRom/python-hub/app/static/assets/logo.png) | PNG `512x512` sobre `#0A0F0D` | 202,925 B |
| [favicon.ico](file:///C:/OzretroRom/python-hub/app/static/assets/favicon.ico) | ICO multi-resolución (`16/32/48/64/256`) | 61,502 B |
| [ozretrorom.ico](file:///C:/OzretroRom/python-hub/packaging/ozretrorom.ico) | ICO PyInstaller (`16/32/48/64/256`) | 61,502 B |
| [splash_hylian.gif](file:///C:/OzretroRom/python-hub/app/static/assets/splash_hylian.gif) | GIF animado `640x360` (30 frames) | 1,994,520 B |

### 1.2 Assets Android (`mipmap-*` y `res/raw`)

| Densidad | `ic_launcher.png` | `ic_launcher_round.png` | `ic_launcher_foreground.png` (Adaptive 66/108) |
| :--- | :--- | :--- | :--- |
| `mdpi` | `48x48` (2,332 B) | `48x48` (3,374 B) | `108x108` (5,474 B) |
| `hdpi` | `72x72` (4,571 B) | `72x72` (5,894 B) | `162x162` (10,552 B) |
| `xhdpi` | `96x96` (7,688 B) | `96x96` (9,580 B) | `216x216` (16,733 B) |
| `xxhdpi` | `144x144` (15,935 B) | `144x144` (18,487 B) | `324x324` (33,258 B) |
| `xxxhdpi` | `192x192` (27,876 B) | `192x192` (29,921 B) | `432x432` (56,584 B) |

- **Splash raw:** [splash_hylian.gif](file:///C:/OzretroRom/android/app/src/main/res/raw/splash_hylian.gif) (`640x360` GIF, 1,994,520 B).

---

## 2. Integración en Android

- **Adaptive Icons (`#0A0F0D`):**
  - Se añadió `<color name="ic_launcher_background">#0A0F0D</color>` en [values/colors.xml](file:///C:/OzretroRom/android/app/src/main/res/values/colors.xml) y [values-night/colors.xml](file:///C:/OzretroRom/android/app/src/main/res/values-night/colors.xml).
  - Se actualizaron [mipmap-anydpi-v26/ic_launcher.xml](file:///C:/OzretroRom/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) e [ic_launcher_round.xml](file:///C:/OzretroRom/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml) para usar `@color/ic_launcher_background` y `@mipmap/ic_launcher_foreground`, eliminando el directorio heredado `mipmap-anydpi/`.
- **Coil 3 GIF (`io.coil-kt.coil3:coil-gif:3.2.0`):**
  - Configurado en [OzApplication.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/OzApplication.kt) (`AnimatedImageDecoder.Factory()` en API >= 28, `GifDecoder.Factory()` en versiones anteriores).
- **Splashscreen Animado (`HylianSplashOverlay`):**
  - Nuevo composable [HylianSplashOverlay.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/HylianSplashOverlay.kt) e integración en [OzApp.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt) sobre `#0A0F0D`.
  - Reproduce `R.raw.splash_hylian` una vez (`repeatCount(0)` + `onAnimationEnd`, respaldo `3200 ms`), aplica `fadeOut` de `420 ms` hacia `HylianPlayScreen` y permite salto inmediato por toque o mando/teclado (`A / Start / Enter / D-Pad Center / Space / B / Back / Escape`).

---

## 3. Integración en PC Hub (`/python-hub`)

- **Rutas y ventana desktop:**
  - [main.py](file:///C:/OzretroRom/python-hub/app/main.py) expone `GET /favicon.ico` apuntando a `app/static/assets/favicon.ico`.
  - [desktop.py](file:///C:/OzretroRom/python-hub/app/desktop.py) actualiza `background_color="#0A0F0D"` e inyecta el ícono resuelto vía `webview.start(icon=...)`.
- **UI y Splash Web:**
  - [ui.html](file:///C:/OzretroRom/python-hub/app/static/ui.html) conecta `/static/assets/favicon.ico`, `/static/assets/logo.png` junto a `OZ GAME VAULT` / `RETRO EDITION · HYLIAN` y añade `#hylian-splash-overlay` con `/static/assets/splash_hylian.gif`.
  - [styles.css](file:///C:/OzretroRom/python-hub/app/static/styles.css) estiliza `.brand-logo-img` y `.hylian-splash-overlay` (`#0A0F0D`, transición `420ms`).
  - [app.js](file:///C:/OzretroRom/python-hub/app/static/js/app.js) y [gamepad.js](file:///C:/OzretroRom/python-hub/app/static/js/gamepad.js) muestran el splash una vez por sesión (`sessionStorage` clave `oz_hylian_splash_seen`, `3200ms`) con salto inmediato por clic, teclado o mando sin filtrar eventos a la UI inferior.
- **Empaquetado PyInstaller:**
  - [ozretrorom.spec](file:///C:/OzretroRom/python-hub/packaging/ozretrorom.spec) empaqueta `app/static/styles.css`, `app/static/js`, `app/static/assets`, `app/data` e incluye `icon=packaging/ozretrorom.ico` en `EXE(...)`.

---

## 4. Resultados de Verificación (Post-Edición Final)

| Suite / Verificación | Comando | Resultado Exacto |
| :--- | :--- | :--- |
| Módulos JS (`/python-hub`) | `node --check` sobre `app/static/js/**/*.js` | **`10/10` archivos sin errores** (`JS_CHECK_PASSED`) |
| Backend + Web (`/python-hub`) | `.\.venv\Scripts\python.exe -m pytest -q` | **`25 passed, 0 failed, 0 skipped`** (`1` warning de `StarletteDeprecationWarning`) |
| Pruebas Android (`/android`) | `.\gradlew.bat testDebugUnitTest --no-daemon` | **`BUILD SUCCESSFUL` · `281` tests totales, `0` fallos, `0` errores, `0` omitidos** |

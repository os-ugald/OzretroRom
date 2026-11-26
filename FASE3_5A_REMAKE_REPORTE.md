# Fase 3.5A — Remake Visual HUD Biselado ("Cyber Vault" / "Hylian")

**Fecha:** 2026-11-24  
**Alcance:** `/android` y `/python-hub` dentro de `C:\OzretroRom`  
**Fuente de verdad visual:** 15 mockups oficiales en `C:\OzretroRom\docs\mockups\`

---

## 1. Design System, Geometría Biselada y Fuentes Offline

### 1.1 Tipografía Incrustada Localmente (`Oxanium` + `Space Grotesk`)
Script de descarga e incrustación: [fetch_brand_fonts.py](file:///C:/OzretroRom/python-hub/scripts/fetch_brand_fonts.py).
- **Android (`android/app/src/main/res/font/`):** `oxanium_semibold.ttf` (28,416 B), `oxanium_bold.ttf` (28,332 B), `space_grotesk_regular.ttf` (69,360 B), `space_grotesk_medium.ttf` (69,416 B), `space_grotesk_semibold.ttf` (69,356 B), `space_grotesk_bold.ttf` (69,308 B).
- **PC Hub (`python-hub/app/static/fonts/`):** equivalentes `.woff2` (14,044 B / 22,288 B) y `.ttf` declarados con `@font-face` local en [styles.css](file:///C:/OzretroRom/python-hub/app/static/styles.css) e incluidos en [ozretrorom.spec](file:///C:/OzretroRom/python-hub/packaging/ozretrorom.spec).
- Se eliminaron todas las dependencias remotas a `fonts.googleapis.com` y fuentes genéricas en [OzTheme.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/theme/OzTheme.kt) y [styles.css](file:///C:/OzretroRom/python-hub/app/static/styles.css).

### 1.2 Primitivas Biseladas a 45° (Chamfered HUD)
- **Android ([OzHudPrimitives.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzHudPrimitives.kt)):** `OzChamferShape(cut)`, `OzHudCard`, `OzHudSurfaceBox`, `OzHudButton` (gradiente cyan→violeta o esmeralda→dorado), `OzHudTag`, `OzHudChip`, `OzHudSectionHeader` y `OzHudSearchField`. Además, `OzShapes` en [OzTheme.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/theme/OzTheme.kt) migró íntegramente a `CutCornerShape`.
- **Web ([styles.css](file:///C:/OzretroRom/python-hub/app/static/styles.css)):** `.oz-chamfer-sm`, `.oz-chamfer-md`, `.oz-chamfer-lg` con `clip-path: polygon(...)` a 45°, botones `.btn`, chips `.oz-hud-chip`, tags `.oz-tag` y tarjetas `.oz-game-card`.

### 1.3 Sistema de Temas Dual (Default `Cyber Vault`)
Persistido en [AppPreferences.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/data/AppPreferences.kt) (`AppThemePreset` + `AppThemeMode`), [SettingsViewModel.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/domain/SettingsViewModel.kt), [MainActivity.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/MainActivity.kt) y `localStorage` (`oz_theme_id`) en web:
- **Cyber Vault Dark (Default):** Fondo `#070816`, superficie `#0E1126`, borde `#252A4A`, primario `#22D3EE`, secundario `#7C3AED`, texto `#F4F6FF`.
- **Cyber Vault Light:** Fondo `#F3F6FC`, superficie `#FFFFFF`, borde `#D6DDF0`, primario `#2563EB` / `#0891B2`, secundario `#7C3AED`, texto `#0F172A`.
- **Hylian Dark / Light (Preset seleccionable en Ajustes):** Fondo `#0A0F0D` / `#F2F7F4`, superficie `#141F1A` / `#FFFFFF`, borde `#23382E` / `#B8CEC3`, primario `#1E8E63` / `#177350`, acento `#D9B64E` / `#A67E16`.

---

## 2. Shell Separado por Plataforma y Reglas Cumplidas

- **Móvil (`< 960px`):** `OzMobileHeader` / `#mobile-header` (`OZ` + `R E T R O R O M` + `PLAY. PRESERVE. REMEMBER.` + módulo derecho con estado PC Hub y engranaje Ajustes) y dock esculpido inferior (`OzCenterDock` / `#mobile-center-dock`) con 4 pestañas (`Inicio`, `Biblioteca`, `Colecciones`, `Buscar`) y botón circular central elevado `OZ` para Quick Resume.
- **Windows PC Hub (`>= 960px`):** `OzDesktopTopBar` / `#desktop-topbar` horizontal superior con pestañas biseladas, búsqueda rápida Omnibox, toggle Dark/Light, estado SSE y Ajustes. Regla `@media (min-width: 960px) { .mobile-center-dock { display: none !important; } }` aplicada estrictamente.
- **Hoja SSE de actividad oculta en reposo:** [OzActivityFloatingSheet.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzActivityFloatingSheet.kt) (`if (!hasActiveWork) return`) y [activity-sheet.js](file:///C:/OzretroRom/python-hub/app/static/js/activity-sheet.js) (`sheet.hidden = true` con `activeCount === 0`) no ocupan espacio alguno sin transferencias o descargas activas.

---

## 3. Las 3 Vistas Core del Remake

1. **`Inicio` ([OzHomeScreen.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/OzHomeScreen.kt) y `#view-home`):** Hero full-bleed con arte Quick Resume degradado hacia el fondo, título en `Oxanium`, un único CTA primario (`CONTINUAR`) y botón secundario `•••`, chips de consolas y carruseles `Jugados recientemente` / `Recomendados`.
2. **`Biblioteca` (`OZ LIBRARY` — [OzLibraryScreen.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/OzLibraryScreen.kt) y `#view-library`):** Pestañas biseladas (`Todos`, `Favoritos`, `Recientes`, `Descargados`, `Por consola`), chips de consola (`ALL`, `PS1`, `DREAMCAST`, `SNES`, `ARCADE`, ...), selector de vista Grilla/Lista, búsqueda/ordenamiento compactos, menú `⋮` para mantenimiento y tarjetas limpias `3:4` con corazón superior y pie biselado ([OzCoverImage.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzCoverImage.kt), [library.js](file:///C:/OzretroRom/python-hub/app/static/js/views/library.js)).
3. **`Buscar y Añadir` (`SEARCH & ADD GAME` — [OzSearchAddScreen.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/OzSearchAddScreen.kt) y `#view-search`):** Buscador biselado grande, chips de sugerencias, tarjeta destacada `AÑADIR JUEGO` (`IMPORT ROM` con SAF en Android), resultados en filas horizontales biseladas unificadas (Local + MiNERVA) y panel `OZ AI RECOMMENDS`.

---

## 4. Auditoría, Verificación y Compilación Final

| Verificación | Resultado Exacto |
| :--- | :--- |
| Auditoría CSS/HTML (`999px`/`9999px`/`fonts.googleapis`) | **`0` coincidencias** (`AUDIT_CSS=0`, `AUDIT_HTML=0`) |
| Auditoría Kotlin nuevo (`RoundedCornerShape(50)`/`OzShapes.pill`/fuentes sistema) | **`0` infracciones** (`AUDIT_KT_PILLS=0`, `AUDIT_KT_SYS_FONTS=0`) |
| Sintaxis JS (`node --check` en `app/static/js/**/*.js`) | **`10/10` archivos sin errores** (`JS_CHECK_PASSED`) |
| Backend + Web (`/python-hub` · `pytest -q`) | **`25 passed, 0 failed, 0 skipped`** (`1` warning de `StarletteDeprecationWarning`) |
| Pruebas Unitarias Android (`.\gradlew.bat testDebugUnitTest --no-daemon`) | **`BUILD SUCCESSFUL` · `281` tests totales, `0` fallos, `0` errores, `0` omitidos** |
| APK Debug Android (`.\gradlew.bat assembleDebug --no-daemon`) | **[app-debug.apk](file:///C:/OzretroRom/android/app/build/outputs/apk/debug/app-debug.apk) · `39.43 MB` (`41,342,197` bytes)** |

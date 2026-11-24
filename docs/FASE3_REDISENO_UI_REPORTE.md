# Fase 3 — Rediseño UI/UX Disruptivo ("Hylian Dark" Console Experience)

**Fecha:** 2026-11-24  
**Alcance:** `/python-hub` y `/android` dentro de `C:\OzretroRom`  
**Estado:** Completado y verificado al 100% en Python/JS y Android

---

## 1. Resumen Ejecutivo

La **Fase 3** sustituyó el paradigma de "dashboard administrativo" (barras laterales fijas, tiras de KPIs y matrices de herramientas) por una interfaz inmersiva tipo consola moderna y minimalista en ambas plataformas:

- **Estética "Hylian Dark" compartida** en [OzTheme.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/theme/OzTheme.kt) y [styles.css](file:///C:/OzretroRom/python-hub/app/static/styles.css), inspirada en verdes profundos OLED y dorado sutil para el foco.
- **Arquitectura de 3 destinos limpios + 1 Hoja Flotante de Actividad persistente**, eliminando la navegación rutinaria hacia Activities XML heredadas en Android y consolidando la barra lateral en web.
- **Carátulas uniformes `3:4` con blur adaptativo**, sin texto ni insignias invasivas sobre el arte, con zoom de foco (`1.05x`), anillo dorado (`#D9B64E`) y fondo ambiental dinámico (*Ambient Backdrop*).
- **Navegación dual Táctil + Mando/Gamepad** (`L1/R1`, `D-Pad`, `A/Cross`, `B/Circle`, `Y/Triangle`) en Compose y vía HTML5 Gamepad API (`navigator.getGamepads`) + teclado espacial en web.

---

## 2. Sistema Visual "Hylian Dark" Compartido

| Token | Valor Hex | Uso principal | Implementación |
| :--- | :--- | :--- | :--- |
| Fondo base OLED | `#0A0F0D` | Lienzo principal oscuro bosque | `OzColors.HylianBg` (`OzTheme.kt`) / `styles.css` |
| Superficie carbón | `#141F1A` | Tarjetas, paneles y barra de navegación | `OzColors.HylianSurface` (`OzTheme.kt`) / `styles.css` |
| Borde sutil | `#23382E` | Separadores y contornos discretos | `OzColors.HylianBorder` (`OzTheme.kt`) / `styles.css` |
| Primario Esmeralda | `#1E8E63` | Acciones principales y estados activos | `OzColors.HylianEmerald` (`OzTheme.kt`) / `styles.css` |
| Acento Dorado | `#D9B64E` | Foco de mando, favoritos y "Continuar Ahora" | `OzColors.HylianGold` (`OzTheme.kt`) / `styles.css` |
| Texto primario | `#F3F6F4` | Títulos y etiquetas principales | `OzTheme.kt` / `styles.css` |
| Texto secundario | `#A7BDB2` | Metadatos y subtítulos | `OzTheme.kt` / `styles.css` |

- **Factor de escala de foco:** `OzMotion.focusScale = 1.05f` en Android y `scale(1.05)` en CSS.
- **Identidad de marca preservada:** en [ui.html](file:///C:/OzretroRom/python-hub/app/static/ui.html) se mantuvo la marca `OZ GAME VAULT` junto con `RETRO EDITION · HYLIAN`.

---

## 3. Arquitectura de Navegación y Superficies

### 3.1 Tres Destinos Principales

1. **`Jugar` (`Play` / `#view-home`)**
   - **Héroe "Continuar Ahora" (Quick Resume):** en Android se resuelve desde Room v7 mediante `SyncMetadataDao.observeLatestQuickResume()` y `HomeDomainHolder.quickResumeHero` ([Daos.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/data/Daos.kt), [HomeViewModel.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/domain/HomeViewModel.kt), [HylianPlayScreen.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/HylianPlayScreen.kt)); en `/python-hub` se expone en `serialize_all()` (`quickResume`) y `build_home_payload()` (`quickResumeHero`) ([catalog.py](file:///C:/OzretroRom/python-hub/app/catalog.py), [home_engine.py](file:///C:/OzretroRom/python-hub/app/engines/home_engine.py)).
   - **Omnibox superior (`#global-search` / `#omnibox-panel`):** búsqueda unificada en Biblioteca Local, PC Hub y catálogo MiNERVA + MAME.
   - **Carruseles horizontales `3:4`:** `Recientes`, `Favoritos` y `Destacados`, sin tiras de KPIs ni barra lateral (`home-stats-strip` y `app-sidebar` eliminados).

2. **`Explorar` (`Explore` / `#view-explore`)**
   - Selector segmentado **`Sagas | Consolas`** ([HylianExploreScreen.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/HylianExploreScreen.kt), [ui.html](file:///C:/OzretroRom/python-hub/app/static/ui.html), [sagas.js](file:///C:/OzretroRom/python-hub/app/static/js/views/sagas.js)).
   - En **Sagas**, [SagasScreens.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/SagasScreens.kt) y el cliente web muestran el artwork oficial (`libretroArt` de `sagas_catalog.json`) con degradado oscuro `#0A0F0D`.

3. **`Ajustes` (`Settings` / `#view-settings`)**
   - En Android, [HylianSettingsScreen.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/HylianSettingsScreen.kt) unifica emparejamiento mDNS del PC Hub en un toque, configuración de RetroArch/emuladores (`EngineMode`, auto-launch, auto-cores), almacenamiento SAF, sincronización de carátulas, tema/orientación y acceso a destinos FTP.
   - En web, [settings.js](file:///C:/OzretroRom/python-hub/app/static/js/views/settings.js) consolida el panel `#spanel-bridge` y la configuración del Hub en `#view-settings`.

### 3.2 Hoja Flotante de Actividad Persistente (SSE + `StateFlow`)

- **Web:** [activity-sheet.js](file:///C:/OzretroRom/python-hub/app/static/js/activity-sheet.js) (`#activity-sheet`) conectado al flujo SSE `GET /api/v1/events` vía [api-client.js](file:///C:/OzretroRom/python-hub/app/static/js/api-client.js) (`onOpen` / `onError`), reportando descargas, parches `[ES]`, sincronización de carátulas y envíos FTP.
- **Android:** [OzActivityFloatingSheet.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzActivityFloatingSheet.kt) anclado sobre `OzNavDock` en [OzApp.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt), agregando `downloadUi`, `artworkSyncUi`, `ftpJobs`, `isApplyingPatch`, `catalogPreparation`, `pcHubSseConnected` y `latestHubEvent` ([HubSyncViewModel.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/domain/HubSyncViewModel.kt), [OzViewModel.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/OzViewModel.kt)).

---

## 4. Carátulas `3:4`, Blur Adaptativo y Navegación Dual

- **Proporción `3:4` (`0.75f`):** constante `OZ_IMMERSIVE_COVER_ASPECT_RATIO = 0.75f` en [OzCoverImage.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzCoverImage.kt) y `aspect-ratio: 3 / 4` en [styles.css](file:///C:/OzretroRom/python-hub/app/static/styles.css).
- **Blur adaptativo sin recorte:** capa de fondo (`ContentScale.Crop` / `.cover-blur-bg`) difuminada + frente completo (`ContentScale.Fit` / `.cover-fg-contain`) + indicador discreto de disponibilidad (`.avail-dot` / punto Local o Hub), eliminando etiquetas superpuestas sobre la carátula.
- **Ambient Backdrop + Foco Dorado:** al enfocar tarjetas en `HylianGameCard`, `OzSurfaceTile` o `setConsoleFocus()` ([gamepad.js](file:///C:/OzretroRom/python-hub/app/static/js/gamepad.js)), la tarjeta escala a `1.05x`, muestra borde `#D9B64E` y actualiza el fondo ambiental ([OzSurface.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/surface/OzSurface.kt), `setAmbientBackdrop` en [state.js](file:///C:/OzretroRom/python-hub/app/static/js/state.js)).
- **Controles Duales (Táctil `>= 48dp` + Gamepad/D-Pad):**
  - `L1 / R1` (y `[` / `]` en teclado web): ciclan entre `Jugar`, `Explorar` y `Ajustes`.
  - `D-Pad` / Stick izquierdo: navegación espacial con foco visible.
  - `A / Cross`: iniciar o activar elemento enfocado; `B / Circle`: volver o cerrar paneles; `Y / Triangle`: enfocar barra Omnibox.

---

## 5. Archivos Creados y Modificados en Fase 3

### `/python-hub`
- Modificados:
  - [app/catalog.py](file:///C:/OzretroRom/python-hub/app/catalog.py)
  - [app/engines/home_engine.py](file:///C:/OzretroRom/python-hub/app/engines/home_engine.py)
  - [app/static/ui.html](file:///C:/OzretroRom/python-hub/app/static/ui.html)
  - [app/static/styles.css](file:///C:/OzretroRom/python-hub/app/static/styles.css)
  - [app/static/js/state.js](file:///C:/OzretroRom/python-hub/app/static/js/state.js)
  - [app/static/js/api-client.js](file:///C:/OzretroRom/python-hub/app/static/js/api-client.js)
  - [app/static/js/app.js](file:///C:/OzretroRom/python-hub/app/static/js/app.js)
  - [app/static/js/views/home.js](file:///C:/OzretroRom/python-hub/app/static/js/views/home.js)
  - [app/static/js/views/library.js](file:///C:/OzretroRom/python-hub/app/static/js/views/library.js)
  - [app/static/js/views/catalog.js](file:///C:/OzretroRom/python-hub/app/static/js/views/catalog.js)
  - [app/static/js/views/sagas.js](file:///C:/OzretroRom/python-hub/app/static/js/views/sagas.js)
  - [app/static/js/views/settings.js](file:///C:/OzretroRom/python-hub/app/static/js/views/settings.js)
  - [tests/test_hub.py](file:///C:/OzretroRom/python-hub/tests/test_hub.py)
- Creados:
  - [app/static/js/activity-sheet.js](file:///C:/OzretroRom/python-hub/app/static/js/activity-sheet.js)
  - [app/static/js/gamepad.js](file:///C:/OzretroRom/python-hub/app/static/js/gamepad.js)

### `/android`
- Modificados:
  - [OzTheme.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/theme/OzTheme.kt)
  - [Daos.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/data/Daos.kt)
  - [HomeViewModel.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/domain/HomeViewModel.kt)
  - [HubSyncViewModel.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/domain/HubSyncViewModel.kt)
  - [OzViewModel.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/OzViewModel.kt)
  - [OzCoverImage.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzCoverImage.kt)
  - [OzSurface.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/surface/OzSurface.kt)
  - [SagasScreens.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/SagasScreens.kt)
  - [OzApp.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt)
- Creados:
  - [OzActivityFloatingSheet.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzActivityFloatingSheet.kt)
  - [HylianPlayScreen.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/HylianPlayScreen.kt)
  - [HylianExploreScreen.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/HylianExploreScreen.kt)
  - [HylianSettingsScreen.kt](file:///C:/OzretroRom/android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/HylianSettingsScreen.kt)
  - [HylianPhase3DesignContractTest.kt](file:///C:/OzretroRom/android/app/src/test/java/cl/gakuto/kizuna/ui/compose/HylianPhase3DesignContractTest.kt)

---

## 6. Resultados de Verificación (Post-Edición Final)

| Suite / Verificación | Comando Ejecutado | Resultado Exacto |
| :--- | :--- | :--- |
| Módulos JS (`/python-hub`) | `node --check` sobre `app/static/js/**/*.js` | **`10/10` archivos sin errores** (`JS_CHECK_PASSED`) |
| Backend + UI Web (`/python-hub`) | `.\.venv\Scripts\python.exe -m pytest -q` | **`24 passed, 0 failed, 0 skipped`** (`1` warning de `StarletteDeprecationWarning`) |
| Suite Unitaria Android (`/android`) | `.\gradlew.bat testDebugUnitTest --no-daemon` | **`BUILD SUCCESSFUL` · `280` tests totales, `0` fallos, `0` errores, `0` omitidos** |

Las pruebas específicas añadidas para Fase 3 (`test_phase3_hylian_dark_console_ui_and_quick_resume` en Python y `HylianPhase3DesignContractTest` en Android) pasaron íntegramente junto al resto de ambas suites.

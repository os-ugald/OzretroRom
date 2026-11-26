# OZ RETROROM — ESPECIFICACIÓN MAESTRA DE REESCRITURA UI ANDROID
**Versión:** 4.0 (Clean UI Overhaul — Spec-Driven)  
**Plataforma:** Android (Jetpack Compose)  
**Estrategia:** Reescritura limpia de la capa visual en **4 fases independientes**, preservando el 100% del motor de negocio, descargas multi-consola, Sagas y sincronización con PC Hub.

---

## 0. REGLAS INNEGOCIABLES Y FRONTERAS BLINDADAS

### 0.1. Código INTOCABLE (Prohibido modificar o eliminar)
El motor de OzretroRom funciona y pasa su suite de tests. **NO TOCAR** bajo ninguna circunstancia:
- `C:\OzretroRom\python-hub\**` (PC Hub completo, índices `source-index/*.bin`, `sagas_catalog.json`, `minerva.py`, `ia_client.py`, `patcher.py`, `transfers.py`, `mdns.py`, `events.py`).
- `android/app/src/main/java/cl/gakuto/kizuna/catalog/**` (Catálogo Libretro, ROMgi, MiNERVA, SmartLists, SourceIndex).
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/domain/**` (`HomeViewModel`, `CatalogViewModel`, `SagasViewModel`, `SettingsViewModel`, `HubSyncViewModel`) y `ui/compose/OzViewModel.kt`.
- `android/app/src/main/java/cl/gakuto/kizuna/launch/**` — **EXCEPTO únicamente** los composables de presentación dentro de `SessionActivity.kt` en la Fase 3 (su ciclo de vida, descargas, intents y lanzamiento a RetroArch/emuladores son intocables).
- `android/app/src/main/java/cl/gakuto/kizuna/ui/InternetArchiveLoginActivity.kt` (flujo WebView seguro de claves IA-S3).

### 0.2. Prohibiciones Estrictas (Cero "Frankenstein")
En todos los archivos tocados durante las 4 fases queda **estrictamente prohibido**:
1. **Mezclar logos:** Eliminar cualquier uso de `GameVaultChestLogo` (cofre naranja) y círculos de texto `"OZ"` en la navegación. Toda la app debe usar un único componente `OzBrandEmblem`.
2. **Controles Material 3 redondos:** Prohibido usar `Switch(...)`, `FilterChip(...)`, `RoundedCornerShape(50)` o `RoundedCornerShape(999.dp)`. Todos los controles son biselados a 45° (`OzChamferShape`).
3. **Badges decorativos inútiles:** Prohibido renderizar tags que digan `"HUD"` en tarjetas de Configuración u otras pantallas.
4. **Emojis de sistema como íconos UI:** Prohibido usar `📡`, `📁`, `🗑️`, `✨`, `★`, `♡`, `♥`, `✓`, `⌕` o `•••` como texto para acciones o botones. Usar exclusivamente `Icons.Rounded.*` o `Canvas` vectorial limpio.
5. **Textos truncados en botones o telemetría:** Ningún botón de ajustes ni caja de métricas de descarga puede cortar palabras críticas (`RETROA...`, `PREGUN...`, `SINCRONIZAR AR...`, `Tamaño pen...`, `Esperando d...`).
6. **Layout vertical estirado en horizontal:** Prohibido mostrar el header móvil superior + el dock inferior cuando el teléfono está en modo horizontal (`maxWidth > maxHeight && maxHeight < 540.dp`).

### 0.3. Protocolo de Ejecución por Fases
- **Ejecuta UNA SOLA FASE por turno**, salvo orden explícita del usuario.
- Al finalizar cada fase, ejecuta obligatoriamente:
  1. Auditoría de regresión de esa fase (ver comandos en cada sección).
  2. `.\gradlew.bat testDebugUnitTest --no-daemon` (100% en verde).
  3. `.\gradlew.bat assembleDebug --no-daemon` (reportar ruta y tamaño de `app-debug.apk`).
- Reporta de forma honesta qué cambió, qué archivos se tocaron y si quedó algún pendiente real. **No inventes verificaciones visuales.**

---

## 1. SISTEMA DE DISEÑO Y BREAKPOINTS RESPONSIVE

### 1.1. Matriz de Layout (`OzShellLayout`)
Definida en `OzApp.kt` mediante `BoxWithConstraints`:

| Modo | Condición Exacta | Navegación | Regla de Contenido |
| :--- | :--- | :--- | :--- |
| **`LANDSCAPE_HANDHELD`** | `maxWidth > maxHeight && maxHeight < 540.dp` | **Rail Lateral Izquierdo (`64.dp`)**. Sin header superior ni dock inferior. | **≥ 80% de altura libre** para contenido/carátulas. Filtros colapsados en 1 sola barra (`40.dp`). Detalle y Descarga en **2 columnas**. |
| **`DESKTOP_WIDE`** | `maxWidth >= 960.dp && maxHeight >= 540.dp` | Barra superior de consola (`52.dp`). Sin dock inferior. | Grillas de 5–7 columnas, vistas maestras-detalle. |
| **`PORTRAIT_PHONE`** | Resto de casos | Header superior fino (`48.dp`) + Dock inferior (`58.dp`) con emblema central elevado (`50.dp`). | **≥ 65% de pantalla visible** para carátulas en Biblioteca sin scroll. |

### 1.2. Wireframe de Referencia — Modo Horizontal (`LANDSCAPE_HANDHELD`)
```text
┌──────┬───────────────────────────────────────────────────────────────────────┐
│ (OZ) │ [TODOS|FAV|REC|DESC]  [Consola ▾]  [🔍 Buscar...]          [⊞|☰] [⋮] │ ← 40dp (1 sola barra)
│      ├───────────────────────────────────────────────────────────────────────┤
│  ⌂   │  ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐     │
│  ≡   │  │ COVER │  │ COVER │  │ COVER │  │ COVER │  │ COVER │  │ COVER │     │
│  ⊞   │  │  3:4  │  │  3:4  │  │  3:4  │  │  3:4  │  │  3:4  │  │  3:4  │     │ ← ≥80% altura libre
│  ⌕   │  │ Título│  │ Título│  │ Título│  │ Título│  │ Título│  │ Título│     │
│      │  └───────┘  └───────┘  └───────┘  └───────┘  └───────┘  └───────┘     │
│ [PC] │                                                                       │
│ [⚙]  │                                                                       │
└──────┴───────────────────────────────────────────────────────────────────────┘
 64dp
```

### 1.3. Geometría, Iluminación y Escala de Alturas
- **Geometría:** `OzChamferShape(cut)` basado en `CutCornerShape`:
  - Tags/badges: `4.dp`
  - Chips/segmentos/botones compactos: `6.dp`
  - Botones primarios/inputs/tarjetas: `8.dp` a `10.dp`
  - Hero/modales: `12.dp`
- **Bordes con Gradiente (`ozHudBorderBrush(alpha)`):** `Brush.linearGradient(listOf(OzColors.AccentPrimary.copy(alpha), OzColors.AccentSecondary.copy(alpha)))`. Reposo: `0.32f`. Activo/Foco: `0.95f`.
- **Glow Neón (`Modifier.ozNeonGlow`):** `drawBehind` con `Brush.radialGradient` suave y controlado (sin ensuciar texto adyacente), aplicado solo en: CTA primario, pestaña activa, tarjeta enfocada por gamepad y aro del emblema `OZ`.
- **Alturas Estándar:**
  - `OzHudChip` compacto: `30.dp`
  - `OzHudSegmentedBar`: `34.dp`
  - `OzHudSearchField`: `36.dp`
  - `OzHudButton`: `42.dp` (compacto `34.dp`)
  - `OzHudToggle`: `44.dp x 24.dp`

---

## 2. FASE 1 — BASE REACTIVA, PRIMITIVAS Y SHELL RESPONSIVE

**Archivos objetivo:**
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/theme/OzTheme.kt`
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzHudPrimitives.kt`
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/ConsoleBadge.kt`
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/OzApp.kt`

### 2.1. `OzTheme.kt` — Estado Reactivo Real
- Convertir `palette` y `activeIsLight` dentro de `object OzColors` a estados reactivos de Compose (`private var palette by mutableStateOf(CyberVaultDarkPalette)` y `private var activeIsLight by mutableStateOf(false)`), garantizando recomposición instantánea al alternar Cyber Vault / Hylian o Dark / Light.
- Exponer `LocalOzShellLayout = staticCompositionLocalOf { OzShellLayout.PORTRAIT_PHONE }` (o en `OzTheme.kt` / archivo dedicado accesible por todas las pantallas) para que cada pantalla sepa con certeza si está renderizando en `LANDSCAPE_HANDHELD`, `PORTRAIT_PHONE` o `DESKTOP_WIDE`.

### 2.2. `OzHudPrimitives.kt` y `ConsoleBadge.kt` — Kit Completo Unificado
Implementar o actualizar en `OzHudPrimitives.kt` (eliminando cualquier `.heightIn(min = 48.dp)` en controles compactos):
1. `ozHudBorderBrush(alpha: Float = 0.4f): Brush`
2. `Modifier.ozNeonGlow(color: Color, radius: Dp = 14.dp, alpha: Float = 0.42f)`
3. `OzBrandEmblem(size: Dp, circular: Boolean = false, glow: Boolean = false, onClick: (() -> Unit)? = null)`: Único emblema oficial de marca usando `R.mipmap.ic_launcher_foreground` con borde en gradiente cyan→violeta.
4. `OzHudCard`: Fondo con gradiente vertical sutil (`SurfaceElevated → Surface`), borde `ozHudBorderBrush`, **sin ningún tag `"HUD"`**.
5. `OzHudButton`: `height = 42.dp` (o `34.dp` si `compact = true`), soporte opcional para `leadingIcon: ImageVector? = null` (ADEMÁS de sobrecarga compatible si alguna pantalla aún pasa `String?`, a limpiar en fases 2–4), gradiente cyan→violeta + `ozNeonGlow` en `primary = true`.
6. `OzHudChip`: `height = 30.dp` por defecto (`compact = true`), `padding(horizontal = 10.dp)`, texto `11.sp` `Oxanium`.
7. `OzHudSegmentedBar`: Contenedor único biselado (`height = 34.dp`, `padding = 3.dp`) que recibe una lista de opciones y pinta la opción activa con fondo de gradiente suave + línea inferior luminosa de `2.dp` (soporte `scrollable = true` para no truncar en pantallas angostas).
8. `OzHudSearchField`: `height = 36.dp`, usando `Icons.Rounded.Search` (`16.dp`) y `Icons.Rounded.Close` (`16.dp`) en lugar de `"⌕"` y `"✕"`.
9. `OzHudToggle`: Switch propio 100% biselado a 45° (`44.dp x 24.dp`, pista con borde gradiente, perilla biselada animada con `animateDpAsState` y glow cyan cuando está activo). Reemplazará a todos los `Switch` de Material 3.
10. `OzHudTag`: `OzChamferShape(4.dp)`, `padding(horizontal = 7.dp, vertical = 2.dp)`, `9.5.sp` `Oxanium`.
11. **`ConsoleBadge.kt`:** Cambiar `OzShapes.pill` por `OzChamferShape(4.dp)` (cero cápsulas ovaladas).

### 2.3. `OzApp.kt` — Shell de 3 Modos y Unificación de Marca
- Implementar el enum público `OzShellLayout { PORTRAIT_PHONE, LANDSCAPE_HANDHELD, DESKTOP_WIDE }` y proveerlo vía `CompositionLocalProvider(LocalOzShellLayout provides shellLayout)`.
- **`LANDSCAPE_HANDHELD`:**
  - `Row(Modifier.fillMaxSize())` con `OzLandscapeRail` (`64.dp`) a la izquierda + línea divisoria vertical `1.dp` en gradiente + `Column(Modifier.weight(1f))` con `OzNavHost` y `OzActivityFloatingSheet`.
  - El Rail contiene: arriba `OzBrandEmblem(40.dp, circular = true, glow = true)` (Quick Resume), al centro los 4 ítems de navegación (`INICIO`, `BIBLIO`, `SAGAS`, `BUSCAR`) con barra vertical activa de `2.5.dp`, y abajo botón vectorial de PC Hub (monitor + punto verde SSE) + botón `Icons.Rounded.Settings`.
- **`PORTRAIT_PHONE`:**
  - `OzMobileHeader` compacto (`vertical = 6.dp`, altura total ~`48.dp`) usando `OzBrandEmblem(32.dp)` (reemplaza a `GameVaultChestLogo`), título `R E T R O R O M` / `PLAY. PRESERVE. REMEMBER.`, y módulo derecho biselado (`34.dp`) con ícono vectorial de PC Hub (con punto verde SSE) + `Icons.Rounded.Settings`.
  - `OzCenterDock` fino (`height = 58.dp`), borde superior `1.dp` en gradiente, 4 pestañas compactas y `OzBrandEmblem(50.dp, circular = true, glow = true)` elevado `(-12).dp` al centro.
- **`DESKTOP_WIDE`:** Barra superior limpia (`52.dp`) usando `OzBrandEmblem`, sin dock inferior.
- Preservar el 100% de rutas de `NavHost`, atajos de gamepad (`L1`, `R1`, `Y`, `Select`, `B`), modales (`OzQuickMenu`, `OzArtworkPicker`, `OzCollectionPicker`, `MoodPickerDialog`, `FtpConfigModal`, `PcHubModal`) y `HylianSplashOverlay`.

### 2.4. Puerta de Verificación — Fase 1
- Verificar con búsqueda en código que `OzApp.kt` tenga **0 referencias** a `GameVaultChestLogo`.
- Verificar que `OzTheme.kt` use `mutableStateOf` en `OzColors`.
- `.\gradlew.bat testDebugUnitTest --no-daemon` en verde + `assembleDebug` exitoso.

---

## 3. FASE 2 — INICIO Y BIBLIOTECA (PROTAGONISMO DE CARÁTULAS)

**Archivos objetivo:**
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzCoverImage.kt`
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/OzHomeScreen.kt`
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/OzLibraryScreen.kt`

### 3.1. `OzCoverImage.kt` — Tarjeta Limpia con Íconos Vectoriales
- En `HylianGameCard` (mantener el nombre del composable para compatibilidad o crear alias):
  - Reemplazar borde plano por `ozHudBorderBrush(if (focused) 0.95f else 0.30f)` y aplicar `.ozNeonGlow(...)` en foco.
  - Reemplazar los glifos de texto `"♥"` / `"♡"`, `"✓"` / `"☁"` / `"⇩"` y `"•••"` por íconos vectoriales `Icons.Rounded.Favorite` / `FavoriteBorder`, `Icons.Rounded.CheckCircle` / `Cloud` / `Download` y `Icons.Rounded.MoreHoriz` (`14.dp`).
  - Reducir el pie de la tarjeta para maximizar la carátula `3:4` (`padding(horizontal = 7.dp, vertical = 6.dp)`).
- Mejorar fallback de `OzCoverImage` cuando un juego no tiene carátula aún: fondo con gradiente sutil usando el color de acento de su consola (`ConsolePalette.forSystem(systemId)`) + cuadrícula sutil en `Canvas`, nunca un rectángulo gris muerto.
- Ajustar `gridColumnsForDensity`: en `LANDSCAPE_HANDHELD`, mostrar **5 columnas** por defecto (`SMALL = 6`, `NORMAL = 5`, `LARGE = 4`).

### 3.2. `OzHomeScreen.kt` — Hero Panorámico con Arte Real
- **Hero de Quick Resume:**
  - Cargar la carátula del juego destacado (`ArtworkResolver.candidates(...)`) como fondo panorámico con blur suave + scrim lateral/inferior oscuro para legibilidad. Si el juego no tiene arte, mostrar carátula o acento cromático de consola + título; **nunca una caja vacía con espacio muerto abajo**.
  - Eliminar cualquier tag flotante desalineado (como el badge `SNES` suelto sobre `•••`).
  - **En `PORTRAIT_PHONE`:** Altura compacta (`~168.dp`), portada miniatura a la izquierda o fondo panorámico, tags (`EN BÓVEDA` + consola), título (máx. 2 líneas), botón `CONTINUAR` (`42.dp`, gradiente + glow + `Icons.Rounded.PlayArrow`) y botón `•••` (`42.dp x 42.dp`, `Icons.Rounded.MoreHoriz`) al lado.
  - **En `LANDSCAPE_HANDHELD`:** Banner compacto horizontal (`~132.dp` de altura) con información a la izquierda y botones `CONTINUAR` + `•••` a la derecha, dejando la primera fila de juegos visible de inmediato sin scroll.
- **Fila Colecciones / Consolas:** Usar `OzHudChip(compact = true)` (`30.dp`).
- **Carruseles (`JUGADOS RECIENTEMENTE`, `RECOMENDADOS Y FAVORITOS`):** Usar tarjetas limpias con ancho optimizado (`118.dp` en portrait, `110.dp` en landscape) para ver 3+ juegos a la vez.

### 3.3. `OzLibraryScreen.kt` — Adiós a los Filtros Gigantes
- **En `PORTRAIT_PHONE`:**
  1. Cabecera (`36.dp`): `OZ LIBRARY` + contador discreto (`11`) a la izquierda; a la derecha toggle unido Grilla/Lista (`30.dp`, `Icons.Rounded.GridView` / `ViewList`) + menú `⋮` (`30.dp`, `Icons.Rounded.MoreVert`).
  2. `OzHudSegmentedBar` (`34.dp`, scrollable) con `TODOS`, `FAVORITOS`, `RECIENTES`, `DESCARGADOS`, `POR CONSOLA`.
  3. Fila de chips de consola (`30.dp`) usando `OzHudChip(compact = true)`.
  4. Fila Ordenar + Buscar (`36.dp`): botón compacto de orden (`Icons.Rounded.SwapVert`) + `OzHudSearchField`.
  - **Meta:** Al menos **65% de la altura visible** ocupado por la grilla de carátulas sin hacer scroll.
- **En `LANDSCAPE_HANDHELD` (Crítico):**
  - **Colapsar los 4 niveles anteriores en UNA SOLA BARRA SUPERIOR de `40.dp`** (`Row` horizontal):
    - Izquierda: `OzHudSegmentedBar` compacto (o selector rápido de pestaña) + chip desplegable de Consola (`ALL ▾` que abre un `DropdownMenu` biselado con todas las consolas, en vez de gastar una fila entera horizontal).
    - Centro: `OzHudSearchField` flexible (`weight(1f)`).
    - Derecha: botón Ordenar (`34.dp`), toggle Grilla/Lista (`34.dp`) y `⋮` (`34.dp`).
  - Debajo inmediatamente la grilla de **5 columnas** ocupando **≥ 82% de la pantalla**.

### 3.4. Puerta de Verificación — Fase 2
- Verificar con búsqueda que no queden glifos Unicode `"♡"`, `"♥"`, `"✓"`, `"•••"`, `"⌕"` en `OzCoverImage.kt`, `OzHomeScreen.kt` ni `OzLibraryScreen.kt`.
- `.\gradlew.bat testDebugUnitTest --no-daemon` en verde + `assembleDebug` exitoso.

---

## 4. FASE 3 — FICHA DE JUEGO Y UNIFICACIÓN DEL HUD DE DESCARGA

**Archivos objetivo:**
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/GameDetailScreen.kt`
- `android/app/src/main/java/cl/gakuto/kizuna/launch/SessionActivity.kt` *(SOLO la capa visual Compose; NO modificar lógica de descarga, torrent, MiNERVA, ROMgi, RetroArch ni servicios).*

### 4.1. `GameDetailScreen.kt` — Jerarquía de Consola (Cero Emojis, Cero Pills)
- **Corregir la jerarquía invertida actual:** El título y estado del juego van **ARRIBA**, junto a la portada, **antes** de las acciones secundarias.
- **Barra superior discreta (`36.dp`):** Botón `Volver` (`Icons.Rounded.ArrowBack`) + tag de consola (`OzHudTag`) + botón Favorito (`Icons.Rounded.Star` / `StarBorder`).
- **Layout `PORTRAIT_PHONE`:**
  - Bloque Hero con fondo blur de la carátula + scrim oscuro: Portada `3:4` (`116.dp` ancho) a la izquierda; a la derecha **Título en `Oxanium` Bold**, plataforma/año/región y tags biselados (`OzHudTag` — ej. `ROM LOCAL`, `USA` — **cero cápsulas ovaladas**).
  - CTA Principal ancho completo (`44.dp`, gradiente cyan→violeta + glow, `Icons.Rounded.PlayArrow` o `Icons.Rounded.Download`): `JUGAR EN RETROARCH` / `DESCARGAR Y JUGAR`.
  - Fila limpia de acciones secundarias (grilla 2x2 o 4 botones compactos de `36.dp` con **íconos vectoriales**, **ZERO emojis `📡📁🗑️★`**):
    - `FTP` (`Icons.Rounded.WifiTethering` o `Upload`)
    - `iiSU` (`Icons.Rounded.FolderOpen` o `Launch`)
    - `Arte` / `Favorito` (`Icons.Rounded.Image` / `Star`)
    - `Borrar ROM` (`Icons.Rounded.DeleteOutline`, borde en `OzColors.Error` solo si es local).
  - Tarjeta de Saga (si pertenece a una saga) con barra de progreso biselada y link `VER CRONOLOGÍA ›`.
  - Descripción y ficha técnica en `OzHudCard` limpia.
- **Layout `LANDSCAPE_HANDHELD` (2 Columnas):**
  - Columna Izquierda (`34%` del ancho): botón `Volver` + portada grande `3:4` centrada + tags de estado.
  - Columna Derecha (`66%` del ancho, scrolleable): Título + CTA Principal + fila de 4 acciones vectoriales + Saga + Descripción.

### 4.2. `SessionActivity.kt` — Reescritura Visual de la Pantalla de Descarga y Preparación
Esta pantalla seguía congelada en la estética antigua (`OZ GAME VAULT`, `RETROARCH BRIDGE`, cajas redondeadas y métricas cortadas).
- **Regla de Oro:** Mantener **exactamente intactos** todos los `StateFlow`, coroutines, `BackgroundRomDownloadEngine`, validadores, lanzadores de RetroArch, notificaciones y botones de acción (`Pausar`, `Reanudar`, `Cancelar`, `Cerrar pantalla`, reintentos, selección de edición). Reescribir **únicamente los composables de renderizado**.
- **Nuevo Acabado Cyber Vault / Hylian:**
  - Envolver con `OzTheme` respetando `AppPreferences(this).appThemeMode` y `appThemePreset`.
  - Cabecera limpia: `OzBrandEmblem(28.dp)` + `R E T R O R O M` + tag biselado `BÓVEDA · RETROARCH` (reemplazar textos obsoletos `"OZ GAME VAULT"` y `"RETROARCH BRIDGE"`).
  - Tarjeta de juego con `OzHudCard` biselada (portada en `OzChamferShape(8.dp)`, consola en `OzHudTag`, fuente y estado en texto claro sin cortar).
  - **Telemetría sin truncamientos:**
    - Barra de progreso lineal biselada (`height = 8.dp`) con gradiente cyan→violeta.
    - Las 3 cajas de métricas (`DESCARGADO`, `VELOCIDAD`, `RESTANTE`) deben usar etiquetas de `9.5.sp` y valores de `12.sp` con strings concisos (`"Pendiente"`, `"Esperando"`, `"--:--"`, `"1.4 MB/s"`) y `softWrap = false` + ancho equilibrado para que **NUNCA** muestren `"Tamaño pen..."`, `"Esperando d..."` ni `"Disponible a..."`.
  - Botones `PAUSAR` / `REANUDAR` y `MINIMIZAR` / `CERRAR` usando `OzHudButton` biselado (cero botones ovalados).
  - Reemplazar el Toast blanco inicial por un banner sutil integrado en el propio HUD de descarga (solo usar Toast si hay error crítico al cerrar la Activity).
  - **Adaptación `LANDSCAPE_HANDHELD`:** Si `maxWidth > maxHeight && maxHeight < 540.dp`, renderizar en **2 columnas** (Izquierda: carátula + info del juego; Derecha: progreso + 3 métricas + botones de control), visible completo sin necesidad de hacer scroll.

### 4.3. Puerta de Verificación — Fase 3
- Verificar con búsqueda que en `GameDetailScreen.kt` haya **0 emojis** (`📡`, `📁`, `🗑️`, `★`) y **0 `RoundedCornerShape(50)` / `999.dp`**.
- Verificar que en `SessionActivity.kt` no queden textos `"OZ GAME VAULT"`, `"RETROARCH BRIDGE"` ni botones ovalados.
- `.\gradlew.bat testDebugUnitTest --no-daemon` en verde + `assembleDebug` exitoso.

---

## 5. FASE 4 — AJUSTES LIMPIOS, SAGAS Y MODALES UNIFICADOS

**Archivos objetivo:**
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/HylianSettingsScreen.kt`
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/HylianExploreScreen.kt`
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/screens/SagasScreens.kt`
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzArtworkPicker.kt`
- `android/app/src/main/java/cl/gakuto/kizuna/ui/compose/components/OzCollectionPicker.kt`

### 5.1. `HylianSettingsScreen.kt` — Adiós a la Ensalada Visual
- **Eliminar el 100% de badges `"HUD"`** en las esquinas de las tarjetas.
- **Reemplazar todos los `Switch(...)` de Material 3 por `OzHudToggle(...)`**.
- **Cero textos truncados:**
  - En `MODO DE MOTOR GLOBAL`: usar 2 filas de 2 botones (o `FlowRow`) con etiquetas completas y claras: `AUTO`, `RETROARCH`, `EXTERNO`, `PREGUNTAR` (prohibido `RETROA...` o `PREGUN...`).
  - En `ALMACENAMIENTO, CARÁTULAS Y FTP`: botones con etiquetas que quepan limpias en 1 línea (`CARPETA ROMS`, `SYNC CARÁTULAS`, `FTP (N)`, `INTERNET ARCHIVE`) usando `11.sp` `Oxanium` o filas dedicadas de ancho completo cuando el texto sea largo.
- **Jerarquía limpia en 4 bloques ordenados:**
  1. **Apariencia y Pantalla:** Preset (`CYBER VAULT` / `HYLIAN`), Luminosidad (`SISTEMA` / `OSCURO` / `CLARO`), Orientación (`AUTO` / `VERTICAL` / `HORIZONTAL`).
  2. **PC Hub LAN (mDNS):** Estado real en tiempo real + input biselado + `CONECTAR` / `BUSCAR LAN`.
  3. **RetroArch y Emulación:** `OzHudToggle` para *Lanzar al terminar descarga* y *Auto-instalar cores* + selector de motor global sin truncar.
  4. **Almacenamiento, Arte y Fuentes:** Ruta actual legible, `CARPETA ROMS`, `SYNC CARÁTULAS`, Región preferida (`USA`, `WORLD`, `EU`, `JP`), `FTP` e `INTERNET ARCHIVE`.
- **En `LANDSCAPE_HANDHELD`:** Renderizar las 4 tarjetas de ajustes en una **grilla de 2 columnas** (o selector de sección a la izquierda + detalle a la derecha) para aprovechamiento total del ancho.

### 5.2. `HylianExploreScreen.kt` y `SagasScreens.kt` — Colecciones y Sagas Respirables
- **`HylianExploreScreen.kt`:**
  - En `PORTRAIT_PHONE`: `OzHudSegmentedBar` (`SAGAS | CONSOLAS | MINERVA`, `34.dp`) + cabecera compacta + búsqueda + chips de filtro (`30.dp`).
  - En `LANDSCAPE_HANDHELD`: **Colapsar tabs + búsqueda + filtros en una sola barra compacta (`40.dp`)** arriba, y mostrar las tarjetas de Sagas en **grilla de 2 o 3 columnas** inmediatamente debajo (actualmente el contenido quedaba oculto fuera de pantalla).
- **`SagasScreens.kt` (`SagaDetailScreen`):**
  - Barra superior compacta (`36.dp`): `Volver` (`Icons.Rounded.ArrowBack`), `Revisar IA` (`Icons.Rounded.AutoAwesome`), `iiSU` (`Icons.Rounded.FolderOpen`) — **cero emojis (`✨`, `📁`)** y sin tapar el arte de la saga.
  - En `LANDSCAPE_HANDHELD`: Hero de saga horizontal compacto (portada + sinopsis + barra de progreso a lo ancho) + cronología con tarjetas bien alineadas.

### 5.3. `OzArtworkPicker.kt` y `OzCollectionPicker.kt` — Modales Biselados
- Reemplazar todos los `RoundedCornerShape(50)`, `RoundedCornerShape(24.dp)` y `RoundedCornerShape(12.dp)` por `OzChamferShape(...)` y `ozHudBorderBrush(...)` para eliminar los últimos restos de UI antigua en overlays.

### 5.4. Puerta de Verificación Final — Fase 4
- Auditoría global sobre `ui/compose/**`:
  - `0` usos de `GameVaultChestLogo` en pantallas activas.
  - `0` usos de `Switch(` de Material 3.
  - `0` badges `"HUD"`.
  - `0` emojis `📡`, `📁`, `🗑️`, `✨` en botones.
  - `0` `RoundedCornerShape(50)` o `RoundedCornerShape(999.dp)`.
- `.\gradlew.bat testDebugUnitTest --no-daemon` 100% en verde.
- `.\gradlew.bat assembleDebug --no-daemon` exitoso.

---

## 6. COMANDOS DE AUDITORÍA AUTOMÁTICA (POWERSHELL)
Antigravity debe ejecutar esta comprobación al cerrar cada fase relevante y reportar el conteo real (objetivo al finalizar Fase 4: **0 coincidencias**):

```powershell
$uiPath = "C:\OzretroRom\android\app\src\main\java\cl\gakuto\kizuna\ui\compose"
Select-String -Path "$uiPath\**\*.kt" -Pattern 'GameVaultChestLogo|Switch\(|RoundedCornerShape\(50\)|RoundedCornerShape\(999|text\s*=\s*"HUD"' | Select-Object Filename, LineNumber, Line
```
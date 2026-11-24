# Oz RetroRom 0.6.0 Beta 4 — Fix 3

## World Region + Archive Preparation

Fecha de validación: 2026-08-08

### Resumen

Fix 3 corrige dos problemas distintos que coincidían en el flujo iiSU → Oz → ROMgi → RetroArch:

1. **`World` ya no se trata como una región de fuente estricta.** En el catálogo, `World` funciona como una etiqueta regional amplia/global. Si el usuario tiene una preferencia concreta (USA, Europa, Japón o Asia), esa preferencia lidera la resolución. Si la entrada es `World` y ROMgi encuentra una variante compatible USA/Europe/Japan, ese cambio no dispara por sí solo un diálogo de fallback regional.
2. **Los ZIP de plataformas de cartucho se normalizan antes de persistir/lanzar.** ROMgi documenta extracción automática de ZIP/7z configurable por plataforma. Oz todavía podía conservar un ZIP de SNES y entregarlo al lanzamiento externo. El core Snes9x documenta como contenido nativo `.smc`, `.sfc`, `.swc`, `.fig`, `.bs` y `.st`; no enumera `.zip`. Fix 3 extrae un contenido compatible de ZIP antes de guardarlo en la biblioteca o pasarlo al preflight/RetroArch.

La segunda corrección es especialmente relevante para el crash observado en `snes9x_libretro_android.so -> core_load_game()`. El stack nativo por sí solo no prueba qué bytes recibió el core, pero la combinación “ROMgi selecciona `ActRaiser (USA).zip` + Oz externo entrega el archivo al core” era un riesgo real. Fix 3 elimina ese riesgo antes de entrar a código nativo.

### Fuentes primarias consultadas

- ROMgi, repositorio oficial (`caprado/romgi`): documenta filtros regionales US/EU/JP, cola/background/resume y **Archive Extraction — Automatic extraction of downloaded ZIP and 7z archives, configurable per-platform**. También declara `commons-compress` como dependencia Android para extracción ZIP/7z.
- Libretro Docs — Snes9x: el core mainline admite `.smc`, `.sfc`, `.swc`, `.fig`, `.bs`, `.st` como extensiones de contenido.
- libretro-database: el dataset de SNES se importa principalmente desde No-Intro y conserva campos de región en las entradas.
- No-Intro DAT-o-MATIC: `World` existe como clasificación de releases en la metadata; no implica que todo proveedor de archivos deba exponer literalmente una variante con `(World)`.

### Cambios de código

#### `RomgiTitleMatcher.kt`
- Nueva semántica `isBroadRegion()` para `World` y `Other`.
- `regionCompatible()` acepta una fuente regional concreta cuando el catálogo pide una región amplia.
- `fallbackOrder()` conserva una región concreta solicitada como primera opción; con `World/Other`, una preferencia concreta del usuario lidera la búsqueda y luego se usa un orden determinista.

Ejemplos:

- `World + preferencia USA` → USA, World, Europe, Japan, Asia, Other.
- `World + preferencia Europe` → Europe, World, USA, Japan, Asia, Other.
- `USA + preferencia Europe` → USA sigue siendo la solicitud primaria; cambiar a Europe sigue siendo un fallback que requiere confirmación.

#### `RomgiCatalog.kt`
- Las búsquedas `World/Other` no fuerzan coincidencia regional literal.
- `regionFallback` solo se activa cuando la región solicitada era concreta y la fuente final difiere.
- Resultado: `World → USA` ya no muestra el diálogo “la región solicitada fue World...” únicamente por esa diferencia.

#### `RomArchivePreparer.kt` — nuevo
- Detecta plataformas de cartucho que requieren contenido nativo.
- Extrae ZIP mediante `java.util.zip.ZipFile`, sin dependencia nueva.
- Selecciona únicamente entradas con extensión compatible con el sistema.
- Para SNES prioriza contenido nativo compatible con Snes9x.
- Límite de extracción: 4 GiB.
- Rechaza ZIP vacío o sin ROM compatible.
- No modifica los ZIP de Arcade/FBNeo, porque esos romsets deben conservarse como ZIP.
- `.7z` que necesite extracción se detiene de forma controlada; no se entrega a un core nativo. La extracción 7z completa queda pendiente para una iteración posterior.

#### `BackgroundRomDownloadEngine.kt`
- Normaliza el archivo descargado antes de validarlo y persistirlo.
- El batch download de colecciones hereda la corrección porque reutiliza este motor.

#### `SessionActivity.kt`
- Normaliza archivos de archivo antes del preflight.
- También cubre ROMs descargadas por versiones anteriores: si una ROM persistida sigue siendo ZIP y el sistema requiere formato nativo, ya no usa el shortcut directo; pasa por staging/extracción.

#### `RomPreparedFileValidator.kt`
- Un archivo comprimido de cartucho que no haya podido normalizarse ya no puede llegar al core.
- ZIP/7z incompatibles producen error controlado en Oz en vez de permitir el salto a código nativo.

### Compatibilidad y datos

- `applicationId`: `cl.gakuto.kizuna`
- `versionName`: `0.6.0-beta.4`
- `versionCode`: `60007` (Fix 3 histórico; Fix 4 incrementa a 60008)
- Room permanece en schema 6.
- No se añadió migración Room.
- No se usa `fallbackToDestructiveMigration`.
- No se eliminan ROMs existentes.
- No se cambian permisos SAF persistentes.
- No se modifican credenciales IA-S3.
- No se cambian los contratos de iiSU añadidos en Fix 1/2.

### Validación ejecutada

```text
VALIDACIÓN ESTÁTICA CORRECTA — Oz RetroRom 0.6.0 Beta 4 (1040 comprobaciones)
Sistemas: 44 · juegos curados: 192 · colecciones: 18

BETA4_FIX1_BATCH_IISU_OK checks=35
BETA4_FIX2_IISU_LAUNCH_GUARD_OK checks=11
BETA4_FIX3_WORLD_ARCHIVE_OK checks=12
BETA4_IISU_ARCHITECTURE_OK checks=18
FIX3_CONTRACTS_OK
FIX4_CONTRACTS_OK
FIX610_RETROARCH_CORE_FALLBACK_OK checks=17
FIX611_RETROARCH_CONFIG_BRIDGE_OK checks=10
FIX612_UX_CORE_OVERLAYS_OK checks=29
FIX613_FINAL_POLISH_OK checks=46
FIX614_SESSION_DRAWABLE_OK
FIX615_SETTINGS_INTERACTION_OK checks=24 beta4_compat=1
FIX66_COLLECTION_ARTWORK_OK
FIX68_BACKGROUND_DOWNLOAD_OK checks=29
FIX69_PERSISTENT_LIBRARY_LAUNCH_OK checks=10
ROOM_MIGRATION_3_4_OK default=NULL data_preserved=1
ROOM_MIGRATION_5_6_OK default=NONE persisted=DOWNLOADED data_preserved=1
```

### Comprobaciones adicionales

- `RomgiTitleMatcher.kt` compilado de forma aislada junto con `TextNormalizer`; se comprobaron las órdenes de región y el caso ActRaiser World → USA.
- `RomArchivePreparer.kt` compilado con stubs mínimos para validar sintaxis/tipos de la lógica de archivo.
- Parche incremental generado contra el árbol exacto de Beta 4 Fix 2.
- El ZIP final se extrae y vuelve a ejecutar el validador y tests antes de entrega.

### Intento de build real en el entorno de entrega

Comando intentado:

```text
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=1 --console=plain --stacktrace
```

No se alcanzó Gradle porque el proyecto no contiene `gradle-wrapper.jar` y el bootstrap verificado no pudo obtenerlo debido a DNS del entorno:

```text
curl: (6) Could not resolve host: raw.githubusercontent.com
```

Por ello **no se declara BUILD SUCCESSFUL** y no se adjunta APK. La compilación definitiva debe realizarse en el Termux ARM64 del usuario, donde las iteraciones anteriores ya han compilado.

### Pruebas recomendadas en dispositivo

1. Abrir desde Oz un juego con metadata `World`, por ejemplo ActRaiser.
2. Confirmar que si la fuente compatible es USA no aparece el diálogo de fallback solo por `World → USA`.
3. Dejar que Oz descargue `ActRaiser (USA).zip` y comprobar que la biblioteca/lanzamiento termina usando el archivo SNES nativo extraído (`.sfc/.smc/...`).
4. Repetir desde iiSU usando el descriptor virtual.
5. Verificar que iiSU → Oz resuelve el juego real y que Snes9x ya no recibe el descriptor ni el ZIP del proveedor.
6. Probar una solicitud concreta `USA` cuya única fuente sea `Europe`: ese caso **sí debe seguir solicitando confirmación**.
7. Probar un romset Arcade ZIP y confirmar que permanece ZIP (no se extrae).

### QA final de empaquetado

```text
ZIP_EXTRACT_VALIDATION_OK
PATCH_APPLY_CHECK_OK
```

El parche se aplicó sobre el árbol exacto de Beta 4 Fix 2, y el árbol resultante volvió a obtener 1040 comprobaciones y `BETA4_FIX3_WORLD_ARCHIVE_OK checks=12`. El ZIP final fue extraído desde cero y se ejecutaron todos los `scripts/test_*.py` sin fallos.

# Informe de investigación — Oz RetroRom 0.6.0 Beta 3

Fecha de corte: **6 de agosto de 2026**. Se priorizaron documentación y repositorios oficiales. Cuando una API de frontend no estaba publicada, la implementación se limitó a detección o resolución en tiempo de ejecución.

## 1. Android moderno

### Decisiones

- Las ROM elegidas por el usuario se reciben con Storage Access Framework y permisos persistentes cuando corresponde.
- Los emuladores externos reciben `content://` y permiso temporal de lectura; no se les envían rutas privadas reconstruidas.
- La visibilidad de paquetes se limita con `<queries>`; no se solicita `QUERY_ALL_PACKAGES`.
- Antes de `startActivity`, Oz exige que Android resuelva una actividad exportada.
- Oz no escribe en carpetas privadas de RetroArch ni de otros emuladores.

### Fuentes primarias

- Android Storage Access Framework: https://developer.android.com/guide/topics/providers/document-provider
- Persistencia de permisos URI: https://developer.android.com/training/data-storage/shared/documents-files
- Package visibility: https://developer.android.com/training/package-visibility
- Seguridad de intents: https://developer.android.com/privacy-and-security/risks/implicit-intent-hijacking

## 2. RetroArch y Libretro

El código Android oficial exporta `RetroActivityFuture`, `CoreSideloadActivity` y el receptor de cores instalados. Su `MainMenuActivity` obtiene `dataDir` y APK desde `ApplicationInfo` y entrega los extras `ROM`, `LIBRETRO`, `DATADIR`, `APK`, `SDCARD`, `EXTERNAL` e `IME`. Beta 3 replica solo parámetros verificables y obtiene las rutas desde PackageManager.

Una carpeta SAF no se considera carpeta privada de cores. El `.so` se prepara en almacenamiento compartido y se importa mediante `CoreSideloadActivity`. En release, la descarga automática queda bloqueada salvo que `trusted_cores.json` contenga URL y SHA-256 aprobados.

Fuentes:

- Instalación Android: https://docs.libretro.com/guides/install-android/
- Gestión de cores: https://docs.libretro.com/guides/download-cores/
- RetroArch Android: https://github.com/libretro/RetroArch/tree/master/pkg/android/phoenix
- Buildbot de cores: https://buildbot.libretro.com/nightly/android/latest/

## 3. Emuladores externos

### PPSSPP

El manifiesto oficial exporta `org.ppsspp.ppsspp.PpssppActivity` con `ACTION_VIEW` para `file` y `content`. Se codificó actividad explícita y se restringió Oz a `content://`. Formatos declarados: ISO, CSO, CHD, ELF y PBP. PPSSPP no requiere BIOS externa.

Fuente: https://github.com/hrydgard/ppsspp

### melonDS

El proyecto documenta el paquete `me.magnum.melonds`, `me.magnum.melonds.ui.emulator.EmulatorActivity`, URI de contenido y permiso de lectura. El ROM debe estar indexado por melonDS; ZIP y 7Z están contemplados por su flujo documentado.

Fuente: https://github.com/rafaelvcaetano/melonDS-android

### Dolphin

El proyecto oficial requiere Android 7+ y ARM64. El paquete `org.dolphinemu.dolphinemu` está verificado en Gradle. La actividad de emulación no aparece exportada en el manifiesto actual; por eso Oz detecta/abre la aplicación, pero no inventa un intent para iniciar un juego.

Fuente: https://github.com/dolphin-emu/dolphin

### Play! y PS2

El paquete Android oficial de Play! es `com.virtualapplications.play`, con ARM64 y target SDK moderno. El core Play! soporta Android y formatos de disco documentados. LRPS2 declara que no funciona nativamente en ARM y no está disponible en Android; por eso Beta 3 reemplaza PCSX2/LRPS2 por `play_libretro_android.so`.

No se encontró una API oficial estable para lanzar directamente la app Play! desde otro frontend, por lo que se usa como **solo abrir app**.

Fuentes:

- Play!: https://github.com/jpd002/Play-
- Core Play!: https://docs.libretro.com/library/play/
- LRPS2: https://docs.libretro.com/library/lrps2/

### NetherSX2

El repositorio de parche comunitario sigue activo, pero no distribuye un APK propio por restricciones de licencia y no publica paquete/actividad/API estable como contrato primario. Se investigó y documentó, pero no se codificó una integración.

Fuente: https://github.com/Trixarian/NetherSX2-patch

### Nintendo 3DS

Azahar es el proyecto vigente investigado y publica el paquete vanilla `org.azahar_emu.azahar`. La app ha trabajado correcciones de intent, pero Beta 3 no fija una actividad: Android debe resolver `ACTION_VIEW` en la versión instalada. Citra histórico no se trata como recomendación actual independiente.

Fuente: https://github.com/azahar-emu/azahar

### DuckStation / SwanStation

DuckStation es la aplicación independiente; SwanStation es un core de RetroArch. El paquete Android de DuckStation se detecta, pero el lanzamiento solo se ofrece si la instalación responde a un intent exportado. No se presenta SwanStation como APK.

Fuentes:

- DuckStation: https://github.com/stenzek/duckstation
- Libretro cores/buildbot: https://buildbot.libretro.com/nightly/android/latest/

### Flycast y Yaba Sanshiro 2

Sus proyectos se consideran mantenidos, pero la documentación primaria disponible no define un contrato Android de frontend suficientemente preciso. Los paquetes usados por Oz se marcan como apoyados por documentación secundaria y nunca bastan por sí solos: Android debe resolver un `ACTION_VIEW` exportado. Si no, Oz abre la aplicación o usa RetroArch.

Fuentes:

- Flycast: https://github.com/flyinghead/flycast
- Yaba Sanshiro: https://www.uoyabause.org/
- Referencia secundaria de frontend: https://gitlab.com/es-de/emulationstation-de/-/blob/master/ANDROID.md

### DraStic

No se encontró un repositorio/documentación primaria mantenida que publique paquete, actividad e intent como API estable de terceros. No se codificó integración. melonDS queda como ruta legítima documentada para DS.

## 4. ROMgi

El repositorio oficial describe catálogo SQLite, búsqueda/filtros regionales, HTTP y torrent selectivo por archivo, cola, reintento y biblioteca. Beta 3 conserva la automatización y añade una capa de resolución tolerante sin pedir URLs o magnets al usuario.

La lógica implementada:

1. exacto con región;
2. exacto sin región;
3. canónico;
4. alias;
5. puntuación/artículos/subtítulos;
6. reordenamiento;
7. fallback regional;
8. fuzzy final.

Las fuentes HTTP sin cifrar se bloquean. En ambigüedad, se muestra selección explícita.

Fuente: https://github.com/caprado/romgi

## 5. iiSU

iiSU se mantiene como frontend Android en desarrollo activo. Su README confirma que es frontend, no emulador, y que configura emuladores por consola. El repositorio público no documenta como API formal el formato privado de pack/comando usado por esta versión; por eso Beta 3 conserva sin regresiones el flujo ya validado por el proyecto: carpetas virtuales, descriptores, `%ROM%`, comando explícito y Bridge con `cl.gakuto.kizuna`.

Fuente: https://github.com/iisu-network/iiSU

## 6. FinalBurn Neo

La documentación Libretro exige romsets compatibles con el core y recomienda usar el DAT generado por la versión correspondiente. Renombrar un ZIP no convierte un set incompatible. Beta 3 exige el nombre exacto, un ZIP con componentes reales, BIOS del mismo set y bloquea `oz-retrorom.json` como ROM. Si el nombre no coincide, detiene el lanzamiento; si el core informa archivos faltantes, la UI indica actualizar el set con el DAT de esa versión.

Fuentes:

- FBNeo: https://github.com/finalburnneo/FBNeo
- Documentación Libretro FBNeo: https://docs.libretro.com/library/fbneo/

## 7. Limitaciones e inferencias declaradas

- La existencia de un paquete no demuestra que acepte ROMs externas.
- DuckStation/Azahar/Flycast/Yaba solo se lanzan si PackageManager confirma el intent de la instalación concreta.
- Dolphin y Play! se detectan como aplicaciones, pero no reciben ROM directa.
- El buildbot cambia; una release segura requiere hashes fijados. El catálogo Beta 3 se entrega vacío antes que publicar hashes no verificados.
- No se certifica compatibilidad física Android 15 hasta ejecutar pruebas instrumentadas en dispositivo.

## Fix 3: Internet Archive transport policy

ROMgi's official repository documents direct HTTP, selective torrent downloads and persistent Internet
Archive login. Its primary authentication source obtains keys from `/account/s3.php`, applies cookies
for Archive downloads and uses LOW authorization as fallback. Oz follows those documented mechanisms.
The preference for authenticated HTTPS over torrent is an Oz-specific engineering decision, not a claim
that ROMgi requires that ranking. See `docs/FIX3_RESEARCH_AND_VALIDATION.md`.


# Notas de versión — Oz RetroRom 0.6.0 Beta 3

## Versión pública

- `versionCode`: `60003`
- `versionName`: `0.6.0-beta.3`
- nombre visible: **Oz RetroRom 0.6.0 Beta 3**
- `applicationId`: `cl.gakuto.kizuna` sin cambios

## ROMgi

- Resolvedor escalonado por título y región.
- Normalización de dos puntos, guiones, apóstrofes tipográficos, artículos, subtítulos y espacios.
- Alias y títulos reordenados antes de recurrir a fuzzy matching.
- Fallback configurable: región original/preferida, World, USA, Europe, Japan, Asia y otras.
- Confirmación visible con título, región, estrategia, confianza, archivo y transporte.
- Diálogo obligatorio cuando varias opciones tienen puntuaciones próximas.
- HTTP sin cifrar excluido; HTTPS y torrent selectivo conservados.
- Corrección específica: “World” dentro del nombre **Super Mario World** ya no se interpreta como metadato regional.

## Interfaz y filtros

- Búsqueda con espacio visual propio: “Buscar juegos, sagas o plataformas”.
- Acciones rápidas separadas: catálogo global, configuración, carpeta virtual, sincronizar iiSU y juego aleatorio.
- Filtros movidos a un bottom sheet con Limpiar, Cancelar y Aplicar.
- Chips compactos para filtros activos.
- Consultas SQL sobre toda la base, paginación, debounce y cancelación de consultas previas.
- Dedupe regional en SQLite antes de paginar, conservando el orden global seleccionado.

## Motores y emuladores

- Preferencia por sistema: Automático, RetroArch, Emulador externo o Preguntar.
- Detección de paquetes visibles e intents exportados.
- Integraciones explícitas verificadas: PPSSPP y melonDS.
- Dolphin y Play! se detectan y pueden abrirse, pero no reciben una ROM directamente cuando su API no está documentada/exportada.
- DuckStation, Azahar, Flycast y Yaba Sanshiro 2 solo aparecen como lanzamiento directo si Android resuelve realmente `ACTION_VIEW`.
- PS2 usa `play_libretro_android.so`; LRPS2 no se usa en Android/ARM.
- NetherSX2 y DraStic no reciben integración inventada.

## RetroArch, iiSU y FinalBurn Neo

- RetroArch nativo, variantes, core requerido, consulta de cores y `CoreSideloadActivity` preservados.
- Las rutas privadas se obtienen desde `ApplicationInfo.dataDir`; no se reconstruyen `/data/user/...`.
- Una carpeta SAF sirve como preparación/importación, no como carpeta privada ejecutable de RetroArch.
- iiSU conserva sincronización, carpetas/descriptores virtuales, pack, comando, parámetro ROM y Bridge.
- FBNeo conserva romset exacto, ZIP real, BIOS, validación y mensaje de incompatibilidad de set/revisión.

## Seguridad y rendimiento

- Release no depurable, R8, shrink resources, cleartext bloqueado, intents explícitos, límites de archivo, censura diagnóstica y SHA-256 de cores.
- Room v4 con índices para últimos jugados, añadidos y disponibilidad ROMgi.
- Sin carga masiva de catálogos; RecyclerView y artwork permanecen acotados.

## Device correction — Download & Filters Fix 3

- Added encrypted Internet Archive login inside Settings.
- Authenticated HTTPS downloads now outrank torrent metadata discovery.
- Torrent metadata timeout reduced to 45 seconds.
- Fixed World/France source-region confusion by making the concrete filename authoritative.
- Removed unnecessary confirmation for exact same-region matches.
- Fixed collection ambiguity choices on MIUI.
- Removed the visible Share diagnostic action.
- Applied the modern Room-backed filter sheet to Global Catalog.


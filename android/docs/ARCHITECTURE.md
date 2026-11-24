# Arquitectura — Oz RetroRom 0.6.0 Beta 3

## Flujo principal

```text
Pantalla principal
  ├─ búsqueda con debounce
  ├─ filtros separados
  ├─ acciones rápidas
  └─ Room paginado
          ↓
CrtGameDialog / iiSU Bridge
          ↓
SessionActivity
  ├─ biblioteca local / SAF
  ├─ ROMgi escalonado
  └─ descriptor virtual iiSU
          ↓
PublicSessionStore + validaciones
          ↓
Selector de motor por sistema
  ├─ RetroArch nativo
  ├─ emulador externo verificado
  └─ preguntar al jugar
```

## Catálogo y filtros

Room v4 es la fuente de verdad. `CatalogPagingSql` construye consultas `SupportSQLiteQuery` con búsqueda, colección, regiones, familias, plataformas, géneros, estado y orden. La variante regional preferida se selecciona en SQLite antes de `LIMIT/OFFSET`; la interfaz nunca necesita cargar miles de filas para filtrar o deduplicar.

`IncrementalGameLoader` mantiene una ventana acotada, `MainActivity` cancela el trabajo anterior al cambiar búsqueda/filtros y `ArtworkLoader` limita memoria con caché LRU.

## Resolución ROMgi

`RomgiTitleMatcher` aplica niveles deterministas:

1. título y región exactos;
2. título exacto sin región;
3. título canónico;
4. alias;
5. puntuación tolerante;
6. orden alternativo;
7. fallback regional;
8. fuzzy final.

La región solo se reconoce desde metadatos delimitados. `RomgiCatalog` puntúa las fuentes y devuelve `RomgiResolution`; `SessionActivity` exige confirmación visible y selección manual cuando existe ambigüedad.

## Motores

`EmulatorProfiles` contiene el perfil interno por sistema. `AppPreferences` conserva `AUTO`, `RETROARCH`, `EXTERNAL` o `ASK`. `ExternalEmulatorManager` usa package visibility, `content://`, permiso temporal, paquete explícito y `resolveActivity`; solo inicia una actividad exportada.

RetroArch se mantiene como motor universal. `RetroArchManager` detecta variantes, consulta cores mediante su receptor oficial, obtiene `dataDir`/`sourceDir` desde `ApplicationInfo`, lanza `RetroActivityFuture` e importa cores preparados mediante `CoreSideloadActivity`.

## iiSU

`IisuExporter` conserva carpetas virtuales, descriptores, assets, pack y comando. `SessionActivity` permanece exportada únicamente para la acción explícita de Oz y recibe el extra `ROM`. El `applicationId` continúa siendo `cl.gakuto.kizuna`.

## Descarga y progreso

`AuthorizedSourceDownloader.Progress` transporta etapa, bytes, total, velocidad, pares, archivo, tiempo, ETA y estancamiento. La pantalla no inventa porcentajes antes de conocer el tamaño. ROMgi conserva HTTPS, torrent selectivo, caché, reintento y fuente alternativa.

## FinalBurn Neo

El flujo exige romset exacto, ZIP real y contenido no vacío. Los descriptores virtuales no se entregan al core. Las BIOS se preparan temporalmente y un set incompatible se bloquea con un mensaje explícito.

## Seguridad

- release no depurable;
- R8 y reducción de recursos;
- tráfico HTTP sin cifrar bloqueado;
- package visibility limitada;
- intents validados;
- SAF y permisos URI;
- límites de descriptor/archivo;
- diagnósticos censurados;
- SHA-256 para cores aprobados;
- firma y alineación verificadas por el script release.

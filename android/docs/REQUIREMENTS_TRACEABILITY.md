# Trazabilidad de requisitos — Oz RetroRom 0.6.0 Beta 3

| Criterio | Implementación / evidencia |
|---|---|
| World, alias y fallback regional | `RomgiTitleMatcher`, `RomgiTitleMatcherTest`, prueba Kotlin aislada |
| No selección regional silenciosa | `RomgiResolution.ambiguous` + `SessionActivity.chooseRomgiSource()` |
| Título/región/método visibles | diálogo de confirmación ROMgi con `entryTitle`, `matchedRegion`, estrategia y confianza |
| Pantalla principal no comprimida | `activity_main.xml`: encabezado, búsqueda, filtros, acciones, chips y lista |
| Panel separado | `CatalogFilterSheet` + `sheet_catalog_filters.xml` |
| Búsqueda/filtros sobre todo el catálogo | `CatalogPagingSql` antes de `LIMIT/OFFSET` |
| Regiones, plataformas, géneros, estado y orden | `CatalogFilters.kt` y SQL dinámico |
| Paginación y cancelación | `IncrementalGameLoader`, `pageJob?.cancel()` y debounce |
| Motores por sistema | `EngineMode`, `EmulatorProfiles`, `EmulatorSettingsActivity` |
| Detectar instalados | `ExternalEmulatorManager.isInstalled()` y `<queries>` limitado |
| Elegir mejor opción instalada | `selectedOrRecommended()` + flujo `AUTO` de `SessionActivity` |
| Probar archivo mediante SAF | `ActivityResultContracts.OpenDocument`, URI visible y validación de extensión |
| No inventar intents | explícito solo PPSSPP/melonDS; los demás se resuelven en el dispositivo o quedan `APP_ONLY` |
| RetroArch nativo | variantes, consulta de cores, `RetroActivityFuture`, `CoreSideloadActivity` |
| Core ausente | detección, importación segura y catálogo SHA-256; release no descarga un core no fijado |
| iiSU sin regresión | extra `ROM`, `ACTION_PLAY`, exportación virtual, pack, comando y Bridge |
| `applicationId` intacto | `cl.gakuto.kizuna` en Gradle, manifiesto y comando iiSU |
| ROMgi automático | DB oficial, HTTPS/torrent selectivo, caché, reintento y biblioteca local |
| Sin URL/magnet manual | no existen campos de entrada manual en la interfaz |
| Progreso Beta 2 | etapa, tiempo, archivo, porcentaje, bytes, velocidad, pares, ETA, estancamiento, reintento, diagnóstico y cancelar |
| FinalBurn Neo | nombre exacto, ZIP real, BIOS, validación y bloqueo del descriptor virtual |
| Seguridad | cleartext bloqueado, R8, shrink, validación de intents, límites, redacción y SHA-256 |
| Versión pública | `60003`, `0.6.0-beta.3`, nombre Oz RetroRom 0.6.0 Beta 3 |
| Validación estática | 514 comprobaciones correctas |
| APK | no generada: wrapper bloqueado por DNS antes de Gradle; error documentado |

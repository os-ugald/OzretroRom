# OzretroRom — Reporte Técnico de Fase 2, Hito 2B (`Desacoplamiento Modular y SSOT`)

**Fecha:** 2026-11-24  
**Estrategia aplicada:** Desacoplamiento modular y Fuente Única de Verdad (SSOT), manteniendo intactas las vistas principales de `ui.html` y Jetpack Compose antes de iniciar la Fase 3.

---

## 1. Resumen Ejecutivo

En el Hito 2B se completó la separación de responsabilidades y la eliminación de duplicación estructural entre `/python-hub` y `/android`:

1. **Fuente Única de Verdad para Sagas (`shared/sagas_catalog.json`):** Se extrajeron las `47` sagas curadas, reglas de emparejamiento/exclusión, sinopsis y las `28` entradas `libretroArt` hacia un catálogo JSON compartido servido en `GET /api/v1/sagas/catalog`, con copias embebidas de respaldo para PyInstaller (`python-hub/app/data/sagas_catalog.json`) y Android/tests JVM (`android/app/src/main/resources/sagas_catalog.json`).
2. **Descomposición de `OzViewModel.kt`:** El ViewModel monolítico se dividió en `5` titulares/ViewModels cohesivos por dominio (`HomeViewModel`, `CatalogViewModel`, `SagasViewModel`, `HubSyncViewModel`, `SettingsViewModel`), conservando `OzViewModel` como fachada compatible para las pantallas actuales.
3. **Migración de `AppPreferences.kt` a DataStore + Room v7:** Se eliminó el uso directo de `SharedPreferences("oz_retrorom")`, delegando preferencias de aplicación en `OzSettingsDataStore` (Jetpack DataStore con `SharedPreferencesMigration("oz_retrorom")`) y metadatos sincronizables por juego en `GameMetadataStore` (Room v7 `SyncMetadataDao`).
4. **Eliminación de Polling por Flujos Reactivos y SSE:** `CatalogPreparationTaskStore`, `CollectionBatchTaskStore`, `CatalogViewModel`, `AuthorizedSourceDownloader` (mediante `AlertListener` + `MutableSharedFlow`) y la interfaz web (`api-client.js` sobre `EventSource('/api/v1/events')`) pasaron a consumir eventos reactivos/push en lugar de bucles periódicos por temporizador.
5. **Modularización del Backend y Frontend Web:** `main.py` se dividió en `6` módulos `APIRouter` (+ `schemas.py` y `__init__.py`), y `ui.html` se desacopló en `styles.css` y `8` ES Modules nativos bajo `static/js/`.

---

## 2. Reducción de Monolitos (Antes vs. Después en LOC)

| Archivo Monolítico | Antes (LOC) | Después (LOC) | Variación | Módulos Extraídos / Reemplazo |
| :--- | :---: | :---: | :---: | :--- |
| `python-hub/app/sagas.py` | `2,166` | `426` | `-80.3%` | `shared/sagas_catalog.json` (`6,999` LOC) + `python-hub/app/data/sagas_catalog.json` |
| `android/.../sagas/SagasRepository.kt` | `1,472` | `634` | `-56.9%` | `android/app/src/main/resources/sagas_catalog.json` + recarga remota via `GameVaultHubClient` |
| `python-hub/app/main.py` | `1,239` | `183` | `-85.2%` | `app/routers/`: `schemas.py` (`209`), `library.py` (`460`), `catalog.py` (`133`), `sagas.py` (`116`), `covers.py` (`135`), `transfers.py` (`127`), `sync.py` (`113`), `__init__.py` (`17`) |
| `python-hub/app/static/ui.html` | `6,800` | `1,288` | `-81.1%` | `styles.css` (`1,351`) + `js/`: `api-client.js` (`50`), `state.js` (`360`), `app.js` (`676`), `views/home.js` (`668`), `views/sagas.js` (`441`), `views/library.js` (`974`), `views/catalog.js` (`597`), `views/settings.js` (`571`) |
| `android/.../ui/compose/OzViewModel.kt` | `1,289` | `277` | `-78.5%` | `ui/compose/domain/`: `HomeViewModel.kt` (`300`), `CatalogViewModel.kt` (`694`), `SagasViewModel.kt` (`147`), `HubSyncViewModel.kt` (`204`), `SettingsViewModel.kt` (`255`) |
| `android/.../data/AppPreferences.kt` | `703` | `644` | `-8.4%` *(fachada)* | `OzSettingsDataStore.kt` (`295`) + `GameMetadataStore.kt` (`120`) |

---

## 3. Detalle por Eje Técnico

### 3.1 SSOT para Sagas (`shared/sagas_catalog.json`)
- **Esquema compartido (`6,999` LOC):** Centraliza `47` sagas curadas (`CURATED_SAGAS`), sinopsis narrativas, patrones de coincidencia/exclusión, plataformas válidas y el diccionario de arte Libretro (`28` claves en `libretroArt`).
- **Backend (`python-hub/app/sagas.py`, `426` LOC; `app/routers/sagas.py`, `116` LOC):** Expone `get_sagas_catalog_payload()` y el endpoint `GET /api/v1/sagas/catalog`, cargando el JSON compartido (o su copia empaquetada `app/data/sagas_catalog.json`) sin listas duplicadas en código Python.
- **Android (`SagasRepository.kt`, `634` LOC; `GameVaultHubClient.kt`):** Parsea el recurso embebido `sagas_catalog.json`, habilita actualización dinámica mediante `reloadFromJson(...)` y agrega `GameVaultHubClient.fetchSagasCatalog(baseUrl)` para sincronizar definiciones desde el PC Hub sin recompilar la APK.

### 3.2 Descomposición de `OzViewModel.kt` en 5 Dominios
- **`HomeDomainHolder` / `HomeViewModel` (`300` LOC):** Selección de juego, recomendación diaria/inteligente, estado de inicio en RetroArch o emulador externo, lectura NFC y flujo de estado de juego activo.
- **`CatalogDomainHolder` / `CatalogViewModel` (`694` LOC):** Sistemas Libretro, búsqueda y filtros de catálogo, preparación del catálogo completo, colecciones personalizadas, paginación Paging 3 de biblioteca, descarga de colecciones y sincronización de carátulas.
- **`SagasDomainHolder` / `SagasViewModel` (`147` LOC):** Construcción reactiva de `SagaSnapshot` y auditoría de sagas con Gemini AI.
- **`HubSyncDomainHolder` / `HubSyncViewModel` (`204` LOC):** Emparejamiento mDNS, estado SSE del PC Hub, envío de ROMs/carátulas/FTP y sincronización incremental Room ↔ PC Hub.
- **`SettingsDomainHolder` / `SettingsViewModel` (`255` LOC):** Carpetas SAF, modo visual, motor/orientación, SteamGridDB, integración iiSU y preferencias reactivas respaldadas por DataStore.
- **`OzViewModel` (`277` LOC):** Delega propiedades y acciones hacia los cinco *holders* de dominio, preservando compatibilidad binaria y de nombres con las pantallas actuales.

### 3.3 Migración de `AppPreferences.kt` a Jetpack DataStore + Room v7
- **`OzSettingsDataStore` (`295` LOC):** Envoltorio sobre `PreferenceDataStoreFactory` (`androidx.datastore:datastore-preferences:1.1.4`) con `SharedPreferencesMigration("oz_retrorom")` para conservar instalaciones existentes, snapshot sincronizado bajo lock (`pendingValues` por secuencia para evitar pérdida de escrituras concurrentes durante hidratación inicial), flujos `Flow` reactivos y método `flush()`.
- **`GameMetadataStore` (`120` LOC):** Caché write-through sobre Room v7 (`SyncMetadataDao`) para URL/URI de carátula personalizada, título/repo/URI local sincronizado, sinopsis cacheada y metadatos por juego.
- **`AppPreferences.kt` (`644` LOC):** Fachada limpia sobre `OzSettingsDataStore` y `GameMetadataStore`, sin acceso directo a `SharedPreferences("oz_retrorom")`.

### 3.4 Sustitución de Polling por Flujos Reactivos y SSE
- **`CatalogPreparationTaskStore` y `CollectionBatchTaskStore`:** Exponen `StateFlow` observables (`CatalogPreparationTaskStore.observe(context)` y `CollectionBatchTaskStore.observe(context, collectionId)`), consumidos directamente desde `CatalogDomainHolder` sin bucles periódicos por temporizador.
- **`AuthorizedSourceDownloader`:** Sustituyó bucles de espera activa por `MutableSharedFlow<Unit>` alimentado desde el `AlertListener` de `libtorrent4j` (`withTimeoutOrNull` reactivo a eventos del torrente).
- **Web UI (`/python-hub`):** `api-client.js` conecta `EventSource('/api/v1/events')` (`connectHubEvents`) y `app.js` despacha los eventos `covers.progress`, `covers.complete`, `download.state`, `download.progress`, `transfer.state`, `transfer.progress`, `library.updated` y `sync.updated` a las vistas modulares.

### 3.5 Modularización de `/python-hub` (Routers + ES Modules)
- **Routers FastAPI (`python-hub/app/routers/`):** Separación limpia de esquemas y contexto (`schemas.py`), biblioteca y archivos (`library.py`), catálogo IA/búsqueda (`catalog.py`), sagas y JSON SSOT (`sagas.py`), sincronización de carátulas (`covers.py`), transferencias FTP (`transfers.py`) y bus SSE + deltas (`/sync.py`). `main.py` queda en `183` LOC.
- **Frontend modular (`python-hub/app/static/`):** Extracción completa del CSS a `styles.css` (`1,351` LOC), estado compartido y registro de acciones en `js/state.js` (`360` LOC), cliente HTTP/SSE en `js/api-client.js` (`50` LOC), orquestador en `js/app.js` (`676` LOC) y vistas en `js/views/{home,sagas,library,catalog,settings}.js`, reduciendo `ui.html` de `6,800` a `1,288` LOC.

---

## 4. Resultados de Verificación

### 4.1 Backend `/python-hub`
- **Tests (`pytest -q`):** `23 passed, 0 failed` (`69.23s`).
  - Incluye el nuevo test `test_shared_sagas_catalog_endpoint_and_modular_static_ui` en `python-hub/tests/test_hub.py`, que valida la paridad de `GET /api/v1/sagas/catalog`, los módulos JS bajo `/static/js/` y `/static/styles.css`.
- **Validación sintáctica JS (`node --check`):** Los `8` módulos ES bajo `python-hub/app/static/js/` pasaron con `0` errores.

### 4.2 Cliente `/android`
- **Tests (`.\gradlew.bat testDebugUnitTest --no-daemon`):** `BUILD SUCCESSFUL in 3m 51s` — **`278` tests ejecutados, `0` fallos, `0` errores, `0` omitidos**.
  - Incluye las nuevas suites agregadas en Hito 2B:
    - `SagasCatalogJsonTest`: carga del JSON compartido embebido y recarga dinámica `reloadFromJson`.
    - `OzSettingsDataStoreAndTaskFlowsTest`: persistencia reactiva en DataStore, almacenamiento por juego en Room v7 y emisión en `StateFlow` de `CatalogPreparationTaskStore` / `CollectionBatchTaskStore`.
    - `DomainViewModelsTest`: inicialización y mutación reactiva de `SettingsViewModel`, `HubSyncViewModel`, `SagasViewModel`, `CatalogViewModel`, `HomeViewModel` y fachada `OzViewModel`.
- **Nota de compatibilidad de tests JVM:** En `LibretroCatalogRepository.kt` se agregó resolución de respaldo vía `ClassLoader` / `src/main/assets/libretro_systems.json` cuando `context.assets` no monta recursos en Robolectric sin `isIncludeAndroidResources = true`, manteniendo intacto el comportamiento en Android.

---

## 5. Estado del Repositorio y Preparación para Fase 3

- Todos los cambios permanecen únicamente en el árbol de trabajo local de `C:\OzretroRom` (no se ejecutó `git commit` ni `git push`), listos para revisión en GitHub Desktop.
- La base arquitectónica (IPC SSE + mDNS + SQLite/Room v7 + SSOT Sagas + DataStore + ViewModels por dominio + ES Modules + APIRouters) queda verificada en verde (`23/23` Python y `278/278` Android) y lista para iniciar el rediseño visual de **Fase 3**.

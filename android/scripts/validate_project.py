#!/usr/bin/env python3
"""Static acceptance validator for Oz RetroRom Android 0.6.0 Beta 4.

This validator checks source/configuration invariants without claiming that Android/Gradle
compilation succeeded. Run the Gradle tasks separately for build certification.
"""
from __future__ import annotations

from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main"
JAVA = APP / "java" / "cl" / "gakuto" / "kizuna"
RES = APP / "res"
ASSETS = APP / "assets"
ERRORS: list[str] = []
CHECKS = 0


def fail(message: str) -> None:
    ERRORS.append(message)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except Exception as exc:
        fail(f"No se pudo leer {path.relative_to(ROOT)}: {exc}")
        return ""


def require(path: Path, *tokens: str) -> None:
    global CHECKS
    text = read(path)
    for token in tokens:
        CHECKS += 1
        if token not in text:
            fail(f"{path.relative_to(ROOT)}: falta {token!r}")


def forbid(path: Path, *tokens: str) -> None:
    global CHECKS
    text = read(path)
    for token in tokens:
        CHECKS += 1
        if token in text:
            fail(f"{path.relative_to(ROOT)}: contiene valor prohibido {token!r}")


def require_regex(path: Path, pattern: str, description: str) -> None:
    global CHECKS
    CHECKS += 1
    if not re.search(pattern, read(path), flags=re.MULTILINE | re.DOTALL):
        fail(f"{path.relative_to(ROOT)}: no cumple {description}")


def load_json(path: Path):
    try:
        return json.loads(read(path))
    except Exception as exc:
        fail(f"JSON inválido {path.relative_to(ROOT)}: {exc}")
        return []


def require_files(*paths: Path) -> None:
    global CHECKS
    for path in paths:
        CHECKS += 1
        if not path.is_file():
            fail(f"Falta archivo requerido: {path.relative_to(ROOT)}")


# Syntax and resource integrity.
for path in sorted(RES.rglob("*.xml")):
    CHECKS += 1
    try:
        ET.parse(path)
    except Exception as exc:
        fail(f"XML inválido {path.relative_to(ROOT)}: {exc}")
for path in sorted(ASSETS.glob("*.json")):
    load_json(path)
CHECKS += 1
try:
    ET.parse(APP / "AndroidManifest.xml")
except Exception as exc:
    fail(f"AndroidManifest inválido: {exc}")

systems = load_json(ASSETS / "libretro_systems.json")
games = load_json(ASSETS / "seed_games.json")
collections = load_json(ASSETS / "featured_collections.json")
trusted_cores = load_json(ASSETS / "trusted_cores.json")
CHECKS += 5
if len(systems) != 44:
    fail(f"Se esperaban 44 sistemas; se encontraron {len(systems)}")
if len({item.get('id') for item in systems}) != len(systems):
    fail("Hay sistemas duplicados")
if len({item.get('id') for item in games}) != len(games):
    fail("Hay juegos curados duplicados")
if not {"all-games", "favorites", "my-collection", "downloaded-library"}.issubset({item.get("id") for item in collections}):
    fail("Faltan colecciones base o la biblioteca descargada")
if not isinstance(trusted_cores, dict) or not isinstance(trusted_cores.get("cores"), list):
    fail("trusted_cores.json debe contener un objeto versionado con una lista cores")

# Versioning and release hardening.
build = ROOT / "app" / "build.gradle.kts"
require(
    build,
    'applicationId = "cl.gakuto.kizuna"',
    "versionCode = 60018",
    'versionName = "0.6.0-beta.4"',
    "compileSdk = 36",
    "targetSdk = 36",
    "minSdk = 26",
    "isMinifyEnabled = true",
    "isShrinkResources = true",
    'abiFilters += "arm64-v8a"',
)
require(ROOT / "gradle.properties", "android.useAndroidX=true")

# Room v6, indexed state, paged full-database filters and global ordering.
entities = JAVA / "data" / "Entities.kt"
database = JAVA / "data" / "OzDatabase.kt"
paging = JAVA / "data" / "CatalogPaging.kt"
dao = JAVA / "data" / "Daos.kt"
repo = JAVA / "data" / "LibraryRepository.kt"
require(entities, 'Index("lastPlayedAt")', 'Index("addedAt")', 'Index("romgiAvailable")', "lastPlayedAt", "addedAt", "romgiAvailable")
require_regex(entities, r"@Entity\(\s*tableName = \"games\".*?\)\s*data class GameEntity", "GameEntity must remain a Room entity")
require(database, "version = 6", "MIGRATION_4_5", "MIGRATION_5_6", "ALTER TABLE games ADD COLUMN lastPlayedAt INTEGER DEFAULT NULL", "ALTER TABLE games ADD COLUMN addedAt", "ALTER TABLE games ADD COLUMN romgiAvailable", "ALTER TABLE games ADD COLUMN libraryOrigin", "addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)")
forbid(database, "fallbackToDestructiveMigration")
require(paging, "SupportSQLiteQuery", "LIMIT ? OFFSET ?", "GROUP BY", "MIN(", "OriginStatusFilter.ROMGI_AVAILABLE", "OriginStatusFilter.LOCAL_LIBRARY", "OriginStatusFilter.FAVORITES", "OriginStatusFilter.RECENTLY_PLAYED", "OriginStatusFilter.WITH_ARTWORK", "OriginStatusFilter.WITHOUT_ARTWORK", "OriginStatusFilter.DOWNLOADED", "OriginStatusFilter.NOT_DOWNLOADED", "CatalogSort.NAME_ASC", "CatalogSort.NAME_DESC", "CatalogSort.YEAR_DESC", "CatalogSort.LAST_PLAYED", "CatalogSort.ADDED_RECENTLY", "CatalogSort.FAVORITES_FIRST")
require(paging, "g.region", "Super Mario World")
forbid(paging, "g.title LIKE '%World%'")
require(dao, "markPlayed", "markRomgiAvailability")
require(repo, "markPlayed", "markRomgiAvailability")

# Main screen separation, complete filter sheet and debounced paged catalogue.
main_layout = RES / "layout" / "activity_main.xml"
main_activity = JAVA / "ui" / "LegacyMainActivity.kt"
compose_main_activity = JAVA / "ui" / "MainActivity.kt"
filter_layout = RES / "layout" / "sheet_catalog_filters.xml"
filter_sheet = JAVA / "ui" / "CatalogFilterSheet.kt"
filters = JAVA / "util" / "CatalogFilters.kt"
for token in ("searchInput", "filtersButton", "globalCatalogButton", "settingsButton", "selectFolderButton", "syncButton", "randomGameButton", "activeFilterGroup", "gameList"):
    require(main_layout, f'@+id/{token}')
require(main_layout, "Buscar juegos, sagas o plataformas")
require(main_activity, "SEARCH_DEBOUNCE_MS", "openFilterSheet", "openRandomGame", "activeLabels", "IncrementalGameLoader")
for token in ("regionGroup", "platformContainer", "genreGroup", "statusGroup", "sortGroup", "clearButton", "cancelButton", "applyButton"):
    require(filter_layout, f'@+id/{token}')
require(filter_sheet, "BottomSheetDialog", "BottomSheetBehavior", "onApply", "clearButton", "cancelButton", "applyButton")
require(filters, "PREFERRED", "WORLD", "USA", "EUROPE", "JAPAN", "ASIA", "OTHER", 'ALL("Todas las regiones y revisiones")', "NINTENDO", "SONY", "SEGA", "SNK", "NEC", "ATARI", "ARCADE", "jrpg", "beat-em-up", "run-and-gun", "shoot-em-up", "survival-horror", "rhythm", "Sin clasificar")

require(filters, "Never infer region from ordinary title words", "val region = game.region.lowercase()")
forbid(filters, 'val value = "${game.region} ${game.title}".lowercase()')

# ROMgi staged resolver, visible ambiguity and no unencrypted/manual source path.
matcher = JAVA / "catalog" / "RomgiTitleMatcher.kt"
romgi = JAVA / "catalog" / "RomgiCatalog.kt"
resolved = JAVA / "catalog" / "ResolvedSource.kt"
edition = JAVA / "catalog" / "RomgiEdition.kt"
session = JAVA / "launch" / "SessionActivity.kt"
edition_picker = JAVA / "ui" / "compose" / "RomgiEditionPicker.kt"
require(matcher, "EXACT_TITLE_AND_REGION", "EXACT_TITLE", "CANONICAL_TITLE", "ALIAS", "PUNCTUATION_TOLERANT", "REORDERED_TITLE", "REGIONAL_FALLBACK", "FUZZY_LAST_RESORT", "regionOrder", "similarity")
require(matcher, "bracketMetadata", "detectRegion")
forbid(matcher, 'listOf("world", "usa", "europe", "japan", "asia").any(normalized::contains)')
require(romgi, "resolveOptions", "AMBIGUITY_SCORE_GAP", "RomgiResolution", "https://raw.githubusercontent.com/caprado/romgi/main/db/romdb.db.gz", "No existe una fuente HTTPS compatible")
require(resolved, "entryTitle", "requestedRegion", "preferredRegion", "matchedRegion", "matchStrategy", "confidence", "matchDescription")
require(romgi, "resolveEditions", "entry_groups", "entry_group_members", "buildEditionResolution", "MULTI_DISC_FORMATS")
require(edition, "RomgiDownloadPlan", "RomgiEditionResolution", "RomgiEditionParser", "M3uPlaylist", "releaseKind", "discIndex")
require(edition_picker, "Elige la edición", "Descargar juego completo", "Versiones avanzadas", "RECOMENDADA")
require(session, "chooseRomgiEdition", "RomgiEditionPicker", "requiresConfirmation", "plan.region", "HTTP sin cifrar", "catalog.resolveEditions")
forbid(session, "chooseRomgiSource", "setSingleChoiceItems")
forbid(session, "magnet manual", "URL manual")

# Fix 3: hard regional choice, authenticated IA HTTPS priority and bounded torrent metadata.
ia_auth = JAVA / "auth" / "InternetArchiveAuthManager.kt"
ia_login = JAVA / "ui" / "InternetArchiveLoginActivity.kt"
ia_parser = JAVA / "auth" / "InternetArchiveS3PageParser.kt"
ia_web_policy = JAVA / "auth" / "InternetArchiveWebLoginPolicy.kt"
downloader = JAVA / "launch" / "AuthorizedSourceDownloader.kt"
background_task = JAVA / "launch" / "RomDownloadTaskStore.kt"
background_scheduler = JAVA / "launch" / "RomDownloadScheduler.kt"
background_job = JAVA / "launch" / "RomDownloadJobService.kt"
background_fgs = JAVA / "launch" / "RomDownloadForegroundService.kt"
background_notification = JAVA / "launch" / "RomDownloadNotification.kt"
prepared_validator = JAVA / "launch" / "RomPreparedFileValidator.kt"
prepared_payload_validator = JAVA / "launch" / "RomPreparedPayloadValidator.kt"
global_catalog = JAVA / "ui" / "GlobalCatalogActivity.kt"
global_layout = RES / "layout" / "activity_global_catalog.xml"
require(matcher, "detectRegionMetadata", "normalizeRegion", "France")
require(
    romgi,
    "val eligible = scored",
    "fileIdentity = playableFileName",
    "sourceRegion",
    "RomgiSourcePolicy.detectConcreteRegion",
    "RomgiSourcePolicy.isDirectHttps",
    "internetArchiveAuthenticated",
    "score += 2_000",
    "score += 120",
)
source_policy = JAVA / "catalog" / "RomgiSourcePolicy.kt"
require(
    source_policy,
    "fileName.orEmpty()",
    "torrentFilePath.orEmpty()",
    "fileNameFromUrl",
    "linkName.orEmpty()",
    "entryTitle",
    "mapNotNull(RomgiTitleMatcher::detectRegionMetadata)",
)
require(resolved, "requiresConfirmation", "regionFallback", "requiresAuth", "useTorrent")
require(ia_auth, "AndroidKeyStore", "AES/GCM/NoPadding", "LOW", "archive.org/account/s3.php", "headersFor", "completeLoginFromCookies", "completeLoginFromPageKeys", "failureCount", "CONNECTED", "STALE", "INVALID")
require(ia_auth, "validate(countAuthFailure: Boolean = true)", "MAX_AUTH_FAILURES = 3")
require(ia_auth, 'listOf("HEAD", "GET")', "probeCredentials", "isS3KeysPageUrl")
require(ia_login, "WebView", "CookieManager", "MIXED_CONTENT_NEVER_ALLOW", "setAcceptThirdPartyCookies", "InternetArchiveAuthManager.S3_KEYS_URL", "completeLoginFromCookies")
require(ia_login, "evaluateJavascript", "DOM_EXTRACTION_SCRIPT", "PasswordVisualTransformation", "Usar claves", "importS3Credentials")
require(ia_parser, "unique.length === 2", "hasOfficialLabels", "parseJavascriptResult", "^[A-Za-z0-9]{16}$")
require(ia_web_policy, 'ARCHIVE_HOST = "archive.org"', 'S3_KEYS_PATH = "/account/s3.php"', "isTrustedDownloadUrl", "uri.userInfo == null", "uri.port == 443")
forbid(ia_login, "addJavascriptInterface", "Destroy Old Keys", "Generate New Keys")
forbid(ia_parser, "XMLHttpRequest", ".click()", "Destroy Old Keys", "Generate New Keys")
require(build, "androidx.browser:browser:1.9.0")
require(APP / "AndroidManifest.xml", "com.android.chrome")
require(downloader, "requiresInternetArchiveAuth", "45 segundos", "METADATA_TIMEOUT_MS = 45L", "METADATA_WARNING_MS = 12L")
require(downloader, "recordAuthFailure", "validate(countAuthFailure = false)")
# Fix 6.5: ROMgi requires_auth is advisory; authenticated IA URLs receive safe opportunistic auth.
require(
    downloader,
    "HTTP_AUTH_PLAN",
    "authManager.takeIf { sessionAvailable }",
    "authManager.isTrustedArchiveUrl(currentUrl.toString())",
    "headersFor(currentUrl.toString(), preferLow = preferLowAuth)",
    "IA_AUTH_LOW_RETRY",
    "lowFallbackAttempted",
    "Internet Archive exige autenticación para esta fuente",
)
require(ia_auth, "fun headersFor(url: String, preferLow: Boolean = false)", 'preferLow || host == "s3.us.archive.org"')
forbid(downloader, "InternetArchiveAuthManager(context).clear()")
# Fix 6.8: user-visible background downloads, resumable HTTP staging and safe cleanup.
require(
    manifest if 'manifest' in globals() else APP / "AndroidManifest.xml",
    "RUN_USER_INITIATED_JOBS",
    "POST_NOTIFICATIONS",
    "FOREGROUND_SERVICE_DATA_SYNC",
    'android:name=".launch.RomDownloadJobService"',
    'android:name=".launch.RomDownloadForegroundService"',
    'android:foregroundServiceType="dataSync"',
    "android.permission.BIND_JOB_SERVICE",
)
require(background_scheduler, "setUserInitiated(true)", "NETWORK_TYPE_ANY", "startForegroundService", "setEstimatedNetworkBytes", "setMinimumNetworkChunkBytes", "store.partialBytes(snapshot)")
require(background_job, "setNotification", "JOB_END_NOTIFICATION_POLICY_REMOVE", "BackgroundRomDownloadEngine", "params.network", "jobFinished")
require(background_fgs, "ServiceCompat.startForeground", "FOREGROUND_SERVICE_TYPE_DATA_SYNC", "START_REDELIVER_INTENT", "onTimeout")
require(background_notification, "Jugar ahora", "Borrar parcial", "setProgress", "Descarga completa")
require(background_task, "RomDownloadStatus", "deletePartial", "stagingId", "FAILED", "SUCCESS", "previous.status != RomDownloadStatus.CANCELLED || partialBytes(previous) > 0L")
require(downloader, 'network?.openConnection(currentUrl)', 'setRequestProperty("Range", "bytes=$resumeOffset-")', "HTTP_RESUME_ACCEPTED", "HTTP_PARTIAL", "FileOutputStream(target, canAppend)")
require(JAVA / "launch" / "SessionFileStore.kt", "if (!directory.exists()) directory.mkdirs()")
require(session, "startOrAttachBackgroundDownload", "attachBackgroundDownload", "Borrar descarga parcial", "POST_NOTIFICATIONS", "EXTRA_BACKGROUND_DOWNLOAD")
require(persistent_library if 'persistent_library' in globals() else JAVA / "launch" / "PersistentRomLibrary.kt", "deleteDownloaded", "Solo las ROM descargadas por Oz")
require(JAVA / "ui" / "SettingsActivity.kt", "InternetArchiveLoginActivity", "refreshInternetArchiveStatus")
require(RES / "layout" / "activity_settings.xml", "internetArchiveLoginButton", "internetArchiveLogoutButton", "Cuenta de Internet Archive")
require(APP / "AndroidManifest.xml", 'android:name=".ui.InternetArchiveLoginActivity"')
require(global_layout, "Buscar juegos, sagas o plataformas", "filterButton", "filterChipGroup")
forbid(global_layout, "genreSpinner", "regionSpinner")
require(global_catalog, "CatalogFilterSheet.show", "CatalogFilterState", "statusFilters", "regionModes", "genreKeys", "platformFamilies", "sort = filterState.sort", "Todos los sistemas preparados")

# Fix 4: Chrome-first IA connection plus persistent SAF ROM library and computed collection.
persistent_library = JAVA / "launch" / "PersistentRomLibrary.kt"
require(
    persistent_library,
    "DocumentFile.fromTreeUri",
    "openOutputStream",
    "MessageDigest.getInstance(\"SHA-256\")",
    "updateLocalState",
    "resolvedRegion",
    "Biblioteca ROMs · descargado",
)
require(session, "persistentLibrary.resolve(loaded)", "persistentLibrary.persist(current, prepared, resolvedRegion)", "requestPersistentRomFolder", "Biblioteca ROMs persistente")
require(paging, "DOWNLOADED_COLLECTION_ID", "downloaded-library", "COALESCE(g.contentUri,'')!=''")
require(dao, "downloadedOnce", "downloaded(query")
require(repo, "CatalogPagingSql.DOWNLOADED_COLLECTION_ID", "downloadedOnce")
require(JAVA / "data" / "SeedLoader.kt", "ensureRuntimeCollections", "DOWNLOADED_COLLECTION_ID")
require(JAVA / "ui" / "GameAdapter.kt", "Descargado", "sin volver a descargar")
require(JAVA / "ui" / "CrtGameDialog.kt", "Descargar y jugar", "Biblioteca: descargado", "DOWNLOADED_COLLECTION_ID")
require(RES / "layout" / "activity_settings.xml", "Biblioteca ROMs persistente", "Elegir carpeta ROMs de RetroArch", "copias temporales de compatibilidad")

# Fix 6: progressive Compose host/design/navigation/paging/library origin/Auth V2.
compose_app = JAVA / "ui" / "compose" / "OzApp.kt"
compose_vm = JAVA / "ui" / "compose" / "OzViewModel.kt"
compose_theme = JAVA / "ui" / "compose" / "theme" / "OzTheme.kt"
compose_components = JAVA / "ui" / "compose" / "components" / "OzComponents.kt"
paging_source = JAVA / "data" / "RepositoryPagingSource.kt"
collection_download = JAVA / "launch" / "CollectionDownloadManager.kt"
libretro_catalog = JAVA / "catalog" / "LibretroCatalogRepository.kt"
catalog_preparation_policy = JAVA / "catalog" / "CatalogPreparationPolicy.kt"
catalog_index_merge_policy = JAVA / "catalog" / "CatalogIndexMergePolicy.kt"
catalog_background = JAVA / "catalog" / "background"
catalog_preparation_scheduler = catalog_background / "CatalogPreparationScheduler.kt"
catalog_preparation_manager = catalog_background / "CatalogPreparationManager.kt"
catalog_preparation_store = catalog_background / "CatalogPreparationTaskStore.kt"
catalog_preparation_fgs = catalog_background / "CatalogPreparationForegroundService.kt"
catalog_preparation_job = catalog_background / "CatalogPreparationJobService.kt"
catalog_preparation_notification = catalog_background / "CatalogPreparationNotification.kt"
require_files(
    catalog_preparation_scheduler,
    catalog_preparation_manager,
    catalog_preparation_store,
    catalog_preparation_fgs,
    catalog_preparation_job,
    catalog_preparation_notification,
)
require(compose_main_activity, "ComponentActivity", "setContent", "OzTheme", "OzApp", "OzViewModel")
require(compose_app, "NavHost", "Inicio", "Colecciones", "Catálogo", "Biblioteca", "Configuración", "LazyVerticalGrid", "GridCells.Adaptive", "collectAsLazyPagingItems", "ROMs · $missingRoms", 'OzButton("iiSU"', "InternetArchiveWebLoginScreen" if False else "InternetArchiveLoginActivity")
require(compose_vm, "AndroidViewModel", "StateFlow", "MutableStateFlow", "flatMapLatest", "cachedIn", "Repository", "CollectionDownloadScheduler", "IisuExporter")
require(compose_vm, "debounce(300)", "collect", "LibraryOrigin.DOWNLOADED", "LibraryOrigin.IMPORTED")
require(
    catalog_preparation_policy,
    "READY_STATUS",
    "FBNEO_SYSTEM_ID",
    "fun needsIndex",
    "indexedGames <= 0",
    "arcadeGamesWithRomSet < indexedGames",
)
require(
    libretro_catalog,
    "suspend fun needsIndex",
    "CatalogPreparationPolicy.needsIndex",
    "invalidateAllPreparedSystems",
    "db.catalogStateDao().markAllStale()",
)
require(libretro_catalog, "db.gameDao().byIds", "CatalogIndexMergePolicy.merge")
require(dao, "markAllStale", "SET status='STALE', indexedAt=NULL, error=NULL")
require(
    catalog_index_merge_policy,
    "contentUri = existing.contentUri",
    "lastPlayedAt = existing.lastPlayedAt",
    "romgiAvailable = existing.romgiAvailable",
    "libraryOrigin = existing.libraryOrigin",
)
require(
    compose_vm,
    "CatalogPreparationUi",
    "catalogPreparation",
    "prepareCompleteCatalog",
    "rebuildCompleteCatalog",
    "cancelCatalogPreparation",
    "prepareCatalogSystems",
    "family == PlatformFamily.OTHER",
    "repository.gameCountPulse()",
    "catalog.invalidateAllPreparedSystems()",
    "catalogPreparationElapsedMillis",
)
require(
    catalog_preparation_store,
    "Plataformas seleccionadas preparadas",
    "el progreso guardado se conserva",
    "catalogPreparationElapsedMillis",
    "formatCatalogPreparationElapsed",
)
require(
    catalog_preparation_scheduler,
    "setUserInitiated(true)",
    "NETWORK_TYPE_ANY",
    "startForegroundService",
    "markCancelled",
)
require(catalog_preparation_manager, "catalog.needsIndex", "catalog.index", "continuando con el siguiente")
require(catalog_preparation_fgs, "ServiceCompat.startForeground", "FOREGROUND_SERVICE_TYPE_DATA_SYNC", "START_REDELIVER_INTENT", "onTimeout")
require(catalog_preparation_job, "setNotification", "JOB_END_NOTIFICATION_POLICY_DETACH", "jobFinished")
require(catalog_preparation_notification, "setUsesChronometer", "Tiempo $elapsed", 'addAction(0, "Pausar"')
forbid(compose_vm, "catalog.index(system)")
require(
    compose_app,
    "CatalogPreparationPanel",
    "Catálogo ${state.readySystems}/${state.totalSystems} sistemas",
    'OzButton("Pausar"',
    '"Continuar" else "Preparar"',
    'OzButton("Reconstruir"',
    'Text("Reconstruir catálogo"',
    "formatCatalogPreparationElapsed(state.elapsedMillis)",
    "No borrará ROMs, favoritos, colecciones, sesiones ni juegos guardados",
)
require(
    APP / "AndroidManifest.xml",
    'android:name=".catalog.background.CatalogPreparationJobService"',
    'android:name=".catalog.background.CatalogPreparationForegroundService"',
    'android:name=".catalog.background.CatalogPreparationActionReceiver"',
)
require(compose_theme, "OzColors", "OzTypography", "OzShapes", "OzSpacing", "OzMotion", "PurpleDeep", "ArcadeViolet", "ElectricBlue", "Cyan")
require(compose_components, "focusable", "onFocusChanged", "animateFloatAsState", "AsyncImage")
# Fix 6.3: compile-time Compose call contracts found by real Termux build.
require(compose_app, "OzGameCover(game, preferences, gameModifier, onClick = { onGame(game.id) })")
require(compose_components, "val context = androidx.compose.ui.platform.LocalContext.current", "ArtworkResolver.candidates(context, preferences, game)")
forbid(compose_components, "preferencesContext(preferences)")
require(paging_source, "PagingSource", "repository.page", "MAX_LOAD")
require(collection_download, "PersistentRomLibrary", "alreadyAvailable", "needsConfirmation", "BackgroundRomDownloadEngine")
require(entities, "LibraryOrigin", "libraryOrigin")
require(paging, "libraryOrigins")
require(build, "org.jetbrains.kotlin.plugin.compose", "compose-bom:2026.06.00", "material3", "navigation-compose:2.9.8", "paging-compose:3.5.0", "coil-compose:3.2.0")
forbid(build, "io.coil-kt.coil3:coil-compose:3.5.0", "io.coil-kt.coil3:coil-network-okhttp:3.5.0")
forbid(compose_app, "content://")

# Fix 6.6: compact home tiles + collection artwork refresh from indexed Libretro twins.
require(compose_theme, "tileTitle", "fontSize = 14.sp")
require(compose_components, "OzTypography.tileTitle", "maxLines = 1", "softWrap = false")
require(dao, "bestArtworkVariant", "canonicalTitle=:canonicalTitle", "source='libretro'")
require(repo, "artworkVariantFor", "bestArtworkVariant", "The collection membership and launch identity are never replaced")
require(compose_app, "game.thumbnailRepo", "preferences.gameArtworkMode(game.id)", "var artworkIndex by remember(candidates)", "if (artworkIndex < candidates.lastIndex) artworkIndex++")

# Emulator selection/profile table and verified launch discipline.
preferences = JAVA / "data" / "AppPreferences.kt"
profiles = JAVA / "launch" / "EmulatorProfiles.kt"
external = JAVA / "launch" / "ExternalEmulatorManager.kt"
emu_activity = JAVA / "ui" / "EmulatorSettingsActivity.kt"
emu_layout = RES / "layout" / "activity_emulator_settings.xml"
settings = JAVA / "ui" / "SettingsActivity.kt"
require(preferences, "enum class EngineMode", "AUTO", "RETROARCH", "EXTERNAL", "ASK", "engineMode", "externalEmulatorPackage")
require(profiles, "VERIFIED_EXPLICIT_CONTENT", "RUNTIME_RESOLVED_VIEW", "APP_ONLY", "org.ppsspp.ppsspp.PpssppActivity", "me.magnum.melonds.ui.emulator.EmulatorActivity", "com.github.stenzek.duckstation", "org.dolphinemu.dolphinemu", "org.azahar_emu.azahar", "com.flycast.emulator", "org.devmiyax.yabasanshioro2.pro", "com.virtualapplications.play", "play_libretro_android.so", "swanstation_libretro_android.so", "SwanStation es un core", "NetherSX2")
forbid(profiles, "xyz.aethersx2.android", "NetherSX2Activity", "pcsx2_libretro_android.so")
require(external, "Intent.ACTION_VIEW", 'require(uri.scheme.equals("content", true))', "FLAG_GRANT_READ_URI_PERMISSION", "ClipData.newRawUri", "setPackage(app.packageName)", "resolveActivity", "activityInfo?.exported == true", "getLaunchIntentForPackage")
require(emu_activity, "OpenDocument", "testExternalLaunch", "detectEmulatorsButton", "restoreAllEnginesButton", "BIOS", "selectedOrRecommended")
require(emu_layout, "emulatorSystemsList", "Detectar instalados", "Restaurar automático")
require(settings, "EmulatorSettingsActivity")
require(RES / "layout" / "activity_settings.xml", "Motores y emuladores")
require(session, "EngineMode.RETROARCH", "EngineMode.EXTERNAL", "EngineMode.ASK", "EngineMode.AUTO", "showEngineChooser", "externalEmulatorManager.canLaunch", "markPlayed")

# Package visibility and modern Android inter-app safeguards.
manifest = APP / "AndroidManifest.xml"
require(manifest, "<queries>", "com.retroarch.aarch64", "com.retroarch.ra32", "com.iiSULauncher", "org.ppsspp.ppsspp", "me.magnum.melonds", "com.github.stenzek.duckstation", "org.dolphinemu.dolphinemu", "org.azahar_emu.azahar", "com.flycast.emulator", "org.devmiyax.yabasanshioro2.pro", "com.virtualapplications.play", 'android:usesCleartextTraffic="false"', 'android:networkSecurityConfig="@xml/network_security_config"', 'android:name=".launch.SessionActivity"', 'android:exported="true"', "cl.gakuto.kizuna.action.PLAY")
forbid(manifest, "QUERY_ALL_PACKAGES", 'android:scheme="http"', 'android:scheme="https"', 'android:scheme="file"')
require(RES / "xml" / "network_security_config.xml", 'cleartextTrafficPermitted="false"')

# RetroArch native integration, no synthesized private paths, safe core sideload.
retro = JAVA / "launch" / "RetroArchManager.kt"
require(retro, "ApplicationInfo", ".dataDir", ".sourceDir", "QUERY_INSTALLED_CORES", "INSTALLED_CORES_RESULT", "RetroActivityFuture", "CoreSideloadActivity", "buildCoreSideloadIntent", "LIBRETRO", "ROM", "DATADIR", "APK", "SDCARD", "EXTERNAL", "SHA-256", "com.retroarch.aarch64", "com.retroarch.ra32")
forbid(retro, '"/data/user/', '"/data/data/')
require(session, "buildCoreSideloadIntent", "queryInstalledCores")
require(retro, "installedVariants", "queryInstalledCores", "directLaunchActivityAvailable")
retro_bios = JAVA / "launch" / "RetroBiosValidator.kt"
retro_panel = JAVA / "ui" / "compose" / "RetroPreparationPanel.kt"
require(retro_bios, "disksys.rom", "dsi_bios7.bin", "dsi_bios9.bin", "dsi_firmware.bin", "dsi_nand.bin", "bios_CD_J.bin", "bios_CD_E.bin", "bios_CD_U.bin", "sega_101.bin", "mpr-17933.bin", "syscard3.pce")
require(retro_panel, "OZ // BOOT SEQUENCE", "Preparando partida", 'RetroPreparationStep("file", "Archivo")', 'RetroPreparationStep("core", "Core")', 'RetroPreparationStep("compatibility", "Compatibilidad")', 'RetroPreparationStep("bios", "BIOS")', 'RetroPreparationStep("open", "RetroArch")', "RetroPreparationViewModel", "StateFlow")
require(session, "verifyRetroFileAccess", "validateRetroCompatibility", "validateRetroBios", "verifyPendingCoreSideload", "directLaunchActivityAvailable", "RetroPreparationPanel")
forbid(session, "if (query?.supported != true && cachedCore != null)")

# Fix 6.10: resolve RetroArch's documented receiver explicitly; query failure is not proof of a missing core.
require(retro, "INSTALLED_CORES_RECEIVER", "queryBroadcastReceivers", "component = receiverComponent", "timeoutMs: Long = 4_000L", "extractCoreNames(resultData)")
require(retro, '"pcsx_rearmed_libretro_android.so" -> add("swanstation_libretro_android.so")', '"swanstation_libretro_android.so" -> add("pcsx_rearmed_libretro_android.so")')
require(session, "RETROARCH_CORE_QUERY_FALLBACK", "preferences.isRetroArchCoreConfirmed(packageName, it)", "retroArchManager.directCorePath(packageName, fallbackCore)", "validateRetroStagesAfterCore(current, retroEntry, fallbackCore)")
forbid(session, "Oz no abrirá RetroArch sin confirmar el core real.")

# Fix 6.11: external RetroArch launches must reuse RetroArch's own persistent config.
require(retro, "defaultConfigPath", "createPackageContext(packageName, 0).getExternalFilesDir(null)", '"Android/data/$packageName/files"', "RETROARCH_CONFIG_FILE", "EXTRA_CONFIGFILE", 'putExtra(EXTRA_CONFIGFILE, configFile)')
require(retro, 'const val EXTRA_CONFIGFILE = "CONFIGFILE"', 'const val RETROARCH_CONFIG_FILE = "retroarch.cfg"')

# Fix 6.12: refreshed UX, favorite artwork parity, separated library settings and official overlays.
library_settings = JAVA / "ui" / "LibrarySettingsActivity.kt"
overlay_manager = JAVA / "launch" / "RetroOverlayManager.kt"
require(repo, "favoriteHome", "artworkVariantFor(game)")
require(compose_components, "var artworkIndex by remember(candidates) { mutableStateOf(0) }", "candidates.getOrNull(artworkIndex)", "if (artworkIndex < candidates.lastIndex) artworkIndex++")
require(session, "The system profile is Oz's maintained recommendation", "profile?.retroArchCore?.takeIf(String::isNotBlank)")
require(library_settings, "Biblioteca y almacenamiento", "globalRomTreeUri", "systemRomTreeUri", "localArtworkTreeUri", "ArtworkMode")
require(manifest, ".ui.LibrarySettingsActivity")
require(settings, "RetroOverlayManager", "retroOverlayFolderPicker", "downloadOfficialOverlays", "SCOPE_ADVANCED", "romLibrarySection.visibility = View.GONE")
require(RES / "layout" / "activity_settings.xml", "romLibrarySection", "Overlays RetroArch oficiales", "retroOverlayFolderUriText", "downloadRetroOverlaysButton")
require(preferences, "retroArchOverlayTreeUri", "KEY_RETROARCH_OVERLAY_TREE")
require(overlay_manager, "https://buildbot.libretro.com/assets/frontend/overlays.zip", "ZipInputStream", "sanitizeZipPath", "MAX_UNPACKED_BYTES")
require(RES / "layout" / "activity_session.xml", "@drawable/bg_session_console", "@color/electric_blue", "@color/oz_text_primary", "@color/oz_text_secondary")

# Fix 6.13: final settings separation and explicit collection artwork synchronization.
emu_integration = JAVA / "ui" / "EmulationIntegrationSettingsActivity.kt"
catalog_download_settings = JAVA / "ui" / "CatalogDownloadSettingsActivity.kt"
collection_artwork_sync = JAVA / "launch" / "CollectionArtworkSyncManager.kt"
require(emu_integration, "Emulación e integraciones", "RetroArch", "Cores por sistema", "BIOS y firmware", "Overlays oficiales", "Motores por sistema", "iiSU", "Compatibilidad y diagnóstico", "Ajustes que conserva RetroArch", "Video/Vulkan", "shaders", "remaps", "overrides")
forbid(emu_integration, "Cuenta de Internet Archive", "Rendimiento del catálogo", "Borrar catálogo ROMgi local")
require(catalog_download_settings, "Catálogo y descargas", "ROMgi", "Región preferida", "Rendimiento del catálogo", "torrent selectivo")
require(JAVA / "ui" / "compose" / "OzApp.kt", "CatalogDownloadSettingsActivity", "EmulationIntegrationSettingsActivity", "Iconos SGDB")
require(JAVA / "ui" / "compose" / "OzViewModel.kt", "CollectionArtworkSyncUi", "syncSelectedCollectionArtwork", "CollectionArtworkSyncManager")
require(collection_artwork_sync, "Preparando índice de artwork", 'state?.status != "READY"', "artworkVariants", "remoteCandidates", "setSyncedArtworkAlias", "setSyncedArtworkLocalUri", "setCollectionArtworkSyncSnapshot")
require(preferences, "syncedArtworkTitle", "syncedArtworkRepo", "CollectionArtworkSyncSnapshot")
require(JAVA / "util" / "ArtworkResolver.kt", "syncedAlias", "syncedArtworkTitle", "syncedArtworkRepo", "remoteCandidates")
require(JAVA / "data" / "Daos.kt", "artworkVariants", "canonicalTitle=:canonicalTitle", "source='libretro'")
require(manifest, ".ui.CatalogDownloadSettingsActivity", ".ui.EmulationIntegrationSettingsActivity")

# Fix 6.14: session background must be a valid Android drawable container.
session_bg = RES / "drawable" / "bg_session_console.xml"
CHECKS += 1
try:
    session_bg_root = ET.parse(session_bg).getroot().tag.split("}")[-1]
    if session_bg_root != "shape":
        fail(f"bg_session_console.xml debe usar <shape> como raíz drawable, no <{session_bg_root}>")
except Exception as exc:
    fail(f"No se pudo validar bg_session_console.xml: {exc}")
require(session_bg, "<shape", "<gradient", 'android:angle="315"', 'android:startColor="@color/oz_background"', 'android:centerColor="@color/oz_surface"', 'android:endColor="@color/oz_surface_raised"')
for drawable_xml in (RES / "drawable").glob("*.xml"):
    CHECKS += 1
    try:
        drawable_root = ET.parse(drawable_xml).getroot().tag.split("}")[-1]
        if drawable_root == "gradient":
            fail(f"{drawable_xml.name}: <gradient> no puede ser raíz de un drawable Android")
    except Exception as exc:
        fail(f"{drawable_xml.name}: XML drawable inválido: {exc}")

# Fix 6.9: persistent SAF library launch must not trust a derived /storage path under scoped storage.
public_session_store = JAVA / "launch" / "PublicSessionStore.kt"
require(session, "ensureRetroArchAccessibleEntry", "direct.isFile && direct.canRead()", "publicStore.publish(sourceUri, entry.displayName)", "mirrored.copy(mediaUri = sourceUri)", "pendingRetroArchEntry = retroEntry")
require(public_session_store, "fun publish(source: Uri, displayName: String): Entry", "openInputStream(source)", "OzRetroRom/Sessions")
forbid(session, "El archivo de juego no existe en la sesión pública.")

# iiSU bridge compatibility and explicit command.
iisu = JAVA / "integration" / "IisuExporter.kt"
require(iisu, "cl.gakuto.kizuna", "SessionActivity", "ROM", "descriptors", "virtual")
require(ROOT / "IIISU-0.0.7.4-COMMAND.txt", "cl.gakuto.kizuna/cl.gakuto.kizuna.launch.SessionActivity", "ROM", "%ROM%")
require(session, "ACTION_PLAY", "EXTRA_IIISU_ROM", "descriptorFromReference", "virtualDescriptor")

# Beta 2 progress screen retained and FBNeo exact-set protection.
progress_layout = RES / "layout" / "activity_session.xml"
for token in ("statusText", "elapsedText", "fileInfoText", "progressPercentText", "downloadedText", "speedText", "peersText", "etaText", "stallWarningText", "retryButton", "deletePartialButton", "cancelButton"):
    require(progress_layout, f'@+id/{token}')
forbid(progress_layout, "shareDiagnosticButton", "Compartir diagnóstico")
require(session, "elapsed", "speed", "peers", "eta", "stall", "retry", "cancel")
require(session, "romset exacto", "descriptor", "FBNeo")
require(prepared_validator, "no coincide", "ZIP de FBNeo", "FBNeo")
require(JAVA / "launch" / "FbneoSupport.kt", "zip", "BIOS")

# Performance and security invariants.
require(main_activity, "SEARCH_DEBOUNCE_MS")
require(JAVA / "ui" / "IncrementalGameLoader.kt", "pageSize", "rawOffset", "loadMore")
require(main_activity, "pageJob?.cancel()")
require(JAVA / "util" / "ArtworkLoader.kt", "LruCache")
require(JAVA / "launch" / "SessionLaunchValidator.kt", "MAX", "content")
require(JAVA / "util" / "DiagnosticLogger.kt", "redact")
require(ROOT / "04_COMPILAR_RELEASE_SEGURO_TERMUX.sh", "assembleRelease", "sha256sum", "0.6.0-beta.4")
require(ROOT / "02_COMPILAR_TERMUX.sh", "assembleDebug", "0.6.0-beta.4")

for app_file in APP.rglob("*"):
    if app_file.is_file() and app_file.suffix in {".kt", ".xml", ".json", ".txt"}:
        forbid(app_file, "0.6.0-beta.2", "versionCode = 60002")

# Public documentation and artifact naming.
required_docs = (
    ROOT / "docs" / "RESEARCH_REPORT_0.6.0_BETA3.md",
    ROOT / "docs" / "EMULATOR_SYSTEM_TABLE_0.6.0_BETA3.md",
    ROOT / "docs" / "USER_MANUAL_0.6.0_BETA3.md",
    ROOT / "docs" / "TERMUX_BUILD_0.6.0_BETA3.md",
    ROOT / "docs" / "RELEASE_NOTES_0.6.0_BETA3.md",
    ROOT / "docs" / "VALIDATION_REPORT_0.6.0_BETA3.md",
    ROOT / "docs" / "BETA4_IISU_LIBRARY_ARCHITECTURE.md",
    ROOT / "docs" / "RELEASE_NOTES_0.6.0_BETA4.md",
    ROOT / "docs" / "VALIDATION_REPORT_0.6.0_BETA4.md",
)
require_files(*required_docs)
require(ROOT / "README.md", "0.6.0 Beta 4", "60018", "0.6.0-beta.4")
for public_doc in (ROOT / "README.md", *required_docs):
    forbid(public_doc, "Rebuild")

# Beta 4 Fix 1: Oz remains the ROM source of truth while iiSU regains virtual on-demand entries.
catalog_settings_beta4 = JAVA / "ui" / "CatalogDownloadSettingsActivity.kt"
emu_settings_beta4 = JAVA / "ui" / "EmulationIntegrationSettingsActivity.kt"
library_settings_beta4 = JAVA / "ui" / "LibrarySettingsActivity.kt"
app_preferences_beta4 = JAVA / "data" / "AppPreferences.kt"
iisu_exporter_beta4 = JAVA / "integration" / "IisuExporter.kt"
oz_view_model_beta4 = JAVA / "ui" / "compose" / "OzViewModel.kt"
collection_artwork_beta4 = JAVA / "launch" / "CollectionArtworkSyncManager.kt"
session_launch_validator_beta4 = JAVA / "launch" / "SessionLaunchValidator.kt"
require(catalog_settings_beta4, "var selectedRegion by remember", "var selectedPerformance by remember", "CatalogChoiceButton", "preferences.preferredRegion = key", "preferences.catalogPerformanceMode = mode", ".clickable(onClick = onClick)")
forbid(catalog_settings_beta4, "FilterChip(")
require(library_settings_beta4, "Biblioteca ROMs principal", "Carpeta por sistema", "Carátulas")
forbid(library_settings_beta4, "Biblioteca virtual iiSU", "globalVirtualTreeUri", "systemVirtualTreeUri", "virtualGlobalPicker", "virtualSystemPicker")
require(emu_settings_beta4, "iiSU · frontend opcional", "Publicar juegos locales", "Opciones avanzadas", "iisuBridgePicker", "Limpiar accesos generados", "Custom Launch Command", "publicar una colección completa")
require(app_preferences_beta4, "iisuIntegrationEnabled", "recordIisuSync", "syncedArtworkLocalUri", "setSyncedArtworkLocalUri")
require(iisu_exporter_beta4, "val bridgeGames = games.distinctBy { it.id }", 'put("contentMode", "oz-virtual-on-demand")', "si falta", "no contienen ROMs", "-a cl.gakuto.kizuna.action.PLAY")
forbid(iisu_exporter_beta4, "val localGames = games.filter { !it.contentUri.isNullOrBlank() }")
require(oz_view_model_beta4, "CollectionDownloadScheduler.schedule", "accesos virtuales publicados", "IisuExporter(getApplication()).exportConfigured(preferences, games)")
require(collection_artwork_beta4, "downloadOfficialArtwork", "MAX_ARTWORK_BYTES", "setSyncedArtworkLocalUri", "filesDir", "Artwork guardado")
require(session_launch_validator_beta4, "explicitIisuMain", "Intent.ACTION_MAIN", "EXTRA_IIISU_ROM", "SessionActivity::class.java.name")
require_files(
    JAVA / "launch" / "CollectionBatchTaskStore.kt",
    JAVA / "launch" / "CollectionDownloadScheduler.kt",
    JAVA / "launch" / "CollectionDownloadJobService.kt",
    JAVA / "launch" / "CollectionDownloadForegroundService.kt",
    JAVA / "launch" / "CollectionDownloadNotification.kt",
)
require(JAVA / "launch" / "CollectionDownloadManager.kt", "BackgroundRomDownloadEngine", "Batch download finalizado")

# Beta 4 Fix 2: a virtual iiSU descriptor must never become emulator content.
require(session, "IISU_STALE_DESCRIPTOR_SOURCE_CLEARED", "isOzVirtualDescriptorReference", "downloaded?.asSource()", "remembered", "verifyRetroFileAccess(current, retroEntry)")
require(prepared_validator, "isOzVirtualDescriptor", "oz-retrorom.json", "oz-virtual-on-demand", "iiSU entregó un descriptor virtual de Oz")
# Persistent Oz ROMs must outrank remembered external/bridge sources.
CHECKS += 1
session_text = session.read_text(encoding="utf-8")
if session_text.find("downloaded?.asSource()") > session_text.find("?: remembered"):
    fail("SessionActivity debe priorizar la ROM persistente de Oz antes de una fuente recordada")


# Beta 4 Fix 4: iiSU bridge is additive, uses a quoted path and resolves descriptor IDs before raw-path access.
fix4_doc = ROOT / "docs" / "BETA4_FIX4_IISU_BRIDGE_REPAIR.md"
require_files(fix4_doc)
require(iisu_exporter_beta4, r'-e ROM \"%ROM%\"', "clearGeneratedBridge", "Publishing is intentionally additive", "isGeneratedDescriptor")
forbid(iisu_exporter_beta4, "cleanStaleDescriptors(platformFolder, systemGames)")
require(emu_settings_beta4, "Usa solo el Bridge en iiSU", "Limpiar accesos generados", "Las comillas alrededor de %ROM% son necesarias")
forbid(emu_settings_beta4, "iisuSystemBridgePicker", "Destino por sistema")
require(session, "IISU_BRIDGE_ID_RESOLVED", "val rawBaseName = rawFileName.substringBeforeLast('.')", "val rawExtension = rawFileName.substringAfterLast", "expectedExtension = bridgeGame.virtualExtension", "dao.byId(rawBaseName)")
CHECKS += 1
fix4_session_text = read(session)
if fix4_session_text.find("dao.byId(rawBaseName)") > fix4_session_text.find("SessionLaunchValidator.validatedReference(this, intent, rawReference)"):
    fail("Fix4: el ID del descriptor iiSU debe resolverse antes de exigir acceso raw a la ruta")
require(ROOT / "IIISU-0.0.7.4-COMMAND.txt", '-e ROM "%ROM%"', "SOLO la carpeta Bridge", "Summary > Update")


# Beta 4 Fix 5: ROMgi title aliases + return to external launcher after emulator exit.
romgi_alias_policy = JAVA / "catalog" / "RomgiTitleAliasPolicy.kt"
fix5_doc = ROOT / "docs" / "BETA4_FIX5_ROMGI_RETURN_IISU.md"
require_files(romgi_alias_policy, fix5_doc)
require(matcher, "requestedAliases: List<String> = emptyList()", "configuredAlias")
require(romgi_alias_policy, "Super Castlevania IV", "Final Fantasy II", "Final Fantasy III", "Legend of Zelda, The - A Link to the Past")
require(romgi, "RomgiTitleAliasPolicy.aliasesFor(descriptor)", "titleAliases.forEach")
require(session, "returnToExternalLauncher", "isExternalBridgeLaunch", "finishSessionToOrigin", "finishAffinity()", "SESSION_RETURN_TARGET")
CHECKS += 1
if read(session).count("finishSessionToOrigin()") < 3:
    fail("Fix5: SessionActivity debe retornar al origen tanto tras RetroArch como al cancelar")


# Beta 4 Fix 6: iiSU sessions keep landscape orientation through download/preflight.
fix6_doc = ROOT / "docs" / "BETA4_FIX6_IISU_ORIENTATION.md"
rom_download_store_fix6 = JAVA / "launch" / "RomDownloadTaskStore.kt"
rom_download_scheduler_fix6 = JAVA / "launch" / "RomDownloadScheduler.kt"
rom_download_notification_fix6 = JAVA / "launch" / "RomDownloadNotification.kt"
require_files(fix6_doc, ROOT / "scripts" / "test_beta4_fix6_iisu_orientation.py")
forbid(manifest, 'android:screenOrientation="portrait"')
require(session, "applySessionOrientation()", "SCREEN_ORIENTATION_FULL_SENSOR", "SCREEN_ORIENTATION_SENSOR_LANDSCAPE", "SCREEN_ORIENTATION_SENSOR_PORTRAIT", "EXTRA_EXTERNAL_LAUNCHER")
require(rom_download_store_fix6, "val externalLauncher: Boolean", 'json.optBoolean("externalLauncher", false)', 'put("externalLauncher", snapshot.externalLauncher)')
require(rom_download_scheduler_fix6, "externalLauncher: Boolean = false", "externalLauncher = externalLauncher")
require(rom_download_notification_fix6, "snapshot.externalLauncher", "EXTRA_EXTERNAL_LAUNCHER")

legacy_settings_fix4 = JAVA / "ui" / "SettingsActivity.kt"
legacy_settings_layout_fix4 = RES / "layout" / "activity_settings.xml"
forbid(legacy_settings_fix4, "systemFolderPicker", "systemVirtualFolderButton", "clearSystemVirtualButton", "systemVirtualUriText")
forbid(legacy_settings_layout_fix4, "systemVirtualFolderButton", "clearSystemVirtualButton", "systemVirtualUriText", "Destino por sistema")


# Oz Ready Core 01: concrete ROM source ranking and cartridge ZIP preparation.
archive_preparer = JAVA / "launch" / "RomArchivePreparer.kt"
region_policy = JAVA / "catalog" / "RomgiRegionPolicy.kt"
region_preference = JAVA / "data" / "RomRegionPreference.kt"
preferences = JAVA / "data" / "AppPreferences.kt"
require(matcher, "fun isBroadRegion", "RomgiRegionPolicy.order", 'it != "World" && it != "Other"')
require(
    region_policy,
    "TV_TIMING_SENSITIVE",
    "PORTABLE",
    "ARCADE_EXACT",
    'listOf("USA", "Japan", "World", "Asia", "Europe", "Other")',
    "fun requiresConfirmation",
)
require(region_preference, 'USA("usa"', "fun fromStored", "?: DEFAULT")
require(preferences, "RomRegionPreference.fromStored")
require(romgi, "sourceRegionRank", "RomgiRegionPolicy.rank", "RomgiRegionPolicy.requiresConfirmation")
require(ROOT / "gradlew.bat", "if defined JAVA_HOME", 'set "JAVA_EXE=%JAVA_HOME%\\bin\\java.exe"', '"%JAVA_EXE%" %JAVA_OPTS%')
require(archive_preparer, "ROM_ARCHIVE_EXTRACTED", '"snes" -> setOf("sfc", "smc", "swc", "fig", "bs", "st")', "ZipFile(downloaded)")
require(session, "RomArchivePreparer.prepare(current, prepared, store, diagnostics)", "val directNeedsExtraction = RomArchivePreparer.needsNativeExtraction", "!directNeedsExtraction")
require(JAVA / "launch" / "BackgroundRomDownloadEngine.kt", "RomArchivePreparer.prepare(current, prepared, store, diagnostics)")
require(JAVA / "launch" / "RomPreparedFileValidator.kt", "La fuente disponible está comprimida en 7z")
require(prepared_validator, "RomPreparedPayloadValidator.validate(file)")
require(
    prepared_payload_validator,
    'file.extension.equals("m3u"',
    "validateMultiDiscPlaylist",
    "discNames.size >= 2",
    "disc.isFile && disc.canRead()",
    "disc.length() > MIN_BINARY_PAYLOAD_BYTES",
)

if ERRORS:
    print(f"VALIDACIÓN ESTÁTICA FALLIDA — {len(ERRORS)} hallazgos / {CHECKS} comprobaciones")
    for error in ERRORS:
        print(f"- {error}")
    sys.exit(1)

print(f"VALIDACIÓN ESTÁTICA CORRECTA — Oz RetroRom 0.6.0 Beta 4 ({CHECKS} comprobaciones)")
print(f"Sistemas: {len(systems)} · juegos curados: {len(games)} · colecciones: {len(collections)}")
print("Nota: esta validación no sustituye :app:testDebugUnitTest ni :app:assembleDebug.")

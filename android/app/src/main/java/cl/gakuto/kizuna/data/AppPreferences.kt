package cl.gakuto.kizuna.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

enum class ArtworkMode(val key: String, val label: String) {
    AUTO("auto", "Automático"),
    BOXART("boxart", "Carátula"),
    TITLE_SCREEN("title", "Pantalla de título"),
    SNAP("snap", "Captura del juego"),
    LOCAL_FOLDER("local", "Carpeta local"),
    CUSTOM("custom", "Imagen personalizada");

    companion object {
        fun fromKey(value: String?): ArtworkMode = entries.firstOrNull { it.key == value } ?: AUTO
    }
}

enum class GameTileStyle(val key: String, val label: String) {
    COMPACT_ICON("compact_icon", "Iconos compactos"),
    CLASSIC_COVER("classic_cover", "Carátulas clásicas");

    companion object {
        fun fromKey(value: String?): GameTileStyle = entries.firstOrNull { it.key == value } ?: COMPACT_ICON
    }
}

enum class AppThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Sistema"),
    DARK("dark", "Oscuro"),
    LIGHT("light", "Claro");

    companion object {
        fun fromKey(value: String?): AppThemeMode = entries.firstOrNull { it.key == value } ?: DARK
    }
}

enum class AppOrientationMode(val key: String, val label: String) {
    AUTOMATIC("automatic", "Automática"),
    PORTRAIT("portrait", "Vertical"),
    LANDSCAPE("landscape", "Horizontal");

    companion object {
        fun fromKey(value: String?): AppOrientationMode =
            entries.firstOrNull { it.key == value } ?: AUTOMATIC
    }
}

enum class GameGridDensity(val key: String, val label: String, val minCellDp: Int) {
    AUTO("auto", "Automática", 104),
    SMALL("small", "Pequeña", 88),
    NORMAL("normal", "Normal", 112),
    LARGE("large", "Grande", 142);

    companion object {
        fun fromKey(value: String?): GameGridDensity =
            entries.firstOrNull { it.key == value } ?: AUTO
    }
}

enum class OzMediaKind(val key: String) {
    IMAGE("image"),
    ANIMATED_IMAGE("animated_image"),
    VIDEO("video");

    companion object {
        fun fromKey(value: String?): OzMediaKind =
            entries.firstOrNull { it.key == value } ?: IMAGE
    }
}

enum class CatalogPerformanceMode(val key: String, val label: String, val pageSize: Int) {
    FLUID("fluid", "Fluido (recomendado) · 80 por carga", 80),
    BALANCED("balanced", "Equilibrado · 120 por carga", 120),
    LARGE("large", "Más juegos por carga · 200", 200);

    companion object {
        fun fromKey(value: String?): CatalogPerformanceMode = entries.firstOrNull { it.key == value } ?: FLUID
    }
}

enum class EngineMode(val key: String, val label: String) {
    AUTO("auto", "Automático recomendado por Oz RetroRom"),
    RETROARCH("retroarch", "RetroArch"),
    EXTERNAL("external", "Emulador externo"),
    ASK("ask", "Preguntar al jugar");

    companion object {
        fun fromKey(value: String?): EngineMode = entries.firstOrNull { it.key == value } ?: AUTO
    }
}

data class AuthorizedSource(
    val value: String,
    val expectedFileName: String? = null,
    val torrentFileIndex: Int? = null,
    val torrentFilePath: String? = null,
    val expectedSize: Long = -1L,
    val sourceLabel: String? = null,
    val requiresInternetArchiveAuth: Boolean = false,
) {
    val isMagnet: Boolean get() = value.startsWith("magnet:", ignoreCase = true)
    val isHttp: Boolean get() = value.startsWith("http://", true) || value.startsWith("https://", true)
    val isContent: Boolean get() = value.startsWith("content://", true) || value.startsWith("file://", true)
    val isFilePath: Boolean get() = value.startsWith("/") || value.matches(Regex("^[A-Za-z]:[\\/].*"))
}

/**
 * Facade delegating app preferences to reactive Jetpack DataStore ([OzSettingsDataStore])
 * and per-game syncable metadata to Room v7 ([GameMetadataStore]).
 * Zero synchronous SharedPreferences("oz_retrorom") I/O remains.
 */
class AppPreferences(context: Context) {
    internal val settingsStore = OzSettingsDataStore.get(context)
    private val gameStore = GameMetadataStore.get(context)

    init {
        migrateLegacyKizunaIfNeeded(context)
    }

    var globalVirtualTreeUri: Uri?
        get() = settingsStore.getString(KEY_GLOBAL_VIRTUAL_TREE, null)?.let(Uri::parse)
        set(value) = settingsStore.putString(KEY_GLOBAL_VIRTUAL_TREE, value?.toString())

    fun systemVirtualTreeUri(systemId: String): Uri? =
        settingsStore.getString("$KEY_SYSTEM_VIRTUAL_TREE$systemId", null)?.let(Uri::parse)

    fun setSystemVirtualTreeUri(systemId: String, value: Uri?) =
        settingsStore.putString("$KEY_SYSTEM_VIRTUAL_TREE$systemId", value?.toString())

    fun virtualTreeUriFor(systemId: String): Uri? = systemVirtualTreeUri(systemId) ?: globalVirtualTreeUri

    var globalRomTreeUri: Uri?
        get() = settingsStore.getString(KEY_GLOBAL_ROM_TREE, null)?.let(Uri::parse)
        set(value) = settingsStore.putString(KEY_GLOBAL_ROM_TREE, value?.toString())

    fun systemRomTreeUri(systemId: String): Uri? =
        settingsStore.getString("$KEY_SYSTEM_ROM_TREE$systemId", null)?.let(Uri::parse)

    fun setSystemRomTreeUri(systemId: String, value: Uri?) =
        settingsStore.putString("$KEY_SYSTEM_ROM_TREE$systemId", value?.toString())

    fun romTreeUriFor(systemId: String): Uri? = systemRomTreeUri(systemId) ?: globalRomTreeUri

    var iisuAssetsTreeUri: Uri?
        get() = settingsStore.getString(KEY_IISU_ASSETS_TREE, null)?.let(Uri::parse)
        set(value) = settingsStore.putString(KEY_IISU_ASSETS_TREE, value?.toString())

    var iisuIntegrationEnabled: Boolean
        get() = if (settingsStore.contains(KEY_IISU_INTEGRATION_ENABLED)) {
            settingsStore.getBoolean(KEY_IISU_INTEGRATION_ENABLED, false)
        } else {
            globalVirtualTreeUri != null || settingsStore.keysStartingWith(KEY_SYSTEM_VIRTUAL_TREE).isNotEmpty()
        }
        set(value) = settingsStore.putBoolean(KEY_IISU_INTEGRATION_ENABLED, value)

    var iisuLastSyncAt: Long
        get() = settingsStore.getLong(KEY_IISU_LAST_SYNC_AT, 0L)
        set(value) = settingsStore.putLong(KEY_IISU_LAST_SYNC_AT, value)

    var iisuLastSyncDescriptors: Int
        get() = settingsStore.getInt(KEY_IISU_LAST_SYNC_DESCRIPTORS, 0)
        set(value) = settingsStore.putInt(KEY_IISU_LAST_SYNC_DESCRIPTORS, value.coerceAtLeast(0))

    var iisuLastSyncSystems: Int
        get() = settingsStore.getInt(KEY_IISU_LAST_SYNC_SYSTEMS, 0)
        set(value) = settingsStore.putInt(KEY_IISU_LAST_SYNC_SYSTEMS, value.coerceAtLeast(0))

    fun recordIisuSync(descriptors: Int, systems: Int, syncedAt: Long = System.currentTimeMillis()) {
        settingsStore.putLong(KEY_IISU_LAST_SYNC_AT, syncedAt)
        settingsStore.putInt(KEY_IISU_LAST_SYNC_DESCRIPTORS, descriptors.coerceAtLeast(0))
        settingsStore.putInt(KEY_IISU_LAST_SYNC_SYSTEMS, systems.coerceAtLeast(0))
    }

    var localArtworkTreeUri: Uri?
        get() = settingsStore.getString(KEY_LOCAL_ARTWORK_TREE, null)?.let(Uri::parse)
        set(value) = settingsStore.putString(KEY_LOCAL_ARTWORK_TREE, value?.toString())

    var fbneoBiosTreeUri: Uri?
        get() = settingsStore.getString(KEY_FBNEO_BIOS_TREE, null)?.let(Uri::parse)
        set(value) = settingsStore.putString(KEY_FBNEO_BIOS_TREE, value?.toString())

    var artworkMode: ArtworkMode
        get() = ArtworkMode.fromKey(settingsStore.getString(KEY_ARTWORK_MODE, ArtworkMode.AUTO.key))
        set(value) = settingsStore.putString(KEY_ARTWORK_MODE, value.key)

    var gameTileStyle: GameTileStyle
        get() = GameTileStyle.fromKey(settingsStore.getString(KEY_GAME_TILE_STYLE, GameTileStyle.COMPACT_ICON.key))
        set(value) = settingsStore.putString(KEY_GAME_TILE_STYLE, value.key)

    var appThemeMode: AppThemeMode
        get() = AppThemeMode.fromKey(settingsStore.getString(KEY_APP_THEME_MODE, AppThemeMode.DARK.key))
        set(value) = settingsStore.putString(KEY_APP_THEME_MODE, value.key)

    var appOrientationMode: AppOrientationMode
        get() = AppOrientationMode.fromKey(settingsStore.getString(KEY_APP_ORIENTATION_MODE, AppOrientationMode.AUTOMATIC.key))
        set(value) = settingsStore.putString(KEY_APP_ORIENTATION_MODE, value.key)

    var gameGridDensity: GameGridDensity
        get() = GameGridDensity.fromKey(settingsStore.getString(KEY_GAME_GRID_DENSITY, GameGridDensity.AUTO.key))
        set(value) = settingsStore.putString(KEY_GAME_GRID_DENSITY, value.key)

    var wallpaperUri: Uri?
        get() = settingsStore.getString(KEY_WALLPAPER_URI, null)?.let(Uri::parse)
        set(value) = settingsStore.putString(KEY_WALLPAPER_URI, value?.toString())

    var wallpaperKind: OzMediaKind
        get() = OzMediaKind.fromKey(settingsStore.getString(KEY_WALLPAPER_KIND, OzMediaKind.IMAGE.key))
        set(value) = settingsStore.putString(KEY_WALLPAPER_KIND, value.key)

    var navigationSoundtrackUri: Uri?
        get() = settingsStore.getString(KEY_NAV_SOUNDTRACK_URI, null)?.let(Uri::parse)
        set(value) = settingsStore.putString(KEY_NAV_SOUNDTRACK_URI, value?.toString())

    var navigationSoundtrackEnabled: Boolean
        get() = settingsStore.getBoolean(KEY_NAV_SOUNDTRACK_ENABLED, true)
        set(value) = settingsStore.putBoolean(KEY_NAV_SOUNDTRACK_ENABLED, value)

    fun heroMediaUri(gameId: String): Uri? =
        settingsStore.getString("$KEY_HERO_MEDIA_URI$gameId", null)?.let(Uri::parse)

    fun heroMediaKind(gameId: String): OzMediaKind =
        OzMediaKind.fromKey(settingsStore.getString("$KEY_HERO_MEDIA_KIND$gameId", OzMediaKind.VIDEO.key))

    fun heroMediaAudioEnabled(gameId: String): Boolean =
        settingsStore.getBoolean("$KEY_HERO_MEDIA_AUDIO$gameId", false)

    fun setHeroMedia(gameId: String, uri: Uri?, kind: OzMediaKind = OzMediaKind.VIDEO) {
        if (uri == null) {
            settingsStore.removeAll(
                listOf("$KEY_HERO_MEDIA_URI$gameId", "$KEY_HERO_MEDIA_KIND$gameId", "$KEY_HERO_MEDIA_AUDIO$gameId")
            )
        } else {
            settingsStore.putString("$KEY_HERO_MEDIA_URI$gameId", uri.toString())
            settingsStore.putString("$KEY_HERO_MEDIA_KIND$gameId", kind.key)
        }
    }

    fun setHeroMediaAudioEnabled(gameId: String, enabled: Boolean) =
        settingsStore.putBoolean("$KEY_HERO_MEDIA_AUDIO$gameId", enabled)

    data class SurfacePlacement(
        val page: Int = 0,
        val slot: Int = 0,
        val size: String = "1x1",
    )

    fun surfacePlacement(gameId: String): SurfacePlacement? {
        val raw = settingsStore.getString("$KEY_SURFACE_PLACEMENT$gameId", null) ?: return null
        val parts = raw.split('|')
        if (parts.size != 3) return null
        return SurfacePlacement(
            page = parts[0].toIntOrNull()?.coerceAtLeast(0) ?: 0,
            slot = parts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0,
            size = parts[2].takeIf { it in SURFACE_SIZES } ?: "1x1",
        )
    }

    fun setSurfacePlacement(gameId: String, page: Int, slot: Int, size: String) {
        val safeSize = size.takeIf { it in SURFACE_SIZES } ?: "1x1"
        settingsStore.putString(
            "$KEY_SURFACE_PLACEMENT$gameId",
            "${page.coerceAtLeast(0)}|${slot.coerceAtLeast(0)}|$safeSize",
        )
    }

    fun setSurfaceSize(gameId: String, size: String) {
        val resized = resizedSurfacePlacement(surfacePlacement(gameId), size)
        setSurfacePlacement(gameId, resized.page, resized.slot, resized.size)
    }

    fun clearSurfacePlacement(gameId: String) =
        settingsStore.putString("$KEY_SURFACE_PLACEMENT$gameId", null)

    var steamGridDbApiKey: String
        get() = settingsStore.getString(KEY_STEAMGRIDDB_API_KEY, "").orEmpty()
        set(value) = settingsStore.putString(KEY_STEAMGRIDDB_API_KEY, value.trim())

    fun gameArtworkMode(gameId: String): ArtworkMode? =
        settingsStore.getString("$KEY_GAME_ARTWORK_MODE$gameId", null)?.let(ArtworkMode::fromKey)

    fun setGameArtworkMode(gameId: String, value: ArtworkMode?) =
        settingsStore.putString("$KEY_GAME_ARTWORK_MODE$gameId", value?.key)

    // Per-game syncable metadata stored in Room v7 (game_sync_metadata)
    fun customArtworkUrl(gameId: String): String? =
        gameStore.get(gameId)?.customArtworkUrl ?: settingsStore.getString("$KEY_CUSTOM_ARTWORK_URL$gameId", null)

    fun setCustomArtworkUrl(gameId: String, value: String?) {
        gameStore.saveCustomArtworkUrl(gameId, value)
        settingsStore.putString("$KEY_CUSTOM_ARTWORK_URL$gameId", value?.trim()?.takeIf { it.isNotBlank() })
    }

    fun customArtworkUri(gameId: String): Uri? =
        (gameStore.get(gameId)?.customArtworkUri ?: settingsStore.getString("$KEY_CUSTOM_ARTWORK_URI$gameId", null))?.let(Uri::parse)

    fun setCustomArtworkUri(gameId: String, value: Uri?) {
        gameStore.saveCustomArtworkUri(gameId, value?.toString())
        settingsStore.putString("$KEY_CUSTOM_ARTWORK_URI$gameId", value?.toString())
    }

    fun syncedArtworkTitle(gameId: String): String? =
        (gameStore.get(gameId)?.syncedArtworkTitle ?: settingsStore.getString("$KEY_SYNCED_ARTWORK_TITLE$gameId", null))
            ?.takeIf { it.isNotBlank() }

    fun syncedArtworkRepo(gameId: String): String? =
        (gameStore.get(gameId)?.syncedArtworkRepo ?: settingsStore.getString("$KEY_SYNCED_ARTWORK_REPO$gameId", null))
            ?.takeIf { it.isNotBlank() }

    fun syncedArtworkLocalUri(gameId: String): String? =
        (gameStore.get(gameId)?.syncedArtworkLocalUri ?: settingsStore.getString("$KEY_SYNCED_ARTWORK_LOCAL_URI$gameId", null))
            ?.takeIf { it.isNotBlank() }

    fun syncedGameIconLocalUri(gameId: String): String? =
        (gameStore.get(gameId)?.syncedGameIconLocalUri ?: settingsStore.getString("$KEY_SYNCED_GAME_ICON_LOCAL_URI$gameId", null))
            ?.takeIf { it.isNotBlank() }

    fun setSyncedGameIconLocalUri(gameId: String, value: String?) =
        gameStore.saveSyncedGameIconLocalUri(gameId, value)

    fun steamGridDbGameId(gameId: String): Int? =
        settingsStore.getInt("$KEY_STEAMGRIDDB_GAME_ID$gameId", -1).takeIf { it > 0 }

    fun setSteamGridDbGameId(gameId: String, value: Int?) {
        if (value == null || value <= 0) settingsStore.removeAll(listOf("$KEY_STEAMGRIDDB_GAME_ID$gameId"))
        else settingsStore.putInt("$KEY_STEAMGRIDDB_GAME_ID$gameId", value)
    }

    fun setSyncedArtworkLocalUri(gameId: String, value: String?) =
        gameStore.saveSyncedArtworkLocalUri(gameId, value)

    fun setSyncedArtworkAlias(gameId: String, title: String?, repository: String?) =
        gameStore.saveSyncedArtworkAlias(gameId, title, repository)

    data class CollectionArtworkSyncSnapshot(
        val resolved: Int,
        val total: Int,
        val syncedAt: Long,
    )

    fun collectionArtworkSyncSnapshot(collectionId: String): CollectionArtworkSyncSnapshot? {
        val syncedAt = settingsStore.getLong("$KEY_COLLECTION_ARTWORK_SYNC_AT$collectionId", 0L)
        if (syncedAt <= 0L) return null
        return CollectionArtworkSyncSnapshot(
            resolved = settingsStore.getInt("$KEY_COLLECTION_ARTWORK_SYNC_RESOLVED$collectionId", 0),
            total = settingsStore.getInt("$KEY_COLLECTION_ARTWORK_SYNC_TOTAL$collectionId", 0),
            syncedAt = syncedAt,
        )
    }

    fun setCollectionArtworkSyncSnapshot(collectionId: String, resolved: Int, total: Int, syncedAt: Long = System.currentTimeMillis()) {
        settingsStore.putInt("$KEY_COLLECTION_ARTWORK_SYNC_RESOLVED$collectionId", resolved.coerceAtLeast(0))
        settingsStore.putInt("$KEY_COLLECTION_ARTWORK_SYNC_TOTAL$collectionId", total.coerceAtLeast(0))
        settingsStore.putLong("$KEY_COLLECTION_ARTWORK_SYNC_AT$collectionId", syncedAt)
    }

    fun authorizedSource(gameId: String): AuthorizedSource? {
        val value = settingsStore.getString("$KEY_SOURCE$gameId", null)?.takeIf { it.isNotBlank() } ?: return null
        return AuthorizedSource(value, settingsStore.getString("$KEY_SOURCE_FILENAME$gameId", null)?.takeIf { it.isNotBlank() })
    }

    fun setAuthorizedSource(gameId: String, source: AuthorizedSource?) {
        if (source == null) {
            settingsStore.removeAll(listOf("$KEY_SOURCE$gameId", "$KEY_SOURCE_FILENAME$gameId"))
        } else {
            settingsStore.putString("$KEY_SOURCE$gameId", source.value.trim())
            settingsStore.putString("$KEY_SOURCE_FILENAME$gameId", source.expectedFileName?.trim()?.takeIf { it.isNotBlank() })
        }
    }

    var romgiAutoEnabled: Boolean
        get() = settingsStore.getBoolean(KEY_ROMGI_AUTO, true)
        set(value) = settingsStore.putBoolean(KEY_ROMGI_AUTO, value)

    var romgiWifiOnly: Boolean
        get() = settingsStore.getBoolean(KEY_ROMGI_WIFI_ONLY, false)
        set(value) = settingsStore.putBoolean(KEY_ROMGI_WIFI_ONLY, value)

    var romgiTorrentsEnabled: Boolean
        get() = settingsStore.getBoolean(KEY_ROMGI_TORRENTS, true)
        set(value) = settingsStore.putBoolean(KEY_ROMGI_TORRENTS, value)

    var preferredRegion: String
        get() = RomRegionPreference.fromStored(settingsStore.getString(KEY_PREFERRED_REGION, null)).key
        set(value) = settingsStore.putString(KEY_PREFERRED_REGION, RomRegionPreference.fromStored(value).key)

    var retroArchPackage: String
        get() = settingsStore.getString(KEY_RETROARCH_PACKAGE, "com.retroarch.aarch64") ?: "com.retroarch.aarch64"
        set(value) = settingsStore.putString(KEY_RETROARCH_PACKAGE, value.trim().ifBlank { "com.retroarch.aarch64" })

    var retroArchCoreTreeUri: Uri?
        get() = settingsStore.getString(KEY_RETROARCH_CORE_TREE, null)?.let(Uri::parse)
        set(value) = settingsStore.putString(KEY_RETROARCH_CORE_TREE, value?.toString())

    var retroArchSystemTreeUri: Uri?
        get() = settingsStore.getString(KEY_RETROARCH_SYSTEM_TREE, null)?.let(Uri::parse)
        set(value) = settingsStore.putString(KEY_RETROARCH_SYSTEM_TREE, value?.toString())

    var retroArchOverlayTreeUri: Uri?
        get() = settingsStore.getString(KEY_RETROARCH_OVERLAY_TREE, null)?.let(Uri::parse)
        set(value) = settingsStore.putString(KEY_RETROARCH_OVERLAY_TREE, value?.toString())

    var retroArchAutoInstallCores: Boolean
        get() = settingsStore.getBoolean(KEY_RETROARCH_AUTO_INSTALL_CORES, true)
        set(value) = settingsStore.putBoolean(KEY_RETROARCH_AUTO_INSTALL_CORES, value)

    var autoLaunchAfterDownload: Boolean
        get() = settingsStore.getBoolean(KEY_AUTO_LAUNCH_AFTER_DOWNLOAD, true)
        set(value) = settingsStore.putBoolean(KEY_AUTO_LAUNCH_AFTER_DOWNLOAD, value)

    var ftpTargetsJson: String
        get() = settingsStore.getString(KEY_FTP_TARGETS_JSON, "").orEmpty()
        set(value) = settingsStore.putString(KEY_FTP_TARGETS_JSON, value)

    var pcHubUrl: String
        get() = settingsStore.getString(KEY_PC_HUB_URL, "").orEmpty()
        set(value) = settingsStore.putString(KEY_PC_HUB_URL, value.trim())

    var geminiApiKey: String
        get() = settingsStore.getString(KEY_GEMINI_API_KEY, "").orEmpty()
        set(value) = settingsStore.putString(KEY_GEMINI_API_KEY, value.trim())

    var geminiModel: String
        get() = settingsStore.getString(KEY_GEMINI_MODEL, "gemini-2.5-flash").orEmpty().ifBlank { "gemini-2.5-flash" }
        set(value) = settingsStore.putString(KEY_GEMINI_MODEL, value.trim().ifBlank { "gemini-2.5-flash" })

    fun cachedDescription(gameId: String): String? =
        (gameStore.get(gameId)?.cachedDescription ?: settingsStore.getString("$KEY_GAME_DESCRIPTION$gameId", null))
            ?.takeIf { it.isNotBlank() }

    fun setCachedDescription(gameId: String, description: String) =
        gameStore.saveCachedDescription(gameId, description)

    data class PatchedRomMeta(
        val path: String,
        val label: String,
        val format: String,
    )

    fun patchedRom(gameId: String): PatchedRomMeta? {
        val roomRow = gameStore.get(gameId)
        val path = roomRow?.patchedRomPath?.takeIf { it.isNotBlank() }
            ?: settingsStore.getString("$KEY_PATCHED_ROM_PATH$gameId", null)?.takeIf { it.isNotBlank() }
            ?: return null
        val file = java.io.File(path)
        if (!file.isFile || !file.canRead()) return null
        return PatchedRomMeta(
            path = path,
            label = roomRow?.patchedRomLabel ?: settingsStore.getString("$KEY_PATCHED_ROM_LABEL$gameId", "Traducción [ES]").orEmpty(),
            format = roomRow?.patchedRomFormat ?: settingsStore.getString("$KEY_PATCHED_ROM_FORMAT$gameId", "IPS").orEmpty(),
        )
    }

    fun setPatchedRom(gameId: String, path: String, label: String, format: String) =
        gameStore.savePatchedRom(gameId, path, label, format)

    fun clearPatchedRom(gameId: String) =
        gameStore.clearPatchedRom(gameId)

    fun isRetroArchCoreConfirmed(packageName: String, coreName: String): Boolean =
        settingsStore.getStringSet(KEY_RETROARCH_CONFIRMED_CORES)
            .contains("${packageName.trim()}|${coreName.trim().lowercase()}")

    fun setRetroArchCoreConfirmed(packageName: String, coreName: String, confirmed: Boolean) {
        val key = "${packageName.trim()}|${coreName.trim().lowercase()}"
        val values = settingsStore.getStringSet(KEY_RETROARCH_CONFIRMED_CORES).toMutableSet()
        if (confirmed) values += key else values -= key
        settingsStore.putStringSet(KEY_RETROARCH_CONFIRMED_CORES, values)
    }

    fun engineMode(systemId: String): EngineMode =
        EngineMode.fromKey(settingsStore.getString("$KEY_ENGINE_MODE$systemId", EngineMode.AUTO.key))

    fun setEngineMode(systemId: String, mode: EngineMode) =
        settingsStore.putString("$KEY_ENGINE_MODE$systemId", mode.key)

    fun externalEmulatorPackage(systemId: String): String? =
        settingsStore.getString("$KEY_EXTERNAL_PACKAGE$systemId", null)?.takeIf(String::isNotBlank)

    fun setExternalEmulatorPackage(systemId: String, packageName: String?) =
        settingsStore.putString("$KEY_EXTERNAL_PACKAGE$systemId", packageName?.trim()?.takeIf { it.isNotBlank() })

    fun restoreAutomaticEngine(systemId: String) =
        settingsStore.removeAll(listOf("$KEY_ENGINE_MODE$systemId", "$KEY_EXTERNAL_PACKAGE$systemId"))

    fun restoreAllAutomaticEngines(systemIds: Collection<String>) {
        val keys = systemIds.flatMap { listOf("$KEY_ENGINE_MODE$it", "$KEY_EXTERNAL_PACKAGE$it") }
        settingsStore.removeAll(keys)
    }

    var ps2EmulatorPackage: String
        get() = settingsStore.getString(KEY_PS2_PACKAGE, "").orEmpty()
        set(value) = settingsStore.putString(KEY_PS2_PACKAGE, value.trim())

    var threeDsEmulatorPackage: String
        get() = settingsStore.getString(KEY_3DS_PACKAGE, "").orEmpty()
        set(value) = settingsStore.putString(KEY_3DS_PACKAGE, value.trim())

    var deleteAfterPlay: Boolean
        get() = settingsStore.getBoolean(KEY_DELETE_AFTER_PLAY, true)
        set(value) = settingsStore.putBoolean(KEY_DELETE_AFTER_PLAY, value)

    var skipIndexedSystems: Boolean
        get() = settingsStore.getBoolean(KEY_SKIP_INDEXED, true)
        set(value) = settingsStore.putBoolean(KEY_SKIP_INDEXED, value)

    var catalogPerformanceMode: CatalogPerformanceMode
        get() = CatalogPerformanceMode.fromKey(settingsStore.getString(KEY_CATALOG_PERFORMANCE, CatalogPerformanceMode.FLUID.key))
        set(value) = settingsStore.putString(KEY_CATALOG_PERFORMANCE, value.key)

    private fun migrateLegacyKizunaIfNeeded(context: Context) {
        if (settingsStore.getBoolean(KEY_LEGACY_MIGRATED, false)) return
        val legacyFile = java.io.File(context.applicationInfo.dataDir, "shared_prefs/gakuto_kizuna.xml")
        if (!legacyFile.exists()) {
            settingsStore.putBoolean(KEY_LEGACY_MIGRATED, true)
            return
        }
        val legacy = context.getSharedPreferences("gakuto_kizuna", Context.MODE_PRIVATE)
        val cacheUpdates = linkedMapOf<String, Any?>(KEY_LEGACY_MIGRATED to true)
        legacy.getString("library_tree", null)?.takeIf { !settingsStore.contains(KEY_GLOBAL_VIRTUAL_TREE) }?.let {
            cacheUpdates[KEY_GLOBAL_VIRTUAL_TREE] = it
        }
        legacy.getString("iisu_assets_tree", null)?.takeIf { !settingsStore.contains(KEY_IISU_ASSETS_TREE) }?.let {
            cacheUpdates[KEY_IISU_ASSETS_TREE] = it
        }
        legacy.getString("retroarch_package", null)?.takeIf { !settingsStore.contains(KEY_RETROARCH_PACKAGE) }?.let {
            cacheUpdates[KEY_RETROARCH_PACKAGE] = it
        }
        legacy.getString("ps2_package", null)?.takeIf { !settingsStore.contains(KEY_PS2_PACKAGE) }?.let {
            cacheUpdates[KEY_PS2_PACKAGE] = it
        }
        legacy.getString("n3ds_package", null)?.takeIf { !settingsStore.contains(KEY_3DS_PACKAGE) }?.let {
            cacheUpdates[KEY_3DS_PACKAGE] = it
        }
        if (legacy.contains("delete_after_play") && !settingsStore.contains(KEY_DELETE_AFTER_PLAY)) {
            cacheUpdates[KEY_DELETE_AFTER_PLAY] = legacy.getBoolean("delete_after_play", true)
        }
        legacy.all.forEach { (key, value) ->
            when {
                key.startsWith("artwork_") && value is String -> {
                    val gameId = key.removePrefix("artwork_")
                    setCustomArtworkUrl(gameId, value)
                    setGameArtworkMode(gameId, ArtworkMode.CUSTOM)
                }
                key.startsWith("source_") && value is String -> {
                    val gameId = key.removePrefix("source_")
                    if (!settingsStore.contains("$KEY_SOURCE$gameId")) cacheUpdates["$KEY_SOURCE$gameId"] = value
                }
            }
        }
        settingsStore.updateSync(cacheUpdates) { prefs ->
            cacheUpdates.forEach { (k, v) ->
                when (v) {
                    is Boolean -> prefs[booleanPreferencesKey(k)] = v
                    is String -> prefs[stringPreferencesKey(k)] = v
                }
            }
        }
    }

    companion object {
        internal val SURFACE_SIZES = setOf("1x1", "2x1", "2x2")

        internal fun resizedSurfacePlacement(
            current: SurfacePlacement?,
            requestedSize: String,
        ): SurfacePlacement {
            val safeSize = requestedSize.takeIf { it in SURFACE_SIZES } ?: "1x1"
            return SurfacePlacement(
                page = current?.page?.coerceAtLeast(0) ?: 0,
                slot = current?.slot?.coerceAtLeast(0) ?: 0,
                size = safeSize,
            )
        }

        internal const val KEY_GLOBAL_VIRTUAL_TREE = "global_virtual_tree"
        internal const val KEY_SYSTEM_VIRTUAL_TREE = "system_virtual_tree_"
        internal const val KEY_GLOBAL_ROM_TREE = "global_rom_tree"
        internal const val KEY_SYSTEM_ROM_TREE = "system_rom_tree_"
        internal const val KEY_IISU_ASSETS_TREE = "iisu_assets_tree"
        internal const val KEY_IISU_INTEGRATION_ENABLED = "iisu_integration_enabled"
        internal const val KEY_IISU_LAST_SYNC_AT = "iisu_last_sync_at"
        internal const val KEY_IISU_LAST_SYNC_DESCRIPTORS = "iisu_last_sync_descriptors"
        internal const val KEY_IISU_LAST_SYNC_SYSTEMS = "iisu_last_sync_systems"
        internal const val KEY_LOCAL_ARTWORK_TREE = "local_artwork_tree"
        internal const val KEY_FBNEO_BIOS_TREE = "fbneo_bios_tree"
        internal const val KEY_ARTWORK_MODE = "artwork_mode"
        internal const val KEY_GAME_TILE_STYLE = "game_tile_style"
        internal const val KEY_APP_THEME_MODE = "app_theme_mode"
        internal const val KEY_APP_ORIENTATION_MODE = "app_orientation_mode"
        internal const val KEY_GAME_GRID_DENSITY = "game_grid_density"
        internal const val KEY_WALLPAPER_URI = "wallpaper_uri"
        internal const val KEY_WALLPAPER_KIND = "wallpaper_kind"
        internal const val KEY_NAV_SOUNDTRACK_URI = "navigation_soundtrack_uri"
        internal const val KEY_NAV_SOUNDTRACK_ENABLED = "navigation_soundtrack_enabled"
        internal const val KEY_HERO_MEDIA_URI = "hero_media_uri_"
        internal const val KEY_HERO_MEDIA_KIND = "hero_media_kind_"
        internal const val KEY_HERO_MEDIA_AUDIO = "hero_media_audio_"
        internal const val KEY_SURFACE_PLACEMENT = "surface_placement_"
        internal const val KEY_STEAMGRIDDB_API_KEY = "steamgriddb_api_key"
        internal const val KEY_GAME_ARTWORK_MODE = "game_artwork_mode_"
        internal const val KEY_CUSTOM_ARTWORK_URL = "custom_artwork_url_"
        internal const val KEY_CUSTOM_ARTWORK_URI = "custom_artwork_uri_"
        internal const val KEY_SYNCED_ARTWORK_TITLE = "synced_artwork_title_"
        internal const val KEY_SYNCED_ARTWORK_REPO = "synced_artwork_repo_"
        internal const val KEY_SYNCED_ARTWORK_LOCAL_URI = "synced_artwork_local_uri_"
        internal const val KEY_SYNCED_GAME_ICON_LOCAL_URI = "synced_game_icon_local_uri_"
        internal const val KEY_STEAMGRIDDB_GAME_ID = "steamgriddb_game_id_"
        internal const val KEY_COLLECTION_ARTWORK_SYNC_RESOLVED = "collection_artwork_sync_resolved_"
        internal const val KEY_COLLECTION_ARTWORK_SYNC_TOTAL = "collection_artwork_sync_total_"
        internal const val KEY_COLLECTION_ARTWORK_SYNC_AT = "collection_artwork_sync_at_"
        internal const val KEY_SOURCE = "authorized_source_"
        internal const val KEY_SOURCE_FILENAME = "authorized_source_filename_"
        internal const val KEY_ROMGI_AUTO = "romgi_auto_enabled"
        internal const val KEY_ROMGI_WIFI_ONLY = "romgi_wifi_only"
        internal const val KEY_ROMGI_TORRENTS = "romgi_torrents_enabled"
        internal const val KEY_PREFERRED_REGION = "preferred_region"
        internal const val KEY_RETROARCH_PACKAGE = "retroarch_package"
        internal const val KEY_RETROARCH_CORE_TREE = "retroarch_core_tree"
        internal const val KEY_RETROARCH_SYSTEM_TREE = "retroarch_system_tree"
        internal const val KEY_RETROARCH_OVERLAY_TREE = "retroarch_overlay_tree"
        internal const val KEY_RETROARCH_AUTO_INSTALL_CORES = "retroarch_auto_install_cores"
        internal const val KEY_AUTO_LAUNCH_AFTER_DOWNLOAD = "auto_launch_after_download"
        internal const val KEY_FTP_TARGETS_JSON = "ftp_targets_json"
        internal const val KEY_PC_HUB_URL = "pc_hub_url"
        internal const val KEY_GAME_DESCRIPTION = "game_description_"
        internal const val KEY_PATCHED_ROM_PATH = "patched_rom_path_"
        internal const val KEY_PATCHED_ROM_LABEL = "patched_rom_label_"
        internal const val KEY_PATCHED_ROM_FORMAT = "patched_rom_format_"
        internal const val KEY_RETROARCH_CONFIRMED_CORES = "retroarch_confirmed_cores"
        internal const val KEY_ENGINE_MODE = "engine_mode_"
        internal const val KEY_EXTERNAL_PACKAGE = "external_emulator_package_"
        internal const val KEY_PS2_PACKAGE = "ps2_package"
        internal const val KEY_3DS_PACKAGE = "n3ds_package"
        internal const val KEY_DELETE_AFTER_PLAY = "delete_after_play"
        internal const val KEY_SKIP_INDEXED = "skip_indexed"
        internal const val KEY_CATALOG_PERFORMANCE = "catalog_performance_mode"
        internal const val KEY_LEGACY_MIGRATED = "legacy_preferences_migrated"
        internal const val KEY_GEMINI_API_KEY = "gemini_api_key"
        internal const val KEY_GEMINI_MODEL = "gemini_model"
    }
}

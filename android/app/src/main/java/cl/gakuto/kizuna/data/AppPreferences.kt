package cl.gakuto.kizuna.data

import android.content.Context
import android.net.Uri
import cl.gakuto.kizuna.BuildConfig

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

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("oz_retrorom", Context.MODE_PRIVATE)

    init {
        migrateLegacyPreferences(context)
    }

    var globalVirtualTreeUri: Uri?
        get() = prefs.getString(KEY_GLOBAL_VIRTUAL_TREE, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_GLOBAL_VIRTUAL_TREE, value?.toString()).apply()

    fun systemVirtualTreeUri(systemId: String): Uri? =
        prefs.getString("$KEY_SYSTEM_VIRTUAL_TREE$systemId", null)?.let(Uri::parse)

    fun setSystemVirtualTreeUri(systemId: String, value: Uri?) {
        prefs.edit().putString("$KEY_SYSTEM_VIRTUAL_TREE$systemId", value?.toString()).apply()
    }

    fun virtualTreeUriFor(systemId: String): Uri? = systemVirtualTreeUri(systemId) ?: globalVirtualTreeUri

    var globalRomTreeUri: Uri?
        get() = prefs.getString(KEY_GLOBAL_ROM_TREE, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_GLOBAL_ROM_TREE, value?.toString()).apply()

    fun systemRomTreeUri(systemId: String): Uri? =
        prefs.getString("$KEY_SYSTEM_ROM_TREE$systemId", null)?.let(Uri::parse)

    fun setSystemRomTreeUri(systemId: String, value: Uri?) {
        prefs.edit().putString("$KEY_SYSTEM_ROM_TREE$systemId", value?.toString()).apply()
    }

    fun romTreeUriFor(systemId: String): Uri? = systemRomTreeUri(systemId) ?: globalRomTreeUri

    var iisuAssetsTreeUri: Uri?
        get() = prefs.getString(KEY_IISU_ASSETS_TREE, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_IISU_ASSETS_TREE, value?.toString()).apply()

    /**
     * iiSU is an optional consumer of Oz RetroRom's persistent library. The legacy virtual-tree
     * preferences are retained only as bridge/output targets; they are no longer treated as a
     * second ROM library. Existing configured users are considered enabled on first Beta 4 run.
     */
    var iisuIntegrationEnabled: Boolean
        get() = if (prefs.contains(KEY_IISU_INTEGRATION_ENABLED)) {
            prefs.getBoolean(KEY_IISU_INTEGRATION_ENABLED, false)
        } else {
            globalVirtualTreeUri != null || prefs.all.keys.any { it.startsWith(KEY_SYSTEM_VIRTUAL_TREE) }
        }
        set(value) = prefs.edit().putBoolean(KEY_IISU_INTEGRATION_ENABLED, value).apply()

    var iisuLastSyncAt: Long
        get() = prefs.getLong(KEY_IISU_LAST_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_IISU_LAST_SYNC_AT, value).apply()

    var iisuLastSyncDescriptors: Int
        get() = prefs.getInt(KEY_IISU_LAST_SYNC_DESCRIPTORS, 0)
        set(value) = prefs.edit().putInt(KEY_IISU_LAST_SYNC_DESCRIPTORS, value.coerceAtLeast(0)).apply()

    var iisuLastSyncSystems: Int
        get() = prefs.getInt(KEY_IISU_LAST_SYNC_SYSTEMS, 0)
        set(value) = prefs.edit().putInt(KEY_IISU_LAST_SYNC_SYSTEMS, value.coerceAtLeast(0)).apply()

    fun recordIisuSync(descriptors: Int, systems: Int, syncedAt: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(KEY_IISU_LAST_SYNC_AT, syncedAt)
            .putInt(KEY_IISU_LAST_SYNC_DESCRIPTORS, descriptors.coerceAtLeast(0))
            .putInt(KEY_IISU_LAST_SYNC_SYSTEMS, systems.coerceAtLeast(0))
            .apply()
    }

    var localArtworkTreeUri: Uri?
        get() = prefs.getString(KEY_LOCAL_ARTWORK_TREE, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_LOCAL_ARTWORK_TREE, value?.toString()).apply()

    var fbneoBiosTreeUri: Uri?
        get() = prefs.getString(KEY_FBNEO_BIOS_TREE, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_FBNEO_BIOS_TREE, value?.toString()).apply()

    var artworkMode: ArtworkMode
        get() = ArtworkMode.fromKey(prefs.getString(KEY_ARTWORK_MODE, ArtworkMode.AUTO.key))
        set(value) = prefs.edit().putString(KEY_ARTWORK_MODE, value.key).apply()

    /** Navigation tiles use square SteamGridDB-style icons by default; detail screens keep box art. */
    var gameTileStyle: GameTileStyle
        get() = GameTileStyle.fromKey(prefs.getString(KEY_GAME_TILE_STYLE, GameTileStyle.COMPACT_ICON.key))
        set(value) = prefs.edit().putString(KEY_GAME_TILE_STYLE, value.key).apply()

    /** Explicit Oz appearance override. Dark remains the default to preserve existing installs. */
    var appThemeMode: AppThemeMode
        get() = AppThemeMode.fromKey(prefs.getString(KEY_APP_THEME_MODE, AppThemeMode.DARK.key))
        set(value) = prefs.edit().putString(KEY_APP_THEME_MODE, value.key).apply()

    var appOrientationMode: AppOrientationMode
        get() = AppOrientationMode.fromKey(prefs.getString(KEY_APP_ORIENTATION_MODE, AppOrientationMode.AUTOMATIC.key))
        set(value) = prefs.edit().putString(KEY_APP_ORIENTATION_MODE, value.key).apply()

    var gameGridDensity: GameGridDensity
        get() = GameGridDensity.fromKey(prefs.getString(KEY_GAME_GRID_DENSITY, GameGridDensity.AUTO.key))
        set(value) = prefs.edit().putString(KEY_GAME_GRID_DENSITY, value.key).apply()

    var wallpaperUri: Uri?
        get() = prefs.getString(KEY_WALLPAPER_URI, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_WALLPAPER_URI, value?.toString()).apply()

    var wallpaperKind: OzMediaKind
        get() = OzMediaKind.fromKey(prefs.getString(KEY_WALLPAPER_KIND, OzMediaKind.IMAGE.key))
        set(value) = prefs.edit().putString(KEY_WALLPAPER_KIND, value.key).apply()

    var navigationSoundtrackUri: Uri?
        get() = prefs.getString(KEY_NAV_SOUNDTRACK_URI, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_NAV_SOUNDTRACK_URI, value?.toString()).apply()

    var navigationSoundtrackEnabled: Boolean
        get() = prefs.getBoolean(KEY_NAV_SOUNDTRACK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NAV_SOUNDTRACK_ENABLED, value).apply()

    fun heroMediaUri(gameId: String): Uri? =
        prefs.getString("$KEY_HERO_MEDIA_URI$gameId", null)?.let(Uri::parse)

    fun heroMediaKind(gameId: String): OzMediaKind =
        OzMediaKind.fromKey(prefs.getString("$KEY_HERO_MEDIA_KIND$gameId", OzMediaKind.VIDEO.key))

    fun heroMediaAudioEnabled(gameId: String): Boolean =
        prefs.getBoolean("$KEY_HERO_MEDIA_AUDIO$gameId", false)

    fun setHeroMedia(gameId: String, uri: Uri?, kind: OzMediaKind = OzMediaKind.VIDEO) {
        val edit = prefs.edit()
        if (uri == null) {
            edit.remove("$KEY_HERO_MEDIA_URI$gameId")
            edit.remove("$KEY_HERO_MEDIA_KIND$gameId")
            edit.remove("$KEY_HERO_MEDIA_AUDIO$gameId")
        } else {
            edit.putString("$KEY_HERO_MEDIA_URI$gameId", uri.toString())
            edit.putString("$KEY_HERO_MEDIA_KIND$gameId", kind.key)
        }
        edit.apply()
    }

    fun setHeroMediaAudioEnabled(gameId: String, enabled: Boolean) {
        prefs.edit().putBoolean("$KEY_HERO_MEDIA_AUDIO$gameId", enabled).apply()
    }

    data class SurfacePlacement(
        val page: Int = 0,
        val slot: Int = 0,
        val size: String = "1x1",
    )

    fun surfacePlacement(gameId: String): SurfacePlacement? {
        val raw = prefs.getString("$KEY_SURFACE_PLACEMENT$gameId", null) ?: return null
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
        prefs.edit()
            .putString(
                "$KEY_SURFACE_PLACEMENT$gameId",
                "${page.coerceAtLeast(0)}|${slot.coerceAtLeast(0)}|$safeSize",
            )
            .apply()
    }

    /**
     * Changes only the tile size. Page/slot are preserved so a quick-menu resize never teleports a
     * game back to the first cell of the first page.
     */
    fun setSurfaceSize(gameId: String, size: String) {
        val resized = resizedSurfacePlacement(surfacePlacement(gameId), size)
        setSurfacePlacement(gameId, resized.page, resized.slot, resized.size)
    }

    fun clearSurfacePlacement(gameId: String) {
        prefs.edit().remove("$KEY_SURFACE_PLACEMENT$gameId").apply()
    }

    /** User-owned SteamGridDB API key. Stored in this app's private preferences and never exported. */
    var steamGridDbApiKey: String
        get() = prefs.getString(KEY_STEAMGRIDDB_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_STEAMGRIDDB_API_KEY, value.trim()).apply()

    fun gameArtworkMode(gameId: String): ArtworkMode? =
        prefs.getString("$KEY_GAME_ARTWORK_MODE$gameId", null)?.let(ArtworkMode::fromKey)

    fun setGameArtworkMode(gameId: String, value: ArtworkMode?) {
        val edit = prefs.edit()
        if (value == null) edit.remove("$KEY_GAME_ARTWORK_MODE$gameId")
        else edit.putString("$KEY_GAME_ARTWORK_MODE$gameId", value.key)
        edit.apply()
    }

    fun customArtworkUrl(gameId: String): String? = prefs.getString("$KEY_CUSTOM_ARTWORK_URL$gameId", null)
    fun setCustomArtworkUrl(gameId: String, value: String?) {
        prefs.edit().putString("$KEY_CUSTOM_ARTWORK_URL$gameId", value?.trim()?.takeIf { it.isNotBlank() }).apply()
    }

    fun customArtworkUri(gameId: String): Uri? =
        prefs.getString("$KEY_CUSTOM_ARTWORK_URI$gameId", null)?.let(Uri::parse)

    fun setCustomArtworkUri(gameId: String, value: Uri?) {
        prefs.edit().putString("$KEY_CUSTOM_ARTWORK_URI$gameId", value?.toString()).apply()
    }

    /**
     * Artwork alias resolved from the indexed Libretro catalog for a stable seed game ID.
     * This never replaces user-selected custom artwork; it only supplies the exact upstream
     * thumbnail repository/title pair used by libretro-thumbnails.
     */
    fun syncedArtworkTitle(gameId: String): String? =
        prefs.getString("$KEY_SYNCED_ARTWORK_TITLE$gameId", null)?.takeIf { it.isNotBlank() }

    fun syncedArtworkRepo(gameId: String): String? =
        prefs.getString("$KEY_SYNCED_ARTWORK_REPO$gameId", null)?.takeIf { it.isNotBlank() }

    /** Cached official artwork downloaded explicitly by the user for a collection. */
    fun syncedArtworkLocalUri(gameId: String): String? =
        prefs.getString("$KEY_SYNCED_ARTWORK_LOCAL_URI$gameId", null)?.takeIf { it.isNotBlank() }

    /** Cached square navigation icon downloaded explicitly from SteamGridDB. */
    fun syncedGameIconLocalUri(gameId: String): String? =
        prefs.getString("$KEY_SYNCED_GAME_ICON_LOCAL_URI$gameId", null)?.takeIf { it.isNotBlank() }

    fun setSyncedGameIconLocalUri(gameId: String, value: String?) {
        val clean = value?.trim()?.takeIf { it.isNotBlank() }
        val edit = prefs.edit()
        if (clean == null) edit.remove("$KEY_SYNCED_GAME_ICON_LOCAL_URI$gameId")
        else edit.putString("$KEY_SYNCED_GAME_ICON_LOCAL_URI$gameId", clean)
        edit.apply()
    }

    fun steamGridDbGameId(gameId: String): Int? =
        prefs.getInt("$KEY_STEAMGRIDDB_GAME_ID$gameId", -1).takeIf { it > 0 }

    fun setSteamGridDbGameId(gameId: String, value: Int?) {
        val edit = prefs.edit()
        if (value == null || value <= 0) edit.remove("$KEY_STEAMGRIDDB_GAME_ID$gameId")
        else edit.putInt("$KEY_STEAMGRIDDB_GAME_ID$gameId", value)
        edit.apply()
    }

    fun setSyncedArtworkLocalUri(gameId: String, value: String?) {
        val clean = value?.trim()?.takeIf { it.isNotBlank() }
        val edit = prefs.edit()
        if (clean == null) edit.remove("$KEY_SYNCED_ARTWORK_LOCAL_URI$gameId")
        else edit.putString("$KEY_SYNCED_ARTWORK_LOCAL_URI$gameId", clean)
        edit.apply()
    }

    fun setSyncedArtworkAlias(gameId: String, title: String?, repository: String?) {
        val edit = prefs.edit()
        val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }
        val cleanRepo = repository?.trim()?.takeIf { it.isNotBlank() }
        if (cleanTitle == null || cleanRepo == null) {
            edit.remove("$KEY_SYNCED_ARTWORK_TITLE$gameId")
            edit.remove("$KEY_SYNCED_ARTWORK_REPO$gameId")
        } else {
            edit.putString("$KEY_SYNCED_ARTWORK_TITLE$gameId", cleanTitle)
            edit.putString("$KEY_SYNCED_ARTWORK_REPO$gameId", cleanRepo)
        }
        edit.apply()
    }

    data class CollectionArtworkSyncSnapshot(
        val resolved: Int,
        val total: Int,
        val syncedAt: Long,
    )

    fun collectionArtworkSyncSnapshot(collectionId: String): CollectionArtworkSyncSnapshot? {
        val syncedAt = prefs.getLong("$KEY_COLLECTION_ARTWORK_SYNC_AT$collectionId", 0L)
        if (syncedAt <= 0L) return null
        return CollectionArtworkSyncSnapshot(
            resolved = prefs.getInt("$KEY_COLLECTION_ARTWORK_SYNC_RESOLVED$collectionId", 0),
            total = prefs.getInt("$KEY_COLLECTION_ARTWORK_SYNC_TOTAL$collectionId", 0),
            syncedAt = syncedAt,
        )
    }

    fun setCollectionArtworkSyncSnapshot(collectionId: String, resolved: Int, total: Int, syncedAt: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putInt("$KEY_COLLECTION_ARTWORK_SYNC_RESOLVED$collectionId", resolved.coerceAtLeast(0))
            .putInt("$KEY_COLLECTION_ARTWORK_SYNC_TOTAL$collectionId", total.coerceAtLeast(0))
            .putLong("$KEY_COLLECTION_ARTWORK_SYNC_AT$collectionId", syncedAt)
            .apply()
    }

    fun authorizedSource(gameId: String): AuthorizedSource? {
        val value = prefs.getString("$KEY_SOURCE$gameId", null)?.takeIf { it.isNotBlank() } ?: return null
        return AuthorizedSource(value, prefs.getString("$KEY_SOURCE_FILENAME$gameId", null)?.takeIf { it.isNotBlank() })
    }

    fun setAuthorizedSource(gameId: String, source: AuthorizedSource?) {
        val edit = prefs.edit()
        if (source == null) {
            edit.remove("$KEY_SOURCE$gameId")
            edit.remove("$KEY_SOURCE_FILENAME$gameId")
        } else {
            edit.putString("$KEY_SOURCE$gameId", source.value.trim())
            edit.putString("$KEY_SOURCE_FILENAME$gameId", source.expectedFileName?.trim()?.takeIf { it.isNotBlank() })
        }
        edit.apply()
    }


    var romgiAutoEnabled: Boolean
        get() = prefs.getBoolean(KEY_ROMGI_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_ROMGI_AUTO, value).apply()

    var romgiWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_ROMGI_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_ROMGI_WIFI_ONLY, value).apply()

    var romgiTorrentsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ROMGI_TORRENTS, true)
        set(value) = prefs.edit().putBoolean(KEY_ROMGI_TORRENTS, value).apply()

    var preferredRegion: String
        get() = RomRegionPreference.fromStored(prefs.getString(KEY_PREFERRED_REGION, null)).key
        set(value) = prefs.edit().putString(KEY_PREFERRED_REGION, RomRegionPreference.fromStored(value).key).apply()

    var retroArchPackage: String
        get() = prefs.getString(KEY_RETROARCH_PACKAGE, "com.retroarch.aarch64") ?: "com.retroarch.aarch64"
        set(value) = prefs.edit().putString(KEY_RETROARCH_PACKAGE, value.trim().ifBlank { "com.retroarch.aarch64" }).apply()

    var retroArchCoreTreeUri: Uri?
        get() = prefs.getString(KEY_RETROARCH_CORE_TREE, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_RETROARCH_CORE_TREE, value?.toString()).apply()

    var retroArchSystemTreeUri: Uri?
        get() = prefs.getString(KEY_RETROARCH_SYSTEM_TREE, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_RETROARCH_SYSTEM_TREE, value?.toString()).apply()

    var retroArchOverlayTreeUri: Uri?
        get() = prefs.getString(KEY_RETROARCH_OVERLAY_TREE, null)?.let(Uri::parse)
        set(value) = prefs.edit().putString(KEY_RETROARCH_OVERLAY_TREE, value?.toString()).apply()

    var retroArchAutoInstallCores: Boolean
        get() = prefs.getBoolean(KEY_RETROARCH_AUTO_INSTALL_CORES, true)
        set(value) = prefs.edit().putBoolean(KEY_RETROARCH_AUTO_INSTALL_CORES, value).apply()

    /** Automatically open RetroArch right after a ROM finishes downloading (preserved unless Download-only is used). */
    var autoLaunchAfterDownload: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LAUNCH_AFTER_DOWNLOAD, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LAUNCH_AFTER_DOWNLOAD, value).apply()

    var ftpTargetsJson: String
        get() = prefs.getString(KEY_FTP_TARGETS_JSON, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_FTP_TARGETS_JSON, value).apply()

    var pcHubUrl: String
        get() = prefs.getString(KEY_PC_HUB_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PC_HUB_URL, value.trim()).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var geminiModel: String
        get() = prefs.getString(KEY_GEMINI_MODEL, "gemini-2.5-flash").orEmpty().ifBlank { "gemini-2.5-flash" }
        set(value) = prefs.edit().putString(KEY_GEMINI_MODEL, value.trim().ifBlank { "gemini-2.5-flash" }).apply()

    fun cachedDescription(gameId: String): String? =
        prefs.getString("$KEY_GAME_DESCRIPTION$gameId", null)?.takeIf { it.isNotBlank() }

    fun setCachedDescription(gameId: String, description: String) {
        prefs.edit().putString("$KEY_GAME_DESCRIPTION$gameId", description.trim()).apply()
    }

    data class PatchedRomMeta(
        val path: String,
        val label: String,
        val format: String,
    )

    fun patchedRom(gameId: String): PatchedRomMeta? {
        val path = prefs.getString("$KEY_PATCHED_ROM_PATH$gameId", null)?.takeIf { it.isNotBlank() } ?: return null
        val file = java.io.File(path)
        if (!file.isFile || !file.canRead()) return null
        return PatchedRomMeta(
            path = path,
            label = prefs.getString("$KEY_PATCHED_ROM_LABEL$gameId", "Traducción [ES]").orEmpty(),
            format = prefs.getString("$KEY_PATCHED_ROM_FORMAT$gameId", "IPS").orEmpty(),
        )
    }

    fun setPatchedRom(gameId: String, path: String, label: String, format: String) {
        prefs.edit()
            .putString("$KEY_PATCHED_ROM_PATH$gameId", path)
            .putString("$KEY_PATCHED_ROM_LABEL$gameId", label)
            .putString("$KEY_PATCHED_ROM_FORMAT$gameId", format)
            .apply()
    }

    fun clearPatchedRom(gameId: String) {
        prefs.edit()
            .remove("$KEY_PATCHED_ROM_PATH$gameId")
            .remove("$KEY_PATCHED_ROM_LABEL$gameId")
            .remove("$KEY_PATCHED_ROM_FORMAT$gameId")
            .apply()
    }

    fun isRetroArchCoreConfirmed(packageName: String, coreName: String): Boolean =
        prefs.getStringSet(KEY_RETROARCH_CONFIRMED_CORES, emptySet())
            .orEmpty()
            .contains("${packageName.trim()}|${coreName.trim().lowercase()}")

    fun setRetroArchCoreConfirmed(packageName: String, coreName: String, confirmed: Boolean) {
        val key = "${packageName.trim()}|${coreName.trim().lowercase()}"
        val values = prefs.getStringSet(KEY_RETROARCH_CONFIRMED_CORES, emptySet()).orEmpty().toMutableSet()
        if (confirmed) values += key else values -= key
        prefs.edit().putStringSet(KEY_RETROARCH_CONFIRMED_CORES, values).apply()
    }

    fun engineMode(systemId: String): EngineMode =
        EngineMode.fromKey(prefs.getString("$KEY_ENGINE_MODE$systemId", EngineMode.AUTO.key))

    fun setEngineMode(systemId: String, mode: EngineMode) {
        prefs.edit().putString("$KEY_ENGINE_MODE$systemId", mode.key).apply()
    }

    fun externalEmulatorPackage(systemId: String): String? =
        prefs.getString("$KEY_EXTERNAL_PACKAGE$systemId", null)?.takeIf(String::isNotBlank)

    fun setExternalEmulatorPackage(systemId: String, packageName: String?) {
        val editor = prefs.edit()
        val key = "$KEY_EXTERNAL_PACKAGE$systemId"
        if (packageName.isNullOrBlank()) editor.remove(key) else editor.putString(key, packageName.trim())
        editor.apply()
    }

    fun restoreAutomaticEngine(systemId: String) {
        prefs.edit()
            .remove("$KEY_ENGINE_MODE$systemId")
            .remove("$KEY_EXTERNAL_PACKAGE$systemId")
            .apply()
    }

    fun restoreAllAutomaticEngines(systemIds: Collection<String>) {
        val editor = prefs.edit()
        systemIds.forEach { systemId ->
            editor.remove("$KEY_ENGINE_MODE$systemId")
            editor.remove("$KEY_EXTERNAL_PACKAGE$systemId")
        }
        editor.apply()
    }

    var ps2EmulatorPackage: String
        get() = prefs.getString(KEY_PS2_PACKAGE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PS2_PACKAGE, value.trim()).apply()

    var threeDsEmulatorPackage: String
        get() = prefs.getString(KEY_3DS_PACKAGE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_3DS_PACKAGE, value.trim()).apply()

    var deleteAfterPlay: Boolean
        get() = prefs.getBoolean(KEY_DELETE_AFTER_PLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_DELETE_AFTER_PLAY, value).apply()

    var skipIndexedSystems: Boolean
        get() = prefs.getBoolean(KEY_SKIP_INDEXED, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_INDEXED, value).apply()

    var catalogPerformanceMode: CatalogPerformanceMode
        get() = CatalogPerformanceMode.fromKey(prefs.getString(KEY_CATALOG_PERFORMANCE, CatalogPerformanceMode.FLUID.key))
        set(value) = prefs.edit().putString(KEY_CATALOG_PERFORMANCE, value.key).apply()

    private fun migrateLegacyPreferences(context: Context) {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return
        val legacy = context.getSharedPreferences("gakuto_kizuna", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        legacy.getString("library_tree", null)?.let {
            if (!prefs.contains(KEY_GLOBAL_VIRTUAL_TREE)) edit.putString(KEY_GLOBAL_VIRTUAL_TREE, it)
        }
        legacy.getString("iisu_assets_tree", null)?.let {
            if (!prefs.contains(KEY_IISU_ASSETS_TREE)) edit.putString(KEY_IISU_ASSETS_TREE, it)
        }
        legacy.getString("retroarch_package", null)?.let {
            if (!prefs.contains(KEY_RETROARCH_PACKAGE)) edit.putString(KEY_RETROARCH_PACKAGE, it)
        }
        legacy.getString("ps2_package", null)?.let {
            if (!prefs.contains(KEY_PS2_PACKAGE)) edit.putString(KEY_PS2_PACKAGE, it)
        }
        legacy.getString("n3ds_package", null)?.let {
            if (!prefs.contains(KEY_3DS_PACKAGE)) edit.putString(KEY_3DS_PACKAGE, it)
        }
        if (legacy.contains("delete_after_play") && !prefs.contains(KEY_DELETE_AFTER_PLAY)) {
            edit.putBoolean(KEY_DELETE_AFTER_PLAY, legacy.getBoolean("delete_after_play", true))
        }
        legacy.all.forEach { (key, value) ->
            when {
                key.startsWith("artwork_") && value is String -> {
                    val gameId = key.removePrefix("artwork_")
                    if (!prefs.contains("$KEY_CUSTOM_ARTWORK_URL$gameId")) {
                        edit.putString("$KEY_CUSTOM_ARTWORK_URL$gameId", value)
                        edit.putString("$KEY_GAME_ARTWORK_MODE$gameId", ArtworkMode.CUSTOM.key)
                    }
                }
                key.startsWith("source_") && value is String -> {
                    val gameId = key.removePrefix("source_")
                    if (!prefs.contains("$KEY_SOURCE$gameId")) edit.putString("$KEY_SOURCE$gameId", value)
                }
            }
        }
        edit.putBoolean(KEY_LEGACY_MIGRATED, true).apply()
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

        private const val KEY_GLOBAL_VIRTUAL_TREE = "global_virtual_tree"
        private const val KEY_SYSTEM_VIRTUAL_TREE = "system_virtual_tree_"
        private const val KEY_GLOBAL_ROM_TREE = "global_rom_tree"
        private const val KEY_SYSTEM_ROM_TREE = "system_rom_tree_"
        private const val KEY_IISU_ASSETS_TREE = "iisu_assets_tree"
        private const val KEY_IISU_INTEGRATION_ENABLED = "iisu_integration_enabled"
        private const val KEY_IISU_LAST_SYNC_AT = "iisu_last_sync_at"
        private const val KEY_IISU_LAST_SYNC_DESCRIPTORS = "iisu_last_sync_descriptors"
        private const val KEY_IISU_LAST_SYNC_SYSTEMS = "iisu_last_sync_systems"
        private const val KEY_LOCAL_ARTWORK_TREE = "local_artwork_tree"
        private const val KEY_FBNEO_BIOS_TREE = "fbneo_bios_tree"
        private const val KEY_ARTWORK_MODE = "artwork_mode"
        private const val KEY_GAME_TILE_STYLE = "game_tile_style"
        private const val KEY_APP_THEME_MODE = "app_theme_mode"
        private const val KEY_APP_ORIENTATION_MODE = "app_orientation_mode"
        private const val KEY_GAME_GRID_DENSITY = "game_grid_density"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_WALLPAPER_KIND = "wallpaper_kind"
        private const val KEY_NAV_SOUNDTRACK_URI = "navigation_soundtrack_uri"
        private const val KEY_NAV_SOUNDTRACK_ENABLED = "navigation_soundtrack_enabled"
        private const val KEY_HERO_MEDIA_URI = "hero_media_uri_"
        private const val KEY_HERO_MEDIA_KIND = "hero_media_kind_"
        private const val KEY_HERO_MEDIA_AUDIO = "hero_media_audio_"
        private const val KEY_SURFACE_PLACEMENT = "surface_placement_"
        private const val KEY_STEAMGRIDDB_API_KEY = "steamgriddb_api_key"
        private const val KEY_GAME_ARTWORK_MODE = "game_artwork_mode_"
        private const val KEY_CUSTOM_ARTWORK_URL = "custom_artwork_url_"
        private const val KEY_CUSTOM_ARTWORK_URI = "custom_artwork_uri_"
        private const val KEY_SYNCED_ARTWORK_TITLE = "synced_artwork_title_"
        private const val KEY_SYNCED_ARTWORK_REPO = "synced_artwork_repo_"
        private const val KEY_SYNCED_ARTWORK_LOCAL_URI = "synced_artwork_local_uri_"
        private const val KEY_SYNCED_GAME_ICON_LOCAL_URI = "synced_game_icon_local_uri_"
        private const val KEY_STEAMGRIDDB_GAME_ID = "steamgriddb_game_id_"
        private const val KEY_COLLECTION_ARTWORK_SYNC_RESOLVED = "collection_artwork_sync_resolved_"
        private const val KEY_COLLECTION_ARTWORK_SYNC_TOTAL = "collection_artwork_sync_total_"
        private const val KEY_COLLECTION_ARTWORK_SYNC_AT = "collection_artwork_sync_at_"
        private const val KEY_SOURCE = "authorized_source_"
        private const val KEY_SOURCE_FILENAME = "authorized_source_filename_"
        private const val KEY_ROMGI_AUTO = "romgi_auto_enabled"
        private const val KEY_ROMGI_WIFI_ONLY = "romgi_wifi_only"
        private const val KEY_ROMGI_TORRENTS = "romgi_torrents_enabled"
        private const val KEY_PREFERRED_REGION = "preferred_region"
        private const val KEY_RETROARCH_PACKAGE = "retroarch_package"
        private const val KEY_RETROARCH_CORE_TREE = "retroarch_core_tree"
        private const val KEY_RETROARCH_SYSTEM_TREE = "retroarch_system_tree"
        private const val KEY_RETROARCH_OVERLAY_TREE = "retroarch_overlay_tree"
        private const val KEY_RETROARCH_AUTO_INSTALL_CORES = "retroarch_auto_install_cores"
        private const val KEY_AUTO_LAUNCH_AFTER_DOWNLOAD = "auto_launch_after_download"
        private const val KEY_FTP_TARGETS_JSON = "ftp_targets_json"
        private const val KEY_PC_HUB_URL = "pc_hub_url"
        private const val KEY_GAME_DESCRIPTION = "game_description_"
        private const val KEY_PATCHED_ROM_PATH = "patched_rom_path_"
        private const val KEY_PATCHED_ROM_LABEL = "patched_rom_label_"
        private const val KEY_PATCHED_ROM_FORMAT = "patched_rom_format_"
        private const val KEY_RETROARCH_CONFIRMED_CORES = "retroarch_confirmed_cores"
        private const val KEY_ENGINE_MODE = "engine_mode_"
        private const val KEY_EXTERNAL_PACKAGE = "external_emulator_package_"
        private const val KEY_PS2_PACKAGE = "ps2_package"
        private const val KEY_3DS_PACKAGE = "n3ds_package"
        private const val KEY_DELETE_AFTER_PLAY = "delete_after_play"
        private const val KEY_SKIP_INDEXED = "skip_indexed"
        private const val KEY_CATALOG_PERFORMANCE = "catalog_performance_mode"
        private const val KEY_LEGACY_MIGRATED = "legacy_preferences_migrated"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
    }
}

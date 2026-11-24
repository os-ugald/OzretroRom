package cl.gakuto.kizuna.catalog.sourceindex

import android.content.Context
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameCatalogOrigin
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.launch.DownloadSourcePlan
import cl.gakuto.kizuna.launch.LogicalDownloadIdentity
import cl.gakuto.kizuna.launch.SourceIndexProvenance
import cl.gakuto.kizuna.util.TextNormalizer
import cl.gakuto.kizuna.util.CatalogFilterState
import cl.gakuto.kizuna.util.CatalogSort
import cl.gakuto.kizuna.util.RegionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SourceIndexSourceSelection(
    val game: SourceIndexGame,
    val release: SourceIndexRelease,
    val candidate: SourceIndexCandidate,
    val sourcePlan: DownloadSourcePlan,
)

internal data class SourceIndexCatalogPage(
    val games: List<GameEntity>,
    val total: Int,
    val controlledTotal: Int,
    val nextOffset: Int,
    val exhausted: Boolean,
)

internal enum class UnresolvedGameSourceRoute { SOURCE_INDEX, ROMGI, SOURCE_REQUIRED }

internal data class SourceIndexCatalogConfig(
    val platformId: String,
    val platformName: String,
    val thumbnailRepo: String,
    val coreId: String,
    val virtualExtension: String,
    val launchValidated: Boolean,
    val shardAsset: String,
    val shardSha256: String,
    val projectionPrefix: String,
    val acquisitionEnabled: Boolean,
    val compatibilityProjectionId: String? = null,
    val compatibilityGameId: String? = null,
)

/**
 * Process-scoped read-only cartridge-system discovery catalog.
 *
 * Source Index records remain outside Room. A Room row is created only when the user opens a game,
 * so favorites, collections, local ROM state and play history can use their existing durable owner.
 */
internal open class CartridgeSourceIndexCatalog(private val config: SourceIndexCatalogConfig) {
    @Volatile private var cachedReader: OzSourceIndexReader? = null

    suspend fun reader(context: Context): OzSourceIndexReader = cachedReader ?: withContext(Dispatchers.IO) {
        synchronized(this@CartridgeSourceIndexCatalog) {
            cachedReader ?: context.applicationContext.assets.open(config.shardAsset).use { input ->
                OzSourceIndexReader.openGzip(input, config.shardSha256).also { reader ->
                    require(reader.platform == config.platformId)
                    cachedReader = reader
                }
            }
        }
    }

    suspend fun page(
        context: Context,
        database: OzDatabase,
        normalizedQuery: String,
        regions: Set<RegionMode>,
        sort: CatalogSort,
        offset: Int,
        limit: Int,
    ): SourceIndexCatalogPage {
        val sourceReader = reader(context)
        val regionFiltered = sourceReader.searchGames(TextNormalizer.normalize(normalizedQuery)).filter { game ->
            matchesRegion(sourceReader.releasesForGame(game.gameId), regions)
        }
        val matches = when (sort) {
            CatalogSort.NAME_DESC -> regionFiltered.sortedByDescending(SourceIndexGame::canonicalTitleKey)
            else -> regionFiltered.sortedBy(SourceIndexGame::canonicalTitleKey)
        }
        val selected = matches.drop(offset).take(limit)
        val projections = selected.map { project(sourceReader, it) }
        val overlays = database.gameDao().byIds(projections.map(GameEntity::id)).associateBy(GameEntity::id)
        val merged = mergeOverlays(projections, overlays)
        val next = offset + merged.size
        return SourceIndexCatalogPage(merged, matches.size, sourceReader.gameCount, next, next >= matches.size)
    }

    /** Removes controls for metadata/state that a read-only Source Index shard cannot evaluate globally. */
    internal fun supportedFilters(state: CatalogFilterState): CatalogFilterState = state.copy(
        platformFamilies = emptySet(),
        systemIds = emptySet(),
        genres = emptySet(),
        status = emptySet(),
        sort = state.sort.takeIf { it == CatalogSort.NAME_ASC || it == CatalogSort.NAME_DESC }
            ?: CatalogSort.NAME_ASC,
    ).normalized()

    suspend fun materialize(context: Context, database: OzDatabase, projection: GameEntity): GameEntity {
        require(isProjection(projection)) { "El juego no pertenece al catálogo ${config.platformName} Source Index." }
        database.gameDao().byId(projection.id)?.let { return it }
        database.gameDao().insertAll(listOf(projection))
        return requireNotNull(database.gameDao().byId(projection.id))
    }

    suspend fun releases(context: Context, projectionId: String): List<SourceIndexRelease> {
        val sourceReader = reader(context)
        val gameId = sourceGameId(projectionId) ?: return emptyList()
        return sourceReader.releasesForGame(gameId)
    }

    suspend fun resolve(
        context: Context,
        selectedGame: GameEntity,
        preferredRegion: String,
        releaseId: String? = null,
        candidateId: String? = null,
    ): SourceIndexSourceSelection {
        return resolve(reader(context), selectedGame, preferredRegion, releaseId, candidateId)
    }

    internal fun resolve(
        sourceReader: OzSourceIndexReader,
        selectedGame: GameEntity,
        preferredRegion: String,
        releaseId: String? = null,
        candidateId: String? = null,
    ): SourceIndexSourceSelection {
        require(isProjection(selectedGame)) { "El juego seleccionado no pertenece al Source Index ${config.platformName}." }
        val gameId = requireNotNull(sourceGameId(selectedGame.id)) { "Identidad de proyección ${config.platformName} inválida." }
        val game = requireNotNull(sourceReader.game(gameId)) { "El juego ya no existe en el Source Index ${config.platformName}." }
        val releases = sourceReader.releasesForGame(gameId)
        val release = releaseId?.let { requested -> releases.singleOrNull { it.releaseId == requested } }
            ?: selectPreferredRelease(releases, preferredRegion)
        requireNotNull(release) { "El juego no tiene una versión ${config.platformName} descargable." }
        val candidates = sourceReader.candidatesForRelease(release.releaseId)
        val candidate = candidateId?.let { requested -> candidates.singleOrNull { it.candidateId == requested } }
            ?: candidates.firstOrNull()
        requireNotNull(candidate) { "La versión seleccionada no tiene una fuente descargable." }
        val downloadCandidate = candidate.toDownloadCandidate()
        return SourceIndexSourceSelection(
            game = game,
            release = release,
            candidate = candidate,
            sourcePlan = DownloadSourcePlan(
                logicalIdentity = LogicalDownloadIdentity.from("oz-source-index-release-v1", release.releaseId),
                candidates = listOf(downloadCandidate),
                selectedCandidateIndex = 0,
                sourceIndexProvenance = SourceIndexProvenance(
                    gameId = game.gameId,
                    releaseId = release.releaseId,
                    candidateId = candidate.candidateId,
                    provider = candidate.provider,
                    infoHash = candidate.infoHash,
                    fileIndex = candidate.fileIndex,
                    exactFullPath = candidate.exactFullPath,
                    expectedSize = candidate.artifactSize,
                ),
            ),
        )
    }

    fun isProjection(game: GameEntity): Boolean =
        game.systemId == config.platformId && game.catalogOrigin == GameCatalogOrigin.SOURCE_INDEX

    fun acquisitionEnabled(game: GameEntity): Boolean = isProjection(game) && config.acquisitionEnabled

    internal fun unresolvedSourceRoute(game: GameEntity, romgiEnabled: Boolean): UnresolvedGameSourceRoute = when {
        isProjection(game) -> UnresolvedGameSourceRoute.SOURCE_INDEX
        romgiEnabled -> UnresolvedGameSourceRoute.ROMGI
        else -> UnresolvedGameSourceRoute.SOURCE_REQUIRED
    }

    fun projectionId(gameId: String): String = if (gameId == config.compatibilityGameId) {
        requireNotNull(config.compatibilityProjectionId)
    } else {
        "${config.projectionPrefix}$gameId"
    }

    fun sourceGameId(projectionId: String): String? = when {
        projectionId == config.compatibilityProjectionId -> config.compatibilityGameId
        projectionId.startsWith(config.projectionPrefix) -> projectionId.removePrefix(config.projectionPrefix).takeIf(String::isNotBlank)
        else -> null
    }

    fun releaseLabel(release: SourceIndexRelease): String = listOfNotNull(
        release.region,
        release.revision,
        release.facets.classificationEvidence.firstNotNullOfOrNull { evidence ->
            Regex("\\((NTSC|PAL)\\)", RegexOption.IGNORE_CASE).find(evidence.value)?.groupValues?.get(1)?.uppercase()
        },
        release.facets.publicationClass.lowercase().replaceFirstChar(Char::uppercase),
        release.facets.modificationClass.takeUnless { it == "ORIGINAL" || it == "UNKNOWN" }
            ?.lowercase()?.replaceFirstChar(Char::uppercase),
    ).distinct().joinToString(" · ")

    internal fun project(reader: OzSourceIndexReader, game: SourceIndexGame): GameEntity {
        val releases = reader.releasesForGame(game.gameId)
        val regions = releases.flatMap { it.facets.regions.ifEmpty { listOf(it.region) } }.distinct()
        return GameEntity(
            id = projectionId(game.gameId),
            title = game.canonicalTitle,
            normalizedTitle = game.canonicalTitleKey,
            platform = config.platformName,
            systemId = config.platformId,
            year = null,
            region = regions.singleOrNull() ?: if (regions.isEmpty()) "Unknown" else "Múltiples regiones",
            thumbnailRepo = config.thumbnailRepo,
            source = GameCatalogOrigin.SOURCE_INDEX.persistedValue,
            launchValidated = config.launchValidated,
            coreId = config.coreId,
            virtualExtension = config.virtualExtension,
            canonicalTitle = game.canonicalTitle,
            genre = UNKNOWN_GENRE,
        )
    }

    internal fun mergeOverlays(
        projections: List<GameEntity>,
        overlays: Map<String, GameEntity>,
    ): List<GameEntity> = projections.map { projection -> overlays[projection.id] ?: projection }

    private fun selectPreferredRelease(releases: List<SourceIndexRelease>, preferredRegion: String): SourceIndexRelease? {
        val preferred = preferredRegion.trim().lowercase()
        return releases.minWithOrNull(
            compareBy<SourceIndexRelease> { release ->
                val region = release.region.lowercase()
                when {
                    preferred.isNotBlank() && preferred in region -> 0
                    "world" in region || "international" in region -> 1
                    "usa" in region || "united states" in region -> 2
                    else -> 3
                }
            }.thenBy { it.region.lowercase() }
                .thenByDescending { it.revision.orEmpty() }
                .thenBy { it.releaseId },
        )
    }

    internal fun matchesRegion(releases: List<SourceIndexRelease>, modes: Set<RegionMode>): Boolean {
        if (modes.isEmpty() || RegionMode.PREFERRED in modes || RegionMode.ALL in modes) return true
        return releases.any { release ->
            val region = release.region.lowercase()
            modes.any { mode ->
                when (mode) {
                    RegionMode.WORLD -> "world" in region || "international" in region
                    RegionMode.USA -> "usa" in region || "united states" in region
                    RegionMode.EUROPE -> "europe" in region || "pal" in region
                    RegionMode.JAPAN -> "japan" in region
                    RegionMode.ASIA -> listOf("asia", "korea", "taiwan", "hong kong", "china").any(region::contains)
                    RegionMode.OTHER -> listOf("world", "international", "usa", "united states", "europe", "pal", "japan", "asia", "korea", "taiwan", "hong kong", "china").none(region::contains)
                    RegionMode.PREFERRED, RegionMode.ALL -> true
                }
            }
        }
    }

    val SHARD_SHA256: String get() = config.shardSha256
    val SHARD_ASSET: String get() = config.shardAsset
    val FIRST_PLAY_GAME_ID: String get() = requireNotNull(config.compatibilityGameId)
    val FIRST_PLAY_RELEASE_ID = "fd32201cc2548a237c757121cf9d103dd4bc3d790b38beb8fb58a0aa037be15c"
    val FIRST_PLAY_CANDIDATE_ID = "be16f73ff801e399fc22521ea18f9dca59365febcec0e99e4f48812ce2cd0052"
    val LEGACY_FIRST_PLAY_PROJECTION_ID: String get() = requireNotNull(config.compatibilityProjectionId)
    val UNKNOWN_GENRE: String get() = Companion.UNKNOWN_GENRE

    companion object {
        internal const val UNKNOWN_GENRE = "Sin clasificar"
    }
}

internal val SnesSourceIndexCatalog = CartridgeSourceIndexCatalog(
    SourceIndexCatalogConfig(
        platformId = "snes", platformName = "Super Nintendo",
        thumbnailRepo = "Nintendo_-_Super_Nintendo_Entertainment_System",
        coreId = "snes9x_libretro_android.so", virtualExtension = "sfc", launchValidated = true,
        shardAsset = "source-index/snes.json.gz.bin",
        shardSha256 = "320e6f424f2ae5b5f01c58efc495d80f16a25d8106a74bbd3934cbc2935d897e",
        projectionPrefix = "oz-source-index-snes-game-",
        acquisitionEnabled = true,
        compatibilityProjectionId = "oz-source-index-snes-240p-test-suite",
        compatibilityGameId = "053ba9c1405a4e66c8fd5378a03b7db3661317ca3879d1803064ae7da1d7588e",
    ),
)

internal val NesSourceIndexCatalog = CartridgeSourceIndexCatalog(
    SourceIndexCatalogConfig(
        platformId = "nes", platformName = "Nintendo Entertainment System",
        thumbnailRepo = "Nintendo_-_Nintendo_Entertainment_System",
        coreId = "mesen_libretro_android.so", virtualExtension = "nes", launchValidated = true,
        shardAsset = "source-index/nes.json.gz.bin",
        shardSha256 = "db19ada0838f5a30f4ce941d24d6b497528263e78881ad6a14a037a54f9957a8",
        projectionPrefix = "oz-source-index-nes-game-",
        acquisitionEnabled = true,
    ),
)

internal val GenesisSourceIndexCatalog = CartridgeSourceIndexCatalog(
    SourceIndexCatalogConfig(
        platformId = "genesis", platformName = "Mega Drive / Genesis",
        thumbnailRepo = "Sega_-_Mega_Drive_-_Genesis",
        coreId = "genesis_plus_gx_libretro_android.so", virtualExtension = "md",
        // Physical first-play and cross-game identity revalidation evidence are frozen in project truth.
        launchValidated = true,
        shardAsset = "source-index/genesis.json.gz.bin",
        shardSha256 = "df42eb52fc05d17c85c88f9f6794cb4f622b8f3293a9bb73ae8dd6db34c1c72f",
        projectionPrefix = "oz-source-index-genesis-game-",
        acquisitionEnabled = true,
    ),
)

internal val GbaSourceIndexCatalog = CartridgeSourceIndexCatalog(
    SourceIndexCatalogConfig(
        platformId = "gba", platformName = "Game Boy Advance",
        thumbnailRepo = "Nintendo_-_Game_Boy_Advance",
        coreId = "mgba_libretro_android.so", virtualExtension = "gba", launchValidated = false,
        shardAsset = "source-index/gba.json.gz.bin",
        shardSha256 = "6ff70a3763fbbd6871c7e9a29135161c84deec4144d470ff5bb993d519d2af50",
        projectionPrefix = "oz-source-index-gba-game-",
        acquisitionEnabled = false,
    ),
)

internal object SourceIndexCatalogs {
    fun forSystem(systemId: String?): CartridgeSourceIndexCatalog? = when (systemId) {
        "snes" -> SnesSourceIndexCatalog
        "nes" -> NesSourceIndexCatalog
        "genesis" -> GenesisSourceIndexCatalog
        "gba" -> GbaSourceIndexCatalog
        else -> null
    }

    fun forProjection(game: GameEntity): CartridgeSourceIndexCatalog? = forSystem(game.systemId)
        ?.takeIf { it.isProjection(game) }

    fun acquisitionForProjection(game: GameEntity): CartridgeSourceIndexCatalog? = forProjection(game)
        ?.takeIf { it.acquisitionEnabled(game) }

    fun unresolvedSourceRoute(game: GameEntity, romgiEnabled: Boolean): UnresolvedGameSourceRoute = when {
        acquisitionForProjection(game) != null -> UnresolvedGameSourceRoute.SOURCE_INDEX
        forProjection(game) != null -> UnresolvedGameSourceRoute.SOURCE_REQUIRED
        romgiEnabled -> UnresolvedGameSourceRoute.ROMGI
        else -> UnresolvedGameSourceRoute.SOURCE_REQUIRED
    }
}

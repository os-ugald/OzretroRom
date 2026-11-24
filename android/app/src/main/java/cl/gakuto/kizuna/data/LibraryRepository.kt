package cl.gakuto.kizuna.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import cl.gakuto.kizuna.util.CatalogSort
import cl.gakuto.kizuna.util.OriginStatusFilter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class CollectionSummary(
    val collection: CollectionEntity,
    val gameCount: Int,
    val localCount: Int,
    val covers: List<GameEntity>,
)

class LibraryRepository(private val db: OzDatabase) {

    suspend fun page(spec: GameListSpec, limit: Int, offset: Int, includeCount: Boolean = false): GamePage {
        val dao = db.gameDao()
        val items = dao.page(CatalogPagingSql.page(spec, limit, offset))
        val total = if (includeCount) dao.countQuery(CatalogPagingSql.count(spec)) else -1
        return GamePage(
            items = items,
            totalMatches = total,
            nextOffset = offset + items.size,
            exhausted = items.size < limit,
        )
    }

    suspend fun count(spec: GameListSpec): Int = db.gameDao().countQuery(CatalogPagingSql.count(spec))
    fun pagedGames(spec: GameListSpec, pageSize: Int = 60): Flow<PagingData<GameEntity>> = Pager(
        PagingConfig(
            pageSize = pageSize,
            initialLoadSize = pageSize * 2,
            prefetchDistance = pageSize / 2,
            enablePlaceholders = false,
        ),
    ) { RepositoryPagingSource(this, spec) }.flow

    fun collections(): Flow<List<CollectionEntity>> = db.collectionDao().observeAll()
    fun localCountPulse(): Flow<Int> = db.gameDao().observeLocalCount()
    fun gameCountPulse(): Flow<Int> = db.gameDao().observeCount()
    fun lastPlayedPulse(): Flow<Long> = db.gameDao().observeLastPlayedPulse()
    fun membershipCountPulse(): Flow<Int> = db.collectionDao().observeMembershipCount()
    fun catalogRevisionPulse(): Flow<String> = db.catalogStateDao().observeAll()
        .map { states ->
            states.asSequence()
                .filter { it.status == "READY" }
                .joinToString("|") { state ->
                    "${state.systemId}:${state.gameCount}:${state.indexedAt ?: 0L}"
                }
        }
        .distinctUntilChanged()
    suspend fun collection(id: String): CollectionEntity? = db.collectionDao().byId(id)

    fun games(collectionId: String, query: String): Flow<List<GameEntity>> = when {
        collectionId == "all-games" -> db.gameDao().searchAll(query)
        collectionId == CatalogPagingSql.DOWNLOADED_COLLECTION_ID -> db.gameDao().downloaded(query)
        collectionId == CatalogPagingSql.RECENT_COLLECTION_ID -> db.gameDao().recent(query)
        CollectionVisibilityPolicy.isSystemCollection(collectionId) -> {
            val systemId = CollectionVisibilityPolicy.systemIdFromCollectionId(collectionId)
            db.gameDao().searchSystem(systemId, query)
        }
        else -> db.gameDao().inCollection(collectionId, query)
    }

    fun searchAll(query: String): Flow<List<GameEntity>> = db.gameDao().searchAll(query)
    fun searchSystem(systemId: String, query: String): Flow<List<GameEntity>> = db.gameDao().searchSystem(systemId, query)
    suspend fun systemGamesOnce(systemId: String): List<GameEntity> = db.gameDao().systemOnce(systemId)
    suspend fun allGamesOnce(): List<GameEntity> = db.gameDao().allOnce()
    suspend fun sagaCandidatesOnce(): List<GameEntity> {
        val keywords = listOf(
            "sonic", "mario", "zelda", "mega man", "megaman", "rockman",
            "castlevania", "dracula", "resident evil", "biohazard", "final fantasy",
            "metroid", "donkey kong", "street fighter", "mortal kombat", "metal slug",
            "kirby", "pokemon", "streets of rage", "bare knuckle", "phantasy star",
            "chrono", "silent hill", "metal gear", "007", "goldeneye", "contra",
            "shinobi", "golden axe", "double dragon", "breath of fire", "dragon quest",
            "ninja gaiden", "king of fighters", "dragon ball", "shining", "turtles",
            "tmnt", "batman", "spider", "x men", "star wars", "illusion", "aladdin",
            "lion king", "fatal fury", "garou", "art of fighting", "thunder force",
            "wonder boy", "monster world", "ecco", "earthworm jim", "strike",
            "outrun", "after burner", "space harrier", "hang on", "road rash",
            "bomberman", "mana", "seiken densetsu", "lufia",
        )
        val clauses = buildString {
            append("SELECT * FROM games WHERE COALESCE(contentUri,'')!='' OR lastPlayedAt IS NOT NULL OR source = 'seed'")
            repeat(keywords.size) { append(" OR normalizedTitle LIKE ?") }
            append(" LIMIT 4000")
        }
        val args = keywords.map { "%$it%" }.toTypedArray<Any>()
        return db.gameDao().page(androidx.sqlite.db.SimpleSQLiteQuery(clauses, args))
    }
    suspend fun moodCandidatesOnce(): List<GameEntity> = db.gameDao().moodCandidatesOnce()

    suspend fun catalogSampleForSmartLists(limit: Int = 420): List<GameEntity> {
        val sql = "SELECT * FROM games " +
            "WHERE COALESCE(contentUri,'')!='' OR source IN ('seed','curated') " +
            "OR (COALESCE(thumbnailRepo,'')!='' AND region IN ('USA','World','Japan, USA')) " +
            "ORDER BY CASE WHEN COALESCE(contentUri,'')!='' THEN 0 WHEN source IN ('seed','curated') THEN 1 ELSE 2 END, " +
            "COALESCE(lastPlayedAt,0) DESC, title LIMIT ?"
        return db.gameDao().page(androidx.sqlite.db.SimpleSQLiteQuery(sql, arrayOf(limit)))
    }

    suspend fun ensureGameForCatalogLaunch(candidate: GameEntity): GameEntity {
        db.gameDao().byId(candidate.id)?.let { return it }
        val byNorm = db.gameDao().page(
            androidx.sqlite.db.SimpleSQLiteQuery(
                "SELECT * FROM games WHERE systemId = ? AND normalizedTitle = ? ORDER BY COALESCE(contentUri,'') DESC LIMIT 1",
                arrayOf(candidate.systemId, candidate.normalizedTitle),
            ),
        ).firstOrNull()
        if (byNorm != null) return byNorm
        db.gameDao().insertAll(listOf(candidate))
        return db.gameDao().byId(candidate.id) ?: candidate
    }

    suspend fun collectionGamesOnce(collectionId: String): List<GameEntity> = when {
        collectionId == "all-games" -> allGamesOnce()
        collectionId == CatalogPagingSql.DOWNLOADED_COLLECTION_ID -> db.gameDao().downloadedOnce()
        collectionId == CatalogPagingSql.RECENT_COLLECTION_ID -> db.gameDao().recentOnce()
        CollectionVisibilityPolicy.isSystemCollection(collectionId) -> {
            val systemId = CollectionVisibilityPolicy.systemIdFromCollectionId(collectionId)
            db.gameDao().systemOnce(systemId)
        }
        else -> db.gameDao().inCollection(collectionId, "").first()
    }


    suspend fun collectionSummaries(): List<CollectionSummary> {
        val persisted = db.collectionDao().all()
            .filter { CollectionVisibilityPolicy.isVisibleInCollections(it.id) }

        val systems = db.catalogStateDao().all()
            .filter { it.gameCount > 0 && it.status == "READY" }
            .map { state ->
                CollectionEntity(
                    id = CollectionVisibilityPolicy.SYSTEM_COLLECTION_PREFIX + state.systemId,
                    name = state.systemId.uppercase(),
                    description = "Colección automática por sistema",
                    icon = "🎮",
                    accent = "#00E5FF",
                    isSystem = true,
                    sortOrder = 500,
                    kind = CollectionKind.SYSTEM.name
                )
            }

        return (persisted + systems)
            .sortedWith(compareBy<CollectionEntity>({ kindRank(it.collectionKind) }, { it.sortOrder }, { it.name.lowercase() }))
            .map { collection ->
                val defaultSort = if (collection.id == CatalogPagingSql.RECENT_COLLECTION_ID) CatalogSort.LAST_PLAYED else CatalogSort.NAME_ASC
                val spec = GameListSpec(collectionId = collection.id, sort = defaultSort)
                val count = count(spec)
                val local = count(spec.copy(statusFilters = setOf(OriginStatusFilter.LOCAL_LIBRARY)))
                val covers = page(spec, limit = 4, offset = 0).items
                    .map { game -> artworkVariantFor(game) }

                val displayName = if (CollectionVisibilityPolicy.isSystemCollection(collection.id) && covers.isNotEmpty()) {
                    covers.first().platform
                } else {
                    collection.name
                }

                CollectionSummary(collection.copy(name = displayName), count, local, covers)
            }
    }

    /**
     * Curated collection memberships keep their stable seed IDs, while the Libretro index may
     * later provide the exact DAT title used by libretro-thumbnails.  For collection artwork
     * only, prefer that indexed twin so cover URLs are built from the canonical upstream name.
     * The collection membership and launch identity are never replaced.
     */
    suspend fun artworkVariantFor(game: GameEntity): GameEntity {
        if (game.canonicalTitle.isBlank()) return game
        val variant = db.gameDao().bestArtworkVariant(game.systemId, game.canonicalTitle) ?: return game
        // Keep the stable collection/favorite ID and launch/local-library metadata. Only borrow
        // the upstream thumbnail identity. This also lets a later synchronized artwork alias,
        // stored under the stable ID, take precedence in ArtworkResolver.
        return game.copy(title = variant.title, thumbnailRepo = variant.thumbnailRepo)
    }

    fun recentHome(limit: Int = 12): Flow<List<GameEntity>> = db.gameDao().recentLimited(limit)
    fun favoriteHome(limit: Int = 12): Flow<List<GameEntity>> = db.gameDao().favoriteLimited(limit)
        .map { games -> games.map { game -> artworkVariantFor(game) } }
    fun localHome(limit: Int = 12): Flow<List<GameEntity>> = db.gameDao().localLimited(limit)

    suspend fun game(id: String) = db.gameDao().byId(id)

    suspend fun markPlayed(id: String, playedAt: Long = System.currentTimeMillis()) = db.gameDao().markPlayed(id, playedAt)

    suspend fun markRomgiAvailability(id: String, available: Boolean) = db.gameDao().markRomgiAvailability(id, available)

    suspend fun updateContentUri(id: String, contentUri: String?) = db.gameDao().updateContentUri(id, contentUri)
    suspend fun updateLocalState(id: String, contentUri: String?, origin: LibraryOrigin) = db.gameDao().updateLocalState(id, contentUri, origin.name)

    suspend fun toggle(collectionId: String, gameId: String): Boolean {
        val dao = db.collectionDao()
        return if (dao.hasMembership(collectionId, gameId)) {
            dao.removeMembership(collectionId, gameId)
            false
        } else {
            dao.addMembership(CollectionMembershipEntity(collectionId, gameId))
            true
        }
    }

    suspend fun createCollection(name: String, description: String = "Colección personal") {
        val clean = name.trim().ifBlank { "Nueva colección" }
        val id = "personal-" + System.currentTimeMillis()
        db.collectionDao().insert(CollectionEntity(id, clean, description, "✦", "#00E5FF", false, 1000, CollectionKind.MANUAL.name))
    }
    private fun kindRank(kind: CollectionKind): Int = when (kind) {
        CollectionKind.AUTOMATIC -> 0
        CollectionKind.MANUAL -> 1
        CollectionKind.CURATED -> 2
        CollectionKind.SYSTEM -> 3
    }
}

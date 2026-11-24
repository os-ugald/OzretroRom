package cl.gakuto.kizuna.data

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): GameEntity?

    @Query("SELECT * FROM games WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<GameEntity>

    @RawQuery(observedEntities = [GameEntity::class, CollectionMembershipEntity::class])
    suspend fun page(query: SupportSQLiteQuery): List<GameEntity>

    @RawQuery(observedEntities = [GameEntity::class, CollectionMembershipEntity::class])
    suspend fun countQuery(query: SupportSQLiteQuery): Int

    @Query("SELECT * FROM games WHERE LOWER(romSet) = LOWER(:romSet) LIMIT 1")
    suspend fun byRomSet(romSet: String): GameEntity?

    @Query("SELECT * FROM games WHERE :legacySlug LIKE '%' || REPLACE(normalizedTitle, ' ', '-') || '%' ORDER BY LENGTH(normalizedTitle) DESC LIMIT 1")
    suspend fun bestLegacyReference(legacySlug: String): GameEntity?

    @Query("SELECT * FROM games WHERE normalizedTitle LIKE '%' || :query || '%' ORDER BY title")
    fun searchAll(query: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE systemId=:systemId AND normalizedTitle LIKE '%' || :query || '%' ORDER BY title")
    fun searchSystem(systemId: String, query: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE systemId=:systemId ORDER BY title")
    suspend fun systemOnce(systemId: String): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE systemId=:systemId
          AND canonicalTitle=:canonicalTitle
          AND source='libretro'
          AND COALESCE(thumbnailRepo,'')!=''
        ORDER BY
          CASE region
            WHEN 'USA' THEN 0
            WHEN 'Japan' THEN 1
            WHEN 'World' THEN 2
            WHEN 'Asia' THEN 3
            WHEN 'Europe' THEN 4
            ELSE 5
          END,
          LENGTH(title),
          title
        LIMIT 1
    """)
    suspend fun bestArtworkVariant(systemId: String, canonicalTitle: String): GameEntity?

    @Query("""
        SELECT * FROM games
        WHERE systemId=:systemId
          AND canonicalTitle=:canonicalTitle
          AND source='libretro'
          AND COALESCE(thumbnailRepo,'')!=''
        ORDER BY
          CASE region
            WHEN 'USA' THEN 0
            WHEN 'Japan' THEN 1
            WHEN 'World' THEN 2
            WHEN 'Asia' THEN 3
            WHEN 'Europe' THEN 4
            ELSE 5
          END,
          LENGTH(title),
          title
        LIMIT :limit
    """)
    suspend fun artworkVariants(systemId: String, canonicalTitle: String, limit: Int): List<GameEntity>

    @Query("SELECT * FROM games ORDER BY title")
    suspend fun allOnce(): List<GameEntity>

    @Query("SELECT * FROM games WHERE COALESCE(contentUri,'')!='' OR source='seed' ORDER BY COALESCE(lastPlayedAt,0) DESC, title LIMIT 250")
    suspend fun moodCandidatesOnce(): List<GameEntity>

    @Query("SELECT * FROM games WHERE COALESCE(contentUri,'')!='' AND normalizedTitle LIKE '%' || :query || '%' ORDER BY title")
    fun downloaded(query: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE COALESCE(contentUri,'')!='' ORDER BY title")
    suspend fun downloadedOnce(): List<GameEntity>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL AND normalizedTitle LIKE '%' || :query || '%' ORDER BY lastPlayedAt DESC, title")
    fun recent(query: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC, title")
    suspend fun recentOnce(): List<GameEntity>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun recentLimited(limit: Int): Flow<List<GameEntity>>

    @Query("SELECT g.* FROM games g INNER JOIN collection_memberships m ON m.gameId=g.id WHERE m.collectionId='favorites' ORDER BY COALESCE(g.lastPlayedAt,0) DESC, g.title LIMIT :limit")
    fun favoriteLimited(limit: Int): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE COALESCE(contentUri,'')!='' ORDER BY COALESCE(lastPlayedAt, addedAt) DESC LIMIT :limit")
    fun localLimited(limit: Int): Flow<List<GameEntity>>

    @Query("SELECT g.* FROM games g INNER JOIN collection_memberships m ON m.gameId=g.id WHERE m.collectionId=:collectionId AND g.normalizedTitle LIKE '%' || :query || '%' ORDER BY g.title")
    fun inCollection(collectionId: String, query: String): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<GameEntity>)

    @Query("SELECT COUNT(*) FROM games")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM games")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM games WHERE COALESCE(contentUri,'')!=''")
    fun observeLocalCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(lastPlayedAt),0) FROM games")
    fun observeLastPlayedPulse(): Flow<Long>

    @Query("SELECT COUNT(*) FROM games WHERE systemId=:systemId")
    fun observeSystemCount(systemId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM games WHERE systemId=:systemId")
    suspend fun countSystemAllSources(systemId: String): Int

    /** Clears the historical legacy GBA validation claim without replacing any row state. */
    @Query("""
        UPDATE games
        SET launchValidated = 0
        WHERE systemId = 'gba'
          AND source != 'oz-source-index'
          AND launchValidated = 1
    """)
    suspend fun clearLegacyGbaLaunchValidation(): Int

    @Query("SELECT COUNT(*) FROM games WHERE systemId=:systemId AND source='libretro'")
    suspend fun countSystem(systemId: String): Int

    @Query("SELECT COUNT(*) FROM games WHERE systemId='arcade-fbneo' AND source='libretro' AND romSet IS NOT NULL AND romSet != ''")
    suspend fun countArcadeWithRomSet(): Int

    @Query("SELECT DISTINCT genre FROM games WHERE systemId=:systemId AND genre != '' AND genre != 'Sin clasificar' ORDER BY genre")
    suspend fun genresForSystem(systemId: String): List<String>

    @Query("SELECT DISTINCT region FROM games WHERE systemId=:systemId AND region != '' ORDER BY region")
    suspend fun regionsForSystem(systemId: String): List<String>

    @Query("UPDATE games SET lastPlayedAt=:playedAt WHERE id=:id")
    suspend fun markPlayed(id: String, playedAt: Long)

    @Query("UPDATE games SET romgiAvailable=:available WHERE id=:id")
    suspend fun markRomgiAvailability(id: String, available: Boolean)

    @Query("UPDATE games SET contentUri=:contentUri WHERE id=:id")
    suspend fun updateContentUri(id: String, contentUri: String?)

    @Query("UPDATE games SET contentUri=:contentUri, libraryOrigin=:origin WHERE id=:id")
    suspend fun updateLocalState(id: String, contentUri: String?, origin: String)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder, name") fun observeAll(): Flow<List<CollectionEntity>>
    @Query("SELECT * FROM collections ORDER BY sortOrder, name") suspend fun all(): List<CollectionEntity>
    @Query("SELECT * FROM collections WHERE id=:id") suspend fun byId(id: String): CollectionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<CollectionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: CollectionEntity)
    @Delete suspend fun delete(item: CollectionEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMembership(item: CollectionMembershipEntity)
    @Query("DELETE FROM collection_memberships WHERE collectionId=:collectionId AND gameId=:gameId") suspend fun removeMembership(collectionId: String, gameId: String)
    @Query("SELECT EXISTS(SELECT 1 FROM collection_memberships WHERE collectionId=:collectionId AND gameId=:gameId)") suspend fun hasMembership(collectionId: String, gameId: String): Boolean
    @Query("SELECT * FROM collection_memberships") suspend fun memberships(): List<CollectionMembershipEntity>
    @Query("SELECT COUNT(*) FROM collection_memberships") fun observeMembershipCount(): Flow<Int>
    @Query("SELECT collectionId FROM collection_memberships WHERE gameId=:gameId") suspend fun collectionIdsForGame(gameId: String): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMemberships(items: List<CollectionMembershipEntity>)
}

@Dao
interface CatalogStateDao {
    @Query("SELECT * FROM catalog_system_state ORDER BY systemId") fun observeAll(): Flow<List<CatalogSystemStateEntity>>
    @Query("SELECT * FROM catalog_system_state ORDER BY systemId") suspend fun all(): List<CatalogSystemStateEntity>
    @Query("SELECT * FROM catalog_system_state WHERE systemId=:id") suspend fun byId(id: String): CatalogSystemStateEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(state: CatalogSystemStateEntity)

    @Query("UPDATE catalog_system_state SET status='STALE', indexedAt=NULL, error=NULL")
    suspend fun markAllStale(): Int
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM game_sync_metadata WHERE gameId = :gameId LIMIT 1")
    suspend fun byGameId(gameId: String): GameSyncMetadataEntity?

    @Query("SELECT * FROM game_sync_metadata WHERE hubEntryId = :entryId LIMIT 1")
    suspend fun byHubEntryId(entryId: String): GameSyncMetadataEntity?

    @Query("SELECT * FROM game_sync_metadata WHERE dirty = 1 ORDER BY localUpdatedAt ASC")
    suspend fun dirtyItems(): List<GameSyncMetadataEntity>

    @Query("SELECT * FROM game_sync_metadata")
    suspend fun all(): List<GameSyncMetadataEntity>

    @Query("SELECT COUNT(*) FROM game_sync_metadata")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: GameSyncMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<GameSyncMetadataEntity>)

    @Query("UPDATE game_sync_metadata SET dirty = 0, hubRevision = :revision WHERE gameId IN (:gameIds)")
    suspend fun markSynced(gameIds: List<String>, revision: Long)

    @Query("SELECT * FROM sync_state WHERE hubId = :hubId LIMIT 1")
    suspend fun getSyncState(hubId: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)
}

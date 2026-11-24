package cl.gakuto.kizuna.hub

import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.CollectionMembershipEntity
import cl.gakuto.kizuna.data.GameSyncMetadataEntity
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.data.SyncStateEntity
import org.json.JSONObject
import java.time.Instant

data class HubSyncSummary(
    val pushed: Int,
    val pulled: Int,
    val latestRevision: Long,
)

/**
 * Coordinates incremental delta synchronization between Room (game_sync_metadata / sync_state)
 * and the PC Hub (/api/v1/sync), while preserving backward compatibility with AppPreferences.
 */
class HubSyncRepository(
    private val db: OzDatabase,
    private val preferences: AppPreferences,
) {
    private val syncDao = db.syncMetadataDao()
    private val gameDao = db.gameDao()
    private val collectionDao = db.collectionDao()

    suspend fun importLegacyPreferencesIfNeeded() {
        val currentState = syncDao.getSyncState(DEFAULT_HUB_ID)
        val deviceId = currentState?.deviceId ?: "android-${android.os.Build.MODEL.orEmpty().ifBlank { "device" }}"
        if (currentState == null) {
            syncDao.upsertSyncState(
                SyncStateEntity(
                    hubId = DEFAULT_HUB_ID,
                    lastRevision = 0L,
                    lastSyncedAt = 0L,
                    deviceId = deviceId,
                )
            )
        }

        val favIds = runCatching {
            collectionDao.memberships()
                .filter { it.collectionId == "favorites" }
                .map { it.gameId }
                .toSet()
        }.getOrDefault(emptySet())

        val recentGames = runCatching { gameDao.recentOnce() }.getOrDefault(emptyList())
        val gameIdsToSeed = LinkedHashSet<String>()
        gameIdsToSeed.addAll(favIds)
        recentGames.forEach { gameIdsToSeed.add(it.id) }

        val existingMap = syncDao.all().associateBy { it.gameId }
        val toInsert = ArrayList<GameSyncMetadataEntity>()
        val now = System.currentTimeMillis()
        for (gameId in gameIdsToSeed) {
            if (existingMap.containsKey(gameId)) continue
            val recentMatch = recentGames.firstOrNull { it.id == gameId }
            val patched = preferences.patchedRom(gameId)
            toInsert += GameSyncMetadataEntity(
                gameId = gameId,
                hubEntryId = null,
                favorite = gameId in favIds,
                playCount = if (recentMatch?.lastPlayedAt != null) 1 else 0,
                lastPlayedAt = recentMatch?.lastPlayedAt,
                customArtworkUrl = preferences.customArtworkUrl(gameId),
                customArtworkUri = preferences.customArtworkUri(gameId)?.toString(),
                syncedArtworkLocalUri = preferences.syncedArtworkLocalUri(gameId),
                syncedArtworkTitle = preferences.syncedArtworkTitle(gameId),
                syncedArtworkRepo = preferences.syncedArtworkRepo(gameId),
                syncedGameIconLocalUri = preferences.syncedGameIconLocalUri(gameId),
                cachedDescription = preferences.cachedDescription(gameId),
                patchedRomPath = patched?.path,
                patchedRomLabel = patched?.label,
                patchedRomFormat = patched?.format,
                localUpdatedAt = recentMatch?.lastPlayedAt ?: now,
                dirty = false,
            )
        }
        if (toInsert.isNotEmpty()) {
            syncDao.upsertAll(toInsert)
        }
    }

    suspend fun recordLocalFavorite(gameId: String, favorite: Boolean) {
        val now = System.currentTimeMillis()
        val prev = syncDao.byGameId(gameId)
        syncDao.upsert(
            (prev ?: GameSyncMetadataEntity(gameId = gameId)).copy(
                favorite = favorite,
                localUpdatedAt = now,
                dirty = true,
            )
        )
    }

    suspend fun recordLocalPlayed(gameId: String, playedAt: Long = System.currentTimeMillis()) {
        val prev = syncDao.byGameId(gameId)
        syncDao.upsert(
            (prev ?: GameSyncMetadataEntity(gameId = gameId)).copy(
                playCount = (prev?.playCount ?: 0) + 1,
                lastPlayedAt = playedAt,
                localUpdatedAt = playedAt,
                dirty = true,
            )
        )
    }

    suspend fun recordQuickResume(gameId: String, slot: Int, statePath: String, coreId: String) {
        val now = System.currentTimeMillis()
        val json = JSONObject()
            .put("slot", slot)
            .put("statePath", statePath)
            .put("coreId", coreId)
            .put("updatedAt", Instant.ofEpochMilli(now).toString())
            .toString()
        val prev = syncDao.byGameId(gameId)
        syncDao.upsert(
            (prev ?: GameSyncMetadataEntity(gameId = gameId)).copy(
                quickResumeJson = json,
                localUpdatedAt = now,
                dirty = true,
            )
        )
    }

    suspend fun syncWithHub(baseUrl: String): HubSyncSummary {
        importLegacyPreferencesIfNeeded()
        val state = syncDao.getSyncState(DEFAULT_HUB_ID) ?: SyncStateEntity(hubId = DEFAULT_HUB_ID)

        val dirtyRows = syncDao.dirtyItems()
        var pushedCount = 0
        var latestRevision = state.lastRevision
        if (dirtyRows.isNotEmpty()) {
            val outbound = dirtyRows.map { row ->
                val cm = JSONObject().apply {
                    row.customArtworkUrl?.let { put("customArtworkUrl", it) }
                    row.cachedDescription?.let { put("cachedDescription", it) }
                    row.patchedRomLabel?.let { put("patchedRomLabel", it) }
                }
                HubSyncOutboundChange(
                    entityId = row.hubEntryId ?: row.gameId,
                    favorite = row.favorite,
                    playCount = row.playCount,
                    lastPlayedAt = row.lastPlayedAt?.let { Instant.ofEpochMilli(it).toString() },
                    quickResumeJson = row.quickResumeJson,
                    customMetadataJson = if (cm.length() > 0) cm.toString() else null,
                    updatedAt = Instant.ofEpochMilli(row.localUpdatedAt.coerceAtLeast(1L)).toString(),
                )
            }
            val pushRes = GameVaultHubClient.pushSyncDelta(baseUrl, state.deviceId, outbound)
            pushedCount = pushRes.applied
            if (pushRes.latestRevision > latestRevision) latestRevision = pushRes.latestRevision
            syncDao.markSynced(dirtyRows.map { it.gameId }, latestRevision)
        }

        val delta = GameVaultHubClient.fetchSyncDelta(baseUrl, state.lastRevision)
        var pulledCount = 0
        for (ch in delta.changes) {
            if (ch.revision > latestRevision) latestRevision = ch.revision
            val payload = runCatching { JSONObject(ch.payloadJson) }.getOrNull() ?: continue
            val entryId = ch.entityId.ifBlank { payload.optString("entryId") }
            if (entryId.isBlank()) continue

            val existing = syncDao.byHubEntryId(entryId) ?: syncDao.byGameId(entryId)
            val remoteLastPlayedMs = parseIsoMillis(payload.optString("lastPlayedAt"))
            val remoteFav = if (payload.has("favorite")) payload.optBoolean("favorite", false) else existing?.favorite ?: false
            val remoteCount = maxOf(existing?.playCount ?: 0, payload.optInt("playCount", 0))
            val mergedLastPlayed = maxNullable(existing?.lastPlayedAt, remoteLastPlayedMs)
            val qrJson = payload.optJSONObject("quickResume")?.toString() ?: existing?.quickResumeJson

            val targetGameId = existing?.gameId ?: entryId
            syncDao.upsert(
                (existing ?: GameSyncMetadataEntity(gameId = targetGameId, hubEntryId = entryId)).copy(
                    hubEntryId = entryId,
                    favorite = remoteFav,
                    playCount = remoteCount,
                    lastPlayedAt = mergedLastPlayed,
                    quickResumeJson = qrJson,
                    hubRevision = ch.revision,
                    localUpdatedAt = maxOf(existing?.localUpdatedAt ?: 0L, parseIsoMillis(ch.updatedAt) ?: 0L),
                    dirty = false,
                )
            )

            // Also mirror into local GameEntity / Favorites when that game exists in Room
            gameDao.byId(targetGameId)?.let { localGame ->
                if (mergedLastPlayed != null && mergedLastPlayed > (localGame.lastPlayedAt ?: 0L)) {
                    gameDao.markPlayed(targetGameId, mergedLastPlayed)
                }
                if (remoteFav) {
                    collectionDao.addMembership(CollectionMembershipEntity("favorites", targetGameId))
                }
            }
            pulledCount++
        }

        syncDao.upsertSyncState(
            state.copy(
                lastRevision = maxOf(latestRevision, delta.latestRevision),
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        return HubSyncSummary(
            pushed = pushedCount,
            pulled = pulledCount,
            latestRevision = maxOf(latestRevision, delta.latestRevision),
        )
    }

    private fun parseIsoMillis(raw: String?): Long? {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank() || clean == "null") return null
        return runCatching { Instant.parse(clean).toEpochMilli() }.getOrNull()
    }

    private fun maxNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    companion object {
        const val DEFAULT_HUB_ID = "default-pc-hub"
    }
}

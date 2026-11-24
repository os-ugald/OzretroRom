package cl.gakuto.kizuna.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Room v7 write-through store for per-game metadata (custom artwork, synced Libretro/SGDB artwork,
 * descriptions, and patched ROMs) backed by table `game_sync_metadata`.
 */
class GameMetadataStore private constructor(
    private val dao: SyncMetadataDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, GameSyncMetadataEntity>()
    private val hydrated = CompletableDeferred<Unit>()

    init {
        scope.launch {
            runCatching {
                dao.all().forEach { cache[it.gameId] = it }
            }
            if (!hydrated.isCompleted) hydrated.complete(Unit)
        }
    }

    private fun ensureHydrated() {
        if (!hydrated.isCompleted) {
            runBlocking(Dispatchers.IO) {
                runCatching {
                    dao.all().forEach { cache[it.gameId] = it }
                }
                if (!hydrated.isCompleted) hydrated.complete(Unit)
            }
        }
    }

    fun get(gameId: String): GameSyncMetadataEntity? {
        ensureHydrated()
        return cache[gameId]
    }

    fun update(gameId: String, markDirty: Boolean = true, transform: (GameSyncMetadataEntity) -> GameSyncMetadataEntity) {
        ensureHydrated()
        val now = System.currentTimeMillis()
        val updated = cache.compute(gameId) { id, current ->
            val base = current ?: GameSyncMetadataEntity(gameId = id)
            transform(base).copy(
                localUpdatedAt = now,
                dirty = if (markDirty) true else base.dirty,
            )
        }
        if (updated != null) {
            scope.launch {
                runCatching { dao.upsert(updated) }
            }
        }
    }

    fun saveCustomArtworkUrl(gameId: String, value: String?) {
        val clean = value?.trim()?.takeIf { it.isNotBlank() }
        update(gameId) { it.copy(customArtworkUrl = clean) }
    }

    fun saveCustomArtworkUri(gameId: String, value: String?) {
        update(gameId) { it.copy(customArtworkUri = value?.trim()?.takeIf { s -> s.isNotBlank() }) }
    }

    fun saveSyncedArtworkLocalUri(gameId: String, value: String?) {
        update(gameId, markDirty = false) { it.copy(syncedArtworkLocalUri = value?.trim()?.takeIf { s -> s.isNotBlank() }) }
    }

    fun saveSyncedArtworkAlias(gameId: String, title: String?, repository: String?) {
        val t = title?.trim()?.takeIf { it.isNotBlank() }
        val r = repository?.trim()?.takeIf { it.isNotBlank() }
        update(gameId, markDirty = false) {
            if (t == null || r == null) it.copy(syncedArtworkTitle = null, syncedArtworkRepo = null)
            else it.copy(syncedArtworkTitle = t, syncedArtworkRepo = r)
        }
    }

    fun saveSyncedGameIconLocalUri(gameId: String, value: String?) {
        update(gameId, markDirty = false) { it.copy(syncedGameIconLocalUri = value?.trim()?.takeIf { s -> s.isNotBlank() }) }
    }

    fun saveCachedDescription(gameId: String, description: String) {
        update(gameId) { it.copy(cachedDescription = description.trim()) }
    }

    fun savePatchedRom(gameId: String, path: String, label: String, format: String) {
        update(gameId) {
            it.copy(patchedRomPath = path, patchedRomLabel = label, patchedRomFormat = format)
        }
    }

    fun clearPatchedRom(gameId: String) {
        update(gameId) {
            it.copy(patchedRomPath = null, patchedRomLabel = null, patchedRomFormat = null)
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, GameMetadataStore>()

        fun get(context: Context): GameMetadataStore {
            val appCtx = context.applicationContext ?: context
            val dbFile = appCtx.getDatabasePath("oz-retrorom.db")
            val key = runCatching { File(dbFile.absolutePath).canonicalPath }.getOrElse { dbFile.absolutePath }
            return instances.getOrPut(key) {
                GameMetadataStore(OzDatabase.get(appCtx).syncMetadataDao())
            }
        }
    }
}

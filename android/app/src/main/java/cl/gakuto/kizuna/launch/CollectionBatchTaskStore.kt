package cl.gakuto.kizuna.launch

import android.content.Context
import org.json.JSONObject

enum class CollectionBatchStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED,
    CANCELLED,
}

data class CollectionBatchSnapshot(
    val collectionId: String,
    val collectionName: String,
    val status: CollectionBatchStatus,
    val stage: String,
    val gameTitle: String,
    val completed: Int,
    val total: Int,
    val currentDownloaded: Long,
    val currentTotal: Long,
    val downloadedGames: Int,
    val alreadyAvailable: Int,
    val failedCount: Int,
    val needsConfirmationCount: Int,
    val error: String?,
    val startedAt: Long,
    val updatedAt: Long,
) {
    val isActive: Boolean get() = status == CollectionBatchStatus.QUEUED || status == CollectionBatchStatus.RUNNING
}

/** Persistent observable-by-polling state for a collection batch. */
class CollectionBatchTaskStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun create(collectionId: String, collectionName: String, totalHint: Int): CollectionBatchSnapshot {
        val now = System.currentTimeMillis()
        val existing = get(collectionId)
        if (existing?.isActive == true) return existing
        val snapshot = CollectionBatchSnapshot(
            collectionId = collectionId,
            collectionName = collectionName,
            status = CollectionBatchStatus.QUEUED,
            stage = "Preparando batch download…",
            gameTitle = "",
            completed = 0,
            total = totalHint.coerceAtLeast(0),
            currentDownloaded = 0L,
            currentTotal = -1L,
            downloadedGames = 0,
            alreadyAvailable = 0,
            failedCount = 0,
            needsConfirmationCount = 0,
            error = null,
            startedAt = now,
            updatedAt = now,
        )
        save(snapshot)
        return snapshot
    }

    fun get(collectionId: String): CollectionBatchSnapshot? = prefs.getString(key(collectionId), null)?.let(::decode)

    @Synchronized
    fun markRunning(collectionId: String): CollectionBatchSnapshot? = update(collectionId) {
        it.copy(status = CollectionBatchStatus.RUNNING, stage = "Preparando lote…", error = null)
    }

    @Synchronized
    fun updateProgress(collectionId: String, progress: CollectionDownloadManager.Progress): CollectionBatchSnapshot? = update(collectionId) {
        it.copy(
            status = CollectionBatchStatus.RUNNING,
            stage = progress.stage,
            gameTitle = progress.gameTitle,
            completed = progress.completed,
            total = if (progress.total > 0) progress.total else it.total,
            currentDownloaded = progress.currentDownloaded,
            currentTotal = progress.currentTotal,
            error = null,
        )
    }

    @Synchronized
    fun markResult(collectionId: String, result: CollectionDownloadManager.Result): CollectionBatchSnapshot? = update(collectionId) {
        val status = when {
            result.failed.isEmpty() && result.needsConfirmation.isEmpty() -> CollectionBatchStatus.SUCCESS
            result.downloaded > 0 || result.alreadyAvailable > 0 -> CollectionBatchStatus.PARTIAL
            else -> CollectionBatchStatus.FAILED
        }
        it.copy(
            status = status,
            stage = when (status) {
                CollectionBatchStatus.SUCCESS -> "Colección descargada"
                CollectionBatchStatus.PARTIAL -> "Batch finalizado con elementos pendientes"
                else -> "No se pudo completar la colección"
            },
            gameTitle = "",
            completed = (result.downloaded + result.failed.size + result.needsConfirmation.size).coerceAtLeast(it.completed),
            downloadedGames = result.downloaded,
            alreadyAvailable = result.alreadyAvailable,
            failedCount = result.failed.size,
            needsConfirmationCount = result.needsConfirmation.size,
            currentDownloaded = 0L,
            currentTotal = -1L,
            error = result.failed.take(3).joinToString(" · ").takeIf { text -> text.isNotBlank() },
        )
    }

    @Synchronized
    fun markFailed(collectionId: String, message: String): CollectionBatchSnapshot? = update(collectionId) {
        it.copy(
            status = CollectionBatchStatus.FAILED,
            stage = "Batch download interrumpido",
            currentDownloaded = 0L,
            currentTotal = -1L,
            error = message.take(700),
        )
    }

    @Synchronized
    fun markCancelled(collectionId: String): CollectionBatchSnapshot? = update(collectionId) {
        it.copy(status = CollectionBatchStatus.CANCELLED, stage = "Batch download cancelado", error = null)
    }

    fun clear(collectionId: String) {
        prefs.edit().remove(key(collectionId)).apply()
    }

    private fun update(collectionId: String, transform: (CollectionBatchSnapshot) -> CollectionBatchSnapshot): CollectionBatchSnapshot? {
        val current = get(collectionId) ?: return null
        val next = transform(current).copy(updatedAt = System.currentTimeMillis())
        save(next)
        return next
    }

    private fun save(snapshot: CollectionBatchSnapshot) {
        prefs.edit().putString(key(snapshot.collectionId), encode(snapshot).toString()).apply()
    }

    private fun encode(snapshot: CollectionBatchSnapshot): JSONObject = JSONObject().apply {
        put("schema", 1)
        put("collectionId", snapshot.collectionId)
        put("collectionName", snapshot.collectionName)
        put("status", snapshot.status.name)
        put("stage", snapshot.stage)
        put("gameTitle", snapshot.gameTitle)
        put("completed", snapshot.completed)
        put("total", snapshot.total)
        put("currentDownloaded", snapshot.currentDownloaded)
        put("currentTotal", snapshot.currentTotal)
        put("downloadedGames", snapshot.downloadedGames)
        put("alreadyAvailable", snapshot.alreadyAvailable)
        put("failedCount", snapshot.failedCount)
        put("needsConfirmationCount", snapshot.needsConfirmationCount)
        put("error", snapshot.error ?: JSONObject.NULL)
        put("startedAt", snapshot.startedAt)
        put("updatedAt", snapshot.updatedAt)
    }

    private fun decode(raw: String): CollectionBatchSnapshot? = runCatching {
        val json = JSONObject(raw)
        CollectionBatchSnapshot(
            collectionId = json.getString("collectionId"),
            collectionName = json.optString("collectionName", "Colección"),
            status = runCatching { CollectionBatchStatus.valueOf(json.optString("status")) }.getOrDefault(CollectionBatchStatus.FAILED),
            stage = json.optString("stage"),
            gameTitle = json.optString("gameTitle"),
            completed = json.optInt("completed"),
            total = json.optInt("total"),
            currentDownloaded = json.optLong("currentDownloaded"),
            currentTotal = json.optLong("currentTotal", -1L),
            downloadedGames = json.optInt("downloadedGames"),
            alreadyAvailable = json.optInt("alreadyAvailable"),
            failedCount = json.optInt("failedCount"),
            needsConfirmationCount = json.optInt("needsConfirmationCount"),
            error = json.optString("error").takeIf { it.isNotBlank() && it != "null" },
            startedAt = json.optLong("startedAt"),
            updatedAt = json.optLong("updatedAt"),
        )
    }.getOrNull()

    private fun key(collectionId: String) = "batch:$collectionId"

    companion object {
        private const val PREFS = "oz_collection_batch_downloads"
    }
}

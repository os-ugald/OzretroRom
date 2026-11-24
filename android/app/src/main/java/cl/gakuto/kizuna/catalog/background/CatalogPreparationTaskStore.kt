package cl.gakuto.kizuna.catalog.background

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class CatalogPreparationStatus {
    QUEUED,
    RUNNING,
    SUSPENDED,
    SUCCESS,
    PARTIAL,
    FAILED,
    CANCELLED,
}

data class CatalogPreparationSnapshot(
    val status: CatalogPreparationStatus,
    val systemIds: List<String>,
    val completeCatalog: Boolean,
    val stage: String,
    val currentSystem: String,
    val processedSystems: Int,
    val totalTargets: Int,
    val readyTargets: Int,
    val failedCount: Int,
    val error: String?,
    val startedAt: Long,
    val updatedAt: Long,
    val currentPreparedGames: Int = 0,
    val lastProgressAt: Long = 0L,
    val stopReason: Int = 0,
    val attempt: Int = 0,
) {
    val isActive: Boolean
        get() = status == CatalogPreparationStatus.QUEUED || status == CatalogPreparationStatus.RUNNING

    val canResume: Boolean
        get() = isActive || status == CatalogPreparationStatus.SUSPENDED
}

internal const val CATALOG_PREPARATION_STALE_AFTER_MILLIS = 5 * 60 * 1_000L

internal fun isCatalogPreparationStale(
    snapshot: CatalogPreparationSnapshot,
    now: Long,
    staleAfterMillis: Long = CATALOG_PREPARATION_STALE_AFTER_MILLIS,
): Boolean {
    if (!snapshot.isActive || staleAfterMillis <= 0L) return false
    val heartbeat = maxOf(snapshot.lastProgressAt, snapshot.updatedAt, snapshot.startedAt)
    return heartbeat > 0L && now - heartbeat >= staleAfterMillis
}

internal fun catalogPreparationElapsedMillis(
    snapshot: CatalogPreparationSnapshot,
    now: Long,
): Long {
    if (snapshot.startedAt <= 0L) return 0L
    val finishedAt = snapshot.updatedAt.takeIf { it >= snapshot.startedAt } ?: snapshot.startedAt
    val end = if (snapshot.isActive) now.coerceAtLeast(snapshot.startedAt) else finishedAt
    return (end - snapshot.startedAt).coerceAtLeast(0L)
}

internal fun formatCatalogPreparationElapsed(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

internal fun catalogPreparationResultStatus(totalTargets: Int, readyTargets: Int): CatalogPreparationStatus = when {
    totalTargets > 0 && readyTargets >= totalTargets -> CatalogPreparationStatus.SUCCESS
    readyTargets > 0 -> CatalogPreparationStatus.PARTIAL
    else -> CatalogPreparationStatus.FAILED
}

/** Persistent state shared by the catalog UI and its background executor. */
class CatalogPreparationTaskStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun create(systemIds: Collection<String>, completeCatalog: Boolean): CatalogPreparationSnapshot = synchronized(LOCK) {
        val existing = get()
        if (existing?.isActive == true) return@synchronized existing
        val now = System.currentTimeMillis()
        val targets = systemIds.distinct()
        if (
            existing?.status == CatalogPreparationStatus.SUSPENDED &&
            existing.completeCatalog == completeCatalog &&
            existing.systemIds.size == targets.size &&
            existing.systemIds.toSet() == targets.toSet()
        ) {
            return@synchronized existing
        }
        CatalogPreparationSnapshot(
            status = CatalogPreparationStatus.QUEUED,
            systemIds = targets,
            completeCatalog = completeCatalog,
            stage = if (completeCatalog) "Comprobando catálogo local…" else "Comprobando plataformas seleccionadas…",
            currentSystem = "",
            processedSystems = 0,
            totalTargets = targets.size,
            readyTargets = 0,
            failedCount = 0,
            error = null,
            startedAt = now,
            updatedAt = now,
            lastProgressAt = now,
        ).also(::save)
    }

    fun get(): CatalogPreparationSnapshot? = prefs.getString(KEY_TASK, null)?.let(::decode)

    fun markQueued(): CatalogPreparationSnapshot? = update {
        if (!it.canResume || it.isActive) {
            it
        } else {
            it.copy(
                status = CatalogPreparationStatus.QUEUED,
                stage = "Preparación en cola; esperando a Android…",
                stopReason = 0,
                error = null,
                lastProgressAt = System.currentTimeMillis(),
            )
        }
    }

    fun markRunning(): CatalogPreparationSnapshot? = update {
        if (!it.canResume) {
            it
        } else {
            it.copy(
                status = CatalogPreparationStatus.RUNNING,
                stage = if (it.status == CatalogPreparationStatus.SUSPENDED) {
                    "Reanudando preparación del catálogo…"
                } else {
                    it.stage
                },
                error = null,
                stopReason = 0,
                attempt = it.attempt + 1,
                lastProgressAt = System.currentTimeMillis(),
            )
        }
    }

    fun updateProgress(
        progress: CatalogPreparationManager.Progress,
        expectedAttempt: Int? = null,
    ): CatalogPreparationSnapshot? = update {
        if (!it.isActive || expectedAttempt != null && it.attempt != expectedAttempt) {
            it
        } else {
            it.copy(
                status = CatalogPreparationStatus.RUNNING,
                stage = progress.stage,
                currentSystem = progress.currentSystem,
                processedSystems = progress.processedSystems,
                totalTargets = progress.totalTargets,
                failedCount = progress.failedCount,
                currentPreparedGames = progress.currentPreparedGames,
                lastProgressAt = System.currentTimeMillis(),
                stopReason = 0,
                error = null,
            )
        }
    }

    fun markResult(
        summary: CatalogPreparationManager.Summary,
        expectedAttempt: Int? = null,
    ): CatalogPreparationSnapshot? = update {
        if (
            it.status == CatalogPreparationStatus.CANCELLED ||
            expectedAttempt != null && it.attempt != expectedAttempt
        ) {
            return@update it
        }
        val status = catalogPreparationResultStatus(summary.totalTargets, summary.readyTargets)
        val stage = when {
            status == CatalogPreparationStatus.SUCCESS && it.completeCatalog -> "Catálogo completo preparado"
            status == CatalogPreparationStatus.SUCCESS -> "Plataformas seleccionadas preparadas"
            summary.failedCount > 0 -> "${summary.remainingSystems} sistemas pendientes · pulsa Reintentar"
            else -> "${summary.remainingSystems} sistemas pendientes · pulsa Continuar"
        }
        it.copy(
            status = status,
            stage = stage,
            currentSystem = "",
            processedSystems = summary.processedSystems,
            totalTargets = summary.totalTargets,
            readyTargets = summary.readyTargets,
            failedCount = summary.failedCount,
            currentPreparedGames = 0,
            stopReason = 0,
            error = summary.errors.take(3).joinToString(" · ").takeIf(String::isNotBlank),
        )
    }

    fun markFailed(message: String, expectedAttempt: Int? = null): CatalogPreparationSnapshot? = update {
        if (
            it.status == CatalogPreparationStatus.CANCELLED ||
            expectedAttempt != null && it.attempt != expectedAttempt
        ) {
            it
        } else {
            it.copy(
                status = CatalogPreparationStatus.FAILED,
                stage = "Preparación del catálogo interrumpida",
                currentSystem = "",
                currentPreparedGames = 0,
                error = message.take(700),
            )
        }
    }

    fun markSuspended(stopReason: Int, message: String): CatalogPreparationSnapshot? = update {
        if (!it.isActive) {
            it
        } else {
            it.copy(
                status = CatalogPreparationStatus.SUSPENDED,
                stage = message.take(700),
                stopReason = stopReason,
                error = null,
            )
        }
    }

    fun markCancelled(): CatalogPreparationSnapshot? = update {
        it.copy(
            status = CatalogPreparationStatus.CANCELLED,
            stage = "Preparación pausada · el progreso guardado se conserva",
            currentSystem = "",
            currentPreparedGames = 0,
            stopReason = 0,
            error = null,
        )
    }

    private fun update(
        transform: (CatalogPreparationSnapshot) -> CatalogPreparationSnapshot,
    ): CatalogPreparationSnapshot? = synchronized(LOCK) {
        val current = get() ?: return null
        val transformed = transform(current)
        val next = if (transformed == current) current else transformed.copy(updatedAt = System.currentTimeMillis())
        if (next != current) save(next)
        next
    }

    private fun save(snapshot: CatalogPreparationSnapshot) {
        // This state is the checkpoint used after process death. Commit it before JobScheduler
        // returns from a stop callback instead of risking an asynchronous write being lost.
        prefs.edit().putString(KEY_TASK, encode(snapshot).toString()).commit()
    }

    private fun encode(snapshot: CatalogPreparationSnapshot): JSONObject = JSONObject().apply {
        put("schema", 2)
        put("status", snapshot.status.name)
        put("systemIds", JSONArray(snapshot.systemIds))
        put("completeCatalog", snapshot.completeCatalog)
        put("stage", snapshot.stage)
        put("currentSystem", snapshot.currentSystem)
        put("processedSystems", snapshot.processedSystems)
        put("totalTargets", snapshot.totalTargets)
        put("readyTargets", snapshot.readyTargets)
        put("failedCount", snapshot.failedCount)
        put("error", snapshot.error ?: JSONObject.NULL)
        put("startedAt", snapshot.startedAt)
        put("updatedAt", snapshot.updatedAt)
        put("currentPreparedGames", snapshot.currentPreparedGames)
        put("lastProgressAt", snapshot.lastProgressAt)
        put("stopReason", snapshot.stopReason)
        put("attempt", snapshot.attempt)
    }

    private fun decode(raw: String): CatalogPreparationSnapshot? = runCatching {
        val json = JSONObject(raw)
        val ids = json.optJSONArray("systemIds") ?: JSONArray()
        CatalogPreparationSnapshot(
            status = runCatching { CatalogPreparationStatus.valueOf(json.optString("status")) }
                .getOrDefault(CatalogPreparationStatus.FAILED),
            systemIds = (0 until ids.length()).mapNotNull { index ->
                ids.optString(index).takeIf(String::isNotBlank)
            },
            completeCatalog = json.optBoolean("completeCatalog"),
            stage = json.optString("stage"),
            currentSystem = json.optString("currentSystem"),
            processedSystems = json.optInt("processedSystems"),
            totalTargets = json.optInt("totalTargets"),
            readyTargets = json.optInt("readyTargets"),
            failedCount = json.optInt("failedCount"),
            error = json.optString("error").takeIf { it.isNotBlank() && it != "null" },
            startedAt = json.optLong("startedAt"),
            updatedAt = json.optLong("updatedAt"),
            currentPreparedGames = json.optInt("currentPreparedGames"),
            lastProgressAt = json.optLong("lastProgressAt", json.optLong("updatedAt")),
            stopReason = json.optInt("stopReason"),
            attempt = json.optInt("attempt"),
        )
    }.getOrNull()

    companion object {
        private val LOCK = Any()
        private const val PREFS = "oz_catalog_preparation"
        private const val KEY_TASK = "active_task"
    }
}

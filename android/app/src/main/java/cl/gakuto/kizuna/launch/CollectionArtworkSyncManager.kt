package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.Uri
import cl.gakuto.kizuna.catalog.LibretroCatalogRepository
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.util.ArtworkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Explicit collection artwork downloader.
 *
 * This action NEVER resolves or downloads ROMs. It prepares only the Libretro DAT indexes needed
 * by the selected collection, resolves the best official thumbnail identity and downloads one
 * usable image per game into Oz's private persistent artwork cache. Memberships, game IDs, launch
 * metadata and user-selected custom artwork are never replaced.
 */
class CollectionArtworkSyncManager(
    private val context: Context,
    private val preferences: AppPreferences,
    private val database: OzDatabase,
) {
    data class Progress(
        val completed: Int,
        val total: Int,
        val resolved: Int,
        val missing: Int,
        val gameTitle: String,
        val stage: String,
    )

    data class Result(
        val total: Int,
        val resolved: Int,
        val missing: Int,
    )

    private data class VisualDownload(
        val alias: GameEntity,
        val localUri: String,
    )

    suspend fun sync(collectionId: String, onProgress: (Progress) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        val repository = LibraryRepository(database)
        val games = repository.collectionGamesOnce(collectionId)
        val result = syncGames(games, onlyMissing = false, onProgress = onProgress)
        preferences.setCollectionArtworkSyncSnapshot(collectionId, result.resolved, result.total)
        result
    }

    suspend fun syncGames(
        games: List<GameEntity>,
        onlyMissing: Boolean = true,
        onProgress: (Progress) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        if (games.isEmpty()) return@withContext Result(0, 0, 0)
        val catalog = LibretroCatalogRepository(context, database)
        val systems = catalog.systems().associateBy { it.id }
        val systemIds = games.map { it.systemId }.distinct()

        // This explicit action may prepare only the DAT indexes needed for artwork matching. It
        // never asks ROMgi for ROM sources and never invokes the ROM download engine.
        systemIds.forEach { systemId ->
            val state = database.catalogStateDao().byId(systemId)
            if (state?.status != "READY") {
                val system = systems[systemId] ?: return@forEach
                onProgress(Progress(0, games.size, 0, 0, system.name, "Preparando índice de artwork"))
                catalog.index(system) { indexProgress ->
                    onProgress(Progress(0, games.size, 0, 0, system.name, indexProgress.stage))
                }.getOrThrow()
            }
        }

        var resolved = 0
        var missing = 0

        games.forEachIndexed { index, game ->
            if (onlyMissing) {
                val existing = preferences.syncedArtworkLocalUri(game.id)
                    ?.let { android.net.Uri.parse(it) }
                    ?.takeIf { it.scheme == "file" }
                    ?.path
                    ?.let { java.io.File(it) }
                    ?.takeIf { it.isFile && it.length() > 0L }
                if (existing != null) {
                    resolved++
                    onProgress(Progress(index + 1, games.size, resolved, missing, game.title, "Carátula en caché"))
                    return@forEachIndexed
                }
            }
            onProgress(Progress(index, games.size, resolved, missing, game.title, "Descargando carátula"))
            val visual = resolveAndDownload(game)
            if (visual != null) {
                preferences.setSyncedArtworkAlias(game.id, visual.alias.title, visual.alias.thumbnailRepo)
                preferences.setSyncedArtworkLocalUri(game.id, visual.localUri)
                resolved++
            } else {
                // A completed explicit sync is authoritative for the official cache, but custom
                // user artwork remains untouched and still has higher priority in ArtworkResolver.
                preferences.setSyncedArtworkAlias(game.id, null, null)
                preferences.setSyncedArtworkLocalUri(game.id, null)
                cachedArtworkFile(game)?.delete()
                missing++
            }
            onProgress(Progress(index + 1, games.size, resolved, missing, game.title, if (visual != null) "Carátula guardada" else "Sin coincidencia"))
        }

        Result(games.size, resolved, missing)
    }

    private suspend fun resolveAndDownload(game: GameEntity): VisualDownload? {
        val variants = linkedMapOf<String, GameEntity>()
        fun add(candidate: GameEntity?) {
            if (candidate == null || candidate.thumbnailRepo.isNullOrBlank()) return
            variants.putIfAbsent("${candidate.thumbnailRepo}|${candidate.title}", candidate)
        }

        add(game)
        if (game.canonicalTitle.isNotBlank()) {
            database.gameDao().artworkVariants(game.systemId, game.canonicalTitle, 8).forEach(::add)
        }

        val target = cachedArtworkFile(game)
        for (candidate in variants.values) {
            for (url in ArtworkResolver.remoteCandidates(candidate)) {
                if (downloadOfficialArtwork(url, target)) {
                    return VisualDownload(candidate, Uri.fromFile(target).toString())
                }
            }
        }
        return null
    }

    /** Returns false only when the official candidate does not exist; transient server errors fail the sync. */
    private fun downloadOfficialArtwork(url: String, target: File): Boolean {
        require(url.startsWith("https://", ignoreCase = true)) { "Artwork no seguro bloqueado." }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 7_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("User-Agent", "OzRetroRom/0.6.0-beta.4 artwork-download")
        val part = File(target.parentFile, "${target.name}.part")
        try {
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE -> false
                in 200..299 -> {
                    require(connection.url.protocol.equals("https", ignoreCase = true)) { "Redirección de artwork no segura bloqueada." }
                    val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                    require(contentType.isNullOrBlank() || contentType.startsWith("image/")) { "El servidor no devolvió una imagen ($contentType)." }
                    val announced = connection.contentLengthLong
                    require(announced <= 0L || announced <= MAX_ARTWORK_BYTES) {
                        "Artwork excede el límite de seguridad (${announced} bytes)."
                    }
                    target.parentFile?.mkdirs()
                    part.delete()
                    var total = 0L
                    connection.inputStream.buffered().use { input ->
                        FileOutputStream(part).buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= MAX_ARTWORK_BYTES) { "Artwork excede el límite de seguridad." }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    require(total > 0L) { "El servidor devolvió artwork vacío." }
                    if (target.exists() && !target.delete()) error("No se pudo reemplazar el artwork anterior.")
                    if (!part.renameTo(target)) {
                        part.copyTo(target, overwrite = true)
                        part.delete()
                    }
                    true
                }
                else -> throw IOException("Servidor de artwork respondió HTTP $code")
            }
        } finally {
            connection.disconnect()
            if (part.exists() && !target.exists()) part.delete()
        }
    }

    private fun cachedArtworkFile(game: GameEntity): File {
        val system = game.systemId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "system" }
        val id = game.id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "game" }
        return File(File(context.filesDir, "artwork-sync/$system"), "$id.png")
    }

    companion object {
        private const val MAX_ARTWORK_BYTES = 12L * 1024L * 1024L
    }
}

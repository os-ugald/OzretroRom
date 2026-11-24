package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.Uri
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.util.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

/**
 * Explicit SteamGridDB square-icon synchronization for Oz navigation tiles.
 *
 * API contract intentionally mirrors SteamGridDB's maintained official node wrapper:
 *   GET /api/v2/search/autocomplete/{query}
 *   GET /api/v2/icons/game/{id}
 *   Authorization: Bearer <api key>
 *
 * The network is never contacted by a Compose tile. Images are cached in app-private storage and
 * normal Oz/Libretro artwork remains the fallback when a game cannot be matched.
 */
class SteamGridDbIconSyncManager(
    private val context: Context,
    private val preferences: AppPreferences,
    database: OzDatabase,
) {
    private val repository = LibraryRepository(database)

    data class Progress(
        val completed: Int,
        val total: Int,
        val resolved: Int,
        val missing: Int,
        val gameTitle: String,
        val stage: String,
    )

    data class Result(val total: Int, val resolved: Int, val missing: Int)

    private data class Match(val id: Int, val name: String, val verified: Boolean)
    private data class Icon(val url: String, val width: Int, val height: Int, val score: Int, val upvotes: Int, val downvotes: Int)

    suspend fun sync(collectionId: String, onProgress: (Progress) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        val games = repository.collectionGamesOnce(collectionId)
        require(games.isNotEmpty()) { "La colección no contiene juegos." }
        val result = syncGames(games, onProgress)
        preferences.setCollectionArtworkSyncSnapshot(collectionId, result.resolved, result.total)
        result
    }

    suspend fun syncGames(games: List<GameEntity>, onProgress: (Progress) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        val apiKey = preferences.steamGridDbApiKey.trim()
        require(apiKey.isNotBlank()) {
            "Configura tu API key de SteamGridDB en Configuración → Rutas, artwork y APIs."
        }
        if (games.isEmpty()) return@withContext Result(0, 0, 0)

        var resolved = 0
        var missing = 0
        games.forEachIndexed { index, game ->
            onProgress(Progress(index, games.size, resolved, missing, game.title, "Buscando icono SteamGridDB"))
            val target = cachedIconFile(game)
            val cached = preferences.syncedGameIconLocalUri(game.id)
                ?.let(Uri::parse)
                ?.takeIf { it.scheme == "file" }
                ?.path
                ?.let(::File)
                ?.takeIf { it.isFile && it.length() > 0L }
            if (cached != null) {
                resolved++
                onProgress(Progress(index + 1, games.size, resolved, missing, game.title, "Icono en caché"))
                return@forEachIndexed
            }

            val gameId = preferences.steamGridDbGameId(game.id) ?: findGame(game, apiKey)?.id?.also {
                preferences.setSteamGridDbGameId(game.id, it)
            }
            val icon = gameId?.let { findBestIcon(it, apiKey) }
            if (icon != null && downloadIcon(icon.url, target)) {
                preferences.setSyncedGameIconLocalUri(game.id, Uri.fromFile(target).toString())
                resolved++
            } else {
                preferences.setSyncedGameIconLocalUri(game.id, null)
                target.delete()
                missing++
            }
            onProgress(Progress(index + 1, games.size, resolved, missing, game.title, if (icon != null) "Icono guardado" else "Sin icono compatible"))
        }

        Result(games.size, resolved, missing)
    }

    private fun findGame(game: GameEntity, apiKey: String): Match? {
        val query = SteamGridDbClient.searchableTitle(game.title)
        val matches = SteamGridDbClient.searchGames(query, apiKey)
        if (matches.isEmpty()) return null

        val wanted = TextNormalizer.normalize(query)
        return matches
            .map { Match(it.id, it.name, it.verified) }
            .minWithOrNull(
                compareBy<Match>({ matchRank(wanted, TextNormalizer.normalize(it.name)) }, { if (it.verified) 0 else 1 }, { it.name.length }),
            )
    }

    private fun findBestIcon(gameId: Int, apiKey: String): Icon? {
        val assets = SteamGridDbClient.fetchAssets(gameId, "icons", apiKey)
        if (assets.isEmpty()) return null

        return assets.map {
            Icon(
                url = it.url,
                width = 0, // client doesn't fetch size for speed, icon logic will adapt or we update client
                height = 0,
                score = it.score,
                upvotes = 0,
                downvotes = 0
            )
        }.minWithOrNull(
            compareBy<Icon>({ -(it.score * 100) })
        )
    }


    private fun downloadIcon(url: String, target: File): Boolean {
        require(url.startsWith("https://", ignoreCase = true)) { "Icono no seguro bloqueado." }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 8_000
        connection.readTimeout = 18_000
        connection.setRequestProperty("User-Agent", "OzRetroRom/0.6.0-beta.4 icon-cache")
        val part = File(target.parentFile, "${target.name}.part")
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND || code == HttpURLConnection.HTTP_GONE) return false
            if (code !in 200..299) throw IOException("Servidor de iconos respondió HTTP $code")
            require(connection.url.protocol.equals("https", ignoreCase = true)) { "Redirección de icono no segura bloqueada." }
            val type = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
            require(type.isNullOrBlank() || type.startsWith("image/")) { "SteamGridDB no devolvió una imagen ($type)." }
            val announced = connection.contentLengthLong
            require(announced <= 0L || announced <= MAX_ICON_BYTES) { "El icono excede el límite de seguridad." }
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
                        require(total <= MAX_ICON_BYTES) { "El icono excede el límite de seguridad." }
                        output.write(buffer, 0, read)
                    }
                }
            }
            require(total > 0L) { "SteamGridDB devolvió un icono vacío." }
            if (target.exists() && !target.delete()) error("No se pudo reemplazar el icono anterior.")
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            return true
        } finally {
            connection.disconnect()
            if (part.exists() && !target.exists()) part.delete()
        }
    }

    private fun cachedIconFile(game: GameEntity): File {
        val system = safe(game.systemId, "system", 80)
        val id = safe(game.id, "game", 120)
        return File(File(context.filesDir, "steamgriddb-icons/$system"), "$id.png")
    }


    private fun matchRank(wanted: String, candidate: String): Int = when {
        candidate == wanted -> 0
        candidate.startsWith(wanted) || wanted.startsWith(candidate) -> 1
        candidate.contains(wanted) || wanted.contains(candidate) -> 2
        else -> 3
    }


    private fun safe(value: String, fallback: String, max: Int): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(max).ifBlank { fallback }
    private fun JSONArray.joinMessages(): String = (0 until length()).joinToString(" · ") { optString(it) }.ifBlank { "Error de SteamGridDB" }

    companion object {
        private const val BASE_URL = "https://www.steamgriddb.com/api/v2"
        private const val MAX_ICON_BYTES = 8L * 1024L * 1024L
    }
}

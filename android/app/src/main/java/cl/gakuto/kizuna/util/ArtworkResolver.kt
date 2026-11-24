package cl.gakuto.kizuna.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.ArtworkMode
import cl.gakuto.kizuna.data.GameEntity
import java.util.concurrent.ConcurrentHashMap

object ArtworkResolver {
    private val localIndexes = ConcurrentHashMap<String, Map<String, String>>()

    fun candidates(context: Context, preferences: AppPreferences, game: GameEntity): List<String> {
        val mode = preferences.gameArtworkMode(game.id) ?: preferences.artworkMode
        val custom = listOfNotNull(
            preferences.customArtworkUri(game.id)?.toString(),
            preferences.customArtworkUrl(game.id),
        )
        val syncedLocal = preferences.syncedArtworkLocalUri(game.id)?.let { listOf(it) }.orEmpty()
        val local = localArtwork(context, preferences, game)?.let { listOf(it) }.orEmpty()
        val remoteGame = syncedAlias(preferences, game)

        return when (mode) {
            // Explicit collection artwork sync is a durable local cache for AUTO mode.
            // User-selected custom art still wins, while explicit BOXART/TITLE/SNAP modes keep
            // their semantic meaning and continue to target the requested upstream category.
            ArtworkMode.AUTO -> (
                custom + syncedLocal + local +
                    LibretroThumbnailHelper.candidateUrls(
                        remoteGame.thumbnailRepo,
                        remoteGame.systemId,
                        remoteGame.title,
                        listOf("Named_Boxarts", "Named_Titles", "Named_Snaps"),
                    )
            ).distinct()
            ArtworkMode.BOXART -> (
                custom + LibretroThumbnailHelper.candidateUrls(
                    remoteGame.thumbnailRepo,
                    remoteGame.systemId,
                    remoteGame.title,
                    listOf("Named_Boxarts"),
                )
            ).distinct()
            ArtworkMode.TITLE_SCREEN -> (
                custom + LibretroThumbnailHelper.candidateUrls(
                    remoteGame.thumbnailRepo,
                    remoteGame.systemId,
                    remoteGame.title,
                    listOf("Named_Titles"),
                )
            ).distinct()
            ArtworkMode.SNAP -> (
                custom + LibretroThumbnailHelper.candidateUrls(
                    remoteGame.thumbnailRepo,
                    remoteGame.systemId,
                    remoteGame.title,
                    listOf("Named_Snaps"),
                )
            ).distinct()
            ArtworkMode.LOCAL_FOLDER -> (custom + local).distinct()
            ArtworkMode.CUSTOM -> custom
        }.distinct()
    }

    /**
     * Artwork candidates for dense navigation tiles. A synchronized square SteamGridDB icon wins
     * over normal box art, while user custom art keeps absolute priority. If no square icon exists,
     * the normal Libretro/local chain is preserved as an offline-safe fallback.
     */
    fun tileCandidates(context: Context, preferences: AppPreferences, game: GameEntity): List<String> {
        val custom = listOfNotNull(
            preferences.customArtworkUri(game.id)?.toString(),
            preferences.customArtworkUrl(game.id),
        )
        val square = preferences.syncedGameIconLocalUri(game.id)?.let(::listOf).orEmpty()
        return (custom + square + candidates(context, preferences, game)).distinct()
    }

    fun label(preferences: AppPreferences, gameId: String): String =
        (preferences.gameArtworkMode(gameId) ?: preferences.artworkMode).label

    fun clearLocalCache() = localIndexes.clear()

    /** Remote candidates using Libretro title/repository fallback chain for [game]. */
    fun remoteCandidates(game: GameEntity): List<String> =
        LibretroThumbnailHelper.candidateUrls(
            repo = game.thumbnailRepo,
            systemId = game.systemId,
            title = game.title,
            folders = listOf("Named_Boxarts", "Named_Titles", "Named_Snaps"),
        )

    private fun syncedAlias(preferences: AppPreferences, game: GameEntity): GameEntity {
        val title = preferences.syncedArtworkTitle(game.id) ?: return game
        val repository = preferences.syncedArtworkRepo(game.id) ?: return game
        return game.copy(title = title, thumbnailRepo = repository)
    }

    private fun remote(game: GameEntity, folder: String): String? {
        val repository = game.thumbnailRepo?.takeIf { it.isNotBlank() }
            ?: LibretroThumbnailHelper.repoForSystem(game.systemId)
            ?: return null
        return LibretroThumbnailHelper.buildUrl(repository, folder, game.title)
    }

    private fun localArtwork(context: Context, preferences: AppPreferences, game: GameEntity): String? {
        val treeUri = preferences.localArtworkTreeUri ?: return null
        val key = treeUri.toString()
        val index = localIndexes[key] ?: buildLocalIndex(context, treeUri).also { localIndexes[key] = it }
        val stems = listOf(game.id, sanitizeTitle(game.title), game.title).map(::normalizeFileStem).distinct()
        val system = normalizeFileStem(game.systemId)
        return stems.firstNotNullOfOrNull { stem -> index["$system/$stem"] ?: index[stem] }
    }

    private fun buildLocalIndex(context: Context, treeUri: Uri): Map<String, String> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        root.listFiles().forEach { file ->
            when {
                file.isFile && isArtwork(file.name) -> {
                    val stem = normalizeFileStem(file.name.orEmpty().substringBeforeLast('.'))
                    result.putIfAbsent(stem, file.uri.toString())
                }
                file.isDirectory -> {
                    val system = normalizeFileStem(file.name.orEmpty())
                    file.listFiles().filter { it.isFile && isArtwork(it.name) }.forEach { artwork ->
                        val stem = normalizeFileStem(artwork.name.orEmpty().substringBeforeLast('.'))
                        result.putIfAbsent("$system/$stem", artwork.uri.toString())
                    }
                }
            }
        }
        return result
    }

    private fun isArtwork(name: String?): Boolean =
        name?.substringAfterLast('.', "")?.lowercase() in setOf("png", "jpg", "jpeg", "webp")

    private fun sanitizeTitle(title: String): String = title.replace('/', '_').replace('\\', '_')
    private fun normalizeFileStem(value: String): String = TextNormalizer.normalize(value).replace(Regex("[^a-z0-9]+"), "")
}

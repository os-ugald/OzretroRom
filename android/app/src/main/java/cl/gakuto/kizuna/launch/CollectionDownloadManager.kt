package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import cl.gakuto.kizuna.auth.InternetArchiveAuthManager
import cl.gakuto.kizuna.catalog.ResolvedSource
import cl.gakuto.kizuna.catalog.RomgiDownloadPlan
import cl.gakuto.kizuna.catalog.RomgiCatalog
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.data.GameDescriptor
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Real collection batch downloader.
 *
 * This class deliberately reuses [BackgroundRomDownloadEngine] for every ROM in the batch so the
 * exact same resumable HTTP, authenticated Internet Archive, FBNeo BIOS merge, validation and SAF
 * persistence path used by a single game is also used by a collection. The surrounding
 * CollectionDownloadJobService/ForegroundService owns the lifetime, so minimizing the UI does not
 * cancel the batch.
 */
class CollectionDownloadManager(
    private val context: Context,
    private val preferences: AppPreferences,
    private val database: OzDatabase,
    private val network: Network? = null,
) {
    data class Progress(
        val stage: String,
        val gameTitle: String = "",
        val completed: Int = 0,
        val total: Int = 0,
        val currentDownloaded: Long = 0L,
        val currentTotal: Long = -1L,
    )

    data class Result(
        val downloaded: Int,
        val alreadyAvailable: Int,
        val failed: List<String>,
        val needsConfirmation: List<String>,
    )

    suspend fun download(collectionId: String, onProgress: suspend (Progress) -> Unit): Result {
        val repository = LibraryRepository(database)
        val games = repository.collectionGamesOnce(collectionId)
        if (games.isEmpty()) return Result(0, 0, emptyList(), emptyList())
        if (preferences.romgiWifiOnly && !isValidatedWifi()) {
            error("Las descargas ROMgi están configuradas solo para Wi-Fi. Conéctate a Wi-Fi o cambia esa opción en Configuración.")
        }

        val persistent = PersistentRomLibrary(context, preferences, database)
        val missing = mutableListOf<GameEntity>()
        var alreadyAvailable = 0
        for (game in games) {
            val persisted = persistent.resolve(game)
            if (persisted != null || localAuthorizedSourceAvailable(preferences.authorizedSource(game.id))) {
                alreadyAvailable++
            } else {
                missing += game
            }
        }
        if (missing.isEmpty()) {
            onProgress(Progress("Todas las ROMs de la colección ya están disponibles", completed = games.size, total = games.size))
            return Result(0, alreadyAvailable, emptyList(), emptyList())
        }

        val noDestination = missing.filterNot { persistent.hasDestination(it.systemId) }
        require(noDestination.isEmpty()) {
            "Falta elegir una Biblioteca ROMs persistente para ${noDestination.first().platform}."
        }

        val auth = InternetArchiveAuthManager(context)
        val catalog = RomgiCatalog(
            context = context,
            allowTorrents = preferences.romgiTorrentsEnabled,
            preferredRegion = preferences.preferredRegion,
            internetArchiveAuthenticated = auth.isLoggedIn(),
        )
        onProgress(Progress("Preparando catálogo ROMgi para el lote…", total = missing.size))
        catalog.ensureReady { catalogProgress ->
            onProgress(
                Progress(
                    stage = catalogProgress.stage,
                    total = missing.size,
                    currentDownloaded = catalogProgress.downloaded,
                    currentTotal = catalogProgress.total,
                ),
            )
        }

        val taskStore = RomDownloadTaskStore(context)
        var downloaded = 0
        val failed = mutableListOf<String>()
        val needsConfirmation = mutableListOf<String>()

        missing.forEachIndexed { index, game ->
            try {
                onProgress(Progress("Resolviendo ROM ${index + 1}/${missing.size}…", game.title, index, missing.size))
                if (!GameDescriptor.supports(game.systemId)) {
                    failed += "${game.title}: ROMgi no publica una ruta automática para ${game.platform}"
                    return@forEachIndexed
                }

                val resolution = catalog.resolveEditions(GameDescriptor.fromEntity(game))
                if (resolution.requiresConfirmation) {
                    needsConfirmation += game.title
                    return@forEachIndexed
                }
                database.gameDao().markRomgiAvailability(game.id, true)

                val selected = resolution.recommended
                val sources = selected.discs.map { it.source.toAuthorizedSource() }
                val source = sources.first()
                if (sources.any { it.requiresInternetArchiveAuth }) {
                    when (auth.validate()) {
                        InternetArchiveAuthManager.Validation.INVALID,
                        InternetArchiveAuthManager.Validation.SIGNED_OUT -> {
                            failed += "${game.title}: Internet Archive requiere reconexión"
                            return@forEachIndexed
                        }
                        else -> Unit
                    }
                }

                val biosSources = if (game.systemId == "arcade-fbneo" && game.biosSets.isNotEmpty()) {
                    buildList {
                        game.biosSets.forEach { setName ->
                            runCatching { catalog.resolve(GameDescriptor.bios(setName)) }
                                .onSuccess { add(setName to it.toAuthorizedSource()) }
                                .onFailure { failed += "${game.title}: no se pudo resolver BIOS $setName (${it.message ?: "error"})" }
                        }
                    }
                } else {
                    emptyList()
                }

                val snapshot = taskStore.createOrReuse(
                    gameId = game.id,
                    source = source,
                    biosSources = biosSources,
                    resolvedRegion = selected.region,
                    downloadSet = selected.toDownloadSet(sources),
                )
                taskStore.markRunning(game.id, "Lote · ${index + 1}/${missing.size} · iniciando…")

                val result = BackgroundRomDownloadEngine(context.applicationContext, network).run(snapshot) { progress ->
                    taskStore.updateProgress(game.id, progress)
                    onProgress(
                        Progress(
                            stage = progress.stage,
                            gameTitle = game.title,
                            completed = index,
                            total = missing.size,
                            currentDownloaded = progress.downloaded,
                            currentTotal = progress.total,
                        ),
                    )
                }
                taskStore.markSuccess(
                    game.id,
                    result.stored.uri.toString(),
                    result.stored.displayName,
                    result.stored.bytes,
                )
                downloaded++
                onProgress(Progress("ROM guardada en Biblioteca Oz", game.title, index + 1, missing.size))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                taskStore.markFailed(game.id, error.message ?: error.javaClass.simpleName)
                failed += "${game.title}: ${error.message ?: error.javaClass.simpleName}"
                onProgress(Progress("Error; continuando con el siguiente juego", game.title, index + 1, missing.size))
            }
        }

        onProgress(Progress("Batch download finalizado", completed = missing.size, total = missing.size))
        return Result(downloaded, alreadyAvailable, failed, needsConfirmation)
    }

    private fun ResolvedSource.toAuthorizedSource(): AuthorizedSource {
        val selectedTransport = when {
            isTorrent && preferences.romgiTorrentsEnabled -> magnetUri ?: url.takeIf { it.startsWith("https://", true) }
            url.startsWith("https://", true) -> url
            !magnetUri.isNullOrBlank() && preferences.romgiTorrentsEnabled -> magnetUri
            url.startsWith("http://", true) -> error("ROMgi ofreció HTTP sin cifrar y Oz lo bloqueó.")
            else -> error("La fuente ROMgi no ofrece un transporte seguro habilitado.")
        } ?: error("La fuente no incluye magnet ni HTTPS utilizable.")
        return AuthorizedSource(
            value = selectedTransport,
            expectedFileName = fileName,
            torrentFileIndex = torrentFileIndex,
            torrentFilePath = torrentFilePath,
            expectedSize = size,
            sourceLabel = "ROMgi · ${host.ifBlank { sourceId.orEmpty() }}",
            requiresInternetArchiveAuth = requiresAuth,
        )
    }

    private fun RomgiDownloadPlan.toDownloadSet(sources: List<AuthorizedSource>): RomDownloadSet? =
        takeIf { it.isMultiDisc }?.let { plan ->
            RomDownloadSet(
                parentTitle = plan.parentTitle,
                editionTitle = plan.editionTitle,
                region = plan.region,
                format = plan.format,
                artworkUrl = plan.artworkUrl,
                items = plan.discs.zip(sources).map { (disc, source) ->
                    RomDownloadSet.singleSourceItem(
                        parentTitle = plan.parentTitle,
                        editionTitle = plan.editionTitle,
                        region = plan.region,
                        format = plan.format,
                        discIndex = disc.index,
                        discLabel = disc.label,
                        source = source,
                    )
                },
            )
        }

    private fun localAuthorizedSourceAvailable(source: AuthorizedSource?): Boolean {
        source ?: return false
        if (source.isHttp || source.isMagnet) return false
        return runCatching {
            when {
                source.isFilePath -> File(Uri.decode(source.value).trim('"', '\'')).let { it.isFile && it.length() > 0L }
                source.value.startsWith("file://", true) -> Uri.parse(source.value).path?.let(::File)?.let { it.isFile && it.length() > 0L } == true
                source.value.startsWith("content://", true) -> context.contentResolver.openAssetFileDescriptor(Uri.parse(source.value), "r")?.use {
                    it.fileDescriptor.valid() && it.length != 0L
                } == true
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun isValidatedWifi(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val active = network ?: manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

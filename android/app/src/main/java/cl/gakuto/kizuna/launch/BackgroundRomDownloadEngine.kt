package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.Network
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.DiagnosticLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class BackgroundRomDownloadEngine(
    private val context: Context,
    initialNetwork: Network? = null,
) {
    private val networkController = DownloadNetworkController(initialNetwork)
    private val activeDownloader = AtomicReference<AuthorizedSourceDownloader?>(null)

    fun updateNetwork(network: Network?) {
        if (!networkController.update(network)) return
        activeDownloader.get()?.onNetworkChanged()
    }
    private val appContext = context.applicationContext
    private val preferences = AppPreferences(appContext)
    private val database = (appContext as OzApplication).database
    private val taskStore = RomDownloadTaskStore(appContext)

    data class Result(
        val game: GameEntity,
        val stored: PersistentRomLibrary.StoredRom,
    )

    suspend fun run(task: RomDownloadTaskSnapshot, onProgress: suspend (AuthorizedSourceDownloader.Progress) -> Unit): Result =
        withContext(Dispatchers.IO) {
            val current = database.gameDao().byId(task.gameId)
                ?: error("El juego solicitado ya no existe en la biblioteca local de Oz.")
            require(preferences.romTreeUriFor(current.systemId) != null) {
                "Selecciona primero la carpeta ROMs persistente desde Configuración."
            }
            val diagnostics = DiagnosticLogger.startSession(appContext, task.stagingId)
            val store = SessionFileStore(appContext, task.stagingId)
            val downloader = AuthorizedSourceDownloader(appContext, store, diagnostics, networkController)
            activeDownloader.set(downloader)
            val persistentLibrary = PersistentRomLibrary(appContext, preferences, database)
            diagnostics.info("BACKGROUND_DOWNLOAD_BEGIN", "game_id=${current.id} system=${current.systemId} task=${task.taskId}")

            val alreadyPersisted = persistentLibrary.resolve(current)
            if (alreadyPersisted != null) {
                diagnostics.info(
                    "BACKGROUND_DOWNLOAD_ALREADY_PERSISTED",
                    "game_id=${current.id} file=${alreadyPersisted.displayName} bytes=${alreadyPersisted.bytes}",
                )
                store.deleteAll()
                return@withContext Result(current, alreadyPersisted)
            }

            try {
                task.downloadSet?.let { downloadSet ->
                    val expectedTotal = downloadSet.totalSize.takeIf { it > 0L } ?: -1L
                    var completedBytes = 0L
                    val preparedDiscs = mutableListOf<Pair<RomDownloadSetItem, File>>()
                    downloadSet.items.forEach { item ->
                        val checkpoint = taskStore.completedDisc(current.id, item)
                        val reusable = checkpoint?.let { saved ->
                            store.file(saved.preparedFileName).takeIf { file ->
                                file.isFile && file.length() > 0L && file.length() == saved.preparedBytes
                            }
                        }

                        val prepared = if (reusable != null) {
                            val validated = runCatching {
                                RomPreparedFileValidator.validate(current, reusable, diagnostics)
                                reusable
                            }.getOrNull()

                            if (validated != null) {
                                diagnostics.info(
                                    "BACKGROUND_MULTIDISC_DISC_REUSED",
                                    "game_id=${current.id} disc=${item.discIndex} file=${validated.name} bytes=${validated.length()}",
                                )
                                onProgress(
                                    AuthorizedSourceDownloader.Progress(
                                        stage = "${item.discLabel} de ${downloadSet.items.size} · Ya preparado",
                                        downloaded = completedBytes + (item.source.expectedSize.takeIf { it > 0L } ?: validated.length()),
                                        total = expectedTotal,
                                        fileName = validated.name,
                                        phase = AuthorizedSourceDownloader.Phase.READY,
                                    ),
                                )
                                validated
                            } else {
                                diagnostics.info(
                                    "BACKGROUND_MULTIDISC_CHECKPOINT_INVALID",
                                    "game_id=${current.id} disc=${item.discIndex} reason=validator_failed",
                                )
                                taskStore.clearDiscCheckpoint(current.id, item.discIndex)
                                runCatching { reusable.delete() }
                                null
                            }
                        } else {
                            if (checkpoint != null) {
                                diagnostics.info(
                                    "BACKGROUND_MULTIDISC_CHECKPOINT_INVALID",
                                    "game_id=${current.id} disc=${item.discIndex} reason=file_missing_or_size_mismatch",
                                )
                                taskStore.clearDiscCheckpoint(current.id, item.discIndex)
                            }
                            null
                        } ?: prepareWithResumeRetry(
                            current,
                            TransferRecoveryScope(task.gameId, item.sourcePlan),
                            downloader,
                            diagnostics,
                        ) { progress ->
                            val aggregateDownloaded = completedBytes + progress.downloaded.coerceAtLeast(0L)
                            onProgress(
                                progress.copy(
                                    stage = "${item.discLabel} de ${downloadSet.items.size} · ${progress.stage}",
                                    downloaded = aggregateDownloaded,
                                    total = expectedTotal,
                                    fileName = item.source.expectedFileName.orEmpty(),
                                ),
                            )
                        }.let { file -> RomArchivePreparer.prepare(current, file, store, diagnostics) }
                            .let { file -> store.promoteDiscForCheckpoint(file, item.discIndex) }
                            .also { newlyPrepared ->
                            onProgress(
                                AuthorizedSourceDownloader.Progress(
                                    stage = "Verificando ${item.discLabel.lowercase()}…",
                                    downloaded = completedBytes + newlyPrepared.length(),
                                    total = expectedTotal,
                                    fileName = newlyPrepared.name,
                                    phase = AuthorizedSourceDownloader.Phase.VERIFYING,
                                ),
                            )
                            RomPreparedFileValidator.validate(current, newlyPrepared, diagnostics)
                            taskStore.markDiscComplete(current.id, item, newlyPrepared)
                            diagnostics.info(
                                "BACKGROUND_MULTIDISC_DISC_CHECKPOINTED",
                                "game_id=${current.id} disc=${item.discIndex} file=${newlyPrepared.name} bytes=${newlyPrepared.length()}",
                            )
                        }

                        preparedDiscs += item to prepared
                        completedBytes += item.source.expectedSize.takeIf { it > 0L } ?: prepared.length()
                    }
                    onProgress(
                        AuthorizedSourceDownloader.Progress(
                            stage = "Guardando discos y lista M3U en tu biblioteca ROMs…",
                            downloaded = completedBytes,
                            total = expectedTotal.takeIf { it > 0L } ?: completedBytes,
                            fileName = downloadSet.playlistName,
                            phase = AuthorizedSourceDownloader.Phase.COPYING,
                        ),
                    )
                    val stored = persistentLibrary.persistSet(
                        game = current,
                        set = downloadSet,
                        preparedDiscs = preparedDiscs,
                        resolvedRegion = task.resolvedRegion,
                    )
                    diagnostics.info(
                        "BACKGROUND_MULTIDISC_SAVED",
                        "game_id=${current.id} discs=${stored.members.size} playlist=${stored.displayName} bytes=${stored.bytes}",
                    )
                    store.deleteAll()
                    return@withContext Result(current, stored)
                }

                var prepared = prepareWithResumeRetry(
                    current,
                    TransferRecoveryScope(task.gameId, task.sourcePlan),
                    downloader,
                    diagnostics,
                    onProgress,
                )
                if (current.systemId == "arcade-fbneo" && task.biosSources.isNotEmpty()) {
                    val downloadedBios = linkedMapOf<String, File>()
                    task.biosSources.forEachIndexed { index, (setName, biosSource) ->
                        onProgress(
                            AuthorizedSourceDownloader.Progress(
                                stage = "Descargando BIOS ${index + 1}/${task.biosSources.size}…",
                                fileName = setName,
                                phase = AuthorizedSourceDownloader.Phase.PREPARING,
                            ),
                        )
                        val biosGame = current.copy(
                            id = "romgi-bios-$setName",
                            title = "$setName BIOS",
                            systemId = "romgi-bios",
                            romSet = null,
                            biosSetsCsv = "",
                            virtualExtension = "zip",
                        )
                        downloadedBios[setName] = prepareWithResumeRetry(
                            biosGame,
                            TransferRecoveryScope(
                                durableTaskGameId = task.gameId,
                                sourcePlan = DownloadSourcePlan.single(
                                    LogicalDownloadIdentity.forTask(biosGame.id, null, biosSource.expectedFileName, biosSource.expectedSize),
                                    biosSource,
                                ),
                            ),
                            downloader,
                            diagnostics,
                            onProgress,
                        )
                    }
                    prepared = FbneoBiosMerger.mergeDownloadedBios(prepared, downloadedBios, diagnostics).file
                }
                if (current.systemId == "arcade-fbneo" && current.biosSets.isNotEmpty()) {
                    val biosResult = FbneoBiosMerger.mergeConfiguredBios(
                        context = appContext,
                        biosTreeUri = preferences.fbneoBiosTreeUri,
                        gameArchive = prepared,
                        requestedSets = current.biosSets,
                        diagnostics = diagnostics,
                    )
                    prepared = biosResult.file
                    if (biosResult.missingSets.isNotEmpty()) {
                        diagnostics.info(
                            "FBNEO_BIOS_NOT_CONFIGURED",
                            "missing=${biosResult.missingSets.joinToString(",")} target=${prepared.name}",
                        )
                    }
                }

                prepared = RomArchivePreparer.prepare(current, prepared, store, diagnostics)
                onProgress(
                    AuthorizedSourceDownloader.Progress(
                        stage = if (prepared.extension.equals("zip", true)) "Verificando ROM…" else "Verificando ROM preparada…",
                        downloaded = prepared.length(),
                        total = prepared.length(),
                        fileName = prepared.name,
                        phase = AuthorizedSourceDownloader.Phase.VERIFYING,
                    ),
                )
                RomPreparedFileValidator.validate(current, prepared, diagnostics)
                onProgress(
                    AuthorizedSourceDownloader.Progress(
                        stage = "Guardando en tu biblioteca ROMs…",
                        downloaded = prepared.length(),
                        total = prepared.length(),
                        fileName = prepared.name,
                        phase = AuthorizedSourceDownloader.Phase.COPYING,
                    ),
                )
                val stored = persistentLibrary.persist(
                    game = current,
                    prepared = prepared,
                    resolvedRegion = task.resolvedRegion,
                    sourceIndexProvenance = task.sourcePlan.sourceIndexProvenance,
                )
                diagnostics.info(
                    "BACKGROUND_DOWNLOAD_SAVED",
                    "game_id=${current.id} file=${stored.displayName} bytes=${stored.bytes}",
                )
                store.deleteAll()
                Result(current, stored)
            } catch (cancelled: CancellationException) {
                diagnostics.info("BACKGROUND_DOWNLOAD_CANCELLED", "game_id=${current.id}")
                throw cancelled
            } catch (error: Throwable) {
                diagnostics.error("BACKGROUND_DOWNLOAD_FAILED", error)
                throw error
            } finally {
                activeDownloader.compareAndSet(downloader, null)
            }
        }

    private suspend fun prepareWithResumeRetry(
        game: GameEntity,
        recoveryScope: TransferRecoveryScope,
        downloader: AuthorizedSourceDownloader,
        diagnostics: DiagnosticLogger.Session,
        onProgress: suspend (AuthorizedSourceDownloader.Progress) -> Unit,
    ): File {
        val sourcePlan = recoveryScope.sourcePlan
        val source = sourcePlan.selectedSource
        var last: Throwable? = null
        var attempt = 0
        var consecutiveNoProgressFailures = 0
        var retryDelayMs = RETRY_DELAYS_MS.first()

        while (true) {
            val beforeBytes = taskStore.get(recoveryScope.durableTaskGameId)?.let(taskStore::partialBytes) ?: 0L
            try {
                if (attempt > 0) {
                    onProgress(
                        AuthorizedSourceDownloader.Progress(
                            stage = "Reconectando - intento ${attempt + 1}",
                            downloaded = beforeBytes,
                            total = source.expectedSize,
                            fileName = source.expectedFileName.orEmpty(),
                            phase = AuthorizedSourceDownloader.Phase.CONNECTING,
                        ),
                    )
                    delay(retryDelayMs)
                }

                return downloader.prepare(
                    game,
                    sourcePlan,
                    taskStore.httpRecoveryAccess(recoveryScope.durableTaskGameId),
                    onProgress,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                last = error

                val retryable = error is IOException ||
                    error.cause is IOException ||
                    error.message?.contains("connection", ignoreCase = true) == true ||
                    error.message?.contains("timed out", ignoreCase = true) == true ||
                    error.message?.contains("reset", ignoreCase = true) == true ||
                    error.message?.contains("abort", ignoreCase = true) == true ||
                    error.message?.contains("socket closed", ignoreCase = true) == true

                if (!retryable) throw error

                val afterBytes =
                    taskStore.get(recoveryScope.durableTaskGameId)?.let(taskStore::partialBytes) ?: beforeBytes

                val gainedBytes =
                    (afterBytes - beforeBytes).coerceAtLeast(0L)

                val meaningfulProgress =
                    source.isHttp && gainedBytes >= MIN_PROGRESS_RESET_BYTES

                if (meaningfulProgress) {
                    consecutiveNoProgressFailures = 0
                    retryDelayMs = RETRY_DELAYS_MS.first()
                } else {
                    consecutiveNoProgressFailures++

                    val delayIndex =
                        (consecutiveNoProgressFailures - 1)
                            .coerceIn(0, RETRY_DELAYS_MS.lastIndex)

                    retryDelayMs = RETRY_DELAYS_MS[delayIndex]
                }

                attempt++

                diagnostics.info(
                    "BACKGROUND_DOWNLOAD_RETRY",
                    "game_id=${game.id} attempt=$attempt error=${error.javaClass.simpleName} " +
                        "before=$beforeBytes after=$afterBytes gained=$gainedBytes " +
                        "meaningful_progress=$meaningfulProgress " +
                        "no_progress_failures=$consecutiveNoProgressFailures",
                )

                val legacyBudgetExhausted =
                    !source.isHttp && attempt >= MAX_NETWORK_ATTEMPTS

                val httpNoProgressBudgetExhausted =
                    source.isHttp &&
                        consecutiveNoProgressFailures >=
                        MAX_CONSECUTIVE_NO_PROGRESS_FAILURES

                if (legacyBudgetExhausted || httpNoProgressBudgetExhausted) {
                    diagnostics.info(
                        "BACKGROUND_DOWNLOAD_RETRY_EXHAUSTED",
                        "game_id=${game.id} attempts=$attempt " +
                            "no_progress_failures=$consecutiveNoProgressFailures " +
                            "partial_bytes=$afterBytes",
                    )
                    throw error
                }
            }
        }

        @Suppress("UNREACHABLE_CODE")
        throw last ?: IllegalStateException("No fue posible preparar la descarga.")
    }

    companion object {
        private const val MAX_NETWORK_ATTEMPTS = 4
        private const val MAX_CONSECUTIVE_NO_PROGRESS_FAILURES = 4
        private const val MIN_PROGRESS_RESET_BYTES = 256L * 1024L
        private val RETRY_DELAYS_MS = longArrayOf(1_500L, 4_000L, 9_000L)
    }
}

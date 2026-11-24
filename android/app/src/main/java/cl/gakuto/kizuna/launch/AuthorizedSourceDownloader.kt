package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import cl.gakuto.kizuna.auth.InternetArchiveAuthManager
import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.DiagnosticLogger
import cl.gakuto.kizuna.util.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.swig.torrent_flags_t
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class AuthorizedSourceDownloader internal constructor(
    private val context: Context,
    private val store: SessionFileStore,
    private val diagnostics: DiagnosticLogger.Session,
    private val networkController: DownloadNetworkController = DownloadNetworkController(),
) {
    private val activeHttpConnection = AtomicReference<HttpURLConnection?>(null)

    fun onNetworkChanged() {
        activeHttpConnection.getAndSet(null)?.disconnect()
        diagnostics.info("DOWNLOAD_NETWORK_CHANGED", "transport=http action=disconnect_for_retry")
    }

    enum class Phase {
        PREPARING,
        CONNECTING,
        METADATA,
        SELECTING_FILE,
        DOWNLOADING,
        VERIFYING,
        COPYING,
        READY,
    }

    data class Progress(
        val stage: String,
        val downloaded: Long = 0L,
        val total: Long = -1L,
        val bytesPerSecond: Long = 0L,
        val peers: Int = 0,
        val fileName: String = "",
        val phase: Phase = Phase.PREPARING,
        val elapsedMillis: Long = 0L,
        val etaSeconds: Long? = null,
        val stalled: Boolean = false,
        val stallMessage: String? = null,
        val resetDownloadedBaseline: Boolean = false,
    )

    suspend fun prepare(
        game: GameEntity,
        source: AuthorizedSource,
        onProgress: suspend (Progress) -> Unit,
    ): File = prepare(
        game,
        DownloadSourcePlan.single(
            LogicalDownloadIdentity.forTask(game.id, null, source.expectedFileName, source.expectedSize),
            source,
        ),
        HttpRecoveryAccess.None,
        onProgress,
    )

    internal suspend fun prepare(
        game: GameEntity,
        sourcePlan: DownloadSourcePlan,
        recoveryAccess: HttpRecoveryAccess,
        onProgress: suspend (Progress) -> Unit,
    ): File {
        val source = sourcePlan.selectedSource
        return when {
        source.isContent -> copyContent(game, source, onProgress)
        source.isFilePath -> copyFilePath(game, source, onProgress)
        source.isHttp -> downloadHttp(game, sourcePlan, recoveryAccess, onProgress)
        source.isMagnet -> downloadTorrent(game, source, onProgress)
        else -> error("La fuente debe ser un archivo del dispositivo, una ruta compartida por iiSU, una URL HTTP/HTTPS o un magnet autorizado.")
        }
    }

    private suspend fun copyFilePath(game: GameEntity, source: AuthorizedSource, onProgress: suspend (Progress) -> Unit): File =
        withContext(Dispatchers.IO) {
            val selected = File(Uri.decode(source.value).trim('\"', '\''))
            require(selected.isFile) { "iiSU entregó una ruta que Android no puede abrir: ${selected.absolutePath}" }
            val expectedArcade = FbneoSupport.expectedArchiveName(game)
            if (expectedArcade != null && !selected.name.equals(expectedArcade, ignoreCase = true)) {
                error("FBNeo espera el ROM set exacto $expectedArcade, pero iiSU entregó ${selected.name}.")
            }
            val target = store.file(expectedArcade ?: selected.name)
            onProgress(Progress("Copiando ROM compartida con iiSU…", 0, selected.length(), fileName = target.name))
            selected.inputStream().buffered(BUFFER_SIZE).use { input ->
                target.outputStream().buffered(BUFFER_SIZE).use { output ->
                    copyWithProgress(input, output, selected.length(), target.name, "Copiando ROM compartida con iiSU…", onProgress)
                }
            }
            require(target.length() > MIN_FILE_SIZE) { "La ROM entregada por iiSU está vacía o incompleta." }
            diagnostics.info("IISU_FILE_SOURCE_READY", "file=${target.name} bytes=${target.length()}")
            target
        }

    private suspend fun copyContent(game: GameEntity, source: AuthorizedSource, onProgress: suspend (Progress) -> Unit): File =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(source.value)
            val selectedName = source.expectedFileName ?: queryName(uri) ?: uri.lastPathSegment ?: "game.rom"
            val expectedArcade = FbneoSupport.expectedArchiveName(game)
            if (expectedArcade != null && !selectedName.equals(expectedArcade, ignoreCase = true)) {
                error("FBNeo espera el ROM set exacto $expectedArcade, pero seleccionaste $selectedName.")
            }
            val displayName = expectedArcade ?: selectedName
            val target = store.file(displayName)
            val total = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
            onProgress(Progress("Copiando archivo propio…", 0, total, fileName = displayName))
            context.contentResolver.openInputStream(uri)?.use { raw ->
                BufferedInputStream(raw, BUFFER_SIZE).use { input ->
                    BufferedOutputStream(target.outputStream(), BUFFER_SIZE).use { output ->
                        copyWithProgress(input, output, total, displayName, "Copiando archivo propio…", onProgress)
                    }
                }
            } ?: error("Android no pudo abrir el archivo seleccionado.")
            require(target.length() > MIN_FILE_SIZE) { "El archivo seleccionado está vacío o incompleto." }
            diagnostics.info("LOCAL_SOURCE_READY", "file=${target.name} bytes=${target.length()}")
            target
        }

    private suspend fun downloadHttp(
        game: GameEntity,
        sourcePlan: DownloadSourcePlan,
        recoveryAccess: HttpRecoveryAccess,
        onProgress: suspend (Progress) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            val candidate = sourcePlan.selectedCandidate
            val source = candidate.source
            val owner = HttpPartialOwner(sourcePlan.logicalIdentity.value, candidate.identity)
            val authManager = InternetArchiveAuthManager(context)
            val sessionAvailable = authManager.isLoggedIn()
            if (source.requiresInternetArchiveAuth) {
                require(sessionAvailable) {
                    "Esta descarga requiere iniciar sesión en Internet Archive desde Configuración."
                }
            }
            val auth = authManager.takeIf { sessionAvailable }
            val expectedArcade = FbneoSupport.expectedArchiveName(game)
            var resetForQuarantinedPartial = recoveryAccess.hasQuarantined(owner)
            var recovery = recoveryAccess.get(owner)?.takeIf { !it.quarantined }
            var resumeTarget = recovery?.let { store.resolveRelative(it.relativePath) }
            if (recovery != null && (resumeTarget?.isFile != true || recovery.representation.ifRangeValue == null)) {
                quarantinePartial(owner, recovery, resumeTarget, recoveryAccess)
                resetForQuarantinedPartial = true
                recovery = null
                resumeTarget = null
            }
            var resumeOffset = resumeTarget?.length()?.coerceAtLeast(0L) ?: 0L
            if (source.expectedSize > 0L && resumeOffset == source.expectedSize && resumeTarget?.isFile == true && recovery != null) {
                diagnostics.info("HTTP_RESUME_ALREADY_COMPLETE", "file=${resumeTarget.name} bytes=$resumeOffset")
                onProgress(
                    Progress(
                        stage = "Descarga ya completa · verificando…",
                        downloaded = resumeOffset,
                        total = source.expectedSize,
                        fileName = resumeTarget.name,
                        phase = Phase.READY,
                        etaSeconds = 0L,
                    ),
                )
                return@withContext requireNotNull(resumeTarget)
            }
            if (source.expectedSize > 0L && resumeOffset > source.expectedSize) {
                recovery?.let { quarantinePartial(owner, it, resumeTarget, recoveryAccess) }
                resetForQuarantinedPartial = recovery != null || resetForQuarantinedPartial
                recovery = null
                resumeTarget = null
                resumeOffset = 0L
            }

            var currentUrl = URL(source.value)
            var connection: HttpURLConnection? = null
            var redirects = 0
            var preferLowAuth = false
            var lowFallbackAttempted = false
            diagnostics.info(
                "HTTP_AUTH_PLAN",
                "host=${currentUrl.host} catalog_requires_auth=${source.requiresInternetArchiveAuth} " +
                    "ia_host=${authManager.isTrustedArchiveUrl(currentUrl.toString())} session_available=$sessionAvailable resume=$resumeOffset",
            )
            try {
                while (true) {
                    connection?.disconnect()
                    connection = ((networkController.current()?.openConnection(currentUrl) ?: currentUrl.openConnection()) as HttpURLConnection).apply {
                        connectTimeout = 25_000
                        readTimeout = 120_000
                        instanceFollowRedirects = false
                        // Match ROMgi's browser-like download request shape. Some large IA/CDN
                        // routes are less reliable with an app-only User-Agent.
                        setRequestProperty("User-Agent", HTTP_USER_AGENT)
                        setRequestProperty("Accept", "*/*")
                        setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                        setRequestProperty("Connection", "keep-alive")
                        setRequestProperty("Accept-Encoding", "identity")
                        if (resumeOffset > 0L) {
                            setRequestProperty("Range", "bytes=$resumeOffset-")
                            recovery?.representation?.ifRangeValue?.let { setRequestProperty("If-Range", it) }
                        }
                        auth?.headersFor(currentUrl.toString(), preferLow = preferLowAuth)?.forEach(::setRequestProperty)
                    }
                    activeHttpConnection.set(connection)
                    connection.connect()
                    val code = connection.responseCode
                    if (code in 300..399) {
                        val location = connection.getHeaderField("Location")
                            ?: error("El servidor respondió HTTP $code sin destino de redirección.")
                        val next = URL(currentUrl, location)
                        redirects++
                        diagnostics.info(
                            "HTTP_REDIRECT",
                            "status=$code host=${currentUrl.host} target_host=${next.host} count=$redirects",
                        )
                        require(redirects <= MAX_HTTP_REDIRECTS) { "La descarga excedió $MAX_HTTP_REDIRECTS redirecciones HTTP." }
                        currentUrl = next
                        continue
                    }
                    val currentIsIa = authManager.isTrustedArchiveUrl(currentUrl.toString())
                    if (code in setOf(401, 403) && currentIsIa && auth != null && !preferLowAuth && !lowFallbackAttempted) {
                        lowFallbackAttempted = true
                        preferLowAuth = true
                        diagnostics.info(
                            "IA_AUTH_LOW_RETRY",
                            "status=$code host=${currentUrl.host} catalog_requires_auth=${source.requiresInternetArchiveAuth}",
                        )
                        continue
                    }
                    break
                }

                val active = requireNotNull(connection)
                val responseCode = active.responseCode
                if (responseCode == 401 || responseCode == 403) {
                    val currentIsIa = authManager.isTrustedArchiveUrl(currentUrl.toString())
                    if (currentIsIa && auth != null) {
                        val failure = auth.recordAuthFailure(responseCode, currentUrl.toString())
                        val validation = auth.validate(countAuthFailure = false)
                        diagnostics.info(
                            "IA_AUTH_REJECTED",
                            "status=$responseCode host=${currentUrl.host} auth_state=${failure.state} failures=${failure.failureCount} " +
                                "validation=$validation low_fallback=$lowFallbackAttempted catalog_requires_auth=${source.requiresInternetArchiveAuth}",
                        )
                        when (validation) {
                            InternetArchiveAuthManager.Validation.OK -> error(
                                "Internet Archive rechazó este recurso (HTTP $responseCode), pero las credenciales de Oz siguen válidas. La fuente ROMgi puede haber cambiado o requerir un permiso específico.",
                            )
                            InternetArchiveAuthManager.Validation.INVALID -> error(
                                "Internet Archive confirmó que la autenticación ya no es válida. Reconecta la sesión desde Configuración.",
                            )
                            InternetArchiveAuthManager.Validation.STALE -> error(
                                "Internet Archive rechazó temporalmente la descarga y no fue posible confirmar la sesión. Oz conservó las credenciales para reintentar o reconectar.",
                            )
                            InternetArchiveAuthManager.Validation.SIGNED_OUT -> error(
                                "Internet Archive requiere una sesión conectada.",
                            )
                        }
                    }
                    if (currentIsIa) {
                        error(
                            "Internet Archive exige autenticación para esta fuente (HTTP $responseCode). " +
                                "Conecta tu cuenta desde Configuración y vuelve a intentar.",
                        )
                    }
                    error("El servidor rechazó la descarga con HTTP $responseCode.")
                }
                if (responseCode in RETRYABLE_HTTP_STATUS) {
                    if (authManager.isTrustedArchiveUrl(currentUrl.toString())) {
                        authManager.recordSourceFailure(responseCode)
                    }
                    diagnostics.info(
                        "HTTP_TRANSIENT_FAILURE",
                        "status=$responseCode host=${currentUrl.host} retryable=true",
                    )
                    // BackgroundRomDownloadEngine owns retry/backoff. Use IOException so its
                    // existing reconnect path handles temporary 5xx/429 responses and preserves
                    // already downloaded bytes when the server supports ranges.
                    throw TransferFailureException(
                        TransferFailure.HttpStatus(responseCode),
                        "El servidor respondió HTTP $responseCode; Oz reintentará la descarga.",
                    )
                }
                require(responseCode in 200..299 || responseCode == 416) { "El servidor respondió HTTP $responseCode." }
                val offeredName = source.expectedFileName
                    ?: contentDispositionName(active.getHeaderField("Content-Disposition"))
                    ?: currentUrl.path.substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: "game.rom"
                if (expectedArcade != null && !offeredName.equals(expectedArcade, ignoreCase = true)) {
                    error("FBNeo espera el ROM set exacto $expectedArcade, pero la fuente ofrece $offeredName.")
                }
                val displayName = expectedArcade ?: offeredName
                val target = resumeTarget ?: store.candidateFile(owner, displayName)
                val evidence = HttpResponseEvidence(
                    status = responseCode,
                    contentRange = active.getHeaderField("Content-Range"),
                    contentLength = active.contentLengthLong,
                    etag = active.getHeaderField("ETag"),
                    lastModified = active.getHeaderField("Last-Modified"),
                    date = active.getHeaderField("Date"),
                    effectiveUrl = safeEffectiveUrl(currentUrl),
                )
                val decision = HttpResumePolicy.decide(
                    requestedOffset = resumeOffset,
                    localLength = resumeTarget?.length() ?: 0L,
                    expectedLength = source.expectedSize,
                    persisted = recovery?.representation,
                    response = evidence,
                )
                if (decision is HttpResumeDecision.AlreadyComplete) {
                    diagnostics.info("HTTP_RESUME_RANGE_COMPLETE", "file=${target.name} bytes=${target.length()}")
                    return@withContext target
                }
                if (decision is HttpResumeDecision.Reject) {
                    recovery?.let { quarantinePartial(owner, it, resumeTarget, recoveryAccess) }
                    diagnostics.info("HTTP_RESUME_REJECTED", "host=${currentUrl.host} status=$responseCode reason=${decision.failure}")
                    throw TransferFailureException(
                        decision.failure,
                        "La respuesta HTTP no permite reutilizar el parcial de forma segura; Oz reiniciará este candidato.",
                    )
                }
                val canAppend = decision is HttpResumeDecision.Append
                val responseLength = active.contentLengthLong.takeIf { it > 0L }
                val total = when {
                    decision is HttpResumeDecision.Append -> decision.total
                    responseLength != null -> responseLength
                    source.expectedSize > 0L -> source.expectedSize
                    else -> -1L
                }
                val resumeWasRequested = resumeOffset > 0L
                val representation = when (decision) {
                    is HttpResumeDecision.Replace -> decision.representation
                    is HttpResumeDecision.Append -> requireNotNull(recovery).representation.copy(
                        totalLength = decision.total,
                        effectiveUrl = safeEffectiveUrl(currentUrl),
                    )
                    else -> error("Decisión HTTP inesperada.")
                }
                val nextRecovery = HttpCandidateRecovery(
                    owner = owner,
                    relativePath = target.relativeTo(store.directory).invariantSeparatorsPath,
                    representation = representation,
                )
                if (decision is HttpResumeDecision.Replace) {
                    if (target.isFile && target.length() > 0L) {
                        recoveryAccess.markUnsafeArtifactsPresent()
                    }
                    prepareHttpReplacementTarget(target)
                }
                recoveryAccess.persist(nextRecovery)
                if (resumeWasRequested && !canAppend) {
                    diagnostics.info("HTTP_RESUME_RESTARTED", "host=${currentUrl.host} file=$displayName previous=$resumeOffset status=$responseCode")
                    resumeOffset = 0L
                } else if (canAppend) {
                    diagnostics.info("HTTP_RESUME_ACCEPTED", "host=${currentUrl.host} file=$displayName offset=$resumeOffset total=$total")
                }
                diagnostics.info("HTTP_SOURCE_BEGIN", "host=${currentUrl.host} file=$displayName expected=$total redirects=$redirects resume=$resumeOffset")
                BufferedInputStream(active.inputStream, BUFFER_SIZE).use { input ->
                    BufferedOutputStream(java.io.FileOutputStream(target, canAppend), BUFFER_SIZE).use { output ->
                        copyWithProgress(
                            input = input,
                            output = output,
                            total = total,
                            fileName = displayName,
                            stage = if (canAppend) "Reanudando desde ${currentUrl.host}" else "Descargando desde ${currentUrl.host}",
                            onProgress = onProgress,
                            initialDownloaded = if (canAppend) resumeOffset else 0L,
                            resetDownloadedBaseline = resumeWasRequested && !canAppend || resetForQuarantinedPartial,
                        )
                    }
                }
                require(target.length() > MIN_FILE_SIZE) { "La descarga está vacía o incompleta." }
                if (total > 0L) {
                    require(target.length() >= total) {
                        "La conexión terminó antes de completar la ROM (${target.length()} de $total bytes)."
                    }
                }
                diagnostics.info("HTTP_SOURCE_READY", "host=${currentUrl.host} file=${target.name} bytes=${target.length()}")
                target
            } finally {
                activeHttpConnection.compareAndSet(connection, null)
                connection?.disconnect()
            }
        }

    private fun quarantinePartial(
        owner: HttpPartialOwner,
        recovery: HttpCandidateRecovery,
        file: File?,
        access: HttpRecoveryAccess,
    ) {
        access.markUnsafeArtifactsPresent()
        access.quarantine(owner)
        if (file?.isFile == true) {
            val quarantine = File(file.parentFile, "${file.name}.unsafe-${UUID.randomUUID()}")
            runCatching { file.renameTo(quarantine) }
        }
        diagnostics.info(
            "HTTP_PARTIAL_QUARANTINED",
            "candidate=${owner.candidateIdentity} path=${recovery.relativePath}",
        )
    }

    private fun safeEffectiveUrl(url: URL): String = URL(url.protocol, url.host, url.port, url.path).toString()

    private suspend fun downloadTorrent(
        game: GameEntity,
        source: AuthorizedSource,
        onProgress: suspend (Progress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val infohash = parseInfoHash(source.value)
        val transferStartedAt = System.currentTimeMillis()
        val temporary = File(context.cacheDir, "oz-torrent-${UUID.randomUUID()}").apply { mkdirs() }
        val manager = SessionManager()
        val torrentSignals = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val alertListener = object : AlertListener {
            private val watched = intArrayOf(
                AlertType.METADATA_RECEIVED.swig(),
                AlertType.PIECE_FINISHED.swig(),
                AlertType.BLOCK_FINISHED.swig(),
                AlertType.STATE_UPDATE.swig(),
                AlertType.STATE_CHANGED.swig(),
                AlertType.TORRENT_FINISHED.swig(),
                AlertType.TORRENT_ERROR.swig(),
            )
            override fun types(): IntArray = watched
            override fun alert(alert: Alert<*>?) {
                torrentSignals.tryEmit(Unit)
            }
        }
        var cleanupScheduled = false
        try {
            manager.addListener(alertListener)
            onProgress(
                Progress(
                    stage = "Iniciando sesión torrent…",
                    fileName = source.expectedFileName.orEmpty(),
                    phase = Phase.CONNECTING,
                    elapsedMillis = elapsedSince(transferStartedAt),
                ),
            )
            val settings = buildSettings()
            manager.start(SessionParams(settings))
            manager.applySettings(settings)
            manager.download(source.value, temporary, torrent_flags_t())
            diagnostics.info("TORRENT_STARTED", "infohash=$infohash")

            val handle = try {
                withTimeout(METADATA_TIMEOUT_MS) {
                    waitForMetadata(manager, infohash, source.expectedFileName.orEmpty(), transferStartedAt, torrentSignals, onProgress)
                }
            } catch (_: TimeoutCancellationException) {
                error(
                    "El torrent no entregó metadatos en 45 segundos. Oz detuvo la espera para evitar bloqueos de varios minutos; " +
                        "reintenta o usa una fuente HTTPS/Internet Archive.",
                )
            }
            val info = requireNotNull(handle.torrentFile()) { "El torrent no entregó metadatos." }
            val expectedName = FbneoSupport.expectedArchiveName(game) ?: source.expectedFileName
            val index = chooseFileIndex(handle, game, expectedName, source)
            val relativePath = info.files().filePath(index)
            val size = info.files().fileSize(index)
            require(size > 0L) { "El archivo seleccionado dentro del torrent está vacío." }
            diagnostics.info("TORRENT_FILE_SELECTED", "index=$index path=$relativePath bytes=$size")

            for (i in 0 until info.numFiles()) {
                handle.filePriority(i, if (i == index) Priority.DEFAULT else Priority.IGNORE)
            }
            diagnostics.info(
                "TORRENT_PRIORITIES_APPLIED",
                "target=$index active=1 ignored=${(info.numFiles() - 1).coerceAtLeast(0)}",
            )
            handle.resume()
            val sourceFile = File(temporary, relativePath)
            withTimeout(DOWNLOAD_TIMEOUT_MS) {
                var lastBytes = -1L
                var stagnantSince = System.currentTimeMillis()
                while (true) {
                    coroutineContext.ensureActive()
                    val status = handle.status()
                    val perFile = runCatching { handle.fileProgress() }.getOrNull()
                    val downloaded = perFile?.getOrNull(index) ?: 0L
                    if (downloaded != lastBytes) {
                        lastBytes = downloaded
                        stagnantSince = System.currentTimeMillis()
                    }
                    val rate = status.downloadRate().toLong().coerceAtLeast(0L)
                    val stagnantMillis = System.currentTimeMillis() - stagnantSince
                    val isComplete = downloaded >= size
                    onProgress(
                        Progress(
                            stage = when {
                                isComplete -> "Verificando archivo descargado…"
                                status.numPeers() == 0 -> "Buscando pares para iniciar la descarga…"
                                else -> "Descargando archivo seleccionado…"
                            },
                            downloaded = downloaded,
                            total = size,
                            bytesPerSecond = rate,
                            peers = status.numPeers(),
                            fileName = relativePath.substringAfterLast('/'),
                            phase = if (isComplete) Phase.VERIFYING else Phase.DOWNLOADING,
                            elapsedMillis = elapsedSince(transferStartedAt),
                            etaSeconds = estimateEtaSeconds(downloaded, size, rate),
                            stalled = !isComplete && stagnantMillis >= STALL_WARNING_MS,
                            stallMessage = if (!isComplete && stagnantMillis >= STALL_WARNING_MS) {
                                "La descarga no ha recibido datos nuevos durante ${formatMinutes(stagnantMillis)}. Puedes reintentar o elegir un archivo propio."
                            } else null,
                        ),
                    )
                    if (downloaded >= size && sourceFile.isFile && sourceFile.length() >= size) break
                    if (System.currentTimeMillis() - stagnantSince > STALL_TIMEOUT_MS) {
                        error("La fuente torrent no avanzó durante 15 minutos.")
                    }
                    manager.postTorrentUpdates()
                    withTimeoutOrNull(650L) { torrentSignals.first() }
                }
            }

            val target = store.file(FbneoSupport.expectedArchiveName(game) ?: relativePath.substringAfterLast('/').substringAfterLast('\\'))
            onProgress(
                Progress(
                    stage = "Copiando ROM a la sesión temporal…",
                    downloaded = size,
                    total = size,
                    fileName = target.name,
                    phase = Phase.COPYING,
                    elapsedMillis = elapsedSince(transferStartedAt),
                    etaSeconds = 0L,
                ),
            )
            sourceFile.inputStream().buffered(BUFFER_SIZE).use { input ->
                target.outputStream().buffered(BUFFER_SIZE).use { output -> input.copyTo(output, BUFFER_SIZE) }
            }
            require(target.length() > MIN_FILE_SIZE) { "El archivo torrent copiado está incompleto." }
            scheduleCleanup(manager, temporary)
            cleanupScheduled = true
            target
        } finally {
            runCatching { manager.removeListener(alertListener) }
            if (!cleanupScheduled) scheduleCleanup(manager, temporary)
        }
    }

    private suspend fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        total: Long,
        fileName: String,
        stage: String,
        onProgress: suspend (Progress) -> Unit,
        initialDownloaded: Long = 0L,
        resetDownloadedBaseline: Boolean = false,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        val started = System.nanoTime()
        var downloaded = initialDownloaded.coerceAtLeast(0L)
        var transferredThisRun = 0L
        var lastUpdate = 0L
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            downloaded += count
            transferredThisRun += count
            val now = System.nanoTime()
            if (now - lastUpdate >= 500_000_000L) {
                lastUpdate = now
                val elapsed = max(0.001, (now - started) / 1_000_000_000.0)
                val rate = (transferredThisRun / elapsed).toLong()
                onProgress(
                    Progress(
                        stage = stage,
                        downloaded = downloaded,
                        total = total,
                        bytesPerSecond = rate,
                        fileName = fileName,
                        phase = if (stage.startsWith("Copiando", true)) Phase.COPYING else Phase.DOWNLOADING,
                        elapsedMillis = ((now - started) / 1_000_000L).coerceAtLeast(0L),
                        etaSeconds = estimateEtaSeconds(downloaded, total, rate),
                        resetDownloadedBaseline = resetDownloadedBaseline,
                    ),
                )
            }
        }
        output.flush()
        onProgress(
            Progress(
                stage = "Archivo preparado",
                downloaded = downloaded,
                total = if (total > 0) total else downloaded,
                fileName = fileName,
                phase = Phase.READY,
                elapsedMillis = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L),
                etaSeconds = 0L,
                resetDownloadedBaseline = resetDownloadedBaseline,
            ),
        )
    }

    private suspend fun waitForMetadata(
        manager: SessionManager,
        infohash: String,
        expectedFileName: String,
        transferStartedAt: Long,
        torrentSignals: MutableSharedFlow<Unit>,
        onProgress: suspend (Progress) -> Unit,
    ): TorrentHandle {
        val hash = Sha1Hash.parseHex(infohash)
        while (true) {
            coroutineContext.ensureActive()
            val handle = manager.find(hash)
            val elapsed = elapsedSince(transferStartedAt)
            if (handle == null) {
                val connectingSlow = elapsed >= METADATA_WARNING_MS
                onProgress(
                    Progress(
                        stage = "Contactando la red torrent…",
                        fileName = expectedFileName,
                        phase = Phase.CONNECTING,
                        elapsedMillis = elapsed,
                        stalled = connectingSlow,
                        stallMessage = if (connectingSlow) {
                            "La red torrent todavía no responde. Puedes reintentar o elegir un archivo propio."
                        } else null,
                    ),
                )
            } else {
                val status = handle.status()
                val ready = handle.torrentFile() != null
                val metadataSlow = !ready && elapsed >= METADATA_WARNING_MS
                onProgress(
                    Progress(
                        stage = if (ready) "Seleccionando el archivo correcto…" else "Obteniendo metadatos del torrent…",
                        bytesPerSecond = status.downloadRate().toLong().coerceAtLeast(0L),
                        peers = status.numPeers(),
                        fileName = expectedFileName,
                        phase = if (ready) Phase.SELECTING_FILE else Phase.METADATA,
                        elapsedMillis = elapsed,
                        stalled = metadataSlow,
                        stallMessage = if (metadataSlow) {
                            "Los metadatos están tardando más de lo normal. La fuente puede tener pocos pares; puedes reintentar o elegir un archivo propio."
                        } else null,
                    ),
                )
                if (ready) return handle
            }
            manager.postTorrentUpdates()
            withTimeoutOrNull(650L) { torrentSignals.first() }
        }
    }

    private fun chooseFileIndex(handle: TorrentHandle, game: GameEntity, expected: String?, source: AuthorizedSource): Int {
        val info = requireNotNull(handle.torrentFile())
        source.torrentFileIndex?.takeIf { it in 0 until info.numFiles() }?.let { index ->
            val actualPath = info.files().filePath(index)
            val expectedPath = source.torrentFilePath
            if (expectedPath.isNullOrBlank() || actualPath.equals(expectedPath, true) ||
                actualPath.substringAfterLast('/').substringAfterLast('\\').equals(expectedPath.substringAfterLast('/').substringAfterLast('\\'), true)
            ) return index
        }
        source.torrentFilePath?.takeIf { it.isNotBlank() }?.let { wantedPath ->
            for (i in 0 until info.numFiles()) {
                val actualPath = info.files().filePath(i)
                if (actualPath.equals(wantedPath, true) ||
                    actualPath.substringAfterLast('/').substringAfterLast('\\').equals(wantedPath.substringAfterLast('/').substringAfterLast('\\'), true)
                ) return i
            }
        }
        if (!expected.isNullOrBlank()) {
            for (i in 0 until info.numFiles()) {
                val path = info.files().filePath(i)
                if (path.equals(expected, true) || path.substringAfterLast('/').substringAfterLast('\\').equals(expected, true)) return i
            }
            error("El torrent no contiene el archivo exacto $expected.")
        }
        if (info.numFiles() == 1) return 0

        val wanted = TextNormalizer.normalize(game.title).replace(Regex("[^a-z0-9]+"), "")
        val preferredExtension = game.virtualExtension.lowercase().removePrefix(".")
        val candidates = (0 until info.numFiles()).map { index ->
            val path = info.files().filePath(index)
            val base = path.substringAfterLast('/').substringAfterLast('\\')
            val stem = TextNormalizer.normalize(base.substringBeforeLast('.')).replace(Regex("[^a-z0-9]+"), "")
            val extension = base.substringAfterLast('.', "").lowercase()
            var score = 0
            if (extension == preferredExtension) score += 80
            if (extension in ROM_EXTENSIONS) score += 30
            if (stem == wanted) score += 200
            else if (stem.contains(wanted) || wanted.contains(stem)) score += 100
            if (base.contains("readme", true) || base.contains("manual", true) || base.contains("bios", true)) score -= 150
            Triple(index, score, info.files().fileSize(index))
        }.filter { it.third > MIN_FILE_SIZE }
        val winner = candidates.maxWithOrNull(compareBy<Triple<Int, Int, Long>> { it.second }.thenBy { it.third })
            ?: error("No se encontró un archivo jugable dentro del torrent.")
        require(winner.second >= 30) {
            "El torrent contiene varios archivos y no fue posible identificar el juego de forma segura. Configura el nombre exacto del archivo."
        }
        return winner.first
    }

    private fun elapsedSince(startedAt: Long): Long =
        (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

    private fun estimateEtaSeconds(downloaded: Long, total: Long, bytesPerSecond: Long): Long? {
        if (total <= 0L || bytesPerSecond <= 0L || downloaded >= total) return if (downloaded >= total && total > 0L) 0L else null
        return ((total - downloaded) / bytesPerSecond).coerceAtLeast(0L)
    }

    private fun formatMinutes(milliseconds: Long): String {
        val seconds = (milliseconds / 1_000L).coerceAtLeast(0L)
        val minutes = seconds / 60L
        val remainder = seconds % 60L
        return if (minutes > 0L) "${minutes} min ${remainder} s" else "${remainder} s"
    }

    private fun parseInfoHash(magnet: String): String = MagnetInfoHashParser.parse(magnet)

    private fun buildSettings(): SettingsPack = SettingsPack()
        .connectionsLimit(180)
        .uploadRateLimit(0)
        .downloadRateLimit(0)
        .apply {
            setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.enable_dht.swigValue(), true)
            setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.enable_upnp.swigValue(), false)
            setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.enable_natpmp.swigValue(), false)
            setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.enable_lsd.swigValue(), false)
            setString(
                org.libtorrent4j.swig.settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                "router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881,dht.libtorrent.org:25401",
            )
            setString(org.libtorrent4j.swig.settings_pack.string_types.listen_interfaces.swigValue(), "0.0.0.0:0")
        }

    private fun scheduleCleanup(manager: SessionManager, directory: File) {
        Thread({
            runCatching { manager.pause() }
            runCatching { manager.stop() }
            repeat(12) {
                if (!directory.exists() || runCatching { directory.deleteRecursively() }.getOrDefault(false)) return@Thread
                Thread.sleep(250L)
            }
        }, "oz-torrent-cleanup").apply { isDaemon = true; start() }
    }

    private fun queryName(uri: Uri): String? = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

    private fun contentDispositionName(value: String?): String? = value
        ?.substringAfter("filename=", "")
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() }

    companion object {
        private const val BUFFER_SIZE = 256 * 1024
        private const val MAX_HTTP_REDIRECTS = 6
        private const val HTTP_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 OzRetroRom/0.6.0-beta.4"
        private val RETRYABLE_HTTP_STATUS = setOf(408, 425, 429, 500, 502, 503, 504)
        private const val MIN_FILE_SIZE = 4_096L
        private const val METADATA_TIMEOUT_MS = 45L * 1000L
        private const val DOWNLOAD_TIMEOUT_MS = 6L * 60L * 60L * 1000L
        private const val STALL_WARNING_MS = 60L * 1000L
        private const val METADATA_WARNING_MS = 12L * 1000L
        private const val STALL_TIMEOUT_MS = 15L * 60L * 1000L
        private val ROM_EXTENSIONS = setOf(
            "zip", "7z", "chd", "iso", "bin", "cue", "sfc", "smc", "nes", "gb", "gbc", "gba",
            "z64", "n64", "v64", "md", "gen", "32x", "gg", "pce", "a26", "a52", "a78", "3ds", "cia",
        )
    }
}

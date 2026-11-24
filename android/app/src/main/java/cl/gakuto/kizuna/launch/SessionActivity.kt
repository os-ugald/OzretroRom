package cl.gakuto.kizuna.launch

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentDialog
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.auth.InternetArchiveAuthManager
import cl.gakuto.kizuna.catalog.CatalogProgress
import cl.gakuto.kizuna.catalog.ResolvedSource
import cl.gakuto.kizuna.catalog.RomgiCatalog
import cl.gakuto.kizuna.catalog.RomgiDownloadPlan
import cl.gakuto.kizuna.catalog.RomgiEditionResolution
import cl.gakuto.kizuna.catalog.sourceindex.SourceIndexCatalogs
import cl.gakuto.kizuna.catalog.sourceindex.UnresolvedGameSourceRoute
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.data.GameDescriptor
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.EngineMode
import cl.gakuto.kizuna.databinding.ActivitySessionBinding
import cl.gakuto.kizuna.ui.EmulatorSettingsActivity
import cl.gakuto.kizuna.ui.compose.RetroPreparationPanel
import cl.gakuto.kizuna.ui.compose.RetroPreparationViewModel
import cl.gakuto.kizuna.ui.compose.RomgiEditionPicker
import cl.gakuto.kizuna.ui.compose.theme.OzTheme
import cl.gakuto.kizuna.util.ArtworkLoader
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters
import cl.gakuto.kizuna.util.DiagnosticLogger
import cl.gakuto.kizuna.util.TextNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume

class SessionActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySessionBinding
    private lateinit var preferences: AppPreferences
    private lateinit var diagnostics: DiagnosticLogger.Session
    private lateinit var store: SessionFileStore
    private lateinit var publicStore: PublicSessionStore
    private lateinit var retroArchManager: RetroArchManager
    private lateinit var externalEmulatorManager: ExternalEmulatorManager
    private lateinit var persistentLibrary: PersistentRomLibrary
    private val retroPreparationViewModel: RetroPreparationViewModel by viewModels()
    private var game: GameEntity? = null
    private var job: Job? = null
    private var emulatorLaunched = false
    private var emulatorCoveredActivity = false
    private var finishingSession = false
    private var waitingForRetroArchInstall = false
    private var pendingRetroArchGame: GameEntity? = null
    private var pendingRetroArchEntry: PublicSessionStore.Entry? = null
    private var pendingCoreSideload: PendingCoreSideload? = null
    private var coreSideloadCoveredActivity = false
    private var retryAction: (() -> Unit)? = null
    private var currentSourceLabel: String = "Preparando"
    private var lastLoggedPhase: AuthorizedSourceDownloader.Phase? = null
    private var pendingPersistentDownload: PendingPersistentDownload? = null
    private var pendingBackgroundSchedule: PendingBackgroundSchedule? = null
    private var backgroundMonitorJob: Job? = null
    private var activityVisible = false
    private var lastBackgroundTaskHandled: String? = null
    private var returnToExternalLauncher = false

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingBackgroundSchedule
        pendingBackgroundSchedule = null
        if (!granted) {
            Toast.makeText(
                this,
                "La descarga continuará, pero Android puede ocultar su progreso del panel de notificaciones.",
                Toast.LENGTH_LONG,
            ).show()
        }
        pending?.let { scheduleBackgroundDownload(it.game, it.source, it.biosSources, it.resolvedRegion, it.downloadSet, it.sourcePlan) }
    }

    private val sourcePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val current = game ?: return@registerForActivityResult
        persist(uri)
        val name = queryName(uri)
        val source = AuthorizedSource(uri.toString(), name, sourceLabel = "Archivo propio")
        preferences.setAuthorizedSource(current.id, source)
        replaceCurrentAttempt { begin(current, source) }
    }

    private val romLibraryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val pending = pendingPersistentDownload
        pendingPersistentDownload = null
        if (uri == null) {
            binding.statusText.text = "Descarga cancelada"
            binding.helperText.text = "Oz necesita una carpeta ROMs persistente para guardar las descargas. Puedes configurarla después en Configuración."
            binding.pickRomButton.visibility = View.VISIBLE
            return@registerForActivityResult
        }
        persistTree(uri)
        preferences.globalRomTreeUri = uri
        Toast.makeText(this, "Biblioteca ROMs guardada. Las descargas nuevas se conservarán aquí.", Toast.LENGTH_LONG).show()
        if (pending != null) begin(pending.game, pending.source, pending.biosSources, pending.resolvedRegion, pending.downloadSet, pending.sourcePlan)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        preferences = AppPreferences(this)
        returnToExternalLauncher = isExternalBridgeLaunch(intent)
        applySessionOrientation()
        binding = ActivitySessionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        retroArchManager = RetroArchManager(this)
        externalEmulatorManager = ExternalEmulatorManager(this)
        persistentLibrary = PersistentRomLibrary(this, preferences, (application as OzApplication).database)
        val sessionId = UUID.randomUUID().toString()
        diagnostics = DiagnosticLogger.startSession(this, sessionId)
        store = SessionFileStore(this, sessionId)
        publicStore = PublicSessionStore(this, sessionId)
        diagnostics.info(
            "SESSION_ORIGIN",
            if (returnToExternalLauncher) "iiSU bridge" else "Oz",
        )
        binding.preparationCompose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.preparationCompose.setContent {
            OzTheme {
                val preparation by retroPreparationViewModel.uiState.collectAsStateWithLifecycle()
                RetroPreparationPanel(preparation)
            }
        }

        binding.cancelButton.setOnClickListener { cancelAndFinish() }
        binding.pauseDownloadButton.setOnClickListener { pauseBackgroundDownload() }
        binding.retryButton.setOnClickListener { retryCurrentAttempt() }
        binding.pickRomButton.setOnClickListener { sourcePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
        binding.deletePartialButton.setOnClickListener { deleteCurrentPartialDownload() }
        resetProgressUi("Preparando")

        lifecycleScope.launch {
            val request = runCatching { resolveLaunchRequest() }
                .getOrElse { error ->
                    diagnostics.error("SESSION_REQUEST_REJECTED", error)
                    showFatal("Solicitud rechazada", error.message ?: "La solicitud de juego no es válida.")
                    return@launch
                }
            val loaded = request?.gameId?.let { (application as OzApplication).database.gameDao().byId(it) }
            if (loaded == null) {
                showFatal("Descriptor inválido", "No se encontró el juego solicitado en la base local.")
                return@launch
            }
            game = loaded
            render(
                runCatching {
                    cl.gakuto.kizuna.data.LibraryRepository((application as OzApplication).database)
                        .artworkVariantFor(loaded)
                }.getOrDefault(loaded),
            )
            val patchedPath = intent.getStringExtra(EXTRA_PATCHED_ROM_PATH)?.trim().orEmpty()
            if (patchedPath.isNotBlank()) {
                val safeRoot = File(filesDir, "patched_roms").canonicalFile
                val candidateFile = File(patchedPath).canonicalFile
                if (candidateFile.path.startsWith(safeRoot.path + File.separator) && candidateFile.isFile && candidateFile.canRead()) {
                    showSimpleStage("Preparando versión traducida [ES]…", "Parche local [ES]", candidateFile.name)
                    runCatching {
                        val mirrored = publicStore.publish(candidateFile)
                        launchEmulator(loaded, mirrored)
                    }.onFailure { error ->
                        diagnostics.error("PATCHED_ROM_LAUNCH_FAILED", error)
                        showFatal("No pude abrir la ROM parcheada", error.message ?: error.javaClass.simpleName)
                    }
                    return@launch
                }
            }
            val background = RomDownloadTaskStore(this@SessionActivity).get(loaded.id)
            if (background?.isActive == true) {
                retryAction = { begin(loaded, background.source, background.biosSources, background.resolvedRegion, background.downloadSet, background.sourcePlan) }
                attachBackgroundDownload(loaded, background)
                return@launch
            }
            if (background?.status == RomDownloadStatus.FAILED) {
                retryAction = { begin(loaded, background.source, background.biosSources, background.resolvedRegion, background.downloadSet, background.sourcePlan) }
                showBackgroundFailure(loaded, background)
                return@launch
            }
            if (background?.status == RomDownloadStatus.CANCELLED && RomDownloadTaskStore(this@SessionActivity).hasPartial(loaded.id)) {
                retryAction = { begin(loaded, background.source, background.biosSources, background.resolvedRegion, background.downloadSet, background.sourcePlan) }
                restoreBackgroundDownloadUi(loaded)
                return@launch
            }
            showSimpleStage("Buscando biblioteca local o ROMgi…", "Biblioteca y ROMgi")
            val incoming = request.source?.takeIf { candidate ->
                !candidate.isFilePath || File(Uri.decode(candidate.value).trim('\"', '\'')).canRead()
            }
            // A real ROM persisted by Oz is the source of truth. Older iiSU bridge revisions could
            // accidentally save a tiny virtual descriptor as an authorized ROM source; if that
            // stale preference survives an update, RetroArch may receive JSON bytes with a .sfc,
            // .gba, etc. extension and the native core can crash inside core_load_game().
            val downloaded = persistentLibrary.resolve(loaded)
            val remembered = preferences.authorizedSource(loaded.id)?.let { saved ->
                if (isOzVirtualDescriptorReference(saved.value)) {
                    preferences.setAuthorizedSource(loaded.id, null)
                    diagnostics.info(
                        "IISU_STALE_DESCRIPTOR_SOURCE_CLEARED",
                        "game_id=${loaded.id} source=${saved.expectedFileName.orEmpty()}",
                    )
                    null
                } else {
                    saved
                }
            }
            if (incoming == null && downloaded?.isMultiDisc == true) {
                runCatching { launchStoredMultiDisc(loaded, downloaded, fresh = false) }
                    .onFailure { error ->
                        diagnostics.error("MULTIDISC_LIBRARY_LAUNCH_FAILED", error)
                        showFatal("No pude abrir el juego multidisco", error.message ?: error.javaClass.simpleName)
                    }
                return@launch
            }
            val legacyLibrarySource = if (LocalRomIdentityPolicy.allowsLegacyLibraryScan(loaded)) {
                RomLibraryResolver(this@SessionActivity, preferences).resolve(loaded)
            } else {
                diagnostics.info(
                    "LOCAL_LIBRARY_LEGACY_FALLBACK_SKIPPED",
                    "projection_id=${loaded.id} source_index_game_id=${SourceIndexCatalogs.forProjection(loaded)?.sourceGameId(loaded.id).orEmpty()} " +
                        "mechanism=STRONG_GAME_ID_REQUIRED",
                )
                null
            }
            val source = incoming
                ?: downloaded?.asSource()
                ?: remembered
                ?: legacyLibrarySource
            when {
                source != null -> {
                    if (incoming != null && !isOzVirtualDescriptorReference(incoming.value)) {
                        preferences.setAuthorizedSource(loaded.id, incoming)
                    }
                    begin(loaded, source)
                }
                else -> when (SourceIndexCatalogs.unresolvedSourceRoute(
                    loaded,
                    preferences.romgiAutoEnabled && GameDescriptor.supports(loaded.systemId),
                )) {
                    UnresolvedGameSourceRoute.SOURCE_INDEX -> beginSourceIndexGame(loaded)
                    UnresolvedGameSourceRoute.ROMGI -> beginRomgi(loaded)
                    UnresolvedGameSourceRoute.SOURCE_REQUIRED -> showSourceRequired()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activityVisible = true
        game?.let(::restoreBackgroundDownloadUi)
    }

    override fun onStop() {
        activityVisible = false
        if (!finishingSession) {
            if (pendingCoreSideload != null) coreSideloadCoveredActivity = true
            else if (emulatorLaunched) emulatorCoveredActivity = true
        }
        super.onStop()
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        returnToExternalLauncher = isExternalBridgeLaunch(newIntent)
        applySessionOrientation()
        val requestedGameId = SessionLaunchValidator.validatedGameId(newIntent.getStringExtra(EXTRA_GAME_ID))
        val current = game
        if (current != null && requestedGameId == current.id) {
            restoreBackgroundDownloadUi(current)
        } else if (requestedGameId != null) {
            recreate()
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingCoreSideload != null && coreSideloadCoveredActivity && !finishingSession) {
            coreSideloadCoveredActivity = false
            verifyPendingCoreSideload()
            return
        }
        if (emulatorLaunched && emulatorCoveredActivity && !finishingSession) {
            finishingSession = true
            lifecycleScope.launch {
                if (preferences.deleteAfterPlay) withContext(Dispatchers.IO) { store.deleteAll(); publicStore.deleteAll() }
                finishSessionToOrigin()
            }
            return
        }
        resumePendingRetroArchInstallIfReady()
    }

    private fun resumePendingRetroArchInstallIfReady() {
        if (!waitingForRetroArchInstall || emulatorLaunched || finishingSession) return
        val current = pendingRetroArchGame ?: return
        val entry = pendingRetroArchEntry ?: return
        val packageName = retroArchManager.preferredInstalledPackage(preferences.retroArchPackage)
        if (packageName == null) {
            binding.statusText.text = "Esperando instalación de RetroArch…"
            binding.fileInfoText.text = "Instálalo desde la página oficial y vuelve a Oz RetroRom."
            return
        }
        waitingForRetroArchInstall = false
        pendingRetroArchGame = null
        pendingRetroArchEntry = null
        preferences.retroArchPackage = packageName
        lifecycleScope.launch {
            runCatching { launchEmulator(current, entry) }
                .onFailure { error ->
                    diagnostics.error("RETROARCH_RESUME_AFTER_INSTALL_FAILED", error)
                    showFatal("No pude continuar con RetroArch", error.message ?: error.javaClass.simpleName)
                }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        if (isFinishing && !emulatorLaunched) { store.deleteAll(); publicStore.deleteAll() }
        super.onDestroy()
    }

    private fun render(current: GameEntity) {
        binding.sessionTitle.text = CatalogFilters.displayTitle(current.title)
        binding.platformBadge.text = current.platform
        ArtworkLoader.load(
            this,
            binding.sessionArtwork,
            ArtworkResolver.candidates(this, preferences, current),
            R.drawable.ic_download_disc_placeholder,
        )
    }

    private fun showSourceRequired() {
        showSimpleStage("No encontré una fuente automática", "Biblioteca local")
        binding.helperText.text = if (preferences.romgiAutoEnabled) {
            "ROMgi no ofrece una ruta compatible para este sistema. Puedes usar una ROM propia."
        } else {
            "La descarga automática ROMgi está desactivada en Configuración."
        }
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0
        binding.progressPercentText.text = "0%"
        binding.pickRomButton.visibility = View.VISIBLE
    }

    private fun beginRomgi(current: GameEntity) {
        if (job?.isActive == true) return
        retryAction = { beginRomgi(current) }
        currentSourceLabel = "ROMgi"
        resetProgressUi(currentSourceLabel)
        binding.pickRomButton.visibility = View.GONE
        binding.cancelButton.text = "Cancelar"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        job = lifecycleScope.launch {
            try {
                if (preferences.romgiWifiOnly && !isUnmeteredWifi()) {
                    error("La descarga ROMgi está configurada solo para Wi-Fi. Conéctate a una red Wi-Fi o cambia la opción en Configuración.")
                }
                val catalog = RomgiCatalog(
                    context = this@SessionActivity,
                    allowTorrents = preferences.romgiTorrentsEnabled,
                    preferredRegion = preferences.preferredRegion,
                    internetArchiveAuthenticated = InternetArchiveAuthManager(this@SessionActivity).isLoggedIn(),
                )
                diagnostics.info(
                    "ROMGI_BEGIN",
                    "game_id=${current.id} torrents=${preferences.romgiTorrentsEnabled} region=${preferences.preferredRegion}",
                )
                catalog.ensureReady(::updateCatalogProgress)
                showSimpleStage("Buscando una versión compatible en ROMgi…", "ROMgi", current.title)
                val resolution = catalog.resolveEditions(GameDescriptor.fromEntity(current))
                val plan = if (resolution.requiresConfirmation) {
                    chooseRomgiEdition(resolution) ?: run {
                        diagnostics.info("ROMGI_SELECTION_CANCELLED", "game_id=${current.id}")
                        showSimpleStage("Descarga cancelada", "ROMgi", current.title)
                        binding.helperText.text = "No se descargó ninguna versión. Puedes volver a intentarlo o elegir un archivo propio."
                        binding.pickRomButton.visibility = View.VISIBLE
                        job = null
                        return@launch
                    }
                } else {
                    resolution.recommended.also { selected ->
                        showSimpleStage(
                            if (selected.isMultiDisc) "Edición completa seleccionada · iniciando ${selected.discCount} discos…"
                            else "Versión compatible seleccionada · iniciando descarga…",
                            "ROMgi · ${selected.region}",
                            selected.editionTitle,
                        )
                    }
                }
                (application as OzApplication).database.gameDao().markRomgiAvailability(current.id, true)
                val dependencies = mutableListOf<Pair<String, ResolvedSource>>()
                if (current.systemId == "arcade-fbneo") {
                    current.biosSets.forEach { setName ->
                        try {
                            dependencies += setName to catalog.resolve(GameDescriptor.bios(setName))
                        } catch (error: Throwable) {
                            diagnostics.info("ROMGI_BIOS_NOT_RESOLVED", "bios=$setName error=${error.message}")
                        }
                    }
                }
                diagnostics.info(
                    "ROMGI_SOURCE_RESOLVED",
                    "edition=${plan.editionTitle} region=${plan.region} format=${plan.format} " +
                        "discs=${plan.discCount} files=${plan.discs.joinToString(",") { it.source.fileName }} " +
                        "torrent=${plan.usesTorrent} dependencies=${dependencies.size}",
                )
                val mainSource = plan.discs.first().source.toAuthorizedSource()
                val downloadSet = plan.toDownloadSet()
                val biosSources = dependencies.map { (name, resolved) -> name to resolved.toAuthorizedSource() }
                job = null
                begin(current, mainSource, biosSources, plan.region, downloadSet)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                diagnostics.error("ROMGI_FAILED", error)
                showFatal("No pude cargar el juego desde ROMgi", error.message ?: error.javaClass.simpleName)
            } finally {
                if (job?.isActive != true) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun beginSourceIndexGame(current: GameEntity) {
        if (job?.isActive == true) return
        retryAction = { beginSourceIndexGame(current) }
        showSimpleStage("Resolviendo versión autorizada…", "Oz Source Index", current.title)
        job = lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    requireNotNull(SourceIndexCatalogs.forProjection(current)).resolve(
                        context = this@SessionActivity,
                        selectedGame = current,
                        preferredRegion = preferences.preferredRegion,
                        releaseId = intent.getStringExtra(EXTRA_SOURCE_INDEX_RELEASE_ID),
                        candidateId = intent.getStringExtra(EXTRA_SOURCE_INDEX_CANDIDATE_ID),
                    )
                }
            }.onSuccess { selection ->
                diagnostics.info(
                    "SOURCE_INDEX_CANDIDATE_SELECTED",
                    "game=${selection.game.gameId} release=${selection.release.releaseId} " +
                        "candidate=${selection.candidate.candidateId} provider=${selection.candidate.provider} " +
                        "transport=torrent infohash=${selection.candidate.infoHash} " +
                        "file_index=${selection.candidate.fileIndex} path=${selection.candidate.exactFullPath}",
                )
                job = null
                begin(
                    current = current,
                    source = selection.sourcePlan.selectedSource,
                    resolvedRegion = selection.release.region,
                    sourcePlan = selection.sourcePlan,
                )
            }.onFailure { error ->
                diagnostics.error("SOURCE_INDEX_RESOLUTION_FAILED", error)
                showFatal("No pude resolver la fuente ${current.platform}", error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private suspend fun chooseRomgiEdition(resolution: RomgiEditionResolution): RomgiDownloadPlan? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var completed = false
                val dialog = ComponentDialog(this@SessionActivity)

                fun finish(value: RomgiDownloadPlan?) {
                    if (completed) return
                    completed = true
                    if (continuation.isActive) continuation.resume(value)
                    if (dialog.isShowing) dialog.dismiss()
                }
                dialog.setContentView(
                    ComposeView(this@SessionActivity).apply {
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent {
                            OzTheme {
                                RomgiEditionPicker(
                                    resolution = resolution,
                                    onConfirm = { finish(it) },
                                    onCancel = { finish(null) },
                                )
                            }
                        }
                    },
                )
                dialog.setOnCancelListener { finish(null) }
                dialog.setOnDismissListener { finish(null) }
                continuation.invokeOnCancellation {
                    runOnUiThread {
                        if (dialog.isShowing) dialog.dismiss()
                    }
                }
                dialog.show()
                dialog.window?.apply {
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setDimAmount(.86f)
                }
            }
        }

    private fun RomgiDownloadPlan.toDownloadSet(): RomDownloadSet? = takeIf { it.isMultiDisc }?.let { plan ->
        RomDownloadSet(
            parentTitle = plan.parentTitle,
            editionTitle = plan.editionTitle,
            region = plan.region,
            format = plan.format,
            artworkUrl = plan.artworkUrl,
            items = plan.discs.map { disc ->
                RomDownloadSet.singleSourceItem(
                    parentTitle = plan.parentTitle,
                    editionTitle = plan.editionTitle,
                    region = plan.region,
                    format = plan.format,
                    discIndex = disc.index,
                    discLabel = disc.label,
                    source = disc.source.toAuthorizedSource(),
                )
            },
        )
    }

    private suspend fun updateCatalogProgress(progress: CatalogProgress) = withContext(Dispatchers.Main) {
        currentSourceLabel = "ROMgi · Catálogo"
        binding.sourceText.text = "Fuente: $currentSourceLabel"
        binding.statusText.text = progress.stage
        binding.helperText.text = "El catálogo se guarda localmente para acelerar las próximas búsquedas."
        val hasTotal = progress.total > 0L
        binding.progressBar.isIndeterminate = !hasTotal
        val percent = if (hasTotal) ((progress.downloaded * 100L) / progress.total).toInt().coerceIn(0, 100) else null
        if (percent != null) binding.progressBar.progress = percent
        binding.progressPercentText.text = percent?.let { "$it%" } ?: "Preparando…"
        binding.downloadedText.text = when {
            progress.downloaded > 0L && progress.total > 0L -> "${formatBytes(progress.downloaded)} de ${formatBytes(progress.total)}"
            progress.downloaded > 0L -> formatBytes(progress.downloaded)
            else -> "Tamaño pendiente"
        }
        binding.fileInfoText.text = "Base local de ROMgi"
        binding.speedText.text = "Preparando índice"
        binding.peersText.text = "No aplica"
        binding.etaText.text = "Calculando…"
    }

    private fun ResolvedSource.toAuthorizedSource(): AuthorizedSource {
        val selected = when {
            isTorrent && preferences.romgiTorrentsEnabled -> magnetUri ?: url.takeIf { it.startsWith("https://", true) }
            url.startsWith("https://", true) -> url
            !magnetUri.isNullOrBlank() && preferences.romgiTorrentsEnabled -> magnetUri
            url.startsWith("http://", true) -> error("La fuente ROMgi disponible usa HTTP sin cifrar y fue bloqueada por seguridad.")
            else -> error("La fuente ROMgi seleccionada no ofrece un transporte seguro habilitado.")
        } ?: error("La fuente torrent no incluye magnet ni descarga HTTPS segura.")
        return AuthorizedSource(
            value = selected,
            expectedFileName = fileName,
            torrentFileIndex = torrentFileIndex,
            torrentFilePath = torrentFilePath,
            expectedSize = size,
            sourceLabel = "ROMgi · ${host.ifBlank { sourceId.orEmpty() }}",
            requiresInternetArchiveAuth = requiresAuth,
        )
    }

    private fun isUnmeteredWifi(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun begin(
        current: GameEntity,
        source: AuthorizedSource,
        romgiBiosSources: List<Pair<String, AuthorizedSource>> = emptyList(),
        resolvedRegion: String? = null,
        downloadSet: RomDownloadSet? = null,
        sourcePlan: DownloadSourcePlan? = null,
    ) {
        if (source.isHttp || source.isMagnet) {
            if (!persistentLibrary.hasDestination(current.systemId)) {
                requestPersistentRomFolder(current, source, romgiBiosSources, resolvedRegion, downloadSet, sourcePlan)
                return
            }
            startOrAttachBackgroundDownload(current, source, romgiBiosSources, resolvedRegion, downloadSet, sourcePlan)
            return
        }
        if (job?.isActive == true) return
        retryAction = { begin(current, source, romgiBiosSources, resolvedRegion, downloadSet, sourcePlan) }
        currentSourceLabel = sourceLabelFor(source)
        lastLoggedPhase = null
        resetProgressUi(currentSourceLabel)
        binding.pickRomButton.visibility = View.GONE
        binding.cancelButton.text = "Cancelar"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        job = lifecycleScope.launch {
            try {
                diagnostics.info("SESSION_BEGIN", "game_id=${current.id} system=${current.systemId}")
                val directPath = source.takeUnless { it.isHttp || it.isMagnet }
                    ?.let { ExternalPathResolver.resolve(it.value) }
                val configuredEngine = preferences.engineMode(current.systemId)
                val profile = EmulatorProfiles.forSystem(current.systemId)
                val externalMayBeUsed = configuredEngine == EngineMode.EXTERNAL ||
                    configuredEngine == EngineMode.ASK ||
                    (configuredEngine == EngineMode.AUTO && profile?.recommendedEngine != EngineMode.RETROARCH)
                val shareableContentUri = source.value.startsWith("content://", true)
                val directDisplayName = source.expectedFileName
                    ?.takeIf(String::isNotBlank)
                    ?: directPath?.substringAfterLast('/')?.ifBlank { "game.rom" }
                    ?: "game.rom"
                val directNeedsExtraction = RomArchivePreparer.needsNativeExtraction(
                    current.systemId,
                    directDisplayName.substringAfterLast('.', ""),
                )
                if (
                    directPath != null &&
                    current.systemId != "arcade-fbneo" &&
                    !directNeedsExtraction &&
                    (!externalMayBeUsed || shareableContentUri)
                ) {
                    val displayName = directDisplayName
                    showSimpleStage("ROM localizada · abriendo emulador…", currentSourceLabel, displayName, completed = true)
                    diagnostics.info("DIRECT_ROM_READY", "file=$displayName path=$directPath")
                    launchEmulator(
                        current,
                        PublicSessionStore.Entry(
                            displayName = displayName,
                            absolutePath = directPath,
                            mediaUri = source.value.takeIf { it.startsWith("content://", true) }?.let(Uri::parse),
                        ),
                    )
                    return@launch
                }

                val downloader = AuthorizedSourceDownloader(this@SessionActivity, store, diagnostics)
                val mainSourceLabel = currentSourceLabel
                var prepared = downloader.prepare(current, source) { progress ->
                    withContext(Dispatchers.Main) { updateProgress(progress) }
                }
                if (current.systemId == "arcade-fbneo" && romgiBiosSources.isNotEmpty()) {
                    val downloadedBios = linkedMapOf<String, File>()
                    romgiBiosSources.forEachIndexed { index, (setName, biosSource) ->
                        showSimpleStage(
                            "Descargando BIOS ${index + 1}/${romgiBiosSources.size} desde ROMgi…",
                            "ROMgi · BIOS FBNeo",
                            setName,
                        )
                        val biosGame = current.copy(
                            id = "romgi-bios-$setName",
                            title = "$setName BIOS",
                            systemId = "romgi-bios",
                            romSet = null,
                            biosSetsCsv = "",
                            virtualExtension = "zip",
                        )
                        downloadedBios[setName] = downloader.prepare(biosGame, biosSource) { progress ->
                            withContext(Dispatchers.Main) { updateProgress(progress) }
                        }
                    }
                    prepared = FbneoBiosMerger.mergeDownloadedBios(prepared, downloadedBios, diagnostics).file
                }
                if (current.systemId == "arcade-fbneo" && current.biosSets.isNotEmpty()) {
                    showSimpleStage("Completando dependencias de FBNeo…", "BIOS configuradas", prepared.name)
                    val biosResult = FbneoBiosMerger.mergeConfiguredBios(
                        context = this@SessionActivity,
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
                RomPreparedFileValidator.validate(current, prepared, diagnostics)
                currentSourceLabel = mainSourceLabel
                showSimpleStage(
                    "ROM lista · preparando acceso del emulador…",
                    mainSourceLabel,
                    if (current.systemId == "arcade-fbneo") {
                        "${prepared.name} · ${formatBytes(prepared.length())} · romset exacto; si FBNeo informa archivos faltantes, actualiza el set según el DAT de la versión del core"
                    } else {
                        "${prepared.name} · ${formatBytes(prepared.length())}"
                    },
                    completed = true,
                )
                val published = if (source.isHttp || source.isMagnet) {
                    showSimpleStage("Guardando ROM en tu biblioteca…", "Biblioteca ROMs", prepared.name)
                    val stored = withContext(Dispatchers.IO) {
                        persistentLibrary.persist(current, prepared, resolvedRegion)
                    }
                    val directPath = ExternalPathResolver.resolve(stored.uri.toString())
                    diagnostics.info(
                        "PERSISTENT_ROM_SAVED",
                        "game_id=${current.id} system=${current.systemId} region=${stored.region} bytes=${stored.bytes} sha256=${stored.sha256} direct_path=${directPath != null}",
                    )
                    showSimpleStage(
                        "ROM guardada · lista para jugar",
                        "Biblioteca ROMs",
                        "${stored.displayName} · ${formatBytes(stored.bytes)}",
                        completed = true,
                    )
                    if (directPath != null) {
                        PublicSessionStore.Entry(stored.displayName, directPath, stored.uri)
                    } else {
                        // Current stable RetroArch launch contracts still commonly expect a
                        // filesystem path. Keep the persistent SAF copy as source of truth and
                        // create only a local compatibility mirror when a path cannot be derived.
                        withContext(Dispatchers.IO) { publicStore.publish(prepared) }.copy(mediaUri = stored.uri)
                    }
                } else {
                    withContext(Dispatchers.IO) { publicStore.publish(prepared) }
                }
                launchEmulator(current, published)
            } catch (cancelled: CancellationException) {
                diagnostics.info("SESSION_CANCELLED", "game_id=${current.id}")
                throw cancelled
            } catch (error: Throwable) {
                diagnostics.error("SESSION_FAILED", error)
                showFatal("No pude preparar el juego", error.message ?: error.javaClass.simpleName)
            } finally {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun updateProgress(progress: AuthorizedSourceDownloader.Progress) {
        val state = SessionProgressFormatter.build(progress)
        binding.sourceText.text = "Fuente: $currentSourceLabel"
        binding.statusText.text = progress.stage
        binding.helperText.text = state.helperText
        binding.elapsedText.text = "Tiempo: ${state.elapsedText}"
        binding.progressPercentText.text = state.percentageText
        binding.progressBar.isIndeterminate = state.percentage == null
        state.percentage?.let { binding.progressBar.progress = it }
        binding.fileInfoText.text = progress.fileName.ifBlank { "Esperando nombre del archivo" }
        binding.downloadedText.text = state.transferredText
        binding.speedText.text = state.speedText
        binding.peersText.text = state.peersText
        binding.etaText.text = state.etaText

        binding.stallWarningText.visibility = if (progress.stalled) View.VISIBLE else View.GONE
        binding.stallWarningText.text = progress.stallMessage.orEmpty()
        binding.retryButton.visibility = if (progress.stalled && retryAction != null) View.VISIBLE else View.GONE
        binding.pickRomButton.visibility = if (progress.stalled) View.VISIBLE else View.GONE

        if (lastLoggedPhase != progress.phase) {
            lastLoggedPhase = progress.phase
            diagnostics.info(
                "SESSION_PHASE",
                "phase=${progress.phase} source=$currentSourceLabel file=${progress.fileName} total=${progress.total}",
            )
        }
    }

    private fun sourceLabelFor(source: AuthorizedSource): String = source.sourceLabel?.takeIf { it.isNotBlank() } ?: when {
        source.isMagnet -> "ROMgi · Torrent"
        source.isHttp -> "ROMgi · HTTPS"
        source.isFilePath -> "iiSU o biblioteca local"
        source.isContent -> "Archivo propio"
        else -> "Fuente autorizada"
    }

    private fun resetProgressUi(sourceLabel: String) {
        binding.preparationCompose.visibility = View.GONE
        binding.downloadProgressContainer.visibility = View.VISIBLE
        currentSourceLabel = sourceLabel
        binding.sourceText.text = "Fuente: $sourceLabel"
        binding.statusText.text = "Preparando sesión…"
        binding.helperText.text = "Oz está preparando la sesión de juego."
        binding.progressBar.isIndeterminate = true
        binding.progressPercentText.text = "Calculando…"
        binding.elapsedText.text = "Tiempo: 00:00"
        binding.fileInfoText.text = "Esperando nombre del archivo"
        binding.downloadedText.text = "Tamaño pendiente"
        binding.speedText.text = "Esperando datos"
        binding.peersText.text = "Sin pares conectados"
        binding.etaText.text = "Calculando…"
        binding.stallWarningText.visibility = View.GONE
        binding.retryButton.visibility = View.GONE
        binding.pickRomButton.visibility = View.GONE
        binding.pauseDownloadButton.visibility = View.GONE
        binding.deletePartialButton.visibility = View.GONE
    }

    private fun showSimpleStage(
        stage: String,
        sourceLabel: String,
        fileName: String = "",
        completed: Boolean = false,
    ) {
        currentSourceLabel = sourceLabel
        binding.sourceText.text = "Fuente: $sourceLabel"
        binding.statusText.text = stage
        binding.helperText.text = if (completed) {
            "La preparación terminó; Oz está transfiriendo el control al emulador."
        } else {
            "Oz continúa trabajando. Esta etapa no tiene un porcentaje de descarga propio."
        }
        binding.fileInfoText.text = fileName.ifBlank { "Preparando archivo" }
        binding.progressBar.isIndeterminate = !completed
        if (completed) binding.progressBar.progress = 100
        binding.progressPercentText.text = if (completed) "100%" else "Preparando…"
        binding.downloadedText.text = if (completed) "Completado" else "Tamaño pendiente"
        binding.speedText.text = "No aplica"
        binding.peersText.text = "No aplica"
        binding.etaText.text = if (completed) "Completado" else "Calculando…"
        binding.stallWarningText.visibility = View.GONE
        binding.retryButton.visibility = View.GONE
        binding.pauseDownloadButton.visibility = View.GONE
        binding.deletePartialButton.visibility = View.GONE
    }

    private fun startOrAttachBackgroundDownload(
        current: GameEntity,
        source: AuthorizedSource,
        biosSources: List<Pair<String, AuthorizedSource>>,
        resolvedRegion: String?,
        downloadSet: RomDownloadSet? = null,
        sourcePlan: DownloadSourcePlan? = null,
    ) {
        retryAction = { startOrAttachBackgroundDownload(current, source, biosSources, resolvedRegion, downloadSet, sourcePlan) }
        val taskStore = RomDownloadTaskStore(this)
        val existing = taskStore.get(current.id)
        if (
            existing?.isActive == true && existing.source == source &&
            (sourcePlan == null || existing.sourcePlan.selectedCandidate.identity == sourcePlan.selectedCandidate.identity) &&
            existing.downloadSet?.signature == downloadSet?.signature
        ) {
            attachBackgroundDownload(current, existing)
            return
        }
        val pending = PendingBackgroundSchedule(current, source, biosSources, resolvedRegion, downloadSet, sourcePlan)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingBackgroundSchedule = pending
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        scheduleBackgroundDownload(current, source, biosSources, resolvedRegion, downloadSet, sourcePlan)
    }

    private fun scheduleBackgroundDownload(
        current: GameEntity,
        source: AuthorizedSource,
        biosSources: List<Pair<String, AuthorizedSource>>,
        resolvedRegion: String?,
        downloadSet: RomDownloadSet? = null,
        sourcePlan: DownloadSourcePlan? = null,
    ) {
        val result = runCatching {
            RomDownloadScheduler.schedule(
                this,
                current,
                source,
                biosSources,
                resolvedRegion,
                externalLauncher = returnToExternalLauncher,
                downloadSet = downloadSet,
                sourcePlan = sourcePlan,
            )
        }.getOrElse { error ->
            showFatal("No pude iniciar la descarga", error.message ?: error.javaClass.simpleName)
            return
        }
        diagnostics.info(
            "BACKGROUND_DOWNLOAD_SCHEDULED",
            "game_id=${current.id} task=${result.snapshot.taskId} mode=${result.mode.name}",
        )
        Toast.makeText(
            this,
            if (result.mode == RomDownloadScheduler.Mode.USER_INITIATED_JOB) {
                "Descarga iniciada. Puedes minimizar Oz; Android la mantendrá en segundo plano."
            } else {
                "Descarga iniciada en segundo plano con servicio visible."
            },
            Toast.LENGTH_LONG,
        ).show()
        attachBackgroundDownload(current, result.snapshot)
    }

    private fun attachBackgroundDownload(current: GameEntity, initial: RomDownloadTaskSnapshot) {
        currentSourceLabel = sourceLabelFor(initial.source)
        renderBackgroundSnapshot(initial)
        backgroundMonitorJob?.cancel()
        backgroundMonitorJob = lifecycleScope.launch {
            while (true) {
                val snapshot = RomDownloadTaskStore(this@SessionActivity).get(current.id) ?: return@launch
                if (activityVisible) renderBackgroundSnapshot(snapshot)
                when (snapshot.status) {
                    RomDownloadStatus.QUEUED, RomDownloadStatus.RUNNING -> delay(500L)
                    RomDownloadStatus.SUCCESS -> {
                        if (activityVisible && lastBackgroundTaskHandled != snapshot.taskId) {
                            lastBackgroundTaskHandled = snapshot.taskId
                            val shouldAutoLaunch = returnToExternalLauncher ||
                                (!intent.getBooleanExtra(EXTRA_DOWNLOAD_ONLY, false) && preferences.autoLaunchAfterDownload)
                            resumeCompletedBackgroundDownload(current, autoLaunch = shouldAutoLaunch)
                        }
                        return@launch
                    }
                    RomDownloadStatus.FAILED -> {
                        if (activityVisible) showBackgroundFailure(current, snapshot)
                        return@launch
                    }
                    RomDownloadStatus.CANCELLED -> {
                        if (activityVisible) showBackgroundPaused(current, snapshot)
                        return@launch
                    }
                }
            }
        }
    }

    private fun restoreBackgroundDownloadUi(current: GameEntity) {
        val snapshot = RomDownloadTaskStore(this).get(current.id) ?: return
        when (snapshot.status) {
            RomDownloadStatus.QUEUED, RomDownloadStatus.RUNNING -> attachBackgroundDownload(current, snapshot)
            RomDownloadStatus.SUCCESS -> {
                if (intent.getBooleanExtra(EXTRA_BACKGROUND_DOWNLOAD, false)) {
                    intent.removeExtra(EXTRA_BACKGROUND_DOWNLOAD)
                    if (lastBackgroundTaskHandled != snapshot.taskId) {
                        lastBackgroundTaskHandled = snapshot.taskId
                        resumeCompletedBackgroundDownload(current, autoLaunch = true)
                    }
                } else {
                    resumeCompletedBackgroundDownload(current, autoLaunch = false)
                }
            }
            RomDownloadStatus.FAILED -> showBackgroundFailure(current, snapshot)
            RomDownloadStatus.CANCELLED -> showBackgroundPaused(current, snapshot)
        }
    }

    private fun pauseBackgroundDownload() {
        val current = game ?: return
        val taskStore = RomDownloadTaskStore(this)
        val snapshot = taskStore.get(current.id) ?: return
        if (!snapshot.isActive) return

        binding.pauseDownloadButton.isEnabled = false
        RomDownloadScheduler.cancel(
            context = this,
            gameId = current.id,
            keepPartial = true,
        )

        val paused = taskStore.get(current.id) ?: snapshot
        showBackgroundPaused(current, paused)
    }

    private fun showBackgroundPaused(current: GameEntity, snapshot: RomDownloadTaskSnapshot) {
        val taskStore = RomDownloadTaskStore(this)
        val hasPartial = taskStore.hasPartial(current.id)

        // Preserve the last real download snapshot while paused.
        // A paused download must never fall back to the generic preparation UI.
        renderBackgroundSnapshot(snapshot)
        binding.statusText.text = "Descarga pausada"
        binding.stallWarningText.visibility = View.GONE
        binding.speedText.text = "Pausada"
        binding.etaText.text = "Se recalculará al reanudar"

        if (snapshot.total <= 0L) {
            // There is no truthful percentage when the complete size is unknown.
            // Stop the indeterminate animation: the transfer is paused.
            binding.progressBar.isIndeterminate = false
            binding.progressPercentText.text = "Progreso conservado"
        }
        binding.helperText.text = if (hasPartial) {
            "La parte descargada se conserva. Puedes reanudar desde los datos guardados o borrar el parcial."
        } else {
            "La descarga se pausó antes de guardar datos. Puedes iniciarla nuevamente cuando quieras."
        }
        retryAction = {
            begin(
                current,
                snapshot.source,
                snapshot.biosSources,
                snapshot.resolvedRegion,
                snapshot.downloadSet,
            )
        }
    }

    private fun renderBackgroundSnapshot(snapshot: RomDownloadTaskSnapshot) {
        val progress = AuthorizedSourceDownloader.Progress(
            stage = snapshot.stage.ifBlank { "Descargando en segundo plano…" },
            downloaded = snapshot.downloaded,
            total = snapshot.total,
            bytesPerSecond = snapshot.bytesPerSecond,
            peers = snapshot.peers,
            fileName = snapshot.fileName,
            phase = AuthorizedSourceDownloader.Phase.DOWNLOADING,
            elapsedMillis = (System.currentTimeMillis() - snapshot.startedAt).coerceAtLeast(0L),
            etaSeconds = if (snapshot.total > snapshot.downloaded && snapshot.bytesPerSecond > 0L) {
                (snapshot.total - snapshot.downloaded) / snapshot.bytesPerSecond
            } else null,
        )
        updateProgress(progress)
        binding.helperText.text = "La descarga es independiente de esta pantalla. Puedes minimizar o cerrar esta vista y seguirá en segundo plano."
        renderBackgroundActions(snapshot)
    }

    private fun renderBackgroundActions(snapshot: RomDownloadTaskSnapshot) {
        binding.pauseDownloadButton.isEnabled = true

        when (snapshot.status) {
            RomDownloadStatus.QUEUED,
            RomDownloadStatus.RUNNING -> {
                binding.pauseDownloadButton.text = "Pausar descarga"
                binding.pauseDownloadButton.visibility = View.VISIBLE
                binding.retryButton.visibility = View.GONE
                binding.pickRomButton.visibility = View.GONE
                binding.deletePartialButton.visibility = View.GONE
                binding.cancelButton.text = "Cerrar pantalla"
            }

            RomDownloadStatus.CANCELLED -> {
                val hasPartial = RomDownloadTaskStore(this).hasPartial(snapshot.gameId)
                binding.pauseDownloadButton.visibility = View.GONE
                binding.retryButton.text = "Reanudar descarga"
                binding.retryButton.visibility = View.VISIBLE
                binding.pickRomButton.visibility = View.GONE
                binding.deletePartialButton.visibility =
                    if (hasPartial) View.VISIBLE else View.GONE
                binding.cancelButton.text = "Cerrar"
            }

            RomDownloadStatus.FAILED -> {
                val hasPartial = RomDownloadTaskStore(this).hasPartial(snapshot.gameId)
                binding.pauseDownloadButton.visibility = View.GONE
                binding.retryButton.text = "Reanudar descarga"
                binding.retryButton.visibility = View.VISIBLE
                binding.pickRomButton.visibility = View.VISIBLE
                binding.deletePartialButton.visibility =
                    if (hasPartial) View.VISIBLE else View.GONE
                binding.cancelButton.text = "Cerrar"
            }

            RomDownloadStatus.SUCCESS -> {
                binding.pauseDownloadButton.visibility = View.GONE
                binding.retryButton.visibility = View.GONE
                binding.pickRomButton.visibility = View.GONE
                binding.deletePartialButton.visibility = View.GONE
                binding.cancelButton.text = "Cerrar"
            }
        }
    }

    private fun showBackgroundFailure(current: GameEntity, snapshot: RomDownloadTaskSnapshot) {
        currentSourceLabel = sourceLabelFor(snapshot.source)
        retryAction = { begin(current, snapshot.source, snapshot.biosSources, snapshot.resolvedRegion, snapshot.downloadSet) }
        renderBackgroundSnapshot(snapshot)
        binding.statusText.text = "Descarga interrumpida"
        binding.fileInfoText.text = snapshot.error ?: "La conexión se interrumpió."
        binding.helperText.text = "Puedes reanudar desde los bytes conservados o borrar la descarga parcial."
    }

    private fun resumeCompletedBackgroundDownload(current: GameEntity, autoLaunch: Boolean) {
        backgroundMonitorJob?.cancel()
        backgroundMonitorJob = null
        lifecycleScope.launch {
            val stored = persistentLibrary.resolve(current)
            if (stored == null) {
                showFatal("ROM no disponible", "La descarga terminó, pero Android ya no puede abrir el archivo guardado.")
                return@launch
            }
            if (autoLaunch) {
                showSimpleStage("Descarga completa · finalizando acceso…", "Biblioteca ROMs", stored.displayName, completed = true)
                if (stored.isMultiDisc) {
                    try {
                        launchStoredMultiDisc(current, stored, fresh = true)
                    } catch (error: Throwable) {
                        diagnostics.error("MULTIDISC_FRESH_LAUNCH_FAILED", error)
                        showFatal(
                            "Juego guardado, pero no pude abrir la lista multidisco",
                            error.message ?: "Android no terminó de publicar todos los discos para RetroArch.",
                        )
                    }
                } else if (current.systemId == "ps1") {
                    try {
                        val entry = prepareFreshPs1LaunchEntry(current, stored)
                        launchEmulator(current, entry)
                    } catch (error: Throwable) {
                        diagnostics.error("PS1_FRESH_LAUNCH_BARRIER_FAILED", error)
                        showFatal(
                            "PS1 guardado, pero no pude abrirlo todavía",
                            error.message ?: "Android no terminó de publicar el archivo para RetroArch.",
                        )
                    }
                } else {
                    begin(current, stored.asSource())
                }
            } else {
                showSimpleStage("Descarga completa", "Biblioteca ROMs", stored.displayName, completed = true)
                binding.helperText.text = if (stored.isMultiDisc) {
                    "Los ${stored.members.size} discos y su lista M3U quedaron guardados. Pulsa Jugar ahora."
                } else {
                    "La ROM quedó guardada. Pulsa Jugar ahora o continúa desde la notificación."
                }
                retryAction = {
                    if (stored.isMultiDisc) {
                        lifecycleScope.launch {
                            runCatching { launchStoredMultiDisc(current, stored, fresh = false) }
                                .onFailure { showFatal("No pude abrir el juego multidisco", it.message ?: it.javaClass.simpleName) }
                        }
                    } else {
                        begin(current, stored.asSource())
                    }
                }
                binding.retryButton.text = "Jugar ahora"
                binding.retryButton.visibility = View.VISIBLE
                binding.deletePartialButton.visibility = View.GONE
                binding.cancelButton.text = "Cerrar"
            }
        }
    }

    private suspend fun launchStoredMultiDisc(
        current: GameEntity,
        stored: PersistentRomLibrary.StoredRom,
        fresh: Boolean,
    ) {
        require(stored.isMultiDisc) { "El registro persistente no contiene un conjunto multidisco válido." }
        showSimpleStage(
            if (fresh) "Finalizando acceso a todos los discos…" else "Lista multidisco localizada · abriendo emulador…",
            "Biblioteca ROMs · ${stored.members.size} discos",
            stored.displayName,
            completed = !fresh,
        )
        val entry = prepareMultiDiscLaunchEntry(stored, fresh)
        diagnostics.info(
            "MULTIDISC_LAUNCH_READY",
            "game_id=${current.id} discs=${stored.members.size} playlist=${entry.displayName} mirrored=${entry.mediaUri != stored.uri}",
        )
        launchEmulator(current, entry)
    }

    private suspend fun prepareMultiDiscLaunchEntry(
        stored: PersistentRomLibrary.StoredRom,
        fresh: Boolean,
    ): PublicSessionStore.Entry = withContext(Dispatchers.IO) {
        val playlistPath = ExternalPathResolver.resolve(stored.uri.toString())
        val memberPaths = stored.members.sortedBy { it.discIndex }
            .map { member -> member to ExternalPathResolver.resolve(member.uri.toString()) }
        val playlistFile = playlistPath?.let(::File)
        val sameFolder = playlistFile != null && memberPaths.all { (_, path) ->
            val file = path?.let(::File)
            file != null && file.isFile && file.canRead() && file.parentFile == playlistFile.parentFile
        }
        val directReady = !fresh && playlistFile?.isFile == true && playlistFile.canRead() && sameFolder
        if (directReady) {
            return@withContext PublicSessionStore.Entry(stored.displayName, playlistFile.absolutePath, stored.uri)
        }
        // A fresh multi-disc save is mirrored as one unit. Copying only the M3U would leave its
        // relative rows pointing at files RetroArch cannot see through the SAF URI boundary.
        publicStore.publishSet(stored)
    }

    /**
     * A freshly persisted PS1 disc can become visible through SAF a fraction of a second before
     * the corresponding shared-storage path is fully stable for another app. The normal second
     * launch naturally happens after that propagation window, which is why the same game can work
     * immediately when opened again. This barrier keeps the first auto-launch from racing Android
     * storage propagation.
     *
     * We prefer the persistent direct path once it has stayed readable with the expected size for
     * several consecutive probes. If it does not stabilize quickly, Oz uses its existing public
     * compatibility mirror from the authoritative SAF URI instead of handing RetroArch a dubious
     * path. No ROM is downloaded again.
     */
    private suspend fun prepareFreshPs1LaunchEntry(
        current: GameEntity,
        stored: PersistentRomLibrary.StoredRom,
    ): PublicSessionStore.Entry {
        val directPath = ExternalPathResolver.resolve(stored.uri.toString())
        diagnostics.info(
            "PS1_FRESH_LAUNCH_BARRIER_BEGIN",
            "game_id=${current.id} file=${stored.displayName} bytes=${stored.bytes} direct_path=${directPath != null}",
        )

        val readyPath = awaitStableFreshRomPath(stored, directPath)
        if (readyPath != null) {
            diagnostics.info(
                "PS1_FRESH_LAUNCH_BARRIER_READY",
                "game_id=${current.id} file=${stored.displayName} path=${readyPath.substringAfterLast('/')} bytes=${stored.bytes}",
            )
            showSimpleStage(
                "ROM verificada · abriendo PlayStation…",
                "Biblioteca ROMs",
                stored.displayName,
                completed = true,
            )
            return PublicSessionStore.Entry(stored.displayName, readyPath, stored.uri)
        }

        showSimpleStage(
            "Finalizando acceso compatible con RetroArch…",
            "Biblioteca ROMs",
            stored.displayName,
            completed = true,
        )
        val mirrored = withContext(Dispatchers.IO) {
            publicStore.publish(stored.uri, stored.displayName)
        }
        val mirrorFile = File(mirrored.absolutePath)
        require(mirrorFile.isFile && mirrorFile.canRead() && mirrorFile.length() > 0L) {
            "La ROM de PS1 se guardó correctamente, pero Android aún no permite preparar una copia legible para RetroArch."
        }
        if (stored.bytes > 0L) {
            require(mirrorFile.length() == stored.bytes) {
                "La copia temporal de PS1 quedó incompleta (${mirrorFile.length()} de ${stored.bytes} bytes)."
            }
        }
        diagnostics.info(
            "PS1_FRESH_LAUNCH_BARRIER_MIRROR",
            "game_id=${current.id} file=${stored.displayName} mirror_bytes=${mirrorFile.length()}",
        )
        return mirrored.copy(mediaUri = stored.uri)
    }

    private suspend fun awaitStableFreshRomPath(
        stored: PersistentRomLibrary.StoredRom,
        directPath: String?,
    ): String? = withContext(Dispatchers.IO) {
        if (directPath.isNullOrBlank()) return@withContext null
        val file = File(directPath)
        var stableSamples = 0
        repeat(PS1_FRESH_PATH_MAX_PROBES) { probe ->
            val uriReadable = runCatching {
                contentResolver.openInputStream(stored.uri)?.use { input ->
                    input.read(ByteArray(PS1_FRESH_PROBE_BYTES)) > 0
                } == true
            }.getOrDefault(false)
            val length = runCatching { file.length() }.getOrDefault(0L)
            val expectedMatches = stored.bytes <= 0L || length == stored.bytes
            val pathReady = file.isFile && file.canRead() && length > 0L && expectedMatches

            if (uriReadable && pathReady) {
                stableSamples += 1
                if (stableSamples >= PS1_FRESH_PATH_STABLE_SAMPLES) {
                    // Give FUSE/DocumentsProvider one last short propagation window, then verify
                    // again immediately before RetroArch receives the path.
                    delay(PS1_FRESH_PATH_SETTLE_MS)
                    val finalLength = runCatching { file.length() }.getOrDefault(0L)
                    val finalExpectedMatches = stored.bytes <= 0L || finalLength == stored.bytes
                    if (file.isFile && file.canRead() && finalLength > 0L && finalExpectedMatches) {
                        return@withContext directPath
                    }
                    stableSamples = 0
                }
            } else {
                stableSamples = 0
            }

            diagnostics.info(
                "PS1_FRESH_LAUNCH_BARRIER_WAIT",
                "probe=${probe + 1}/$PS1_FRESH_PATH_MAX_PROBES uri=$uriReadable path=$pathReady bytes=$length expected=${stored.bytes}",
            )
            delay(PS1_FRESH_PATH_POLL_MS)
        }
        null
    }

    private fun deleteCurrentPartialDownload() {
        val current = game ?: return
        val store = RomDownloadTaskStore(this)
        val snapshot = store.get(current.id) ?: return
        AlertDialog.Builder(this)
            .setTitle("Borrar descarga parcial")
            .setMessage("Se eliminarán los bytes incompletos de ${snapshot.fileName.ifBlank { current.title }}. La ROM guardada en tu biblioteca, si existe, no se tocará.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Borrar") { _, _ ->
                RomDownloadScheduler.cancel(this, current.id, keepPartial = false)
                binding.deletePartialButton.visibility = View.GONE
                showSimpleStage("Descarga parcial eliminada", "Biblioteca ROMs", current.title)
                binding.helperText.text = "Puedes volver a iniciar la descarga cuando quieras."
            }
            .show()
    }

    private fun requestPersistentRomFolder(
        current: GameEntity,
        source: AuthorizedSource,
        biosSources: List<Pair<String, AuthorizedSource>>,
        resolvedRegion: String?,
        downloadSet: RomDownloadSet? = null,
        sourcePlan: DownloadSourcePlan? = null,
    ) {
        pendingPersistentDownload = PendingPersistentDownload(current, source, biosSources, resolvedRegion, downloadSet, sourcePlan)
        showSimpleStage("Elige dónde conservar tus ROMs", "Biblioteca ROMs", current.title)
        AlertDialog.Builder(this)
            .setTitle("Biblioteca ROMs persistente")
            .setMessage(
                "Oz descargará este juego una sola vez y lo conservará. Selecciona la carpeta compartida de ROMs que también usas con RetroArch. " +
                    "Android recordará el permiso; Oz no escribirá en carpetas privadas de RetroArch.",
            )
            .setPositiveButton("Elegir carpeta ROMs") { _, _ ->
                romLibraryPicker.launch(preferences.globalRomTreeUri)
            }
            .setNegativeButton("Cancelar") { _, _ ->
                pendingPersistentDownload = null
                showSimpleStage("Descarga cancelada", "ROMgi", current.title)
            }
            .show()
    }

    private fun persistTree(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    private fun retryCurrentAttempt() {
        val action = retryAction ?: return
        replaceCurrentAttempt(action)
    }

    private fun replaceCurrentAttempt(action: () -> Unit) {
        val previous = job
        job = null
        previous?.cancel()
        lifecycleScope.launch {
            previous?.join()
            withContext(Dispatchers.IO) {
                store.deleteAll()
                publicStore.deleteAll()
            }
            action()
        }
    }


    private suspend fun launchEmulator(current: GameEntity, entry: PublicSessionStore.Entry) {
        val profile = EmulatorProfiles.forSystem(current.systemId)
        val mode = preferences.engineMode(current.systemId)
        val selectedApp = profile?.let {
            externalEmulatorManager.selectedOrRecommended(
                it,
                preferences.externalEmulatorPackage(current.systemId),
            )
        }
        val mediaUri = entry.mediaUri
        val extension = entry.displayName.substringAfterLast('.', "").lowercase()
        val externalReady = profile != null && selectedApp != null && mediaUri != null &&
            extension in selectedApp.acceptedExtensions &&
            externalEmulatorManager.canLaunch(selectedApp, mediaUri, entry.displayName)

        when (mode) {
            EngineMode.RETROARCH -> launchRetroArch(current, entry)
            EngineMode.EXTERNAL -> {
                if (externalReady) launchExternal(current, entry, selectedApp!!, mediaUri!!)
                else showExternalUnavailable(current, entry, profile, selectedApp, extension)
            }
            EngineMode.ASK -> showEngineChooser(current, entry, profile, selectedApp, mediaUri, externalReady)
            EngineMode.AUTO -> {
                val prefersExternal = profile?.recommendedEngine == EngineMode.EXTERNAL ||
                    profile?.recommendedEngine == EngineMode.AUTO
                if (prefersExternal && externalReady) {
                    launchExternal(current, entry, selectedApp!!, mediaUri!!)
                } else {
                    binding.statusText.text = "Motor automático: RetroArch"
                    binding.fileInfoText.text = "${entry.displayName} · ${profile?.retroArchCore ?: current.coreId.orEmpty()}"
                    diagnostics.info(
                        "ENGINE_AUTO_SELECTED",
                        "system=${current.systemId} engine=retroarch external_ready=$externalReady",
                    )
                    launchRetroArch(current, entry)
                }
            }
        }
    }

    private fun launchExternal(
        current: GameEntity,
        entry: PublicSessionStore.Entry,
        app: ExternalEmulatorApp,
        uri: Uri,
    ) {
        binding.statusText.text = "Abriendo ${app.name}…"
        binding.fileInfoText.text = "${entry.displayName} · ${app.packageName}"
        diagnostics.info(
            "EXTERNAL_EMULATOR_SELECTED",
            "system=${current.systemId} package=${app.packageName} uri_scheme=${uri.scheme}",
        )
        emulatorLaunched = true
        if (externalEmulatorManager.launch(app, uri, entry.displayName)) {
            markPlayed(current.id)
            diagnostics.info("EMULATOR_LAUNCHED", "package=${app.packageName} file=${entry.displayName}")
        } else {
            emulatorLaunched = false
            showExternalUnavailable(current, entry, EmulatorProfiles.forSystem(current.systemId), app, entry.displayName.substringAfterLast('.', ""))
        }
    }

    private fun showEngineChooser(
        current: GameEntity,
        entry: PublicSessionStore.Entry,
        profile: EmulatorProfile?,
        app: ExternalEmulatorApp?,
        uri: Uri?,
        externalReady: Boolean,
    ) {
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (externalReady && app != null && uri != null) {
            options += "${app.name} · ${app.packageName}" to {
                launchExternal(current, entry, app, uri)
            }
        }
        options += "RetroArch · ${profile?.retroArchCore ?: current.coreId.orEmpty()}" to {
            lifecycleScope.launch {
                runCatching { launchRetroArch(current, entry) }
                    .onFailure { showFatal("No pude abrir RetroArch", it.message ?: it.javaClass.simpleName) }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Elegir motor para ${current.platform}")
            .setMessage("Archivo: ${entry.displayName}\nOz solo muestra motores que puede validar en este dispositivo.")
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options.getOrNull(which)?.second?.invoke() }
            .setNeutralButton("Configurar") { _, _ -> startActivity(Intent(this, EmulatorSettingsActivity::class.java)) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showExternalUnavailable(
        current: GameEntity,
        entry: PublicSessionStore.Entry,
        profile: EmulatorProfile?,
        app: ExternalEmulatorApp?,
        extension: String,
    ) {
        val reason = when {
            app == null -> "No existe una integración externa documentada para ${current.platform}."
            !externalEmulatorManager.isInstalled(app.packageName) -> "${app.name} no está instalado."
            app.launchKind == ExternalLaunchKind.APP_ONLY -> "${app.name} está instalado, pero su actividad de emulación no es exportada por el manifiesto actual."
            entry.mediaUri == null -> "El archivo no dispone de un content URI compartible."
            extension !in app.acceptedExtensions -> "El formato .$extension no está declarado para ${app.name}."
            else -> "Android no confirmó una actividad exportada compatible para ${app.name}."
        }
        diagnostics.info(
            "EXTERNAL_EMULATOR_UNAVAILABLE",
            "system=${current.systemId} package=${app?.packageName.orEmpty()} extension=$extension",
        )
        val dialog = AlertDialog.Builder(this)
            .setTitle("Emulador externo no disponible")
            .setMessage("$reason\n\nOz permanecerá abierto. Puedes usar RetroArch o revisar la configuración por sistema.")
            .setPositiveButton("Usar RetroArch") { _, _ ->
                lifecycleScope.launch {
                    runCatching { launchRetroArch(current, entry) }
                        .onFailure { showFatal("No pude abrir RetroArch", it.message ?: it.javaClass.simpleName) }
                }
            }
            .setNeutralButton("Configurar") { _, _ -> startActivity(Intent(this, EmulatorSettingsActivity::class.java)) }
        if (app != null && externalEmulatorManager.isInstalled(app.packageName) && app.launchKind == ExternalLaunchKind.APP_ONLY) {
            dialog.setNegativeButton("Abrir ${app.name}") { _, _ ->
                if (!externalEmulatorManager.openApp(app)) {
                    Toast.makeText(this, "Android no permitió abrir ${app.name}.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            dialog.setNegativeButton("Cancelar", null)
        }
        dialog.show()
    }

    private suspend fun launchRetroArch(current: GameEntity, entry: PublicSessionStore.Entry) {
        startRetroPreparation()
        val retroEntry = try {
            ensureRetroArchAccessibleEntry(entry)
        } catch (error: Throwable) {
            markRetroPreparation("file", false, error.message ?: "archivo inaccesible")
            throw error
        }
        try {
            val fileStatus = verifyRetroFileAccess(current, retroEntry)
            markRetroPreparation("file", true, fileStatus)
        } catch (error: Throwable) {
            markRetroPreparation("file", false, error.message ?: "archivo inaccesible")
            throw error
        }

        retroPreparationViewModel.processing("core")
        val profile = EmulatorProfiles.forSystem(current.systemId)
        // The system profile is Oz's maintained recommendation. Catalog/game metadata may
        // contain an older compatible core (for example PCSX ReARMed on PS1), so it is a
        // fallback rather than the first choice. installedCompatibleCore() can still select
        // a documented compatible alternative when the preferred core is not installed.
        val requestedCore = profile?.retroArchCore?.takeIf(String::isNotBlank)
            ?: current.coreId?.takeIf(String::isNotBlank)
            ?: error("${current.platform} no tiene un core RetroArch configurado.")
        runCatching { validateDeclaredRetroCore(current, profile, requestedCore) }
            .onFailure {
                markRetroPreparation("core", false, it.message ?: "core incompatible con el sistema")
                throw it
            }

        val packageName = retroArchManager.preferredInstalledPackage(preferences.retroArchPackage)
        if (packageName == null) {
            markRetroPreparation("core", false, "RetroArch no está instalado")
            diagnostics.info("RETROARCH_NOT_INSTALLED", "system=${current.systemId}")
            pendingRetroArchGame = current
            pendingRetroArchEntry = retroEntry
            showRetroArchInstallRequired()
            return
        }
        if (preferences.retroArchPackage != packageName) preferences.retroArchPackage = packageName

        binding.statusText.text = "Preparando juego"
        val query = runCatching { retroArchManager.queryInstalledCores(packageName) }
            .onFailure { diagnostics.error("RETROARCH_CORE_QUERY_FAILED", it) }
            .getOrNull()
        if (query?.supported != true) {
            val candidates = buildList {
                profile?.retroArchCore?.takeIf(String::isNotBlank)?.let {
                    addAll(retroArchManager.coreCandidatesFor(it))
                }
                addAll(retroArchManager.coreCandidatesFor(requestedCore))
            }.distinct()
            val fallbackCore = candidates.firstOrNull {
                preferences.isRetroArchCoreConfirmed(packageName, it)
            } ?: candidates.firstOrNull()
            if (fallbackCore == null) {
                markRetroPreparation("core", false, "No existe un core configurado para ${current.platform}")
                showFatal(
                    "No pude preparar el core",
                    "Oz no encontró un core compatible configurado para ${current.platform}.",
                )
                return
            }
            if (!retroArchManager.directLaunchActivityAvailable(packageName)) {
                markRetroPreparation("core", false, "RetroArch no expone su actividad de lanzamiento")
                showFatal(
                    "No pude abrir RetroArch",
                    "La variante instalada no expone RetroActivityFuture para lanzamiento externo.",
                )
                return
            }
            val previouslyConfirmed = preferences.isRetroArchCoreConfirmed(packageName, fallbackCore)
            val detail = if (previouslyConfirmed) {
                "$fallbackCore · confirmado anteriormente"
            } else {
                "$fallbackCore · ruta estándar RetroArch"
            }
            markRetroPreparation("core", true, detail)
            diagnostics.info(
                "RETROARCH_CORE_QUERY_FALLBACK",
                "package=$packageName core=$fallbackCore previous=$previouslyConfirmed receiver=${retroArchManager.installedCoresReceiver(packageName) != null}",
            )
            validateRetroStagesAfterCore(current, retroEntry, fallbackCore)
            val corePath = retroArchManager.directCorePath(packageName, fallbackCore)
            launchRetroArchDirect(current, packageName, corePath, retroEntry, fallbackCore)
            return
        }
        val installedCore = retroArchManager.installedCompatibleCore(query, requestedCore)
        if (installedCore == null) {
            retroArchManager.coreCandidatesFor(requestedCore).forEach {
                preferences.setRetroArchCoreConfirmed(packageName, it, false)
            }
            markRetroPreparation("core", false, "Falta ${retroArchManager.normalizeCoreId(requestedCore)}")
            if (!preferences.retroArchAutoInstallCores) {
                showCoreInstallRequired(current, retroEntry, packageName, requestedCore)
                return
            }
            installCoreAndLaunch(current, retroEntry, packageName, requestedCore)
            return
        }

        preferences.setRetroArchCoreConfirmed(packageName, installedCore, true)
        markRetroPreparation("core", true, installedCore)
        validateRetroStagesAfterCore(current, retroEntry, installedCore)
        val corePath = retroArchManager.directCorePath(packageName, installedCore)
        launchRetroArchDirect(current, packageName, corePath, retroEntry, installedCore)
    }

    private fun launchRetroArchDirect(
        current: GameEntity,
        packageName: String,
        corePath: String,
        entry: PublicSessionStore.Entry,
        coreName: String,
    ) {
        if (!retroArchManager.directLaunchActivityAvailable(packageName)) {
            markRetroPreparation("open", false, "Activity de RetroArch no disponible para lanzamiento externo")
            error("La variante de RetroArch seleccionada no expone una actividad de lanzamiento válida.")
        }
        retroPreparationViewModel.processing("open", "${current.platform} · $coreName")
        val launch = retroArchManager.buildDirectLaunchIntent(packageName, corePath, entry.absolutePath)
        try {
            emulatorLaunched = true
            startActivity(launch)
            markRetroPreparation("open", true, "RetroArch abierto")
            markPlayed(current.id)
            diagnostics.info(
                "RETROARCH_NATIVE_LAUNCHED",
                "package=$packageName core=$coreName file=${entry.displayName}",
            )
        } catch (error: ActivityNotFoundException) {
            emulatorLaunched = false
            preferences.setRetroArchCoreConfirmed(packageName, coreName, false)
            markRetroPreparation("open", false, "Android rechazó la actividad de lanzamiento")
            throw IllegalStateException(
                "La variante de RetroArch seleccionada no admite el lanzamiento externo. Selecciona otra variante en Configuración.",
                error,
            )
        }
    }

    private suspend fun installCoreAndLaunch(
        current: GameEntity,
        entry: PublicSessionStore.Entry,
        packageName: String,
        requestedCore: String,
    ) {
        retroPreparationViewModel.processing("core", "Preparando ${retroArchManager.normalizeCoreId(requestedCore)}")
        binding.statusText.text = "Preparando core de ${current.platform}…"
        binding.progressBar.isIndeterminate = true
        binding.fileInfoText.text = retroArchManager.normalizeCoreId(requestedCore)
        val artifact = retroArchManager.downloadAndStageCore(
            requestedCore,
            preferences.retroArchCoreTreeUri,
        ) { progress ->
            lifecycleScope.launch(Dispatchers.Main) {
                binding.statusText.text = progress.stage
                val hasTotal = progress.total > 0L
                binding.progressBar.isIndeterminate = !hasTotal
                if (hasTotal) {
                    binding.progressBar.progress = ((progress.downloaded * 100L) / progress.total).toInt().coerceIn(0, 100)
                }
                binding.fileInfoText.text = if (hasTotal) {
                    "${artifactSafeName(requestedCore)} · ${formatBytes(progress.downloaded)} / ${formatBytes(progress.total)}"
                } else artifactSafeName(requestedCore)
            }
        }
        preferences.setRetroArchCoreConfirmed(packageName, artifact.coreName, false)
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 100

        if (artifact.directExternalCore) {
            markRetroPreparation("core", true, "${artifact.coreName} · carpeta externa verificada")
            validateRetroStagesAfterCore(current, entry, artifact.coreName)
            diagnostics.info(
                "RETROARCH_CORE_READY_EXTERNAL",
                "package=$packageName core=${artifact.coreName}",
            )
            launchRetroArchDirect(current, packageName, artifact.absolutePath, entry, artifact.coreName)
            return
        }

        // Fix5 separated core installation from game launch: the ROM is never sent to the sideload Activity.
        val sideload = retroArchManager.buildCoreSideloadIntent(packageName, artifact)
        pendingCoreSideload = PendingCoreSideload(current, entry, packageName, requestedCore)
        coreSideloadCoveredActivity = false
        try {
            emulatorLaunched = false
            retroPreparationViewModel.processing("core", "Instalando ${artifact.coreName} en RetroArch")
            binding.statusText.text = "Instalando core en RetroArch…"
            binding.helperText.text = "Cuando vuelvas a Oz, se verificará que RetroArch reconozca el core antes de abrir el juego."
            binding.fileInfoText.text = artifact.coreName
            startActivity(sideload)
            diagnostics.info(
                "RETROARCH_CORE_SIDELOAD_STARTED",
                "package=$packageName core=${artifact.coreName}",
            )
        } catch (error: ActivityNotFoundException) {
            pendingCoreSideload = null
            coreSideloadCoveredActivity = false
            preferences.setRetroArchCoreConfirmed(packageName, artifact.coreName, false)
            markRetroPreparation("core", false, "La instalación de RetroArch no admite el sideload de cores")
            throw IllegalStateException(
                "Esta instalación de RetroArch no permite agregar cores desde Oz. Instala la versión oficial actual o elige una carpeta externa de cores en Configuración.",
                error,
            )
        }
    }

    private fun artifactSafeName(requestedCore: String): String = retroArchManager.normalizeCoreId(requestedCore)

    private fun verifyPendingCoreSideload() {
        val pending = pendingCoreSideload ?: return
        binding.statusText.text = "Verificando core instalado…"
        binding.helperText.text = "Oz confirmará que RetroArch reconoce el core antes de transferirle la ROM."
        retroPreparationViewModel.processing("core", "Verificando instalación")
        lifecycleScope.launch {
            val query = runCatching { retroArchManager.queryInstalledCores(pending.packageName) }
                .onFailure { diagnostics.error("RETROARCH_CORE_RECHECK_FAILED", it) }
                .getOrNull()
            val installedCore = query
                ?.takeIf { it.supported }
                ?.let { retroArchManager.installedCompatibleCore(it, pending.requestedCore) }
            if (installedCore != null) {
                preferences.setRetroArchCoreConfirmed(pending.packageName, installedCore, true)
                pendingCoreSideload = null
                markRetroPreparation("core", true, "$installedCore · confirmado después de instalar")
                diagnostics.info(
                    "RETROARCH_CORE_SIDELOAD_CONFIRMED",
                    "package=${pending.packageName} core=$installedCore",
                )
                runCatching {
                    validateRetroStagesAfterCore(pending.game, pending.entry, installedCore)
                    val corePath = retroArchManager.directCorePath(pending.packageName, installedCore)
                    launchRetroArchDirect(pending.game, pending.packageName, corePath, pending.entry, installedCore)
                }.onFailure { error ->
                    diagnostics.error("RETROARCH_AFTER_CORE_PREFLIGHT_FAILED", error)
                    showFatal("No pude abrir RetroArch", error.message ?: error.javaClass.simpleName)
                }
            } else {
                pendingCoreSideload = null
                markRetroPreparation("core", false, "RetroArch todavía no reporta el core instalado")
                showFatal(
                    "Core no confirmado",
                    "RetroArch no reportó el core solicitado después de instalarlo. Oz no abrirá el juego hasta poder verificarlo.",
                )
            }
        }
    }

    private fun startRetroPreparation() {
        binding.downloadProgressContainer.visibility = View.GONE
        binding.preparationCompose.visibility = View.VISIBLE
        binding.statusText.text = "Preparando juego"
        retroPreparationViewModel.begin()
    }

    private fun markRetroPreparation(id: String, success: Boolean, detail: String) {
        if (success) retroPreparationViewModel.complete(id, detail)
        else retroPreparationViewModel.error(id, detail)
    }

    /**
     * RetroArch's verified direct-launch contract consumes a filesystem path. A ROM stored through
     * SAF can remain perfectly readable through content:// while its derived /storage path is not
     * readable under scoped storage. In that case create a short-lived public mirror strictly for
     * RetroArch; the persistent SAF URI remains the library source of truth.
     */
    private suspend fun ensureRetroArchAccessibleEntry(entry: PublicSessionStore.Entry): PublicSessionStore.Entry =
        withContext(Dispatchers.IO) {
            val direct = entry.absolutePath.takeIf(String::isNotBlank)?.let(::File)
            if (direct != null && direct.isFile && direct.canRead() && direct.length() > 0L) {
                return@withContext entry
            }

            val sourceUri = entry.mediaUri
                ?: error("La ROM local no ofrece una ruta legible ni una URI SAF que Oz pueda compartir con RetroArch.")
            val readable = runCatching {
                contentResolver.openFileDescriptor(sourceUri, "r")?.use { descriptor -> descriptor.statSize != 0L } == true
            }.getOrDefault(false)
            require(readable) { "Android ya no permite leer la ROM guardada en tu biblioteca." }

            val mirrored = publicStore.publish(sourceUri, entry.displayName)
            diagnostics.info(
                "RETROARCH_SAF_COMPAT_MIRROR",
                "file=${entry.displayName} source_scheme=${sourceUri.scheme} mirror=${mirrored.absolutePath.substringAfterLast('/')}"
            )
            mirrored.copy(mediaUri = sourceUri)
        }

    private suspend fun verifyRetroFileAccess(current: GameEntity, entry: PublicSessionStore.Entry): String = withContext(Dispatchers.IO) {
        val file = File(entry.absolutePath)
        require(file.exists() && file.isFile) { "La copia compatible con RetroArch no está disponible." }
        require(file.length() > 0L) { "El archivo de juego está vacío." }
        // Final safety gate before entering native emulator code. A virtual iiSU descriptor is a
        // launcher token, never emulation content, even when its filename intentionally mimics the
        // platform extension so iiSU can classify it under the correct console.
        RomPreparedFileValidator.validate(current, file, diagnostics)
        entry.mediaUri?.let { uri ->
            val readable = runCatching {
                contentResolver.openFileDescriptor(uri, "r")?.use { descriptor -> descriptor.statSize != 0L } == true
            }.getOrDefault(false)
            require(readable) { "Android ya no permite acceder a la URI persistente del juego." }
        }
        "${entry.displayName} · ${formatBytes(file.length())}"
    }

    private fun validateDeclaredRetroCore(current: GameEntity, profile: EmulatorProfile?, requestedCore: String) {
        val expected = profile?.retroArchCore ?: return
        val normalizedRequested = retroArchManager.normalizeCoreId(requestedCore)
        require(normalizedRequested in retroArchManager.coreCandidatesFor(expected)) {
            "El core $normalizedRequested no corresponde al perfil RetroArch de ${current.platform}."
        }
    }

    private suspend fun validateRetroStagesAfterCore(
        current: GameEntity,
        entry: PublicSessionStore.Entry,
        coreName: String,
    ) {
        retroPreparationViewModel.processing("compatibility")
        try {
            validateRetroCompatibility(current, entry, coreName)
            val extension = entry.displayName.substringAfterLast('.', "").lowercase()
            markRetroPreparation("compatibility", true, ".$extension · ${current.platform}")
        } catch (error: Throwable) {
            markRetroPreparation("compatibility", false, error.message ?: "formato o core incompatible")
            throw error
        }

        retroPreparationViewModel.processing("bios")
        val profile = EmulatorProfiles.forSystem(current.systemId)
        try {
            val biosStatus = validateRetroBios(current, profile, entry)
            markRetroPreparation("bios", true, biosStatus)
        } catch (error: Throwable) {
            markRetroPreparation("bios", false, error.message ?: "BIOS incompleta")
            throw error
        }
    }

    private fun validateRetroCompatibility(current: GameEntity, entry: PublicSessionStore.Entry, coreName: String) {
        val profile = EmulatorProfiles.forSystem(current.systemId)
            ?: error("No existe un perfil de emulación verificado para ${current.platform}.")
        val extension = entry.displayName.substringAfterLast('.', "").lowercase()
        require(extension.isNotBlank() && extension in profile.acceptedExtensions) {
            "El formato .${extension.ifBlank { "sin extensión" }} no está declarado como compatible para ${current.platform}."
        }
        val expectedCore = current.coreId?.takeIf(String::isNotBlank) ?: profile.retroArchCore
        require(retroArchManager.normalizeCoreId(coreName) in retroArchManager.coreCandidatesFor(expectedCore)) {
            "El core $coreName no coincide con el core requerido para ${current.platform}."
        }
    }

    private suspend fun validateRetroBios(
        current: GameEntity,
        profile: EmulatorProfile?,
        entry: PublicSessionStore.Entry,
    ): String {
        if (current.systemId == "arcade-fbneo") {
            val file = File(entry.absolutePath)
            FbneoSupport.expectedArchiveName(current)?.let { expected ->
                require(file.name.equals(expected, ignoreCase = true)) {
                    "El romset ${file.name} no coincide con $expected. Oz no abrirá RetroArch hasta corregir el set."
                }
            }
            require(file.isFile && file.length() > 0L) { "El romset FBNeo no está disponible o está vacío." }
        }
        val requirements = RetroBiosValidator.requirements(current, entry.displayName)
        if (requirements.isNotEmpty()) {
            val verified = RetroBiosValidator.validate(this, preferences.retroArchSystemTreeUri, requirements)
            diagnostics.info(
                "RETROARCH_BIOS_VERIFIED",
                "system=${current.systemId} files=${verified.matched.joinToString(",")}",
            )
            return "${verified.required} requisito(s) verificado(s) · ${verified.matched.joinToString()}"
        }
        if (profile != null && profile.bios.contains("requer", ignoreCase = true)) {
            diagnostics.info(
                "RETROARCH_BIOS_REQUIREMENT_UNMAPPED",
                "system=${current.systemId} requirement=${profile.bios}",
            )
            return "requisito reconocido · ${profile.bios}"
        }
        return when {
            current.systemId == "arcade-fbneo" && current.biosSets.isNotEmpty() -> "dependencias FBNeo preparadas"
            profile == null -> "sin requisito declarado"
            else -> profile.bios
        }
    }

    private data class PendingCoreSideload(
        val game: GameEntity,
        val entry: PublicSessionStore.Entry,
        val packageName: String,
        val requestedCore: String,
    )

    private fun showRetroArchInstallRequired() {
        binding.statusText.text = "RetroArch no está instalado"
        binding.fileInfoText.text = "Instala RetroArch y vuelve a Oz. La sesión continuará y iiSU seguirá disponible como integración avanzada."
        binding.progressBar.isIndeterminate = false
        AlertDialog.Builder(this)
            .setTitle("Instalar RetroArch")
            .setMessage(
                "Oz RetroRom necesita RetroArch para emular este sistema. Android solicitará tu confirmación durante la instalación. " +
                    "Cuando vuelvas a Oz, la sesión continuará y se preparará automáticamente el core necesario.",
            )
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Configuración") { _, _ ->
                startActivity(Intent(this, cl.gakuto.kizuna.ui.SettingsActivity::class.java))
            }
            .setPositiveButton("Instalar") { _, _ ->
                waitingForRetroArchInstall = true
                runCatching { retroArchManager.openOfficialInstallPage() }
                    .onFailure {
                        waitingForRetroArchInstall = false
                        showFatal("No se pudo abrir el instalador", it.message ?: "Sin navegador disponible")
                    }
            }
            .show()
    }

    private fun showCoreInstallRequired(
        current: GameEntity,
        entry: PublicSessionStore.Entry,
        packageName: String,
        requestedCore: String,
    ) {
        val coreName = retroArchManager.normalizeCoreId(requestedCore)
        val canInstall = retroArchManager.canAutomaticallyInstall(requestedCore)
        binding.statusText.text = "Falta el core de ${current.platform}"
        binding.fileInfoText.text = coreName
        val builder = AlertDialog.Builder(this)
            .setTitle("Core requerido")
            .setMessage(
                if (canInstall) {
                    "RetroArch está instalado, pero falta $coreName. Oz descargará únicamente la copia aprobada por SHA-256 y la entregará a RetroArch."
                } else {
                    "RetroArch está instalado, pero falta $coreName. Este core no está incluido en el catálogo SHA-256 de esta APK; instálalo desde el actualizador de cores de RetroArch y vuelve a Oz."
                },
            )
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Configuración") { _, _ ->
                startActivity(Intent(this, cl.gakuto.kizuna.ui.SettingsActivity::class.java))
            }

        if (canInstall) {
            builder.setPositiveButton("Instalar y jugar") { _, _ ->
                lifecycleScope.launch {
                    runCatching { installCoreAndLaunch(current, entry, packageName, requestedCore) }
                        .onFailure { error ->
                            diagnostics.error("RETROARCH_CORE_INSTALL_FAILED", error)
                            showFatal("No se pudo instalar el core", error.message ?: error.javaClass.simpleName)
                        }
                }
            }
        } else {
            builder.setPositiveButton("Abrir RetroArch") { _, _ ->
                if (!retroArchManager.openRetroArch(packageName)) {
                    showFatal("No se pudo abrir RetroArch", "Abre RetroArch manualmente e instala $coreName desde su actualizador de cores.")
                }
            }
        }
        builder.show()
    }

    private fun cancelAndFinish() {
        job?.cancel()
        backgroundMonitorJob?.cancel()
        store.deleteAll()
        publicStore.deleteAll()
        finishSessionToOrigin()
    }

    private fun isExternalBridgeLaunch(value: Intent): Boolean =
        value.getBooleanExtra(EXTRA_EXTERNAL_LAUNCHER, false) ||
            (!value.getStringExtra(EXTRA_IIISU_ROM).isNullOrBlank() &&
                value.getStringExtra(EXTRA_GAME_ID).isNullOrBlank())

    /**
     * iiSU is a landscape-first launcher on the handheld flow we support. SessionActivity used
     * to be hard-locked to portrait in the manifest, which rotated the device exactly while a
     * virtual game was being resolved/downloaded. Remove that manifest lock and choose the
     * orientation from the launch origin instead. sensorLandscape keeps either landscape side
     * available; internal Oz sessions retain the portrait UX used by the app itself.
     *
     * Android can ignore orientation requests on some large-screen configurations. That is fine:
     * SessionActivity now has fixed adaptive portrait/landscape layouts and the request is only a
     * phone/handheld hint; the progress screen never depends on an internal ScrollView.
     */
    private fun applySessionOrientation() {
        requestedOrientation = when (preferences.appOrientationMode) {
            cl.gakuto.kizuna.data.AppOrientationMode.AUTOMATIC ->
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            cl.gakuto.kizuna.data.AppOrientationMode.PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            cl.gakuto.kizuna.data.AppOrientationMode.LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    /**
     * When iiSU is the launcher, SessionActivity is a transient bridge. Finishing only this
     * activity can reveal Oz's existing MainActivity because Android preserves the task back
     * stack. finishAffinity() removes Oz activities with the same affinity from that foreground
     * task, allowing the previously active launcher task (iiSU) to resume. Internal Oz launches
     * keep normal finish() semantics.
     */
    private fun finishSessionToOrigin() {
        diagnostics.info(
            "SESSION_RETURN_TARGET",
            if (returnToExternalLauncher) "external launcher" else "Oz",
        )
        if (returnToExternalLauncher) {
            runCatching { finishAffinity() }.onFailure { finish() }
        } else {
            finish()
        }
    }

    private fun showFatal(title: String, message: String) {
        binding.preparationCompose.visibility = View.GONE
        binding.downloadProgressContainer.visibility = View.VISIBLE
        binding.statusText.text = title
        binding.fileInfoText.text = message
        binding.progressBar.isIndeterminate = false
        binding.cancelButton.text = "Cerrar"
        binding.helperText.text = "La sesión se detuvo. Puedes reintentar o usar un archivo propio."
        binding.retryButton.visibility = if (retryAction != null) View.VISIBLE else View.GONE
        binding.pickRomButton.visibility = if (game != null) View.VISIBLE else View.GONE
        binding.deletePartialButton.visibility = game?.let { if (RomDownloadTaskStore(this).hasPartial(it.id)) View.VISIBLE else View.GONE } ?: View.GONE
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("$message\n\nPuedes reintentar la fuente o elegir un archivo propio.")
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private data class LaunchRequest(val gameId: String, val source: AuthorizedSource? = null)

    private suspend fun resolveLaunchRequest(): LaunchRequest? {
        SessionLaunchValidator.requireAllowedAction(intent)

        SessionLaunchValidator.validatedGameId(intent.getStringExtra(EXTRA_GAME_ID))?.let {
            return LaunchRequest(it)
        }

        val rawReference = intent.getStringExtra(EXTRA_IIISU_ROM)?.takeIf { it.isNotBlank() }
            ?: intent.data?.toString()?.takeIf { it.isNotBlank() }
            ?: return null
        val dao = (application as OzApplication).database.gameDao()

        // iiSU normally supplies a raw filesystem path. With scoped storage Oz may not be allowed
        // to open that raw path even though iiSU can. Bridge descriptors use the stable game ID as
        // their filename, so resolve that identity BEFORE requiring filesystem readability.
        val rawCleaned = Uri.decode(rawReference).substringBefore('?').substringBefore('#').trim('\"', '\'')
        val rawFileName = rawCleaned.substringAfterLast('/').substringAfterLast('\\')
        val rawBaseName = rawFileName.substringBeforeLast('.')
        val rawExtension = rawFileName.substringAfterLast('.', missingDelimiterValue = "")
        if (rawBaseName.isNotBlank() && rawExtension.isNotBlank()) {
            dao.byId(rawBaseName)?.let { bridgeGame ->
                val expectedExtension = bridgeGame.virtualExtension.ifBlank { "ozr" }
                if (rawExtension.equals(expectedExtension, ignoreCase = true)) {
                    diagnostics.info("IISU_BRIDGE_ID_RESOLVED", "game_id=${bridgeGame.id} file=$rawFileName")
                    return LaunchRequest(bridgeGame.id)
                }
            }
        }

        val reference = SessionLaunchValidator.validatedReference(this, intent, rawReference)

        descriptorFromReference(reference)
            ?.optString("gameId")
            ?.let(SessionLaunchValidator::validatedGameId)
            ?.let { return LaunchRequest(it) }

        val cleaned = Uri.decode(reference).substringBefore('?').substringBefore('#').trim('\"', '\'')
        val fileName = cleaned.substringAfterLast('/').substringAfterLast('\\')
        val baseName = fileName.substringBeforeLast('.')
        if (baseName.isBlank()) return null
        val loaded = dao.byId(baseName)
            ?: dao.byRomSet(baseName)
            ?: dao.bestLegacyReference(TextNormalizer.slug(baseName))
        return loaded?.let { game ->
            val virtualDescriptor = baseName.equals(game.id, ignoreCase = true)
            LaunchRequest(
                gameId = game.id,
                source = if (virtualDescriptor) null else AuthorizedSource(reference, fileName, sourceLabel = "iiSU"),
            )
        }
    }

    private fun descriptorFromReference(reference: String): JSONObject? {
        val uri = when {
            reference.startsWith("content://", true) -> Uri.parse(reference)
            reference.startsWith("file://", true) -> Uri.parse(reference)
            else -> Uri.fromFile(File(reference))
        }
        return descriptorFromUri(uri)
    }

    private fun isOzVirtualDescriptorReference(reference: String): Boolean {
        val descriptor = descriptorFromReference(reference) ?: return false
        return descriptor.optString("brand").equals("Oz RetroRom", ignoreCase = true) &&
            descriptor.optString("contentMode").equals("oz-virtual-on-demand", ignoreCase = true) &&
            descriptor.optString("gameId").isNotBlank()
    }

    private fun descriptorFromUri(uri: Uri): JSONObject? = runCatching {
        val input = when (uri.scheme?.lowercase()) {
            "content" -> contentResolver.openInputStream(uri)
            "file" -> uri.path?.let(::File)?.takeIf { it.isFile }?.inputStream()
            else -> null
        } ?: return@runCatching null
        input.use { stream ->
            val buffered = BufferedInputStream(stream).apply { mark(4) }
            val signature = ByteArray(4)
            val signatureBytes = buffered.read(signature)
            buffered.reset()
            val isZip = signatureBytes == 4 &&
                signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte()
            if (isZip) {
                ZipInputStream(buffered).use { zip ->
                    var scannedEntries = 0
                    while (scannedEntries++ < MAX_DESCRIPTOR_ZIP_ENTRIES) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.substringAfterLast('/').equals("oz-retrorom.json", true)) {
                            return@runCatching JSONObject(zip.readUtf8Limited(MAX_DESCRIPTOR_BYTES))
                        }
                    }
                    null
                }
            } else {
                JSONObject(buffered.readUtf8Limited(MAX_DESCRIPTOR_BYTES))
            }
        }
    }.getOrNull()

    private fun InputStream.readUtf8Limited(maxBytes: Int): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "El descriptor excede el tamaño permitido." }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun queryName(uri: Uri): String? = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

    private fun persist(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun markPlayed(gameId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { (application as OzApplication).database.gameDao().markPlayed(gameId, System.currentTimeMillis()) }
                .onFailure { diagnostics.error("PLAY_HISTORY_WRITE_FAILED", it) }
        }
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "zip" -> "application/zip"
        "iso" -> "application/x-iso9660-image"
        else -> "application/octet-stream"
    }

    private fun formatBytes(value: Long): String = when {
        value >= 1024L * 1024L * 1024L -> "%.2f GB".format(value / (1024.0 * 1024.0 * 1024.0))
        value >= 1024L * 1024L -> "%.1f MB".format(value / (1024.0 * 1024.0))
        value >= 1024L -> "%.0f KB".format(value / 1024.0)
        else -> "$value B"
    }

    private data class PendingPersistentDownload(
        val game: GameEntity,
        val source: AuthorizedSource,
        val biosSources: List<Pair<String, AuthorizedSource>>,
        val resolvedRegion: String?,
        val downloadSet: RomDownloadSet?,
        val sourcePlan: DownloadSourcePlan?,
    )

    private data class PendingBackgroundSchedule(
        val game: GameEntity,
        val source: AuthorizedSource,
        val biosSources: List<Pair<String, AuthorizedSource>>,
        val resolvedRegion: String?,
        val downloadSet: RomDownloadSet?,
        val sourcePlan: DownloadSourcePlan?,
    )

    companion object {
        private const val PS1_FRESH_PATH_MAX_PROBES = 12
        private const val PS1_FRESH_PATH_STABLE_SAMPLES = 3
        private const val PS1_FRESH_PATH_POLL_MS = 250L
        private const val PS1_FRESH_PATH_SETTLE_MS = 450L
        private const val PS1_FRESH_PROBE_BYTES = 64
        const val ACTION_PLAY = "cl.gakuto.kizuna.action.PLAY"
        const val EXTRA_GAME_ID = "oz.gameId"
        const val EXTRA_SOURCE_INDEX_RELEASE_ID = "oz.sourceIndexReleaseId"
        const val EXTRA_SOURCE_INDEX_CANDIDATE_ID = "oz.sourceIndexCandidateId"
        const val EXTRA_BACKGROUND_DOWNLOAD = "oz.backgroundDownload"
        const val EXTRA_DOWNLOAD_ONLY = "oz.downloadOnly"
        const val EXTRA_PATCHED_ROM_PATH = "oz.patchedRomPath"
        const val EXTRA_EXTERNAL_LAUNCHER = "oz.externalLauncher"
        const val EXTRA_IIISU_ROM = "ROM"
        private const val MAX_DESCRIPTOR_BYTES = 128 * 1024
        private const val MAX_DESCRIPTOR_ZIP_ENTRIES = 256
    }
}

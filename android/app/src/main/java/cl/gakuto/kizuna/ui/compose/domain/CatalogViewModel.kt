package cl.gakuto.kizuna.ui.compose.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.catalog.CatalogSearchItem
import cl.gakuto.kizuna.catalog.CatalogSystem
import cl.gakuto.kizuna.catalog.CatalogSystemRouting
import cl.gakuto.kizuna.catalog.LibretroCatalogRepository
import cl.gakuto.kizuna.catalog.VaultCatalogSearchEngine
import cl.gakuto.kizuna.catalog.background.CatalogPreparationScheduler
import cl.gakuto.kizuna.catalog.background.CatalogPreparationStatus
import cl.gakuto.kizuna.catalog.background.CatalogPreparationTaskStore
import cl.gakuto.kizuna.catalog.background.catalogPreparationElapsedMillis
import cl.gakuto.kizuna.catalog.background.isCatalogPreparationStale
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.CollectionSummary
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameListSpec
import cl.gakuto.kizuna.data.GameTileStyle
import cl.gakuto.kizuna.data.LibraryOrigin
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.hub.GameVaultHubClient
import cl.gakuto.kizuna.launch.CollectionArtworkSyncManager
import cl.gakuto.kizuna.launch.CollectionBatchStatus
import cl.gakuto.kizuna.launch.CollectionBatchTaskStore
import cl.gakuto.kizuna.launch.CollectionDownloadScheduler
import cl.gakuto.kizuna.launch.SteamGridDbIconSyncManager
import cl.gakuto.kizuna.ui.compose.CatalogPreparationUi
import cl.gakuto.kizuna.ui.compose.CollectionArtworkSyncUi
import cl.gakuto.kizuna.ui.compose.CollectionDownloadUi
import cl.gakuto.kizuna.ui.compose.LibrarySection
import cl.gakuto.kizuna.util.CatalogFilterState
import cl.gakuto.kizuna.util.CatalogSort
import cl.gakuto.kizuna.util.OriginStatusFilter
import cl.gakuto.kizuna.util.PlatformFamily
import cl.gakuto.kizuna.util.TextNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cohesive domain holder for catalog search, systems, collections, library paging, and reactive task stores. */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CatalogDomainHolder(
    private val application: Application,
    private val scope: CoroutineScope,
    private val database: OzDatabase,
    private val repository: LibraryRepository,
    private val catalog: LibretroCatalogRepository,
    private val preferences: AppPreferences,
    private val collectionSummaries: StateFlow<List<CollectionSummary>>,
    private val pcHubUrlProvider: () -> String,
    private val messages: MutableSharedFlow<String>,
) {
    val systems: List<CatalogSystem> = catalog.systems()
    val sourceIndexCatalogSystems: List<CatalogSystem> = CatalogSystemRouting.eligibleSourceIndexSystems(systems)
    private val knownSystemIds = systems.mapTo(linkedSetOf()) { it.id }

    private val _catalogQuery = MutableStateFlow("")
    val catalogQuery: StateFlow<String> = _catalogQuery
    private val _catalogPlatformFilter = MutableStateFlow("")
    val catalogPlatformFilter: StateFlow<String> = _catalogPlatformFilter
    private val _catalogSearchResults = MutableStateFlow<List<CatalogSearchItem>>(emptyList())
    val catalogSearchResults: StateFlow<List<CatalogSearchItem>> = _catalogSearchResults
    private val _catalogSearching = MutableStateFlow(false)
    val catalogSearching: StateFlow<Boolean> = _catalogSearching
    private val _catalogShardCount = MutableStateFlow(0)
    val catalogShardCount: StateFlow<Int> = _catalogShardCount
    private val _catalogFilters = MutableStateFlow(CatalogFilterState())
    val catalogFilters: StateFlow<CatalogFilterState> = _catalogFilters
    private var catalogOnlineSearchJob: Job? = null

    private val _catalogPreparationRun = MutableStateFlow(CatalogPreparationUi(totalSystems = systems.size))
    val catalogPreparation: StateFlow<CatalogPreparationUi> = combine(
        _catalogPreparationRun,
        database.catalogStateDao().observeAll(),
        repository.gameCountPulse(),
    ) { run, states, gameCount ->
        run.copy(
            readySystems = states.count {
                it.systemId in knownSystemIds && it.status == "READY" && it.gameCount > 0
            },
            totalSystems = systems.size,
            gameCount = gameCount,
        )
    }.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5_000),
        CatalogPreparationUi(totalSystems = systems.size),
    )
    private var catalogPreparationWatchJob: Job? = null
    private var catalogRebuildJob: Job? = null

    val catalogGames: Flow<PagingData<GameEntity>> = combine(
        _catalogQuery.map { TextNormalizer.normalize(it.trim()) }.debounce(300).distinctUntilChanged(),
        _catalogFilters,
        repository.catalogRevisionPulse(),
    ) { query, filters, _ ->
        val f = filters.normalized()
        GameListSpec(
            normalizedSearch = query,
            systemIds = f.systemIds,
            platformFamilies = f.platformFamilies,
            genreKeys = f.genres,
            regionModes = f.regions,
            statusFilters = f.status,
            sort = f.sort,
        )
    }.flatMapLatest(repository::pagedGames).cachedIn(scope)

    private val _selectedCollectionId = MutableStateFlow<String?>(null)
    val selectedCollectionId: StateFlow<String?> = _selectedCollectionId
    private val _collectionQuery = MutableStateFlow("")
    private val _collectionFilters = MutableStateFlow(CatalogFilterState())
    val collectionQuery: StateFlow<String> = _collectionQuery
    val collectionFilters: StateFlow<CatalogFilterState> = _collectionFilters

    val selectedCollection: StateFlow<CollectionSummary?> = combine(collectionSummaries, _selectedCollectionId) { summaries, id ->
        summaries.firstOrNull { it.collection.id == id }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    val collectionGames: Flow<PagingData<GameEntity>> = combine(
        _selectedCollectionId,
        _collectionQuery.map { TextNormalizer.normalize(it.trim()) }.debounce(300).distinctUntilChanged(),
        _collectionFilters,
    ) { id, query, filters ->
        val f = filters.normalized()
        GameListSpec(
            collectionId = id,
            normalizedSearch = query,
            systemIds = f.systemIds,
            platformFamilies = f.platformFamilies,
            genreKeys = f.genres,
            regionModes = f.regions,
            statusFilters = f.status,
            sort = if (id == "recently-played" && f.sort == CatalogSort.NAME_ASC) CatalogSort.LAST_PLAYED else f.sort,
        )
    }.flatMapLatest { spec ->
        if (spec.collectionId == null) flowOf(PagingData.empty()) else repository.pagedGames(spec)
    }.cachedIn(scope)

    private val _librarySection = MutableStateFlow(LibrarySection.ALL)
    val librarySection: StateFlow<LibrarySection> = _librarySection
    private val _libraryPlatform = MutableStateFlow<String?>(null)
    val libraryPlatform: StateFlow<String?> = _libraryPlatform

    val libraryGames: Flow<PagingData<GameEntity>> = combine(_librarySection, _libraryPlatform) { section, platform ->
        when (section) {
            LibrarySection.ALL -> GameListSpec(statusFilters = setOf(OriginStatusFilter.LOCAL_LIBRARY), sort = CatalogSort.ADDED_RECENTLY)
            LibrarySection.DOWNLOADED -> GameListSpec(
                statusFilters = setOf(OriginStatusFilter.LOCAL_LIBRARY),
                libraryOrigins = setOf(LibraryOrigin.DOWNLOADED),
                sort = CatalogSort.ADDED_RECENTLY,
            )
            LibrarySection.IMPORTED -> GameListSpec(
                statusFilters = setOf(OriginStatusFilter.LOCAL_LIBRARY),
                libraryOrigins = setOf(LibraryOrigin.IMPORTED),
                sort = CatalogSort.ADDED_RECENTLY,
            )
            LibrarySection.PLATFORM -> GameListSpec(
                systemId = platform,
                statusFilters = setOf(OriginStatusFilter.LOCAL_LIBRARY),
                sort = CatalogSort.NAME_ASC,
            )
            LibrarySection.RECENT -> GameListSpec(
                statusFilters = setOf(OriginStatusFilter.LOCAL_LIBRARY, OriginStatusFilter.RECENTLY_PLAYED),
                sort = CatalogSort.LAST_PLAYED,
            )
        }
    }.flatMapLatest(repository::pagedGames).cachedIn(scope)

    private val _downloadUi = MutableStateFlow(CollectionDownloadUi())
    val downloadUi: StateFlow<CollectionDownloadUi> = _downloadUi
    private val _artworkSyncUi = MutableStateFlow(CollectionArtworkSyncUi())
    val artworkSyncUi: StateFlow<CollectionArtworkSyncUi> = _artworkSyncUi
    private var collectionBatchWatchJob: Job? = null

    init {
        scope.launch {
            val loadedCount = VaultCatalogSearchEngine.ensureLoaded(application)
            _catalogShardCount.value = loadedCount
            runCatalogSearch(_catalogQuery.value, _catalogPlatformFilter.value)
        }
    }

    fun setCatalogQuery(value: String) {
        _catalogQuery.value = value
        runCatalogSearch(value, _catalogPlatformFilter.value)
    }

    fun setCatalogPlatformFilter(platform: String) {
        val normalized = platform.trim().lowercase()
        _catalogPlatformFilter.value = normalized
        runCatalogSearch(_catalogQuery.value, normalized)
    }

    fun clearCatalogSearch() {
        _catalogQuery.value = ""
        _catalogPlatformFilter.value = ""
        _catalogSearchResults.value = emptyList()
        _catalogSearching.value = false
        catalogOnlineSearchJob?.cancel()
    }

    private fun runCatalogSearch(rawQuery: String, platform: String) {
        catalogOnlineSearchJob?.cancel()
        val q = rawQuery.trim()
        if (q.isEmpty() && platform.isBlank()) {
            _catalogSearchResults.value = emptyList()
            _catalogSearching.value = false
            return
        }
        catalogOnlineSearchJob = scope.launch {
            val total = VaultCatalogSearchEngine.ensureLoaded(application)
            if (_catalogShardCount.value == 0) _catalogShardCount.value = total

            if (q.length < 2) {
                if (platform.isNotBlank()) {
                    _catalogSearchResults.value = withContext(Dispatchers.Default) {
                        VaultCatalogSearchEngine.browsePlatform(platform, limit = 90)
                    }
                    _catalogSearching.value = false
                } else {
                    _catalogSearchResults.value = emptyList()
                    _catalogSearching.value = false
                }
                return@launch
            }

            delay(180)
            val localHits = withContext(Dispatchers.Default) {
                VaultCatalogSearchEngine.searchLocal(q, platform, limit = 80)
            }
            _catalogSearchResults.value = localHits

            val needsRemote = localHits.size < 12 || platform in setOf("nds", "psp", "dc", "gc", "wii", "ps2", "gb", "gbc", "saturn")
            if (needsRemote) {
                _catalogSearching.value = true
                val remoteHits = runCatching {
                    VaultCatalogSearchEngine.searchOnline(q, platform, pcHubUrlProvider())
                }.getOrDefault(emptyList())
                if (isActive) {
                    _catalogSearchResults.value = (localHits + remoteHits).distinctBy { it.id }
                    _catalogSearching.value = false
                }
            } else {
                _catalogSearching.value = false
            }
        }
    }

    suspend fun materializeCatalogItem(item: CatalogSearchItem): GameEntity = withContext(Dispatchers.IO) {
        val resolved = VaultCatalogSearchEngine.resolveRemoteSourceIfNeeded(item)
        val prototype = VaultCatalogSearchEngine.toGameEntity(resolved)
        val entity = repository.ensureGameForCatalogLaunch(prototype)
        if (entity.contentUri.isNullOrBlank()) {
            VaultCatalogSearchEngine.toAuthorizedSource(resolved)?.let { src ->
                preferences.setAuthorizedSource(entity.id, src)
            }
        }
        entity
    }

    fun setCatalogFilters(value: CatalogFilterState) {
        val normalized = value.normalized()
        _catalogFilters.value = normalized
        val requestedSystems = buildSet {
            addAll(normalized.systemIds)
            normalized.platformFamilies.forEach { family ->
                if (family == PlatformFamily.OTHER) {
                    val classified = PlatformFamily.entries
                        .filter { it != PlatformFamily.OTHER }
                        .flatMapTo(linkedSetOf()) { it.systemIds }
                    addAll(knownSystemIds - classified)
                } else {
                    addAll(family.systemIds)
                }
            }
        }
        prepareCatalogSystems(requestedSystems, completeCatalog = false)
    }

    fun prepareCompleteCatalog() {
        prepareCatalogSystems(knownSystemIds, completeCatalog = true)
    }

    fun rebuildCompleteCatalog() {
        if (_catalogPreparationRun.value.running || catalogRebuildJob?.isActive == true) return
        catalogRebuildJob = scope.launch {
            _catalogPreparationRun.value = _catalogPreparationRun.value.copy(
                stage = "Preparando reconstrucción segura…",
                failures = 0,
                elapsedMillis = 0L,
            )
            try {
                catalog.invalidateAllPreparedSystems()
                prepareCatalogSystems(knownSystemIds, completeCatalog = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = error.message ?: "No se pudo reiniciar la preparación del catálogo"
                _catalogPreparationRun.value = _catalogPreparationRun.value.copy(
                    running = false,
                    currentSystem = "",
                    stage = message,
                    failures = 1,
                )
                messages.emit(message)
            } finally {
                catalogRebuildJob = null
            }
        }
    }

    fun cancelCatalogPreparation() {
        if (!_catalogPreparationRun.value.running) return
        _catalogPreparationRun.value = _catalogPreparationRun.value.copy(stage = "Pausando de forma segura…")
        CatalogPreparationScheduler.cancel(application)
    }

    private fun prepareCatalogSystems(requestedSystemIds: Set<String>, completeCatalog: Boolean) {
        if (requestedSystemIds.isEmpty() || _catalogPreparationRun.value.running) return
        val targets = systems.filter { it.id in requestedSystemIds }
        if (targets.isEmpty()) return

        runCatching {
            CatalogPreparationScheduler.schedule(
                context = application,
                systemIds = targets.map { it.id },
                completeCatalog = completeCatalog,
            )
        }.onSuccess { scheduled ->
            _catalogPreparationRun.value = _catalogPreparationRun.value.copy(
                running = scheduled.snapshot.isActive,
                currentSystem = scheduled.snapshot.currentSystem,
                stage = scheduled.snapshot.stage,
                failures = scheduled.snapshot.failedCount,
                elapsedMillis = catalogPreparationElapsedMillis(scheduled.snapshot, System.currentTimeMillis()),
            )
            observeCatalogPreparationTask()
            if (completeCatalog) messages.tryEmit("Preparación del catálogo iniciada en segundo plano")
        }.onFailure { error ->
            _catalogPreparationRun.value = _catalogPreparationRun.value.copy(
                running = false,
                currentSystem = "",
                stage = error.message ?: "No se pudo iniciar la preparación del catálogo",
                failures = 1,
            )
            messages.tryEmit(error.message ?: "No se pudo iniciar la preparación del catálogo")
        }
    }

    /** Reactive StateFlow observer replacing fixed while + delay(700) polling. */
    private fun observeCatalogPreparationTask() {
        catalogPreparationWatchJob?.cancel()
        catalogPreparationWatchJob = scope.launch {
            val store = CatalogPreparationTaskStore(application)
            var observedActive = false
            var lastUpdatedAt = Long.MIN_VALUE
            CatalogPreparationTaskStore.observe(application).collect { rawSnapshot ->
                var snapshot = rawSnapshot
                if (snapshot != null && isCatalogPreparationStale(snapshot, System.currentTimeMillis())) {
                    snapshot = store.markSuspended(
                        stopReason = 0,
                        message = "No se detectó avance durante 5 minutos · pulsa Continuar; el progreso guardado se conserva",
                    )
                }
                if (snapshot != null) {
                    val snapshotChanged = snapshot.updatedAt != lastUpdatedAt
                    if (snapshotChanged) lastUpdatedAt = snapshot.updatedAt
                    if (snapshot.isActive) observedActive = true
                    _catalogPreparationRun.value = _catalogPreparationRun.value.copy(
                        running = snapshot.isActive,
                        currentSystem = snapshot.currentSystem,
                        stage = snapshot.stage,
                        failures = snapshot.failedCount,
                        elapsedMillis = catalogPreparationElapsedMillis(snapshot, System.currentTimeMillis()),
                    )
                    if (snapshotChanged && observedActive && !snapshot.isActive) {
                        val message = when (snapshot.status) {
                            CatalogPreparationStatus.SUCCESS,
                            CatalogPreparationStatus.PARTIAL -> snapshot.stage
                            CatalogPreparationStatus.FAILED -> snapshot.error ?: snapshot.stage
                            CatalogPreparationStatus.CANCELLED -> "Catálogo pausado; puedes continuarlo cuando quieras"
                            CatalogPreparationStatus.SUSPENDED,
                            CatalogPreparationStatus.QUEUED,
                            CatalogPreparationStatus.RUNNING -> snapshot.stage
                        }
                        if (snapshot.completeCatalog) messages.emit(message)
                        observedActive = false
                    }
                }
            }
        }
    }

    fun openCollection(id: String) {
        _selectedCollectionId.value = id
        _collectionQuery.value = ""
        _collectionFilters.value = CatalogFilterState()
        val snapshot = preferences.collectionArtworkSyncSnapshot(id)
        _artworkSyncUi.value = snapshot?.let {
            CollectionArtworkSyncUi(
                total = it.total,
                completed = it.total,
                resolved = it.resolved,
                missing = (it.total - it.resolved).coerceAtLeast(0),
                syncedAt = it.syncedAt,
            )
        } ?: CollectionArtworkSyncUi()
        observeCollectionBatch(id, announceCompletion = false)
    }

    fun setCollectionQuery(value: String) { _collectionQuery.value = value }
    fun setCollectionFilters(value: CatalogFilterState) { _collectionFilters.value = value.normalized() }
    fun setLibrarySection(value: LibrarySection) { _librarySection.value = value }
    fun setLibraryPlatform(systemId: String?) { _libraryPlatform.value = systemId }

    fun downloadSelectedCollection() {
        val id = _selectedCollectionId.value ?: return
        val summary = selectedCollection.value ?: return
        val existing = CollectionBatchTaskStore(application).get(id)
        if (existing?.isActive == true) {
            observeCollectionBatch(id, announceCompletion = false)
            messages.tryEmit("El batch download de ${summary.collection.name} ya está en ejecución.")
            return
        }
        runCatching {
            CollectionDownloadScheduler.schedule(
                context = application,
                collectionId = id,
                collectionName = summary.collection.name,
                totalHint = summary.gameCount,
            )
        }.onSuccess {
            _downloadUi.value = CollectionDownloadUi(
                running = true,
                stage = "Batch download iniciado en segundo plano",
                total = summary.gameCount,
            )
            messages.tryEmit(
                "Descarga por lote iniciada · ${summary.collection.name} · ${summary.gameCount - summary.localCount} ROMs faltantes",
            )
            observeCollectionBatch(id, announceCompletion = true)
        }.onFailure { error ->
            _downloadUi.value = CollectionDownloadUi()
            messages.tryEmit(error.message ?: "No se pudo iniciar el batch download")
        }
    }

    /** Reactive StateFlow observer replacing fixed while + delay(700) polling. */
    private fun observeCollectionBatch(collectionId: String, announceCompletion: Boolean) {
        collectionBatchWatchJob?.cancel()
        collectionBatchWatchJob = scope.launch {
            var announced = false
            CollectionBatchTaskStore.observe(application, collectionId).collect { snapshot ->
                if (_selectedCollectionId.value != collectionId) return@collect
                if (snapshot == null) {
                    _downloadUi.value = CollectionDownloadUi()
                    return@collect
                }
                _downloadUi.value = CollectionDownloadUi(
                    running = snapshot.isActive,
                    stage = snapshot.stage,
                    gameTitle = snapshot.gameTitle,
                    completed = snapshot.completed,
                    total = snapshot.total,
                    downloadedBytes = snapshot.currentDownloaded,
                    totalBytes = snapshot.currentTotal,
                )
                if (!snapshot.isActive && announceCompletion && !announced) {
                    announced = true
                    val resultText = when (snapshot.status) {
                        CollectionBatchStatus.SUCCESS -> "Batch completado: ${snapshot.downloadedGames} ROMs descargadas · ${snapshot.alreadyAvailable} ya locales"
                        CollectionBatchStatus.PARTIAL -> "Batch finalizado: ${snapshot.downloadedGames} descargadas · ${snapshot.failedCount} fallos · ${snapshot.needsConfirmationCount} por revisar"
                        CollectionBatchStatus.FAILED -> snapshot.error ?: "El batch download no pudo completarse"
                        CollectionBatchStatus.CANCELLED -> "Batch download cancelado"
                        else -> snapshot.stage
                    }
                    messages.emit(resultText)
                }
            }
        }
    }

    fun syncSelectedCollectionArtwork() {
        val id = _selectedCollectionId.value ?: return
        if (_artworkSyncUi.value.running) return
        val compactIcons = preferences.gameTileStyle == GameTileStyle.COMPACT_ICON
        scope.launch {
            _artworkSyncUi.value = CollectionArtworkSyncUi(
                running = true,
                stage = if (compactIcons) "Preparando SteamGridDB…" else "Preparando artwork…",
            )
            runCatching {
                if (compactIcons) {
                    val result = SteamGridDbIconSyncManager(application, preferences, database).sync(id) { progress ->
                        _artworkSyncUi.value = CollectionArtworkSyncUi(
                            running = true,
                            stage = progress.stage,
                            gameTitle = progress.gameTitle,
                            completed = progress.completed,
                            total = progress.total,
                            resolved = progress.resolved,
                            missing = progress.missing,
                        )
                    }
                    Triple(result.total, result.resolved, result.missing)
                } else {
                    val result = CollectionArtworkSyncManager(application, preferences, database).sync(id) { progress ->
                        _artworkSyncUi.value = CollectionArtworkSyncUi(
                            running = true,
                            stage = progress.stage,
                            gameTitle = progress.gameTitle,
                            completed = progress.completed,
                            total = progress.total,
                            resolved = progress.resolved,
                            missing = progress.missing,
                        )
                    }
                    Triple(result.total, result.resolved, result.missing)
                }
            }.onSuccess { result ->
                val (total, resolved, missing) = result
                _artworkSyncUi.value = CollectionArtworkSyncUi(
                    running = false,
                    completed = total,
                    total = total,
                    resolved = resolved,
                    missing = missing,
                    syncedAt = System.currentTimeMillis(),
                )
                val label = if (compactIcons) "SteamGridDB" else "Artwork Libretro"
                messages.emit("$label: $resolved/$total imágenes disponibles${if (missing > 0) " · $missing sin coincidencia" else ""}")
            }.onFailure { error ->
                _artworkSyncUi.value = CollectionArtworkSyncUi()
                messages.emit(error.message ?: "No se pudo sincronizar el arte de la colección")
            }
        }
    }

    fun syncAllArtwork(includePcHub: Boolean = true) {
        if (_artworkSyncUi.value.running) return
        val useSteamGrid = preferences.gameTileStyle == GameTileStyle.COMPACT_ICON &&
            preferences.steamGridDbApiKey.trim().isNotBlank()
        scope.launch {
            _artworkSyncUi.value = CollectionArtworkSyncUi(
                running = true,
                stage = if (useSteamGrid) "Escaneando iconos SteamGridDB…" else "Buscando carátulas faltantes…",
            )
            runCatching {
                val targetGames = withContext(Dispatchers.IO) {
                    val dao = database.gameDao()
                    val pool = (dao.downloadedOnce() + dao.recentOnce() + repository.sagaCandidatesOnce().take(50))
                        .distinctBy { it.id }
                    if (pool.isNotEmpty()) pool else repository.moodCandidatesOnce().take(60)
                }
                require(targetGames.isNotEmpty()) { "No hay juegos indexados todavía para sincronizar carátulas." }
                val res = if (useSteamGrid) {
                    val out = SteamGridDbIconSyncManager(application, preferences, database).syncGames(targetGames) { progress ->
                        _artworkSyncUi.value = CollectionArtworkSyncUi(
                            running = true,
                            stage = progress.stage,
                            gameTitle = progress.gameTitle,
                            completed = progress.completed,
                            total = progress.total,
                            resolved = progress.resolved,
                            missing = progress.missing,
                        )
                    }
                    Triple(out.total, out.resolved, out.missing)
                } else {
                    val out = CollectionArtworkSyncManager(application, preferences, database).syncGames(
                        games = targetGames,
                        onlyMissing = true,
                    ) { progress ->
                        _artworkSyncUi.value = CollectionArtworkSyncUi(
                            running = true,
                            stage = progress.stage,
                            gameTitle = progress.gameTitle,
                            completed = progress.completed,
                            total = progress.total,
                            resolved = progress.resolved,
                            missing = progress.missing,
                        )
                    }
                    Triple(out.total, out.resolved, out.missing)
                }
                var hubNote = ""
                val hubUrl = pcHubUrlProvider()
                if (includePcHub && hubUrl.isNotBlank()) {
                    runCatching { GameVaultHubClient.triggerCoverSync(hubUrl) }
                        .onSuccess { hubNote = " · PC Hub sincronizado" }
                }
                res to hubNote
            }.onSuccess { (counts, hubNote) ->
                val (total, resolved, missing) = counts
                _artworkSyncUi.value = CollectionArtworkSyncUi(
                    running = false,
                    completed = total,
                    total = total,
                    resolved = resolved,
                    missing = missing,
                    syncedAt = System.currentTimeMillis(),
                )
                val suffix = if (missing > 0) " · $missing sin coincidencia" else ""
                messages.emit("Carátulas sincronizadas: $resolved/$total listas$suffix$hubNote")
            }.onFailure { error ->
                _artworkSyncUi.value = CollectionArtworkSyncUi()
                messages.emit(error.message ?: "No se pudieron sincronizar las carátulas")
            }
        }
    }
}

class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as? OzApplication)?.database ?: OzDatabase.get(application)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val preferences = AppPreferences(application)
    private val repository = LibraryRepository(db)
    private val collectionSummaries = repository.collections()
        .map { repository.collectionSummaries() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val domain = CatalogDomainHolder(
        application = application,
        scope = viewModelScope,
        database = db,
        repository = repository,
        catalog = LibretroCatalogRepository(application, db),
        preferences = preferences,
        collectionSummaries = collectionSummaries,
        pcHubUrlProvider = { preferences.pcHubUrl },
        messages = messages,
    )

    val systems = domain.systems
    val sourceIndexCatalogSystems = domain.sourceIndexCatalogSystems
    val catalogQuery = domain.catalogQuery
    val catalogPlatformFilter = domain.catalogPlatformFilter
    val catalogSearchResults = domain.catalogSearchResults
    val catalogSearching = domain.catalogSearching
    val catalogShardCount = domain.catalogShardCount
    val catalogFilters = domain.catalogFilters
    val catalogPreparation = domain.catalogPreparation
    val catalogGames = domain.catalogGames
    val selectedCollection = domain.selectedCollection
    val collectionGames = domain.collectionGames
    val libraryGames = domain.libraryGames
    val downloadUi = domain.downloadUi
    val artworkSyncUi = domain.artworkSyncUi

    fun setCatalogQuery(value: String) = domain.setCatalogQuery(value)
    fun setCatalogPlatformFilter(platform: String) = domain.setCatalogPlatformFilter(platform)
    fun clearCatalogSearch() = domain.clearCatalogSearch()
    fun setCatalogFilters(value: CatalogFilterState) = domain.setCatalogFilters(value)
    fun prepareCompleteCatalog() = domain.prepareCompleteCatalog()
    fun rebuildCompleteCatalog() = domain.rebuildCompleteCatalog()
    fun cancelCatalogPreparation() = domain.cancelCatalogPreparation()
    fun openCollection(id: String) = domain.openCollection(id)
    fun setLibrarySection(value: LibrarySection) = domain.setLibrarySection(value)
    fun setLibraryPlatform(systemId: String?) = domain.setLibraryPlatform(systemId)

    companion object {
        fun factory(application: OzApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CatalogViewModel(application) as T
        }
    }
}

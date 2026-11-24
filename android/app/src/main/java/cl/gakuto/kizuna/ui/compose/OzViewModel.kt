package cl.gakuto.kizuna.ui.compose

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.auth.InternetArchiveAuthManager
import cl.gakuto.kizuna.catalog.CatalogSystem
import cl.gakuto.kizuna.catalog.CatalogSystemRouting
import cl.gakuto.kizuna.catalog.DynamicSmartList
import cl.gakuto.kizuna.catalog.DynamicSmartListsEngine
import cl.gakuto.kizuna.catalog.GameDescriptions
import cl.gakuto.kizuna.catalog.GameMood
import cl.gakuto.kizuna.catalog.GameSynopsis
import cl.gakuto.kizuna.catalog.LibretroCatalogRepository
import cl.gakuto.kizuna.catalog.MoodPickerEngine
import cl.gakuto.kizuna.catalog.CatalogSearchItem
import cl.gakuto.kizuna.catalog.VaultCatalogSearchEngine
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.AppOrientationMode
import cl.gakuto.kizuna.data.GameGridDensity
import cl.gakuto.kizuna.data.CollectionKind
import cl.gakuto.kizuna.data.CollectionSummary
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameTileStyle
import cl.gakuto.kizuna.data.GameListSpec
import cl.gakuto.kizuna.data.LibraryOrigin
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.ftp.FtpBackgroundManager
import cl.gakuto.kizuna.ftp.FtpTargetConfig
import cl.gakuto.kizuna.ftp.FtpTransferJob
import cl.gakuto.kizuna.hub.GameVaultHubClient
import cl.gakuto.kizuna.hub.HubRemoteGame
import cl.gakuto.kizuna.integration.IisuExporter
import cl.gakuto.kizuna.patcher.PatchSearchClient
import cl.gakuto.kizuna.patcher.PatchedRomRecord
import cl.gakuto.kizuna.patcher.RomPatcher
import cl.gakuto.kizuna.patcher.SpanishPatchCandidate
import cl.gakuto.kizuna.sagas.SagaAiAuditEngine
import cl.gakuto.kizuna.sagas.SagaAiAuditReport
import cl.gakuto.kizuna.sagas.SagaSnapshot
import cl.gakuto.kizuna.sagas.SagaSummaryRef
import cl.gakuto.kizuna.sagas.SagasRepository
import cl.gakuto.kizuna.launch.CollectionBatchStatus
import cl.gakuto.kizuna.launch.CollectionBatchTaskStore
import cl.gakuto.kizuna.catalog.background.CatalogPreparationScheduler
import cl.gakuto.kizuna.catalog.background.CatalogPreparationStatus
import cl.gakuto.kizuna.catalog.background.CatalogPreparationTaskStore
import cl.gakuto.kizuna.catalog.background.catalogPreparationElapsedMillis
import cl.gakuto.kizuna.catalog.background.isCatalogPreparationStale
import cl.gakuto.kizuna.launch.CollectionDownloadScheduler
import cl.gakuto.kizuna.launch.CollectionArtworkSyncManager
import cl.gakuto.kizuna.launch.SteamGridDbIconSyncManager
import cl.gakuto.kizuna.launch.PersistentRomLibrary
import cl.gakuto.kizuna.util.CatalogFilterState
import cl.gakuto.kizuna.util.CatalogSort
import cl.gakuto.kizuna.util.OriginStatusFilter
import cl.gakuto.kizuna.util.PlatformFamily
import cl.gakuto.kizuna.util.TextNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibrarySection(val label: String) {
    ALL("Todos"),
    DOWNLOADED("Descargados"),
    IMPORTED("Importados"),
    PLATFORM("Plataforma"),
    RECENT("Últimos jugados"),
}

data class CollectionDownloadUi(
    val running: Boolean = false,
    val stage: String = "",
    val gameTitle: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
)

data class CollectionArtworkSyncUi(
    val running: Boolean = false,
    val stage: String = "",
    val gameTitle: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val resolved: Int = 0,
    val missing: Int = 0,
    val syncedAt: Long = 0L,
)

data class CatalogPreparationUi(
    val running: Boolean = false,
    val readySystems: Int = 0,
    val totalSystems: Int = 0,
    val gameCount: Int = 0,
    val currentSystem: String = "",
    val stage: String = "",
    val failures: Int = 0,
    val elapsedMillis: Long = 0L,
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OzViewModel(application: Application) : AndroidViewModel(application) {
    private val oz = application as OzApplication
    private val repository = LibraryRepository(oz.database)
    private val catalog = LibretroCatalogRepository(application, oz.database)
    val preferences = AppPreferences(application)
    private val _appThemeMode = MutableStateFlow(preferences.appThemeMode)
    val appThemeMode: StateFlow<AppThemeMode> = _appThemeMode
    private val _appOrientationMode = MutableStateFlow(preferences.appOrientationMode)
    val appOrientationMode: StateFlow<AppOrientationMode> = _appOrientationMode
    private val _gameGridDensity = MutableStateFlow(preferences.gameGridDensity)
    val gameGridDensity: StateFlow<GameGridDensity> = _gameGridDensity

    val systems: List<CatalogSystem> = catalog.systems()
    val sourceIndexCatalogSystems: List<CatalogSystem> = CatalogSystemRouting.eligibleSourceIndexSystems(systems)
    private val knownSystemIds = systems.mapTo(linkedSetOf()) { it.id }

    val recent: StateFlow<List<GameEntity>> = repository.recentHome(14)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites: StateFlow<List<GameEntity>> = repository.favoriteHome(14)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val local: StateFlow<List<GameEntity>> = repository.localHome(14)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val continuePlaying: StateFlow<List<GameEntity>> = recent
        .map { games -> games.filter { !it.contentUri.isNullOrBlank() }.take(8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collectionSummaries: StateFlow<List<CollectionSummary>> = combine(
        repository.collections().distinctUntilChanged(),
        repository.catalogRevisionPulse(),
        repository.localCountPulse().distinctUntilChanged(),
        repository.lastPlayedPulse().distinctUntilChanged(),
        repository.membershipCountPulse().distinctUntilChanged(),
    ) { _, _, _, _, _ -> Unit }
        .conflate()
        .map { repository.collectionSummaries() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val featuredCollections: StateFlow<List<CollectionSummary>> = collectionSummaries
        .map { list ->
            list.filter {
                it.collection.collectionKind == CollectionKind.CURATED ||
                it.collection.collectionKind == CollectionKind.AUTOMATIC ||
                it.collection.collectionKind == CollectionKind.SYSTEM
            }.take(8)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    init {
        viewModelScope.launch {
            val loadedCount = VaultCatalogSearchEngine.ensureLoaded(application)
            _catalogShardCount.value = loadedCount
            runCatalogSearch(_catalogQuery.value, _catalogPlatformFilter.value)
        }
    }

    val dynamicSmartLists: StateFlow<List<DynamicSmartList>> = combine(
        recent,
        favorites,
        local,
    ) { rec, fav, loc -> Triple(rec, fav, loc) }
        .conflate()
        .map { (rec, fav, loc) ->
            val sample = repository.catalogSampleForSmartLists(420)
            DynamicSmartListsEngine.buildSmartLists(rec, fav, loc, sample)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _catalogPreparationRun = MutableStateFlow(CatalogPreparationUi(totalSystems = systems.size))
    val catalogPreparation: StateFlow<CatalogPreparationUi> = combine(
        _catalogPreparationRun,
        oz.database.catalogStateDao().observeAll(),
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
        viewModelScope,
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
    }.flatMapLatest(repository::pagedGames).cachedIn(viewModelScope)

    private val _selectedCollectionId = MutableStateFlow<String?>(null)
    private val _collectionQuery = MutableStateFlow("")
    private val _collectionFilters = MutableStateFlow(CatalogFilterState())
    val collectionQuery: StateFlow<String> = _collectionQuery
    val collectionFilters: StateFlow<CatalogFilterState> = _collectionFilters

    val selectedCollection: StateFlow<CollectionSummary?> = combine(collectionSummaries, _selectedCollectionId) { summaries, id ->
        summaries.firstOrNull { it.collection.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        if (spec.collectionId == null) kotlinx.coroutines.flow.flowOf(PagingData.empty()) else repository.pagedGames(spec)
    }.cachedIn(viewModelScope)

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
    }.flatMapLatest(repository::pagedGames).cachedIn(viewModelScope)

    private val _downloadUi = MutableStateFlow(CollectionDownloadUi())
    val downloadUi: StateFlow<CollectionDownloadUi> = _downloadUi
    private val _artworkSyncUi = MutableStateFlow(CollectionArtworkSyncUi())
    val artworkSyncUi: StateFlow<CollectionArtworkSyncUi> = _artworkSyncUi
    private var collectionBatchWatchJob: Job? = null
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val _selectedGame = MutableStateFlow<GameEntity?>(null)
    val selectedGame: StateFlow<GameEntity?> = _selectedGame
    private val _selectedSynopsis = MutableStateFlow<GameSynopsis?>(null)
    val selectedSynopsis: StateFlow<GameSynopsis?> = _selectedSynopsis
    private val _selectedPatchedRom = MutableStateFlow<AppPreferences.PatchedRomMeta?>(null)
    val selectedPatchedRom: StateFlow<AppPreferences.PatchedRomMeta?> = _selectedPatchedRom
    private val _patchCandidates = MutableStateFlow<List<SpanishPatchCandidate>>(emptyList())
    val patchCandidates: StateFlow<List<SpanishPatchCandidate>> = _patchCandidates
    private val _isSearchingPatches = MutableStateFlow(false)
    val isSearchingPatches: StateFlow<Boolean> = _isSearchingPatches
    private val _isApplyingPatch = MutableStateFlow(false)
    val isApplyingPatch: StateFlow<Boolean> = _isApplyingPatch

    val sagas: StateFlow<List<SagaSnapshot>> = combine(
        repository.catalogRevisionPulse(),
        repository.localCountPulse().distinctUntilChanged(),
        repository.lastPlayedPulse().distinctUntilChanged(),
    ) { _, _, _ -> Unit }
        .conflate()
        .map { SagasRepository.buildSagas(repository.sagaCandidatesOnce()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _autoLaunchAfterDownload = MutableStateFlow(preferences.autoLaunchAfterDownload)
    val autoLaunchAfterDownload: StateFlow<Boolean> = _autoLaunchAfterDownload
    private val _retroAutoInstallCores = MutableStateFlow(preferences.retroArchAutoInstallCores)
    val retroAutoInstallCores: StateFlow<Boolean> = _retroAutoInstallCores

    val ftpJobs: StateFlow<List<FtpTransferJob>> = FtpBackgroundManager.jobs
    private val _ftpTargets = MutableStateFlow(FtpTargetConfig.listFromJson(preferences.ftpTargetsJson))
    val ftpTargets: StateFlow<List<FtpTargetConfig>> = _ftpTargets

    private val _pcHubUrl = MutableStateFlow(preferences.pcHubUrl)
    val pcHubUrl: StateFlow<String> = _pcHubUrl
    private val _pcHubStatus = MutableStateFlow("")
    val pcHubStatus: StateFlow<String> = _pcHubStatus
    private val _pcHubGames = MutableStateFlow<List<HubRemoteGame>>(emptyList())
    val pcHubGames: StateFlow<List<HubRemoteGame>> = _pcHubGames

    private val _moodPicks = MutableStateFlow<List<GameEntity>>(emptyList())
    val moodPicks: StateFlow<List<GameEntity>> = _moodPicks

    private val internetArchiveAuth = InternetArchiveAuthManager(application)
    private val _internetArchiveState = MutableStateFlow(internetArchiveAuth.state())
    val internetArchiveState: StateFlow<InternetArchiveAuthManager.State> = _internetArchiveState
    private val _isValidatingIA = MutableStateFlow(false)
    val isValidatingIA: StateFlow<Boolean> = _isValidatingIA

    private val _artworkCandidates = MutableStateFlow<List<cl.gakuto.kizuna.launch.SteamGridDbClient.Asset>>(emptyList())
    val artworkCandidates: StateFlow<List<cl.gakuto.kizuna.launch.SteamGridDbClient.Asset>> = _artworkCandidates
    private val _isFetchingArtwork = MutableStateFlow(false)
    val isFetchingArtwork: StateFlow<Boolean> = _isFetchingArtwork

    private val _geminiApiKey = MutableStateFlow(preferences.geminiApiKey)
    val geminiApiKey: StateFlow<String> = _geminiApiKey
    private val _geminiModel = MutableStateFlow(preferences.geminiModel)
    val geminiModel: StateFlow<String> = _geminiModel

    private val _sagaAuditReport = MutableStateFlow<SagaAiAuditReport?>(null)
    val sagaAuditReport: StateFlow<SagaAiAuditReport?> = _sagaAuditReport
    private val _isAuditingSaga = MutableStateFlow(false)
    val isAuditingSaga: StateFlow<Boolean> = _isAuditingSaga

    fun setGeminiApiKey(key: String) {
        preferences.geminiApiKey = key.trim()
        _geminiApiKey.value = preferences.geminiApiKey
        viewModelScope.launch {
            messages.emit(if (preferences.geminiApiKey.isNotBlank()) "API key de Gemini guardada" else "Gemini en modo local (sin API)")
        }
    }

    fun setGeminiModel(model: String) {
        preferences.geminiModel = model
        _geminiModel.value = preferences.geminiModel
    }

    fun clearSagaAudit() {
        _sagaAuditReport.value = null
    }

    fun auditSagaWithAi(saga: SagaSnapshot) {
        if (_isAuditingSaga.value) return
        viewModelScope.launch {
            _isAuditingSaga.value = true
            runCatching {
                SagaAiAuditEngine.audit(saga, preferences)
            }.onSuccess { report ->
                _sagaAuditReport.value = report
            }.onFailure { err ->
                _sagaAuditReport.value = SagaAiAuditEngine.localAudit(saga)
                messages.emit(err.message ?: "Auditoría completada en modo local")
            }
            _isAuditingSaga.value = false
        }
    }


    fun setAppThemeMode(mode: AppThemeMode) {
        if (_appThemeMode.value == mode) return
        preferences.appThemeMode = mode
        _appThemeMode.value = mode
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
    }

    fun setAppOrientationMode(mode: AppOrientationMode) {
        preferences.appOrientationMode = mode
        _appOrientationMode.value = mode
    }

    fun setGameGridDensity(value: GameGridDensity) {
        preferences.gameGridDensity = value
        _gameGridDensity.value = value
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
        catalogOnlineSearchJob = viewModelScope.launch {
            val total = VaultCatalogSearchEngine.ensureLoaded(getApplication())
            if (_catalogShardCount.value == 0) _catalogShardCount.value = total

            if (q.length < 2) {
                // Platform filter selected without text query: show instant retail highlights for that platform!
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
                    VaultCatalogSearchEngine.searchOnline(q, platform, _pcHubUrl.value)
                }.getOrDefault(emptyList())
                if (isActive) {
                    val merged = (localHits + remoteHits).distinctBy { it.id }
                    _catalogSearchResults.value = merged
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
        catalogRebuildJob = viewModelScope.launch {
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
        CatalogPreparationScheduler.cancel(getApplication())
    }

    private fun prepareCatalogSystems(requestedSystemIds: Set<String>, completeCatalog: Boolean) {
        if (requestedSystemIds.isEmpty() || _catalogPreparationRun.value.running) return
        val targets = systems.filter { it.id in requestedSystemIds }
        if (targets.isEmpty()) return

        runCatching {
            CatalogPreparationScheduler.schedule(
                context = getApplication(),
                systemIds = targets.map { it.id },
                completeCatalog = completeCatalog,
            )
        }.onSuccess { scheduled ->
            _catalogPreparationRun.value = _catalogPreparationRun.value.copy(
                running = scheduled.snapshot.isActive,
                currentSystem = scheduled.snapshot.currentSystem,
                stage = scheduled.snapshot.stage,
                failures = scheduled.snapshot.failedCount,
                elapsedMillis = catalogPreparationElapsedMillis(
                    scheduled.snapshot,
                    System.currentTimeMillis(),
                ),
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

    private fun observeCatalogPreparationTask() {
        catalogPreparationWatchJob?.cancel()
        catalogPreparationWatchJob = viewModelScope.launch {
            val store = CatalogPreparationTaskStore(getApplication())
            var observedActive = false
            var lastUpdatedAt = Long.MIN_VALUE
            while (isActive) {
                var snapshot = store.get()
                if (
                    snapshot != null &&
                    isCatalogPreparationStale(snapshot, System.currentTimeMillis())
                ) {
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
                        elapsedMillis = catalogPreparationElapsedMillis(
                            snapshot,
                            System.currentTimeMillis(),
                        ),
                    )
                    if (snapshotChanged && observedActive && !snapshot.isActive) {
                        val message = when (snapshot.status) {
                            CatalogPreparationStatus.SUCCESS,
                            CatalogPreparationStatus.PARTIAL -> snapshot.stage
                            CatalogPreparationStatus.FAILED -> snapshot.error ?: snapshot.stage
                            CatalogPreparationStatus.CANCELLED -> "Catálogo pausado; puedes continuarlo cuando quieras"
                            CatalogPreparationStatus.SUSPENDED -> snapshot.stage
                            CatalogPreparationStatus.QUEUED,
                            CatalogPreparationStatus.RUNNING -> snapshot.stage
                        }
                        if (snapshot.completeCatalog) messages.emit(message)
                        observedActive = false
                    }
                }
                delay(700)
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

    fun openGame(gameId: String) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                repository.game(gameId)?.let { repository.artworkVariantFor(it) }
            }
            _selectedGame.value = loaded
            _selectedPatchedRom.value = loaded?.let { preferences.patchedRom(it.id) }
            _patchCandidates.value = emptyList()
            _selectedSynopsis.value = null
            if (loaded != null) {
                _selectedSynopsis.value = GameDescriptions.resolve(loaded, preferences)
            }
        }
    }

    fun hasPatchedRom(gameId: String): Boolean = preferences.patchedRom(gameId) != null

    fun setAutoLaunchAfterDownload(enabled: Boolean) {
        preferences.autoLaunchAfterDownload = enabled
        _autoLaunchAfterDownload.value = enabled
    }

    fun setRetroAutoInstallCores(enabled: Boolean) {
        preferences.retroArchAutoInstallCores = enabled
        _retroAutoInstallCores.value = enabled
    }

    fun rollMoodPicks(mood: GameMood, offset: Int) {
        viewModelScope.launch {
            val pool = withContext(Dispatchers.IO) { repository.moodCandidatesOnce() }
            _moodPicks.value = MoodPickerEngine.pick(pool, mood, offset, limit = 3)
        }
    }

    fun sagaForGame(game: GameEntity?): SagaSummaryRef? {
        game ?: return null
        return SagasRepository.findSagaForGame(game, sagas.value)
    }

    fun publishSagaToIisu(saga: SagaSnapshot) {
        viewModelScope.launch {
            val games = saga.entries.mapNotNull { it.game }.distinctBy { it.id }
            if (games.isEmpty()) {
                messages.emit("Prepara el catálogo de esos sistemas antes de publicar esta saga en iiSU.")
                return@launch
            }
            val hasTarget = preferences.globalVirtualTreeUri != null
            if (!hasTarget) {
                messages.emit("Primera vez: Configuración → Emulación e integraciones → iiSU → Elegir carpeta de puente.")
                return@launch
            }
            runCatching {
                preferences.iisuIntegrationEnabled = true
                withContext(Dispatchers.IO) { IisuExporter(getApplication()).exportConfigured(preferences, games) }
            }.onSuccess { result ->
                preferences.recordIisuSync(result.descriptors, result.systems)
                messages.emit("iiSU · Saga ${saga.name}: ${result.descriptors} accesos publicados (${result.systems} sistemas).")
            }.onFailure { err ->
                messages.emit(err.message ?: "No se pudo publicar la saga en iiSU")
            }
        }
    }

    fun publishGameToIisu(game: GameEntity) {
        viewModelScope.launch {
            if (preferences.globalVirtualTreeUri == null) {
                messages.emit("Primera vez: Configuración → Emulación e integraciones → iiSU → Elegir carpeta de puente.")
                return@launch
            }
            runCatching {
                preferences.iisuIntegrationEnabled = true
                withContext(Dispatchers.IO) { IisuExporter(getApplication()).exportConfigured(preferences, listOf(game)) }
            }.onSuccess { res ->
                preferences.recordIisuSync(res.descriptors, res.systems)
                messages.emit("iiSU: ${game.title} publicado en tu frontend.")
            }.onFailure { err ->
                messages.emit(err.message ?: "No se pudo publicar el juego en iiSU")
            }
        }
    }

    fun sendGameViaFtp(game: GameEntity, target: FtpTargetConfig, usePatched: Boolean = false) {
        viewModelScope.launch {
            val patchedFile = if (usePatched) preferences.patchedRom(game.id)?.let { java.io.File(it.path) } else null
            runCatching {
                FtpBackgroundManager.enqueueGameTransfer(
                    context = getApplication(),
                    preferences = preferences,
                    game = game,
                    target = target,
                    patchedLocalFile = patchedFile,
                )
            }.onSuccess {
                messages.emit("FTP en segundo plano: enviando ${game.title} a ${target.name}…")
            }.onFailure { err ->
                messages.emit(err.message ?: "No se pudo iniciar transferencia FTP")
            }
        }
    }

    fun saveFtpTargets(targets: List<FtpTargetConfig>) {
        preferences.ftpTargetsJson = FtpTargetConfig.listToJson(targets)
        _ftpTargets.value = targets
        messages.tryEmit("Consolas FTP guardadas")
    }

    fun testFtpTarget(target: FtpTargetConfig) {
        viewModelScope.launch {
            val (ok, msg) = FtpBackgroundManager.testTarget(target)
            messages.emit(if (ok) "✓ $msg" else "✗ $msg")
        }
    }

    fun savePcHubUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        preferences.pcHubUrl = clean
        _pcHubUrl.value = clean
    }

    fun testAndSyncPcHub(url: String = _pcHubUrl.value) {
        viewModelScope.launch {
            savePcHubUrl(url)
            _pcHubStatus.value = "Conectando con Game Vault PC…"
            runCatching {
                val health = GameVaultHubClient.testHealth(url)
                val items = GameVaultHubClient.fetchLibrary(url)
                health to items
            }.onSuccess { (health, items) ->
                _pcHubGames.value = items
                _pcHubStatus.value = "Conectado · ${health.romsCount} ROMs · ${health.cachedCovers} carátulas"
            }.onFailure { err ->
                _pcHubStatus.value = "✗ ${err.message ?: "Sin respuesta del servidor"}"
            }
        }
    }

    fun searchSpanishPatches(game: GameEntity) {
        viewModelScope.launch {
            _isSearchingPatches.value = true
            runCatching { PatchSearchClient.searchSpanishPatches(game.title) }
                .onSuccess { list ->
                    _patchCandidates.value = list
                    if (list.isEmpty()) messages.emit("No se encontraron parches automáticos; puedes elegir un .ips/.bps local.")
                }
                .onFailure { err -> messages.emit(err.message ?: "Error buscando parches") }
            _isSearchingPatches.value = false
        }
    }

    fun applyLocalPatchUri(game: GameEntity, patchUri: android.net.Uri, label: String = "") {
        viewModelScope.launch {
            _isApplyingPatch.value = true
            runCatching {
                val name = patchUri.lastPathSegment?.substringAfterLast('/') ?: "patch.bps"
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(patchUri)?.use { it.readBytes() }
                        ?: error("No se pudo leer el archivo de parche.")
                }
                withContext(Dispatchers.IO) {
                    RomPatcher.applyPatchToGame(getApplication(), preferences, game, bytes, name, label)
                }
            }.onSuccess { rec ->
                _selectedPatchedRom.value = preferences.patchedRom(game.id)
                messages.emit("✓ Parche ${rec.format} aplicado (CRC32 ${rec.crc32Hex}). Listo para jugar en [ES].")
            }.onFailure { err ->
                messages.emit(err.message ?: "Error aplicando parche")
            }
            _isApplyingPatch.value = false
        }
    }

    fun applyRemoteSpanishPatch(game: GameEntity, candidate: SpanishPatchCandidate) {
        viewModelScope.launch {
            _isApplyingPatch.value = true
            runCatching {
                val bytes = PatchSearchClient.downloadPatchBytes(candidate.patchUrl)
                withContext(Dispatchers.IO) {
                    RomPatcher.applyPatchToGame(
                        context = getApplication(),
                        preferences = preferences,
                        game = game,
                        patchBytes = bytes,
                        patchFilename = candidate.filename,
                        label = candidate.title.ifBlank { "Traducción [ES]" },
                    )
                }
            }.onSuccess { rec ->
                _selectedPatchedRom.value = preferences.patchedRom(game.id)
                messages.emit("✓ ${rec.patchedFilename} lista (${rec.format}).")
            }.onFailure { err ->
                messages.emit(err.message ?: "No se pudo aplicar el parche")
            }
            _isApplyingPatch.value = false
        }
    }

    fun clearPatchedRom(gameId: String) {
        preferences.clearPatchedRom(gameId)
        _selectedPatchedRom.value = null
        messages.tryEmit("Parche [ES] desvinculado del acceso rápido")
    }

    fun refreshInternetArchiveState(validate: Boolean = false) {
        viewModelScope.launch {
            if (validate && internetArchiveAuth.isLoggedIn()) {
                _isValidatingIA.value = true
                val result = internetArchiveAuth.validate()
                if (result != InternetArchiveAuthManager.Validation.OK) {
                    val message = when (result) {
                        InternetArchiveAuthManager.Validation.INVALID -> "Sesión de Internet Archive invalidada; vuelve a conectar."
                        InternetArchiveAuthManager.Validation.STALE -> "Error de conexión con Internet Archive; reintentando…"
                        else -> "No se pudo validar la sesión de Internet Archive."
                    }
                    messages.emit(message)
                }
                _isValidatingIA.value = false
            }
            _internetArchiveState.value = internetArchiveAuth.state()
        }
    }

    fun disconnectInternetArchive() {
        internetArchiveAuth.clear()
        _internetArchiveState.value = InternetArchiveAuthManager.State.DISCONNECTED
    }

    fun toggleFavorite(gameId: String) {
        viewModelScope.launch { repository.toggle("favorites", gameId) }
    }

    fun toggleCollection(collectionId: String, gameId: String) {
        viewModelScope.launch { repository.toggle(collectionId, gameId) }
    }

    fun markImported(gameId: String, uri: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.updateLocalState(gameId, uri, LibraryOrigin.IMPORTED) }
            _selectedGame.value = repository.game(gameId)
        }
    }

    fun deleteDownloadedRom(gameId: String) {
        viewModelScope.launch {
            val current = repository.game(gameId) ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    PersistentRomLibrary(getApplication(), preferences, oz.database).deleteDownloaded(current)
                }
            }.onSuccess { deleted ->
                if (deleted) {
                    _selectedGame.value = repository.game(gameId)
                    messages.emit("ROM eliminada de la biblioteca")
                } else {
                    messages.emit("Android no permitió eliminar la ROM. Revisa el permiso de la carpeta de biblioteca.")
                }
            }.onFailure { error ->
                messages.emit(error.message ?: "No se pudo eliminar la ROM")
            }
        }
    }

    fun downloadSelectedCollection() {
        val id = _selectedCollectionId.value ?: return
        val summary = selectedCollection.value ?: return
        val existing = CollectionBatchTaskStore(getApplication()).get(id)
        if (existing?.isActive == true) {
            observeCollectionBatch(id, announceCompletion = false)
            messages.tryEmit("El batch download de ${summary.collection.name} ya está en ejecución.")
            return
        }
        runCatching {
            CollectionDownloadScheduler.schedule(
                context = getApplication(),
                collectionId = id,
                collectionName = summary.collection.name,
                totalHint = summary.gameCount,
            )
        }.onSuccess { scheduled ->
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

    private fun observeCollectionBatch(collectionId: String, announceCompletion: Boolean) {
        collectionBatchWatchJob?.cancel()
        collectionBatchWatchJob = viewModelScope.launch {
            val store = CollectionBatchTaskStore(getApplication())
            var announced = false
            while (isActive && _selectedCollectionId.value == collectionId) {
                val snapshot = store.get(collectionId)
                if (snapshot == null) {
                    _downloadUi.value = CollectionDownloadUi()
                    return@launch
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
                if (!snapshot.isActive) {
                    if (announceCompletion && !announced) {
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
                    return@launch
                }
                delay(700)
            }
        }
    }

    fun syncSelectedCollectionArtwork() {
        val id = _selectedCollectionId.value ?: return
        if (_artworkSyncUi.value.running) return
        val compactIcons = preferences.gameTileStyle == GameTileStyle.COMPACT_ICON
        viewModelScope.launch {
            _artworkSyncUi.value = CollectionArtworkSyncUi(
                running = true,
                stage = if (compactIcons) "Preparando SteamGridDB…" else "Preparando artwork…",
            )
            runCatching {
                if (compactIcons) {
                    val result = SteamGridDbIconSyncManager(getApplication(), preferences, oz.database).sync(id) { progress ->
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
                    val result = CollectionArtworkSyncManager(getApplication(), preferences, oz.database).sync(id) { progress ->
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
                val now = System.currentTimeMillis()
                _artworkSyncUi.value = CollectionArtworkSyncUi(
                    running = false,
                    completed = total,
                    total = total,
                    resolved = resolved,
                    missing = missing,
                    syncedAt = now,
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
        viewModelScope.launch {
            _artworkSyncUi.value = CollectionArtworkSyncUi(
                running = true,
                stage = if (useSteamGrid) "Escaneando iconos SteamGridDB…" else "Buscando carátulas faltantes…",
            )
            runCatching {
                val targetGames = withContext(Dispatchers.IO) {
                    val dao = oz.database.gameDao()
                    val pool = (dao.downloadedOnce() + dao.recentOnce() + repository.sagaCandidatesOnce().take(50))
                        .distinctBy { it.id }
                    if (pool.isNotEmpty()) pool else repository.moodCandidatesOnce().take(60)
                }
                require(targetGames.isNotEmpty()) { "No hay juegos indexados todavía para sincronizar carátulas." }
                val res = if (useSteamGrid) {
                    val out = SteamGridDbIconSyncManager(getApplication(), preferences, oz.database).syncGames(targetGames) { progress ->
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
                    val out = CollectionArtworkSyncManager(getApplication(), preferences, oz.database).syncGames(
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
                if (includePcHub && _pcHubUrl.value.isNotBlank()) {
                    runCatching { GameVaultHubClient.triggerCoverSync(_pcHubUrl.value) }
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

    fun syncSelectedCollectionWithIisu() {
        val id = _selectedCollectionId.value ?: return
        viewModelScope.launch {
            runCatching {
                val games = repository.collectionGamesOnce(id)
                require(games.isNotEmpty()) { "La colección no contiene juegos." }
                val hasTarget = preferences.globalVirtualTreeUri != null
                require(hasTarget) {
                    "Primera vez: Configuración → Emulación e integraciones → iiSU → Elegir carpeta de puente. Luego importa esa carpeta como ROM folder en iiSU."
                }
                // Collection publishing intentionally includes missing ROMs. These are tiny virtual
                // descriptors: iiSU shows the game, and Oz downloads it on demand when launched.
                preferences.iisuIntegrationEnabled = true
                withContext(Dispatchers.IO) { IisuExporter(getApplication()).exportConfigured(preferences, games) }
            }.onSuccess { result ->
                preferences.recordIisuSync(result.descriptors, result.systems)
                messages.emit("iiSU: ${result.descriptors} accesos virtuales publicados · ${result.systems} sistemas. Actualiza la plataforma en iiSU.")
            }.onFailure { error ->
                messages.emit(error.message ?: "No se pudo publicar la colección en iiSU")
            }
        }
    }

    fun fetchArtworkCandidates(game: GameEntity, style: String) {
        val apiKey = preferences.steamGridDbApiKey
        if (apiKey.isBlank()) {
            viewModelScope.launch { messages.emit("Configura la API key de SteamGridDB en Ajustes") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isFetchingArtwork.value = true
            try {
                val query = cl.gakuto.kizuna.launch.SteamGridDbClient.searchableTitle(game.title)
                val matches = cl.gakuto.kizuna.launch.SteamGridDbClient.searchGames(query, apiKey)
                val steamId = matches.firstOrNull()?.id // Simplified: take first match or we could show a list
                if (steamId != null) {
                    val assets = cl.gakuto.kizuna.launch.SteamGridDbClient.fetchAssets(steamId, style, apiKey)
                    _artworkCandidates.value = assets
                } else {
                    _artworkCandidates.value = emptyList()
                    messages.emit("No se encontró el juego en SteamGridDB")
                }
            } catch (e: Exception) {
                messages.emit(e.message ?: "Error al buscar artwork")
            } finally {
                _isFetchingArtwork.value = false
            }
        }
    }

    fun setManualArtwork(gameId: String, url: String) {
        preferences.setCustomArtworkUrl(gameId, url)
        preferences.setGameArtworkMode(gameId, cl.gakuto.kizuna.data.ArtworkMode.CUSTOM)
        // Refresh selected game if it matches
        if (_selectedGame.value?.id == gameId) {
            viewModelScope.launch {
                _selectedGame.value = repository.game(gameId)?.let { repository.artworkVariantFor(it) }
            }
        }
    }

    fun restoreAutomaticArtwork(gameId: String) {
        preferences.setCustomArtworkUrl(gameId, null)
        preferences.setGameArtworkMode(gameId, null)
        if (_selectedGame.value?.id == gameId) {
            viewModelScope.launch {
                _selectedGame.value = repository.game(gameId)?.let { repository.artworkVariantFor(it) }
            }
        }
    }

    companion object {
        fun factory(application: OzApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OzViewModel(application) as T
        }
    }
}

package cl.gakuto.kizuna.ui.compose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.auth.InternetArchiveAuthManager
import cl.gakuto.kizuna.catalog.CatalogSearchItem
import cl.gakuto.kizuna.catalog.CatalogSystem
import cl.gakuto.kizuna.catalog.DynamicSmartList
import cl.gakuto.kizuna.catalog.GameMood
import cl.gakuto.kizuna.catalog.GameSynopsis
import cl.gakuto.kizuna.catalog.LibretroCatalogRepository
import cl.gakuto.kizuna.data.AppOrientationMode
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.CollectionSummary
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameGridDensity
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.ftp.FtpTargetConfig
import cl.gakuto.kizuna.ftp.FtpTransferJob
import cl.gakuto.kizuna.hub.DiscoveredLanHub
import cl.gakuto.kizuna.hub.HubRemoteGame
import cl.gakuto.kizuna.launch.SteamGridDbClient
import cl.gakuto.kizuna.patcher.SpanishPatchCandidate
import cl.gakuto.kizuna.sagas.SagaAiAuditReport
import cl.gakuto.kizuna.sagas.SagaSnapshot
import cl.gakuto.kizuna.sagas.SagaSummaryRef
import cl.gakuto.kizuna.ui.compose.domain.CatalogDomainHolder
import cl.gakuto.kizuna.ui.compose.domain.HomeDomainHolder
import cl.gakuto.kizuna.ui.compose.domain.HubSyncDomainHolder
import cl.gakuto.kizuna.ui.compose.domain.SagasDomainHolder
import cl.gakuto.kizuna.ui.compose.domain.SettingsDomainHolder
import cl.gakuto.kizuna.util.CatalogFilterState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class LibrarySection(val label: String) {
    ALL("Todos"),
    FAVORITES("Favoritos"),
    RECENT("Recientes"),
    DOWNLOADED("Descargados"),
    IMPORTED("Importados"),
    PLATFORM("Por consola"),
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

/**
 * Compatibility facade composing cohesive domain holders:
 * [HomeDomainHolder], [CatalogDomainHolder], [SagasDomainHolder], [HubSyncDomainHolder], [SettingsDomainHolder].
 */
class OzViewModel(application: Application) : AndroidViewModel(application) {
    private val database: OzDatabase = (application as? OzApplication)?.database ?: OzDatabase.get(application)
    private val repository = LibraryRepository(database)
    private val catalogRepo = LibretroCatalogRepository(application, database)
    val preferences = AppPreferences(application)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val sagasDomain = SagasDomainHolder(
        application = application,
        scope = viewModelScope,
        repository = repository,
        preferences = preferences,
        messages = messages,
    )

    val hubSyncDomain = HubSyncDomainHolder(
        application = application,
        scope = viewModelScope,
        database = database,
        preferences = preferences,
        messages = messages,
        onSyncSagasCatalog = { baseUrl -> sagasDomain.syncCatalogFromHub(baseUrl) },
    )

    val homeDomain = HomeDomainHolder(
        application = application,
        scope = viewModelScope,
        database = database,
        repository = repository,
        preferences = preferences,
        hubSyncRepository = hubSyncDomain.hubSyncRepository,
        messages = messages,
    )

    val catalogDomain = CatalogDomainHolder(
        application = application,
        scope = viewModelScope,
        database = database,
        repository = repository,
        catalog = catalogRepo,
        preferences = preferences,
        collectionSummaries = homeDomain.collectionSummaries,
        pcHubUrlProvider = { hubSyncDomain.pcHubUrl.value },
        messages = messages,
    )

    val settingsDomain = SettingsDomainHolder(
        application = application,
        scope = viewModelScope,
        repository = repository,
        preferences = preferences,
        messages = messages,
        onGameRefresh = { gameId -> homeDomain.refreshSelectedGameIfMatches(gameId) },
    )

    // Settings streams & actions
    val appThemeMode: StateFlow<AppThemeMode> = settingsDomain.appThemeMode
    val appThemePreset = settingsDomain.appThemePreset
    val appOrientationMode: StateFlow<AppOrientationMode> = settingsDomain.appOrientationMode
    val gameGridDensity: StateFlow<GameGridDensity> = settingsDomain.gameGridDensity
    val autoLaunchAfterDownload: StateFlow<Boolean> = settingsDomain.autoLaunchAfterDownload
    val retroAutoInstallCores: StateFlow<Boolean> = settingsDomain.retroAutoInstallCores
    val internetArchiveState: StateFlow<InternetArchiveAuthManager.State> = settingsDomain.internetArchiveState
    val isValidatingIA: StateFlow<Boolean> = settingsDomain.isValidatingIA
    val artworkCandidates: StateFlow<List<SteamGridDbClient.Asset>> = settingsDomain.artworkCandidates
    val isFetchingArtwork: StateFlow<Boolean> = settingsDomain.isFetchingArtwork
    val geminiApiKey: StateFlow<String> = settingsDomain.geminiApiKey
    val geminiModel: StateFlow<String> = settingsDomain.geminiModel

    fun setGeminiApiKey(key: String) = settingsDomain.setGeminiApiKey(key)
    fun setGeminiModel(model: String) = settingsDomain.setGeminiModel(model)
    fun setAppThemeMode(mode: AppThemeMode) = settingsDomain.setAppThemeMode(mode)
    fun setAppThemePreset(preset: cl.gakuto.kizuna.data.AppThemePreset) = settingsDomain.setAppThemePreset(preset)
    fun setAppOrientationMode(mode: AppOrientationMode) = settingsDomain.setAppOrientationMode(mode)
    fun setGameGridDensity(value: GameGridDensity) = settingsDomain.setGameGridDensity(value)
    fun setAutoLaunchAfterDownload(enabled: Boolean) = settingsDomain.setAutoLaunchAfterDownload(enabled)
    fun setRetroAutoInstallCores(enabled: Boolean) = settingsDomain.setRetroAutoInstallCores(enabled)
    fun refreshInternetArchiveState(validate: Boolean = false) = settingsDomain.refreshInternetArchiveState(validate)
    fun disconnectInternetArchive() = settingsDomain.disconnectInternetArchive()
    fun publishGameToIisu(game: GameEntity) = settingsDomain.publishGameToIisu(game)
    fun syncSelectedCollectionWithIisu() = settingsDomain.syncSelectedCollectionWithIisu(catalogDomain.selectedCollectionId.value)
    fun fetchArtworkCandidates(game: GameEntity, style: String) = settingsDomain.fetchArtworkCandidates(game, style)
    fun setManualArtwork(gameId: String, url: String) = settingsDomain.setManualArtwork(gameId, url)
    fun restoreAutomaticArtwork(gameId: String) = settingsDomain.restoreAutomaticArtwork(gameId)

    // Home streams & actions
    val recent: StateFlow<List<GameEntity>> = homeDomain.recent
    val favorites: StateFlow<List<GameEntity>> = homeDomain.favorites
    val local: StateFlow<List<GameEntity>> = homeDomain.local
    val continuePlaying: StateFlow<List<GameEntity>> = homeDomain.continuePlaying
    val quickResumeHero = homeDomain.quickResumeHero
    val collectionSummaries: StateFlow<List<CollectionSummary>> = homeDomain.collectionSummaries
    val featuredCollections: StateFlow<List<CollectionSummary>> = homeDomain.featuredCollections
    val dynamicSmartLists: StateFlow<List<DynamicSmartList>> = homeDomain.dynamicSmartLists
    val selectedGame: StateFlow<GameEntity?> = homeDomain.selectedGame
    val selectedSynopsis: StateFlow<GameSynopsis?> = homeDomain.selectedSynopsis
    val selectedPatchedRom: StateFlow<AppPreferences.PatchedRomMeta?> = homeDomain.selectedPatchedRom
    val patchCandidates: StateFlow<List<SpanishPatchCandidate>> = homeDomain.patchCandidates
    val isSearchingPatches: StateFlow<Boolean> = homeDomain.isSearchingPatches
    val isApplyingPatch: StateFlow<Boolean> = homeDomain.isApplyingPatch
    val moodPicks: StateFlow<List<GameEntity>> = homeDomain.moodPicks

    fun openGame(gameId: String) = homeDomain.openGame(gameId)
    fun hasPatchedRom(gameId: String): Boolean = homeDomain.hasPatchedRom(gameId)
    fun rollMoodPicks(mood: GameMood, offset: Int) = homeDomain.rollMoodPicks(mood, offset)
    fun searchSpanishPatches(game: GameEntity) = homeDomain.searchSpanishPatches(game)
    fun applyLocalPatchUri(game: GameEntity, patchUri: android.net.Uri, label: String = "") = homeDomain.applyLocalPatchUri(game, patchUri, label)
    fun applyRemoteSpanishPatch(game: GameEntity, candidate: SpanishPatchCandidate) = homeDomain.applyRemoteSpanishPatch(game, candidate)
    fun clearPatchedRom(gameId: String) = homeDomain.clearPatchedRom(gameId)
    fun toggleFavorite(gameId: String) = homeDomain.toggleFavorite(gameId)
    fun toggleCollection(collectionId: String, gameId: String) = homeDomain.toggleCollection(collectionId, gameId)
    fun markImported(gameId: String, uri: String) = homeDomain.markImported(gameId, uri)
    fun deleteDownloadedRom(gameId: String) = homeDomain.deleteDownloadedRom(gameId)

    // Catalog streams & actions
    val systems: List<CatalogSystem> = catalogDomain.systems
    val sourceIndexCatalogSystems: List<CatalogSystem> = catalogDomain.sourceIndexCatalogSystems
    val catalogQuery: StateFlow<String> = catalogDomain.catalogQuery
    val catalogPlatformFilter: StateFlow<String> = catalogDomain.catalogPlatformFilter
    val catalogSearchResults: StateFlow<List<CatalogSearchItem>> = catalogDomain.catalogSearchResults
    val catalogSearching: StateFlow<Boolean> = catalogDomain.catalogSearching
    val catalogShardCount: StateFlow<Int> = catalogDomain.catalogShardCount
    val catalogFilters: StateFlow<CatalogFilterState> = catalogDomain.catalogFilters
    val catalogPreparation: StateFlow<CatalogPreparationUi> = catalogDomain.catalogPreparation
    val catalogGames: Flow<PagingData<GameEntity>> = catalogDomain.catalogGames
    val collectionQuery: StateFlow<String> = catalogDomain.collectionQuery
    val collectionFilters: StateFlow<CatalogFilterState> = catalogDomain.collectionFilters
    val selectedCollection: StateFlow<CollectionSummary?> = catalogDomain.selectedCollection
    val collectionGames: Flow<PagingData<GameEntity>> = catalogDomain.collectionGames
    val librarySection: StateFlow<LibrarySection> = catalogDomain.librarySection
    val libraryPlatform: StateFlow<String?> = catalogDomain.libraryPlatform
    val libraryQuery: StateFlow<String> = catalogDomain.libraryQuery
    val librarySort = catalogDomain.librarySort
    val libraryGames: Flow<PagingData<GameEntity>> = catalogDomain.libraryGames
    val downloadUi: StateFlow<CollectionDownloadUi> = catalogDomain.downloadUi
    val artworkSyncUi: StateFlow<CollectionArtworkSyncUi> = catalogDomain.artworkSyncUi

    fun setCatalogQuery(value: String) = catalogDomain.setCatalogQuery(value)
    fun setCatalogPlatformFilter(platform: String) = catalogDomain.setCatalogPlatformFilter(platform)
    fun clearCatalogSearch() = catalogDomain.clearCatalogSearch()
    suspend fun materializeCatalogItem(item: CatalogSearchItem): GameEntity = catalogDomain.materializeCatalogItem(item)
    fun setCatalogFilters(value: CatalogFilterState) = catalogDomain.setCatalogFilters(value)
    fun prepareCompleteCatalog() = catalogDomain.prepareCompleteCatalog()
    fun rebuildCompleteCatalog() = catalogDomain.rebuildCompleteCatalog()
    fun cancelCatalogPreparation() = catalogDomain.cancelCatalogPreparation()
    fun openCollection(id: String) = catalogDomain.openCollection(id)
    fun setCollectionQuery(value: String) = catalogDomain.setCollectionQuery(value)
    fun setCollectionFilters(value: CatalogFilterState) = catalogDomain.setCollectionFilters(value)
    fun setLibrarySection(value: LibrarySection) = catalogDomain.setLibrarySection(value)
    fun setLibraryPlatform(systemId: String?) = catalogDomain.setLibraryPlatform(systemId)
    fun setLibraryQuery(value: String) = catalogDomain.setLibraryQuery(value)
    fun setLibrarySort(value: cl.gakuto.kizuna.util.CatalogSort) = catalogDomain.setLibrarySort(value)
    fun downloadSelectedCollection() = catalogDomain.downloadSelectedCollection()
    fun syncSelectedCollectionArtwork() = catalogDomain.syncSelectedCollectionArtwork()
    fun syncAllArtwork(includePcHub: Boolean = true) = catalogDomain.syncAllArtwork(includePcHub)

    // Sagas streams & actions
    val sagas: StateFlow<List<SagaSnapshot>> = sagasDomain.sagas
    val sagaAuditReport: StateFlow<SagaAiAuditReport?> = sagasDomain.sagaAuditReport
    val isAuditingSaga: StateFlow<Boolean> = sagasDomain.isAuditingSaga

    fun clearSagaAudit() = sagasDomain.clearSagaAudit()
    fun auditSagaWithAi(saga: SagaSnapshot) = sagasDomain.auditSagaWithAi(saga)
    fun sagaForGame(game: GameEntity?): SagaSummaryRef? = sagasDomain.sagaForGame(game)
    fun publishSagaToIisu(saga: SagaSnapshot) = sagasDomain.publishSagaToIisu(saga)

    // Hub / FTP / LAN mDNS / SSE streams & actions
    val ftpJobs: StateFlow<List<FtpTransferJob>> = hubSyncDomain.ftpJobs
    val ftpTargets: StateFlow<List<FtpTargetConfig>> = hubSyncDomain.ftpTargets
    val discoveredLanHubs: StateFlow<List<DiscoveredLanHub>> = hubSyncDomain.discoveredLanHubs
    val isScanningLanHubs: StateFlow<Boolean> = hubSyncDomain.isScanningLanHubs
    val pcHubSseConnected: StateFlow<Boolean> = hubSyncDomain.pcHubSseConnected
    val latestHubEvent = hubSyncDomain.latestHubEvent
    val pcHubUrl: StateFlow<String> = hubSyncDomain.pcHubUrl
    val pcHubStatus: StateFlow<String> = hubSyncDomain.pcHubStatus
    val pcHubGames: StateFlow<List<HubRemoteGame>> = hubSyncDomain.pcHubGames

    fun sendGameViaFtp(game: GameEntity, target: FtpTargetConfig, usePatched: Boolean = false) = hubSyncDomain.sendGameViaFtp(game, target, usePatched)
    fun saveFtpTargets(targets: List<FtpTargetConfig>) = hubSyncDomain.saveFtpTargets(targets)
    fun testFtpTarget(target: FtpTargetConfig) = hubSyncDomain.testFtpTarget(target)
    fun savePcHubUrl(url: String) = hubSyncDomain.savePcHubUrl(url)
    fun startLanHubDiscovery() = hubSyncDomain.startLanHubDiscovery()
    fun stopLanHubDiscovery() = hubSyncDomain.stopLanHubDiscovery()
    fun pairDiscoveredHub(hub: DiscoveredLanHub) = hubSyncDomain.pairDiscoveredHub(hub)
    fun testAndSyncPcHub(url: String = pcHubUrl.value) = hubSyncDomain.testAndSyncPcHub(url)

    override fun onCleared() {
        hubSyncDomain.clear()
        super.onCleared()
    }

    companion object {
        fun factory(application: OzApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OzViewModel(application) as T
        }
    }
}

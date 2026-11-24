package cl.gakuto.kizuna.ui.compose.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.catalog.DynamicSmartList
import cl.gakuto.kizuna.catalog.DynamicSmartListsEngine
import cl.gakuto.kizuna.catalog.GameDescriptions
import cl.gakuto.kizuna.catalog.GameMood
import cl.gakuto.kizuna.catalog.GameSynopsis
import cl.gakuto.kizuna.catalog.MoodPickerEngine
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.CollectionKind
import cl.gakuto.kizuna.data.CollectionSummary
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryOrigin
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.hub.HubSyncRepository
import cl.gakuto.kizuna.launch.PersistentRomLibrary
import cl.gakuto.kizuna.patcher.PatchSearchClient
import cl.gakuto.kizuna.patcher.RomPatcher
import cl.gakuto.kizuna.patcher.SpanishPatchCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quick Resume hero model built from Room v7 sync metadata (quickResumeJson) with safe fallback. */
data class QuickResumeHero(
    val game: GameEntity,
    val slot: Int? = null,
    val coreId: String? = null,
    val updatedAt: String? = null,
    val isQuickResume: Boolean = false,
)

/** Cohesive domain holder for Home shelves, mood discovery, game detail, favorites, and Spanish patches. */
class HomeDomainHolder(
    private val application: Application,
    private val scope: CoroutineScope,
    private val database: OzDatabase,
    private val repository: LibraryRepository,
    private val preferences: AppPreferences,
    private val hubSyncRepository: HubSyncRepository,
    private val messages: MutableSharedFlow<String>,
) {
    val recent: StateFlow<List<GameEntity>> = repository.recentHome(14)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites: StateFlow<List<GameEntity>> = repository.favoriteHome(14)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val local: StateFlow<List<GameEntity>> = repository.localHome(14)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val continuePlaying: StateFlow<List<GameEntity>> = recent
        .map { games -> games.filter { !it.contentUri.isNullOrBlank() }.take(8) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quickResumeHero: StateFlow<QuickResumeHero?> = combine(
        database.syncMetadataDao().observeLatestQuickResume(),
        continuePlaying,
        recent,
        favorites,
        local,
    ) { qrMeta, cont, rec, fav, loc ->
        val qrGame = qrMeta?.let { meta ->
            (cont + rec + fav + loc).firstOrNull { it.id == meta.gameId } ?: repository.game(meta.gameId)
        }
        if (qrMeta != null && qrGame != null && !qrMeta.quickResumeJson.isNullOrBlank()) {
            val parsed = runCatching { org.json.JSONObject(qrMeta.quickResumeJson) }.getOrNull()
            QuickResumeHero(
                game = qrGame,
                slot = parsed?.takeIf { it.has("slot") }?.optInt("slot"),
                coreId = parsed?.optString("coreId")?.takeIf { it.isNotBlank() },
                updatedAt = parsed?.optString("updatedAt")?.takeIf { it.isNotBlank() },
                isQuickResume = true,
            )
        } else {
            val fallback = (cont + rec + favorites.value + loc).firstOrNull()
            fallback?.let { QuickResumeHero(game = it, isQuickResume = false) }
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

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
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val featuredCollections: StateFlow<List<CollectionSummary>> = collectionSummaries
        .map { list ->
            list.filter {
                it.collection.collectionKind == CollectionKind.CURATED ||
                    it.collection.collectionKind == CollectionKind.AUTOMATIC ||
                    it.collection.collectionKind == CollectionKind.SYSTEM
            }.take(8)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private val _moodPicks = MutableStateFlow<List<GameEntity>>(emptyList())
    val moodPicks: StateFlow<List<GameEntity>> = _moodPicks

    fun openGame(gameId: String) {
        scope.launch {
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

    suspend fun refreshSelectedGameIfMatches(gameId: String) {
        if (_selectedGame.value?.id == gameId) {
            _selectedGame.value = withContext(Dispatchers.IO) {
                repository.game(gameId)?.let { repository.artworkVariantFor(it) }
            }
        }
    }

    fun hasPatchedRom(gameId: String): Boolean = preferences.patchedRom(gameId) != null

    fun rollMoodPicks(mood: GameMood, offset: Int) {
        scope.launch {
            val pool = withContext(Dispatchers.IO) { repository.moodCandidatesOnce() }
            _moodPicks.value = MoodPickerEngine.pick(pool, mood, offset, limit = 3)
        }
    }

    fun searchSpanishPatches(game: GameEntity) {
        scope.launch {
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
        scope.launch {
            _isApplyingPatch.value = true
            runCatching {
                val name = patchUri.lastPathSegment?.substringAfterLast('/') ?: "patch.bps"
                val bytes = withContext(Dispatchers.IO) {
                    application.contentResolver.openInputStream(patchUri)?.use { it.readBytes() }
                        ?: error("No se pudo leer el archivo de parche.")
                }
                withContext(Dispatchers.IO) {
                    RomPatcher.applyPatchToGame(application, preferences, game, bytes, name, label)
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
        scope.launch {
            _isApplyingPatch.value = true
            runCatching {
                val bytes = PatchSearchClient.downloadPatchBytes(candidate.patchUrl)
                withContext(Dispatchers.IO) {
                    RomPatcher.applyPatchToGame(
                        context = application,
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

    fun toggleFavorite(gameId: String) {
        scope.launch {
            repository.toggle("favorites", gameId)
            val isFav = withContext(Dispatchers.IO) {
                database.collectionDao().hasMembership("favorites", gameId)
            }
            withContext(Dispatchers.IO) {
                hubSyncRepository.recordLocalFavorite(gameId, isFav)
            }
        }
    }

    fun toggleCollection(collectionId: String, gameId: String) {
        scope.launch { repository.toggle(collectionId, gameId) }
    }

    fun markImported(gameId: String, uri: String) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.updateLocalState(gameId, uri, LibraryOrigin.IMPORTED) }
            _selectedGame.value = repository.game(gameId)
        }
    }

    fun deleteDownloadedRom(gameId: String) {
        scope.launch {
            val current = repository.game(gameId) ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    PersistentRomLibrary(application, preferences, database).deleteDownloaded(current)
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
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as? OzApplication)?.database ?: OzDatabase.get(application)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val preferences = AppPreferences(application)
    val domain = HomeDomainHolder(
        application = application,
        scope = viewModelScope,
        database = db,
        repository = LibraryRepository(db),
        preferences = preferences,
        hubSyncRepository = HubSyncRepository(db, preferences),
        messages = messages,
    )

    val recent = domain.recent
    val favorites = domain.favorites
    val local = domain.local
    val continuePlaying = domain.continuePlaying
    val quickResumeHero = domain.quickResumeHero
    val collectionSummaries = domain.collectionSummaries
    val featuredCollections = domain.featuredCollections
    val dynamicSmartLists = domain.dynamicSmartLists
    val selectedGame = domain.selectedGame
    val selectedSynopsis = domain.selectedSynopsis
    val selectedPatchedRom = domain.selectedPatchedRom
    val patchCandidates = domain.patchCandidates
    val isSearchingPatches = domain.isSearchingPatches
    val isApplyingPatch = domain.isApplyingPatch
    val moodPicks = domain.moodPicks

    fun openGame(gameId: String) = domain.openGame(gameId)
    fun hasPatchedRom(gameId: String) = domain.hasPatchedRom(gameId)
    fun rollMoodPicks(mood: GameMood, offset: Int) = domain.rollMoodPicks(mood, offset)
    fun searchSpanishPatches(game: GameEntity) = domain.searchSpanishPatches(game)
    fun clearPatchedRom(gameId: String) = domain.clearPatchedRom(gameId)
    fun toggleFavorite(gameId: String) = domain.toggleFavorite(gameId)
    fun toggleCollection(collectionId: String, gameId: String) = domain.toggleCollection(collectionId, gameId)
    fun markImported(gameId: String, uri: String) = domain.markImported(gameId, uri)
    fun deleteDownloadedRom(gameId: String) = domain.deleteDownloadedRom(gameId)

    companion object {
        fun factory(application: OzApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(application) as T
        }
    }
}

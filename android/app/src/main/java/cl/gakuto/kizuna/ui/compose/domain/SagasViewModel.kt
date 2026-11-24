package cl.gakuto.kizuna.ui.compose.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.hub.GameVaultHubClient
import cl.gakuto.kizuna.integration.IisuExporter
import cl.gakuto.kizuna.sagas.SagaAiAuditEngine
import cl.gakuto.kizuna.sagas.SagaAiAuditReport
import cl.gakuto.kizuna.sagas.SagaSnapshot
import cl.gakuto.kizuna.sagas.SagaSummaryRef
import cl.gakuto.kizuna.sagas.SagasRepository
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

/** Cohesive domain holder for Sagas timelines, chronological auditing, and iiSU saga publishing. */
class SagasDomainHolder(
    private val application: Application,
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val preferences: AppPreferences,
    private val messages: MutableSharedFlow<String>,
) {
    private val catalogSyncPulse = MutableStateFlow(0L)

    val sagas: StateFlow<List<SagaSnapshot>> = combine(
        repository.catalogRevisionPulse(),
        repository.localCountPulse().distinctUntilChanged(),
        repository.lastPlayedPulse().distinctUntilChanged(),
        catalogSyncPulse,
    ) { _, _, _, _ -> Unit }
        .conflate()
        .map { SagasRepository.buildSagas(repository.sagaCandidatesOnce()) }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sagaAuditReport = MutableStateFlow<SagaAiAuditReport?>(null)
    val sagaAuditReport: StateFlow<SagaAiAuditReport?> = _sagaAuditReport

    private val _isAuditingSaga = MutableStateFlow(false)
    val isAuditingSaga: StateFlow<Boolean> = _isAuditingSaga

    fun clearSagaAudit() {
        _sagaAuditReport.value = null
    }

    fun auditSagaWithAi(saga: SagaSnapshot) {
        if (_isAuditingSaga.value) return
        scope.launch {
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

    fun sagaForGame(game: GameEntity?): SagaSummaryRef? {
        game ?: return null
        return SagasRepository.findSagaForGame(game, sagas.value)
    }

    suspend fun syncCatalogFromHub(baseUrl: String): Int {
        val rawJson = GameVaultHubClient.fetchSagasCatalog(baseUrl)
        val count = SagasRepository.reloadFromJson(rawJson)
        if (count > 0) {
            catalogSyncPulse.value = catalogSyncPulse.value + 1L
        }
        return count
    }

    fun publishSagaToIisu(saga: SagaSnapshot) {
        scope.launch {
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
                withContext(Dispatchers.IO) { IisuExporter(application).exportConfigured(preferences, games) }
            }.onSuccess { result ->
                preferences.recordIisuSync(result.descriptors, result.systems)
                messages.emit("iiSU · Saga ${saga.name}: ${result.descriptors} accesos publicados (${result.systems} sistemas).")
            }.onFailure { err ->
                messages.emit(err.message ?: "No se pudo publicar la saga en iiSU")
            }
        }
    }
}

class SagasViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as? OzApplication)?.database ?: OzDatabase.get(application)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val domain = SagasDomainHolder(
        application = application,
        scope = viewModelScope,
        repository = LibraryRepository(db),
        preferences = AppPreferences(application),
        messages = messages,
    )

    val sagas: StateFlow<List<SagaSnapshot>> = domain.sagas
    val sagaAuditReport: StateFlow<SagaAiAuditReport?> = domain.sagaAuditReport
    val isAuditingSaga: StateFlow<Boolean> = domain.isAuditingSaga

    fun clearSagaAudit() = domain.clearSagaAudit()
    fun auditSagaWithAi(saga: SagaSnapshot) = domain.auditSagaWithAi(saga)
    fun sagaForGame(game: GameEntity?) = domain.sagaForGame(game)
    fun publishSagaToIisu(saga: SagaSnapshot) = domain.publishSagaToIisu(saga)

    companion object {
        fun factory(application: OzApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SagasViewModel(application) as T
        }
    }
}

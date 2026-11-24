package cl.gakuto.kizuna.ui.compose.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.ftp.FtpBackgroundManager
import cl.gakuto.kizuna.ftp.FtpTargetConfig
import cl.gakuto.kizuna.ftp.FtpTransferJob
import cl.gakuto.kizuna.hub.DiscoveredLanHub
import cl.gakuto.kizuna.hub.GameVaultHubClient
import cl.gakuto.kizuna.hub.HubRemoteGame
import cl.gakuto.kizuna.hub.HubSyncRepository
import cl.gakuto.kizuna.hub.LanHubDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HubSseActivity(
    val event: String,
    val summary: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

/** Cohesive domain holder for PC Hub pairing, mDNS discovery, SSE stream, delta sync, and FTP transfers. */
class HubSyncDomainHolder(
    private val application: Application,
    private val scope: CoroutineScope,
    private val database: OzDatabase,
    private val preferences: AppPreferences,
    private val messages: MutableSharedFlow<String>,
    private val onSyncSagasCatalog: (suspend (String) -> Int)? = null,
) {
    val ftpJobs: StateFlow<List<FtpTransferJob>> = FtpBackgroundManager.jobs
    private val _ftpTargets = MutableStateFlow(FtpTargetConfig.listFromJson(preferences.ftpTargetsJson))
    val ftpTargets: StateFlow<List<FtpTargetConfig>> = _ftpTargets

    private val lanHubDiscovery = LanHubDiscovery(application)
    val discoveredLanHubs: StateFlow<List<DiscoveredLanHub>> = lanHubDiscovery.discoveredHubs
    val isScanningLanHubs: StateFlow<Boolean> = lanHubDiscovery.isScanning

    val hubSyncRepository = HubSyncRepository(database, preferences)
    private var pcHubSseJob: Job? = null
    private val _pcHubSseConnected = MutableStateFlow(false)
    val pcHubSseConnected: StateFlow<Boolean> = _pcHubSseConnected
    private val _latestHubEvent = MutableStateFlow<HubSseActivity?>(null)
    val latestHubEvent: StateFlow<HubSseActivity?> = _latestHubEvent

    private val _pcHubUrl = MutableStateFlow(preferences.pcHubUrl)
    val pcHubUrl: StateFlow<String> = _pcHubUrl
    private val _pcHubStatus = MutableStateFlow("")
    val pcHubStatus: StateFlow<String> = _pcHubStatus
    private val _pcHubGames = MutableStateFlow<List<HubRemoteGame>>(emptyList())
    val pcHubGames: StateFlow<List<HubRemoteGame>> = _pcHubGames

    fun sendGameViaFtp(game: GameEntity, target: FtpTargetConfig, usePatched: Boolean = false) {
        scope.launch {
            val patchedFile = if (usePatched) preferences.patchedRom(game.id)?.let { java.io.File(it.path) } else null
            runCatching {
                FtpBackgroundManager.enqueueGameTransfer(
                    context = application,
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
        scope.launch {
            val (ok, msg) = FtpBackgroundManager.testTarget(target)
            messages.emit(if (ok) "✓ $msg" else "✗ $msg")
        }
    }

    fun savePcHubUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        preferences.pcHubUrl = clean
        _pcHubUrl.value = clean
    }

    fun startLanHubDiscovery() {
        lanHubDiscovery.startDiscovery()
    }

    fun stopLanHubDiscovery() {
        lanHubDiscovery.stopDiscovery()
    }

    fun pairDiscoveredHub(hub: DiscoveredLanHub) {
        testAndSyncPcHub(hub.baseUrl)
    }

    fun testAndSyncPcHub(url: String = _pcHubUrl.value) {
        scope.launch {
            savePcHubUrl(url)
            _pcHubStatus.value = "Conectando con Game Vault PC…"
            runCatching {
                val health = GameVaultHubClient.testHealth(url)
                val items = GameVaultHubClient.fetchLibrary(url)
                val syncSummary = withContext(Dispatchers.IO) { hubSyncRepository.syncWithHub(url) }
                withContext(Dispatchers.IO) { runCatching { onSyncSagasCatalog?.invoke(url) } }
                Triple(health, items, syncSummary)
            }.onSuccess { (health, items, syncSummary) ->
                _pcHubGames.value = items
                val syncSuffix = if (syncSummary.pushed > 0 || syncSummary.pulled > 0) {
                    " · Sync rev ${syncSummary.latestRevision} (↑${syncSummary.pushed} ↓${syncSummary.pulled})"
                } else {
                    " · Sync rev ${syncSummary.latestRevision}"
                }
                _pcHubStatus.value = "Conectado · ${health.romsCount} ROMs · ${health.cachedCovers} carátulas$syncSuffix"
                subscribeToPcHubEvents(url)
            }.onFailure { err ->
                _pcHubStatus.value = "✗ ${err.message ?: "Sin respuesta del servidor"}"
            }
        }
    }

    private fun subscribeToPcHubEvents(url: String) {
        pcHubSseJob?.cancel()
        pcHubSseJob = scope.launch {
            _pcHubSseConnected.value = true
            runCatching {
                GameVaultHubClient.streamEvents(url).collect { ev ->
                    when (ev.event) {
                        "covers.progress" -> {
                            _latestHubEvent.value = HubSseActivity("covers.progress", "Sincronizando carátulas en PC Hub")
                        }
                        "covers.complete" -> {
                            _pcHubStatus.value = "Carátulas actualizadas en PC Hub · ${ev.data.take(80)}"
                            _latestHubEvent.value = HubSseActivity("covers.complete", "Carátulas actualizadas en PC Hub")
                        }
                        "transfer.state", "transfer.progress" -> {
                            _pcHubStatus.value = "Transferencia FTP Hub actualizada"
                            _latestHubEvent.value = HubSseActivity(ev.event, "Transferencia FTP en PC Hub")
                        }
                        "download.state", "download.progress" -> {
                            _pcHubStatus.value = "Descarga PC Hub actualizada"
                            _latestHubEvent.value = HubSseActivity(ev.event, "Descarga en curso en PC Hub")
                        }
                        "sync.updated" -> {
                            withContext(Dispatchers.IO) { runCatching { hubSyncRepository.syncWithHub(url) } }
                            _latestHubEvent.value = HubSseActivity("sync.updated", "Sincronización delta completada")
                        }
                    }
                }
            }
            _pcHubSseConnected.value = false
        }
    }

    fun clear() {
        lanHubDiscovery.stopDiscovery()
        pcHubSseJob?.cancel()
    }
}

class HubSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as? OzApplication)?.database ?: OzDatabase.get(application)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val domain = HubSyncDomainHolder(
        application = application,
        scope = viewModelScope,
        database = db,
        preferences = AppPreferences(application),
        messages = messages,
    )

    val ftpJobs = domain.ftpJobs
    val ftpTargets = domain.ftpTargets
    val discoveredLanHubs = domain.discoveredLanHubs
    val isScanningLanHubs = domain.isScanningLanHubs
    val pcHubSseConnected = domain.pcHubSseConnected
    val latestHubEvent = domain.latestHubEvent
    val pcHubUrl = domain.pcHubUrl
    val pcHubStatus = domain.pcHubStatus
    val pcHubGames = domain.pcHubGames

    fun saveFtpTargets(targets: List<FtpTargetConfig>) = domain.saveFtpTargets(targets)
    fun testFtpTarget(target: FtpTargetConfig) = domain.testFtpTarget(target)
    fun savePcHubUrl(url: String) = domain.savePcHubUrl(url)
    fun startLanHubDiscovery() = domain.startLanHubDiscovery()
    fun stopLanHubDiscovery() = domain.stopLanHubDiscovery()
    fun pairDiscoveredHub(hub: DiscoveredLanHub) = domain.pairDiscoveredHub(hub)
    fun testAndSyncPcHub(url: String = pcHubUrl.value) = domain.testAndSyncPcHub(url)

    override fun onCleared() {
        domain.clear()
        super.onCleared()
    }

    companion object {
        fun factory(application: OzApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HubSyncViewModel(application) as T
        }
    }
}

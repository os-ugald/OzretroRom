package cl.gakuto.kizuna.ui.compose.domain

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.auth.InternetArchiveAuthManager
import cl.gakuto.kizuna.data.AppOrientationMode
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.AppThemePreset
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameGridDensity
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.integration.IisuExporter
import cl.gakuto.kizuna.launch.SteamGridDbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cohesive domain holder for appearance, Gemini AI, Internet Archive auth, SteamGridDB, and iiSU export. */
class SettingsDomainHolder(
    private val application: Application,
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val preferences: AppPreferences,
    private val messages: MutableSharedFlow<String>,
    private val onGameRefresh: suspend (String) -> Unit = {},
) {
    private val _appThemeMode = MutableStateFlow(preferences.appThemeMode)
    val appThemeMode: StateFlow<AppThemeMode> = _appThemeMode

    private val _appThemePreset = MutableStateFlow(preferences.appThemePreset)
    val appThemePreset: StateFlow<AppThemePreset> = _appThemePreset

    private val _appOrientationMode = MutableStateFlow(preferences.appOrientationMode)
    val appOrientationMode: StateFlow<AppOrientationMode> = _appOrientationMode

    private val _gameGridDensity = MutableStateFlow(preferences.gameGridDensity)
    val gameGridDensity: StateFlow<GameGridDensity> = _gameGridDensity

    private val _autoLaunchAfterDownload = MutableStateFlow(preferences.autoLaunchAfterDownload)
    val autoLaunchAfterDownload: StateFlow<Boolean> = _autoLaunchAfterDownload

    private val _retroAutoInstallCores = MutableStateFlow(preferences.retroArchAutoInstallCores)
    val retroAutoInstallCores: StateFlow<Boolean> = _retroAutoInstallCores

    private val internetArchiveAuth = InternetArchiveAuthManager(application)
    private val _internetArchiveState = MutableStateFlow(internetArchiveAuth.state())
    val internetArchiveState: StateFlow<InternetArchiveAuthManager.State> = _internetArchiveState
    private val _isValidatingIA = MutableStateFlow(false)
    val isValidatingIA: StateFlow<Boolean> = _isValidatingIA

    private val _artworkCandidates = MutableStateFlow<List<SteamGridDbClient.Asset>>(emptyList())
    val artworkCandidates: StateFlow<List<SteamGridDbClient.Asset>> = _artworkCandidates
    private val _isFetchingArtwork = MutableStateFlow(false)
    val isFetchingArtwork: StateFlow<Boolean> = _isFetchingArtwork

    private val _geminiApiKey = MutableStateFlow(preferences.geminiApiKey)
    val geminiApiKey: StateFlow<String> = _geminiApiKey
    private val _geminiModel = MutableStateFlow(preferences.geminiModel)
    val geminiModel: StateFlow<String> = _geminiModel

    fun setGeminiApiKey(key: String) {
        preferences.geminiApiKey = key.trim()
        _geminiApiKey.value = preferences.geminiApiKey
        scope.launch {
            messages.emit(if (preferences.geminiApiKey.isNotBlank()) "API key de Gemini guardada" else "Gemini en modo local (sin API)")
        }
    }

    fun setGeminiModel(model: String) {
        preferences.geminiModel = model
        _geminiModel.value = preferences.geminiModel
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

    fun setAppThemePreset(preset: AppThemePreset) {
        if (_appThemePreset.value == preset) return
        preferences.appThemePreset = preset
        _appThemePreset.value = preset
    }

    fun setAppOrientationMode(mode: AppOrientationMode) {
        preferences.appOrientationMode = mode
        _appOrientationMode.value = mode
    }

    fun setGameGridDensity(value: GameGridDensity) {
        preferences.gameGridDensity = value
        _gameGridDensity.value = value
    }

    fun setAutoLaunchAfterDownload(enabled: Boolean) {
        preferences.autoLaunchAfterDownload = enabled
        _autoLaunchAfterDownload.value = enabled
    }

    fun setRetroAutoInstallCores(enabled: Boolean) {
        preferences.retroArchAutoInstallCores = enabled
        _retroAutoInstallCores.value = enabled
    }

    fun refreshInternetArchiveState(validate: Boolean = false) {
        scope.launch {
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

    fun publishGameToIisu(game: GameEntity) {
        scope.launch {
            if (preferences.globalVirtualTreeUri == null) {
                messages.emit("Primera vez: Configuración → Emulación e integraciones → iiSU → Elegir carpeta de puente.")
                return@launch
            }
            runCatching {
                preferences.iisuIntegrationEnabled = true
                withContext(Dispatchers.IO) { IisuExporter(application).exportConfigured(preferences, listOf(game)) }
            }.onSuccess { res ->
                preferences.recordIisuSync(res.descriptors, res.systems)
                messages.emit("iiSU: ${game.title} publicado en tu frontend.")
            }.onFailure { err ->
                messages.emit(err.message ?: "No se pudo publicar el juego en iiSU")
            }
        }
    }

    fun syncSelectedCollectionWithIisu(selectedCollectionId: String?) {
        val id = selectedCollectionId ?: return
        scope.launch {
            runCatching {
                val games = repository.collectionGamesOnce(id)
                require(games.isNotEmpty()) { "La colección no contiene juegos." }
                val hasTarget = preferences.globalVirtualTreeUri != null
                require(hasTarget) {
                    "Primera vez: Configuración → Emulación e integraciones → iiSU → Elegir carpeta de puente. Luego importa esa carpeta como ROM folder en iiSU."
                }
                preferences.iisuIntegrationEnabled = true
                withContext(Dispatchers.IO) { IisuExporter(application).exportConfigured(preferences, games) }
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
            scope.launch { messages.emit("Configura la API key de SteamGridDB en Ajustes") }
            return
        }
        scope.launch(Dispatchers.IO) {
            _isFetchingArtwork.value = true
            try {
                val query = SteamGridDbClient.searchableTitle(game.title)
                val matches = SteamGridDbClient.searchGames(query, apiKey)
                val steamId = matches.firstOrNull()?.id
                if (steamId != null) {
                    _artworkCandidates.value = SteamGridDbClient.fetchAssets(steamId, style, apiKey)
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
        scope.launch { onGameRefresh(gameId) }
    }

    fun restoreAutomaticArtwork(gameId: String) {
        preferences.setCustomArtworkUrl(gameId, null)
        preferences.setGameArtworkMode(gameId, null)
        scope.launch { onGameRefresh(gameId) }
    }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as? OzApplication)?.database ?: OzDatabase.get(application)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val domain = SettingsDomainHolder(
        application = application,
        scope = viewModelScope,
        repository = LibraryRepository(db),
        preferences = AppPreferences(application),
        messages = messages,
    )

    val appThemeMode = domain.appThemeMode
    val appThemePreset = domain.appThemePreset
    val appOrientationMode = domain.appOrientationMode
    val gameGridDensity = domain.gameGridDensity
    val autoLaunchAfterDownload = domain.autoLaunchAfterDownload
    val retroAutoInstallCores = domain.retroAutoInstallCores
    val internetArchiveState = domain.internetArchiveState
    val isValidatingIA = domain.isValidatingIA
    val artworkCandidates = domain.artworkCandidates
    val isFetchingArtwork = domain.isFetchingArtwork
    val geminiApiKey = domain.geminiApiKey
    val geminiModel = domain.geminiModel

    fun setGeminiApiKey(key: String) = domain.setGeminiApiKey(key)
    fun setGeminiModel(model: String) = domain.setGeminiModel(model)
    fun setAppThemeMode(mode: AppThemeMode) = domain.setAppThemeMode(mode)
    fun setAppThemePreset(preset: AppThemePreset) = domain.setAppThemePreset(preset)
    fun setAppOrientationMode(mode: AppOrientationMode) = domain.setAppOrientationMode(mode)
    fun setGameGridDensity(value: GameGridDensity) = domain.setGameGridDensity(value)
    fun setAutoLaunchAfterDownload(enabled: Boolean) = domain.setAutoLaunchAfterDownload(enabled)
    fun setRetroAutoInstallCores(enabled: Boolean) = domain.setRetroAutoInstallCores(enabled)
    fun refreshInternetArchiveState(validate: Boolean = false) = domain.refreshInternetArchiveState(validate)
    fun disconnectInternetArchive() = domain.disconnectInternetArchive()

    companion object {
        fun factory(application: OzApplication): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(application) as T
        }
    }
}

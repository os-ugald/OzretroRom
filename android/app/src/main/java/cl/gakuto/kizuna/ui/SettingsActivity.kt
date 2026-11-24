package cl.gakuto.kizuna.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import cl.gakuto.kizuna.auth.InternetArchiveAuthManager
import cl.gakuto.kizuna.catalog.CatalogSystem
import cl.gakuto.kizuna.catalog.LibretroCatalogRepository
import cl.gakuto.kizuna.catalog.RomgiCatalog
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.RomRegionPreference
import cl.gakuto.kizuna.data.CatalogPerformanceMode
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.data.ArtworkMode
import cl.gakuto.kizuna.databinding.ActivitySettingsBinding
import cl.gakuto.kizuna.integration.IisuExporter
import cl.gakuto.kizuna.launch.RetroArchManager
import cl.gakuto.kizuna.launch.RetroOverlayManager
import cl.gakuto.kizuna.util.ArtworkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: AppPreferences
    private lateinit var retroArchManager: RetroArchManager
    private lateinit var internetArchiveAuth: InternetArchiveAuthManager
    private lateinit var retroOverlayManager: RetroOverlayManager
    private var systems: List<CatalogSystem> = emptyList()
    private var retroVariants: List<RetroArchManager.Variant> = emptyList()

    private val regionKeys = RomRegionPreference.entries.map(RomRegionPreference::key)
    private val regionLabels = RomRegionPreference.entries.map(RomRegionPreference::label)

    private val internetArchiveLogin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshInternetArchiveStatus()
    }

    private val globalFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri); preferences.globalVirtualTreeUri = uri; refreshUris()
    }
    private val globalRomFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri); preferences.globalRomTreeUri = uri; refreshUris()
    }
    private val systemRomFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        val system = selectedSystem() ?: return@registerForActivityResult
        persist(uri); preferences.setSystemRomTreeUri(system.id, uri); refreshUris()
    }
    private val integrationPackFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { IisuExporter(this@SettingsActivity).exportIntegrationPack(uri) } }
                .onSuccess { Toast.makeText(this@SettingsActivity, "${it.fileName} exportado", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(this@SettingsActivity, "No se pudo exportar: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    private val assetsFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri); preferences.iisuAssetsTreeUri = uri; refreshUris()
    }
    private val artworkFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri); preferences.localArtworkTreeUri = uri; ArtworkResolver.clearLocalCache(); refreshUris()
    }
    private val fbneoBiosFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri); preferences.fbneoBiosTreeUri = uri; refreshUris()
    }
    private val retroCoreFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri)
        preferences.retroArchCoreTreeUri = uri
        refreshRetroArchSection()
    }
    private val retroSystemFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri)
        preferences.retroArchSystemTreeUri = uri
        refreshRetroArchSection()
    }
    private val retroOverlayFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri)
        preferences.retroArchOverlayTreeUri = uri
        refreshRetroArchSection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferences = AppPreferences(this)
        retroArchManager = RetroArchManager(this)
        internetArchiveAuth = InternetArchiveAuthManager(this)
        retroOverlayManager = RetroOverlayManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        runCatching { setupScreen() }
            .onFailure { error ->
                Log.e(TAG, "No se pudo inicializar Configuración", error)
                Toast.makeText(this, "Configuración abrió en modo seguro: ${error.message}", Toast.LENGTH_LONG).show()
                setupSafeFallback()
            }
    }

    private fun setupScreen() {
        applyScope()
        systems = runCatching {
            LibretroCatalogRepository(this, OzDatabase.get(this)).systems()
        }.getOrElse { error ->
            Log.e(TAG, "No se pudieron leer los sistemas", error)
            emptyList()
        }

        binding.systemSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            systems.map { it.name }.ifEmpty { listOf("Sistemas no disponibles") },
        )
        binding.systemSpinner.onItemSelectedListener = SimpleItemSelectedListener {
            refreshUris()
            refreshSelectedCoreStatus()
        }
        setSystemControlsEnabled(systems.isNotEmpty())

        binding.artworkModeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ArtworkMode.entries.map { it.label },
        )
        binding.artworkModeSpinner.setSelection(ArtworkMode.entries.indexOf(preferences.artworkMode).coerceAtLeast(0))

        binding.preferredRegionSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            regionLabels,
        )
        binding.preferredRegionSpinner.setSelection(regionKeys.indexOf(preferences.preferredRegion).coerceAtLeast(0))

        binding.romgiAutoSwitch.isChecked = preferences.romgiAutoEnabled
        binding.romgiWifiOnlySwitch.isChecked = preferences.romgiWifiOnly
        binding.romgiTorrentSwitch.isChecked = preferences.romgiTorrentsEnabled
        binding.catalogPerformanceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            CatalogPerformanceMode.entries.map { it.label },
        )
        binding.catalogPerformanceSpinner.setSelection(
            CatalogPerformanceMode.entries.indexOf(preferences.catalogPerformanceMode).coerceAtLeast(0),
        )
        binding.clearRomgiCatalogButton.setOnClickListener {
            val cleared = RomgiCatalog(this).clearCache()
            Toast.makeText(this, if (cleared) "Catálogo ROMgi borrado" else "No se pudo borrar el catálogo", Toast.LENGTH_SHORT).show()
            refreshRomgiStatus()
        }

        binding.internetArchiveLoginButton.setOnClickListener {
            internetArchiveLogin.launch(Intent(this, InternetArchiveLoginActivity::class.java))
        }
        binding.internetArchiveLogoutButton.setOnClickListener {
            internetArchiveAuth.clear()
            refreshInternetArchiveStatus()
            Toast.makeText(this, "Sesión de Internet Archive cerrada", Toast.LENGTH_SHORT).show()
        }
        refreshInternetArchiveStatus()

        binding.globalVirtualFolderButton.setOnClickListener { globalFolderPicker.launch(preferences.globalVirtualTreeUri) }
        binding.globalRomFolderButton.setOnClickListener { globalRomFolderPicker.launch(preferences.globalRomTreeUri) }
        binding.systemRomFolderButton.setOnClickListener { systemRomFolderPicker.launch(selectedSystem()?.let { preferences.systemRomTreeUri(it.id) }) }
        binding.clearSystemRomButton.setOnClickListener {
            selectedSystem()?.let { preferences.setSystemRomTreeUri(it.id, null) }
            refreshUris()
        }
        binding.iisuAssetsFolderButton.setOnClickListener { assetsFolderPicker.launch(preferences.iisuAssetsTreeUri) }
        binding.iisuCommandText.text = IisuExporter.IISU_LAUNCH_COMMAND
        binding.copyIisuCommandButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Oz RetroRom iiSU", IisuExporter.IISU_LAUNCH_COMMAND))
            Toast.makeText(this, "Comando iiSU copiado", Toast.LENGTH_SHORT).show()
        }
        binding.exportIisuPackButton.setOnClickListener { integrationPackFolderPicker.launch(null) }
        binding.localArtworkFolderButton.setOnClickListener { artworkFolderPicker.launch(preferences.localArtworkTreeUri) }
        binding.fbneoBiosFolderButton.setOnClickListener { fbneoBiosFolderPicker.launch(preferences.fbneoBiosTreeUri) }
        binding.saveButton.setOnClickListener { saveAndFinish() }

        binding.retroPackageInput.setText(preferences.retroArchPackage)
        setupRetroArchSection()
        binding.deleteAfterPlaySwitch.isChecked = preferences.deleteAfterPlay
        refreshUris()
        refreshRomgiStatus()
    }

    private fun setupSafeFallback() {
        applyScope()
        binding.saveButton.setOnClickListener { saveAndFinish() }
        binding.romgiAutoSwitch.isChecked = preferences.romgiAutoEnabled
        binding.romgiWifiOnlySwitch.isChecked = preferences.romgiWifiOnly
        binding.romgiTorrentSwitch.isChecked = preferences.romgiTorrentsEnabled
        binding.catalogPerformanceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            CatalogPerformanceMode.entries.map { it.label },
        )
        binding.catalogPerformanceSpinner.setSelection(
            CatalogPerformanceMode.entries.indexOf(preferences.catalogPerformanceMode).coerceAtLeast(0),
        )
        binding.deleteAfterPlaySwitch.isChecked = preferences.deleteAfterPlay
        binding.retroPackageInput.setText(preferences.retroArchPackage)
        setupRetroArchSection()
        setSystemControlsEnabled(false)
        refreshRomgiStatus()
        binding.internetArchiveLoginButton.setOnClickListener {
            internetArchiveLogin.launch(Intent(this, InternetArchiveLoginActivity::class.java))
        }
        binding.internetArchiveLogoutButton.setOnClickListener {
            internetArchiveAuth.clear()
            refreshInternetArchiveStatus()
        }
        refreshInternetArchiveStatus()
    }

    private fun setupRetroArchSection() {
        binding.emulatorSettingsButton.setOnClickListener {
            startActivity(Intent(this, EmulatorSettingsActivity::class.java))
        }
        binding.retroAutoInstallCoresSwitch.isChecked = preferences.retroArchAutoInstallCores
        binding.retroCoreFolderButton.setOnClickListener {
            retroCoreFolderPicker.launch(preferences.retroArchCoreTreeUri)
        }
        binding.clearRetroCoreFolderButton.setOnClickListener {
            preferences.retroArchCoreTreeUri = null
            refreshRetroArchSection()
        }
        binding.retroSystemFolderButton.setOnClickListener {
            retroSystemFolderPicker.launch(preferences.retroArchSystemTreeUri)
        }
        binding.clearRetroSystemFolderButton.setOnClickListener {
            preferences.retroArchSystemTreeUri = null
            refreshRetroArchSection()
        }
        binding.retroOverlayFolderButton.setOnClickListener {
            retroOverlayFolderPicker.launch(preferences.retroArchOverlayTreeUri)
        }
        binding.downloadRetroOverlaysButton.setOnClickListener { downloadOfficialOverlays() }
        binding.detectRetroArchButton.setOnClickListener { refreshRetroArchSection() }
        binding.installOrOpenRetroArchButton.setOnClickListener {
            val packageName = effectiveRetroPackage()
            if (packageName != null && retroArchManager.isInstalled(packageName)) {
                if (!retroArchManager.openRetroArch(packageName)) {
                    Toast.makeText(this, "No se pudo abrir RetroArch", Toast.LENGTH_LONG).show()
                }
            } else {
                runCatching { retroArchManager.openOfficialInstallPage() }
                    .onFailure { Toast.makeText(this, "No se pudo abrir la página oficial: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
        binding.retroArchStorageAccessButton.setOnClickListener {
            val packageName = effectiveRetroPackage()
            if (packageName == null || !retroArchManager.isInstalled(packageName)) {
                Toast.makeText(this, "RetroArch todavía no está instalado", Toast.LENGTH_LONG).show()
            } else if (!retroArchManager.openStorageAccessSettings(packageName)) {
                Toast.makeText(this, "No se pudo abrir el permiso de archivos", Toast.LENGTH_LONG).show()
            }
        }
        binding.installSelectedCoreButton.setOnClickListener { installSelectedSystemCore() }
        refreshRetroArchSection()
    }

    private fun refreshRetroArchSection() {
        retroVariants = retroArchManager.variants()
        binding.retroArchVariantSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            retroVariants.map { variant ->
                if (variant.installed) "${variant.label} · instalado" else "${variant.label} · no instalado"
            },
        )
        val preferred = preferences.retroArchPackage
        val preferredPosition = retroVariants.indexOfFirst { it.packageName == preferred }
        val installedPosition = retroVariants.indexOfFirst { it.installed }
        val position = when {
            preferredPosition >= 0 -> preferredPosition
            installedPosition >= 0 -> installedPosition
            else -> 0
        }
        if (retroVariants.isNotEmpty()) {
            binding.retroArchVariantSpinner.setSelection(position.coerceAtLeast(0))
            retroVariants.getOrNull(position)?.let { binding.retroPackageInput.setText(it.packageName) }
        }
        binding.retroArchVariantSpinner.onItemSelectedListener = SimpleItemSelectedListener {
            val variant = retroVariants.getOrNull(binding.retroArchVariantSpinner.selectedItemPosition)
            if (variant != null) binding.retroPackageInput.setText(variant.packageName)
            refreshRetroArchStatusOnly()
            refreshSelectedCoreStatus()
        }
        binding.retroCoreFolderUriText.text = preferences.retroArchCoreTreeUri?.toString()
            ?: "Automático: Descargas/OzRetroRom/Cores → instalador de cores de RetroArch"
        binding.retroSystemFolderUriText.text = if (preferences.retroArchSystemTreeUri != null) {
            "Carpeta System/BIOS seleccionada · disponible para verificación previa"
        } else {
            "Sin carpeta System/BIOS seleccionada · Oz la pedirá cuando una BIOS obligatoria sea verificable"
        }
        binding.retroOverlayFolderUriText.text = preferences.retroArchOverlayTreeUri?.let {
            "Carpeta compartida autorizada · ${it.lastPathSegment?.substringAfterLast(':') ?: "overlays"}"
        } ?: "Sin carpeta compartida de overlays · selecciónala una vez"
        refreshRetroArchStatusOnly()
        refreshSelectedCoreStatus()
    }

    private fun applyScope() {
        if (intent.getStringExtra(EXTRA_SCOPE) == SCOPE_ADVANCED) {
            binding.romLibrarySection.visibility = View.GONE
            binding.artworkLibrarySection.visibility = View.GONE
            binding.toolbar.title = "Emulación e integraciones"
        }
    }

    private fun downloadOfficialOverlays() {
        val target = preferences.retroArchOverlayTreeUri
        if (target == null) {
            Toast.makeText(this, "Selecciona primero una carpeta compartida para los overlays", Toast.LENGTH_LONG).show()
            retroOverlayFolderPicker.launch(null)
            return
        }
        binding.downloadRetroOverlaysButton.isEnabled = false
        binding.retroOverlayStatusText.text = "Preparando descarga oficial de Libretro…"
        lifecycleScope.launch {
            runCatching {
                retroOverlayManager.installOfficialBundle(target) { progress ->
                    runOnUiThread {
                        binding.retroOverlayStatusText.text = when {
                            progress.total > 0 && progress.stage.startsWith("Descargando") -> {
                                val pct = ((progress.downloaded * 100L) / progress.total).coerceIn(0L, 100L)
                                "${progress.stage} · $pct% · ${formatBytes(progress.downloaded)} / ${formatBytes(progress.total)}"
                            }
                            progress.entries > 0 -> "${progress.stage} · ${progress.entries} archivos"
                            else -> progress.stage
                        }
                    }
                }
            }.onSuccess { result ->
                binding.retroOverlayStatusText.text = "Paquete oficial actualizado · ${result.entriesInstalled} archivos · ${formatBytes(result.downloadedBytes)}"
                Toast.makeText(
                    this@SettingsActivity,
                    "Overlays oficiales actualizados. En RetroArch apunta Settings → Directory → Input Overlays a esta misma carpeta.",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { error ->
                binding.retroOverlayStatusText.text = "No se pudieron actualizar los overlays · ${error.message}"
            }
            binding.downloadRetroOverlaysButton.isEnabled = true
        }
    }

    private fun refreshRetroArchStatusOnly() {
        val packageName = effectiveRetroPackage()
        val installed = packageName != null && retroArchManager.isInstalled(packageName)
        val installedLabels = retroArchManager.installedVariants().joinToString { it.label }
        binding.retroArchStatusText.text = when {
            installed -> "RetroArch detectado · $packageName"
            installedLabels.isNotBlank() -> "Se encontró $installedLabels. Selecciónalo para usarlo."
            else -> "RetroArch no está instalado. Oz solicitará instalar la versión oficial antes de jugar."
        }
        binding.installOrOpenRetroArchButton.text = if (installed) "Abrir RetroArch" else "Instalar RetroArch"
    }

    private fun effectiveRetroPackage(): String? {
        val typed = binding.retroPackageInput.text?.toString()?.trim().orEmpty()
        if (typed.isNotBlank()) return typed
        return retroVariants.getOrNull(binding.retroArchVariantSpinner.selectedItemPosition)?.packageName
    }

    private fun refreshSelectedCoreStatus() {
        val system = selectedSystem()
        if (system == null) {
            binding.retroCoreStatusText.text = "Selecciona un sistema para revisar su core."
            binding.installSelectedCoreButton.isEnabled = false
            return
        }
        val rawCore = system.coreId?.takeIf(String::isNotBlank)
        if (rawCore == null) {
            binding.retroCoreStatusText.text = "${system.name}: no tiene un core RetroArch configurado."
            binding.installSelectedCoreButton.isEnabled = false
            return
        }
        binding.installSelectedCoreButton.isEnabled = true
        val core = retroArchManager.normalizeCoreId(rawCore)
        val packageName = effectiveRetroPackage()
        if (packageName == null || !retroArchManager.isInstalled(packageName)) {
            binding.retroCoreStatusText.text = "${system.name}: instala RetroArch para preparar $core."
            return
        }
        val stagedCore = retroArchManager.selectedFolderCompatibleCore(
            preferences.retroArchCoreTreeUri,
            core,
        )
        if (stagedCore != null) {
            binding.retroCoreStatusText.text =
                "${system.name}: $stagedCore preparado en la carpeta seleccionada; RetroArch aún debe importarlo."
        }
        binding.retroCoreStatusText.text = "${system.name}: comprobando $core…"
        lifecycleScope.launch {
            val query = runCatching { retroArchManager.queryInstalledCores(packageName) }.getOrNull()
            val installedCore = query
                ?.takeIf { it.supported }
                ?.let { retroArchManager.installedCompatibleCore(it, core) }
            if (query?.supported == true) {
                retroArchManager.coreCandidatesFor(core).forEach { candidate ->
                    preferences.setRetroArchCoreConfirmed(packageName, candidate, candidate == installedCore)
                }
            }
            binding.retroCoreStatusText.text = when {
                installedCore != null -> "${system.name}: core $installedCore instalado."
                query?.supported == true && retroArchManager.canAutomaticallyInstall(core) ->
                    "${system.name}: falta $core. Oz puede instalar una copia verificada."
                query?.supported == true ->
                    "${system.name}: falta $core y no está aprobado en esta APK. Instálalo desde RetroArch."
                stagedCore != null ->
                    "${system.name}: $stagedCore está preparado para importarlo en RetroArch."
                retroArchManager.coreCandidatesFor(core).any { preferences.isRetroArchCoreConfirmed(packageName, it) } ->
                    "${system.name}: RetroArch confirmó anteriormente un core compatible."
                else -> "${system.name}: no fue posible consultar los cores; Oz lo preparará al jugar."
            }
        }
    }

    private fun installSelectedSystemCore() {
        val system = selectedSystem() ?: return
        val rawCore = system.coreId?.takeIf(String::isNotBlank) ?: return
        val packageName = effectiveRetroPackage()
        if (packageName == null || !retroArchManager.isInstalled(packageName)) {
            Toast.makeText(this, "Primero instala RetroArch", Toast.LENGTH_LONG).show()
            runCatching { retroArchManager.openOfficialInstallPage() }
            return
        }
        if (!retroArchManager.canAutomaticallyInstall(rawCore)) {
            Toast.makeText(
                this,
                "Este core no está incluido en el catálogo SHA-256 de esta APK. Instálalo desde RetroArch.",
                Toast.LENGTH_LONG,
            ).show()
            retroArchManager.openRetroArch(packageName)
            return
        }
        binding.installSelectedCoreButton.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                retroArchManager.downloadAndStageCore(rawCore, preferences.retroArchCoreTreeUri) { progress ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.retroCoreStatusText.text = if (progress.total > 0L) {
                            "${progress.stage} · ${(progress.downloaded * 100L / progress.total).coerceIn(0L, 100L)}%"
                        } else progress.stage
                    }
                }
            }.onSuccess { artifact ->
                // La copia queda pendiente hasta que RetroArch informe el core instalado.
                preferences.setRetroArchCoreConfirmed(packageName, artifact.coreName, false)
                if (artifact.directExternalCore) {
                    binding.retroCoreStatusText.text = "${artifact.coreName} copiado en la carpeta de cores seleccionada."
                } else {
                    binding.retroCoreStatusText.text = "Entregando ${artifact.coreName} a RetroArch…"
                    runCatching { startActivity(retroArchManager.buildCoreSideloadIntent(packageName, artifact)) }
                        .onFailure { error ->
                            binding.retroCoreStatusText.text = "RetroArch no aceptó el core: ${error.message}"
                            preferences.setRetroArchCoreConfirmed(packageName, artifact.coreName, false)
                        }
                }
            }.onFailure { error ->
                binding.retroCoreStatusText.text = "No se pudo preparar el core: ${error.message}"
            }
            binding.installSelectedCoreButton.isEnabled = true
        }
    }

    private fun selectedSystem(): CatalogSystem? = systems.getOrNull(binding.systemSpinner.selectedItemPosition)

    private fun setSystemControlsEnabled(enabled: Boolean) {
        binding.systemSpinner.isEnabled = enabled
        binding.systemRomFolderButton.isEnabled = enabled
        binding.clearSystemRomButton.isEnabled = enabled
    }

    private fun refreshInternetArchiveStatus() {
        val username = internetArchiveAuth.username()
        val loggedIn = internetArchiveAuth.isLoggedIn()
        binding.internetArchiveStatusText.text = if (loggedIn) {
            "Conectado${username?.let { " · $it" }.orEmpty()} · HTTPS autenticado disponible sin volver a iniciar sesión en cada juego"
        } else {
            "Sin conexión · Chrome se abrirá una vez para obtener las claves IA-S3; las descargas públicas siguen disponibles"
        }
        binding.internetArchiveLoginButton.text = if (loggedIn) "Renovar conexión" else "Conectar con Chrome"
        binding.internetArchiveLogoutButton.isEnabled = loggedIn
    }

    private fun refreshRomgiStatus() {
        val bytes = RomgiCatalog(this).cachedCatalogSize()
        binding.romgiCatalogStatusText.text = if (bytes > 0L) {
            "Catálogo ROMgi disponible sin conexión · ${formatBytes(bytes)}"
        } else {
            "El catálogo ROMgi se descargará automáticamente al jugar por primera vez"
        }
    }

    private fun refreshUris() {
        binding.globalVirtualUriText.text = preferences.globalVirtualTreeUri?.toString() ?: "Sin carpeta global seleccionada"
        val system = selectedSystem()
        binding.globalRomUriText.text = preferences.globalRomTreeUri?.let {
            "Biblioteca persistente: $it"
        } ?: "Sin biblioteca ROMs persistente · se pedirá al descargar por primera vez"
        val romOverride = system?.let { preferences.systemRomTreeUri(it.id) }
        binding.systemRomUriText.text = when {
            system == null -> "Selecciona un sistema"
            romOverride != null -> "${system.name}: biblioteca específica $romOverride"
            preferences.globalRomTreeUri != null -> "${system.name}: guardará las descargas en la biblioteca global"
            else -> "${system.name}: la primera descarga pedirá dónde conservar la ROM"
        }
        binding.iisuAssetsUriText.text = preferences.iisuAssetsTreeUri?.toString() ?: "Carpeta de assets opcional"
        binding.localArtworkUriText.text = preferences.localArtworkTreeUri?.toString() ?: "Sin carpeta local de imágenes"
        binding.fbneoBiosUriText.text = preferences.fbneoBiosTreeUri?.toString() ?: "ROMgi intentará resolver las BIOS; puedes añadir una carpeta de respaldo"
    }

    private fun saveAndFinish() {
        preferences.romgiAutoEnabled = binding.romgiAutoSwitch.isChecked
        preferences.romgiWifiOnly = binding.romgiWifiOnlySwitch.isChecked
        preferences.romgiTorrentsEnabled = binding.romgiTorrentSwitch.isChecked
        preferences.catalogPerformanceMode = CatalogPerformanceMode.entries.getOrElse(
            binding.catalogPerformanceSpinner.selectedItemPosition,
        ) { CatalogPerformanceMode.FLUID }
        preferences.preferredRegion = regionKeys.getOrElse(binding.preferredRegionSpinner.selectedItemPosition) { "usa" }
        preferences.artworkMode = ArtworkMode.entries.getOrElse(binding.artworkModeSpinner.selectedItemPosition) { ArtworkMode.AUTO }
        preferences.retroArchPackage = binding.retroPackageInput.text?.toString().orEmpty()
        preferences.retroArchAutoInstallCores = binding.retroAutoInstallCoresSwitch.isChecked
        preferences.deleteAfterPlay = binding.deleteAfterPlaySwitch.isChecked
        Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun persist(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Log.w(TAG, "No se pudo persistir el permiso SAF para $uri", it) }
    }

    private fun formatBytes(value: Long): String = when {
        value >= 1024L * 1024L * 1024L -> "%.2f GB".format(value / (1024.0 * 1024.0 * 1024.0))
        value >= 1024L * 1024L -> "%.1f MB".format(value / (1024.0 * 1024.0))
        value >= 1024L -> "%.0f KB".format(value / 1024.0)
        else -> "$value B"
    }

    companion object {
        const val EXTRA_SCOPE = "settings_scope"
        const val SCOPE_ADVANCED = "advanced"
        private const val TAG = "OzSettings"
    }
}

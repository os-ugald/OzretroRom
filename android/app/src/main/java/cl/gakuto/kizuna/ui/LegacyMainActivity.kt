package cl.gakuto.kizuna.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.catalog.CatalogSystem
import cl.gakuto.kizuna.catalog.LibretroCatalogRepository
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.CollectionEntity
import cl.gakuto.kizuna.data.GameListSpec
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.databinding.ActivityMainBinding
import cl.gakuto.kizuna.integration.CollectionTransfer
import cl.gakuto.kizuna.integration.IisuExporter
import cl.gakuto.kizuna.util.CatalogFilterState
import cl.gakuto.kizuna.util.TextNormalizer
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class LegacyMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: LibraryRepository
    private lateinit var preferences: AppPreferences
    private lateinit var adapter: GameAdapter
    private lateinit var pageLoader: IncrementalGameLoader
    private lateinit var layoutManager: LinearLayoutManager
    private var collections: List<CollectionEntity> = emptyList()
    private var systems: List<CatalogSystem> = emptyList()
    private var selectedCollection = "all-games"
    private var filterState = CatalogFilterState()
    private var pageJob: Job? = null
    private var totalDatabaseGames = 0
    private var loadedGames = 0
    private var matchedGames = 0
    private var pageExhausted = true
    private var pageLoading = false
    private var queryGeneration = 0L
    private var activePageSize = 0
    private var updatingCollectionSpinner = false

    private val createJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch { CollectionTransfer(this@LegacyMainActivity, db()).exportJson(uri); toast("Colecciones exportadas") }
    }
    private val createCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch { CollectionTransfer(this@LegacyMainActivity, db()).exportCsv(uri); toast("CSV exportado") }
    }
    private val openJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { CollectionTransfer(this@LegacyMainActivity, db()).importJson(uri) }
                .onSuccess { (collectionCount, memberships) -> toast("Importados: $collectionCount colecciones y $memberships membresías") }
                .onFailure { toast("No se pudo importar: ${it.message}") }
        }
    }
    private val globalVirtualFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri)
        preferences.globalVirtualTreeUri = uri
        updateFolderStatus()
        toast("Carpeta de puente iiSU guardada")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }

        preferences = AppPreferences(this)
        repository = LibraryRepository(db())
        systems = LibretroCatalogRepository(this, db()).systems()
        createPageLoader()
        adapter = GameAdapter(preferences) { game ->
            CrtGameDialog.newInstance(game.id).show(supportFragmentManager, "crt")
        }
        layoutManager = LinearLayoutManager(this).apply { initialPrefetchItemCount = 6 }
        binding.gameList.layoutManager = layoutManager
        binding.gameList.adapter = adapter
        binding.gameList.itemAnimator = null
        binding.gameList.setHasFixedSize(true)
        binding.gameList.setItemViewCacheSize(12)
        binding.gameList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || pageLoading || pageExhausted) return
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_AHEAD_ITEMS) loadNextPage()
            }
        })

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_global -> openGlobalCatalog()
                R.id.action_settings -> openSettings()
                R.id.action_sync_iisu -> showSyncScope()
                R.id.action_new_collection -> showNewCollection()
                R.id.action_export_json -> createJson.launch("oz-retrorom-collections.json")
                R.id.action_import_json -> openJson.launch(arrayOf("application/json", "text/json", "text/plain"))
                R.id.action_export_csv -> createCsv.launch("oz-retrorom-collections.csv")
                R.id.action_about -> showAbout()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }

        binding.globalCatalogButton.setOnClickListener { openGlobalCatalog() }
        binding.settingsButton.setOnClickListener { openSettings() }
        binding.selectFolderButton.setOnClickListener { globalVirtualFolderPicker.launch(preferences.globalVirtualTreeUri) }
        binding.syncButton.setOnClickListener { showSyncScope() }
        binding.randomGameButton.setOnClickListener { openRandomGame() }
        binding.filtersButton.setOnClickListener { openFilterSheet() }
        binding.clearFiltersButton.setOnClickListener {
            filterState = CatalogFilterState()
            renderActiveFilters()
            scheduleReload()
        }

        lifecycleScope.launch { repository.collections().collectLatest(::updateCollections) }
        lifecycleScope.launch {
            db().gameDao().observeCount().collectLatest {
                totalDatabaseGames = it
                updateStatusText()
            }
        }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = scheduleReload(SEARCH_DEBOUNCE_MS)
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderActiveFilters()
        updateFolderStatus()
    }

    override fun onResume() {
        super.onResume()
        updateFolderStatus()
        val first = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val last = layoutManager.findLastVisibleItemPosition().coerceAtLeast(first)
        adapter.refreshRange(first, last - first + 1)
        val nextPageSize = preferences.catalogPerformanceMode.pageSize
        if (collections.isNotEmpty()) {
            if (activePageSize != nextPageSize) createPageLoader()
            // A SessionActivity may have just persisted a ROM and updated games.contentUri.
            // Reload the bounded first page so status chips and the computed Descargados
            // collection reflect Room immediately instead of stale adapter objects.
            scheduleReload()
        }
    }

    private fun createPageLoader() {
        activePageSize = preferences.catalogPerformanceMode.pageSize
        pageLoader = IncrementalGameLoader(repository, activePageSize)
    }

    private fun openFilterSheet() {
        CatalogFilterSheet.show(this, filterState, systems) { next ->
            filterState = next
            renderActiveFilters()
            scheduleReload()
        }
    }

    private fun renderActiveFilters() {
        binding.activeFilterGroup.removeAllViews()
        val labels = filterState.activeLabels()
        if (labels.isEmpty()) {
            binding.activeFilterGroup.addView(summaryChip("Región preferida · sin filtros adicionales"))
        } else {
            labels.forEach { binding.activeFilterGroup.addView(summaryChip(it)) }
        }
        binding.filtersButton.text = if (labels.isEmpty()) "Filtros" else "Filtros (${labels.size})"
        binding.clearFiltersButton.isEnabled = labels.isNotEmpty()
    }

    private fun summaryChip(label: String): Chip = Chip(this).apply {
        text = label
        isClickable = true
        isCheckable = false
        setOnClickListener { openFilterSheet() }
    }

    private fun updateCollections(items: List<CollectionEntity>) {
        collections = items
        if (items.none { it.id == selectedCollection }) selectedCollection = items.firstOrNull()?.id ?: "all-games"
        updatingCollectionSpinner = true
        binding.collectionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map { it.name })
        binding.collectionSpinner.setSelection(items.indexOfFirst { it.id == selectedCollection }.coerceAtLeast(0), false)
        updatingCollectionSpinner = false
        binding.collectionSpinner.onItemSelectedListener = SimpleItemSelectedListener { index ->
            if (updatingCollectionSpinner) return@SimpleItemSelectedListener
            val next = collections.getOrNull(index)?.id ?: "all-games"
            if (next != selectedCollection) {
                selectedCollection = next
                scheduleReload()
            }
        }
        scheduleReload()
    }

    private fun currentSpec(): GameListSpec = GameListSpec(
        collectionId = selectedCollection,
        normalizedSearch = TextNormalizer.normalize(binding.searchInput.text?.toString().orEmpty()),
        systemIds = filterState.systemIds,
        platformFamilies = filterState.platformFamilies,
        genreKeys = filterState.genres,
        regionModes = filterState.regions,
        statusFilters = filterState.status,
        sort = filterState.sort,
    )

    private fun scheduleReload(delayMs: Long = 0L) {
        if (collections.isEmpty()) return
        pageJob?.cancel()
        val generation = ++queryGeneration
        val loader = IncrementalGameLoader(repository, activePageSize).also { pageLoader = it }
        pageLoading = true
        pageExhausted = false
        loadedGames = 0
        matchedGames = 0
        adapter.replace(emptyList())
        updateStatusText(loading = true)
        pageJob = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            val result = runCatching { loader.reset(currentSpec()) }
            if (generation != queryGeneration) return@launch
            pageLoading = false
            result.onSuccess {
                adapter.append(it.appended)
                loadedGames = it.loadedCount
                matchedGames = it.totalMatches
                pageExhausted = it.exhausted
                updateStatusText()
            }.onFailure {
                pageExhausted = true
                updateStatusText(error = it.message ?: "Error al cargar el catálogo")
            }
        }
    }

    private fun loadNextPage() {
        if (pageLoading || pageExhausted) return
        val generation = queryGeneration
        pageLoading = true
        updateStatusText(loading = true)
        pageJob = lifecycleScope.launch {
            val result = runCatching { pageLoader.loadMore() }
            if (generation != queryGeneration) return@launch
            pageLoading = false
            result.onSuccess {
                adapter.append(it.appended)
                loadedGames = it.loadedCount
                matchedGames = it.totalMatches
                pageExhausted = it.exhausted
                updateStatusText()
            }.onFailure {
                pageExhausted = true
                updateStatusText(error = it.message ?: "Error al cargar más juegos")
            }
        }
    }

    private fun updateStatusText(loading: Boolean = false, error: String? = null) {
        val collectionName = collections.firstOrNull { it.id == selectedCollection }?.name.orEmpty()
        val state = when {
            error != null -> error
            loading && loadedGames == 0 -> "Cargando primera página…"
            loading -> "$loadedGames cargados · preparando más…"
            matchedGames == 0 -> "Sin coincidencias"
            pageExhausted -> "$loadedGames mostrados"
            else -> "$loadedGames cargados de $matchedGames · desplázate para continuar"
        }
        binding.statusText.text = "$state · $totalDatabaseGames indexados · $collectionName"
    }

    private fun openRandomGame() {
        lifecycleScope.launch {
            val spec = currentSpec()
            val count = runCatching { repository.count(spec) }.getOrDefault(0)
            if (count <= 0) {
                toast("No hay juegos que cumplan los filtros actuales")
                return@launch
            }
            val offset = Random.nextInt(count)
            val game = runCatching { repository.page(spec, 1, offset).items.firstOrNull() }.getOrNull()
            if (game == null) toast("No pude seleccionar un juego aleatorio")
            else CrtGameDialog.newInstance(game.id).show(supportFragmentManager, "crt-random")
        }
    }

    private fun showNewCollection() {
        val input = TextInputEditText(this).apply { hint = "Nombre de la colección"; setPadding(32, 16, 32, 16) }
        AlertDialog.Builder(this)
            .setTitle("Nueva colección personal")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                lifecycleScope.launch { repository.createCollection(input.text?.toString().orEmpty()); toast("Colección creada") }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSyncScope() {
        val currentName = collections.firstOrNull { it.id == selectedCollection }?.name ?: "Colección actual"
        val options = arrayOf("Colección actual: $currentName", "Mi colección", "Todos los juegos indexados")
        AlertDialog.Builder(this)
            .setTitle("Sincronizar con iiSU")
            .setMessage("Elige qué accesos de puente iiSU deseas generar. Las ROMs permanecen en Oz.")
            .setItems(options) { _, which ->
                val target = when (which) { 0 -> selectedCollection; 1 -> "my-collection"; else -> "all-games" }
                val label = when (which) { 0 -> currentName; 1 -> "Mi colección"; else -> "Todos los juegos indexados" }
                if (target == "all-games") {
                    AlertDialog.Builder(this)
                        .setTitle("Sincronizar catálogo completo")
                        .setMessage("Esto puede generar miles de accesos virtuales. ¿Continuar?")
                        .setPositiveButton("Continuar") { _, _ -> syncIisu(target, label) }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else syncIisu(target, label)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun syncIisu(collectionId: String, scopeLabel: String) {
        lifecycleScope.launch {
            val games = repository.collectionGamesOnce(collectionId)
            if (games.isEmpty()) { toast("$scopeLabel no contiene juegos"); return@launch }
            val hasTarget = preferences.globalVirtualTreeUri != null
            if (!hasTarget) { toast("Selecciona primero la carpeta de puente iiSU"); globalVirtualFolderPicker.launch(null); return@launch }
            binding.syncButton.isEnabled = false
            binding.syncButton.text = "Sincronizando…"
            try {
                runCatching { withContext(Dispatchers.IO) { IisuExporter(this@LegacyMainActivity).exportConfigured(preferences, games) } }
                    .onSuccess { result ->
                        val missing = if (result.systemsWithoutFolder.isEmpty()) "" else "\n\nSin carpeta: ${result.systemsWithoutFolder.joinToString()}"
                        AlertDialog.Builder(this@LegacyMainActivity)
                            .setTitle("Sincronización iiSU completada")
                            .setMessage("Ámbito: $scopeLabel\n${result.descriptors} accesos virtuales · ${result.systems} sistemas · ${result.assetManifests} manifiestos · ${result.artworkFiles} boxarts$missing")
                            .setPositiveButton("OK", null).show()
                    }
                    .onFailure { toast("No se pudo sincronizar: ${it.message}") }
            } finally {
                binding.syncButton.isEnabled = true
                binding.syncButton.text = "Sincronizar iiSU"
            }
        }
    }

    private fun updateFolderStatus() {
        binding.folderStatusText.text = preferences.globalVirtualTreeUri?.let { "Carpeta global: $it" }
            ?: "Puente iiSU pendiente · puedes definir destinos avanzados por sistema"
    }

    private fun openGlobalCatalog() = startActivity(Intent(this, GlobalCatalogActivity::class.java))
    private fun openSettings() = runCatching { startActivity(Intent(this, SettingsActivity::class.java)) }
        .onFailure { toast("No se pudo abrir Configuración: ${it.message}") }.let { Unit }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Oz RetroRom 0.6.0 Beta 4")
            .setMessage("Resolución ROMgi tolerante, filtros separados, motores por sistema, RetroArch nativo, iiSU y catálogo paginado.")
            .setPositiveButton("Cerrar", null).show()
    }

    private fun persist(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private fun db() = (application as OzApplication).database
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val LOAD_AHEAD_ITEMS = 12
    }
}

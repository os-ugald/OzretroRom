package cl.gakuto.kizuna.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.catalog.CatalogSystem
import cl.gakuto.kizuna.catalog.CatalogBackend
import cl.gakuto.kizuna.catalog.CatalogSystemRouting
import cl.gakuto.kizuna.catalog.SourceIndexCatalogStatus
import cl.gakuto.kizuna.catalog.LibretroCatalogRepository
import cl.gakuto.kizuna.catalog.sourceindex.SourceIndexCatalogs
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.CatalogSystemStateEntity
import cl.gakuto.kizuna.data.GameListSpec
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.databinding.ActivityGlobalCatalogBinding
import cl.gakuto.kizuna.util.CatalogFilterState
import cl.gakuto.kizuna.util.RegionMode
import cl.gakuto.kizuna.util.TextNormalizer
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Full Room-backed catalog. Search and every modern filter are executed by SQLite before paging;
 * no filter is limited to the 80 items currently visible.
 */
class GlobalCatalogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGlobalCatalogBinding
    private lateinit var adapter: GameAdapter
    private lateinit var catalog: LibretroCatalogRepository
    private lateinit var repository: LibraryRepository
    private lateinit var preferences: AppPreferences
    private lateinit var database: OzDatabase
    private lateinit var systems: List<CatalogSystem>
    private lateinit var pageLoader: IncrementalGameLoader
    private lateinit var layoutManager: LinearLayoutManager
    private var states: List<CatalogSystemStateEntity> = emptyList()
    private var selectedSystem: CatalogSystem? = null
    private var filterState = CatalogFilterState()
    private var pageJob: Job? = null
    private var indexJob: Job? = null
    private var totalGames = 0
    private var loadedGames = 0
    private var matchedGames = 0
    private var pageExhausted = true
    private var pageLoading = false
    private var queryGeneration = 0L
    private var activePageSize = 0
    private var sourceIndexOffset = 0
    private var sourceIndexTotal = 0

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityGlobalCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        val db = (application as OzApplication).database
        database = db
        preferences = AppPreferences(this)
        catalog = LibretroCatalogRepository(this, db)
        repository = LibraryRepository(db)
        val sourceIndexNavigation = intent.getBooleanExtra(EXTRA_SOURCE_INDEX_SYSTEM_ONLY, false)
        systems = if (sourceIndexNavigation) {
            CatalogSystemRouting.eligibleSourceIndexSystems(catalog.systems())
        } else catalog.systems()
        createPageLoader()

        val includeAllSystems = !sourceIndexNavigation
        binding.systemSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            (if (includeAllSystems) listOf("Todos los sistemas preparados") else emptyList()) + systems.map { it.name },
        )
        binding.systemSpinner.onItemSelectedListener = SimpleItemSelectedListener { index ->
            val next = systems.getOrNull(index - if (includeAllSystems) 1 else 0)
            if (next?.id != selectedSystem?.id) selectSystem(next)
        }
        intent.getStringExtra(EXTRA_SYSTEM_ID)?.let { requested ->
            systems.indexOfFirst { it.id == requested }.takeIf { it >= 0 }?.let { index ->
                binding.systemSpinner.setSelection(index + if (includeAllSystems) 1 else 0)
            }
        }

        binding.filterButton.setOnClickListener {
            val sourceCatalog = SourceIndexCatalogs.forSystem(selectedSystem?.id)
            val sourceIndexMode = sourceCatalog != null
            val initial = sourceCatalog?.supportedFilters(filterState) ?: filterState
            CatalogFilterSheet.show(
                context = this,
                initial = initial,
                systems = systems,
                mode = if (sourceIndexMode) CatalogFilterSheet.Mode.SOURCE_INDEX else CatalogFilterSheet.Mode.FULL,
            ) { next ->
                filterState = next.normalized()
                renderFilterSummary()
                scheduleReload()
            }
        }

        adapter = GameAdapter(preferences) { game ->
            lifecycleScope.launch {
                val selected = if (SourceIndexCatalogs.forProjection(game) != null) {
                    requireNotNull(SourceIndexCatalogs.forProjection(game)).materialize(this@GlobalCatalogActivity, database, game)
                } else game
                CrtGameDialog.newInstance(selected.id).show(supportFragmentManager, "crt")
            }
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
        binding.cancelIndexButton.setOnClickListener { indexJob?.cancel() }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                scheduleReload(SEARCH_DEBOUNCE_MS)
            override fun afterTextChanged(s: Editable?) = Unit
        })

        lifecycleScope.launch {
            db.catalogStateDao().observeAll().collectLatest {
                states = it
                updateSummary()
            }
        }
        lifecycleScope.launch {
            db.gameDao().observeCount().collectLatest {
                totalGames = it
                updateSummary()
            }
        }
        renderFilterSummary()
        updateSummary()
        scheduleReload()
    }

    override fun onResume() {
        super.onResume()
        val first = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val last = layoutManager.findLastVisibleItemPosition().coerceAtLeast(first)
        adapter.refreshRange(first, last - first + 1)
        val nextPageSize = preferences.catalogPerformanceMode.pageSize
        if (activePageSize != nextPageSize) createPageLoader()
        // Refresh Room-backed installed/downloaded state after returning from SessionActivity.
        scheduleReload()
    }

    private fun createPageLoader() {
        activePageSize = preferences.catalogPerformanceMode.pageSize
        pageLoader = IncrementalGameLoader(repository, activePageSize)
    }

    private fun selectSystem(system: CatalogSystem?) {
        indexJob?.cancel()
        selectedSystem = system
        if (CatalogSystemRouting.backendFor(system?.id) == CatalogBackend.SOURCE_INDEX) {
            filterState = requireNotNull(SourceIndexCatalogs.forSystem(system?.id)).supportedFilters(filterState)
        }
        renderFilterSummary()
        scheduleReload()
        if (system == null) {
            binding.indexProgressText.text =
                "Mostrando todos los sistemas ya preparados · usa Filtros para fabricante, plataforma, género, región y estado"
            updateSummary()
            return
        }
        lifecycleScope.launch {
            if (CatalogSystemRouting.backendFor(system.id) == CatalogBackend.SOURCE_INDEX) {
                val controlledGames = requireNotNull(SourceIndexCatalogs.forSystem(system.id))
                    .reader(this@GlobalCatalogActivity)
                    .gameCount
                binding.indexProgressText.text = SourceIndexCatalogStatus.ready(system.name, controlledGames)
            } else if (catalog.needsIndex(system)) startAutomaticIndex(system)
            else binding.indexProgressText.text = "${system.name}: catálogo local listo · filtros SQL y carga progresiva activos"
        }
    }

    private fun startAutomaticIndex(system: CatalogSystem) {
        if (indexJob?.isActive == true) return
        setIndexing(true)
        indexJob = lifecycleScope.launch {
            try {
                val result = catalog.index(system) { progress ->
                    runOnUiThread { binding.indexProgressText.text = progress.stage }
                }
                result.onSuccess {
                    binding.indexProgressText.text = "${system.name}: $it juegos listos"
                    scheduleReload()
                }.onFailure {
                    binding.indexProgressText.text = "No se pudo preparar ${system.name}: ${it.message}"
                }
            } catch (_: CancellationException) {
                binding.indexProgressText.text = "Preparación cancelada · los juegos ya guardados se conservan"
            } finally {
                setIndexing(false)
            }
        }
    }

    private fun setIndexing(active: Boolean) {
        binding.systemSpinner.isEnabled = !active
        binding.cancelIndexButton.visibility = if (active) View.VISIBLE else View.GONE
        binding.loadingIndicator.visibility = if (active) View.VISIBLE else View.GONE
        if (active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun currentSpec(): GameListSpec {
        val constrainedBySpinner = selectedSystem != null
        return GameListSpec(
            systemId = selectedSystem?.id,
            normalizedSearch = TextNormalizer.normalize(binding.searchInput.text?.toString().orEmpty()),
            systemIds = if (constrainedBySpinner) emptySet() else filterState.systemIds,
            platformFamilies = if (constrainedBySpinner) emptySet() else filterState.platformFamilies,
            genreKeys = filterState.genres,
            regionModes = filterState.regions,
            statusFilters = filterState.status,
            sort = filterState.sort,
        )
    }

    private fun scheduleReload(delayMs: Long = 0L) {
        pageJob?.cancel()
        val generation = ++queryGeneration
        val loader = IncrementalGameLoader(repository, activePageSize).also { pageLoader = it }
        pageLoading = true
        pageExhausted = false
        loadedGames = 0
        matchedGames = 0
        adapter.replace(emptyList())
        updateListStatus(loading = true)
        if (CatalogSystemRouting.backendFor(selectedSystem?.id) == CatalogBackend.SOURCE_INDEX) {
            sourceIndexOffset = 0
            pageJob = lifecycleScope.launch {
                if (delayMs > 0) delay(delayMs)
                val result = runCatching {
                    requireNotNull(SourceIndexCatalogs.forSystem(selectedSystem?.id)).page(
                        this@GlobalCatalogActivity,
                        database,
                        binding.searchInput.text?.toString().orEmpty(),
                        regions = filterState.normalized().regions,
                        sort = filterState.sort,
                        offset = 0,
                        limit = activePageSize,
                    )
                }
                if (generation != queryGeneration) return@launch
                pageLoading = false
                result.onSuccess {
                    adapter.append(it.games)
                    loadedGames = it.games.size
                    matchedGames = it.total
                    sourceIndexTotal = it.controlledTotal
                    sourceIndexOffset = it.nextOffset
                    pageExhausted = it.exhausted
                    updateListStatus()
                    updateSummary()
                }.onFailure {
                    pageExhausted = true
                    updateListStatus(error = it.message ?: "Error al abrir Oz Source Index")
                }
            }
            return
        }
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
                updateListStatus()
            }.onFailure {
                pageExhausted = true
                updateListStatus(error = it.message ?: "Error al consultar el catálogo")
            }
        }
    }

    private fun loadNextPage() {
        if (pageLoading || pageExhausted) return
        val generation = queryGeneration
        pageLoading = true
        updateListStatus(loading = true)
        if (CatalogSystemRouting.backendFor(selectedSystem?.id) == CatalogBackend.SOURCE_INDEX) {
            pageJob = lifecycleScope.launch {
                val result = runCatching {
                    requireNotNull(SourceIndexCatalogs.forSystem(selectedSystem?.id)).page(
                        this@GlobalCatalogActivity,
                        database,
                        binding.searchInput.text?.toString().orEmpty(),
                        regions = filterState.normalized().regions,
                        sort = filterState.sort,
                        offset = sourceIndexOffset,
                        limit = activePageSize,
                    )
                }
                if (generation != queryGeneration) return@launch
                pageLoading = false
                result.onSuccess {
                    adapter.append(it.games)
                    loadedGames += it.games.size
                    matchedGames = it.total
                    sourceIndexOffset = it.nextOffset
                    pageExhausted = it.exhausted
                    updateListStatus()
                }.onFailure {
                    pageExhausted = true
                    updateListStatus(error = it.message ?: "Error al cargar Oz Source Index")
                }
            }
            return
        }
        pageJob = lifecycleScope.launch {
            val result = runCatching { pageLoader.loadMore() }
            if (generation != queryGeneration) return@launch
            pageLoading = false
            result.onSuccess {
                adapter.append(it.appended)
                loadedGames = it.loadedCount
                matchedGames = it.totalMatches
                pageExhausted = it.exhausted
                updateListStatus()
            }.onFailure {
                pageExhausted = true
                updateListStatus(error = it.message ?: "Error al cargar más juegos")
            }
        }
    }

    private fun updateListStatus(loading: Boolean = false, error: String? = null) {
        val scope = selectedSystem?.name ?: "Catálogo global"
        binding.statusText.text = when {
            error != null -> error
            loading && loadedGames == 0 -> "$scope: consultando toda la base…"
            loading -> "$loadedGames cargados · preparando más…"
            matchedGames == 0 -> "$scope: sin coincidencias para la búsqueda y filtros actuales"
            pageExhausted -> "$loadedGames mostrados · $scope · consulta completa"
            else -> "$loadedGames cargados de $matchedGames · desplázate para continuar · $scope"
        }
    }

    private fun renderFilterSummary() {
        binding.filterChipGroup.removeAllViews()
        val labels = buildList {
            selectedSystem?.let { add(it.name) }
            if (selectedSystem == null) {
                filterState.systemIds.mapNotNull { id -> systems.firstOrNull { it.id == id }?.name }.forEach(::add)
            }
            addAll(filterState.activeLabels())
            if (isEmpty()) add(RegionMode.PREFERRED.label)
        }
        labels.distinct().forEach { label ->
            binding.filterChipGroup.addView(Chip(this).apply {
                text = label
                isCheckable = false
                isClickable = false
            })
        }
        val activeCount = filterState.activeLabels().size +
            if (selectedSystem == null) filterState.systemIds.size else 0
        binding.filterButton.text = if (activeCount > 0) "Filtros ($activeCount)" else "Filtros"
    }

    private fun updateSummary() {
        val ready = states.count { it.status == "READY" }
        val selected = selectedSystem
        binding.catalogSummaryText.text = if (selected == null) {
            "$totalGames juegos locales · $ready/${systems.size} sistemas preparados · filtros sobre toda la base"
        } else if (CatalogSystemRouting.backendFor(selected.id) == CatalogBackend.SOURCE_INDEX && sourceIndexTotal > 0) {
            "$sourceIndexTotal juegos ${selected.name} controlados · Oz Source Index · biblioteca personal separada"
        } else {
            val state = states.firstOrNull { it.systemId == selected.id }
            val stateLabel = when (state?.status) {
                "READY" -> "listo"
                "DOWNLOADING" -> "preparando"
                "STALE" -> "requiere actualización"
                "ERROR" -> "con error"
                "CANCELLED" -> "cancelado"
                else -> "pendiente"
            }
            "$totalGames juegos locales · ${selected.name}: $stateLabel · página ${preferences.catalogPerformanceMode.pageSize}"
        }
    }

    companion object {
        fun intent(context: Context, systemId: String): Intent = Intent(context, GlobalCatalogActivity::class.java)
            .putExtra(EXTRA_SYSTEM_ID, systemId)
            .putExtra(EXTRA_SOURCE_INDEX_SYSTEM_ONLY, true)

        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val LOAD_AHEAD_ITEMS = 12
        internal const val EXTRA_SYSTEM_ID = "oz.catalogSystemId"
        internal const val EXTRA_SOURCE_INDEX_SYSTEM_ONLY = "oz.sourceIndexSystemOnly"
    }
}

package cl.gakuto.kizuna.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.catalog.sourceindex.SourceIndexCatalogs
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.CatalogPagingSql
import cl.gakuto.kizuna.data.ArtworkMode
import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.data.GameDescriptor
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.databinding.DialogCrtGameBinding
import cl.gakuto.kizuna.launch.SessionActivity
import cl.gakuto.kizuna.util.ArtworkLoader
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters
import kotlinx.coroutines.launch

class CrtGameDialog : DialogFragment() {
    private var _binding: DialogCrtGameBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferences: AppPreferences
    private var game: GameEntity? = null

    private val artworkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val current = game ?: return@registerForActivityResult
        persist(uri)
        preferences.setCustomArtworkUri(current.id, uri)
        preferences.setGameArtworkMode(current.id, ArtworkMode.CUSTOM)
        refreshGame(current)
    }

    private val sourcePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val current = game ?: return@registerForActivityResult
        persist(uri)
        val name = runCatching {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        preferences.setAuthorizedSource(current.id, AuthorizedSource(uri.toString(), name))
        refreshGame(current)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = DialogCrtGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        preferences = AppPreferences(requireContext())
        val id = requireArguments().getString(ARG_ID) ?: return
        val repo = LibraryRepository((requireActivity().application as OzApplication).database)
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = repo.game(id) ?: return@launch
            val rendered = repo.artworkVariantFor(loaded)
            game = loaded
            refreshGame(rendered)
            binding.playButton.setOnClickListener {
                if (SourceIndexCatalogs.acquisitionForProjection(loaded) != null && loaded.contentUri.isNullOrBlank()) {
                    chooseSourceIndexRelease(loaded)
                } else {
                    launchGame(loaded)
                }
            }
            binding.favoriteButton.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val added = repo.toggle("favorites", loaded.id)
                    binding.favoriteButton.text = if (added) "Quitar de Favoritos" else "Agregar a Favoritos"
                }
            }
            binding.collectionButton.setOnClickListener { showCollections(repo, loaded) }
            binding.artworkButton.setOnClickListener { showArtworkOptions(loaded) }
            binding.sourceButton.setOnClickListener { showSourceOptions(loaded) }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
            setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.68f }
        }
    }

    private fun refreshGame(current: GameEntity) {
        binding.gameTitle.text = CatalogFilters.displayTitle(current.title)
        binding.gameMeta.text = listOfNotNull(
            current.platform,
            current.year?.toString(),
            current.region.takeIf { it != "Unknown" },
        ).joinToString(" · ")
        ArtworkLoader.load(
            requireContext(),
            binding.gameArtwork,
            ArtworkResolver.candidates(requireContext(), preferences, current),
            R.drawable.oz_app_icon_arcade,
        )
        binding.artworkButton.text = "Boxart: ${ArtworkResolver.label(preferences, current.id)}"
        val source = preferences.authorizedSource(current.id)
        val downloaded = !current.contentUri.isNullOrBlank()
        val sourceIndexAvailable = SourceIndexCatalogs.forProjection(current) != null
        val sourceIndexAcquisitionEnabled = SourceIndexCatalogs.acquisitionForProjection(current) != null
        val romgiAvailable = preferences.romgiAutoEnabled && GameDescriptor.supports(current.systemId)
        binding.sourceButton.text = when {
            downloaded -> "Biblioteca: descargado"
            source != null -> "ROM propia configurada"
            sourceIndexAvailable -> "Fuente: Oz Source Index"
            romgiAvailable -> "Fuente: ROMgi automática"
            else -> "Elegir ROM propia"
        }
        binding.playButton.text = when {
            downloaded || source != null -> "Jugar"
            sourceIndexAcquisitionEnabled -> "Descargar y jugar"
            sourceIndexAvailable -> "Descarga pendiente de validación"
            romgiAvailable -> "Descargar y jugar"
            else -> "Jugar"
        }
        binding.playButton.isEnabled = (downloaded || source != null || !sourceIndexAvailable || sourceIndexAcquisitionEnabled) &&
            (current.coreId?.isNotBlank() == true || current.systemId in setOf("ps2", "3ds"))
        binding.sourceText.text = buildString {
            append("Origen del catálogo: ${current.source}. ")
            append(if (current.launchValidated) "Perfil de plataforma validado." else "Se usará el núcleo configurado; puede requerir ajuste avanzado.")
            append("\nBoxart: ${ArtworkResolver.label(preferences, current.id)}.")
            append("\nFuente: ")
            append(
                when {
                    downloaded -> "biblioteca ROMs persistente; al jugar se reutiliza este archivo local."
                    source != null -> redactSource(source.value)
                    sourceIndexAcquisitionEnabled -> "Oz Source Index; una fuente MiNERVA exacta y sin cambio automático de candidato."
                    sourceIndexAvailable -> "Oz Source Index; catálogo controlado disponible, con descarga pendiente de validación."
                    romgiAvailable -> "ROMgi automática; la primera descarga se conservará en tu biblioteca ROMs."
                    else -> "biblioteca local o archivo propio."
                },
            )
        }
    }

    private fun chooseSourceIndexRelease(current: GameEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val catalog = requireNotNull(SourceIndexCatalogs.forProjection(current))
            val releases = runCatching { catalog.releases(requireContext(), current.id) }
                .getOrElse {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Oz Source Index")
                        .setMessage(it.message ?: "No pude abrir las versiones disponibles.")
                        .setPositiveButton("Cerrar", null)
                        .show()
                    return@launch
                }
            when (releases.size) {
                0 -> AlertDialog.Builder(requireContext())
                    .setTitle("Sin versión descargable")
                    .setMessage("Oz conserva el juego en el catálogo, pero no encontró una versión con fuente válida.")
                    .setPositiveButton("Cerrar", null)
                    .show()
                1 -> launchGame(current, releases.single().releaseId)
                else -> AlertDialog.Builder(requireContext())
                    .setTitle("Elegir versión")
                    .setItems(releases.map(catalog::releaseLabel).toTypedArray()) { _, index ->
                        launchGame(current, releases[index].releaseId)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun launchGame(current: GameEntity, releaseId: String? = null) {
        val intent = Intent(requireContext(), SessionActivity::class.java)
            .putExtra(SessionActivity.EXTRA_GAME_ID, current.id)
        releaseId?.let { intent.putExtra(SessionActivity.EXTRA_SOURCE_INDEX_RELEASE_ID, it) }
        startActivity(intent)
        dismiss()
    }

    private fun showCollections(repo: LibraryRepository, current: GameEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val dao = (requireActivity().application as OzApplication).database.collectionDao()
            val collections = dao.all().filter {
                it.id != "all-games" && it.id != CatalogPagingSql.DOWNLOADED_COLLECTION_ID
            }
            val selected = dao.collectionIdsForGame(current.id).toMutableSet()
            val checks = BooleanArray(collections.size) { collections[it].id in selected }
            AlertDialog.Builder(requireContext())
                .setTitle("Añadir a colecciones")
                .setMultiChoiceItems(collections.map { it.name }.toTypedArray(), checks) { _, which, checked -> checks[which] = checked }
                .setPositiveButton("Guardar") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        collections.forEachIndexed { index, collection ->
                            val had = collection.id in selected
                            if (checks[index] != had) repo.toggle(collection.id, current.id)
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun showArtworkOptions(current: GameEntity) {
        val options = arrayOf(
            "Restablecer y usar configuración global",
            "Automático",
            "Carátula (Boxarts)",
            "Pantalla de título",
            "Captura del juego",
            "Carpeta local",
            "Elegir imagen del dispositivo",
            "Configurar URL de imagen",
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Boxart de ${current.title}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        preferences.setGameArtworkMode(current.id, null)
                        preferences.setCustomArtworkUri(current.id, null)
                        preferences.setCustomArtworkUrl(current.id, null)
                    }
                    1 -> preferences.setGameArtworkMode(current.id, ArtworkMode.AUTO)
                    2 -> preferences.setGameArtworkMode(current.id, ArtworkMode.BOXART)
                    3 -> preferences.setGameArtworkMode(current.id, ArtworkMode.TITLE_SCREEN)
                    4 -> preferences.setGameArtworkMode(current.id, ArtworkMode.SNAP)
                    5 -> preferences.setGameArtworkMode(current.id, ArtworkMode.LOCAL_FOLDER)
                    6 -> artworkPicker.launch(arrayOf("image/*"))
                    7 -> showArtworkUrlDialog(current)
                }
                if (which <= 5) refreshGame(current)
            }
            .show()
    }

    private fun showArtworkUrlDialog(current: GameEntity) {
        val input = EditText(requireContext()).apply {
            hint = "https://…/boxart.png"
            setText(preferences.customArtworkUrl(current.id).orEmpty())
            setSingleLine(true)
            setPadding(32, 16, 32, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("URL de boxart")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                preferences.setCustomArtworkUrl(current.id, input.text?.toString())
                preferences.setGameArtworkMode(current.id, ArtworkMode.CUSTOM)
                refreshGame(current)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSourceOptions(current: GameEntity) {
        val hasSource = preferences.authorizedSource(current.id) != null
        val options = buildList {
            add("Elegir ROM o archivo propio")
            if (hasSource) add("Olvidar ROM propia y volver a ROMgi")
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Fuente de juego")
            .setItems(options) { _, which ->
                when {
                    which == 0 -> sourcePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    hasSource && which == 1 -> {
                        preferences.setAuthorizedSource(current.id, null)
                        refreshGame(current)
                    }
                }
            }
            .show()
    }

    private fun persist(uri: Uri) {
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun redactSource(value: String): String = when {
        value.startsWith("magnet:", true) -> "magnet configurado"
        value.startsWith("content:", true) -> "archivo del dispositivo"
        value.startsWith("http", true) -> runCatching { Uri.parse(value).host }.getOrNull()?.let { "servidor $it" } ?: "URL configurada"
        else -> "fuente configurada"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ID = "id"
        fun newInstance(id: String) = CrtGameDialog().apply {
            arguments = Bundle().apply { putString(ARG_ID, id) }
        }
    }
}

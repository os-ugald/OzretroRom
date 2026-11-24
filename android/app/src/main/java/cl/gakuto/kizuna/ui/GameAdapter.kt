package cl.gakuto.kizuna.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameCatalogOrigin
import cl.gakuto.kizuna.databinding.ItemGameBinding
import cl.gakuto.kizuna.util.ArtworkLoader
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters

/**
 * Mutable, append-only adapter used by the incremental catalogue loader. It avoids running a
 * full DiffUtil comparison every time another page is appended to a large system.
 */
class GameAdapter(
    private val preferences: AppPreferences,
    private val onClick: (GameEntity) -> Unit,
) : RecyclerView.Adapter<GameAdapter.Holder>() {
    private val items = ArrayList<GameEntity>()

    init {
        setHasStableIds(true)
    }

    class Holder(val binding: ItemGameBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()
    override fun getItemCount(): Int = items.size

    fun replace(next: List<GameEntity>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    fun append(next: List<GameEntity>) {
        if (next.isEmpty()) return
        val start = items.size
        items.addAll(next)
        notifyItemRangeInserted(start, next.size)
    }

    fun refreshRange(first: Int, count: Int) {
        if (items.isEmpty() || count <= 0) return
        val safeFirst = first.coerceIn(0, items.lastIndex)
        val safeCount = count.coerceAtMost(items.size - safeFirst)
        notifyItemRangeChanged(safeFirst, safeCount, PAYLOAD_REFRESH)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = bind(holder, items[position])

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        bind(holder, items[position])
    }

    private fun bind(holder: Holder, game: GameEntity) {
        val context = holder.binding.root.context
        holder.binding.titleText.text = CatalogFilters.displayTitle(game.title)
        holder.binding.metaText.text = listOfNotNull(
            game.platform,
            game.year?.toString(),
            game.region.takeIf { it != "Unknown" },
            game.genre.takeIf { it.isNotBlank() && !it.equals("Sin clasificar", true) },
        ).joinToString(" · ")
        holder.binding.stateText.text = when {
            !game.contentUri.isNullOrBlank() ->
                "✓ Descargado · listo para jugar sin volver a descargar"
            preferences.authorizedSource(game.id) != null ->
                "✓ Local · archivo propio configurado"
            game.catalogOrigin == GameCatalogOrigin.SOURCE_INDEX ->
                "○ Disponible · Oz Source Index"
            game.romgiAvailable || game.launchValidated ->
                "○ Disponible · ROMgi o biblioteca local"
            else -> "Coleccionable · lanzamiento aún no validado"
        }
        ArtworkLoader.load(
            context,
            holder.binding.gameArtwork,
            ArtworkResolver.candidates(context, preferences, game),
            R.drawable.oz_app_icon_arcade,
        )
        holder.binding.root.setOnClickListener { onClick(game) }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.binding.gameArtwork.tag = null
        holder.binding.gameArtwork.setImageResource(R.drawable.oz_app_icon_arcade)
        holder.binding.root.setOnClickListener(null)
        super.onViewRecycled(holder)
    }

    companion object {
        private const val PAYLOAD_REFRESH = "refresh"
    }
}

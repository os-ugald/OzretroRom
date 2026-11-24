package cl.gakuto.kizuna.catalog

import android.content.Context
import cl.gakuto.kizuna.util.TextNormalizer
import org.json.JSONArray

data class CuratedGameMetadata(
    val genre: String,
    val romSet: String?,
    val biosSets: List<String>,
)

class CuratedMetadata(context: Context) {
    private val byTitle: Map<String, CuratedGameMetadata>
    private val byRomSet: Map<String, CuratedGameMetadata>

    init {
        val array = JSONArray(context.assets.open("curated_metadata.json").bufferedReader().use { it.readText() })
        val titleMap = LinkedHashMap<String, CuratedGameMetadata>()
        val romMap = LinkedHashMap<String, CuratedGameMetadata>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val metadata = CuratedGameMetadata(
                genre = item.optString("genre", "Sin clasificar"),
                romSet = item.optString("romSet").takeIf(String::isNotBlank),
                biosSets = item.optJSONArray("biosSets")?.let { bios ->
                    (0 until bios.length()).map { bios.getString(it) }
                }.orEmpty(),
            )
            titleMap[TextNormalizer.normalize(item.getString("title"))] = metadata
            metadata.romSet?.lowercase()?.let { romMap[it] = metadata }
        }
        byTitle = titleMap
        byRomSet = romMap
    }

    fun find(title: String, romSet: String?): CuratedGameMetadata? =
        romSet?.lowercase()?.let(byRomSet::get) ?: byTitle[TextNormalizer.normalize(title)]
}

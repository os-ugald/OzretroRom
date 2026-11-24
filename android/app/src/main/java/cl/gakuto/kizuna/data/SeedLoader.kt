package cl.gakuto.kizuna.data

import android.content.Context
import androidx.room.withTransaction
import cl.gakuto.kizuna.catalog.CuratedMetadata
import cl.gakuto.kizuna.util.CatalogFilters
import cl.gakuto.kizuna.util.TextNormalizer
import org.json.JSONArray

class SeedLoader(private val context: Context, private val db: OzDatabase) {
    suspend fun seedIfNeeded() {
        if (db.gameDao().count() == 0) seedFreshDatabase()
        ensureRuntimeCollections()
        backfillMetadataIfNeeded()
    }

    /**
     * Computed/system collections must also exist for users upgrading an existing database,
     * because the JSON seed is only consumed for a fresh install.
     */
    private suspend fun ensureRuntimeCollections() {
        val runtime = listOf(
            CollectionEntity("all-games", "Todos los juegos", "Todo el catálogo disponible en Oz.", "▦", "#00E5FF", true, 0, CollectionKind.AUTOMATIC.name),
            CollectionEntity(CatalogPagingSql.DOWNLOADED_COLLECTION_ID, "Descargados", "Juegos guardados en tu biblioteca ROMs; se abren sin volver a descargar.", "↓", "#00E5FF", true, 1, CollectionKind.AUTOMATIC.name),
            CollectionEntity("favorites", "Favoritos", "Tus imprescindibles personales.", "★", "#FFECC7", true, 2, CollectionKind.AUTOMATIC.name),
            CollectionEntity(CatalogPagingSql.RECENT_COLLECTION_ID, "Recientes", "Los juegos que abriste últimamente, ordenados por actividad.", "◷", "#B78CFF", true, 3, CollectionKind.AUTOMATIC.name),
        )
        runtime.forEach { expected ->
            val current = db.collectionDao().byId(expected.id)
            if (current == null) db.collectionDao().insert(expected)
            else if (current.kind != CollectionKind.AUTOMATIC.name) db.collectionDao().insert(current.copy(kind = CollectionKind.AUTOMATIC.name))
        }
        db.collectionDao().byId("my-collection")?.let { current ->
            if (current.kind != CollectionKind.MANUAL.name) db.collectionDao().insert(current.copy(kind = CollectionKind.MANUAL.name))
        }
    }

    private suspend fun seedFreshDatabase() {
        val collections = JSONArray(context.assets.open("featured_collections.json").bufferedReader().use { it.readText() })
        val games = JSONArray(context.assets.open("seed_games.json").bufferedReader().use { it.readText() })
        db.withTransaction {
            val collectionEntities = (0 until collections.length()).map { i ->
                val o = collections.getJSONObject(i)
                CollectionEntity(
                    o.getString("id"),
                    o.getString("name"),
                    o.getString("description"),
                    o.optString("icon", "★"),
                    o.optString("accent", "#6A31B7"),
                    o.optBoolean("isSystem", true),
                    i,
                    o.optString("kind", CollectionKind.CURATED.name),
                )
            }
            db.collectionDao().insertAll(collectionEntities)
            val gameEntities = mutableListOf<GameEntity>()
            val memberships = mutableListOf<CollectionMembershipEntity>()
            for (i in 0 until games.length()) {
                val o = games.getJSONObject(i)
                val title = o.getString("title")
                val bios = o.optJSONArray("biosSets")?.let { array ->
                    (0 until array.length()).map { array.getString(it) }
                }.orEmpty()
                val game = GameEntity(
                    id = o.getString("id"),
                    title = title,
                    normalizedTitle = TextNormalizer.normalize(title),
                    platform = o.getString("platform"),
                    systemId = o.getString("systemId"),
                    year = o.optInt("year").takeIf { it > 0 },
                    region = o.optString("region", "World"),
                    thumbnailRepo = o.optString("thumbnailRepo").takeIf(String::isNotBlank),
                    source = "curated",
                    launchValidated = o.optBoolean("launchValidated"),
                    coreId = o.optString("coreId").takeIf(String::isNotBlank),
                    virtualExtension = o.optString("virtualExtension", "ozr"),
                    canonicalTitle = CatalogFilters.canonicalTitle(title),
                    genre = o.optString("genre", "Sin clasificar"),
                    romSet = o.optString("romSet").takeIf(String::isNotBlank),
                    biosSetsCsv = bios.joinToString(","),
                )
                gameEntities += game
                val array = o.getJSONArray("collections")
                for (j in 0 until array.length()) memberships += CollectionMembershipEntity(array.getString(j), game.id)
            }
            db.gameDao().insertAll(gameEntities)
            db.collectionDao().addMemberships(memberships)
        }
    }

    /**
     * Beta 3 adds canonical-title, genre, ROM-set and BIOS metadata. Existing Beta 2
     * databases are upgraded once without deleting favourites, collections or indexed games.
     */
    private suspend fun backfillMetadataIfNeeded() {
        val state = context.getSharedPreferences(UPGRADE_PREFS, Context.MODE_PRIVATE)
        if (state.getInt(KEY_METADATA_VERSION, 0) >= METADATA_VERSION) return

        val curated = CuratedMetadata(context)
        db.gameDao().allOnce().chunked(500).forEach { batch ->
            val updated = batch.map { game ->
                val metadata = curated.find(game.title, game.romSet)
                val genre = game.genre.takeUnless { it.isBlank() || it.equals("Sin clasificar", true) }
                    ?: metadata?.genre
                    ?: "Sin clasificar"
                val romSet = game.romSet?.takeIf { it.isNotBlank() } ?: metadata?.romSet
                val bios = game.biosSets.ifEmpty { metadata?.biosSets.orEmpty() }
                game.copy(
                    canonicalTitle = CatalogFilters.canonicalTitle(game.title),
                    genre = genre,
                    romSet = romSet,
                    biosSetsCsv = bios.joinToString(","),
                )
            }
            db.gameDao().insertAll(updated)
        }
        state.edit().putInt(KEY_METADATA_VERSION, METADATA_VERSION).apply()
    }

    companion object {
        private const val UPGRADE_PREFS = "oz-data-upgrades"
        private const val KEY_METADATA_VERSION = "catalog-metadata-version"
        private const val METADATA_VERSION = 3
    }
}

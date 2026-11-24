package cl.gakuto.kizuna.catalog

import android.content.Context
import androidx.room.withTransaction
import cl.gakuto.kizuna.data.CatalogSystemStateEntity
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.util.CatalogFilters
import cl.gakuto.kizuna.util.TextNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class LibretroCatalogRepository(private val context: Context, private val db: OzDatabase) {
    private val curated by lazy { CuratedMetadata(context) }

    fun systems(): List<CatalogSystem> {
        val array = JSONArray(context.assets.open("libretro_systems.json").bufferedReader().use { it.readText() })
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val datUrl = o.getString("datUrl")
            CatalogSystem(
                id = o.getString("id"),
                name = o.getString("name"),
                datUrl = datUrl,
                genreDatUrl = o.optString("genreDatUrl").takeIf(String::isNotBlank)
                    ?: deriveGenreUrl(o.getString("id"), datUrl),
                thumbnailRepo = o.getString("thumbnailRepo"),
                platform = o.getString("platform"),
                coreId = o.optString("coreId").takeIf(String::isNotBlank),
                virtualExtension = o.getString("virtualExtension"),
                launchValidated = o.optBoolean("launchValidated", false),
            )
        }
    }

    /**
     * Returns true when a system must be prepared or resumed. Existing rows are never removed:
     * an interrupted index is completed by stable Libretro IDs and READY systems are skipped.
     */
    suspend fun needsIndex(system: CatalogSystem): Boolean = withContext(Dispatchers.IO) {
        val state = db.catalogStateDao().byId(system.id)
        val count = db.gameDao().countSystem(system.id)
        val arcadeWithRomSet = if (system.id == CatalogPreparationPolicy.FBNEO_SYSTEM_ID) {
            db.gameDao().countArcadeWithRomSet()
        } else {
            count
        }
        CatalogPreparationPolicy.needsIndex(
            systemId = system.id,
            status = state?.status,
            indexedGames = count,
            arcadeGamesWithRomSet = arcadeWithRomSet,
        )
    }

    /**
     * Forces the next preparation pass to verify every known system without deleting indexed
     * games. Stable IDs and [CatalogIndexMergePolicy] keep ROM URIs and runtime/user state intact.
     */
    suspend fun invalidateAllPreparedSystems(): Int = withContext(Dispatchers.IO) {
        db.catalogStateDao().markAllStale()
    }

    suspend fun index(
        system: CatalogSystem,
        progress: (CatalogIndexProgress) -> Unit,
    ): Result<Int> = withContext(Dispatchers.IO) {
        db.catalogStateDao().upsert(CatalogSystemStateEntity(system.id, "DOWNLOADING", null, 0, null))
        try {
            coroutineContext.ensureActive()
            val genres = downloadGenres(system, progress)
            progress(CatalogIndexProgress("Descargando índice oficial de ${system.name}…"))
            val connection = open(system.datUrl)
            try {
                require(connection.responseCode in 200..299) { "${system.name} respondió HTTP ${connection.responseCode}" }
                val occurrences = mutableMapOf<String, Int>()
                val total = connection.inputStream.bufferedReader().use { reader ->
                    var inserted = 0
                    for (batch in LibretroDatParser().batches(reader, 500)) {
                        coroutineContext.ensureActive()
                        val entities = batch.map { item ->
                            val identity = item.name.ifBlank { item.description }
                            val baseSlug = TextNormalizer.slug(identity).ifBlank { "game" }
                            val occurrence = occurrences.merge(baseSlug, 1, Int::plus) ?: 1
                            val suffix = if (occurrence == 1) "" else {
                                val signature = listOf(identity, item.romName.orEmpty(), occurrence.toString()).joinToString("|")
                                "-${shortHash(signature)}"
                            }
                            val romSet = item.romName
                                ?.substringAfterLast('/')
                                ?.substringAfterLast('\\')
                                ?.substringBeforeLast('.')
                                ?.takeIf(String::isNotBlank)
                            val metadata = curated.find(item.description, romSet)
                            val normalized = TextNormalizer.normalize(item.description)
                            GameEntity(
                                id = "libretro-${system.id}-$baseSlug$suffix",
                                title = item.description,
                                normalizedTitle = normalized,
                                platform = system.platform,
                                systemId = system.id,
                                year = item.releaseYear ?: extractYear(item.description),
                                region = item.region ?: extractRegion(item.description),
                                thumbnailRepo = system.thumbnailRepo,
                                source = "libretro",
                                launchValidated = system.launchValidated,
                                coreId = system.coreId,
                                virtualExtension = system.virtualExtension,
                                canonicalTitle = CatalogFilters.canonicalTitle(item.description),
                                genre = genres[normalized] ?: metadata?.genre ?: "Sin clasificar",
                                romSet = romSet ?: metadata?.romSet,
                                biosSetsCsv = metadata?.biosSets.orEmpty().joinToString(","),
                            )
                        }
                        db.withTransaction {
                            val existing = db.gameDao().byIds(entities.map { it.id }).associateBy { it.id }
                            db.gameDao().insertAll(
                                entities.map { indexed ->
                                    CatalogIndexMergePolicy.merge(indexed, existing[indexed.id])
                                },
                            )
                        }
                        inserted += entities.size
                        progress(
                            CatalogIndexProgress(
                                stage = "${system.name}: $inserted juegos preparados",
                                preparedGames = inserted,
                            ),
                        )
                    }
                    inserted
                }
                coroutineContext.ensureActive()
                db.catalogStateDao().upsert(CatalogSystemStateEntity(system.id, "READY", System.currentTimeMillis(), total, null))
                progress(
                    CatalogIndexProgress(
                        stage = "${system.name}: $total juegos disponibles sin conexión",
                        preparedGames = total,
                    ),
                )
                Result.success(total)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            db.catalogStateDao().upsert(
                CatalogSystemStateEntity(
                    system.id,
                    "CANCELLED",
                    null,
                    db.gameDao().countSystem(system.id),
                    "Indexación interrumpida; el progreso guardado se puede reanudar",
                ),
            )
            throw cancelled
        } catch (error: Throwable) {
            db.catalogStateDao().upsert(
                CatalogSystemStateEntity(system.id, "ERROR", null, db.gameDao().countSystem(system.id), error.message ?: error.javaClass.simpleName),
            )
            Result.failure(error)
        }
    }

    private suspend fun downloadGenres(
        system: CatalogSystem,
        progress: (CatalogIndexProgress) -> Unit,
    ): Map<String, String> {
        val url = system.genreDatUrl ?: return emptyMap()
        return runCatching {
            coroutineContext.ensureActive()
            progress(CatalogIndexProgress("Consultando géneros verificados de ${system.name}…"))
            val connection = open(url)
            try {
                if (connection.responseCode !in 200..299) return@runCatching emptyMap()
                connection.inputStream.bufferedReader().use { GenreDatParser().parse(it) }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { emptyMap() }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 25_000
        readTimeout = 120_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "OzRetroRom/0.6.0-beta.4")
        setRequestProperty("Accept", "text/plain, application/octet-stream;q=0.9, */*;q=0.5")
        connect()
    }

    private fun deriveGenreUrl(systemId: String, datUrl: String): String? {
        if (systemId == "arcade-fbneo") return null
        val encodedName = datUrl.substringAfterLast('/')
        return "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/genre/$encodedName"
    }

    private fun shortHash(value: String): String = MessageDigest.getInstance("SHA-1")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(5)
        .joinToString("") { "%02x".format(it) }

    private fun extractYear(value: String): Int? = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(value)?.value?.toIntOrNull()

    private fun extractRegion(value: String): String {
        val groups = Regex("\\(([^)]*)\\)").findAll(value).map { it.groupValues[1] }.toList()
        val region = groups.firstOrNull { candidate ->
            listOf(
                "World", "USA", "Europe", "Japan", "Korea", "Australia", "Brazil", "Asia", "Spain",
                "France", "Germany", "Italy", "Sweden", "Canada", "Taiwan", "Hong Kong",
            ).any { candidate.contains(it, ignoreCase = true) }
        }
        return region ?: "Unknown"
    }
}

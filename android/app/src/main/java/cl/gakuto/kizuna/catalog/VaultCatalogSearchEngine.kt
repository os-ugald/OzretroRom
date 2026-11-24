package cl.gakuto.kizuna.catalog

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.LibretroThumbnailHelper
import cl.gakuto.kizuna.util.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream

data class CatalogPlatformChoice(
    val id: String,
    val label: String,
    val offlineSharded: Boolean,
)

data class CatalogSearchItem(
    val id: String,
    val title: String,
    val edition: String,
    val platform: String,
    val filename: String,
    val size: Long,
    val infoHash: String = "",
    val fileIndex: Int = -1,
    val exactFullPath: String = "",
    val trackerUrls: List<String> = emptyList(),
    val provider: String = "MiNERVA",
    val archiveUrl: String = "",
    val arcadeCore: String = "",
    val searchText: String = "",
    val thumbnailRepo: String = "",
    val coreId: String = "",
    val virtualExtension: String = "zip",
)

object VaultCatalogSearchEngine {
    val PLATFORMS: List<CatalogPlatformChoice> = listOf(
        CatalogPlatformChoice("", "Todas", offlineSharded = true),
        CatalogPlatformChoice("snes", "SNES", offlineSharded = true),
        CatalogPlatformChoice("nes", "NES", offlineSharded = true),
        CatalogPlatformChoice("genesis", "Genesis", offlineSharded = true),
        CatalogPlatformChoice("gba", "GBA", offlineSharded = true),
        CatalogPlatformChoice("n64", "N64", offlineSharded = true),
        CatalogPlatformChoice("ps1", "PS1", offlineSharded = true),
        CatalogPlatformChoice("arcade", "Arcade", offlineSharded = true),
        CatalogPlatformChoice("mame", "MAME", offlineSharded = true),
        CatalogPlatformChoice("nds", "NDS", offlineSharded = false),
        CatalogPlatformChoice("psp", "PSP", offlineSharded = false),
        CatalogPlatformChoice("dc", "Dreamcast", offlineSharded = false),
        CatalogPlatformChoice("gc", "GameCube", offlineSharded = false),
        CatalogPlatformChoice("wii", "Wii", offlineSharded = false),
    )

    private val REMOTE_CONSOLE_NAMES = mapOf(
        "nds" to "Nintendo DS",
        "psp" to "PlayStation Portable",
        "dc" to "Sega Dreamcast",
        "gc" to "Nintendo GameCube",
        "gamecube" to "Nintendo GameCube",
        "wii" to "Nintendo Wii",
        "ps2" to "Sony PlayStation 2",
        "gb" to "Game Boy",
        "gbc" to "Game Boy Color",
        "saturn" to "Sega Saturn",
        "segacd" to "Sega CD",
        "sms" to "Sega Master System",
        "gg" to "Sega Game Gear",
        "pce" to "PC Engine",
        "snes" to "Super Nintendo Entertainment System",
        "nes" to "Nintendo Entertainment System",
        "genesis" to "Sega Mega Drive",
        "gba" to "Game Boy Advance",
        "n64" to "Nintendo 64",
        "ps1" to "Sony PlayStation",
        "arcade" to "Arcade",
    )

    private val IA_COLLECTION_QUERIES = mapOf(
        "arcade" to "internetarcade",
        "mame" to "internetarcade",
        "nds" to "(\"Nintendo DS\" OR NDS)",
        "psp" to "(PSP OR \"PlayStation Portable\")",
        "dc" to "(Dreamcast)",
        "gc" to "(GameCube OR \"Nintendo GameCube\")",
        "wii" to "(Wii OR \"Nintendo Wii\")",
        "ps2" to "(\"PlayStation 2\" OR PS2)",
        "3ds" to "(\"Nintendo 3DS\" OR 3DS)",
        "saturn" to "(Saturn)",
        "segacd" to "(\"Sega CD\" OR \"Mega-CD\")",
    )

    private const val IA_ROM_FILTER =
        "-collection:thingiverse -title:(\"TAS WIP\" OR \"Soundtrack\" OR \"Manual\" OR \"Commercial\" OR \"Boxart\" OR \"Holder\" OR \"for Xbox\") " +
            "AND (format:(\"ZIP\" OR \"7z\" OR \"ISO Image\" OR \"CHD\" OR \"RVZ\" OR \"WBFS\" OR \"Nintendo DS\" OR \"GDI\" OR \"CSO\") " +
            "OR subject:(rom OR iso OR redump OR \"no-intro\" OR chd OR wbfs OR rvz OR nds OR psp OR dreamcast OR gamecube OR wii)) " +
            "AND mediatype:(software OR data)"

    private val NON_ROM_WORDS = listOf(
        "holder", "stand ", "3d print", "thingiverse", ".stl", "soundtrack",
        "ost ", "manual", "strategy guide", "tas wip", "boxart", "for xbox 360",
    )

    private val ROM_FILE_EXTENSIONS = listOf(
        ".nds", ".cso", ".iso", ".chd", ".rvz", ".wbfs", ".gdi", ".cdi",
        ".pbp", ".3ds", ".cia", ".zip", ".7z", ".rar", ".bin", ".cue",
        ".sfc", ".smc", ".md", ".gba", ".gbc", ".gb", ".z64", ".n64", ".nes",
    )

    private val IGNORED_META_EXTENSIONS = setOf(
        ".xml", ".sqlite", ".torrent", ".png", ".jpg", ".jpeg", ".gif",
        ".webp", ".txt", ".log", ".json", ".md5", ".sha1", ".sha256", ".stl",
    )

    @Volatile
    private var minervaRemoteDownUntilMs: Long = 0L

    private data class PlatformSpec(
        val displayName: String,
        val systemId: String,
        val thumbnailRepo: String,
        val coreId: String,
        val virtualExtension: String,
    )

    private val PLATFORM_SPECS = mapOf(
        "snes" to PlatformSpec("Super Nintendo", "snes", "Nintendo_-_Super_Nintendo_Entertainment_System", "snes9x_libretro_android.so", "sfc"),
        "nes" to PlatformSpec("Nintendo NES", "nes", "Nintendo_-_Nintendo_Entertainment_System", "mesen_libretro_android.so", "nes"),
        "genesis" to PlatformSpec("Mega Drive / Genesis", "genesis", "Sega_-_Mega_Drive_-_Genesis", "genesis_plus_gx_libretro_android.so", "md"),
        "gba" to PlatformSpec("Game Boy Advance", "gba", "Nintendo_-_Game_Boy_Advance", "mgba_libretro_android.so", "gba"),
        "n64" to PlatformSpec("Nintendo 64", "n64", "Nintendo_-_Nintendo_64", "mupen64plus_next_gles3_libretro_android.so", "n64"),
        "ps1" to PlatformSpec("PlayStation", "ps1", "Sony_-_PlayStation", "pcsx_rearmed_libretro_android.so", "chd"),
        "arcade" to PlatformSpec("Arcade (FBNeo / MAME)", "arcade-fbneo", "FBNeo_-_Arcade_Games", "FBNeo", "zip"),
        "mame" to PlatformSpec("MAME Arcade", "arcade-fbneo", "MAME", "mame2003_plus_libretro_android.so", "zip"),
        "nds" to PlatformSpec("Nintendo DS", "nds", "Nintendo_-_Nintendo_DS", "melonds_libretro_android.so", "nds"),
        "psp" to PlatformSpec("PlayStation Portable", "psp", "Sony_-_PlayStation_Portable", "ppsspp_libretro_android.so", "iso"),
        "dc" to PlatformSpec("Sega Dreamcast", "dreamcast", "Sega_-_Dreamcast", "flycast_libretro_android.so", "chd"),
        "gc" to PlatformSpec("Nintendo GameCube", "gamecube", "Nintendo_-_GameCube", "dolphin_libretro_android.so", "iso"),
        "wii" to PlatformSpec("Nintendo Wii", "wii", "Nintendo_-_Wii", "dolphin_libretro_android.so", "wbfs"),
        "ps2" to PlatformSpec("PlayStation 2", "ps2", "Sony_-_PlayStation_2", "play_libretro_android.so", "iso"),
    )

    private val TAG_REGEX = Regex("""\([^)]*\)|\[[^\]]*]""")
    private val PRIORITY_TAGS = listOf("usa", "world", "en,fr,es", "spanish", "esp", "europe", "japan")

    private val lock = Any()
    @Volatile private var loaded = false
    @Volatile private var allEntries: List<CatalogSearchItem> = emptyList()
    @Volatile private var byPlatform: Map<String, List<CatalogSearchItem>> = emptyMap()
    @Volatile private var fbneoEntries: List<CatalogSearchItem> = emptyList()
    @Volatile private var mameEntries: List<CatalogSearchItem> = emptyList()

    val totalLoadedCount: Int get() = allEntries.size

    fun resetForTest() {
        synchronized(lock) {
            allEntries = emptyList()
            byPlatform = emptyMap()
            fbneoEntries = emptyList()
            mameEntries = emptyList()
            loaded = false
        }
    }

    internal fun seedEntriesForTest(items: List<CatalogSearchItem>) {
        synchronized(lock) {
            indexEntries(items)
            loaded = true
        }
    }

    private fun indexEntries(items: List<CatalogSearchItem>) {
        allEntries = items
        byPlatform = items.groupBy { it.platform }
        fbneoEntries = items.filter { it.platform == "arcade" && it.arcadeCore == "FBNeo" }
        mameEntries = items.filter { it.arcadeCore == "MAME" || it.platform == "mame" }
    }

    suspend fun ensureLoaded(context: Context): Int {
        if (loaded) return allEntries.size
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (loaded) return@synchronized allEntries.size
                val collected = ArrayList<CatalogSearchItem>(52_000)
                val trackerPool = HashMap<String, List<String>>()

                for (plat in listOf("snes", "nes", "genesis", "gba")) {
                    runCatching {
                        context.assets.open("source-index/$plat.json.gz.bin").use { raw ->
                            parseStandardShardStream(GZIPInputStream(raw), plat, trackerPool, collected)
                        }
                    }
                }
                runCatching {
                    context.assets.open("source-index/minerva-expansion.json.gz.bin").use { raw ->
                        parseExpansionStream(GZIPInputStream(raw), trackerPool, collected)
                    }
                }
                runCatching {
                    context.assets.open("source-index/mame-ia.json.gz.bin").use { raw ->
                        parseMameIaStream(GZIPInputStream(raw), collected)
                    }
                }
                indexEntries(collected)
                loaded = true
                collected.size
            }
        }
    }

    fun searchLocal(query: String, platform: String = "", limit: Int = 80): List<CatalogSearchItem> {
        val q = query.trim().lowercase(Locale.ROOT)
        val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val plat = platform.trim().lowercase(Locale.ROOT)

        if (plat == "mame") {
            return bucketRank(mameEntries, q, tokens).take(limit)
        }
        if (plat == "arcade") {
            val fbHits = bucketRank(fbneoEntries, q, tokens)
            val mameHits = bucketRank(mameEntries, q, tokens)
            if (mameHits.isEmpty()) return fbHits.take(limit)
            if (fbHits.isEmpty()) return mameHits.take(limit)
            val mameQuota = minOf(mameHits.size, maxOf(limit / 2, limit - fbHits.size))
            val fbQuota = minOf(fbHits.size, limit - mameQuota)
            val fbSel = fbHits.take(fbQuota)
            val mameSel = mameHits.take(mameQuota)
            val merged = ArrayList<CatalogSearchItem>(limit)
            val seen = HashSet<String>(limit)
            for (e in fbSel) if (e.title.lowercase(Locale.ROOT).startsWith(q) && seen.add(e.id)) merged += e
            for (e in mameSel) if (e.title.lowercase(Locale.ROOT).startsWith(q) && seen.add(e.id)) merged += e
            for (e in fbSel + mameSel) if (seen.add(e.id)) merged += e
            return merged.take(limit)
        }

        val pool = if (plat.isBlank()) allEntries else byPlatform[plat].orEmpty()
        val ranked = bucketRank(pool, q, tokens)
        if (plat.isBlank() && ranked.size > limit) {
            val mameMatches = ranked.filter { it.arcadeCore == "MAME" }
            val nonMame = ranked.filter { it.arcadeCore != "MAME" }
            if (mameMatches.isNotEmpty()) {
                val mameSlice = mameMatches.take(minOf(mameMatches.size, limit / 4))
                val restSlice = nonMame.take(limit - mameSlice.size)
                return (restSlice + mameSlice).take(limit)
            }
        }
        return ranked.take(limit)
    }

    /** Clean deduplicated retail highlights when browsing a console without a query. */
    fun browsePlatform(platform: String, limit: Int = 90): List<CatalogSearchItem> {
        val plat = platform.trim().lowercase(Locale.ROOT)
        val pool = when (plat) {
            "mame" -> mameEntries
            "arcade" -> fbneoEntries + mameEntries
            "" -> return emptyList()
            else -> byPlatform[plat].orEmpty()
        }
        if (pool.isEmpty()) return emptyList()

        // Prefer retail editions (no Beta/Proto/Demo/Sample/Unl/Pirate) and deduplicate by title stem
        val bestByStem = LinkedHashMap<String, CatalogSearchItem>()
        for (item in pool) {
            val edLow = item.edition.lowercase(Locale.ROOT)
            val titleLow = item.title.lowercase(Locale.ROOT)
            if (listOf("demo", "proto", "beta", "sample", "program", "bios", "[b]").any { it in edLow || it in titleLow }) {
                continue
            }
            val key = "${item.platform}|${titleLow}"
            val current = bestByStem[key]
            if (current == null || regionScore(item) < regionScore(current)) {
                bestByStem[key] = item
            }
        }
        return bestByStem.values
            .sortedWith(compareBy<CatalogSearchItem>({ regionScore(it) }, { it.title.lowercase(Locale.ROOT) }))
            .take(limit)
    }

    suspend fun searchOnline(query: String, platform: String = "", pcHubUrl: String = ""): List<CatalogSearchItem> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()
            val plat = platform.trim().lowercase(Locale.ROOT)
            val results = ArrayList<CatalogSearchItem>()

            // 1. Optional Oz PC Hub LAN catalog search (fastest if connected on Wi-Fi)
            if (pcHubUrl.isNotBlank()) {
                runCatching {
                    val hubItems = cl.gakuto.kizuna.hub.GameVaultHubClient.searchCatalog(pcHubUrl, q, plat)
                    results.addAll(hubItems)
                }
            }

            // 2. Remote MiNERVA API with 5-min circuit breaker so timeouts never stall IA discovery
            val remoteConsole = REMOTE_CONSOLE_NAMES[plat]
            val nowMs = System.currentTimeMillis()
            if (remoteConsole != null && nowMs >= minervaRemoteDownUntilMs) {
                runCatching {
                    val url = "https://minerva-archive.org/v1/api/rom/search?query=" +
                        urlEncode(q) + "&console=" + urlEncode(remoteConsole) + "&limit=24"
                    val json = httpGetText(url, 2500)
                    results.addAll(parseMinervaRemoteJson(json, plat))
                }.onFailure {
                    minervaRemoteDownUntilMs = System.currentTimeMillis() + 300_000L
                }
            }

            // 3. Filtered Internet Archive ROM discovery (covers DS, PSP, Dreamcast, GameCube, Wii, MAME & global search)
            if (results.size < 8) {
                runCatching {
                    val safeQ = q.replace(Regex("[\"()]+"), " ").trim()
                    val iaCol = IA_COLLECTION_QUERIES[plat]
                    val queryExpr = when {
                        iaCol != null && (plat == "arcade" || plat == "mame") ->
                            "(title:($safeQ) OR identifier:($safeQ)) AND ($iaCol) AND mediatype:software"
                        iaCol != null ->
                            "(title:($safeQ) OR identifier:($safeQ)) AND ($iaCol) AND $IA_ROM_FILTER"
                        else ->
                            "(title:($safeQ) OR identifier:($safeQ)) AND (\"Nintendo DS\" OR NDS OR PSP OR \"PlayStation Portable\" OR Dreamcast OR GameCube OR \"Nintendo Wii\" OR internetarcade) AND $IA_ROM_FILTER"
                    }
                    val url = "https://archive.org/advancedsearch.php?q=${urlEncode(queryExpr)}" +
                        "&fl[]=identifier,title,item_size,subject&rows=24&page=1&output=json"
                    val json = httpGetText(url, 6000)
                    results.addAll(parseInternetArchiveJson(json, plat, inferPlatform = plat.isBlank()))
                }
            }

            results.distinctBy { it.id }.take(30)
        }

    fun bucketRank(entries: List<CatalogSearchItem>, query: String, tokens: List<String>): List<CatalogSearchItem> {
        val exact = ArrayList<CatalogSearchItem>()
        val prefix = ArrayList<CatalogSearchItem>()
        val word = ArrayList<CatalogSearchItem>()
        val contains = ArrayList<CatalogSearchItem>()

        for (e in entries) {
            if (!tokens.all { it in e.searchText }) continue
            val tLow = e.title.lowercase(Locale.ROOT)
            when {
                tLow == query -> exact += e
                tLow.startsWith(query) -> prefix += e
                tLow.split(' ').any { it.startsWith(query) } -> word += e
                else -> contains += e
            }
        }
        val tieBreak = compareBy<CatalogSearchItem>({ regionScore(it) }, { it.title.length }, { it.title.lowercase(Locale.ROOT) })
        exact.sortWith(tieBreak)
        prefix.sortWith(tieBreak)
        word.sortWith(tieBreak)
        contains.sortWith(tieBreak)
        return exact + prefix + word + contains
    }

    private fun regionScore(item: CatalogSearchItem): Int {
        val low = "${item.edition} ${item.filename}".lowercase(Locale.ROOT)
        if (listOf("beta", "proto", "demo", "sample").any { it in low }) return 99
        PRIORITY_TAGS.forEachIndexed { idx, tag ->
            if (tag in low) return idx
        }
        return if (item.provider == "MiNERVA" || item.provider == "FBNeo") 20 else 30
    }

    fun toAuthorizedSource(item: CatalogSearchItem): AuthorizedSource? {
        if (item.infoHash.isNotBlank()) {
            val magnet = buildString {
                append("magnet:?xt=urn:btih:").append(item.infoHash)
                if (item.filename.isNotBlank()) append("&dn=").append(URLEncoder.encode(item.filename, "UTF-8"))
                item.trackerUrls.forEach { tr ->
                    append("&tr=").append(URLEncoder.encode(tr, "UTF-8"))
                }
            }
            return AuthorizedSource(
                value = magnet,
                expectedFileName = item.filename.ifBlank { null },
                expectedSize = item.size,
                torrentFileIndex = item.fileIndex.takeIf { it >= 0 },
                torrentFilePath = item.exactFullPath.ifBlank { null },
                sourceLabel = item.provider.ifBlank { "MiNERVA" },
            )
        }
        if (item.archiveUrl.startsWith("https://", ignoreCase = true) ||
            item.archiveUrl.startsWith("http://", ignoreCase = true)
        ) {
            return AuthorizedSource(
                value = item.archiveUrl,
                expectedFileName = item.filename.ifBlank { null },
                expectedSize = item.size,
                sourceLabel = item.provider.ifBlank { "Archive" },
            )
        }
        return null
    }

    /**
     * Resolves magnet coordinates for online MiNERVA hits or picks the real ROM file inside
     * an Internet Archive item (`https://archive.org/details/<identifier>`).
     */
    suspend fun resolveRemoteSourceIfNeeded(item: CatalogSearchItem): CatalogSearchItem {
        if (item.archiveUrl.startsWith("https://archive.org/details/", ignoreCase = true)) {
            val ident = item.archiveUrl.substringAfter("/details/").substringBefore('/').trim()
            if (ident.isNotBlank()) {
                return withContext(Dispatchers.IO) {
                    runCatching {
                        val metaJson = httpGetText("https://archive.org/metadata/${urlEncode(ident)}/files", 7500)
                        val files = JSONObject(metaJson).optJSONArray("result") ?: return@runCatching item
                        var bestName = ""
                        var bestSize = 0L
                        var bestRank = Int.MAX_VALUE
                        for (i in 0 until files.length()) {
                            val f = files.optJSONObject(i) ?: continue
                            val name = f.optString("name").trim()
                            if (name.isBlank() || name.endsWith("_meta.xml", ignoreCase = true)) continue
                            val low = name.lowercase(Locale.ROOT)
                            if (IGNORED_META_EXTENSIONS.any { low.endsWith(it) }) continue
                            val extIdx = ROM_FILE_EXTENSIONS.indexOfFirst { low.endsWith(it) }
                            val rank = if (extIdx >= 0) extIdx else 999
                            if (rank < bestRank) {
                                bestRank = rank
                                bestName = name
                                bestSize = f.optString("size").toLongOrNull() ?: f.optLong("size", 0L)
                            }
                        }
                        if (bestName.isNotBlank()) {
                            val encodedSegments = bestName.split('/').joinToString("/") { urlEncode(it) }
                            item.copy(
                                filename = bestName.substringAfterLast('/'),
                                size = if (bestSize > 0L) bestSize else item.size,
                                archiveUrl = "https://archive.org/download/${urlEncode(ident)}/$encodedSegments",
                            )
                        } else {
                            item
                        }
                    }.getOrDefault(item)
                }
            }
        }
        if (item.infoHash.isNotBlank() || item.archiveUrl.isNotBlank()) return item
        if (!item.id.startsWith("minerva-remote-")) return item
        val recordId = item.id.removePrefix("minerva-remote-").toLongOrNull() ?: return item
        return withContext(Dispatchers.IO) {
            runCatching {
                val html = httpGetText("https://minerva-archive.org/rom?id=$recordId", 7500)
                val hrefRaw = Regex("<a\\s+id=\"magnet\"\\s+href=\"([^\"]+)\"").find(html)
                    ?.groupValues?.getOrNull(1)?.replace("&amp;", "&")
                    ?: return@runCatching item
                val infoHash = Regex("btih:([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE)
                    .find(hrefRaw)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT)
                    ?: return@runCatching item
                val trackers = Regex("[?&]tr=([^&]+)").findAll(hrefRaw)
                    .mapNotNull { runCatching { java.net.URLDecoder.decode(it.groupValues[1], "UTF-8") }.getOrNull() }
                    .toList()
                item.copy(
                    infoHash = infoHash,
                    trackerUrls = trackers,
                )
            }.getOrDefault(item)
        }
    }

    fun toGameEntity(item: CatalogSearchItem): GameEntity {
        val spec = PLATFORM_SPECS[item.platform.lowercase(Locale.ROOT)]
        val systemId = spec?.systemId ?: item.platform.lowercase(Locale.ROOT)
        val repo = item.thumbnailRepo.ifBlank {
            spec?.thumbnailRepo ?: LibretroThumbnailHelper.repoForSystem(systemId).orEmpty()
        }
        val core = item.coreId.ifBlank { spec?.coreId ?: "" }.takeIf { it.isNotBlank() }
        val ext = item.virtualExtension.ifBlank {
            item.filename.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: spec?.virtualExtension ?: "zip"
        }
        val clean = item.title.ifBlank { item.filename.substringBeforeLast('.') }
        val entityId = "vault-cat-${systemId}-${sha1Short(item.id)}"
        val region = extractRegion(item.edition.ifBlank { item.filename })
        return GameEntity(
            id = entityId,
            title = if (clean.isNotBlank()) clean else "ROM",
            normalizedTitle = TextNormalizer.normalize(clean),
            platform = spec?.displayName ?: item.platform.uppercase(Locale.ROOT),
            systemId = systemId,
            year = null,
            region = region,
            thumbnailRepo = repo.takeIf { it.isNotBlank() },
            source = "minerva-catalog",
            launchValidated = true,
            coreId = core,
            virtualExtension = ext,
            canonicalTitle = clean,
            romSet = if (systemId == "arcade-fbneo") item.filename.substringBeforeLast('.') else null,
            romgiAvailable = true,
        )
    }

    private fun extractEdition(filename: String, fallback: String = ""): String {
        val stem = filename.substringBeforeLast('.')
        val tags = TAG_REGEX.findAll(stem).map { it.value }.toList()
        if (tags.isNotEmpty()) return tags.joinToString(" ")
        return fallback
    }

    private fun extractRegion(text: String): String {
        val low = text.lowercase(Locale.ROOT)
        return when {
            "usa, europe" in low -> "USA, Europe"
            "usa" in low -> "USA"
            "world" in low -> "World"
            "europe" in low -> "Europe"
            "japan" in low -> "Japan"
            "spain" in low || "spanish" in low -> "Spain"
            else -> "World"
        }
    }

    private data class GameStub(val title: String)
    private data class ReleaseStub(val gameId: String, val region: String)
    private data class CandidateStub(
        val id: String,
        val releaseId: String,
        val fileName: String,
        val size: Long,
        val infoHash: String,
        val fileIndex: Int,
        val exactFullPath: String,
        val trackers: List<String>,
    )

    private fun parseStandardShardStream(
        stream: GZIPInputStream,
        platform: String,
        trackerPool: HashMap<String, List<String>>,
        out: MutableList<CatalogSearchItem>,
    ) {
        val spec = PLATFORM_SPECS[platform]
        val games = HashMap<String, GameStub>(4000)
        val releases = HashMap<String, ReleaseStub>(6000)
        val candidates = ArrayList<CandidateStub>(7000)

        JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "games" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var gid = ""
                            var title = ""
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "gameId" -> gid = r.nextStringOrEmpty()
                                    "canonicalTitle" -> title = r.nextStringOrEmpty()
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            if (gid.isNotEmpty()) games[gid] = GameStub(title)
                        }
                        r.endArray()
                    }
                    "releases" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var rid = ""
                            var gid = ""
                            var region = ""
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "releaseId" -> rid = r.nextStringOrEmpty()
                                    "gameId" -> gid = r.nextStringOrEmpty()
                                    "region" -> region = r.nextStringOrEmpty()
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            if (rid.isNotEmpty()) releases[rid] = ReleaseStub(gid, region)
                        }
                        r.endArray()
                    }
                    "candidates" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var cid = ""
                            var rid = ""
                            var fname = ""
                            var size = 0L
                            var infoHash = ""
                            var fileIndex = -1
                            var exactPath = ""
                            var trackers: List<String> = emptyList()

                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "candidateId" -> cid = r.nextStringOrEmpty()
                                    "releaseId" -> rid = r.nextStringOrEmpty()
                                    "artifact" -> {
                                        r.beginObject()
                                        while (r.hasNext()) {
                                            when (r.nextName()) {
                                                "fileName" -> fname = r.nextStringOrEmpty()
                                                "size" -> size = r.nextLongOrZero()
                                                else -> r.skipValue()
                                            }
                                        }
                                        r.endObject()
                                    }
                                    "torrent" -> {
                                        r.beginObject()
                                        while (r.hasNext()) {
                                            when (r.nextName()) {
                                                "infoHash" -> infoHash = r.nextStringOrEmpty()
                                                "fileIndex" -> fileIndex = r.nextIntOrDefault(-1)
                                                "exactFullPath" -> exactPath = r.nextStringOrEmpty()
                                                "trackerUrls" -> {
                                                    val list = ArrayList<String>(16)
                                                    r.beginArray()
                                                    while (r.hasNext()) list += r.nextStringOrEmpty()
                                                    r.endArray()
                                                    val cacheKey = if (infoHash.isNotEmpty()) infoHash else list.firstOrNull().orEmpty()
                                                    trackers = trackerPool.getOrPut(cacheKey) { list }
                                                }
                                                else -> r.skipValue()
                                            }
                                        }
                                        r.endObject()
                                    }
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            if (cid.isNotEmpty() && fname.isNotEmpty()) {
                                candidates += CandidateStub(cid, rid, fname, size, infoHash, fileIndex, exactPath, trackers)
                            }
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }

        for (c in candidates) {
            val rel = releases[c.releaseId]
            val game = rel?.let { games[it.gameId] }
            val title = game?.title?.takeIf { it.isNotBlank() } ?: c.fileName.substringBeforeLast('.')
            val edition = extractEdition(c.fileName, rel?.region.orEmpty())
            val finalTrackers = if (c.trackers.isNotEmpty()) c.trackers else trackerPool[c.infoHash].orEmpty()
            out += CatalogSearchItem(
                id = c.id,
                title = title,
                edition = edition,
                platform = platform,
                filename = c.fileName,
                size = c.size,
                infoHash = c.infoHash,
                fileIndex = c.fileIndex,
                exactFullPath = c.exactFullPath,
                trackerUrls = finalTrackers,
                provider = "MiNERVA",
                searchText = "$title $edition ${c.fileName}".lowercase(Locale.ROOT),
                thumbnailRepo = spec?.thumbnailRepo.orEmpty(),
                coreId = spec?.coreId.orEmpty(),
                virtualExtension = spec?.virtualExtension ?: "zip",
            )
        }
    }

    private fun parseExpansionStream(
        stream: GZIPInputStream,
        trackerPool: HashMap<String, List<String>>,
        out: MutableList<CatalogSearchItem>,
    ) {
        JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() != "collections") {
                    r.skipValue()
                    continue
                }
                r.beginArray()
                while (r.hasNext()) {
                    var plat = ""
                    var infoHash = ""
                    var directory = ""
                    var trackers: List<String> = emptyList()
                    val tempFiles = ArrayList<TempExpFile>(512)

                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "platform" -> plat = r.nextStringOrEmpty().lowercase(Locale.ROOT)
                            "infoHash" -> infoHash = r.nextStringOrEmpty()
                            "directory" -> directory = r.nextStringOrEmpty()
                            "trackers" -> {
                                val list = ArrayList<String>(24)
                                r.beginArray()
                                while (r.hasNext()) list += r.nextStringOrEmpty()
                                r.endArray()
                                trackers = list
                            }
                            "files" -> {
                                var loopIdx = 0
                                r.beginArray()
                                while (r.hasNext()) {
                                    var fn = ""
                                    var title = ""
                                    var ed = ""
                                    var sz = 0L
                                    var idx = loopIdx
                                    r.beginObject()
                                    while (r.hasNext()) {
                                        when (r.nextName()) {
                                            "filename" -> fn = r.nextStringOrEmpty()
                                            "title" -> title = r.nextStringOrEmpty()
                                            "edition" -> ed = r.nextStringOrEmpty()
                                            "size" -> sz = r.nextLongOrZero()
                                            "index" -> idx = r.nextIntOrDefault(loopIdx)
                                            else -> r.skipValue()
                                        }
                                    }
                                    r.endObject()
                                    if (fn.isNotEmpty()) {
                                        tempFiles += TempExpFile(idx, fn, title, ed, sz)
                                    }
                                    loopIdx++
                                }
                                r.endArray()
                            }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()

                    val spec = PLATFORM_SPECS[plat]
                    val sharedTrackers = trackerPool.getOrPut(infoHash) { trackers }
                    for (f in tempFiles) {
                        val resolvedTitle = f.title.ifBlank { f.filename.substringBeforeLast('.') }
                        val resolvedEd = f.edition.ifBlank { extractEdition(f.filename) }
                        val relPath = if (directory.isNotBlank()) "$directory/${f.filename}" else f.filename
                        val idSeed = "minerva\u0000$plat\u0000$infoHash\u0000${f.index}\u0000$relPath"
                        out += CatalogSearchItem(
                            id = sha256Hex(idSeed),
                            title = resolvedTitle,
                            edition = resolvedEd,
                            platform = plat,
                            filename = f.filename,
                            size = f.size,
                            infoHash = infoHash,
                            fileIndex = f.index,
                            exactFullPath = relPath,
                            trackerUrls = sharedTrackers,
                            provider = if (plat == "arcade") "MiNERVA · FBNeo" else "MiNERVA",
                            arcadeCore = if (plat == "arcade") "FBNeo" else "",
                            searchText = "$resolvedTitle $resolvedEd ${f.filename}".lowercase(Locale.ROOT),
                            thumbnailRepo = spec?.thumbnailRepo.orEmpty(),
                            coreId = spec?.coreId.orEmpty(),
                            virtualExtension = spec?.virtualExtension ?: "zip",
                        )
                    }
                }
                r.endArray()
            }
            r.endObject()
        }
    }

    private data class TempExpFile(
        val index: Int,
        val filename: String,
        val title: String,
        val edition: String,
        val size: Long,
    )

    private fun parseMameIaStream(stream: GZIPInputStream, out: MutableList<CatalogSearchItem>) {
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        out.addAll(parseMameJsonString(text))
    }

    internal fun parseMameJsonString(jsonText: String): List<CatalogSearchItem> {
        val root = JSONObject(jsonText)
        val items = root.optJSONArray("items") ?: return emptyList()
        val spec = PLATFORM_SPECS["mame"]
        val result = ArrayList<CatalogSearchItem>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val ident = item.optString("identifier").trim()
            if (ident.isBlank()) continue
            val fn = item.optString("filename").ifBlank { "$ident.zip" }.trim()
            val title = item.optString("title").ifBlank { ident }.trim()
            val romset = item.optString("romset").ifBlank { fn.substringBeforeLast('.') }.trim()
            val size = item.optLong("size", 0L)
            val id = "mame-ia-${sha256Hex(ident).take(16)}"
            result += CatalogSearchItem(
                id = id,
                title = title,
                edition = extractEdition(title, "MAME · Internet Archive"),
                platform = "arcade",
                filename = fn,
                size = size,
                provider = "MAME (Internet Archive)",
                archiveUrl = "https://archive.org/download/${urlEncode(ident)}/${urlEncode(fn)}",
                arcadeCore = "MAME",
                searchText = "$title $fn $romset mame arcade".lowercase(Locale.ROOT),
                thumbnailRepo = spec?.thumbnailRepo ?: "MAME",
                coreId = spec?.coreId.orEmpty(),
                virtualExtension = "zip",
            )
        }
        return result
    }

    internal fun parseMinervaRemoteJson(jsonText: String, platform: String): List<CatalogSearchItem> {
        val arr = JSONArray(jsonText)
        val spec = PLATFORM_SPECS[platform]
        val out = ArrayList<CatalogSearchItem>(arr.length())
        for (i in 0 until minOf(arr.length(), 24)) {
            val r = arr.optJSONObject(i) ?: continue
            val fullPath = r.optString("full_path").trim().removePrefix("./")
            val filename = fullPath.substringAfterLast('/').ifBlank { "rom-${r.optString("id", "0")}.zip" }
            val stem = filename.substringBeforeLast('.')
            val title = TAG_REGEX.replace(stem, "").replace(Regex("\\s+"), " ").trim().ifBlank { stem }
            val magnet = r.optString("magnet")
            val infoHash = Regex("btih:([a-fA-F0-9]{40})").find(magnet)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT).orEmpty()
            val edition = extractEdition(filename, "MiNERVA Online")
            out += CatalogSearchItem(
                id = "minerva-remote-${r.optString("id", sha1Short(filename))}",
                title = title,
                edition = edition,
                platform = platform.lowercase(Locale.ROOT),
                filename = filename,
                size = r.optLong("size", 0L),
                infoHash = infoHash,
                fileIndex = r.optInt("so_id", -1),
                exactFullPath = fullPath,
                provider = "MiNERVA Online",
                searchText = "$title $edition $filename".lowercase(Locale.ROOT),
                thumbnailRepo = spec?.thumbnailRepo.orEmpty(),
                coreId = spec?.coreId.orEmpty(),
                virtualExtension = spec?.virtualExtension ?: "zip",
            )
        }
        return out
    }

    private fun isCleanIaRomDoc(title: String, ident: String, plat: String): Boolean {
        val blob = "$title $ident".lowercase(Locale.ROOT)
        if (NON_ROM_WORDS.any { it in blob }) return false
        if (plat == "dc" && "xbox" in blob) return false
        if (plat == "gc" && "wii u" in blob) return false
        return true
    }

    private fun inferExtendedPlatform(title: String, ident: String, subject: String): String {
        val blob = "$title $ident $subject".lowercase(Locale.ROOT)
        return when {
            listOf("nintendo ds", "nds", ".nds", "desmume").any { it in blob } -> "nds"
            listOf("playstation portable", " psp", "(psp)", "psp-", "cso", "ppsspp").any { it in blob } -> "psp"
            listOf("dreamcast", "gdi", "dc-redump").any { it in blob } -> "dc"
            listOf("gamecube", "game cube", "rvz", "gc-").any { it in blob } -> "gc"
            listOf("nintendo wii", " wii", "(wii)", "wbfs").any { it in blob } -> "wii"
            listOf("playstation 2", "ps2").any { it in blob } -> "ps2"
            else -> "arcade"
        }
    }

    internal fun parseInternetArchiveJson(
        jsonText: String,
        platform: String,
        inferPlatform: Boolean = false,
    ): List<CatalogSearchItem> {
        val docs = JSONObject(jsonText).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        val out = ArrayList<CatalogSearchItem>(docs.length())
        for (i in 0 until docs.length()) {
            val d = docs.optJSONObject(i) ?: continue
            val ident = d.optString("identifier").trim()
            val rawTitle = d.optString("title").ifBlank { ident }.trim()
            if (ident.isBlank()) continue
            val subjRaw = d.opt("subject")
            val subject = when (subjRaw) {
                is JSONArray -> buildString {
                    for (j in 0 until subjRaw.length()) {
                        append(' ').append(subjRaw.optString(j))
                    }
                }
                else -> subjRaw?.toString().orEmpty()
            }
            val resolvedPlat = if (inferPlatform || platform.isBlank()) {
                inferExtendedPlatform(rawTitle, ident, subject)
            } else {
                platform.lowercase(Locale.ROOT)
            }
            val isArcade = resolvedPlat == "arcade" || resolvedPlat == "mame"
            if (!isArcade && !isCleanIaRomDoc(rawTitle, ident, resolvedPlat)) continue
            val spec = PLATFORM_SPECS[resolvedPlat]
            val ext = spec?.virtualExtension ?: "zip"
            val fn = if (ident.startsWith("arcade_")) "${ident.removePrefix("arcade_")}.zip" else "$ident.$ext"
            val archiveUrl = if (ident.startsWith("arcade_")) {
                "https://archive.org/download/${urlEncode(ident)}/${urlEncode(fn)}"
            } else {
                "https://archive.org/details/${urlEncode(ident)}"
            }
            val itemSize = d.optLong("item_size", 0L)
            out += CatalogSearchItem(
                id = "ia-${sha1Short(ident)}",
                title = rawTitle,
                edition = if (isArcade) "MAME · Internet Archive" else "IA · ${resolvedPlat.uppercase(Locale.ROOT)}",
                platform = resolvedPlat,
                filename = fn,
                size = itemSize,
                provider = if (isArcade) "MAME (Internet Archive)" else "Internet Archive",
                archiveUrl = archiveUrl,
                arcadeCore = if (isArcade) "MAME" else "",
                searchText = "$rawTitle $ident $fn $resolvedPlat".lowercase(Locale.ROOT),
                thumbnailRepo = spec?.thumbnailRepo.orEmpty(),
                coreId = spec?.coreId.orEmpty(),
                virtualExtension = ext,
            )
        }
        return out
    }

    private fun JsonReader.nextStringOrEmpty(): String =
        if (peek() == JsonToken.NULL) {
            nextNull()
            ""
        } else {
            nextString()
        }

    private fun JsonReader.nextLongOrZero(): Long =
        if (peek() == JsonToken.NULL) {
            nextNull()
            0L
        } else {
            runCatching { nextLong() }.getOrDefault(0L)
        }

    private fun JsonReader.nextIntOrDefault(fallback: Int): Int =
        if (peek() == JsonToken.NULL) {
            nextNull()
            fallback
        } else {
            runCatching { nextInt() }.getOrDefault(fallback)
        }

    private fun httpGetText(url: String, timeoutMs: Int): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "OzGameVault/2.0")
            require(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun sha1Short(value: String): String = MessageDigest.getInstance("SHA-1")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(12)

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

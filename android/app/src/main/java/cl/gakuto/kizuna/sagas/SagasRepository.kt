package cl.gakuto.kizuna.sagas

import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.LibretroThumbnailHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

data class SagaHeroArt(
    val repo: String,
    val titleName: String,
    val boxartName: String,
)

data class SagaMilestoneDef(
    val title: String,
    val year: Int,
    val consoleKey: String,
    val era: String,
    val matchPatterns: List<Regex>,
    val excludeMatchPatterns: List<Regex> = emptyList(),
    val snesAliasPatterns: List<Regex> = emptyList(),
    val excludeConsoles: Set<String> = emptySet(),
    val preferConsoles: List<String> = emptyList(),
    val allowCombo: Boolean = false,
    val minervaQuery: String = title,
    val libretroArt: Pair<String, String>? = null, // repo to exact PNG stem
)

data class SagaDefinition(
    val id: String,
    val name: String,
    val tagline: String,
    val foundedYear: Int,
    val creator: String,
    val accentHex: String,
    val history: String,
    val heroArt: SagaHeroArt,
    val includePatterns: List<Regex>,
    val excludePatterns: List<Regex> = emptyList(),
    val timeline: List<SagaMilestoneDef>,
)

data class SagaEntryItem(
    val order: Int,
    val title: String,
    val matchedTitle: String,
    val year: Int?,
    val era: String,
    val consoleKey: String,
    val isExtra: Boolean,
    val variantCount: Int,
    val localOwned: Boolean,
    val inCatalog: Boolean,
    val gameId: String?,
    val game: GameEntity?,
    val coverUrl: String?,
    val minervaQuery: String,
    val coverCandidates: List<String> = emptyList(),
)

data class SagaSnapshot(
    val id: String,
    val name: String,
    val tagline: String,
    val history: String,
    val foundedYear: Int,
    val creator: String,
    val accentHex: String,
    val localOwnedCount: Int,
    val catalogCount: Int,
    val totalCount: Int,
    val consoles: List<String>,
    val heroBannerUrl: String,
    val keyArtUrl: String,
    val entries: List<SagaEntryItem>,
    val heroBannerCandidates: List<String> = emptyList(),
    val keyArtCandidates: List<String> = emptyList(),
)

data class SagaSummaryRef(
    val id: String,
    val name: String,
    val localOwnedCount: Int,
    val totalCount: Int,
    val accentHex: String,
)

object SagasRepository {
    private const val EMBEDDED_CATALOG_RESOURCE = "sagas_catalog.json"
    private val parenTrailer = Regex("""\s*(?:\([^)]*\)|\[[^\]]*\])\s*""")
    private val nonAlnum = Regex("""[^a-z0-9]+""")

    private val catalogRef: AtomicReference<List<SagaDefinition>> by lazy {
        AtomicReference(loadEmbeddedCatalog())
    }

    val CURATED_SAGAS: List<SagaDefinition>
        get() = catalogRef.get()

    private fun rx(pattern: String): Regex = Regex(pattern, RegexOption.IGNORE_CASE)

    fun norm(value: String): String =
        nonAlnum.replace(value.lowercase(Locale.ROOT), " ").trim()

    fun cleanTitle(value: String): String {
        val noTags = parenTrailer.replace(value, " ").replace(Regex("""\s*#.*$"""), "")
        return noTags.replace(Regex("""\s+"""), " ").trim()
    }

    private fun cleanMatchPair(game: GameEntity): Pair<String, String> {
        val nc = norm(cleanTitle(game.title))
        val nr = norm(game.title)
        return nc to nr
    }

    private fun matchesAny(patterns: List<Regex>, a: String, b: String): Boolean {
        if (patterns.isEmpty()) return false
        return patterns.any { it.containsMatchIn(a) || (b.isNotBlank() && it.containsMatchIn(b)) }
    }

    fun libretroThumbnailUrl(repo: String, kind: String, name: String): String =
        LibretroThumbnailHelper.buildUrl(repo, kind, name)

    private fun readStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotBlank()) out += s
        }
        return out
    }

    private fun readRegexList(arr: JSONArray?): List<Regex> =
        readStringList(arr).map(::rx)

    fun parseCatalogJson(jsonText: String): List<SagaDefinition> {
        val root = JSONObject(jsonText)
        val sagasArr = root.optJSONArray("sagas") ?: JSONArray()
        val out = ArrayList<SagaDefinition>(sagasArr.length())
        for (i in 0 until sagasArr.length()) {
            val s = sagasArr.optJSONObject(i) ?: continue
            val heroObj = s.optJSONObject("heroArt") ?: JSONObject()
            val tlArr = s.optJSONArray("timeline") ?: JSONArray()
            val timeline = ArrayList<SagaMilestoneDef>(tlArr.length())
            for (j in 0 until tlArr.length()) {
                val ms = tlArr.optJSONObject(j) ?: continue
                val title = ms.optString("title")
                if (title.isBlank()) continue
                val artObj = ms.optJSONObject("libretroArt")
                val libretroArt = artObj?.let {
                    val r = it.optString("repo").trim()
                    val n = it.optString("name").trim()
                    if (r.isNotBlank() && n.isNotBlank()) r to n else null
                }
                timeline += SagaMilestoneDef(
                    title = title,
                    year = ms.optInt("year", 1990),
                    consoleKey = ms.optString("consoleKey", "other"),
                    era = ms.optString("era", ""),
                    matchPatterns = readRegexList(ms.optJSONArray("matchRegex")),
                    excludeMatchPatterns = readRegexList(ms.optJSONArray("excludeMatchRegex")),
                    snesAliasPatterns = readRegexList(ms.optJSONArray("snesAliasRegex")),
                    excludeConsoles = readStringList(ms.optJSONArray("excludeConsoles"))
                        .mapTo(linkedSetOf()) { it.lowercase(Locale.ROOT) },
                    preferConsoles = readStringList(ms.optJSONArray("preferConsoles")),
                    allowCombo = ms.optBoolean("allowCombo", false),
                    minervaQuery = ms.optString("minervaQuery").ifBlank { title },
                    libretroArt = libretroArt,
                )
            }
            out += SagaDefinition(
                id = s.optString("id"),
                name = s.optString("name"),
                tagline = s.optString("tagline"),
                foundedYear = s.optInt("foundedYear", 1990),
                creator = s.optString("creator"),
                accentHex = s.optString("accent", "#FFB000"),
                history = s.optString("history"),
                heroArt = SagaHeroArt(
                    repo = heroObj.optString("repo"),
                    titleName = heroObj.optString("titleName"),
                    boxartName = heroObj.optString("boxartName"),
                ),
                includePatterns = readRegexList(s.optJSONArray("includeRegex")),
                excludePatterns = readRegexList(s.optJSONArray("excludeRegex")),
                timeline = timeline,
            )
        }
        return out
    }

    private fun loadEmbeddedCatalog(): List<SagaDefinition> {
        val stream = SagasRepository::class.java.classLoader?.getResourceAsStream(EMBEDDED_CATALOG_RESOURCE)
            ?: error("No se encontró $EMBEDDED_CATALOG_RESOURCE en resources")
        val jsonText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseCatalogJson(jsonText)
    }

    fun reloadFromJson(jsonText: String): Int {
        val parsed = parseCatalogJson(jsonText)
        if (parsed.isNotEmpty()) {
            catalogRef.set(parsed)
        }
        return parsed.size
    }

    private fun pickBestMatch(
        candidates: List<GameEntity>,
        preferConsoles: List<String> = emptyList(),
        allowCombo: Boolean = false,
    ): GameEntity {
        return candidates.sortedWith(
            compareByDescending<GameEntity> { !it.contentUri.isNullOrBlank() }
                .thenByDescending {
                    val raw = it.title.lowercase(Locale.ROOT)
                    !listOf("(beta", "(proto", "(pirate", "(unl", "(aftermarket", "[b").any { noise -> noise in raw }
                }
                .thenByDescending {
                    if (allowCombo) true
                    else {
                        val raw = it.title.lowercase(Locale.ROOT)
                        " + " !in raw && " ~ " !in raw && "compilation" !in raw && "collection" !in raw
                    }
                }
                .thenByDescending {
                    val idx = preferConsoles.indexOf(it.systemId.lowercase(Locale.ROOT))
                    if (idx >= 0) preferConsoles.size - idx else 0
                }
                .thenByDescending { "usa" in it.region.lowercase(Locale.ROOT) }
                .thenByDescending { (it.lastPlayedAt ?: 0L) > 0L },
        ).first()
    }

    private val unofficialNoiseRegex = Regex(
        """\((?:pirate|aftermarket|proto(?:type)?|seganet|demo|sample)[^)]*\)|\[b[0-9a-z]*\]""",
        RegexOption.IGNORE_CASE,
    )

    fun isUnofficialSagaNoise(game: GameEntity): Boolean =
        unofficialNoiseRegex.containsMatchIn(game.title)

    private fun shouldSkipDemoDisc(game: GameEntity): Boolean =
        game.title.split(" + ").size >= 3

    fun buildSagas(games: List<GameEntity>): List<SagaSnapshot> {
        val base = games.filter { !shouldSkipDemoDisc(it) && !isUnofficialSagaNoise(it) }
        val result = mutableListOf<SagaSnapshot>()

        for (saga in CURATED_SAGAS) {
            val pool = base.filter { game ->
                val (nc, nr) = cleanMatchPair(game)
                matchesAny(saga.includePatterns, nc, nr) &&
                    !matchesAny(saga.excludePatterns, nc, nr)
            }

            val entries = mutableListOf<SagaEntryItem>()
            val usedIds = hashSetOf<String>()
            val usedNorms = hashSetOf<String>()
            val sortedTimeline = saga.timeline.sortedWith(compareBy<SagaMilestoneDef>({ it.year }, { it.title }))

            var order = 1
            for (ms in sortedTimeline) {
                val hits = pool.filter { game ->
                    if (game.id in usedIds) return@filter false
                    val sys = game.systemId.lowercase(Locale.ROOT)
                    if (sys in ms.excludeConsoles) return@filter false
                    val (nc, nr) = cleanMatchPair(game)
                    if (matchesAny(ms.excludeMatchPatterns, nc, nr)) return@filter false
                    var ok = matchesAny(ms.matchPatterns, nc, nr)
                    if (!ok && ms.snesAliasPatterns.isNotEmpty() && sys == "snes") {
                        ok = matchesAny(ms.snesAliasPatterns, nc, nr)
                    }
                    ok
                }

                if (hits.isNotEmpty()) {
                    val best = pickBestMatch(hits, ms.preferConsoles, ms.allowCombo)
                    for (h in hits) {
                        usedIds += h.id
                        val (nc, _) = cleanMatchPair(h)
                        if (nc.isNotBlank()) usedNorms += nc
                    }
                    val bestClean = cleanTitle(best.title)
                    val coverCands = buildList {
                        if (ms.libretroArt != null) {
                            add(libretroThumbnailUrl(ms.libretroArt.first, "Named_Boxarts", ms.libretroArt.second))
                        }
                        addAll(
                            LibretroThumbnailHelper.candidateUrls(
                                repo = best.thumbnailRepo,
                                systemId = best.systemId,
                                title = best.title,
                                folders = listOf("Named_Boxarts", "Named_Titles"),
                                maxUrls = 8,
                            ),
                        )
                    }.distinct()
                    val cover = coverCands.firstOrNull()

                    val resolvedConsole = best.systemId.lowercase(Locale.ROOT)
                    entries += SagaEntryItem(
                        order = order,
                        title = ms.title,
                        matchedTitle = if (norm(ms.title) != norm(bestClean)) bestClean else "",
                        year = ms.year,
                        era = canonicalEraFor(saga.id, ms.era, resolvedConsole, ms.year),
                        consoleKey = resolvedConsole,
                        isExtra = false,
                        variantCount = hits.size,
                        localOwned = !best.contentUri.isNullOrBlank(),
                        inCatalog = true,
                        gameId = best.id,
                        game = best,
                        coverUrl = cover,
                        minervaQuery = ms.minervaQuery,
                        coverCandidates = coverCands,
                    )
                } else {
                    val ghostCands = if (ms.libretroArt != null) {
                        listOf(libretroThumbnailUrl(ms.libretroArt.first, "Named_Boxarts", ms.libretroArt.second))
                    } else {
                        LibretroThumbnailHelper.candidateUrls(
                            repo = null,
                            systemId = ms.consoleKey,
                            title = ms.title,
                            folders = listOf("Named_Boxarts", "Named_Titles"),
                            maxUrls = 6,
                        )
                    }
                    entries += SagaEntryItem(
                        order = order,
                        title = ms.title,
                        matchedTitle = "",
                        year = ms.year,
                        era = canonicalEraFor(saga.id, ms.era, ms.consoleKey, ms.year),
                        consoleKey = ms.consoleKey,
                        isExtra = false,
                        variantCount = 0,
                        localOwned = false,
                        inCatalog = false,
                        gameId = null,
                        game = null,
                        coverUrl = ghostCands.firstOrNull(),
                        minervaQuery = ms.minervaQuery,
                        coverCandidates = ghostCands,
                    )
                }
                order++
            }

            // Remaining distinct extra / spin-off titles
            val extraGroups = linkedMapOf<String, MutableList<GameEntity>>()
            for (game in pool) {
                if (game.id in usedIds) continue
                val (nc, _) = cleanMatchPair(game)
                if (" + " in game.title && usedNorms.any { it.length >= 6 && it in nc }) {
                    usedIds += game.id
                    continue
                }
                if (nc.isNotBlank() && nc in usedNorms) {
                    usedIds += game.id
                    continue
                }
                extraGroups.getOrPut(nc.ifBlank { game.id }) { mutableListOf() }.add(game)
            }

            for (key in extraGroups.keys.sorted()) {
                val group = extraGroups[key] ?: continue
                val best = pickBestMatch(group)
                group.forEach { usedIds += it.id }
                val coverCands = LibretroThumbnailHelper.candidateUrls(
                    repo = best.thumbnailRepo,
                    systemId = best.systemId,
                    title = best.title,
                    folders = listOf("Named_Boxarts", "Named_Titles"),
                    maxUrls = 6,
                )
                entries += SagaEntryItem(
                    order = order,
                    title = cleanTitle(best.title),
                    matchedTitle = "",
                    year = best.year,
                    era = "Extras y Spin-offs",
                    consoleKey = best.systemId.lowercase(Locale.ROOT),
                    isExtra = true,
                    variantCount = group.size,
                    localOwned = !best.contentUri.isNullOrBlank(),
                    inCatalog = true,
                    gameId = best.id,
                    game = best,
                    coverUrl = coverCands.firstOrNull(),
                    minervaQuery = cleanTitle(best.title).substringBefore(":"),
                    coverCandidates = coverCands,
                )
                order++
            }

            val localOwnedCount = entries.count { it.localOwned }
            val catalogCount = entries.count { it.inCatalog }
            val consoles = entries.filter { it.inCatalog }.map { it.consoleKey }.distinct()
                .ifEmpty { entries.take(4).map { it.consoleKey }.distinct() }

            val primaryHeroBanner = libretroThumbnailUrl(saga.heroArt.repo, "Named_Titles", saga.heroArt.titleName)
            val primaryKeyArt = libretroThumbnailUrl(saga.heroArt.repo, "Named_Boxarts", saga.heroArt.boxartName)
            val entryFallbacks = entries.flatMap { it.coverCandidates }.distinct().take(6)

            result += SagaSnapshot(
                id = saga.id,
                name = saga.name,
                tagline = saga.tagline,
                history = saga.history,
                foundedYear = saga.foundedYear,
                creator = saga.creator,
                accentHex = saga.accentHex,
                localOwnedCount = localOwnedCount,
                catalogCount = catalogCount,
                totalCount = entries.size,
                consoles = consoles,
                heroBannerUrl = primaryHeroBanner,
                keyArtUrl = primaryKeyArt,
                entries = entries,
                heroBannerCandidates = (listOf(primaryHeroBanner, primaryKeyArt) + entryFallbacks).distinct(),
                keyArtCandidates = (listOf(primaryKeyArt, primaryHeroBanner) + entryFallbacks).distinct(),
            )
        }

        return result.sortedWith(
            compareByDescending<SagaSnapshot> { it.localOwnedCount > 0 }
                .thenByDescending { it.localOwnedCount }
                .thenByDescending { it.catalogCount }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )
    }

    fun findSagaForGame(game: GameEntity, sagas: List<SagaSnapshot>): SagaSummaryRef? {
        val hit = sagas.firstOrNull { saga ->
            saga.entries.any { it.gameId == game.id }
        } ?: return null
        return SagaSummaryRef(
            id = hit.id,
            name = hit.name,
            localOwnedCount = hit.localOwnedCount,
            totalCount = hit.totalCount,
            accentHex = hit.accentHex,
        )
    }

    internal fun canonicalEraFor(sagaId: String, rawEra: String, consoleKey: String, year: Int?): String {
        val sys = consoleKey.lowercase(Locale.ROOT)
        val raw = rawEra.trim()
        return when (sagaId) {
            "super-mario" -> when {
                sys == "nes" || raw == "Era 8-bit" -> "Era 8-bit · NES"
                sys == "snes" || raw in setOf("Era 16-bit", "Spin-off Arcade", "Compilación 16-bit") || (year != null && year <= 1996 && raw == "Sub-serie RPG") -> "Era 16-bit · SNES"
                sys == "n64" || raw == "Revolución 3D" -> "Era 64-bit · N64"
                else -> "Era GameCube, DS y Wii"
            }
            "the-legend-of-zelda" -> when {
                sys == "nes" -> "Era 8-bit · NES"
                sys == "snes" -> "Era 16-bit · SNES"
                sys in setOf("gb", "gbc", "gba") -> "Portátiles · GB y GBA"
                sys == "n64" -> "Era 64-bit · N64"
                else -> "Era GameCube y Wii"
            }
            "sonic" -> when {
                sys in setOf("genesis", "segacd", "sms", "gg") -> "Era 16-bit · Mega Drive y Sega CD"
                sys == "gba" -> "Era Portátil · GBA"
                else -> "Era 128-bit · 3D"
            }
            "mega-man" -> when {
                raw in setOf("Serie X") -> "Serie X · SNES y PS1"
                raw in setOf("Serie Zero") -> "Serie Zero · GBA"
                else -> "Serie Clásica · NES, 16-bit y PS1"
            }
            "castlevania" -> when {
                sys == "nes" -> "Clásico 8-bit · NES"
                sys in setOf("snes", "pce", "genesis") -> "Era 16-bit · Consolas"
                else -> "Era Metroidvania · PS1, GBA y DS"
            }
            "resident-evil" -> when {
                sys == "ps1" -> "Trilogía Clásica · PS1"
                else -> "Era 128-bit · Dreamcast, GC y PS2"
            }
            "final-fantasy" -> when {
                sys == "nes" -> "Era 8-bit · NES"
                sys == "snes" -> "Era 16-bit · SNES"
                sys == "ps1" || raw in setOf("Trilogía PS1", "Táctico") -> "Era PlayStation"
                else -> "Era PS2"
            }
            "metroid" -> when {
                sys in setOf("nes", "gb") -> "Era 8-bit · NES y Game Boy"
                sys in setOf("snes", "gba") -> "Era 16-bit y GBA"
                else -> "Trilogía Prime · GameCube y Wii"
            }
            "donkey-kong" -> when {
                sys in setOf("arcade", "nes", "snes") -> "Era Arcade, NES y Country (SNES)"
                else -> "Era 64-bit · N64"
            }
            "street-fighter" -> when {
                raw.contains("Alpha", ignoreCase = true) -> "Sub-serie Alpha · Arcade y Consolas"
                raw.contains("Crossover", ignoreCase = true) -> "Crossovers Vs. Capcom"
                else -> "Serie Principal · CPS-1, CPS-2 y CPS-3"
            }
            "mortal-kombat" -> when {
                sys in setOf("arcade", "ps1", "genesis", "snes") -> "Era Clásica 2D · Arcade y 16/32-bit"
                else -> "Era 3D y Aventura · N64 y PS2"
            }
            "metal-slug" -> when {
                sys == "gba" -> "Portátil · GBA"
                else -> "Era Arcade · Neo Geo MVS"
            }
            "kirby" -> when {
                sys in setOf("nes", "gb", "snes") -> "Era Clásica · GB, NES y SNES"
                else -> "Era 64-bit y GBA"
            }
            "pokemon" -> when {
                sys in setOf("gb", "gbc") -> "Gen I y II · Game Boy"
                sys == "n64" -> "Era N64 · Stadium y Snap"
                else -> "Gen III · Hoenn y GBA"
            }
            "streets-of-rage" -> "Trilogía Mega Drive"
            "phantasy-star" -> when {
                sys == "dc" || sys == "gc" -> "Era 128-bit · Online"
                else -> "Era Clásica · Master System y Mega Drive"
            }
            "chrono" -> "Era Clásica · SNES y PS1"
            "silent-hill" -> "Era Team Silent · PS1 y PS2"
            "metal-gear" -> when {
                sys in setOf("nes", "msx") -> "Era Clásica · 8-bit y MSX2"
                else -> "Era Solid · PS1 y PS2"
            }
            "007-bond" -> when {
                sys in setOf("genesis", "n64") -> "Era 16-bit y N64"
                else -> "Era 128-bit · GameCube"
            }
            "contra" -> when {
                sys == "nes" -> "Era 8-bit · NES"
                sys in setOf("snes", "genesis") -> "Era 16-bit · SNES y Mega Drive"
                else -> "Era PS2"
            }
            "shinobi" -> "Era Clásica · Arcade y Mega Drive"
            "golden-axe" -> "Era Clásica · Arcade y Mega Drive"
            "double-dragon" -> when {
                sys == "nes" || (year != null && year <= 1990) -> "Era Arcade y 8-bit"
                else -> "Era 16-bit · SNES y Mega Drive"
            }
            "breath-of-fire" -> when {
                sys in setOf("snes", "gba") -> "Era 16-bit · SNES"
                else -> "Era PlayStation"
            }
            "dragon-quest" -> when {
                sys in setOf("nes", "snes") -> "Era Clásica · NES y SNES"
                else -> "Era PS2"
            }
            "ninja-gaiden" -> "Trilogía Clásica · NES y SNES"
            "king-of-fighters" -> when {
                year != null && year <= 1998 -> "Saga Orochi ('94-'98)"
                else -> "Crónicas NESTS ('99-2002)"
            }
            "dragon-ball" -> when {
                sys == "gba" -> "Era Portátil · GBA"
                else -> "Era 16-bit y 32-bit · Lucha"
            }
            "shining" -> when {
                sys == "gba" -> "Remake GBA"
                else -> "Era Clásica · Mega Drive y Sega CD"
            }
            "tmnt" -> "Era Clásica · 8-bit y 16-bit"
            "batman" -> when {
                sys in setOf("nes", "genesis") && (year == null || year <= 1991) -> "Era Clásica · Sunsoft"
                else -> "Era 16-bit · SNES"
            }
            "spider-man" -> when {
                sys == "ps1" -> "Era 3D · PlayStation"
                else -> "Era 16-bit · Clásicos 90s"
            }
            "x-men" -> when {
                sys in setOf("arcade", "ps1") -> "Era Arcade y PlayStation"
                else -> "Era 16-bit · Mega Drive y SNES"
            }
            "star-wars" -> when {
                sys == "gba" -> "Era Portátil · GBA"
                else -> "Trilogía Clásica · SNES"
            }
            "disney-illusion" -> when {
                raw == "Illusion" -> "Serie Illusion · Mega Drive"
                else -> "Clásicos Disney · 16-bit"
            }
            "fatal-fury" -> "Era SNK · Arcade y Consolas"
            "thunder-force" -> when {
                sys == "ps1" -> "Era 32-bit · PlayStation"
                else -> "Trilogía Clásica · Mega Drive"
            }
            "wonder-boy" -> "Era Clásica · Master System y Mega Drive"
            "ecco" -> when {
                sys == "dc" -> "Era 128-bit · Dreamcast"
                else -> "Era 16-bit · Mega Drive"
            }
            "earthworm-jim" -> "Era 16-bit y 64-bit"
            "strike-series" -> when {
                sys == "ps1" -> "Era 32-bit · PlayStation"
                else -> "Trilogía 16-bit · Mega Drive"
            }
            "sega-super-scaler" -> "Clásicos Super Scaler AM2 · Mega Drive"
            "road-rash" -> when {
                sys == "n64" -> "Era 64-bit · N64"
                else -> "Trilogía Clásica · Mega Drive"
            }
            "bomberman" -> when {
                sys == "n64" -> "Era 64-bit · N64"
                else -> "Era 16-bit · SNES y Mega Drive"
            }
            "seiken-densetsu" -> when {
                sys == "snes" -> "Era 16-bit · SNES"
                else -> "Era PlayStation y GBA"
            }
            "lufia" -> when {
                sys in setOf("gbc", "gba") -> "Era Portátil · GBC y GBA"
                else -> "Era 16-bit · SNES"
            }
            else -> when {
                sys in setOf("nes", "sms", "msx") -> "Era 8-bit"
                sys in setOf("snes", "genesis", "segacd", "pce") -> "Era 16-bit"
                sys in setOf("ps1", "saturn", "n64") -> "Era 32 y 64-bit"
                sys in setOf("gb", "gbc", "gba", "nds", "psp") -> "Era Portátil"
                sys in setOf("dc", "ps2", "gc", "wii") -> "Era 128-bit"
                else -> raw.ifBlank { "Serie Principal" }
            }
        }
    }
}

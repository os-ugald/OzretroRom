package cl.gakuto.kizuna.sagas

import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.LibretroThumbnailHelper
import java.util.Locale

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
    private val parenTrailer = Regex("""\s*(?:\([^)]*\)|\[[^\]]*\])\s*""")
    private val nonAlnum = Regex("""[^a-z0-9]+""")

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

    val CURATED_SAGAS: List<SagaDefinition> = listOf(
        SagaDefinition(
            id = "super-mario",
            name = "Super Mario",
            tagline = "Plataformas doradas, mundos RPG y el salto al 3D del Reino Champiñón.",
            foundedYear = 1985,
            creator = "Nintendo / Shigeru Miyamoto",
            accentHex = "#F87171",
            history = "Nacida en 1985 en NES tras el debut de Jumpman en Donkey Kong, Super Mario definió para siempre el diseño de plataformas laterales con desplazamiento suave y secretos ocultos. Desde el salto maestro de Super Mario Bros. 3 y Super Mario World en SNES hasta la revolución tridimensional de Super Mario 64, la franquicia ha reinventado el género en cada generación.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Super Mario World (USA)", "Super Mario World (USA)"),
            includePatterns = listOf(rx("""\b(super mario|mario rpg|paper mario|yoshi s island|mario kart|mario galaxy)\b""")),
            excludePatterns = listOf(rx("""\b(mario andretti|mario lemieux|mario is missing|mario s time machine)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Super Mario Bros.", 1985, "nes", "Era 8-bit", listOf(rx("""\bsuper mario bros\b""")), listOf(rx("""\b(new super|all stars|deluxe|lost levels|wii|ds|2|3)\b""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Super Mario Bros. 2", 1988, "nes", "Era 8-bit", listOf(rx("""\bsuper mario bros 2\b""")), preferConsoles = listOf("nes", "gba")),
                SagaMilestoneDef("Super Mario Bros. 3", 1988, "nes", "Era 8-bit", listOf(rx("""\bsuper mario bros 3\b"""), rx("""\bsuper mario advance 4\b""")), preferConsoles = listOf("nes", "gba")),
                SagaMilestoneDef("Super Mario World", 1990, "snes", "Era 16-bit", listOf(rx("""\bsuper mario world\b"""), rx("""\bsuper mario advance 2\b""")), listOf(rx("""\b(yoshi|2)\b""")), preferConsoles = listOf("snes", "gba"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Super Mario World (USA)"),
                SagaMilestoneDef("Super Mario Kart", 1992, "snes", "Spin-off Arcade", listOf(rx("""\bsuper mario kart\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Super Mario All-Stars", 1993, "snes", "Compilación 16-bit", listOf(rx("""\bsuper mario all stars\b""")), preferConsoles = listOf("snes"), allowCombo = true),
                SagaMilestoneDef("Super Mario World 2: Yoshi's Island", 1995, "snes", "Era 16-bit", listOf(rx("""\byoshi s? ?island\b""")), preferConsoles = listOf("snes", "gba")),
                SagaMilestoneDef("Super Mario RPG: Legend of the Seven Stars", 1996, "snes", "Sub-serie RPG", listOf(rx("""\bsuper mario rpg\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Super Mario 64", 1996, "n64", "Revolución 3D", listOf(rx("""\bsuper mario 64\b""")), preferConsoles = listOf("n64", "nds")),
                SagaMilestoneDef("Paper Mario", 2000, "n64", "Sub-serie RPG", listOf(rx("""^paper mario\b""")), listOf(rx("""\b(thousand|ttyd|super|color|origami|sticker)\b""")), preferConsoles = listOf("n64")),
                SagaMilestoneDef("Super Mario Sunshine", 2002, "gc", "Era 128-bit", listOf(rx("""\bsuper mario sunshine\b""")), preferConsoles = listOf("gc")),
                SagaMilestoneDef("New Super Mario Bros.", 2006, "nds", "Renacimiento 2D", listOf(rx("""\bnew super mario bros\b""")), listOf(rx("""\b(wii|2|u)\b""")), preferConsoles = listOf("nds")),
                SagaMilestoneDef("Super Mario Galaxy", 2007, "wii", "Era Wii", listOf(rx("""\bsuper mario galaxy\b""")), listOf(rx("""\b2\b""")), preferConsoles = listOf("wii")),
                SagaMilestoneDef("New Super Mario Bros. Wii", 2009, "wii", "Era Wii", listOf(rx("""\bnew super mario bros wii\b""")), preferConsoles = listOf("wii")),
            ),
        ),
        SagaDefinition(
            id = "the-legend-of-zelda",
            name = "The Legend of Zelda",
            tagline = "Calabozos, la Trifuerza y aventuras eternas en Hyrule y más allá.",
            foundedYear = 1986,
            creator = "Nintendo / Shigeru Miyamoto & Eiji Aonuma",
            accentHex = "#39FF88",
            history = "Creada en 1986 para Famicom y NES, The Legend of Zelda sentó las bases de la aventura no lineal con batería de guardado en cartucho. A Link to the Past (1991) consolidó la estructura de mundos duales, mientras Ocarina of Time (1998) y Majora's Mask (2000) inventaron la cámara Z-Targeting y la aventura en 3D moderna.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Legend of Zelda, The - A Link to the Past (USA)", "Legend of Zelda, The - A Link to the Past (USA)"),
            includePatterns = listOf(rx("""\b(zelda|link s awakening|ocarina of time|majora s mask|wind waker|minish cap|twilight princess)\b""")),
            excludePatterns = listOf(rx("""\b(cd i|wand of gamelon|faces of evil|zelda s adventure)\b""")),
            timeline = listOf(
                SagaMilestoneDef("The Legend of Zelda", 1986, "nes", "Era 8-bit", listOf(rx("""^(the )?legend of zelda( nes classics)?$"""), rx("""^legend of zelda the$""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Zelda II: The Adventure of Link", 1987, "nes", "Era 8-bit", listOf(rx("""\bzelda ii\b"""), rx("""\badventure of link\b""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("The Legend of Zelda: A Link to the Past", 1991, "snes", "Era 16-bit", listOf(rx("""\blink to the past\b""")), preferConsoles = listOf("snes", "gba"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Legend of Zelda, The - A Link to the Past (USA)"),
                SagaMilestoneDef("The Legend of Zelda: Link's Awakening", 1993, "gbc", "Portátil Clásico", listOf(rx("""\blink s? awakening\b""")), preferConsoles = listOf("gbc", "gb")),
                SagaMilestoneDef("The Legend of Zelda: Ocarina of Time", 1998, "n64", "Era 64-bit", listOf(rx("""\bocarina of time\b""")), preferConsoles = listOf("n64", "gc", "3ds")),
                SagaMilestoneDef("The Legend of Zelda: Majora's Mask", 2000, "n64", "Era 64-bit", listOf(rx("""\bmajora s? mask\b""")), preferConsoles = listOf("n64", "3ds")),
                SagaMilestoneDef("The Legend of Zelda: Oracle of Seasons / Ages", 2001, "gbc", "Portátil GBC", listOf(rx("""\boracle of (seasons|ages)\b""")), preferConsoles = listOf("gbc")),
                SagaMilestoneDef("The Legend of Zelda: The Wind Waker", 2002, "gc", "Era GameCube", listOf(rx("""\bwind waker\b""")), preferConsoles = listOf("gc")),
                SagaMilestoneDef("The Legend of Zelda: The Minish Cap", 2004, "gba", "Portátil GBA", listOf(rx("""\bminish cap\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("The Legend of Zelda: Twilight Princess", 2006, "gc", "GameCube / Wii", listOf(rx("""\btwilight princess\b""")), preferConsoles = listOf("gc", "wii")),
            ),
        ),
        SagaDefinition(
            id = "sonic",
            name = "Sonic the Hedgehog",
            tagline = "Velocidad supersónica, física de momentum y loops icónicos en Sega.",
            foundedYear = 1991,
            creator = "Sonic Team / Yuji Naka & Naoto Ohshima",
            accentHex = "#60A5FA",
            history = "Debutando en 1991 como el arma secreta de Sega Mega Drive para competir cara a cara con Nintendo, Sonic conquistó al mundo con físicas de pendiente y música inolvidable. Su época dorada en 16 bits culminó con la tecnología Lock-On de Sonic 3 & Knuckles y la ambición animada de Sonic CD, dando paso luego a Sonic Adventure y Sonic Advance.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Sonic The Hedgehog (USA, Europe)", "Sonic The Hedgehog (USA, Europe)"),
            includePatterns = listOf(rx("""\bsonic\b""")),
            excludePatterns = listOf(rx("""\b(aerobiz|supersonic|dragon ball|sonic blast man|sonic wings|aero fighters)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Sonic the Hedgehog", 1991, "genesis", "Mega Drive 16-bit", listOf(rx("""^sonic the hedgehog$""")), preferConsoles = listOf("genesis", "sms"), libretroArt = "Sega - Mega Drive - Genesis" to "Sonic The Hedgehog (USA, Europe)"),
                SagaMilestoneDef("Sonic the Hedgehog 2", 1992, "genesis", "Mega Drive 16-bit", listOf(rx("""^sonic the hedgehog 2$"""), rx("""^sonic 2$""")), preferConsoles = listOf("genesis", "sms"), libretroArt = "Sega - Mega Drive - Genesis" to "Sonic The Hedgehog 2 (World)"),
                SagaMilestoneDef("Sonic CD", 1993, "segacd", "Sega CD", listOf(rx("""\bsonic cd\b""")), preferConsoles = listOf("segacd")),
                SagaMilestoneDef("Sonic Spinball", 1993, "genesis", "Spin-off Pinball", listOf(rx("""\bsonic( the hedgehog)? spinball\b""")), preferConsoles = listOf("genesis", "gg")),
                SagaMilestoneDef("Sonic the Hedgehog 3", 1994, "genesis", "Mega Drive 16-bit", listOf(rx("""\bsonic( the hedgehog)? 3\b""")), listOf(rx("""\b3d|advance\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Sonic & Knuckles", 1994, "genesis", "Lock-On", listOf(rx("""^sonic (and )?knuckles$""")), listOf(rx("""\b(hedgehog|2|3)\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Sonic 3D Blast", 1996, "genesis", "Isométrico", listOf(rx("""\bsonic 3d( blast| flickies)?\b""")), preferConsoles = listOf("genesis", "saturn")),
                SagaMilestoneDef("Sonic Adventure", 1998, "dc", "Dreamcast 3D", listOf(rx("""\bsonic adventure\b""")), listOf(rx("""\b2\b""")), preferConsoles = listOf("dc", "gc")),
                SagaMilestoneDef("Sonic Adventure 2", 2001, "dc", "Dreamcast / GC", listOf(rx("""\bsonic adventure 2\b""")), preferConsoles = listOf("dc", "gc")),
                SagaMilestoneDef("Sonic Advance", 2001, "gba", "Portátil GBA", listOf(rx("""\bsonic advance\b""")), listOf(rx("""\b(2|3)\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Sonic Advance 2", 2002, "gba", "Portátil GBA", listOf(rx("""\bsonic advance 2\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Sonic Battle", 2003, "gba", "Portátil GBA", listOf(rx("""\bsonic battle\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Sonic Heroes", 2003, "gc", "Era 128-bit", listOf(rx("""\bsonic heroes\b""")), preferConsoles = listOf("gc", "ps2")),
                SagaMilestoneDef("Sonic Advance 3", 2004, "gba", "Portátil GBA", listOf(rx("""\bsonic advance 3\b""")), preferConsoles = listOf("gba")),
            ),
        ),
        SagaDefinition(
            id = "mega-man",
            name = "Mega Man / X / Zero",
            tagline = "Jefes robóticos, armas copiables y acción de precisión desde 1987.",
            foundedYear = 1987,
            creator = "Capcom / Akira Kitamura & Keiji Inafune",
            accentHex = "#38BDF8",
            history = "Mega Man (Rockman en Japón) debutó en NES en 1987 imponiendo un diseño revolucionario: elegir el orden de los Robot Masters para robar sus armas y descubrir debilidades elementales. En 1993 nació Mega Man X en SNES con dash en paredes y armaduras ocultas, seguido en 2002 por la exigente subsaga Mega Man Zero en GBA.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Mega Man X (USA)", "Mega Man X (USA)"),
            includePatterns = listOf(rx("""\b(mega ?man|rockman)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Mega Man", 1987, "nes", "Serie Clásica", listOf(rx("""^(mega ?man|rockman)( 1| i)?$""")), listOf(rx("""\b(x|zero|ii|iii|iv|v|vi|vii|2|3|4|5|6|7|8|wily|soccer|battle)\b""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Mega Man 2", 1988, "nes", "Serie Clásica", listOf(rx("""^(mega ?man|rockman) (2|ii)$""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Mega Man 3", 1990, "nes", "Serie Clásica", listOf(rx("""^(mega ?man|rockman) (3|iii)$""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Mega Man 4", 1991, "nes", "Serie Clásica", listOf(rx("""^(mega ?man|rockman) (4|iv)$""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Mega Man 5", 1992, "nes", "Serie Clásica", listOf(rx("""^(mega ?man|rockman) (5|v)$""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Mega Man 6", 1993, "nes", "Serie Clásica", listOf(rx("""^(mega ?man|rockman) (6|vi)$""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Mega Man X", 1993, "snes", "Serie X", listOf(rx("""^(mega ?man|rockman) x( 1)?$""")), listOf(rx("""\b(2|3|4|5|6|7|8|command|maverick)\b""")), preferConsoles = listOf("snes", "psp"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Mega Man X (USA)"),
                SagaMilestoneDef("Mega Man: The Wily Wars", 1994, "genesis", "Remake 16-bit", listOf(rx("""\b(wily wars|mega world)\b""")), preferConsoles = listOf("genesis"), allowCombo = true),
                SagaMilestoneDef("Mega Man X2", 1994, "snes", "Serie X", listOf(rx("""\b(mega ?man|rockman) x ?2\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Mega Man 7", 1995, "snes", "Serie Clásica", listOf(rx("""^(mega ?man|rockman) (7|vii)$""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Mega Man X3", 1995, "snes", "Serie X", listOf(rx("""\b(mega ?man|rockman) x ?3\b""")), preferConsoles = listOf("snes", "ps1")),
                SagaMilestoneDef("Mega Man 8", 1996, "ps1", "Serie Clásica", listOf(rx("""^(mega ?man|rockman) (8|viii)\b""")), preferConsoles = listOf("ps1", "saturn")),
                SagaMilestoneDef("Mega Man X4", 1997, "ps1", "Serie X", listOf(rx("""\b(mega ?man|rockman) x ?4\b""")), preferConsoles = listOf("ps1", "saturn")),
                SagaMilestoneDef("Mega Man X5", 2000, "ps1", "Serie X", listOf(rx("""\b(mega ?man|rockman) x ?5\b""")), preferConsoles = listOf("ps1")),
                SagaMilestoneDef("Mega Man X6", 2001, "ps1", "Serie X", listOf(rx("""\b(mega ?man|rockman) x ?6\b""")), preferConsoles = listOf("ps1")),
                SagaMilestoneDef("Mega Man Zero", 2002, "gba", "Serie Zero", listOf(rx("""^(mega ?man|rockman) zero( 1)?$""")), listOf(rx("""\b(2|3|4|collection|zx)\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Mega Man Zero 2", 2003, "gba", "Serie Zero", listOf(rx("""\b(mega ?man|rockman) zero 2\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Mega Man Zero 3", 2004, "gba", "Serie Zero", listOf(rx("""\b(mega ?man|rockman) zero 3\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Mega Man Zero 4", 2005, "gba", "Serie Zero", listOf(rx("""\b(mega ?man|rockman) zero 4\b""")), preferConsoles = listOf("gba")),
            ),
        ),
        SagaDefinition(
            id = "castlevania",
            name = "Castlevania",
            tagline = "Cazadores de vampiros del clan Belmont y exploraciones góticas en Castlevania.",
            foundedYear = 1986,
            creator = "Konami / Hitoshi Akamatsu & Koji Igarashi",
            accentHex = "#C4B5FD",
            history = "Akumajō Dracula nació en 1986 como una exigente cruzada gótica contra el Conde Drácula. Tras obras lineales memorables como Castlevania III, Super Castlevania IV y Rondo of Blood, Koji Igarashi reinventó la saga en 1997 con Symphony of the Night en PS1, dando nombre al subgénero Metroidvania continuado en GBA y Nintendo DS.",
            heroArt = SagaHeroArt("Sony - PlayStation", "Castlevania - Symphony of the Night (USA)", "Castlevania - Symphony of the Night (USA)"),
            includePatterns = listOf(rx("""\b(castlevania|akumajou dracula|akumajo dracula|dracula x)\b""")),
            excludePatterns = listOf(rx("""\b(bram stoker|kid dracula|hotel transylvania)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Castlevania", 1986, "nes", "Clásico Lineal", listOf(rx("""^castlevania( chronicle)?$"""), rx("""^akumajou? dracula nes$""")), listOf(rx("""\b(ii|iii|iv|2|3|4|64|chronicles|bloodlines|dracula x|symphony|circle|harmony|aria|dawn|portrait|order|new generation)\b""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Castlevania II: Simon's Quest", 1987, "nes", "Clásico Lineal", listOf(rx("""\bsimon s? quest\b"""), rx("""\bcastlevania ii\b""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Castlevania III: Dracula's Curse", 1989, "nes", "Clásico Lineal", listOf(rx("""\bdracula s? curse\b"""), rx("""\bcastlevania iii\b""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Super Castlevania IV", 1991, "snes", "Era 16-bit", listOf(rx("""\bsuper castlevania iv\b"""), rx("""\bcastlevania iv\b"""), rx("""^akumajou? dracula$""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Castlevania: Rondo of Blood", 1993, "pce", "PC Engine CD", listOf(rx("""\brondo of blood\b"""), rx("""\bchi no rondo\b""")), preferConsoles = listOf("pce", "psp")),
                SagaMilestoneDef("Castlevania: Bloodlines", 1994, "genesis", "Mega Drive 16-bit", listOf(rx("""\bbloodlines\b"""), rx("""\bnew generation\b"""), rx("""\bvampire killer\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Castlevania: Dracula X", 1995, "snes", "Era 16-bit", listOf(rx("""\bdracula x\b"""), rx("""\bvampire s? kiss\b""")), listOf(rx("""\bsymphony\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Castlevania: Symphony of the Night", 1997, "ps1", "Metroidvania", listOf(rx("""\bsymphony of the night\b"""), rx("""\bnocturne in the moonlight\b""")), preferConsoles = listOf("ps1", "saturn"), libretroArt = "Sony - PlayStation" to "Castlevania - Symphony of the Night (USA)"),
                SagaMilestoneDef("Castlevania: Circle of the Moon", 2001, "gba", "Metroidvania GBA", listOf(rx("""\bcircle of the moon\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Castlevania: Harmony of Dissonance", 2002, "gba", "Metroidvania GBA", listOf(rx("""\bharmony of dissonance\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Castlevania: Aria of Sorrow", 2003, "gba", "Metroidvania GBA", listOf(rx("""\baria of sorrow\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Castlevania: Dawn of Sorrow", 2005, "nds", "Metroidvania DS", listOf(rx("""\bdawn of sorrow\b""")), preferConsoles = listOf("nds")),
                SagaMilestoneDef("Castlevania: Portrait of Ruin", 2006, "nds", "Metroidvania DS", listOf(rx("""\bportrait of ruin\b""")), preferConsoles = listOf("nds")),
                SagaMilestoneDef("Castlevania: Order of Ecclesia", 2008, "nds", "Metroidvania DS", listOf(rx("""\border of ecclesia\b""")), preferConsoles = listOf("nds")),
            ),
        ),
        SagaDefinition(
            id = "resident-evil",
            name = "Resident Evil",
            tagline = "Survival horror biológico desde la mansión Spencer hasta Raccoon City.",
            foundedYear = 1996,
            creator = "Capcom / Shinji Mikami & Hideki Kamiya",
            accentHex = "#F87171",
            history = "Lanzado como Biohazard en Japón en 1996, Resident Evil popularizó mundialmente el término 'Survival Horror' con cámaras fijas cinematográficas y gestión estricta de munición. Tras la trilogía de Raccoon City y Code: Veronica, Resident Evil 4 (2005) transformó para siempre la acción en tercera persona.",
            heroArt = SagaHeroArt("Sony - PlayStation", "Resident Evil 2 (USA) (Disc 1)", "Resident Evil 2 (USA) (Disc 1)"),
            includePatterns = listOf(rx("""\b(resident evil|biohazard)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Resident Evil", 1996, "ps1", "Trilogía PS1", listOf(rx("""^(resident evil|biohazard)( director s? cut)?( dual shock.*)?$""")), listOf(rx("""\b(2|3|4|5|code|veronica|zero|survivor|outbreak)\b""")), preferConsoles = listOf("ps1", "gc")),
                SagaMilestoneDef("Resident Evil 2", 1998, "ps1", "Trilogía PS1", listOf(rx("""\b(resident evil|biohazard) 2\b""")), preferConsoles = listOf("ps1", "dc", "gc", "n64"), libretroArt = "Sony - PlayStation" to "Resident Evil 2 (USA) (Disc 1)"),
                SagaMilestoneDef("Resident Evil 3: Nemesis", 1999, "ps1", "Trilogía PS1", listOf(rx("""\b(resident evil|biohazard) 3\b"""), rx("""\bnemesis\b""")), preferConsoles = listOf("ps1", "dc", "gc")),
                SagaMilestoneDef("Resident Evil Code: Veronica", 2000, "dc", "Dreamcast / PS2", listOf(rx("""\bcode ?veronica\b""")), preferConsoles = listOf("dc", "ps2", "gc")),
                SagaMilestoneDef("Resident Evil Zero", 2002, "gc", "Era GameCube", listOf(rx("""\b(resident evil|biohazard) (0|zero)\b""")), preferConsoles = listOf("gc", "wii")),
                SagaMilestoneDef("Resident Evil (Remake)", 2002, "gc", "Era GameCube", listOf(rx("""\b(resident evil|biohazard) (remake|rebirth)\b""")), preferConsoles = listOf("gc")),
                SagaMilestoneDef("Resident Evil Outbreak", 2003, "ps2", "Era PS2", listOf(rx("""\boutbreak\b""")), preferConsoles = listOf("ps2")),
                SagaMilestoneDef("Resident Evil 4", 2005, "gc", "Acción Moderna", listOf(rx("""\b(resident evil|biohazard) 4\b""")), preferConsoles = listOf("gc", "ps2", "wii")),
            ),
        ),
        SagaDefinition(
            id = "final-fantasy",
            name = "Final Fantasy",
            tagline = "Cristales, chocobos, invocaciones y las grandes epopeyas JRPG de Square.",
            foundedYear = 1987,
            creator = "Square / Hironobu Sakaguchi & Nobuo Uematsu",
            accentHex = "#67E8F9",
            history = "Inaugurada por Hironobu Sakaguchi junto a Nobuo Uematsu y Yoshitaka Amano en 1987, Final Fantasy es la saga insignia del JRPG. Desde los picos dramáticos de 16 bits en FF IV y FF VI hasta el fenómeno cinematográfico de Final Fantasy VII, VIII, IX y X.",
            heroArt = SagaHeroArt("Sony - PlayStation", "Final Fantasy VII (USA) (Disc 1)", "Final Fantasy VII (USA) (Disc 1)"),
            includePatterns = listOf(rx("""\bfinal fantasy\b""")),
            timeline = listOf(
                SagaMilestoneDef("Final Fantasy", 1987, "nes", "Era 8-bit", listOf(rx("""^final fantasy( origins| anniversary| i)?$""")), listOf(rx("""\b(ii|iii|iv|v|vi|vii|viii|ix|x|tactics|mystic|crystal|adventure|2|3|4|5|6|7|8|9|10)\b""")), preferConsoles = listOf("nes", "psp", "ps1")),
                SagaMilestoneDef("Final Fantasy II (JP)", 1988, "nes", "Era 8-bit", listOf(rx("""^final fantasy (ii|2)$""")), excludeConsoles = setOf("snes"), preferConsoles = listOf("nes", "psp")),
                SagaMilestoneDef("Final Fantasy III (JP)", 1990, "nes", "Era 8-bit", listOf(rx("""^final fantasy (iii|3)$""")), excludeConsoles = setOf("snes"), preferConsoles = listOf("nes", "nds")),
                SagaMilestoneDef("Final Fantasy IV (US: FF II)", 1991, "snes", "Era 16-bit", listOf(rx("""\bfinal fantasy (iv|4)\b""")), snesAliasPatterns = listOf(rx("""^final fantasy (ii|2)$""")), preferConsoles = listOf("snes", "gba", "nds", "psp")),
                SagaMilestoneDef("Final Fantasy V", 1992, "snes", "Era 16-bit", listOf(rx("""\bfinal fantasy (v|5)\b""")), preferConsoles = listOf("snes", "gba", "ps1")),
                SagaMilestoneDef("Final Fantasy VI (US: FF III)", 1994, "snes", "Era 16-bit", listOf(rx("""\bfinal fantasy (vi|6)\b""")), snesAliasPatterns = listOf(rx("""^final fantasy (iii|3)$""")), preferConsoles = listOf("snes", "gba", "ps1")),
                SagaMilestoneDef("Final Fantasy VII", 1997, "ps1", "Trilogía PS1", listOf(rx("""\bfinal fantasy (vii|7)\b""")), preferConsoles = listOf("ps1"), libretroArt = "Sony - PlayStation" to "Final Fantasy VII (USA) (Disc 1)"),
                SagaMilestoneDef("Final Fantasy Tactics", 1997, "ps1", "Táctico", listOf(rx("""\bfinal fantasy tactics\b""")), preferConsoles = listOf("ps1", "psp", "gba")),
                SagaMilestoneDef("Final Fantasy VIII", 1999, "ps1", "Trilogía PS1", listOf(rx("""\bfinal fantasy (viii|8)\b""")), preferConsoles = listOf("ps1")),
                SagaMilestoneDef("Final Fantasy IX", 2000, "ps1", "Trilogía PS1", listOf(rx("""\bfinal fantasy (ix|9)\b""")), preferConsoles = listOf("ps1")),
                SagaMilestoneDef("Final Fantasy X", 2001, "ps2", "Era PS2", listOf(rx("""\bfinal fantasy (x|10)\b""")), listOf(rx("""\bx ?2\b""")), preferConsoles = listOf("ps2")),
                SagaMilestoneDef("Final Fantasy XII", 2006, "ps2", "Era PS2", listOf(rx("""\bfinal fantasy (xii|12)\b""")), preferConsoles = listOf("ps2")),
            ),
        ),
        SagaDefinition(
            id = "metroid",
            name = "Metroid",
            tagline = "Exploración atmosférica y cazarrecompensas junto a Samus Aran.",
            foundedYear = 1986,
            creator = "Nintendo R&D1 / Yoshio Sakamoto & Gunpei Yokoi",
            accentHex = "#F97316",
            history = "Metroid sorprendió en 1986 en NES con su mundo laberíntico interconectado y atmósfera solitaria junto a Samus Aran. Super Metroid (1994) fijó el estándar dorado de la exploración no lineal antes del doble triunfo de Metroid Fusion y Metroid Prime.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Super Metroid (Japan, USA) (En,Ja)", "Super Metroid (Japan, USA) (En,Ja)"),
            includePatterns = listOf(rx("""\bmetroid\b""")),
            timeline = listOf(
                SagaMilestoneDef("Metroid", 1986, "nes", "Era 8-bit", listOf(rx("""^metroid$""")), excludeConsoles = setOf("snes"), preferConsoles = listOf("nes", "gba")),
                SagaMilestoneDef("Metroid II: Return of Samus", 1991, "gb", "Game Boy", listOf(rx("""\bmetroid ii\b"""), rx("""\breturn of samus\b""")), preferConsoles = listOf("gb", "3ds")),
                SagaMilestoneDef("Super Metroid", 1994, "snes", "Era 16-bit", listOf(rx("""\bsuper metroid\b""")), preferConsoles = listOf("snes"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Super Metroid (Japan, USA) (En,Ja)"),
                SagaMilestoneDef("Metroid Fusion", 2002, "gba", "Portátil GBA", listOf(rx("""\bmetroid fusion\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Metroid Prime", 2002, "gc", "Trilogía Prime", listOf(rx("""\bmetroid prime\b""")), listOf(rx("""\b(2|echoes|3|corruption|hunters|pinball)\b""")), preferConsoles = listOf("gc", "wii")),
                SagaMilestoneDef("Metroid: Zero Mission", 2004, "gba", "Remake GBA", listOf(rx("""\bzero mission\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Metroid Prime 2: Echoes", 2004, "gc", "Trilogía Prime", listOf(rx("""\bechoes\b"""), rx("""\bmetroid prime 2\b""")), preferConsoles = listOf("gc", "wii")),
            ),
        ),
        SagaDefinition(
            id = "donkey-kong",
            name = "Donkey Kong Country",
            tagline = "Barriles cañón, vagonetas y plataformas selváticas de Rare y Nintendo.",
            foundedYear = 1981,
            creator = "Nintendo & Rare / Gregg Mayles & Tim Stamper",
            accentHex = "#FBBF24",
            history = "Tras nacer en arcades en 1981, el gorila más famoso del videojuego renació en 1994 cuando Rare deslumbró en SNES con gráficos ACM pre-renderizados y la inolvidable música de David Wise en Donkey Kong Country 1, 2 y 3.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Donkey Kong Country (USA)", "Donkey Kong Country (USA)"),
            includePatterns = listOf(rx("""\b(donkey kong|diddy kong)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Donkey Kong (Arcade / NES)", 1981, "nes", "Origen Arcade", listOf(rx("""^donkey kong$""")), listOf(rx("""\b(country|64|land|jr|returns|3)\b""")), preferConsoles = listOf("arcade", "nes")),
                SagaMilestoneDef("Donkey Kong Country", 1994, "snes", "Trilogía Rare 16-bit", listOf(rx("""^donkey kong country( 1)?$""")), listOf(rx("""\b(2|3|returns|tropical)\b""")), preferConsoles = listOf("snes", "gba"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Donkey Kong Country (USA)"),
                SagaMilestoneDef("Donkey Kong Country 2: Diddy's Kong Quest", 1995, "snes", "Trilogía Rare 16-bit", listOf(rx("""\bdonkey kong country 2\b"""), rx("""\bdiddy s? kong quest\b""")), preferConsoles = listOf("snes", "gba")),
                SagaMilestoneDef("Donkey Kong Country 3: Dixie Kong's Double Trouble!", 1996, "snes", "Trilogía Rare 16-bit", listOf(rx("""\bdonkey kong country 3\b"""), rx("""\bdixie kong\b""")), preferConsoles = listOf("snes", "gba")),
                SagaMilestoneDef("Diddy Kong Racing", 1997, "n64", "Era 64-bit", listOf(rx("""\bdiddy kong racing\b""")), preferConsoles = listOf("n64", "nds")),
                SagaMilestoneDef("Donkey Kong 64", 1999, "n64", "Era 64-bit", listOf(rx("""\bdonkey kong 64\b""")), preferConsoles = listOf("n64")),
            ),
        ),
        SagaDefinition(
            id = "street-fighter",
            name = "Street Fighter & Capcom Vs.",
            tagline = "El padre moderno de la lucha arcade 2D y los crossovers legendarios de Capcom.",
            foundedYear = 1987,
            creator = "Capcom / Takashi Nishiyama & Akira Yasuda",
            accentHex = "#FBBF24",
            history = "En 1991 Street Fighter II desató una fiebre mundial al establecer los combos y un elenco icónico. Capcom perfeccionó la fórmula con las subsagas Alpha, 3rd Strike y la explosiva línea tag-team frente a X-Men y Marvel.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Street Fighter II Turbo (USA)", "Street Fighter II Turbo (USA)"),
            includePatterns = listOf(rx("""\b(street fighter|x men vs|marvel super heroes vs|marvel vs capcom|super gem fighter|capcom vs snk)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Street Fighter", 1987, "arcade", "Origen Arcade", listOf(rx("""^street fighter$""")), preferConsoles = listOf("arcade", "pce")),
                SagaMilestoneDef("Street Fighter II", 1991, "arcade", "CPS-1 Clásico", listOf(rx("""\bstreet fighter (ii|2)\b""")), listOf(rx("""\b(super|alpha|ex|iii|3|vs)\b""")), preferConsoles = listOf("arcade", "snes", "genesis")),
                SagaMilestoneDef("Super Street Fighter II Turbo", 1994, "arcade", "CPS-2 Clásico", listOf(rx("""\bsuper street fighter ii\b""")), preferConsoles = listOf("arcade", "snes", "dc"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Street Fighter II Turbo (USA)"),
                SagaMilestoneDef("Street Fighter Alpha / Zero", 1995, "arcade", "Sub-serie Alpha", listOf(rx("""\bstreet fighter (alpha|zero)( 1)?\b""")), listOf(rx("""\b(2|3)\b""")), preferConsoles = listOf("arcade", "ps1")),
                SagaMilestoneDef("Street Fighter Alpha 2", 1996, "arcade", "Sub-serie Alpha", listOf(rx("""\bstreet fighter (alpha|zero) 2\b""")), preferConsoles = listOf("arcade", "snes", "ps1")),
                SagaMilestoneDef("X-Men Vs. Street Fighter", 1996, "arcade", "Crossover CPS-2", listOf(rx("""\bx men vs street fighter\b""")), preferConsoles = listOf("arcade", "saturn", "ps1")),
                SagaMilestoneDef("Marvel Super Heroes Vs. Street Fighter", 1997, "arcade", "Crossover CPS-2", listOf(rx("""\bmarvel super heroes vs\b""")), preferConsoles = listOf("arcade", "ps1")),
                SagaMilestoneDef("Marvel Vs. Capcom: Clash of Super Heroes", 1998, "arcade", "Crossover CPS-2", listOf(rx("""\bmarvel vs capcom\b""")), listOf(rx("""\b2\b""")), preferConsoles = listOf("arcade", "dc", "ps1")),
                SagaMilestoneDef("Street Fighter Alpha 3", 1998, "arcade", "Sub-serie Alpha", listOf(rx("""\b(alpha|zero) 3\b""")), preferConsoles = listOf("arcade", "ps1", "dc", "gba")),
                SagaMilestoneDef("Street Fighter III: 3rd Strike", 1999, "arcade", "CPS-3 Cumbre", listOf(rx("""\b(street fighter iii|3rd strike|third strike)\b""")), preferConsoles = listOf("arcade", "dc", "ps2")),
                SagaMilestoneDef("Marvel Vs. Capcom 2", 2000, "dc", "Crossover Naomi", listOf(rx("""\bmarvel vs capcom 2\b""")), preferConsoles = listOf("dc", "ps2")),
            ),
        ),
        SagaDefinition(
            id = "mortal-kombat",
            name = "Mortal Kombat",
            tagline = "El torneo de Outworld, actores digitalizados, secretos y Fatalities.",
            foundedYear = 1992,
            creator = "Midway / Ed Boon & John Tobias",
            accentHex = "#FF4D6D",
            history = "En 1992 Ed Boon y John Tobias revolucionaron los salones recreativos con Mortal Kombat usando actores digitalizados y los legendarios Fatalities. Mortal Kombat II y Ultimate Mortal Kombat 3 llevaron la lucha 2D a su máxima expresión.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Mortal Kombat II (USA)", "Mortal Kombat II (USA)"),
            includePatterns = listOf(rx("""\bmortal kombat\b""")),
            timeline = listOf(
                SagaMilestoneDef("Mortal Kombat", 1992, "arcade", "Arcade 2D", listOf(rx("""^mortal kombat$""")), listOf(rx("""\b(ii|iii|iv|2|3|4|trilogy|mythologies|gold|deadly|shaolin|armageddon|deception)\b""")), preferConsoles = listOf("arcade", "genesis", "snes")),
                SagaMilestoneDef("Mortal Kombat II", 1993, "arcade", "Arcade 2D", listOf(rx("""\bmortal kombat (ii|2)\b""")), preferConsoles = listOf("arcade", "snes", "genesis"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Mortal Kombat II (USA)"),
                SagaMilestoneDef("Mortal Kombat 3", 1995, "arcade", "Arcade 2D", listOf(rx("""^mortal kombat 3$""")), preferConsoles = listOf("arcade", "snes", "genesis", "ps1")),
                SagaMilestoneDef("Ultimate Mortal Kombat 3", 1995, "arcade", "Arcade 2D", listOf(rx("""\bultimate mortal kombat 3\b""")), preferConsoles = listOf("arcade", "snes", "genesis", "saturn")),
                SagaMilestoneDef("Mortal Kombat Trilogy", 1996, "ps1", "Compilación Clásica", listOf(rx("""\bmortal kombat trilogy\b""")), preferConsoles = listOf("ps1", "n64"), allowCombo = true),
                SagaMilestoneDef("Mortal Kombat 4 / Gold", 1997, "n64", "Salto al 3D", listOf(rx("""\bmortal kombat (4|gold)\b""")), preferConsoles = listOf("n64", "ps1", "dc")),
                SagaMilestoneDef("Mortal Kombat: Shaolin Monks", 2005, "ps2", "Aventura PS2", listOf(rx("""\bshaolin monks\b""")), preferConsoles = listOf("ps2")),
            ),
        ),
        SagaDefinition(
            id = "metal-slug",
            name = "Metal Slug",
            tagline = "Run & gun de SNK con la animación pixel-art más expresiva del arcade.",
            foundedYear = 1996,
            creator = "Nazca / SNK",
            accentHex = "#A3E635",
            history = "Desarrollado por Nazca Corporation para Neo Geo en 1996, Metal Slug elevó el pixel-art a obra maestra animada cuadro por cuadro con humor bélico y el icónico tanque SV-001.",
            heroArt = SagaHeroArt("FBNeo - Arcade Games", "Metal Slug - Super Vehicle-001", "Metal Slug - Super Vehicle-001"),
            includePatterns = listOf(rx("""\bmetal slug\b""")),
            timeline = listOf(
                SagaMilestoneDef("Metal Slug: Super Vehicle-001", 1996, "arcade", "Neo Geo MVS", listOf(rx("""^metal slug( super vehicle 001)?$""")), listOf(rx("""\b(2|x|3|4|5|6|7|advance|xx)\b""")), preferConsoles = listOf("arcade", "ps1"), libretroArt = "FBNeo - Arcade Games" to "Metal Slug - Super Vehicle-001"),
                SagaMilestoneDef("Metal Slug 2", 1998, "arcade", "Neo Geo MVS", listOf(rx("""\bmetal slug 2\b""")), preferConsoles = listOf("arcade")),
                SagaMilestoneDef("Metal Slug X", 1999, "arcade", "Neo Geo MVS", listOf(rx("""\bmetal slug x\b""")), preferConsoles = listOf("arcade", "ps1")),
                SagaMilestoneDef("Metal Slug 3", 2000, "arcade", "Neo Geo MVS", listOf(rx("""\bmetal slug 3\b""")), preferConsoles = listOf("arcade", "ps2")),
                SagaMilestoneDef("Metal Slug 4", 2002, "arcade", "Neo Geo MVS", listOf(rx("""\bmetal slug 4\b""")), preferConsoles = listOf("arcade", "ps2")),
                SagaMilestoneDef("Metal Slug 5", 2003, "arcade", "Neo Geo MVS", listOf(rx("""\bmetal slug 5\b""")), preferConsoles = listOf("arcade", "ps2")),
                SagaMilestoneDef("Metal Slug Advance", 2004, "gba", "Portátil GBA", listOf(rx("""\bmetal slug advance\b""")), preferConsoles = listOf("gba")),
            ),
        ),
        SagaDefinition(
            id = "kirby",
            name = "Kirby",
            tagline = "Aventuras coloridas en Dream Land absorbiendo habilidades enemigas.",
            foundedYear = 1992,
            creator = "HAL Laboratory / Masahiro Sakurai",
            accentHex = "#F9A8D4",
            history = "Creado por Masahiro Sakurai en 1992 en Game Boy, Kirby conquistó Dream Land como un héroe accesible repleto de transformaciones. De Kirby Super Star en SNES a The Amazing Mirror en GBA, combina carisma y exploración.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Kirby Super Star (USA)", "Kirby Super Star (USA)"),
            includePatterns = listOf(rx("""\bkirby\b""")),
            timeline = listOf(
                SagaMilestoneDef("Kirby's Dream Land", 1992, "gb", "Game Boy", listOf(rx("""\bkirby s? dream land$""")), listOf(rx("""\b(2|3|nightmare)\b""")), preferConsoles = listOf("gb")),
                SagaMilestoneDef("Kirby's Adventure", 1993, "nes", "Era 8-bit", listOf(rx("""\bkirby s? adventure\b""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Kirby's Dream Land 2", 1995, "gb", "Game Boy", listOf(rx("""\bdream land 2\b""")), preferConsoles = listOf("gb")),
                SagaMilestoneDef("Kirby Super Star", 1996, "snes", "Era 16-bit", listOf(rx("""\bsuper star\b"""), rx("""\bfun pak\b""")), preferConsoles = listOf("snes", "nds"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Kirby Super Star (USA)"),
                SagaMilestoneDef("Kirby's Dream Land 3", 1997, "snes", "Era 16-bit", listOf(rx("""\bdream land 3\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Kirby 64: The Crystal Shards", 2000, "n64", "Era 64-bit", listOf(rx("""\bkirby 64\b"""), rx("""\bcrystal shards\b""")), preferConsoles = listOf("n64")),
                SagaMilestoneDef("Kirby: Nightmare in Dream Land", 2002, "gba", "Portátil GBA", listOf(rx("""\bnightmare in dream\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Kirby & The Amazing Mirror", 2004, "gba", "Metroidvania GBA", listOf(rx("""\bamazing mirror\b""")), preferConsoles = listOf("gba")),
            ),
        ),
        SagaDefinition(
            id = "pokemon",
            name = "Pokémon",
            tagline = "Captura, estrategia por turnos y viajes inolvidables de Kanto a Hoenn.",
            foundedYear = 1996,
            creator = "Game Freak / Satoshi Tajiri & Ken Sugimori",
            accentHex = "#FACC15",
            history = "Inspirado por Satoshi Tajiri en 1996 para Game Boy, Pokémon se convirtió en un fenómeno cultural sin precedentes gracias a su intercambio por Cable Link, combates estratégicos y aventuras portátiles de Rojo/Azul a Esmeralda y Rojo Fuego.",
            heroArt = SagaHeroArt("Nintendo - Game Boy Advance", "Pokemon - Emerald Version (USA, Europe)", "Pokemon - Emerald Version (USA, Europe)"),
            includePatterns = listOf(rx("""\bpokemon\b""")),
            timeline = listOf(
                SagaMilestoneDef("Pokémon Rojo / Azul / Amarillo", 1996, "gb", "Gen I · Kanto", listOf(rx("""\bpokemon (red|blue|yellow|rojo|azul|amarillo)\b""")), listOf(rx("""\b(fire|fuego|rescue|misterioso)\b""")), preferConsoles = listOf("gb")),
                SagaMilestoneDef("Pokémon Snap", 1999, "n64", "Spin-off N64", listOf(rx("""\bpokemon snap\b""")), preferConsoles = listOf("n64")),
                SagaMilestoneDef("Pokémon Oro / Plata / Cristal", 1999, "gbc", "Gen II · Johto", listOf(rx("""\bpokemon (gold|silver|crystal|oro|plata|cristal)\b""")), preferConsoles = listOf("gbc")),
                SagaMilestoneDef("Pokémon Stadium", 1999, "n64", "N64 Arena", listOf(rx("""^pokemon stadium$""")), listOf(rx("""\b2\b""")), excludeConsoles = setOf("snes"), preferConsoles = listOf("n64")),
                SagaMilestoneDef("Pokémon Stadium 2", 2000, "n64", "N64 Arena", listOf(rx("""\bpokemon stadium 2\b""")), preferConsoles = listOf("n64")),
                SagaMilestoneDef("Pokémon Rubí / Zafiro", 2002, "gba", "Gen III · Hoenn", listOf(rx("""\bpokemon (ruby|sapphire|rubi|zafiro)\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Pokémon Rojo Fuego / Verde Hoja", 2004, "gba", "Gen III · Remake", listOf(rx("""\bpokemon (fire ?red|leaf ?green|rojo fuego|verde hoja)\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Pokémon Esmeralda", 2004, "gba", "Gen III · Hoenn", listOf(rx("""\bpokemon.*(emerald|esmeralda)\b""")), preferConsoles = listOf("gba"), libretroArt = "Nintendo - Game Boy Advance" to "Pokemon - Emerald Version (USA, Europe)"),
                SagaMilestoneDef("Pokémon Mundo Misterioso: Equipo de Rescate Rojo", 2005, "gba", "Roguelike GBA", listOf(rx("""\b(mystery dungeon|mundo misterioso)\b""")), preferConsoles = listOf("gba", "nds")),
            ),
        ),
        SagaDefinition(
            id = "streets-of-rage",
            name = "Streets of Rage",
            tagline = "Beat 'em up callejero clásico de Sega al ritmo electrónico de Yuzo Koshiro.",
            foundedYear = 1991,
            creator = "Sega / Yuzo Koshiro",
            accentHex = "#38BDF8",
            history = "Conocida como Bare Knuckle en Japón, Streets of Rage debutó en 1991 en Mega Drive acompañada por la vanguardista música electrónica de Yuzo Koshiro. Streets of Rage 2 (1992) figura entre los mejores beat 'em up cooperativos de todos los tiempos.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Streets of Rage 2 (USA)", "Streets of Rage 2 (USA)"),
            includePatterns = listOf(rx("""\b(streets of rage|bare knuckle)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Streets of Rage", 1991, "genesis", "Mega Drive", listOf(rx("""^(streets of rage|bare knuckle)( 1| i)?$"""), rx("""\bbare knuckle.*streets of rage\b""")), listOf(rx("""\b(2|3|4|ii|iii)\b""")), preferConsoles = listOf("genesis", "sms")),
                SagaMilestoneDef("Streets of Rage 2", 1992, "genesis", "Mega Drive", listOf(rx("""\b(streets of rage 2|bare knuckle ii)\b""")), preferConsoles = listOf("genesis", "sms"), libretroArt = "Sega - Mega Drive - Genesis" to "Streets of Rage 2 (USA)"),
                SagaMilestoneDef("Streets of Rage 3", 1994, "genesis", "Mega Drive", listOf(rx("""\b(streets of rage 3|bare knuckle iii)\b""")), preferConsoles = listOf("genesis")),
            ),
        ),
        SagaDefinition(
            id = "phantasy-star",
            name = "Phantasy Star",
            tagline = "Ciencia ficción, hechicería y épicas espaciales del sistema estelar Algol.",
            foundedYear = 1987,
            creator = "Sega / Rieko Kodama & Yuji Naka",
            accentHex = "#7DD3FC",
            history = "Pionera absoluta entre los RPG de consola desde Master System (1987), la tetralogía clásica del sistema Algol culminó con Phantasy Star IV (1993) en Mega Drive mediante secuencias estilo cómic y combos mágicos.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Phantasy Star IV (USA)", "Phantasy Star IV (USA)"),
            includePatterns = listOf(rx("""\bphantasy star\b""")),
            timeline = listOf(
                SagaMilestoneDef("Phantasy Star", 1987, "sms", "Master System", listOf(rx("""^phantasy star$""")), preferConsoles = listOf("sms", "gba")),
                SagaMilestoneDef("Phantasy Star II", 1989, "genesis", "Mega Drive", listOf(rx("""^phantasy star (ii|2)$"""), rx("""\bkaerazaru toki\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Phantasy Star III: Generations of Doom", 1990, "genesis", "Mega Drive", listOf(rx("""\bphantasy star (iii|3)\b"""), rx("""\bgenerations of doom\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Phantasy Star IV: The End of the Millennium", 1993, "genesis", "Mega Drive", listOf(rx("""\bphantasy star (iv|4)\b"""), rx("""\bend of the millennium\b"""), rx("""\bsennenki no owari ni\b""")), preferConsoles = listOf("genesis"), libretroArt = "Sega - Mega Drive - Genesis" to "Phantasy Star IV (USA)"),
                SagaMilestoneDef("Phantasy Star Online", 2000, "dc", "Dreamcast", listOf(rx("""\bphantasy star online\b""")), preferConsoles = listOf("dc", "gc")),
            ),
        ),
        SagaDefinition(
            id = "chrono",
            name = "Saga Chrono",
            tagline = "Viajes en el tiempo, mundos paralelos y música inmortal de Yasunori Mitsuda.",
            foundedYear = 1995,
            creator = "Square / Dream Team",
            accentHex = "#A78BFA",
            history = "Chrono Trigger (1995) reunió en Super Nintendo al 'Dream Team' formado por Hironobu Sakaguchi, Yuji Horii y Akira Toriyama con combates sin transición y múltiples finales, expandido luego con Chrono Cross en PlayStation.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Chrono Trigger (USA)", "Chrono Trigger (USA)"),
            includePatterns = listOf(rx("""\b(chrono (trigger|cross)|radical dreamers)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Chrono Trigger", 1995, "snes", "SNES / DS", listOf(rx("""\bchrono trigger\b""")), preferConsoles = listOf("snes", "nds", "ps1"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Chrono Trigger (USA)"),
                SagaMilestoneDef("Radical Dreamers", 1996, "snes", "Satellaview", listOf(rx("""\bradical dreamers\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Chrono Cross", 1999, "ps1", "PlayStation", listOf(rx("""\bchrono cross\b""")), preferConsoles = listOf("ps1")),
            ),
        ),
        SagaDefinition(
            id = "silent-hill",
            name = "Silent Hill",
            tagline = "Terror psicológico envuelto en niebla, culpa y radios con estática.",
            foundedYear = 1999,
            creator = "Konami / Team Silent & Akira Yamaoka",
            accentHex = "#94A3B8",
            history = "Nacida en 1999 en Team Silent, Silent Hill transformó la niebla de PS1 en su mayor virtud estética, creando una cuatrilogía irrepetible de terror psicológico acompañada por las composiciones de Akira Yamaoka.",
            heroArt = SagaHeroArt("Sony - PlayStation", "Silent Hill (USA)", "Silent Hill (USA)"),
            includePatterns = listOf(rx("""\bsilent hill\b""")),
            timeline = listOf(
                SagaMilestoneDef("Silent Hill", 1999, "ps1", "Team Silent", listOf(rx("""^silent hill$""")), preferConsoles = listOf("ps1"), libretroArt = "Sony - PlayStation" to "Silent Hill (USA)"),
                SagaMilestoneDef("Silent Hill 2", 2001, "ps2", "Team Silent", listOf(rx("""\bsilent hill 2\b""")), preferConsoles = listOf("ps2")),
                SagaMilestoneDef("Silent Hill 3", 2003, "ps2", "Team Silent", listOf(rx("""\bsilent hill 3\b""")), preferConsoles = listOf("ps2")),
                SagaMilestoneDef("Silent Hill 4: The Room", 2004, "ps2", "Team Silent", listOf(rx("""\bsilent hill 4\b"""), rx("""\bthe room\b""")), preferConsoles = listOf("ps2")),
            ),
        ),
        SagaDefinition(
            id = "metal-gear",
            name = "Metal Gear",
            tagline = "Infiltración táctica, tramas cinematográficas y espionaje junto a Solid Snake.",
            foundedYear = 1987,
            creator = "Konami / Hideo Kojima",
            accentHex = "#5EEAD4",
            history = "Hideo Kojima creó en 1987 el sigilo táctico moderno con Metal Gear, antes de revolucionar PlayStation en 1998 con Metal Gear Solid, Sons of Liberty y Snake Eater.",
            heroArt = SagaHeroArt("Sony - PlayStation", "Metal Gear Solid (USA) (Disc 1)", "Metal Gear Solid (USA) (Disc 1)"),
            includePatterns = listOf(rx("""\bmetal gear\b""")),
            timeline = listOf(
                SagaMilestoneDef("Metal Gear", 1987, "nes", "Origen 8-bit", listOf(rx("""^metal gear$""")), preferConsoles = listOf("msx", "nes")),
                SagaMilestoneDef("Metal Gear 2: Solid Snake", 1990, "msx", "MSX2", listOf(rx("""\bmetal gear 2\b""")), preferConsoles = listOf("msx")),
                SagaMilestoneDef("Metal Gear Solid", 1998, "ps1", "Era Solid", listOf(rx("""^metal gear solid$"""), rx("""\btwin snakes\b""")), preferConsoles = listOf("ps1", "gc"), libretroArt = "Sony - PlayStation" to "Metal Gear Solid (USA) (Disc 1)"),
                SagaMilestoneDef("Metal Gear Solid 2: Sons of Liberty", 2001, "ps2", "Era PS2", listOf(rx("""\bmetal gear solid 2\b"""), rx("""\bsons of liberty\b""")), preferConsoles = listOf("ps2")),
                SagaMilestoneDef("Metal Gear Solid 3: Snake Eater", 2004, "ps2", "Era PS2", listOf(rx("""\bmetal gear solid 3\b"""), rx("""\bsnake eater\b""")), preferConsoles = listOf("ps2", "3ds")),
            ),
        ),
        SagaDefinition(
            id = "007-bond",
            name = "007 · James Bond",
            tagline = "Misiones secretas, espionaje táctico y multijugador mítico del agente 007.",
            foundedYear = 1993,
            creator = "Rare / EA / MGM",
            accentHex = "#FBBF24",
            history = "Desde The Duel en Mega Drive hasta el hito histórico de GoldenEye 007 en Nintendo 64, que redefinió los shooters en consola y el multijugador a pantalla dividida, siguiendo con Everything or Nothing y From Russia with Love.",
            heroArt = SagaHeroArt("Nintendo - Nintendo 64", "GoldenEye 007 (USA)", "GoldenEye 007 (USA)"),
            includePatterns = listOf(rx("""\b(007|goldeneye|james bond)\b""")),
            excludePatterns = listOf(rx("""\bjames pond\b""")),
            timeline = listOf(
                SagaMilestoneDef("James Bond 007: The Duel", 1993, "genesis", "16-bit", listOf(rx("""\bthe duel\b"""), rx("""\bshitou\b""")), preferConsoles = listOf("genesis", "sms", "gg")),
                SagaMilestoneDef("GoldenEye 007", 1997, "n64", "Clásico N64", listOf(rx("""\bgoldeneye\b""")), preferConsoles = listOf("n64", "wii"), libretroArt = "Nintendo - Nintendo 64" to "GoldenEye 007 (USA)"),
                SagaMilestoneDef("007: Nightfire", 2002, "gc", "128-bit", listOf(rx("""\bnightfire\b""")), preferConsoles = listOf("gc", "ps2", "gba")),
                SagaMilestoneDef("007: Everything or Nothing", 2004, "gc", "128-bit", listOf(rx("""\beverything or nothing\b""")), preferConsoles = listOf("gc", "ps2", "gba")),
                SagaMilestoneDef("007: From Russia with Love", 2005, "gc", "128-bit", listOf(rx("""\brussia with love\b""")), preferConsoles = listOf("gc", "ps2", "psp")),
            ),
        ),
        SagaDefinition(
            id = "contra",
            name = "Contra",
            tagline = "Acción explosiva cooperativa contra hordas alienígenas y el Código Konami.",
            foundedYear = 1987,
            creator = "Konami",
            accentHex = "#F87171",
            history = "Nacido en arcades en 1987 e inmortalizado un año después en NES junto al Código Konami de las 30 vidas, Contra definió el run & gun cooperativo y alcanzó cimas gemelas en SNES y Mega Drive.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Contra III - The Alien Wars (USA)", "Contra III - The Alien Wars (USA)"),
            includePatterns = listOf(rx("""\b(contra|super c|probotector)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Contra", 1987, "nes", "Era 8-bit", listOf(rx("""^contra$""")), listOf(rx("""\b(iii|3|hard|force|shattered|rebirth|4)\b""")), preferConsoles = listOf("nes", "arcade")),
                SagaMilestoneDef("Super C (Super Contra)", 1988, "nes", "Era 8-bit", listOf(rx("""\bsuper c(ontra)?\b""")), preferConsoles = listOf("nes", "arcade")),
                SagaMilestoneDef("Contra III: The Alien Wars", 1992, "snes", "SNES 16-bit", listOf(rx("""\bcontra iii\b"""), rx("""\balien wars\b""")), preferConsoles = listOf("snes", "gba"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Contra III - The Alien Wars (USA)"),
                SagaMilestoneDef("Contra: Hard Corps", 1994, "genesis", "Mega Drive", listOf(rx("""\b(the )?hard corps\b"""), rx("""^probotector$""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Contra: Shattered Soldier", 2002, "ps2", "Era PS2", listOf(rx("""\bshattered soldier\b""")), preferConsoles = listOf("ps2")),
            ),
        ),
        SagaDefinition(
            id = "shinobi",
            name = "Shinobi",
            tagline = "Ninjutsu letal, shurikens y estilo clásico arcade de Sega con Joe Musashi.",
            foundedYear = 1987,
            creator = "Sega / Yutaka Sugano & Noriyoshi Ohba",
            accentHex = "#F43F5E",
            history = "Tras conquistar los salones arcade en 1987, Shinobi se convirtió en pilar de Mega Drive con The Revenge of Shinobi, Shadow Dancer y la obra maestra Shinobi III: Return of the Ninja Master.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Shinobi III - Return of the Ninja Master (USA)", "Shinobi III - Return of the Ninja Master (USA)"),
            includePatterns = listOf(rx("""\b(shinobi|shadow dancer)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Shinobi", 1987, "arcade", "Arcade / SMS", listOf(rx("""^shinobi$""")), listOf(rx("""\b(revenge|shadow|iii|3|ii|2|super|legions)\b""")), preferConsoles = listOf("arcade", "sms")),
                SagaMilestoneDef("The Revenge of Shinobi (The Super Shinobi)", 1989, "genesis", "Mega Drive", listOf(rx("""\brevenge of shinobi\b"""), rx("""\bsuper shinobi\b(?!.*\b(?:ii|2)\b)""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Shadow Dancer: The Secret of Shinobi", 1990, "genesis", "Mega Drive", listOf(rx("""\bshadow dancer\b""")), preferConsoles = listOf("genesis", "arcade")),
                SagaMilestoneDef("Shinobi III: Return of the Ninja Master", 1993, "genesis", "Mega Drive", listOf(rx("""\bshinobi iii\b"""), rx("""\breturn of the ninja\b"""), rx("""\bsuper shinobi ii\b""")), preferConsoles = listOf("genesis"), libretroArt = "Sega - Mega Drive - Genesis" to "Shinobi III - Return of the Ninja Master (USA)"),
            ),
        ),
        SagaDefinition(
            id = "golden-axe",
            name = "Golden Axe",
            tagline = "Fantasía heroica, bestias montables y espadas arcade de Sega.",
            foundedYear = 1989,
            creator = "Sega / Makoto Uchida",
            accentHex = "#FBBF24",
            history = "Estrenado en 1989 en placas System 16 y Mega Drive, Golden Axe permitió cabalgar dragones y liberar magias elementales en una trilogía clásica de espada y brujería cooperativa.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Golden Axe (World)", "Golden Axe (World)"),
            includePatterns = listOf(rx("""\bgolden axe\b""")),
            timeline = listOf(
                SagaMilestoneDef("Golden Axe", 1989, "genesis", "Arcade / Mega Drive", listOf(rx("""^golden axe$""")), listOf(rx("""\b(ii|iii|death adder)\b""")), preferConsoles = listOf("arcade", "genesis"), libretroArt = "Sega - Mega Drive - Genesis" to "Golden Axe (World)"),
                SagaMilestoneDef("Golden Axe II", 1991, "genesis", "Mega Drive", listOf(rx("""\bgolden axe ii\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Golden Axe: The Revenge of Death Adder", 1992, "arcade", "Arcade System 32", listOf(rx("""\bdeath adder\b""")), preferConsoles = listOf("arcade")),
                SagaMilestoneDef("Golden Axe III", 1993, "genesis", "Mega Drive", listOf(rx("""\bgolden axe iii\b""")), preferConsoles = listOf("genesis")),
            ),
        ),
        SagaDefinition(
            id = "double-dragon",
            name = "Double Dragon",
            tagline = "Los hermanos Billy y Jimmy Lee inaugurando el beat 'em up cooperativo moderno.",
            foundedYear = 1987,
            creator = "Technōs Japan / Yoshihisa Kishimoto",
            accentHex = "#FB923C",
            history = "Technōs Japan estableció en 1987 las reglas definitivas del beat 'em up urbano cooperativo en Double Dragon, brillando en NES, Super Nintendo y su cruce con Battletoads.",
            heroArt = SagaHeroArt("Nintendo - Nintendo Entertainment System", "Double Dragon II - The Revenge (USA)", "Double Dragon II - The Revenge (USA)"),
            includePatterns = listOf(rx("""\bdouble dragon\b""")),
            timeline = listOf(
                SagaMilestoneDef("Double Dragon", 1987, "genesis", "Arcade / Consolas", listOf(rx("""^double dragon$""")), preferConsoles = listOf("arcade", "nes", "genesis")),
                SagaMilestoneDef("Double Dragon II: The Revenge", 1988, "nes", "Arcade / NES", listOf(rx("""\bdouble dragon ii\b""")), preferConsoles = listOf("nes", "genesis", "arcade"), libretroArt = "Nintendo - Nintendo Entertainment System" to "Double Dragon II - The Revenge (USA)"),
                SagaMilestoneDef("Double Dragon 3", 1990, "genesis", "Arcade / 16-bit", listOf(rx("""\bdouble dragon (3|iii)\b""")), preferConsoles = listOf("nes", "genesis", "arcade")),
                SagaMilestoneDef("Super Double Dragon", 1992, "snes", "SNES 16-bit", listOf(rx("""\bsuper double dragon\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Battletoads & Double Dragon", 1993, "snes", "Crossover Rare", listOf(rx("""\bbattletoads.*double dragon\b""")), preferConsoles = listOf("snes", "genesis", "nes"), allowCombo = true),
                SagaMilestoneDef("Double Dragon V: The Shadow Falls", 1994, "snes", "Lucha 16-bit", listOf(rx("""\bdouble dragon v\b"""), rx("""\bshadow falls\b""")), preferConsoles = listOf("snes", "genesis")),
            ),
        ),
        SagaDefinition(
            id = "breath-of-fire",
            name = "Breath of Fire",
            tagline = "Dragones legendarios, clanes antropomorfos y JRPG clásicos de Capcom.",
            foundedYear = 1993,
            creator = "Capcom / Makoto Ikehara",
            accentHex = "#60A5FA",
            history = "La gran franquicia JRPG tradicional de Capcom desde Super Nintendo hasta PlayStation, siguiendo a Ryu y Nina a través de milenios con transformaciones en dragón.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Breath of Fire II (USA)", "Breath of Fire II (USA)"),
            includePatterns = listOf(rx("""\bbreath of fire\b""")),
            timeline = listOf(
                SagaMilestoneDef("Breath of Fire", 1993, "snes", "SNES / GBA", listOf(rx("""^breath of fire$""")), preferConsoles = listOf("snes", "gba")),
                SagaMilestoneDef("Breath of Fire II", 1994, "snes", "SNES / GBA", listOf(rx("""^breath of fire (ii|2)$""")), preferConsoles = listOf("snes", "gba"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Breath of Fire II (USA)"),
                SagaMilestoneDef("Breath of Fire III", 1997, "ps1", "PlayStation", listOf(rx("""^breath of fire (iii|3)$""")), preferConsoles = listOf("ps1", "psp")),
                SagaMilestoneDef("Breath of Fire IV", 2000, "ps1", "PlayStation", listOf(rx("""^breath of fire (iv|4)$""")), preferConsoles = listOf("ps1")),
            ),
        ),
        SagaDefinition(
            id = "dragon-quest",
            name = "Dragon Quest",
            tagline = "La saga fundadora del JRPG con diseños inconfundibles de Akira Toriyama.",
            foundedYear = 1986,
            creator = "Enix / Yuji Horii, Akira Toriyama & Koichi Sugiyama",
            accentHex = "#38BDF8",
            history = "Desde su debut en Famicom en 1986, Dragon Quest se convirtió en patrimonio del JRPG gracias a Yuji Horii, los diseños de Akira Toriyama y la música sinfónica de Koichi Sugiyama.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Dragon Quest V - Tenkuu no Hanayome (Japan)", "Dragon Quest V - Tenkuu no Hanayome (Japan)"),
            includePatterns = listOf(rx("""\bdragon (quest|warrior)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Dragon Quest III", 1988, "nes", "Trilogía Erdrick", listOf(rx("""\bdragon (quest|warrior) (iii|3)\b""")), preferConsoles = listOf("snes", "nes", "gbc")),
                SagaMilestoneDef("Dragon Quest IV", 1990, "nes", "Trilogía Zenithia", listOf(rx("""\bdragon (quest|warrior) (iv|4)\b""")), preferConsoles = listOf("nds", "nes", "ps1")),
                SagaMilestoneDef("Dragon Quest V: Hand of the Heavenly Bride", 1992, "snes", "Trilogía Zenithia", listOf(rx("""\bdragon quest (v|5)\b"""), rx("""\btenkuu no hanayome\b""")), preferConsoles = listOf("snes", "nds", "ps2"), libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Dragon Quest V - Tenkuu no Hanayome (Japan)"),
                SagaMilestoneDef("Dragon Quest VI", 1995, "snes", "Trilogía Zenithia", listOf(rx("""\bdragon quest (vi|6)\b""")), preferConsoles = listOf("snes", "nds")),
                SagaMilestoneDef("Dragon Quest VIII", 2004, "ps2", "Era PS2", listOf(rx("""\bdragon quest (viii|8)\b""")), preferConsoles = listOf("ps2", "3ds")),
            ),
        ),
        SagaDefinition(
            id = "ninja-gaiden",
            name = "Ninja Gaiden",
            tagline = "Cinemáticas pioneras y precisión ninja extrema protagonizada por Ryu Hayabusa.",
            foundedYear = 1988,
            creator = "Tecmo / Hideo Yoshizawa & Tomonobu Itagaki",
            accentHex = "#C084FC",
            history = "Tecmo inauguró en 1988 una trilogía legendaria en NES con cinemáticas estilo anime y acción vertiginosa por paredes, reunida luego en SNES con Ninja Gaiden Trilogy.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Ninja Gaiden Trilogy (USA)", "Ninja Gaiden Trilogy (USA)"),
            includePatterns = listOf(rx("""\bninja (gaiden|ryukenden)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Ninja Gaiden", 1988, "nes", "Trilogía 8-bit", listOf(rx("""^ninja gaiden$""")), preferConsoles = listOf("nes", "arcade")),
                SagaMilestoneDef("Ninja Gaiden II: The Dark Sword of Chaos", 1990, "nes", "Trilogía 8-bit", listOf(rx("""\bninja gaiden ii\b""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Ninja Gaiden III: The Ancient Ship of Doom", 1991, "nes", "Trilogía 8-bit", listOf(rx("""\bninja gaiden iii\b""")), preferConsoles = listOf("nes")),
                SagaMilestoneDef("Ninja Gaiden Trilogy", 1995, "snes", "Compilación 16-bit", listOf(rx("""\bninja gaiden trilogy\b""")), preferConsoles = listOf("snes"), allowCombo = true, libretroArt = "Nintendo - Super Nintendo Entertainment System" to "Ninja Gaiden Trilogy (USA)"),
            ),
        ),
        SagaDefinition(
            id = "king-of-fighters",
            name = "The King of Fighters",
            tagline = "Equipos 3 contra 3 y el torneo definitivo de estrellas arcade de SNK.",
            foundedYear = 1994,
            creator = "SNK / Takashi Nishiyama",
            accentHex = "#F59E0B",
            history = "En 1994 SNK reunió los elencos de Fatal Fury y Art of Fighting en equipos 3v3 de lucha arcade, brillando con la saga Orochi hasta el legendario Dream Match de KOF '98 y '99.",
            heroArt = SagaHeroArt("SNK - Neo Geo", "The King of Fighters '98 - The Slugfest _ King of Fighters '98 - dream match never ends (NGM-2420)", "The King of Fighters '98 - The Slugfest _ King of Fighters '98 - dream match never ends (NGM-2420)"),
            includePatterns = listOf(rx("""\bking of fighters\b""")),
            timeline = listOf(
                SagaMilestoneDef("The King of Fighters '94", 1994, "arcade", "Saga Orochi", listOf(rx("""\bking of fighters '?94\b""")), preferConsoles = listOf("arcade")),
                SagaMilestoneDef("The King of Fighters '96", 1996, "arcade", "Saga Orochi", listOf(rx("""\bking of fighters '?96\b""")), preferConsoles = listOf("arcade", "ps1")),
                SagaMilestoneDef("The King of Fighters '97", 1997, "arcade", "Saga Orochi", listOf(rx("""\bking of fighters '?97\b""")), preferConsoles = listOf("arcade", "ps1")),
                SagaMilestoneDef("The King of Fighters '98", 1998, "arcade", "Dream Match", listOf(rx("""\bking of fighters '?98\b""")), preferConsoles = listOf("arcade", "ps1", "dc")),
                SagaMilestoneDef("The King of Fighters '99", 1999, "arcade", "Crónicas NESTS", listOf(rx("""\bking of fighters '?99\b""")), preferConsoles = listOf("arcade", "ps1", "dc")),
                SagaMilestoneDef("The King of Fighters 2002", 2002, "arcade", "Dream Match", listOf(rx("""\bking of fighters 2002\b""")), preferConsoles = listOf("arcade", "dc", "ps2")),
            ),
        ),
        SagaDefinition(
            id = "dragon-ball",
            name = "Dragon Ball / Z",
            tagline = "Torneos de artes marciales, Kamehamehas y batallas desde Butouden a Budokai.",
            foundedYear = 1986,
            creator = "Akira Toriyama / Bandai",
            accentHex = "#F97316",
            history = "Las adaptaciones de Dragon Ball Z marcaron los 16 bits con Super Butouden y Buyuu Retsuden en Mega Drive, antes de triunfar en GBA con Legacy of Goku y Supersonic Warriors.",
            heroArt = SagaHeroArt("Nintendo - Game Boy Advance", "Dragon Ball Z - Supersonic Warriors (USA)", "Dragon Ball Z - Supersonic Warriors (USA)"),
            includePatterns = listOf(rx("""\bdragon ?ball\b""")),
            timeline = listOf(
                SagaMilestoneDef("Dragon Ball Z: Super Butouden", 1993, "snes", "Butouden 16-bit", listOf(rx("""\bsuper buto[u]?den$"""), rx("""\bla legende saien\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Dragon Ball Z: Buyuu Retsuden (L'Appel du Destin)", 1994, "genesis", "Mega Drive 16-bit", listOf(rx("""\bbuyuu retsuden\b"""), rx("""\bl appel du destin\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Dragon Ball Z: Super Butouden 2", 1994, "snes", "Butouden 16-bit", listOf(rx("""\bsuper buto[u]?den 2\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Dragon Ball Z: Super Butouden 3", 1994, "snes", "Butouden 16-bit", listOf(rx("""\bsuper buto[u]?den 3\b"""), rx("""\bultime menace\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Dragon Ball Z: Hyper Dimension", 1996, "snes", "SNES Tardío", listOf(rx("""\bhyper dimension\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Dragon Ball GT: Final Bout", 1997, "ps1", "PlayStation", listOf(rx("""\bfinal bout\b""")), preferConsoles = listOf("ps1")),
                SagaMilestoneDef("Dragon Ball Z: The Legacy of Goku", 2002, "gba", "Action RPG GBA", listOf(rx("""\blegacy of goku$""")), listOf(rx("""\b(ii|2|buu)\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Dragon Ball Z: The Legacy of Goku II", 2003, "gba", "Action RPG GBA", listOf(rx("""\blegacy of goku (ii|2)\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Dragon Ball: Advanced Adventure", 2004, "gba", "Acción GBA", listOf(rx("""\badvanced adventure\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Dragon Ball Z: Supersonic Warriors", 2004, "gba", "Combate Aéreo", listOf(rx("""\bsupersonic warriors\b""")), preferConsoles = listOf("gba", "nds")),
            ),
        ),
        SagaDefinition(
            id = "shining",
            name = "Shining Force",
            tagline = "Rol táctico y mazmorras legendarias nacidas en Sega Mega Drive por Camelot.",
            foundedYear = 1991,
            creator = "Sonic Software Planning (Camelot) / Hiroyuki Takahashi",
            accentHex = "#60A5FA",
            history = "Iniciada en 1991 con Shining in the Darkness, la franquicia Shining se consagró con Shining Force I y II en Mega Drive como referente del RPG táctico por turnos con combates animados.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Shining Force II (USA)", "Shining Force II (USA)"),
            includePatterns = listOf(rx("""\bshining (and|in|force|soul|the holy)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Shining in the Darkness", 1991, "genesis", "Mazmorra 16-bit", listOf(rx("""\bshining (in|and) the darkness\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Shining Force", 1992, "genesis", "SRPG Mega Drive", listOf(rx("""^shining force$"""), rx("""\bkamigami no isan\b""")), excludeConsoles = setOf("gba"), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Shining Force II", 1993, "genesis", "SRPG Mega Drive", listOf(rx("""\bshining force ii\b"""), rx("""\binishie no fuuin\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Shining Force CD", 1994, "segacd", "Sega CD", listOf(rx("""\bshining force cd\b""")), preferConsoles = listOf("segacd")),
                SagaMilestoneDef("Shining Force: Resurrection of the Dark Dragon", 2004, "gba", "Remake GBA", listOf(rx("""\bshining force\b"""), rx("""\bresurrection of the dark dragon\b""")), preferConsoles = listOf("gba")),
            ),
        ),
        SagaDefinition(
            id = "tmnt",
            name = "Tortugas Ninja (TMNT)",
            tagline = "Pizza, alcantarillas y beat 'em ups cooperativos míticos de Konami.",
            foundedYear = 1989,
            creator = "Konami / Mirage Studios",
            accentHex = "#34D399",
            history = "Entre 1989 y 1994 Konami firmó adaptaciones inolvidables de las Tortugas Ninja, culminando con Turtles in Time en SNES y The Hyperstone Heist en Mega Drive.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Teenage Mutant Ninja Turtles IV - Turtles in Time (USA)", "Teenage Mutant Ninja Turtles IV - Turtles in Time (USA)"),
            includePatterns = listOf(rx("""\bteenage mutant (?:ninja|hero) turtles\b"""), rx("""\btmnt\b"""), rx("""\bturtles in time\b"""), rx("""\bhyperstone heist\b""")),
            timeline = listOf(
                SagaMilestoneDef("Teenage Mutant Ninja Turtles", 1989, "nes", "Era 8-bit", listOf(rx("""^teenage mutant (?:ninja|hero) turtles$""")), preferConsoles = listOf("nes", "arcade")),
                SagaMilestoneDef("TMNT II: The Arcade Game", 1990, "nes", "Arcade / NES", listOf(rx("""\bturtles (ii|2)\b"""), rx("""\barcade game\b""")), preferConsoles = listOf("nes", "arcade")),
                SagaMilestoneDef("TMNT IV: Turtles in Time", 1991, "snes", "SNES 16-bit", listOf(rx("""\bturtles in time\b""")), preferConsoles = listOf("snes", "arcade")),
                SagaMilestoneDef("TMNT: The Hyperstone Heist", 1992, "genesis", "Mega Drive 16-bit", listOf(rx("""\bhyperstone heist\b"""), rx("""\breturn of the shredder\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("TMNT: Tournament Fighters", 1993, "snes", "Lucha 16-bit", listOf(rx("""\btournament fighters\b""")), preferConsoles = listOf("snes", "genesis", "nes")),
            ),
        ),
        SagaDefinition(
            id = "batman",
            name = "Batman Retro",
            tagline = "El Caballero Oscuro entre el plataformas de Sunsoft, Konami y el cómic animado de los 90.",
            foundedYear = 1989,
            creator = "Sunsoft / Konami / Sega",
            accentHex = "#A78BFA",
            history = "Batman brilló en 8 y 16 bits gracias a Sunsoft en NES/Mega Drive, Batman Returns de Konami y The Adventures of Batman & Robin.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Adventures of Batman & Robin, The (USA)", "Adventures of Batman & Robin, The (USA)"),
            includePatterns = listOf(rx("""\bbatman\b""")),
            excludePatterns = listOf(rx("""\bgotham\b""")),
            timeline = listOf(
                SagaMilestoneDef("Batman: The Video Game", 1989, "genesis", "Sunsoft Clásico", listOf(rx("""^batman$"""), rx("""\bbatman.*video game\b""")), preferConsoles = listOf("nes", "genesis")),
                SagaMilestoneDef("Batman: Return / Revenge of the Joker", 1991, "snes", "Sunsoft 16-bit", listOf(rx("""\b(return|revenge) of the joker\b""")), preferConsoles = listOf("genesis", "snes", "nes")),
                SagaMilestoneDef("Batman Returns", 1992, "snes", "Konami 16-bit", listOf(rx("""\bbatman returns\b""")), preferConsoles = listOf("snes", "genesis", "nes")),
                SagaMilestoneDef("The Adventures of Batman & Robin", 1994, "snes", "Serie Animada", listOf(rx("""\badventures of batman\b""")), preferConsoles = listOf("snes", "genesis")),
                SagaMilestoneDef("Batman Forever", 1995, "snes", "Era Acclaim", listOf(rx("""\bbatman forever\b""")), preferConsoles = listOf("snes", "genesis")),
            ),
        ),
        SagaDefinition(
            id = "spider-man",
            name = "Spider-Man & Venom",
            tagline = "Redes, simbiotes y combates urbanos de Nueva York de Sega a Neversoft.",
            foundedYear = 1991,
            creator = "Sega / Software Creations / Neversoft",
            accentHex = "#EF4444",
            history = "Desde Spider-Man vs. The Kingpin y los beat 'em ups junto a Venom (Maximum Carnage y Separation Anxiety) hasta el salto poligonal de Neversoft en PS1 y Dreamcast.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Spider-Man - Venom - Maximum Carnage (USA)", "Spider-Man - Venom - Maximum Carnage (USA)"),
            includePatterns = listOf(rx("""\bspider[\s-]*man\b"""), rx("""\bmaximum carnage\b"""), rx("""\bseparation anxiety\b""")),
            timeline = listOf(
                SagaMilestoneDef("Spider-Man vs. The Kingpin", 1991, "genesis", "Mega Drive", listOf(rx("""^spider[\s-]*man$"""), rx("""\bkingpin\b""")), preferConsoles = listOf("genesis", "segacd")),
                SagaMilestoneDef("Spider-Man / X-Men: Arcade's Revenge", 1992, "snes", "Crossover 16-bit", listOf(rx("""\barcade s? revenge\b""")), preferConsoles = listOf("snes", "genesis"), allowCombo = true),
                SagaMilestoneDef("Spider-Man & Venom: Maximum Carnage", 1994, "snes", "Simbiotes 16-bit", listOf(rx("""\bmaximum carnage\b""")), preferConsoles = listOf("snes", "genesis")),
                SagaMilestoneDef("Venom / Spider-Man: Separation Anxiety", 1995, "snes", "Simbiotes 16-bit", listOf(rx("""\bseparation anxiety\b""")), preferConsoles = listOf("snes", "genesis")),
                SagaMilestoneDef("Spider-Man (Neversoft)", 2000, "ps1", "Era 3D", listOf(rx("""\bspider[\s-]*man\b""")), listOf(rx("""\b(maximum|separation|arcade|kingpin|mysterio|lethal|2|enter)\b""")), excludeConsoles = setOf("snes", "genesis"), preferConsoles = listOf("ps1", "dc", "n64")),
            ),
        ),
        SagaDefinition(
            id = "x-men",
            name = "X-Men",
            tagline = "Poderes mutantes en equipo desde Mega Drive hasta las arenas de Capcom.",
            foundedYear = 1992,
            creator = "Konami / Sega / Capcom",
            accentHex = "#FACC15",
            history = "Los mutantes de Marvel destacaron en Mega Drive con X-Men y X-Men 2: Clone Wars, Capcom creó Mutant Apocalypse en SNES y su cruce frente a Street Fighter.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "X-Men 2 - Clone Wars (USA, Europe)", "X-Men 2 - Clone Wars (USA, Europe)"),
            includePatterns = listOf(rx("""\bx[\s-]*men\b"""), rx("""\bwolverine\b""")),
            timeline = listOf(
                SagaMilestoneDef("X-Men (Mega Drive)", 1993, "genesis", "16-bit", listOf(rx("""^x[\s-]*men$""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("X-Men: Mutant Apocalypse", 1994, "snes", "Capcom SNES", listOf(rx("""\bmutant apocalypse\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("X-Men 2: Clone Wars", 1995, "genesis", "16-bit", listOf(rx("""\bclone wars\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("X-Men vs. Street Fighter", 1996, "arcade", "Capcom CPS-2", listOf(rx("""\bx[\s-]*men vs street fighter\b""")), preferConsoles = listOf("arcade", "ps1")),
                SagaMilestoneDef("X-Men: Mutant Academy 2", 2001, "ps1", "PlayStation", listOf(rx("""\bmutant academy 2\b""")), preferConsoles = listOf("ps1")),
            ),
        ),
        SagaDefinition(
            id = "star-wars",
            name = "Star Wars",
            tagline = "Trilogía galáctica de Super Nintendo y aventuras Jedi en Game Boy Advance.",
            foundedYear = 1992,
            creator = "LucasArts / Sculptured Software / Factor 5",
            accentHex = "#FDE047",
            history = "Desde la trilogía Super Star Wars de SNES hasta las batallas de GBA centradas en las precuelas y la trilogía original, Star Wars ha sido sinónimo de ambición técnica retro.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Super Star Wars (USA)", "Super Star Wars (USA)"),
            includePatterns = listOf(rx("""\bstar wars\b""")),
            timeline = listOf(
                SagaMilestoneDef("Super Star Wars", 1992, "snes", "Trilogía SNES", listOf(rx("""^super star wars$""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Super Star Wars: The Empire Strikes Back", 1993, "snes", "Trilogía SNES", listOf(rx("""\bempire strikes back\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Super Star Wars: Return of the Jedi", 1994, "snes", "Trilogía SNES", listOf(rx("""\breturn of the jedi\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Star Wars: Episode II - Attack of the Clones", 2002, "gba", "GBA", listOf(rx("""\battack of the clones\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Star Wars: The New Droid Army", 2002, "gba", "GBA", listOf(rx("""\bnew droid army\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Star Wars Trilogy: Apprentice of the Force", 2004, "gba", "GBA", listOf(rx("""\bapprentice of the force\b""")), preferConsoles = listOf("gba")),
                SagaMilestoneDef("Star Wars: Episode III - Revenge of the Sith", 2005, "gba", "GBA", listOf(rx("""\brevenge of the sith\b""")), preferConsoles = listOf("gba", "ps2")),
            ),
        ),
        SagaDefinition(
            id = "disney-illusion",
            name = "Disney: Illusion & Magical Quest",
            tagline = "Las joyas plataformeras de Sega y Capcom con Mickey, Minnie y Donald.",
            foundedYear = 1990,
            creator = "Sega / Tokuro Fujiwara (Capcom)",
            accentHex = "#38BDF8",
            history = "La década de los 90 representó la edad de oro de Disney: Sega brilló con Castle of Illusion y QuackShot mientras Capcom deslumbraba en SNES con Magical Quest y Aladdin.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Castle of Illusion Starring Mickey Mouse (USA, Europe)", "Castle of Illusion Starring Mickey Mouse (USA, Europe)"),
            includePatterns = listOf(rx("""\b(castle of illusion|world of illusion|quackshot|magical quest|great circus mystery|aladdin|lion king|donald in maui)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Castle of Illusion Starring Mickey Mouse", 1990, "genesis", "Illusion", listOf(rx("""\bcastle of illusion\b"""), rx("""\bfushigi no oshiro\b""")), listOf(rx("""\bquackshot\b""")), preferConsoles = listOf("genesis", "sms")),
                SagaMilestoneDef("QuackShot Starring Donald Duck", 1991, "genesis", "Illusion", listOf(rx("""^quackshot\b""")), listOf(rx("""\bcastle of illusion\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("World of Illusion (Mickey & Donald)", 1992, "genesis", "Illusion", listOf(rx("""\bworld of illusion\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("The Magical Quest Starring Mickey Mouse", 1992, "snes", "Magical Quest", listOf(rx("""\bmagical quest\b""")), preferConsoles = listOf("snes", "gba")),
                SagaMilestoneDef("Disney's Aladdin", 1993, "genesis", "Clásicos 16-bit", listOf(rx("""^aladdin$"""), rx("""\bdisney s aladdin\b""")), preferConsoles = listOf("genesis", "snes", "gba")),
                SagaMilestoneDef("The Great Circus Mystery (Magical Quest 2)", 1994, "snes", "Magical Quest", listOf(rx("""\bgreat circus mystery\b"""), rx("""\bmagical adventure 2\b""")), preferConsoles = listOf("snes", "genesis")),
                SagaMilestoneDef("The Lion King", 1994, "snes", "Clásicos 16-bit", listOf(rx("""\blion king\b""")), preferConsoles = listOf("snes", "genesis")),
                SagaMilestoneDef("Donald in Maui Mallard (Cold Shadow)", 1995, "genesis", "16-bit Tardío", listOf(rx("""\bmaui mallard\b""")), preferConsoles = listOf("snes", "genesis")),
            ),
        ),
        SagaDefinition(
            id = "fatal-fury",
            name = "Fatal Fury & Art of Fighting",
            tagline = "Las raíces de South Town con Terry Bogard, Ryo Sakazaki y los combates de SNK.",
            foundedYear = 1991,
            creator = "SNK / Takashi Nishiyama",
            accentHex = "#FBBF24",
            history = "Antes de unirse en The King of Fighters, Fatal Fury y Art of Fighting construyeron South Town en Neo Geo con planos dobles de combate y zoom dinámico hasta culminar en Garou.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Fatal Fury Special (USA)", "Fatal Fury Special (USA)"),
            includePatterns = listOf(rx("""\b(fatal fury|garou densetsu|art of fighting|ryuuko no ken)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Fatal Fury: King of Fighters", 1991, "genesis", "South Town", listOf(rx("""^fatal fury$"""), rx("""\bgarou densetsu shukumei\b""")), preferConsoles = listOf("arcade", "genesis", "snes")),
                SagaMilestoneDef("Art of Fighting", 1992, "snes", "South Town", listOf(rx("""^art of fighting$""")), preferConsoles = listOf("arcade", "snes", "genesis")),
                SagaMilestoneDef("Fatal Fury 2", 1992, "genesis", "South Town", listOf(rx("""\bfatal fury 2\b"""), rx("""\bgarou densetsu 2\b""")), preferConsoles = listOf("arcade", "genesis", "snes")),
                SagaMilestoneDef("Fatal Fury Special", 1993, "snes", "Era SNK", listOf(rx("""\bfatal fury special\b""")), preferConsoles = listOf("arcade", "snes", "segacd")),
                SagaMilestoneDef("Art of Fighting 2", 1994, "arcade", "Era SNK", listOf(rx("""\bart of fighting 2\b""")), preferConsoles = listOf("arcade", "snes")),
                SagaMilestoneDef("Garou: Mark of the Wolves", 1999, "dc", "Obra Cumbre", listOf(rx("""\bmark of the wolves\b""")), preferConsoles = listOf("arcade", "dc", "ps2")),
            ),
        ),
        SagaDefinition(
            id = "thunder-force",
            name = "Thunder Force",
            tagline = "Shmups espaciales vertiginosos con rock metálico de Technosoft en Mega Drive.",
            foundedYear = 1988,
            creator = "Technosoft / Naosuke Arai",
            accentHex = "#38BDF8",
            history = "Considerada una de las mejores sagas de disparos horizontales de los 16 bits por su cambio de velocidad dinámico, arsenal variado, parallax multicapa y rock sintetizado.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Thunder Force IV (Europe)", "Thunder Force IV (Europe)"),
            includePatterns = listOf(rx("""\b(thunder force|lightening force)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Thunder Force II", 1988, "genesis", "Mega Drive", listOf(rx("""\bthunder force ii\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Thunder Force III", 1990, "genesis", "Mega Drive", listOf(rx("""\bthunder force iii\b"""), rx("""\bthunder spirits\b""")), preferConsoles = listOf("genesis", "snes")),
                SagaMilestoneDef("Thunder Force IV (Lightening Force)", 1992, "genesis", "Obra Maestra", listOf(rx("""\bthunder force iv\b"""), rx("""\blightening force\b""")), preferConsoles = listOf("genesis", "saturn")),
                SagaMilestoneDef("Thunder Force V", 1997, "ps1", "32-bit", listOf(rx("""\bthunder force v\b""")), preferConsoles = listOf("saturn", "ps1")),
            ),
        ),
        SagaDefinition(
            id = "wonder-boy",
            name = "Wonder Boy & Monster World",
            tagline = "Aventura, tiendas, equipamiento RPG y plataformas inolvidables de Westone.",
            foundedYear = 1986,
            creator = "Westone Bit Entertainment / Ryuichi Nishizawa",
            accentHex = "#34D399",
            history = "Westone fusionó las plataformas arcade de Wonder Boy con tiendas de equipo y exploración no lineal en Monster World, coronando en Mega Drive con Monster World III y IV.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Wonder Boy in Monster World (USA, Europe)", "Wonder Boy in Monster World (USA, Europe)"),
            includePatterns = listOf(rx("""\b(wonder boy|monster world|dragon s trap|monster lair)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Wonder Boy / III: Monster Lair", 1988, "genesis", "Arcade / MD", listOf(rx("""\bmonster lair\b"""), rx("""^wonder boy$""")), preferConsoles = listOf("genesis", "pce")),
                SagaMilestoneDef("Wonder Boy III: The Dragon's Trap", 1989, "sms", "Master System", listOf(rx("""\bdragon s? trap\b""")), preferConsoles = listOf("sms", "gg")),
                SagaMilestoneDef("Wonder Boy V: Monster World III", 1991, "genesis", "Mega Drive RPG", listOf(rx("""\bwonder boy in monster world\b"""), rx("""\bwonder boy v\b""")), preferConsoles = listOf("genesis", "sms")),
                SagaMilestoneDef("Monster World IV", 1994, "genesis", "Obra Cumbre MD", listOf(rx("""\bmonster world iv\b""")), preferConsoles = listOf("genesis")),
            ),
        ),
        SagaDefinition(
            id = "ecco",
            name = "Ecco the Dolphin",
            tagline = "Sonares, océanos profundos y ciencia ficción submarina en Sega.",
            foundedYear = 1992,
            creator = "Novotrade / Ed Annunziata",
            accentHex = "#38BDF8",
            history = "Ecco the Dolphin destacó en Mega Drive y Sega CD combinando físicas fluidas de natación, sonar ecológico y una profunda historia de ciencia ficción marina.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Ecco - The Tides of Time (USA)", "Ecco the Dolphin (USA, Europe)"),
            includePatterns = listOf(rx("""\becco\b""")),
            timeline = listOf(
                SagaMilestoneDef("Ecco the Dolphin", 1992, "genesis", "Mega Drive", listOf(rx("""^ecco the dolphin$""")), preferConsoles = listOf("genesis", "segacd")),
                SagaMilestoneDef("Ecco: The Tides of Time", 1994, "genesis", "Mega Drive", listOf(rx("""\btides of time\b"""), rx("""\becco.*ii\b""")), preferConsoles = listOf("genesis", "segacd")),
                SagaMilestoneDef("Ecco Jr.", 1995, "genesis", "Spin-off", listOf(rx("""\becco jr\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Ecco: Defender of the Future", 2000, "dc", "Dreamcast / PS2", listOf(rx("""\bdefender of the future\b""")), preferConsoles = listOf("dc", "ps2")),
            ),
        ),
        SagaDefinition(
            id = "earthworm-jim",
            name = "Earthworm Jim",
            tagline = "Humor absurdo de dibujos animados, animación fluida y acción surrealista de los 90.",
            foundedYear = 1994,
            creator = "Shiny Entertainment / David Perry & Doug TenNapel",
            accentHex = "#FBBF24",
            history = "Shiny Entertainment llevó la fluidez de la animación tradicional hecha a mano a SNES y Mega Drive con un sentido del humor irreverente inolvidable.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Earthworm Jim (USA)", "Earthworm Jim (USA)"),
            includePatterns = listOf(rx("""\bearthworm jim\b""")),
            timeline = listOf(
                SagaMilestoneDef("Earthworm Jim", 1994, "genesis", "16-bit Shiny", listOf(rx("""^earthworm jim$""")), preferConsoles = listOf("genesis", "snes", "segacd")),
                SagaMilestoneDef("Earthworm Jim 2", 1995, "snes", "16-bit / PS1", listOf(rx("""\bearthworm jim 2\b""")), preferConsoles = listOf("snes", "genesis", "ps1")),
                SagaMilestoneDef("Earthworm Jim 3D", 1999, "n64", "Era 64-bit", listOf(rx("""\bearthworm jim 3d\b""")), preferConsoles = listOf("n64")),
            ),
        ),
        SagaDefinition(
            id = "strike-series",
            name = "Strike Series (Desert / Jungle / Urban)",
            tagline = "Combate táctico isométrico en helicóptero de Electronic Arts.",
            foundedYear = 1992,
            creator = "Electronic Arts / Mike Posehn",
            accentHex = "#A3E635",
            history = "La trilogía Strike de 16 bits fusionó acción en helicóptero en vista isométrica con gestión real de combustible, blindaje y misiones no lineales abiertas.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Desert Strike - Return to the Gulf (USA, Europe)", "Desert Strike - Return to the Gulf (USA, Europe)"),
            includePatterns = listOf(rx("""\b(?:desert|jungle|urban|soviet|nuclear) strike\b""")),
            timeline = listOf(
                SagaMilestoneDef("Desert Strike: Return to the Gulf", 1992, "genesis", "Trilogía 16-bit", listOf(rx("""\bdesert strike\b""")), preferConsoles = listOf("genesis", "snes", "gba")),
                SagaMilestoneDef("Jungle Strike", 1993, "genesis", "Trilogía 16-bit", listOf(rx("""\bjungle strike\b""")), preferConsoles = listOf("genesis", "snes")),
                SagaMilestoneDef("Urban Strike", 1994, "genesis", "Trilogía 16-bit", listOf(rx("""\burban strike\b""")), preferConsoles = listOf("genesis", "snes")),
                SagaMilestoneDef("Soviet Strike", 1996, "ps1", "32-bit", listOf(rx("""\bsoviet strike\b""")), preferConsoles = listOf("ps1", "saturn")),
                SagaMilestoneDef("Nuclear Strike", 1997, "ps1", "32-bit", listOf(rx("""\bnuclear strike\b""")), preferConsoles = listOf("ps1", "n64")),
            ),
        ),
        SagaDefinition(
            id = "sega-super-scaler",
            name = "Sega Super Scalers (Arcade)",
            tagline = "Velocidad pseudo-3D en Ferrari, motos y cazas de combate diseñada por Yu Suzuki.",
            foundedYear = 1985,
            creator = "Sega AM2 / Yu Suzuki",
            accentHex = "#F43F5E",
            history = "Yu Suzuki y Sega AM2 sorprendieron al mundo con OutRun, After Burner II, Space Harrier y Super Hang-On a toda velocidad en cabinas arcade y Sega Mega Drive.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "OutRun (USA, Europe)", "OutRun (USA, Europe)"),
            includePatterns = listOf(rx("""\b(?:outrun|out run|turbo outrun|outrun 2019|after burner|space harrier|super hang[\s-]*on)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Space Harrier II", 1988, "genesis", "Mega Drive", listOf(rx("""\bspace harrier ii\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Super Hang-On", 1989, "genesis", "Motos AM2", listOf(rx("""\bsuper hang[\s-]*on\b""")), preferConsoles = listOf("genesis", "arcade")),
                SagaMilestoneDef("After Burner II", 1990, "genesis", "Combate Aéreo", listOf(rx("""\bafter burner ii\b""")), preferConsoles = listOf("genesis", "arcade")),
                SagaMilestoneDef("OutRun", 1991, "genesis", "Ferrari Testarossa", listOf(rx("""^outrun$"""), rx("""^out run$""")), preferConsoles = listOf("genesis", "arcade")),
                SagaMilestoneDef("Turbo OutRun", 1992, "genesis", "Arcade", listOf(rx("""\bturbo outrun\b""")), preferConsoles = listOf("genesis", "arcade")),
                SagaMilestoneDef("OutRun 2019", 1993, "genesis", "Mega Drive", listOf(rx("""\boutrun 2019\b""")), preferConsoles = listOf("genesis")),
            ),
        ),
        SagaDefinition(
            id = "road-rash",
            name = "Road Rash",
            tagline = "Carreras ilegales en motocicleta, cadenas, tráfico y asfalto en Mega Drive.",
            foundedYear = 1991,
            creator = "Electronic Arts / Randy Breen & Dan Geisler",
            accentHex = "#FB923C",
            history = "Road Rash mezcló velocidad por carreteras abiertas llenas de tráfico y colinas con combate cuerpo a cuerpo sobre dos ruedas en Mega Drive.",
            heroArt = SagaHeroArt("Sega - Mega Drive - Genesis", "Road Rash (USA, Europe)", "Road Rash (USA, Europe)"),
            includePatterns = listOf(rx("""\broad rash\b""")),
            timeline = listOf(
                SagaMilestoneDef("Road Rash", 1991, "genesis", "Mega Drive", listOf(rx("""^road rash$""")), preferConsoles = listOf("genesis", "sms")),
                SagaMilestoneDef("Road Rash II", 1992, "genesis", "Mega Drive", listOf(rx("""\broad rash (ii|2)\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Road Rash 3", 1995, "genesis", "Mega Drive", listOf(rx("""\broad rash (3|iii)\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Road Rash 64 / 3D", 1998, "n64", "Era 3D", listOf(rx("""\broad rash (3d|64)\b""")), preferConsoles = listOf("n64", "ps1")),
            ),
        ),
        SagaDefinition(
            id = "bomberman",
            name = "Bomberman",
            tagline = "Laberintos explosivos y multijugador legendario de Hudson Soft.",
            foundedYear = 1985,
            creator = "Hudson Soft / Shinichi Nakamoto",
            accentHex = "#F472B6",
            history = "Desde Super Bomberman en SNES y Mega Bomberman en Mega Drive, pocas sagas han logrado ser sinónimo tan inmediato de partidas locales multijugador.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Super Bomberman (USA)", "Super Bomberman (USA)"),
            includePatterns = listOf(rx("""\bbomberman\b""")),
            timeline = listOf(
                SagaMilestoneDef("Super Bomberman", 1993, "snes", "SNES 16-bit", listOf(rx("""^super bomberman$""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Mega Bomberman", 1994, "genesis", "Mega Drive 16-bit", listOf(rx("""\bmega bomberman\b""")), preferConsoles = listOf("genesis")),
                SagaMilestoneDef("Super Bomberman 2", 1994, "snes", "SNES 16-bit", listOf(rx("""\bsuper bomberman 2\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Super Bomberman 3", 1995, "snes", "SNES 16-bit", listOf(rx("""\bsuper bomberman 3\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Bomberman 64", 1997, "n64", "Era 64-bit", listOf(rx("""\bbomberman 64\b""")), preferConsoles = listOf("n64")),
            ),
        ),
        SagaDefinition(
            id = "seiken-densetsu",
            name = "Saga Mana (Seiken Densetsu)",
            tagline = "Action RPG coloridos, música mágica y cooperativo desde Secret of Mana.",
            foundedYear = 1991,
            creator = "Square / Koichi Ishii & Hiroki Kikuta",
            accentHex = "#34D399",
            history = "Nacida como spin-off de Final Fantasy y elevada a obra cumbre con Secret of Mana y Trials of Mana, la saga Mana redefinió los Action RPG cooperativos.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Secret of Mana (USA)", "Secret of Mana (USA)"),
            includePatterns = listOf(rx("""\b(?:secret of mana|seiken densetsu|trials of mana|legend of mana|sword of mana|final fantasy adventure)\b""")),
            timeline = listOf(
                SagaMilestoneDef("Secret of Mana", 1993, "snes", "SNES Cooperativo", listOf(rx("""\bsecret of mana\b"""), rx("""\bseiken densetsu 2\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Trials of Mana (Seiken Densetsu 3)", 1995, "snes", "Obra Cumbre", listOf(rx("""\bseiken densetsu 3\b"""), rx("""\btrials of mana\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Legend of Mana", 1999, "ps1", "PlayStation", listOf(rx("""\blegend of mana\b""")), preferConsoles = listOf("ps1")),
                SagaMilestoneDef("Sword of Mana", 2003, "gba", "Remake GBA", listOf(rx("""\bsword of mana\b""")), preferConsoles = listOf("gba")),
            ),
        ),
        SagaDefinition(
            id = "lufia",
            name = "Saga Lufia (Estpolis)",
            tagline = "Calabozos con ingeniosos acertijos, monstruos cápsula y RPG inolvidables de 16 bits.",
            foundedYear = 1993,
            creator = "Neverland / Taito",
            accentHex = "#A78BFA",
            history = "Lufia & The Fortress of Doom y Lufia II: Rise of the Sinistrals en Super Nintendo cautivaron con acertijos estilo Zelda y la Ancient Cave de 99 pisos.",
            heroArt = SagaHeroArt("Nintendo - Super Nintendo Entertainment System", "Lufia II - Rise of the Sinistrals (USA)", "Lufia II - Rise of the Sinistrals (USA)"),
            includePatterns = listOf(rx("""\blufia\b"""), rx("""\bestpolis\b""")),
            timeline = listOf(
                SagaMilestoneDef("Lufia & The Fortress of Doom", 1993, "snes", "16-bit", listOf(rx("""\bfortress of doom\b""")), preferConsoles = listOf("snes", "genesis")),
                SagaMilestoneDef("Lufia II: Rise of the Sinistrals", 1995, "snes", "Obra Cumbre SNES", listOf(rx("""\blufia ii\b"""), rx("""\brise of the sinistrals\b""")), preferConsoles = listOf("snes")),
                SagaMilestoneDef("Lufia: The Legend Returns", 2001, "gbc", "Portátil", listOf(rx("""\blegend returns\b""")), preferConsoles = listOf("gbc")),
                SagaMilestoneDef("Lufia: The Ruins of Lore", 2002, "gba", "GBA", listOf(rx("""\bruins of lore\b""")), preferConsoles = listOf("gba")),
            ),
        ),
    )

    internal fun canonicalEraFor(
        sagaId: String,
        rawEra: String,
        consoleKey: String,
        year: Int?,
    ): String {
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

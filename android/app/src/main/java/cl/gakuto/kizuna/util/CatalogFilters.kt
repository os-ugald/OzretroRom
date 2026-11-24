package cl.gakuto.kizuna.util

import cl.gakuto.kizuna.data.GameEntity

enum class RegionMode(val label: String) {
    PREFERRED("Región preferida (sin duplicados)"),
    WORLD("World"),
    USA("USA"),
    EUROPE("Europe"),
    JAPAN("Japan"),
    ASIA("Asia"),
    OTHER("Otras"),
    ALL("Todas las regiones y revisiones"),
}

enum class PlatformFamily(val label: String, val systemIds: Set<String>) {
    NINTENDO(
        "Nintendo",
        setOf("nes", "fds", "snes", "n64", "n64dd", "gb", "gbc", "gba", "nds", "dsi", "3ds", "gamecube", "wii", "pokemon-mini", "virtual-boy"),
    ),
    SONY("Sony", setOf("ps1", "ps2", "psp")),
    SEGA("Sega", setOf("master-system", "genesis", "game-gear", "sega-32x", "sega-cd", "saturn", "dreamcast")),
    SNK("SNK", setOf("ngp", "ngpc", "arcade-fbneo")),
    NEC("NEC", setOf("pc-engine", "pc-engine-cd", "pc-fx")),
    ATARI("Atari", setOf("atari-2600", "atari-5200", "atari-7800", "atari-jaguar", "atari-lynx")),
    ARCADE("Arcade", setOf("arcade-fbneo")),
    OTHER("Otras", emptySet()),
}

enum class OriginStatusFilter(val label: String) {
    ROMGI_AVAILABLE("Disponible mediante ROMgi"),
    LOCAL_LIBRARY("Biblioteca local"),
    FAVORITES("Favoritos"),
    RECENTLY_PLAYED("Jugados recientemente"),
    WITH_ARTWORK("Con artwork"),
    WITHOUT_ARTWORK("Sin artwork"),
    DOWNLOADED("Descargados"),
    NOT_DOWNLOADED("No descargados"),
}

enum class CatalogSort(val label: String) {
    NAME_ASC("Nombre A-Z"),
    NAME_DESC("Nombre Z-A"),
    YEAR_DESC("Año"),
    LAST_PLAYED("Últimos jugados"),
    ADDED_RECENTLY("Añadidos recientemente"),
    FAVORITES_FIRST("Favoritos primero"),
}

data class CatalogFilterState(
    val regions: Set<RegionMode> = setOf(RegionMode.PREFERRED),
    val platformFamilies: Set<PlatformFamily> = emptySet(),
    val systemIds: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val status: Set<OriginStatusFilter> = emptySet(),
    val sort: CatalogSort = CatalogSort.NAME_ASC,
) {
    fun normalized(): CatalogFilterState {
        val cleanedRegions = when {
            RegionMode.ALL in regions -> setOf(RegionMode.ALL)
            regions.isEmpty() -> setOf(RegionMode.PREFERRED)
            RegionMode.PREFERRED in regions && regions.size > 1 -> regions - RegionMode.PREFERRED
            else -> regions
        }
        val cleanedStatus = status.toMutableSet().apply {
            if (OriginStatusFilter.WITH_ARTWORK in this && OriginStatusFilter.WITHOUT_ARTWORK in this) {
                remove(OriginStatusFilter.WITH_ARTWORK)
                remove(OriginStatusFilter.WITHOUT_ARTWORK)
            }
            if (OriginStatusFilter.DOWNLOADED in this && OriginStatusFilter.NOT_DOWNLOADED in this) {
                remove(OriginStatusFilter.DOWNLOADED)
                remove(OriginStatusFilter.NOT_DOWNLOADED)
            }
        }
        return copy(regions = cleanedRegions, status = cleanedStatus)
    }

    fun activeLabels(): List<String> = buildList {
        val state = normalized()
        if (state.regions != setOf(RegionMode.PREFERRED)) addAll(state.regions.map { it.label })
        addAll(state.platformFamilies.map { it.label })
        addAll(state.genres.map(CatalogFilters::displayGenre))
        addAll(state.status.map { it.label })
        if (state.sort != CatalogSort.NAME_ASC) add(state.sort.label)
    }
}

data class GenreChoice(val key: String?, val label: String)

/** Shared catalog filtering for the main library and the global catalog. */
object CatalogFilters {
    private val metadataParenthesis = Regex("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]")
    private val revisionTokens = Regex(
        "\\b(rev|revision|version|ver|set|program)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val advancedTokens = Regex(
        "\\b(prototype|proto|beta|alpha|demo|sample|bootleg|hack)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val genreLabels = linkedMapOf(
        "action" to "Acción",
        "adventure" to "Aventura",
        "platform" to "Plataformas",
        "rpg" to "RPG",
        "jrpg" to "JRPG",
        "fighting" to "Pelea",
        "beat-em-up" to "Beat 'em up",
        "run-and-gun" to "Run and gun",
        "shoot-em-up" to "Shoot 'em up",
        "racing" to "Carreras",
        "sports" to "Deportes",
        "strategy" to "Estrategia",
        "puzzle" to "Puzzle",
        "simulation" to "Simulación",
        "survival-horror" to "Survival horror",
        "rhythm" to "Ritmo y música",
        "party" to "Party",
    )

    fun canonicalTitle(title: String): String {
        val stripped = title.replace(metadataParenthesis, " ")
            .replace(Regex("\\s+-\\s+(USA|Europe|Japan|World|Asia).*$", RegexOption.IGNORE_CASE), "")
        return TextNormalizer.normalize(stripped).ifBlank { TextNormalizer.normalize(title) }
    }

    /** Parent title shown by normal catalog views; discs and dump metadata stay in the picker. */
    fun displayTitle(title: String): String = title
        .replace(metadataParenthesis, " ")
        .replace(Regex("\\s+-\\s+(USA|Europe|Japan|World|Asia).*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { title.trim() }

    fun apply(
        games: List<GameEntity>,
        systemId: String? = null,
        genre: String? = null,
        regionMode: RegionMode = RegionMode.PREFERRED,
    ): List<GameEntity> {
        var filtered = games.asSequence()
        if (!systemId.isNullOrBlank()) filtered = filtered.filter { it.systemId == systemId }
        if (!genre.isNullOrBlank()) filtered = filtered.filter { genre in genreKeys(it.genre) }

        val regionFiltered = when (regionMode) {
            RegionMode.PREFERRED, RegionMode.ALL -> filtered.toList()
            else -> filtered.filter { regionMatches(it, regionMode) }.toList()
        }
        if (regionMode == RegionMode.ALL) return regionFiltered.sortedWith(gameComparator)

        return regionFiltered
            .groupBy { it.canonicalTitle.ifBlank { canonicalTitle(it.title) } }
            .values
            .mapNotNull { variants ->
                variants.minWithOrNull(
                    compareBy<GameEntity> { regionRank(it, regionMode) }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.region.lowercase() }
                        .thenBy { it.id },
                )
            }
            .sortedWith(gameComparator)
    }

    fun genres(games: List<GameEntity>): List<GenreChoice> {
        val keys = games.asSequence().flatMap { genreKeys(it.genre).asSequence() }.toSet()
        return buildList {
            add(GenreChoice(null, "Todos los géneros"))
            genreLabels.forEach { (key, label) -> if (key in keys) add(GenreChoice(key, label)) }
            keys.asSequence()
                .filter { it.startsWith(RAW_PREFIX) }
                .map { it to it.removePrefix(RAW_PREFIX) }
                .sortedBy { it.second }
                .forEach { (key, label) -> add(GenreChoice(key, label)) }
            if (UNKNOWN_KEY in keys) add(GenreChoice(UNKNOWN_KEY, "Sin clasificar"))
        }
    }

    fun allGenreChoices(): List<GenreChoice> = buildList {
        add(GenreChoice(null, "Todos los géneros"))
        genreLabels.forEach { (key, label) -> add(GenreChoice(key, label)) }
        add(GenreChoice(UNKNOWN_KEY, "Sin clasificar"))
    }

    fun requestedGenreChoices(): List<GenreChoice> = genreLabels.map { GenreChoice(it.key, it.value) } +
        GenreChoice(UNKNOWN_KEY, "Sin clasificar")

    fun displayGenre(value: String): String {
        if (value in genreLabels) return genreLabels.getValue(value)
        if (value == UNKNOWN_KEY) return "Sin clasificar"
        if (value.startsWith(RAW_PREFIX)) return value.removePrefix(RAW_PREFIX)
        return value
    }

    fun genreKeys(value: String): Set<String> {
        val source = value.trim()
        if (source.isBlank() || source.equals("Sin clasificar", true)) return setOf(UNKNOWN_KEY)
        val normalized = TextNormalizer.normalize(source)
        val keys = linkedSetOf<String>()

        val isBeatEmUp = listOf("beat em up", "beat'em up", "beat 'em up", "scrolling fighter", "brawler")
            .any { normalized.contains(TextNormalizer.normalize(it)) }
        if (isBeatEmUp) keys += "beat-em-up"
        if (!isBeatEmUp && listOf("fighting", "versus fighter", "fight").any(normalized::contains)) keys += "fighting"
        if (listOf("jrpg", "japanese role playing").any { normalized.contains(TextNormalizer.normalize(it)) }) keys += "jrpg"
        if (listOf("role playing", "role-playing", "rpg").any { normalized.contains(TextNormalizer.normalize(it)) }) keys += "rpg"
        if (listOf("platform", "platformer").any(normalized::contains)) keys += "platform"
        if ("adventure" in normalized) keys += "adventure"
        if ("action" in normalized) keys += "action"
        if (listOf("run and gun", "run'n gun").any { normalized.contains(TextNormalizer.normalize(it)) }) keys += "run-and-gun"
        if (listOf("shoot em up", "shoot'em up", "shmup").any { normalized.contains(TextNormalizer.normalize(it)) }) keys += "shoot-em-up"
        if (listOf("shooter", "gun").any(normalized::contains) && "run-and-gun" !in keys && "shoot-em-up" !in keys) keys += "shoot-em-up"
        if (listOf("racing", "driving", "race").any(normalized::contains)) keys += "racing"
        if ("sports" in normalized || "sport" in normalized) keys += "sports"
        if ("puzzle" in normalized) keys += "puzzle"
        if (listOf("strategy", "tactical").any(normalized::contains)) keys += "strategy"
        if (listOf("simulation", "simulator", "life sim").any(normalized::contains)) keys += "simulation"
        if ("survival horror" in normalized || ("horror" in normalized && "survival" in normalized)) keys += "survival-horror"
        if (listOf("rhythm", "music").any(normalized::contains)) keys += "rhythm"
        if ("party" in normalized) keys += "party"

        if (keys.isEmpty()) keys += "$RAW_PREFIX$source"
        return keys
    }

    fun regionMatches(game: GameEntity, mode: RegionMode): Boolean {
        // Never infer region from ordinary title words. A title such as "Super Mario World"
        // is not a World-region dump unless the indexed region metadata says so.
        val region = game.region.lowercase()
        return when (mode) {
            RegionMode.WORLD -> "world" in region || "mundial" in region || "international" in region
            RegionMode.USA -> listOf("usa", "united states", "north america").any(region::contains)
            RegionMode.EUROPE -> listOf("europe", "euro", "europa", "pal").any(region::contains)
            RegionMode.JAPAN -> listOf("japan", "japanese", "nihon").any(region::contains)
            RegionMode.ASIA -> listOf("asia", "korea", "taiwan", "hong kong", "china").any(region::contains)
            RegionMode.OTHER -> !listOf(
                "world", "international", "usa", "united states", "europe", "euro", "japan", "asia",
                "korea", "taiwan", "hong kong", "china",
            ).any(region::contains)
            RegionMode.PREFERRED, RegionMode.ALL -> true
        }
    }

    private fun regionRank(game: GameEntity, requested: RegionMode): Int {
        if (requested != RegionMode.PREFERRED && regionMatches(game, requested)) return 0
        val region = game.region.lowercase()
        val metadata = game.title.lowercase()
        var rank = when {
            "usa" in region || "united states" in region -> 0
            "japan" in region -> 1
            "world" in region || "international" in region -> 2
            "asia" in region -> 3
            "europe" in region || "euro" in region -> 4
            else -> 5
        }
        if (revisionTokens.containsMatchIn(metadata)) rank += 10
        if (advancedTokens.containsMatchIn(metadata)) rank += 20
        return rank
    }

    private val gameComparator = compareBy<GameEntity> { it.title.lowercase() }
        .thenBy { it.region.lowercase() }
        .thenBy { it.id }

    private const val RAW_PREFIX = "raw:"
    private const val UNKNOWN_KEY = "unknown"
}

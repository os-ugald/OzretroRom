package cl.gakuto.kizuna.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import cl.gakuto.kizuna.util.CatalogFilterState
import cl.gakuto.kizuna.util.CatalogSort
import cl.gakuto.kizuna.util.OriginStatusFilter
import cl.gakuto.kizuna.util.PlatformFamily
import cl.gakuto.kizuna.util.RegionMode

data class GameListSpec(
    val collectionId: String? = null,
    val systemId: String? = null,
    val normalizedSearch: String = "",
    val genreKey: String? = null,
    val regionMode: RegionMode = RegionMode.PREFERRED,
    val systemIds: Set<String> = emptySet(),
    val platformFamilies: Set<PlatformFamily> = emptySet(),
    val genreKeys: Set<String> = emptySet(),
    val regionModes: Set<RegionMode> = emptySet(),
    val statusFilters: Set<OriginStatusFilter> = emptySet(),
    val libraryOrigins: Set<LibraryOrigin> = emptySet(),
    val sort: CatalogSort = CatalogSort.NAME_ASC,
) {
    val effectiveSystems: Set<String>
        get() = buildSet {
            systemId?.takeIf(String::isNotBlank)?.let(::add)
            addAll(systemIds)
            platformFamilies.filter { it != PlatformFamily.OTHER }.forEach { addAll(it.systemIds) }
        }

    val effectiveGenres: Set<String>
        get() = buildSet {
            genreKey?.takeIf(String::isNotBlank)?.let(::add)
            addAll(genreKeys.filter(String::isNotBlank))
        }

    val effectiveRegions: Set<RegionMode>
        get() = CatalogFilterState(
            regions = regionModes.ifEmpty { setOf(regionMode) },
        ).normalized().regions

    val includesAllRegions: Boolean
        get() = RegionMode.ALL in effectiveRegions
}

data class GamePage(
    val items: List<GameEntity>,
    val totalMatches: Int,
    val nextOffset: Int,
    val exhausted: Boolean,
)

/**
 * Creates bounded Room queries for large catalogues. Every filter is executed by SQLite over the
 * complete database; the UI only receives the requested page.
 */
object CatalogPagingSql {
    fun page(spec: GameListSpec, limit: Int, offset: Int): SupportSQLiteQuery {
        val query = build(spec)
        if (!spec.includesAllRegions) {
            // SQLite returns the non-aggregate columns from the row that produced the single
            // MIN(), allowing one preferred regional variant per canonical game without loading
            // every duplicate into Kotlin. This keeps the user's selected global sort intact.
            val metadata = "LOWER(g.title)"
            val discRank = "CASE WHEN $metadata LIKE '%(disc 1%' OR $metadata LIKE '%[disc 1%' " +
                "OR $metadata LIKE '%(disk 1%' OR $metadata LIKE '%[disk 1%' THEN 0 " +
                "WHEN $metadata LIKE '%(disc %' OR $metadata LIKE '%[disc %' " +
                "OR $metadata LIKE '%(disk %' OR $metadata LIKE '%[disk %' THEN 2 ELSE 1 END"
            val artworkRank = "CASE WHEN COALESCE(g.thumbnailRepo,'')!='' THEN 0 ELSE 1 END"
            val choiceRank = "(${regionRankExpression("g")} * 100 + ($discRank) * 10 + ($artworkRank) * 2 + " +
                "CASE WHEN g.source='curated' THEN 0 ELSE 1 END)"
            query.sql.insert("SELECT g.*".length, ", MIN($choiceRank) AS oz_choice_rank")
            query.sql.append(" GROUP BY g.systemId, ${canonicalExpression("g")}")
        }
        query.sql.append(orderExpression(spec))
        query.sql.append(" LIMIT ? OFFSET ?")
        query.args += limit
        query.args += offset
        return SimpleSQLiteQuery(query.sql.toString(), query.args.toTypedArray())
    }

    fun count(spec: GameListSpec): SupportSQLiteQuery {
        val query = build(spec)
        val countExpression = if (spec.includesAllRegions) {
            "COUNT(*)"
        } else {
            "COUNT(DISTINCT g.systemId || '|' || ${canonicalExpression("g")})"
        }
        val sql = StringBuilder("SELECT $countExpression ")
            .append(query.sql.toString().substringAfter("SELECT g.* "))
        return SimpleSQLiteQuery(sql.toString(), query.args.toTypedArray())
    }

    private fun build(spec: GameListSpec): QueryParts {
        val sql = StringBuilder("SELECT g.* FROM games g")
        val args = mutableListOf<Any?>()
        val cid = spec.collectionId
        val isStandard = cid.isNullOrBlank() || cid in setOf("all-games", DOWNLOADED_COLLECTION_ID, RECENT_COLLECTION_ID)
        val isSystem = cid != null && CollectionVisibilityPolicy.isSystemCollection(cid)

        if (!isStandard && !isSystem) {
            sql.append(" INNER JOIN collection_memberships m ON m.gameId=g.id AND m.collectionId=?")
            args += cid!!
        }
        sql.append(" WHERE 1=1")
        if (isSystem) {
            sql.append(" AND g.systemId=?")
            args += CollectionVisibilityPolicy.systemIdFromCollectionId(cid!!)
        }
        if (cid == DOWNLOADED_COLLECTION_ID) {
            sql.append(" AND COALESCE(g.contentUri,'')!=''")
        }
        if (spec.collectionId == RECENT_COLLECTION_ID) {
            sql.append(" AND g.lastPlayedAt IS NOT NULL")
        }
        appendSystems(sql, args, spec)
        appendSearch(sql, args, spec.normalizedSearch)
        appendGenres(sql, args, spec.effectiveGenres)
        appendRegions(sql, args, spec.effectiveRegions)
        appendStatus(sql, args, spec.statusFilters)
        appendLibraryOrigins(sql, args, spec.libraryOrigins)
        return QueryParts(sql, args)
    }

    private fun appendLibraryOrigins(sql: StringBuilder, args: MutableList<Any?>, origins: Set<LibraryOrigin>) {
        if (origins.isEmpty()) return
        sql.append(" AND g.libraryOrigin IN (${origins.joinToString(",") { "?" }})")
        args.addAll(origins.map { it.name }.sorted())
    }

    private fun appendSystems(sql: StringBuilder, args: MutableList<Any?>, spec: GameListSpec) {
        val explicit = spec.effectiveSystems
        val other = PlatformFamily.OTHER in spec.platformFamilies
        if (explicit.isEmpty() && !other) return
        val allKnown = PlatformFamily.entries
            .filter { it != PlatformFamily.OTHER }
            .flatMapTo(linkedSetOf()) { it.systemIds }
        sql.append(" AND (")
        var wrote = false
        if (explicit.isNotEmpty()) {
            sql.append("g.systemId IN (${explicit.joinToString(",") { "?" }})")
            args.addAll(explicit.sorted())
            wrote = true
        }
        if (other) {
            if (wrote) sql.append(" OR ")
            sql.append("g.systemId NOT IN (${allKnown.joinToString(",") { "?" }})")
            args.addAll(allKnown.sorted())
        }
        sql.append(')')
    }

    private fun appendSearch(sql: StringBuilder, args: MutableList<Any?>, normalizedSearch: String) {
        if (normalizedSearch.isBlank()) return
        val pattern = "%${escapeLike(normalizedSearch.lowercase())}%"
        sql.append(
            " AND (g.normalizedTitle LIKE ? ESCAPE '\\'" +
                " OR g.canonicalTitle LIKE ? ESCAPE '\\'" +
                " OR LOWER(COALESCE(g.romSet,'')) LIKE ? ESCAPE '\\'" +
                " OR LOWER(g.platform) LIKE ? ESCAPE '\\'" +
                " OR LOWER(g.systemId) LIKE ? ESCAPE '\\'" +
                " OR LOWER(g.genre) LIKE ? ESCAPE '\\')",
        )
        repeat(6) { args += pattern }
    }

    private fun appendGenres(sql: StringBuilder, args: MutableList<Any?>, keys: Set<String>) {
        if (keys.isEmpty()) return
        sql.append(" AND (")
        keys.sorted().forEachIndexed { index, key ->
            if (index > 0) sql.append(" OR ")
            appendSingleGenre(sql, args, key)
        }
        sql.append(')')
    }

    private fun appendSingleGenre(sql: StringBuilder, args: MutableList<Any?>, key: String) {
        if (key == "unknown") {
            sql.append("(TRIM(g.genre)='' OR LOWER(g.genre)='sin clasificar')")
            return
        }
        if (key.startsWith("raw:")) {
            sql.append("LOWER(g.genre)=LOWER(?)")
            args += key.removePrefix("raw:")
            return
        }
        val terms = when (key) {
            "beat-em-up" -> listOf("beat em up", "beat'em up", "beat 'em up", "scrolling fighter", "brawler")
            "fighting" -> listOf("fighting", "versus fighter", " fight")
            "rpg" -> listOf("role playing", "role-playing", "rpg")
            "jrpg" -> listOf("jrpg", "japanese role playing")
            "platform" -> listOf("platform", "platformer")
            "adventure" -> listOf("adventure")
            "action" -> listOf("action")
            "run-and-gun" -> listOf("run and gun", "run'n gun")
            "shoot-em-up" -> listOf("shoot em up", "shoot'em up", "shmup", "shooter")
            "racing" -> listOf("racing", "driving", "race")
            "sports" -> listOf("sports", "sport")
            "puzzle" -> listOf("puzzle")
            "strategy" -> listOf("strategy", "tactical")
            "simulation" -> listOf("simulation", "simulator", "life sim")
            "survival-horror" -> listOf("survival horror")
            "rhythm" -> listOf("rhythm", "music")
            "party" -> listOf("party")
            else -> emptyList()
        }
        if (terms.isEmpty()) {
            sql.append("1=1")
            return
        }
        sql.append('(')
        terms.forEachIndexed { index, term ->
            if (index > 0) sql.append(" OR ")
            sql.append("LOWER(g.genre) LIKE ?")
            args += "%${term.lowercase()}%"
        }
        sql.append(')')
        if (key == "fighting") {
            sql.append(" AND LOWER(g.genre) NOT LIKE '%beat%em%up%' AND LOWER(g.genre) NOT LIKE '%brawler%'")
        }
    }

    private fun appendRegions(sql: StringBuilder, args: MutableList<Any?>, modes: Set<RegionMode>) {
        if (modes.isEmpty() || RegionMode.PREFERRED in modes || RegionMode.ALL in modes) return
        sql.append(" AND (")
        modes.sortedBy { it.ordinal }.forEachIndexed { index, mode ->
            if (index > 0) sql.append(" OR ")
            appendSingleRegion(sql, args, mode)
        }
        sql.append(')')
    }

    private fun appendSingleRegion(sql: StringBuilder, args: MutableList<Any?>, mode: RegionMode) {
        // Region filters use the normalized region column, never arbitrary words in the title
        // (for example, "Super Mario World" is not evidence of a World-region dump).
        val value = "LOWER(TRIM(COALESCE(g.region,'')))"
        val patterns = when (mode) {
            RegionMode.WORLD -> listOf("%world%", "%mundial%", "%international%")
            RegionMode.USA -> listOf("%usa%", "%united states%", "%north america%", "%america%")
            RegionMode.EUROPE -> listOf("%europe%", "%euro%", "%europa%", "%pal%")
            RegionMode.JAPAN -> listOf("%japan%", "%japanese%", "%nihon%")
            RegionMode.ASIA -> listOf("%asia%", "%korea%", "%taiwan%", "%hong kong%", "%china%")
            else -> emptyList()
        }
        if (mode == RegionMode.OTHER) {
            val excluded = listOf(
                "%world%", "%international%", "%usa%", "%united states%", "%europe%", "%euro%",
                "%japan%", "%asia%", "%korea%", "%taiwan%", "%hong kong%", "%china%",
            )
            sql.append("NOT (")
            excluded.forEachIndexed { index, pattern ->
                if (index > 0) sql.append(" OR ")
                sql.append("$value LIKE ?")
                args += pattern
            }
            sql.append(')')
            return
        }
        sql.append('(')
        patterns.forEachIndexed { index, pattern ->
            if (index > 0) sql.append(" OR ")
            sql.append("$value LIKE ?")
            args += pattern
        }
        sql.append(')')
    }

    private fun appendStatus(
        sql: StringBuilder,
        args: MutableList<Any?>,
        filters: Set<OriginStatusFilter>,
    ) {
        filters.forEach { filter ->
            when (filter) {
                OriginStatusFilter.ROMGI_AVAILABLE -> sql.append(" AND g.romgiAvailable=1")
                OriginStatusFilter.LOCAL_LIBRARY -> sql.append(" AND (g.source='local' OR COALESCE(g.contentUri,'')!='')")
                OriginStatusFilter.FAVORITES -> sql.append(
                    " AND EXISTS(SELECT 1 FROM collection_memberships fav WHERE fav.gameId=g.id AND fav.collectionId='favorites')",
                )
                OriginStatusFilter.RECENTLY_PLAYED -> sql.append(" AND g.lastPlayedAt IS NOT NULL")
                OriginStatusFilter.WITH_ARTWORK -> sql.append(" AND COALESCE(g.thumbnailRepo,'')!=''")
                OriginStatusFilter.WITHOUT_ARTWORK -> sql.append(" AND COALESCE(g.thumbnailRepo,'')=''")
                OriginStatusFilter.DOWNLOADED -> sql.append(" AND COALESCE(g.contentUri,'')!=''")
                OriginStatusFilter.NOT_DOWNLOADED -> sql.append(" AND COALESCE(g.contentUri,'')=''")
            }
        }
    }

    private fun orderExpression(spec: GameListSpec): String = when (spec.sort) {
        CatalogSort.NAME_ASC -> " ORDER BY LOWER(g.title), LOWER(g.region), g.id"
        CatalogSort.NAME_DESC -> " ORDER BY LOWER(g.title) DESC, LOWER(g.region), g.id"
        CatalogSort.YEAR_DESC -> " ORDER BY CASE WHEN g.year IS NULL THEN 1 ELSE 0 END, g.year DESC, LOWER(g.title), g.id"
        CatalogSort.LAST_PLAYED -> " ORDER BY CASE WHEN g.lastPlayedAt IS NULL THEN 1 ELSE 0 END, g.lastPlayedAt DESC, LOWER(g.title), g.id"
        CatalogSort.ADDED_RECENTLY -> " ORDER BY g.addedAt DESC, LOWER(g.title), g.id"
        CatalogSort.FAVORITES_FIRST -> " ORDER BY CASE WHEN EXISTS(SELECT 1 FROM collection_memberships fav WHERE fav.gameId=g.id AND fav.collectionId='favorites') THEN 0 ELSE 1 END, LOWER(g.title), g.id"
    }

    private fun canonicalExpression(alias: String): String =
        "COALESCE(NULLIF($alias.canonicalTitle,''), $alias.normalizedTitle)"

    private fun regionRankExpression(alias: String): String {
        val region = "LOWER(TRIM(COALESCE($alias.region,'')))"
        val metadata = "LOWER($alias.title)"
        return "(CASE " +
            "WHEN $region LIKE '%usa%' OR $region LIKE '%united states%' THEN 0 " +
            "WHEN $region LIKE '%japan%' THEN 1 " +
            "WHEN $region LIKE '%world%' OR $region LIKE '%international%' THEN 2 " +
            "WHEN $region LIKE '%asia%' THEN 3 " +
            "WHEN $region LIKE '%europe%' OR $region LIKE '%euro%' THEN 4 ELSE 5 END " +
            "+ CASE WHEN $metadata LIKE '%(rev%' OR $metadata LIKE '%[rev%' OR $metadata LIKE '%(version%' " +
            "THEN 10 ELSE 0 END " +
            "+ CASE WHEN $metadata LIKE '%(prototype%' OR $metadata LIKE '%(proto%' OR $metadata LIKE '%(beta%' " +
            "OR $metadata LIKE '%(alpha%' OR $metadata LIKE '%(demo%' OR $metadata LIKE '%(sample%' THEN 20 ELSE 0 END " +
            "+ CASE WHEN $metadata LIKE '%(bootleg%' OR $metadata LIKE '%(hack%' THEN 20 ELSE 0 END)"
    }

    private fun escapeLike(value: String): String = value.replace("%", "\\%").replace("_", "\\_")

    private data class QueryParts(val sql: StringBuilder, val args: MutableList<Any?>)

    const val DOWNLOADED_COLLECTION_ID = "downloaded-library"
    const val RECENT_COLLECTION_ID = "recently-played"
}

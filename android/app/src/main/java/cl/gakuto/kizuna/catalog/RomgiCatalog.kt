package cl.gakuto.kizuna.catalog

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import cl.gakuto.kizuna.data.GameDescriptor
import cl.gakuto.kizuna.data.RomRegionPreference
import cl.gakuto.kizuna.util.CatalogFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

/**
 * Read-only client for ROMgi's public catalog format.
 *
 * Gakuto Kizuna never accepts the tiny iiSU placeholder as playable content. It resolves the
 * selected title against ROMgi's catalog and, for arcade, requires an exact set filename such as
 * dino.zip or captcomm.zip before the emulator is allowed to start.
 */
class RomgiCatalog(
    private val context: Context,
    private val allowTorrents: Boolean = true,
    private val preferredRegion: String = RomRegionPreference.DEFAULT.key,
    private val internetArchiveAuthenticated: Boolean = false,
) {
    private val catalogDir = File(context.filesDir, "romgi-catalog").apply { mkdirs() }
    private val databaseFile = File(catalogDir, DATABASE_FILE)
    private val gzipFile = File(catalogDir, "$DATABASE_FILE.gz.part")
    private val databasePart = File(catalogDir, "$DATABASE_FILE.part")

    suspend fun ensureReady(onProgress: suspend (CatalogProgress) -> Unit) = withContext(Dispatchers.IO) {
        if (isValidDatabase(databaseFile)) return@withContext
        var lastFailure: Throwable? = null
        DATABASE_SOURCES.forEachIndexed { index, source ->
            runCatching { databaseFile.delete() }
            runCatching { databasePart.delete() }
            runCatching { gzipFile.delete() }
            try {
                downloadCatalog(source, onProgress)
                source.sha256?.let { expected ->
                    require(sha256(gzipFile).equals(expected, ignoreCase = true)) {
                        "El respaldo ROMgi no coincide con su huella oficial esperada."
                    }
                }
                onProgress(CatalogProgress("Descomprimiendo catálogo…"))
                GZIPInputStream(BufferedInputStream(gzipFile.inputStream(), BUFFER_SIZE)).use { input ->
                    BufferedOutputStream(FileOutputStream(databasePart), BUFFER_SIZE).use { output ->
                        input.copyTo(output, BUFFER_SIZE)
                    }
                }
                require(isValidDatabase(databasePart)) { "El catálogo descargado no contiene una base ROMgi válida." }
                if (!databasePart.renameTo(databaseFile)) {
                    databasePart.copyTo(databaseFile, overwrite = true)
                    databasePart.delete()
                }
                gzipFile.delete()
                onProgress(CatalogProgress(if (index == 0) "Catálogo listo" else "Catálogo listo · respaldo verificado"))
                return@withContext
            } catch (error: Throwable) {
                lastFailure = error
                if (index < DATABASE_SOURCES.lastIndex) {
                    onProgress(CatalogProgress("La base ROMgi actual no superó la validación · probando respaldo oficial…"))
                }
            }
        }
        throw lastFailure ?: IllegalStateException("No fue posible preparar el catálogo ROMgi.")
    }

    suspend fun resolve(descriptor: GameDescriptor): ResolvedSource {
        val resolution = resolveOptions(descriptor)
        if (resolution.ambiguous) {
            val names = resolution.allOptions.take(4).joinToString { "${it.entryTitle} [${it.matchedRegion}]" }
            error("ROMgi encontró varias coincidencias ambiguas: $names. Selecciona una antes de descargar.")
        }
        return resolution.selected
    }

    suspend fun resolveOptions(descriptor: GameDescriptor): RomgiResolution = withContext(Dispatchers.IO) {
        require(isValidDatabase(databaseFile)) { "El catálogo aún no está preparado." }
        val db = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val aliases = platformAliases(descriptor.platform)
            val expectedArcadeName = descriptor.romSet
                ?.takeIf { descriptor.platform == "arcade" }
                ?.let { "${it.lowercase(Locale.ROOT)}.zip" }

            val entries = linkedMapOf<String, CatalogEntry>()
            if (expectedArcadeName != null) {
                findEntriesByExactLink(db, aliases, expectedArcadeName).forEach { entries[it.slug] = it }
            }
            findEntriesByIdentity(db, aliases, descriptor).forEach { entries[it.slug] = it }

            require(entries.isNotEmpty()) {
                if (expectedArcadeName != null) {
                    "ROMgi no encontró el set exacto $expectedArcadeName para ${descriptor.title}."
                } else {
                    "ROMgi no encontró ${descriptor.title} en ${descriptor.platformName}."
                }
            }

            val scored = entries.values.flatMap { entry ->
                val titleMatch = if (expectedArcadeName != null) {
                    RomTitleMatch(
                        strategy = RomMatchStrategy.ARCADE_EXACT_SET,
                        titleScore = 200_000,
                        similarity = 1.0,
                        matchedRegion = RomgiTitleMatcher.detectRegion(entry.title),
                        regionRank = 0,
                    )
                } else {
                    RomgiTitleMatcher.match(
                        requestedTitle = descriptor.title,
                        requestedRegion = descriptor.region,
                        candidateTitle = entry.title,
                        preferredRegion = preferredRegion,
                        requestedAliases = RomgiTitleAliasPolicy.aliasesFor(descriptor),
                        systemId = descriptor.platformShort,
                    )
                } ?: return@flatMap emptyList()

                readLinks(db, entry.slug).mapNotNull { link ->
                    val concreteRegion = sourceRegion(entry, link)
                    scoreLink(descriptor, entry, link, expectedArcadeName, concreteRegion)?.let { sourceScore ->
                        ScoredSource(
                            score = titleMatch.titleScore + sourceScore,
                            titleMatch = titleMatch,
                            entry = entry,
                            link = link,
                            sourceRegion = concreteRegion,
                            sourceRegionRank = RomgiRegionPolicy.rank(
                                systemId = descriptor.platformShort,
                                originalRegion = descriptor.region,
                                preferredRegion = preferredRegion,
                                candidateRegion = concreteRegion,
                            ),
                        )
                    }
                }
            }.sortedWith(
                compareByDescending<ScoredSource> { it.score }
                    .thenBy { it.titleMatch.strategy.tier }
                    .thenBy { it.sourceRegionRank }
                    .thenByDescending { it.link.size },
            )

            require(scored.isNotEmpty()) {
                if (expectedArcadeName != null) {
                    "El catálogo no ofrece una fuente automática exacta para $expectedArcadeName. No abriré FBNeo con un ROM set dudoso."
                } else if (!allowTorrents) {
                    "No existe una fuente HTTPS compatible para ${descriptor.title}. Activa torrents o usa tu biblioteca local."
                } else {
                    "No existe una fuente automática compatible para ${descriptor.title}."
                }
            }

            // Region is a ranking preference, never a destructive filter.
            // Disc 1/2/3 and revisions remain separate because the concrete filename
            // is part of the option identity.
            val eligible = scored
            val distinct = eligible
                .groupBy {
                    val fileIdentity = playableFileName(descriptor, it.link).lowercase(Locale.ROOT)
                    "${it.entry.slug}|${it.sourceRegion}|$fileIdentity"
                }
                .mapNotNull { (_, values) -> values.maxByOrNull { it.score } }
                .sortedWith(
                    compareBy<ScoredSource> { it.sourceRegionRank }
                        .thenByDescending { it.score }
                        .thenBy { playableFileName(descriptor, it.link).lowercase(Locale.ROOT) },
                )
                .take(MAX_RESOLUTION_OPTIONS)

            val options = distinct.map { winner -> winner.toResolvedSource(db, descriptor) }
            val selected = options.first()
            val secondScore = distinct.getOrNull(1)?.score
            val firstScore = distinct.first().score
            val sameTier = distinct.getOrNull(1)?.titleMatch?.strategy?.tier == distinct.first().titleMatch.strategy.tier
            val closeScore = secondScore != null && firstScore - secondScore <= AMBIGUITY_SCORE_GAP
            val differentIdentity = options.getOrNull(1)?.let { second ->
                RomgiTitleMatcher.canonical(second.entryTitle) != RomgiTitleMatcher.canonical(selected.entryTitle) ||
                    second.matchedRegion != selected.matchedRegion
            } == true
            val ambiguous = expectedArcadeName == null && (
                options.size > 1 || (sameTier && closeScore && differentIdentity)
            )

            RomgiResolution(
                selected = selected,
                alternatives = options.drop(1),
                ambiguous = ambiguous,
            )
        } finally {
            db.close()
        }
    }

    /**
     * Resolves the complete release hierarchy before applying any option limit. ROMgi's official
     * entry groups are authoritative when present; older cached databases use the same disc-token
     * semantics locally, while keeping region/language/revision in the edition identity.
     */
    suspend fun resolveEditions(descriptor: GameDescriptor): RomgiEditionResolution = withContext(Dispatchers.IO) {
        require(isValidDatabase(databaseFile)) { "El catálogo aún no está preparado." }
        val db = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val aliases = platformAliases(descriptor.platform)
            val expectedArcadeName = descriptor.romSet
                ?.takeIf { descriptor.platform == "arcade" }
                ?.let { "${it.lowercase(Locale.ROOT)}.zip" }
            val entries = linkedMapOf<String, CatalogEntry>()
            if (expectedArcadeName != null) {
                findEntriesByExactLink(db, aliases, expectedArcadeName).forEach { entries[it.slug] = it }
            }
            findEntriesByIdentity(db, aliases, descriptor).forEach { entries[it.slug] = it }
            require(entries.isNotEmpty()) {
                if (expectedArcadeName != null) "ROMgi no encontró el set exacto $expectedArcadeName para ${descriptor.title}."
                else "ROMgi no encontró ${descriptor.title} en ${descriptor.platformName}."
            }

            val memberships = readGroupMemberships(db, entries.keys)
            val candidates = entries.values.flatMap { entry ->
                val titleMatch = if (expectedArcadeName != null) {
                    RomTitleMatch(
                        strategy = RomMatchStrategy.ARCADE_EXACT_SET,
                        titleScore = 200_000,
                        similarity = 1.0,
                        matchedRegion = RomgiTitleMatcher.detectRegion(entry.title),
                        regionRank = 0,
                    )
                } else {
                    RomgiTitleMatcher.match(
                        requestedTitle = descriptor.title,
                        requestedRegion = descriptor.region,
                        candidateTitle = entry.title,
                        preferredRegion = preferredRegion,
                        requestedAliases = RomgiTitleAliasPolicy.aliasesFor(descriptor),
                        systemId = descriptor.platformShort,
                    )
                } ?: return@flatMap emptyList()

                readLinks(db, entry.slug).mapNotNull { link ->
                    val concreteRegion = sourceRegion(entry, link)
                    val sourceScore = scoreLink(descriptor, entry, link, expectedArcadeName, concreteRegion,
                        enforcePreparationPolicy = false)
                        ?: return@mapNotNull null
                    val scored = ScoredSource(
                        score = titleMatch.titleScore + sourceScore,
                        titleMatch = titleMatch,
                        entry = entry,
                        link = link,
                        sourceRegion = concreteRegion,
                        sourceRegionRank = RomgiRegionPolicy.rank(
                            systemId = descriptor.platformShort,
                            originalRegion = descriptor.region,
                            preferredRegion = preferredRegion,
                            candidateRegion = concreteRegion,
                        ),
                    )
                    val membership = memberships[entry.slug]
                    val parsed = RomgiEditionParser.parse(
                        title = entry.title,
                        forcedDiscIndex = membership?.memberIndex,
                        forcedDiscLabel = membership?.memberLabel,
                    )
                    EditionCandidate(
                        scored = scored,
                        resolved = scored.toResolvedSource(db, descriptor),
                        parsed = parsed,
                        membership = membership,
                    )
                }
            }
            require(candidates.isNotEmpty()) {
                if (!allowTorrents) "No existe una fuente HTTPS compatible para ${descriptor.title}. Activa torrents o usa tu biblioteca local."
                else "No existe una fuente automática compatible para ${descriptor.title}."
            }

            buildEditionResolution(candidates)
        } finally {
            db.close()
        }
    }

    private fun ScoredSource.toResolvedSource(db: SQLiteDatabase, descriptor: GameDescriptor): ResolvedSource {
        val infohash = link.torrentInfohash?.lowercase(Locale.ROOT)
        val magnet = when {
            link.url.startsWith("magnet:", ignoreCase = true) -> link.url
            infohash != null -> readMagnet(db, infohash, entry.title)
            else -> null
        }
        val matchedRegion = sourceRegion
        val regionFallback = RomgiRegionPolicy.requiresConfirmation(
            systemId = descriptor.platformShort,
            originalRegion = descriptor.region,
            preferredRegion = preferredRegion,
            candidateRegion = matchedRegion,
        )
        val directHttps = isDirectHttps(link)
        val useTorrent = !directHttps && !link.torrentInfohash.isNullOrBlank() && link.torrentFileIndex != null
        val confidence = when (titleMatch.strategy) {
            RomMatchStrategy.EXACT_TITLE_AND_REGION, RomMatchStrategy.ARCADE_EXACT_SET -> 100
            RomMatchStrategy.EXACT_TITLE -> 98
            RomMatchStrategy.CANONICAL_TITLE -> 95
            RomMatchStrategy.ALIAS -> 91
            RomMatchStrategy.PUNCTUATION_TOLERANT -> 88
            RomMatchStrategy.REORDERED_TITLE -> 84
            RomMatchStrategy.REGIONAL_FALLBACK -> 80
            RomMatchStrategy.FUZZY_LAST_RESORT -> (titleMatch.similarity * 100).toInt().coerceIn(70, 89)
        }
        return ResolvedSource(
            entrySlug = entry.slug,
            entryTitle = entry.title,
            platform = entry.platform,
            fileName = playableFileName(descriptor, link),
            format = link.format,
            url = link.url,
            host = link.host,
            size = link.size,
            sourceId = link.sourceId,
            torrentInfohash = infohash,
            torrentFileIndex = link.torrentFileIndex,
            torrentFilePath = link.torrentFilePath,
            magnetUri = magnet,
            useTorrent = useTorrent,
            matchStrategy = if (regionFallback && titleMatch.strategy.tier < RomMatchStrategy.REGIONAL_FALLBACK.tier) {
                RomMatchStrategy.REGIONAL_FALLBACK
            } else titleMatch.strategy,
            matchedRegion = matchedRegion,
            requestedRegion = descriptor.region,
            preferredRegion = RomgiRegionPolicy.effectivePreference(preferredRegion),
            confidence = if (regionFallback) confidence.coerceAtMost(80) else confidence,
            requiresAuth = link.requiresAuth,
            regionFallback = regionFallback,
            boxartUrl = entry.boxartUrl,
        )
    }

    private fun buildEditionResolution(candidates: List<EditionCandidate>): RomgiEditionResolution {
        val editions = candidates.groupBy { candidate ->
            val membership = candidate.membership
            if (membership != null) {
                "official:${membership.groupId}:${candidate.scored.sourceRegion}"
            } else {
                "fallback:${candidate.scored.entry.platform}:${candidate.parsed.editionKey}:${candidate.scored.sourceRegion}"
            }
        }
        val plans = editions.flatMap { (editionKey, editionCandidates) ->
            val expectedDiscs = editionCandidates.mapNotNull { it.membership?.memberCount }
                .maxOrNull()
                ?.coerceAtLeast(editionCandidates.maxOf { it.parsed.discIndex })
                ?: editionCandidates.maxOf { it.parsed.discIndex }
            val expectedIndexes = (1..expectedDiscs).toList()
            val membership = editionCandidates.firstNotNullOfOrNull { it.membership }
            val editionTitle = membership?.groupTitle?.takeIf(String::isNotBlank)
                ?: editionCandidates.minBy { it.parsed.discIndex }.parsed.editionTitle
            val parsedEdition = RomgiEditionParser.parse(editionTitle)

            // Oz currently verifies ZIP preparation but has no current 7z extractor. Prefer any
            // playable direct/ZIP alternative in the same ROMgi edition instead of choosing a 7z
            // route that will inevitably fail after download.
            editionCandidates
                .filterNot { containerExtension(it.resolved) == "7z" }
                .filter { it.scored.entry.platform != "ps1" ||
                    Ps1DownloadFormatPolicy.canPrepare(it.resolved.fileName, it.resolved.format) }
                .groupBy { formatFamily(it.resolved) }
                .mapNotNull { (format, formatCandidates) ->
                    // ROMgi separates the logical payload format (ISO/RVZ/CHD/...) from the
                    // transport container (ZIP/7z). Never "rescue" an incomplete multi-disc edition
                    // by silently reducing it to Disc 1: the exact disc set is part of the release.
                    val optical = formatCandidates.any { it.scored.entry.platform in OPTICAL_PLATFORMS }
                    val selected = formatCandidates.groupBy { it.parsed.discIndex }
                        .mapValues { (_, values) -> values.maxBy { it.scored.score } }

                    if (selected.keys.sorted() != expectedIndexes) return@mapNotNull null

                    val effectiveIsMultiDisc = expectedIndexes.size > 1
                    if (optical && format in UNSUPPORTED_MULTI_FILE_DISC_FORMATS) return@mapNotNull null
                    if (effectiveIsMultiDisc && format !in MULTI_DISC_FORMATS) return@mapNotNull null
                    if (effectiveIsMultiDisc && formatCandidates.none { it.scored.entry.platform in M3U_PLATFORMS }) return@mapNotNull null

                    val discs = expectedIndexes.map { index ->
                        val candidate = selected.getValue(index)
                        RomgiDiscSource(index, candidate.parsed.discLabel, candidate.resolved)
                    }
                    val releaseKind = formatCandidates.map { it.parsed.releaseKind }
                        .firstOrNull { it.advanced } ?: RomgiReleaseKind.RETAIL
                    val sourceScore = formatCandidates.maxOf { it.scored.score }
                    val regionRank = formatCandidates.minOf { it.scored.sourceRegionRank }
                    RomgiDownloadPlan(
                        id = ("$editionKey|$format").hashCode().toUInt().toString(16),
                        parentTitle = parsedEdition.parentTitle,
                        editionTitle = editionTitle,
                        region = formatCandidates.minBy { it.scored.sourceRegionRank }.scored.sourceRegion,
                        format = format.uppercase(Locale.ROOT),
                        releaseKind = releaseKind,
                        discs = discs,
                        artworkUrl = formatCandidates.asSequence()
                            .sortedBy { it.parsed.discIndex }
                            .mapNotNull { it.resolved.boxartUrl?.takeIf(String::isNotBlank) }
                            .firstOrNull(),
                        groupId = membership?.groupId,
                        score = sourceScore - regionRank * 10_000 - formatRank(format) * 1_000,
                    )
            }
        }.filter { it.isComplete }
            .distinctBy { plan -> plan.discs.joinToString("|") { it.source.fileName.lowercase(Locale.ROOT) } }
            .sortedWith(
                compareByDescending<RomgiDownloadPlan> { it.score }
                    .thenBy { it.editionTitle.lowercase(Locale.ROOT) }
                    .thenBy { formatRank(it.format.lowercase(Locale.ROOT)) },
            )
            .take(MAX_EDITION_PLANS)

        require(plans.isNotEmpty()) {
            "ROMgi encontró la edición, pero no ofrece un conjunto completo de discos en un formato reproducible."
        }
        val standard = plans.filterNot { it.releaseKind.advanced }
        val advanced = plans.filter { it.releaseKind.advanced }
        return RomgiEditionResolution(
            recommended = standard.firstOrNull() ?: advanced.first(),
            standard = standard,
            advanced = advanced,
        )
    }

    private fun formatFamily(source: ResolvedSource): String {
        val extension = containerExtension(source)
        val logical = source.format.trim().lowercase(Locale.ROOT)
        return when (extension) {
            "zip", "7z" -> logical.takeIf { it in LOGICAL_DISC_FORMATS } ?: extension
            "chd", "cue", "iso", "cso", "pbp", "rvz", "gcm", "wbfs", "gdi", "cdi" -> extension
            else -> logical.ifBlank { extension.ifBlank { "rom" } }
        }
    }

    private fun containerExtension(source: ResolvedSource): String =
        source.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun formatRank(format: String): Int = when (format.lowercase(Locale.ROOT)) {
        "rvz" -> 0
        "chd" -> 1
        "iso" -> 2
        "gcm", "wbfs" -> 3
        "cso", "pbp" -> 4
        "cue", "gdi", "cdi" -> 5
        else -> 10
    }

    private fun optionHaystack(value: ScoredSource): String = listOf(
        value.entry.title,
        value.link.name,
        value.link.filename,
        value.link.torrentFilePath.orEmpty(),
        value.link.url,
    ).joinToString(" ")

    private suspend fun downloadCatalog(source: CatalogDatabaseSource, onProgress: suspend (CatalogProgress) -> Unit) {
        onProgress(CatalogProgress("Descargando catálogo ROMgi${if (source.fallback) " · respaldo" else ""}…"))
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "OzRetroRom/0.6.0-beta.4")
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            connection.connect()
            require(connection.responseCode in 200..299) {
                "No se pudo descargar el catálogo: HTTP ${connection.responseCode}."
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            var lastUpdate = 0L
            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                BufferedOutputStream(FileOutputStream(gzipFile), BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        val now = System.nanoTime()
                        if (now - lastUpdate > 150_000_000L) {
                            lastUpdate = now
                            onProgress(CatalogProgress("Descargando catálogo ROMgi…", downloaded, total))
                        }
                    }
                }
            }
            require(downloaded > 1_000_000L) { "El catálogo recibido está incompleto." }
            onProgress(CatalogProgress("Catálogo descargado", downloaded, downloaded))
        } finally {
            connection.disconnect()
        }
    }

    private fun isValidDatabase(file: File): Boolean {
        if (!file.isFile || file.length() < 1_000_000L) return false
        return runCatching {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                db.rawQuery("SELECT COUNT(*) FROM entries", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getLong(0) > 100L
                } && db.rawQuery("SELECT COUNT(*) FROM links", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getLong(0) > 100L
                }
            } finally {
                db.close()
            }
        }.getOrDefault(false)
    }

    private fun findEntriesByIdentity(
        db: SQLiteDatabase,
        platforms: List<String>,
        descriptor: GameDescriptor,
    ): List<CatalogEntry> {
        if (platforms.isEmpty()) return emptyList()
        val placeholders = platforms.joinToString(",") { "?" }
        val rawTitle = descriptor.title.trim().lowercase(Locale.ROOT)
        val displayBase = catalogDisplayTitle(descriptor.title).lowercase(Locale.ROOT)
        val titleAliases = RomgiTitleAliasPolicy.aliasesFor(descriptor)
            .map(::catalogDisplayTitle)
            .map { it.lowercase(Locale.ROOT) }
            .filter(String::isNotBlank)
            .distinct()
        val predicates = mutableListOf<String>()
        val args = ArrayList<String>().apply { addAll(platforms) }

        descriptor.romSet?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)?.let { romSet ->
            predicates += "lower(COALESCE(rom_id, '')) = ?"
            args += romSet
            predicates += "lower(slug) = ?"
            args += romSet
        }
        predicates += "lower(title) = ?"
        args += rawTitle
        if (displayBase.isNotBlank() && displayBase != rawTitle) {
            predicates += "lower(title) = ?"
            args += displayBase
            predicates += "lower(title) LIKE ?"
            args += "$displayBase (%"
            predicates += "lower(title) LIKE ?"
            args += "$displayBase [%"
        }
        predicates += "lower(title) LIKE ?"
        args += "%${displayBase.ifBlank { rawTitle }}%"
        titleAliases.forEach { alias ->
            predicates += "lower(title) = ?"
            args += alias
            predicates += "lower(title) LIKE ?"
            args += "$alias (%"
            predicates += "lower(title) LIKE ?"
            args += "$alias [%"
        }
        RomgiTitleMatcher.queryTokens(descriptor.title).forEach { token ->
            predicates += "lower(title) LIKE ?"
            args += "%$token%"
        }

        val sql = """
            SELECT slug, rom_id, title, platform, boxart_url
            FROM entries
            WHERE platform IN ($placeholders)
              AND (${predicates.joinToString(" OR ")})
            LIMIT 500
        """.trimIndent()
        return db.rawQuery(sql, args.toTypedArray()).use(::readEntries)
    }

    private fun findEntriesByExactLink(
        db: SQLiteDatabase,
        platforms: List<String>,
        expectedFileName: String,
    ): List<CatalogEntry> {
        if (platforms.isEmpty()) return emptyList()
        val placeholders = platforms.joinToString(",") { "?" }
        val args = ArrayList<String>().apply {
            addAll(platforms)
            add(expectedFileName.lowercase(Locale.ROOT))
            add("%/${expectedFileName.lowercase(Locale.ROOT)}")
            add("%\\${expectedFileName.lowercase(Locale.ROOT)}")
        }.toTypedArray()
        val sql = """
            SELECT DISTINCT e.slug, e.rom_id, e.title, e.platform, e.boxart_url
            FROM entries e
            JOIN links l ON l.entry = e.slug
            WHERE e.platform IN ($placeholders)
              AND (
                lower(COALESCE(l.filename, '')) = ? OR
                lower(COALESCE(l.torrent_file_path, '')) LIKE ? OR
                lower(COALESCE(l.torrent_file_path, '')) LIKE ?
              )
            LIMIT 30
        """.trimIndent()
        return db.rawQuery(sql, args).use(::readEntries)
    }

    private fun readEntries(cursor: Cursor): List<CatalogEntry> {
        val out = mutableListOf<CatalogEntry>()
        val slug = cursor.getColumnIndexOrThrow("slug")
        val romId = cursor.getColumnIndex("rom_id")
        val title = cursor.getColumnIndexOrThrow("title")
        val platform = cursor.getColumnIndexOrThrow("platform")
        val boxartUrl = cursor.getColumnIndex("boxart_url")
        while (cursor.moveToNext()) {
            out += CatalogEntry(
                slug = cursor.getString(slug),
                romId = cursor.stringOrNull(romId),
                title = cursor.getString(title),
                platform = cursor.getString(platform),
                boxartUrl = cursor.stringOrNull(boxartUrl),
            )
        }
        return out
    }

    private fun readLinks(db: SQLiteDatabase, slug: String): List<CatalogLink> {
        return db.query("links", null, "entry = ?", arrayOf(slug), null, null, null).use { cursor ->
            val out = mutableListOf<CatalogLink>()
            val names = cursor.columnNames.toSet()
            while (cursor.moveToNext()) {
                out += CatalogLink(
                    name = cursor.value(names, "name"),
                    type = cursor.value(names, "type", "Game"),
                    format = cursor.value(names, "format"),
                    url = cursor.value(names, "url"),
                    filename = cursor.value(names, "filename"),
                    host = cursor.value(names, "host"),
                    size = cursor.longValue(names, "size"),
                    sourceId = cursor.nullableValue(names, "source_id"),
                    requiresAuth = cursor.longValue(names, "requires_auth") != 0L,
                    torrentInfohash = cursor.nullableValue(names, "torrent_infohash"),
                    torrentFileIndex = cursor.intValueOrNull(names, "torrent_file_index"),
                    torrentFilePath = cursor.nullableValue(names, "torrent_file_path"),
                )
            }
            out
        }
    }

    private fun readGroupMemberships(db: SQLiteDatabase, slugs: Set<String>): Map<String, CatalogGroupMembership> {
        if (slugs.isEmpty() || !tableExists(db, "entry_groups") || !tableExists(db, "entry_group_members")) {
            return emptyMap()
        }
        val out = linkedMapOf<String, CatalogGroupMembership>()
        slugs.chunked(400).forEach { batch ->
            val placeholders = batch.joinToString(",") { "?" }
            val sql = """
                SELECT gm.entry, gm.group_id, gm.member_index, gm.member_label,
                       eg.title AS group_title, eg.member_count
                FROM entry_group_members gm
                JOIN entry_groups eg ON eg.id = gm.group_id
                WHERE gm.entry IN ($placeholders) AND eg.kind = 'disc'
            """.trimIndent()
            db.rawQuery(sql, batch.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val entry = cursor.getString(cursor.getColumnIndexOrThrow("entry"))
                    val candidate = CatalogGroupMembership(
                        groupId = cursor.getString(cursor.getColumnIndexOrThrow("group_id")),
                        groupTitle = cursor.getString(cursor.getColumnIndexOrThrow("group_title")),
                        memberIndex = cursor.getInt(cursor.getColumnIndexOrThrow("member_index")),
                        memberLabel = cursor.getString(cursor.getColumnIndexOrThrow("member_label")),
                        memberCount = cursor.getInt(cursor.getColumnIndexOrThrow("member_count")),
                    )
                    val previous = out[entry]
                    if (previous == null || candidate.memberCount > previous.memberCount) out[entry] = candidate
                }
            }
        }
        return out
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean = db.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
        arrayOf(name),
    ).use { it.moveToFirst() }

    private fun scoreLink(
        descriptor: GameDescriptor,
        entry: CatalogEntry,
        link: CatalogLink,
        expectedArcadeName: String?,
        concreteRegion: String,
        enforcePreparationPolicy: Boolean = true,
    ): Int? {
        if (!link.type.equals("Game", ignoreCase = true)) return null
        val directHttps = isDirectHttps(link)
        val isTorrent = !directHttps && !link.torrentInfohash.isNullOrBlank() && link.torrentFileIndex != null
        if (!directHttps && !isTorrent) return null
        if (isTorrent && !allowTorrents) return null
        if (link.requiresAuth && (!internetArchiveAuthenticated || !isInternetArchiveUrl(link.url))) return null

        val linkFile = sequenceOf<String?>(
            link.filename.takeIf { it.isNotBlank() },
            link.torrentFilePath,
            link.url.takeIf { it.isNotBlank() },
        )
            .filterNotNull()
            .map { value: String -> baseName(value) }
            .firstOrNull { value: String -> value.isNotBlank() }
            .orEmpty()
            .lowercase(Locale.ROOT)

        if (expectedArcadeName != null && linkFile != expectedArcadeName) return null
        // Filter before ranking so both single-source and edition resolution can choose a usable
        // alternative. A ZIP is only a transport; its declared logical payload must be persistable.
        if (enforcePreparationPolicy && descriptor.platform in setOf("ps1", "psx") &&
            !Ps1DownloadFormatPolicy.canPrepare(linkFile, link.format)) return null
        // The current Android preparation path can extract ZIP but not 7z. ROMgi exposes a
        // GameCube IA 7z route, so do not auto-select an artifact Oz cannot prepare.
        if (descriptor.platform in setOf("ps2", "gc", "wii") && linkFile.endsWith(".7z")) return null

        var score = 0
        if (expectedArcadeName != null) score += 10_000
        if (entry.romId?.equals(descriptor.romSet, ignoreCase = true) == true) score += 2_000
        if (entry.title.equals(descriptor.title, ignoreCase = true)) score += 1_000
        if (CatalogFilters.canonicalTitle(entry.title) == CatalogFilters.canonicalTitle(descriptor.title)) score += 1_200
        if (entry.platform == platformAliases(descriptor.platform).firstOrNull()) score += 500
        // Direct HTTPS wins over magnet metadata discovery, especially for large PS1 images.
        // Torrent remains a selective fallback when ROMgi has no usable direct link.
        if (directHttps) score += 2_000
        if (directHttps && link.requiresAuth) score += 350
        if (isTorrent) score += 120
        if (link.format.equals(expectedFormat(descriptor.platform), ignoreCase = true)) score += 150
        if (link.size > 4_096L) score += 50
        if (descriptor.platform == "arcade" && link.host.contains("MiNERVA", ignoreCase = true)) score += 1_500
        val preferred = RomgiRegionPolicy.effectivePreference(preferredRegion)
        if (concreteRegion == preferred) score += 350
        return score
    }

    private fun sourceRegion(entry: CatalogEntry, link: CatalogLink): String =
        RomgiSourcePolicy.detectConcreteRegion(
            fileName = link.filename,
            torrentFilePath = link.torrentFilePath,
            url = link.url,
            linkName = link.name,
            entryTitle = entry.title,
        )

    private fun isDirectHttps(link: CatalogLink): Boolean = RomgiSourcePolicy.isDirectHttps(link.url)

    private fun isInternetArchiveUrl(value: String): Boolean = runCatching {
        val host = java.net.URI(value).host?.lowercase(Locale.ROOT).orEmpty()
        host == "archive.org" || host.endsWith(".archive.org")
    }.getOrDefault(false)

    private fun playableFileName(descriptor: GameDescriptor, link: CatalogLink): String {
        if (descriptor.platform == "arcade" && !descriptor.romSet.isNullOrBlank()) {
            return "${descriptor.romSet.lowercase(Locale.ROOT)}.zip"
        }
        return sequenceOf<String?>(
            link.filename.takeIf { it.isNotBlank() },
            link.torrentFilePath,
            link.url.takeIf { it.isNotBlank() },
        )
            .filterNotNull()
            .map { value: String -> baseName(value) }
            .firstOrNull { value: String -> value.contains('.') }
            ?: "${descriptor.gameId}.${expectedFormat(descriptor.platform)}"
    }

    private fun readMagnet(db: SQLiteDatabase, infohash: String, title: String): String {
        val tableExists = db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name='torrents' LIMIT 1",
            null,
        ).use { it.moveToFirst() }
        if (tableExists) {
            db.query("torrents", null, "infohash = ?", arrayOf(infohash), null, null, null, "1").use { cursor ->
                if (cursor.moveToFirst()) {
                    val names = cursor.columnNames.toSet()
                    for (column in listOf("magnet", "magnet_uri", "url")) {
                        val value = cursor.nullableValue(names, column)
                        if (!value.isNullOrBlank() && value.startsWith("magnet:")) return value
                    }
                    val trackers = sequenceOf("trackers", "tracker_urls", "announce")
                        .mapNotNull { cursor.nullableValue(names, it) }
                        .flatMap { parseTrackers(it).asSequence() }
                        .toMutableList()
                    return synthesizeMagnet(infohash, title, trackers)
                }
            }
        }
        return synthesizeMagnet(infohash, title, emptyList())
    }

    private fun synthesizeMagnet(infohash: String, title: String, catalogTrackers: List<String>): String {
        val trackers = (catalogTrackers + DEFAULT_TRACKERS).distinct()
        return buildString {
            append("magnet:?xt=urn:btih:")
            append(infohash)
            append("&dn=")
            append(Uri.encode(title))
            trackers.forEach {
                append("&tr=")
                append(Uri.encode(it))
            }
        }
    }

    private fun parseTrackers(raw: String): List<String> {
        val cleaned = raw.trim().removePrefix("[").removeSuffix("]")
        return cleaned.split(Regex("[,\\n\\r]+"))
            .map { it.trim().trim('"', '\'') }
            .filter { it.startsWith("udp://") || it.startsWith("https://") }
    }

    private fun platformAliases(platform: String): List<String> = when (platform) {
        // Arcade is deliberately restricted to FBNeo. Mixing MAME set generations causes
        // "Romset is unknown" even when the archive happens to have the same display title.
        "arcade" -> listOf("fbneo")
        "psx" -> listOf("ps1")
        "n3ds" -> listOf("n3ds", "3ds")
        "genesis" -> listOf("smd")
        else -> listOf(platform)
    }

    private fun expectedFormat(platform: String): String = when (platform) {
        "arcade" -> "zip"
        "a26" -> "a26"
        "a52" -> "a52"
        "a78" -> "a78"
        "jag" -> "j64"
        "lynx" -> "lnx"
        "cv" -> "col"
        "tg16" -> "pce"
        "tgcd", "pcfx", "scd", "sat" -> "chd"
        "nes" -> "nes"
        "fds" -> "fds"
        "snes" -> "sfc"
        "n64" -> "z64"
        "ndd" -> "ndd"
        "gb" -> "gb"
        "gbc" -> "gbc"
        "gba" -> "gba"
        "nds" -> "nds"
        "dsi" -> "dsi"
        "3ds", "n3ds" -> "3ds"
        "gc" -> "rvz"
        "wii" -> "rvz"
        "min" -> "min"
        "vb" -> "vb"
        "sms" -> "sms"
        "smd" -> "md"
        "gg" -> "gg"
        "32x" -> "32x"
        "dc" -> "chd"
        "ps1" -> "chd"
        "ps2" -> "iso"
        "psp" -> "iso"
        else -> "rom"
    }

    private fun catalogDisplayTitle(value: String): String {
        return value
            .replace(Regex("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]"), " ")
            .replace(Regex("\\s+-\\s+(USA|Europe|Japan|World|Asia).*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun baseName(value: String): String {
        val clean = value.substringBefore('?').substringBefore('#').trim()
        return clean.substringAfterLast('/').substringAfterLast('\\')
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun Cursor.stringOrNull(index: Int): String? =
        if (index < 0 || isNull(index)) null else getString(index)

    private fun Cursor.value(columns: Set<String>, name: String, default: String = ""): String =
        nullableValue(columns, name) ?: default

    private fun Cursor.nullableValue(columns: Set<String>, name: String): String? {
        if (name !in columns) return null
        val index = getColumnIndex(name)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.longValue(columns: Set<String>, name: String): Long {
        if (name !in columns) return 0L
        val index = getColumnIndex(name)
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    private fun Cursor.intValueOrNull(columns: Set<String>, name: String): Int? {
        if (name !in columns) return null
        val index = getColumnIndex(name)
        return if (index < 0 || isNull(index)) null else getInt(index)
    }

    private data class CatalogEntry(
        val slug: String,
        val romId: String?,
        val title: String,
        val platform: String,
        val boxartUrl: String?,
    )

    private data class CatalogGroupMembership(
        val groupId: String,
        val groupTitle: String,
        val memberIndex: Int,
        val memberLabel: String,
        val memberCount: Int,
    )

    private data class EditionCandidate(
        val scored: ScoredSource,
        val resolved: ResolvedSource,
        val parsed: RomgiParsedEdition,
        val membership: CatalogGroupMembership?,
    )

    private data class CatalogLink(
        val name: String,
        val type: String,
        val format: String,
        val url: String,
        val filename: String,
        val host: String,
        val size: Long,
        val sourceId: String?,
        val requiresAuth: Boolean,
        val torrentInfohash: String?,
        val torrentFileIndex: Int?,
        val torrentFilePath: String?,
    )

    private data class ScoredSource(
        val score: Int,
        val titleMatch: RomTitleMatch,
        val entry: CatalogEntry,
        val link: CatalogLink,
        val sourceRegion: String,
        val sourceRegionRank: Int,
    )

    private data class CatalogDatabaseSource(
        val url: String,
        val sha256: String? = null,
        val fallback: Boolean = false,
    )

    fun cachedCatalogSize(): Long = databaseFile.takeIf { it.isFile }?.length() ?: 0L

    fun clearCache(): Boolean = runCatching {
        listOf(databaseFile, databasePart, gzipFile).forEach { if (it.exists()) it.delete() }
        true
    }.getOrDefault(false)

    companion object {
        private const val DATABASE_FILE = "romdb.db"
        private val DATABASE_SOURCES = listOf(
            CatalogDatabaseSource(
                url = "https://raw.githubusercontent.com/caprado/romgi/main/db/romdb.db.gz",
            ),
            CatalogDatabaseSource(
                url = "https://raw.githubusercontent.com/caprado/romgi/cb4ba3e49928a537dfa944007a3ae0e62e8b63e9/db/romdb.db.gz",
                sha256 = "178fb285f1b9bc2cd7b6c80a0c09fd519b985c13f05cbd625e183be54a668e72",
                fallback = true,
            ),
        )
        private const val BUFFER_SIZE = 256 * 1024
        private const val MAX_RESOLUTION_OPTIONS = 16
        private const val MAX_EDITION_PLANS = 48
        private const val AMBIGUITY_SCORE_GAP = 260
        // CUE/BIN and GDI require companion files. PersistentRomLibrary currently persists one
        // prepared file per disc, so selecting these formats would create a broken local artifact.
        // Keep them recognizable for source classification, but reject them as playable plans until
        // Oz has a durable bundle persistence model.
        internal val UNSUPPORTED_MULTI_FILE_DISC_FORMATS = setOf("cue", "bin", "bin/cue", "gdi")
        private val LOGICAL_DISC_FORMATS = setOf(
            "chd", "iso", "rvz", "cso", "pbp", "gcm", "wbfs", "gdi", "cdi", "bin/cue",
        )
        private val MULTI_DISC_FORMATS = LOGICAL_DISC_FORMATS - UNSUPPORTED_MULTI_FILE_DISC_FORMATS
        private val OPTICAL_PLATFORMS = setOf(
            "ps1", "ps2", "dc", "sat", "scd", "tgcd", "pcfx", "gc", "wii", "psp",
        )
        private val M3U_PLATFORMS = setOf("ps1", "ps2", "dc", "sat", "scd", "tgcd", "gc", "wii", "psp", "pcfx")
        private val DEFAULT_TRACKERS = listOf(
            "udp://bt1.archive.org:6969/announce",
            "udp://bt2.archive.org:6969/announce",
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
        )
    }
}

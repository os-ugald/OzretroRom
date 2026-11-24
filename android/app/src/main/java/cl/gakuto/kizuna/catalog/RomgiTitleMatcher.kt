package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.util.TextNormalizer
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

enum class RomMatchStrategy(val label: String, val tier: Int) {
    EXACT_TITLE_AND_REGION("Título exacto y región original", 0),
    EXACT_TITLE("Título exacto sin región", 1),
    CANONICAL_TITLE("Título canónico normalizado", 2),
    ALIAS("Título alternativo o alias", 3),
    PUNCTUATION_TOLERANT("Coincidencia tolerante de puntuación/artículos/subtítulo", 4),
    REORDERED_TITLE("Título alternativo reordenado", 5),
    REGIONAL_FALLBACK("Fallback regional configurable", 6),
    FUZZY_LAST_RESORT("Coincidencia difusa de último recurso", 7),
    ARCADE_EXACT_SET("Romset arcade exacto", 0),
}

data class RomTitleMatch(
    val strategy: RomMatchStrategy,
    val titleScore: Int,
    val similarity: Double,
    val matchedRegion: String,
    val regionRank: Int,
)

object RomgiTitleMatcher {
    private val bracketMetadata = Regex("\\s*[\\(\\[]([^\\)\\]]+)[\\)\\]]")
    private val genericMetadata = bracketMetadata
    private val subtitleSeparator = Regex("\\s*(?:[:：]|\\s[-–—]\\s)\\s*")
    private val apostrophes = Regex("[’‘`´]")
    private val articles = setOf("a", "an", "the", "el", "la", "los", "las", "un", "una", "le", "les")

    fun match(
        requestedTitle: String,
        requestedRegion: String,
        candidateTitle: String,
        preferredRegion: String,
        requestedAliases: List<String> = emptyList(),
        systemId: String = "",
    ): RomTitleMatch? {
        val requestedRaw = cleanWhitespace(requestedTitle)
        val candidateRaw = cleanWhitespace(candidateTitle)
        val requestedNoRegion = stripRegionMetadata(requestedRaw)
        val candidateNoRegion = stripRegionMetadata(candidateRaw)
        val matchedRegion = detectRegion(candidateRaw)
        val regionOrder = fallbackOrder(requestedRegion, preferredRegion, systemId)
        val rank = regionRank(matchedRegion, regionOrder)

        val exactRaw = requestedRaw.equals(candidateRaw, ignoreCase = true)
        val exactNoRegion = requestedNoRegion.equals(candidateNoRegion, ignoreCase = true)
        val requestedCanonical = canonical(requestedNoRegion)
        val candidateCanonical = canonical(candidateNoRegion)
        val sameCanonical = requestedCanonical.isNotBlank() && requestedCanonical == candidateCanonical
        val requestedAliasForms = buildSet {
            addAll(aliases(requestedNoRegion))
            requestedAliases.forEach { configuredAlias ->
                addAll(aliases(stripRegionMetadata(configuredAlias)))
            }
        }
        val candidateAliases = aliases(candidateNoRegion)
        val aliasMatch = requestedAliasForms.any { alias -> candidateAliases.any { it == alias } }
        val tolerantRequested = tolerant(requestedNoRegion)
        val tolerantCandidate = tolerant(candidateNoRegion)
        val tolerantMatch = tolerantRequested.isNotBlank() && tolerantRequested == tolerantCandidate
        val reorderedMatch = reordered(requestedNoRegion).intersect(reordered(candidateNoRegion)).isNotEmpty()
        val similarity = tokenSimilarity(tolerantRequested, tolerantCandidate)

        val base = when {
            exactRaw && regionCompatible(requestedRegion, matchedRegion) -> RomMatchStrategy.EXACT_TITLE_AND_REGION
            exactNoRegion -> RomMatchStrategy.EXACT_TITLE
            sameCanonical -> RomMatchStrategy.CANONICAL_TITLE
            aliasMatch -> RomMatchStrategy.ALIAS
            tolerantMatch -> RomMatchStrategy.PUNCTUATION_TOLERANT
            reorderedMatch -> RomMatchStrategy.REORDERED_TITLE
            similarity >= 0.90 && rank <= 5 -> RomMatchStrategy.REGIONAL_FALLBACK
            similarity >= 0.80 -> RomMatchStrategy.FUZZY_LAST_RESORT
            else -> return null
        }
        val titleScore = 100_000 - base.tier * 10_000 + (similarity * 1_000).toInt() - rank * 120
        return RomTitleMatch(base, titleScore, similarity, matchedRegion, rank)
    }

    fun queryTokens(title: String): List<String> = tolerant(stripRegionMetadata(title))
        .split(' ')
        .filter { it.length >= 3 && it !in articles }
        .distinct()
        .sortedByDescending(String::length)
        .take(5)

    fun stripRegionMetadata(value: String): String = cleanWhitespace(
        bracketMetadata.replace(value) { match ->
            val metadata = match.groupValues[1]
            if (regionFromMetadata(metadata) != null || isRevisionMetadata(metadata)) " " else match.value
        },
    )

    fun canonical(value: String): String = TextNormalizer.normalize(
        reorderTrailingArticle(stripGenericMetadata(value))
            .replace(apostrophes, "'")
            .replace(Regex("[:：–—-]"), " "),
    )

    fun tolerant(value: String): String {
        val words = canonical(value).split(' ').filter(String::isNotBlank).toMutableList()
        while (words.firstOrNull() in articles) words.removeAt(0)
        return words.joinToString(" ")
    }

    fun aliases(value: String): Set<String> {
        val clean = stripGenericMetadata(value)
        val parts = subtitleSeparator.split(clean).map(::tolerant).filter(String::isNotBlank)
        return buildSet {
            add(tolerant(clean))
            add(canonical(clean))
            add(tolerant(reorderTrailingArticle(clean)))
            if (parts.size > 1) {
                add(parts.joinToString(" "))
                add(parts.reversed().joinToString(" "))
                add(parts.last())
            }
        }
    }

    private fun reordered(value: String): Set<String> {
        val clean = stripGenericMetadata(value)
        val parts = subtitleSeparator.split(clean).map(::tolerant).filter(String::isNotBlank)
        return buildSet {
            if (parts.size > 1) {
                add(parts.joinToString(" "))
                add(parts.reversed().joinToString(" "))
            }
            val comma = clean.split(',').map(String::trim).filter(String::isNotBlank)
            if (comma.size == 2) {
                add(tolerant("${comma[1]} ${comma[0]}"))
                add(tolerant("${comma[0]} ${comma[1]}"))
            }
        }
    }

    private fun stripGenericMetadata(value: String): String = cleanWhitespace(value.replace(genericMetadata, " "))

    private fun reorderTrailingArticle(value: String): String {
        val match = Regex("^(.+),\\s*(The|A|An|El|La|Los|Las)$", RegexOption.IGNORE_CASE).matchEntire(value.trim())
            ?: return value
        return "${match.groupValues[2]} ${match.groupValues[1]}"
    }

    private fun tokenSimilarity(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0
        val leftTokens = left.split(' ').filter(String::isNotBlank).toSet()
        val rightTokens = right.split(' ').filter(String::isNotBlank).toSet()
        val union = leftTokens union rightTokens
        val jaccard = if (union.isEmpty()) 0.0 else (leftTokens intersect rightTokens).size.toDouble() / union.size
        val edit = 1.0 - levenshtein(left, right).toDouble() / max(left.length, right.length).coerceAtLeast(1)
        return jaccard * 0.62 + edit.coerceAtLeast(0.0) * 0.38
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        left.forEachIndexed { i, lc ->
            current[0] = i + 1
            right.forEachIndexed { j, rc ->
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (lc == rc) 0 else 1,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    fun detectRegion(value: String): String = detectRegionMetadata(value) ?: "Other"

    /**
     * Returns null when no regional metadata exists. Concrete country markers are mapped to their
     * broad region (for example France -> Europe) and still retain authority over a generic World
     * label found later in the catalog entry.
     */
    fun detectRegionMetadata(value: String): String? {
        val detected = bracketMetadata.findAll(value)
            .mapNotNull { regionFromMetadata(it.groupValues[1]) }
            .toList()
        return detected.firstOrNull { it != "World" && it != "Other" }
            ?: detected.firstOrNull { it == "World" }
            ?: detected.firstOrNull { it == "Other" }
    }

    private fun regionFromMetadata(metadata: String): String? {
        val normalized = Normalizer.normalize(metadata, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
        val tokens = normalized.split(Regex("[^a-z0-9]+"))
            .filter(String::isNotBlank)
            .toSet()
        return when {
            "world" in tokens || "international" in tokens || "mundial" in tokens -> "World"
            "usa" in tokens || "us" in tokens || ("united" in tokens && "states" in tokens) -> "USA"
            "europe" in tokens || "europa" in tokens || "eu" in tokens || "pal" in tokens -> "Europe"
            "japan" in tokens || "japanese" in tokens || "jp" in tokens || "japon" in tokens -> "Japan"
            setOf("asia", "korea", "taiwan", "china").any(tokens::contains) ||
                ("hong" in tokens && "kong" in tokens) -> "Asia"
            setOf("spain", "france", "germany", "italy", "portugal", "netherlands", "belgium").any(tokens::contains) -> "Europe"
            setOf("australia", "brazil", "canada", "mexico").any(tokens::contains) -> "Other"
            else -> null
        }
    }

    private fun isRevisionMetadata(metadata: String): Boolean {
        val normalized = TextNormalizer.normalize(metadata)
        return normalized.startsWith("rev ") || normalized == "rev" ||
            normalized.startsWith("revision ") || normalized.startsWith("version ") ||
            normalized.matches(Regex("v[0-9].*"))
    }

    fun fallbackOrder(originalRegion: String, preferredRegion: String, systemId: String = ""): List<String> =
        RomgiRegionPolicy.order(systemId, originalRegion, preferredRegion)

    fun isBroadRegion(value: String): Boolean = normalizeRegion(value) in setOf("World", "Other")

    fun normalizeRegion(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "world", "international", "mundial" -> "World"
        "usa", "us", "united states", "north america" -> "USA"
        "europe", "eu", "europa", "pal" -> "Europe"
        "japan", "jp", "japon", "japón" -> "Japan"
        "asia", "korea", "taiwan", "china" -> "Asia"
        else -> "Other"
    }

    private fun regionCompatible(requested: String, candidate: String): Boolean {
        val normalized = normalizeRegion(requested)
        return isBroadRegion(normalized) || normalized == candidate
    }

    private fun regionRank(region: String, order: List<String>): Int = order.indexOf(region).takeIf { it >= 0 } ?: order.size

    private fun cleanWhitespace(value: String): String = value.replace(Regex("\\s+"), " ").trim()
}

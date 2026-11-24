package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.util.TextNormalizer
import java.util.Locale

/** Release families that should not be mixed with normal retail downloads. */
enum class RomgiReleaseKind(val label: String, val advanced: Boolean) {
    RETAIL("Retail", false),
    DEMO("Demo", true),
    PROTOTYPE("Prototipo", true),
    BETA("Beta", true),
    SAMPLE("Sample", true),
    OTHER("Especial", true),
}

data class RomgiParsedEdition(
    val originalTitle: String,
    val parentTitle: String,
    val editionTitle: String,
    val editionKey: String,
    val discIndex: Int,
    val discLabel: String,
    val releaseKind: RomgiReleaseKind,
    val revisionLabel: String?,
)

data class RomgiDiscSource(
    val index: Int,
    val label: String,
    val source: ResolvedSource,
)

/** A concrete, complete download choice: one edition in one playable format. */
data class RomgiDownloadPlan(
    val id: String,
    val parentTitle: String,
    val editionTitle: String,
    val region: String,
    val format: String,
    val releaseKind: RomgiReleaseKind,
    val discs: List<RomgiDiscSource>,
    val artworkUrl: String?,
    val groupId: String? = null,
    val score: Int = 0,
) {
    val discCount: Int get() = discs.size
    val isMultiDisc: Boolean get() = discCount > 1
    val totalSize: Long get() = discs.sumOf { it.source.size.coerceAtLeast(0L) }
    val usesTorrent: Boolean get() = discs.any { it.source.isTorrent }
    val isComplete: Boolean
        get() = discs.isNotEmpty() && discs.map { it.index } == (1..discs.size).toList()
    val requiresRegionConfirmation: Boolean get() = discs.any { it.source.regionFallback }
    val hasWeakMatch: Boolean get() = discs.any {
        it.source.matchStrategy in setOf(
            RomMatchStrategy.REORDERED_TITLE,
            RomMatchStrategy.REGIONAL_FALLBACK,
            RomMatchStrategy.FUZZY_LAST_RESORT,
        )
    }
}

data class RomgiEditionResolution(
    val recommended: RomgiDownloadPlan,
    val standard: List<RomgiDownloadPlan>,
    val advanced: List<RomgiDownloadPlan>,
) {
    val allPlans: List<RomgiDownloadPlan> get() = standard + advanced

    /** A single exact retail plan may continue without interrupting the launch flow. */
    val requiresConfirmation: Boolean
        get() = standard.size != 1 || recommended.requiresRegionConfirmation || recommended.hasWeakMatch
}

/**
 * ROMgi-compatible disc parser. It deliberately removes only disc metadata from the edition key:
 * region, language and revision remain part of the identity so Rev 1 can never absorb retail v1.
 */
object RomgiEditionParser {
    private val discToken = Regex(
        "[\\(\\[]\\s*(?:disc|disk|disco)\\s+([0-9]+|[ivxlcdm]+|[a-z])" +
            "(?:\\s+(?:of|de)\\s+([0-9]+|[ivxlcdm]+|[a-z]))?\\s*[\\)\\]]",
        RegexOption.IGNORE_CASE,
    )
    private val metadataToken = Regex("[\\(\\[]([^\\)\\]]+)[\\)\\]]")
    private val regionSuffix = Regex("\\s+-\\s+(USA|Europe|Japan|World|Asia).*$", RegexOption.IGNORE_CASE)
    private val revision = Regex("\\b(?:rev(?:ision)?|ver(?:sion)?)\\s*[._ -]*([0-9a-z.]+)", RegexOption.IGNORE_CASE)

    fun parse(title: String, forcedDiscIndex: Int? = null, forcedDiscLabel: String? = null): RomgiParsedEdition {
        val match = discToken.find(title)
        val parsedIndex = forcedDiscIndex?.takeIf { it > 0 }
            ?: match?.groupValues?.getOrNull(1)?.let(::discNumber)
            ?: 1
        val editionTitle = cleanTitle(title.replace(discToken, " ")).ifBlank { cleanTitle(title) }
        val parentTitle = cleanTitle(editionTitle.replace(metadataToken, " ").replace(regionSuffix, " "))
            .ifBlank { editionTitle }
        val lower = TextNormalizer.normalize(editionTitle)
        val kind = when {
            Regex("\\b(demo|trial|kiosk)\\b").containsMatchIn(lower) -> RomgiReleaseKind.DEMO
            Regex("\\b(prototype|proto)\\b").containsMatchIn(lower) -> RomgiReleaseKind.PROTOTYPE
            Regex("\\b(beta|alpha|preview)\\b").containsMatchIn(lower) -> RomgiReleaseKind.BETA
            Regex("\\b(sample|sampler)\\b").containsMatchIn(lower) -> RomgiReleaseKind.SAMPLE
            Regex("\\b(bootleg|hack|homebrew)\\b").containsMatchIn(lower) -> RomgiReleaseKind.OTHER
            else -> RomgiReleaseKind.RETAIL
        }
        val revisionLabel = metadataToken.findAll(editionTitle)
            .map { it.groupValues[1].trim() }
            .firstOrNull { revision.containsMatchIn(it) }
        return RomgiParsedEdition(
            originalTitle = title,
            parentTitle = parentTitle,
            editionTitle = editionTitle,
            editionKey = TextNormalizer.normalize(editionTitle),
            discIndex = parsedIndex,
            discLabel = forcedDiscLabel?.takeIf(String::isNotBlank) ?: "Disco $parsedIndex",
            releaseKind = kind,
            revisionLabel = revisionLabel,
        )
    }

    fun parentTitle(title: String): String = parse(title).parentTitle

    private fun discNumber(value: String): Int? {
        value.toIntOrNull()?.let { return it.takeIf { number -> number > 0 } }
        val normalized = value.uppercase(Locale.ROOT)
        if (normalized.all { it in "IVXLCDM" }) {
            var total = 0
            var previous = 0
            normalized.reversed().forEach { symbol ->
                val current = when (symbol) {
                    'I' -> 1
                    'V' -> 5
                    'X' -> 10
                    'L' -> 50
                    'C' -> 100
                    'D' -> 500
                    'M' -> 1_000
                    else -> return null
                }
                total += if (current < previous) -current else current
                previous = current
            }
            return total.takeIf { it > 0 }
        }
        if (normalized.length == 1 && normalized[0] in 'A'..'Z') return normalized[0] - 'A' + 1
        return null
    }

    private fun cleanTitle(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s+([,;:])"), "$1")
        .trim(' ', '-', '_')
}

object M3uPlaylist {
    fun fileName(title: String): String = title
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(150)
        .ifBlank { "Juego multidisco" } + ".m3u"

    fun content(discNames: List<String>): String {
        require(discNames.size >= 2) { "Una lista M3U multidisco necesita al menos dos discos." }
        val safe = discNames.map { name ->
            require(name.isNotBlank() && '/' !in name && '\\' !in name && '\n' !in name && '\r' !in name) {
                "El M3U solo admite nombres relativos seguros."
            }
            name
        }
        return safe.joinToString(separator = "\n", postfix = "\n")
    }
}

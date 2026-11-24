package cl.gakuto.kizuna.catalog

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Pure ROMgi source rules kept separate from Android/SQLite so regional precedence can be tested.
 */
object RomgiSourcePolicy {
    /**
     * The real downloadable filename is authoritative. A broad catalog label such as "World"
     * must never hide an explicit country marker from the concrete file, such as "(France)".
     */
    fun detectConcreteRegion(
        fileName: String?,
        torrentFilePath: String?,
        url: String?,
        linkName: String?,
        entryTitle: String,
    ): String = sequenceOf(
        fileName.orEmpty(),
        torrentFilePath.orEmpty(),
        url.orEmpty().fileNameFromUrl(),
        linkName.orEmpty(),
        entryTitle,
    ).mapNotNull(RomgiTitleMatcher::detectRegionMetadata)
        .firstOrNull()
        ?: "Other"

    fun isDirectHttps(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val clean = url.substringBefore('?').substringBefore('#')
        return !clean.endsWith(".torrent", ignoreCase = true)
    }

    private fun String.fileNameFromUrl(): String {
        val clean = substringBefore('?').substringBefore('#').trim()
        val encodedName = clean.substringAfterLast('/').substringAfterLast('\\')
        return runCatching { URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name()) }
            .getOrDefault(encodedName)
    }
}

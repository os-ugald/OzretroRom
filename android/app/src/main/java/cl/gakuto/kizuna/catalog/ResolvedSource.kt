package cl.gakuto.kizuna.catalog

data class ResolvedSource(
    val entrySlug: String,
    val entryTitle: String,
    val platform: String,
    val fileName: String,
    val format: String,
    val url: String,
    val host: String,
    val size: Long,
    val sourceId: String?,
    val torrentInfohash: String?,
    val torrentFileIndex: Int?,
    val torrentFilePath: String?,
    val magnetUri: String?,
    val useTorrent: Boolean = false,
    val matchStrategy: RomMatchStrategy = RomMatchStrategy.CANONICAL_TITLE,
    val matchedRegion: String = "Other",
    val requestedRegion: String = "Unknown",
    val preferredRegion: String = "USA",
    val confidence: Int = 0,
    val requiresAuth: Boolean = false,
    val regionFallback: Boolean = false,
    val boxartUrl: String? = null,
) {
    val isTorrent: Boolean
        get() = useTorrent

    val matchDescription: String
        get() = "${matchStrategy.label} · región $matchedRegion · confianza $confidence%"
}

data class RomgiResolution(
    val selected: ResolvedSource,
    val alternatives: List<ResolvedSource>,
    val ambiguous: Boolean,
) {
    val allOptions: List<ResolvedSource>
        get() = listOf(selected) + alternatives

    /**
     * Exact, same-region choices can start immediately. Confirmation is reserved for cases where
     * user input actually protects against a wrong edition: ambiguity, a regional fallback or a
     * low-confidence/reordered match.
     */
    val requiresConfirmation: Boolean
        get() = ambiguous || alternatives.isNotEmpty() || selected.regionFallback ||
            selected.matchStrategy in setOf(
                RomMatchStrategy.REORDERED_TITLE,
                RomMatchStrategy.REGIONAL_FALLBACK,
                RomMatchStrategy.FUZZY_LAST_RESORT,
            )
}

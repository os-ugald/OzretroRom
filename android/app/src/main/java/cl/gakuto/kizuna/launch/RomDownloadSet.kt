package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.catalog.M3uPlaylist
import cl.gakuto.kizuna.data.AuthorizedSource

data class RomDownloadSetItem(
    val discIndex: Int,
    val discLabel: String,
    val source: AuthorizedSource,
    val sourcePlan: DownloadSourcePlan = DownloadSourcePlan.single(
        LogicalDownloadIdentity.forDisc(
            parentTitle = null,
            editionTitle = null,
            region = null,
            format = null,
            discIndex = discIndex,
            expectedFileName = source.expectedFileName,
            expectedSize = source.expectedSize,
        ),
        source,
    ),
) {
    init {
        require(sourcePlan.selectedSource == source) {
            "La fuente activa del disco debe coincidir con el candidato seleccionado."
        }
    }
}

/** Serializable transfer contract for a complete multi-disc edition. */
data class RomDownloadSet(
    val parentTitle: String,
    val editionTitle: String,
    val region: String,
    val format: String,
    val artworkUrl: String?,
    val items: List<RomDownloadSetItem>,
) {
    init {
        require(items.size >= 2) { "Un conjunto multidisco requiere al menos dos archivos." }
        require(items.map { it.discIndex } == (1..items.size).toList()) {
            "Los discos deben estar completos y ordenados desde 1."
        }
    }

    val totalSize: Long
        get() {
            val sizes = items.map { it.source.expectedSize }
            return if (sizes.all { it > 0L }) sizes.sum() else -1L
        }
    val playlistName: String get() = M3uPlaylist.fileName(editionTitle)
    val signature: String get() = items.joinToString("|") {
        "${it.discIndex}:${it.source.value}:${it.source.expectedFileName.orEmpty()}"
    }

    companion object {
        fun singleSourceItem(
            parentTitle: String,
            editionTitle: String,
            region: String,
            format: String,
            discIndex: Int,
            discLabel: String,
            source: AuthorizedSource,
        ): RomDownloadSetItem = RomDownloadSetItem(
            discIndex = discIndex,
            discLabel = discLabel,
            source = source,
            sourcePlan = DownloadSourcePlan.single(
                LogicalDownloadIdentity.forDisc(
                    parentTitle = parentTitle,
                    editionTitle = editionTitle,
                    region = region,
                    format = format,
                    discIndex = discIndex,
                    expectedFileName = source.expectedFileName,
                    expectedSize = source.expectedSize,
                ),
                source,
            ),
        )
    }
}

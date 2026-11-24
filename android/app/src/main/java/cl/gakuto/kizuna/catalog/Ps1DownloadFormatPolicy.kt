package cl.gakuto.kizuna.catalog

import java.util.Locale

/** Eligibility for the current one-persistent-file-per-disc Android contract, not certification. */
internal object Ps1DownloadFormatPolicy {
    private val selfContained = setOf("chd", "iso", "pbp")

    fun canPrepare(fileName: String, logicalFormat: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in selfContained ||
            (extension == "zip" && logicalFormat.trim().lowercase(Locale.ROOT) in selfContained)
    }
}

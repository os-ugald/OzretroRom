package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.data.AuthorizedSource
import java.security.MessageDigest

@JvmInline
value class LogicalDownloadIdentity(val value: String) {
    init {
        require(value.isNotBlank()) { "La identidad logica de descarga no puede estar vacia." }
    }

    companion object {
        fun from(namespace: String, vararg exactParts: String?): LogicalDownloadIdentity =
            LogicalDownloadIdentity(stableIdentity(namespace, exactParts.toList()))

        fun forTask(
            gameId: String,
            resolvedRegion: String?,
            expectedFileName: String?,
            expectedSize: Long,
        ): LogicalDownloadIdentity = from(
            "rom-download-task-v1",
            gameId,
            resolvedRegion,
            expectedFileName,
            expectedSize.toString(),
        )

        fun forDisc(
            parentTitle: String?,
            editionTitle: String?,
            region: String?,
            format: String?,
            discIndex: Int,
            expectedFileName: String?,
            expectedSize: Long,
        ): LogicalDownloadIdentity = from(
            "rom-download-disc-v1",
            parentTitle,
            editionTitle,
            region,
            format,
            discIndex.toString(),
            expectedFileName,
            expectedSize.toString(),
        )
    }
}

data class DownloadSourceCandidate(
    val identity: String,
    val source: AuthorizedSource,
) {
    init {
        require(identity.isNotBlank()) { "La identidad del candidato no puede estar vacia." }
    }

    companion object {
        fun from(source: AuthorizedSource): DownloadSourceCandidate = DownloadSourceCandidate(
            identity = stableIdentity(
                namespace = "authorized-source-v1",
                parts = listOf(
                    source.value,
                    source.expectedFileName,
                    source.expectedSize.toString(),
                    source.torrentFileIndex?.toString(),
                    source.torrentFilePath,
                    source.requiresInternetArchiveAuth.toString(),
                ),
            ),
            source = source,
        )
    }
}

/** Immutable Source Index coordinates carried through a download task into local persistence. */
data class SourceIndexProvenance(
    val gameId: String,
    val releaseId: String,
    val candidateId: String,
    val provider: String,
    val infoHash: String,
    val fileIndex: Int,
    val exactFullPath: String,
    val expectedSize: Long,
)

/** Ordered, durable source description. Runtime selection remains fixed in DOWNLOAD-1B.3a. */
data class DownloadSourcePlan(
    val logicalIdentity: LogicalDownloadIdentity,
    val candidates: List<DownloadSourceCandidate>,
    val selectedCandidateIndex: Int = 0,
    val sourceIndexProvenance: SourceIndexProvenance? = null,
) {
    init {
        require(candidates.isNotEmpty()) { "El plan de fuentes requiere al menos un candidato." }
        require(selectedCandidateIndex in candidates.indices) { "El candidato seleccionado no existe." }
        require(candidates.map { it.identity }.distinct().size == candidates.size) {
            "Las identidades de candidatos deben ser unicas dentro del plan."
        }
    }

    val selectedCandidate: DownloadSourceCandidate get() = candidates[selectedCandidateIndex]
    val selectedSource: AuthorizedSource get() = selectedCandidate.source

    companion object {
        fun single(logicalIdentity: LogicalDownloadIdentity, source: AuthorizedSource): DownloadSourcePlan =
            DownloadSourcePlan(logicalIdentity, listOf(DownloadSourceCandidate.from(source)))
    }
}

internal data class StoredDownloadSourceCandidate(
    val identity: String,
    val source: AuthorizedSource,
)

internal data class StoredDownloadSourcePlan(
    val logicalIdentity: String,
    val candidates: List<StoredDownloadSourceCandidate>,
    val selectedCandidateIndex: Int,
    val sourceIndexProvenance: SourceIndexProvenance?,
)

internal fun DownloadSourcePlan.toStoredSourcePlan(): StoredDownloadSourcePlan = StoredDownloadSourcePlan(
    logicalIdentity = logicalIdentity.value,
    candidates = candidates.map { StoredDownloadSourceCandidate(it.identity, it.source) },
    selectedCandidateIndex = selectedCandidateIndex,
    sourceIndexProvenance = sourceIndexProvenance,
)

internal fun StoredDownloadSourcePlan.toSourcePlan(): DownloadSourcePlan = DownloadSourcePlan(
    logicalIdentity = LogicalDownloadIdentity(logicalIdentity),
    candidates = candidates.map { DownloadSourceCandidate(it.identity, it.source) },
    selectedCandidateIndex = selectedCandidateIndex,
    sourceIndexProvenance = sourceIndexProvenance,
)

private fun stableIdentity(namespace: String, parts: List<String?>): String {
    val canonical = buildString {
        append(namespace.length).append(':').append(namespace)
        parts.forEach { part ->
            if (part == null) append("|-1:") else append('|').append(part.length).append(':').append(part)
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

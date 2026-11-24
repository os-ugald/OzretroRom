package cl.gakuto.kizuna.launch

import java.net.HttpURLConnection
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal fun candidatePartialRelativePath(owner: HttpPartialOwner, displayName: String): String {
    require(owner.logicalIdentity.matches(Regex("[a-f0-9]{64}")))
    require(owner.candidateIdentity.matches(Regex("[a-f0-9]{64}")))
    val safe = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180).ifBlank { "game.rom" }
    return "http/${owner.logicalIdentity}/${owner.candidateIdentity}/$safe"
}

internal data class HttpReplacementPreparation(
    val activeTarget: File,
    val preservedPrevious: File?,
)

/**
 * Establishes an empty physical generation before its representation metadata is published.
 * The rename stays within the candidate directory so the previous Oz-owned bytes are preserved.
 */
internal fun prepareHttpReplacementTarget(target: File): HttpReplacementPreparation {
    target.parentFile?.mkdirs()
    val preserved = target.takeIf { it.isFile && it.length() > 0L }?.let { previous ->
        val quarantine = generateSequence(1) { it + 1 }
            .map { File(previous.parentFile, "${previous.name}.unsafe-$it") }
            .first { !it.exists() }
        check(previous.renameTo(quarantine)) {
            "No fue posible preservar la generación HTTP anterior antes de reemplazarla."
        }
        quarantine
    }
    FileOutputStream(target, false).use { output ->
        output.fd.sync()
    }
    check(target.isFile && target.length() == 0L) {
        "No fue posible establecer una generación HTTP vacía y segura."
    }
    return HttpReplacementPreparation(target, preserved)
}

internal data class TransferRecoveryScope(
    val durableTaskGameId: String,
    val sourcePlan: DownloadSourcePlan,
)

internal fun RomDownloadTaskSnapshot.withPersistedHttpRecovery(
    recovery: HttpCandidateRecovery,
    updatedAt: Long,
): RomDownloadTaskSnapshot = copy(
    httpRecoveries = httpRecoveries.filterNot { it.owner == recovery.owner } + recovery,
    // This is deliberately sticky: persisting one safe candidate cannot prove that unrelated
    // legacy root, multidisc, or BIOS artifacts no longer exist.
    unsafeLegacyPartial = unsafeLegacyPartial,
    updatedAt = updatedAt,
)

data class HttpRepresentationState(
    val strongETag: String? = null,
    val weakETag: String? = null,
    val lastModified: String? = null,
    val lastModifiedStrong: Boolean = false,
    val totalLength: Long = -1L,
    val effectiveUrl: String? = null,
) {
    init {
        require(strongETag == null || isValidEntityTag(strongETag) && !strongETag.startsWith("W/"))
        require(weakETag == null || isValidEntityTag(weakETag) && weakETag.startsWith("W/"))
    }

    val ifRangeValue: String?
        get() = strongETag ?: lastModified?.takeIf { lastModifiedStrong }

    internal fun contradicts(headers: HttpResponseEvidence): Boolean =
        (strongETag != null && headers.etag != null && strongETag != headers.etag) ||
            (lastModified != null && headers.lastModified != null && lastModified != headers.lastModified)

    companion object {
        internal fun fromResponse(headers: HttpResponseEvidence): HttpRepresentationState {
            val etag = headers.etag?.trim()?.takeIf(::isValidEntityTag)
            val weak = etag?.takeIf { it.startsWith("W/") }
            val strong = etag?.takeUnless { it.startsWith("W/") }
            return HttpRepresentationState(
                strongETag = strong,
                weakETag = weak,
                lastModified = headers.lastModified,
                lastModifiedStrong = etag == null && isStrongLastModified(headers.lastModified, headers.date),
                totalLength = headers.completeLength,
                effectiveUrl = headers.effectiveUrl,
            )
        }

        internal fun isStrongLastModified(lastModified: String?, date: String?): Boolean {
            val modifiedAt = parseHttpDate(lastModified) ?: return false
            val responseAt = parseHttpDate(date) ?: return false
            return !modifiedAt.isAfter(responseAt.minusSeconds(60))
        }

        private fun parseHttpDate(value: String?): Instant? = runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }.getOrNull()

        private fun isValidEntityTag(value: String): Boolean {
            val opaque = value.removePrefix("W/")
            return opaque.length >= 2 && opaque.first() == '"' && opaque.last() == '"' &&
                opaque.substring(1, opaque.lastIndex).none { it == '"' || it.code < 0x21 || it.code == 0x7f }
        }
    }
}

data class HttpPartialOwner(
    val logicalIdentity: String,
    val candidateIdentity: String,
)

data class HttpCandidateRecovery(
    val owner: HttpPartialOwner,
    val relativePath: String,
    val representation: HttpRepresentationState,
    val quarantined: Boolean = false,
)

internal interface HttpRecoveryAccess {
    fun get(owner: HttpPartialOwner): HttpCandidateRecovery?
    fun hasQuarantined(owner: HttpPartialOwner): Boolean
    fun persist(recovery: HttpCandidateRecovery)
    fun quarantine(owner: HttpPartialOwner)
    fun markUnsafeArtifactsPresent()

    object None : HttpRecoveryAccess {
        override fun get(owner: HttpPartialOwner) = null
        override fun hasQuarantined(owner: HttpPartialOwner) = false
        override fun persist(recovery: HttpCandidateRecovery) = Unit
        override fun quarantine(owner: HttpPartialOwner) = Unit
        override fun markUnsafeArtifactsPresent() = Unit
    }
}

internal data class HttpResponseEvidence(
    val status: Int,
    val contentRange: String?,
    val contentLength: Long,
    val etag: String?,
    val lastModified: String?,
    val date: String?,
    val effectiveUrl: String?,
) {
    val parsedRange: ParsedContentRange? get() = ParsedContentRange.parse(contentRange)
    val completeLength: Long
        get() = when {
            parsedRange?.completeLength != null -> parsedRange!!.completeLength!!
            status == HttpURLConnection.HTTP_OK && contentLength >= 0L -> contentLength
            else -> -1L
        }
}

internal data class ParsedContentRange(
    val first: Long?,
    val last: Long?,
    val completeLength: Long?,
    val unsatisfied: Boolean,
) {
    companion object {
        private val satisfied = Regex("^bytes (\\d+)-(\\d+)/(\\d+|\\*)$", RegexOption.IGNORE_CASE)
        private val unsatisfied = Regex("^bytes \\*/(\\d+)$", RegexOption.IGNORE_CASE)

        fun parse(value: String?): ParsedContentRange? {
            val normalized = value?.trim() ?: return null
            unsatisfied.matchEntire(normalized)?.let { match ->
                return ParsedContentRange(null, null, match.groupValues[1].toLongOrNull(), true)
            }
            val match = satisfied.matchEntire(normalized) ?: return null
            val first = match.groupValues[1].toLongOrNull() ?: return null
            val last = match.groupValues[2].toLongOrNull() ?: return null
            val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
            if (last < first || total != null && last >= total) return null
            return ParsedContentRange(first, last, total, false)
        }
    }
}

internal sealed interface HttpResumeDecision {
    data class Append(val total: Long) : HttpResumeDecision
    data class Replace(val representation: HttpRepresentationState) : HttpResumeDecision
    data object AlreadyComplete : HttpResumeDecision
    data class Reject(val failure: TransferFailure) : HttpResumeDecision
}

internal object HttpResumePolicy {
    fun decide(
        requestedOffset: Long,
        localLength: Long,
        expectedLength: Long,
        persisted: HttpRepresentationState?,
        response: HttpResponseEvidence,
    ): HttpResumeDecision {
        if (response.status == HttpURLConnection.HTTP_OK) {
            if (expectedLength > 0L && response.completeLength > 0L && expectedLength != response.completeLength) {
                return HttpResumeDecision.Reject(TransferFailure.RepresentationMismatch)
            }
            return HttpResumeDecision.Replace(HttpRepresentationState.fromResponse(response))
        }
        if (response.status == HttpURLConnection.HTTP_PARTIAL) {
            val range = response.parsedRange
                ?: return HttpResumeDecision.Reject(TransferFailure.UnsafeHttpResponse("Content-Range ausente o invalido"))
            val total = range.completeLength
                ?: return HttpResumeDecision.Reject(TransferFailure.UnsafeHttpResponse("Content-Range sin longitud completa"))
            val expectedCompatible = expectedLength <= 0L || expectedLength == total
            val persistedCompatible = persisted?.totalLength?.let { it <= 0L || it == total } ?: true
            val responseLengthCompatible = range.first != null && range.last != null &&
                (response.contentLength < 0L || response.contentLength == (range.last - range.first + 1L))
            if (requestedOffset <= 0L || localLength != requestedOffset || range.unsatisfied ||
                range.first != requestedOffset || !expectedCompatible || !persistedCompatible ||
                !responseLengthCompatible || persisted?.ifRangeValue == null || persisted.contradicts(response)
            ) {
                return HttpResumeDecision.Reject(TransferFailure.RepresentationMismatch)
            }
            return HttpResumeDecision.Append(total)
        }
        if (response.status == 416) {
            val range = response.parsedRange
            val total = range?.completeLength
            val safe = requestedOffset > 0L && persisted != null && persisted.ifRangeValue != null &&
                range?.unsatisfied == true && total != null && localLength == total &&
                (expectedLength <= 0L || expectedLength == total) &&
                (persisted.totalLength <= 0L || persisted.totalLength == total) &&
                !persisted.contradicts(response)
            return if (safe) HttpResumeDecision.AlreadyComplete
            else HttpResumeDecision.Reject(TransferFailure.UnsafeRangeNotSatisfiable)
        }
        return HttpResumeDecision.Reject(TransferFailure.HttpStatus(response.status))
    }
}

sealed interface TransferFailure {
    val retryDisposition: RetryDisposition

    data class HttpStatus(val status: Int) : TransferFailure {
        override val retryDisposition = RetryDisposition.SAME_SOURCE
    }
    data class UnsafeHttpResponse(val reason: String) : TransferFailure {
        override val retryDisposition = RetryDisposition.SAME_SOURCE
    }
    data object RepresentationMismatch : TransferFailure {
        override val retryDisposition = RetryDisposition.SAME_SOURCE
    }
    data object UnsafeRangeNotSatisfiable : TransferFailure {
        override val retryDisposition = RetryDisposition.SAME_SOURCE
    }
}

enum class RetryDisposition { SAME_SOURCE, NEXT_CANDIDATE, USER_ACTION, PERMANENT }

class TransferFailureException(
    val failure: TransferFailure,
    message: String,
    cause: Throwable? = null,
) : java.io.IOException(message, cause)

package cl.gakuto.kizuna.launch

import java.net.HttpURLConnection
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTransferRecoveryTest {
    private val logical = LogicalDownloadIdentity.from("test", "game", "USA", "CHD")
    private val candidateA = DownloadSourceCandidate.from(source("https://one.invalid/game.chd"))
    private val candidateB = DownloadSourceCandidate.from(source("https://two.invalid/game.chd"))
    private val strong = HttpRepresentationState(strongETag = "\"v1\"", totalLength = 1000L)

    @Test
    fun identicalNamesHaveCandidateOwnedPaths() {
        val first = candidatePartialRelativePath(owner(candidateA), "game.chd")
        val second = candidatePartialRelativePath(owner(candidateB), "game.chd")

        assertNotEquals(first, second)
        assertTrue(first.contains(candidateA.identity))
        assertTrue(second.contains(candidateB.identity))
    }

    @Test
    fun traversalTextCannotEscapeCandidateDirectory() {
        val path = candidatePartialRelativePath(owner(candidateA), "../../other/game.chd")

        assertEquals(3, path.count { it == '/' })
        assertTrue(path.substringAfterLast('/').endsWith("game.chd"))
    }

    @Test
    fun strongEtagIsEligibleButWeakEtagIsNot() {
        assertEquals("\"v1\"", strong.ifRangeValue)
        assertNull(HttpRepresentationState(weakETag = "W/\"v1\"").ifRangeValue)
        val weakWithDate = response(
            status = 200,
            etag = "W/\"v1\"",
            lastModified = "Sun, 06 Nov 1994 08:49:37 GMT",
            date = "Sun, 06 Nov 1994 08:50:37 GMT",
        )
        assertNull(HttpRepresentationState.fromResponse(weakWithDate).ifRangeValue)
    }

    @Test
    fun lastModifiedIsEligibleOnlyWhenItIsStrongByRfcHeuristic() {
        val eligible = response(
            status = 200,
            lastModified = "Sun, 06 Nov 1994 08:49:37 GMT",
            date = "Sun, 06 Nov 1994 08:50:37 GMT",
        )
        val tooRecent = eligible.copy(date = "Sun, 06 Nov 1994 08:50:36 GMT")

        assertEquals(eligible.lastModified, HttpRepresentationState.fromResponse(eligible).ifRangeValue)
        assertNull(HttpRepresentationState.fromResponse(tooRecent).ifRangeValue)
    }

    @Test
    fun valid206AppendsAtExactOffset() {
        val decision = decide(
            response = response(206, "bytes 400-999/1000", 600L, etag = "\"v1\""),
        )

        assertEquals(HttpResumeDecision.Append(1000L), decision)
    }

    @Test
    fun mismatchedOrMalformed206NeverAppends() {
        val wrongStart = decide(response = response(206, "bytes 401-999/1000", 599L))
        val malformed = decide(response = response(206, "bytes nope", 600L))
        val wrongBodyLength = decide(response = response(206, "bytes 400-999/1000", 599L))

        assertTrue(wrongStart is HttpResumeDecision.Reject)
        assertTrue(malformed is HttpResumeDecision.Reject)
        assertTrue(wrongBodyLength is HttpResumeDecision.Reject)
    }

    @Test
    fun changedValidatorNeverAppends() {
        val decision = decide(response = response(206, "bytes 400-999/1000", 600L, etag = "\"v2\""))

        assertEquals(
            TransferFailure.RepresentationMismatch,
            (decision as HttpResumeDecision.Reject).failure,
        )
    }

    @Test
    fun noSafeValidatorNeverAllowsDurableAppend() {
        val decision = decide(
            persisted = HttpRepresentationState(weakETag = "W/\"v1\"", totalLength = 1000L),
            response = response(206, "bytes 400-999/1000", 600L),
        )

        assertTrue(decision is HttpResumeDecision.Reject)
    }

    @Test
    fun rangedRequestReturning200ReplacesRepresentation() {
        val decision = decide(response = response(200, contentLength = 1000L, etag = "\"v2\""))

        assertEquals("\"v2\"", (decision as HttpResumeDecision.Replace).representation.strongETag)
        assertEquals(1000L, decision.representation.totalLength)
    }

    @Test
    fun safe416RequiresExactLocalAndKnownLengths() {
        val safe = decide(response = response(416, "bytes */1000", 0L), localLength = 1000L, requestedOffset = 1000L)
        val oversized = decide(response = response(416, "bytes */1000", 0L), localLength = 1001L, requestedOffset = 1001L)
        val malformed = decide(response = response(416, "bytes 1000-1000/1000", 0L), localLength = 1000L, requestedOffset = 1000L)

        assertEquals(HttpResumeDecision.AlreadyComplete, safe)
        assertTrue(oversized is HttpResumeDecision.Reject)
        assertTrue(malformed is HttpResumeDecision.Reject)
    }

    @Test
    fun typedFailureRemainsSameSourceAndCannotAdvancePlan() {
        val plan = DownloadSourcePlan(logical, listOf(candidateA, candidateB), selectedCandidateIndex = 0)
        val failure = TransferFailure.UnsafeRangeNotSatisfiable

        assertEquals(RetryDisposition.SAME_SOURCE, failure.retryDisposition)
        assertEquals(0, plan.selectedCandidateIndex)
        assertEquals(candidateA, plan.selectedCandidate)
    }

    @Test
    fun replacementTransitionNeverPublishesNewMetadataOverOldBytes() {
        val directory = Files.createTempDirectory("oz-http-replacement").toFile()
        val target = directory.resolve("game.chd")
        val oldBytes = "representation-v1".toByteArray()
        val oldRecovery = recovery(candidateA, strong)
        val newRepresentation = HttpRepresentationState(strongETag = "\"v2\"", totalLength = 1000L)
        val newRecovery = recovery(candidateA, newRepresentation)
        var durableMetadata = oldRecovery
        target.writeBytes(oldBytes)

        try {
            val prepared = prepareHttpReplacementTarget(target)

            // Simulated death after filesystem generation reset, before metadata transition.
            assertEquals(oldRecovery, durableMetadata)
            assertEquals(0L, prepared.activeTarget.length())
            assertTrue(prepared.preservedPrevious!!.readBytes().contentEquals(oldBytes))

            durableMetadata = newRecovery

            // Simulated death after metadata transition, before the V2 response body is written.
            assertEquals(newRecovery, durableMetadata)
            assertEquals(0L, target.length())
            assertFalse(target.readBytes().contentEquals(oldBytes))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun biosLogicalIdentityUsesParentDurableTaskOwner() {
        val biosPlan = DownloadSourcePlan.single(
            LogicalDownloadIdentity.forTask("romgi-bios-neogeo", null, "neogeo.zip", 1000L),
            source("https://example.invalid/neogeo.zip").copy(expectedFileName = "neogeo.zip"),
        )
        val scope = TransferRecoveryScope(
            durableTaskGameId = "parent-arcade-game",
            sourcePlan = biosPlan,
        )

        assertEquals("parent-arcade-game", scope.durableTaskGameId)
        assertEquals(biosPlan.logicalIdentity, scope.sourcePlan.logicalIdentity)
        assertNotEquals(
            LogicalDownloadIdentity.forTask("parent-arcade-game", null, "neogeo.zip", 1000L),
            scope.sourcePlan.logicalIdentity,
        )
    }

    @Test
    fun persistingOneRecoveryCannotClearOtherUnsafeLegacyArtifacts() {
        val snapshot = snapshot(unsafeLegacyPartial = true)

        val updated = snapshot.withPersistedHttpRecovery(recovery(candidateA, strong), updatedAt = 2L)

        assertTrue(updated.unsafeLegacyPartial)
        assertEquals(1, updated.httpRecoveries.size)
    }

    private fun decide(
        response: HttpResponseEvidence,
        persisted: HttpRepresentationState = strong,
        requestedOffset: Long = 400L,
        localLength: Long = requestedOffset,
    ) = HttpResumePolicy.decide(requestedOffset, localLength, 1000L, persisted, response)

    private fun response(
        status: Int,
        contentRange: String? = null,
        contentLength: Long = -1L,
        etag: String? = null,
        lastModified: String? = null,
        date: String? = null,
    ) = HttpResponseEvidence(status, contentRange, contentLength, etag, lastModified, date, "https://cdn.invalid/game.chd")

    private fun owner(candidate: DownloadSourceCandidate) = HttpPartialOwner(logical.value, candidate.identity)

    private fun recovery(candidate: DownloadSourceCandidate, representation: HttpRepresentationState) =
        HttpCandidateRecovery(
            owner(candidate),
            candidatePartialRelativePath(owner(candidate), "game.chd"),
            representation,
        )

    private fun snapshot(unsafeLegacyPartial: Boolean) = RomDownloadTaskSnapshot(
        taskId = "task",
        gameId = "game",
        source = candidateA.source,
        downloadSet = null,
        biosSources = emptyList(),
        resolvedRegion = "USA",
        externalLauncher = false,
        stagingId = "staging",
        status = RomDownloadStatus.RUNNING,
        stage = "Downloading",
        downloaded = 0L,
        total = 1000L,
        bytesPerSecond = 0L,
        peers = 0,
        fileName = "game.chd",
        error = null,
        storedUri = null,
        storedDisplayName = null,
        startedAt = 1L,
        updatedAt = 1L,
        sourcePlan = DownloadSourcePlan.single(logical, candidateA.source),
        unsafeLegacyPartial = unsafeLegacyPartial,
    )

    private fun source(url: String) = cl.gakuto.kizuna.data.AuthorizedSource(
        value = url,
        expectedFileName = "game.chd",
        expectedSize = 1000L,
    )
}

package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.data.AuthorizedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadSourcePlanTest {
    private fun source(
        value: String,
        name: String = "game.chd",
        label: String? = null,
    ) = AuthorizedSource(
        value = value,
        expectedFileName = name,
        expectedSize = 4096L,
        sourceLabel = label,
    )

    @Test
    fun authorizedSourceBecomesOneStableCandidate() {
        val source = source("https://example.invalid/game.chd")
        val first = DownloadSourceCandidate.from(source)
        val second = DownloadSourceCandidate.from(source.copy(sourceLabel = "Display only"))
        val plan = DownloadSourcePlan.single(LogicalDownloadIdentity.from("test", "game", "USA", "CHD"), source)

        assertEquals(first.identity, second.identity)
        assertEquals(listOf(first.identity), plan.candidates.map { it.identity })
        assertEquals(source, plan.selectedSource)
    }

    @Test
    fun transportProvenanceChangesCandidateIdentity() {
        val first = DownloadSourceCandidate.from(source("https://one.invalid/game.chd"))
        val second = DownloadSourceCandidate.from(source("https://two.invalid/game.chd"))

        assertNotEquals(first.identity, second.identity)
    }

    @Test
    fun httpAndTorrentCandidatesCanShareOneLogicalPayloadIdentity() {
        val http = source("https://example.invalid/game.chd")
        val torrent = http.copy(
            value = "magnet:?xt=urn:btih:0123456789abcdef",
            torrentFileIndex = 4,
            torrentFilePath = "Game (USA)/game.chd",
        )

        val httpIdentity = LogicalDownloadIdentity.forTask("game-id", "USA", http.expectedFileName, http.expectedSize)
        val torrentIdentity = LogicalDownloadIdentity.forTask(
            "game-id",
            "USA",
            torrent.expectedFileName,
            torrent.expectedSize,
        )

        assertEquals(httpIdentity, torrentIdentity)
        assertNotEquals(DownloadSourceCandidate.from(http).identity, DownloadSourceCandidate.from(torrent).identity)
    }

    @Test
    fun orderedPlanKeepsSelectionWithoutAutomaticAdvance() {
        val first = DownloadSourceCandidate.from(source("https://one.invalid/game.chd"))
        val second = DownloadSourceCandidate.from(source("https://two.invalid/game.chd"))
        val plan = DownloadSourcePlan(
            logicalIdentity = LogicalDownloadIdentity.from("test", "game", "USA", "CHD"),
            candidates = listOf(first, second),
        )

        assertEquals(listOf(first.identity, second.identity), plan.candidates.map { it.identity })
        assertEquals(0, plan.selectedCandidateIndex)
        assertEquals(first.source, plan.selectedSource)
    }

    @Test
    fun orderedPlanSurvivesPersistenceProjectionRoundTrip() {
        val first = DownloadSourceCandidate.from(source("https://one.invalid/game.chd"))
        val second = DownloadSourceCandidate.from(source("https://two.invalid/game.chd"))
        val original = DownloadSourcePlan(
            logicalIdentity = LogicalDownloadIdentity.from("test", "game", "USA", "CHD"),
            candidates = listOf(first, second),
            selectedCandidateIndex = 1,
        )

        val restored = original.toStoredSourcePlan().toSourcePlan()

        assertEquals(original, restored)
        assertEquals(listOf(first.identity, second.identity), restored.candidates.map { it.identity })
        assertEquals(second.source, restored.selectedSource)
    }

    @Test
    fun localManualSourceRemainsAValidSingleSourcePlan() {
        val source = source("content://library/game.chd")
        val plan = DownloadSourcePlan.single(LogicalDownloadIdentity.from("manual", "game.chd"), source)

        assertEquals(source, plan.selectedSource)
        assertEquals(1, plan.candidates.size)
    }

    @Test
    fun legacySourceCannotDivergeFromSelectedPlanSource() {
        val selected = source("https://one.invalid/game.chd")
        val different = source("https://two.invalid/game.chd")
        val plan = DownloadSourcePlan.single(LogicalDownloadIdentity.from("test", "game"), selected)

        assertThrows(IllegalArgumentException::class.java) {
            RomDownloadSetItem(1, "Disc 1", different, plan)
        }
    }

    @Test
    fun sourceIndexProvenanceSurvivesPlanRoundTrip() {
        val provenance = SourceIndexProvenance(
            gameId = "game",
            releaseId = "release",
            candidateId = "candidate",
            provider = "minerva",
            infoHash = "0123456789abcdef",
            fileIndex = 7,
            exactFullPath = "Game (USA).zip",
            expectedSize = 1234L,
        )
        val original = DownloadSourcePlan.single(
            LogicalDownloadIdentity.from("source-index", "release"),
            source("magnet:?xt=urn:btih:0123456789abcdef", "Game.zip"),
        ).copy(sourceIndexProvenance = provenance)

        assertEquals(original, original.toStoredSourcePlan().toSourcePlan())
        assertEquals("candidate", original.sourceIndexProvenance?.candidateId)
    }

    @Test
    fun localReleaseKeyIsUnambiguousAndStable() {
        val first = localReleaseRecordKey("ab", "c")
        val second = localReleaseRecordKey("a", "bc")
        assertNotEquals(first, second)
        assertEquals(first, localReleaseRecordKey("ab", "c"))
    }

    @Test
    fun physicalDeletionRequiresExplicitOzOwnership() {
        assertEquals(true, isPhysicalDeletionAuthorized(PersistentRomLibrary.LocalReleaseOwnership.OZ_OWNED))
        assertEquals(false, isPhysicalDeletionAuthorized(PersistentRomLibrary.LocalReleaseOwnership.LEGACY_UNKNOWN))
    }
}

package cl.gakuto.kizuna.catalog.sourceindex

import cl.gakuto.kizuna.data.LibraryOrigin
import cl.gakuto.kizuna.util.CatalogFilterState
import cl.gakuto.kizuna.util.CatalogSort
import cl.gakuto.kizuna.util.OriginStatusFilter
import cl.gakuto.kizuna.util.RegionMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnesSourceIndexCatalogTest {
    private val reader by lazy(::frozenReader)

    @Test fun controlledShardExposesEveryLogicalGameWithoutDuplicates() {
        assertEquals(2_505, reader.gameCount)
        assertEquals(4_764, reader.releaseCount)
        assertEquals(4_874, reader.candidateCount)
        assertEquals(2_505, reader.games().map(SourceIndexGame::gameId).distinct().size)
    }

    @Test fun exactNormalizedAndUnknownSearchBehaveDeterministically() {
        val exact = reader.searchGames("240p test suite")
        val normalized = reader.searchGames("240P   TEST SUITE".lowercase().replace(Regex("\\s+"), " "))
        assertEquals(listOf(SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID), exact.map(SourceIndexGame::gameId))
        assertEquals(exact, normalized)
        assertTrue(reader.searchGames("definitely-not-a-real-snes-title").isEmpty())
    }

    @Test fun realRetailRegionalAndMultiReleaseSamplesRemainDistinct() {
        val sos = requireNotNull(reader.game(SOS_GAME_ID))
        val japan = requireNotNull(reader.game(JAPAN_GAME_ID))
        val multi = requireNotNull(reader.game(MULTI_GAME_ID))
        assertEquals("USA", reader.releasesForGame(sos.gameId).single().region)
        val japanRelease = reader.releasesForGame(japan.gameId).single()
        assertEquals("Japan", japanRelease.region)
        assertEquals("UNKNOWN", japanRelease.facets.regionalExclusivity.status)
        assertEquals(3, reader.releasesForGame(multi.gameId).size)
        assertEquals(3, reader.releasesForGame(multi.gameId).map(SourceIndexRelease::releaseId).distinct().size)
    }

    @Test fun releaseAndCandidateEvidenceDoNotMutateStableIds() {
        val release = reader.releasesForGame(MULTI_GAME_ID).first()
        val enriched = release.copy(
            region = "Changed display evidence",
            facets = release.facets.copy(publicationClass = "PROTOTYPE"),
        )
        assertEquals(release.releaseId, enriched.releaseId)
        val candidate = reader.candidatesForRelease(SOS_RELEASE_ID).single()
        val churned = candidate.copy(providerItemId = "changed", trackerUrls = listOf("udp://changed.invalid"))
        assertEquals(candidate.candidateId, churned.candidateId)
    }

    @Test fun oneExplicitCandidateEntersPlanAndFirstPlayRegressionIsExact() {
        val projection = SnesSourceIndexCatalog.project(
            reader,
            requireNotNull(reader.game(SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID)),
        )
        val selection = SnesSourceIndexCatalog.resolve(
            reader,
            projection,
            preferredRegion = "usa",
            releaseId = SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID,
            candidateId = SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID,
        )
        assertEquals(SnesSourceIndexCatalog.FIRST_PLAY_RELEASE_ID, selection.release.releaseId)
        assertEquals(SnesSourceIndexCatalog.FIRST_PLAY_CANDIDATE_ID, selection.candidate.candidateId)
        assertEquals(1, selection.sourcePlan.candidates.size)
        assertEquals(0, selection.sourcePlan.selectedCandidateIndex)
        assertEquals(6, selection.sourcePlan.selectedSource.torrentFileIndex)
        assertTrue(selection.sourcePlan.selectedSource.torrentFilePath!!.endsWith("240p Test Suite (World) (v1.061) (NTSC) (Program) (Aftermarket) (Unl).zip"))
    }

    @Test fun meaningfulFirstPlayReleaseChoicesRemainVisiblyDistinct() {
        val releases = reader.releasesForGame(SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID)
        val labels = releases.map(SnesSourceIndexCatalog::releaseLabel)
        assertEquals(4, releases.size)
        assertEquals(4, labels.distinct().size)
        assertTrue(labels.any { "NTSC" in it && "v1.061" in it })
        assertTrue(labels.any { "PAL" in it && "v1.061" in it })
    }

    @Test fun discoveryIsNotDownloadedAndExactRoomOverlayWinsWithoutMutation() {
        val projection = SnesSourceIndexCatalog.project(reader, requireNotNull(reader.game(SOS_GAME_ID)))
        assertNull(projection.contentUri)
        assertEquals(LibraryOrigin.NONE.name, projection.libraryOrigin)
        val local = projection.copy(contentUri = "content://library/sos.sfc", libraryOrigin = LibraryOrigin.DOWNLOADED.name)
        val merged = SnesSourceIndexCatalog.mergeOverlays(listOf(projection), mapOf(projection.id to local)).single()
        assertEquals(local, merged)
        assertFalse(projection == merged)
        assertNull(projection.contentUri)
    }

    @Test fun publicationClassNeverPollutesUnknownGenre() {
        val aftermarket = SnesSourceIndexCatalog.project(
            reader,
            requireNotNull(reader.game(SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID)),
        )
        val retail = SnesSourceIndexCatalog.project(reader, requireNotNull(reader.game(SOS_GAME_ID)))
        assertEquals(SnesSourceIndexCatalog.UNKNOWN_GENRE, aftermarket.genre)
        assertEquals(SnesSourceIndexCatalog.UNKNOWN_GENRE, retail.genre)
        assertFalse(aftermarket.genre.equals("Aftermarket", ignoreCase = true))
        assertFalse(retail.genre.equals("Retail", ignoreCase = true))
    }

    @Test fun unsupportedSourceIndexFiltersAreRemovedRatherThanIgnored() {
        val requested = CatalogFilterState(
            genres = setOf("action"),
            status = setOf(OriginStatusFilter.FAVORITES, OriginStatusFilter.DOWNLOADED),
            sort = CatalogSort.YEAR_DESC,
        )
        val supported = SnesSourceIndexCatalog.supportedFilters(requested)
        assertTrue(supported.genres.isEmpty())
        assertTrue(supported.status.isEmpty())
        assertTrue(supported.platformFamilies.isEmpty())
        assertTrue(supported.systemIds.isEmpty())
        assertEquals(CatalogSort.NAME_ASC, supported.sort)
        assertEquals(requested.regions, supported.regions)
        assertEquals(CatalogSort.NAME_DESC, SnesSourceIndexCatalog.supportedFilters(requested.copy(sort = CatalogSort.NAME_DESC)).sort)
    }

    @Test fun supportedRegionContractUsesReleaseEvidence() {
        val japanReleases = reader.releasesForGame(JAPAN_GAME_ID)
        assertTrue(SnesSourceIndexCatalog.matchesRegion(japanReleases, setOf(RegionMode.JAPAN)))
        assertFalse(SnesSourceIndexCatalog.matchesRegion(japanReleases, setOf(RegionMode.USA)))
        assertTrue(SnesSourceIndexCatalog.matchesRegion(japanReleases, setOf(RegionMode.ALL)))
    }

    private fun frozenReader(): OzSourceIndexReader {
        val candidates = listOf(
            File("src/main/assets/source-index/snes.json.gz.bin"),
            File("app/src/main/assets/source-index/snes.json.gz.bin"),
        )
        val shard = requireNotNull(candidates.firstOrNull(File::isFile)) { "Frozen SNES shard not found" }
        return shard.inputStream().use { OzSourceIndexReader.openGzip(it, SnesSourceIndexCatalog.SHARD_SHA256) }
    }

    companion object {
        const val SOS_GAME_ID = "8264135ad5cd61a958bfb583c5a48357f6ff49de55ce70761ac2c9e9cfc7394d"
        const val SOS_RELEASE_ID = "0041a9494f821a55e9bddaed11aa0c704e4a17049bfea03ddafff6dec4414a6b"
        const val JAPAN_GAME_ID = "fefe94c36523d1d7d19a55a9bdc23891619fe1835271c48b5db6a23bc85e579f"
        const val MULTI_GAME_ID = "29e2f05f8c5c299148328e7f35dd627b15650c9ead5ee96a38b1d9a0202fbc87"
    }
}

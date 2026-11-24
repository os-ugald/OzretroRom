package cl.gakuto.kizuna.catalog.sourceindex

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NesSourceIndexCatalogTest {
    private val reader by lazy {
        val shard = listOf(
            File("src/main/assets/source-index/nes.json.gz.bin"),
            File("app/src/main/assets/source-index/nes.json.gz.bin"),
        ).first(File::isFile)
        shard.inputStream().use { OzSourceIndexReader.openGzip(it, NesSourceIndexCatalog.SHARD_SHA256) }
    }

    @Test fun controlledHeaderedShardIsCompleteAndDeterministic() {
        assertEquals("nes", reader.platform)
        assertEquals(4_415, reader.gameCount)
        assertEquals(8_894, reader.releaseCount)
        assertEquals(10_057, reader.candidateCount)
        assertEquals(reader.gameCount, reader.games().map(SourceIndexGame::gameId).distinct().size)
    }

    @Test fun projectionPreservesNesDomainAndPhysicalLaunchEvidence() {
        val game = reader.games().first()
        val projection = NesSourceIndexCatalog.project(reader, game)
        assertEquals("nes", projection.systemId)
        assertEquals("oz-source-index", projection.source)
        assertEquals("Nintendo Entertainment System", projection.platform)
        assertEquals("nes", projection.virtualExtension)
        assertEquals("mesen_libretro_android.so", projection.coreId)
        assertTrue(projection.launchValidated)
        assertTrue(projection.id.startsWith("oz-source-index-nes-game-"))
    }

    @Test fun headerEvidenceAndSingleCandidatePlanRemainExact() {
        val game = reader.games().first { item -> reader.releasesForGame(item.gameId).isNotEmpty() }
        val projection = NesSourceIndexCatalog.project(reader, game)
        val selection = NesSourceIndexCatalog.resolve(reader, projection, preferredRegion = "usa")
        assertEquals("headered", selection.release.headerState)
        assertEquals(1, selection.sourcePlan.candidates.size)
        assertEquals(0, selection.sourcePlan.selectedCandidateIndex)
        assertTrue(selection.sourcePlan.selectedSource.value.contains(selection.candidate.infoHash))
        assertEquals(selection.candidate.fileIndex, selection.sourcePlan.selectedSource.torrentFileIndex)
        assertEquals(selection.candidate.exactFullPath, selection.sourcePlan.selectedSource.torrentFilePath)
    }
}

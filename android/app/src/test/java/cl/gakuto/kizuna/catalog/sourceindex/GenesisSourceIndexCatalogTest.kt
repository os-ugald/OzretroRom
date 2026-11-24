package cl.gakuto.kizuna.catalog.sourceindex

import cl.gakuto.kizuna.data.LibraryOrigin
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisSourceIndexCatalogTest {
    private val reader by lazy {
        val shard = listOf(
            File("src/main/assets/source-index/genesis.json.gz.bin"),
            File("app/src/main/assets/source-index/genesis.json.gz.bin"),
        ).first(File::isFile)
        shard.inputStream().use { OzSourceIndexReader.openGzip(it, GenesisSourceIndexCatalog.SHARD_SHA256) }
    }

    @Test fun controlledShardIdentityAndCountsAreExact() {
        assertEquals("genesis", reader.platform)
        assertEquals(1_918, reader.gameCount)
        assertEquals(4_729, reader.releaseCount)
        assertEquals(5_036, reader.candidateCount)
        assertEquals(reader.gameCount, reader.games().map(SourceIndexGame::gameId).distinct().size)
    }

    @Test fun projectionUsesSharedGenesisConfigurationWithPhysicalLaunchEvidence() {
        val projection = GenesisSourceIndexCatalog.project(reader, reader.games().first())
        assertEquals("genesis", projection.systemId)
        assertEquals("Mega Drive / Genesis", projection.platform)
        assertEquals("oz-source-index", projection.source)
        assertEquals("md", projection.virtualExtension)
        assertEquals("genesis_plus_gx_libretro_android.so", projection.coreId)
        assertTrue(projection.launchValidated)
        assertTrue(projection.id.startsWith("oz-source-index-genesis-game-"))
        assertSame(GenesisSourceIndexCatalog, SourceIndexCatalogs.forSystem("genesis"))
        assertNull(SourceIndexCatalogs.forSystem("sega-32x"))
        assertSame(GenesisSourceIndexCatalog, SourceIndexCatalogs.acquisitionForProjection(projection))
        assertEquals(UnresolvedGameSourceRoute.SOURCE_INDEX, SourceIndexCatalogs.unresolvedSourceRoute(projection, romgiEnabled = true))
    }

    @Test fun exact240pProgramCandidateResolvesWithoutPayloadAccess() {
        val game = requireNotNull(reader.game(FIRST_PLAY_GAME_ID))
        val projection = GenesisSourceIndexCatalog.project(reader, game)
        val selection = GenesisSourceIndexCatalog.resolve(
            reader, projection, preferredRegion = "world",
            releaseId = FIRST_PLAY_RELEASE_ID, candidateId = FIRST_PLAY_CANDIDATE_ID,
        )
        assertEquals(FIRST_PLAY_RELEASE_ID, selection.release.releaseId)
        assertEquals(FIRST_PLAY_CANDIDATE_ID, selection.candidate.candidateId)
        assertEquals("cf45a4f757253f0c44931d353cbc95bc3c6ef4f9", selection.candidate.infoHash)
        assertEquals(7, selection.candidate.fileIndex)
        assertEquals(82_432L, selection.candidate.artifactSize)
        assertTrue(selection.candidate.exactFullPath.endsWith("240p Test Suite (World) (v1.30) (Program) (Aftermarket) (Unl).zip"))
        assertEquals(1, selection.sourcePlan.candidates.size)
        assertEquals(0, selection.sourcePlan.selectedCandidateIndex)
        assertEquals(FIRST_PLAY_CANDIDATE_ID, selection.sourcePlan.selectedCandidate.identity)
    }

    @Test fun regionalNamesAndRevisionsRemainProvisionalAndUnmerged() {
        val contra = reader.searchGames("contra - hard corps").single()
        val probotector = reader.searchGames("probotector").single()
        assertFalse(contra.gameId == probotector.gameId)
        val sonic = reader.searchGames("sonic the hedgehog 2").single { it.canonicalTitleKey == "sonic the hedgehog 2" }
        assertTrue(reader.releasesForGame(sonic.gameId).map(SourceIndexRelease::releaseId).distinct().size > 1)
        assertFalse(FIRST_PLAY_GAME_ID == SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID)
    }

    @Test fun discoveryProjectionIsNotLocalAndExactOverlayWins() {
        val projection = GenesisSourceIndexCatalog.project(reader, reader.games().first())
        assertNull(projection.contentUri)
        assertEquals(LibraryOrigin.NONE.name, projection.libraryOrigin)
        val local = projection.copy(contentUri = "content://library/game.md", libraryOrigin = LibraryOrigin.DOWNLOADED.name)
        assertEquals(local, GenesisSourceIndexCatalog.mergeOverlays(listOf(projection), mapOf(projection.id to local)).single())
    }

    companion object {
        const val FIRST_PLAY_GAME_ID = "393411b9ec7e074469ac08acf21ba92c66a47ba7decd74b0d51fb5d54e4d60a8"
        const val FIRST_PLAY_RELEASE_ID = "f7e52049909d969559d78a4337cf981e92c138e039a365738fd1e19c0565e46c"
        const val FIRST_PLAY_CANDIDATE_ID = "9deae8309b64342026dba31215ba28df37ba3c049884e53398b21580a6dd0dcd"
    }
}

package cl.gakuto.kizuna.catalog.sourceindex

import cl.gakuto.kizuna.data.LibraryOrigin
import cl.gakuto.kizuna.util.TextNormalizer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GbaSourceIndexCatalogTest {
    private val reader by lazy {
        val shard = listOf(
            File("src/main/assets/source-index/gba.json.gz.bin"),
            File("app/src/main/assets/source-index/gba.json.gz.bin"),
        ).first(File::isFile)
        shard.inputStream().use { OzSourceIndexReader.openGzip(it, GbaSourceIndexCatalog.SHARD_SHA256) }
    }

    @Test fun productionShardCountsAndSearchAreExact() {
        assertEquals("gba", reader.platform)
        assertEquals(2_368, reader.gameCount)
        assertEquals(3_962, reader.releaseCount)
        assertEquals(4_021, reader.candidateCount)
        assertEquals(1, reader.searchGames(TextNormalizer.normalize("Hajime no Ippo")).size)
        assertTrue(reader.searchGames("not-a-real-gba-title").isEmpty())
    }

    @Test fun projectionKeepsTranslationAndPrototypeFacetsAndDisablesGuards() {
        val translation = requireNotNull(reader.game("dc95ebaef4fd03d721805a74562f58f23bdbe35bbacc2991104ff34d411427d2"))
        val translationRelease = reader.releasesForGame(translation.gameId)
            .single { it.facets.modificationClass == "TRANSLATION" }
        assertEquals("TRANSLATION", translationRelease.facets.modificationClass)
        assertEquals(1, reader.candidatesForRelease(translationRelease.releaseId).size)
        assertFalse(GbaSourceIndexCatalog.project(reader, translation).launchValidated)
        assertFalse(GbaSourceIndexCatalog.acquisitionEnabled(GbaSourceIndexCatalog.project(reader, translation)))
        assertNull(SourceIndexCatalogs.acquisitionForProjection(GbaSourceIndexCatalog.project(reader, translation)))
        assertTrue(GbaSourceIndexCatalog.releaseLabel(translationRelease).contains("Translation"))

        val prototype = reader.games().asSequence()
            .flatMap { reader.releasesForGame(it.gameId).asSequence() }
            .first { it.facets.publicationClass == "PROTOTYPE" }
        assertEquals("PROTOTYPE", prototype.facets.publicationClass)
    }

    @Test fun projectionIsRoomOverlayOnly() {
        val projection = GbaSourceIndexCatalog.project(reader, reader.games().first())
        assertNull(projection.contentUri)
        assertEquals(LibraryOrigin.NONE.name, projection.libraryOrigin)
        val local = projection.copy(contentUri = "content://library/game.gba", libraryOrigin = LibraryOrigin.DOWNLOADED.name)
        assertEquals(local, GbaSourceIndexCatalog.mergeOverlays(listOf(projection), mapOf(projection.id to local)).single())
        assertNull(projection.contentUri)
    }
}

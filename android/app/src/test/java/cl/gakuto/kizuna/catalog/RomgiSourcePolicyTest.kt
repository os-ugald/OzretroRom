package cl.gakuto.kizuna.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomgiSourcePolicyTest {
    @Test
    fun concreteFranceFileCannotInheritWorldFromCatalogLabels() {
        val region = RomgiSourcePolicy.detectConcreteRegion(
            fileName = "Resident Evil (France).zip",
            torrentFilePath = "Sony PlayStation/Resident Evil (France).zip",
            url = "magnet:?xt=urn:btih:test",
            linkName = "Resident Evil (World)",
            entryTitle = "Resident Evil (World)",
        )

        assertEquals("Europe", region)
    }

    @Test
    fun exactWorldFileRemainsWorld() {
        assertEquals(
            "World",
            RomgiSourcePolicy.detectConcreteRegion(
                fileName = "Resident Evil (World).chd",
                torrentFilePath = null,
                url = "https://archive.org/download/item/Resident%20Evil%20(World).chd",
                linkName = "Resident Evil",
                entryTitle = "Resident Evil (USA)",
            ),
        )
    }

    @Test
    fun directHttpsRejectsTorrentManifests() {
        assertTrue(RomgiSourcePolicy.isDirectHttps("https://archive.org/download/item/game.chd"))
        assertFalse(RomgiSourcePolicy.isDirectHttps("https://archive.org/download/item/item_archive.torrent"))
        assertFalse(RomgiSourcePolicy.isDirectHttps("magnet:?xt=urn:btih:test"))
    }
}

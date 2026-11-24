package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogIndexMergePolicyTest {
    @Test
    fun resumedIndexKeepsDownloadedAndPlaybackState() {
        val old = game(title = "Old metadata").copy(
            contentUri = "content://roms/game.chd",
            lastPlayedAt = 1234L,
            addedAt = 5678L,
            romgiAvailable = true,
            libraryOrigin = LibraryOrigin.DOWNLOADED.name,
        )
        val refreshed = CatalogIndexMergePolicy.merge(game(title = "Correct title"), old)

        assertEquals("Correct title", refreshed.title)
        assertEquals(old.contentUri, refreshed.contentUri)
        assertEquals(old.lastPlayedAt, refreshed.lastPlayedAt)
        assertEquals(old.addedAt, refreshed.addedAt)
        assertEquals(old.romgiAvailable, refreshed.romgiAvailable)
        assertEquals(old.libraryOrigin, refreshed.libraryOrigin)
    }

    @Test
    fun firstIndexUsesCleanRuntimeDefaults() {
        val indexed = CatalogIndexMergePolicy.merge(game(), null)
        assertNull(indexed.contentUri)
        assertEquals(LibraryOrigin.NONE.name, indexed.libraryOrigin)
    }

    private fun game(title: String = "Game") = GameEntity(
        id = "libretro-ps1-game",
        title = title,
        normalizedTitle = title.lowercase(),
        platform = "Sony PlayStation",
        systemId = "ps1",
        year = 1998,
        region = "USA",
        thumbnailRepo = "Sony - PlayStation",
        source = "libretro",
        launchValidated = true,
        coreId = "pcsx_rearmed",
        virtualExtension = "chd",
        canonicalTitle = "game",
    )
}

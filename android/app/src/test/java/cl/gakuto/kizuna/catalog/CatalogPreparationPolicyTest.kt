package cl.gakuto.kizuna.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPreparationPolicyTest {
    @Test
    fun freshSystemNeedsIndexEvenWithoutState() {
        assertTrue(CatalogPreparationPolicy.needsIndex("snes", null, 0))
    }

    @Test
    fun interruptedSystemRemainsPendingWithoutDeletingItsGames() {
        assertTrue(CatalogPreparationPolicy.needsIndex("ps1", "CANCELLED", 1_250))
    }

    @Test
    fun readySystemIsSkippedWhenCompletePreparationResumes() {
        assertFalse(CatalogPreparationPolicy.needsIndex("dreamcast", "READY", 1_100))
    }

    @Test
    fun fbneoRequiresRomSetIdentityForEveryIndexedGame() {
        assertTrue(
            CatalogPreparationPolicy.needsIndex(
                CatalogPreparationPolicy.FBNEO_SYSTEM_ID,
                "READY",
                indexedGames = 5_000,
                arcadeGamesWithRomSet = 4_999,
            ),
        )
        assertFalse(
            CatalogPreparationPolicy.needsIndex(
                CatalogPreparationPolicy.FBNEO_SYSTEM_ID,
                "READY",
                indexedGames = 5_000,
                arcadeGamesWithRomSet = 5_000,
            ),
        )
    }
}

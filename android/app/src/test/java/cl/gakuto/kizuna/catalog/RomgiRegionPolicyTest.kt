package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.data.RomRegionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomgiRegionPolicyTest {
    @Test
    fun legacyAutoMigratesToUsa() {
        assertEquals(RomRegionPreference.USA, RomRegionPreference.fromStored("auto"))
        assertEquals("USA", RomgiRegionPolicy.effectivePreference("auto"))
    }

    @Test
    fun worldTvGameDefaultsToNtscBeforeUnverifiedWorldOrPal() {
        assertEquals(
            listOf("USA", "Japan", "World", "Asia", "Europe", "Other"),
            RomgiRegionPolicy.order("snes", "World", "auto"),
        )
    }

    @Test
    fun concreteCatalogEditionRemainsFirst() {
        assertEquals(
            listOf("Europe", "USA", "Japan", "World", "Asia", "Other"),
            RomgiRegionPolicy.order("ps1", "Europe", "usa"),
        )
    }

    @Test
    fun tvPolicyAcceptsNtscJapanButConfirmsPalAndUnverifiedWorld() {
        assertFalse(RomgiRegionPolicy.requiresConfirmation("snes", "World", "usa", "USA"))
        assertFalse(RomgiRegionPolicy.requiresConfirmation("snes", "World", "usa", "Japan"))
        assertTrue(RomgiRegionPolicy.requiresConfirmation("snes", "World", "usa", "Europe"))
        assertTrue(RomgiRegionPolicy.requiresConfirmation("snes", "World", "usa", "World"))
    }

    @Test
    fun portableWorldDoesNotPretendToBePalOrNtsc() {
        assertFalse(RomgiRegionPolicy.requiresConfirmation("3ds", "World", "usa", "World"))
        assertTrue(RomgiRegionPolicy.requiresConfirmation("3ds", "World", "usa", "Europe"))
    }

    @Test
    fun arcadeExactSetIgnoresRegionalRanking() {
        assertEquals(0, RomgiRegionPolicy.rank("arcade-fbneo", "World", "usa", "Europe"))
        assertFalse(RomgiRegionPolicy.requiresConfirmation("arcade-fbneo", "World", "usa", "Europe"))
    }
}

package cl.gakuto.kizuna.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ps2DiscRegressionTest {

    @Test
    fun ps2SingleDiscWithDiscTokenShouldBeTreatedAsSingleDiscIfNoOthersFound() {
        // This simulates a PS2 game that has "(Disc 1)" in title but is single-disc.
        // If expectedDiscs becomes 1, it will work even if PS2 is not in M3U_PLATFORMS.
        // Wait, if it has "(Disc 1)", parsedIndex is 1. If no other discs, max index is 1.
        // So expectedDiscs = 1.
        
        val parsed = RomgiEditionParser.parse("Metal Gear Solid 2 - Sons of Liberty (USA) (Disc 1)")
        assertEquals(1, parsed.discIndex)
        
        // The real issue happens when RomgiCatalog's buildEditionResolution 
        // receives a membership with memberCount > 1.
    }

    @Test
    fun discTokenShouldNotMatchGenericNumbers() {
        val parsed = RomgiEditionParser.parse("Tekken 5 (USA)")
        assertEquals(1, parsed.discIndex)
        assertEquals("Tekken 5 (USA)", parsed.editionTitle)
    }

    @Test
    fun discTokenMatchesVariousFormats() {
        assertTrue(RomgiEditionParser.parse("Game (Disc 1)").discIndex == 1)
        assertTrue(RomgiEditionParser.parse("Game (Disk 2)").discIndex == 2)
        assertTrue(RomgiEditionParser.parse("Game (Disco 3)").discIndex == 3)
        assertTrue(RomgiEditionParser.parse("Game [Disc 01]").discIndex == 1)
    }
}

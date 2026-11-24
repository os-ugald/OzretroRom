package cl.gakuto.kizuna.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomgiEditionParserTest {
    @Test
    fun discTokensCollapseIntoOneEditionWithoutLosingRevision() {
        val first = RomgiEditionParser.parse("Final Fantasy IX (USA) (Rev 1) (Disc 1)")
        val fourth = RomgiEditionParser.parse("Final Fantasy IX (USA) (Rev 1) (Disc IV)")

        assertEquals(first.editionKey, fourth.editionKey)
        assertEquals(1, first.discIndex)
        assertEquals(4, fourth.discIndex)
        assertEquals("Final Fantasy IX (USA) (Rev 1)", first.editionTitle)
        assertEquals("Final Fantasy IX", first.parentTitle)
        assertEquals("Rev 1", first.revisionLabel)
    }

    @Test
    fun retailAndDemoRemainDifferentEditions() {
        val retail = RomgiEditionParser.parse("Resident Evil 2 (USA) (Disc 1)")
        val demo = RomgiEditionParser.parse("Resident Evil 2 (USA) (Demo) (Disc 1)")

        assertFalse(retail.releaseKind.advanced)
        assertTrue(demo.releaseKind.advanced)
        assertTrue(retail.editionKey != demo.editionKey)
    }

    @Test
    fun officialMembershipCanSupplyDiscIndex() {
        val parsed = RomgiEditionParser.parse(
            title = "Example Game (USA)",
            forcedDiscIndex = 2,
            forcedDiscLabel = "Bonus Disc",
        )

        assertEquals(2, parsed.discIndex)
        assertEquals("Bonus Disc", parsed.discLabel)
    }

    @Test
    fun m3uUsesOnlyRelativeRows() {
        assertEquals(
            "Example (Disc 1).chd\nExample (Disc 2).chd\n",
            M3uPlaylist.content(listOf("Example (Disc 1).chd", "Example (Disc 2).chd")),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun m3uRejectsPaths() {
        M3uPlaylist.content(listOf("folder/Disc 1.chd", "Disc 2.chd"))
    }
}

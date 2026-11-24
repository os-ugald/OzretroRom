package cl.gakuto.kizuna.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomgiTitleMatcherTest {
    @Test
    fun resolvesYoshisIslandWorldAcrossPunctuationApostropheWithoutRegionalPenalty() {
        val match = RomgiTitleMatcher.match(
            requestedTitle = "Super Mario World 2: Yoshi’s Island (World)",
            requestedRegion = "World",
            candidateTitle = "Super Mario World 2 - Yoshi's Island (USA)",
            preferredRegion = "USA",
        )

        assertNotNull(match)
        assertEquals(RomMatchStrategy.CANONICAL_TITLE, match?.strategy)
        assertEquals("USA", match?.matchedRegion)
        assertTrue((match?.similarity ?: 0.0) >= 0.99)
    }

    @Test
    fun resolvesReorderedSubtitleWithoutUsingFuzzyMatch() {
        val match = RomgiTitleMatcher.match(
            requestedTitle = "Super Mario World 2: Yoshi's Island",
            requestedRegion = "World",
            candidateTitle = "Yoshi's Island - Super Mario World 2 (Europe)",
            preferredRegion = "Europe",
        )

        assertNotNull(match)
        assertTrue(match?.strategy != RomMatchStrategy.FUZZY_LAST_RESORT)
        assertEquals("Europe", match?.matchedRegion)
    }


    @Test
    fun resolvesConfiguredCatalogAliasBeforeFuzzyFallback() {
        val match = RomgiTitleMatcher.match(
            requestedTitle = "Castlevania IV",
            requestedRegion = "World",
            candidateTitle = "Super Castlevania IV (USA)",
            preferredRegion = "USA",
            requestedAliases = listOf("Super Castlevania IV"),
        )

        assertNotNull(match)
        assertEquals(RomMatchStrategy.ALIAS, match?.strategy)
        assertEquals("USA", match?.matchedRegion)
    }

    @Test
    fun currentSnesPolicyContainsCastlevaniaAndLegacyFinalFantasyAliases() {
        val castlevania = cl.gakuto.kizuna.data.GameDescriptor(
            gameId = "castlevania-iv-snes",
            title = "Castlevania IV",
            platform = "snes",
            platformName = "Super Nintendo",
            platformShort = "snes",
            core = "snes9x_libretro_android.so",
        )
        val ff4 = castlevania.copy(gameId = "final-fantasy-iv-snes", title = "Final Fantasy IV")

        assertTrue("Super Castlevania IV" in RomgiTitleAliasPolicy.aliasesFor(castlevania))
        assertTrue("Final Fantasy II" in RomgiTitleAliasPolicy.aliasesFor(ff4))
    }

    @Test
    fun broadWorldLetsExplicitPreferenceLead() {
        assertEquals(
            listOf("Europe", "World", "USA", "Japan", "Asia", "Other"),
            RomgiTitleMatcher.fallbackOrder("World", "Europe"),
        )
    }

    @Test
    fun concreteMetadataBeatsBroadWorldInsideTheSameName() {
        assertEquals("USA", RomgiTitleMatcher.detectRegion("Yoshi's Island (World) (USA)"))
    }

    @Test
    fun exactSameRegionDoesNotRequireConfirmation() {
        val source = source(matched = "World", requested = "World", fallback = false)
        assertFalse(RomgiResolution(source, emptyList(), ambiguous = false).requiresConfirmation)
    }

    @Test
    fun worldToUsaIsNotARegionalFallback() {
        val source = source(matched = "USA", requested = "World", fallback = false)
        assertFalse(RomgiResolution(source, emptyList(), ambiguous = false).requiresConfirmation)
    }

    @Test
    fun concreteRegionFallbackStillRequiresConfirmation() {
        val source = source(matched = "Europe", requested = "USA", fallback = true)
        assertTrue(RomgiResolution(source, emptyList(), ambiguous = false).requiresConfirmation)
    }

    private fun source(matched: String, requested: String, fallback: Boolean): ResolvedSource = ResolvedSource(
        entrySlug = "actraiser",
        entryTitle = "ActRaiser ($matched)",
        platform = "snes",
        fileName = "ActRaiser ($matched).zip",
        format = "zip",
        url = "https://archive.org/download/item/ActRaiser.zip",
        host = "Internet Archive",
        size = 1L,
        sourceId = "ia",
        torrentInfohash = null,
        torrentFileIndex = null,
        torrentFilePath = null,
        magnetUri = null,
        matchStrategy = if (fallback) RomMatchStrategy.REGIONAL_FALLBACK else RomMatchStrategy.CANONICAL_TITLE,
        matchedRegion = matched,
        requestedRegion = requested,
        confidence = if (fallback) 80 else 95,
        regionFallback = fallback,
    )
}

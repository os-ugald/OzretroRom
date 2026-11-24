package cl.gakuto.kizuna.util

import cl.gakuto.kizuna.data.GameEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogFiltersTest {
    @Test
    fun preferredRegionDeduplicatesButAllKeepsVariants() {
        val games = listOf(
            game("world", "Sunset Riders (World)", "World", "Beat 'em up"),
            game("japan", "Sunset Riders (Japan)", "Japan", "Beat 'em up"),
        )

        assertEquals(1, CatalogFilters.apply(games, regionMode = RegionMode.PREFERRED).size)
        assertEquals(2, CatalogFilters.apply(games, regionMode = RegionMode.ALL).size)
    }

    @Test
    fun compoundGenreAppearsInMoreThanOneFamily() {
        val game = game("guardian", "Guardian Heroes", "World", "Beat em up / RPG")

        assertEquals(1, CatalogFilters.apply(listOf(game), genre = "beat-em-up", regionMode = RegionMode.ALL).size)
        assertEquals(1, CatalogFilters.apply(listOf(game), genre = "rpg", regionMode = RegionMode.ALL).size)
    }

    @Test
    fun worldWordInsideTitleIsNotTreatedAsRegionMetadata() {
        val usa = game("yoshi", "Super Mario World 2: Yoshi's Island", "USA", "Platform")

        assertEquals(0, CatalogFilters.apply(listOf(usa), regionMode = RegionMode.WORLD).size)
        assertEquals(1, CatalogFilters.apply(listOf(usa), regionMode = RegionMode.USA).size)
    }

    @Test
    fun normalCatalogDisplaysOneParentTitleInsteadOfDiscMetadata() {
        assertEquals(
            "Final Fantasy IX",
            CatalogFilters.displayTitle("Final Fantasy IX (USA) (Rev 1) (Disc 1)"),
        )
    }

    private fun game(id: String, title: String, region: String, genre: String) = GameEntity(
        id = id,
        title = title,
        normalizedTitle = TextNormalizer.normalize(title),
        platform = "Arcade / FinalBurn Neo",
        systemId = "arcade-fbneo",
        year = 1991,
        region = region,
        thumbnailRepo = null,
        source = "test",
        launchValidated = true,
        coreId = "fbneo_libretro_android.so",
        virtualExtension = "zip",
        canonicalTitle = CatalogFilters.canonicalTitle(title),
        genre = genre,
    )
}

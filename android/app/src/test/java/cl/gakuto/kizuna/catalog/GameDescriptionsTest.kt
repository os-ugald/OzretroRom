package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.sagas.SagasRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDescriptionsTest {

    private fun game(id: String, title: String, sys: String, genre: String): GameEntity = GameEntity(
        id = id,
        title = title,
        normalizedTitle = SagasRepository.norm(title),
        platform = sys.uppercase(),
        systemId = sys,
        year = 1995,
        region = "USA",
        thumbnailRepo = null,
        source = "CATALOG",
        launchValidated = true,
        coreId = null,
        virtualExtension = "zip",
        contentUri = if (id.startsWith("local")) "content://rom/$id" else null,
        canonicalTitle = title,
        genre = genre,
    )

    @Test
    fun `lookupCurated matches ampersand and subtitle variants cleanly`() {
        val desc = GameDescriptions.lookupCurated("Kirby & The Amazing Mirror (USA) (Rev 1)")
        assertNotNull(desc)
        assertTrue(desc!!.contains("Kirby"))
    }

    @Test
    fun `mood picker ranks fitting games per mood`() {
        val pool = listOf(
            game("local_1", "Chrono Trigger (USA)", "snes", "RPG / JRPG"),
            game("local_2", "Metal Slug (World)", "arcade", "Acción / Run and Gun"),
            game("local_3", "Streets of Rage 2 (USA)", "genesis", "Beat 'em up / Acción"),
            game("cat_4", "Final Fantasy IX (USA)", "ps1", "JRPG / Aventura"),
        )

        val adventurePicks = MoodPickerEngine.pick(pool, GameMood.ADVENTURE, offset = 0, limit = 2)
        assertEquals("local_1", adventurePicks.first().id)

        val actionPicks = MoodPickerEngine.pick(pool, GameMood.ACTION, offset = 0, limit = 2)
        assertTrue(actionPicks.first().id in setOf("local_2", "local_3"))
    }
}

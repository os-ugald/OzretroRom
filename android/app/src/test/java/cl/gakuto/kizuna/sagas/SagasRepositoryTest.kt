package cl.gakuto.kizuna.sagas

import cl.gakuto.kizuna.data.GameEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SagasRepositoryTest {

    private fun fakeGame(
        id: String,
        title: String,
        systemId: String,
        platform: String = systemId.uppercase(),
        year: Int? = null,
        localUri: String? = null,
    ): GameEntity = GameEntity(
        id = id,
        title = title,
        normalizedTitle = SagasRepository.norm(title),
        platform = platform,
        systemId = systemId,
        year = year,
        region = "USA",
        thumbnailRepo = null,
        source = "CATALOG",
        launchValidated = true,
        coreId = null,
        virtualExtension = "zip",
        contentUri = localUri,
        canonicalTitle = title,
    )

    @Test
    fun `sonic excludes aerobiz supersonic and dbz supersonic warriors and blast man`() {
        val games = listOf(
            fakeGame("s1", "Sonic The Hedgehog (USA, Europe)", "genesis", year = 1991, localUri = "content://rom/s1"),
            fakeGame("s2", "Sonic The Hedgehog 2 (World)", "genesis", year = 1992),
            fakeGame("bad1", "Aerobiz Supersonic (USA)", "snes", year = 1994, localUri = "content://rom/bad1"),
            fakeGame("bad2", "Dragon Ball Z - Supersonic Warriors (USA)", "gba", year = 2004),
            fakeGame("bad3", "Sonic Blast Man (USA)", "snes", year = 1992),
        )

        val sagas = SagasRepository.buildSagas(games)
        val sonic = sagas.firstOrNull { it.id == "sonic" }
        assertNotNull(sonic)
        val matchedTitles = sonic!!.entries.mapNotNull { it.game?.title }

        assertTrue(matchedTitles.any { it.contains("Sonic The Hedgehog (USA") })
        assertTrue(matchedTitles.any { it.contains("Sonic The Hedgehog 2") })
        assertFalse("Aerobiz Supersonic must not match Sonic saga", matchedTitles.any { it.contains("Aerobiz") })
        assertFalse("DBZ Supersonic must not match Sonic saga", matchedTitles.any { it.contains("Supersonic Warriors") })
        assertFalse("Sonic Blast Man must not match Sonic saga", matchedTitles.any { it.contains("Blast Man") })
    }

    @Test
    fun `castlevania excludes bram stokers dracula and keeps symphony`() {
        val games = listOf(
            fakeGame("c1", "Castlevania (USA)", "nes", year = 1986),
            fakeGame("c2", "Castlevania - Symphony of the Night (USA)", "ps1", year = 1997, localUri = "content://rom/c2"),
            fakeGame("bad", "Bram Stoker's Dracula (USA)", "snes", year = 1993),
        )

        val sagas = SagasRepository.buildSagas(games)
        val cv = sagas.first { it.id == "castlevania" }
        val matchedTitles = cv.entries.mapNotNull { it.game?.title }

        assertTrue(matchedTitles.any { it.contains("Symphony of the Night") })
        assertFalse("Bram Stoker's Dracula must be excluded", matchedTitles.any { it.contains("Bram Stoker") })
    }

    @Test
    fun `all sagas keep chronological timeline substantial history and valid libretro art urls`() {
        val sagas = SagasRepository.buildSagas(emptyList())
        assertTrue("Expected at least 46 curated sagas", sagas.size >= 46)

        sagas.forEach { saga ->
            assertTrue("Saga ${saga.id} history too short", saga.history.length >= 80)
            assertTrue("Saga ${saga.id} hero banner URL must be HTTPS", saga.heroBannerUrl.startsWith("https://"))
            assertTrue("Saga ${saga.id} key art URL must be HTTPS", saga.keyArtUrl.startsWith("https://"))
            assertFalse(
                "Saga ${saga.id} hero banner repo must use underscores never %20: ${saga.heroBannerUrl}",
                saga.heroBannerUrl.substringBefore("/master/").contains("%20"),
            )
            assertFalse(
                "Saga ${saga.id} key art repo must use underscores never %20: ${saga.keyArtUrl}",
                saga.keyArtUrl.substringBefore("/master/").contains("%20"),
            )
            val mainYears = saga.entries.filter { !it.isExtra }.mapNotNull { it.year }
            assertEquals("Saga ${saga.id} timeline must stay chronological", mainYears.sorted(), mainYears)
        }
    }

    @Test
    fun `unofficial pirates protos and seganet entries are excluded and aliases map cleanly`() {
        val games = listOf(
            fakeGame("bad1", "Pokemon Stadium (USA) (Pirate)", "snes"),
            fakeGame("bad2", "Metroid (World) (2023-11-30) (Beta) (Aftermarket) (Pirate)", "snes"),
            fakeGame("bad3", "Ninja Gaiden (Japan) (Proto) [b]", "genesis"),
            fakeGame("bad4", "Phantasy Star II - Amia's Adventure (Japan) (SegaNet)", "genesis"),
            fakeGame("ps4", "Phantasy Star - Sennenki no Owari ni (Japan)", "genesis", localUri = "content://rom/ps4"),
            fakeGame("sh1", "Super Shinobi, The (Japan)", "genesis", localUri = "content://rom/sh1"),
            fakeGame("dbz", "Dragon Ball Z - Supersonic Warriors (USA)", "gba", localUri = "content://rom/dbz"),
            fakeGame("sf2", "Shining Force II (USA)", "genesis", localUri = "content://rom/sf2"),
        )

        val sagas = SagasRepository.buildSagas(games).associateBy { it.id }

        assertEquals(0, sagas["pokemon"]!!.catalogCount)
        assertEquals(0, sagas["metroid"]!!.catalogCount)
        assertEquals(0, sagas["ninja-gaiden"]!!.catalogCount)

        val psOwned = sagas["phantasy-star"]!!.entries.filter { it.inCatalog }
        assertEquals(1, psOwned.size)
        assertEquals("Phantasy Star IV: The End of the Millennium", psOwned.first().title)
        assertFalse(psOwned.first().isExtra)

        val shOwned = sagas["shinobi"]!!.entries.filter { it.inCatalog }
        assertEquals(1, shOwned.size)
        assertFalse(shOwned.first().isExtra)

        assertTrue(sagas["dragon-ball"]!!.localOwnedCount >= 1)
        assertTrue(sagas["shining"]!!.localOwnedCount >= 1)
    }

    @Test
    fun `canonical eras consolidate fragmented tags per saga and local AI audit verifies health`() {
        val sagas = SagasRepository.buildSagas(emptyList())
        val microEras = setOf(
            "Ferrari Testarossa",
            "16-bit Shiny",
            "SNES Tardío",
            "Lock-On",
            "Motos AM2",
            "Obra Cumbre MD",
            "Renacimiento 2D",
        )
        sagas.forEach { saga ->
            val eras = saga.entries.map { it.era }
            assertFalse(
                "Saga ${saga.id} must not expose micro-era tags: $eras",
                eras.any { it in microEras },
            )
            val mainYears = saga.entries.filter { !it.isExtra }.mapNotNull { it.year }
            assertEquals("Chronological order must stay intact in ${saga.id}", mainYears.sorted(), mainYears)
        }

        val superMario = sagas.first { it.id == "super-mario" }
        assertTrue(superMario.entries.map { it.era }.distinct().size <= 4)

        val report = SagaAiAuditEngine.localAudit(superMario)
        assertEquals("super-mario", report.sagaId)
        assertTrue(report.chronologyOk)
        assertTrue(report.score in 45..100)
        assertTrue(report.missingHighlights.isNotEmpty())

        val parsed = SagaAiAuditEngine.parseAuditJson(
            """{"score": 92, "summary": "Secuencia coherente", "issues": [], "eraSuggestions": ["Todo bien"], "missingHighlights": ["Super Mario 64"]}""",
            superMario,
        )
        assertNotNull(parsed)
        assertEquals(92, parsed!!.score)
        assertEquals("Secuencia coherente", parsed.summary)
    }
}

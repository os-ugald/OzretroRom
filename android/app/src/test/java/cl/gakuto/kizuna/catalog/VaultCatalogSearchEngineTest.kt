package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.ui.compose.components.OZ_NDS_COVER_ASPECT_RATIO
import cl.gakuto.kizuna.ui.compose.components.gridColumnsForDensity
import cl.gakuto.kizuna.data.GameGridDensity
import cl.gakuto.kizuna.util.TextNormalizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCatalogSearchEngineTest {

    @After
    fun tearDown() {
        VaultCatalogSearchEngine.resetForTest()
    }

    @Test
    fun `bucketRank prioritizes exact then prefix then word then substring`() {
        val entries = listOf(
            sampleItem("1", "Wariomario Collection", "nes"),      // substring only
            sampleItem("2", "Mario", "nes"),                      // exact
            sampleItem("3", "Mario Paint", "snes"),               // prefix
            sampleItem("4", "Paper Mario", "n64"),                // word prefix
            sampleItem("5", "Super Mario Bros", "nes"),           // longer word prefix
        )
        VaultCatalogSearchEngine.seedEntriesForTest(entries)

        val hits = VaultCatalogSearchEngine.searchLocal("mario", "", limit = 10)
        assertEquals(listOf("2", "3", "4", "5", "1"), hits.map { it.id })
    }

    @Test
    fun `arcade balanced search preserves both FBNeo and MAME results`() {
        val items = mutableListOf<CatalogSearchItem>()
        repeat(8) { idx ->
            items += sampleItem("fb-$idx", "Street Fighter $idx (USA)", "arcade", arcadeCore = "FBNeo", provider = "MiNERVA · FBNeo")
        }
        repeat(4) { idx ->
            items += sampleItem(
                "mame-$idx",
                "Street Fighter EX $idx",
                "arcade",
                arcadeCore = "MAME",
                provider = "MAME (Internet Archive)",
                archiveUrl = "https://archive.org/download/arcade_sfex$idx/sfex$idx.zip",
            )
        }
        VaultCatalogSearchEngine.seedEntriesForTest(items)

        val hits = VaultCatalogSearchEngine.searchLocal("street", platform = "arcade", limit = 6)
        assertTrue(hits.any { it.arcadeCore == "FBNeo" })
        assertTrue(hits.any { it.arcadeCore == "MAME" })
    }

    @Test
    fun `toAuthorizedSource builds magnet for minerva and https for mame ia`() {
        val minerva = CatalogSearchItem(
            id = "c1",
            title = "Chrono Trigger",
            edition = "(USA)",
            platform = "snes",
            filename = "Chrono Trigger (USA).zip",
            size = 3_145_728L,
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            fileIndex = 42,
            exactFullPath = "Minerva_Myrient/No-Intro/SNES/Chrono Trigger (USA).zip",
            trackerUrls = listOf("udp://tracker.opentrackr.org:1337/announce"),
        )
        val magnetSrc = VaultCatalogSearchEngine.toAuthorizedSource(minerva)
        assertNotNull(magnetSrc)
        assertTrue(magnetSrc!!.isMagnet)
        assertEquals(42, magnetSrc.torrentFileIndex)

        val mame = CatalogSearchItem(
            id = "m1",
            title = "Cadillacs and Dinosaurs",
            edition = "MAME",
            platform = "arcade",
            filename = "dino.zip",
            size = 1_500_000L,
            provider = "MAME (Internet Archive)",
            archiveUrl = "https://archive.org/download/arcade_dino/dino.zip",
            arcadeCore = "MAME",
        )
        val httpSrc = VaultCatalogSearchEngine.toAuthorizedSource(mame)
        assertNotNull(httpSrc)
        assertTrue(httpSrc!!.isHttp)
    }

    @Test
    fun `dynamic smart lists update reactively from play history`() {
        val chrono = fakeGame("g1", "Chrono Trigger", "snes", genre = "JRPG", playedAt = 1_000L)
        val ff6 = fakeGame("g2", "Final Fantasy VI", "snes", genre = "JRPG")
        val sonic = fakeGame("g3", "Sonic the Hedgehog 2", "genesis", genre = "Platform", localUri = "content://rom/g3")
        val sf2 = fakeGame("g4", "Street Fighter II", "arcade-fbneo", genre = "Fighting")

        val lists = DynamicSmartListsEngine.buildSmartLists(
            recent = listOf(chrono),
            favorites = emptyList(),
            local = listOf(sonic),
            catalogSample = listOf(chrono, ff6, sonic, sf2),
        )
        val becauseList = lists.firstOrNull { it.id == "ia-because-played" }
        assertNotNull(becauseList)
        assertTrue(becauseList!!.title.contains("Chrono Trigger"))
        assertTrue(becauseList.games.any { it.id == "g2" })
        assertTrue(lists.any { it.id == "ia-ready-vault" })
    }

    @Test
    fun `nds cover aspect ratio and 3 column portrait policy are fixed cleanly`() {
        assertTrue(OZ_NDS_COVER_ASPECT_RATIO in 1.05f..1.18f)
        assertEquals(3, gridColumnsForDensity(GameGridDensity.AUTO, wide = false, maxWidthDp = 390f))
        assertEquals(3, gridColumnsForDensity(GameGridDensity.NORMAL, wide = false, maxWidthDp = 390f))
        assertEquals(4, gridColumnsForDensity(GameGridDensity.SMALL, wide = false, maxWidthDp = 390f))
        assertEquals(2, gridColumnsForDensity(GameGridDensity.LARGE, wide = false, maxWidthDp = 390f))
        assertEquals(5, gridColumnsForDensity(GameGridDensity.AUTO, wide = true, maxWidthDp = 840f))
        assertEquals(6, gridColumnsForDensity(GameGridDensity.AUTO, wide = true, maxWidthDp = 1200f))
    }

    @Test
    fun `parseInternetArchiveJson filters noise and infers nds psp dc gc wii platforms`() {
        val iaJson = """
            {
              "response": {
                "docs": [
                  {"identifier": "nsmb_nds_rom", "title": "New Super Mario Bros. (USA)", "item_size": 33554432, "subject": ["Nintendo DS", "No-Intro"]},
                  {"identifier": "thingiverse_999", "title": "Nintendo DS Cartridge Holder 3D Print", "item_size": 1024, "subject": ["thingiverse"]},
                  {"identifier": "gow_psp_cso", "title": "God of War Chains of Olympus (USA)", "item_size": 900000000, "subject": ["PlayStation Portable"]},
                  {"identifier": "sa2_dc", "title": "Sonic Adventure 2 (USA)", "item_size": 800000000, "subject": ["Sega Dreamcast"]},
                  {"identifier": "ww_gc_rvz", "title": "Legend of Zelda Wind Waker", "item_size": 1200000000, "subject": ["Nintendo GameCube"]},
                  {"identifier": "smg_wbfs", "title": "Super Mario Galaxy (USA)", "item_size": 3400000000, "subject": ["Nintendo Wii"]},
                  {"identifier": "ost_wii", "title": "Super Mario Galaxy Original Soundtrack OST", "item_size": 50000000, "subject": ["Nintendo Wii"]}
                ]
              }
            }
        """.trimIndent()

        val items = VaultCatalogSearchEngine.parseInternetArchiveJson(iaJson, platform = "", inferPlatform = true)
        assertEquals(5, items.size)
        assertEquals(listOf("nds", "psp", "dc", "gc", "wii"), items.map { it.platform })
        assertTrue(items.all { it.archiveUrl.startsWith("https://archive.org/details/") })
        assertEquals("Nintendo_-_Nintendo_DS", items.first().thumbnailRepo)
    }

    @Test
    fun `pc hub catalog parser supports both array and wrapped results object`() {
        val arrayBody = """
            [
              {"id": "h1", "title": "Mario Kart DS", "platform": "NDS", "filename": "mkds.nds", "size": 67108864, "archiveUrl": "/api/v1/library/h1/download"}
            ]
        """.trimIndent()
        val arrayItems = cl.gakuto.kizuna.hub.GameVaultHubClient.parseCatalogSearchJson(arrayBody, "http://192.168.1.10:17894")
        assertEquals(1, arrayItems.size)
        assertEquals("nds", arrayItems[0].platform)
        assertEquals("http://192.168.1.10:17894/api/v1/library/h1/download", arrayItems[0].archiveUrl)

        val objBody = """{"results": [{"id": "h2", "title": "OutRun", "platform": "arcade", "filename": "outrun.zip", "size": 524288}]}"""
        val objItems = cl.gakuto.kizuna.hub.GameVaultHubClient.parseCatalogSearchJson(objBody, "http://192.168.1.10:17894")
        assertEquals(1, objItems.size)
        assertEquals("arcade", objItems[0].platform)
    }

    private fun sampleItem(
        id: String,
        title: String,
        platform: String,
        arcadeCore: String = "",
        provider: String = "MiNERVA",
        archiveUrl: String = "",
    ): CatalogSearchItem = CatalogSearchItem(
        id = id,
        title = title,
        edition = "(USA)",
        platform = platform,
        filename = "$title.zip",
        size = 1024L,
        infoHash = if (archiveUrl.isEmpty()) "0123456789abcdef0123456789abcdef01234567" else "",
        provider = provider,
        archiveUrl = archiveUrl,
        arcadeCore = arcadeCore,
        searchText = "$title (usa) $platform".lowercase(),
    )

    private fun fakeGame(
        id: String,
        title: String,
        systemId: String,
        genre: String,
        playedAt: Long? = null,
        localUri: String? = null,
    ): GameEntity = GameEntity(
        id = id,
        title = title,
        normalizedTitle = TextNormalizer.normalize(title),
        platform = systemId.uppercase(),
        systemId = systemId,
        year = 1995,
        region = "USA",
        thumbnailRepo = null,
        source = "curated",
        launchValidated = true,
        coreId = null,
        virtualExtension = "sfc",
        contentUri = localUri,
        canonicalTitle = title,
        genre = genre,
        lastPlayedAt = playedAt,
    )
}

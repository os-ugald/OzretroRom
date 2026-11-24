package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.catalog.sourceindex.SnesSourceIndexCatalog
import cl.gakuto.kizuna.catalog.sourceindex.UnresolvedGameSourceRoute
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.CatalogFilterState
import cl.gakuto.kizuna.util.CatalogSort
import cl.gakuto.kizuna.util.OriginStatusFilter
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSystemRoutingTest {
    @Test fun actualRegistryMakesQualifiedSnesSelectable() {
        val eligible = CatalogSystemRouting.eligibleSourceIndexSystems(actualSystems())
        assertEquals(listOf("gba", "genesis", "nes", "snes"), eligible.map(CatalogSystem::id))
        assertEquals("Game Boy Advance", eligible.first().name)
        assertFalse(eligible.first().launchValidated)
        assertTrue(eligible.last().launchValidated)
    }

    @Test fun catalogAvailabilityIsIndependentFromLaunchValidationAndCertification() {
        val systems = actualSystems().associateBy(CatalogSystem::id)
        assertTrue(CatalogSystemRouting.sourceIndexAvailable("snes"))
        assertTrue(CatalogSystemRouting.sourceIndexAvailable("nes"))
        assertTrue(CatalogSystemRouting.sourceIndexAvailable("genesis"))
        assertTrue(CatalogSystemRouting.sourceIndexAvailable("gba"))
        assertTrue(requireNotNull(systems["snes"]).launchValidated)
        assertTrue(requireNotNull(systems["nes"]).launchValidated)
        assertTrue(requireNotNull(systems["genesis"]).launchValidated)
        assertFalse(requireNotNull(systems["gba"]).launchValidated)
        assertFalse(CatalogSystemRouting.sourceIndexAvailable("n64"))
        assertFalse(CatalogSystemRouting.sourceIndexAvailable("fds"))
        assertFalse(CatalogSystemRouting.sourceIndexAvailable("sega-32x"))
        assertFalse(CatalogSystemRouting.sourceIndexAvailable("sega-cd"))
    }

    @Test fun genericSourceIndexStatusUsesSelectedSystemAndActualCount() {
        assertEquals(
            "Super Nintendo: Oz Source Index local listo · 2505 juegos controlados",
            SourceIndexCatalogStatus.ready("Super Nintendo", 2_505),
        )
        assertEquals(
            "Nintendo Entertainment System: Oz Source Index local listo · 4415 juegos controlados",
            SourceIndexCatalogStatus.ready("Nintendo Entertainment System", 4_415),
        )
    }

    @Test fun snesSelectsSourceIndexAndOtherSystemsKeepRoomBackend() {
        assertEquals(CatalogBackend.SOURCE_INDEX, CatalogSystemRouting.backendFor("snes"))
        assertEquals(CatalogBackend.SOURCE_INDEX, CatalogSystemRouting.backendFor("nes"))
        assertEquals(CatalogBackend.SOURCE_INDEX, CatalogSystemRouting.backendFor("genesis"))
        assertEquals(CatalogBackend.SOURCE_INDEX, CatalogSystemRouting.backendFor("gba"))
        assertFalse(CatalogSystemRouting.backendFor("snes") == CatalogBackend.ROOM_CATALOG)
        assertEquals(CatalogBackend.ROOM_CATALOG, CatalogSystemRouting.backendFor("n64"))
        assertEquals(CatalogBackend.ROOM_CATALOG, CatalogSystemRouting.backendFor("sega-32x"))
        assertEquals(CatalogBackend.ROOM_CATALOG, CatalogSystemRouting.backendFor(null))
    }

    @Test fun sourceIndexProjectionAndFirstPlayIdentityRemainExact() {
        val projected = sourceIndexGame(SnesSourceIndexCatalog.projectionId(SnesSourceIndexCatalog.FIRST_PLAY_GAME_ID))
        assertEquals("snes", projected.systemId)
        assertEquals("oz-source-index", projected.source)
        assertEquals(SnesSourceIndexCatalog.LEGACY_FIRST_PLAY_PROJECTION_ID, projected.id)
    }

    @Test fun sourceIndexProjectionWinsBeforeRomgiWhileOrdinaryGamesKeepRomgi() {
        val projected = sourceIndexGame("oz-source-index-snes-game-test")
        assertEquals(
            UnresolvedGameSourceRoute.SOURCE_INDEX,
            SnesSourceIndexCatalog.unresolvedSourceRoute(projected, romgiEnabled = true),
        )
        assertEquals(
            UnresolvedGameSourceRoute.ROMGI,
            SnesSourceIndexCatalog.unresolvedSourceRoute(projected.copy(source = "libretro"), romgiEnabled = true),
        )
    }

    @Test fun snesBackendMakesTruthfulFilterModeContractReachable() {
        assertEquals(CatalogBackend.SOURCE_INDEX, CatalogSystemRouting.backendFor("snes"))
        val supported = SnesSourceIndexCatalog.supportedFilters(
            CatalogFilterState(
                status = setOf(OriginStatusFilter.ROMGI_AVAILABLE),
                sort = CatalogSort.YEAR_DESC,
            ),
        )
        assertTrue(supported.status.isEmpty())
        assertEquals(CatalogSort.NAME_ASC, supported.sort)
    }

    private fun actualSystems(): List<CatalogSystem> {
        val asset = listOf(
            File("src/main/assets/libretro_systems.json"),
            File("app/src/main/assets/libretro_systems.json"),
        ).first(File::isFile)
        val array = JSONArray(asset.readText())
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            CatalogSystem(
                id = item.getString("id"),
                name = item.getString("name"),
                datUrl = item.getString("datUrl"),
                genreDatUrl = null,
                thumbnailRepo = item.getString("thumbnailRepo"),
                platform = item.getString("platform"),
                coreId = item.optString("coreId").takeIf(String::isNotBlank),
                virtualExtension = item.getString("virtualExtension"),
                launchValidated = item.optBoolean("launchValidated", false),
            )
        }
    }

    private fun sourceIndexGame(id: String) = GameEntity(
        id = id,
        title = "Test",
        normalizedTitle = "test",
        platform = "Super Nintendo",
        systemId = "snes",
        year = null,
        region = "World",
        thumbnailRepo = "Nintendo_-_Super_Nintendo_Entertainment_System",
        source = "oz-source-index",
        launchValidated = true,
        coreId = "snes9x_libretro_android.so",
        virtualExtension = "sfc",
    )
}

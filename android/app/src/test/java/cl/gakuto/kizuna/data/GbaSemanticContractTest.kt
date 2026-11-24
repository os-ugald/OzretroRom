package cl.gakuto.kizuna.data

import cl.gakuto.kizuna.catalog.CatalogSystem
import cl.gakuto.kizuna.catalog.CatalogSystemRouting
import cl.gakuto.kizuna.catalog.sourceindex.SourceIndexCatalogs
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GbaSemanticContractTest {
    @Test fun freshSeedGbaRowsAreNotLaunchValidated() {
        val array = JSONArray(asset("seed_games.json").readText())
        val gba = (0 until array.length()).map { array.getJSONObject(it) }
            .filter { it.getString("systemId") == "gba" }
        assertTrue(gba.isNotEmpty())
        assertTrue(gba.all { !it.optBoolean("launchValidated", false) })
    }

    @Test fun registryGbaIsNotLaunchValidatedAndFrozenPlatformsRemainUnchanged() {
        val array = JSONArray(asset("libretro_systems.json").readText())
        val byId = (0 until array.length()).map { array.getJSONObject(it) }.associateBy { it.getString("id") }
        assertFalse(byId.getValue("gba").optBoolean("launchValidated", false))
        assertTrue(byId.getValue("snes").getBoolean("launchValidated"))
        assertTrue(byId.getValue("nes").getBoolean("launchValidated"))
        assertTrue(byId.getValue("genesis").getBoolean("launchValidated"))
    }

    @Test fun legacyGbaWithoutOneTapSupportCannotProduceTrue() {
        assertFalse(LegacyLaunchValidationPolicy.forImported("gba", oneTapSupported = true))
        assertFalse(LegacyLaunchValidationPolicy.forImported("gba", oneTapSupported = false))
        assertTrue(LegacyLaunchValidationPolicy.forImported("snes", oneTapSupported = true))
    }

    @Test fun gbaUsesSourceIndexRoutingWithoutLaunchOrAcquisition() {
        assertTrue(CatalogSystemRouting.sourceIndexAvailable("gba"))
        assertTrue(CatalogSystemRouting.backendFor("gba") == cl.gakuto.kizuna.catalog.CatalogBackend.SOURCE_INDEX)
        assertTrue(SourceIndexCatalogs.forSystem("gba") != null)
        val gba = CatalogSystem("gba", "Game Boy Advance", "", null, "", "", null, "gba", false)
        assertEquals(listOf("gba"), CatalogSystemRouting.eligibleSourceIndexSystems(listOf(gba)).map(CatalogSystem::id))
    }

    private fun asset(name: String): File = listOf(
        File("src/main/assets/$name"),
        File("app/src/main/assets/$name"),
    ).first(File::isFile)
}

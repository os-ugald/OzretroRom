package cl.gakuto.kizuna.launch

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisCoreResolutionContractTest {
    @Test fun registryProfileAndRuntimeFilenameAgreeWithPhysicalLaunchEvidence() {
        val systems = JSONArray(asset("libretro_systems.json").readText())
        val genesis = (0 until systems.length()).map(systems::getJSONObject)
            .single { it.getString("id") == "genesis" }
        val profile = requireNotNull(EmulatorProfiles.forSystem("genesis"))

        assertEquals(CORE, genesis.getString("coreId"))
        assertTrue(genesis.getBoolean("launchValidated"))
        assertEquals(CORE, profile.retroArchCore)
        assertTrue("md" in profile.acceptedExtensions)
        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/$CORE.zip",
            RetroArchManager.developmentCoreArtifactUrl("arm64-v8a", CORE),
        )
        assertEquals(
            File("/data/user/0/com.retroarch.aarch64", "cores/$CORE").absolutePath,
            RetroArchManager.resolvedCorePath("/data/user/0/com.retroarch.aarch64", CORE),
        )
        val trusted = JSONObject(asset("trusted_cores.json").readText()).getJSONArray("cores")
        assertEquals(0, trusted.length())
    }

    private fun asset(name: String): File = listOf(
        File("src/main/assets/$name"), File("app/src/main/assets/$name"),
    ).first(File::isFile)

    companion object { const val CORE = "genesis_plus_gx_libretro_android.so" }
}

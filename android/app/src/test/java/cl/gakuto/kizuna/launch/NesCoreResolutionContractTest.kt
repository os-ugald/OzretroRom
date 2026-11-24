package cl.gakuto.kizuna.launch

import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NesCoreResolutionContractTest {
    @Test fun registryProfileDownloadArtifactAndRuntimeFilenameAgree() {
        val configured = configuredNesCore()
        val profile = requireNotNull(EmulatorProfiles.forSystem("nes"))

        assertEquals("mesen_libretro_android.so", configured.coreId)
        assertTrue(configured.launchValidated)
        assertEquals(configured.coreId, profile.retroArchCore)
        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/mesen_libretro_android.so.zip",
            RetroArchManager.developmentCoreArtifactUrl("arm64-v8a", configured.coreId),
        )
        assertEquals(
            File("/data/user/0/com.retroarch.aarch64", "cores/mesen_libretro_android.so").absolutePath,
            RetroArchManager.resolvedCorePath(
                "/data/user/0/com.retroarch.aarch64",
                configured.coreId,
            ),
        )
    }

    private fun configuredNesCore(): ConfiguredCore {
        val asset = listOf(
            File("src/main/assets/libretro_systems.json"),
            File("app/src/main/assets/libretro_systems.json"),
        ).first(File::isFile)
        val systems = JSONArray(asset.readText())
        val nes = (0 until systems.length())
            .map(systems::getJSONObject)
            .single { it.getString("id") == "nes" }
        return ConfiguredCore(
            coreId = nes.getString("coreId"),
            launchValidated = nes.optBoolean("launchValidated", false),
        )
    }

    private data class ConfiguredCore(val coreId: String, val launchValidated: Boolean)
}

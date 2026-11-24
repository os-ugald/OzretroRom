package cl.gakuto.kizuna.launch

import android.app.Application
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Platform Factory V1 — Phase 0 characterization.
 *
 * Generalizes the same contract already proven individually by
 * [NesCoreResolutionContractTest] and [GenesisCoreResolutionContractTest] into one parameterized
 * test covering every systemId the libretro_systems.json registry currently marks
 * launchValidated=true, so a future Platform Factory refactor has one place to prove it did not
 * change resolved-core or accepted-extension behavior for any already-validated platform.
 *
 * This does not replace the two existing tests (kept as-is; Genesis also guards an unrelated
 * trusted_cores.json invariant this test does not touch).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = Application::class, sdk = [25])
class PlatformCoreResolutionCharacterizationTest(private val systemId: String) {

    @Test fun registryProfileCoreAndExtensionAgree() {
        val registry = registryEntry(systemId)
        assertTrue("registry says '$systemId' is launchValidated=false; test selection is stale", registry.launchValidated)

        val profile = requireNotNull(EmulatorProfiles.forSystem(systemId)) {
            "libretro_systems.json marks '$systemId' launchValidated=true but EmulatorProfiles has no profile for it"
        }

        val manager = RetroArchManager(RuntimeEnvironment.getApplication())
        val candidates = manager.coreCandidatesFor(registry.coreId)
        assertTrue(
            "registry coreId '${registry.coreId}' (resolved candidates=$candidates) does not include " +
                "profile.retroArchCore '${profile.retroArchCore}' for '$systemId'",
            profile.retroArchCore in candidates,
        )

        assertTrue(
            "registry virtualExtension '${registry.virtualExtension}' is not in " +
                "profile.acceptedExtensions ${profile.acceptedExtensions} for '$systemId'",
            registry.virtualExtension in profile.acceptedExtensions,
        )

        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/${profile.retroArchCore}.zip",
            RetroArchManager.developmentCoreArtifactUrl("arm64-v8a", profile.retroArchCore),
        )
        assertEquals(
            File("/data/user/0/com.retroarch.aarch64", "cores/${profile.retroArchCore}").absolutePath,
            RetroArchManager.resolvedCorePath("/data/user/0/com.retroarch.aarch64", profile.retroArchCore),
        )
    }

    private fun registryEntry(id: String): RegistryEntry {
        val match = (0 until libretroSystems.length())
            .map(libretroSystems::getJSONObject)
            .single { it.getString("id") == id }
        return RegistryEntry(
            coreId = match.getString("coreId"),
            virtualExtension = match.getString("virtualExtension"),
            launchValidated = match.optBoolean("launchValidated", false),
        )
    }

    private data class RegistryEntry(val coreId: String, val virtualExtension: String, val launchValidated: Boolean)

    companion object {
        private fun libretroSystemsAsset(): File = listOf(
            File("src/main/assets/libretro_systems.json"),
            File("app/src/main/assets/libretro_systems.json"),
        ).first(File::isFile)

        private val libretroSystems: JSONArray by lazy { JSONArray(libretroSystemsAsset().readText()) }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun launchValidatedSystemIds(): List<String> = (0 until libretroSystems.length())
            .map(libretroSystems::getJSONObject)
            .filter { it.optBoolean("launchValidated", false) }
            .map { it.getString("id") }
    }
}

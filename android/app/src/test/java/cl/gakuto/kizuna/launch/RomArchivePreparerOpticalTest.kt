package cl.gakuto.kizuna.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomArchivePreparerOpticalTest {
    @Test
    fun ps2GameCubeAndWiiDeclareNativeDiscFormats() {
        assertTrue("iso" in RomArchivePreparer.nativeExtensions("ps2"))
        assertTrue("rvz" in RomArchivePreparer.nativeExtensions("gamecube"))
        assertTrue("rvz" in RomArchivePreparer.nativeExtensions("wii"))
        assertTrue("wbfs" in RomArchivePreparer.nativeExtensions("wii"))
    }

    @Test
    fun cueAndGdiBundlesAreRejectedUntilCompanionPersistenceExists() {
        assertTrue(
            RomArchivePreparer.requiresUnsupportedBundlePersistence(
                "ps1",
                setOf("cue", "bin"),
            ),
        )
        assertTrue(
            RomArchivePreparer.requiresUnsupportedBundlePersistence(
                "dreamcast",
                setOf("gdi"),
            ),
        )
    }

    @Test
    fun selfContainedOpticalImagesRemainEligible() {
        assertFalse(
            RomArchivePreparer.requiresUnsupportedBundlePersistence(
                "ps1",
                setOf("chd"),
            ),
        )
        assertFalse(
            RomArchivePreparer.requiresUnsupportedBundlePersistence(
                "dreamcast",
                setOf("cdi"),
            ),
        )
        assertFalse(
            RomArchivePreparer.requiresUnsupportedBundlePersistence(
                "ps2",
                setOf("iso"),
            ),
        )
    }

    @Test
    fun snesZipUsesExistingNativePreparationAndVerifiedCoreRoute() {
        assertTrue(RomArchivePreparer.needsNativeExtraction("snes", "zip"))
        assertTrue("sfc" in RomArchivePreparer.nativeExtensions("snes"))
        val profile = requireNotNull(EmulatorProfiles.forSystem("snes"))
        assertTrue(profile.retroArchCore == "snes9x_libretro_android.so")
        assertTrue("zip" in profile.acceptedExtensions)
    }
}

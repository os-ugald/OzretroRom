package cl.gakuto.kizuna.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Platform Factory V1 — Shared Foundation Slice C.
 *
 * Proves [RomArchivePreparer.isArchiveAsRomset] — the new explicit runtime choke point for
 * [MediaKind.ARCHIVE_AS_ROMSET] — without registering any real platform data in
 * [PlatformMediaProfiles] (no Neo Geo entry is added by this changeset). The synthetic
 * [PlatformMediaProfile] instances below are test-only fixtures passed via the function's optional
 * `profile` parameter; they never touch the real registry.
 */
class RomArchivePreparerArchiveAsRomsetWiringTest {

    @Test fun legacyArcadeFbneoStillTreatedAsArchiveAsRomset() {
        // No profile registered for "arcade-fbneo" today -> falls to the original literal check.
        assertTrue(RomArchivePreparer.isArchiveAsRomset("arcade-fbneo"))
    }

    @Test fun migratedSingleFileProfilesAreNotArchiveAsRomset() {
        listOf("nes", "snes", "genesis", "gba", "n64").forEach { id ->
            assertFalse("'$id' should not be treated as archive-as-romset", RomArchivePreparer.isArchiveAsRomset(id))
        }
    }

    @Test fun unregisteredNonFbneoSystemIsNotArchiveAsRomset() {
        assertFalse(RomArchivePreparer.isArchiveAsRomset("some-unregistered-system"))
    }

    @Test fun syntheticArchiveAsRomsetProfileIsHonored() {
        val synthetic = PlatformMediaProfile(systemId = "synthetic-romset", mediaKind = MediaKind.ARCHIVE_AS_ROMSET)
        assertTrue(RomArchivePreparer.isArchiveAsRomset("synthetic-romset", synthetic))
    }

    @Test fun syntheticSingleFileProfileIsNotArchiveAsRomset() {
        val synthetic = PlatformMediaProfile(systemId = "synthetic-single", mediaKind = MediaKind.SINGLE_FILE)
        assertFalse(RomArchivePreparer.isArchiveAsRomset("synthetic-single", synthetic))
    }

    @Test fun syntheticMultiFileOpticalProfileIsNotArchiveAsRomset() {
        val synthetic = PlatformMediaProfile(systemId = "synthetic-optical", mediaKind = MediaKind.MULTI_FILE_OPTICAL)
        assertFalse(RomArchivePreparer.isArchiveAsRomset("synthetic-optical", synthetic))
    }

    @Test fun needsNativeExtractionStillFalseForArcadeFbneoAfterWiring() {
        assertFalse(RomArchivePreparer.needsNativeExtraction("arcade-fbneo", "zip"))
        assertFalse(RomArchivePreparer.needsNativeExtraction("arcade-fbneo", "7z"))
    }

    @Test fun needsNativeExtractionStillTrueForMigratedSingleFileSystemsAfterWiring() {
        listOf("nes", "snes", "genesis", "gba", "n64").forEach { id ->
            assertTrue("'$id' should still need native extraction for zip", RomArchivePreparer.needsNativeExtraction(id, "zip"))
        }
    }
}

package cl.gakuto.kizuna.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomgiOpticalFormatPolicyTest {
    @Test
    fun multiFileDiscFormatsAreNotAdvertisedAsPersistablePlans() {
        assertTrue("cue" in RomgiCatalog.UNSUPPORTED_MULTI_FILE_DISC_FORMATS)
        assertTrue("bin" in RomgiCatalog.UNSUPPORTED_MULTI_FILE_DISC_FORMATS)
        assertTrue("bin/cue" in RomgiCatalog.UNSUPPORTED_MULTI_FILE_DISC_FORMATS)
        assertTrue("gdi" in RomgiCatalog.UNSUPPORTED_MULTI_FILE_DISC_FORMATS)
    }

    @Test
    fun selfContainedDiscFormatsAreNotInTheBlockedSet() {
        listOf("chd", "iso", "rvz", "cdi", "pbp").forEach { format ->
            assertFalse(format in RomgiCatalog.UNSUPPORTED_MULTI_FILE_DISC_FORMATS)
        }
    }
}

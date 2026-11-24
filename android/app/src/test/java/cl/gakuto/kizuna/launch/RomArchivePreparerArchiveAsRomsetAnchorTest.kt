package cl.gakuto.kizuna.launch

import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Platform Factory V1 — Shared Foundation Slice C anchor.
 *
 * Intentionally hardcoded expected values, captured against the CURRENT (pre-wiring)
 * RomArchivePreparer behavior, before MediaKind.ARCHIVE_AS_ROMSET becomes an explicit runtime
 * choke point. Re-run unchanged after the wiring change to prove zero regression for:
 * arcade-fbneo legacy behavior, migrated SINGLE_FILE platforms, the N64 preferred-extension case,
 * and optical multi-file behavior.
 */
class RomArchivePreparerArchiveAsRomsetAnchorTest {

    @Test fun arcadeFbneoDoesNotNeedNativeExtractionForZip() =
        assertFalse(RomArchivePreparer.needsNativeExtraction("arcade-fbneo", "zip"))

    @Test fun arcadeFbneoDoesNotNeedNativeExtractionFor7z() =
        assertFalse(RomArchivePreparer.needsNativeExtraction("arcade-fbneo", "7z"))

    @Test fun migratedSingleFileSystemsStillNeedNativeExtraction() {
        listOf("nes", "snes", "genesis", "gba", "n64").forEach { id ->
            assertTrue("'$id' should still need native extraction for zip", RomArchivePreparer.needsNativeExtraction(id, "zip"))
        }
    }

    @Test fun n64StillPrefersZ64OverN64() {
        assertEquals(setOf("n64", "z64", "v64"), RomArchivePreparer.nativeExtensions("n64"))
        assertEquals("z64", winningExtension("n64"))
    }

    @Test fun opticalMultiFileDescriptorsStillRejected() {
        assertTrue(RomArchivePreparer.requiresUnsupportedBundlePersistence("ps1", setOf("cue", "bin")))
        assertTrue(RomArchivePreparer.requiresUnsupportedBundlePersistence("dreamcast", setOf("gdi")))
    }

    @Test fun selfContainedOpticalImagesStillRemainEligible() {
        assertFalse(RomArchivePreparer.requiresUnsupportedBundlePersistence("ps1", setOf("chd")))
        assertFalse(RomArchivePreparer.requiresUnsupportedBundlePersistence("dreamcast", setOf("cdi")))
    }

    /** Mirrors the helper already used by PlatformMediaProfileCharacterizationTest / the Phase 1B anchor. */
    private fun winningExtension(systemId: String): String? {
        val extensions = RomArchivePreparer.nativeExtensions(systemId)
        if (extensions.size <= 1) return extensions.firstOrNull()
        val zipFile = File.createTempFile("pmp-slicec-anchor-", ".zip")
        val outputDir = File(zipFile.parentFile, "pmp-slicec-anchor-out-${System.nanoTime()}").apply { mkdirs() }
        try {
            ZipOutputStream(zipFile.outputStream()).use { zip ->
                extensions.forEachIndexed { index, extension ->
                    zip.putNextEntry(ZipEntry("rom.$extension"))
                    zip.write(ByteArray(8_192) { (index + 1).toByte() })
                    zip.closeEntry()
                }
            }
            val prepared = RomArchivePreparer.prepareZip(
                systemId = systemId,
                platform = systemId,
                downloaded = zipFile,
                targetForName = { name -> File(outputDir, name) },
            )
            return prepared.entryName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        } finally {
            zipFile.delete()
            outputDir.deleteRecursively()
        }
    }
}

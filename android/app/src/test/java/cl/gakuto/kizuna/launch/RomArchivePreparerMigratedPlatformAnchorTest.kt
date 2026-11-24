package cl.gakuto.kizuna.launch

import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Platform Factory V1 — Phase 1B anchor.
 *
 * Intentionally hardcodes expected values — the one deliberate exception to "never duplicate
 * expected values by hand" in this migration — because its entire purpose is to stay independent
 * of [PlatformMediaProfile]. Once [RomArchivePreparer] reads from that profile, comparing
 * production against the profile becomes circular for these five platforms; this test instead
 * pins the original, pre-migration behavior as ground truth. Run once against the unmodified
 * RomArchivePreparer before wiring, then re-run unchanged after wiring — see
 * docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1b-media-profile-wiring.md for both runs.
 */
class RomArchivePreparerMigratedPlatformAnchorTest {

    @Test fun nesNativeExtensions() =
        assertEquals(setOf("nes", "fds", "unf", "unif"), RomArchivePreparer.nativeExtensions("nes"))

    @Test fun snesNativeExtensions() =
        assertEquals(setOf("sfc", "smc", "swc", "fig", "bs", "st"), RomArchivePreparer.nativeExtensions("snes"))

    @Test fun genesisNativeExtensions() =
        assertEquals(setOf("md", "gen", "bin", "smd"), RomArchivePreparer.nativeExtensions("genesis"))

    @Test fun gbaNativeExtensions() =
        assertEquals(setOf("gba"), RomArchivePreparer.nativeExtensions("gba"))

    @Test fun n64NativeExtensions() =
        assertEquals(setOf("n64", "z64", "v64"), RomArchivePreparer.nativeExtensions("n64"))

    @Test fun nesEffectivePreferredExtensionIsNes() =
        assertEquals("nes", winningExtension("nes"))

    @Test fun snesEffectivePreferredExtensionIsSfc() =
        assertEquals("sfc", winningExtension("snes"))

    @Test fun genesisEffectivePreferredExtensionIsMd() =
        assertEquals("md", winningExtension("genesis"))

    @Test fun n64EffectivePreferredExtensionIsZ64NotN64() {
        // The one genuine divergence in the whole dataset: "n64" lists first in
        // nativeExtensions("n64"), but production has always preferred "z64". ZIP contains
        // .n64/.z64/.v64 -> result must be .z64.
        assertEquals(setOf("n64", "z64", "v64"), RomArchivePreparer.nativeExtensions("n64"))
        assertEquals("n64", RomArchivePreparer.nativeExtensions("n64").first())
        assertEquals("z64", winningExtension("n64"))
    }

    /**
     * Builds a real synthetic zip with one candidate entry per native extension and asks the real
     * RomArchivePreparer.prepareZip which one it selects — proves the effective tie-break winner
     * via actual behavior (extensionPriority itself is private and not directly callable).
     */
    private fun winningExtension(systemId: String): String? {
        val extensions = RomArchivePreparer.nativeExtensions(systemId)
        if (extensions.size <= 1) return extensions.firstOrNull()

        val zipFile = File.createTempFile("pmp-anchor-", ".zip")
        val outputDir = File(zipFile.parentFile, "pmp-anchor-out-${System.nanoTime()}").apply { mkdirs() }
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

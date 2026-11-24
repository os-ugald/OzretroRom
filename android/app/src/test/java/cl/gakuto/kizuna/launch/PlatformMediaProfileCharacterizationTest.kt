package cl.gakuto.kizuna.launch

import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Platform Factory V1 — Phase 1A characterization.
 *
 * Proves [PlatformMediaProfiles] matches CURRENT [RomArchivePreparer] production behavior for the
 * first migration slice (nes, snes, genesis, gba, n64), by calling RomArchivePreparer's real
 * public functions rather than duplicating hand-copied expected values. RomArchivePreparer itself
 * is not modified by this changeset and does not yet consume this profile.
 */
class PlatformMediaProfileCharacterizationTest {

    private val migratedSystemIds = listOf("nes", "snes", "genesis", "gba", "n64")

    @Test fun everyMigratedPlatformHasAProfile() {
        migratedSystemIds.forEach { id ->
            assertNotNull("no PlatformMediaProfile registered for '$id'", PlatformMediaProfiles.forSystem(id))
        }
    }

    @Test fun nativeExtensionsMatchCurrentRomArchivePreparerBehavior() {
        migratedSystemIds.forEach { id ->
            val profile = requireNotNull(PlatformMediaProfiles.forSystem(id))
            assertEquals(
                "nativeExtensions mismatch for '$id'",
                RomArchivePreparer.nativeExtensions(id),
                profile.nativeExtensions,
            )
        }
    }

    @Test fun mediaKindMatchesCurrentArchiveHandlingForMigratedPlatforms() {
        // None of the five migrated platforms is FBNeo (archive-as-romset, bypasses extraction
        // entirely) or an optical multi-file system; production performs ordinary zip extraction
        // for all five today, which this profile represents as the SINGLE_FILE default.
        migratedSystemIds.forEach { id ->
            val profile = requireNotNull(PlatformMediaProfiles.forSystem(id))
            assertEquals("mediaKind mismatch for '$id'", MediaKind.SINGLE_FILE, profile.mediaKind)
            assertTrue(
                "RomArchivePreparer.needsNativeExtraction('$id', \"zip\") should be true given non-empty nativeExtensions",
                RomArchivePreparer.needsNativeExtraction(id, "zip"),
            )
        }
    }

    @Test fun extractionSizeLimitMatchesCurrentGenericDefaultForMigratedPlatforms() {
        // RomArchivePreparer only special-cases ps2/wii/gamecube extraction limits; none of the
        // five migrated platforms is in that set, so production applies the generic 4 GiB
        // ceiling to all of them today. This profile represents that as `null` (documented
        // default), not a duplicated magic number.
        migratedSystemIds.forEach { id ->
            val profile = requireNotNull(PlatformMediaProfiles.forSystem(id))
            assertNull("extractionSizeLimit should be null (generic default) for '$id'", profile.extractionSizeLimit)
        }
    }

    @Test fun preferredExtensionResolvesToCurrentExtensionPriorityWinner() {
        migratedSystemIds.forEach { id ->
            val profile = requireNotNull(PlatformMediaProfiles.forSystem(id))
            val resolved = profile.preferredExtension ?: profile.nativeExtensions.firstOrNull()
            assertEquals(
                "preferred-extension resolution mismatch for '$id'",
                currentExtensionPriorityWinner(id),
                resolved,
            )
        }
    }

    @Test fun n64PrefersZ64OverN64EvenThoughN64ListsFirstInNativeExtensions() {
        val profile = requireNotNull(PlatformMediaProfiles.forSystem("n64"))
        assertEquals(setOf("n64", "z64", "v64"), profile.nativeExtensions)
        assertEquals("n64 must remain first in nativeExtensions (matches production order)", "n64", profile.nativeExtensions.first())
        assertEquals("z64", profile.preferredExtension)
        assertEquals(
            "RomArchivePreparer must still pick .z64 over .n64 today",
            "z64",
            currentExtensionPriorityWinner("n64"),
        )
    }

    /**
     * Builds one synthetic in-memory zip containing one candidate entry per native extension for
     * [systemId], then asks RomArchivePreparer.prepareZip (the real, unmodified production
     * function) which one it actually selects — proving parity against real behavior instead of a
     * hand-copied expected value.
     */
    private fun currentExtensionPriorityWinner(systemId: String): String? {
        val extensions = RomArchivePreparer.nativeExtensions(systemId)
        if (extensions.size <= 1) return extensions.firstOrNull()

        val zipFile = File.createTempFile("pmp-characterization-", ".zip")
        val outputDir = File(zipFile.parentFile, "pmp-characterization-out-${System.nanoTime()}").apply { mkdirs() }
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

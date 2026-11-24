package cl.gakuto.kizuna.launch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RomPreparedPayloadValidatorTest {
    @Test
    fun acceptsSmallPlaylistWhenBothDiscPayloadsAreComplete() {
        val folder = Files.createTempDirectory("oz-m3u-complete").toFile()
        try {
            val first = folder.resolve("Resident Evil 2 (Disc 1).chd").apply {
                writeBytes(ByteArray(4_097) { 1 })
            }
            val second = folder.resolve("Resident Evil 2 (Disc 2).chd").apply {
                writeBytes(ByteArray(4_097) { 2 })
            }
            val playlist = folder.resolve("Resident Evil 2.m3u").apply {
                writeText("${first.name}\n${second.name}\n")
            }

            assertTrue(playlist.length() < 4_096L)
            RomPreparedPayloadValidator.validate(playlist)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun stillRejectsTruncatedBinaryPayload() {
        val file = Files.createTempFile("oz-truncated", ".chd").toFile()
        try {
            file.writeBytes(ByteArray(128))
            RomPreparedPayloadValidator.validate(file)
        } finally {
            file.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPlaylistWhenOneDiscIsMissing() {
        val folder = Files.createTempDirectory("oz-m3u-missing").toFile()
        try {
            folder.resolve("Resident Evil 2 (Disc 1).chd").writeBytes(ByteArray(4_097) { 1 })
            val playlist = folder.resolve("Resident Evil 2.m3u").apply {
                writeText("Resident Evil 2 (Disc 1).chd\nResident Evil 2 (Disc 2).chd\n")
            }

            RomPreparedPayloadValidator.validate(playlist)
        } finally {
            folder.deleteRecursively()
        }
    }
}

package cl.gakuto.kizuna.patcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomPatcherTest {

    @Test
    fun `applyIps patches standard and RLE hunks cleanly`() {
        val source = "HELLO RETRO WORLD!".toByteArray(Charsets.US_ASCII)
        val patch = ByteArrayOutputStream().apply {
            write("PATCH".toByteArray(Charsets.US_ASCII))
            // Overwrite offset 6 ("RETRO") with "VAULT"
            write(byteArrayOf(0x00, 0x00, 0x06, 0x00, 0x05))
            write("VAULT".toByteArray(Charsets.US_ASCII))
            // RLE hunk at offset 12 size 5 repeat '!'
            write(byteArrayOf(0x00, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x05, '!'.code.toByte()))
            write("EOF".toByteArray(Charsets.US_ASCII))
        }.toByteArray()

        val result = RomPatcher.applyIps(source, patch)
        assertEquals("HELLO VAULT !!!!!!", String(result, Charsets.US_ASCII))
    }

    @Test
    fun `applyBps patches unheadered source and strips 512 byte SMC header for SNES`() {
        val baseRom = ByteArray(1024) { i -> (i % 251).toByte() }
        val patchedTarget = baseRom.copyOf()
        "TRADUCCION ES".toByteArray(Charsets.US_ASCII).copyInto(patchedTarget, destinationOffset = 64)

        val bpsPatch = buildMinimalBpsPatch(baseRom, patchedTarget)

        // Direct unheadered match
        val direct = RomPatcher.applyBps(baseRom, bpsPatch, systemId = "snes")
        assertEquals(0, direct.headerShift)
        assertArrayEquals(patchedTarget, direct.output)

        // Headered SNES ROM (512-byte SMC header prepended)
        val smcHeader = ByteArray(512) { 0x55 }
        val headeredSource = smcHeader + baseRom
        val shifted = RomPatcher.applyBps(headeredSource, bpsPatch, systemId = "snes")
        assertEquals(-512, shifted.headerShift)
        assertArrayEquals(patchedTarget, shifted.output)
    }

    @Test
    fun `extractPatchFromBytes unwraps ips inside zip archive`() {
        val ipsBytes = "PATCH\u0000\u0000\u0000\u0000\u0001XEOF".toByteArray(Charsets.ISO_8859_1)
        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("readme.txt"))
                zos.write("hola".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("traduccion_es.ips"))
                zos.write(ipsBytes)
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val extracted = RomPatcher.extractPatchFromBytes(zipBytes, "bundle.zip")
        assertEquals("IPS", extracted.format)
        assertEquals("traduccion_es.ips", extracted.innerName)
        assertArrayEquals(ipsBytes, extracted.patchBytes)
    }

    private fun buildMinimalBpsPatch(source: ByteArray, target: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("BPS1".toByteArray(Charsets.US_ASCII))
        writeVlq(out, source.size.toLong())
        writeVlq(out, target.size.toLong())
        writeVlq(out, 0L) // metadata size
        // Single TargetRead command covering entire target
        writeVlq(out, ((target.size.toLong() - 1L) shl 2) or 1L)
        out.write(target)

        writeUint32Le(out, crc32(source))
        writeUint32Le(out, crc32(target))
        val patchWithoutFooter = out.toByteArray()
        writeUint32Le(out, crc32(patchWithoutFooter))
        return out.toByteArray()
    }

    private fun writeVlq(out: ByteArrayOutputStream, valueInput: Long) {
        var data = valueInput
        while (true) {
            val x = (data and 0x7FL).toInt()
            data = data ushr 7
            if (data == 0L) {
                out.write(x or 0x80)
                return
            }
            out.write(x)
            data -= 1L
        }
    }

    private fun writeUint32Le(out: ByteArrayOutputStream, v: Long) {
        out.write((v and 0xFFL).toInt())
        out.write(((v ushr 8) and 0xFFL).toInt())
        out.write(((v ushr 16) and 0xFFL).toInt())
        out.write(((v ushr 24) and 0xFFL).toInt())
    }

    private fun crc32(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value
}

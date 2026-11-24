package cl.gakuto.kizuna.patcher

import android.content.Context
import android.net.Uri
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.launch.PersistentRomLibrary
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

data class ExtractedPatch(
    val format: String, // "IPS" or "BPS"
    val patchBytes: ByteArray,
    val innerName: String,
)

data class BpsPatchResult(
    val output: ByteArray,
    val headerShift: Int,
)

data class PatchedRomRecord(
    val gameId: String,
    val label: String,
    val format: String,
    val patchedPath: String,
    val patchedFilename: String,
    val crc32Hex: String,
    val bytes: Long,
)

object RomPatcher {
    private val IPS_HEADER = "PATCH".toByteArray(Charsets.US_ASCII)
    private val IPS_EOF = "EOF".toByteArray(Charsets.US_ASCII)
    private val BPS_HEADER = "BPS1".toByteArray(Charsets.US_ASCII)
    private const val MAX_PATCH_BYTES = 25 * 1024 * 1024
    private const val MAX_OUTPUT_BYTES = 256 * 1024 * 1024

    fun applyIps(source: ByteArray, patch: ByteArray): ByteArray {
        require(patch.size >= 8 && patch.sliceArray(0 until 5).contentEquals(IPS_HEADER)) {
            "El archivo IPS no comienza con la firma 'PATCH'."
        }
        val out = ByteArrayOutputStream(source.size + 4096)
        val buf = source.copyOf()
        var current = buf.toMutableList()
        var pos = 5
        while (pos + 3 <= patch.size) {
            if (patch[pos] == IPS_EOF[0] && patch[pos + 1] == IPS_EOF[1] && patch[pos + 2] == IPS_EOF[2]) {
                pos += 3
                if (pos + 3 == patch.size) {
                    val truncLen = ((patch[pos].toInt() and 0xFF) shl 16) or
                        ((patch[pos + 1].toInt() and 0xFF) shl 8) or
                        (patch[pos + 2].toInt() and 0xFF)
                    if (truncLen < current.size) {
                        current = current.subList(0, truncLen)
                    }
                }
                out.write(current.toByteArray())
                return out.toByteArray()
            }
            require(pos + 5 <= patch.size) { "Registro IPS truncado." }
            val offset = ((patch[pos].toInt() and 0xFF) shl 16) or
                ((patch[pos + 1].toInt() and 0xFF) shl 8) or
                (patch[pos + 2].toInt() and 0xFF)
            val size = ((patch[pos + 3].toInt() and 0xFF) shl 8) or (patch[pos + 4].toInt() and 0xFF)
            pos += 5

            val endOffset = if (size == 0) {
                require(pos + 3 <= patch.size) { "Registro IPS RLE truncado." }
                val rleSize = ((patch[pos].toInt() and 0xFF) shl 8) or (patch[pos + 1].toInt() and 0xFF)
                val fillByte = patch[pos + 2]
                pos += 3
                val needed = offset + rleSize
                require(needed <= MAX_OUTPUT_BYTES) { "Tamaño resultante excesivo." }
                while (current.size < needed) current.add(0)
                for (i in offset until needed) current[i] = fillByte
                needed
            } else {
                require(pos + size <= patch.size) { "Bloque de datos IPS truncado." }
                val needed = offset + size
                require(needed <= MAX_OUTPUT_BYTES) { "Tamaño resultante excesivo." }
                while (current.size < needed) current.add(0)
                for (i in 0 until size) current[offset + i] = patch[pos + i]
                pos += size
                needed
            }
            require(endOffset >= 0)
        }
        throw IllegalArgumentException("El parche IPS terminó sin marcador EOF.")
    }

    private fun crc32Unsigned(bytes: ByteArray, length: Int = bytes.size): Long {
        val crc = CRC32()
        crc.update(bytes, 0, length)
        return crc.value
    }

    private fun readUint32Le(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
    }

    private fun applyBpsRaw(source: ByteArray, patch: ByteArray): ByteArray {
        require(patch.size >= 16 && patch.sliceArray(0 until 4).contentEquals(BPS_HEADER)) {
            "El archivo BPS no comienza con 'BPS1'."
        }
        val expectedPatchCrc = readUint32Le(patch, patch.size - 4)
        val actualPatchCrc = crc32Unsigned(patch, patch.size - 4)
        require(expectedPatchCrc == actualPatchCrc) {
            "El archivo BPS está dañado (CRC32 inválido)."
        }

        val expectedTargetCrc = readUint32Le(patch, patch.size - 8)
        val expectedSourceCrc = readUint32Le(patch, patch.size - 12)
        val actionEnd = patch.size - 12
        var pos = 4

        fun readVlq(): Long {
            var data = 0L
            var shift = 1L
            while (pos < actionEnd) {
                val x = patch[pos++].toInt() and 0xFF
                data += (x and 0x7F).toLong() * shift
                if ((x and 0x80) != 0) return data
                shift = shift shl 7
                data += shift
            }
            throw IllegalArgumentException("Parche BPS truncado en VLQ.")
        }

        val sourceSize = readVlq().toInt()
        val targetSize = readVlq().toInt()
        val metadataSize = readVlq().toInt()
        pos += metadataSize

        require(source.size == sourceSize && crc32Unsigned(source) == expectedSourceCrc) {
            "Esta ROM no coincide con la versión exacta requerida por el parche BPS (CRC32 fuente distinto)."
        }
        require(targetSize in 1..MAX_OUTPUT_BYTES) {
            "Tamaño objetivo BPS fuera de rango."
        }

        val target = ByteArray(targetSize)
        var outOffset = 0
        var sourceRelOffset = 0
        var targetRelOffset = 0

        while (pos < actionEnd) {
            val token = readVlq()
            val mode = (token and 3L).toInt()
            val length = ((token ushr 2) + 1L).toInt()
            when (mode) {
                0 -> { // SourceRead
                    for (i in 0 until length) {
                        val b = if (outOffset < source.size) source[outOffset] else 0
                        target[outOffset++] = b
                    }
                }
                1 -> { // TargetRead
                    require(pos + length <= actionEnd) { "TargetRead BPS fuera de límites." }
                    System.arraycopy(patch, pos, target, outOffset, length)
                    pos += length
                    outOffset += length
                }
                2 -> { // SourceCopy
                    val deltaRaw = readVlq()
                    val delta = (deltaRaw ushr 1).toInt() * if ((deltaRaw and 1L) != 0L) -1 else 1
                    sourceRelOffset += delta
                    require(sourceRelOffset in 0 until source.size) { "SourceCopy BPS fuera de rango." }
                    for (i in 0 until length) target[outOffset++] = source[sourceRelOffset++]
                }
                3 -> { // TargetCopy
                    val deltaRaw = readVlq()
                    val delta = (deltaRaw ushr 1).toInt() * if ((deltaRaw and 1L) != 0L) -1 else 1
                    targetRelOffset += delta
                    require(targetRelOffset in 0 until outOffset) { "TargetCopy BPS fuera de rango." }
                    for (i in 0 until length) target[outOffset++] = target[targetRelOffset++]
                }
            }
        }

        require(outOffset == targetSize && crc32Unsigned(target) == expectedTargetCrc) {
            "El archivo final no pasó la verificación CRC32 del parche BPS."
        }
        return target
    }

    fun applyBps(source: ByteArray, patch: ByteArray, systemId: String = ""): BpsPatchResult {
        try {
            return BpsPatchResult(applyBpsRaw(source, patch), headerShift = 0)
        } catch (primaryErr: Throwable) {
            if (systemId.lowercase(Locale.ROOT) == "snes" && source.size > 512 && (source.size % 1024) == 512) {
                val unheadered = source.copyOfRange(512, source.size)
                return BpsPatchResult(applyBpsRaw(unheadered, patch), headerShift = -512)
            }
            if (systemId.lowercase(Locale.ROOT) == "snes" && (source.size % 1024) == 0) {
                val withDummyHeader = ByteArray(512) + source
                return BpsPatchResult(applyBpsRaw(withDummyHeader, patch), headerShift = 512)
            }
            throw primaryErr
        }
    }

    fun extractPatchFromBytes(raw: ByteArray, filename: String): ExtractedPatch {
        require(raw.size in 8..MAX_PATCH_BYTES) {
            "El archivo de parche debe pesar entre 8 bytes y 25 MB."
        }
        val lower = filename.lowercase(Locale.ROOT)
        if (raw.size >= 5 && raw.sliceArray(0 until 5).contentEquals(IPS_HEADER)) {
            return ExtractedPatch("IPS", raw, filename.ifBlank { "patch.ips" })
        }
        if (raw.size >= 4 && raw.sliceArray(0 until 4).contentEquals(BPS_HEADER)) {
            return ExtractedPatch("BPS", raw, filename.ifBlank { "patch.bps" })
        }
        if (lower.endsWith(".zip") || (raw.size >= 4 && raw[0] == 'P'.code.toByte() && raw[1] == 'K'.code.toByte())) {
            val candidates = mutableListOf<ExtractedPatch>()
            ZipInputStream(ByteArrayInputStream(raw)).use { zis ->
                var entries = 0
                while (entries++ < 128) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val eName = entry.name.substringAfterLast('/')
                    val eLower = eName.lowercase(Locale.ROOT)
                    if (eLower.endsWith(".bps") || eLower.endsWith(".ips")) {
                        val content = zis.readBytes()
                        val fmt = if (eLower.endsWith(".bps")) "BPS" else "IPS"
                        candidates += ExtractedPatch(fmt, content, eName)
                    }
                }
            }
            val preferred = candidates.sortedWith(
                compareByDescending<ExtractedPatch> { it.format == "BPS" }
                    .thenByDescending { val n = it.innerName.lowercase(Locale.ROOT); "es" in n || "spa" in n || "castellano" in n },
            ).firstOrNull()
            require(preferred != null) { "El ZIP no contenía ningún parche .bps o .ips compatible." }
            return preferred
        }
        throw IllegalArgumentException("Formato de parche no reconocido. Usa un archivo .ips, .bps o .zip.")
    }

    suspend fun applyPatchToGame(
        context: Context,
        preferences: AppPreferences,
        game: GameEntity,
        patchBytes: ByteArray,
        patchFilename: String,
        label: String = "",
    ): PatchedRomRecord {
        val rawUriStr = game.contentUri?.takeIf { it.isNotBlank() }
            ?: PersistentRomLibrary(context, preferences, cl.gakuto.kizuna.data.OzDatabase.get(context)).resolve(game)?.members?.firstOrNull()?.uri?.toString()
            ?: error("Descarga o selecciona primero la ROM local antes de aplicar el parche.")

        val uri = Uri.parse(rawUriStr)
        val sourceBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Android no pudo leer el archivo ROM original.")
        require(sourceBytes.size <= MAX_OUTPUT_BYTES) {
            "Parcheado directo disponible para ROMs de hasta 256 MB en Android."
        }

        val extracted = extractPatchFromBytes(patchBytes, patchFilename)
        val patchedBytes = when (extracted.format) {
            "IPS" -> applyIps(sourceBytes, extracted.patchBytes)
            "BPS" -> applyBps(sourceBytes, extracted.patchBytes, game.systemId).output
            else -> error("Formato no soportado: ${extracted.format}")
        }

        val crcHex = "%08X".format(Locale.US, crc32Unsigned(patchedBytes))
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 1..6 && it.all(Char::isLetterOrDigit) }
            ?: game.virtualExtension.ifBlank { "rom" }
        val safeGameDir = game.id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
        val safeBase = game.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(64).ifBlank { safeGameDir }
        val patchedFilename = "$safeBase [ES].$ext"

        val outDir = File(context.filesDir, "patched_roms/$safeGameDir").apply { mkdirs() }
        val outFile = File(outDir, patchedFilename)
        outFile.writeBytes(patchedBytes)

        val finalLabel = label.trim().ifBlank { "Traducción [ES] (${extracted.format})" }
        preferences.setPatchedRom(game.id, outFile.absolutePath, finalLabel, extracted.format)
        return PatchedRomRecord(
            gameId = game.id,
            label = finalLabel,
            format = extracted.format,
            patchedPath = outFile.absolutePath,
            patchedFilename = patchedFilename,
            crc32Hex = crcHex,
            bytes = patchedBytes.size.toLong(),
        )
    }

    fun sha1Short(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.take(6).joinToString("") { "%02x".format(it) }
    }
}

package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Installs the official Libretro overlay bundle into a SAF folder chosen by the user.
 *
 * Oz deliberately does not edit RetroArch's private retroarch.cfg and does not pretend there is
 * a stable external API for assigning a different overlay per core. The user can point
 * RetroArch's Input Overlays directory at this same shared folder once; after that Oz can keep
 * the official bundle updated safely.
 */
class RetroOverlayManager(private val context: Context) {
    data class Progress(
        val stage: String,
        val downloaded: Long = 0L,
        val total: Long = -1L,
        val entries: Int = 0,
    )

    data class Result(
        val entriesInstalled: Int,
        val downloadedBytes: Long,
    )

    suspend fun installOfficialBundle(
        targetTreeUri: Uri,
        onProgress: (Progress) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, targetTreeUri)
            ?: error("No se pudo abrir la carpeta de overlays seleccionada.")
        require(root.canWrite()) { "La carpeta de overlays no permite escritura." }

        val cacheFile = File(context.cacheDir, "libretro-overlays-${System.currentTimeMillis()}.zip")
        var downloaded = 0L
        try {
            val connection = (URL(OFFICIAL_OVERLAYS_URL).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 45_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "OzRetroRom/${cl.gakuto.kizuna.BuildConfig.VERSION_NAME}")
            }
            connection.connect()
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "Libretro redirigió la descarga de overlays fuera de HTTPS."
            }
            require(connection.responseCode in 200..299) {
                "Libretro respondió HTTP ${connection.responseCode} al descargar overlays."
            }
            val total = connection.contentLengthLong
            require(total <= 0L || total <= MAX_DOWNLOAD_BYTES) { "El paquete de overlays excede el límite de descarga." }
            onProgress(Progress("Descargando paquete oficial", 0L, total))
            connection.inputStream.buffered().use { input ->
                FileOutputStream(cacheFile).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        require(downloaded <= MAX_DOWNLOAD_BYTES) { "El paquete de overlays excede el límite de descarga." }
                        onProgress(Progress("Descargando paquete oficial", downloaded, total))
                    }
                }
            }
            connection.disconnect()

            var installed = 0
            var unpackedBytes = 0L
            ZipInputStream(cacheFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val safeName = sanitizeZipPath(entry.name)
                    if (safeName.isBlank()) {
                        zip.closeEntry()
                        continue
                    }
                    installed++
                    require(installed <= MAX_ENTRIES) { "El paquete de overlays contiene demasiados archivos." }
                    val parts = safeName.split('/').filter(String::isNotBlank)
                    if (entry.isDirectory) {
                        ensureDirectory(root, parts)
                    } else {
                        val parent = ensureDirectory(root, parts.dropLast(1))
                        val name = parts.last()
                        parent.findFile(name)?.delete()
                        val target = parent.createFile(mimeFor(name), name)
                            ?: error("No se pudo crear $safeName en la carpeta de overlays.")
                        context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                unpackedBytes += read
                                require(unpackedBytes <= MAX_UNPACKED_BYTES) {
                                    "El paquete de overlays excede el límite de seguridad."
                                }
                                output.write(buffer, 0, read)
                            }
                        } ?: error("No se pudo escribir $safeName.")
                    }
                    zip.closeEntry()
                    if (installed % 25 == 0) {
                        onProgress(Progress("Instalando overlays", downloaded, downloaded, installed))
                    }
                }
            }
            require(installed > 0) { "El paquete oficial de overlays no contenía archivos instalables." }
            onProgress(Progress("Overlays actualizados", downloaded, downloaded, installed))
            Result(installed, downloaded)
        } finally {
            cacheFile.delete()
        }
    }

    private fun sanitizeZipPath(raw: String): String {
        val clean = raw.replace('\\', '/').trimStart('/')
        require(clean.split('/').none { it == ".." }) { "Ruta insegura dentro del paquete de overlays." }
        return clean
    }

    private fun ensureDirectory(root: DocumentFile, parts: List<String>): DocumentFile {
        var current = root
        parts.forEach { part ->
            val existing = current.findFile(part)
            current = when {
                existing?.isDirectory == true -> existing
                existing != null -> {
                    existing.delete()
                    current.createDirectory(part) ?: error("No se pudo crear la carpeta $part.")
                }
                else -> current.createDirectory(part) ?: error("No se pudo crear la carpeta $part.")
            }
        }
        return current
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "cfg" -> "text/plain"
        else -> "application/octet-stream"
    }

    companion object {
        /** Official RetroArch frontend asset bundle maintained by Libretro Buildbot. */
        const val OFFICIAL_OVERLAYS_URL = "https://buildbot.libretro.com/assets/frontend/overlays.zip"
        private const val MAX_DOWNLOAD_BYTES = 256L * 1024L * 1024L
        private const val MAX_ENTRIES = 20_000
        private const val MAX_UNPACKED_BYTES = 768L * 1024L * 1024L
    }
}

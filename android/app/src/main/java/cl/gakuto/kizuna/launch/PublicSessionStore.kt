package cl.gakuto.kizuna.launch

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import cl.gakuto.kizuna.catalog.M3uPlaylist
import java.io.File
import java.io.FileOutputStream

/** Publishes a temporary ROM into Downloads so RetroArch can receive a normal filesystem path. */
class PublicSessionStore(
    private val context: Context,
    private val sessionId: String,
) {
    data class Entry(val displayName: String, val absolutePath: String, val mediaUri: Uri?)

    private val created = mutableListOf<Pair<Uri?, String>>()

    fun publish(source: File): Entry {
        val safe = source.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180).ifBlank { "game.rom" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative = "${Environment.DIRECTORY_DOWNLOADS}/OzRetroRom/Sessions/$sessionId/"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, mimeType(safe))
                put(MediaStore.Downloads.RELATIVE_PATH, relative)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Android no pudo crear la sesión pública temporal.")
            context.contentResolver.openOutputStream(uri, "w")?.use { output -> source.inputStream().use { it.copyTo(output) } }
                ?: error("Android no pudo escribir la sesión temporal.")
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            @Suppress("DEPRECATION")
            val path = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OzRetroRom/Sessions/$sessionId/$safe").absolutePath
            created += uri to path
            Entry(safe, path, uri)
        } else {
            @Suppress("DEPRECATION")
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OzRetroRom/Sessions/$sessionId").apply { mkdirs() }
            val target = File(folder, safe)
            source.inputStream().use { input -> FileOutputStream(target).use { input.copyTo(it) } }
            created += null to target.absolutePath
            Entry(safe, target.absolutePath, null)
        }
    }


    /**
     * Creates the short-lived public compatibility mirror RetroArch needs when the persistent
     * library is exposed only as a SAF content URI. The persistent URI remains the source of
     * truth; this copy is never registered as the user's library ROM.
     */
    fun publish(source: Uri, displayName: String): Entry {
        val safe = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180).ifBlank { "game.rom" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative = "${Environment.DIRECTORY_DOWNLOADS}/OzRetroRom/Sessions/$sessionId/"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, mimeType(safe))
                put(MediaStore.Downloads.RELATIVE_PATH, relative)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Android no pudo crear la copia temporal compatible con RetroArch.")
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    context.contentResolver.openOutputStream(uri, "w")?.use { output -> input.copyTo(output) }
                        ?: error("Android no pudo escribir la copia temporal compatible con RetroArch.")
                } ?: error("Android ya no permite leer la ROM persistente seleccionada.")
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (error: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw error
            }
            @Suppress("DEPRECATION")
            val path = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OzRetroRom/Sessions/$sessionId/$safe").absolutePath
            created += uri to path
            Entry(safe, path, uri)
        } else {
            @Suppress("DEPRECATION")
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OzRetroRom/Sessions/$sessionId").apply { mkdirs() }
            val target = File(folder, safe)
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: error("Android ya no permite leer la ROM persistente seleccionada.")
            created += null to target.absolutePath
            Entry(safe, target.absolutePath, null)
        }
    }

    /** Mirrors every persisted disc into one public session folder and writes a relative M3U. */
    fun publishSet(stored: PersistentRomLibrary.StoredRom): Entry {
        require(stored.isMultiDisc) { "La ROM guardada no es un conjunto multidisco." }
        val ordered = stored.members.sortedBy { it.discIndex }
        ordered.forEach { member -> publish(member.uri, member.displayName) }
        return publishText(
            displayName = stored.displayName,
            text = M3uPlaylist.content(ordered.map { it.displayName }),
        )
    }

    private fun publishText(displayName: String, text: String): Entry {
        val safe = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180).ifBlank { "game.m3u" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative = "${Environment.DIRECTORY_DOWNLOADS}/OzRetroRom/Sessions/$sessionId/"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, "audio/x-mpegurl")
                put(MediaStore.Downloads.RELATIVE_PATH, relative)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Android no pudo crear la lista multidisco temporal.")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output -> output.write(bytes) }
                    ?: error("Android no pudo escribir la lista multidisco temporal.")
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
            } catch (error: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw error
            }
            @Suppress("DEPRECATION")
            val path = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "OzRetroRom/Sessions/$sessionId/$safe",
            ).absolutePath
            created += uri to path
            Entry(safe, path, uri)
        } else {
            @Suppress("DEPRECATION")
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "OzRetroRom/Sessions/$sessionId",
            ).apply { mkdirs() }
            val target = File(folder, safe)
            FileOutputStream(target).use { it.write(bytes) }
            created += null to target.absolutePath
            Entry(safe, target.absolutePath, null)
        }
    }

    fun deleteAll() {
        created.forEach { (uri, path) ->
            if (uri != null) runCatching { context.contentResolver.delete(uri, null, null) }
            else runCatching { File(path).delete() }
        }
        @Suppress("DEPRECATION")
        runCatching { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OzRetroRom/Sessions/$sessionId").deleteRecursively() }
        created.clear()
    }

    private fun mimeType(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "zip" -> "application/zip"
        "iso" -> "application/x-iso9660-image"
        "cue", "m3u" -> "text/plain"
        else -> "application/octet-stream"
    }
}

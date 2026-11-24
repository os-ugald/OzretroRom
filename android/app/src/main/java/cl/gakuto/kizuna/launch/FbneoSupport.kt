package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

object FbneoSupport {
    val knownBiosSets = listOf(
        "bubsys", "cchip", "decocass", "isgsm", "midssio", "msx", "namcoc69", "namcoc70",
        "namcoc75", "neogeo", "nmk004", "pgm", "skns", "ym2608",
    )

    fun expectedArchiveName(game: GameEntity): String? {
        if (game.systemId != "arcade-fbneo") return null
        val set = game.romSet?.trim()?.removeSuffix(".zip")?.takeIf(String::isNotBlank) ?: return null
        return "$set.zip"
    }

    suspend fun copyAvailableBios(
        context: Context,
        treeUri: Uri?,
        store: SessionFileStore,
        requested: List<String>,
        diagnostics: DiagnosticLogger.Session,
    ): Set<String> = withContext(Dispatchers.IO) {
        if (treeUri == null) return@withContext emptySet()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptySet()
        val files = root.listFiles().associateBy { it.name?.lowercase().orEmpty() }
        val wanted = (requested.ifEmpty { knownBiosSets }).map { it.removeSuffix(".zip").lowercase() }.distinct()
        val copied = linkedSetOf<String>()
        wanted.forEach { setName ->
            val source = files["$setName.zip"] ?: return@forEach
            val target = store.file("$setName.zip")
            context.contentResolver.openInputStream(source.uri)?.use { raw ->
                BufferedInputStream(raw, 256 * 1024).use { input ->
                    BufferedOutputStream(target.outputStream(), 256 * 1024).use { output -> input.copyTo(output, 256 * 1024) }
                }
            }
            if (target.length() > 0L) {
                copied += setName
                diagnostics.info("FBNEO_BIOS_READY", "bios=$setName file=${target.name} bytes=${target.length()}")
            }
        }
        copied
    }
}

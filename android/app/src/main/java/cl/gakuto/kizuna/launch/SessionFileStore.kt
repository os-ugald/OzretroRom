package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class SessionFileStore(
    private val context: Context,
    sessionId: String,
) {
    val directory: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "sessions/$sessionId").apply { mkdirs() }

    fun file(displayName: String): File {
        if (!directory.exists()) directory.mkdirs()
        val safe = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(180).ifBlank { "game.rom" }
        return File(directory, safe)
    }

    fun candidateFile(owner: HttpPartialOwner, displayName: String): File {
        val target = File(directory, candidatePartialRelativePath(owner, displayName))
        target.parentFile?.mkdirs()
        return target
    }

    fun resolveRelative(relativePath: String): File? {
        val candidate = File(directory, relativePath)
        val root = directory.canonicalFile
        val resolved = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        return resolved.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    fun promoteForCheckpoint(file: File): File {
        if (file.parentFile?.canonicalFile == directory.canonicalFile) return file
        val expectedLength = file.length()
        val target = file(file.name)
        if (target.exists()) target.delete()
        if (!file.renameTo(target)) {
            file.copyTo(target, overwrite = true)
            if (target.isFile && target.length() == expectedLength) file.delete()
        }
        require(target.isFile && target.length() == expectedLength) {
            "No fue posible conservar el disco preparado en staging."
        }
        return target
    }

    /** Keep each disc independent even when two source archives both contain e.g. game.chd.
     * Copy instead of moving: candidate-owned HTTP bytes/validators remain available for resume.
     * Unique generations preserve the previous checkpoint if copying or validation is interrupted.
     */
    fun promoteDiscForCheckpoint(file: File, discIndex: Int): File {
        require(discIndex > 0 && file.isFile) { "El disco preparado no existe o tiene un índice inválido." }
        val target = generateSequence(0) { it + 1 }.map { generation ->
            val prefix = if (generation == 0) "disc-$discIndex-" else "disc-$discIndex-$generation-"
            file(prefix + file.name.takeLast(150))
        }.first { !it.exists() }
        file.copyTo(target, overwrite = false)
        require(target.isFile && target.length() == file.length()) {
            "No fue posible conservar el disco preparado en staging."
        }
        return target
    }

    fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    fun deleteAll() = runCatching { directory.deleteRecursively() }

}

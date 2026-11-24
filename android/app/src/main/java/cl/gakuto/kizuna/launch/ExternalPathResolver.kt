package cl.gakuto.kizuna.launch

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Converts local Android storage-document URIs into the path expected by RetroArch launch extras.
 *
 * Oz still keeps SAF permission and never assumes that arbitrary content providers map to files.
 * Only the Android external-storage provider and explicit file paths are converted.
 */
object ExternalPathResolver {
    fun resolve(reference: String): String? {
        val decoded = Uri.decode(reference).trim('\"', '\'')
        if (decoded.startsWith("/")) return decoded
        val uri = runCatching { Uri.parse(decoded) }.getOrNull() ?: return null
        if (uri.scheme.equals("file", true)) return uri.path
        if (!uri.scheme.equals("content", true)) return null
        if (!uri.authority.equals(EXTERNAL_STORAGE_AUTHORITY, true)) return null

        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }
            .recoverCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrNull()
            ?: return null
        val parts = documentId.split(':', limit = 2)
        val volume = parts.firstOrNull()?.takeIf(String::isNotBlank) ?: return null
        val relative = parts.getOrNull(1).orEmpty()
        val root = when {
            volume.equals("primary", true) -> Environment.getExternalStorageDirectory()
            volume.equals("home", true) -> File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOCUMENTS)
            else -> File("/storage", volume)
        }
        return if (relative.isBlank()) root.absolutePath else File(root, relative).absolutePath
    }

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
}

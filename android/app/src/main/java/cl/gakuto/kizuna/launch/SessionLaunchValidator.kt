package cl.gakuto.kizuna.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

/**
 * Validates every request reaching the exported SessionActivity.
 *
 * iiSU uses an explicit component and the ROM extra. Oz uses the internal gameId
 * extra. HTTP/HTTPS and other executable URI schemes are never accepted here;
 * network sources are resolved internally by ROMgi after a trusted gameId lookup.
 */
object SessionLaunchValidator {
    private const val MAX_REFERENCE_LENGTH = 8_192
    private val gameIdPattern = Regex("[A-Za-z0-9._-]{1,120}")

    private val allowedActions = setOf(
        SessionActivity.ACTION_PLAY,
        Intent.ACTION_VIEW,
    )

    private val allowedExtensions = setOf(
        // Oz/iiSU descriptors.
        "json", "zip", "omc", "ozr", "gkz",
        // Optical media and playlists.
        "cue", "chd", "iso", "bin", "img", "ccd", "sub", "mdf", "m3u", "gdi", "cso", "pbp",
        "rvz", "gcz", "wbfs", "wad",
        // Cartridge and computer formats.
        "7z", "nes", "fds", "sfc", "smc", "gb", "gbc", "gba", "n64", "z64", "v64", "nds",
        "3ds", "cia", "md", "gen", "smd", "sms", "gg", "32x", "pce", "sgx", "a26", "a52",
        "a78", "j64", "lnx", "ws", "wsc", "ngp", "ngc", "d64", "t64", "tap", "adf", "hdf",
        "ipf", "dsk", "rom", "elf",
    )

    fun requireAllowedAction(intent: Intent) {
        val action = intent.action ?: return // Explicit iiSU/internal launch.
        if (action in allowedActions) return

        // Android's shell-style explicit component launch (used by iiSU custom emulator commands)
        // commonly arrives as ACTION_MAIN. Accept it only for the exact exported SessionActivity
        // AND only when the trusted bridge contract includes the ROM descriptor extra. Arbitrary
        // MAIN intents remain rejected; the path/content URI is validated immediately afterwards.
        val explicitIisuMain = action == Intent.ACTION_MAIN &&
            !intent.getStringExtra(SessionActivity.EXTRA_IIISU_ROM).isNullOrBlank() &&
            intent.component?.className == SessionActivity::class.java.name
        require(explicitIisuMain) { "Acción externa no permitida: $action" }
    }

    fun validatedGameId(raw: String?): String? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        require(gameIdPattern.matches(value)) { "Identificador de juego inválido." }
        return value
    }

    fun validatedReference(context: Context, intent: Intent, raw: String): String {
        require(raw.length in 1..MAX_REFERENCE_LENGTH) { "La referencia de ROM es demasiado larga o está vacía." }
        require('\u0000' !in raw) { "La referencia de ROM contiene caracteres no permitidos." }

        val value = Uri.decode(raw).trim().trim('"', '\'')
        require(value.isNotBlank()) { "La referencia de ROM está vacía." }
        require(!containsTraversal(value)) { "La referencia de ROM contiene navegación de directorios no permitida." }

        val uri = runCatching { Uri.parse(value) }.getOrNull()
        return when (uri?.scheme?.lowercase(Locale.ROOT)) {
            null, "" -> validateFilePath(value)
            "file" -> validateFilePath(uri.path.orEmpty())
            "content" -> validateContentUri(context, intent, uri)
            else -> throw IllegalArgumentException("Esquema de URI no permitido: ${uri.scheme}")
        }
    }

    private fun validateFilePath(rawPath: String): String {
        require(rawPath.startsWith('/')) { "iiSU debe entregar una ruta absoluta o content://." }
        val file = File(rawPath)
        val canonical = runCatching { file.canonicalFile }.getOrElse {
            throw IllegalArgumentException("No se pudo validar la ruta de la ROM.", it)
        }
        require(canonical.isFile && canonical.canRead()) { "La ROM indicada no existe o no puede leerse." }
        requireAllowedExtension(canonical.name)
        return canonical.absolutePath
    }

    private fun validateContentUri(context: Context, intent: Intent, uri: Uri): String {
        val hasGrantFlag = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        val alreadyGranted = context.checkUriPermission(
            uri,
            android.os.Process.myPid(),
            android.os.Process.myUid(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
        require(hasGrantFlag || alreadyGranted) { "La aplicación remitente no concedió permiso de lectura para la ROM." }

        val name = queryDisplayName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: throw IllegalArgumentException("No se pudo identificar el archivo recibido.")
        requireAllowedExtension(name)

        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            require(descriptor.statSize != 0L) { "El archivo recibido está vacío." }
        } ?: throw IllegalArgumentException("No se pudo abrir el archivo recibido.")
        return uri.toString()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun requireAllowedExtension(name: String) {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension in allowedExtensions) {
            "Tipo de archivo no permitido: ${extension.ifBlank { "sin extensión" }}"
        }
    }

    private fun containsTraversal(value: String): Boolean {
        val normalized = value.replace('\\', '/')
        return normalized.split('/').any { it == ".." } ||
            normalized.contains("%2e%2e", ignoreCase = true)
    }
}

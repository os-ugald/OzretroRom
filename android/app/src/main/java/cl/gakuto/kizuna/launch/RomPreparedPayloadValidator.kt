package cl.gakuto.kizuna.launch

import java.io.File

/**
 * Validates the shape of the final payload before it reaches native emulator code.
 *
 * Binary ROMs must be large enough not to be bridge descriptors or truncated downloads. An M3U
 * playlist is intentionally tiny, so it is validated by structure and by checking every sibling
 * disc instead of applying the binary-size threshold to the playlist itself.
 */
object RomPreparedPayloadValidator {
    fun validate(file: File) {
        if (file.extension.equals("m3u", ignoreCase = true)) {
            validateMultiDiscPlaylist(file)
        } else {
            require(file.length() > MIN_BINARY_PAYLOAD_BYTES) {
                "El archivo preparado está vacío o incompleto."
            }
        }
    }

    private fun validateMultiDiscPlaylist(playlist: File) {
        require(playlist.length() in 1..MAX_PLAYLIST_BYTES) {
            "La lista multidisco está vacía o tiene un tamaño inválido."
        }
        val discNames = playlist.useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith('#') }
                .toList()
        }
        require(discNames.size >= 2) {
            "La lista multidisco no contiene al menos dos discos."
        }
        require(discNames.distinct().size == discNames.size) {
            "La lista multidisco contiene discos duplicados."
        }

        val folder = playlist.parentFile
            ?: error("La lista multidisco no tiene una carpeta de trabajo válida.")
        discNames.forEachIndexed { index, discName ->
            require(isSafeSiblingName(discName)) {
                "La entrada ${index + 1} de la lista multidisco no es un nombre relativo seguro."
            }
            val disc = File(folder, discName)
            require(disc.isFile && disc.canRead()) {
                "No se encontró el disco ${index + 1} junto a la lista multidisco."
            }
            require(disc.length() > MIN_BINARY_PAYLOAD_BYTES) {
                "El disco ${index + 1} está vacío o incompleto."
            }
        }
    }

    private fun isSafeSiblingName(value: String): Boolean =
        value.isNotBlank() &&
            !File(value).isAbsolute &&
            '/' !in value &&
            '\\' !in value &&
            ':' !in value &&
            value != "." &&
            value != ".."

    private const val MIN_BINARY_PAYLOAD_BYTES = 4_096L
    private const val MAX_PLAYLIST_BYTES = 64L * 1_024L
}

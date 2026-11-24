package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.util.TextNormalizer
import java.io.BufferedReader
import java.io.Reader

/** Parses Libretro's official metadata fragments under metadat/genre. */
class GenreDatParser {
    fun parse(reader: Reader): Map<String, String> {
        val buffered = if (reader is BufferedReader) reader else reader.buffered()
        val result = LinkedHashMap<String, String>()
        var inGame = false
        var depth = 0
        var comment: String? = null
        var name: String? = null
        var genre: String? = null

        buffered.forEachLine { raw ->
            val line = raw.trim()
            if (!inGame && (line.startsWith("game (") || line == "game(")) {
                inGame = true
                depth = line.count { it == '(' } - line.count { it == ')' }
                comment = null
                name = null
                genre = null
                return@forEachLine
            }
            if (!inGame) return@forEachLine
            depth += line.count { it == '(' } - line.count { it == ')' }
            if (comment == null) comment = value(line, "comment")
            if (name == null) name = value(line, "name")
            if (genre == null) genre = value(line, "genre")
            if (depth <= 0 || line == ")") {
                val title = comment?.takeIf(String::isNotBlank) ?: name?.takeIf(String::isNotBlank)
                val type = genre?.trim()?.takeIf(String::isNotBlank)
                if (title != null && type != null) result[TextNormalizer.normalize(title)] = type
                inGame = false
            }
        }
        return result
    }

    private fun value(line: String, key: String): String? {
        if (!line.startsWith("$key ")) return null
        val raw = line.removePrefix(key).trim()
        return if (raw.startsWith('"') && raw.endsWith('"') && raw.length >= 2) raw.substring(1, raw.length - 1)
        else raw.takeIf(String::isNotBlank)
    }
}

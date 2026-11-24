package cl.gakuto.kizuna.catalog

import java.io.BufferedReader
import java.io.Reader

data class DatGame(
    val name: String,
    val description: String,
    val region: String? = null,
    val releaseYear: Int? = null,
    val romName: String? = null,
)

/**
 * Streaming parser for Libretro's clrmamepro DAT files.
 *
 * It never keeps the complete catalog in memory: entries are returned in bounded batches.
 * No-Intro, Redump and FBNeo DATs currently use this text format in libretro-database.
 */
class LibretroDatParser {
    fun parse(reader: Reader, batchSize: Int = 500, onBatch: (List<DatGame>) -> Unit): Int {
        var total = 0
        batches(reader, batchSize).forEach { batch ->
            onBatch(batch)
            total += batch.size
        }
        return total
    }

    fun batches(reader: Reader, batchSize: Int = 500): Sequence<List<DatGame>> = sequence {
        require(batchSize > 0) { "batchSize must be greater than zero" }
        val buffered = if (reader is BufferedReader) reader else reader.buffered()
        val batch = ArrayList<DatGame>(batchSize)
        var inGame = false
        var depth = 0
        var name: String? = null
        var description: String? = null
        var region: String? = null
        var releaseYear: Int? = null
        var romName: String? = null
        while (true) {
            val raw = buffered.readLine() ?: break
            val line = raw.trim()
            if (!inGame && isGameStart(line)) {
                inGame = true
                depth = line.count { it == '(' } - line.count { it == ')' }
                name = null
                description = null
                region = null
                releaseYear = null
                romName = null
                continue
            }
            if (!inGame) continue

            depth += line.count { it == '(' } - line.count { it == ')' }
            if (name == null) name = value(line, "name")
            if (description == null) description = value(line, "description")
            if (region == null) region = value(line, "region")
            if (releaseYear == null) {
                releaseYear = value(line, "releaseyear")?.toIntOrNull()
                    ?: value(line, "year")?.toIntOrNull()
            }
            if (romName == null && line.startsWith("rom (")) romName = inlineAttribute(line, "name")

            if (depth <= 0 || line == ")") {
                val cleanName = name?.takeIf { it.isNotBlank() }
                if (cleanName != null) {
                    batch += DatGame(
                        name = cleanName,
                        description = description?.takeIf { it.isNotBlank() } ?: cleanName,
                        region = region?.takeIf { it.isNotBlank() },
                        releaseYear = releaseYear,
                        romName = romName?.takeIf { it.isNotBlank() },
                    )
                    if (batch.size >= batchSize) {
                        yield(batch.toList())
                        batch.clear()
                    }
                }
                inGame = false
                depth = 0
            }
        }
        if (batch.isNotEmpty()) yield(batch.toList())
    }

    private fun isGameStart(line: String): Boolean =
        line.startsWith("game (") || line == "game(" || line == "game ("

    private fun value(line: String, key: String): String? {
        if (!line.startsWith("$key ")) return null
        val raw = line.removePrefix(key).trim()
        if (raw.startsWith('"') && raw.endsWith('"') && raw.length >= 2) {
            return raw.substring(1, raw.length - 1)
        }
        return raw.takeIf { it.isNotBlank() }
    }

    private fun inlineAttribute(line: String, key: String): String? {
        val marker = Regex("(?:^|\\s)${Regex.escape(key)}\\s+")
        val match = marker.find(line) ?: return null
        val start = match.range.last + 1
        if (start >= line.length) return null
        val remainder = line.substring(start).trimStart()
        return if (remainder.startsWith('"')) {
            val closing = remainder.indexOf('"', startIndex = 1)
            if (closing > 1) remainder.substring(1, closing) else null
        } else {
            remainder.substringBefore(' ').substringBefore(')').takeIf { it.isNotBlank() }
        }
    }
}

package cl.gakuto.kizuna.launch

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Parses BTIH values from opaque magnet URIs without Android Uri query APIs. */
object MagnetInfoHashParser {
    fun parse(magnet: String): String {
        val query = magnet.substringAfter('?', missingDelimiterValue = "")
        val xt = query
            .split('&')
            .asSequence()
            .mapNotNull { parameter ->
                val key = parameter.substringBefore('=', missingDelimiterValue = parameter)
                val value = parameter.substringAfter('=', missingDelimiterValue = "")
                if (decode(key).equals("xt", ignoreCase = true)) decode(value) else null
            }
            .firstOrNull { it.startsWith("urn:btih:", ignoreCase = true) }
            ?: error("El magnet no contiene un infohash BTIH.")
        val raw = xt.substringAfterLast(':').trim()
        return when {
            raw.matches(Regex("[A-Fa-f0-9]{40}")) -> raw.lowercase(Locale.ROOT)
            raw.matches(Regex("[A-Z2-7a-z2-7]{32}")) -> base32ToHex(raw.uppercase(Locale.ROOT))
            else -> error("El infohash del magnet no tiene un formato compatible.")
        }
    }

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun base32ToHex(value: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val output = ArrayList<Byte>()
        value.forEach { char ->
            val index = alphabet.indexOf(char)
            require(index >= 0) { "Infohash Base32 inválido." }
            buffer = (buffer shl 5) or index
            bits += 5
            if (bits >= 8) {
                bits -= 8
                output += ((buffer shr bits) and 0xFF).toByte()
            }
        }
        return output.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}

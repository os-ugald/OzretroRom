package cl.gakuto.kizuna.util

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
    fun slug(value: String): String = normalize(value).replace(' ', '-').take(120).trim('-')
}

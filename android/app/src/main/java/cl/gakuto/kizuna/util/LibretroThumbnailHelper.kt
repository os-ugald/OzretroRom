package cl.gakuto.kizuna.util

import java.net.URLEncoder
import java.util.Locale

/**
 * Pure-JVM helper for building exact Libretro Thumbnails GitHub raw URLs and resilient fallback
 * chains across untagged titles, region suffixes, ": " -> " - " punctuation, and ", The" articles.
 */
object LibretroThumbnailHelper {
    private val illegalChars = Regex("[&*/:`<>?\\\\|]")
    private val trailingTags = Regex("""\s*(?:\([^)]*\)|\[[^\]]*\])\s*""")
    private val leadingArticle = Regex("""^the\s+(.+)$""", RegexOption.IGNORE_CASE)

    private val systemToRepo = mapOf(
        "nes" to "Nintendo_-_Nintendo_Entertainment_System",
        "fds" to "Nintendo_-_Family_Computer_Disk_System",
        "snes" to "Nintendo_-_Super_Nintendo_Entertainment_System",
        "sfc" to "Nintendo_-_Super_Nintendo_Entertainment_System",
        "n64" to "Nintendo_-_Nintendo_64",
        "gb" to "Nintendo_-_Game_Boy",
        "gbc" to "Nintendo_-_Game_Boy_Color",
        "gba" to "Nintendo_-_Game_Boy_Advance",
        "nds" to "Nintendo_-_Nintendo_DS",
        "dsi" to "Nintendo_-_Nintendo_DSi",
        "3ds" to "Nintendo_-_Nintendo_3DS",
        "gc" to "Nintendo_-_GameCube",
        "gamecube" to "Nintendo_-_GameCube",
        "wii" to "Nintendo_-_Wii",
        "genesis" to "Sega_-_Mega_Drive_-_Genesis",
        "md" to "Sega_-_Mega_Drive_-_Genesis",
        "megadrive" to "Sega_-_Mega_Drive_-_Genesis",
        "sms" to "Sega_-_Master_System_-_Mark_III",
        "master-system" to "Sega_-_Master_System_-_Mark_III",
        "gg" to "Sega_-_Game_Gear",
        "game-gear" to "Sega_-_Game_Gear",
        "segacd" to "Sega_-_Mega-CD_-_Sega_CD",
        "sega-cd" to "Sega_-_Mega-CD_-_Sega_CD",
        "sega32x" to "Sega_-_32X",
        "sega-32x" to "Sega_-_32X",
        "saturn" to "Sega_-_Saturn",
        "dc" to "Sega_-_Dreamcast",
        "dreamcast" to "Sega_-_Dreamcast",
        "ps1" to "Sony_-_PlayStation",
        "psx" to "Sony_-_PlayStation",
        "ps2" to "Sony_-_PlayStation_2",
        "psp" to "Sony_-_PlayStation_Portable",
        "arcade" to "FBNeo_-_Arcade_Games",
        "arcade-fbneo" to "FBNeo_-_Arcade_Games",
        "fbneo" to "FBNeo_-_Arcade_Games",
        "mame" to "MAME",
        "neogeo" to "SNK_-_Neo_Geo",
        "ngp" to "SNK_-_Neo_Geo_Pocket",
        "ngpc" to "SNK_-_Neo_Geo_Pocket_Color",
        "pce" to "NEC_-_PC_Engine_-_TurboGrafx_16",
        "pc-engine" to "NEC_-_PC_Engine_-_TurboGrafx_16",
        "pc-engine-cd" to "NEC_-_PC_Engine_CD_-_TurboGrafx-CD",
        "atari-2600" to "Atari_-_2600",
        "atari-7800" to "Atari_-_7800",
        "atari-lynx" to "Atari_-_Lynx",
        "wonderswan" to "Bandai_-_WonderSwan",
        "wonderswan-color" to "Bandai_-_WonderSwan_Color",
    )

    private val defaultRegionSuffixes = listOf(
        "(USA)",
        "(USA, Europe)",
        "(World)",
        "(Japan, USA)",
        "(Europe)",
        "(Japan)",
        "(USA) (En,Fr,Es)",
    )

    fun repoForSystem(systemId: String?): String? {
        val key = systemId.orEmpty().trim().lowercase(Locale.ROOT)
        return systemToRepo[key]
    }

    fun normalizeRepoName(repo: String): String =
        repo.trim().replace(' ', '_')

    fun sanitizeFileStem(name: String): String =
        illegalChars.replace(name.trim(), "_")

    fun buildUrl(repo: String, folder: String, name: String): String {
        val ghRepo = normalizeRepoName(repo)
        val cleanName = sanitizeFileStem(name) + ".png"
        val encodedRepo = encodeSegment(ghRepo, keepParensAndComma = false)
        val encodedFile = encodeSegment(cleanName, keepParensAndComma = true)
        return "https://raw.githubusercontent.com/libretro-thumbnails/$encodedRepo/master/$folder/$encodedFile"
    }

    fun candidateUrls(
        repo: String?,
        systemId: String,
        title: String,
        folders: List<String> = listOf("Named_Boxarts", "Named_Titles", "Named_Snaps"),
        maxUrls: Int = 14,
    ): List<String> {
        val resolvedRepo = repo?.takeIf { it.isNotBlank() }?.let(::normalizeRepoName)
            ?: repoForSystem(systemId)
            ?: return emptyList()

        val rawTitle = title.trim()
        if (rawTitle.isBlank()) return emptyList()

        val stems = buildCandidateStems(rawTitle, systemId)
        val urls = LinkedHashSet<String>(maxUrls)
        val boxFolder = folders.firstOrNull() ?: "Named_Boxarts"

        // First try primary stems across Boxarts (highest probability matches)
        for (stem in stems) {
            urls += buildUrl(resolvedRepo, boxFolder, stem)
            if (urls.size >= maxUrls - 2) break
        }
        // Fallback across secondary folders (Titles / Snaps) with top 2 stems
        val topStems = stems.take(2)
        for (folder in folders.drop(1)) {
            for (stem in topStems) {
                urls += buildUrl(resolvedRepo, folder, stem)
                if (urls.size >= maxUrls) break
            }
            if (urls.size >= maxUrls) break
        }
        return urls.toList()
    }

    private fun buildCandidateStems(rawTitle: String, systemId: String): List<String> {
        val out = LinkedHashSet<String>()
        val hasParenTags = '(' in rawTitle || '[' in rawTitle
        val sys = systemId.trim().lowercase(Locale.ROOT)
        val isArcade = sys in setOf("arcade", "arcade-fbneo", "fbneo", "mame")

        fun baseTransforms(input: String): List<String> {
            val variants = LinkedHashSet<String>()
            val colonToDash = input.replace(Regex("""\s*:\s*"""), " - ").trim()
            variants += colonToDash
            if (colonToDash != input) variants += input
            // Handle leading "The X..." -> "X, The..." and "The X - Sub" -> "X, The - Sub"
            val dashParts = colonToDash.split(" - ", limit = 2)
            val m = leadingArticle.find(dashParts[0].trim())
            if (m != null) {
                val moved = "${m.groupValues[1]}, The"
                variants += if (dashParts.size == 2) "$moved - ${dashParts[1]}" else moved
            }
            return variants.toList()
        }

        if (hasParenTags) {
            out.addAll(baseTransforms(rawTitle))
            val stripped = trailingTags.replace(rawTitle, " ").replace(Regex("""\s+"""), " ").trim()
            if (stripped.isNotBlank() && !isArcade) {
                val strippedVariants = baseTransforms(stripped)
                for (stem in strippedVariants.take(2)) {
                    for (suffix in listOf("(USA)", "(USA, Europe)", "(World)")) {
                        out += "$stem $suffix"
                    }
                }
            }
        } else {
            val transforms = baseTransforms(rawTitle)
            if (!isArcade) {
                val suffixes = when (sys) {
                    "ps1" -> listOf("(USA)", "(USA) (Disc 1)", "(USA, Europe)", "(Europe)", "(World)")
                    else -> defaultRegionSuffixes
                }
                for (suffix in suffixes) {
                    for (stem in transforms) {
                        out += "$stem $suffix"
                    }
                }
            }
            out.addAll(transforms)
        }
        return out.toList()
    }

    private fun encodeSegment(value: String, keepParensAndComma: Boolean): String {
        val encoded = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        return if (!keepParensAndComma) {
            encoded.replace("%2D", "-").replace("%5F", "_")
        } else {
            encoded
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%2C", ",")
                .replace("%27", "'")
                .replace("%21", "!")
        }
    }
}

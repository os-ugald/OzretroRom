package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.data.RomRegionPreference

enum class RomRegionProfile {
    ARCADE_EXACT,
    PORTABLE,
    TV_TIMING_SENSITIVE,
}

/**
 * Regional selection policy for ROMgi sources.
 *
 * The downloadable file is ranked, not just the broad ROMgi entry. USA/NTSC-U is the default;
 * Japan/NTSC-J is the next automatic choice. PAL never silently replaces an NTSC preference.
 */
object RomgiRegionPolicy {
    private val arcadeSystems = setOf("arcade", "arcade-fbneo", "fbneo")
    private val portableSystems = setOf(
        "atari-lynx",
        "lynx",
        "wonderswan",
        "wonderswan-color",
        "gb",
        "gbc",
        "gba",
        "nds",
        "dsi",
        "3ds",
        "n3ds",
        "pokemon-mini",
        "min",
        "game-gear",
        "gg",
        "ngp",
        "ngpc",
        "psp",
    )
    private val ntscRegions = setOf("USA", "Japan")

    fun profile(systemId: String): RomRegionProfile = when (systemId.trim().lowercase()) {
        in arcadeSystems -> RomRegionProfile.ARCADE_EXACT
        in portableSystems -> RomRegionProfile.PORTABLE
        else -> RomRegionProfile.TV_TIMING_SENSITIVE
    }

    fun effectivePreference(value: String): String = RomgiTitleMatcher.normalizeRegion(
        RomRegionPreference.fromStored(value).key,
    )

    fun order(systemId: String, originalRegion: String, preferredRegion: String): List<String> {
        val regionProfile = profile(systemId)
        if (regionProfile == RomRegionProfile.ARCADE_EXACT) return listOf("Other")

        val original = RomgiTitleMatcher.normalizeRegion(originalRegion)
        val preferred = effectivePreference(preferredRegion)
        val base = when (preferred) {
            "Japan" -> listOf("Japan", "USA", "World", "Asia", "Europe", "Other")
            "Europe" -> listOf("Europe", "World", "USA", "Japan", "Asia", "Other")
            "World" -> listOf("World", "USA", "Japan", "Asia", "Europe", "Other")
            "Asia" -> listOf("Asia", "Japan", "USA", "World", "Europe", "Other")
            else -> if (regionProfile == RomRegionProfile.PORTABLE) {
                listOf("USA", "Japan", "World", "Europe", "Asia", "Other")
            } else {
                listOf("USA", "Japan", "World", "Asia", "Europe", "Other")
            }
        }

        // A concrete catalog variant is an explicit edition choice and must stay first. Broad
        // World/Unknown seed metadata delegates the decision to the user's regional policy.
        return if (RomgiTitleMatcher.isBroadRegion(original)) {
            base
        } else {
            listOf(original) + base.filterNot { it == original }
        }
    }

    fun rank(systemId: String, originalRegion: String, preferredRegion: String, candidateRegion: String): Int {
        if (profile(systemId) == RomRegionProfile.ARCADE_EXACT) return 0
        val order = order(systemId, originalRegion, preferredRegion)
        val candidate = RomgiTitleMatcher.normalizeRegion(candidateRegion)
        return order.indexOf(candidate).takeIf { it >= 0 } ?: order.size
    }

    fun requiresConfirmation(
        systemId: String,
        originalRegion: String,
        preferredRegion: String,
        candidateRegion: String,
    ): Boolean {
        val regionProfile = profile(systemId)
        if (regionProfile == RomRegionProfile.ARCADE_EXACT) return false

        val original = RomgiTitleMatcher.normalizeRegion(originalRegion)
        val preferred = effectivePreference(preferredRegion)
        val candidate = RomgiTitleMatcher.normalizeRegion(candidateRegion)

        // Selecting a concrete catalog variant is explicit; only a different concrete file is a
        // fallback. Broad seed metadata (the curated Oz catalog is mostly World) uses policy.
        if (!RomgiTitleMatcher.isBroadRegion(original)) return candidate != original
        if (candidate == preferred) return false

        // USA <-> Japan remains inside the requested NTSC policy. Europe/PAL, Asia, unknown and a
        // timing-unverified World dump require a visible choice on TV-oriented systems.
        if (preferred in ntscRegions && candidate in ntscRegions) return false
        if (regionProfile == RomRegionProfile.PORTABLE && candidate == "World") return false
        return true
    }
}

package cl.gakuto.kizuna.data

import cl.gakuto.kizuna.util.CatalogFilters

/**
 * Compact bridge between the Room catalog and ROMgi's platform-oriented catalog.
 * It is intentionally internal to the launch pipeline; iiSU descriptors continue to use gameId.
 */
data class GameDescriptor(
    val gameId: String,
    val title: String,
    val platform: String,
    val platformName: String,
    val platformShort: String,
    val core: String,
    val romSet: String? = null,
    val biosSets: List<String> = emptyList(),
    val region: String = "Unknown",
) {
    companion object {
        fun fromEntity(game: GameEntity): GameDescriptor = GameDescriptor(
            gameId = game.id,
            title = CatalogFilters.displayTitle(game.title),
            platform = romgiPlatform(game.systemId),
            platformName = game.platform,
            platformShort = game.systemId,
            core = game.coreId.orEmpty(),
            romSet = game.romSet,
            biosSets = game.biosSets,
            region = game.region,
        )

        fun bios(setName: String): GameDescriptor = GameDescriptor(
            gameId = "bios-$setName",
            title = "$setName BIOS",
            platform = "arcade",
            platformName = "Arcade / FinalBurn Neo",
            platformShort = "fbneo",
            core = "fbneo",
            romSet = setName.removeSuffix(".zip"),
            biosSets = emptyList(),
        )

        fun supports(systemId: String): Boolean = runCatching { romgiPlatform(systemId) }.isSuccess

        private fun romgiPlatform(systemId: String): String = when (systemId) {
            "arcade-fbneo" -> "arcade"
            "atari-2600" -> "a26"
            "atari-5200" -> "a52"
            "atari-7800" -> "a78"
            "atari-jaguar" -> "jag"
            "atari-lynx" -> "lynx"
            "colecovision" -> "cv"
            "pc-engine" -> "tg16"
            "pc-engine-cd" -> "tgcd"
            "pc-fx" -> "pcfx"
            "nes" -> "nes"
            "fds" -> "fds"
            "snes" -> "snes"
            "n64" -> "n64"
            "n64dd" -> "ndd"
            "gb" -> "gb"
            "gbc" -> "gbc"
            "gba" -> "gba"
            "nds" -> "nds"
            "dsi" -> "dsi"
            "3ds" -> "3ds"
            "gamecube" -> "gc"
            "wii" -> "wii"
            "pokemon-mini" -> "min"
            "virtual-boy" -> "vb"
            "master-system" -> "sms"
            "genesis" -> "smd"
            "game-gear" -> "gg"
            "sega-32x" -> "32x"
            "sega-cd" -> "scd"
            "saturn" -> "sat"
            "dreamcast" -> "dc"
            "ps1" -> "ps1"
            "ps2" -> "ps2"
            "psp" -> "psp"
            else -> error("ROMgi todavía no publica una ruta automática para $systemId")
        }
    }
}

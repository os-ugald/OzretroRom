package cl.gakuto.kizuna.ui.compose.theme

import androidx.compose.ui.graphics.Color
import java.util.Locale

data class ConsoleVisual(
    val shortLabel: String,
    val fullName: String,
    val accent: Color,
    val badgeBg: Color,
)

object ConsolePalette {
    private val map = mapOf(
        "nes" to ConsoleVisual("NES", "Nintendo NES", Color(0xFFFCA5A5), Color(0xFF2B161B)),
        "snes" to ConsoleVisual("SNES", "Super Nintendo", Color(0xFFC4B5FD), Color(0xFF1D1833)),
        "gb" to ConsoleVisual("GB", "Game Boy", Color(0xFFBEF264), Color(0xFF1B2413)),
        "gbc" to ConsoleVisual("GBC", "Game Boy Color", Color(0xFFA3E635), Color(0xFF182413)),
        "gba" to ConsoleVisual("GBA", "Game Boy Advance", Color(0xFFA5B4FC), Color(0xFF181E36)),
        "nds" to ConsoleVisual("NDS", "Nintendo DS", Color(0xFF93C5FD), Color(0xFF142033)),
        "3ds" to ConsoleVisual("3DS", "Nintendo 3DS", Color(0xFFF9A8D4), Color(0xFF2A1624)),
        "n64" to ConsoleVisual("N64", "Nintendo 64", Color(0xFF6EE7B7), Color(0xFF122820)),
        "gc" to ConsoleVisual("GameCube", "Nintendo GameCube", Color(0xFF93C5FD), Color(0xFF152035)),
        "wii" to ConsoleVisual("Wii", "Nintendo Wii", Color(0xFFBAE6FD), Color(0xFF132433)),
        "genesis" to ConsoleVisual("Genesis", "Mega Drive / Genesis", Color(0xFF7DD3FC), Color(0xFF122233)),
        "md" to ConsoleVisual("Genesis", "Mega Drive / Genesis", Color(0xFF7DD3FC), Color(0xFF122233)),
        "sms" to ConsoleVisual("Master Sys", "Sega Master System", Color(0xFF60A5FA), Color(0xFF131F33)),
        "gg" to ConsoleVisual("Game Gear", "Sega Game Gear", Color(0xFF818CF8), Color(0xFF181D35)),
        "segacd" to ConsoleVisual("Sega CD", "Sega CD / Mega-CD", Color(0xFF38BDF8), Color(0xFF102130)),
        "sega32x" to ConsoleVisual("32X", "Sega 32X", Color(0xFFFBBF24), Color(0xFF282012)),
        "saturn" to ConsoleVisual("Saturn", "Sega Saturn", Color(0xFFE9D5FF), Color(0xFF231730)),
        "dc" to ConsoleVisual("Dreamcast", "Sega Dreamcast", Color(0xFFFDBA74), Color(0xFF2B1D12)),
        "ps1" to ConsoleVisual("PS1", "PlayStation", Color(0xFF5EEAD4), Color(0xFF102627)),
        "ps2" to ConsoleVisual("PS2", "PlayStation 2", Color(0xFF93C5FD), Color(0xFF131D34)),
        "psp" to ConsoleVisual("PSP", "PlayStation Portable", Color(0xFFCBD5E1), Color(0xFF1A202C)),
        "arcade" to ConsoleVisual("Arcade", "Arcade (FBNeo / MAME)", Color(0xFFFCD34D), Color(0xFF292011)),
        "fbneo" to ConsoleVisual("Arcade", "Arcade (FBNeo)", Color(0xFFFCD34D), Color(0xFF292011)),
        "mame" to ConsoleVisual("MAME", "Arcade (MAME)", Color(0xFFFCD34D), Color(0xFF292011)),
        "pce" to ConsoleVisual("PC Engine", "PC Engine / TurboGrafx", Color(0xFFFDBA74), Color(0xFF281C14)),
        "msx" to ConsoleVisual("MSX", "MSX / MSX2", Color(0xFFCBD5E1), Color(0xFF191E28)),
    )

    fun forSystem(systemId: String?): ConsoleVisual {
        val key = systemId.orEmpty().trim().lowercase(Locale.ROOT)
        return map[key] ?: ConsoleVisual(
            shortLabel = key.ifBlank { "ROM" }.uppercase(Locale.ROOT).take(10),
            fullName = key.ifBlank { "Sistema Retro" }.uppercase(Locale.ROOT),
            accent = Color(0xFF7DD3FC),
            badgeBg = Color(0xFF181E29),
        )
    }

    fun parseHexColor(hex: String?, fallback: Color = Color(0xFFFFB000)): Color = runCatching {
        val clean = hex?.trim()?.removePrefix("#").orEmpty()
        if (clean.length == 6) Color(0xFF000000L or clean.toLong(16)) else fallback
    }.getOrDefault(fallback)
}

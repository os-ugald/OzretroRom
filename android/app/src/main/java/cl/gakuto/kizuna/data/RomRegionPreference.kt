package cl.gakuto.kizuna.data

/** Persisted user preference for ROMgi variants. Legacy `auto` values migrate to USA. */
enum class RomRegionPreference(val key: String, val label: String) {
    USA("usa", "USA / NTSC-U · recomendado"),
    JAPAN("japan", "Japón / NTSC-J"),
    WORLD("world", "World / Internacional"),
    EUROPE("europe", "Europa / PAL"),
    ASIA("asia", "Asia"),
    ;

    companion object {
        val DEFAULT: RomRegionPreference = USA

        fun fromStored(value: String?): RomRegionPreference {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == normalized } ?: DEFAULT
        }
    }
}

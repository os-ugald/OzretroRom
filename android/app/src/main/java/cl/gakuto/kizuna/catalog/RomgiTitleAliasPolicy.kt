package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.data.GameDescriptor

/**
 * Conservative aliases for catalog/display-name mismatches.
 *
 * Oz keeps the user-facing curated title intact. These aliases are used only while querying and
 * scoring ROMgi, where source catalogs may use original regional release names. Exact title
 * matches always outrank aliases.
 */
object RomgiTitleAliasPolicy {
    private val explicitAliases = mapOf(
        "castlevania-iv-snes" to listOf("Super Castlevania IV"),
        "final-fantasy-iv-snes" to listOf("Final Fantasy II"),
        "final-fantasy-vi-snes" to listOf("Final Fantasy III"),
        "the-legend-of-zelda-a-link-to-the-past-snes" to listOf("Legend of Zelda, The - A Link to the Past"),
    )

    fun aliasesFor(descriptor: GameDescriptor): List<String> = buildList {
        addAll(explicitAliases[descriptor.gameId].orEmpty())

        // Several SNES releases use the platform-era "Super" prefix in ROM catalogs while a
        // curated frontend may display the shorter title. Keep this one-way and platform-scoped
        // so unrelated systems are not broadened.
        if (descriptor.platformShort == "snes" &&
            !descriptor.title.startsWith("Super ", ignoreCase = true)
        ) {
            add("Super ${descriptor.title}")
        }
    }.map(String::trim).filter(String::isNotBlank).distinctBy { it.lowercase() }
}

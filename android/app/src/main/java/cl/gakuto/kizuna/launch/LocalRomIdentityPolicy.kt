package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameCatalogOrigin

/** Identity boundary between durable catalog records and legacy title-based library discovery. */
internal object LocalRomIdentityPolicy {
    fun hasStrongSourceIndexIdentity(game: GameEntity): Boolean =
        game.catalogOrigin == GameCatalogOrigin.SOURCE_INDEX

    /**
     * Legacy scanning is useful for catalog rows that predate durable source identity. A Source
     * Index projection must instead be associated explicitly by its exact GameEntity.id.
     */
    fun allowsLegacyLibraryScan(game: GameEntity): Boolean = !hasStrongSourceIndexIdentity(game)

    /** Mirrors the historical prefix fallback for diagnostics and collision regression tests. */
    fun legacyPrefixMatches(target: String, candidate: String): Boolean =
        candidate.startsWith("$target ")
}

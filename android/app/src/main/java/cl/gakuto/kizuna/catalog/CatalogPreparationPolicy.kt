package cl.gakuto.kizuna.catalog

/**
 * Pure readiness policy shared by the legacy and Compose catalog entry points.
 *
 * A READY row alone is not enough: an interrupted first download can leave an empty index, and
 * FBNeo needs a ROM-set identity for every indexed entry before Oz can resolve split archives.
 */
object CatalogPreparationPolicy {
    const val READY_STATUS = "READY"
    const val FBNEO_SYSTEM_ID = "arcade-fbneo"

    fun needsIndex(
        systemId: String,
        status: String?,
        indexedGames: Int,
        arcadeGamesWithRomSet: Int = 0,
    ): Boolean {
        if (indexedGames <= 0) return true
        if (status != READY_STATUS) return true
        return systemId == FBNEO_SYSTEM_ID && arcadeGamesWithRomSet < indexedGames
    }
}

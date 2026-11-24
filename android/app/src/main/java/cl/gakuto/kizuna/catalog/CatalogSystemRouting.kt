package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.util.PlatformFamily

internal enum class CatalogBackend { ROOM_CATALOG, SOURCE_INDEX }

/** Product-qualified system routing. Catalog registry presence alone never enables a system here. */
internal object CatalogSystemRouting {
    fun sourceIndexAvailable(systemId: String?): Boolean = systemId in SOURCE_INDEX_AVAILABLE_SYSTEM_IDS

    fun backendFor(systemId: String?): CatalogBackend = when {
        sourceIndexAvailable(systemId) -> CatalogBackend.SOURCE_INDEX
        else -> CatalogBackend.ROOM_CATALOG
    }

    /** Development-validation visibility; it does not imply launch validation or Oz certification. */
    fun eligibleSourceIndexSystems(systems: List<CatalogSystem>): List<CatalogSystem> = systems
        .filter { sourceIndexAvailable(it.id) }
        .sortedBy(CatalogSystem::name)

    fun familyFor(systemId: String): PlatformFamily = PlatformFamily.entries
        .firstOrNull { it != PlatformFamily.OTHER && systemId in it.systemIds }
        ?: PlatformFamily.OTHER

    const val SNES_SYSTEM_ID = "snes"
    const val NES_SYSTEM_ID = "nes"
    const val GENESIS_SYSTEM_ID = "genesis"
    const val GBA_SYSTEM_ID = "gba"
    private val SOURCE_INDEX_AVAILABLE_SYSTEM_IDS = setOf(SNES_SYSTEM_ID, NES_SYSTEM_ID, GENESIS_SYSTEM_ID, GBA_SYSTEM_ID)
}

internal object SourceIndexCatalogStatus {
    fun ready(systemName: String, controlledGames: Int): String =
        "$systemName: Oz Source Index local listo · $controlledGames juegos controlados"
}

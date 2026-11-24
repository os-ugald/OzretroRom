package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.data.GameEntity

/** Keeps user/runtime state when a deterministic Libretro row is refreshed or resumed. */
object CatalogIndexMergePolicy {
    fun merge(indexed: GameEntity, existing: GameEntity?): GameEntity {
        if (existing == null) return indexed
        return indexed.copy(
            contentUri = existing.contentUri,
            lastPlayedAt = existing.lastPlayedAt,
            addedAt = existing.addedAt,
            romgiAvailable = existing.romgiAvailable,
            libraryOrigin = existing.libraryOrigin,
        )
    }
}

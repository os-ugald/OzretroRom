package cl.gakuto.kizuna.data

/**
 * Controls which persisted collections are presented as cards in the Compose Collections screen.
 *
 * The legacy all-games row remains addressable by ID for compatibility with old routes and iiSU;
 * Catalog is the single global entry point in the current UI.
 */
internal object CollectionVisibilityPolicy {
    private const val ALL_GAMES_COLLECTION_ID = "all-games"
    const val SYSTEM_COLLECTION_PREFIX = "system-"

    fun isVisibleInCollections(collectionId: String): Boolean =
        collectionId != ALL_GAMES_COLLECTION_ID && !collectionId.startsWith(SYSTEM_COLLECTION_PREFIX)

    fun isSystemCollection(collectionId: String): Boolean =
        collectionId.startsWith(SYSTEM_COLLECTION_PREFIX)

    fun systemIdFromCollectionId(collectionId: String): String =
        collectionId.removePrefix(SYSTEM_COLLECTION_PREFIX)
}

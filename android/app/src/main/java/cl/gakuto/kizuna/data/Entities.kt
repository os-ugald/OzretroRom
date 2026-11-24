package cl.gakuto.kizuna.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

enum class LibraryOrigin { NONE, DOWNLOADED, IMPORTED }

/** Typed domain interpretation of the persisted catalog-source wire value. */
enum class GameCatalogOrigin(val persistedValue: String) {
    SOURCE_INDEX("oz-source-index"),
    LEGACY_OR_OTHER(""),
    ;

    companion object {
        fun fromPersisted(value: String): GameCatalogOrigin =
            if (value == SOURCE_INDEX.persistedValue) SOURCE_INDEX else LEGACY_OR_OTHER
    }
}

@Entity(
    tableName = "games",
    indices = [
        Index("normalizedTitle"),
        Index("canonicalTitle"),
        Index("systemId"),
        Index("genre"),
        Index("region"),
        Index("romSet"),
        Index(value = ["systemId", "canonicalTitle", "title"]),
        Index(value = ["systemId", "normalizedTitle"]),
        Index(value = ["source", "systemId"]),
        Index("lastPlayedAt"),
        Index("addedAt"),
        Index("romgiAvailable"),
    ],
)
data class GameEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val normalizedTitle: String,
    val platform: String,
    val systemId: String,
    val year: Int?,
    val region: String,
    val thumbnailRepo: String?,
    val source: String,
    val launchValidated: Boolean,
    val coreId: String?,
    val virtualExtension: String,
    val contentUri: String? = null,
    @ColumnInfo(defaultValue = "''") val canonicalTitle: String = "",
    @ColumnInfo(defaultValue = "'Sin clasificar'") val genre: String = "Sin clasificar",
    val romSet: String? = null,
    @ColumnInfo(defaultValue = "''") val biosSetsCsv: String = "",
    @ColumnInfo(defaultValue = "NULL") val lastPlayedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val addedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val romgiAvailable: Boolean = false,
    @ColumnInfo(defaultValue = "'NONE'") val libraryOrigin: String = LibraryOrigin.NONE.name,
) {
    val catalogOrigin: GameCatalogOrigin
        get() = GameCatalogOrigin.fromPersisted(source)

    val biosSets: List<String>
        get() = biosSetsCsv.split(',').map(String::trim).filter(String::isNotBlank).distinct()
}

enum class CollectionKind { AUTOMATIC, MANUAL, CURATED, SYSTEM }

@Entity(tableName = "collections")
data class CollectionEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val accent: String,
    val isSystem: Boolean,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "'CURATED'") val kind: String = CollectionKind.CURATED.name,
) {
    val collectionKind: CollectionKind
        get() = runCatching { CollectionKind.valueOf(kind) }.getOrDefault(CollectionKind.CURATED)
}

@Entity(
    tableName = "collection_memberships",
    primaryKeys = ["collectionId", "gameId"],
    indices = [Index("gameId")],
)
data class CollectionMembershipEntity(val collectionId: String, val gameId: String)

@Entity(tableName = "catalog_system_state")
data class CatalogSystemStateEntity(
    @androidx.room.PrimaryKey val systemId: String,
    val status: String,
    val indexedAt: Long?,
    val gameCount: Int,
    val error: String?,
)

@Entity(
    tableName = "game_sync_metadata",
    indices = [
        Index("hubEntryId"),
        Index("dirty"),
        Index("localUpdatedAt"),
    ],
)
data class GameSyncMetadataEntity(
    @androidx.room.PrimaryKey val gameId: String,
    val hubEntryId: String? = null,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val quickResumeJson: String? = null,
    val customArtworkUrl: String? = null,
    val customArtworkUri: String? = null,
    val syncedArtworkLocalUri: String? = null,
    val syncedArtworkTitle: String? = null,
    val syncedArtworkRepo: String? = null,
    val syncedGameIconLocalUri: String? = null,
    val cachedDescription: String? = null,
    val patchedRomPath: String? = null,
    val patchedRomLabel: String? = null,
    val patchedRomFormat: String? = null,
    @ColumnInfo(defaultValue = "0") val localUpdatedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val hubRevision: Long = 0L,
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @androidx.room.PrimaryKey val hubId: String,
    @ColumnInfo(defaultValue = "0") val lastRevision: Long = 0L,
    @ColumnInfo(defaultValue = "0") val lastSyncedAt: Long = 0L,
    val lastEventId: String? = null,
    @ColumnInfo(defaultValue = "'android'") val deviceId: String = "android",
)

package cl.gakuto.kizuna.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GameEntity::class,
        CollectionEntity::class,
        CollectionMembershipEntity::class,
        CatalogSystemStateEntity::class,
        GameSyncMetadataEntity::class,
        SyncStateEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class OzDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun collectionDao(): CollectionDao
    abstract fun catalogStateDao(): CatalogStateDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        @Volatile private var instance: OzDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN canonicalTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE games ADD COLUMN genre TEXT NOT NULL DEFAULT 'Sin clasificar'")
                db.execSQL("ALTER TABLE games ADD COLUMN romSet TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN biosSetsCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE games SET canonicalTitle = normalizedTitle WHERE canonicalTitle = ''")
                db.execSQL("UPDATE catalog_system_state SET status = 'STALE'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_canonicalTitle ON games(canonicalTitle)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_genre ON games(genre)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_region ON games(region)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_romSet ON games(romSet)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_systemId_canonicalTitle_title ON games(systemId, canonicalTitle, title)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_systemId_normalizedTitle ON games(systemId, normalizedTitle)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_source_systemId ON games(source, systemId)")
            }
        }


        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN lastPlayedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE games ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN romgiAvailable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE games SET addedAt = CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE addedAt = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_lastPlayedAt ON games(lastPlayedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_addedAt ON games(addedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_games_romgiAvailable ON games(romgiAvailable)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN kind TEXT NOT NULL DEFAULT 'CURATED'")
                db.execSQL("UPDATE collections SET kind='AUTOMATIC' WHERE id IN ('all-games','downloaded-library','favorites','recently-played')")
                db.execSQL("UPDATE collections SET kind='MANUAL' WHERE id='my-collection' OR id LIKE 'personal-%'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN libraryOrigin TEXT NOT NULL DEFAULT 'NONE'")
                // contentUri was introduced by the persistent library before origin tracking existed.
                db.execSQL("UPDATE games SET libraryOrigin='DOWNLOADED' WHERE COALESCE(contentUri,'')!=''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS game_sync_metadata (
                        gameId TEXT NOT NULL PRIMARY KEY,
                        hubEntryId TEXT,
                        favorite INTEGER NOT NULL DEFAULT 0,
                        playCount INTEGER NOT NULL DEFAULT 0,
                        lastPlayedAt INTEGER,
                        quickResumeJson TEXT,
                        customArtworkUrl TEXT,
                        customArtworkUri TEXT,
                        syncedArtworkLocalUri TEXT,
                        syncedArtworkTitle TEXT,
                        syncedArtworkRepo TEXT,
                        syncedGameIconLocalUri TEXT,
                        cachedDescription TEXT,
                        patchedRomPath TEXT,
                        patchedRomLabel TEXT,
                        patchedRomFormat TEXT,
                        localUpdatedAt INTEGER NOT NULL DEFAULT 0,
                        hubRevision INTEGER NOT NULL DEFAULT 0,
                        dirty INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_game_sync_metadata_hubEntryId ON game_sync_metadata(hubEntryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_game_sync_metadata_dirty ON game_sync_metadata(dirty)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_game_sync_metadata_localUpdatedAt ON game_sync_metadata(localUpdatedAt)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_state (
                        hubId TEXT NOT NULL PRIMARY KEY,
                        lastRevision INTEGER NOT NULL DEFAULT 0,
                        lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                        lastEventId TEXT,
                        deviceId TEXT NOT NULL DEFAULT 'android'
                    )
                    """.trimIndent(),
                )
            }
        }

        fun get(context: Context): OzDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OzDatabase::class.java, "oz-retrorom.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                .also { instance = it }
        }
    }
}

package cl.gakuto.kizuna.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OzDatabaseMigration6To7Test {

    @Test
    fun `migration 6 to 7 preserves v6 tables and enables sync metadata entities`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val dbName = "oz-migration-6-7-test.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // Recreate exact v6 schema from schemas/cl.gakuto.kizuna.data.OzDatabase/6.json
            rawDb.execSQL(
                "CREATE TABLE IF NOT EXISTS `games` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `normalizedTitle` TEXT NOT NULL, `platform` TEXT NOT NULL, `systemId` TEXT NOT NULL, `year` INTEGER, `region` TEXT NOT NULL, `thumbnailRepo` TEXT, `source` TEXT NOT NULL, `launchValidated` INTEGER NOT NULL, `coreId` TEXT, `virtualExtension` TEXT NOT NULL, `contentUri` TEXT, `canonicalTitle` TEXT NOT NULL DEFAULT '', `genre` TEXT NOT NULL DEFAULT 'Sin clasificar', `romSet` TEXT, `biosSetsCsv` TEXT NOT NULL DEFAULT '', `lastPlayedAt` INTEGER DEFAULT NULL, `addedAt` INTEGER NOT NULL DEFAULT 0, `romgiAvailable` INTEGER NOT NULL DEFAULT 0, `libraryOrigin` TEXT NOT NULL DEFAULT 'NONE', PRIMARY KEY(`id`))",
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_normalizedTitle` ON `games` (`normalizedTitle`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_canonicalTitle` ON `games` (`canonicalTitle`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_systemId` ON `games` (`systemId`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_genre` ON `games` (`genre`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_region` ON `games` (`region`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_romSet` ON `games` (`romSet`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_systemId_canonicalTitle_title` ON `games` (`systemId`, `canonicalTitle`, `title`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_systemId_normalizedTitle` ON `games` (`systemId`, `normalizedTitle`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_source_systemId` ON `games` (`source`, `systemId`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_lastPlayedAt` ON `games` (`lastPlayedAt`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_addedAt` ON `games` (`addedAt`)")
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_games_romgiAvailable` ON `games` (`romgiAvailable`)")

            rawDb.execSQL(
                "CREATE TABLE IF NOT EXISTS `collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `icon` TEXT NOT NULL, `accent` TEXT NOT NULL, `isSystem` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `kind` TEXT NOT NULL DEFAULT 'CURATED', PRIMARY KEY(`id`))",
            )
            rawDb.execSQL(
                "CREATE TABLE IF NOT EXISTS `collection_memberships` (`collectionId` TEXT NOT NULL, `gameId` TEXT NOT NULL, PRIMARY KEY(`collectionId`, `gameId`))",
            )
            rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_memberships_gameId` ON `collection_memberships` (`gameId`)")
            rawDb.execSQL(
                "CREATE TABLE IF NOT EXISTS `catalog_system_state` (`systemId` TEXT NOT NULL, `status` TEXT NOT NULL, `indexedAt` INTEGER, `gameCount` INTEGER NOT NULL, `error` TEXT, PRIMARY KEY(`systemId`))",
            )
            rawDb.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            rawDb.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd41fe00b20aa66861dec53db47c9ab14')")

            // Insert sample v6 records
            rawDb.execSQL(
                """
                INSERT INTO games (
                    id, title, normalizedTitle, platform, systemId, year, region, thumbnailRepo,
                    source, launchValidated, coreId, virtualExtension, contentUri, canonicalTitle,
                    genre, romSet, biosSetsCsv, lastPlayedAt, addedAt, romgiAvailable, libraryOrigin
                ) VALUES (
                    'snes-smw', 'Super Mario World (USA)', 'super mario world', 'Super Nintendo', 'snes',
                    1991, 'USA', 'Nintendo_-_Super_Nintendo_Entertainment_System', 'libretro', 1, 'snes9x',
                    'sfc', 'content://rom/smw', 'super mario world', 'Plataformas', NULL, '', 1700000000000,
                    1699999000000, 1, 'DOWNLOADED'
                )
                """.trimIndent(),
            )
            rawDb.version = 6
        } finally {
            rawDb.close()
        }

        val migratedDb = Room.databaseBuilder(context, OzDatabase::class.java, dbName)
            .addMigrations(OzDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        try {
            runBlocking {
                val preserved = migratedDb.gameDao().byId("snes-smw")
                assertNotNull(preserved)
                assertEquals("Super Mario World (USA)", preserved?.title)
                assertEquals("DOWNLOADED", preserved?.libraryOrigin)

                val syncDao = migratedDb.syncMetadataDao()
                syncDao.upsert(
                    GameSyncMetadataEntity(
                        gameId = "snes-smw",
                        hubEntryId = "hub-smw-01",
                        favorite = true,
                        playCount = 3,
                        lastPlayedAt = 1700000000000L,
                        quickResumeJson = """{"slot":1,"statePath":"/s/smw.state1","coreId":"snes9x"}""",
                        localUpdatedAt = 1700000001000L,
                        dirty = true,
                    ),
                )
                syncDao.upsertSyncState(
                    SyncStateEntity(
                        hubId = "default-pc-hub",
                        lastRevision = 12L,
                        lastSyncedAt = 1700000002000L,
                        deviceId = "android-test",
                    ),
                )

                val loaded = syncDao.byHubEntryId("hub-smw-01")
                assertNotNull(loaded)
                assertTrue(loaded?.favorite == true)
                assertEquals(3, loaded?.playCount)
                assertEquals(1, syncDao.dirtyItems().size)
                assertEquals(12L, syncDao.getSyncState("default-pc-hub")?.lastRevision)
            }
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }
}

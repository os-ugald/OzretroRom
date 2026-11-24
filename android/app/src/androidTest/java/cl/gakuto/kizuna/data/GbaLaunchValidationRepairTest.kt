package cl.gakuto.kizuna.data

import android.content.Context
import androidx.room.Room
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GbaLaunchValidationRepairTest {
    private lateinit var database: OzDatabase

    @Before fun setUp() {
        @Suppress("DEPRECATION")
        val context: Context = InstrumentationRegistry.getTargetContext()
        database = Room.inMemoryDatabaseBuilder(context, OzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = database.close()

    @Test fun repairClearsLegacyGbaAndPreservesRuntimeState() = runBlocking {
        val legacy = game("legacy-gba", "curated", "gba", launchValidated = true).copy(
            contentUri = "content://owned/gba",
            lastPlayedAt = 123L,
            addedAt = 456L,
            romgiAvailable = true,
            libraryOrigin = LibraryOrigin.IMPORTED.name,
        )
        val other = game("snes", "curated", "snes", launchValidated = true)
        val sourceIndex = game("gba-source", "oz-source-index", "gba", launchValidated = true)
        database.gameDao().insertAll(listOf(legacy, other, sourceIndex))

        assertEquals(1, database.gameDao().clearLegacyGbaLaunchValidation())
        val repaired = requireNotNull(database.gameDao().byId(legacy.id))
        assertFalse(repaired.launchValidated)
        assertEquals(legacy.contentUri, repaired.contentUri)
        assertEquals(legacy.lastPlayedAt, repaired.lastPlayedAt)
        assertEquals(legacy.addedAt, repaired.addedAt)
        assertEquals(legacy.romgiAvailable, repaired.romgiAvailable)
        assertEquals(legacy.libraryOrigin, repaired.libraryOrigin)
        assertTrue(requireNotNull(database.gameDao().byId(other.id)).launchValidated)
        assertTrue(requireNotNull(database.gameDao().byId(sourceIndex.id)).launchValidated)
    }

    @Test fun repairIsIdempotent() = runBlocking {
        database.gameDao().insertAll(listOf(game("gba", "libretro", "gba", launchValidated = true)))
        assertEquals(1, database.gameDao().clearLegacyGbaLaunchValidation())
        assertEquals(0, database.gameDao().clearLegacyGbaLaunchValidation())
        assertFalse(requireNotNull(database.gameDao().byId("gba")).launchValidated)
    }

    private fun game(id: String, source: String, systemId: String, launchValidated: Boolean) = GameEntity(
        id = id,
        title = id,
        normalizedTitle = id,
        platform = systemId,
        systemId = systemId,
        year = null,
        region = "World",
        thumbnailRepo = null,
        source = source,
        launchValidated = launchValidated,
        coreId = null,
        virtualExtension = "gba",
    )
}

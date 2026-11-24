package cl.gakuto.kizuna.data

import android.app.Application
import cl.gakuto.kizuna.catalog.background.CatalogPreparationTaskStore
import cl.gakuto.kizuna.launch.CollectionBatchTaskStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OzSettingsDataStoreAndTaskFlowsTest {

    @Test
    fun `app preferences persist settings via reactive DataStore and per-game data in Room v7`() {
        val app: Application = RuntimeEnvironment.getApplication()
        val prefs = AppPreferences(app)

        prefs.pcHubUrl = "http://192.168.100.173:17894"
        prefs.autoLaunchAfterDownload = false
        prefs.geminiModel = "gemini-2.5-pro"
        prefs.setCustomArtworkUrl("snes-smw", "https://cdn.example.org/smw.webp")
        prefs.setCachedDescription("snes-smw", "Super Mario World SNES.")

        runBlocking {
            prefs.settingsStore.flush()
            val storedHubUrl = prefs.settingsStore.stringFlow(AppPreferences.KEY_PC_HUB_URL, "").first()
            assertEquals("http://192.168.100.173:17894", storedHubUrl)
        }

        val reloaded = AppPreferences(app)
        assertEquals("http://192.168.100.173:17894", reloaded.pcHubUrl)
        assertFalse(reloaded.autoLaunchAfterDownload)
        assertEquals("gemini-2.5-pro", reloaded.geminiModel)
        assertEquals("https://cdn.example.org/smw.webp", reloaded.customArtworkUrl("snes-smw"))
        assertEquals("Super Mario World SNES.", reloaded.cachedDescription("snes-smw"))
    }

    @Test
    fun `task stores emit reactive StateFlow snapshots on mutations`() {
        val app: Application = RuntimeEnvironment.getApplication()
        val catStore = CatalogPreparationTaskStore(app)
        val batchStore = CollectionBatchTaskStore(app)

        val createdCat = catStore.create(listOf("snes", "nes"), completeCatalog = false)
        assertTrue(createdCat.isActive)
        assertEquals(createdCat, CatalogPreparationTaskStore.observe(app).value)

        val createdBatch = batchStore.create("favs", "Favoritos", totalHint = 5)
        assertTrue(createdBatch.isActive)
        runBlocking {
            val observedBatch = CollectionBatchTaskStore.observe(app, "favs").first()
            assertNotNull(observedBatch)
            assertEquals("Favoritos", observedBatch?.collectionName)
        }
    }
}

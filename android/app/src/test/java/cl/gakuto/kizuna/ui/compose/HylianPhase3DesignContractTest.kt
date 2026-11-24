package cl.gakuto.kizuna.ui.compose

import android.app.Application
import androidx.room.Room
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.R
import java.io.File
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryOrigin
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.hub.HubSyncRepository
import cl.gakuto.kizuna.ui.compose.components.HYLIAN_SPLASH_DURATION_MS
import cl.gakuto.kizuna.ui.compose.components.HYLIAN_SPLASH_FADE_OUT_MS
import cl.gakuto.kizuna.ui.compose.components.OZ_IMMERSIVE_COVER_ASPECT_RATIO
import cl.gakuto.kizuna.ui.compose.domain.HomeDomainHolder
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
class HylianPhase3DesignContractTest {

    @Test
    fun `hylian dark palette tokens and 3 to 4 cover constants match design spec`() {
        OzColors.use(AppThemeMode.DARK)
        assertEquals(0xFF0A0F0D, OzColors.HylianBg.value.toLong() ushr 32)
        assertEquals(0xFF141F1A, OzColors.HylianSurface.value.toLong() ushr 32)
        assertEquals(0xFF23382E, OzColors.HylianBorder.value.toLong() ushr 32)
        assertEquals(0xFF1E8E63, OzColors.HylianEmerald.value.toLong() ushr 32)
        assertEquals(0xFFD9B64E, OzColors.HylianGold.value.toLong() ushr 32)
        assertEquals(0xFF0A0F0D, OzColors.DeepSpace.value.toLong() ushr 32)
        assertEquals(0xFF141F1A, OzColors.Panel.value.toLong() ushr 32)
        assertEquals(0xFF1E8E63, OzColors.ArcadeViolet.value.toLong() ushr 32)
        assertEquals(0xFFD9B64E, OzColors.Gold.value.toLong() ushr 32)

        assertEquals(0.75f, OZ_IMMERSIVE_COVER_ASPECT_RATIO, 0.0001f)
        assertEquals(1.05f, OzMotion().focusScale, 0.0001f)
    }

    @Test
    fun `quickResumeHero resolves persisted quick resume metadata from Room v7`() = runBlocking {
        val app: Application = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(app, OzDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val prefs = AppPreferences(app)
            val repo = LibraryRepository(db)
            val syncRepo = HubSyncRepository(db, prefs)

            val zelda = GameEntity(
                id = "snes-alttp",
                title = "The Legend of Zelda: A Link to the Past",
                normalizedTitle = "legend of zelda a link to the past",
                platform = "Super Nintendo",
                systemId = "snes",
                year = 1991,
                region = "USA",
                thumbnailRepo = "Nintendo_-_Super_Nintendo_Entertainment_System",
                source = "local",
                launchValidated = true,
                coreId = "snes9x",
                virtualExtension = "sfc",
                contentUri = "content://roms/alttp.sfc",
                libraryOrigin = LibraryOrigin.DOWNLOADED.name,
            )
            db.gameDao().insertAll(listOf(zelda))
            syncRepo.recordQuickResume(zelda.id, slot = 3, statePath = "/states/alttp.state3", coreId = "snes9x")

            val home = HomeDomainHolder(
                application = app,
                scope = scope,
                database = db,
                repository = repo,
                preferences = prefs,
                hubSyncRepository = syncRepo,
                messages = MutableSharedFlow(extraBufferCapacity = 4),
            )

            val hero = home.quickResumeHero.filterNotNull().first { it.isQuickResume }
            assertEquals("snes-alttp", hero.game.id)
            assertEquals(3, hero.slot)
            assertEquals("snes9x", hero.coreId)
            assertTrue(hero.isQuickResume)

            val vm = OzViewModel(app)
            assertNotNull(vm.quickResumeHero)
            assertNotNull(vm.latestHubEvent)
        } finally {
            scope.cancel()
            db.close()
        }
    }

    @Test
    fun `brand icon mipmaps splash raw asset and Coil GIF ImageLoader resolve cleanly`() {
        val app: Application = RuntimeEnvironment.getApplication()
        assertTrue(R.raw.splash_hylian != 0)
        assertTrue(R.mipmap.ic_launcher != 0)
        assertTrue(R.mipmap.ic_launcher_round != 0)
        assertTrue(R.mipmap.ic_launcher_foreground != 0)
        assertTrue(R.color.ic_launcher_background != 0)
        assertEquals(3200L, HYLIAN_SPLASH_DURATION_MS)
        assertEquals(420, HYLIAN_SPLASH_FADE_OUT_MS)

        val colorsXml = File("src/main/res/values/colors.xml")
        if (colorsXml.isFile) {
            assertTrue(colorsXml.readText().contains("""<color name="ic_launcher_background">#0A0F0D</color>"""))
        }

        val loader = OzApplication().newImageLoader(app)
        assertNotNull(loader)
        assertTrue(
            loader.components.decoderFactories.any {
                it.javaClass.name.contains("AnimatedImageDecoder") || it.javaClass.name.contains("GifDecoder")
            },
        )
    }
}

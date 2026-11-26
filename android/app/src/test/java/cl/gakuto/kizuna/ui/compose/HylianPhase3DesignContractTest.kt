package cl.gakuto.kizuna.ui.compose

import android.app.Application
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.dp
import androidx.room.Room
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.R
import java.io.File
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.AppThemePreset
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryOrigin
import cl.gakuto.kizuna.data.LibraryRepository
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.hub.HubSyncRepository
import cl.gakuto.kizuna.ui.compose.components.HYLIAN_SPLASH_DURATION_MS
import cl.gakuto.kizuna.ui.compose.components.HYLIAN_SPLASH_FADE_OUT_MS
import cl.gakuto.kizuna.ui.compose.components.OZ_IMMERSIVE_COVER_ASPECT_RATIO
import cl.gakuto.kizuna.ui.compose.components.OZ_HUD_BUTTON_COMPACT_HEIGHT
import cl.gakuto.kizuna.ui.compose.components.OZ_HUD_BUTTON_HEIGHT
import cl.gakuto.kizuna.ui.compose.components.OZ_HUD_CHIP_HEIGHT
import cl.gakuto.kizuna.ui.compose.components.OZ_HUD_SEARCH_HEIGHT
import cl.gakuto.kizuna.ui.compose.components.OZ_HUD_SEGMENTED_HEIGHT
import cl.gakuto.kizuna.ui.compose.components.OZ_HUD_TOGGLE_HEIGHT
import cl.gakuto.kizuna.ui.compose.components.OZ_HUD_TOGGLE_WIDTH
import cl.gakuto.kizuna.ui.compose.components.OzChamferShape
import cl.gakuto.kizuna.ui.compose.domain.HomeDomainHolder
import cl.gakuto.kizuna.ui.compose.theme.LocalOzShellLayout
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzMotion
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzShellLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
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
class HylianPhase3DesignContractTest {

    @Test
    fun `cyber vault default and hylian dark preset tokens chamfer shapes and fonts match design spec`() {
        OzColors.use(AppThemeMode.DARK, AppThemePreset.CYBER_VAULT)
        assertEquals(0xFF070816, OzColors.Bg.value.toLong() ushr 32)
        assertEquals(0xFF0E1126, OzColors.Surface.value.toLong() ushr 32)
        assertEquals(0xFF252A4A, OzColors.Border.value.toLong() ushr 32)
        assertEquals(0xFF22D3EE, OzColors.AccentPrimary.value.toLong() ushr 32)
        assertEquals(0xFF7C3AED, OzColors.AccentSecondary.value.toLong() ushr 32)

        OzColors.use(AppThemeMode.DARK, AppThemePreset.HYLIAN)
        assertEquals(0xFF0A0F0D, OzColors.Bg.value.toLong() ushr 32)
        assertEquals(0xFF141F1A, OzColors.Surface.value.toLong() ushr 32)
        assertEquals(0xFF23382E, OzColors.Border.value.toLong() ushr 32)
        assertEquals(0xFF1E8E63, OzColors.AccentPrimary.value.toLong() ushr 32)
        assertEquals(0xFFD9B64E, OzColors.AccentSecondary.value.toLong() ushr 32)

        assertTrue(OzChamferShape(10.dp) is CutCornerShape)
        assertTrue(OzShapes.pill is CutCornerShape)
        assertTrue(R.font.oxanium_semibold != 0)
        assertTrue(R.font.oxanium_bold != 0)
        assertTrue(R.font.space_grotesk_regular != 0)
        assertTrue(R.font.space_grotesk_bold != 0)

        assertEquals(0.75f, OZ_IMMERSIVE_COVER_ASPECT_RATIO, 0.0001f)
        assertEquals(1.05f, OzMotion().focusScale, 0.0001f)

        val app: Application = RuntimeEnvironment.getApplication()
        val prefs = AppPreferences(app)
        assertEquals(AppThemePreset.CYBER_VAULT, prefs.appThemePreset)
        assertEquals(AppThemeMode.DARK, prefs.appThemeMode)
        prefs.appThemePreset = AppThemePreset.HYLIAN
        prefs.appThemeMode = AppThemeMode.LIGHT
        val prefsAfter = AppPreferences(app)
        assertEquals(AppThemePreset.HYLIAN, prefsAfter.appThemePreset)
        assertEquals(AppThemeMode.LIGHT, prefsAfter.appThemeMode)
    }

    @Test
    fun `OzColors palette is snapshot state so theme switches recompose instantly`() {
        OzColors.use(AppThemeMode.DARK, AppThemePreset.CYBER_VAULT)

        // Reading tokens must register Compose snapshot reads (palette + activeIsLight).
        val reads = mutableSetOf<Any>()
        val readSnapshot = Snapshot.takeSnapshot(readObserver = { reads.add(it) })
        try {
            readSnapshot.enter {
                val bg = OzColors.Bg
                val light = OzColors.isLightTheme
                assertEquals(0xFF070816, bg.value.toLong() ushr 32)
                assertFalse(light)
            }
        } finally {
            readSnapshot.dispose()
        }
        assertTrue("OzColors must expose at least 2 snapshot states", reads.size >= 2)

        // Writing a new preset/mode must notify Compose apply observers (triggers recomposition).
        val changed = mutableSetOf<Any>()
        val handle = Snapshot.registerApplyObserver { set, _ -> changed.addAll(set) }
        try {
            Snapshot.withMutableSnapshot { OzColors.use(AppThemeMode.LIGHT, AppThemePreset.HYLIAN) }
        } finally {
            handle.dispose()
        }
        assertTrue("Theme switch must emit snapshot change notifications", changed.isNotEmpty())
        assertTrue(changed.containsAll(reads))
        assertTrue(OzColors.isLightTheme)
        assertEquals(0xFFF2F7F4, OzColors.Bg.value.toLong() ushr 32)
        assertEquals(0xFF177350, OzColors.AccentPrimary.value.toLong() ushr 32)

        OzColors.use(AppThemeMode.DARK, AppThemePreset.CYBER_VAULT)
        assertFalse(OzColors.isLightTheme)
        assertEquals(0xFF22D3EE, OzColors.AccentPrimary.value.toLong() ushr 32)
    }

    @Test
    fun `OzShellLayout resolves landscape rail desktop bar and portrait dock breakpoints`() {
        assertEquals(
            listOf(OzShellLayout.PORTRAIT_PHONE, OzShellLayout.LANDSCAPE_HANDHELD, OzShellLayout.DESKTOP_WIDE),
            OzShellLayout.values().toList(),
        )
        assertNotNull(LocalOzShellLayout)

        // Phones held horizontally → rail (no header / dock)
        assertEquals(OzShellLayout.LANDSCAPE_HANDHELD, resolveOzShellLayout(800.dp, 360.dp))
        assertEquals(OzShellLayout.LANDSCAPE_HANDHELD, resolveOzShellLayout(915.dp, 412.dp))
        assertEquals(OzShellLayout.LANDSCAPE_HANDHELD, resolveOzShellLayout(1100.dp, 500.dp))
        // Portrait phones and small tablets → header + dock
        assertEquals(OzShellLayout.PORTRAIT_PHONE, resolveOzShellLayout(411.dp, 891.dp))
        assertEquals(OzShellLayout.PORTRAIT_PHONE, resolveOzShellLayout(800.dp, 1280.dp))
        assertEquals(OzShellLayout.PORTRAIT_PHONE, resolveOzShellLayout(900.dp, 600.dp))
        // Wide tablets / desktop → console top bar
        assertEquals(OzShellLayout.DESKTOP_WIDE, resolveOzShellLayout(1280.dp, 800.dp))
        assertEquals(OzShellLayout.DESKTOP_WIDE, resolveOzShellLayout(960.dp, 540.dp))

        assertEquals(64f, OZ_LANDSCAPE_RAIL_WIDTH.value, 0.001f)
        assertEquals(48f, OZ_MOBILE_HEADER_HEIGHT.value, 0.001f)
        assertEquals(58f, OZ_CENTER_DOCK_HEIGHT.value, 0.001f)
        assertEquals(50f, OZ_CENTER_DOCK_EMBLEM.value, 0.001f)
        assertEquals(52f, OZ_DESKTOP_TOP_BAR_HEIGHT.value, 0.001f)

        assertEquals(30f, OZ_HUD_CHIP_HEIGHT.value, 0.001f)
        assertEquals(34f, OZ_HUD_SEGMENTED_HEIGHT.value, 0.001f)
        assertEquals(38f, OZ_HUD_SEARCH_HEIGHT.value, 0.001f)
        assertEquals(44f, OZ_HUD_BUTTON_HEIGHT.value, 0.001f)
        assertEquals(34f, OZ_HUD_BUTTON_COMPACT_HEIGHT.value, 0.001f)
        assertEquals(44f, OZ_HUD_TOGGLE_WIDTH.value, 0.001f)
        assertEquals(24f, OZ_HUD_TOGGLE_HEIGHT.value, 0.001f)
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

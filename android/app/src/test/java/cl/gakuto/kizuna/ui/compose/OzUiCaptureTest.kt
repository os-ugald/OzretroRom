package cl.gakuto.kizuna.ui.compose

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.AppThemePreset
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.LibraryOrigin
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.hub.HubSyncRepository
import cl.gakuto.kizuna.ui.compose.screens.OzHomeScreen
import cl.gakuto.kizuna.ui.compose.screens.OzLibraryScreen
import cl.gakuto.kizuna.ui.compose.theme.LocalOzShellLayout
import cl.gakuto.kizuna.ui.compose.theme.OzShellLayout
import cl.gakuto.kizuna.ui.compose.theme.OzTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OzUiCaptureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: OzViewModel

    @Before
    fun setUp() {
        val app: Application = RuntimeEnvironment.getApplication()
        val db = OzDatabase.get(app)
        val prefs = AppPreferences(app)
        prefs.appThemeMode = AppThemeMode.DARK
        prefs.appThemePreset = AppThemePreset.CYBER_VAULT
        val syncRepo = HubSyncRepository(db, prefs)

        val now = 1_760_000_000_000L
        val sampleGames = listOf(
            GameEntity(
                id = "snes-chrono-trigger",
                title = "Chrono Trigger (USA)",
                normalizedTitle = "chrono trigger",
                platform = "Super Nintendo",
                systemId = "snes",
                year = 1995,
                genre = "RPG",
                region = "USA",
                thumbnailRepo = "Nintendo_-_Super_Nintendo_Entertainment_System",
                source = "local",
                launchValidated = true,
                coreId = "snes9x",
                virtualExtension = "sfc",
                contentUri = "content://roms/chrono_trigger.sfc",
                libraryOrigin = LibraryOrigin.DOWNLOADED.name,
                lastPlayedAt = now - 60_000L,
            ),
            GameEntity(
                id = "n64-zelda-oot",
                title = "The Legend of Zelda: Ocarina of Time (USA)",
                normalizedTitle = "legend of zelda ocarina of time",
                platform = "Nintendo 64",
                systemId = "n64",
                year = 1998,
                genre = "Action Adventure",
                region = "USA",
                thumbnailRepo = "Nintendo_-_Nintendo_64",
                source = "local",
                launchValidated = true,
                coreId = "mupen64plus_next_gles3",
                virtualExtension = "z64",
                contentUri = "content://roms/oot.z64",
                libraryOrigin = LibraryOrigin.DOWNLOADED.name,
                lastPlayedAt = now - 300_000L,
            ),
            GameEntity(
                id = "gba-metroid-fusion",
                title = "Metroid Fusion (USA)",
                normalizedTitle = "metroid fusion",
                platform = "Game Boy Advance",
                systemId = "gba",
                year = 2002,
                genre = "Action",
                region = "USA",
                thumbnailRepo = "Nintendo_-_Game_Boy_Advance",
                source = "local",
                launchValidated = true,
                coreId = "mgba",
                virtualExtension = "gba",
                contentUri = "content://roms/metroid_fusion.gba",
                libraryOrigin = LibraryOrigin.DOWNLOADED.name,
                lastPlayedAt = now - 900_000L,
            ),
            GameEntity(
                id = "psx-castlevania-sotn",
                title = "Castlevania: Symphony of the Night (USA)",
                normalizedTitle = "castlevania symphony of the night",
                platform = "PlayStation",
                systemId = "psx",
                year = 1997,
                genre = "Action RPG",
                region = "USA",
                thumbnailRepo = "Sony_-_PlayStation",
                source = "local",
                launchValidated = true,
                coreId = "swanstation",
                virtualExtension = "chd",
                contentUri = "content://roms/sotn.chd",
                libraryOrigin = LibraryOrigin.DOWNLOADED.name,
                lastPlayedAt = now - 1_500_000L,
            ),
            GameEntity(
                id = "nds-pokemon-heartgold",
                title = "Pokemon HeartGold Version (USA)",
                normalizedTitle = "pokemon heartgold version",
                platform = "Nintendo DS",
                systemId = "nds",
                year = 2010,
                genre = "RPG",
                region = "USA",
                thumbnailRepo = "Nintendo_-_Nintendo_DS",
                source = "local",
                launchValidated = true,
                coreId = "melondsds",
                virtualExtension = "nds",
                contentUri = "content://roms/heartgold.nds",
                libraryOrigin = LibraryOrigin.DOWNLOADED.name,
                lastPlayedAt = now - 2_400_000L,
            ),
            GameEntity(
                id = "genesis-sonic-3",
                title = "Sonic the Hedgehog 3 (USA)",
                normalizedTitle = "sonic the hedgehog 3",
                platform = "Sega Genesis",
                systemId = "genesis",
                year = 1994,
                genre = "Platformer",
                region = "USA",
                thumbnailRepo = "Sega_-_Mega_Drive_-_Genesis",
                source = "local",
                launchValidated = true,
                coreId = "genesis_plus_gx",
                virtualExtension = "md",
                contentUri = "content://roms/sonic3.md",
                libraryOrigin = LibraryOrigin.DOWNLOADED.name,
                lastPlayedAt = now - 3_600_000L,
            ),
            GameEntity(
                id = "nes-super-mario-bros-3",
                title = "Super Mario Bros. 3 (USA)",
                normalizedTitle = "super mario bros 3",
                platform = "Nintendo Entertainment System",
                systemId = "nes",
                year = 1990,
                genre = "Platformer",
                region = "USA",
                thumbnailRepo = "Nintendo_-_Nintendo_Entertainment_System",
                source = "local",
                launchValidated = true,
                coreId = "fceumm",
                virtualExtension = "nes",
                contentUri = null,
                libraryOrigin = LibraryOrigin.NONE.name,
                lastPlayedAt = null,
            ),
            GameEntity(
                id = "psp-god-of-war-coo",
                title = "God of War: Chains of Olympus (USA)",
                normalizedTitle = "god of war chains of olympus",
                platform = "PlayStation Portable",
                systemId = "psp",
                year = 2008,
                genre = "Action",
                region = "USA",
                thumbnailRepo = "Sony_-_PlayStation_Portable",
                source = "local",
                launchValidated = true,
                coreId = "ppsspp",
                virtualExtension = "iso",
                contentUri = "content://roms/gow_coo.iso",
                libraryOrigin = LibraryOrigin.DOWNLOADED.name,
                lastPlayedAt = null,
            ),
        )

        runBlocking {
            db.gameDao().insertAll(sampleGames)
            syncRepo.recordQuickResume(
                gameId = "snes-chrono-trigger",
                slot = 2,
                statePath = "/states/chrono_trigger.state2",
                coreId = "snes9x",
            )
        }

        viewModel = OzViewModel(app)
    }

    private fun captureFile(name: String): File {
        val dir = File("../../docs/ui-captures")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$name.png")
    }

    private fun renderHome(shellLayout: OzShellLayout, captureName: String) {
        composeRule.setContent {
            OzTheme(themeMode = AppThemeMode.DARK, themePreset = AppThemePreset.CYBER_VAULT) {
                CompositionLocalProvider(LocalOzShellLayout provides shellLayout) {
                    OzHomeScreen(
                        viewModel = viewModel,
                        onLaunchGame = {},
                        onOpenGame = {},
                        onStartGameMenu = {},
                        onOpenLibraryPlatform = {},
                        onOpenExplore = {},
                        onOpenSearch = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(captureFile(captureName))
    }

    private fun renderLibrary(shellLayout: OzShellLayout, captureName: String) {
        composeRule.setContent {
            OzTheme(themeMode = AppThemeMode.DARK, themePreset = AppThemePreset.CYBER_VAULT) {
                CompositionLocalProvider(LocalOzShellLayout provides shellLayout) {
                    OzLibraryScreen(
                        viewModel = viewModel,
                        onOpenGame = {},
                        onStartGameMenu = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(captureFile(captureName))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
    fun capture_home_portrait() {
        renderHome(OzShellLayout.PORTRAIT_PHONE, "home_portrait")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w915dp-h411dp-xxhdpi")
    fun capture_home_landscape() {
        renderHome(OzShellLayout.LANDSCAPE_HANDHELD, "home_landscape")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
    fun capture_library_portrait() {
        renderLibrary(OzShellLayout.PORTRAIT_PHONE, "library_portrait")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w915dp-h411dp-xxhdpi")
    fun capture_library_landscape() {
        renderLibrary(OzShellLayout.LANDSCAPE_HANDHELD, "library_landscape")
    }
}

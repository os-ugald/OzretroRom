package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.data.GameEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Platform Factory V1 — Shared Foundation Slice B anchor.
 *
 * Intentionally hardcodes expected values, captured against the CURRENT (pre-wiring)
 * RetroBiosValidator.requirements() behavior, before a PlatformFirmwareProfile registry becomes an
 * optional runtime authority. Re-run unchanged after the wiring change to prove zero regression for
 * every legacy systemId branch, including region-dependent sega-cd/saturn cases and the nes/.fds
 * extension gate.
 */
class RetroBiosValidatorRequirementsAnchorTest {

    @Test fun nesWithFdsExtensionRequiresDisksys() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "nes"), "Game.fds")
        assertEquals(listOf(RetroBiosValidator.Requirement("Famicom Disk System", setOf("disksys.rom"))), requirements)
    }

    @Test fun nesWithNonFdsExtensionRequiresNothing() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "nes"), "Game.nes")
        assertTrue(requirements.isEmpty())
    }

    @Test fun dsiRequiresFourFixedFiles() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "dsi"), "Game.nds")
        assertEquals(
            listOf(
                RetroBiosValidator.Requirement("DSi ARM7 BIOS", setOf("dsi_bios7.bin")),
                RetroBiosValidator.Requirement("DSi ARM9 BIOS", setOf("dsi_bios9.bin")),
                RetroBiosValidator.Requirement("DSi firmware", setOf("dsi_firmware.bin")),
                RetroBiosValidator.Requirement("DSi NAND", setOf("dsi_nand.bin")),
            ),
            requirements,
        )
    }

    @Test fun segaCdJapanRequiresJapaneseBios() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "sega-cd", region = "Japan"), "Game.cue")
        assertEquals(listOf(RetroBiosValidator.Requirement("Mega-CD / Sega CD BIOS", setOf("bios_CD_J.bin"))), requirements)
    }

    @Test fun segaCdEuropeRequiresEuropeanBios() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "sega-cd", region = "Europe"), "Game.cue")
        assertEquals(listOf(RetroBiosValidator.Requirement("Mega-CD / Sega CD BIOS", setOf("bios_CD_E.bin"))), requirements)
    }

    @Test fun segaCdUsaRequiresUsBios() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "sega-cd", region = "USA"), "Game.cue")
        assertEquals(listOf(RetroBiosValidator.Requirement("Mega-CD / Sega CD BIOS", setOf("bios_CD_U.bin"))), requirements)
    }

    @Test fun segaCdUnknownRegionAcceptsAnyBios() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "sega-cd", region = "World"), "Game.cue")
        assertEquals(
            listOf(RetroBiosValidator.Requirement("Mega-CD / Sega CD BIOS", setOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin"))),
            requirements,
        )
    }

    @Test fun saturnJapanRequiresJapaneseBios() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "saturn", region = "Japan"), "Game.cue")
        assertEquals(listOf(RetroBiosValidator.Requirement("Saturn BIOS", setOf("sega_101.bin"))), requirements)
    }

    @Test fun saturnUsaRequiresMpr17933() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "saturn", region = "USA"), "Game.cue")
        assertEquals(listOf(RetroBiosValidator.Requirement("Saturn BIOS", setOf("mpr-17933.bin"))), requirements)
    }

    @Test fun saturnEuropeAlsoRequiresMpr17933() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "saturn", region = "Europe"), "Game.cue")
        assertEquals(listOf(RetroBiosValidator.Requirement("Saturn BIOS", setOf("mpr-17933.bin"))), requirements)
    }

    @Test fun saturnUnknownRegionAcceptsEitherBios() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "saturn", region = "World"), "Game.cue")
        assertEquals(
            listOf(RetroBiosValidator.Requirement("Saturn BIOS", setOf("sega_101.bin", "mpr-17933.bin"))),
            requirements,
        )
    }

    @Test fun pcEngineCdRequiresSystemCard() {
        val requirements = RetroBiosValidator.requirements(game(systemId = "pc-engine-cd"), "Game.cue")
        assertEquals(listOf(RetroBiosValidator.Requirement("PC Engine CD System Card", setOf("syscard3.pce"))), requirements)
    }

    @Test fun unregisteredSystemRequiresNothing() {
        listOf("gba", "snes", "genesis", "n64", "arcade-fbneo").forEach { id ->
            assertTrue(
                "'$id' should require no firmware today",
                RetroBiosValidator.requirements(game(systemId = id), "Game.bin").isEmpty(),
            )
        }
    }

    private fun game(systemId: String, region: String = "USA") = GameEntity(
        id = "anchor-$systemId",
        title = "Anchor Game",
        normalizedTitle = "anchor game",
        platform = systemId,
        systemId = systemId,
        year = 1994,
        region = region,
        thumbnailRepo = null,
        source = "test",
        launchValidated = true,
        coreId = null,
        virtualExtension = "bin",
        canonicalTitle = "anchor game",
    )
}

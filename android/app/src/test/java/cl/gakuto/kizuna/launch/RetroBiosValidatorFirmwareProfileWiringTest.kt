package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.data.GameEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Platform Factory V1 — Shared Foundation Slice B.
 *
 * Proves [RetroBiosValidator.requirements] — after wiring in [PlatformFirmwareProfiles] as an
 * optional runtime authority — without registering any real platform data (no Neo Geo entry is
 * added by this changeset). Synthetic [PlatformFirmwareProfile] instances below are test-only
 * fixtures passed via the function's optional `profile` parameter; they never touch the real
 * (still-empty) registry.
 */
class RetroBiosValidatorFirmwareProfileWiringTest {

    @Test fun syntheticProfileBecomesAuthorityOverLegacyTable() {
        val synthetic = PlatformFirmwareProfile(
            systemId = "synthetic-firmware-system",
            requirements = listOf(FirmwareRequirement("Synthetic BIOS", setOf("synthetic.bin"))),
        )
        val game = game(systemId = "synthetic-firmware-system")
        val requirements = RetroBiosValidator.requirements(game, "Game.bin", synthetic)
        assertEquals(listOf(RetroBiosValidator.Requirement("Synthetic BIOS", setOf("synthetic.bin"))), requirements)
    }

    @Test fun syntheticProfileAcceptableAlternativesArePreserved() {
        val synthetic = PlatformFirmwareProfile(
            systemId = "synthetic-firmware-system",
            requirements = listOf(FirmwareRequirement("Synthetic BIOS", setOf("a.bin", "b.bin", "c.bin"))),
        )
        val requirements = RetroBiosValidator.requirements(game(systemId = "synthetic-firmware-system"), "Game.bin", synthetic)
        assertEquals(setOf("a.bin", "b.bin", "c.bin"), requirements.single().alternatives)
    }

    @Test fun syntheticProfileWithNoRequirementsMeansNoFirmwareNeeded() {
        val synthetic = PlatformFirmwareProfile(systemId = "synthetic-empty-system", requirements = emptyList())
        val requirements = RetroBiosValidator.requirements(game(systemId = "synthetic-empty-system"), "Game.bin", synthetic)
        assertTrue(requirements.isEmpty())
    }

    @Test fun unregisteredSystemFallsBackToLegacyTableExactlyAsBefore() {
        // No profile passed -> default parameter resolves via PlatformFirmwareProfiles.forSystem,
        // which is empty today, so every current systemId still falls to the legacy table.
        assertEquals(
            listOf(RetroBiosValidator.Requirement("Famicom Disk System", setOf("disksys.rom"))),
            RetroBiosValidator.requirements(game(systemId = "nes"), "Game.fds"),
        )
        assertTrue(RetroBiosValidator.requirements(game(systemId = "gba"), "Game.gba").isEmpty())
    }

    @Test fun existingRegionDependentLegacyBehaviorUnchanged() {
        assertEquals(
            listOf(RetroBiosValidator.Requirement("Mega-CD / Sega CD BIOS", setOf("bios_CD_J.bin"))),
            RetroBiosValidator.requirements(game(systemId = "sega-cd", region = "Japan"), "Game.cue"),
        )
        assertEquals(
            listOf(RetroBiosValidator.Requirement("Saturn BIOS", setOf("mpr-17933.bin"))),
            RetroBiosValidator.requirements(game(systemId = "saturn", region = "USA"), "Game.cue"),
        )
    }

    @Test fun firmwareProfileNeverAppearsOnGameEntity() {
        // Identity boundary: PlatformFirmwareProfile has no relationship to GameEntity's persisted
        // fields — constructing a synthetic profile does not require, read, or mutate any
        // Game/Release/Candidate/Local Copy identity field.
        val synthetic = PlatformFirmwareProfile(systemId = "synthetic-firmware-system", requirements = emptyList())
        val game = game(systemId = "synthetic-firmware-system")
        RetroBiosValidator.requirements(game, "Game.bin", synthetic)
        assertEquals("anchor-synthetic-firmware-system", game.id)
        assertEquals("synthetic-firmware-system", game.systemId)
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

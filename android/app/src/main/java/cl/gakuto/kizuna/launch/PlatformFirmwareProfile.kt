package cl.gakuto.kizuna.launch

/**
 * Platform Factory V1 — Shared Foundation Slice B.
 *
 * Characterizes, as data, the firmware/BIOS requirement shape [RetroBiosValidator] currently
 * expresses as a single hardcoded `when(systemId)` table. Deliberately mirrors
 * [RetroBiosValidator.Requirement] (`label` + `alternatives`) exactly — nothing more is added here,
 * because nothing currently registered needs more: the region-dependent bucketing sega-cd/saturn use
 * today stays inside [RetroBiosValidator]'s legacy table, unmigrated, since no platform yet requires
 * expressing that bucketing as data (adding a `regionBucketed`-style field now, with no current
 * consumer, would be exactly the kind of speculative field this slice must avoid).
 *
 * Firmware stays a launch dependency, separate from Game/Release/Candidate/Local Copy identity: this
 * file has no reference to any of those types, and nothing here is persisted onto [cl.gakuto.kizuna.data.GameEntity]
 * or any Source Index type.
 *
 * Not yet consumed for any real platform — [PlatformFirmwareProfiles.all] is intentionally empty in
 * this foundation slice. See docs/ai/evidence/2026-09-13_platform-factory-v1-shared-foundation-b-firmware.md.
 */
data class FirmwareRequirement(
    val label: String,
    val alternatives: Set<String>,
)

data class PlatformFirmwareProfile(
    val systemId: String,
    val requirements: List<FirmwareRequirement> = emptyList(),
)

/**
 * Intentionally empty: this slice introduces the contract, not any platform's firmware data
 * (including Neo Geo). Legacy systems (nes, dsi, sega-cd, saturn, pc-engine-cd) are not migrated
 * here — [RetroBiosValidator] keeps their exact existing behavior unchanged via its legacy table.
 */
object PlatformFirmwareProfiles {
    val all: List<PlatformFirmwareProfile> = emptyList()

    fun forSystem(systemId: String): PlatformFirmwareProfile? = all.firstOrNull { it.systemId == systemId }
}

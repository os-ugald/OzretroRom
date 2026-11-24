package cl.gakuto.kizuna.launch

/**
 * Platform Factory V1 — Phase 1A media contract.
 *
 * Characterizes, as data, the archive-preparation behavior [RomArchivePreparer] currently
 * expresses as parallel `when(systemId)` tables. Not yet consumed by production code — see
 * docs/ai/evidence/2026-09-12_platform-factory-v1-phase-1a-media-profile-contract.md.
 *
 * BIOS is explicitly out of scope for this slice (Forge decision, 2026-09-12): FBNeo's BIOS model
 * is acquisition/merge behavior tied to a per-game `biosSets` column and the legacy ROMgi path,
 * not platform media data, and RetroBiosValidator's region/extension-conditional requirements()
 * needs a separate design pass before it can be safely expressed as data.
 */
enum class MediaKind { SINGLE_FILE, ARCHIVE_AS_ROMSET, MULTI_FILE_OPTICAL }

data class PlatformMediaProfile(
    val systemId: String,
    val mediaKind: MediaKind = MediaKind.SINGLE_FILE,
    val nativeExtensions: Set<String> = emptySet(),
    val preferredExtension: String? = null,
    val extractionSizeLimit: Long? = null,
)

/**
 * First Platform Factory V1 migration slice: NES, SNES, Genesis and GBA (already evidenced as
 * reference/foundation platforms), plus N64 — included specifically because it is the one
 * existing platform whose preferred extension ("z64") diverges from the natural first element of
 * its native-extensions set ("n64"), proving `preferredExtension` is a required field rather than
 * a derivable one.
 *
 * All other systemIds remain governed entirely by RomArchivePreparer's existing tables; this
 * registry intentionally does not attempt completeness.
 */
object PlatformMediaProfiles {
    val all: List<PlatformMediaProfile> = listOf(
        PlatformMediaProfile(
            systemId = "nes",
            nativeExtensions = setOf("nes", "fds", "unf", "unif"),
        ),
        PlatformMediaProfile(
            systemId = "snes",
            nativeExtensions = setOf("sfc", "smc", "swc", "fig", "bs", "st"),
            preferredExtension = "sfc",
        ),
        PlatformMediaProfile(
            systemId = "genesis",
            nativeExtensions = setOf("md", "gen", "bin", "smd"),
            preferredExtension = "md",
        ),
        PlatformMediaProfile(
            systemId = "gba",
            nativeExtensions = setOf("gba"),
        ),
        PlatformMediaProfile(
            systemId = "n64",
            nativeExtensions = setOf("n64", "z64", "v64"),
            preferredExtension = "z64",
        ),
    )

    fun forSystem(systemId: String): PlatformMediaProfile? = all.firstOrNull { it.systemId == systemId }
}

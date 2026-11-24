package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Prepares downloaded ROM archives before an external RetroArch launch.
 *
 * ROMgi itself advertises automatic ZIP/7z extraction. Oz previously persisted a downloaded ZIP
 * unchanged for cartridge platforms and could then hand that archive to an externally launched
 * core. Mainline Snes9x documents .sfc/.smc/etc. as content extensions, not .zip, so this class
 * extracts the real cartridge ROM from a simple ZIP before persistence/launch.
 *
 * Arcade/FBNeo intentionally keeps its ZIP romset intact. Multi-file optical descriptors such as
 * CUE/GDI are rejected until Oz can persist the descriptor and all companion tracks as one durable
 * library artifact. 7z is rejected later with a controlled error on systems that need native
 * content until Oz adds a verified 7z extraction implementation.
 */
object RomArchivePreparer {
    suspend fun prepare(
        game: GameEntity,
        downloaded: File,
        store: SessionFileStore,
        diagnostics: DiagnosticLogger.Session,
    ): File = withContext(Dispatchers.IO) {
        if (!downloaded.isFile) return@withContext downloaded
        if (isArchiveAsRomset(game.systemId)) return@withContext downloaded
        if (!downloaded.extension.equals("zip", ignoreCase = true)) return@withContext downloaded

        val nativeExtensions = nativeExtensions(game.systemId)
        if (nativeExtensions.isEmpty()) return@withContext downloaded

        val extracted = prepareZip(
            systemId = game.systemId,
            platform = game.platform,
            downloaded = downloaded,
            targetForName = store::file,
        )
        diagnostics.info(
            "ROM_ARCHIVE_EXTRACTED",
            "system=${game.systemId} archive=${downloaded.name} entry=${extracted.entryName} bytes=${extracted.file.length()}",
        )
        extracted.file
    }

    internal data class PreparedZipEntry(val entryName: String, val file: File)

    /** Pure host-testable ZIP contract used by the Android session-store adapter above. */
    internal fun prepareZip(
        systemId: String,
        platform: String,
        downloaded: File,
        targetForName: (String) -> File,
    ): PreparedZipEntry {
        require(downloaded.isFile) { "El ZIP descargado no existe." }
        return ZipFile(downloaded).use { zip ->
            val nativeExtensions = nativeExtensions(systemId)
            val candidates = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { entry ->
                    val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    extension in nativeExtensions
                }
                .toList()

            require(candidates.isNotEmpty()) {
                "El ZIP descargado no contiene una ROM compatible para $platform."
            }

            if (systemId == "ps1") {
                // One selected download represents one disc. Never guess a disc/release by size,
                // or discard companion tracks. Bundle persistence is deliberately still unsupported.
                require(candidates.size == 1 &&
                    candidates.single().name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
                    setOf("chd", "iso", "pbp") &&
                    zip.entries().asSequence().none {
                        !it.isDirectory && it.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
                            setOf("cue", "bin", "gdi", "m3u")
                    }) {
                    "El ZIP PS1 debe contener un único disco CHD, ISO o PBP sin pistas ni listas adicionales. " +
                        "Selecciona una fuente autocontenida por disco; Oz conserva el ZIP para revisión."
                }
            }

            val selected = candidates.maxWithOrNull(
                compareBy<java.util.zip.ZipEntry> { extensionPriority(it.name, systemId) }
                    .thenBy { it.size },
            ) ?: error("No se encontró una ROM válida dentro del ZIP.")

            require(isSafeArchiveEntryName(selected.name)) {
                "El ZIP contiene una ruta de archivo no válida."
            }

            val candidateExtensions = candidates
                .map { it.name.substringAfterLast('.', "").lowercase(Locale.ROOT) }
                .toSet()
            require(!requiresUnsupportedBundlePersistence(systemId, candidateExtensions)) {
                "El ZIP contiene una imagen óptica multiarchivo (CUE/GDI + pistas). " +
                    "Oz todavía no puede persistir el conjunto completo de forma segura; " +
                    "elige una fuente CHD/ISO/CDI o un archivo propio."
            }

            // The old global 4 GiB limit rejected valid DVD9 images used by PS2/Wii.
            val maxBytes = extractionLimit(systemId)
            if (selected.size > 0L) {
                require(selected.size <= maxBytes) {
                    "La ROM dentro del ZIP excede el límite de seguridad para $platform."
                }
            }

            val safeName = selected.name.substringAfterLast('/').substringAfterLast('\\')
            require(safeName.isNotBlank() && safeName != "." && safeName != "..") {
                "El ZIP contiene un nombre de archivo no válido."
            }
            val target = targetForName(safeName)
            target.parentFile?.mkdirs()
            var copied = 0L
            zip.getInputStream(selected).buffered().use { input ->
                FileOutputStream(target, false).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        require(copied <= maxBytes) {
                            "La ROM extraída excede el límite de seguridad para $platform."
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            require(copied > 4_096L) { "La ROM extraída está vacía o incompleta." }
            PreparedZipEntry(selected.name, target)
        }
    }

    fun needsNativeExtraction(systemId: String, extension: String): Boolean =
        !isArchiveAsRomset(systemId) && extension.lowercase(Locale.ROOT) in setOf("zip", "7z") &&
            nativeExtensions(systemId).isNotEmpty()

    /**
     * Platform Factory V1 — Shared Foundation Slice C: makes [MediaKind.ARCHIVE_AS_ROMSET] an
     * explicit runtime choke point instead of the historical `systemId == "arcade-fbneo"` literal.
     * A registered [PlatformMediaProfile] with that media kind preserves the archive intact, exactly
     * like the legacy arcade-fbneo carve-out did; a [SINGLE_FILE][MediaKind.SINGLE_FILE] profile (the
     * default for all currently migrated platforms) or no profile at all falls back to the original
     * literal check, so every existing systemId — migrated or not — behaves byte-for-byte identically
     * to before this change. The optional [profile] parameter exists solely so tests can prove the
     * ARCHIVE_AS_ROMSET branch works with a synthetic profile, without registering real platform data
     * in [PlatformMediaProfiles].
     */
    internal fun isArchiveAsRomset(
        systemId: String,
        profile: PlatformMediaProfile? = PlatformMediaProfiles.forSystem(systemId),
    ): Boolean = profile?.let { it.mediaKind == MediaKind.ARCHIVE_AS_ROMSET } ?: (systemId == "arcade-fbneo")

    /**
     * Platform Factory V1: for a systemId with a registered [PlatformMediaProfile], that profile
     * is the sole source of truth for native extensions — never mixed with [legacyNativeExtensions]
     * for the same systemId. Every other systemId keeps its unchanged legacy table entry.
     */
    fun nativeExtensions(systemId: String): Set<String> =
        PlatformMediaProfiles.forSystem(systemId)?.nativeExtensions ?: legacyNativeExtensions(systemId)

    private fun legacyNativeExtensions(systemId: String): Set<String> = when (systemId) {
        "gb" -> setOf("gb")
        "gbc" -> setOf("gbc", "gb")
        "master-system" -> setOf("sms", "bin")
        "game-gear" -> setOf("gg", "bin")
        "ngp" -> setOf("ngp", "ngc")
        "ngpc" -> setOf("ngc", "ngp")
        "pc-engine" -> setOf("pce")
        "wonderswan" -> setOf("ws")
        "wonderswan-color" -> setOf("wsc", "ws")
        "atari-lynx" -> setOf("lnx")
        "atari-2600" -> setOf("a26", "bin")
        "atari-5200" -> setOf("a52", "bin")
        "atari-7800" -> setOf("a78", "bin")
        "ps1" -> setOf("chd", "pbp", "iso", "cue", "bin")
        "ps2" -> setOf("iso", "chd", "cso", "gz", "bin")
        "gamecube" -> setOf("rvz", "iso", "gcm", "gcz", "ciso")
        "wii" -> setOf("rvz", "wbfs", "wia", "iso", "gcz", "ciso")
        "dreamcast" -> setOf("chd", "gdi", "cdi", "cue", "iso")
        "saturn" -> setOf("chd", "iso", "cue")
        else -> emptySet()
    }

    private fun extensionPriority(name: String, systemId: String): Int {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val preferred = PlatformMediaProfiles.forSystem(systemId)
            ?.let { it.preferredExtension ?: it.nativeExtensions.firstOrNull() }
            ?: legacyPreferredExtension(systemId)
        return if (extension == preferred) 2 else 1
    }

    private fun legacyPreferredExtension(systemId: String): String = when (systemId) {
        "gbc" -> "gbc"
        "ngpc" -> "ngc"
        "wonderswan-color" -> "wsc"
        "ps2" -> "iso"
        "gamecube", "wii" -> "rvz"
        else -> legacyNativeExtensions(systemId).firstOrNull().orEmpty()
    }

    internal fun requiresUnsupportedBundlePersistence(
        systemId: String,
        candidateExtensions: Set<String>,
    ): Boolean {
        if (systemId !in MULTI_FILE_OPTICAL_SYSTEMS) return false
        return candidateExtensions.any { it in MULTI_FILE_DESCRIPTOR_EXTENSIONS }
    }

    internal fun isSafeArchiveEntryName(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/') || DRIVE_PREFIX.containsMatchIn(normalized)) return false
        return normalized.split('/').none { segment -> segment.isBlank() || segment == "." || segment == ".." }
    }

    private fun extractionLimit(systemId: String): Long = when (systemId) {
        "ps2", "wii" -> MAX_DVD_IMAGE_BYTES
        "gamecube" -> MAX_GAMECUBE_IMAGE_BYTES
        else -> MAX_GENERIC_ROM_BYTES
    }

    private val MULTI_FILE_OPTICAL_SYSTEMS = setOf(
        "ps1", "dreamcast", "saturn", "sega-cd", "pc-engine-cd", "pc-fx",
    )
    private val MULTI_FILE_DESCRIPTOR_EXTENSIONS = setOf("cue", "gdi")
    private val DRIVE_PREFIX = Regex("^[A-Za-z]:")

    private const val MAX_GENERIC_ROM_BYTES = 4L * 1024L * 1024L * 1024L
    private const val MAX_GAMECUBE_IMAGE_BYTES = 3L * 1024L * 1024L * 1024L
    private const val MAX_DVD_IMAGE_BYTES = 16L * 1024L * 1024L * 1024L
}

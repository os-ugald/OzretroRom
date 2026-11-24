package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cl.gakuto.kizuna.data.GameEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verifies only firmware requirements that Fix5 had already mapped to concrete Libretro filenames.
 * Optional/conditional firmware is deliberately not guessed here.
 */
object RetroBiosValidator {
    data class Requirement(
        val label: String,
        val alternatives: Set<String>,
    )

    data class Verified(
        val required: Int,
        val matched: List<String>,
    )

    /**
     * Platform Factory V1 — Shared Foundation Slice B: for a systemId with a registered
     * [PlatformFirmwareProfile], that profile is the sole source of truth for firmware
     * requirements — never mixed with [legacyRequirements] for the same systemId. Every other
     * systemId (all of them today, since [PlatformFirmwareProfiles.all] is empty in this
     * foundation slice) keeps its unchanged legacy table entry. The optional [profile] parameter
     * exists solely so tests can prove the profile-authority branch works with a synthetic
     * profile, without registering real platform data in [PlatformFirmwareProfiles].
     */
    fun requirements(
        game: GameEntity,
        displayName: String,
        profile: PlatformFirmwareProfile? = PlatformFirmwareProfiles.forSystem(game.systemId),
    ): List<Requirement> =
        profile?.requirements?.map { Requirement(it.label, it.alternatives) }
            ?: legacyRequirements(game, displayName)

    private fun legacyRequirements(game: GameEntity, displayName: String): List<Requirement> {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return when (game.systemId) {
            "nes" -> if (extension == "fds") {
                listOf(Requirement("Famicom Disk System", setOf("disksys.rom")))
            } else emptyList()

            "dsi" -> listOf(
                Requirement("DSi ARM7 BIOS", setOf("dsi_bios7.bin")),
                Requirement("DSi ARM9 BIOS", setOf("dsi_bios9.bin")),
                Requirement("DSi firmware", setOf("dsi_firmware.bin")),
                Requirement("DSi NAND", setOf("dsi_nand.bin")),
            )

            "sega-cd" -> listOf(
                Requirement(
                    "Mega-CD / Sega CD BIOS",
                    when (regionBucket(game.region)) {
                        "japan" -> setOf("bios_CD_J.bin")
                        "europe" -> setOf("bios_CD_E.bin")
                        "usa" -> setOf("bios_CD_U.bin")
                        else -> setOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin")
                    },
                ),
            )

            "saturn" -> listOf(
                Requirement(
                    "Saturn BIOS",
                    when (regionBucket(game.region)) {
                        "japan" -> setOf("sega_101.bin")
                        "usa", "europe" -> setOf("mpr-17933.bin")
                        else -> setOf("sega_101.bin", "mpr-17933.bin")
                    },
                ),
            )

            "pc-engine-cd" -> listOf(
                Requirement("PC Engine CD System Card", setOf("syscard3.pce")),
            )

            else -> emptyList()
        }
    }

    suspend fun validate(
        context: Context,
        systemTreeUri: Uri?,
        requirements: List<Requirement>,
    ): Verified = withContext(Dispatchers.IO) {
        if (requirements.isEmpty()) return@withContext Verified(0, emptyList())
        val uri = systemTreeUri ?: error(
            "Este sistema requiere BIOS verificables. En Configuración > RetroArch y cores, selecciona la carpeta System/BIOS que RetroArch usa realmente.",
        )
        val root = DocumentFile.fromTreeUri(context, uri)
            ?.takeIf { it.exists() && it.isDirectory }
            ?: error("Oz ya no puede acceder a la carpeta System/BIOS seleccionada. Vuelve a elegirla en Configuración.")

        val files = linkedMapOf<String, DocumentFile>()
        indexFiles(root, files, depth = 0)
        val matched = mutableListOf<String>()
        val missing = mutableListOf<Requirement>()
        requirements.forEach { requirement ->
            val found = requirement.alternatives.firstOrNull { expected ->
                files[expected.lowercase()]?.let { it.exists() && it.isFile && it.length() > 0L } == true
            }
            if (found == null) missing += requirement else matched += found
        }
        if (missing.isNotEmpty()) {
            val detail = missing.joinToString("; ") { requirement ->
                "${requirement.label}: ${requirement.alternatives.joinToString(" o ")}"
            }
            error("Faltan BIOS/firmware requeridos en la carpeta System/BIOS seleccionada: $detail")
        }
        Verified(requirements.size, matched)
    }

    private fun indexFiles(root: DocumentFile, out: MutableMap<String, DocumentFile>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val children = runCatching { root.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (out.size >= MAX_FILES) break
            if (child.isDirectory) {
                indexFiles(child, out, depth + 1)
            } else if (child.isFile) {
                child.name?.takeIf(String::isNotBlank)?.lowercase()?.let { out.putIfAbsent(it, child) }
            }
        }
    }

    private fun regionBucket(value: String): String {
        val normalized = value.lowercase()
        return when {
            listOf("japan", "japanese", "jp", "ntsc-j").any(normalized::contains) -> "japan"
            listOf("europe", "pal", "france", "germany", "spain", "italy", "uk").any(normalized::contains) -> "europe"
            listOf("usa", "united states", "us", "ntsc-u").any(normalized::contains) -> "usa"
            else -> "other"
        }
    }

    private const val MAX_DEPTH = 4
    private const val MAX_FILES = 4096
}

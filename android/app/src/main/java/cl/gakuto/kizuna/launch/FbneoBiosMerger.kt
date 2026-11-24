package cl.gakuto.kizuna.launch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cl.gakuto.kizuna.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Builds a self-contained FBNeo archive when a game declares separate BIOS dependencies.
 *
 * The original ROM set remains the source of truth. BIOS entries are only appended when the
 * user has explicitly selected a BIOS directory and the target entry is not already present.
 */
object FbneoBiosMerger {
    data class Result(
        val file: File,
        val mergedSets: Set<String>,
        val missingSets: Set<String>,
    )

    suspend fun mergeConfiguredBios(
        context: Context,
        biosTreeUri: Uri?,
        gameArchive: File,
        requestedSets: List<String>,
        diagnostics: DiagnosticLogger.Session,
    ): Result = withContext(Dispatchers.IO) {
        val requested = requestedSets
            .map { it.trim().removeSuffix(".zip").lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .distinct()

        if (requested.isEmpty() || biosTreeUri == null) {
            return@withContext Result(gameArchive, emptySet(), requested.toSet())
        }

        val root = DocumentFile.fromTreeUri(context, biosTreeUri)
            ?: return@withContext Result(gameArchive, emptySet(), requested.toSet())
        val available = root.listFiles()
            .filter { it.isFile }
            .associateBy { it.name?.lowercase(Locale.ROOT).orEmpty() }

        val selected = requested.mapNotNull { setName ->
            available["$setName.zip"]?.let { setName to it }
        }
        val missing = requested.toSet() - selected.mapTo(linkedSetOf()) { it.first }
        if (selected.isEmpty()) return@withContext Result(gameArchive, emptySet(), missing)

        val existingNames = linkedSetOf<String>()
        ZipFile(gameArchive).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach {
                existingNames += it.name.lowercase(Locale.ROOT)
            }
        }

        val temporary = File(gameArchive.parentFile, "${gameArchive.name}.bios-merge.part")
        if (temporary.exists()) temporary.delete()
        val merged = linkedSetOf<String>()

        try {
            ZipOutputStream(BufferedOutputStream(temporary.outputStream(), BUFFER_SIZE)).use { output ->
                ZipFile(gameArchive).use { source ->
                    source.entries().asSequence().forEach { entry ->
                        if (entry.isDirectory) return@forEach
                        output.putNextEntry(copyEntry(entry))
                        source.getInputStream(entry).use { it.copyTo(output, BUFFER_SIZE) }
                        output.closeEntry()
                    }
                }

                selected.forEach { (setName, document) ->
                    var addedForSet = 0
                    context.contentResolver.openInputStream(document.uri)?.use { raw ->
                        ZipInputStream(BufferedInputStream(raw, BUFFER_SIZE)).use { biosZip ->
                            while (true) {
                                val entry = biosZip.nextEntry ?: break
                                if (!entry.isDirectory) {
                                    val normalized = entry.name.lowercase(Locale.ROOT)
                                    if (normalized !in existingNames) {
                                        output.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                                        biosZip.copyTo(output, BUFFER_SIZE)
                                        output.closeEntry()
                                        existingNames += normalized
                                        addedForSet++
                                    }
                                }
                                biosZip.closeEntry()
                            }
                        }
                    }
                    if (addedForSet > 0) {
                        merged += setName
                        diagnostics.info("FBNEO_BIOS_MERGED", "bios=$setName entries=$addedForSet target=${gameArchive.name}")
                    } else {
                        diagnostics.info("FBNEO_BIOS_ALREADY_PRESENT", "bios=$setName target=${gameArchive.name}")
                        merged += setName
                    }
                }
            }

            require(temporary.length() > 4_096L) { "No se pudo preparar el ROM set de FBNeo con sus BIOS." }
            if (gameArchive.exists() && !gameArchive.delete()) {
                error("No se pudo reemplazar el ROM set temporal de FBNeo.")
            }
            require(temporary.renameTo(gameArchive)) { "No se pudo finalizar el ROM set temporal de FBNeo." }
            Result(gameArchive, merged, missing)
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }


    suspend fun mergeDownloadedBios(
        gameArchive: File,
        biosArchives: Map<String, File>,
        diagnostics: DiagnosticLogger.Session,
    ): Result = withContext(Dispatchers.IO) {
        if (biosArchives.isEmpty()) return@withContext Result(gameArchive, emptySet(), emptySet())

        val existingNames = linkedSetOf<String>()
        ZipFile(gameArchive).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach {
                existingNames += it.name.lowercase(Locale.ROOT)
            }
        }

        val temporary = File(gameArchive.parentFile, "${gameArchive.name}.romgi-bios.part")
        if (temporary.exists()) temporary.delete()
        val merged = linkedSetOf<String>()
        val missing = linkedSetOf<String>()

        try {
            ZipOutputStream(BufferedOutputStream(temporary.outputStream(), BUFFER_SIZE)).use { output ->
                ZipFile(gameArchive).use { source ->
                    source.entries().asSequence().forEach { entry ->
                        if (entry.isDirectory) return@forEach
                        output.putNextEntry(copyEntry(entry))
                        source.getInputStream(entry).use { it.copyTo(output, BUFFER_SIZE) }
                        output.closeEntry()
                    }
                }

                biosArchives.forEach { (setName, archive) ->
                    if (!archive.isFile || archive.length() <= 4_096L) {
                        missing += setName
                        return@forEach
                    }
                    var added = 0
                    ZipFile(archive).use { biosZip ->
                        biosZip.entries().asSequence().forEach { entry ->
                            if (entry.isDirectory) return@forEach
                            val normalized = entry.name.lowercase(Locale.ROOT)
                            if (normalized !in existingNames) {
                                output.putNextEntry(copyEntry(entry))
                                biosZip.getInputStream(entry).use { it.copyTo(output, BUFFER_SIZE) }
                                output.closeEntry()
                                existingNames += normalized
                                added++
                            }
                        }
                    }
                    merged += setName
                    diagnostics.info("FBNEO_ROMGI_BIOS_MERGED", "bios=$setName entries=$added target=${gameArchive.name}")
                }
            }

            require(temporary.length() > 4_096L) { "No se pudo preparar el ROM set de FBNeo con las BIOS de ROMgi." }
            if (gameArchive.exists() && !gameArchive.delete()) error("No se pudo reemplazar el ROM set temporal de FBNeo.")
            require(temporary.renameTo(gameArchive)) { "No se pudo finalizar el ROM set temporal de FBNeo." }
            Result(gameArchive, merged, missing)
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private fun copyEntry(entry: ZipEntry): ZipEntry = ZipEntry(entry.name).apply {
        time = entry.time
        comment = entry.comment
        extra = entry.extra
    }

    private const val BUFFER_SIZE = 256 * 1024
}

package cl.gakuto.kizuna.launch

import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

object RomPreparedFileValidator {
    suspend fun validate(game: GameEntity, file: File, diagnostics: DiagnosticLogger.Session) = withContext(Dispatchers.IO) {
        require(file.isFile) { "El archivo preparado no existe." }
        require(!isOzVirtualDescriptor(file)) {
            "iiSU entregó un descriptor virtual de Oz en vez de la ROM real. Oz detuvo el lanzamiento antes de entrar al core para evitar un cierre nativo. Vuelve a publicar la plataforma/colección en iiSU y reintenta."
        }
        RomPreparedPayloadValidator.validate(file)
        if (RomArchivePreparer.needsNativeExtraction(game.systemId, file.extension)) {
            require(!file.extension.equals("zip", true)) {
                "El ZIP no pudo convertirse a la ROM nativa requerida por ${game.platform}. Oz detuvo el lanzamiento para proteger el core."
            }
            require(!file.extension.equals("7z", true)) {
                "La fuente disponible está comprimida en 7z. Oz todavía no puede extraer este formato de forma verificada para ${game.platform}; elige otra fuente o un archivo propio."
            }
        }
        if (game.systemId == "arcade-fbneo" && file.extension.equals("zip", true)) {
            FbneoSupport.expectedArchiveName(game)?.let { expected ->
                require(file.name.equals(expected, ignoreCase = true)) {
                    "El romset ${file.name} no coincide con $expected, que es el set exacto esperado por el catálogo FBNeo. No se abrirá el core para evitar una versión o revisión incompatible."
                }
            }
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
                val virtualOnly = entries.size == 1 && entries.first().name.equals("oz-retrorom.json", true)
                require(!virtualOnly) { "Se recibió el acceso virtual de iiSU en vez del ROM set real." }
                require(entries.isNotEmpty()) { "El archivo ZIP de FBNeo no contiene componentes de ROM." }
            }
        }
        diagnostics.info(
            "VALIDATION_OK",
            "file=${file.name} bytes=${file.length()} romset=${game.romSet.orEmpty()} bios=${game.biosSets.joinToString(",")}",
        )
    }

    private fun isOzVirtualDescriptor(file: File): Boolean {
        if (!file.isFile) return false
        if (file.extension.equals("zip", true)) {
            return runCatching {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence().any { entry ->
                        !entry.isDirectory && entry.name.substringAfterLast('/').equals("oz-retrorom.json", true)
                    }
                }
            }.getOrDefault(false)
        }
        // Plain bridge descriptors are deliberately tiny JSON files carrying a real platform
        // extension (for example .sfc) so iiSU can classify them. Inspect only small files and
        // require all Oz bridge markers to avoid false positives on legitimate ROM/homebrew data.
        if (file.length() !in 1..MAX_DESCRIPTOR_BYTES) return false
        return runCatching {
            val text = file.inputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
            text.contains("\"brand\"") &&
                text.contains("Oz RetroRom") &&
                text.contains("\"contentMode\"") &&
                text.contains("oz-virtual-on-demand") &&
                text.contains("\"gameId\"")
        }.getOrDefault(false)
    }

    private const val MAX_DESCRIPTOR_BYTES = 64_000L
}

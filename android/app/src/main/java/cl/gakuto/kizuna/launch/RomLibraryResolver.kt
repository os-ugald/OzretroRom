package cl.gakuto.kizuna.launch

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AuthorizedSource
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.util.CatalogFilters
import cl.gakuto.kizuna.util.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

/**
 * Locates a user-owned ROM in a SAF tree shared with iiSU.
 *
 * The resolver is deliberately deterministic: exact FBNeo romsets win, then exact title/file
 * matches, and finally a bounded canonical-title fallback that ignores No-Intro/Redump region and
 * revision tags. This makes one-tap launching practical without guessing unrelated short titles.
 */
class RomLibraryResolver(
    private val context: Context,
    private val preferences: AppPreferences,
) {
    suspend fun resolve(game: GameEntity): AuthorizedSource? = withContext(Dispatchers.IO) {
        // Source Index projections carry a durable GameId. Scanning by title would allow another
        // logical game (for example a numbered sequel) to replace that strong identity.
        if (!LocalRomIdentityPolicy.allowsLegacyLibraryScan(game)) return@withContext null
        val treeUri = preferences.romTreeUriFor(game.systemId) ?: return@withContext null
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
        val extensionPriority = acceptedExtensions(game).withIndex().associate { it.value to it.index }
        val exactNames = exactCandidateNames(game, extensionPriority.keys)
        val targetStems = targetStems(game)
        val queue = ArrayDeque<Pair<DocumentFile, Int>>()
        queue.add(root to 0)
        var visited = 0
        var best: Match? = null

        while (queue.isNotEmpty() && visited < MAX_DOCUMENTS) {
            val (node, depth) = queue.removeFirst()
            for (child in runCatching { node.listFiles().toList() }.getOrDefault(emptyList())) {
                if (++visited > MAX_DOCUMENTS) break
                if (child.isDirectory) {
                    if (depth < MAX_DEPTH) queue.addLast(child to depth + 1)
                    continue
                }
                val name = child.name ?: continue
                val lower = name.lowercase()
                val extension = lower.substringAfterLast('.', "")
                val extensionRank = extensionPriority[extension] ?: continue

                val exactRank = exactNames[lower]
                val score = when {
                    exactRank != null -> exactRank * 100 + extensionRank
                    else -> fuzzyScore(lower.substringBeforeLast('.'), targetStems)?.plus(10_000 + extensionRank)
                } ?: continue

                if (best == null || score < best!!.score) best = Match(child, score)
                if (score == 0) return@withContext AuthorizedSource(child.uri.toString(), name)
            }
        }
        best?.file?.let { AuthorizedSource(it.uri.toString(), it.name) }
    }

    private fun exactCandidateNames(game: GameEntity, extensions: Set<String>): Map<String, Int> = buildMap {
        fun addCandidate(name: String, rank: Int) {
            val normalized = name.trim().lowercase()
            if (normalized.isNotBlank()) putIfAbsent(normalized, rank)
        }

        FbneoSupport.expectedArchiveName(game)?.let { addCandidate(it, 0) }
        game.romSet?.trim()?.takeIf(String::isNotBlank)?.let { romSet ->
            addCandidate(romSet, 1)
            extensions.forEach { addCandidate("$romSet.$it", 1) }
        }
        listOf(game.id, game.title, game.canonicalTitle)
            .filter(String::isNotBlank)
            .forEach { value ->
                val raw = value.trim().lowercase()
                val slug = TextNormalizer.slug(value).lowercase()
                addCandidate(raw, 10)
                extensions.forEach {
                    addCandidate("$raw.$it", 10)
                    addCandidate("$slug.$it", 11)
                }
            }
    }

    private fun targetStems(game: GameEntity): Set<String> = buildSet {
        listOfNotNull(game.romSet, game.id, game.title, game.canonicalTitle)
            .filter(String::isNotBlank)
            .forEach { value ->
                add(TextNormalizer.normalize(value))
                add(CatalogFilters.canonicalTitle(value))
                add(TextNormalizer.normalize(TextNormalizer.slug(value)))
            }
    }.filterTo(linkedSetOf()) { it.length >= MIN_FUZZY_LENGTH }

    private fun fuzzyScore(fileStem: String, targets: Set<String>): Int? {
        val normalized = TextNormalizer.normalize(fileStem)
        val canonical = CatalogFilters.canonicalTitle(fileStem)
        if (normalized in targets || canonical in targets) return 0

        // Accommodate uncommon dump suffixes not enclosed in brackets, while avoiding broad matches.
        val prefix = targets.firstOrNull { target ->
            target.length >= MIN_PREFIX_LENGTH &&
                (canonical.startsWith("$target ") || normalized.startsWith("$target "))
        }
        return if (prefix != null) 100 + (canonical.length - prefix.length).coerceAtLeast(0) else null
    }

    private fun acceptedExtensions(game: GameEntity): LinkedHashSet<String> = linkedSetOf<String>().apply {
        when (game.systemId) {
            "arcade-fbneo" -> addAll(listOf("zip", "7z"))
            "ps1" -> addAll(listOf("chd", "pbp", "cue", "m3u", "iso", "bin"))
            "ps2" -> addAll(listOf("chd", "iso", "cso", "gz"))
            "psp" -> addAll(listOf("iso", "cso", "pbp"))
            "3ds" -> addAll(listOf("3ds", "cci", "cxi", "cia"))
            "gamecube" -> addAll(listOf("rvz", "iso", "gcz", "ciso"))
            "wii" -> addAll(listOf("rvz", "wbfs", "wia", "iso", "gcz"))
            "dreamcast" -> addAll(listOf("chd", "gdi", "cue", "cdi", "m3u", "iso"))
            "saturn", "sega-cd", "pc-engine-cd", "pc-fx" -> addAll(listOf("chd", "cue", "m3u", "iso"))
            "n64" -> addAll(listOf("z64", "n64", "v64"))
            "snes" -> addAll(listOf("sfc", "smc", "fig", "bs"))
            "genesis" -> addAll(listOf("md", "bin", "gen", "smd"))
            "master-system" -> addAll(listOf("sms", "bin", "rom"))
            "nes" -> addAll(listOf("nes", "unf", "unif"))
            "amiga" -> addAll(listOf("lha", "adf", "hdf", "ipf"))
            "commodore-64" -> addAll(listOf("d64", "g64", "t64", "tap", "crt", "prg", "p00"))
            "scummvm" -> add("scummvm")
            else -> game.virtualExtension.lowercase().takeIf(String::isNotBlank)?.let(::add)
        }
        add("zip")
        add("7z")
    }

    private data class Match(val file: DocumentFile, val score: Int)

    companion object {
        private const val MAX_DEPTH = 5
        private const val MAX_DOCUMENTS = 30_000
        private const val MIN_FUZZY_LENGTH = 4
        private const val MIN_PREFIX_LENGTH = 7
    }
}

package cl.gakuto.kizuna.catalog

import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.sagas.SagasRepository
import cl.gakuto.kizuna.util.CatalogFilters
import java.util.Locale

data class DynamicSmartList(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val accentHex: String,
    val games: List<GameEntity>,
)

/**
 * Dynamically curates smart discovery lists based on what the user plays, favorites, and stores.
 */
object DynamicSmartListsEngine {
    fun buildSmartLists(
        recent: List<GameEntity>,
        favorites: List<GameEntity>,
        local: List<GameEntity>,
        catalogSample: List<GameEntity>,
    ): List<DynamicSmartList> {
        val lists = mutableListOf<DynamicSmartList>()
        val pool = (catalogSample + favorites + local + recent).distinctBy { it.id }
        val playedKeys = recent.mapTo(HashSet()) { normKey(it) }
        val favKeys = favorites.mapTo(HashSet()) { normKey(it) }

        fun dedupeUnplayed(source: List<GameEntity>, excludePlayed: Boolean = true, limit: Int = 18): List<GameEntity> {
            val seen = HashSet<String>()
            val out = ArrayList<GameEntity>(limit)
            for (g in source) {
                val k = normKey(g)
                if (excludePlayed && k in playedKeys) continue
                if (seen.add(k)) out += g
                if (out.size >= limit) break
            }
            return out
        }

        // 1. Affinity list: "Porque jugaste <Last Title>"
        val anchor = recent.firstOrNull() ?: favorites.firstOrNull()
        if (anchor != null) {
            val anchorGenres = CatalogFilters.genreKeys(anchor.genre)
            val anchorSys = anchor.systemId.lowercase(Locale.ROOT)
            val anchorTitleClean = CatalogFilters.displayTitle(anchor.title)
            val matches = pool.filter { cand ->
                val k = normKey(cand)
                k != normKey(anchor) && (
                    CatalogFilters.genreKeys(cand.genre).any { it in anchorGenres } ||
                        cand.systemId.equals(anchorSys, ignoreCase = true)
                    )
            }.sortedWith(
                compareByDescending<GameEntity> { CatalogFilters.genreKeys(it.genre).any { g -> g in anchorGenres } }
                    .thenByDescending { it.systemId.equals(anchorSys, ignoreCase = true) }
                    .thenByDescending { !it.contentUri.isNullOrBlank() }
                    .thenBy { it.title },
            )
            val finalGames = dedupeUnplayed(matches, excludePlayed = true, limit = 16)
                .ifEmpty { dedupeUnplayed(matches, excludePlayed = false, limit = 16) }
            if (finalGames.isNotEmpty()) {
                lists += DynamicSmartList(
                    id = "ia-because-played",
                    title = "Porque jugaste $anchorTitleClean",
                    subtitle = "Recomendados según tu última partida",
                    badge = "IA · DINÁMICA",
                    accentHex = "#00E5FF",
                    games = finalGames,
                )
            }
        }

        // 2. Dominant Console Profile (weighted by recent plays + favorites + local ROMs)
        val consoleWeights = LinkedHashMap<String, Int>()
        recent.forEachIndexed { idx, g ->
            val sys = g.systemId.lowercase(Locale.ROOT)
            consoleWeights[sys] = (consoleWeights[sys] ?: 0) + (20 - idx).coerceAtLeast(3)
        }
        favorites.forEach { g ->
            val sys = g.systemId.lowercase(Locale.ROOT)
            consoleWeights[sys] = (consoleWeights[sys] ?: 0) + 12
        }
        local.forEach { g ->
            val sys = g.systemId.lowercase(Locale.ROOT)
            consoleWeights[sys] = (consoleWeights[sys] ?: 0) + 4
        }
        val topSystem = consoleWeights.entries.maxByOrNull { it.value }?.key ?: "snes"
        val topSysGames = pool.filter { it.systemId.equals(topSystem, ignoreCase = true) }
            .sortedWith(
                compareByDescending<GameEntity> { normKey(it) in favKeys }
                    .thenByDescending { !it.contentUri.isNullOrBlank() }
                    .thenBy { it.title },
            )
        val pickedTopSys = dedupeUnplayed(topSysGames, excludePlayed = false, limit = 18)
        if (pickedTopSys.isNotEmpty()) {
            val displayPlatform = pickedTopSys.first().platform
            lists += DynamicSmartList(
                id = "ia-top-console-$topSystem",
                title = "Tu consola estrella: $displayPlatform",
                subtitle = "Selección adaptada a tu historial y descargas",
                badge = "IA · PERFIL",
                accentHex = "#C4B5FD",
                games = pickedTopSys,
            )
        }

        // 3. Ready to play now (local library prioritized by recency/favorites)
        if (local.isNotEmpty()) {
            val localOrdered = (local + pool.filter { !it.contentUri.isNullOrBlank() })
                .distinctBy { normKey(it) }
                .sortedWith(
                    compareByDescending<GameEntity> { normKey(it) in favKeys }
                        .thenByDescending { it.lastPlayedAt ?: 0L }
                        .thenBy { it.title },
                )
            lists += DynamicSmartList(
                id = "ia-ready-vault",
                title = "Listos en tu bóveda",
                subtitle = "ROMs descargadas listas para abrir en RetroArch",
                badge = "LOCAL",
                accentHex = "#39FF88",
                games = localOrdered.take(18),
            )
        }

        // 4. Quick arcade & action bursts
        val quickAction = pool.filter { g ->
            val genres = CatalogFilters.genreKeys(g.genre)
            g.systemId.equals("arcade-fbneo", ignoreCase = true) ||
                genres.any { it in setOf("beat-em-up", "fighting", "run-and-gun", "shoot-em-up", "action") }
        }.sortedWith(
            compareByDescending<GameEntity> { !it.contentUri.isNullOrBlank() }
                .thenBy { normKey(it) in playedKeys }
                .thenBy { it.title },
        )
        val quickFinal = dedupeUnplayed(quickAction, excludePlayed = true, limit = 18)
            .ifEmpty { dedupeUnplayed(quickAction, excludePlayed = false, limit = 18) }
        if (quickFinal.isNotEmpty()) {
            lists += DynamicSmartList(
                id = "ia-quick-action",
                title = "Partidas rápidas · Arcade y acción",
                subtitle = "Combate, beat 'em up y arcade directo sin esperas",
                badge = "ACCIÓN",
                accentHex = "#FCD34D",
                games = quickFinal,
            )
        }

        // 5. Unplayed Hidden Gems (dynamically rotates as recent history grows!)
        val unplayedGems = dedupeUnplayed(
            pool.sortedWith(
                compareByDescending<GameEntity> { it.source == "curated" || it.source == "seed" }
                    .thenBy { (it.id.hashCode() xor recent.size.shl(5)) },
            ),
            excludePlayed = true,
            limit = 18,
        )
        if (unplayedGems.isNotEmpty()) {
            lists += DynamicSmartList(
                id = "ia-hidden-gems",
                title = "Joyas que aún no pruebas",
                subtitle = "Se renueva automáticamente según lo que vas jugando",
                badge = "IA · DESCUBRIR",
                accentHex = "#38BDF8",
                games = unplayedGems,
            )
        }

        // 6. 16-bit Legends (SNES + Genesis unplayed or top hits)
        val era16 = pool.filter {
            it.systemId.lowercase(Locale.ROOT) in setOf("snes", "genesis", "md")
        }.sortedWith(
            compareByDescending<GameEntity> { normKey(it) in favKeys }
                .thenBy { normKey(it) in playedKeys }
                .thenBy { it.title },
        )
        val era16Final = dedupeUnplayed(era16, excludePlayed = false, limit = 18)
        if (era16Final.isNotEmpty()) {
            lists += DynamicSmartList(
                id = "ia-16bit-legends",
                title = "Edad dorada de 16 bits",
                subtitle = "Lo mejor de Super Nintendo y Sega Mega Drive",
                badge = "16-BIT",
                accentHex = "#F472B6",
                games = era16Final,
            )
        }

        return lists
    }

    private fun normKey(game: GameEntity): String {
        val clean = SagasRepository.norm(SagasRepository.cleanTitle(game.title))
        return "${game.systemId.lowercase(Locale.ROOT)}|$clean"
    }
}

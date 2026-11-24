package cl.gakuto.kizuna.ui

import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameListSpec
import cl.gakuto.kizuna.data.LibraryRepository
import java.util.ArrayDeque

/** Keeps only a small, scroll-driven window of a large catalogue in UI memory. */
class IncrementalGameLoader(
    private val repository: LibraryRepository,
    private val pageSize: Int,
) {
    data class Result(
        val appended: List<GameEntity>,
        val loadedCount: Int,
        val totalMatches: Int,
        val exhausted: Boolean,
    )

    private var spec = GameListSpec()
    private var rawOffset = 0
    private var rawExhausted = false
    private var totalMatches = 0
    private var loadedCount = 0
    private val seenCanonical = linkedSetOf<String>()
    private val pending = ArrayDeque<GameEntity>()

    suspend fun reset(next: GameListSpec): Result {
        spec = next
        rawOffset = 0
        rawExhausted = false
        totalMatches = repository.count(next)
        loadedCount = 0
        seenCanonical.clear()
        pending.clear()
        return loadMore()
    }

    suspend fun loadMore(): Result {
        if (isExhausted()) return Result(emptyList(), loadedCount, totalMatches, true)
        val appended = ArrayList<GameEntity>(pageSize)
        while (appended.size < pageSize) {
            while (pending.isNotEmpty() && appended.size < pageSize) {
                appended += pending.removeFirst()
            }
            if (appended.size >= pageSize || rawExhausted) break
            fetchRawBatch()
        }
        loadedCount += appended.size
        return Result(appended, loadedCount, totalMatches, isExhausted())
    }

    private suspend fun fetchRawBatch() {
        val rawLimit = (pageSize * 2).coerceAtLeast(MIN_RAW_BATCH)
        val page = repository.page(spec, rawLimit, rawOffset)
        rawOffset = page.nextOffset
        rawExhausted = page.exhausted
        page.items.forEach { game ->
            if (spec.includesAllRegions || seenCanonical.add(canonicalKey(game))) {
                pending.addLast(game)
            }
        }
    }

    private fun canonicalKey(game: GameEntity): String {
        val canonical = game.canonicalTitle.ifBlank { game.normalizedTitle.ifBlank { game.title.lowercase() } }
        return "${game.systemId}|$canonical"
    }

    private fun isExhausted(): Boolean = rawExhausted && pending.isEmpty()

    companion object {
        private const val MIN_RAW_BATCH = 240
    }
}

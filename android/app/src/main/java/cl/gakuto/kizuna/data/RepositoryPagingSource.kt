package cl.gakuto.kizuna.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * Bounded adapter around the existing dynamic Room SQL. It intentionally reuses the same
 * full-database predicates as the Views implementation so Compose cannot accidentally filter
 * only the visible page.
 */
class RepositoryPagingSource(
    private val repository: LibraryRepository,
    private val spec: GameListSpec,
) : PagingSource<Int, GameEntity>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GameEntity> = try {
        val offset = params.key ?: 0
        val page = repository.page(spec, params.loadSize.coerceAtMost(MAX_LOAD), offset)
        LoadResult.Page(
            data = page.items,
            prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
            nextKey = if (page.exhausted) null else page.nextOffset,
        )
    } catch (error: Throwable) {
        LoadResult.Error(error)
    }

    override fun getRefreshKey(state: PagingState<Int, GameEntity>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(state.config.pageSize)
            ?: page.nextKey?.minus(state.config.pageSize)?.coerceAtLeast(0)
    }

    companion object { private const val MAX_LOAD = 240 }
}

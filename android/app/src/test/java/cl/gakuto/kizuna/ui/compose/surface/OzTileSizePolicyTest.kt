package cl.gakuto.kizuna.ui.compose.surface

import org.junit.Assert.assertEquals
import org.junit.Test

class OzTileSizePolicyTest {
    @Test
    fun persistedTileSizesMatchAppPreferencesContract() {
        assertEquals(listOf("1x1", "2x1", "2x2"), OzTileSize.entries.map { it.key })
    }

    @Test
    fun quickResizeCyclesOnlyThroughPersistableSizes() {
        var current = OzTileSize.SMALL
        val visited = mutableListOf<String>()
        repeat(OzTileSize.entries.size) {
            visited += current.key
            current = current.next()
        }

        assertEquals(listOf("1x1", "2x1", "2x2"), visited)
        assertEquals(OzTileSize.SMALL, current)
    }
}

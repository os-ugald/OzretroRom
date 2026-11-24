package cl.gakuto.kizuna.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesSurfacePlacementTest {
    @Test
    fun resizePreservesExistingPageAndSlot() {
        val current = AppPreferences.SurfacePlacement(page = 3, slot = 7, size = "1x1")

        val resized = AppPreferences.resizedSurfacePlacement(current, "2x2")

        assertEquals(3, resized.page)
        assertEquals(7, resized.slot)
        assertEquals("2x2", resized.size)
    }

    @Test
    fun invalidSizeFallsBackWithoutInventingVerticalMode() {
        val current = AppPreferences.SurfacePlacement(page = 2, slot = 5, size = "2x1")

        val resized = AppPreferences.resizedSurfacePlacement(current, "1x2")

        assertEquals(2, resized.page)
        assertEquals(5, resized.slot)
        assertEquals("1x1", resized.size)
    }
}

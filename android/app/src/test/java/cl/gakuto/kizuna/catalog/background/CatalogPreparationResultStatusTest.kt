package cl.gakuto.kizuna.catalog.background

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogPreparationResultStatusTest {
    @Test
    fun completeTargetsAreSuccessful() {
        assertEquals(
            CatalogPreparationStatus.SUCCESS,
            catalogPreparationResultStatus(totalTargets = 44, readyTargets = 44),
        )
    }

    @Test
    fun someReadyTargetsArePartial() {
        assertEquals(
            CatalogPreparationStatus.PARTIAL,
            catalogPreparationResultStatus(totalTargets = 44, readyTargets = 16),
        )
    }

    @Test
    fun noReadyTargetsAreFailed() {
        assertEquals(
            CatalogPreparationStatus.FAILED,
            catalogPreparationResultStatus(totalTargets = 44, readyTargets = 0),
        )
    }
}

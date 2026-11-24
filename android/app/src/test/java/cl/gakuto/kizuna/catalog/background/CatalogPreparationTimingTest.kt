package cl.gakuto.kizuna.catalog.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPreparationTimingTest {
    @Test
    fun activeTaskUsesCurrentTime() {
        val snapshot = snapshot(
            status = CatalogPreparationStatus.RUNNING,
            startedAt = 1_000L,
            updatedAt = 2_000L,
        )

        assertEquals(4_000L, catalogPreparationElapsedMillis(snapshot, now = 5_000L))
    }

    @Test
    fun finishedTaskKeepsItsFinalDuration() {
        val snapshot = snapshot(
            status = CatalogPreparationStatus.SUCCESS,
            startedAt = 1_000L,
            updatedAt = 3_500L,
        )

        assertEquals(2_500L, catalogPreparationElapsedMillis(snapshot, now = 99_000L))
    }

    @Test
    fun suspendedTaskFreezesUntilItIsResumed() {
        val snapshot = snapshot(
            status = CatalogPreparationStatus.SUSPENDED,
            startedAt = 1_000L,
            updatedAt = 4_000L,
        )

        assertEquals(3_000L, catalogPreparationElapsedMillis(snapshot, now = 99_000L))
        assertTrue(snapshot.canResume)
        assertFalse(snapshot.isActive)
    }

    @Test
    fun activeTaskBecomesStaleOnlyAfterHeartbeatLimit() {
        val snapshot = snapshot(
            status = CatalogPreparationStatus.RUNNING,
            startedAt = 1_000L,
            updatedAt = 2_000L,
        ).copy(lastProgressAt = 5_000L)

        assertFalse(isCatalogPreparationStale(snapshot, now = 304_999L, staleAfterMillis = 300_000L))
        assertTrue(isCatalogPreparationStale(snapshot, now = 305_000L, staleAfterMillis = 300_000L))
        assertFalse(
            isCatalogPreparationStale(
                snapshot.copy(status = CatalogPreparationStatus.SUSPENDED),
                now = 999_999L,
                staleAfterMillis = 300_000L,
            ),
        )
    }

    @Test
    fun elapsedFormatterSupportsMinutesAndHours() {
        assertEquals("00:00", formatCatalogPreparationElapsed(0L))
        assertEquals("01:05", formatCatalogPreparationElapsed(65_000L))
        assertEquals("1:01:01", formatCatalogPreparationElapsed(3_661_000L))
    }

    private fun snapshot(
        status: CatalogPreparationStatus,
        startedAt: Long,
        updatedAt: Long,
    ) = CatalogPreparationSnapshot(
        status = status,
        systemIds = listOf("ps1"),
        completeCatalog = true,
        stage = "Preparando",
        currentSystem = "Sony PlayStation",
        processedSystems = 1,
        totalTargets = 44,
        readyTargets = 0,
        failedCount = 0,
        error = null,
        startedAt = startedAt,
        updatedAt = updatedAt,
    )
}

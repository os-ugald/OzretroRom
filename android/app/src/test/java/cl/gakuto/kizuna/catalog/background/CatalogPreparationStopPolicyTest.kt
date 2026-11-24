package cl.gakuto.kizuna.catalog.background

import android.app.job.JobParameters
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPreparationStopPolicyTest {
    @Test
    fun systemStopsRemainResumable() {
        assertTrue(catalogPreparationShouldRetry(JobParameters.STOP_REASON_TIMEOUT))
        assertTrue(catalogPreparationShouldRetry(JobParameters.STOP_REASON_DEVICE_STATE))
        assertTrue(catalogPreparationShouldRetry(JobParameters.STOP_REASON_BACKGROUND_RESTRICTION))
    }

    @Test
    fun explicitStopsAreRespected() {
        assertFalse(catalogPreparationShouldRetry(JobParameters.STOP_REASON_CANCELLED_BY_APP))
        assertFalse(catalogPreparationShouldRetry(JobParameters.STOP_REASON_USER))
    }
}

package cl.gakuto.kizuna.ui.compose.domain

import android.app.Application
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.ui.compose.OzViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DomainViewModelsTest {

    @Test
    fun `domain viewmodels initialize cleanly and update reactive states`() {
        val app: Application = RuntimeEnvironment.getApplication()

        val settingsVm = SettingsViewModel(app)
        settingsVm.setAutoLaunchAfterDownload(false)
        settingsVm.setGeminiModel("gemini-2.5-flash")
        settingsVm.setAppThemeMode(AppThemeMode.LIGHT)
        assertFalse(settingsVm.autoLaunchAfterDownload.value)
        assertEquals("gemini-2.5-flash", settingsVm.geminiModel.value)
        assertEquals(AppThemeMode.LIGHT, settingsVm.appThemeMode.value)

        val hubVm = HubSyncViewModel(app)
        hubVm.savePcHubUrl("http://192.168.100.173:17894/")
        assertEquals("http://192.168.100.173:17894", hubVm.pcHubUrl.value)

        val sagasVm = SagasViewModel(app)
        assertNull(sagasVm.sagaAuditReport.value)
        assertFalse(sagasVm.isAuditingSaga.value)

        val catalogVm = CatalogViewModel(app)
        assertTrue(catalogVm.systems.isNotEmpty())
        assertEquals("", catalogVm.catalogQuery.value)

        val homeVm = HomeViewModel(app)
        assertNull(homeVm.selectedGame.value)

        val facade = OzViewModel(app)
        assertEquals(catalogVm.systems.size, facade.systems.size)
    }
}

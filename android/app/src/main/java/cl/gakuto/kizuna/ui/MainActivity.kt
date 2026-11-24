package cl.gakuto.kizuna.ui

import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.gakuto.kizuna.OzApplication
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.AppOrientationMode
import cl.gakuto.kizuna.ui.compose.OzApp
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.theme.OzTheme

/** Single Compose host. Legacy Views remain in source while their replacements are validated. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val model: OzViewModel = viewModel(factory = OzViewModel.factory(application as OzApplication))
            val themeMode by model.appThemeMode.collectAsStateWithLifecycle()
            val orientationMode by model.appOrientationMode.collectAsStateWithLifecycle()
            androidx.compose.runtime.SideEffect {
                requestedOrientation = when (orientationMode) {
                    AppOrientationMode.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    AppOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    AppOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = themeMode == AppThemeMode.LIGHT
                    isAppearanceLightNavigationBars = themeMode == AppThemeMode.LIGHT
                }
            }
            OzTheme(themeMode = themeMode) {
                OzApp(model)
            }
        }
    }
}

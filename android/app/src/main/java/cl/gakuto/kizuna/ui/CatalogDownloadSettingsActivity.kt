@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cl.gakuto.kizuna.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import cl.gakuto.kizuna.catalog.RomgiCatalog
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.CatalogPerformanceMode
import cl.gakuto.kizuna.data.RomRegionPreference
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTheme
import cl.gakuto.kizuna.ui.compose.theme.OzTypography

/** Catalog/source policy lives here instead of polluting Emulation & integrations. */
class CatalogDownloadSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = AppPreferences(this)
        val romgi = RomgiCatalog(this)
        setContent {
            OzTheme {
                CatalogDownloadSettingsContent(preferences, romgi, ::finish)
            }
        }
    }
}

@Composable
private fun CatalogDownloadSettingsContent(
    preferences: AppPreferences,
    romgi: RomgiCatalog,
    onBack: () -> Unit,
) {
    var revision by remember { mutableStateOf(0) }
    var selectedRegion by remember { mutableStateOf(preferences.preferredRegion) }
    var selectedPerformance by remember { mutableStateOf(preferences.catalogPerformanceMode) }
    revision
    val regionOptions = RomRegionPreference.entries.map { it.key to it.label }

    Column(
        Modifier
            .fillMaxSize()
            .background(OzColors.DeepSpace)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(OzColors.PurpleDeep, OzColors.DeepSpace, OzColors.ElectricBlue.copy(alpha = .18f))))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("‹  Volver", style = OzTypography.meta, color = OzColors.Cyan, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp))
            Text("Catálogo y descargas", style = OzTypography.hero, color = OzColors.TextPrimary)
            Text("ROMgi · región · transporte · rendimiento", style = OzTypography.meta, color = OzColors.TextSecondary)
        }

        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                CatalogSettingCard("Resolución automática", "Cómo Oz encuentra y prepara una fuente cuando un juego no está todavía en tu biblioteca.") {
                    CatalogToggle("Resolver y descargar automáticamente con ROMgi", preferences.romgiAutoEnabled) {
                        preferences.romgiAutoEnabled = it; revision++
                    }
                    CatalogToggle("Descargas automáticas solo mediante Wi‑Fi", preferences.romgiWifiOnly) {
                        preferences.romgiWifiOnly = it; revision++
                    }
                    CatalogToggle("Permitir transporte torrent selectivo", preferences.romgiTorrentsEnabled) {
                        preferences.romgiTorrentsEnabled = it; revision++
                    }
                }
            }

            item {
                CatalogSettingCard(
                    "Región preferida",
                    "Oz prioriza USA y luego Japón. En consolas de TV, World sin frecuencia verificada y Europa/PAL siempre requieren confirmación.",
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        regionOptions.forEach { (key, label) ->
                            CatalogChoiceButton(
                                text = label,
                                selected = selectedRegion == key,
                            ) {
                                selectedRegion = key
                                preferences.preferredRegion = key
                                revision++
                            }
                        }
                    }
                }
            }

            item {
                CatalogSettingCard("Rendimiento del catálogo", "Controla el tamaño de cada carga progresiva sin limitar cuántos juegos existen.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        CatalogPerformanceMode.entries.forEach { mode ->
                            CatalogChoiceButton(
                                text = mode.label,
                                selected = selectedPerformance == mode,
                            ) {
                                selectedPerformance = mode
                                preferences.catalogPerformanceMode = mode
                                revision++
                            }
                        }
                    }
                }
            }

            item {
                val bytes = romgi.cachedCatalogSize()
                CatalogSettingCard("Catálogo ROMgi local", "La copia local evita descargar el índice completo cada vez que necesitas resolver una fuente.") {
                    Text(
                        if (bytes > 0L) "Disponible sin conexión · ${formatBytes(bytes)}" else "Todavía no descargado; Oz lo obtendrá cuando haga falta.",
                        style = OzTypography.body,
                        color = OzColors.TextSecondary,
                    )
                    if (bytes > 0L) {
                        Spacer(Modifier.height(10.dp))
                        CatalogButton("Borrar catálogo local") { romgi.clearCache(); revision++ }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSettingCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(OzColors.PanelRaised, OzShapes.large)
            .border(1.dp, OzColors.Cyan.copy(alpha = .18f), OzShapes.large)
            .padding(16.dp),
    ) {
        Text(title, style = OzTypography.section, color = OzColors.TextPrimary)
        Text(subtitle, style = OzTypography.meta, color = OzColors.TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        content()
    }
}

@Composable
private fun CatalogToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = OzTypography.body, color = OzColors.TextPrimary, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun CatalogChoiceButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = OzTypography.body,
        color = if (selected) OzColors.DeepSpace else OzColors.TextPrimary,
        modifier = Modifier
            .background(
                if (selected) OzColors.Cyan else OzColors.Panel,
                OzShapes.pill,
            )
            .border(
                1.dp,
                if (selected) OzColors.Cyan else OzColors.Cyan.copy(alpha = .35f),
                OzShapes.pill,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun CatalogButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = OzColors.Cyan.copy(alpha = .16f),
            contentColor = OzColors.Cyan,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, OzColors.Cyan.copy(alpha = .42f)),
        shape = OzShapes.pill,
    ) { Text(text, style = OzTypography.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = OzColors.TextPrimary) }
}

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> "%.2f GB".format(value / (1024.0 * 1024.0 * 1024.0))
    value >= 1024L * 1024L -> "%.1f MB".format(value / (1024.0 * 1024.0))
    value >= 1024L -> "%.0f KB".format(value / 1024.0)
    else -> "$value B"
}

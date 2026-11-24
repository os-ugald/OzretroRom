package cl.gakuto.kizuna.ui.compose.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.gakuto.kizuna.data.AppOrientationMode
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.EngineMode
import cl.gakuto.kizuna.data.RomRegionPreference
import cl.gakuto.kizuna.ui.InternetArchiveLoginActivity
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography

/**
 * Console destination 3 ("Ajustes"):
 * Unified Compose settings consolidating 1-tap mDNS PC Hub pairing, RetroArch/Emulators,
 * SAF storage & cover sync, theme/gamepad display, and FTP targets — removing routine jumps
 * to legacy XML Activities.
 */
@Composable
fun HylianSettingsScreen(
    viewModel: OzViewModel,
    onOpenFtpConfig: () -> Unit,
) {
    val context = LocalContext.current
    val discoveredHubs by viewModel.discoveredLanHubs.collectAsStateWithLifecycle()
    val isScanningHubs by viewModel.isScanningLanHubs.collectAsStateWithLifecycle()
    val pcHubUrl by viewModel.pcHubUrl.collectAsStateWithLifecycle()
    val pcHubStatus by viewModel.pcHubStatus.collectAsStateWithLifecycle()
    val sseConnected by viewModel.pcHubSseConnected.collectAsStateWithLifecycle()
    val autoLaunch by viewModel.autoLaunchAfterDownload.collectAsStateWithLifecycle()
    val autoCores by viewModel.retroAutoInstallCores.collectAsStateWithLifecycle()
    val themeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()
    val orientationMode by viewModel.appOrientationMode.collectAsStateWithLifecycle()
    val artworkSyncUi by viewModel.artworkSyncUi.collectAsStateWithLifecycle()
    val ftpTargets by viewModel.ftpTargets.collectAsStateWithLifecycle()

    var manualUrl by remember(pcHubUrl) { mutableStateOf(pcHubUrl) }
    var prefsRev by remember { mutableStateOf(0) }
    prefsRev

    val currentEngine = remember(prefsRev) { viewModel.preferences.engineMode("snes") }
    val selectedRegion = viewModel.preferences.preferredRegion
    val romFolderName = viewModel.preferences.globalRomTreeUri?.lastPathSegment ?: "Carpeta local predeterminada"

    DisposableEffect(Unit) {
        viewModel.startLanHubDiscovery()
        onDispose { viewModel.stopLanHubDiscovery() }
    }

    val romPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.preferences.globalRomTreeUri = uri
            prefsRev += 1
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OzColors.HylianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Ajustes", style = OzTypography.hero, color = OzColors.TextPrimary)
        }

        // 1. PC Hub LAN (1-Tap mDNS)
        item {
            HylianSettingsCard(
                title = "PC Hub LAN (mDNS)",
                subtitle = if (sseConnected) "Conectado en vivo (SSE)" else pcHubStatus.ifBlank { "Empareja tu PC en un toque" },
                accent = OzColors.HylianGold,
            ) {
                if (discoveredHubs.isNotEmpty()) {
                    discoveredHubs.forEach { hub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(hub.serviceName, style = OzTypography.cardTitle, color = OzColors.TextPrimary)
                                Text(hub.baseUrl, style = OzTypography.meta, color = OzColors.HylianEmerald)
                            }
                            Button(
                                onClick = { viewModel.pairDiscoveredHub(hub) },
                                shape = OzShapes.pill,
                                colors = ButtonDefaults.buttonColors(containerColor = OzColors.HylianGold, contentColor = OzColors.HylianBg),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("Emparejar", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                } else {
                    Text(
                        if (isScanningHubs) "Buscando PC Hub vía _ozvault._tcp.local.…" else "Sin servidores mDNS detectados aún.",
                        style = OzTypography.meta,
                        color = OzColors.TextSecondary,
                    )
                }

                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    singleLine = true,
                    label = { Text("URL manual del PC Hub", style = OzTypography.meta) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.testAndSyncPcHub(manualUrl) },
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.HylianEmerald, contentColor = OzColors.TextPrimary),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Conectar y Sincronizar", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
                    }
                    Button(
                        onClick = { viewModel.startLanHubDiscovery() },
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.HylianSurfaceRaised, contentColor = OzColors.TextPrimary),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Buscar LAN", style = OzTypography.meta)
                    }
                }
            }
        }

        // 2. Emulators & RetroArch
        item {
            HylianSettingsCard(
                title = "RetroArch y Emuladores",
                subtitle = "Motor unificado sin salir de Compose",
                accent = OzColors.HylianEmerald,
            ) {
                SettingsToggleRow(
                    title = "Lanzar al terminar descarga",
                    checked = autoLaunch,
                    onCheckedChange = viewModel::setAutoLaunchAfterDownload,
                )
                SettingsToggleRow(
                    title = "Auto-instalar cores de RetroArch",
                    checked = autoCores,
                    onCheckedChange = viewModel::setRetroAutoInstallCores,
                )
                Text("MODO DE MOTOR GLOBAL", style = OzTypography.meta, color = OzColors.TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EngineMode.entries.forEach { mode ->
                        val selected = currentEngine == mode
                        FilterChip(
                            selected = selected,
                            onClick = {
                                viewModel.systems.forEach { sys -> viewModel.preferences.setEngineMode(sys.id, mode) }
                                prefsRev += 1
                            },
                            label = {
                                Text(
                                    when (mode) {
                                        EngineMode.AUTO -> "Auto"
                                        EngineMode.RETROARCH -> "RetroArch"
                                        EngineMode.EXTERNAL -> "Externo"
                                        EngineMode.ASK -> "Preguntar"
                                    },
                                    style = OzTypography.meta,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OzColors.HylianEmerald,
                                selectedLabelColor = OzColors.TextPrimary,
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }

        // 3. Storage & Artwork
        item {
            HylianSettingsCard(
                title = "Almacenamiento y Carátulas",
                subtitle = romFolderName,
                accent = OzColors.HylianEmerald,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { romPicker.launch(viewModel.preferences.globalRomTreeUri) },
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.HylianSurfaceRaised, contentColor = OzColors.TextPrimary),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Elegir carpeta ROMs", style = OzTypography.meta)
                    }
                    Button(
                        onClick = { viewModel.syncAllArtwork() },
                        enabled = !artworkSyncUi.running,
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.HylianEmerald, contentColor = OzColors.TextPrimary),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (artworkSyncUi.running) "${artworkSyncUi.completed}/${artworkSyncUi.total}" else "Sincronizar carátulas",
                            style = OzTypography.meta.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Text("REGIÓN PREFERIDA", style = OzTypography.meta, color = OzColors.TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        RomRegionPreference.USA to "USA",
                        RomRegionPreference.WORLD to "World",
                        RomRegionPreference.EUROPE to "EU",
                        RomRegionPreference.JAPAN to "JP",
                    ).forEach { (reg, lbl) ->
                        FilterChip(
                            selected = selectedRegion == reg.key,
                            onClick = {
                                viewModel.preferences.preferredRegion = reg.key
                                prefsRev += 1
                            },
                            label = { Text(lbl, style = OzTypography.meta) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }

        // 4. Theme, Orientation & FTP
        item {
            HylianSettingsCard(
                title = "Tema, Pantalla y Consolas FTP",
                subtitle = "${ftpTargets.size} destinos FTP · Hylian Dark",
                accent = OzColors.HylianGold,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setAppThemeMode(mode) },
                            label = { Text(mode.label, style = OzTypography.meta) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOrientationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = orientationMode == mode,
                            onClick = { viewModel.setAppOrientationMode(mode) },
                            label = { Text(mode.label, style = OzTypography.meta, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenFtpConfig,
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.HylianSurfaceRaised, contentColor = OzColors.HylianGold),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Configurar FTP (${ftpTargets.size})", style = OzTypography.meta)
                    }
                    Button(
                        onClick = { context.startActivity(Intent(context, InternetArchiveLoginActivity::class.java)) },
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.HylianSurfaceRaised, contentColor = OzColors.TextPrimary),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Internet Archive", style = OzTypography.meta)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun HylianSettingsCard(
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = OzShapes.large,
        color = OzColors.HylianSurface,
        border = BorderStroke(1.dp, OzColors.HylianBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(title, style = OzTypography.section, color = accent)
                Text(subtitle, style = OzTypography.meta, color = OzColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = OzTypography.body,
            color = if (focused) OzColors.HylianGold else OzColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

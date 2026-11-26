package cl.gakuto.kizuna.ui.compose.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.gakuto.kizuna.data.AppOrientationMode
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.AppThemePreset
import cl.gakuto.kizuna.data.EngineMode
import cl.gakuto.kizuna.data.RomRegionPreference
import cl.gakuto.kizuna.ui.InternetArchiveLoginActivity
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.OzChamferShape
import cl.gakuto.kizuna.ui.compose.components.OzHudButton
import cl.gakuto.kizuna.ui.compose.components.OzHudCard
import cl.gakuto.kizuna.ui.compose.components.OzHudChip
import cl.gakuto.kizuna.ui.compose.components.OzHudSectionHeader
import cl.gakuto.kizuna.ui.compose.components.OzHudTag
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzTypography

/**
 * Unified Chamfered Settings Screen:
 * - Appearance: Theme Preset (Cyber Vault / Hylian) + Dark / Light / System mode + Orientation
 * - PC Hub LAN 1-tap mDNS pairing + manual fallback
 * - RetroArch / External Emulator global mode + auto-launch + auto-cores
 * - SAF ROM directory + cover sync + preferred region + FTP & Internet Archive
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
    val themePreset by viewModel.appThemePreset.collectAsStateWithLifecycle()
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
            .background(OzColors.Bg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OzHudSectionHeader(title = "SETTINGS / PERSONALIZATION")
        }

        // 1. Dual Theme System (Cyber Vault / Hylian + Dark / Light / Auto)
        item {
            HudSettingsCard(
                title = "Apariencia y Tema",
                subtitle = "${themePreset.label} · ${themeMode.label}",
                accent = OzColors.AccentPrimary,
            ) {
                Text("PRESET VISUAL", style = OzTypography.meta, color = OzColors.TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemePreset.entries.forEach { preset ->
                        OzHudChip(
                            label = preset.label,
                            selected = themePreset == preset,
                            onClick = { viewModel.setAppThemePreset(preset) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text("MODO DE LUMINOSIDAD", style = OzTypography.meta, color = OzColors.TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        OzHudChip(
                            label = mode.label,
                            selected = themeMode == mode,
                            onClick = { viewModel.setAppThemeMode(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text("ORIENTACIÓN", style = OzTypography.meta, color = OzColors.TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOrientationMode.entries.forEach { mode ->
                        OzHudChip(
                            label = mode.label,
                            selected = orientationMode == mode,
                            onClick = { viewModel.setAppOrientationMode(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 2. PC Hub LAN (1-Tap mDNS)
        item {
            HudSettingsCard(
                title = "PC Hub LAN (mDNS)",
                subtitle = if (sseConnected) "Conectado en vivo (SSE)" else pcHubStatus.ifBlank { "Empareja tu PC en un toque" },
                accent = OzColors.AccentSecondary,
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
                                Text(hub.baseUrl, style = OzTypography.meta, color = OzColors.AccentPrimary)
                            }
                            OzHudButton(
                                text = "Emparejar",
                                primary = true,
                                onClick = { viewModel.pairDiscoveredHub(hub) },
                            )
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
                    shape = OzChamferShape(8.dp),
                    label = { Text("URL manual del PC Hub", style = OzTypography.meta) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OzColors.AccentPrimary,
                        unfocusedBorderColor = OzColors.Border,
                        focusedTextColor = OzColors.TextPrimary,
                        unfocusedTextColor = OzColors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OzHudButton(
                        text = "Conectar",
                        primary = true,
                        onClick = { viewModel.testAndSyncPcHub(manualUrl) },
                        modifier = Modifier.weight(1f),
                    )
                    OzHudButton(
                        text = "Buscar LAN",
                        primary = false,
                        onClick = { viewModel.startLanHubDiscovery() },
                    )
                }
            }
        }

        // 3. Emulators & RetroArch
        item {
            HudSettingsCard(
                title = "RetroArch y Emuladores",
                subtitle = "Motor unificado sin salir de Compose",
                accent = OzColors.AccentPrimary,
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
                        val label = when (mode) {
                            EngineMode.AUTO -> "Auto"
                            EngineMode.RETROARCH -> "RetroArch"
                            EngineMode.EXTERNAL -> "Externo"
                            EngineMode.ASK -> "Preguntar"
                        }
                        OzHudChip(
                            label = label,
                            selected = currentEngine == mode,
                            onClick = {
                                viewModel.systems.forEach { sys -> viewModel.preferences.setEngineMode(sys.id, mode) }
                                prefsRev += 1
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 4. Storage, Artwork & FTP
        item {
            HudSettingsCard(
                title = "Almacenamiento, Carátulas y FTP",
                subtitle = romFolderName,
                accent = OzColors.AccentSecondary,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OzHudButton(
                        text = "Carpeta ROMs",
                        primary = false,
                        onClick = { romPicker.launch(viewModel.preferences.globalRomTreeUri) },
                        modifier = Modifier.weight(1f),
                    )
                    OzHudButton(
                        text = if (artworkSyncUi.running) "${artworkSyncUi.completed}/${artworkSyncUi.total}" else "Sincronizar arte",
                        primary = true,
                        enabled = !artworkSyncUi.running,
                        onClick = { viewModel.syncAllArtwork() },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("REGIÓN PREFERIDA", style = OzTypography.meta, color = OzColors.TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        RomRegionPreference.USA to "USA",
                        RomRegionPreference.WORLD to "World",
                        RomRegionPreference.EUROPE to "EU",
                        RomRegionPreference.JAPAN to "JP",
                    ).forEach { (reg, lbl) ->
                        OzHudChip(
                            label = lbl,
                            selected = selectedRegion == reg.key,
                            onClick = {
                                viewModel.preferences.preferredRegion = reg.key
                                prefsRev += 1
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OzHudButton(
                        text = "FTP (${ftpTargets.size})",
                        primary = false,
                        onClick = onOpenFtpConfig,
                        modifier = Modifier.weight(1f),
                    )
                    OzHudButton(
                        text = "Internet Archive",
                        primary = false,
                        onClick = { context.startActivity(Intent(context, InternetArchiveLoginActivity::class.java)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HudSettingsCard(
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    OzHudCard(cut = 12.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title.uppercase(), style = OzTypography.section, color = OzColors.TextPrimary)
                    Text(subtitle, style = OzTypography.body.copy(fontSize = 12.5.sp), color = OzColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OzHudTag(text = "HUD", accent = accent)
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
    val shape = remember { OzChamferShape(6.dp) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .border(1.dp, if (focused) OzColors.AccentPrimary else Color.Transparent, shape)
            .focusable()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = OzTypography.body,
            color = if (focused) OzColors.AccentPrimary else OzColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OzColors.TextPrimary,
                checkedTrackColor = OzColors.AccentPrimary,
            ),
        )
    }
}

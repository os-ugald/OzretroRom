package cl.gakuto.kizuna.ui.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.ftp.FtpTargetConfig
import cl.gakuto.kizuna.ftp.FtpTransferJob
import cl.gakuto.kizuna.ftp.FtpTransferState
import cl.gakuto.kizuna.hub.GameVaultHubClient
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.ConsoleBadge
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import kotlinx.coroutines.launch

@Composable
fun FtpSendDialog(
    game: GameEntity,
    viewModel: OzViewModel,
    usePatched: Boolean,
    onDismiss: () -> Unit,
) {
    val targets by viewModel.ftpTargets.collectAsState()
    val jobs by viewModel.ftpJobs.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = OzShapes.hero,
            color = OzColors.Panel,
            border = BorderStroke(1.dp, OzColors.Gold.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 580.dp)
                .padding(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enviar por FTP (segundo plano)", style = OzTypography.section, color = OzColors.Gold)
                        Text(
                            game.title,
                            style = OzTypography.meta,
                            color = OzColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "✕",
                        style = OzTypography.section,
                        color = OzColors.TextSecondary,
                        modifier = Modifier
                            .clip(OzShapes.pill)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(targets, key = { it.id }) { target ->
                        Surface(
                            shape = OzShapes.medium,
                            color = OzColors.PanelRaised,
                            border = BorderStroke(1.dp, OzColors.Outline.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(target.name, style = OzTypography.tileTitle, color = OzColors.TextPrimary)
                                    Text(
                                        "${target.host}:${target.port} → ${target.resolveRemoteDir(game.systemId)}",
                                        style = OzTypography.meta,
                                        color = OzColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { viewModel.sendGameViaFtp(game, target, usePatched) },
                                    shape = OzShapes.pill,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OzColors.Gold,
                                        contentColor = OzColors.DeepSpace,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("Enviar", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    if (jobs.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text("Cola en segundo plano", style = OzTypography.cardTitle, color = OzColors.Cyan)
                        }
                        items(jobs.take(5), key = { it.jobId }) { job ->
                            FtpJobProgressRow(job)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FtpJobProgressRow(job: FtpTransferJob) {
    val color = when (job.state) {
        FtpTransferState.COMPLETE -> OzColors.Positive
        FtpTransferState.ERROR -> OzColors.Error
        FtpTransferState.UPLOADING -> OzColors.Gold
        FtpTransferState.QUEUED -> OzColors.Cyan
    }
    Surface(
        shape = OzShapes.medium,
        color = OzColors.DeepSpace.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${job.title} → ${job.targetName}",
                    style = OzTypography.meta,
                    color = OzColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("${job.progressPct}%", style = OzTypography.meta, color = color)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (job.progressPct / 100f).coerceIn(0f, 1f) },
                color = color,
                trackColor = OzColors.PanelRaised,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(OzShapes.pill),
            )
            Spacer(Modifier.height(3.dp))
            Text(job.message, style = OzTypography.meta, color = OzColors.TextSecondary)
        }
    }
}

@Composable
fun FtpConfigModal(
    viewModel: OzViewModel,
    onDismiss: () -> Unit,
) {
    val savedTargets by viewModel.ftpTargets.collectAsState()
    var editable by remember(savedTargets) { mutableStateOf(savedTargets) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = OzShapes.hero,
            color = OzColors.Panel,
            border = BorderStroke(1.dp, OzColors.Gold.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
                .padding(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Consolas FTP (segundo plano)", style = OzTypography.section, color = OzColors.Gold)
                        Text("PS4 EmuSuNi · PS3 webMAN · Switch ftpd/DBI", style = OzTypography.meta, color = OzColors.TextSecondary)
                    }
                    Text(
                        "✕",
                        style = OzTypography.section,
                        color = OzColors.TextSecondary,
                        modifier = Modifier
                            .clip(OzShapes.pill)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(editable.size) { index ->
                        val item = editable[index]
                        val updateItem: (FtpTargetConfig) -> Unit = { updated ->
                            editable = editable.toMutableList().also { it[index] = updated }
                        }
                        Surface(
                            shape = OzShapes.large,
                            color = OzColors.PanelRaised,
                            border = BorderStroke(1.dp, OzColors.Outline.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(item.name, style = OzTypography.cardTitle, color = OzColors.TextPrimary)
                                    Button(
                                        onClick = { viewModel.testFtpTarget(item) },
                                        shape = OzShapes.pill,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = OzColors.Cyan.copy(alpha = 0.16f),
                                            contentColor = OzColors.Cyan,
                                        ),
                                        border = BorderStroke(1.dp, OzColors.Cyan.copy(alpha = 0.45f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Text("Probar", style = OzTypography.meta)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ModalTextField(
                                        value = item.host,
                                        onValueChange = { updateItem(item.copy(host = it)) },
                                        label = "IP / Host",
                                        modifier = Modifier.weight(0.68f),
                                    )
                                    ModalTextField(
                                        value = item.port.toString(),
                                        onValueChange = { v ->
                                            val parsed = v.filter { it.isDigit() }.toIntOrNull()
                                            if (parsed != null) updateItem(item.copy(port = parsed.coerceIn(1, 65535)))
                                        },
                                        label = "Puerto",
                                        keyboardType = KeyboardType.Number,
                                        modifier = Modifier.weight(0.32f),
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ModalTextField(
                                        value = item.username,
                                        onValueChange = { updateItem(item.copy(username = it)) },
                                        label = "Usuario",
                                        modifier = Modifier.weight(0.5f),
                                    )
                                    ModalTextField(
                                        value = item.password,
                                        onValueChange = { updateItem(item.copy(password = it)) },
                                        label = "Clave",
                                        modifier = Modifier.weight(0.5f),
                                    )
                                }
                                ModalTextField(
                                    value = item.basePath,
                                    onValueChange = { updateItem(item.copy(basePath = it)) },
                                    label = "Carpeta base ROMs",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.PanelRaised, contentColor = OzColors.TextSecondary),
                    ) {
                        Text("Cancelar", style = OzTypography.meta)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.saveFtpTargets(editable)
                            onDismiss()
                        },
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.Gold, contentColor = OzColors.DeepSpace),
                    ) {
                        Text("Guardar cambios", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun PcHubModal(
    viewModel: OzViewModel,
    onDismiss: () -> Unit,
    onOpenCatalogQuery: (String) -> Unit,
) {
    val savedUrl by viewModel.pcHubUrl.collectAsState()
    val status by viewModel.pcHubStatus.collectAsState()
    val games by viewModel.pcHubGames.collectAsState()
    val discoveredHubs by viewModel.discoveredLanHubs.collectAsState()
    val isScanningLan by viewModel.isScanningLanHubs.collectAsState()
    val sseConnected by viewModel.pcHubSseConnected.collectAsState()
    val ftpTargets by viewModel.ftpTargets.collectAsState()
    var urlInput by remember(savedUrl) { mutableStateOf(savedUrl.ifBlank { "http://192.168.100.173:17894" }) }
    var remoteFtpMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        viewModel.startLanHubDiscovery()
        onDispose { viewModel.stopLanHubDiscovery() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = OzShapes.hero,
            color = OzColors.Panel,
            border = BorderStroke(1.dp, OzColors.Cyan.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 650.dp)
                .padding(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Game Vault PC Hub (LAN)", style = OzTypography.section, color = OzColors.Cyan)
                        Text(
                            "Conéctate opcionalmente al servidor de tu PC dentro de tu Wi-Fi local (RFC-1918).",
                            style = OzTypography.meta,
                            color = OzColors.TextSecondary,
                        )
                    }
                    Text(
                        "✕",
                        style = OzTypography.section,
                        color = OzColors.TextSecondary,
                        modifier = Modifier
                            .clip(OzShapes.pill)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                ModalTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = "URL LAN (ej. http://192.168.100.173:17894)",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.testAndSyncPcHub(urlInput) },
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.Cyan, contentColor = OzColors.DeepSpace),
                    ) {
                        Text("Conectar y sincronizar", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
                    }
                    Button(
                        onClick = { viewModel.savePcHubUrl(urlInput) },
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.PanelRaised, contentColor = OzColors.TextPrimary),
                    ) {
                        Text("Guardar URL", style = OzTypography.meta)
                    }
                }

                if (isScanningLan || discoveredHubs.isNotEmpty() || sseConnected) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (discoveredHubs.isNotEmpty()) "PC Hubs detectados en LAN (mDNS · 1 toque)"
                            else "Buscando PC Hubs en tu red local (_ozvault._tcp)…",
                            style = OzTypography.meta,
                            color = OzColors.Cyan,
                        )
                        if (sseConnected) {
                            Text("● SSE activo", style = OzTypography.meta, color = OzColors.Positive)
                        }
                    }
                    if (discoveredHubs.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            discoveredHubs.take(3).forEach { hub ->
                                Text(
                                    "⚡ ${hub.serviceName} (${hub.host}:${hub.port})",
                                    style = OzTypography.meta,
                                    color = OzColors.Cyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(OzShapes.pill)
                                        .background(OzColors.DeepSpace)
                                        .border(1.dp, OzColors.Cyan.copy(alpha = 0.55f), OzShapes.pill)
                                        .clickable {
                                            urlInput = hub.baseUrl
                                            viewModel.pairDiscoveredHub(hub)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                if (status.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(status, style = OzTypography.meta, color = OzColors.Gold)
                }
                if (!remoteFtpMsg.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(remoteFtpMsg!!, style = OzTypography.meta, color = OzColors.Positive)
                }

                Spacer(Modifier.height(10.dp))

                if (games.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(games.take(60), key = { it.entryId }) { remote ->
                            Surface(
                                shape = OzShapes.medium,
                                color = OzColors.PanelRaised,
                                border = BorderStroke(1.dp, OzColors.Outline.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            remote.cleanTitle,
                                            style = OzTypography.tileTitle,
                                            color = OzColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            ConsoleBadge(systemId = remote.consoleKey)
                                            if (remote.hasSpanishPatch) {
                                                Text("[ES]", style = OzTypography.meta, color = OzColors.Gold)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    val firstTarget = ftpTargets.firstOrNull()
                                    if (firstTarget != null) {
                                        Text(
                                            "PC→FTP",
                                            style = OzTypography.meta,
                                            color = OzColors.Gold,
                                            modifier = Modifier
                                                .clip(OzShapes.pill)
                                                .background(OzColors.DeepSpace)
                                                .border(1.dp, OzColors.Gold.copy(alpha = 0.45f), OzShapes.pill)
                                                .clickable {
                                                    scope.launch {
                                                        remoteFtpMsg = runCatching {
                                                            GameVaultHubClient.enqueueRemoteFtp(urlInput, remote.entryId, firstTarget.id)
                                                            "Enviado desde PC a ${firstTarget.name}"
                                                        }.getOrElse { it.message ?: "Error al encolar FTP en PC" }
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        "Buscar",
                                        style = OzTypography.meta,
                                        color = OzColors.Cyan,
                                        modifier = Modifier
                                            .clip(OzShapes.pill)
                                            .background(OzColors.DeepSpace)
                                            .border(1.dp, OzColors.Cyan.copy(alpha = 0.45f), OzShapes.pill)
                                            .clickable {
                                                onDismiss()
                                                onOpenCatalogQuery(remote.cleanTitle)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = OzTypography.meta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = OzShapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OzColors.Cyan,
            unfocusedBorderColor = OzColors.Outline.copy(alpha = 0.4f),
            focusedContainerColor = OzColors.DeepSpace.copy(alpha = 0.6f),
            unfocusedContainerColor = OzColors.DeepSpace.copy(alpha = 0.4f),
            focusedTextColor = OzColors.TextPrimary,
            unfocusedTextColor = OzColors.TextPrimary,
        ),
        modifier = modifier,
    )
}

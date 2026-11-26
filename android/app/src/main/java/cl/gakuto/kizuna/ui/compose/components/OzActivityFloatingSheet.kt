package cl.gakuto.kizuna.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.gakuto.kizuna.ftp.FtpTransferJob
import cl.gakuto.kizuna.ftp.FtpTransferState
import cl.gakuto.kizuna.ui.compose.CatalogPreparationUi
import cl.gakuto.kizuna.ui.compose.CollectionArtworkSyncUi
import cl.gakuto.kizuna.ui.compose.CollectionDownloadUi
import cl.gakuto.kizuna.ui.compose.domain.HubSseActivity
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography

/**
 * Chamfered floating activity bar rendered ONLY when downloads, cover syncs,
 * translations, catalog indexing, or FTP transfers are actively running (0dp when idle).
 */
@Composable
fun OzActivityFloatingSheet(
    downloadUi: CollectionDownloadUi,
    artworkSyncUi: CollectionArtworkSyncUi,
    ftpJobs: List<FtpTransferJob>,
    isApplyingPatch: Boolean,
    catalogPreparation: CatalogPreparationUi,
    pcHubSseConnected: Boolean,
    latestHubEvent: HubSseActivity?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenFtpModal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeFtp = remember(ftpJobs) {
        ftpJobs.filter { it.state == FtpTransferState.QUEUED || it.state == FtpTransferState.UPLOADING }
    }
    val hasActiveWork = downloadUi.running ||
        artworkSyncUi.running ||
        activeFtp.isNotEmpty() ||
        isApplyingPatch ||
        catalogPreparation.running

    if (!hasActiveWork) return

    val summaryText = when {
        downloadUi.running -> "DESCARGANDO: ${downloadUi.gameTitle} (${downloadUi.completed}/${downloadUi.total})"
        artworkSyncUi.running -> "CARÁTULAS: ${artworkSyncUi.gameTitle} (${artworkSyncUi.completed}/${artworkSyncUi.total})"
        isApplyingPatch -> "APLICANDO PARCHE [ES]…"
        activeFtp.isNotEmpty() -> {
            val first = activeFtp.first()
            "FTP → ${first.targetName}: ${first.title} (${first.progressPct}%)"
        }
        else -> "INDEXANDO: ${catalogPreparation.currentSystem} (${catalogPreparation.readySystems}/${catalogPreparation.totalSystems})"
    }

    var focused by remember { mutableStateOf(false) }
    val shape = remember { OzChamferShape(8.dp) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = shape,
        color = OzColors.Surface.copy(alpha = 0.96f),
        border = BorderStroke(
            width = if (focused) 1.5.dp else 1.dp,
            color = if (focused) OzColors.AccentSecondary else OzColors.AccentPrimary,
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(OzColors.AccentPrimary),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = summaryText,
                        style = OzTypography.meta.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                        color = OzColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (expanded) "▼" else "LIVE ▲",
                    style = OzTypography.hudTag,
                    color = OzColors.AccentPrimary,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, OzColors.Border)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (downloadUi.running) {
                        val prog = if (downloadUi.total > 0) downloadUi.completed.toFloat() / downloadUi.total else 0f
                        ActivityProgressRow(
                            title = "Descarga: ${downloadUi.gameTitle}",
                            subtitle = "${downloadUi.stage} · ${downloadUi.completed}/${downloadUi.total}",
                            progress = prog,
                            accent = OzColors.AccentPrimary,
                        )
                    }
                    if (artworkSyncUi.running) {
                        val prog = if (artworkSyncUi.total > 0) artworkSyncUi.completed.toFloat() / artworkSyncUi.total else 0f
                        ActivityProgressRow(
                            title = "Carátulas: ${artworkSyncUi.gameTitle}",
                            subtitle = "${artworkSyncUi.resolved} listas · ${artworkSyncUi.completed}/${artworkSyncUi.total}",
                            progress = prog,
                            accent = OzColors.AccentSecondary,
                        )
                    }
                    if (isApplyingPatch) {
                        Text("Aplicando parche [ES]…", style = OzTypography.meta, color = OzColors.AccentPrimary)
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().clip(OzShapes.tiny),
                            color = OzColors.AccentPrimary,
                            trackColor = OzColors.Bg,
                        )
                    }
                    if (catalogPreparation.running) {
                        val prog = if (catalogPreparation.totalSystems > 0) {
                            catalogPreparation.readySystems.toFloat() / catalogPreparation.totalSystems
                        } else 0f
                        ActivityProgressRow(
                            title = "Catálogo: ${catalogPreparation.currentSystem}",
                            subtitle = "${catalogPreparation.readySystems}/${catalogPreparation.totalSystems} sistemas",
                            progress = prog,
                            accent = OzColors.AccentPrimary,
                        )
                    }
                    activeFtp.take(3).forEach { job ->
                        ActivityProgressRow(
                            title = "FTP · ${job.title} → ${job.targetName}",
                            subtitle = "${job.message} (${job.progressPct}%)",
                            progress = (job.progressPct / 100f).coerceIn(0f, 1f),
                            accent = OzColors.AccentSecondary,
                        )
                    }
                    if (latestHubEvent != null && pcHubSseConnected) {
                        Text(
                            text = "SSE (${latestHubEvent.event}): ${latestHubEvent.summary}",
                            style = OzTypography.meta,
                            color = OzColors.AccentPrimary,
                        )
                    }
                    if (ftpJobs.isNotEmpty()) {
                        Text(
                            text = "ABRIR MONITOR FTP ›",
                            style = OzTypography.hudTag,
                            color = OzColors.AccentPrimary,
                            modifier = Modifier
                                .clickable(onClick = onOpenFtpModal)
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityProgressRow(
    title: String,
    subtitle: String,
    progress: Float,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = OzTypography.meta, color = OzColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(OzShapes.tiny),
            color = accent,
            trackColor = OzColors.Bg,
        )
        Text(subtitle, style = OzTypography.body.copy(fontSize = 11.sp), color = OzColors.TextSecondary, maxLines = 1)
    }
}

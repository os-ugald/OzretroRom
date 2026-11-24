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
 * Persistent collapsible activity mini-player / bottom sheet docked over the bottom navigation bar.
 * Streams live StateFlow + PC Hub SSE state without interrupting navigation.
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
    val hasActiveWork = downloadUi.running || artworkSyncUi.running || activeFtp.isNotEmpty() || isApplyingPatch || catalogPreparation.running

    val summaryText = when {
        downloadUi.running -> "Descargando: ${downloadUi.gameTitle} (${downloadUi.completed}/${downloadUi.total})"
        artworkSyncUi.running -> "Carátulas: ${artworkSyncUi.gameTitle} (${artworkSyncUi.completed}/${artworkSyncUi.total})"
        isApplyingPatch -> "Aplicando parche de traducción [ES]…"
        activeFtp.isNotEmpty() -> {
            val first = activeFtp.first()
            "FTP → ${first.targetName}: ${first.title} (${first.progressPct}%)"
        }
        catalogPreparation.running -> "Indexando catálogo: ${catalogPreparation.currentSystem} (${catalogPreparation.readySystems}/${catalogPreparation.totalSystems})"
        latestHubEvent != null -> latestHubEvent.summary
        pcHubSseConnected -> "SSE activo · PC Hub en línea"
        else -> "Actividad en segundo plano"
    }

    var focused by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = OzShapes.large,
        color = OzColors.HylianSurface.copy(alpha = 0.96f),
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) OzColors.HylianGold else if (hasActiveWork) OzColors.HylianEmerald else OzColors.HylianBorder,
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
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    hasActiveWork -> OzColors.HylianGold
                                    pcHubSseConnected -> OzColors.HylianEmerald
                                    else -> OzColors.TextSecondary
                                },
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = summaryText,
                        style = OzTypography.meta.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = OzColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (expanded) "▼" else if (hasActiveWork) "LIVE ▲" else "SSE ▲",
                    style = OzTypography.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = if (hasActiveWork) OzColors.HylianGold else OzColors.HylianEmerald,
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
                        .border(0.5.dp, OzColors.HylianBorder)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!hasActiveWork && latestHubEvent == null && ftpJobs.isEmpty()) {
                        Text(
                            text = "Sin descargas, parches [ES] ni envíos FTP activos. Las operaciones correrán aquí sin interrumpir tu navegación.",
                            style = OzTypography.body.copy(fontSize = 12.5.sp),
                            color = OzColors.TextSecondary,
                        )
                    }
                    if (downloadUi.running) {
                        val prog = if (downloadUi.total > 0) downloadUi.completed.toFloat() / downloadUi.total else 0f
                        ActivityProgressRow(
                            title = "Descarga: ${downloadUi.gameTitle}",
                            subtitle = "${downloadUi.stage} · ${downloadUi.completed}/${downloadUi.total}",
                            progress = prog,
                            accent = OzColors.HylianGold,
                        )
                    }
                    if (artworkSyncUi.running) {
                        val prog = if (artworkSyncUi.total > 0) artworkSyncUi.completed.toFloat() / artworkSyncUi.total else 0f
                        ActivityProgressRow(
                            title = "Carátulas: ${artworkSyncUi.gameTitle}",
                            subtitle = "${artworkSyncUi.resolved} listas · ${artworkSyncUi.completed}/${artworkSyncUi.total}",
                            progress = prog,
                            accent = OzColors.HylianEmerald,
                        )
                    }
                    if (isApplyingPatch) {
                        Text("Aplicando parche [ES]…", style = OzTypography.meta, color = OzColors.HylianGold)
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = OzColors.HylianGold,
                            trackColor = OzColors.HylianBg,
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
                            accent = OzColors.HylianEmerald,
                        )
                    }
                    activeFtp.take(3).forEach { job ->
                        ActivityProgressRow(
                            title = "FTP · ${job.title} → ${job.targetName}",
                            subtitle = "${job.message} (${job.progressPct}%)",
                            progress = (job.progressPct / 100f).coerceIn(0f, 1f),
                            accent = OzColors.HylianGold,
                        )
                    }
                    if (latestHubEvent != null) {
                        Text(
                            text = "SSE PC Hub (${latestHubEvent.event}): ${latestHubEvent.summary}",
                            style = OzTypography.meta,
                            color = OzColors.HylianEmerald,
                        )
                    }
                    if (ftpJobs.isNotEmpty()) {
                        Text(
                            text = "Abrir monitor FTP completo ›",
                            style = OzTypography.meta.copy(fontWeight = FontWeight.Bold),
                            color = OzColors.HylianGold,
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
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = OzTypography.meta, color = OzColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(OzShapes.pill),
            color = accent,
            trackColor = OzColors.HylianBg,
        )
        Text(subtitle, style = OzTypography.meta.copy(fontSize = 10.sp), color = OzColors.TextSecondary, maxLines = 1)
    }
}

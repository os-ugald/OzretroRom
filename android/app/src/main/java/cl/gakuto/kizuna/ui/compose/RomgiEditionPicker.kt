package cl.gakuto.kizuna.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.gakuto.kizuna.catalog.RomgiDownloadPlan
import cl.gakuto.kizuna.catalog.RomgiEditionResolution
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import coil3.compose.AsyncImage

@Composable
fun RomgiEditionPicker(
    resolution: RomgiEditionResolution,
    onConfirm: (RomgiDownloadPlan) -> Unit,
    onCancel: () -> Unit,
) {
    var showAdvanced by remember { mutableStateOf(resolution.standard.isEmpty()) }
    var selectedId by remember { mutableStateOf(resolution.recommended.id) }
    val initialFocusRequester = remember { FocusRequester() }
    val visiblePlans = if (showAdvanced) resolution.standard + resolution.advanced else resolution.standard
    val selected = resolution.allPlans.firstOrNull { it.id == selectedId } ?: resolution.recommended

    LaunchedEffect(resolution.recommended.id) {
        initialFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(OzColors.DeepSpace, OzColors.PurpleDeep, OzColors.DeepSpace),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text("OZ // ROMGI", style = OzTypography.meta, color = OzColors.Cyan)
        Text("Elige la edición", style = OzTypography.hero, color = OzColors.WarmIvory)
        Text(
            "Cada opción es un conjunto completo. Región, revisión y formato se conservan como ediciones separadas.",
            style = OzTypography.body,
            color = OzColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(visiblePlans, key = { it.id }) { plan ->
                EditionCard(
                    plan = plan,
                    selected = plan.id == selectedId,
                    recommended = plan.id == resolution.recommended.id,
                    modifier = if (plan.id == resolution.recommended.id) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else {
                        Modifier
                    },
                    onClick = { selectedId = plan.id },
                )
            }
            if (resolution.advanced.isNotEmpty() && resolution.standard.isNotEmpty()) {
                item(key = "advanced-toggle") {
                    OutlinedButton(
                        onClick = {
                            if (showAdvanced && resolution.advanced.any { it.id == selectedId }) {
                                selectedId = resolution.recommended.id
                            }
                            showAdvanced = !showAdvanced
                        },
                        modifier = Modifier.fillMaxWidth().focusable(),
                    ) {
                        Text(if (showAdvanced) "Ocultar demos y prototipos" else "Versiones avanzadas (${resolution.advanced.size})")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).focusable()) {
                Text("Cancelar")
            }
            Button(
                onClick = { onConfirm(selected) },
                modifier = Modifier.weight(1.5f).focusable(),
                shape = OzShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = OzColors.Cyan, contentColor = OzColors.DeepSpace),
            ) {
                Text(if (selected.isMultiDisc) "Descargar juego completo" else "Descargar")
            }
        }
    }
}

@Composable
private fun EditionCard(
    plan: RomgiDownloadPlan,
    selected: Boolean,
    recommended: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val outline = if (selected) OzColors.Cyan else OzColors.Outline.copy(alpha = .62f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(OzShapes.medium)
            .background(if (selected) OzColors.ElectricBlue.copy(alpha = .16f) else OzColors.PanelRaised)
            .border(if (selected) 2.dp else 1.dp, outline, OzShapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .focusable()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!plan.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = plan.artworkUrl,
                contentDescription = "Carátula de ${plan.parentTitle}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 58.dp, height = 78.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OzColors.Panel),
            )
            Spacer(Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    plan.editionTitle,
                    style = OzTypography.cardTitle,
                    color = OzColors.TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (recommended) {
                    Text("RECOMENDADA", style = OzTypography.meta, color = OzColors.Positive)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${plan.region} · ${plan.releaseKind.label} · ${plan.format}",
                style = OzTypography.meta,
                color = if (plan.requiresRegionConfirmation) OzColors.Warning else OzColors.Cyan,
            )
            Text(
                if (plan.isMultiDisc) "${plan.discCount} discos · conjunto completo" else "1 archivo",
                style = OzTypography.body,
                color = OzColors.TextSecondary,
            )
            Text(
                "${formatBytes(plan.totalSize)} · ${if (plan.usesTorrent) "torrent selectivo" else "HTTPS"}",
                style = OzTypography.meta,
                color = OzColors.TextSecondary,
            )
        }
        if (selected) {
            Box(
                Modifier
                    .padding(start = 10.dp)
                    .size(18.dp)
                    .background(OzColors.Cyan, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = OzColors.DeepSpace, fontWeight = FontWeight.Black)
            }
        }
    }
}

private fun formatBytes(value: Long): String {
    if (value <= 0L) return "Tamaño pendiente"
    val units = arrayOf("B", "KB", "MB", "GB")
    var amount = value.toDouble()
    var unit = 0
    while (amount >= 1024.0 && unit < units.lastIndex) {
        amount /= 1024.0
        unit += 1
    }
    return if (unit == 0) "$value ${units[unit]}" else String.format(java.util.Locale.US, "%.1f %s", amount, units[unit])
}

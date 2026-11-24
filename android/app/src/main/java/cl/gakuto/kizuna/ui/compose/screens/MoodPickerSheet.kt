package cl.gakuto.kizuna.ui.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cl.gakuto.kizuna.catalog.GameDescriptions
import cl.gakuto.kizuna.catalog.GameMood
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.ConsoleBadge
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodPickerDialog(
    visible: Boolean,
    viewModel: OzViewModel,
    onDismiss: () -> Unit,
    onPlayGame: (GameEntity) -> Unit,
    onOpenGameDetail: (String) -> Unit,
) {
    if (!visible) return

    var selectedMood by remember { mutableStateOf(GameMood.QUICK) }
    var rollOffset by remember { mutableIntStateOf(0) }
    val picks by viewModel.moodPicks.collectAsState()

    LaunchedEffect(visible, selectedMood, rollOffset) {
        if (visible) {
            viewModel.rollMoodPicks(selectedMood, rollOffset)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val isWide = maxWidth >= 680.dp
            Surface(
                shape = OzShapes.hero,
                color = OzColors.Panel,
                border = BorderStroke(1.5.dp, OzColors.Cyan.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth(if (isWide) 0.86f else 1f)
                    .heightIn(max = 620.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("¿Qué juego hoy?", style = OzTypography.hero, color = OzColors.TextPrimary)
                            Text(
                                "Recomendaciones rápidas según tu humor y bóveda local.",
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
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GameMood.entries.forEach { mood ->
                            val selected = mood == selectedMood
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedMood = mood
                                    rollOffset = 0
                                },
                                label = {
                                    Text("${mood.badge} ${mood.label}", style = OzTypography.meta)
                                },
                                shape = OzShapes.pill,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OzColors.Cyan.copy(alpha = 0.22f),
                                    selectedLabelColor = OzColors.Cyan,
                                    containerColor = OzColors.PanelRaised,
                                    labelColor = OzColors.TextSecondary,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = OzColors.Outline.copy(alpha = 0.35f),
                                    selectedBorderColor = OzColors.Cyan,
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Modo: ${selectedMood.durationHint}",
                            style = OzTypography.meta,
                            color = OzColors.Gold,
                        )
                        Button(
                            onClick = { rollOffset += 1 },
                            shape = OzShapes.pill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OzColors.Gold.copy(alpha = 0.16f),
                                contentColor = OzColors.Gold,
                            ),
                            border = BorderStroke(1.dp, OzColors.Gold.copy(alpha = 0.55f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text("🎲 Otro giro", style = OzTypography.meta)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (picks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Descarga o prepara el catálogo para obtener sugerencias.",
                                style = OzTypography.body,
                                color = OzColors.TextSecondary,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(picks, key = { it.id }) { game ->
                                MoodPickCard(
                                    game = game,
                                    viewModel = viewModel,
                                    onPlay = {
                                        onDismiss()
                                        onPlayGame(game)
                                    },
                                    onOpenDetail = {
                                        onDismiss()
                                        onOpenGameDetail(game.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodPickCard(
    game: GameEntity,
    viewModel: OzViewModel,
    onPlay: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val context = LocalContext.current
    val console = remember(game.systemId) { ConsolePalette.forSystem(game.systemId) }
    val artwork = remember(game.id, game.title, game.thumbnailRepo) {
        ArtworkResolver.candidates(context, viewModel.preferences, game).firstOrNull()
    }
    val synopsisPreview = remember(game.id, game.title) {
        GameDescriptions.lookupCurated(game.title) ?: GameDescriptions.synthesizeFallback(game)
    }
    val isLocal = !game.contentUri.isNullOrBlank()

    Surface(
        shape = OzShapes.large,
        color = OzColors.PanelRaised,
        border = BorderStroke(1.dp, console.accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(OzShapes.large)
            .clickable(onClick = onOpenDetail),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 78.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OzColors.DeepSpace)
                    .border(1.dp, console.accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
            ) {
                if (!artwork.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(artwork).build(),
                        contentDescription = game.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    game.title,
                    style = OzTypography.cardTitle,
                    color = OzColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ConsoleBadge(systemId = game.systemId)
                    Text(
                        if (isLocal) "Listo local" else "Descarga + RetroArch",
                        style = OzTypography.meta,
                        color = if (isLocal) OzColors.Positive else OzColors.Cyan,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    synopsisPreview,
                    style = OzTypography.meta,
                    color = OzColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPlay,
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OzColors.Positive,
                            contentColor = OzColors.DeepSpace,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text("▶ Jugar ahora", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
                    }
                    Button(
                        onClick = onOpenDetail,
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OzColors.Panel,
                            contentColor = OzColors.TextPrimary,
                        ),
                        border = BorderStroke(1.dp, OzColors.Outline.copy(alpha = 0.45f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Ficha", style = OzTypography.meta)
                    }
                }
            }
        }
    }
}

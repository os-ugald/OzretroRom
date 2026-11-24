package cl.gakuto.kizuna.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import coil3.compose.AsyncImage

@Composable
fun OzArtworkPicker(
    game: GameEntity,
    viewModel: OzViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val candidates by viewModel.artworkCandidates.collectAsStateWithLifecycle()
    val loading by viewModel.isFetchingArtwork.collectAsStateWithLifecycle()
    var selectedStyle by remember { mutableStateOf("grids") }

    LaunchedEffect(visible, selectedStyle) {
        if (visible) {
            viewModel.fetchArtworkCandidates(game, selectedStyle)
        }
    }

    AnimatedVisibility(visible = visible, modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .background(OzColors.DeepSpace.copy(alpha = 0.95f))
                .padding(24.dp)
        ) {
            Text(
                "SELECCIONAR ARTWORK",
                style = OzTypography.section,
                color = OzColors.TextPrimary
            )
            Text(
                game.title.uppercase(),
                style = OzTypography.meta,
                color = OzColors.Cyan
            )

            Spacer(Modifier.height(16.dp))

            // Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StyleTab("CARÁTULAS", selectedStyle == "grids") { selectedStyle = "grids" }
                StyleTab("ICONOS", selectedStyle == "icons") { selectedStyle = "icons" }
                StyleTab("HEROES", selectedStyle == "heroes") { selectedStyle = "heroes" }
            }

            Spacer(Modifier.height(16.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                        color = OzColors.Cyan
                    )
                } else if (candidates.isEmpty()) {
                    Text(
                        "No se encontraron alternativas para este juego.",
                        style = OzTypography.body,
                        color = OzColors.TextSecondary,
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (selectedStyle == "icons") 100.dp else 140.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(candidates) { asset ->
                            ArtworkCandidateTile(asset) {
                                viewModel.setManualArtwork(game.id, asset.url)
                                onDismiss()
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "B · VOLVER",
                    style = OzTypography.meta,
                    color = OzColors.Cyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(8.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "RESTAURAR AUTOMÁTICO",
                    style = OzTypography.meta,
                    color = OzColors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.restoreAutomaticArtwork(game.id)
                            onDismiss()
                        }
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun StyleTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) OzColors.Cyan.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) OzColors.Cyan else OzColors.Outline.copy(alpha = 0.3f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = OzTypography.meta,
            color = if (selected) OzColors.Cyan else OzColors.TextSecondary
        )
    }
}

@Composable
private fun ArtworkCandidateTile(
    asset: cl.gakuto.kizuna.launch.SteamGridDbClient.Asset,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "oz-art-tile-scale")

    Box(
        Modifier
            .scale(scale)
            .aspectRatio(if (asset.style == "icons") 1f else if (asset.style == "heroes") 2.5f else 0.7f)
            .clip(OzShapes.medium)
            .background(OzColors.PanelRaised)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) OzColors.Cyan else OzColors.Outline.copy(alpha = 0.2f),
                OzShapes.medium
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
    ) {
        AsyncImage(
            model = asset.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(OzColors.Cyan.copy(alpha = 0.1f))
            )
        }
    }
}

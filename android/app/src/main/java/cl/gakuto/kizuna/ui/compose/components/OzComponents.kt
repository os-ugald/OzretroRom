package cl.gakuto.kizuna.ui.compose.components

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameTileStyle
import cl.gakuto.kizuna.launch.SessionActivity
import cl.gakuto.kizuna.ui.compose.theme.*
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters
import coil3.compose.AsyncImage

@Composable
fun OzSectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = OzTypography.section, color = OzColors.TextPrimary)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = OzTypography.meta, color = OzColors.TextSecondary)
        }
        if (!action.isNullOrBlank() && onAction != null) {
            Text(
                action,
                style = OzTypography.meta,
                color = OzColors.Cyan,
                modifier = Modifier.clip(OzShapes.tiny).clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
fun OzGameCover(
    game: GameEntity,
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    showTitle: Boolean = true,
) {
    val displayTitle = remember(game.title) { CatalogFilters.displayTitle(game.title) }
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var showContextMenu by remember(game.id) { mutableStateOf(false) }
    val compact = preferences.gameTileStyle == GameTileStyle.COMPACT_ICON
    val scale by animateFloatAsState(
        targetValue = if (focused) LocalOzMotion.current.focusScale else if (pressed) 0.985f else 1f,
        animationSpec = tween(LocalOzMotion.current.fast),
        label = "oz-game-tile-focus",
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    val candidates = remember(
        context,
        compact,
        game.id,
        game.title,
        game.thumbnailRepo,
        preferences.artworkMode,
        preferences.localArtworkTreeUri,
        preferences.gameArtworkMode(game.id),
        preferences.customArtworkUri(game.id),
        preferences.customArtworkUrl(game.id),
        preferences.syncedArtworkTitle(game.id),
        preferences.syncedArtworkRepo(game.id),
        preferences.syncedArtworkLocalUri(game.id),
        preferences.syncedGameIconLocalUri(game.id),
    ) {
        if (compact) ArtworkResolver.tileCandidates(context, preferences, game)
        else ArtworkResolver.candidates(context, preferences, game)
    }
    val tileShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .scale(if (focused) 1.04f else scale)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.ButtonStart -> { showContextMenu = true; true }
                    Key.ButtonA, Key.Enter, Key.DirectionCenter -> { onClick(); true }
                    else -> false
                }
            }
            .focusable()
            .clickable(
                onClick = { pressed = false; onClick() },
                onClickLabel = "Abrir $displayTitle",
            )
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO)
                .clip(tileShape)
                .border(
                    BorderStroke(
                        if (focused) 2.dp else 1.dp,
                        if (focused) OzColors.Cyan else OzColors.Outline.copy(alpha = .45f),
                    ),
                    tileShape,
                ),
        ) {
            OzCoverImage(
                candidates = candidates,
                title = displayTitle,
                systemId = game.systemId,
                contentScale = ContentScale.Crop,
                showTitleOnFallback = true,
                modifier = Modifier.fillMaxSize(),
            )
            if (!game.contentUri.isNullOrBlank()) {
                if (compact) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .size(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(OzColors.Positive)
                            .border(1.dp, OzColors.DeepSpace.copy(alpha = .8f), RoundedCornerShape(999.dp)),
                    )
                } else {
                    OzStatusPill("LOCAL", OzColors.Positive, Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
        }
        if (showTitle) {
            Text(
                displayTitle,
                style = if (compact) OzTypography.meta else OzTypography.cardTitle,
                color = if (focused) OzColors.TextPrimary else OzColors.TextSecondary,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 2.dp else 4.dp, vertical = if (compact) 5.dp else 7.dp),
            )
        }
    }
    if (showContextMenu) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = { Text(displayTitle, style = OzTypography.section, color = OzColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        listOfNotNull(game.platform, game.year?.toString(), game.region)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = OzTypography.meta,
                        color = OzColors.TextSecondary,
                    )
                    Text("START · acciones del juego", style = OzTypography.meta, color = OzColors.Cyan)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showContextMenu = false
                    context.startActivity(
                        Intent(context, SessionActivity::class.java)
                            .putExtra(SessionActivity.EXTRA_GAME_ID, game.id)
                    )
                }) { Text(if (game.contentUri.isNullOrBlank()) "Preparar / jugar" else "Jugar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showContextMenu = false; onClick() }) { Text("Ficha") }
                    TextButton(onClick = { showContextMenu = false }) { Text("Cerrar") }
                }
            },
            containerColor = OzColors.PanelRaised,
            textContentColor = OzColors.TextPrimary,
        )
    }

}

@Composable
fun OzStatusPill(text: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(OzColors.DeepSpace.copy(alpha = .86f))
            .border(1.dp, accent.copy(alpha = .75f), RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(text, style = OzTypography.meta, color = accent)
    }
}

@Composable
fun OzPrimaryTile(
    title: String,
    subtitle: String,
    symbol: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, tween(LocalOzMotion.current.fast), label = "tile")
    Row(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(OzShapes.large)
            .background(
                Brush.linearGradient(
                    listOf(OzColors.PurpleDeep, OzColors.ArcadeViolet.copy(alpha = .55f), OzColors.ElectricBlue.copy(alpha = .38f)),
                ),
            )
            .border(1.dp, if (focused) OzColors.Cyan else OzColors.Outline.copy(alpha = .7f), OzShapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(symbol, style = OzTypography.section, color = OzColors.Cyan)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = OzTypography.tileTitle,
                color = OzColors.TextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
            Text(subtitle, style = OzTypography.meta, color = OzColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

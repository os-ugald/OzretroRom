package cl.gakuto.kizuna.ui.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameGridDensity
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Nintendo DS retail box art proportion (~137mm x 124mm / Libretro 512x460).
 */
const val OZ_NDS_COVER_ASPECT_RATIO = 1.11f

/**
 * Phase 3 Hylian Dark uniform immersive portrait cover ratio (3:4).
 */
const val OZ_IMMERSIVE_COVER_ASPECT_RATIO = 0.75f

/**
 * Fixed 3-column phone grid by default ("tres hacia abajo") while adapting smoothly to wide tablet screens.
 */
fun gridColumnsForDensity(
    density: GameGridDensity,
    wide: Boolean,
    maxWidthDp: Float,
): Int = if (!wide) {
    when (density) {
        GameGridDensity.SMALL -> 4
        GameGridDensity.LARGE -> 2
        GameGridDensity.AUTO,
        GameGridDensity.NORMAL -> 3
    }
} else {
    when (density) {
        GameGridDensity.SMALL -> if (maxWidthDp >= 1040f) 7 else 6
        GameGridDensity.LARGE -> 4
        GameGridDensity.AUTO,
        GameGridDensity.NORMAL -> if (maxWidthDp >= 1040f) 6 else 5
    }
}

@Composable
fun OzCoverImage(
    candidates: List<String>,
    title: String,
    systemId: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    showTitleOnFallback: Boolean = true,
    adaptiveBlur: Boolean = true,
) {
    val context = LocalContext.current
    val consoleVisual = remember(systemId) { ConsolePalette.forSystem(systemId) }
    val cleanTitle = remember(title) { CatalogFilters.displayTitle(title).ifBlank { title } }
    var candidateIndex by remember(candidates) { mutableStateOf(0) }
    var loaded by remember(candidates) { mutableStateOf(false) }
    var exhausted by remember(candidates) { mutableStateOf(candidates.isEmpty()) }

    val activeUrl = if (!exhausted) candidates.getOrNull(candidateIndex) else null

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    OzColors.PanelRaised,
                    OzColors.DeepSpace,
                    OzColors.Panel,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = consoleVisual.shortLabel,
                    style = OzTypography.meta.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = OzColors.Gold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OzColors.ArcadeViolet.copy(alpha = 0.22f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                if (showTitleOnFallback) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = cleanTitle,
                        style = OzTypography.meta.copy(fontSize = 10.sp),
                        color = OzColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (activeUrl != null) {
            if (adaptiveBlur) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(activeUrl)
                        .crossfade(160)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = 0.55f)
                        .blur(20.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OzColors.HylianBg.copy(alpha = 0.36f)),
                )
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeUrl)
                    .crossfade(160)
                    .build(),
                contentDescription = cleanTitle,
                contentScale = if (adaptiveBlur) ContentScale.Fit else contentScale,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { loaded = true },
                onError = {
                    if (candidateIndex < candidates.lastIndex) {
                        candidateIndex += 1
                    } else {
                        exhausted = true
                    }
                },
            )
        }
    }
}

/**
 * Clean 3:4 Hylian Dark cover card:
 * - Zero text/badges covering the art
 * - Single discreet top-end dot for Local (Emerald) vs PC Hub/Remote (Gold)
 * - 1.05x zoom + Gold focus ring + ambient backdrop notification on focus
 */
@Composable
fun HylianGameCard(
    game: GameEntity,
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
    isRemoteHubOnly: Boolean = false,
    onFocused: (String?) -> Unit = {},
    onOpen: () -> Unit,
    onStart: () -> Unit = onOpen,
) {
    val context = LocalContext.current
    val displayTitle = remember(game.title) { CatalogFilters.displayTitle(game.title).ifBlank { game.title } }
    val candidates = remember(
        context,
        game.id,
        game.title,
        game.thumbnailRepo,
        preferences.artworkMode,
        preferences.customArtworkUrl(game.id),
        preferences.syncedArtworkLocalUri(game.id),
    ) {
        ArtworkResolver.tileCandidates(context, preferences, game)
    }
    var focused by remember(game.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "hylian-card-focus",
    )
    val isLocal = !game.contentUri.isNullOrBlank()
    val dotColor = if (isLocal) OzColors.HylianEmerald else if (isRemoteHubOnly) OzColors.HylianGold else OzColors.Outline

    Column(
        modifier = modifier
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(candidates.firstOrNull())
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.ButtonStart -> { onStart(); true }
                    Key.ButtonA, Key.Enter, Key.DirectionCenter -> { onOpen(); true }
                    else -> false
                }
            }
            .focusable()
            .clickable {
                onFocused(candidates.firstOrNull())
                onOpen()
            }
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(OZ_IMMERSIVE_COVER_ASPECT_RATIO)
                .clip(OzShapes.medium)
                .border(
                    BorderStroke(
                        width = if (focused) 2.5.dp else 1.dp,
                        color = if (focused) OzColors.HylianGold else OzColors.HylianBorder,
                    ),
                    OzShapes.medium,
                ),
        ) {
            OzCoverImage(
                candidates = candidates,
                title = displayTitle,
                systemId = game.systemId,
                adaptiveBlur = true,
                showTitleOnFallback = true,
                modifier = Modifier.fillMaxSize(),
            )
            if (isLocal || isRemoteHubOnly) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(1.dp, OzColors.HylianBg, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = displayTitle,
            style = OzTypography.cardTitle.copy(fontSize = 12.5.sp),
            color = if (focused) OzColors.HylianGold else OzColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
fun OzFourCoverMosaic(
    games: List<GameEntity>,
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val items = remember(games) { games.take(4) }
    if (items.isEmpty()) {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(listOf(OzColors.PanelRaised, OzColors.DeepSpace)),
            ),
        )
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items.getOrNull(0)?.let { g ->
                val cands = remember(g.id, g.title, g.thumbnailRepo) {
                    ArtworkResolver.tileCandidates(context, preferences, g)
                }
                OzCoverImage(
                    candidates = cands,
                    title = g.title,
                    systemId = g.systemId,
                    adaptiveBlur = false,
                    contentScale = ContentScale.Crop,
                    showTitleOnFallback = false,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            if (items.size > 1) {
                val g = items[1]
                val cands = remember(g.id, g.title, g.thumbnailRepo) {
                    ArtworkResolver.tileCandidates(context, preferences, g)
                }
                OzCoverImage(
                    candidates = cands,
                    title = g.title,
                    systemId = g.systemId,
                    adaptiveBlur = false,
                    contentScale = ContentScale.Crop,
                    showTitleOnFallback = false,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        if (items.size > 2) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val g2 = items[2]
                val cands2 = remember(g2.id, g2.title, g2.thumbnailRepo) {
                    ArtworkResolver.tileCandidates(context, preferences, g2)
                }
                OzCoverImage(
                    candidates = cands2,
                    title = g2.title,
                    systemId = g2.systemId,
                    adaptiveBlur = false,
                    contentScale = ContentScale.Crop,
                    showTitleOnFallback = false,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                if (items.size > 3) {
                    val g3 = items[3]
                    val cands3 = remember(g3.id, g3.title, g3.thumbnailRepo) {
                        ArtworkResolver.tileCandidates(context, preferences, g3)
                    }
                    OzCoverImage(
                        candidates = cands3,
                        title = g3.title,
                        systemId = g3.systemId,
                        adaptiveBlur = false,
                        contentScale = ContentScale.Crop,
                        showTitleOnFallback = false,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

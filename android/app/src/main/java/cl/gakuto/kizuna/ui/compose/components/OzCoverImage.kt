package cl.gakuto.kizuna.ui.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameGridDensity
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShellLayout
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/** Nintendo DS retail box art proportion (~137mm x 124mm / Libretro 512x460). */
const val OZ_NDS_COVER_ASPECT_RATIO = 1.11f

/** Uniform immersive portrait cover ratio (3:4). */
const val OZ_IMMERSIVE_COVER_ASPECT_RATIO = 0.75f

/**
 * Column count resolver for Library & Catalog grids.
 * Preserves the legacy 3-param signature while supporting `OzShellLayout.LANDSCAPE_HANDHELD` (5 cols default).
 */
fun gridColumnsForDensity(
    density: GameGridDensity,
    wide: Boolean,
    maxWidthDp: Float,
    shellLayout: OzShellLayout? = null,
): Int = when (shellLayout) {
    OzShellLayout.LANDSCAPE_HANDHELD -> when (density) {
        GameGridDensity.SMALL -> 6
        GameGridDensity.LARGE -> 4
        GameGridDensity.AUTO,
        GameGridDensity.NORMAL -> 5
    }
    OzShellLayout.DESKTOP_WIDE -> when (density) {
        GameGridDensity.SMALL -> if (maxWidthDp >= 1040f) 7 else 6
        GameGridDensity.LARGE -> 4
        GameGridDensity.AUTO,
        GameGridDensity.NORMAL -> if (maxWidthDp >= 1040f) 6 else 5
    }
    OzShellLayout.PORTRAIT_PHONE -> when (density) {
        GameGridDensity.SMALL -> 4
        GameGridDensity.LARGE -> 2
        GameGridDensity.AUTO,
        GameGridDensity.NORMAL -> 3
    }
    null -> if (!wide) {
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
}

/**
 * Resilient box-art image with stylized console-themed technical fallback (never a flat grey box).
 */
@Composable
fun OzCoverImage(
    candidates: List<String>,
    title: String,
    systemId: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    showTitleOnFallback: Boolean = true,
    showConsoleBadgeOnFallback: Boolean = true,
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
                    consoleVisual.badgeBg.copy(alpha = 0.92f),
                    OzColors.SurfaceElevated,
                    OzColors.Bg,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        // Technical HUD grid & inner frame when artwork is missing or still loading
        if (!loaded) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val accent = consoleVisual.accent
                val step = 18.dp.toPx()
                val gridColor = accent.copy(alpha = 0.08f)
                var x = step
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
                val inset = 7.dp.toPx()
                if (size.width > inset * 2 && size.height > inset * 2) {
                    drawRect(
                        color = accent.copy(alpha = 0.24f),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    // Corner tech accents
                    val tick = 10.dp.toPx().coerceAtMost(size.minDimension * 0.22f)
                    val cornerColor = accent.copy(alpha = 0.65f)
                    val sw = 1.6.dp.toPx()
                    drawLine(cornerColor, Offset(inset, inset), Offset(inset + tick, inset), sw)
                    drawLine(cornerColor, Offset(inset, inset), Offset(inset, inset + tick), sw)
                    drawLine(cornerColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - tick, size.height - inset), sw)
                    drawLine(cornerColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - tick), sw)
                }
            }

            if (showConsoleBadgeOnFallback || showTitleOnFallback) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showConsoleBadgeOnFallback) {
                        OzHudTag(
                            text = consoleVisual.shortLabel,
                            accent = consoleVisual.accent,
                        )
                    }
                    if (showTitleOnFallback) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = cleanTitle.uppercase(),
                            style = OzTypography.meta.copy(fontSize = 10.sp, lineHeight = 13.sp),
                            color = OzColors.TextPrimary.copy(alpha = 0.88f),
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                        .background(OzColors.Bg.copy(alpha = 0.40f)),
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
 * Clean 3:4 chamfered HUD game card:
 * - Chamfered 45° corners (`OzChamferShape(10.dp)`), `ozHudBorderBrush`, neon glow on focus
 * - Vector icons only (`Icons.Rounded.*` and clean `Canvas` glyphs — zero Unicode symbols)
 * - Compact footer maximizing the 3:4 cover
 */
@Composable
fun HylianGameCard(
    game: GameEntity,
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
    isRemoteHubOnly: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onFocused: (String?) -> Unit = {},
    onOpen: () -> Unit,
    onStart: () -> Unit = onOpen,
) {
    val context = LocalContext.current
    val consoleVisual = remember(game.systemId) { ConsolePalette.forSystem(game.systemId) }
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
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(150),
        label = "oz-card-focus",
    )
    val isLocal = !game.contentUri.isNullOrBlank()
    val cardShape = remember { OzChamferShape(10.dp) }
    val favShape = remember { OzChamferShape(4.dp) }
    val borderBrush = ozHudBorderBrush(if (focused) 0.95f else 0.30f)

    Column(
        modifier = modifier
            .scale(scale)
            .then(if (focused) Modifier.ozNeonGlow(OzColors.AccentPrimary, radius = 12.dp, alpha = 0.35f) else Modifier)
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    listOf(OzColors.SurfaceElevated.copy(alpha = 0.65f), OzColors.Surface),
                ),
            )
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = cardShape,
            )
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
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(OZ_IMMERSIVE_COVER_ASPECT_RATIO),
        ) {
            OzCoverImage(
                candidates = candidates,
                title = displayTitle,
                systemId = game.systemId,
                adaptiveBlur = true,
                showTitleOnFallback = true,
                modifier = Modifier.fillMaxSize(),
            )
            if (onToggleFavorite != null || isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(24.dp)
                        .clip(favShape)
                        .background(OzColors.Bg.copy(alpha = 0.80f))
                        .border(
                            width = 1.dp,
                            color = if (isFavorite) OzColors.AccentSecondary else OzColors.Border,
                            shape = favShape,
                        )
                        .let { if (onToggleFavorite != null) it.clickable { onToggleFavorite() } else it },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar favorito" else "Favorito",
                        tint = if (isFavorite) OzColors.AccentSecondary else OzColors.TextSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = displayTitle.uppercase(),
                style = OzTypography.cardTitle.copy(fontSize = 11.sp),
                color = if (focused) OzColors.AccentPrimary else OzColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OzHudTag(
                    text = consoleVisual.shortLabel,
                    accent = consoleVisual.accent,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        isLocal -> Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "ROM local",
                            tint = OzColors.Positive,
                            modifier = Modifier.size(13.dp),
                        )
                        isRemoteHubOnly -> OzCloudVectorGlyph(
                            tint = OzColors.AccentPrimary,
                            modifier = Modifier.size(13.dp),
                        )
                        else -> OzDownloadVectorGlyph(
                            tint = OzColors.TextSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(OzChamferShape(4.dp))
                            .clickable { onStart() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Opciones",
                            tint = OzColors.TextSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OzCloudVectorGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = 1.5.dp.toPx()
        val p = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.74f)
            lineTo(size.width * 0.76f, size.height * 0.74f)
            cubicTo(
                size.width * 0.90f, size.height * 0.74f,
                size.width * 0.92f, size.height * 0.48f,
                size.width * 0.74f, size.height * 0.46f,
            )
            cubicTo(
                size.width * 0.72f, size.height * 0.22f,
                size.width * 0.36f, size.height * 0.20f,
                size.width * 0.32f, size.height * 0.44f,
            )
            cubicTo(
                size.width * 0.14f, size.height * 0.46f,
                size.width * 0.12f, size.height * 0.74f,
                size.width * 0.25f, size.height * 0.74f,
            )
            close()
        }
        drawPath(p, tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun OzDownloadVectorGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = 1.5.dp.toPx()
        drawLine(tint, Offset(size.width * 0.50f, size.height * 0.16f), Offset(size.width * 0.50f, size.height * 0.66f), sw, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.28f, size.height * 0.46f), Offset(size.width * 0.50f, size.height * 0.68f), sw, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.72f, size.height * 0.46f), Offset(size.width * 0.50f, size.height * 0.68f), sw, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.20f, size.height * 0.84f), Offset(size.width * 0.80f, size.height * 0.84f), sw, StrokeCap.Round)
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
                Brush.verticalGradient(listOf(OzColors.SurfaceElevated, OzColors.Bg)),
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

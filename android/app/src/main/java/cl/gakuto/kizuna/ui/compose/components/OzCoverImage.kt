package cl.gakuto.kizuna.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Nintendo DS retail box art proportion (~137mm x 124mm / Libretro 512x460).
 * Applied consistently across grids, rails, Sagas, and Smart Lists so every cover renders uniformly.
 */
const val OZ_NDS_COVER_ASPECT_RATIO = 1.11f

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
    contentScale: ContentScale = ContentScale.Crop,
    showTitleOnFallback: Boolean = true,
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
                    consoleVisual.badgeBg.copy(alpha = 0.96f),
                    OzColors.DeepSpace,
                    OzColors.PanelRaised,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        // Fallback placeholder shown before AsyncImage completes or if offline/unmatched
        if (!loaded) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = consoleVisual.shortLabel,
                    style = OzTypography.meta.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = consoleVisual.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(consoleVisual.accent.copy(alpha = 0.14f))
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
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeUrl)
                    .crossfade(160)
                    .build(),
                contentDescription = cleanTitle,
                contentScale = contentScale,
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
                Brush.verticalGradient(listOf(OzColors.PurpleDeep.copy(alpha = 0.45f), OzColors.DeepSpace)),
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
                        showTitleOnFallback = false,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

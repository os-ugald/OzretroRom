package cl.gakuto.kizuna.ui.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.HylianGameCard
import cl.gakuto.kizuna.ui.compose.components.OZ_IMMERSIVE_COVER_ASPECT_RATIO
import cl.gakuto.kizuna.ui.compose.components.OzChamferShape
import cl.gakuto.kizuna.ui.compose.components.OzCoverImage
import cl.gakuto.kizuna.ui.compose.components.OzHudButton
import cl.gakuto.kizuna.ui.compose.components.OzHudCard
import cl.gakuto.kizuna.ui.compose.components.OzHudChip
import cl.gakuto.kizuna.ui.compose.components.OzHudSectionHeader
import cl.gakuto.kizuna.ui.compose.components.OzHudTag
import cl.gakuto.kizuna.ui.compose.components.ozHudBorderBrush
import cl.gakuto.kizuna.ui.compose.components.ozNeonGlow
import cl.gakuto.kizuna.ui.compose.domain.QuickResumeHero
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.LocalOzShellLayout
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShellLayout
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters

/**
 * Core View 1 ("Inicio") — clean rewrite driven by `LocalOzShellLayout.current`:
 * - Panoramic Quick Resume Hero with real cover backdrop + dark high-contrast scrim + crisp cover thumbnail.
 * - `LANDSCAPE_HANDHELD`: compact 148dp horizontal hero → `JUGADOS RECIENTEMENTE` immediately below → `COLECCIONES` → `RECOMENDADOS`.
 * - `PORTRAIT_PHONE` / `DESKTOP_WIDE`: cinematic hero → `JUGADOS RECIENTEMENTE` → `COLECCIONES` → `RECOMENDADOS`.
 */
@Composable
fun OzHomeScreen(
    viewModel: OzViewModel,
    onLaunchGame: (GameEntity) -> Unit,
    onOpenGame: (String) -> Unit,
    onStartGameMenu: (GameEntity) -> Unit,
    onOpenLibraryPlatform: (String) -> Unit,
    onOpenExplore: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val shellLayout = LocalOzShellLayout.current
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val local by viewModel.local.collectAsStateWithLifecycle()
    val quickResumeHero by viewModel.quickResumeHero.collectAsStateWithLifecycle()
    val favoriteIds = remember(favorites) { favorites.mapTo(hashSetOf()) { it.id } }

    val platformSystems = remember(viewModel.systems) { viewModel.systems.take(10) }
    val heroTargetGame = quickResumeHero?.game ?: recent.firstOrNull() ?: favorites.firstOrNull() ?: local.firstOrNull()
    val recommendedPool = remember(favorites, local) { (favorites + local).distinctBy { it.id }.take(12) }

    val isLandscape = shellLayout == OzShellLayout.LANDSCAPE_HANDHELD
    val isDesktop = shellLayout == OzShellLayout.DESKTOP_WIDE
    val horizontalPad = when (shellLayout) {
        OzShellLayout.DESKTOP_WIDE -> 24.dp
        OzShellLayout.LANDSCAPE_HANDHELD -> 14.dp
        OzShellLayout.PORTRAIT_PHONE -> 14.dp
    }
    val verticalGap = if (isLandscape) 12.dp else 16.dp
    val cardWidth = when (shellLayout) {
        OzShellLayout.LANDSCAPE_HANDHELD -> 112.dp
        OzShellLayout.DESKTOP_WIDE -> 160.dp
        OzShellLayout.PORTRAIT_PHONE -> 124.dp
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OzColors.Bg),
        contentPadding = PaddingValues(
            horizontal = horizontalPad,
            vertical = if (isLandscape) 10.dp else 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(verticalGap),
    ) {
        item(key = "hero") {
            OzHomeHeroBanner(
                hero = quickResumeHero,
                fallbackGame = heroTargetGame,
                preferences = viewModel.preferences,
                shellLayout = shellLayout,
                onContinueNow = onLaunchGame,
                onOpenGame = { g -> onOpenGame(g.id) },
                onMoreActions = onStartGameMenu,
                onExplore = onOpenExplore,
                onSearch = onOpenSearch,
            )
        }

        if (recent.isNotEmpty()) {
            item(key = "recent") {
                OzHomeCarousel(
                    title = "Jugados recientemente",
                    games = recent.take(12),
                    favoriteIds = favoriteIds,
                    preferences = viewModel.preferences,
                    cardWidth = cardWidth,
                    onOpen = onOpenGame,
                    onStart = onStartGameMenu,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onViewAll = { onOpenLibraryPlatform("") },
                )
            }
        }

        if (platformSystems.isNotEmpty()) {
            item(key = "collections") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OzHudSectionHeader(
                        title = "Colecciones",
                        actionLabel = "Ver todo",
                        onAction = onOpenExplore,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(platformSystems, key = { it.id }) { sys ->
                            val meta = ConsolePalette.forSystem(sys.id)
                            OzHudChip(
                                label = meta.shortLabel,
                                selected = false,
                                compact = true,
                                accent = meta.accent,
                                onClick = { onOpenLibraryPlatform(sys.id) },
                            )
                        }
                    }
                }
            }
        }

        if (recommendedPool.isNotEmpty()) {
            item(key = "recommended") {
                OzHomeCarousel(
                    title = if (favorites.isNotEmpty()) "Recomendados y favoritos" else "En tu bóveda",
                    games = recommendedPool,
                    favoriteIds = favoriteIds,
                    preferences = viewModel.preferences,
                    cardWidth = cardWidth,
                    onOpen = onOpenGame,
                    onStart = onStartGameMenu,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onViewAll = { onOpenLibraryPlatform("") },
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(if (isDesktop) 16.dp else 10.dp))
        }
    }
}

@Composable
private fun OzHomeHeroBanner(
    hero: QuickResumeHero?,
    fallbackGame: GameEntity?,
    preferences: AppPreferences,
    shellLayout: OzShellLayout,
    onContinueNow: (GameEntity) -> Unit,
    onOpenGame: (GameEntity) -> Unit,
    onMoreActions: (GameEntity) -> Unit,
    onExplore: () -> Unit,
    onSearch: () -> Unit,
) {
    val context = LocalContext.current
    val game = hero?.game ?: fallbackGame
    val heroShape = remember { OzChamferShape(12.dp) }
    val thumbShape = remember { OzChamferShape(8.dp) }
    val isLandscape = shellLayout == OzShellLayout.LANDSCAPE_HANDHELD
    val isDesktop = shellLayout == OzShellLayout.DESKTOP_WIDE

    if (game == null) {
        OzHudCard(cut = 12.dp, elevated = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isLandscape) 14.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OzHudTag(text = "BÓVEDA LISTA", accent = OzColors.AccentPrimary)
                Text("OZ RETROROM", style = OzTypography.hero, color = OzColors.TextPrimary)
                Text(
                    "Explora colecciones curadas o añade tu carpeta de ROMs desde Buscar.",
                    style = OzTypography.body,
                    color = OzColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OzHudButton(
                        text = "Explorar",
                        onClick = onExplore,
                        primary = true,
                        compact = isLandscape,
                        leadingVector = Icons.Rounded.PlayArrow,
                        modifier = Modifier.weight(1f),
                    )
                    OzHudButton(
                        text = "Buscar",
                        onClick = onSearch,
                        primary = false,
                        compact = isLandscape,
                        leadingVector = Icons.Rounded.Search,
                    )
                }
            }
        }
        return
    }

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
    val consoleVisual = remember(game.systemId) { ConsolePalette.forSystem(game.systemId) }
    val displayTitle = remember(game.title) { CatalogFilters.displayTitle(game.title).ifBlank { game.title } }
    val subtitleText = remember(consoleVisual.fullName, game.year, hero?.isQuickResume, hero?.slot) {
        buildString {
            append(consoleVisual.fullName)
            if (game.year != null) append(" · ${game.year}")
            if (hero?.isQuickResume == true) append(" · Slot ${hero.slot}")
        }
    }
    val heroHeight = when (shellLayout) {
        OzShellLayout.LANDSCAPE_HANDHELD -> 148.dp
        OzShellLayout.DESKTOP_WIDE -> 196.dp
        OzShellLayout.PORTRAIT_PHONE -> 176.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(heroShape)
            .background(OzColors.Surface)
            .border(1.dp, ozHudBorderBrush(0.45f), heroShape),
    ) {
        // Real box-art panoramic backdrop (or stylized ConsolePalette fallback when missing)
        OzCoverImage(
            candidates = candidates,
            title = displayTitle,
            systemId = game.systemId,
            adaptiveBlur = false,
            contentScale = ContentScale.Crop,
            showTitleOnFallback = false,
            showConsoleBadgeOnFallback = false,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(alpha = 0.72f)
                .blur(14.dp),
        )

        // High-contrast dual scrim: guarantees 100% readable typography even when blur is unavailable (Android < 12)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to OzColors.Bg.copy(alpha = 0.90f),
                        0.48f to OzColors.Bg.copy(alpha = 0.80f),
                        1.0f to OzColors.Bg.copy(alpha = 0.62f),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to OzColors.Bg.copy(alpha = 0.28f),
                        0.60f to OzColors.Bg.copy(alpha = 0.52f),
                        1.0f to OzColors.Bg.copy(alpha = 0.88f),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isLandscape) 12.dp else 14.dp, vertical = if (isLandscape) 10.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Crisp 3:4 cover thumbnail on the left
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(OZ_IMMERSIVE_COVER_ASPECT_RATIO)
                    .clip(thumbShape)
                    .border(1.dp, ozHudBorderBrush(0.70f), thumbShape)
                    .clickable { onOpenGame(game) },
            ) {
                OzCoverImage(
                    candidates = candidates,
                    title = displayTitle,
                    systemId = game.systemId,
                    adaptiveBlur = true,
                    showTitleOnFallback = true,
                    showConsoleBadgeOnFallback = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Metadata + CTA column on the right
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OzHudTag(
                            text = if (hero?.isQuickResume == true) "CONTINUAR JUGANDO" else "EN BÓVEDA",
                            accent = OzColors.AccentPrimary,
                        )
                        OzHudTag(
                            text = consoleVisual.shortLabel,
                            accent = consoleVisual.accent,
                        )
                    }

                    Text(
                        text = displayTitle.uppercase(),
                        style = OzTypography.hero.copy(
                            fontSize = when (shellLayout) {
                                OzShellLayout.DESKTOP_WIDE -> 22.sp
                                OzShellLayout.LANDSCAPE_HANDHELD -> 18.sp
                                OzShellLayout.PORTRAIT_PHONE -> 17.sp
                            },
                            lineHeight = if (isLandscape) 21.sp else 22.sp,
                        ),
                        color = OzColors.TextPrimary,
                        maxLines = if (isLandscape) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = subtitleText,
                        style = OzTypography.bodyMedium.copy(fontSize = if (isLandscape) 12.sp else 12.5.sp),
                        color = OzColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OzHudButton(
                        text = "Continuar",
                        leadingVector = Icons.Rounded.PlayArrow,
                        primary = true,
                        compact = isLandscape,
                        onClick = { onContinueNow(game) },
                        modifier = if (isLandscape || isDesktop) Modifier.width(180.dp) else Modifier.weight(1f),
                    )
                    OzHeroOverflowButton(
                        size = if (isLandscape) 34.dp else 44.dp,
                        onClick = { onMoreActions(game) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OzHeroOverflowButton(
    size: Dp,
    onClick: () -> Unit,
) {
    val shape = remember { OzChamferShape(8.dp) }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(size)
            .then(if (focused) Modifier.ozNeonGlow(OzColors.AccentPrimary, radius = 10.dp, alpha = 0.30f) else Modifier)
            .clip(shape)
            .background(OzColors.SurfaceElevated.copy(alpha = 0.92f))
            .border(if (focused) 1.5.dp else 1.dp, ozHudBorderBrush(if (focused) 0.95f else 0.40f), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = "Más acciones",
            tint = OzColors.TextPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun OzHomeCarousel(
    title: String,
    games: List<GameEntity>,
    favoriteIds: Set<String>,
    preferences: AppPreferences,
    cardWidth: Dp,
    onOpen: (String) -> Unit,
    onStart: (GameEntity) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onViewAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OzHudSectionHeader(
            title = title,
            actionLabel = "Ver todo",
            onAction = onViewAll,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(games, key = { it.id }) { game ->
                HylianGameCard(
                    game = game,
                    preferences = preferences,
                    isFavorite = game.id in favoriteIds,
                    onToggleFavorite = { onToggleFavorite(game.id) },
                    onOpen = { onOpen(game.id) },
                    onStart = { onStart(game) },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

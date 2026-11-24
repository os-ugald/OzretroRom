@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cl.gakuto.kizuna.ui.compose.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.hub.HubRemoteGame
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.HylianGameCard
import cl.gakuto.kizuna.ui.compose.components.OZ_IMMERSIVE_COVER_ASPECT_RATIO
import cl.gakuto.kizuna.ui.compose.components.OzCoverImage
import cl.gakuto.kizuna.ui.compose.components.OzSectionHeader
import cl.gakuto.kizuna.ui.compose.domain.QuickResumeHero
import cl.gakuto.kizuna.ui.compose.surface.OzSurfaceBackdrop
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters
import kotlinx.coroutines.launch

/**
 * Console destination 1 ("Jugar"):
 * - Top Omnibox (Local + PC Hub + MiNERVA unified search, Y button focusable)
 * - Immersive Quick Resume Hero ("Continuar Ahora") with 1-tap launch
 * - Clean horizontal 3:4 carousels (Recientes, Favoritos, En tu bóveda)
 */
@Composable
fun HylianPlayScreen(
    viewModel: OzViewModel,
    omniboxFocusCommand: Int,
    onLaunchGame: (GameEntity) -> Unit,
    onOpenGame: (String) -> Unit,
    onStartGameMenu: (GameEntity) -> Unit,
    onOpenExplore: () -> Unit,
    onOpenPcHub: () -> Unit,
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val local by viewModel.local.collectAsStateWithLifecycle()
    val quickResumeHero by viewModel.quickResumeHero.collectAsStateWithLifecycle()
    val pcHubGames by viewModel.pcHubGames.collectAsStateWithLifecycle()
    val minervaResults by viewModel.catalogSearchResults.collectAsStateWithLifecycle()
    val catalogQuery by viewModel.catalogQuery.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val omniboxRequester = remember { FocusRequester() }
    var omniboxText by remember { mutableStateOf(catalogQuery) }
    var focusedArtUrl by remember { mutableStateOf<String?>(null) }
    var materializingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(omniboxFocusCommand) {
        if (omniboxFocusCommand > 0) {
            runCatching { omniboxRequester.requestFocus() }
        }
    }

    val localPool = remember(local, recent, favorites) {
        (recent + favorites + local).distinctBy { it.id }
    }
    val needle = omniboxText.trim().lowercase()
    val isSearching = needle.length >= 2

    val localMatches = remember(localPool, needle) {
        if (!isSearching) emptyList()
        else localPool.filter {
            it.title.lowercase().contains(needle) || it.platform.lowercase().contains(needle)
        }.take(6)
    }
    val hubMatches = remember(pcHubGames, needle) {
        if (!isSearching) emptyList()
        else pcHubGames.filter {
            it.cleanTitle.lowercase().contains(needle) ||
                it.title.lowercase().contains(needle) ||
                it.consoleKey.lowercase().contains(needle)
        }.take(6)
    }
    val minervaMatches = remember(minervaResults, needle) {
        if (!isSearching) emptyList() else minervaResults.take(8)
    }

    val heroArtFallback = remember(quickResumeHero, context) {
        quickResumeHero?.let { ArtworkResolver.tileCandidates(context, viewModel.preferences, it.game).firstOrNull() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp || maxWidth > maxHeight

        OzSurfaceBackdrop(focusedGameArtUrl = focusedArtUrl ?: heroArtFallback) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    HylianOmniboxBar(
                        value = omniboxText,
                        onValueChange = { next ->
                            omniboxText = next
                            viewModel.setCatalogQuery(next)
                        },
                        onClear = {
                            omniboxText = ""
                            viewModel.clearCatalogSearch()
                        },
                        focusRequester = omniboxRequester,
                    )
                }

                if (isSearching) {
                    item {
                        OmniboxResultsSection(
                            query = omniboxText.trim(),
                            localMatches = localMatches,
                            hubMatches = hubMatches,
                            minervaMatches = minervaMatches,
                            materializingId = materializingId,
                            preferences = viewModel.preferences,
                            onOpenLocal = onOpenGame,
                            onOpenPcHub = onOpenPcHub,
                            onOpenMinerva = { item ->
                                if (materializingId == null) {
                                    scope.launch {
                                        materializingId = item.id
                                        runCatching { viewModel.materializeCatalogItem(item) }
                                            .onSuccess { onOpenGame(it.id) }
                                        materializingId = null
                                    }
                                }
                            },
                        )
                    }
                } else {
                    item {
                        ContinuarAhoraHero(
                            hero = quickResumeHero,
                            preferences = viewModel.preferences,
                            wide = wide,
                            onContinueNow = { game -> onLaunchGame(game) },
                            onOpenDetail = { game -> onOpenGame(game.id) },
                            onExplore = onOpenExplore,
                        )
                    }

                    if (recent.isNotEmpty()) {
                        item {
                            HylianCarouselSection(
                                title = "Recientes",
                                subtitle = "Tus últimas sesiones",
                                games = recent.take(12),
                                preferences = viewModel.preferences,
                                onFocused = { focusedArtUrl = it },
                                onOpen = onOpenGame,
                                onStart = onStartGameMenu,
                            )
                        }
                    }

                    if (favorites.isNotEmpty()) {
                        item {
                            HylianCarouselSection(
                                title = "Favoritos",
                                subtitle = "Acceso directo",
                                games = favorites.take(12),
                                preferences = viewModel.preferences,
                                onFocused = { focusedArtUrl = it },
                                onOpen = onOpenGame,
                                onStart = onStartGameMenu,
                            )
                        }
                    }

                    if (local.isNotEmpty()) {
                        item {
                            HylianCarouselSection(
                                title = "Tu bóveda local",
                                subtitle = "ROMs listas en Android",
                                games = local.take(12),
                                preferences = viewModel.preferences,
                                onFocused = { focusedArtUrl = it },
                                onOpen = onOpenGame,
                                onStart = onStartGameMenu,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun HylianOmniboxBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = OzShapes.pill,
        placeholder = {
            Text(
                "Omnibox (Y): busca en Local, PC Hub y MiNERVA…",
                style = OzTypography.body.copy(fontSize = 13.5.sp),
                color = OzColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                Text(
                    "✕",
                    style = OzTypography.meta,
                    color = OzColors.HylianGold,
                    modifier = Modifier
                        .clickable(onClick = onClear)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = OzColors.HylianSurface,
            unfocusedContainerColor = OzColors.HylianSurface.copy(alpha = 0.88f),
            focusedBorderColor = OzColors.HylianGold,
            unfocusedBorderColor = if (focused) OzColors.HylianGold else OzColors.HylianBorder,
            focusedTextColor = OzColors.TextPrimary,
            unfocusedTextColor = OzColors.TextPrimary,
            cursorColor = OzColors.HylianGold,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
    )
}

@Composable
private fun ContinuarAhoraHero(
    hero: QuickResumeHero?,
    preferences: AppPreferences,
    wide: Boolean,
    onContinueNow: (GameEntity) -> Unit,
    onOpenDetail: (GameEntity) -> Unit,
    onExplore: () -> Unit,
) {
    val context = LocalContext.current
    if (hero == null) {
        Surface(
            shape = OzShapes.hero,
            color = OzColors.HylianSurface,
            border = BorderStroke(1.dp, OzColors.HylianBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CONTINUAR AHORA", style = OzTypography.meta, color = OzColors.HylianGold)
                Text("Tu consola Hylian está lista", style = OzTypography.hero, color = OzColors.TextPrimary)
                Text(
                    "Explora sagas curadas o descarga tu primer título en un toque.",
                    style = OzTypography.body,
                    color = OzColors.TextSecondary,
                )
                Button(
                    onClick = onExplore,
                    shape = OzShapes.pill,
                    colors = ButtonDefaults.buttonColors(containerColor = OzColors.HylianGold, contentColor = OzColors.HylianBg),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Explorar Sagas y Consolas", style = OzTypography.cardTitle)
                }
            }
        }
        return
    }

    val game = hero.game
    val candidates = remember(game.id, game.title, game.thumbnailRepo) {
        ArtworkResolver.tileCandidates(context, preferences, game)
    }
    val console = remember(game.systemId) { ConsolePalette.forSystem(game.systemId) }
    val cleanTitle = remember(game.title) { CatalogFilters.displayTitle(game.title).ifBlank { game.title } }
    var ctaFocused by remember { mutableStateOf(false) }

    val quickLabel = buildString {
        append("CONTINUAR AHORA")
        if (hero.isQuickResume) {
            append(" · QUICK RESUME")
            hero.slot?.let { append(" SLOT $it") }
            hero.coreId?.let { append(" · ${it.uppercase()}") }
        }
    }

    Surface(
        shape = OzShapes.hero,
        color = OzColors.HylianSurface,
        border = BorderStroke(1.dp, OzColors.HylianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.ButtonA || it.key == Key.DirectionCenter)) {
                    onContinueNow(game)
                    true
                } else false
            },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                OzColors.HylianBg.copy(alpha = 0.72f),
                                OzColors.HylianSurface,
                                OzColors.HylianEmerald.copy(alpha = 0.14f),
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier.padding(if (wide) 22.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(if (wide) 126.dp else 104.dp)
                        .aspectRatio(OZ_IMMERSIVE_COVER_ASPECT_RATIO)
                        .clip(OzShapes.medium)
                        .border(1.5.dp, OzColors.HylianGold.copy(alpha = 0.75f), OzShapes.medium),
                ) {
                    OzCoverImage(
                        candidates = candidates,
                        title = cleanTitle,
                        systemId = game.systemId,
                        adaptiveBlur = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(quickLabel, style = OzTypography.meta, color = OzColors.HylianGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cleanTitle, style = OzTypography.hero, color = OzColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${console.fullName} · ${game.region}",
                        style = OzTypography.meta,
                        color = OzColors.HylianEmerald,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onContinueNow(game) },
                            shape = OzShapes.pill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OzColors.HylianGold,
                                contentColor = OzColors.HylianBg,
                            ),
                            border = if (ctaFocused) BorderStroke(2.dp, OzColors.TextPrimary) else null,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .onFocusChanged { ctaFocused = it.isFocused },
                        ) {
                            Text("▶ Continuar Ahora", style = OzTypography.cardTitle)
                        }
                        Button(
                            onClick = { onOpenDetail(game) },
                            shape = OzShapes.pill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OzColors.HylianSurfaceRaised,
                                contentColor = OzColors.TextPrimary,
                            ),
                            border = BorderStroke(1.dp, OzColors.HylianBorder),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Ficha", style = OzTypography.meta)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HylianCarouselSection(
    title: String,
    subtitle: String,
    games: List<GameEntity>,
    preferences: AppPreferences,
    onFocused: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onStart: (GameEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OzSectionHeader(title = title, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(games, key = { it.id }) { game ->
                HylianGameCard(
                    game = game,
                    preferences = preferences,
                    onFocused = onFocused,
                    onOpen = { onOpen(game.id) },
                    onStart = { onStart(game) },
                    modifier = Modifier.width(142.dp),
                )
            }
        }
    }
}

@Composable
private fun OmniboxResultsSection(
    query: String,
    localMatches: List<GameEntity>,
    hubMatches: List<HubRemoteGame>,
    minervaMatches: List<cl.gakuto.kizuna.catalog.CatalogSearchItem>,
    materializingId: String?,
    preferences: AppPreferences,
    onOpenLocal: (String) -> Unit,
    onOpenPcHub: () -> Unit,
    onOpenMinerva: (cl.gakuto.kizuna.catalog.CatalogSearchItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Resultados para \"$query\"", style = OzTypography.section, color = OzColors.HylianGold)

        if (localMatches.isNotEmpty()) {
            OzSectionHeader("En tu dispositivo", "${localMatches.size} locales")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(localMatches, key = { it.id }) { g ->
                    HylianGameCard(
                        game = g,
                        preferences = preferences,
                        onOpen = { onOpenLocal(g.id) },
                        modifier = Modifier.width(136.dp),
                    )
                }
            }
        }

        if (hubMatches.isNotEmpty()) {
            OzSectionHeader("PC Hub LAN", "${hubMatches.size} disponibles en red")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hubMatches.forEach { hubItem ->
                    OmniboxListRow(
                        title = hubItem.cleanTitle.ifBlank { hubItem.title },
                        badge = "PC HUB · ${hubItem.consoleKey.uppercase()}",
                        accent = OzColors.HylianGold,
                        onClick = onOpenPcHub,
                    )
                }
            }
        }

        if (minervaMatches.isNotEmpty()) {
            OzSectionHeader("MiNERVA + MAME", "${minervaMatches.size} ediciones")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                minervaMatches.forEach { item ->
                    val busy = materializingId == item.id
                    OmniboxListRow(
                        title = if (busy) "Abriendo ${item.title}…" else item.title,
                        badge = "MiNERVA · ${item.platform.uppercase()}",
                        accent = OzColors.HylianEmerald,
                        onClick = { onOpenMinerva(item) },
                    )
                }
            }
        }

        if (localMatches.isEmpty() && hubMatches.isEmpty() && minervaMatches.isEmpty()) {
            Text(
                "Sin coincidencias en Local, PC Hub o MiNERVA.",
                style = OzTypography.body,
                color = OzColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun OmniboxListRow(
    title: String,
    badge: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        shape = OzShapes.medium,
        color = OzColors.HylianSurface,
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) OzColors.HylianGold else OzColors.HylianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                style = OzTypography.cardTitle,
                color = OzColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(badge, style = OzTypography.meta, color = accent)
        }
    }
}

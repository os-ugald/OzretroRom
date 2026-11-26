package cl.gakuto.kizuna.ui.compose.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.gakuto.kizuna.catalog.CatalogSearchItem
import cl.gakuto.kizuna.catalog.GameMood
import cl.gakuto.kizuna.catalog.VaultCatalogSearchEngine
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.HylianGameCard
import cl.gakuto.kizuna.ui.compose.components.OzChamferShape
import cl.gakuto.kizuna.ui.compose.components.OzCoverImage
import cl.gakuto.kizuna.ui.compose.components.OzHudButton
import cl.gakuto.kizuna.ui.compose.components.OzHudCard
import cl.gakuto.kizuna.ui.compose.components.OzHudChip
import cl.gakuto.kizuna.ui.compose.components.OzHudSearchField
import cl.gakuto.kizuna.ui.compose.components.OzHudSectionHeader
import cl.gakuto.kizuna.ui.compose.components.OzHudTag
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver
import kotlinx.coroutines.launch

/**
 * Core View 3 ("SEARCH & ADD GAME" — faithful to 06 SEARCH / ADD GAME mockup):
 * - Large chamfered search input + integrated filter action
 * - Quick chamfered suggestions + highlighted "AÑADIR JUEGO" card with "IMPORT ROM"
 * - Quick platform chamfered chips + horizontal chamfered result rows + "OZ AI RECOMMENDS"
 */
@Composable
fun OzSearchAddScreen(
    viewModel: OzViewModel,
    focusCommand: Int,
    onOpenGame: (String) -> Unit,
    onStartGameMenu: (cl.gakuto.kizuna.data.GameEntity) -> Unit,
    onOpenFullCatalog: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val query by viewModel.catalogQuery.collectAsStateWithLifecycle()
    val platformFilter by viewModel.catalogPlatformFilter.collectAsStateWithLifecycle()
    val results by viewModel.catalogSearchResults.collectAsStateWithLifecycle()
    val searching by viewModel.catalogSearching.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val localGames by viewModel.local.collectAsStateWithLifecycle()
    val moodPicks by viewModel.moodPicks.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    var materializingId by remember { mutableStateOf<String?>(null) }
    var showPlatformRow by remember { mutableStateOf(true) }
    var moodRollOffset by remember { mutableStateOf(0) }
    val favoriteIds = remember(favorites) { favorites.mapTo(hashSetOf()) { it.id } }

    LaunchedEffect(focusCommand) {
        if (focusCommand > 0) runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(Unit) {
        if (moodPicks.isEmpty()) viewModel.rollMoodPicks(GameMood.QUICK, 0)
    }

    val romFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.preferences.globalRomTreeUri = uri
            viewModel.prepareCompleteCatalog()
        }
    }

    val needle = query.trim().lowercase()
    val localMatches = remember(localGames, needle, platformFilter) {
        if (needle.length < 2) emptyList()
        else localGames.filter {
            (platformFilter.isBlank() || it.systemId.equals(platformFilter, ignoreCase = true)) &&
                (it.title.lowercase().contains(needle) || it.platform.lowercase().contains(needle))
        }.take(6)
    }

    val quickQueries = remember {
        listOf("Chrono Cross", "Final Fantasy VII", "Metal Slug 3", "Zelda", "Symphony of the Night")
    }
    val aiCarousel = remember(moodPicks, localGames) {
        if (moodPicks.isNotEmpty()) moodPicks.take(4) else localGames.take(4)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OzColors.Bg),
    ) {
        val wide = maxWidth >= 780.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (wide) 24.dp else 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OzHudSectionHeader(
                    title = "SEARCH & ADD GAME",
                    actionLabel = "Catálogo MiNERVA",
                    onAction = onOpenFullCatalog,
                )
            }

            item {
                OzHudSearchField(
                    value = query,
                    onValueChange = viewModel::setCatalogQuery,
                    placeholder = "Buscar juegos, plataformas o géneros…",
                    focusRequester = focusRequester,
                    onClear = viewModel::clearCatalogSearch,
                    trailingAction = {
                        Box(
                            modifier = Modifier
                                .clip(OzChamferShape(6.dp))
                                .background(OzColors.SurfaceElevated)
                                .border(1.dp, OzColors.Border, OzChamferShape(6.dp))
                                .clickable { showPlatformRow = !showPlatformRow }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text("☰", style = OzTypography.meta, color = OzColors.AccentPrimary)
                        }
                    },
                )
            }

            // Suggestions + ADD ANY GAME / IMPORT ROM card
            item {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SUGERENCIAS", style = OzTypography.meta, color = OzColors.TextSecondary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(quickQueries) { q ->
                                    OzHudChip(label = q, selected = query.equals(q, ignoreCase = true), onClick = { viewModel.setCatalogQuery(q) })
                                }
                            }
                        }
                        AddGameSpotlightCard(
                            onImportRom = { romFolderPicker.launch(viewModel.preferences.globalRomTreeUri) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("SUGERENCIAS", style = OzTypography.meta, color = OzColors.TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quickQueries) { q ->
                                OzHudChip(label = q, selected = query.equals(q, ignoreCase = true), onClick = { viewModel.setCatalogQuery(q) })
                            }
                        }
                        AddGameSpotlightCard(
                            onImportRom = { romFolderPicker.launch(viewModel.preferences.globalRomTreeUri) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (showPlatformRow) {
                item {
                    Text("PLATAFORMA RÁPIDA", style = OzTypography.meta, color = OzColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VaultCatalogSearchEngine.PLATFORMS.take(10), key = { it.id }) { p ->
                            OzHudChip(
                                label = p.label,
                                selected = platformFilter == p.id,
                                onClick = {
                                    viewModel.setCatalogPlatformFilter(if (platformFilter == p.id) "" else p.id)
                                },
                            )
                        }
                    }
                }
            }

            // Local matches (instant)
            if (localMatches.isNotEmpty()) {
                item {
                    OzHudSectionHeader(title = "En tu biblioteca (${localMatches.size})")
                }
                items(localMatches, key = { "loc-${it.id}" }) { game ->
                    val meta = remember(game.systemId) { ConsolePalette.forSystem(game.systemId) }
                    val candidates = remember(game.id) { ArtworkResolver.tileCandidates(context, viewModel.preferences, game) }
                    SearchHorizontalRow(
                        title = game.title,
                        platformLabel = meta.shortLabel,
                        subtitle = "En tu bóveda · ${game.genre}",
                        candidates = candidates,
                        systemId = game.systemId,
                        isFavorite = game.id in favoriteIds,
                        actionGlyph = "▶",
                        onRowClick = { onOpenGame(game.id) },
                        onActionClick = { onOpenGame(game.id) },
                        onToggleFav = { viewModel.toggleFavorite(game.id) },
                    )
                }
            }

            // MiNERVA + Catalog results
            if (searching) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = OzColors.AccentPrimary)
                        Text("Consultando índice MiNERVA…", style = OzTypography.meta, color = OzColors.AccentPrimary)
                    }
                }
            }

            if (results.isNotEmpty()) {
                item {
                    OzHudSectionHeader(
                        title = "Resultados de catálogo (${results.size})",
                        actionLabel = "Ver todo",
                        onAction = onOpenFullCatalog,
                    )
                }
                items(results.take(12), key = { it.id }) { item ->
                    val meta = remember(item.platform) { ConsolePalette.forSystem(item.platform) }
                    val busy = materializingId == item.id
                    SearchHorizontalRow(
                        title = item.title,
                        platformLabel = meta.shortLabel,
                        subtitle = "${item.provider} · ${formatCatalogSize(item.size)}",
                        candidates = emptyList(),
                        systemId = item.platform,
                        isFavorite = false,
                        actionGlyph = if (busy) "…" else "+",
                        onRowClick = {
                            if (materializingId == null) {
                                scope.launch {
                                    materializingId = item.id
                                    runCatching { viewModel.materializeCatalogItem(item) }
                                        .onSuccess { onOpenGame(it.id) }
                                    materializingId = null
                                }
                            }
                        },
                        onActionClick = {
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
            }

            // OZ AI RECOMMENDS
            if (aiCarousel.isNotEmpty()) {
                item {
                    OzHudCard(cut = 12.dp, elevated = true) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("OZ AI RECOMMENDS", style = OzTypography.section, color = OzColors.AccentPrimary)
                                    OzHudTag(text = "BETA", accent = OzColors.AccentSecondary)
                                }
                                Text(
                                    text = "REFRESH ↻",
                                    style = OzTypography.hudTag,
                                    color = OzColors.AccentPrimary,
                                    modifier = Modifier
                                        .clickable {
                                            moodRollOffset += 1
                                            val mood = GameMood.entries[moodRollOffset % GameMood.entries.size]
                                            viewModel.rollMoodPicks(mood, moodRollOffset)
                                        }
                                        .padding(6.dp),
                                )
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(aiCarousel, key = { "ai-${it.id}" }) { game ->
                                    HylianGameCard(
                                        game = game,
                                        preferences = viewModel.preferences,
                                        isFavorite = game.id in favoriteIds,
                                        onToggleFavorite = { viewModel.toggleFavorite(game.id) },
                                        onOpen = { onOpenGame(game.id) },
                                        onStart = { onStartGameMenu(game) },
                                        modifier = Modifier.width(if (wide) 148.dp else 124.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AddGameSpotlightCard(
    onImportRom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = remember { OzChamferShape(10.dp) }
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        OzColors.AccentSecondary.copy(alpha = 0.22f),
                        OzColors.SurfaceElevated,
                    ),
                ),
            )
            .border(1.dp, OzColors.AccentSecondary.copy(alpha = 0.65f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("AÑADIR JUEGO", style = OzTypography.section.copy(fontSize = 14.sp), color = OzColors.AccentPrimary)
            OzHudTag(text = "SAF", accent = OzColors.AccentSecondary)
        }
        Text(
            "Importa tus ROMs locales o carpeta de juegos; Oz organiza carátulas y núcleos automáticamente.",
            style = OzTypography.body.copy(fontSize = 12.5.sp),
            color = OzColors.TextSecondary,
        )
        OzHudButton(
            text = "Import ROM",
            primary = true,
            onClick = onImportRom,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SearchHorizontalRow(
    title: String,
    platformLabel: String,
    subtitle: String,
    candidates: List<String>,
    systemId: String,
    isFavorite: Boolean,
    actionGlyph: String,
    onRowClick: () -> Unit,
    onActionClick: () -> Unit,
    onToggleFav: (() -> Unit)? = null,
) {
    OzHudCard(cut = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp)
                .clickable(onClick = onRowClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OzCoverImage(
                candidates = candidates,
                title = title,
                systemId = systemId,
                adaptiveBlur = true,
                showTitleOnFallback = false,
                modifier = Modifier.size(52.dp).clip(OzChamferShape(6.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title.uppercase(),
                    style = OzTypography.cardTitle,
                    color = OzColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OzHudTag(text = platformLabel, accent = OzColors.AccentPrimary)
                    Text(
                        text = subtitle,
                        style = OzTypography.body.copy(fontSize = 12.sp),
                        color = OzColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onToggleFav != null) {
                Text(
                    text = if (isFavorite) "♥" else "♡",
                    style = OzTypography.hero.copy(fontSize = 16.sp),
                    color = if (isFavorite) OzColors.AccentSecondary else OzColors.TextSecondary,
                    modifier = Modifier.clickable(onClick = onToggleFav).padding(6.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(OzChamferShape(6.dp))
                    .background(OzColors.SurfaceElevated)
                    .border(1.dp, OzColors.AccentPrimary.copy(alpha = 0.6f), OzChamferShape(6.dp))
                    .clickable(onClick = onActionClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionGlyph, style = OzTypography.hudButton, color = OzColors.AccentPrimary)
            }
        }
    }
}

private fun formatCatalogSize(bytes: Long): String = when {
    bytes <= 0L -> "Catálogo"
    bytes >= 1024L * 1024L * 1024L -> String.format("%.1f GB", bytes / (1024f * 1024f * 1024f))
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
    else -> "${bytes / 1024L} KB"
}

package cl.gakuto.kizuna.ui.compose.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.gakuto.kizuna.catalog.CatalogSearchItem
import cl.gakuto.kizuna.catalog.DynamicSmartList
import cl.gakuto.kizuna.catalog.VaultCatalogSearchEngine
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.CollectionKind
import cl.gakuto.kizuna.data.CollectionSummary
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.OZ_NDS_COVER_ASPECT_RATIO
import cl.gakuto.kizuna.ui.compose.components.OzCoverImage
import cl.gakuto.kizuna.ui.compose.components.OzFourCoverMosaic
import cl.gakuto.kizuna.ui.compose.components.OzSectionHeader
import cl.gakuto.kizuna.ui.compose.components.gridColumnsForDensity
import cl.gakuto.kizuna.ui.compose.surface.OzSurfaceTile
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.LibretroThumbnailHelper
import kotlinx.coroutines.launch

@Composable
fun CatalogHubScreen(
    viewModel: OzViewModel,
    onOpenGame: (String) -> Unit,
    onPlayGame: (GameEntity) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenSmartList: (String) -> Unit,
) {
    val query by viewModel.catalogQuery.collectAsStateWithLifecycle()
    val platformFilter by viewModel.catalogPlatformFilter.collectAsStateWithLifecycle()
    val searchResults by viewModel.catalogSearchResults.collectAsStateWithLifecycle()
    val searching by viewModel.catalogSearching.collectAsStateWithLifecycle()
    val shardCount by viewModel.catalogShardCount.collectAsStateWithLifecycle()
    val summaries by viewModel.collectionSummaries.collectAsStateWithLifecycle()
    val smartLists by viewModel.dynamicSmartLists.collectAsStateWithLifecycle()
    val density by viewModel.gameGridDensity.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var materializingId by remember { mutableStateOf<String?>(null) }

    val hasActiveSearch = query.trim().length >= 2 || platformFilter.isNotBlank()

    val systemsSummaries = remember(summaries) {
        summaries.filter { it.collection.collectionKind == CollectionKind.SYSTEM }
    }
    val curatedSummaries = remember(summaries) {
        summaries.filter { it.collection.collectionKind == CollectionKind.CURATED }
    }
    val personalSummaries = remember(summaries) {
        summaries.filter {
            it.collection.collectionKind == CollectionKind.AUTOMATIC ||
                it.collection.collectionKind == CollectionKind.MANUAL
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OzColors.DeepSpace),
    ) {
        val wide = maxWidth >= 720.dp || maxWidth > maxHeight
        val cols = gridColumnsForDensity(density, wide, maxWidth.value)

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: Title + shard index status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CATÁLOGO Y LISTAS", style = OzTypography.hero, color = OzColors.TextPrimary)
                    Text(
                        if (shardCount > 0) "Buscador MiNERVA · MAME · IA ($shardCount ROMs)"
                        else "Preparando índice rápido MiNERVA…",
                        style = OzTypography.meta,
                        color = OzColors.Cyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasActiveSearch) {
                    Text(
                        "✕ Limpiar",
                        style = OzTypography.meta,
                        color = OzColors.Gold,
                        modifier = Modifier
                            .clip(OzShapes.pill)
                            .background(OzColors.PanelRaised)
                            .border(1.dp, OzColors.Gold.copy(alpha = 0.45f), OzShapes.pill)
                            .clickable { viewModel.clearCatalogSearch() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            // PC-style instant search box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setCatalogQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Buscar en MiNERVA, MAME o Internet Archive…",
                            style = OzTypography.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    shape = OzShapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OzColors.Cyan,
                        unfocusedBorderColor = OzColors.Outline.copy(alpha = 0.4f),
                        focusedContainerColor = OzColors.Panel,
                        unfocusedContainerColor = OzColors.PanelRaised.copy(alpha = 0.72f),
                        focusedTextColor = OzColors.TextPrimary,
                        unfocusedTextColor = OzColors.TextPrimary,
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Text(
                                "✕",
                                style = OzTypography.cardTitle,
                                color = OzColors.TextSecondary,
                                modifier = Modifier
                                    .clickable { viewModel.setCatalogQuery("") }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            // Fast platform selector strip (mirrors PC dropdown chips)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(VaultCatalogSearchEngine.PLATFORMS, key = { it.id }) { plat ->
                    val selected = platformFilter == plat.id
                    val visual = if (plat.id.isBlank()) null else ConsolePalette.forSystem(plat.id)
                    val accent = visual?.accent ?: OzColors.Cyan
                    Text(
                        text = plat.label,
                        style = OzTypography.meta,
                        color = if (selected) accent else OzColors.TextSecondary,
                        modifier = Modifier
                            .clip(OzShapes.pill)
                            .background(if (selected) accent.copy(alpha = 0.2f) else OzColors.PanelRaised.copy(alpha = 0.7f))
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) accent else OzColors.Outline.copy(alpha = 0.3f),
                                shape = OzShapes.pill,
                            )
                            .clickable { viewModel.setCatalogPlatformFilter(plat.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            if (searching) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    color = OzColors.Cyan,
                    trackColor = OzColors.PanelRaised,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            if (hasActiveSearch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val activeLabel = VaultCatalogSearchEngine.PLATFORMS
                        .firstOrNull { it.id == platformFilter }?.label ?: "Todas"
                    Text(
                        if (searching && searchResults.isEmpty()) "Buscando en $activeLabel…"
                        else "${searchResults.size} resultados · $activeLabel",
                        style = OzTypography.meta,
                        color = OzColors.TextSecondary,
                    )
                    Text(
                        "‹ Listas y Sistemas",
                        style = OzTypography.meta,
                        color = OzColors.Cyan,
                        modifier = Modifier
                            .clickable { viewModel.clearCatalogSearch() }
                            .padding(vertical = 2.dp),
                    )
                }

                if (searchResults.isEmpty() && !searching) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                if (shardCount == 0) "Cargando catálogo MiNERVA…"
                                else "Sin coincidencias para \"$query\"",
                                style = OzTypography.section,
                                color = OzColors.TextPrimary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Prueba con otro título o cambia la consola arriba.",
                                style = OzTypography.body,
                                color = OzColors.TextSecondary,
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(searchResults, key = { it.id }) { item ->
                            CatalogSearchTile(
                                item = item,
                                busy = materializingId == item.id,
                                onOpenDetail = {
                                    if (materializingId == null) {
                                        scope.launch {
                                            materializingId = item.id
                                            runCatching { viewModel.materializeCatalogItem(item) }
                                                .onSuccess { entity -> onOpenGame(entity.id) }
                                            materializingId = null
                                        }
                                    }
                                },
                                onQuickPlay = {
                                    if (materializingId == null) {
                                        scope.launch {
                                            materializingId = item.id
                                            runCatching { viewModel.materializeCatalogItem(item) }
                                                .onSuccess { entity -> onPlayGame(entity) }
                                            materializingId = null
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            } else {
                // Organized Discovery View ("Sistemas + Listas dinámicas + Curadas + Bóveda")
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (systemsSummaries.isNotEmpty() || VaultCatalogSearchEngine.PLATFORMS.size > 1) {
                        item {
                            OzSectionHeader(
                                title = "Sistemas",
                                subtitle = "Toca una consola para explorar y descargar ROMs al instante",
                            )
                            Spacer(Modifier.height(10.dp))
                            if (systemsSummaries.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(systemsSummaries, key = { it.collection.id }) { summary ->
                                        CatalogCollectionTile(
                                            summary = summary,
                                            preferences = viewModel.preferences,
                                            onClick = {
                                                val sysId = summary.collection.id.removePrefix("system-")
                                                val searchKey = when (sysId) {
                                                    "arcade-fbneo" -> "arcade"
                                                    "dreamcast" -> "dc"
                                                    "gamecube" -> "gc"
                                                    else -> sysId
                                                }
                                                if (VaultCatalogSearchEngine.PLATFORMS.any { it.id == searchKey && it.offlineSharded }) {
                                                    viewModel.setCatalogPlatformFilter(searchKey)
                                                } else {
                                                    onOpenCollection(summary.collection.id)
                                                }
                                            },
                                        )
                                    }
                                }
                            } else {
                                // Fast MiNERVA sharded consoles even before full Room indexing
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(
                                        VaultCatalogSearchEngine.PLATFORMS.filter { it.id.isNotBlank() && it.offlineSharded },
                                        key = { it.id },
                                    ) { plat ->
                                        val visual = ConsolePalette.forSystem(plat.id)
                                        Surface(
                                            shape = OzShapes.large,
                                            color = OzColors.PanelRaised,
                                            border = BorderStroke(1.dp, visual.accent.copy(alpha = 0.45f)),
                                            modifier = Modifier
                                                .width(156.dp)
                                                .clip(OzShapes.large)
                                                .clickable { viewModel.setCatalogPlatformFilter(plat.id) },
                                        ) {
                                            Column(Modifier.padding(12.dp)) {
                                                Text(visual.shortLabel, style = OzTypography.section, color = visual.accent)
                                                Spacer(Modifier.height(3.dp))
                                                Text(visual.fullName, style = OzTypography.meta, color = OzColors.TextSecondary, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (smartLists.isNotEmpty()) {
                        item {
                            OzSectionHeader(
                                title = "Listas dinámicas",
                                subtitle = "Se actualizan solas según lo que juegas y guardas",
                            )
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(smartLists, key = { it.id }) { smart ->
                                    DynamicSmartListCard(
                                        smartList = smart,
                                        preferences = viewModel.preferences,
                                        onClick = { onOpenSmartList(smart.id) },
                                    )
                                }
                            }
                        }
                    }

                    if (curatedSummaries.isNotEmpty()) {
                        item {
                            OzSectionHeader(
                                title = "Listas curadas por Oz",
                                subtitle = "Selecciones temáticas listas para descargar y jugar",
                            )
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(curatedSummaries, key = { it.collection.id }) { summary ->
                                    CatalogCollectionTile(
                                        summary = summary,
                                        preferences = viewModel.preferences,
                                        onClick = { onOpenCollection(summary.collection.id) },
                                    )
                                }
                            }
                        }
                    }

                    if (personalSummaries.isNotEmpty()) {
                        item {
                            OzSectionHeader(
                                title = "Tu bóveda y listas",
                                subtitle = "Descargados, Favoritos, Recientes y colecciones personales",
                            )
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(personalSummaries, key = { it.collection.id }) { summary ->
                                    CatalogCollectionTile(
                                        summary = summary,
                                        preferences = viewModel.preferences,
                                        onClick = { onOpenCollection(summary.collection.id) },
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CatalogSearchTile(
    item: CatalogSearchItem,
    busy: Boolean,
    onOpenDetail: () -> Unit,
    onQuickPlay: () -> Unit,
) {
    val visual = remember(item.platform) { ConsolePalette.forSystem(item.platform) }
    val candidates = remember(item.id, item.title, item.thumbnailRepo, item.platform) {
        LibretroThumbnailHelper.candidateUrls(
            repo = item.thumbnailRepo.ifBlank { null },
            systemId = item.platform,
            title = item.title,
            folders = listOf("Named_Boxarts", "Named_Titles", "Named_Snaps"),
            maxUrls = 10,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO)
            .clip(OzShapes.medium)
            .background(OzColors.PanelRaised)
            .border(1.dp, visual.accent.copy(alpha = 0.42f), OzShapes.medium)
            .clickable(onClick = onOpenDetail),
    ) {
        OzCoverImage(
            candidates = candidates,
            title = item.title,
            systemId = item.platform,
            contentScale = ContentScale.Crop,
            showTitleOnFallback = true,
            modifier = Modifier.fillMaxSize(),
        )

        // Console accent bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(visual.accent)
                .align(Alignment.TopCenter),
        )

        // Bottom readable title scrim + quick play button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.48f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.90f),
                    ),
                ),
        )

        // Quick play pill at top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(OzColors.DeepSpace.copy(alpha = 0.84f))
                .border(1.dp, OzColors.Cyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .clickable(onClick = onQuickPlay)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = OzColors.Cyan,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.height(11.dp).width(11.dp),
                )
            } else {
                Text("▶", style = OzTypography.meta.copy(fontSize = 9.sp), color = OzColors.Cyan)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 6.dp),
        ) {
            Text(
                item.title,
                style = OzTypography.meta.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(visual.shortLabel, item.edition.takeIf { it.isNotBlank() && it.length <= 16 })
                    .filterNotNull()
                    .joinToString(" · "),
                style = OzTypography.meta.copy(fontSize = 8.sp),
                color = visual.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DynamicSmartListCard(
    smartList: DynamicSmartList,
    preferences: AppPreferences,
    onClick: () -> Unit,
) {
    val accent = remember(smartList.accentHex) {
        ConsolePalette.parseHexColor(smartList.accentHex, OzColors.Cyan)
    }
    Surface(
        shape = OzShapes.large,
        color = OzColors.PanelRaised,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier
            .width(248.dp)
            .height(152.dp)
            .clip(OzShapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .background(OzColors.DeepSpace),
            ) {
                OzFourCoverMosaic(
                    games = smartList.games,
                    preferences = preferences,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .padding(11.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        smartList.badge,
                        style = OzTypography.meta.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = accent,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        smartList.title,
                        style = OzTypography.tileTitle,
                        color = OzColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        smartList.subtitle,
                        style = OzTypography.meta.copy(fontSize = 10.sp),
                        color = OzColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${smartList.games.size} juegos ›",
                    style = OzTypography.meta.copy(fontSize = 10.sp),
                    color = accent,
                )
            }
        }
    }
}

@Composable
private fun CatalogCollectionTile(
    summary: CollectionSummary,
    preferences: AppPreferences,
    onClick: () -> Unit,
) {
    val accent = remember(summary.collection.accent) {
        ConsolePalette.parseHexColor(summary.collection.accent, OzColors.Cyan)
    }
    Surface(
        shape = OzShapes.large,
        color = OzColors.PanelRaised,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        modifier = Modifier
            .width(226.dp)
            .height(144.dp)
            .clip(OzShapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .background(OzColors.DeepSpace),
            ) {
                OzFourCoverMosaic(
                    games = summary.covers,
                    preferences = preferences,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(11.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    summary.collection.name.uppercase(),
                    style = OzTypography.cardTitle,
                    color = OzColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text("${summary.gameCount} títulos", style = OzTypography.meta, color = accent)
                if (summary.localCount > 0) {
                    Text(
                        "${summary.localCount} en bóveda",
                        style = OzTypography.meta.copy(fontSize = 10.sp),
                        color = OzColors.Positive,
                    )
                }
            }
        }
    }
}

@Composable
fun SmartListDetailScreen(
    viewModel: OzViewModel,
    listId: String,
    onBack: () -> Unit,
    onGame: (String) -> Unit,
    onStartGame: (GameEntity) -> Unit,
) {
    val smartLists by viewModel.dynamicSmartLists.collectAsStateWithLifecycle()
    val density by viewModel.gameGridDensity.collectAsStateWithLifecycle()
    val smart = remember(smartLists, listId) { smartLists.firstOrNull { it.id == listId } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OzColors.DeepSpace),
    ) {
        val wide = maxWidth >= 720.dp || maxWidth > maxHeight
        val cols = gridColumnsForDensity(density, wide, maxWidth.value)

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        smart?.title ?: "Lista dinámica",
                        style = OzTypography.hero,
                        color = OzColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        smart?.subtitle ?: "Selección adaptativa",
                        style = OzTypography.meta,
                        color = OzColors.Cyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "‹ Volver",
                    style = OzTypography.meta,
                    color = OzColors.Cyan,
                    modifier = Modifier
                        .clip(OzShapes.pill)
                        .background(OzColors.PanelRaised)
                        .border(1.dp, OzColors.Cyan.copy(alpha = 0.45f), OzShapes.pill)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (smart == null || smart.games.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando recomendaciones…", style = OzTypography.body, color = OzColors.TextSecondary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(smart.games, key = { it.id }) { game ->
                        OzSurfaceTile(
                            game = game,
                            preferences = viewModel.preferences,
                            selected = false,
                            onFocused = {},
                            onOpen = { onGame(game.id) },
                            onStart = { onStartGame(game) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO),
                        )
                    }
                }
            }
        }
    }
}

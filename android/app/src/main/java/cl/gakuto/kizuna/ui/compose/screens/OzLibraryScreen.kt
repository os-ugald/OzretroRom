package cl.gakuto.kizuna.ui.compose.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.ui.compose.LibrarySection
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.HylianGameCard
import cl.gakuto.kizuna.ui.compose.components.OzChamferShape
import cl.gakuto.kizuna.ui.compose.components.OzCoverImage
import cl.gakuto.kizuna.ui.compose.components.OzHudCard
import cl.gakuto.kizuna.ui.compose.components.OzHudChip
import cl.gakuto.kizuna.ui.compose.components.OzHudSearchField
import cl.gakuto.kizuna.ui.compose.components.OzHudSectionHeader
import cl.gakuto.kizuna.ui.compose.components.OzHudSegmentedBar
import cl.gakuto.kizuna.ui.compose.components.OzHudSurfaceBox
import cl.gakuto.kizuna.ui.compose.components.OzHudTag
import cl.gakuto.kizuna.ui.compose.components.gridColumnsForDensity
import cl.gakuto.kizuna.ui.compose.components.ozHudBorderBrush
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.LocalOzShellLayout
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShellLayout
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters
import cl.gakuto.kizuna.util.CatalogSort
import java.util.Locale

private val LIBRARY_SECTIONS = listOf(
    LibrarySection.ALL,
    LibrarySection.FAVORITES,
    LibrarySection.RECENT,
    LibrarySection.DOWNLOADED,
    LibrarySection.PLATFORM,
)

private val LIBRARY_SECTION_SHORT_LABELS = listOf("TODOS", "FAV", "RECIENTES", "LOCAL", "CONSOLAS")
private val LIBRARY_SECTION_PORTRAIT_LABELS = listOf("TODOS", "FAVORITOS", "RECIENTES", "DESCARGADOS", "CONSOLAS")

private val LIBRARY_SORT_OPTIONS = listOf(
    CatalogSort.NAME_ASC,
    CatalogSort.NAME_DESC,
    CatalogSort.LAST_PLAYED,
    CatalogSort.ADDED_RECENTLY,
    CatalogSort.FAVORITES_FIRST,
)

/**
 * Clean console chip label avoiding awkward mid-word truncations (e.g. `ATARI-JAGU` → `JAGUAR`).
 */
private fun cleanConsoleChipLabel(systemId: String): String {
    val key = systemId.trim().lowercase(Locale.ROOT)
    val explicit = when (key) {
        "atari-jaguar", "jaguar" -> "JAGUAR"
        "atari-2600", "atari2600" -> "ATARI 2600"
        "atari-5200", "atari5200" -> "ATARI 5200"
        "atari-7800", "atari7800" -> "ATARI 7800"
        "atari-lynx", "lynx" -> "LYNX"
        "neo-geo", "neogeo" -> "NEOGEO"
        "ngp", "ngpc", "neo-geo-pocket" -> "NGP"
        "turbografx-16", "tg16" -> "TG-16"
        "wonderswan", "ws", "wsc" -> "WONDERSWAN"
        "colecovision" -> "COLECO"
        "intellivision" -> "INTELLI"
        "pc-fx", "pcfx" -> "PC-FX"
        "virtual-boy", "vb" -> "V-BOY"
        "3do" -> "3DO"
        else -> null
    }
    if (explicit != null) return explicit
    val meta = ConsolePalette.forSystem(key)
    val fallbackCut = key.uppercase(Locale.ROOT).take(10)
    return if (meta.shortLabel != fallbackCut || key.length <= 10) {
        meta.shortLabel
    } else {
        key.replace('-', ' ').uppercase(Locale.ROOT)
    }
}

/**
 * Core View 2 ("Biblioteca") — clean rewrite driven by `LocalOzShellLayout.current`:
 * - `LANDSCAPE_HANDHELD`: max 2 compact rows (~72dp total, no redundant title) + 5-column cover grid (~78% height).
 * - `PORTRAIT_PHONE`: compact 36dp header + segmented bar + console chips + sort/search + 3-column grid.
 * - `DESKTOP_WIDE`: clean bar + 5–7 column cover grid.
 */
@Composable
fun OzLibraryScreen(
    viewModel: OzViewModel,
    onOpenGame: (String) -> Unit,
    onStartGameMenu: (GameEntity) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val shellLayout = LocalOzShellLayout.current
    val games = viewModel.libraryGames.collectAsLazyPagingItems()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val localGames by viewModel.local.collectAsStateWithLifecycle()
    val section by viewModel.librarySection.collectAsStateWithLifecycle()
    val activePlatform by viewModel.libraryPlatform.collectAsStateWithLifecycle()
    val query by viewModel.libraryQuery.collectAsStateWithLifecycle()
    val sort by viewModel.librarySort.collectAsStateWithLifecycle()
    val density by viewModel.gameGridDensity.collectAsStateWithLifecycle()

    val favoriteIds = remember(favorites) { favorites.mapTo(hashSetOf()) { it.id } }
    var isGridView by remember { mutableStateOf(true) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val selectedSectionIndex = LIBRARY_SECTIONS.indexOf(section).coerceAtLeast(0)
    val consoleChips = remember(viewModel.systems) { viewModel.systems.take(14) }
    val isLandscape = shellLayout == OzShellLayout.LANDSCAPE_HANDHELD

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OzColors.Bg)
            .padding(top = if (isLandscape) 4.dp else 6.dp),
    ) {
        if (isLandscape) {
            // LANDSCAPE_HANDHELD: 2 compact rows (~72dp total), no "OZ LIBRARY" title (rail shows active tab)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OzHudSegmentedBar(
                    items = LIBRARY_SECTION_SHORT_LABELS,
                    selectedIndex = selectedSectionIndex,
                    onSelect = { idx -> viewModel.setLibrarySection(LIBRARY_SECTIONS[idx]) },
                    scrollable = true,
                    modifier = Modifier.weight(1.15f),
                )
                OzHudSearchField(
                    value = query,
                    onValueChange = viewModel::setLibraryQuery,
                    placeholder = "Buscar…",
                    height = 34.dp,
                    onClear = { viewModel.setLibraryQuery("") },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 160.dp),
                )
                OzSortIconButton(
                    expanded = showSortMenu,
                    onExpandedChange = { showSortMenu = it },
                    onSelectSort = viewModel::setLibrarySort,
                    size = 34.dp,
                )
                OzGridListToggle(
                    isGridView = isGridView,
                    onToggle = { isGridView = it },
                    height = 34.dp,
                )
                OzLibraryOverflowButton(
                    expanded = showOverflowMenu,
                    onExpandedChange = { showOverflowMenu = it },
                    onSyncArtwork = viewModel::syncAllArtwork,
                    onPrepareCatalog = viewModel::prepareCompleteCatalog,
                    onOpenSettings = onOpenSettings,
                    size = 34.dp,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Row 2 (30dp): Console chips LazyRow + discrete count pinned on the right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item(key = "all") {
                        OzHudChip(
                            label = "ALL",
                            selected = activePlatform.isNullOrBlank(),
                            compact = true,
                            onClick = { viewModel.setLibraryPlatform(null) },
                        )
                    }
                    items(consoleChips, key = { it.id }) { sys ->
                        val meta = ConsolePalette.forSystem(sys.id)
                        OzHudChip(
                            label = cleanConsoleChipLabel(sys.id),
                            selected = activePlatform == sys.id,
                            compact = true,
                            accent = meta.accent,
                            onClick = {
                                viewModel.setLibraryPlatform(if (activePlatform == sys.id) null else sys.id)
                            },
                        )
                    }
                }
                Text(
                    text = "${games.itemCount} ROMS",
                    style = OzTypography.meta.copy(fontSize = 10.sp),
                    color = OzColors.AccentPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            Spacer(Modifier.height(4.dp))
        } else {
            // PORTRAIT_PHONE & DESKTOP_WIDE
            val hPad = if (shellLayout == OzShellLayout.DESKTOP_WIDE) 20.dp else 14.dp

            // 1. Compact 36dp header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = hPad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OzHudSectionHeader(title = "OZ LIBRARY")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OzHudTag(text = "${games.itemCount}", accent = OzColors.AccentPrimary)
                    OzGridListToggle(
                        isGridView = isGridView,
                        onToggle = { isGridView = it },
                        height = 30.dp,
                    )
                    OzLibraryOverflowButton(
                        expanded = showOverflowMenu,
                        onExpandedChange = { showOverflowMenu = it },
                        onSyncArtwork = viewModel::syncAllArtwork,
                        onPrepareCatalog = viewModel::prepareCompleteCatalog,
                        onOpenSettings = onOpenSettings,
                        size = 30.dp,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // 2. Unified 34dp OzHudSegmentedBar
            OzHudSegmentedBar(
                items = LIBRARY_SECTION_PORTRAIT_LABELS,
                selectedIndex = selectedSectionIndex,
                onSelect = { idx -> viewModel.setLibrarySection(LIBRARY_SECTIONS[idx]) },
                scrollable = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad),
            )

            Spacer(Modifier.height(6.dp))

            // 3. Compact 30dp console chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = hPad),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "all") {
                    OzHudChip(
                        label = "ALL",
                        selected = activePlatform.isNullOrBlank(),
                        compact = true,
                        onClick = { viewModel.setLibraryPlatform(null) },
                    )
                }
                items(consoleChips, key = { it.id }) { sys ->
                    val meta = ConsolePalette.forSystem(sys.id)
                    OzHudChip(
                        label = cleanConsoleChipLabel(sys.id),
                        selected = activePlatform == sys.id,
                        compact = true,
                        accent = meta.accent,
                        onClick = {
                            viewModel.setLibraryPlatform(if (activePlatform == sys.id) null else sys.id)
                        },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // 4. Compact 38dp Sort + Search row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = hPad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    val sortShape = remember { OzChamferShape(6.dp) }
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(sortShape)
                            .background(OzColors.Surface)
                            .border(1.dp, ozHudBorderBrush(if (showSortMenu) 0.95f else 0.32f), sortShape)
                            .clickable { showSortMenu = true }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OzSortVectorGlyph(
                            tint = OzColors.AccentPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = sort.label.uppercase(),
                            style = OzTypography.meta.copy(fontSize = 10.5.sp),
                            color = OzColors.TextPrimary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    OzSortDropdownMenu(
                        expanded = showSortMenu,
                        onDismiss = { showSortMenu = false },
                        onSelect = viewModel::setLibrarySort,
                    )
                }
                OzHudSearchField(
                    value = query,
                    onValueChange = viewModel::setLibraryQuery,
                    placeholder = "Buscar en biblioteca…",
                    onClear = { viewModel.setLibraryQuery("") },
                    modifier = Modifier.weight(1f),
                )
            }

            // 5. Discreet single-line vault counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad + 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${games.itemCount} JUEGOS",
                    style = OzTypography.meta.copy(fontSize = 10.sp),
                    color = OzColors.AccentPrimary,
                )
                Text(
                    text = "${localGames.size} EN BÓVEDA LOCAL",
                    style = OzTypography.meta.copy(fontSize = 10.sp),
                    color = OzColors.TextSecondary,
                )
            }
        }

        // Cover Grid / List content
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val wide = shellLayout != OzShellLayout.PORTRAIT_PHONE
            val cols = gridColumnsForDensity(
                density = density,
                wide = wide,
                maxWidthDp = maxWidth.value,
                shellLayout = shellLayout,
            )
            val contentHPad = when (shellLayout) {
                OzShellLayout.DESKTOP_WIDE -> 20.dp
                OzShellLayout.LANDSCAPE_HANDHELD -> 12.dp
                OzShellLayout.PORTRAIT_PHONE -> 14.dp
            }

            if (games.itemCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OzHudCard(cut = 12.dp) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("BIBLIOTECA SIN COINCIDENCIAS", style = OzTypography.section, color = OzColors.TextPrimary)
                            Text(
                                "Ajusta los filtros de consola o añade ROMs desde Buscar.",
                                style = OzTypography.body,
                                color = OzColors.TextSecondary,
                            )
                        }
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(
                        horizontal = contentHPad,
                        vertical = if (isLandscape) 4.dp else 6.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(games.itemCount) { idx ->
                        val game = games[idx]
                        if (game != null) {
                            HylianGameCard(
                                game = game,
                                preferences = viewModel.preferences,
                                isFavorite = game.id in favoriteIds,
                                onToggleFavorite = { viewModel.toggleFavorite(game.id) },
                                onOpen = { onOpenGame(game.id) },
                                onStart = { onStartGameMenu(game) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = contentHPad, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(games.itemCount) { idx ->
                        val game = games[idx]
                        if (game != null) {
                            OzLibraryListRow(
                                game = game,
                                isFavorite = game.id in favoriteIds,
                                onToggleFavorite = { viewModel.toggleFavorite(game.id) },
                                onOpen = { onOpenGame(game.id) },
                                onMore = { onStartGameMenu(game) },
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OzSortIconButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectSort: (CatalogSort) -> Unit,
    size: Dp,
) {
    val shape = remember { OzChamferShape(6.dp) }
    Box {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(if (expanded) OzColors.AccentPrimary.copy(alpha = 0.2f) else OzColors.Surface)
                .border(1.dp, ozHudBorderBrush(if (expanded) 0.95f else 0.32f), shape)
                .clickable { onExpandedChange(true) },
            contentAlignment = Alignment.Center,
        ) {
            OzSortVectorGlyph(
                tint = if (expanded) OzColors.AccentPrimary else OzColors.TextPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
        OzSortDropdownMenu(
            expanded = expanded,
            onDismiss = { onExpandedChange(false) },
            onSelect = onSelectSort,
        )
    }
}

@Composable
private fun OzSortDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (CatalogSort) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = OzColors.SurfaceElevated,
    ) {
        LIBRARY_SORT_OPTIONS.forEach { opt ->
            DropdownMenuItem(
                text = { Text(opt.label, style = OzTypography.body, color = OzColors.TextPrimary) },
                onClick = {
                    onDismiss()
                    onSelect(opt)
                },
            )
        }
    }
}

@Composable
private fun OzLibraryOverflowButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSyncArtwork: () -> Unit,
    onPrepareCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    size: Dp,
) {
    val btnShape = remember { OzChamferShape(6.dp) }
    Box {
        Box(
            modifier = Modifier
                .size(size)
                .clip(btnShape)
                .background(if (expanded) OzColors.AccentPrimary.copy(alpha = 0.2f) else OzColors.Surface)
                .border(1.dp, ozHudBorderBrush(if (expanded) 0.95f else 0.32f), btnShape)
                .clickable { onExpandedChange(true) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Mantenimiento",
                tint = OzColors.TextPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = OzColors.SurfaceElevated,
        ) {
            DropdownMenuItem(
                text = { Text("Sincronizar carátulas", style = OzTypography.body, color = OzColors.TextPrimary) },
                onClick = {
                    onExpandedChange(false)
                    onSyncArtwork()
                },
            )
            DropdownMenuItem(
                text = { Text("Escanear / preparar catálogo", style = OzTypography.body, color = OzColors.TextPrimary) },
                onClick = {
                    onExpandedChange(false)
                    onPrepareCatalog()
                },
            )
            DropdownMenuItem(
                text = { Text("Abrir Ajustes", style = OzTypography.body, color = OzColors.TextPrimary) },
                onClick = {
                    onExpandedChange(false)
                    onOpenSettings()
                },
            )
        }
    }
}

@Composable
private fun OzGridListToggle(
    isGridView: Boolean,
    onToggle: (Boolean) -> Unit,
    height: Dp,
) {
    val segShape = remember { OzChamferShape(4.dp) }
    val innerHeight = height - 4.dp
    OzHudSurfaceBox(
        cut = 6.dp,
        modifier = Modifier.height(height),
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = innerHeight)
                    .clip(segShape)
                    .background(if (isGridView) OzColors.AccentPrimary.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onToggle(true) },
                contentAlignment = Alignment.Center,
            ) {
                OzGridVectorGlyph(
                    tint = if (isGridView) OzColors.AccentPrimary else OzColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = innerHeight)
                    .clip(segShape)
                    .background(if (!isGridView) OzColors.AccentPrimary.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onToggle(false) },
                contentAlignment = Alignment.Center,
            ) {
                OzListVectorGlyph(
                    tint = if (!isGridView) OzColors.AccentPrimary else OzColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun OzSortVectorGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = 1.6.dp.toPx()
        // Up arrow (left)
        val lx = size.width * 0.30f
        drawLine(tint, Offset(lx, size.height * 0.20f), Offset(lx, size.height * 0.80f), sw, StrokeCap.Round)
        drawLine(tint, Offset(lx - size.width * 0.16f, size.height * 0.36f), Offset(lx, size.height * 0.20f), sw, StrokeCap.Round)
        drawLine(tint, Offset(lx + size.width * 0.16f, size.height * 0.36f), Offset(lx, size.height * 0.20f), sw, StrokeCap.Round)
        // Down arrow (right)
        val rx = size.width * 0.70f
        drawLine(tint, Offset(rx, size.height * 0.20f), Offset(rx, size.height * 0.80f), sw, StrokeCap.Round)
        drawLine(tint, Offset(rx - size.width * 0.16f, size.height * 0.64f), Offset(rx, size.height * 0.80f), sw, StrokeCap.Round)
        drawLine(tint, Offset(rx + size.width * 0.16f, size.height * 0.64f), Offset(rx, size.height * 0.80f), sw, StrokeCap.Round)
    }
}

@Composable
private fun OzGridVectorGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = 1.4.dp.toPx()
        val cell = size.width * 0.34f
        val gap = size.width * 0.14f
        val start = size.width * 0.09f
        repeat(2) { r ->
            repeat(2) { c ->
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(start + c * (cell + gap), start + r * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(1.2.dp.toPx()),
                    style = Stroke(width = sw),
                )
            }
        }
    }
}

@Composable
private fun OzListVectorGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = 1.6.dp.toPx()
        val ys = listOf(0.24f, 0.50f, 0.76f)
        ys.forEach { yf ->
            drawLine(
                color = tint,
                start = Offset(size.width * 0.14f, size.height * yf),
                end = Offset(size.width * 0.86f, size.height * yf),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun OzLibraryListRow(
    game: GameEntity,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    viewModel: OzViewModel,
) {
    val context = LocalContext.current
    val meta = remember(game.systemId) { ConsolePalette.forSystem(game.systemId) }
    val displayTitle = remember(game.title) { CatalogFilters.displayTitle(game.title).ifBlank { game.title } }
    val candidates = remember(game.id) { ArtworkResolver.tileCandidates(context, viewModel.preferences, game) }

    OzHudCard(cut = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OzCoverImage(
                candidates = candidates,
                title = displayTitle,
                systemId = game.systemId,
                adaptiveBlur = true,
                showTitleOnFallback = false,
                modifier = Modifier
                    .size(44.dp, 56.dp)
                    .clip(OzChamferShape(6.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = displayTitle.uppercase(),
                    style = OzTypography.cardTitle,
                    color = OzColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OzHudTag(text = meta.shortLabel, accent = meta.accent)
                    if (game.genre.isNotBlank()) {
                        Text(
                            text = game.genre,
                            style = OzTypography.body.copy(fontSize = 11.5.sp),
                            color = OzColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(OzChamferShape(5.dp))
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Quitar favorito" else "Favorito",
                    tint = if (isFavorite) OzColors.AccentSecondary else OzColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(OzChamferShape(5.dp))
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Opciones",
                    tint = OzColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

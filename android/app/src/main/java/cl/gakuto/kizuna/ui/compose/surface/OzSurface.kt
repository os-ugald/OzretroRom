package cl.gakuto.kizuna.ui.compose.surface

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.launch.SessionActivity
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver
import cl.gakuto.kizuna.util.CatalogFilters
import coil3.compose.AsyncImage

@Composable
fun OzSurfaceBackdrop(
    modifier: Modifier = Modifier,
    focusedGameArtUrl: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(OzColors.HylianBg)) {
        if (focusedGameArtUrl != null) {
            AsyncImage(
                model = focusedGameArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.28f)
                    .blur(84.dp),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            OzColors.HylianBg.copy(alpha = 0.72f),
                            OzColors.HylianSurface.copy(alpha = 0.26f),
                            OzColors.HylianBg,
                        ),
                    ),
                ),
            content = content,
        )
    }
}

@Composable
fun OzHeroSection(
    game: GameEntity?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = if (compact) 100.dp else 200.dp) // Critical fix for squashing
            .padding(
                horizontal = if (compact) 10.dp else 20.dp,
                vertical = if (compact) 8.dp else 16.dp
            )
    ) {
        if (game != null) {
            Text(
                text = game.title.uppercase(),
                style = OzTypography.marquee.copy(
                    fontSize = if (compact) 22.sp else 34.sp, // Even smaller in compact
                    letterSpacing = if (compact) 0.6.sp else 1.2.sp
                ),
                color = OzColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = if (compact) 2.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    game.platform.uppercase(),
                    style = OzTypography.meta.copy(fontSize = if (compact) 9.sp else 11.sp),
                    color = OzColors.Cyan,
                    fontWeight = FontWeight.Bold
                )

                Box(Modifier.size(3.dp).clip(CircleShape).background(OzColors.TextSecondary.copy(alpha = 0.4f)))

                if (game.year != null) {
                    Text(
                        game.year.toString(),
                        style = OzTypography.meta.copy(fontSize = if (compact) 9.sp else 11.sp),
                        color = OzColors.TextSecondary
                    )
                    Box(Modifier.size(3.dp).clip(CircleShape).background(OzColors.TextSecondary.copy(alpha = 0.4f)))
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.size(if (compact) 10.dp else 12.dp).border(
                            1.dp,
                            OzColors.TextSecondary.copy(alpha = 0.6f),
                            RoundedCornerShape(2.dp)
                        )
                    )
                    Text(
                        "1 JUGADOR",
                        style = OzTypography.meta.copy(fontSize = if (compact) 8.sp else 11.sp),
                        color = OzColors.TextSecondary
                    )
                }
            }
        } else {
            Text(
                "OZ RETROROM",
                style = OzTypography.marquee.copy(fontSize = if (compact) 22.sp else 28.sp),
                color = OzColors.TextPrimary,
            )
            Text(
                "BIBLIOTECA · COLECCIONES · EMULACIÓN",
                style = OzTypography.meta.copy(fontSize = if (compact) 9.sp else 11.sp),
                color = OzColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

enum class OzTileSize(val key: String, val widthCells: Int, val heightCells: Int) {
    SMALL("1x1", 1, 1),
    WIDE("2x1", 2, 1),
    LARGE("2x2", 2, 2);

    fun next(): OzTileSize = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(value: String?): OzTileSize =
            entries.firstOrNull { it.key == value } ?: SMALL
    }
}

data class OzTilePlacement(
    val gameId: String,
    val page: Int,
    val slot: Int,
    val size: OzTileSize,
)

private fun defaultPlacement(
    index: Int,
    game: GameEntity,
    pageCapacity: Int,
): OzTilePlacement {
    val page = index / pageCapacity
    val slot = index % pageCapacity
    return OzTilePlacement(game.id, page, slot, OzTileSize.SMALL)
}

private fun loadPlacements(
    games: List<GameEntity>,
    preferences: AppPreferences,
    pageCapacity: Int,
): List<OzTilePlacement> =
    games.mapIndexed { index, game ->
        preferences.surfacePlacement(game.id)?.let { saved ->
            OzTilePlacement(
                gameId = game.id,
                page = saved.page,
                slot = saved.slot,
                size = OzTileSize.fromKey(saved.size),
            )
        } ?: defaultPlacement(index, game, pageCapacity)
    }

private fun savePlacement(
    preferences: AppPreferences,
    placement: OzTilePlacement,
) {
    preferences.setSurfacePlacement(
        gameId = placement.gameId,
        page = placement.page,
        slot = placement.slot,
        size = placement.size.key,
    )
}

private data class OzPackedCell(
    val page: Int,
    val slot: Int,
    val col: Int,
    val row: Int,
    val widthCells: Int,
    val heightCells: Int,
)

private fun reflowPlacements(
    source: List<OzTilePlacement>,
    columns: Int,
    rows: Int,
    priorityGameId: String? = null,
): List<OzTilePlacement> {
    val capacity = columns * rows
    val occupancy = mutableListOf<Array<BooleanArray>>()

    fun page(index: Int): Array<BooleanArray> {
        while (occupancy.size <= index) {
            occupancy += Array(rows) { BooleanArray(columns) }
        }
        return occupancy[index]
    }

    fun fits(
        pageIndex: Int,
        slot: Int,
        widthCells: Int,
        heightCells: Int,
    ): Boolean {
        val row = slot / columns
        val col = slot % columns
        if (col + widthCells > columns || row + heightCells > rows) return false
        val grid = page(pageIndex)
        for (r in row until row + heightCells) {
            for (c in col until col + widthCells) {
                if (grid[r][c]) return false
            }
        }
        return true
    }

    fun reserve(
        pageIndex: Int,
        slot: Int,
        widthCells: Int,
        heightCells: Int,
    ) {
        val row = slot / columns
        val col = slot % columns
        val grid = page(pageIndex)
        for (r in row until row + heightCells) {
            for (c in col until col + widthCells) grid[r][c] = true
        }
    }

    val ordered = source.sortedWith(
        compareBy<OzTilePlacement> { if (it.gameId == priorityGameId) 0 else 1 }
            .thenBy { it.page }
            .thenBy { it.slot },
    )

    val packed = mutableListOf<OzTilePlacement>()

    ordered.forEach { placement ->
        val width = placement.size.widthCells.coerceIn(1, columns)
        val height = placement.size.heightCells.coerceIn(1, rows)
        var targetPage = placement.page.coerceAtLeast(0)
        var foundSlot: Int? = null

        while (foundSlot == null) {
            val preferred = if (targetPage == placement.page) {
                placement.slot.coerceIn(0, capacity - 1)
            } else {
                0
            }
            val candidates = (preferred until capacity) + (0 until preferred)

            foundSlot = candidates.firstOrNull { slot ->
                fits(targetPage, slot, width, height)
            }

            if (foundSlot == null) targetPage += 1
        }

        reserve(targetPage, foundSlot, width, height)
        packed += placement.copy(page = targetPage, slot = foundSlot)
    }

    return source.map { original ->
        packed.first { it.gameId == original.gameId }
    }
}

@Composable
fun OzHomeMosaic(
    games: List<GameEntity>,
    preferences: AppPreferences,
    columns: Int,
    focusedGame: GameEntity?,
    onFocused: (GameEntity) -> Unit,
    onOpen: (GameEntity) -> Unit,
    onStart: (GameEntity) -> Unit,
    modifier: Modifier = Modifier,
    pageCommand: Int = 0,
    pageDirection: Int = 0,
    organizeGameId: String? = null,
    onOrganizeFinished: () -> Unit = {},
) {
    /*
     * Surface 03A — Spatial Home.
     *
     * Unlike LazyVerticalGrid spans, this layout owns width AND height cells.
     * A 2x2 tile therefore occupies four real cells and neighboring content is
     * reflowed around it instead of leaving an orphan tile or visual hole.
     */
    val rows = if (columns <= 3) 4 else 2
    val pageCapacity = columns * rows

    var placements by remember(games.map { it.id }, columns) {
        mutableStateOf(
            reflowPlacements(
                loadPlacements(games, preferences, pageCapacity),
                columns = columns,
                rows = rows,
            ),
        )
    }
    var currentPage by remember { mutableStateOf(0) }
    var editSnapshot by remember { mutableStateOf<List<OzTilePlacement>?>(null) }

    fun reflow(priorityGameId: String? = null) {
        placements = reflowPlacements(
            placements,
            columns = columns,
            rows = rows,
            priorityGameId = priorityGameId,
        )
    }

    fun updatePlacement(
        gameId: String,
        transform: (OzTilePlacement) -> OzTilePlacement,
    ) {
        placements = placements.map { placement ->
            if (placement.gameId == gameId) transform(placement) else placement
        }
        reflow(priorityGameId = gameId)
    }

    val maxPage = maxOf(0, placements.maxOfOrNull { it.page } ?: 0)

    LaunchedEffect(pageCommand, maxPage) {
        if (pageCommand > 0 && pageDirection != 0) {
            currentPage = (currentPage + pageDirection).coerceIn(0, maxPage)
        }
    }

    LaunchedEffect(organizeGameId) {
        if (organizeGameId != null) {
            // Quick-menu "Tamaño" can update preferences before entering organize mode.
            // Pull that persisted value into the live surface before taking the snapshot.
            preferences.surfacePlacement(organizeGameId)?.let { saved ->
                placements = placements.map { placement ->
                    if (placement.gameId == organizeGameId) {
                        placement.copy(
                            page = saved.page,
                            slot = saved.slot,
                            size = OzTileSize.fromKey(saved.size),
                        )
                    } else {
                        placement
                    }
                }
                reflow(priorityGameId = organizeGameId)
            }

            editSnapshot = placements.map { it.copy() }
            placements.firstOrNull { it.gameId == organizeGameId }?.let {
                currentPage = it.page
            }
        } else {
            editSnapshot = null
        }
    }

    val pageItems = placements
        .filter { it.page == currentPage }
        .sortedBy { it.slot }
        .mapNotNull { placement ->
            games.firstOrNull { it.id == placement.gameId }
                ?.let { game -> placement to game }
        }

    Column(modifier) {
        if (organizeGameId != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "MODO ORGANIZAR · D-PAD mover · X tamaño · A guardar · B cancelar",
                    style = OzTypography.meta,
                    color = OzColors.Cyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(5.dp))
        }

        Layout(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            content = {
                pageItems.forEach { item ->
                    val placement = item.first
                    val game = item.second
                    val organizing = organizeGameId == game.id

                    OzSurfaceTile(
                        game = game,
                        preferences = preferences,
                        selected = focusedGame?.id == game.id,
                        organizing = organizing,
                        onFocused = { onFocused(game) },
                        onOpen = { onOpen(game) },
                        onStart = { onStart(game) },
                        onOrganizeKey = { key ->
                            if (!organizing) {
                                false
                            } else {
                                when (key) {
                                    Key.DirectionLeft -> {
                                        updatePlacement(game.id) {
                                            it.copy(slot = (it.slot - 1).coerceAtLeast(0))
                                        }
                                        currentPage = placements.first { it.gameId == game.id }.page
                                        true
                                    }

                                    Key.DirectionRight -> {
                                        updatePlacement(game.id) {
                                            it.copy(slot = (it.slot + 1).coerceAtMost(pageCapacity - 1))
                                        }
                                        currentPage = placements.first { it.gameId == game.id }.page
                                        true
                                    }

                                    Key.DirectionUp -> {
                                        updatePlacement(game.id) {
                                            it.copy(slot = (it.slot - columns).coerceAtLeast(0))
                                        }
                                        currentPage = placements.first { it.gameId == game.id }.page
                                        true
                                    }

                                    Key.DirectionDown -> {
                                        updatePlacement(game.id) {
                                            it.copy(slot = (it.slot + columns).coerceAtMost(pageCapacity - 1))
                                        }
                                        currentPage = placements.first { it.gameId == game.id }.page
                                        true
                                    }

                                    Key.ButtonX -> {
                                        updatePlacement(game.id) {
                                            it.copy(size = it.size.next())
                                        }
                                        currentPage = placements.first { it.gameId == game.id }.page
                                        true
                                    }

                                    Key.ButtonL1 -> {
                                        updatePlacement(game.id) {
                                            it.copy(page = (it.page - 1).coerceAtLeast(0), slot = 0)
                                        }
                                        currentPage = placements.first { it.gameId == game.id }.page
                                        true
                                    }

                                    Key.ButtonR1 -> {
                                        updatePlacement(game.id) {
                                            it.copy(page = it.page + 1, slot = 0)
                                        }
                                        currentPage = placements.first { it.gameId == game.id }.page
                                        true
                                    }

                                    Key.ButtonA,
                                    Key.Enter,
                                    Key.DirectionCenter -> {
                                        // Persist the entire reflow, not only the active tile.
                                        placements.forEach { savePlacement(preferences, it) }
                                        editSnapshot = null
                                        onOrganizeFinished()
                                        true
                                    }

                                    Key.ButtonB,
                                    Key.Back -> {
                                        editSnapshot?.let { original ->
                                            placements = original
                                            original.firstOrNull { it.gameId == game.id }?.let {
                                                currentPage = it.page
                                            }
                                        }
                                        editSnapshot = null
                                        onOrganizeFinished()
                                        true
                                    }

                                    else -> false
                                }
                            }
                        },
                    )
                }
            },
        ) { measurables, constraints ->
            val gapPx = 10.dp.roundToPx()
            val usableWidth =
                (constraints.maxWidth - gapPx * (columns - 1)).coerceAtLeast(columns)
            val usableHeight =
                (constraints.maxHeight - gapPx * (rows - 1)).coerceAtLeast(rows)
            val cellWidth = usableWidth / columns
            val cellHeight = usableHeight / rows

            val pagePlacements = pageItems.map { it.first }
            val placeables = measurables.mapIndexed { index, measurable ->
                val placement = pagePlacements[index]
                val widthCells = placement.size.widthCells.coerceIn(1, columns)
                val heightCells = placement.size.heightCells.coerceIn(1, rows)
                val width = cellWidth * widthCells + gapPx * (widthCells - 1)
                val height = cellHeight * heightCells + gapPx * (heightCells - 1)

                measurable.measure(
                    Constraints.fixed(
                        width.coerceAtLeast(1),
                        height.coerceAtLeast(1),
                    ),
                )
            }

            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->
                    val placement = pagePlacements[index]
                    val row = placement.slot / columns
                    val col = placement.slot % columns
                    placeable.placeRelative(
                        x = col * (cellWidth + gapPx),
                        y = row * (cellHeight + gapPx),
                    )
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        OzPageDots(
            pageCount = maxPage + 1,
            currentPage = currentPage,
            onPageSelected = { currentPage = it }
        )
    }
}

@Composable
private fun OzPageDots(
    pageCount: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { page ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .clickable { onPageSelected(page) }
                    .width(if (page == currentPage) 20.dp else 7.dp)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (page == currentPage) OzColors.Cyan
                        else OzColors.Outline.copy(alpha = .42f),
                    ),
            )
        }
    }
}

@Composable
fun OzSurfaceTile(
    game: GameEntity,
    preferences: AppPreferences,
    selected: Boolean,
    organizing: Boolean = false,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onOrganizeKey: (Key) -> Boolean = { false },
) {
    val context = LocalContext.current
    val displayTitle = remember(game.title) { CatalogFilters.displayTitle(game.title) }
    val consoleVisual = remember(game.systemId) { cl.gakuto.kizuna.ui.compose.theme.ConsolePalette.forSystem(game.systemId) }
    var focused by remember(game.id) { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = when {
            organizing -> 1.075f
            focused || selected -> 1.05f
            else -> 1f
        },
        animationSpec = tween(150),
        label = "oz-surface-focus",
    )

    val candidates = remember(
        context,
        game.id,
        game.title,
        game.thumbnailRepo,
        preferences.artworkMode,
        preferences.localArtworkTreeUri,
        preferences.gameArtworkMode(game.id),
        preferences.customArtworkUri(game.id),
        preferences.customArtworkUrl(game.id),
        preferences.syncedArtworkLocalUri(game.id),
    ) {
        ArtworkResolver.tileCandidates(context, preferences, game)
    }

    Box(
        modifier
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(OzShapes.medium)
            .background(OzColors.PanelRaised.copy(alpha = .64f))
            .border(
                BorderStroke(
                    if (organizing) 3.dp else if (focused || selected) 2.5.dp else 1.dp,
                    when {
                        organizing -> OzColors.Magenta
                        focused || selected -> OzColors.HylianGold
                        else -> OzColors.HylianBorder
                    },
                ),
                OzShapes.medium,
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else if (organizing && onOrganizeKey(event.key)) {
                    true
                } else {
                    when (event.key) {
                        Key.ButtonStart -> {
                            onStart()
                            true
                        }

                        Key.ButtonA,
                        Key.Enter,
                        Key.DirectionCenter -> {
                            onOpen()
                            true
                        }

                        else -> false
                    }
                }
            }
            .focusable()
            .combinedClickable(
                onLongClickLabel = "Acciones del juego",
                onLongClick = {
                    if (!organizing) {
                        onFocused()
                        onStart()
                    }
                },
                onClick = {
                    onFocused()
                    if (!organizing) onOpen()
                },
            ),
    ) {
        cl.gakuto.kizuna.ui.compose.components.OzCoverImage(
            candidates = candidates,
            title = displayTitle,
            systemId = game.systemId,
            contentScale = ContentScale.Crop,
            showTitleOnFallback = true,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(consoleVisual.accent.copy(alpha = 0.85f))
                .align(Alignment.TopCenter),
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = maxWidth
            val tinyTile = availableWidth < 90.dp || maxHeight < 70.dp
            val showFullLabel = focused || selected || organizing || !tinyTile

            if (showFullLabel) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                .50f to Color.Transparent,
                                1f to Color.Black.copy(alpha = .88f),
                            ),
                        ),
                )

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        displayTitle,
                        style = if (availableWidth < 120.dp) OzTypography.meta.copy(fontSize = 10.sp) else OzTypography.tileTitle,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (organizing) "MOVIENDO" else consoleVisual.shortLabel.uppercase(),
                        style = OzTypography.meta.copy(fontSize = 8.sp),
                        color = consoleVisual.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun OzQuickMenu(
    game: GameEntity,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenDetails: () -> Unit,
    onMove: () -> Unit = onOpenDetails,
    onResize: () -> Unit = onOpenDetails,
    onResizeTo: (OzTileSize) -> Unit = { onResize() },
    onOpenCollection: (String) -> Unit = {},
    onArtwork: () -> Unit = {},
    onCollection: () -> Unit = {},
    onHeroMedia: () -> Unit = {},
    onEmulator: () -> Unit = {},
    onInfo: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val displayTitle = remember(game.title) { CatalogFilters.displayTitle(game.title) }
    var showSizes by remember(game.id, visible) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible, showSizes) {
        if (visible) {
            focusRequester.requestFocus()
        }
    }

    AnimatedVisibility(visible = visible, modifier = modifier) {
        Column(
            Modifier
                .width(260.dp) // More compact width
                .fillMaxHeight(0.85f) // Don't take full height on mobile
                .clip(RoundedCornerShape(20.dp)) // Rounded for floating feel
                .background(OzColors.PanelRaised.copy(alpha = 0.95f))
                .border(1.dp, OzColors.Outline.copy(alpha = .3f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.ButtonB || event.key == Key.Back)) {
                        if (showSizes) showSizes = false else onDismiss()
                        true
                    } else false
                }
                .focusable(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                displayTitle.uppercase(),
                style = OzTypography.section,
                color = OzColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                game.platform.uppercase(),
                style = OzTypography.meta,
                color = OzColors.Cyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showSizes) {
                    OzCommandHeader("Tamaño del icono")
                    OzCommandAction("▣", "Pequeño", "1 × 1 · cuadrícula densa", onClick = { onResizeTo(OzTileSize.SMALL) }, modifier = Modifier.focusRequester(focusRequester))
                    OzCommandAction("▤", "Horizontal", "2 × 1 · artwork panorámico", onClick = { onResizeTo(OzTileSize.WIDE) })
                    OzCommandAction("▥", "Destacado", "2 × 2 · hero de página", onClick = { onResizeTo(OzTileSize.LARGE) })
                    Spacer(Modifier.height(8.dp))
                    OzCommandAction("←", "Volver", onClick = { showSizes = false })
                } else {
                    OzCommandAction("▶", "Jugar", "Iniciar sesión", onClick = {
                        context.startActivity(Intent(context, SessionActivity::class.java).putExtra(SessionActivity.EXTRA_GAME_ID, game.id))
                        onDismiss()
                    }, modifier = Modifier.focusRequester(focusRequester))

                    OzCommandAction("↻", "Continuar", "Reanudar donde estabas", onClick = {
                        context.startActivity(Intent(context, SessionActivity::class.java).putExtra(SessionActivity.EXTRA_GAME_ID, game.id))
                        onDismiss()
                    })

                    OzCommandHeader("Organizar")
                    OzCommandAction("↔", "Mover", onClick = onMove)
                    OzCommandAction("▦", "Tamaño", "Cambiar dimensiones", onClick = { showSizes = true })

                    OzCommandHeader("Personalizar")
                    OzCommandAction("◉", "Hero Media", onClick = onHeroMedia)
                    OzCommandAction("▧", "Artwork", onClick = onArtwork)
                    OzCommandAction("♡", "Colección", onClick = onCollection)

                    OzCommandHeader("Sistema")
                    OzCommandAction("📂", "Ver sistema", onClick = {
                        onOpenCollection(cl.gakuto.kizuna.data.CollectionVisibilityPolicy.SYSTEM_COLLECTION_PREFIX + game.systemId)
                    })
                    OzCommandAction("⚙", "Emulador", onClick = onEmulator)
                    OzCommandAction("ⓘ", "Información", onClick = onInfo)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(OzColors.Cyan.copy(alpha = .1f))
                        .border(1.dp, OzColors.Cyan.copy(alpha = .4f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("B", style = OzTypography.meta, color = OzColors.Cyan)
                }
                Spacer(Modifier.width(8.dp))
                Text("CERRAR", style = OzTypography.meta, color = OzColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun OzCommandHeader(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), style = OzTypography.meta, color = OzColors.TextSecondary)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(OzColors.Outline.copy(alpha = .2f))
        )
    }
}

@Composable
private fun OzCommandAction(
    glyph: String,
    label: String,
    detail: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "oz-command-scale")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) OzColors.Cyan.copy(alpha = .12f) else Color.Transparent)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) OzColors.Cyan else OzColors.Outline.copy(alpha = .1f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .focusable(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            glyph,
            style = OzTypography.section.copy(fontSize = 16.sp),
            color = if (focused) OzColors.Cyan else OzColors.TextSecondary,
            modifier = Modifier.width(24.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = OzTypography.body.copy(fontSize = 13.sp), color = OzColors.TextPrimary)
            if (detail != null) {
                Text(detail, style = OzTypography.meta.copy(fontSize = 9.sp), color = OzColors.TextSecondary)
            }
        }
    }
}

@Composable
fun OzControllerHints(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Hint("A", "Abrir", OzColors.Cyan)
        Hint("B", "Volver", OzColors.Magenta)
        Hint("Y", "Buscar", OzColors.Warning)
        Hint("X", "Filtros", OzColors.ElectricBlue)
        
        Spacer(Modifier.weight(1f))
        
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, OzColors.Outline.copy(alpha = .3f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("START  ACCIONES", style = OzTypography.meta, color = OzColors.TextSecondary)
        }
        
        Text("L / R  PÁGINA", style = OzTypography.meta, color = OzColors.TextSecondary)
    }
}

@Composable
private fun Hint(key: String, label: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.dp, accent.copy(alpha = .6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(key, style = OzTypography.meta.copy(fontSize = 10.sp), color = accent)
        }
        Text(label.uppercase(), style = OzTypography.meta, color = OzColors.TextSecondary)
    }
}

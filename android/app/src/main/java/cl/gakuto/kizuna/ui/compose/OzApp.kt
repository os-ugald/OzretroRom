@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package cl.gakuto.kizuna.ui.compose

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.data.GameGridDensity
import cl.gakuto.kizuna.launch.SessionActivity
import cl.gakuto.kizuna.ui.compose.components.GameVaultChestLogo
import cl.gakuto.kizuna.ui.compose.components.HylianGameCard
import cl.gakuto.kizuna.ui.compose.components.HylianSplashOverlay
import cl.gakuto.kizuna.ui.compose.components.OZ_IMMERSIVE_COVER_ASPECT_RATIO
import cl.gakuto.kizuna.ui.compose.components.OzActivityFloatingSheet
import cl.gakuto.kizuna.ui.compose.components.OzArtworkPicker
import cl.gakuto.kizuna.ui.compose.components.OzCollectionPicker
import cl.gakuto.kizuna.ui.compose.components.gridColumnsForDensity
import cl.gakuto.kizuna.ui.compose.screens.CatalogHubScreen
import cl.gakuto.kizuna.ui.compose.screens.FtpConfigModal
import cl.gakuto.kizuna.ui.compose.screens.HylianExploreScreen
import cl.gakuto.kizuna.ui.compose.screens.HylianPlayScreen
import cl.gakuto.kizuna.ui.compose.screens.HylianSettingsScreen
import cl.gakuto.kizuna.ui.compose.screens.MoodPickerDialog
import cl.gakuto.kizuna.ui.compose.screens.PcHubModal
import cl.gakuto.kizuna.ui.compose.screens.SagaDetailScreen
import cl.gakuto.kizuna.ui.compose.screens.SmartListDetailScreen
import cl.gakuto.kizuna.ui.compose.surface.OzQuickMenu
import cl.gakuto.kizuna.ui.compose.surface.OzTileSize
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.ui.compose.screens.GameDetailScreen as VaultGameDetailScreen

private sealed class TopDestination(val route: String, val label: String) {
    object Play : TopDestination("play", "Jugar")
    object Explore : TopDestination("explore", "Explorar")
    object Settings : TopDestination("settings", "Ajustes")
}

private val topDestinations = listOf(
    TopDestination.Play,
    TopDestination.Explore,
    TopDestination.Settings,
)

@Composable
fun OzApp(viewModel: OzViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var omniboxFocusCommand by remember { mutableStateOf(0) }
    var activitySheetExpanded by remember { mutableStateOf(false) }

    // Global UI State for Overlays
    var quickMenuGame by remember { mutableStateOf<GameEntity?>(null) }
    var artworkPickerGame by remember { mutableStateOf<GameEntity?>(null) }
    var collectionPickerGame by remember { mutableStateOf<GameEntity?>(null) }
    var showMoodPicker by remember { mutableStateOf(false) }
    var showFtpConfig by remember { mutableStateOf(false) }
    var showPcHub by remember { mutableStateOf(false) }

    val ftpJobs by viewModel.ftpJobs.collectAsStateWithLifecycle()
    val downloadUi by viewModel.downloadUi.collectAsStateWithLifecycle()
    val artworkSyncUi by viewModel.artworkSyncUi.collectAsStateWithLifecycle()
    val isApplyingPatch by viewModel.isApplyingPatch.collectAsStateWithLifecycle()
    val catalogPreparation by viewModel.catalogPreparation.collectAsStateWithLifecycle()
    val pcHubSseConnected by viewModel.pcHubSseConnected.collectAsStateWithLifecycle()
    val latestHubEvent by viewModel.latestHubEvent.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(OzColors.HylianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (showSplash) {
                    if (event.key in setOf(
                            Key.ButtonA,
                            Key.ButtonStart,
                            Key.Enter,
                            Key.DirectionCenter,
                            Key.Spacebar,
                            Key.ButtonB,
                            Key.Back,
                            Key.Escape,
                        )
                    ) {
                        showSplash = false
                    }
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.ButtonL1 -> {
                        val currentIndex = topDestinations.indexOfFirst { it.route == currentRoute }.let { if (it >= 0) it else 0 }
                        val targetIndex = (currentIndex - 1 + topDestinations.size) % topDestinations.size
                        navController.navigateTop(topDestinations[targetIndex].route)
                        true
                    }
                    Key.ButtonR1 -> {
                        val currentIndex = topDestinations.indexOfFirst { it.route == currentRoute }.let { if (it >= 0) it else 0 }
                        val targetIndex = (currentIndex + 1) % topDestinations.size
                        navController.navigateTop(topDestinations[targetIndex].route)
                        true
                    }
                    Key.ButtonY -> {
                        navController.navigateTop(TopDestination.Play.route)
                        omniboxFocusCommand += 1
                        true
                    }
                    Key.ButtonSelect -> {
                        navController.navigateTop(TopDestination.Settings.route)
                        true
                    }
                    Key.ButtonB, Key.Back -> {
                        when {
                            quickMenuGame != null -> { quickMenuGame = null; true }
                            artworkPickerGame != null -> { artworkPickerGame = null; true }
                            collectionPickerGame != null -> { collectionPickerGame = null; true }
                            showMoodPicker -> { showMoodPicker = false; true }
                            showFtpConfig -> { showFtpConfig = false; true }
                            showPcHub -> { showPcHub = false; true }
                            activitySheetExpanded -> { activitySheetExpanded = false; true }
                            else -> navController.popBackStack()
                        }
                    }
                    else -> false
                }
            },
    ) {
        val wide = maxWidth >= 720.dp || maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                OzNavDock(
                    navController = navController,
                    vertical = true,
                    modifier = Modifier.width(88.dp).fillMaxHeight(),
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    OzNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        omniboxFocusCommand = omniboxFocusCommand,
                        onStartGame = { quickMenuGame = it },
                        onOpenFtpConfig = { showFtpConfig = true },
                        onOpenPcHub = { showPcHub = true },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    OzActivityFloatingSheet(
                        downloadUi = downloadUi,
                        artworkSyncUi = artworkSyncUi,
                        ftpJobs = ftpJobs,
                        isApplyingPatch = isApplyingPatch,
                        catalogPreparation = catalogPreparation,
                        pcHubSseConnected = pcHubSseConnected,
                        latestHubEvent = latestHubEvent,
                        expanded = activitySheetExpanded,
                        onToggleExpanded = { activitySheetExpanded = !activitySheetExpanded },
                        onOpenFtpModal = { showFtpConfig = true },
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                OzNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    omniboxFocusCommand = omniboxFocusCommand,
                    onStartGame = { quickMenuGame = it },
                    onOpenFtpConfig = { showFtpConfig = true },
                    onOpenPcHub = { showPcHub = true },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                OzActivityFloatingSheet(
                    downloadUi = downloadUi,
                    artworkSyncUi = artworkSyncUi,
                    ftpJobs = ftpJobs,
                    isApplyingPatch = isApplyingPatch,
                    catalogPreparation = catalogPreparation,
                    pcHubSseConnected = pcHubSseConnected,
                    latestHubEvent = latestHubEvent,
                    expanded = activitySheetExpanded,
                    onToggleExpanded = { activitySheetExpanded = !activitySheetExpanded },
                    onOpenFtpModal = { showFtpConfig = true },
                )
                OzNavDock(
                    navController = navController,
                    vertical = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Global Context Menu
        quickMenuGame?.let { game ->
            val isCompact = maxWidth < 760.dp
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(250)) { it / 2 } + scaleIn(tween(250), transformOrigin = TransformOrigin(1f, 0.5f)),
                    exit = fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 2 } + scaleOut(tween(200), transformOrigin = TransformOrigin(1f, 0.5f)),
                    modifier = if (isCompact) Modifier.align(Alignment.Center) else Modifier.align(Alignment.CenterEnd).padding(32.dp),
                ) {
                    OzQuickMenu(
                        game = game,
                        visible = true,
                        onDismiss = { quickMenuGame = null },
                        onOpenDetails = { quickMenuGame = null; navController.navigate("game/${game.id}") },
                        onOpenCollection = { id -> quickMenuGame = null; viewModel.openCollection(id); navController.navigate("collection/$id") },
                        onMove = { quickMenuGame = null; navController.navigate("game/${game.id}") },
                        onResizeTo = { size: OzTileSize ->
                            viewModel.preferences.setSurfaceSize(game.id, size.key)
                            quickMenuGame = null
                        },
                        onArtwork = { quickMenuGame = null; artworkPickerGame = game },
                        onCollection = { quickMenuGame = null; collectionPickerGame = game },
                        onHeroMedia = { quickMenuGame = null; navController.navigate("game/${game.id}") },
                        onEmulator = { quickMenuGame = null; navController.navigate("game/${game.id}") },
                        onInfo = { quickMenuGame = null; navController.navigate("game/${game.id}") },
                    )
                }
            }
        }

        if (artworkPickerGame != null) {
            OzArtworkPicker(game = artworkPickerGame!!, viewModel = viewModel, visible = true, onDismiss = { artworkPickerGame = null })
        }
        if (collectionPickerGame != null) {
            OzCollectionPicker(game = collectionPickerGame!!, viewModel = viewModel, visible = true, onDismiss = { collectionPickerGame = null })
        }
        MoodPickerDialog(
            visible = showMoodPicker,
            viewModel = viewModel,
            onDismiss = { showMoodPicker = false },
            onPlayGame = { g ->
                context.startActivity(Intent(context, SessionActivity::class.java).putExtra(SessionActivity.EXTRA_GAME_ID, g.id))
            },
            onOpenGameDetail = { id ->
                viewModel.openGame(id)
                navController.navigate("game/$id")
            },
        )
        if (showFtpConfig) {
            FtpConfigModal(viewModel = viewModel, onDismiss = { showFtpConfig = false })
        }
        if (showPcHub) {
            PcHubModal(
                viewModel = viewModel,
                onDismiss = { showPcHub = false },
                onOpenCatalogQuery = { query ->
                    viewModel.setCatalogQuery(query)
                    navController.navigate("catalog")
                },
            )
        }

        HylianSplashOverlay(
            visible = showSplash,
            onDismiss = { showSplash = false },
        )
    }
}

@Composable
private fun OzNavHost(
    navController: NavHostController,
    viewModel: OzViewModel,
    omniboxFocusCommand: Int,
    onStartGame: (GameEntity) -> Unit,
    onOpenFtpConfig: () -> Unit,
    onOpenPcHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launchRetroArch: (GameEntity) -> Unit = { g ->
        context.startActivity(Intent(context, SessionActivity::class.java).putExtra(SessionActivity.EXTRA_GAME_ID, g.id))
    }
    NavHost(navController, startDestination = TopDestination.Play.route, modifier = modifier) {
        composable(TopDestination.Play.route) {
            HylianPlayScreen(
                viewModel = viewModel,
                omniboxFocusCommand = omniboxFocusCommand,
                onLaunchGame = launchRetroArch,
                onOpenGame = { id -> viewModel.openGame(id); navController.navigate("game/$id") },
                onStartGameMenu = onStartGame,
                onOpenExplore = { navController.navigateTop(TopDestination.Explore.route) },
                onOpenPcHub = onOpenPcHub,
            )
        }
        composable(TopDestination.Explore.route) {
            HylianExploreScreen(
                viewModel = viewModel,
                onOpenSaga = { sagaId -> navController.navigate("saga/$sagaId") },
                onOpenSystemCollection = { colId ->
                    viewModel.openCollection(colId)
                    navController.navigate("collection/$colId")
                },
                onOpenFullCatalog = { navController.navigate("catalog") },
                onOpenFullLibrary = { navController.navigate("library") },
            )
        }
        composable(TopDestination.Settings.route) {
            HylianSettingsScreen(
                viewModel = viewModel,
                onOpenFtpConfig = onOpenFtpConfig,
            )
        }
        composable("saga/{id}") { entry ->
            val sagaId = entry.arguments?.getString("id").orEmpty()
            SagaDetailScreen(
                viewModel = viewModel,
                sagaId = sagaId,
                onBack = navController::popBackStack,
                onOpenGame = { gameId -> viewModel.openGame(gameId); navController.navigate("game/$gameId") },
                onPlayGame = launchRetroArch,
                onOpenCatalogSearch = { query ->
                    viewModel.setCatalogPlatformFilter("")
                    viewModel.setCatalogQuery(query)
                    navController.navigate("catalog")
                },
            )
        }
        composable("catalog") {
            CatalogHubScreen(
                viewModel = viewModel,
                onOpenGame = { id -> viewModel.openGame(id); navController.navigate("game/$id") },
                onPlayGame = launchRetroArch,
                onOpenCollection = { id -> viewModel.openCollection(id); navController.navigate("collection/$id") },
                onOpenSmartList = { listId -> navController.navigate("smart-list/$listId") },
            )
        }
        composable("smart-list/{id}") { entry ->
            val listId = entry.arguments?.getString("id").orEmpty()
            SmartListDetailScreen(
                viewModel = viewModel,
                listId = listId,
                onBack = navController::popBackStack,
                onGame = { id -> viewModel.openGame(id); navController.navigate("game/$id") },
                onStartGame = onStartGame,
            )
        }
        composable("library") {
            LibraryScreen(
                viewModel = viewModel,
                onGame = { id -> viewModel.openGame(id); navController.navigate("game/$id") },
                onStartGame = onStartGame,
            )
        }
        composable("collection/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.openCollection(id) }
            CollectionDetailScreen(viewModel, onBack = navController::popBackStack) { gameId ->
                viewModel.openGame(gameId)
                navController.navigate("game/$gameId")
            }
        }
        composable("game/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            VaultGameDetailScreen(
                viewModel = viewModel,
                gameId = id,
                onBack = navController::popBackStack,
                onOpenSaga = { sagaId -> navController.navigate("saga/$sagaId") },
            )
        }
    }
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) { popUpTo(graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
}

@Composable
private fun OzNavDock(
    navController: NavHostController,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    if (vertical) {
        Column(
            modifier
                .background(OzColors.HylianSurface.copy(alpha = 0.94f))
                .border(width = 0.5.dp, color = OzColors.HylianBorder)
                .padding(vertical = 18.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GameVaultChestLogo(modifier = Modifier.size(44.dp), openHint = true)
                Spacer(Modifier.height(4.dp))
                Text("HYLIAN", style = OzTypography.meta.copy(fontSize = 9.sp), color = OzColors.HylianGold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                topDestinations.forEach { destination ->
                    val selected = current?.hierarchy?.any { it.route == destination.route } == true
                    OzNavItem(destination, selected, vertical = true) { navController.navigateTop(destination.route) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(OzColors.HylianBg.copy(alpha = 0.97f))
                .border(width = 0.5.dp, color = OzColors.HylianBorder)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topDestinations.forEach { destination ->
                val selected = current?.hierarchy?.any { it.route == destination.route } == true
                OzNavItem(destination, selected, vertical = false, modifier = Modifier.weight(1f)) {
                    navController.navigateTop(destination.route)
                }
            }
        }
    }
}

@Composable
private fun OzNavItem(
    destination: TopDestination,
    selected: Boolean,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "hylian-nav-focus")
    Box(
        modifier
            .heightIn(min = 48.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    selected -> OzColors.HylianEmerald
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> OzColors.HylianGold
                    selected -> OzColors.Cyan
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
                shape = RoundedCornerShape(14.dp),
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            OzNavGlyph(destination.route, selected || focused)
            Spacer(Modifier.height(2.dp))
            Text(
                destination.label,
                style = OzTypography.meta.copy(fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (focused) OzColors.HylianGold else if (selected) OzColors.TextPrimary else OzColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OzNavGlyph(route: String, active: Boolean) {
    val tint = if (active) OzColors.TextPrimary else OzColors.TextSecondary
    Canvas(Modifier.size(20.dp)) {
        val sw = 1.8.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (route) {
            TopDestination.Play.route -> {
                val p = Path().apply {
                    moveTo(size.width * .24f, size.height * .18f)
                    lineTo(size.width * .84f, size.height * .50f)
                    lineTo(size.width * .24f, size.height * .82f)
                    close()
                }
                drawPath(p, tint, style = stroke)
            }
            TopDestination.Explore.route -> {
                val cell = size.width * .28f
                val gap = size.width * .12f
                val s = size.width * .16f
                repeat(2) { r ->
                    repeat(2) { c ->
                        drawRoundRect(
                            tint,
                            topLeft = Offset(s + c * (cell + gap), s + r * (cell + gap)),
                            size = androidx.compose.ui.geometry.Size(cell, cell),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = stroke,
                        )
                    }
                }
            }
            else -> {
                val ys = listOf(.28f, .50f, .72f)
                val knobs = listOf(.62f, .38f, .68f)
                ys.forEachIndexed { index, y ->
                    drawLine(tint, Offset(size.width * .16f, size.height * y), Offset(size.width * .84f, size.height * y), sw, StrokeCap.Round)
                    drawCircle(tint, radius = 2.6.dp.toPx(), center = Offset(size.width * knobs[index], size.height * y))
                }
            }
        }
    }
}

@Composable
private fun ScreenShell(title: String, kicker: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(OzColors.HylianBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = OzTypography.section, color = OzColors.TextPrimary)
            if (kicker.isNotBlank()) {
                Spacer(Modifier.width(9.dp))
                Text(kicker.uppercase(), style = OzTypography.meta, color = OzColors.HylianGold, maxLines = 1)
            }
        }
        content()
    }
}

@Composable
private fun LibraryScreen(viewModel: OzViewModel, onGame: (String) -> Unit, onStartGame: (GameEntity) -> Unit) {
    val games = viewModel.libraryGames.collectAsLazyPagingItems()
    val section by viewModel.librarySection.collectAsStateWithLifecycle()
    val density by viewModel.gameGridDensity.collectAsStateWithLifecycle()
    val artworkUi by viewModel.artworkSyncUi.collectAsStateWithLifecycle()
    ScreenShell("Biblioteca", "Tu colección local") {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = OzShapes.pill,
                    color = if (artworkUi.running) OzColors.HylianEmerald else OzColors.HylianSurface,
                    border = BorderStroke(1.dp, OzColors.HylianEmerald),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(OzShapes.pill)
                        .clickable(enabled = !artworkUi.running) { viewModel.syncAllArtwork() },
                ) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (artworkUi.running) "${artworkUi.completed}/${artworkUi.total}" else "Sincronizar carátulas",
                            style = OzTypography.meta.copy(fontWeight = FontWeight.Bold),
                            color = OzColors.TextPrimary,
                        )
                    }
                }
                LibrarySection.entries.forEach { s ->
                    FilterChip(
                        selected = section == s,
                        onClick = { viewModel.setLibrarySection(s) },
                        label = { Text(s.label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            if (artworkUi.running && artworkUi.total > 0) {
                LinearProgressIndicator(
                    progress = { (artworkUi.completed.toFloat() / artworkUi.total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                    color = OzColors.HylianEmerald,
                    trackColor = OzColors.HylianSurface,
                )
            }
            Box(Modifier.weight(1f)) { PagedGameGrid(games, viewModel.preferences, onGame, onStartGame, gridDensity = density) }
        }
    }
}

@Composable
private fun CollectionDetailScreen(viewModel: OzViewModel, onBack: () -> Unit, onGame: (String) -> Unit) {
    val summary by viewModel.selectedCollection.collectAsStateWithLifecycle()
    val games = viewModel.collectionGames.collectAsLazyPagingItems()
    val query by viewModel.collectionQuery.collectAsStateWithLifecycle()
    val density by viewModel.gameGridDensity.collectAsStateWithLifecycle()
    val missingRoms = ((summary?.gameCount ?: 0) - (summary?.localCount ?: 0)).coerceAtLeast(0)
    val subtitle = if (summary != null) "${summary!!.gameCount} ROMs · $missingRoms faltantes" else summary?.collection?.description.orEmpty()
    ScreenShell(summary?.collection?.name ?: "Colección", subtitle) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setCollectionQuery,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    placeholder = { Text("Buscar título…", style = OzTypography.body) },
                    singleLine = true,
                    shape = OzShapes.pill,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = OzColors.HylianSurface,
                        focusedContainerColor = OzColors.HylianSurface,
                        unfocusedBorderColor = OzColors.HylianBorder,
                        focusedBorderColor = OzColors.HylianGold,
                    ),
                )
                OzButton("", icon = "⇩") { viewModel.downloadSelectedCollection() }
                OzButton("", icon = "←") { onBack() }
            }
            Box(Modifier.weight(1f)) { PagedGameGrid(games, viewModel.preferences, onGame, gridDensity = density) }
        }
    }
}

@Composable
private fun PagedGameGrid(
    games: androidx.paging.compose.LazyPagingItems<GameEntity>,
    preferences: AppPreferences,
    onGame: (String) -> Unit,
    onStartGame: (GameEntity) -> Unit = {},
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
    gridDensity: GameGridDensity = preferences.gameGridDensity,
) {
    val firstGameFocusRequester = remember { FocusRequester() }
    var initialFocusRequested by remember(requestInitialFocus) { mutableStateOf(false) }
    LaunchedEffect(requestInitialFocus, games.itemCount) {
        if (requestInitialFocus && games.itemCount > 0 && !initialFocusRequested) {
            kotlinx.coroutines.delay(100)
            runCatching { firstGameFocusRequester.requestFocus() }
            initialFocusRequested = true
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp || maxWidth > maxHeight
        val cols = gridColumnsForDensity(gridDensity, wide, maxWidth.value)
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(games.itemCount) { index ->
                val game = games[index]
                if (game != null) {
                    HylianGameCard(
                        game = game,
                        preferences = preferences,
                        onOpen = { onGame(game.id) },
                        onStart = { onStartGame(game) },
                        modifier = if (index == 0) Modifier.fillMaxWidth().focusRequester(firstGameFocusRequester) else Modifier.fillMaxWidth(),
                    )
                }
            }
            if (games.loadState.append is LoadState.Loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OzColors.HylianEmerald)
                    }
                }
            }
        }
    }
}

@Composable
private fun OzButton(label: String = "", emphasized: Boolean = false, enabled: Boolean = true, icon: String? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (emphasized) OzColors.HylianEmerald else OzColors.HylianSurface,
            contentColor = OzColors.TextPrimary,
        ),
        shape = OzShapes.pill,
        border = BorderStroke(1.dp, if (emphasized) OzColors.HylianEmerald else OzColors.HylianBorder),
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = if (label.isEmpty()) 12.dp else 16.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Text(icon, style = OzTypography.section.copy(fontSize = 18.sp))
            if (label.isNotBlank()) Spacer(Modifier.width(8.dp))
        }
        if (label.isNotBlank()) {
            Text(label.uppercase(), style = OzTypography.meta)
        }
    }
}

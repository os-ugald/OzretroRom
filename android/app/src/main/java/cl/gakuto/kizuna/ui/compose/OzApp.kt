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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.launch.SessionActivity
import cl.gakuto.kizuna.ui.compose.components.HylianSplashOverlay
import cl.gakuto.kizuna.ui.compose.components.OzActivityFloatingSheet
import cl.gakuto.kizuna.ui.compose.components.OzArtworkPicker
import cl.gakuto.kizuna.ui.compose.components.OzBrandEmblem
import cl.gakuto.kizuna.ui.compose.components.OzChamferShape
import cl.gakuto.kizuna.ui.compose.components.OzCollectionPicker
import cl.gakuto.kizuna.ui.compose.components.ozHudBorderBrush
import cl.gakuto.kizuna.ui.compose.components.ozNeonGlow
import cl.gakuto.kizuna.ui.compose.screens.CatalogHubScreen
import cl.gakuto.kizuna.ui.compose.screens.FtpConfigModal
import cl.gakuto.kizuna.ui.compose.screens.HylianExploreScreen
import cl.gakuto.kizuna.ui.compose.screens.HylianSettingsScreen
import cl.gakuto.kizuna.ui.compose.screens.MoodPickerDialog
import cl.gakuto.kizuna.ui.compose.screens.OzHomeScreen
import cl.gakuto.kizuna.ui.compose.screens.OzLibraryScreen
import cl.gakuto.kizuna.ui.compose.screens.OzSearchAddScreen
import cl.gakuto.kizuna.ui.compose.screens.PcHubModal
import cl.gakuto.kizuna.ui.compose.screens.SagaDetailScreen
import cl.gakuto.kizuna.ui.compose.screens.SmartListDetailScreen
import cl.gakuto.kizuna.ui.compose.surface.OzQuickMenu
import cl.gakuto.kizuna.ui.compose.surface.OzTileSize
import cl.gakuto.kizuna.ui.compose.theme.LocalOzShellLayout
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShellLayout
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.ui.compose.screens.GameDetailScreen as VaultGameDetailScreen

private sealed class TopDestination(val route: String, val label: String, val shortLabel: String) {
    object Home : TopDestination("home", "Inicio", "Inicio")
    object Library : TopDestination("library", "Biblioteca", "Biblio")
    object Collections : TopDestination("collections", "Colecciones", "Sagas")
    object Search : TopDestination("search", "Buscar", "Buscar")
}

private val dockDestinations = listOf(
    TopDestination.Home,
    TopDestination.Library,
    TopDestination.Collections,
    TopDestination.Search,
)

/** Shell layout dimensions (Spec §1.1 / §2.3). */
internal val OZ_LANDSCAPE_RAIL_WIDTH: Dp = 64.dp
internal val OZ_MOBILE_HEADER_HEIGHT: Dp = 48.dp
internal val OZ_CENTER_DOCK_HEIGHT: Dp = 58.dp
internal val OZ_CENTER_DOCK_EMBLEM: Dp = 50.dp
internal val OZ_DESKTOP_TOP_BAR_HEIGHT: Dp = 52.dp

/**
 * Responsive shell matrix (Spec §1.1):
 * - LANDSCAPE_HANDHELD: phone held horizontally → left rail only (no header, no bottom dock).
 * - DESKTOP_WIDE: tablets/desktop → console top bar, no dock.
 * - PORTRAIT_PHONE: thin header + bottom dock.
 */
internal fun resolveOzShellLayout(maxWidth: Dp, maxHeight: Dp): OzShellLayout = when {
    maxWidth > maxHeight && maxHeight < 540.dp -> OzShellLayout.LANDSCAPE_HANDHELD
    maxWidth >= 960.dp && maxHeight >= 540.dp -> OzShellLayout.DESKTOP_WIDE
    else -> OzShellLayout.PORTRAIT_PHONE
}

@Composable
fun OzApp(viewModel: OzViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var searchFocusCommand by remember { mutableStateOf(0) }
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
    val quickResumeHero by viewModel.quickResumeHero.collectAsStateWithLifecycle()
    val themeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // NavHost is movable so rotating between portrait shell and landscape rail keeps screen state.
    val navHostContent = remember(navController, viewModel) {
        movableContentOf { hostModifier: Modifier ->
            OzNavHost(
                navController = navController,
                viewModel = viewModel,
                searchFocusCommand = searchFocusCommand,
                onStartGame = { quickMenuGame = it },
                onOpenFtpConfig = { showFtpConfig = true },
                modifier = hostModifier,
            )
        }
    }

    val onCenterOzAction: () -> Unit = {
        val hero = quickResumeHero
        if (hero != null) {
            context.startActivity(
                Intent(context, SessionActivity::class.java).putExtra(SessionActivity.EXTRA_GAME_ID, hero.game.id),
            )
        } else {
            showMoodPicker = true
        }
    }
    val openSettings: () -> Unit = { navController.navigateTop("settings") }
    val openPcHub: () -> Unit = { showPcHub = true }

    val activitySheet: @Composable () -> Unit = {
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

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(OzColors.Bg)
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
                        val currentIndex = dockDestinations.indexOfFirst { it.route == currentRoute }.let { if (it >= 0) it else 0 }
                        val targetIndex = (currentIndex - 1 + dockDestinations.size) % dockDestinations.size
                        navController.navigateTop(dockDestinations[targetIndex].route)
                        true
                    }
                    Key.ButtonR1 -> {
                        val currentIndex = dockDestinations.indexOfFirst { it.route == currentRoute }.let { if (it >= 0) it else 0 }
                        val targetIndex = (currentIndex + 1) % dockDestinations.size
                        navController.navigateTop(dockDestinations[targetIndex].route)
                        true
                    }
                    Key.ButtonY -> {
                        navController.navigateTop(TopDestination.Search.route)
                        searchFocusCommand += 1
                        true
                    }
                    Key.ButtonSelect -> {
                        navController.navigateTop("settings")
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
        // Dedicated landscape branch: maxWidth > maxHeight && maxHeight < 540.dp → left rail.
        val shellLayout = resolveOzShellLayout(maxWidth, maxHeight)

        CompositionLocalProvider(LocalOzShellLayout provides shellLayout) {
            when (shellLayout) {
                OzShellLayout.LANDSCAPE_HANDHELD -> {
                    Row(Modifier.fillMaxSize()) {
                        OzLandscapeRail(
                            navController = navController,
                            sseConnected = pcHubSseConnected,
                            onCenterOzAction = onCenterOzAction,
                            onOpenPcHub = openPcHub,
                            onOpenSettings = openSettings,
                        )
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            OzColors.AccentPrimary.copy(alpha = 0.45f),
                                            OzColors.AccentSecondary.copy(alpha = 0.45f),
                                        ),
                                    ),
                                ),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            navHostContent(Modifier.weight(1f).fillMaxWidth())
                            activitySheet()
                        }
                    }
                }

                OzShellLayout.DESKTOP_WIDE -> {
                    Column(Modifier.fillMaxSize()) {
                        OzDesktopTopBar(
                            navController = navController,
                            sseConnected = pcHubSseConnected,
                            themeMode = themeMode,
                            onToggleTheme = {
                                viewModel.setAppThemeMode(
                                    if (themeMode == AppThemeMode.LIGHT) AppThemeMode.DARK else AppThemeMode.LIGHT,
                                )
                            },
                            onOpenPcHub = openPcHub,
                            onOpenSettings = openSettings,
                        )
                        navHostContent(Modifier.weight(1f).fillMaxWidth())
                        activitySheet()
                    }
                }

                OzShellLayout.PORTRAIT_PHONE -> {
                    Column(Modifier.fillMaxSize()) {
                        OzMobileHeader(
                            sseConnected = pcHubSseConnected,
                            onHome = { navController.navigateTop(TopDestination.Home.route) },
                            onOpenPcHub = openPcHub,
                            onOpenSettings = openSettings,
                        )
                        navHostContent(Modifier.weight(1f).fillMaxWidth())
                        activitySheet()
                        OzCenterDock(
                            navController = navController,
                            onCenterOzAction = onCenterOzAction,
                        )
                    }
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
                        navController.navigateTop(TopDestination.Search.route)
                    },
                )
            }

            HylianSplashOverlay(
                visible = showSplash,
                onDismiss = { showSplash = false },
            )
        }
    }
}

@Composable
private fun OzNavHost(
    navController: NavHostController,
    viewModel: OzViewModel,
    searchFocusCommand: Int,
    onStartGame: (GameEntity) -> Unit,
    onOpenFtpConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launchRetroArch: (GameEntity) -> Unit = { g ->
        context.startActivity(Intent(context, SessionActivity::class.java).putExtra(SessionActivity.EXTRA_GAME_ID, g.id))
    }
    NavHost(navController, startDestination = TopDestination.Home.route, modifier = modifier) {
        composable(TopDestination.Home.route) {
            OzHomeScreen(
                viewModel = viewModel,
                onLaunchGame = launchRetroArch,
                onOpenGame = { id -> viewModel.openGame(id); navController.navigate("game/$id") },
                onStartGameMenu = onStartGame,
                onOpenLibraryPlatform = { sysId ->
                    viewModel.setLibraryPlatform(sysId.takeIf { it.isNotBlank() })
                    navController.navigateTop(TopDestination.Library.route)
                },
                onOpenExplore = { navController.navigateTop(TopDestination.Collections.route) },
                onOpenSearch = { navController.navigateTop(TopDestination.Search.route) },
            )
        }
        composable(TopDestination.Library.route) {
            OzLibraryScreen(
                viewModel = viewModel,
                onOpenGame = { id -> viewModel.openGame(id); navController.navigate("game/$id") },
                onStartGameMenu = onStartGame,
                onOpenSettings = { navController.navigateTop("settings") },
            )
        }
        composable(TopDestination.Collections.route) {
            HylianExploreScreen(
                viewModel = viewModel,
                onOpenSaga = { sagaId -> navController.navigate("saga/$sagaId") },
                onOpenSystemCollection = { colId ->
                    viewModel.openCollection(colId)
                    navController.navigate("collection/$colId")
                },
                onOpenFullCatalog = { navController.navigate("catalog") },
                onOpenFullLibrary = { navController.navigateTop(TopDestination.Library.route) },
            )
        }
        composable(TopDestination.Search.route) {
            OzSearchAddScreen(
                viewModel = viewModel,
                focusCommand = searchFocusCommand,
                onOpenGame = { id -> viewModel.openGame(id); navController.navigate("game/$id") },
                onStartGameMenu = onStartGame,
                onOpenFullCatalog = { navController.navigate("catalog") },
            )
        }
        composable("settings") {
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
                    navController.navigateTop(TopDestination.Search.route)
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
        composable("collection/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.openCollection(id) }
            val games by viewModel.favorites.collectAsStateWithLifecycle()
            OzLibraryScreen(
                viewModel = viewModel,
                onOpenGame = { gid -> viewModel.openGame(gid); navController.navigate("game/$gid") },
                onStartGameMenu = onStartGame,
                onOpenSettings = { navController.navigateTop("settings") },
            )
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

/**
 * Landscape handheld rail (64dp): Quick Resume emblem on top, 4 sections centered,
 * PC Hub + Settings at the bottom. No top header, no bottom dock → full height for content.
 */
@Composable
private fun OzLandscapeRail(
    navController: NavHostController,
    sseConnected: Boolean,
    onCenterOzAction: () -> Unit,
    onOpenPcHub: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Column(
        modifier = Modifier
            .width(OZ_LANDSCAPE_RAIL_WIDTH)
            .fillMaxHeight()
            .background(OzColors.Surface.copy(alpha = 0.96f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OzBrandEmblem(
            size = 40.dp,
            circular = true,
            glow = true,
            onClick = onCenterOzAction,
            contentDescription = "Quick Resume",
        )
        Spacer(Modifier.weight(1f))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            dockDestinations.forEach { destination ->
                val selected = current?.hierarchy?.any { it.route == destination.route } == true
                OzRailItem(
                    destination = destination,
                    selected = selected,
                    onClick = { navController.navigateTop(destination.route) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        OzPcHubButton(sseConnected = sseConnected, onClick = onOpenPcHub, size = 36.dp)
        Spacer(Modifier.height(4.dp))
        OzSettingsButton(onClick = onOpenSettings, size = 36.dp)
    }
}

@Composable
private fun OzRailItem(
    destination: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    val chipShape = remember { OzChamferShape(5.dp) }
    Box(
        modifier = Modifier
            .width(OZ_LANDSCAPE_RAIL_WIDTH)
            .height(42.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(2.5.dp)
                    .height(26.dp)
                    .background(Brush.verticalGradient(listOf(OzColors.AccentPrimary, OzColors.AccentSecondary))),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(width = 30.dp, height = 22.dp)
                    .then(if (selected) Modifier.ozNeonGlow(OzColors.AccentPrimary, radius = 6.dp, alpha = 0.22f) else Modifier)
                    .clip(chipShape)
                    .background(
                        when {
                            selected -> OzColors.AccentPrimary.copy(alpha = 0.14f)
                            focused -> OzColors.SurfaceElevated
                            else -> Color.Transparent
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                OzNavGlyph(destination.route, active, glyphSize = 18.dp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = destination.shortLabel.uppercase(),
                style = OzTypography.hudTag.copy(
                    fontSize = 8.5.sp,
                    letterSpacing = 0.6.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                ),
                color = if (active) OzColors.AccentPrimary else OzColors.TextSecondary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Vector PC Hub button: clean Canvas monitor + SSE status dot (green when connected). */
@Composable
private fun OzPcHubButton(
    sseConnected: Boolean,
    onClick: () -> Unit,
    size: Dp,
    bordered: Boolean = true,
) {
    val shape = remember { OzChamferShape(6.dp) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (bordered) OzColors.Bg.copy(alpha = 0.6f) else Color.Transparent)
            .then(if (bordered) Modifier.border(1.dp, ozHudBorderBrush(if (sseConnected) 0.7f else 0.32f), shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OzMonitorGlyph(
            tint = if (sseConnected) OzColors.AccentPrimary else OzColors.TextPrimary,
            modifier = Modifier.size(size * 0.56f),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(if (sseConnected) OzColors.Positive else OzColors.Border),
        )
    }
}

@Composable
private fun OzSettingsButton(
    onClick: () -> Unit,
    size: Dp,
    bordered: Boolean = true,
) {
    val shape = remember { OzChamferShape(6.dp) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (bordered) OzColors.Bg.copy(alpha = 0.6f) else Color.Transparent)
            .then(if (bordered) Modifier.border(1.dp, ozHudBorderBrush(0.32f), shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = "Ajustes",
            tint = OzColors.TextPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun OzMonitorGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = 1.7.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.08f, size.height * 0.14f),
            size = Size(size.width * 0.84f, size.height * 0.56f),
            cornerRadius = CornerRadius(1.6.dp.toPx()),
            style = stroke,
        )
        drawLine(tint, Offset(size.width * 0.5f, size.height * 0.70f), Offset(size.width * 0.5f, size.height * 0.84f), sw, StrokeCap.Round)
        drawLine(tint, Offset(size.width * 0.30f, size.height * 0.88f), Offset(size.width * 0.70f, size.height * 0.88f), sw, StrokeCap.Round)
    }
}

/** Clean Canvas sun / moon glyph for the desktop theme toggle (zero emojis). */
@Composable
private fun OzThemeGlyph(light: Boolean, tint: Color, background: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        if (light) {
            val sw = 1.6.dp.toPx()
            drawCircle(tint, radius = size.minDimension * 0.2f, center = c, style = Stroke(width = sw))
            repeat(8) { i ->
                val angle = Math.toRadians(i * 45.0)
                val inner = size.minDimension * 0.32f
                val outer = size.minDimension * 0.46f
                val cos = kotlin.math.cos(angle).toFloat()
                val sin = kotlin.math.sin(angle).toFloat()
                drawLine(tint, Offset(c.x + cos * inner, c.y + sin * inner), Offset(c.x + cos * outer, c.y + sin * outer), sw, StrokeCap.Round)
            }
        } else {
            val r = size.minDimension * 0.36f
            drawCircle(tint, radius = r, center = c)
            drawCircle(background, radius = r * 0.86f, center = Offset(c.x + r * 0.45f, c.y - r * 0.32f))
        }
    }
}

/**
 * Portrait mobile header (~48dp) per docs/mockups/:
 * Left: OzBrandEmblem + "R E T R O R O M" / "PLAY. PRESERVE. REMEMBER."
 * Right: chamfered 34dp module with vector PC Hub monitor (+ SSE dot) and Settings.
 */
@Composable
private fun OzMobileHeader(
    sseConnected: Boolean,
    onHome: () -> Unit,
    onOpenPcHub: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val moduleShape = remember { OzChamferShape(8.dp) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OZ_MOBILE_HEADER_HEIGHT)
            .background(OzColors.Bg.copy(alpha = 0.96f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onHome),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OzBrandEmblem(size = 32.dp)
            Column {
                Text(
                    text = "R E T R O R O M",
                    style = OzTypography.tileTitle.copy(letterSpacing = 2.2.sp, fontSize = 13.sp),
                    color = OzColors.TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "PLAY. PRESERVE. REMEMBER.",
                    style = OzTypography.hudTag.copy(fontSize = 8.sp, letterSpacing = 1.2.sp),
                    color = OzColors.AccentPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(moduleShape)
                .background(OzColors.Surface)
                .border(1.dp, ozHudBorderBrush(0.45f), moduleShape)
                .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            OzPcHubButton(sseConnected = sseConnected, onClick = onOpenPcHub, size = 28.dp, bordered = false)
            Box(
                Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(OzColors.Border),
            )
            OzSettingsButton(onClick = onOpenSettings, size = 28.dp, bordered = false)
        }
    }
}

/**
 * Wide/Desktop console top bar (52dp) — no bottom dock on desktop viewports.
 */
@Composable
private fun OzDesktopTopBar(
    navController: NavHostController,
    sseConnected: Boolean,
    themeMode: AppThemeMode,
    onToggleTheme: () -> Unit,
    onOpenPcHub: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(OZ_DESKTOP_TOP_BAR_HEIGHT)
                .background(OzColors.Surface.copy(alpha = 0.96f))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable { navController.navigateTop(TopDestination.Home.route) },
            ) {
                OzBrandEmblem(size = 32.dp)
                Column {
                    Text("R E T R O R O M", style = OzTypography.tileTitle.copy(letterSpacing = 2.sp), color = OzColors.TextPrimary)
                    Text("PLAY. PRESERVE. REMEMBER.", style = OzTypography.hudTag.copy(fontSize = 8.5.sp), color = OzColors.AccentPrimary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                dockDestinations.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    val shape = remember { OzChamferShape(6.dp) }
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .then(if (selected) Modifier.ozNeonGlow(OzColors.AccentPrimary, radius = 8.dp, alpha = 0.24f) else Modifier)
                            .clip(shape)
                            .background(
                                if (selected) {
                                    Brush.verticalGradient(
                                        listOf(OzColors.AccentPrimary.copy(alpha = 0.08f), OzColors.AccentSecondary.copy(alpha = 0.24f)),
                                    )
                                } else {
                                    Brush.verticalGradient(listOf(OzColors.Bg.copy(alpha = 0.5f), OzColors.Bg.copy(alpha = 0.5f)))
                                },
                            )
                            .border(1.dp, ozHudBorderBrush(if (selected) 0.95f else 0.32f), shape)
                            .clickable { navController.navigateTop(dest.route) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OzNavGlyph(dest.route, selected, glyphSize = 16.dp)
                            Text(
                                text = dest.label.uppercase(),
                                style = OzTypography.hudTag.copy(fontSize = 11.sp),
                                color = if (selected) OzColors.TextPrimary else OzColors.TextSecondary,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OzPcHubButton(sseConnected = sseConnected, onClick = onOpenPcHub, size = 36.dp)
                val toggleShape = remember { OzChamferShape(6.dp) }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(toggleShape)
                        .background(OzColors.Bg.copy(alpha = 0.6f))
                        .border(1.dp, ozHudBorderBrush(0.32f), toggleShape)
                        .clickable(onClick = onToggleTheme),
                    contentAlignment = Alignment.Center,
                ) {
                    OzThemeGlyph(
                        light = themeMode == AppThemeMode.LIGHT,
                        tint = OzColors.AccentPrimary,
                        background = OzColors.Bg,
                        modifier = Modifier.size(18.dp),
                    )
                }
                OzSettingsButton(onClick = onOpenSettings, size = 36.dp)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ozHudBorderBrush(0.45f)),
        )
    }
}

/**
 * Compact portrait bottom dock (58dp): 1dp gradient top line, 4 compact chamfered tabs
 * and an elevated 50dp glowing OzBrandEmblem at the center (Quick Resume).
 */
@Composable
private fun OzCenterDock(
    navController: NavHostController,
    onCenterOzAction: () -> Unit,
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(OZ_CENTER_DOCK_HEIGHT)
                .background(OzColors.Surface.copy(alpha = 0.98f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                OzColors.AccentPrimary.copy(alpha = 0.15f),
                                OzColors.AccentPrimary.copy(alpha = 0.85f),
                                OzColors.AccentSecondary.copy(alpha = 0.85f),
                                OzColors.AccentSecondary.copy(alpha = 0.15f),
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dockDestinations.take(2).forEach { destination ->
                    val selected = current?.hierarchy?.any { it.route == destination.route } == true
                    OzDockTabItem(
                        destination = destination,
                        selected = selected,
                        onClick = { navController.navigateTop(destination.route) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.width(OZ_CENTER_DOCK_EMBLEM + 14.dp))

                dockDestinations.drop(2).forEach { destination ->
                    val selected = current?.hierarchy?.any { it.route == destination.route } == true
                    OzDockTabItem(
                        destination = destination,
                        selected = selected,
                        onClick = { navController.navigateTop(destination.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        OzBrandEmblem(
            size = OZ_CENTER_DOCK_EMBLEM,
            circular = true,
            glow = true,
            onClick = onCenterOzAction,
            contentDescription = "Quick Resume",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-12).dp),
        )
    }
}

@Composable
private fun OzDockTabItem(
    destination: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "oz-dock-focus")
    val shape = remember { OzChamferShape(6.dp) }
    val active = selected || focused
    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (selected) OzColors.AccentPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                brush = when {
                    focused -> ozHudBorderBrush(0.95f)
                    selected -> ozHudBorderBrush(0.45f)
                    else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                },
                shape = shape,
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            OzNavGlyph(destination.route, active, glyphSize = 18.dp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = destination.shortLabel.uppercase(),
                style = OzTypography.hudTag.copy(fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
                color = if (active) OzColors.AccentPrimary else OzColors.TextSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun OzNavGlyph(route: String, active: Boolean, glyphSize: Dp = 20.dp) {
    val tint = if (active) OzColors.AccentPrimary else OzColors.TextSecondary
    Canvas(Modifier.size(glyphSize)) {
        val sw = 1.8.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (route) {
            TopDestination.Home.route -> {
                val p = Path().apply {
                    moveTo(size.width * .18f, size.height * .48f)
                    lineTo(size.width * .50f, size.height * .18f)
                    lineTo(size.width * .82f, size.height * .48f)
                    lineTo(size.width * .82f, size.height * .82f)
                    lineTo(size.width * .18f, size.height * .82f)
                    close()
                }
                drawPath(p, tint, style = stroke)
            }
            TopDestination.Library.route -> {
                drawLine(tint, Offset(size.width * .24f, size.height * .20f), Offset(size.width * .24f, size.height * .82f), sw, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .50f, size.height * .20f), Offset(size.width * .50f, size.height * .82f), sw, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .76f, size.height * .20f), Offset(size.width * .76f, size.height * .82f), sw, StrokeCap.Round)
            }
            TopDestination.Collections.route -> {
                val cell = size.width * .28f
                val gap = size.width * .12f
                val s = size.width * .16f
                repeat(2) { r ->
                    repeat(2) { c ->
                        drawRoundRect(
                            tint,
                            topLeft = Offset(s + c * (cell + gap), s + r * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                            style = stroke,
                        )
                    }
                }
            }
            else -> {
                drawCircle(tint, radius = size.minDimension * .28f, center = Offset(size.width * .44f, size.height * .44f), style = stroke)
                drawLine(tint, Offset(size.width * .64f, size.height * .64f), Offset(size.width * .84f, size.height * .84f), sw, StrokeCap.Round)
            }
        }
    }
}

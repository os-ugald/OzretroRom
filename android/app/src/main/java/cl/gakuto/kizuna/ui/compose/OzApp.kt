@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package cl.gakuto.kizuna.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import coil3.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import cl.gakuto.kizuna.auth.InternetArchiveAuthManager
import cl.gakuto.kizuna.catalog.background.formatCatalogPreparationElapsed
import cl.gakuto.kizuna.catalog.CatalogSystemRouting
import cl.gakuto.kizuna.data.*
import cl.gakuto.kizuna.ftp.FtpTransferState
import cl.gakuto.kizuna.ui.InternetArchiveLoginActivity
import cl.gakuto.kizuna.ui.GlobalCatalogActivity
import cl.gakuto.kizuna.launch.SessionActivity
import cl.gakuto.kizuna.sagas.SagaSnapshot
import cl.gakuto.kizuna.ui.compose.surface.*
import cl.gakuto.kizuna.ui.compose.theme.*
import cl.gakuto.kizuna.util.*
import cl.gakuto.kizuna.ui.compose.components.ConsoleBadge
import cl.gakuto.kizuna.ui.compose.components.GameVaultChestLogo
import cl.gakuto.kizuna.ui.compose.components.OZ_NDS_COVER_ASPECT_RATIO
import cl.gakuto.kizuna.ui.compose.components.OzArtworkPicker
import cl.gakuto.kizuna.ui.compose.components.OzCollectionPicker
import cl.gakuto.kizuna.ui.compose.components.OzCoverImage
import cl.gakuto.kizuna.ui.compose.components.OzFourCoverMosaic
import cl.gakuto.kizuna.ui.compose.components.OzSectionHeader
import cl.gakuto.kizuna.ui.compose.components.gridColumnsForDensity
import cl.gakuto.kizuna.ui.compose.screens.CatalogHubScreen
import cl.gakuto.kizuna.ui.compose.screens.FtpConfigModal
import cl.gakuto.kizuna.ui.compose.screens.MoodPickerDialog
import cl.gakuto.kizuna.ui.compose.screens.PcHubModal
import cl.gakuto.kizuna.ui.compose.screens.SagaBannerCard
import cl.gakuto.kizuna.ui.compose.screens.SagaDetailScreen
import cl.gakuto.kizuna.ui.compose.screens.SagasGalleryScreen
import cl.gakuto.kizuna.ui.compose.screens.SmartListDetailScreen
import cl.gakuto.kizuna.ui.compose.screens.GameDetailScreen as VaultGameDetailScreen

private sealed class TopDestination(val route: String, val label: String, val compactLabel: String) {
    object Home : TopDestination("home", "Inicio", "Inicio")
    object Sagas : TopDestination("sagas", "Sagas", "Sagas")
    object Collections : TopDestination("collections", "Colecciones", "Listas")
    object Catalog : TopDestination("catalog", "Catálogo", "Catálogo")
    object Library : TopDestination("library", "Biblioteca", "Local")
    object Settings : TopDestination("settings", "Configuración", "Ajustes")
}

private val topDestinations = listOf(
    TopDestination.Catalog,
    TopDestination.Sagas,
    TopDestination.Home,
    TopDestination.Library,
    TopDestination.Settings,
)

@Composable
fun OzApp(viewModel: OzViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var searchCommand by remember { mutableStateOf(0) }
    var filterCommand by remember { mutableStateOf(0) }
    var surfacePageCommand by remember { mutableStateOf(0) }
    var surfacePageDirection by remember { mutableStateOf(0) }
    
    // Global UI State for Overlays
    var quickMenuGame by remember { mutableStateOf<GameEntity?>(null) }
    var artworkPickerGame by remember { mutableStateOf<GameEntity?>(null) }
    var collectionPickerGame by remember { mutableStateOf<GameEntity?>(null) }
    var organizeGameId by remember { mutableStateOf<String?>(null) }
    var showMoodPicker by remember { mutableStateOf(false) }
    var showFtpConfig by remember { mutableStateOf(false) }
    var showPcHub by remember { mutableStateOf(false) }

    val ftpJobs by viewModel.ftpJobs.collectAsStateWithLifecycle()
    val activeFtpJobs = remember(ftpJobs) {
        ftpJobs.filter { it.state == FtpTransferState.QUEUED || it.state == FtpTransferState.UPLOADING }
    }

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
            .background(OzColors.DeepSpace)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.ButtonL1 -> {
                        if (currentRoute == TopDestination.Home.route) {
                            surfacePageDirection = -1
                            surfacePageCommand += 1
                        } else {
                            val currentIndex = topDestinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
                            val targetIndex = (currentIndex - 1 + topDestinations.size) % topDestinations.size
                            navController.navigateTop(topDestinations[targetIndex].route)
                        }
                        true
                    }
                    Key.ButtonR1 -> {
                        if (currentRoute == TopDestination.Home.route) {
                            surfacePageDirection = 1
                            surfacePageCommand += 1
                        } else {
                            val currentIndex = topDestinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
                            val targetIndex = (currentIndex + 1) % topDestinations.size
                            navController.navigateTop(topDestinations[targetIndex].route)
                        }
                        true
                    }
                    Key.ButtonY -> {
                        navController.navigateTop(TopDestination.Catalog.route)
                        searchCommand += 1
                        true
                    }
                    Key.ButtonX -> {
                        navController.navigateTop(TopDestination.Catalog.route)
                        filterCommand += 1
                        true
                    }
                    Key.ButtonSelect -> {
                        navController.navigateTop(TopDestination.Settings.route)
                        true
                    }
                    Key.ButtonB, Key.Back -> {
                        if (quickMenuGame != null) { quickMenuGame = null; true }
                        else if (artworkPickerGame != null) { artworkPickerGame = null; true }
                        else if (collectionPickerGame != null) { collectionPickerGame = null; true }
                        else if (organizeGameId != null) { organizeGameId = null; true }
                        else { navController.popBackStack() }
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
                    activeFtpCount = activeFtpJobs.size,
                    onFtpClick = { showFtpConfig = true },
                    modifier = Modifier.width(88.dp).fillMaxHeight(),
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (activeFtpJobs.isNotEmpty()) {
                        ActiveFtpSubtleStrip(job = activeFtpJobs.first(), onClick = { showFtpConfig = true })
                    }
                    OzNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        searchCommand = searchCommand,
                        filterCommand = filterCommand,
                        surfacePageCommand = surfacePageCommand,
                        surfacePageDirection = surfacePageDirection,
                        organizeGameId = organizeGameId,
                        onStartGame = { quickMenuGame = it },
                        onOpenMoodPicker = { showMoodPicker = true },
                        onOpenFtpConfig = { showFtpConfig = true },
                        onOpenPcHub = { showPcHub = true },
                        onOrganizeFinished = { organizeGameId = null },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                PortraitTopVaultBar(
                    activeFtpCount = activeFtpJobs.size,
                    onOpenMoodPicker = { showMoodPicker = true },
                    onOpenPcHub = { showPcHub = true },
                    onOpenFtpConfig = { showFtpConfig = true },
                )
                if (activeFtpJobs.isNotEmpty()) {
                    ActiveFtpSubtleStrip(job = activeFtpJobs.first(), onClick = { showFtpConfig = true })
                }
                OzNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    searchCommand = searchCommand,
                    filterCommand = filterCommand,
                    surfacePageCommand = surfacePageCommand,
                    surfacePageDirection = surfacePageDirection,
                    organizeGameId = organizeGameId,
                    onStartGame = { quickMenuGame = it },
                    onOpenMoodPicker = { showMoodPicker = true },
                    onOpenFtpConfig = { showFtpConfig = true },
                    onOpenPcHub = { showPcHub = true },
                    onOrganizeFinished = { organizeGameId = null },
                    modifier = Modifier.weight(1f),
                )
                OzNavDock(
                    navController = navController,
                    vertical = false,
                    activeFtpCount = activeFtpJobs.size,
                    onFtpClick = { showFtpConfig = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        
        // Global Context Menu (Glass)
        quickMenuGame?.let { game ->
            val isCompact = maxWidth < 760.dp
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(250)) { it / 2 } + scaleIn(tween(250), transformOrigin = TransformOrigin(1f, 0.5f)),
                    exit = fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { it / 2 } + scaleOut(tween(200), transformOrigin = TransformOrigin(1f, 0.5f)),
                    modifier = if (isCompact) Modifier.align(Alignment.Center) else Modifier.align(Alignment.CenterEnd).padding(32.dp)
                ) {
                    OzQuickMenu(
                        game = game,
                        visible = true,
                        onDismiss = { quickMenuGame = null },
                        onOpenDetails = { quickMenuGame = null; navController.navigate("game/${game.id}") },
                        onOpenCollection = { id -> quickMenuGame = null; viewModel.openCollection(id); navController.navigate("collection/$id") },
                        onMove = { quickMenuGame = null; organizeGameId = game.id },
                        onResizeTo = { size ->
                            viewModel.preferences.setSurfaceSize(game.id, size.key)
                            quickMenuGame = null
                            organizeGameId = game.id
                        },
                        onArtwork = { quickMenuGame = null; artworkPickerGame = game },
                        onCollection = { quickMenuGame = null; collectionPickerGame = game },
                        onHeroMedia = { quickMenuGame = null; navController.navigate("game/${game.id}") },
                        onEmulator = { quickMenuGame = null; navController.navigate("game/${game.id}") },
                        onInfo = { quickMenuGame = null; navController.navigate("game/${game.id}") }
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
                    navController.navigateTop(TopDestination.Catalog.route)
                },
            )
        }
    }
}

@Composable
private fun PortraitTopVaultBar(
    activeFtpCount: Int,
    onOpenMoodPicker: () -> Unit,
    onOpenPcHub: () -> Unit,
    onOpenFtpConfig: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OzColors.DeepSpace.copy(alpha = 0.96f))
            .border(width = 0.5.dp, color = OzColors.Outline.copy(alpha = 0.25f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            GameVaultChestLogo(modifier = Modifier.size(34.dp), openHint = true)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("OZ GAME VAULT", style = OzTypography.cardTitle, color = OzColors.Gold)
                Text("Retro Edition · by Oz", style = OzTypography.meta.copy(fontSize = 10.sp), color = OzColors.TextSecondary, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (activeFtpCount > 0) {
                Text(
                    "📡 $activeFtpCount",
                    style = OzTypography.meta,
                    color = OzColors.Gold,
                    modifier = Modifier
                        .clip(OzShapes.pill)
                        .background(OzColors.Gold.copy(alpha = 0.16f))
                        .border(1.dp, OzColors.Gold.copy(alpha = 0.5f), OzShapes.pill)
                        .clickable(onClick = onOpenFtpConfig)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
            Text(
                "🎲",
                style = OzTypography.cardTitle,
                modifier = Modifier
                    .clip(OzShapes.pill)
                    .background(OzColors.PanelRaised)
                    .border(1.dp, OzColors.Cyan.copy(alpha = 0.4f), OzShapes.pill)
                    .clickable(onClick = onOpenMoodPicker)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
            Text(
                "PC",
                style = OzTypography.meta,
                color = OzColors.Gold,
                modifier = Modifier
                    .clip(OzShapes.pill)
                    .background(OzColors.PanelRaised)
                    .border(1.dp, OzColors.Gold.copy(alpha = 0.4f), OzShapes.pill)
                    .clickable(onClick = onOpenPcHub)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun ActiveFtpSubtleStrip(
    job: cl.gakuto.kizuna.ftp.FtpTransferJob,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OzColors.Gold.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "📡 FTP 2º plano: ${job.title} → ${job.targetName}",
            style = OzTypography.meta.copy(fontSize = 11.sp),
            color = OzColors.Gold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text("${job.progressPct}%", style = OzTypography.meta, color = OzColors.Gold)
    }
}

@Composable
private fun OzNavHost(
    navController: NavHostController,
    viewModel: OzViewModel,
    searchCommand: Int,
    filterCommand: Int,
    surfacePageCommand: Int,
    surfacePageDirection: Int,
    organizeGameId: String?,
    onStartGame: (GameEntity) -> Unit,
    onOpenMoodPicker: () -> Unit,
    onOpenFtpConfig: () -> Unit,
    onOpenPcHub: () -> Unit,
    onOrganizeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launchRetroArch: (GameEntity) -> Unit = { g ->
        context.startActivity(Intent(context, SessionActivity::class.java).putExtra(SessionActivity.EXTRA_GAME_ID, g.id))
    }
    NavHost(navController, startDestination = TopDestination.Home.route, modifier = modifier) {
        composable(TopDestination.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onCollections = { navController.navigateTop(TopDestination.Collections.route) },
                onSagas = { navController.navigateTop(TopDestination.Sagas.route) },
                onSaga = { id -> navController.navigate("saga/$id") },
                onCatalog = { navController.navigateTop(TopDestination.Catalog.route) },
                onOpenMoodPicker = onOpenMoodPicker,
                onOpenFtpConfig = onOpenFtpConfig,
                onOpenPcHub = onOpenPcHub,
                onGame = { id -> viewModel.openGame(id); navController.navigate("game/$id") },
                onCollection = { id -> viewModel.openCollection(id); navController.navigate("collection/$id") },
                onStartGame = onStartGame,
                surfacePageCommand = surfacePageCommand,
                surfacePageDirection = surfacePageDirection,
                organizeGameId = organizeGameId,
                onOrganizeFinished = onOrganizeFinished,
            )
        }
        composable(TopDestination.Sagas.route) {
            SagasGalleryScreen(
                viewModel = viewModel,
                onSagaClick = { sagaId -> navController.navigate("saga/$sagaId") },
                onBackToCollections = { navController.navigateTop(TopDestination.Collections.route) },
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
                    navController.navigateTop(TopDestination.Catalog.route)
                },
            )
        }
        composable(TopDestination.Collections.route) {
            CollectionsScreen(viewModel, onCollection = { id -> viewModel.openCollection(id); navController.navigate("collection/$id") }, onStartGame = onStartGame)
        }
        composable(TopDestination.Catalog.route) {
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
        composable(TopDestination.Library.route) {
            LibraryScreen(viewModel, onGame = { id -> viewModel.openGame(id); navController.navigate("game/$id") }, onStartGame = onStartGame)
        }
        composable(TopDestination.Settings.route) {
            SettingsBridgeScreen(
                viewModel = viewModel,
                onOpenFtpConfig = onOpenFtpConfig,
                onOpenPcHub = onOpenPcHub,
            )
        }
        composable("collection/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { viewModel.openCollection(id) }
            CollectionDetailScreen(viewModel, onBack = navController::popBackStack) { gameId -> viewModel.openGame(gameId); navController.navigate("game/$gameId") }
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
    activeFtpCount: Int,
    onFtpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    if (vertical) {
        Column(
            modifier
                .background(OzColors.Panel.copy(alpha = 0.72f))
                .border(width = 0.5.dp, color = OzColors.Outline.copy(alpha = 0.25f))
                .padding(vertical = 18.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GameVaultChestLogo(modifier = Modifier.size(46.dp), openHint = true)
                Spacer(Modifier.height(4.dp))
                Text("VAULT", style = OzTypography.meta.copy(fontSize = 9.sp), color = OzColors.Gold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                topDestinations.forEach { destination ->
                    val selected = current?.hierarchy?.any { it.route == destination.route } == true
                    OzNavItem(destination, selected, vertical = true) { navController.navigateTop(destination.route) }
                }
            }
            if (activeFtpCount > 0) {
                Text(
                    "📡 $activeFtpCount",
                    style = OzTypography.meta,
                    color = OzColors.Gold,
                    modifier = Modifier
                        .clip(OzShapes.pill)
                        .background(OzColors.Gold.copy(alpha = 0.16f))
                        .border(1.dp, OzColors.Gold.copy(alpha = 0.5f), OzShapes.pill)
                        .clickable(onClick = onFtpClick)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .height(66.dp)
                .background(OzColors.DeepSpace.copy(alpha = 0.97f))
                .border(width = 0.5.dp, color = OzColors.Outline.copy(alpha = 0.3f))
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topDestinations.forEach { destination ->
                val selected = current?.hierarchy?.any { it.route == destination.route } == true
                OzNavItem(destination, selected, vertical = false, modifier = Modifier.weight(1f)) { navController.navigateTop(destination.route) }
            }
        }
    }
}

@Composable
private fun OzNavItem(destination: TopDestination, selected: Boolean, vertical: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shell = modifier
        .scale(if (focused) 1.05f else 1f)
        .onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .focusable()
        .padding(vertical = 2.dp)
    Box(shell, contentAlignment = Alignment.Center) {
        if (selected) {
            Box(
                Modifier
                    .fillMaxWidth(if (vertical) 0.9f else 0.88f)
                    .height(if (vertical) 52.dp else 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(OzColors.Cyan.copy(alpha = 0.14f))
                    .border(1.dp, OzColors.Cyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
            )
            if (vertical) {
                Box(Modifier.align(Alignment.CenterEnd).width(3.dp).height(22.dp).clip(CircleShape).background(OzColors.Cyan))
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            OzNavGlyph(destination.route, selected || focused)
            Spacer(Modifier.height(2.dp))
            Text(
                destination.compactLabel,
                style = OzTypography.meta.copy(fontSize = 9.sp),
                color = if (selected) OzColors.Cyan else OzColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun OzNavGlyph(route: String, selected: Boolean) {
    val tint = if (selected) OzColors.Cyan else OzColors.TextSecondary
    Canvas(Modifier.size(21.dp)) {
        val sw = 1.8.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (route) {
            TopDestination.Home.route -> {
                val roof = Path().apply { moveTo(size.width * .16f, size.height * .48f); lineTo(size.width * .50f, size.height * .19f); lineTo(size.width * .84f, size.height * .48f) }
                drawPath(roof, tint, style = stroke)
                drawRoundRect(color = tint, topLeft = Offset(size.width * .27f, size.height * .43f), size = androidx.compose.ui.geometry.Size(size.width * .46f, size.height * .40f), cornerRadius = CornerRadius(2.dp.toPx()), style = stroke)
            }
            TopDestination.Sagas.route -> {
                drawCircle(tint, radius = size.minDimension * 0.34f, style = stroke)
                drawCircle(tint, radius = size.minDimension * 0.12f, style = Stroke(width = sw * 0.85f))
                drawLine(tint, Offset(size.width * .5f, size.height * .08f), Offset(size.width * .5f, size.height * .24f), sw, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .5f, size.height * .76f), Offset(size.width * .5f, size.height * .92f), sw, StrokeCap.Round)
            }
            TopDestination.Collections.route -> {
                listOf(.15f to .24f, .27f to .16f, .39f to .08f).forEach { (x, y) ->
                    drawRoundRect(tint, topLeft = Offset(size.width * x, size.height * y), size = androidx.compose.ui.geometry.Size(size.width * .46f, size.height * .58f), cornerRadius = CornerRadius(3.dp.toPx()), style = stroke)
                }
            }
            TopDestination.Catalog.route -> {
                val cell = size.width * .20f; val gap = size.width * .09f; val start = size.width * .16f
                repeat(3) { row -> repeat(3) { col ->
                    drawRoundRect(tint, topLeft = Offset(start + col * (cell + gap), start + row * (cell + gap)), size = androidx.compose.ui.geometry.Size(cell, cell), cornerRadius = CornerRadius(1.5.dp.toPx()), style = Stroke(width = sw * .88f))
                } }
            }
            TopDestination.Library.route -> {
                drawRoundRect(tint, topLeft = Offset(size.width * .18f, size.height * .15f), size = androidx.compose.ui.geometry.Size(size.width * .64f, size.height * .70f), cornerRadius = CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(tint, Offset(size.width * .34f, size.height * .18f), Offset(size.width * .34f, size.height * .82f), sw)
                drawLine(tint, Offset(size.width * .46f, size.height * .34f), Offset(size.width * .69f, size.height * .34f), sw, StrokeCap.Round)
            }
            else -> {
                val ys = listOf(.28f, .50f, .72f); val knobs = listOf(.62f, .38f, .68f)
                ys.forEachIndexed { index, y ->
                    drawLine(tint, Offset(size.width * .16f, size.height * y), Offset(size.width * .84f, size.height * y), sw, StrokeCap.Round)
                    drawCircle(tint, radius = 2.7.dp.toPx(), center = Offset(size.width * knobs[index], size.height * y))
                    drawCircle(OzColors.Panel, radius = 1.2.dp.toPx(), center = Offset(size.width * knobs[index], size.height * y))
                }
            }
        }
    }
}

@Composable
private fun ScreenShell(title: String, kicker: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(OzColors.DeepSpace)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = OzTypography.section, color = OzColors.TextPrimary)
            if (kicker.isNotBlank()) { Spacer(Modifier.width(9.dp)); Text(kicker.uppercase(), style = OzTypography.meta, color = OzColors.TextSecondary, maxLines = 1) }
        }
        content()
    }
}

private val HOME_CONSOLE_FILTERS = listOf("all", "snes", "gba", "ps1", "arcade", "genesis", "n64", "psp", "dc", "nes")

@Composable
private fun HomeScreen(
    viewModel: OzViewModel,
    onCollections: () -> Unit,
    onSagas: () -> Unit,
    onSaga: (String) -> Unit,
    onCatalog: () -> Unit,
    onOpenMoodPicker: () -> Unit,
    onOpenFtpConfig: () -> Unit,
    onOpenPcHub: () -> Unit,
    onGame: (String) -> Unit,
    onCollection: (String) -> Unit,
    onStartGame: (GameEntity) -> Unit,
    surfacePageCommand: Int,
    surfacePageDirection: Int,
    organizeGameId: String?,
    onOrganizeFinished: () -> Unit,
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val continuePlaying by viewModel.continuePlaying.collectAsStateWithLifecycle()
    val sagas by viewModel.sagas.collectAsStateWithLifecycle()
    val artworkSyncUi by viewModel.artworkSyncUi.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedConsole by remember { mutableStateOf("all") }

    val allPool = remember(continuePlaying, favorites, recent) {
        (continuePlaying + favorites + recent).distinctBy { it.id }
    }
    val homeGames = remember(allPool, selectedConsole) {
        val base = if (selectedConsole == "all") allPool else allPool.filter {
            it.systemId.equals(selectedConsole, ignoreCase = true) ||
                (selectedConsole == "arcade" && it.systemId.lowercase() in setOf("arcade", "fbneo", "mame")) ||
                (selectedConsole == "genesis" && it.systemId.lowercase() in setOf("genesis", "md"))
        }
        base.take(12)
    }
    var focusedGame by remember(homeGames) { mutableStateOf(homeGames.firstOrNull()) }
    val featuredSagas = remember(sagas) {
        (sagas.filter { it.localOwnedCount > 0 } + sagas).distinctBy { it.id }.take(8)
    }
    val launchDirect: (GameEntity) -> Unit = { g ->
        context.startActivity(Intent(context, SessionActivity::class.java).putExtra(SessionActivity.EXTRA_GAME_ID, g.id))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val wide = availableWidth >= 720.dp || availableWidth > maxHeight
        val shortViewport = maxHeight < 520.dp
        OzSurfaceBackdrop(focusedGameArtUrl = focusedGame?.let { ArtworkResolver.remoteCandidates(it).firstOrNull() }) {
            if (wide) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LazyColumn(
                        Modifier
                            .width(if (availableWidth >= 980.dp) 340.dp else 290.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 18.dp),
                    ) {
                        item {
                            HomeFeaturedHeroCard(
                                game = focusedGame,
                                preferences = viewModel.preferences,
                                hasPatched = focusedGame?.let { viewModel.hasPatchedRom(it.id) } == true,
                                onPlay = launchDirect,
                                onOpenDetail = { g -> onGame(g.id) },
                                onOpenCatalog = onCatalog,
                            )
                        }
                        item {
                            HomeQuickActionGrid(
                                sagaCount = sagas.size,
                                artworkSyncRunning = artworkSyncUi.running,
                                onOpenMoodPicker = onOpenMoodPicker,
                                onSagas = onSagas,
                                onCollections = onCatalog,
                                onPcHub = onOpenPcHub,
                                onFtp = onOpenFtpConfig,
                                onSyncCovers = { viewModel.syncAllArtwork() },
                            )
                        }
                        if (!shortViewport) {
                            item { OzControllerHints(Modifier.fillMaxWidth()) }
                        }
                    }

                    LazyColumn(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item {
                            ConsoleFilterStrip(
                                selected = selectedConsole,
                                onSelect = { selectedConsole = it },
                            )
                        }
                        item {
                            Text("Bóveda rápida · RetroArch Auto-Launch", style = OzTypography.cardTitle, color = OzColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            if (homeGames.isEmpty()) {
                                EmptyHomeVaultCard(selectedConsole = selectedConsole, onOpenCatalog = onCatalog)
                            } else {
                                Box(Modifier.fillMaxWidth().height(if (shortViewport) 220.dp else 280.dp)) {
                                    OzHomeMosaic(
                                        games = homeGames,
                                        preferences = viewModel.preferences,
                                        columns = if (availableWidth >= 1040.dp) 5 else 4,
                                        focusedGame = focusedGame,
                                        onFocused = { focusedGame = it },
                                        onOpen = { onGame(it.id) },
                                        onStart = {
                                            focusedGame = it
                                            onStartGame(it)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        pageCommand = surfacePageCommand,
                                        pageDirection = surfacePageDirection,
                                        organizeGameId = organizeGameId,
                                        onOrganizeFinished = onOrganizeFinished,
                                    )
                                }
                            }
                        }
                        if (featuredSagas.isNotEmpty()) {
                            item {
                                SagasHomeRail(sagas = featuredSagas, onSeeAll = onSagas, onSaga = onSaga)
                            }
                        }
                        if (favorites.isNotEmpty()) {
                            item {
                                GameRail("Favoritos", "Acceso directo en un toque", favorites.take(12), viewModel.preferences, onGame)
                            }
                        }
                        if (recent.isNotEmpty()) {
                            item {
                                GameRail("Jugados recientemente", "Historial de sesiones RetroArch", recent.take(12), viewModel.preferences, onGame)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        HomeFeaturedHeroCard(
                            game = focusedGame,
                            preferences = viewModel.preferences,
                            hasPatched = focusedGame?.let { viewModel.hasPatchedRom(it.id) } == true,
                            onPlay = launchDirect,
                            onOpenDetail = { g -> onGame(g.id) },
                            onOpenCatalog = onCatalog,
                        )
                    }
                    item {
                        ConsoleFilterStrip(
                            selected = selectedConsole,
                            onSelect = { selectedConsole = it },
                        )
                    }
                    item {
                        HomeQuickActionGrid(
                            sagaCount = sagas.size,
                            artworkSyncRunning = artworkSyncUi.running,
                            onOpenMoodPicker = onOpenMoodPicker,
                            onSagas = onSagas,
                            onCollections = onCatalog,
                            onPcHub = onOpenPcHub,
                            onFtp = onOpenFtpConfig,
                            onSyncCovers = { viewModel.syncAllArtwork() },
                        )
                    }
                    item {
                        if (homeGames.isEmpty()) {
                            EmptyHomeVaultCard(selectedConsole = selectedConsole, onOpenCatalog = onCatalog)
                        } else {
                            GameRail(
                                title = if (selectedConsole == "all") "Destacados en tu bóveda" else "Destacados · ${ConsolePalette.forSystem(selectedConsole).fullName}",
                                subtitle = "Toca para abrir ficha o lanzar en RetroArch",
                                games = homeGames,
                                preferences = viewModel.preferences,
                                onGame = onGame,
                            )
                        }
                    }
                    if (featuredSagas.isNotEmpty()) {
                        item {
                            SagasHomeRail(sagas = featuredSagas, onSeeAll = onSagas, onSaga = onSaga)
                        }
                    }
                    if (favorites.isNotEmpty()) {
                        item {
                            GameRail("Favoritos", "Tus juegos marcados", favorites.take(12), viewModel.preferences, onGame)
                        }
                    }
                    if (recent.isNotEmpty()) {
                        item {
                            GameRail("Recientes", "Últimas partidas en Android", recent.take(12), viewModel.preferences, onGame)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleFilterStrip(
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(HOME_CONSOLE_FILTERS) { key ->
            val isSelected = selected == key
            val visual = if (key == "all") null else ConsolePalette.forSystem(key)
            val accent = visual?.accent ?: OzColors.Cyan
            val bg = if (isSelected) accent.copy(alpha = 0.22f) else (visual?.badgeBg ?: OzColors.PanelRaised)
            Text(
                text = if (key == "all") "Todas las consolas" else visual!!.shortLabel,
                style = OzTypography.meta,
                color = if (isSelected) accent else (visual?.accent?.copy(alpha = 0.88f) ?: OzColors.TextSecondary),
                modifier = Modifier
                    .clip(OzShapes.pill)
                    .background(bg)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) accent else accent.copy(alpha = 0.35f),
                        shape = OzShapes.pill,
                    )
                    .clickable { onSelect(key) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun HomeFeaturedHeroCard(
    game: GameEntity?,
    preferences: AppPreferences,
    hasPatched: Boolean,
    onPlay: (GameEntity) -> Unit,
    onOpenDetail: (GameEntity) -> Unit,
    onOpenCatalog: () -> Unit,
) {
    val context = LocalContext.current
    if (game == null) {
        Surface(
            shape = OzShapes.hero,
            color = OzColors.Panel,
            border = BorderStroke(1.dp, OzColors.Cyan.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("BIENVENIDO A OZ GAME VAULT", style = OzTypography.meta, color = OzColors.Gold)
                Spacer(Modifier.height(4.dp))
                Text("Bóveda Retro para Android", style = OzTypography.hero, color = OzColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Explora el catálogo, descarga ROMs en un toque con core automático para RetroArch o navega por sagas cronológicas.",
                    style = OzTypography.body,
                    color = OzColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenCatalog,
                    shape = OzShapes.pill,
                    colors = ButtonDefaults.buttonColors(containerColor = OzColors.Cyan, contentColor = OzColors.DeepSpace),
                ) {
                    Text("Explorar Catálogo", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        return
    }

    val console = remember(game.systemId) { ConsolePalette.forSystem(game.systemId) }
    val artworkCandidates = remember(game.id, game.title, game.thumbnailRepo) {
        ArtworkResolver.tileCandidates(context, preferences, game)
    }
    val hasLocal = !game.contentUri.isNullOrBlank()

    Surface(
        shape = OzShapes.hero,
        color = OzColors.Panel,
        border = BorderStroke(1.dp, console.accent.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(OzShapes.hero)
            .clickable { onOpenDetail(game) },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO)
                    .clip(OzShapes.medium)
                    .border(1.5.dp, console.accent.copy(alpha = 0.65f), OzShapes.medium),
            ) {
                OzCoverImage(
                    candidates = artworkCandidates,
                    title = game.title,
                    systemId = game.systemId,
                    contentScale = ContentScale.Crop,
                    showTitleOnFallback = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ConsoleBadge(systemId = game.systemId)
                    if (hasPatched) Text("[ES]", style = OzTypography.meta, color = OzColors.Gold)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    game.title,
                    style = OzTypography.section,
                    color = OzColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (hasLocal) "ROM lista · RetroArch" else "Descarga + Core auto",
                    style = OzTypography.meta,
                    color = if (hasLocal) OzColors.Positive else OzColors.Cyan,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPlay(game) },
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasLocal) OzColors.Positive else OzColors.Cyan,
                            contentColor = OzColors.DeepSpace,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text("▶ Jugar", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
                    }
                    Button(
                        onClick = { onOpenDetail(game) },
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(containerColor = OzColors.PanelRaised, contentColor = OzColors.TextPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Ficha", style = OzTypography.meta)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeQuickActionGrid(
    sagaCount: Int,
    artworkSyncRunning: Boolean,
    onOpenMoodPicker: () -> Unit,
    onSagas: () -> Unit,
    onCollections: () -> Unit,
    onPcHub: () -> Unit,
    onFtp: () -> Unit,
    onSyncCovers: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickToolPill(
                label = "🎲 ¿Qué juego hoy?",
                accent = OzColors.Cyan,
                modifier = Modifier.weight(1f),
                onClick = onOpenMoodPicker,
            )
            QuickToolPill(
                label = "🗺 Sagas ($sagaCount)",
                accent = OzColors.Gold,
                modifier = Modifier.weight(1f),
                onClick = onSagas,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickToolPill(
                label = "🔍 Catálogo",
                accent = OzColors.TextPrimary,
                modifier = Modifier.weight(1f),
                onClick = onCollections,
            )
            QuickToolPill(
                label = if (artworkSyncRunning) "⏳ Sincronizando…" else "🖼 Carátulas",
                accent = OzColors.Positive,
                modifier = Modifier.weight(1f),
                onClick = onSyncCovers,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickToolPill(
                label = "🖥 Hub PC",
                accent = OzColors.ElectricBlue,
                modifier = Modifier.weight(1f),
                onClick = onPcHub,
            )
            QuickToolPill(
                label = "📡 FTP Consola",
                accent = OzColors.Gold,
                modifier = Modifier.weight(1f),
                onClick = onFtp,
            )
        }
    }
}

@Composable
private fun QuickToolPill(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = OzShapes.medium,
        color = OzColors.PanelRaised,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        modifier = modifier
            .clip(OzShapes.medium)
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = OzTypography.meta,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun EmptyHomeVaultCard(
    selectedConsole: String,
    onOpenCatalog: () -> Unit,
) {
    Surface(
        shape = OzShapes.large,
        color = OzColors.PanelRaised.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, OzColors.Outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (selectedConsole == "all") "Todavía no hay partidas en tu bóveda rápida."
                else "Sin recientes locales para ${ConsolePalette.forSystem(selectedConsole).fullName}.",
                style = OzTypography.body,
                color = OzColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onOpenCatalog,
                shape = OzShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = OzColors.Cyan, contentColor = OzColors.DeepSpace),
            ) {
                Text("Catálogo", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun SagasHomeRail(
    sagas: List<SagaSnapshot>,
    onSeeAll: () -> Unit,
    onSaga: (String) -> Unit,
) {
    Column {
        OzSectionHeader(
            title = "Sagas destacadas",
            subtitle = "Cronología curada e historia",
            action = "Ver todas ›",
            onAction = onSeeAll,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(sagas, key = { it.id }) { saga ->
                SagaBannerCard(
                    saga = saga,
                    onClick = { onSaga(saga.id) },
                    modifier = Modifier.width(208.dp),
                )
            }
        }
    }
}

@Composable
private fun GameRail(title: String, subtitle: String, games: List<GameEntity>, preferences: AppPreferences, onGame: (String) -> Unit) {
    Column { OzSectionHeader(title, subtitle); Spacer(Modifier.height(10.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(games, key = { it.id }) { game -> GameCard(game, preferences) { onGame(game.id) } } } }
}

@Composable
private fun CollectionsScreen(viewModel: OzViewModel, onCollection: (String) -> Unit, onStartGame: (GameEntity) -> Unit) {
    val summaries by viewModel.collectionSummaries.collectAsStateWithLifecycle()
    val automatic = summaries.filter { it.collection.collectionKind == CollectionKind.AUTOMATIC }; val manual = summaries.filter { it.collection.collectionKind == CollectionKind.MANUAL }; val curated = summaries.filter { it.collection.collectionKind == CollectionKind.CURATED }; val systems = summaries.filter { it.collection.collectionKind == CollectionKind.SYSTEM }
    ScreenShell("Colecciones", "Identidad Oz") {
        if (summaries.isEmpty()) { Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = OzColors.Cyan, strokeWidth = 2.dp); Spacer(Modifier.height(12.dp)); Text("Actualizando colecciones…", style = OzTypography.body, color = OzColors.TextSecondary) } }
        else { LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (automatic.isNotEmpty()) item { CollectionRail("Automáticas", "Descargados · Favoritos · Recientes", automatic, viewModel.preferences, onCollection) }
            if (manual.isNotEmpty()) item { CollectionRail("Tus colecciones", "Listas personales persistentes", manual, viewModel.preferences, onCollection) }
            if (curated.isNotEmpty()) item { CollectionRail("Curadas por Oz", "Selecciones temáticas", curated, viewModel.preferences, onCollection) }
            if (systems.isNotEmpty()) item { CollectionRail("Sistemas", "Catálogo por consola", systems, viewModel.preferences, onCollection) }
        } }
    }
}

@Composable
private fun CollectionRail(title: String, subtitle: String, summaries: List<CollectionSummary>, preferences: AppPreferences, onCollection: (String) -> Unit) {
    Column { OzSectionHeader(title, subtitle); Spacer(Modifier.height(10.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(summaries, key = { it.collection.id }) { summary -> CollectionCard(summary, preferences, Modifier.width(228.dp)) { onCollection(summary.collection.id) } } } }
}

@Composable
private fun CollectionCard(summary: CollectionSummary, preferences: AppPreferences, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }; val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "oz-collection-focus")
    Box(modifier.scale(scale).clip(OzShapes.large).background(OzColors.PanelRaised).border(1.5.dp, if (focused) OzColors.Cyan else OzColors.Outline.copy(alpha = .42f), OzShapes.large).onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick).height(160.dp)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1.2f).fillMaxHeight().background(OzColors.PurpleDeep.copy(alpha = .18f))) {
                OzFourCoverMosaic(games = summary.covers, preferences = preferences, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.Center) { Text(summary.collection.name.uppercase(), style = OzTypography.section, color = OzColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(5.dp)); Text("${summary.gameCount} títulos", style = OzTypography.meta, color = OzColors.Cyan); if (summary.localCount > 0) { Text("${summary.localCount} en biblioteca", style = OzTypography.meta, color = OzColors.TextSecondary) } }
        }
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
                    color = if (artworkUi.running) OzColors.Cyan.copy(alpha = 0.2f) else OzColors.PanelRaised,
                    border = BorderStroke(1.dp, OzColors.Positive.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .clip(OzShapes.pill)
                        .clickable(enabled = !artworkUi.running) { viewModel.syncAllArtwork() },
                ) {
                    Text(
                        if (artworkUi.running) "⏳ ${artworkUi.completed}/${artworkUi.total}" else "🖼 Sincronizar carátulas",
                        style = OzTypography.meta.copy(fontWeight = FontWeight.Bold),
                        color = OzColors.Positive,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                LibrarySection.entries.forEach { s ->
                    FilterChip(selected = section == s, onClick = { viewModel.setLibrarySection(s) }, label = { Text(s.label) })
                }
            }
            if (artworkUi.running && artworkUi.total > 0) {
                LinearProgressIndicator(
                    progress = { (artworkUi.completed.toFloat() / artworkUi.total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                    color = OzColors.Positive,
                    trackColor = OzColors.PanelRaised,
                )
            }
            Box(Modifier.weight(1f)) { PagedGameGrid(games, viewModel.preferences, onGame, onStartGame, gridDensity = density) }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = modifier.heightIn(max = 52.dp), placeholder = { Text("Buscar título…", style = OzTypography.body) }, singleLine = true, shape = OzShapes.medium, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = OzColors.Panel.copy(alpha = .5f), focusedContainerColor = OzColors.Panel, unfocusedBorderColor = OzColors.Outline.copy(alpha = .35f), focusedBorderColor = OzColors.Cyan))
}

@Composable
private fun GameCard(game: GameEntity, preferences: AppPreferences, onStart: (GameEntity) -> Unit = {}, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "oz-game-focus")
    val visual = remember(game.systemId) { ConsolePalette.forSystem(game.systemId) }
    val candidates = remember(game.id, game.title, game.thumbnailRepo) {
        ArtworkResolver.tileCandidates(context, preferences, game)
    }
    Box(
        Modifier
            .width(136.dp)
            .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO)
            .scale(scale)
            .clip(OzShapes.medium)
            .background(OzColors.PanelRaised)
            .border(1.5.dp, if (focused) OzColors.Cyan else visual.accent.copy(alpha = .42f), OzShapes.medium)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.ButtonStart) {
                    onStart(game); true
                } else false
            },
    ) {
        OzCoverImage(
            candidates = candidates,
            title = game.title,
            systemId = game.systemId,
            contentScale = ContentScale.Crop,
            showTitleOnFallback = true,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(visual.accent)
                .align(Alignment.TopCenter),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.52f to Color.Transparent, 1f to Color.Black.copy(alpha = .86f))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        ) {
            Text(
                CatalogFilters.displayTitle(game.title),
                style = OzTypography.meta.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                visual.shortLabel,
                style = OzTypography.meta.copy(fontSize = 9.sp),
                color = visual.accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CatalogFilterSheet(current: CatalogFilterState, onDismiss: () -> Unit, onApply: (CatalogFilterState) -> Unit) {
    var draft by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = OzColors.Panel, scrimColor = OzColors.Scrim) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Filtros", style = OzTypography.section, color = OzColors.TextPrimary); Spacer(Modifier.weight(1f)); OzButton("Limpiar") { draft = CatalogFilterState() }; Spacer(Modifier.width(8.dp)); OzButton("Aplicar") { onApply(draft); onDismiss() } }
            HorizontalDivider(color = OzColors.Outline.copy(alpha = .2f))
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FilterGroup("Región") { RegionMode.entries.forEach { mode -> FilterChip(selected = mode in draft.regions, onClick = { val next = draft.regions.toMutableSet().apply { if (!add(mode)) remove(mode) }; draft = draft.copy(regions = next).normalized() }, label = { Text(mode.label) }) } }
                FilterGroup("Plataformas") { PlatformFamily.entries.forEach { family -> FilterChip(selected = family in draft.platformFamilies, onClick = { val next = draft.platformFamilies.toMutableSet().apply { if (!add(family)) remove(family) }; draft = draft.copy(platformFamilies = next) }, label = { Text(family.label) }) } }
                FilterGroup("Estado") { OriginStatusFilter.entries.forEach { status -> FilterChip(selected = status in draft.status, onClick = { val next = draft.status.toMutableSet().apply { if (!add(status)) remove(status) }; draft = draft.copy(status = next) }, label = { Text(status.label) }) } }
            }
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column { Text(title, style = OzTypography.meta, color = OzColors.TextSecondary); FlowRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
private fun CollectionDetailScreen(viewModel: OzViewModel, onBack: () -> Unit, onGame: (String) -> Unit) {
    val summary by viewModel.selectedCollection.collectAsStateWithLifecycle(); val games = viewModel.collectionGames.collectAsLazyPagingItems(); val query by viewModel.collectionQuery.collectAsStateWithLifecycle(); val downloadUi by viewModel.downloadUi.collectAsStateWithLifecycle(); val artworkUi by viewModel.artworkSyncUi.collectAsStateWithLifecycle()
    val density by viewModel.gameGridDensity.collectAsStateWithLifecycle()
    val missingRoms = ((summary?.gameCount ?: 0) - (summary?.localCount ?: 0)).coerceAtLeast(0)
    val subtitle = if (summary != null) "${summary!!.gameCount} ROMs · $missingRoms faltantes" else summary?.collection?.description.orEmpty()
    ScreenShell(summary?.collection?.name ?: "Colección", subtitle) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(value = query, onValueChange = viewModel::setCollectionQuery, modifier = Modifier.weight(1f))
                OzButton("", icon = "⇩") { viewModel.downloadSelectedCollection() }
                OzButton("", icon = "▧") { viewModel.syncSelectedCollectionArtwork() }
                OzButton("iiSU", icon = "📂") { viewModel.syncSelectedCollectionWithIisu() }
                OzButton("", icon = "←") { onBack() }
            }
            if (downloadUi.running || artworkUi.running) {
                val progress = if (downloadUi.running) { if (downloadUi.total > 0) downloadUi.completed.toFloat() / downloadUi.total else 0f } else { if (artworkUi.total > 0) artworkUi.completed.toFloat() / artworkUi.total else 0f }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = OzColors.Cyan); Text(if (downloadUi.running) "Descargando: ${downloadUi.gameTitle} (${downloadUi.completed}/${downloadUi.total})" else "Arte: ${artworkUi.gameTitle} (${artworkUi.completed}/${artworkUi.total})", style = OzTypography.meta, color = OzColors.Cyan, modifier = Modifier.padding(14.dp))
            }
            Box(Modifier.weight(1f)) { PagedGameGrid(games, viewModel.preferences, onGame, gridDensity = density) }
        }
    }
}

@Composable
private fun SettingsBridgeScreen(
    viewModel: OzViewModel,
    onOpenFtpConfig: () -> Unit,
    onOpenPcHub: () -> Unit,
) {
    val context = LocalContext.current
    val iaState by viewModel.internetArchiveState.collectAsStateWithLifecycle()
    val isValidatingIA by viewModel.isValidatingIA.collectAsStateWithLifecycle()
    val autoLaunch by viewModel.autoLaunchAfterDownload.collectAsStateWithLifecycle()
    val autoCores by viewModel.retroAutoInstallCores.collectAsStateWithLifecycle()
    val ftpJobs by viewModel.ftpJobs.collectAsStateWithLifecycle()
    val ftpTargets by viewModel.ftpTargets.collectAsStateWithLifecycle()
    val pcHubUrl by viewModel.pcHubUrl.collectAsStateWithLifecycle()
    val pcHubStatus by viewModel.pcHubStatus.collectAsStateWithLifecycle()
    val preparation by viewModel.catalogPreparation.collectAsStateWithLifecycle()
    val artworkSyncUi by viewModel.artworkSyncUi.collectAsStateWithLifecycle()
    val savedGeminiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val savedGeminiModel by viewModel.geminiModel.collectAsStateWithLifecycle()

    var prefsRev by remember { mutableStateOf(0) }
    var showAdvanced by remember { mutableStateOf(false) }
    var geminiDraft by remember(savedGeminiKey) { mutableStateOf(savedGeminiKey) }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshInternetArchiveState(validate = true)
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
            prefsRev += 1
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshInternetArchiveState(validate = false) }
    prefsRev

    val iaStatusLabel = when {
        isValidatingIA -> "Validando…"
        iaState == InternetArchiveAuthManager.State.CONNECTED -> "Conectado"
        iaState == InternetArchiveAuthManager.State.STALE -> "Error de red"
        iaState == InternetArchiveAuthManager.State.INVALID -> "Sesión caducada"
        else -> "Opcional · Desconectado"
    }
    val romLabel = viewModel.preferences.globalRomTreeUri?.let(::humanLocalName) ?: "Sin carpeta seleccionada"
    val activeFtp = ftpJobs.count { it.state == FtpTransferState.QUEUED || it.state == FtpTransferState.UPLOADING }
    val selectedRegion = viewModel.preferences.preferredRegion
    val wifiOnly = viewModel.preferences.romgiWifiOnly
    val torrentsEnabled = viewModel.preferences.romgiTorrentsEnabled

    ScreenShell("Ajustes", "Pantalla · RetroArch · Descargas · Red") {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { AppearanceCard(viewModel) }

            item {
                RetroArchAutomationCard(
                    autoLaunch = autoLaunch,
                    autoInstallCores = autoCores,
                    onToggleAutoLaunch = viewModel::setAutoLaunchAfterDownload,
                    onToggleAutoCores = viewModel::setRetroAutoInstallCores,
                    onOpenEmulationDetails = {
                        context.startActivity(Intent(context, cl.gakuto.kizuna.ui.EmulationIntegrationSettingsActivity::class.java))
                    },
                )
            }

            item {
                StorageAndDownloadsCard(
                    romFolderLabel = romLabel,
                    selectedRegion = selectedRegion,
                    wifiOnly = wifiOnly,
                    torrentsEnabled = torrentsEnabled,
                    iaConnected = iaState == InternetArchiveAuthManager.State.CONNECTED,
                    iaStatusLabel = iaStatusLabel,
                    isValidatingIA = isValidatingIA,
                    onPickRomFolder = { romFolderPicker.launch(viewModel.preferences.globalRomTreeUri) },
                    onSelectRegion = { key ->
                        viewModel.preferences.preferredRegion = key
                        prefsRev += 1
                    },
                    onToggleWifiOnly = { enabled ->
                        viewModel.preferences.romgiWifiOnly = enabled
                        prefsRev += 1
                    },
                    onToggleTorrents = { enabled ->
                        viewModel.preferences.romgiTorrentsEnabled = enabled
                        prefsRev += 1
                    },
                    onConnectIA = { authLauncher.launch(Intent(context, InternetArchiveLoginActivity::class.java)) },
                    onDisconnectIA = { viewModel.disconnectInternetArchive() },
                )
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(OzShapes.large)
                        .background(OzColors.PanelRaised.copy(alpha = 0.65f))
                        .border(1.dp, OzColors.Cyan.copy(alpha = 0.36f), OzShapes.large)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("CARÁTULAS E IA DE SAGAS", style = OzTypography.section.copy(fontSize = 15.sp), color = OzColors.Cyan)
                    Text(
                        if (artworkSyncUi.running) {
                            "${artworkSyncUi.stage}: ${artworkSyncUi.gameTitle} (${artworkSyncUi.completed}/${artworkSyncUi.total})"
                        } else if (artworkSyncUi.total > 0) {
                            "Última sincronización: ${artworkSyncUi.resolved}/${artworkSyncUi.total} carátulas listas"
                        } else {
                            "Descarga carátulas faltantes de Libretro / SteamGridDB para toda tu biblioteca"
                        },
                        style = OzTypography.meta,
                        color = OzColors.TextSecondary,
                    )
                    if (artworkSyncUi.running && artworkSyncUi.total > 0) {
                        LinearProgressIndicator(
                            progress = { (artworkSyncUi.completed.toFloat() / artworkSyncUi.total.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = OzColors.Cyan,
                            trackColor = OzColors.Panel,
                        )
                    }
                    OzButton(
                        label = if (artworkSyncUi.running) "Sincronizando carátulas…" else "🖼 Sincronizar carátulas ahora",
                        emphasized = true,
                        icon = "↻",
                        enabled = !artworkSyncUi.running,
                    ) { viewModel.syncAllArtwork() }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (savedGeminiKey.isBlank()) "Gemini API (Opcional · Motor Retro Local activo)" else "Gemini API Conectada ($savedGeminiModel)",
                        style = OzTypography.tileTitle.copy(fontSize = 14.sp),
                        color = OzColors.TextPrimary,
                    )
                    Text(
                        "Sin clave funciona en modo local sin límites. Para auditoría IA de sagas con Gemini, crea tu clave gratis en Google AI Studio (aistudio.google.com/apikey).",
                        style = OzTypography.meta,
                        color = OzColors.TextSecondary,
                    )
                    OutlinedTextField(
                        value = geminiDraft,
                        onValueChange = { geminiDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Gemini API Key (opcional)", style = OzTypography.meta) },
                        singleLine = true,
                        shape = OzShapes.medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("gemini-2.5-flash", "gemini-2.5-pro").forEach { modelId ->
                            FilterChip(
                                selected = savedGeminiModel == modelId,
                                onClick = { viewModel.setGeminiModel(modelId) },
                                label = { Text(modelId, style = OzTypography.meta) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.setGeminiApiKey(geminiDraft) },
                            shape = OzShapes.pill,
                            colors = ButtonDefaults.buttonColors(containerColor = OzColors.Cyan, contentColor = OzColors.DeepSpace),
                        ) { Text("Guardar Gemini", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold)) }
                        Button(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/apikey")))
                            },
                            shape = OzShapes.pill,
                            colors = ButtonDefaults.buttonColors(containerColor = OzColors.Panel, contentColor = OzColors.Cyan),
                            border = BorderStroke(1.dp, OzColors.Cyan.copy(alpha = 0.45f)),
                        ) { Text("🌐 Obtener API Key", style = OzTypography.meta) }
                    }
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(OzShapes.large)
                        .background(OzColors.PanelRaised.copy(alpha = 0.55f))
                        .border(1.dp, OzColors.Gold.copy(alpha = 0.35f), OzShapes.large)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("CONEXIONES EN SEGUNDO PLANO", style = OzTypography.section.copy(fontSize = 15.sp), color = OzColors.Gold)
                    SettingCard(
                        title = "Consolas FTP",
                        subtitle = "${ftpTargets.size} destinos (PS4 / PS3 / Switch) · $activeFtp activas",
                        symbol = "📡",
                        onClick = onOpenFtpConfig,
                    )
                    SettingCard(
                        title = "Oz Game Vault PC Hub (LAN Wi-Fi)",
                        subtitle = pcHubStatus.ifBlank { pcHubUrl.ifBlank { "Conecta tu biblioteca y catálogo de PC" } },
                        symbol = "🖥",
                        onClick = onOpenPcHub,
                    )
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(OzShapes.large)
                        .background(OzColors.PanelRaised.copy(alpha = 0.4f))
                        .border(1.dp, OzColors.Outline.copy(alpha = 0.28f), OzShapes.large)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Opciones avanzadas", style = OzTypography.tileTitle, color = OzColors.TextPrimary)
                            Text("Rutas por consola, SteamGridDB, caché y catálogo Libretro completo", style = OzTypography.meta, color = OzColors.TextSecondary)
                        }
                        Text(if (showAdvanced) "▲" else "▼", style = OzTypography.meta, color = OzColors.Cyan)
                    }
                    if (showAdvanced) {
                        SettingCard(
                            title = "Rutas por consola y artwork local",
                            subtitle = "Carpetas individuales · SteamGridDB API",
                            symbol = "▣",
                        ) {
                            context.startActivity(Intent(context, cl.gakuto.kizuna.ui.LibrarySettingsActivity::class.java))
                        }
                        SettingCard(
                            title = "Caché y fuentes ROMgi avanzadas",
                            subtitle = "Paginación y limpieza de caché local",
                            symbol = "⚙",
                        ) {
                            context.startActivity(Intent(context, cl.gakuto.kizuna.ui.CatalogDownloadSettingsActivity::class.java))
                        }
                        if (preparation.running) {
                            Text(
                                "Sincronizando catálogo Libretro: ${preparation.currentSystem} (${preparation.readySystems}/${preparation.totalSystems})",
                                style = OzTypography.meta,
                                color = OzColors.Cyan,
                            )
                        } else if (preparation.readySystems < preparation.totalSystems) {
                            OzButton(
                                label = "Sincronizar sistemas Libretro (${preparation.totalSystems - preparation.readySystems})",
                                emphasized = false,
                                icon = "↻",
                            ) { viewModel.prepareCompleteCatalog() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageAndDownloadsCard(
    romFolderLabel: String,
    selectedRegion: String,
    wifiOnly: Boolean,
    torrentsEnabled: Boolean,
    iaConnected: Boolean,
    iaStatusLabel: String,
    isValidatingIA: Boolean,
    onPickRomFolder: () -> Unit,
    onSelectRegion: (String) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onToggleTorrents: (Boolean) -> Unit,
    onConnectIA: () -> Unit,
    onDisconnectIA: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(OzShapes.large)
            .background(OzColors.PanelRaised.copy(alpha = 0.58f))
            .border(1.dp, OzColors.Outline.copy(alpha = 0.28f), OzShapes.large)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("ALMACENAMIENTO Y DESCARGAS", style = OzTypography.section.copy(fontSize = 15.sp), color = OzColors.Cyan)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Carpeta principal de ROMs", style = OzTypography.tileTitle, color = OzColors.TextPrimary)
                Text(romFolderLabel, style = OzTypography.meta, color = OzColors.Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OzButton("Cambiar", icon = "📂") { onPickRomFolder() }
        }

        Text("REGIÓN PREFERIDA", style = OzTypography.meta, color = OzColors.TextSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RomRegionPreference.entries.forEach { reg ->
                val shortName = when (reg) {
                    RomRegionPreference.USA -> "USA"
                    RomRegionPreference.WORLD -> "World"
                    RomRegionPreference.EUROPE -> "Europa"
                    RomRegionPreference.JAPAN -> "Japón"
                    RomRegionPreference.ASIA -> "Asia"
                }
                ThemeChoice(
                    label = shortName,
                    selected = selectedRegion == reg.key,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectRegion(reg.key) },
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Descargar solo por Wi-Fi", style = OzTypography.tileTitle, color = OzColors.TextPrimary)
                Text("Evita consumo accidental con datos móviles", style = OzTypography.meta, color = OzColors.TextSecondary)
            }
            Switch(checked = wifiOnly, onCheckedChange = onToggleWifiOnly)
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Permitir descargas MiNERVA (P2P)", style = OzTypography.tileTitle, color = OzColors.TextPrimary)
                Text("Usa torrent selectivo por juego desde el catálogo", style = OzTypography.meta, color = OzColors.TextSecondary)
            }
            Switch(checked = torrentsEnabled, onCheckedChange = onToggleTorrents)
        }

        HorizontalDivider(color = OzColors.Outline.copy(alpha = 0.2f))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Internet Archive (cuenta opcional)", style = OzTypography.tileTitle, color = OzColors.TextPrimary)
                Text(iaStatusLabel, style = OzTypography.meta, color = if (iaConnected) OzColors.Positive else OzColors.TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OzButton(if (iaConnected) "Validar" else "Conectar", enabled = !isValidatingIA) { onConnectIA() }
                if (iaConnected) {
                    OzButton("", enabled = !isValidatingIA, icon = "✕") { onDisconnectIA() }
                }
            }
        }
    }
}

@Composable
private fun RetroArchAutomationCard(
    autoLaunch: Boolean,
    autoInstallCores: Boolean,
    onToggleAutoLaunch: (Boolean) -> Unit,
    onToggleAutoCores: (Boolean) -> Unit,
    onOpenEmulationDetails: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(OzShapes.large)
            .background(OzColors.PanelRaised.copy(alpha = 0.58f))
            .border(1.dp, OzColors.Cyan.copy(alpha = 0.35f), OzShapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("RETROARCH E IISU", style = OzTypography.section.copy(fontSize = 15.sp), color = OzColors.Cyan)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ejecutar RetroArch automáticamente al bajar", style = OzTypography.tileTitle, color = OzColors.TextPrimary)
                Text("Descarga el juego y abre el emulador directo al terminar", style = OzTypography.meta, color = OzColors.TextSecondary)
            }
            Switch(checked = autoLaunch, onCheckedChange = onToggleAutoLaunch)
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Instalar cores faltantes automáticamente", style = OzTypography.tileTitle, color = OzColors.TextPrimary)
                Text("Verifica cores de RetroArch y descarga el que falte antes de abrir", style = OzTypography.meta, color = OzColors.TextSecondary)
            }
            Switch(checked = autoInstallCores, onCheckedChange = onToggleAutoCores)
        }
        SettingCard(
            title = "Cores, BIOS, overlays e iiSU",
            subtitle = "Configuración detallada de emuladores y puente virtual",
            symbol = "🎮",
            onClick = onOpenEmulationDetails,
        )
    }
}

@Composable
private fun AppearanceCard(viewModel: OzViewModel) {
    val themeMode by viewModel.appThemeMode.collectAsStateWithLifecycle()
    val orientationMode by viewModel.appOrientationMode.collectAsStateWithLifecycle()
    val density by viewModel.gameGridDensity.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().clip(OzShapes.large).background(OzColors.PanelRaised.copy(alpha = 0.5f)).border(1.dp, OzColors.Outline.copy(alpha = .2f), OzShapes.large).padding(16.dp)) {
        Text("APARIENCIA Y PANTALLA", style = OzTypography.section.copy(fontSize = 15.sp), color = OzColors.TextPrimary)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppThemeMode.entries.forEach { mode -> ThemeChoice(mode.label, selected = mode == themeMode, Modifier.weight(1f)) { viewModel.setAppThemeMode(mode) } }
        }
        Spacer(Modifier.height(12.dp))
        Text("ORIENTACIÓN (TELÉFONO VERTICAL / TABLET HORIZONTAL)", style = OzTypography.meta, color = OzColors.Gold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOrientationMode.entries.forEach { mode -> ThemeChoice(mode.label, selected = mode == orientationMode, Modifier.weight(1f)) { viewModel.setAppOrientationMode(mode) } }
        }
        Spacer(Modifier.height(12.dp))
        Text("COLUMNAS DE CARÁTULAS DS", style = OzTypography.meta, color = OzColors.TextSecondary)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameGridDensity.entries.forEach { d ->
                val shortLabel = when (d) {
                    GameGridDensity.AUTO -> "Auto (3)"
                    GameGridDensity.SMALL -> "Compacta (4)"
                    GameGridDensity.NORMAL -> "DS (3)"
                    GameGridDensity.LARGE -> "Grande (2)"
                }
                ThemeChoice(shortLabel, selected = d == density, Modifier.weight(1f)) { viewModel.setGameGridDensity(d) }
            }
        }
    }
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.heightIn(min = 42.dp).clip(OzShapes.small).background(if (selected) OzColors.Cyan.copy(alpha = .14f) else OzColors.PanelRaised.copy(alpha = 0.4f)).border(1.dp, if (selected) OzColors.Cyan else OzColors.Outline.copy(alpha = .3f), OzShapes.small).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Text(label.uppercase(), style = OzTypography.cardTitle.copy(fontSize = 10.sp), color = if (selected) OzColors.Cyan else OzColors.TextPrimary, maxLines = 1) }
}

@Composable
private fun SettingCard(title: String, subtitle: String, symbol: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(OzShapes.large).background(OzColors.PanelRaised.copy(alpha = 0.6f)).border(1.dp, OzColors.Outline.copy(alpha = .3f), OzShapes.large).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(symbol, style = OzTypography.section, color = OzColors.Cyan, modifier = Modifier.width(40.dp)); Column { Text(title, style = OzTypography.section.copy(fontSize = 15.sp), color = OzColors.TextPrimary); Text(subtitle.uppercase(), style = OzTypography.meta.copy(fontSize = 9.sp), color = OzColors.TextSecondary) } }
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(games.itemCount) { index ->
                val game = games[index]
                if (game != null) {
                    val baseMod = Modifier
                        .fillMaxWidth()
                        .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO)
                    OzSurfaceTile(
                        game = game,
                        preferences = preferences,
                        selected = false,
                        onFocused = {},
                        onOpen = { onGame(game.id) },
                        onStart = { onStartGame(game) },
                        modifier = if (index == 0) baseMod.focusRequester(firstGameFocusRequester) else baseMod,
                    )
                }
            }
            if (games.loadState.append is LoadState.Loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OzColors.Cyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun OzButton(label: String = "", emphasized: Boolean = false, enabled: Boolean = true, icon: String? = null, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = if (emphasized) OzColors.Cyan.copy(alpha = 0.15f) else OzColors.PanelRaised.copy(alpha = 0.6f), contentColor = if (emphasized) OzColors.Cyan else OzColors.TextPrimary), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (emphasized) OzColors.Cyan.copy(alpha = 0.5f) else OzColors.Outline.copy(alpha = 0.2f)), contentPadding = PaddingValues(horizontal = if (label.isEmpty()) 10.dp else 16.dp, vertical = 8.dp)) { if (icon != null) { Text(icon, style = OzTypography.section.copy(fontSize = 18.sp)); if (label.isNotBlank()) Spacer(Modifier.width(8.dp)) }; if (label.isNotBlank()) { Text(label.uppercase(), style = OzTypography.meta) } }
}

private fun humanLocalName(uri: Uri): String = uri.path?.substringAfterLast(':')?.substringAfterLast('/') ?: "Carpeta local"

package cl.gakuto.kizuna.ui.compose.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.gakuto.kizuna.catalog.GameSynopsis
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.launch.SessionActivity
import cl.gakuto.kizuna.patcher.SpanishPatchCandidate
import cl.gakuto.kizuna.sagas.SagaSummaryRef
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.ConsoleBadge
import cl.gakuto.kizuna.ui.compose.components.OZ_NDS_COVER_ASPECT_RATIO
import cl.gakuto.kizuna.ui.compose.components.OzCoverImage
import cl.gakuto.kizuna.ui.compose.components.OzStatusPill
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import cl.gakuto.kizuna.util.ArtworkResolver

@Composable
fun GameDetailScreen(
    viewModel: OzViewModel,
    gameId: String,
    onBack: () -> Unit,
    onOpenSaga: (String) -> Unit,
) {
    LaunchedEffect(gameId) {
        viewModel.openGame(gameId)
    }

    val context = LocalContext.current
    val game by viewModel.selectedGame.collectAsState()
    val synopsis by viewModel.selectedSynopsis.collectAsState()
    val patchedRom by viewModel.selectedPatchedRom.collectAsState()
    val patchCandidates by viewModel.patchCandidates.collectAsState()
    val isSearchingPatches by viewModel.isSearchingPatches.collectAsState()
    val isApplyingPatch by viewModel.isApplyingPatch.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    var showFtpDialog by remember { mutableStateOf(false) }
    var usePatchedForFtp by remember { mutableStateOf(false) }

    val patchPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val currentGame = game ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            val filename = resolveDisplayName(context.contentResolver, uri)
            viewModel.applyLocalPatchUri(currentGame, uri, filename)
        }
    }

    val currentGame = game
    if (currentGame == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cargando juego…", style = OzTypography.section, color = OzColors.TextSecondary)
        }
        return
    }

    if (showFtpDialog) {
        FtpSendDialog(
            game = currentGame,
            viewModel = viewModel,
            usePatched = usePatchedForFtp,
            onDismiss = { showFtpDialog = false },
        )
    }

    val consoleVisual = remember(currentGame.systemId) { ConsolePalette.forSystem(currentGame.systemId) }
    val isFavorite = remember(currentGame.id, favorites) { favorites.any { it.id == currentGame.id } }
    val sagaRef = remember(currentGame.id, currentGame.title) { viewModel.sagaForGame(currentGame) }
    val artworkCandidates = remember(currentGame.id, currentGame.title, currentGame.thumbnailRepo) {
        ArtworkResolver.candidates(context, viewModel.preferences, currentGame)
    }

    val launchGame: (downloadOnly: Boolean, patchedPath: String?) -> Unit = { downloadOnly, patchedPath ->
        val intent = Intent(context, SessionActivity::class.java).apply {
            putExtra(SessionActivity.EXTRA_GAME_ID, currentGame.id)
            if (downloadOnly) putExtra(SessionActivity.EXTRA_DOWNLOAD_ONLY, true)
            if (!patchedPath.isNullOrBlank()) putExtra(SessionActivity.EXTRA_PATCHED_ROM_PATH, patchedPath)
        }
        context.startActivity(intent)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 740.dp || maxWidth > maxHeight
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        GameLeftVisualPane(
                            game = currentGame,
                            artworkCandidates = artworkCandidates,
                            accent = consoleVisual.accent,
                            isFavorite = isFavorite,
                            patchedRom = patchedRom,
                            onBack = onBack,
                            onPlay = { launchGame(false, null) },
                            onPlayPatched = { launchGame(false, patchedRom?.path) },
                            onDownloadOnly = { launchGame(true, null) },
                            onOpenFtp = {
                                usePatchedForFtp = patchedRom != null
                                showFtpDialog = true
                            },
                            onPublishIisu = { viewModel.publishGameToIisu(currentGame) },
                            onToggleFavorite = { viewModel.toggleFavorite(currentGame.id) },
                            onDeleteLocal = { viewModel.deleteDownloadedRom(currentGame.id) },
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        GameHeaderMetaCard(game = currentGame, accent = consoleVisual.accent, patchedRom = patchedRom)
                    }
                    if (sagaRef != null) {
                        item { SagaLinkBanner(sagaRef = sagaRef, onClick = { onOpenSaga(sagaRef.id) }) }
                    }
                    item {
                        GameSynopsisCard(synopsis = synopsis, accent = consoleVisual.accent)
                    }
                    item {
                        TranslationPatcherCard(
                            game = currentGame,
                            patchedRom = patchedRom,
                            candidates = patchCandidates,
                            isSearching = isSearchingPatches,
                            isApplying = isApplyingPatch,
                            onPickLocalPatch = { patchPickerLauncher.launch(arrayOf("*/*")) },
                            onSearchOnline = { viewModel.searchSpanishPatches(currentGame) },
                            onApplyCandidate = { viewModel.applyRemoteSpanishPatch(currentGame, it) },
                            onPlayPatched = { launchGame(false, patchedRom?.path) },
                            onClearPatch = { viewModel.clearPatchedRom(currentGame.id) },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GameLeftVisualPane(
                        game = currentGame,
                        artworkCandidates = artworkCandidates,
                        accent = consoleVisual.accent,
                        isFavorite = isFavorite,
                        patchedRom = patchedRom,
                        onBack = onBack,
                        onPlay = { launchGame(false, null) },
                        onPlayPatched = { launchGame(false, patchedRom?.path) },
                        onDownloadOnly = { launchGame(true, null) },
                        onOpenFtp = {
                            usePatchedForFtp = patchedRom != null
                            showFtpDialog = true
                        },
                        onPublishIisu = { viewModel.publishGameToIisu(currentGame) },
                        onToggleFavorite = { viewModel.toggleFavorite(currentGame.id) },
                        onDeleteLocal = { viewModel.deleteDownloadedRom(currentGame.id) },
                    )
                }
                item {
                    GameHeaderMetaCard(game = currentGame, accent = consoleVisual.accent, patchedRom = patchedRom)
                }
                if (sagaRef != null) {
                    item { SagaLinkBanner(sagaRef = sagaRef, onClick = { onOpenSaga(sagaRef.id) }) }
                }
                item {
                    GameSynopsisCard(synopsis = synopsis, accent = consoleVisual.accent)
                }
                item {
                    TranslationPatcherCard(
                        game = currentGame,
                        patchedRom = patchedRom,
                        candidates = patchCandidates,
                        isSearching = isSearchingPatches,
                        isApplying = isApplyingPatch,
                        onPickLocalPatch = { patchPickerLauncher.launch(arrayOf("*/*")) },
                        onSearchOnline = { viewModel.searchSpanishPatches(currentGame) },
                        onApplyCandidate = { viewModel.applyRemoteSpanishPatch(currentGame, it) },
                        onPlayPatched = { launchGame(false, patchedRom?.path) },
                        onClearPatch = { viewModel.clearPatchedRom(currentGame.id) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameLeftVisualPane(
    game: GameEntity,
    artworkCandidates: List<String>,
    accent: Color,
    isFavorite: Boolean,
    patchedRom: AppPreferences.PatchedRomMeta?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayPatched: () -> Unit,
    onDownloadOnly: () -> Unit,
    onOpenFtp: () -> Unit,
    onPublishIisu: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteLocal: () -> Unit,
) {
    val hasLocal = !game.contentUri.isNullOrBlank()

    Surface(
        shape = OzShapes.hero,
        color = OzColors.Panel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹ Volver",
                    style = OzTypography.meta,
                    color = OzColors.Cyan,
                    modifier = Modifier
                        .clip(OzShapes.pill)
                        .background(OzColors.PanelRaised)
                        .border(1.dp, OzColors.Cyan.copy(alpha = 0.4f), OzShapes.pill)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                ConsoleBadge(systemId = game.systemId)
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO)
                    .clip(OzShapes.large)
                    .border(1.dp, accent.copy(alpha = 0.45f), OzShapes.large),
                contentAlignment = Alignment.Center,
            ) {
                OzCoverImage(
                    candidates = artworkCandidates,
                    title = game.title,
                    systemId = game.systemId,
                    contentScale = ContentScale.Fit,
                    showTitleOnFallback = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            if (patchedRom != null) {
                Button(
                    onClick = onPlayPatched,
                    shape = OzShapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OzColors.Gold,
                        contentColor = OzColors.DeepSpace,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 13.dp),
                ) {
                    Text("▶ JUGAR VERSIÓN [ES] EN RETROARCH", style = OzTypography.tileTitle.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onPlay,
                shape = OzShapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasLocal) OzColors.Positive else OzColors.Cyan,
                    contentColor = OzColors.DeepSpace,
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 13.dp),
            ) {
                Text(
                    if (hasLocal) "▶ JUGAR AHORA EN RETROARCH" else "▶ DESCARGAR Y JUGAR EN RETROARCH",
                    style = OzTypography.tileTitle.copy(fontWeight = FontWeight.Bold),
                )
            }

            Spacer(Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!hasLocal) {
                    SecondaryActionPill("⬇ Solo descargar", OzColors.TextPrimary, onDownloadOnly)
                }
                SecondaryActionPill("📡 Enviar por FTP", OzColors.Gold, onOpenFtp)
                SecondaryActionPill("📂 Publicar en iiSU", OzColors.Cyan, onPublishIisu)
                SecondaryActionPill(
                    if (isFavorite) "★ En favoritos" else "☆ Favorito",
                    if (isFavorite) OzColors.Gold else OzColors.TextSecondary,
                    onToggleFavorite,
                )
                if (hasLocal) {
                    SecondaryActionPill("🗑 Borrar ROM", OzColors.Error, onDeleteLocal)
                }
            }
        }
    }
}

@Composable
private fun SecondaryActionPill(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = OzTypography.meta,
        color = color,
        modifier = Modifier
            .clip(OzShapes.pill)
            .background(OzColors.PanelRaised)
            .border(1.dp, color.copy(alpha = 0.42f), OzShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameHeaderMetaCard(
    game: GameEntity,
    accent: Color,
    patchedRom: AppPreferences.PatchedRomMeta?,
) {
    Surface(
        shape = OzShapes.large,
        color = OzColors.Panel,
        border = BorderStroke(1.dp, OzColors.Outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(game.title, style = OzTypography.hero, color = OzColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OzStatusPill(game.platform, accent)
                if (game.year != null) OzStatusPill("${game.year}", OzColors.TextPrimary)
                if (game.region.isNotBlank() && !game.region.equals("Sin región", true)) {
                    OzStatusPill(game.region, OzColors.Cyan)
                }
                if (game.genre.isNotBlank() && !game.genre.equals("Sin clasificar", true)) {
                    OzStatusPill(game.genre, OzColors.ArcadeViolet)
                }
                OzStatusPill(
                    if (!game.contentUri.isNullOrBlank()) "ROM LOCAL LISTA" else "DISPONIBLE EN CATÁLOGO",
                    if (!game.contentUri.isNullOrBlank()) OzColors.Positive else OzColors.Cyan,
                )
                if (patchedRom != null) {
                    OzStatusPill("PARCHE [ES] ${patchedRom.format}", OzColors.Gold)
                }
            }
        }
    }
}

@Composable
private fun SagaLinkBanner(sagaRef: SagaSummaryRef, onClick: () -> Unit) {
    val accent = ConsolePalette.parseHexColor(sagaRef.accentHex, OzColors.Cyan)
    Surface(
        shape = OzShapes.large,
        color = OzColors.PanelRaised,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(OzShapes.large)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("PARTE DE LA SAGA", style = OzTypography.meta, color = accent)
                Text(sagaRef.name, style = OzTypography.cardTitle, color = OzColors.TextPrimary)
                Text(
                    "${sagaRef.localOwnedCount} de ${sagaRef.totalCount} entregas en tu bóveda",
                    style = OzTypography.meta,
                    color = OzColors.TextSecondary,
                )
            }
            Text("Ver cronología ›", style = OzTypography.meta, color = accent)
        }
    }
}

@Composable
private fun GameSynopsisCard(synopsis: GameSynopsis?, accent: Color) {
    Surface(
        shape = OzShapes.large,
        color = OzColors.Panel,
        border = BorderStroke(1.dp, OzColors.Outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sinopsis del juego", style = OzTypography.cardTitle, color = accent)
                if (synopsis != null) {
                    Text(synopsis.source.uppercase(), style = OzTypography.meta, color = OzColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                synopsis?.description ?: "Obteniendo descripción…",
                style = OzTypography.body,
                color = OzColors.TextPrimary.copy(alpha = 0.92f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslationPatcherCard(
    game: GameEntity,
    patchedRom: AppPreferences.PatchedRomMeta?,
    candidates: List<SpanishPatchCandidate>,
    isSearching: Boolean,
    isApplying: Boolean,
    onPickLocalPatch: () -> Unit,
    onSearchOnline: () -> Unit,
    onApplyCandidate: (SpanishPatchCandidate) -> Unit,
    onPlayPatched: () -> Unit,
    onClearPatch: () -> Unit,
) {
    val hasLocalRom = !game.contentUri.isNullOrBlank()

    Surface(
        shape = OzShapes.large,
        color = OzColors.Panel,
        border = BorderStroke(1.dp, OzColors.Gold.copy(alpha = 0.42f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Traducción al Español (.ips / .bps / .zip)", style = OzTypography.cardTitle, color = OzColors.Gold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Aplica parches IPS/BPS directo en tu teléfono sin PC y ejecuta la versión [ES] automáticamente en RetroArch.",
                style = OzTypography.meta,
                color = OzColors.TextSecondary,
            )

            if (patchedRom != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = OzShapes.medium,
                    color = OzColors.Positive.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, OzColors.Positive.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Parche aplicado: ${patchedRom.label}", style = OzTypography.tileTitle, color = OzColors.Positive)
                            Text("Formato ${patchedRom.format} · Listo para RetroArch o FTP", style = OzTypography.meta, color = OzColors.TextSecondary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "▶ [ES]",
                                style = OzTypography.meta.copy(fontWeight = FontWeight.Bold),
                                color = OzColors.DeepSpace,
                                modifier = Modifier
                                    .clip(OzShapes.pill)
                                    .background(OzColors.Positive)
                                    .clickable(onClick = onPlayPatched)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                            Text(
                                "Quitar",
                                style = OzTypography.meta,
                                color = OzColors.Error,
                                modifier = Modifier
                                    .clip(OzShapes.pill)
                                    .background(OzColors.PanelRaised)
                                    .clickable(onClick = onClearPatch)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            if (isSearching || isApplying) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    color = OzColors.Gold,
                    trackColor = OzColors.PanelRaised,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(OzShapes.pill),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isApplying) "Parcheando ROM en memoria…" else "Buscando traducciones [ES] en Internet Archive…",
                    style = OzTypography.meta,
                    color = OzColors.Gold,
                )
            }

            Spacer(Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSearchOnline,
                    enabled = !isSearching && !isApplying,
                    shape = OzShapes.pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OzColors.Gold.copy(alpha = 0.2f),
                        contentColor = OzColors.Gold,
                    ),
                    border = BorderStroke(1.dp, OzColors.Gold.copy(alpha = 0.55f)),
                ) {
                    Text("🔍 Buscar traducción [ES]", style = OzTypography.meta)
                }
                Button(
                    onClick = onPickLocalPatch,
                    enabled = !isApplying,
                    shape = OzShapes.pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OzColors.PanelRaised,
                        contentColor = OzColors.Cyan,
                    ),
                    border = BorderStroke(1.dp, OzColors.Cyan.copy(alpha = 0.45f)),
                ) {
                    Text("📁 Elegir .ips / .bps / .zip", style = OzTypography.meta)
                }
            }

            if (!hasLocalRom) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Nota: descarga o juega la ROM primero una vez para aplicar el parche encima del archivo base.",
                    style = OzTypography.meta,
                    color = OzColors.Warning,
                )
            }

            if (candidates.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                candidates.forEach { candidate ->
                    Surface(
                        shape = OzShapes.medium,
                        color = OzColors.PanelRaised,
                        border = BorderStroke(1.dp, OzColors.Outline.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    candidate.filename,
                                    style = OzTypography.tileTitle,
                                    color = OzColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${candidate.format} · ${candidate.title}",
                                    style = OzTypography.meta,
                                    color = OzColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onApplyCandidate(candidate) },
                                enabled = hasLocalRom && !isApplying,
                                shape = OzShapes.pill,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OzColors.Gold,
                                    contentColor = OzColors.DeepSpace,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Aplicar", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveDisplayName(resolver: android.content.ContentResolver, uri: Uri): String {
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx) ?: "patch"
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "patch"
}

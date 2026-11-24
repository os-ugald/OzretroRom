package cl.gakuto.kizuna.ui.compose.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.sagas.SagaEntryItem
import cl.gakuto.kizuna.sagas.SagaSnapshot
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.ConsoleBadge
import cl.gakuto.kizuna.ui.compose.components.OZ_NDS_COVER_ASPECT_RATIO
import cl.gakuto.kizuna.ui.compose.components.OzCoverImage
import cl.gakuto.kizuna.ui.compose.components.OzStatusPill
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography

private enum class SagasFilterScope(val label: String) {
    ALL("Todas"),
    IN_VAULT("En mi bóveda"),
    ERA_8_16("8 / 16-bit"),
    ERA_3D_ARCADE("3D y Arcade"),
}

@Composable
fun SagasGalleryScreen(
    viewModel: OzViewModel,
    onSagaClick: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBackToCollections: () -> Unit = {},
) {
    val sagas by viewModel.sagas.collectAsState()
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(SagasFilterScope.ALL) }

    val filtered = remember(sagas, query, scope) {
        val needle = query.trim().lowercase()
        sagas.filter { saga ->
            val scopeOk = when (scope) {
                SagasFilterScope.ALL -> true
                SagasFilterScope.IN_VAULT -> saga.localOwnedCount > 0
                SagasFilterScope.ERA_8_16 -> saga.consoles.any {
                    it in setOf("nes", "snes", "genesis", "md", "sms", "gb", "gbc", "gba")
                }
                SagasFilterScope.ERA_3D_ARCADE -> saga.consoles.any {
                    it in setOf("arcade", "arcade-fbneo", "fbneo", "mame", "ps1", "n64", "dc", "saturn", "psp", "gc", "ps2")
                }
            }
            val queryOk = needle.isEmpty() ||
                saga.name.lowercase().contains(needle) ||
                saga.creator.lowercase().contains(needle) ||
                saga.tagline.lowercase().contains(needle) ||
                saga.consoles.any { it.lowercase().contains(needle) }
            scopeOk && queryOk
        }
    }

    val sagasWithOwned = remember(sagas) { sagas.count { it.localOwnedCount > 0 } }
    val showSpotlight = query.isBlank() && scope == SagasFilterScope.ALL && filtered.size > 1
    val spotlight = if (showSpotlight) filtered.firstOrNull() else null
    val gridSagas = if (spotlight != null) filtered.drop(1) else filtered

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(OzColors.DeepSpace)) {
        val isWide = maxWidth >= 720.dp || maxWidth > maxHeight
        val cols = when {
            maxWidth >= 1080.dp -> 4
            isWide -> 3
            else -> 2
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isWide) 18.dp else 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SAGAS", style = OzTypography.hero, color = OzColors.TextPrimary)
                    Text(
                        "${sagas.size} cronologías curadas · $sagasWithOwned con juegos en tu bóveda",
                        style = OzTypography.meta,
                        color = OzColors.Cyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SagaSearchInput(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                    )
                    SagaScopeRow(scope = scope, onSelect = { scope = it })
                }
            } else {
                SagaSearchInput(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                SagaScopeRow(scope = scope, onSelect = { scope = it })
            }

            Spacer(Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No hay sagas con ese filtro", style = OzTypography.section, color = OzColors.TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Prueba cambiando a \"Todas\" o limpia el buscador.",
                            style = OzTypography.body,
                            color = OzColors.TextSecondary,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (spotlight != null) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "spotlight-${spotlight.id}") {
                            SagaSpotlightCard(
                                saga = spotlight,
                                onClick = { onSagaClick(spotlight.id) },
                            )
                        }
                    }
                    items(gridSagas, key = { it.id }) { saga ->
                        SagaBannerCard(
                            saga = saga,
                            onClick = { onSagaClick(saga.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SagaSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Buscar saga, creador o consola…", style = OzTypography.body) },
        singleLine = true,
        shape = OzShapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OzColors.Cyan,
            unfocusedBorderColor = OzColors.Outline.copy(alpha = 0.45f),
            focusedContainerColor = OzColors.Panel.copy(alpha = 0.9f),
            unfocusedContainerColor = OzColors.Panel.copy(alpha = 0.72f),
            focusedTextColor = OzColors.TextPrimary,
            unfocusedTextColor = OzColors.TextPrimary,
        ),
        modifier = modifier,
    )
}

@Composable
private fun SagaScopeRow(
    scope: SagasFilterScope,
    onSelect: (SagasFilterScope) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SagasFilterScope.entries) { entry ->
            val selected = entry == scope
            FilterChip(
                selected = selected,
                onClick = { onSelect(entry) },
                label = { Text(entry.label, style = OzTypography.meta) },
                shape = OzShapes.pill,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = OzColors.Cyan.copy(alpha = 0.18f),
                    selectedLabelColor = OzColors.Cyan,
                    containerColor = OzColors.Panel,
                    labelColor = OzColors.TextSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = OzColors.Outline.copy(alpha = 0.35f),
                    selectedBorderColor = OzColors.Cyan.copy(alpha = 0.75f),
                ),
            )
        }
    }
}

@Composable
private fun SagaSpotlightCard(
    saga: SagaSnapshot,
    onClick: () -> Unit,
) {
    val accent = remember(saga.accentHex) { ConsolePalette.parseHexColor(saga.accentHex, OzColors.Cyan) }
    val progress = remember(saga.localOwnedCount, saga.totalCount) {
        if (saga.totalCount <= 0) 0f else (saga.localOwnedCount.toFloat() / saga.totalCount.toFloat()).coerceIn(0f, 1f)
    }
    val bannerCands = remember(saga.id, saga.heroBannerUrl, saga.heroBannerCandidates) {
        saga.heroBannerCandidates.ifEmpty { listOf(saga.heroBannerUrl, saga.keyArtUrl) }
    }
    val keyArtCands = remember(saga.id, saga.keyArtUrl, saga.keyArtCandidates) {
        saga.keyArtCandidates.ifEmpty { listOf(saga.keyArtUrl, saga.heroBannerUrl) }
    }
    val primaryConsole = saga.consoles.firstOrNull() ?: "snes"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OzShapes.hero)
            .clickable(onClick = onClick),
        shape = OzShapes.hero,
        color = OzColors.Panel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(152.dp)) {
            OzCoverImage(
                candidates = bannerCands,
                title = saga.name,
                systemId = primaryConsole,
                contentScale = ContentScale.Crop,
                showTitleOnFallback = false,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                OzColors.DeepSpace.copy(alpha = 0.94f),
                                OzColors.DeepSpace.copy(alpha = 0.80f),
                                OzColors.DeepSpace.copy(alpha = 0.42f),
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(114.dp)
                        .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO)
                        .clip(OzShapes.medium)
                        .border(1.5.dp, accent.copy(alpha = 0.8f), OzShapes.medium),
                ) {
                    OzCoverImage(
                        candidates = keyArtCands,
                        title = saga.name,
                        systemId = primaryConsole,
                        contentScale = ContentScale.Crop,
                        showTitleOnFallback = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OzStatusPill("DESTACADA", accent)
                        if (saga.localOwnedCount > 0) {
                            OzStatusPill("${saga.localOwnedCount}/${saga.totalCount}", OzColors.Positive)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        saga.name,
                        style = OzTypography.hero,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        saga.tagline,
                        style = OzTypography.meta,
                        color = OzColors.TextPrimary.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = accent,
                        trackColor = OzColors.PanelRaised.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(OzShapes.pill),
                    )
                }
            }
        }
    }
}

@Composable
fun SagaBannerCard(
    saga: SagaSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = remember(saga.accentHex) { ConsolePalette.parseHexColor(saga.accentHex, OzColors.HylianEmerald) }
    val progress = remember(saga.localOwnedCount, saga.totalCount) {
        if (saga.totalCount <= 0) 0f else (saga.localOwnedCount.toFloat() / saga.totalCount.toFloat()).coerceIn(0f, 1f)
    }
    val bannerCands = remember(saga.id, saga.heroBannerUrl, saga.heroBannerCandidates, saga.keyArtUrl, saga.keyArtCandidates) {
        (saga.heroBannerCandidates + saga.keyArtCandidates + listOf(saga.heroBannerUrl, saga.keyArtUrl))
            .filter { it.isNotBlank() }
            .distinct()
    }
    val primaryConsole = saga.consoles.firstOrNull() ?: "snes"
    var focused by remember(saga.id) { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "saga-card-focus",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(cl.gakuto.kizuna.ui.compose.components.OZ_IMMERSIVE_COVER_ASPECT_RATIO)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(OzShapes.large)
            .clickable(onClick = onClick),
        shape = OzShapes.large,
        color = OzColors.Panel,
        border = BorderStroke(if (focused) 2.5.dp else 1.dp, if (focused) OzColors.HylianGold else OzColors.HylianBorder),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            OzCoverImage(
                candidates = bannerCands,
                title = saga.name,
                systemId = primaryConsole,
                contentScale = ContentScale.Crop,
                adaptiveBlur = false,
                showTitleOnFallback = true,
                modifier = Modifier.fillMaxSize(),
            )

            // Readable dark Hylian gradient over official libretroArt background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to OzColors.HylianBg.copy(alpha = 0.14f),
                            0.46f to OzColors.HylianBg.copy(alpha = 0.45f),
                            1f to OzColors.HylianBg.copy(alpha = 0.95f),
                        ),
                    ),
            )

            // Owned progress / year badge in top-right corner
            val badgeText = if (saga.localOwnedCount > 0) "${saga.localOwnedCount}/${saga.totalCount}" else "${saga.foundedYear}"
            val badgeColor = if (saga.localOwnedCount > 0) OzColors.Positive else accent
            Text(
                text = badgeText,
                style = OzTypography.meta.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = badgeColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .clip(OzShapes.pill)
                    .background(OzColors.DeepSpace.copy(alpha = 0.86f))
                    .border(1.dp, badgeColor.copy(alpha = 0.6f), OzShapes.pill)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
            ) {
                Text(
                    saga.name,
                    style = OzTypography.cardTitle,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${saga.foundedYear} · ${saga.totalCount} juegos",
                    style = OzTypography.meta.copy(fontSize = 10.sp),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = accent,
                    trackColor = OzColors.PanelRaised.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(OzShapes.pill),
                )
            }
        }
    }
}

@Composable
fun SagaDetailScreen(
    viewModel: OzViewModel,
    sagaId: String,
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit,
    onPlayGame: (GameEntity) -> Unit,
    onOpenCatalogSearch: (String) -> Unit,
) {
    val sagas by viewModel.sagas.collectAsState()
    val auditReport by viewModel.sagaAuditReport.collectAsState()
    val isAuditing by viewModel.isAuditingSaga.collectAsState()
    val saga = remember(sagas, sagaId) { sagas.firstOrNull { it.id == sagaId } }
    var onlyOwned by remember(sagaId) { mutableStateOf(false) }
    var ftpTargetGame by remember { mutableStateOf<GameEntity?>(null) }

    androidx.compose.runtime.LaunchedEffect(sagaId) {
        viewModel.clearSagaAudit()
    }

    if (ftpTargetGame != null) {
        FtpSendDialog(
            game = ftpTargetGame!!,
            viewModel = viewModel,
            usePatched = viewModel.hasPatchedRom(ftpTargetGame!!.id),
            onDismiss = { ftpTargetGame = null },
        )
    }

    if (saga == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Cargando saga…", style = OzTypography.section, color = OzColors.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onBack) { Text("Volver") }
            }
        }
        return
    }

    val accent = remember(saga.accentHex) { ConsolePalette.parseHexColor(saga.accentHex, OzColors.Cyan) }
    val visibleEntries = remember(saga, onlyOwned) {
        if (onlyOwned) saga.entries.filter { it.localOwned || it.inCatalog } else saga.entries
    }
    val eraGroups = remember(visibleEntries) {
        visibleEntries.groupBy { it.era }.entries.toList()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(OzColors.DeepSpace)) {
        val isWide = maxWidth >= 760.dp || maxWidth > maxHeight
        val eraColumns = if (maxWidth >= 1040.dp) 4 else 3
        val activeAudit = auditReport?.takeIf { it.sagaId == saga.id }
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        SagaHeroHeader(
                            saga = saga,
                            accent = accent,
                            onlyOwned = onlyOwned,
                            onToggleOwned = { onlyOwned = it },
                            onBack = onBack,
                            onPublishIisu = { viewModel.publishSagaToIisu(saga) },
                            isAuditing = isAuditing,
                            onAuditAi = { viewModel.auditSagaWithAi(saga) },
                        )
                    }
                    if (activeAudit != null) {
                        item {
                            SagaAiAuditCard(
                                report = activeAudit,
                                accent = accent,
                                onDismiss = { viewModel.clearSagaAudit() },
                            )
                        }
                    }
                    item {
                        SagaHistoryBox(saga = saga, accent = accent)
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    eraGroups.forEach { (era, groupEntries) ->
                        item(key = "header-$era") {
                            SagaEraHeader(era = era, accent = accent)
                        }
                        val rows = groupEntries.chunked(eraColumns)
                        items(rows.size, key = { idx -> "row-$era-$idx" }) { idx ->
                            SagaEraGridRow(
                                entries = rows[idx],
                                columns = eraColumns,
                                accent = accent,
                                onOpenGame = onOpenGame,
                                onSearchCatalog = onOpenCatalogSearch,
                            )
                        }
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
                    SagaHeroHeader(
                        saga = saga,
                        accent = accent,
                        onlyOwned = onlyOwned,
                        onToggleOwned = { onlyOwned = it },
                        onBack = onBack,
                        onPublishIisu = { viewModel.publishSagaToIisu(saga) },
                        isAuditing = isAuditing,
                        onAuditAi = { viewModel.auditSagaWithAi(saga) },
                    )
                }
                if (activeAudit != null) {
                    item {
                        SagaAiAuditCard(
                            report = activeAudit,
                            accent = accent,
                            onDismiss = { viewModel.clearSagaAudit() },
                        )
                    }
                }
                item {
                    SagaHistoryBox(saga = saga, accent = accent)
                }
                eraGroups.forEach { (era, groupEntries) ->
                    item(key = "header-$era") {
                        SagaEraHeader(era = era, accent = accent)
                    }
                    val rows = groupEntries.chunked(3)
                    items(rows.size, key = { idx -> "row-$era-$idx" }) { idx ->
                        SagaEraGridRow(
                            entries = rows[idx],
                            columns = 3,
                            accent = accent,
                            onOpenGame = onOpenGame,
                            onSearchCatalog = onOpenCatalogSearch,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SagaHeroHeader(
    saga: SagaSnapshot,
    accent: Color,
    onlyOwned: Boolean,
    onToggleOwned: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPublishIisu: () -> Unit,
    isAuditing: Boolean,
    onAuditAi: () -> Unit,
) {
    val progress = if (saga.totalCount <= 0) 0f else (saga.localOwnedCount.toFloat() / saga.totalCount.toFloat()).coerceIn(0f, 1f)
    val bannerCands = remember(saga.id, saga.heroBannerUrl, saga.heroBannerCandidates) {
        saga.heroBannerCandidates.ifEmpty { listOf(saga.heroBannerUrl, saga.keyArtUrl) }
    }
    val keyArtCands = remember(saga.id, saga.keyArtUrl, saga.keyArtCandidates) {
        saga.keyArtCandidates.ifEmpty { listOf(saga.keyArtUrl, saga.heroBannerUrl) }
    }
    val primaryConsole = saga.consoles.firstOrNull() ?: "snes"

    Surface(
        shape = OzShapes.hero,
        color = OzColors.Panel,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                OzCoverImage(
                    candidates = bannerCands,
                    title = saga.name,
                    systemId = primaryConsole,
                    contentScale = ContentScale.Crop,
                    showTitleOnFallback = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                OzColors.DeepSpace.copy(alpha = 0.55f),
                                OzColors.Panel.copy(alpha = 0.92f),
                                OzColors.Panel,
                            ),
                        ),
                    ),
            )

            Column(modifier = Modifier.padding(16.dp)) {
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
                            .background(OzColors.DeepSpace.copy(alpha = 0.72f))
                            .border(1.dp, OzColors.Cyan.copy(alpha = 0.45f), OzShapes.pill)
                            .clickable(onClick = onBack)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onAuditAi,
                            enabled = !isAuditing,
                            shape = OzShapes.pill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OzColors.PanelRaised.copy(alpha = 0.92f),
                                contentColor = OzColors.Cyan,
                            ),
                            border = BorderStroke(1.dp, OzColors.Cyan.copy(alpha = 0.55f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(if (isAuditing) "Analizando…" else "✨ Revisar con IA", style = OzTypography.meta)
                        }
                        Button(
                            onClick = onPublishIisu,
                            shape = OzShapes.pill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OzColors.PanelRaised.copy(alpha = 0.92f),
                                contentColor = OzColors.Gold,
                            ),
                            border = BorderStroke(1.dp, OzColors.Gold.copy(alpha = 0.55f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("📂 Publicar en iiSU", style = OzTypography.meta)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(92.dp)
                            .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO)
                            .clip(OzShapes.medium)
                            .background(OzColors.PanelRaised)
                            .border(1.5.dp, accent.copy(alpha = 0.75f), OzShapes.medium),
                    ) {
                        OzCoverImage(
                            candidates = keyArtCands,
                            title = saga.name,
                            systemId = primaryConsole,
                            contentScale = ContentScale.Crop,
                            showTitleOnFallback = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SAGA CRONOLÓGICA", style = OzTypography.meta.copy(fontSize = 10.sp), color = accent)
                        Text(saga.name, style = OzTypography.hero, color = OzColors.TextPrimary)
                        Text(
                            "Desde ${saga.foundedYear} · ${saga.creator}",
                            style = OzTypography.meta,
                            color = OzColors.TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(saga.tagline, style = OzTypography.body, color = OzColors.TextPrimary.copy(alpha = 0.9f))
                    }
                }

                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    saga.consoles.forEach { sys -> ConsoleBadge(systemId = sys) }
                }

                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = accent,
                    trackColor = OzColors.PanelRaised,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(OzShapes.pill),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${saga.localOwnedCount} de ${saga.totalCount} entregas en tu bóveda · ${saga.catalogCount} en catálogo",
                    style = OzTypography.meta,
                    color = OzColors.TextSecondary,
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !onlyOwned,
                        onClick = { onToggleOwned(false) },
                        label = { Text("Cronología (${saga.totalCount})", style = OzTypography.meta) },
                        shape = OzShapes.pill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.22f),
                            selectedLabelColor = accent,
                            containerColor = OzColors.PanelRaised,
                            labelColor = OzColors.TextSecondary,
                        ),
                    )
                    FilterChip(
                        selected = onlyOwned,
                        onClick = { onToggleOwned(true) },
                        label = { Text("Disponibles (${saga.catalogCount + saga.localOwnedCount})", style = OzTypography.meta) },
                        shape = OzShapes.pill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OzColors.Positive.copy(alpha = 0.22f),
                            selectedLabelColor = OzColors.Positive,
                            containerColor = OzColors.PanelRaised,
                            labelColor = OzColors.TextSecondary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SagaHistoryBox(saga: SagaSnapshot, accent: Color) {
    Surface(
        shape = OzShapes.large,
        color = OzColors.PanelRaised.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Sobre la saga", style = OzTypography.cardTitle, color = accent)
            Spacer(Modifier.height(6.dp))
            Text(saga.history, style = OzTypography.body, color = OzColors.TextPrimary.copy(alpha = 0.92f))
        }
    }
}

@Composable
private fun SagaEraHeader(era: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(OzShapes.pill)
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            era.uppercase(),
            style = OzTypography.meta.copy(fontWeight = FontWeight.Bold),
            color = accent,
        )
    }
}

@Composable
private fun SagaEraGridRow(
    entries: List<SagaEntryItem>,
    columns: Int,
    accent: Color,
    onOpenGame: (String) -> Unit,
    onSearchCatalog: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        entries.forEach { entry ->
            SagaEraCoverCard(
                entry = entry,
                accent = accent,
                modifier = Modifier.weight(1f),
                onClick = {
                    val game = entry.game
                    if (game != null) onOpenGame(game.id) else onSearchCatalog(entry.minervaQuery)
                },
            )
        }
        repeat((columns - entries.size).coerceAtLeast(0)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SagaEraCoverCard(
    entry: SagaEntryItem,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val coverCands = remember(entry.title, entry.coverUrl, entry.coverCandidates) {
        entry.coverCandidates.ifEmpty { listOfNotNull(entry.coverUrl) }
    }
    val borderColor = when {
        entry.localOwned -> OzColors.Positive.copy(alpha = 0.65f)
        entry.inCatalog -> accent.copy(alpha = 0.42f)
        else -> OzColors.Outline.copy(alpha = 0.28f)
    }
    val statusDotColor = when {
        entry.localOwned -> OzColors.Positive
        entry.inCatalog -> OzColors.Cyan
        else -> OzColors.TextSecondary.copy(alpha = 0.5f)
    }
    val statusLabel = when {
        entry.localOwned -> "Listo"
        entry.inCatalog -> "Catálogo"
        else -> "Buscar"
    }

    Surface(
        shape = OzShapes.medium,
        color = OzColors.PanelRaised.copy(alpha = if (entry.game != null) 0.88f else 0.48f),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clip(OzShapes.medium)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO)
                    .clip(OzShapes.small)
                    .background(OzColors.DeepSpace.copy(alpha = 0.65f)),
            ) {
                OzCoverImage(
                    candidates = coverCands,
                    title = entry.title,
                    systemId = entry.consoleKey,
                    contentScale = ContentScale.Crop,
                    showTitleOnFallback = true,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusDotColor),
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                entry.title,
                style = OzTypography.cardTitle.copy(fontSize = 12.sp, lineHeight = 15.sp),
                color = OzColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                statusLabel,
                style = OzTypography.meta.copy(fontSize = 10.sp),
                color = statusDotColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SagaAiAuditCard(
    report: cl.gakuto.kizuna.sagas.SagaAiAuditReport,
    accent: Color,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = OzShapes.large,
        color = OzColors.PanelRaised,
        border = BorderStroke(1.dp, OzColors.Cyan.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "✨ ${report.sourceLabel}",
                        style = OzTypography.meta.copy(fontWeight = FontWeight.Bold),
                        color = OzColors.Cyan,
                    )
                    Text(
                        "Salud ${report.score}%",
                        style = OzTypography.meta.copy(fontWeight = FontWeight.Bold),
                        color = if (report.score >= 85) OzColors.Positive else accent,
                        modifier = Modifier
                            .clip(OzShapes.pill)
                            .background(OzColors.DeepSpace.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    "Cerrar",
                    style = OzTypography.meta,
                    color = OzColors.TextSecondary,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(report.summary, style = OzTypography.body, color = OzColors.TextPrimary)
            if (report.issues.isNotEmpty()) {
                report.issues.forEach { issue ->
                    Text("• $issue", style = OzTypography.meta, color = OzColors.Gold)
                }
            }
            if (report.eraSuggestions.isNotEmpty()) {
                report.eraSuggestions.forEach { sug ->
                    Text("→ $sug", style = OzTypography.meta, color = OzColors.TextSecondary)
                }
            }
            if (report.missingHighlights.isNotEmpty()) {
                Text(
                    "Pendientes en bóveda: ${report.missingHighlights.joinToString(" · ")}",
                    style = OzTypography.meta,
                    color = OzColors.Cyan,
                )
            }
        }
    }
}

@Composable
private fun SagaTimelineRow(
    entry: SagaEntryItem,
    accent: Color,
    onOpenGame: (String) -> Unit,
    onPlayGame: (GameEntity) -> Unit,
    onSendFtp: (GameEntity) -> Unit,
    onSearchCatalog: (String) -> Unit,
) {
    val game = entry.game
    val statusColor = when {
        entry.localOwned -> OzColors.Positive
        entry.inCatalog -> OzColors.Cyan
        else -> OzColors.TextSecondary
    }
    val statusText = when {
        entry.localOwned -> "En tu bóveda"
        entry.inCatalog -> "Catálogo (descarga auto)"
        else -> "Buscar en MiNERVA"
    }
    val coverCands = remember(entry.title, entry.coverUrl, entry.coverCandidates) {
        entry.coverCandidates.ifEmpty { listOfNotNull(entry.coverUrl) }
    }

    Surface(
        shape = OzShapes.large,
        color = OzColors.PanelRaised.copy(alpha = if (game != null) 0.84f else 0.45f),
        border = BorderStroke(
            1.dp,
            if (entry.localOwned) OzColors.Positive.copy(alpha = 0.48f) else OzColors.Outline.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(OzShapes.large)
            .clickable {
                if (game != null) onOpenGame(game.id) else onSearchCatalog(entry.minervaQuery)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.width(46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .clip(OzShapes.small)
                        .background(accent.copy(alpha = if (entry.isExtra) 0.14f else 0.25f))
                        .border(1.dp, accent.copy(alpha = 0.6f), OzShapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (entry.isExtra) "+" else "#${entry.order}",
                        style = OzTypography.meta,
                        color = accent,
                    )
                }
                if (entry.year != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("${entry.year}", style = OzTypography.meta.copy(fontSize = 11.sp), color = OzColors.TextSecondary)
                }
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .width(62.dp)
                    .aspectRatio(OZ_NDS_COVER_ASPECT_RATIO)
                    .clip(OzShapes.small)
                    .border(1.dp, OzColors.Outline.copy(alpha = 0.35f), OzShapes.small),
            ) {
                OzCoverImage(
                    candidates = coverCands,
                    title = entry.title,
                    systemId = entry.consoleKey,
                    contentScale = ContentScale.Crop,
                    showTitleOnFallback = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = OzTypography.tileTitle,
                    color = OzColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ConsoleBadge(systemId = entry.consoleKey)
                    Text(statusText, style = OzTypography.meta.copy(fontSize = 11.sp), color = statusColor)
                    if (entry.variantCount > 1) {
                        Text("· ${entry.variantCount} ver.", style = OzTypography.meta.copy(fontSize = 10.sp), color = OzColors.Gold)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            if (game != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.localOwned) {
                        Text(
                            "📡",
                            style = OzTypography.cardTitle,
                            modifier = Modifier
                                .clip(OzShapes.small)
                                .background(OzColors.Panel)
                                .border(1.dp, OzColors.Gold.copy(alpha = 0.45f), OzShapes.small)
                                .clickable { onSendFtp(game) }
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                        )
                    }
                    Button(
                        onClick = { onPlayGame(game) },
                        shape = OzShapes.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (entry.localOwned) OzColors.Positive else OzColors.Cyan,
                            contentColor = OzColors.DeepSpace,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("▶ Jugar", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold))
                    }
                }
            } else {
                Button(
                    onClick = { onSearchCatalog(entry.minervaQuery) },
                    shape = OzShapes.pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OzColors.Panel,
                        contentColor = OzColors.Cyan,
                    ),
                    border = BorderStroke(1.dp, OzColors.Cyan.copy(alpha = 0.45f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("+ Buscar", style = OzTypography.meta)
                }
            }
        }
    }
}

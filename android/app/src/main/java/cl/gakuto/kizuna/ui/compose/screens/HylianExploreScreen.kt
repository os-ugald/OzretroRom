package cl.gakuto.kizuna.ui.compose.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.gakuto.kizuna.catalog.CatalogSystem
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.components.ConsoleBadge
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography

private enum class ExploreSegment(val label: String) {
    SAGAS("Sagas"),
    CONSOLES("Consolas"),
}

/**
 * Console destination 2 ("Explorar"):
 * - Clean segmented toggle: Sagas | Consolas (touch >= 48dp & D-Pad L2/R2)
 * - Sagas renders official libretroArt backgrounds from sagas_catalog.json
 * - Consolas launches clean platform collection or MiNERVA catalog drill-down
 */
@Composable
fun HylianExploreScreen(
    viewModel: OzViewModel,
    onOpenSaga: (String) -> Unit,
    onOpenSystemCollection: (String) -> Unit,
    onOpenFullCatalog: () -> Unit,
    onOpenFullLibrary: () -> Unit,
) {
    var segment by remember { mutableStateOf(ExploreSegment.SAGAS) }
    val summaries by viewModel.collectionSummaries.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OzColors.HylianBg)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && (ev.key == Key.ButtonL2 || ev.key == Key.ButtonR2)) {
                    segment = if (segment == ExploreSegment.SAGAS) ExploreSegment.CONSOLES else ExploreSegment.SAGAS
                    true
                } else false
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(OzShapes.pill)
                    .background(OzColors.HylianSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExploreSegment.entries.forEach { tab ->
                    val selected = segment == tab
                    var focused by remember(tab) { mutableStateOf(false) }
                    Surface(
                        shape = OzShapes.pill,
                        color = if (selected) OzColors.HylianEmerald else OzColors.HylianSurface,
                        border = BorderStroke(
                            width = if (focused) 2.dp else 0.dp,
                            color = if (focused) OzColors.HylianGold else OzColors.HylianSurface,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .clickable { segment = tab },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = tab.label,
                                style = OzTypography.cardTitle,
                                color = if (selected) OzColors.TextPrimary else OzColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            Surface(
                shape = OzShapes.pill,
                color = OzColors.HylianSurfaceRaised,
                border = BorderStroke(1.dp, OzColors.HylianBorder),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .focusable()
                    .clickable(onClick = onOpenFullCatalog),
            ) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("MiNERVA", style = OzTypography.meta.copy(fontWeight = FontWeight.Bold), color = OzColors.HylianGold)
                }
            }
        }

        when (segment) {
            ExploreSegment.SAGAS -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SagasGalleryScreen(
                        viewModel = viewModel,
                        onSagaClick = onOpenSaga,
                        onBackToCollections = {},
                    )
                }
            }
            ExploreSegment.CONSOLES -> {
                val systemCounts = remember(summaries) {
                    summaries.associate { it.collection.id to it.localCount }
                }
                ConsolesExploreGrid(
                    systems = viewModel.systems,
                    systemLocalCounts = systemCounts,
                    onSystemClick = { sys -> onOpenSystemCollection("system-${sys.id}") },
                    onOpenFullLibrary = onOpenFullLibrary,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ConsolesExploreGrid(
    systems: List<CatalogSystem>,
    systemLocalCounts: Map<String, Int>,
    onSystemClick: (CatalogSystem) -> Unit,
    onOpenFullLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val cols = if (maxWidth >= 720.dp) 3 else 2
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ConsoleExploreCard(
                    title = "Biblioteca completa",
                    subtitle = "Todas las ROMs locales",
                    badge = "LOCAL",
                    onClick = onOpenFullLibrary,
                )
            }
            items(systems, key = { it.id }) { sys ->
                val count = systemLocalCounts["system-${sys.id}"] ?: 0
                val visual = ConsolePalette.forSystem(sys.id)
                ConsoleExploreCard(
                    title = sys.name,
                    subtitle = if (count > 0) "$count locales" else sys.platform,
                    badge = visual.shortLabel,
                    systemId = sys.id,
                    onClick = { onSystemClick(sys) },
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun ConsoleExploreCard(
    title: String,
    subtitle: String,
    badge: String,
    systemId: String? = null,
    onClick: () -> Unit,
) {
    var focused by remember(title) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "console-tile-focus")

    Surface(
        shape = OzShapes.large,
        color = OzColors.HylianSurface,
        border = BorderStroke(
            width = if (focused) 2.5.dp else 1.dp,
            color = if (focused) OzColors.HylianGold else OzColors.HylianBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (systemId != null) {
                ConsoleBadge(systemId = systemId)
            } else {
                Text(badge, style = OzTypography.meta, color = OzColors.HylianGold)
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = OzTypography.cardTitle, color = OzColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = OzTypography.meta, color = OzColors.TextSecondary, maxLines = 1)
        }
    }
}

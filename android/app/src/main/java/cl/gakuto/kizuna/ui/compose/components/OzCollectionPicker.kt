package cl.gakuto.kizuna.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.gakuto.kizuna.data.CollectionKind
import cl.gakuto.kizuna.data.GameEntity
import cl.gakuto.kizuna.ui.compose.OzViewModel
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzTypography

@Composable
fun OzCollectionPicker(
    game: GameEntity,
    viewModel: OzViewModel,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val summaries by viewModel.collectionSummaries.collectAsStateWithLifecycle()
    val manualCollections = remember(summaries) { 
        summaries.filter { it.collection.collectionKind == CollectionKind.MANUAL } 
    }

    AnimatedVisibility(visible = visible) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(OzColors.PanelRaised)
                    .border(1.dp, OzColors.Outline.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
                    .clickable(enabled = false) { }
            ) {
                Text(
                    "AÑADIR A COLECCIÓN",
                    style = OzTypography.section,
                    color = OzColors.TextPrimary
                )
                Text(
                    game.title.uppercase(),
                    style = OzTypography.meta,
                    color = OzColors.Cyan
                )

                Spacer(Modifier.height(16.dp))

                if (manualCollections.isEmpty()) {
                    Text(
                        "No tienes colecciones personales. Créalas en la pantalla de Colecciones.",
                        style = OzTypography.body,
                        color = OzColors.TextSecondary
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(manualCollections) { summary ->
                            CollectionSelectItem(summary.collection.name) {
                                viewModel.toggleCollection(summary.collection.id, game.id)
                                onDismiss()
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "CERRAR",
                        style = OzTypography.meta,
                        color = OzColors.Cyan,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionSelectItem(name: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) OzColors.Cyan.copy(alpha = 0.1f) else Color.Transparent)
            .border(
                1.dp,
                if (focused) OzColors.Cyan else OzColors.Outline.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(14.dp)
    ) {
        Text(name, style = OzTypography.body, color = OzColors.TextPrimary)
    }
}

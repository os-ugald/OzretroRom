package cl.gakuto.kizuna.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RetroStepStatus { WAITING, PROCESSING, COMPLETE, ERROR }

data class RetroPreparationStep(
    val id: String,
    val label: String,
    val status: RetroStepStatus = RetroStepStatus.WAITING,
    val detail: String = "",
)

data class RetroPreparationUiState(
    val visible: Boolean = false,
    val steps: List<RetroPreparationStep> = defaultRetroSteps(),
)

class RetroPreparationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RetroPreparationUiState())
    val uiState: StateFlow<RetroPreparationUiState> = _uiState.asStateFlow()

    fun begin() {
        _uiState.value = RetroPreparationUiState(
            visible = true,
            steps = defaultRetroSteps().mapIndexed { index, step ->
                if (index == 0) step.copy(status = RetroStepStatus.PROCESSING) else step
            },
        )
    }

    fun mark(id: String, status: RetroStepStatus, detail: String = "") {
        _uiState.update { current ->
            current.copy(
                visible = true,
                steps = current.steps.map { step -> if (step.id == id) step.copy(status = status, detail = detail) else step },
            )
        }
    }

    fun processing(id: String, detail: String = "") = mark(id, RetroStepStatus.PROCESSING, detail)
    fun complete(id: String, detail: String = "") = mark(id, RetroStepStatus.COMPLETE, detail)
    fun error(id: String, detail: String) = mark(id, RetroStepStatus.ERROR, detail)
}

private fun defaultRetroSteps() = listOf(
    RetroPreparationStep("file", "Archivo"),
    RetroPreparationStep("core", "Core"),
    RetroPreparationStep("compatibility", "Compatibilidad"),
    RetroPreparationStep("bios", "BIOS"),
    RetroPreparationStep("open", "RetroArch"),
)

@Composable
fun RetroPreparationPanel(state: RetroPreparationUiState, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = state.visible) {
        val complete = state.steps.count { it.status == RetroStepStatus.COMPLETE }
        val current = state.steps.firstOrNull { it.status == RetroStepStatus.PROCESSING }
            ?: state.steps.firstOrNull { it.status == RetroStepStatus.ERROR }
            ?: state.steps.lastOrNull()
        val hasError = state.steps.any { it.status == RetroStepStatus.ERROR }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            OzColors.PurpleDeep.copy(alpha = .78f),
                            OzColors.Panel,
                            OzColors.ElectricBlue.copy(alpha = .18f),
                        ),
                    ),
                    OzShapes.large,
                )
                .border(1.dp, if (hasError) OzColors.Error.copy(alpha = .7f) else OzColors.Cyan.copy(alpha = .34f), OzShapes.large)
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("OZ // BOOT SEQUENCE", style = OzTypography.meta, color = OzColors.Cyan)
                    Text(
                        if (hasError) "Revisión detenida" else if (complete == state.steps.size) "Sistema listo" else "Preparando partida",
                        style = OzTypography.section,
                        color = OzColors.WarmIvory,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text("$complete/${state.steps.size}", style = OzTypography.meta, color = if (hasError) OzColors.Error else OzColors.Positive)
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                state.steps.forEach { step ->
                    val color = when (step.status) {
                        RetroStepStatus.COMPLETE -> OzColors.Positive
                        RetroStepStatus.PROCESSING -> OzColors.Cyan
                        RetroStepStatus.ERROR -> OzColors.Error
                        RetroStepStatus.WAITING -> OzColors.Outline.copy(alpha = .75f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .background(color, RoundedCornerShape(999.dp)),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            current?.let { step ->
                Text(
                    text = when (step.status) {
                        RetroStepStatus.COMPLETE -> "LISTO · ${step.label.uppercase()}"
                        RetroStepStatus.ERROR -> "ATENCIÓN · ${step.label.uppercase()}"
                        else -> "COMPROBANDO · ${step.label.uppercase()}"
                    },
                    style = OzTypography.meta,
                    color = when (step.status) {
                        RetroStepStatus.ERROR -> OzColors.Error
                        RetroStepStatus.COMPLETE -> OzColors.Positive
                        else -> OzColors.Cyan
                    },
                )
                if (step.detail.isNotBlank()) {
                    Text(
                        step.detail,
                        style = OzTypography.body,
                        color = OzColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                state.steps.forEach { step ->
                    MiniStep(step, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniStep(step: RetroPreparationStep, modifier: Modifier = Modifier) {
    val accent = when (step.status) {
        RetroStepStatus.COMPLETE -> OzColors.Positive
        RetroStepStatus.PROCESSING -> OzColors.Cyan
        RetroStepStatus.ERROR -> OzColors.Error
        RetroStepStatus.WAITING -> OzColors.TextSecondary.copy(alpha = .45f)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(24.dp)
                .background(accent.copy(alpha = .12f), RoundedCornerShape(999.dp))
                .border(1.dp, accent.copy(alpha = .65f), RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (step.status) {
                    RetroStepStatus.COMPLETE -> "✓"
                    RetroStepStatus.PROCESSING -> "•"
                    RetroStepStatus.ERROR -> "×"
                    RetroStepStatus.WAITING -> "·"
                },
                color = accent,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            step.label,
            style = OzTypography.meta,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

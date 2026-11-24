package cl.gakuto.kizuna.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.gakuto.kizuna.ui.compose.theme.ConsolePalette
import cl.gakuto.kizuna.ui.compose.theme.OzShapes
import cl.gakuto.kizuna.ui.compose.theme.OzTypography

@Composable
fun ConsoleBadge(
    systemId: String?,
    modifier: Modifier = Modifier,
) {
    val visual = remember(systemId) { ConsolePalette.forSystem(systemId) }
    Text(
        text = visual.shortLabel,
        style = OzTypography.meta.copy(fontSize = 10.sp),
        color = visual.accent,
        modifier = modifier
            .clip(OzShapes.pill)
            .background(visual.badgeBg)
            .border(1.dp, visual.accent.copy(alpha = 0.55f), OzShapes.pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

package cl.gakuto.kizuna.ui.compose.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Iconic retro RPG treasure chest emblem with warm radiant aura and glowing emerald gem.
 */
@Composable
fun GameVaultChestLogo(
    modifier: Modifier = Modifier,
    openHint: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "gv-chest-aura")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gv-chest-pulse",
    )

    val gold = Color(0xFFFFB000)
    val goldBright = Color(0xFFFFE599)
    val gemGreen = Color(0xFF39FF88)
    val lidBrown = Color(0xFF8B4513)
    val baseBrown = Color(0xFF5C2E14)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF25274D), Color(0xFF15162C)),
                ),
            )
            .border(1.2.dp, gold.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Golden inner glow behind chest lid
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF3B0).copy(alpha = 0.45f * pulse),
                        gold.copy(alpha = 0.25f * pulse),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.5f, h * 0.42f),
                    radius = w * 0.48f,
                ),
                center = Offset(w * 0.5f, h * 0.42f),
                radius = w * 0.48f,
            )

            // Floating emerald rupee/gem peeking from chest
            val gemOffset = if (openHint) -h * 0.08f else -h * 0.03f * pulse
            val gemPath = Path().apply {
                moveTo(w * 0.50f, h * 0.16f + gemOffset)
                lineTo(w * 0.60f, h * 0.30f + gemOffset)
                lineTo(w * 0.50f, h * 0.44f + gemOffset)
                lineTo(w * 0.40f, h * 0.30f + gemOffset)
                close()
            }
            drawPath(gemPath, gemGreen)
            drawPath(gemPath, Color(0xFFF8F6E8), style = Stroke(width = w * 0.03f))

            // Hinged domed lid
            val lidLift = if (openHint) -12f else -4f * (pulse - 0.5f)
            rotate(degrees = lidLift, pivot = Offset(w * 0.18f, h * 0.44f)) {
                val lidPath = Path().apply {
                    moveTo(w * 0.14f, h * 0.44f)
                    quadraticTo(w * 0.50f, h * 0.14f, w * 0.86f, h * 0.44f)
                    lineTo(w * 0.82f, h * 0.52f)
                    lineTo(w * 0.18f, h * 0.52f)
                    close()
                }
                drawPath(lidPath, lidBrown)
                drawPath(
                    lidPath,
                    gold,
                    style = Stroke(width = w * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // Wooden chest base
            drawRoundRect(
                color = baseBrown,
                topLeft = Offset(w * 0.17f, h * 0.50f),
                size = Size(w * 0.66f, h * 0.38f),
                cornerRadius = CornerRadius(w * 0.09f, w * 0.09f),
            )
            drawRoundRect(
                color = gold,
                topLeft = Offset(w * 0.17f, h * 0.50f),
                size = Size(w * 0.66f, h * 0.38f),
                cornerRadius = CornerRadius(w * 0.09f, w * 0.09f),
                style = Stroke(width = w * 0.052f),
            )

            // Vertical gold straps
            drawRect(gold, topLeft = Offset(w * 0.28f, h * 0.50f), size = Size(w * 0.08f, h * 0.38f))
            drawRect(gold, topLeft = Offset(w * 0.64f, h * 0.50f), size = Size(w * 0.08f, h * 0.38f))

            // Golden lock clasp
            drawRoundRect(
                color = goldBright,
                topLeft = Offset(w * 0.41f, h * 0.53f),
                size = Size(w * 0.18f, h * 0.17f),
                cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
            )
            drawCircle(
                color = Color(0xFF0B0C17),
                radius = w * 0.035f,
                center = Offset(w * 0.50f, h * 0.615f),
            )
        }
    }
}

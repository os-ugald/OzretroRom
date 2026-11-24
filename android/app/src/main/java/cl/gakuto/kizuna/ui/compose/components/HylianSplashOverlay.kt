package cl.gakuto.kizuna.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import coil3.compose.AsyncImage
import coil3.gif.onAnimationEnd
import coil3.gif.repeatCount
import coil3.request.ImageRequest
import kotlinx.coroutines.delay

const val HYLIAN_SPLASH_DURATION_MS = 3200L
const val HYLIAN_SPLASH_FADE_OUT_MS = 420

@Composable
fun HylianSplashOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        runCatching { focusRequester.requestFocus() }
        delay(HYLIAN_SPLASH_DURATION_MS)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = HYLIAN_SPLASH_FADE_OUT_MS)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OzColors.HylianBg)
                .focusRequester(focusRequester)
                .focusable()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onDismiss,
                )
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.ButtonA,
                        Key.ButtonStart,
                        Key.Enter,
                        Key.DirectionCenter,
                        Key.Spacebar,
                        Key.ButtonB,
                        Key.Back,
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            AsyncImage(
                model = remember(context) {
                    ImageRequest.Builder(context)
                        .data(R.raw.splash_hylian)
                        .repeatCount(0)
                        .onAnimationEnd { onDismiss() }
                        .build()
                },
                contentDescription = "Oz Game Vault",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.88f),
            )
            Text(
                text = "Toca o pulsa A / Start para saltar",
                style = OzTypography.meta.copy(fontWeight = FontWeight.Medium),
                color = OzColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            )
        }
    }
}

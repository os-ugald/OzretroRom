package cl.gakuto.kizuna.ui.compose.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.ui.compose.theme.OzColors
import cl.gakuto.kizuna.ui.compose.theme.OzTypography
import kotlin.math.max

/** Standard HUD heights (Spec §1.3, adjusted by product decision for Fase 1). */
val OZ_HUD_CHIP_HEIGHT: Dp = 30.dp
val OZ_HUD_SEGMENTED_HEIGHT: Dp = 34.dp
val OZ_HUD_SEARCH_HEIGHT: Dp = 38.dp
val OZ_HUD_BUTTON_HEIGHT: Dp = 44.dp
val OZ_HUD_BUTTON_COMPACT_HEIGHT: Dp = 34.dp
val OZ_HUD_TOGGLE_WIDTH: Dp = 44.dp
val OZ_HUD_TOGGLE_HEIGHT: Dp = 24.dp

private const val BORDER_ALPHA_IDLE = 0.32f
private const val BORDER_ALPHA_ACTIVE = 0.95f

/**
 * 45° Chamfered HUD geometry per docs/mockups/.
 */
fun OzChamferShape(cut: Dp = 10.dp): CutCornerShape = CutCornerShape(cut)

/**
 * Centered ambient radial neon glow rendered behind a component without clipping.
 */
fun Modifier.ozNeonGlow(
    color: Color,
    radius: Dp = 14.dp,
    alpha: Float = 0.42f,
): Modifier = drawBehind {
    val glowRadius = (max(size.width, size.height) * 0.5f + radius.toPx()).coerceAtLeast(1f)
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.35f),
                Color.Transparent,
            ),
            center = center,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = center,
    )
}

/** Real cyan→violet (or emerald→gold in Hylian) gradient border brush. */
fun ozHudBorderBrush(alpha: Float = 0.4f): Brush = Brush.linearGradient(
    listOf(
        OzColors.AccentPrimary.copy(alpha = alpha),
        OzColors.AccentSecondary.copy(alpha = alpha),
    ),
)

/**
 * Single official brand emblem (replaces the legacy chest logo and text-only avatar circles).
 * Uses the raster `ic_launcher_foreground` PNG (never the adaptive XML round icon).
 */
@Composable
fun OzBrandEmblem(
    size: Dp,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    glow: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = "Oz RetroRom",
) {
    val shape: Shape = if (circular) CircleShape else OzChamferShape(if (size >= 40.dp) 8.dp else 6.dp)
    val doubleRing = circular && glow
    val outerRing = Brush.linearGradient(listOf(OzColors.AccentPrimary, OzColors.AccentSecondary))
    val innerRing = Brush.linearGradient(
        listOf(OzColors.AccentSecondary.copy(alpha = 0.85f), OzColors.AccentPrimary.copy(alpha = 0.85f)),
    )
    Box(
        modifier = modifier
            .size(size)
            .then(if (glow) Modifier.ozNeonGlow(OzColors.AccentPrimary, radius = size * 0.32f, alpha = 0.5f) else Modifier)
            .border(if (doubleRing) 2.dp else 1.dp, outerRing, shape)
            .padding(if (doubleRing) 3.dp else 1.5.dp)
            .clip(shape)
            .background(OzColors.Bg)
            .then(if (doubleRing) Modifier.border(1.dp, innerRing, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.38f),
        )
    }
}

@Composable
fun OzHudCard(
    modifier: Modifier = Modifier,
    cut: Dp = 10.dp,
    elevated: Boolean = false,
    focused: Boolean = false,
    accentColor: Color = OzColors.AccentPrimary,
    containerColor: Color = if (elevated) OzColors.SurfaceElevated else OzColors.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(cut) { OzChamferShape(cut) }
    val borderBrush = ozHudBorderBrush(if (focused) BORDER_ALPHA_ACTIVE else BORDER_ALPHA_IDLE)
    val bgBrush = Brush.verticalGradient(
        listOf(
            if (elevated) OzColors.SurfaceElevated else OzColors.SurfaceElevated.copy(alpha = 0.72f),
            containerColor,
        ),
    )
    Surface(
        modifier = modifier
            .then(if (focused) Modifier.ozNeonGlow(accentColor, radius = 12.dp, alpha = 0.24f) else Modifier),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(if (focused) 1.5.dp else 1.dp, borderBrush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgBrush),
            content = content,
        )
    }
}

@Composable
fun OzHudSurfaceBox(
    modifier: Modifier = Modifier,
    cut: Dp = 10.dp,
    focused: Boolean = false,
    accentColor: Color = OzColors.AccentPrimary,
    containerColor: Color = OzColors.Surface,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = remember(cut) { OzChamferShape(cut) }
    val borderBrush = ozHudBorderBrush(if (focused) BORDER_ALPHA_ACTIVE else BORDER_ALPHA_IDLE)
    Box(
        modifier = modifier
            .then(if (focused) Modifier.ozNeonGlow(accentColor, radius = 10.dp, alpha = 0.24f) else Modifier)
            .clip(shape)
            .background(containerColor)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = shape,
            ),
        content = content,
    )
}

/**
 * Chamfered HUD button: 44dp (34dp compact), cyan→violet gradient + neon glow when primary.
 * `leadingVector` is the preferred icon slot; `leadingIcon` (String) is kept only for
 * backward compatibility with Fase 2–4 screens until they are rewritten.
 */
@Composable
fun OzHudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    leadingIcon: String? = null,
    cut: Dp = 8.dp,
    leadingVector: ImageVector? = null,
    compact: Boolean = false,
) {
    val shape = remember(cut) { OzChamferShape(cut) }
    var focused by remember { mutableStateOf(false) }
    val bgBrush = if (primary) {
        Brush.horizontalGradient(
            listOf(
                OzColors.AccentPrimary.copy(alpha = if (enabled) 1f else 0.45f),
                OzColors.AccentSecondary.copy(alpha = if (enabled) 1f else 0.45f),
            ),
        )
    } else {
        SolidColor(OzColors.SurfaceElevated.copy(alpha = if (enabled) 0.96f else 0.5f))
    }
    val borderBrush = ozHudBorderBrush(if (focused || primary) BORDER_ALPHA_ACTIVE else BORDER_ALPHA_IDLE)
    val textColor = when {
        !enabled -> OzColors.TextSecondary
        primary && !OzColors.isLightTheme -> Color(0xFFF4F6FF)
        primary && OzColors.isLightTheme -> Color.White
        else -> OzColors.TextPrimary
    }
    val height = if (compact) OZ_HUD_BUTTON_COMPACT_HEIGHT else OZ_HUD_BUTTON_HEIGHT
    val hPad = if (compact) 12.dp else 16.dp
    val textStyle = if (compact) OzTypography.hudButton.copy(fontSize = 11.5.sp, letterSpacing = 1.1.sp) else OzTypography.hudButton

    Row(
        modifier = modifier
            .height(height)
            .then(
                when {
                    primary && enabled -> Modifier.ozNeonGlow(OzColors.AccentPrimary)
                    focused -> Modifier.ozNeonGlow(OzColors.AccentPrimary, radius = 10.dp, alpha = 0.28f)
                    else -> Modifier
                },
            )
            .clip(shape)
            .background(bgBrush, shape)
            .border(if (focused) 1.5.dp else 1.dp, borderBrush, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = hPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when {
            leadingVector != null -> {
                Icon(
                    imageVector = leadingVector,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                )
                if (text.isNotBlank()) Spacer(Modifier.width(8.dp))
            }
            !leadingIcon.isNullOrBlank() -> {
                Text(
                    text = leadingIcon,
                    style = textStyle,
                    color = textColor,
                )
                if (text.isNotBlank()) Spacer(Modifier.width(8.dp))
            }
        }
        if (text.isNotBlank()) {
            Text(
                text = text.uppercase(),
                style = textStyle,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun OzHudTag(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = OzColors.AccentPrimary,
    cut: Dp = 4.dp,
) {
    val shape = remember(cut) { OzChamferShape(cut) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.48f), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = OzTypography.hudTag.copy(fontSize = 9.5.sp),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Compact chamfered chip: fixed 30dp by default (no oversized 48dp min height). */
@Composable
fun OzHudChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    leadingGlyph: String? = null,
    accent: Color = OzColors.AccentPrimary,
    cut: Dp = 6.dp,
) {
    val shape = remember(cut) { OzChamferShape(cut) }
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> Brush.horizontalGradient(
            listOf(
                OzColors.AccentPrimary.copy(alpha = 0.24f),
                OzColors.AccentSecondary.copy(alpha = 0.22f),
            ),
        )
        else -> SolidColor(OzColors.Surface)
    }
    val borderBrush = ozHudBorderBrush(if (focused || selected) BORDER_ALPHA_ACTIVE else BORDER_ALPHA_IDLE)
    val chipHeight = if (compact) OZ_HUD_CHIP_HEIGHT else 36.dp
    val hPad = if (compact) 10.dp else 12.dp
    val textSize = 11.sp

    Row(
        modifier = modifier
            .height(chipHeight)
            .then(if (selected || focused) Modifier.ozNeonGlow(accent, radius = 10.dp, alpha = 0.28f) else Modifier)
            .clip(shape)
            .background(bg, shape)
            .border(if (focused || selected) 1.5.dp else 1.dp, borderBrush, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = hPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (!leadingGlyph.isNullOrBlank()) {
            Text(
                text = leadingGlyph,
                style = OzTypography.meta.copy(fontSize = textSize),
                color = if (selected || focused) accent else OzColors.TextSecondary,
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label.uppercase(),
            style = OzTypography.meta.copy(
                fontSize = textSize,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = if (selected || focused) OzColors.TextPrimary else OzColors.TextSecondary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Single chamfered segmented container (34dp, 3dp inner padding). The active option gets a
 * soft gradient wash + 2dp luminous bottom bar + subtle glow. `scrollable = true` avoids
 * truncating labels on narrow screens.
 */
@Composable
fun OzHudSegmentedBar(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val shape = remember { OzChamferShape(6.dp) }
    val segmentShape = remember { OzChamferShape(4.dp) }
    val rowModifier = if (scrollable) {
        Modifier.horizontalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxWidth()
    }
    Box(
        modifier = modifier
            .height(OZ_HUD_SEGMENTED_HEIGHT)
            .clip(shape)
            .background(OzColors.Surface)
            .border(1.dp, ozHudBorderBrush(BORDER_ALPHA_IDLE), shape)
            .padding(3.dp),
    ) {
        Row(
            modifier = rowModifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, label ->
                val active = index == selectedIndex
                var focused by remember { mutableStateOf(false) }
                val barBrush = Brush.horizontalGradient(listOf(OzColors.AccentPrimary, OzColors.AccentSecondary))
                Box(
                    modifier = (if (scrollable) Modifier else Modifier.weight(1f))
                        .fillMaxHeight()
                        .then(if (active) Modifier.ozNeonGlow(OzColors.AccentPrimary, radius = 6.dp, alpha = 0.22f) else Modifier)
                        .clip(segmentShape)
                        .background(
                            when {
                                active -> Brush.verticalGradient(
                                    listOf(
                                        OzColors.AccentPrimary.copy(alpha = 0.08f),
                                        OzColors.AccentSecondary.copy(alpha = 0.24f),
                                    ),
                                )
                                focused -> SolidColor(OzColors.SurfaceElevated)
                                else -> SolidColor(Color.Transparent)
                            },
                        )
                        .drawBehind {
                            if (active) {
                                val barHeight = 2.dp.toPx()
                                drawRect(
                                    brush = barBrush,
                                    topLeft = Offset(0f, size.height - barHeight),
                                    size = Size(size.width, barHeight),
                                )
                            }
                        }
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { onSelect(index) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label.uppercase(),
                        style = OzTypography.meta.copy(
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                        color = when {
                            active -> OzColors.TextPrimary
                            focused -> OzColors.AccentPrimary
                            else -> OzColors.TextSecondary
                        },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * HUD switch 100% chamfered at 45° (44x24dp) — replaces Material 3 round `Switch`.
 * Gradient-bordered track, animated chamfered knob and cyan glow when checked.
 */
@Composable
fun OzHudToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackShape = remember { OzChamferShape(4.dp) }
    val knobShape = remember { OzChamferShape(3.dp) }
    var focused by remember { mutableStateOf(false) }
    val knobSize = 18.dp
    val trackPadding = 3.dp
    val knobOffset by animateDpAsState(
        targetValue = if (checked) OZ_HUD_TOGGLE_WIDTH - knobSize - trackPadding * 2 else 0.dp,
        label = "oz-hud-toggle-knob",
    )
    val trackBrush = if (checked) {
        Brush.horizontalGradient(
            listOf(OzColors.AccentPrimary.copy(alpha = 0.30f), OzColors.AccentSecondary.copy(alpha = 0.30f)),
        )
    } else {
        SolidColor(OzColors.Surface)
    }
    val knobBrush = if (checked) {
        Brush.linearGradient(listOf(OzColors.AccentPrimary, OzColors.AccentSecondary))
    } else {
        SolidColor(OzColors.TextSecondary.copy(alpha = 0.7f))
    }
    Box(
        modifier = modifier
            .size(OZ_HUD_TOGGLE_WIDTH, OZ_HUD_TOGGLE_HEIGHT)
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (checked && enabled) Modifier.ozNeonGlow(OzColors.AccentPrimary, radius = 8.dp, alpha = 0.32f) else Modifier)
            .clip(trackShape)
            .background(trackBrush, trackShape)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                brush = ozHudBorderBrush(if (checked || focused) BORDER_ALPHA_ACTIVE else BORDER_ALPHA_IDLE),
                shape = trackShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = enabled)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(trackPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(knobSize)
                .clip(knobShape)
                .background(knobBrush, knobShape),
        )
    }
}

@Composable
fun OzHudSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(15.dp)
                    .background(OzColors.AccentPrimary),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = title.uppercase(),
                style = OzTypography.section,
                color = OzColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Text(
                text = "${actionLabel.uppercase()} ›",
                style = OzTypography.meta.copy(fontSize = 11.sp),
                color = OzColors.AccentPrimary,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/** Chamfered search field: 38dp, vector Search/Close icons (zero Unicode glyphs). */
@Composable
fun OzHudSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = OZ_HUD_SEARCH_HEIGHT,
    focusRequester: FocusRequester? = null,
    onClear: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val shape = remember { OzChamferShape(8.dp) }
    var focused by remember { mutableStateOf(false) }
    val borderBrush = ozHudBorderBrush(if (focused) BORDER_ALPHA_ACTIVE else BORDER_ALPHA_IDLE)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(OzColors.Surface, shape)
            .border(if (focused) 1.5.dp else 1.dp, borderBrush, shape)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = OzColors.AccentPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = OzTypography.body.copy(fontSize = 13.sp),
                    color = OzColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val fieldMod = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = OzTypography.body.copy(fontSize = 13.sp, color = OzColors.TextPrimary),
                cursorBrush = SolidColor(OzColors.AccentPrimary),
                modifier = fieldMod,
            )
        }
        if (value.isNotEmpty() && onClear != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(OzChamferShape(4.dp))
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Limpiar búsqueda",
                    tint = OzColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (trailingAction != null) {
            Spacer(Modifier.width(6.dp))
            trailingAction()
        }
        Spacer(Modifier.width(6.dp))
    }
}

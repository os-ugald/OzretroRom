package cl.gakuto.kizuna.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.AppThemePreset

/**
 * Dual-preset Oz RetroRom color system:
 * - Cyber Vault (default, faithful to docs/mockups/)
 * - Hylian (optional emerald + gold preset selectable in Settings)
 */
@Immutable
private data class OzPalette(
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val positive: Color,
    val warning: Color,
    val error: Color,
    val scrim: Color,
)

private val CyberVaultDarkPalette = OzPalette(
    bg = Color(0xFF070816),
    surface = Color(0xFF0E1126),
    surfaceElevated = Color(0xFF151936),
    border = Color(0xFF252A4A),
    accentPrimary = Color(0xFF22D3EE),
    accentSecondary = Color(0xFF7C3AED),
    textPrimary = Color(0xFFF4F6FF),
    textSecondary = Color(0xFF9AA3C7),
    positive = Color(0xFF22D3EE),
    warning = Color(0xFFF59E0B),
    error = Color(0xFFFB7185),
    scrim = Color(0xCC040510),
)

private val CyberVaultLightPalette = OzPalette(
    bg = Color(0xFFF3F6FC),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE8EEF9),
    border = Color(0xFFD6DDF0),
    accentPrimary = Color(0xFF2563EB),
    accentSecondary = Color(0xFF7C3AED),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    positive = Color(0xFF0891B2),
    warning = Color(0xFFD97706),
    error = Color(0xFFDC2626),
    scrim = Color(0x990F172A),
)

private val HylianDarkPalette = OzPalette(
    bg = Color(0xFF0A0F0D),
    surface = Color(0xFF141F1A),
    surfaceElevated = Color(0xFF1B2A23),
    border = Color(0xFF23382E),
    accentPrimary = Color(0xFF1E8E63),
    accentSecondary = Color(0xFFD9B64E),
    textPrimary = Color(0xFFF3F6F4),
    textSecondary = Color(0xFFA7BDB2),
    positive = Color(0xFF2EC48B),
    warning = Color(0xFFD9B64E),
    error = Color(0xFFE05A5A),
    scrim = Color(0xCC060A08),
)

private val HylianLightPalette = OzPalette(
    bg = Color(0xFFF2F7F4),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE5EFEA),
    border = Color(0xFFB8CEC3),
    accentPrimary = Color(0xFF177350),
    accentSecondary = Color(0xFFA67E16),
    textPrimary = Color(0xFF0F1915),
    textSecondary = Color(0xFF43594F),
    positive = Color(0xFF177350),
    warning = Color(0xFF9E7714),
    error = Color(0xFFB3261E),
    scrim = Color(0x990A0F0D),
)

object OzColors {
    /**
     * Snapshot-backed Compose state: every composable reading a token (Bg, AccentPrimary, ...)
     * is invalidated and recomposed instantly when the preset (Cyber Vault / Hylian)
     * or luminosity (Dark / Light) changes — no screen restart required.
     */
    private var palette by mutableStateOf(CyberVaultDarkPalette)
    private var activeIsLight by mutableStateOf(false)

    val HylianBg: Color = Color(0xFF0A0F0D)
    val HylianSurface: Color = Color(0xFF141F1A)
    val HylianSurfaceRaised: Color = Color(0xFF1B2A23)
    val HylianBorder: Color = Color(0xFF23382E)
    val HylianEmerald: Color = Color(0xFF1E8E63)
    val HylianGold: Color = Color(0xFFD9B64E)

    val CyberBgDark: Color = Color(0xFF070816)
    val CyberSurfaceDark: Color = Color(0xFF0E1126)
    val CyberBorderDark: Color = Color(0xFF252A4A)
    val CyberCyan: Color = Color(0xFF22D3EE)
    val CyberViolet: Color = Color(0xFF7C3AED)

    internal fun use(mode: AppThemeMode, preset: AppThemePreset = AppThemePreset.CYBER_VAULT) {
        activeIsLight = mode == AppThemeMode.LIGHT
        palette = when (preset) {
            AppThemePreset.CYBER_VAULT -> if (activeIsLight) CyberVaultLightPalette else CyberVaultDarkPalette
            AppThemePreset.HYLIAN -> if (activeIsLight) HylianLightPalette else HylianDarkPalette
        }
    }

    val isLightTheme: Boolean get() = activeIsLight

    val Bg: Color get() = palette.bg
    val Surface: Color get() = palette.surface
    val SurfaceElevated: Color get() = palette.surfaceElevated
    val Border: Color get() = palette.border
    val AccentPrimary: Color get() = palette.accentPrimary
    val AccentSecondary: Color get() = palette.accentSecondary
    val TextPrimary: Color get() = palette.textPrimary
    val TextSecondary: Color get() = palette.textSecondary

    // Semantic aliases preserved for existing Compose components
    val DeepSpace: Color get() = palette.bg
    val Panel: Color get() = palette.surface
    val PanelRaised: Color get() = palette.surfaceElevated
    val PurpleDeep: Color get() = palette.surfaceElevated
    val ArcadeViolet: Color get() = palette.accentSecondary
    val ElectricBlue: Color get() = palette.accentPrimary
    val Cyan: Color get() = palette.accentPrimary
    val Gold: Color get() = palette.accentSecondary
    val Magenta: Color get() = palette.error
    val WarmIvory: Color get() = palette.textPrimary
    val Positive: Color get() = palette.positive
    val Warning: Color get() = palette.warning
    val Error: Color get() = palette.error
    val Outline: Color get() = palette.border
    val Scrim: Color get() = palette.scrim
}

@Immutable
data class OzSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
)

@Immutable
data class OzMotion(
    val fast: Int = 110,
    val normal: Int = 180,
    val slow: Int = 280,
    val focusScale: Float = 1.05f,
)

val LocalOzSpacing = staticCompositionLocalOf { OzSpacing() }
val LocalOzMotion = staticCompositionLocalOf { OzMotion() }

/**
 * Responsive shell modes resolved by `OzApp` via `BoxWithConstraints`:
 * - LANDSCAPE_HANDHELD: maxWidth > maxHeight && maxHeight < 540.dp → 64dp left rail, no header/dock.
 * - DESKTOP_WIDE: maxWidth >= 960.dp && maxHeight >= 540.dp → 52dp console top bar, no dock.
 * - PORTRAIT_PHONE: everything else → 48dp header + 58dp bottom dock.
 */
enum class OzShellLayout { PORTRAIT_PHONE, LANDSCAPE_HANDHELD, DESKTOP_WIDE }

val LocalOzShellLayout = staticCompositionLocalOf { OzShellLayout.PORTRAIT_PHONE }

val OxaniumFontFamily: FontFamily = FontFamily(
    Font(resId = R.font.oxanium_semibold, weight = FontWeight.SemiBold),
    Font(resId = R.font.oxanium_bold, weight = FontWeight.Bold),
)

val SpaceGroteskFontFamily: FontFamily = FontFamily(
    Font(resId = R.font.space_grotesk_regular, weight = FontWeight.Normal),
    Font(resId = R.font.space_grotesk_medium, weight = FontWeight.Medium),
    Font(resId = R.font.space_grotesk_semibold, weight = FontWeight.SemiBold),
    Font(resId = R.font.space_grotesk_bold, weight = FontWeight.Bold),
)

/**
 * Offline embedded typography strictly enforcing Oxanium (HUD Display/Buttons/Tags)
 * and Space Grotesk (UI Body/Metadata) — zero generic system fonts.
 */
object OzTypography {
    val marquee: TextStyle = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = 1.4.sp,
    )
    val hero: TextStyle = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 0.8.sp,
    )
    val section: TextStyle = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 1.6.sp,
    )
    val tileTitle: TextStyle = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        letterSpacing = 0.4.sp,
    )
    val cardTitle: TextStyle = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.3.sp,
    )
    val hudButton: TextStyle = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        letterSpacing = 1.4.sp,
    )
    val hudTag: TextStyle = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.1.sp,
    )
    val body: TextStyle = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    )
    val meta: TextStyle = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.9.sp,
    )
}

/** 45° Chamfered HUD geometry across all controls — zero pills/ovals. */
object OzShapes {
    val tiny = CutCornerShape(4.dp)
    val small = CutCornerShape(6.dp)
    val medium = CutCornerShape(10.dp)
    val large = CutCornerShape(12.dp)
    val hero = CutCornerShape(16.dp)
    val pill = CutCornerShape(6.dp)
}

private fun schemeFor(palette: OzPalette, light: Boolean) = if (light) {
    lightColorScheme(
        primary = palette.accentPrimary,
        onPrimary = Color.White,
        secondary = palette.accentSecondary,
        onSecondary = Color.White,
        tertiary = palette.positive,
        onTertiary = Color.White,
        background = palette.bg,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        error = palette.error,
    )
} else {
    darkColorScheme(
        primary = palette.accentPrimary,
        onPrimary = palette.bg,
        secondary = palette.accentSecondary,
        onSecondary = palette.textPrimary,
        tertiary = palette.positive,
        onTertiary = palette.bg,
        background = palette.bg,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        error = palette.error,
    )
}

@Composable
fun OzTheme(
    themeMode: AppThemeMode? = null,
    themePreset: AppThemePreset? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { AppPreferences(context) }
    val requestedMode = themeMode ?: remember(prefs) { prefs.appThemeMode }
    val resolvedPreset = themePreset ?: remember(prefs) { prefs.appThemePreset }
    val resolvedMode = when (requestedMode) {
        AppThemeMode.SYSTEM -> if (isSystemInDarkTheme()) AppThemeMode.DARK else AppThemeMode.LIGHT
        else -> requestedMode
    }
    val isLight = resolvedMode == AppThemeMode.LIGHT
    OzColors.use(resolvedMode, resolvedPreset)

    val activePalette = when (resolvedPreset) {
        AppThemePreset.CYBER_VAULT -> if (isLight) CyberVaultLightPalette else CyberVaultDarkPalette
        AppThemePreset.HYLIAN -> if (isLight) HylianLightPalette else HylianDarkPalette
    }

    CompositionLocalProvider(
        LocalOzSpacing provides OzSpacing(),
        LocalOzMotion provides OzMotion(),
    ) {
        MaterialTheme(
            colorScheme = schemeFor(activePalette, isLight),
            typography = androidx.compose.material3.Typography(
                bodyLarge = OzTypography.body,
                bodyMedium = OzTypography.bodyMedium,
                titleLarge = OzTypography.hero,
                titleMedium = OzTypography.section,
                titleSmall = OzTypography.cardTitle,
                labelLarge = OzTypography.hudButton,
                labelMedium = OzTypography.meta,
                labelSmall = OzTypography.hudTag,
            ),
            content = content,
        )
    }
}

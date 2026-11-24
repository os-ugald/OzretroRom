package cl.gakuto.kizuna.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.isAvailableOnDevice
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.gakuto.kizuna.R
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AppThemeMode

/**
 * Oz RetroRom color system.
 *
 * The palette deliberately avoids dynamic Material color: Oz keeps a stable retro-console identity
 * while exposing an explicit light/dark override chosen by the user.
 */
@Immutable
private data class OzPalette(
    val deepSpace: Color,
    val panel: Color,
    val panelRaised: Color,
    val purpleDeep: Color,
    val arcadeViolet: Color,
    val electricBlue: Color,
    val cyan: Color,
    val magenta: Color,
    val warmIvory: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val positive: Color,
    val warning: Color,
    val error: Color,
    val outline: Color,
    val scrim: Color,
)

private val OzDarkPalette = OzPalette(
    deepSpace = Color(0xFF0A0F0D),
    panel = Color(0xFF141F1A),
    panelRaised = Color(0xFF1B2A23),
    purpleDeep = Color(0xFF1E3229),
    arcadeViolet = Color(0xFF1E8E63),
    electricBlue = Color(0xFF28A776),
    cyan = Color(0xFF2EC48B),
    magenta = Color(0xFFE05A5A),
    warmIvory = Color(0xFFD9B64E),
    textPrimary = Color(0xFFF3F6F4),
    textSecondary = Color(0xFFA7BDB2),
    positive = Color(0xFF2EC48B),
    warning = Color(0xFFD9B64E),
    error = Color(0xFFE05A5A),
    outline = Color(0xFF23382E),
    scrim = Color(0xCC060A08),
)

private val OzLightPalette = OzPalette(
    deepSpace = Color(0xFFF2F7F4),
    panel = Color(0xFFFFFFFF),
    panelRaised = Color(0xFFE5EFEA),
    purpleDeep = Color(0xFFCDE3D8),
    arcadeViolet = Color(0xFF177350),
    electricBlue = Color(0xFF1E8E63),
    cyan = Color(0xFF156948),
    magenta = Color(0xFFB33838),
    warmIvory = Color(0xFFA67E16),
    textPrimary = Color(0xFF0F1915),
    textSecondary = Color(0xFF43594F),
    positive = Color(0xFF177350),
    warning = Color(0xFF9E7714),
    error = Color(0xFFB3261E),
    outline = Color(0xFFB8CEC3),
    scrim = Color(0x990A0F0D),
)

/** Existing call sites keep concise OzColors.* names while exposing the shared Hylian Dark palette. */
object OzColors {
    private var palette: OzPalette = OzDarkPalette

    val HylianBg: Color = Color(0xFF0A0F0D)
    val HylianSurface: Color = Color(0xFF141F1A)
    val HylianSurfaceRaised: Color = Color(0xFF1B2A23)
    val HylianBorder: Color = Color(0xFF23382E)
    val HylianEmerald: Color = Color(0xFF1E8E63)
    val HylianGold: Color = Color(0xFFD9B64E)

    internal fun use(mode: AppThemeMode) {
        palette = if (mode == AppThemeMode.LIGHT) OzLightPalette else OzDarkPalette
    }

    val DeepSpace: Color get() = palette.deepSpace
    val Panel: Color get() = palette.panel
    val PanelRaised: Color get() = palette.panelRaised
    val PurpleDeep: Color get() = palette.purpleDeep
    val ArcadeViolet: Color get() = palette.arcadeViolet
    val ElectricBlue: Color get() = palette.electricBlue
    val Cyan: Color get() = palette.cyan
    val Gold: Color get() = palette.warmIvory
    val Magenta: Color get() = palette.magenta
    val WarmIvory: Color get() = palette.warmIvory
    val TextPrimary: Color get() = palette.textPrimary
    val TextSecondary: Color get() = palette.textSecondary
    val Positive: Color get() = palette.positive
    val Warning: Color get() = palette.warning
    val Error: Color get() = palette.error
    val Outline: Color get() = palette.outline
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

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)
private val OxaniumGoogleFont = GoogleFont(name = "Oxanium")
private val OxaniumFontFamily = FontFamily(
    Font(googleFont = OxaniumGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = OxaniumGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
    Font(googleFont = OxaniumGoogleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.ExtraBold),
)

/**
 * Typography identity: Oxanium for display/brand when Google Fonts is available, conservative
 * Android sans-serif fallback otherwise. Body copy stays neutral and metadata keeps a terminal cue.
 */
object OzTypography {
    private var displayFamily: FontFamily = FontFamily.SansSerif

    internal fun useDisplayFamily(value: FontFamily) {
        displayFamily = value
    }

    val marquee: TextStyle get() = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = 1.1.sp,
    )
    val hero: TextStyle get() = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.25.sp,
    )
    val section: TextStyle get() = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 0.1.sp,
    )
    val tileTitle: TextStyle get() = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    )
    val cardTitle: TextStyle get() = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    )
    val body: TextStyle get() = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val meta: TextStyle get() = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.35.sp,
    )
}

object OzShapes {
    val tiny = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    val small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
    val hero = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
    val pill = androidx.compose.foundation.shape.CircleShape
}

private fun darkScheme() = darkColorScheme(
    primary = OzDarkPalette.arcadeViolet,
    onPrimary = Color.White,
    secondary = OzDarkPalette.electricBlue,
    onSecondary = OzDarkPalette.deepSpace,
    tertiary = OzDarkPalette.cyan,
    onTertiary = OzDarkPalette.deepSpace,
    background = OzDarkPalette.deepSpace,
    onBackground = OzDarkPalette.textPrimary,
    surface = OzDarkPalette.panel,
    onSurface = OzDarkPalette.textPrimary,
    surfaceVariant = OzDarkPalette.panelRaised,
    onSurfaceVariant = OzDarkPalette.textSecondary,
    outline = OzDarkPalette.outline,
    error = OzDarkPalette.error,
)

private fun lightScheme() = lightColorScheme(
    primary = OzLightPalette.arcadeViolet,
    onPrimary = Color.White,
    secondary = OzLightPalette.electricBlue,
    onSecondary = Color.White,
    tertiary = OzLightPalette.cyan,
    onTertiary = Color.White,
    background = OzLightPalette.deepSpace,
    onBackground = OzLightPalette.textPrimary,
    surface = OzLightPalette.panel,
    onSurface = OzLightPalette.textPrimary,
    surfaceVariant = OzLightPalette.panelRaised,
    onSurfaceVariant = OzLightPalette.textSecondary,
    outline = OzLightPalette.outline,
    error = OzLightPalette.error,
)

@Composable
fun OzTheme(themeMode: AppThemeMode? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val requestedMode = themeMode ?: remember(context) { AppPreferences(context).appThemeMode }
    val resolvedMode = when (requestedMode) {
        AppThemeMode.SYSTEM -> if (isSystemInDarkTheme()) AppThemeMode.DARK else AppThemeMode.LIGHT
        else -> requestedMode
    }
    var downloadableFontAvailable by remember(context) { mutableStateOf(false) }

    LaunchedEffect(context) {
        downloadableFontAvailable = runCatching { GoogleFontsProvider.isAvailableOnDevice(context) }.getOrDefault(false)
    }

    OzColors.use(resolvedMode)
    OzTypography.useDisplayFamily(if (downloadableFontAvailable) OxaniumFontFamily else FontFamily.SansSerif)

    CompositionLocalProvider(
        LocalOzSpacing provides OzSpacing(),
        LocalOzMotion provides OzMotion(),
    ) {
        MaterialTheme(
            colorScheme = if (resolvedMode == AppThemeMode.LIGHT) lightScheme() else darkScheme(),
            typography = androidx.compose.material3.Typography(
                bodyLarge = OzTypography.body,
                bodyMedium = OzTypography.body,
                titleLarge = OzTypography.hero,
                titleMedium = OzTypography.section,
                titleSmall = OzTypography.cardTitle,
                labelMedium = OzTypography.meta,
            ),
            content = content,
        )
    }
}

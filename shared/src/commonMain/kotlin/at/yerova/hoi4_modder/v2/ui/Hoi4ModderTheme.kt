package at.yerova.hoi4_modder.v2.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hoi4_modder.shared.generated.resources.Res
import hoi4_modder.shared.generated.resources.material_symbols_outlined
import org.jetbrains.compose.resources.Font

private val StudioDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF061621),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD6EFFF),
    secondary = Color(0xFF60A5FA),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF22C55E),
    onTertiary = Color(0xFF052E16),
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF30363D),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF451A1A),
    onErrorContainer = Color(0xFFFECACA)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFF2563EB),
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFF16A34A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF9FAFB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

@Immutable
data class ExtendedStudioColors(
    val warning: Color,
    val onWarning: Color,
    val success: Color,
    val codeBackground: Color
)

private val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedStudioColors(
        warning = Color.Unspecified,
        onWarning = Color.Unspecified,
        success = Color.Unspecified,
        codeBackground = Color.Unspecified
    )
}

private val StudioShapes = Shapes(
    extraSmall = HMDefaults.ComponentShape as CornerBasedShape,
    small = HMDefaults.ComponentShape,
    medium = HMDefaults.PanelShape as CornerBasedShape,
    large = HMDefaults.PanelShape,
    extraLarge = HMDefaults.PanelShape
)

/**
 * Compact desktop typography so standard Text() calls (which default to bodyLarge)
 * look like a crisp IDE (13.sp) instead of a bloated mobile app (16.sp).
 */
private val StudioTypography = Typography(
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    bodySmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp
    )
)

val LocalSymbolFont = staticCompositionLocalOf<FontFamily> {
    error("No Symbol Font provided - ensure this is called within Hoi4ModderTheme")
}

@Composable
fun Hoi4ModderTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    disableMobileTouchTargets: Boolean = true,
    content: @Composable () -> Unit
) {
    val symbolFont = FontFamily(Font(Res.font.material_symbols_outlined))

    val activeColorScheme = if (useDarkTheme) StudioDarkColorScheme else StudioLightColorScheme

    val activeExtendedColors = if (useDarkTheme) {
        ExtendedStudioColors(
            warning = Color(0xFFF59E0B),
            onWarning = Color(0xFF271700),
            success = Color(0xFF22C55E),
            codeBackground = Color(0xFF090D11)
        )
    } else {
        ExtendedStudioColors(
            warning = Color(0xFFD97706),
            onWarning = Color(0xFFFFFFFF),
            success = Color(0xFF16A34A),
            codeBackground = Color(0xFFF1F5F9)
        )
    }

    val activeSelectionColors = TextSelectionColors(
        handleColor = activeColorScheme.primary,
        backgroundColor = activeColorScheme.primary.copy(alpha = 0.28f)
    )

    MaterialTheme(
        colorScheme = activeColorScheme,
        motionScheme = MotionScheme.standard(),
        shapes = StudioShapes,
        typography = StudioTypography
    ) {
        CompositionLocalProvider(
            LocalExtendedColors provides activeExtendedColors,
            LocalTextSelectionColors provides activeSelectionColors,
            LocalMinimumInteractiveComponentSize provides if (disableMobileTouchTargets) Dp.Unspecified else 48.dp,
            LocalSymbolFont provides symbolFont,
            content = content
        )
    }
}

object Hoi4ModderTheme {
    val extendedColors: ExtendedStudioColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current

    val symbolFont: FontFamily
        @Composable
        @ReadOnlyComposable
        get() = LocalSymbolFont.current
}
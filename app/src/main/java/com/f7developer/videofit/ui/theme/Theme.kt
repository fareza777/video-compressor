package com.f7developer.videofit.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.f7developer.videofit.core.data.ThemeMode

/** Everything the UI paints with, resolved per theme. */
data class VFPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val accent: Color,
    val accentDim: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val gradientTop: Color,
    val isLight: Boolean,
)

val DarkPalette = VFPalette(
    background = Color(0xFF0B0D10),
    surface = Color(0xFF14181D),
    surfaceElevated = Color(0xFF1B2128),
    outline = Color(0xFF2A323B),
    accent = Color(0xFFB6F36A),
    accentDim = Color(0xFF8FCC3F),
    onAccent = Color(0xFF101509),
    textPrimary = Color(0xFFF3F6F1),
    textSecondary = Color(0xFF9AA5A0),
    textTertiary = Color(0xFF6B7772),
    danger = Color(0xFFFF7A6B),
    warning = Color(0xFFFFC46B),
    success = Color(0xFF7BE0C3),
    gradientTop = Color(0xFF17201A),
    isLight = false,
)

/** Light is the default. The accent darkens to stay readable on white. */
val LightPalette = VFPalette(
    background = Color(0xFFF4F6F0),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEDF1E6),
    outline = Color(0xFFD9E1D0),
    accent = Color(0xFF4F8A12),
    accentDim = Color(0xFF6FA92B),
    onAccent = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF12180F),
    textSecondary = Color(0xFF505B4A),
    textTertiary = Color(0xFF7A8574),
    danger = Color(0xFFB3261E),
    warning = Color(0xFF8A5A00),
    success = Color(0xFF10705A),
    gradientTop = Color(0xFFE9F1DE),
    isLight = true,
)

val LocalVFPalette = staticCompositionLocalOf { LightPalette }

/**
 * Theme-aware colour accessors. Every screen keeps using `VF.Surface` etc., but
 * the value now follows the selected light/dark palette.
 */
object VF {
    val Background: Color @Composable get() = LocalVFPalette.current.background
    val Surface: Color @Composable get() = LocalVFPalette.current.surface
    val SurfaceElevated: Color @Composable get() = LocalVFPalette.current.surfaceElevated
    val Outline: Color @Composable get() = LocalVFPalette.current.outline
    val Accent: Color @Composable get() = LocalVFPalette.current.accent
    val AccentDim: Color @Composable get() = LocalVFPalette.current.accentDim
    val OnAccent: Color @Composable get() = LocalVFPalette.current.onAccent
    val TextPrimary: Color @Composable get() = LocalVFPalette.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalVFPalette.current.textSecondary
    val TextTertiary: Color @Composable get() = LocalVFPalette.current.textTertiary
    val Danger: Color @Composable get() = LocalVFPalette.current.danger
    val Warning: Color @Composable get() = LocalVFPalette.current.warning
    val Success: Color @Composable get() = LocalVFPalette.current.success
    val GradientTop: Color @Composable get() = LocalVFPalette.current.gradientTop
}

private fun schemeFor(palette: VFPalette) = if (palette.isLight) {
    lightColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.accentDim,
        onPrimaryContainer = palette.onAccent,
        secondary = palette.success,
        onSecondary = Color.White,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.outline,
        outlineVariant = palette.outline,
        error = palette.danger,
        onError = Color.White,
    )
} else {
    darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.accentDim,
        onPrimaryContainer = palette.onAccent,
        secondary = palette.success,
        onSecondary = palette.onAccent,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.outline,
        outlineVariant = palette.outline,
        error = palette.danger,
        onError = palette.onAccent,
    )
}

private val VFTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.6).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun VideoFitTheme(
    mode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalVFPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = VFTypography,
            content = content,
        )
    }
}

/** Convenience spacing scale so screens stay visually consistent. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val huge = 48.dp
}

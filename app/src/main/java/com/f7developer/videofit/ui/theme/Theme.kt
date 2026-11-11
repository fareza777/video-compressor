package com.f7developer.videofit.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** VideoFit palette: night-dark surfaces with a single lime accent. */
object VF {
    val Background = Color(0xFF0B0D10)
    val Surface = Color(0xFF14181D)
    val SurfaceElevated = Color(0xFF1B2128)
    val Outline = Color(0xFF2A323B)
    val Accent = Color(0xFFB6F36A)
    val AccentDim = Color(0xFF7FAE3F)
    val OnAccent = Color(0xFF101509)
    val TextPrimary = Color(0xFFF3F6F1)
    val TextSecondary = Color(0xFF9AA5A0)
    val TextTertiary = Color(0xFF6B7772)
    val Danger = Color(0xFFFF7A6B)
    val Warning = Color(0xFFFFC46B)
    val Success = Color(0xFF7BE0C3)
    val GradientTop = Color(0xFF17201A)
}

private val VFColorScheme = darkColorScheme(
    primary = VF.Accent,
    onPrimary = VF.OnAccent,
    primaryContainer = VF.AccentDim,
    onPrimaryContainer = VF.OnAccent,
    secondary = VF.Success,
    onSecondary = VF.OnAccent,
    background = VF.Background,
    onBackground = VF.TextPrimary,
    surface = VF.Surface,
    onSurface = VF.TextPrimary,
    surfaceVariant = VF.SurfaceElevated,
    onSurfaceVariant = VF.TextSecondary,
    outline = VF.Outline,
    outlineVariant = VF.Outline,
    error = VF.Danger,
    onError = VF.OnAccent,
)

private val VFTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
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
fun VideoFitTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = VFColorScheme,
        typography = VFTypography,
        content = content,
    )
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

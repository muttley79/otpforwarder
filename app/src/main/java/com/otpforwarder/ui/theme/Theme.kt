package com.otpforwarder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The one warm accent in the app: the lamp that says "sharing is live". It is not used for anything else. */
val Lamp = Color(0xFFF2A72B)

private val Light = lightColorScheme(
    primary = Color(0xFF12433D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE8DF),
    onPrimaryContainer = Color(0xFF0A2A26),
    secondary = Color(0xFF4E605B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE8E3),
    onSecondaryContainer = Color(0xFF14201D),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFE2A8),
    onTertiaryContainer = Color(0xFF2B1B00),
    background = Color(0xFFF3F6F4),
    onBackground = Color(0xFF151D1B),
    surface = Color(0xFFF3F6F4),
    onSurface = Color(0xFF151D1B),
    onSurfaceVariant = Color(0xFF4A5754),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFE9EFEC),
    surfaceContainer = Color(0xFFE2E9E6),
    surfaceContainerHigh = Color(0xFFDAE3DF),
    outline = Color(0xFF75837F),
    outlineVariant = Color(0xFFC3CFCB),
    error = Color(0xFFB3412F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD3),
    onErrorContainer = Color(0xFF410A02),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3C3),
    onPrimary = Color(0xFF00201B),
    primaryContainer = Color(0xFF1B4A43),
    onPrimaryContainer = Color(0xFFCFE8DF),
    secondary = Color(0xFFB4C9C2),
    onSecondary = Color(0xFF1F332E),
    secondaryContainer = Color(0xFF2C3F3A),
    onSecondaryContainer = Color(0xFFDDE8E3),
    tertiary = Color(0xFFF2B84B),
    tertiaryContainer = Color(0xFF4A3000),
    onTertiaryContainer = Color(0xFFFFE2A8),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFE0E8E5),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFE0E8E5),
    onSurfaceVariant = Color(0xFFA9B8B3),
    surfaceContainerLowest = Color(0xFF0A100E),
    surfaceContainerLow = Color(0xFF161F1C),
    surfaceContainer = Color(0xFF1B2521),
    surfaceContainerHigh = Color(0xFF232E2A),
    outline = Color(0xFF7F8E89),
    outlineVariant = Color(0xFF34423E),
    error = Color(0xFFFFB4A5),
    onError = Color(0xFF561003),
    errorContainer = Color(0xFF7A2415),
    onErrorContainer = Color(0xFFFFDAD3),
)

private val Heading = FontFamily.Serif
private val Body = FontFamily.SansSerif

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

/** Digits only: monospaced so a code reads the same width every time. */
val CodeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, letterSpacing = 3.sp)

// Different sizes for different jobs: small controls, ordinary cards, and the big hero surfaces.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun OtpTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

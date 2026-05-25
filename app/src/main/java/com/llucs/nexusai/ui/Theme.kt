package com.llucs.nexusai.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val NexusBlue = Color(0xFF5B5FFF)
val NexusBlueDark = Color(0xFF3D3FFF)
val NexusPurple = Color(0xFF9B59B6)
val NexusCyan = Color(0xFF00D2FF)
val NexusSurfaceDark = Color(0xFF0E0E12)
val NexusSurfaceLight = Color(0xFFF8F8FC)
val NexusCardDark = Color(0xFF1A1A22)
val NexusCardLight = Color(0xFFFFFFFF)

private val LightScheme = lightColorScheme(
    primary = NexusBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = NexusBlueDark,
    secondary = NexusPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E0FF),
    onSecondaryContainer = Color(0xFF3B1D5E),
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2DFDB),
    onTertiaryContainer = Color(0xFF00251E),
    background = NexusSurfaceLight,
    onBackground = Color(0xFF111113),
    surface = NexusCardLight,
    onSurface = Color(0xFF111113),
    surfaceVariant = Color(0xFFF0F0F5),
    onSurfaceVariant = Color(0xFF3A3A42),
    outline = Color(0xFFD0D0D8),
    outlineVariant = Color(0xFFE5E5EC),
    error = Color(0xFFE53935),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = NexusSurfaceDark,
    inverseOnSurface = Color(0xFFF0F0F5),
    inversePrimary = Color(0xFFB0B0FF),
    surfaceTint = NexusBlue
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB0B0FF),
    onPrimary = Color(0xFF1A1A2E),
    primaryContainer = NexusBlueDark,
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFD0B0F0),
    onSecondary = Color(0xFF2A1A3E),
    secondaryContainer = Color(0xFF4A2A6E),
    onSecondaryContainer = Color(0xFFF0E0FF),
    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF00332E),
    tertiaryContainer = Color(0xFF00695C),
    onTertiaryContainer = Color(0xFFB2DFDB),
    background = NexusSurfaceDark,
    onBackground = Color(0xFFE8E8F0),
    surface = NexusCardDark,
    onSurface = Color(0xFFE8E8F0),
    surfaceVariant = Color(0xFF1E1E28),
    onSurfaceVariant = Color(0xFFC8C8D4),
    outline = Color(0xFF2E2E3A),
    outlineVariant = Color(0xFF22222E),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    errorContainer = Color(0xFF4D2424),
    onErrorContainer = Color(0xFFFFB4AB),
    inverseSurface = NexusSurfaceLight,
    inverseOnSurface = Color(0xFF1A1A22),
    inversePrimary = NexusBlue,
    surfaceTint = Color(0xFFB0B0FF)
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 34.sp, lineHeight = 40.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.05.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
)

@Composable
fun NexusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content
    )
}

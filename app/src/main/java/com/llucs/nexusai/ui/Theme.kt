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

private val LightScheme = lightColorScheme(
    primary = Color(0xFF111113),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E9EE),
    onPrimaryContainer = Color(0xFF111113),

    secondary = Color(0xFF2A2A2F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E2E8),
    onSecondaryContainer = Color(0xFF111113),

    tertiary = Color(0xFF4A4A52),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E5EA),
    onTertiaryContainer = Color(0xFF111113),

    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF111113),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111113),

    surfaceVariant = Color(0xFFF0F0F2),
    onSurfaceVariant = Color(0xFF3A3A40),

    outline = Color(0xFFCDCDD2),
    outlineVariant = Color(0xFFE3E3E8),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF0B0B0C),
    primaryContainer = Color(0xFF232327),
    onPrimaryContainer = Color(0xFFFFFFFF),

    secondary = Color(0xFFE5E5EA),
    onSecondary = Color(0xFF0B0B0C),
    secondaryContainer = Color(0xFF1C1C20),
    onSecondaryContainer = Color(0xFFEDEDF2),

    tertiary = Color(0xFFBDBDC6),
    onTertiary = Color(0xFF0B0B0C),
    tertiaryContainer = Color(0xFF1C1C20),
    onTertiaryContainer = Color(0xFFEDEDF2),

    background = Color(0xFF0B0B0C),
    onBackground = Color(0xFFEDEDF2),

    surface = Color(0xFF111113),
    onSurface = Color(0xFFEDEDF2),

    surfaceVariant = Color(0xFF1A1A1E),
    onSurfaceVariant = Color(0xFFCFCFD8),

    outline = Color(0xFF34343A),
    outlineVariant = Color(0xFF25252A),

    error = Color(0xFFFF5449),
    onError = Color.White,
    errorContainer = Color(0xFF4D2424),
    onErrorContainer = Color(0xFFFFB4AB)
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val ExpressiveTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )
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

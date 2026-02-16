package com.llucs.nexusai.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape

private val BrandPurple = Color(0xFF7C3AED)
private val BrandPurpleDark = Color(0xFF8B5CF6)
private val BrandTeal = Color(0xFF22C55E)
private val BrandPink = Color(0xFFEC4899)

private val LightScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF21005D),

    secondary = BrandTeal,
    onSecondary = Color(0xFF05210F),
    secondaryContainer = Color(0xFFD5FDE4),
    onSecondaryContainer = Color(0xFF062114),

    tertiary = BrandPink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E8),
    onTertiaryContainer = Color(0xFF3B001E),

    background = Color(0xFFF8F7FB),
    onBackground = Color(0xFF1B1B1F),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1F),

    surfaceVariant = Color(0xFFE9E6EF),
    onSurfaceVariant = Color(0xFF49454F),

    outline = Color(0xFF79747E),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkScheme = darkColorScheme(
    primary = BrandPurpleDark,
    onPrimary = Color(0xFF1B0B2A),
    primaryContainer = Color(0xFF2A1741),
    onPrimaryContainer = Color(0xFFEDE4FF),

    secondary = BrandTeal,
    onSecondary = Color(0xFF05210F),
    secondaryContainer = Color(0xFF0B2B19),
    onSecondaryContainer = Color(0xFFD5FDE4),

    tertiary = BrandPink,
    onTertiary = Color(0xFF2A0014),
    tertiaryContainer = Color(0xFF3B001E),
    onTertiaryContainer = Color(0xFFFFD8E8),

    background = Color(0xFF0C0B10),
    onBackground = Color(0xFFE6E1E5),

    surface = Color(0xFF121117),
    onSurface = Color(0xFFE6E1E5),

    surfaceVariant = Color(0xFF1E1B26),
    onSurfaceVariant = Color(0xFFCAC4D0),

    outline = Color(0xFF948F99),
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
    dynamicColor: Boolean = true,
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

package com.llucs.nexusai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val PrimaryColor = Color(0xFF10A37F) // Verde moderno
private val PrimaryVariant = Color(0xFF0D8A6C)
private val BackgroundDark = Color(0xFF0A0A0A) // Quase preto
private val SurfaceDark = Color(0xFF1A1A1A) // Cinza escuro
private val SurfaceVariantDark = Color(0xFF2A2A2A) 
private val OnSurface = Color(0xFFECECEC) 
private val OnSurfaceVariant = Color(0xFFB0B0B0)

private val UserMessageBg = Color(0xFF2D5BFF) 
private val AssistantMessageBg = Color(0xFF2A2A2A) 

private val DarkScheme = darkColorScheme(
    primary = PrimaryColor,
    onPrimary = Color.White,
    primaryContainer = UserMessageBg,
    onPrimaryContainer = Color.White,
    
    secondary = PrimaryVariant,
    onSecondary = Color.White,
    
    background = BackgroundDark,
    onBackground = OnSurface,
    
    surface = SurfaceDark,
    onSurface = OnSurface,
    
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariant,
    
    error = Color(0xFFFF5449),
    onError = Color.White,
    errorContainer = Color(0xFF4D2424),
    onErrorContainer = Color(0xFFFFB4AB)
)

private val NexusTypography = Typography(

)

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = NexusTypography,
        content = content
    )
}

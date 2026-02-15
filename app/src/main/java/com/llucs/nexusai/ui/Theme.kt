package com.llucs.nexusai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF7C4DFF)
private val PurpleDark = Color(0xFF5E35B1)
private val BackgroundDark = Color(0xFF0E0B14)

private val DarkScheme = darkColorScheme(
    primary = Purple,
    secondary = PurpleDark,
    tertiary = Purple,
    background = BackgroundDark,
    surface = BackgroundDark
)

private val NexusTypography = Typography()

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = NexusTypography,
        content = content
    )
}

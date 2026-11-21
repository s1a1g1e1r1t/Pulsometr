package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PulseCoral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C1021),
    onPrimaryContainer = PulseBlushContainer,
    secondary = PulseRoseSoft,
    onSecondary = Color(0xFF3E0B18),
    secondaryContainer = ObsidianSurfaceHigh,
    onSecondaryContainer = ObsidianTextPrimary,
    tertiary = ZoneNormalMint,
    onTertiary = Color(0xFF00241D),
    background = ObsidianBg,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = ObsidianTextMuted,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF2E1E24),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF3E000F),
)

private val LightColorScheme = lightColorScheme(
    primary = PulseCrimson,
    onPrimary = Color.White,
    primaryContainer = PulseBlushContainer,
    onPrimaryContainer = PulseBlushInk,
    secondary = Color(0xFF9C2D49),
    onSecondary = Color.White,
    secondaryContainer = IvorySurfaceHigh,
    onSecondaryContainer = IvoryTextPrimary,
    tertiary = Color(0xFF0E9F84),
    onTertiary = Color.White,
    background = IvoryBg,
    onBackground = IvoryTextPrimary,
    surface = IvorySurface,
    onSurface = IvoryTextPrimary,
    surfaceVariant = IvorySurfaceHigh,
    onSurfaceVariant = IvoryTextMuted,
    outline = IvoryBorder,
    outlineVariant = Color(0xFFF3E2E7),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

package com.andika.watersupply.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF17384D)
val Sky = Color(0xFFB4E1EB)
val Mist = Color(0xFF95BDD7)
val Water = Color(0xFF78A4CB)
val Sun = Color(0xFFF9E8A2)
private val Palette = lightColorScheme(primary = Ink, onPrimary = Color.White,
    primaryContainer = Sun, onPrimaryContainer = Ink, secondary = Ink,
    secondaryContainer = Mist, onSecondaryContainer = Ink, background = Sky,
    onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Sky, onSurfaceVariant = Ink, outline = Color(0xFF526E80),
    outlineVariant = Color(0xFF526E80), surfaceTint = Ink,
    surfaceContainer = Color.White, surfaceContainerHigh = Sky,
    surfaceContainerHighest = Mist, surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White, tertiary = Ink, onTertiary = Color.White,
    tertiaryContainer = Sun, onTertiaryContainer = Ink)
@Composable fun WaterSupplyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, typography = Typography, content = content)
}

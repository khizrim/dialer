package com.khizrim.dialer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val CallGreen = Color(0xFF22C55E)
val DeclineRed = Color(0xFFEF4444)
val SpamOrange = Color(0xFFF97316)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E5FF),
    onPrimaryContainer = Color(0xFF0B1A6B),
    secondary = Color(0xFF5B6478),
    secondaryContainer = Color(0xFFE6E9F2),
    onSecondaryContainer = Color(0xFF1B2232),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFF7F8FC),
    surfaceContainer = Color(0xFFEEF0F7),
    surfaceContainerHigh = Color(0xFFE8EBF3),
    surfaceContainerLow = Color(0xFFF2F4FA),
    onSurface = Color(0xFF151A24),
    onSurfaceVariant = Color(0xFF5B6478),
    outlineVariant = Color(0xFFD5D9E4),
    error = DeclineRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AA8FF),
    onPrimary = Color(0xFF0B1A6B),
    primaryContainer = Color(0xFF2A3A9E),
    onPrimaryContainer = Color(0xFFE0E5FF),
    secondary = Color(0xFFB9C0D2),
    secondaryContainer = Color(0xFF2A3142),
    onSecondaryContainer = Color(0xFFE2E6F0),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    surfaceContainer = Color(0xFF1A1F27),
    surfaceContainerHigh = Color(0xFF232933),
    surfaceContainerLow = Color(0xFF151A21),
    onSurface = Color(0xFFE6E8EE),
    onSurfaceVariant = Color(0xFF9AA2B4),
    outlineVariant = Color(0xFF2E3542),
    error = Color(0xFFFF8A8A),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DialerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}

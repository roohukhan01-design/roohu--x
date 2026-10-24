package com.roohu.roohux.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roohu.roohux.core.settings.ThemeMode

// Brand: cinematic near-black with one warm, restrained accent (amber).
val Ink = Color(0xFF0A0B0F)
val Accent = Color(0xFFF5B544)
val AccentDeep = Color(0xFFC77D12)
val Teal = Color(0xFF6FD6C4)

private val DarkColors = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF2A1A00),
    primaryContainer = Color(0xFF3D2A06), onPrimaryContainer = Color(0xFFFFDDA3),
    secondary = Color(0xFF9BB4D8), onSecondary = Color(0xFF0E1A2B),
    secondaryContainer = Color(0xFF222B3A), onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Teal, onTertiary = Color(0xFF00201B),
    background = Ink, onBackground = Color(0xFFECEDF2),
    surface = Color(0xFF111318), onSurface = Color(0xFFECEDF2),
    surfaceVariant = Color(0xFF1B1E26), onSurfaceVariant = Color(0xFF9AA3B5),
    surfaceContainerLowest = Color(0xFF07080B), surfaceContainerLow = Color(0xFF0F1116),
    surfaceContainer = Color(0xFF161922), surfaceContainerHigh = Color(0xFF1C202A), surfaceContainerHighest = Color(0xFF232834),
    outline = Color(0xFF3A4050), outlineVariant = Color(0xFF272C38),
    error = Color(0xFFFF6B6B), onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF4A1418), onErrorContainer = Color(0xFFFFD9D9),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A00), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDA3), onPrimaryContainer = Color(0xFF2A1A00),
    secondary = Color(0xFF455A7A), tertiary = Color(0xFF00695C),
    background = Color(0xFFF7F5F0), onBackground = Color(0xFF16181F),
    surface = Color.White, onSurface = Color(0xFF16181F),
    surfaceVariant = Color(0xFFECE8E0), onSurfaceVariant = Color(0xFF555B6B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F0E9),
    surfaceContainer = Color(0xFFEFEBE3), surfaceContainerHigh = Color(0xFFE9E5DC), surfaceContainerHighest = Color(0xFFE3DFD5),
    outline = Color(0xFF8A8F9C), outlineVariant = Color(0xFFD5D1C8),
    error = Color(0xFFB3261E),
)

private val RoohuXShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun RoohuXTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = RoohuXTypography,
        shapes = RoohuXShapes,
        content = content,
    )
}

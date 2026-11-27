package com.thetesting.musicapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MidnightBg = Color(0xFF0B0F17)
val MidnightSurface = Color(0xFF131A26)
val MidnightSurfaceHigh = Color(0xFF1C2536)
val EmeraldAccent = Color(0xFF10B981)
val EmeraldBright = Color(0xFF34D399)
val CyanAccent = Color(0xFF22D3EE)
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)
val ErrorRose = Color(0xFFF87171)

private val MusikKuColorScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = Color(0xFF052E1E),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = CyanAccent,
    onSecondary = Color(0xFF083344),
    background = MidnightBg,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    error = ErrorRose,
    onError = Color(0xFF450A0A)
)

@Composable
fun MusikKuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MusikKuColorScheme,
        typography = Typography(),
        content = content
    )
}

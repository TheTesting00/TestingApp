package com.thetesting.musicapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.thetesting.musicapp.model.AppThemeId

data class ThemeGradientPalette(
    val start: Color,
    val end: Color,
    val isLight: Boolean
)

val LocalThemeExtras = staticCompositionLocalOf {
    ThemeGradientPalette(
        start = Color(0xFF10B981),
        end = Color(0xFF06B6D4),
        isLight = false
    )
}

fun paletteForTheme(themeId: AppThemeId): Pair<ColorScheme, ThemeGradientPalette> {
    return when (themeId) {
        AppThemeId.MIDNIGHT_EMERALD -> darkColorScheme(
            primary = Color(0xFF10B981),
            onPrimary = Color(0xFF052E1E),
            primaryContainer = Color(0xFF064E3B),
            onPrimaryContainer = Color(0xFFA7F3D0),
            secondary = Color(0xFF22D3EE),
            onSecondary = Color(0xFF083344),
            background = Color(0xFF0B0F17),
            onBackground = Color(0xFFF3F4F6),
            surface = Color(0xFF131A26),
            onSurface = Color(0xFFF3F4F6),
            surfaceVariant = Color(0xFF1C2536),
            onSurfaceVariant = Color(0xFF9CA3AF),
            error = Color(0xFFF87171),
            onError = Color(0xFF450A0A)
        ) to ThemeGradientPalette(Color(0xFF10B981), Color(0xFF06B6D4), false)

        AppThemeId.CYBER_VIOLET -> darkColorScheme(
            primary = Color(0xFF8B5CF6),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF4C1D95),
            onPrimaryContainer = Color(0xFFEDE9FE),
            secondary = Color(0xFFEC4899),
            onSecondary = Color(0xFFFFFFFF),
            background = Color(0xFF0C0818),
            onBackground = Color(0xFFF5F3FF),
            surface = Color(0xFF16102A),
            onSurface = Color(0xFFF5F3FF),
            surfaceVariant = Color(0xFF231A3F),
            onSurfaceVariant = Color(0xFFC4B5FD),
            error = Color(0xFFFB7185),
            onError = Color(0xFF4C0519)
        ) to ThemeGradientPalette(Color(0xFF6D28D9), Color(0xFFEC4899), false)

        AppThemeId.GLASS_AURORA -> darkColorScheme(
            primary = Color(0xFF60A5FA),
            onPrimary = Color(0xFF082F49),
            primaryContainer = Color(0xFF1E3A8A),
            onPrimaryContainer = Color(0xFFDBEAFE),
            secondary = Color(0xFFA78BFA),
            onSecondary = Color(0xFF2E1065),
            background = Color(0xFF091124),
            onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF121D38),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF1C2C4E),
            onSurfaceVariant = Color(0xFF94A3B8),
            error = Color(0xFFF87171),
            onError = Color(0xFF450A0A)
        ) to ThemeGradientPalette(Color(0xFF3B82F6), Color(0xFF8B5CF6), false)

        AppThemeId.SUNSET_BOLD -> darkColorScheme(
            primary = Color(0xFFF97316),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF7C2D12),
            onPrimaryContainer = Color(0xFFFFEDD5),
            secondary = Color(0xFFF43F5E),
            onSecondary = Color(0xFFFFFFFF),
            background = Color(0xFF140B0E),
            onBackground = Color(0xFFFFF7ED),
            surface = Color(0xFF211218),
            onSurface = Color(0xFFFFF7ED),
            surfaceVariant = Color(0xFF331B24),
            onSurfaceVariant = Color(0xFFFDBA74),
            error = Color(0xFFEF4444),
            onError = Color(0xFF450A0A)
        ) to ThemeGradientPalette(Color(0xFFF97316), Color(0xFFEC4899), false)

        AppThemeId.ROSE_VELVET -> darkColorScheme(
            primary = Color(0xFFF472B6),
            onPrimary = Color(0xFF500724),
            primaryContainer = Color(0xFF831843),
            onPrimaryContainer = Color(0xFFFCE7F3),
            secondary = Color(0xFFFBBF24),
            onSecondary = Color(0xFF451A03),
            background = Color(0xFF140C12),
            onBackground = Color(0xFFFDF2F8),
            surface = Color(0xFF21141E),
            onSurface = Color(0xFFFDF2F8),
            surfaceVariant = Color(0xFF321E2D),
            onSurfaceVariant = Color(0xFFF9A8D4),
            error = Color(0xFFFB7185),
            onError = Color(0xFF4C0519)
        ) to ThemeGradientPalette(Color(0xFFDB2777), Color(0xFFF59E0B), false)

        AppThemeId.MINIMAL_LIGHT -> lightColorScheme(
            primary = Color(0xFF5B4FE9),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEDE9FE),
            onPrimaryContainer = Color(0xFF312E81),
            secondary = Color(0xFF0EA5E9),
            onSecondary = Color(0xFFFFFFFF),
            background = Color(0xFFF6F7FB),
            onBackground = Color(0xFF111827),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111827),
            surfaceVariant = Color(0xFFE5E7EB),
            onSurfaceVariant = Color(0xFF6B7280),
            error = Color(0xFFDC2626),
            onError = Color(0xFFFFFFFF)
        ) to ThemeGradientPalette(Color(0xFF5B4FE9), Color(0xFFEC4899), true)
    }
}

@Composable
fun MusikKuTheme(
    themeId: AppThemeId = AppThemeId.MIDNIGHT_EMERALD,
    content: @Composable () -> Unit
) {
    val (colorScheme, extras) = paletteForTheme(themeId)
    CompositionLocalProvider(LocalThemeExtras provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}

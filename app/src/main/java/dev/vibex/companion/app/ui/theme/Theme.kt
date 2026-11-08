package dev.vibex.companion.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun CompanionTheme(appearance: Appearance = Appearance.System, accent: AccentChoice = AccentChoice.Blue, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.Dark -> true
        Appearance.Light -> false
        Appearance.System -> isSystemInDarkTheme()
    }
    val colors = companionColors(dark, accent)
    val scheme = if (dark) darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = colors.bg,
        onBackground = colors.textPrimary,
        surface = colors.bgElevated,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.bgElevated,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.stroke,
        error = colors.danger,
        tertiary = colors.hold,
    ) else lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = colors.bg,
        onBackground = colors.textPrimary,
        surface = colors.bgElevated,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.bgElevated,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.stroke,
        error = colors.danger,
        tertiary = colors.hold,
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalCompanionColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = CompanionTypography, content = content)
    }
}

object VibexTheme {
    val colors: CompanionColors
        @Composable @ReadOnlyComposable get() = LocalCompanionColors.current
}

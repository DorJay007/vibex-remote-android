package dev.vibex.companion.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Hold = Color(0xFFD48A2A)
val Stop = Color(0xFFC43C3C)
val Pass = Color(0xFF2F8A63)
val Quiet = Color(0xFF6B7380)
val Ink = Color(0xFF12161C)
val Paper = Color(0xFFE6E7E9)

@Immutable
data class CompanionColors(
    val isDark: Boolean,
    val bg: Color,
    val bgElevated: Color,
    val sheet: Color,
    val card: Color,
    val stroke: Color,
    val hairline: Color,
    val codeSurface: Color,
    val rail: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentDim: Color,
    val danger: Color,
    val hold: Color,
    val pass: Color,
)

fun companionColors(dark: Boolean, accent: AccentChoice = AccentChoice.Ink) = if (dark) CompanionColors(
    isDark = true,
    bg = Color(0xFF121417),
    bgElevated = Color(0xFF1A2029),
    sheet = Color(0xFF2A3038),
    card = Color(0xFF343B45),
    stroke = Color.White.copy(0.10f),
    hairline = Color.White.copy(0.06f),
    codeSurface = Color.Black.copy(0.30f),
    rail = Color.White.copy(0.16f),
    textPrimary = Color(0xFFF4F6F8),
    textSecondary = Color(0xFFA8B0BB),
    textTertiary = Color(0xFF6E7784),
    accent = accent.color(true),
    onAccent = accent.onColor(true),
    accentDim = accent.color(true).copy(0.18f),
    danger = Color(0xFFF07575),
    hold = Color(0xFFE0A04A),
    pass = Color(0xFF4CAF82),
) else CompanionColors(
    isDark = false,
    bg = Paper,
    bgElevated = Color.White,
    sheet = Color(0xFFE6E7E9),
    card = Color(0xFFF3F4F5),
    stroke = Color.Black.copy(0.08f),
    hairline = Color.Black.copy(0.06f),
    codeSurface = Color.Black.copy(0.045f),
    rail = Color.Black.copy(0.20f),
    textPrimary = Color(0xFF1A1F27),
    textSecondary = Color(0xFF5C6570),
    textTertiary = Color(0xFF8A929C),
    accent = accent.color(false),
    onAccent = accent.onColor(false),
    accentDim = accent.color(false).copy(0.14f),
    danger = Stop,
    hold = Hold,
    pass = Pass,
)

val LocalCompanionColors = staticCompositionLocalOf { companionColors(false) }

fun folderTint(id: String): Color {
    val palette = listOf(
        Color(0xFF5C6570),
        Color(0xFF4E8CA0),
        Color(0xFF6A7BD4),
        Color(0xFF3F8A78),
        Color(0xFF7A6BB8),
    )
    val index = id.hashCode().toUInt() % palette.size.toUInt()
    return palette[index.toInt()]
}

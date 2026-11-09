package dev.vibex.companion.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Hold = Color(0xFFFF9500)
val Stop = Color(0xFFFF3B30)
val Pass = Color(0xFF34C759)
val Quiet = Color(0xFF8E8E93)
val Ink = Color(0xFF000000)
val Paper = Color(0xFFF2F2F7)

@Immutable
data class CompanionColors(
    val isDark: Boolean,
    val bg: Color,
    val bgElevated: Color,
    val sheet: Color,
    val card: Color,
    val segmentedTrack: Color,
    val segmentedThumb: Color,
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

fun companionColors(dark: Boolean, accent: AccentChoice = AccentChoice.Blue) = if (dark) CompanionColors(
    isDark = true,
    bg = Color(0xFF000000),
    bgElevated = Color(0xFF1C1C1E),
    sheet = Color(0xFF111113),
    card = Color(0xFF1C1C1E),
    segmentedTrack = Color(0xFF2C2C2E),
    segmentedThumb = Color(0xFF3A3A3C),
    stroke = Color.White.copy(0.08f),
    hairline = Color.White.copy(0.10f),
    codeSurface = Color(0xFF2C2C2E),
    rail = Color.White.copy(0.16f),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    textTertiary = Color(0xFF636366),
    accent = accent.color(true),
    onAccent = accent.onColor(true),
    accentDim = accent.color(true).copy(0.18f),
    danger = Color(0xFFFF453A),
    hold = Color(0xFFFF9F0A),
    pass = Color(0xFF30D158),
) else CompanionColors(
    isDark = false,
    bg = Paper,
    bgElevated = Color.White,
    sheet = Color(0xFFF2F2F7),
    card = Color.White,
    segmentedTrack = Color(0xFFE5E5EA),
    segmentedThumb = Color.White,
    stroke = Color.Black.copy(0.05f),
    hairline = Color.Black.copy(0.08f),
    codeSurface = Color(0xFFE5E5EA),
    rail = Color.Black.copy(0.18f),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF6C6C70),
    textTertiary = Color(0xFF8E8E93),
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

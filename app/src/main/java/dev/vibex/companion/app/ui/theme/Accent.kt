package dev.vibex.companion.app.ui.theme

import androidx.compose.ui.graphics.Color

enum class AccentChoice(
    val id: String,
    val label: String,
    val light: Color,
    val dark: Color,
) {
    Ink("ink", "深色", Color(0xFF1A1F27), Color(0xFFE8EAED)),
    Gray("gray", "灰色", Color(0xFF5C6570), Color(0xFFC4C8CE)),
    Blue("blue", "经典蓝", Color(0xFF007AFF), Color(0xFF0A84FF)),
    Green("green", "绿色", Color(0xFF34C759), Color(0xFF30D158)),
    Amber("amber", "琥珀", Color(0xFFFF9500), Color(0xFFFF9F0A)),
    Rose("rose", "玫红", Color(0xFFFF2D55), Color(0xFFFF375F)),
    Purple("purple", "紫色", Color(0xFF5856D6), Color(0xFF5E5CE6)),
    ;

    fun color(isDark: Boolean): Color = if (isDark) dark else light

    fun onColor(isDark: Boolean): Color {
        val c = color(isDark)
        val l = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        return if (l > 0.65f) Color(0xFF12161C) else Color.White
    }

    companion object {
        fun from(id: String?): AccentChoice =
            entries.firstOrNull { it.id == id } ?: Blue
    }
}

package dev.vibex.companion.app.ui.theme

import androidx.compose.ui.graphics.Color

enum class AccentChoice(
    val id: String,
    val label: String,
    val light: Color,
    val dark: Color,
) {
    Ink("ink", "黑色", Color(0xFF1A1F27), Color(0xFFE8EAED)),
    Gray("gray", "灰色", Color(0xFF5C6570), Color(0xFFC4C8CE)),
    Blue("blue", "蓝色", Color(0xFF3F6CC4), Color(0xFF6B8FDB)),
    Green("green", "绿色", Color(0xFF2F8A63), Color(0xFF4CAF82)),
    Amber("amber", "琥珀", Color(0xFFC4842A), Color(0xFFE0A04A)),
    Rose("rose", "玫红", Color(0xFFB43B45), Color(0xFFF07575)),
    Purple("purple", "紫色", Color(0xFF6A5AAD), Color(0xFF9B8AD4)),
    ;

    fun color(isDark: Boolean): Color = if (isDark) dark else light

    fun onColor(isDark: Boolean): Color {
        val c = color(isDark)
        val l = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        return if (l > 0.55f) Color(0xFF12161C) else Color.White
    }

    companion object {
        fun from(id: String?): AccentChoice =
            entries.firstOrNull { it.id == id } ?: Ink
    }
}

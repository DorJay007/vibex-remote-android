package dev.vibex.companion.app.ui.theme

enum class Appearance(val id: String) {
    Light("light"),
    Dark("dark"),
    System("system");

    companion object {
        fun from(id: String?): Appearance = entries.firstOrNull { it.id == id } ?: System
    }
}

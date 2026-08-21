package dev.vibex.companion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.vibex.companion.app.ui.theme.VibexTheme

@Composable
fun GlowBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = VibexTheme.colors
    Box(modifier.fillMaxSize().background(colors.bg), content = { content() })
}

package dev.vibex.companion.app.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SplashGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    var show by remember { mutableStateOf(true) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            show = false
        } else {
            delay(180)
            show = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(visible = show, enter = fadeIn(), exit = fadeOut()) {
            SplashScreen()
        }
    }
}

@Composable
fun SplashScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(88.dp)
        Text("VibeX", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

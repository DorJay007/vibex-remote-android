package dev.vibex.companion.app.ui.timeline

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.STREAMING_ACTIVITY_INTERVAL_MS
import dev.vibex.companion.nextStreamingActivityVerb
import dev.vibex.companion.streamingActivityLabel
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlinx.coroutines.delay

@Composable
fun StreamingStatus(compact: Boolean = false, modifier: Modifier = Modifier) {
    val colors = VibexTheme.colors
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    var verb by remember { mutableStateOf(nextStreamingActivityVerb()) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(STREAMING_ACTIVITY_INTERVAL_MS)
            verb = nextStreamingActivityVerb(current = verb)
        }
    }
    val pulse = if (reduceMotion) {
        1f
    } else {
        val infinite = rememberInfiniteTransition(label = "streaming-shimmer")
        val value by infinite.animateFloat(
            initialValue = 0.58f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "streaming-shimmer-alpha",
        )
        value
    }
    val spinner = if (compact) 12.dp else 14.dp
    Row(
        modifier
            .padding(vertical = if (compact) 2.dp else 4.dp)
            .semantics { contentDescription = "正在回复" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        CircularProgressIndicator(Modifier.size(spinner), colors.accent, if (compact) 1.5.dp else 2.dp)
        Text(
            streamingActivityLabel(verb),
            fontSize = if (compact) 12.sp else 13.sp,
            color = if (compact) colors.textTertiary else colors.textSecondary,
            modifier = Modifier.graphicsLayer { alpha = pulse },
        )
    }
}

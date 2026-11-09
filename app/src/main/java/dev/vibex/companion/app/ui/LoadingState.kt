package dev.vibex.companion.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vibex.companion.app.ui.theme.PlexMono
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val LoaderDelays: Array<DoubleArray> = Array(3) { row ->
    DoubleArray(3) { column ->
        (column + abs(row - 1)) * 90.0
    }
}

private const val LoaderCycleMs = 650.0

/**
 * 3x3 pixel grid matching iOS `PixelLoader` (`LoadingState.swift`) and desktop `BrailleSpinner`.
 */
@Composable
fun PixelLoader(
    modifier: Modifier = Modifier,
    tint: Color = VibexTheme.colors.textPrimary,
) {
    var timeMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            timeMs = now - start
            delay(16L)
        }
    }

    val cellSize = 4.dp
    val spacing = 1.5.dp
    val totalSize = cellSize * 3 + spacing * 2

    Canvas(modifier = modifier.size(totalSize)) {
        val cellPx = cellSize.toPx()
        val gapPx = spacing.toPx()
        val cornerPx = 0.8.dp.toPx()
        val t = timeMs.toDouble()
        for (row in 0 until 3) {
            for (column in 0 until 3) {
                val raw = (t - LoaderDelays[row][column]).mod(LoaderCycleMs)
                val phase = (if (raw < 0) raw + LoaderCycleMs else raw) / LoaderCycleMs
                val wave = sin(PI * phase)
                val alpha = (0.15 + 0.85 * wave * wave).toFloat().coerceIn(0.15f, 1f)
                drawRoundRect(
                    color = tint.copy(alpha = alpha),
                    topLeft = Offset(
                        x = column * (cellPx + gapPx),
                        y = row * (cellPx + gapPx),
                    ),
                    size = Size(cellPx, cellPx),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )
            }
        }
    }
}

/**
 * Sweeping highlight across a short status label (`LoadingState.swift` `ShimmerText`).
 */
@Composable
fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    val colors = VibexTheme.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )

    val baseColor = colors.textSecondary
    val peakColor = colors.textPrimary

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = peakColor,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val band = w * 0.6f
                val startX = -band + phase * (w + band)
                val brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to baseColor,
                        0.4f to peakColor,
                        0.6f to peakColor,
                        1.0f to baseColor,
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(startX + band, 0f),
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush = brush, blendMode = BlendMode.SrcIn)
                }
            },
    )
}

/**
 * 1:1 port of iOS `LoadingState(label:)` (`LoadingState.swift`):
 * `PixelLoader` + `ShimmerText` + live `0.1s` monospaced elapsed timer (`0.0s` / `1m 2.3s`).
 */
@Composable
fun LoadingState(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val startMs = remember { System.currentTimeMillis() }
    var nowMs by remember { mutableLongStateOf(startMs) }

    LaunchedEffect(startMs) {
        while (true) {
            delay(100L)
            nowMs = System.currentTimeMillis()
        }
    }

    val elapsedSec = ((nowMs - startMs).coerceAtLeast(0L)).toDouble() / 1000.0
    val formattedElapsed = remember(elapsedSec) { formatElapsed(elapsedSec) }

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelLoader()
        ShimmerText(text = label)
        Text(
            text = formattedElapsed,
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 11.5.sp,
                fontFeatureSettings = "tnum",
                color = colors.textSecondary.copy(alpha = 0.7f),
            ),
        )
    }
}

private fun formatElapsed(seconds: Double): String {
    if (seconds < 60.0) return String.format(java.util.Locale.US, "%.1fs", seconds)
    val minutes = (seconds / 60.0).toInt()
    val rem = seconds - minutes * 60.0
    return String.format(java.util.Locale.US, "%dm %.1fs", minutes, rem)
}

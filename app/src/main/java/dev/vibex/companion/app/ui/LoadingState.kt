package dev.vibex.companion.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
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

/**
 * Reusable sweeping-highlight shimmer block (`LoadingState.swift` `ShimmerBlock`).
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 14.dp,
    cornerRadius: Dp = 6.dp,
) {
    val colors = VibexTheme.colors
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonPhase",
    )

    val base = colors.textTertiary.copy(alpha = 0.14f)
    val peak = colors.textSecondary.copy(alpha = 0.28f)

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val band = (w * 0.75f).coerceAtLeast(120f)
                val startX = -band + phase * (w + band * 1.6f)
                val brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to base,
                        0.45f to peak,
                        0.55f to peak,
                        1.0f to base,
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(startX + band, size.height * 0.2f),
                )
                onDrawBehind {
                    drawRect(brush = brush)
                }
            },
    )
}

@Composable
fun ShimmerCircle(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    ShimmerBox(
        modifier = modifier,
        width = size,
        height = size,
        cornerRadius = size / 2,
    )
}

@Composable
fun TimelineSkeletonView(modifier: Modifier = Modifier) {
    val colors = VibexTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ShimmerBox(width = 210.dp, height = 42.dp, cornerRadius = 16.dp)
        }
        repeat(2) { idx ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(0.6.dp, colors.stroke, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShimmerCircle(size = 24.dp)
                    ShimmerBox(width = 110.dp, height = 13.dp, cornerRadius = 5.dp)
                    Spacer(Modifier.weight(1f))
                    ShimmerBox(width = 52.dp, height = 11.dp, cornerRadius = 4.dp)
                }
                ShimmerBox(height = 12.dp, cornerRadius = 5.dp)
                ShimmerBox(width = 250.dp, height = 12.dp, cornerRadius = 5.dp)
                if (idx == 0) {
                    ShimmerBox(height = 56.dp, cornerRadius = 10.dp)
                } else {
                    ShimmerBox(width = 180.dp, height = 12.dp, cornerRadius = 5.dp)
                }
            }
        }
    }
}

@Composable
fun KanbanBoardSkeletonView(modifier: Modifier = Modifier) {
    val colors = VibexTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(0.6.dp, colors.stroke, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(width = 52.dp, height = 18.dp, cornerRadius = 6.dp)
                    ShimmerBox(width = 170.dp, height = 15.dp, cornerRadius = 5.dp)
                    Spacer(Modifier.weight(1f))
                    ShimmerBox(width = 40.dp, height = 12.dp, cornerRadius = 4.dp)
                }
                ShimmerBox(height = 12.dp, cornerRadius = 5.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerCircle(size = 18.dp)
                    ShimmerBox(width = 86.dp, height = 11.dp, cornerRadius = 4.dp)
                    Spacer(Modifier.weight(1f))
                    ShimmerBox(width = 60.dp, height = 11.dp, cornerRadius = 4.dp)
                }
            }
        }
    }
}

@Composable
fun CollaborationListSkeletonView(modifier: Modifier = Modifier) {
    val colors = VibexTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(5) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .border(0.6.dp, colors.stroke, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerCircle(size = 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBox(width = 115.dp, height = 14.dp, cornerRadius = 5.dp)
                        Spacer(Modifier.weight(1f))
                        ShimmerBox(width = 42.dp, height = 11.dp, cornerRadius = 4.dp)
                    }
                    ShimmerBox(width = 195.dp, height = 12.dp, cornerRadius = 5.dp)
                }
            }
        }
    }
}

@Composable
fun CollaborationChatSkeletonView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(3) { idx ->
            if (idx % 2 == 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ShimmerBox(width = 190.dp, height = 42.dp, cornerRadius = 16.dp)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShimmerCircle(size = 34.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShimmerBox(width = 86.dp, height = 11.dp, cornerRadius = 4.dp)
                        ShimmerBox(width = 230.dp, height = 58.dp, cornerRadius = 14.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCardSkeletonView(rows: Int = 4, modifier: Modifier = Modifier) {
    val colors = VibexTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .border(0.6.dp, colors.stroke, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerBox(width = 32.dp, height = 32.dp, cornerRadius = 8.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(width = 130.dp, height = 14.dp, cornerRadius = 5.dp)
                    ShimmerBox(width = 210.dp, height = 11.dp, cornerRadius = 4.dp)
                }
                ShimmerBox(width = 44.dp, height = 18.dp, cornerRadius = 6.dp)
            }
        }
    }
}

@Composable
fun AccountProfileSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShimmerCircle(size = 50.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(width = 130.dp, height = 15.dp, cornerRadius = 5.dp)
            ShimmerBox(width = 180.dp, height = 12.dp, cornerRadius = 4.dp)
        }
    }
}

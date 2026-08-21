package dev.vibex.companion.app.ui.timeline

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.vibex.companion.app.ui.AgentAvatar
import dev.vibex.companion.app.ui.theme.VibexTheme

internal val SuccessGreen = Color(0xFF85D18F)

object TimelineMetrics {
    val gutterWidth: Dp = 30.dp
    val markerSize: Dp = 26.dp
    val lineWidth: Dp = 1.5.dp
    val rowSpacing: Dp = 18.dp
    val groupGap: Dp = 10.dp
    val gutterContentGap: Dp = 10.dp
    val markerGap: Dp = 3.dp
    val blockGap: Dp = 8.dp
}

enum class RailStyle { Standalone, Head, Middle, Tail }

sealed interface MarkerKind {
    data object User : MarkerKind
    data class Assistant(val agentId: String) : MarkerKind
    data object Reasoning : MarkerKind
    data class Tool(val bucket: ToolKindBucket, val state: ToolCallState) : MarkerKind
    data class ToolGroup(val error: Boolean, val streaming: Boolean) : MarkerKind
    data object Plan : MarkerKind
    data object Thinking : MarkerKind
    data object Error : MarkerKind
    data object System : MarkerKind
    data object Footer : MarkerKind
}

data class RailNode(
    val id: String,
    val marker: MarkerKind,
    val content: NodeContent,
    val connectTop: Boolean = true,
    val connectBottom: Boolean = true,
    val startsGroup: Boolean = false,
    val rail: RailStyle = RailStyle.Standalone,
)

sealed interface NodeContent {
    data class User(val text: String) : NodeContent
    data class Assistant(val block: dev.vibex.companion.app.ui.MarkdownBlock, val streaming: Boolean = false) : NodeContent
    data class Reasoning(val text: String, val streaming: Boolean = false) : NodeContent
    data class Tool(val vm: ToolCallVM) : NodeContent
    data class ToolGroup(val items: List<ToolCallVM>, val streaming: Boolean) : NodeContent
    data class Plan(val lines: List<String>) : NodeContent
    data object Thinking : NodeContent
    data class Error(val title: String, val body: String) : NodeContent
    data class System(val text: String) : NodeContent
    data class Footer(val copyText: String) : NodeContent
}

@Composable
fun RailRow(
    marker: MarkerKind,
    connectTop: Boolean,
    connectBottom: Boolean,
    startsGroup: Boolean,
    modifier: Modifier = Modifier,
    rail: RailStyle = RailStyle.Standalone,
    content: @Composable () -> Unit,
) {
    val railColor = VibexTheme.colors.rail
    val hasMarker = rail == RailStyle.Standalone || rail == RailStyle.Head
    val continuation = rail == RailStyle.Middle || rail == RailStyle.Tail
    val topPad = when (rail) {
        RailStyle.Standalone, RailStyle.Head ->
            TimelineMetrics.rowSpacing / 2 + (if (startsGroup) TimelineMetrics.groupGap else 0.dp)
        RailStyle.Middle, RailStyle.Tail -> 0.dp
    }
    val bottomPad = when (rail) {
        RailStyle.Standalone, RailStyle.Tail -> TimelineMetrics.rowSpacing / 2
        RailStyle.Head, RailStyle.Middle -> TimelineMetrics.blockGap
    }
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val centerX = TimelineMetrics.gutterWidth.toPx() / 2f
                val markerCenterY = topPad.toPx() + TimelineMetrics.markerSize.toPx() / 2f
                val gap = if (continuation) 0f else TimelineMetrics.markerSize.toPx() / 2f + TimelineMetrics.markerGap.toPx()
                val w = TimelineMetrics.lineWidth.toPx()
                if (connectTop) {
                    drawLine(railColor, Offset(centerX, -1f), Offset(centerX, markerCenterY - gap), w)
                }
                if (connectBottom) {
                    drawLine(railColor, Offset(centerX, markerCenterY + gap), Offset(centerX, size.height + 1f), w)
                }
            }
            .padding(top = topPad, bottom = bottomPad),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(TimelineMetrics.gutterWidth), contentAlignment = Alignment.TopCenter) {
            if (hasMarker) NodeMarker(marker)
        }
        Spacer(Modifier.width(TimelineMetrics.gutterContentGap))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun NodeMarker(kind: MarkerKind, modifier: Modifier = Modifier) {
    val colors = VibexTheme.colors
    val size = TimelineMetrics.markerSize
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when (kind) {
            is MarkerKind.User -> FilledDisc(Icons.Rounded.Person, colors.accent)
            is MarkerKind.Assistant -> AgentAvatar(kind.agentId, size = size)
            is MarkerKind.Reasoning -> SectionBadgeIcon(Icons.Rounded.Psychology, colors.textTertiary, size)
            is MarkerKind.Tool -> {
                val tint = when (kind.state) {
                    ToolCallState.RUNNING -> colors.accent
                    ToolCallState.DONE -> SuccessGreen
                    ToolCallState.ERROR -> colors.danger
                }
                SectionBadgeIcon(bucketIcon(kind.bucket), tint, size)
                if (kind.state == ToolCallState.RUNNING) PulseRing(tint, size)
            }
            is MarkerKind.ToolGroup -> {
                SectionBadgeIcon(Icons.Rounded.Layers, if (kind.error) colors.danger else colors.accent, size)
                if (kind.streaming) PulseRing(colors.accent, size)
            }
            is MarkerKind.Plan -> SectionBadgeIcon(Icons.Rounded.Checklist, colors.accent, size)
            is MarkerKind.Thinking -> {
                SectionBadgeIcon(Icons.Rounded.MoreHoriz, colors.accent, size)
                PulseRing(colors.accent, size)
            }
            is MarkerKind.Error -> FilledDisc(Icons.Rounded.PriorityHigh, colors.danger)
            is MarkerKind.System ->
                Box(Modifier.size(size * 0.34f).clip(CircleShape).background(colors.textTertiary.copy(alpha = 0.55f)))
            is MarkerKind.Footer ->
                Box(
                    Modifier.size(size * 0.34f).clip(CircleShape)
                        .background(colors.bg)
                        .border(1.5.dp, colors.rail, CircleShape),
                )
        }
    }
}

@Composable
fun SectionBadgeIcon(icon: ImageVector, tint: Color, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.32f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun FilledDisc(icon: ImageVector, fill: Color) {
    val size = TimelineMetrics.markerSize
    val glyph = if (fill.luminance() > 0.6f) Color(0xFF0F0F0F) else Color.White
    Box(Modifier.size(size).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = glyph, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
private fun PulseRing(tint: Color, size: Dp) {
    val transition = rememberInfiniteTransition(label = "pulse-ring")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "pulse-progress",
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                val s = 1f + progress * 0.75f
                scaleX = s
                scaleY = s
                alpha = (1f - progress) * 0.7f
            }
            .border(TimelineMetrics.lineWidth, tint, CircleShape),
    )
}

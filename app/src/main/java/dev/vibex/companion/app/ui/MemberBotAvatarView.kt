package dev.vibex.companion.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.vibex.companion.BotAvatarColorPreset
import dev.vibex.companion.BotAvatarExpression
import dev.vibex.companion.BotAvatarShape
import dev.vibex.companion.CollabBotAvatarConfig
import dev.vibex.companion.app.ui.theme.VibexTheme
import kotlinx.coroutines.delay

fun parseCollabHexColor(raw: String?, fallback: Color = Color(0xFF1584FF)): Color {
    val normalized = CollabBotAvatarConfig.normalizeHexColor(raw, "")
    if (normalized.length == 7 && normalized.startsWith("#")) {
        val hexVal = normalized.drop(1).toLongOrNull(16)
        if (hexVal != null) {
            return Color(0xFF000000L or hexVal)
        }
    }
    return fallback
}

object CollabTagPalette {
    data class Preset(val id: String, val label: String, val color: Color)

    val presets = listOf(
        Preset("amber", "琥珀", Color(0xFFF99408)),
        Preset("violet", "紫罗兰", Color(0xFF8B52FF)),
        Preset("rose", "玫瑰", Color(0xFFFF2994)),
        Preset("cyan", "青色", Color(0xFF0DB49B)),
    )

    fun colorFor(raw: String?): Color? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        presets.firstOrNull { it.id.equals(trimmed, ignoreCase = true) }?.let { return it.color }
        if (trimmed.startsWith("#")) return parseCollabHexColor(trimmed)
        return null
    }

    fun hashColor(name: String): Color {
        val palette = listOf(
            Color(0xFF1584FF),
            Color(0xFF5856D6),
            Color(0xFF0DB49B),
            Color(0xFFFF6B0A),
            Color(0xFFFF2994),
            Color(0xFF09B866),
            Color(0xFF8B52FF),
        )
        val hash = CollabBotAvatarConfig.hashSeed(name).toLong()
        return palette[(hash % palette.size).toInt()]
    }
}

private enum class BotSessionEmote(val expression: BotAvatarExpression) {
    Idle(BotAvatarExpression.Neutral),
    Happy(BotAvatarExpression.Happy),
    Curious(BotAvatarExpression.Curious),
    Excited(BotAvatarExpression.Excited),
    Thinking(BotAvatarExpression.Attentive),
    Nodding(BotAvatarExpression.Proud),
    Surprised(BotAvatarExpression.Surprised),
    Laughing(BotAvatarExpression.Laughing),
    Focused(BotAvatarExpression.Suspicious),
    Wink(BotAvatarExpression.Shy),
    Confused(BotAvatarExpression.Confused),
    Sleepy(BotAvatarExpression.Sleepy),
    Proud(BotAvatarExpression.Proud);

    companion object {
        val sequence = listOf(
            Happy, Curious, Excited, Thinking, Nodding, Surprised,
            Laughing, Focused, Wink, Confused, Sleepy, Proud,
        )
    }
}

private fun buildBotAvatarShapePath(shape: BotAvatarShape, width: Float, height: Float): Path {
    val sx = width / 64f
    val sy = height / 64f
    fun px(x: Float) = x * sx
    fun py(y: Float) = y * sy

    return Path().apply {
        when (shape) {
            BotAvatarShape.Circle -> {
                addOval(Rect(px(8f), py(8f), px(56f), py(56f)))
            }
            BotAvatarShape.Cloud -> {
                moveTo(px(20.5f), py(48.5f))
                cubicTo(px(12f), py(48.5f), px(6.8f), py(43.2f), px(6.8f), py(36.2f))
                cubicTo(px(6.8f), py(30.8f), px(10.4f), py(26.2f), px(15.6f), py(24.8f))
                cubicTo(px(17.1f), py(16f), px(24.2f), py(10.5f), px(32.8f), py(10.5f))
                cubicTo(px(41.8f), py(10.5f), px(49.2f), py(16.5f), px(50.8f), py(25f))
                cubicTo(px(55f), py(26.8f), px(57.2f), py(30.8f), px(57.2f), py(35.5f))
                cubicTo(px(57.2f), py(43.2f), px(51f), py(48.5f), px(43f), py(48.5f))
                close()
            }
            BotAvatarShape.Squircle -> {
                addRoundRect(
                    RoundRect(
                        rect = Rect(px(10.5f), py(10.5f), px(53.5f), py(53.5f)),
                        cornerRadius = CornerRadius(px(10.5f), py(10.5f)),
                    ),
                )
            }
            BotAvatarShape.Sparkle -> {
                moveTo(px(32f), py(6f))
                cubicTo(px(37.5f), py(6f), px(41.2f), py(17.5f), px(46.2f), py(21.5f))
                cubicTo(px(51.2f), py(25.5f), px(58f), py(27f), px(58f), py(32f))
                cubicTo(px(58f), py(37f), px(51.2f), py(38.5f), px(46.2f), py(42.5f))
                cubicTo(px(41.2f), py(46.5f), px(37.5f), py(58f), px(32f), py(58f))
                cubicTo(px(26.5f), py(58f), px(22.8f), py(46.5f), px(17.8f), py(42.5f))
                cubicTo(px(12.8f), py(38.5f), px(6f), py(37f), px(6f), py(32f))
                cubicTo(px(6f), py(27f), px(12.8f), py(25.5f), px(17.8f), py(21.5f))
                cubicTo(px(22.8f), py(17.5f), px(26.5f), py(6f), px(32f), py(6f))
                close()
            }
            BotAvatarShape.Clover -> {
                moveTo(px(32f), py(13.5f))
                cubicTo(px(24f), py(5.5f), px(11.2f), py(10.8f), px(13.8f), py(21.8f))
                cubicTo(px(6.5f), py(25.2f), px(6.5f), py(38.8f), px(13.8f), py(42.2f))
                cubicTo(px(11.2f), py(53.2f), px(24f), py(58.5f), px(32f), py(50.5f))
                cubicTo(px(40f), py(58.5f), px(52.8f), py(53.2f), px(50.2f), py(42.2f))
                cubicTo(px(57.5f), py(38.8f), px(57.5f), py(25.2f), px(50.2f), py(21.8f))
                cubicTo(px(52.8f), py(10.8f), px(40f), py(5.5f), px(32f), py(13.5f))
                close()
            }
            BotAvatarShape.Heart -> {
                moveTo(px(32f), py(53.5f))
                cubicTo(px(27.8f), py(50.6f), px(8.5f), py(37.8f), px(8.5f), py(23.2f))
                cubicTo(px(8.5f), py(13.8f), px(15.8f), py(8.5f), px(23.8f), py(8.5f))
                cubicTo(px(27.8f), py(8.5f), px(30.5f), py(10.5f), px(32f), py(13f))
                cubicTo(px(33.5f), py(10.5f), px(36.2f), py(8.5f), px(40.2f), py(8.5f))
                cubicTo(px(48.2f), py(8.5f), px(55.5f), py(13.8f), px(55.5f), py(23.2f))
                cubicTo(px(55.5f), py(37.8f), px(36.2f), py(50.6f), px(32f), py(53.5f))
                close()
            }
            BotAvatarShape.Pebble -> {
                moveTo(px(32f), py(9f))
                cubicTo(px(36f), py(6f), px(42f), py(8.5f), px(42.5f), py(14f))
                cubicTo(px(48f), py(13.5f), px(52.5f), py(18f), px(50.5f), py(23.5f))
                cubicTo(px(55.5f), py(26f), px(56.5f), py(32.5f), px(52f), py(36.5f))
                cubicTo(px(55f), py(41.5f), px(51.5f), py(47.5f), px(46f), py(47.5f))
                cubicTo(px(45f), py(53f), px(39f), py(56f), px(34f), py(52.5f))
                cubicTo(px(30f), py(56.5f), px(23.5f), py(55f), px(21.5f), py(49.5f))
                cubicTo(px(16f), py(50.5f), px(11.5f), py(45.5f), px(13.5f), py(40f))
                cubicTo(px(8.5f), py(37.5f), px(7.5f), py(31f), px(12f), py(27f))
                cubicTo(px(9f), py(22f), px(12.5f), py(16f), px(18f), py(16f))
                cubicTo(px(19f), py(10.5f), px(25f), py(7.5f), px(30f), py(11f))
                close()
            }
            BotAvatarShape.Droplet -> {
                moveTo(px(32f), py(7.5f))
                cubicTo(px(36.5f), py(7.5f), px(53.5f), py(25.5f), px(53.5f), py(38.5f))
                cubicTo(px(53.5f), py(49.8f), px(44.2f), py(55.8f), px(32f), py(55.8f))
                cubicTo(px(19.8f), py(55.8f), px(10.5f), py(49.8f), px(10.5f), py(38.5f))
                cubicTo(px(10.5f), py(25.5f), px(27.5f), py(7.5f), px(32f), py(7.5f))
                close()
            }
            BotAvatarShape.Capsule -> {
                addRoundRect(
                    RoundRect(
                        rect = Rect(px(7.5f), py(15f), px(56.5f), py(49f)),
                        cornerRadius = CornerRadius(px(17f), py(17f)),
                    ),
                )
            }
            BotAvatarShape.Triangle -> {
                moveTo(px(26f), py(9.5f))
                quadraticTo(px(32f), py(2.5f), px(38f), py(9.5f))
                lineTo(px(55.5f), py(40f))
                quadraticTo(px(60.5f), py(48.5f), px(50.5f), py(51.5f))
                quadraticTo(px(32f), py(55.5f), px(13.5f), py(51.5f))
                quadraticTo(px(3.5f), py(48.5f), px(8.5f), py(40f))
                close()
            }
            BotAvatarShape.Pentagon -> {
                moveTo(px(27.5f), py(9.2f))
                quadraticTo(px(32f), py(6f), px(36.5f), py(9.2f))
                lineTo(px(52.5f), py(20.8f))
                quadraticTo(px(56.5f), py(23.8f), px(54.8f), py(28.8f))
                lineTo(px(48.8f), py(47.5f))
                quadraticTo(px(47.2f), py(52.5f), px(41.8f), py(52.5f))
                lineTo(px(22.2f), py(52.5f))
                quadraticTo(px(16.8f), py(52.5f), px(15.2f), py(47.5f))
                lineTo(px(9.2f), py(28.8f))
                quadraticTo(px(7.5f), py(23.8f), px(11.5f), py(20.8f))
                close()
            }
            BotAvatarShape.Star -> {
                moveTo(px(29f), py(7.5f))
                cubicTo(px(31.5f), py(4.5f), px(35.5f), py(5.5f), px(36.5f), py(9.5f))
                cubicTo(px(37.5f), py(13.5f), px(40.5f), py(14.5f), px(44f), py(12.5f))
                cubicTo(px(47.5f), py(10.5f), px(51f), py(13.5f), px(49f), py(17.2f))
                cubicTo(px(47f), py(21f), px(48.5f), py(24f), px(52.5f), py(25f))
                cubicTo(px(56.5f), py(26f), px(57.2f), py(30.5f), px(53.5f), py(32.5f))
                cubicTo(px(49.8f), py(34.5f), px(49.5f), py(37.8f), px(52f), py(41f))
                cubicTo(px(54.5f), py(44.2f), px(52f), py(48.2f), px(48f), py(46.8f))
                cubicTo(px(44f), py(45.4f), px(41f), py(47.2f), px(40.5f), py(51.2f))
                cubicTo(px(40f), py(55.2f), px(35.5f), py(56.5f), px(33f), py(53f))
                cubicTo(px(30.5f), py(49.5f), px(27.5f), py(49.5f), px(24.5f), py(52.5f))
                cubicTo(px(21.5f), py(55.5f), px(17.2f), py(53.2f), px(18.2f), py(49.2f))
                cubicTo(px(19.2f), py(45.2f), px(17.2f), py(42.5f), px(13.2f), py(42f))
                cubicTo(px(9.2f), py(41.5f), px(8f), py(37f), px(11.5f), py(34.8f))
                cubicTo(px(15f), py(32.6f), px(15.2f), py(29.5f), px(12.2f), py(26.5f))
                cubicTo(px(9.2f), py(23.5f), px(11.5f), py(19.2f), px(15.5f), py(20.2f))
                cubicTo(px(19.5f), py(21.2f), px(22.5f), py(19.2f), px(23f), py(15.2f))
                cubicTo(px(23.5f), py(11.2f), px(26.5f), py(10.5f), px(29f), py(7.5f))
                close()
            }
            BotAvatarShape.Hexagon -> {
                moveTo(px(27.5f), py(8f))
                quadraticTo(px(32f), py(5.4f), px(36.5f), py(8f))
                lineTo(px(51.5f), py(16.8f))
                quadraticTo(px(55.5f), py(19.4f), px(55.5f), py(24.2f))
                lineTo(px(55.5f), py(39.8f))
                quadraticTo(px(55.5f), py(44.6f), px(51.5f), py(47.2f))
                lineTo(px(36.5f), py(56f))
                quadraticTo(px(32f), py(58.6f), px(27.5f), py(56f))
                lineTo(px(12.5f), py(47.2f))
                quadraticTo(px(8.5f), py(44.6f), px(8.5f), py(39.8f))
                lineTo(px(8.5f), py(24.2f))
                quadraticTo(px(8.5f), py(19.4f), px(12.5f), py(16.8f))
                close()
            }
        }
    }
}

private fun DrawScope.drawBotFace(expression: BotAvatarExpression, eyeColor: Color) {
    val sx = size.width / 64f
    val sy = size.height / 64f
    fun px(x: Float) = x * sx
    fun py(y: Float) = y * sy
    fun drawPill(x: Float, y: Float, w: Float, h: Float, rx: Float) {
        drawRoundRect(
            color = eyeColor,
            topLeft = Offset(px(x), py(y)),
            size = Size(w * sx, h * sy),
            cornerRadius = CornerRadius(rx * sx, rx * sy),
        )
    }

    when (expression) {
        BotAvatarExpression.Happy, BotAvatarExpression.Proud -> {
            val left = Path().apply {
                moveTo(px(21.8f), py(33.2f))
                cubicTo(px(23.4f), py(26.8f), px(28.8f), py(26.8f), px(30.4f), py(33.2f))
            }
            val right = Path().apply {
                moveTo(px(33.6f), py(33.2f))
                cubicTo(px(35.2f), py(26.8f), px(40.6f), py(26.8f), px(42.2f), py(33.2f))
            }
            val stroke = Stroke(width = 4.6f * sx, cap = StrokeCap.Round)
            drawPath(left, eyeColor, style = stroke)
            drawPath(right, eyeColor, style = stroke)
        }
        BotAvatarExpression.Laughing -> {
            val left = Path().apply {
                moveTo(px(21.8f), py(27.6f))
                lineTo(px(28.4f), py(31.5f))
                lineTo(px(21.8f), py(35.4f))
            }
            val right = Path().apply {
                moveTo(px(42.2f), py(27.6f))
                lineTo(px(35.6f), py(31.5f))
                lineTo(px(42.2f), py(35.4f))
            }
            val stroke = Stroke(width = 4.4f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(left, eyeColor, style = stroke)
            drawPath(right, eyeColor, style = stroke)
        }
        BotAvatarExpression.Curious -> {
            drawPill(23.8f, 27f, 5.2f, 9.6f, 2.6f)
            drawPill(34.8f, 23.5f, 5.6f, 14.5f, 2.8f)
        }
        BotAvatarExpression.Confused -> {
            drawPill(23.8f, 25.5f, 5.2f, 12.6f, 2.6f)
            drawPill(34.8f, 24.5f, 5.4f, 8.4f, 2.7f)
        }
        BotAvatarExpression.Excited -> {
            drawPill(23.4f, 23.5f, 5.8f, 15.5f, 2.9f)
            drawPill(34.8f, 23.5f, 5.8f, 15.5f, 2.9f)
        }
        BotAvatarExpression.Surprised -> {
            drawPill(22.8f, 23.8f, 6.6f, 15f, 3.3f)
            drawPill(34.6f, 23.8f, 6.6f, 15f, 3.3f)
        }
        BotAvatarExpression.Attentive -> {
            drawPill(24f, 26f, 5.2f, 10.8f, 2.6f)
            drawPill(34.8f, 26f, 5.2f, 10.8f, 2.6f)
        }
        BotAvatarExpression.Suspicious -> {
            drawPill(22.8f, 28.2f, 7.2f, 5.8f, 2.9f)
            drawPill(34f, 28.2f, 7.2f, 5.8f, 2.9f)
        }
        BotAvatarExpression.Shy -> {
            val left = Path().apply {
                moveTo(px(21.8f), py(32.5f))
                cubicTo(px(23.4f), py(27.2f), px(28.6f), py(27.2f), px(30.2f), py(32.5f))
            }
            drawPath(left, eyeColor, style = Stroke(width = 4.5f * sx, cap = StrokeCap.Round))
            drawPill(34.6f, 24.8f, 5.4f, 13.4f, 2.7f)
        }
        BotAvatarExpression.Sleepy, BotAvatarExpression.Unimpressed -> {
            drawPill(22.8f, 29.6f, 7.4f, 4.4f, 2.2f)
            drawPill(33.8f, 29.6f, 7.4f, 4.4f, 2.2f)
        }
        else -> {
            drawPill(23.8f, 25f, 5.4f, 13f, 2.7f)
            drawPill(34.8f, 25f, 5.4f, 13f, 2.7f)
        }
    }
}

@Composable
fun MemberBotAvatarView(
    config: CollabBotAvatarConfig,
    size: Dp = 38.dp,
    animated: Boolean = true,
    sessionActive: Boolean = false,
    selectedOutline: Boolean = false,
    silhouetteOutlineWidth64: Float? = null,
    gazeUpRight: Boolean = false,
    isLead: Boolean = false,
    isSupervisor: Boolean = false,
    reactionTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val colors = VibexTheme.colors
    val fillColor = remember(config.color) { parseCollabHexColor(config.color) }
    val seed = remember(config.color, config.shape) {
        CollabBotAvatarConfig.hashSeed("${config.color}:${config.shape.rawValue}").toLong()
    }

    var currentEmote by remember { mutableStateOf(BotSessionEmote.Idle) }
    var isBlinking by remember { mutableStateOf(false) }

    val eyeScaleY by animateFloatAsState(
        targetValue = if (isBlinking) 0.12f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "blink",
    )

    LaunchedEffect(animated, sessionActive, config.color, config.shape, reactionTrigger) {
        if (reactionTrigger > 0) {
            val next = BotSessionEmote.sequence[(reactionTrigger - 1) % BotSessionEmote.sequence.size]
            currentEmote = next
            delay(650)
            currentEmote = BotSessionEmote.Idle
        }
        if (!animated) return@LaunchedEffect
        var step = (seed % BotSessionEmote.sequence.size).toInt()
        if (sessionActive) {
            while (true) {
                currentEmote = BotSessionEmote.sequence[step % BotSessionEmote.sequence.size]
                step += 1
                delay(750)
                currentEmote = BotSessionEmote.Idle
                delay(1400L + (seed % 600L))
            }
        } else {
            delay(700L + (seed % 20L) * 90L)
            var cycle = 0
            while (true) {
                cycle += 1
                if (cycle % 3 == 0) {
                    currentEmote = BotSessionEmote.sequence[step % BotSessionEmote.sequence.size]
                    step += 1
                    delay(620)
                    currentEmote = BotSessionEmote.Idle
                } else {
                    isBlinking = true
                    delay(100)
                    isBlinking = false
                }
                delay(2800L + (seed % 18L) * 110L)
            }
        }
    }

    val effectiveExpr = if (currentEmote != BotSessionEmote.Idle) currentEmote.expression else config.expression
    val bgColor = colors.bg

    Box(modifier = modifier.size(size), contentAlignment = Alignment.BottomEnd) {
        Canvas(modifier = Modifier.size(size)) {
            val k = this.size.width / 64f
            val path = buildBotAvatarShapePath(config.shape, this.size.width, this.size.height)

            if (selectedOutline) {
                drawPath(
                    path = path,
                    color = Color(0xFFD1D5DB),
                    style = Stroke(width = 8f * k, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 4.5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            if (silhouetteOutlineWidth64 != null) {
                drawPath(
                    path = path,
                    color = bgColor,
                    style = Stroke(width = silhouetteOutlineWidth64 * k, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            drawPath(path = path, color = fillColor, style = Fill)

            val faceOffsetX = (if (gazeUpRight) 3f else 0f) * k
            val faceOffsetY = (if (gazeUpRight) -4f else 0f) * k
            withTransform({
                translate(left = faceOffsetX, top = faceOffsetY)
                scale(scaleX = 1f, scaleY = eyeScaleY, pivot = Offset(this.size.width / 2f, this.size.height / 2f))
            }) {
                drawBotFace(effectiveExpr, Color.White)
            }
        }

        if (isLead || isSupervisor) {
            val badgeSize = (size.value * 0.34f).coerceAtLeast(12f).dp
            Box(
                Modifier
                    .offset(x = 2.dp, y = 2.dp)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(colors.bg)
                    .padding(1.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSupervisor) Icons.Rounded.WorkspacePremium else Icons.Rounded.Star,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(badgeSize - 2.dp),
                )
            }
        }
    }
}

private data class GroupAvatarSlot(val x: Float, val y: Float, val slotSize: Float, val z: Float)

private val groupLayouts = mapOf(
    1 to listOf(GroupAvatarSlot(4f, 4f, 20f, 1f)),
    2 to listOf(
        GroupAvatarSlot(0.25f, 5f, 18f, 1f),
        GroupAvatarSlot(9.75f, 5f, 18f, 2f),
    ),
    3 to listOf(
        GroupAvatarSlot(5.5f, 0.5f, 17f, 1f),
        GroupAvatarSlot(10.5f, 10f, 17f, 2f),
        GroupAvatarSlot(0.5f, 10f, 17f, 3f),
    ),
    4 to listOf(
        GroupAvatarSlot(1f, 1f, 16f, 1f),
        GroupAvatarSlot(11f, 1f, 16f, 2f),
        GroupAvatarSlot(1f, 11f, 16f, 2f),
        GroupAvatarSlot(11f, 11f, 16f, 3f),
    ),
)

@Composable
fun CollabGroupAvatarView(
    avatars: List<CollabBotAvatarConfig>,
    tagColorHex: String? = null,
    size: Dp = 40.dp,
    sessionActive: Boolean = false,
) {
    val colors = VibexTheme.colors
    val shown = remember(avatars) {
        avatars.take(4).ifEmpty { listOf(CollabBotAvatarConfig.derive("group")) }
    }
    val count = shown.size.coerceIn(1, 4)
    val slots = groupLayouts[count] ?: groupLayouts.getValue(1)
    val k = size.value / 28f
    val ringColor = remember(tagColorHex) { CollabTagPalette.colorFor(tagColorHex) }
    val cornerRadius = (size.value * 0.28f).dp

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.bgElevated)
            .then(
                if (ringColor != null) {
                    Modifier.border(1.75.dp, ringColor, RoundedCornerShape(cornerRadius))
                } else {
                    Modifier
                },
            ),
    ) {
        shown.forEachIndexed { index, avatar ->
            val slot = slots[index]
            val px = slot.slotSize * k
            val outlinePx = 0.75f * (size.value / 24f)
            val strokeWidth64 = ((outlinePx * 2f) / px.coerceAtLeast(1f)) * 64f
            MemberBotAvatarView(
                config = avatar,
                size = px.dp,
                animated = true,
                sessionActive = sessionActive,
                silhouetteOutlineWidth64 = strokeWidth64,
                gazeUpRight = true,
                modifier = Modifier
                    .zIndex(slot.z)
                    .offset(x = (slot.x * k).dp, y = (slot.y * k).dp)
                    .rotate(-12f),
            )
        }
    }
}

@Composable
fun MemberBotAvatarPicker(
    value: CollabBotAvatarConfig,
    onValueChange: (CollabBotAvatarConfig) -> Unit,
    label: String = "头像",
) {
    val colors = VibexTheme.colors
    var activeSection by remember { mutableStateOf("color") }
    var reactionKey by remember { mutableIntStateOf(0) }
    val firstRowColors = remember { CollabBotAvatarConfig.colorPresets.take(6) }
    val secondRowColors = remember { CollabBotAvatarConfig.colorPresets.drop(6) }
    val shapeRows = remember { BotAvatarShape.entries.chunked(5) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.bgElevated)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf(
                    Triple("color", "颜色", Icons.Rounded.Palette),
                    Triple("shape", "形状", Icons.Rounded.Category),
                ).forEach { (id, title, icon) ->
                    val selected = activeSection == id
                    Row(
                        modifier = Modifier
                            .then(
                                if (selected) Modifier.shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(0.08f))
                                else Modifier,
                            )
                            .clip(CircleShape)
                            .background(if (selected) colors.card else Color.Transparent)
                            .clickable { activeSection = id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (selected) colors.textPrimary else colors.textSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) colors.textPrimary else colors.textSecondary,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clickable { reactionKey += 1 },
                contentAlignment = Alignment.Center,
            ) {
                MemberBotAvatarView(
                    config = value,
                    size = 68.dp,
                    animated = true,
                    sessionActive = true,
                    reactionTrigger = reactionKey,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.card)
                    .border(1.dp, colors.stroke, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (activeSection == "color") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            firstRowColors.forEach { preset ->
                                ColorSwatchButton(preset, value) {
                                    reactionKey += 1
                                    onValueChange(value.copy(color = preset.hex, expression = BotAvatarExpression.Neutral))
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            secondRowColors.forEach { preset ->
                                ColorSwatchButton(preset, value) {
                                    reactionKey += 1
                                    onValueChange(value.copy(color = preset.hex, expression = BotAvatarExpression.Neutral))
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shapeRows.forEach { rowShapes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                rowShapes.forEach { shape ->
                                    val selected = value.shape == shape
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) colors.textPrimary.copy(0.06f) else Color.Transparent)
                                            .clickable {
                                                reactionKey += 1
                                                onValueChange(value.copy(shape = shape, expression = BotAvatarExpression.Neutral))
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        MemberBotAvatarView(
                                            config = CollabBotAvatarConfig(
                                                color = value.color,
                                                shape = shape,
                                                expression = BotAvatarExpression.Neutral,
                                            ),
                                            size = 32.dp,
                                            animated = false,
                                            selectedOutline = selected,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchButton(
    preset: BotAvatarColorPreset,
    current: CollabBotAvatarConfig,
    onClick: () -> Unit,
) {
    val colors = VibexTheme.colors
    val selected = CollabBotAvatarConfig.normalizeHexColor(current.color) ==
        CollabBotAvatarConfig.normalizeHexColor(preset.hex)
    val swatchColor = remember(preset.hex) { parseCollabHexColor(preset.hex) }

    Box(
        modifier = Modifier
            .size(32.dp)
            .then(
                if (selected) Modifier.border(2.dp, colors.textPrimary.copy(0.25f), RoundedCornerShape(11.dp))
                else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(swatchColor),
        )
    }
}

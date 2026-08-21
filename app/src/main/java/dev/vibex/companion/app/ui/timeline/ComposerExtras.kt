package dev.vibex.companion.app.ui.timeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import dev.vibex.companion.DockGesture
import dev.vibex.companion.classifyDockGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.vibex.companion.ComposerSegment
import dev.vibex.companion.ComposerToken
import dev.vibex.companion.QueuedInput
import dev.vibex.companion.TokenDraft
import dev.vibex.companion.TokenPrefix
import dev.vibex.companion.COMPOSER_TOKEN_SENTINEL
import dev.vibex.companion.applyTextChange
import dev.vibex.companion.deleteComposerTokenBehind
import dev.vibex.companion.insertToken
import dev.vibex.companion.joinComposerSegments
import dev.vibex.companion.lastTokenQuery
import dev.vibex.companion.matchingTokens
import dev.vibex.companion.parseComposerSegments
import dev.vibex.companion.removeComposerSegment
import dev.vibex.companion.app.ui.theme.Ink
import dev.vibex.companion.app.ui.theme.VibexTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenComposerField(
    draft: TokenDraft,
    onDraft: (TokenDraft) -> Unit,
    slash: List<ComposerToken>,
    files: List<ComposerToken>,
    agents: List<ComposerToken>,
    enabled: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
    tags: List<ComposerToken> = emptyList(),
) {
    val colors = VibexTheme.colors
    val suggestions = matchingTokens(draft.text, slash, files, agents, tags)
    val query = lastTokenQuery(draft.text)
    val segments = parseComposerSegments(draft.text)
    val lastText = (segments.lastOrNull() as? ComposerSegment.Text)?.value.orEmpty()
    val head = if (segments.lastOrNull() is ComposerSegment.Text) {
        joinComposerSegments(segments.dropLast(1))
    } else {
        draft.text
    }
    val leading = if (segments.lastOrNull() is ComposerSegment.Text) segments.dropLast(1) else segments
    val focus = remember { FocusRequester() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (suggestions != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.card)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
            ) {
                if (query?.prefix == TokenPrefix.File) {
                    FileMentionPicker(
                        files = files,
                        query = query.query,
                        onPick = { token -> if (token.description != "目录") onDraft(insertToken(draft, token)) },
                    )
                } else if (suggestions.isEmpty()) {
                    Text(
                        tokenEmptyHint(query?.prefix),
                        fontSize = 13.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else {
                    suggestions.forEach { token ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onDraft(insertToken(draft, token)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(token.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.accent)
                            val hint = token.description.ifBlank { token.value.takeIf { it != token.name }.orEmpty() }
                            if (hint.isNotBlank()) Text(hint, fontSize = 12.sp, color = colors.textTertiary, maxLines = 2)
                        }
                    }
                }
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { focus.requestFocus() },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            leading.forEachIndexed { index, part ->
                when (part) {
                    is ComposerSegment.Token -> {
                        TokenChip(part.token, fontSize = 15.sp, lineHeight = 22.sp) {
                            onDraft(TokenDraft(removeComposerSegment(draft.text, index)))
                        }
                    }
                    is ComposerSegment.Text -> {
                        if (part.value.isNotBlank()) {
                            Text(part.value, fontSize = 15.sp, lineHeight = 22.sp, color = colors.textPrimary)
                        }
                    }
                }
            }
            val fieldText = if (lastText.isEmpty() && leading.any { it is ComposerSegment.Token }) COMPOSER_TOKEN_SENTINEL else lastText
            Box(Modifier.wrapContentWidth().widthIn(min = 64.dp)) {
                if (draft.text.isBlank()) {
                    Text(placeholder, color = colors.textTertiary, fontSize = 15.sp, lineHeight = 22.sp)
                }
                BasicTextField(
                    value = fieldText,
                    onValueChange = { typed ->
                        val cleaned = typed.replace(COMPOSER_TOKEN_SENTINEL, "")
                        val deletingToken = lastText.isEmpty() && leading.any { it is ComposerSegment.Token } && cleaned.isEmpty()
                        if (deletingToken) {
                            onDraft(TokenDraft(deleteComposerTokenBehind(draft.text)))
                        } else {
                            onDraft(applyTextChange(draft, head + cleaned, slash, files, agents, tags))
                        }
                    },
                    enabled = enabled,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp, lineHeight = 22.sp),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .focusRequester(focus)
                        .wrapContentWidth()
                        .widthIn(min = 64.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && lastText.isEmpty() && leading.any { it is ComposerSegment.Token }) {
                                onDraft(TokenDraft(deleteComposerTokenBehind(draft.text)))
                                true
                            } else {
                                false
                            }
                        },
                )
            }
        }
    }
}


@Composable
private fun FileMentionPicker(
    files: List<ComposerToken>,
    query: String,
    onPick: (ComposerToken) -> Unit,
) {
    val colors = VibexTheme.colors
    var filter by remember(query) { mutableStateOf(query) }
    val visible = files.filter {
        filter.isBlank() || it.name.contains(filter, true) || it.value.contains(filter, true)
    }.take(24)
    Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.sheet)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("搜索文件", fontSize = 12.sp, color = colors.textTertiary)
            BasicTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.weight(1f),
            )
        }
        if (visible.isEmpty()) {
            Text("没有匹配的文件", fontSize = 13.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp))
        }
        visible.forEach { token ->
            val directory = token.description == "目录"
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (directory) Modifier else Modifier.clickable { onPick(token) })
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (directory) "文件夹" else "文件", fontSize = 11.sp, color = colors.textTertiary)
                Column(Modifier.weight(1f)) {
                    Text(token.name, fontSize = 14.sp, color = if (directory) colors.textSecondary else colors.textPrimary, fontWeight = FontWeight.Medium)
                    if (token.value != token.name) Text(token.value, fontSize = 12.sp, color = colors.textTertiary, maxLines = 1)
                }
            }
        }
    }
}

private fun tokenEmptyHint(prefix: TokenPrefix?): String = when (prefix) {
    TokenPrefix.Slash -> "没有匹配的命令"
    TokenPrefix.File -> "没有匹配的文件"
    TokenPrefix.Tag -> "输入标签名"
    TokenPrefix.Agent -> "没有匹配的 Agent"
    TokenPrefix.Dollar -> "没有匹配项"
    null -> "没有匹配项"
}

data class NavMessage(val index: Int, val text: String)

@Composable
fun EdgeDock(
    messages: List<NavMessage>,
    todos: List<String>,
    open: Boolean,
    onToggle: () -> Unit,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
    parentWidth: Dp,
    parentHeight: Dp,
) {
    val colors = VibexTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    LaunchedEffect(open) { if (open) tab = 0 }
    val parentW = with(density) { parentWidth.toPx() }
    val parentH = with(density) { parentHeight.toPx() }
    val orb = with(density) { 36.dp.toPx() }
    val pad = with(density) { 10.dp.toPx() }
    val x = remember { Animatable(pad) }
    val y = remember { Animatable(with(density) { 8.dp.toPx() }) }
    fun snap() {
        val left = x.value + orb / 2f < parentW / 2f
        val tx = if (left) pad else (parentW - orb - pad).coerceAtLeast(pad)
        val ty = y.value.coerceIn(pad, (parentH - orb - pad).coerceAtLeast(pad))
        scope.launch {
            launch { x.animateTo(tx, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) }
            launch { y.animateTo(ty, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) }
        }
    }
    val onLeft = x.value + orb / 2f <= parentW / 2f
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
                .pointerInput(parentW, parentH) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var traveled = 0f
                        val held = drag(down.id) { change ->
                            val amount = change.positionChange()
                            change.consume()
                            traveled += kotlin.math.abs(amount.x) + kotlin.math.abs(amount.y)
                            scope.launch {
                                x.snapTo((x.value + amount.x).coerceIn(0f, (parentW - orb).coerceAtLeast(0f)))
                                y.snapTo((y.value + amount.y).coerceIn(0f, (parentH - orb).coerceAtLeast(0f)))
                            }
                        }
                        if (held && classifyDockGesture(traveled) == DockGesture.Tap) onToggle() else snap()
                    }
                },
        ) {
            EdgeOrb(
                icon = Icons.Rounded.Forum,
                label = "导航",
                active = open || messages.isNotEmpty() || todos.isNotEmpty(),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
            modifier = Modifier.offset {
                val gap = with(density) { 8.dp.roundToPx() }
                val panelW = with(density) { 280.dp.roundToPx() }
                val px = if (onLeft) {
                    (x.value + orb + gap).roundToInt()
                } else {
                    (x.value - panelW - gap).roundToInt().coerceAtLeast(pad.toInt())
                }
                IntOffset(px, y.value.roundToInt())
            },
        ) {
            Column(
                Modifier
                    .widthIn(max = 280.dp)
                    .heightIn(max = 420.dp)
                    .shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = colors.textPrimary.copy(0.10f), spotColor = colors.textPrimary.copy(0.14f))
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.bgElevated.copy(0.96f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.codeSurface)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DockTab("消息列表", tab == 0, Modifier.weight(1f)) { tab = 0 }
                    DockTab("任务列表", tab == 1, Modifier.weight(1f)) { tab = 1 }
                }
                Spacer(Modifier.size(10.dp))
                if (tab == 0) {
                    MessageNavList(messages, onJump)
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (todos.isEmpty()) Text("还没有任务", fontSize = 13.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp))
                        else todos.forEach { Text(it, fontSize = 13.sp, color = colors.textPrimary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = VibexTheme.colors
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (selected) colors.onAccent else colors.textPrimary,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun EdgeOrb(icon: ImageVector, label: String, active: Boolean) {
    val colors = VibexTheme.colors
    Box(
        Modifier
            .size(36.dp)
            .shadow(4.dp, CircleShape, ambientColor = colors.textPrimary.copy(0.16f), spotColor = colors.textPrimary.copy(0.18f))
            .clip(CircleShape)
            .background(colors.card),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = (if (active) colors.accent else colors.textPrimary).copy(0.72f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MessageNavList(messages: List<NavMessage>, onJump: (Int) -> Unit) {
    val colors = VibexTheme.colors
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (messages.isEmpty()) {
            Text("还没有用户消息", fontSize = 13.sp, color = colors.textTertiary, modifier = Modifier.padding(8.dp))
        }
        messages.forEachIndexed { i, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bg.copy(0.35f))
                    .clickable { onJump(item.index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "#${i + 1}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.codeSurface).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(item.text.ifBlank { "消息" }, fontSize = 13.sp, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun QueueStack(
    items: List<QueuedInput>,
    onCancel: (QueuedInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val colors = VibexTheme.colors
    var expandedId by remember { mutableStateOf<String?>(null) }
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.BottomCenter) {
        items.takeLast(3).forEachIndexed { index, item ->
            val fromBack = items.takeLast(3).size - 1 - index
            val interaction = remember(item.id) { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val open = expandedId == item.id || pressed
            Column(
                Modifier
                    .fillMaxWidth()
                    .zIndex(index.toFloat())
                    .graphicsLayer {
                        translationY = -fromBack * 10f
                        scaleX = 1f - fromBack * 0.04f
                        scaleY = 1f - fromBack * 0.04f
                    }
                    .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = colors.textPrimary.copy(0.10f), spotColor = colors.textPrimary.copy(0.12f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.bgElevated)
                    .border(0.5.dp, colors.stroke, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = interaction, indication = null) {
                        expandedId = if (expandedId == item.id) null else item.id
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        item.text.ifBlank { "排队中的消息" },
                        fontSize = 14.sp,
                        color = colors.textPrimary,
                        maxLines = if (open) 8 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Rounded.Close,
                        "取消排队",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp).clickable { onCancel(item) },
                    )
                }
            }
        }
        Spacer(Modifier.size(((8 + (items.takeLast(3).size - 1).coerceAtLeast(0) * 10)).dp))
    }
}

@Composable
fun SendStopButton(
    inFlight: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = VibexTheme.colors
    val stop = inFlight && !canSend
    val enabled = stop || canSend
    val shape = if (stop) RoundedCornerShape(8.dp) else CircleShape
    val fill = when {
        stop -> colors.danger
        canSend -> Ink
        else -> Ink.copy(0.28f)
    }
    Box(
        Modifier
            .size(36.dp)
            .clip(shape)
            .background(fill)
            .clickable(enabled = enabled) { if (stop) onStop() else onSend() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (stop) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
            if (stop) "停止" else "发送",
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}


@Composable
fun TokenChip(
    token: ComposerToken,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 18.sp,
    onClick: (() -> Unit)? = null,
) {
    val colors = VibexTheme.colors
    Text(
        token.label,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = FontWeight.Medium,
        color = colors.accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .heightIn(min = lineHeight.value.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.accent.copy(0.14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
